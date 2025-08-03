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

package com.android.tradefed.targetprep;

import com.android.tradefed.config.Option;
import com.android.tradefed.util.RunUtil;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Common preparer for launching a local emulator.
 *
 * <p>Handles input and processing of common arguments.
 */
public abstract class BaseEmulatorPreparer extends BaseTargetPreparer {
    @Option(name = "gpu", description = "emulator gpu mode to use")
    private String mGpu = "swiftshader_indirect";

    @Option(name = "emulator-path", description = "path to emulator binary")
    private String mEmulatorPath = "emulator";

    @Option(name = "window", description = "launch emulator with a window")
    private boolean mWindow = false;

    @Option(name = "feature", description = "comma separated list of emulator features to enable")
    private String mFeatures = "";

    @Option(name = "avd-name", description = "avd name to use")
    private String mAvdName = null;

    @Option(
            name = "sdk-root",
            description = "Android SDK root file path. If set, --emulator-path will be ignored")
    private File mSdkRoot = null;

    @Option(
            name = "avd-root",
            description =
                    "file path to custom AVD storage location. "
                            + "If unset, emulator will use the default AVD folder.")
    private File mAvdRoot = null;

    protected List<String> buildEmulatorLaunchArgs() {
        List<String> args = new ArrayList<>();
        if (mSdkRoot != null) {
            args.add(Paths.get(mSdkRoot.getAbsolutePath(), "emulator", "emulator").toString());
        } else {
            args.add(mEmulatorPath);
        }
        args.add("-gpu");
        args.add(mGpu);
        if (!mWindow) {
            args.add("-no-window");
        }
        if (!mFeatures.isEmpty()) {
            args.add("-feature");
            args.add(mFeatures);
        }
        if (mAvdName != null) {
            args.add("-avd");
            args.add(mAvdName);
        }
        return args;
    }

    protected RunUtil buildRunUtilForEmulatorLaunch() {
        RunUtil runUtil = new RunUtil();
        if (mSdkRoot != null) {
            runUtil.setEnvVariable("ANDROID_HOME", mSdkRoot.getAbsolutePath());
        }
        if (mAvdRoot != null) {
            runUtil.setEnvVariable("ANDROID_AVD_HOME", mAvdRoot.getAbsolutePath());
        }
        return runUtil;
    }
}
