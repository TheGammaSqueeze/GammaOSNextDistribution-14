/*
 * Copyright 2015 The Android Open Source Project
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

package android.hardware.input.cts.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.hardware.input.cts.InputCallback;
import android.hardware.input.cts.InputCtsActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;
import com.android.cts.input.DebugInputRule;

import org.junit.After;
import org.junit.Before;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public abstract class InputTestCase {
    private static final String TAG = "InputTestCase";
    private static final float TOLERANCE = 0.005f;
    private static final int NUM_MAX_ATTEMPTS_TO_RECEIVE_SINGLE_EVENT = 5;

    // Ignore comparing input values for these axes. This is used to prevent breakages caused by
    // OEMs using custom key layouts to remap GAS/BRAKE to RTRIGGER/LTRIGGER (for example,
    // b/197062720).
    private static final Set<Integer> IGNORE_AXES = new HashSet<>(Arrays.asList(
            MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER,
            MotionEvent.AXIS_GAS, MotionEvent.AXIS_BRAKE));

    private final BlockingQueue<InputEvent> mEvents;
    protected final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();

    private final InputListener mInputListener;
    private View mDecorView;

    // Stores the name of the currently running test
    protected String mCurrentTestCase;

    // State used for motion events
    private int mLastButtonState;

    protected InputCtsActivity mTestActivity;

    InputTestCase() {
        mEvents = new LinkedBlockingQueue<>();
        mInputListener = new InputListener();
    }

    private ActivityScenario<InputCtsActivity> mActivityRule;

    @Before
    public void setUp() throws Exception {
        onBeforeLaunchActivity();
        mActivityRule = ActivityScenario.launch(InputCtsActivity.class, getActivityOptions())
                .onActivity(activity -> mTestActivity = activity);
        mTestActivity.clearUnhandleKeyCode();
        mTestActivity.setInputCallback(mInputListener);
        mDecorView = mTestActivity.getWindow().getDecorView();

        onSetUp();
        PollingCheck.waitFor(mTestActivity::hasWindowFocus);
        assertTrue(mCurrentTestCase + ": Activity window must have focus",
                mTestActivity.hasWindowFocus());

        mEvents.clear();
    }

    @After
    public void tearDown() throws Exception {
        onTearDown();
        if (mActivityRule != null) {
            mActivityRule.close();
        }
    }

    /** Optional setup logic performed before the test activity is launched. */
    void onBeforeLaunchActivity() {}

    abstract void onSetUp();

    abstract void onTearDown();

    /**
     * Get the activity options to launch the activity with.
     * @return the activity options or null.
     */
    @Nullable Bundle getActivityOptions() {
        return null;
    }

    /**
     * Asserts that the application received a {@link KeyEvent} with the given metadata.
     *
     * If the expected {@link KeyEvent} is not received within a reasonable number of attempts, then
     * this will throw an {@link AssertionError}.
     *
     * Only action, source, keyCode and metaState are being compared.
     */
    private void assertReceivedKeyEvent(@NonNull KeyEvent expectedKeyEvent) {
        KeyEvent receivedKeyEvent = waitForKey();
        if (receivedKeyEvent == null) {
            failWithMessage("Did not receive " + expectedKeyEvent);
        }
        assertEquals(mCurrentTestCase + " (action)",
                expectedKeyEvent.getAction(), receivedKeyEvent.getAction());
        assertSource(mCurrentTestCase, expectedKeyEvent, receivedKeyEvent);
        assertEquals(mCurrentTestCase + " (keycode) expected: "
                + KeyEvent.keyCodeToString(expectedKeyEvent.getKeyCode()) + " received: "
                + KeyEvent.keyCodeToString(receivedKeyEvent.getKeyCode()),
                expectedKeyEvent.getKeyCode(), receivedKeyEvent.getKeyCode());
        assertMetaState(mCurrentTestCase, expectedKeyEvent.getMetaState(),
                receivedKeyEvent.getMetaState());
    }

    /**
     * Asserts that the application received a {@link MotionEvent} with the given metadata.
     *
     * If the expected {@link MotionEvent} is not received within a reasonable number of attempts,
     * then this will throw an {@link AssertionError}.
     *
     * Only action, source, keyCode and metaState are being compared.
     */
    private void assertReceivedMotionEvent(@NonNull MotionEvent expectedEvent) {
        MotionEvent event = waitForMotion();
        /*
         If the test fails here, one thing to try is to forcefully add a delay after the device
         added callback has been received, but before any hid data has been written to the device.
         We already wait for all of the proper callbacks here and in other places of the stack, but
         it appears that the device sometimes is still not ready to receive hid data. If any data
         gets written to the device in that state, it will disappear,
         and no events will be generated.
          */

        if (event == null) {
            failWithMessage("Did not receive " + expectedEvent);
        }
        if (event.getHistorySize() > 0) {
            failWithMessage("expected each MotionEvent to only have a single entry");
        }
        assertEquals(mCurrentTestCase + " (action)",
                expectedEvent.getAction(), event.getAction());
        assertSource(mCurrentTestCase, expectedEvent, event);
        assertEquals(mCurrentTestCase + " (button state)",
                expectedEvent.getButtonState(), event.getButtonState());
        if (event.getActionMasked() == MotionEvent.ACTION_BUTTON_PRESS
                || event.getActionMasked() == MotionEvent.ACTION_BUTTON_RELEASE) {
            // Only checking getActionButton() for ACTION_BUTTON_PRESS or ACTION_BUTTON_RELEASE
            // because for actions other than ACTION_BUTTON_PRESS and ACTION_BUTTON_RELEASE the
            // returned value of getActionButton() is undefined.
            assertEquals(mCurrentTestCase + " (action button)",
                    mLastButtonState ^ event.getButtonState(), event.getActionButton());
            mLastButtonState = event.getButtonState();
        }
        assertAxis(mCurrentTestCase, expectedEvent, event);
    }

    /**
     * Asserts motion event axis values. Separate this into a different method to allow individual
     * test case to specify it.
     *
     * @param expectedEvent expected event flag specified in JSON files.
     * @param actualEvent actual event flag received in the test app.
     */
    void assertAxis(String testCase, MotionEvent expectedEvent, MotionEvent actualEvent) {
        for (int i = 0; i < actualEvent.getPointerCount(); i++) {
            for (int axis = MotionEvent.AXIS_X; axis <= MotionEvent.AXIS_GENERIC_16; axis++) {
                if (IGNORE_AXES.contains(axis)) continue;
                assertEquals(testCase + " pointer " + i
                        + " (" + MotionEvent.axisToString(axis) + ")",
                        expectedEvent.getAxisValue(axis, i), actualEvent.getAxisValue(axis, i),
                        TOLERANCE);
            }
        }
    }

    /**
     * Asserts source flags. Separate this into a different method to allow individual test case to
     * specify it.
     * The input source check verifies if actual source is equal or a subset of the expected source.
     * With Linux kernel 4.18 or later the input hid driver could register multiple evdev devices
     * when the HID descriptor has HID usages for different applications. Android frameworks will
     * create multiple KeyboardInputMappers for each of the evdev device, and each
     * KeyboardInputMapper will generate key events with source of the evdev device it belongs to.
     * As long as the source of these key events is a subset of expected source, we consider it as
     * a valid source.
     *
     * @param expected expected event with source flag specified in JSON files.
     * @param actual actual event with source flag received in the test app.
     */
    private void assertSource(String testCase, InputEvent expected, InputEvent actual) {
        assertNotEquals(testCase + " (source)", InputDevice.SOURCE_CLASS_NONE, actual.getSource());
        assertTrue(testCase + " (source)", expected.isFromSource(actual.getSource()));
    }

    /**
     * Asserts meta states. Separate this into a different method to allow individual test case to
     * specify it.
     *
     * @param expectedMetaState expected meta state specified in JSON files.
     * @param actualMetaState actual meta state received in the test app.
     */
    void assertMetaState(String testCase, int expectedMetaState, int actualMetaState) {
        assertEquals(testCase + " (meta state)", expectedMetaState, actualMetaState);
    }

    /**
     * Assert that no more events have been received by the application.
     *
     * If any more events have been received by the application, this will cause failure.
     */
    protected void assertNoMoreEvents() {
        mInstrumentation.waitForIdleSync();
        InputEvent event = mEvents.poll();
        if (event == null) {
            return;
        }
        failWithMessage("extraneous events generated: " + event);
    }

    protected void verifyEvents(List<InputEvent> events) {
        verifyFirstEvents(events);
        assertNoMoreEvents();
    }

    private void verifyFirstEvents(List<InputEvent> events) {
        // Make sure we received the expected input events
        if (events.size() == 0) {
            // If no event is expected we need to wait for event until timeout and fail on
            // any unexpected event received caused by the HID report injection.
            InputEvent event = waitForEvent();
            if (event != null) {
                failWithMessage("Received unexpected event " + event);
            }
            return;
        }
        for (int i = 0; i < events.size(); i++) {
            final InputEvent event = events.get(i);
            try {
                if (event instanceof MotionEvent) {
                    assertReceivedMotionEvent((MotionEvent) event);
                    continue;
                }
                if (event instanceof KeyEvent) {
                    assertReceivedKeyEvent((KeyEvent) event);
                    continue;
                }
            } catch (AssertionError error) {
                failWithMessage("Assertion on entry " + i + " failed: " + error);
            }
            failWithMessage("Entry " + i + " is neither a KeyEvent nor a MotionEvent: " + event);
        }
    }

    protected void verifyNoKeyEvents() {
        InputEvent event = waitForEvent();
        while (event != null) {
            if (event instanceof KeyEvent) {
                failWithMessage(" : Received unexpected KeyEvent " + event);
            }
            event = waitForEvent();
        }
    }

    private InputEvent waitForEvent() {
        try {
            return mEvents.poll(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            failWithMessage("unexpectedly interrupted while waiting for InputEvent");
            return null;
        }
    }

    /**
     * Try polling the events queue till a Key event is received. Ignore Motion events received
     * during the attempts, and return the first Key event received.
     */
    private KeyEvent waitForKey() {
        for (int i = 0; i < NUM_MAX_ATTEMPTS_TO_RECEIVE_SINGLE_EVENT; i++) {
            InputEvent event = waitForEvent();
            if (event instanceof KeyEvent) {
                return (KeyEvent) event;
            }
        }
        return null;
    }

    /**
     * Try polling the events queue till a Motion event is received. Ignore Key events received
     * during the attempts, and return the first Motion event received.
     */
    private MotionEvent waitForMotion() {
        for (int i = 0; i < NUM_MAX_ATTEMPTS_TO_RECEIVE_SINGLE_EVENT; i++) {
            InputEvent event = waitForEvent();
            if (event instanceof MotionEvent) {
                return (MotionEvent) event;
            }
        }
        return null;
    }

    /**
     * Since MotionEvents are batched together based on overall system timings (i.e. vsync), we
     * can't rely on them always showing up batched in the same way. In order to make sure our
     * test results are consistent, we instead split up the batches so they end up in a
     * consistent and reproducible stream.
     *
     * Note, however, that this ignores the problem of resampling, as we still don't know how to
     * distinguish resampled events from real events. Only the latter will be consistent and
     * reproducible.
     *
     * @param event The (potentially) batched MotionEvent
     * @return List of MotionEvents, with each event guaranteed to have zero history size, and
     * should otherwise be equivalent to the original batch MotionEvent.
     */
    private static List<MotionEvent> splitBatchedMotionEvent(MotionEvent event) {
        List<MotionEvent> events = new ArrayList<>();
        final int historySize = event.getHistorySize();
        final int pointerCount = event.getPointerCount();
        MotionEvent.PointerProperties[] properties =
                new MotionEvent.PointerProperties[pointerCount];
        MotionEvent.PointerCoords[] currentCoords = new MotionEvent.PointerCoords[pointerCount];
        for (int p = 0; p < pointerCount; p++) {
            properties[p] = new MotionEvent.PointerProperties();
            event.getPointerProperties(p, properties[p]);
            currentCoords[p] = new MotionEvent.PointerCoords();
            event.getPointerCoords(p, currentCoords[p]);
        }
        for (int h = 0; h < historySize; h++) {
            long eventTime = event.getHistoricalEventTime(h);
            MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[pointerCount];

            for (int p = 0; p < pointerCount; p++) {
                coords[p] = new MotionEvent.PointerCoords();
                event.getHistoricalPointerCoords(p, h, coords[p]);
            }
            MotionEvent singleEvent =
                    MotionEvent.obtain(event.getDownTime(), eventTime, event.getAction(),
                            pointerCount, properties, coords,
                            event.getMetaState(), event.getButtonState(),
                            event.getXPrecision(), event.getYPrecision(),
                            event.getDeviceId(), event.getEdgeFlags(),
                            event.getSource(), event.getFlags());
            singleEvent.setActionButton(event.getActionButton());
            events.add(singleEvent);
        }

        MotionEvent singleEvent =
                MotionEvent.obtain(event.getDownTime(), event.getEventTime(), event.getAction(),
                        pointerCount, properties, currentCoords,
                        event.getMetaState(), event.getButtonState(),
                        event.getXPrecision(), event.getYPrecision(),
                        event.getDeviceId(), event.getEdgeFlags(),
                        event.getSource(), event.getFlags());
        singleEvent.setActionButton(event.getActionButton());
        events.add(singleEvent);
        return events;
    }

    /**
     * Append the name of the currently executing test case to the fail message.
     * Dump out the events queue to help debug.
     */
    private void failWithMessage(String message) {
        DebugInputRule.dumpInputStateToLogcat();
        if (mEvents.isEmpty()) {
            Log.i(TAG, "The events queue is empty");
        } else {
            Log.e(TAG, "There are additional events received by the test activity:");
            for (InputEvent event : mEvents) {
                Log.i(TAG, event.toString());
            }
        }
        fail(mCurrentTestCase + ": " + message);
    }

    void setConsumeGenericMotionEvents(boolean enable) {
        mTestActivity.setConsumeGenericMotionEvents(enable);
    }

    private class InputListener implements InputCallback {
        @Override
        public void onKeyEvent(KeyEvent ev) {
            try {
                mEvents.put(new KeyEvent(ev));
            } catch (InterruptedException ex) {
                failWithMessage("interrupted while adding a KeyEvent to the queue");
            }
        }

        @Override
        public void onMotionEvent(MotionEvent ev) {
            try {
                for (MotionEvent event : splitBatchedMotionEvent(ev)) {
                    mEvents.put(event);
                }
            } catch (InterruptedException ex) {
                failWithMessage("interrupted while adding a MotionEvent to the queue");
            }
        }
    }

    protected class PointerCaptureSession implements AutoCloseable {
        protected PointerCaptureSession() {
            ensurePointerCaptureState(true);
        }

        @Override
        public void close() {
            ensurePointerCaptureState(false);
        }

        private void ensurePointerCaptureState(boolean enable) {
            final CountDownLatch latch = new CountDownLatch(1);
            mTestActivity.setPointerCaptureCallback(hasCapture -> {
                if (enable == hasCapture) {
                    latch.countDown();
                }
            });
            mTestActivity.runOnUiThread(enable ? mDecorView::requestPointerCapture
                    : mDecorView::releasePointerCapture);
            try {
                if (!latch.await(60, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Did not receive callback after "
                                    + (enable ? "enabling" : "disabling")
                                    + " Pointer Capture.");
                }
            } catch (InterruptedException e) {
                throw new IllegalStateException(
                        "Interrupted while waiting for Pointer Capture state.");
            } finally {
                mTestActivity.setPointerCaptureCallback(null);
            }
            assertEquals("The view's Pointer Capture state did not match.", enable,
                    mDecorView.hasPointerCapture());
        }
    }
}
