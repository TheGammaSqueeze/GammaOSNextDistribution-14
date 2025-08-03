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

package com.android.cts.verifier.sharesheet;

import android.content.ClipDescription;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.cts.verifier.sharesheet.TestContract.UriParams;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SharesheetTestImageProvider extends ContentProvider {
    private static final String TAG = "TestImageProvider";
    private final Executor mExecutor = Executors.newCachedThreadPool();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return uri.getQueryParameter(UriParams.Type);
    }

    @Nullable
    @Override
    public AssetFileDescriptor openAssetFile(@NonNull Uri uri, @NonNull String mode)
            throws FileNotFoundException {
        String name = uri.getQueryParameter(UriParams.Name);
        if (name == null) {
            throw new FileNotFoundException("Malformed URI: " + uri);
        }
        try {
            final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createReliablePipe();
            mExecutor.execute(() -> {
                try {
                    sendBitmap(pipe[1], uriToColor(uri), name, getType(uri));
                } catch (IOException e) {
                    Log.e(TAG, "Failed to send bitmap", e);
                } finally {
                    try {
                        pipe[1].close();
                    } catch (Throwable t) {
                        // ignore
                    }
                }
            });
            return new AssetFileDescriptor(pipe[0], 0, -1);
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    private void sendBitmap(
                ParcelFileDescriptor fd,
                int color,
                String name,
                @Nullable String mimeType) throws IOException {
        Bitmap bitmap = createBitmap(200, 200, color, name);
        try (FileOutputStream fos = new FileOutputStream(fd.getFileDescriptor())) {
            CompressFormat format = ClipDescription.compareMimeTypes(mimeType, "image/jpg")
                    ? CompressFormat.JPEG
                    : CompressFormat.PNG;
            bitmap.compress(format, 100, fos);
        }
    }

    private static Bitmap createBitmap(int width, int height, int bgColor, String name) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setColor(bgColor);
        paint.setStyle(Style.FILL);
        canvas.drawPaint(paint);
        paint.setColor(getContrast(bgColor));
        paint.setAntiAlias(true);
        paint.setTextSize(28f);
        paint.setTextAlign(Align.CENTER);
        canvas.drawText(name, width / 2f, height / 2f, paint);
        return bitmap;
    }

    private static int uriToColor(Uri uri) {
        int hash = uri.hashCode();
        return Color.argb(
                255,
                (hash & 0x00ff0000) >>> 16,
                (hash & 0x0000ff00) >>> 8,
                hash & 0x000000ff);
    }

    private static int getContrast(int color) {
        return Color.argb(
                255,
                Color.red(color) > 127 ? 0 : 255,
                Color.green(color) > 127 ? 0 : 255,
                Color.blue(color) > 127 ? 0 : 255);
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
            @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }


    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
            @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
            @Nullable String[] selectionArgs) {
        return 0;
    }
}
