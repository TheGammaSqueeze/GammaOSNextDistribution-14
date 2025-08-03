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

package com.android.csuite.core;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import com.google.common.annotations.VisibleForTesting;

import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Uninstalls a system app.
 *
 * <p>This utility class may not restore the uninstalled system app after test completes.
 *
 * <p>The class may disable dm verity on some devices, and it does not re-enable it after
 * uninstalling a system app.
 */
public final class SystemPackageUninstaller {
    @VisibleForTesting static final String OPTION_PACKAGE_NAME = "package-name";
    static final String SYSPROP_DEV_BOOTCOMPLETE = "dev.bootcomplete";
    static final String SYSPROP_SYS_BOOT_COMPLETED = "sys.boot_completed";
    static final long WAIT_FOR_BOOT_COMPLETE_TIMEOUT_MILLIS = 1000 * 60;
    @VisibleForTesting static final int MAX_NUMBER_OF_UPDATES = 100;
    @VisibleForTesting static final String PM_CHECK_COMMAND = "pm path android";

    public static void uninstallPackage(String packageName, ITestDevice device)
            throws TargetSetupError, DeviceNotAvailableException {
        checkNotNull(packageName);

        if (!isPackageManagerRunning(device)) {
            CLog.w(
                    "Package manager is not available on the device."
                            + " Attempting to recover it by restarting the framework.");
            runAsRoot(
                    device,
                    () -> {
                        stopFramework(device);
                        startFramework(device);
                    });
            if (!isPackageManagerRunning(device)) {
                throw new TargetSetupError(
                        "The package manager failed to start.", device.getDeviceDescriptor());
            }
        }

        if (!isPackageInstalled(packageName, device)) {
            CLog.i("Package %s is not installed.", packageName);
            return;
        }

        // Attempts to uninstall the package/updates from user partition.
        // This method should be called before the other methods and requires
        // the framework to be running.
        removePackageUpdates(packageName, device);

        if (!isPackageInstalled(packageName, device)) {
            CLog.i("Package %s has been removed.", packageName);
            return;
        }

        String packageInstallDirectory = getPackageInstallDirectory(packageName, device);
        CLog.d("Install directory for package %s is %s", packageName, packageInstallDirectory);

        if (!isPackagePathSystemApp(packageInstallDirectory)) {
            CLog.w("%s is not a system app, skipping", packageName);
            return;
        }

        CLog.i("Uninstalling system app %s", packageName);

        runWithWritableFilesystem(
                device,
                () ->
                        runWithFrameworkOff(
                                device,
                                () -> {
                                    removePackageInstallDirectory(packageInstallDirectory, device);
                                    removePackageData(packageName, device);
                                }));
    }

    private interface PreparerTask {
        void run() throws TargetSetupError, DeviceNotAvailableException;
    }

    private static void runWithFrameworkOff(ITestDevice device, PreparerTask action)
            throws TargetSetupError, DeviceNotAvailableException {
        stopFramework(device);

        try {
            action.run();
        } finally {
            startFramework(device);
        }
    }

    private static void runWithWritableFilesystem(ITestDevice device, PreparerTask action)
            throws TargetSetupError, DeviceNotAvailableException {
        runAsRoot(
                device,
                () -> {
                    // TODO(yuexima): The remountSystemWritable method may internally disable dm
                    // verity on some devices. Consider restoring verity which would require a
                    // reboot.
                    device.remountSystemWritable();

                    try {
                        action.run();
                    } finally {
                        remountSystemReadOnly(device);
                    }
                });
    }

    private static void runAsRoot(ITestDevice device, PreparerTask action)
            throws TargetSetupError, DeviceNotAvailableException {
        boolean disableRootAfterUninstall = false;

        if (!device.isAdbRoot()) {
            if (!device.enableAdbRoot()) {
                throw new TargetSetupError(
                        "Failed to enable adb root", device.getDeviceDescriptor());
            }

            disableRootAfterUninstall = true;
        }

        try {
            action.run();
        } finally {
            if (disableRootAfterUninstall && !device.disableAdbRoot()) {
                throw new TargetSetupError(
                        "Failed to disable adb root", device.getDeviceDescriptor());
            }
        }
    }

    private static void stopFramework(ITestDevice device)
            throws TargetSetupError, DeviceNotAvailableException {
        // 'stop' is a blocking command.
        executeShellCommandOrThrow(device, "stop", "Failed to stop framework");
        // Set the boot complete flags to false. When the framework is started again, both flags
        // will be set to true by the system upon the completion of restarting. This allows
        // ITestDevice#waitForBootComplete to wait for framework start, and it only works
        // when adb is rooted.
        device.setProperty(SYSPROP_SYS_BOOT_COMPLETED, "0");
        device.setProperty(SYSPROP_DEV_BOOTCOMPLETE, "0");
    }

