/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.compatibility.common.util;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Point;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.Nullable;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.UiDevice;

import java.util.Objects;

/**
 * Test utilities for touch emulation.
 */
public final class CtsTouchUtils {

    private static final String TAG = CtsTouchUtils.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final UserHelper mUserHelper;

    public CtsTouchUtils(Context context) {
        this(new UserHelper(context));
    }

    public CtsTouchUtils(UserHelper userHelper) {
        mUserHelper = Objects.requireNonNull(userHelper);
        if (DEBUG) {
            Log.d(TAG, "Creating CtsTouchUtils() for " + userHelper);
        }
    }

    /**
     * Interface definition for a callback to be invoked when an event has been injected.
     */
    public interface EventInjectionListener {
        /**
         * Callback method to be invoked when a {MotionEvent#ACTION_DOWN} has been injected.
         * @param xOnScreen X coordinate of the injected event.
         * @param yOnScreen Y coordinate of the injected event.
         */
        public void onDownInjected(int xOnScreen, int yOnScreen);

        /**
         * Callback method to be invoked when a {MotionEvent#ACTION_MOVE} has been injected.
         * @param xOnScreen X coordinates of the injected event.
         * @param yOnScreen Y coordinates of the injected event.
         */
        public void onMoveInjected(int[] xOnScreen, int[] yOnScreen);

        /**
         * Callback method to be invoked when a {MotionEvent#ACTION_UP} has been injected.
         * @param xOnScreen X coordinate of the injected event.
         * @param yOnScreen Y coordinate of the injected event.
         */
        public void onUpInjected(int xOnScreen, int yOnScreen);
    }

    /**
     * Emulates a tap in the center of the passed {@link View}.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param view the view to "tap"
     */
    public void emulateTapOnViewCenter(Instrumentation instrumentation,
            ActivityTestRule<?> activityTestRule, View view) {
        emulateTapOnViewCenter(instrumentation, activityTestRule, view, true,
                /* useGlobalInjection= */ false);
    }

    /**
     * Emulates a tap in the center of the passed {@link View}.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param view the view to "tap"
     * @param waitForAnimations wait for animations to complete before sending an event
     * @param useGlobalInjection Whether to use targeted injection (false) or global (true).
     * Targeted injection will only send injected events to the owned test app, while global will
     * send the events to the entire system, including spy windows (launcher/System UI).
     * Always use targeted injection unless you know what you are doing.
     */
    public void emulateTapOnViewCenter(Instrumentation instrumentation,
            ActivityTestRule<?> activityTestRule, View view, boolean waitForAnimations,
            boolean useGlobalInjection) {
        emulateTapOnView(instrumentation, activityTestRule, view, view.getWidth() / 2,
                view.getHeight() / 2, waitForAnimations, useGlobalInjection);
    }

    /**
     * Emulates a tap on a point relative to the top-left corner of the passed {@link View}. Offset
     * parameters are used to compute the final screen coordinates of the tap point.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param anchorView the anchor view to determine the tap location on the screen
     * @param offsetX extra X offset for the tap
     * @param offsetY extra Y offset for the tap
     */
    public void emulateTapOnView(Instrumentation instrumentation,
            ActivityTestRule<?> activityTestRule, View anchorView,
            int offsetX, int offsetY) {
        emulateTapOnView(instrumentation, activityTestRule, anchorView, offsetX, offsetY,
                /* waitForAnimations= */ true, /* useGlobalInjection= */ false);
    }

    /**
     * Emulates a tap on a point relative to the top-left corner of the passed {@link View}. Offset
     * parameters are used to compute the final screen coordinates of the tap point.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param anchorView the anchor view to determine the tap location on the screen
     * @param offsetX extra X offset for the tap
     * @param offsetY extra Y offset for the tap
     * @param waitForAnimations wait for animations to complete before sending an event
     * @param useGlobalInjection Whether to use targeted injection (false) or global (true).
     * Targeted injection will only send injected events to the owned test app, while global will
     * send the events to the entire system, including spy windows (launcher/System UI).
     * Always use targeted injection unless you know what you are doing.
     */
    public void emulateTapOnView(Instrumentation instrumentation,
            ActivityTestRule<?> activityTestRule, View anchorView,
            int offsetX, int offsetY, boolean waitForAnimations, boolean useGlobalInjection) {
        final int touchSlop = ViewConfiguration.get(anchorView.getContext()).getScaledTouchSlop();
        // Get anchor coordinates on the screen
        final int[] viewOnScreenXY = new int[2];
        anchorView.getLocationOnScreen(viewOnScreenXY);
        int xOnScreen = viewOnScreenXY[0] + offsetX;
        int yOnScreen = viewOnScreenXY[1] + offsetY;
        final long downTime = SystemClock.uptimeMillis();

        injectDownEvent(instrumentation, downTime, xOnScreen, yOnScreen, waitForAnimations,
                /* eventInjectionListener= */ null, useGlobalInjection);
        injectMoveEventForTap(instrumentation, downTime, touchSlop, xOnScreen, yOnScreen,
                waitForAnimations, useGlobalInjection);
        injectUpEvent(instrumentation, downTime, /* useCurrentEventTime= */ false,
                xOnScreen, yOnScreen, waitForAnimations, /* eventInjectionListener= */ null,
                useGlobalInjection);

        // Wait for the system to process all events in the queue
        if (activityTestRule != null) {
            WidgetTestUtils.runOnMainAndDrawSync(activityTestRule,
                    activityTestRule.getActivity().getWindow().getDecorView(), null);
        } else {
            instrumentation.waitForIdleSync();
        }
    }

