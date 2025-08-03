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

package com.android.adservices.service.common;

import static android.adservices.customaudience.CustomAudienceFixture.getValidFetchUriByBuyer;

import static com.android.adservices.service.common.AppManifestConfigCall.API_CUSTOM_AUDIENCES;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyString;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.os.LimitExceededException;
import android.os.Process;


import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;

public final class CustomAudienceServiceFilterTest extends AdServicesMockitoTestCase {

    private static final String CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;

    private static final Flags TEST_FLAGS = FlagsFactory.getFlagsForTest();
    private static final Flags FLAGS_WITH_ENROLLMENT_CHECK =
            new Flags() {
                @Override
                public boolean getDisableFledgeEnrollmentCheck() {
                    return false;
                }
            };

    @Mock private ConsentManager mConsentManagerMock;

    @Mock AppImportanceFilter mAppImportanceFilter;

    private final AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);

    @Spy
    FledgeAllowListsFilter mFledgeAllowListsFilterSpy =
            new FledgeAllowListsFilter(TEST_FLAGS, mAdServicesLoggerMock);

    @Spy
    FledgeAuthorizationFilter mFledgeAuthorizationFilterSpy =
            new FledgeAuthorizationFilter(
                    mSpyContext.getPackageManager(),
                    new EnrollmentDao(
                            mSpyContext, DbTestUtil.getSharedDbHelperForTest(), TEST_FLAGS),
                    mAdServicesLoggerMock);

    @Mock private Throttler mMockThrottler;

    private CustomAudienceServiceFilter mCustomAudienceServiceFilter;

    private static final AdTechIdentifier SELLER_VALID =
            AdTechIdentifier.fromString("developer.android.com");
    private static final AdTechIdentifier SELLER_LOCALHOST =
            AdTechIdentifier.fromString("127.0.0.1:8080");

    private static final int API_NAME = 0;

    private static final int MY_UID = Process.myUid();

    @Before
    public void setUp() throws Exception {
        mCustomAudienceServiceFilter =
                new CustomAudienceServiceFilter(
                        mSpyContext,
                        mConsentManagerMock,
                        TEST_FLAGS,
                        mAppImportanceFilter,
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy,
                        mMockThrottler);

        when(mMockThrottler.tryAcquire(eq(Throttler.ApiKey.UNKNOWN), anyString())).thenReturn(true);
    }

    @Test
    public void testFilterRequest_invalidPackageName_throws() {
        assertThrows(
                FledgeAuthorizationFilter.CallerMismatchException.class,
                () ->
                        mCustomAudienceServiceFilter.filterRequest(
                                SELLER_VALID,
                                "invalidPackageName",
                                false,
                                false,
                                MY_UID,
                                API_NAME,
                                Throttler.ApiKey.UNKNOWN,
                                DevContext.createForDevOptionsDisabled()));
    }

    @Test
    public void testFilterRequest_throttled_throws() {
        when(mMockThrottler.tryAcquire(eq(Throttler.ApiKey.UNKNOWN), anyString()))
                .thenReturn(false);

        assertThrows(
                LimitExceededException.class,
                () ->
                        mCustomAudienceServiceFilter.filterRequest(
                                SELLER_VALID,
                                CALLER_PACKAGE_NAME,
                                false,
                                false,
                                MY_UID,
                                API_NAME,
                                Throttler.ApiKey.UNKNOWN,
                                DevContext.createForDevOptionsDisabled()));
    }

    @Test
    public void testFilterRequest_enforceForegroundTrue_foregroundCheckFails_throws() {
        doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(MY_UID, API_NAME, null);

        assertThrows(
                AppImportanceFilter.WrongCallingApplicationStateException.class,
                () ->
                        mCustomAudienceServiceFilter.filterRequest(
                                SELLER_VALID,
                                CALLER_PACKAGE_NAME,
                                true,
                                false,
                                MY_UID,
                                API_NAME,
                                Throttler.ApiKey.UNKNOWN,
                                DevContext.createForDevOptionsDisabled()));
    }

    @Test
    public void testFilterRequest_enforceForegroundFalse_foregroundCheckFails_succeeds() {
        doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(Process.myUid(), API_NAME, null);

        mCustomAudienceServiceFilter.filterRequest(
                SELLER_VALID,
                CALLER_PACKAGE_NAME,
                false,
                false,
                MY_UID,
                API_NAME,
                Throttler.ApiKey.UNKNOWN,
                DevContext.createForDevOptionsDisabled());
    }

    @Test
    public void testFilterRequest_adTechNotAuthorized_throws() {
        // Create new CustomAudienceServiceFilter with new flags
        mCustomAudienceServiceFilter =
                new CustomAudienceServiceFilter(
                        mSpyContext,
                        mConsentManagerMock,
                        FLAGS_WITH_ENROLLMENT_CHECK,
                        mAppImportanceFilter,
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy,
                        mMockThrottler);

        doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mFledgeAuthorizationFilterSpy)
                .assertAdTechAllowed(
                        mSpyContext,
                        CALLER_PACKAGE_NAME,
                        SELLER_VALID,
                        API_NAME,
                        API_CUSTOM_AUDIENCES);

        assertThrows(
                FledgeAuthorizationFilter.AdTechNotAllowedException.class,
                () ->
                        mCustomAudienceServiceFilter.filterRequest(
                                SELLER_VALID,
                                CALLER_PACKAGE_NAME,
                                false,
                                false,
                                MY_UID,
                                API_NAME,
                                Throttler.ApiKey.UNKNOWN,
                                DevContext.createForDevOptionsDisabled()));
    }

    @Test
    public void testFilterRequest_withLocalhostDomain_doesNotPass() {
        mCustomAudienceServiceFilter =
                new CustomAudienceServiceFilter(
                        mSpyContext,
                        mConsentManagerMock,
                        FLAGS_WITH_ENROLLMENT_CHECK,
                        mAppImportanceFilter,
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy,
                        mMockThrottler);

        assertThrows(
                FledgeAuthorizationFilter.AdTechNotAllowedException.class,
                () ->
                        mCustomAudienceServiceFilter.filterRequest(
                                SELLER_LOCALHOST,
                                CALLER_PACKAGE_NAME,
                                false,
                                false,
                                MY_UID,
                                API_NAME,
                                Throttler.ApiKey.UNKNOWN,
                                DevContext.createForDevOptionsDisabled()));
    }

    @Test
    public void testFilterRequest_withDeveloperMode_succeeds() {
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(CALLER_PACKAGE_NAME);
        mCustomAudienceServiceFilter.filterRequest(
                SELLER_VALID,
                CALLER_PACKAGE_NAME,
                false,
                false,
                MY_UID,
                API_NAME,
                Throttler.ApiKey.UNKNOWN,
                DevContext.builder()
                        .setDevOptionsEnabled(true)
                        .setCallingAppPackageName(CALLER_PACKAGE_NAME)
                        .build());
    }

    @Test
    public void testFilterRequest_withLocalhostDomainInDeveloperMode_skipCheck() {
        mCustomAudienceServiceFilter =
                new CustomAudienceServiceFilter(
                        mSpyContext,
                        mConsentManagerMock,
                        FLAGS_WITH_ENROLLMENT_CHECK,
                        mAppImportanceFilter,
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy,
                        mMockThrottler);

        mCustomAudienceServiceFilter.filterRequest(
                SELLER_LOCALHOST,
                CALLER_PACKAGE_NAME,
                false,
                false,
                MY_UID,
                API_NAME,
                Throttler.ApiKey.UNKNOWN,
                DevContext.builder()
                        .setDevOptionsEnabled(true)
                        .setCallingAppPackageName(CALLER_PACKAGE_NAME)
                        .build());

        verify(mFledgeAuthorizationFilterSpy, never())
                .assertAdTechAllowed(any(), anyString(), any(), anyInt(), anyInt());
    }

    @Test
    public void testFilterRequest_nullAdTech_skipCheck() {
        mCustomAudienceServiceFilter.filterRequest(
                null,
                CALLER_PACKAGE_NAME,
                false,
                false,
                MY_UID,
                API_NAME,
                Throttler.ApiKey.UNKNOWN,
                DevContext.createForDevOptionsDisabled());

        verify(mFledgeAuthorizationFilterSpy, never())
                .assertAdTechAllowed(any(), anyString(), any(), anyInt(), anyInt());
    }

    @Test
    public void testFilterRequest_appNotInAllowlist_throws() {
        doThrow(new FledgeAllowListsFilter.AppNotAllowedException())
                .when(mFledgeAllowListsFilterSpy)
                .assertAppInAllowlist(CALLER_PACKAGE_NAME, API_NAME, API_CUSTOM_AUDIENCES);

        assertThrows(
                FledgeAllowListsFilter.AppNotAllowedException.class,
                () ->
                        mCustomAudienceServiceFilter.filterRequest(
                                SELLER_VALID,
                                CALLER_PACKAGE_NAME,
                                false,
                                false,
                                MY_UID,
                                API_NAME,
                                Throttler.ApiKey.UNKNOWN,
                                DevContext.createForDevOptionsDisabled()));
    }

    @Test
    public void testFilterRequest_enforceConsentTrue_hasUserConsentForApp_succeeds() {
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(CALLER_PACKAGE_NAME);
        mCustomAudienceServiceFilter.filterRequest(
                SELLER_VALID,
                CALLER_PACKAGE_NAME,
                false,
                true,
                MY_UID,
                API_NAME,
                Throttler.ApiKey.UNKNOWN,
                DevContext.createForDevOptionsDisabled());
    }

    @Test
    public void testFilterRequest_enforceConsentTrue_lacksUserConsentForApp_throws() {
        doReturn(true)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(CALLER_PACKAGE_NAME);

        assertThrows(
                ConsentManager.RevokedConsentException.class,
                () ->
                        mCustomAudienceServiceFilter.filterRequest(
                                SELLER_VALID,
                                CALLER_PACKAGE_NAME,
                                false,
                                true,
                                MY_UID,
                                API_NAME,
                                Throttler.ApiKey.UNKNOWN,
                                DevContext.createForDevOptionsDisabled()));
    }

    @Test
    public void testFilterRequest_enforceConsentFalse_lacksUserConsentForApp_throws() {
        doReturn(true)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(CALLER_PACKAGE_NAME);
        mCustomAudienceServiceFilter.filterRequest(
                SELLER_VALID,
                CALLER_PACKAGE_NAME,
                false,
                false,
                MY_UID,
                API_NAME,
                Throttler.ApiKey.UNKNOWN,
                DevContext.createForDevOptionsDisabled());
    }

    @Test
    public void testFilterRequestAndExtractIdentifier_invalidPackageName_throws() {
        assertThrows(
                FledgeAuthorizationFilter.CallerMismatchException.class,
                () ->
                        mCustomAudienceServiceFilter.filterRequestAndExtractIdentifier(
                                getValidFetchUriByBuyer(SELLER_VALID),
                                "invalidPackageName",
                                false,
                                false,
                                false,
                                MY_UID,
                                API_NAME,
                                Throttler.ApiKey.UNKNOWN,
                                DevContext.createForDevOptionsDisabled()));
    }

    @Test
    public void testFilterRequestAndExtractIdentifier_throttled_throws() {
        when(mMockThrottler.tryAcquire(eq(Throttler.ApiKey.UNKNOWN), anyString()))
                .thenReturn(false);

        assertThrows(
                LimitExceededException.class,
                () ->
                        mCustomAudienceServiceFilter.filterRequestAndExtractIdentifier(
                                getValidFetchUriByBuyer(SELLER_VALID),
                                CALLER_PACKAGE_NAME,
                                false,
                                false,
                                false,
                                MY_UID,
                                API_NAME,
                                Throttler.ApiKey.UNKNOWN,
                                DevContext.createForDevOptionsDisabled()));
    }

    @Test
    public void testFilterRequestAndExtractIdentifier_enforceForegroundTrue_foregroundCheckFails() {
        doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(MY_UID, API_NAME, null);

        assertThrows(
                AppImportanceFilter.WrongCallingApplicationStateException.class,
                () ->
                        mCustomAudienceServiceFilter.filterRequestAndExtractIdentifier(
                                getValidFetchUriByBuyer(SELLER_VALID),
                                CALLER_PACKAGE_NAME,
                                false,
                                true,
                                false,
                                MY_UID,
                                API_NAME,
                                Throttler.ApiKey.UNKNOWN,
                                DevContext.createForDevOptionsDisabled()));
    }

    @Test
    public void
            testFilterRequestAndExtractIdentifier_enforceForegroundFalse_foregroundCheckFails() {
        doReturn(SELLER_VALID)
                .when(mFledgeAuthorizationFilterSpy)
                .getAndAssertAdTechFromUriAllowed(
                        mSpyContext,
                        CALLER_PACKAGE_NAME,
                        getValidFetchUriByBuyer(SELLER_VALID),
                        API_NAME,
                        API_CUSTOM_AUDIENCES);

        doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(Process.myUid(), API_NAME, null);

        mCustomAudienceServiceFilter.filterRequestAndExtractIdentifier(
                getValidFetchUriByBuyer(SELLER_VALID),
                CALLER_PACKAGE_NAME,
                false,
                false,
                false,
                MY_UID,
                API_NAME,
                Throttler.ApiKey.UNKNOWN,
                DevContext.createForDevOptionsDisabled());
    }

    @Test
    public void testFilterRequestAndExtractIdentifier_enableEnrollmentCheck_invalidAdTech_throws() {
        doReturn(SELLER_VALID)
                .when(mFledgeAuthorizationFilterSpy)
                .getAndAssertAdTechFromUriAllowed(
                        mSpyContext,
                        CALLER_PACKAGE_NAME,
                        getValidFetchUriByBuyer(SELLER_VALID),
                        API_NAME,
                        API_CUSTOM_AUDIENCES);

        doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mFledgeAuthorizationFilterSpy)
                .getAndAssertAdTechFromUriAllowed(
                        mSpyContext,
                        CALLER_PACKAGE_NAME,
                        getValidFetchUriByBuyer(SELLER_VALID),
                        API_NAME,
                        API_CUSTOM_AUDIENCES);

        assertThrows(
                FledgeAuthorizationFilter.AdTechNotAllowedException.class,
                () ->
                        mCustomAudienceServiceFilter.filterRequestAndExtractIdentifier(
                                getValidFetchUriByBuyer(SELLER_VALID),
                                CALLER_PACKAGE_NAME,
                                false,
                                false,
                                false,
                                MY_UID,
                                API_NAME,
                                Throttler.ApiKey.UNKNOWN,
                                DevContext.createForDevOptionsDisabled()));
    }

    @Test
    public void
            testFilterRequestAndExtractIdentifier_disableEnrollmentCheck_eTLDPlus1NotExtracted() {
        AdTechIdentifier seller =
                mCustomAudienceServiceFilter.filterRequestAndExtractIdentifier(
                        getValidFetchUriByBuyer(SELLER_VALID),
                        CALLER_PACKAGE_NAME,
                        true,
                        false,
                        false,
                        MY_UID,
                        API_NAME,
                        Throttler.ApiKey.UNKNOWN,
                        DevContext.createForDevOptionsDisabled());

        // Assert URI host is extracted as the ad tech identifier
        assertThat(seller).isEqualTo(SELLER_VALID);

        // Assert eTLD+1 extractions and the implied enrollment check is skipped.
        verify(mFledgeAuthorizationFilterSpy, never())
                .getAndAssertAdTechFromUriAllowed(
                        mSpyContext,
                        CALLER_PACKAGE_NAME,
                        getValidFetchUriByBuyer(SELLER_VALID),
                        API_NAME,
                        API_CUSTOM_AUDIENCES);
    }

    @Test
    public void testFilterRequestAndExtractIdentifier_appNotInAllowlist_throws() {
        doReturn(SELLER_VALID)
                .when(mFledgeAuthorizationFilterSpy)
                .getAndAssertAdTechFromUriAllowed(
                        mSpyContext,
                        CALLER_PACKAGE_NAME,
                        getValidFetchUriByBuyer(SELLER_VALID),
                        API_NAME,
                        API_CUSTOM_AUDIENCES);

        doThrow(new FledgeAllowListsFilter.AppNotAllowedException())
                .when(mFledgeAllowListsFilterSpy)
                .assertAppInAllowlist(CALLER_PACKAGE_NAME, API_NAME, API_CUSTOM_AUDIENCES);

        assertThrows(
                FledgeAllowListsFilter.AppNotAllowedException.class,
                () ->
                        mCustomAudienceServiceFilter.filterRequestAndExtractIdentifier(
                                getValidFetchUriByBuyer(SELLER_VALID),
                                CALLER_PACKAGE_NAME,
                                false,
                                false,
                                false,
                                MY_UID,
                                API_NAME,
                                Throttler.ApiKey.UNKNOWN,
                                DevContext.createForDevOptionsDisabled()));
    }

    @Test
    public void testFilterRequestAndExtractIdentifier_enforceConsentTrue_hasUserConsentForApp() {
        doReturn(SELLER_VALID)
                .when(mFledgeAuthorizationFilterSpy)
                .getAndAssertAdTechFromUriAllowed(
                        mSpyContext,
                        CALLER_PACKAGE_NAME,
                        getValidFetchUriByBuyer(SELLER_VALID),
                        API_NAME,
                        API_CUSTOM_AUDIENCES);

        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(CALLER_PACKAGE_NAME);

        mCustomAudienceServiceFilter.filterRequestAndExtractIdentifier(
                getValidFetchUriByBuyer(SELLER_VALID),
                CALLER_PACKAGE_NAME,
                false,
                false,
                true,
                MY_UID,
                API_NAME,
                Throttler.ApiKey.UNKNOWN,
                DevContext.createForDevOptionsDisabled());
    }

    @Test
    public void testFilterRequestAndExtractIdentifier_enforceConsentTrue_lacksUserConsentForApp() {
        doReturn(SELLER_VALID)
                .when(mFledgeAuthorizationFilterSpy)
                .getAndAssertAdTechFromUriAllowed(
                        mSpyContext,
                        CALLER_PACKAGE_NAME,
                        getValidFetchUriByBuyer(SELLER_VALID),
                        API_NAME,
                        API_CUSTOM_AUDIENCES);

        doReturn(true)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(CALLER_PACKAGE_NAME);

        assertThrows(
                ConsentManager.RevokedConsentException.class,
                () ->
                        mCustomAudienceServiceFilter.filterRequestAndExtractIdentifier(
                                getValidFetchUriByBuyer(SELLER_VALID),
                                CALLER_PACKAGE_NAME,
                                false,
                                false,
                                true,
                                MY_UID,
                                API_NAME,
                                Throttler.ApiKey.UNKNOWN,
                                DevContext.createForDevOptionsDisabled()));
    }

    @Test
    public void testFilterRequestAndExtractIdentifier_enforceConsentFalse_lacksUserConsentForApp() {
        doReturn(SELLER_VALID)
                .when(mFledgeAuthorizationFilterSpy)
                .getAndAssertAdTechFromUriAllowed(
                        mSpyContext,
                        CALLER_PACKAGE_NAME,
                        getValidFetchUriByBuyer(SELLER_VALID),
                        API_NAME,
                        API_CUSTOM_AUDIENCES);

        doReturn(true)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(CALLER_PACKAGE_NAME);

        mCustomAudienceServiceFilter.filterRequestAndExtractIdentifier(
                getValidFetchUriByBuyer(SELLER_VALID),
                CALLER_PACKAGE_NAME,
                false,
                false,
                false,
                MY_UID,
                API_NAME,
                Throttler.ApiKey.UNKNOWN,
                DevContext.createForDevOptionsDisabled());
    }
}
