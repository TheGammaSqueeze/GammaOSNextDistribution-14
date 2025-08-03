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

package com.android.car.qc.testutils;

import android.R;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.android.car.qc.QCItem;
import com.android.car.qc.QCTile;
import com.android.car.qc.provider.BaseQCProvider;

import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class TestQCProvider extends BaseQCProvider {

    public static final String METHOD_IS_SUBSCRIBED = "METHOD_IS_SUBSCRIBED";
    public static final String IS_SUBSCRIBED_KEY = "IS_SUBSCRIBED";
    public static final String METHOD_IS_DESTROYED = "METHOD_IS_DESTROYED";
    public static final String IS_DESTROYED_KEY = "IS_DESTROYED";

    public static final String KEY_DEFAULT = "DEFAULT";
    public static final String KEY_SLOW = "SLOW";

    private final Set<Uri> mSubscribedUris = new HashSet<>();
    private final Set<Uri> mDestroyedUris = new HashSet<>();

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (METHOD_IS_SUBSCRIBED.equals(method)) {
            Uri uri = getUriWithoutUserId(extras.getParcelable(EXTRA_URI));
            Bundle bundle = new Bundle();
            bundle.putBoolean(IS_SUBSCRIBED_KEY, mSubscribedUris.contains(uri));
            return bundle;
        }
        if (METHOD_IS_DESTROYED.equals(method)) {
            Uri uri = getUriWithoutUserId(extras.getParcelable(EXTRA_URI));
            Bundle bundle = new Bundle();
            bundle.putBoolean(IS_DESTROYED_KEY, mDestroyedUris.contains(uri));
            return bundle;
        }
        return super.call(method, arg, extras);
    }

    @Override
    protected QCItem onBind(@NonNull Uri uri) {
        List<String> pathSegments = uri.getPathSegments();
        String key = pathSegments.get(0);

        if (KEY_DEFAULT.equals(key)) {
            return new QCTile.Builder()
                    .setIcon(Icon.createWithResource(getContext(), R.drawable.btn_star))
                    .build();
        } else if (KEY_SLOW.equals(key)) {
            // perform a slow operation that should trigger the strict thread policy
            Drawable d = getContext().getDrawable(R.drawable.btn_star);
            Bitmap bitmap = drawableToBitmap(d);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
            byte[] b = baos.toByteArray();
            Icon icon = Icon.createWithData(b, 0, b.length);
            return new QCTile.Builder()
                    .setIcon(icon)
                    .build();
        }
        return null;
    }

    @Override
    protected void onSubscribed(@NonNull Uri uri) {
        mSubscribedUris.add(uri);
    }

    @Override
    protected void onUnsubscribed(@NonNull Uri uri) {
        mSubscribedUris.remove(uri);
    }

    @Override
    protected void onDestroy(@NonNull Uri uri) {
        mDestroyedUris.add(uri);
    }

    private static Bitmap drawableToBitmap(Drawable drawable) {

        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }
}
