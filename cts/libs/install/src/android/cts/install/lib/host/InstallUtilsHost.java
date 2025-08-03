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

package android.cts.install.lib.host;

import static com.android.cts.shim.lib.ShimPackage.SHIM_APEX_PACKAGE_NAME;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.android.ddmlib.Log;
import com.android.tradefed.build.BuildInfoKey;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.SystemUtil;

import com.google.common.base.Stopwatch;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities to facilitate installation in tests on host side.
 */
public class InstallUtilsHost {
    private static final String TAG = InstallUtilsHost.class.getSimpleName();
    private static final String APEX_INFO_EXTRACT_REGEX =
            ".*package:\\sname='(\\S+)\\'\\sversionCode='(\\d+)'\\s.*";

    private final IRunUtil mRunUtil = new RunUtil();
    private BaseHostJUnit4Test mTest = null;
    private TestInformation mTestInfo = null;

    public InstallUtilsHost(BaseHostJUnit4Test test) {
        mTest = test;
    }

    public InstallUtilsHost(TestInformation testInfo) {
        assertThat(testInfo).isNotNull();
        mTestInfo = testInfo;
    }

    /**
     * Return {@code true} if and only if device supports updating apex.
     */
    public boolean isApexUpdateSupported() throws Exception {
        return getTestInfo().getDevice().getBooleanProperty("ro.apex.updatable", false);
    }

    /**
     * Return {@code true} if and only if device supports file system checkpoint.
     */
    public boolean isCheckpointSupported() throws Exception {
        CommandResult result = getTestInfo().getDevice().executeShellV2Command(
                "sm supports-checkpoint");
        assertWithMessage("Failed to check if file system checkpoint is supported : %s",
                result.getStderr()).that(result.getStatus()).isEqualTo(CommandStatus.SUCCESS);
        return "true".equals(result.getStdout().trim());
    }

    /**
     * Uninstalls a shim apex only if it's latest version is installed on /data partition (i.e.
     * it has a version higher than {@code 1}).
     *
     * <p>This is purely to optimize tests run time. Since uninstalling an apex requires a reboot,
     * and only a small subset of tests successfully install an apex, this code avoids ~10
     * unnecessary reboots.
     */
    public void uninstallShimApexIfNecessary() throws Exception {
        final ITestDevice.ApexInfo shimApex = getShimApex().orElseThrow(
                () -> new AssertionError("Can't find " + SHIM_APEX_PACKAGE_NAME));
        if (shimApex.sourceDir.startsWith("/system")) {
            // System version is active, nothing to uninstall.
            return;
        }
        // Non system version is active, need to uninstall it and reboot the device.
        Log.i(TAG, "Uninstalling shim apex");
        final String errorMessage =
                getTestInfo().getDevice().uninstallPackage(SHIM_APEX_PACKAGE_NAME);
        if (errorMessage != null) {
            Log.e(TAG, "Failed to uninstall " + SHIM_APEX_PACKAGE_NAME + " : " + errorMessage);
        } else {
            getTestInfo().getDevice().reboot();
            final ITestDevice.ApexInfo shim = getShimApex().orElseThrow(
                    () -> new AssertionError("Can't find " + SHIM_APEX_PACKAGE_NAME));
            assertThat(shim.versionCode).isEqualTo(1L);
            assertThat(shim.sourceDir).startsWith("/system");
        }
    }

    /**
     * Returns the active shim apex as optional.
     */
    public Optional<ITestDevice.ApexInfo> getShimApex() throws DeviceNotAvailableException {
        return getTestInfo().getDevice().getActiveApexes().stream().filter(
                apex -> apex.name.equals(SHIM_APEX_PACKAGE_NAME)).findAny();
    }

