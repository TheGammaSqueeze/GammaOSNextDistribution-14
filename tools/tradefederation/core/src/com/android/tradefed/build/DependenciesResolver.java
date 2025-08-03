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

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.build.BuildInfoKey.BuildInfoFileKey;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.remote.ExtendedFile;
import com.android.tradefed.dependency.TestDependencyResolver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.ExecutionFiles;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.logger.CurrentInvocation;
import com.android.tradefed.invoker.logger.CurrentInvocation.InvocationInfo;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.targetprep.DeviceFlashPreparer;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.testtype.IInvocationContextReceiver;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.FlashingResourceUtil;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/** A new type of provider that allows to get all the dependencies for a test. */
public class DependenciesResolver
        implements IBuildProvider,
                IDeviceBuildProvider,
                IInvocationContextReceiver,
                IConfigurationReceiver {

    @Option(name = "build-id", description = "build id to supply.")
    private String mBuildId = "0";

    @Option(name = "branch", description = "build branch name to supply.")
    private String mBranch = null;

    @Option(name = "build-flavor", description = "build flavor name to supply.")
    private String mBuildFlavor = null;

    @Option(name = "build-os", description = "build os name to supply.")
    private String mBuildOs = "linux";

    @Option(name = "dependency", description = "The set of dependency to provide for the test")
    private Map<String, File> mDependencies = new LinkedHashMap<>();

    private File mTestsDir;
    private IInvocationContext mInvocationContext;
    private IConfiguration mConfiguration;

    @Override
    public IBuildInfo getBuild(ITestDevice device)
            throws BuildRetrievalError, DeviceNotAvailableException {
        IDeviceBuildInfo build =
                new DeviceBuildInfo(
                        mBuildId, String.format("%s-%s-%s", mBranch, mBuildOs, mBuildFlavor));
        build.setBuildBranch(mBranch);
        build.setBuildFlavor(mBuildFlavor);
        Map<String, File> mCopiedDependencies = new LinkedHashMap<>(mDependencies);
        // Complete flashing dependencies
        if (isFlasherEnabled(mConfiguration.getTargetPreparers())) {
            completeFlashingDependencies(mCopiedDependencies);
        }
        // TODO: Resolve the extra files

        // Handle the flashing files
        FlashingResourceUtil.setUpFlashingResources(build, mCopiedDependencies);
        // Handle the remaining files
        for (Entry<String, File> dependency : mCopiedDependencies.entrySet()) {
            File f =
                    TestDependencyResolver.resolveDependencyFromContext(
                            dependency.getValue(), build, mInvocationContext);
            if (f != null) {
                getInvocationFiles().put(dependency.getKey(), f);
                String version = "1";
                if (f instanceof ExtendedFile) {
                    version = ((ExtendedFile) f).getBuildId();
                }
                build.setFile(dependency.getKey(), f, version);
            }
        }
        // Create a tests dir if there are none
        if (build.getTestsDir() == null) {
            try {
                mTestsDir =
                        FileUtil.createTempDir(
                                "bootstrap-dep-test-dir",
                                CurrentInvocation.getInfo(InvocationInfo.WORK_FOLDER));
            } catch (IOException e) {
                throw new BuildRetrievalError(
                        e.getMessage(), e, InfraErrorIdentifier.FAIL_TO_CREATE_FILE);
            }
            build.setTestsDir(mTestsDir, "1");
        }
        return build;
    }

    @Override
    public IBuildInfo getBuild() throws BuildRetrievalError {
        throw new IllegalArgumentException("Should not be called");
    }

    @Override
    public void cleanUp(IBuildInfo info) {
        info.cleanUp();
    }

    @Override
    public void setInvocationContext(IInvocationContext invocationContext) {
        mInvocationContext = invocationContext;
    }

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfiguration = configuration;
    }

    @VisibleForTesting
    public final Map<String, File> getDependencies() {
        return mDependencies;
    }

    @VisibleForTesting
    ExecutionFiles getInvocationFiles() {
        return CurrentInvocation.getInvocationFiles();
    }

    /** Returns true if a flasher is present and enabled for the run. */
    private boolean isFlasherEnabled(List<ITargetPreparer> preparers) {
        boolean flashing = false;
        for (ITargetPreparer p : preparers) {
            if (p instanceof DeviceFlashPreparer && !((DeviceFlashPreparer) p).isDisabled()) {
                flashing = true;
            }
        }
        return flashing;
    }

    private void completeFlashingDependencies(Map<String, File> dependencies) {
        if (dependencies.containsKey(BuildInfoFileKey.DEVICE_IMAGE.getFileKey())) {
            return;
        }
        // Complete the dependencies to flash the device
        String baseLink = String.format("ab://%s/%s/%s/", mBranch, mBuildFlavor, mBuildId);
        dependencies.put(
                BuildInfoFileKey.DEVICE_IMAGE.getFileKey(), new File(baseLink + ".*-img-.*.zip"));
        dependencies.put(
                BuildInfoFileKey.BOOTLOADER_IMAGE.getFileKey(),
                new File(baseLink + ".*bootloader.img"));
        dependencies.put(
                BuildInfoFileKey.BASEBAND_IMAGE.getFileKey(), new File(baseLink + ".*radio.img"));
        dependencies.put(
                BuildInfoFileKey.RAMDISK_IMAGE.getFileKey(), new File(baseLink + ".*ramdisk.img"));
    }
}
