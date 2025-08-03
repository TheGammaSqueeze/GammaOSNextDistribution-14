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

package com.android.ondevicepersonalization.services.data.events;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ColumnSchemaTest {
    @Test
    public void testBuilderAndEquals() {
        String columnName = "column";
        int sqlType = ColumnSchema.SQL_DATA_TYPE_INTEGER;
        ColumnSchema columnSchema1 = new ColumnSchema.Builder().setName(columnName).setType(
                sqlType).build();

        assertEquals(columnSchema1.getName(), columnName);
        assertEquals(columnSchema1.getType(), sqlType);

        ColumnSchema columnSchema2 = new ColumnSchema.Builder(
                columnName, sqlType)
                .build();
        assertEquals(columnSchema1, columnSchema2);
        assertEquals(columnSchema1.hashCode(), columnSchema2.hashCode());
    }

    @Test
    public void testToString() {
        String columnName = "column";
        ColumnSchema columnSchema = new ColumnSchema.Builder().setName(columnName).setType(
                ColumnSchema.SQL_DATA_TYPE_INTEGER).build();
        assertEquals(columnName + " " + "INTEGER", columnSchema.toString());

        columnSchema = new ColumnSchema.Builder().setName(columnName).setType(
                ColumnSchema.SQL_DATA_TYPE_REAL).build();
        assertEquals(columnName + " " + "REAL", columnSchema.toString());

        columnSchema = new ColumnSchema.Builder().setName(columnName).setType(
                ColumnSchema.SQL_DATA_TYPE_TEXT).build();
        assertEquals(columnName + " " + "TEXT", columnSchema.toString());

        columnSchema = new ColumnSchema.Builder().setName(columnName).setType(
                ColumnSchema.SQL_DATA_TYPE_BLOB).build();
        assertEquals(columnName + " " + "BLOB", columnSchema.toString());


    }

    @Test
    public void testBuildTwiceThrows() {
        String columnName = "column";
        int sqlType = ColumnSchema.SQL_DATA_TYPE_INTEGER;
        ColumnSchema.Builder builder = new ColumnSchema.Builder().setName(columnName).setType(
                sqlType);

        builder.build();
        assertThrows(IllegalStateException.class, builder::build);
    }
}
