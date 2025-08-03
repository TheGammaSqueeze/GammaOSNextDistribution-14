/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.android.ondevicepersonalization.services.data.events.EventStateContract;
import com.android.ondevicepersonalization.services.data.events.EventsContract;
import com.android.ondevicepersonalization.services.data.events.QueriesContract;
import com.android.ondevicepersonalization.services.data.vendor.VendorSettingsContract;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationDbHelperTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private OnDevicePersonalizationDbHelper mDbHelper;
    private SQLiteDatabase mDb;

    @Before
    public void setup() {
        mDbHelper = OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
        mDb = mDbHelper.getWritableDatabase();
    }

    @Test
    public void testOnCreate() {
        mDbHelper.onCreate(mDb);
        assertTrue(hasEntity(VendorSettingsContract.VendorSettingsEntry.TABLE_NAME, "table"));
        assertTrue(hasEntity(QueriesContract.QueriesEntry.TABLE_NAME, "table"));
        assertTrue(hasEntity(EventsContract.EventsEntry.TABLE_NAME, "table"));
        assertTrue(hasEntity(EventStateContract.EventStateEntry.TABLE_NAME, "table"));
    }

    @Test
    public void testOnUpgrade() {
        assertThrows(UnsupportedOperationException.class, () -> mDbHelper.onUpgrade(mDb, 2, 1));
    }

    @Test
    public void testGetInstance() {
        OnDevicePersonalizationDbHelper instance1 =
                OnDevicePersonalizationDbHelper.getInstance(mContext);
        OnDevicePersonalizationDbHelper instance2 =
                OnDevicePersonalizationDbHelper.getInstance(mContext);
        assertEquals(instance1, instance2);
    }

    private boolean hasEntity(String entityName, String type) {
        String query = "select DISTINCT name from sqlite_master where name = '"
                + entityName + "' and type = '" + type + "'";
        Cursor cursor = mDb.rawQuery(query, null);
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                cursor.close();
                return true;
            }
            cursor.close();
        }
        return false;
    }
}
