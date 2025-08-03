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
package android.car.cts.permissiontest.diagnostic;

import static android.car.Car.DIAGNOSTIC_SERVICE;
import static android.car.diagnostic.CarDiagnosticManager.FRAME_TYPE_LIVE;

import static org.junit.Assert.assertThrows;

import android.app.UiAutomation;
import android.car.Car;
import android.car.diagnostic.CarDiagnosticEvent;
import android.car.diagnostic.CarDiagnosticManager;
import android.car.diagnostic.CarDiagnosticManager.OnDiagnosticEventListener;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import android.car.cts.permissiontest.AbstractCarManagerPermissionTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This class contains security permission tests for {@link CarDiagnosticManager}.
 */
@RunWith(AndroidJUnit4.class)
public class CarDiagnosticManagerPermissionTest extends AbstractCarManagerPermissionTest {
    private final UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    @Before
    public void setUp() {
        super.connectCar();
    }

    @Test
    public void testGetCarDiagnosticManager() {
        assertThrows(SecurityException.class, () -> mCar.getCarManager(DIAGNOSTIC_SERVICE));
    }

    private CarDiagnosticManager getCarDiagnosticManager() {
        return (CarDiagnosticManager) mCar.getCarManager(DIAGNOSTIC_SERVICE);
    }

    @Test
    public void testRegisterUnregisterListenerWithCorrectPermission() {
        mUiAutomation.adoptShellPermissionIdentity(Car.PERMISSION_CAR_DIAGNOSTIC_READ_ALL);

        CarDiagnosticManager manager = getCarDiagnosticManager();
        OnDiagnosticEventListener listener = (CarDiagnosticEvent carDiagnosticEvent) -> {};

        manager.registerListener(listener, FRAME_TYPE_LIVE, /* rate= */ 1);
        manager.unregisterListener(listener);

        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void testRegisterListenerWithWrongPermission() {
        mUiAutomation.adoptShellPermissionIdentity(Car.PERMISSION_CAR_DIAGNOSTIC_CLEAR);

        CarDiagnosticManager manager = getCarDiagnosticManager();
        OnDiagnosticEventListener listener = (CarDiagnosticEvent carDiagnosticEvent) -> {};

        assertThrows(SecurityException.class, () -> manager.registerListener(listener,
                FRAME_TYPE_LIVE, /* rate= */ 1));

        // unregisterListener does not require permission, so we skip the check here.

        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void testClearFreezeFrameWithCorrectPermission() {
        mUiAutomation.adoptShellPermissionIdentity(Car.PERMISSION_CAR_DIAGNOSTIC_CLEAR);
        CarDiagnosticManager manager = getCarDiagnosticManager();

        manager.clearFreezeFrames(/* timestamps= */ 0);

        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void testClearFreezeFrameWithWrongPermission() {
        mUiAutomation.adoptShellPermissionIdentity(Car.PERMISSION_CAR_DIAGNOSTIC_READ_ALL);
        CarDiagnosticManager manager = getCarDiagnosticManager();

        assertThrows(SecurityException.class, () -> manager.clearFreezeFrames(/* timestamps= */ 0));

        mUiAutomation.dropShellPermissionIdentity();
    }
}
