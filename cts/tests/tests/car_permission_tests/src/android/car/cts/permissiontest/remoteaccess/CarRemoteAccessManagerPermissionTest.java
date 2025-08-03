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

package android.car.cts.permissiontest.remoteaccess;

import static android.car.remoteaccess.CarRemoteAccessManager.NEXT_POWER_STATE_OFF;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.car.Car;
import android.car.cts.permissiontest.AbstractCarManagerPermissionTest;
import android.car.remoteaccess.CarRemoteAccessManager;
import android.car.remoteaccess.CarRemoteAccessManager.CompletableRemoteTaskFuture;
import android.car.remoteaccess.CarRemoteAccessManager.RemoteTaskClientCallback;
import android.car.remoteaccess.RemoteTaskClientRegistrationInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.Executor;

/**
 * This class contains security permission tests for the {@link CarRemoteAccessManager}'s APIs.
 */
@RunWith(MockitoJUnitRunner.class)
public final class CarRemoteAccessManagerPermissionTest extends AbstractCarManagerPermissionTest {

    private CarRemoteAccessManager mCarRemoteAccessManager;
    @Mock private Executor mExecutor;

    private static final class FakeRemoteTaskClientCallback implements RemoteTaskClientCallback {
        @Override
        public void onRegistrationUpdated(@NonNull RemoteTaskClientRegistrationInfo info) {}

        @Override
        public void onRegistrationFailed() {}

        @Override
        public void onRemoteTaskRequested(@NonNull String taskId, @Nullable byte[] data,
                int taskMaxDurationInSec) {}

        @Override
        public void onShutdownStarting(@NonNull CompletableRemoteTaskFuture future) {}
    }

    private final FakeRemoteTaskClientCallback mCallback = new FakeRemoteTaskClientCallback();

    @Before
    public void setUp() {
        super.connectCar();
        mCarRemoteAccessManager = (CarRemoteAccessManager) mCar.getCarManager(
                Car.CAR_REMOTE_ACCESS_SERVICE);
        assumeTrue("CarRemoteAccessManagerPermissionTest requires remote access feature to be "
                + "enabled", mCarRemoteAccessManager != null);
    }

    @Test
    @ApiTest(apis = {"android.car.remoteaccess.CarRemoteAccessManager#setRemoteTaskClient"})
    public void testSetRemoteTaskClient() throws Exception {
        assertThrows(SecurityException.class, () -> mCarRemoteAccessManager.setRemoteTaskClient(
                mExecutor, mCallback));
    }

    @Test
    @ApiTest(apis = {"android.car.remoteaccess.CarRemoteAccessManager#"
            + "setPowerStatePostTaskExecution"})
    public void testSetPowerStatePostTaskExecution() throws Exception {
        assertThrows(SecurityException.class, () ->
                mCarRemoteAccessManager.setPowerStatePostTaskExecution(NEXT_POWER_STATE_OFF,
                /* runGarageMode= */ false));
    }

    @Test
    @ApiTest(apis = {"android.car.remoteaccess.CarRemoteAccessManager#isTaskScheduleSupported"})
    public void testIsTaskScheduleSupported() throws Exception {
        assertThrows(SecurityException.class, () ->
                mCarRemoteAccessManager.isTaskScheduleSupported());
    }

    @Test
    @ApiTest(apis = {"android.car.remoteaccess.CarRemoteAccessManager#getInVehicleTaskScheduler"})
    public void testGetInVehicleTaskScheduler() throws Exception {
        assertThrows(SecurityException.class, () ->
                mCarRemoteAccessManager.getInVehicleTaskScheduler());
    }
}
