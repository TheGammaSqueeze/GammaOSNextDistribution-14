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

import static android.view.MotionEvent.ACTION_BUTTON_PRESS;
import static android.view.MotionEvent.obtain;

import static com.android.adservices.service.Flags.MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.android.adservices.service.Flags.MEASUREMENT_MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.android.adservices.service.measurement.reporting.AggregateReportSender.AGGREGATE_ATTRIBUTION_REPORT_URI_PATH;
import static com.android.adservices.service.measurement.reporting.AggregateReportSender.DEBUG_AGGREGATE_ATTRIBUTION_REPORT_URI_PATH;
import static com.android.adservices.service.measurement.reporting.DebugReportSender.DEBUG_REPORT_URI_PATH;
import static com.android.adservices.service.measurement.reporting.EventReportSender.DEBUG_EVENT_ATTRIBUTION_REPORT_URI_PATH;
import static com.android.adservices.service.measurement.reporting.EventReportSender.EVENT_ATTRIBUTION_REPORT_URI_PATH;

import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.provider.DeviceConfig;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;

import androidx.annotation.Nullable;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.service.measurement.actions.Action;
import com.android.adservices.service.measurement.actions.AggregateReportingJob;
import com.android.adservices.service.measurement.actions.EventReportingJob;
import com.android.adservices.service.measurement.actions.InstallApp;
import com.android.adservices.service.measurement.actions.RegisterListSources;
import com.android.adservices.service.measurement.actions.RegisterSource;
import com.android.adservices.service.measurement.actions.RegisterTrigger;
import com.android.adservices.service.measurement.actions.RegisterWebSource;
import com.android.adservices.service.measurement.actions.RegisterWebTrigger;
import com.android.adservices.service.measurement.actions.ReportObjects;
import com.android.adservices.service.measurement.actions.UninstallApp;
import com.android.adservices.service.measurement.actions.UriConfig;

import com.google.common.collect.ImmutableList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * End-to-end test from source and trigger registration to attribution reporting. Extensions of this
 * class can implement different ways to prepare the registrations, either with an external server
 * or mocking HTTP responses, for example; similarly for examining the attribution reports.
 *
 * <p>Consider @RunWith(Parameterized.class)
 */
public abstract class E2ETest extends AdServicesUnitTestCase {
    // Used to fuzzy-match expected report (not delivery) time
    private static final String LOG_TAG = "ADSERVICES_MSMT_E2E_TEST";

    private final String mName;
    private final Collection<Action> mActionsList;
    final ReportObjects mExpectedOutput;
    private final Map<String, String> mPhFlagsMap;
    // Extenders of the class populate in their own ways this container for actual output.
    final ReportObjects mActualOutput;

    enum ReportType {
        EVENT,
        AGGREGATE,
        EVENT_DEBUG,
        AGGREGATE_DEBUG,
        VERBOSE_DEBUG
    }

    private enum OutputType {
        EXPECTED,
        ACTUAL
    }

    private interface EventReportPayloadKeys {
        // Keys used to compare actual with expected output
        List<String> STRINGS =
                ImmutableList.of(
                        "scheduled_report_time",
                        "source_event_id",
                        "trigger_data",
                        "source_type",
                        "source_debug_key",
                        "trigger_debug_key",
                        "trigger_summary_bucket");
        String DOUBLE = "randomized_trigger_rate";
        String STRING_OR_ARRAY = "attribution_destination";
        String ARRAY = "trigger_debug_keys";
    }

    interface AggregateReportPayloadKeys {
        String ATTRIBUTION_DESTINATION = "attribution_destination";
        String HISTOGRAMS = "histograms";
        String SOURCE_DEBUG_KEY = "source_debug_key";
        String SOURCE_REGISTRATION_TIME = "source_registration_time";
        String TRIGGER_DEBUG_KEY = "trigger_debug_key";
    }

    interface DebugReportPayloadKeys {
        String TYPE = "type";
        String BODY = "body";
        List<String> BODY_KEYS =
                ImmutableList.of(
                        "attribution_destination",
                        "limit",
                        "randomized_trigger_rate",
                        "scheduled_report_time",
                        "source_debug_key",
                        "source_event_id",
                        "source_site",
                        "source_type",
                        "trigger_debug_key");
    }

    interface AggregateHistogramKeys {
        String BUCKET = "key";
        String VALUE = "value";
    }

    interface UnparsableRegistrationKeys {
        String TIME = "time";
        String TYPE = "type";
    }

    interface UnparsableRegistrationTypes {
        String SOURCE = "source";
        String TRIGGER = "trigger";
    }

    public interface TestFormatJsonMapping {
        String DEFAULT_CONFIG_FILENAME = "default_config.json";
        String API_CONFIG_KEY = "api_config";
        String PH_FLAGS_OVERRIDE_KEY = "phflags_override";
        String TEST_INPUT_KEY = "input";
        String TEST_OUTPUT_KEY = "output";
        String REGISTRATIONS_KEY = "registrations";
        String SOURCE_REGISTRATIONS_KEY = "sources";
        String WEB_SOURCES_KEY = "web_sources";
        String LIST_SOURCES_KEY = "list_sources";
        String SOURCE_PARAMS_REGISTRATIONS_KEY = "source_params";
        String TRIGGERS_KEY = "triggers";
        String WEB_TRIGGERS_KEY = "web_triggers";
        String TRIGGER_PARAMS_REGISTRATIONS_KEY = "trigger_params";
        String URI_TO_RESPONSE_HEADERS_KEY = "responses";
        String URI_TO_RESPONSE_HEADERS_URL_KEY = "url";
        String URI_TO_RESPONSE_HEADERS_RESPONSE_KEY = "response";
        String REGISTRATION_REQUEST_KEY = "registration_request";
        String ATTRIBUTION_SOURCE_KEY = "registrant";
        String ATTRIBUTION_SOURCE_DEFAULT = "com.interop.app";
        String CONTEXT_ORIGIN_URI_KEY = "context_origin";
        String SOURCE_TOP_ORIGIN_URI_KEY = "source_origin";
        String TRIGGER_TOP_ORIGIN_URI_KEY = "destination_origin";
        String SOURCE_APP_DESTINATION_URI_KEY = "app_destination";
        String SOURCE_WEB_DESTINATION_URI_KEY = "web_destination";
        String SOURCE_VERIFIED_DESTINATION_URI_KEY = "verified_destination";
        String REGISTRATION_URI_KEY = "attribution_src_url";
        String REGISTRATION_URIS_KEY = "attribution_src_urls";
        String HAS_AD_ID_PERMISSION = "has_ad_id_permission";
        String DEBUG_KEY = "debug_key";
        String DEBUG_PERMISSION_KEY = "debug_permission";
        String DEBUG_REPORTING_KEY = "debug_reporting";
        String INPUT_EVENT_KEY = "source_type";
        String SOURCE_VIEW_TYPE = "event";
        String TIMESTAMP_KEY = "timestamp";
        String UNPARSABLE_REGISTRATIONS_KEY = "unparsable_registrations";
        String REPORTS_OBJECTS_KEY = "reports";
        String EVENT_REPORT_OBJECTS_KEY = "event_level_results";
        String AGGREGATE_REPORT_OBJECTS_KEY = "aggregatable_results";
        String DEBUG_EVENT_REPORT_OBJECTS_KEY = "debug_event_level_results";
        String DEBUG_AGGREGATE_REPORT_OBJECTS_KEY = "debug_aggregatable_results";
        String VERBOSE_DEBUG_OBJECTS_KEY = "verbose_debug_reports";
        String INSTALLS_KEY = "installs";
        String UNINSTALLS_KEY = "uninstalls";
        String INSTALLS_URI_KEY = "uri";
        String INSTALLS_TIMESTAMP_KEY = "timestamp";
        String REPORT_TIME_KEY = "report_time";
        String REPORT_TO_KEY = "report_url";
        String PAYLOAD_KEY = "payload";
        String ENROLL = "enroll";
        String PLATFORM_AD_ID = "platform_ad_id";
        String SOURCE_REGISTRATION_TIME = "source_registration_time";
        String SCHEDULED_REPORT_TIME = "scheduled_report_time";
    }

