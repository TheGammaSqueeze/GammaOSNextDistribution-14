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

package android.adservices.debuggablects;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdServicesOutcomeReceiver;
import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.MeasurementManager;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.adservices.utils.MockWebServerRule;
import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdservicesTestHelper;

import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** CTS debuggable test for Measurement API. */
@RunWith(JUnit4.class)
public class MeasurementCtsDebuggableTest {
    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static UiDevice sDevice;

    private static final String SERVER_BASE_URI = replaceTestDomain("https://localhost");
    private static final String WEB_ORIGIN = replaceTestDomain("https://rb-example-origin.test");
    private static final String WEB_DESTINATION =
            replaceTestDomain("https://rb-example-destination.test");

    private static final String PACKAGE_NAME =
            ApplicationProvider.getApplicationContext().getPackageName();

    private static final int DEFAULT_PORT = 38383;
    private static final int KEYS_PORT = 38384;

    private static final long TIMEOUT_IN_MS = 5_000;

    private static final int EVENT_REPORTING_JOB_ID = 3;
    private static final int ATTRIBUTION_REPORTING_JOB_ID = 5;
    private static final int ASYNC_REGISTRATION_QUEUE_JOB_ID = 20;
    private static final int AGGREGATE_REPORTING_JOB_ID = 7;

    private static final String AGGREGATE_ENCRYPTION_KEY_COORDINATOR_ORIGIN =
            SERVER_BASE_URI + ":" + KEYS_PORT;
    private static final String AGGREGATE_ENCRYPTION_KEY_COORDINATOR_PATH = "keys";
    private static final String REGISTRATION_RESPONSE_SOURCE_HEADER =
            "Attribution-Reporting-Register-Source";
    private static final String REGISTRATION_RESPONSE_TRIGGER_HEADER =
            "Attribution-Reporting-Register-Trigger";
    private static final String SOURCE_PATH = "/source";
    private static final String TRIGGER_PATH = "/trigger";
    private static final String AGGREGATE_ATTRIBUTION_REPORT_URI_PATH =
            "/.well-known/attribution-reporting/report-aggregate-attribution";
    private static final String EVENT_ATTRIBUTION_REPORT_URI_PATH =
            "/.well-known/attribution-reporting/report-event-attribution";

    private MeasurementManager mMeasurementManager;

    @Rule(order = 0)
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    @Rule(order = 1)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests()
                    .setCompatModeFlags()
                    .setMeasurementTags();

    @BeforeClass
    public static void setupDevicePropertiesAndInitializeClient() throws Exception {
        setFlagsForMeasurement();
    }

    @AfterClass
    public static void resetDeviceProperties() throws Exception {
        resetFlagsForMeasurement();
    }

    @Before
    public void setup() throws Exception {
        mMeasurementManager = MeasurementManager.get(sContext);
        Objects.requireNonNull(mMeasurementManager);
        executeDeleteRegistrations();
    }

    @Test
    public void registerSourceAndTriggerAndRunAttributionAndEventReporting() throws Exception {
        executeRegisterSource();
        executeRegisterTrigger();
        executeAttribution();
        executeEventReporting();
        executeDeleteRegistrations();
    }

    @Test
    public void registerSourceAndTriggerAndRunAttributionAndAggregateReporting() throws Exception {
        executeRegisterSource();
        executeRegisterTrigger();
        executeAttribution();
        executeAggregateReporting();
        executeDeleteRegistrations();
    }

    @Test
    public void registerWebSourceAndWebTriggerAndRunAttributionAndEventReporting()
            throws Exception {
        executeRegisterWebSource();
        executeRegisterWebTrigger();
        executeAttribution();
        executeEventReporting();
        executeDeleteRegistrations();
    }

    @Test
    public void registerWebSourceAndWebTriggerAndRunAttributionAndAggregateReporting()
            throws Exception {
        executeRegisterWebSource();
        executeRegisterWebTrigger();
        executeAttribution();
        executeAggregateReporting();
        executeDeleteRegistrations();
    }

