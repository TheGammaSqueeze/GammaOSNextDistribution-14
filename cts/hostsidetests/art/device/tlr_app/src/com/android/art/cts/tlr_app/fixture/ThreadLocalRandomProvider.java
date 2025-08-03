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

package com.android.art.cts.tlr_app.fixture;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import java.util.concurrent.ThreadLocalRandom;

public class ThreadLocalRandomProvider extends ContentProvider {

    private static final String METHOD_NEXT_RANDOM = "next_random";

    private static final String CALL_RESULT_NEXT_RANDOM = "random";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        // No impl.
        return null;
    }

    @Override
    public String getType(Uri uri) {
        // No impl.
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // No impl.
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // No impl.
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // No impl.
        return 0;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (METHOD_NEXT_RANDOM.equals(method)) {
            Bundle bundle = new Bundle();
            bundle.putLong(CALL_RESULT_NEXT_RANDOM, ThreadLocalRandom.current().nextLong());
            return bundle;
        }
        throw new IllegalArgumentException("Method " + method + " is not recognised");
    }
}
