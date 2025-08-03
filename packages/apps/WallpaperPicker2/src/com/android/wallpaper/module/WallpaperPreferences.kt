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
package com.android.wallpaper.module

import android.app.WallpaperColors
import android.app.WallpaperManager.SetWallpaperFlags
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.text.TextUtils
import androidx.annotation.IntDef
import com.android.wallpaper.model.LiveWallpaperInfo
import com.android.wallpaper.model.LiveWallpaperPrefMetadata
import com.android.wallpaper.model.StaticWallpaperPrefMetadata
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.picker.customization.shared.model.WallpaperDestination
import com.android.wallpaper.picker.data.WallpaperModel.LiveWallpaperModel
import com.android.wallpaper.picker.data.WallpaperModel.StaticWallpaperModel

/** Interface for persisting and retrieving wallpaper specific preferences. */
interface WallpaperPreferences {

    /** Returns the wallpaper presentation mode. */
    @PresentationMode fun getWallpaperPresentationMode(): Int

    /** Sets the presentation mode of the current wallpaper. */
    fun setWallpaperPresentationMode(@PresentationMode presentationMode: Int)

    /** Returns the home attributions as a list. */
    fun getHomeWallpaperAttributions(): List<String?>?

    /**
     * Sets the attributions for the current home wallpaper. Clears existing attributions if any
     * exist.
     */
    fun setHomeWallpaperAttributions(attributions: List<String?>?)

    /** Returns the home wallpaper's action URL or null if there is none. */
    fun getHomeWallpaperActionUrl(): String?

    /** Sets the home wallpaper's action URL. */
    fun setHomeWallpaperActionUrl(actionUrl: String?)

    /** Returns the home wallpaper's collection ID or null if there is none. */
    fun getHomeWallpaperCollectionId(): String?

    /** Sets the home wallpaper's collection ID. */
    fun setHomeWallpaperCollectionId(collectionId: String?)

    /** Removes all home metadata from SharedPreferences. */
    fun clearHomeWallpaperMetadata()

    /** Set homescreen static image wallpaper metadata to SharedPreferences. */
    fun setHomeStaticImageWallpaperMetadata(metadata: StaticWallpaperPrefMetadata)

    /** Set homescreen live wallpaper metadata to SharedPreferences. */
    fun setHomeLiveWallpaperMetadata(metadata: LiveWallpaperPrefMetadata)

    /** Returns the home wallpaper's bitmap hash code or 0 if there is none. */
    fun getHomeWallpaperHashCode(): Long

    /** Sets the home wallpaper's bitmap hash code if it is an individual image. */
    fun setHomeWallpaperHashCode(hashCode: Long)

    /** Gets the home wallpaper's service name, which is present for live wallpapers. */
    fun getHomeWallpaperServiceName(): String?

    /** Sets the home wallpaper's service name, which is present for live wallpapers. */
    fun setHomeWallpaperServiceName(serviceName: String?)

    /**
     * Gets the home wallpaper's ID, which is provided by WallpaperManager for static wallpapers.
     */
    fun getHomeWallpaperManagerId(): Int

    /**
     * Sets the home wallpaper's ID, which is provided by WallpaperManager for static wallpapers.
     */
    fun setHomeWallpaperManagerId(homeWallpaperId: Int)

    /** Gets the home wallpaper's remote identifier. */
    fun getHomeWallpaperRemoteId(): String?

    /**
     * Sets the home wallpaper's remote identifier to SharedPreferences. This should be a string
     * which uniquely identifies the currently set home wallpaper in the context of a remote
     * wallpaper collection.
     */
    fun setHomeWallpaperRemoteId(wallpaperRemoteId: String?)

    /** Gets the home wallpaper's identifier used to index into the list of recent wallpapers. */
    fun getHomeWallpaperRecentsKey(): String?

    /** Sets the home wallpaper's identifier used to index into the list of recent wallpapers. */
    fun setHomeWallpaperRecentsKey(recentsKey: String?)

    /** Gets the home wallpaper's effects. */
    fun getHomeWallpaperEffects(): String?

    /** Sets the home wallpaper's effects to SharedPreferences. */
    fun setHomeWallpaperEffects(wallpaperEffects: String?)

    /** Returns the lock screen attributions as a list. */
    fun getLockWallpaperAttributions(): List<String?>?

