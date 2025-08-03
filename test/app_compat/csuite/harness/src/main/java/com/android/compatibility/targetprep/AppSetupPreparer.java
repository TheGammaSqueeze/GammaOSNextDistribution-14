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

package com.android.compatibility.targetprep;

import static com.google.common.base.Preconditions.checkArgument;

import com.android.csuite.core.SystemPackageUninstaller;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestLoggerReceiver;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.TestAppInstallSetup;
import com.android.tradefed.util.AaptParser.AaptVersion;
import com.android.tradefed.util.ZipUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** A Tradefed preparer that downloads and installs an app on the target device. */
public final class AppSetupPreparer implements ITargetPreparer, ITestLoggerReceiver {

    @VisibleForTesting
    static final String OPTION_WAIT_FOR_DEVICE_AVAILABLE_SECONDS =
            "wait-for-device-available-seconds";

    @VisibleForTesting
    static final String OPTION_EXPONENTIAL_BACKOFF_MULTIPLIER_SECONDS =
            "exponential-backoff-multiplier-seconds";

    @VisibleForTesting static final String OPTION_TEST_FILE_NAME = "test-file-name";
    @VisibleForTesting static final String OPTION_INSTALL_ARG = "install-arg";
    @VisibleForTesting static final String OPTION_SETUP_TIMEOUT_MILLIS = "setup-timeout-millis";
    @VisibleForTesting static final String OPTION_MAX_RETRY = "max-retry";
    @VisibleForTesting static final String OPTION_AAPT_VERSION = "aapt-version";
    @VisibleForTesting static final String OPTION_INCREMENTAL_INSTALL = "incremental";
    @VisibleForTesting static final String OPTION_INCREMENTAL_FILTER = "incremental-block-filter";
    @VisibleForTesting static final String OPTION_SAVE_APKS = "save-apks";

    @VisibleForTesting
    static final String OPTION_INCREMENTAL_TIMEOUT_SECS = "incremental-install-timeout-secs";

    @Option(name = "package-name", description = "Package name of testing app.")
    private String mPackageName;

    @Option(
            name = OPTION_TEST_FILE_NAME,
            description = "the name of an apk file to be installed on device. Can be repeated.")
    private final List<File> mTestFiles = new ArrayList<>();

    @Option(name = OPTION_AAPT_VERSION, description = "The version of AAPT for APK parsing.")
    private AaptVersion mAaptVersion = AaptVersion.AAPT2;

    @Option(
            name = OPTION_INSTALL_ARG,
            description =
                    "Additional arguments to be passed to install command, "
                            + "including leading dash, e.g. \"-d\"")
    private final List<String> mInstallArgs = new ArrayList<>();

    @Option(
            name = OPTION_INCREMENTAL_INSTALL,
            description = "Enable packages to be installed incrementally.")
    private boolean mIncrementalInstallation = false;

    @Option(
            name = OPTION_INCREMENTAL_FILTER,
            description = "Specify percentage of blocks to filter.")
    private double mBlockFilterPercentage = 0.0;

    @Option(
            name = OPTION_INCREMENTAL_TIMEOUT_SECS,
            description = "Specify timeout of incremental installation.")
    private int mIncrementalTimeout = 1800;

    @Option(name = OPTION_MAX_RETRY, description = "Max number of retries upon TargetSetupError.")
    private int mMaxRetry = 0;

    @Option(
            name = OPTION_EXPONENTIAL_BACKOFF_MULTIPLIER_SECONDS,
            description =
                    "The exponential backoff multiplier for retries in seconds. "
                            + "A value n means the preparer will wait for n^(retry_count) "
                            + "seconds between retries.")
    private int mExponentialBackoffMultiplierSeconds = 0;

    @Option(
            name = OPTION_WAIT_FOR_DEVICE_AVAILABLE_SECONDS,
            description =
                    "Timeout value for waiting for device available in seconds. "
                            + "A negative value means not to check device availability.")
    private int mWaitForDeviceAvailableSeconds = -1;

    @Option(
            name = OPTION_SETUP_TIMEOUT_MILLIS,
            description =
                    "Timeout value for a setUp operation. "
                            + "Note that the timeout is not a global timeout and will "
                            + "be applied to each retry attempt.")
    private long mSetupOnceTimeoutMillis = TimeUnit.MINUTES.toMillis(10);

    @Option(
            name = OPTION_SAVE_APKS,
            description = "Whether to save the input APKs into test output.")
    private boolean mSaveApks = false;

    private final TestAppInstallSetup mTestAppInstallSetup;
    private final Sleeper mSleeper;
    private final TimeLimiter mTimeLimiter =
            SimpleTimeLimiter.create(Executors.newCachedThreadPool());
    private ITestLogger mTestLogger;

    public AppSetupPreparer() {
        this(new TestAppInstallSetup(), Sleepers.DefaultSleeper.INSTANCE);
    }

    @VisibleForTesting
    public AppSetupPreparer(TestAppInstallSetup testAppInstallSetup, Sleeper sleeper) {
        mTestAppInstallSetup = testAppInstallSetup;
        mSleeper = sleeper;
    }

