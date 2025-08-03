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

import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.ApexInfo;
import com.android.tradefed.error.HarnessException;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.InfraErrorIdentifier;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 * A {@link TargetPreparer} that attempts to install mainline modules via adb push
 * and verify push success.
 */
@OptionClass(alias = "mainline-oem-installer")
public class ModuleOemTargetPreparer extends InstallApexModuleTargetPreparer {
    private static final long DELAY_WAITING_TIME = 2000; // 2 sec.

    @Option(
            name = "reboot-wait-time-ms",
            description = "Additional wait for device to be ready after reboot.")
    private static long mRebootWaitTimeMs = 1000 * 60 * 5; // 5 min.

    @Option(name = "recover-preload-modules", description = "Recover the preload mainline modules.")
    private boolean mRecoverPreloadModules = false;

    @Option(name = "push-test-modules", description = "Push the test mainline modules.")
    private boolean mPushTestModules = true;

    @Option(
            name = "reload-by-factory-reset",
            description = "If reload apex modules by factory reset.")
    private boolean mReloadByFactoryReset = false;

    @Option(name = "disable-package-cache", description = "Disable the cache of package manager")
    private boolean mDisablePackageCache = false;

    @Option(
            name = "recover-module-folder",
            description =
                    "The path to recover module folder. All packages should have their own"
                            + " subfolder whose name is the package name.")
    private File mRecoverModuleFolder;

    /**
     * Perform the target setup for testing, push modules to replace the preload ones
     *
     * @param testInfo The {@link TestInformation} of the invocation.
     * @throws TargetSetupError if fatal error occurred setting up environment
     * @throws BuildError If an error occurs due to the build being prepared
     * @throws DeviceNotAvailableException if device became unresponsive
     */
    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        setTestInformation(testInfo);
        ITestDevice device = testInfo.getDevice();

        final int apiLevel = device.getApiLevel();
        if (apiLevel < 29 /* Build.VERSION_CODES.Q */) {
            CLog.i(
                    "Skip the target preparer because the api level is %i and"
                            + "the build doesn't have mainline modules",
                    apiLevel);
            return;
        }

        ModulePusher pusher = getPusher(device);

        if (mRecoverPreloadModules && mRecoverModuleFolder != null) {
            ImmutableMultimap<String, File> files = getRecoverModules();
            if (files.isEmpty()) {
                CLog.i("No recover modules to install.");
                return;
            }
            ImmutableMultimap<String, File> recoverFiles = filterModulesToInstall(testInfo, files);
            recoverPreloadModules(pusher, recoverFiles, device.getDeviceDescriptor());
        }

        if (mPushTestModules) {
            if (mTrainFolderPath != null) {
                addApksToTestFiles();
            }
            List<File> testAppFiles = getModulesToInstall(testInfo);
            if (testAppFiles.isEmpty()) {
                CLog.i("No modules to install.");
                return;
            }
            installTestModules(testInfo, pusher, testAppFiles, device.getDeviceDescriptor());
        }
    }

    private ImmutableMultimap<String, File> getRecoverModules() throws TargetSetupError {
        File rootFolder = getRecoverModuleFolder();
        if (!rootFolder.exists() || !rootFolder.isDirectory()) {
            throw new TargetSetupError(
                    String.format("%s is not a dir", rootFolder),
                    InfraErrorIdentifier.ARTIFACT_NOT_FOUND);
        }
        ImmutableMultimap.Builder<String, File> builder = ImmutableMultimap.builder();
        for (File moduleFolder : rootFolder.listFiles()) {
            if (!moduleFolder.exists() || !moduleFolder.isDirectory()) {
                throw new TargetSetupError(
                        String.format("%s is not a dir", moduleFolder),
                        InfraErrorIdentifier.ARTIFACT_NOT_FOUND);
            }
            // The rootFolder name will be changed by file resolver, but the name of the subdir
            // moduleFoler will remain the same.
            String packageName = moduleFolder.getName();
            CLog.i(
                    "Get modules files %s for the package %s",
                    moduleFolder.listFiles(), packageName);
            builder.putAll(packageName, moduleFolder.listFiles());
        }
        return builder.build();
    }

    private File getRecoverModuleFolder() {
        return mRecoverModuleFolder;
    }

    private ImmutableMultimap<String, File> filterModulesToInstall(
            TestInformation testInfo, ImmutableMultimap<String, File> moduleFiles)
            throws DeviceNotAvailableException {
        // Get all preloaded modules for the device.
        ITestDevice device = testInfo.getDevice();
        ImmutableSet<String> installedPackages = getPreloadPackageNames(device);

        ImmutableMultimap.Builder<String, File> builder = ImmutableMultimap.builder();
        for (String packageName : moduleFiles.keySet()) {
            if (installedPackages.contains(packageName)) {
                CLog.i("Found preloaded module for %s.", packageName);
                builder.putAll(packageName, moduleFiles.get(packageName));
            } else {
                CLog.i(
                        "The module package %s is not preloaded on the device but is included in "
                                + "the train.",
                        packageName);
            }
        }
        return builder.build();
    }

    private void recoverPreloadModules(
            ModulePusher pusher,
            ImmutableMultimap<String, File> moduleFiles,
            DeviceDescriptor deviceDescriptor)
            throws TargetSetupError {
        try {
            pusher.installModules(moduleFiles, mReloadByFactoryReset, mDisablePackageCache);
        } catch (HarnessException e) {
            throw new TargetSetupError("Failed to recover modules", e, deviceDescriptor);
        }
    }

    private void installTestModules(
            TestInformation testInfo,
            ModulePusher pusher,
            List<File> testAppFiles,
            DeviceDescriptor deviceDescriptor)
            throws TargetSetupError {
        ImmutableMultimap.Builder<String, File> builder = ImmutableMultimap.builder();
        for (File moduleFile : testAppFiles) {
            File[] toPush = new File[] {moduleFile};
            if (ModulePusher.hasExtension(SPLIT_APKS_SUFFIX, moduleFile)) {
                // The base file is the first element
                toPush = getSplitsForApks(testInfo, moduleFile).toArray(new File[0]);
            }
            String packageName = parsePackageName(toPush[0]);
            builder.putAll(packageName, toPush);
        }

        try {
            pusher.installModules(builder.build(), mReloadByFactoryReset, mDisablePackageCache);
        } catch (HarnessException e) {
            throw new TargetSetupError("Failed to install modules", e, deviceDescriptor);
        }
    }

    private ImmutableSet<String> getPreloadPackageNames(ITestDevice device)
            throws DeviceNotAvailableException {
        Set<String> installedPackages = new HashSet<>(device.getInstalledPackageNames());
        Set<ApexInfo> installedApexes = new HashSet<>(device.getActiveApexes());
        for (ApexInfo installedApex : installedApexes) {
            installedPackages.add(installedApex.name);
        }
        return ImmutableSet.copyOf(installedPackages);
    }

    @VisibleForTesting
    ModulePusher getPusher(ITestDevice device) {
        return new ModulePusher(device, mRebootWaitTimeMs, DELAY_WAITING_TIME);
    }
}
