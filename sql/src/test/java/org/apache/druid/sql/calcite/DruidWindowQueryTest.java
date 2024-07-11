package org.apache.druid.sql.calcite;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.File;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

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

  @Test
  public void ensureAllDeclared() throws Exception {
    final URL windowQueriesUrl = ClassLoader.getSystemResource("druid/window/");
    Path windowFolder = new File(windowQueriesUrl.toURI()).toPath();

    Set<String> allCases = FileUtils
        .streamFiles(windowFolder.toFile(), true, "q")
        .map(file -> windowFolder.relativize(file.toPath()).toString())
        .sorted()
        .collect(Collectors.toSet());

    for (Method method : DruidWindowQueryTest.class.getDeclaredMethods()) {
      DruidTest ann = method.getAnnotation(DruidTest.class);
      if (method.getAnnotation(Test.class) == null || ann == null) {
        continue;
      }
      if (allCases.remove(ann.value() + ".q")) {
        continue;
      }
      fail(String.format(Locale.ENGLISH, "Testcase [%s] references invalid file [%s].", method.getName(), ann.value()));
    }

    for (String string : allCases) {
      string = string.substring(0, string.lastIndexOf('.'));
      System.out.printf(Locale.ENGLISH, "@%s(\"%s\")\n"
                                        + "@Test\n"
                                        + "public void test_%s() {\n"
                                        + "    windowQueryTest();\n"
                                        + "}\n",
                        DruidTest.class.getSimpleName(),
                        string,
                        string.replace('/', '_'));
    }
    assertEquals("Found some non-declared testcases; please add the new testcases printed to the console!", 0, allCases.size());
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
