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
package com.android.test.inputinjection

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import java.util.concurrent.Executors

/**
 * This app is used for testing the input injection. It's used in 2 ways:
 * 1) This app tries to use Instrumentation APIs to inject various events. All of these injection
 *    attempts should fail because it does not have INJECT_EVENTS permission. The results of the
 *    injection are reported by this app via the IInputInjectionTestCallbacks interface.
 * 2) The test code tries to inject events into this app. Any keys or motions received by
 *    this app are reported back to the test via the IInputInjectionTestCallbacks interface.
 */
class InputInjectionActivity : Activity() {

    companion object {
        const val INTENT_ACTION_TEST_INJECTION =
            "com.android.test.inputinjection.action.TEST_INJECTION"
        const val INTENT_EXTRA_CALLBACK = "com.android.test.inputinjection.extra.CALLBACK"

        // We don't need to send matching "UP" events for the injected keys and motions because
        // under normal circumstances, these injections would fail so the gesture would never
        // actually start.
        val injectionMethods = listOf<Pair<String, (View) -> Unit>>(
            Pair("sendPointerSync", { view ->
                Instrumentation().sendPointerSync(getMotionDownInView(view))
            }),
            Pair("sendTrackballEventSync", { view ->
                Instrumentation().sendTrackballEventSync(getMotionDownInView(view))
            }),
            Pair("sendKeySync", {
                Instrumentation().sendKeySync(getKeyDown())
            }),
            Pair("sendKeyUpDownSync", {
                Instrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_A)
            }),
            Pair("sendCharacterSync", {
                Instrumentation().sendCharacterSync(KeyEvent.KEYCODE_A)
            }),
            Pair("sendStringSync", {
                Instrumentation().sendStringSync("Hello World!")
            })
        )
    }

    // The binder callbacks that report results back to the test process
    private lateinit var callbacks: IInputInjectionTestCallbacks

    private lateinit var view: View

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_input_injection)

        callbacks = IInputInjectionTestCallbacks.Stub.asInterface(
            intent.extras?.getBinder(INTENT_EXTRA_CALLBACK)
        ) ?: throw IllegalStateException("InputInjectionActivity started without binder callback")

        view = findViewById(R.id.view)!!
    }

    override fun onNewIntent(intent: Intent?) {
        if (intent!!.action == INTENT_ACTION_TEST_INJECTION) {
            Executors.newSingleThreadExecutor().execute(this::testInputInjectionFromApp)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            callbacks.onWindowFocused()
        }
    }

    /**
     * Attempt to inject input events from this application into the system, and report the result
     * to the test process through the [callbacks]. Since this method synchronously injects events,
     * it must not be called from the main thread.
     */
    private fun testInputInjectionFromApp() {
        val errors = mutableListOf<String>()
        for ((name, inject) in injectionMethods) {
            try {
                inject(view)
                errors.add(
                    "Call to $name succeeded without throwing an exception " +
                            "from an app that does not have INJECT_EVENTS permission."
                )
            } catch (e: RuntimeException) {
                // We expect a security exception to be thrown because this app does not have
                // the INJECT_EVENTS permission.
            }
        }
        callbacks.onTestInjectionFromApp(errors)
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        callbacks.onKeyEvent(event)
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        callbacks.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }
}

private fun getMotionDownInView(view: View): MotionEvent {
    val now = SystemClock.uptimeMillis()
    val (x, y) = view.getCenterOnScreen()
    // Use the default source and allow the injection methods to configure it if needed.
    return MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
}

private fun getKeyDown(): KeyEvent {
    val now = SystemClock.uptimeMillis()
    return KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0 /*repeat*/)
}

private fun View.getCenterOnScreen(): Pair<Float, Float> {
    val location = IntArray(2).also { getLocationOnScreen(it) }
    return location[0].toFloat() + width / 2f to location[1].toFloat() + height / 2f
}