    private static UiDevice getUiDevice() {
        if (sDevice == null) {
            sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        }
        return sDevice;
    }

    private static String replaceTestDomain(String value) {
        return value.replaceAll("test", "com");
    }

    private MockWebServerRule createForHttps(int port) {
        MockWebServerRule mockWebServerRule =
                MockWebServerRule.forHttps(
                        sContext, "adservices_untrusted_test_server.p12", "adservices_test");
        try {
            mockWebServerRule.reserveServerListeningPort(port);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return mockWebServerRule;
    }

    private MockWebServer startServer(int port, MockResponse... mockResponses) {
        try {
            final MockWebServerRule serverRule = createForHttps(port);
            return serverRule.startMockWebServer(List.of(mockResponses));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void shutdownServer(MockWebServer mockWebServer) {
        try {
            if (mockWebServer != null) mockWebServer.shutdown();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            sleep();
        }
    }

    private static void sleep() {
        sleep(1L);
    }

    private static void sleep(long seconds) {
        try {
            TimeUnit.MILLISECONDS.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private MockResponse createRegisterSourceResponse() {
        final MockResponse mockRegisterSourceResponse = new MockResponse();
        final String payload =
                "{"
                        + "\"destination\": \"android-app://"
                        + PACKAGE_NAME
                        + "\","
                        + "\"priority\": \"10\","
                        + "\"expiry\": \"1728000\","
                        + "\"source_event_id\": \"11111111111\","
                        + "\"aggregation_keys\": "
                        + "              {"
                        + "                \"campaignCounts\": \"0x159\","
                        + "                \"geoValue\": \"0x5\""
                        + "              }"
                        + "}";

        mockRegisterSourceResponse.setHeader(REGISTRATION_RESPONSE_SOURCE_HEADER, payload);
        mockRegisterSourceResponse.setResponseCode(200);
        return mockRegisterSourceResponse;
    }

    private MockResponse createRegisterTriggerResponse() {
        final MockResponse mockRegisterTriggerResponse = new MockResponse();
        final String payload =
                "{\"event_trigger_data\":"
                        + "[{"
                        + "  \"trigger_data\": \"1\","
                        + "  \"priority\": \"1\","
                        + "  \"deduplication_key\": \"111\""
                        + "}],"
                        + "\"aggregatable_trigger_data\": ["
                        + "              {"
                        + "                \"key_piece\": \"0x200\","
                        + "                \"source_keys\": ["
                        + "                  \"campaignCounts\","
                        + "                  \"geoValue\""
                        + "                ]"
                        + "              }"
                        + "            ],"
                        + "            \"aggregatable_values\": {"
                        + "              \"campaignCounts\": 32768,"
                        + "              \"geoValue\": 1664"
                        + "            }"
                        + "}";

        mockRegisterTriggerResponse.setHeader(REGISTRATION_RESPONSE_TRIGGER_HEADER, payload);
        mockRegisterTriggerResponse.setResponseCode(200);
        return mockRegisterTriggerResponse;
    }

    private MockResponse createRegisterWebSourceResponse() {
        final MockResponse mockRegisterWebSourceResponse = new MockResponse();
        final String payload =
                "{"
                        + "\"web_destination\": \""
                        + WEB_DESTINATION
                        + "\","
                        + "\"priority\": \"10\","
                        + "\"expiry\": \"1728000\","
                        + "\"source_event_id\": \"99999999999\","
                        + "\"aggregation_keys\": "
                        + "              {"
                        + "                \"campaignCounts\": \"0x159\","
                        + "                \"geoValue\": \"0x5\""
                        + "              }"
                        + "}";

        mockRegisterWebSourceResponse.setHeader(REGISTRATION_RESPONSE_SOURCE_HEADER, payload);
        mockRegisterWebSourceResponse.setResponseCode(200);
        return mockRegisterWebSourceResponse;
    }

    private MockResponse createRegisterWebTriggerResponse() {
        final MockResponse mockRegisterWebTriggerResponse = new MockResponse();
        final String payload =
                "{\"event_trigger_data\":"
                        + "[{"
                        + "  \"trigger_data\": \"9\","
                        + "  \"priority\": \"9\","
                        + "  \"deduplication_key\": \"999\""
                        + "}],"
                        + "\"aggregatable_trigger_data\": ["
                        + "              {"
                        + "                \"key_piece\": \"0x200\","
                        + "                \"source_keys\": ["
                        + "                  \"campaignCounts\","
                        + "                  \"geoValue\""
                        + "                ]"
                        + "              }"
                        + "            ],"
                        + "            \"aggregatable_values\": {"
                        + "              \"campaignCounts\": 32768,"
                        + "              \"geoValue\": 1664"
                        + "            }"
                        + "}]}";

        mockRegisterWebTriggerResponse.setHeader(REGISTRATION_RESPONSE_TRIGGER_HEADER, payload);
        mockRegisterWebTriggerResponse.setResponseCode(200);
        return mockRegisterWebTriggerResponse;
    }

    private MockResponse createEventReportUploadResponse() {
        MockResponse reportResponse = new MockResponse();
        reportResponse.setResponseCode(200);
        return reportResponse;
    }

    private MockResponse createAggregateReportUploadResponse() {
        MockResponse reportResponse = new MockResponse();
        reportResponse.setResponseCode(200);
        return reportResponse;
    }

    private MockResponse createGetAggregationKeyResponse() {
        MockResponse mockGetAggregationKeyResponse = new MockResponse();
        final String body =
                "{\"keys\":[{"
                        + "\"id\":\"0fa73e34-c6f3-4839-a4ed-d1681f185a76\","
                        + "\"key\":\"bcy3EsCsm/7rhO1VSl9W+h4MM0dv20xjcFbbLPE16Vg\\u003d\"}]}";

        mockGetAggregationKeyResponse.setBody(body);
        mockGetAggregationKeyResponse.setHeader("age", "14774");
        mockGetAggregationKeyResponse.setHeader("cache-control", "max-age=72795");
        mockGetAggregationKeyResponse.setResponseCode(200);

        return mockGetAggregationKeyResponse;
    }

    private void executeAsyncRegistrationJob() {
        executeJob(ASYNC_REGISTRATION_QUEUE_JOB_ID);
    }

    private void executeJob(int jobId) {
        final String packageName = AdservicesTestHelper.getAdServicesPackageName(sContext);
        final String cmd = "cmd jobscheduler run -f " + packageName + " " + jobId;
        try {
            getUiDevice().executeShellCommand(cmd);
        } catch (IOException e) {
            throw new IllegalStateException(
                    String.format("Error while executing job %d", jobId), e);
        }
    }

    private void executeAttributionJob() {
        executeJob(ATTRIBUTION_REPORTING_JOB_ID);
    }

    private void executeEventReportingJob() {
        executeJob(EVENT_REPORTING_JOB_ID);
    }

    private void executeAggregateReportingJob() {
        executeJob(AGGREGATE_REPORTING_JOB_ID);
    }

    private void executeRegisterSource() {
        final MockResponse mockResponse = createRegisterSourceResponse();
        final MockWebServer mockWebServer = startServer(DEFAULT_PORT, mockResponse);

        try {
            final String path = SERVER_BASE_URI + ":" + mockWebServer.getPort() + SOURCE_PATH;

            final CountDownLatch countDownLatch = new CountDownLatch(1);
            mMeasurementManager.registerSource(
                    Uri.parse(path),
                    /* inputEvent= */ null,
                    CALLBACK_EXECUTOR,
                    (AdServicesOutcomeReceiver<Object, Exception>)
                            result -> countDownLatch.countDown());
            assertThat(countDownLatch.await(TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)).isTrue();

            sleep();
            executeAsyncRegistrationJob();
            sleep();

            RecordedRequest recordedRequest = takeRequestTimeoutWrapper(mockWebServer);
            assertThat(recordedRequest.getPath()).isEqualTo(SOURCE_PATH);
            assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Error while registering source", e);
        } finally {
            shutdownServer(mockWebServer);
        }
    }

    private void executeRegisterTrigger() {
        final MockResponse mockResponse = createRegisterTriggerResponse();
        final MockWebServer mockWebServer = startServer(DEFAULT_PORT, mockResponse);

        try {
            final String path = SERVER_BASE_URI + ":" + mockWebServer.getPort() + TRIGGER_PATH;

            final CountDownLatch countDownLatch = new CountDownLatch(1);
            mMeasurementManager.registerTrigger(
                    Uri.parse(path),
                    CALLBACK_EXECUTOR,
                    (AdServicesOutcomeReceiver<Object, Exception>)
                            result -> countDownLatch.countDown());
            assertThat(countDownLatch.await(TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)).isTrue();

            sleep();
            executeAsyncRegistrationJob();
            sleep();

            RecordedRequest recordedRequest = takeRequestTimeoutWrapper(mockWebServer);
            assertThat(recordedRequest.getPath()).isEqualTo(TRIGGER_PATH);
            assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Error while registering trigger", e);
        } finally {
            shutdownServer(mockWebServer);
        }
    }

    private void executeRegisterWebSource() {
        final MockResponse mockResponse = createRegisterWebSourceResponse();
        final MockWebServer mockWebServer = startServer(DEFAULT_PORT, mockResponse);

        try {
            final String path = SERVER_BASE_URI + ":" + mockWebServer.getPort() + SOURCE_PATH;
            final WebSourceParams params = new WebSourceParams.Builder(Uri.parse(path)).build();
            final WebSourceRegistrationRequest request =
                    new WebSourceRegistrationRequest.Builder(
                                    Collections.singletonList(params), Uri.parse(WEB_ORIGIN))
                            .setWebDestination(Uri.parse(WEB_DESTINATION))
                            .build();

            final CountDownLatch countDownLatch = new CountDownLatch(1);
            mMeasurementManager.registerWebSource(
                    request,
                    CALLBACK_EXECUTOR,
                    (AdServicesOutcomeReceiver<Object, Exception>)
                            result -> countDownLatch.countDown());
            assertThat(countDownLatch.await(TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)).isTrue();

            sleep();
            executeAsyncRegistrationJob();
            sleep();

            RecordedRequest recordedRequest = takeRequestTimeoutWrapper(mockWebServer);
            assertThat(recordedRequest.getPath()).isEqualTo(SOURCE_PATH);
            assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Error while registering web source", e);
        } finally {
            shutdownServer(mockWebServer);
        }
    }

    private void executeRegisterWebTrigger() {
        final MockResponse mockResponse = createRegisterWebTriggerResponse();
        final MockWebServer mockWebServer = startServer(DEFAULT_PORT, mockResponse);

        try {
            final String path = SERVER_BASE_URI + ":" + mockWebServer.getPort() + TRIGGER_PATH;
            final WebTriggerParams params = new WebTriggerParams.Builder(Uri.parse(path)).build();
            final WebTriggerRegistrationRequest request =
                    new WebTriggerRegistrationRequest.Builder(
                                    Collections.singletonList(params), Uri.parse(WEB_DESTINATION))
                            .build();

            final CountDownLatch countDownLatch = new CountDownLatch(1);
            mMeasurementManager.registerWebTrigger(
                    request,
                    CALLBACK_EXECUTOR,
                    (AdServicesOutcomeReceiver<Object, Exception>)
                            result -> countDownLatch.countDown());
            assertThat(countDownLatch.await(TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)).isTrue();

            sleep();
            executeAsyncRegistrationJob();
            sleep();

            RecordedRequest recordedRequest = takeRequestTimeoutWrapper(mockWebServer);
            assertThat(recordedRequest.getPath()).isEqualTo(TRIGGER_PATH);
            assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Error while registering web trigger", e);
        } finally {
            shutdownServer(mockWebServer);
        }
    }

    private void executeAttribution() {
        final MockResponse mockResponse = createGetAggregationKeyResponse();
        final MockWebServer mockWebServer = startServer(KEYS_PORT, mockResponse);

        try {
            sleep();
            executeAttributionJob();
            sleep();
        } finally {
            shutdownServer(mockWebServer);
        }
    }

    private void executeEventReporting() {
        final MockResponse mockResponse = createEventReportUploadResponse();
        final MockWebServer mockWebServer = startServer(DEFAULT_PORT, mockResponse, mockResponse);
        try {
            sleep();
            executeEventReportingJob();
            sleep();

            RecordedRequest recordedRequest = takeRequestTimeoutWrapper(mockWebServer);
            assertThat(recordedRequest.getPath()).isEqualTo(EVENT_ATTRIBUTION_REPORT_URI_PATH);
            assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
        } finally {
            shutdownServer(mockWebServer);
        }
    }

    private void executeAggregateReporting() {
        final MockResponse aggregateReportMockResponse = createAggregateReportUploadResponse();
        final MockWebServer aggregateReportWebServer =
                startServer(DEFAULT_PORT, aggregateReportMockResponse, aggregateReportMockResponse);

        final MockResponse keysMockResponse = createGetAggregationKeyResponse();
        final MockWebServer keysReportWebServer =
                startServer(KEYS_PORT, keysMockResponse, keysMockResponse);

        try {
            sleep();
            executeAggregateReportingJob();
            sleep();

            RecordedRequest recordedRequest = takeRequestTimeoutWrapper(aggregateReportWebServer);
            assertThat(recordedRequest.getPath()).isEqualTo(AGGREGATE_ATTRIBUTION_REPORT_URI_PATH);
            assertThat(aggregateReportWebServer.getRequestCount()).isEqualTo(1);
        } finally {
            shutdownServer(aggregateReportWebServer);
            shutdownServer(keysReportWebServer);
        }
    }

    private void executeDeleteRegistrations() {
        try {
            DeletionRequest deletionRequest =
                    new DeletionRequest.Builder()
                            // Preserve none since empty origin and site lists are provided.
                            .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_PRESERVE)
                            .build();
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            mMeasurementManager.deleteRegistrations(
                    deletionRequest,
                    CALLBACK_EXECUTOR,
                    (AdServicesOutcomeReceiver<Object, Exception>)
                            result -> countDownLatch.countDown());
            assertThat(countDownLatch.await(TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)).isTrue();
        } catch (InterruptedException e) {
            throw new IllegalStateException("Error while deleting registrations", e);
        }
    }

    private RecordedRequest takeRequestTimeoutWrapper(MockWebServer mockWebServer) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<RecordedRequest> future = executor.submit(mockWebServer::takeRequest);
        try {
            return future.get(60, TimeUnit.SECONDS);
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            throw new IllegalStateException("Error while running mockWebServer.takeRequest()", e);
        } finally {
            future.cancel(true);
        }
    }

    private static void setFlagsForMeasurement() throws Exception {
        // Disable device config sync
        getUiDevice().executeShellCommand(
                "device_config set_sync_disabled_for_tests persistent");

        // Override consent notified behavior to give user consent.
        getUiDevice()
                .executeShellCommand("setprop debug.adservices.consent_notified_debug_mode true");

        // Override consent manager behavior to give user consent.
        getUiDevice().executeShellCommand(
                "setprop debug.adservices.consent_manager_debug_mode true");

        // Override the flag to allow current package to call APIs.
        getUiDevice()
                .executeShellCommand(
                        "device_config put adservices msmt_api_app_allow_list " + PACKAGE_NAME);

        // Override the flag to allow current package to call web API.
        getUiDevice().executeShellCommand(
                "device_config put adservices web_context_client_allow_list " + PACKAGE_NAME);

        // Override global kill switch.
        getUiDevice().executeShellCommand(
                "device_config put adservices global_kill_switch false");

        // Override Ad ID kill switch.
        getUiDevice().executeShellCommand("device_config put adservices adid_kill_switch false");

        // Override measurement kill switch.
        getUiDevice().executeShellCommand(
                "device_config put adservices measurement_kill_switch false");

        // Override measurement registration job kill switch.
        getUiDevice().executeShellCommand(
                "device_config put adservices measurement_job_registration_job_queue_kill_switch "
                + "false");

        // Disable foreground checks.
        getUiDevice().executeShellCommand(
                "device_config put adservices "
                + "measurement_enforce_foreground_status_register_source false");

        getUiDevice().executeShellCommand(
                "device_config put adservices "
                + "measurement_enforce_foreground_status_register_trigger false");

        // Set aggregate key URL.
        getUiDevice()
                .executeShellCommand(
                        "device_config put adservices "
                                + "measurement_aggregation_coordinator_origin_list "
                                + AGGREGATE_ENCRYPTION_KEY_COORDINATOR_ORIGIN);

        getUiDevice()
                .executeShellCommand(
                        "device_config put adservices "
                                + "measurement_default_aggregation_coordinator_origin "
                                + AGGREGATE_ENCRYPTION_KEY_COORDINATOR_ORIGIN);

        getUiDevice()
                .executeShellCommand(
                        "device_config put adservices "
                                + "measurement_aggregation_coordinator_path "
                                + AGGREGATE_ENCRYPTION_KEY_COORDINATOR_PATH);

        // Set reporting windows
        // Assume trigger registration can happen within 8 seconds of source registration.
        getUiDevice().executeShellCommand(
                "device_config put adservices "
                + "measurement_event_reports_vtc_early_reporting_windows 8,15");

        getUiDevice().executeShellCommand(
                "device_config put adservices "
                + "measurement_min_event_report_delay_millis 0");

        getUiDevice().executeShellCommand(
                "device_config put adservices "
                + "measurement_enable_configurable_aggregate_report_delay true");

        getUiDevice().executeShellCommand(
                "device_config put adservices "
                + "measurement_aggregate_report_delay_config 0,0");
        sleep();
    }

    private static void resetFlagsForMeasurement() throws Exception {
        // Reset device config sync
        getUiDevice().executeShellCommand(
                "device_config set_sync_disabled_for_tests null");

        // Reset allowed packages.
        getUiDevice()
                .executeShellCommand("device_config put adservices msmt_api_app_allow_list null");

        // Reset the flag to allow current package to call web API.
        getUiDevice().executeShellCommand(
                "device_config put adservices web_context_client_allow_list null");

        // Reset global kill switch.
        getUiDevice().executeShellCommand(
                "device_config put adservices global_kill_switch null");

        // Reset Ad ID kill switch.
        getUiDevice().executeShellCommand("device_config put adservices adid_kill_switch null");

        // Reset measurement kill switch.
        getUiDevice().executeShellCommand(
                "device_config put adservices measurement_kill_switch false");

        // Reset measurement registration job kill switch.
        getUiDevice().executeShellCommand(
                "device_config put adservices measurement_job_registration_job_queue_kill_switch "
                + "null");

        // Reset foreground checks.
        getUiDevice().executeShellCommand(
                "device_config put adservices "
                + "measurement_enforce_foreground_status_register_source null");

        getUiDevice().executeShellCommand(
                "device_config put adservices "
                + "measurement_enforce_foreground_status_register_trigger null");

        // Reset aggregate key URL.
        getUiDevice()
                .executeShellCommand(
                        "device_config put adservices "
                                + "measurement_default_aggregation_coordinator_origin null");
        getUiDevice()
                .executeShellCommand(
                        "device_config put adservices "
                                + "measurement_aggregation_coordinator_path null");

        // Reset reporting windows
        getUiDevice().executeShellCommand(
                "device_config put adservices "
                + "measurement_event_reports_vtc_early_reporting_windows null");

        getUiDevice().executeShellCommand(
                "device_config put adservices "
                + "measurement_min_event_report_delay_millis null");

        getUiDevice().executeShellCommand(
                "device_config put adservices "
                + "measurement_enable_configurable_aggregate_report_delay null");

        getUiDevice().executeShellCommand(
                "device_config put adservices "
                + "measurement_aggregate_report_delay_config null");
    }
}
