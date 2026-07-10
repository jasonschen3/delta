/*
 * Copyright (2023) The Delta Lake Project Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.delta.kernel.internal.checkpoints;

import io.delta.kernel.data.ArrayValue;
import io.delta.kernel.data.MapValue;
import io.delta.kernel.data.Row;
import io.delta.kernel.internal.util.JsonUtils;
import io.delta.kernel.types.FieldMetadata;
import io.delta.kernel.types.StringType;
import io.delta.kernel.types.StructField;
import io.delta.kernel.types.StructType;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * A serialized view of a {@code _last_checkpoint} pointer: a parsed {@link
 * CheckpointMetaData} plus the pointer's {@code checkpointSchema} carried as its verbatim JSON
 * text.
 *
 * <p>This type is deliberately separate from {@link CheckpointMetaData} so that the generic,
 * widely-used {@link CheckpointMetaData} stays free of a String-typed schema field. The {@code
 * checkpointSchema} of a {@code _last_checkpoint} is a recursive, polymorphic schema-of-schema
 * object that the columnar JSON reader cannot project into a fixed schema. So, it can only be
 * obtained as raw text. Consumers that need the exact on-pointer text (e.g. re-caching the pointer
 * elsewhere) use this type.
 */
public class CheckpointMetaDataSerialized {

  /**
   * Schema used to read a {@code _last_checkpoint} pointer with its raw {@code checkpointSchema}.
   */
  public static final StructType READ_SCHEMA = buildReadSchema();

  private static final int CHECKPOINT_SCHEMA_ORDINAL =
      CheckpointMetaData.READ_SCHEMA.length(); // the last ordinal

  private static StructType buildReadSchema() {
    StructType schema = new StructType();
    for (StructField field : CheckpointMetaData.READ_SCHEMA.fields()) {
      schema = schema.add(field);
    }
    return schema.add(
        "checkpointSchema",
        StringType.STRING,
        true /* nullable */,
        FieldMetadata.builder().putBoolean(JsonUtils.RAW_JSON_FIELD_METADATA_KEY, true).build());
  }

  /**
   * Builds a {@link CheckpointMetaDataSerialized} from a {@link Row} read using {@link
   * #READ_SCHEMA}. The base fields are delegated to {@link CheckpointMetaData#fromRow} and
   * {@code checkpointSchema} is captured as its raw JSON text.
   */
  public static CheckpointMetaDataSerialized fromRow(Row row) {
    CheckpointMetaData base = CheckpointMetaData.fromRow(row);
    Optional<String> checkpointSchemaJson =
        row.isNullAt(CHECKPOINT_SCHEMA_ORDINAL)
            ? Optional.empty()
            : Optional.of(row.getString(CHECKPOINT_SCHEMA_ORDINAL));
    return new CheckpointMetaDataSerialized(base, checkpointSchemaJson);
  }

  private final CheckpointMetaData checkpointMetaData;
  private final Optional<String> checkpointSchemaJson;

  public CheckpointMetaDataSerialized(
      CheckpointMetaData checkpointMetaData, Optional<String> checkpointSchemaJson) {
    this.checkpointMetaData = checkpointMetaData;
    this.checkpointSchemaJson = checkpointSchemaJson;
  }

  public CheckpointMetaData getCheckpointMetaData() {
    return checkpointMetaData;
  }

  public Optional<String> getCheckpointSchemaJson() {
    return checkpointSchemaJson;
  }

  /**
   * A {@link Row} over {@link #READ_SCHEMA}: the base {@link CheckpointMetaData} row for the
   * leading ordinals, plus the raw {@code checkpointSchema} JSON string at the last ordinal.
   * Delegating (rather than copying values out by type) keeps this robust to the base schema
   * evolving.
   */
  public Row toRow() {
    Row baseRow = checkpointMetaData.toRow();
    String checkpointSchema = checkpointSchemaJson.orElse(null);
    return new Row() {
      @Override
      public StructType getSchema() {
        return READ_SCHEMA;
      }

      @Override
      public boolean isNullAt(int ordinal) {
        return ordinal == CHECKPOINT_SCHEMA_ORDINAL
            ? checkpointSchema == null
            : baseRow.isNullAt(ordinal);
      }

      @Override
      public boolean getBoolean(int ordinal) {
        return baseRow.getBoolean(ordinal);
      }

      @Override
      public byte getByte(int ordinal) {
        return baseRow.getByte(ordinal);
      }

      @Override
      public short getShort(int ordinal) {
        return baseRow.getShort(ordinal);
      }

      @Override
      public int getInt(int ordinal) {
        return baseRow.getInt(ordinal);
      }

      @Override
      public long getLong(int ordinal) {
        return baseRow.getLong(ordinal);
      }

      @Override
      public float getFloat(int ordinal) {
        return baseRow.getFloat(ordinal);
      }

      @Override
      public double getDouble(int ordinal) {
        return baseRow.getDouble(ordinal);
      }

      @Override
      public String getString(int ordinal) {
        return ordinal == CHECKPOINT_SCHEMA_ORDINAL ? checkpointSchema : baseRow.getString(ordinal);
      }

      @Override
      public BigDecimal getDecimal(int ordinal) {
        return baseRow.getDecimal(ordinal);
      }

      @Override
      public byte[] getBinary(int ordinal) {
        return baseRow.getBinary(ordinal);
      }

      @Override
      public Row getStruct(int ordinal) {
        return baseRow.getStruct(ordinal);
      }

      @Override
      public ArrayValue getArray(int ordinal) {
        return baseRow.getArray(ordinal);
      }

      @Override
      public MapValue getMap(int ordinal) {
        return baseRow.getMap(ordinal);
      }
    };
  }

  public String toJson() {
    return JsonUtils.rowToJson(toRow());
  }

  @Override
  public String toString() {
    return "CheckpointMetaDataSerialized{"
        + "checkpointMetaData="
        + checkpointMetaData
        + ", checkpointSchemaJson="
        + checkpointSchemaJson
        + '}';
  }
}
