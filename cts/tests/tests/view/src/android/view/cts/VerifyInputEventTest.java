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

package android.view.cts;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.INVALID_DISPLAY;
import static android.view.InputDevice.SOURCE_JOYSTICK;
import static android.view.KeyEvent.FLAG_CANCELED;
import static android.view.KeyEvent.KEYCODE_A;
import static android.view.MotionEvent.FLAG_WINDOW_IS_OBSCURED;
import static android.view.MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Point;
import android.hardware.input.InputManager;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VerifiedInputEvent;
import android.view.VerifiedKeyEvent;
import android.view.VerifiedMotionEvent;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.WindowUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

/**
 * Test {@link android.hardware.input.InputManager#verifyInputEvent(InputEvent)} functionality.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class VerifyInputEventTest {
    private static final int NANOS_PER_MILLISECOND = 1000000;
    private static final float EPSILON = 0.001f;
    private static final int INJECTED_EVENT_DEVICE_ID = KeyCharacterMap.VIRTUAL_KEYBOARD;

    private InputManager mInputManager;
    private UiAutomation mAutomation;
    private InputEventInterceptTestActivity mActivity;

    @Rule(order = 0)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

    @Rule(order = 1)
    public ActivityTestRule<InputEventInterceptTestActivity> mActivityRule =
            new ActivityTestRule<>(InputEventInterceptTestActivity.class);

    @Before
    public void setup() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mInputManager = instrumentation.getTargetContext().getSystemService(InputManager.class);
        assertNotNull(mInputManager);
        mAutomation = instrumentation.getUiAutomation();
        mActivity = mActivityRule.getActivity();
        WindowUtil.waitForFocus(mActivity);
    }

    @Test
    public void testKeyEvent() {
        final long downTime = SystemClock.uptimeMillis();
        final int keyCode = KEYCODE_A;
        KeyEvent downEvent = new KeyEvent(downTime, downTime,
                KeyEvent.ACTION_DOWN, keyCode, 0 /* repeatCount */);
        mAutomation.injectInputEvent(downEvent, true);
        KeyEvent received = waitForKey();
        VerifiedInputEvent verified = mInputManager.verifyInputEvent(received);
        assertNotNull(verified);
        compareKeys(downEvent, verified);

        // Send UP event for consistency
        KeyEvent upEvent = new KeyEvent(downTime, SystemClock.uptimeMillis(),
                KeyEvent.ACTION_UP, keyCode, 0 /* repeatCount */);
        mAutomation.injectInputEvent(upEvent, true);
        waitForKey();
    }

    /**
     * Try to verify an event that did not come from system, and therefore does not have an hmac
     * set properly.
     * Verification should fail.
     */
    @Test
    public void testKeyEventWithoutHmac() {
        final long downTime = SystemClock.uptimeMillis();
        final int keyCode = KEYCODE_A;
        KeyEvent downEvent = new KeyEvent(downTime, downTime,
                KeyEvent.ACTION_DOWN, keyCode, 0 /* repeatCount */);
        mAutomation.injectInputEvent(downEvent, true);
        waitForKey(); // we will not be using the received event
        VerifiedInputEvent verified = mInputManager.verifyInputEvent(downEvent);
        assertNull(verified);

        // Send UP event for consistency
        KeyEvent upEvent = new KeyEvent(downTime, SystemClock.uptimeMillis(),
                KeyEvent.ACTION_UP, keyCode, 0 /* repeatCount */);
        mAutomation.injectInputEvent(upEvent, true);
        waitForKey();
    }

    /**
     * Try to verify an event that came from system, but has been tempered with.
     * Verification should fail.
     */
    @Test
    public void testTamperedKeyEvent() {
        final long downTime = SystemClock.uptimeMillis();
        final int keyCode = KEYCODE_A;
        KeyEvent downEvent = new KeyEvent(downTime, downTime,
                KeyEvent.ACTION_DOWN, keyCode, 0 /* repeatCount */);
        mAutomation.injectInputEvent(downEvent, true);
        KeyEvent received = waitForKey();
        received.setSource(SOURCE_JOYSTICK); // use the received event, but modify its source
        VerifiedInputEvent verified = mInputManager.verifyInputEvent(received);
        assertNull(verified);

        // Send UP event for consistency
        KeyEvent upEvent = new KeyEvent(downTime, SystemClock.uptimeMillis(),
                KeyEvent.ACTION_UP, keyCode, 0 /* repeatCount */);
        mAutomation.injectInputEvent(upEvent, true);
        waitForKey();
    }

    @Test
    public void testMotionEvent() {
        final View view = mActivity.getWindow().getDecorView();
        final Point point = getViewCenterOnScreen(view);
        final long downTime = SystemClock.uptimeMillis();
        MotionEvent downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN,
                point.x, point.y, 0 /*metaState*/);
        downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        mAutomation.injectInputEvent(downEvent, true);
        MotionEvent received = waitForMotion();
        VerifiedInputEvent verified = mInputManager.verifyInputEvent(received);
        assertNotNull(verified);

        compareMotions(downEvent, verified);

        // Send UP event for consistency
        MotionEvent upEvent = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP, point.x, point.y, 0 /*metaState*/);
        upEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        mAutomation.injectInputEvent(upEvent, true);
        waitForMotion();
    }

    /**
     * Try to verify an event that did not come from system, and therefore does not have an hmac
     * set properly.
     * Verification should fail.
     */
    @Test
    public void testMotionEventWithoutHmac() {
        final View view = mActivity.getWindow().getDecorView();
        final Point point = getViewCenterOnScreen(view);
        final long downTime = SystemClock.uptimeMillis();
        MotionEvent downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN,
                point.x, point.y, 0 /*metaState*/);
        downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        mAutomation.injectInputEvent(downEvent, true);
        waitForMotion(); // we will not be using the received event
        VerifiedInputEvent verified = mInputManager.verifyInputEvent(downEvent);
        assertNull(verified);

        // Send UP event for consistency
        MotionEvent upEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_UP,
                point.x, point.y, 0 /*metaState*/);
        upEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        mAutomation.injectInputEvent(upEvent, true);
        waitForMotion();
    }

    /**
     * Try to verify an event that came from system, but has been tempered with.
     * Verification should fail.
     */
    @Test
    public void testTamperedMotionEvent() {
        final View view = mActivity.getWindow().getDecorView();
        final Point point = getViewCenterOnScreen(view);
        final long downTime = SystemClock.uptimeMillis();
        MotionEvent downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN,
                point.x, point.y, 0 /*metaState*/);
        downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        mAutomation.injectInputEvent(downEvent, true);
        MotionEvent received = waitForMotion();
        // use the received event, by modify its action
        received.setAction(MotionEvent.ACTION_CANCEL);
        VerifiedInputEvent verified = mInputManager.verifyInputEvent(received);
        assertNull(verified);

        // Send UP event for consistency
        MotionEvent upEvent = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP, point.x, point.y, 0 /*metaState*/);
        upEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        mAutomation.injectInputEvent(upEvent, true);
        waitForMotion();
    }

    /**
     * Ensure that injected key events that contain a real device id get injected as virtual
     * device events, to prevent misrepresentation of actual hardware.
     * The verified events should contain the virtual device id, which is consistent with what the
     * app receives.
     */
    @Test
    public void testDeviceIdBecomesVirtualForInjectedKeys() {
        final long downTime = SystemClock.uptimeMillis();
        KeyEvent downEvent = new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_A, 0 /*repeat*/, 0 /*metaState*/,
                1/*deviceId*/, 0 /*scanCode*/);
        mAutomation.injectInputEvent(downEvent, true);
        KeyEvent received = waitForKey();
        assertEquals(INJECTED_EVENT_DEVICE_ID, received.getDeviceId());

        // This event can still be verified, however.
        VerifiedInputEvent verified = mInputManager.verifyInputEvent(received);
        assertEquals(INJECTED_EVENT_DEVICE_ID, verified.getDeviceId());

        // Send UP event for consistency
        KeyEvent upEvent = new KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_A, 0 /*repeat*/, 0 /*metaState*/,
                1/*deviceId*/, 0 /*scanCode*/);
        mAutomation.injectInputEvent(upEvent, true);
        waitForKey();
    }

    /**
     * Ensure that injected motion events that contain a real device id get injected as virtual
     * device events, to prevent misrepresentation of actual hardware.
     * The verified events should contain the virtual device id, which is consistent with what the
     * app receives.
     */
    @Test
    public void testDeviceIdBecomesVirtualForInjectedMotions() {
        final View view = mActivity.getWindow().getDecorView();
        final Point point = getViewCenterOnScreen(view);
        final long downTime = SystemClock.uptimeMillis();
        MotionEvent downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN,
                point.x, point.y, 1 /*pressure*/, 1 /*size*/, 0 /*metaState*/,
                0 /*xPrecision*/, 0 /*yPrecision*/, 1 /*deviceId*/, 0 /*edgeFlags*/);
        downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        mAutomation.injectInputEvent(downEvent, true);
        MotionEvent received = waitForMotion();
        assertEquals(INJECTED_EVENT_DEVICE_ID, received.getDeviceId());

        // This event can still be verified, however.
        VerifiedInputEvent verified = mInputManager.verifyInputEvent(received);
        assertEquals(INJECTED_EVENT_DEVICE_ID, verified.getDeviceId());

        // Send UP event for consistency
        MotionEvent upEvent = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP, point.x, point.y, 0 /*pressure*/, 1 /*size*/,
                0 /*metaState*/, 0 /*xPrecision*/, 0 /*yPrecision*/,
                1 /*deviceId*/, 0 /*edgeFlags*/);
        upEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        mAutomation.injectInputEvent(upEvent, true);
        waitForMotion();
    }

    private static Point getViewCenterOnScreen(View view) {
        final int[] location = new int[2];
        view.getLocationOnScreen(location);
        final int width = view.getWidth();
        final int height = view.getHeight();

        return new Point(location[0] + width / 2, location[1] + height / 2);
    }

    private KeyEvent waitForKey() {
        return mActivity.mKeyEvents.poll();
    }

    private MotionEvent waitForMotion() {
        return mActivity.mMotionEvents.poll();
    }

    private static void compareKeys(KeyEvent keyEvent, VerifiedInputEvent verified) {
        assertEquals(INJECTED_EVENT_DEVICE_ID, verified.getDeviceId());
        assertEquals(keyEvent.getEventTime() * NANOS_PER_MILLISECOND,
                verified.getEventTimeNanos());
        assertEquals(keyEvent.getSource(), verified.getSource());
        assertEquals(INVALID_DISPLAY, verified.getDisplayId());

        assertTrue(verified instanceof VerifiedKeyEvent);
        VerifiedKeyEvent verifiedKey = (VerifiedKeyEvent) verified;

        assertEquals(keyEvent.getAction(), verifiedKey.getAction());
        assertEquals(keyEvent.getDownTime() * NANOS_PER_MILLISECOND,
                verifiedKey.getDownTimeNanos());
        compareKeyFlags(keyEvent.getFlags(), verifiedKey);
        assertEquals(keyEvent.getKeyCode(), verifiedKey.getKeyCode());
        assertEquals(keyEvent.getScanCode(), verifiedKey.getScanCode());
        assertEquals(keyEvent.getMetaState(), verifiedKey.getMetaState());
        assertEquals(keyEvent.getRepeatCount(), verifiedKey.getRepeatCount());
    }

    private static void compareMotions(MotionEvent motionEvent, VerifiedInputEvent verified) {
        assertEquals(INJECTED_EVENT_DEVICE_ID, verified.getDeviceId());
        assertEquals(motionEvent.getEventTime() * NANOS_PER_MILLISECOND,
                verified.getEventTimeNanos());
        assertEquals(motionEvent.getSource(), verified.getSource());
        assertEquals(DEFAULT_DISPLAY, verified.getDisplayId());

        assertTrue(verified instanceof VerifiedMotionEvent);
        VerifiedMotionEvent verifiedMotion = (VerifiedMotionEvent) verified;

        assertEquals(motionEvent.getRawX(), verifiedMotion.getRawX(), EPSILON);
        assertEquals(motionEvent.getRawY(), verifiedMotion.getRawY(), EPSILON);
        assertEquals(motionEvent.getActionMasked(), verifiedMotion.getActionMasked());
        assertEquals(motionEvent.getDownTime() * NANOS_PER_MILLISECOND,
                verifiedMotion.getDownTimeNanos());
        compareMotionFlags(motionEvent.getFlags(), verifiedMotion);
        assertEquals(motionEvent.getMetaState(), verifiedMotion.getMetaState());
        assertEquals(motionEvent.getButtonState(), verifiedMotion.getButtonState());
    }

    private static void compareKeyFlags(int expectedFlags, VerifiedKeyEvent verified) {
        final List<Integer> verifiedKeyFlags = Arrays.asList(
                FLAG_CANCELED,
                KeyEvent.FLAG_IS_ACCESSIBILITY_EVENT);
        // Separately check the value of verifiable flags
        for (int flag : verifiedKeyFlags) {
            assertFlag(expectedFlags, flag, verified);
        }
        // All other flags should be null, because they are not verifiable
        for (int i = 0; i < Integer.SIZE; i++) {
            int flag = 1 << i;
            if (verifiedKeyFlags.contains(flag)) {
                continue;
            }
            assertNull(verified.getFlag(flag));
        }
    }

    private static void compareMotionFlags(int expectedFlags, VerifiedMotionEvent verified) {
        final List<Integer> verifiedMotionFlags = Arrays.asList(
                FLAG_WINDOW_IS_OBSCURED,
                FLAG_WINDOW_IS_PARTIALLY_OBSCURED,
                MotionEvent.FLAG_IS_ACCESSIBILITY_EVENT);
        // Separately check the value of verifiable flags
        for (int flag : verifiedMotionFlags) {
            assertFlag(expectedFlags, flag, verified);
        }
        // All other flags should be null, because they are not verifiable
        for (int i = 0; i < Integer.SIZE; i++) {
            int flag = 1 << i;
            if (verifiedMotionFlags.contains(flag)) {
                continue;
            }
            assertNull(verified.getFlag(flag));
        }
    }

    private static void assertFlag(int expectedFlags, int flag, VerifiedInputEvent verified) {
        final Boolean actual;
        if (verified instanceof VerifiedKeyEvent) {
            actual = ((VerifiedKeyEvent) verified).getFlag(flag);
        } else if (verified instanceof VerifiedMotionEvent) {
            actual = ((VerifiedMotionEvent) verified).getFlag(flag);
        } else {
            fail("Unknown type of VerifiedInputEvent");
            actual = null;
        }
        assertNotNull(actual);
        boolean flagValue = (expectedFlags & flag) != 0;
        assertEquals(flagValue, actual);
    }
}
