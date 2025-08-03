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

package com.android.server.inputmethod;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.Condition;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

/**
 * This is a simple smoke test to help finding culprit CLs when using bisect.
 */
public final class ImeSmokeTest {

    private static final long KEYBOARD_LAUNCH_TIMEOUT = 5_000;

    private static final long SWITCH_TO_HARD_IME_TIMEOUT_SECONDS = 5_000;

    private static final String PLAIN_TEXT_EDIT_RESOURCE_ID =
            "com.google.android.car.kitchensink:id/plain_text_edit";

    private static final String KITCHEN_SINK_APP =
            "com.google.android.car.kitchensink";

    // Values of setting key SHOW_IME_WITH_HARD_KEYBOARD settings.
    private static final int STATE_HIDE_IME = 0;
    private static final int STATE_SHOW_IME = 1;

    private static Instrumentation sInstrumentation;
    private static UiDevice sDevice;
    private static Context sContext;
    private static ContentResolver sContentResolver;
    private static int sOriginalShowImeWithHardKeyboard;

    @BeforeClass
    public static void setUpClass() throws Exception {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sDevice = UiDevice.getInstance(sInstrumentation);
        sContext = sInstrumentation.getContext();
        sContentResolver = sContext.getContentResolver();

        // Set this test to run on auto only, it was mostly designed to capture configuration
        // issues on auto keyboards.
        assumeTrue(sContext.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));

        // Ensure that the DUT doesn't have hard keyboard enabled.
        sOriginalShowImeWithHardKeyboard = Settings.Secure.getInt(sContentResolver,
                Settings.Secure.SHOW_IME_WITH_HARD_KEYBOARD, /*def=*/STATE_SHOW_IME);
        if (sOriginalShowImeWithHardKeyboard == STATE_HIDE_IME) {
            assertThat(Settings.Secure.putInt(
                    sContentResolver, Settings.Secure.SHOW_IME_WITH_HARD_KEYBOARD,
                    /*def=*/STATE_SHOW_IME)).isTrue();

            // Give 5 seconds for IME to properly act on the settings change.
            // TODO(b/301521594): Instead of sleeping, just verify the mShowImeWithHardKeyboard
            // field from the current IME in IMMS.
            SystemClock.sleep(SWITCH_TO_HARD_IME_TIMEOUT_SECONDS);
        }
    }

    @Before
    public void setUp() throws IOException {
        closeKitchenSink();
    }

    @After
    public void tearDown() throws IOException {
        closeKitchenSink();
    }

    @AfterClass
    public static void tearDownClass() {
        // Change back the original value of show_ime_with_hard_keyboard in Settings.
        if (sOriginalShowImeWithHardKeyboard == STATE_HIDE_IME) {
            assertThat(Settings.Secure.putInt(
                    sContentResolver, Settings.Secure.SHOW_IME_WITH_HARD_KEYBOARD,
                    /*def=*/STATE_HIDE_IME)).isTrue();
        }
    }

    private void closeKitchenSink() throws IOException {
        sDevice.executeShellCommand(String.format("am force-stop %s", KITCHEN_SINK_APP));
    }

    @Test
    public void canOpenIME() throws UiObjectNotFoundException {
        // Open KitchenSink > Carboard
        Intent intent = sInstrumentation
                .getContext()
                .getPackageManager()
                .getLaunchIntentForPackage(KITCHEN_SINK_APP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("select", "carboard");
        sContext.startActivity(intent);

        UiObject editText = sDevice.findObject((new UiSelector().resourceId(
                PLAIN_TEXT_EDIT_RESOURCE_ID)));
        editText.click();

        assertThat(sDevice.wait(isKeyboardOpened(), KEYBOARD_LAUNCH_TIMEOUT)).isTrue();
    }

    private static Condition<UiDevice, Boolean> isKeyboardOpened() {
        return unusedDevice -> {
            for (AccessibilityWindowInfo window : sInstrumentation.getUiAutomation().getWindows()) {
                if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    return true;
                }
            }
            return false;
        };
    }
}
