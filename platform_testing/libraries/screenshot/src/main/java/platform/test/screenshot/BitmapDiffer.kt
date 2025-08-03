/*
 * Copyright (C) 2024 The Android Open Source Project
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
import platform.test.screenshot.matchers.BitmapMatcher

/**
 * Asserts that a supplied bitmap matches a previously generated golden image.
 *
 * Assertion results are exported as a test artifact, to be uploaded to Scuba by the test harness.
 * Throws an exception whenever the bitmap does not match the golden.
 */
interface BitmapDiffer {
    /**
     * Asserts the given bitmap against the golden identified by the given name.
     *
     * Note: The golden identifier should be unique per your test module (unless you want multiple
     * tests to match the same golden). The name must not contain extension. You should also avoid
     * adding strings like "golden", "image" and instead describe what is the golder referring to.
     *
     * @param actual The bitmap captured during the test.
     * @param goldenIdentifier Name of the golden. Allowed characters: 'A-Za-z0-9_-'
     * @param matcher The algorithm to be used to perform the matching.
     * @param regions An optional array of interesting regions for partial screenshot diff.
     * @throws IllegalArgumentException If the golden identifier contains forbidden characters or is
     *   empty.
     * @see MSSIMMatcher
     * @see PixelPerfectMatcher
     * @see Bitmap.assertAgainstGolden
     */
    fun assertBitmapAgainstGolden(
        actual: Bitmap,
        goldenIdentifier: String,
        matcher: BitmapMatcher,
        regions: List<Rect> = emptyList(),
    )
}
