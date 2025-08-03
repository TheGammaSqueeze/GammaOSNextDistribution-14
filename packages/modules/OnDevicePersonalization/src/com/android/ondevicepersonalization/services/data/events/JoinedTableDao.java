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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.util.OnDevicePersonalizationFlatbufferUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dao used to manage and create in-memory table for joined Events and Queries tables
 */
public class JoinedTableDao {
    /** Map of column name to {@link ColumnSchema} of columns provided by OnDevicePersonalization */
    public static final Map<String, ColumnSchema> ODP_PROVIDED_COLUMNS;
    // TODO(298682670): Finalize provided column and table names.
    public static final String SERVICE_NAME_COL = "serviceName";
    public static final String TYPE_COL = "type";
    public static final String EVENT_TIME_MILLIS_COL = "eventTimeMillis";
    public static final String QUERY_TIME_MILLIS_COL = "queryTimeMillis";
    public static final String TABLE_NAME = "odp_joined_table";
    private static final String TAG = "JoinedTableDao";
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();

    static {
        ODP_PROVIDED_COLUMNS = new HashMap<>();
        ODP_PROVIDED_COLUMNS.put(SERVICE_NAME_COL, new ColumnSchema.Builder().setName(
                SERVICE_NAME_COL).setType(ColumnSchema.SQL_DATA_TYPE_TEXT).build());
        ODP_PROVIDED_COLUMNS.put(TYPE_COL, new ColumnSchema.Builder().setName(TYPE_COL).setType(
                ColumnSchema.SQL_DATA_TYPE_INTEGER).build());
        ODP_PROVIDED_COLUMNS.put(EVENT_TIME_MILLIS_COL, new ColumnSchema.Builder().setName(
                EVENT_TIME_MILLIS_COL).setType(ColumnSchema.SQL_DATA_TYPE_INTEGER).build());
        ODP_PROVIDED_COLUMNS.put(QUERY_TIME_MILLIS_COL, new ColumnSchema.Builder().setName(
                QUERY_TIME_MILLIS_COL).setType(ColumnSchema.SQL_DATA_TYPE_INTEGER).build());
    }

    private final SQLiteOpenHelper mDbHelper;
    private final Map<String, ColumnSchema> mColumns;

    public JoinedTableDao(List<ColumnSchema> columnSchemaList, long fromEventId, long fromQueryId,
            Context context) {
        if (!validateColumns(columnSchemaList)) {
            throw new IllegalArgumentException("Provided columns are invalid.");
        }
        // Move the List to a HashMap <ColumnName, ColumnSchema> for easier access.
        mColumns = columnSchemaList.stream().collect(Collectors.toMap(
                ColumnSchema::getName,
                Function.identity(),
                (v1, v2) -> {
                    // Throw on duplicate keys.
                    throw new IllegalArgumentException("Duplicate key found in columnSchemaList");
                },
                HashMap::new));
        mDbHelper = createInMemoryTable(columnSchemaList, context);
        populateTable(fromEventId, fromQueryId, context);
    }

    private static SQLiteOpenHelper createInMemoryTable(List<ColumnSchema> columnSchemaList,
            Context context) {
        List<String> columns = columnSchemaList.stream().map(ColumnSchema::toString).collect(
                Collectors.toList());
        String createTableStatement = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                + String.join(",", columns) + ")";
        SQLiteOpenHelper sqLiteOpenHelper = new SQLiteOpenHelper(context, null, null, 1) {
            @Override
            public void onCreate(SQLiteDatabase db) {
                // Do nothing.
            }

            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                // Do nothing. Should never be called.
            }
        };

