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

import static com.android.adservices.service.adselection.ReportEventDisabledImpl.API_DISABLED_MESSAGE;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.adselection.ReportInteractionCallback;
import android.adservices.adselection.ReportInteractionInput;
import android.adservices.common.FledgeErrorResponse;
import android.os.Process;
import android.os.RemoteException;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.stats.AdServicesLogger;

import com.google.common.util.concurrent.ListeningExecutorService;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.CountDownLatch;

@RunWith(MockitoJUnitRunner.class)
public class ReportEventDisabledImplTest {
    @Mock private AdSelectionEntryDao mAdSelectionEntryDaoMock;
    @Mock private AdServicesHttpsClient mHttpsClientMock;
    private ListeningExecutorService mLightweightExecutorService =
            AdServicesExecutors.getLightWeightExecutor();
    private ListeningExecutorService mBackgroundExecutorService =
            AdServicesExecutors.getBackgroundExecutor();
    @Mock private AdServicesLogger mAdServicesLoggerMock;
    private Flags mFlags = FlagsFactory.getFlagsForTest();
    @Mock private FledgeAuthorizationFilter mFledgeAuthorizationFilterMock;
    @Mock private AdSelectionServiceFilter mAdSelectionServiceFilterMock;
    private static final int MY_UID = Process.myUid();
    @Mock private ReportInteractionInput mReportInteractionInputMock;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testReportEventDisabledImplFailsWhenCalled() throws Exception {
        EventReporter eventReporter =
                new ReportEventDisabledImpl(
                        mAdSelectionEntryDaoMock,
                        mHttpsClientMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mAdServicesLoggerMock,
                        mFlags,
                        mAdSelectionServiceFilterMock,
                        MY_UID,
                        mFledgeAuthorizationFilterMock,
                        DevContext.createForDevOptionsDisabled(),
                        false);

        CountDownLatch resultLatch = new CountDownLatch(1);
        ReportEventTestCallback callback = new ReportEventTestCallback(resultLatch);
        eventReporter.reportInteraction(mReportInteractionInputMock, callback);
        resultLatch.await();

        assertThat(callback.mIsSuccess).isFalse();
        assertThat(callback.mFledgeErrorResponse.getStatusCode()).isEqualTo(STATUS_INTERNAL_ERROR);
        assertThat(callback.mFledgeErrorResponse.getErrorMessage()).isEqualTo(API_DISABLED_MESSAGE);
    }

    static class ReportEventTestCallback extends ReportInteractionCallback.Stub {
        private final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        ReportEventTestCallback(CountDownLatch countDownLatch) {
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
    }
}
