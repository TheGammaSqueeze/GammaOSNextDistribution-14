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
package com.android.tradefed.device.connection;

import com.android.ddmlib.IDevice.DeviceState;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.IManagedTestDevice;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.device.NativeDevice;
import com.android.tradefed.device.NullDevice;
import com.android.tradefed.device.RemoteAvdIDevice;
import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.device.TestDeviceOptions.InstanceType;
import com.android.tradefed.device.cloud.CommonLogRemoteFileUtil;
import com.android.tradefed.device.cloud.GceAvdInfo;
import com.android.tradefed.device.cloud.GceAvdInfo.GceStatus;
import com.android.tradefed.device.cloud.GceManager;
import com.android.tradefed.device.cloud.GceSshTunnelMonitor;
import com.android.tradefed.device.cloud.OxygenUtil;
import com.android.tradefed.device.cloud.RemoteFileUtil;
import com.android.tradefed.device.cloud.VmRemoteDevice;
import com.android.tradefed.host.IHostOptions.PermitLimitType;
import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.result.error.ErrorIdentifier;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.StreamUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.net.HostAndPort;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Adb connection over an ssh bridge. */
public class AdbSshConnection extends AdbTcpConnection {

    private GceAvdInfo mGceAvd = null;

    private GceManager mGceHandler = null;
    private GceSshTunnelMonitor mGceSshMonitor;
    private DeviceNotAvailableException mTunnelInitFailed = null;

    private boolean mIsRemote = false;
    private String mKnownIp = null;

    private static final long CHECK_WAIT_DEVICE_AVAIL_MS = 30 * 1000;
    private static final int WAIT_TIME_DIVISION = 4;
    private static final long WAIT_FOR_DEVICE_ONLINE = 1 * 60 * 1000;
    private static final long WAIT_FOR_TUNNEL_OFFLINE = 5 * 1000;
    private static final long WAIT_FOR_TUNNEL_ONLINE = 2 * 60 * 1000;
    private static final long FETCH_TOMBSTONES_TIMEOUT_MS = 5 * 60 * 1000;

    public AdbSshConnection(ConnectionBuilder builder) {
        super(builder);
        if (builder.existingAvdInfo != null) {
            mGceAvd = builder.existingAvdInfo;
        }
    }

    @Override
    public void initializeConnection() throws DeviceNotAvailableException, TargetSetupError {
        mGceSshMonitor = null;
        mTunnelInitFailed = null;
        // We create a brand new GceManager each time to ensure clean state.
        mGceHandler =
                new GceManager(
                        getDevice().getDeviceDescriptor(),
                        getDevice().getOptions(),
                        getBuildInfo());
        if (getDevice().getIDevice() instanceof VmRemoteDevice) {
            mIsRemote = true;
            mKnownIp = ((VmRemoteDevice) getDevice().getIDevice()).getKnownDeviceIp();
        }

        long remainingTime = getDevice().getOptions().getGceCmdTimeout();
        // mGceAvd is null means the device hasn't been launched.
        if (mGceAvd != null) {
            CLog.d("skipped GCE launch because GceAvdInfo %s is already set", mGceAvd);
            createGceSshMonitor(
                    getDevice(), getBuildInfo(), mGceAvd.hostAndPort(), getDevice().getOptions());
        } else {
            // Launch GCE helper script.
            long startTime = getCurrentTime();

            try {
                if (GlobalConfiguration.getInstance()
                                .getHostOptions()
                                .getConcurrentVirtualDeviceStartupLimit()
                        != null) {
                    GlobalConfiguration.getInstance()
                            .getHostOptions()
                            .takePermit(PermitLimitType.CONCURRENT_VIRTUAL_DEVICE_STARTUP);
                    long queueTime = System.currentTimeMillis() - startTime;
                    CLog.v(
                            "Fetch and launch CVD permit obtained after %ds",
                            TimeUnit.MILLISECONDS.toSeconds(queueTime));
                }
                launchGce(getBuildInfo(), getAttributes());
                remainingTime = remainingTime - (getCurrentTime() - startTime);
            } finally {
                if (GlobalConfiguration.getInstance()
                                .getHostOptions()
                                .getConcurrentVirtualDeviceStartupLimit()
                        != null) {
                    GlobalConfiguration.getInstance()
                            .getHostOptions()
                            .returnPermit(PermitLimitType.CONCURRENT_VIRTUAL_DEVICE_STARTUP);
                }
            }
            if (remainingTime <= 0) {
                throw new DeviceNotAvailableException(
                        String.format(
                                "Failed to launch GCE after %sms",
                                getDevice().getOptions().getGceCmdTimeout()),
                        getDevice().getSerialNumber(),
                        DeviceErrorIdentifier.FAILED_TO_LAUNCH_GCE);
            }
            CLog.d("%sms left before timeout after GCE launch returned", remainingTime);
        }
        // Wait for device to be ready.
        RecoveryMode previousMode = getDevice().getRecoveryMode();
        getDevice().setRecoveryMode(RecoveryMode.NONE);
        boolean unresponsive = true;
        try {
            for (int i = 0; i < WAIT_TIME_DIVISION; i++) {
                // We don't have a way to bail out of waitForDeviceAvailable if the Gce Avd
                // boot up and then fail some other setup so we check to make sure the monitor
                // thread is alive and we have an opportunity to abort and avoid wasting time.
                if (((IManagedTestDevice) getDevice())
                                .getMonitor()
                                .waitForDeviceAvailable(remainingTime / WAIT_TIME_DIVISION)
                        != null) {
                    unresponsive = false;
                    break;
                }
                waitForTunnelOnline(WAIT_FOR_TUNNEL_ONLINE);
                waitForAdbConnect(getDevice().getSerialNumber(), WAIT_FOR_ADB_CONNECT);
            }
        } finally {
            getDevice().setRecoveryMode(previousMode);
        }
        if (!DeviceState.ONLINE.equals(getDevice().getIDevice().getState()) || unresponsive) {
            if (mGceAvd != null && GceStatus.SUCCESS.equals(mGceAvd.getStatus())) {
                // Update status to reflect that we were not able to connect to it.
                mGceAvd.setStatus(GceStatus.DEVICE_OFFLINE);
            }
            if (unresponsive) {
                throw new DeviceUnresponsiveException(
                        "AVD device booted to online but is unresponsive.",
                        getDevice().getSerialNumber(),
                        DeviceErrorIdentifier.DEVICE_UNRESPONSIVE);
            }
            throw new DeviceNotAvailableException(
                    String.format(
                            "AVD device booted but was in %s state",
                            getDevice().getIDevice().getState()),
                    getDevice().getSerialNumber(),
                    DeviceErrorIdentifier.FAILED_TO_LAUNCH_GCE);
        }
        verifyKernel();
        getDevice().enableAdbRoot();
        // For virtual device we only start logcat collection after we are sure it's online.
        if (getDevice().getOptions().isLogcatCaptureEnabled()) {
            getDevice().startLogcat();
        }
    }

