/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.cts.deviceandprofileowner;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.admin.DevicePolicyManager;
import android.graphics.Bitmap;
import android.util.Log;

/**
 * Tests for {@link DevicePolicyManager#setScreenCaptureDisabled} and
 * {@link DevicePolicyManager#getScreenCaptureDisabled} APIs.
 */
public final class ScreenCaptureDisabledTest extends BaseDeviceAdminTest {
    private final int MAX_ATTEMPTS_COUNT = 20;
    private final int WAIT_IN_MILLISECOND = 500;

    private static final String TAG = ScreenCaptureDisabledTest.class.getSimpleName();

    public void testSetScreenCaptureDisabled_false() {
        mDevicePolicyManager.setScreenCaptureDisabled(ADMIN_RECEIVER_COMPONENT, false);
        assertWithMessage("dpm.getScreenCaptureDisabled(%s)",
                ADMIN_RECEIVER_COMPONENT.flattenToShortString())
                    .that(mDevicePolicyManager.getScreenCaptureDisabled(ADMIN_RECEIVER_COMPONENT))
                    .isFalse();
        assertWithMessage("dpm.getScreenCaptureDisabled(null)")
                .that(mDevicePolicyManager.getScreenCaptureDisabled(/* admin= */ null)).isFalse();
    }

    public void testSetScreenCaptureDisabled_true() {
        mDevicePolicyManager.setScreenCaptureDisabled(ADMIN_RECEIVER_COMPONENT, true);
        assertWithMessage("dpm.getScreenCaptureDisabled(%s)",
                ADMIN_RECEIVER_COMPONENT.flattenToShortString())
                     .that(mDevicePolicyManager.getScreenCaptureDisabled(ADMIN_RECEIVER_COMPONENT))
                     .isTrue();
        assertWithMessage("dpm.getScreenCaptureDisabled(null)")
                .that(mDevicePolicyManager.getScreenCaptureDisabled(/* admin= */ null)).isTrue();
    }

    public void testScreenCaptureImpossible() throws Exception {
        Bitmap screenshot = null;
        for (int i = 1; i <= MAX_ATTEMPTS_COUNT; i++) {
            Log.d(TAG, "testScreenCaptureImpossible(): attempt #" + i);
            sleep(WAIT_IN_MILLISECOND);
            screenshot = takeScreenshot();
            if (screenshot == null) break;
        }
        assertWithMessage("screenshot").that(screenshot).isNull();
    }

    public void testScreenCapturePossible() throws Exception {
        Bitmap screenshot = null;
        for (int i = 1; i <= MAX_ATTEMPTS_COUNT; i++) {
            Log.d(TAG, "testScreenCapturePossible): attempt #" + i);
            sleep(WAIT_IN_MILLISECOND);
            screenshot = takeScreenshot();
            if (screenshot != null) break;
        }
        assertWithMessage("screenshot").that(screenshot).isNotNull();
    }

    private Bitmap takeScreenshot() {
        Bitmap screenshot = getInstrumentation().getUiAutomation().takeScreenshot();
        Log.d(TAG, "takeScreenshot(): got " + screenshot);
        return screenshot;
    }
}
