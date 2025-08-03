/*
 * Copyright (C) 2021 The Android Open Source Project
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
package android.ambientcontext.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.ambientcontext.AmbientContextEvent;
import android.app.ambientcontext.AmbientContextManager;
import android.content.Context;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.service.ambientcontext.AmbientContextDetectionResult;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.DeviceConfigStateChangerRule;
import com.android.compatibility.common.util.RequiredServiceRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;


/**
 * This suite of test ensures that AmbientContextManagerService behaves correctly when properly
 * bound to an AmbientContextDetectionService implementation.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(
        reason = "PM will not recognize CtsTestAmbientContextDetectionService in instantMode.")
public class CtsAmbientContextDetectionServiceDeviceTest {
    @Rule
    public final RequiredServiceRule mRequiredServiceRule =
            new RequiredServiceRule(Context.AMBIENT_CONTEXT_SERVICE);
    private static final String NAMESPACE_ambient_context = "ambient_context";
    private static final String KEY_SERVICE_ENABLED = "service_enabled";
    private static final String FAKE_APP_PACKAGE = "foo.bar.baz";
    public static final String CTS_PACKAGE_NAME =
            CtsAmbientContextDetectionService.class.getPackage().getName();
    private static final String CTS_SERVICE_NAME = CTS_PACKAGE_NAME + "/."
                    + CtsAmbientContextDetectionService.class.getSimpleName();
    private static final int USER_ID = UserHandle.myUserId();

    private static final AmbientContextEvent FAKE_EVENT = new AmbientContextEvent.Builder()
            .setEventType(AmbientContextEvent.EVENT_COUGH)
            .setConfidenceLevel(AmbientContextEvent.LEVEL_HIGH)
            .setDensityLevel(AmbientContextEvent.LEVEL_MEDIUM)
            .build();
    private static final int TEMPORARY_SERVICE_DURATION = 5000;

    @Rule
    public final DeviceConfigStateChangerRule mLookAllTheseRules =
            new DeviceConfigStateChangerRule(getInstrumentation().getTargetContext(),
                    NAMESPACE_ambient_context,
                    KEY_SERVICE_ENABLED,
                    "true");

    @Before
    public void setUp() {
        assumeTrue("VERSION.SDK_INT=" + VERSION.SDK_INT,
                VERSION.SDK_INT >= VERSION_CODES.TIRAMISU);
        clearTestableAmbientContextDetectionService();
        CtsAmbientContextDetectionService.reset();
        bindToTestService();
    }

    @After
    public void tearDown() {
        clearTestableAmbientContextDetectionService();
    }

    @Test
    public void testAmbientContextDetectionService_OnSuccess() {
        // From manager, call startDetection() on test service
        assertThat(CtsAmbientContextDetectionService.hasPendingRequest()).isFalse();
        callStartDetection();
        assertThat(CtsAmbientContextDetectionService.hasPendingRequest()).isTrue();

        // From test service, respond with onSuccess
        CtsAmbientContextDetectionService.respondSuccess(FAKE_EVENT);

        // From manager, verify callback was called
        assertThat(getLastStatusCode()).isEqualTo(AmbientContextManager.STATUS_SUCCESS);
    }

    @Test
    public void testAmbientContextDetectionService_OnServiceUnavailable() {
        // From manager, call startDetection() on test service
        assertThat(CtsAmbientContextDetectionService.hasPendingRequest()).isFalse();
        callStartDetection();
        assertThat(CtsAmbientContextDetectionService.hasPendingRequest()).isTrue();

        // From test service, cancel the request and respond with STATUS_SERVICE_UNAVAILABLE
        CtsAmbientContextDetectionService.respondFailure(
                AmbientContextManager.STATUS_SERVICE_UNAVAILABLE);

        // From manager, verify that the callback was called with STATUS_SERVICE_UNAVAILABLE
        assertThat(getLastStatusCode()).isEqualTo(
                AmbientContextManager.STATUS_SERVICE_UNAVAILABLE);
    }

    @Test
    public void testAmbientContextDetectionService_QueryEventStatus() {
        assertThat(CtsAmbientContextDetectionService.hasQueryRequest()).isFalse();
        callQueryServiceStatus();
        assertThat(CtsAmbientContextDetectionService.hasQueryRequest()).isTrue();

        // From test service, respond with STATUS_ACCESS_DENIED
        CtsAmbientContextDetectionService.respondFailure(
                AmbientContextManager.STATUS_ACCESS_DENIED);

        // From manager, verify callback was called
        assertThat(getLastStatusCode()).isEqualTo(AmbientContextManager.STATUS_ACCESS_DENIED);
    }

    @Test
    public void testConstructAmbientContextDetectionResult() {
        List<AmbientContextEvent> events = Arrays.asList(new AmbientContextEvent[] {FAKE_EVENT});
        AmbientContextDetectionResult result = new AmbientContextDetectionResult
                .Builder(FAKE_APP_PACKAGE)
                .addEvents(events)
                .build();
        List<AmbientContextEvent> actualEvents = result.getEvents();
        assertThat(actualEvents.size()).isEqualTo(1);
        assertThat(actualEvents).contains(FAKE_EVENT);

        result = new AmbientContextDetectionResult
                .Builder(FAKE_APP_PACKAGE)
                .addEvents(events)
                .clearEvents()
                .build();
        assertThat(result.getEvents()).isEmpty();
    }

    private int getLastStatusCode() {
        return Integer.parseInt(runShellCommand(
                "cmd ambient_context get-last-status-code"));
    }

    private void bindToTestService() {
        // On Manager, bind to test service
        assertThat(getAmbientContextDetectionServiceComponent()).isNotEqualTo(CTS_SERVICE_NAME);
        setTestableAmbientContextDetectionService(CTS_SERVICE_NAME);
        assertThat(CTS_SERVICE_NAME).contains(getAmbientContextDetectionServiceComponent());
    }

    private String getAmbientContextDetectionServiceComponent() {
        return runShellCommand("cmd ambient_context get-bound-package %d", USER_ID);
    }

    /**
     * This call is asynchronous (manager spawns + binds to service and then asynchronously makes a
     * call).
     * As such, we need to ensure consistent testing results, by waiting until we receive a response
     * in our test service w/ CountDownLatch(s).
     */
    private void callStartDetection() {
        runShellCommand("cmd ambient_context start-detection %d %s",
                USER_ID, FAKE_APP_PACKAGE);
        CtsAmbientContextDetectionService.onReceivedResponse();
    }

    /**
     * This call is asynchronous (manager spawns + binds to service and then asynchronously makes a
     * call).
     * As such, we need to ensure consistent testing results, by waiting until we receive a response
     * in our test service w/ CountDownLatch(s).
     */
    private void callQueryServiceStatus() {
        runShellCommand("cmd ambient_context query-service-status %d %s",
                USER_ID, FAKE_APP_PACKAGE);
        CtsAmbientContextDetectionService.onReceivedResponse();
    }

    private void setTestableAmbientContextDetectionService(String service) {
        runShellCommand("cmd ambient_context set-temporary-service %d %s %d",
                USER_ID, service, TEMPORARY_SERVICE_DURATION);
    }

    private void clearTestableAmbientContextDetectionService() {
        runShellCommand("cmd ambient_context set-temporary-service %d", USER_ID);
    }
}