    /**
     * Retrieve package name and version code from test apex file.
     *
     * @param apex input apex file to retrieve the info from
     */
    public ITestDevice.ApexInfo getApexInfo(File apex) {
        String aaptOutput = runCmd(String.format("aapt dump badging %s", apex.getAbsolutePath()));
        String[] lines = aaptOutput.split("\n");
        Pattern p = Pattern.compile(APEX_INFO_EXTRACT_REGEX);
        for (String l : lines) {
            Matcher m = p.matcher(l);
            if (m.matches()) {
                return new ITestDevice.ApexInfo(m.group(1), Long.parseLong(m.group(2)));
            }
        }
        return null;
    }

    /**
     * Installs packages using staged install flow and waits for pre-reboot verification to complete
     */
    public String installStagedPackage(File pkg) throws Exception {
        return getTestInfo().getDevice().installPackage(pkg, false, "--staged");
    }

    /**
     * Installs packages using rebootless (non-staged) install flow and waits
     *     for verification to complete
     */
    public String installRebootlessPackage(File pkg) throws Exception {
        return getTestInfo().getDevice().installPackage(pkg, false, "--non-staged");
    }

    /**
     * Install multiple package at the same time
     */
    public void installApexes(String... filenames) throws Exception {
        String[] args = new String[filenames.length + 1];
        args[0] = "install-multi-package";
        for (int i = 0; i < filenames.length; i++) {
            args[i + 1] = getTestFile(filenames[i]).getAbsolutePath();
        }
        String stdout = getTestInfo().getDevice().executeAdbCommand(args);
        assertThat(stdout).isNotNull();
    }

    /**
     * Waits for given {@code timeout} for {@code filePath} to be deleted.
     */
    public void waitForFileDeleted(String filePath, Duration timeout) throws Exception {
        Stopwatch stopwatch = Stopwatch.createStarted();
        while (true) {
            if (!getTestInfo().getDevice().doesFileExist(filePath)) {
                return;
            }
            if (stopwatch.elapsed().compareTo(timeout) > 0) {
                break;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Timed out waiting for " + filePath + " to be deleted");
    }

    /**
     * Get the test file.
     *
     * @param testFileName name of the file
     */
    public File getTestFile(String testFileName) throws IOException {
        File testFile = null;

        final List<File> testCasesDirs = SystemUtil.getTestCasesDirs(getTestInfo().getBuildInfo());
        for (File testCasesDir : testCasesDirs) {
            testFile = searchTestFile(testCasesDir, testFileName);
            if (testFile != null) {
                return testFile;
            }
        }

        File hostLinkedDir = getTestInfo().getBuildInfo().getFile(
                BuildInfoKey.BuildInfoFileKey.HOST_LINKED_DIR);
        if (hostLinkedDir != null) {
            testFile = searchTestFile(hostLinkedDir, testFileName);
        }
        if (testFile != null) {
            return testFile;
        }

        // Find the file in the buildinfo.
        File buildInfoFile = getTestInfo().getBuildInfo().getFile(testFileName);
        if (buildInfoFile != null) {
            return buildInfoFile;
        }

        throw new IOException("Cannot find " + testFileName);
    }

    /**
     * Searches the file with the given name under the given directory, returns null if not found.
     */
    private File searchTestFile(File baseSearchFile, String testFileName) {
        if (baseSearchFile != null && baseSearchFile.isDirectory()) {
            File testFile = FileUtil.findFile(baseSearchFile, testFileName);
            if (testFile != null && testFile.isFile()) {
                return testFile;
            }
        }
        return null;
    }

    private String runCmd(String cmd) {
        Log.d("About to run command: %s", cmd);
        CommandResult result = mRunUtil.runTimedCmd(1000 * 60 * 5, cmd.split("\\s+"));
        assertThat(result).isNotNull();
        assertWithMessage(String.format("Command %s failed", cmd)).that(result.getStatus())
                .isEqualTo(CommandStatus.SUCCESS);
        Log.d("output:\n%s", result.getStdout());
        return result.getStdout();
    }

    private TestInformation getTestInfo() {
        if (mTestInfo == null) {
            mTestInfo = mTest.getTestInformation();
            assertThat(mTestInfo).isNotNull();
        }
        return mTestInfo;
    }
}
