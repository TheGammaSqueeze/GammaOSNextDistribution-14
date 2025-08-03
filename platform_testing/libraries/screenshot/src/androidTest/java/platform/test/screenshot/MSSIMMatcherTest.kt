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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.screenshot.matchers.MSSIMMatcher
import platform.test.screenshot.utils.loadBitmap

@RunWith(AndroidJUnit4::class)
@MediumTest
class MSSIMMatcherTest {

    @Test
    fun performDiff_sameBitmaps() {
        val first = loadBitmap("round_rect_gray")
        val second = loadBitmap("round_rect_gray")
        val filter = BooleanArray(first.width * first.height) { true }

        val matcher = MSSIMMatcher()
        val result =
            matcher.calculateSSIM(
                first.toIntArray(),
                second.toIntArray(),
                first.width,
                first.height,
                filter
            )

        // Asserts that all compared pixels are categorized as "similar".
        assertThat(result.SSIM).isEqualTo(1.0)
    }

    @Test
    fun performDiff_noPixelsCompared() {
        val first = loadBitmap("checkbox_checked")
        val second = loadBitmap("round_rect_gray")
        val filter = BooleanArray(first.width * first.height) { false }

        val matcher = MSSIMMatcher()
        val result =
            matcher.calculateSSIM(
                first.toIntArray(),
                second.toIntArray(),
                first.width,
                first.height,
                filter
            )

        assertThat(result.numPixelsCompared).isEqualTo(0)
    }

    @Test
    fun performDiff_sameRegion() {
        val first = loadBitmap("qmc-folder1")
        val second = loadBitmap("qmc-folder2")
        val filter = BooleanArray(first.width * first.height) { false }
        val startHeight = 18 * first.height / 20
        val endHeight = 37 * first.height / 40
        val startWidth = 10 * first.width / 20
        val endWidth = 11 * first.width / 20
        for (i in startHeight..endHeight) {
            for (j in startWidth..endWidth) {
                filter[j + i * first.width] = true
            }
        }

        val matcher = MSSIMMatcher()
        val result =
            matcher.calculateSSIM(
                first.toIntArray(),
                second.toIntArray(),
                first.width,
                first.height,
                filter
            )

        // Asserts that all compared pixels are categorized as "similar".
        assertThat(result.SSIM).isEqualTo(1.0)
    }

    @Test
    fun performDiff_checkedAgainstUnchecked() {
        val first = loadBitmap("checkbox_checked")
        val second = loadBitmap("round_rect_gray")
        val filter = BooleanArray(first.width * first.height) { true }

        val matcher = MSSIMMatcher()
        val result =
            matcher.calculateSSIM(
                first.toIntArray(),
                second.toIntArray(),
                first.width,
                first.height,
                filter
            )

        assertThat(result.SSIM).isWithin(0.001).of(0.490)
    }

    @Test
    fun performDiff_differentBorders() {
        val first = loadBitmap("round_rect_gray")
        val second = loadBitmap("round_rect_green")
        val filter = BooleanArray(first.width * first.height) { true }

        val matcher = MSSIMMatcher()
        val result =
            matcher.calculateSSIM(
                first.toIntArray(),
                second.toIntArray(),
                first.width,
                first.height,
                filter
            )

        assertThat(result.SSIM).isWithin(0.001).of(0.951)
    }

    @Test
    fun performDiff_fullscreen_differentBorders_dark() {
        val first = loadBitmap("fullscreen_rect_gray")
        val second = loadBitmap("fullscreen_rect_gray_dark")
        val filter = BooleanArray(first.width * first.height) { true }

        val matcher = MSSIMMatcher()
        val result =
            matcher.calculateSSIM(
                first.toIntArray(),
                second.toIntArray(),
                first.width,
                first.height,
                filter
            )

        assertThat(result.SSIM).isWithin(0.001).of(0.990)
    }

    @Test
    fun performDiff_differentBorders_dark() {
        val first = loadBitmap("round_rect_gray")
        val second = loadBitmap("round_rect_gray_dark")
        val filter = BooleanArray(first.width * first.height) { true }

        val matcher = MSSIMMatcher()
        val result =
            matcher.calculateSSIM(
                first.toIntArray(),
                second.toIntArray(),
                first.width,
                first.height,
                filter
            )

        assertThat(result.SSIM).isWithin(0.001).of(0.960)
    }

    @Test
    fun performDiff_fullscreen_movedToRight() {
        val first = loadBitmap("fullscreen_rect_gray")
        val second = loadBitmap("fullscreen_rect_gray_moved_1px")
        val filter = BooleanArray(first.width * first.height) { true }

        val matcher = MSSIMMatcher()
        val result =
            matcher.calculateSSIM(
                first.toIntArray(),
                second.toIntArray(),
                first.width,
                first.height,
                filter
            )

        assertThat(result.SSIM).isWithin(0.001).of(0.695)
    }

    @Test
    fun performDiff_fullscreen_checkboxes_differentRadius() {
        val first = loadBitmap("fullscreen_checked_checkbox")
        val second = loadBitmap("fullscreen_checked_checkbox_round")
        val filter = BooleanArray(first.width * first.height) { true }

        val matcher = MSSIMMatcher()
        val result =
            matcher.calculateSSIM(
                first.toIntArray(),
                second.toIntArray(),
                first.width,
                first.height,
                filter
            )

        assertThat(result.SSIM).isWithin(0.001).of(0.921)
    }
}
