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
package com.android.tradefed.result.proto;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationGroupMetricKey;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.invoker.logger.TfObjectTracker;
import com.android.tradefed.invoker.proto.InvocationContext.Context;
import com.android.tradefed.invoker.tracing.ActiveTrace;
import com.android.tradefed.invoker.tracing.TracingLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ActionInProgress;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.error.ErrorIdentifier;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.result.proto.LogFileProto.LogFileInfo;
import com.android.tradefed.result.proto.TestRecordProto.ChildReference;
import com.android.tradefed.result.proto.TestRecordProto.DebugInfo;
import com.android.tradefed.result.proto.TestRecordProto.DebugInfoContext;
import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;
import com.android.tradefed.result.proto.TestRecordProto.SkipReason;
import com.android.tradefed.result.proto.TestRecordProto.TestRecord;
import com.android.tradefed.testtype.suite.ModuleDefinition;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.SerializationUtil;
import com.android.tradefed.util.proto.TestRecordProtoUtil;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nonnull;

/** Parser for the Tradefed results proto format. */
public class ProtoResultParser {

    private ITestInvocationListener mListener;
    private String mCurrentRunName = null;
    private TestDescription mCurrentTestCase = null;
    /**
     * We don't always want to report the invocation level events again. If we are within an
     * invocation scope we should not report it again.
     */
    private boolean mReportInvocation = false;
    /** In some cases we do not need to forward the logs. */
    private boolean mReportLogs = true;
    /** Prefix that will be added to the files logged through the parser. */
    private String mFilePrefix;
    /** The context from the invocation in progress, not the proto one. */
    private IInvocationContext mMainContext;

    private boolean mQuietParsing = true;
    private boolean mSkipParsingAccounting = false;

    private boolean mInvocationStarted = false;
    /** Track whether or not invocationFailed was called. */
    private boolean mInvocationFailed = false;

    private boolean mInvocationEnded = false;
    private boolean mFirstModule = true;
    /** Track the name of the module in progress. */
    private String mModuleInProgress = null;

    private IInvocationContext mModuleContext = null;

    private boolean mMergeInvocationContext = true;

    /** Ctor. */
    public ProtoResultParser(
            ITestInvocationListener listener,
            IInvocationContext context,
            boolean reportInvocation) {
        this(listener, context, reportInvocation, "subprocess-");
    }

    /** Ctor. */
    public ProtoResultParser(
            ITestInvocationListener listener,
            IInvocationContext context,
            boolean reportInvocation,
            String prefixForFile) {
        mListener = listener;
        mMainContext = context;
        mReportInvocation = reportInvocation;
        mFilePrefix = prefixForFile;
    }

    /** Enumeration representing the current level of the proto being processed. */
    public enum TestLevel {
        INVOCATION,
        MODULE,
        TEST_RUN,
        TEST_CASE
    }

    /** Sets whether or not to print when events are received. */
    public void setQuiet(boolean quiet) {
        mQuietParsing = quiet;
    }

    public void setSkipParsingAccounting(boolean skip) {
        mSkipParsingAccounting = skip;
    }

    /** Sets whether or not we should report the logs. */
    public void setReportLogs(boolean reportLogs) {
        mReportLogs = reportLogs;
    }

    /**
     * Enable or disable merging the serialized invocation context with the main context that this
     * object is initialized with.
     *
     * <p>Note that disabling invocation-level reporting via the {@code reportInvocation}
     * constructor parameter still merges context information and requires explicitly using this
     * method to disable the behavior.
     *
     * <p>TODO(b/288001953): Revisit the proper API for accomplishing this.
     *
     * @return the previous state
     * @see #ProtoResultParser
     */
    public boolean setMergeInvocationContext(boolean enabled) {
        boolean previousContext = mMergeInvocationContext;
        mMergeInvocationContext = enabled;
        return previousContext;
    }

    /**
     * Main entry function that takes the finalized completed proto and replay its results.
     *
     * @param finalProto The final {@link TestRecord} to be parsed.
     */
    public void processFinalizedProto(TestRecord finalProto) {
        if (!finalProto.getParentTestRecordId().isEmpty()) {
            throw new IllegalArgumentException("processFinalizedProto only expect a root proto.");
        }

        // Invocation Start
        handleInvocationStart(finalProto);

        evalChildrenProto(finalProto.getChildrenList(), false);
        // Invocation End
        handleInvocationEnded(finalProto);
    }

