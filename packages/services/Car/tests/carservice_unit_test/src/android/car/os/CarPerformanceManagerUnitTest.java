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

package android.car.os;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceSpecificException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class CarPerformanceManagerUnitTest {

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Mock private Context mContext;
    @Mock private Car mCar;
    @Mock private IBinder mBinder;
    @Mock private ICarPerformanceService mService;

    private CarPerformanceManager mCarPerformanceManager;

    @Before
    public void setUp() {
        when(mCar.getContext()).thenReturn(mContext);
        when(mCar.getEventHandler()).thenReturn(mMainHandler);
        when(mCar.handleRemoteExceptionFromCarService(any(RemoteException.class), any()))
                .thenCallRealMethod();
        when(mBinder.queryLocalInterface(anyString())).thenReturn(mService);
        mCarPerformanceManager = new CarPerformanceManager(mCar, mBinder);
    }

    @Test
    public void testSetThreadPriority() throws Exception {
        ThreadPolicyWithPriority expected = new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_FIFO, /* priority= */ 1);

        mCarPerformanceManager.setThreadPriority(expected);

        verify(mService).setThreadPriority(anyInt(), eq(expected));
    }

    @Test
    public void testSetThreadPriorityServiceSpecificExceptionFromService() throws Exception {
        ThreadPolicyWithPriority p = new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_FIFO, /* priority= */ 1);
        doThrow(new ServiceSpecificException(0)).when(mService).setThreadPriority(anyInt(), eq(p));

        assertThrows(CarPerformanceManager.SetSchedulerFailedException.class,
                () -> mCarPerformanceManager.setThreadPriority(p));
    }

    @Test
    public void testSetThreadPriorityRemoteExceptionFromService() throws Exception {
        ThreadPolicyWithPriority p = new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_FIFO, /* priority= */ 1);
        doThrow(new RemoteException("")).when(mService).setThreadPriority(anyInt(), eq(p));

        // Nothing should be thrown since {@link RemoteException} should be handled.
        mCarPerformanceManager.setThreadPriority(p);
    }

    @Test
    public void testGetThreadPriority() throws Exception {
        ThreadPolicyWithPriority expected = new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_FIFO, /* priority= */ 1);
        when(mService.getThreadPriority(anyInt())).thenReturn(expected);

        ThreadPolicyWithPriority got = mCarPerformanceManager.getThreadPriority();

        assertThat(got.getPolicy()).isEqualTo(expected.getPolicy());
        assertThat(got.getPriority()).isEqualTo(expected.getPriority());
    }

    @Test
    public void testGetThreadPriorityRemoteExceptionFromService() throws Exception {
        when(mService.getThreadPriority(anyInt())).thenThrow(new RemoteException(""));

        ThreadPolicyWithPriority got = mCarPerformanceManager.getThreadPriority();

        assertThat(got.getPolicy()).isEqualTo(ThreadPolicyWithPriority.SCHED_DEFAULT);
        assertThat(got.getPriority()).isEqualTo(0);
    }
}
