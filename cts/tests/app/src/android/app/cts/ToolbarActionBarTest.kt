/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.app.cts

import android.app.stubs.R
import android.app.stubs.ToolbarActivity
import android.view.KeyEvent
import android.view.Window
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

private fun waitForKey(activity: ToolbarActivity, keyCode: Int, action: Int) {
    val event = activity.getInputEvent()
    assertNotNull(event)
    assertTrue(event is KeyEvent)
    val key = event as KeyEvent
    assertEquals(action, key.action)
    assertEquals(keyCode, key.keyCode)
}

private fun waitForKeyDownUp(activity: ToolbarActivity, keyCode: Int) {
    waitForKey(activity, keyCode, KeyEvent.ACTION_DOWN)
    waitForKey(activity, keyCode, KeyEvent.ACTION_UP)
}

public class ToolbarActionBarTest {
    val TAG = "ToolbarAction"
    @get:Rule
    val activityRule = ActivityScenarioRule(ToolbarActivity::class.java)
    private lateinit var activity: ToolbarActivity
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() {
        activityRule.getScenario().onActivity {
            activity = it
            activity.getToolbar().inflateMenu(R.menu.flat_menu)
        }

        PollingCheck.waitFor { activity.hasWindowFocus() }
    }

    @Test
    fun testOptionsMenuKey() {
        assumeTrue(activity.getWindow().hasFeature(Window.FEATURE_OPTIONS_PANEL))
        val menuIsVisible = AtomicBoolean(false)
        activity.actionBar!!.addOnMenuVisibilityListener {
            isVisible -> menuIsVisible.set(isVisible)
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU)
        waitForKeyDownUp(activity, KeyEvent.KEYCODE_MENU)
        PollingCheck.waitFor { menuIsVisible.get() }
        PollingCheck.waitFor { activity.getToolbar().isOverflowMenuShowing() }

        // Inject KEYCODE_MENU for the second time, to hide the action bar.
        // The key will now go to the PopupWindow instead of the activity.
        // There's no simple way to wait for the PopupWindow to get focus, but we already wait for
        // the overflow menu to be visible above, and we can also wait for the activity to lose
        // focus.
        PollingCheck.waitFor { !activity.getToolbar().hasWindowFocus() }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU)
        // We can't wait for the key in the activity, because it will now go to the PopupWindow
        PollingCheck.waitFor { !menuIsVisible.get() }
        PollingCheck.waitFor { !activity.getToolbar().isOverflowMenuShowing() }
    }

    @Test
    fun testOpenOptionsMenu() {
        assumeTrue(activity.getWindow().hasFeature(Window.FEATURE_OPTIONS_PANEL))
        val menuIsVisible = AtomicBoolean(false)
        activity.actionBar!!.addOnMenuVisibilityListener {
            isVisible -> menuIsVisible.set(isVisible)
        }
        activityRule.getScenario().onActivity {
            it.openOptionsMenu()
        }
        PollingCheck.waitFor { menuIsVisible.get() }
        PollingCheck.waitFor { activity.getToolbar().isOverflowMenuShowing() }
        activityRule.getScenario().onActivity {
            it.closeOptionsMenu()
        }
        PollingCheck.waitFor { !menuIsVisible.get() }
        PollingCheck.waitFor { !activity.getToolbar().isOverflowMenuShowing() }
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun pressHome() {
            // Press HOME key so that any UI that is showing, such as notification shade,
            // will be hidden.
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_HOME)
        }
    }
}
