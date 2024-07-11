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

package org.apache.druid.sql.calcite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

public class DruidWindowQueryTest extends WindowQueryTestBase
{
  @RegisterExtension
  private final DruidTestCaseLoaderRule druidTestCaseRule = new DruidTestCaseLoaderRule();

  public DruidWindowQueryTest() {
    this.testCaseLoaderRule = new DruidTestCaseLoaderRule();
  }

  @Override
  protected WindowTestCase getCurrentTestCase() {
    return druidTestCaseRule.testCase;
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD})
  public @interface DruidTest {
    String value();
  }

  static class DruidTestCase extends WindowTestCase {
    public DruidTestCase(String filename) {
      super(filename, "druid/window/");
    }
  }

  private static class DruidTestCaseLoaderRule extends TestCaseLoaderRule
  {
    @Override
    protected WindowTestCase loadTestCase(Method method) {
      DruidTest annotation = method.getAnnotation(DruidTest.class);
      return (annotation == null) ? null : new DruidTestCase(annotation.value());
    }
  }

  @Test
  public void ensureAllDeclared() throws Exception {
    super.ensureAllDeclared("druid/window/", DruidWindowQueryTest.class, DruidTest.class);
  }

  @DruidTest("same_window_across_columns/wikipedia_query_1")
  @Test
  public void test_same_window_wikipedia_query_1() {
    windowQueryTest();
  }

  @DruidTest("same_window_across_columns/wikipedia_query_1_named_window")
  @Test
  public void test_same_window_wikipedia_query_1_named_window() {
    windowQueryTest();
  }

  @DruidTest("multiple_windows/wikipedia_query_1")
  @Test
  public void test_multiple_windows_wikipedia_query_1() {
    windowQueryTest();
  }

  @DruidTest("multiple_windows/wikipedia_query_1_named_windows")
  @Test
  public void test_multiple_windows_wikipedia_query_1_named_windows() {
    windowQueryTest();
  }

  /*

  same window across columns
  - c1 over (xyz), c2 over (xyz)
  - c1 over w, c2 over w, w (xyz) -- named window

  multiple windows
  - over w1 over w2

  swapped columns
  - c1, c2
  - c2, c1
  - the above 2 with non-window and window columns mixed and swapped

   */
}
