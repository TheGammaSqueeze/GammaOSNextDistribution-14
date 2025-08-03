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

package com.android.wallpaper.picker.customization.ui.section

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.android.wallpaper.R
import com.android.wallpaper.model.CustomizationSectionController
import com.android.wallpaper.picker.SectionView
import java.util.*

/**
 * A section controller that renders two sections that are connected.
 *
 * In portrait mode, they are rendered vertically; in landscape mode, side-by-side.
 */
class ConnectedSectionController(
    /** First section. */
    private val firstSectionController: CustomizationSectionController<out SectionView>,
    /** Second section. */
    private val secondSectionController: CustomizationSectionController<out SectionView>,
    /** Whether to flip the order of the child sections when laid out horizontally. */
    private val reverseOrderWhenHorizontal: Boolean = false,
) : CustomizationSectionController<ResponsiveLayoutSectionView> {
    override fun isAvailable(context: Context): Boolean {
        return firstSectionController.isAvailable(context) ||
            secondSectionController.isAvailable(context)
    }

    @SuppressLint("InflateParams") // It's okay that we're inflating without a parent view.
    override fun createView(context: Context): ResponsiveLayoutSectionView {
        val view =
            LayoutInflater.from(context)
                .inflate(
                    R.layout.responsive_section,
                    null,
                ) as ResponsiveLayoutSectionView

        val isHorizontal = view.orientation == LinearLayout.HORIZONTAL
        val flipViewOrder = reverseOrderWhenHorizontal && isHorizontal

        add(
            parentView = view,
            childController =
                if (flipViewOrder) {
                    secondSectionController
                } else {
                    firstSectionController
                },
            isHorizontal = isHorizontal,
            isFirst = true,
        )
        add(
            parentView = view,
            childController =
                if (flipViewOrder) {
                    firstSectionController
                } else {
                    secondSectionController
                },
            isHorizontal = isHorizontal,
            isFirst = false,
        )

        return view
    }

    private fun add(
        parentView: LinearLayout,
        childController: CustomizationSectionController<out SectionView>,
        isHorizontal: Boolean,
        isFirst: Boolean,
    ) {
        val childView =
            childController.createView(
                context = parentView.context,
                params =
                    CustomizationSectionController.ViewCreationParams(
                        isConnectedHorizontallyToOtherSections = isHorizontal,
                    ),
            )

        if (isHorizontal) {
            // We want each child to stretch to fill an equal amount as the other children.
            childView.layoutParams =
                LinearLayout.LayoutParams(
                    /* width= */ 0,
                    /* height= */ LinearLayout.LayoutParams.WRAP_CONTENT,
                    /* weight= */ 1f,
                )

            val isLeftToRight =
                TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) ==
                    View.LAYOUT_DIRECTION_LTR
            childView.setBackgroundResource(
                if (isLeftToRight) {
                    // In left-to-right layouts, the first item is on the left.
                    if (isFirst) {
                        R.drawable.leftmost_connected_section_background
                    } else {
                        R.drawable.rightmost_connected_section_background
                    }
                } else {
                    // In right-to-left layouts, the first item is on the right.
                    if (isFirst) {
                        R.drawable.rightmost_connected_section_background
                    } else {
                        R.drawable.leftmost_connected_section_background
                    }
                }
            )
        }

        parentView.addView(childView)
    }
}
