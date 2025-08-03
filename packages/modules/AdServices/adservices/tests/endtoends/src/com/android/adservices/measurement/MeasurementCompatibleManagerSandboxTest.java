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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.adservices.adid.AdId;
import android.adservices.adid.AdIdCompatibleManager;
import android.adservices.common.AdServicesOutcomeReceiver;
import android.adservices.measurement.DeletionParam;
import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.IMeasurementService;
import android.adservices.measurement.MeasurementCompatibleManager;
import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.StatusParam;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebSourceRegistrationRequestInternal;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.adservices.measurement.WebTriggerRegistrationRequestInternal;
import android.app.sdksandbox.SandboxedSdkContext;
import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.concurrent.Executor;

@SmallTest
@RunWith(AndroidJUnit4.class)
public final class MeasurementCompatibleManagerSandboxTest {
    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    protected static final Context sSandboxedSdkContext =
            new SandboxedSdkContext(
                    /* baseContext = */ sContext,
                    /* classLoader = */ sContext.getClassLoader(),
                    /* clientPackageName = */ sContext.getPackageName(),
                    /* info = */ sContext.getApplicationInfo(),
                    /* sdkName = */ "sdkName",
                    /* sdkCeDataDir = */ null,
                    /* sdkDeDataDir = */ null,
                    /* isCustomizedSdkContext = */ false);

    private Executor mMockCallbackExecutor;
    private AdServicesOutcomeReceiver mMockOutcomeReceiver;
    private IMeasurementService mMockMeasurementService;

    private MeasurementCompatibleManager mMeasurementManager;

    // Ignore tests when device is not at least T
    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    // Skip the test if it runs on unsupported platforms.
    @Rule(order = 1)
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    @Before
    public void setUp() {
        mMockCallbackExecutor = mock(Executor.class);
        mMockOutcomeReceiver = mock(AdServicesOutcomeReceiver.class);
        mMockMeasurementService = mock(IMeasurementService.class);

        // The intention of spying on MeasurementManager and returning an IMeasurementService mock
        // is to avoid calling the process on the device. The goal of these tests are to verify the
        // same parameters are being sent as with a regular context. In these cases, the sdk package
        // name could be the only parameter that could differ, so package name would need to be
        // verified that it remains the same as the context package name on all the APIs.
        String adId = "35a4ac90-e4dc-4fe7-bbc6-95e804aa7dbc";
        AdIdCompatibleManager adIdManager = mock(AdIdCompatibleManager.class);
        mMeasurementManager = spy(MeasurementCompatibleManager.get(sContext, adIdManager));
        doReturn(mMockMeasurementService).when(mMeasurementManager).getService();
        doAnswer(
                        (invocation) -> {
                            ((AdServicesOutcomeReceiver) invocation.getArgument(1))
                                    .onResult(new AdId(adId, true));
                            return null;
                        })
                .when(adIdManager)
                .getAdId(any(), any(AdServicesOutcomeReceiver.class));

        overrideMeasurementKillSwitches(true);
    }

    @After
    public void teardown() {
        overrideMeasurementKillSwitches(false);
    }

    @Test
    public void testRegisterSource_verifySamePackageAsContext() throws Exception {
        // Execution
        mMeasurementManager.registerSource(
                Uri.parse("https://registration-source"),
                /* inputEvent = */ null,
                mMockCallbackExecutor,
                mMockOutcomeReceiver);
        // Verification
        ArgumentCaptor<RegistrationRequest> captor =
                ArgumentCaptor.forClass(RegistrationRequest.class);

        verify(mMockMeasurementService, timeout(2000)).register(captor.capture(), any(), any());
        Assert.assertNotNull(captor.getValue());
        Assert.assertEquals(sContext.getPackageName(), captor.getValue().getAppPackageName());
    }

    @Test
    public void testRegisterTrigger_verifySamePackageAsContext() throws Exception {
        // Execution
        mMeasurementManager.registerTrigger(
                Uri.parse("https://registration-trigger"),
                mMockCallbackExecutor,
                mMockOutcomeReceiver);

        // Verification
        ArgumentCaptor<RegistrationRequest> captor =
                ArgumentCaptor.forClass(RegistrationRequest.class);

        verify(mMockMeasurementService, timeout(2000)).register(captor.capture(), any(), any());
        Assert.assertNotNull(captor.getValue());
        Assert.assertEquals(sContext.getPackageName(), captor.getValue().getAppPackageName());
    }

