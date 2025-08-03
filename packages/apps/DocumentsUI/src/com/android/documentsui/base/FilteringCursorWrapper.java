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

package com.android.documentsui.base;

import static com.android.documentsui.base.DocumentInfo.getCursorLong;
import static com.android.documentsui.base.DocumentInfo.getCursorString;
import static com.android.documentsui.base.SharedMinimal.DEBUG;
import static com.android.documentsui.base.SharedMinimal.TAG;

import android.database.AbstractCursor;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.DocumentsContract.Document;
import android.util.Log;

/**
 * Cursor wrapper that filters cursor results by given conditions.
 */
public class FilteringCursorWrapper extends AbstractCursor {
    private final Cursor mCursor;

    private int[] mPositions;
    private int mCount;

    public FilteringCursorWrapper(Cursor cursor) {
        mCursor = cursor;
        mCount = cursor.getCount();
        mPositions = new int[mCount];
        for (int i = 0; i < mCount; i++) {
            mPositions[i] = i;
        }
    }

    /**
     * Filters cursor according to mimes. If both lists are empty, all mimes will be rejected.
     *
     * @param acceptMimes allowed list of mimes
     * @param rejectMimes blocked list of mimes
     */
    public void filterMimes(String[] acceptMimes, String[] rejectMimes) {
        filterByCondition((cursor) -> {
            final String mimeType = getCursorString(cursor, Document.COLUMN_MIME_TYPE);
            if (rejectMimes != null && MimeTypes.mimeMatches(rejectMimes, mimeType)) {
                return false;
            }
            return MimeTypes.mimeMatches(acceptMimes, mimeType);
        });
    }

    /** Filters cursor according to last modified time, and reject earlier than given timestamp. */
    public void filterLastModified(long rejectBeforeTimestamp) {
        filterByCondition((cursor) -> {
            final long lastModified = getCursorLong(cursor, Document.COLUMN_LAST_MODIFIED);
            return lastModified >= rejectBeforeTimestamp;
        });
    }

    /** Filter hidden files based on preference. */
    public void filterHiddenFiles(boolean showHiddenFiles) {
        if (showHiddenFiles) {
            return;
        }

        filterByCondition((cursor) -> {
            // Judge by name and documentId separately because for some providers
            // e.g. DownloadProvider, documentId may not contain file name.
            final String name = getCursorString(cursor, Document.COLUMN_DISPLAY_NAME);
            final String documentId = getCursorString(cursor, Document.COLUMN_DOCUMENT_ID);
            boolean documentIdHidden = documentId != null && documentId.contains("/.");
            boolean fileNameHidden = name != null && name.startsWith(".");
            return !(documentIdHidden || fileNameHidden);
        });
    }

    @Override
    public Bundle getExtras() {
        return mCursor.getExtras();
    }

    @Override
    public void close() {
        super.close();
        mCursor.close();
    }

    @Override
    public boolean onMove(int oldPosition, int newPosition) {
        return mCursor.moveToPosition(mPositions[newPosition]);
    }

    @Override
    public String[] getColumnNames() {
        return mCursor.getColumnNames();
    }

    @Override
    public int getCount() {
        return mCount;
    }

    @Override
    public double getDouble(int column) {
        return mCursor.getDouble(column);
    }

    @Override
    public float getFloat(int column) {
        return mCursor.getFloat(column);
    }

    @Override
    public int getInt(int column) {
        return mCursor.getInt(column);
    }

    @Override
    public long getLong(int column) {
        return mCursor.getLong(column);
    }

    @Override
    public short getShort(int column) {
        return mCursor.getShort(column);
    }

    @Override
    public String getString(int column) {
        return mCursor.getString(column);
    }

    @Override
    public int getType(int column) {
        return mCursor.getType(column);
    }

    @Override
    public boolean isNull(int column) {
        return mCursor.isNull(column);
    }

    @Override
    public void registerContentObserver(ContentObserver observer) {
        mCursor.registerContentObserver(observer);
    }

    @Override
    public void unregisterContentObserver(ContentObserver observer) {
        mCursor.unregisterContentObserver(observer);
    }

    private interface FilteringCondition {
        boolean accept(Cursor cursor);
    }

    private void filterByCondition(FilteringCondition condition) {
        final int oldCount = this.getCount();
        int[] newPositions = new int[oldCount];
        int newCount = 0;

        this.moveToPosition(-1);
        while (this.moveToNext() && newCount < oldCount) {
            if (condition.accept(mCursor)) {
                newPositions[newCount++] = mPositions[this.getPosition()];
            }
        }

        if (DEBUG && newCount != this.getCount()) {
            Log.d(TAG, "Before filtering " + oldCount + ", after " + newCount);
        }
        mCount = newCount;
        mPositions = newPositions;
    }
}