    private static void startFramework(ITestDevice device)
            throws TargetSetupError, DeviceNotAvailableException {
        // 'start' is a non-blocking command.
        executeShellCommandOrThrow(device, "start", "Failed to start framework");
        // This wait only blocks if the boot completed flags are set to 0.
        device.waitForBootComplete(WAIT_FOR_BOOT_COMPLETE_TIMEOUT_MILLIS);
    }

    private static CommandResult executeShellCommandOrThrow(
            ITestDevice device, String command, String failureMessage)
            throws TargetSetupError, DeviceNotAvailableException {
        CommandResult commandResult = device.executeShellV2Command(command);

        if (commandResult.getStatus() != CommandStatus.SUCCESS) {
            throw new TargetSetupError(
                    String.format("%s; Command result: %s", failureMessage, commandResult),
                    device.getDeviceDescriptor());
        }

        return commandResult;
    }

    private static CommandResult executeShellCommandOrLog(
            ITestDevice device, String command, String failureMessage)
            throws DeviceNotAvailableException {
        CommandResult commandResult = device.executeShellV2Command(command);
        if (commandResult.getStatus() != CommandStatus.SUCCESS) {
            CLog.e("%s. Command result: %s", failureMessage, commandResult);
        }

        return commandResult;
    }

    private static void remountSystemReadOnly(ITestDevice device)
            throws TargetSetupError, DeviceNotAvailableException {
        executeShellCommandOrThrow(
                device,
                "mount -o ro,remount /system",
                "Failed to remount system partition as read only");
    }

    private static boolean isPackagePathSystemApp(String packagePath) {
        return packagePath.startsWith("/system/") || packagePath.startsWith("/product/");
    }

    private static void removePackageInstallDirectory(
            String packageInstallDirectory, ITestDevice device)
            throws TargetSetupError, DeviceNotAvailableException {
        CLog.i("Removing package install directory %s", packageInstallDirectory);
        executeShellCommandOrThrow(
                device,
                String.format("rm -r %s", packageInstallDirectory),
                String.format(
                        "Failed to remove system app package path %s", packageInstallDirectory));
    }

    private static void removePackageUpdates(String packageName, ITestDevice device)
            throws TargetSetupError, DeviceNotAvailableException {
        CLog.i("Removing package updates for %s", packageName);

        // A system package may have update packages. If so, each `adb uninstall` call
        // only uninstalls the latest update. To remove all update packages we can
        // call uninstall repeatedly until the command fails.
        for (int i = 0; i < MAX_NUMBER_OF_UPDATES; i++) {
            String errMsg = device.uninstallPackage(packageName);
            if (errMsg != null) {
                CLog.d("Completed removing updates as the uninstall command returned: %s", errMsg);
                return;
            }
            CLog.i("Removed an update package for %s", packageName);
        }

        throw new TargetSetupError(
                "Too many updates were uninstalled. Something must be wrong.",
                device.getDeviceDescriptor());
    }

    private static void removePackageData(String packageName, ITestDevice device)
            throws DeviceNotAvailableException {
        String dataPath = String.format("/data/data/%s", packageName);
        CLog.i("Removing package data directory for %s", dataPath);
        executeShellCommandOrLog(
                device,
                String.format("rm -r %s", dataPath),
                String.format(
                        "Failed to remove system app data %s from %s", packageName, dataPath));
    }

    private static boolean isPackageManagerRunning(ITestDevice device)
            throws DeviceNotAvailableException {
        return device.executeShellV2Command(PM_CHECK_COMMAND).getStatus() == CommandStatus.SUCCESS;
    }

    private static boolean isPackageInstalled(String packageName, ITestDevice device)
            throws TargetSetupError, DeviceNotAvailableException {
        CommandResult commandResult =
                executeShellCommandOrThrow(
                        device,
                        String.format("pm list packages %s", packageName),
                        "Failed to execute pm command");

        if (commandResult.getStdout() == null) {
            throw new TargetSetupError(
                    String.format("Failed to get pm command output: %s", commandResult.getStdout()),
                    device.getDeviceDescriptor());
        }

        return Arrays.asList(commandResult.getStdout().split("\\r?\\n"))
                .contains(String.format("package:%s", packageName));
    }

    private static String getPackageInstallDirectory(String packageName, ITestDevice device)
            throws TargetSetupError, DeviceNotAvailableException {
        CommandResult commandResult =
                executeShellCommandOrThrow(
                        device,
                        String.format("pm path %s", packageName),
                        "Failed to execute pm command");

        if (commandResult.getStdout() == null
                || !commandResult.getStdout().startsWith("package:")) {
            throw new TargetSetupError(
                    String.format(
                            "Failed to get pm path command output %s", commandResult.getStdout()),
                    device.getDeviceDescriptor());
        }

        String packageInstallPath = commandResult.getStdout().substring("package:".length());
        return Paths.get(packageInstallPath).getParent().toString();
    }
}
