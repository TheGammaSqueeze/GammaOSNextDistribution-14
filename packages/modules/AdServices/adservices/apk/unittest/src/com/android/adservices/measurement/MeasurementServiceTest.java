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
package com.android.adservices.measurement;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.download.MddJobService;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.PackageChangedReceiver;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.encryptionkey.EncryptionKeyJobService;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.measurement.DeleteExpiredJobService;
import com.android.adservices.service.measurement.DeleteUninstalledJobService;
import com.android.adservices.service.measurement.MeasurementImpl;
import com.android.adservices.service.measurement.attribution.AttributionFallbackJobService;
import com.android.adservices.service.measurement.attribution.AttributionJobService;
import com.android.adservices.service.measurement.registration.AsyncRegistrationFallbackJobService;
import com.android.adservices.service.measurement.registration.AsyncRegistrationQueueJobService;
import com.android.adservices.service.measurement.reporting.AggregateFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.AggregateReportingJobService;
import com.android.adservices.service.measurement.reporting.DebugReportingFallbackJobService;
import com.android.adservices.service.measurement.reporting.EventFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.EventReportingJobService;
import com.android.adservices.service.measurement.reporting.VerboseDebugReportingFallbackJobService;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.compatibility.common.util.TestUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;
import org.mockito.Mock;

import java.util.List;

