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

package android.car.cts.builtin.power;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.car.builtin.power.PowerManagerHelper;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.view.Display;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class PowerManagerHelperTest {

    private static final String TAG = PowerManagerHelperTest.class.getSimpleName();

    private Context mContext;
    private UiAutomation mUiAutomation;

    @Before
    public void setUp() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = instrumentation.getContext();
        mUiAutomation = instrumentation.getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.DEVICE_POWER);
    }

    @After
    public void tearDown() {
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void testSetDisplayState() {
        PowerManager powerManager = mContext.getSystemService(PowerManager.class);

        PowerManagerHelper.setDisplayState(mContext, /* on= */ true, SystemClock.uptimeMillis());
        assertWithMessage("Screen on").that(powerManager.isInteractive()).isTrue();

        PowerManagerHelper.setDisplayState(mContext, /* on= */ false,
                SystemClock.uptimeMillis());
        assertWithMessage("Screen on").that(powerManager.isInteractive()).isFalse();

        PowerManagerHelper.setDisplayState(mContext, /* on= */ true, SystemClock.uptimeMillis());
        assertWithMessage("Screen on").that(powerManager.isInteractive()).isTrue();
    }

    @Test
    public void testNewWakeLock() {
        DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);
        Display[] displays = displayManager.getDisplays();

        for (Display display : displays) {
            int displayId = display.getDisplayId();

            WakeLock wakeLock = PowerManagerHelper.newWakeLock(mContext,
                    PowerManager.PARTIAL_WAKE_LOCK, TAG, displayId);
            wakeLock.acquire();

            assertWithMessage("Wake lock for display " + displayId).that(wakeLock.isHeld())
                    .isTrue();

            wakeLock.release();

            assertWithMessage("Wake lock for display " + displayId).that(wakeLock.isHeld())
                    .isFalse();
        }
    }
}
