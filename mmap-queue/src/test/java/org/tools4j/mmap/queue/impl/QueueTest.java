/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2024 tools4j.org (Marco Terzer, Anton Anufriev)
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
///*
// * The MIT License (MIT)
// *
// * Copyright (c) 2016-2024 tools4j.org (Marco Terzer, Anton Anufriev)
// *
// * Permission is hereby granted, free of charge, to any person obtaining a copy
// * of this software and associated documentation files (the "Software"), to deal
// * in the Software without restriction, including without limitation the rights
// * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// * copies of the Software, and to permit persons to whom the Software is
// * furnished to do so, subject to the following conditions:
// *
// * The above copyright notice and this permission notice shall be included in all
// * copies or substantial portions of the Software.
// *
// * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// * SOFTWARE.
// */
//package org.tools4j.mmap.queue.impl;
//
//import org.agrona.ExpandableDirectByteBuffer;
//import org.agrona.concurrent.BusySpinIdleStrategy;
//import org.agrona.concurrent.IdleStrategy;
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.tools4j.mmap.queue.api.Appender;
//import org.tools4j.mmap.queue.api.Poller;
//import org.tools4j.mmap.queue.api.Queue;
//import org.tools4j.mmap.queue.util.FileUtil;
//import org.tools4j.mmap.region.api.AsyncRuntime;
//
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicReference;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//class QueueTest {
//    private static final IdleStrategy ASYNC_RUNTIME_IDLE_STRATEGY = BusySpinIdleStrategy.INSTANCE;
//    private Path tempDir;
//
//    @BeforeEach
//    void setUp() throws IOException {
//        tempDir = Files.createTempDirectory(QueueTest.class.getSimpleName());
//        tempDir.toFile().deleteOnExit();
//    }
//
//    @AfterEach
//    void tearDown() {
//        try {
//            FileUtil.deleteRecursively(tempDir.toFile());
//        } catch (final IOException e) {
//            System.err.println("Deleting temp files failed: tempDir=" + tempDir + ", e=" + e);
//        }
//    }
//
//    @Test
//    void test() {
//        try (final AsyncRuntime asyncRuntime = AsyncRuntime.create(ASYNC_RUNTIME_IDLE_STRATEGY)) {
//            final RegionMapperFactory regionMapperFactory = RegionMapperFactory.async("async", asyncRuntime, false);
//            //final RegionMapperFactory regionMapperFactory = RegionMapperFactory.SYNC;
//            final Queue queue = Queue.builder("test", tempDir.toString(), regionMapperFactory)
//                    .writeTimeout(10, TimeUnit.SECONDS) // for windows build
//                    .build();
//            try (Appender appender = queue.createAppender();
//                 Poller poller1 = queue.createPoller();
//                 Poller poller2 = queue.createPoller()) {
//                String testString1 = "qwerfwvrgtw3243rcvgfwrvwer";
//                long index = append(appender, testString1);
//                assertThat(index).isGreaterThan(-1);
//
//                assertThat(get(poller1, index)).isEqualTo(testString1);
//                assertThat(get(poller2, index)).isEqualTo(testString1);
//
//                String testString2 = "rvwer";
//                long index2 = append(appender, testString2);
//                assertThat(index2).isGreaterThan(-1);
//
//                assertThat(get(poller1, index2)).isEqualTo(testString2);
//                assertThat(get(poller2, index2)).isEqualTo(testString2);
//            }
//        }
//    }
//
//    private static long append(Appender appender, String string) {
//        ExpandableDirectByteBuffer buffer = new ExpandableDirectByteBuffer();
//        int len = buffer.putStringUtf8(0, string);
//
//
//        return appender.append(buffer, 0, len);
//    }
//
//    private String get(Poller poller, long index) {
//        AtomicReference<String> stringValue = new AtomicReference<>(null);
//        if (poller.moveToIndex(index)) {
//            poller.poll((index1, buffer) -> {
//                assertThat(index1).isEqualTo(index);
//                stringValue.set(buffer.getStringUtf8(0));
//                return Direction.STAY;
//            });
//        }
//        return stringValue.get();
//    }
//}