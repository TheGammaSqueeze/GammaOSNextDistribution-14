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

package com.android.art.tests;

import com.android.csuite.core.ApkInstaller;
import com.android.csuite.core.ApkInstaller.ApkInstallerException;
import com.android.csuite.core.BlankScreenDetectorWithSameColorRectangle;
import com.android.csuite.core.BlankScreenDetectorWithSameColorRectangle.BlankScreen;
import com.android.csuite.core.DeviceUtils;
import com.android.csuite.core.DeviceUtils.DeviceTimestamp;
import com.android.csuite.core.DeviceUtils.DeviceUtilsException;
import com.android.csuite.core.DeviceUtils.DropboxEntry;
import com.android.csuite.core.DeviceUtils.RunnableThrowingDeviceNotAvailable;
import com.android.csuite.core.TestUtils;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestLogData;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.RunUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

/** A test that verifies that a single app can be successfully launched. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class AppCompileLaunchTest extends BaseHostJUnit4Test {
    @VisibleForTesting static final String SCREENSHOT_AFTER_LAUNCH = "screenshot-after-launch";
    @VisibleForTesting static final String COLLECT_APP_VERSION = "collect-app-version";
    @VisibleForTesting static final String COLLECT_GMS_VERSION = "collect-gms-version";
    @VisibleForTesting static final String RECORD_SCREEN = "record-screen";
    @Rule public TestLogData mLogData = new TestLogData();
    private ApkInstaller mApkInstaller;
    private boolean mIsLastTestPass;
    private boolean mIsApkSaved = false;

    @Option(name = RECORD_SCREEN, description = "Whether to record screen during test.")
    private boolean mRecordScreen;

    @Option(
            name = SCREENSHOT_AFTER_LAUNCH,
            description = "Whether to take a screenshost after a package is launched.")
    private boolean mScreenshotAfterLaunch;

    @Option(
            name = COLLECT_APP_VERSION,
            description =
                    "Whether to collect package version information and store the information in"
                            + " test log files.")
    private boolean mCollectAppVersion;

    @Option(
            name = COLLECT_GMS_VERSION,
            description =
                    "Whether to collect GMS core version information and store the information in"
                            + " test log files.")
    private boolean mCollectGmsVersion;

    @Option(
            name = "install-apk",
            description =
                    "The path to an apk file or a directory of apk files of a singe package to be"
                            + " installed on device. Can be repeated.")
    private final List<File> mApkPaths = new ArrayList<>();

    @Option(
            name = "install-arg",
            description = "Arguments for the 'adb install-multiple' package installation command.")
    private final List<String> mInstallArgs = new ArrayList<>();

    @Option(
            name = "save-apk-when",
            description = "When to save apk files to the test result artifacts.")
    private TestUtils.TakeEffectWhen mSaveApkWhen = TestUtils.TakeEffectWhen.NEVER;

    @Option(name = "package-name", description = "Package name of testing app.")
    private String mPackageName;

    @Option(
            name = "app-launch-timeout-ms",
            description = "Time to wait for app to launch in msecs.")
    private int mAppLaunchTimeoutMs = 15000;

    @Option(
            name = "blank-screen-same-color-area-threshold",
            description =
                    "Percentage of the screen which, if occupied by a same-color rectangle "
                            + "area, indicates that the app has reached a blank screen.")
    private double mBlankScreenSameColorThreshold = -1;

    @Test
    public void testCompileLaunch() throws Throwable {
        Assert.assertNotNull("Package name cannot be null", mPackageName);
        mIsLastTestPass = false;

        DeviceUtils deviceUtils = DeviceUtils.getInstance(getDevice());
        TestUtils testUtils = TestUtils.getInstance(getTestInformation(), mLogData);

        mApkInstaller = ApkInstaller.getInstance(getDevice());
        try {
        mApkInstaller.install(
                mApkPaths.stream().map(File::toPath).collect(Collectors.toList()), mInstallArgs);


        if (mCollectGmsVersion) {
            testUtils.collectGmsVersion(mPackageName);
        }

        if (mCollectAppVersion) {
            testUtils.collectAppVersion(mPackageName);
        }

        deviceUtils.freezeRotation();
        } catch (ApkInstallerException | IOException | DeviceNotAvailableException e) {
            CLog.e("Skipping the test on %s as the setup failed: %s", mPackageName, e.getMessage());
            // Do not throw to fail the test here as it's not compile related
            Assume.assumeNoException(e);
        }

        Throwable testFailureThrowable = null;

        CommandResult cmdResult =
                getDevice().executeShellV2Command("cmd package compile -m speed " + mPackageName);
        Assert.assertEquals(
                "Failed to execute compile command: " + cmdResult,
                cmdResult.getStatus(),
                CommandStatus.SUCCESS);

        try {
            doTestAppCrash(false);
            mIsLastTestPass = true;
        } catch (DeviceNotAvailableException e) {
            throw e;
        } catch (Throwable e) {
            testFailureThrowable = e;
        }

        if (mScreenshotAfterLaunch) {
            testUtils.collectScreenshot(mPackageName);
        }

        if (mIsLastTestPass) {
            return;
        }

        CLog.i("Test on %s failed. Starting verification without speed compile.", mPackageName);

        // No need to call uninstall command as the install command does uninstall first anyway
        mApkInstaller.install(
                mApkPaths.stream().map(File::toPath).collect(Collectors.toList()), mInstallArgs);

        try {
            doTestAppCrash(true);
        } catch (Throwable e) {
            CLog.i(
                    "Ignoring test result for %s as the test failed both with and without art"
                            + " compile.",
                    mPackageName);
            mIsLastTestPass = true;
            // Do not throw to fail the test here as it's not compile related
            Assume.assumeNoException(e);
        }

        throw testFailureThrowable;
    }

    private void doTestAppCrash(boolean isVerificationRun)
            throws DeviceNotAvailableException, IOException {
        CLog.d("Launching package: %s.", mPackageName);

        DeviceUtils deviceUtils = DeviceUtils.getInstance(getDevice());
        TestUtils testUtils = TestUtils.getInstance(getTestInformation(), mLogData);

        try {
            if (!deviceUtils.isPackageInstalled(mPackageName)) {
                Assert.fail(
                        "Package "
                                + mPackageName
                                + " is not installed on the device. Aborting the test.");
            }
        } catch (DeviceUtilsException e) {
            Assert.fail("Failed to check the installed package list: " + e.getMessage());
        }

        AtomicReference<DeviceTimestamp> startTime = new AtomicReference<>();
        AtomicReference<DeviceTimestamp> videoStartTime = new AtomicReference<>();

        RunnableThrowingDeviceNotAvailable launchJob =
                () -> {
                    startTime.set(deviceUtils.currentTimeMillis());
                    try {
                        deviceUtils.launchPackage(mPackageName);
                    } catch (DeviceUtilsException e) {
                        Assert.fail(
                                "Failed to launch package " + mPackageName + ": " + e.getMessage());
                    }

                    CLog.d(
                            "Waiting %s milliseconds for the app to launch fully.",
                            mAppLaunchTimeoutMs);
                    RunUtil.getDefault().sleep(mAppLaunchTimeoutMs);
                };

        if (mRecordScreen && !isVerificationRun) {
            testUtils.collectScreenRecord(
                    launchJob,
                    mPackageName,
                    videoStartTimeOnDevice -> videoStartTime.set(videoStartTimeOnDevice));
        } else {
            launchJob.run();
        }

        CLog.d("Completed launching package: %s", mPackageName);
        DeviceTimestamp endTime = deviceUtils.currentTimeMillis();

        try {
            List<DropboxEntry> crashEntries =
                    deviceUtils.getDropboxEntries(
                            DeviceUtils.DROPBOX_APP_CRASH_TAGS,
                            mPackageName,
                            startTime.get(),
                            endTime);
            String crashLog =
                    testUtils.compileTestFailureMessage(
                            mPackageName, crashEntries, true, videoStartTime.get());
            if (crashLog != null) {
                Assert.fail(crashLog);
            }
        } catch (IOException e) {
            Assert.fail("Error while getting dropbox crash log: " + e);
        }

        if (mBlankScreenSameColorThreshold > 0) {
            BufferedImage screen;
            try (InputStreamSource screenShot =
                    testUtils.getTestInformation().getDevice().getScreenshot()) {
                Preconditions.checkNotNull(screenShot);
                screen = ImageIO.read(screenShot.createInputStream());
            }
            BlankScreen blankScreen =
                    BlankScreenDetectorWithSameColorRectangle.getBlankScreen(screen);
            double blankScreenPercent = blankScreen.getBlankScreenPercent();
            if (blankScreenPercent > mBlankScreenSameColorThreshold) {
                if (!isVerificationRun) {
                    BlankScreenDetectorWithSameColorRectangle.saveBlankScreenArtifact(
                            mPackageName,
                            blankScreen,
                            testUtils.getTestArtifactReceiver(),
                            testUtils.getTestInformation().getDevice().getSerialNumber());
                }
                Assert.fail(
                        String.format(
                                "Blank screen detected with same-color rectangle area percentage of"
                                        + " %.2f%%",
                                blankScreenPercent * 100));
            }
        }
    }

    @After
    public void tearDown() {
        DeviceUtils deviceUtils = DeviceUtils.getInstance(getDevice());
        TestUtils testUtils = TestUtils.getInstance(getTestInformation(), mLogData);

        if (!mIsApkSaved) {
            mIsApkSaved =
                    testUtils.saveApks(mSaveApkWhen, mIsLastTestPass, mPackageName, mApkPaths);
        }

        try {
            deviceUtils.stopPackage(mPackageName);
            deviceUtils.unfreezeRotation();
            mApkInstaller.uninstallAllInstalledPackages();
        } catch (ApkInstallerException | DeviceNotAvailableException e) {
            CLog.e("Failed to tearDown the test for %s", mPackageName);
            // Do not throw to fail the test here as it's not compile related
            Assume.assumeNoException(e);
        }
    }
}