    /**
     * Main entry function where each proto is presented to get parsed into Tradefed events.
     *
     * @param currentProto The current {@link TestRecord} to be parsed.
     * @return True if the proto processed was a module.
     */
    public TestLevel processNewProto(TestRecord currentProto) {
        // Handle initial root proto
        if (currentProto.getParentTestRecordId().isEmpty()) {
            handleRootProto(currentProto);
            return TestLevel.INVOCATION;
        } else if (currentProto.hasDescription()) {
            // If it has a Any Description with Context then it's a module
            handleModuleProto(currentProto);
            return TestLevel.MODULE;
        } else if (mCurrentRunName == null
                || currentProto.getTestRecordId().equals(mCurrentRunName)) {
            // Need to track the parent test run id to make sure we need testRunEnd or testRunFail
            handleTestRun(currentProto);
            return TestLevel.TEST_RUN;
        } else {
            // Test cases handling
            handleTestCase(currentProto);
            return TestLevel.TEST_CASE;
        }
    }

    /**
     * In case of parsing proto files directly, handle direct parsing of them as a sequence.
     * Associated with {@link FileProtoResultReporter} when reporting a sequence of files.
     *
     * @param protoFile The proto file to be parsed.
     * @throws IOException
     */
    public void processFileProto(File protoFile) throws IOException {
        TestRecord record = null;
        try {
            record = TestRecordProtoUtil.readFromFile(protoFile);
        } catch (InvalidProtocolBufferException e) {
            // Log the proto that failed to parse
            try (FileInputStreamSource protoFail = new FileInputStreamSource(protoFile, true)) {
                mListener.testLog("failed-result-protobuf", LogDataType.PB, protoFail);
            }
            throw e;
        }
        if (!mInvocationStarted) {
            handleInvocationStart(record);
            mInvocationStarted = true;
        } else if (record.getParentTestRecordId().isEmpty()) {
            handleInvocationEnded(record);
        } else {
            evalProto(record, false);
        }
    }

    /** Returns whether or not the parsing reached an invocation ended. */
    public boolean invocationEndedReached() {
        return mInvocationEnded;
    }

    /** Returns the id of the module in progress. Returns null if none in progress. */
    public String getModuleInProgress() {
        return mModuleInProgress;
    }

    /** Returns whether or not the invocation failed has been reported. */
    public boolean hasInvocationFailed() {
        return mInvocationFailed;
    }

    /**
     * If needed to ensure consistent reporting, complete the events of the module, run and methods.
     */
    public void completeModuleEvents() {
        if (mCurrentRunName == null && getModuleInProgress() != null) {
            mListener.testRunStarted(getModuleInProgress(), 0);
        }
        if (mCurrentTestCase != null) {
            FailureDescription failure =
                    FailureDescription.create(
                            "Run was interrupted after starting, results are incomplete.");
            mListener.testFailed(mCurrentTestCase, failure);
            mListener.testEnded(mCurrentTestCase, new HashMap<String, Metric>());
        }
        if (getModuleInProgress() != null || mCurrentRunName != null) {
            FailureDescription failure =
                    FailureDescription.create(
                            "Module was interrupted after starting, results are incomplete.",
                            FailureStatus.INFRA_FAILURE);
            mListener.testRunFailed(failure);
            mListener.testRunEnded(0L, new HashMap<String, Metric>());
            mCurrentRunName = null;
        }
        if (getModuleInProgress() != null) {
            mListener.testModuleEnded();
        }
    }

    private void evalChildrenProto(List<ChildReference> children, boolean isInRun) {
        for (ChildReference child : children) {
            TestRecord childProto = child.getInlineTestRecord();
            evalProto(childProto, isInRun);
        }
    }

