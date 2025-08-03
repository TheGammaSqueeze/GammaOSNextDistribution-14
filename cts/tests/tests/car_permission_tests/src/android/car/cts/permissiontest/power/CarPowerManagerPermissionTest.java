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

package android.car.cts.permissiontest.power;

import static android.car.Car.PERMISSION_CAR_POWER;
import static android.car.Car.PERMISSION_CONTROL_CAR_POWER_POLICY;
import static android.car.Car.PERMISSION_CONTROL_SHUTDOWN_PROCESS;
import static android.car.Car.PERMISSION_READ_CAR_POWER_POLICY;
import static android.car.Car.POWER_SERVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.CarPowerPolicyFilter;
import android.frameworks.automotive.powerpolicy.PowerComponent;
import android.view.Display;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import android.car.cts.permissiontest.AbstractCarManagerPermissionTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;

/**
 * This class contains security permission tests for the
 * {@link android.car.hardware.power#CarPowerManager}'s system APIs.
 */
@RunWith(AndroidJUnit4.class)
public final class CarPowerManagerPermissionTest extends AbstractCarManagerPermissionTest {
    private CarPowerManager mCarPowerManager;

    @Before
    public void setUp() {
        super.connectCar();
        mCarPowerManager = (CarPowerManager) mCar.getCarManager(POWER_SERVICE);
    }

    @Test
    public void testGetPowerState() throws Exception {
        Exception e = assertThrows(SecurityException.class, () -> mCarPowerManager.getPowerState());

        assertThat(e.getMessage()).contains(PERMISSION_CAR_POWER);
    }

    @Test
    public void testRequestShutdownOnNextSuspend() throws Exception {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarPowerManager.requestShutdownOnNextSuspend());

        assertThat(e.getMessage()).contains(PERMISSION_CAR_POWER);
    }

    @Test
    public void testScheduleNextWakeupTime() throws Exception {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarPowerManager.scheduleNextWakeupTime(100));

        assertThat(e.getMessage()).contains(PERMISSION_CAR_POWER);
    }

    @Test
    public void testSetListener() throws Exception {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarPowerManager.setListener(mContext.getMainExecutor(), (state) -> {}));

        assertThat(e.getMessage()).contains(PERMISSION_CAR_POWER);
    }

    @Test
    public void testSetListenerWithCompletion() throws Exception {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarPowerManager.setListenerWithCompletion(mContext.getMainExecutor(),
                        (state, future) -> {}));

        assertThat(e.getMessage()).contains(PERMISSION_CONTROL_SHUTDOWN_PROCESS);
    }

    @Test
    public void testGetCurrentPowerPolicy() throws Exception {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarPowerManager.getCurrentPowerPolicy());

        assertThat(e.getMessage()).contains(PERMISSION_READ_CAR_POWER_POLICY);
    }

    @Test
    public void testApplyPowerPolicy() throws Exception {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarPowerManager.applyPowerPolicy("policy_id"));

        assertThat(e.getMessage()).contains(PERMISSION_CONTROL_CAR_POWER_POLICY);
    }

    @Test
    public void testAddPowerPolicyChangeListener() throws Exception {
        Executor executor = mContext.getMainExecutor();
        CarPowerPolicyFilter filter = new CarPowerPolicyFilter.Builder()
                .setComponents(PowerComponent.AUDIO).build();
        Exception e = assertThrows(SecurityException.class,
                () -> mCarPowerManager.addPowerPolicyListener(executor, filter, (p) -> { }));

        assertThat(e.getMessage()).contains(PERMISSION_READ_CAR_POWER_POLICY);
    }

    @Test
    public void testNotifyUserActivity() throws Exception {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarPowerManager.notifyUserActivity(Display.DEFAULT_DISPLAY));

        assertThat(e.getMessage()).contains(PERMISSION_CAR_POWER);
    }
}
