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

package android.widget.cts

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.widget.TextView
import androidx.test.InstrumentationRegistry
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.android.compatibility.common.util.PollingCheck
import com.android.compatibility.common.util.ShellIdentityUtils
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test {@link TextView} under non-linear font scaling.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class TextViewFontScalingTest {
    lateinit var mInstrumentation: Instrumentation

    @get:Rule
    val scenarioRule = ActivityScenarioRule(TextViewFontScalingActivity::class.java)

    @Before
    fun setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation()
    }

    @After
    fun teardown() {
        restoreSystemFontScaleToDefault()
    }

    @Test
    @Throws(Throwable::class)
    fun testNonLinearFontScaling_testSetLineHeightSpAndSetTextSizeSp() {
        setSystemFontScale(2f)
        scenarioRule.scenario.onActivity { activity ->
            assertThat(activity.resources.configuration.fontScale).isWithin(0.02f).of(2f)

            val textView = TextView(activity)
            val textSizeSp = 20f
            val lineHeightSp = 40f

            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            textView.setLineHeight(TypedValue.COMPLEX_UNIT_SP, lineHeightSp)

            verifyLineHeightIsIntendedProportions(lineHeightSp, textSizeSp, activity, textView)
        }
    }

    @Test
    @Throws(Throwable::class)
    fun testNonLinearFontScaling_testSetLineHeightSpFirstAndSetTextSizeSpAfter() {
        setSystemFontScale(2f)
        scenarioRule.scenario.onActivity { activity ->
            assertThat(activity.resources.configuration.fontScale).isWithin(0.02f).of(2f)

            val textView = TextView(activity)
            val textSizeSp = 20f
            val lineHeightSp = 40f

            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            textView.setLineHeight(TypedValue.COMPLEX_UNIT_SP, lineHeightSp)

            val newTextSizeSp = 10f
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, newTextSizeSp)

            verifyLineHeightIsIntendedProportions(lineHeightSp, newTextSizeSp, activity, textView)
        }
    }

    @Test
    @Throws(Throwable::class)
    fun testNonLinearFontScaling_overwriteXml_testSetLineHeightSpAndSetTextSizeSp() {
        setSystemFontScale(2f)
        scenarioRule.scenario.onActivity { activity ->
            assertThat(activity.resources.configuration.fontScale).isWithin(0.02f).of(2f)

            val textView = findTextView(activity, R.id.textview_lineheight2x)
            val textSizeSp = 20f
            val lineHeightSp = 40f

            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            textView.setLineHeight(TypedValue.COMPLEX_UNIT_SP, lineHeightSp)

            verifyLineHeightIsIntendedProportions(lineHeightSp, textSizeSp, activity, textView)
        }
    }

    @Test
    @Throws(Throwable::class)
    fun testNonLinearFontScaling_xml_testLineHeightAttrSpAndTextSizeAttrSp() {
        setSystemFontScale(2f)
        scenarioRule.scenario.onActivity { activity ->
            assertThat(activity.resources.configuration.fontScale).isWithin(0.02f).of(2f)

            val textView = findTextView(activity, R.id.textview_lineheight2x)
            val textSizeSp = 20f
            val lineHeightSp = 40f

            verifyLineHeightIsIntendedProportions(lineHeightSp, textSizeSp, activity, textView)
        }
    }

    @Test
    @Throws(Throwable::class)
    fun testNonLinearFontScaling_overwriteXml_testLineHeightAttrSpAndSetTextSizeSpAfter() {
        setSystemFontScale(2f)
        scenarioRule.scenario.onActivity { activity ->
            assertThat(activity.resources.configuration.fontScale).isWithin(0.02f).of(2f)

            val textView = findTextView(activity, R.id.textview_lineheight2x)
            val newTextSizeSp = 10f
            val lineHeightSp = 40f

            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, newTextSizeSp)

            verifyLineHeightIsIntendedProportions(lineHeightSp, newTextSizeSp, activity, textView)
        }
    }

    @Test
    @Throws(Throwable::class)
    fun testNonLinearFontScaling_dimenXml_testLineHeightAttrSpAndTextSizeAttrSp() {
        setSystemFontScale(2f)
        scenarioRule.scenario.onActivity { activity ->
            assertThat(activity.resources.configuration.fontScale).isWithin(0.02f).of(2f)

            val textView = findTextView(activity, R.id.textview_lineheight_dimen3x)
            val textSizeSp = 20f
            val lineHeightSp = 60f

            verifyLineHeightIsIntendedProportions(lineHeightSp, textSizeSp, activity, textView)
        }
    }

    @Test
    @Throws(Throwable::class)
    fun testNonLinearFontScaling_styleXml_testLineHeightAttrSpAndTextSizeAttrSp() {
        setSystemFontScale(2f)
        scenarioRule.scenario.onActivity { activity ->
            assertThat(activity.resources.configuration.fontScale).isWithin(0.02f).of(2f)

            val textView = findTextView(activity, R.id.textview_lineheight_style3x)
            val textSizeSp = 20f
            val lineHeightSp = 60f

            verifyLineHeightIsIntendedProportions(lineHeightSp, textSizeSp, activity, textView)
        }
    }

    @Test
    @Throws(Throwable::class)
    fun testNonLinearFontScaling_dimenXml_testSetLineHeightSpAndTextSizeAttrSp() {
        setSystemFontScale(2f)
        scenarioRule.scenario.onActivity { activity ->
            assertThat(activity.resources.configuration.fontScale).isWithin(0.02f).of(2f)

            val textView = findTextView(activity, R.id.textview_lineheight_dimen3x)
            val textSizeSp = 20f
            val lineHeightSp = 30f

            textView.setLineHeight(TypedValue.COMPLEX_UNIT_SP, lineHeightSp)

            verifyLineHeightIsIntendedProportions(lineHeightSp, textSizeSp, activity, textView)
        }
    }

    private fun findTextView(activity: Activity, id: Int): TextView {
        return activity.findViewById(id)!!
    }

    private fun setSystemFontScale(fontScale: Float) {
        ShellIdentityUtils.invokeWithShellPermissions {
            Settings.System.putFloat(
                    mInstrumentation.context.contentResolver,
                    Settings.System.FONT_SCALE,
                    fontScale
            )
        }
        PollingCheck.waitFor(/* timeout= */ 5000) {
            val isActivityAtCorrectScale = AtomicBoolean(false)
            scenarioRule.scenario.onActivity { it ->
                isActivityAtCorrectScale.set(it.resources.configuration.fontScale == fontScale)
            }
            isActivityAtCorrectScale.get() && mInstrumentation
                    .context
                    .resources
                    .configuration.fontScale == fontScale
        }
    }

    private fun restoreSystemFontScaleToDefault() {
        ShellIdentityUtils.invokeWithShellPermissions {
            // TODO(b/279083734): would use Settings.System.resetToDefaults() if it existed
            Settings.System.putString(
                mInstrumentation.context.contentResolver,
                Settings.System.FONT_SCALE,
                null,
                /* overrideableByRestore= */ true
            )
        }
        PollingCheck.waitFor(/* timeout= */ 5000) {
            mInstrumentation
                .context
                .resources
                .configuration.fontScale == 1f
        }
    }

    class TextViewFontScalingActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.textview_fontscaling_layout)
        }
    }

    companion object {
        /**
         * Tolerance for comparing expected float lineHeight to the integer one returned by
         * getLineHeight(). It is pretty lenient to account for integer rounding when text size is
         * loaded from an attribute. (When loading an SP resource from an attribute for textSize,
         * it is rounded to the nearest pixel, which can throw off calculations quite a lot. Not
         * enough to make much of a difference to the user, but enough to need a wide tolerance in
         * tests. See b/279456702 for more details.)
         */
        const val TOLERANCE = 5f

        private fun verifyLineHeightIsIntendedProportions(
            lineHeightSp: Float,
            textSizeSp: Float,
            activity: Activity,
            textView: TextView
        ) {
            val lineHeightMultiplier = lineHeightSp / textSizeSp
            // Calculate what line height would be without non-linear font scaling compressing it.
            // The trick is multiplying afterwards (by the pixel value) instead of before (by the SP
            // value)
            val expectedLineHeightPx = lineHeightMultiplier * TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                textSizeSp,
                activity.resources.displayMetrics
            )
            assertThat(textView.lineHeight.toFloat())
                .isWithin(TOLERANCE)
                .of(expectedLineHeightPx)
        }
    }
}
