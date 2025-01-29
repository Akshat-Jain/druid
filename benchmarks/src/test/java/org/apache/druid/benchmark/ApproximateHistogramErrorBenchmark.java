package org.apache.druid.benchmark;

import org.apache.druid.query.aggregation.histogram.ApproximateHistogram;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.Random;
import com.google.common.primitives.Floats;
import org.apache.druid.query.aggregation.Histogram;

@State(Scope.Thread)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(value = 1)
@Warmup(iterations = 1)
@Measurement(iterations = 1)
public class ApproximateHistogramErrorBenchmark {

  @Param({"10", "100", "1000", "10000", "100000"})
  private int numHists;

  @Param({"50"})
  private int numPerHist;

  @Param({"50"})
  private int resolution;

  @Param({"100"})
  private int combinedResolution;

  private final int numBuckets = 20;
  private final int numBreaks = numBuckets + 1;
  private final Random rand = new Random(2);

  @Benchmark
  public void measureErrors(Blackhole blackhole) {
    float[] tmp = getErrors();
    System.out.printf(Locale.ENGLISH, "Errors for approximate histogram for numHists = %d: %f\n", numHists, tmp[0]);
    System.out.printf(Locale.ENGLISH, "Errors for approximate histogram for histogram, ruleFold = %d: %f\n", numHists, tmp[1]);

    blackhole.consume(tmp);
  }

  private float[] getErrors()
  {
    final int numValues = numHists * numPerHist;
    final float[] values = new float[numValues];

    for (int i = 0; i < numValues; ++i) {
      values[i] = (float) rand.nextGaussian();
    }

    float min = Floats.min(values);
    min = (float) (min < 0 ? 1.02 : .98) * min;
    float max = Floats.max(values);
    max = (float) (max < 0 ? .98 : 1.02) * max;
    final float stride = (max - min) / numBuckets;
    final float[] breaks = new float[numBreaks];
    for (int i = 0; i < numBreaks; i++) {
      breaks[i] = min + stride * i;
    }

    Histogram h = new Histogram(breaks);
    for (float v : values) {
      h.offer(v);
    }
    double[] hcounts = h.asVisual().counts;

    ApproximateHistogram ah1 = new ApproximateHistogram(resolution);
    ApproximateHistogram ah2 = new ApproximateHistogram(combinedResolution);
    ApproximateHistogram tmp = new ApproximateHistogram(resolution);
    for (int i = 0; i < numValues; ++i) {
      tmp.offer(values[i]);
      if ((i + 1) % numPerHist == 0) {
        ah1.fold(tmp, null, null, null);
        ah2.foldRule(tmp, null, null);
        tmp = new ApproximateHistogram(resolution);
      }
    }
    double[] ahcounts1 = ah1.toHistogram(breaks).getCounts();
    double[] ahcounts2 = ah2.toHistogram(breaks).getCounts();

    float err1 = 0;
    float err2 = 0;
    for (int j = 0; j < hcounts.length; j++) {
      err1 += (float) Math.abs((hcounts[j] - ahcounts1[j]) / numValues);
      err2 += (float) Math.abs((hcounts[j] - ahcounts2[j]) / numValues);
    }
    return new float[]{err1, err2, err2 / err1};
  }
}
