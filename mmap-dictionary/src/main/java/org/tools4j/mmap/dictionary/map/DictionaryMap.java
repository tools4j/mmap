/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2025 tools4j.org (Marco Terzer, Anton Anufriev)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.tools4j.mmap.dictionary.map;

import org.agrona.DirectBuffer;
import org.tools4j.mmap.dictionary.api.DeleteResult;
import org.tools4j.mmap.dictionary.api.DeletingContext;
import org.tools4j.mmap.dictionary.api.Dictionary;
import org.tools4j.mmap.dictionary.api.InsertingContext;
import org.tools4j.mmap.dictionary.api.KeyValueIterable;
import org.tools4j.mmap.dictionary.api.KeyValuePair;
import org.tools4j.mmap.dictionary.api.Lookup;
import org.tools4j.mmap.dictionary.api.Lookup.KeySpecifier;
import org.tools4j.mmap.dictionary.api.LookupResult;
import org.tools4j.mmap.dictionary.api.UpdatePredicate;
import org.tools4j.mmap.dictionary.api.UpdateResult;
import org.tools4j.mmap.dictionary.api.Updater;
import org.tools4j.mmap.dictionary.api.UpdatingContext;
import org.tools4j.mmap.dictionary.marshal.Marshaller;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public class DictionaryMap<K, V> implements ConcurrentMap<K, V>, AutoCloseable {

    private final Dictionary dictionary;
    private final Class<K> keyType;
    private final Marshaller<K> keyMarshaller;
    private final Marshaller<V> valueMarshaller;
    private final ThreadLocal<Updater> updater;
    private final ThreadLocal<Lookup> lookup;
    private final ThreadLocal<KeyValueIterable> iterable;

    private transient KeySetView<K, V> keySet;
    private transient ValuesView<K, V> values;
    private transient EntrySetView<K, V> entrySet;

    public DictionaryMap(final Dictionary dictionary,
                         final Class<K> keyType,
                         final Marshaller<K> keyMarshaller,
                         final Marshaller<V> valueMarshaller) {
        this.dictionary = requireNonNull(dictionary);
        this.keyType = requireNonNull(keyType);
        this.keyMarshaller = requireNonNull(keyMarshaller);
        this.valueMarshaller = requireNonNull(valueMarshaller);
        this.updater = ThreadLocal.withInitial(dictionary::createUpdater);
        this.lookup = ThreadLocal.withInitial(dictionary::createLookup);
        this.iterable = ThreadLocal.withInitial(dictionary::createIterable);
    }

    @Override
    public void close() {
        dictionary.close();
    }

    public long sizeAsLong() {
        return iterable.get().size();
    }

    @Override
    public int size() {
        final long size = sizeAsLong();
        return size <= Integer.MAX_VALUE ? (int)size : Integer.MAX_VALUE;
    }

    @Override
    public boolean isEmpty() {
        return iterable.get().isEmpty();
    }

    @Override
    public boolean containsKey(final Object key) {
        if (!keyType.isInstance(key) && key != null) {
            return false;
        }
        final K typedKey = keyType.cast(key);
        try (final KeySpecifier keySpec = lookup.get().getting()) {
            final int len = keyMarshaller.marshal(typedKey, keySpec.keyBuffer());
            return keySpec.commitKey(len).isPresent();
        }
    }

    @Override
    public boolean containsValue(final Object value) {
        for (final DirectBuffer buffer : iterable.get().values()) {
            final V candidate = valueMarshaller.unmarshal(buffer);
            if (Objects.equals(value, candidate)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public V get(final Object key) {
        return getOrDefault(key, null);
    }

    @Override
    public V getOrDefault(final Object key, final V defaultValue) {
        if (!keyType.isInstance(key) && key != null) {
            return null;
        }
        final K typedKey = keyType.cast(key);
        try (final KeySpecifier keySpec = lookup.get().getting()) {
            final int len = keyMarshaller.marshal(typedKey, keySpec.keyBuffer());
            final LookupResult result = keySpec.commitKey(len);
            return result.isPresent() ? valueMarshaller.unmarshal(result.value()) : defaultValue;
        }
    }

    private <T> T get(final K key, final BiFunction<? super DictionaryMap<K,V>, ? super LookupResult, T> result) {
        try (final KeySpecifier keySpec = lookup.get().getting()) {
            final int len = keyMarshaller.marshal(key, keySpec.keyBuffer());
            final LookupResult lookupResult = keySpec.commitKey(len);
            return result.apply(this, lookupResult);
        }
    }

    @Override
    public V put(final K key, final V value) {
        return put(key, value, (map, result) ->
                result.hasOldValue() ? valueMarshaller.unmarshal(result.oldValue()) : null);
    }

    private <T> T put(final K key,
                      final V value,
                      final BiFunction<? super DictionaryMap<K,V>, ? super UpdateResult, T> result) {
        final int capacity = keyMarshaller.maxByteCapacity() + valueMarshaller.maxByteCapacity();
        try (final InsertingContext context = updater.get().inserting(capacity)) {
            final int keyLen = keyMarshaller.marshal(key, context.keyBuffer());
            context.commitKey(keyLen);
            final int valueLen = valueMarshaller.marshal(value, context.valueBuffer());
            context.commitValue(valueLen);
            final UpdateResult updateResult = context.put();
            return result.apply(this, updateResult);
        }
    }


    @Override
    public V putIfAbsent(final K key, final V value) {
        final int capacity = keyMarshaller.maxByteCapacity() + valueMarshaller.maxByteCapacity();
        try (final InsertingContext context = updater.get().inserting(capacity)) {
            final int keyLen = keyMarshaller.marshal(key, context.keyBuffer());
            context.commitKey(keyLen);
            final int valueLen = valueMarshaller.marshal(value, context.valueBuffer());
            context.commitValue(valueLen);
            final UpdateResult result = context.putIfAbsent();
            return result.isUpdated() ? value : valueMarshaller.unmarshal(result.value());
        }
    }

    private <T> T putIfMatching(final K key,
                                final V value,
                                final UpdatePredicate condition,
                                final BiFunction<? super DictionaryMap<K, V>, ? super UpdateResult, T> result) {
        final int capacity = keyMarshaller.maxByteCapacity() + valueMarshaller.maxByteCapacity();
        try (final InsertingContext context = updater.get().inserting(capacity)) {
            final int keyLen = keyMarshaller.marshal(key, context.keyBuffer());
            context.commitKey(keyLen);
            final int valueLen = valueMarshaller.marshal(value, context.valueBuffer());
            context.commitValue(valueLen);
            return result.apply(this, context.putIfMatching(condition));
        }
    }

    @Override
    public V remove(final Object key) {
        if (!keyType.isInstance(key) && key != null) {
            return null;
        }
        return remove(keyType.cast(key), (map, result) ->
                result.isDeleted() ? map.valueMarshaller.unmarshal(result.value()) : null);
    }

    private <T> T remove(final K key,
                         final BiFunction<? super DictionaryMap<K,V>, ? super DeleteResult, T> result) {
        try (final DeletingContext context = updater.get().deleting()) {
            final int keyLen = keyMarshaller.marshal(key, context.keyBuffer());
            context.commitKey(keyLen);
            final DeleteResult deleteResult = context.delete();
            return result.apply(this, deleteResult);
        }
    }

    @Override
    public boolean remove(final Object key, final Object value) {
        if (!keyType.isInstance(key) && key != null) {
            return false;
        }
        final K typedKey = keyType.cast(key);
        try (final DeletingContext context = updater.get().deleting()) {
            final int keyLen = keyMarshaller.marshal(typedKey, context.keyBuffer());
            context.commitKey(keyLen);
            final DeleteResult result = context.deleteIfMatching(keyValue -> {
                final V current = valueMarshaller.unmarshal(keyValue.value());
                return Objects.equals(value, current);
            });
            return result.isDeleted();
        }
    }

    @Override
    public V replace(final K key, final V value) {
        return putIfMatching(key, value, (kvOld, kvNew) -> kvOld != null, (map, result) ->
                result.hasOldValue() ? map.valueMarshaller.unmarshal(result.oldValue()) : null
        );
    }

    @Override
    public boolean replace(final K key, final V oldValue, final V newValue) {
        return putIfMatching(key, newValue, (kvOld, kvNew) -> {
            if (kvOld == null) {
                return false;
            }
            final V old = valueMarshaller.unmarshal(kvOld.value());
            return Objects.equals(old, oldValue);
        }, (map, result) -> result.isUpdated());
    }

    @Override
    public void putAll(final Map<? extends K, ? extends V> map) {
        for (final Entry<? extends K, ? extends V> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue(), (dictMap, result) -> null);
        }
    }

    @Override
    public void clear() {
        for (final DirectBuffer buffer : iterable.get().keys()) {
            updater.get().delete(buffer);
        }
    }

    @Override
    public Set<K> keySet() {
        final KeySetView<K,V> ks;
        return (ks = keySet) != null ? ks : (keySet = new KeySetView<>(this));
    }

    @Override
    public Collection<V> values() {
        final ValuesView<K,V> vs;
        return (vs = values) != null ? vs : (values = new ValuesView<>(this));
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        final EntrySetView<K,V> es;
        return (es = entrySet) != null ? es : (entrySet = new EntrySetView<>(this));
    }

    @Override
    public void forEach(final BiConsumer<? super K, ? super V> action) {
        requireNonNull(action);
        for (final KeyValuePair kv : iterable.get()) {
            final K key = keyMarshaller.unmarshal(kv.key());
            final V value = valueMarshaller.unmarshal(kv.value());
            action.accept(key, value);
        }
    }

    @Override
    public void replaceAll(final BiFunction<? super K, ? super V, ? extends V> function) {
        requireNonNull(function);
        final int valueCapacity = valueMarshaller.maxByteCapacity();
        for (final DirectBuffer keyBuf : iterable.get().keys()) {
            final K key = keyMarshaller.unmarshal(keyBuf);
            boolean repeat;
            do {
                try (final UpdatingContext context = updater.get().updating(keyBuf, valueCapacity)) {
                    final V oldValue = valueMarshaller.unmarshal(context.valueBuffer());
                    final V newValue = function.apply(key, oldValue);
                    final int valueLen = valueMarshaller.marshal(newValue, context.valueBuffer());
                    context.commitValue(valueLen);
                    final UpdateResult result = context.putIfMatching((oldPair, newPair) -> {
                        if (oldPair == null) {
                            //has been deleted
                            return false;
                        }
                        final V curValue = valueMarshaller.unmarshal(oldPair.value());
                        return Objects.equals(oldPair, curValue);
                    });
                    repeat = result.isPresent() && !result.isUpdated();
                }
            } while (repeat);
        }
    }

    @Override
    public V computeIfAbsent(final K key, final Function<? super K, ? extends V> mappingFunction) {
        return ConcurrentMap.super.computeIfAbsent(key, mappingFunction);
    }

    @Override
    public V computeIfPresent(final K key, final BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return ConcurrentMap.super.computeIfPresent(key, remappingFunction);
    }

    @Override
    public V compute(final K key, final BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return ConcurrentMap.super.compute(key, remappingFunction);
    }

    @Override
    public V merge(final K key, final V value, final BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        return ConcurrentMap.super.merge(key, value, remappingFunction);
    }

    /* ----------------Views -------------- */

    /**
     * Base class for views.
     */
    private abstract static class CollectionView<K,V,E> implements Collection<E> {
        /**
         * The largest possible (non-power of two) array size.
         */
        static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

        final DictionaryMap<K,V> map;
        CollectionView(final DictionaryMap<K,V> map)  {
            this.map = map;
        }

        /**
         * Removes all elements from this view by removing all the mappings from the map
         * backing this view.
         */
        @Override
        public final void clear()      { map.clear(); }
        @Override
        public final int size()        { return map.size(); }
        @Override
        public final boolean isEmpty() { return map.isEmpty(); }

        // implementations below rely on concrete classes supplying these
        // abstract methods
        /**
         * Returns an iterator over the elements in this collection.
         *
         * <p>The returned iterator is <i>weakly consistent</i>.
         *
         * @return an iterator over the elements in this collection
         */
        @Override
        public abstract Iterator<E> iterator();
        @Override
        public abstract boolean contains(Object o);
        @Override
        public abstract boolean remove(Object o);

        private static final String oomeMsg = "Required array size too large";

        @Override
        public final Object[] toArray() {
            long sz = map.sizeAsLong();
            if (sz > MAX_ARRAY_SIZE)
                throw new OutOfMemoryError(oomeMsg);
            int n = (int)sz;
            Object[] r = new Object[n];
            int i = 0;
            for (E e : this) {
                if (i == n) {
                    if (n >= MAX_ARRAY_SIZE)
                        throw new OutOfMemoryError(oomeMsg);
                    if (n >= MAX_ARRAY_SIZE - (MAX_ARRAY_SIZE >>> 1) - 1)
                        n = MAX_ARRAY_SIZE;
                    else
                        n += (n >>> 1) + 1;
                    r = Arrays.copyOf(r, n);
                }
                r[i++] = e;
            }
            return (i == n) ? r : Arrays.copyOf(r, i);
        }

        @SuppressWarnings("unchecked")
        @Override
        public final <T> T[] toArray(final T[] a) {
            long sz = map.sizeAsLong();
            if (sz > MAX_ARRAY_SIZE)
                throw new OutOfMemoryError(oomeMsg);
            int m = (int)sz;
            T[] r = (a.length >= m) ? a :
                    (T[])java.lang.reflect.Array
                            .newInstance(a.getClass().getComponentType(), m);
            int n = r.length;
            int i = 0;
            for (E e : this) {
                if (i == n) {
                    if (n >= MAX_ARRAY_SIZE)
                        throw new OutOfMemoryError(oomeMsg);
                    if (n >= MAX_ARRAY_SIZE - (MAX_ARRAY_SIZE >>> 1) - 1)
                        n = MAX_ARRAY_SIZE;
                    else
                        n += (n >>> 1) + 1;
                    r = Arrays.copyOf(r, n);
                }
                r[i++] = (T)e;
            }
            if (a == r && i < n) {
                r[i] = null; // null-terminate
                return r;
            }
            return (i == n) ? r : Arrays.copyOf(r, i);
        }

        /**
         * Returns a string representation of this collection.
         * The string representation consists of the string representations
         * of the collection's elements in the order they are returned by
         * its iterator, enclosed in square brackets ({@code "[]"}).
         * Adjacent elements are separated by the characters {@code ", "}
         * (comma and space).  Elements are converted to strings as by
         * {@link String#valueOf(Object)}.
         *
         * @return a string representation of this collection
         */
        @Override
        public final String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            Iterator<E> it = iterator();
            if (it.hasNext()) {
                for (;;) {
                    Object e = it.next();
                    sb.append(e == this ? "(this Collection)" : e);
                    if (!it.hasNext())
                        break;
                    sb.append(',').append(' ');
                }
            }
            return sb.append(']').toString();
        }

        @Override
        public final boolean containsAll(final Collection<?> c) {
            if (c != this) {
                for (Object e : c) {
                    if (!contains(e)) {
                        return false;
                    }
                }
            }
            return true;
        }

        @Override
        public final boolean removeAll(final Collection<?> c) {
            if (c == null) throw new NullPointerException();
            boolean modified = false;
            for (Iterator<E> it = iterator(); it.hasNext();) {
                if (c.contains(it.next())) {
                    it.remove();
                    modified = true;
                }
            }
            return modified;
        }

        @Override
        public final boolean retainAll(final Collection<?> c) {
            if (c == null) throw new NullPointerException();
            boolean modified = false;
            for (Iterator<E> it = iterator(); it.hasNext();) {
                if (!c.contains(it.next())) {
                    it.remove();
                    modified = true;
                }
            }
            return modified;
        }
    }

    /**
     * A view of a DictionaryMap as a {@link Set} of keys, in which additions are disabled.
     * This class cannot be directly instantiated. See {@link #keySet() keySet()}.
     */
    private static class KeySetView<K,V> extends CollectionView<K,V,K> implements Set<K> {
        KeySetView(final DictionaryMap<K,V> map) {  // non-public
            super(map);
        }

        @Override
        public boolean contains(final Object o) {
            //noinspection SuspiciousMethodCalls
            return map.containsKey(o);
        }

        /**
         * Removes the key from this map view, by removing the key (and its
         * corresponding value) from the backing map.  This method does
         * nothing if the key is not in the map.
         *
         * @param  o the key to be removed from the backing map
         * @return {@code true} if the backing map contained the specified key
         */
        @Override
        public boolean remove(final Object o) {
            if (!map.keyType.isInstance(o) && o != null) {
                return false;
            }
            return map.remove(map.keyType.cast(o), (map, result) -> result.isDeleted());
        }

        /**
         * @return an iterator over the keys of the backing map
         */
        @Override
        public Iterator<K> iterator() {
            return new KeyIterator<>(map);
        }

        @Override
        public boolean add(final K K) {
            throw new UnsupportedOperationException();
        }
        @Override
        public boolean addAll(final Collection<? extends K> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int hashCode() {
            int h = 0;
            for (final K k : this) {
                h += Objects.hashCode(k);
            }
            return h;
        }

        @Override
        public boolean equals(final Object o) {
            Set<?> c;
            //noinspection SuspiciousMethodCalls
            return ((o instanceof Set) &&
                    ((c = (Set<?>)o) == this ||
                            (containsAll(c) && c.containsAll(this))));
        }

        @Override
        public Spliterator<K> spliterator() {
            return Spliterators.spliterator(this, Spliterator.CONCURRENT | Spliterator.DISTINCT);
        }

        @Override
        public void forEach(final Consumer<? super K> action) {
            requireNonNull(action);
            for (final KeyValuePair kv : map.iterable.get()) {
                final K key = map.keyMarshaller.unmarshal(kv.key());
                action.accept(key);
            }
        }
    }

    /**
     * A view of a DictionaryMap as a {@link Collection} of values, in which additions are disabled.
     * This class cannot be directly instantiated. See {@link #values()}.
     */
    static final class ValuesView<K,V> extends CollectionView<K,V,V> implements Collection<V> {
        ValuesView(final DictionaryMap<K,V> map) {
            super(map);
        }
        
        @Override
        public boolean contains(final Object o) {
            //noinspection SuspiciousMethodCalls
            return map.containsValue(o);
        }

        @Override
        public boolean remove(final Object o) {
            for (final Iterator<V> it = iterator(); it.hasNext();) {
                if (Objects.equals(o, it.next())) {
                    it.remove();
                    return true;
                }
            }
            return false;
        }

        @Override
        public Iterator<V> iterator() {
            return new ValueIterator<>(map);
        }

        @Override
        public boolean add(final V e) {
            throw new UnsupportedOperationException();
        }
        @Override
        public boolean addAll(final Collection<? extends V> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Spliterator<V> spliterator() {
            return Spliterators.spliterator(this, Spliterator.CONCURRENT);
        }

        @Override
        public void forEach(final Consumer<? super V> action) {
            requireNonNull(action);
            for (final KeyValuePair kv : map.iterable.get()) {
                final V value = map.valueMarshaller.unmarshal(kv.value());
                action.accept(value);
            }
        }
    }

    /**
     * A view of a DictionaryMap as a {@link Set} of (key, value)
     * entries.  This class cannot be directly instantiated. See
     * {@link #entrySet()}.
     */
    private static final class EntrySetView<K,V> extends CollectionView<K,V, Entry<K,V>> implements Set<Map.Entry<K,V>> {
        EntrySetView(final DictionaryMap<K,V> map) {
            super(map);
        }

        @Override
        public boolean contains(final Object o) {
            if (o instanceof Map.Entry) {
                final Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                final Object key = e.getKey();
                if (!map.keyType.isInstance(key) && key != null) {
                    return false;
                }
                final Object value = e.getValue();
                final Object mapped = value != Boolean.FALSE ?
                        map.get(map.keyType.cast(key), (map, result) -> result.isPresent() ?
                                map.valueMarshaller.unmarshal(result.value()) : Boolean.FALSE)
                        :
                        map.get(map.keyType.cast(key), (map, result) -> result.isPresent() ?
                                map.valueMarshaller.unmarshal(result.value()) : Boolean.TRUE);
                return Objects.equals(value, mapped);
            }
            return false;
        }

        @Override
        public boolean remove(final Object o) {
            if (o instanceof Map.Entry) {
                final Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                //noinspection SuspiciousMethodCalls
                return map.remove(e.getKey(), e.getValue());
            }
            return false;
        }

        /**
         * @return an iterator over the entries of the backing map
         */
        public Iterator<Map.Entry<K,V>> iterator() {
            return new EntryIterator<>(map);
        }

        @Override
        public boolean add(final Entry<K,V> e) {
            return map.put(e.getKey(), e.getValue(), (map, result) -> result.isUpdated());
        }

        @Override
        public boolean addAll(final Collection<? extends Entry<K,V>> c) {
            boolean added = false;
            for (final Entry<K,V> e : c) {
                if (add(e)) {
                    added = true;
                }
            }
            return added;
        }

        @Override
        public int hashCode() {
            int h = 0;
            for (final KeyValuePair kv : map.iterable.get()) {
                final K key = map.keyMarshaller.unmarshal(kv.key());
                final V value = map.valueMarshaller.unmarshal(kv.value());
                h += MapEntry.hashCode(key, value);
            }
            return h;
        }

        @Override
        public boolean equals(final Object o) {
            final Set<?> c;
            //noinspection SuspiciousMethodCalls
            return ((o instanceof Set) &&
                    ((c = (Set<?>)o) == this ||
                            (containsAll(c) && c.containsAll(this))));
        }

        public Spliterator<Map.Entry<K,V>> spliterator() {
            return Spliterators.spliterator(this, Spliterator.CONCURRENT | Spliterator.DISTINCT);
        }

        public void forEach(final Consumer<? super Map.Entry<K,V>> action) {
            requireNonNull(action);
            for (final KeyValuePair kv : map.iterable.get()) {
                final K key = map.keyMarshaller.unmarshal(kv.key());
                final V value = map.valueMarshaller.unmarshal(kv.value());
                action.accept(new MapEntry<>(map, key, value));
            }
        }
    }

    private abstract static class BaseIterator<K,V,E> implements Iterator<E> {
        final DictionaryMap<K,V> map;
        KeyValueIterable iterable;
        Iterator<KeyValuePair> iterator;
        KeyValuePair lastReturned;

        BaseIterator(final DictionaryMap<K,V> map) {
            this.map = requireNonNull(map);
            this.iterable = map.dictionary.createIterable();
            this.iterator = iterable.iterator();
        }

        @Override
        public boolean hasNext() {
            if (iterator != null) {
                if (iterator.hasNext()) {
                    return true;
                }
                iterable.close();
                iterable = null;
                iterator = null;
            }
            return false;
        }

        @Override
        public E next() {
            if (iterator == null) {
                throw new NoSuchElementException();
            }
            final KeyValuePair pair = iterator.next();
            lastReturned = pair;
            return unmarshal(pair);
        }

        @Override
        public void remove() {
            final KeyValuePair pair = lastReturned;
            if (pair == null) {
                throw new IllegalStateException();
            }
            lastReturned = null;
            map.updater.get().delete(pair.key());
        }

        abstract protected E unmarshal(final KeyValuePair pair);
    }

    private static final class KeyIterator<K,V> extends BaseIterator<K,V,K> {
        KeyIterator(final DictionaryMap<K,V> map) {
            super(map);
        }

        @Override
        protected K unmarshal(final KeyValuePair pair) {
            return map.keyMarshaller.unmarshal(pair.key());
        }
    }

    private static final class ValueIterator<K,V> extends BaseIterator<K,V,V> {
        ValueIterator(final DictionaryMap<K,V> map) {
            super(map);
        }

        @Override
        protected V unmarshal(final KeyValuePair pair) {
            return map.valueMarshaller.unmarshal(pair.value());
        }
    }

    private static final class EntryIterator<K,V> extends BaseIterator<K,V,Map.Entry<K,V>> {
        EntryIterator(final DictionaryMap<K,V> map) {
            super(map);
        }

        @Override
        protected Entry<K, V> unmarshal(final KeyValuePair pair) {
            final DictionaryMap<K,V> m = map;
            return new MapEntry<>(
                    m,
                    m.keyMarshaller.unmarshal(pair.key()),
                    m.valueMarshaller.unmarshal(pair.value())
            );
        }
    }

    private static final class MapEntry<K,V> implements Map.Entry<K,V> {
        final DictionaryMap<K,V> map;
        final K key;
        V val;

        MapEntry(final DictionaryMap<K,V> map, final K key, final V val) {
            this.map = requireNonNull(map);
            this.key = key;
            this.val = val;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return val;
        }

        @Override
        public int hashCode() {
            return hashCode(key, val);
        }

        static int hashCode(final Object key, final Object val) {
            return Objects.hashCode(key) ^ Objects.hashCode(val);
        }

        @Override
        public String toString() {
            return key + "=" + val;
        }

        @Override
        public boolean equals(final Object o) {
            if (o instanceof Map.Entry) {
                final Map.Entry<?,?> e = (Map.Entry<?,?>)o;
                return Objects.equals(key, e.getKey()) && Objects.equals(val, e.getValue());
            }
            return false;
        }

        /**
         * Sets our entry's value and writes through to the map. The
         * value to return is somewhat arbitrary here. Since we do not
         * necessarily track asynchronous changes, the most recent
         * "previous" value could be different from what we return (or
         * could even have been removed, in which case the put will
         * re-establish). We do not and cannot guarantee more.
         */
        public V setValue(final V value) {
            final V v = val;
            val = value;
            map.put(key, value, (map, result) -> null);
            return v;
        }
    }
}
