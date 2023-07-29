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
package org.tools4j.mmap.region.api;

import org.tools4j.mmap.region.impl.DefaultAsyncRuntime;

/**
 * Enables async region mappings to be registered and executed in a separate thread.
 */
public interface AsyncRuntime extends AutoCloseable {
  /**
   * Abstraction for execution of region mapping of a ring of regions.
   */
  interface Executable {
    /**
     * Executes mapping/unmapping of regions that belong to one region ring.
     *
     * @return number of executed mapping/unmapping done
     */
    int execute();
  }

  /**
   * Register mapping/unmapping of ring regions.
   *
   * @param executable - logic to map or unmap regions in a single ring that a requested for mapping/unmapping.
   */
  void register(Executable executable);

  /**
   * Provides default runtime backed by a thread watching to execute registered mapping tasks.
   *
   * @return default
   */
  static AsyncRuntime createDefault() {
    return new DefaultAsyncRuntime();
  }

  /**
   * Closes the runtime.
   */
  @Override
  void close();
}
