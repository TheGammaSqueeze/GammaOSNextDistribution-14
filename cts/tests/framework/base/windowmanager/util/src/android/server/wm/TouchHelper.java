/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.server.wm;

import static android.server.wm.StateLogger.log;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

public class TouchHelper {
    public final Context mContext;
    public final Instrumentation mInstrumentation;
    public final WindowManagerStateHelper mWmState;

    public TouchHelper(Instrumentation instrumentation, WindowManagerStateHelper wmState) {
        mInstrumentation = instrumentation;
        mContext = mInstrumentation.getContext();
        mWmState = wmState;
    }

    /**
     * Insert an input event (ACTION_DOWN -> ACTION_CANCEL) to ensures the display to be focused
     * without triggering potential clicked to impact the test environment.
     * (e.g: Keyguard credential activated unexpectedly.)
     *
     * @param displayId the display ID to gain focused by inject swipe action
     */
    public void touchAndCancelOnDisplayCenterSync(int displayId) {
        WindowManagerState.DisplayContent dc = mWmState.getDisplay(displayId);
        if (dc == null) {
            // never get wm state before?
            mWmState.computeState();
            dc = mWmState.getDisplay(displayId);
        }
        if (dc == null) {
            log("Cannot tap on display: " + displayId);
            return;
        }
        final Rect bounds = dc.getDisplayRect();
        final int x = bounds.left + bounds.width() / 2;
        final int y = bounds.top + bounds.height() / 2;
        final long downTime = SystemClock.uptimeMillis();
        injectMotion(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, displayId, true /* sync */);

        final long eventTime = SystemClock.uptimeMillis();
        final int touchSlop = ViewConfiguration.get(mContext).getScaledTouchSlop();
        final int tapX = x + Math.round(touchSlop / 2.0f);
        final int tapY = y + Math.round(touchSlop / 2.0f);
        injectMotion(downTime, eventTime, MotionEvent.ACTION_CANCEL, tapX, tapY, displayId,
                true /* sync */);
    }

    public void tapOnDisplaySync(int x, int y, int displayId) {
        tapOnDisplay(x, y, displayId, true /* sync*/);
    }

    public void tapOnDisplay(int x, int y, int displayId, boolean sync) {
        tapOnDisplay(x, y, displayId, sync, /* waitAnimations */ true);
    }

    public void tapOnDisplay(int x, int y, int displayId, boolean sync, boolean waitAnimations) {
        final long downTime = SystemClock.uptimeMillis();
        injectMotion(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, displayId, sync,
                waitAnimations);

        final long upTime = SystemClock.uptimeMillis();
        injectMotion(downTime, upTime, MotionEvent.ACTION_UP, x, y, displayId, sync,
                waitAnimations);

        if (waitAnimations) {
            mWmState.waitForWithAmState(state -> state.getFocusedDisplayId() == displayId,
                    "top focused displayId: " + displayId);
        }
        // This is needed after a tap in multi-display to ensure that the display focus has really
        // changed, if needed. The call to syncInputTransaction will wait until focus change has
        // propagated from WMS to native input before returning.
        mInstrumentation.getUiAutomation().syncInputTransactions(waitAnimations);
    }

    public void tapOnCenter(Rect bounds, int displayId) {
        tapOnCenter(bounds, displayId, true  /* waitAnimation */);
    }

    public void tapOnCenter(Rect bounds, int displayId, boolean waitAnimation) {
        final int tapX = bounds.left + bounds.width() / 2;
        final int tapY = bounds.top + bounds.height() / 2;
        tapOnDisplay(tapX, tapY, displayId, true /* sync */, waitAnimation);
    }

    public void tapOnViewCenter(View view) {
        tapOnViewCenter(view, true /* waitAnimations */);
    }

    public void tapOnViewCenter(View view, boolean waitAnimations) {
        final int[] topleft = new int[2];
        view.getLocationOnScreen(topleft);
        int x = topleft[0] + view.getWidth() / 2;
        int y = topleft[1] + view.getHeight() / 2;
        tapOnDisplay(x, y, view.getDisplay().getDisplayId(), true /* sync */, waitAnimations);
    }

    public void tapOnTaskCenter(WindowManagerState.Task task) {
        tapOnCenter(task.getBounds(), task.mDisplayId);
    }

    public void tapOnDisplayCenter(int displayId) {
        final Rect bounds = mWmState.getDisplay(displayId).getDisplayRect();
        tapOnDisplaySync(bounds.centerX(), bounds.centerY(), displayId);
    }

    public void tapOnDisplayCenterAsync(int displayId) {
        final Rect bounds = mWmState.getDisplay(displayId).getDisplayRect();
        tapOnDisplay(bounds.centerX(), bounds.centerY(), displayId, false /* sync */);
    }

    public static void injectMotion(long downTime, long eventTime, int action,
            int x, int y, int displayId, boolean sync) {
        injectMotion(downTime, eventTime, action, x, y, displayId, sync,
                true /* waitForAnimations */);
    }

    public static void injectMotion(long downTime, long eventTime, int action,
            int x, int y, int displayId, boolean sync, boolean waitAnimations) {
        final MotionEvent event = MotionEvent.obtain(downTime, eventTime, action,
                x, y, 0 /* metaState */);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        event.setDisplayId(displayId);
        getInstrumentation().getUiAutomation().injectInputEvent(event, sync, waitAnimations);
    }

