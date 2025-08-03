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

package com.android.bedstead.harrier;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link ContentProvider} used to expose test results to Bedstead.
 */
public class BedsteadRunResultsProvider extends ContentProvider {

    public static final AtomicInteger sNumberOfTests = new AtomicInteger(-1);
    public static final ConcurrentHashMap<Integer, BedsteadResult> sResults =
            new ConcurrentHashMap<>();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        if (uri.getPath().equals("/numTests")) {
            return getNumTests();
        }
        int target = Integer.parseInt(uri.getPath().substring(1));
        if (!sResults.containsKey(target)) {
            return null;
        }

        MatrixCursor matrixCursor = new MatrixCursor(
                new String[] {
                        "index", "testName", "result", "message",
                        "stackTrace", "runTime", "isFinished"});
        BedsteadResult result = sResults.get(target);
        matrixCursor.addRow(new Object[]{
                result.mIndex, result.mTestName, result.mResult,
                result.mMessage, result.mStackTrace, result.mRuntime, result.mIsFinished});
        if (result.mIsFinished) {
            result.mHasBeenFetched = true;
        }
        return matrixCursor;
    }

    private Cursor getNumTests() {
        MatrixCursor matrixCursor = new MatrixCursor(new String[] {"tests"});
        matrixCursor.addRow(new Object[]{sNumberOfTests.get()});
        return matrixCursor;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // We do not allow inserting
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // We do not allow deleting
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // We do not allow updating
        return 0;
    }
}
