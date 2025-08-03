/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.wallpaper.asset;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.os.FileUtils;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.MultiTransformation;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.bumptech.glide.load.resource.bitmap.FitCenter;
import com.bumptech.glide.request.RequestOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Image asset representing a Partner stub APK resource.
 */
public final class SystemStaticAsset extends ResourceAsset {
    private final String mResName;

    /**
     * @param res   Resources containing the asset.
     * @param resId Resource ID referencing the asset.
     * @param isThumbnail Indicates this resource is specific for thumbnail.
     */
    public SystemStaticAsset(Resources res, int resId, String resName, boolean isThumbnail) {
        super(res, resId, isThumbnail);
        mResName = resName;
    }

    @Override
    public Key getKey() {
        if (mKey == null) {
            mKey = new PackageResourceKey(mRes, mResId, mResName);
        }
        return mKey;
    }

    @Override
    public void loadLowResDrawable(Activity activity, ImageView imageView, int placeholderColor,
            BitmapTransformation transformation) {
        MultiTransformation<Bitmap> multiTransformation =
                new MultiTransformation<>(new FitCenter(), transformation);
        Glide.with(activity)
                .asDrawable()
                .load(this)
                .apply(RequestOptions.bitmapTransform(multiTransformation)
                        .placeholder(new ColorDrawable(placeholderColor)))
                .into(imageView);
    }


    @Override
    public void copy(File dest) {
        super.copy(dest);
        try (InputStream inputStream = openInputStream();
                OutputStream outputStream = new FileOutputStream(dest)) {
            FileUtils.copy(inputStream, outputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Glide caching key for resources from System stub APK.
     */
    private static class PackageResourceKey extends ResourceAsset.PackageResourceKey {
        private String mResName;

        PackageResourceKey(Resources res, int resId, String resName) {
            super(res, resId);
            mResName = resName;
        }

        @Override
        protected String getCacheKey() {
            return "PackageResourceKey{"
                    + "packageName=" + mPackageName
                    + ",resId=" + mResId
                    + ",resName=" + mResName
                    + '}';
        }
    }
}
