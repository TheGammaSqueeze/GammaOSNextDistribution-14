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

package android.car.cts.permissiontest.watchdog;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import android.app.UiAutomation;
import android.car.Car;
import android.car.watchdog.CarWatchdogManager;
import android.os.Process;
import android.os.SystemProperties;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import android.car.cts.permissiontest.AbstractCarManagerPermissionTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class contains security permission tests for the
 * {@link android.car.watchdog.CarWatchdogManager}'s system APIs.
 */
@RunWith(AndroidJUnit4.class)
public class CarWatchdogManagerPermissionTest extends AbstractCarManagerPermissionTest  {
    private final UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    private CarWatchdogManager mCarWatchdogManager;

    @Before
    public void setUp() throws Exception {
        super.connectCar();
        mCarWatchdogManager = (CarWatchdogManager) mCar.getCarManager(Car.CAR_WATCHDOG_SERVICE);
    }

    @Test
    public void testRegisterClient() {
        CarWatchdogManager.CarWatchdogClientCallback client =
                new CarWatchdogManager.CarWatchdogClientCallback() {
                };

        expectPermissionException(Car.PERMISSION_USE_CAR_WATCHDOG,
                () -> mCarWatchdogManager.registerClient(mContext.getMainExecutor(), client,
                        CarWatchdogManager.TIMEOUT_NORMAL));
    }

    @Test
    public void testUnregisterClient() {
        CarWatchdogManager.CarWatchdogClientCallback client =
                new CarWatchdogManager.CarWatchdogClientCallback() {
                };
        mUiAutomation.adoptShellPermissionIdentity(Car.PERMISSION_USE_CAR_WATCHDOG);
        mCarWatchdogManager.registerClient(mContext.getMainExecutor(), client,
                CarWatchdogManager.TIMEOUT_NORMAL);
        mUiAutomation.dropShellPermissionIdentity();

        expectPermissionException(Car.PERMISSION_USE_CAR_WATCHDOG,
                () -> mCarWatchdogManager.unregisterClient(client));
    }

    @Test
    public void testTellClientAlive() throws Exception {
        AtomicReference<Integer> actualSessionId = new AtomicReference<>(-1);
        CarWatchdogManager.CarWatchdogClientCallback client =
                new CarWatchdogManager.CarWatchdogClientCallback() {
                    @Override
                    public boolean onCheckHealthStatus(int sessionId,
                            @CarWatchdogManager.TimeoutLengthEnum int timeout) {
                        synchronized (actualSessionId) {
                            actualSessionId.set(sessionId);
                            actualSessionId.notifyAll();
                        }
                        return false;
                    }

                    @Override
                    public void onPrepareProcessTermination() {
                        fail("Unexpected call to onPrepareProcessTermination");
                    }
                };
        mUiAutomation.adoptShellPermissionIdentity(Car.PERMISSION_USE_CAR_WATCHDOG);
        mCarWatchdogManager.registerClient(mContext.getMainExecutor(), client,
                CarWatchdogManager.TIMEOUT_CRITICAL);
        mUiAutomation.dropShellPermissionIdentity();
        synchronized (actualSessionId) {
            actualSessionId.wait(isEmulator() ? 30000 : 6000);
        }

        expectPermissionException(Car.PERMISSION_USE_CAR_WATCHDOG,
                () -> mCarWatchdogManager.tellClientAlive(client, actualSessionId.get()));
    }

    @Test
    public void testGetAllResourceOveruseStats() {
        expectPermissionException(Car.PERMISSION_COLLECT_CAR_WATCHDOG_METRICS,
                () -> mCarWatchdogManager.getAllResourceOveruseStats(
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        CarWatchdogManager.FLAG_MINIMUM_STATS_IO_1_MB,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY));
    }

    @Test
    public void testGetResourceOveruseStatsForUserPackage() {
        String packageName = mContext.getPackageName();
        UserHandle userHandle = UserHandle.getUserHandleForUid(Process.myUid());

        expectPermissionException(Car.PERMISSION_COLLECT_CAR_WATCHDOG_METRICS,
                () -> mCarWatchdogManager.getResourceOveruseStatsForUserPackage(
                        packageName, userHandle,
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY));
    }

    @Test
    public void testAddResourceOveruseListenerForSystem() {
        CarWatchdogManager.ResourceOveruseListener listener = resourceOveruseStats -> {
            // Do nothing
        };

        expectPermissionException(Car.PERMISSION_COLLECT_CAR_WATCHDOG_METRICS,
                () -> mCarWatchdogManager.addResourceOveruseListenerForSystem(
                        mContext.getMainExecutor(),
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        listener));
    }

    @Test
    public void testRemoveResourceOveruseListenerForSystem() {
        CarWatchdogManager.ResourceOveruseListener listener = resourceOveruseStats -> {
            // Do nothing
        };
        mUiAutomation.adoptShellPermissionIdentity(Car.PERMISSION_COLLECT_CAR_WATCHDOG_METRICS);
        mCarWatchdogManager.addResourceOveruseListenerForSystem(mContext.getMainExecutor(),
                CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO, listener);
        mUiAutomation.dropShellPermissionIdentity();

        expectPermissionException(Car.PERMISSION_COLLECT_CAR_WATCHDOG_METRICS,
                () -> mCarWatchdogManager.removeResourceOveruseListenerForSystem(listener));
    }

    @Test
    public void testSetKillablePackageAsUser() {
        String packageName = mContext.getPackageName();
        UserHandle userHandle = UserHandle.getUserHandleForUid(Process.myUid());

        expectPermissionException(Car.PERMISSION_CONTROL_CAR_WATCHDOG_CONFIG,
                () -> mCarWatchdogManager.setKillablePackageAsUser(packageName, userHandle, true));
    }

    @Test
    public void testGetPackageKillableStatesAsUser() {
        UserHandle userHandle = UserHandle.getUserHandleForUid(Process.myUid());

        expectPermissionException(Car.PERMISSION_CONTROL_CAR_WATCHDOG_CONFIG,
                () -> mCarWatchdogManager.getPackageKillableStatesAsUser(userHandle));
    }

    @Test
    public void testSetResourceOveruseConfigurations() {
        expectPermissionException(Car.PERMISSION_CONTROL_CAR_WATCHDOG_CONFIG,
                () -> mCarWatchdogManager.setResourceOveruseConfigurations(new ArrayList<>(),
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO));
    }

    @Test
    public void testGetResourceOveruseConfigurations() {
        expectPermissionException(Arrays.asList(Car.PERMISSION_CONTROL_CAR_WATCHDOG_CONFIG,
                Car.PERMISSION_COLLECT_CAR_WATCHDOG_METRICS),
                () -> mCarWatchdogManager.getResourceOveruseConfigurations(
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO));
    }

    private void expectPermissionException(String permission, ThrowingRunnable runnable) {
        SecurityException thrown = assertThrows(SecurityException.class, runnable);
        assertThat(thrown.getMessage()).isEqualTo("requires " + permission);
    }

    private void expectPermissionException(List<String> permissions, ThrowingRunnable runnable) {
        SecurityException thrown = assertThrows(SecurityException.class, runnable);
        String exceptionBuilder = "requires any of [" + String.join(", ", permissions) + "]";
        assertThat(thrown.getMessage()).isEqualTo(exceptionBuilder);
    }

    private static boolean isEmulator() {
        return SystemProperties.getBoolean("ro.boot.qemu", false)
                || SystemProperties.getBoolean("ro.kernel.qemu", false);
    }
}
