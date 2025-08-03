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
import android.app.WallpaperColors;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.config.BaseFlags;
import com.android.wallpaper.module.InjectorProvider;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Interface for wallpaper info model.
 */
public abstract class WallpaperInfo implements Parcelable {

    private static final ExecutorService sExecutor = Executors.newCachedThreadPool();
    private ColorInfo mColorInfo = new ColorInfo();

    private PriorityQueue<String> mEffectNames = new PriorityQueue<>();

    protected final HashMap<String, String> mCropHints = new HashMap<>();

    public WallpaperInfo() {
    }

    protected WallpaperInfo(Parcel in) {
        mColorInfo = new ColorInfo(in.readParcelable(WallpaperColors.class.getClassLoader()),
                in.readInt());
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeParcelable(mColorInfo.getWallpaperColors(), flags);
        parcel.writeInt(mColorInfo.getPlaceholderColor());
    }

    public static final int BACKUP_NOT_ALLOWED = 0;
    public static final int BACKUP_ALLOWED = 1;

    /**
     * @param context
     * @return The title for this wallpaper, if applicable (as in a wallpaper "app" or live
     * wallpaper), or null if not applicable.
     */
    public String getTitle(Context context) {
        return null;
    }

    /**
     * Returns the content description for this wallpaper, or null if none exists.
     */
    public String getContentDescription(Context context) {
        return null;
    }

    /**
     * @return The available attributions for this wallpaper, as a list of strings. These represent
     * the author / website or any other attribution required to be displayed for this wallpaper
     * regarding authorship, ownership, etc.
     */
    public abstract List<String> getAttributions(Context context);

    /**
     * Returns the base (remote) image URL for this wallpaper, or null if none exists.
     */
    public String getBaseImageUrl() {
        return null;
    }

    /**
     * Returns the action or "explore" URL for the wallpaper, or null if none exists.
     */
    public String getActionUrl(Context unused) {
        return null;
    }

    /** Returns the URI corresponding to the wallpaper, or null if none exists. */
    public Uri getUri() {
        return null;
    }

    /**
     * Returns the icon to use to represent the action link corresponding to
     * {@link #getActionUrl(Context)}
     */

    /**
     * @param context
     * @return An overlay icon to be used instead of a thumbnail, if appropriate, or null if not
     * applicable.
     */
    public Drawable getOverlayIcon(Context context) {
        return null;
    }

    ;

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * @param context The client application's context.
     * @return The {@link Asset} representing the wallpaper image.
     */
    public abstract Asset getAsset(Context context);

    /**
     * @param context The client application's context.
     * @return The {@link Asset} representing the wallpaper's thumbnail.
     */
    public abstract Asset getThumbAsset(Context context);

    /**
     * @param context The client application's context.
     * @return An {@link Asset} that is appropriately sized to be directly set to the desktop. By
     * default, this just the full wallpaper image asset (#getAsset) but subclasses may provide an
     * Asset sized exactly for the device's primary display (i.e., cropped prior to providing a
     * bitmap or input stream).
     */
    public Asset getDesktopAsset(Context context) {
        return getAsset(context);
    }

    /**
     * @return the {@link android.app.WallpaperInfo} associated with this wallpaper, which is
     * generally present for live wallpapers, or null if there is none.
     */
    public android.app.WallpaperInfo getWallpaperComponent() {
        return null;
    }

    /**
     * Returns the ID of the collection this image is associated with, if any.
     */
    public abstract String getCollectionId(Context context);

    /**
     * Returns the ID of this wallpaper or null if there is no ID.
     */
    public String getWallpaperId() {
        return null;
    }

    /**
     * Returns the distinct ID of the stored wallpaper or null if there is no ID.
     */
    public String getStoredWallpaperId(Context context) {
        if (getWallpaperId() == null) {
            return null;
        }
        return getCollectionId(context) + "-" + getWallpaperId();
    }

    /**
     * Returns whether backup is allowed for this wallpaper.
     */
    @BackupPermission
    public int getBackupPermission() {
        return BACKUP_ALLOWED;
    }

    /**
     * Shows the appropriate preview activity for this WallpaperInfo.
     *
     * @param srcActivity
     * @param factory     A factory for showing the inline preview activity for within this app.
     *                    Only used for certain WallpaperInfo implementations that require an inline preview
     *                    (as opposed to some external preview activity).
     * @param requestCode Request code to pass in when starting the inline preview activity.
     */
    public abstract void showPreview(Activity srcActivity, InlinePreviewIntentFactory factory,
                                     int requestCode, boolean isAssetIdPresent);

