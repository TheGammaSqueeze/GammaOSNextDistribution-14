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

package platform.test.screenshot.utils.compose

import android.app.Activity
import android.app.Dialog
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.android.compose.theme.PlatformTheme
import java.util.concurrent.TimeUnit
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import platform.test.screenshot.BitmapDiffer
import platform.test.screenshot.DeviceEmulationRule
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.GoldenImagePathManager
import platform.test.screenshot.MaterialYouColorsRule
import platform.test.screenshot.ScreenshotActivity
import platform.test.screenshot.ScreenshotAsserterFactory
import platform.test.screenshot.ScreenshotTestRule
import platform.test.screenshot.UnitTestBitmapMatcher
import platform.test.screenshot.bitmapWithMaterialYouColorsSimulation
import platform.test.screenshot.captureToBitmapAsync
import platform.test.screenshot.dialogScreenshotTest

/** A rule for Compose screenshot diff tests. */
class ComposeScreenshotTestRule(
    private val emulationSpec: DeviceEmulationSpec,
    pathManager: GoldenImagePathManager,
    private val screenshotRule: ScreenshotTestRule = ScreenshotTestRule(pathManager)
) : TestRule, BitmapDiffer by screenshotRule, ScreenshotAsserterFactory by screenshotRule {
    private val colorsRule = MaterialYouColorsRule()
    private val deviceEmulationRule = DeviceEmulationRule(emulationSpec)
    val composeRule = createAndroidComposeRule<ScreenshotActivity>()
    private val isRobolectric = Build.FINGERPRINT.contains("robolectric")
    private val delegateRule =
        RuleChain.outerRule(colorsRule)
            .around(deviceEmulationRule)
            .around(screenshotRule)
            .around(composeRule)
    private val roboRule =
        RuleChain.outerRule(deviceEmulationRule).around(screenshotRule).around(composeRule)
    private val matcher = UnitTestBitmapMatcher

    override fun apply(base: Statement, description: Description): Statement {
        val ruleToApply = if (isRobolectric) roboRule else delegateRule
        return ruleToApply.apply(base, description)
    }

    /**
     * Compare [content] with the golden image identified by [goldenIdentifier] in the context of
     * [testSpec]. If [content] is `null`, we will take a screenshot of the current [composeRule]
     * content.
     */
    fun screenshotTest(
        goldenIdentifier: String,
        clearFocus: Boolean = false,
        beforeScreenshot: () -> Unit = {},
        viewFinder: () -> SemanticsNodeInteraction = { composeRule.onRoot() },
        content: (@Composable () -> Unit)? = null,
    ) {
        // Make sure that the activity draws full screen and fits the whole display instead of the
        // system bars.
        val activity = composeRule.activity
        activity.mainExecutor.execute { activity.window.setDecorFitsSystemWindows(false) }

        // Set the content using the AndroidComposeRule to make sure that the Activity is set up
        // correctly.
        if (content != null) {
            var focusManager: FocusManager? = null

            composeRule.setContent {
                val focusManager = LocalFocusManager.current.also { focusManager = it }

                PlatformTheme {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        content()

                        // Clear the focus early. This disposable effect will run after any
                        // DisposableEffect in content() but will run before layout/drawing, so
                        // clearing focus early here will make sure we never draw a focused effect.
                        if (clearFocus) {
                            DisposableEffect(Unit) {
                                focusManager.clearFocus()
                                onDispose {}
                            }
                        }
                    }
                }
            }
            beforeScreenshot()

            // Make sure focus is still cleared after everything settles.
            if (clearFocus) {
                focusManager!!.clearFocus()
            }
        }
        composeRule.waitForIdle()

        val view = (viewFinder().fetchSemanticsNode().root as ViewRootForTest).view
        val bitmap = view.captureToBitmapAsync().get(10, TimeUnit.SECONDS)
        val viewBitmap =
            if (isRobolectric) {
                bitmapWithMaterialYouColorsSimulation(
                    bitmap,
                    emulationSpec.isDarkTheme,
                    /* doPixelAveraging= */ false
                )
            } else {
                bitmap
            }
        screenshotRule.assertBitmapAgainstGolden(viewBitmap, goldenIdentifier, matcher)
    }

    fun dialogScreenshotTest(
        goldenIdentifier: String,
        dialogProvider: (Activity) -> Dialog,
    ) {
        dialogScreenshotTest(
            composeRule.activityRule,
            screenshotRule,
            matcher,
            goldenIdentifier,
            waitForIdle = { composeRule.waitForIdle() },
            dialogProvider,
        )
    }
}