    private interface ApiConfigKeys {
        // Privacy params
        String NAVIGATION_SOURCE_TRIGGER_DATA_CARDINALITY =
                "navigation_source_trigger_data_cardinality";
    }

    public static class ParamsProvider {
        // Privacy params
        private Integer mNavigationTriggerDataCardinality;
        public ParamsProvider(JSONObject json) throws JSONException {
            // Privacy params
            if (!json.isNull(ApiConfigKeys.NAVIGATION_SOURCE_TRIGGER_DATA_CARDINALITY)) {
                mNavigationTriggerDataCardinality = json.getInt(
                        ApiConfigKeys.NAVIGATION_SOURCE_TRIGGER_DATA_CARDINALITY);
            } else {
                mNavigationTriggerDataCardinality =
                        PrivacyParams.getNavigationTriggerDataCardinality();
            }
        }

        // Privacy params
        public Integer getNavigationTriggerDataCardinality() {
            return mNavigationTriggerDataCardinality;
        }
    }

    static Collection<Object[]> data(String testDirName, Function<String, String> preprocessor)
            throws IOException, JSONException {
        return data(testDirName, preprocessor, new HashMap<>());
    }

    static Collection<Object[]> data(String testDirName, Function<String, String> preprocessor,
            Map<String, String> apiConfigPhFlags) throws IOException, JSONException {
        AssetManager assetManager = sContext.getAssets();
        List<InputStream> inputStreams = new ArrayList<>();
        List<String> dirPathList = new ArrayList<>(Collections.singletonList(testDirName));
        List<String> testFileList = new ArrayList<>();
        while (dirPathList.size() > 0) {
            testDirName = dirPathList.remove(0);
            String[] testAssets = assetManager.list(testDirName);
            for (String testAsset : testAssets) {
                if (isDirectory(testDirName + "/" + testAsset)) {
                    dirPathList.add(testDirName + "/" + testAsset);
                } else {
                    inputStreams.add(assetManager.open(testDirName + "/" + testAsset));
                    testFileList.add(testAsset);
                }
            }
        }
        return getTestCasesFrom(
                inputStreams,
                testFileList.stream().toArray(String[]::new),
                preprocessor,
                apiConfigPhFlags);
    }

    private static boolean isDirectory(String testAssetName) throws IOException {
        String[] assetList = sContext.getAssets().list(testAssetName);
        if (assetList.length > 0) {
            return true;
        }
        return false;
    }

    public static boolean hasArDebugPermission(JSONObject obj) throws JSONException {
        JSONObject urlToResponse =
                obj.getJSONArray(TestFormatJsonMapping.URI_TO_RESPONSE_HEADERS_KEY)
                        .getJSONObject(0);
        return urlToResponse.optBoolean(TestFormatJsonMapping.DEBUG_PERMISSION_KEY, false);
    }

    public static boolean hasAdIdPermission(JSONObject obj) throws JSONException {
        JSONObject urlToResponse =
                obj.getJSONArray(TestFormatJsonMapping.URI_TO_RESPONSE_HEADERS_KEY)
                        .getJSONObject(0);
        return urlToResponse.optBoolean(TestFormatJsonMapping.HAS_AD_ID_PERMISSION, false);
    }

    public static boolean hasSourceDebugReportingPermission(JSONObject obj) throws JSONException {
        JSONObject headersMapJson =
                obj.getJSONArray(TestFormatJsonMapping.URI_TO_RESPONSE_HEADERS_KEY)
                        .getJSONObject(0)
                        .getJSONObject(TestFormatJsonMapping.URI_TO_RESPONSE_HEADERS_RESPONSE_KEY);
        JSONObject registerSource =
                headersMapJson.getJSONObject("Attribution-Reporting-Register-Source");
        return registerSource.optBoolean(TestFormatJsonMapping.DEBUG_REPORTING_KEY, false);
    }

    public static boolean hasTriggerDebugReportingPermission(JSONObject obj) throws JSONException {
        JSONObject headersMapJson =
                obj.getJSONArray(TestFormatJsonMapping.URI_TO_RESPONSE_HEADERS_KEY)
                        .getJSONObject(0)
                        .getJSONObject(TestFormatJsonMapping.URI_TO_RESPONSE_HEADERS_RESPONSE_KEY);
        JSONObject registerTrigger =
                headersMapJson.getJSONObject("Attribution-Reporting-Register-Trigger");
        return registerTrigger.optBoolean(TestFormatJsonMapping.DEBUG_REPORTING_KEY, false);
    }

    public static Map<String, List<Map<String, List<String>>>> getUriToResponseHeadersMap(
            JSONObject obj) throws JSONException {
        JSONArray uriToResArray = obj.getJSONArray(
                TestFormatJsonMapping.URI_TO_RESPONSE_HEADERS_KEY);
        Map<String, List<Map<String, List<String>>>> uriToResponseHeadersMap = new HashMap<>();

        for (int i = 0; i < uriToResArray.length(); i++) {
            JSONObject urlToResponse = uriToResArray.getJSONObject(i);
            String uri = urlToResponse.getString(
                    TestFormatJsonMapping.URI_TO_RESPONSE_HEADERS_URL_KEY);
            JSONObject headersMapJson = urlToResponse.getJSONObject(
                    TestFormatJsonMapping.URI_TO_RESPONSE_HEADERS_RESPONSE_KEY);

            Iterator<String> headers = headersMapJson.keys();
            Map<String, List<String>> headersMap = new HashMap<>();

            while (headers.hasNext()) {
                String header = headers.next();
                if (!headersMapJson.isNull(header)) {
                    String data = headersMapJson.getString(header);
                    if (header.equals("Attribution-Reporting-Redirect")) {
                        JSONArray redirects = new JSONArray(data);
                        for (int j = 0; j < redirects.length(); j++) {
                            String redirectUri = redirects.getString(j);
                            headersMap.computeIfAbsent(
                                    header, k -> new ArrayList<>()).add(redirectUri);
                        }
                    } else {
                        headersMap.put(header, Collections.singletonList(data));
                    }
                } else {
                    headersMap.put(header, null);
                }
            }

            uriToResponseHeadersMap.computeIfAbsent(uri, k -> new ArrayList<>()).add(headersMap);
        }

        return uriToResponseHeadersMap;
    }

    public static Map<String, UriConfig> getUriConfigMap(JSONObject obj) throws JSONException {
        JSONArray uriToResArray =
                obj.getJSONArray(TestFormatJsonMapping.URI_TO_RESPONSE_HEADERS_KEY);
        Map<String, UriConfig> uriConfigMap = new HashMap<>();

        for (int i = 0; i < uriToResArray.length(); i++) {
            JSONObject urlToResponse = uriToResArray.getJSONObject(i);
            String uri =
                    urlToResponse.getString(TestFormatJsonMapping.URI_TO_RESPONSE_HEADERS_URL_KEY);
            uriConfigMap.put(uri, new UriConfig(urlToResponse));
        }

        return uriConfigMap;
    }

    public static InputEvent getInputEvent() {
        return obtain(
                0 /*long downTime*/,
                0 /*long eventTime*/,
                ACTION_BUTTON_PRESS,
                1 /*int pointerCount*/,
                new PointerProperties[] { new PointerProperties() },
                new PointerCoords[] { new PointerCoords() },
                0 /*int metaState*/,
                0 /*int buttonState*/,
                1.0f /*float xPrecision*/,
                1.0f /*float yPrecision*/,
                0 /*int deviceId*/,
                0 /*int edgeFlags*/,
                InputDevice.SOURCE_TOUCH_NAVIGATION,
                0 /*int flags*/);
    }

    static String getReportUrl(ReportType reportType, String origin) {
        String reportUrl = null;
        if (reportType == ReportType.EVENT) {
            reportUrl = EVENT_ATTRIBUTION_REPORT_URI_PATH;
        } else if (reportType == ReportType.AGGREGATE) {
            reportUrl = AGGREGATE_ATTRIBUTION_REPORT_URI_PATH;
        } else if (reportType == ReportType.EVENT_DEBUG) {
            reportUrl = DEBUG_EVENT_ATTRIBUTION_REPORT_URI_PATH;
        } else if (reportType == ReportType.AGGREGATE_DEBUG) {
            reportUrl = DEBUG_AGGREGATE_ATTRIBUTION_REPORT_URI_PATH;
        } else if (reportType == ReportType.VERBOSE_DEBUG) {
            reportUrl = DEBUG_REPORT_URI_PATH;
        }
        return origin + "/" + reportUrl;
    }