    private void evalProto(TestRecord childProto, boolean isInRun) {
        if (isInRun) {
            // test case
            String[] info = childProto.getTestRecordId().split("#");
            TestDescription description = new TestDescription(info[0], info[1]);
            mListener.testStarted(description, timeStampToMillis(childProto.getStartTime()));
            handleTestCaseEnd(description, childProto);
        } else {
            boolean inRun = false;
            if (childProto.hasDescription()) {
                // Module start
                handleModuleStart(childProto);
            } else {
                // run start
                handleTestRunStart(childProto);
                inRun = true;
            }
            evalChildrenProto(childProto.getChildrenList(), inRun);
            if (childProto.hasDescription()) {
                // Module end
                handleModuleProto(childProto);
            } else {
                // run end
                handleTestRunEnd(childProto);
            }
        }
    }

    /** Handles the root of the invocation: They have no parent record id. */
    private void handleRootProto(TestRecord rootProto) {
        if (rootProto.hasEndTime()) {
            handleInvocationEnded(rootProto);
        } else {
            handleInvocationStart(rootProto);
        }
    }

    private void handleInvocationStart(TestRecord startInvocationProto) {
        // invocation starting
        Any anyDescription = startInvocationProto.getDescription();
        if (!anyDescription.is(Context.class)) {
            throw new RuntimeException("Expected Any description of type Context");
        }
        IInvocationContext receivedContext;
        try {
            receivedContext = InvocationContext.fromProto(anyDescription.unpack(Context.class));
            mergeInvocationContext(mMainContext, receivedContext);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }

        log("Invocation started proto");
        if (!mReportInvocation) {
            CLog.d("Skipping invocation start reporting.");
            return;
        }
        // Only report invocation start if enabled
        mListener.invocationStarted(receivedContext);
    }

    private void handleInvocationEnded(TestRecord endInvocationProto) {
        // Still report the logs even if not reporting the invocation level.
        handleLogs(endInvocationProto);

        if (mInvocationEnded) {
            CLog.d("Re-entry in invocationEnded, most likely for subprocess final logs.");
            return;
        }

        // Get final context in case it changed.
        Any anyDescription = endInvocationProto.getDescription();
        if (!anyDescription.is(Context.class)) {
            throw new RuntimeException(
                    String.format(
                            "Expected Any description of type Context, was %s", anyDescription));
        }
        try {
            IInvocationContext context =
                    InvocationContext.fromProto(anyDescription.unpack(Context.class));
            mergeInvocationContext(mMainContext, context);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }

        if (endInvocationProto.hasDebugInfo()) {
            DebugInfo debugInfo = endInvocationProto.getDebugInfo();
            FailureDescription failure = FailureDescription.create(debugInfo.getErrorMessage());
            if (!TestRecordProto.FailureStatus.UNSET.equals(
                    endInvocationProto.getDebugInfo().getFailureStatus())) {
                failure.setFailureStatus(debugInfo.getFailureStatus());
            }
            parseDebugInfoContext(endInvocationProto.getDebugInfo(), failure);
            if (endInvocationProto.getDebugInfo().hasDebugInfoContext()) {
                String errorType =
                        endInvocationProto.getDebugInfo().getDebugInfoContext().getErrorType();
                if (!Strings.isNullOrEmpty(errorType)) {
                    try {
                        Throwable invocationError =
                                (Throwable) SerializationUtil.deserialize(errorType);
                        failure.setCause(invocationError);
                        if (invocationError instanceof OutOfMemoryError) {
                            failure.setErrorIdentifier(InfraErrorIdentifier.OUT_OF_MEMORY_ERROR);
                        }
                    } catch (IOException e) {
                        CLog.e("Failed to deserialize the invocation exception:");
                        CLog.e(e);
                        failure.setCause(new RuntimeException(failure.getErrorMessage()));
                    }
                }
            }
            CLog.d("Invocation failed with: %s", failure);
            mListener.invocationFailed(failure);
            mInvocationFailed = true;
        }
        if (endInvocationProto.hasSkipReason()) {
            SkipReason reason = endInvocationProto.getSkipReason();
            CLog.d("Invocation skipped with: %s", reason);
            mListener.invocationSkipped(
                    new com.android.tradefed.result.skipped.SkipReason(
                            reason.getReason(), reason.getTrigger()));
        }

        log("Invocation ended proto");
        mInvocationEnded = true;
        if (!mReportInvocation) {
            CLog.d("Skipping invocation ended reporting.");
            return;
        }
        // Only report invocation ended if enabled
        long elapsedTime =
                timeStampToMillis(endInvocationProto.getEndTime())
                        - timeStampToMillis(endInvocationProto.getStartTime());
        mListener.invocationEnded(elapsedTime);
    }

