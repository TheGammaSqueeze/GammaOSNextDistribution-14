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

package com.android.adservices.service.measurement;

import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_DEV_OPTIONS_DISABLED_WHILE_USING_LOCALHOST;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_FOREGROUND_APP_NOT_IN_FOREGROUND;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_PACKAGE_NOT_IN_ALLOWLIST;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_UNSET;
import static android.adservices.common.AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_KILLSWITCH_ENABLED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_PERMISSION_NOT_REQUESTED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_NOTIFICATION_NOT_DISPLAYED_YET;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
import static android.adservices.measurement.MeasurementManager.MEASUREMENT_API_STATE_DISABLED;
import static android.adservices.measurement.MeasurementManager.MEASUREMENT_API_STATE_ENABLED;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__DELETE_REGISTRATIONS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_MEASUREMENT_API_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REGISTER_SOURCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REGISTER_SOURCES;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REGISTER_TRIGGER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REGISTER_WEB_SOURCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REGISTER_WEB_TRIGGER;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.CallerMetadata;
import android.adservices.measurement.DeletionParam;
import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.IMeasurementApiStatusCallback;
import android.adservices.measurement.IMeasurementCallback;
import android.adservices.measurement.MeasurementErrorResponse;
import android.adservices.measurement.MeasurementManager;
import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.SourceRegistrationRequest;
import android.adservices.measurement.SourceRegistrationRequestInternal;
import android.adservices.measurement.StatusParam;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebSourceRegistrationRequestInternal;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.adservices.measurement.WebTriggerRegistrationRequestInternal;
import android.content.Context;
import android.net.Uri;
import android.os.Binder;
import android.os.SystemClock;
import android.test.mock.MockContext;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.WebUtil;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AllowLists;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.PermissionHelper;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.shared.util.Clock;
import com.android.compatibility.common.util.TestUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Unit tests for {@link MeasurementServiceImpl} */
@SmallTest
public final class MeasurementServiceImplTest {

    private static final Uri APP_DESTINATION = Uri.parse("android-app://test.app-destination");
    private static final String APP_PACKAGE_NAME = "app.package.name";
    private static final Uri REGISTRATION_URI = WebUtil.validUri("https://registration-uri.test");
    private static final Uri LOCALHOST = Uri.parse("https://localhost");
    private static final String SDK_PACKAGE_NAME = "sdk.package.name";
    private static final int TIMEOUT = 5_000;
    private static final Uri WEB_DESTINATION = WebUtil.validUri("https://web-destination-uri.test");

    @Mock private AdServicesLogger mMockAdServicesLogger;
    @Mock private AppImportanceFilter mMockAppImportanceFilter;
    @Mock private ConsentManager mMockConsentManager;
    @Mock private Flags mMockFlags;
    @Mock private MeasurementImpl mMockMeasurementImpl;
    @Mock private Throttler mMockThrottler;
    @Mock private MockContext mMockContext;
    @Mock private DevContextFilter mDevContextFilter;

    @Rule
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .mockStatic(Binder.class)
                    .mockStatic(FlagsFactory.class)
                    .mockStatic(PermissionHelper.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    private MeasurementServiceImpl mMeasurementServiceImpl;
    private Map<Integer, Boolean> mKillSwitchSnapshot;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mKillSwitchSnapshot =
                new HashMap<>() {
                    {
                        put(AD_SERVICES_API_CALLED__API_NAME__REGISTER_SOURCE, false);
                        put(AD_SERVICES_API_CALLED__API_NAME__REGISTER_SOURCES, false);
                        put(AD_SERVICES_API_CALLED__API_NAME__REGISTER_TRIGGER, false);
                        put(AD_SERVICES_API_CALLED__API_NAME__REGISTER_WEB_SOURCE, false);
                        put(AD_SERVICES_API_CALLED__API_NAME__REGISTER_WEB_TRIGGER, false);
                        put(AD_SERVICES_API_CALLED__API_NAME__DELETE_REGISTRATIONS, false);
                        put(AD_SERVICES_API_CALLED__API_NAME__GET_MEASUREMENT_API_STATUS, false);
                    }
                };
    }

    @Test
    public void testRegisterSource_success() throws Exception {
        runWithMocks(Api.REGISTER_SOURCE, new AccessDenier(), this::registerSourceAndAssertSuccess);
    }