    /**
     * Emulates a double tap in the center of the passed {@link View}.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param view the view to "double tap"
     */
    public void emulateDoubleTapOnViewCenter(Instrumentation instrumentation,
            ActivityTestRule<?> activityTestRule, View view) {
        emulateDoubleTapOnView(instrumentation, activityTestRule, view, view.getWidth() / 2,
                view.getHeight() / 2);
    }

    /**
     * Emulates a double tap on a point relative to the top-left corner of the passed {@link View}.
     * Offset parameters are used to compute the final screen coordinates of the tap points.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param anchorView the anchor view to determine the tap location on the screen
     * @param offsetX extra X offset for the taps
     * @param offsetY extra Y offset for the taps
     */
    public void emulateDoubleTapOnView(Instrumentation instrumentation,
            ActivityTestRule<?> activityTestRule, View anchorView,
            int offsetX, int offsetY) {
        final int touchSlop = ViewConfiguration.get(anchorView.getContext()).getScaledTouchSlop();
        // Get anchor coordinates on the screen
        final int[] viewOnScreenXY = new int[2];
        anchorView.getLocationOnScreen(viewOnScreenXY);
        int xOnScreen = viewOnScreenXY[0] + offsetX;
        int yOnScreen = viewOnScreenXY[1] + offsetY;
        final long downTime = SystemClock.uptimeMillis();

        injectDownEvent(instrumentation, downTime, xOnScreen, yOnScreen, null,
                /* useGlobalInjection= */ false);
        injectMoveEventForTap(instrumentation, downTime, touchSlop, xOnScreen, yOnScreen, true,
                /* useGlobalInjection= */ false);
        injectUpEvent(instrumentation, downTime, false, xOnScreen, yOnScreen, null,
                /* useGlobalInjection= */ false);
        injectDownEvent(instrumentation, downTime, xOnScreen, yOnScreen, null,
                /* useGlobalInjection= */ false);
        injectMoveEventForTap(instrumentation, downTime, touchSlop, xOnScreen, yOnScreen, true,
                /* useGlobalInjection= */ false);
        injectUpEvent(instrumentation, downTime, false, xOnScreen, yOnScreen, null,
                /* useGlobalInjection= */ false);

        // Wait for the system to process all events in the queue
        if (activityTestRule != null) {
            WidgetTestUtils.runOnMainAndDrawSync(activityTestRule,
                    activityTestRule.getActivity().getWindow().getDecorView(), null);
        } else {
            instrumentation.waitForIdleSync();
        }
    }

    /**
     * Emulates a linear drag gesture between 2 points across the screen.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param dragStartX Start X of the emulated drag gesture
     * @param dragStartY Start Y of the emulated drag gesture
     * @param dragAmountX X amount of the emulated drag gesture
     * @param dragAmountY Y amount of the emulated drag gesture
     */
    public void emulateDragGesture(Instrumentation instrumentation,
            ActivityTestRule<?> activityTestRule, int dragStartX, int dragStartY,
            int dragAmountX, int dragAmountY) {
        emulateDragGesture(instrumentation, activityTestRule,
                dragStartX, dragStartY, dragAmountX, dragAmountY, /* dragDurationMs= */ 2000,
                /* moveEventCount= */ 20, /* waitForAnimations= */ true,
                /* eventInjectionListener= */ null);
    }

    private void emulateDragGesture(Instrumentation instrumentation,
            ActivityTestRule<?> activityTestRule, int dragStartX, int dragStartY, int dragAmountX,
            int dragAmountY, int dragDurationMs, int moveEventCount) {
        emulateDragGesture(instrumentation, activityTestRule, dragStartX, dragStartY, dragAmountX,
                dragAmountY, dragDurationMs, moveEventCount, /* eventInjectionListener= */ null);
    }

