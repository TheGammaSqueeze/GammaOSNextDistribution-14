/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.cluster.view;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.util.Log;
import android.util.LruCache;

import java.io.IOException;

/**
 * Class to fetch a bitmap images from the ContentProvider.
 */
public class BitmapFetcher {
    private static final String TAG = "Cluster.BitmapFetcher";

    private static final int IMAGE_CACHE_SIZE_BYTES = 4 * 1024 * 1024; /* 4 mb */
    private final LruCache<String, Bitmap> mCache = new LruCache<String, Bitmap>(
            IMAGE_CACHE_SIZE_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private static final String BITMAP_QUERY_WIDTH = "w";
    private static final String BITMAP_QUERY_HEIGHT = "h";
    private static final String BITMAP_QUERY_OFFLANESALPHA = "offLanesAlpha";

    private final Context mContext;

    public BitmapFetcher (Context context) {
        mContext = context;
    }

    /**
     * Fetches a bitmap from the ContentProvider with the given width and height
     * and off lane opacity. The fetched bitmaps are cached.
     * It returns null if:
     * <ul>
     * <li>there is no navigation context owner
     * <li>or if the {@link Uri} is invalid
     * <li>or if it references a process other than the current navigation context owner
     * </ul>
     * This is a costly operation. Returned bitmaps should be fetched on a secondary thread.
     *
     * @param uri           The URI of the bitmap
     * @param width         Requested width
     * @param height        Requested height
     * @param offLanesAlpha Opacity value of the off-lane images. Only used for lane guidance images
     * @throws IllegalArgumentException if width, height <= 0, or 0 > offLanesAlpha > 1
     */
    @Nullable
    public Bitmap getBitmap(@NonNull Uri uri, int width, int height, float offLanesAlpha) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be > 0");
        }
        if (offLanesAlpha < 0 || offLanesAlpha > 1) {
            throw new IllegalArgumentException("offLanesAlpha must be between [0, 1]");
        }

        try {
            uri = uri.buildUpon()
                    .appendQueryParameter(BITMAP_QUERY_WIDTH, String.valueOf(width))
                    .appendQueryParameter(BITMAP_QUERY_HEIGHT, String.valueOf(height))
                    .appendQueryParameter(BITMAP_QUERY_OFFLANESALPHA, String.valueOf(offLanesAlpha))
                    .build();

            Bitmap bitmap = mCache.get(uri.toString());
            if (bitmap == null) {
                // Fetch the bitmap
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Requesting bitmap: " + uri);
                }
                try (ParcelFileDescriptor fileDesc =
                             getContentResolverForUser(mContext, ActivityManager.getCurrentUser())
                                     .openFileDescriptor(uri, "r")) {
                    if (fileDesc != null) {
                        bitmap = BitmapFactory.decodeFileDescriptor(fileDesc.getFileDescriptor());
                        return bitmap;
                    } else {
                        Log.e(TAG, "Failed to create pipe for uri string: " + uri);
                    }
                }
                if (bitmap.getWidth() != width || bitmap.getHeight() != height) {
                    bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
                }
                mCache.put(uri.toString(), bitmap);
            }
            return bitmap;
        } catch (IOException e) {
            Log.e(TAG, "Unable to fetch uri: " + uri, e);
        }
        return null;
    }

    private static ContentResolver getContentResolverForUser(Context context,
            @UserIdInt int userId) {
        Log.d(TAG, "Create context as user for user = " + userId);
        return context
                .createContextAsUser(UserHandle.of(userId), /* flags= */ 0)
                .getContentResolver();
    }
}
