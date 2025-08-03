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

package com.android.car.os;

import static android.car.os.ThreadPolicyWithPriority.SCHED_FIFO;
import static android.car.os.ThreadPolicyWithPriority.SCHED_RR;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.os.ThreadPolicyWithPriority;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.Context;
import android.os.Binder;

import com.android.car.CarLocalServices;
import com.android.car.watchdog.CarWatchdogService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * <p>This class contains unit tests for the {@link CarPerformanceService}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class CarPerformanceServiceUnitTest extends AbstractExtendedMockitoTestCase {

    @Mock private Context mMockContext;
    @Mock private CarWatchdogService mMockCarWatchdogService;

    private CarPerformanceService mCarPerformanceService;

    public CarPerformanceServiceUnitTest() {
        super(CarPerformanceService.TAG);
    }

    @Before
    public void setUp() throws Exception {
        CarLocalServices.addService(CarWatchdogService.class, mMockCarWatchdogService);
        mCarPerformanceService = new CarPerformanceService(mMockContext);
        mCarPerformanceService.init();
    }

    @After
    public void tearDown() throws Exception {
        CarLocalServices.removeServiceForTest(CarWatchdogService.class);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder builder) {
        builder.spyStatic(Car.class);
    }

    @Test
    public void testSetThreadPriority() throws Exception {
        ThreadPolicyWithPriority policyWithPriority = new ThreadPolicyWithPriority(SCHED_FIFO, 10);

        mCarPerformanceService.setThreadPriority(/* tid= */234000, policyWithPriority);

        verify(mMockCarWatchdogService).setThreadPriority(Binder.getCallingPid(),
                /* tid= */234000, Binder.getCallingUid(), policyWithPriority.getPolicy(),
                policyWithPriority.getPriority());
    }

    @Test
    public void testGetThreadPriority() throws Exception {
        when(mMockCarWatchdogService.getThreadPriority(Binder.getCallingPid(),
                /* tid= */234000, Binder.getCallingUid())).thenReturn(new int[] {SCHED_RR, 30});

        ThreadPolicyWithPriority actual =
                mCarPerformanceService.getThreadPriority(/* tid= */234000);

        assertWithMessage("Thread policy").that(actual.getPolicy()).isEqualTo(SCHED_RR);
        assertWithMessage("Thread priority").that(actual.getPriority()).isEqualTo(30);
    }
}
