package org.apache.druid.msq.exec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.inject.Injector;
import com.google.inject.Module;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql2rel.SqlToRelConverter;
import org.apache.druid.guice.DruidInjectorBuilder;
import org.apache.druid.java.util.common.Numbers;
import org.apache.druid.java.util.common.Pair;
import org.apache.druid.java.util.common.StringUtils;
import org.apache.druid.java.util.common.parsers.TimestampParser;
import org.apache.druid.msq.indexing.MSQSpec;
import org.apache.druid.msq.indexing.report.MSQResultsReport;
import org.apache.druid.msq.sql.MSQTaskSqlEngine;
import org.apache.druid.msq.test.CalciteMSQTestsHelper;
import org.apache.druid.msq.test.CalciteSelectQueryMSQTest;
import org.apache.druid.msq.test.ExtractResultsFactory;
import org.apache.druid.msq.test.MSQTestBase;
import org.apache.druid.msq.test.MSQTestOverlordServiceClient;
import org.apache.druid.msq.test.MSQTestTaskActionClient;
import org.apache.druid.msq.test.VerifyMSQSupportedNativeQueriesPredicate;
import org.apache.druid.query.QueryContexts;
import org.apache.druid.query.groupby.TestGroupByBuffers;
import org.apache.druid.segment.column.ColumnType;
import org.apache.druid.segment.column.RowSignature;
import org.apache.druid.server.QueryLifecycleFactory;
import org.apache.druid.sql.calcite.DrillWindowQueryTest;
import org.apache.druid.sql.calcite.QueryTestBuilder;
import org.apache.druid.sql.calcite.QueryTestRunner;
import org.apache.druid.sql.calcite.QueryTestRunner.QueryResults;
import org.apache.druid.sql.calcite.SqlTestFrameworkConfig;
import org.apache.druid.sql.calcite.TempDirProducer;
import org.apache.druid.sql.calcite.planner.PlannerCaptureHook;
import org.apache.druid.sql.calcite.planner.PlannerContext;
import org.apache.druid.sql.calcite.run.SqlEngine;
import org.apache.druid.sql.calcite.util.SqlTestFramework;
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

@SqlTestFrameworkConfig.ComponentSupplier(CalciteSelectQueryMSQTest.SelectMSQComponentSupplier.class)
public class MSQDrillWindowQueryTest extends DrillWindowQueryTest
{
  public static class SelectMSQComponentSupplier extends SqlTestFramework.StandardComponentSupplier
  {
    public SelectMSQComponentSupplier(TempDirProducer tempFolderProducer)
    {
      super(tempFolderProducer);
    }

    @Override
    public void configureGuice(DruidInjectorBuilder builder)
    {
      super.configureGuice(builder);
      builder.addModules(CalciteMSQTestsHelper.fetchModules(tempDirProducer::newTempFolder, TestGroupByBuffers.createDefault()).toArray(new Module[0]));
    }


    @Override
    public SqlEngine createEngine(
        QueryLifecycleFactory qlf,
        ObjectMapper queryJsonMapper,
        Injector injector
    )
    {
      final WorkerMemoryParameters workerMemoryParameters =
          WorkerMemoryParameters.createInstance(
              WorkerMemoryParameters.PROCESSING_MINIMUM_BYTES * 50,
              2,
              10,
              2,
              0,
              0
          );
      final MSQTestOverlordServiceClient indexingServiceClient = new MSQTestOverlordServiceClient(
          queryJsonMapper,
          injector,
          new MSQTestTaskActionClient(queryJsonMapper, injector),
          workerMemoryParameters,
          ImmutableList.of()
      );
      return new MSQTaskSqlEngine(indexingServiceClient, queryJsonMapper);
    }
  }

  @Override
  protected QueryTestBuilder testBuilder()
  {
    return new QueryTestBuilder(new CalciteTestConfig(true))
        .addCustomRunner(new ExtractResultsFactory(() -> (MSQTestOverlordServiceClient) ((MSQTaskSqlEngine) queryFramework().engine()).overlordClient()))
        .skipVectorize(true)
        .verifyNativeQueries(new VerifyMSQSupportedNativeQueriesPredicate());
  }
}
