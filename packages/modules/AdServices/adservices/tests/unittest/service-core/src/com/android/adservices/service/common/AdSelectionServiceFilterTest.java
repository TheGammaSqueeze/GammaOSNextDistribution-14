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

import static com.android.adservices.service.common.AppManifestConfigCall.API_AD_SELECTION;
import static com.android.adservices.service.common.AppManifestConfigCall.API_CUSTOM_AUDIENCES;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.os.LimitExceededException;
import android.os.Process;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.common.RequiresSdkLevelAtLeastS;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.Spy;

@RequiresSdkLevelAtLeastS
public final class AdSelectionServiceFilterTest extends AdServicesMockitoTestCase {

    private static final String CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final Flags TEST_FLAGS = FlagsFactory.getFlagsForTest();
    private static final Flags FLAGS_WITH_ENROLLMENT_CHECK =
            new Flags() {
                @Override
                public boolean getDisableFledgeEnrollmentCheck() {
                    return false;
                }
            };
    private static final Flags FLAGS_WITH_GA_UX_ENABLED =
            new Flags() {
                @Override
                public boolean getGaUxFeatureEnabled() {
                    return true;
                }

                @Override
                public boolean getDisableFledgeEnrollmentCheck() {
                    return true;
                }
            };
    @Mock AppImportanceFilter mAppImportanceFilter;

