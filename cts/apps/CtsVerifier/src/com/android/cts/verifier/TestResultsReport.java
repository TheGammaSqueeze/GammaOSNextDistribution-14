/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.cts.verifier;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;

import com.android.compatibility.common.util.DevicePropertyInfo;
import com.android.compatibility.common.util.ICaseResult;
import com.android.compatibility.common.util.IInvocationResult;
import com.android.compatibility.common.util.IModuleResult;
import com.android.compatibility.common.util.ITestResult;
import com.android.compatibility.common.util.InvocationResult;
import com.android.compatibility.common.util.ReportLog;
import com.android.compatibility.common.util.TestResultHistory;
import com.android.compatibility.common.util.TestScreenshotsMetadata;
import com.android.compatibility.common.util.TestStatus;
import com.android.cts.verifier.TestListActivity.DisplayMode;
import com.android.cts.verifier.TestListAdapter.TestListItem;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Helper class for creating an {@code InvocationResult} for CTS result generation.
 */
class TestResultsReport {

    /** Version of the test report. Increment whenever adding new tags and attributes. */
    private static final int REPORT_VERSION = 2;

    /** Format of the report's creation time. Maintain the same format at CTS. */
    private static DateFormat DATE_FORMAT =
            new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH);

    private static final String PREFIX_TAG = "build_";
    private static final String TEST_RESULTS_REPORT_TAG = "test-results-report";
    private static final String VERIFIER_INFO_TAG = "verifier-info";
    private static final String DEVICE_INFO_TAG = "device-info";
    private static final String BUILD_INFO_TAG = "build-info";
    private static final String TEST_RESULTS_TAG = "test-results";
    private static final String TEST_TAG = "test";
    private static final String TEST_DETAILS_TAG = "details";

    private static final String TEST_CASE_NAME = "manualTests";

    private final Context mContext;

    private final TestListAdapter mAdapter;

    TestResultsReport(Context context, TestListAdapter adapter) {
        this.mContext = context;
        this.mAdapter = adapter;
    }

    IInvocationResult generateResult() {
        String abis = null;
        String abis32 = null;
        String abis64 = null;
        String versionBaseOs = null;
        String versionSecurityPatch = null;
        String versionRelease = null;
        IInvocationResult result = new InvocationResult();
        IModuleResult moduleResult = result.getOrCreateModule(
                mContext.getResources().getString(R.string.module_id));

        // Collect build fields available in API level 21
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            abis = TextUtils.join(",", Build.SUPPORTED_ABIS);
            abis32 = TextUtils.join(",", Build.SUPPORTED_32_BIT_ABIS);
            abis64 = TextUtils.join(",", Build.SUPPORTED_64_BIT_ABIS);
        }

        // Collect build fields available in API level 23
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            versionBaseOs = Build.VERSION.BASE_OS;
            versionSecurityPatch = Build.VERSION.SECURITY_PATCH;
        }

        versionRelease = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                ? Build.VERSION.RELEASE_OR_CODENAME : Build.VERSION.RELEASE;

        // at the time of writing, the build class has no REFERENCE_FINGERPRINT property
        String referenceFingerprint = null;

        DevicePropertyInfo devicePropertyInfo = new DevicePropertyInfo(Build.CPU_ABI,
                Build.CPU_ABI2, abis, abis32, abis64, Build.BOARD, Build.BRAND, Build.DEVICE,
                Build.FINGERPRINT, null, Build.ID, Build.MANUFACTURER, Build.MODEL, Build.PRODUCT,
                referenceFingerprint, Build.getSerial(), Build.TAGS, Build.TYPE, versionBaseOs,
                versionRelease, Integer.toString(Build.VERSION.SDK_INT),
                versionSecurityPatch, Build.VERSION.INCREMENTAL);

        // add device properties to the result with a prefix tag for each key
        for (Entry<String, String> entry :
                devicePropertyInfo.getPropertytMapWithPrefix(PREFIX_TAG).entrySet()) {
            String entryValue = entry.getValue();
            if (entryValue != null) {
                result.addInvocationInfo(entry.getKey(), entry.getValue());
            }
        }

        // Get test result, including test name, result, report log, details and histories.
        getCaseResult(moduleResult);

        return result;
    }

    String getContents() {
        // TODO: remove getContents and everything that depends on it
        return "Report viewing is deprecated. See contents on the SD Card.";
    }

    /**
     * Get case results per test, including result, report log, details and histories.
     *
     * @param IModuleResult The module result bound with {@link IInvocationResult}.
     */
    private void getCaseResult(IModuleResult moduleResult) {
        ICaseResult caseResult = moduleResult.getOrCreateResult(TEST_CASE_NAME);
        int notExecutedCount = 0;
        for (DisplayMode mode: DisplayMode.values()) {
            String displayMode = mode.toString();
            int count = mAdapter.getCount(displayMode);
            for (int i = 0; i < count; i++) {
                TestListItem item = mAdapter.getItem(displayMode, i);
                if (item.isTest()) {
                    ITestResult currentTestResult = caseResult.getOrCreateResult(item.testName);
                    TestStatus resultStatus =
                        getTestResultStatus(mAdapter.getTestResult(displayMode, i));
                    if (resultStatus == null) {
                        ++notExecutedCount;
                    }
                    currentTestResult.setResultStatus(resultStatus);
                    // TODO: report test details with Extended Device Info (EDI) or CTS metrics
                    String details = mAdapter.getTestDetails(displayMode, i);
                    currentTestResult.setMessage(details);

                    ReportLog reportLog = mAdapter.getReportLog(displayMode, i);
                    if (reportLog != null) {
                        currentTestResult.setReportLog(reportLog);
                    }

                    TestResultHistoryCollection historyCollection = mAdapter
                        .getHistoryCollection(displayMode, i);
                    if (historyCollection != null) {
                        List<TestResultHistory> leafTestHistories =
                            getTestResultHistories(historyCollection);
                        currentTestResult.setTestResultHistories(leafTestHistories);
                    }

                    TestScreenshotsMetadata screenshotsMetadata = mAdapter
                            .getScreenshotsMetadata(displayMode, i);
                    if (screenshotsMetadata != null) {
                        currentTestResult.setTestScreenshotsMetadata(screenshotsMetadata);
                    }
                }
            }
        }
        moduleResult.setDone(true);
        moduleResult.setNotExecuted(notExecutedCount);
    }

    private TestStatus getTestResultStatus(int testResult) {
        switch (testResult) {
            case TestResult.TEST_RESULT_PASSED:
                return TestStatus.PASS;

            case TestResult.TEST_RESULT_FAILED:
                return TestStatus.FAIL;

            case TestResult.TEST_RESULT_NOT_EXECUTED:
                return null;

            default:
                throw new IllegalArgumentException("Unknown test result: " + testResult);
        }
    }

    /**
     * Get test histories per test by filtering out non-leaf histories.
     *
     * @param TestResultHistoryCollection The raw test history collection.
     * @return A list containing test result histories per test.
     */
    @SuppressWarnings("ReturnValueIgnored")
    private List<TestResultHistory> getTestResultHistories(
        TestResultHistoryCollection historyCollection) {
        // Get non-terminal prefixes.
        Set<String> prefixes = new HashSet<>();
        for (TestResultHistory history : historyCollection.asSet()) {
            Arrays.stream(history.getTestName().split(":")).reduce(
                (total, current) -> {
                    prefixes.add(total);
                    return total + ":" + current;
                });
        }

        // Filter out non-leaf test histories.
        List<TestResultHistory> leafTestHistories =
            new ArrayList<TestResultHistory>();
        for (TestResultHistory history : historyCollection.asSet()) {
            if (!prefixes.contains(history.getTestName())) {
                leafTestHistories.add(history);
            }
        }
        return leafTestHistories;
    }
}
