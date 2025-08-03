/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tradefed.build;

import com.android.tradefed.config.Option;

/**
 * Utility meant to capture the usual build information arguments from a command line and create a
 * {@link IBuildInfo} from them. We use this to create a placeholder build info in case of download
 * issue while reporting the appropriate build number.
 */
public class CommandLineBuildInfoBuilder {

    @Option(name = "build-id", description = "build id to supply.")
    private String mBuildId = BuildInfo.UNKNOWN_BUILD_ID;

    @Option(name = "branch", description = "build branch name to supply.")
    private String mBranch = null;

    @Option(name = "build-flavor", description = "build flavor name to supply.")
    private String mBuildFlavor = null;

    @Option(name = "build-os", description = "build os name to supply.")
    private String mBuildOs = "linux";

    public IBuildInfo createBuild() {
        IBuildInfo build =
                new BuildInfo(mBuildId, String.format("%s-%s-%s", mBranch, mBuildOs, mBuildFlavor));
        build.setBuildBranch(mBranch);
        build.setBuildFlavor(mBuildFlavor);
        return build;
    }
}
