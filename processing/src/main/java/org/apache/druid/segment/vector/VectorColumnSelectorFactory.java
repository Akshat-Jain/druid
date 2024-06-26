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

package org.apache.druid.segment.vector;

import org.apache.druid.query.dimension.DimensionSpec;
import org.apache.druid.query.groupby.DeferExpressionDimensions;
import org.apache.druid.query.groupby.epinephelinae.vector.GroupByVectorColumnProcessorFactory;
import org.apache.druid.query.groupby.epinephelinae.vector.GroupByVectorColumnSelector;
import org.apache.druid.segment.ColumnInspector;
import org.apache.druid.segment.ColumnProcessors;
import org.apache.druid.segment.column.ColumnCapabilities;

import javax.annotation.Nullable;

/**
 * A class that comes from {@link VectorCursor#getColumnSelectorFactory()} and is used to create vector selectors.
 *
 * If you need to write code that adapts to different input types, you should write a
 * {@link org.apache.druid.segment.VectorColumnProcessorFactory} and use one of the
 * {@link ColumnProcessors#makeVectorProcessor} functions instead of using this class.
 *
 * @see org.apache.druid.segment.ColumnSelectorFactory the non-vectorized version.
 */
public interface VectorColumnSelectorFactory extends ColumnInspector
{
  /**
   * Returns a {@link ReadableVectorInspector} for the {@link VectorCursor} that generated this object.
   */
  ReadableVectorInspector getReadableVectorInspector();

  /**
   * Returns the maximum vector size for the {@link VectorCursor} that generated this object.
   *
   * @see VectorCursor#getMaxVectorSize()
   */
  default int getMaxVectorSize()
  {
    return getReadableVectorInspector().getMaxVectorSize();
  }

  /**
   * Returns a dictionary encoded, string-typed, single-value-per-row column selector. Should only be called on columns
   * where {@link #getColumnCapabilities} indicates they return STRING, or on nonexistent columns. Since the selector
   * created with this method operates directly on the dictionary encoded input, STRING values must be translated from
   * the dictionary id for a given row value using {@link SingleValueDimensionVectorSelector#lookupName(int)}, but
   * this selector can prove optimal for operations which can be done directly on the underlying dictionary ids, such
   * as grouping within a segment.
   *
   * If you need to write code that adapts to different input types, you should write a
   * {@link org.apache.druid.segment.VectorColumnProcessorFactory} and use one of the
   * {@link ColumnProcessors#makeVectorProcessor} functions instead of using this method.
   */
  SingleValueDimensionVectorSelector makeSingleValueDimensionSelector(DimensionSpec dimensionSpec);

  /**
   * Returns a dictionary encoded, string-typed, multi-value-per-row column selector. Should only be called on columns
   * where {@link #getColumnCapabilities} indicates they return STRING. Unlike
   * {@link #makeSingleValueDimensionSelector}, this should not be called on nonexistent columns.
   *
   * If you need to write code that adapts to different input types, you should write a
   * {@link org.apache.druid.segment.VectorColumnProcessorFactory} and use one of the
   * {@link ColumnProcessors#makeVectorProcessor} functions instead of using this method.
   */
  MultiValueDimensionVectorSelector makeMultiValueDimensionSelector(DimensionSpec dimensionSpec);

  /**
   * Returns a primitive column selector. Should only be called on columns where {@link #getColumnCapabilities}
   * indicates they return DOUBLE, FLOAT, or LONG, or on nonexistent columns.
   *
   * If you need to write code that adapts to different input types, you should write a
   * {@link org.apache.druid.segment.VectorColumnProcessorFactory} and use one of the
   * {@link ColumnProcessors#makeVectorProcessor} functions instead of using this method.
   */
  VectorValueSelector makeValueSelector(String column);

  /**
   * Returns an object selector. Should only be called on columns where {@link #getColumnCapabilities} indicates that
   * they return STRING, ARRAY, or COMPLEX, or on nonexistent columns.
   *
   * For STRING, this is needed if values are not dictionary encoded, such as computed virtual columns, or can
   * optionally be used in place of {@link SingleValueDimensionVectorSelector} when using the dictionary isn't helpful.
   * Currently, this should only be called on single valued STRING inputs (multi-value STRING vector object selector
   * is not yet implemented).
   *
   * If you need to write code that adapts to different input types, you should write a
   * {@link org.apache.druid.segment.VectorColumnProcessorFactory} and use one of the
   * {@link ColumnProcessors#makeVectorProcessor} functions instead of using this method.
   */
  VectorObjectSelector makeObjectSelector(String column);

  /**
   * Returns capabilities of a particular column, or null if the column doesn't exist. Unlike ColumnSelectorFactory,
   * null does not potentially indicate a dynamically discovered column.
   *
   * @return capabilities, or null if the column doesn't exist.
   */
  @Override
  @Nullable
  ColumnCapabilities getColumnCapabilities(String column);

  /**
   * Returns a group-by selector. Allows columns to control their own grouping behavior.
   *
   * @param column                    column name
   * @param deferExpressionDimensions active value of {@link org.apache.druid.query.groupby.GroupByQueryConfig#CTX_KEY_DEFER_EXPRESSION_DIMENSIONS}
   */
  default GroupByVectorColumnSelector makeGroupByVectorColumnSelector(
      String column,
      DeferExpressionDimensions deferExpressionDimensions
  )
  {
    return ColumnProcessors.makeVectorProcessor(
        column,
        GroupByVectorColumnProcessorFactory.instance(),
        this
    );
  }
}
