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
package com.android.wallpaper.model;

import android.app.Activity;
import android.content.Context;
import android.os.Parcel;

import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.BuiltInWallpaperAsset;

import java.util.Arrays;
import java.util.List;

/**
 * Represents the default built-in wallpaper on the device.
 */
public class DefaultWallpaperInfo extends WallpaperInfo {
    public static final Creator<DefaultWallpaperInfo> CREATOR =
            new Creator<DefaultWallpaperInfo>() {
                @Override
                public DefaultWallpaperInfo createFromParcel(Parcel in) {
                    return new DefaultWallpaperInfo(in);
                }

                @Override
                public DefaultWallpaperInfo[] newArray(int size) {
                    return new DefaultWallpaperInfo[size];
                }
            };
    private Asset mAsset;

    public DefaultWallpaperInfo() {}

    protected DefaultWallpaperInfo(Parcel in) {
        super(in);
    }

    @Override
    public List<String> getAttributions(Context context) {
        return Arrays.asList(context.getResources().getString(R.string.fallback_wallpaper_title));
    }

    @Override
    public Asset getAsset(Context context) {
        if (mAsset == null) {
            mAsset = new BuiltInWallpaperAsset(context);
        }
        return mAsset;
    }

    @Override
    public Asset getThumbAsset(Context context) {
        // Same asset as full size.
        return getAsset(context);
    }

    @Override
    public String getCollectionId(Context context) {
        return context.getString(R.string.on_device_wallpaper_collection_id);
    }

    @Override
    public String getWallpaperId() {
        return "built-in-wallpaper";
    }

    @Override
    public void showPreview(Activity srcActivity, InlinePreviewIntentFactory factory,
                            int requestCode, boolean isAssetIdPresent) {
        srcActivity.startActivityForResult(factory.newIntent(srcActivity, this,
                isAssetIdPresent), requestCode);
    }

    @Override
    @BackupPermission
    public int getBackupPermission() {
        return BACKUP_NOT_ALLOWED;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        super.writeToParcel(parcel, i);
    }
}
