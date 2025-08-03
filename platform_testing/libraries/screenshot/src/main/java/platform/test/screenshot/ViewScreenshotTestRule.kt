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

import android.app.Activity
import android.app.Dialog
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import platform.test.screenshot.matchers.BitmapMatcher

/** A rule for View screenshot diff unit tests. */
open class ViewScreenshotTestRule(
    private val emulationSpec: DeviceEmulationSpec,
    pathManager: GoldenImagePathManager,
    private val matcher: BitmapMatcher = UnitTestBitmapMatcher,
    private val decorFitsSystemWindows: Boolean = false,
    private val screenshotRule: ScreenshotTestRule = ScreenshotTestRule(pathManager)
) : TestRule, BitmapDiffer by screenshotRule, ScreenshotAsserterFactory by screenshotRule {
    private val colorsRule = MaterialYouColorsRule()
    private val timeZoneRule = TimeZoneRule()
    private val deviceEmulationRule = DeviceEmulationRule(emulationSpec)
    private val activityRule = ActivityScenarioRule(ScreenshotActivity::class.java)
    private val commonRule =
        RuleChain.outerRule(deviceEmulationRule).around(screenshotRule).around(activityRule)
    private val deviceRule = RuleChain.outerRule(colorsRule).around(commonRule)
    private val roboRule = RuleChain.outerRule(timeZoneRule).around(commonRule)
    private val isRobolectric = if (Build.FINGERPRINT.contains("robolectric")) true else false

    override fun apply(base: Statement, description: Description): Statement {
        val ruleToApply = if (isRobolectric) roboRule else deviceRule
        return ruleToApply.apply(base, description)
    }

    protected fun takeScreenshot(
        mode: Mode = Mode.WrapContent,
        viewProvider: (ComponentActivity) -> View,
        /**
         * Don't set it true unless you have to control MainLooper for the view creation.
         *
         * TODO(b/314985107) : remove when looper dependency is removed
         */
        inflateViewsInTestScope: Boolean = false,
        beforeScreenshot: (ComponentActivity) -> Unit = {}
    ): Bitmap {
        var inflatedView: View? = null

        if (inflateViewsInTestScope) {
            lateinit var componentActivity: ComponentActivity
            activityRule.scenario.onActivity { activity -> componentActivity = activity }
            inflatedView = viewProvider(componentActivity)
        }

        activityRule.scenario.onActivity { activity ->
            // Make sure that the activity draws full screen and fits the whole display instead of
            // the system bars.
            val window = activity.window
            window.setDecorFitsSystemWindows(decorFitsSystemWindows)

            // Set the content.
            if (inflateViewsInTestScope) {
                checkNotNull(inflatedView) {
                    "The inflated view shouldn't be null when inflateViewsInTestScope is enabled"
                }
            } else {
                inflatedView = viewProvider(activity)
            }
            activity.setContentView(inflatedView, mode.layoutParams)

            // Elevation/shadows is not deterministic when doing hardware rendering, so we disable
            // it for any view in the hierarchy.
            window.decorView.removeElevationRecursively()

            activity.currentFocus?.clearFocus()
        }

        // We call onActivity again because it will make sure that our Activity is done measuring,
        // laying out and drawing its content (that we set in the previous onActivity lambda).
        var contentView: View? = null
        activityRule.scenario.onActivity { activity ->
            // Check that the content is what we expected.
            val content = activity.requireViewById<ViewGroup>(android.R.id.content)
            assertEquals(1, content.childCount)
            contentView = content.getChildAt(0)
            beforeScreenshot(activity)
        }

        val originalBitmap = contentView?.captureToBitmapAsync()?.get(10, TimeUnit.SECONDS)
        if (originalBitmap == null) {
            error("timeout while trying to capture view to bitmap")
        }
        return if (isRobolectric) {
            bitmapWithMaterialYouColorsSimulation(
                originalBitmap,
                emulationSpec.isDarkTheme,
                /* doPixelAveraging= */ true
            )
        } else {
            originalBitmap
        }
    }

    /**
     * Compare the content of the view provided by [viewProvider] with the golden image identified
     * by [goldenIdentifier] in the context of [emulationSpec].
     */
    fun screenshotTest(
        goldenIdentifier: String,
        mode: Mode = Mode.WrapContent,
        /**
         * Don't set it true unless you have to control MainLooper for the view creation.
         *
         * TODO(b/314985107) : remove when looper dependency is removed
         */
        inflateViewInTestScope: Boolean = false,
        beforeScreenshot: (ComponentActivity) -> Unit = {},
        viewProvider: (ComponentActivity) -> View,
    ) {
        val bitmap = takeScreenshot(mode, viewProvider, inflateViewInTestScope, beforeScreenshot)
        screenshotRule.assertBitmapAgainstGolden(
            bitmap,
            goldenIdentifier,
            matcher,
        )
    }

    /**
     * Compare the content of the dialog provided by [dialogProvider] with the golden image
     * identified by [goldenIdentifier] in the context of [emulationSpec].
     */
    fun dialogScreenshotTest(
        goldenIdentifier: String,
        dialogProvider: (Activity) -> Dialog,
    ) {
        dialogScreenshotTest(
            activityRule,
            screenshotRule,
            matcher,
            goldenIdentifier,
            dialogProvider = dialogProvider,
        )
    }

    enum class Mode(val layoutParams: LayoutParams) {
        WrapContent(LayoutParams(WRAP_CONTENT, WRAP_CONTENT)),
        MatchSize(LayoutParams(MATCH_PARENT, MATCH_PARENT)),
        MatchWidth(LayoutParams(MATCH_PARENT, WRAP_CONTENT)),
        MatchHeight(LayoutParams(WRAP_CONTENT, MATCH_PARENT)),
    }
}
