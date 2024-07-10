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

package org.apache.druid.msq.querykit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.apache.druid.frame.key.ClusterBy;
import org.apache.druid.frame.key.KeyColumn;
import org.apache.druid.frame.key.KeyOrder;
import org.apache.druid.msq.exec.Limits;
import org.apache.druid.msq.input.stage.StageInputSpec;
import org.apache.druid.msq.kernel.HashShuffleSpec;
import org.apache.druid.msq.kernel.QueryDefinition;
import org.apache.druid.msq.kernel.QueryDefinitionBuilder;
import org.apache.druid.msq.kernel.ShuffleSpec;
import org.apache.druid.msq.kernel.StageDefinition;
import org.apache.druid.msq.util.MultiStageQueryContext;
import org.apache.druid.query.Query;
import org.apache.druid.query.operator.ColumnWithDirection;
import org.apache.druid.query.operator.NaivePartitioningOperatorFactory;
import org.apache.druid.query.operator.NaiveSortOperatorFactory;
import org.apache.druid.query.operator.OperatorFactory;
import org.apache.druid.query.operator.WindowOperatorQuery;
import org.apache.druid.query.operator.window.WindowOperatorFactory;
import org.apache.druid.segment.column.RowSignature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WindowOperatorQueryKit implements QueryKit<WindowOperatorQuery>
{
  private final ObjectMapper jsonMapper;

  public WindowOperatorQueryKit(ObjectMapper jsonMapper)
  {
    this.jsonMapper = jsonMapper;
  }

  @Override
  public QueryDefinition makeQueryDefinition(
      String queryId,
      WindowOperatorQuery originalQuery,
      QueryKit<Query<?>> queryKit,
      ShuffleSpecFactory resultShuffleSpecFactory,
      int maxWorkerCount,
      int minStageNumber
  )
  {
    // Need to validate query first.
    // Populate the group of operators to be processed at each stage.
    // The size of the operators is the number of serialized stages.
    // Later we should also check if these can be parallelized.
    // Check if there is an empty OVER() clause or not.
    List<List<OperatorFactory>> operatorList = new ArrayList<>();
    System.out.println("originalQuery.getRowSignature() = " + originalQuery.getRowSignature());
    System.out.println("==============\nPrinting originalQuery.getOperators() = ");
    for (OperatorFactory operator : originalQuery.getOperators()) {
      System.out.println("operator = " + operator);
    }
    System.out.println("==============");
    boolean isEmptyOverFound = isEmptyOverPresentInWindowOperators(originalQuery, operatorList);
//    System.out.println("isEmptyOverFound = " + isEmptyOverFound);
    System.out.println("Printing each index of operatorList");
    for (List<OperatorFactory> operatorFactories : operatorList) {
      System.out.println("operatorFactories = " + operatorFactories);
    }

    System.out.println("Printing output column names for WindowOperatorFactory in the operatorList");
    for (List<OperatorFactory> operatorFactories : operatorList) {
      for (OperatorFactory operatorFactory : operatorFactories) {
        if (operatorFactory instanceof WindowOperatorFactory) {
          System.out.println("((WindowOperatorFactory) operatorFactory).getProcessor().getColumnNames() = "
                             + ((WindowOperatorFactory) operatorFactory).getProcessor().getColumnNames());
        }
      }
    }

    ShuffleSpec nextShuffleSpec = findShuffleSpecForNextWindow(operatorList.get(0), maxWorkerCount);
//    System.out.println("nextShuffleSpec 1 = " + nextShuffleSpec);
    // add this shuffle spec to the last stage of the inner query
    // Question: What does "inner query" mean?

    final QueryDefinitionBuilder queryDefBuilder = QueryDefinition.builder(queryId);
    if (nextShuffleSpec != null) {
      final ClusterBy windowClusterBy = nextShuffleSpec.clusterBy();
//      System.out.println("windowClusterBy = " + windowClusterBy);
      originalQuery = (WindowOperatorQuery) originalQuery.withOverriddenContext(ImmutableMap.of(
          MultiStageQueryContext.NEXT_WINDOW_SHUFFLE_COL,
          windowClusterBy
      ));
    }
    final DataSourcePlan dataSourcePlan = DataSourcePlan.forDataSource(
        queryKit,
        queryId,
        originalQuery.context(),
        originalQuery.getDataSource(),
        originalQuery.getQuerySegmentSpec(),
        originalQuery.getFilter(),
        null,
        maxWorkerCount,
        minStageNumber,
        false
    );

//    dataSourcePlan.getSubQueryDefBuilder().get().getStageBuilder().getSignature()
    dataSourcePlan.getSubQueryDefBuilder().ifPresent(queryDefBuilder::addAll);

    final int firstStageNumber = Math.max(minStageNumber, queryDefBuilder.getNextStageNumber());
    final WindowOperatorQuery queryToRun = (WindowOperatorQuery) originalQuery.withDataSource(dataSourcePlan.getNewDataSource());
    final int maxRowsMaterialized;
    RowSignature rowSignature = queryToRun.getRowSignature();
    System.out.println("rowSignature = " + rowSignature);
//    System.out.println("CHECK originalQuery.getRowSignature() = " + originalQuery.getRowSignature());
//    System.out.println("CHECK queryToRun.getRowSignature() = " + rowSignature);
    if (originalQuery.context() != null && originalQuery.context().containsKey(MultiStageQueryContext.MAX_ROWS_MATERIALIZED_IN_WINDOW)) {
      maxRowsMaterialized = (int) originalQuery.context().get(MultiStageQueryContext.MAX_ROWS_MATERIALIZED_IN_WINDOW);
    } else {
      maxRowsMaterialized = Limits.MAX_ROWS_MATERIALIZED_IN_WINDOW;
    }


    if (isEmptyOverFound) {
      // empty over clause found
      // moving everything to a single partition
      // Question: Why? Is this why we get IndexOutOfBoundsException when mixing empty over() with non empty over()?
      queryDefBuilder.add(
          StageDefinition.builder(firstStageNumber)
                         .inputs(new StageInputSpec(firstStageNumber - 1))
                         .signature(rowSignature)
                         .maxWorkerCount(maxWorkerCount)
                         .shuffleSpec(null)
                         .processorFactory(new WindowOperatorQueryFrameProcessorFactory(
                             queryToRun,
                             queryToRun.getOperators(),
                             rowSignature,
                             true,
                             maxRowsMaterialized,
                             new ArrayList<>()
                         ))
      );
    } else {
      // There are multiple windows present in the query.
      // Create stages for each window in the query.
      // These stages will be serialized.
      // The partition by clause of the next window will be the shuffle key for the previous window.
      RowSignature.Builder bob = RowSignature.builder();
//      final int numberOfWindows = operatorList.size();
//      final int baseSize = rowSignature.size() - numberOfWindows;
//      System.out.println("baseSize = " + baseSize);
//      for (int i = 0; i < baseSize; i++) {
//        bob.add(rowSignature.getColumnName(i), rowSignature.getColumnType(i).get());
//      }

      // row signature = s1, s2, s3, s4, s5
//      RowSignature signatureFromInput = dataSourcePlan.getSubQueryDefBuilder()
//                                             .get()
//                                             .build()
//                                             .getStageDefinitions()
//                                             .get(1)
//                                             .getSignature();
//      StageDefinition finalStageDefinition = null;
//      int indexForHighestStageNumber = -1;
//      int currentMaxStageNumber = -1;
//      for (StageDefinition stageDefinition : dataSourcePlan.getSubQueryDefBuilder()
//                                                           .get()
//                                                           .build()
//                                                           .getStageDefinitions()) {
//        if (stageDefinition.getStageNumber() > currentMaxStageNumber) {
//          currentMaxStageNumber = stageDefinition.getStageNumber();
//          finalStageDefinition = stageDefinition;
//        }
//      }
//      RowSignature signatureFromInput = finalStageDefinition.getSignature();
      RowSignature signatureFromInput = dataSourcePlan.getSubQueryDefBuilder().get().build().getFinalStageDefinition()
                                                      .getSignature();
      System.out.println("signatureFromInput = " + signatureFromInput);

      for (int i = 0; i < signatureFromInput.getColumnNames().size(); i++) {
        bob.add(signatureFromInput.getColumnName(i), signatureFromInput.getColumnType(i).get());
      }

      // todo: bob = dataSourcePlan.getSubQueryDefBuilder() -> last stage's row signature

      // try printing nextShuffleSpec.clusterBy().getColumns() and see if it could be used to populate partitionColumnsNames
      List<String> partitionColumnsNames = new ArrayList<>();
//      partitionColumnsNames.add(0);

      /*
      test_frameclause_subQueries_frmInSubQry_53

            SELECT
                  *
            FROM
            (
                SELECT
                    COUNT(c1) OVER W as count_c1 ,
                    COUNT(c2) OVER W as count_c2 ,
                    COUNT(c3) OVER W as count_c3 ,
                    COUNT(c4) OVER W as count_c4 ,
                    COUNT(c5) OVER W as count_c5 ,
                    COUNT(c6) OVER W as count_c6 ,
                    COUNT(c7) OVER W as count_c7 ,
                    COUNT(c8) OVER W as count_c8 ,
                    COUNT(c9) OVER W as count_c9
                FROM "t_alltype.parquet"
                    WINDOW W AS ( PARTITION BY c8 ORDER BY c1 RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING )
            ) subQry

            ==============
            Printing originalQuery.getOperators() =
            operator = NaiveSortOperatorFactory{sortColumns=[ColumnWithDirection{columnName='c8', direction=ASC}, ColumnWithDirection{columnName='c1', direction=ASC}]}
            operator = NaivePartitioningOperatorFactory{partitionColumns=[c8]}
            operator = WindowOperatorFactory{processor=WindowFramedAggregateProcessor{frame=WindowFrame{peerType=ROWS, lowerUnbounded=true, lowerOffset=0, upperUnbounded=true, upperOffset=0, orderBy=null}, aggregations=[FilteredAggregatorFactory{delegate=CountAggregatorFactory{name='w0'}, dimFilter=!c1 IS NULL, name='w0'}, FilteredAggregatorFactory{delegate=CountAggregatorFactory{name='w1'}, dimFilter=!c2 IS NULL, name='w1'}, FilteredAggregatorFactory{delegate=CountAggregatorFactory{name='w2'}, dimFilter=!c3 IS NULL, name='w2'}, FilteredAggregatorFactory{delegate=CountAggregatorFactory{name='w3'}, dimFilter=!c4 IS NULL, name='w3'}, FilteredAggregatorFactory{delegate=CountAggregatorFactory{name='w4'}, dimFilter=!c5 IS NULL, name='w4'}, FilteredAggregatorFactory{delegate=CountAggregatorFactory{name='w5'}, dimFilter=!c6 IS NULL, name='w5'}, FilteredAggregatorFactory{delegate=CountAggregatorFactory{name='w6'}, dimFilter=!c7 IS NULL, name='w6'}, FilteredAggregatorFactory{delegate=CountAggregatorFactory{name='w7'}, dimFilter=!c8 IS NULL, name='w7'}, FilteredAggregatorFactory{delegate=CountAggregatorFactory{name='w8'}, dimFilter=!c9 IS NULL, name='w8'}]}}
            ==============

            numberOfWindows=1 is wrong, or shouldn't be used to determine which column names to append to the row signature.
            baseSize is also wrong.
       */

      for (int i = 0; i < operatorList.size(); i++) {
        System.out.println("\ni = " + i);
        System.out.println("bob.build() before adding columns to it = " + bob.build());
        for (OperatorFactory operatorFactory : operatorList.get(i)) {
          if (operatorFactory instanceof WindowOperatorFactory) {
            List<String> columnNames = ((WindowOperatorFactory) operatorFactory).getProcessor().getColumnNames();

            // Need to add column names which are present in columnNames and rowSignature but not in bob
            for (String columnName : columnNames) {
              int indexInRowSignature = rowSignature.indexOf(columnName);
              if (indexInRowSignature != -1 && bob.build().indexOf(columnName) == -1) {
                bob.add(rowSignature.getColumnName(indexInRowSignature), rowSignature.getColumnType(indexInRowSignature).get()).build();
              }
            }
          }
        }
        System.out.println("bob.build() after adding columns to it = " + bob.build());

//        bob.add(rowSignature.getColumnName(baseSize + i), rowSignature.getColumnType(baseSize + i).get()).build();
        // find the shuffle spec of the next stage
        // if it is the last stage set the next shuffle spec to single partition
//        System.out.println("CHECK bob.build() = " + bob.build());
        if (i + 1 == operatorList.size()) {
          // todo: this can be replaced with ShuffleSpec.Mix directly
          nextShuffleSpec = ShuffleSpecFactories.singlePartition()
                                                .build(ClusterBy.none(), false);
        } else {
          nextShuffleSpec = findShuffleSpecForNextWindow(operatorList.get(i + 1), maxWorkerCount);
        }

//        boolean partitionOperatorExists = false;
//        List<Integer> currentPartitionColumns = new ArrayList<>();
//        for (OperatorFactory of : operatorList.get(i)) {
//          if (of instanceof NaivePartitioningOperatorFactory) {
//            for (String s : ((NaivePartitioningOperatorFactory) of).getPartitionColumns()) {
//              currentPartitionColumns.add(bob.build().indexOf(s)); // use bob here
//              partitionOperatorExists = true;
//            }
//          }
//        }
//
//        System.out.println("partitionColumnsNames = " + partitionColumnsNames);
//        System.out.println("partitionOperatorExists = " + partitionOperatorExists);
//
//        if (partitionOperatorExists) {
//          partitionColumnsNames = currentPartitionColumns;
//        }

//        System.out.println("partitionColumnsNames = " + partitionColumnsNames);

//        System.out.println("nextShuffleSpec 2 = " + nextShuffleSpec);

        final RowSignature intermediateSignature = bob.build();
        System.out.println("intermediateSignature = " + intermediateSignature);
        final RowSignature stageRowSignature;
        if (nextShuffleSpec == null) {
          stageRowSignature = intermediateSignature;
        } else {
          stageRowSignature = QueryKitUtils.sortableSignature(
              intermediateSignature,
              nextShuffleSpec.clusterBy().getColumns()
          );
        }
        System.out.println("stageRowSignature = " + stageRowSignature);

        boolean partitionOperatorExists = false;
        List<String> currentPartitionColumns = new ArrayList<>();
        for (OperatorFactory of : operatorList.get(i)) {
          if (of instanceof NaivePartitioningOperatorFactory) {
            for (String s : ((NaivePartitioningOperatorFactory) of).getPartitionColumns()) {
              currentPartitionColumns.add(s); // using stageRowSignature here instead of bob
              partitionOperatorExists = true;
            }
          }
        }

        if (partitionOperatorExists) {
          partitionColumnsNames = currentPartitionColumns;
        }

        // window 1: p1, s1
        // window 2: p1, s1
        //
        // window 4: p2, s2
        // window 5: p2, s1
        // window 6: <p1, p3>, s1

        // window 1, 2, 5, 4 should be put together by Windowing.java

//        [0]
//        PartitionOp
//            Sortop
//        WindowOp
//        WindowOP
//            [1]
//        WindowOP

        System.out.println("partitionColumnsNames being passed to WindowOperatorQueryFrameProcessorFactory = " + partitionColumnsNames);

        queryDefBuilder.add(
            StageDefinition.builder(firstStageNumber + i)
                           .inputs(new StageInputSpec(firstStageNumber + i - 1))
                           .signature(stageRowSignature)
                           .maxWorkerCount(maxWorkerCount)
                           .shuffleSpec(nextShuffleSpec)
                           .processorFactory(new WindowOperatorQueryFrameProcessorFactory(
                               queryToRun,
                               operatorList.get(i), // todo: pass the 2nd window also in the same stage
                               stageRowSignature,
                               false,
                               maxRowsMaterialized,
                               partitionColumnsNames
                           ))
        );
      }
    }
    return queryDefBuilder.build();
  }

  /**
   *
   * @param originalQuery
   * @param operatorList
   * @return true if the operator List has a partitioning operator with an empty OVER clause, false otherwise
   */
  private boolean isEmptyOverPresentInWindowOperators(
      WindowOperatorQuery originalQuery,
      List<List<OperatorFactory>> operatorList
  )
  {
    // todo: add a new list if the operator is a naive sort or partition operator
    final List<OperatorFactory> operators = originalQuery.getOperators();
    List<OperatorFactory> operatorFactoryList = new ArrayList<>();
    for (OperatorFactory of : operators) {
      operatorFactoryList.add(of);
      if (of instanceof WindowOperatorFactory) {
        // Question: Why are we adding groups of operators ending with WindowOperatorFactory separately?
        operatorList.add(operatorFactoryList);
        operatorFactoryList = new ArrayList<>();
      } else if (of instanceof NaivePartitioningOperatorFactory) {
        if (((NaivePartitioningOperatorFactory) of).getPartitionColumns().isEmpty()) {
          // Question: Why are we clearing everything on encountering an empty OVER()?
          operatorList.clear();
          operatorList.add(originalQuery.getOperators());
          return true;
        }
      }
    }
//    final List<OperatorFactory> operators = originalQuery.getOperators();
//    List<OperatorFactory> operatorFactoryList = new ArrayList<>();
//    for (OperatorFactory of : operators) {
//      operatorFactoryList.add(of);
//      if (of instanceof WindowOperatorFactory) {
//        // Question: Why are we adding groups of operators ending with WindowOperatorFactory separately?
//        operatorList.add(operatorFactoryList);
//        operatorFactoryList = new ArrayList<>();
//      } else if (of instanceof NaivePartitioningOperatorFactory) {
//        if (((NaivePartitioningOperatorFactory) of).getPartitionColumns().isEmpty()) {
//          // Question: Why are we clearing everything on encountering an empty OVER()?
//          operatorList.clear();
//          operatorList.add(originalQuery.getOperators());
//          return true;
//        }
//      }
//    }
    return false;
  }

  private ShuffleSpec findShuffleSpecForNextWindow(List<OperatorFactory> operatorFactories, int maxWorkerCount)
  {
//    System.out.println("WindowOperatorQueryKit.findShuffleSpecForNextWindow");
    NaivePartitioningOperatorFactory partition = null;
    NaiveSortOperatorFactory sort = null;
    for (OperatorFactory of : operatorFactories) {
//      System.out.println("of.hashCode() = " + of.hashCode());
      if (of instanceof NaivePartitioningOperatorFactory) {
        partition = (NaivePartitioningOperatorFactory) of;
      } else if (of instanceof NaiveSortOperatorFactory) {
        sort = (NaiveSortOperatorFactory) of;
      }
    }

    Map<String, ColumnWithDirection.Direction> sortColumnsMap = new HashMap<>();
    if (sort != null) {
      for (ColumnWithDirection sortColumn : sort.getSortColumns()) {
        sortColumnsMap.put(sortColumn.getColumn(), sortColumn.getDirection());
      }
    }
//    assert partition != null;
    if (partition == null || partition.getPartitionColumns().isEmpty()) {
      // Question: Does this indicate to keep the shuffle spec from previous stage?
      return null;
    }

    List<KeyColumn> keyColsOfWindow = new ArrayList<>();
    for (String partitionColumn : partition.getPartitionColumns()) {
      KeyColumn kc;
      if (sortColumnsMap.containsKey(partitionColumn)) {
        if (sortColumnsMap.get(partitionColumn) == ColumnWithDirection.Direction.ASC) {
          kc = new KeyColumn(partitionColumn, KeyOrder.ASCENDING);
        } else {
          kc = new KeyColumn(partitionColumn, KeyOrder.DESCENDING);
        }
      } else {
        kc = new KeyColumn(partitionColumn, KeyOrder.ASCENDING);
      }
      keyColsOfWindow.add(kc);
    }

//    System.out.println("keyColsOfWindow = " + keyColsOfWindow);
    return new HashShuffleSpec(new ClusterBy(keyColsOfWindow, 0), maxWorkerCount);
  }
}
