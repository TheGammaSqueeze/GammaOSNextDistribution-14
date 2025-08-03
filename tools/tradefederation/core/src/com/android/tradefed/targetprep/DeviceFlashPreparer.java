/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.device.NullDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.host.IHostOptions;
import com.android.tradefed.host.IHostOptions.PermitLimitType;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.invoker.logger.CurrentInvocation.IsolationGrade;
import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.invoker.tracing.CloseableTraceScope;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.retry.BaseRetryDecision;
import com.android.tradefed.targetprep.IDeviceFlasher.UserDataFlashOption;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.image.DeviceImageTracker;
import com.android.tradefed.util.image.IncrementalImageUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/** A {@link ITargetPreparer} that flashes an image on physical Android hardware. */
public abstract class DeviceFlashPreparer extends BaseTargetPreparer
        implements IConfigurationReceiver {

    private static final int BOOT_POLL_TIME_MS = 5 * 1000;
    private static final long SNAPSHOT_CANCEL_TIMEOUT = 20000L;

    @Option(
        name = "device-boot-time",
        description = "max time to wait for device to boot.",
        isTimeVal = true
    )
    private long mDeviceBootTime = 5 * 60 * 1000;

    @Option(name = "userdata-flash", description =
        "specify handling of userdata partition.")
    private UserDataFlashOption mUserDataFlashOption = UserDataFlashOption.FLASH;

    @Option(name = "force-system-flash", description =
        "specify if system should always be flashed even if already running desired build.")
    private boolean mForceSystemFlash = false;

    /*
     * A temporary workaround for special builds. Should be removed after changes from build team.
     * Bug: 18078421
     */
    @Deprecated
    @Option(
            name = "skip-post-flash-flavor-check",
            description = "specify if system flavor should not be checked after flash")
    private boolean mSkipPostFlashFlavorCheck = false;

    /*
     * Used for update testing
     */
    @Option(name = "skip-post-flash-build-id-check", description =
            "specify if build ID should not be checked after flash")
    private boolean mSkipPostFlashBuildIdCheck = false;

    @Option(name = "wipe-skip-list", description =
        "list of /data subdirectories to NOT wipe when doing UserDataFlashOption.TESTS_ZIP")
    private Collection<String> mDataWipeSkipList = new ArrayList<>();

    /**
     * @deprecated use host-options:concurrent-flasher-limit.
     */
    @Deprecated
    @Option(name = "concurrent-flasher-limit", description =
        "No-op, do not use. Left for backwards compatibility.")
    private Integer mConcurrentFlasherLimit = null;

    @Option(name = "skip-post-flashing-setup",
            description = "whether or not to skip post-flashing setup steps")
    private boolean mSkipPostFlashingSetup = false;

    @Option(name = "wipe-timeout",
            description = "the timeout for the command of wiping user data.", isTimeVal = true)
    private long mWipeTimeout = 4 * 60 * 1000;

    @Option(
        name = "fastboot-flash-option",
        description = "additional options to pass with fastboot flash/update command."
    )
    private Collection<String> mFastbootFlashOptions = new ArrayList<>();

    @Option(
            name = "flash-ramdisk",
            description =
                    "flashes ramdisk (usually on boot partition) in addition to "
                            + "regular system image")
    private boolean mShouldFlashRamdisk = false;

    @Option(
            name = "ramdisk-partition",
            description =
                    "the partition (such as boot, vendor_boot) that ramdisk image "
                            + "should be flashed to")
    private String mRamdiskPartition = "boot";

    @Option(
            name = "cancel-ota-snapshot",
            description = "In case an OTA snapshot is in progress, cancel it.")
    private boolean mCancelSnapshot = false;

    @Option(
            name = "incremental-flashing",
            description = "Leverage the incremental flashing feature for device update.")
    private boolean mUseIncrementalFlashing = false;

    @Option(
            name = "force-disable-incremental-flashing",
            description = "Ignore HostOptions and disable the feature if true.")
    private boolean mForceDisableIncrementalFlashing = false;

    @Option(
            name = "create-snapshot-binary",
            description = "Override the create_snapshot binary for incremental flashing.")
    private File mCreateSnapshotBinary = null;

    @Option(
            name = "allow-incremental-same-build",
            description = "Allow doing incremental update on same build.")
    private boolean mAllowIncrementalOnSameBuild = false;

    @Option(
            name = "allow-incremental-cross-release",
            description = "Allow doing incremental update across release build configs.")
    private boolean mAllowIncrementalCrossRelease = false;

    @Option(
            name = "apply-snapshot",
            description =
                    "Whether to apply the snapshot after mounting it. "
                            + "This changes the baseline and does require reverting.")
    private boolean mApplySnapshot = false;

    @Option(
            name = "enforce-snapshot-completed",
            description = "Test mode was snapshot to ensure the logic was used and throw if not.")
    private boolean mEnforceSnapshotCompleted = false;

    private IncrementalImageUtil mIncrementalImageUtil;
    private IConfiguration mConfig;

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfig = configuration;
    }

    /**
     * Sets the device boot time
     * <p/>
     * Exposed for unit testing
     */
    void setDeviceBootTime(long bootTime) {
        mDeviceBootTime = bootTime;
    }

    /** Gets the device boot wait time */
    protected long getDeviceBootWaitTime() {
        return mDeviceBootTime;
    }

    /**
     * Gets the interval between device boot poll attempts.
     * <p/>
     * Exposed for unit testing
     */
    int getDeviceBootPollTimeMs() {
        return BOOT_POLL_TIME_MS;
    }

    /**
     * Gets the {@link IRunUtil} instance to use.
     * <p/>
     * Exposed for unit testing
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * Gets the {@link IHostOptions} instance to use.
     * <p/>
     * Exposed for unit testing
     */
    protected IHostOptions getHostOptions() {
        return GlobalConfiguration.getInstance().getHostOptions();
    }

    /**
     * Set the userdata-flash option
     *
     * @param flashOption
     */
    public void setUserDataFlashOption(UserDataFlashOption flashOption) {
        mUserDataFlashOption = flashOption;
    }

    /** Wrap the getBuildInfo so we have a change to override it for specific scenarios. */
    public IBuildInfo getBuild(TestInformation testInfo) {
        return testInfo.getBuildInfo();
    }

    /** {@inheritDoc} */
    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, DeviceNotAvailableException, BuildError {
        if (testInfo.getDevice().getIDevice() instanceof NullDevice) {
            CLog.i("Skipping device flashing, this is a null-device.");
            return;
        }
        ITestDevice device = testInfo.getDevice();
        IBuildInfo buildInfo = getBuild(testInfo);
        CLog.i("Performing setup on %s", device.getSerialNumber());
        if (!(buildInfo instanceof IDeviceBuildInfo)) {
            throw new IllegalArgumentException("Provided buildInfo is not a IDeviceBuildInfo");
        }
        IDeviceBuildInfo deviceBuild = (IDeviceBuildInfo) buildInfo;
        if (mShouldFlashRamdisk && deviceBuild.getRamdiskFile() == null) {
            throw new HarnessRuntimeException(
                    "ramdisk flashing enabled but no ramdisk file was found in build info",
                    InfraErrorIdentifier.CONFIGURED_ARTIFACT_NOT_FOUND);
        }
        // For debugging: log the original build from the device
        if (TestDeviceState.ONLINE.equals(testInfo.getDevice().getDeviceState())) {
            buildInfo.addBuildAttribute(
                    "original_build_fingerprint",
                    device.getProperty("ro.product.build.fingerprint"));
        }

        long queueTime = -1;
        long flashingTime = -1;
        long start = -1;
        // HostOptions can force the incremental flashing to true.
        if (getHostOptions().isIncrementalFlashingEnabled()) {
            mUseIncrementalFlashing = true;
        }
        if (getHostOptions().isOptOutOfIncrementalFlashing()) {
            mUseIncrementalFlashing = false;
        }
        if (mForceDisableIncrementalFlashing) {
            // The local option disable the feature, and skip tracking baseline
            // for this run to avoid tracking a potentially bad baseline.
            mUseIncrementalFlashing = false;
            // Do not keep a cache when we are about to override it
            DeviceImageTracker.getDefaultCache().invalidateTracking(device.getSerialNumber());
        }
        boolean useIncrementalFlashing = mUseIncrementalFlashing;
        if (useIncrementalFlashing) {
            boolean isIsolated = false;
            if (mConfig.getRetryDecision() instanceof BaseRetryDecision) {
                isIsolated =
                        IsolationGrade.FULLY_ISOLATED.equals(
                                ((BaseRetryDecision) mConfig.getRetryDecision())
                                        .getIsolationGrade());
            }
            mIncrementalImageUtil =
                    IncrementalImageUtil.initialize(
                            device,
                            deviceBuild,
                            mCreateSnapshotBinary,
                            isIsolated,
                            mAllowIncrementalCrossRelease,
                            mApplySnapshot);
            if (mIncrementalImageUtil == null) {
                useIncrementalFlashing = false;
            } else {
                if (mAllowIncrementalOnSameBuild) {
                    mIncrementalImageUtil.allowSameBuildFlashing();
                }
                if (TestDeviceState.ONLINE.equals(device.getDeviceState())) {
                    // No need to reboot yet, it will happen later in the sequence
                    String verityOutput = device.executeAdbCommand("enable-verity");
                    CLog.d("%s", verityOutput);
                }
            }
        }
        try {
            checkDeviceProductType(device, deviceBuild);
            device.setRecoveryMode(RecoveryMode.ONLINE);
            IDeviceFlasher flasher = createFlasher(device);
            flasher.setWipeTimeout(mWipeTimeout);
            // only surround fastboot related operations with flashing permit restriction
            try {
                flasher.overrideDeviceOptions(device);
                flasher.setUserDataFlashOption(mUserDataFlashOption);
                flasher.setForceSystemFlash(mForceSystemFlash);
                flasher.setDataWipeSkipList(mDataWipeSkipList);
                flasher.setShouldFlashRamdisk(mShouldFlashRamdisk);
                if (mShouldFlashRamdisk) {
                    flasher.setRamdiskPartition(mRamdiskPartition);
                }
                if (flasher instanceof FastbootDeviceFlasher) {
                    ((FastbootDeviceFlasher) flasher).setFlashOptions(mFastbootFlashOptions);
                    ((FastbootDeviceFlasher) flasher).setIncrementalFlashing(mIncrementalImageUtil);
                }
                start = System.currentTimeMillis();
                flasher.preFlashOperations(device, deviceBuild);
                // After preFlashOperations device should be in bootloader
                if (mCancelSnapshot && TestDeviceState.FASTBOOT.equals(device.getDeviceState())) {
                    CommandResult res =
                            device.executeFastbootCommand(
                                    SNAPSHOT_CANCEL_TIMEOUT, "snapshot-update", "cancel");
                    if (!CommandStatus.SUCCESS.equals(res.getStatus())) {
                        CLog.w(
                                "Failed to cancel snapshot: %s.\nstdout:%s\nstderr:%s",
                                res.getStatus(), res.getStdout(), res.getStderr());
                    }
                }

                try (CloseableTraceScope ignored =
                        new CloseableTraceScope("wait_for_flashing_permit")) {
                    // Only #flash is included in the critical section
                    getHostOptions().takePermit(PermitLimitType.CONCURRENT_FLASHER);
                    queueTime = System.currentTimeMillis() - start;
                    CLog.v(
                            "Flashing permit obtained after %ds",
                            TimeUnit.MILLISECONDS.toSeconds(queueTime));
                    InvocationMetricLogger.addInvocationMetrics(
                            InvocationMetricKey.FLASHING_PERMIT_LATENCY, queueTime);
                }
                // Don't allow interruptions during flashing operations.
                getRunUtil().allowInterrupt(false);
                start = System.currentTimeMillis();
                // Set flashing method as unknown here as a fallback, in case it wasn't overwritten
                // by subclass implementations
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.FLASHING_METHOD,
                        FlashingMethod.FASTBOOT_UNCATEGORIZED.toString());
                flasher.flash(device, deviceBuild);
            } catch (DeviceNotAvailableException | TargetSetupError | RuntimeException e) {
                // Clear tracking in case of error
                DeviceImageTracker.getDefaultCache().invalidateTracking(device.getSerialNumber());
                throw e;
            } finally {
                flashingTime = System.currentTimeMillis() - start;
                getHostOptions().returnPermit(PermitLimitType.CONCURRENT_FLASHER);
                flasher.postFlashOperations(device, deviceBuild);
                // report flashing status
                CommandStatus status = flasher.getSystemFlashingStatus();
                if (status == null) {
                    CLog.i("Skipped reporting metrics because system partitions were not flashed.");
                } else {
                    if (mIncrementalImageUtil != null) {
                        InvocationMetricLogger.addInvocationMetrics(
                                InvocationMetricKey.INCREMENTAL_FLASHING_TIME, flashingTime);
                    }
                    InvocationMetricLogger.addInvocationMetrics(
                            InvocationMetricKey.FLASHING_TIME, flashingTime);
                    reportFlashMetrics(buildInfo.getBuildBranch(), buildInfo.getBuildFlavor(),
                            buildInfo.getBuildId(), device.getSerialNumber(), queueTime,
                            flashingTime, status);
                }
            }
            if (mIncrementalImageUtil == null) {
                // only want logcat captured for current build, delete any accumulated log data
                device.clearLogcat();
            }
            if (mSkipPostFlashingSetup) {
                return;
            }
            // Temporary re-enable interruptable since the critical flashing operation is over.
            getRunUtil().allowInterrupt(true);
            device.waitForDeviceOnline();
            // device may lose date setting if wiped, update with host side date in case anything on
            // device side malfunction with an invalid date
            if (device.enableAdbRoot()) {
                device.setDate(null);
            }
            // Disable interrupt for encryption operation.
            getRunUtil().allowInterrupt(false);
            checkBuild(device, deviceBuild);
            // Once critical operation is done, we re-enable interruptable
            getRunUtil().allowInterrupt(true);
            try {
                boolean available = device.waitForDeviceAvailableInRecoverPath(mDeviceBootTime);
                if (!available) {
                    // Clear tracking in case of error
                    DeviceImageTracker.getDefaultCache()
                            .invalidateTracking(device.getSerialNumber());
                    throw new DeviceFailedToBootError(
                            String.format(
                                    "Device %s did not become available after flashing %s",
                                    device.getSerialNumber(), deviceBuild.getDeviceBuildId()),
                            device.getDeviceDescriptor(),
                            DeviceErrorIdentifier.ERROR_AFTER_FLASHING);
                }
            } catch (DeviceNotAvailableException e) {
                // Clear tracking in case of error
                DeviceImageTracker.getDefaultCache().invalidateTracking(device.getSerialNumber());
                // Assume this is a build problem
                throw new DeviceFailedToBootError(
                        String.format(
                                "Device %s did not become available after flashing %s",
                                device.getSerialNumber(), deviceBuild.getDeviceBuildId()),
                        device.getDeviceDescriptor(),
                        e,
                        DeviceErrorIdentifier.ERROR_AFTER_FLASHING);
            }
            device.postBootSetup();
            // In case success with full flashing
            if (!getHostOptions().isOptOutOfIncrementalFlashing()) {
                boolean moveBaseLine = true;
                if (!mUseIncrementalFlashing || useIncrementalFlashing) {
                    // Do not move baseline if using incremental flashing
                    moveBaseLine = false;
                }
                if (mApplySnapshot) {
                    // Move baseline when going with incremental + apply update
                    moveBaseLine = true;
                }
                if (moveBaseLine) {
                    DeviceImageTracker.getDefaultCache()
                            .trackUpdatedDeviceImage(
                                    device.getSerialNumber(),
                                    deviceBuild.getDeviceImageFile(),
                                    deviceBuild.getBootloaderImageFile(),
                                    deviceBuild.getBasebandImageFile(),
                                    deviceBuild.getBuildId(),
                                    deviceBuild.getBuildBranch(),
                                    deviceBuild.getBuildFlavor());
                }
            }
        } finally {
            device.setRecoveryMode(RecoveryMode.AVAILABLE);
            // Allow interruption at the end no matter what.
            getRunUtil().allowInterrupt(true);
        }
    }

    /**
     * Possible check before flashing to ensure the device is as expected compare to the build info.
     *
     * @param device the {@link ITestDevice} to flash.
     * @param deviceBuild the {@link IDeviceBuildInfo} used to flash.
     * @throws BuildError
     * @throws DeviceNotAvailableException
     */
    protected void checkDeviceProductType(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws BuildError, DeviceNotAvailableException {
        // empty of purpose
    }

    /**
     * Verifies the expected build matches the actual build on device after flashing
     * @throws DeviceNotAvailableException
     */
    private void checkBuild(ITestDevice device, IDeviceBuildInfo deviceBuild)
            throws DeviceNotAvailableException {
        // Need to use deviceBuild.getDeviceBuildId instead of getBuildId because the build info
        // could be an AppBuildInfo and return app build id. Need to be more explicit that we
        // check for the device build here.
        if (!mSkipPostFlashBuildIdCheck) {
            checkBuildAttribute(deviceBuild.getDeviceBuildId(), device.getBuildId(),
                    device.getSerialNumber());
        }
    }

    private void checkBuildAttribute(String expectedBuildAttr, String actualBuildAttr,
            String serial) throws DeviceNotAvailableException {
        if (expectedBuildAttr == null || actualBuildAttr == null ||
                !expectedBuildAttr.equals(actualBuildAttr)) {
            // throw DNAE - assume device hardware problem - we think flash was successful but
            // device is not running right bits
            throw new DeviceNotAvailableException(
                    String.format(
                            "Unexpected build after flashing. Expected %s, actual %s",
                            expectedBuildAttr, actualBuildAttr),
                    serial,
                    DeviceErrorIdentifier.ERROR_AFTER_FLASHING);
        }
    }

    /**
     * Create {@link IDeviceFlasher} to use. Subclasses can override
     * @throws DeviceNotAvailableException
     */
    protected abstract IDeviceFlasher createFlasher(ITestDevice device)
            throws DeviceNotAvailableException;

    @Override
    public void tearDown(TestInformation testInfo, Throwable e) throws DeviceNotAvailableException {
        if (testInfo.getDevice().getIDevice() instanceof NullDevice) {
            CLog.i("Skipping device flashing tearDown, this is a null-device.");
            return;
        }
        if (mIncrementalImageUtil != null) {
            CLog.d("Teardown related to incremental update.");
            mIncrementalImageUtil.teardownDevice();
        }
        if (mEnforceSnapshotCompleted && e == null) {
            if (mIncrementalImageUtil == null || !mIncrementalImageUtil.updateCompleted()) {
                throw new RuntimeException(
                        "We expected incremental-flashing to be used but wasn't.");
            }
        }
    }

    /**
     * Reports device flashing timing data to metrics backend
     * @param branch the branch where the device build originated from
     * @param buildFlavor the build flavor of the device build
     * @param buildId the build number of the device build
     * @param serial the serial number of device
     * @param queueTime the time spent waiting for a flashing limit to become available
     * @param flashingTime the time spent in flashing device image zip
     * @param flashingStatus the execution status of flashing command
     */
    protected void reportFlashMetrics(String branch, String buildFlavor, String buildId,
            String serial, long queueTime, long flashingTime, CommandStatus flashingStatus) {
        // no-op as default implementation
    }

    /**
     * Sets the option for whether ramdisk should be flashed
     *
     * @param shouldFlashRamdisk
     */
    @VisibleForTesting
    void setShouldFlashRamdisk(boolean shouldFlashRamdisk) {
        mShouldFlashRamdisk = shouldFlashRamdisk;
    }

    protected void setSkipPostFlashBuildIdCheck(boolean skipPostFlashBuildIdCheck) {
        mSkipPostFlashBuildIdCheck = skipPostFlashBuildIdCheck;
    }

    protected void setUseIncrementalFlashing(boolean incrementalFlashing) {
        mUseIncrementalFlashing = incrementalFlashing;
    }

    public boolean isIncrementalFlashingEnabled() {
        return mUseIncrementalFlashing;
    }

    public boolean isIncrementalFlashingForceDisabled() {
        return mForceDisableIncrementalFlashing;
    }

    public void setAllowCrossReleaseFlashing(boolean allowCrossReleaseFlashing) {
        mAllowIncrementalCrossRelease = allowCrossReleaseFlashing;
    }
}
