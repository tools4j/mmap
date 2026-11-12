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
package org.tools4j.mmap.core.logging;

import org.junit.jupiter.api.Test;
import org.tools4j.mmap.core.base.Describable;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link LogTarget} with {@link Slf4jLogTarget}, {@link JulLogTarget} and {@link SystemErrLogTarget}.
 */
class LogTargetTest {
    private static final Describable DESCRIBABLE = sb -> sb.append("Resource:name=test");

    @Test
    void messageConsistsOfTextSeparatorDescriptionAndSuffix() {
        assertThat(LogTarget.message("Opened", " ", DESCRIBABLE, "")).isEqualTo("Opened Resource:name=test");
        assertThat(LogTarget.message("Failed", " in ", DESCRIBABLE, " [2 repetitions]"))
                .isEqualTo("Failed in Resource:name=test [2 repetitions]");
    }

    @Test
    void slf4jIsUsedIfPresentWithOneTargetPerClass() {
        final LogTarget target = LogTarget.forClass(LogTargetTest.class);
        assertThat(target).isInstanceOf(Slf4jLogTarget.class);
        assertThat(LogTarget.forClass(LogTargetTest.class)).isSameAs(target);
        target.log(LogTarget.Level.INFO, "slf4j target test message", null);
    }

    @Test
    void systemErrTargetLogsInfoAndAboveWithThreadLevelNameAndStackTrace() {
        final LogTarget target = SystemErrLogTarget.create(LogTargetTest.class);
        assertThat(target.isEnabled(LogTarget.Level.DEBUG)).isFalse();
        assertThat(target.isEnabled(LogTarget.Level.INFO)).isTrue();
        assertThat(target.isEnabled(LogTarget.Level.ERROR)).isTrue();
        final PrintStream err = System.err;
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        System.setErr(new PrintStream(output, true, StandardCharsets.UTF_8));
        try {
            target.log(LogTarget.Level.ERROR, "error message", new RuntimeException("test error"));
        } finally {
            System.setErr(err);
        }
        assertThat(output.toString(StandardCharsets.UTF_8))
                .startsWith("[" + Thread.currentThread().getName() + "] ERROR " + LogTargetTest.class.getName() +
                        " - error message" + System.lineSeparator() + "java.lang.RuntimeException: test error")
                .contains("at " + LogTargetTest.class.getName());
    }

    @Test
    void julTargetMapsLevelsAndLogsMessageWithError() {
        final Logger logger = Logger.getLogger(LogTargetTest.class.getName());
        final List<LogRecord> records = new ArrayList<>();
        final Handler handler = new Handler() {
            @Override
            public void publish(final LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        final Level level = logger.getLevel();
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);
        try {
            logger.setLevel(Level.INFO);
            final LogTarget target = JulLogTarget.create(LogTargetTest.class);
            assertThat(target.isEnabled(LogTarget.Level.DEBUG)).isFalse();
            assertThat(target.isEnabled(LogTarget.Level.INFO)).isTrue();
            assertThat(target.isEnabled(LogTarget.Level.WARN)).isTrue();
            assertThat(target.isEnabled(LogTarget.Level.ERROR)).isTrue();

            final RuntimeException error = new RuntimeException("test");
            target.log(LogTarget.Level.ERROR, "error message", error);
            target.log(LogTarget.Level.WARN, "warning message", null);

            assertThat(records).hasSize(2);
            assertThat(records.get(0).getLevel()).isEqualTo(Level.SEVERE);
            assertThat(records.get(0).getMessage()).isEqualTo("error message");
            assertThat(records.get(0).getThrown()).isSameAs(error);
            assertThat(records.get(1).getLevel()).isEqualTo(Level.WARNING);
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(true);
            logger.setLevel(level);
        }
    }
}
