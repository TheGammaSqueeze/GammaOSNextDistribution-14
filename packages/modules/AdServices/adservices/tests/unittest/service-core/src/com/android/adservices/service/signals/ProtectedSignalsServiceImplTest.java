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

package com.android.adservices.service.signals;

import static com.android.adservices.mockito.MockitoExpectations.mockLogApiCallStats;
import static com.android.adservices.service.common.Throttler.ApiKey.PROTECTED_SIGNAL_API_UPDATE_SIGNALS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.AdServicesPermissions;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.signals.UpdateSignalsCallback;
import android.adservices.signals.UpdateSignalsInput;
import android.content.Context;
import android.net.Uri;
import android.os.LimitExceededException;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.NoFailureSyncCallback;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.CallingAppUidSupplier;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.ProtectedSignalsServiceFilter;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.concurrent.ExecutorService;

public class ProtectedSignalsServiceImplTest {

    private static final int API_NAME = AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS;
    private static final int UID = 42;
    private static final AdTechIdentifier ADTECH = AdTechIdentifier.fromString("example.com");
    private static final Uri URI = Uri.parse("https://example.com");
    private static final String PACKAGE = CommonFixture.TEST_PACKAGE_NAME_1;
    private static final String EXCEPTION_MESSAGE = "message";

    private MockitoSession mStaticMockSession = null;
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final ExecutorService DIRECT_EXECUTOR = MoreExecutors.newDirectExecutorService();
    @Mock private UpdateSignalsOrchestrator mUpdateSignalsOrchestratorMock;
    @Mock private FledgeAuthorizationFilter mFledgeAuthorizationFilterMock;
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private DevContextFilter mDevContextFilterMock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;
    @Mock private CallingAppUidSupplier mCallingAppUidSupplierMock;
    @Mock private ProtectedSignalsServiceFilter mProtectedSignalsServiceFilterMock;
    @Mock private UpdateSignalsCallback mUpdateSignalsCallbackMock;

    @Captor ArgumentCaptor<FledgeErrorResponse> mErrorCaptor;

