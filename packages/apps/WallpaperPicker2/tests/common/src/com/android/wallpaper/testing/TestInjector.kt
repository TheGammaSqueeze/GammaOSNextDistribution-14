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
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.android.customization.model.color.DefaultWallpaperColorResources
import com.android.customization.model.color.WallpaperColorResources
import com.android.systemui.shared.customization.data.content.CustomizationProviderClient
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.effects.EffectsController
import com.android.wallpaper.model.CategoryProvider
import com.android.wallpaper.model.InlinePreviewIntentFactory
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.module.AlarmManagerWrapper
import com.android.wallpaper.module.BitmapCropper
import com.android.wallpaper.module.CurrentWallpaperInfoFactory
import com.android.wallpaper.module.CustomizationSections
import com.android.wallpaper.module.DefaultLiveWallpaperInfoFactory
import com.android.wallpaper.module.DrawableLayerResolver
import com.android.wallpaper.module.ExploreIntentChecker
import com.android.wallpaper.module.Injector
import com.android.wallpaper.module.LiveWallpaperInfoFactory
import com.android.wallpaper.module.NetworkStatusNotifier
import com.android.wallpaper.module.PackageStatusNotifier
import com.android.wallpaper.module.PartnerProvider
import com.android.wallpaper.module.SystemFeatureChecker
import com.android.wallpaper.module.WallpaperPersister
import com.android.wallpaper.module.WallpaperPreferences
import com.android.wallpaper.module.WallpaperRefresher
import com.android.wallpaper.module.WallpaperStatusChecker
import com.android.wallpaper.module.logging.UserEventLogger
import com.android.wallpaper.monitor.PerformanceMonitor
import com.android.wallpaper.network.Requester
import com.android.wallpaper.picker.ImagePreviewFragment
import com.android.wallpaper.picker.MyPhotosStarter
import com.android.wallpaper.picker.PreviewActivity
import com.android.wallpaper.picker.PreviewFragment
import com.android.wallpaper.picker.ViewOnlyPreviewActivity
import com.android.wallpaper.picker.customization.data.repository.WallpaperColorsRepository
import com.android.wallpaper.picker.customization.data.repository.WallpaperRepository
import com.android.wallpaper.picker.customization.domain.interactor.WallpaperInteractor
import com.android.wallpaper.picker.customization.domain.interactor.WallpaperSnapshotRestorer
import com.android.wallpaper.picker.individual.IndividualPickerFragment
import com.android.wallpaper.picker.undo.data.repository.UndoRepository
import com.android.wallpaper.picker.undo.domain.interactor.UndoInteractor
import com.android.wallpaper.util.DisplayUtils
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/** Test implementation of [Injector] */
@Singleton
open class TestInjector @Inject constructor(private val userEventLogger: UserEventLogger) :
    Injector {
    private var appScope: CoroutineScope? = null
    private var alarmManagerWrapper: AlarmManagerWrapper? = null
    private var bitmapCropper: BitmapCropper? = null
    private var categoryProvider: CategoryProvider? = null
    private var currentWallpaperInfoFactory: CurrentWallpaperInfoFactory? = null
    private var customizationSections: CustomizationSections? = null
    private var drawableLayerResolver: DrawableLayerResolver? = null
    private var exploreIntentChecker: ExploreIntentChecker? = null
    private var networkStatusNotifier: NetworkStatusNotifier? = null
    private var packageStatusNotifier: PackageStatusNotifier? = null
    private var partnerProvider: PartnerProvider? = null
    private var performanceMonitor: PerformanceMonitor? = null
    private var requester: Requester? = null
    private var systemFeatureChecker: SystemFeatureChecker? = null
    private var wallpaperPersister: WallpaperPersister? = null
    @Inject lateinit var prefs: WallpaperPreferences
    private var wallpaperRefresher: WallpaperRefresher? = null
    private var wallpaperStatusChecker: WallpaperStatusChecker? = null
    private var flags: BaseFlags? = null
    private var undoInteractor: UndoInteractor? = null
    private var wallpaperInteractor: WallpaperInteractor? = null
    @Inject lateinit var injectedWallpaperInteractor: WallpaperInteractor
    private var wallpaperSnapshotRestorer: WallpaperSnapshotRestorer? = null
    private var wallpaperColorsRepository: WallpaperColorsRepository? = null
    private var wallpaperClient: FakeWallpaperClient? = null
    private var previewActivityIntentFactory: InlinePreviewIntentFactory? = null
    private var viewOnlyPreviewActivityIntentFactory: InlinePreviewIntentFactory? = null

    override fun getApplicationCoroutineScope(): CoroutineScope {
        return appScope ?: CoroutineScope(Dispatchers.Main).also { appScope = it }
    }

    override fun getAlarmManagerWrapper(context: Context): AlarmManagerWrapper {
        return alarmManagerWrapper ?: TestAlarmManagerWrapper().also { alarmManagerWrapper = it }
    }

    override fun getBitmapCropper(): BitmapCropper {
        return bitmapCropper ?: TestBitmapCropper().also { bitmapCropper = it }
    }

    override fun getCategoryProvider(context: Context): CategoryProvider {
        return categoryProvider ?: TestCategoryProvider().also { categoryProvider = it }
    }

    override fun getCurrentWallpaperInfoFactory(context: Context): CurrentWallpaperInfoFactory {
        return currentWallpaperInfoFactory
            ?: TestCurrentWallpaperInfoFactory(context.applicationContext).also {
                currentWallpaperInfoFactory = it
            }
    }

    override fun getCustomizationSections(activity: ComponentActivity): CustomizationSections {
        return customizationSections
            ?: TestCustomizationSections().also { customizationSections = it }
    }

    override fun getDeepLinkRedirectIntent(context: Context, uri: Uri): Intent {
        return Intent()
    }

    override fun getDisplayUtils(context: Context): DisplayUtils {
        return DisplayUtils(context)
    }

    override fun getDownloadableIntentAction(): String? {
        return null
    }

    override fun getDrawableLayerResolver(): DrawableLayerResolver {
        return drawableLayerResolver
            ?: TestDrawableLayerResolver().also { drawableLayerResolver = it }
    }

    override fun getEffectsController(
        context: Context,
    ): EffectsController? {
        return null
    }

    override fun getExploreIntentChecker(context: Context): ExploreIntentChecker {
        return exploreIntentChecker ?: TestExploreIntentChecker().also { exploreIntentChecker = it }
    }

    override fun getIndividualPickerFragment(
        context: Context,
        collectionId: String
    ): IndividualPickerFragment {
        return IndividualPickerFragment.newInstance(collectionId)
    }

    override fun getLiveWallpaperInfoFactory(context: Context): LiveWallpaperInfoFactory {
        return DefaultLiveWallpaperInfoFactory()
    }

    override fun getNetworkStatusNotifier(context: Context): NetworkStatusNotifier {
        return networkStatusNotifier
            ?: TestNetworkStatusNotifier().also { networkStatusNotifier = it }
    }

    override fun getPackageStatusNotifier(context: Context): PackageStatusNotifier {
        return packageStatusNotifier
            ?: TestPackageStatusNotifier().also { packageStatusNotifier = it }
    }

    override fun getPartnerProvider(context: Context): PartnerProvider {
        return partnerProvider ?: TestPartnerProvider().also { partnerProvider = it }
    }

    override fun getPerformanceMonitor(): PerformanceMonitor? {
        return performanceMonitor ?: TestPerformanceMonitor().also { performanceMonitor = it }
    }

    override fun getPreviewFragment(
        context: Context,
        wallpaperInfo: WallpaperInfo,
        viewAsHome: Boolean,
        isAssetIdPresent: Boolean,
        isNewTask: Boolean,
    ): Fragment {
        val args = Bundle()
        args.putParcelable(PreviewFragment.ARG_WALLPAPER, wallpaperInfo)
        args.putBoolean(PreviewFragment.ARG_VIEW_AS_HOME, viewAsHome)
        args.putBoolean(PreviewFragment.ARG_IS_ASSET_ID_PRESENT, isAssetIdPresent)
        args.putBoolean(PreviewFragment.ARG_IS_NEW_TASK, isNewTask)
        val fragment = ImagePreviewFragment()
        fragment.arguments = args
        return fragment
    }

    override fun getRequester(context: Context): Requester {
        return requester ?: TestRequester().also { requester = it }
    }

    override fun getSystemFeatureChecker(): SystemFeatureChecker {
        return systemFeatureChecker ?: TestSystemFeatureChecker().also { systemFeatureChecker = it }
    }

    override fun getUserEventLogger(): UserEventLogger {
        return userEventLogger
    }

    override fun getWallpaperPersister(context: Context): WallpaperPersister {
        return wallpaperPersister
            ?: TestWallpaperPersister(context.applicationContext).also { wallpaperPersister = it }
    }

    override fun getPreferences(context: Context): WallpaperPreferences {
        return prefs
    }

    override fun getWallpaperRefresher(context: Context): WallpaperRefresher {
        return wallpaperRefresher
            ?: TestWallpaperRefresher(context.applicationContext).also { wallpaperRefresher = it }
    }

    override fun getWallpaperStatusChecker(context: Context): WallpaperStatusChecker {
        return wallpaperStatusChecker
            ?: TestWallpaperStatusChecker().also { wallpaperStatusChecker = it }
    }

    override fun getFlags(): BaseFlags {
        return flags
            ?: object : BaseFlags() {
                    override fun isWallpaperRestorerEnabled(): Boolean {
                        return true
                    }

                    override fun isPageTransitionsFeatureEnabled(context: Context): Boolean {
                        return true
                    }

                    override fun getCachedFlags(
                        context: Context
                    ): List<CustomizationProviderClient.Flag> {
                        return listOf()
                    }
                }
                .also { flags = it }
    }

    override fun getUndoInteractor(
        context: Context,
        lifecycleOwner: LifecycleOwner
    ): UndoInteractor {
        return undoInteractor
            ?: UndoInteractor(
                getApplicationCoroutineScope(),
                UndoRepository(),
                HashMap()
            ) // Empty because we don't support undoing in WallpaperPicker2..also{}
    }

    override fun getWallpaperInteractor(context: Context): WallpaperInteractor {
        if (getFlags().isMultiCropEnabled() && getFlags().isMultiCropPreviewUiEnabled()) {
            return injectedWallpaperInteractor
        }

        return wallpaperInteractor
            ?: WallpaperInteractor(
                    repository =
                        WallpaperRepository(
                            scope = getApplicationCoroutineScope(),
                            client = getWallpaperClient(context),
                            wallpaperPreferences = getPreferences(context = context),
                            backgroundDispatcher = Dispatchers.IO,
                        ),
                )
                .also { wallpaperInteractor = it }
    }

    override fun getWallpaperSnapshotRestorer(context: Context): WallpaperSnapshotRestorer {
        return wallpaperSnapshotRestorer
            ?: WallpaperSnapshotRestorer(
                    scope = getApplicationCoroutineScope(),
                    interactor = getWallpaperInteractor(context),
                )
                .also { wallpaperSnapshotRestorer = it }
    }

    override fun getWallpaperColorResources(
        wallpaperColors: WallpaperColors,
        context: Context
    ): WallpaperColorResources {
        return DefaultWallpaperColorResources(wallpaperColors)
    }

    override fun getWallpaperColorsRepository(): WallpaperColorsRepository {
        return wallpaperColorsRepository
            ?: WallpaperColorsRepository().also { wallpaperColorsRepository = it }
    }

    override fun getMyPhotosIntentProvider(): MyPhotosStarter.MyPhotosIntentProvider {
        return object : MyPhotosStarter.MyPhotosIntentProvider {}
    }

    override fun isCurrentSelectedColorPreset(context: Context): Boolean {
        return false
    }

    override fun getPreviewActivityIntentFactory(): InlinePreviewIntentFactory {
        return previewActivityIntentFactory
            ?: PreviewActivity.PreviewActivityIntentFactory().also {
                previewActivityIntentFactory = it
            }
    }

    override fun getViewOnlyPreviewActivityIntentFactory(): InlinePreviewIntentFactory {
        return viewOnlyPreviewActivityIntentFactory
            ?: ViewOnlyPreviewActivity.ViewOnlyPreviewActivityIntentFactory().also {
                viewOnlyPreviewActivityIntentFactory = it
            }
    }

    override fun getWallpaperClient(context: Context): FakeWallpaperClient {
        return wallpaperClient ?: FakeWallpaperClient().also { wallpaperClient = it }
    }

    override fun isInstrumentationTest(): Boolean {
        return true
    }
}
