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
package com.android.wallpaper.picker.preview.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.android.wallpaper.R
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.picker.AppbarFragment
import com.android.wallpaper.picker.BasePreviewActivity
import com.android.wallpaper.picker.data.WallpaperModel
import com.android.wallpaper.picker.preview.data.repository.EffectsRepository
import com.android.wallpaper.picker.preview.data.repository.WallpaperPreviewRepository
import com.android.wallpaper.picker.preview.data.util.LiveWallpaperDownloader
import com.android.wallpaper.picker.preview.ui.fragment.SmallPreviewFragment
import com.android.wallpaper.picker.preview.ui.viewmodel.PreviewActionsViewModel.Companion.getEditActivityIntent
import com.android.wallpaper.picker.preview.ui.viewmodel.PreviewActionsViewModel.Companion.isNewCreativeWallpaper
import com.android.wallpaper.picker.preview.ui.viewmodel.WallpaperPreviewViewModel
import com.android.wallpaper.util.ActivityUtils
import com.android.wallpaper.util.DisplayUtils
import com.android.wallpaper.util.WallpaperConnection
import com.android.wallpaper.util.converter.WallpaperModelFactory
import com.android.wallpaper.util.wallpaperconnection.WallpaperConnectionUtils
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** This activity holds the flow for the preview screen. */
@AndroidEntryPoint(BasePreviewActivity::class)
class WallpaperPreviewActivity :
    Hilt_WallpaperPreviewActivity(), AppbarFragment.AppbarFragmentHost {
    @ApplicationContext @Inject lateinit var appContext: Context
    @Inject lateinit var displayUtils: DisplayUtils
    @Inject lateinit var wallpaperModelFactory: WallpaperModelFactory
    @Inject lateinit var wallpaperPreviewRepository: WallpaperPreviewRepository
    @Inject lateinit var effectsRepository: EffectsRepository
    @Inject lateinit var liveWallpaperDownloader: LiveWallpaperDownloader

    private val wallpaperPreviewViewModel: WallpaperPreviewViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enforcePortraitForHandheldAndFoldedDisplay()
        window.navigationBarColor = Color.TRANSPARENT
        window.statusBarColor = Color.TRANSPARENT
        setContentView(R.layout.activity_wallpaper_preview)
        // Fits screen to navbar and statusbar
        WindowCompat.setDecorFitsSystemWindows(window, ActivityUtils.isSUWMode(this))
        val isAssetIdPresent = intent.getBooleanExtra(IS_ASSET_ID_PRESENT, false)
        wallpaperPreviewViewModel.isNewTask = intent.getBooleanExtra(IS_NEW_TASK, false)
        wallpaperPreviewViewModel.isViewAsHome = intent.getBooleanExtra(EXTRA_VIEW_AS_HOME, false)
        val wallpaper =
            checkNotNull(intent.getParcelableExtra(EXTRA_WALLPAPER_INFO, WallpaperInfo::class.java))
                .convertToWallpaperModel()
        wallpaperPreviewRepository.setWallpaperModel(wallpaper)
        val whichPreview =
            if (isAssetIdPresent) WallpaperConnection.WhichPreview.EDIT_NON_CURRENT
            else WallpaperConnection.WhichPreview.EDIT_CURRENT
        wallpaperPreviewViewModel.setWhichPreview(whichPreview)
        if (wallpaper is WallpaperModel.StaticWallpaperModel) {
            wallpaper.staticWallpaperData.cropHints?.let {
                wallpaperPreviewViewModel.setCropHints(it)
            }
        }
        if (
            (wallpaper as? WallpaperModel.StaticWallpaperModel)?.downloadableWallpaperData != null
        ) {
            liveWallpaperDownloader.initiateDownloadableService(
                this,
                wallpaper,
                registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}
            )
        }

        if ((wallpaper as? WallpaperModel.StaticWallpaperModel)?.imageWallpaperData != null) {
            lifecycleScope.launch {
                effectsRepository.initializeEffect(
                    staticWallpaperModel = wallpaper,
                    onWallpaperModelUpdated = { wallpaper ->
                        wallpaperPreviewRepository.setWallpaperModel(wallpaper)
                    },
                )
            }
        }

        val liveWallpaperModel = (wallpaper as? WallpaperModel.LiveWallpaperModel)
        if (liveWallpaperModel != null && liveWallpaperModel.isNewCreativeWallpaper()) {
            // If it's a new creative wallpaper, override the start destination to the fullscreen
            // fragment for the create-new flow of creative wallpapers
            val navController =
                (supportFragmentManager.findFragmentById(R.id.wallpaper_preview_nav_host)
                        as NavHostFragment)
                    .navController
            val navGraph =
                navController.navInflater.inflate(R.navigation.wallpaper_preview_nav_graph)
            navGraph.setStartDestination(R.id.creativeNewPreviewFragment)
            navController.setGraph(
                navGraph,
                Bundle().apply {
                    putParcelable(
                        SmallPreviewFragment.ARG_EDIT_INTENT,
                        liveWallpaperModel.liveWallpaperData.getEditActivityIntent()
                    )
                }
            )
        }
    }

    override fun onUpArrowPressed() {
        onBackPressedDispatcher.onBackPressed()
    }

    override fun isUpArrowSupported(): Boolean {
        return !ActivityUtils.isSUWMode(baseContext)
    }

    override fun onResume() {
        super.onResume()
        if (isInMultiWindowMode) {
            Toast.makeText(this, R.string.wallpaper_exit_split_screen, Toast.LENGTH_SHORT).show()
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onDestroy() {
        liveWallpaperDownloader.cleanup()
        (wallpaperPreviewViewModel.wallpaper.value as? WallpaperModel.LiveWallpaperModel)?.let {
            runBlocking { WallpaperConnectionUtils.disconnect(applicationContext, it) }
        }
        effectsRepository.destroy()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        enforcePortraitForHandheldAndFoldedDisplay()
    }

    private fun WallpaperInfo.convertToWallpaperModel(): WallpaperModel {
        return wallpaperModelFactory.getWallpaperModel(appContext, this)
    }

    companion object {
        /**
         * Returns a new [Intent] that can be used to start [WallpaperPreviewActivity].
         *
         * @param context application context.
         * @param wallpaperInfo selected by user for editing preview.
         * @param isNewTask true to launch at a new task.
         *
         * TODO(b/291761856): Use wallpaper model to replace wallpaper info.
         */
        fun newIntent(
            context: Context,
            wallpaperInfo: WallpaperInfo,
            isAssetIdPresent: Boolean,
            isViewAsHome: Boolean = false,
            isNewTask: Boolean = false,
        ): Intent {
            val intent = Intent(context.applicationContext, WallpaperPreviewActivity::class.java)
            if (isNewTask) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            intent.putExtra(EXTRA_WALLPAPER_INFO, wallpaperInfo)
            intent.putExtra(IS_ASSET_ID_PRESENT, isAssetIdPresent)
            intent.putExtra(EXTRA_VIEW_AS_HOME, isViewAsHome)
            intent.putExtra(IS_NEW_TASK, isNewTask)
            return intent
        }
    }

    /**
     * If the display is a handheld display or a folded display from a foldable, we enforce the
     * activity to be portrait.
     *
     * This method should be called upon initialization of this activity, and whenever there is a
     * configuration change.
     */
    private fun enforcePortraitForHandheldAndFoldedDisplay() {
        val wantedOrientation =
            if (displayUtils.isLargeScreenOrUnfoldedDisplay(this))
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        if (requestedOrientation != wantedOrientation) {
            requestedOrientation = wantedOrientation
        }
    }
}
