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

package com.android.permissioncontroller.safetycenter.ui

import android.graphics.Rect
import android.os.Build
import android.view.TouchDelegate
import android.view.View
import androidx.annotation.DimenRes
import androidx.annotation.RequiresApi

/** Class to configure touch targets for Safety Center. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
object SafetyCenterTouchTarget {
    /**
     * Resizes the touch target of views by delegating to the parent component.
     *
     * @param view component that will be expanded
     * @param minTouchTargetSizeResource required minimum touch target size
     */
    @JvmStatic
    fun configureSize(view: View, @DimenRes minTouchTargetSizeResource: Int) {
        val parent = view.parent as View
        val res = view.context.resources
        val minTouchTargetSize = res.getDimensionPixelSize(minTouchTargetSizeResource)

        // Defer getHitRect so that it's called after the parent's children are laid out.
        parent.post {
            val hitRect = Rect()
            view.getHitRect(hitRect)
            val currentTouchTargetWidth = hitRect.width()
            if (currentTouchTargetWidth < minTouchTargetSize) {
                // Divide width difference by two to get adjustment
                val adjustInsetBy = (minTouchTargetSize - currentTouchTargetWidth) / 2

                // Inset adjustment is applied to top, bottom, left, right
                hitRect.inset(-adjustInsetBy, -adjustInsetBy)
                parent.touchDelegate = TouchDelegate(hitRect, view)
            }
        }
    }
}
