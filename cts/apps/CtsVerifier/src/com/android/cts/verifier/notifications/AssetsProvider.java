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

package com.android.cts.verifier.notifications;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;

/** {@link ContentProvider} for assets used in Notification-related tests. */
public class AssetsProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public AssetFileDescriptor openAssetFile(@NonNull Uri uri, @NonNull String mode)
            throws FileNotFoundException {
        AssetManager assets = getContext().getAssets();
        String filename = uri.getLastPathSegment();
        if (mode.equals("r") && assets != null && filename != null) {
            try {
                return assets.openFd("notifications/" + filename);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return super.openAssetFile(uri, mode);
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
            @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
            @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
            @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
