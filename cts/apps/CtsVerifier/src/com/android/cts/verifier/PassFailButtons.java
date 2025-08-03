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

import static com.android.cts.verifier.TestListActivity.sCurrentDisplayMode;
import static com.android.cts.verifier.TestListAdapter.setTestNameSuffix;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.Toast;

import com.android.compatibility.common.util.ReportLog;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * {@link Activity}s to handle clicks to the pass and fail buttons of the pass fail buttons layout.
 *
 * <ol>
 *     <li>Include the pass fail buttons layout in your layout:
 *         <pre><include layout="@layout/pass_fail_buttons" /></pre>
 *     </li>
 *     <li>Extend one of the activities and call setPassFailButtonClickListeners after
 *         setting your content view.</li>
 *     <li>Make sure to call setResult(RESULT_CANCEL) in your Activity initially.</li>
 *     <li>Optionally call setInfoTextResources to add an info button that will show a
 *         dialog with instructional text.</li>
 * </ol>
 */
public class PassFailButtons {
    private static final String INFO_TAG = "CtsVerifierInstructions";

    // Need different IDs for these alerts or else the first one created
    // will just be reused, with the old title/message.
    private static final int INFO_DIALOG_ID = 1337;
    private static final int REPORTLOG_DIALOG_ID = 1338;

    private static final String INFO_DIALOG_VIEW_ID = "infoDialogViewId";
    private static final String INFO_DIALOG_TITLE_ID = "infoDialogTitleId";
    private static final String INFO_DIALOG_MESSAGE_ID = "infoDialogMessageId";

    // ReportLog file for CTS-Verifier. The "stream" name gets mapped to the test class name.
    public static final String GENERAL_TESTS_REPORT_LOG_NAME = "CtsVerifierGeneralTestCases";
    public static final String AUDIO_TESTS_REPORT_LOG_NAME = "CtsVerifierAudioTestCases";

    private static final String SECTION_UNDEFINED = "undefined_section_name";

    // Interface mostly for making documentation and refactoring easier...
    public interface PassFailActivity {

        /**
         * Hooks up the pass and fail buttons to click listeners that will record the test results.
         * <p>
         * Call from {@link Activity#onCreate} after {@link Activity #setContentView(int)}.
         */
        void setPassFailButtonClickListeners();

        /**
         * Adds an initial informational dialog that appears when entering the test activity for
         * the first time. Also enables the visibility of an "Info" button between the "Pass" and
         * "Fail" buttons that can be clicked to show the information dialog again.
         * <p>
         * Call from {@link Activity#onCreate} after {@link Activity #setContentView(int)}.
         *
         * @param titleId for the text shown in the dialog title area
         * @param messageId for the text shown in the dialog's body area
         */
        void setInfoResources(int titleId, int messageId, int viewId);

        View getPassButton();

        /**
         * Returns a unique identifier for the test.  Usually, this is just the class name.
         */
        String getTestId();

        /** @return null or details about the test run. */
        String getTestDetails();

        /**
         * Set the result of the test and finish the activity.
         *
         * @param passed Whether or not the test passed.
         */
        void setTestResultAndFinish(boolean passed);

        /**
         * @return true if the test module will write a ReportLog entry
         *
         * Note that in order to obtain a non-null CtsVerifierReportLog object from
         * the getReportLog() method, the activity must override this method to return true
         * and should implement the getReportFileName(), getReportSectionName() and
         * recordTestResults() methods.
         *
         * If this method returns true and the user did not setup up CtsVerifier correctly
         * with respect to accessing local data on the DUT;
         * <code>
         * adb shell appops set com.android.cts.verifier android:read_device_identifiers allow
         * adb shell appops set com.android.cts.verifier MANAGE_EXTERNAL_STORAGE 0
         * </code>
         * a warning dialog will be displayed when invoking that test.
         */
        public boolean requiresReportLog();

        /**
         * @return The name of the file to store the (suite of) ReportLog information.
         *
         * If a test uses the CtsVerifierReportLog, it should implement this method to provide
         * a file name for the (JSON) report log data. This does not need to be unique to the
         * test, perhaps specific to a set of related tests.
         */
        public String getReportFileName();

