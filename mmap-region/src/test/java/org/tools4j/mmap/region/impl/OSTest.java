package org.tools4j.mmap.region.impl;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;


public class OSTest {
    @Test
    public void ifWindows() throws Exception {
        assertThat(OS.ifWindows("win", "nonwin")).isEqualTo("nonwin");
    }

    @Test
    public void isWindows() throws Exception {
        assertThat(OS.isWindows()).isEqualTo(false);
    }

}