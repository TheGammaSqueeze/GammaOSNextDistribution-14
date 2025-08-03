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
package com.android.wallpaper.module;

import static android.app.WallpaperManager.FLAG_LOCK;
import static android.app.WallpaperManager.FLAG_SYSTEM;
import static android.app.WallpaperManager.SetWallpaperFlags;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.model.StaticWallpaperPrefMetadata;
import com.android.wallpaper.model.WallpaperInfo;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Interface for classes which persist wallpapers to the system.
 */
public interface WallpaperPersister {

    int DEST_HOME_SCREEN = 0;
    int DEST_LOCK_SCREEN = 1;
    int DEST_BOTH = 2;

    /**
     * Sets a static individual wallpaper to the system via the WallpaperManager.
     *
     * @param wallpaper   Wallpaper model object. Wallpaper image will be set from the asset provided
     *                    to this method.
     * @param asset       Wallpaper asset from which to retrieve image data.
     * @param cropRect    Desired crop area of the wallpaper in post-scale units. If null, then the
     *                    wallpaper image will be set without any scaling or cropping.
     * @param scale       Scaling factor applied to the source image before setting the wallpaper to the
     *                    device.
     * @param destination The destination - where to set the wallpaper to.
     * @param callback    Called once the wallpaper was set or if an error occurred.
     */
    void setIndividualWallpaper(WallpaperInfo wallpaper, Asset asset, @Nullable Rect cropRect,
                                float scale, @Destination int destination, SetWallpaperCallback callback);

    /**
     * Sets an individual wallpaper to the system as the wallpaper in the current rotation along with
     * its metadata. Prevents automatic wallpaper backup to conserve user data.
     * <p>
     * This method should only be called off the main UI thread because it will compress and set the
     * bitmap on the same thread as the caller.
     *
     * @param wallpaperBitmap Cropped and scaled wallpaper bitmap. This bitmap will be persisted as-is
     *                        with no additional processing.
     * @param attributions    List of attribution items.
     * @param actionUrl       The action or "explore" URL for the wallpaper.
     * @param collectionId    ID of this wallpaper's collection.
     * @param remoteId        Remote ID of this wallpaper
     * @return Whether the set wallpaper operation was successful.
     */
    boolean setWallpaperInRotation(Bitmap wallpaperBitmap, List<String> attributions,
            String actionUrl, String collectionId, String remoteId);

    /**
     * Sets only the bitmap of a rotating wallpaper of the next rotation to the system and stores
     * the given static wallpaper data in the recent wallpapers list (and not metadata).
     * This function is used for the very first time setting the rotation wallpaper. It is called
     * after the user initiates rotation wallpaper and the first wallpaper is downloaded.
     *
     * @param wallpaperBitmap The rotating wallpaper's bitmap.
     * @param attributions List of attribution items.
     * @param actionUrl    The action or "explore" URL for the wallpaper.
     * @param collectionId ID of this wallpaper's collection.
     * @return wallpaper ID, which is a positive integer if the set wallpaper operation was
     * successful, or 0 otherwise. On Android versions prior to N, this method will always return
     * 1 if the operation was successful because wallpaper IDs are not supported prior to N.
     */
    int setWallpaperBitmapInNextRotation(Bitmap wallpaperBitmap, List<String> attributions,
            String actionUrl, String collectionId);

    /**
     * Persists rotating wallpaper metadata for the next rotation and finalizes the preview wallpaper
     * image so that it's visible as the actual device wallpaper.
     *
     * @param attributions List of attribution items.
     * @param actionUrl    The action or "explore" URL for the wallpaper.
     * @param collectionId ID of this wallpaper's collection.
     * @param wallpaperId  Wallpaper ID that on Android N and later uniquely identifies the wallpaper
     *                     image.
     * @param remoteId     Remote ID of this wallpaper.
     * @return Whether the operation succeeded.
     */
    boolean finalizeWallpaperForNextRotation(List<String> attributions, String actionUrl,
            String collectionId, int wallpaperId, String remoteId);

    /**
     * Finalizes wallpaper metadata by persisting them to SharedPreferences and finalizes the
     * wallpaper image for live rotating components by copying the "preview" image to the "final"
     * image file location.
     *
     * @param attributions      List of attribution items.
     * @param actionUrl         The action or "explore" URL for the wallpaper.
     * @param collectionId      ID of this wallpaper's collection.
     * @param wallpaperId       ID that uniquely identifies a wallpaper set to the
     *                          {@link android.app.WallpaperManager}.
     * @param remoteId          Remote ID of this wallpaper.
     * @param destination       Lock, home screen or both.
     * @return Whether the operation was successful.
     */
    boolean saveStaticWallpaperMetadata(List<String> attributions,
            String actionUrl,
            String collectionId,
            int wallpaperId,
            String remoteId,
            @Destination int destination
        );

