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

package com.android.federatedcompute.services;

import static android.federatedcompute.common.ClientConstants.STATUS_INTERNAL_ERROR;
import static android.federatedcompute.common.ClientConstants.STATUS_SUCCESS;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_API_CALLED__API_NAME__CANCEL;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_API_CALLED__API_NAME__SCHEDULE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.federatedcompute.aidl.IFederatedComputeCallback;
import android.federatedcompute.common.TrainingOptions;

import androidx.test.core.app.ApplicationProvider;

import com.android.federatedcompute.services.common.Clock;
import com.android.federatedcompute.services.common.PhFlagsTestUtil;
import com.android.federatedcompute.services.scheduling.FederatedComputeJobManager;
import com.android.federatedcompute.services.statsd.ApiCallStats;
import com.android.federatedcompute.services.statsd.FederatedComputeStatsdLogger;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public final class FederatedComputeManagingServiceDelegateTest {
    private static final int BINDER_CONNECTION_TIMEOUT_MS = 10_000;

    private static final String CALLING_PACKAGE_NAME = "callingPkg";
    private static final String CALLING_CLASS_NAME = "callingClass";

    public static final ComponentName OWNER_COMPONENT_NAME =
            ComponentName.createRelative(CALLING_PACKAGE_NAME, CALLING_CLASS_NAME);

    private FederatedComputeManagingServiceDelegate mFcpService;
    private Context mContext;
    private final FederatedComputeStatsdLogger mFcStatsdLogger =
            spy(FederatedComputeStatsdLogger.getInstance());
    private static final CountDownLatch sJobFinishCountDown = new CountDownLatch(1);

    @Mock FederatedComputeJobManager mMockJobManager;
    @Mock private Clock mClock;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        PhFlagsTestUtil.disableGlobalKillSwitch();

        mContext = ApplicationProvider.getApplicationContext();
        mFcpService =
                new FederatedComputeManagingServiceDelegate(
                        mContext, new TestInjector(), mFcStatsdLogger, mClock);
        when(mClock.elapsedRealtime()).thenReturn(100L, 200L);
    }

    @Test
    public void testScheduleMissingPackageName_throwsException() {
        TrainingOptions trainingOptions =
                new TrainingOptions.Builder().setPopulationName("fake-population").build();

        assertThrows(
                NullPointerException.class,
                () -> mFcpService.schedule(null, trainingOptions, new FederatedComputeCallback()));
    }

    @Test
    public void testScheduleMissingCallback_throwsException() {
        TrainingOptions trainingOptions =
                new TrainingOptions.Builder().setPopulationName("fake-population").build();
        assertThrows(
                NullPointerException.class,
                () -> mFcpService.schedule(mContext.getPackageName(), trainingOptions, null));
    }

    @Test
    public void testSchedule_returnsSuccess() throws Exception {
        when(mMockJobManager.onTrainerStartCalled(anyString(), any())).thenReturn(STATUS_SUCCESS);

        TrainingOptions trainingOptions =
                new TrainingOptions.Builder().setPopulationName("fake-population").build();
        invokeScheduleAndVerifyLogging(trainingOptions, STATUS_SUCCESS);
    }

    @Test
    public void testScheduleFailed() throws Exception {
        when(mMockJobManager.onTrainerStartCalled(anyString(), any()))
                .thenReturn(STATUS_INTERNAL_ERROR);

        TrainingOptions trainingOptions =
                new TrainingOptions.Builder().setPopulationName("fake-population").build();
        invokeScheduleAndVerifyLogging(trainingOptions, STATUS_INTERNAL_ERROR);
    }

    @Test
    public void testScheduleThrowsRTE() throws Exception {
        when(mMockJobManager.onTrainerStartCalled(anyString(), any()))
                .thenThrow(RuntimeException.class);

        TrainingOptions trainingOptions =
                new TrainingOptions.Builder().setPopulationName("fake-population").build();
        invokeScheduleAndVerifyLogging(trainingOptions, STATUS_INTERNAL_ERROR);
    }

    @Test
    public void testScheduleThrowsNPE() throws Exception {
        when(mMockJobManager.onTrainerStartCalled(anyString(), any()))
                .thenThrow(NullPointerException.class);

        TrainingOptions trainingOptions =
                new TrainingOptions.Builder().setPopulationName("fake-population").build();
        invokeScheduleAndVerifyLogging(trainingOptions, STATUS_INTERNAL_ERROR);
    }

    @Test
    public void testScheduleClockThrowsRTE() throws Exception {
        when(mClock.elapsedRealtime()).thenThrow(RuntimeException.class);
        TrainingOptions trainingOptions =
                new TrainingOptions.Builder().setPopulationName("fake-population").build();
        FederatedComputeCallback callback = spy(new FederatedComputeCallback());

        mFcpService.schedule(
                mContext.getPackageName(), trainingOptions, callback);

        ArgumentCaptor<Integer> argument = ArgumentCaptor.forClass(Integer.class);
        verify(callback).onFailure(argument.capture());
        assertThat(argument.getValue()).isEqualTo(STATUS_INTERNAL_ERROR);

    }

    @Test
    public void testScheduleClockThrowsIAE() throws Exception {
        when(mClock.elapsedRealtime()).thenThrow(IllegalArgumentException.class);

        TrainingOptions trainingOptions =
                new TrainingOptions.Builder().setPopulationName("fake-population").build();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mFcpService.schedule(
                                mContext.getPackageName(),
                                trainingOptions,
                                new FederatedComputeCallback()));
    }

    @Test
    public void testScheduleEnabledGlobalKillSwitch_throwsException() throws Exception {
        PhFlagsTestUtil.enableGlobalKillSwitch();
        TrainingOptions trainingOptions =
                new TrainingOptions.Builder().setPopulationName("fake-population").build();
        try {
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            mFcpService.schedule(
                                    mContext.getPackageName(),
                                    trainingOptions,
                                    new FederatedComputeCallback()));
        } finally {
            PhFlagsTestUtil.disableGlobalKillSwitch();
        }
    }

    @Test
    public void testCancelMissingPackageName_throwsException() {
        assertThrows(
                NullPointerException.class,
                () -> mFcpService.cancel(null, "fake-population", new FederatedComputeCallback()));
    }

    @Test
    public void testCancelMissingCallback_throwsException() {
        assertThrows(
                NullPointerException.class,
                () -> mFcpService.cancel(OWNER_COMPONENT_NAME, "fake-population", null));
    }

    @Test
    public void testCancel_returnsSuccess() throws Exception {
        when(mMockJobManager.onTrainerStopCalled(any(), anyString()))
                .thenReturn(STATUS_SUCCESS);

        invokeCancelAndVerifyLogging("fake-population", STATUS_SUCCESS);
    }

    @Test
    public void testCancelFails() throws Exception {
        when(mMockJobManager.onTrainerStopCalled(any(), any()))
                .thenReturn(STATUS_INTERNAL_ERROR);

        invokeCancelAndVerifyLogging("fake-population", STATUS_INTERNAL_ERROR);
    }

    @Test
    public void testCancelEnabledGlobalKillSwitch_throwsException() {
        PhFlagsTestUtil.enableGlobalKillSwitch();
        try {
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            mFcpService.cancel(
                                    OWNER_COMPONENT_NAME,
                                    "fake-population",
                                    new FederatedComputeCallback()));
        } finally {
            PhFlagsTestUtil.disableGlobalKillSwitch();
        }
    }

    @Test
    public void testCancelThrowsRTE() throws Exception {
        when(mMockJobManager.onTrainerStopCalled(any(), any()))
                .thenThrow(RuntimeException.class);

        invokeCancelAndVerifyLogging("fake-population", STATUS_INTERNAL_ERROR);
    }

    @Test
    public void testCancelThrowsNPE() throws Exception {
        when(mMockJobManager.onTrainerStopCalled(any(), any()))
                .thenThrow(NullPointerException.class);

        invokeCancelAndVerifyLogging("fake-population", STATUS_INTERNAL_ERROR);
    }

    @Test
    public void testCancelClockThrowsRTE() throws Exception {
        when(mClock.elapsedRealtime()).thenThrow(RuntimeException.class);
        FederatedComputeCallback callback = spy(new FederatedComputeCallback());

        mFcpService.cancel(OWNER_COMPONENT_NAME, "fake-population", callback);

        ArgumentCaptor<Integer> argument = ArgumentCaptor.forClass(Integer.class);
        verify(callback).onFailure(argument.capture());
        assertThat(argument.getValue()).isEqualTo(STATUS_INTERNAL_ERROR);

    }

    @Test
    public void testCancelClockThrowsIAE() throws Exception {
        when(mClock.elapsedRealtime()).thenThrow(IllegalArgumentException.class);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mFcpService.cancel(
                                OWNER_COMPONENT_NAME,
                                "fake-population",
                                new FederatedComputeCallback()));
    }

    private void invokeScheduleAndVerifyLogging(
            TrainingOptions trainingOptions, int expectedResultCode) throws InterruptedException {
        mFcpService.schedule(
                mContext.getPackageName(), trainingOptions, new FederatedComputeCallback());

        final CountDownLatch logOperationCalledLatch = new CountDownLatch(1);
        doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                // The method logAPiCallStats is called.
                                invocation.callRealMethod();
                                logOperationCalledLatch.countDown();
                                return null;
                            }
                        })
                .when(mFcStatsdLogger)
                .logApiCallStats(any(ApiCallStats.class));
        sJobFinishCountDown.await(BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        logOperationCalledLatch.await(BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        ArgumentCaptor<ApiCallStats> argument = ArgumentCaptor.forClass(ApiCallStats.class);
        verify(mFcStatsdLogger).logApiCallStats(argument.capture());
        assertThat(argument.getValue().getResponseCode()).isEqualTo(expectedResultCode);
        assertThat(argument.getValue().getLatencyMillis()).isEqualTo(100);
        assertThat(argument.getValue().getApiName())
                .isEqualTo(FEDERATED_COMPUTE_API_CALLED__API_NAME__SCHEDULE);
    }

    private void invokeCancelAndVerifyLogging(String populationName, int expectedResultCode)
            throws InterruptedException {
        mFcpService.cancel(OWNER_COMPONENT_NAME, populationName, new FederatedComputeCallback());

        final CountDownLatch logOperationCalledLatch = new CountDownLatch(1);
        doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                // The method logAPiCallStats is called.
                                invocation.callRealMethod();
                                logOperationCalledLatch.countDown();
                                return null;
                            }
                        })
                .when(mFcStatsdLogger)
                .logApiCallStats(any(ApiCallStats.class));
        sJobFinishCountDown.await(BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        logOperationCalledLatch.await(BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        ArgumentCaptor<ApiCallStats> argument = ArgumentCaptor.forClass(ApiCallStats.class);
        verify(mFcStatsdLogger).logApiCallStats(argument.capture());
        assertThat(argument.getValue().getResponseCode()).isEqualTo(expectedResultCode);
        assertThat(argument.getValue().getLatencyMillis()).isEqualTo(100);
        assertThat(argument.getValue().getApiName())
                .isEqualTo(FEDERATED_COMPUTE_API_CALLED__API_NAME__CANCEL);
    }

    static class FederatedComputeCallback extends IFederatedComputeCallback.Stub {
        public boolean mError = false;
        public int mErrorCode = 0;

        @Override
        public void onSuccess() {
            sJobFinishCountDown.countDown();
        }

        @Override
        public void onFailure(int errorCode) {
            mError = true;
            mErrorCode = errorCode;
            sJobFinishCountDown.countDown();
        }
    }

    class TestInjector extends FederatedComputeManagingServiceDelegate.Injector {
        FederatedComputeJobManager getJobManager(Context mContext) {
            return mMockJobManager;
        }
    }
}