    /**
     * Sets the attributions for the current lock screen wallpaper. Clears existing attributions if
     * any exist.
     */
    fun setLockWallpaperAttributions(attributions: List<String?>?)

    /** Returns the lock wallpaper's action URL or null if there is none. */
    fun getLockWallpaperActionUrl(): String?

    /** Sets the lock wallpaper's action URL. */
    fun setLockWallpaperActionUrl(actionUrl: String?)

    /** Returns the lock wallpaper's collection ID or null if there is none. */
    fun getLockWallpaperCollectionId(): String?

    /** Sets the lock wallpaper's collection ID. */
    fun setLockWallpaperCollectionId(collectionId: String?)

    /** Removes all lock screen metadata from SharedPreferences. */
    fun clearLockWallpaperMetadata()

    /** Set lockscreen static image wallpaper metadata to SharedPreferences. */
    fun setLockStaticImageWallpaperMetadata(metadata: StaticWallpaperPrefMetadata)

    /** Set lockscreen live wallpaper metadata to SharedPreferences. */
    fun setLockLiveWallpaperMetadata(metadata: LiveWallpaperPrefMetadata)

    /** Returns the lock screen wallpaper's bitmap hash code or 0 if there is none. */
    fun getLockWallpaperHashCode(): Long

    /** Sets the lock screen wallpaper's bitmap hash code if it is an individual image. */
    fun setLockWallpaperHashCode(hashCode: Long)

    /** Gets the lock wallpaper's service name, which is present for live wallpapers. */
    fun getLockWallpaperServiceName(): String?

    /** Sets the lock wallpaper's service name, which is present for live wallpapers. */
    fun setLockWallpaperServiceName(serviceName: String?)

    /**
     * Gets the lock wallpaper's ID, which is provided by WallpaperManager for static wallpapers.
     */
    fun getLockWallpaperManagerId(): Int

    /**
     * Sets the lock wallpaper's ID, which is provided by WallpaperManager for static wallpapers.
     */
    fun setLockWallpaperManagerId(lockWallpaperId: Int)

    /** Gets the lock wallpaper's remote identifier. */
    fun getLockWallpaperRemoteId(): String?

    /**
     * Sets the lock wallpaper's remote identifier to SharedPreferences. This should be a string
     * which uniquely identifies the currently set lock wallpaper in the context of a remote
     * wallpaper collection.
     */
    fun setLockWallpaperRemoteId(wallpaperRemoteId: String?)

    /** Gets lock home wallpaper's identifier used to index into the list of recent wallpapers. */
    fun getLockWallpaperRecentsKey(): String?

    /** Sets lock home wallpaper's identifier used to index into the list of recent wallpapers. */
    fun setLockWallpaperRecentsKey(recentsKey: String?)

    /** Gets the lock wallpaper's effects. */
    // TODO (b/307939748): Log lock wallpaper effects. We need this function for snapshot logging
    fun getLockWallpaperEffects(): String?

    /** Sets the lock wallpaper's effects to SharedPreferences. */
    fun setLockWallpaperEffects(wallpaperEffects: String?)

    /** Persists the timestamp of a daily wallpaper rotation that just occurred. */
    fun addDailyRotation(timestamp: Long)

    /**
     * Returns the timestamp of the last wallpaper daily rotation or -1 if there has never been a
     * daily wallpaper rotation on the user's device.
     */
    fun getLastDailyRotationTimestamp(): Long

    /**
     * Returns the daily wallpaper enabled timestamp in milliseconds since Unix epoch, or -1 if
     * daily wallpaper is not currently enabled.
     */
    fun getDailyWallpaperEnabledTimestamp(): Long

    /**
     * Persists the timestamp when daily wallpaper feature was last enabled.
     *
     * @param timestamp Milliseconds since Unix epoch.
     */
    fun setDailyWallpaperEnabledTimestamp(timestamp: Long)

    /**
     * Clears the persisted daily rotation timestamps and the "daily wallpaper enabled" timestamp.
     * Called if daily rotation is disabled.
     */
    fun clearDailyRotations()

    /**
     * Returns the timestamp of the most recent daily logging event, in milliseconds since Unix
     * epoch. Returns -1 if the very first daily logging event has not occurred yet.
     */
    fun getLastDailyLogTimestamp(): Long

    /**
     * Sets the timestamp of the most recent daily logging event.
     *
     * @param timestamp Milliseconds since Unix epoch.
     */
    fun setLastDailyLogTimestamp(timestamp: Long)