    /** Handles module level of the invocation: They have a Description for the module context. */
    private void handleModuleProto(TestRecord moduleProto) {
        if (moduleProto.hasEndTime()) {
            handleModuleEnded(moduleProto);
        } else {
            handleModuleStart(moduleProto);
        }
    }

    private void handleModuleStart(TestRecord moduleProto) {
        Any anyDescription = moduleProto.getDescription();
        if (!anyDescription.is(Context.class)) {
            throw new RuntimeException("Expected Any description of type Context");
        }
        try {
            IInvocationContext moduleContext =
                    InvocationContext.fromProto(anyDescription.unpack(Context.class));
            String message = "Test module started proto";
            if (moduleContext.getAttributes().containsKey(ModuleDefinition.MODULE_ID)) {
                String moduleId =
                        moduleContext
                                .getAttributes()
                                .getUniqueMap()
                                .get(ModuleDefinition.MODULE_ID);
                message += (": " + moduleId);
                mModuleInProgress = moduleId;
            }
            log(message);
            mModuleContext = moduleContext;
            mListener.testModuleStarted(moduleContext);
            if (mFirstModule) {
                mFirstModule = false;
                // Parse the build attributes once after invocation start to update the BuildInfo
                mergeBuildInfo(mMainContext, moduleContext);
            }
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    private void handleModuleEnded(TestRecord moduleProto) {
        handleLogs(moduleProto);
        log("Test module ended proto");
        try {
            Any anyDescription = moduleProto.getDescription();
            IInvocationContext moduleContext =
                    InvocationContext.fromProto(anyDescription.unpack(Context.class));
            // Merge attributes
            mModuleContext.addInvocationAttributes(moduleContext.getAttributes());
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
        mListener.testModuleEnded();
        mModuleInProgress = null;
        mModuleContext = null;
    }

    /** Handles the test run level of the invocation. */
    private void handleTestRun(TestRecord runProto) {
        // If the proto end-time is present we are evaluating the end of a test run.
        if (runProto.hasEndTime()) {
            handleTestRunEnd(runProto);
            mCurrentRunName = null;
        } else {
            // If the end-time is not populated yet we are dealing with the start of a run.
            mCurrentRunName = runProto.getTestRecordId();
            handleTestRunStart(runProto);
        }
    }

    private void handleTestRunStart(TestRecord runProto) {
        String id = runProto.getTestRecordId();
        log(
                "Test run started proto: %s. Expected tests: %s. Attempt: %s",
                id, runProto.getNumExpectedChildren(), runProto.getAttemptId());
        mListener.testRunStarted(
                id,
                (int) runProto.getNumExpectedChildren(),
                (int) runProto.getAttemptId(),
                timeStampToMillis(runProto.getStartTime()));
    }

    private void handleTestRunEnd(TestRecord runProto) {
        // If we find debugging information, the test run failed and we reflect it.
        if (runProto.hasDebugInfo()) {
            DebugInfo debugInfo = runProto.getDebugInfo();
            FailureDescription failure = FailureDescription.create(debugInfo.getErrorMessage());
            if (!TestRecordProto.FailureStatus.UNSET.equals(
                    runProto.getDebugInfo().getFailureStatus())) {
                failure.setFailureStatus(debugInfo.getFailureStatus());
            }

            parseDebugInfoContext(debugInfo, failure);

            mListener.testRunFailed(failure);
            log("Test run failure proto: %s", failure.toString());
        }
        handleLogs(runProto);
        log("Test run ended proto: %s", runProto.getTestRecordId());
        long elapsedTime =
                timeStampToMillis(runProto.getEndTime())
                        - timeStampToMillis(runProto.getStartTime());
        HashMap<String, Metric> metrics = new HashMap<>(runProto.getMetricsMap());
        mListener.testRunEnded(elapsedTime, metrics);
    }

    /** Handles the test cases level of the invocation. */
    private void handleTestCase(TestRecord testcaseProto) {
        String[] info = testcaseProto.getTestRecordId().split("#");
        TestDescription description = new TestDescription(info[0], info[1]);
        if (testcaseProto.hasEndTime()) {
            // Allow end event that also report start in one go. When using
            // StreamProtoResultReporter we can save some socket communication
            // by reporting test cases start and end at the same time in some instances.
            if (mCurrentTestCase == null) {
                log("Test case started proto: %s", description.toString());
                mListener.testStarted(description, timeStampToMillis(testcaseProto.getStartTime()));
            }
            handleTestCaseEnd(description, testcaseProto);
            mCurrentTestCase = null;
        } else {
            log("Test case started proto: %s", description.toString());
            mListener.testStarted(description, timeStampToMillis(testcaseProto.getStartTime()));
            mCurrentTestCase = description;
        }
    }

    private void handleTestCaseEnd(TestDescription description, TestRecord testcaseProto) {
        DebugInfo debugInfo = testcaseProto.getDebugInfo();
        switch (testcaseProto.getStatus()) {
            case FAIL:
                FailureDescription failure =
                        FailureDescription.create(testcaseProto.getDebugInfo().getErrorMessage());
                if (!TestRecordProto.FailureStatus.UNSET.equals(
                        testcaseProto.getDebugInfo().getFailureStatus())) {
                    failure.setFailureStatus(testcaseProto.getDebugInfo().getFailureStatus());
                }

                parseDebugInfoContext(debugInfo, failure);

                mListener.testFailed(description, failure);
                log("Test case failed proto: %s - %s", description.toString(), failure.toString());
                break;
            case ASSUMPTION_FAILURE:
                FailureDescription assumption =
                        FailureDescription.create(testcaseProto.getDebugInfo().getErrorMessage());
                if (!TestRecordProto.FailureStatus.UNSET.equals(
                        testcaseProto.getDebugInfo().getFailureStatus())) {
                    assumption.setFailureStatus(testcaseProto.getDebugInfo().getFailureStatus());
                }

                parseDebugInfoContext(debugInfo, assumption);

                mListener.testAssumptionFailure(description, assumption);
                log(
                        "Test case assumption failure proto: %s - %s",
                        description.toString(), testcaseProto.getDebugInfo().getTrace());
                break;
            case IGNORED:
                mListener.testIgnored(description);
                log("Test case ignored proto: %s", description.toString());
                break;
            case PASS:
                if (testcaseProto.hasSkipReason()) {
                    log(
                            "Test case skipped proto: %s",
                            description.toString(), testcaseProto.getSkipReason());
                    mListener.testSkipped(
                            description,
                            new com.android.tradefed.result.skipped.SkipReason(
                                    testcaseProto.getSkipReason().getReason(),
                                    testcaseProto.getSkipReason().getTrigger()));
                }
                break;
            default:
                throw new RuntimeException(
                        String.format(
                                "Received unexpected test status %s.", testcaseProto.getStatus()));
        }
        handleLogs(testcaseProto);
        HashMap<String, Metric> metrics = new HashMap<>(testcaseProto.getMetricsMap());
        log("Test case ended proto: %s", description.toString());
        mListener.testEnded(description, timeStampToMillis(testcaseProto.getEndTime()), metrics);
    }

    private long timeStampToMillis(Timestamp stamp) {
        return stamp.getSeconds() * 1000L + (stamp.getNanos() / 1000000L);
    }

    private void handleLogs(TestRecord proto) {
        if (!(mListener instanceof ILogSaverListener)) {
            return;
        }
        ILogSaverListener logger = (ILogSaverListener) mListener;
        for (Entry<String, Any> entry : proto.getArtifactsMap().entrySet()) {
            try {
                LogFileInfo info = entry.getValue().unpack(LogFileInfo.class);
                LogDataType dataType = null;
                try {
                    dataType = LogDataType.valueOf(info.getLogType());
                } catch (NullPointerException | IllegalArgumentException e) {
                    dataType = LogDataType.TEXT;
                }
                LogFile file =
                        new LogFile(
                                info.getPath(),
                                info.getUrl(),
                                info.getIsCompressed(),
                                dataType,
                                info.getSize());
                if (Strings.isNullOrEmpty(file.getPath())) {
                    CLog.e("Log '%s' was registered but without a path.", entry.getKey());
                    continue;
                }
                File path = new File(file.getPath());
                if (Strings.isNullOrEmpty(file.getUrl()) && path.exists()) {
                    LogDataType type = file.getType();
                    if (mReportLogs) {
                        try (InputStreamSource source = new FileInputStreamSource(path)) {
                            log(
                                    "Logging %s [type: %s]from subprocess: %s ",
                                    entry.getKey(), type, file.getPath());
                            logger.testLog(mFilePrefix + entry.getKey(), type, source);
                        }
                    }
                    if (entry.getKey().startsWith(ActiveTrace.TRACE_KEY)
                            && LogDataType.PERFETTO.equals(type)) {
                        CLog.d("Log the subprocess trace");
                        TracingLogger.getActiveTrace().addSubprocessTrace(path);
                        FileUtil.deleteFile(path);
                    }
                } else {
                    if (entry.getKey().startsWith(ActiveTrace.TRACE_KEY)
                            && LogDataType.PERFETTO.equals(file.getType())
                            && path.exists()) {
                        CLog.d("Log the subprocess trace");
                        TracingLogger.getActiveTrace().addSubprocessTrace(path);
                    }
                    if (mReportLogs) {
                        log(
                                "Logging %s [type: %s] from subprocess. url: %s, path: %s [exists:"
                                        + " %s]",
                                entry.getKey(),
                                file.getType(),
                                file.getUrl(),
                                file.getPath(),
                                path.exists());
                        logger.logAssociation(mFilePrefix + entry.getKey(), file);
                    }
                }
            } catch (InvalidProtocolBufferException e) {
                CLog.e("Couldn't unpack %s as a LogFileInfo", entry.getKey());
                CLog.e(e);
            }
        }
    }

    private void mergeBuildInfo(
            IInvocationContext receiverContext, IInvocationContext endInvocationContext) {
        if (receiverContext == null) {
            return;
        }
        // Gather attributes of build infos
        for (IBuildInfo info : receiverContext.getBuildInfos()) {
            String name = receiverContext.getBuildInfoName(info);
            IBuildInfo endInvocationInfo = endInvocationContext.getBuildInfo(name);
            if (endInvocationInfo == null) {
                CLog.e("No build info named: %s", name);
                continue;
            }
            info.addBuildAttributes(endInvocationInfo.getBuildAttributes());
        }
    }

    /**
     * Copy the build info and invocation attributes from the proto context to the current
     * invocation context
     *
     * @param receiverContext The context receiving the attributes
     * @param endInvocationContext The context providing the attributes
     */
    private void mergeInvocationContext(
            IInvocationContext receiverContext, IInvocationContext endInvocationContext) {
        if (!mMergeInvocationContext) {
            CLog.d("Skipping merging invocation context");
            return;
        }
        if (receiverContext == null) {
            return;
        }
        mergeBuildInfo(receiverContext, endInvocationContext);

        try {
            Method unlock = InvocationContext.class.getDeclaredMethod("unlock");
            unlock.setAccessible(true);
            unlock.invoke(receiverContext);
            unlock.setAccessible(false);
        } catch (NoSuchMethodException
                | SecurityException
                | IllegalAccessException
                | IllegalArgumentException
                | InvocationTargetException e) {
            CLog.e("Couldn't unlock the main context. Skip copying attributes");
            return;
        }
        // Copy invocation attributes
        MultiMap<String, String> attributes = endInvocationContext.getAttributes();
        // Parse the invocation metric group first.
        for (InvocationGroupMetricKey groupKey : InvocationGroupMetricKey.values()) {
            Set<String> attKeys = new HashSet<>(attributes.keySet());
            for (String attKey : attKeys) {
                if (attKey.startsWith(groupKey.toString() + ":")) {
                    if (attributes.get(attKey) == null) {
                        continue;
                    }
                    List<String> values = attributes.get(attKey);
                    attributes.remove(attKey);
                    if (mSkipParsingAccounting) {
                        continue;
                    }
                    String group = attKey.split(":", 2)[1];
                    for (String val : values) {
                        if (groupKey.shouldAdd()) {
                            try {
                                InvocationMetricLogger.addInvocationMetrics(
                                        groupKey, group, Long.parseLong(val));
                            } catch (NumberFormatException e) {
                                CLog.d(
                                        "Key %s doesn't have a number value, was: %s.",
                                        groupKey, val);
                                InvocationMetricLogger.addInvocationMetrics(groupKey, group, val);
                            }
                        } else {
                            InvocationMetricLogger.addInvocationMetrics(groupKey, group, val);
                        }
                    }
                }
            }
        }
        for (InvocationMetricKey key : InvocationMetricKey.values()) {
            if (!attributes.containsKey(key.toString())) {
                continue;
            }
            List<String> values = attributes.get(key.toString());
            attributes.remove(key.toString());

            if (mSkipParsingAccounting) {
                continue;
            }
            if (values == null) {
                continue;
            }
            for (String val : values) {
                if (key.shouldAdd()) {
                    try {
                        InvocationMetricLogger.addInvocationMetrics(key, Long.parseLong(val));
                    } catch (NumberFormatException e) {
                        CLog.d("Key %s doesn't have a number value, was: %s.", key, val);
                        InvocationMetricLogger.addInvocationMetrics(key, val);
                    }
                } else {
                    InvocationMetricLogger.addInvocationMetrics(key, val);
                }
            }
        }
        if (attributes.containsKey(TfObjectTracker.TF_OBJECTS_TRACKING_KEY)) {
            List<String> values = attributes.remove(TfObjectTracker.TF_OBJECTS_TRACKING_KEY);
            if (!mSkipParsingAccounting) {
                for (String val : values) {
                    for (String pair : Splitter.on(",").split(val)) {
                        if (!pair.contains("=")) {
                            continue;
                        }
                        String[] pairSplit = pair.split("=");
                        try {
                            TfObjectTracker.directCount(pairSplit[0], Long.parseLong(pairSplit[1]));
                        } catch (NumberFormatException e) {
                            CLog.e(e);
                            continue;
                        }
                    }
                }
            }
        }
        CLog.d("Adding following properties: %s", attributes.entries());
        receiverContext.addInvocationAttributes(attributes);
    }

    private void log(String format, Object... obj) {
        if (!mQuietParsing) {
            CLog.d(format, obj);
        }
    }

    private void parseDebugInfoContext(DebugInfo debugInfo, FailureDescription failure) {
        if (!debugInfo.hasDebugInfoContext()) {
            return;
        }
        DebugInfoContext debugContext = debugInfo.getDebugInfoContext();
        if (!Strings.isNullOrEmpty(debugContext.getActionInProgress())) {
            try {
                ActionInProgress value =
                        ActionInProgress.valueOf(debugContext.getActionInProgress());
                failure.setActionInProgress(value);
            } catch (IllegalArgumentException parseError) {
                CLog.e(parseError);
            }
        }
        if (!Strings.isNullOrEmpty(debugContext.getDebugHelpMessage())) {
            failure.setDebugHelpMessage(debugContext.getDebugHelpMessage());
        }
        if (!Strings.isNullOrEmpty(debugContext.getOrigin())) {
            failure.setOrigin(debugContext.getOrigin());
        }
        String errorName = debugContext.getErrorName();
        long errorCode = debugContext.getErrorCode();
        if (!Strings.isNullOrEmpty(errorName)) {
            // Most of the implementations will be Enums which represent the name/code.
            // But since there might be several Enum implementation of ErrorIdentifier, we can't
            // parse back the name to the Enum so instead we stub create a pre-populated
            // ErrorIdentifier to carry the infos.
            ErrorIdentifier errorId =
                    new ErrorIdentifier() {
                        @Override
                        public String name() {
                            return errorName;
                        }

                        @Override
                        public long code() {
                            return errorCode;
                        }

                        @Override
                        public @Nonnull FailureStatus status() {
                            FailureStatus status = failure.getFailureStatus();
                            return (status == null ? FailureStatus.UNSET : status);
                        }
                    };
            failure.setErrorIdentifier(errorId);
        }
    }
}
