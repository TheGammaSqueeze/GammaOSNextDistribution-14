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
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.test.ext.junit.rules.ActivityScenarioRule
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import platform.test.screenshot.ViewScreenshotTestRule.Mode
import platform.test.screenshot.matchers.BitmapMatcher

/** A rule for View screenshot diff unit tests. */
open class FragmentScreenshotTestRule(
    private val emulationSpec: DeviceEmulationSpec,
    pathManager: GoldenImagePathManager,
    private val matcher: BitmapMatcher = UnitTestBitmapMatcher,
    private val decorFitsSystemWindows: Boolean = false,
    private val screenshotRule: ScreenshotTestRule = ScreenshotTestRule(pathManager)
) : TestRule, BitmapDiffer by screenshotRule, ScreenshotAsserterFactory by screenshotRule {
    private val colorsRule = MaterialYouColorsRule()
    private val timeZoneRule = TimeZoneRule()
    private val deviceEmulationRule = DeviceEmulationRule(emulationSpec)
    private val activityRule = ActivityScenarioRule(FragmentScreenshotActivity::class.java)
    private val commonRule =
        RuleChain.outerRule(deviceEmulationRule).around(screenshotRule).around(activityRule)
    private val deviceRule = RuleChain.outerRule(colorsRule).around(commonRule)
    private val roboRule = RuleChain.outerRule(timeZoneRule).around(commonRule)
    private val isRobolectric = if (Build.FINGERPRINT.contains("robolectric")) true else false

    override fun apply(base: Statement, description: Description): Statement {
        val ruleToApply = if (isRobolectric) roboRule else deviceRule
        return ruleToApply.apply(base, description)
    }

    protected fun takeScreenshotFragment(
        mode: Mode = Mode.WrapContent,
        /**
         * Don't set it true unless you have to control MainLooper for the view creation.
         *
         * TODO(b/314985107) : remove when looper dependency is removed
         */
        beforeScreenshot: (AppCompatActivity) -> Unit = {},
        fragment: Fragment,
    ): Bitmap {
        lateinit var appCompatActivity: AppCompatActivity
        activityRule.scenario.onActivity { activity ->
            appCompatActivity = activity
            activity.supportFragmentManager
                .beginTransaction()
                .add(android.R.id.content, fragment)
                .commit()
        }

        activityRule.scenario.onActivity { activity ->
            // Make sure that the activity draws full screen and fits the whole display instead of
            // the system bars.
            val window = activity.window
            window.setDecorFitsSystemWindows(decorFitsSystemWindows)

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
            activity.setContentView(contentView, mode.layoutParams)
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
     * Compare the content of the view provided by [fragment] with the golden image identified by
     * [goldenIdentifier] in the context of [emulationSpec].
     */
    fun screenshotTest(
        goldenIdentifier: String,
        mode: Mode = Mode.WrapContent,
        fragment: Fragment,
        /**
         * Don't set it true unless you have to control MainLooper for the view creation.
         *
         * TODO(b/314985107) : remove when looper dependency is removed
         */
        beforeScreenshot: (AppCompatActivity) -> Unit = {},
    ) {
        val bitmap = takeScreenshotFragment(mode, beforeScreenshot, fragment)
        assertBitmapAgainstGolden(
            bitmap,
            goldenIdentifier,
            matcher,
        )
    }
}
