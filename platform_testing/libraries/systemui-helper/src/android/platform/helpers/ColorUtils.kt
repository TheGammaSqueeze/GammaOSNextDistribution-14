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

package android.platform.helpers

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.runner.screenshot.Screenshot
import androidx.test.uiautomator.UiDevice

fun UiDevice.getScreenBorderColors(): ScreenBorderColors {
    val screenshot: Bitmap = Screenshot.capture().bitmap

    val firstColumn = screenshot.columnColor(x = 0)
    val lastColumn = screenshot.columnColor(x = screenshot.width - 1)
    return ScreenBorderColors(leftColumn = firstColumn, rightColumn = lastColumn)
}

/** Represents colors of the first and last screen column. */
data class ScreenBorderColors(val leftColumn: List<Color>, val rightColumn: List<Color>) {
    infix fun darkerThan(other: ScreenBorderColors): Boolean =
        leftColumn darkerThan other.leftColumn && rightColumn darkerThan other.rightColumn
}

/** Returns a list of colors of the column at [x]. */
fun Bitmap.columnColor(x: Int): List<Color> = (0 until height).map { y -> getColor(x, y) }

/** Returns middle element of a list. Used for debugging purposes only. */
fun List<Color>.middle(): Color? = getOrNull(size / 2)

/** Returns whether i-th colors in this list have a luminance lower than the i-th one in [other] */
infix fun List<Color>.darkerThan(other: List<Color>): Boolean =
    zip(other).all { (thisColor, otherColor) -> thisColor darkerThan otherColor }

/** Returns whether this color is darker than [other] based on [Color.luminance]. */
infix fun Color.darkerThan(other: Color): Boolean = luminance() < other.luminance()

/** Returns [true] if the entire device screen is completely black. */
// TODO(b/262588714): Add tracing once this is moved to uiautomator_utils.
//  (androidx.tracing is not available here.)
fun UiDevice.hasBlackScreen(): Boolean = Screenshot.capture().bitmap.isBlack()

private fun Bitmap.isBlack(): Boolean {
    for (i in 0 until width) {
        for (j in 0 until height) {
            if (getColor(i, j).toArgb() != Color.BLACK) {
                return false
            }
        }
    }
    return true
}
