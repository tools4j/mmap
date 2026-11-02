/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2026 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.mapping.impl;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Reads and validates system property values, throwing an {@link IllegalArgumentException} naming the property and
 * its value if the value is invalid. Class names as values are instantiated with their public no-arg constructor.
 */
public enum SystemProperties {
    ;

    /**
     * Instance created for a property value, reused while the property value is unchanged.
     *
     * @param value    the property value the instance was created for
     * @param instance the instance
     */
    public record PropertyInstance(String value, Object instance) {}

    /**
     * Returns the int value of the given system property, or the default value if it is not set.
     *
     * @param propertyName the system property name
     * @param validator    validates the value, throwing an exception if it is invalid
     * @param defaultValue the value to return if the property is not set
     * @return the property value if set, and the default value otherwise
     * @throws IllegalArgumentException if the property value is not an int or invalid
     */
    public static int intProperty(final String propertyName, final IntConsumer validator, final int defaultValue) {
        final String propVal = System.getProperty(propertyName, null);
        return propVal == null ? defaultValue : parseInt(propertyName, propVal, validator);
    }

    /**
     * Returns the int value of the given system property, or the supplied default value if it is not set.
     *
     * @param propertyName         the system property name
     * @param validator            validates the value, throwing an exception if it is invalid
     * @param defaultValueSupplier supplies the value to return if the property is not set
     * @return the property value if set, and the supplied default value otherwise
     * @throws IllegalArgumentException if the property value is not an int or invalid
     */
    public static int intProperty(final String propertyName,
                                  final IntConsumer validator,
                                  final IntSupplier defaultValueSupplier) {
        final String propVal = System.getProperty(propertyName, null);
        return propVal == null ? defaultValueSupplier.getAsInt() : parseInt(propertyName, propVal, validator);
    }

    private static int parseInt(final String propertyName, final String propVal, final IntConsumer validator) {
        try {
            final int intValue = Integer.parseInt(propVal);
            validator.accept(intValue);
            return intValue;
        } catch (final Exception e) {
            throw invalidValueException(propertyName, propVal, e);
        }
    }

    /**
     * Returns the long value of the given system property, or the default value if it is not set.
     *
     * @param propertyName the system property name
     * @param validator    validates the value, throwing an exception if it is invalid
     * @param defaultValue the value to return if the property is not set
     * @return the property value if set, and the default value otherwise
     * @throws IllegalArgumentException if the property value is not a long or invalid
     */
    public static long longProperty(final String propertyName, final LongConsumer validator, final long defaultValue) {
        final String propVal = System.getProperty(propertyName, null);
        if (propVal == null) {
            return defaultValue;
        }
        try {
            final long longValue = Long.parseLong(propVal);
            validator.accept(longValue);
            return longValue;
        } catch (final Exception e) {
            throw invalidValueException(propertyName, propVal, e);
        }
    }

