package org.apache.druid.msq.exec;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql2rel.SqlToRelConverter;
import org.apache.druid.java.util.common.Numbers;
import org.apache.druid.java.util.common.Pair;
import org.apache.druid.java.util.common.StringUtils;
import org.apache.druid.java.util.common.parsers.TimestampParser;
import org.apache.druid.msq.indexing.MSQSpec;
import org.apache.druid.msq.indexing.report.MSQResultsReport;
import org.apache.druid.msq.test.MSQTestBase;
import org.apache.druid.query.QueryContexts;
import org.apache.druid.segment.column.ColumnType;
import org.apache.druid.segment.column.RowSignature;
import org.apache.druid.sql.calcite.DrillWindowQueryTest;
import org.apache.druid.sql.calcite.QueryTestRunner;
import org.apache.druid.sql.calcite.QueryTestRunner.QueryResults;
import org.apache.druid.sql.calcite.planner.PlannerCaptureHook;
import org.apache.druid.sql.calcite.planner.PlannerContext;
import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertNull;

public class MSQDrillWindowQueryTest extends MSQTestBase
{
  public static Collection<Object[]> data()
  {
    Object[][] data = new Object[][]{
        {DEFAULT, DEFAULT_MSQ_CONTEXT}
    };

    return Arrays.asList(data);
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD})
  public @interface DrillTest
  {
    /**
     * Name of the file this test should execute.
     */
    String value();
  }

  @RegisterExtension
  public MSQDrillWindowQueryTest.DrillTestCaseLoaderRule drillTestCaseRule = new MSQDrillWindowQueryTest.DrillTestCaseLoaderRule();

  static class DrillTestCaseLoaderRule implements BeforeEachCallback
  {
    public MSQDrillWindowQueryTest.DrillTestCase testCase = null;

    @Override
    public void beforeEach(ExtensionContext context)
    {
      Method method = context.getTestMethod().get();
      MSQDrillWindowQueryTest.DrillTest annotation = method.getAnnotation(MSQDrillWindowQueryTest.DrillTest.class);
      testCase = (annotation == null) ? null : new MSQDrillWindowQueryTest.DrillTestCase(annotation.value());
    }
  }

  static class DrillTestCase
  {
    private final String query;
    private final List<String[]> results;
    private String filename;

    public DrillTestCase(String filename)
    {
      try {
        this.filename = filename;
        this.query = readStringFromResource(".q");
        String resultsStr = readStringFromResource(".e");
        String[] lines = resultsStr.split("\n");
        results = new ArrayList<>();
        if (resultsStr.length() > 0) {
          for (String string : lines) {
            String[] cols = string.split("\t");
            results.add(cols);
          }
        }
      }
      catch (Exception e) {
        throw new RuntimeException(
            String.format(Locale.ENGLISH, "Encountered exception while loading testcase [%s]", filename),
            e);
      }
    }

    @Nonnull
    private String getQueryString()
    {
      return query;
    }

    @Nonnull
    private List<String[]> getExpectedResults()
    {
      return results;
    }

    @Nonnull
    private String readStringFromResource(String s) throws IOException
    {
      final String query;
      try (InputStream queryIn = ClassLoader.getSystemResourceAsStream("drill/window/queries/" + filename + s)) {
        query = new String(ByteStreams.toByteArray(queryIn), StandardCharsets.UTF_8);
      }
      return query;
    }
  }

  public void windowQueryTest()
  {
    Thread thread = null;
    String oldName = null;
    try {
      thread = Thread.currentThread();
      oldName = thread.getName();
      DrillTestCase testCase = drillTestCaseRule.testCase;
      thread.setName("drillWindowQuery-" + testCase.filename);

      System.out.println("testCase.getQueryString() = " + testCase.getQueryString());

      Pair<MSQSpec, Pair<List<MSQResultsReport.ColumnAndType>, List<Object[]>>> msqSpecPairPair = testSelectQuery()
          .setSql(testCase.getQueryString())
          .setQueryContext(DEFAULT_MSQ_CONTEXT)
          .runQueryWithResult();

      System.out.println("msqSpecPairPair = " + msqSpecPairPair);

//      testBuilder()
//          .skipVectorize(true)
//          .queryContext(ImmutableMap.of(
//                            PlannerContext.CTX_ENABLE_WINDOW_FNS, true,
//                            PlannerCaptureHook.NEED_CAPTURE_HOOK, true,
//                            QueryContexts.ENABLE_DEBUG, true
//                        )
//          )
//          .sql(testCase.getQueryString())
//          .expectedResults(new TextualResultsVerifier(testCase.getExpectedResults(), null))
//          .run();
    }
    finally {
      if (thread != null && oldName != null) {
        thread.setName(oldName);
      }
    }
  }

  public class TextualResultsVerifier implements ResultsVerifier
  {
    protected final List<String[]> expectedResultsText;
    @Nullable
    protected final RowSignature expectedResultRowSignature;
    private RowSignature currentRowSignature;

    public TextualResultsVerifier(List<String[]> expectedResultsString, RowSignature expectedSignature)
    {
      this.expectedResultsText = expectedResultsString;
      this.expectedResultRowSignature = expectedSignature;
    }

    @Override
    public void verifyRowSignature(RowSignature rowSignature)
    {
      if (expectedResultRowSignature != null) {
        Assert.assertEquals(expectedResultRowSignature, rowSignature);
      }
      currentRowSignature = rowSignature;
    }

    @Override
    public void verify(String sql, QueryResults queryResults)
    {
      System.out.println("TextualResultsVerifier.verify");
      List<Object[]> results = queryResults.results;
      System.out.println("results = " + results);
      List<Object[]> expectedResults = parseResults(currentRowSignature, expectedResultsText);
      System.out.println("expectedResults = " + expectedResults);
//      try {
//        Assert.assertEquals(StringUtils.format("result count: %s", sql), expectedResultsText.size(), results.size());
//        if (!isOrdered(queryResults)) {
//          // in case the resultset is not ordered; order via the same comparator before comparision
//          results.sort(new DrillWindowQueryTest.ArrayRowCmp());
//          expectedResults.sort(new DrillWindowQueryTest.ArrayRowCmp());
//        }
//        assertResultsValid(ResultMatchMode.EQUALS_RELATIVE_1000_ULPS, expectedResults, queryResults);
//      }
//      catch (AssertionError e) {
//        log.info("query: %s", sql);
//        log.info(resultsToString("Expected", expectedResults));
//        log.info(resultsToString("Actual", results));
//        throw new AssertionError(StringUtils.format("%s while processing: %s", e.getMessage(), sql), e);
//      }
    }

    private boolean isOrdered(QueryTestRunner.QueryResults queryResults)
    {
      SqlNode sqlNode = queryResults.capture.getSqlNode();
      return SqlToRelConverter.isOrdered(sqlNode);
    }
  }

  private static List<Object[]> parseResults(RowSignature rs, List<String[]> results)
  {
    System.out.println("rs = " + rs);
    List<Object[]> ret = new ArrayList<>();
    for (String[] row : results) {
      Object[] newRow = new Object[row.length];
      List<String> cc = rs.getColumnNames();
      for (int i = 0; i < cc.size(); i++) {
        ColumnType type = rs.getColumnType(i).get();
        assertNull(type.getComplexTypeName());
        final String val = row[i];
        Object newVal;
        if ("null".equals(val)) {
          newVal = null;
        } else {
          switch (type.getType()) {
            case STRING:
              newVal = val;
              break;
            case LONG:
              newVal = parseLongValue(val);
              break;
            case DOUBLE:
              newVal = Numbers.parseDoubleObject(val);
              break;
            default:
              throw new RuntimeException("unimplemented");
          }
        }
        newRow[i] = newVal;
      }
      ret.add(newRow);
    }
    return ret;
  }

  private static Object parseLongValue(final String val)
  {
    if ("".equals(val)) {
      return null;
    }
    try {
      return Long.parseLong(val);
    }
    catch (NumberFormatException e) {
    }
    try {
      double d = Double.parseDouble(val);
      return (long) d;
    }
    catch (NumberFormatException e) {
    }
    try {
      LocalTime v = LocalTime.parse(val);
      Long l = (long) v.getMillisOfDay();
      return l;
    }
    catch (Exception e) {
    }
    Function<String, DateTime> parser = TimestampParser.createTimestampParser("auto");
    try {
      DateTime v = parser.apply(val);
      return v.getMillis();
    }
    catch (IllegalArgumentException iae) {
    }
    throw new RuntimeException("Can't parse input!");
  }

  // testcases_start
  @DrillTest("aggregates/wPrtnOrdrBy_7")
  @Test
  public void test_aggregates_wPrtnOrdrBy_7()
  {
    windowQueryTest();
  }
}