/** Unit test for {@link com.android.adservices.measurement.MeasurementService}. */
@SpyStatic(AggregateReportingJobService.class)
@SpyStatic(AggregateFallbackReportingJobService.class)
@SpyStatic(AppImportanceFilter.class)
@SpyStatic(AttributionJobService.class)
@SpyStatic(AttributionFallbackJobService.class)
@SpyStatic(ConsentManager.class)
@SpyStatic(UxStatesManager.class)
@SpyStatic(DevContextFilter.class)
@SpyStatic(EnrollmentDao.class)
@SpyStatic(EventReportingJobService.class)
@SpyStatic(PackageChangedReceiver.class)
@SpyStatic(EventFallbackReportingJobService.class)
@SpyStatic(DeleteExpiredJobService.class)
@SpyStatic(DeleteUninstalledJobService.class)
@SpyStatic(MddJobService.class)
@SpyStatic(EncryptionKeyJobService.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(MeasurementImpl.class)
@SpyStatic(AsyncRegistrationQueueJobService.class)
@SpyStatic(AsyncRegistrationFallbackJobService.class)
@SpyStatic(VerboseDebugReportingFallbackJobService.class)
@SpyStatic(DebugReportingFallbackJobService.class)
public final class MeasurementServiceTest extends AdServicesExtendedMockitoTestCase {
    @Mock private ConsentManager mMockConsentManager;
    @Mock private DevContextFilter mDevContextFilter;
    @Mock private Flags mMockFlags;
    @Mock private MeasurementImpl mMockMeasurementImpl;
    @Mock private EnrollmentDao mMockEnrollmentDao;
    @Mock private AppImportanceFilter mMockAppImportanceFilter;

    private static final EnrollmentData ENROLLMENT =
            new EnrollmentData.Builder()
                    .setEnrollmentId("E1")
                    .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                    .setSdkNames("sdk1")
                    .setAttributionSourceRegistrationUrl(List.of("https://test.com/source"))
                    .setAttributionTriggerRegistrationUrl(List.of("https://test.com/trigger"))
                    .setAttributionReportingUrl(List.of("https://test.com"))
                    .setRemarketingResponseBasedRegistrationUrl(List.of("https://test.com"))
                    .setEncryptionKeyUrl("https://test.com/keys")
                    .build();

    /** Test kill switch off with consent given */
    @Test
    public void testBindableMeasurementService_killSwitchOff_gaUxEnabled_consentGiven()
            throws Exception {
        runWithMocks(
                /* killSwitchOff */ false,
                /* consentNotifiedState */
                /* consentGiven */ true,
                () -> {
                    // Execute
                    final IBinder binder = onCreateAndOnBindService();

                    // Verification
                    assertNotNull(binder);
                    verify(mMockConsentManager, never()).getConsent();
                    verify(mMockConsentManager, times(1))
                            .getConsent(eq(AdServicesApiType.MEASUREMENTS));
                    ExtendedMockito.verify(
                            () -> PackageChangedReceiver.enableReceiver(any(Context.class), any()));
                    assertJobScheduled(/* timesCalled */ 1);
                });
    }

    /** Test kill switch off with consent revoked */
    @Test
    public void testBindableMeasurementService_killSwitchOff_gaUxEnabled_consentRevoked()
            throws Exception {
        runWithMocks(
                /* killSwitchOff */ false,
                /* consentNotifiedState */
                /* consentRevoked */ false,
                () -> {
                    // Execute
                    final IBinder binder = onCreateAndOnBindService();

                    // Verification
                    assertNotNull(binder);
                    verify(mMockConsentManager, never()).getConsent();
                    verify(mMockConsentManager, times(1))
                            .getConsent(eq(AdServicesApiType.MEASUREMENTS));
                    assertJobScheduled(/* timesCalled */ 0);
                });
    }

    /** Test kill switch on */
    @Test
    public void testBindableMeasurementService_killSwitchOn_gaUxEnabled() throws Exception {
        runWithMocks(
                /* killSwitchOn */ true,
                /* consentGiven */ true,
                () -> {
                    // Execute
                    final IBinder binder = onCreateAndOnBindService();

                    // Verification
                    assertNull(binder);
                    verify(mMockConsentManager, never()).getConsent();
                    verify(mMockConsentManager, never()).getConsent(any());
                    assertJobScheduled(/* timesCalled */ 0);
                });
    }

    private Intent getIntentForMeasurementService() {
        return new Intent(ApplicationProvider.getApplicationContext(), MeasurementService.class);
    }

    private IBinder onCreateAndOnBindService() {
        MeasurementService spyMeasurementService = spy(new MeasurementService());
        doReturn(mock(PackageManager.class)).when(spyMeasurementService).getPackageManager();
        spyMeasurementService.onCreate();
        return spyMeasurementService.onBind(getIntentForMeasurementService());
    }

    private void runWithMocks(
            boolean killSwitchStatus,
            boolean consentStatus,
            TestUtils.RunnableWithThrow execute)
            throws Exception {
        doReturn(!killSwitchStatus).when(mMockFlags).getMeasurementEnabled();

        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
        ExtendedMockito.doReturn(mMockConsentManager).when(() -> ConsentManager.getInstance());
        ExtendedMockito.doReturn(mDevContextFilter)
                .when(() -> DevContextFilter.create(any(Context.class)));

        final AdServicesApiConsent mockConsent = mock(AdServicesApiConsent.class);
        doReturn(consentStatus).when(mockConsent).isGiven();

        doReturn(mockConsent)
                .when(mMockConsentManager)
                .getConsent(eq(AdServicesApiType.MEASUREMENTS));

        ExtendedMockito.doReturn(mMockEnrollmentDao).when(() -> EnrollmentDao.getInstance(any()));
        doReturn(ENROLLMENT).when(mMockEnrollmentDao).getEnrollmentDataFromMeasurementUrl(any());
        ExtendedMockito.doReturn(mMockMeasurementImpl)
                .when(() -> MeasurementImpl.getInstance(any()));

        ExtendedMockito.doReturn(mMockAppImportanceFilter)
                .when(() -> AppImportanceFilter.create(any(), anyInt(), any()));

        ExtendedMockito.doReturn(true)
                .when(() -> PackageChangedReceiver.enableReceiver(any(Context.class), any()));
        ExtendedMockito.doNothing()
                .when(() -> AggregateReportingJobService.scheduleIfNeeded(any(), anyBoolean()));
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AggregateFallbackReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean()));
        ExtendedMockito.doNothing()
                .when(() -> AttributionJobService.scheduleIfNeeded(any(), anyBoolean()));
        ExtendedMockito.doNothing()
                .when(() -> AttributionFallbackJobService.scheduleIfNeeded(any(), anyBoolean()));
        ExtendedMockito.doNothing()
                .when(() -> EventReportingJobService.scheduleIfNeeded(any(), anyBoolean()));
        ExtendedMockito.doNothing()
                .when(() -> EventFallbackReportingJobService.scheduleIfNeeded(any(), anyBoolean()));
        ExtendedMockito.doNothing()
                .when(() -> DeleteExpiredJobService.scheduleIfNeeded(any(), anyBoolean()));
        ExtendedMockito.doNothing()
                .when(() -> DeleteUninstalledJobService.scheduleIfNeeded(any(), anyBoolean()));
        ExtendedMockito.doReturn(true)
                .when(() -> MddJobService.scheduleIfNeeded(any(), anyBoolean()));
        ExtendedMockito.doReturn(true)
                .when(() -> EncryptionKeyJobService.scheduleIfNeeded(any(), anyBoolean()));
        ExtendedMockito.doNothing()
                .when(() -> AsyncRegistrationQueueJobService.scheduleIfNeeded(any(), anyBoolean()));
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AsyncRegistrationFallbackJobService.scheduleIfNeeded(
                                        any(), anyBoolean()));
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                VerboseDebugReportingFallbackJobService.scheduleIfNeeded(
                                        any(), anyBoolean()));
        ExtendedMockito.doNothing()
                .when(() -> DebugReportingFallbackJobService.scheduleIfNeeded(any(), anyBoolean()));
        // Execute
        execute.run();
    }

    private void assertJobScheduled(int timesCalled) {
        ExtendedMockito.verify(
                () -> AggregateReportingJobService.scheduleIfNeeded(any(), anyBoolean()),
                times(timesCalled));
        ExtendedMockito.verify(
                () -> AggregateFallbackReportingJobService.scheduleIfNeeded(any(), anyBoolean()),
                times(timesCalled));
        ExtendedMockito.verify(
                () -> AttributionJobService.scheduleIfNeeded(any(), anyBoolean()),
                times(timesCalled));
        ExtendedMockito.verify(
                () -> AttributionFallbackJobService.scheduleIfNeeded(any(), anyBoolean()),
                times(timesCalled));
        ExtendedMockito.verify(
                () -> EventReportingJobService.scheduleIfNeeded(any(), anyBoolean()),
                times(timesCalled));
        ExtendedMockito.verify(
                () -> EventFallbackReportingJobService.scheduleIfNeeded(any(), anyBoolean()),
                times(timesCalled));
        ExtendedMockito.verify(
                () -> DeleteExpiredJobService.scheduleIfNeeded(any(), anyBoolean()),
                times(timesCalled));
        ExtendedMockito.verify(
                () -> DeleteUninstalledJobService.scheduleIfNeeded(any(), anyBoolean()),
                times(timesCalled));
        ExtendedMockito.verify(
                () -> MddJobService.scheduleIfNeeded(any(), anyBoolean()), times(timesCalled));
        ExtendedMockito.verify(
                () -> EncryptionKeyJobService.scheduleIfNeeded(any(), anyBoolean()),
                times(timesCalled));
        ExtendedMockito.verify(
                () -> AsyncRegistrationQueueJobService.scheduleIfNeeded(any(), anyBoolean()),
                times(timesCalled));
        ExtendedMockito.verify(
                () -> AsyncRegistrationFallbackJobService.scheduleIfNeeded(any(), anyBoolean()),
                times(timesCalled));
        ExtendedMockito.verify(
                () -> VerboseDebugReportingFallbackJobService.scheduleIfNeeded(any(), anyBoolean()),
                times(timesCalled));
        ExtendedMockito.verify(
                () -> DebugReportingFallbackJobService.scheduleIfNeeded(any(), anyBoolean()),
                times(timesCalled));
    }
}