    @Override
    public void reconnect(String serial) throws DeviceNotAvailableException {
        if (!getGceSshMonitor().isTunnelAlive()) {
            getGceSshMonitor().closeConnection();
            getRunUtil().sleep(WAIT_FOR_TUNNEL_OFFLINE);
            waitForTunnelOnline(WAIT_FOR_TUNNEL_ONLINE);
        }
        super.reconnect(serial);
    }

    @Override
    public void reconnectForRecovery(String serial) throws DeviceNotAvailableException {
        if (getGceSshMonitor() == null) {
            if (mTunnelInitFailed != null) {
                // We threw before but was not reported, so throw the root cause here.
                throw mTunnelInitFailed;
            }
            waitForTunnelOnline(WAIT_FOR_TUNNEL_ONLINE);
        }
        // Check that shell is available before resetting the bridge
        if (!getDevice().waitForDeviceShell(CHECK_WAIT_DEVICE_AVAIL_MS)) {
            long startTime = System.currentTimeMillis();
            try {
                // Re-init tunnel when attempting recovery
                CLog.i("Attempting recovery on GCE AVD %s", serial);
                getGceSshMonitor().closeConnection();
                getRunUtil().sleep(WAIT_FOR_TUNNEL_OFFLINE);
                waitForTunnelOnline(WAIT_FOR_TUNNEL_ONLINE);
                waitForAdbConnect(serial, WAIT_FOR_ADB_CONNECT);
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.DEVICE_RECOVERED_FROM_SSH_TUNNEL, 1);
            } catch (Exception e) {
                // Log the entrance in recovery here to avoid double counting with
                // super.recoverDevice.
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.RECOVERY_ROUTINE_COUNT, 1);
                throw e;
            } finally {
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.RECOVERY_TIME, System.currentTimeMillis() - startTime);
            }
        }
    }

    @Override
    public void notifyAdbRebootCalled() {
        final GceSshTunnelMonitor tunnelMonitor = getGceSshMonitor();
        if (tunnelMonitor != null) {
            tunnelMonitor.isAdbRebootCalled(true);
        }
    }

    @Override
    public void tearDownConnection() {
        try {
            CLog.i("Invocation tear down for device %s", getDevice().getSerialNumber());
            // Just clear the logcat, we don't need the teardown logcat
            getDevice().clearLogcat();
            getDevice().stopLogcat();
            // Terminate SSH tunnel process.
            if (getGceSshMonitor() != null) {
                getGceSshMonitor().logSshTunnelLogs(getLogger());
                getGceSshMonitor().shutdown();
                try {
                    getGceSshMonitor().joinMonitor();
                } catch (InterruptedException e1) {
                    CLog.i("Interrupted while waiting for GCE SSH monitor to shutdown.");
                }
                // We are done with the monitor, clean it to prevent re-entry.
                mGceSshMonitor = null;
            }
            if (!((IManagedTestDevice) getDevice())
                    .waitForDeviceNotAvailable(DEFAULT_SHORT_CMD_TIMEOUT)) {
                CLog.w("Device %s still available after timeout.", getDevice().getSerialNumber());
            }

            if (mGceAvd != null) {
                // Host and port can be null in case of acloud timeout
                if (mGceAvd.hostAndPort() != null) {
                    // attempt to get a bugreport if Gce Avd is a failure
                    if (!GceStatus.SUCCESS.equals(mGceAvd.getStatus())
                            && !mGceAvd.getSkipBugreportCollection()) {
                        // Get a bugreport via ssh
                        getSshBugreport();
                    }
                    // Log the serial output of the instance.
                    getGceHandler().logSerialOutput(mGceAvd, getLogger());

                    // Test if an SSH connection can be established. If can't, skip all collection.
                    boolean isGceReachable =
                            CommonLogRemoteFileUtil.isRemoteGceReachableBySsh(
                                    mGceAvd, getDevice().getOptions(), getRunUtil());

                    if (isGceReachable) {
                        // Fetch remote files
                        CommonLogRemoteFileUtil.fetchCommonFiles(
                                getLogger(), mGceAvd, getDevice().getOptions(), getRunUtil());

                        // Fetch all tombstones if any.
                        CommonLogRemoteFileUtil.fetchTombstones(
                                getLogger(), mGceAvd, getDevice().getOptions(), getRunUtil());
                    } else {
                        CLog.e(
                                "Failed to establish ssh connect to remote file host, skipping"
                                        + " remote common file and tombstones collection.");
                    }

                    // Fetch host kernel log by running `dmesg` for Oxygen hosts
                    if (getDevice().getOptions().useOxygen()) {
                        CommonLogRemoteFileUtil.logRemoteCommandOutput(
                                getLogger(),
                                mGceAvd,
                                getDevice().getOptions(),
                                getRunUtil(),
                                "host_kernel.log",
                                "toybox",
                                "dmesg");
                    }
                }
            }

            // Cleanup GCE first to make sure ssh tunnel has nowhere to go.
            if (!getDevice().getOptions().shouldSkipTearDown() && getGceHandler() != null) {
                getGceHandler().shutdownGce();
            }
            // We are done with the gce related information, clean it to prevent re-entry.
            mGceAvd = null;
            // TODO: Ensure the release is always done so we never leak placeholders
            if (getInitialSerial() != null) {
                if (wasTemporaryHolder()) {
                    // Logic linked to {@link ManagedDeviceList#allocate()}.
                    // restore the temporary placeholder to avoid leaking it
                    ((IManagedTestDevice) getDevice())
                            .setIDevice(new NullDevice(getInitialSerial(), true));
                } else if (mIsRemote) {
                    ((IManagedTestDevice) getDevice())
                            .setIDevice(new VmRemoteDevice(getInitialSerial(), mKnownIp));
                } else {
                    ((IManagedTestDevice) getDevice())
                            .setIDevice(
                                    new RemoteAvdIDevice(
                                            getInitialSerial(),
                                            getInitialIp(),
                                            getInitialUser(),
                                            getInitialDeviceNumOffset()));
                }
                CLog.d("Release as idevice: %s", ((IManagedTestDevice) getDevice()).getIDevice());
            }

            if (getGceHandler() != null) {
                getGceHandler().cleanUp();
            }
        } finally {
            super.tearDownConnection();
        }
    }

    /** Launch the actual gce device based on the build info. */
    protected void launchGce(IBuildInfo buildInfo, MultiMap<String, String> attributes)
            throws TargetSetupError {
        TargetSetupError exception = null;
        for (int attempt = 0; attempt < getDevice().getOptions().getGceMaxAttempt(); attempt++) {
            try {
                mGceAvd =
                        getGceHandler()
                                .startGce(
                                        getInitialIp(),
                                        getInitialUser(),
                                        getInitialDeviceNumOffset(),
                                        attributes,
                                        getLogger());
                if (mGceAvd != null) {
                    if (GceStatus.SUCCESS.equals(mGceAvd.getStatus())) {
                        break;
                    }
                    CLog.w(
                            "Failed to start AVD with attempt: %s out of %s, error: %s",
                            attempt + 1,
                            getDevice().getOptions().getGceMaxAttempt(),
                            mGceAvd.getErrors());
                }
            } catch (TargetSetupError tse) {
                CLog.w(
                        "Failed to start Gce with attempt: %s out of %s. With Exception: %s",
                        attempt + 1, getDevice().getOptions().getGceMaxAttempt(), tse);
                exception = tse;

                if (getDevice().getOptions().useOxygen()) {
                    OxygenUtil util = new OxygenUtil();
                    util.downloadLaunchFailureLogs(tse, getLogger());
                }
            }
        }
        if (mGceAvd == null) {
            throw exception;
        } else {
            CLog.i("GCE AVD has been started: %s", mGceAvd);
            ErrorIdentifier errorIdentifier =
                    (mGceAvd.getErrorType() != null)
                            ? mGceAvd.getErrorType()
                            : DeviceErrorIdentifier.FAILED_TO_LAUNCH_GCE;
            if (GceAvdInfo.GceStatus.BOOT_FAIL.equals(mGceAvd.getStatus())) {
                String errorMsg =
                        String.format(
                                "Device failed to boot. Error from device leasing attempt: %s",
                                mGceAvd.getErrors());
                throw new TargetSetupError(
                        errorMsg, getDevice().getDeviceDescriptor(), errorIdentifier);
            } else if (GceAvdInfo.GceStatus.FAIL.equals(mGceAvd.getStatus())) {
                throw new TargetSetupError(
                        mGceAvd.getErrors(), getDevice().getDeviceDescriptor(), errorIdentifier);
            }
        }
        createGceSshMonitor(
                getDevice(), buildInfo, mGceAvd.hostAndPort(), getDevice().getOptions());
    }

    /** Create an ssh tunnel, connect to it, and keep the connection alive. */
    void createGceSshMonitor(
            ITestDevice device,
            IBuildInfo buildInfo,
            HostAndPort hostAndPort,
            TestDeviceOptions deviceOptions) {
        mGceSshMonitor = new GceSshTunnelMonitor(device, buildInfo, hostAndPort, deviceOptions);
        mGceSshMonitor.start();
    }

    /** Check if the tunnel monitor is running. */
    protected void waitForTunnelOnline(final long waitTime) throws DeviceNotAvailableException {
        CLog.i("Waiting %d ms for tunnel to be restarted", waitTime);
        long startTime = getCurrentTime();
        while (getCurrentTime() - startTime < waitTime) {
            if (getGceSshMonitor() == null) {
                CLog.e("Tunnel Thread terminated, something went wrong with the device.");
                break;
            }
            if (getGceSshMonitor().isTunnelAlive()) {
                CLog.d("Tunnel online again, resuming.");
                return;
            }
            getRunUtil().sleep(RETRY_INTERVAL_MS);
        }
        mTunnelInitFailed =
                new DeviceNotAvailableException(
                        String.format("Tunnel did not come back online after %sms", waitTime),
                        getDevice().getSerialNumber(),
                        DeviceErrorIdentifier.FAILED_TO_CONNECT_TO_GCE);
        throw mTunnelInitFailed;
    }

    /** Returns the {@link com.android.tradefed.device.cloud.GceSshTunnelMonitor} of the device. */
    public GceSshTunnelMonitor getGceSshMonitor() {
        return mGceSshMonitor;
    }

    /** Returns the current system time. Exposed for testing. */
    protected long getCurrentTime() {
        return System.currentTimeMillis();
    }

    /** Returns the instance of the {@link com.android.tradefed.device.cloud.GceManager}. */
    @VisibleForTesting
    GceManager getGceHandler() {
        return mGceHandler;
    }

    /** Capture a remote bugreport by ssh-ing into the device directly. */
    public void getSshBugreport() {
        if (mGceAvd == null) {
            CLog.w("No GceAvdInfo to fetch bugreport from.");
            return;
        }
        InstanceType type = getDevice().getOptions().getInstanceType();
        File bugreportFile = null;
        try {
            if (InstanceType.GCE.equals(type) || InstanceType.REMOTE_AVD.equals(type)) {
                bugreportFile =
                        GceManager.getBugreportzWithSsh(
                                mGceAvd, getDevice().getOptions(), getRunUtil());
            } else {
                bugreportFile =
                        GceManager.getNestedDeviceSshBugreportz(
                                mGceAvd, getDevice().getOptions(), getRunUtil());
            }
            if (bugreportFile != null) {
                InputStreamSource bugreport = new FileInputStreamSource(bugreportFile);
                getLogger().testLog("bugreportz-ssh", LogDataType.BUGREPORTZ, bugreport);
                StreamUtil.cancel(bugreport);
            }
        } catch (IOException e) {
            CLog.e(e);
        } finally {
            FileUtil.deleteFile(bugreportFile);
        }
    }

    /**
     * Attempt to powerwash a GCE instance
     *
     * @return returns CommandResult of the powerwash attempts
     * @throws TargetSetupError
     */
    public CommandResult powerwash() throws TargetSetupError {
        return powerwashGce(null, null);
    }

    /**
     * Attempt to powerwash a GCE instance
     *
     * @param user the host running user of AVD, <code>null</code> if not applicable.
     * @param offset the device num offset of the AVD in the host, <code>null</code> if not
     *     applicable
     * @return returns CommandResult of the powerwash attempts
     * @throws TargetSetupError
     */
    public CommandResult powerwashGce(String user, Integer offset) throws TargetSetupError {
        long startTime = System.currentTimeMillis();

        if (mGceAvd == null) {
            String errorMsg = String.format("Can not get GCE AVD Info. launch GCE first?");
            throw new TargetSetupError(
                    errorMsg,
                    getDevice().getDeviceDescriptor(),
                    DeviceErrorIdentifier.DEVICE_UNAVAILABLE);
        }
        // Get the user from options instance-user if user is null.
        if (user == null) {
            user = getDevice().getOptions().getInstanceUser();
        }

        String powerwashCommand = String.format("/home/%s/bin/powerwash_cvd", user);

        if (offset != null) {
            powerwashCommand =
                    String.format(
                            "HOME=/home/%s/acloud_cf_%d acloud_cf_%d/bin/powerwash_cvd"
                                    + " -instance_num %d",
                            user, offset + 1, offset + 1, offset + 1);
        }

        if (getDevice().getOptions().useOxygen()) {
            // TODO(dshi): Simplify the logic after Oxygen creates symlink of the tmp dir.
            CommandResult result =
                    GceManager.remoteSshCommandExecution(
                            mGceAvd,
                            getDevice().getOptions(),
                            getRunUtil(),
                            10000L,
                            "toybox find /tmp -name powerwash_cvd".split(" "));
            if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                CLog.e("Failed to locate powerwash_cvd: %s", result.getStderr());
                return result;
            }
            String powerwashPath = result.getStdout();
            // Remove tailing `/bin/powerwash_cvd`
            String tmpDir = powerwashPath.substring(0, powerwashPath.length() - 18);
            powerwashCommand = String.format("HOME=%s %s", tmpDir, powerwashPath);
        }
        CommandResult powerwashRes =
                GceManager.remoteSshCommandExecution(
                        mGceAvd,
                        getDevice().getOptions(),
                        getRunUtil(),
                        Math.max(300000L, getDevice().getOptions().getGceCmdTimeout()),
                        powerwashCommand.split(" "));

        // Time taken for powerwash this invocation
        InvocationMetricLogger.addInvocationMetrics(
                InvocationMetricKey.POWERWASH_TIME,
                Long.toString(System.currentTimeMillis() - startTime));

        if (CommandStatus.SUCCESS.equals(powerwashRes.getStatus())) {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.POWERWASH_SUCCESS_COUNT, 1);
        } else {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.POWERWASH_FAILURE_COUNT, 1);
            CLog.e("%s", powerwashRes.getStderr());
            // Log 'adb devices' to confirm device is gone
            CommandResult printAdbDevices = getRunUtil().runTimedCmd(60000L, "adb", "devices");
            CLog.e("%s\n%s", printAdbDevices.getStdout(), printAdbDevices.getStderr());
            // Proceed here, device could have been already gone.
            return powerwashRes;
        }

        ((IManagedTestDevice) getDevice()).getMonitor().waitForDeviceAvailable();
        if (getDevice() instanceof NativeDevice) {
            ((NativeDevice) getDevice()).resetContentProviderSetup();
        }
        return powerwashRes;
    }

    /**
     * Create command string
     *
     * @param bin binary to use.
     * @param args arguments passed for the binary.
     * @param user the host running user of AVD, <code>null</code> if not applicable.
     * @param offset the device num offset of the AVD in the host, <code>null</code> if not
     *     applicable
     * @return returns String of the command to be run
     */
    String commandBuilder(String bin, String args, String user, Integer offset) {
        String builtCommand = String.format("/home/%s/bin/%s %s", user, bin, args);
        if (offset != null) {
            builtCommand =
                    String.format(
                            "HOME=/home/%s/acloud_cf_%d acloud_cf_%d/bin/%s %s -instance_num %d",
                            user, offset + 1, offset + 1, bin, args, offset + 1);
        }

        if (getDevice().getOptions().useOxygen()) {
            CommandResult result =
                    GceManager.remoteSshCommandExecution(
                            mGceAvd,
                            getDevice().getOptions(),
                            getRunUtil(),
                            10000L,
                            String.format("toybox find /tmp -name %s", bin).split(" "));
            if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                CLog.e("Failed to locate %s: %s", bin, result.getStderr());
                return "";
            }
            String commandPath = result.getStdout().trim();
            // Remove tailing `/bin/COMMAND`
            String tmpDir = commandPath.substring(0, commandPath.length() - (bin.length() + 5));
            builtCommand = String.format("HOME=%s %s %s", tmpDir, commandPath, args);
        }
        return builtCommand;
    }

    /**
     * Attempt to snapshot a Cuttlefish instance
     *
     * @param user the host running user of AVD, <code>null</code> if not applicable.
     * @param offset the device num offset of the AVD in the host, <code>null</code> if not
     *     applicable
     * @return returns CommandResult of the snapshot attempts
     * @throws TargetSetupError
     */
    public CommandResult snapshotGce(String user, Integer offset, String snapshotId)
            throws TargetSetupError {
        cleanupSnapshotGce(user, snapshotId);
        suspendGce(user, offset);
        long startTime = System.currentTimeMillis();

        if (mGceAvd == null) {
            String errorMsg = "Can not get GCE AVD Info. launch GCE first?";
            throw new TargetSetupError(
                    errorMsg,
                    getDevice().getDeviceDescriptor(),
                    DeviceErrorIdentifier.DEVICE_UNAVAILABLE);
        }
        // Get the user from options instance-user if user is null.
        if (user == null) {
            user = getDevice().getOptions().getInstanceUser();
        }

        String snapshotCommand =
                commandBuilder(
                        "cvd",
                        String.format(
                                "snapshot_take --snapshot_path=/tmp/%s/snapshots/%s",
                                user, snapshotId),
                        user,
                        offset);
        if (Strings.isNullOrEmpty(snapshotCommand)) {
            throw new TargetSetupError(
                    "failed to set up snapshot command, invalid path",
                    getDevice().getDeviceDescriptor(),
                    DeviceErrorIdentifier.DEVICE_FAILED_TO_SNAPSHOT);
        }

        CommandResult snapshotRes =
                GceManager.remoteSshCommandExecution(
                        mGceAvd,
                        getDevice().getOptions(),
                        getRunUtil(),
                        // TODO(khei): explore shorter timeouts.
                        Math.max(30000L, getDevice().getOptions().getGceCmdTimeout()),
                        snapshotCommand.split(" "));

        if (CommandStatus.SUCCESS.equals(snapshotRes.getStatus())) {
            // Time taken for snapshot this invocation
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DEVICE_SNAPSHOT_DURATIONS,
                    Long.toString(System.currentTimeMillis() - startTime));

            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DEVICE_SNAPSHOT_SUCCESS_COUNT, 1);
        } else {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DEVICE_SNAPSHOT_FAILURE_COUNT, 1);
            CLog.e("%s", snapshotRes.getStderr());
            throw new TargetSetupError(
                    String.format("failed to snapshot device: %s", snapshotRes.getStderr()),
                    getDevice().getDeviceDescriptor(),
                    DeviceErrorIdentifier.DEVICE_FAILED_TO_SNAPSHOT);
        }
        resumeGce(user, offset);

        return snapshotRes;
    }

    /**
     * Attempt to suspend a Cuttlefish instance
     *
     * @param user the host running user of AVD, <code>null</code> if not applicable.
     * @param offset the device num offset of the AVD in the host, <code>null</code> if not
     *     applicable
     * @throws TargetSetupError
     */
    private void suspendGce(String user, Integer offset) throws TargetSetupError {
        long startTime = System.currentTimeMillis();

        // Get the user from options instance-user if user is null.
        if (user == null) {
            user = getDevice().getOptions().getInstanceUser();
        }

        String suspendCommand = commandBuilder("cvd", "suspend", user, offset);
        if (suspendCommand.length() == 0) {
            throw new TargetSetupError(
                    "failed to set up suspend command, invalid path",
                    getDevice().getDeviceDescriptor(),
                    DeviceErrorIdentifier.DEVICE_FAILED_TO_SUSPEND);
        }

        CommandResult suspendRes =
                GceManager.remoteSshCommandExecution(
                        mGceAvd,
                        getDevice().getOptions(),
                        getRunUtil(),
                        // TODO(khei): explore shorter timeouts.
                        Math.max(30000L, getDevice().getOptions().getGceCmdTimeout()),
                        suspendCommand.split(" "));

        if (CommandStatus.SUCCESS.equals(suspendRes.getStatus())) {
            // Time taken for suspend this invocation
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DEVICE_SUSPEND_DURATIONS,
                    Long.toString(System.currentTimeMillis() - startTime));

            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DEVICE_SUSPEND_SUCCESS_COUNT, 1);
        } else {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DEVICE_SUSPEND_FAILURE_COUNT, 1);
            CLog.e("%s", suspendRes.getStderr());
            throw new TargetSetupError(
                    String.format("failed to suspend device: %s", suspendRes.getStderr()),
                    getDevice().getDeviceDescriptor(),
                    DeviceErrorIdentifier.DEVICE_FAILED_TO_SUSPEND);
        }
    }

    /**
     * Attempt to resume a Cuttlefish instance
     *
     * @param user the host running user of AVD, <code>null</code> if not applicable.
     * @param offset the device num offset of the AVD in the host, <code>null</code> if not
     *     applicable
     * @throws TargetSetupError
     */
    private void resumeGce(String user, Integer offset) throws TargetSetupError {
        long startTime = System.currentTimeMillis();

        // Get the user from options instance-user if user is null.
        if (user == null) {
            user = getDevice().getOptions().getInstanceUser();
        }

        String resumeCommand = commandBuilder("cvd", "resume", user, offset);
        if (resumeCommand.length() == 0) {
            throw new TargetSetupError(
                    "failed to set up resume command, invalid path",
                    getDevice().getDeviceDescriptor(),
                    DeviceErrorIdentifier.DEVICE_FAILED_TO_RESUME);
        }

        CommandResult resumeRes =
                GceManager.remoteSshCommandExecution(
                        mGceAvd,
                        getDevice().getOptions(),
                        getRunUtil(),
                        Math.max(300000L, getDevice().getOptions().getGceCmdTimeout()),
                        resumeCommand.split(" "));

        if (CommandStatus.SUCCESS.equals(resumeRes.getStatus())) {
            // Time taken for resume this invocation
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DEVICE_RESUME_DURATIONS,
                    Long.toString(System.currentTimeMillis() - startTime));

            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DEVICE_RESUME_SUCCESS_COUNT, 1);
        } else {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DEVICE_RESUME_FAILURE_COUNT, 1);
            CLog.e("%s", resumeRes.getStderr());
            throw new TargetSetupError(
                    String.format("failed to resume device: %s", resumeRes.getStderr()),
                    getDevice().getDeviceDescriptor(),
                    DeviceErrorIdentifier.DEVICE_FAILED_TO_RESUME);
        }
    }

    /**
     * Attempt to restore snapshot of a Cuttlefish instance
     *
     * @param user the host running user of AVD, <code>null</code> if not applicable.
     * @param offset the device num offset of the AVD in the host, <code>null</code> if not
     *     applicable
     * @param snapshotId the snapshot ID
     * @return returns CommandResult of the restore snapshot attempts
     * @throws TargetSetupError
     */
    public CommandResult restoreSnapshotGce(String user, Integer offset, String snapshotId)
            throws TargetSetupError {
        stopGce(user, offset);
        long startTime = System.currentTimeMillis();

        // Get the user from options instance-user if user is null.
        if (user == null) {
            user = getDevice().getOptions().getInstanceUser();
        }

        String restoreCommand =
                commandBuilder(
                        "cvd",
                        String.format(
                                "start --snapshot_path=/tmp/%s/snapshots/%s", user, snapshotId),
                        user,
                        offset);
        if (restoreCommand.length() == 0) {
            throw new TargetSetupError(
                    "failed to set up restore command, invalid path",
                    getDevice().getDeviceDescriptor(),
                    DeviceErrorIdentifier.DEVICE_FAILED_TO_RESTORE_SNAPSHOT);
        }

        CommandResult restoreRes =
                GceManager.remoteSshCommandExecution(
                        mGceAvd,
                        getDevice().getOptions(),
                        getRunUtil(),
                        Math.max(300000L, getDevice().getOptions().getGceCmdTimeout()),
                        restoreCommand.split(" "));

        if (CommandStatus.SUCCESS.equals(restoreRes.getStatus())) {
            try {
                waitForAdbConnect(getDevice().getSerialNumber(), WAIT_FOR_ADB_CONNECT);
                getDevice().waitForDeviceOnline(WAIT_FOR_DEVICE_ONLINE);
            } catch (DeviceNotAvailableException e) {
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.DEVICE_SNAPSHOT_RESTORE_FAILURE_COUNT, 1);
                CLog.e("%s", e.toString());
                throw new TargetSetupError(
                        String.format("failed to restore device: %s", e.toString()),
                        getDevice().getDeviceDescriptor(),
                        DeviceErrorIdentifier.DEVICE_FAILED_TO_RESTORE_SNAPSHOT);
            }
            // Time taken for restore this invocation
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DEVICE_RESUME_DURATIONS,
                    Long.toString(System.currentTimeMillis() - startTime));

            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DEVICE_SNAPSHOT_RESTORE_SUCCESS_COUNT, 1);
        } else {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DEVICE_SNAPSHOT_RESTORE_FAILURE_COUNT, 1);
            CLog.e("%s", restoreRes.getStderr());
            throw new TargetSetupError(
                    String.format("failed to restore device: %s", restoreRes.getStderr()),
                    getDevice().getDeviceDescriptor(),
                    DeviceErrorIdentifier.DEVICE_FAILED_TO_RESTORE_SNAPSHOT);
        }

        return restoreRes;
    }

    /**
     * Attempt to stop a Cuttlefish instance
     *
     * @param user the host running user of AVD, <code>null</code> if not applicable.
     * @param offset the device num offset of the AVD in the host, <code>null</code> if not
     *     applicable
     * @throws TargetSetupError
     */
    private void stopGce(String user, Integer offset) throws TargetSetupError {
        long startTime = System.currentTimeMillis();

        // Get the user from options instance-user if user is null.
        if (user == null) {
            user = getDevice().getOptions().getInstanceUser();
        }

        String stopCommand = commandBuilder("cvd", "stop", user, offset);
        if (stopCommand.length() == 0) {
            throw new TargetSetupError(
                    "failed to set up stop command, invalid path",
                    getDevice().getDeviceDescriptor(),
                    DeviceErrorIdentifier.DEVICE_FAILED_TO_STOP);
        }

        CommandResult stopRes =
                GceManager.remoteSshCommandExecution(
                        mGceAvd,
                        getDevice().getOptions(),
                        getRunUtil(),
                        Math.max(300000L, getDevice().getOptions().getGceCmdTimeout()),
                        stopCommand.split(" "));

        if (CommandStatus.SUCCESS.equals(stopRes.getStatus())) {
            // Time taken for stop this invocation
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DEVICE_STOP_DURATIONS,
                    Long.toString(System.currentTimeMillis() - startTime));

            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DEVICE_STOP_SUCCESS_COUNT, 1);
        } else {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DEVICE_STOP_FAILURE_COUNT, 1);
            CLog.e("%s", stopRes.getStderr());
            throw new TargetSetupError(
                    "failed to stop device",
                    getDevice().getDeviceDescriptor(),
                    DeviceErrorIdentifier.DEVICE_FAILED_TO_STOP);
        }
    }

    /**
     * Delete snapshot folder
     *
     * @param user the host running use of AVD, <code>null</code> if not applicable.
     * @param snapshotId the id of the snapshot to delete.
     */
    private void cleanupSnapshotGce(String user, String snapshotId) {
        long startTime = System.currentTimeMillis();

        // Get the user from options instance-user if user is null.
        if (user == null) {
            user = getDevice().getOptions().getInstanceUser();
        }

        String cleanupSnapshotCommand =
                String.format("rm -rf /tmp/%s/snapshots/%s", user, snapshotId);

        CommandResult deleteRes =
                GceManager.remoteSshCommandExecution(
                        mGceAvd,
                        getDevice().getOptions(),
                        getRunUtil(),
                        Math.max(3000L, getDevice().getOptions().getGceCmdTimeout()),
                        cleanupSnapshotCommand.split(" "));

        if (CommandStatus.SUCCESS.equals(deleteRes.getStatus())) {
            // Time taken for stop this invocation
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DELETE_SNAPSHOT_FILES,
                    Long.toString(System.currentTimeMillis() - startTime));

            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DELETE_SNAPSHOT_FILES_COUNT, 1);
        } else {
            CLog.e("failed to delete snapshot with ID: %s. Does the snapshot exist?", snapshotId);
        }
    }

    /**
     * Cuttlefish has a special feature that brings the tombstones to the remote host where we can
     * get them directly.
     */
    public List<File> getTombstones() {
        InstanceType type = getDevice().getOptions().getInstanceType();
        if (InstanceType.CUTTLEFISH.equals(type) || InstanceType.REMOTE_NESTED_AVD.equals(type)) {
            List<File> tombs = new ArrayList<>();
            String remoteRuntimePath =
                    String.format(
                                    CommonLogRemoteFileUtil.NESTED_REMOTE_LOG_DIR,
                                    getDevice().getOptions().getInstanceUser())
                            + "tombstones/*";
            File localDir = null;
            try {
                localDir = FileUtil.createTempDir("tombstones");
            } catch (IOException e) {
                CLog.e(e);
                return tombs;
            }
            if (!fetchRemoteDir(localDir, remoteRuntimePath)) {
                CLog.e("Failed to pull %s", remoteRuntimePath);
                FileUtil.recursiveDelete(localDir);
            } else {
                tombs.addAll(Arrays.asList(localDir.listFiles()));
                localDir.deleteOnExit();
            }
            return tombs;
        }
        // If it's not Cuttlefish, returns nothing
        return new ArrayList<>();
    }

    @VisibleForTesting
    boolean fetchRemoteDir(File localDir, String remotePath) {
        return RemoteFileUtil.fetchRemoteDir(
                mGceAvd,
                getDevice().getOptions(),
                getRunUtil(),
                FETCH_TOMBSTONES_TIMEOUT_MS,
                remotePath,
                localDir);
    }

    /**
     * Returns the {@link GceAvdInfo} from the created remote VM. Returns regardless of the status
     * so we can inspect the info.
     */
    public GceAvdInfo getAvdInfo() {
        return mGceAvd;
    }

    /**
     * Verify device kernel build ID
     *
     * <p>Throw TargetSetupError if kernel build ID doesn't match
     *
     * @throws DeviceNotAvailableException, TargetSetupError
     */
    @VisibleForTesting
    void verifyKernel() throws DeviceNotAvailableException, TargetSetupError {
        List<String> gceDriverParams = getDevice().getOptions().getGceDriverParams();
        if (gceDriverParams.contains("--kernel-build-id")) {
            String kernelBuildId =
                    gceDriverParams.get(gceDriverParams.indexOf("--kernel-build-id") + 1);
            String output = getDevice().executeShellCommand("uname -r");
            if (!Strings.isNullOrEmpty(output)) {
                if (output.contains(kernelBuildId)) {
                    CLog.i(
                            "Device has the expected kernel build ID %s in %s",
                            kernelBuildId, output);
                } else {
                    throw new TargetSetupError(
                            String.format(
                                    "Device booted up with wrong kernel %s, expecting kernel build"
                                            + " ID %s",
                                    output, kernelBuildId),
                            getDevice().getDeviceDescriptor(),
                            DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
                }
            } else {
                CLog.w("Failed to get kernel information by `uname -r` from device");
            }
        }
    }
}