    private ProtectedSignalsServiceImpl mProtectedSignalsService;
    private DevContext mDevContext;
    private UpdateSignalsInput mInput;
    private Flags mFlags;
    private NoFailureSyncCallback<ApiCallStats> logApiCallStatsCallback;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Before
    public void setup() {

        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(PeriodicEncodingJobService.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();

        mFlags = new ProtectedSignalsServiceImplTestFlags();
        logApiCallStatsCallback = mockLogApiCallStats(mAdServicesLoggerMock);

        mProtectedSignalsService =
                new ProtectedSignalsServiceImpl(
                        CONTEXT,
                        mUpdateSignalsOrchestratorMock,
                        mFledgeAuthorizationFilterMock,
                        mConsentManagerMock,
                        mDevContextFilterMock,
                        DIRECT_EXECUTOR,
                        mAdServicesLoggerMock,
                        mFlags,
                        mCallingAppUidSupplierMock,
                        mProtectedSignalsServiceFilterMock);

        mDevContext =
                DevContext.builder()
                        .setDevOptionsEnabled(false)
                        .setCallingAppPackageName(PACKAGE)
                        .build();
        mInput = new UpdateSignalsInput.Builder(URI, PACKAGE).build();

        // Set up the mocks for a success flow -- indivual tests that want a failure can overwrite
        when(mCallingAppUidSupplierMock.getCallingAppUid()).thenReturn(UID);
        when(mDevContextFilterMock.createDevContext()).thenReturn(mDevContext);
        when(mProtectedSignalsServiceFilterMock.filterRequestAndExtractIdentifier(
                        eq(URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(true),
                        eq(false),
                        eq(UID),
                        eq(API_NAME),
                        eq(PROTECTED_SIGNAL_API_UPDATE_SIGNALS),
                        eq(mDevContext)))
                .thenReturn(ADTECH);
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(eq(PACKAGE)))
                .thenReturn(false);
        when(mConsentManagerMock.isPasFledgeConsentGiven()).thenReturn(true);
        SettableFuture<Object> emptyReturn = SettableFuture.create();
        emptyReturn.set(new Object());
        when(mUpdateSignalsOrchestratorMock.orchestrateUpdate(
                        eq(URI), eq(ADTECH), eq(PACKAGE), eq(mDevContext)))
                .thenReturn(FluentFuture.from(emptyReturn));
        doNothing()
                .when(
                        () ->
                                PeriodicEncodingJobService.scheduleIfNeeded(
                                        any(), any(), anyBoolean()));
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Test
    public void testUpdateSignalsSuccess() throws Exception {
        mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(
                        eq(CONTEXT),
                        eq(PACKAGE),
                        eq(API_NAME),
                        eq(AdServicesPermissions.ACCESS_ADSERVICES_PROTECTED_SIGNALS));
        verify(mCallingAppUidSupplierMock).getCallingAppUid();
        verify(mDevContextFilterMock).createDevContext();
        verify(mProtectedSignalsServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        eq(URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(true),
                        eq(false),
                        eq(UID),
                        eq(API_NAME),
                        eq(PROTECTED_SIGNAL_API_UPDATE_SIGNALS),
                        eq(mDevContext));
        verify(mConsentManagerMock).isFledgeConsentRevokedForAppAfterSettingFledgeUse(eq(PACKAGE));
        verify(mUpdateSignalsOrchestratorMock)
                .orchestrateUpdate(eq(URI), eq(ADTECH), eq(PACKAGE), eq(mDevContext));
        verify(mUpdateSignalsCallbackMock).onSuccess();
        verifyUpdateSignalsApiUsageLog(AdServicesStatusUtils.STATUS_SUCCESS, PACKAGE);
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(1));
    }

    @Test
    public void testUpdateSignalsNullInput() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> mProtectedSignalsService.updateSignals(null, mUpdateSignalsCallbackMock));
        verifyUpdateSignalsApiUsageLog(
                AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, /* packageName */"");
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(0));
    }

    @Test
    public void testUpdateSignalsNullCallback() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> mProtectedSignalsService.updateSignals(mInput, null));
        verifyUpdateSignalsApiUsageLog(AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, PACKAGE);
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(0));
    }

    @Test
    public void testUpdateSignalsExceptionGettingUid() throws Exception {
        when(mCallingAppUidSupplierMock.getCallingAppUid()).thenThrow(new IllegalStateException());
        assertThrows(
                IllegalStateException.class,
                () -> mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock));
        verifyUpdateSignalsApiUsageLog(AdServicesStatusUtils.STATUS_INTERNAL_ERROR, PACKAGE);
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(0));
    }

    @Test
    public void testUpdateSignalsFilterException() throws Exception {
        when(mProtectedSignalsServiceFilterMock.filterRequestAndExtractIdentifier(
                        eq(URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(true),
                        eq(false),
                        eq(UID),
                        eq(API_NAME),
                        eq(PROTECTED_SIGNAL_API_UPDATE_SIGNALS),
                        eq(mDevContext)))
                .thenThrow(new LimitExceededException(EXCEPTION_MESSAGE));
        mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock);

        verify(mUpdateSignalsCallbackMock).onFailure(mErrorCaptor.capture());
        FledgeErrorResponse actual = mErrorCaptor.getValue();
        assertEquals(AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED, actual.getStatusCode());
        assertEquals(EXCEPTION_MESSAGE, actual.getErrorMessage());
        verifyZeroInteractions(mAdServicesLoggerMock);
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(0));
    }

    @Test
    public void testUpdateSignalsIllegalArgumentException() throws Exception {
        IllegalArgumentException exception = new IllegalArgumentException(EXCEPTION_MESSAGE);
        SettableFuture<Object> future = SettableFuture.create();
        future.setException(exception);
        when(mUpdateSignalsOrchestratorMock.orchestrateUpdate(
                        eq(URI), eq(ADTECH), eq(PACKAGE), eq(mDevContext)))
                .thenReturn(FluentFuture.from(future));
        mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock);

        verify(mUpdateSignalsCallbackMock).onFailure(mErrorCaptor.capture());
        FledgeErrorResponse actual = mErrorCaptor.getValue();
        assertEquals(AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, actual.getStatusCode());
        assertEquals(EXCEPTION_MESSAGE, actual.getErrorMessage());
        verifyUpdateSignalsApiUsageLog(AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, PACKAGE);
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(0));
    }

    @Test
    public void testUpdateSignalsNoConsentIfCallerNotHaveConsent() throws Exception {
        // Does NOT have FLEDGE consent
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(eq(PACKAGE)))
                .thenReturn(true);
        // Seen the PAS notification
        when(mConsentManagerMock.isPasFledgeConsentGiven()).thenReturn(true);

        mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock);
        verifyUpdateSignalsApiUsageLog(AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED, PACKAGE);
    }

    @Test
    public void testUpdateSignalsNoConsentIfUserNotSeenPASNotification() throws Exception {
        // Has FLEDGE consent
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(eq(PACKAGE)))
                .thenReturn(false);
        // Not seen the PAS notification
        when(mConsentManagerMock.isPasFledgeConsentGiven()).thenReturn(false);

        mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock);
        verifyUpdateSignalsApiUsageLog(AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED, PACKAGE);
    }

    @Test
    public void testUpdateSignalsNoConsent() throws Exception {
        // Revokes PA consent
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(eq(PACKAGE)))
                .thenReturn(true);
        // Revokes PAS consent
        when(mConsentManagerMock.isPasFledgeConsentGiven()).thenReturn(false);

        mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock);
        verifyUpdateSignalsApiUsageLog(AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED, PACKAGE);
        verify(mUpdateSignalsCallbackMock).onSuccess();
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(0));
    }

    @Test
    public void testUpdateSignalsCallbackException() throws Exception {
        doThrow(new RuntimeException()).when(mUpdateSignalsCallbackMock).onSuccess();
        mProtectedSignalsService.updateSignals(mInput, mUpdateSignalsCallbackMock);
        verifyUpdateSignalsApiUsageLog(AdServicesStatusUtils.STATUS_INTERNAL_ERROR, PACKAGE);
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(1));
    }

    private void verifyUpdateSignalsApiUsageLog(int resultCode, String packageName)
            throws InterruptedException {
        ApiCallStats apiCallStats = logApiCallStatsCallback.assertResultReceived();
        assertThat(apiCallStats.getApiName()).isEqualTo(
                AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS);
        assertThat(apiCallStats.getAppPackageName()).isEqualTo(packageName);
        assertThat(apiCallStats.getResultCode()).isEqualTo(resultCode);
        assertThat(apiCallStats.getLatencyMillisecond()).isAtLeast(0);
    }

    private static class ProtectedSignalsServiceImplTestFlags implements Flags {
        @Override
        public boolean getDisableFledgeEnrollmentCheck() {
            return false;
        }

        @Override
        public boolean getEnforceForegroundStatusForSignals() {
            return true;
        }

        @Override
        public boolean getFledgeAppPackageNameLoggingEnabled() {
            return true;
        }
    }
}
