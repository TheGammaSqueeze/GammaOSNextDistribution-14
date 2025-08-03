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

package com.android.server.deviceconfig.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import com.android.server.deviceconfig.db.DeviceConfigDbHelper.Contract.DeviceConfigEntry;

import java.util.HashMap;
import java.util.Map;

/**
 * @hide
 */
public class DeviceConfigDbAdapter {

    private final SQLiteDatabase mDb;

    public DeviceConfigDbAdapter(SQLiteDatabase db) {
        mDb = db;
    }

    public Map<String, String> getValuesForNamespace(String namespace, String... keys) {

        String[] projection = {
                DeviceConfigEntry.COLUMN_NAME_KEY,
                DeviceConfigEntry.COLUMN_NAME_VALUE
        };

        String selection;
        String[] selectionArgs;
        if (keys != null && keys.length > 0) {
            selection = DeviceConfigEntry.COLUMN_NAME_NAMESPACE + " = ? "
                    + "and " + DeviceConfigEntry.COLUMN_NAME_KEY + " in ( ? ) ";
            String keySelection = TextUtils.join(",", keys);
            selectionArgs = new String[]{namespace, keySelection};
        } else {
            selection = DeviceConfigEntry.COLUMN_NAME_NAMESPACE + " = ?";
            selectionArgs = new String[]{namespace};
        }
        Cursor cursor = mDb.query(
                DeviceConfigEntry.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null
        );

        Map<String, String> map = new HashMap<>(cursor.getCount());
        while (cursor.moveToNext()) {
            String key = cursor.getString(
                    cursor.getColumnIndexOrThrow(DeviceConfigEntry.COLUMN_NAME_KEY));
            String value = cursor.getString(
                    cursor.getColumnIndexOrThrow(DeviceConfigEntry.COLUMN_NAME_VALUE));
            map.put(key, value);
        }
        cursor.close();
        return map;
    }

    /**
     *
     * @return true if the data was inserted or updated in the database
     */
    private boolean insertOrUpdateValue_inTransaction(String namespace, String key, String value) {
        // TODO(b/265948914): see if this is the most performant way to either insert or update a record
        ContentValues values = new ContentValues();
        values.put(DeviceConfigEntry.COLUMN_NAME_NAMESPACE, namespace);
        values.put(DeviceConfigEntry.COLUMN_NAME_KEY, key);
        values.put(DeviceConfigEntry.COLUMN_NAME_VALUE, value);

        String where = DeviceConfigEntry.COLUMN_NAME_NAMESPACE + " = ? "
                + "and " + DeviceConfigEntry.COLUMN_NAME_VALUE + " = ? ";

        String[] whereArgs = {namespace, key};
        int updatedRows = mDb.update(DeviceConfigEntry.TABLE_NAME, values, where, whereArgs);
        if (updatedRows == 0) {
            // this is a new row, we need to insert it
            long id = mDb.insert(DeviceConfigEntry.TABLE_NAME, null, values);
            return id != -1;
        }
        return updatedRows > 0;
    }

    /**
     * Set or update the values in the map into the namespace.
     *
     * @return true if all values were set. Returns true if the map is empty.
     */
    public boolean setValues(String namespace, Map<String, String> map) {
        if (map.size() == 0) {
            return true;
        }
        boolean allSucceeded = true;
        try {
            mDb.beginTransaction();
            for (Map.Entry<String, String> entry : map.entrySet()) {
                // TODO(b/265948914) probably should call yieldIfContendedSafely in this loop
                allSucceeded &= insertOrUpdateValue_inTransaction(namespace, entry.getKey(),
                        entry.getValue());
            }
            mDb.setTransactionSuccessful();
        } finally {
            mDb.endTransaction();
        }
        return allSucceeded;
    }

    /**
     *
     * @return true if the value was set
     */
    public boolean setValue(String namespace, String key, String value, boolean makeDefault) {
        HashMap<String, String> map = new HashMap<>();
        map.put(key, value);
        return setValues(namespace, map);
        // TODO(b/265948914) implement make default!
    }

    /**
     *
     * @return true if any value was deleted
     */
    public boolean deleteValue(String namespace, String key) {
        String where = DeviceConfigEntry.COLUMN_NAME_NAMESPACE + " = ? "
                + "and " + DeviceConfigEntry.COLUMN_NAME_KEY + " = ? ";
        String[] whereArgs = { namespace, key };
        int count = mDb.delete(DeviceConfigEntry.TABLE_NAME, where, whereArgs);
        return count > 0;
    }
}