    /**
     * Emulates a linear drag gesture between 2 points across the screen.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param dragStartX Start X of the emulated drag gesture
     * @param dragStartY Start Y of the emulated drag gesture
     * @param dragAmountX X amount of the emulated drag gesture
     * @param dragAmountY Y amount of the emulated drag gesture
     * @param dragDurationMs The time in milliseconds over which the drag occurs
     * @param moveEventCount The number of events that produce the movement
     * @param eventInjectionListener Called after each down, move, and up events.
     */
    public void emulateDragGesture(Instrumentation instrumentation,
            ActivityTestRule<?> activityTestRule,
            int dragStartX, int dragStartY, int dragAmountX, int dragAmountY,
            int dragDurationMs, int moveEventCount,
            @Nullable EventInjectionListener eventInjectionListener) {
        emulateDragGesture(instrumentation, activityTestRule, dragStartX, dragStartY, dragAmountX,
                dragAmountY, dragDurationMs, moveEventCount, /* waitForAnimations= */ true,
                eventInjectionListener);
    }

    /**
     * Emulates a linear drag gesture between 2 points across the screen.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param dragStartX Start X of the emulated drag gesture
     * @param dragStartY Start Y of the emulated drag gesture
     * @param dragAmountX X amount of the emulated drag gesture
     * @param dragAmountY Y amount of the emulated drag gesture
     * @param dragDurationMs The time in milliseconds over which the drag occurs
     * @param moveEventCount The number of events that produce the movement
     * @param waitForAnimations wait for animations to complete before sending an event
     * @param eventInjectionListener Called after each down, move, and up events.
     */
    public void emulateDragGesture(Instrumentation instrumentation,
            ActivityTestRule<?> activityTestRule,
            int dragStartX, int dragStartY, int dragAmountX, int dragAmountY,
            int dragDurationMs, int moveEventCount,
            boolean waitForAnimations, @Nullable EventInjectionListener eventInjectionListener) {
        final long downTime = SystemClock.uptimeMillis();

        injectDownEvent(instrumentation, downTime, dragStartX, dragStartY, waitForAnimations,
                eventInjectionListener, /* useGlobalInjection= */ false);

        // Inject a sequence of MOVE events that emulate the "move" part of the gesture
        injectMoveEventsForDrag(instrumentation, downTime, dragStartX, dragStartY,
                dragStartX + dragAmountX, dragStartY + dragAmountY, moveEventCount,
                dragDurationMs, waitForAnimations, eventInjectionListener,
                /* useGlobalInjection= */ false);

        injectUpEvent(instrumentation, downTime, true, dragStartX + dragAmountX,
                dragStartY + dragAmountY, waitForAnimations, eventInjectionListener,
                /* useGlobalInjection= */ false);

        // Wait for the system to process all events in the queue
        if (activityTestRule != null) {
            WidgetTestUtils.runOnMainAndDrawSync(activityTestRule,
                    activityTestRule.getActivity().getWindow().getDecorView(), null);
        } else {
            instrumentation.waitForIdleSync();
        }
    }

    /**
     * Emulates a series of linear drag gestures across the screen between multiple points without
     * lifting the finger. Note that this function does not support curve movements between the
     * points.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param coordinates the ordered list of points for the drag gesture
     */
    public void emulateDragGesture(Instrumentation instrumentation,
            ActivityTestRule<?> activityTestRule, SparseArray<Point> coordinates) {
        final int moveEventCount = 20;
        int dragDurationMs = 2000;

        final int touchSlop = ViewConfiguration.get(
                activityTestRule.getActivity()).getScaledTouchSlop();
        final long longPressTimeoutMs = ViewConfiguration.getLongPressTimeout();
        final int maxDragDurationMs = getMaxDragDuration(touchSlop, longPressTimeoutMs, coordinates,
                moveEventCount);
        if (maxDragDurationMs < dragDurationMs) {
            Log.d(TAG, "emulateDragGesture: Lowering standard drag duration from " + dragDurationMs
                    + " ms to " + maxDragDurationMs + " ms to avoid triggering a long press ");
            dragDurationMs = maxDragDurationMs;
        }

        emulateDragGesture(instrumentation, activityTestRule, coordinates, dragDurationMs,
                moveEventCount, /* useGlobalInjection= */ false);
    }

    /**
     * Get the maximal drag duration that assures not triggering a long press during a drag gesture
     * considering long press timeout and touch slop.
     *
     * The calculation is based on the distance between the first and the second point of provided
     * coordinates.
     */
    private int getMaxDragDuration(int touchSlop, long longPressTimeoutMs,
            SparseArray<Point> coordinates, int moveEventCount) {
        final int coordinatesSize = coordinates.size();
        if (coordinatesSize < 2) {
            throw new IllegalArgumentException("Need at least 2 points for emulating drag");
        }

        final int deltaX = coordinates.get(0).x - coordinates.get(1).x;
        final int deltaY = coordinates.get(0).y - coordinates.get(1).y;
        final double dragDistance = Math.sqrt(Math.pow(deltaX, 2) + Math.pow(deltaY, 2));
        final double moveEventDistance = (double) dragDistance / moveEventCount;

        // Number of move events needed to drag outside of the touch slop.
        // The initial sleep before the drag gesture begins is considered by adding one extra event.
        final double neededMoveEventsToExceedTouchSlop = touchSlop / moveEventDistance + 1;

        // Get maximal drag duration that assures a drag speed that does not trigger a long press.
        // Multiply with 0.9 to be on the safe side.
        int maxDragDuration = (int) (longPressTimeoutMs * 0.9 * moveEventCount
                / neededMoveEventsToExceedTouchSlop);
        return maxDragDuration;
    }

