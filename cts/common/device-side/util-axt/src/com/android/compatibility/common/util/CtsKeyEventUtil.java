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
import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;

import com.google.errorprone.annotations.InlineMe;

import java.lang.reflect.Field;
import java.util.Objects;

/**
 * Utility class to send KeyEvents.
 *
 * <p>Keep in mind that the injected key events will also be dispatched into the currently selected
 * IME. To make your test deterministic, the test must be running with
 * {@code com.android.cts.testime}, {@code com.android.cts.mockime}, or any other instrumented test
 * IME that is owned and maintained by CTS that gives you a strong guarantee on how the IME responds
 * to such a key event (usually your expectation for the IME is to do nothing).</p>
 */
public final class CtsKeyEventUtil {

    private final UserHelper mUserHelper;

    public CtsKeyEventUtil(Context context) {
        this(new UserHelper(Objects.requireNonNull(context)));
    }

    public CtsKeyEventUtil(UserHelper userHelper) {
        mUserHelper = Objects.requireNonNull(userHelper);
    }

    /**
     * Sends the key events corresponding to the text to the app being instrumented.
     *
     * @param instrumentation the instrumentation used to run the test.
     * @param targetView Ignored.
     * @param text The text to be sent. Null value returns immediately.
     * @deprecated Use {@link #sendString(Instrumentation, String)} instead.
     */
    @Deprecated
    @InlineMe(replacement = "this.sendString(instrumentation, text)")
    public void sendString(Instrumentation instrumentation, View targetView, String text) {
        sendString(instrumentation, text);
    }

    /**
     * Sends the key events corresponding to the text to the app being instrumented.
     *
     * @param instrumentation the instrumentation used to run the test.
     * @param text The text to be sent. Null value returns immediately.
     */
    public void sendString(Instrumentation instrumentation, String text) {
        if (text == null) {
            return;
        }

        KeyEvent[] events = getKeyEvents(text);

        if (events != null) {
            for (int i = 0; i < events.length; i++) {
                // We have to change the time of an event before injecting it because
                // all KeyEvents returned by KeyCharacterMap.getEvents() have the same
                // time stamp and the system rejects too old events. Hence, it is
                // possible for an event to become stale before it is injected if it
                // takes too long to inject the preceding ones.
                sendKey(instrumentation, KeyEvent.changeTimeRepeat(
                        events[i], SystemClock.uptimeMillis(), 0 /* newRepeat */));
            }
        }
    }

    /**
     * Sends a series of key events through instrumentation. For instance:
     * sendKeys(view, KEYCODE_DPAD_LEFT, KEYCODE_DPAD_CENTER).
     *
     * @param instrumentation the instrumentation used to run the test.
     * @param targetView Ignored.
     * @param keys The series of key codes.
     * @deprecated Use {@link #sendKeys(Instrumentation, int...)} instead.
     */
    @Deprecated
    @InlineMe(replacement = "this.sendKeys(instrumentation, keys)")
    public void sendKeys(Instrumentation instrumentation, View targetView, int...keys) {
        sendKeys(instrumentation, keys);
    }

    /**
     * Sends a series of key events through instrumentation. For instance:
     * sendKeys(view, KEYCODE_DPAD_LEFT, KEYCODE_DPAD_CENTER).
     *
     * @param instrumentation the instrumentation used to run the test.
     * @param keys The series of key codes.
     */
    public void sendKeys(Instrumentation instrumentation, int...keys) {
        final int count = keys.length;

        for (int i = 0; i < count; i++) {
            try {
                sendKeyDownUp(instrumentation, keys[i]);
            } catch (SecurityException e) {
                // Ignore security exceptions that are now thrown
                // when trying to send to another app, to retain
                // compatibility with existing tests.
            }
        }
    }

    /**
     * Sends a series of key events through instrumentation. The sequence of keys is a string
     * containing the key names as specified in KeyEvent, without the KEYCODE_ prefix. For
     * instance: sendKeys(view, "DPAD_LEFT A B C DPAD_CENTER"). Each key can be repeated by using
     * the N* prefix. For instance, to send two KEYCODE_DPAD_LEFT, use the following:
     * sendKeys(view, "2*DPAD_LEFT").
     *
     * @param instrumentation the instrumentation used to run the test.
     * @param targetView Ignored.
     * @param keysSequence The sequence of keys.
     */
    public void sendKeys(Instrumentation instrumentation, View targetView, String keysSequence) {
        sendKeys(instrumentation, keysSequence);
    }

