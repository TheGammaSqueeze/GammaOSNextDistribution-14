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

import java.util.List;

/** Represents the results of a single build analysis. */
public class BuildAnalysis {

    private final boolean mDeviceImageChanged;
    private final boolean mHasTestsArtifacts;
    private boolean mHasChangesInTests = false;

    public BuildAnalysis(boolean deviceImageChanged, boolean hasTestsArtifacts) {
        this.mDeviceImageChanged = deviceImageChanged;
        this.mHasTestsArtifacts = hasTestsArtifacts;
    }

    public boolean deviceImageChanged() {
        return mDeviceImageChanged;
    }

    public boolean hasTestsArtifacts() {
        return mHasTestsArtifacts;
    }

    public boolean hasChangesInTestsArtifacts() {
        return mHasChangesInTests;
    }

    public void setChangesInTests(boolean hasChanges) {
        mHasChangesInTests = hasChanges;
    }

    @Override
    public String toString() {
        return "BuildAnalysis [mDeviceImageChanged="
                + mDeviceImageChanged
                + ", mHasTestsArtifacts="
                + mHasTestsArtifacts
                + ", mHasChangesInTests="
                + mHasChangesInTests
                + "]";
    }

    public static BuildAnalysis mergeReports(List<BuildAnalysis> reports) {
        boolean deviceImageChanged = false;
        boolean hasTestsArtifacts = false;
        // Anchor toward things changing
        for (BuildAnalysis rep : reports) {
            deviceImageChanged |= rep.deviceImageChanged();
            hasTestsArtifacts |= rep.hasTestsArtifacts();
        }
        return new BuildAnalysis(deviceImageChanged, hasTestsArtifacts);
    }
}
