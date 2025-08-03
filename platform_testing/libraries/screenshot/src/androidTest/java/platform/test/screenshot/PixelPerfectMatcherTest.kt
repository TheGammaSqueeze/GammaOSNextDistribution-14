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

package platform.test.screenshot

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.screenshot.matchers.PixelPerfectMatcher
import platform.test.screenshot.utils.loadBitmap

@RunWith(AndroidJUnit4::class)
@MediumTest
class PixelPerfectMatcherTest {

    @Test
    fun performDiff_sameBitmaps() {
        val first = loadBitmap("round_rect_gray")
        val second = loadBitmap("round_rect_gray")

        val matcher = PixelPerfectMatcher()
        val result =
            matcher.compareBitmaps(
                first.toIntArray(),
                second.toIntArray(),
                first.width,
                first.height
            )

        assertThat(result.matches).isTrue()
    }

    @Test
    fun performDiff_sameSize_partialCompare_topLeftOutOfBoundaries_diff() {
        val first = loadBitmap("round_rect_gray")
        val second = loadBitmap("round_rect_gray_green_bounds")

        val matcher = PixelPerfectMatcher()
        val result =
            matcher.compareBitmaps(
                expected = first.toIntArray(),
                given = second.toIntArray(),
                width = first.width,
                height = first.height,
                regions = listOf(Rect(-10, -10, 0, 0))
            )

        assertThat(result.matches).isFalse()
    }

    @Test
    fun performDiff_sameSize_partialCompare_topLeft_diffBoundaries() {
        val first = loadBitmap("round_rect_gray")
        val second = loadBitmap("round_rect_gray_green_bounds")

        val matcher = PixelPerfectMatcher()
        val result =
            matcher.compareBitmaps(
                expected = first.toIntArray(),
                given = second.toIntArray(),
                width = first.width,
                height = first.height,
                regions = listOf(Rect(0, 0, first.width / 2, first.height / 2))
            )

        assertThat(result.matches).isFalse()
    }

    @Test
    fun performDiff_sameSize_partialCompare_bottomRight() {
        val first = loadBitmap("round_rect_gray")
        val second = loadBitmap("round_rect_gray_green_bounds")

        val matcher = PixelPerfectMatcher()
        val result =
            matcher.compareBitmaps(
                expected = first.toIntArray(),
                given = second.toIntArray(),
                width = first.width,
                height = first.height,
                regions = listOf(Rect(first.width / 2, first.height / 2, first.width, first.height))
            )

        assertThat(result.matches).isFalse()
    }

    @Test
    fun performDiff_sameSize_partialCompare_bottomRightOutOfBoundaries_diff() {
        val first = loadBitmap("round_rect_gray")
        val second = loadBitmap("round_rect_gray_green_bounds")

        val matcher = PixelPerfectMatcher()
        val result =
            matcher.compareBitmaps(
                expected = first.toIntArray(),
                given = second.toIntArray(),
                width = first.width,
                height = first.height,
                regions =
                    listOf(
                        Rect(first.width / 2, first.height / 2, first.width + 10, first.height + 10)
                    )
            )

        assertThat(result.matches).isFalse()
    }

    @Test
    fun performDiff_sameSize_differentBorders() {
        val first = loadBitmap("round_rect_gray")
        val second = loadBitmap("round_rect_green")

        val matcher = PixelPerfectMatcher()
        val result =
            matcher.compareBitmaps(
                first.toIntArray(),
                second.toIntArray(),
                first.width,
                first.height
            )

        assertThat(result.matches).isFalse()
    }

    @Test
    fun performDiff_sameSize_differentBorders_partialCompare() {
        val first = loadBitmap("round_rect_gray")
        val second = loadBitmap("round_rect_green")

        val matcher = PixelPerfectMatcher()
        val result =
            matcher.compareBitmaps(
                first.toIntArray(),
                second.toIntArray(),
                first.width,
                first.height,
                listOf(Rect(/* left= */ 1, /* top= */ 1, /* right= */ 4, /* bottom= */ 4))
            )

        assertThat(result.matches).isTrue()
    }

    @Test
    fun performDiff_sameSize_partialCompare_checkDiffImage() {
        val first = loadBitmap("qmc-folder1")
        val second = loadBitmap("qmc-folder2")
        val matcher = PixelPerfectMatcher()
        val interestingRegion =
            Rect(/* left= */ 10, /* top= */ 15, /* right= */ 70, /* bottom= */ 50)
        val result =
            matcher.compareBitmaps(
                first.toIntArray(),
                second.toIntArray(),
                first.width,
                first.height,
                listOf(interestingRegion)
            )
        val diffImage = result.diff!!.toIntArray()

        assertThat(result.matches).isFalse()
        for (i in 0 until first.height) {
            for (j in 0 until first.width) {
                val rowInRange = i >= interestingRegion.top && i <= interestingRegion.bottom
                val colInRange = j >= interestingRegion.left && j <= interestingRegion.right
                if (!(rowInRange && colInRange)) {
                    assertThat(diffImage[i * first.width + j] == 0).isTrue()
                }
            }
        }
    }
}
