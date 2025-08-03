/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.wallpaper.widget.floatingsheetcontent

import android.content.Context
import android.content.Intent
import com.android.wallpaper.R
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.module.InjectorProvider
import com.android.wallpaper.picker.WallpaperInfoHelper

/** Floating Sheet Content for displaying wallpaper info */
class WallpaperInfoContent(private var context: Context, private val wallpaper: WallpaperInfo?) :
    FloatingSheetContent<WallpaperInfoView>(context) {

    private var exploreIntent: Intent? = null
    private var actionLabel: CharSequence? = null
    private var wallpaperInfoView: WallpaperInfoView? = null
    override val viewId: Int
        get() = R.layout.floating_sheet_wallpaper_info_view

    /** Gets called when the content view is created or recreated by [FloatingSheetContent] */
    override fun onViewCreated(view: WallpaperInfoView) {
        wallpaperInfoView = view
        context = view.context
        initializeWallpaperContent()
    }

    private fun initializeWallpaperContent() {
        if (wallpaper == null) {
            return
        }
        if (actionLabel == null) {
            setUpExploreIntentAndLabel { populateWallpaperInfo(wallpaperInfoView) }
        } else {
            populateWallpaperInfo(wallpaperInfoView)
        }
    }

    private fun setUpExploreIntentAndLabel(callback: Runnable?) {
        WallpaperInfoHelper.loadExploreIntent(context, wallpaper!!) {
            actionLabel: CharSequence?,
            exploreIntent: Intent? ->
            this.actionLabel = actionLabel
            this.exploreIntent = exploreIntent
            callback?.run()
        }
    }

    private fun onExploreClicked() {
        val injector = InjectorProvider.getInjector()
        injector.getUserEventLogger().logWallpaperExploreButtonClicked()
        context.startActivity(exploreIntent)
    }

    private fun populateWallpaperInfo(view: WallpaperInfoView?) {
        view!!.populateWallpaperInfo(
            wallpaper!!,
            actionLabel,
            WallpaperInfoHelper.shouldShowExploreButton(context, exploreIntent)
        ) {
            onExploreClicked()
        }
    }
}
