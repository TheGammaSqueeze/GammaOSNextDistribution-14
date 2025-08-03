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
package android.car.cts.permissiontest.input;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.cts.permissiontest.AbstractCarManagerPermissionTest;
import android.car.input.CarInputManager;
import android.os.SystemClock;
import android.view.KeyEvent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.Executor;

/**
 * This class contains security permission tests for the {@link CarInputManager}'s system APIs.
 */
@RunWith(MockitoJUnitRunner.class)
public class CarInputManagerPermissionTest extends AbstractCarManagerPermissionTest {

    private CarInputManager mCarInputManager;

    @Mock
    private CarInputManager.CarInputCaptureCallback mMockedCallback;

    @Before
    public void setUp() {
        super.connectCar();
        mCarInputManager = (CarInputManager) mCar.getCarManager(Car.CAR_INPUT_SERVICE);
        assertThat(mCarInputManager).isNotNull();
    }

    @Test
    public void testRequestInputEventCapturePermission() {
        assertThrows(SecurityException.class, () -> mCarInputManager.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION}, 0, mMockedCallback));
    }

    @Test
    public void testRequestInputEventCaptureWithExecutorPermission() {
        assertThrows(SecurityException.class, () -> mCarInputManager.requestInputEventCapture(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
                new int[]{CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION}, 0,
                mock(Executor.class), mMockedCallback));
    }

    @Test
    public void testInjectKeyEventPermission() {
        long currentTime = SystemClock.uptimeMillis();
        KeyEvent anyKeyEvent = new KeyEvent(/* downTime= */ currentTime,
                /* eventTime= */ currentTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME,
                /* repeat= */ 0);

        SecurityException thrown = assertThrows(SecurityException.class,
                () -> mCarInputManager.injectKeyEvent(anyKeyEvent,
                        CarOccupantZoneManager.DISPLAY_TYPE_MAIN));
        assertThat(thrown.getMessage()).isEqualTo(
                "Injecting KeyEvent requires INJECT_EVENTS permission");
    }
}
