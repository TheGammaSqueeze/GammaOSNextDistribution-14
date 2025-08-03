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
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.android.wallpaper.R
import com.android.wallpaper.model.WallpaperInfo
import java.util.concurrent.Executors

/** A view for displaying wallpaper info. */
class WallpaperInfoView(context: Context?, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    private val executorService = Executors.newCachedThreadPool()
    private var title: TextView? = null
    private var subtitle1: TextView? = null
    private var subtitle2: TextView? = null
    private var exploreButton: Button? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        title = findViewById(R.id.wallpaper_info_title)
        subtitle1 = findViewById(R.id.wallpaper_info_subtitle1)
        subtitle2 = findViewById(R.id.wallpaper_info_subtitle2)
        exploreButton = findViewById(R.id.wallpaper_info_explore_button)
    }

    /** Populates wallpaper info. */
    fun populateWallpaperInfo(
        wallpaperInfo: WallpaperInfo,
        actionLabel: CharSequence?,
        shouldShowExploreButton: Boolean,
        exploreButtonClickListener: OnClickListener?
    ) {
        loadWallpaperInfoData(
            wallpaperInfo.getAttributions(context),
            actionLabel,
            shouldShowExploreButton,
            exploreButtonClickListener,
            shouldShowMetadata(wallpaperInfo)
        )
    }

    private fun loadWallpaperInfoData(
        attributions: List<String?>?,
        actionLabel: CharSequence?,
        shouldShowExploreButton: Boolean,
        exploreButtonClickListener: OnClickListener?,
        shouldShowMetadata: Boolean,
    ) {

        executorService.execute {
            Handler(Looper.getMainLooper()).post {

                // Reset wallpaper information UI
                title?.text = ""
                subtitle1?.text = ""
                subtitle1?.visibility = GONE
                subtitle2?.text = ""
                subtitle2?.visibility = GONE
                exploreButton?.text = ""
                exploreButton?.setOnClickListener(null)
                exploreButton?.visibility = GONE

                if (attributions != null && attributions.size > 0 && attributions[0] != null) {
                    title?.text = attributions[0]
                }
                if (shouldShowMetadata) {
                    if (attributions != null && attributions.size > 1 && attributions[1] != null) {
                        subtitle1?.visibility = VISIBLE
                        subtitle1?.text = attributions[1]
                    }
                    if (attributions != null && attributions.size > 2 && attributions[2] != null) {
                        subtitle2?.visibility = VISIBLE
                        subtitle2?.text = attributions[2]
                    }
                    if (shouldShowExploreButton) {
                        exploreButton?.visibility = VISIBLE
                        exploreButton?.text = actionLabel
                        exploreButton?.setOnClickListener(exploreButtonClickListener)
                    }
                }
            }
        }
    }

    private fun shouldShowMetadata(wallpaperInfo: WallpaperInfo): Boolean {
        val wallpaperComponent = wallpaperInfo.wallpaperComponent
        return wallpaperComponent == null || wallpaperComponent.showMetadataInPreview
    }
}
