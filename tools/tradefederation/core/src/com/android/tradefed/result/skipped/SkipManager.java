/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tradefed.result.skipped;

import com.android.tradefed.build.content.ContentAnalysisContext;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.skipped.SkipReason.DemotionTrigger;
import com.android.tradefed.service.TradefedFeatureClient;
import com.android.tradefed.util.IDisableable;
import com.android.tradefed.util.MultiMap;

import com.proto.tradefed.feature.FeatureResponse;
import com.proto.tradefed.feature.PartResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Based on a variety of criteria the skip manager helps to decide what should be skipped at
 * different levels: invocation, modules and tests.
 */
@OptionClass(alias = "skip-manager")
public class SkipManager implements IDisableable {

    @Option(name = "disable-skip-manager", description = "Disable the skip manager feature.")
    private boolean mIsDisabled = false;

    @Option(
            name = "demotion-filters",
            description =
                    "An option to manually inject demotion filters. Intended for testing and"
                            + " validation, not for production demotion.")
    private Map<String, String> mDemotionFilterOption = new LinkedHashMap<>();

    @Option(
            name = "skip-on-no-change",
            description = "Enable the layer of skipping when there is no changes to artifacts.")
    private boolean mSkipOnNoChange = false;

    @Option(
            name = "skip-on-no-tests-discovered",
            description = "Enable the layer of skipping when there is no discovered tests to run.")
    private boolean mSkipOnNoTestsDiscovered = false;

    @Option(
            name = "skip-on-no-change-presubmit-only",
            description = "Allow enabling the skip logic only in presubmit.")
    private boolean mSkipOnNoChangePresubmitOnly = false;

    @Option(
            name = "considered-for-content-analysis",
            description = "Some tests do not directly rely on content for being relevant.")
    private boolean mConsideredForContent = true;

    // Contains the filter and reason for demotion
    private final Map<String, SkipReason> mDemotionFilters = new LinkedHashMap<>();

    private boolean mNoTestsDiscovered = false;
    private MultiMap<ITestDevice, ContentAnalysisContext> mImageAnalysis = new MultiMap<>();
    private List<ContentAnalysisContext> mTestArtifactsAnalysisContent = new ArrayList<>();
    private List<String> mModulesDiscovered = new ArrayList<String>();
    private List<String> mDependencyFiles = new ArrayList<String>();

    private String mReasonForSkippingInvocation = "SkipManager decided to skip.";

    /** Setup and initialize the skip manager. */
    public void setup(IConfiguration config, IInvocationContext context) {
        if (config.getCommandOptions().getInvocationData().containsKey("subprocess")) {
            // Information is going to flow through GlobalFilters mechanism
            return;
        }
        for (Entry<String, String> filterReason : mDemotionFilterOption.entrySet()) {
            mDemotionFilters.put(
                    filterReason.getKey(),
                    new SkipReason(filterReason.getValue(), DemotionTrigger.UNKNOWN_TRIGGER));
        }
        fetchDemotionInformation(context);
    }

    /** Returns the demoted tests and the reason for demotion */
    public Map<String, SkipReason> getDemotedTests() {
        return mDemotionFilters;
    }

    public void setImageAnalysis(ITestDevice device, ContentAnalysisContext analysisContext) {
        CLog.d("Received image artifact analysis for %s", device.getSerialNumber());
        mImageAnalysis.put(device, analysisContext);
    }

    public void setTestArtifactsAnalysis(ContentAnalysisContext analysisContext) {
        CLog.d("Received test artifact analysis.");
        mTestArtifactsAnalysisContent.add(analysisContext);
    }

    /**
     * In the early download and discovery process, report to the skip manager that no tests are
     * expected to be run. This should lead to skipping the invocation.
     */
    public void reportDiscoveryWithNoTests() {
        CLog.d("Test discovery reported that no tests were found.");
        mNoTestsDiscovered = true;
    }

    public void reportDiscoveryDependencies(List<String> modules, List<String> depFiles) {
        mModulesDiscovered.addAll(modules);
        mDependencyFiles.addAll(depFiles);
    }

