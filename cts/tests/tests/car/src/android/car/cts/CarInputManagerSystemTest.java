/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.car.cts;

import static android.car.CarOccupantZoneManager.DISPLAY_TYPE_MAIN;

import static com.android.compatibility.common.util.SystemUtil.eventually;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.car.Car;
import android.car.input.CarInputManager;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.os.UserManager;
import android.support.test.uiautomator.UiDevice;
import android.view.KeyEvent;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class CarInputManagerSystemTest {

    private static final long MAX_WAIT_TIMEOUT_MS = 5_000;

    private Context mContext;
    private Instrumentation mInstrumentation;
    private UiAutomation mUiAutomation;
    private UiDevice mDevice;
    private CarInputManager mCarInputManager;
    private UserManager mUserManager;

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getContext();
        mUserManager = mContext.getSystemService(UserManager.class);

        mDevice = UiDevice.getInstance(mInstrumentation);
        mUiAutomation = mInstrumentation.getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity("android.permission.INJECT_EVENTS");

        Car car = Car.createCar(mContext);
        mCarInputManager = (CarInputManager) car.getCarManager(Car.CAR_INPUT_SERVICE);
        assertThat(mCarInputManager).isNotNull();
    }

    @After
    public void tearDown() {
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void testCarInputService_injectKeyEvent() throws Exception {
        // CarInputManager#injectKeyEvent is introduced in Android 12
        assumeTrue("Test requires at least Android 12 to run",
                ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S));
        assumeTrue("This test expects the device to be awake", mDevice.isScreenOn());

        // TODO(b/284220186): Remove this assumption check on this bug is fixed.
        assumeFalse("This test is disabled on multi-user/multi-display devices",
                mUserManager.isVisibleBackgroundUsersSupported());

        injectKey(KeyEvent.KEYCODE_SLEEP);
        waitUntilScreenOnIs(false);

        injectKey(KeyEvent.KEYCODE_WAKEUP);
        waitUntilScreenOnIs(true);
    }

    private void waitUntilScreenOnIs(boolean expected) {
        eventually(() -> assertThat(mDevice.isScreenOn()).isEqualTo(expected), MAX_WAIT_TIMEOUT_MS);
    }

    private void injectKey(int keyCode) {
        long currentTime = SystemClock.uptimeMillis();
        KeyEvent keyDown = new KeyEvent(/* downTime= */ currentTime, /* eventTime= */ currentTime,
                KeyEvent.ACTION_DOWN, keyCode, /* repeat= */ 0);
        mCarInputManager.injectKeyEvent(keyDown, DISPLAY_TYPE_MAIN);

        KeyEvent keyUp = new KeyEvent(/* downTime= */ currentTime, /* eventTime= */ currentTime,
                KeyEvent.ACTION_UP, keyCode, /* repeat= */ 0);
        mCarInputManager.injectKeyEvent(keyUp, DISPLAY_TYPE_MAIN);
    }
}

