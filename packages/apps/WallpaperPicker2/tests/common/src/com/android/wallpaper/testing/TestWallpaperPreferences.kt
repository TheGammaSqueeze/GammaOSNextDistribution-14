/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.wallpaper.testing

import android.app.WallpaperColors
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import com.android.wallpaper.model.LiveWallpaperInfo
import com.android.wallpaper.model.LiveWallpaperPrefMetadata
import com.android.wallpaper.model.StaticWallpaperPrefMetadata
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.module.WallpaperPersister
import com.android.wallpaper.module.WallpaperPreferences
import com.android.wallpaper.module.WallpaperPreferences.PendingDailyWallpaperUpdateStatus
import com.android.wallpaper.module.WallpaperPreferences.PendingWallpaperSetStatus
import com.android.wallpaper.module.WallpaperPreferences.PresentationMode
import com.android.wallpaper.picker.customization.shared.model.WallpaperDestination
import com.android.wallpaper.picker.data.WallpaperModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** Test implementation of the WallpaperPreferences interface. Just keeps prefs in memory. */
@Singleton
open class TestWallpaperPreferences @Inject constructor() : WallpaperPreferences {

    private var appLaunchCount = 0
    private var firstLaunchDate = 0
    private var firstWallpaperApplyDate = 0
    @PresentationMode private var wallpaperPresentationMode: Int
    private var homeScreenAttributions: List<String?>?
    private var homeScreenBitmapHashCode: Long = 0
    private var homeWallpaperManagerId = 0
    private var homeScreenServiceName: String? = null
    private var lockScreenServiceName: String? = null
    private var homeActionUrl: String? = null
    private val homeBaseImageUrl: String? = null
    private var homeCollectionId: String? = null
    private var homeWallpaperRemoteId: String? = null
    private var homeWallpaperRecentsKey: String? = null
    private var lockScreenAttributions: List<String?>?
    private var lockScreenBitmapHashCode: Long = 0
    private var lockWallpaperManagerId = 0
    private var lockActionUrl: String? = null
    private var lockCollectionId: String? = null
    private var lockWallpaperRemoteId: String? = null
    private var lockWallpaperRecentsKey: String? = null
    private val dailyRotations: MutableList<Long>
    private var dailyWallpaperEnabledTimestamp: Long
    private var lastDailyLogTimestamp: Long
    private var lastAppActiveTimestamp: Long = 0
    private var lastDailyWallpaperRotationStatus: Int
    private var lastDailyWallpaperRotationStatusTimestamp: Long = 0
    private val lastSyncTimestamp: Long = 0

    @PendingWallpaperSetStatus private var mPendingWallpaperSetStatus: Int

    @PendingDailyWallpaperUpdateStatus private var mPendingDailyWallpaperUpdateStatus = 0
    private var mNumDaysDailyRotationFailed = 0
    private var mNumDaysDailyRotationNotAttempted = 0
    private var mHomeWallpaperEffects: String? = null
    private var mLockWallpaperEffects: String? = null
    private var mHomeStaticWallpaperPrefMetadata: StaticWallpaperPrefMetadata? = null
    private var mLockStaticWallpaperPrefMetadata: StaticWallpaperPrefMetadata? = null
    private var mHomeLiveWallpaperPrefMetadata: LiveWallpaperPrefMetadata? = null
    private var mLockLiveWallpaperPrefMetadata: LiveWallpaperPrefMetadata? = null
    private val mWallStoredColor: HashMap<String, String> = HashMap()

    private var hasPreviewTooltipBeenShown = true

    init {
        wallpaperPresentationMode = WallpaperPreferences.PRESENTATION_MODE_STATIC
        homeScreenAttributions = mutableListOf<String?>("Android wallpaper")
        lockScreenAttributions = mutableListOf("Android wallpaper")
        dailyRotations = ArrayList()
        dailyWallpaperEnabledTimestamp = -1
        lastDailyLogTimestamp = -1
        lastDailyWallpaperRotationStatus = -1
        mPendingWallpaperSetStatus = WallpaperPreferences.WALLPAPER_SET_NOT_PENDING
    }

    override fun getWallpaperPresentationMode(): Int {
        return wallpaperPresentationMode
    }

    override fun setWallpaperPresentationMode(@PresentationMode presentationMode: Int) {
        wallpaperPresentationMode = presentationMode
    }

    override fun getHomeWallpaperAttributions(): List<String?>? {
        return homeScreenAttributions
    }

