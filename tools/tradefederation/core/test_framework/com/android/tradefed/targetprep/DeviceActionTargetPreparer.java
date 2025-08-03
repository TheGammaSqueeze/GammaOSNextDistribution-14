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

package com.android.tradefed.targetprep;

import static com.android.tradefed.log.LogUtil.CLog;

import static java.util.stream.Collectors.joining;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.result.ITestLoggerReceiver;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.DeviceActionUtil;
import com.android.tradefed.util.DeviceActionUtil.Command;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

/** A {@link ITargetPreparer} to perform device actions. */
@OptionClass(alias = "device-action")
public class DeviceActionTargetPreparer extends BaseTargetPreparer implements ITestLoggerReceiver {
    private static final String REPEATED_FLAG_DELIMITER = ", ";
    private static final String FILE_ARG_FORMAT = "file_%s=%s";
    private static final String MAINLINE_MODULES_TAG = "mainline_modules";
    private static final String APKS_ZIPS_TAG = "apks_zips";
    private static final String TRAIN_FOLDER_TAG = "train_folder";
    private static final String ENABLE_ROLLBACK_FLAG = "enable_rollback";
    private static final String DEV_KEY_SIGNED_FLAG = "dev_key_signed";

    // Common options for all commands.
    @Option(name = "da-command", description = "Command name of device action.", mandatory = true)
    private Command mCommand;

    @Option(
            name = "bundletool-jar",
            description = "The file name of the bundletool jar.",
            mandatory = true)
    File mBundletoolJar;

    @Option(
            name = "device-action-jar",
            description = "The file name of the device action jar.",
            mandatory = true)
    File mDeviceActionJar;

    // Options for install mainline command.
    @Option(name = "enable-rollback", description = "Enable rollback if the mainline update fails.")
    private boolean mEnableRollback = true;

    @Option(name = "dev-key-signed", description = "If the modules are signed by dev keys.")
    private boolean mDevKeySigned = false;

    @Option(name = "train-folder", description = "The path of the train folder.")
    private File mTrainFolderPath;

    @Option(
            name = "mainline-modules",
            description =
                    "The name of module file to be installed on device. Can be repeated. The file"
                            + " can be apk/apex/apks. If one file is apks, then all files should be"
                            + " apks.")
    private Set<File> mModuleFiles = new HashSet<>();

    @Option(
            name = "apks-zips",
            description = "The name of zip file containing train apks. Can be repeated.")
    private Set<File> mZipFiles = new HashSet<>();

    private DeviceActionUtil mDeviceActionUtil;

    private ITestLogger mTestLogger;

    @Override
    public void setUp(TestInformation testInfo) throws TargetSetupError {
        if (mDeviceActionUtil == null) {
            try {
                mDeviceActionUtil = DeviceActionUtil.create(mDeviceActionJar, mBundletoolJar);
            } catch (DeviceActionUtil.DeviceActionConfigError e) {
                throw new TargetSetupError("Failed to create DeviceActionUtil", e, e.getErrorId());
            }
        }

        CommandResult result = performAction(testInfo);
        if (result == null) {
            CLog.w("No action performed.");
            return;
        }
        CLog.i(result.getStdout());
        CLog.i(result.getStderr());

        TargetSetupError error = null;
        try {
            mDeviceActionUtil.generateLogFile(result);
            if (mTestLogger != null) {
                mDeviceActionUtil.saveToLogs(mCommand, mTestLogger);
            }
        } catch (IOException e) {
            error =
                    new TargetSetupError(
                            "Failed to save log files.",
                            e,
                            InfraErrorIdentifier.FAIL_TO_CREATE_FILE);
        }
        if (result.getExitCode() != 0) {
            error =
                    new TargetSetupError(
                            String.format(
                                    "Failed to execute device action command %s. The status is %s.",
                                    mCommand, result.getStatus()),
                            DeviceErrorIdentifier.DEVICE_ACTION_EXECUTION_FAILURE);
        } else {
            CLog.i("Device action completed.");
        }

        if (error != null) {
            throw error;
        }
    }

    @Override
    public void setTestLogger(ITestLogger testLogger) {
        mTestLogger = testLogger;
    }

    @VisibleForTesting
    void setDeviceActionUtil(DeviceActionUtil deviceActionUtil) {
        mDeviceActionUtil = deviceActionUtil;
    }

    /** Performs device action. Returns null if no action performed. */
    @Nullable
    private CommandResult performAction(TestInformation testInfo) {
        String deviceId = testInfo.getDevice().getSerialNumber();
        switch (mCommand) {
            case RESET:
                return reset(deviceId);
            case INSTALL_MAINLINE:
                return installMainline(deviceId);
            default:
                return null;
        }
    }

    private CommandResult reset(String deviceId) {
        return mDeviceActionUtil.execute(Command.RESET, deviceId, ImmutableList.of());
    }

    @Nullable
    private CommandResult installMainline(String deviceId) {
        ArrayList<String> args = new ArrayList<>();
        if (!mZipFiles.isEmpty()) {
            args.add(createFileArg(APKS_ZIPS_TAG, mZipFiles));
        } else if (!mModuleFiles.isEmpty()) {
            args.add(createFileArg(MAINLINE_MODULES_TAG, mModuleFiles));
        } else if (mTrainFolderPath != null && mTrainFolderPath.exists()) {
            args.add(createFileArg(TRAIN_FOLDER_TAG, mTrainFolderPath));
        } else {
            CLog.i("No module provided. No mainline update.");
            return null;
        }
        if (mEnableRollback) {
            args.add(ENABLE_ROLLBACK_FLAG);
        }
        if (mDevKeySigned) {
            args.add(DEV_KEY_SIGNED_FLAG);
        }
        return mDeviceActionUtil.execute(Command.INSTALL_MAINLINE, deviceId, args);
    }

    private static String createFileArg(String tag, Collection<File> files) {
        return files.stream()
                .map(file -> createFileArg(tag, file))
                .collect(joining(REPEATED_FLAG_DELIMITER));
    }

    private static String createFileArg(String tag, File file) {
        return String.format(FILE_ARG_FORMAT, tag, file.getAbsolutePath());
    }
}
