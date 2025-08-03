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

package platform.test.screenshot

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.test.runner.screenshot.Screenshot
import platform.test.screenshot.matchers.BitmapMatcher
import platform.test.screenshot.matchers.PixelPerfectMatcher

interface ScreenshotAsserter {
    fun assertGoldenImage(goldenId: String, areas: List<Rect>)
    fun assertGoldenImage(goldenId: String)
}

/**
 * Factory to create [ScreenshotAsserter] instances.
 *
 * Prefer using this interface over of exposing [ScreenshotTestRule] - or its wrappers - directly.
 */
interface ScreenshotAsserterFactory {
    /** Creates a pre-configured [ScreenshotAsserter]. */
    fun createScreenshotAsserter(
        config: ScreenshotAsserterConfig = ScreenshotAsserterConfig()
    ): ScreenshotAsserter
}

/** Config options to configure new [ScreenshotAsserter] instances. */
data class ScreenshotAsserterConfig(
    /**
     * Strategy to compute whether two bitmaps are the same for the purpose of a screenshot golden
     * tests
     */
    val matcher: BitmapMatcher = PixelPerfectMatcher(),
    val beforeScreenshot: () -> Unit = {},
    val afterScreenshot: () -> Unit = {},
    /**
     * The [Bitmap] produced by [captureStrategy] will be recycled immediately after assertions are
     * completed. Therefore, do not retain references to created [Bitmap]s.
     */
    val captureStrategy: BitmapSupplier = { Screenshot.capture().bitmap }
)
