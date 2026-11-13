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
package org.tools4j.mmap.perf;

import net.openhft.chronicle.queue.ChronicleQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Smoke test for {@link QueuePerf} with {@link PerfOptions} parsed from command line arguments.
 */
public class QueuePerfTest {

    @TempDir
    File tempDir;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "--queue mmap --senders 2 --receivers 2",
            "--queue mmap --strategy SyncMappingStrategy --region-size 64k --reader iterator",
            "--queue mmap --strategy AsyncMapAheadStrategy --rate 0 --reader iterator",
            "--queue mmap --senders 3 --receivers 2 --reader iterator",
            "--queue mmap --map-ahead 5 --region-size 64k",
            "--queue mmap --prefill both --region-size 64k",
            "--queue mmap --prefill payload --strategy SyncMappingStrategy",
            "--queue mmap --senders 2 --pin 0-3",
            "--queue mmap --pin --receivers 1",
            "--queue chronicle --senders 2 --receivers 2"
    })
    public void runCompletesAndDeletesQueueFiles(final String args) throws Throwable {
        //given
        final PerfOptions options = PerfOptions.parse((args + " --messages 20k --warmup 1k --rate 200k --length 64" +
                " --timeout 60 --dir " + tempDir).split(" "));

        //when
        QueuePerf.run(Objects.requireNonNull(options));

        //then
        assertThat(tempDir.list()).isEmpty();
    }

    @Test
    public void chronicleQueueVersionMatchesChronicleOnClassPath() throws IOException {
        //given
        final Properties pom = new Properties();
        try (final InputStream in = ChronicleQueue.class.getResourceAsStream(
                "/META-INF/maven/net.openhft/chronicle-queue/pom.properties")) {
            pom.load(Objects.requireNonNull(in));
        }

        //when
        final String version = QueuePerf.chronicleQueueVersion();

        //then
        assertThat(version).isEqualTo(pom.getProperty("version"));
    }

    @Test
    public void parseAppliesDefaultsAndValues() {
        assertThat(PerfOptions.parse("--help")).isNull();
        final PerfOptions defaults = Objects.requireNonNull(PerfOptions.parse());
        assertThat(defaults.queue()).isEqualTo(PerfOptions.QueueType.MMAP);
        assertThat(defaults.messages()).isEqualTo(11_000_000);
        assertThat(defaults.warmup()).isEqualTo(1_000_000);
        assertThat(defaults.rate()).isEqualTo(1_000_000);
        assertThat(defaults.receivers()).isEqualTo(2);
        assertThat(defaults.reader()).isEqualTo(PerfOptions.ReaderType.POLLER);
        assertThat(Objects.requireNonNull(PerfOptions.parse("--reader", "Iterator")).reader())
                .isEqualTo(PerfOptions.ReaderType.ITERATOR);
        assertThat(defaults.prefill()).isNull();
        final PerfOptions.PrefillMode header =
                Objects.requireNonNull(PerfOptions.parse("--prefill", "Header")).prefill();
        assertThat(header).isEqualTo(PerfOptions.PrefillMode.HEADER);
        assertThat(header.header()).isTrue();
        assertThat(header.payload()).isFalse();

        final PerfOptions options = Objects.requireNonNull(PerfOptions.parse("--queue", "Chronicle",
                "--messages", "2_500k", "--warmup", "1_000", "--rate", "0", "--senders", "3", "--keep"));
        assertThat(options.queue()).isEqualTo(PerfOptions.QueueType.CHRONICLE);
        assertThat(options.messages()).isEqualTo(2_500_000);
        assertThat(options.warmup()).isEqualTo(1_000);
        assertThat(options.rate()).isZero();
        assertThat(options.senders()).isEqualTo(3);
        assertThat(options.keep()).isTrue();
        assertThat(defaults.pinCpus()).isNull();

        final PerfOptions pinned = Objects.requireNonNull(PerfOptions.parse("--pin", "6,2,4", "--keep"));
        assertThat(pinned.pinCpus()).containsExactly(6, 2, 4);
        assertThat(pinned.keep()).isTrue();
        final PerfOptions pinDefault = Objects.requireNonNull(PerfOptions.parse("--pin", "--keep"));
        assertThat(pinDefault.pinCpus()).startsWith(2);
        assertThat(pinDefault.keep()).isTrue();
    }

    @Test
    public void parseRejectsInvalidArguments() {
        assertThatThrownBy(() -> PerfOptions.parse("--unknown", "1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unknown option");
        assertThatThrownBy(() -> PerfOptions.parse("--messages"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Missing value");
        assertThatThrownBy(() -> PerfOptions.parse("--messages", "1x"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid number");
        assertThatThrownBy(() -> PerfOptions.parse("--queue", "kafka"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must be one of");
        assertThatThrownBy(() -> PerfOptions.parse("--warmup", "2m", "--messages", "1m"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("--warmup");
        assertThatThrownBy(() -> PerfOptions.parse("--queue", "chronicle", "--reader", "iterator"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("only supported");
        assertThatThrownBy(() -> PerfOptions.parse("--queue", "chronicle", "--prefill", "both"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("only supported");
        assertThatThrownBy(() -> PerfOptions.parse("--prefill", "all"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must be one of");
        assertThatThrownBy(() -> PerfOptions.parse("--strategy", "SyncMappingStrategy", "--map-ahead", "4"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("async mapping");
        assertThatThrownBy(() -> PerfOptions.parse("--pin", "1,x"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid CPU list");
        assertThatThrownBy(() -> PerfOptions.parse("--pin", "1,1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid CPU list");
    }
}