    /**
     * Sends a series of key events through instrumentation. The sequence of keys is a string
     * containing the key names as specified in KeyEvent, without the KEYCODE_ prefix. For
     * instance: sendKeys(view, "DPAD_LEFT A B C DPAD_CENTER"). Each key can be repeated by using
     * the N* prefix. For instance, to send two KEYCODE_DPAD_LEFT, use the following:
     * sendKeys(view, "2*DPAD_LEFT").
     *
     * @param instrumentation the instrumentation used to run the test.
     * @param keysSequence The sequence of keys.
     */
    public void sendKeys(Instrumentation instrumentation, String keysSequence) {
        final String[] keys = keysSequence.split(" ");
        final int count = keys.length;

        for (int i = 0; i < count; i++) {
            String key = keys[i];
            int repeater = key.indexOf('*');

            int keyCount;
            try {
                keyCount = repeater == -1 ? 1 : Integer.parseInt(key.substring(0, repeater));
            } catch (NumberFormatException e) {
                Log.w("ActivityTestCase", "Invalid repeat count: " + key);
                continue;
            }

            if (repeater != -1) {
                key = key.substring(repeater + 1);
            }

            for (int j = 0; j < keyCount; j++) {
                try {
                    final Field keyCodeField = KeyEvent.class.getField("KEYCODE_" + key);
                    final int keyCode = keyCodeField.getInt(null);
                    try {
                        sendKeyDownUp(instrumentation, keyCode);
                    } catch (SecurityException e) {
                        // Ignore security exceptions that are now thrown
                        // when trying to send to another app, to retain
                        // compatibility with existing tests.
                    }
                } catch (NoSuchFieldException e) {
                    Log.w("ActivityTestCase", "Unknown keycode: KEYCODE_" + key);
                    break;
                } catch (IllegalAccessException e) {
                    Log.w("ActivityTestCase", "Unknown keycode: KEYCODE_" + key);
                    break;
                }
            }
        }
    }

    /**
     * Sends an up and down key events.
     *
     * @param instrumentation the instrumentation used to run the test.
     * @param targetView Ignored.
     * @param key The integer keycode for the event to be sent.
     * @deprecated Use {@link #sendKeyDownUp(Instrumentation, int)} instead.
     */
    @Deprecated
    @InlineMe(replacement = "this.sendKeyDownUp(instrumentation, key)")
    public void sendKeyDownUp(Instrumentation instrumentation, View targetView, int key) {
        sendKeyDownUp(instrumentation, key);
    }

    /**
     * Sends an up and down key events.
     *
     * @param instrumentation the instrumentation used to run the test.
     * @param key The integer keycode for the event to be sent.
     */
    public void sendKeyDownUp(Instrumentation instrumentation, int key) {
        sendKey(instrumentation, new KeyEvent(KeyEvent.ACTION_DOWN, key), false /* waitForIdle */);
        sendKey(instrumentation, new KeyEvent(KeyEvent.ACTION_UP, key));
    }

    /**
     * Sends a key event.
     *
     * @param instrumentation the instrumentation used to run the test.
     * @param targetView Ignored.
     * @param event KeyEvent to be sent.
     * @deprecated Use {@link #sendKey(Instrumentation, KeyEvent)} instead.
     */
    @Deprecated
    @InlineMe(replacement = "this.sendKey(instrumentation, event)")
    public void sendKey(Instrumentation instrumentation, View targetView, KeyEvent event) {
        sendKey(instrumentation, event);
    }

    /**
     * Sends a key event.
     *
     * @param instrumentation the instrumentation used to run the test.
     * @param event KeyEvent to be sent.
     */
    public void sendKey(Instrumentation instrumentation, KeyEvent event) {
        sendKey(instrumentation, event, true /* waitForIdle */);
    }

    private void sendKey(Instrumentation instrumentation, KeyEvent event, boolean waitForIdle) {
        validateNotAppThread();

        mUserHelper.injectDisplayIdIfNeeded(event);

        long downTime = event.getDownTime();
        long eventTime = event.getEventTime();
        int action = event.getAction();
        int code = event.getKeyCode();
        int repeatCount = event.getRepeatCount();
        int metaState = event.getMetaState();
        int deviceId = event.getDeviceId();
        int scanCode = event.getScanCode();
        int source = event.getSource();
        int flags = event.getFlags();
        if (source == InputDevice.SOURCE_UNKNOWN) {
            source = InputDevice.SOURCE_KEYBOARD;
        }
        if (eventTime == 0) {
            eventTime = SystemClock.uptimeMillis();
        }
        if (downTime == 0) {
            downTime = eventTime;
        }

        final KeyEvent newEvent = new KeyEvent(downTime, eventTime, action, code, repeatCount,
                metaState, deviceId, scanCode, flags, source);

        instrumentation.sendKeySync(newEvent);
        if (waitForIdle) {
            instrumentation.waitForIdleSync();
        }
    }