        /**
         * @return A unique name to serve as a section header in the CtsVerifierReportLog file.
         * Tests need to conform to the underscore_delineated_name standard for use with
         * the protobuff/json ReportLog parsing in Google3
         *
         * If the test implements this method, it should also implement the requiresReportLog()
         * method to return true and the getReportFileName() and recordTestResults() methods.
         */
        public String getReportSectionName();

        /**
         * Test subclasses can override this to record their CtsVerifierReportLogs.
         * This is called when the test is exited.
         *
         * NOTE: If the test gathers reportLog data, the test class should implement
         * the requiresReportLog() to return true, and implement the getReportFileName() and
         * getReportSectionName() methods.
         */
        void recordTestResults();

        /**
         * @return A {@link ReportLog} that is used to record test metric data or null.
         *
         * If a test calls this method it must implement the requiresReportLog() to return true,
         * and implement the getReportFileName() and getReportSectionName() methods, otherwise
         * this method will return null.
         */
        CtsVerifierReportLog getReportLog();

        /**
         * @return A {@link TestResultHistoryCollection} that is used to record test execution time.
         */
        TestResultHistoryCollection getHistoryCollection();
    }   /* class PassFailButtons.PassFailActivity */

    public static class Activity extends android.app.Activity implements PassFailActivity {
        private WakeLock mWakeLock;
        private CtsVerifierReportLog mReportLog;
        private final TestResultHistoryCollection mHistoryCollection;

        protected boolean mRequireReportLogToPass;

        public Activity() {
            this.mHistoryCollection = new TestResultHistoryCollection();
            if (requiresReportLog()) {
                // if the subclass reports a report filename, they need a ReportLog object
                newReportLog();
            }
        }

