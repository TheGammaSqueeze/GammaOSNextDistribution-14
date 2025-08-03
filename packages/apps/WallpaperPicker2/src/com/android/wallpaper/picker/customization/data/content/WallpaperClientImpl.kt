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
 *
 */

package com.android.wallpaper.picker.customization.data.content

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.app.WallpaperManager.FLAG_LOCK
import android.app.WallpaperManager.FLAG_SYSTEM
import android.app.WallpaperManager.SetWallpaperFlags
import android.content.ComponentName
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import android.os.Looper
import android.util.Log
import androidx.collection.ArrayMap
import com.android.wallpaper.asset.BitmapUtils
import com.android.wallpaper.model.CreativeCategory
import com.android.wallpaper.model.LiveWallpaperPrefMetadata
import com.android.wallpaper.model.StaticWallpaperPrefMetadata
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.module.InjectorProvider
import com.android.wallpaper.module.WallpaperPreferences
import com.android.wallpaper.module.logging.UserEventLogger.SetWallpaperEntryPoint
import com.android.wallpaper.picker.customization.shared.model.WallpaperDestination
import com.android.wallpaper.picker.customization.shared.model.WallpaperDestination.BOTH
import com.android.wallpaper.picker.customization.shared.model.WallpaperDestination.Companion.toDestinationInt
import com.android.wallpaper.picker.customization.shared.model.WallpaperDestination.HOME
import com.android.wallpaper.picker.customization.shared.model.WallpaperDestination.LOCK
import com.android.wallpaper.picker.customization.shared.model.WallpaperModel
import com.android.wallpaper.picker.data.WallpaperModel.LiveWallpaperModel
import com.android.wallpaper.picker.data.WallpaperModel.StaticWallpaperModel
import com.android.wallpaper.picker.preview.shared.model.FullPreviewCropModel
import com.android.wallpaper.util.WallpaperCropUtils
import java.io.IOException
import java.io.InputStream
import java.util.EnumMap
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class WallpaperClientImpl(
    private val context: Context,
    private val wallpaperManager: WallpaperManager,
    private val wallpaperPreferences: WallpaperPreferences,
) : WallpaperClient {

    private var recentsContentProviderAvailable: Boolean? = null
    private val cachedRecents: MutableMap<WallpaperDestination, List<WallpaperModel>> =
        EnumMap(WallpaperDestination::class.java)

    init {
        if (areRecentsAvailable()) {
            context.contentResolver.registerContentObserver(
                LIST_RECENTS_URI,
                /* notifyForDescendants= */ true,
                object : ContentObserver(null) {
                    override fun onChange(selfChange: Boolean) {
                        cachedRecents.clear()
                    }
                },
            )
        }
    }

    override fun recentWallpapers(
        destination: WallpaperDestination,
        limit: Int,
    ): Flow<List<WallpaperModel>> {
        return callbackFlow {
            // TODO(b/280891780) Remove this check
            if (Looper.myLooper() == Looper.getMainLooper()) {
                throw IllegalStateException("Do not call method recentWallpapers() on main thread")
            }
            suspend fun queryAndSend(limit: Int) {
                send(queryRecentWallpapers(destination = destination, limit = limit))
            }

            val contentObserver =
                if (areRecentsAvailable()) {
                        object : ContentObserver(null) {
                            override fun onChange(selfChange: Boolean) {
                                launch { queryAndSend(limit = limit) }
                            }
                        }
                    } else {
                        null
                    }
                    ?.also {
                        context.contentResolver.registerContentObserver(
                            LIST_RECENTS_URI,
                            /* notifyForDescendants= */ true,
                            it,
                        )
                    }
            queryAndSend(limit = limit)

            awaitClose {
                if (contentObserver != null) {
                    context.contentResolver.unregisterContentObserver(contentObserver)
                }
            }
        }
    }

    override suspend fun setStaticWallpaper(
        @SetWallpaperEntryPoint setWallpaperEntryPoint: Int,
        destination: WallpaperDestination,
        wallpaperModel: StaticWallpaperModel,
        inputStream: InputStream?,
        bitmap: Bitmap,
        wallpaperSize: Point,
        fullPreviewCropModels: Map<Point, FullPreviewCropModel>?,
    ) {
        if (destination == HOME || destination == BOTH) {
            // Disable rotation wallpaper when setting to home screen. Daily rotation rotates both
            // home and lock screen wallpaper when lock screen is not set; otherwise daily rotation
            // only rotates home screen while lock screen wallpaper stays as what it's set to.
            stopWallpaperRotation()
        }

        val cropHintsWithParallax =
            fullPreviewCropModels?.let { cropModels ->
                cropModels.mapValues { it.value.adjustCropForParallax(wallpaperSize) }
            }
                ?: emptyMap()
        val managerId =
            wallpaperManager.setStaticWallpaperToSystem(
                inputStream,
                bitmap,
                cropHintsWithParallax,
                destination,
            )

        wallpaperPreferences.setStaticWallpaperMetadata(
            metadata = wallpaperModel.getMetadata(bitmap, managerId),
            destination = destination,
        )

        // Save the static wallpaper to recent wallpapers
        // TODO(b/309138446): check if we can update recent with all cropHints from WM later
        wallpaperPreferences.addStaticWallpaperToRecentWallpapers(
            destination,
            wallpaperModel,
            bitmap,
            cropHintsWithParallax,
        )
    }

    private fun stopWallpaperRotation() {
        wallpaperPreferences.setWallpaperPresentationMode(
            WallpaperPreferences.PRESENTATION_MODE_STATIC
        )
        wallpaperPreferences.clearDailyRotations()
    }

    /**
     * Use [WallpaperManager] to set a static wallpaper to the system.
     *
     * @return Wallpaper manager ID
     */
    private fun WallpaperManager.setStaticWallpaperToSystem(
        inputStream: InputStream?,
        bitmap: Bitmap,
        cropHints: Map<Point, Rect>,
        destination: WallpaperDestination,
    ): Int {
        return if (inputStream != null) {
            setStreamWithCrops(
                inputStream,
                cropHints,
                /* allowBackup= */ true,
                destination.toFlags(),
            )
        } else {
            setBitmapWithCrops(
                bitmap,
                cropHints,
                /* allowBackup= */ true,
                destination.toFlags(),
            )
        }
    }

    private fun StaticWallpaperModel.getMetadata(
        bitmap: Bitmap,
        managerId: Int,
    ): StaticWallpaperPrefMetadata {
        val bitmapHash = BitmapUtils.generateHashCode(bitmap)
        return StaticWallpaperPrefMetadata(
            commonWallpaperData.attributions,
            commonWallpaperData.exploreActionUrl,
            commonWallpaperData.id.collectionId,
            bitmapHash,
            managerId,
            commonWallpaperData.id.uniqueId,
        )
    }

    /**
     * Save wallpaper metadata in the preference for two purposes:
     * 1. Quickly reconstruct the currently-selected wallpaper when opening the app
     * 2. Snapshot logging
     */
    private fun WallpaperPreferences.setStaticWallpaperMetadata(
        metadata: StaticWallpaperPrefMetadata,
        destination: WallpaperDestination
    ) {
        when (destination) {
            HOME -> {
                clearHomeWallpaperMetadata()
                setHomeStaticImageWallpaperMetadata(metadata)
            }
            LOCK -> {
                clearLockWallpaperMetadata()
                setLockStaticImageWallpaperMetadata(metadata)
            }
            BOTH -> {
                clearHomeWallpaperMetadata()
                setHomeStaticImageWallpaperMetadata(metadata)
                clearLockWallpaperMetadata()
                setLockStaticImageWallpaperMetadata(metadata)
            }
        }
    }

    override suspend fun setLiveWallpaper(
        setWallpaperEntryPoint: Int,
        destination: WallpaperDestination,
        wallpaperModel: LiveWallpaperModel,
    ) {
        if (destination == HOME || destination == BOTH) {
            // Disable rotation wallpaper when setting to home screen. Daily rotation rotates both
            // home and lock screen wallpaper when lock screen is not set; otherwise daily rotation
            // only rotates home screen while lock screen wallpaper stays as what it's set to.
            stopWallpaperRotation()
        }

        if (wallpaperModel.creativeWallpaperData != null) {
            saveCreativeWallpaperAtExternal(wallpaperModel, destination)
        }

        val managerId = wallpaperManager.setLiveWallpaperToSystem(wallpaperModel, destination)

        wallpaperPreferences.setLiveWallpaperMetadata(
            metadata = wallpaperModel.getMetadata(managerId),
            destination = destination,
        )

        wallpaperPreferences.addLiveWallpaperToRecentWallpapers(destination, wallpaperModel)
    }

    /** Call the external app to save the creative wallpaper. */
    private fun saveCreativeWallpaperAtExternal(
        wallpaperModel: LiveWallpaperModel,
        destination: WallpaperDestination,
    ) {
        wallpaperModel.getSaveWallpaperUriAndAuthority(destination)?.let { (uri, authority) ->
            try {
                context.contentResolver.acquireContentProviderClient(authority).use { client ->
                    client?.query(
                        /* url= */ uri,
                        /* projection= */ null,
                        /* selection= */ null,
                        /* selectionArgs= */ null,
                        /* sortOrder= */ null,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed updating creative live wallpaper at external.")
            }
        }
    }

    /**
     * Use [WallpaperManager] to set a live wallpaper to the system.
     *
     * @return Wallpaper manager ID
     */
    private fun WallpaperManager.setLiveWallpaperToSystem(
        wallpaperModel: LiveWallpaperModel,
        destination: WallpaperDestination
    ): Int {
        val componentName = wallpaperModel.commonWallpaperData.id.componentName
        try {
            // Probe if the function setWallpaperComponentWithFlags exists
            javaClass.getMethod(
                "setWallpaperComponentWithFlags",
                ComponentName::class.java,
                Int::class.javaPrimitiveType
            )
            setWallpaperComponentWithFlags(componentName, destination.toFlags())
        } catch (e: NoSuchMethodException) {
            setWallpaperComponent(componentName)
        }

        // Be careful that WallpaperManager.getWallpaperId can only accept either
        // WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK.
        // If destination is BOTH, either flag should return the same wallpaper manager ID.
        return getWallpaperId(
            if (destination == BOTH || destination == HOME) FLAG_SYSTEM else FLAG_LOCK
        )
    }

    private fun LiveWallpaperModel.getMetadata(managerId: Int): LiveWallpaperPrefMetadata {
        return LiveWallpaperPrefMetadata(
            commonWallpaperData.attributions,
            liveWallpaperData.systemWallpaperInfo.serviceName,
            liveWallpaperData.effectNames,
            commonWallpaperData.id.collectionId,
            managerId,
        )
    }

    /**
     * Save wallpaper metadata in the preference for two purposes:
     * 1. Quickly reconstruct the currently-selected wallpaper when opening the app
     * 2. Snapshot logging
     */
    private fun WallpaperPreferences.setLiveWallpaperMetadata(
        metadata: LiveWallpaperPrefMetadata,
        destination: WallpaperDestination
    ) {
        when (destination) {
            HOME -> {
                clearHomeWallpaperMetadata()
                setHomeLiveWallpaperMetadata(metadata)
            }
            LOCK -> {
                clearLockWallpaperMetadata()
                setLockLiveWallpaperMetadata(metadata)
            }
            BOTH -> {
                clearHomeWallpaperMetadata()
                setHomeLiveWallpaperMetadata(metadata)
                clearLockWallpaperMetadata()
                setLockLiveWallpaperMetadata(metadata)
            }
        }
    }

    /** Get the URI to call the external app to save the creative wallpaper. */
    private fun LiveWallpaperModel.getSaveWallpaperUriAndAuthority(
        destination: WallpaperDestination
    ): Pair<Uri, String>? {
        val uriString =
            liveWallpaperData.systemWallpaperInfo.serviceInfo.metaData.getString(
                CreativeCategory.KEY_WALLPAPER_SAVE_CREATIVE_CATEGORY_WALLPAPER
            )
                ?: return null
        val uri =
            Uri.parse(uriString)
                ?.buildUpon()
                ?.appendQueryParameter("destination", destination.toDestinationInt().toString())
                ?.build()
                ?: return null
        val authority = uri.authority ?: return null
        return Pair(uri, authority)
    }

    override suspend fun setRecentWallpaper(
        @SetWallpaperEntryPoint setWallpaperEntryPoint: Int,
        destination: WallpaperDestination,
        wallpaperId: String,
        onDone: () -> Unit,
    ) {
        val updateValues = ContentValues()
        updateValues.put(KEY_ID, wallpaperId)
        updateValues.put(KEY_SCREEN, destination.asString())
        updateValues.put(KEY_SET_WALLPAPER_ENTRY_POINT, setWallpaperEntryPoint)
        val updatedRowCount = context.contentResolver.update(SET_WALLPAPER_URI, updateValues, null)
        if (updatedRowCount == 0) {
            Log.e(TAG, "Error setting wallpaper: $wallpaperId")
        }
        onDone.invoke()
    }

    private suspend fun queryRecentWallpapers(
        destination: WallpaperDestination,
        limit: Int,
    ): List<WallpaperModel> {
        val recentWallpapers =
            cachedRecents[destination]
                ?: if (!areRecentsAvailable()) {
                    listOf(getCurrentWallpaperFromFactory(destination))
                } else {
                    queryAllRecentWallpapers(destination)
                }

        cachedRecents[destination] = recentWallpapers
        return recentWallpapers.take(limit)
    }

    private suspend fun queryAllRecentWallpapers(
        destination: WallpaperDestination
    ): List<WallpaperModel> {
        context.contentResolver
            .query(
                LIST_RECENTS_URI.buildUpon().appendPath(destination.asString()).build(),
                arrayOf(KEY_ID, KEY_PLACEHOLDER_COLOR, KEY_LAST_UPDATED),
                null,
                null,
            )
            .use { cursor ->
                if (cursor == null || cursor.count == 0) {
                    return emptyList()
                }

                return buildList {
                    val idColumnIndex = cursor.getColumnIndex(KEY_ID)
                    val placeholderColorColumnIndex = cursor.getColumnIndex(KEY_PLACEHOLDER_COLOR)
                    val lastUpdatedColumnIndex = cursor.getColumnIndex(KEY_LAST_UPDATED)
                    val titleColumnIndex = cursor.getColumnIndex(TITLE)
                    while (cursor.moveToNext()) {
                        val wallpaperId = cursor.getString(idColumnIndex)
                        val placeholderColor = cursor.getInt(placeholderColorColumnIndex)
                        val lastUpdated = cursor.getLong(lastUpdatedColumnIndex)
                        val title =
                            if (titleColumnIndex > -1) cursor.getString(titleColumnIndex) else null

                        add(
                            WallpaperModel(
                                wallpaperId = wallpaperId,
                                placeholderColor = placeholderColor,
                                lastUpdated = lastUpdated,
                                title = title,
                            )
                        )
                    }
                }
            }
    }

    private suspend fun getCurrentWallpaperFromFactory(
        destination: WallpaperDestination
    ): WallpaperModel {
        val currentWallpapers = getCurrentWallpapers()
        val wallpaper: WallpaperInfo =
            if (destination == LOCK) {
                currentWallpapers.second ?: currentWallpapers.first
            } else {
                currentWallpapers.first
            }
        val colors = wallpaperManager.getWallpaperColors(destination.toFlags())

        return WallpaperModel(
            wallpaperId = wallpaper.wallpaperId,
            placeholderColor = colors?.primaryColor?.toArgb() ?: Color.TRANSPARENT,
            title = wallpaper.getTitle(context)
        )
    }

    private suspend fun getCurrentWallpapers(): Pair<WallpaperInfo, WallpaperInfo?> =
        suspendCancellableCoroutine { continuation ->
            InjectorProvider.getInjector()
                .getCurrentWallpaperInfoFactory(context)
                .createCurrentWallpaperInfos(
                    context,
                    /* forceRefresh= */ false,
                ) { homeWallpaper, lockWallpaper, _ ->
                    continuation.resume(Pair(homeWallpaper, lockWallpaper), null)
                }
        }

    override suspend fun loadThumbnail(
        wallpaperId: String,
        destination: WallpaperDestination
    ): Bitmap? {
        if (areRecentsAvailable()) {
            try {
                // We're already using this in a suspend function, so we're okay.
                @Suppress("BlockingMethodInNonBlockingContext")
                context.contentResolver
                    .openFile(
                        GET_THUMBNAIL_BASE_URI.buildUpon()
                            .appendPath(wallpaperId)
                            .appendQueryParameter(KEY_DESTINATION, destination.asString())
                            .build(),
                        "r",
                        null,
                    )
                    .use { file ->
                        if (file == null) {
                            Log.e(TAG, "Error getting wallpaper preview: $wallpaperId")
                        } else {
                            return BitmapFactory.decodeFileDescriptor(file.fileDescriptor)
                        }
                    }
            } catch (e: IOException) {
                Log.e(
                    TAG,
                    "Error getting wallpaper preview: $wallpaperId, destination: ${destination.asString()}",
                    e
                )
            }
        } else {
            val currentWallpapers = getCurrentWallpapers()
            val wallpaper =
                if (currentWallpapers.first.wallpaperId == wallpaperId) {
                    currentWallpapers.first
                } else if (currentWallpapers.second?.wallpaperId == wallpaperId) {
                    currentWallpapers.second
                } else null
            return wallpaper?.getThumbAsset(context)?.getLowResBitmap(context)
        }

        return null
    }

    override fun areRecentsAvailable(): Boolean {
        if (recentsContentProviderAvailable == null) {
            recentsContentProviderAvailable =
                try {
                    context.packageManager.resolveContentProvider(
                        AUTHORITY,
                        0,
                    ) != null
                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "Exception trying to resolve recents content provider, skipping it",
                        e
                    )
                    false
                }
        }
        return recentsContentProviderAvailable == true
    }

    override fun getCurrentCropHints(
        displaySizes: List<Point>,
        @SetWallpaperFlags which: Int
    ): Map<Point, Rect>? {
        val flags = InjectorProvider.getInjector().getFlags()
        val isMultiCropEnabled = flags.isMultiCropPreviewUiEnabled() && flags.isMultiCropEnabled()
        if (!isMultiCropEnabled) {
            return null
        }
        val cropHints: List<Rect>? =
            wallpaperManager.getBitmapCrops(displaySizes, which, /* originalBitmap= */ true)
        val cropHintsMap: MutableMap<Point, Rect> = ArrayMap()
        if (cropHints != null) {
            for (i in cropHints.indices) {
                cropHintsMap[displaySizes[i]] = cropHints[i]
            }
        }
        return cropHintsMap
    }

    override suspend fun getWallpaperColors(
        bitmap: Bitmap,
        cropHints: Map<Point, Rect>?
    ): WallpaperColors? {
        return wallpaperManager.getWallpaperColors(bitmap, cropHints)
    }

    fun WallpaperDestination.asString(): String {
        return when (this) {
            BOTH -> SCREEN_ALL
            HOME -> SCREEN_HOME
            LOCK -> SCREEN_LOCK
        }
    }

    private fun WallpaperDestination.toFlags(): Int {
        return when (this) {
            BOTH -> FLAG_LOCK or FLAG_SYSTEM
            HOME -> FLAG_SYSTEM
            LOCK -> FLAG_LOCK
        }
    }

    /**
     * Adjusts cropHints for parallax effect.
     *
     * [WallpaperCropUtils.calculateCropRect] calculates based on the scaled size, the scale depends
     * on the view size hosting the preview and the wallpaper zoom of the preview on that view,
     * whereas the rest of multi-crop is based on full wallpaper size. So scaled back at the end.
     *
     * If [CropSizeModel] is null, returns the original cropHint without parallax.
     *
     * @param wallpaperSize full wallpaper image size.
     */
    private fun FullPreviewCropModel.adjustCropForParallax(
        wallpaperSize: Point,
    ): Rect {
        return cropSizeModel?.let {
            WallpaperCropUtils.calculateCropRect(
                    context,
                    it.hostViewSize,
                    it.cropSurfaceSize,
                    wallpaperSize,
                    cropHint,
                    it.wallpaperZoom,
                    /* cropExtraWidth= */ true,
                )
                .apply { scale(1f / it.wallpaperZoom) }
        }
            ?: cropHint
    }

    companion object {
        private const val TAG = "WallpaperClientImpl"
        private const val AUTHORITY = "com.google.android.apps.wallpaper.recents"

        /** Path for making a content provider request to set the wallpaper. */
        private const val PATH_SET_WALLPAPER = "set_recent_wallpaper"
        /** Path for making a content provider request to query for the recent wallpapers. */
        private const val PATH_LIST_RECENTS = "list_recent"
        /** Path for making a content provider request to query for the thumbnail of a wallpaper. */
        private const val PATH_GET_THUMBNAIL = "thumb"

        private val BASE_URI =
            Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(AUTHORITY).build()
        /** [Uri] for making a content provider request to set the wallpaper. */
        private val SET_WALLPAPER_URI = BASE_URI.buildUpon().appendPath(PATH_SET_WALLPAPER).build()
        /** [Uri] for making a content provider request to query for the recent wallpapers. */
        private val LIST_RECENTS_URI = BASE_URI.buildUpon().appendPath(PATH_LIST_RECENTS).build()
        /**
         * [Uri] for making a content provider request to query for the thumbnail of a wallpaper.
         */
        private val GET_THUMBNAIL_BASE_URI =
            BASE_URI.buildUpon().appendPath(PATH_GET_THUMBNAIL).build()

        /** Key for a parameter used to pass the wallpaper ID to/from the content provider. */
        private const val KEY_ID = "id"
        /** Key for a parameter used to pass the screen to/from the content provider. */
        private const val KEY_SCREEN = "screen"
        /** Key for a parameter used to pass the wallpaper destination to/from content provider. */
        private const val KEY_DESTINATION = "destination"
        /** Key for a parameter used to pass the screen to/from the content provider. */
        private const val KEY_SET_WALLPAPER_ENTRY_POINT = "set_wallpaper_entry_point"
        private const val KEY_LAST_UPDATED = "last_updated"
        private const val SCREEN_ALL = "all_screens"
        private const val SCREEN_HOME = "home_screen"
        private const val SCREEN_LOCK = "lock_screen"

        private const val TITLE = "title"
        /**
         * Key for a parameter used to get the placeholder color for a wallpaper from the content
         * provider.
         */
        private const val KEY_PLACEHOLDER_COLOR = "placeholder_color"
    }
}
