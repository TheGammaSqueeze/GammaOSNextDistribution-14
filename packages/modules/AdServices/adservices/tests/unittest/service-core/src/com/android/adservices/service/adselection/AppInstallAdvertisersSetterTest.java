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

import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;

import static com.android.adservices.service.adselection.AppInstallAdvertisersSetter.FILTERING_IS_DISABLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SET_APP_INSTALL_ADVERTISERS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.adservices.adselection.SetAppInstallAdvertisersCallback;
import android.adservices.adselection.SetAppInstallAdvertisersInput;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.os.LimitExceededException;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.DBAppInstallPermissions;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.stats.AdServicesLogger;

import com.google.common.util.concurrent.ListeningExecutorService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.verification.VerificationMode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

@RunWith(MockitoJUnitRunner.class)
public class AppInstallAdvertisersSetterTest {

    private static final int UID = 42;

    private static final SetAppInstallAdvertisersInput SAMPLE_INPUT =
            getSampleSetAppInstallAdvertisersInputBuilder().build();
    private static final List<DBAppInstallPermissions> DB_WRITE_FOR_SAMPLE_INPUT =
            SAMPLE_INPUT.getAdvertisers().stream()
                    .map(
                            x ->
                                    new DBAppInstallPermissions.Builder()
                                            .setBuyer(x)
                                            .setPackageName(SAMPLE_INPUT.getCallerPackageName())
                                            .build())
                    .collect(Collectors.toList());
    private final ListeningExecutorService mExecutorService =
            AdServicesExecutors.getBackgroundExecutor();
    @Mock private AppInstallDao mAppInstallDaoMock;
    @Mock private AdServicesLogger mAdServicesLogger;
    @Mock private AdSelectionServiceFilter mAdSelectionServiceFilter;

    @Mock private ConsentManager mConsentManager;

