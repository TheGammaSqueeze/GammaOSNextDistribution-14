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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.common.CommonFixture;
import android.content.Context;
import android.os.Process;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionDebugReportDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;

import com.google.common.util.concurrent.Futures;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class DebugReportingTest {

    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final DevContext DEV_CONTEXT_DISABLED = DevContext.createForDevOptionsDisabled();
    private static final String CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final int CALLER_UID = Process.myUid();
    private static final long AD_ID_FETCHER_TIMEOUT_MS = 50;
    @Mock private Flags mFlagsMock;
    @Mock private AdServicesHttpsClient mHttpClientMock;
    @Mock private AdSelectionDebugReportDao mAdSelectionDebugReportDao;
    @Mock private AdIdFetcher mAdIdFetcher;
    private ExecutorService mLightweightExecutorService;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() {
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        MockitoAnnotations.initMocks(this);
        when(mFlagsMock.getAdIdKillSwitch()).thenReturn(false);
        when(mFlagsMock.getFledgeEventLevelDebugReportSendImmediately()).thenReturn(false);
        when(mFlagsMock.getAdIdFetcherTimeoutMs()).thenReturn(AD_ID_FETCHER_TIMEOUT_MS);
        when(mAdIdFetcher.isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS))
                .thenReturn(Futures.immediateFuture(true));
    }

    @Test
    public void isDisabled_withAdIdServiceKillSwitchTrue_returnsFalse() {
        when(mFlagsMock.getAdIdKillSwitch()).thenReturn(true);
        when(mFlagsMock.getFledgeEventLevelDebugReportingEnabled()).thenReturn(true);
        when(mAdIdFetcher.isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS))
                .thenReturn(Futures.immediateFuture(false));

        DebugReporting debugReporting = initDebugReporting();

        assertThat(debugReporting.isEnabled()).isFalse();
        assertThat(debugReporting).isInstanceOf(DebugReportingDisabled.class);
        verify(mAdIdFetcher, never())
                .isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS);
    }

    @Test
    public void isEnabled_withLatDisabledAndFlagEnabled_returnsTrue() {
        when(mFlagsMock.getFledgeEventLevelDebugReportingEnabled()).thenReturn(true);
        when(mAdIdFetcher.isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS))
                .thenReturn(Futures.immediateFuture(false));

        DebugReporting debugReporting = initDebugReporting();

        assertThat(debugReporting.isEnabled()).isTrue();
        assertThat(debugReporting).isInstanceOf(DebugReportingEnabled.class);
        verify(mAdIdFetcher, times(1))
                .isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS);
    }

    @Test
    public void isEnabled_withFlagDisabled_returnsFalse() {
        when(mFlagsMock.getFledgeEventLevelDebugReportingEnabled()).thenReturn(false);

        DebugReporting debugReporting = initDebugReporting();

        assertThat(debugReporting.isEnabled()).isFalse();
        assertThat(debugReporting).isInstanceOf(DebugReportingDisabled.class);
        verify(mAdIdFetcher, never())
                .isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS);
    }

    @Test
    public void isEnabled_withLatEnabled_returnsFalse() {
        when(mFlagsMock.getFledgeEventLevelDebugReportingEnabled()).thenReturn(true);
        when(mAdIdFetcher.isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS))
                .thenReturn(Futures.immediateFuture(true));

        DebugReporting debugReporting = initDebugReporting();

        assertThat(debugReporting.isEnabled()).isFalse();
        assertThat(debugReporting).isInstanceOf(DebugReportingDisabled.class);
        verify(mAdIdFetcher, times(1))
                .isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS);
    }

    @Test
    public void getScriptStrategy_isEnabled_returnsCorrect() {
        when(mFlagsMock.getFledgeEventLevelDebugReportingEnabled()).thenReturn(true);
        when(mAdIdFetcher.isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS))
                .thenReturn(Futures.immediateFuture(false));

        DebugReporting debugReporting = initDebugReporting();

        assertThat(debugReporting.getScriptStrategy())
                .isInstanceOf(DebugReportingEnabledScriptStrategy.class);
    }

    @Test
    public void getScriptStrategy_isDisabled_returnsCorrect() {
        when(mFlagsMock.getFledgeEventLevelDebugReportingEnabled()).thenReturn(false);

        DebugReporting debugReporting = initDebugReporting();

        assertThat(debugReporting.getScriptStrategy()).isInstanceOf(
                DebugReportingScriptDisabledStrategy.class);
    }

    @Test
    public void getSenderStrategy_isSentImmediatelyEnabled_returnsCorrect() {
        when(mFlagsMock.getFledgeEventLevelDebugReportingEnabled()).thenReturn(true);
        when(mFlagsMock.getFledgeEventLevelDebugReportSendImmediately()).thenReturn(true);
        when(mAdIdFetcher.isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS))
                .thenReturn(Futures.immediateFuture(false));

        DebugReporting debugReporting = initDebugReporting();

        assertThat(debugReporting.getSenderStrategy()).isInstanceOf(
                DebugReportSenderStrategyHttpImpl.class);
    }

    @Test
    public void getSenderStrategy_isEnabled_returnsCorrect() {
        when(mFlagsMock.getFledgeEventLevelDebugReportingEnabled()).thenReturn(true);
        when(mAdIdFetcher.isLimitedAdTrackingEnabled(
                        CALLER_PACKAGE_NAME, CALLER_UID, AD_ID_FETCHER_TIMEOUT_MS))
                .thenReturn(Futures.immediateFuture(false));

        DebugReporting debugReporting = initDebugReporting();

        assertThat(debugReporting.getSenderStrategy())
                .isInstanceOf(DebugReportSenderStrategyBatchImpl.class);
    }

    @Test
    public void getSenderStrategy_isDisabled_returnsCorrect() {
        when(mFlagsMock.getFledgeEventLevelDebugReportingEnabled()).thenReturn(false);

        DebugReporting debugReporting = initDebugReporting();

        assertThat(debugReporting.getSenderStrategy()).isInstanceOf(
                DebugReportSenderStrategyNoOp.class);
    }

    private DebugReporting initDebugReporting() {
        try {
            return DebugReporting.createInstance(
                            CONTEXT,
                            mFlagsMock,
                            mHttpClientMock,
                            DEV_CONTEXT_DISABLED,
                            mAdSelectionDebugReportDao,
                            mLightweightExecutorService,
                            mAdIdFetcher,
                            CALLER_PACKAGE_NAME,
                            CALLER_UID)
                    .get();
        } catch (ExecutionException | InterruptedException exception) {
            Assert.fail(
                    "Failed to create instance of debug reporting."
                            + exception.getLocalizedMessage());
            throw new RuntimeException("Unchecked Exception");
        }
    }
}