        try {
            sqLiteOpenHelper.getReadableDatabase().execSQL(createTableStatement);
        } catch (SQLException e) {
            sLogger.e(e, TAG + " : Failed to create JoinedTable database in memory.");
            throw new IllegalStateException(e);
        }
        return sqLiteOpenHelper;
    }

    private static boolean validateColumns(List<ColumnSchema> columnSchemaList) {
        if (columnSchemaList.size() == 0) {
            sLogger.d(TAG, ": Empty columnSchemaList provided");
            return false;
        }
        for (ColumnSchema columnSchema : columnSchemaList) {
            // Validate any ODP_PROVIDED_COLUMNS are the correct type
            if (ODP_PROVIDED_COLUMNS.containsKey(columnSchema.getName())) {
                ColumnSchema expected = ODP_PROVIDED_COLUMNS.get(columnSchema.getName());
                if (expected.getType() != columnSchema.getType()) {
                    sLogger.d(TAG
                                    + ": ODP column %s of type %s provided does not match "
                                    + "expected type %s",
                            columnSchema.getName(), columnSchema.getType(), expected.getType());
                    return false;
                }
            }
        }
        // TODO(298225729): Additional validation on column name formatting.
        return true;
    }

    /**
     * Executes the given query on the in-memory db.
     *
     * @return Cursor holding result of the query.
     */
    public Cursor rawQuery(String sql) {
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        // TODO(298225729): Determine return format.
        return db.rawQuery(sql, null);
    }

    private void populateTable(long fromEventId, long fromQueryId, Context context) {
        EventsDao eventsDao = EventsDao.getInstance(context);
        List<JoinedEvent> joinedEventList = eventsDao.readAllNewRows(fromEventId,
                fromQueryId);

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        try {
            db.beginTransactionNonExclusive();
            for (JoinedEvent joinedEvent : joinedEventList) {
                if (joinedEvent.getEventId() == 0) {
                    // Process Query-only rows
                    if (joinedEvent.getQueryData() != null) {
                        List<ContentValues> queryFieldRows =
                                OnDevicePersonalizationFlatbufferUtils
                                        .getContentValuesFromQueryData(
                                                joinedEvent.getQueryData());
                        for (ContentValues queryRow : queryFieldRows) {
                            ContentValues insertValues = new ContentValues();
                            insertValues.putAll(extractValidColumns(queryRow));
                            insertValues.putAll(addProvidedColumns(joinedEvent));
                            long insertResult = db.insert(TABLE_NAME, null, insertValues);
                            if (insertResult == -1) {
                                throw new IllegalStateException("Failed to insert row into SQL DB");
                            }
                        }
                    }
                } else {
                    ContentValues insertValues = new ContentValues();
                    // Add eventData columns
                    if (joinedEvent.getEventData() != null) {
                        ContentValues eventData =
                                OnDevicePersonalizationFlatbufferUtils
                                        .getContentValuesFromEventData(
                                                joinedEvent.getEventData());
                        insertValues.putAll(extractValidColumns(eventData));
                    }
                    // Add queryData columns
                    if (joinedEvent.getQueryData() != null) {
                        ContentValues queryData =
                                OnDevicePersonalizationFlatbufferUtils
                                        .getContentValuesRowFromQueryData(
                                                joinedEvent.getQueryData(),
                                                joinedEvent.getRowIndex());
                        insertValues.putAll(extractValidColumns(queryData));
                    }
                    // Add ODP provided columns
                    insertValues.putAll(addProvidedColumns(joinedEvent));
                    long insertResult = db.insert(TABLE_NAME, null, insertValues);
                    if (insertResult == -1) {
                        throw new IllegalStateException("Failed to insert row into SQL DB");
                    }
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private ContentValues addProvidedColumns(JoinedEvent joinedEvent) {
        ContentValues result = new ContentValues();
        if (mColumns.containsKey(SERVICE_NAME_COL)) {
            result.put(SERVICE_NAME_COL,
                    joinedEvent.getServiceName());
        }
        if (mColumns.containsKey(TYPE_COL)) {
            result.put(TYPE_COL, joinedEvent.getType());
        }
        if (mColumns.containsKey(EVENT_TIME_MILLIS_COL)) {
            result.put(EVENT_TIME_MILLIS_COL, joinedEvent.getEventTimeMillis());
        }
        if (mColumns.containsKey(QUERY_TIME_MILLIS_COL)) {
            result.put(QUERY_TIME_MILLIS_COL, joinedEvent.getQueryTimeMillis());
        }
        return result;
    }

    private ContentValues extractValidColumns(ContentValues data) {
        ContentValues result = new ContentValues();
        for (String key : data.keySet()) {
            if (mColumns.containsKey(key)) {
                Object value = data.get(key);
                int sqlType = mColumns.get(key).getType();
                if (value instanceof Byte) {
                    if (sqlType == ColumnSchema.SQL_DATA_TYPE_INTEGER) {
                        result.put(key, (Byte) value);
                    }
                } else if (value instanceof Short) {
                    if (sqlType == ColumnSchema.SQL_DATA_TYPE_INTEGER) {
                        result.put(key, (Short) value);
                    }
                } else if (value instanceof Integer) {
                    if (sqlType == ColumnSchema.SQL_DATA_TYPE_INTEGER) {
                        result.put(key, (Integer) value);
                    }
                } else if (value instanceof Long) {
                    if (sqlType == ColumnSchema.SQL_DATA_TYPE_INTEGER) {
                        result.put(key, (Long) value);
                    }
                } else if (value instanceof Float) {
                    if (sqlType == ColumnSchema.SQL_DATA_TYPE_REAL) {
                        result.put(key, (Float) value);
                    }
                } else if (value instanceof Double) {
                    if (sqlType == ColumnSchema.SQL_DATA_TYPE_REAL) {
                        result.put(key, (Double) value);
                    }
                } else if (value instanceof String) {
                    if (sqlType == ColumnSchema.SQL_DATA_TYPE_TEXT) {
                        result.put(key, (String) value);
                    }
                } else if (value instanceof byte[]) {
                    if (sqlType == ColumnSchema.SQL_DATA_TYPE_BLOB) {
                        result.put(key, (byte[]) value);
                    }
                } else if (value instanceof Boolean) {
                    if (sqlType == ColumnSchema.SQL_DATA_TYPE_INTEGER) {
                        result.put(key, (Boolean) value);
                    }
                }
            }
        }
        return result;
    }
}