    private AppInstallAdvertisersSetter mAppInstallAdvertisersSetter;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        boolean filteringEnabled = true;
        Flags flags = new AppInstallAdvertisersSetterTestFlags(filteringEnabled);
        mAppInstallAdvertisersSetter =
                new AppInstallAdvertisersSetter(
                        mAppInstallDaoMock,
                        mExecutorService,
                        mAdServicesLogger,
                        flags,
                        mAdSelectionServiceFilter,
                        mConsentManager,
                        UID,
                        DevContext.createForDevOptionsDisabled());
        when(mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(any()))
                .thenReturn(false);
    }

    @Test
    public void testSetAppInstallAdvertisersSuccess() throws Exception {
        SetAppInstallAdvertisersTestCallback callback = callSetAppInstallAdvertisers(SAMPLE_INPUT);

        verify(mAdSelectionServiceFilter)
                .filterRequest(
                        null,
                        CommonFixture.TEST_PACKAGE_NAME_1,
                        true,
                        false,
                        UID,
                        AD_SERVICES_API_CALLED__API_NAME__SET_APP_INSTALL_ADVERTISERS,
                        Throttler.ApiKey.FLEDGE_API_SET_APP_INSTALL_ADVERTISERS,
                        DevContext.createForDevOptionsDisabled());
        assertTrue(callback.mIsSuccess);
        verifyLog(AdServicesStatusUtils.STATUS_SUCCESS);
        verify(mAppInstallDaoMock)
                .setAdTechsForPackage(
                        eq(CommonFixture.TEST_PACKAGE_NAME_1), eq(DB_WRITE_FOR_SAMPLE_INPUT));
    }

    @Test
    public void testSetAppInstallAdvertisersDisabledFailure() throws Exception {
        boolean filteringEnabled = false;
        Flags flags = new AppInstallAdvertisersSetterTestFlags(filteringEnabled);
        when(mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(any()))
                .thenReturn(false);
        AppInstallAdvertisersSetter appInstallAdvertisersSetter =
                new AppInstallAdvertisersSetter(
                        mAppInstallDaoMock,
                        mExecutorService,
                        mAdServicesLogger,
                        flags,
                        mAdSelectionServiceFilter,
                        mConsentManager,
                        UID,
                        DevContext.createForDevOptionsDisabled());
        SetAppInstallAdvertisersTestCallback callback =
                callSetAppInstallAdvertisers(SAMPLE_INPUT, appInstallAdvertisersSetter);

        assertFalse(callback.mIsSuccess);
        assertTrue(callback.mFledgeErrorResponse.getErrorMessage().contains(FILTERING_IS_DISABLED));
    }

    @Test
    public void testSetAppInstallAdvertisersFailure() throws Exception {
        doThrow(new RuntimeException()).when(mAppInstallDaoMock).setAdTechsForPackage(any(), any());
        SetAppInstallAdvertisersTestCallback callback = callSetAppInstallAdvertisers(SAMPLE_INPUT);

        assertFalse(callback.mIsSuccess);
        verifyLog(AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
    }

    @Test
    public void testSetAppInstallAdvertisersRevokedConsent() throws Exception {
        when(mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(any()))
                .thenReturn(true);
        SetAppInstallAdvertisersTestCallback callback = callSetAppInstallAdvertisers(SAMPLE_INPUT);

        verify(mConsentManager)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        SAMPLE_INPUT.getCallerPackageName());
        assertTrue(callback.mIsSuccess);
        verifyLog(AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED);
        verifyNoMoreInteractions(mAppInstallDaoMock);
    }

    @Test
    public void testSetAppInstallAdvertisersBadInput() throws Exception {
        SetAppInstallAdvertisersInput input =
                getSampleSetAppInstallAdvertisersInputBuilder()
                        .setAdvertisers(
                                new HashSet<>(Arrays.asList(CommonFixture.INVALID_EMPTY_BUYER)))
                        .build();
        SetAppInstallAdvertisersTestCallback callback = callSetAppInstallAdvertisers(input);

        assertFalse(callback.mIsSuccess);
        verifyLog(AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
        verifyNoMoreInteractions(mAppInstallDaoMock);
        // Consent should be checked after validations are run
        verifyNoMoreInteractions(mConsentManager);
    }

    @Test
    public void testSetAppInstallAdvertisersBackgroundCaller() throws Exception {
        doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                .when(mAdSelectionServiceFilter)
                .filterRequest(
                        any(), any(), anyBoolean(), anyBoolean(), anyInt(), anyInt(), any(), any());
        SetAppInstallAdvertisersTestCallback callback = callSetAppInstallAdvertisers(SAMPLE_INPUT);

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verifyLog(AdServicesStatusUtils.STATUS_BACKGROUND_CALLER, never());
        assertFalse(callback.mIsSuccess);
        verifyLog(AdServicesStatusUtils.STATUS_BACKGROUND_CALLER);
        verifyNoMoreInteractions(mAppInstallDaoMock);
        // Consent should be checked after foreground check
        verifyNoMoreInteractions(mConsentManager);
    }

    @Test
    public void testSetAppInstallAdvertisersAppNotAllowed() throws Exception {
        doThrow(new FledgeAllowListsFilter.AppNotAllowedException())
                .when(mAdSelectionServiceFilter)
                .filterRequest(
                        any(), any(), anyBoolean(), anyBoolean(), anyInt(), anyInt(), any(), any());
        SetAppInstallAdvertisersTestCallback callback = callSetAppInstallAdvertisers(SAMPLE_INPUT);

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verifyLog(AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED, never());
        assertFalse(callback.mIsSuccess);
        verifyLog(AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED);
        verifyNoMoreInteractions(mAppInstallDaoMock);
        // Consent should be checked after app permissions check
        verifyNoMoreInteractions(mConsentManager);
    }

    @Test
    public void testSetAppInstallAdvertisersUidMismatch() throws Exception {
        doThrow(new FledgeAuthorizationFilter.CallerMismatchException())
                .when(mAdSelectionServiceFilter)
                .filterRequest(
                        any(), any(), anyBoolean(), anyBoolean(), anyInt(), anyInt(), any(), any());
        SetAppInstallAdvertisersTestCallback callback = callSetAppInstallAdvertisers(SAMPLE_INPUT);

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verifyLog(AdServicesStatusUtils.STATUS_UNAUTHORIZED, never());
        assertFalse(callback.mIsSuccess);
        verifyLog(AdServicesStatusUtils.STATUS_UNAUTHORIZED);
        verifyNoMoreInteractions(mAppInstallDaoMock);
        // Consent should be checked after package name check
        verifyNoMoreInteractions(mConsentManager);
    }

    @Test
    public void testSetAppInstallAdvertisersLimitExceeded() throws Exception {
        doThrow(new LimitExceededException())
                .when(mAdSelectionServiceFilter)
                .filterRequest(
                        any(), any(), anyBoolean(), anyBoolean(), anyInt(), anyInt(), any(), any());
        SetAppInstallAdvertisersTestCallback callback = callSetAppInstallAdvertisers(SAMPLE_INPUT);

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verifyLog(AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED, never());
        assertFalse(callback.mIsSuccess);
        verifyLog(AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED);
        verifyNoMoreInteractions(mAppInstallDaoMock);
        // Consent should be checked after throttling
        verifyNoMoreInteractions(mConsentManager);
    }

    private void verifyLog(int status) {
        verify(mAdServicesLogger, atMost(1))
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__SET_APP_INSTALL_ADVERTISERS,
                        TEST_PACKAGE_NAME,
                        status,
                        0);
    }

    private void verifyLog(int status, VerificationMode verificationMode) {
        verify(mAdServicesLogger, verificationMode)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__SET_APP_INSTALL_ADVERTISERS,
                        TEST_PACKAGE_NAME,
                        status,
                        0);
    }

    private SetAppInstallAdvertisersTestCallback callSetAppInstallAdvertisers(
            SetAppInstallAdvertisersInput request) throws Exception {
        CountDownLatch callbackLatch = new CountDownLatch(1);
        SetAppInstallAdvertisersTestCallback callback =
                new SetAppInstallAdvertisersTestCallback(callbackLatch);

        mAppInstallAdvertisersSetter.setAppInstallAdvertisers(request, callback);
        callbackLatch.await();
        return callback;
    }

    private SetAppInstallAdvertisersTestCallback callSetAppInstallAdvertisers(
            SetAppInstallAdvertisersInput request,
            AppInstallAdvertisersSetter appInstallAdvertisersSetter)
            throws Exception {
        CountDownLatch callbackLatch = new CountDownLatch(1);
        SetAppInstallAdvertisersTestCallback callback =
                new SetAppInstallAdvertisersTestCallback(callbackLatch);

        appInstallAdvertisersSetter.setAppInstallAdvertisers(request, callback);
        callbackLatch.await();
        return callback;
    }

    private static SetAppInstallAdvertisersInput.Builder
            getSampleSetAppInstallAdvertisersInputBuilder() {
        return new SetAppInstallAdvertisersInput.Builder()
                .setAdvertisers(CommonFixture.BUYER_SET)
                .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME_1);
    }

    public static class SetAppInstallAdvertisersTestCallback
            extends SetAppInstallAdvertisersCallback.Stub {
        private final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        public SetAppInstallAdvertisersTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onSuccess() {
            mIsSuccess = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) {
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }

    private static class AppInstallAdvertisersSetterTestFlags implements Flags {
        private final boolean mAdSelectionFilteringEnabled;

        AppInstallAdvertisersSetterTestFlags(boolean filteringEnabled) {
            mAdSelectionFilteringEnabled = filteringEnabled;
        }

        @Override
        public boolean getFledgeAdSelectionFilteringEnabled() {
            return mAdSelectionFilteringEnabled;
        }
    }
}
