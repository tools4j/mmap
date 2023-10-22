/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2023 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.region.impl;

import org.agrona.concurrent.BackoffIdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.AsyncRuntime;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;


public class DefaultAsyncRuntime implements AsyncRuntime {
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAsyncRuntime.class);

  private final List<Executable> regionRingProcessors = new CopyOnWriteArrayList<>();
  private final AtomicBoolean finished = new AtomicBoolean(false);

  public DefaultAsyncRuntime() {

    final Thread thread = new Thread(() -> {
      LOGGER.info("Started async region mapping runtime");
      BackoffIdleStrategy idleStrategy = new BackoffIdleStrategy(100, 100, 100, 100);
      while (!finished.get()) {
        int workCount = 0;
        for (int index = 0; index < regionRingProcessors.size(); index++) {
          final Executable regionRingProcessor = regionRingProcessors.get(index);
          try {
            workCount += regionRingProcessor.execute();
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        }
        idleStrategy.idle(workCount);
      }
      LOGGER.info("Stopped async region mapping runtime");
    });
    thread.setName("region-mapper");
    thread.setDaemon(true);
    thread.setUncaughtExceptionHandler((t, e) -> LOGGER.error("Async runtime failed with exception", e));
    thread.start();
  }

  @Override
  public void register(Executable executable) {
    regionRingProcessors.add(executable);
  }

  @Override
  public void close() {
    finished.set(true);
  }

  @Override
  public String toString() {
    return "Default AsyncRuntime";
  }

}
