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

package com.google.android.wallpaper.weathereffects

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.torus.core.activity.TorusViewerActivity
import com.google.android.torus.core.engine.TorusEngine
import com.google.android.torus.utils.extensions.setImmersiveFullScreen
import com.google.android.wallpaper.weathereffects.dagger.BackgroundScope
import com.google.android.wallpaper.weathereffects.dagger.MainScope
import com.google.android.wallpaper.weathereffects.provider.WallpaperInfoContract
import com.google.android.wallpaper.weathereffects.shared.model.WallpaperFileModel
import com.google.android.wallpaper.weathereffects.domain.WeatherEffectsInteractor
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WallpaperEffectsDebugActivity : TorusViewerActivity() {

    @Inject
    @MainScope
    lateinit var mainScope: CoroutineScope
    @Inject
    @BackgroundScope
    lateinit var bgScope: CoroutineScope
    @Inject
    lateinit var context: Context
    @Inject
    lateinit var interactor: WeatherEffectsInteractor

    private lateinit var rootView: FrameLayout
    private lateinit var surfaceView: SurfaceView
    private var engine: WeatherEngine? = null
    private var weatherEffect: WallpaperInfoContract.WeatherEffect? = null
    private var assetIndex = 0
    private val fgCachedAssetPaths: ArrayList<String> = arrayListOf()
    private val bgCachedAssetPaths: ArrayList<String> = arrayListOf()

    override fun getWallpaperEngine(context: Context, surfaceView: SurfaceView): TorusEngine {
        this.surfaceView = surfaceView
        val engine = WeatherEngine(surfaceView.holder, context)
        this.engine = engine
        return engine
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WallpaperEffectsDebugApplication.graph.inject(this)

        setContentView(R.layout.debug_activity)
        setImmersiveFullScreen()

         writeAssetsToCache()

        rootView = requireViewById(R.id.main_layout)
        rootView.requireViewById<FrameLayout>(R.id.wallpaper_layout).addView(surfaceView)

        rootView.requireViewById<Button>(R.id.rain).setOnClickListener {
            weatherEffect = WallpaperInfoContract.WeatherEffect.RAIN
            updateWallpaper()
            setDebugText(context.getString(R.string.generating))
        }
        rootView.requireViewById<Button>(R.id.fog).setOnClickListener {
            weatherEffect = WallpaperInfoContract.WeatherEffect.FOG
            updateWallpaper()
            setDebugText(context.getString(R.string.generating))
        }
        rootView.requireViewById<Button>(R.id.snow).setOnClickListener {
            weatherEffect = WallpaperInfoContract.WeatherEffect.SNOW
            updateWallpaper()
            setDebugText(context.getString(R.string.generating))
        }
        rootView.requireViewById<Button>(R.id.clear).setOnClickListener {
            weatherEffect = null

            updateWallpaper()
        }
        rootView.requireViewById<Button>(R.id.change_asset).setOnClickListener {
            assetIndex = (assetIndex + 1) % fgCachedAssetPaths.size

            updateWallpaper()
        }

        rootView.requireViewById<Button>(R.id.set_wallpaper).setOnClickListener {
            val i = Intent()
            i.action = WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER
            i.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this, WeatherWallpaperService::class.java)
            )
            this.startActivityForResult(i, SET_WALLPAPER_REQUEST_CODE)
            saveWallpaper()
        }

        rootView.requireViewById<FrameLayout>(R.id.wallpaper_layout)
            .setOnTouchListener { view, event ->
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (rootView.requireViewById<ConstraintLayout>(R.id.buttons).visibility
                            == View.GONE) {
                            showButtons()
                        } else {
                            hideButtons()
                        }
                    }
                }

                view.onTouchEvent(event)
            }

        engine?.initialize(mainScope, interactor)
        setDebugText()
    }

    private fun writeAssetsToCache() {
        // Writes test files from assets to local cache dir
        // (on the main thread!.. only ok for the debug app)
        fgCachedAssetPaths.apply {
            clear()
            addAll(
                listOf(
                    /* TODO(b/300991599): Add debug assets. */
                    FOREGROUND_IMAGE_1,
                    FOREGROUND_IMAGE_2,
                    FOREGROUND_IMAGE_3,
                ).map { getFileFromAssets(it).absolutePath })
        }
        bgCachedAssetPaths.apply {
            clear()
            addAll(
                listOf(
                    /* TODO(b/300991599): Add debug assets. */
                    BACKGROUND_IMAGE_1,
                    BACKGROUND_IMAGE_2,
                    BACKGROUND_IMAGE_3,
                ).map { getFileFromAssets(it).absolutePath })
        }
    }

    private fun getFileFromAssets(fileName: String): File {
        return File(context.cacheDir, fileName).also {
            if (!it.exists()) {
                it.outputStream().use { cache ->
                    context.assets.open(fileName).use { inputStream ->
                        inputStream.copyTo(cache)
                    }
                }
            }
        }
    }

    private fun updateWallpaper() {
        mainScope.launch {
            val fgPath = fgCachedAssetPaths[assetIndex]
            val bgPath = bgCachedAssetPaths[assetIndex]
            interactor.updateWallpaper(
                WallpaperFileModel(
                    fgPath,
                    bgPath,
                    weatherEffect,
                )
            )
            setDebugText("Wallpaper updated successfully.\n* Weather: " +
                    "$weatherEffect\n* Foreground: $fgPath\n* Background: $bgPath")
        }
    }

    private fun saveWallpaper() {
        bgScope.launch {
            interactor.saveWallpaper()
        }
    }

    private fun setDebugText(text: String? = null) {
        val output = rootView.requireViewById<TextView>(R.id.output)
        output.text = text

        if (text.isNullOrEmpty()) {
            output.visibility = View.INVISIBLE
        } else {
            output.visibility = View.VISIBLE
            mainScope.launch {
                // Make the text disappear after 3 sec.
                delay(3000L)
                output.visibility = View.INVISIBLE
            }
        }
    }

    private fun showButtons() {
        val buttons = rootView.requireViewById<ConstraintLayout>(R.id.buttons)
        buttons.visibility = View.VISIBLE
        buttons.animate().alpha(1f).setDuration(400).setListener(null)
    }

    private fun hideButtons() {
        val buttons = rootView.requireViewById<ConstraintLayout>(R.id.buttons)
        buttons.animate()
            .alpha(0f)
            .setDuration(400)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    buttons.visibility = View.GONE
                }
            })
    }

    private companion object {
        // TODO(b/300991599): Add debug assets.
        private const val FOREGROUND_IMAGE_1 = "test-foreground.png"
        private const val BACKGROUND_IMAGE_1 = "test-background.png"
        private const val FOREGROUND_IMAGE_2 = "test-foreground2.png"
        private const val BACKGROUND_IMAGE_2 = "test-background2.png"
        private const val FOREGROUND_IMAGE_3 = "test-foreground3.png"
        private const val BACKGROUND_IMAGE_3 = "test-background3.png"
        private const val SET_WALLPAPER_REQUEST_CODE = 2
    }
}
