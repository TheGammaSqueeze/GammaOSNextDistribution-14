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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.os.CarPerformanceManager;
import android.car.os.CarPerformanceManager.SetSchedulerFailedException;
import android.car.os.ThreadPolicyWithPriority;
import android.car.watchdoglib.CarWatchdogDaemonHelper;
import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceSpecificException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.car.MockedCarTestBase;
import com.android.car.watchdog.CarWatchdogService;
import com.android.car.watchdog.TimeSource;
import com.android.car.watchdog.WatchdogStorage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.time.Instant;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class CarPerformanceManagerTest extends MockedCarTestBase {
    private CarPerformanceManager mCarPerformanceManager;
    private CarWatchdogService mCarWatchdogService;
    @Mock private CarWatchdogDaemonHelper mCarWatchdogDaemonHelper;
    @Mock private Context mMockBuiltinPackageContext;
    @Mock private WatchdogStorage mMockWatchdogStorage;
    private final TestTimeSource mTimeSource = new TestTimeSource();

    private static final class TestTimeSource extends TimeSource {
        private static final Instant TEST_DATE_TIME = Instant.parse("2021-11-12T13:14:15.16Z");
        private Instant mNow;
        TestTimeSource() {
            mNow = TEST_DATE_TIME;
        }

        @Override
        public Instant now() {
            /* Return the same time, so the tests are deterministic. */
            return mNow;
        }

        @Override
        public String toString() {
            return "Mocked date to " + now();
        }
    }

    @Override
    public void configureMockedHal() {
        mCarWatchdogService = new CarWatchdogService(
                getContext(), mMockBuiltinPackageContext, mMockWatchdogStorage, mTimeSource);
        mCarWatchdogService.setCarWatchdogDaemonHelper(mCarWatchdogDaemonHelper);
        setCarWatchDogService(mCarWatchdogService);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mCarPerformanceManager = (CarPerformanceManager) getCar().getCarManager(
                Car.CAR_PERFORMANCE_SERVICE);
        assertThat(mCarPerformanceManager).isNotNull();
    }

    @Test
    public void testSetThreadPriority() throws Exception {
        ThreadPolicyWithPriority p = new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_FIFO, /* priority= */ 1);
        mCarPerformanceManager.setThreadPriority(p);

        verify(mCarWatchdogDaemonHelper).setThreadPriority(anyInt(), anyInt(), anyInt(),
                eq(ThreadPolicyWithPriority.SCHED_FIFO), eq(1));
    }

    @Test
    public void testSetThreadPriorityIllegalArgumentException() throws Exception {
        doThrow(new IllegalArgumentException("test")).when(mCarWatchdogDaemonHelper)
                .setThreadPriority(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        ThreadPolicyWithPriority p = new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_FIFO, /* priority= */ 1);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> mCarPerformanceManager.setThreadPriority(p));

        assertWithMessage("exception on illegal set thread priority argument").that(thrown)
                .hasMessageThat().contains("test");
    }

    @Test
    public void testSetThreadPriorityIllegalStateException() throws Exception {
        doThrow(new IllegalStateException("test")).when(mCarWatchdogDaemonHelper).setThreadPriority(
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        ThreadPolicyWithPriority p = new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_FIFO, /* priority= */ 1);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> mCarPerformanceManager.setThreadPriority(p));

        assertWithMessage("exception on illegal state exception").that(thrown).hasMessageThat()
                .contains("test");
    }

    @Test
    public void testSetThreadPriorityRemoteException() throws Exception {
        doThrow(new RemoteException("test")).when(mCarWatchdogDaemonHelper).setThreadPriority(
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        ThreadPolicyWithPriority p = new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_FIFO, /* priority= */ 1);

        // Nothing must be thrown. Remote exception is just logged.
        mCarPerformanceManager.setThreadPriority(p);
    }

    @Test
    public void testSetThreadPriorityServiceSpecificException() throws Exception {
        doThrow(new ServiceSpecificException(0)).when(mCarWatchdogDaemonHelper).setThreadPriority(
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        ThreadPolicyWithPriority p = new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_FIFO, /* priority= */ 1);

        assertThrows(SetSchedulerFailedException.class,
                () -> mCarPerformanceManager.setThreadPriority(p));
    }

    @Test
    public void testGetThreadPriority() throws Exception {
        int[] p = new int[]{ThreadPolicyWithPriority.SCHED_FIFO, 1};
        when(mCarWatchdogDaemonHelper.getThreadPriority(
                anyInt(), anyInt(), anyInt())).thenReturn(p);

        ThreadPolicyWithPriority result = mCarPerformanceManager.getThreadPriority();

        assertThat(result.getPolicy()).isEqualTo(ThreadPolicyWithPriority.SCHED_FIFO);
        assertThat(result.getPriority()).isEqualTo(1);
    }

    @Test
    public void testGetThreadPriorityDefaultPolicy() throws Exception {
        int[] p = new int[]{ThreadPolicyWithPriority.SCHED_DEFAULT, 0};
        when(mCarWatchdogDaemonHelper.getThreadPriority(
                anyInt(), anyInt(), anyInt())).thenReturn(p);

        ThreadPolicyWithPriority result = mCarPerformanceManager.getThreadPriority();

        assertThat(result.getPolicy()).isEqualTo(ThreadPolicyWithPriority.SCHED_DEFAULT);
        assertThat(result.getPriority()).isEqualTo(0);
    }

    @Test
    public void testGetThreadPriorityInvalidPolicy() throws Exception {
        int[] p = new int[]{-1, 1};
        when(mCarWatchdogDaemonHelper.getThreadPriority(
                anyInt(), anyInt(), anyInt())).thenReturn(p);

        assertThrows(IllegalStateException.class, () -> mCarPerformanceManager.getThreadPriority());
    }

    @Test
    public void testGetThreadPriorityInvalidPriority() throws Exception {
        int[] p = new int[]{ThreadPolicyWithPriority.SCHED_FIFO, 0};
        when(mCarWatchdogDaemonHelper.getThreadPriority(
                anyInt(), anyInt(), anyInt())).thenReturn(p);

        assertThrows(IllegalStateException.class, () -> mCarPerformanceManager.getThreadPriority());
    }

    @Test
    public void testGetThreadPriorityRemoteException() throws Exception {
        when(mCarWatchdogDaemonHelper.getThreadPriority(anyInt(), anyInt(), anyInt())).thenThrow(
                new RemoteException(""));

        ThreadPolicyWithPriority result = mCarPerformanceManager.getThreadPriority();

        assertThat(result.getPolicy()).isEqualTo(ThreadPolicyWithPriority.SCHED_DEFAULT);
        assertThat(result.getPriority()).isEqualTo(0);
    }

    @Test
    public void testGetThreadPriorityIllegalStateException() throws Exception {
        when(mCarWatchdogDaemonHelper.getThreadPriority(anyInt(), anyInt(), anyInt())).thenThrow(
                new IllegalStateException(""));

        assertThrows(IllegalStateException.class, () -> mCarPerformanceManager.getThreadPriority());
    }

    @Test
    public void testGetThreadPriorityServiceSpecificException() throws Exception {
        when(mCarWatchdogDaemonHelper.getThreadPriority(anyInt(), anyInt(), anyInt())).thenThrow(
                new ServiceSpecificException(0));

        assertThrows(IllegalStateException.class, () -> mCarPerformanceManager.getThreadPriority());
    }
}
