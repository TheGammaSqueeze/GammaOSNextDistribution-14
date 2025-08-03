/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tradefed.testtype.rust;

import com.android.ddmlib.MultiLineReceiver;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interprets the output of tests run with Rust's unittest framework and translates it into calls on
 * a series of {@link ITestInvocationListener}s.
 *
 * <p>Looks for the following output from Rust tests:
 *
 * <p><code>
 * running 10 tests
 * test LexError ... ok <0.001s>
 * test idents ... FAILED <0.000s>
 * test make_sure_no_proc_macro ... ignored
 * test test_scalar_div_rem_zero ... ignored, Android sometimes uses panic_abort
 * ...
 * ---- tests::idents stdout ----
 * thread 'main' panicked at 'assertion failed: `(left == right)`
 * ...
 *
 * test result: ok. 10 passed; 1 failed; 1 ignored; 0 measured; 0 filtered out
 * </code> @See <a href="Rust test output
 * examples">https://doc.rust-lang.org/book/ch11-00-testing.html</a>
 */
public class RustTestResultParser extends MultiLineReceiver {

    private String mCurrentTestFile;
    private String mCurrentTestName;
    private String mCurrentTestStatus;
    private String mCurrentTestTime;
    private StringBuilder mCurrentTestTrace;
    private Matcher mCurrentMatcher;

    // General state
    private Collection<ITestInvocationListener> mListeners = new ArrayList<>();
    private Map<TestDescription, Pair<String, String>> mTestResultCache;
    private Map<TestDescription, String> mTestTraceCache;

    /** True if we have seen at least one test start. */
    private boolean mSeenOneTestRunStart = false;
    /**
     * Track all the log lines before the test is started, as it is helpful on an early failure to
     * report those logs.
     */
    private List<String> mTrackLogsBeforeRunStart = new ArrayList<>();

    private static int mNumTestsStarted = 0;
    private static int mNumTestsEnded = 0;

    private boolean mDoneCalled = false;

    // Use a special entry to mark skipped test in mTestResultCache
    static final String SKIPPED_ENTRY = "Skipped";
    // Failed but without stacktrace tests in mTestResultCache
    static final String FAILED_ENTRY = "FailedNoStack";

    static final Pattern COMPLETE_PATTERN =
            Pattern.compile("test result: (.*) (\\d+) passed; (\\d+) failed; (\\d+) ignored;.*");

    static final Pattern RUST_ONE_LINE_RESULT =
            Pattern.compile(
                    "test (\\S*) (?:- should panic )? *\\.\\.\\. (\\S*)(?: <(\\d+\\.\\d+)s>)?");
    static final Pattern RUST_IGNORE_RESULT =
            Pattern.compile("test (\\S*) +(?:- should panic )?\\.\\.\\. (ignored).*");
    static final Pattern RUNNING_PATTERN = Pattern.compile("running (.*) test[s]?");

    static final Pattern TEST_FAIL_PATTERN = Pattern.compile("---- (\\S*) stdout ----");

    static final Pattern FAILURES_PATTERN = Pattern.compile("failures:");

    /**
     * Create a new {@link RustTestResultParser} that reports to the given {@link
     * ITestInvocationListener}.
     *
     * @param listener the test invocation listener
     * @param runName the test name
     */
    public RustTestResultParser(ITestInvocationListener listener, String runName) {
        this(Arrays.asList(listener), runName);
    }

    /**
     * Create a new {@link RustTestResultParser} that reports to the given {@link
     * ITestInvocationListener}s.
     *
     * @param listeners the test invocation listeners
     * @param runName the test name
     */
    public RustTestResultParser(Collection<ITestInvocationListener> listeners, String runName) {
        mListeners.addAll(listeners);
        mCurrentTestFile = runName;
        mCurrentTestTrace = null;
        mTestResultCache = new HashMap<>();
        mTestTraceCache = new HashMap<>();
    }

    /** Process Rust unittest output. */
    @Override
    public void processNewLines(String[] lines) {
        if (!mSeenOneTestRunStart) {
            mTrackLogsBeforeRunStart.addAll(Arrays.asList(lines));
        }

        for (String line : lines) {
            if (lineMatchesPattern(line, RUST_IGNORE_RESULT)) {
                mCurrentTestName = mCurrentMatcher.group(1);
                mCurrentTestStatus = mCurrentMatcher.group(2);
                mCurrentTestTime = null;
                mNumTestsEnded++;
                reportTestResult();
            } else if (lineMatchesPattern(line, RUST_ONE_LINE_RESULT)) {
                mCurrentTestName = mCurrentMatcher.group(1);
                mCurrentTestStatus = mCurrentMatcher.group(2);
                mCurrentTestTime = mCurrentMatcher.group(3);
                mNumTestsEnded++;
                reportTestResult();
            } else if (lineMatchesPattern(line, RUNNING_PATTERN)) {
                mSeenOneTestRunStart = true;
                try {
                    mNumTestsStarted = Integer.parseInt(mCurrentMatcher.group(1));
                } catch (NumberFormatException e) {
                    CLog.e(
                            "Unable to determine number of tests expected, received: %s",
                            mCurrentMatcher.group(1));
                }
                mNumTestsEnded = 0;
                mTrackLogsBeforeRunStart.clear();
            } else if (lineMatchesPattern(line, TEST_FAIL_PATTERN)) {
                if (mCurrentTestTrace != null) {
                    reportTestTrace();
                }
                mCurrentTestName = mCurrentMatcher.group(1);
                mCurrentTestTrace = new StringBuilder();
            } else if (lineMatchesPattern(line, FAILURES_PATTERN)) {
                if (mCurrentTestTrace != null) {
                    reportTestTrace();
                    mCurrentTestTrace = null;
                }
            } else if (mCurrentTestTrace != null) {
                mCurrentTestTrace.append(line).append('\n');
            }
        }
    }