    static void clearDatabase() {
        SQLiteDatabase db = DbTestUtil.getMeasurementDbHelperForTest().getWritableDatabase();
        emptyTables(db);

        DbTestUtil.getSharedDbHelperForTest()
                .getWritableDatabase()
                .delete("enrollment_data", null, null);
    }

    // The 'name' parameter is needed for the JUnit parameterized test, although it's ostensibly
    // unused by this constructor.
    E2ETest(
            Collection<Action> actions,
            ReportObjects expectedOutput,
            String name,
            Map<String, String> phFlagsMap) {
        mActionsList = actions;
        mExpectedOutput = expectedOutput;
        mActualOutput = new ReportObjects();
        mName = name;
        mPhFlagsMap = phFlagsMap;
    }

    @Test
    public void runTest() throws IOException, JSONException, DeviceConfig.BadConfigException {
        clearDatabase();
        setupDeviceConfigForPhFlags();
        for (Action action : mActionsList) {
            if (action instanceof RegisterSource) {
                processAction((RegisterSource) action);
            } else if (action instanceof RegisterTrigger) {
                processAction((RegisterTrigger) action);
            } else if (action instanceof RegisterWebSource) {
                processAction((RegisterWebSource) action);
            } else if (action instanceof RegisterWebTrigger) {
                processAction((RegisterWebTrigger) action);
            } else if (action instanceof RegisterListSources) {
                processAction((RegisterListSources) action);
            } else if (action instanceof EventReportingJob) {
                processAction((EventReportingJob) action);
            } else if (action instanceof AggregateReportingJob) {
                processAction((AggregateReportingJob) action);
            } else if (action instanceof InstallApp) {
                processAction((InstallApp) action);
            } else if (action instanceof UninstallApp) {
                processAction((UninstallApp) action);
            }
        }
        evaluateResults();
        clearDatabase();
    }

    public void log(String message) {
        Log.i(LOG_TAG, String.format("%s: %s", mName, message));
    }

    /**
     * The reporting job may be handled differently depending on whether network requests are mocked
     * or a test server is used.
     */
    abstract void processAction(EventReportingJob reportingJob) throws IOException, JSONException;

    /**
     * The reporting job may be handled differently depending on whether network requests are mocked
     * or a test server is used.
     */
    abstract void processAction(AggregateReportingJob reportingJob)
            throws IOException, JSONException;

    /**
     * Override with HTTP response mocks, for example.
     */
    abstract void prepareRegistrationServer(RegisterSource sourceRegistration)
            throws IOException;

    /** Override with HTTP response mocks, for example. */
    abstract void prepareRegistrationServer(RegisterListSources sourceRegistration)
            throws IOException;

    /**
     * Override with HTTP response mocks, for example.
     */
    abstract void prepareRegistrationServer(RegisterTrigger triggerRegistration)
            throws IOException;

    /** Override with HTTP response mocks, for example. */
    abstract void prepareRegistrationServer(RegisterWebSource sourceRegistration)
            throws IOException;

    /** Override with HTTP response mocks, for example. */
    abstract void prepareRegistrationServer(RegisterWebTrigger triggerRegistration)
            throws IOException;

    private static int hashForEventReportObject(OutputType outputType, JSONObject obj) {
        int n = EventReportPayloadKeys.STRINGS.size();
        int numValuesExcludingN = 4;
        Object[] objArray = new Object[n + numValuesExcludingN];
        // TODO (b/306863121) add time to hash
        String url = obj.optString(TestFormatJsonMapping.REPORT_TO_KEY, "");
        objArray[0] =
                outputType == OutputType.EXPECTED ? url : getReportUrl(ReportType.EVENT, url);
        JSONObject payload = obj.optJSONObject(TestFormatJsonMapping.PAYLOAD_KEY);
        objArray[1] = payload.optDouble(EventReportPayloadKeys.DOUBLE, 0);
        // Try string then JSONArray in order so as to override the string if the array parsing is
        // successful.
        objArray[2] = null;
        String maybeString = payload.optString(EventReportPayloadKeys.STRING_OR_ARRAY);
        if (maybeString != null) {
            objArray[2] = maybeString;
        }
        JSONArray maybeArray1 = payload.optJSONArray(EventReportPayloadKeys.STRING_OR_ARRAY);
        if (maybeArray1 != null) {
            objArray[2] = maybeArray1;
        }
        JSONArray maybeArray2 = payload.optJSONArray(EventReportPayloadKeys.ARRAY);
        objArray[3] = maybeArray2;
        for (int i = 0; i < n; i++) {
            objArray[i + numValuesExcludingN] =
                    payload.optString(EventReportPayloadKeys.STRINGS.get(i), "");
        }
        return Arrays.hashCode(objArray);
    }

    private static int hashForAggregateReportObject(OutputType outputType, JSONObject obj) {
        Object[] objArray = new Object[6];
        // TODO (b/306863121) add time to hash
        String url = obj.optString(TestFormatJsonMapping.REPORT_TO_KEY, "");
        objArray[0] =
                outputType == OutputType.EXPECTED ? url : getReportUrl(ReportType.AGGREGATE, url);
        JSONObject payload = obj.optJSONObject(TestFormatJsonMapping.PAYLOAD_KEY);
        objArray[1] = payload.optString(AggregateReportPayloadKeys.ATTRIBUTION_DESTINATION, "");
        // To compare histograms, we already converted them to an ordered string of value pairs.
        objArray[2] = getComparableHistograms(
                payload.optJSONArray(AggregateReportPayloadKeys.HISTOGRAMS));
        objArray[3] = payload.optString(AggregateReportPayloadKeys.SOURCE_DEBUG_KEY, "");
        objArray[4] = payload.optString(AggregateReportPayloadKeys.TRIGGER_DEBUG_KEY, "");
        objArray[5] = payload.optString(AggregateReportPayloadKeys.SOURCE_REGISTRATION_TIME, "");
        return Arrays.hashCode(objArray);
    }

    private static int hashForUnparsableRegistrationObjects(JSONObject obj) {
        Object[] objArray = new Object[2];
        objArray[0] = obj.optString(UnparsableRegistrationKeys.TIME, "");
        objArray[1] = obj.optString(UnparsableRegistrationKeys.TYPE, "");
        return Arrays.hashCode(objArray);
    }

    private static long reportTimeFrom(JSONObject obj) {
        return obj.optLong(TestFormatJsonMapping.REPORT_TIME_KEY, 0);
    }

    // 'obj1' is the expected result, 'obj2' is the actual result.
    private boolean matchReportTimeAndReportTo(ReportType reportType, JSONObject obj1,
            JSONObject obj2) throws JSONException {
        if (obj1.getLong(TestFormatJsonMapping.REPORT_TIME_KEY)
                != obj2.getLong(TestFormatJsonMapping.REPORT_TIME_KEY)) {
            log("Report-time mismatch. Report type: " + reportType.name());
            return false;
        }
        if (!obj1.getString(TestFormatJsonMapping.REPORT_TO_KEY).equals(
                getReportUrl(reportType, obj2.getString(TestFormatJsonMapping.REPORT_TO_KEY)))) {
            log("Report-to mismatch. Report type: " + reportType.name());
            return false;
        }
        return true;
    }