    /**
     * Returns a Future to obtain a wallpaper color and a placeholder color calculated in a
     * background thread for this wallpaper's thumbnail.
     * If it's already available, the Future will return the color immediately.
     * This is intended to be a "best effort" attempt and might not obtain a color if no low res
     * thumbnail is available.
     */
    public Future<ColorInfo> computeColorInfo(Context context) {
        if (mColorInfo.getWallpaperColors() != null
                && mColorInfo.getPlaceholderColor() != Color.TRANSPARENT) {
            return CompletableFuture.completedFuture(mColorInfo);
        }
        final Context appContext = context.getApplicationContext();
        return sExecutor.submit(() -> {
            synchronized (WallpaperInfo.this) {
                if (mColorInfo.getWallpaperColors() != null
                        && mColorInfo.getPlaceholderColor() != Color.TRANSPARENT) {
                    return mColorInfo;
                }

                Asset thumbAsset = getThumbAsset(appContext);
                Bitmap lowResBitmap = thumbAsset.getLowResBitmap(appContext);
                if (lowResBitmap == null) {
                    return new ColorInfo(
                            new WallpaperColors(Color.valueOf(Color.TRANSPARENT), null, null),
                            Color.TRANSPARENT);
                }
                mColorInfo = new ColorInfo(WallpaperColors.fromBitmap(lowResBitmap));
                return mColorInfo;
            }
        });
    }

    /**
     * Remove the effect name from this wallpaper, only use it for logging.
     */
    public void removeEffectName(String effect) {
        mEffectNames.remove(effect);
    }

    /**
     * Add the effect name apply with this wallpaper, only use it for logging.
     */
    public void addEffectName(String effect) {
        mEffectNames.add(effect);
    }

    /**
     * Returns the effects apply with this wallpaper.
     */
    public String getEffectNames() {
        if (mEffectNames.isEmpty()) {
            return null;
        }
        String effectNames = "";
        Iterator value = mEffectNames.iterator();
        while (value.hasNext()) {
            if (!effectNames.isEmpty()) {
                effectNames += ",";
            }
            effectNames += value.next();
        }

        return effectNames;
    }

    /**
     * Whether backup is allowed for this type of wallpaper.
     */
    @IntDef({
            BACKUP_NOT_ALLOWED,
            BACKUP_ALLOWED
    })
    public @interface BackupPermission {
    }

    /**
     * Returns a group name under which this Wallpaper should be grouped when displayed in
     * a gallery, or an empty String if no grouping is required.
     */
    public String getGroupName(Context context) {
        return "";
    }

    /**
     * Returns the resource id of a drawable to use as a badge when displaying this wallpaper
     * in a gallery, or {@link Resources#ID_NULL} if no badge is required.
     */
    @DrawableRes
    public int getBadgeDrawableRes() {
        return Resources.ID_NULL;
    }

    /** Sets the crop {@link Rect} of each displaySize for this wallpaper. */
    public void setWallpaperCropHints(Map<Point, Rect> cropHints) {
        if (cropHints == null) {
            return;
        }

        cropHints.forEach((displaySize, rect) -> {
            if (rect != null) {
                mCropHints.put(displaySize.flattenToString(),
                        rect.flattenToString());
            }
        });
    }

    /** Returns the crop {@link Rect} of each displaySize for this wallpaper. */
    public @Nullable Map<Point, Rect> getWallpaperCropHints() {
        BaseFlags flags = InjectorProvider.getInjector().getFlags();
        boolean isMultiCropEnabled =
                flags.isMultiCropPreviewUiEnabled() && flags.isMultiCropEnabled();
        if (!isMultiCropEnabled) {
            return null;
        }

        Map<Point, Rect> cropHints = new HashMap<>();
        mCropHints.forEach(
                (displaySize, rect) -> cropHints.put(
                        Point.unflattenFromString(displaySize),
                        Rect.unflattenFromString(rect)));
        return cropHints;
    }

    /**
     * Inner class to keep wallpaper colors and placeholder color.
     */
    public static class ColorInfo {
        private WallpaperColors mWallpaperColors;
        private Integer mPlaceholderColor = Color.TRANSPARENT;

        public ColorInfo() {
        }

        public ColorInfo(WallpaperColors wallpaperColors) {
            mWallpaperColors = wallpaperColors;
            if (mWallpaperColors != null) {
                mPlaceholderColor = mWallpaperColors.getPrimaryColor().toArgb();
            }
        }

        public ColorInfo(WallpaperColors wallpaperColors, Integer placeholderColor) {
            mWallpaperColors = wallpaperColors;
            mPlaceholderColor = placeholderColor;
        }

        public WallpaperColors getWallpaperColors() {
            return mWallpaperColors;
        }

        public Integer getPlaceholderColor() {
            return mPlaceholderColor;
        }
    }

    public ColorInfo getColorInfo() {
        return mColorInfo;
    }
}
