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
package com.android.wallpaper.picker

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceControl
import android.view.SurfaceView

/** A `SurfaceView` which allows the surface alpha to be adjusted by setting view alpha. */
class FadeAnimationSurfaceView(context: Context, attrs: AttributeSet) :
    SurfaceView(context, attrs) {

    override fun onSetAlpha(alpha: Int): Boolean {
        requestUpdateSurfacePositionAndScale()
        return super.onSetAlpha(alpha)
    }

    override fun onSetSurfacePositionAndScale(
        transaction: SurfaceControl.Transaction,
        surface: SurfaceControl,
        positionLeft: Int,
        positionTop: Int,
        postScaleX: Float,
        postScaleY: Float
    ) {
        super.onSetSurfacePositionAndScale(
            transaction,
            surface,
            positionLeft,
            positionTop,
            postScaleX,
            postScaleY
        )
        transaction.setAlpha(surface, alpha)
    }
}
