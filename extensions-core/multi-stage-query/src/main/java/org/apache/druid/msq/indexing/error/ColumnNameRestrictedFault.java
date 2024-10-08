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

package org.apache.druid.msq.indexing.error;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.Preconditions;
import org.apache.druid.error.DruidException;
import org.apache.druid.java.util.common.StringUtils;

import java.util.Objects;

@JsonTypeName(ColumnNameRestrictedFault.CODE)
public class ColumnNameRestrictedFault extends BaseMSQFault
{
  static final String CODE = "ColumnNameRestricted";

  private final String columnName;

  @JsonCreator
  public ColumnNameRestrictedFault(
      @JsonProperty("columnName") final String columnName
  )
  {
    super(CODE, StringUtils.format(
        "[%s] column name is reserved for MSQ's internal purpose. Please retry the query after renaming the column.",
        columnName));
    this.columnName = Preconditions.checkNotNull(columnName, "columnName");
  }

  @JsonProperty
  public String getColumnName()
  {
    return columnName;
  }

  @Override
  public DruidException toDruidException()
  {
    return DruidException.forPersona(DruidException.Persona.USER)
                         .ofCategory(DruidException.Category.INVALID_INPUT)
                         .build(MSQFaultUtils.generateMessageWithErrorCode(this));
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    ColumnNameRestrictedFault that = (ColumnNameRestrictedFault) o;
    return Objects.equals(columnName, that.columnName);
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(super.hashCode(), columnName);
  }
}
