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

import static com.android.cts.verifier.ReportExporter.LOGS_DIRECTORY;
import static com.android.cts.verifier.TestListActivity.sCurrentDisplayMode;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.hardware.devicestate.DeviceStateManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.android.compatibility.common.util.ReportLog;
import com.android.compatibility.common.util.TestScreenshotsMetadata;
import com.android.cts.verifier.TestListActivity.DisplayMode;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * {@link BaseAdapter} that handles loading, refreshing, and setting test results. What tests are
 * shown can be customized by overriding {@link #getRows()}. See {@link ArrayTestListAdapter} and
 * {@link ManifestTestListAdapter} for examples.
 */
public abstract class TestListAdapter extends BaseAdapter {

    /** Activities implementing {@link Intent#ACTION_MAIN} and this will appear in the list. */
    public static final String CATEGORY_MANUAL_TEST = "android.cts.intent.category.MANUAL_TEST";

    /** View type for a category of tests like "Sensors" or "Features" */
    private static final int CATEGORY_HEADER_VIEW_TYPE = 0;

    /** View type for an actual test like the Accelerometer test. */
    private static final int TEST_VIEW_TYPE = 1;

    /** Padding around the text views and icons. */
    private static final int PADDING = 10;

    private final Context mContext;

    /** Immutable data of tests like the test's title and launch intent. */
    private final List<TestListItem> mRows = new ArrayList<TestListItem>();

    /** Mutable test results that will change as each test activity finishes. */
    private final Map<String, Integer> mTestResults = new HashMap<String, Integer>();

    /** Map from test name to test details. */
    private final Map<String, String> mTestDetails = new HashMap<String, String>();

    /** Map from test name to {@link ReportLog}. */
    private final Map<String, ReportLog> mReportLogs = new HashMap<String, ReportLog>();

    /** Map from test name to {@link TestResultHistoryCollection}. */
    private final Map<String, TestResultHistoryCollection> mHistories = new HashMap<>();

    /** Map from test name to {@link TestScreenshotsMetadata}. */
    private final Map<String, TestScreenshotsMetadata> mScreenshotsMetadata = new HashMap<>();

    /** Flag to identify whether the mHistories has been loaded. */
    private final AtomicBoolean mHasLoadedResultHistory = new AtomicBoolean(false);

    private final LayoutInflater mLayoutInflater;

    /**
     * Map from display mode to the list of {@link TestListItem}. Records the TestListItem from main
     * view only, including unfolded mode and folded mode respectively.
     */
    protected Map<String, List<TestListItem>> mDisplayModesTests = new HashMap<>();

    /** A keyword to help filter out test cases by the test name. */
    protected String mTestFilter;

    /** {@link ListView} row that is either a test category header or a test. */
    public static class TestListItem {

        /** Title shown in the {@link ListView}. */
        public final String title;

        /** Test name with class and test ID to uniquely identify the test. Null for categories. */
        public String testName;

        /** Intent used to launch the activity from the list. Null for categories. */
        public final Intent intent;

        /** Features necessary to run this test. */
        public final String[] requiredFeatures;

        /** Configs necessary to run this test. */
        public final String[] requiredConfigs;

        /** Intent actions necessary to run this test. */
        public final String[] requiredActions;

        /** Features such that, if any present, the test gets excluded from being shown. */
        public final String[] excludedFeatures;

        /** User "types" that, if any present, the test gets excluded from being shown. */
        public final String[] excludedUserTypes;

        /** If any of of the features are present the test is meaningful to run. */
        public final String[] applicableFeatures;

        /** Configs display mode to run this test. */
        public final String displayMode;

        /** Configs test pass mode to record the test result. */
        public final boolean passInEitherMode;

        // TODO: refactor to use a Builder approach instead

        /**
         * Creates a new test item with given required, excluded and applicable features, the
         * context and the resource ID of the title.
         */
        public static TestListItem newTest(
                Context context,
                int titleResId,
                String testName,
                Intent intent,
                String[] requiredFeatures,
                String[] excludedFeatures,
                String[] applicableFeatures) {
            return newTest(
                    context.getString(titleResId),
                    testName,
                    intent,
                    requiredFeatures,
                    excludedFeatures,
                    applicableFeatures);
        }

        /**
         * Creates a new test item with given required and excluded features, the context and the
         * resource ID of the title.
         */
        public static TestListItem newTest(
                Context context,
                int titleResId,
                String testName,
                Intent intent,
                String[] requiredFeatures,
                String[] excludedFeatures) {
            return newTest(
                    context.getString(titleResId),
                    testName,
                    intent,
                    requiredFeatures,
                    excludedFeatures,
                    /* applicableFeatures= */ null);
        }

        /**
         * Creates a new test item with given required features, the context and the resource ID of
         * the title.
         */
        public static TestListItem newTest(
                Context context,
                int titleResId,
                String testName,
                Intent intent,
                String[] requiredFeatures) {
            return newTest(
                    context.getString(titleResId),
                    testName,
                    intent,
                    requiredFeatures,
                    /* excludedFeatures= */ null,
                    /* applicableFeatures= */ null);
        }

        /**
         * Creates a new test item with given display mode, the required, excluded, applicable
         * features and required configureations and actions.
         */
        public static TestListItem newTest(
                String title,
                String testName,
                Intent intent,
                String[] requiredFeatures,
                String[] requiredConfigs,
                String[] requiredActions,
                String[] excludedFeatures,
                String[] applicableFeatures,
                String[] excludedUserTypes,
                String displayMode,
                boolean passInEitherMode) {
            return new TestListItem(
                    title,
                    testName,
                    intent,
                    requiredFeatures,
                    requiredConfigs,
                    requiredActions,
                    excludedFeatures,
                    applicableFeatures,
                    excludedUserTypes,
                    displayMode,
                    passInEitherMode);
        }

        /**
         * Creates a new test item with given display mode, the required, excluded, applicable
         * features, required configurations and actions and test pass mode.
         */
        public static TestListItem newTest(
                String title,
                String testName,
                Intent intent,
                String[] requiredFeatures,
                String[] requiredConfigs,
                String[] requiredActions,
                String[] excludedFeatures,
                String[] applicableFeatures,
                String[] excludedUserTypes,
                String displayMode) {
            return new TestListItem(
                    title,
                    testName,
                    intent,
                    requiredFeatures,
                    requiredConfigs,
                    requiredActions,
                    excludedFeatures,
                    applicableFeatures,
                    excludedUserTypes,
                    displayMode,
                    /* passInEitherMode= */ false);
        }

        /**
         * Creates a new test item with given required, excluded, applicable features and required
         * configureations.
         */
        public static TestListItem newTest(
                String title,
                String testName,
                Intent intent,
                String[] requiredFeatures,
                String[] requiredConfigs,
                String[] excludedFeatures,
                String[] applicableFeatures) {
            return new TestListItem(
                    title,
                    testName,
                    intent,
                    requiredFeatures,
                    requiredConfigs,
                    /* requiredActions= */ null,
                    excludedFeatures,
                    applicableFeatures,
                    /* excludedUserTypes= */ null,
                    /* displayMode= */ null,
                    /* passInEitherMode= */ false);
        }

        /** Creates a new test item with given required, excluded and applicable features. */
        public static TestListItem newTest(
                String title,
                String testName,
                Intent intent,
                String[] requiredFeatures,
                String[] excludedFeatures,
                String[] applicableFeatures) {
            return new TestListItem(
                    title,
                    testName,
                    intent,
                    requiredFeatures,
                    /* requiredConfigs= */ null,
                    /* requiredActions= */ null,
                    excludedFeatures,
                    applicableFeatures,
                    /* excludedUserTypes= */ null,
                    /* displayMode= */ null,
                    /* passInEitherMode= */ false);
        }

        /** Creates a new test item with given required and excluded features. */
        public static TestListItem newTest(
                String title,
                String testName,
                Intent intent,
                String[] requiredFeatures,
                String[] excludedFeatures) {
            return new TestListItem(
                    title,
                    testName,
                    intent,
                    requiredFeatures,
                    /* requiredConfigs= */ null,
                    /* requiredActions= */ null,
                    excludedFeatures,
                    /* applicableFeatures= */ null,
                    /* excludedUserTypes= */ null,
                    /* displayMode= */ null,
                    /* passInEitherMode= */ false);
        }

        /** Creates a new test item with given required features. */
        public static TestListItem newTest(
                String title, String testName, Intent intent, String[] requiredFeatures) {
            return new TestListItem(
                    title,
                    testName,
                    intent,
                    requiredFeatures,
                    /* requiredConfigs= */ null,
                    /* requiredActions= */ null,
                    /* excludedFeatures= */ null,
                    /* applicableFeatures= */ null,
                    /* excludedUserTypes= */ null,
                    /* displayMode= */ null,
                    /* passInEitherMode= */ false);
        }

        public static TestListItem newCategory(Context context, int titleResId) {
            return newCategory(context.getString(titleResId));
        }

        public static TestListItem newCategory(String title) {
            return new TestListItem(
                    title,
                    /* testName= */ null,
                    /* intent= */ null,
                    /* requiredFeatures= */ null,
                    /* requiredConfigs= */ null,
                    /* requiredActions= */ null,
                    /* excludedFeatures= */ null,
                    /* applicableFeatures= */ null,
                    /* excludedUserTypes= */ null,
                    /* displayMode= */ null,
                    /* passInEitherMode= */ false);
        }

        protected TestListItem(
                String title,
                String testName,
                Intent intent,
                String[] requiredFeatures,
                String[] excludedFeatures,
                String[] applicableFeatures) {
            this(
                    title,
                    testName,
                    intent,
                    requiredFeatures,
                    /* requiredConfigs= */ null,
                    /* requiredActions= */ null,
                    excludedFeatures,
                    applicableFeatures,
                    /* excludedUserTypes= */ null,
                    /* displayMode= */ null,
                    /* passInEitherMode= */ false);
        }

        protected TestListItem(
                String title,
                String testName,
                Intent intent,
                String[] requiredFeatures,
                String[] requiredConfigs,
                String[] requiredActions,
                String[] excludedFeatures,
                String[] applicableFeatures,
                String[] excludedUserTypes,
                String displayMode,
                boolean passInEitherMode) {
            this.title = title;
            this.testName = setTestNameSuffix(sCurrentDisplayMode, testName);
            this.intent = intent;
            this.requiredActions = requiredActions;
            this.requiredFeatures = requiredFeatures;
            this.requiredConfigs = requiredConfigs;
            this.excludedFeatures = excludedFeatures;
            this.applicableFeatures = applicableFeatures;
            this.excludedUserTypes = excludedUserTypes;
            this.displayMode = displayMode;
            this.passInEitherMode = passInEitherMode;
        }

        boolean isTest() {
            return intent != null;
        }
    }

    public TestListAdapter(Context context) {
        this.mContext = context;
        this.mLayoutInflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        TestResultContentObserver observer = new TestResultContentObserver();
        ContentResolver resolver = context.getContentResolver();
        resolver.registerContentObserver(
                TestResultsProvider.getResultContentUri(context), true, observer);
    }

    public void loadTestResults() {
        new RefreshTestResultsTask().execute();
    }

    public void clearTestResults() {
        new ClearTestResultsTask().execute();
    }

    public void setTestResult(TestResult testResult) {
        String name = testResult.getName();

        // Append existing history
        TestResultHistoryCollection histories = testResult.getHistoryCollection();
        histories.merge(null, mHistories.get(name));

        new SetTestResultTask(
                        name,
                        testResult.getResult(),
                        testResult.getDetails(),
                        testResult.getReportLog(),
                        histories,
                        mScreenshotsMetadata.get(name))
                .execute();
    }

    void setTestFilter(String testFilter) {
        mTestFilter = testFilter;
    }

    class RefreshTestResultsTask extends AsyncTask<Void, Void, RefreshResult> {

        @Override
        protected RefreshResult doInBackground(Void... params) {
            return getRefreshResults(getRows());
        }

        @Override
        protected void onPostExecute(RefreshResult result) {
            super.onPostExecute(result);
            mRows.clear();
            mRows.addAll(result.mItems);
            mTestResults.clear();
            mTestResults.putAll(result.mResults);
            mTestDetails.clear();
            mTestDetails.putAll(result.mDetails);
            mReportLogs.clear();
            mReportLogs.putAll(result.mReportLogs);
            mHistories.clear();
            mHistories.putAll(result.mHistories);
            mScreenshotsMetadata.clear();
            mScreenshotsMetadata.putAll(result.mScreenshotsMetadata);
            mHasLoadedResultHistory.set(true);
            notifyDataSetChanged();
        }
    }

    static class RefreshResult {
        List<TestListItem> mItems;
        Map<String, Integer> mResults;
        Map<String, String> mDetails;
        Map<String, ReportLog> mReportLogs;
        Map<String, TestResultHistoryCollection> mHistories;
        Map<String, TestScreenshotsMetadata> mScreenshotsMetadata;

        RefreshResult(
                List<TestListItem> items,
                Map<String, Integer> results,
                Map<String, String> details,
                Map<String, ReportLog> reportLogs,
                Map<String, TestResultHistoryCollection> histories,
                Map<String, TestScreenshotsMetadata> screenshotsMetadata) {
            mItems = items;
            mResults = results;
            mDetails = details;
            mReportLogs = reportLogs;
            mHistories = histories;
            mScreenshotsMetadata = screenshotsMetadata;
        }
    }

    protected abstract List<TestListItem> getRows();

    static final String[] REFRESH_PROJECTION = {
        TestResultsProvider._ID,
        TestResultsProvider.COLUMN_TEST_NAME,
        TestResultsProvider.COLUMN_TEST_RESULT,
        TestResultsProvider.COLUMN_TEST_DETAILS,
        TestResultsProvider.COLUMN_TEST_METRICS,
        TestResultsProvider.COLUMN_TEST_RESULT_HISTORY,
        TestResultsProvider.COLUMN_TEST_SCREENSHOTS_METADATA,
    };

    RefreshResult getRefreshResults(List<TestListItem> items) {
        Map<String, Integer> results = new HashMap<String, Integer>();
        Map<String, String> details = new HashMap<String, String>();
        Map<String, ReportLog> reportLogs = new HashMap<String, ReportLog>();
        Map<String, TestResultHistoryCollection> histories = new HashMap<>();
        Map<String, TestScreenshotsMetadata> screenshotsMetadata = new HashMap<>();
        ContentResolver resolver = mContext.getContentResolver();
        Cursor cursor = null;
        try {
            cursor =
                    resolver.query(
                            TestResultsProvider.getResultContentUri(mContext),
                            REFRESH_PROJECTION,
                            null,
                            null,
                            null);
            if (cursor.moveToFirst()) {
                do {
                    String testName = cursor.getString(1);
                    int testResult = cursor.getInt(2);
                    String testDetails = cursor.getString(3);
                    ReportLog reportLog = (ReportLog) deserialize(cursor.getBlob(4));
                    TestResultHistoryCollection historyCollection =
                            (TestResultHistoryCollection) deserialize(cursor.getBlob(5));
                    TestScreenshotsMetadata screenshots =
                            (TestScreenshotsMetadata) deserialize(cursor.getBlob(6));
                    results.put(testName, testResult);
                    details.put(testName, testDetails);
                    reportLogs.put(testName, reportLog);
                    histories.put(testName, historyCollection);
                    screenshotsMetadata.put(testName, screenshots);
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return new RefreshResult(
                items, results, details, reportLogs, histories, screenshotsMetadata);
    }

    class ClearTestResultsTask extends AsyncTask<Void, Void, Void> {

        private void deleteDirectory(File file) {
            for (File subfile : file.listFiles()) {
                if (subfile.isDirectory()) {
                    deleteDirectory(subfile);
                }
                subfile.delete();
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.delete(TestResultsProvider.getResultContentUri(mContext), "1", null);

            // Apart from deleting metadata from content resolver database, need to delete
            // files generated in LOGS_DIRECTORY. For example screenshots.
            File resFolder =
                    new File(
                            Environment.getExternalStorageDirectory().getAbsolutePath()
                                    + File.separator
                                    + LOGS_DIRECTORY);
            deleteDirectory(resFolder);

            return null;
        }
    }

    class SetTestResultTask extends AsyncTask<Void, Void, Void> {

        private final String mTestName;
        private final int mResult;
        private final String mDetails;
        private final ReportLog mReportLog;
        private final TestResultHistoryCollection mHistoryCollection;
        private final TestScreenshotsMetadata mScreenshotsMetadata;

        SetTestResultTask(
                String testName,
                int result,
                String details,
                ReportLog reportLog,
                TestResultHistoryCollection historyCollection,
                TestScreenshotsMetadata screenshotsMetadata) {
            mTestName = testName;
            mResult = result;
            mDetails = details;
            mReportLog = reportLog;
            mHistoryCollection = historyCollection;
            mScreenshotsMetadata = screenshotsMetadata;
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (mHasLoadedResultHistory.get()) {
                mHistoryCollection.merge(null, mHistories.get(mTestName));
            } else {
                // Loads history from ContentProvider directly if it has not been loaded yet.
                ContentResolver resolver = mContext.getContentResolver();

                try (Cursor cursor =
                        resolver.query(
                                TestResultsProvider.getTestNameUri(mContext, mTestName),
                                new String[] {TestResultsProvider.COLUMN_TEST_RESULT_HISTORY},
                                null,
                                null,
                                null)) {
                    if (cursor.moveToFirst()) {
                        do {
                            TestResultHistoryCollection historyCollection =
                                    (TestResultHistoryCollection) deserialize(cursor.getBlob(0));
                            mHistoryCollection.merge(null, historyCollection);
                        } while (cursor.moveToNext());
                    }
                }
            }
            TestResultsProvider.setTestResult(
                    mContext,
                    mTestName,
                    mResult,
                    mDetails,
                    mReportLog,
                    mHistoryCollection,
                    mScreenshotsMetadata);
            return null;
        }
    }

    class TestResultContentObserver extends ContentObserver {

        public TestResultContentObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            loadTestResults();
        }
    }

    @Override
    public boolean areAllItemsEnabled() {
        // Section headers for test categories are not clickable.
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        if (getItem(position) == null) {
            return false;
        }
        return getItem(position).isTest();
    }

    @Override
    public int getItemViewType(int position) {
        if (getItem(position) == null) {
            return CATEGORY_HEADER_VIEW_TYPE;
        }
        return getItem(position).isTest() ? TEST_VIEW_TYPE : CATEGORY_HEADER_VIEW_TYPE;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getCount() {
        return mRows.size();
    }

    @Override
    public TestListItem getItem(int position) {
        return mRows.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    /** Gets {@link TestListItem} with the given test name. */
    public TestListItem getItemByName(String testName) {
        for (TestListItem item : mRows) {
            if (item != null && item.testName != null && item.testName.equals(testName)) {
                return item;
            }
        }
        return null;
    }

    public int getTestResult(int position) {
        TestListItem item = getItem(position);
        return mTestResults.containsKey(item.testName)
                ? mTestResults.get(item.testName)
                : TestResult.TEST_RESULT_NOT_EXECUTED;
    }

    public String getTestDetails(int position) {
        TestListItem item = getItem(position);
        return mTestDetails.containsKey(item.testName) ? mTestDetails.get(item.testName) : null;
    }

    public ReportLog getReportLog(int position) {
        TestListItem item = getItem(position);
        return mReportLogs.containsKey(item.testName) ? mReportLogs.get(item.testName) : null;
    }

    /**
     * Get test result histories.
     *
     * @param position The position of test.
     * @return A {@link TestResultHistoryCollection} object containing test result histories of
     *     tests.
     */
    public TestResultHistoryCollection getHistoryCollection(int position) {
        TestListItem item = getItem(position);
        if (item == null) {
            return null;
        }
        return mHistories.containsKey(item.testName) ? mHistories.get(item.testName) : null;
    }

    /**
     * Get test screenshots metadata
     *
     * @param position The position of test
     * @return A {@link TestScreenshotsMetadata} object containing test screenshots metadata.
     */
    public TestScreenshotsMetadata getScreenshotsMetadata(String mode, int position) {
        TestListItem item = getItem(mode, position);
        return mScreenshotsMetadata.containsKey(item.testName)
                ? mScreenshotsMetadata.get(item.testName)
                : null;
    }

    /**
     * Get test item by the given display mode and position.
     *
     * @param mode The display mode.
     * @param position The position of test.
     * @return A {@link TestListItem} object containing the test item.
     */
    public TestListItem getItem(String mode, int position) {
        return mDisplayModesTests.get(mode).get(position);
    }

    /**
     * Get test item count by the given display mode.
     *
     * @param mode The display mode.
     * @return A count of test items.
     */
    public int getCount(String mode) {
        return mDisplayModesTests.getOrDefault(mode, new ArrayList<>()).size();
    }

    /**
     * Get test result by the given display mode and position.
     *
     * @param mode The display mode.
     * @param position The position of test.
     * @return The test item result.
     */
    public int getTestResult(String mode, int position) {
        TestListItem item = mDisplayModesTests.get(mode).get(position);
        return mTestResults.containsKey(item.testName)
                ? mTestResults.get(item.testName)
                : TestResult.TEST_RESULT_NOT_EXECUTED;
    }

    /**
     * Get test details by the given display mode and position.
     *
     * @param mode The display mode.
     * @param position The position of test.
     * @return A string containing the test details.
     */
    public String getTestDetails(String mode, int position) {
        TestListItem item = mDisplayModesTests.get(mode).get(position);
        return mTestDetails.containsKey(item.testName) ? mTestDetails.get(item.testName) : null;
    }

    /**
     * Get test report log by the given display mode and position.
     *
     * @param mode The display mode.
     * @param position The position of test.
     * @return A {@link ReportLog} object containing the test report log of the test item.
     */
    public ReportLog getReportLog(String mode, int position) {
        TestListItem item = mDisplayModesTests.get(mode).get(position);
        return mReportLogs.containsKey(item.testName) ? mReportLogs.get(item.testName) : null;
    }

    /**
     * Get test result histories by the given display mode and position.
     *
     * @param mode The display mode.
     * @param position The position of test.
     * @return A {@link TestResultHistoryCollection} object containing the test result histories of
     *     the test item.
     */
    public TestResultHistoryCollection getHistoryCollection(String mode, int position) {
        TestListItem item = mDisplayModesTests.get(mode).get(position);
        return mHistories.containsKey(item.testName) ? mHistories.get(item.testName) : null;
    }

    public boolean allTestsPassed() {
        for (TestListItem item : mRows) {
            if (item != null
                    && item.isTest()
                    && (!mTestResults.containsKey(item.testName)
                            || (mTestResults.get(item.testName)
                                    != TestResult.TEST_RESULT_PASSED))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView textView;
        if (convertView == null) {
            int layout = getLayout(position);
            textView = (TextView) mLayoutInflater.inflate(layout, parent, false);
        } else {
            textView = (TextView) convertView;
        }

        TestListItem item = getItem(position);

        if (item == null) {
            return textView;
        }

        textView.setText(item.title);
        textView.setPadding(PADDING, 0, PADDING, 0);
        textView.setCompoundDrawablePadding(PADDING);

        if (item.isTest()) {
            int testResult = getTestResult(position);
            int backgroundResource = 0;
            int iconResource = 0;

            /** TODO: Remove fs_ prefix from feature icons since they are used here too. */
            switch (testResult) {
                case TestResult.TEST_RESULT_PASSED:
                    backgroundResource = R.drawable.test_pass_gradient;
                    iconResource = R.drawable.fs_good;
                    break;

                case TestResult.TEST_RESULT_FAILED:
                    backgroundResource = R.drawable.test_fail_gradient;
                    iconResource = R.drawable.fs_error;
                    break;

                case TestResult.TEST_RESULT_NOT_EXECUTED:
                    break;

                default:
                    throw new IllegalArgumentException("Unknown test result: " + testResult);
            }

            textView.setBackgroundResource(backgroundResource);
            textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, iconResource, 0);
        }

        return textView;
    }

    /**
     * Uses {@link DeviceStateManager} to determine if the device is foldable or not. It relies on
     * the OEM exposing supported states, and setting
     * com.android.internal.R.array.config_foldedDeviceStates correctly with the folded states.
     *
     * @return true if the device is foldable, false otherwise
     */
    public boolean isFoldableDevice() {
        DeviceStateManager deviceStateManager = mContext.getSystemService(DeviceStateManager.class);
        if (deviceStateManager == null) {
            return false;
        }
        Set<Integer> supportedStates =
                Arrays.stream(deviceStateManager.getSupportedStates())
                        .boxed()
                        .collect(Collectors.toSet());
        int identifier =
                mContext.getResources()
                        .getIdentifier("config_foldedDeviceStates", "array", "android");
        int[] foldedDeviceStates = mContext.getResources().getIntArray(identifier);
        return Arrays.stream(foldedDeviceStates).anyMatch(supportedStates::contains);
    }

    private int getLayout(int position) {
        int viewType = getItemViewType(position);
        switch (viewType) {
            case CATEGORY_HEADER_VIEW_TYPE:
                return R.layout.test_category_row;
            case TEST_VIEW_TYPE:
                return android.R.layout.simple_list_item_1;
            default:
                throw new IllegalArgumentException("Illegal view type: " + viewType);
        }
    }

    public static Object deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        ByteArrayInputStream byteStream = new ByteArrayInputStream(bytes);
        ObjectInputStream objectInput = null;
        try {
            objectInput = new ObjectInputStream(byteStream);
            return objectInput.readObject();
        } catch (IOException e) {
            return null;
        } catch (ClassNotFoundException e) {
            return null;
        } finally {
            try {
                if (objectInput != null) {
                    objectInput.close();
                }
                byteStream.close();
            } catch (IOException e) {
                // Ignore close exception.
            }
        }
    }

    /**
     * Sets test name suffix. In the folded mode, the suffix is [folded]; otherwise, it is empty
     * string.
     *
     * @param mode A string of current display mode.
     * @param name A string of test name.
     * @return A string of test name with suffix, [folded], in the folded mode. A string of input
     *     test name in the unfolded mode.
     */
    public static String setTestNameSuffix(String mode, String name) {
        if (name != null
                && mode.equalsIgnoreCase(DisplayMode.FOLDED.toString())
                && !name.endsWith(DisplayMode.FOLDED.asSuffix())) {
            return name + DisplayMode.FOLDED.asSuffix();
        }
        return name;
    }

    /**
     * Removes test name suffix. In the unfolded mode, remove the suffix [folded].
     *
     * @param mode A string of current display mode.
     * @param name A string of test name.
     * @return A string of test name without suffix, [folded], in the unfolded mode. A string of
     *     input test name in the folded mode.
     */
    public static String removeTestNameSuffix(String mode, String name) {
        if (name != null
                && mode.equalsIgnoreCase(DisplayMode.UNFOLDED.toString())
                && name.endsWith(DisplayMode.FOLDED.asSuffix())) {
            return name.substring(0, name.length() - DisplayMode.FOLDED.asSuffix().length());
        }
        return name;
    }
}
