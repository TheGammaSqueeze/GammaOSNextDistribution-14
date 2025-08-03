/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tradefed.device.metric;

import com.android.tradefed.build.BuildInfoKey.BuildInfoFileKey;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceActionReceiver;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.invoker.tracing.CloseableTraceScope;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;
import com.android.tradefed.result.skipped.SkipReason;
import com.android.tradefed.testtype.suite.ModuleDefinition;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Base implementation of {@link IMetricCollector} that allows to start and stop collection on
 * {@link #onTestRunStart(DeviceMetricData)} and {@link #onTestRunEnd(DeviceMetricData, Map)}.
 */
public class BaseDeviceMetricCollector implements IMetricCollector, IDeviceActionReceiver {

    public static final String TEST_CASE_INCLUDE_GROUP_OPTION = "test-case-include-group";
    public static final String TEST_CASE_EXCLUDE_GROUP_OPTION = "test-case-exclude-group";

    @Option(name = "disable", description = "disables the metrics collector")
    private boolean mDisable = false;

    @Option(
        name = TEST_CASE_INCLUDE_GROUP_OPTION,
        description =
                "Specify a group to include as part of the collection,"
                        + "group can be specified via @MetricOption. Can be repeated."
                        + "Usage: @MetricOption(group = \"groupname\") to your test methods, then"
                        + "use --test-case-include-anotation groupename to only run your group."
    )
    private List<String> mTestCaseIncludeAnnotationGroup = new ArrayList<>();

    @Option(
        name = TEST_CASE_EXCLUDE_GROUP_OPTION,
        description =
                "Specify a group to exclude from the metric collection,"
                        + "group can be specified via @MetricOption. Can be repeated."
    )
    private List<String> mTestCaseExcludeAnnotationGroup = new ArrayList<>();

    private IInvocationContext mContext;
    private List<ITestDevice> mRealDeviceList;
    private ITestInvocationListener mForwarder;
    private Map<String, File> mTestArtifactFilePathMap = new HashMap<>();
    private DeviceMetricData mRunData;
    private DeviceMetricData mTestData;
    private String mRunName;

    /**
     * Variable for whether or not to skip the collection of one test case because it was filtered.
     */
    private boolean mSkipTestCase = false;
    /** Whether or not the collector was initialized already or not. */
    private boolean mWasInitDone = false;
    /** Whether or not a DNAE occurred and we should stop collection. */
    private boolean mDeviceNoAvailable = false;
    /** Whether the {@link IDeviceActionReceiver} should be disabled or not. */
    private boolean mDisableReceiver = true;

    @Override
    public final ITestInvocationListener init(
            IInvocationContext context, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        mContext = context;
        mForwarder = listener;
        if (mWasInitDone) {
            throw new IllegalStateException(
                    String.format("init was called a second time on %s", this));
        }
        mWasInitDone = true;
        mDeviceNoAvailable = false;
        // Register this collector for device action events.
        if (!isDisabledReceiver()) {
            for (ITestDevice device : getRealDevices()) {
                device.registerDeviceActionReceiver(this);
            }
        }
        long start = System.currentTimeMillis();
        try {
            extraInit(context, listener);
        } finally {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.COLLECTOR_TIME, System.currentTimeMillis() - start);
        }
        return this;
    }

    /**
     * @param context
     * @param listener
     * @throws DeviceNotAvailableException
     */
    public void extraInit(IInvocationContext context, ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        // Empty by default
    }

    @Override
    public final List<ITestDevice> getDevices() {
        return mContext.getDevices();
    }

    /** Returns all the non-stub devices from the {@link #getDevices()} list. */
    public final List<ITestDevice> getRealDevices() {
        if (mRealDeviceList == null) {
            mRealDeviceList =
                    mContext.getDevices()
                            .stream()
                            .filter(d -> !(d.getIDevice() instanceof StubDevice))
                            .collect(Collectors.toList());
        }
        return mRealDeviceList;
    }

    /**
     * Retrieve the file from the test artifacts or module artifacts and cache
     * it in a map for the subsequent calls.
     *
     * @param fileName name of the file to look up in the artifacts.
     * @return File from the test artifact or module artifact. Returns null
     *         if file is not found.
     */
    public File getFileFromTestArtifacts(String fileName) {
        if (mTestArtifactFilePathMap.containsKey(fileName)) {
            return mTestArtifactFilePathMap.get(fileName);
        }

        File resolvedFile = resolveRelativeFilePath(fileName);
        if (resolvedFile != null) {
            CLog.i("Using file %s from %s", fileName, resolvedFile.getAbsolutePath());
            mTestArtifactFilePathMap.put(fileName, resolvedFile);
        }
        return resolvedFile;
    }

    @Override
    public final List<IBuildInfo> getBuildInfos() {
        return mContext.getBuildInfos();
    }

    @Override
    public final ITestInvocationListener getInvocationListener() {
        return mForwarder;
    }

    @Override
    public void onTestModuleStarted() throws DeviceNotAvailableException {
        // Does nothing
    }

    @Override
    public void onTestModuleEnded() throws DeviceNotAvailableException {
        // Does nothing
    }

    @Override
    public void onTestRunStart(DeviceMetricData runData) throws DeviceNotAvailableException {
        // Does nothing
    }

    /**
     * Callback for testRunFailed events
     *
     * @param testData
     * @param failure
     * @throws DeviceNotAvailableException
     */
    public void onTestRunFailed(DeviceMetricData testData, FailureDescription failure)
            throws DeviceNotAvailableException {
        // Does nothing
    }

    @Override
    public void onTestRunEnd(DeviceMetricData runData, final Map<String, Metric> currentRunMetrics)
            throws DeviceNotAvailableException {
        // Does nothing
    }

    @Override
    public void onTestStart(DeviceMetricData testData) throws DeviceNotAvailableException {
        // Does nothing
    }

    @Override
    public void onTestFail(DeviceMetricData testData, TestDescription test)
            throws DeviceNotAvailableException {
        // Does nothing
    }

    @Override
    public void onTestAssumptionFailure(DeviceMetricData testData, TestDescription test)
            throws DeviceNotAvailableException {
        // Does nothing
    }

    @Override
    public void onTestEnd(
            DeviceMetricData testData, final Map<String, Metric> currentTestCaseMetrics)
            throws DeviceNotAvailableException {
        // Does nothing
    }

    @Override
    public void onTestEnd(
            DeviceMetricData testData,
            final Map<String, Metric> currentTestCaseMetrics,
            TestDescription test)
            throws DeviceNotAvailableException {
        // Call the default implementation of onTestEnd if not overridden
        onTestEnd(testData, currentTestCaseMetrics);
    }

    /** =================================== */
    /** Invocation Listeners for forwarding */
    @Override
    public final void invocationStarted(IInvocationContext context) {
        mForwarder.invocationStarted(context);
    }

    @Override
    public final void invocationFailed(Throwable cause) {
        mForwarder.invocationFailed(cause);
    }

    @Override
    public final void invocationFailed(FailureDescription failure) {
        mForwarder.invocationFailed(failure);
    }

    @Override
    public void invocationSkipped(SkipReason reason) {
        mForwarder.invocationSkipped(reason);
    }

    @Override
    public final void invocationEnded(long elapsedTime) {
        mForwarder.invocationEnded(elapsedTime);
    }

    @Override
    public final void testLog(String dataName, LogDataType dataType, InputStreamSource dataStream) {
        mForwarder.testLog(dataName, dataType, dataStream);
    }

    @Override
    public final void testModuleStarted(IInvocationContext moduleContext) {
        long start = System.currentTimeMillis();
        try (CloseableTraceScope ignored =
                new CloseableTraceScope("module_start_" + this.getClass().getSimpleName())) {
            onTestModuleStarted();
        } catch (DeviceNotAvailableException dnae) {
            mDeviceNoAvailable = true;
            CLog.e(dnae);
        } catch (Throwable t) {
            CLog.e(t);
        } finally {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.COLLECTOR_TIME, System.currentTimeMillis() - start);
            mForwarder.testModuleStarted(moduleContext);
        }
    }

    @Override
    public final void testModuleEnded() {
        long start = System.currentTimeMillis();
        try (CloseableTraceScope ignored =
                new CloseableTraceScope("module_end_" + this.getClass().getSimpleName())) {
            onTestModuleEnded();
        } catch (DeviceNotAvailableException dnae) {
            CLog.e(dnae);
        } catch (Throwable t) {
            CLog.e(t);
        } finally {
            // De-register this collector from device action events
            if (!isDisabledReceiver()) {
                for (ITestDevice device : getRealDevices()) {
                    device.deregisterDeviceActionReceiver(this);
                }
            }
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.COLLECTOR_TIME, System.currentTimeMillis() - start);
            mForwarder.testModuleEnded();
        }
    }

    /** Test run callbacks */
    @Override
    public final void testRunStarted(String runName, int testCount) {
        testRunStarted(runName, testCount, 0);
    }

    @Override
    public final void testRunStarted(String runName, int testCount, int attemptNumber) {
        testRunStarted(runName, testCount, 0, System.currentTimeMillis());
    }

    @Override
    public final void testRunStarted(
            String runName, int testCount, int attemptNumber, long startTime) {
        mRunData = new DeviceMetricData(mContext);
        mRunName = runName;
        mDeviceNoAvailable = false;
        long start = System.currentTimeMillis();
        try (CloseableTraceScope ignored =
                new CloseableTraceScope("run_start_" + this.getClass().getSimpleName())) {
            onTestRunStart(mRunData, testCount);
        } catch (DeviceNotAvailableException e) {
            mDeviceNoAvailable = true;
            CLog.e(e);
        } catch (Throwable t) {
            // Prevent exception from messing up the status reporting.
            CLog.e(t);
        } finally {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.COLLECTOR_TIME, System.currentTimeMillis() - start);
        }
        mForwarder.testRunStarted(runName, testCount, attemptNumber, startTime);
    }

    @Override
    public final void testRunFailed(String errorMessage) {
        if (!mDeviceNoAvailable) {
            long start = System.currentTimeMillis();
            try {
                onTestRunFailed(mRunData, FailureDescription.create(errorMessage));
            } catch (DeviceNotAvailableException e) {
                mDeviceNoAvailable = true;
                CLog.e(e);
            } catch (Throwable t) {
                // Prevent exception from messing up the status reporting.
                CLog.e(t);
            } finally {
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.COLLECTOR_TIME, System.currentTimeMillis() - start);
            }
        }
        mForwarder.testRunFailed(errorMessage);
    }

    @Override
    public final void testRunFailed(FailureDescription failure) {
        if (!mDeviceNoAvailable) {
            long start = System.currentTimeMillis();
            try {
                onTestRunFailed(mRunData, failure);
            } catch (DeviceNotAvailableException e) {
                mDeviceNoAvailable = true;
                CLog.e(e);
            } catch (Throwable t) {
                // Prevent exception from messing up the status reporting.
                CLog.e(t);
            } finally {
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.COLLECTOR_TIME, System.currentTimeMillis() - start);
            }
        }
        mForwarder.testRunFailed(failure);
    }

    @Override
    public final void testRunStopped(long elapsedTime) {
        mForwarder.testRunStopped(elapsedTime);
    }

    @Override
    public final void testRunEnded(long elapsedTime, HashMap<String, Metric> runMetrics) {
        if (!mDeviceNoAvailable) {
            long start = System.currentTimeMillis();
            try (CloseableTraceScope ignored =
                    new CloseableTraceScope("run_end_" + this.getClass().getSimpleName())) {
                onTestRunEnd(mRunData, runMetrics);
                mRunData.addToMetrics(runMetrics);
            } catch (DeviceNotAvailableException e) {
                mDeviceNoAvailable = true;
                CLog.e(e);
            } catch (Throwable t) {
                // Prevent exception from messing up the status reporting.
                CLog.e(t);
            } finally {
                // De-register this collector from device action events
                if (!isDisabledReceiver()) {
                    for (ITestDevice device : getRealDevices()) {
                        device.deregisterDeviceActionReceiver(this);
                    }
                }
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.COLLECTOR_TIME, System.currentTimeMillis() - start);
            }
        }
        mForwarder.testRunEnded(elapsedTime, runMetrics);
    }

    /** Test cases callbacks */
    @Override
    public final void testStarted(TestDescription test) {
        testStarted(test, System.currentTimeMillis());
    }

    @Override
    public final void testStarted(TestDescription test, long startTime) {
        if (!mDeviceNoAvailable) {
            mTestData = new DeviceMetricData(mContext);
            mSkipTestCase = shouldSkip(test);
            if (!mSkipTestCase) {
                long start = System.currentTimeMillis();
                try {
                    onTestStart(mTestData);
                } catch (DeviceNotAvailableException e) {
                    mDeviceNoAvailable = true;
                    CLog.e(e);
                } catch (Throwable t) {
                    // Prevent exception from messing up the status reporting.
                    CLog.e(t);
                } finally {
                    InvocationMetricLogger.addInvocationMetrics(
                            InvocationMetricKey.COLLECTOR_TIME, System.currentTimeMillis() - start);
                }
            }
        }
        mForwarder.testStarted(test, startTime);
    }

    @Override
    public final void testFailed(TestDescription test, String trace) {
        if (!mSkipTestCase && !mDeviceNoAvailable) {
            long start = System.currentTimeMillis();
            try {
                onTestFail(mTestData, test);
            } catch (DeviceNotAvailableException e) {
                mDeviceNoAvailable = true;
                CLog.e(e);
            } catch (Throwable t) {
                // Prevent exception from messing up the status reporting.
                CLog.e(t);
            } finally {
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.COLLECTOR_TIME, System.currentTimeMillis() - start);
            }
        }
        mForwarder.testFailed(test, trace);
    }

    @Override
    public final void testFailed(TestDescription test, FailureDescription failure) {
        if (!mSkipTestCase && !mDeviceNoAvailable) {
            // Don't collect on not_executed test case
            if (failure.getFailureStatus() == null
                    || !FailureStatus.NOT_EXECUTED.equals(failure.getFailureStatus())) {
                long start = System.currentTimeMillis();
                try {
                    onTestFail(mTestData, test);
                } catch (DeviceNotAvailableException e) {
                    mDeviceNoAvailable = true;
                    CLog.e(e);
                } catch (Throwable t) {
                    // Prevent exception from messing up the status reporting.
                    CLog.e(t);
                } finally {
                    InvocationMetricLogger.addInvocationMetrics(
                            InvocationMetricKey.COLLECTOR_TIME, System.currentTimeMillis() - start);
                }
            }
        }
        mForwarder.testFailed(test, failure);
    }

    @Override
    public final void testEnded(TestDescription test, HashMap<String, Metric> testMetrics) {
        testEnded(test, System.currentTimeMillis(), testMetrics);
    }

    @Override
    public final void testEnded(
            TestDescription test, long endTime, HashMap<String, Metric> testMetrics) {
        if (!mSkipTestCase && !mDeviceNoAvailable) {
            long start = System.currentTimeMillis();
            try {
                onTestEnd(mTestData, testMetrics, test);
                mTestData.addToMetrics(testMetrics);
            } catch (DeviceNotAvailableException e) {
                mDeviceNoAvailable = true;
                CLog.e(e);
            } catch (Throwable t) {
                // Prevent exception from messing up the status reporting.
                CLog.e(t);
            } finally {
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.COLLECTOR_TIME, System.currentTimeMillis() - start);
            }
        } else {
            CLog.d("Skipping %s collection for %s.", this.getClass().getName(), test.toString());
        }
        mForwarder.testEnded(test, endTime, testMetrics);
    }

    @Override
    public final void testSkipped(TestDescription test, SkipReason reason) {
        mForwarder.testSkipped(test, reason);
    }

    @Override
    public final void testAssumptionFailure(TestDescription test, String trace) {
        if (!mSkipTestCase && !mDeviceNoAvailable) {
            long start = System.currentTimeMillis();
            try {
                onTestAssumptionFailure(mTestData, test);
            } catch (DeviceNotAvailableException e) {
                mDeviceNoAvailable = true;
                CLog.e(e);
            } catch (Throwable t) {
                // Prevent exception from messing up the status reporting.
                CLog.e(t);
            } finally {
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.COLLECTOR_TIME, System.currentTimeMillis() - start);
            }
        }
        mForwarder.testAssumptionFailure(test, trace);
    }

    @Override
    public final void testAssumptionFailure(TestDescription test, FailureDescription failure) {
        if (!mSkipTestCase && !mDeviceNoAvailable) {
            long start = System.currentTimeMillis();
            try {
                onTestAssumptionFailure(mTestData, test);
            } catch (DeviceNotAvailableException e) {
                mDeviceNoAvailable = true;
                CLog.e(e);
            } catch (Throwable t) {
                // Prevent exception from messing up the status reporting.
                CLog.e(t);
            } finally {
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.COLLECTOR_TIME, System.currentTimeMillis() - start);
            }
        }
        mForwarder.testAssumptionFailure(test, failure);
    }

    @Override
    public final void testIgnored(TestDescription test) {
        mForwarder.testIgnored(test);
    }

    /**
     * Do not use inside metric collector implementation. This is pure forwarding.
     */
    @Override
    public final void setLogSaver(ILogSaver logSaver) {
        if (mForwarder instanceof ILogSaverListener) {
            ((ILogSaverListener) mForwarder).setLogSaver(logSaver);
        }
    }

    /**
     * Do not use inside metric collector implementation. This is pure forwarding.
     */
    @Override
    public final void logAssociation(String dataName, LogFile logFile) {
        if (mForwarder instanceof ILogSaverListener) {
            ((ILogSaverListener) mForwarder).logAssociation(dataName, logFile);
        }
    }

    /**
     * Do not use inside metric collector implementation. This is pure forwarding.
     */
    @Override
    public final void testLogSaved(String dataName, LogDataType dataType,
            InputStreamSource dataStream, LogFile logFile) {
        if (mForwarder instanceof ILogSaverListener) {
            ((ILogSaverListener) mForwarder).testLogSaved(dataName, dataType, dataStream, logFile);
        }
    }

    /** {@inheritDoc} */
    @Override
    public final boolean isDisabled() {
        return mDisable;
    }

    /** {@inheritDoc} */
    @Override
    public final void setDisable(boolean isDisabled) {
        mDisable = isDisabled;
    }

    /**
     * Returns the name of test run {@code mRunName} that triggers the collector.
     *
     * @return mRunName, the current test run name.
     */
    public String getRunName() {
        return mRunName;
    }

    public String getModuleName() {
        return mContext.getAttributes().get(ModuleDefinition.MODULE_NAME) != null
                ? mContext.getAttributes().get(ModuleDefinition.MODULE_NAME).get(0)
                : null;
    }

    /**
     * Helper to decide if a test case should or not run the collector method associated.
     *
     * @param desc the identifier of the test case.
     * @return True the collector should be skipped. False otherwise.
     */
    private boolean shouldSkip(TestDescription desc) {
        Set<String> testCaseGroups = new HashSet<>();
        if (desc.getAnnotation(MetricOption.class) != null) {
            String groupName = desc.getAnnotation(MetricOption.class).group();
            testCaseGroups.addAll(Arrays.asList(groupName.split(",")));
        } else {
            // Add empty group name for default case.
            testCaseGroups.add("");
        }
        // Exclusion has priority: if any of the groups is excluded, exclude the test case.
        for (String groupName : testCaseGroups) {
            if (mTestCaseExcludeAnnotationGroup.contains(groupName)) {
                return true;
            }
        }
        // Inclusion filter: if any of the group is included, include the test case.
        for (String includeGroupName : mTestCaseIncludeAnnotationGroup) {
            if (testCaseGroups.contains(includeGroupName)) {
                return false;
            }
        }

        // If we had filters and did not match any groups
        if (!mTestCaseIncludeAnnotationGroup.isEmpty()) {
            return true;
        }
        return false;
    }

    /**
     * Resolves the relative path of the file from the test artifacts
     * directory or module directory.
     *
     * @param fileName file name that needs to be resolved.
     * @return File file resolved for the given file name. Returns null
     *         if file not found.
     */
    private File resolveRelativeFilePath(String fileName) {
        IBuildInfo buildInfo = getBuildInfos().get(0);
        String mModuleName = null;
        // Retrieve the module name.
        if (mContext.getAttributes().get(ModuleDefinition.MODULE_NAME) != null) {
            mModuleName = mContext.getAttributes().get(ModuleDefinition.MODULE_NAME)
                    .get(0);
        }

        File src = null;
        if (buildInfo != null) {
            src = buildInfo.getFile(fileName);
            if (src != null && src.exists()) {
                return src;
            }
        }

        if (buildInfo instanceof IDeviceBuildInfo) {
            IDeviceBuildInfo deviceBuild = (IDeviceBuildInfo) buildInfo;
            File testDir = deviceBuild.getTestsDir();
            List<File> scanDirs = new ArrayList<>();
            // If it exists, always look first in the ANDROID_TARGET_OUT_TESTCASES
            File targetTestCases = deviceBuild.getFile(BuildInfoFileKey.TARGET_LINKED_DIR);
            if (targetTestCases != null) {
                scanDirs.add(targetTestCases);
            }
            // If not, look into the test directory.
            if (testDir != null) {
                scanDirs.add(testDir);
            }

            if (mModuleName != null) {
                // Use module name as a discriminant to find some files
                if (testDir != null) {
                    try {
                        File moduleDir = FileUtil.findDirectory(
                                mModuleName, scanDirs.toArray(new File[] {}));
                        if (moduleDir != null) {
                            // If the spec is pushing the module itself
                            if (mModuleName.equals(fileName)) {
                                // If that's the main binary generated by the target, we push the
                                // full directory
                                return moduleDir;
                            }
                            // Search the module directory if it exists use it in priority
                            src = FileUtil.findFile(fileName, null, moduleDir);
                            if (src != null) {
                                CLog.i("Retrieving src file from" + src.getAbsolutePath());
                                return src;
                            }
                        } else {
                            CLog.d("Did not find any module directory for '%s'", mModuleName);
                        }

                    } catch (IOException e) {
                        CLog.w(
                                "Something went wrong while searching for the module '%s' "
                                        + "directory.",
                                mModuleName);
                    }
                }
            }

            for (File searchDir : scanDirs) {
                try {
                    Set<File> allMatch = FileUtil.findFilesObject(searchDir, fileName);
                    if (allMatch.size() > 1) {
                        CLog.d(
                                "Several match for filename '%s', searching for top-level match.",
                                fileName);
                        for (File f : allMatch) {
                            if (f.getParent().equals(searchDir.getAbsolutePath())) {
                                return f;
                            }
                        }
                    } else if (allMatch.size() == 1) {
                        return allMatch.iterator().next();
                    }
                } catch (IOException e) {
                    CLog.w("Failed to find test files from directory.");
                }
            }
        }
        return src;
    }

    @Override
    public void rebootStarted(ITestDevice device) throws DeviceNotAvailableException {
        // Does nothing
    }

    @Override
    public void rebootEnded(ITestDevice device) throws DeviceNotAvailableException {
        // Does nothing
    }

    @Override
    public void setDisableReceiver(boolean isDisabled) {
        mDisableReceiver = isDisabled;
    }

    @Override
    public boolean isDisabledReceiver() {
        return mDisableReceiver;
    }
}
