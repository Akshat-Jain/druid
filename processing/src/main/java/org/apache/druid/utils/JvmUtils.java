/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.utils;

import com.google.common.primitives.Ints;
import com.google.inject.Inject;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JvmUtils
{
  public static final int UNKNOWN_VERSION = -1;
  private static final int MAJOR_VERSION = computeMajorVersion();

  @Inject
  private static RuntimeInfo RUNTIME_INFO = new RuntimeInfo();

  private static final ThreadMXBean THREAD_MX_BEAN = ManagementFactory.getThreadMXBean();

  private static int computeMajorVersion()
  {
    final StringTokenizer st = new StringTokenizer(System.getProperty("java.specification.version"), ".");
    if (!st.hasMoreTokens()) {
      return UNKNOWN_VERSION;
    }

    final String majorVersionString = st.nextToken();
    final Integer majorVersion = Ints.tryParse(majorVersionString);
    return majorVersion == null ? UNKNOWN_VERSION : majorVersion;
  }

  /**
   * Returns the major version of the current Java runtime for Java 9 and above. For example: 9, 11, 17, etc.
   *
   * Returns 1 for Java 8 and earlier.
   *
   * Returns {@link #UNKNOWN_VERSION} if the major version cannot be determined. This is a negative number and is
   * therefore lower than all valid versions.
   */
  public static int majorVersion()
  {
    return MAJOR_VERSION;
  }

  /**
   * Deprecated, inject {@link RuntimeInfo} instead of using this function.
   */
  @Deprecated
  public static RuntimeInfo getRuntimeInfo()
  {
    return RUNTIME_INFO;
  }

  public static boolean isThreadCpuTimeEnabled()
  {
    return THREAD_MX_BEAN.isThreadCpuTimeSupported() && THREAD_MX_BEAN.isThreadCpuTimeEnabled();
  }

  public static long safeGetThreadCpuTime()
  {
    if (!isThreadCpuTimeEnabled()) {
      return 0L;
    } else {
      return getCurrentThreadCpuTime();
    }
  }

  /**
   * Returns the total CPU time for current thread.
   * This method should be called after verifying that cpu time measurement for current thread is supported by JVM
   *
   * @return total CPU time for the current thread in nanoseconds.
   *
   * @throws UnsupportedOperationException if the Java virtual machine does not support CPU time measurement for
   *                                       the current thread.
   */
  public static long getCurrentThreadCpuTime()
  {
    return THREAD_MX_BEAN.getCurrentThreadCpuTime();
  }

  /**
   * Executes and returns the value of {@code function}. Also accumulates the CPU time taken for the function (as
   * reported by {@link #getCurrentThreadCpuTime()} into {@param accumulator}.
   */
  public static <T> T safeAccumulateThreadCpuTime(final AtomicLong accumulator, final Supplier<T> function)
  {
    final long start = safeGetThreadCpuTime();

    try {
      return function.get();
    }
    finally {
      accumulator.addAndGet(safeGetThreadCpuTime() - start);
    }
  }

  public static List<URL> systemClassPath()
  {
    List<URL> jobURLs;
    String[] paths = System.getProperty("java.class.path").split(File.pathSeparator);
    jobURLs = Stream.of(paths).map(
        s -> {
          try {
            return Paths.get(s).toUri().toURL();
          }
          catch (MalformedURLException e) {
            throw new UnsupportedOperationException("Unable to create URL classpath entry", e);
          }
        }
    ).collect(Collectors.toList());
    return jobURLs;
  }
}