    @Test
    public void testRegisterSource_sessionStableEnabledAndKillSwitchFlipOn_success()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(true);
        runWithMocks(
                Api.REGISTER_SOURCE,
                new AccessDenier(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiRegisterSourceKillSwitch()).thenReturn(true);
                    registerSourceAndAssertSuccess();
                });
    }

    @Test
    public void testRegisterSource_sessionStableDisabledAndKillSwitchFlipOn_failureByKillSwitch()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(false);
        runWithMocks(
                Api.REGISTER_SOURCE,
                new AccessDenier(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiRegisterSourceKillSwitch()).thenReturn(true);
                    registerSourceAndAssertFailure(STATUS_KILLSWITCH_ENABLED);
                });
    }

    @Test
    public void testRegisterSource_sessionStableEnabledAndKillSwitchFlipOff_failureByKillSwitch()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(true);
        runWithMocks(
                Api.REGISTER_SOURCE,
                new AccessDenier().deniedByKillSwitch(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiRegisterSourceKillSwitch()).thenReturn(false);
                    registerSourceAndAssertFailure(STATUS_KILLSWITCH_ENABLED);
                });
    }

    @Test
    public void testRegisterSource_sessionStableDisabledAndKillSwitchFlipOff_success()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(false);
        runWithMocks(
                Api.REGISTER_SOURCE,
                new AccessDenier().deniedByKillSwitch(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiRegisterSourceKillSwitch()).thenReturn(false);
                    registerSourceAndAssertSuccess();
                });
    }

    private void registerSourceAndAssertSuccess() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<Integer> list = new ArrayList<>();

        mMeasurementServiceImpl.register(
                createRegistrationSourceRequest(),
                createCallerMetadata(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {
                        list.add(STATUS_SUCCESS);
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onFailure(MeasurementErrorResponse responseParcel) {}
                });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0)).isEqualTo(STATUS_SUCCESS);
        assertPackageNameLogged();
    }

    private void registerSourceAndAssertFailure(@AdServicesStatusUtils.StatusCode int status)
            throws InterruptedException {
        registerSourceAndAssertFailure(
                status,
                createRegistrationSourceRequest(),
                /* assertFailureReason = */ false,
                FAILURE_REASON_UNSET);
    }

    private void registerSourceAndAssertFailure(
            @AdServicesStatusUtils.StatusCode int status,
            RegistrationRequest registrationSourceRequest)
            throws InterruptedException {
        registerSourceAndAssertFailure(
                status,
                registrationSourceRequest,
                /* assertFailureReason = */ false,
                FAILURE_REASON_UNSET);
    }

    private void registerSourceAndAssertFailure(
            @AdServicesStatusUtils.StatusCode int status,
            RegistrationRequest registrationSourceRequest,
            boolean assertFailureReason,
            int failureReason)
            throws InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<MeasurementErrorResponse> errorContainer = new ArrayList<>();
        mMeasurementServiceImpl.register(
                registrationSourceRequest,
                createCallerMetadata(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {}

                    @Override
                    public void onFailure(MeasurementErrorResponse responseParcel) {
                        errorContainer.add(responseParcel);
                        countDownLatch.countDown();
                    }
                });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        verify(mMockMeasurementImpl, never()).register(any(), anyBoolean(), anyLong());
        Assert.assertEquals(1, errorContainer.size());
        Assert.assertEquals(status, errorContainer.get(0).getStatusCode());
        if (assertFailureReason) {
            assertFailureReasonLogged(failureReason);
        }
    }

    @Test
    public void testRegisterSource_failureByDevContextAccessResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_SOURCE,
                new AccessDenier().deniedByDevContext(),
                () ->
                        registerSourceAndAssertFailure(
                                STATUS_UNAUTHORIZED, createRegistrationSourceRequest(true)));
    }

    @Test
    public void testRegisterSource_failureByDevContextAccessResolver_logFailureReason()
            throws Exception {
        runWithMocks(
                Api.REGISTER_SOURCE,
                new AccessDenier().deniedByDevContext(),
                () ->
                        registerSourceAndAssertFailure(
                                STATUS_UNAUTHORIZED,
                                createRegistrationSourceRequest(true),
                                /* assertFailureReason= */ true,
                                FAILURE_REASON_DEV_OPTIONS_DISABLED_WHILE_USING_LOCALHOST));
    }

    @Test
    public void testRegisterSource_failureByAppPackageMsmtApiAccessResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_SOURCE,
                new AccessDenier().deniedByAppPackageMsmtApiApp(),
                () -> registerSourceAndAssertFailure(STATUS_CALLER_NOT_ALLOWED));
    }

    @Test
    public void testRegisterSource_failureByAppPackageMsmtApiAccessResolver_logFailureReason()
            throws Exception {
        runWithMocks(
                Api.REGISTER_SOURCE,
                new AccessDenier().deniedByAppPackageMsmtApiApp(),
                () ->
                        registerSourceAndAssertFailure(
                                STATUS_CALLER_NOT_ALLOWED,
                                createRegistrationSourceRequest(),
                                /* assertFailureReason= */ true,
                                FAILURE_REASON_PACKAGE_NOT_IN_ALLOWLIST));
    }

    @Test
    public void testRegisterSource_failureByAttributionPermissionResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_SOURCE,
                new AccessDenier().deniedByAttributionPermission(),
                () -> registerSourceAndAssertFailure(STATUS_PERMISSION_NOT_REQUESTED));
    }

    @Test
    public void testRegisterSource_failureByConsentResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_SOURCE,
                new AccessDenier().deniedByConsent(),
                () -> registerSourceAndAssertFailure(STATUS_USER_CONSENT_REVOKED));
    }

    @Test
    public void testRegisterSource_failureByConsentNotifiedResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_SOURCE,
                new AccessDenier().deniedByConsentNotificationNotDisplayed().deniedByConsent(),
                () ->
                        registerSourceAndAssertFailure(
                                STATUS_USER_CONSENT_NOTIFICATION_NOT_DISPLAYED_YET));
    }

    @Test
    public void testRegisterSource_failureByForegroundEnforcementAccessResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_SOURCE,
                new AccessDenier().deniedByForegroundEnforcement(),
                () -> registerSourceAndAssertFailure(STATUS_BACKGROUND_CALLER));
    }

    @Test
    public void testRegisterSource_failureByForegroundEnforcementAccessResolver_logFailureReason()
            throws Exception {
        runWithMocks(
                Api.REGISTER_SOURCE,
                new AccessDenier().deniedByForegroundEnforcement(),
                () ->
                        registerSourceAndAssertFailure(
                                STATUS_BACKGROUND_CALLER,
                                createRegistrationSourceRequest(),
                                /* assertFailureReason= */ true,
                                FAILURE_REASON_FOREGROUND_APP_NOT_IN_FOREGROUND));
    }

    @Test
    public void testRegisterSource_failureByKillSwitchAccessResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_SOURCE,
                new AccessDenier().deniedByKillSwitch(),
                () -> registerSourceAndAssertFailure(STATUS_KILLSWITCH_ENABLED));
    }

    @Test
    public void testRegisterSource_failureByThrottler() throws Exception {
        runWithMocks(
                Api.REGISTER_SOURCE,
                new AccessDenier().deniedByThrottler(),
                () -> registerSourceAndAssertFailure(STATUS_RATE_LIMIT_REACHED));
    }

    @Test
    public void testRegisterTrigger_success() throws Exception {
        runWithMocks(
                Api.REGISTER_TRIGGER, new AccessDenier(), this::registerTriggerAndAssertSuccess);
    }

    @Test
    public void testRegisterTrigger_consentNotNotifiedButConsentGiven_success() throws Exception {
        runWithMocks(
                Api.REGISTER_TRIGGER,
                new AccessDenier().deniedByConsentNotificationNotDisplayed(),
                this::registerTriggerAndAssertSuccess);
    }

    @Test
    public void testRegisterTrigger_sessionStableEnabledAndKillSwitchFlipOn_success()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(true);
        runWithMocks(
                Api.REGISTER_TRIGGER,
                new AccessDenier(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiRegisterTriggerKillSwitch()).thenReturn(true);
                    registerTriggerAndAssertSuccess();
                });
    }

    @Test
    public void testRegisterTrigger_sessionStableDisabledAndKillSwitchFlipOn_failureByKillSwitch()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(false);
        runWithMocks(
                Api.REGISTER_TRIGGER,
                new AccessDenier(),
                () -> {
                    when(mMockFlags.getMeasurementApiRegisterTriggerKillSwitch()).thenReturn(true);
                    registerTriggerAndAssertFailure(STATUS_KILLSWITCH_ENABLED);
                });
    }

    @Test
    public void testTriggerSource_sessionStableEnabledAndKillSwitchFlipOff_failureByKillSwitch()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(true);
        runWithMocks(
                Api.REGISTER_TRIGGER,
                new AccessDenier().deniedByKillSwitch(),
                () -> {
                    when(mMockFlags.getMeasurementApiRegisterTriggerKillSwitch()).thenReturn(false);
                    registerTriggerAndAssertFailure(STATUS_KILLSWITCH_ENABLED);
                });
    }

    @Test
    public void testRegisterTrigger_sessionStableDisabledAndKillSwitchFlipOff_success()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(false);
        runWithMocks(
                Api.REGISTER_TRIGGER,
                new AccessDenier().deniedByKillSwitch(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiRegisterTriggerKillSwitch()).thenReturn(false);
                    registerSourceAndAssertSuccess();
                });
    }

    private void registerTriggerAndAssertSuccess() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<Integer> list = new ArrayList<>();

        mMeasurementServiceImpl.register(
                createRegistrationTriggerRequest(),
                createCallerMetadata(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {
                        list.add(STATUS_SUCCESS);
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onFailure(MeasurementErrorResponse responseParcel) {}
                });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0)).isEqualTo(STATUS_SUCCESS);
        assertPackageNameLogged();
    }

    private void registerTriggerAndAssertFailure(@AdServicesStatusUtils.StatusCode int status)
            throws InterruptedException {
        registerTriggerAndAssertFailure(status, createRegistrationTriggerRequest());
    }

    private void registerTriggerAndAssertFailure(@AdServicesStatusUtils.StatusCode int status,
            RegistrationRequest registrationTriggerRequest) throws InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<MeasurementErrorResponse> errorContainer = new ArrayList<>();
        mMeasurementServiceImpl.register(
                registrationTriggerRequest,
                createCallerMetadata(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {}

                    @Override
                    public void onFailure(MeasurementErrorResponse responseParcel) {
                        errorContainer.add(responseParcel);
                        countDownLatch.countDown();
                    }
                });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        verify(mMockMeasurementImpl, never()).register(any(), anyBoolean(), anyLong());
        Assert.assertEquals(1, errorContainer.size());
        Assert.assertEquals(status, errorContainer.get(0).getStatusCode());
    }

    @Test
    public void testRegisterTrigger_failureByDevContextAccessResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_TRIGGER,
                new AccessDenier().deniedByDevContext(),
                () ->
                        registerTriggerAndAssertFailure(
                                STATUS_UNAUTHORIZED, createRegistrationTriggerRequest(true)));
    }

    @Test
    public void testRegisterTrigger_failureByAppPackageMsmtApiAccessResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_TRIGGER,
                new AccessDenier().deniedByAppPackageMsmtApiApp(),
                () -> registerTriggerAndAssertFailure(STATUS_CALLER_NOT_ALLOWED));
    }

    @Test
    public void testRegisterTrigger_failureByAttributionPermissionResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_TRIGGER,
                new AccessDenier().deniedByAttributionPermission(),
                () -> registerTriggerAndAssertFailure(STATUS_PERMISSION_NOT_REQUESTED));
    }

    @Test
    public void testRegisterTrigger_failureByConsentResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_TRIGGER,
                new AccessDenier().deniedByConsent(),
                () -> registerTriggerAndAssertFailure(STATUS_USER_CONSENT_REVOKED));
    }

    @Test
    public void testRegisterTrigger_failureByConsentNotifiedResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_TRIGGER,
                new AccessDenier().deniedByConsentNotificationNotDisplayed().deniedByConsent(),
                () ->
                        registerTriggerAndAssertFailure(
                                STATUS_USER_CONSENT_NOTIFICATION_NOT_DISPLAYED_YET));
    }

    @Test
    public void testRegisterTrigger_failureByForegroundEnforcementAccessResolver()
            throws Exception {
        runWithMocks(
                Api.REGISTER_TRIGGER,
                new AccessDenier().deniedByForegroundEnforcement(),
                () -> registerTriggerAndAssertFailure(STATUS_BACKGROUND_CALLER));
    }

    @Test
    public void testRegisterTrigger_failureByKillSwitchAccessResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_TRIGGER,
                new AccessDenier().deniedByKillSwitch(),
                () -> registerTriggerAndAssertFailure(STATUS_KILLSWITCH_ENABLED));
    }

    @Test
    public void testRegisterTrigger_failureByThrottler() throws Exception {
        runWithMocks(
                Api.REGISTER_TRIGGER,
                new AccessDenier().deniedByThrottler(),
                () -> registerTriggerAndAssertFailure(STATUS_RATE_LIMIT_REACHED));
    }

    @Test
    public void testRegister_invalidRequest_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mMeasurementServiceImpl.register(
                                /* request = */ null,
                                createCallerMetadata(),
                                new IMeasurementCallback.Default()));
    }

    @Test
    public void testRegister_invalidCallerMetadata_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mMeasurementServiceImpl.register(
                                createRegistrationSourceRequest(),
                                /* callerMetadata = */ null,
                                new IMeasurementCallback.Default()));
    }

    @Test
    public void testRegister_invalidCallback_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mMeasurementServiceImpl.register(
                                createRegistrationSourceRequest(),
                                createCallerMetadata(),
                                /* callback = */ null));
    }

    @Test
    public void testDeleteRegistrations_success() throws Exception {
        runWithMocks(
                Api.DELETE_REGISTRATIONS,
                new AccessDenier(),
                this::deleteRegistrationsAndAssertSuccess);
    }

    @Test
    public void testDeleteRegistrations_sessionStableEnabledAndKillSwitchFlipOn_success()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(true);
        runWithMocks(
                Api.DELETE_REGISTRATIONS,
                new AccessDenier(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiDeleteRegistrationsKillSwitch())
                            .thenReturn(true);
                    deleteRegistrationsAndAssertSuccess();
                });
    }

    @Test
    public void
            testDeleteRegistrations_sessionStableDisabledAndKillSwitchFlipOn_failureByKillSwitch()
                    throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(false);
        runWithMocks(
                Api.DELETE_REGISTRATIONS,
                new AccessDenier(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiDeleteRegistrationsKillSwitch())
                            .thenReturn(true);
                    deleteRegistrationsAndAssertFailure(STATUS_KILLSWITCH_ENABLED);
                });
    }

    @Test
    public void
            testDeleteRegistrations_sessionStableEnabledAndKillSwitchFlipOff_failureByKillSwitch()
                    throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(true);
        runWithMocks(
                Api.DELETE_REGISTRATIONS,
                new AccessDenier().deniedByKillSwitch(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiDeleteRegistrationsKillSwitch())
                            .thenReturn(false);
                    deleteRegistrationsAndAssertFailure(STATUS_KILLSWITCH_ENABLED);
                });
    }

    @Test
    public void testDeleteRegistrations_sessionStableDisabledAndKillSwitchFlipOff_success()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(false);
        runWithMocks(
                Api.DELETE_REGISTRATIONS,
                new AccessDenier().deniedByKillSwitch(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiDeleteRegistrationsKillSwitch())
                            .thenReturn(false);
                    deleteRegistrationsAndAssertSuccess();
                });
    }

    private void deleteRegistrationsAndAssertSuccess() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<Integer> list = new ArrayList<>();

        mMeasurementServiceImpl.deleteRegistrations(
                createDeletionRequest(),
                createCallerMetadata(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {
                        list.add(STATUS_SUCCESS);
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onFailure(MeasurementErrorResponse responseParcel) {}
                });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(list.get(0)).isEqualTo(STATUS_SUCCESS);
        assertThat(list.size()).isEqualTo(1);
        assertPackageNameLogged();
    }

    private void deleteRegistrationsAndAssertFailure(@AdServicesStatusUtils.StatusCode int status)
            throws InterruptedException {
        final List<MeasurementErrorResponse> errorContainer = new ArrayList<>();
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        mMeasurementServiceImpl.deleteRegistrations(
                createDeletionRequest(),
                createCallerMetadata(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {}

                    @Override
                    public void onFailure(MeasurementErrorResponse errorResponse) {
                        errorContainer.add(errorResponse);
                        countDownLatch.countDown();
                    }
                });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        verify(mMockMeasurementImpl, never()).deleteRegistrations(any());
        Assert.assertEquals(1, errorContainer.size());
        Assert.assertEquals(status, errorContainer.get(0).getStatusCode());
    }

    @Test
    public void testDeleteRegistrations_failureByAppPackageMsmtApiAccessResolver()
            throws Exception {
        runWithMocks(
                Api.DELETE_REGISTRATIONS,
                new AccessDenier().deniedByAppPackageMsmtApiApp(),
                () -> deleteRegistrationsAndAssertFailure(STATUS_CALLER_NOT_ALLOWED));
    }

    @Test
    public void testDeleteRegistrations_failureByAppPackageWebContextClientAccessResolver()
            throws Exception {
        runWithMocks(
                Api.DELETE_REGISTRATIONS,
                new AccessDenier().deniedByAppPackageWebContextClientApp(),
                () -> deleteRegistrationsAndAssertFailure(STATUS_CALLER_NOT_ALLOWED));
    }

    @Test
    public void testDeleteRegistrations_failureByForegroundEnforcementAccessResolver()
            throws Exception {
        runWithMocks(
                Api.DELETE_REGISTRATIONS,
                new AccessDenier().deniedByForegroundEnforcement(),
                () -> deleteRegistrationsAndAssertFailure(STATUS_BACKGROUND_CALLER));
    }

    @Test
    public void testDeleteRegistrations_failureByKillSwitchAccessResolver() throws Exception {
        runWithMocks(
                Api.DELETE_REGISTRATIONS,
                new AccessDenier().deniedByKillSwitch(),
                () -> deleteRegistrationsAndAssertFailure(STATUS_KILLSWITCH_ENABLED));
    }

    @Test
    public void testDeleteRegistrations_failureByThrottler() throws Exception {
        runWithMocks(
                Api.DELETE_REGISTRATIONS,
                new AccessDenier().deniedByThrottler(),
                () -> deleteRegistrationsAndAssertFailure(STATUS_RATE_LIMIT_REACHED));
    }

    @Test
    public void testDeleteRegistrations_invalidRequest_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mMeasurementServiceImpl.deleteRegistrations(
                                /* request = */ null,
                                createCallerMetadata(),
                                new IMeasurementCallback.Default()));
    }

    @Test
    public void testDeleteRegistrations_invalidCallerMetadata_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mMeasurementServiceImpl.deleteRegistrations(
                                createDeletionRequest(),
                                /* callerMetadata = */ null,
                                new IMeasurementCallback.Default()));
    }

    @Test
    public void testDeleteRegistrations_invalidCallback_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mMeasurementServiceImpl.deleteRegistrations(
                                createDeletionRequest(),
                                createCallerMetadata(),
                                /* callback = */ null));
    }

    @Test
    public void testGetMeasurementApiStatus_success() throws Exception {
        runWithMocks(Api.STATUS, new AccessDenier(), this::getMeasurementApiStatusAndAssertSuccess);
    }

    @Test
    public void testGetMsmtApiStatus_sessionStableEnabledAndKillSwitchFlipOn_success()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(true);
        runWithMocks(
                Api.STATUS,
                new AccessDenier(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiStatusKillSwitch()).thenReturn(true);
                    getMeasurementApiStatusAndAssertSuccess();
                });
    }

    @Test
    public void testGetMsmtApiStatus_sessionStableDisabledAndKillSwitchFlipOn_failureByKillSwitch()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(false);
        runWithMocks(
                Api.STATUS,
                new AccessDenier(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiStatusKillSwitch()).thenReturn(true);
                    getMeasurementApiStatusAndAssertFailure();
                });
    }

    @Test
    public void testGetMsmtApiStatus_sessionStableEnabledAndKillSwitchFlipOff_failureByKillSwitch()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(true);
        runWithMocks(
                Api.STATUS,
                new AccessDenier().deniedByKillSwitch(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiStatusKillSwitch()).thenReturn(false);
                    getMeasurementApiStatusAndAssertFailure();
                });
    }

    @Test
    public void testGetMsmtApiStatus_sessionStableDisabledAndKillSwitchFlipOff_success()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(false);
        runWithMocks(
                Api.STATUS,
                new AccessDenier().deniedByKillSwitch(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiStatusKillSwitch()).thenReturn(false);
                    getMeasurementApiStatusAndAssertSuccess();
                });
    }

    private void getMeasurementApiStatusAndAssertSuccess() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final AtomicInteger resultWrapper = new AtomicInteger();

        mMeasurementServiceImpl.getMeasurementApiStatus(
                createStatusParam(),
                createCallerMetadata(),
                new IMeasurementApiStatusCallback.Stub() {
                    @Override
                    public void onResult(int result) {
                        resultWrapper.set(result);
                        countDownLatch.countDown();
                    }
                });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(resultWrapper.get()).isEqualTo(MeasurementManager.MEASUREMENT_API_STATE_ENABLED);
        assertPackageNameLogged();
    }

    @Test
    public void testGetMeasurementApiStatus_consentNotNotifiedButConsentGiven_success()
            throws Exception {
        runWithMocks(
                Api.STATUS,
                new AccessDenier().deniedByConsentNotificationNotDisplayed(),
                this::getMeasurementApiStatusAndAssertSuccess);
    }

    @Test
    public void testGetMeasurementApiStatus_EnableApiStatusAllowListCheck_success()
            throws Exception {
        when(mMockFlags.getMsmtEnableApiStatusAllowListCheck()).thenReturn(true);
        runWithMocks(Api.STATUS, new AccessDenier(), this::getMeasurementApiStatusAndAssertSuccess);
    }

    private void getMeasurementApiStatusAndAssertFailure() throws InterruptedException {
        final CountDownLatch countDownLatchAny = new CountDownLatch(1);
        final AtomicInteger resultWrapper = new AtomicInteger();

        mMeasurementServiceImpl.getMeasurementApiStatus(
                createStatusParam(),
                createCallerMetadata(),
                new IMeasurementApiStatusCallback.Stub() {
                    @Override
                    public void onResult(int result) {
                        resultWrapper.set(result);
                        countDownLatchAny.countDown();
                    }
                });

        assertThat(countDownLatchAny.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(resultWrapper.get()).isEqualTo(MEASUREMENT_API_STATE_DISABLED);
    }

    private void getMeasurementApiStatusAndAssertEnabled() throws InterruptedException {
        final CountDownLatch countDownLatchAny = new CountDownLatch(1);
        final AtomicInteger resultWrapper = new AtomicInteger();

        mMeasurementServiceImpl.getMeasurementApiStatus(
                createStatusParam(),
                createCallerMetadata(),
                new IMeasurementApiStatusCallback.Stub() {
                    @Override
                    public void onResult(int result) {
                        resultWrapper.set(result);
                        countDownLatchAny.countDown();
                    }
                });

        assertThat(countDownLatchAny.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(resultWrapper.get()).isEqualTo(MEASUREMENT_API_STATE_ENABLED);
    }

    @Test
    public void testGetMeasurementApiStatus_failureByAppPackageMsmtApiAccessResolver()
            throws Exception {
        runWithMocks(
                Api.STATUS,
                new AccessDenier().deniedByAppPackageMsmtApiApp(),
                this::getMeasurementApiStatusAndAssertFailure);
    }

    @Test
    public void testGetMeasurementApiStatus_enabledAppPackageMsmtApiAccessResolver_flagAllowList()
            throws Exception {
        when(mMockFlags.getMsmtEnableApiStatusAllowListCheck()).thenReturn(true);
        runWithMocks(
                Api.STATUS,
                new AccessDenier().deniedByAppPackageMsmtApiApp(),
                this::getMeasurementApiStatusAndAssertEnabled);
    }

    @Test
    public void testGetMeasurementApiStatus_failureByForegroundEnforcementAccessResolver()
            throws Exception {
        runWithMocks(
                Api.STATUS,
                new AccessDenier().deniedByForegroundEnforcement(),
                this::getMeasurementApiStatusAndAssertFailure);
    }

    @Test
    public void
            testGetMeasurementApiStatus_failureByForegroundEnforcementAccessResolver_flagAllowList()
                    throws Exception {
        when(mMockFlags.getMsmtEnableApiStatusAllowListCheck()).thenReturn(true);
        runWithMocks(
                Api.STATUS,
                new AccessDenier().deniedByForegroundEnforcement(),
                this::getMeasurementApiStatusAndAssertFailure);
    }

    @Test
    public void testGetMeasurementApiStatus_failureByKillSwitchAccessResolver() throws Exception {
        runWithMocks(
                Api.STATUS,
                new AccessDenier().deniedByKillSwitch(),
                this::getMeasurementApiStatusAndAssertFailure);
    }

    @Test
    public void testGetMeasurementApiStatus_failureByKillSwitchAccessResolver_flagAllowList()
            throws Exception {
        when(mMockFlags.getMsmtEnableApiStatusAllowListCheck()).thenReturn(true);
        runWithMocks(
                Api.STATUS,
                new AccessDenier().deniedByKillSwitch(),
                this::getMeasurementApiStatusAndAssertFailure);
    }

    @Test
    public void testGetMeasurementApiStatus_failureByConsentAccessResolver() throws Exception {
        runWithMocks(
                Api.STATUS,
                new AccessDenier().deniedByConsent(),
                this::getMeasurementApiStatusAndAssertFailure);
    }

    @Test
    public void testGetMeasurementApiStatus_failureByConsentAccessResolver_flagAllowList()
            throws Exception {
        when(mMockFlags.getMsmtEnableApiStatusAllowListCheck()).thenReturn(true);
        runWithMocks(
                Api.STATUS,
                new AccessDenier().deniedByKillSwitch(),
                this::getMeasurementApiStatusAndAssertFailure);
    }

    @Test
    public void testGetMeasurementApiStatus_failureByConsentNotifiedAccessResolver()
            throws Exception {
        runWithMocks(
                Api.STATUS,
                new AccessDenier().deniedByConsentNotificationNotDisplayed().deniedByConsent(),
                this::getMeasurementApiStatusAndAssertFailure);
    }

    @Test
    public void testGetMeasurementApiStatus_invalidRequest_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mMeasurementServiceImpl.getMeasurementApiStatus(
                                /* request = */ null,
                                createCallerMetadata(),
                                new IMeasurementApiStatusCallback.Default()));
    }

    @Test
    public void testGetMeasurementApiStatus_invalidRequest_throwException_flagAllowList() {
        when(mMockFlags.getMsmtEnableApiStatusAllowListCheck()).thenReturn(true);
        assertThrows(
                NullPointerException.class,
                () ->
                        mMeasurementServiceImpl.getMeasurementApiStatus(
                                /* request */ null,
                                createCallerMetadata(),
                                new IMeasurementApiStatusCallback.Default()));
    }

    @Test
    public void testGetMeasurementApiStatus_invalidCallerMetadata_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mMeasurementServiceImpl.getMeasurementApiStatus(
                                createStatusParam(),
                                /* callerMetadata = */ null,
                                new IMeasurementApiStatusCallback.Default()));
    }

    @Test
    public void testGetMeasurementApiStatus_invalidCallerMetadata_throwException_flagAllowList() {
        when(mMockFlags.getMsmtEnableApiStatusAllowListCheck()).thenReturn(true);
        assertThrows(
                NullPointerException.class,
                () ->
                        mMeasurementServiceImpl.getMeasurementApiStatus(
                                createStatusParam(),
                                /* callerMetadata = */ null,
                                new IMeasurementApiStatusCallback.Default()));
    }

    @Test
    public void testGetMeasurementApiStatus_invalidCallback_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mMeasurementServiceImpl.getMeasurementApiStatus(
                                createStatusParam(),
                                createCallerMetadata(),
                                /* callback = */ null));
    }

    @Test
    public void testGetMeasurementApiStatus_invalidCallback_throwException_flagAllowList() {
        when(mMockFlags.getMsmtEnableApiStatusAllowListCheck()).thenReturn(true);
        assertThrows(
                NullPointerException.class,
                () ->
                        mMeasurementServiceImpl.getMeasurementApiStatus(
                                createStatusParam(),
                                createCallerMetadata(),
                                /* callback = */ null));
    }

    @Test
    public void registerWebSource_success() throws Exception {
        runWithMocks(
                Api.REGISTER_WEB_SOURCE,
                new AccessDenier(),
                this::registerWebSourceAndAssertSuccess);
    }

    @Test
    public void testRegisterWebSource_sessionStableEnabledAndKillSwitchFlipOn_success()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(true);
        runWithMocks(
                Api.REGISTER_WEB_SOURCE,
                new AccessDenier(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiRegisterWebSourceKillSwitch())
                            .thenReturn(true);
                    registerWebSourceAndAssertSuccess();
                });
    }

    @Test
    public void testRegisterWebSource_sessionStableDisabledAndKillSwitchFlipOn_failureByKillSwitch()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(false);
        runWithMocks(
                Api.REGISTER_WEB_SOURCE,
                new AccessDenier(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiRegisterWebSourceKillSwitch())
                            .thenReturn(true);
                    registerWebSourceAndAssertFailure(STATUS_KILLSWITCH_ENABLED);
                });
    }

    @Test
    public void testRegisterWebSource_sessionStableEnabledAndKillSwitchFlipOff_failureByKillSwitch()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(true);
        runWithMocks(
                Api.REGISTER_WEB_SOURCE,
                new AccessDenier().deniedByKillSwitch(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiRegisterWebSourceKillSwitch())
                            .thenReturn(false);
                    registerWebSourceAndAssertFailure(STATUS_KILLSWITCH_ENABLED);
                });
    }

    @Test
    public void testRegisterWebSource_sessionStableDisabledAndKillSwitchFlipOff_success()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(false);
        runWithMocks(
                Api.REGISTER_WEB_SOURCE,
                new AccessDenier().deniedByKillSwitch(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiRegisterWebSourceKillSwitch())
                            .thenReturn(false);
                    registerWebSourceAndAssertSuccess();
                });
    }

    private void registerWebSourceAndAssertSuccess() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<Integer> list = new ArrayList<>();

        mMeasurementServiceImpl.registerWebSource(
                createWebSourceRegistrationRequest(),
                createCallerMetadata(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {
                        list.add(STATUS_SUCCESS);
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onFailure(MeasurementErrorResponse measurementErrorResponse) {}
                });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(list.get(0)).isEqualTo(STATUS_SUCCESS);
        assertThat(list.size()).isEqualTo(1);
        assertPackageNameLogged();
    }

    @Test
    public void registerWebSource_consentNotNotifiedButConsentGiven_success() throws Exception {
        runWithMocks(
                Api.REGISTER_WEB_SOURCE,
                new AccessDenier().deniedByConsentNotificationNotDisplayed(),
                () -> {
                    CountDownLatch countDownLatch = new CountDownLatch(1);
                    final List<Integer> list = new ArrayList<>();

                    mMeasurementServiceImpl.registerWebSource(
                            createWebSourceRegistrationRequest(),
                            createCallerMetadata(),
                            new IMeasurementCallback.Stub() {
                                @Override
                                public void onResult() {
                                    list.add(STATUS_SUCCESS);
                                    countDownLatch.countDown();
                                }

                                @Override
                                public void onFailure(
                                        MeasurementErrorResponse measurementErrorResponse) {}
                            });

                    assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
                    assertThat(list.get(0)).isEqualTo(STATUS_SUCCESS);
                    assertThat(list.size()).isEqualTo(1);
                    assertPackageNameLogged();
                });
    }

    @Test
    public void registerSources_success() throws Exception {
        runWithMocks(
                Api.REGISTER_SOURCES, new AccessDenier(), this::registerSourcesAndAssertSuccess);
    }

    @Test
    public void testRegisterSources_sessionStableEnabledAndKillSwitchFlipOn_success()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(true);
        runWithMocks(
                Api.REGISTER_WEB_SOURCE,
                new AccessDenier(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiRegisterSourcesKillSwitch()).thenReturn(true);
                    registerSourcesAndAssertSuccess();
                });
    }

    @Test
    public void testRegisterSources_sessionStableDisabledAndKillSwitchFlipOn_failureByKillSwitch()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(false);
        runWithMocks(
                Api.REGISTER_SOURCES,
                new AccessDenier(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiRegisterSourcesKillSwitch()).thenReturn(true);
                    registerSourcesAndAssertFailure(STATUS_KILLSWITCH_ENABLED);
                });
    }

    @Test
    public void testRegisterSources_sessionStableEnabledAndKillSwitchFlipOff_failureByKillSwitch()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(true);
        runWithMocks(
                Api.REGISTER_SOURCES,
                new AccessDenier().deniedByKillSwitch(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiRegisterSourcesKillSwitch()).thenReturn(false);
                    registerSourcesAndAssertFailure(STATUS_KILLSWITCH_ENABLED);
                });
    }

    @Test
    public void testRegisterSources_sessionStableDisabledAndKillSwitchFlipOff_success()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(false);
        runWithMocks(
                Api.REGISTER_SOURCES,
                new AccessDenier().deniedByKillSwitch(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiRegisterSourcesKillSwitch()).thenReturn(false);
                    registerSourcesAndAssertSuccess();
                });
    }

    private void registerSourcesAndAssertSuccess() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<Integer> list = new ArrayList<>();

        mMeasurementServiceImpl.registerSource(
                createSourcesRegistrationRequest(false),
                createCallerMetadata(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {
                        list.add(STATUS_SUCCESS);
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onFailure(MeasurementErrorResponse measurementErrorResponse) {}
                });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(list.get(0)).isEqualTo(STATUS_SUCCESS);
        assertThat(list.size()).isEqualTo(1);
        assertPackageNameLogged();
    }

    @Test
    public void registerSources_consentGivenButNotificationNotShown_success() throws Exception {
        runWithMocks(
                Api.REGISTER_SOURCES,
                new AccessDenier().deniedByConsentNotificationNotDisplayed(),
                () -> {
                    CountDownLatch countDownLatch = new CountDownLatch(1);
                    final List<Integer> list = new ArrayList<>();

                    mMeasurementServiceImpl.registerSource(
                            createSourcesRegistrationRequest(false),
                            createCallerMetadata(),
                            new IMeasurementCallback.Stub() {
                                @Override
                                public void onResult() {
                                    list.add(STATUS_SUCCESS);
                                    countDownLatch.countDown();
                                }

                                @Override
                                public void onFailure(
                                        MeasurementErrorResponse measurementErrorResponse) {}
                            });

                    assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
                    assertThat(list.get(0)).isEqualTo(STATUS_SUCCESS);
                    assertThat(list.size()).isEqualTo(1);
                    assertPackageNameLogged();
                });
    }

    @Test
    public void testRegisterSources_failureByDevContextAccessResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_SOURCES,
                new AccessDenier().deniedByDevContext(),
                () ->
                        registerSourcesAndAssertFailure(
                                STATUS_UNAUTHORIZED, createSourcesRegistrationRequest(true)));
    }

    @Test
    public void testRegisterSources_failureByAppPackageMsmtApiAccessResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_SOURCES,
                new AccessDenier().deniedByAppPackageMsmtApiApp(),
                () -> registerSourcesAndAssertFailure(STATUS_CALLER_NOT_ALLOWED));
    }

    @Test
    public void testRegisterSources_failureByAttributionPermissionResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_SOURCES,
                new AccessDenier().deniedByAttributionPermission(),
                () -> registerSourcesAndAssertFailure(STATUS_PERMISSION_NOT_REQUESTED));
    }

    @Test
    public void testRegisterSources_failureByConsentResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_SOURCES,
                new AccessDenier().deniedByConsent(),
                () -> registerSourcesAndAssertFailure(STATUS_USER_CONSENT_REVOKED));
    }

    @Test
    public void testRegisterSources_failureByConsentNotifiedResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_SOURCES,
                new AccessDenier().deniedByConsentNotificationNotDisplayed().deniedByConsent(),
                () ->
                        registerSourcesAndAssertFailure(
                                STATUS_USER_CONSENT_NOTIFICATION_NOT_DISPLAYED_YET));
    }

    @Test
    public void testRegisterSources_failureByForegroundEnforcementAccessResolver()
            throws Exception {
        runWithMocks(
                Api.REGISTER_SOURCES,
                new AccessDenier().deniedByForegroundEnforcement(),
                () -> registerSourcesAndAssertFailure(STATUS_BACKGROUND_CALLER));
    }

    @Test
    public void testRegisterSources_failureByKillSwitchAccessResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_SOURCES,
                new AccessDenier().deniedByKillSwitch(),
                () -> registerSourcesAndAssertFailure(STATUS_KILLSWITCH_ENABLED));
    }

    @Test
    public void testRegisterSources_failureByThrottler() throws Exception {
        runWithMocks(
                Api.REGISTER_SOURCES,
                new AccessDenier().deniedByThrottler(),
                () -> registerSourcesAndAssertFailure(STATUS_RATE_LIMIT_REACHED));
    }

    @Test
    public void registerSources_invalidRequest_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mMeasurementServiceImpl.registerSource(
                                /* request = */ null,
                                createCallerMetadata(),
                                new IMeasurementCallback.Default()));
    }

    @Test
    public void registerSources_invalidCallerMetadata_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mMeasurementServiceImpl.registerSource(
                                createSourcesRegistrationRequest(false),
                                /* callerMetadata */ null,
                                new IMeasurementCallback.Default()));
    }

    @Test
    public void registerSources_invalidCallback_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mMeasurementServiceImpl.registerSource(
                                createSourcesRegistrationRequest(false),
                                createCallerMetadata(),
                                /* callback = */ null));
    }

    @Test
    public void testRegisterWebSource_failureByDevContextAccessResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_WEB_SOURCE,
                new AccessDenier().deniedByDevContext(),
                () ->
                        registerWebSourceAndAssertFailure(
                                STATUS_UNAUTHORIZED, createWebSourceRegistrationRequest(true)));
    }

    @Test
    public void testRegisterWebSource_failureByAppPackageMsmtApiAccessResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_WEB_SOURCE,
                new AccessDenier().deniedByAppPackageMsmtApiApp(),
                () -> registerWebSourceAndAssertFailure(STATUS_CALLER_NOT_ALLOWED));
    }

    @Test
    public void testRegisterWebSource_failureByAppPackageWebContextClientAccessResolver()
            throws Exception {
        runWithMocks(
                Api.REGISTER_WEB_SOURCE,
                new AccessDenier().deniedByAppPackageWebContextClientApp(),
                () -> registerWebSourceAndAssertFailure(STATUS_CALLER_NOT_ALLOWED));
    }

    @Test
    public void testRegisterWebSource_failureByAttributionPermissionResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_WEB_SOURCE,
                new AccessDenier().deniedByAttributionPermission(),
                () -> registerWebSourceAndAssertFailure(STATUS_PERMISSION_NOT_REQUESTED));
    }

    @Test
    public void testRegisterWebSource_failureByConsentResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_WEB_SOURCE,
                new AccessDenier().deniedByConsent(),
                () -> registerWebSourceAndAssertFailure(STATUS_USER_CONSENT_REVOKED));
    }

    @Test
    public void testRegisterWebSource_failureByConsentNotifiedResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_WEB_SOURCE,
                new AccessDenier().deniedByConsentNotificationNotDisplayed().deniedByConsent(),
                () ->
                        registerWebSourceAndAssertFailure(
                                STATUS_USER_CONSENT_NOTIFICATION_NOT_DISPLAYED_YET));
    }

    @Test
    public void testRegisterWebSource_failureByForegroundEnforcementAccessResolver()
            throws Exception {
        runWithMocks(
                Api.REGISTER_WEB_SOURCE,
                new AccessDenier().deniedByForegroundEnforcement(),
                () -> registerWebSourceAndAssertFailure(STATUS_BACKGROUND_CALLER));
    }

    @Test
    public void testRegisterWebSource_failureByKillSwitchAccessResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_WEB_SOURCE,
                new AccessDenier().deniedByKillSwitch(),
                () -> registerWebSourceAndAssertFailure(STATUS_KILLSWITCH_ENABLED));
    }

    @Test
    public void testRegisterWebSource_failureByThrottler() throws Exception {
        runWithMocks(
                Api.REGISTER_WEB_SOURCE,
                new AccessDenier().deniedByThrottler(),
                () -> registerWebSourceAndAssertFailure(STATUS_RATE_LIMIT_REACHED));
    }

    @Test
    public void registerWebSource_invalidRequest_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mMeasurementServiceImpl.registerWebSource(
                                /* request = */ null,
                                createCallerMetadata(),
                                new IMeasurementCallback.Default()));
    }

    @Test
    public void registerWebSource_invalidCallerMetadata_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mMeasurementServiceImpl.registerWebSource(
                                createWebSourceRegistrationRequest(),
                                /* callerMetadata */ null,
                                new IMeasurementCallback.Default()));
    }

    @Test
    public void registerWebSource_invalidCallback_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mMeasurementServiceImpl.registerWebSource(
                                createWebSourceRegistrationRequest(),
                                createCallerMetadata(),
                                /* callback = */ null));
    }

    @Test
    public void registerWebTrigger_success() throws Exception {
        runWithMocks(
                Api.REGISTER_WEB_TRIGGER,
                new AccessDenier(),
                this::registerWebTriggerAndAssertSuccess);
    }

    @Test
    public void testRegisterWebTrigger_sessionStableEnabledAndKillSwitchFlipOn_success()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(true);
        runWithMocks(
                Api.REGISTER_WEB_TRIGGER,
                new AccessDenier(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiRegisterWebTriggerKillSwitch())
                            .thenReturn(true);
                    registerWebTriggerAndAssertSuccess();
                });
    }

    @Test
    public void
            testRegisterWebTrigger_sessionStableDisabledAndKillSwitchFlipOn_failureByKillSwitch()
                    throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(false);
        runWithMocks(
                Api.REGISTER_WEB_TRIGGER,
                new AccessDenier(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiRegisterWebTriggerKillSwitch())
                            .thenReturn(true);
                    registerWebTriggerAndAssertFailure(STATUS_KILLSWITCH_ENABLED);
                });
    }

    @Test
    public void
            testRegisterWebTrigger_sessionStableEnabledAndKillSwitchFlipOff_failureByKillSwitch()
                    throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(true);
        runWithMocks(
                Api.REGISTER_WEB_TRIGGER,
                new AccessDenier().deniedByKillSwitch(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiRegisterWebTriggerKillSwitch())
                            .thenReturn(false);
                    registerWebTriggerAndAssertFailure(STATUS_KILLSWITCH_ENABLED);
                });
    }

    @Test
    public void testRegisterWebTrigger_sessionStableDisabledAndKillSwitchFlipOff_success()
            throws Exception {
        when(mMockFlags.getMeasurementEnableSessionStableKillSwitches()).thenReturn(false);
        runWithMocks(
                Api.REGISTER_WEB_TRIGGER,
                new AccessDenier().deniedByKillSwitch(),
                () -> {
                    // Flip kill switch.
                    when(mMockFlags.getMeasurementApiRegisterWebTriggerKillSwitch())
                            .thenReturn(false);
                    registerWebTriggerAndAssertSuccess();
                });
    }

    private void registerWebTriggerAndAssertSuccess() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<Integer> list = new ArrayList<>();

        mMeasurementServiceImpl.registerWebTrigger(
                createWebTriggerRegistrationRequest(),
                createCallerMetadata(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {
                        list.add(STATUS_SUCCESS);
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onFailure(MeasurementErrorResponse measurementErrorResponse) {}
                });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(list.get(0)).isEqualTo(STATUS_SUCCESS);
        assertThat(list.size()).isEqualTo(1);
        assertPackageNameLogged();
    }

    @Test
    public void registerWebTrigger_consentNotNotifiedButConsentGivensuccess() throws Exception {
        runWithMocks(
                Api.REGISTER_WEB_TRIGGER,
                new AccessDenier().deniedByConsentNotificationNotDisplayed(),
                () -> {
                    CountDownLatch countDownLatch = new CountDownLatch(1);
                    final List<Integer> list = new ArrayList<>();

                    mMeasurementServiceImpl.registerWebTrigger(
                            createWebTriggerRegistrationRequest(),
                            createCallerMetadata(),
                            new IMeasurementCallback.Stub() {
                                @Override
                                public void onResult() {
                                    list.add(STATUS_SUCCESS);
                                    countDownLatch.countDown();
                                }

                                @Override
                                public void onFailure(
                                        MeasurementErrorResponse measurementErrorResponse) {}
                            });

                    assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
                    assertThat(list.get(0)).isEqualTo(STATUS_SUCCESS);
                    assertThat(list.size()).isEqualTo(1);
                    assertPackageNameLogged();
                });
    }

    private void registerWebTriggerAndAssertFailure(@AdServicesStatusUtils.StatusCode int status)
            throws InterruptedException {
        registerWebTriggerAndAssertFailure(status, createWebTriggerRegistrationRequest());
    }

    private void registerWebTriggerAndAssertFailure(@AdServicesStatusUtils.StatusCode int status,
            WebTriggerRegistrationRequestInternal webTriggerRegistrationRequest)
            throws InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<MeasurementErrorResponse> errorContainer = new ArrayList<>();
        mMeasurementServiceImpl.registerWebTrigger(
                webTriggerRegistrationRequest,
                createCallerMetadata(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {}

                    @Override
                    public void onFailure(MeasurementErrorResponse responseParcel) {
                        errorContainer.add(responseParcel);
                        countDownLatch.countDown();
                    }
                });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        verify(mMockMeasurementImpl, never()).registerWebTrigger(any(), anyBoolean(), anyLong());
        Assert.assertEquals(1, errorContainer.size());
        Assert.assertEquals(status, errorContainer.get(0).getStatusCode());
    }

    @Test
    public void testRegisterWebTrigger_failureByDevContextAccessResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_WEB_TRIGGER,
                new AccessDenier().deniedByDevContext(),
                () ->
                        registerWebTriggerAndAssertFailure(
                                STATUS_UNAUTHORIZED, createWebTriggerRegistrationRequest(true)));
    }

    @Test
    public void testRegisterWebTrigger_failureByAppPackageMsmtApiAccessResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_WEB_TRIGGER,
                new AccessDenier().deniedByAppPackageMsmtApiApp(),
                () -> registerWebTriggerAndAssertFailure(STATUS_CALLER_NOT_ALLOWED));
    }

    @Test
    public void testRegisterWebTrigger_failureByAttributionPermissionResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_WEB_TRIGGER,
                new AccessDenier().deniedByAttributionPermission(),
                () -> registerWebTriggerAndAssertFailure(STATUS_PERMISSION_NOT_REQUESTED));
    }

    @Test
    public void testRegisterWebTrigger_failureByConsentResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_WEB_TRIGGER,
                new AccessDenier().deniedByConsent(),
                () -> registerWebTriggerAndAssertFailure(STATUS_USER_CONSENT_REVOKED));
    }

    @Test
    public void testRegisterWebTrigger_failureByConsentNotifiedResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_WEB_TRIGGER,
                new AccessDenier().deniedByConsentNotificationNotDisplayed().deniedByConsent(),
                () ->
                        registerWebTriggerAndAssertFailure(
                                STATUS_USER_CONSENT_NOTIFICATION_NOT_DISPLAYED_YET));
    }

    @Test
    public void testRegisterWebTrigger_failureByForegroundEnforcementAccessResolver()
            throws Exception {
        runWithMocks(
                Api.REGISTER_WEB_TRIGGER,
                new AccessDenier().deniedByForegroundEnforcement(),
                () -> registerWebTriggerAndAssertFailure(STATUS_BACKGROUND_CALLER));
    }

    @Test
    public void testRegisterWebTrigger_failureByKillSwitchAccessResolver() throws Exception {
        runWithMocks(
                Api.REGISTER_WEB_TRIGGER,
                new AccessDenier().deniedByKillSwitch(),
                () -> registerWebTriggerAndAssertFailure(STATUS_KILLSWITCH_ENABLED));
    }

    @Test
    public void testRegisterWebTrigger_failureByThrottler() throws Exception {
        runWithMocks(
                Api.REGISTER_WEB_TRIGGER,
                new AccessDenier().deniedByThrottler(),
                () -> registerWebTriggerAndAssertFailure(STATUS_RATE_LIMIT_REACHED));
    }

    @Test
    public void registerWebTrigger_invalidRequest_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mMeasurementServiceImpl.registerWebTrigger(
                                /* request = */ null,
                                createCallerMetadata(),
                                new IMeasurementCallback.Default()));
    }

    @Test
    public void registerWebTrigger_invalidCallerMetadata_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mMeasurementServiceImpl.registerWebTrigger(
                                createWebTriggerRegistrationRequest(),
                                /* callerMetadata */ null,
                                new IMeasurementCallback.Default()));
    }

    @Test
    public void registerWebTrigger_invalidCallback_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mMeasurementServiceImpl.registerWebTrigger(
                                createWebTriggerRegistrationRequest(),
                                createCallerMetadata(),
                                /* callback = */ null));
    }

    private RegistrationRequest createRegistrationSourceRequest() {
        return createRegistrationSourceRequest(false);
    }

    private RegistrationRequest createRegistrationSourceRequest(boolean isLocalhost) {
        return new RegistrationRequest.Builder(
                        RegistrationRequest.REGISTER_SOURCE,
                        isLocalhost ? LOCALHOST : REGISTRATION_URI,
                        APP_PACKAGE_NAME,
                        SDK_PACKAGE_NAME)
                .build();
    }

    private RegistrationRequest createRegistrationTriggerRequest() {
        return createRegistrationTriggerRequest(false);
    }

    private RegistrationRequest createRegistrationTriggerRequest(boolean isLocalhost) {
        return new RegistrationRequest.Builder(
                        RegistrationRequest.REGISTER_TRIGGER,
                        isLocalhost ? LOCALHOST : REGISTRATION_URI,
                        APP_PACKAGE_NAME,
                        SDK_PACKAGE_NAME)
                .build();
    }

    private void registerWebSourceAndAssertFailure(@AdServicesStatusUtils.StatusCode int status)
            throws InterruptedException {
        registerWebSourceAndAssertFailure(status, createWebSourceRegistrationRequest());
    }

    private void registerWebSourceAndAssertFailure(
            @AdServicesStatusUtils.StatusCode int status,
            WebSourceRegistrationRequestInternal webSourceRegistrationRequest)
            throws InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<MeasurementErrorResponse> errorContainer = new ArrayList<>();
        mMeasurementServiceImpl.registerWebSource(
                webSourceRegistrationRequest,
                createCallerMetadata(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {}

                    @Override
                    public void onFailure(MeasurementErrorResponse responseParcel) {
                        errorContainer.add(responseParcel);
                        countDownLatch.countDown();
                    }
                });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        verify(mMockMeasurementImpl, never()).registerWebSource(any(), anyBoolean(), anyLong());
        Assert.assertEquals(1, errorContainer.size());
        Assert.assertEquals(status, errorContainer.get(0).getStatusCode());
    }

    private void registerSourcesAndAssertFailure(@AdServicesStatusUtils.StatusCode int status)
            throws InterruptedException {
        registerSourcesAndAssertFailure(status, createSourcesRegistrationRequest(false));
    }

    private void registerSourcesAndAssertFailure(
            @AdServicesStatusUtils.StatusCode int status,
            SourceRegistrationRequestInternal sourceRegistrationRequest)
            throws InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<MeasurementErrorResponse> errorContainer = new ArrayList<>();
        mMeasurementServiceImpl.registerSource(
                sourceRegistrationRequest,
                createCallerMetadata(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {}

                    @Override
                    public void onFailure(MeasurementErrorResponse responseParcel) {
                        errorContainer.add(responseParcel);
                        countDownLatch.countDown();
                    }
                });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        verify(mMockMeasurementImpl, never()).registerSources(any(), anyLong());
        Assert.assertEquals(1, errorContainer.size());
        Assert.assertEquals(status, errorContainer.get(0).getStatusCode());
    }

    private SourceRegistrationRequestInternal createSourcesRegistrationRequest(
            boolean isLocalhost) {
        SourceRegistrationRequest sourceRegistrationRequest =
                new SourceRegistrationRequest.Builder(
                                Collections.singletonList(
                                        isLocalhost ? LOCALHOST : REGISTRATION_URI))
                        .build();
        return new SourceRegistrationRequestInternal.Builder(
                        sourceRegistrationRequest, APP_PACKAGE_NAME, SDK_PACKAGE_NAME, 10000L)
                .build();
    }

    private WebSourceRegistrationRequestInternal createWebSourceRegistrationRequest() {
        return createWebSourceRegistrationRequest(false);
    }

    private WebSourceRegistrationRequestInternal createWebSourceRegistrationRequest(
            boolean isLocalhost) {
        WebSourceRegistrationRequest sourceRegistrationRequest =
                new WebSourceRegistrationRequest.Builder(
                                Collections.singletonList(
                                        new WebSourceParams.Builder(
                                                        isLocalhost ? LOCALHOST : REGISTRATION_URI)
                                                .setDebugKeyAllowed(true)
                                                .build()),
                                Uri.parse("android-app//com.example"))
                        .setWebDestination(WEB_DESTINATION)
                        .setAppDestination(APP_DESTINATION)
                        .build();
        return new WebSourceRegistrationRequestInternal.Builder(
                        sourceRegistrationRequest, APP_PACKAGE_NAME, SDK_PACKAGE_NAME, 10000L)
                .build();
    }

    private WebTriggerRegistrationRequestInternal createWebTriggerRegistrationRequest() {
        return createWebTriggerRegistrationRequest(false);
    }

    private WebTriggerRegistrationRequestInternal createWebTriggerRegistrationRequest(
            boolean isLocalhost) {
        WebTriggerRegistrationRequest webTriggerRegistrationRequest =
                new WebTriggerRegistrationRequest.Builder(
                                Collections.singletonList(
                                        new WebTriggerParams.Builder(
                                                        isLocalhost ? LOCALHOST : REGISTRATION_URI)
                                                .setDebugKeyAllowed(true)
                                                .build()),
                                Uri.parse("android-app://com.example"))
                        .build();
        return new WebTriggerRegistrationRequestInternal.Builder(
                        webTriggerRegistrationRequest, APP_PACKAGE_NAME, SDK_PACKAGE_NAME)
                .build();
    }

    private DeletionParam createDeletionRequest() {
        return new DeletionParam.Builder(
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Instant.MIN,
                        Instant.MAX,
                        APP_PACKAGE_NAME,
                        SDK_PACKAGE_NAME)
                .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_DELETE)
                .setDeletionMode(DeletionRequest.DELETION_MODE_ALL)
                .build();
    }

    private CallerMetadata createCallerMetadata() {
        return new CallerMetadata.Builder()
                .setBinderElapsedTimestamp(SystemClock.elapsedRealtime())
                .build();
    }

    private StatusParam createStatusParam() {
        return new StatusParam.Builder(APP_PACKAGE_NAME, SDK_PACKAGE_NAME).build();
    }

    private void assertPackageNameLogged() {
        ArgumentCaptor<ApiCallStats> captorStatus = ArgumentCaptor.forClass(ApiCallStats.class);
        verify(mMockAdServicesLogger, timeout(TIMEOUT)).logApiCallStats(captorStatus.capture());
        assertEquals(APP_PACKAGE_NAME, captorStatus.getValue().getAppPackageName());
        assertEquals(SDK_PACKAGE_NAME, captorStatus.getValue().getSdkPackageName());
    }

    private void assertFailureReasonLogged(int failureReason) {
        ArgumentCaptor<ApiCallStats> captorStatus = ArgumentCaptor.forClass(ApiCallStats.class);
        verify(mMockAdServicesLogger, timeout(TIMEOUT)).logApiCallStats(captorStatus.capture());
        assertEquals(failureReason, captorStatus.getValue().getFailureReason());
    }

    private MeasurementServiceImpl createServiceWithMocks() {
        return new MeasurementServiceImpl(
                mMockMeasurementImpl,
                mMockContext,
                Clock.getInstance(),
                mMockConsentManager,
                mMockThrottler,
                new CachedFlags(mMockFlags),
                mMockAdServicesLogger,
                mMockAppImportanceFilter,
                mDevContextFilter);
    }

    private enum Api {
        DELETE_REGISTRATIONS,
        REGISTER_SOURCE,
        REGISTER_SOURCES,
        REGISTER_TRIGGER,
        REGISTER_WEB_SOURCE,
        REGISTER_WEB_TRIGGER,
        STATUS
    }

    private void runWithMocks(
            final Api api,
            final AccessDenier accessDenier,
            final TestUtils.RunnableWithThrow execute)
            throws Exception {
            // Flags
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

            // Binder
            ExtendedMockito.doReturn(1).when(Binder::getCallingUidOrThrow);
            switch (api) {
                case DELETE_REGISTRATIONS:
                    mockDeleteRegistrationsApi(accessDenier);
                    break;
                case REGISTER_SOURCE:
                    mockRegisterSourceApi(accessDenier);
                    break;
                case REGISTER_TRIGGER:
                    mockRegisterTriggerApi(accessDenier);
                    break;
                case REGISTER_WEB_SOURCE:
                    mockRegisterWebSourceApi(accessDenier);
                    break;
                case REGISTER_WEB_TRIGGER:
                    mockRegisterWebTriggerApi(accessDenier);
                    break;
                case STATUS:
                    mockStatusApi(accessDenier);
                    break;
                case REGISTER_SOURCES:
                    mockRegisterSourcesApi(accessDenier);
                    break;
                default:
                    break;
            }

            mMeasurementServiceImpl = createServiceWithMocks();
            execute.run();
    }

    /**
     * Mock related objects for Delete Registrations API only. Same mock objects could be shared
     * among other APIs, but implementation could change. Therefore, each mock{API} should describe
     * the mock objects the API uses for readability and separation of concerns.
     *
     * @param accessDenier describes if API is allowed or denied by any barrier
     */
    private void mockDeleteRegistrationsApi(AccessDenier accessDenier) {
        // Throttler
        updateThrottlerDenied(accessDenier.mByThrottler);

        // Access Resolvers
        // Kill Switch Resolver
        final boolean killSwitchEnabled = accessDenier.mByKillSwitch;
        when(mMockFlags.getMeasurementApiDeleteRegistrationsKillSwitch())
                .thenReturn(killSwitchEnabled);
        mKillSwitchSnapshot.put(
                AD_SERVICES_API_CALLED__API_NAME__DELETE_REGISTRATIONS, killSwitchEnabled);

        // Foreground Resolver
        if (accessDenier.mByForegroundEnforcement) {
            when(mMockFlags.getEnforceForegroundStatusForMeasurementDeleteRegistrations())
                    .thenReturn(true);
            doThrowExceptionCallerNotInForeground();
        } else {
            when(mMockFlags.getEnforceForegroundStatusForMeasurementDeleteRegistrations())
                    .thenReturn(false);
        }

        // App Package Resolver Measurement Api
        updateAppPackageAccessResolverDenied(accessDenier.mByAppPackageMsmtApiApp);

        // App Package Resolver Web Context Client App
        updateAppPackageResolverWebAppDenied(accessDenier.mByAppPackageWebContextClientApp);

        // Results
        when(mMockMeasurementImpl.deleteRegistrations(any(DeletionParam.class)))
                .thenReturn(STATUS_SUCCESS);
    }

    /**
     * Mock related objects for Register Source API only. Same mock objects could be shared among
     * other APIs, but implementation could change. Therefore, each mock{API} should describe the
     * mock objects the API uses for readability and separation of concerns.
     *
     * @param accessDenier describes if API is allowed or denied by any barrier
     */
    private void mockRegisterSourceApi(AccessDenier accessDenier) {
        // Throttler
        updateThrottlerDenied(accessDenier.mByThrottler);

        // Access Resolvers
        // Kill Switch Resolver
        final boolean killSwitchEnabled = accessDenier.mByKillSwitch;
        when(mMockFlags.getMeasurementApiRegisterSourceKillSwitch()).thenReturn(killSwitchEnabled);
        mKillSwitchSnapshot.put(
                AD_SERVICES_API_CALLED__API_NAME__REGISTER_SOURCE, killSwitchEnabled);

        // Foreground Resolver
        if (accessDenier.mByForegroundEnforcement) {
            when(mMockFlags.getEnforceForegroundStatusForMeasurementRegisterSource())
                    .thenReturn(true);
            doThrowExceptionCallerNotInForeground();
        } else {
            when(mMockFlags.getEnforceForegroundStatusForMeasurementRegisterSource())
                    .thenReturn(false);
        }

        // DevContext
        updateDevContextDenied(accessDenier.mByDevContext);

        // App Package Resolver Measurement Api
        updateAppPackageAccessResolverDenied(accessDenier.mByAppPackageMsmtApiApp);

        // Consent notification displayed Resolver
        updateConsentNotificationNotDisplayedDenied(accessDenier.mByConsentNotificationDisplayed);

        // Consent Resolver
        updateConsentDenied(accessDenier.mByConsent);

        // PermissionHelper
        updateAttributionPermissionDenied(accessDenier.mByAttributionPermission);

        // Results
        when(mMockMeasurementImpl.register(any(RegistrationRequest.class), anyBoolean(), anyLong()))
                .thenReturn(STATUS_SUCCESS);
    }

    /**
     * Mock related objects for Register Sources API only. Same mock objects could be shared among
     * other APIs, but implementation could change. Therefore, each mock{API} should describe the
     * mock objects the API uses for readability and separation of concerns.
     *
     * @param accessDenier describes if API is allowed or denied by any barrier
     */
    private void mockRegisterSourcesApi(AccessDenier accessDenier) {
        // Throttler
        updateThrottlerDenied(accessDenier.mByThrottler);

        // Access Resolvers
        // Kill Switch Resolver
        final boolean killSwitchEnabled = accessDenier.mByKillSwitch;
        when(mMockFlags.getMeasurementApiRegisterSourcesKillSwitch()).thenReturn(killSwitchEnabled);
        mKillSwitchSnapshot.put(
                AD_SERVICES_API_CALLED__API_NAME__REGISTER_SOURCES, killSwitchEnabled);

        // Foreground Resolver
        if (accessDenier.mByForegroundEnforcement) {
            when(mMockFlags.getEnforceForegroundStatusForMeasurementRegisterSources())
                    .thenReturn(true);
            doThrowExceptionCallerNotInForeground();
        } else {
            when(mMockFlags.getEnforceForegroundStatusForMeasurementRegisterSources())
                    .thenReturn(false);
        }

        // DevContext
        updateDevContextDenied(accessDenier.mByDevContext);

        // App Package Resolver Measurement Api
        updateAppPackageAccessResolverDenied(accessDenier.mByAppPackageMsmtApiApp);

        // Consent notification displayed Resolver
        updateConsentNotificationNotDisplayedDenied(accessDenier.mByConsentNotificationDisplayed);

        // Consent Resolver
        updateConsentDenied(accessDenier.mByConsent);

        // PermissionHelper
        updateAttributionPermissionDenied(accessDenier.mByAttributionPermission);

        // Results
        when(mMockMeasurementImpl.registerSources(
                        any(SourceRegistrationRequestInternal.class), anyLong()))
                .thenReturn(STATUS_SUCCESS);
    }

    /**
     * Mock related objects for Register Trigger API only. Same mock objects could be shared among
     * other APIs, but implementation could change. Therefore, each mock{API} should describe the
     * mock objects the API uses for readability and separation of concerns.
     *
     * @param accessDenier describes if API is allowed or denied by any barrier
     */
    private void mockRegisterTriggerApi(AccessDenier accessDenier) {
        // Throttler
        updateThrottlerDenied(accessDenier.mByThrottler);

        // Access Resolvers
        // Kill Switch Resolver
        final boolean killSwitchEnabled = accessDenier.mByKillSwitch;
        when(mMockFlags.getMeasurementApiRegisterTriggerKillSwitch()).thenReturn(killSwitchEnabled);
        mKillSwitchSnapshot.put(
                AD_SERVICES_API_CALLED__API_NAME__REGISTER_TRIGGER, killSwitchEnabled);

        // Foreground Resolver
        if (accessDenier.mByForegroundEnforcement) {
            when(mMockFlags.getEnforceForegroundStatusForMeasurementRegisterTrigger())
                    .thenReturn(true);
            doThrowExceptionCallerNotInForeground();
        } else {
            when(mMockFlags.getEnforceForegroundStatusForMeasurementRegisterTrigger())
                    .thenReturn(false);
        }

        // DevContext
        updateDevContextDenied(accessDenier.mByDevContext);

        // App Package Resolver Measurement Api
        updateAppPackageAccessResolverDenied(accessDenier.mByAppPackageMsmtApiApp);

        // Consent notification displayed Resolver
        updateConsentNotificationNotDisplayedDenied(accessDenier.mByConsentNotificationDisplayed);

        // Consent Resolver
        updateConsentDenied(accessDenier.mByConsent);

        // PermissionHelper
        updateAttributionPermissionDenied(accessDenier.mByAttributionPermission);

        // Results
        when(mMockMeasurementImpl.register(any(RegistrationRequest.class), anyBoolean(), anyLong()))
                .thenReturn(STATUS_SUCCESS);
    }

    /**
     * Mock related objects for Register Web Source API only. Same mock objects could be shared
     * among other APIs, but implementation could change. Therefore, each mock{API} should describe
     * the mock objects the API uses for readability and separation of concerns.
     *
     * @param accessDenier describes if API is allowed or denied by any barrier
     */
    private void mockRegisterWebSourceApi(AccessDenier accessDenier) {
        // Throttler
        updateThrottlerDenied(accessDenier.mByThrottler);

        // Access Resolvers
        // Kill Switch Resolver
        final boolean killSwitchEnabled = accessDenier.mByKillSwitch;
        when(mMockFlags.getMeasurementApiRegisterWebSourceKillSwitch())
                .thenReturn(killSwitchEnabled);
        mKillSwitchSnapshot.put(
                AD_SERVICES_API_CALLED__API_NAME__REGISTER_WEB_SOURCE, killSwitchEnabled);

        // Foreground Resolver
        if (accessDenier.mByForegroundEnforcement) {
            when(mMockFlags.getEnforceForegroundStatusForMeasurementRegisterWebSource())
                    .thenReturn(true);
            doThrowExceptionCallerNotInForeground();
        } else {
            when(mMockFlags.getEnforceForegroundStatusForMeasurementRegisterWebSource())
                    .thenReturn(false);
        }

        // DevContext
        updateDevContextDenied(accessDenier.mByDevContext);

        // App Package Resolver Measurement Api
        updateAppPackageAccessResolverDenied(accessDenier.mByAppPackageMsmtApiApp);

        // App Package Resolver Web Context Client App
        updateAppPackageResolverWebAppDenied(accessDenier.mByAppPackageWebContextClientApp);

        // Consent notification displayed Resolver
        updateConsentNotificationNotDisplayedDenied(accessDenier.mByConsentNotificationDisplayed);

        // Consent Resolver
        updateConsentDenied(accessDenier.mByConsent);

        // PermissionHelper
        updateAttributionPermissionDenied(accessDenier.mByAttributionPermission);

        // Results
        when(mMockMeasurementImpl.registerWebSource(
                        any(WebSourceRegistrationRequestInternal.class), anyBoolean(), anyLong()))
                .thenReturn(STATUS_SUCCESS);
    }

    /**
     * Mock related objects for Register Web Trigger API only. Same mock objects could be shared
     * among other APIs, but implementation could change. Therefore, each mock{API} should describe
     * the mock objects the API uses for readability and separation of concerns.
     *
     * @param accessDenier describes if API is allowed or denied by any barrier
     */
    private void mockRegisterWebTriggerApi(AccessDenier accessDenier) {
        // Throttler
        updateThrottlerDenied(accessDenier.mByThrottler);

        // Access Resolvers
        // Kill Switch Resolver
        final boolean killSwitchEnabled = accessDenier.mByKillSwitch;
        when(mMockFlags.getMeasurementApiRegisterWebTriggerKillSwitch())
                .thenReturn(killSwitchEnabled);
        mKillSwitchSnapshot.put(
                AD_SERVICES_API_CALLED__API_NAME__REGISTER_WEB_TRIGGER, killSwitchEnabled);

        // Foreground Resolver
        if (accessDenier.mByForegroundEnforcement) {
            when(mMockFlags.getEnforceForegroundStatusForMeasurementRegisterWebTrigger())
                    .thenReturn(true);
            doThrowExceptionCallerNotInForeground();
        } else {
            when(mMockFlags.getEnforceForegroundStatusForMeasurementRegisterWebTrigger())
                    .thenReturn(false);
        }

        // DevContext
        updateDevContextDenied(accessDenier.mByDevContext);

        // App Package Resolver Measurement Api
        updateAppPackageAccessResolverDenied(accessDenier.mByAppPackageMsmtApiApp);

        // Consent notification displayed Resolver
        updateConsentNotificationNotDisplayedDenied(accessDenier.mByConsentNotificationDisplayed);

        // Consent Resolver
        updateConsentDenied(accessDenier.mByConsent);

        // PermissionHelper
        updateAttributionPermissionDenied(accessDenier.mByAttributionPermission);

        // Results
        when(mMockMeasurementImpl.registerWebTrigger(
                        any(WebTriggerRegistrationRequestInternal.class), anyBoolean(), anyLong()))
                .thenReturn(STATUS_SUCCESS);
    }

    /**
     * Mock related objects for Status API only. Same mock objects could be shared among other APIs,
     * but implementation could change. Therefore, each mock{API} should describe the mock objects
     * the API uses for readability and separation of concerns.
     *
     * @param accessDenier describes if API is allowed or denied by any barrier
     */
    private void mockStatusApi(AccessDenier accessDenier) {
        // Access Resolvers
        // Kill Switch Resolver
        final boolean killSwitchEnabled = accessDenier.mByKillSwitch;
        when(mMockFlags.getMeasurementApiStatusKillSwitch()).thenReturn(killSwitchEnabled);
        mKillSwitchSnapshot.put(
                AD_SERVICES_API_CALLED__API_NAME__GET_MEASUREMENT_API_STATUS, killSwitchEnabled);

        // Foreground Resolver
        if (accessDenier.mByForegroundEnforcement) {
            when(mMockFlags.getEnforceForegroundStatusForMeasurementStatus()).thenReturn(true);
            doThrowExceptionCallerNotInForeground();
        } else {
            when(mMockFlags.getEnforceForegroundStatusForMeasurementStatus()).thenReturn(false);
        }

        // App Package Resolver Measurement Api
        updateAppPackageAccessResolverDeniedEnableApiStatusAllowListCheck(
                accessDenier.mByAppPackageMsmtApiApp);
        updateAppPackageAccessResolverDenied(accessDenier.mByAppPackageMsmtApiApp);

        // Consent notification displayed Resolver
        updateConsentNotificationNotDisplayedDenied(accessDenier.mByConsentNotificationDisplayed);

        // Consent Resolver
        updateConsentDenied(accessDenier.mByConsent);
    }

    private void doThrowExceptionCallerNotInForeground() {
        doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                .when(mMockAppImportanceFilter)
                .assertCallerIsInForeground(anyInt(), anyInt(), any());
    }

    private void updateDevContextDenied(boolean denied) {
        if (denied) {
            when(mDevContextFilter.createDevContextFromCallingUid(anyInt()))
                    .thenReturn(DevContext.createForDevOptionsDisabled());
        }
    }

    private void updateAppPackageAccessResolverDenied(boolean denied) {
        String allowList = denied ? AllowLists.ALLOW_NONE : AllowLists.ALLOW_ALL;
        String blockList = AllowLists.ALLOW_NONE;
        when(mMockFlags.getMsmtApiAppAllowList()).thenReturn(allowList);
        when(mMockFlags.getMsmtApiAppBlockList()).thenReturn(blockList);
    }

    private void updateAppPackageAccessResolverDeniedEnableApiStatusAllowListCheck(boolean denied) {
        String allowList = denied ? AllowLists.ALLOW_NONE : AllowLists.ALLOW_ALL;
        when(mMockFlags.getWebContextClientAppAllowList()).thenReturn(allowList);
    }

    private void updateAppPackageResolverWebAppDenied(boolean denied) {
        String allowList = denied ? AllowLists.ALLOW_NONE : AllowLists.ALLOW_ALL;
        when(mMockFlags.getWebContextClientAppAllowList()).thenReturn(allowList);
    }

    private void updateAttributionPermissionDenied(boolean denied) {
        final boolean allowed = !denied;
        ExtendedMockito.doReturn(allowed)
                .when(
                        () ->
                                PermissionHelper.hasAttributionPermission(
                                        any(Context.class), anyString()));
    }

    private void updateConsentNotificationNotDisplayedDenied(boolean denied) {
        when(mMockConsentManager.wasNotificationDisplayed()).thenReturn(!denied);
    }

    private void updateConsentDenied(boolean denied) {
        final AdServicesApiConsent apiConsent =
                denied ? AdServicesApiConsent.REVOKED : AdServicesApiConsent.GIVEN;
        when(mMockConsentManager.getConsent(AdServicesApiType.MEASUREMENTS)).thenReturn(apiConsent);
    }

    private void updateThrottlerDenied(boolean denied) {
        final boolean canAcquire = !denied;
        when(mMockThrottler.tryAcquire(any(), any())).thenReturn(canAcquire);
    }

    private static class AccessDenier {
        private boolean mByAppPackageMsmtApiApp;
        private boolean mByAppPackageWebContextClientApp;
        private boolean mByAttributionPermission;
        private boolean mByConsentNotificationDisplayed;
        private boolean mByConsent;
        private boolean mByForegroundEnforcement;
        private boolean mByKillSwitch;
        private boolean mByThrottler;
        private boolean mByDevContext;

        private AccessDenier deniedByDevContext() {
            mByDevContext = true;
            return this;
        }

        private AccessDenier deniedByAppPackageMsmtApiApp() {
            mByAppPackageMsmtApiApp = true;
            return this;
        }

        private AccessDenier deniedByAppPackageWebContextClientApp() {
            mByAppPackageWebContextClientApp = true;
            return this;
        }

        private AccessDenier deniedByAttributionPermission() {
            mByAttributionPermission = true;
            return this;
        }

        private AccessDenier deniedByConsentNotificationNotDisplayed() {
            mByConsentNotificationDisplayed = true;
            return this;
        }

        private AccessDenier deniedByConsent() {
            mByConsent = true;
            return this;
        }

        private AccessDenier deniedByForegroundEnforcement() {
            mByForegroundEnforcement = true;
            return this;
        }

        private AccessDenier deniedByKillSwitch() {
            mByKillSwitch = true;
            return this;
        }

        private AccessDenier deniedByThrottler() {
            mByThrottler = true;
            return this;
        }
    }
}
