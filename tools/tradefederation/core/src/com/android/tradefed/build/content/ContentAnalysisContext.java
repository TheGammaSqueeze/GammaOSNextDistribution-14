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
package com.android.tradefed.build.content;

import java.util.HashSet;
import java.util.Set;

/** Provide the context surrounding a content to analyze it properly. */
public class ContentAnalysisContext {

    /** This describes what to expect from the content structure for proper analysis. */
    public enum AnalysisMethod {
        FILE,
        MODULE_XTS,
        SANDBOX_WORKDIR,
        BUILD_KEY, // Search directly for a specific item in build info
        DEVICE_IMAGE // Analyze the device image content
    }

    private final String contentEntry;
    private final ContentInformation information;
    private final AnalysisMethod analysisMethod;
    // This tracks path to ignore from analysis because known to always change but do not cause
    // functional changes.
    private Set<String> ignoredChange = new HashSet<>();
    // Report what is considered a common locations which if modified invalidate the results.
    private Set<String> commonLocations = new HashSet<>();
    // Set this flag if somehow we need to invalidate the full analysis.
    private boolean invalidateAnalysis = false;
    private String mInvalidationReason;

    public ContentAnalysisContext(
            String contentEntry, ContentInformation information, AnalysisMethod method) {
        this.contentEntry = contentEntry;
        this.information = information;
        this.analysisMethod = method;
    }

    public String contentEntry() {
        return contentEntry;
    }

    public ContentInformation contentInformation() {
        return information;
    }

    public AnalysisMethod analysisMethod() {
        return analysisMethod;
    }

    public Set<String> ignoredChanges() {
        return ignoredChange;
    }

    public Set<String> commonLocations() {
        return commonLocations;
    }

    public boolean abortAnalysis() {
        return invalidateAnalysis || information == null;
    }

    public String abortReason() {
        return mInvalidationReason;
    }

    public ContentAnalysisContext addIgnoreChange(String path) {
        ignoredChange.add(path);
        return this;
    }

    public ContentAnalysisContext addIgnoreChanges(Set<String> paths) {
        ignoredChange.addAll(paths);
        return this;
    }

    public ContentAnalysisContext addCommonLocation(String path) {
        commonLocations.add(path);
        return this;
    }

    public ContentAnalysisContext addCommonLocations(Set<String> paths) {
        commonLocations.addAll(paths);
        return this;
    }

    public void invalidateAnalysis() {
        invalidateAnalysis = true;
    }

    public void invalidateAnalysis(String reason) {
        invalidateAnalysis = true;
        mInvalidationReason = reason;
    }
}
