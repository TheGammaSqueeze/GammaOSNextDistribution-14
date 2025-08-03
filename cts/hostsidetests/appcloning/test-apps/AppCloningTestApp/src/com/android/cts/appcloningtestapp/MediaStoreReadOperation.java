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

package com.android.cts.appcloningtestapp;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;

public class MediaStoreReadOperation {

    private static final String TAG = "MediaStoreReadOperation";
    private static final int ANDROID_Q = 29;

    // Need READ_EXTERNAL_STORAGE permission if accessing image files that your app didn't create.
    public static List<Image> getImageFilesFromMediaStore(Context context, Uri collection) {
        List<Image> imageList = new ArrayList<>();

        String[] projection = new String[] {
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DISPLAY_NAME,
        };

        String sortOrder = MediaStore.Images.Media.DISPLAY_NAME + " ASC";

        try (Cursor cursor = context.getContentResolver().query(collection, projection,
                null, null, sortOrder)) {
            // Cache column indices.
            int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            int displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media
                    .DISPLAY_NAME);

            while (cursor.moveToNext()) {
                // Get values of columns for a given image.
                String data = cursor.getString(dataColumn);
                String displayName = cursor.getString(displayNameColumn);

                /*
                 Stores column values in a local object that represents
                 the media file.
                 */
                imageList.add(new Image(data, displayName));
            }
        }

        return imageList;
    }
}