    override fun setHomeWallpaperAttributions(attributions: List<String?>?) {
        homeScreenAttributions = attributions
    }

    override fun getHomeWallpaperActionUrl(): String? {
        return homeActionUrl
    }

    override fun setHomeWallpaperActionUrl(actionUrl: String?) {
        homeActionUrl = actionUrl
    }

    override fun getHomeWallpaperCollectionId(): String? {
        return homeCollectionId
    }

    override fun setHomeWallpaperCollectionId(collectionId: String?) {
        homeCollectionId = collectionId
    }

    override fun clearHomeWallpaperMetadata() {
        homeScreenAttributions = null
        wallpaperPresentationMode = WallpaperPreferences.PRESENTATION_MODE_STATIC
        homeScreenBitmapHashCode = 0
        homeScreenServiceName = null
        homeWallpaperManagerId = 0
    }

    override fun setHomeStaticImageWallpaperMetadata(metadata: StaticWallpaperPrefMetadata) {
        mHomeStaticWallpaperPrefMetadata = metadata
    }

    override fun setHomeLiveWallpaperMetadata(metadata: LiveWallpaperPrefMetadata) {
        mHomeLiveWallpaperPrefMetadata = metadata
    }

    override fun getHomeWallpaperHashCode(): Long {
        return homeScreenBitmapHashCode
    }

    override fun setHomeWallpaperHashCode(hashCode: Long) {
        homeScreenBitmapHashCode = hashCode
    }

    override fun getHomeWallpaperServiceName(): String? {
        return homeScreenServiceName
    }

    override fun setHomeWallpaperServiceName(serviceName: String?) {
        homeScreenServiceName = serviceName
        setFirstWallpaperApplyDateIfNeeded()
    }

    override fun getHomeWallpaperManagerId(): Int {
        return homeWallpaperManagerId
    }

    override fun setHomeWallpaperManagerId(homeWallpaperId: Int) {
        homeWallpaperManagerId = homeWallpaperId
    }

    override fun getHomeWallpaperRemoteId(): String? {
        return homeWallpaperRemoteId
    }

    override fun setHomeWallpaperRemoteId(wallpaperRemoteId: String?) {
        homeWallpaperRemoteId = wallpaperRemoteId
        setFirstWallpaperApplyDateIfNeeded()
    }

    override fun getHomeWallpaperRecentsKey(): String? {
        return homeWallpaperRecentsKey
    }

    override fun setHomeWallpaperRecentsKey(recentsKey: String?) {
        homeWallpaperRecentsKey = recentsKey
    }

    override fun getHomeWallpaperEffects(): String? {
        return mHomeWallpaperEffects
    }

    override fun setHomeWallpaperEffects(wallpaperEffects: String?) {
        mHomeWallpaperEffects = wallpaperEffects
    }

    override fun getLockWallpaperAttributions(): List<String?>? {
        return lockScreenAttributions
    }

    override fun setLockWallpaperAttributions(attributions: List<String?>?) {
        lockScreenAttributions = attributions
    }

    override fun getLockWallpaperActionUrl(): String? {
        return lockActionUrl
    }

    override fun setLockWallpaperActionUrl(actionUrl: String?) {
        lockActionUrl = actionUrl
    }

    override fun getLockWallpaperCollectionId(): String? {
        return lockCollectionId
    }

    override fun setLockWallpaperCollectionId(collectionId: String?) {
        lockCollectionId = collectionId
    }

    override fun clearLockWallpaperMetadata() {
        lockScreenAttributions = null
        lockScreenBitmapHashCode = 0
        lockWallpaperManagerId = 0
    }

    override fun setLockStaticImageWallpaperMetadata(metadata: StaticWallpaperPrefMetadata) {
        mLockStaticWallpaperPrefMetadata = metadata
    }

    override fun setLockLiveWallpaperMetadata(metadata: LiveWallpaperPrefMetadata) {
        mLockLiveWallpaperPrefMetadata = metadata
    }

    override fun getLockWallpaperHashCode(): Long {
        return lockScreenBitmapHashCode
    }

    override fun setLockWallpaperHashCode(hashCode: Long) {
        lockScreenBitmapHashCode = hashCode
    }

    override fun getLockWallpaperServiceName(): String? {
        return lockScreenServiceName
    }

    override fun setLockWallpaperServiceName(serviceName: String?) {
        lockScreenServiceName = serviceName
    }

    override fun getLockWallpaperManagerId(): Int {
        return lockWallpaperManagerId
    }

