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
package android.input.cts

import android.view.KeyEvent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.compatibility.common.util.ShellUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals

/**
 * Tests for the common key combinations should be consumed by app first.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class AppKeyCombinationsTest {
    @get:Rule
    var activityRule = ActivityScenarioRule(CaptureEventActivity::class.java)
    private lateinit var activity: CaptureEventActivity
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() {
        activityRule.getScenario().onActivity {
            activity = it
        }
        PollingCheck.waitFor { activity.hasWindowFocus() }
        instrumentation.uiAutomation.syncInputTransactions()
    }

    @Test
    fun testCtrlSpace() {
        ShellUtils.runShellCommand("input keycombination CTRL_LEFT SPACE")
        assertKeyEvent(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.ACTION_DOWN, 0)
        assertKeyEvent(KeyEvent.KEYCODE_SPACE, KeyEvent.ACTION_DOWN, META_CTRL)
        assertKeyEvent(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.ACTION_UP, META_CTRL)
        assertKeyEvent(KeyEvent.KEYCODE_SPACE, KeyEvent.ACTION_UP, 0)
    }

    @Test
    fun testCtrlAltZ() {
        ShellUtils.runShellCommand("input keycombination CTRL_LEFT ALT_LEFT Z")
        assertKeyEvent(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.ACTION_DOWN, 0)
        assertKeyEvent(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.ACTION_DOWN, META_CTRL)
        assertKeyEvent(KeyEvent.KEYCODE_Z, KeyEvent.ACTION_DOWN, META_CTRL or META_ALT)
        assertKeyEvent(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.ACTION_UP, META_CTRL or META_ALT)
        assertKeyEvent(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.ACTION_UP, META_ALT)
        assertKeyEvent(KeyEvent.KEYCODE_Z, KeyEvent.ACTION_UP, 0)
    }

    @Test
    fun testSYSRQ() {
        ShellUtils.runShellCommand("input keyevent KEYCODE_SYSRQ")
        assertKeyEvent(KeyEvent.KEYCODE_SYSRQ, KeyEvent.ACTION_DOWN, 0)
        assertKeyEvent(KeyEvent.KEYCODE_SYSRQ, KeyEvent.ACTION_UP, 0)
    }

    private fun assertKeyEvent(keyCode: Int, action: Int, metaState: Int) {
        val event = activity.getInputEvent() as KeyEvent
        assertEquals(keyCode, event.keyCode)
        assertEquals(action, event.action)
        assertEquals(0, event.flags)
        assertEquals(metaState, event.metaState)
    }

    companion object {
        const val META_CTRL = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        const val META_ALT = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
    }
}
