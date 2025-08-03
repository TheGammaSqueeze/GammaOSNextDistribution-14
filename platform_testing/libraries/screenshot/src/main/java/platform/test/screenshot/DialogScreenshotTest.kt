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

package platform.test.screenshot

import android.app.Activity
import android.app.Dialog
import android.graphics.Bitmap
import androidx.test.ext.junit.rules.ActivityScenarioRule
import java.util.concurrent.TimeUnit
import platform.test.screenshot.matchers.BitmapMatcher

fun <A : Activity> dialogScreenshotTest(
    activityRule: ActivityScenarioRule<A>,
    bitmapDiffer: BitmapDiffer,
    matcher: BitmapMatcher,
    goldenIdentifier: String,
    waitForIdle: () -> Unit = {},
    dialogProvider: (A) -> Dialog,
) {
    var dialog: Dialog? = null
    activityRule.scenario.onActivity { activity ->
        dialog =
            dialogProvider(activity).apply {
                val window = checkNotNull(window)

                // Make sure that the dialog draws full screen and fits the whole display
                // instead of the system bars.
                window.setDecorFitsSystemWindows(false)

                // Disable enter/exit animations.
                create()
                window.setWindowAnimations(0)

                // Elevation/shadows is not deterministic when doing hardware rendering, so we
                // disable it for any view in the hierarchy.
                window.decorView.removeElevationRecursively()

                // Show the dialog.
                show()
            }
    }

    waitForIdle()

    try {
        val bitmap = dialog?.toBitmap() ?: error("dialog is null")

        bitmapDiffer.assertBitmapAgainstGolden(
            bitmap,
            goldenIdentifier,
            matcher,
        )
    } finally {
        dialog?.dismiss()
    }
}

private fun Dialog.toBitmap(): Bitmap {
    val window = checkNotNull(window)
    return window.decorView.captureToBitmapAsync().get(10, TimeUnit.SECONDS)
        ?: error("timeout while trying to capture view to bitmap for window")
}
