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

package android.input.cts

import android.app.ActivityManager
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.server.wm.WindowManagerState
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.FlakyTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil
import com.android.test.inputinjection.IInputInjectionTestCallbacks
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test case for injecting input through various means.
 *
 * We verify that injection succeeds only in the following two cases:
 * 1. The calling uid must have the [android.Manifest.permission.INJECT_EVENTS]; OR
 * 2. The caller must be instrumented by an uid that has the same permission (e.g. tests that
 *    don't have the permission that are instrumented by the Shell).
 */
@RunWith(AndroidJUnit4::class)
class InputInjectionTest {

    companion object {
        const val INPUT_INJECTION_PACKAGE = "com.android.test.inputinjection"
        const val INPUT_INJECTION_ACTIVITY =
            "$INPUT_INJECTION_PACKAGE.InputInjectionActivity"
        val INPUT_INJECTION_COMPONENT =
            ComponentName(INPUT_INJECTION_PACKAGE, INPUT_INJECTION_ACTIVITY)
        const val INTENT_ACTION_TEST_INJECTION =
            "com.android.test.inputinjection.action.TEST_INJECTION"
        const val INTENT_EXTRA_CALLBACK = "com.android.test.inputinjection.extra.CALLBACK"

        val expectNoEvent =
            { e: InputEvent -> fail("Expected no input events, but got: $e") }
        val expectNoInjectionResult =
            { _: Any -> fail("No injection result expected") }
    }

    private lateinit var instrumentation: Instrumentation
    private lateinit var targetContext: Context
    private lateinit var activityManager: ActivityManager
    private lateinit var windowFocusLatch: CountDownLatch

    @Before
    fun setUp() {
        instrumentation = InstrumentationRegistry.getInstrumentation()!!
        targetContext = instrumentation.targetContext
        activityManager = instrumentation.context
            .getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        windowFocusLatch = CountDownLatch(1)
    }

    /**
     * Verify that any non-instrumented app that does not have
     * [android.Manifest.permission.INJECT_EVENTS] cannot inject events into the system.
     *
     * This test delivers [INTENT_ACTION_TEST_INJECTION] to the test app, which will attempt to
     * inject input events, and report back to this test through the callbacks. The app should
     * not be able to perform event injection successfully.
     */
    @Test
    fun testCannotInjectInputFromApplications() {
        val injectionTestResultLatch = CountDownLatch(1)
        val callbacks = withCallbacks(onTestResult = { errors: List<String?> ->
                if (errors.isNotEmpty()) {
                    fail(errors.joinToString("\n"))
                }
                injectionTestResultLatch.countDown()
            })
        startInjectionActivitySync(callbacks).use {
            targetContext.startActivity(Intent().apply {
                `package` = INPUT_INJECTION_PACKAGE
                action = INTENT_ACTION_TEST_INJECTION
                // The NEW_TASK flag is required for starting exported activities from other
                // processes. However, since it is a "singleInstance" activity, a new intent will
                // be delivered to the running instance.
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            assertTrue(injectionTestResultLatch.await(10, TimeUnit.SECONDS),
                "Did not receive callback for test completion")
        }
    }

    /**
     * When injecting pointer events through [Instrumentation.sendPointerSync], the pointer event
     * should only be injected successfully if it is directed at a window owned by same uid as the
     * instrumentation. This means tests cannot inject pointer events into other foreground windows
     * that are not being instrumented.
     */
    @FlakyTest(bugId = 293575644)
    @Test
    fun testCannotInjectPointerEventsFromInstrumentationToUnownedApp() {
        startInjectionActivitySync(withCallbacks()).use {
            clickInCenterOfInjectionActivity { eventToInject ->
                // The Instrumentation class should not be allowed to inject the start of a new
                // gesture to a window owned by another uid. However, it should be allowed to inject
                // the end of the gesture to ensure consistency.
                try {
                    instrumentation.sendPointerSync(eventToInject)
                    if (eventToInject.actionMasked == MotionEvent.ACTION_DOWN) {
                        fail("Instrumentation cannot inject DOWN to windows owned by another uid")
                    }
                } catch (e: RuntimeException) {
                    if (eventToInject.actionMasked == MotionEvent.ACTION_UP) {
                        fail("Instrumentation must be allowed to inject UP to ensure consistency")
                    }
                }
            }
        }
    }

    /**
     * Instrumented tests can inject key events synchronously into any focused window through
     * [Instrumentation].
     */
    @Test
    fun testInjectKeyEventsFromInstrumentation() {
        val keyPressLatch = CountDownLatch(2)
        startInjectionActivitySync(
                withCallbacks(onKey = { keyPressLatch.countDown() })).use {
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_A)
            assertEquals(0, keyPressLatch.count,
                "Instrumentation should synchronously send key events to the activity")
        }
    }

    /**
     * Instrumented tests can inject pointer events synchronously into any focused window through
     * [android.app.UiAutomation].
     */
    @Test
    fun testInjectPointerEventsFromUiAutomation() {
        val clickLatch = CountDownLatch(2)
        startInjectionActivitySync(
                withCallbacks(onTouch = { clickLatch.countDown() })).use {
            clickInCenterOfInjectionActivity { eventToInject ->
                instrumentation.uiAutomation.injectInputEvent(eventToInject, true /*sync*/)
            }
            assertEquals(0, clickLatch.count,
                "UiAutomation should synchronously send pointer events to the activity")
        }
    }

    /**
     * Instrumented tests can inject key events synchronously into any focused window through
     * [android.app.UiAutomation].
     */
    @Test
    fun testInjectKeyEventsFromUiAutomation() {
        val keyPressLatch = CountDownLatch(2)
        startInjectionActivitySync(
                withCallbacks(onKey = { keyPressLatch.countDown() })).use {
            val downTime = SystemClock.uptimeMillis()
            instrumentation.uiAutomation.injectInputEvent(
                KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0),
                true /*sync*/)
            val upTime = SystemClock.uptimeMillis()
            instrumentation.uiAutomation.injectInputEvent(
                KeyEvent(downTime, upTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A, 0),
                true /*sync*/)
            assertEquals(0, keyPressLatch.count,
                "UiAutomation should synchronously send key events to the activity")
        }
    }

    /** Synchronously starts the test activity and waits for it to gain window focus. */
    private fun startInjectionActivitySync(callback: IInputInjectionTestCallbacks): AutoCloseable {
        targetContext.startActivity(Intent().apply {
            component = INPUT_INJECTION_COMPONENT
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtras(Bundle().also {
                it.putBinder(INTENT_EXTRA_CALLBACK, callback.asBinder())
            })
        })

        assertTrue(windowFocusLatch.await(5, TimeUnit.SECONDS),
                "Timed out waiting for $INPUT_INJECTION_COMPONENT window to be focused")

        return AutoCloseable {
            SystemUtil.runWithShellPermissionIdentity {
                activityManager.forceStopPackage(INPUT_INJECTION_PACKAGE)
            }
        }
    }

    private fun clickInCenterOfInjectionActivity(injector: (MotionEvent) -> Unit) {
        val bounds = getActivityBounds(INPUT_INJECTION_COMPONENT)
        val downTime = SystemClock.uptimeMillis()
        injector(
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN,
                bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0))
        val upTime = SystemClock.uptimeMillis()
        injector(
            MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP,
                bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0))
    }

    private fun withCallbacks(
            onKey: (KeyEvent) -> Unit = InputInjectionTest.expectNoEvent,
            onTouch: (MotionEvent) -> Unit = InputInjectionTest.expectNoEvent,
            onTestResult: (List<String?>) -> Unit = InputInjectionTest.expectNoInjectionResult
    ): IInputInjectionTestCallbacks {
        return object : IInputInjectionTestCallbacks.Stub() {
            override fun onKeyEvent(ev: KeyEvent?) = onKey(ev!!)
            override fun onTouchEvent(ev: MotionEvent?) = onTouch(ev!!)
            override fun onTestInjectionFromApp(errors: List<String?>) = onTestResult(errors)
            override fun onWindowFocused() {
                windowFocusLatch.countDown()
            }
        }
    }
}

private fun getActivityBounds(component: ComponentName): Rect {
    val wms = WindowManagerState().apply { computeState() }
    val activity = wms.getActivity(component) ?: fail("Failed to get activity for $component")
    return activity.bounds ?: fail("Failed to get bounds for activity $component")
}