    override fun setLockWallpaperManagerId(lockWallpaperId: Int) {
        lockWallpaperManagerId = lockWallpaperId
    }

    override fun getLockWallpaperRemoteId(): String? {
        return lockWallpaperRemoteId
    }

    override fun setLockWallpaperRemoteId(wallpaperRemoteId: String?) {
        lockWallpaperRemoteId = wallpaperRemoteId
        setFirstWallpaperApplyDateIfNeeded()
    }

    override fun getLockWallpaperRecentsKey(): String? {
        return lockWallpaperRecentsKey
    }

    override fun setLockWallpaperRecentsKey(recentsKey: String?) {
        lockWallpaperRecentsKey = recentsKey
    }

    override fun getLockWallpaperEffects(): String? {
        return mLockWallpaperEffects
    }

    override fun setLockWallpaperEffects(wallpaperEffects: String?) {
        mLockWallpaperEffects = wallpaperEffects
    }

    override fun addDailyRotation(timestamp: Long) {
        dailyRotations.add(timestamp)
    }

    override fun getLastDailyRotationTimestamp(): Long {
        return if (dailyRotations.size == 0) {
            -1
        } else dailyRotations[dailyRotations.size - 1]
    }

    override fun getDailyWallpaperEnabledTimestamp(): Long {
        return dailyWallpaperEnabledTimestamp
    }

    override fun setDailyWallpaperEnabledTimestamp(timestamp: Long) {
        dailyWallpaperEnabledTimestamp = timestamp
    }

    override fun clearDailyRotations() {
        dailyRotations.clear()
    }

    override fun getLastDailyLogTimestamp(): Long {
        return lastDailyLogTimestamp
    }

    override fun setLastDailyLogTimestamp(timestamp: Long) {
        lastDailyLogTimestamp = timestamp
    }

    override fun getLastAppActiveTimestamp(): Long {
        return lastAppActiveTimestamp
    }

    override fun setLastAppActiveTimestamp(timestamp: Long) {
        lastAppActiveTimestamp = timestamp
    }

    override fun setDailyWallpaperRotationStatus(status: Int, timestamp: Long) {
        lastDailyWallpaperRotationStatus = status
        lastDailyWallpaperRotationStatusTimestamp = timestamp
    }

    override fun setPendingWallpaperSetStatusSync(@PendingWallpaperSetStatus setStatus: Int) {
        mPendingWallpaperSetStatus = setStatus
    }

    @PendingWallpaperSetStatus
    override fun getPendingWallpaperSetStatus(): Int {
        return mPendingWallpaperSetStatus
    }

    override fun setPendingWallpaperSetStatus(@PendingWallpaperSetStatus setStatus: Int) {
        mPendingWallpaperSetStatus = setStatus
    }

    override fun setPendingDailyWallpaperUpdateStatusSync(
        @PendingDailyWallpaperUpdateStatus updateStatus: Int
    ) {
        mPendingDailyWallpaperUpdateStatus = updateStatus
    }

    @PendingDailyWallpaperUpdateStatus
    override fun getPendingDailyWallpaperUpdateStatus(): Int {
        return mPendingDailyWallpaperUpdateStatus
    }

    override fun setPendingDailyWallpaperUpdateStatus(
        @PendingDailyWallpaperUpdateStatus updateStatus: Int
    ) {
        mPendingDailyWallpaperUpdateStatus = updateStatus
    }

    override fun getAppLaunchCount(): Int {
        return appLaunchCount
    }

    override fun getFirstLaunchDateSinceSetup(): Int {
        return firstLaunchDate
    }

    override fun incrementAppLaunched() {
        if (getFirstLaunchDateSinceSetup() == 0) {
            setFirstLaunchDateSinceSetup(getCurrentDate())
        }
        val appLaunchCount = getAppLaunchCount()
        if (appLaunchCount < Int.MAX_VALUE) {
            setAppLaunchCount(appLaunchCount + 1)
        }
    }

    override fun getFirstWallpaperApplyDateSinceSetup(): Int {
        return firstWallpaperApplyDate
    }

