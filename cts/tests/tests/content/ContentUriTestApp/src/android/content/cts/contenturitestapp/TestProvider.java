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
 * limitations under the License.
 */

package android.content.cts.contenturitestapp;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

public class TestProvider extends ContentProvider {
    private static final String AUTHORITY = "android.content.cts.contenturitestapp.provider";

    public static final Uri CONTENT_URI_BASE = Uri.parse("content://" + AUTHORITY);

    public static final Uri CONTENT_URI_NONE =
            Uri.parse("content://" + AUTHORITY + "/none");

    public static final Uri CONTENT_URI_ONLY_READ =
            Uri.parse("content://" + AUTHORITY + "/only.read");

    public static final Uri CONTENT_URI_ONLY_WRITE =
            Uri.parse("content://" + AUTHORITY + "/only.write");

    public static final Uri CONTENT_URI_READ_WRITE =
            Uri.parse("content://" + AUTHORITY + "/read.write");

    /** Returns a content URI according to modeFlags */
    public static Uri getSubsetContentUri(int modeFlags) {
        boolean hasRead = (modeFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0;
        boolean hasWrite = (modeFlags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0;

        if (hasRead && hasWrite) {
            return TestProvider.CONTENT_URI_READ_WRITE;
        } else if (hasRead) {
            return TestProvider.CONTENT_URI_ONLY_READ;
        } else {
            return TestProvider.CONTENT_URI_ONLY_WRITE;
        }
    }

    @Override
    public boolean onCreate() {
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] strings, String s, String[] strings1, String s1) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {
        return null;
    }

    @Override
    public int delete(Uri uri, String s, String[] strings) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues contentValues, String s, String[] strings) {
        return 0;
    }
}
