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

package com.google.android.wallpaper.weathereffects.graphics.utils

import android.graphics.Matrix
import android.util.SizeF

/** Helper functions for matrix operations. */
object MatrixUtils {
    /** Returns a [Matrix] that crops the image and centers to the screen. */
    fun centerCropMatrix(surfaceSize: SizeF, imageSize: SizeF): Matrix {
        val widthScale = surfaceSize.width / imageSize.width
        val heightScale = surfaceSize.height / imageSize.height
        val scale = maxOf(widthScale, heightScale)

        return Matrix(Matrix.IDENTITY_MATRIX).apply {
            // Move the origin of the image to its center.
            postTranslate(-imageSize.width / 2f, -imageSize.height / 2f)
            // Apply scale.
            postScale(scale, scale)
            // Translate back to the center of the screen.
            postTranslate(surfaceSize.width / 2f, surfaceSize.height / 2f)
        }
    }
}