    /** Process the run summary line and return total test count. */
    int processRunSummary(String line) {
        try {
            if (!lineMatchesPattern(line, COMPLETE_PATTERN)) {
                throw new RuntimeException("Failed to parse summary line: " + line);
            }
            int passed = Integer.parseInt(mCurrentMatcher.group(2));
            int failed = Integer.parseInt(mCurrentMatcher.group(3));
            int ignored = Integer.parseInt(mCurrentMatcher.group(4));
            return passed + failed + ignored;
        } catch (NumberFormatException e) {
            // this should never happen, since regular expression matches on digits
            throw new RuntimeException("Failed to parse number in " + line);
        }
    }

    /** Check if the given line matches the given pattern and caches the matcher object */
    private boolean lineMatchesPattern(String line, Pattern p) {
        mCurrentMatcher = p.matcher(line);
        return mCurrentMatcher.matches();
    }

    /** Send recorded test results to all listeners. */
    @Override
    public void done() {
        if (mDoneCalled) {
            return;
        }
        mDoneCalled = true;
        for (ITestInvocationListener listener : mListeners) {
            for (Entry<TestDescription, Pair<String, String>> test : mTestResultCache.entrySet()) {
                String status = test.getValue().first;
                Double time = parseTime(test.getValue().second);
                long startTime = System.currentTimeMillis();
                if (time == null) {
                    listener.testStarted(test.getKey());
                } else {
                    listener.testStarted(test.getKey(), startTime);
                }
                if (SKIPPED_ENTRY.equals(status)) {
                    listener.testIgnored(test.getKey());
                } else if (FAILED_ENTRY.equals(status)) {
                    listener.testFailed(
                            test.getKey(), mTestTraceCache.getOrDefault(test.getKey(), ""));
                } else if (status != null) {
                    // Report all unexpected test result as failed tests,
                    // so they are not missed.
                    listener.testFailed(test.getKey(), status);
                }
                if (time == null) {
                    listener.testEnded(test.getKey(), new HashMap<String, Metric>());
                } else {
                    listener.testEnded(
                            test.getKey(),
                            startTime + (long) (time * 1000),
                            new HashMap<String, Metric>());
                }
            }
            // If we have not seen any tests start, report a failure.
            // If this happens, there are presumably no test results,
            // so this must be outside the previous loop.
            if (!mSeenOneTestRunStart) {
                listener.testRunFailed(
                        String.format(
                                "test did not report any run:\n%s",
                                String.join("\n", mTrackLogsBeforeRunStart)));
            } else if (mNumTestsStarted > mNumTestsEnded) {
                listener.testRunFailed(
                        String.format(
                                "Test run incomplete. Started %d tests, finished %d",
                                mNumTestsStarted, mNumTestsEnded));
            }
        }
    }

    /** Record a test case. */
    private void reportTestResult() {
        TestDescription testId = new TestDescription(mCurrentTestFile, mCurrentTestName);
        String status;
        if (mCurrentTestStatus.equals("ok")) {
            status = null;
        } else if (mCurrentTestStatus.startsWith("ignored")) {
            status = SKIPPED_ENTRY;
        } else if (mCurrentTestStatus.equals("FAILED")) {
            // Rust tests report "FAILED" without stack trace.
            status = FAILED_ENTRY;
        } else {
            status = mCurrentTestStatus;
        }
        mTestResultCache.put(testId, new Pair<>(status, mCurrentTestTime));
    }

    private void reportTestTrace() {
        // Remove all trailing newlines.
        int lastNewline = mCurrentTestTrace.length();
        while (lastNewline > 0 && mCurrentTestTrace.charAt(lastNewline - 1) == '\n') {
            lastNewline--;
        }
        mCurrentTestTrace.delete(lastNewline, mCurrentTestTrace.length());
        // Add the trace.
        TestDescription testId = new TestDescription(mCurrentTestFile, mCurrentTestName);
        mTestTraceCache.put(testId, mCurrentTestTrace.toString());
    }

    /** Parse the given string into a number */
    private Double parseTime(String time) {
        // Passing and failing tests have times, but ignored tests do not.
        if (time == null) {
            return null;
        }
        try {
            return Double.valueOf(time);
        } catch (NumberFormatException e) {
            CLog.e("Test run time value is invalid, received: %s", time);
            return null;
        }
    }

    @Override
    public boolean isCancelled() {
        return false;
    }
}
