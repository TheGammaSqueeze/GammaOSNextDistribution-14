/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.documentsui;

import static com.android.documentsui.base.DocumentInfo.getCursorString;

import android.content.ContentProviderClient;
import android.database.Cursor;
import android.os.FileUtils;
import android.provider.DocumentsContract;
import android.util.Log;

import com.android.documentsui.archives.ArchivesProvider;
import com.android.documentsui.base.DocumentInfo;

import java.util.HashSet;
import java.util.Set;

public class DirectoryResult implements AutoCloseable {

    private static final String TAG = "DirectoryResult";

    public Exception exception;
    public DocumentInfo doc;
    ContentProviderClient client;

    private Cursor mCursor;
    private Set<String> mFileNames;
    private String[] mModelIds;

    @Override
    public void close() {
        FileUtils.closeQuietly(mCursor);
        if (client != null && doc.isInArchive()) {
            ArchivesProvider.releaseArchive(client, doc.derivedUri);
        }
        FileUtils.closeQuietly(client);
        client = null;
        doc = null;

        setCursor(null);
    }

    public Cursor getCursor() {
        return mCursor;
    }

    public String[] getModelIds() {
        return mModelIds;
    }

    public Set<String> getFileNames() {
        return mFileNames;
    }

    /** Update the cursor and populate cursor-related fields. */
    public void setCursor(Cursor cursor) {
        mCursor = cursor;

        if (mCursor == null) {
            mFileNames = null;
            mModelIds = null;
        } else {
            loadDataFromCursor();
        }
    }

    /** Populate cursor-related field. Must not be called from UI thread. */
    private void loadDataFromCursor() {
        ThreadHelper.assertNotOnMainThread();
        int cursorCount = mCursor.getCount();
        String[] modelIds = new String[cursorCount];
        Set<String> fileNames = new HashSet<>();
        try {
            mCursor.moveToPosition(-1);
            for (int pos = 0; pos < cursorCount; ++pos) {
                if (!mCursor.moveToNext()) {
                    Log.e(TAG, "Fail to move cursor to next pos: " + pos);
                    return;
                }

                // Generates a Model ID for a cursor entry that refers to a document. The Model
                // ID is a unique string that can be used to identify the document referred to by
                // the cursor. Prefix the ids with the authority to avoid collisions.
                modelIds[pos] = ModelId.build(mCursor);
                fileNames.add(
                        getCursorString(mCursor, DocumentsContract.Document.COLUMN_DISPLAY_NAME));
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception when moving cursor. Stale cursor?", e);
            return;
        }

        // Model related data is only non-null when no error iterating through cursor.
        mModelIds = modelIds;
        mFileNames = fileNames;
    }
}
