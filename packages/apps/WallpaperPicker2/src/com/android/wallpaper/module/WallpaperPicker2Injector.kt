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
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.android.customization.model.color.DefaultWallpaperColorResources
import com.android.customization.model.color.WallpaperColorResources
import com.android.systemui.shared.settings.data.repository.SecureSettingsRepository
import com.android.systemui.shared.settings.data.repository.SecureSettingsRepositoryImpl
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.effects.EffectsController
import com.android.wallpaper.model.CategoryProvider
import com.android.wallpaper.model.InlinePreviewIntentFactory
import com.android.wallpaper.model.LiveWallpaperInfo
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.module.logging.UserEventLogger
import com.android.wallpaper.monitor.PerformanceMonitor
import com.android.wallpaper.network.Requester
import com.android.wallpaper.network.WallpaperRequester
import com.android.wallpaper.picker.CustomizationPickerActivity
import com.android.wallpaper.picker.ImagePreviewFragment
import com.android.wallpaper.picker.LivePreviewFragment
import com.android.wallpaper.picker.MyPhotosStarter
import com.android.wallpaper.picker.PreviewActivity
import com.android.wallpaper.picker.PreviewFragment
import com.android.wallpaper.picker.ViewOnlyPreviewActivity
import com.android.wallpaper.picker.customization.data.content.WallpaperClient
import com.android.wallpaper.picker.customization.data.content.WallpaperClientImpl
import com.android.wallpaper.picker.customization.data.repository.WallpaperColorsRepository
import com.android.wallpaper.picker.customization.data.repository.WallpaperRepository
import com.android.wallpaper.picker.customization.domain.interactor.WallpaperInteractor
import com.android.wallpaper.picker.customization.domain.interactor.WallpaperSnapshotRestorer
import com.android.wallpaper.picker.di.modules.BackgroundDispatcher
import com.android.wallpaper.picker.di.modules.MainDispatcher
import com.android.wallpaper.picker.individual.IndividualPickerFragment
import com.android.wallpaper.picker.undo.data.repository.UndoRepository
import com.android.wallpaper.picker.undo.domain.interactor.UndoInteractor
import com.android.wallpaper.util.DisplayUtils
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

