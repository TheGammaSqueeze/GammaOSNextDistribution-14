/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.healthconnect.cts.database;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HealthConnectDatabaseUnitTest {
    /** Sample sql query taken for unit testing. */
    static final String SAMPLE_QUERY =
            "CREATE UNIQUE INDEX idx_sleep_session_record_table_0 ON"
                    + " sleep_stages_table(stage_start_time)\n"
                    + "CREATE TABLE sleep_stages_table (parent_key INTEGER NOT NULL UNIQUE"
                    + " AUTOINCREMENT, stage_start_time INTEGER NOT NULL CHECK"
                    + "(stage_start_timer>0),"
                    + " stage_end_time INTEGER NOT NULL, stage_type INTEGER NOT NULL,  FOREIGN KEY"
                    + " (parent_key) REFERENCES application_info_table(row_id) ON DELETE CASCADE)\n"
                    + "CREATE TABLE application_info_table (row_id INTEGER PRIMARY KEY, "
                    + "package_name"
                    + " TEXT NOT NULL UNIQUE, app_name TEXT, app_icon BLOB, record_types_used TEXT"
                    + " DEFAULT 'Empty')\n";

    HashMap<String, TableInfo> mTableList = new HashMap<>();
    static final String FIRST_TABLE_NAME = "sleep_stages_table";
    static final String SECOND_TABLE_NAME = "application_info_table";
    static final String DATA_TYPE_INTEGER = "INTEGER";
    static final String DATA_TYPE_TEXT = "TEXT";
    static final String DATA_TYPE_BLOB = "BLOB";

    /** Initial setup to check whether table list is empty or not. */
    @Before
    public void setup() {
        HealthConnectDatabaseSchema schema = new HealthConnectDatabaseSchema(SAMPLE_QUERY);
        mTableList = schema.getTableInfo();
        assertThat(mTableList).isNotNull();
    }

    /** tests the list of table names. */
    @Test
    public void test_tableName_matches() {
        List<String> expectedTableNames = new ArrayList<>();
        expectedTableNames.add(FIRST_TABLE_NAME);
        expectedTableNames.add(SECOND_TABLE_NAME);

        List<String> tableNameList = mTableList.keySet().stream().toList();

        assertThat(expectedTableNames.size()).isEqualTo(tableNameList.size());
        for (String expectedName : expectedTableNames) {
            assertThat(tableNameList.contains(expectedName)).isTrue();
        }
    }

    /** tests the list of all columns for each table. */
    @Test
    public void test_column_matches() {
        HashMap<String, ColumnInfo> expectedColumnListFirstTable = new HashMap<>();

        expectedColumnListFirstTable.put(
                "parent_key",
                new ColumnInfo.Builder("parent_key", DATA_TYPE_INTEGER)
                        .setDefaultValue(null)
                        .addConstraint(ColumnInfo.UNIQUE_CONSTRAINT)
                        .addConstraint(ColumnInfo.NOT_NULL_CONSTRAINT)
                        .addConstraint(ColumnInfo.AUTO_INCREMENT_CONSTRAINT)
                        .build());

        expectedColumnListFirstTable.put(
                "stage_start_time",
                new ColumnInfo.Builder("stage_start_time", DATA_TYPE_INTEGER)
                        .setDefaultValue(null)
                        .addConstraint(ColumnInfo.NOT_NULL_CONSTRAINT)
                        .addCheckConstraint("stage_start_timer>0")
                        .build());

        expectedColumnListFirstTable.put(
                "stage_end_time",
                new ColumnInfo.Builder("stage_end_time", DATA_TYPE_INTEGER)
                        .setDefaultValue(null)
                        .addConstraint(ColumnInfo.NOT_NULL_CONSTRAINT)
                        .build());

        expectedColumnListFirstTable.put(
                "stage_type",
                new ColumnInfo.Builder("stage_type", DATA_TYPE_INTEGER)
                        .setDefaultValue(null)
                        .addConstraint(ColumnInfo.NOT_NULL_CONSTRAINT)
                        .build());

        HashMap<String, ColumnInfo> columnListFirstTable =
                mTableList.get(FIRST_TABLE_NAME).getColumnInfoMapping();

        assertThat(columnListFirstTable.size() == expectedColumnListFirstTable.size()).isTrue();

        for (Map.Entry<String, ColumnInfo> column : expectedColumnListFirstTable.entrySet()) {
            String columnName = column.getKey();
            assertThat(columnListFirstTable.containsKey(columnName)).isTrue();
            assertThat(columnListFirstTable.get(columnName).isEqual(column.getValue())).isTrue();
        }

        HashMap<String, ColumnInfo> expectedColumnListSecondTable = new HashMap<>();

        expectedColumnListSecondTable.put(
                "row_id",
                new ColumnInfo.Builder("row_id", DATA_TYPE_INTEGER).setDefaultValue(null).build());

        expectedColumnListSecondTable.put(
                "package_name",
                new ColumnInfo.Builder("package_name", DATA_TYPE_TEXT)
                        .setDefaultValue(null)
                        .addConstraint(ColumnInfo.UNIQUE_CONSTRAINT)
                        .addConstraint(ColumnInfo.NOT_NULL_CONSTRAINT)
                        .build());

        expectedColumnListSecondTable.put(
                "app_name",
                new ColumnInfo.Builder("app_name", DATA_TYPE_TEXT).setDefaultValue(null).build());

        expectedColumnListSecondTable.put(
                "app_icon",
                new ColumnInfo.Builder("app_icon", DATA_TYPE_BLOB).setDefaultValue(null).build());

        expectedColumnListSecondTable.put(
                "record_types_used",
                new ColumnInfo.Builder("record_types_used", DATA_TYPE_TEXT)
                        .setDefaultValue("'Empty'")
                        .build());

        HashMap<String, ColumnInfo> columnListSecondTable =
                mTableList.get(SECOND_TABLE_NAME).getColumnInfoMapping();

        assertThat(columnListSecondTable.size() == expectedColumnListSecondTable.size()).isTrue();

        for (Map.Entry<String, ColumnInfo> column : expectedColumnListSecondTable.entrySet()) {
            String columnName = column.getKey();
            assertThat(columnListSecondTable.containsKey(columnName)).isTrue();
            assertThat(columnListSecondTable.get(columnName).isEqual(column.getValue())).isTrue();
        }
    }

    /** tests the primary key for each table. */
    @Test
    public void test_primaryKey_matches() {

        List<String> primaryKeyFirstTable = mTableList.get(FIRST_TABLE_NAME).getPrimaryKey();
        /**
         * Directly checking whether primary key of first table is empty because expected list of
         * primary key column is empty.
         */
        assertThat(primaryKeyFirstTable).isNotNull();
        assertThat(primaryKeyFirstTable.isEmpty()).isTrue();

        List<String> expectedPrimaryKeySecondTable = new ArrayList<>();
        expectedPrimaryKeySecondTable.add("row_id");

        List<String> primaryKeySecondTable = mTableList.get(SECOND_TABLE_NAME).getPrimaryKey();

        assertThat(expectedPrimaryKeySecondTable.size() == primaryKeySecondTable.size()).isTrue();

        for (String primaryKeyColumn : expectedPrimaryKeySecondTable) {
            assertThat(primaryKeySecondTable.contains(primaryKeyColumn)).isTrue();
        }
    }

    /** tests the list of foreign keys for each table. */
    @Test
    public void test_foreignKey_matches() {
        HashMap<String, ForeignKeyInfo> expectedForeignKeyListSecondTable = new HashMap<>();
        expectedForeignKeyListSecondTable.put(
                "parent_key",
                new ForeignKeyInfo.Builder("parent_key", "application_info_table", "row_id")
                        .addFlag(ForeignKeyInfo.ON_DELETE_CASCADE)
                        .build());

        HashMap<String, ForeignKeyInfo> foreignKeyListSecondTable =
                mTableList.get(FIRST_TABLE_NAME).getForeignKeyMapping();

        assertThat(expectedForeignKeyListSecondTable.size() == foreignKeyListSecondTable.size())
                .isTrue();

        for (Map.Entry<String, ForeignKeyInfo> foreignKey :
                expectedForeignKeyListSecondTable.entrySet()) {
            String foreignKeyName = foreignKey.getKey();
            assertThat(foreignKeyListSecondTable.containsKey(foreignKeyName)).isTrue();
            assertThat(
                            expectedForeignKeyListSecondTable
                                    .get(foreignKeyName)
                                    .isEqual(foreignKeyListSecondTable.get(foreignKeyName)))
                    .isTrue();
        }
    }

    /** tests the list of all indexes. */
    @Test
    public void test_index_matches() {

        IndexInfo expectedIndexInfoFirstTable =
                new IndexInfo.Builder("idx_sleep_session_record_table_0", FIRST_TABLE_NAME)
                        .setUniqueFlag(true)
                        .addIndexCols("stage_start_time")
                        .build();

        HashMap<String, IndexInfo> indexInfoFirstTable =
                mTableList.get(FIRST_TABLE_NAME).getIndexInfoMapping();

        for (Map.Entry<String, IndexInfo> index : indexInfoFirstTable.entrySet()) {
            assertThat(index.getValue().isEqual(expectedIndexInfoFirstTable)).isTrue();
        }

        HashMap<String, IndexInfo> indexInfoSecondTable =
                mTableList.get(SECOND_TABLE_NAME).getIndexInfoMapping();

        assertThat(indexInfoSecondTable.isEmpty()).isTrue();
    }
}
