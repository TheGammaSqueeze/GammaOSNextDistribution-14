/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.content.res.cts.config

import android.app.Activity
import android.app.Instrumentation
import android.content.cts.R
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.cts.config.activity.ApplyOverrideConfigActivity
import android.content.res.cts.config.activity.ApplyOverrideConfigHandleOrientationActivity
import android.content.res.cts.config.activity.CreateConfigBaseContextActivity
import android.content.res.cts.config.activity.CreateConfigInflaterContextActivity
import android.content.res.cts.config.activity.OverrideConfigBaseActivity
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.common.truth.Truth.assertThat
import kotlin.reflect.KClass
import org.junit.AfterClass
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

/**
 * Tests how [Configuration] overrides affect the value of resources in an [Activity].
 */
class OverrideConfigTest {

    companion object {
        private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
        private val context get() = instrumentation.context!!
        private val uiDevice = UiDevice.getInstance(instrumentation)!!

        private var isNaturalPortrait = true
        private var isRotationSupported = true

        @JvmStatic
        @BeforeClass
        fun setUpRotation() {
            val packageManager = context.packageManager
            isRotationSupported =
                packageManager.hasSystemFeature(PackageManager.FEATURE_SCREEN_PORTRAIT) &&
                packageManager.hasSystemFeature(PackageManager.FEATURE_SCREEN_LANDSCAPE) &&
                (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
                    != Configuration.UI_MODE_TYPE_VR_HEADSET)

            if (!isRotationSupported) {
                return
            }

            uiDevice.freezeRotation()
            uiDevice.setOrientationNatural()

            val resources = context.resources
            val orientation = resources.configuration.orientation
            val isPortraitByConfig = orientation == Configuration.ORIENTATION_PORTRAIT

            // In case the configuration uses ORIENTATION_UNDEFINED somehow
            val displayMetrics = resources.displayMetrics
            val isPortraitByDisplay = displayMetrics.heightPixels > displayMetrics.widthPixels

            isNaturalPortrait = isPortraitByConfig || isPortraitByDisplay

            // Start the test as portrait
            if (!isNaturalPortrait) {
                uiDevice.setOrientationLeft()
            }
        }

        @JvmStatic
        @AfterClass
        fun resetRotation() {
            if (isRotationSupported) {
                uiDevice.unfreezeRotation()
            }
        }
    }

    @Before
    fun assumeRotationSupported() {
        assumeTrue(isRotationSupported)
    }

    @Before
    fun setOrientationPortrait() {
        uiDevice.setOrientationNatural()
        if (!isNaturalPortrait) {
            uiDevice.setOrientationLeft()
        }
    }

    @Test
    fun applyOverrideConfig() {
        testActivity(ApplyOverrideConfigActivity::class)
    }

    @Test
    fun applyOverrideConfigHandlesOrientation() {
        var checkedNewConfig = false
        testActivity(
            ApplyOverrideConfigHandleOrientationActivity::class,
            handlesOrientationChange = true
        ) {
            if (it.configuration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // Verify that Configuration passed to onConfigurationChanged callback matches
                assertThat(it.newConfig).isEqualTo(it.configuration())
                checkedNewConfig = true
            }
        }

        // Ensure the callback Configuration was verified
        assertThat(checkedNewConfig).isTrue()
    }

    @Test
    fun createConfigContextBaseContext() {
        testActivity(CreateConfigBaseContextActivity::class)
    }

    @Test
    fun createConfigContextInflaterContext() {
        testActivity(
            CreateConfigInflaterContextActivity::class,
            // Because this class only applies the override to the LayoutInflater, the base
            // Resources value should not be overridden by the smallestWidth qualified value
            expectBaseResourcesOverride = false
        ) {
            // Assert that the Activity's base Resources aren't overridden,
            // since this class overrides the configuration for the LayoutInflater
            assertThat(it.resources.getString(R.string.smallestWidthTest)).isEqualTo("default")
        }
    }

    private fun <Activity : OverrideConfigBaseActivity> testActivity(
        kClass: KClass<Activity>,
        handlesOrientationChange: Boolean = false,
        expectBaseResourcesOverride: Boolean = true,
        additionalAssertion: (Activity) -> Unit = {},
    ) {
        ActivityScenario.launch(kClass.java).use {
            // Initial state should already have overridden the values, verify that first
            it.onActivity {
                assertThat(it.textOrientation.text).isEqualTo("default")
                assertThat(it.textSmallestWidth.text).isEqualTo("overridden 99999")
                assertThat(it.resources.getString(R.string.config_overridden_string))
                    .isEqualTo("default")

                assertThat(it.resources.getString(R.string.smallestWidthTest)).run {
                    if (expectBaseResourcesOverride) {
                        isEqualTo("overridden 99999")
                    } else {
                        isEqualTo("default")
                    }
                }

                assertThat(it.configuration().smallestScreenWidthDp)
                    .isEqualTo(OverrideConfigBaseActivity.OVERRIDE_SMALLEST_WIDTH)
                additionalAssertion(it)
            }

            // Then save the old values
            var oldConfig: Configuration? = null
            var oldOrientation: Int? = null
            it.onActivity {
                // Copy the Configuration in case something in the system edits the object
                val initialConfig = it.configuration()
                oldConfig = Configuration(initialConfig)
                assertThat(oldConfig).isEqualTo(initialConfig)
                oldOrientation = oldConfig!!.orientation
            }

            // Rotate the device so that a Resources configuration update is propagated
            if (isNaturalPortrait) {
                uiDevice.setOrientationLeft()
            } else {
                uiDevice.setOrientationNatural()
            }

            if (handlesOrientationChange) {
                // Can't call recreate if the Activity is expected to handle the change,
                // just sleep and assume the Activity has handled the change in 5 seconds.
                Thread.sleep(5000)
                it.moveToState(Lifecycle.State.RESUMED)
            } else {
                it.recreate()
            }

            // Verify that the values have changed to their landscape variants
            // and that the override still works
            it.onActivity {
                val newConfig = it.configuration()
                assertThat(newConfig).isNotEqualTo(oldConfig)
                assertThat(newConfig.orientation).isNotEqualTo(oldOrientation)

                if (handlesOrientationChange) {
                    // If the Activity wasn't recreated, then the view was not re-inflated and
                    // so the text value should be the same
                    assertThat(it.textOrientation.text).isEqualTo("default")
                } else {
                    assertThat(it.textOrientation.text).isEqualTo("landscape")
                }

                assertThat(it.textSmallestWidth.text).isEqualTo("overridden 99999")
                assertThat(it.resources.getString(R.string.config_overridden_string))
                    .isEqualTo("landscape")

                assertThat(it.resources.getString(R.string.smallestWidthTest)).run {
                    if (expectBaseResourcesOverride) {
                        isEqualTo("overridden 99999")
                    } else {
                        isEqualTo("default")
                    }
                }

                assertThat(newConfig.smallestScreenWidthDp)
                    .isEqualTo(OverrideConfigBaseActivity.OVERRIDE_SMALLEST_WIDTH)
                additionalAssertion(it)
            }
        }
    }
}
