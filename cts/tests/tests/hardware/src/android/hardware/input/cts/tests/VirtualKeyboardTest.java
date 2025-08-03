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

import static org.junit.Assert.assertThrows;

import android.hardware.input.VirtualKeyEvent;
import android.hardware.input.VirtualKeyboard;
import android.hardware.input.cts.virtualcreators.VirtualInputDeviceCreator;
import android.hardware.input.cts.virtualcreators.VirtualInputEventCreator;
import android.view.KeyEvent;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class VirtualKeyboardTest extends VirtualDeviceTestCase {

    private static final String DEVICE_NAME = "CtsVirtualKeyboardTestDevice";
    private VirtualKeyboard mVirtualKeyboard;

    @Override
    void onSetUpVirtualInputDevice() {
        mVirtualKeyboard = VirtualInputDeviceCreator.createAndPrepareKeyboard(mVirtualDevice,
                DEVICE_NAME, mVirtualDisplay.getDisplay()).getDevice();
    }

    @Test
    public void sendKeyEvent() {
        mVirtualKeyboard.sendKeyEvent(
                new VirtualKeyEvent.Builder()
                        .setKeyCode(KeyEvent.KEYCODE_A)
                        .setAction(VirtualKeyEvent.ACTION_DOWN)
                        .build());
        mVirtualKeyboard.sendKeyEvent(
                new VirtualKeyEvent.Builder()
                        .setKeyCode(KeyEvent.KEYCODE_A)
                        .setAction(VirtualKeyEvent.ACTION_UP)
                        .build());
        verifyEvents(
                Arrays.asList(VirtualInputEventCreator.createKeyboardEvent(KeyEvent.ACTION_DOWN,
                                KeyEvent.KEYCODE_A),
                        VirtualInputEventCreator.createKeyboardEvent(KeyEvent.ACTION_UP,
                                KeyEvent.KEYCODE_A)));
    }

    @Test
    public void sendKeyEvent_withoutCreateVirtualDevicePermission_throwsException() {
        mRule.runWithoutPermissions(
                () -> assertThrows(SecurityException.class,
                        () -> mVirtualKeyboard.sendKeyEvent(
                                new VirtualKeyEvent.Builder()
                                        .setKeyCode(KeyEvent.KEYCODE_DPAD_UP)
                                        .setAction(VirtualKeyEvent.ACTION_DOWN)
                                        .build())));
    }

    @Test
    public void keyEvent_nullEvent_throwsNpe() {
        assertThrows(NullPointerException.class, () -> mVirtualKeyboard.sendKeyEvent(null));
    }

    @Test
    public void rejectsUnsupportedKeyCodes() {
        assertThrows(IllegalArgumentException.class,
                () -> mVirtualKeyboard.sendKeyEvent(
                        new VirtualKeyEvent.Builder()
                                .setKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
                                .setAction(VirtualKeyEvent.ACTION_DOWN)
                                .build()));
    }

    @Test
    public void createVirtualKeyboard_nullArguments_throwsException() {
        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.createVirtualKeyboard(null));
    }
}
