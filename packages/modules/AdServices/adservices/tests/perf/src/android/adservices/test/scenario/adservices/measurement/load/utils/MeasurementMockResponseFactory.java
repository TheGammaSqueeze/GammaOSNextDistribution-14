/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.adservices.test.scenario.adservices.measurement.load.utils;

import com.google.mockwebserver.MockResponse;

public class MeasurementMockResponseFactory {

    private static final String PACKAGE_NAME = "android.platform.test.scenario";
    private static final String REGISTRATION_RESPONSE_SOURCE_HEADER =
            "Attribution-Reporting-Register-Source";
    private static final String REGISTRATION_RESPONSE_TRIGGER_HEADER =
            "Attribution-Reporting-Register-Trigger";

    private static final String WEB_DESTINATION =
            replaceTestDomain("https://rb-example-destination.test");

    private static String replaceTestDomain(String value) {
        return value.replaceAll("test", "com");
    }

    /**
     * @return MockResponse for Register Source
     */
    public static MockResponse createRegisterSourceResponse() {
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

    /**
     * @return MockResponse for Register Trigger
     */
    public static MockResponse createRegisterTriggerResponse() {
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

    /**
     * @return MockResponse for Register Web Source
     */
    public static MockResponse createRegisterWebSourceResponse() {
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

    /**
     * @return MockResponse for Register Web Trigger
     */
    public static MockResponse createRegisterWebTriggerResponse() {
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

    /**
     * @return MockResponse for Event Report Upload
     */
    public static MockResponse createEventReportUploadResponse() {
        MockResponse reportResponse = new MockResponse();
        reportResponse.setResponseCode(200);
        return reportResponse;
    }

    /**
     * @return MockResponse for Aggregate Report Upload
     */
    public static MockResponse createAggregateReportUploadResponse() {
        MockResponse reportResponse = new MockResponse();
        reportResponse.setResponseCode(200);
        return reportResponse;
    }

    /**
     * @return MockResponse for Get Aggregation Key
     */
    public static MockResponse createGetAggregationKeyResponse() {
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
}
