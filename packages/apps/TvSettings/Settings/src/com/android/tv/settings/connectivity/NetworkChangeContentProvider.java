/*
 * Copyright (C) 2024 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.tv.settings.connectivity;


import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

/**
 * A {@link ContentProvider} that provides information for detecting network changes to the
 * current network for re-authenticating SmartHome access.
 * This ContentProvider should only be accessed by the Launcherx Process, and no other apps should
 * be able to invoke it (as determined by permission).
 */
public class NetworkChangeContentProvider extends ContentProvider {
    private static final String TAG = "NetworkChangeContentProvider";
    private static final int DEFAULT_COUNT = 0;

    private SharedPreferences mSharedPreferences;

    @Override
    public boolean onCreate() {
        if (getContext() != null) {
            mSharedPreferences = getContext().getSharedPreferences(
                    NetworkChangeDetectionConfigs.SHARED_PREFERENCES_NAME,
                    Context.MODE_PRIVATE);
        }
        return true;
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        Log.d(TAG, "Querying with URI " + uri);
        String path = uri.getPath();

        if (!path.endsWith("/" + NetworkChangeDetectionConfigs.PREFERENCE_KEY)) {
            throw new IllegalArgumentException("Unknown URI: " + uri);
        } else {
            int totalCount = mSharedPreferences.getInt(NetworkChangeDetectionConfigs.PREFERENCE_KEY,
                    DEFAULT_COUNT);
            MatrixCursor cursor = new MatrixCursor(
                    new String[]{NetworkChangeDetectionConfigs.PREFERENCE_KEY});
            cursor.addRow(new Object[]{totalCount});
            cursor.setNotificationUri(getContext().getContentResolver(),
                    NetworkChangeDetectionConfigs.CONTENT_URI);
            return cursor;
        }
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {
        throw new UnsupportedOperationException("Read-only access supported.");
    }

    @Override
    public int delete(Uri uri, String s, String[] strings) {
        throw new UnsupportedOperationException("Read-only access supported.");
    }

    @Override
    public int update(Uri uri, ContentValues contentValues, String s, String[] strings) {
        throw new UnsupportedOperationException("Read-only access supported.");
    }
}