    public static void injectKey(int keyCode, boolean longPress, boolean sync) {
        final long downTime = injectKeyActionDown(keyCode, longPress, sync);
        injectKeyActionUp(keyCode, downTime, /* cancelled = */ false, sync);
    }

    public static long injectKeyActionDown(int keyCode, boolean longPress, boolean sync) {
        final long downTime = SystemClock.uptimeMillis();
        int repeatCount = 0;
        final KeyEvent downEvent =
                new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, repeatCount);
        getInstrumentation().getUiAutomation().injectInputEvent(downEvent, sync);
        if (longPress) {
            repeatCount += 1;
            final KeyEvent repeatEvent = new KeyEvent(downTime, SystemClock.uptimeMillis(),
                    KeyEvent.ACTION_DOWN, keyCode, repeatCount);
            getInstrumentation().getUiAutomation().injectInputEvent(repeatEvent, sync);
        }
        return downTime;
    }

    public static void injectKeyActionUp(int keyCode, long downTime, boolean cancelled,
            boolean sync) {
        final int flags;
        if (cancelled) {
            flags = KeyEvent.FLAG_CANCELED;
        } else {
            flags = 0;
        }
        final KeyEvent upEvent = new KeyEvent(downTime, SystemClock.uptimeMillis(),
                KeyEvent.ACTION_UP, keyCode, /* repeatCount = */ 0, /* metaState= */ 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, /* scancode= */ 0, flags);
        getInstrumentation().getUiAutomation().injectInputEvent(upEvent, sync);
    }

    public void tapOnTaskCenterAsync(WindowManagerState.Task task) {
        final Rect bounds = task.getBounds();
        final int x = bounds.left + bounds.width() / 2;
        final int y = bounds.top + bounds.height() / 2;
        tapOnDisplay(x, y, task.mDisplayId, false /* sync*/);
    }

    public void triggerBackEventByGesture(int displayId, boolean sync, boolean waitForAnimations) {
        final Rect bounds = mWmState.getDisplay(displayId).getDisplayRect();
        int midHeight = bounds.top + bounds.height() / 2;
        int midWidth = bounds.left + bounds.width() / 2;
        final SwipeSession session = new SwipeSession(displayId, sync, waitForAnimations);
        session.quickSwipe(0, midHeight, midWidth, midHeight, 10);
    }

    /**
     * Helper class for injecting a sequence of motion event to simulate a gesture swipe.
     */
    public static class SwipeSession {
        private static final int INJECT_INPUT_DELAY_MILLIS = 5;
        private final int mDisplayId;
        private final boolean mSync;
        private final boolean mWaitForAnimations;
        private int mStartX;
        private int mStartY;
        private int mEndX;
        private int mEndY;
        private long mStartDownTime = -1;
        private long mNextEventTime = -1;

        public SwipeSession(int displayId, boolean sync, boolean waitForAnimations) {
            mDisplayId = displayId;
            mSync = sync;
            mWaitForAnimations = waitForAnimations;
        }

        public long beginSwipe(int startX, int startY) {
            mStartX = startX;
            mStartY = startY;
            mStartDownTime = SystemClock.uptimeMillis();
            injectMotion(mStartDownTime, mStartDownTime, MotionEvent.ACTION_DOWN, mStartX, mStartY,
                    mDisplayId, mSync, mWaitForAnimations);
            return mStartDownTime;
        }

        public void continueSwipe(int endX, int endY, int steps) {
            if (steps <= 0) {
                steps = 1;
            }
            mEndX = endX;
            mEndY = endY;
            // inject in every INJECT_INPUT_DELAY_MILLIS ms.
            final int delayMillis = INJECT_INPUT_DELAY_MILLIS;
            mNextEventTime = mStartDownTime + delayMillis;
            final int stepGapX = (mEndX - mStartX) / steps;
            final int stepGapY = (mEndY - mStartY) / steps;
            for (int i = 0; i < steps; i++) {
                SystemClock.sleep(delayMillis);
                final int nextX = mStartX + stepGapX * i;
                final int nextY = mStartY + stepGapY * i;
                injectMotion(mStartDownTime, mNextEventTime,
                        MotionEvent.ACTION_MOVE, nextX, nextY,
                        mDisplayId, mSync, mWaitForAnimations);
                mNextEventTime += delayMillis;
            }
        }

        public void finishSwipe() {
            injectMotion(mStartDownTime, mNextEventTime, MotionEvent.ACTION_UP, mEndX, mEndY,
                    mDisplayId, mSync, mWaitForAnimations);
        }

        public void cancelSwipe() {
            injectMotion(mStartDownTime, mNextEventTime, MotionEvent.ACTION_CANCEL, mEndX, mEndY,
                    mDisplayId, mSync, mWaitForAnimations);
        }

        void quickSwipe(int startX, int startY, int endX, int endY, int steps) {
            beginSwipe(startX, startY);
            continueSwipe(endX, endY, steps);
            SystemClock.sleep(INJECT_INPUT_DELAY_MILLIS);
            finishSwipe();
        }
    }
}
