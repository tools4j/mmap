package org.tools4j.mmap.region.impl;

public class OS {
    public static final String NAME = java.lang.System.getProperty("os.name");
    public static final boolean WINDOWS = NAME.startsWith("win");

    public static <T> T ifWindows(final T value, final T defaultValue) {
        if (WINDOWS) {
            return value;
        }
        return defaultValue;
    }

    public static boolean isWindows() {
        return WINDOWS;
    }
}