@Singleton
open class WallpaperPicker2Injector
@Inject
constructor(
    @MainDispatcher private val mainScope: CoroutineScope,
    @BackgroundDispatcher private val bgDispatcher: CoroutineDispatcher,
) : Injector {
    private var alarmManagerWrapper: AlarmManagerWrapper? = null
    private var bitmapCropper: BitmapCropper? = null
    private var categoryProvider: CategoryProvider? = null
    private var currentWallpaperFactory: CurrentWallpaperInfoFactory? = null
    private var customizationSections: CustomizationSections? = null
    private var displayUtils: DisplayUtils? = null
    private var drawableLayerResolver: DrawableLayerResolver? = null
    private var exploreIntentChecker: ExploreIntentChecker? = null
    private var liveWallpaperInfoFactory: LiveWallpaperInfoFactory? = null
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
    @Inject lateinit var injectedWallpaperInteractor: Lazy<WallpaperInteractor>
    private var wallpaperClient: WallpaperClient? = null
    @Inject lateinit var injectedWallpaperClient: Lazy<WallpaperClient>
    private var wallpaperSnapshotRestorer: WallpaperSnapshotRestorer? = null
    private var secureSettingsRepository: SecureSettingsRepository? = null
    private var wallpaperColorsRepository: WallpaperColorsRepository? = null
    private var previewActivityIntentFactory: InlinePreviewIntentFactory? = null
    private var viewOnlyPreviewActivityIntentFactory: InlinePreviewIntentFactory? = null
    @Inject lateinit var userEventLogger: Lazy<UserEventLogger>

    override fun getApplicationCoroutineScope(): CoroutineScope {
        return mainScope
    }

    @Synchronized
    override fun getAlarmManagerWrapper(context: Context): AlarmManagerWrapper {
        return alarmManagerWrapper
            ?: DefaultAlarmManagerWrapper(context.applicationContext).also {
                alarmManagerWrapper = it
            }
    }

    @Synchronized
    override fun getBitmapCropper(): BitmapCropper {
        return bitmapCropper ?: DefaultBitmapCropper().also { bitmapCropper = it }
    }

    override fun getCategoryProvider(context: Context): CategoryProvider {
        return categoryProvider
            ?: DefaultCategoryProvider(context.applicationContext).also { categoryProvider = it }
    }

    @Synchronized
    override fun getCurrentWallpaperInfoFactory(context: Context): CurrentWallpaperInfoFactory {
        return currentWallpaperFactory
            ?: DefaultCurrentWallpaperInfoFactory(
                    getWallpaperRefresher(context.applicationContext),
                    getLiveWallpaperInfoFactory(context.applicationContext),
                )
                .also { currentWallpaperFactory = it }
    }

    override fun getCustomizationSections(activity: ComponentActivity): CustomizationSections {
        return customizationSections
            ?: WallpaperPickerSections().also { customizationSections = it }
    }

    override fun getDeepLinkRedirectIntent(context: Context, uri: Uri): Intent {
        val intent = Intent()
        intent.setClass(context, CustomizationPickerActivity::class.java)
        intent.data = uri
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        return intent
    }

    override fun getDisplayUtils(context: Context): DisplayUtils {
        return displayUtils ?: DisplayUtils(context.applicationContext).also { displayUtils = it }
    }

    override fun getDownloadableIntentAction(): String? {
        return null
    }

    override fun getDrawableLayerResolver(): DrawableLayerResolver {
        return drawableLayerResolver
            ?: DefaultDrawableLayerResolver().also { drawableLayerResolver = it }
    }

    override fun getEffectsController(
        context: Context,
    ): EffectsController? {
        return null
    }

    @Synchronized
    override fun getExploreIntentChecker(context: Context): ExploreIntentChecker {
        return exploreIntentChecker
            ?: DefaultExploreIntentChecker(context.applicationContext).also {
                exploreIntentChecker = it
            }
    }

    override fun getIndividualPickerFragment(context: Context, collectionId: String): Fragment {
        return IndividualPickerFragment.newInstance(collectionId)
    }

    override fun getLiveWallpaperInfoFactory(context: Context): LiveWallpaperInfoFactory {
        return liveWallpaperInfoFactory
            ?: DefaultLiveWallpaperInfoFactory().also { liveWallpaperInfoFactory = it }
    }

    @Synchronized
    override fun getNetworkStatusNotifier(context: Context): NetworkStatusNotifier {
        return networkStatusNotifier
            ?: DefaultNetworkStatusNotifier(context.applicationContext).also {
                networkStatusNotifier = it
            }
    }

    @Synchronized
    override fun getPackageStatusNotifier(context: Context): PackageStatusNotifier {
        return packageStatusNotifier
            ?: DefaultPackageStatusNotifier(context.applicationContext).also {
                packageStatusNotifier = it
            }
    }

    @Synchronized
    override fun getPartnerProvider(context: Context): PartnerProvider {
        return partnerProvider
            ?: DefaultPartnerProvider(context.applicationContext).also { partnerProvider = it }
    }

    @Synchronized
    override fun getPerformanceMonitor(): PerformanceMonitor? {

        return performanceMonitor
            ?: PerformanceMonitor {
                    /** No Op */
                }
                .also { performanceMonitor = it }
    }

    override fun getPreviewFragment(
        context: Context,
        wallpaperInfo: WallpaperInfo,
        viewAsHome: Boolean,
        isAssetIdPresent: Boolean,
        isNewTask: Boolean,
    ): Fragment {
        val isLiveWallpaper = wallpaperInfo is LiveWallpaperInfo
        return (if (isLiveWallpaper) LivePreviewFragment() else ImagePreviewFragment()).apply {
            arguments =
                Bundle().apply {
                    putParcelable(PreviewFragment.ARG_WALLPAPER, wallpaperInfo)
                    putBoolean(PreviewFragment.ARG_VIEW_AS_HOME, viewAsHome)
                    putBoolean(PreviewFragment.ARG_IS_ASSET_ID_PRESENT, isAssetIdPresent)
                    putBoolean(PreviewFragment.ARG_IS_NEW_TASK, isNewTask)
                }
        }
    }

    @Synchronized
    override fun getRequester(context: Context): Requester {
        return requester ?: WallpaperRequester(context.applicationContext).also { requester = it }
    }

    @Synchronized
    override fun getSystemFeatureChecker(): SystemFeatureChecker {
        return systemFeatureChecker
            ?: DefaultSystemFeatureChecker().also { systemFeatureChecker = it }
    }

    override fun getUserEventLogger(): UserEventLogger {
        return userEventLogger.get()
    }

    @Synchronized
    override fun getWallpaperPersister(context: Context): WallpaperPersister {
        return wallpaperPersister
            ?: DefaultWallpaperPersister(
                    context.applicationContext,
                    WallpaperManager.getInstance(context.applicationContext),
                    getPreferences(context),
                    WallpaperChangedNotifier.getInstance(),
                    getDisplayUtils(context),
                    getBitmapCropper(),
                    getWallpaperStatusChecker(context),
                    getCurrentWallpaperInfoFactory(context),
                    getFlags().isRefactorSettingWallpaper(),
                )
                .also { wallpaperPersister = it }
    }

    @Synchronized
    override fun getPreferences(context: Context): WallpaperPreferences {
        return prefs
    }

    @Synchronized
    override fun getWallpaperRefresher(context: Context): WallpaperRefresher {
        return wallpaperRefresher
            ?: DefaultWallpaperRefresher(context.applicationContext).also {
                wallpaperRefresher = it
            }
    }

    override fun getWallpaperStatusChecker(context: Context): WallpaperStatusChecker {
        return wallpaperStatusChecker
            ?: DefaultWallpaperStatusChecker(
                    wallpaperManager = WallpaperManager.getInstance(context.applicationContext),
                )
                .also { wallpaperStatusChecker = it }
    }

    override fun getFlags(): BaseFlags {
        return flags ?: object : BaseFlags() {}.also { flags = it }
    }

    override fun getUndoInteractor(
        context: Context,
        lifecycleOwner: LifecycleOwner
    ): UndoInteractor {
        return undoInteractor
            ?: UndoInteractor(
                    getApplicationCoroutineScope(),
                    UndoRepository(),
                    getSnapshotRestorers(context),
                )
                .also { undoInteractor = it }
    }

    override fun getWallpaperInteractor(context: Context): WallpaperInteractor {
        if (getFlags().isMultiCropEnabled() && getFlags().isMultiCropPreviewUiEnabled()) {
            return injectedWallpaperInteractor.get()
        }

        val appContext = context.applicationContext
        return wallpaperInteractor
            ?: WallpaperInteractor(
                    repository =
                        WallpaperRepository(
                            scope = getApplicationCoroutineScope(),
                            client = getWallpaperClient(context),
                            wallpaperPreferences = getPreferences(context = appContext),
                            backgroundDispatcher = bgDispatcher,
                        ),
                )
                .also { wallpaperInteractor = it }
    }

    override fun getWallpaperClient(context: Context): WallpaperClient {
        if (getFlags().isMultiCropEnabled() && getFlags().isMultiCropPreviewUiEnabled()) {
            return injectedWallpaperClient.get()
        }

        val appContext = context.applicationContext
        return wallpaperClient
            ?: WallpaperClientImpl(
                    context = appContext,
                    wallpaperManager = WallpaperManager.getInstance(appContext),
                    wallpaperPreferences = getPreferences(appContext),
                )
                .also { wallpaperClient = it }
    }

    override fun getWallpaperSnapshotRestorer(context: Context): WallpaperSnapshotRestorer {
        return wallpaperSnapshotRestorer
            ?: WallpaperSnapshotRestorer(
                    scope = getApplicationCoroutineScope(),
                    interactor = getWallpaperInteractor(context),
                )
                .also { wallpaperSnapshotRestorer = it }
    }

    protected fun getSecureSettingsRepository(context: Context): SecureSettingsRepository {
        return secureSettingsRepository
            ?: SecureSettingsRepositoryImpl(
                    contentResolver = context.applicationContext.contentResolver,
                    backgroundDispatcher = bgDispatcher,
                )
                .also { secureSettingsRepository = it }
    }

    override fun getWallpaperColorsRepository(): WallpaperColorsRepository {
        return wallpaperColorsRepository
            ?: WallpaperColorsRepository().also { wallpaperColorsRepository = it }
    }

    override fun getWallpaperColorResources(
        wallpaperColors: WallpaperColors,
        context: Context
    ): WallpaperColorResources {
        return DefaultWallpaperColorResources(wallpaperColors)
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

    companion object {
        /**
         * When this injector is overridden, this is the minimal value that should be used by
         * restorers returns in [getSnapshotRestorers].
         */
        @JvmStatic protected val MIN_SNAPSHOT_RESTORER_KEY = 0
    }
}