    /**
     * Sends a key event while holding another modifier key down, then releases both keys and
     * waits for idle sync. Useful for sending combinations like shift + tab.
     *
     * @param instrumentation the instrumentation used to run the test.
     * @param targetView Ignored.
     * @param keyCodeToSend The integer keycode for the event to be sent.
     * @param modifierKeyCodeToHold The integer keycode of the modifier to be held.
     * @deprecated Use {@link #sendKeyWhileHoldingModifier(Instrumentation, int, int)} instead.
     */
    @Deprecated
    @InlineMe(replacement = "this.sendKeyWhileHoldingModifier(instrumentation, keyCodeToSend, "
            + "modifierKeyCodeToHold)")
    public void sendKeyWhileHoldingModifier(Instrumentation instrumentation, View targetView,
            int keyCodeToSend, int modifierKeyCodeToHold) {
        sendKeyWhileHoldingModifier(instrumentation, keyCodeToSend, modifierKeyCodeToHold);
    }

    /**
     * Sends a key event while holding another modifier key down, then releases both keys and
     * waits for idle sync. Useful for sending combinations like shift + tab.
     *
     * @param instrumentation the instrumentation used to run the test.
     * @param keyCodeToSend The integer keycode for the event to be sent.
     * @param modifierKeyCodeToHold The integer keycode of the modifier to be held.
     */
    public void sendKeyWhileHoldingModifier(Instrumentation instrumentation, int keyCodeToSend,
            int modifierKeyCodeToHold) {
        final int metaState = getMetaStateForModifierKeyCode(modifierKeyCodeToHold);
        final long downTime = SystemClock.uptimeMillis();

        final KeyEvent holdKeyDown = new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
                modifierKeyCodeToHold, 0 /* repeat */);
        sendKey(instrumentation, holdKeyDown);

        final KeyEvent keyDown = new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
                keyCodeToSend, 0 /* repeat */, metaState);
        sendKey(instrumentation, keyDown);

        final KeyEvent keyUp = new KeyEvent(downTime, downTime, KeyEvent.ACTION_UP,
                keyCodeToSend, 0 /* repeat */, metaState);
        sendKey(instrumentation, keyUp);

        final KeyEvent holdKeyUp = new KeyEvent(downTime, downTime, KeyEvent.ACTION_UP,
                modifierKeyCodeToHold, 0 /* repeat */);
        sendKey(instrumentation, holdKeyUp);

        instrumentation.waitForIdleSync();
    }

    private static int getMetaStateForModifierKeyCode(int modifierKeyCode) {
        if (!KeyEvent.isModifierKey(modifierKeyCode)) {
            throw new IllegalArgumentException("Modifier key expected, but got: "
                    + KeyEvent.keyCodeToString(modifierKeyCode));
        }

        int metaState;
        switch (modifierKeyCode) {
            case KeyEvent.KEYCODE_SHIFT_LEFT:
                metaState = KeyEvent.META_SHIFT_LEFT_ON;
                break;
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
                metaState = KeyEvent.META_SHIFT_RIGHT_ON;
                break;
            case KeyEvent.KEYCODE_ALT_LEFT:
                metaState = KeyEvent.META_ALT_LEFT_ON;
                break;
            case KeyEvent.KEYCODE_ALT_RIGHT:
                metaState = KeyEvent.META_ALT_RIGHT_ON;
                break;
            case KeyEvent.KEYCODE_CTRL_LEFT:
                metaState = KeyEvent.META_CTRL_LEFT_ON;
                break;
            case KeyEvent.KEYCODE_CTRL_RIGHT:
                metaState = KeyEvent.META_CTRL_RIGHT_ON;
                break;
            case KeyEvent.KEYCODE_META_LEFT:
                metaState = KeyEvent.META_META_LEFT_ON;
                break;
            case KeyEvent.KEYCODE_META_RIGHT:
                metaState = KeyEvent.META_META_RIGHT_ON;
                break;
            case KeyEvent.KEYCODE_SYM:
                metaState = KeyEvent.META_SYM_ON;
                break;
            case KeyEvent.KEYCODE_NUM:
                metaState = KeyEvent.META_NUM_LOCK_ON;
                break;
            case KeyEvent.KEYCODE_FUNCTION:
                metaState = KeyEvent.META_FUNCTION_ON;
                break;
            default:
                // Safety net: all modifier keys need to have at least one meta state associated.
                throw new UnsupportedOperationException("No meta state associated with "
                        + "modifier key: " + KeyEvent.keyCodeToString(modifierKeyCode));
        }

        return KeyEvent.normalizeMetaState(metaState);
    }

    private static KeyEvent[] getKeyEvents(final String text) {
        KeyCharacterMap keyCharacterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
        return keyCharacterMap.getEvents(text.toCharArray());
    }

    private static void validateNotAppThread() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new RuntimeException(
                    "This method can not be called from the main application thread");
        }
    }
}
