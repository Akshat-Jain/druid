package org.apache.druid.benchmark.query;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;
import org.apache.druid.common.config.NullHandling;
import org.apache.druid.java.util.common.logger.Logger;
import org.apache.druid.msq.sql.MSQTaskSqlEngine;
import org.apache.druid.msq.test.ExtractResultsFactory;
import org.apache.druid.msq.test.MSQTestOverlordServiceClient;
import org.apache.druid.msq.test.StandardMSQComponentSupplier;
import org.apache.druid.msq.util.MultiStageQueryContext;
import org.apache.druid.query.QueryRunnerFactoryConglomerate;
import org.apache.druid.segment.join.JoinableFactoryWrapper;
import org.apache.druid.server.SpecificSegmentsQuerySegmentWalker;
import org.apache.druid.sql.calcite.BaseCalciteQueryTest;
import org.apache.druid.sql.calcite.QueryTestBuilder;
import org.apache.druid.sql.calcite.SqlTestFrameworkConfig;
import org.apache.druid.sql.calcite.TempDirProducer;
import org.apache.druid.sql.calcite.util.TestDataBuilder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark that tests various SQL queries against MSQ engine.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 1)
@Warmup(iterations = 1)
@Measurement(iterations = 5)
@SqlTestFrameworkConfig.ComponentSupplier(MSQWindowFunctionsBenchmark.MSQComponentSupplier.class)
public class MSQWindowFunctionsBenchmark extends BaseCalciteQueryTest
{
  static {
    NullHandling.initializeForTests();
  }

  private static final Logger log = new Logger(MSQWindowFunctionsBenchmark.class);

  @Param({"2", "5"})
  private int maxNumTasks;

  private List<Annotation> annotations;

  @Setup(Level.Trial)
  public void setup()
  {
    annotations = Arrays.asList(MSQWindowFunctionsBenchmark.class.getAnnotations());

    // Populate the QueryableIndex for the benchmark datasource.
    TestDataBuilder.getQueryableIndexForBenchmarkDatasource();
  }

  @Benchmark
  public void benchmarkTest1(Blackhole blackhole) throws NoSuchMethodException
  {
    String sql = "SELECT ROW_NUMBER() "
                 + "OVER (PARTITION BY dimUniform ORDER BY dimSequential) "
                 + "FROM benchmark_ds";
    querySql(sql, blackhole);
  }

  @Benchmark
  public void benchmarkTest2(Blackhole blackhole) throws NoSuchMethodException
  {
    String sql = "SELECT "
                 + "SUM(dimSequentialHalfNull) + SUM(dimHyperUnique), "
                 + "LAG(SUM(dimSequentialHalfNull + dimHyperUnique)) OVER (PARTITION BY dimUniform ORDER BY dimSequential) "
                 + "FROM benchmark_ds "
                 + "GROUP BY __time, dimUniform, dimSequential";
    querySql(sql, blackhole);
  }

  @Benchmark
  public void benchmarkTest3(Blackhole blackhole) throws NoSuchMethodException
  {
    String sql = "select\n"
                 + "dimZipf, dimSequential, minFloatZipf,\n"
                 + "row_number() over (partition by dimSequential order by minFloatZipf) as c1,\n"
                 + "row_number() over (partition by dimZipf order by minFloatZipf) as c2,\n"
                 + "row_number() over (partition by minFloatZipf order by minFloatZipf) as c3,\n"
                 + "row_number() over (partition by dimSequential, dimZipf order by minFloatZipf, dimSequential) as c4,\n"
                 + "row_number() over (partition by minFloatZipf, dimZipf order by dimSequential) as c5,\n"
                 + "row_number() over (partition by minFloatZipf, dimSequential order by dimZipf) as c6,\n"
                 + "row_number() over (partition by dimSequential, minFloatZipf, dimZipf order by dimZipf, minFloatZipf) as c7,\n"
                 + "row_number() over (partition by dimSequential, minFloatZipf, dimZipf order by minFloatZipf) as c8\n"
                 + "from benchmark_ds\n"
                 + "group by dimZipf, dimSequential, minFloatZipf";
    querySql(sql, blackhole);
  }

  public void querySql(String sql, Blackhole blackhole) throws NoSuchMethodException
  {
    final Map<String, Object> context = ImmutableMap.of(
        MultiStageQueryContext.CTX_MAX_NUM_TASKS, maxNumTasks
    );
    CalciteTestConfig calciteTestConfig = createCalciteTestConfig();
    QueryTestBuilder queryTestBuilder = new QueryTestBuilder(calciteTestConfig)
        .addCustomRunner(
            new ExtractResultsFactory(() -> (MSQTestOverlordServiceClient) ((MSQTaskSqlEngine) queryFramework().engine()).overlordClient())
        );

    queryFrameworkRule.setConfig(new SqlTestFrameworkConfig(annotations));
    final List<Object[]> resultList = queryTestBuilder
        .skipVectorize(true)
        .queryContext(context)
        .sql(sql)
        .results()
        .results;

    if (!resultList.isEmpty()) {
      Object[] lastRow = resultList.get(resultList.size() - 1);
      blackhole.consume(lastRow);
    } else {
      log.info("No rows returned by the query.");
    }
  }

  protected static class MSQComponentSupplier extends StandardMSQComponentSupplier
  {
    public MSQComponentSupplier(TempDirProducer tempFolderProducer)
    {
      super(tempFolderProducer);
    }

    @Override
    public SpecificSegmentsQuerySegmentWalker createQuerySegmentWalker(
        QueryRunnerFactoryConglomerate conglomerate,
        JoinableFactoryWrapper joinableFactory,
        Injector injector
    )
    {
      final SpecificSegmentsQuerySegmentWalker retVal = super.createQuerySegmentWalker(
          conglomerate,
          joinableFactory,
          injector);

      final File tmpFolder = tempDirProducer.newTempFolder();
      TestDataBuilder.attachIndexesForBenchmarkDatasource(retVal, tmpFolder);
      return retVal;
    }
  }
}

/*
todo: try adding bigger datasource in the segment walker [done]
todo: add a param for maxNumTasks
todo: add different queries
todo: remove these todos

# Run complete. Total time: 01:59:06

Benchmark                                   (maxNumTasks)  Mode  Cnt       Score      Error  Units
MSQWindowFunctionsBenchmark.benchmarkTest1              2  avgt    5  101768.278 ± 3488.765  ms/op
MSQWindowFunctionsBenchmark.benchmarkTest1              5  avgt    5  101551.508 ± 5248.099  ms/op
MSQWindowFunctionsBenchmark.benchmarkTest2              2  avgt    5  175451.830 ± 7012.636  ms/op
MSQWindowFunctionsBenchmark.benchmarkTest2              5  avgt    5  171531.370 ± 7201.760  ms/op
MSQWindowFunctionsBenchmark.benchmarkTest3              2  avgt    5   62332.156 ± 1115.049  ms/op
MSQWindowFunctionsBenchmark.benchmarkTest3              5  avgt    5   68790.699 ± 4459.277  ms/op
 */