    /** {@inheritDoc} */
    @Override
    public void setUp(TestInformation testInfo)
            throws DeviceNotAvailableException, BuildError, TargetSetupError {
        checkArgumentNonNegative(mMaxRetry, OPTION_MAX_RETRY);
        checkArgumentNonNegative(
                mExponentialBackoffMultiplierSeconds,
                OPTION_EXPONENTIAL_BACKOFF_MULTIPLIER_SECONDS);
        checkArgumentNonNegative(mSetupOnceTimeoutMillis, OPTION_SETUP_TIMEOUT_MILLIS);

        if (mSaveApks) {
            mTestFiles.forEach(
                    path -> {
                        if (!path.exists()) {
                            CLog.w(
                                    "Skipping saving %s as the path might be a relative path.",
                                    path);
                            return;
                        }
                        try {
                            File outputZip = ZipUtil.createZip(path);
                            mTestLogger.testLog(
                                    mPackageName + "-input_apk-" + path.getName(),
                                    LogDataType.ZIP,
                                    new FileInputStreamSource(outputZip));
                        } catch (IOException e) {
                            CLog.e("Failed to zip the output directory: " + e);
                        }
                    });
        }

        int runCount = 0;
        while (true) {
            TargetSetupError currentException;
            try {
                runCount++;

                ITargetPreparer handler =
                        mTimeLimiter.newProxy(
                                new ITargetPreparer() {
                                    @Override
                                    public void setUp(TestInformation testInfo)
                                            throws DeviceNotAvailableException, BuildError,
                                                    TargetSetupError {
                                        setUpOnce(testInfo);
                                    }
                                },
                                ITargetPreparer.class,
                                mSetupOnceTimeoutMillis,
                                TimeUnit.MILLISECONDS);
                handler.setUp(testInfo);

                break;
            } catch (TargetSetupError e) {
                currentException = e;
            } catch (UncheckedTimeoutException e) {
                currentException =
                        new TargetSetupError(
                                e.getMessage(), e, testInfo.getDevice().getDeviceDescriptor());
            }

            waitForDeviceAvailable(testInfo.getDevice());
            if (runCount > mMaxRetry) {
                throw currentException;
            }
            CLog.w("setUp failed: %s. Run count: %d. Retrying...", currentException, runCount);

            try {
                mSleeper.sleep(
                        Duration.ofSeconds(
                                (int) Math.pow(mExponentialBackoffMultiplierSeconds, runCount)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TargetSetupError(
                        e.getMessage(), e, testInfo.getDevice().getDeviceDescriptor());
            }
        }
    }

    private void setUpOnce(TestInformation testInfo)
            throws DeviceNotAvailableException, BuildError, TargetSetupError {
        mTestAppInstallSetup.setAaptVersion(mAaptVersion);

        try {
            OptionSetter setter = new OptionSetter(mTestAppInstallSetup);
            setter.setOptionValue("incremental", String.valueOf(mIncrementalInstallation));
            setter.setOptionValue(
                    "incremental-block-filter", String.valueOf(mBlockFilterPercentage));
            setter.setOptionValue(
                    "incremental-install-timeout-secs", String.valueOf(mIncrementalTimeout));
        } catch (ConfigurationException e) {
            throw new TargetSetupError(
                    e.getMessage(), e, testInfo.getDevice().getDeviceDescriptor());
        }

        if (mPackageName != null) {
            SystemPackageUninstaller.uninstallPackage(mPackageName, testInfo.getDevice());
        }

        for (File testFile : mTestFiles) {
            CLog.d("Adding apk path %s for installation.", testFile);
            mTestAppInstallSetup.addTestFile(testFile);
        }

        for (String installArg : mInstallArgs) {
            mTestAppInstallSetup.addInstallArg(installArg);
        }

        mTestAppInstallSetup.setUp(testInfo);
    }

    /** {@inheritDoc} */
    @Override
    public void tearDown(TestInformation testInfo, Throwable e) throws DeviceNotAvailableException {
        mTestAppInstallSetup.tearDown(testInfo, e);
    }

    private void waitForDeviceAvailable(ITestDevice device) throws DeviceNotAvailableException {
        if (mWaitForDeviceAvailableSeconds < 0) {
            return;
        }

        device.waitForDeviceAvailable(1000L * mWaitForDeviceAvailableSeconds);
    }

    private void checkArgumentNonNegative(long val, String name) {
        checkArgument(val >= 0, "%s (%s) must not be negative", name, val);
    }

    @VisibleForTesting
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private static class Sleepers {
        enum DefaultSleeper implements Sleeper {
            INSTANCE;

            @Override
            public void sleep(Duration duration) throws InterruptedException {
                Thread.sleep(duration.toMillis());
            }
        }

        private Sleepers() {}
    }

    @Override
    public void setTestLogger(ITestLogger testLogger) {
        mTestLogger = testLogger;
    }
}
