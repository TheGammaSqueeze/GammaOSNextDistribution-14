/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tradefed.result;

import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.SubprocessEventHelper.BaseTestEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.FailedTestEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.InvocationFailedEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.InvocationStartedEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.LogAssociationEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.TestEndedEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.TestModuleStartedEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.TestRunEndedEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.TestRunFailedEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.TestRunStartedEventInfo;
import com.android.tradefed.util.SubprocessEventHelper.TestStartedEventInfo;
import com.android.tradefed.util.SubprocessTestResultsParser;

import org.json.JSONObject;

import java.util.Map;

import sun.misc.Signal;
import sun.misc.SignalHandler;

/**
 * A frozen implementation of the subprocess results reporter which should remain compatible with
 * earlier versions of TF/CTS (e.g. 8+), despite changes in its superclass.
 *
 * <p>This reporter can be dynamically injected to enable subprocess reporting capability in old
 * TF-based test suites.
 */
public final class LegacySubprocessResultsReporter extends SubprocessResultsReporter {

    private static SignalHandler handler =
            new SignalHandler() {
                @Override
                public void handle(Signal sig) {
                    CLog.logAndDisplay(
                            LogLevel.ERROR,
                            String.format("Received signal %s. Shutting down...", sig.getName()));
                    GlobalConfiguration.getInstance().getCommandScheduler().shutdownHard();
                }
            };

    public LegacySubprocessResultsReporter() {
        this(true);
    }

    protected LegacySubprocessResultsReporter(boolean installHandler) {
        if (installHandler) {
            // Install a signal handler to properly stop running invocations. This allows old
            // TF-based
            // test suites to generate test result files when interrupted.
            // FIXME: Don't install a new handler if CommandRunner already installed one.
            Signal.handle(new Signal("TERM"), handler);
        }
    }

    /* Legacy method compatible with TF/CTS 8+. */
    public void testAssumptionFailure(TestIdentifier testId, String trace) {
        FailedTestEventInfo info =
                new FailedTestEventInfo(testId.getClassName(), testId.getTestName(), trace);
        printEvent(SubprocessTestResultsParser.StatusKeys.TEST_ASSUMPTION_FAILURE, info);
    }

    /* Legacy method compatible with TF/CTS 8+. */
    public void testEnded(TestIdentifier testId, Map<String, String> metrics) {
        testEnded(testId, System.currentTimeMillis(), metrics);
    }

    /* Legacy method compatible with TF/CTS 8+. */
    public void testEnded(TestIdentifier testId, long endTime, Map<String, String> metrics) {
        TestEndedEventInfo info =
                new TestEndedEventInfo(
                        testId.getClassName(), testId.getTestName(), endTime, metrics);
        printEvent(SubprocessTestResultsParser.StatusKeys.TEST_ENDED, info);
    }

    /* Legacy method compatible with TF/CTS 8+. */
    public void testFailed(TestIdentifier testId, String reason) {
        FailedTestEventInfo info =
                new FailedTestEventInfo(testId.getClassName(), testId.getTestName(), reason);
        printEvent(SubprocessTestResultsParser.StatusKeys.TEST_FAILED, info);
    }

    /* Legacy method compatible with TF/CTS 8+. */
    public void testIgnored(TestIdentifier testId) {
        BaseTestEventInfo info = new BaseTestEventInfo(testId.getClassName(), testId.getTestName());
        printEvent(SubprocessTestResultsParser.StatusKeys.TEST_IGNORED, info);
    }

    /* Legacy method compatible with TF/CTS 8+. */
    public void testStarted(TestIdentifier testId) {
        testStarted(testId, System.currentTimeMillis());
    }

    /* Legacy method compatible with TF/CTS 8+. */
    public void testStarted(TestIdentifier testId, long startTime) {
        TestStartedEventInfo info =
                new TestStartedEventInfo(testId.getClassName(), testId.getTestName(), startTime);
        printEvent(SubprocessTestResultsParser.StatusKeys.TEST_STARTED, info);
    }

    /* Legacy method compatible with TF/CTS 8+. */
    public void invocationStarted(IBuildInfo buildInfo) {
        InvocationStartedEventInfo info =
                new InvocationStartedEventInfo(buildInfo.getTestTag(), System.currentTimeMillis());
        printEvent(SubprocessTestResultsParser.StatusKeys.INVOCATION_STARTED, info);
    }

    /* Legacy method compatible with TF/CTS 8+. */
    @Override
    public void invocationFailed(Throwable cause) {
        InvocationFailedEventInfo info = new InvocationFailedEventInfo(cause);
        printEvent(SubprocessTestResultsParser.StatusKeys.INVOCATION_FAILED, info);
    }

    /* Legacy method compatible with TF/CTS 8. */
    @Override
    public void invocationEnded(long elapsedTime) {
        // ignore
    }

    /* Legacy method compatible with TF/CTS 8+. */
    @Override
    public void testRunFailed(String reason) {
        TestRunFailedEventInfo info = new TestRunFailedEventInfo(reason);
        printEvent(SubprocessTestResultsParser.StatusKeys.TEST_RUN_FAILED, info);
    }

    /* Legacy method compatible with TF/CTS 8+. */
    @Override
    public void testRunStarted(String runName, int testCount) {
        TestRunStartedEventInfo info = new TestRunStartedEventInfo(runName, testCount);
        printEvent(SubprocessTestResultsParser.StatusKeys.TEST_RUN_STARTED, info);
    }

    /* Legacy method compatible with TF/CTS 8+. */
    @Override
    public void testRunEnded(long time, Map<String, String> runMetrics) {
        TestRunEndedEventInfo info = new TestRunEndedEventInfo(time, runMetrics);
        printEvent(SubprocessTestResultsParser.StatusKeys.TEST_RUN_ENDED, info);
    }

    /* Legacy method compatible with TF/CTS 8.1+ (not called in 8). */
    @Override
    public void testModuleStarted(IInvocationContext moduleContext) {
        TestModuleStartedEventInfo info = new TestModuleStartedEventInfo(moduleContext);
        printEvent(SubprocessTestResultsParser.StatusKeys.TEST_MODULE_STARTED, info);
    }

    /* Legacy method compatible with TF/CTS 8.1+ (not called in 8). */
    @Override
    public void testModuleEnded() {
        printEvent(SubprocessTestResultsParser.StatusKeys.TEST_MODULE_ENDED, new JSONObject());
    }

    /* Legacy method compatible with TF/CTS 8.1+ (not called in 8). */
    @Override
    public void testLogSaved(
            String dataName, LogDataType dataType, InputStreamSource dataStream, LogFile logFile) {
        // ignore
    }

    /* Legacy method compatible with TF/CTS 8.1+ (not called in 8). */
    @Override
    public void logAssociation(String dataName, LogFile logFile) {
        LogAssociationEventInfo info = new LogAssociationEventInfo(dataName, logFile);
        printEvent(SubprocessTestResultsParser.StatusKeys.LOG_ASSOCIATION, info);
    }

    /* Legacy method compatible with TF/CTS 8.1+ (not called in 8). */
    @Override
    public void setLogSaver(ILogSaver logSaver) {
        // ignore
    }
}