    private void emulateDragGesture(Instrumentation instrumentation,
            ActivityTestRule<?> activityTestRule,
            SparseArray<Point> coordinates, int dragDurationMs, int moveEventCount,
            boolean useGlobalInjection) {
        final int coordinatesSize = coordinates.size();
        if (coordinatesSize < 2) {
            throw new IllegalArgumentException("Need at least 2 points for emulating drag");
        }

        final long downTime = SystemClock.uptimeMillis();

        injectDownEvent(instrumentation, downTime, coordinates.get(0).x, coordinates.get(0).y,
                /* eventInjectionListener= */ null, useGlobalInjection);

        // Move to each coordinate.
        for (int i = 0; i < coordinatesSize - 1; i++) {
            // Inject a sequence of MOVE events that emulate the "move" part of the gesture.
            injectMoveEventsForDrag(instrumentation,
                    downTime,
                    coordinates.get(i).x,
                    coordinates.get(i).y,
                    coordinates.get(i + 1).x,
                    coordinates.get(i + 1).y,
                    moveEventCount,
                    dragDurationMs,
                    true,
                    null, useGlobalInjection);
        }

        injectUpEvent(instrumentation,
                downTime,
                true,
                coordinates.get(coordinatesSize - 1).x,
                coordinates.get(coordinatesSize - 1).y,
                null, useGlobalInjection);

        // Wait for the system to process all events in the queue
        if (activityTestRule != null) {
            WidgetTestUtils.runOnMainAndDrawSync(activityTestRule,
                    activityTestRule.getActivity().getWindow().getDecorView(), null);
        } else {
            instrumentation.waitForIdleSync();
        }
    }

    /**
     * Injects an {@link MotionEvent#ACTION_DOWN} event at the given coordinates.
     *
     * @param uiAutomation the uiAutomation used to run the test
     * @param downTime The time of the event, usually from {@link SystemClock#uptimeMillis()}
     * @param xOnScreen The x screen coordinate to press on
     * @param yOnScreen The y screen coordinate to press on
     * @param eventInjectionListener The listener to call back immediately after the down was
     *                               sent.
     * @param useGlobalInjection Whether to use targeted injection (false) or global (true).
     * Targeted injection will only send injected events to the owned test app, while global will
     * send the events to the entire system, including spy windows (launcher/System UI).
     * Always use targeted injection unless you know what you are doing.
     *
     * @return <code>downTime</code>
     */
    public long injectDownEvent(Instrumentation instrumentation, long downTime, int xOnScreen,
            int yOnScreen, @Nullable EventInjectionListener eventInjectionListener,
            boolean useGlobalInjection) {
        return injectDownEvent(instrumentation, downTime, xOnScreen, yOnScreen,
                /* waitForAnimations= */ true, eventInjectionListener, useGlobalInjection);
    }

    /**
     * Injects an {@link MotionEvent#ACTION_DOWN} event at the given coordinates.
     *
     * @param uiAutomation the uiAutomation used to run the test
     * @param downTime The time of the event, usually from {@link SystemClock#uptimeMillis()}
     * @param xOnScreen The x screen coordinate to press on
     * @param yOnScreen The y screen coordinate to press on
     * @param waitForAnimations wait for animations to complete before sending an event
     * @param eventInjectionListener The listener to call back immediately after the down was
     *                               sent.
     * @param useGlobalInjection Whether to use targeted injection (false) or global (true).
     * Targeted injection will only send injected events to the owned test app, while global will
     * send the events to the entire system, including spy windows (launcher/System UI).
     * Always use targeted injection unless you know what you are doing.
     *
     * @return <code>downTime</code>
     */
    private long injectDownEvent(Instrumentation instrumentation, long downTime, int xOnScreen,
            int yOnScreen, boolean waitForAnimations,
            @Nullable EventInjectionListener eventInjectionListener, boolean useGlobalInjection) {
        MotionEvent eventDown = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, xOnScreen, yOnScreen, 1);
        injectDisplayIdIfNeeded(eventDown);
        eventDown.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        injectPointerEvent(instrumentation, eventDown, waitForAnimations, useGlobalInjection);

