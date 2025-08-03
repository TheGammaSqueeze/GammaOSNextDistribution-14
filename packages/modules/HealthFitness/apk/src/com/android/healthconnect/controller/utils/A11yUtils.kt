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
 *
 */

package com.android.healthconnect.controller.utils

import android.content.Context
import android.graphics.Rect
import android.view.TouchDelegate
import android.view.View
import com.android.healthconnect.controller.R
import kotlin.math.max

/** Increases a view's touch target size and uses the parentView as the touch delegate. */
fun increaseViewTouchTargetSize(context: Context, childView: View, parentView: View) {
    val largerTouchBounds = Rect()
    childView.getHitRect(largerTouchBounds)
    val margin =
        getTouchTargetMargin(
            context.resources.getDimension(R.dimen.button_size), largerTouchBounds.height())
    largerTouchBounds.top -= margin
    largerTouchBounds.left -= margin
    largerTouchBounds.bottom += margin
    largerTouchBounds.right += margin
    parentView.touchDelegate = TouchDelegate(largerTouchBounds, childView)
}

fun getTouchTargetMargin(desiredSize: Float, actualSize: Int): Int {
    return max(((desiredSize - actualSize) / 2) * 1.0f, 0f).toInt()
}
