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

package com.android.adservices.service.adselection;

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.adservices.adselection.UpdateAdCounterHistogramCallback;
import android.adservices.adselection.UpdateAdCounterHistogramInput;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.common.FrequencyCapFilters;
import android.os.Parcel;
import android.os.RemoteException;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesStatsLog;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class UpdateAdCounterHistogramWorkerTest {
    private static final int CALLBACK_WAIT_MS = 500;
    private static final int CALLER_UID = 10;
    private static final long AD_SELECTION_ID = 20;
    private static final int LOGGING_API_NAME =
            AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__UPDATE_AD_COUNTER_HISTOGRAM;
    private static final ExecutorService DIRECT_EXECUTOR = MoreExecutors.newDirectExecutorService();

    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock private AdCounterHistogramUpdater mHistogramUpdaterMock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;
    @Mock private AdSelectionServiceFilter mServiceFilterMock;
    @Mock private ConsentManager mConsentManagerMock;

    private UpdateAdCounterHistogramWorker mUpdateWorker;
    private UpdateAdCounterHistogramInput mInputParams;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        mUpdateWorker =
                new UpdateAdCounterHistogramWorker(
                        mHistogramUpdaterMock,
                        DIRECT_EXECUTOR,
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        mAdServicesLoggerMock,
                        new FlagsOverridingAdFiltering(true),
                        mServiceFilterMock,
                        mConsentManagerMock,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled());

        mInputParams =
                new UpdateAdCounterHistogramInput.Builder(
                                AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                CommonFixture.VALID_BUYER_1,
                                TEST_PACKAGE_NAME)
                        .build();
    }

    @Test
    public void testWorkerUpdatesHistogramAndNotifiesSuccess() throws InterruptedException {
        CountDownLatch callbackLatch = new CountDownLatch(1);
        UpdateAdCounterHistogramTestCallback callback =
                new UpdateAdCounterHistogramTestCallback(callbackLatch);

        mUpdateWorker.updateAdCounterHistogram(mInputParams, callback);

        assertThat(callbackLatch.await(CALLBACK_WAIT_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(callback.mIsSuccess).isTrue();

        verify(mHistogramUpdaterMock)
                .updateNonWinHistogram(
                        eq(AD_SELECTION_ID),
                        eq(TEST_PACKAGE_NAME),
                        eq(FrequencyCapFilters.AD_EVENT_TYPE_CLICK),
                        eq(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(LOGGING_API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_SUCCESS), anyInt());
    }

    @Test
    public void testWorkerValidatesInvalidAdEventTypeAndNotifiesFailure() throws Exception {
        Parcel sourceParcel = Parcel.obtain();
        sourceParcel.writeLong(AD_SELECTION_ID);
        sourceParcel.writeInt(FrequencyCapFilters.AD_EVENT_TYPE_MAX + 10);
        CommonFixture.VALID_BUYER_1.writeToParcel(sourceParcel, 0);
        sourceParcel.writeString(TEST_PACKAGE_NAME);
        sourceParcel.setDataPosition(0);

        UpdateAdCounterHistogramInput invalidInputParams =
                UpdateAdCounterHistogramInput.CREATOR.createFromParcel(sourceParcel);

        CountDownLatch callbackLatch = new CountDownLatch(1);
        UpdateAdCounterHistogramTestCallback callback =
                new UpdateAdCounterHistogramTestCallback(callbackLatch);

        mUpdateWorker.updateAdCounterHistogram(invalidInputParams, callback);

        assertWithMessage("Callback latch wait")
                .that(callbackLatch.await(CALLBACK_WAIT_MS, TimeUnit.MILLISECONDS))
                .isTrue();
        assertWithMessage("Callback success").that(callback.mIsSuccess).isFalse();

        verify(mHistogramUpdaterMock, never())
                .updateNonWinHistogram(anyLong(), any(), anyInt(), any());

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(LOGGING_API_NAME),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INVALID_ARGUMENT),
                        anyInt());
    }

    @Test
    public void testWorkerFeatureFlagDisabledStopsAndNotifiesFailure() throws InterruptedException {
        mUpdateWorker =
                new UpdateAdCounterHistogramWorker(
                        mHistogramUpdaterMock,
                        DIRECT_EXECUTOR,
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        mAdServicesLoggerMock,
                        new FlagsOverridingAdFiltering(false),
                        mServiceFilterMock,
                        mConsentManagerMock,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled());

        CountDownLatch callbackLatch = new CountDownLatch(1);
        UpdateAdCounterHistogramTestCallback callback =
                new UpdateAdCounterHistogramTestCallback(callbackLatch);

        mUpdateWorker.updateAdCounterHistogram(mInputParams, callback);

        assertThat(callbackLatch.await(CALLBACK_WAIT_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(callback.mIsSuccess).isFalse();
        assertThat(callback.mFledgeErrorResponse.getStatusCode()).isEqualTo(STATUS_INTERNAL_ERROR);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(LOGGING_API_NAME),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());

        verifyNoMoreInteractions(mHistogramUpdaterMock);
    }

    @Test
    public void testWorkerFilterFailureStopsAndNotifiesFailure() throws InterruptedException {
        doThrow(new FilterException(new FledgeAuthorizationFilter.CallerMismatchException()))
                .when(mServiceFilterMock)
                .filterRequest(
                        any(), any(), anyBoolean(), anyBoolean(), anyInt(), anyInt(), any(), any());

        CountDownLatch callbackLatch = new CountDownLatch(1);
        UpdateAdCounterHistogramTestCallback callback =
                new UpdateAdCounterHistogramTestCallback(callbackLatch);

        mUpdateWorker.updateAdCounterHistogram(mInputParams, callback);

        assertThat(callbackLatch.await(CALLBACK_WAIT_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(callback.mIsSuccess).isFalse();

        verifyNoMoreInteractions(mHistogramUpdaterMock, mAdServicesLoggerMock);
    }

    @Test
    public void testWorkerConsentFailureStopsAndNotifiesSuccess() throws InterruptedException {
        doReturn(true)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        CountDownLatch callbackLatch = new CountDownLatch(1);
        UpdateAdCounterHistogramTestCallback callback =
                new UpdateAdCounterHistogramTestCallback(callbackLatch);

        mUpdateWorker.updateAdCounterHistogram(mInputParams, callback);

        assertThat(callbackLatch.await(CALLBACK_WAIT_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(callback.mIsSuccess).isTrue();

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(LOGGING_API_NAME),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        anyInt());

        verifyNoMoreInteractions(mHistogramUpdaterMock);
    }

    @Test
    public void testWorkerInvalidArgumentFailureStopsAndNotifiesFailure()
            throws InterruptedException {
        doThrow(new IllegalArgumentException())
                .when(mHistogramUpdaterMock)
                .updateNonWinHistogram(anyLong(), any(), anyInt(), any());

        CountDownLatch callbackLatch = new CountDownLatch(1);
        UpdateAdCounterHistogramTestCallback callback =
                new UpdateAdCounterHistogramTestCallback(callbackLatch);

        mUpdateWorker.updateAdCounterHistogram(mInputParams, callback);

        assertThat(callbackLatch.await(CALLBACK_WAIT_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(callback.mIsSuccess).isFalse();
        assertThat(callback.mFledgeErrorResponse.getStatusCode())
                .isEqualTo(STATUS_INVALID_ARGUMENT);

        verify(mHistogramUpdaterMock)
                .updateNonWinHistogram(
                        eq(AD_SELECTION_ID),
                        eq(TEST_PACKAGE_NAME),
                        eq(FrequencyCapFilters.AD_EVENT_TYPE_CLICK),
                        eq(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(LOGGING_API_NAME),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INVALID_ARGUMENT),
                        anyInt());
    }

    @Test
    public void testWorkerSuccessWithCallbackErrorLogsUnknownError() throws InterruptedException {
        CountDownLatch logCallbackErrorLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInput -> {
                            logCallbackErrorLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(LOGGING_API_NAME),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_UNKNOWN_ERROR),
                        anyInt());

        CountDownLatch callbackLatch = new CountDownLatch(1);
        UpdateAdCounterHistogramTestCallback callback =
                new UpdateAdCounterHistogramTestErrorCallback(callbackLatch);

        mUpdateWorker.updateAdCounterHistogram(mInputParams, callback);

        assertThat(callbackLatch.await(CALLBACK_WAIT_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(callback.mIsSuccess).isTrue();

        verify(mHistogramUpdaterMock)
                .updateNonWinHistogram(
                        eq(AD_SELECTION_ID),
                        eq(TEST_PACKAGE_NAME),
                        eq(FrequencyCapFilters.AD_EVENT_TYPE_CLICK),
                        eq(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(LOGGING_API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_SUCCESS), anyInt());

        assertThat(logCallbackErrorLatch.await(CALLBACK_WAIT_MS, TimeUnit.MILLISECONDS)).isTrue();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(LOGGING_API_NAME),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_UNKNOWN_ERROR),
                        anyInt());
    }

    @Test
    public void testWorkerFailureWithCallbackErrorLogsUnknownError() throws InterruptedException {
        doThrow(new IllegalStateException())
                .when(mHistogramUpdaterMock)
                .updateNonWinHistogram(anyLong(), any(), anyInt(), any());

        CountDownLatch logCallbackErrorLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInput -> {
                            logCallbackErrorLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(LOGGING_API_NAME),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_UNKNOWN_ERROR),
                        anyInt());

        CountDownLatch callbackLatch = new CountDownLatch(1);
        UpdateAdCounterHistogramTestCallback callback =
                new UpdateAdCounterHistogramTestErrorCallback(callbackLatch);

        mUpdateWorker.updateAdCounterHistogram(mInputParams, callback);

        assertThat(callbackLatch.await(CALLBACK_WAIT_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(callback.mIsSuccess).isFalse();

        verify(mHistogramUpdaterMock)
                .updateNonWinHistogram(
                        eq(AD_SELECTION_ID),
                        eq(TEST_PACKAGE_NAME),
                        eq(FrequencyCapFilters.AD_EVENT_TYPE_CLICK),
                        eq(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(LOGGING_API_NAME),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());

        assertThat(logCallbackErrorLatch.await(CALLBACK_WAIT_MS, TimeUnit.MILLISECONDS)).isTrue();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(LOGGING_API_NAME),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_UNKNOWN_ERROR),
                        anyInt());
    }

    public static class UpdateAdCounterHistogramTestCallback
            extends UpdateAdCounterHistogramCallback.Stub {
        protected final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        public UpdateAdCounterHistogramTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }

        public boolean isSuccess() {
            return mIsSuccess;
        }

        @Override
        public String toString() {
            return "UpdateAdCounterHistogramTestCallback{"
                    + "mCountDownLatch="
                    + mCountDownLatch
                    + ", mIsSuccess="
                    + mIsSuccess
                    + ", mFledgeErrorResponse="
                    + mFledgeErrorResponse
                    + '}';
        }
    }

    public static class UpdateAdCounterHistogramTestErrorCallback
            extends UpdateAdCounterHistogramTestCallback {
        public UpdateAdCounterHistogramTestErrorCallback(CountDownLatch countDownLatch) {
            super(countDownLatch);
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
            mCountDownLatch.countDown();
            throw new RemoteException();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
            throw new RemoteException();
        }
    }

    public static class FlagsOverridingAdFiltering implements Flags {
        private final boolean mShouldEnableAdFilteringFeature;

        public FlagsOverridingAdFiltering(boolean shouldEnableAdFilteringFeature) {
            mShouldEnableAdFilteringFeature = shouldEnableAdFilteringFeature;
        }

        public FlagsOverridingAdFiltering() {
            this(FLEDGE_AD_SELECTION_FILTERING_ENABLED);
        }

        @Override
        public boolean getFledgeOnDeviceAuctionKillSwitch() {
            return false;
        }

        @Override
        public boolean getEnforceIsolateMaxHeapSize() {
            return false;
        }

        @Override
        public boolean getFledgeAdSelectionFilteringEnabled() {
            return mShouldEnableAdFilteringFeature;
        }
    }
}
