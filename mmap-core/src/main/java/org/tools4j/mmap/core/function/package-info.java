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
/**
 * Functional interfaces complementing {@link java.util.function}, for combinations of parameters that it lacks.
 * <p>
 * Naming rules, following the scheme of {@code java.util.function}:
 * <ul>
 *     <li><b>Parameter types as prefix, in the order of the parameters:</b> {@code Int}, {@code Long} etc. for
 *     primitives, {@code Obj} for an object, e.g. {@link IntObjConsumer} accepting {@code (int, T)}.</li>
 *     <li><b>Primitives first:</b> the combinations defined here put the primitive before the object, as the
 *     counterparts of the JDK's {@code ObjInt...} types, which take {@code (T, int)}.</li>
 *     <li><b>The kind of function as suffix:</b> {@code Consumer} (no result), {@code Predicate} (boolean result),
 *     {@code Function} (object result), and {@code To<Type>Function} for a primitive result, e.g.
 *     {@code IntObjToIntFunction}.</li>
 *     <li><b>Method names as in the JDK:</b> {@code accept}, {@code test}, {@code apply}, and
 *     {@code applyAs<Type>} for a primitive result.</li>
 *     <li><b>No names of {@code java.util.function}:</b> where the JDK has a type, such as {@code IntFunction},
 *     {@code IntPredicate} or {@code ObjIntConsumer}, it is used instead of defining one here.</li>
 *     <li><b>Added when needed,</b> not as a complete set of all combinations.</li>
 * </ul>
 */
package org.tools4j.mmap.core.function;