    override fun storeWallpaperColors(
        storedWallpaperId: String?,
        wallpaperColors: WallpaperColors?
    ) {
        if (storedWallpaperId == null || wallpaperColors == null) {
            return
        }
        val primaryColor = wallpaperColors.primaryColor
        var value = java.lang.String(primaryColor.toArgb().toString()) as String
        val secondaryColor = wallpaperColors.secondaryColor
        if (secondaryColor != null) {
            value += "," + secondaryColor.toArgb()
        }
        val tertiaryColor = wallpaperColors.tertiaryColor
        if (tertiaryColor != null) {
            value += "," + tertiaryColor.toArgb()
        }
        mWallStoredColor[storedWallpaperId] = value
    }

    override fun getWallpaperColors(storedWallpaperId: String): WallpaperColors? {
        if (mWallStoredColor.isEmpty()) {
            return null
        }
        val value = mWallStoredColor[storedWallpaperId]
        if (value == "") {
            return null
        }
        val colorStrings =
            value!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val colorPrimary = Color.valueOf(colorStrings[0].toInt())
        var colorSecondary: Color? = null
        if (colorStrings.size >= 2) {
            colorSecondary = Color.valueOf(colorStrings[1].toInt())
        }
        var colorTerTiary: Color? = null
        if (colorStrings.size >= 3) {
            colorTerTiary = Color.valueOf(colorStrings[2].toInt())
        }
        return WallpaperColors(
            colorPrimary,
            colorSecondary,
            colorTerTiary,
            WallpaperColors.HINT_FROM_BITMAP
        )
    }

    override fun updateDailyWallpaperSet(
        @WallpaperPersister.Destination destination: Int,
        collectionId: String?,
        wallpaperId: String?
    ) {
        // Assign wallpaper info by destination.
        when (destination) {
            WallpaperPersister.DEST_HOME_SCREEN -> {
                setHomeWallpaperCollectionId(collectionId!!)
                setHomeWallpaperRemoteId(wallpaperId)
            }
            WallpaperPersister.DEST_LOCK_SCREEN -> {
                setLockWallpaperCollectionId(collectionId!!)
                setLockWallpaperRemoteId(wallpaperId!!)
            }
            WallpaperPersister.DEST_BOTH -> {
                setHomeWallpaperCollectionId(collectionId!!)
                setHomeWallpaperRemoteId(wallpaperId)
                setLockWallpaperCollectionId(collectionId)
                setLockWallpaperRemoteId(wallpaperId!!)
            }
        }
    }

    override fun storeLatestWallpaper(
        which: Int,
        wallpaperId: String,
        wallpaper: LiveWallpaperInfo,
        colors: WallpaperColors
    ) {}

    override fun storeLatestWallpaper(
        which: Int,
        wallpaperId: String,
        wallpaper: WallpaperInfo,
        croppedWallpaperBitmap: Bitmap,
        colors: WallpaperColors
    ) {}

    override fun storeLatestWallpaper(
        which: Int,
        wallpaperId: String,
        attributions: List<String>?,
        actionUrl: String?,
        collectionId: String?,
        croppedWallpaperBitmap: Bitmap,
        colors: WallpaperColors
    ) {}

    override suspend fun addStaticWallpaperToRecentWallpapers(
        destination: WallpaperDestination,
        wallpaperModel: WallpaperModel.StaticWallpaperModel,
        bitmap: Bitmap,
        cropHints: Map<Point, Rect>?,
    ) {}

    override suspend fun addLiveWallpaperToRecentWallpapers(
        destination: WallpaperDestination,
        wallpaperModel: WallpaperModel.LiveWallpaperModel
    ) {}

    override fun setHasPreviewTooltipBeenShown(hasTooltipBeenShown: Boolean) {
        this.hasPreviewTooltipBeenShown = hasTooltipBeenShown
    }

    override fun getHasPreviewTooltipBeenShown(): Boolean {
        return hasPreviewTooltipBeenShown
    }

    private fun setAppLaunchCount(count: Int) {
        appLaunchCount = count
    }

    private fun setFirstLaunchDateSinceSetup(firstLaunchDate: Int) {
        this.firstLaunchDate = firstLaunchDate
    }

    private fun setFirstWallpaperApplyDateSinceSetup(firstWallpaperApplyDate: Int) {
        this.firstWallpaperApplyDate = firstWallpaperApplyDate
    }

    private fun setFirstWallpaperApplyDateIfNeeded() {
        if (getFirstWallpaperApplyDateSinceSetup() == 0) {
            setFirstWallpaperApplyDateSinceSetup(getCurrentDate())
        }
    }

    private fun getCurrentDate(): Int {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val format = SimpleDateFormat("yyyyMMdd", Locale.US)
        return format.format(calendar.time).toInt()
    }
}