    @Test
    public void testRegisterWebSource_verifySamePackageAsContext() throws Exception {
        // Setup
        final Uri source = Uri.parse("https://source");
        final Uri osDestination = Uri.parse("android-app://os.destination");
        final Uri webDestination = Uri.parse("https://web-destination");

        WebSourceParams webSourceParams =
                new WebSourceParams.Builder(source).setDebugKeyAllowed(false).build();

        WebSourceRegistrationRequest webSourceRegistrationRequest =
                new WebSourceRegistrationRequest.Builder(
                                Collections.singletonList(webSourceParams), source)
                        .setInputEvent(null)
                        .setAppDestination(osDestination)
                        .setWebDestination(webDestination)
                        .setVerifiedDestination(null)
                        .build();

        // Execution
        mMeasurementManager.registerWebSource(
                webSourceRegistrationRequest, mMockCallbackExecutor, mMockOutcomeReceiver);

        // Verification
        ArgumentCaptor<WebSourceRegistrationRequestInternal> captor =
                ArgumentCaptor.forClass(WebSourceRegistrationRequestInternal.class);

        verify(mMockMeasurementService, timeout(2000))
                .registerWebSource(captor.capture(), any(), any());
        Assert.assertNotNull(captor.getValue());
        Assert.assertEquals(sContext.getPackageName(), captor.getValue().getAppPackageName());
    }

    @Test
    public void testRegisterWebTrigger_verifySamePackageAsContext() throws Exception {
        // Setup
        final Uri registrationUri = Uri.parse("https://registration-uri");
        final Uri destination = Uri.parse("https://destination");

        WebTriggerParams webTriggerParams = new WebTriggerParams.Builder(registrationUri).build();
        WebTriggerRegistrationRequest webTriggerRegistrationRequest =
                new WebTriggerRegistrationRequest.Builder(
                                Collections.singletonList(webTriggerParams), destination)
                        .build();

        // Execution
        mMeasurementManager.registerWebTrigger(
                webTriggerRegistrationRequest, mMockCallbackExecutor, mMockOutcomeReceiver);

        // Verification
        ArgumentCaptor<WebTriggerRegistrationRequestInternal> captor =
                ArgumentCaptor.forClass(WebTriggerRegistrationRequestInternal.class);

        verify(mMockMeasurementService, timeout(2000))
                .registerWebTrigger(captor.capture(), any(), any());
        Assert.assertNotNull(captor.getValue());
        Assert.assertEquals(sContext.getPackageName(), captor.getValue().getAppPackageName());
    }

    @Test
    public void testDeleteRegistrations_verifySamePackageAsContext() throws Exception {
        // Setup
        DeletionRequest deletionRequest =
                new DeletionRequest.Builder()
                        .setOriginUris(Collections.singletonList(Uri.parse("https://origin")))
                        .setDomainUris(Collections.singletonList(Uri.parse("https://domain")))
                        .build();

        // Execution
        mMeasurementManager.deleteRegistrations(
                deletionRequest, mMockCallbackExecutor, mMockOutcomeReceiver);

        // Verification
        ArgumentCaptor<DeletionParam> captor = ArgumentCaptor.forClass(DeletionParam.class);

        verify(mMockMeasurementService, timeout(2000))
                .deleteRegistrations(captor.capture(), any(), any());
        Assert.assertNotNull(captor.getValue());
        Assert.assertEquals(sContext.getPackageName(), captor.getValue().getAppPackageName());
    }

    @Test
    public void testMeasurementApiStatus_verifySamePackageAsContext() throws Exception {
        // Execution
        mMeasurementManager.getMeasurementApiStatus(mMockCallbackExecutor, mMockOutcomeReceiver);

        // Verification
        ArgumentCaptor<StatusParam> captor = ArgumentCaptor.forClass(StatusParam.class);

        verify(mMockMeasurementService, timeout(2000))
                .getMeasurementApiStatus(captor.capture(), any(), any());
        Assert.assertNotNull(captor.getValue());
        Assert.assertEquals(sContext.getPackageName(), captor.getValue().getAppPackageName());
    }

    // Override measurement related kill switch to ignore the effect of actual PH values.
    // If isOverride = true, override measurement related kill switch to OFF to allow adservices
    // If isOverride = false, override measurement related kill switch to meaningless value so that
    // PhFlags will use the default value.
    private void overrideMeasurementKillSwitches(boolean isOverride) {
        String overrideString = isOverride ? "false" : "null";
        ShellUtils.runShellCommand(
                "device_config put adservices global_kill_switch " + overrideString);
        ShellUtils.runShellCommand(
                "device_config put adservices measurement_kill_switch " + overrideString);
        ShellUtils.runShellCommand(
                "device_config put adservices measurement_api_register_source_kill_switch "
                        + overrideString);
        ShellUtils.runShellCommand(
                "device_config put adservices measurement_api_register_trigger_kill_switch "
                        + overrideString);
        ShellUtils.runShellCommand(
                "device_config put adservices measurement_api_register_web_source_kill_switch "
                        + overrideString);
        ShellUtils.runShellCommand(
                "device_config put adservices measurement_api_register_web_trigger_kill_switch "
                        + overrideString);
        ShellUtils.runShellCommand(
                "device_config put adservices measurement_api_delete_registrations_kill_switch "
                        + overrideString);
        ShellUtils.runShellCommand(
                "device_config put adservices measurement_api_status_kill_switch "
                        + overrideString);
    }
}
