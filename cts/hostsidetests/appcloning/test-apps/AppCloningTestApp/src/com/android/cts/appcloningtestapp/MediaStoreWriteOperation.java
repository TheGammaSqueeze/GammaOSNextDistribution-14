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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Calendar;

public class MediaStoreWriteOperation {

    private static final String TAG = "MediaStoreWriteOperation";

    // Write an image to primary external storage using MediaStore API
    public static Uri createImageFileToMediaStoreReturnUri(Context context, String displayName,
            Bitmap bitmap, Uri imageCollection) {

        // Publish a new image
        ContentValues newImageDetails = new ContentValues();
        newImageDetails.put(MediaStore.Images.Media.DISPLAY_NAME,
                displayName + "_" + Calendar.getInstance().getTime() + ".jpg");
        newImageDetails.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        newImageDetails.put(MediaStore.Images.Media.WIDTH, bitmap.getWidth());
        newImageDetails.put(MediaStore.Images.Media.HEIGHT, bitmap.getHeight());

        // Add a specific media item
        ContentResolver resolver = context.getContentResolver();

        Uri newImageUri = null;

        try {
            // Keeps a handle to the new image's URI in case we need to modify it later
            newImageUri = resolver.insert(imageCollection, newImageDetails);

            if (newImageUri == null) {
                throw new IOException("Couldn't create MediaStore entry");
            }

            // Now you got the URI of an image, finally save it in the MediaStore
            OutputStream outputStream = resolver.openOutputStream(newImageUri);
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                throw new IOException("Couldn't save bitmap");
            }

            outputStream.flush();
            outputStream.close();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        return newImageUri;
    }

    public static boolean createImageFileToMediaStore(Context context, String displayName,
            Bitmap bitmap, Uri imageCollection) {
        Uri newImageUri =
                createImageFileToMediaStoreReturnUri(context, displayName, bitmap, imageCollection);
        if (newImageUri != null) {
            String[] projection = new String[]{
                    MediaStore.Images.Media.DISPLAY_NAME,
            };
            String sortOrder = MediaStore.Images.Media.DISPLAY_NAME + " ASC";
            Cursor cursor = context.getContentResolver().query(newImageUri, projection,
                        null, null, sortOrder);
            int displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media
                    .DISPLAY_NAME);
            if (cursor.moveToNext()) {
                // Get values of columns for a given image.
                String displayNameActual = cursor.getString(displayNameColumn);
                return displayNameActual.contains(displayName);
            }
        }
        return false;
    }
}