    private static boolean areEqualStringOrJSONArray(Object expected, Object actual)
            throws JSONException {
        if (expected instanceof String) {
            return (actual instanceof String) && (expected.equals(actual));
        } else {
            JSONArray jsonArr1 = (JSONArray) expected;
            JSONArray jsonArr2 = (JSONArray) actual;
            if (jsonArr1.length() != jsonArr2.length()) {
                return false;
            }
            for (int i = 0; i < jsonArr1.length(); i++) {
                if (!jsonArr1.getString(i).equals(jsonArr2.getString(i))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean areNullOrEqualJSONArray(JSONArray expected, JSONArray actual)
            throws JSONException {
        if (expected == null) {
            return actual == null;
        } else if (actual == null) {
            return false;
        }
        if (expected.length() != actual.length()) {
            return false;
        }
        for (int i = 0; i < expected.length(); i++) {
            if (!expected.getString(i).equals(actual.getString(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean areEqualEventReportJsons(
            ReportType reportType, JSONObject expected, JSONObject actual) throws JSONException {
        JSONObject expectedPayload = expected.getJSONObject(TestFormatJsonMapping.PAYLOAD_KEY);
        JSONObject actualPayload = actual.getJSONObject(TestFormatJsonMapping.PAYLOAD_KEY);
        if (expectedPayload.getDouble(EventReportPayloadKeys.DOUBLE)
                != actualPayload.getDouble(EventReportPayloadKeys.DOUBLE)) {
            log("Event payload double mismatch. Report type: " + reportType.name());
            return false;
        }
        if (!areEqualStringOrJSONArray(
                expectedPayload.get(EventReportPayloadKeys.STRING_OR_ARRAY),
                actualPayload.get(EventReportPayloadKeys.STRING_OR_ARRAY))) {
            log("Event payload string-or-array mismatch. Report type: " + reportType.name());
            return false;
        }
        if (!areNullOrEqualJSONArray(
                expectedPayload.optJSONArray(EventReportPayloadKeys.ARRAY),
                actualPayload.optJSONArray(EventReportPayloadKeys.ARRAY))) {
            log("Event payload array mismatch. Report type: " + reportType.name());
            return false;
        }
        for (String key : EventReportPayloadKeys.STRINGS) {
            if (!expectedPayload.optString(key, "").equals(actualPayload.optString(key, ""))) {
                log("Event payload string mismatch: " + key + ". Report type: "
                        + reportType.name());
                return false;
            }
        }
        return matchReportTimeAndReportTo(reportType, expected, actual);
    }

    private boolean areEqualAggregateReportJsons(
            ReportType reportType, JSONObject expected, JSONObject actual) throws JSONException {
        JSONObject payload1 = expected.getJSONObject(TestFormatJsonMapping.PAYLOAD_KEY);
        JSONObject payload2 = actual.getJSONObject(TestFormatJsonMapping.PAYLOAD_KEY);
        if (!payload1.optString(AggregateReportPayloadKeys.ATTRIBUTION_DESTINATION, "").equals(
                payload2.optString(AggregateReportPayloadKeys.ATTRIBUTION_DESTINATION, ""))) {
            log("Aggregate attribution destination mismatch");
            return false;
        }
        if (!payload1.optString(AggregateReportPayloadKeys.SOURCE_DEBUG_KEY, "")
                .equals(payload2.optString(AggregateReportPayloadKeys.SOURCE_DEBUG_KEY, ""))) {
            log("Source debug key mismatch");
            return false;
        }
        if (!payload1.optString(AggregateReportPayloadKeys.TRIGGER_DEBUG_KEY, "")
                .equals(payload2.optString(AggregateReportPayloadKeys.TRIGGER_DEBUG_KEY, ""))) {
            log("Trigger debug key mismatch");
            return false;
        }
        JSONArray histograms1 = payload1.optJSONArray(AggregateReportPayloadKeys.HISTOGRAMS);
        JSONArray histograms2 = payload2.optJSONArray(AggregateReportPayloadKeys.HISTOGRAMS);
        if (!getComparableHistograms(histograms1).equals(getComparableHistograms(histograms2))) {
            log("Aggregate histogram mismatch");
            return false;
        }
        if (!payload1.optString(AggregateReportPayloadKeys.SOURCE_REGISTRATION_TIME, "")
                .equals(
                        payload2.optString(
                                AggregateReportPayloadKeys.SOURCE_REGISTRATION_TIME, ""))) {
            log("Source registration time mismatch");
            return false;
        }

        return matchReportTimeAndReportTo(reportType, expected, actual);
    }

    private boolean areEqualDebugReportJsons(
            ReportType reportType, JSONObject expected, JSONObject actual) throws JSONException {
        JSONArray payloads1 = expected.getJSONArray(TestFormatJsonMapping.PAYLOAD_KEY);
        JSONArray payloads2 = actual.getJSONArray(TestFormatJsonMapping.PAYLOAD_KEY);
        if (payloads1.length() != payloads2.length()) {
            log("Debug report size mismatch");
            return false;
        }
        for (int i = 0; i < payloads1.length(); i++) {
            JSONObject payload1 = payloads1.getJSONObject(i);
            String type = payload1.optString(DebugReportPayloadKeys.TYPE, "");
            boolean hasSameType = false;
            for (int j = 0; j < payloads2.length(); j++) {
                JSONObject payload2 = payloads2.getJSONObject(j);
                if (type.equals(payload2.optString(DebugReportPayloadKeys.TYPE, ""))) {
                    hasSameType = true;
                    JSONObject body1 = payload1.getJSONObject(DebugReportPayloadKeys.BODY);
                    JSONObject body2 = payload2.getJSONObject(DebugReportPayloadKeys.BODY);
                    if (body1.length() != body2.length()) {
                        log(
                                "Verbose debug report payload body key-value pair not equal for"
                                        + " type: "
                                        + type);
                        return false;
                    }
                    for (String key : DebugReportPayloadKeys.BODY_KEYS) {
                        if (!body1.optString(key, "").equals(body2.optString(key, ""))) {
                            log(
                                    "Verbose debug report payload body mismatch for type: "
                                            + type
                                            + ", body key: "
                                            + key);
                            return false;
                        }
                    }
                    break;
                }
            }
            if (!hasSameType) {
                log("Debug report type mismatch.");
                return false;
            }
        }
        return matchReportTimeAndReportTo(reportType, expected, actual);
    }

    private boolean areEqualUnparsableRegistrationJsons(JSONObject expected, JSONObject actual) {
        if (!expected.optString(UnparsableRegistrationKeys.TIME, "").equals(
                actual.optString(UnparsableRegistrationKeys.TIME, ""))) {
            log("Unparsable registration time mismatch");
            return false;
        }
        if (!expected.optString(UnparsableRegistrationKeys.TYPE, "").equals(
                actual.optString(UnparsableRegistrationKeys.TYPE, ""))) {
            log("Unparsable registration type mismatch");
            return false;
        }
        return true;
    }

    private static String getComparableHistograms(@Nullable JSONArray arr) {
        if (arr == null) {
            return "";
        }
        try {
            List<String> tempList = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject pair = arr.getJSONObject(i);
                tempList.add(pair.getString(AggregateHistogramKeys.BUCKET) + ","
                        + pair.getString(AggregateHistogramKeys.VALUE));
            }
            Collections.sort(tempList);
            return String.join(";", tempList);
        } catch (JSONException ignored) {
            return "";
        }
    }

    private static void sortEventReportObjects(OutputType outputType,
            List<JSONObject> eventReportObjects) {
        eventReportObjects.sort(
                // Report time can vary across implementations so cannot be included in the hash;
                // they should be similarly ordered, however, so we can use them to sort.
                Comparator.comparing(E2ETest::reportTimeFrom)
                        .thenComparing(obj -> hashForEventReportObject(outputType, obj)));
    }

    private static void sortAggregateReportObjects(OutputType outputType,
            List<JSONObject> aggregateReportObjects) {
        aggregateReportObjects.sort(
                // Unlike event reports (sorted elsewhere in this file), aggregate reports are
                // scheduled with randomised times, and using report time for sorting can result
                // in unexpected variations in the sort order, depending on test timing. Without
                // time ordering, we rely on other data across the reports to yield different
                // hash codes.
                Comparator.comparing(obj -> hashForAggregateReportObject(outputType, obj)));
    }

    private static void sortUnparsableRegistrationObjects(
            List<JSONObject> unparsableRegistrationObjects) {
        unparsableRegistrationObjects.sort(
                Comparator.comparing(obj -> hashForUnparsableRegistrationObjects(obj)));
    }

    private boolean areEqual(ReportObjects expected, ReportObjects actual) throws JSONException {
        if (expected.mEventReportObjects.size() != actual.mEventReportObjects.size()
                || expected.mAggregateReportObjects.size() != actual.mAggregateReportObjects.size()
                || expected.mDebugAggregateReportObjects.size()
                        != actual.mDebugAggregateReportObjects.size()
                || expected.mDebugEventReportObjects.size()
                        != actual.mDebugEventReportObjects.size()
                || expected.mDebugReportObjects.size() != actual.mDebugReportObjects.size()
                || expected.mUnparsableRegistrationObjects.size()
                        != actual.mUnparsableRegistrationObjects.size()) {
            log("Report list size mismatch");
            return false;
        }
        for (int i = 0; i < expected.mEventReportObjects.size(); i++) {
            if (!areEqualEventReportJsons(
                    ReportType.EVENT,
                    expected.mEventReportObjects.get(i),
                    actual.mEventReportObjects.get(i))) {
                log("Event report object mismatch");
                return false;
            }
        }
        for (int i = 0; i < expected.mAggregateReportObjects.size(); i++) {
            if (!areEqualAggregateReportJsons(
                    ReportType.AGGREGATE,
                    expected.mAggregateReportObjects.get(i),
                    actual.mAggregateReportObjects.get(i))) {
                log("Aggregate report object mismatch");
                return false;
            }
        }
        for (int i = 0; i < expected.mDebugEventReportObjects.size(); i++) {
            if (!areEqualEventReportJsons(
                    ReportType.EVENT_DEBUG,
                    expected.mDebugEventReportObjects.get(i),
                    actual.mDebugEventReportObjects.get(i))) {
                log("Debug event report object mismatch");
                return false;
            }
        }
        for (int i = 0; i < expected.mDebugAggregateReportObjects.size(); i++) {
            if (!areEqualAggregateReportJsons(
                    ReportType.AGGREGATE_DEBUG,
                    expected.mDebugAggregateReportObjects.get(i),
                    actual.mDebugAggregateReportObjects.get(i))) {
                log("Debug aggregate report object mismatch");
                return false;
            }
        }
        for (int i = 0; i < expected.mDebugReportObjects.size(); i++) {
            if (!areEqualDebugReportJsons(
                    ReportType.VERBOSE_DEBUG,
                    expected.mDebugReportObjects.get(i),
                    actual.mDebugReportObjects.get(i))) {
                log("Debug report object mismatch");
                return false;
            }
        }
        for (int i = 0; i < expected.mUnparsableRegistrationObjects.size(); i++) {
            if (!areEqualUnparsableRegistrationJsons(
                    expected.mUnparsableRegistrationObjects.get(i),
                    actual.mUnparsableRegistrationObjects.get(i))) {
                log("Unparsable registration object mismatch");
                return false;
            }
        }

        return true;
    }

    private static String getTestFailureMessage(ReportObjects expectedOutput,
            ReportObjects actualOutput) {
        return String.format(
                        "Actual output does not match expected.\n\n"
                            + "(Note that displayed randomized_trigger_rate and report_url are not"
                            + " normalised.\n"
                            + "Note that report IDs are ignored in comparisons since they are not"
                            + " known in advance.)\n\n"
                            + "Event report objects:\n"
                            + "%s\n\n"
                            + "Debug Event report objects:\n"
                            + "%s\n\n"
                            + "Expected aggregate report objects: %s\n\n"
                            + "Actual aggregate report objects: %s\n\n"
                            + "Expected debug aggregate report objects: %s\n\n"
                            + "Actual debug aggregate report objects: %s\n\n"
                            + "Expected debug report objects: %s\n\n"
                            + "Actual debug report objects: %s\n\n"
                            + "Expected unparsable registration objects: %s\n\n"
                            + "Actual unparsable registration objects: %s\n",
                        prettify(
                                expectedOutput.mEventReportObjects,
                                actualOutput.mEventReportObjects),
                        prettify(
                                expectedOutput.mDebugEventReportObjects,
                                actualOutput.mDebugEventReportObjects),
                        expectedOutput.mAggregateReportObjects,
                        actualOutput.mAggregateReportObjects,
                        expectedOutput.mDebugAggregateReportObjects,
                        actualOutput.mDebugAggregateReportObjects,
                        expectedOutput.mDebugReportObjects,
                        actualOutput.mDebugReportObjects,
                        expectedOutput.mUnparsableRegistrationObjects,
                        actualOutput.mUnparsableRegistrationObjects)
                + getDatastoreState();
    }

    private static String prettify(List<JSONObject> expected, List<JSONObject> actual) {
        StringBuilder result = new StringBuilder("(Expected ::: Actual)"
                + "\n------------------------\n");
        for (int i = 0; i < Math.max(expected.size(), actual.size()); i++) {
            if (i < expected.size() && i < actual.size()) {
                result.append(prettifyObjs(expected.get(i), actual.get(i)));
            } else {
                if (i < expected.size()) {
                    result.append(prettifyObj("", expected.get(i)));
                }
                if (i < actual.size()) {
                    result.append(prettifyObj(" ::: ", actual.get(i)));
                }
            }
            result.append("\n------------------------\n");
        }
        return result.toString();
    }

    private static String prettifyObjs(JSONObject obj1, JSONObject obj2) {
        StringBuilder result = new StringBuilder();
        result.append(TestFormatJsonMapping.REPORT_TIME_KEY + ": ")
                .append(obj1.optString(TestFormatJsonMapping.REPORT_TIME_KEY))
                .append(" ::: ")
                .append(obj2.optString(TestFormatJsonMapping.REPORT_TIME_KEY))
                .append("\n");
        result.append(TestFormatJsonMapping.REPORT_TO_KEY + ": ")
                .append(obj1.optString(TestFormatJsonMapping.REPORT_TO_KEY))
                .append(" ::: ")
                .append(obj2.optString(TestFormatJsonMapping.REPORT_TO_KEY))
                .append("\n");
        JSONObject payload1 = obj1.optJSONObject(TestFormatJsonMapping.PAYLOAD_KEY);
        JSONObject payload2 = obj2.optJSONObject(TestFormatJsonMapping.PAYLOAD_KEY);
        try {
            result.append(EventReportPayloadKeys.STRING_OR_ARRAY + ": ")
                    .append(payload1.get(EventReportPayloadKeys.STRING_OR_ARRAY).toString())
                    .append(" ::: ")
                    .append(payload2.get(EventReportPayloadKeys.STRING_OR_ARRAY).toString() + "\n");
        } catch (JSONException e) {
            result.append("JSONObject::get failed for EventReportPayloadKeys.STRING_OR_ARRAY "
                    + e + "\n");
        }
        result.append(EventReportPayloadKeys.ARRAY + ": ")
                .append(payload1.optJSONArray(EventReportPayloadKeys.ARRAY))
                .append(" ::: ")
                .append(payload2.optJSONArray(EventReportPayloadKeys.ARRAY))
                .append("\n");
        for (String key : EventReportPayloadKeys.STRINGS) {
            result.append(key)
                    .append(": ")
                    .append(payload1.optString(key))
                    .append(" ::: ")
                    .append(payload2.optString(key))
                    .append("\n");
        }
        result.append(EventReportPayloadKeys.DOUBLE + ": ")
                .append(payload1.optDouble(EventReportPayloadKeys.DOUBLE))
                .append(" ::: ")
                .append(payload2.optDouble(EventReportPayloadKeys.DOUBLE));
        return result.toString();
    }

    private static String prettifyObj(String pad, JSONObject obj) {
        StringBuilder result = new StringBuilder();
        result.append(TestFormatJsonMapping.REPORT_TIME_KEY + ": ")
                .append(pad)
                .append(obj.optString(TestFormatJsonMapping.REPORT_TIME_KEY))
                .append("\n");
        JSONObject payload = obj.optJSONObject(TestFormatJsonMapping.PAYLOAD_KEY);
        try {
            result.append(EventReportPayloadKeys.STRING_OR_ARRAY + ": ")
                    .append(pad)
                    .append(payload.get(EventReportPayloadKeys.STRING_OR_ARRAY).toString() + "\n");
        } catch (JSONException e) {
            result.append("JSONObject::get failed for EventReportPayloadKeys.STRING_OR_ARRAY "
                    + e + "\n");
        }
        result.append(EventReportPayloadKeys.ARRAY + ": ")
                .append(pad)
                .append(payload.optJSONArray(EventReportPayloadKeys.ARRAY))
                .append("\n");
        for (String key : EventReportPayloadKeys.STRINGS) {
            result.append(key).append(": ").append(pad).append(payload.optString(key)).append("\n");
        }
        result.append(EventReportPayloadKeys.DOUBLE + ": ")
                .append(pad)
                .append(payload.optDouble(EventReportPayloadKeys.DOUBLE));
        return result.toString();
    }

    protected static String getDatastoreState() {
        StringBuilder result = new StringBuilder();
        SQLiteDatabase db = DbTestUtil.getMeasurementDbHelperForTest().getWritableDatabase();
        List<String> tableNames =
                ImmutableList.of(
                        "msmt_source",
                        "msmt_source_destination",
                        "msmt_trigger",
                        "msmt_attribution",
                        "msmt_event_report",
                        "msmt_aggregate_report",
                        "msmt_async_registration_contract");
        for (String tableName : tableNames) {
            result.append("\n" + tableName + ":\n");
            result.append(getTableState(db, tableName));
        }
        SQLiteDatabase enrollmentDb = DbTestUtil.getSharedDbHelperForTest().getWritableDatabase();
        List<String> enrollmentTables = ImmutableList.of("enrollment_data");
        for (String tableName : enrollmentTables) {
            result.append("\n" + tableName + ":\n");
            result.append(getTableState(enrollmentDb, tableName));
        }
        return result.toString();
    }

    private static String getTableState(SQLiteDatabase db, String tableName) {
        Cursor cursor = getAllRows(db, tableName);
        StringBuilder result = new StringBuilder();
        while (cursor.moveToNext()) {
            result.append("\n" + DatabaseUtils.dumpCurrentRowToString(cursor));
        }
        return result.toString();
    }

    private static Cursor getAllRows(SQLiteDatabase db, String tableName) {
        return db.query(
                /* boolean distinct */ false,
                tableName,
                /* String[] columns */ null,
                /* String selection */ null,
                /* String[] selectionArgs */ null,
                /* String groupBy */ null,
                /* String having */ null,
                /* String orderBy */ null,
                /* String limit */ null);
    }

    private static Set<Long> getExpiryTimesFrom(
            Collection<List<Map<String, List<String>>>> responseHeadersCollection)
            throws JSONException {
        Set<Long> expiryTimes = new HashSet<>();

        for (List<Map<String, List<String>>> responseHeaders : responseHeadersCollection) {
            for (Map<String, List<String>> headersMap : responseHeaders) {
                if (!headersMap.containsKey("Attribution-Reporting-Register-Source")) {
                    continue;
                }
                String sourceStr = headersMap.get("Attribution-Reporting-Register-Source").get(0);
                JSONObject sourceJson = new JSONObject(sourceStr);
                if (sourceJson.has("expiry")) {
                    expiryTimes.add(sourceJson.getLong("expiry"));
                } else {
                    expiryTimes.add(
                            MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
                }
                if (sourceJson.has("event_report_windows")) {
                    expiryTimes.addAll(
                            getFlexEndTimes(sourceJson.getJSONObject("event_report_windows")));
                }
            }
        }

        return expiryTimes;
    }

    private static Set<Long> getFlexEndTimes(JSONObject eventReportWindows) throws JSONException {
        Set<Long> endTimes = new HashSet<>();
        JSONArray endTimesArray = eventReportWindows.getJSONArray("end_times");
        for (int i = 0; i < endTimesArray.length(); i++) {
            endTimes.add(endTimesArray.getLong(i));
        }
        return endTimes;
    }

    private static long roundSecondsToWholeDays(long seconds) {
        long remainder = seconds % TimeUnit.DAYS.toSeconds(1);
        boolean roundUp = remainder >= TimeUnit.DAYS.toSeconds(1) / 2L;
        return seconds - remainder + (roundUp ? TimeUnit.DAYS.toSeconds(1) : 0);
    }

    private static Set<Action> maybeAddEventReportingJobTimes(boolean isEventType,
            long sourceTime, Collection<List<Map<String, List<String>>>> responseHeaders)
            throws JSONException {
        Set<Action> reportingJobsActions = new HashSet<>();
        Set<Long> expiryTimes = getExpiryTimesFrom(responseHeaders);
        for (Long expiry : expiryTimes) {
            long validExpiry = expiry;
            if (expiry > MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS) {
                validExpiry = MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
            } else if (expiry < MEASUREMENT_MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS) {
                validExpiry = MEASUREMENT_MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
            }
            if (isEventType) {
                validExpiry = roundSecondsToWholeDays(validExpiry);
            }

            long jobTime = sourceTime + 1000 * validExpiry + 3600000L;

            reportingJobsActions.add(new EventReportingJob(jobTime));
            // Add a job two days earlier for interop tests
            reportingJobsActions.add(new EventReportingJob(jobTime - TimeUnit.DAYS.toMillis(2)));
        }

        return reportingJobsActions;
    }

    static String preprocessTestJson(String json) {
        return json.replaceAll("\\.test(?=[\"\\/])", ".com");
    }

    /**
     * Builds and returns test cases from a JSON InputStream to be used by JUnit parameterized
     * tests.
     *
     * @return A collection of Object arrays, each with
     * {@code [Collection<Object> actions, ReportObjects expectedOutput,
     * ParamsProvider paramsProvider, String name]}
     */
    private static Collection<Object[]> getTestCasesFrom(
            List<InputStream> inputStreams,
            String[] filenames,
            Function<String, String> preprocessor,
            Map<String, String> apiConfigPhFlags) throws IOException, JSONException {
        List<Object[]> testCases = new ArrayList<>();

        for (int i = 0; i < inputStreams.size(); i++) {
            String name = filenames[i];
            if (name.equals(TestFormatJsonMapping.DEFAULT_CONFIG_FILENAME)) {
                continue;
            }
            int size = inputStreams.get(i).available();
            byte[] buffer = new byte[size];
            inputStreams.get(i).read(buffer);
            inputStreams.get(i).close();
            String json = new String(buffer, StandardCharsets.UTF_8);

            JSONObject testObj = new JSONObject(preprocessor.apply(json));
            JSONObject input = testObj.getJSONObject(TestFormatJsonMapping.TEST_INPUT_KEY);
            JSONObject output = testObj.getJSONObject(TestFormatJsonMapping.TEST_OUTPUT_KEY);

            // "Actions" are source or trigger registrations, or a reporting job.
            List<Action> actions = new ArrayList<>();

            actions.addAll(createSourceBasedActions(input));
            actions.addAll(createTriggerBasedActions(input));
            actions.addAll(createInstallActions(input));
            actions.addAll(createUninstallActions(input));

            actions.sort(Comparator.comparing(Action::getComparable));

            ReportObjects expectedOutput = getExpectedOutput(output);

            JSONObject apiConfigObj = testObj.isNull(TestFormatJsonMapping.API_CONFIG_KEY)
                    ? new JSONObject()
                    : testObj.getJSONObject(TestFormatJsonMapping.API_CONFIG_KEY);

            ParamsProvider paramsProvider = new ParamsProvider(apiConfigObj);

            testCases.add(
                    new Object[] {
                        actions,
                        expectedOutput,
                        paramsProvider,
                        name,
                        extractPhFlags(testObj, apiConfigObj, apiConfigPhFlags)
                    });
        }

        return testCases;
    }

    private static Map<String, String> extractPhFlags(JSONObject testObj, JSONObject apiConfigObj,
            // Interop tests may have some configurations in the "api_config" field that correspond
            // with Ph Flags.
            Map<String, String> apiConfigPhFlags) {
        Map<String, String> phFlagsMap = new HashMap<>();
        apiConfigPhFlags.keySet().forEach(
                (key) -> {
                        if (!apiConfigObj.isNull(key)) {
                            phFlagsMap.put(apiConfigPhFlags.get(key), apiConfigObj.optString(key));
                        }
                }
        );
        if (testObj.isNull(TestFormatJsonMapping.PH_FLAGS_OVERRIDE_KEY)) {
            return phFlagsMap;
        }

        JSONObject phFlagsObject =
                testObj.optJSONObject(TestFormatJsonMapping.PH_FLAGS_OVERRIDE_KEY);
        phFlagsObject.keySet().forEach((key) -> phFlagsMap.put(key, phFlagsObject.optString(key)));
        return phFlagsMap;
    }

    private static boolean isSourceRegistration(JSONObject obj) throws JSONException {
        JSONObject request = obj.getJSONObject(TestFormatJsonMapping.REGISTRATION_REQUEST_KEY);
        return !request.isNull("source_type");
    }

    private static void addSourceRegistration(JSONObject sourceObj, List<Action> actions,
            Set<Action> eventReportingJobActions) throws JSONException {
        RegisterSource sourceRegistration = new RegisterSource(sourceObj);
        actions.add(sourceRegistration);
        // Add corresponding reporting job time actions
        eventReportingJobActions.addAll(
                maybeAddEventReportingJobTimes(
                        sourceRegistration.mRegistrationRequest.getInputEvent() == null,
                        sourceRegistration.mTimestamp,
                        sourceRegistration.mUriToResponseHeadersMap.values()));
    }

    private static List<Action> createSourceBasedActions(JSONObject input) throws JSONException {
        List<Action> actions = new ArrayList<>();
        // Set avoids duplicate reporting times across sources to do attribution upon.
        Set<Action> eventReportingJobActions = new HashSet<>();

        // Interop tests have all registration types in one list
        if (!input.isNull(TestFormatJsonMapping.REGISTRATIONS_KEY)) {
            JSONArray registrationArray = input.getJSONArray(
                    TestFormatJsonMapping.REGISTRATIONS_KEY);
            for (int i = 0; i < registrationArray.length(); i++) {
                if (registrationArray.isNull(i)) {
                    continue;
                }
                JSONObject obj = registrationArray.getJSONObject(i);
                if (isSourceRegistration(obj)) {
                    addSourceRegistration(obj, actions, eventReportingJobActions);
                }
            }
        }

        if (!input.isNull(TestFormatJsonMapping.SOURCE_REGISTRATIONS_KEY)) {
            JSONArray sourceRegistrationArray = input.getJSONArray(
                    TestFormatJsonMapping.SOURCE_REGISTRATIONS_KEY);
            for (int j = 0; j < sourceRegistrationArray.length(); j++) {
                if (sourceRegistrationArray.isNull(j)) {
                    continue;
                }
                addSourceRegistration(sourceRegistrationArray.getJSONObject(j),
                        actions, eventReportingJobActions);
            }
        }

        if (!input.isNull(TestFormatJsonMapping.WEB_SOURCES_KEY)) {
            JSONArray webSourceRegistrationArray =
                    input.getJSONArray(TestFormatJsonMapping.WEB_SOURCES_KEY);
            for (int j = 0; j < webSourceRegistrationArray.length(); j++) {
                RegisterWebSource webSource =
                        new RegisterWebSource(webSourceRegistrationArray.getJSONObject(j));
                actions.add(webSource);
                // Add corresponding reporting job time actions
                eventReportingJobActions.addAll(
                        maybeAddEventReportingJobTimes(
                                webSource.mRegistrationRequest.getSourceRegistrationRequest()
                                        .getInputEvent() == null,
                                webSource.mTimestamp,
                                webSource.mUriToResponseHeadersMap.values()));
            }
        }

        if (!input.isNull(TestFormatJsonMapping.LIST_SOURCES_KEY)) {
            JSONArray listSourceRegistrationArray =
                    input.getJSONArray(TestFormatJsonMapping.LIST_SOURCES_KEY);
            for (int j = 0; j < listSourceRegistrationArray.length(); j++) {
                RegisterListSources listSources =
                        new RegisterListSources(listSourceRegistrationArray.getJSONObject(j));
                actions.add(listSources);
                // Add corresponding reporting job time actions
                eventReportingJobActions.addAll(
                        maybeAddEventReportingJobTimes(
                                listSources
                                                .mRegistrationRequest
                                                .getSourceRegistrationRequest()
                                                .getInputEvent()
                                        == null,
                                listSources.mTimestamp,
                                listSources.mUriToResponseHeadersMap.values()));
            }
        }

        actions.addAll(eventReportingJobActions);
        return actions;
    }

    private static List<Action> createTriggerBasedActions(JSONObject input) throws JSONException {
        List<Action> actions = new ArrayList<>();
        List<Action> aggregateReportingJobActions = new ArrayList<>();

        long aggregateReportMaxDelay = PrivacyParams.AGGREGATE_REPORT_MIN_DELAY
                + PrivacyParams.AGGREGATE_REPORT_DELAY_SPAN;

        // Interop tests have all registration types in one list
        if (!input.isNull(TestFormatJsonMapping.REGISTRATIONS_KEY)) {
            JSONArray registrationArray = input.getJSONArray(
                    TestFormatJsonMapping.REGISTRATIONS_KEY);
            for (int i = 0; i < registrationArray.length(); i++) {
                if (registrationArray.isNull(i)) {
                    continue;
                }
                JSONObject obj = registrationArray.getJSONObject(i);
                if (!isSourceRegistration(obj)) {
                    RegisterTrigger triggerRegistration = new RegisterTrigger(obj);
                    actions.add(triggerRegistration);
                    aggregateReportingJobActions.add(new AggregateReportingJob(
                            triggerRegistration.mTimestamp + aggregateReportMaxDelay));
                }
            }
        }

        if (!input.isNull(TestFormatJsonMapping.TRIGGERS_KEY)) {
            JSONArray triggerRegistrationArray =
                    input.getJSONArray(TestFormatJsonMapping.TRIGGERS_KEY);
            for (int j = 0; j < triggerRegistrationArray.length(); j++) {
                RegisterTrigger triggerRegistration =
                        new RegisterTrigger(triggerRegistrationArray.getJSONObject(j));
                actions.add(triggerRegistration);
                aggregateReportingJobActions.add(new AggregateReportingJob(
                        triggerRegistration.mTimestamp + aggregateReportMaxDelay));
            }
        }

        if (!input.isNull(TestFormatJsonMapping.WEB_TRIGGERS_KEY)) {
            JSONArray webTriggerRegistrationArray =
                    input.getJSONArray(TestFormatJsonMapping.WEB_TRIGGERS_KEY);
            for (int j = 0; j < webTriggerRegistrationArray.length(); j++) {
                RegisterWebTrigger webTrigger =
                        new RegisterWebTrigger(webTriggerRegistrationArray.getJSONObject(j));
                actions.add(webTrigger);
                aggregateReportingJobActions.add(new AggregateReportingJob(
                        webTrigger.mTimestamp + aggregateReportMaxDelay));
            }
        }

        actions.addAll(aggregateReportingJobActions);
        return actions;
    }

    private static List<Action> createInstallActions(JSONObject input) throws JSONException {
        List<Action> actions = new ArrayList<>();
        if (!input.isNull(TestFormatJsonMapping.INSTALLS_KEY)) {
            JSONArray installsArray = input.getJSONArray(TestFormatJsonMapping.INSTALLS_KEY);
            for (int j = 0; j < installsArray.length(); j++) {
                InstallApp installApp = new InstallApp(installsArray.getJSONObject(j));
                actions.add(installApp);
            }
        }

        return actions;
    }

    private static List<Action> createUninstallActions(JSONObject input) throws JSONException {
        List<Action> actions = new ArrayList<>();
        if (!input.isNull(TestFormatJsonMapping.UNINSTALLS_KEY)) {
            JSONArray uninstallsArray = input.getJSONArray(TestFormatJsonMapping.UNINSTALLS_KEY);
            for (int j = 0; j < uninstallsArray.length(); j++) {
                UninstallApp uninstallApp = new UninstallApp(uninstallsArray.getJSONObject(j));
                actions.add(uninstallApp);
            }
        }

        return actions;
    }

    private static ReportObjects getExpectedOutput(JSONObject output) throws JSONException {
        List<JSONObject> eventReportObjects = new ArrayList<>();
        List<JSONObject> aggregateReportObjects = new ArrayList<>();
        List<JSONObject> debugEventReportObjects = new ArrayList<>();
        List<JSONObject> debugAggregateReportObjects = new ArrayList<>();
        List<JSONObject> debugReportObjects = new ArrayList<>();
        List<JSONObject> unparsableRegistrationObjects = new ArrayList<>();

        // Interop tests have all report types in one list
        if (!output.isNull(TestFormatJsonMapping.REPORTS_OBJECTS_KEY)) {
            // We compare the suffixes of the different reporting URLs with the report URL to
            // determine the report type. This assumes the longest suffix match corresponds with the
            // report type.
            String[] eventUrlTokens = EVENT_ATTRIBUTION_REPORT_URI_PATH.split("/");
            String[] debugEventUrlTokens = DEBUG_EVENT_ATTRIBUTION_REPORT_URI_PATH.split("/");
            String[] aggregateUrlTokens = AGGREGATE_ATTRIBUTION_REPORT_URI_PATH.split("/");
            String[] debugAggregateUrlTokens =
                DEBUG_AGGREGATE_ATTRIBUTION_REPORT_URI_PATH.split("/");
            String[] debugUrlTokens = DEBUG_REPORT_URI_PATH.split("/");

            JSONArray reportsObjectsArray = output.getJSONArray(
                    TestFormatJsonMapping.REPORTS_OBJECTS_KEY);

            for (int i = 0; i < reportsObjectsArray.length(); i++) {
                JSONObject obj = reportsObjectsArray.getJSONObject(i);
                String[] urlTokens = obj.getString(TestFormatJsonMapping.REPORT_TO_KEY).split("/");
                // Collect reports to different lists (event, aggregate, debug, etc.) based on the
                // reporting URL.
                if (urlTokens[urlTokens.length - 1].equals(
                        debugUrlTokens[debugUrlTokens.length - 1])) {
                    debugReportObjects.add(obj);
                } else if (urlTokens[urlTokens.length - 1].equals(
                        eventUrlTokens[eventUrlTokens.length - 1])) {
                    if (urlTokens[urlTokens.length - 2].equals(
                            debugEventUrlTokens[debugEventUrlTokens.length - 2])) {
                        debugEventReportObjects.add(obj);
                    } else {
                        eventReportObjects.add(obj);
                    }
                } else if (urlTokens[urlTokens.length - 1].equals(
                        aggregateUrlTokens[aggregateUrlTokens.length - 1])) {
                    if (urlTokens[urlTokens.length - 2].equals(
                            debugAggregateUrlTokens[debugAggregateUrlTokens.length - 2])) {
                        debugAggregateReportObjects.add(obj);
                    } else {
                        aggregateReportObjects.add(obj);
                    }
                }
            }

            JSONArray unparsableRegistrationsArray = output.getJSONArray(
                    TestFormatJsonMapping.UNPARSABLE_REGISTRATIONS_KEY);

            for (int i = 0; i < unparsableRegistrationsArray.length(); i++) {
                unparsableRegistrationObjects.add(
                        unparsableRegistrationsArray.getJSONObject(i));
            }
        }

        if (!output.isNull(TestFormatJsonMapping.EVENT_REPORT_OBJECTS_KEY)) {
            JSONArray eventReportObjectsArray = output.getJSONArray(
                    TestFormatJsonMapping.EVENT_REPORT_OBJECTS_KEY);
            for (int i = 0; i < eventReportObjectsArray.length(); i++) {
                eventReportObjects.add(eventReportObjectsArray.getJSONObject(i));
            }
        }

        if (!output.isNull(TestFormatJsonMapping.AGGREGATE_REPORT_OBJECTS_KEY)) {
            JSONArray aggregateReportObjectsArray =
                    output.getJSONArray(TestFormatJsonMapping.AGGREGATE_REPORT_OBJECTS_KEY);
            for (int i = 0; i < aggregateReportObjectsArray.length(); i++) {
                aggregateReportObjects.add(aggregateReportObjectsArray.getJSONObject(i));
            }
        }

        if (!output.isNull(TestFormatJsonMapping.DEBUG_EVENT_REPORT_OBJECTS_KEY)) {
            JSONArray debugEventReportObjectsArray =
                    output.getJSONArray(TestFormatJsonMapping.DEBUG_EVENT_REPORT_OBJECTS_KEY);
            for (int i = 0; i < debugEventReportObjectsArray.length(); i++) {
                debugEventReportObjects.add(debugEventReportObjectsArray.getJSONObject(i));
            }
        }

        if (!output.isNull(TestFormatJsonMapping.DEBUG_AGGREGATE_REPORT_OBJECTS_KEY)) {
            JSONArray debugAggregateReportObjectsArray =
                    output.getJSONArray(TestFormatJsonMapping.DEBUG_AGGREGATE_REPORT_OBJECTS_KEY);
            for (int i = 0; i < debugAggregateReportObjectsArray.length(); i++) {
                debugAggregateReportObjects.add(debugAggregateReportObjectsArray.getJSONObject(i));
            }
        }

        if (!output.isNull(TestFormatJsonMapping.VERBOSE_DEBUG_OBJECTS_KEY)) {
            JSONArray debugReportObjectsArray =
                    output.getJSONArray(TestFormatJsonMapping.VERBOSE_DEBUG_OBJECTS_KEY);
            for (int i = 0; i < debugReportObjectsArray.length(); i++) {
                debugReportObjects.add(debugReportObjectsArray.getJSONObject(i));
            }
        }

        return new ReportObjects(
                eventReportObjects,
                aggregateReportObjects,
                debugEventReportObjects,
                debugAggregateReportObjects,
                debugReportObjects,
                unparsableRegistrationObjects);
    }

    /**
     * Empties measurement database tables, used for test cleanup.
     */
    private static void emptyTables(SQLiteDatabase db) {
        db.delete("msmt_source", null, null);
        db.delete("msmt_trigger", null, null);
        db.delete("msmt_event_report", null, null);
        db.delete("msmt_attribution", null, null);
        db.delete("msmt_aggregate_report", null, null);
        db.delete("msmt_async_registration_contract", null, null);
    }

    abstract void processAction(RegisterSource sourceRegistration)
            throws IOException, JSONException;

    abstract void processAction(RegisterWebSource sourceRegistration)
            throws IOException, JSONException;

    abstract void processAction(RegisterListSources sourceRegistration)
            throws IOException, JSONException;

    abstract void processAction(RegisterTrigger triggerRegistration)
            throws IOException, JSONException;

    abstract void processAction(RegisterWebTrigger triggerRegistration)
            throws IOException, JSONException;

    abstract void processAction(InstallApp installApp);

    abstract void processAction(UninstallApp uninstallApp);

    void evaluateResults() throws JSONException {
        sortEventReportObjects(OutputType.EXPECTED, mExpectedOutput.mEventReportObjects);
        sortEventReportObjects(OutputType.ACTUAL, mActualOutput.mEventReportObjects);
        sortAggregateReportObjects(OutputType.EXPECTED, mExpectedOutput.mAggregateReportObjects);
        sortAggregateReportObjects(OutputType.ACTUAL, mActualOutput.mAggregateReportObjects);
        sortEventReportObjects(OutputType.EXPECTED, mExpectedOutput.mDebugEventReportObjects);
        sortEventReportObjects(OutputType.ACTUAL, mActualOutput.mDebugEventReportObjects);
        sortAggregateReportObjects(
                OutputType.EXPECTED, mExpectedOutput.mDebugAggregateReportObjects);
        sortAggregateReportObjects(OutputType.ACTUAL, mActualOutput.mDebugAggregateReportObjects);
        sortUnparsableRegistrationObjects(mExpectedOutput.mUnparsableRegistrationObjects);
        sortUnparsableRegistrationObjects(mActualOutput.mUnparsableRegistrationObjects);
        Assert.assertTrue(getTestFailureMessage(mExpectedOutput, mActualOutput),
                areEqual(mExpectedOutput, mActualOutput));
    }

    private void setupDeviceConfigForPhFlags() {
        mPhFlagsMap
                .keySet()
                .forEach(
                        key -> {
                                log(String.format(
                                        "Setting PhFlag %s to %s", key, mPhFlagsMap.get(key)));
                                DeviceConfig.setProperty(
                                        DeviceConfig.NAMESPACE_ADSERVICES,
                                        key,
                                        mPhFlagsMap.get(key),
                                        false);
                        });
    }
}