    private final AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);

    @Spy
    FledgeAllowListsFilter mFledgeAllowListsFilterSpy =
            new FledgeAllowListsFilter(TEST_FLAGS, mAdServicesLoggerMock);

    @Mock private ConsentManager mConsentManagerMock;

    @Spy
    FledgeAuthorizationFilter mFledgeAuthorizationFilterSpy =
            new FledgeAuthorizationFilter(
                    mSpyContext.getPackageManager(),
                    new EnrollmentDao(
                            mSpyContext, DbTestUtil.getSharedDbHelperForTest(), TEST_FLAGS),
                    mAdServicesLoggerMock);

    @Mock private Throttler mMockThrottler;

    private MockitoSession mStaticMockSession = null;

    private AdSelectionServiceFilter mAdSelectionServiceFilter;

    private static final AdTechIdentifier SELLER_VALID =
            AdTechIdentifier.fromString("developer.android.com");
    private static final AdTechIdentifier SELLER_LOCALHOST =
            AdTechIdentifier.fromString("localhost");

    private static final int API_NAME = 0;

    private static final int MY_UID = Process.myUid();

    @Before
    public void setUp() throws Exception {
        mAdSelectionServiceFilter =
                new AdSelectionServiceFilter(
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
    public void testFilterRequestSucceedsGaUxDisabled() {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        mAdSelectionServiceFilter.filterRequest(
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
    public void testFilterRequestSucceedsGaUxEnabled() {
        mAdSelectionServiceFilter =
                new AdSelectionServiceFilter(
                        mSpyContext,
                        mConsentManagerMock,
                        FLAGS_WITH_GA_UX_ENABLED,
                        mAppImportanceFilter,
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy,
                        mMockThrottler);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        mAdSelectionServiceFilter.filterRequest(
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
    public void testFilterRequestThrowsCallerMismatchExceptionWithInvalidPackageName() {
        FilterException exception =
                assertThrows(
                        FilterException.class,
                        () ->
                                mAdSelectionServiceFilter.filterRequest(
                                        SELLER_VALID,
                                        "invalidPackageName",
                                        false,
                                        true,
                                        MY_UID,
                                        API_NAME,
                                        Throttler.ApiKey.UNKNOWN,
                                        DevContext.createForDevOptionsDisabled()));
        assertThat(exception.getCause())
                .isInstanceOf(FledgeAuthorizationFilter.CallerMismatchException.class);
    }

    @Test
    public void testFilterRequestThrowsLimitExceededExceptionIfThrottled() {
        when(mMockThrottler.tryAcquire(eq(Throttler.ApiKey.UNKNOWN), anyString()))
                .thenReturn(false);
        FilterException exception =
                assertThrows(
                        FilterException.class,
                        () ->
                                mAdSelectionServiceFilter.filterRequest(
                                        SELLER_VALID,
                                        CALLER_PACKAGE_NAME,
                                        false,
                                        true,
                                        MY_UID,
                                        API_NAME,
                                        Throttler.ApiKey.UNKNOWN,
                                        DevContext.createForDevOptionsDisabled()));
        assertThat(exception.getCause()).isInstanceOf(LimitExceededException.class);
    }

    @Test
    public void
            testFilterRequestThrowsWrongCallingApplicationStateExceptionIfForegroundCheckFails() {
        doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(Process.myUid(), API_NAME, null);
        FilterException exception =
                assertThrows(
                        FilterException.class,
                        () ->
                                mAdSelectionServiceFilter.filterRequest(
                                        SELLER_VALID,
                                        CALLER_PACKAGE_NAME,
                                        true,
                                        true,
                                        MY_UID,
                                        API_NAME,
                                        Throttler.ApiKey.UNKNOWN,
                                        DevContext.createForDevOptionsDisabled()));
        assertThat(exception.getCause())
                .isInstanceOf(AppImportanceFilter.WrongCallingApplicationStateException.class);
    }

    @Test
    public void testFilterRequestSucceedsForBackgroundAppsWhenEnforceForegroundFalse() {
        doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(Process.myUid(), API_NAME, null);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        mAdSelectionServiceFilter.filterRequest(
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
    public void testFilterRequestThrowsAdTechNotAllowedExceptionWhenAdTechNotAuthorized() {

        // Create new AdSelectionServiceFilter with new flags
        mAdSelectionServiceFilter =
                new AdSelectionServiceFilter(
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
        FilterException exception =
                assertThrows(
                        FilterException.class,
                        () ->
                                mAdSelectionServiceFilter.filterRequest(
                                        SELLER_VALID,
                                        CALLER_PACKAGE_NAME,
                                        false,
                                        true,
                                        MY_UID,
                                        API_NAME,
                                        Throttler.ApiKey.UNKNOWN,
                                        DevContext.createForDevOptionsDisabled()));
        assertThat(exception.getCause())
                .isInstanceOf(FledgeAuthorizationFilter.AdTechNotAllowedException.class);
    }

    @Test
    public void testFilterRequestThrowsAppNotAllowedExceptionWhenAppNotInAllowlist() {
        doThrow(new FledgeAllowListsFilter.AppNotAllowedException())
                .when(mFledgeAllowListsFilterSpy)
                .assertAppInAllowlist(CALLER_PACKAGE_NAME, API_NAME, API_AD_SELECTION);
        FilterException exception =
                assertThrows(
                        FilterException.class,
                        () ->
                                mAdSelectionServiceFilter.filterRequest(
                                        SELLER_VALID,
                                        CALLER_PACKAGE_NAME,
                                        false,
                                        true,
                                        MY_UID,
                                        API_NAME,
                                        Throttler.ApiKey.UNKNOWN,
                                        DevContext.createForDevOptionsDisabled()));
        assertThat(exception.getCause())
                .isInstanceOf(FledgeAllowListsFilter.AppNotAllowedException.class);
    }

    @Test
    public void testFilterRequestThrowsRevokedConsentExceptionAppDoesNotHaveConsentGaUxDisabled() {
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        FilterException exception =
                assertThrows(
                        FilterException.class,
                        () ->
                                mAdSelectionServiceFilter.filterRequest(
                                        SELLER_VALID,
                                        CALLER_PACKAGE_NAME,
                                        false,
                                        true,
                                        MY_UID,
                                        API_NAME,
                                        Throttler.ApiKey.UNKNOWN,
                                        DevContext.createForDevOptionsDisabled()));
        assertThat(exception.getCause()).isInstanceOf(ConsentManager.RevokedConsentException.class);
    }

    @Test
    public void testFilterRequestSucceedsConsentRevokedEnforceConsentFalse() {
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        mAdSelectionServiceFilter.filterRequest(
                SELLER_VALID,
                CALLER_PACKAGE_NAME,
                false,
                false,
                MY_UID,
                API_NAME,
                Throttler.ApiKey.UNKNOWN,
                DevContext.createForDevOptionsDisabled());
        verifyNoMoreInteractions(mConsentManagerMock);
    }

    @Test
    public void testFilterRequestThrowsRevokedConsentExceptionAppDoesNotHaveConsentGaUxEnabled() {
        // Create new AdSelectionServiceFilter with new flags
        mAdSelectionServiceFilter =
                new AdSelectionServiceFilter(
                        mSpyContext,
                        mConsentManagerMock,
                        FLAGS_WITH_GA_UX_ENABLED,
                        mAppImportanceFilter,
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy,
                        mMockThrottler);
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        FilterException exception =
                assertThrows(
                        FilterException.class,
                        () ->
                                mAdSelectionServiceFilter.filterRequest(
                                        SELLER_VALID,
                                        CALLER_PACKAGE_NAME,
                                        false,
                                        true,
                                        MY_UID,
                                        API_NAME,
                                        Throttler.ApiKey.UNKNOWN,
                                        DevContext.createForDevOptionsDisabled()));
        assertThat(exception.getCause()).isInstanceOf(ConsentManager.RevokedConsentException.class);
    }

    @Test
    public void testFilterRequestDoesNotDoEnrollmentCheckWhenAdTechParamIsNull() {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);

        mAdSelectionServiceFilter.filterRequest(
                null,
                CALLER_PACKAGE_NAME,
                false,
                true,
                MY_UID,
                API_NAME,
                Throttler.ApiKey.UNKNOWN,
                DevContext.createForDevOptionsDisabled());

        verify(mFledgeAuthorizationFilterSpy, never())
                .assertAdTechAllowed(any(), anyString(), any(), anyInt(), anyInt());
    }

    @Test
    public void testFilterRequest_withLocalhostDomain_doesNotPass() {
        mAdSelectionServiceFilter =
                new AdSelectionServiceFilter(
                        mSpyContext,
                        mConsentManagerMock,
                        FLAGS_WITH_ENROLLMENT_CHECK,
                        mAppImportanceFilter,
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy,
                        mMockThrottler);

        FilterException exception =
                assertThrows(
                        FilterException.class,
                        () ->
                                mAdSelectionServiceFilter.filterRequest(
                                        SELLER_LOCALHOST,
                                        CALLER_PACKAGE_NAME,
                                        false,
                                        false,
                                        MY_UID,
                                        API_NAME,
                                        Throttler.ApiKey.UNKNOWN,
                                        DevContext.createForDevOptionsDisabled()));

        assertThat(exception.getCause())
                .isInstanceOf(FledgeAuthorizationFilter.AdTechNotAllowedException.class);
    }

    @Test
    public void testFilterRequest_withDeveloperMode_succeeds() {
        mAdSelectionServiceFilter.filterRequest(
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
        mAdSelectionServiceFilter =
                new AdSelectionServiceFilter(
                        mSpyContext,
                        mConsentManagerMock,
                        FLAGS_WITH_ENROLLMENT_CHECK,
                        mAppImportanceFilter,
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy,
                        mMockThrottler);

        mAdSelectionServiceFilter.filterRequest(
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
}
