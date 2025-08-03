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
package com.android.wallpaper.testing;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.model.InlinePreviewIntentFactory;
import com.android.wallpaper.model.LiveWallpaperInfo;

import java.util.Arrays;
import java.util.List;
/**
 * Test model object for a wallpaper coming from a live wallpaper component. This is essentially a
 * copy of {@link TestStaticWallpaperInfo} with the minimum changes required to show a preview.
 */
public class TestLiveWallpaperInfo extends LiveWallpaperInfo {
    public static final int COLOR_DEFAULT = 0xff000000;
    public static final Parcelable.Creator<TestLiveWallpaperInfo> CREATOR =
            new Parcelable.Creator<TestLiveWallpaperInfo>() {
                @Override
                public TestLiveWallpaperInfo createFromParcel(Parcel in) {
                    return new TestLiveWallpaperInfo(in);
                }
                @Override
                public TestLiveWallpaperInfo[] newArray(int size) {
                    return new TestLiveWallpaperInfo[size];
                }
            };
    private int mPixelColor;
    private TestAsset mAsset;
    private TestAsset mThumbAsset;
    private List<String> mAttributions;
    private android.app.WallpaperInfo mWallpaperComponent;
    private String mActionUrl;
    private String mBaseImageUrl;
    private String mCollectionId;
    private String mWallpaperId;
    private boolean mIsAssetCorrupt;
    private int mBackupPermission;

    /** Constructs a test WallpaperInfo object representing a 1x1 wallpaper of the given color. */
    public TestLiveWallpaperInfo(int pixelColor) {
        this(pixelColor, null, "test-wallpaper");
    }

    /** Constructs a test WallpaperInfo object representing a 1x1 wallpaper of the given color. */
    public TestLiveWallpaperInfo(int pixelColor, android.app.WallpaperInfo info, String id) {
        super(info);
        mPixelColor = pixelColor;
        mAttributions = Arrays.asList("Test wallpaper");
        mWallpaperComponent = null;
        mIsAssetCorrupt = false;
        mBackupPermission = BACKUP_ALLOWED;
        mWallpaperId = id;
    }

    private TestLiveWallpaperInfo(Parcel in) {
        super(in);
        mPixelColor = in.readInt();
        mAttributions = in.createStringArrayList();
        mActionUrl = in.readString();
        mBaseImageUrl = in.readString();
        mCollectionId = in.readString();
        mWallpaperId = in.readString();
        mIsAssetCorrupt = in.readInt() == 1;
        mBackupPermission = in.readInt();
    }

    @Override
    public Drawable getOverlayIcon(Context context) {
        return null;
    }

    @Override
    public List<String> getAttributions(Context context) {
        return mAttributions;
    }

    /**
     * Override default "Test wallpaper" attributions for testing.
     */
    public void setAttributions(List<String> attributions) {
        mAttributions = attributions;
    }

    @Override
    public CharSequence getActionDescription(Context context) {
        return null;
    }

    @Override
    public String getActionUrl(Context unused) {
        return mActionUrl;
    }

    /** Sets the action URL for this wallpaper. */
    public void setActionUrl(String actionUrl) {
        mActionUrl = actionUrl;
    }

    @Override
    public String getBaseImageUrl() {
        return mBaseImageUrl;
    }

    /** Sets the base image URL for this wallpaper. */
    public void setBaseImageUrl(String baseImageUrl) {
        mBaseImageUrl = baseImageUrl;
    }

    @Override
    public String getCollectionId(Context unused) {
        return mCollectionId;
    }

    /** Sets the collection ID for this wallpaper. */
    public void setCollectionId(String collectionId) {
        mCollectionId = collectionId;
    }

    @Override
    public String getWallpaperId() {
        return mWallpaperId;

    }
    /** Sets the ID for this wallpaper. */
    public void setWallpaperId(String wallpaperId) {
        mWallpaperId = wallpaperId;
    }

    @Override
    public Asset getAsset(Context context) {
        if (mAsset == null) {
            mAsset = new TestAsset(mPixelColor, mIsAssetCorrupt);
        }
        return mAsset;
    }

    @Override
    public Asset getThumbAsset(Context context) {
        if (mThumbAsset == null) {
            mThumbAsset = new TestAsset(mPixelColor, mIsAssetCorrupt);
        }
        return mThumbAsset;
    }

    @Override
    public void showPreview(Activity srcActivity,
            InlinePreviewIntentFactory inlinePreviewIntentFactory, int requestCode,
            boolean isAssetIdPresent) {
        srcActivity.startActivityForResult(
                inlinePreviewIntentFactory.newIntent(srcActivity, this, isAssetIdPresent),
                requestCode);
    }

    @Override
    @BackupPermission
    public int getBackupPermission() {
        return mBackupPermission;
    }

    public void setBackupPermission(@BackupPermission int backupPermission) {
        mBackupPermission = backupPermission;
    }

    @Override
    public android.app.WallpaperInfo getWallpaperComponent() {
        return mWallpaperComponent;
    }

    public void setWallpaperComponent(android.app.WallpaperInfo wallpaperComponent) {
        mWallpaperComponent = wallpaperComponent;
    }

    /**
     * Simulates that the {@link Asset} instances returned by calls to #getAsset and #getThumbAsset
     * on this object are "corrupt" and will fail to perform decode operations such as
     * #decodeBitmap, #decodeBitmapRegion, #decodeRawDimensions, etc (these methods will call their
     * callbacks with null instead of meaningful objects).
     */
    public void corruptAssets() {
        mIsAssetCorrupt = true;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        super.writeToParcel(parcel, i);
        parcel.writeInt(mPixelColor);
        parcel.writeStringList(mAttributions);
        parcel.writeString(mActionUrl);
        parcel.writeString(mBaseImageUrl);
        parcel.writeString(mCollectionId);
        parcel.writeString(mWallpaperId);
        parcel.writeInt(mIsAssetCorrupt ? 1 : 0);
        parcel.writeInt(mBackupPermission);
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if (object instanceof TestLiveWallpaperInfo) {
            return mPixelColor == ((TestLiveWallpaperInfo) object).mPixelColor;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return mPixelColor;
    }
}