    /** Reports whether we should skip the current invocation. */
    public boolean shouldSkipInvocation(TestInformation information) {
        // Build heuristic for skipping invocation
        if (mNoTestsDiscovered) {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.SKIP_NO_TESTS_DISCOVERED, 1);
            if (mSkipOnNoTestsDiscovered) {
                mReasonForSkippingInvocation =
                        "No tests to be executed where found in the configuration.";
                return true;
            } else {
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.SILENT_INVOCATION_SKIP_COUNT, 1);
                return false;
            }
        }
        ArtifactsAnalyzer analyzer =
                new ArtifactsAnalyzer(
                        information,
                        mImageAnalysis,
                        mTestArtifactsAnalysisContent,
                        mModulesDiscovered,
                        mDependencyFiles);
        return buildAnalysisDecision(information, analyzer.analyzeArtifacts());
    }

    /**
     * Request to fetch the demotion information for the invocation. This should only be done once
     * in the parent process.
     */
    private void fetchDemotionInformation(IInvocationContext context) {
        if (isDisabled()) {
            return;
        }
        if ("WORK_NODE".equals(context.getAttribute("trigger"))) {
            try (TradefedFeatureClient client = new TradefedFeatureClient()) {
                Map<String, String> args = new HashMap<>();
                FeatureResponse response = client.triggerFeature("FetchDemotionInformation", args);
                if (response.hasErrorInfo()) {
                    InvocationMetricLogger.addInvocationMetrics(
                            InvocationMetricKey.DEMOTION_ERROR_RESPONSE, 1);
                } else {
                    for (PartResponse part :
                            response.getMultiPartResponse().getResponsePartList()) {
                        String filter = part.getKey();
                        mDemotionFilters.put(filter, SkipReason.fromString(part.getValue()));
                    }
                }
            }
        }
        if (!mDemotionFilters.isEmpty()) {
            CLog.d("Demotion filters size '%s': %s", mDemotionFilters.size(), mDemotionFilters);
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DEMOTION_FILTERS_RECEIVED_COUNT, mDemotionFilters.size());
        }
    }

    /** Based on environment of the run and the build analysis, decide to skip or not. */
    private boolean buildAnalysisDecision(TestInformation information, BuildAnalysis results) {
        if (results == null) {
            return false;
        }
        boolean presubmit = "WORK_NODE".equals(information.getContext().getAttribute("trigger"));
        if (results.deviceImageChanged()) {
            return false;
        }
        InvocationMetricLogger.addInvocationMetrics(
                InvocationMetricKey.DEVICE_IMAGE_NOT_CHANGED, 1);
        if (results.hasTestsArtifacts()) {
            if (results.hasChangesInTestsArtifacts()) {
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.TEST_ARTIFACT_CHANGE_ONLY, 1);
                return false;
            } else {
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.TEST_ARTIFACT_NOT_CHANGED, 1);
            }
        } else {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.PURE_DEVICE_IMAGE_UNCHANGED, 1);
        }
        // If we get here, it means both device image and test artifacts are unaffected.
        if (!mConsideredForContent) {
            return false;
        }
        if (!presubmit) {
            // Eventually support postsubmit analysis.
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.NO_CHANGES_POSTSUBMIT, 1);
            return false;
        }
        // Currently only consider skipping in presubmit
        InvocationMetricLogger.addInvocationMetrics(InvocationMetricKey.SKIP_NO_CHANGES, 1);
        if (mSkipOnNoChange) {
            mReasonForSkippingInvocation =
                    "No relevant changes to device image or test artifacts detected.";
            return true;
        }
        if (presubmit && mSkipOnNoChangePresubmitOnly) {
            mReasonForSkippingInvocation =
                    "No relevant changes to device image or test artifacts detected.";
            return true;
        }
        InvocationMetricLogger.addInvocationMetrics(
                InvocationMetricKey.SILENT_INVOCATION_SKIP_COUNT, 1);
        return false;
    }

    public void clearManager() {
        mDemotionFilters.clear();
        mDemotionFilterOption.clear();
        mModulesDiscovered.clear();
        mDependencyFiles.clear();
        for (ContentAnalysisContext request : mTestArtifactsAnalysisContent) {
            if (request.contentInformation() != null) {
                request.contentInformation().clean();
            }
        }
        for (ContentAnalysisContext request : mImageAnalysis.values()) {
            if (request.contentInformation() != null) {
                request.contentInformation().clean();
            }
        }
        mTestArtifactsAnalysisContent.clear();
        mImageAnalysis.clear();
    }

    @Override
    public boolean isDisabled() {
        return mIsDisabled;
    }

    @Override
    public void setDisable(boolean isDisabled) {
        mIsDisabled = isDisabled;
    }

    public void setSkipDecision(boolean shouldSkip) {
        mSkipOnNoChange = shouldSkip;
        mSkipOnNoTestsDiscovered = shouldSkip;
    }

    public String getInvocationSkipReason() {
        return mReasonForSkippingInvocation;
    }
}
