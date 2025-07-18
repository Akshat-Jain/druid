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

package org.apache.druid.frame.write.columnar;

import com.google.common.base.Preconditions;
import org.apache.druid.frame.FrameType;
import org.apache.druid.frame.allocation.AppendableMemory;
import org.apache.druid.frame.allocation.MemoryAllocator;
import org.apache.druid.frame.allocation.MemoryAllocatorFactory;
import org.apache.druid.frame.key.KeyColumn;
import org.apache.druid.frame.write.FrameWriter;
import org.apache.druid.frame.write.FrameWriterFactory;
import org.apache.druid.frame.write.FrameWriterUtils;
import org.apache.druid.frame.write.UnsupportedColumnTypeException;
import org.apache.druid.java.util.common.IAE;
import org.apache.druid.segment.ColumnSelectorFactory;
import org.apache.druid.segment.column.ColumnType;
import org.apache.druid.segment.column.RowSignature;
import org.apache.druid.utils.CloseableUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ColumnarFrameWriterFactory implements FrameWriterFactory
{
  private final MemoryAllocatorFactory allocatorFactory;
  private final RowSignature signature;
  private final List<KeyColumn> keyColumns;

  /**
   * Create a ColumnarFrameWriterFactory.
   *
   * @param allocatorFactory memory allocator; will use as much as possible
   * @param signature        output signature for this frame writer
   * @param keyColumns       columns to sort by, if any. May be empty.
   *
   * @throws UnsupportedColumnTypeException if "signature" contains any type that we cannot handle
   */
  public ColumnarFrameWriterFactory(
      final MemoryAllocatorFactory allocatorFactory,
      final RowSignature signature,
      final List<KeyColumn> keyColumns
  )
  {
    this.allocatorFactory = Preconditions.checkNotNull(allocatorFactory, "allocatorFactory");
    this.signature = signature;
    this.keyColumns = Preconditions.checkNotNull(keyColumns, "sortColumns");

    if (!keyColumns.isEmpty()) {
      throw new IAE("Columnar frames cannot be sorted");
    }

    // Check for disallowed field names.
    final Set<String> disallowedFieldNames = FrameWriterUtils.findDisallowedFieldNames(signature);
    if (!disallowedFieldNames.isEmpty()) {
      throw new IAE("Disallowed field names: %s", disallowedFieldNames);
    }
  }

  @Override
  public FrameWriter newFrameWriter(final ColumnSelectorFactory columnSelectorFactory)
  {
    final MemoryAllocator allocator = allocatorFactory.newAllocator();
    final List<FrameColumnWriter> columnWriters = new ArrayList<>();

    try {
      for (int i = 0; i < signature.size(); i++) {
        final String column = signature.getColumnName(i);
        // note: null type won't work, but we'll get a nice error from FrameColumnWriters.create
        final ColumnType columnType = signature.getColumnType(i).orElse(null);
        columnWriters.add(FrameColumnWriters.create(columnSelectorFactory, allocator, column, columnType));
      }
    }
    catch (Throwable e) {
      // FrameColumnWriters.create can throw exceptions. If this happens, we need to close previously-created writers.
      throw CloseableUtils.closeAndWrapInCatch(e, () -> CloseableUtils.closeAll(columnWriters));
    }

    // Only need rowOrderMemory if we are sorting.
    final AppendableMemory rowOrderMemory = keyColumns.isEmpty() ? null : AppendableMemory.create(allocator);

    return new ColumnarFrameWriter(
        signature,
        keyColumns,
        rowOrderMemory,
        columnWriters
    );
  }

  @Override
  public long allocatorCapacity()
  {
    return allocatorFactory.allocatorCapacity();
  }

  @Override
  public RowSignature signature()
  {
    return signature;
  }

  @Override
  public FrameType frameType()
  {
    return FrameType.latestColumnar();
  }
}