    /**
     * Returns the timestamp of the last time the app was noted to be active; i.e. the last time an
     * activity entered the foreground (milliseconds since Unix epoch).
     */
    fun getLastAppActiveTimestamp(): Long

    /**
     * Sets the timestamp of the last time the app was noted to be active; i.e. the last time an
     * activity entered the foreground.
     *
     * @param timestamp Milliseconds since Unix epoch.
     */
    fun setLastAppActiveTimestamp(timestamp: Long)

    /**
     * Sets the last rotation status for daily wallpapers with a timestamp.
     *
     * @param status Last status code of daily rotation.
     * @param timestamp Milliseconds since Unix epoch.
     */
    fun setDailyWallpaperRotationStatus(status: Int, timestamp: Long)

    /**
     * Sets the status of whether a wallpaper is currently pending being set (i.e., user tapped the
     * UI to set a wallpaper but it has not yet been actually set on the device). Does so in a
     * synchronous manner so a caller may be assured that the underlying store has been updated when
     * this method returns.
     */
    fun setPendingWallpaperSetStatusSync(@PendingWallpaperSetStatus setStatus: Int)

    /** Gets the status of whether a wallpaper is currently pending being set. */
    @PendingWallpaperSetStatus fun getPendingWallpaperSetStatus(): Int

    /**
     * Sets the status of whether a wallpaper is currently pending being set (i.e., user tapped the
     * UI to set a wallpaper but it has not yet been actually set on the device). Does so in an
     * asynchronous manner so writing the preference to the underlying store doesn't block the
     * calling thread.
     */
    fun setPendingWallpaperSetStatus(@PendingWallpaperSetStatus setStatus: Int)

    /**
     * Sets whether a daily wallpaper update is pending. Writes status to memory and also to disk
     * before returning.
     */
    fun setPendingDailyWallpaperUpdateStatusSync(
        @PendingDailyWallpaperUpdateStatus updateStatus: Int,
    )

    /** Returns whether a daily wallpaper update is pending. */
    @PendingDailyWallpaperUpdateStatus fun getPendingDailyWallpaperUpdateStatus(): Int

    /**
     * Sets whether a daily wallpaper update is pending. Writes status to memory immediately and to
     * disk after returning.
     */
    fun setPendingDailyWallpaperUpdateStatus(@PendingDailyWallpaperUpdateStatus updateStatus: Int)

    /** Return the count of wallpaper picker launch. */
    fun getAppLaunchCount(): Int

    /** Return the date for the first time to launch wallpaper picker. */
    fun getFirstLaunchDateSinceSetup(): Int

    /** Increments the number of wallpaper picker launch. */
    fun incrementAppLaunched()

    /** Returns the date for the first time to apply a wallpaper. */
    fun getFirstWallpaperApplyDateSinceSetup(): Int

    /**
     * Sets wallpapers colors of wallpaper's id.
     *
     * @param storedWallpaperId wallpaper id.
     * @param wallpaperColors Colors extracted from an image via quantization.
     */
    fun storeWallpaperColors(storedWallpaperId: String?, wallpaperColors: WallpaperColors?)

    /**
     * Returns the wallpaper colors from wallpaper's id.
     *
     * @param storedWallpaperId wallpaper id.
     */
    fun getWallpaperColors(storedWallpaperId: String): WallpaperColors?

    /**
     * Update currently set daily wallpaper info.
     *
     * @param destination The wallpaper destination, 1: home, 2: lockscreen, 3: both.
     * @param collectionId wallpaper category.
     * @param wallpaperId wallpaper id.
     */
    fun updateDailyWallpaperSet(
        @WallpaperPersister.Destination destination: Int,
        collectionId: String?,
        wallpaperId: String?,
    )

    /**
     * Stores the given live wallpaper in the recent wallpapers list
     *
     * @param which flag indicating the wallpaper destination
     * @param wallpaperId unique identifier for this wallpaper
     * @param wallpaper [LiveWallpaperInfo] for the applied wallpaper
     * @param colors WallpaperColors to be used as placeholder for quickswitching
     */
    fun storeLatestWallpaper(
        @SetWallpaperFlags which: Int,
        wallpaperId: String,
        wallpaper: LiveWallpaperInfo,
        colors: WallpaperColors,
    )

