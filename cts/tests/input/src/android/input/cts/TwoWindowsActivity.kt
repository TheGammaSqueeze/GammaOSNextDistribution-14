/*
 * Copyright 2024 The Android Open Source Project
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

import android.app.Activity
import android.graphics.Color
import android.server.wm.CtsWindowInfoUtils.waitForWindowVisible
import android.view.Gravity
import android.view.InputDevice.SOURCE_STYLUS
import android.view.InputDevice.SOURCE_TOUCHSCREEN
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
import android.view.WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION
import java.util.concurrent.FutureTask
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNull
import org.junit.Assert.fail

private fun assertNoEvents(queue: LinkedBlockingQueue<InputEvent>) {
    val event = queue.poll(100, TimeUnit.MILLISECONDS)
    assertNull("Expected no events, but received $event", event)
}

class TwoWindowsActivity : Activity() {
    private val leftWindowEvents = LinkedBlockingQueue<InputEvent>()
    private val rightWindowEvents = LinkedBlockingQueue<InputEvent>()

    /**
     * Launch two windows and wait for them to be visible. Must not be called on UI thread.
     */
    fun launchTwoWindows() {
        lateinit var leftView: View
        lateinit var rightView: View
        val addWindowsTask = FutureTask<Unit> {
            val wm = getSystemService(WindowManager::class.java)
            var wmlp = WindowManager.LayoutParams(
                TYPE_APPLICATION,
                FLAG_NOT_TOUCH_MODAL or FLAG_SPLIT_TOUCH
            )

            val windowMetrics = windowManager.currentWindowMetrics
            val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars()
            )
            val width = windowMetrics.bounds.width() - insets.left - insets.right
            val height = windowMetrics.bounds.height() - insets.top - insets.bottom

            wmlp.setTitle("Left -- " + getPackageName())
            wmlp.width = width / 2
            wmlp.height = height
            wmlp.gravity = Gravity.TOP or Gravity.LEFT
            wmlp.setTitle(getPackageName())

            val vglp = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            leftView = View(this)
            leftView.setOnTouchListener { _: View, event: MotionEvent ->
                                            leftWindowEvents.add(MotionEvent.obtain(event)); true }
            leftView.setOnHoverListener { _, event ->
                                            leftWindowEvents.add(MotionEvent.obtain(event)); true }
            leftView.setBackgroundColor(Color.GREEN)
            leftView.setLayoutParams(vglp)
            wm.addView(leftView, wmlp)

            wmlp.setTitle("Right -- " + getPackageName())
            wmlp.gravity = Gravity.TOP or Gravity.RIGHT

            rightView = View(this)
            rightView.setBackgroundColor(Color.BLUE)
            rightView.setOnTouchListener{ _: View, event: MotionEvent ->
                                            rightWindowEvents.add(MotionEvent.obtain(event)); true }
            rightView.setOnHoverListener { _, event ->
                                            rightWindowEvents.add(MotionEvent.obtain(event)); true }
            rightView.setLayoutParams(vglp)

            wm.addView(rightView, wmlp)

            // Disable resampling, otherwise ACTION_MOVE events could contain unexpected coordinates
            leftView.requestUnbufferedDispatch(SOURCE_TOUCHSCREEN or SOURCE_STYLUS)
            rightView.requestUnbufferedDispatch(SOURCE_TOUCHSCREEN or SOURCE_STYLUS)
        }
        runOnUiThread(addWindowsTask)
        try {
            addWindowsTask.get(); // this will block until FutureTask completes on the main thread
        } catch (e: Exception) {
            fail("Interrupted while waiting to add windows")
        }

        waitForWindowVisible(leftView)
        waitForWindowVisible(rightView)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent?): Boolean {
        fail("Unexpected event " + ev)
        return true
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        fail("Unexpected event " + ev)
        return true
    }

    override fun dispatchKeyEvent(ev: KeyEvent?): Boolean {
        fail("Unexpected event " + ev)
        return true
    }

    override fun dispatchTrackballEvent(ev: MotionEvent?): Boolean {
        fail("Unexpected event " + ev)
        return true
    }

    fun getLeftWindowInputEvent(): InputEvent? {
        return leftWindowEvents.poll(5, TimeUnit.SECONDS)
    }

    fun getRightWindowInputEvent(): InputEvent? {
        return rightWindowEvents.poll(5, TimeUnit.SECONDS)
    }

    fun assertNoEvents() {
        assertNoEvents(leftWindowEvents)
        assertNoEvents(rightWindowEvents)
    }
}
