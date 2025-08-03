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

package android.hardware.input.cts.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.cts.R;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.InputDevice;
import android.view.KeyEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.atomic.AtomicInteger;

@ApiTest(apis = {"android.view.InputDevice#getKeyCodeForKeyLocation"})
@SmallTest
@RunWith(AndroidJUnit4.class)
public class KeyboardLayoutChangeTest extends InputHidTestCase {

    private static final long KEYBOARD_LAYOUT_CHANGE_TIMEOUT = 5000;

    private InputManager mInputManager;
    private InputDevice mInputDevice;
    @Mock private InputManager.InputDeviceListener mInputDeviceChangedListener;
    private final AtomicInteger mInputDeviceChangeCount = new AtomicInteger(0);

    // this test needs any physical keyboard to test the keyboard layout change
    public KeyboardLayoutChangeTest() {
        super(R.raw.microsoft_designer_keyboard_register);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        setNewSettingsUiFlag(mInstrumentation.getTargetContext(), "false");
        MockitoAnnotations.initMocks(this);
        mInputManager = mInstrumentation.getTargetContext().getSystemService(InputManager.class);
        assertNotNull(mInputManager);
        mInputManager.registerInputDeviceListener(mInputDeviceChangedListener,
                new Handler(Looper.getMainLooper()));
        mInputDevice = getInputDevice(
                (d) -> d.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC);
        assertNotNull(mInputDevice);
        final int deviceId = mInputDevice.getId();
        doAnswer((invocation) -> mInputDeviceChangeCount.incrementAndGet()).when(
                mInputDeviceChangedListener).onInputDeviceChanged(eq(deviceId));
    }

    @Override
    void onTearDown() {
        super.onTearDown();
        if (mInputManager != null) {
            mInputManager.unregisterInputDeviceListener(mInputDeviceChangedListener);
        }
        setNewSettingsUiFlag(mInstrumentation.getTargetContext(), "");
    }