    /**
     * Save static image wallpaper's meta to the system preferences.
     */
    boolean saveStaticWallpaperToPreferences(int destination,
            StaticWallpaperPrefMetadata metadata);

    /**
     * @return the flag indicating which wallpaper to set when we're trying to set a wallpaper with
     * no user intervention. The idea is that if there's a static wallpaper on lock, we will only
     * override home, otherwise both
     */
    int getDefaultWhichWallpaper();

    /**
     * Sets a wallpaper bitmap to the {@link android.app.WallpaperManager}.
     *
     * @return an integer wallpaper ID. This is an actual wallpaper ID on N and later versions of
     * Android, otherwise on pre-N versions of Android will return a positive integer when the
     * operation was successful and zero if the operation encountered an error.
     */
    int setBitmapToWallpaperManager(Bitmap wallpaperBitmap, Rect cropHint,
            boolean allowBackup, int whichWallpaper);

    /**
     * Sets a wallpaper stream to the {@link android.app.WallpaperManager}.
     *
     * @return an integer wallpaper ID. This is an actual wallpaper ID on N and later versions of
     * Android, otherwise on pre-N versions of Android will return a positive integer when the
     * operation was successful and zero if the operation encountered an error.
     */
    int setStreamToWallpaperManager(InputStream inputStream, @Nullable Rect cropHint,
            boolean allowBackup, int whichWallpaper);

    /**
     * Sets a wallpaper stream to the {@link android.app.WallpaperManager}.
     *
     * @return an integer wallpaper ID. This is an actual wallpaper ID on N and later versions of
     * Android, otherwise on pre-N versions of Android will return a positive integer when the
     * operation was successful and zero if the operation encountered an error.
     */
    int setStreamWithCropsToWallpaperManager(InputStream inputStream,
            @NonNull Map<Point, Rect> cropModel,
            boolean allowBackup, int whichWallpaper);

    /**
     * Saves the last wallpaper which showed a preview from this app.
     */
    void setWallpaperInfoInPreview(WallpaperInfo wallpaper);

    /**
     * Saves attributions to WallpaperPreferences for the last previewed wallpaper if it has an
     * {@link android.app.WallpaperInfo} component matching the one currently set to the
     * {@link android.app.WallpaperManager}.
     *
     * @param destination Live wallpaper destination (home/lock/both)
     */
    void onLiveWallpaperSet(@Destination int destination);

    /**
     * Updates lie wallpaper metadata by persisting them to SharedPreferences.
     *
     * @param wallpaperInfo Wallpaper model for the live wallpaper
     * @param effects Comma-separate list of effect (see {@link WallpaperInfo#getEffectNames})
     * @param destination Live wallpaper destination (home/lock/both)
     */
    void setLiveWallpaperMetadata(WallpaperInfo wallpaperInfo, String effects,
            @Destination int destination);

    /**
     * Interface for tracking success or failure of set wallpaper operations.
     */
    interface SetWallpaperCallback {
        void onSuccess(WallpaperInfo wallpaperInfo, @Destination int destination);

        void onError(@Nullable Throwable throwable);
    }

    /**
     * The possible destinations to which a wallpaper may be set.
     */
    @IntDef({
            DEST_HOME_SCREEN,
            DEST_LOCK_SCREEN,
            DEST_BOTH})
    @interface Destination {
    }

    /**
     * Converts a {@link Destination} to the corresponding set of {@link SetWallpaperFlags}.
     */
    @SetWallpaperFlags
    static int destinationToFlags(@Destination int destination) {
        switch (destination) {
            case DEST_HOME_SCREEN:
                return FLAG_SYSTEM;
            case DEST_LOCK_SCREEN:
                return FLAG_LOCK;
            case DEST_BOTH:
                return FLAG_SYSTEM | FLAG_LOCK;
            default:
                throw new AssertionError("Unknown @Destination");
        }
    }

    /**
     * Converts a set of {@link SetWallpaperFlags} to the corresponding {@link Destination}.
     */
    @Destination
    static int flagsToDestination(@SetWallpaperFlags int flags) {
        if (flags == (FLAG_SYSTEM | FLAG_LOCK)) {
            return DEST_BOTH;
        } else if (flags == FLAG_SYSTEM) {
            return DEST_HOME_SCREEN;
        } else if (flags == FLAG_LOCK) {
            return DEST_LOCK_SCREEN;
        } else {
            throw new AssertionError("Unknown @SetWallpaperFlags value");
        }
    }
}
