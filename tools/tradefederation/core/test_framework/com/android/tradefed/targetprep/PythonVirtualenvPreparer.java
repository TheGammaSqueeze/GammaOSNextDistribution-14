/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.invoker.logger.CurrentInvocation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.PythonVirtualenvHelper;
import com.android.tradefed.util.RunUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Sets up a Python virtualenv on the host and installs packages. To activate it, the working
 * directory is changed to the root of the virtualenv.
 */
@OptionClass(alias = "python-venv")
public class PythonVirtualenvPreparer extends BaseTargetPreparer {

    private static final String PIP = "pip3";
    protected static final String PYTHONPATH = "PYTHONPATH";
    private static final int BASE_TIMEOUT = 1000 * 60;

    @Option(name = "venv-dir", description = "path of an existing virtualenv to use")
    private File mVenvDir = null;

    @Option(name = "requirements-file", description = "pip-formatted requirements file")
    private File mRequirementsFile = null;

    @Option(name = "dep-module", description = "modules which need to be installed by pip")
    private List<String> mDepModules = new ArrayList<>();

    IRunUtil mRunUtil = new RunUtil();
    String mPip = PIP;
    private File mTmpVenvDir = null;

    @Override
    public void setUp(TestInformation testInformation) throws TargetSetupError {
        if (isDisabled()) {
            CLog.i("Skipping PythonVirtualenvPreparer");
            return;
        }
        startVirtualenv(testInformation.getBuildInfo(), testInformation.getDevice());
        installDeps(testInformation.getBuildInfo(), testInformation.getDevice());
    }

    protected void installDeps(IBuildInfo buildInfo, ITestDevice device) throws TargetSetupError {
        boolean hasDependencies = false;
        mPip = getPipPath();
        if (mRequirementsFile != null) {
            CommandResult c = mRunUtil.runTimedCmd(BASE_TIMEOUT * 5, mPip,
                    "install", "-r", mRequirementsFile.getAbsolutePath());
            if (c.getStatus() != CommandStatus.SUCCESS) {
                CLog.e("Installing dependencies from %s failed",
                        mRequirementsFile.getAbsolutePath());
                throw new TargetSetupError("Failed to install dependencies with pip",
                        device.getDeviceDescriptor());
            }
            hasDependencies = true;
        }
        if (!mDepModules.isEmpty()) {
            for (String dep : mDepModules) {
                CLog.i("Attempting installation of %s", dep);
                CommandResult c = mRunUtil.runTimedCmd(BASE_TIMEOUT * 5, mPip,
                        "install", dep);
                if (c.getStatus() != CommandStatus.SUCCESS) {
                    CLog.e("Installing %s failed", dep);
                    throw new TargetSetupError("Failed to install dependencies with pip",
                            device.getDeviceDescriptor());
                } else {
                    CLog.d("Successfullly installed %s.", dep);
                    CLog.d("Stdout: %s", c.getStdout());
                }
                hasDependencies = true;
            }
        }
        if (!hasDependencies) {
            CLog.i("No dependencies to install");
        } else {
            // make the install directory of new packages available to other classes that
            // receive the build
            // TODO(b/166688272): Get install location from pip rather than hard code it.
            buildInfo.setFile(
                    PYTHONPATH,
                    new File(mTmpVenvDir, "local/lib/python3.8/site-packages"),
                    buildInfo.getBuildId());
            buildInfo.setFile("VIRTUAL_ENV", mTmpVenvDir, buildInfo.getBuildId());
        }
    }

    protected void startVirtualenv(IBuildInfo buildInfo, ITestDevice device)
            throws TargetSetupError {
        FileUtil.recursiveDelete(mTmpVenvDir);
        mTmpVenvDir = null;
        if (mVenvDir != null) {
            mTmpVenvDir = FileUtil.getFileForPath(CurrentInvocation.getWorkFolder(),
                    buildInfo.getTestTag() + "-virtualenv");
            try {
                FileUtil.symlinkFile(mVenvDir, mTmpVenvDir);
            } catch (IOException e) {
                CLog.e("Failed to create temp directory for virtualenv");
                throw new TargetSetupError("Error creating virtualenv", e,
                        device.getDeviceDescriptor());
            }
            CLog.i("Using existing virtualenv based at %s", mVenvDir.getAbsolutePath());
            PythonVirtualenvHelper.activate(mRunUtil, mTmpVenvDir);
            return;
        }
        checkVirtualenvVersion(device);
        try {
            mTmpVenvDir =
                    FileUtil.createNamedTempDir(
                            CurrentInvocation.getWorkFolder(),
                            buildInfo.getTestTag() + "-virtualenv");
            CommandResult c =
                    mRunUtil.runTimedCmd(BASE_TIMEOUT, "virtualenv", mTmpVenvDir.getAbsolutePath());
            if (c.getStatus() != CommandStatus.SUCCESS) {
                CLog.e("Creating virtual environment at %s failed.", mTmpVenvDir.getAbsoluteFile());
                CLog.e(
                        "Status: %s\nStdout: %s\nStderr: %s",
                        c.getStatus(), c.getStdout(), c.getStderr());
                throw new TargetSetupError(
                        String.format(
                                "Failed to create virtual environment. Error:\n%s", c.getStderr()),
                        device.getDeviceDescriptor());
            }
            CLog.i("Created a virtualenv based at %s", mTmpVenvDir.getAbsolutePath());
            PythonVirtualenvHelper.activate(mRunUtil, mTmpVenvDir);
        } catch (IOException e) {
            CLog.e("Failed to create temp directory for virtualenv");
            throw new TargetSetupError("Error creating virtualenv", e,
                    device.getDeviceDescriptor());
        }
    }

    protected void addDepModule(String module) {
        mDepModules.add(module);
    }

    protected void setRequirementsFile(File f) {
        mRequirementsFile = f;
    }

    private String getPipPath() {
        if (mTmpVenvDir == null || !mTmpVenvDir.exists()) {
            return null;
        }
        String virtualenvPath = mTmpVenvDir.getAbsolutePath();
        File pipFile = new File(PythonVirtualenvHelper.getPythonBinDir(virtualenvPath), PIP);
        pipFile.setExecutable(true);
        return pipFile.getAbsolutePath();
    }

    /** Check if the virtualenv on the host is too old. */
    private void checkVirtualenvVersion(ITestDevice device) throws TargetSetupError {
        CommandResult result = mRunUtil.runTimedCmd(BASE_TIMEOUT, "virtualenv", "--version");
        if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
            throw new TargetSetupError(
                    "Failed to run `virtualenv --version`. Reason:\n" + result.getStderr(),
                    device.getDeviceDescriptor());
        }
        String stdout = result.getStdout(); // should start with 'virtualenv <version> from'
        if (stdout.contains("command not found")) {
            throw new TargetSetupError(
                    "virtualenv is not installed.", device.getDeviceDescriptor());
        }
        CLog.d("Output from virtualenv --version: %s", stdout);
        String[] split = stdout.split(" ");
        if (split.length < 2) {
            throw new TargetSetupError(
                    String.format(
                            "Something is wrong with your installed virtualenv version: %s",
                            stdout),
                    device.getDeviceDescriptor());
        }
        String version = split[1];
        int majorVersion = Integer.parseInt(version.split("\\.")[0]);
        if (majorVersion < 20) {
            throw new TargetSetupError(
                    "virtualenv is too old. Required: >=20.0.1, yours: " + version,
                    device.getDeviceDescriptor());
        }
    }
}