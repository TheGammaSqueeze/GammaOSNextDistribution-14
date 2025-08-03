/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *            http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.input.cts

import android.Manifest.permission.OVERRIDE_SYSTEM_KEY_BEHAVIOR_IN_FOCUSED_WINDOW
import android.content.pm.PackageManager
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.view.KeyEvent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.compatibility.common.util.ShellUtils
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.WindowUtil
import com.android.cts.input.inputeventmatchers.withKeyCode
import com.android.input.flags.Flags
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests to confirm that apps with {@link OVERRIDE_SYSTEM_KEY_BEHAVIOR_IN_FOCUSED_WINDOW} permission
 * can override the default system key behavior.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class OverrideSystemKeyBehaviorTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule val activityScenarioRule =
            ActivityScenarioRule<CaptureEventActivity>(CaptureEventActivity::class.java)
    @get:Rule val checkFlagRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private lateinit var activity: CaptureEventActivity
    private lateinit var verifier: EventVerifier

    @Before
    fun setUp() {
        activityScenarioRule.getScenario().onActivity {
            activity = it
            verifier = EventVerifier(activity::getInputEvent)
        }
        PollingCheck.waitFor { activity.hasWindowFocus() }
    }

    /**
     * Ensure that apps without {@link OVERRIDE_SYSTEM_KEY_BEHAVIOR_IN_FOCUSED_WINDOW} permission
     * doesn't receive {@link KEYCODE_STEM_PRIMARY} key events. And the default stem primary key
     * behavior will be triggered which will launch a new activity and causes the test activity to
     * lose focus.
     */
    @Test
    fun testStemPrimaryKeyNotDeliveredToAppWithoutPermission() {
        assumeTrue("This test requires a watch", isWatch())

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_STEM_PRIMARY)
        PollingCheck.waitFor(WindowUtil.WINDOW_FOCUS_TIMEOUT_MILLIS) { !activity.hasWindowFocus() }

        assertFalse(activity.hasWindowFocus())
        activity.assertNoEvents()
    }

    /**
     * Ensure that apps with {@link OVERRIDE_SYSTEM_KEY_BEHAVIOR_IN_FOCUSED_WINDOW} permission
     * receives {@link KEYCODE_STEM_PRIMARY} key events. And when the app doesn't handle the key
     * event, the default stem primary key behavior will be triggered which will launch a new
     * activity and causes the test activity to lose focus.
     */
    @RequiresFlagsEnabled(Flags.FLAG_OVERRIDE_KEY_BEHAVIOR_PERMISSION_APIS)
    @Test
    fun testStemPrimaryKeyUnhandledByApp() {
        assumeTrue("This test requires a watch", isWatch())
        activity.shouldHandleKeyEvents = false

        SystemUtil.runWithShellPermissionIdentity ({
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_STEM_PRIMARY)
            verifier.assertReceivedKey(withKeyCode(KeyEvent.KEYCODE_STEM_PRIMARY))
        }, OVERRIDE_SYSTEM_KEY_BEHAVIOR_IN_FOCUSED_WINDOW)

        // The activity should lose focus due to the default KEYCODE_STEM_PRIMARY action.
        PollingCheck.waitFor(WindowUtil.WINDOW_FOCUS_TIMEOUT_MILLIS) { !activity.hasWindowFocus() }
    }

    /**
     * Ensure that apps with {@link OVERRIDE_SYSTEM_KEY_BEHAVIOR_IN_FOCUSED_WINDOW} permission
     * receives {@link KEYCODE_STEM_PRIMARY} key events. And when the app handles the key
     * event, the default stem primary key behavior will be skipped and the app will remain focused.
     */
    @RequiresFlagsEnabled(Flags.FLAG_OVERRIDE_KEY_BEHAVIOR_PERMISSION_APIS)
    @Test
    fun testStemPrimaryKeyHandledByApp() {
        assumeTrue("This test requires a watch", isWatch())
        activity.shouldHandleKeyEvents = true

        SystemUtil.runWithShellPermissionIdentity ({
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_STEM_PRIMARY)
            verifier.assertReceivedKey(withKeyCode(KeyEvent.KEYCODE_STEM_PRIMARY))
        }, OVERRIDE_SYSTEM_KEY_BEHAVIOR_IN_FOCUSED_WINDOW)

        // The activity should continue to have focus because the system default
        // KEYCODE_STEM_PRIMARY action is overridden.
        assertTrue(activity.hasWindowFocus())
    }

    private fun isWatch(): Boolean {
        return activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)
    }
}
