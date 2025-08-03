/*
 * Copyright 2023 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;

import android.companion.virtual.VirtualDeviceManager;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.input.InputManager;
import android.hardware.input.VirtualKeyboard;
import android.hardware.input.cts.virtualcreators.VirtualInputDeviceCreator;
import android.provider.Settings;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class VirtualKeyboardLayoutTest {

    private static final String DEVICE_NAME = "CtsVirtualKeyboardTestDevice";

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    private Context mContext;
    private InputManager mInputManager;
    private VirtualDeviceManager.VirtualDevice mVirtualDevice;
    private VirtualDisplay mVirtualDisplay;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mInputManager = mContext.getSystemService(InputManager.class);
        Settings.Global.putString(
                mContext.getContentResolver(), "settings_new_keyboard_ui", "true");
        mVirtualDevice = mRule.createManagedVirtualDevice();
        mVirtualDisplay = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED);
    }

    @After
    public void tearDown() {
        Settings.Global.putString(mContext.getContentResolver(), "settings_new_keyboard_ui", "");
    }

    @Test
    public void createVirtualKeyboard_layoutSelected() {
        InputDevice frenchKeyboard =
                createVirtualKeyboard("fr-Latn-FR", "azerty");
        assertKeyMappings(
                frenchKeyboard,
                new int[]{
                        KeyEvent.KEYCODE_Q,
                        KeyEvent.KEYCODE_W,
                        KeyEvent.KEYCODE_E,
                        KeyEvent.KEYCODE_R,
                        KeyEvent.KEYCODE_T,
                        KeyEvent.KEYCODE_Y
                },
                new int[]{
                        KeyEvent.KEYCODE_A,
                        KeyEvent.KEYCODE_Z,
                        KeyEvent.KEYCODE_E,
                        KeyEvent.KEYCODE_R,
                        KeyEvent.KEYCODE_T,
                        KeyEvent.KEYCODE_Y
                });

        InputDevice swissGermanKeyboard =
                createVirtualKeyboard("de-CH", "qwertz");
        assertKeyMappings(
                swissGermanKeyboard,
                new int[]{
                        KeyEvent.KEYCODE_Q,
                        KeyEvent.KEYCODE_W,
                        KeyEvent.KEYCODE_E,
                        KeyEvent.KEYCODE_R,
                        KeyEvent.KEYCODE_T,
                        KeyEvent.KEYCODE_Y
                },
                new int[]{
                        KeyEvent.KEYCODE_Q,
                        KeyEvent.KEYCODE_W,
                        KeyEvent.KEYCODE_E,
                        KeyEvent.KEYCODE_R,
                        KeyEvent.KEYCODE_T,
                        KeyEvent.KEYCODE_Z
                });
    }

    @Test
    public void createVirtualKeyboard_layoutSelected_differentLayoutType() {
        InputDevice qwertyKeyboard =
                createVirtualKeyboard("en-Latn-US", "qwerty");
        assertKeyMappings(
                qwertyKeyboard,
                new int[]{
                        KeyEvent.KEYCODE_Q,
                        KeyEvent.KEYCODE_W,
                        KeyEvent.KEYCODE_E,
                        KeyEvent.KEYCODE_R,
                        KeyEvent.KEYCODE_T,
                        KeyEvent.KEYCODE_Y
                },
                new int[]{
                        KeyEvent.KEYCODE_Q,
                        KeyEvent.KEYCODE_W,
                        KeyEvent.KEYCODE_E,
                        KeyEvent.KEYCODE_R,
                        KeyEvent.KEYCODE_T,
                        KeyEvent.KEYCODE_Y
                });

        InputDevice dvorakKeyboard =
                createVirtualKeyboard("en-Latn-US", "dvorak");
        assertKeyMappings(
                dvorakKeyboard,
                new int[]{
                        KeyEvent.KEYCODE_Q,
                        KeyEvent.KEYCODE_W,
                        KeyEvent.KEYCODE_E,
                        KeyEvent.KEYCODE_R,
                        KeyEvent.KEYCODE_T,
                        KeyEvent.KEYCODE_Y
                },
                new int[]{
                        KeyEvent.KEYCODE_APOSTROPHE,
                        KeyEvent.KEYCODE_COMMA,
                        KeyEvent.KEYCODE_E,
                        KeyEvent.KEYCODE_P,
                        KeyEvent.KEYCODE_Y,
                        KeyEvent.KEYCODE_F
                });
    }

    private static void assertKeyMappings(InputDevice device, int[] fromKeys, int[] toKeys) {
        for (int i = 0; i < fromKeys.length; i++) {
            assertEquals(
                    "Key location "
                            + KeyEvent.keyCodeToString(fromKeys[i])
                            + " should map to "
                            + KeyEvent.keyCodeToString(toKeys[i])
                            + " on a "
                            + device.getKeyboardLanguageTag()
                            + "/"
                            + device.getKeyboardLayoutType()
                            + " layout.",
                    device.getKeyCodeForKeyLocation(fromKeys[i]),
                    toKeys[i]);
        }
    }

    private InputDevice createVirtualKeyboard(String languageTag, String layoutType) {
        VirtualKeyboard virtualKeyboard =  VirtualInputDeviceCreator.createAndPrepareKeyboard(
                mVirtualDevice, DEVICE_NAME + "/" + languageTag + "/" + layoutType,
                mVirtualDisplay.getDisplay(), languageTag, layoutType).getDevice();
        InputDevice inputDevice = mInputManager.getInputDevice(virtualKeyboard.getInputDeviceId());
        assertThat(inputDevice).isNotNull();
        return inputDevice;
    }
}
