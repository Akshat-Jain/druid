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

package org.apache.druid.benchmark.compression;

import com.google.common.collect.ImmutableList;
import org.apache.druid.java.util.common.logger.Logger;
import org.apache.druid.segment.column.ValueType;
import org.apache.druid.segment.data.ColumnarLongsSerializer;
import org.apache.druid.segment.data.CompressionFactory;
import org.apache.druid.segment.data.CompressionStrategy;
import org.apache.druid.segment.generator.ColumnValueGenerator;
import org.apache.druid.segment.generator.GeneratorColumnSchema;
import org.apache.druid.segment.writeout.OffHeapMemorySegmentWriteOutMedium;
import org.apache.druid.segment.writeout.SegmentWriteOutMedium;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LongCompressionBenchmarkFileGenerator
{
  private static final Logger log = new Logger(LongCompressionBenchmarkFileGenerator.class);
  public static final int ROW_NUM = 5000000;
  public static final List<CompressionStrategy> COMPRESSIONS =
      ImmutableList.of(
          CompressionStrategy.LZ4,
          CompressionStrategy.NONE);
  public static final List<CompressionFactory.LongEncodingStrategy> ENCODINGS =
      ImmutableList.of(CompressionFactory.LongEncodingStrategy.AUTO, CompressionFactory.LongEncodingStrategy.LONGS);

  private static String dirPath = "longCompress/";

  public static void main(String[] args) throws IOException
  {
    if (args.length >= 1) {
      dirPath = args[0];
    }

    GeneratorColumnSchema enumeratedSchema = GeneratorColumnSchema.makeEnumerated(
        "",
        ValueType.LONG,
        true,
        1,
        0d,
        ImmutableList.of(
            0,
            1,
            2,
            3,
            4
        ),
        ImmutableList.of(
            0.95,
            0.001,
            0.0189,
            0.03,
            0.0001
        )
    );
    GeneratorColumnSchema zipfLowSchema = GeneratorColumnSchema.makeZipf("", ValueType.LONG, true, 1, 0d, -1, 1000, 1d);
    GeneratorColumnSchema zipfHighSchema = GeneratorColumnSchema.makeZipf(
        "",
        ValueType.LONG,
        true,
        1,
        0d,
        -1,
        1000,
        3d
    );
    GeneratorColumnSchema sequentialSchema = GeneratorColumnSchema.makeSequential(
        "",
        ValueType.LONG,
        true,
        1,
        0d,
        1470187671,
        2000000000
    );
    GeneratorColumnSchema uniformSchema = GeneratorColumnSchema.makeDiscreteUniform(
        "",
        ValueType.LONG,
        true,
        1,
        0d,
        0,
        1000
    );

    Map<String, ColumnValueGenerator> generators = new HashMap<>();
    generators.put("enumerate", new ColumnValueGenerator(enumeratedSchema, 1));
    generators.put("zipfLow", new ColumnValueGenerator(zipfLowSchema, 1));
    generators.put("zipfHigh", new ColumnValueGenerator(zipfHighSchema, 1));
    generators.put("sequential", new ColumnValueGenerator(sequentialSchema, 1));
    generators.put("uniform", new ColumnValueGenerator(uniformSchema, 1));

    File dir = new File(dirPath);
    dir.mkdir();

    // create data files using BenchmarkColunValueGenerator
    for (Map.Entry<String, ColumnValueGenerator> entry : generators.entrySet()) {
      final File dataFile = new File(dir, entry.getKey());
      dataFile.delete();
      try (Writer writer = Files.newBufferedWriter(dataFile.toPath(), StandardCharsets.UTF_8)) {
        for (int i = 0; i < ROW_NUM; i++) {
          writer.write((long) entry.getValue().generateRowValue() + "\n");
        }
      }
    }

    // create compressed files using all combinations of CompressionStrategy and LongEncoding provided
    for (Map.Entry<String, ColumnValueGenerator> entry : generators.entrySet()) {
      for (CompressionStrategy compression : COMPRESSIONS) {
        for (CompressionFactory.LongEncodingStrategy encoding : ENCODINGS) {
          String name = entry.getKey() + "-" + compression + "-" + encoding;
          log.info("%s: ", name);
          File compFile = new File(dir, name);
          compFile.delete();
          File dataFile = new File(dir, entry.getKey());

          SegmentWriteOutMedium segmentWriteOutMedium = new OffHeapMemorySegmentWriteOutMedium();
          ColumnarLongsSerializer writer = CompressionFactory.getLongSerializer(
              "long-benchmark",
              segmentWriteOutMedium,
              "long",
              ByteOrder.nativeOrder(),
              encoding,
              compression,
              segmentWriteOutMedium.getCloser()
          );
          try (
              BufferedReader br = Files.newBufferedReader(dataFile.toPath(), StandardCharsets.UTF_8);
              FileChannel output =
                  FileChannel.open(compFile.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
          ) {
            writer.open();
            String line;
            while ((line = br.readLine()) != null) {
              writer.add(Long.parseLong(line));
            }
            writer.writeTo(output, null);
          }
          log.info("%d", compFile.length() / 1024);
        }
      }
    }
  }
}
