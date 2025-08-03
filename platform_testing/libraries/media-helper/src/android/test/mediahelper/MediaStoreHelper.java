/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.test.mediahelper;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import com.google.common.collect.ImmutableList;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Helper functions for interacting with android {@link MediaStore}. */
public class MediaStoreHelper {
    private static MediaStoreHelper sInstance;
    private ContentResolver mContentResolver;

    private MediaStoreHelper(Context context) {
        mContentResolver = context.getContentResolver();
    }

    public static MediaStoreHelper getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new MediaStoreHelper(context);
        }
        return sInstance;
    }

    /**
     * Returns the maximum value of {@link MediaStore.MediaColumns.GENERATION_ADDED} at given URI.
     *
     * @param uri The URI, the content:// style uri, for the content to retrieve from {@link
     *     MediaStore}. For example, to retrieve from image media table, use
     *     "content://media/external/images/media".
     * @param selection A filter declaring which rows to return, formatted as an SQL WHERE clause
     *     (excluding the WHERE itself). Passing null will return all rows for the given URI.
     * @param selectionArgs You may include ?s in selection, which will be replaced by the values
     *     from selectionArgs, in the order that they appear in the selection. The values will be
     *     bound as Strings.
     * @return A long value of the maximum generation. Returns -1L if no records find.
     */
    public long getMaxGeneration(Uri uri, String selection, String[] selectionArgs) {
        long maxGen = -1L;
        Cursor cursor =
                mContentResolver.query(
                        uri,
                        new String[] {MediaStore.MediaColumns.GENERATION_ADDED},
                        selection,
                        selectionArgs,
                        MediaStore.MediaColumns.GENERATION_ADDED + " DESC");
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                maxGen =
                        cursor.getLong(
                                cursor.getColumnIndex(MediaStore.MediaColumns.GENERATION_ADDED));
            }
            cursor.close();
        }
        return maxGen;
    }

    /**
     * Returns a value list of {@link MediaStore.MediaColumns#_ID}.
     *
     * @param uri The URI, the content:// style uri, for the content to retrieve from {@link
     *     MediaStore}. For example, to retrieve from image media table, use
     *     "content://media/external/images/media".
     * @param selection A filter declaring which rows to return, formatted as an SQL WHERE clause
     *     (excluding the WHERE itself). Passing null will return all rows for the given URI.
     * @param selectionArgs You may include ?s in selection, which will be replaced by the values
     *     from selectionArgs, in the order that they appear in the selection. The values will be
     *     bound as Strings.
     * @param fromGen Generation added. The id of the records returned all have generation bigger
     *     than this value. It is also formatted as a WHERE clause together with what specified in
     *     selection.
     * @return A list of record ids.
     */
    public List<Long> getListOfIdsFromMediaStore(
            Uri uri, String selection, String[] selectionArgs, long fromGen) {
        ImmutableList.Builder builder = new ImmutableList.Builder();
        String selectionStr =
                MediaStore.MediaColumns.GENERATION_ADDED + ">" + String.valueOf(fromGen);
        if (selection != null && !selection.isEmpty()) {
            selectionStr = selectionStr + " AND " + selection;
        }
        Cursor cursor =
                mContentResolver.query(
                        uri,
                        new String[] {
                            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.GENERATION_ADDED
                        },
                        selectionStr,
                        selectionArgs,
                        null);
        try {
            while (cursor != null && cursor.moveToNext()) {
                builder.add(cursor.getLong(cursor.getColumnIndex(MediaStore.MediaColumns._ID)));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return builder.build();
    }

    /**
     * Returns a list of media {@link FileDescriptor} for specific MIME type.
     *
     * @param uri The URI, the content:// style uri, for the content to retrieve from {@link
     *     MediaStore}. For example, to retrieve from image media table, use
     *     "content://media/external/images/media".
     * @param mimeType The MIME type of the media item, eg. "image/jpeg". {@see
     *     MediaStore.MediaColumns#MIME_TYPE}
     * @param fromGen Generation added. The returned records all have generation bigger than this.
     * @param selection A filter declaring which rows to return, formatted as an SQL WHERE clause
     *     (excluding the WHERE itself). Passing null will return all rows for the given URI.
     * @param selectionArgs You may include ?s in selection, which will be replaced by the values
     *     from selectionArgs, in the order that they appear in the selection. The values will be
     *     bound as Strings.
     * @return A list of media {@link FileDescriptor} for specified type.
     */
    public List<FileDescriptor> getMediaFileDescriptors(
            Uri uri, String mimeType, String selection, String[] selectionArgs, long fromGen) {
        ImmutableList.Builder fileListBuilder = new ImmutableList.Builder();
        StringBuilder selectionBuilder = new StringBuilder();
        List<String> argList = new ArrayList<>();
        selectionBuilder.append(MediaStore.MediaColumns.IS_PENDING + "=?");
        argList.add("0");
        if (mimeType != null) {
            selectionBuilder.append(" AND ");
            selectionBuilder.append(MediaStore.MediaColumns.MIME_TYPE + "=?");
            argList.add(mimeType);
        }
        if (selection != null && !selection.isEmpty()) {
            selectionBuilder.append(" AND ");
            selectionBuilder.append(selection);
        }
        if (selectionArgs != null) {
            argList.addAll(Arrays.asList(selectionArgs));
        }
        List<Long> ids =
                getListOfIdsFromMediaStore(
                        uri,
                        selectionBuilder.toString(),
                        argList.toArray(new String[argList.size()]),
                        fromGen);
        for (long id : ids) {
            Uri fileUri = ContentUris.withAppendedId(uri, id);
            try {
                FileDescriptor fileDescriptor =
                        mContentResolver.openFileDescriptor(fileUri, "r").getFileDescriptor();
                fileListBuilder.add(fileDescriptor);
            } catch (FileNotFoundException e) {
                throw new RuntimeException("Unable to open file descriptor at " + uri, e);
            }
        }
        return fileListBuilder.build();
    }
}
