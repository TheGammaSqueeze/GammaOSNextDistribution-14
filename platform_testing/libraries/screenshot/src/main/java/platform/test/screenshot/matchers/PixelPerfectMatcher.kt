/*
 * Copyright 2022 The Android Open Source Project
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
import kotlin.collections.List
import platform.test.screenshot.proto.ScreenshotResultProto

/** Bitmap matching that does an exact comparison of pixels between bitmaps. */
class PixelPerfectMatcher : BitmapMatcher() {

    override fun compareBitmaps(
        expected: IntArray,
        given: IntArray,
        width: Int,
        height: Int,
        regions: List<Rect>
    ): MatchResult {
        check(expected.size == given.size)

        val filter = getFilter(width, height, regions)
        var different = 0
        var same = 0
        var ignored = 0

        val diffArray = lazy { IntArray(width * height) { Color.TRANSPARENT } }

        expected.indices.forEach { index ->
            when {
                !filter[index] -> ignored++
                expected[index] == given[index] -> same++
                else -> diffArray.value[index] = Color.MAGENTA.also { different++ }
            }
        }

        val stats =
            ScreenshotResultProto.DiffResult.ComparisonStatistics.newBuilder()
                .setNumberPixelsCompared(width * height)
                .setNumberPixelsIdentical(same)
                .setNumberPixelsDifferent(different)
                .setNumberPixelsIgnored(ignored)
                .build()

        return if (different > 0) {
            val diff = Bitmap.createBitmap(diffArray.value, width, height, Bitmap.Config.ARGB_8888)
            MatchResult(matches = false, diff = diff, comparisonStatistics = stats)
        } else {
            MatchResult(matches = true, diff = null, comparisonStatistics = stats)
        }
    }
}
