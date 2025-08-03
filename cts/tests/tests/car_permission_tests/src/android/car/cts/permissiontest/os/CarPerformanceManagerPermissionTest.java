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

package android.car.cts.permissiontest.os;

import static android.car.Car.CAR_PERFORMANCE_SERVICE;
import static android.car.Car.PERMISSION_MANAGE_THREAD_PRIORITY;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.car.os.CarPerformanceManager;
import android.car.os.ThreadPolicyWithPriority;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import android.car.cts.permissiontest.AbstractCarManagerPermissionTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This class contains security permission tests for the
 * {@link android.car.os#CarPerformanceManager}'s system APIs.
 */
@RunWith(AndroidJUnit4.class)
public final class CarPerformanceManagerPermissionTest extends AbstractCarManagerPermissionTest {
    private CarPerformanceManager mCarPerformanceManager;

    @Before
    public void setUp() {
        super.connectCar();
        mCarPerformanceManager =
                (CarPerformanceManager) mCar.getCarManager(CAR_PERFORMANCE_SERVICE);
    }

    @Test
    public void testSetThreadPriority() throws Exception {
        ThreadPolicyWithPriority p = new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_FIFO, /* priority= */ 1);
        Exception e = assertThrows(
                SecurityException.class, () -> mCarPerformanceManager.setThreadPriority(p));

        assertThat(e.getMessage()).contains(PERMISSION_MANAGE_THREAD_PRIORITY);
    }

    @Test
    public void testGetThreadPriority() throws Exception {
        Exception e = assertThrows(
                SecurityException.class, () -> mCarPerformanceManager.getThreadPriority());

        assertThat(e.getMessage()).contains(PERMISSION_MANAGE_THREAD_PRIORITY);
    }
}
