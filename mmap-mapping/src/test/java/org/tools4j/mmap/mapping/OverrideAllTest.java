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
package org.tools4j.mmap.mapping;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.tools4j.mmap.core.base.OverrideAll;
import org.tools4j.mmap.mapping.unsafe.RegionMapper;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link OverrideAll} in mmap-mapping's main code.
 */
class OverrideAllTest {
    private static final String PACKAGE_PREFIX = OverrideAllTest.class.getPackageName() + ".";
    private static final Set<String> EXCLUDED_METHODS = Set.of("equals(Object)", "hashCode()", "toString()");

    /** The concrete classes of main code implementing an {@link OverrideAll} interface, and its methods */
    static Stream<Arguments> enforcedClasses() throws IOException, URISyntaxException, ClassNotFoundException {
        final List<Arguments> arguments = new ArrayList<>();
        for (final Class<?> type : mainClasses()) {
            if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
                continue;
            }
            final Map<String, Method> methods = overrideAllMethods(type);
            if (!methods.isEmpty()) {
                arguments.add(Arguments.of(Named.of(type.getName().substring(PACKAGE_PREFIX.length()), type),
                        Named.of(String.join(", ", methods.keySet()), methods)));
            }
        }
        return arguments.stream();
    }

    @ParameterizedTest(name = "{0} declares {1}")
    @MethodSource("enforcedClasses")
    void declaresAllMethodsOfOverrideAllInterfaces(final Class<?> type, final Map<String, Method> methods) {
        assertThat(methods.entrySet().stream().filter(entry -> !declares(type, entry.getValue())).map(Map.Entry::getKey))
                .as("methods of @OverrideAll interfaces not declared").isEmpty();
    }

    /**
     * The methods of the {@link OverrideAll} interfaces of the given type, of their superinterfaces and of the
     * interfaces between them and the type, by signature, sorted
     */
    private static Map<String, Method> overrideAllMethods(final Class<?> type) {
        final Set<Class<?>> supertypes = new LinkedHashSet<>();
        collectSupertypes(type, supertypes);
        final Set<Class<?>> annotated = new LinkedHashSet<>();
        for (final Class<?> supertype : supertypes) {
            if (supertype.isInterface() && supertype.isAnnotationPresent(OverrideAll.class)) {
                annotated.add(supertype);
            }
        }
        final Set<Class<?>> interfaces = new LinkedHashSet<>();
        for (final Class<?> supertype : supertypes) {
            for (final Class<?> overrideAll : annotated) {
                if (supertype.isInterface()
                        && (overrideAll.isAssignableFrom(supertype) || supertype.isAssignableFrom(overrideAll))) {
                    interfaces.add(supertype);
                }
            }
        }
        final Map<String, Method> methods = new TreeMap<>();
        for (final Class<?> anInterface : interfaces) {
            for (final Method method : anInterface.getDeclaredMethods()) {
                final String signature = signature(method);
                if (!Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers())
                        && !method.isSynthetic() && !EXCLUDED_METHODS.contains(signature)) {
                    methods.putIfAbsent(signature, method);
                }
            }
        }
        return methods;
    }

    private static String signature(final Method method) {
        return method.getName() + Arrays.stream(method.getParameterTypes()).map(Class::getSimpleName)
                .collect(joining(", ", "(", ")"));
    }

    private static void collectSupertypes(final Class<?> type, final Set<Class<?>> supertypes) {
        if (type != null && supertypes.add(type)) {
            collectSupertypes(type.getSuperclass(), supertypes);
            for (final Class<?> superInterface : type.getInterfaces()) {
                collectSupertypes(superInterface, supertypes);
            }
        }
    }

    private static boolean declares(final Class<?> type, final Method method) {
        try {
            type.getDeclaredMethod(method.getName(), method.getParameterTypes());
            return true;
        } catch (final NoSuchMethodException e) {
            return false;
        }
    }

    /** All classes compiled from main code, found in its class files, sorted by name */
    private static List<Class<?>> mainClasses() throws IOException, URISyntaxException, ClassNotFoundException {
        final Path root = Path.of(RegionMapper.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        final List<Class<?>> classes = new ArrayList<>();
        try (final Stream<Path> files = Files.walk(root)) {
            for (final Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                final String path = root.relativize(file).toString();
                final String name = path.substring(0, path.length() - ".class".length())
                        .replace(file.getFileSystem().getSeparator(), ".");
                classes.add(Class.forName(name, false, RegionMapper.class.getClassLoader()));
            }
        }
        classes.sort(Comparator.comparing(Class::getName));
        return classes;
    }
}