    @Test
    public void testKeyboardLayoutChanges() {
        final String germanLayoutId = getKeyboardLayoutId(mInputDevice, "german");
        final String englishLayoutId = getKeyboardLayoutId(mInputDevice, "english_us");
        final String frenchLayoutId = getKeyboardLayoutId(mInputDevice, "french");
        final String layoutError = "The %s layout descriptor is non-existent / empty.";
        assertNotEquals(String.format(layoutError, "German"), "", germanLayoutId);
        assertNotEquals(String.format(layoutError, "English (US)"), "", englishLayoutId);
        assertNotEquals(String.format(layoutError, "French"), "", frenchLayoutId);
        try {
            setCurrentKeyboardLayout(mInputDevice, germanLayoutId);
            assertEquals("Key location KEYCODE_Q should map to KEYCODE_Q on a German layout.",
                    KeyEvent.KEYCODE_Q, mInputDevice.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_Q));
            assertEquals("Key location KEYCODE_W should map to KEYCODE_W on a German layout.",
                    KeyEvent.KEYCODE_W, mInputDevice.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_W));
            assertEquals("Key location KEYCODE_E should map to KEYCODE_E on a German layout.",
                    KeyEvent.KEYCODE_E, mInputDevice.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_E));
            assertEquals("Key location KEYCODE_R should map to KEYCODE_R on a German layout.",
                    KeyEvent.KEYCODE_R, mInputDevice.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_R));
            assertEquals("Key location KEYCODE_T should map to KEYCODE_T on a German layout.",
                    KeyEvent.KEYCODE_T, mInputDevice.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_T));
            assertEquals("Key location KEYCODE_Y should map to KEYCODE_Z on a German layout.",
                    KeyEvent.KEYCODE_Z, mInputDevice.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_Y));

            setCurrentKeyboardLayout(mInputDevice, englishLayoutId);
            assertEquals(
                    "Key location KEYCODE_Q should map to KEYCODE_Q on an English (US) layout.",
                    KeyEvent.KEYCODE_Q, mInputDevice.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_Q));
            assertEquals(
                    "Key location KEYCODE_W should map to KEYCODE_W on an English (US) layout.",
                    KeyEvent.KEYCODE_W, mInputDevice.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_W));
            assertEquals(
                    "Key location KEYCODE_E should map to KEYCODE_E on an English (US) layout.",
                    KeyEvent.KEYCODE_E, mInputDevice.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_E));
            assertEquals(
                    "Key location KEYCODE_R should map to KEYCODE_R on an English (US) layout.",
                    KeyEvent.KEYCODE_R, mInputDevice.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_R));
            assertEquals(
                    "Key location KEYCODE_T should map to KEYCODE_T on an English (US) layout.",
                    KeyEvent.KEYCODE_T, mInputDevice.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_T));
            assertEquals(
                    "Key location KEYCODE_Y should map to KEYCODE_Y on an English (US) layout.",
                    KeyEvent.KEYCODE_Y, mInputDevice.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_Y));

            setCurrentKeyboardLayout(mInputDevice, frenchLayoutId);
            assertEquals("Key location KEYCODE_Q should map to KEYCODE_A on a French layout.",
                    KeyEvent.KEYCODE_A, mInputDevice.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_Q));
            assertEquals("Key location KEYCODE_W should map to KEYCODE_Z on a French layout.",
                    KeyEvent.KEYCODE_Z, mInputDevice.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_W));
            assertEquals("Key location KEYCODE_E should map to KEYCODE_E on a French layout.",
                    KeyEvent.KEYCODE_E, mInputDevice.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_E));
            assertEquals("Key location KEYCODE_R should map to KEYCODE_R on a French layout.",
                    KeyEvent.KEYCODE_R, mInputDevice.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_R));
            assertEquals("Key location KEYCODE_T should map to KEYCODE_T on a French layout.",
                    KeyEvent.KEYCODE_T, mInputDevice.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_T));
            assertEquals("Key location KEYCODE_Y should map to KEYCODE_Y on a French layout.",
                    KeyEvent.KEYCODE_Y, mInputDevice.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_Y));
        } finally {
            // clean up to make sure this test doesn't affect other test cases
            removeKeyboardLayout(mInputDevice, germanLayoutId);
            removeKeyboardLayout(mInputDevice, englishLayoutId);
            removeKeyboardLayout(mInputDevice, frenchLayoutId);
            assertNull(
                    mInputManager.getCurrentKeyboardLayoutForInputDevice(
                            mInputDevice.getIdentifier()));
        }
    }

    @Test
    public void testGetKeyCodeForKeyLocationWithInvalidKeyCode() {
        assertEquals(KeyEvent.KEYCODE_UNKNOWN, mInputDevice.getKeyCodeForKeyLocation(-10));
        assertEquals(KeyEvent.KEYCODE_UNKNOWN,
                mInputDevice.getKeyCodeForKeyLocation(KeyEvent.getMaxKeyCode() + 1));
    }

    /**
     * Removes the specified keyboard layout for the given input device.
     *
     * @param device The input device for which the specified keyboard layout shall be removed.
     * @param layoutDescriptor the layout descriptor.
     */
    private void removeKeyboardLayout(InputDevice device, String layoutDescriptor) {
        SystemUtil.runWithShellPermissionIdentity(
                () -> mInputManager.removeKeyboardLayoutForInputDevice(device.getIdentifier(),
                        layoutDescriptor), Manifest.permission.SET_KEYBOARD_LAYOUT);
    }

    /**
     * Returns the first matching keyboard layout id that is supported by the provided input device
     * and matches the provided language.
     *
     * @param device The input device for which to query the keyboard layouts.
     * @param language The language to query for.
     * @return The first matching keyboard layout descriptor or an empty string if none was found.
     */
    private String getKeyboardLayoutId(InputDevice device, String language) {
        return SystemUtil.runWithShellPermissionIdentity(() -> {
            for (String kl : mInputManager.getKeyboardLayoutDescriptorsForInputDevice(device)) {
                if (kl.endsWith(language)) {
                    return kl;
                }
            }
            fail("Failed to get keyboard layout for language " + language);
            return "";
        }, Manifest.permission.INTERACT_ACROSS_USERS);
    }

    /**
     * Sets the current keyboard layout for the given input device.
     *
     * @param device The input device for which the current keyboard layout is changed.
     * @param layoutDescriptor The layout to which the current keyboard layout is set to.
     */
    private void setCurrentKeyboardLayout(InputDevice device, String layoutDescriptor) {
        final int currCount = mInputDeviceChangeCount.get();
        SystemUtil.runWithShellPermissionIdentity(
                () -> mInputManager.setCurrentKeyboardLayoutForInputDevice(device.getIdentifier(),
                        layoutDescriptor), Manifest.permission.SET_KEYBOARD_LAYOUT);
        PollingCheck.waitFor(KEYBOARD_LAYOUT_CHANGE_TIMEOUT,
                () -> mInputDeviceChangeCount.get() == currCount + 1);
    }

    public static class CtsKeyboardLayoutProvider extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Nothing to do at this time.
        }
    }

    private static void setNewSettingsUiFlag(Context context, String flag) {
        Settings.Global.putString(context.getContentResolver(), "settings_new_keyboard_ui", flag);
    }
}
