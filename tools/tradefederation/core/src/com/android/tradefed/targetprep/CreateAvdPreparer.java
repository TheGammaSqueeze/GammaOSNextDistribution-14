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

import static com.android.tradefed.invoker.logger.CurrentInvocation.getWorkFolder;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;

import com.google.common.base.Preconditions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

@OptionClass(alias = "avd")
public class CreateAvdPreparer extends BaseTargetPreparer implements IConfigurationReceiver {

    @Option(
            name = "avd-device",
            description =
                    "The device configuration to use for AVD. eg 'Nexus One'. See"
                            + " sdk/cmdline-tools/bin/avdmanager list devices for full list")
    private String mDeviceType = "Nexus One";

    @Option(name = "sdk-root", description = "The root filesystem path of Android SDK.")
    private File mSdkRoot = null;

    @Option(
            name = "system-image-path",
            description =
                    "The path to system image to use. Expected format: "
                            + "'system-images;android-30;default;x86. If unset, the first system "
                            + "image found in --sdk-root will be used")
    private String mSystemImagePath = null;

    private IConfiguration mConfig;

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfig = configuration;
    }

    @Override
    public void setUp(TestInformation testInformation)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {

        Preconditions.checkNotNull(mSdkRoot, "Invalid configuration: --sdk-root was not set");

        try {
            // create a temporary avd folder inside tradefed's invocation workfolder so it gets
            // auto-cleaned up by tradefed.
            // AVDs eat diskspace like candy so we want to clean up after ourselves

            File avdHome = FileUtil.createTempDir("avd-home", getWorkFolder());

            Path avdManagerPath =
                    Paths.get(
                            mSdkRoot.getAbsolutePath(),
                            "cmdline-tools",
                            "latest",
                            "bin",
                            "avdmanager");

            String systemImagePath = getSystemImagePath(mSdkRoot);

            // auto generate the avd name based on device type
            String avdName = mDeviceType.replace(' ', '_');

            RunUtil runUtil = new RunUtil();
            runUtil.setEnvVariable("ANDROID_SDK_ROOT", mSdkRoot.getAbsolutePath());
            runUtil.setEnvVariable("ANDROID_AVD_HOME", avdHome.getAbsolutePath());

            CommandResult result =
                    runUtil.runTimedCmd(
                            10 * 1000,
                            avdManagerPath.toString(),
                            "create",
                            "avd",
                            "--device",
                            mDeviceType,
                            "--name",
                            avdName,
                            "--package",
                            systemImagePath);
            if (result.getStatus() != CommandStatus.SUCCESS) {
                LogUtil.CLog.e("avdmanager failed: stderr: \n" + result.getStderr());
                LogUtil.CLog.e("avdmanager failed: stdout: \n" + result.getStdout());
                throw new TargetSetupError(
                        "failed to create avd via avdmanager",
                        testInformation.getDevice().getDeviceDescriptor());
            }
            LogUtil.CLog.i("Successfully created avd %s for %s", avdName, systemImagePath);

            // now inject the avd home and name into the Configuration, so any BaseEmulatorPreparer
            // class can use it
            mConfig.injectOptionValue("avd-root", avdHome.getAbsolutePath());
            mConfig.injectOptionValue("avd-name", avdName);

        } catch (IOException | ConfigurationException e) {
            throw new TargetSetupError(
                    "failed to create avd", e, testInformation.getDevice().getDeviceDescriptor());
        }
    }

    /**
     * Gets the system image path to use for AVD.
     *
     * <p>Derives the first available system image path in sdk if --system-image-path was not set
     *
     * <p>throws if one cannot be found
     */
    private String getSystemImagePath(File sdk) throws IOException {
        if (mSystemImagePath != null) {
            return mSystemImagePath;
        }
        Path systemImagesRoot = sdk.toPath().resolve("system-images");
        Optional<Path> firstSystemImage;
        try (Stream<Path> stream = Files.walk(systemImagesRoot)) {
            firstSystemImage =
                    stream.filter(
                                    new Predicate<Path>() {
                                        @Override
                                        public boolean test(Path path) {
                                            return path.getFileName()
                                                            .toString()
                                                            .equals("system.img")
                                                    && Files.isRegularFile(path);
                                        }
                                    })
                            .findFirst();
        }
        if (!firstSystemImage.isPresent()) {
            throw new IOException("failed to find any system.img file inside " + systemImagesRoot);
        }

        Path systemImageDir = firstSystemImage.get().getParent();

        return sdk.toPath().relativize(systemImageDir).toString().replace(File.separatorChar, ';');
    }
}
