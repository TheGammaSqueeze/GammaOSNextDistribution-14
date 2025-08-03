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
package android.app.cts.wallpapers

import android.app.WallpaperColors
import android.os.Bundle
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.WindowInsets
import com.google.common.truth.Truth.assertWithMessage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Wrapper for WallpaperService.
 * This class does not add any logic or change any function.
 * It verifies that the public callback methods from [WallpaperService.Engine]
 * are called by the main thread.
 * The callback methods are the methods overridden by this class.
 *
 * It also includes a few checks to verify that the methods are called in a proper order.
 * For example, many methods should only be called after [WallpaperService.Engine.onSurfaceCreated],
 * which itself should only be called after [WallpaperService.Engine.onCreate].
 */
abstract class TestWallpaperService : WallpaperService() {

    private val mainThread: Thread = Looper.getMainLooper().thread
    companion object {
        private val TAG = TestWallpaperService::class.java.simpleName
        private const val DEBUG = true
        private var assertionError: AssertionError? = null
        private var prevAction: String? = null
        private var engineStateCallbacks = mutableSetOf<EngineCallback>()

        /**
         * Tracks the number of times [FakeEngine.onCreate] is called
         */
        var createCount: Int = 0

        /**
         * Tracks the number of times [FakeEngine.onDestroy] is called
         */
        var destroyCount: Int = 0

        fun resetCounts() {
            createCount = 0
            destroyCount = 0
        }

        /**
         * To be called at the end of tests requiring assertion checks from this class.
         * The first assertion error encountered by this class, if there is any,
         * will be raised when calling this function.
         * We use this to avoid raising errors directly in the callback methods, since errors in
         * callback methods could be raised from the main thread and crash the entire test process.
         */
        fun checkAssertions() {
            assertionError?.let { throw assertionError!! }
            assertionError = null
        }

        /**
         * Allow user to peek the previous handle command
         */
        fun getPrevAction(): String? {
          return prevAction
        }

        fun resetPrevAction() {
            prevAction = null
        }

        fun addCallback(callback: EngineCallback) {
            engineStateCallbacks.add(callback)
        }

        fun removeCallback(callback: EngineCallback) {
            engineStateCallbacks.remove(callback)
        }
    }

    override fun onCreateEngine(): Engine {
        if (DEBUG) Log.d(TAG, "onCreateEngine")
        assertMainThread()
        return FakeEngine()
    }