    /**
     * Returns the boolean value of the given system property, or the default value if it is not set.
     *
     * @param propertyName the system property name
     * @param defaultValue the value to return if the property is not set
     * @return the property value if set, and the default value otherwise
     * @throws IllegalArgumentException if the property value is neither true nor false, ignoring case
     */
    public static boolean booleanProperty(final String propertyName, final boolean defaultValue) {
        final String propVal = System.getProperty(propertyName, null);
        if (propVal == null) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(propVal)) {
            return true;
        }
        if ("false".equalsIgnoreCase(propVal)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid value for system property: " + propertyName + "=" + propVal +
                " (expected true or false)");
    }

    /**
     * Returns the enum constant named by the given system property, or the default value if it is not set.
     *
     * @param propertyName the system property name
     * @param enumType     the enum type
     * @param defaultValue the value to return if the property is not set
     * @param <E>          the enum type
     * @return the enum constant named by the property value if set, and the default value otherwise
     * @throws IllegalArgumentException if the property value is not the name of an enum constant
     */
    public static <E extends Enum<E>> E enumProperty(final String propertyName,
                                                     final Class<E> enumType,
                                                     final E defaultValue) {
        final String propVal = System.getProperty(propertyName, null);
        if (propVal == null) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumType, propVal);
        } catch (final Exception e) {
            throw invalidValueException(propertyName, propVal, e);
        }
    }

    /**
     * Returns the instance for the value of the given system property: a named instance if the value is a name known
     * to the given function, and otherwise an instance of the class given by name as value, created once and reused
     * while the property value is unchanged.
     *
     * @param propertyName   the system property name
     * @param type           the type of the instance
     * @param defaultValue   the property value to use if the property is not set
     * @param namedInstances returns the instance for a name, or null if the value is not a known name
     * @param instance       holds the instance created for the last property value
     * @param <T>            the type of the instance
     * @return the named instance, or the instance of the class given by the property value
     * @throws IllegalArgumentException if the class cannot be instantiated or is not of the expected type
     */
    public static <T> T namedOrClassInstanceProperty(final String propertyName,
                                                     final Class<T> type,
                                                     final String defaultValue,
                                                     final Function<? super String, ? extends T> namedInstances,
                                                     final AtomicReference<PropertyInstance> instance) {
        final String propVal = System.getProperty(propertyName, defaultValue);
        final T namedInstance = namedInstances.apply(propVal);
        if (namedInstance != null) {
            return namedInstance;
        }
        return instanceProperty(propVal, type, instance, value -> newInstance(propertyName, value, type));
    }

    /**
     * Returns the instance for the given property value, created with the factory once and reused while the property
     * value is unchanged.
     *
     * @param propVal  the property value
     * @param type     the type of the instance
     * @param instance holds the instance created for the last property value
     * @param factory  creates the instance for a property value
     * @param <T>      the type of the instance
     * @return the instance for the property value
     */
    public static <T> T instanceProperty(final String propVal,
                                         final Class<T> type,
                                         final AtomicReference<PropertyInstance> instance,
                                         final Function<? super String, ? extends T> factory) {
        final PropertyInstance current = instance.get();
        if (current != null && current.value().equals(propVal)) {
            return type.cast(current.instance());
        }
        //NOTE: racing threads may each create an instance, the first to publish its instance wins
        final PropertyInstance created = new PropertyInstance(propVal, factory.apply(propVal));
        final PropertyInstance witness = instance.compareAndExchange(current, created);
        final PropertyInstance winner = witness == current ? created : witness;
        return type.cast(winner.value().equals(propVal) ? winner.instance() : created.instance());
    }

    /**
     * Returns a supplier for the type given by class name as value of the given system property, either the class of
     * the value itself or of a supplier of such values, or the default supplier if the property is not set. Every
     * invocation of the returned supplier provides a new value, unless it is a supplier returning the same value
     * every time.
     *
     * @param propertyName         the system property name
     * @param type                 the type of the supplied values
     * @param defaultValueSupplier the supplier to return if the property is not set
     * @param <T>                  the type of the supplied values
     * @return a supplier for the property value if set, and the default supplier otherwise
     */
    public static <T> Supplier<T> supplierProperty(final String propertyName,
                                                   final Class<T> type,
                                                   final Supplier<T> defaultValueSupplier) {
        requireNonNull(propertyName);
        requireNonNull(type);
        requireNonNull(defaultValueSupplier);
        final String propVal = System.getProperty(propertyName, null);
        return propVal == null ? defaultValueSupplier : newSupplier(propertyName, propVal, type);
    }

    private static <T> Supplier<T> newSupplier(final String propName, final String propVal, final Class<T> type) {
        final AtomicReference<Supplier<?>> supplierPtr = new AtomicReference<>();
        return () -> {
            try {
                Supplier<?> supplier = supplierPtr.get();
                if (supplier != null) {
                    return type.cast(supplier.get());
                }
                final Object value = newInstance(propName, propVal, Object.class);
                if (type.isInstance(value)) {
                    supplierPtr.set(() -> newInstance(propName, propVal, type));
                    return type.cast(value);
                }
                if (value instanceof Supplier) {
                    supplier = (Supplier<?>) value;
                    supplierPtr.set(supplier);
                    return type.cast(supplier.get());
                }
                throw new IllegalArgumentException("Value expected to be of type " + type.getName() +
                        " or a supplier of such a value, but was found to be: " + value);
            } catch (final Exception e) {
                throw invalidValueException(propName, propVal, e);
            }
        };
    }

    /**
     * Returns a new instance of the class given by name as system property value.
     *
     * @param propName the system property name, for the exception message
     * @param propVal  the system property value, a class name
     * @param type     the expected type of the instance
     * @param <T>      the expected type of the instance
     * @return a new instance of the class created with its public no-arg constructor
     * @throws IllegalArgumentException if the class cannot be instantiated or is not of the expected type
     */
    public static <T> T newInstance(final String propName, final String propVal, final Class<T> type) {
        try {
            final Class<?> clazz = Class.forName(propVal);
            final Object value = clazz.getDeclaredConstructor().newInstance();
            return type.cast(value);
        } catch (final Exception e) {
            throw invalidValueException(propName, propVal, e);
        }
    }

    private static IllegalArgumentException invalidValueException(final String propertyName,
                                                                  final String propVal,
                                                                  final Exception cause) {
        return new IllegalArgumentException("Invalid value for system property: " + propertyName + "=" + propVal,
                cause);
    }
}
