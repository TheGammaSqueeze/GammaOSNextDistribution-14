/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.documentsui;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static java.util.Objects.requireNonNull;

import android.app.Instrumentation;
import android.content.Context;
import android.view.KeyEvent;

import androidx.test.uiautomator.UiDevice;

import com.android.documentsui.bots.Bots;

import java.io.IOException;


/** Base class for instrumentation tests for DocumentsUI Activities. */
class DocumentsUiTestBase {
    private static final int BOTS_TIMEOUT = 5000; // 5 seconds

    protected Context targetContext;
    protected Context context;
    protected UiDevice device;
    protected Bots bots;

    private String initialScreenOffTimeoutValue = null;
    private String initialSleepTimeoutValue = null;

    protected void setUp() throws Exception {
        final Instrumentation instrumentation = getInstrumentation();
        targetContext = instrumentation.getTargetContext();
        context = instrumentation.getContext();
        device = UiDevice.getInstance(instrumentation);

        disableScreenOffAndSleepTimeouts();

        device.setOrientationNatural();

        // "Wake-up" the device and navigate to the home page.
        device.pressKeyCode(KeyEvent.KEYCODE_WAKEUP);
        device.pressKeyCode(KeyEvent.KEYCODE_MENU);

        bots = new Bots(device, instrumentation.getUiAutomation(), targetContext, BOTS_TIMEOUT);
    }

    protected void tearDown() throws Exception {
        restoreScreenOffAndSleepTimeouts();
    }

    private void disableScreenOffAndSleepTimeouts() throws IOException {
        initialScreenOffTimeoutValue = device.executeShellCommand(
                "settings get system screen_off_timeout");
        initialSleepTimeoutValue = device.executeShellCommand(
                "settings get secure sleep_timeout");
        device.executeShellCommand("settings put system screen_off_timeout -1");
        device.executeShellCommand("settings put secure sleep_timeout -1");
    }

    private void restoreScreenOffAndSleepTimeouts() throws IOException {
        requireNonNull(initialScreenOffTimeoutValue);
        requireNonNull(initialSleepTimeoutValue);
        try {
            device.executeShellCommand(
                    "settings put system screen_off_timeout " + initialScreenOffTimeoutValue);
            device.executeShellCommand(
                    "settings put secure sleep_timeout " + initialSleepTimeoutValue);
        } finally {
            initialScreenOffTimeoutValue = null;
            initialSleepTimeoutValue = null;
        }
    }
}