        if (eventInjectionListener != null) {
            eventInjectionListener.onDownInjected(xOnScreen, yOnScreen);
        }
        eventDown.recycle();
        return downTime;
    }

    private void injectMoveEventForTap(Instrumentation instrumentation, long downTime,
            int touchSlop, int xOnScreen, int yOnScreen, boolean waitForAnimations,
            boolean useGlobalInjection) {
        MotionEvent eventMove = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_MOVE,
                xOnScreen + (touchSlop / 2.0f), yOnScreen + (touchSlop / 2.0f), 1);
        injectDisplayIdIfNeeded(eventMove);
        eventMove.setSource(InputDevice.SOURCE_TOUCHSCREEN);

        injectPointerEvent(instrumentation, eventMove, waitForAnimations, useGlobalInjection);
        eventMove.recycle();
    }

    private void injectMoveEventsForDrag(Instrumentation instrumentation, long downTime,
            int dragStartX, int dragStartY, int dragEndX, int dragEndY, int moveEventCount,
            int dragDurationMs, boolean waitForAnimations,
            EventInjectionListener eventInjectionListener, boolean useGlobalInjection) {

        final int dragAmountX = dragEndX - dragStartX;
        final int dragAmountY = dragEndY - dragStartY;
        final int sleepTime = dragDurationMs / moveEventCount;

        long prevEventTime = downTime;

        for (int i = 0; i < moveEventCount; i++) {
            // Note that the first MOVE event is generated "away" from the coordinates
            // of the start / DOWN event, and the last MOVE event is generated
            // at the same coordinates as the subsequent UP event.
            final int moveX = dragStartX + dragAmountX * (i  + 1) / moveEventCount;
            final int moveY = dragStartY + dragAmountY * (i  + 1) / moveEventCount;
            // Sleep for a bit to emulate the overall drag gesture. Take into account the amount
            // of time we already spent injecting the event (since the injection could be
            // synchronous).
            final long remainingSleep = sleepTime - (SystemClock.uptimeMillis() - prevEventTime);
            SystemClock.sleep(Math.max(0, remainingSleep));
            final long eventTime = SystemClock.uptimeMillis();

            // If necessary, generate history for our next MOVE event. The history is generated
            // to be spaced at 10 millisecond intervals, interpolating the coordinates from the
            // last generated MOVE event to our current one.
            int historyEventCount = (int) ((eventTime - prevEventTime) / 10);
            int[] xCoordsForListener = (eventInjectionListener == null) ? null :
                    new int[Math.max(1, historyEventCount)];
            int[] yCoordsForListener = (eventInjectionListener == null) ? null :
                    new int[Math.max(1, historyEventCount)];
            MotionEvent eventMove = null;
            if (historyEventCount == 0) {
                eventMove = MotionEvent.obtain(
                        downTime, eventTime, MotionEvent.ACTION_MOVE, moveX, moveY, 1);
                injectDisplayIdIfNeeded(eventMove);
                if (eventInjectionListener != null) {
                    xCoordsForListener[0] = moveX;
                    yCoordsForListener[0] = moveY;
                }
            } else {
                final int prevMoveX = dragStartX + dragAmountX * i / moveEventCount;
                final int prevMoveY = dragStartY + dragAmountY * i / moveEventCount;
                final int deltaMoveX = moveX - prevMoveX;
                final int deltaMoveY = moveY - prevMoveY;
                final long deltaTime = (eventTime - prevEventTime);
                for (int historyIndex = 0; historyIndex < historyEventCount; historyIndex++) {
                    int stepMoveX = prevMoveX + deltaMoveX * (historyIndex + 1) / historyEventCount;
                    int stepMoveY = prevMoveY + deltaMoveY * (historyIndex + 1) / historyEventCount;
                    long stepEventTime =
                            prevEventTime + deltaTime * (historyIndex + 1) / historyEventCount;
                    if (historyIndex == 0) {
                        // Generate the first event in our sequence
                        eventMove = MotionEvent.obtain(downTime, stepEventTime,
                                MotionEvent.ACTION_MOVE, stepMoveX, stepMoveY, 1);
                        injectDisplayIdIfNeeded(eventMove);
                    } else {
                        // and then add to it
                        eventMove.addBatch(stepEventTime, stepMoveX, stepMoveY, 1.0f, 1.0f, 1);
                    }
                    if (eventInjectionListener != null) {
                        xCoordsForListener[historyIndex] = stepMoveX;
                        yCoordsForListener[historyIndex] = stepMoveY;
                    }
                }
            }

            eventMove.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            injectPointerEvent(instrumentation, eventMove, waitForAnimations, useGlobalInjection);

            if (eventInjectionListener != null) {
                eventInjectionListener.onMoveInjected(xCoordsForListener, yCoordsForListener);
            }
            eventMove.recycle();
            prevEventTime = eventTime;
        }
    }

    /**
     * Injects an {@link MotionEvent#ACTION_UP} event at the given coordinates.
     *
     * @param instrumentation the Instrumentation used to run the test
     * @param downTime The time of the event, usually from {@link SystemClock#uptimeMillis()}
     * @param useCurrentEventTime <code>true</code> if it should use the current time for the
     *                            up event or <code>false</code> to use <code>downTime</code>.
     * @param xOnScreen The x screen coordinate to press on
     * @param yOnScreen The y screen coordinate to press on
     * @param eventInjectionListener The listener to call back immediately after the up was
     *                               sent.
     */
    public void injectUpEvent(Instrumentation instrumentation, long downTime,
            boolean useCurrentEventTime, int xOnScreen, int yOnScreen,
            EventInjectionListener eventInjectionListener) {
        injectUpEvent(instrumentation, downTime,
                useCurrentEventTime, xOnScreen, yOnScreen,
                eventInjectionListener, /*useGlobalInjection=*/ false);
    }

    private void injectUpEvent(Instrumentation instrumentation, long downTime,
            boolean useCurrentEventTime, int xOnScreen, int yOnScreen,
            EventInjectionListener eventInjectionListener, boolean useGlobalInjection) {
        injectUpEvent(instrumentation, downTime, useCurrentEventTime, xOnScreen, yOnScreen, true,
                eventInjectionListener, useGlobalInjection);
    }

    /**
     * Injects an {@link MotionEvent#ACTION_UP} event at the given coordinates.
     *
     * @param instrumentation the Instrumentation used to run the test
     * @param downTime The time of the event, usually from {@link SystemClock#uptimeMillis()}
     * @param useCurrentEventTime <code>true</code> if it should use the current time for the
     *                            up event or <code>false</code> to use <code>downTime</code>.
     * @param xOnScreen The x screen coordinate to press on
     * @param yOnScreen The y screen coordinate to press on
     * @param waitForAnimations wait for animations to complete before sending an event
     * @param eventInjectionListener The listener to call back immediately after the up was
     *                               sent.
     * @param useGlobalInjection Whether to use targeted injection (false) or global (true).
     * Targeted injection will only send injected events to the owned test app, while global will
     * send the events to the entire system, including spy windows (launcher/System UI).
     * Always use targeted injection unless you know what you are doing.
     */
    public void injectUpEvent(Instrumentation instrumentation, long downTime,
            boolean useCurrentEventTime, int xOnScreen, int yOnScreen,
            boolean waitForAnimations, EventInjectionListener eventInjectionListener,
            boolean useGlobalInjection) {
        long eventTime = useCurrentEventTime ? SystemClock.uptimeMillis() : downTime;
        MotionEvent eventUp = MotionEvent.obtain(
                downTime, eventTime, MotionEvent.ACTION_UP, xOnScreen, yOnScreen, 1);
        injectDisplayIdIfNeeded(eventUp);
        eventUp.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        injectPointerEvent(instrumentation, eventUp, waitForAnimations, useGlobalInjection);

        if (eventInjectionListener != null) {
            eventInjectionListener.onUpInjected(xOnScreen, yOnScreen);
        }
        eventUp.recycle();
    }

    private void injectPointerEvent(Instrumentation instrumentation, MotionEvent event,
            boolean waitForAnimations, boolean useGlobalInjection) {
        if (useGlobalInjection) {
            final UiAutomation uiAutomation = instrumentation.getUiAutomation();
            uiAutomation.injectInputEvent(event, true, waitForAnimations);
        } else {
            instrumentation.sendPointerSync(event);
        }
    }

    /**
     * Emulates a fling gesture across the horizontal center of the passed view.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param view the view to fling
     * @param isDownwardsFlingGesture if <code>true</code>, the emulated fling will
     *      be a downwards gesture
     * @return The vertical amount of emulated fling in pixels
     */
    public int emulateFlingGesture(Instrumentation instrumentation,
            ActivityTestRule<?> activityTestRule, View view, boolean isDownwardsFlingGesture) {
        return emulateFlingGesture(instrumentation, activityTestRule,
                view, isDownwardsFlingGesture, null);
    }

    /**
     * Emulates a fling gesture across the horizontal center of the passed view.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param view the view to fling
     * @param isDownwardsFlingGesture if <code>true</code>, the emulated fling will
     *      be a downwards gesture
     * @param eventInjectionListener optional listener to notify about the injected events
     * @return The vertical amount of emulated fling in pixels
     */
    public int emulateFlingGesture(Instrumentation instrumentation,
            ActivityTestRule<?> activityTestRule, View view, boolean isDownwardsFlingGesture,
            EventInjectionListener eventInjectionListener) {
        return emulateFlingGesture(instrumentation, activityTestRule, view, isDownwardsFlingGesture,
                true, eventInjectionListener);
    }

    /**
     * Emulates a fling gesture across the horizontal center of the passed view.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param view the view to fling
     * @param isDownwardsFlingGesture if <code>true</code>, the emulated fling will
     *      be a downwards gesture
     * @param waitForAnimations wait for animations to complete before sending an event
     * @param eventInjectionListener optional listener to notify about the injected events
     * @return The vertical amount of emulated fling in pixels
     */
    public int emulateFlingGesture(Instrumentation instrumentation,
            ActivityTestRule<?> activityTestRule, View view, boolean isDownwardsFlingGesture,
            boolean waitForAnimations, EventInjectionListener eventInjectionListener) {
        final ViewConfiguration configuration = ViewConfiguration.get(view.getContext());
        final int flingVelocity = (configuration.getScaledMinimumFlingVelocity() +
                configuration.getScaledMaximumFlingVelocity()) / 2;
        // Get view coordinates on the screen
        final int[] viewOnScreenXY = new int[2];
        view.getLocationOnScreen(viewOnScreenXY);

        // Our fling gesture will be from 25% height of the view to 75% height of the view
        // for downwards fling gesture, and the other way around for upwards fling gesture
        final int viewHeight = view.getHeight();
        final int x = viewOnScreenXY[0] + view.getWidth() / 2;
        final int startY = isDownwardsFlingGesture ? viewOnScreenXY[1] + viewHeight / 4
                : viewOnScreenXY[1] + 3 * viewHeight / 4;
        final int amountY = isDownwardsFlingGesture ? viewHeight / 2 : -viewHeight / 2;

        // Compute fling gesture duration based on the distance (50% height of the view) and
        // fling velocity
        final int durationMs = (1000 * viewHeight) / (2 * flingVelocity);

        // And do the same event injection sequence as our generic drag gesture
        emulateDragGesture(instrumentation, activityTestRule,
                x, startY, 0, amountY, durationMs, durationMs / 16,
                waitForAnimations, eventInjectionListener);

        return amountY;
    }

    private static final class ViewStateSnapshot {
        final View mFirst;
        final View mLast;
        final int mFirstTop;
        final int mLastBottom;
        final int mChildCount;
        private ViewStateSnapshot(ViewGroup viewGroup) {
            mChildCount = viewGroup.getChildCount();
            if (mChildCount == 0) {
                mFirst = mLast = null;
                mFirstTop = mLastBottom = Integer.MIN_VALUE;
            } else {
                mFirst = viewGroup.getChildAt(0);
                mLast = viewGroup.getChildAt(mChildCount - 1);
                mFirstTop = mFirst.getTop();
                mLastBottom = mLast.getBottom();
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final ViewStateSnapshot that = (ViewStateSnapshot) o;
            return mFirstTop == that.mFirstTop &&
                    mLastBottom == that.mLastBottom &&
                    mFirst == that.mFirst &&
                    mLast == that.mLast &&
                    mChildCount == that.mChildCount;
        }

        @Override
        public int hashCode() {
            int result = mFirst != null ? mFirst.hashCode() : 0;
            result = 31 * result + (mLast != null ? mLast.hashCode() : 0);
            result = 31 * result + mFirstTop;
            result = 31 * result + mLastBottom;
            result = 31 * result + mChildCount;
            return result;
        }
    }

    /**
     * Emulates a scroll to the bottom of the specified {@link ViewGroup}.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param viewGroup View group
     */
    public void emulateScrollToBottom(Instrumentation instrumentation,
            ActivityTestRule<?> activityTestRule, ViewGroup viewGroup) throws Throwable {
        final int[] viewGroupOnScreenXY = new int[2];
        viewGroup.getLocationOnScreen(viewGroupOnScreenXY);

        final int emulatedX = viewGroupOnScreenXY[0] + viewGroup.getWidth() / 2;
        final int emulatedStartY = viewGroupOnScreenXY[1] + 3 * viewGroup.getHeight() / 4;
        final int swipeAmount = viewGroup.getHeight() / 2;

        ViewStateSnapshot prev;
        ViewStateSnapshot next = new ViewStateSnapshot(viewGroup);
        do {
            prev = next;
            emulateDragGesture(instrumentation, activityTestRule,
                    emulatedX, emulatedStartY, 0, -swipeAmount, 300, 10);
            next = new ViewStateSnapshot(viewGroup);
	     // Wait for the UI Thread to become idle.
            final UiDevice device = UiDevice.getInstance(instrumentation);
            device.waitForIdle();
        } while (!prev.equals(next));

        // wait until the overscroll animation completes
        final boolean[] redrawn = new boolean[1];
        final boolean[] animationFinished = new boolean[1];
        final ViewTreeObserver.OnDrawListener onDrawListener = () -> {
            redrawn[0] = true;
        };

        activityTestRule.runOnUiThread(() -> {
            viewGroup.getViewTreeObserver().addOnDrawListener(onDrawListener);
        });
        while (!animationFinished[0]) {
            activityTestRule.runOnUiThread(() -> {
                if (!redrawn[0]) {
                    animationFinished[0] = true;
                }
                redrawn[0] = false;
            });
        }
        activityTestRule.runOnUiThread(() -> {
            viewGroup.getViewTreeObserver().removeOnDrawListener(onDrawListener);
        });
    }

    /**
     * Emulates a long press in the center of the passed {@link View}.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param view the view to "long press"
     */
    public void emulateLongPressOnViewCenter(Instrumentation instrumentation,
            ActivityTestRule<?> activityTestRule, View view) {
        emulateLongPressOnViewCenter(instrumentation, activityTestRule, view, 0);
    }

    /**
     * Emulates a long press in the center of the passed {@link View}.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param view the view to "long press"
     * @param extraWaitMs the duration of emulated "long press" in milliseconds starting
     *      after system-level long press timeout.
     */
    public void emulateLongPressOnViewCenter(Instrumentation instrumentation,
            ActivityTestRule<?> activityTestRule, View view, long extraWaitMs) {
        final int touchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        // Use instrumentation to emulate a tap on the spinner to bring down its popup
        final int[] viewOnScreenXY = new int[2];
        view.getLocationOnScreen(viewOnScreenXY);
        int xOnScreen = viewOnScreenXY[0] + view.getWidth() / 2;
        int yOnScreen = viewOnScreenXY[1] + view.getHeight() / 2;

        emulateLongPressOnScreen(instrumentation, activityTestRule,
                xOnScreen, yOnScreen, touchSlop, extraWaitMs, true);
    }

    /**
     * Emulates a long press confirmed on a point relative to the top-left corner of the passed
     * {@link View}. Offset parameters are used to compute the final screen coordinates of the
     * press point.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param view the view to "long press"
     * @param offsetX extra X offset for the tap
     * @param offsetY extra Y offset for the tap
     */
    public void emulateLongPressOnView(Instrumentation instrumentation,
            ActivityTestRule<?> activityTestRule, View view, int offsetX, int offsetY) {
        final int touchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        final int[] viewOnScreenXY = new int[2];
        view.getLocationOnScreen(viewOnScreenXY);
        int xOnScreen = viewOnScreenXY[0] + offsetX;
        int yOnScreen = viewOnScreenXY[1] + offsetY;

        emulateLongPressOnScreen(instrumentation, activityTestRule,
                xOnScreen, yOnScreen, touchSlop, 0, true);
    }

    /**
     * Emulates a long press then a linear drag gesture between 2 points across the screen.
     * This is used for drag selection.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param dragStartX Start X of the emulated drag gesture
     * @param dragStartY Start Y of the emulated drag gesture
     * @param dragAmountX X amount of the emulated drag gesture
     * @param dragAmountY Y amount of the emulated drag gesture
     */
    public void emulateLongPressAndDragGesture(Instrumentation instrumentation,
            ActivityTestRule<?> activityTestRule,
            int dragStartX, int dragStartY, int dragAmountX, int dragAmountY) {
        emulateLongPressOnScreen(instrumentation, activityTestRule, dragStartX, dragStartY,
                0 /* touchSlop */, 0 /* extraWaitMs */, false /* upGesture */);
        emulateDragGesture(instrumentation, activityTestRule, dragStartX, dragStartY, dragAmountX,
                dragAmountY);
    }

    /**
     * Emulates a long press on the screen.
     *
     * @param instrumentation the instrumentation used to run the test
     * @param xOnScreen X position on screen for the "long press"
     * @param yOnScreen Y position on screen for the "long press"
     * @param extraWaitMs extra duration of emulated long press in milliseconds added
     *        after the system-level "long press" timeout.
     * @param upGesture whether to include an up event.
     */
    private void emulateLongPressOnScreen(Instrumentation instrumentation,
            ActivityTestRule<?> activityTestRule,
            int xOnScreen, int yOnScreen, int touchSlop, long extraWaitMs, boolean upGesture) {
        final long downTime = SystemClock.uptimeMillis();

        injectDownEvent(instrumentation, downTime, xOnScreen, yOnScreen, null,
                /* useGlobalInjection= */ false);
        injectMoveEventForTap(instrumentation, downTime, touchSlop, xOnScreen, yOnScreen, true,
                /* useGlobalInjection= */ false);
        SystemClock.sleep((long) (ViewConfiguration.getLongPressTimeout() * 1.5f) + extraWaitMs);
        if (upGesture) {
            injectUpEvent(instrumentation, downTime, false, xOnScreen, yOnScreen, null,
                    /* useGlobalInjection= */ false);
        }

        // Wait for the system to process all events in the queue
        if (activityTestRule != null) {
            WidgetTestUtils.runOnMainAndDrawSync(activityTestRule,
                    activityTestRule.getActivity().getWindow().getDecorView(), null);
        } else {
            instrumentation.waitForIdleSync();
        }
    }

    private void injectDisplayIdIfNeeded(MotionEvent event) {
        mUserHelper.injectDisplayIdIfNeeded(event);
    }
}
