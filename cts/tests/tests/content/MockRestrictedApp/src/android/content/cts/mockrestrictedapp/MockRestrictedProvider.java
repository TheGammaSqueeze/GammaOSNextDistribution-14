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

package android.content.cts.mockrestrictedapp;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;

/**
 * This ContentProvider is restricted to use by defined permission
 */
public class MockRestrictedProvider extends ContentProvider {

    private static final String AUTHORITY = "restrictedctstest";
    private static final int TESTTABLE1 = 1;
    private static final int TESTTABLE1_ID = 2;
    private final UriMatcher mUrlMatcher;

    public MockRestrictedProvider() {
        mUrlMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mUrlMatcher.addURI(AUTHORITY, "testtable1", TESTTABLE1);
        mUrlMatcher.addURI(AUTHORITY, "testtable1/#", TESTTABLE1_ID);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        switch (mUrlMatcher.match(uri)) {
            case TESTTABLE1:
                return "vnd.android.cursor.dir/com.android.content.testtable1";
            case TESTTABLE1_ID:
                return "vnd.android.cursor.item/com.android.content.testtable1";
            default:
                throw new IllegalArgumentException("Unknown URL " + uri);
        }
    }

    @Override
    public String getTypeAnonymous(Uri uri) {
        final String type = getType(uri);
        switch(type) {
            case "vnd.android.cursor.dir/com.android.content.testtable1":
                return type;
        }
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection,
            String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