    /**
     * Stores the given static wallpaper data in the recent wallpapers list.
     *
     * @param which flag indicating the wallpaper destination
     * @param wallpaperId unique identifier for this wallpaper
     * @param wallpaper [WallpaperInfo] for the applied wallpaper
     * @param croppedWallpaperBitmap wallpaper bitmap exactly as applied to WallaperManager
     * @param colors WallpaperColors to be used as placeholder for quickswitching
     */
    fun storeLatestWallpaper(
        @SetWallpaperFlags which: Int,
        wallpaperId: String,
        wallpaper: WallpaperInfo,
        croppedWallpaperBitmap: Bitmap,
        colors: WallpaperColors,
    )

    /**
     * Stores the given static wallpaper data in the recent wallpapers list.
     *
     * @param which flag indicating the wallpaper destination
     * @param wallpaperId unique identifier for this wallpaper
     * @param attributions List of attribution items.
     * @param actionUrl The action or "explore" URL for the wallpaper.
     * @param collectionId identifier of this wallpaper's collection.
     * @param croppedWallpaperBitmap wallpaper bitmap exactly as applied to WallaperManager
     * @param colors [WallpaperColors] to be used as placeholder for quickswitching
     */
    fun storeLatestWallpaper(
        @SetWallpaperFlags which: Int,
        wallpaperId: String,
        attributions: List<String>?,
        actionUrl: String?,
        collectionId: String?,
        croppedWallpaperBitmap: Bitmap,
        colors: WallpaperColors,
    )

    /**
     * Add a static wallpaper to recent wallpapers as json array, saved in preferences.
     *
     * @param destination destination where the wallpaper is set to
     * @param wallpaperModel static wallpaper model
     * @param bitmap full sie bitmap of the static wallpaper
     * @param cropHints crop hints of the static wallpaper
     */
    suspend fun addStaticWallpaperToRecentWallpapers(
        destination: WallpaperDestination,
        wallpaperModel: StaticWallpaperModel,
        bitmap: Bitmap,
        cropHints: Map<Point, Rect>?,
    )

    /**
     * Add a live wallpaper to recent wallpapers as json array, saved in preferences.
     *
     * @param destination destination where the wallpaper is set to
     * @param wallpaperModel live wallpaper model
     */
    suspend fun addLiveWallpaperToRecentWallpapers(
        destination: WallpaperDestination,
        wallpaperModel: LiveWallpaperModel,
    )

    /** Sets whether the preview tooltip should be shown. */
    fun setHasPreviewTooltipBeenShown(hasTooltipBeenShown: Boolean)

    /** Gets whether the preview tooltip should be shown. */
    fun getHasPreviewTooltipBeenShown(): Boolean

    /** The possible wallpaper presentation modes, i.e., either "static" or "rotating". */
    @IntDef(PRESENTATION_MODE_STATIC, PRESENTATION_MODE_ROTATING) annotation class PresentationMode

    /** Possible status of whether a wallpaper set operation is pending or not. */
    @IntDef(WALLPAPER_SET_NOT_PENDING, WALLPAPER_SET_PENDING)
    annotation class PendingWallpaperSetStatus

    /** Possible status of whether a wallpaper set operation is pending or not. */
    @IntDef(DAILY_WALLPAPER_UPDATE_NOT_PENDING, DAILY_WALLPAPER_UPDATE_PENDING)
    annotation class PendingDailyWallpaperUpdateStatus

    companion object {
        /**
         * Generates a default key to look up a wallpaper in the list of recent wallpapers.
         *
         * This key can be used as a fallback when [.getHomeWallpaperRecentsKey] or
         * [.getLockWallpaperRecentsKey] return null.
         *
         * @param remoteId wallpaper's remote id
         * @param hashCode wallpaper's hash code
         * @return the recents key
         */
        fun generateRecentsKey(remoteId: String?, hashCode: Long): String? {
            return if (!TextUtils.isEmpty(remoteId)) {
                remoteId
            } else if (hashCode > 0) {
                hashCode.toString()
            } else {
                null
            }
        }

        const val PRESENTATION_MODE_STATIC = 1
        const val PRESENTATION_MODE_ROTATING = 2
        const val WALLPAPER_SET_NOT_PENDING = 0
        const val WALLPAPER_SET_PENDING = 1
        const val DAILY_WALLPAPER_UPDATE_NOT_PENDING = 0
        const val DAILY_WALLPAPER_UPDATE_PENDING = 1
    }
}
