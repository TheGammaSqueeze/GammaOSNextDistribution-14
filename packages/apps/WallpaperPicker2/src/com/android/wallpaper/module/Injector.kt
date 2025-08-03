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
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.android.customization.model.color.WallpaperColorResources
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.effects.EffectsController
import com.android.wallpaper.model.CategoryProvider
import com.android.wallpaper.model.InlinePreviewIntentFactory
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.module.logging.UserEventLogger
import com.android.wallpaper.monitor.PerformanceMonitor
import com.android.wallpaper.network.Requester
import com.android.wallpaper.picker.MyPhotosStarter.MyPhotosIntentProvider
import com.android.wallpaper.picker.customization.data.content.WallpaperClient
import com.android.wallpaper.picker.customization.data.repository.WallpaperColorsRepository
import com.android.wallpaper.picker.customization.domain.interactor.WallpaperInteractor
import com.android.wallpaper.picker.customization.domain.interactor.WallpaperSnapshotRestorer
import com.android.wallpaper.picker.undo.domain.interactor.SnapshotRestorer
import com.android.wallpaper.picker.undo.domain.interactor.UndoInteractor
import com.android.wallpaper.util.DisplayUtils
import kotlinx.coroutines.CoroutineScope

/**
 * Interface for a provider of "injected dependencies." (NOTE: The term "injector" is somewhat of a
 * misnomer; this is more aptly a service registry as part of a service locator design pattern.)
 */
interface Injector {
    /**
     * Returns a [CoroutineScope] that's bound to the lifecycle of the application.
     *
     * It starts immediately and is never paused, stopped, or destroyed.
     */
    fun getApplicationCoroutineScope(): CoroutineScope

    fun getAlarmManagerWrapper(context: Context): AlarmManagerWrapper

    fun getBitmapCropper(): BitmapCropper

    fun getCategoryProvider(context: Context): CategoryProvider

    fun getCurrentWallpaperInfoFactory(context: Context): CurrentWallpaperInfoFactory

    fun getCustomizationSections(activity: ComponentActivity): CustomizationSections

    fun getDeepLinkRedirectIntent(context: Context, uri: Uri): Intent

    fun getDisplayUtils(context: Context): DisplayUtils

    fun getDownloadableIntentAction(): String?

    fun getDrawableLayerResolver(): DrawableLayerResolver

    fun getEffectsController(context: Context): EffectsController?

    fun getExploreIntentChecker(context: Context): ExploreIntentChecker

    fun getIndividualPickerFragment(context: Context, collectionId: String): Fragment

    fun getLiveWallpaperInfoFactory(context: Context): LiveWallpaperInfoFactory

    fun getNetworkStatusNotifier(context: Context): NetworkStatusNotifier

    fun getPackageStatusNotifier(context: Context): PackageStatusNotifier

    fun getPartnerProvider(context: Context): PartnerProvider

    fun getPerformanceMonitor(): PerformanceMonitor?

    // TODO b/242908637 Remove this method when migrating to the new wallpaper preview screen
    fun getPreviewFragment(
        context: Context,
        wallpaperInfo: WallpaperInfo,
        viewAsHome: Boolean,
        isAssetIdPresent: Boolean,
        isNewTask: Boolean,
    ): Fragment

    fun getRequester(context: Context): Requester

    fun getSystemFeatureChecker(): SystemFeatureChecker

    fun getUserEventLogger(): UserEventLogger

    fun getWallpaperPersister(context: Context): WallpaperPersister

    fun getPreferences(context: Context): WallpaperPreferences

    fun getWallpaperRefresher(context: Context): WallpaperRefresher

    fun getWallpaperStatusChecker(context: Context): WallpaperStatusChecker

    fun getFragmentFactory(): FragmentFactory? {
        return null
    }

    fun getFlags(): BaseFlags

    fun getUndoInteractor(context: Context, lifecycleOwner: LifecycleOwner): UndoInteractor

    fun getSnapshotRestorers(
        context: Context,
    ): Map<Int, SnapshotRestorer> {
        // Empty because we don't support undoing in WallpaperPicker2.
        return HashMap()
    }

    fun getWallpaperInteractor(context: Context): WallpaperInteractor

    fun getWallpaperClient(context: Context): WallpaperClient

    fun getWallpaperSnapshotRestorer(context: Context): WallpaperSnapshotRestorer

    fun getWallpaperColorsRepository(): WallpaperColorsRepository

    fun getWallpaperColorResources(
        wallpaperColors: WallpaperColors,
        context: Context
    ): WallpaperColorResources

    fun getMyPhotosIntentProvider(): MyPhotosIntentProvider

    fun isInstrumentationTest(): Boolean {
        return false
    }

    fun isCurrentSelectedColorPreset(context: Context): Boolean

    /**
     * Implements [InlinePreviewIntentFactory] that provides an intent to start [PreviewActivity].
     */
    fun getPreviewActivityIntentFactory(): InlinePreviewIntentFactory

    /**
     * Implements [InlinePreviewIntentFactory] that provides an intent to start
     * [ViewOnlyPreviewActivity].
     *
     * TODO(b/298037335): Rename or remove view only preview.
     */
    fun getViewOnlyPreviewActivityIntentFactory(): InlinePreviewIntentFactory
}
