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
package com.android.tradefed.testtype.rust;

import com.android.ddmlib.MultiLineReceiver;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interprets the output of tests run with Criterion benchmarking framework and translates it into
 * calls on a series of {@link ITestInvocationListener}s.
 *
 * <p>Looks for the following output from Rust criterion bench:
 *
 * <p><code>
 * Benchmarking Fibonacci/Recursive
 * Benchmarking Fibonacci/Recursive: Warming up for 3.0000 s
 * Benchmarking Fibonacci/Recursive: Collecting 100 samples in estimated 5.0785 s (278k iterations)
 * Benchmarking Fibonacci/Recursive: Analyzing
 * Fibonacci/Recursive     time:   [18.272 us 18.276 us 18.280 us]
 *                         change: [-0.0788% -0.0361% +0.0025%] (p = 0.07 &gt; 0.05)
 *                         No change in performance detected.
 * Found 2 outliers among 100 measurements (2.00%)
 *   1 (1.00%) low severe
 *   1 (1.00%) low mild
 * ...
 * </code> @See <a href="Criterion Command-Line
 * output">https://bheisler.github.io/criterion.rs/book/user_guide/command_line_output.html</a>
 */
public class RustBenchmarkResultParser extends MultiLineReceiver {

    private String mCurrentTestFile;
    private String mCurrentTestName;
    private String mCurrentTestStatus;
    private Matcher mCurrentMatcher;

    /** List of all the tests extracted from the output. */
    private TestDescription mLastTestId;

    /** Flipped to true once the first test reports start. */
    private boolean mAnyTestSeen = false;

    /** For some reason on the device done is called twice. This flag guards agains it. */
    private boolean mDoneDone = false;

    // General state
    private Collection<ITestInvocationListener> mListeners = new ArrayList<>();

    /**
     * Track all the log lines before the test is started, as it is helpful on an early failure to
     * report those logs. After first test started this track log lines since the last start to
     * report failure in the recent test.
     */
    private List<String> mTrackLogsSinceLastStart = new ArrayList<>();

    /** Line example: 'Benchmarking Fibonacci/Recursive' */
    static final Pattern CRITERION_START_PATTERN = Pattern.compile("Benchmarking ([^\\s]*)");

    /** Line example: 'Fibonacci/Recursive time: [34.400 us 34.408 us 34.417 us]' */
    static final Pattern CRITERION_END_PATTERN = Pattern.compile(".*time:.*");

    /**
     * Create a new {@link RustBenchmarkResultParser} that reports to the given {@link
     * ITestInvocationListener}.
     *
     * @param listener the test invocation listener
     * @param runName the test name
     */
    public RustBenchmarkResultParser(ITestInvocationListener listener, String runName) {
        this(Arrays.asList(listener), runName);
    }

    /**
     * Create a new {@link RustBenchmarkResultParser} that reports to the given {@link
     * ITestInvocationListener}s.
     *
     * @param listeners the test invocation listeners
     * @param runName the test name
     */
    public RustBenchmarkResultParser(
            Collection<ITestInvocationListener> listeners, String runName) {
        mListeners.addAll(listeners);
        mCurrentTestFile = runName;
    }

    /** Process Rust benchmark output. */
    @Override
    public void processNewLines(String[] lines) {
        for (String line : lines) {
            mTrackLogsSinceLastStart.add(line);
            // Note that this is benchmark parser, that's why there are only start and end patterns
            // matchers. Benchmarks aren't supposed to fail unless when they quit unexpectedly. This
            // case is handled in the done() method.
            Matcher startMatcher = CRITERION_START_PATTERN.matcher(line);
            Matcher endMatcher = CRITERION_END_PATTERN.matcher(line);
            if (startMatcher.matches()) {
                // If there is next start before we've seen an end, report a failure.
                if (mLastTestId != null) {
                    for (ITestInvocationListener listener : mListeners) {
                        listener.testFailed(
                                mLastTestId, String.join("\n", mTrackLogsSinceLastStart));
                        listener.testEnded(mLastTestId, new HashMap<String, Metric>());
                    }
                    mLastTestId = null;
                }
                mLastTestId = new TestDescription(mCurrentTestFile, startMatcher.group(1));
                for (ITestInvocationListener listener : mListeners) {
                    listener.testStarted(mLastTestId);
                }
                mTrackLogsSinceLastStart.clear();
                mAnyTestSeen = true;
            } else if (endMatcher.matches()) {
                // This should never fail.
                if (mLastTestId != null) {
                    for (ITestInvocationListener listener : mListeners) {
                        // TODO(qtr): Report metrics.
                        listener.testEnded(mLastTestId, new HashMap<String, Metric>());
                    }
                    mLastTestId = null;
                } else {
                    CLog.e("End of test matched before its start. Is it a bug?");
                }
            }
        }
    }

    /** Send recorded test results to all listeners. */
    @Override
    public void done() {
        // It seems that on the device done can be called twice...
        if (mDoneDone) {
            return;
        }
        mDoneDone = true;
        // If we have not seen any tests start, report a failure.
        if (!mAnyTestSeen) {
            for (ITestInvocationListener listener : mListeners) {
                listener.testRunFailed(
                        String.format(
                                "test did not report any run:\n%s",
                                String.join("\n", mTrackLogsSinceLastStart)));
            }
        }
        // Fail the test if we have seen a start but haven't seen an end.
        if (mLastTestId != null) {
            for (ITestInvocationListener listener : mListeners) {
                listener.testFailed(mLastTestId, String.join("\n", mTrackLogsSinceLastStart));
                listener.testEnded(mLastTestId, new HashMap<String, Metric>());
                listener.testRunFailed(mCurrentTestFile + " execution failed.");
            }
        }
    }

    @Override
    public boolean isCancelled() {
        return false;
    }
}