    internal inner class FakeEngine : Engine() {
        private var mCreated = false
        private var mSurfaceCreated = false

        private fun draw(holder: SurfaceHolder) {
            val c = holder.lockCanvas()
            c.drawColor(getColor())
            holder.unlockCanvasAndPost(c)
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean, animationDuration: Long) {
            if (DEBUG) Log.d(TAG, "onAmbientModeChanged")
            assertMainThread()
            super.onAmbientModeChanged(inAmbientMode, animationDuration)
        }

        override fun onApplyWindowInsets(insets: WindowInsets) {
            if (DEBUG) Log.d(TAG, "onApplyWindowInsets")
            assertMainThread()
            super.onApplyWindowInsets(insets)
        }

        override fun onCommand(
            action: String,
            x: Int,
            y: Int,
            z: Int,
            extras: Bundle?,
            resultRequested: Boolean
        ): Bundle? {
            if (DEBUG) Log.d(TAG, "onCommand")
            assertMainThread()
            prevAction = action
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        override fun onComputeColors(): WallpaperColors? {
            if (DEBUG) Log.d(TAG, "onComputeColors")
            assertMainThread()
            return super.onComputeColors()
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            if (DEBUG) Log.d(TAG, "onCreate")
            assertMainThread()
            assertNotCreated()
            assertSurfaceNotCreated()
            mCreated = true
            createCount++
            super.onCreate(surfaceHolder)
            engineStateCallbacks.forEach { it.onEvent(EngineCallback.Event.CREATE) }
        }

        override fun onDesiredSizeChanged(desiredWidth: Int, desiredHeight: Int) {
            if (DEBUG) Log.d(TAG, "onDesiredSizeChanged")
            assertMainThread()
            assertCreated()
            assertSurfaceCreated()
            super.onDesiredSizeChanged(desiredWidth, desiredHeight)
        }

        override fun onDestroy() {
            if (DEBUG) Log.d(TAG, "onDestroy, new count=" + (destroyCount + 1))
            assertMainThread()
            assertCreated()
            mCreated = false
            destroyCount++
            super.onDestroy()
            engineStateCallbacks.forEach { it.onEvent(EngineCallback.Event.DESTROY) }
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int
        ) {
            if (DEBUG) Log.d(TAG, "onOffsetsChanged")
            assertMainThread()
            super.onOffsetsChanged(
                xOffset,
                yOffset,
                xOffsetStep,
                yOffsetStep,
                xPixelOffset,
                yPixelOffset
            )
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            if (DEBUG) Log.d(TAG, "onSurfaceChanged")
            assertMainThread()
            assertCreated()
            assertSurfaceCreated()
            super.onSurfaceChanged(holder, format, width, height)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            if (DEBUG) Log.d(TAG, "onSurfaceCreated")
            assertMainThread()
            assertCreated()
            assertSurfaceNotCreated()
            mSurfaceCreated = true
            super.onSurfaceCreated(holder)
            engineStateCallbacks.forEach { it.onEvent(EngineCallback.Event.SURFACE_CREATE) }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            if (DEBUG) Log.d(TAG, "onSurfaceDestroyed")
            assertMainThread()
            assertCreated()
            assertSurfaceCreated()
            super.onSurfaceDestroyed(holder)
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            if (DEBUG) Log.d(TAG, "onSurfaceRedrawNeeded")
            draw(holder)
            assertMainThread()
            assertCreated()
            assertSurfaceCreated()
            super.onSurfaceRedrawNeeded(holder)
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (DEBUG) Log.d(TAG, "onTouchEvent")
            assertMainThread()
            super.onTouchEvent(event)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (DEBUG) Log.d(TAG, "onVisibilityChanged")
            assertMainThread()
            super.onVisibilityChanged(visible)
        }

        override fun onZoomChanged(zoom: Float) {
            if (DEBUG) Log.d(TAG, "onZoomChanged")
            assertMainThread()
            super.onZoomChanged(zoom)
        }

        private fun assertCreated() {
            assertHelper {
                assertWithMessage(
                    "Engine must be created (with onCreate) " +
                    "and not destroyed before calling this function"
                )
                    .that(mCreated).isTrue()
            }
        }

        private fun assertSurfaceCreated() {
            assertHelper {
                assertWithMessage(
                    "Surface must be created (with onSurfaceCreated) " +
                    "and not destroyed before calling this function"
                )
                    .that(mSurfaceCreated).isTrue()
            }
        }

        private fun assertNotCreated() {
            assertHelper {
                assertWithMessage(
                    "Engine must not be created (with onCreate) " +
                    "or must be destroyed before calling this function"
                )
                    .that(mCreated).isFalse()
            }
        }

        private fun assertSurfaceNotCreated() {
            assertHelper {
                assertWithMessage(
                    "Surface must not be created (with onSurfaceCreated) " +
                    "or must be destroyed before calling this function"
                )
                    .that(mSurfaceCreated).isFalse()
            }
        }
    }

    /**
     * Check that the current thread is the main thread
     */
    private fun assertMainThread() {
        assertHelper {
            val callerThread = Thread.currentThread()
            assertWithMessage(
                "Callback methods from WallpaperService.Engine " +
                    "must be called by the main thread; but was called by " + callerThread
            )
                .that(callerThread).isSameInstanceAs(mainThread)
        }
    }

    /**
     * Run an executable that performs some assertions. If any assertion is raised, and it is the
     * first one raised so far, store it.
     */
    private fun assertHelper(check: Runnable) {
        try {
            check.run()
        } catch (error: AssertionError) {
            assertionError = assertionError ?: error
        }
    }

    /**
     * The color that this test wallpaper should draw, for debug purposes.
     */
    protected abstract fun getColor(): Int

    interface EngineCallback {
        enum class Event { CREATE, DESTROY, SURFACE_CREATE }

        fun onEvent(e: Event)
    }

    class EngineCallbackCountdown(create: Int, destroy: Int, surfaceCreate: Int) : EngineCallback {
        private val createLatch = CountDownLatch(create)
        private val destroyLatch = CountDownLatch(destroy)
        private val surfaceCreateLatch = CountDownLatch(surfaceCreate)

        override fun onEvent(e: EngineCallback.Event) {
            when (e) {
                EngineCallback.Event.CREATE -> createLatch.countDown()
                EngineCallback.Event.DESTROY -> destroyLatch.countDown()
                EngineCallback.Event.SURFACE_CREATE -> surfaceCreateLatch.countDown()
            }
        }

        fun awaitEvents(time: Long, units: TimeUnit): Boolean {
            return (createLatch.await(time, units) &&
                    destroyLatch.await(time, units) &&
                    surfaceCreateLatch.await(time, units))
        }
    }
}
