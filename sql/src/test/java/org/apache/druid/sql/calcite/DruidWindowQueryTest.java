package org.apache.druid.sql.calcite;

import org.apache.commons.io.FileUtils;
import org.apache.druid.common.config.NullHandling;
import org.apache.druid.sql.calcite.DisableUnless.DisableUnlessRule;
import org.apache.druid.sql.calcite.NotYetSupported.NotYetSupportedProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
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
  static {
    NullHandling.initializeForTests();
  }

  @RegisterExtension
  public DisableUnlessRule disableWhenNonSqlCompat = DisableUnless.SQL_COMPATIBLE;

  @RegisterExtension
  public NotYetSupportedProcessor ignoreProcessor = new NotYetSupportedProcessor();

  @RegisterExtension
  public DruidTestCaseLoaderRule druidTestCaseRule = new DruidTestCaseLoaderRule();

  @Override
  protected WindowTestCase getCurrentTestCase() {
    return druidTestCaseRule.testCase;
  }

  @Test
  public void ensureAllDeclared() throws Exception {
    final URL windowQueriesUrl = ClassLoader.getSystemResource("druid/window/queries/");
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
      super(filename, "druid/window/queries/");
    }
  }

  static class DruidTestCaseLoaderRule implements BeforeEachCallback {
    public DruidTestCase testCase = null;

    @Override
    public void beforeEach(ExtensionContext context) {
      Method method = context.getTestMethod().get();
      DruidTest annotation = method.getAnnotation(DruidTest.class);
      testCase = (annotation == null) ? null : new DruidTestCase(annotation.value());
    }
  }

  // Test methods
  @DruidTest("aggregates/aggOWnFn_11")
  @Test
  public void test_aggregates_aggOWnFn_11() {
    windowQueryTest();
  }

  @DruidTest("aggregates/aggOWnFn_13")
  @Test
  public void test_aggregates_aggOWnFn_13() {
    windowQueryTest();
  }

//  @DruidTest("aggregates/aggOWnFn_16")
//  @Test
//  public void test_aggregates_aggOWnFn_16() {
//    windowQueryTest();
//  }
//
//  @DruidTest("aggregates/aggOWnFn_1")
//  @Test
//  public void test_aggregates_aggOWnFn_1() {
//    windowQueryTest();
//  }

  // Add more test methods as needed...
}