        @Override
        protected void onResume() {
            super.onResume();
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
                mWakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE))
                        .newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "PassFailButtons");
                mWakeLock.acquire();
            }

            if (mReportLog != null && !mReportLog.isOpen()) {
                showReportLogWarningDialog(this);
            }
        }

        @Override
        protected void onPause() {
            super.onPause();
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
                mWakeLock.release();
            }
        }

        @Override
        public void setPassFailButtonClickListeners() {
            setPassFailClickListeners(this);
        }

        @Override
        public void setInfoResources(int titleId, int messageId, int viewId) {
            setInfo(this, titleId, messageId, viewId);
        }

        @Override
        public View getPassButton() {
            return getPassButtonView(this);
        }

        @Override
        public Dialog onCreateDialog(int id, Bundle args) {
            return createDialog(this, id, args);
        }

        @Override
        public String getTestId() {
            return setTestNameSuffix(sCurrentDisplayMode, getClass().getName());
        }

        @Override
        public String getTestDetails() {
            return null;
        }

        @Override
        public void setTestResultAndFinish(boolean passed) {
            PassFailButtons.setTestResultAndFinishHelper(
                    this, getTestId(), getTestDetails(), passed, getReportLog(),
                    getHistoryCollection());
        }

        protected CtsVerifierReportLog newReportLog() {
            return mReportLog = new CtsVerifierReportLog(
                    getReportFileName(), getReportSectionName());
        }

        @Override
        public boolean requiresReportLog() {
            return false;
        }

        @Override
        public CtsVerifierReportLog getReportLog() {
            return mReportLog;
        }

        /**
         * A mechanism to block tests from passing if no ReportLog data has been collected.
         * @return true if the ReportLog is open OR if the test does not require that.
         */
        public boolean isReportLogOkToPass() {
            return !mRequireReportLogToPass || (mReportLog != null & mReportLog.isOpen());
        }

        /**
         * @return The name of the file to store the (suite of) ReportLog information.
         */
        @Override
        public String getReportFileName() { return GENERAL_TESTS_REPORT_LOG_NAME; }

        @Override
        public String getReportSectionName() {
            return setTestNameSuffix(sCurrentDisplayMode, SECTION_UNDEFINED);
        }

        @Override
        public TestResultHistoryCollection getHistoryCollection() { return mHistoryCollection; }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            ActionBar actBar = getActionBar();
            if (actBar != null) {
                actBar.setDisplayHomeAsUpEnabled(true);
            }
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            if (item.getItemId() == android.R.id.home) {
                onBackPressed();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public void recordTestResults() {
            // default - NOP
        }

        protected static void showNonSdkAccessibilityWarningDialog(
                final android.app.Activity activity) {
            Bundle args = new Bundle();
            args.putInt(INFO_DIALOG_TITLE_ID, R.string.nonsdk_interfaces_warning_title);
            args.putInt(INFO_DIALOG_MESSAGE_ID, R.string.nonsdk_interfaces_warning_body);
            args.putInt(INFO_DIALOG_VIEW_ID, -1);
            activity.showDialog(REPORTLOG_DIALOG_ID, args);
        }
    }   /* class PassFailButtons.Activity */

    public static class ListActivity extends android.app.ListActivity implements PassFailActivity {

        private final CtsVerifierReportLog mReportLog;
        private final TestResultHistoryCollection mHistoryCollection;

        public ListActivity() {
            mHistoryCollection = new TestResultHistoryCollection();
            mReportLog = null;
        }

        @Override
        public void setPassFailButtonClickListeners() {
            setPassFailClickListeners(this);
        }

        @Override
        public void setInfoResources(int titleId, int messageId, int viewId) {
            setInfo(this, titleId, messageId, viewId);
        }

        @Override
        public View getPassButton() {
            return getPassButtonView(this);
        }

        @Override
        public Dialog onCreateDialog(int id, Bundle args) {
            return createDialog(this, id, args);
        }

        @Override
        public String getTestId() {
            return setTestNameSuffix(sCurrentDisplayMode, getClass().getName());
        }

        @Override
        public String getTestDetails() {
            return null;
        }

        @Override
        public void setTestResultAndFinish(boolean passed) {
            PassFailButtons.setTestResultAndFinishHelper(
                    this, getTestId(), getTestDetails(), passed, getReportLog(),
                    getHistoryCollection());
        }

        @Override
        public boolean requiresReportLog() {
            return false;
        }

        @Override
        public CtsVerifierReportLog getReportLog() {
            return mReportLog;
        }

        /**
         * @return The name of the file to store the (suite of) ReportLog information.
         */
        @Override
        public String getReportFileName() { return GENERAL_TESTS_REPORT_LOG_NAME; }

        @Override
        public String getReportSectionName() {
            return setTestNameSuffix(sCurrentDisplayMode, SECTION_UNDEFINED);
        }

        @Override
        public TestResultHistoryCollection getHistoryCollection() { return mHistoryCollection; }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            ActionBar actBar = getActionBar();
            if (actBar != null) {
                actBar.setDisplayHomeAsUpEnabled(true);
            }
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            if (item.getItemId() == android.R.id.home) {
                onBackPressed();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public void recordTestResults() {
            // default - NOP
        }
    } // class PassFailButtons.ListActivity

    public static class TestListActivity extends AbstractTestListActivity
            implements PassFailActivity {

        private final CtsVerifierReportLog mReportLog;

        public TestListActivity() {
            // TODO(b/186555602): temporary hack^H^H^H^H workaround to fix crash
            // This DOES NOT in fact fix that bug.
            // if (true) this.mReportLog = new CtsVerifierReportLog(b/186555602, "42"); else

            this.mReportLog = new CtsVerifierReportLog(getReportFileName(), getReportSectionName());
        }

        @Override
        public void setPassFailButtonClickListeners() {
            setPassFailClickListeners(this);
        }

        @Override
        public void setInfoResources(int titleId, int messageId, int viewId) {
            setInfo(this, titleId, messageId, viewId);
        }

        @Override
        public View getPassButton() {
            return getPassButtonView(this);
        }

        @Override
        public Dialog onCreateDialog(int id, Bundle args) {
            return createDialog(this, id, args);
        }

        @Override
        public String getTestId() {
            return setTestNameSuffix(sCurrentDisplayMode, getClass().getName());
        }

        @Override
        public String getTestDetails() {
            return null;
        }

        @Override
        public void setTestResultAndFinish(boolean passed) {
            PassFailButtons.setTestResultAndFinishHelper(
                    this, getTestId(), getTestDetails(), passed, getReportLog(),
                    getHistoryCollection());
        }

        @Override
        public boolean requiresReportLog() {
            return false;
        }

        @Override
        public CtsVerifierReportLog getReportLog() {
            return mReportLog;
        }

        /**
         * @return The name of the file to store the (suite of) ReportLog information.
         */
        @Override
        public String getReportFileName() { return GENERAL_TESTS_REPORT_LOG_NAME; }

        @Override
        public String getReportSectionName() {
            return setTestNameSuffix(sCurrentDisplayMode, SECTION_UNDEFINED);
        }


        /**
         * Get existing test history to aggregate.
         */
        @Override
        public TestResultHistoryCollection getHistoryCollection() {
            List<TestResultHistoryCollection> histories =
                IntStream.range(0, mAdapter.getCount())
                .mapToObj(mAdapter::getHistoryCollection)
                .collect(Collectors.toList());
            TestResultHistoryCollection historyCollection = new TestResultHistoryCollection();
            historyCollection.merge(getTestId(), histories);
            return historyCollection;
        }

        public void updatePassButton() {
            getPassButton().setEnabled(mAdapter.allTestsPassed());
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            ActionBar actBar = getActionBar();
            if (actBar != null) {
                actBar.setDisplayHomeAsUpEnabled(true);
            }
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            if (item.getItemId() == android.R.id.home) {
                onBackPressed();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public void recordTestResults() {
            // default - NOP
        }
    } // class PassFailButtons.TestListActivity

    protected static <T extends android.app.Activity & PassFailActivity>
            void setPassFailClickListeners(final T activity) {
        View.OnClickListener clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View target) {
                setTestResultAndFinish(activity, activity.getTestId(), activity.getTestDetails(),
                        activity.getReportLog(), activity.getHistoryCollection(), target);
            }
        };

        View passButton = activity.findViewById(R.id.pass_button);
        passButton.setOnClickListener(clickListener);
        passButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                Toast.makeText(activity, R.string.pass_button_text, Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        View failButton = activity.findViewById(R.id.fail_button);
        failButton.setOnClickListener(clickListener);
        failButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                Toast.makeText(activity, R.string.fail_button_text, Toast.LENGTH_SHORT).show();
                return true;
            }
        });
    } // class PassFailButtons.<T extends android.app.Activity & PassFailActivity>

    protected static void setInfo(final android.app.Activity activity, final int titleId,
            final int messageId, final int viewId) {
        // Show the middle "info" button and make it show the info dialog when clicked.
        View infoButton = activity.findViewById(R.id.info_button);
        infoButton.setVisibility(View.VISIBLE);
        infoButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                showInfoDialog(activity, titleId, messageId, viewId);
            }
        });
        infoButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                Toast.makeText(activity, R.string.info_button_text, Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        // Show the info dialog if the user has never seen it before.
        if (!hasSeenInfoDialog(activity)) {
            showInfoDialog(activity, titleId, messageId, viewId);
        }
    }

    protected static boolean hasSeenInfoDialog(android.app.Activity activity) {
        ContentResolver resolver = activity.getContentResolver();
        Cursor cursor = null;
        try {
            cursor = resolver.query(TestResultsProvider.getTestNameUri(activity),
                    new String[] {TestResultsProvider.COLUMN_TEST_INFO_SEEN}, null, null, null);
            return cursor.moveToFirst() && cursor.getInt(0) > 0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    protected static void showInfoDialog(final android.app.Activity activity, int titleId,
            int messageId, int viewId) {
        Bundle args = new Bundle();
        args.putInt(INFO_DIALOG_TITLE_ID, titleId);
        args.putInt(INFO_DIALOG_MESSAGE_ID, messageId);
        args.putInt(INFO_DIALOG_VIEW_ID, viewId);
        activity.showDialog(INFO_DIALOG_ID, args);
    }

    protected static void showReportLogWarningDialog(final android.app.Activity activity) {
        Bundle args = new Bundle();
        args.putInt(INFO_DIALOG_TITLE_ID, R.string.reportlog_warning_title);
        args.putInt(INFO_DIALOG_MESSAGE_ID, R.string.reportlog_warning_body);
        args.putInt(INFO_DIALOG_VIEW_ID, -1);
        activity.showDialog(REPORTLOG_DIALOG_ID, args);
    }

    protected static Dialog createDialog(final android.app.Activity activity, int id, Bundle args) {
        switch (id) {
            case INFO_DIALOG_ID:
            case REPORTLOG_DIALOG_ID:
                return createInfoDialog(activity, id, args);
            default:
                throw new IllegalArgumentException("Bad dialog id: " + id);
        }
    }

    protected static Dialog createInfoDialog(final android.app.Activity activity, int id,
            Bundle args) {
        int viewId = args.getInt(INFO_DIALOG_VIEW_ID);
        int titleId = args.getInt(INFO_DIALOG_TITLE_ID);
        int messageId = args.getInt(INFO_DIALOG_MESSAGE_ID);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity).setIcon(
                android.R.drawable.ic_dialog_info).setTitle(titleId);
        if (viewId > 0) {
            LayoutInflater inflater = (LayoutInflater) activity
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            builder.setView(inflater.inflate(viewId, null));
        } else {
            builder.setMessage(messageId);
        }
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                markSeenInfoDialog(activity);
            }
        }).setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                markSeenInfoDialog(activity);
            }
        });
        return builder.create();
    }

    protected static void markSeenInfoDialog(android.app.Activity activity) {
        ContentResolver resolver = activity.getContentResolver();
        ContentValues values = new ContentValues(2);
        String activityName = setTestNameSuffix(sCurrentDisplayMode, activity.getClass().getName());
        values.put(TestResultsProvider.COLUMN_TEST_NAME, activityName);
        values.put(TestResultsProvider.COLUMN_TEST_INFO_SEEN, 1);
        int numUpdated = resolver.update(
                TestResultsProvider.getTestNameUri(activity), values, null, null);
        if (numUpdated == 0) {
            resolver.insert(TestResultsProvider.getResultContentUri(activity), values);
        }
    }

    /** Set the test result corresponding to the button clicked and finish the activity. */
    protected static void setTestResultAndFinish(android.app.Activity activity, String testId,
            String testDetails, ReportLog reportLog, TestResultHistoryCollection historyCollection,
            View target) {

        boolean passed;
        if (target.getId() == R.id.pass_button) {
            passed = true;
        } else if (target.getId() == R.id.fail_button) {
            passed = false;
        } else {
            throw new IllegalArgumentException("Unknown id: " + target.getId());
        }

        // Let test classes record their CTSVerifierReportLogs
        ((PassFailActivity) activity).recordTestResults();

        setTestResultAndFinishHelper(activity, testId, testDetails, passed, reportLog, historyCollection);
    }

    /** Set the test result and finish the activity. */
    protected static void setTestResultAndFinishHelper(android.app.Activity activity, String testId,
            String testDetails, boolean passed, ReportLog reportLog,
            TestResultHistoryCollection historyCollection) {
        if (passed) {
            TestResult.setPassedResult(activity, testId, testDetails, reportLog, historyCollection);
        } else {
            TestResult.setFailedResult(activity, testId, testDetails, reportLog, historyCollection);
        }

        // We store results here straight into the content provider so it can be fetched by the
        // CTSInteractive host
        TestResultsProvider.setTestResult(
                activity, testId, passed ? 1 : 2, testDetails, reportLog, historyCollection,
                null);

        activity.finish();
    }

    protected static ImageButton getPassButtonView(android.app.Activity activity) {
        return (ImageButton) activity.findViewById(R.id.pass_button);
    }

} // class PassFailButtons
