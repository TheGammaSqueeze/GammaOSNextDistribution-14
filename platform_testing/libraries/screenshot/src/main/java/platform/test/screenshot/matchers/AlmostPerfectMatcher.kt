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
package platform.test.screenshot.matchers

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import platform.test.screenshot.proto.ScreenshotResultProto

/**
 * Matcher for differences not detectable by human eye. The relaxed threshold allows for low quality
 * png storage.
 */
class AlmostPerfectMatcher(
    private val acceptableThreshold: Double = 0.0,
) : BitmapMatcher() {
    override fun compareBitmaps(
        expected: IntArray,
        given: IntArray,
        width: Int,
        height: Int,
        regions: List<Rect>
    ): MatchResult {
        check(expected.size == given.size) { "Size of two bitmaps does not match" }

        val filter = getFilter(width, height, regions)
        var different = 0
        var same = 0
        var ignored = 0

        val diffArray = lazy { IntArray(width * height) { Color.TRANSPARENT } }

        expected.indices.forEach { index ->
            when {
                !filter[index] -> ignored++
                areSame(expected[index], given[index]) -> same++
                else -> diffArray.value[index] = Color.MAGENTA.also { different++ }
            }
        }

        val matches = different <= (acceptableThreshold * width * height)
        val diffBmp =
            if (different <= 0) null
            else Bitmap.createBitmap(diffArray.value, width, height, Bitmap.Config.ARGB_8888)
        if (matches) {
            ignored += different
            different = 0
        }

        val stats =
            ScreenshotResultProto.DiffResult.ComparisonStatistics.newBuilder()
                .setNumberPixelsCompared(width * height)
                .setNumberPixelsIdentical(same)
                .setNumberPixelsDifferent(different)
                .setNumberPixelsIgnored(ignored)
                .build()

        return MatchResult(matches = matches, diff = diffBmp, comparisonStatistics = stats)
    }

    // ref
    // R. F. Witzel, R. W. Burnham, and J. W. Onley. Threshold and suprathreshold perceptual color
    // differences. J. Optical Society of America, 63:615{625, 1973. 14
    private fun areSame(referenceColor: Int, testColor: Int): Boolean {
        val green = Color.green(referenceColor) - Color.green(testColor)
        val blue = Color.blue(referenceColor) - Color.blue(testColor)
        val red = Color.red(referenceColor) - Color.red(testColor)
        val redMean = (Color.red(referenceColor) + Color.red(testColor)) / 2
        val redScalar = if (redMean < 128) 2 else 3
        val blueScalar = if (redMean < 128) 3 else 2
        val greenScalar = 4
        val correction =
            (redScalar * red * red) + (greenScalar * green * green) + (blueScalar * blue * blue)
        // 1.5 no difference
        // 3.0 observable by experienced human observer
        // 6.0 minimal difference
        // 12.0 perceivable difference
        return correction <= THRESHOLD_SQ
    }

    companion object {
        const val THRESHOLD_SQ = 3 * 3
    }
}
