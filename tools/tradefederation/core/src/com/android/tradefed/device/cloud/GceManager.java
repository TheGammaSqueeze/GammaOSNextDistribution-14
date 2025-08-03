/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tradefed.device.cloud;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.device.cloud.AcloudConfigParser.AcloudKeys;
import com.android.tradefed.device.cloud.GceAvdInfo.GceStatus;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.invoker.tracing.CloseableTraceScope;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.GoogleApiClientUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.RunUtil;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.Compute.Instances.GetSerialPortOutput;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.compute.model.SerialPortOutput;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.net.HostAndPort;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Helper that manages the GCE calls to start/stop and collect logs from GCE. */
public class GceManager {
    public static final String GCE_INSTANCE_NAME_KEY = "gce-instance-name";
    public static final String GCE_HOSTNAME_KEY = "gce-hostname";
    public static final String GCE_INSTANCE_CLEANED_KEY = "gce-instance-clean-called";
    public static final String GCE_IP_PRECONFIGURED_KEY = "gce-ip-pre-configured";

    // Align default value of bugreport timeout with option bugreportz-timeout
    private static final long BUGREPORT_TIMEOUT = 5 * 60 * 1000L;
    private static final long REMOTE_FILE_OP_TIMEOUT = 10 * 60 * 1000L;
    private static final Pattern BUGREPORTZ_RESPONSE_PATTERN = Pattern.compile("(OK:)(.*)");
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Arrays.asList(ComputeScopes.COMPUTE_READONLY);

    // Create list of error codes that warrant a lease retry
    private static final List<InfraErrorIdentifier> RETRIABLE_LEASE_ERRORS =
            new ArrayList<>(
                    Arrays.asList(
                            InfraErrorIdentifier.OXYGEN_BAD_GATEWAY_ERROR,
                            InfraErrorIdentifier.OXYGEN_CLIENT_BINARY_ERROR,
                            InfraErrorIdentifier.OXYGEN_SERVER_CONNECTION_FAILURE,
                            InfraErrorIdentifier.OXYGEN_SERVER_LB_CONNECTION_ERROR,
                            InfraErrorIdentifier.OXYGEN_SERVER_SHUTTING_DOWN));

    private static final int MAX_LEASE_RETRIES = 3;
    private DeviceDescriptor mDeviceDescriptor;
    private TestDeviceOptions mDeviceOptions;
    private IBuildInfo mBuildInfo;

    private String mGceInstanceName = null;
    private String mGceHost = null;
    private GceAvdInfo mGceAvdInfo = null;

    private boolean mSkipSerialLogCollection = false;

    /**
     * Ctor
     *
     * @param deviceDesc The {@link DeviceDescriptor} that will be associated with the GCE device.
     * @param deviceOptions A {@link TestDeviceOptions} associated with the device.
     * @param buildInfo A {@link IBuildInfo} describing the gce build to start.
     */
    public GceManager(
            DeviceDescriptor deviceDesc, TestDeviceOptions deviceOptions, IBuildInfo buildInfo) {
        mDeviceDescriptor = deviceDesc;
        mDeviceOptions = deviceOptions;
        mBuildInfo = buildInfo;

        if (!deviceOptions.allowGceCmdTimeoutOverride()) {
            return;
        }
        int index = deviceOptions.getGceDriverParams().lastIndexOf("--boot-timeout");
        if (index != -1 && deviceOptions.getGceDriverParams().size() > index + 1) {
            String driverTimeoutStringSec = deviceOptions.getGceDriverParams().get(index + 1);
            try {
                // Add some extra time on top of Acloud: acloud boot the device then we expect
                // the Tradefed online check to take a bit of time, use 3min as a safe overhead
                long driverTimeoutMs =
                        Long.parseLong(driverTimeoutStringSec) * 1000 + 3 * 60 * 1000;
                long gceCmdTimeoutMs = deviceOptions.getGceCmdTimeout();
                deviceOptions.setGceCmdTimeout(driverTimeoutMs);
                CLog.i(
                        "Replacing --gce-boot-timeout %s by --boot-timeout %s.",
                        gceCmdTimeoutMs, driverTimeoutMs);
            } catch (NumberFormatException e) {
                CLog.e(e);
            }
        }
    }

    /** @deprecated Use other constructors, we keep this temporarily for backward compatibility. */
    @Deprecated
    public GceManager(
            DeviceDescriptor deviceDesc,
            TestDeviceOptions deviceOptions,
            IBuildInfo buildInfo,
            List<IBuildInfo> testResourceBuildInfos) {
        this(deviceDesc, deviceOptions, buildInfo);
    }

    /**
     * Ctor, variation that can be used to provide the GCE instance name to use directly.
     *
     * @param deviceDesc The {@link DeviceDescriptor} that will be associated with the GCE device.
     * @param deviceOptions A {@link TestDeviceOptions} associated with the device
     * @param buildInfo A {@link IBuildInfo} describing the gce build to start.
     * @param gceInstanceName The instance name to use.
     * @param gceHost The host name or ip of the instance to use.
     */
    public GceManager(
            DeviceDescriptor deviceDesc,
            TestDeviceOptions deviceOptions,
            IBuildInfo buildInfo,
            String gceInstanceName,
            String gceHost) {
        this(deviceDesc, deviceOptions, buildInfo);
        mGceInstanceName = gceInstanceName;
        mGceHost = gceHost;
    }

    public GceAvdInfo startGce() throws TargetSetupError {
        return startGce(null, null, null, null);
    }

    /**
     * Attempt to start a gce instance.
     *
     * @param ipDevice the initial IP of the GCE instance to run AVD in, <code>null</code> if not
     *     applicable
     * @param attributes attributes associated with current invocation, used for passing applicable
     *     information down to the GCE instance to be added as VM metadata
     * @return a {@link GceAvdInfo} describing the GCE instance. Could be a BOOT_FAIL instance.
     * @throws TargetSetupError
     */
    public GceAvdInfo startGce(String ipDevice, MultiMap<String, String> attributes)
            throws TargetSetupError {
        return startGce(ipDevice, null, null, attributes);
    }

    /**
     * Attempt to start a gce instance with either Acloud or Oxygen.
     *
     * @param ipDevice the initial IP of the GCE instance to run AVD in, <code>null</code> if not
     *     applicable
     * @param user the host running user of AVD, <code>null</code> if not applicable
     * @param offset the device num offset of the AVD in the host, <code>null</code> if not
     *     applicable
     * @param attributes attributes associated with current invocation, used for passing applicable
     *     information down to the GCE instance to be added as VM metadata
     * @return a {@link GceAvdInfo} describing the GCE instance. Could be a BOOT_FAIL instance.
     * @throws TargetSetupError
     */
    public GceAvdInfo startGce(
            String ipDevice, String user, Integer offset, MultiMap<String, String> attributes)
            throws TargetSetupError {
        return startGce(ipDevice, user, offset, attributes, null);
    }

    /**
     * Attempt to start a gce instance with either Acloud or Oxygen.
     *
     * @param ipDevice the initial IP of the GCE instance to run AVD in, <code>null</code> if not
     *     applicable
     * @param user the host running user of AVD, <code>null</code> if not applicable
     * @param offset the device num offset of the AVD in the host, <code>null</code> if not
     *     applicable
     * @param attributes attributes associated with current invocation, used for passing applicable
     *     information down to the GCE instance to be added as VM metadata
     * @param logger The {@link ITestLogger} where to log the device launch logs.
     * @return a {@link GceAvdInfo} describing the GCE instance. Could be a BOOT_FAIL instance.
     * @throws TargetSetupError
     */
    public GceAvdInfo startGce(
            String ipDevice,
            String user,
            Integer offset,
            MultiMap<String, String> attributes,
            ITestLogger logger)
            throws TargetSetupError {
        // If ipDevice is specified, skip collecting serial log as the host may not be GCE instance
        // If Oxygen cuttlefish is used, skip collecting serial log due to lack of access.
        mSkipSerialLogCollection =
                (!Strings.isNullOrEmpty(ipDevice) || getTestDeviceOptions().useOxygen());
        if (getTestDeviceOptions().useOxygen()) {
            return startGceWithOxygenClient(logger, attributes);
        } else {
            return startGceWithAcloud(ipDevice, user, offset, attributes);
        }
    }

    /**
     * @deprecated Remove this after master branch is updated.
     */
    @Deprecated
    public List<GceAvdInfo> startMultiDevicesGce(List<IBuildInfo> buildInfos)
            throws TargetSetupError {
        return startMultiDevicesGce(buildInfos, null);
    }

    /**
     * Attempt to start multi devices gce instance with Oxygen.
     *
     * @param buildInfos {@link List<IBuildInfo>}
     * @param attributes attributes associated with current invocation
     * @return a {@link List<GceAvdInfo>} describing the GCE Avd Info.
     */
    public List<GceAvdInfo> startMultiDevicesGce(
            List<IBuildInfo> buildInfos, MultiMap<String, String> attributes)
            throws TargetSetupError {
        List<GceAvdInfo> gceAvdInfos;
        long startTime = System.currentTimeMillis();
        try (CloseableTraceScope ignore = new CloseableTraceScope("startMultiDevicesGce")) {
            OxygenClient oxygenClient =
                    new OxygenClient(getTestDeviceOptions().getAvdDriverBinary());
            CommandResult res =
                    oxygenClient.leaseMultipleDevices(
                            buildInfos, getTestDeviceOptions(), attributes);
            gceAvdInfos =
                    GceAvdInfo.parseGceInfoFromOxygenClientOutput(
                            res, mDeviceOptions.getRemoteAdbPort());
            mGceAvdInfo = gceAvdInfos.get(0);
            return gceAvdInfos;
        } finally {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.OXYGEN_DEVICE_DIRECT_LEASE_COUNT, 2);
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.CF_LAUNCH_CVD_TIME, System.currentTimeMillis() - startTime);
        }
    }

    /**
     * Attempt to start a gce instance with Oxygen.
     *
     * @param logger The {@link ITestLogger} where to log the device launch logs.
     * @param attributes attributes associated with current invocation
     * @return a {@link GceAvdInfo} describing the GCE instance.
     */
    private GceAvdInfo startGceWithOxygenClient(
            ITestLogger logger, MultiMap<String, String> attributes) throws TargetSetupError {
        long startTime = System.currentTimeMillis();
        long fetchTime = 0;
        try {
            OxygenClient oxygenClient =
                    new OxygenClient(getTestDeviceOptions().getAvdDriverBinary());
            CommandResult res =
                    oxygenClient.leaseDevice(mBuildInfo, getTestDeviceOptions(), attributes);

            // Retry lease up to 2x if error code is in list of error codes
            int iteration = 1;
            while (res.getStatus() != CommandStatus.SUCCESS && iteration < MAX_LEASE_RETRIES) {
                InfraErrorIdentifier identifier = GceAvdInfo.refineOxygenErrorType(res.getStderr());
                if (!RETRIABLE_LEASE_ERRORS.contains(identifier)) {
                    break;
                }
                CLog.d("Retrying lease call due to earlier failure of %s", identifier);
                res = oxygenClient.leaseDevice(mBuildInfo, getTestDeviceOptions(), attributes);
                // Update Oxygen lease attempt metrics
                if (res.getStatus() == CommandStatus.SUCCESS) {
                    InvocationMetricLogger.addInvocationMetrics(
                            InvocationMetricKey.LEASE_RETRY_COUNT_SUCCESS, iteration);
                } else {
                    InvocationMetricLogger.addInvocationMetrics(
                            InvocationMetricKey.LEASE_RETRY_COUNT_FAILURE, iteration);
                }
                iteration++;
            }

            mGceAvdInfo =
                    GceAvdInfo.parseGceInfoFromOxygenClientOutput(
                                    res, mDeviceOptions.getRemoteAdbPort())
                            .get(0);
            // Lease may time out, skip remaining logic if lease failed.
            if (mGceAvdInfo.hostAndPort() == null) {
                CLog.w("Failed to lease a device: %s", mGceAvdInfo);
                return mGceAvdInfo;
            }
            if (oxygenClient.noWaitForBootSpecified(getTestDeviceOptions())) {
                CLog.d(
                        "Device leased without waiting for boot to finish. Poll emulator_stderr.txt"
                                + " for flag `VIRTUAL_DEVICE_BOOT_COMPLETED`");
                // When leasing without wait for boot to finish, use the time spent so far as the
                // best estimate of cf_fetch_artifact_time_ms.
                fetchTime = System.currentTimeMillis() - startTime;
                Boolean bootSuccess = false;
                long timeout =
                        startTime
                                + getTestDeviceOptions().getGceCmdTimeout()
                                - System.currentTimeMillis();
                startTime = System.currentTimeMillis();
                final String remoteFile =
                        CommonLogRemoteFileUtil.OXYGEN_EMULATOR_LOG_DIR + "3/emulator_stderr.txt";
                // Continuously scan cf boot status and exit immediately when the magic string
                // VIRTUAL_DEVICE_BOOT_COMPLETED is found
                String cfBootStatusSshCmd =
                        "tail -F -n +1 "
                                + remoteFile
                                + " | grep -m 1 VIRTUAL_DEVICE_BOOT_COMPLETED";
                String[] cfBootStatusSshCommand = cfBootStatusSshCmd.split(" ");

                res =
                        remoteSshCommandExecution(
                                mGceAvdInfo,
                                getTestDeviceOptions(),
                                RunUtil.getDefault(),
                                timeout,
                                cfBootStatusSshCommand);
                if (CommandStatus.SUCCESS.equals(res.getStatus())) {
                    bootSuccess = true;
                    CLog.d(
                            "Device boot completed after %sms, flag located: %s",
                            System.currentTimeMillis() - startTime, res.getStdout().trim());
                }

                if (!bootSuccess) {
                    if (logger != null) {
                        CommonLogRemoteFileUtil.fetchCommonFiles(
                                logger, mGceAvdInfo, getTestDeviceOptions(), getRunUtil());
                    }
                    mGceAvdInfo.setErrorType(InfraErrorIdentifier.OXYGEN_DEVICE_LAUNCHER_TIMEOUT);
                    mGceAvdInfo.setStatus(GceStatus.BOOT_FAIL);
                    // Align the error message raised when Oxygen lease timed out.
                    mGceAvdInfo.setErrors("Timed out waiting for virtual device to start.");
                }
            }
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.CF_OXYGEN_SESSION_ID, mGceAvdInfo.instanceName());
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.CF_OXYGEN_SERVER_URL, mGceAvdInfo.hostAndPort().getHost());
            return mGceAvdInfo;
        } finally {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.OXYGEN_DEVICE_DIRECT_LEASE_COUNT, 1);
            if (mGceAvdInfo != null && GceStatus.SUCCESS.equals(mGceAvdInfo.getStatus())) {
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.CF_FETCH_ARTIFACT_TIME, fetchTime);
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.CF_LAUNCH_CVD_TIME,
                        System.currentTimeMillis() - startTime);
            }
        }
    }

    /**
     * Attempt to start a gce instance with Acloud.
     *
     * @param ipDevice the initial IP of the GCE instance to run AVD in, <code>null</code> if not
     *     applicable
     * @param user the host running user of AVD, <code>null</code> if not applicable
     * @param offset the device num offset of the AVD in the host, <code>null</code> if not
     *     applicable
     * @param attributes attributes associated with current invocation, used for passing applicable
     *     information down to the GCE instance to be added as VM metadata
     * @return a {@link GceAvdInfo} describing the GCE instance. Could be a BOOT_FAIL instance.
     * @throws TargetSetupError
     */
    private GceAvdInfo startGceWithAcloud(
            String ipDevice, String user, Integer offset, MultiMap<String, String> attributes)
            throws TargetSetupError {
        mGceAvdInfo = null;
        // For debugging purposes bypass.
        if (mGceHost != null && mGceInstanceName != null) {
            mGceAvdInfo =
                    new GceAvdInfo(
                            mGceInstanceName,
                            HostAndPort.fromString(mGceHost)
                                    .withDefaultPort(mDeviceOptions.getRemoteAdbPort()));
            mGceAvdInfo.setIpPreconfigured(ipDevice != null);
            mGceAvdInfo.setDeviceOffset(offset);
            mGceAvdInfo.setInstanceUser(user);
            return mGceAvdInfo;
        }

        mBuildInfo.addBuildAttribute(GCE_IP_PRECONFIGURED_KEY, Boolean.toString(ipDevice != null));

        // Add extra args.
        File reportFile = null;
        try {
            reportFile = FileUtil.createTempFile("gce_avd_driver", ".json");
            // Override the instance name by specified user
            if (user != null) {
                getTestDeviceOptions().setInstanceUser(user);
            }
            List<String> gceArgs =
                    buildGceCmd(reportFile, mBuildInfo, ipDevice, user, offset, attributes);

            long driverTimeoutMs = getTestDeviceOptions().getGceCmdTimeout();
            if (!getTestDeviceOptions().allowGceCmdTimeoutOverride()) {
                long driverTimeoutSec =
                        Duration.ofMillis(driverTimeoutMs - 3 * 60 * 1000).toSeconds();
                // --boot-timeout takes a value in seconds
                gceArgs.add("--boot-timeout");
                gceArgs.add(Long.toString(driverTimeoutSec));
                driverTimeoutMs = driverTimeoutSec * 1000;
            }

            CLog.i("Launching GCE with %s", gceArgs.toString());
            CommandResult cmd =
                    getRunUtil()
                            .runTimedCmd(
                                    getTestDeviceOptions().getGceCmdTimeout(),
                                    gceArgs.toArray(new String[gceArgs.size()]));
            CLog.i("GCE driver stderr: %s", cmd.getStderr());
            String instanceName = extractInstanceName(cmd.getStderr());
            if (instanceName != null) {
                mBuildInfo.addBuildAttribute(GCE_INSTANCE_NAME_KEY, instanceName);
            } else {
                CLog.w("Could not extract an instance name for the gce device.");
            }
            if (CommandStatus.TIMED_OUT.equals(cmd.getStatus())) {
                String errors =
                        String.format(
                                "acloud errors: timeout after %dms, acloud did not return",
                                driverTimeoutMs);
                if (instanceName != null) {
                    // If we managed to parse the instance name, report the boot failure so it
                    // can be shutdown.
                    mGceAvdInfo =
                            new GceAvdInfo(instanceName, null, null, errors, GceStatus.BOOT_FAIL);
                    mGceAvdInfo.setIpPreconfigured(ipDevice != null);
                    mGceAvdInfo.setDeviceOffset(offset);
                    mGceAvdInfo.setInstanceUser(user);
                    return mGceAvdInfo;
                }
                throw new TargetSetupError(
                        errors, mDeviceDescriptor, InfraErrorIdentifier.ACLOUD_TIMED_OUT);
            } else if (!CommandStatus.SUCCESS.equals(cmd.getStatus())) {
                CLog.w("Error when booting the Gce instance, reading output of gce driver");
                mGceAvdInfo =
                        GceAvdInfo.parseGceInfoFromFile(
                                reportFile, mDeviceDescriptor, mDeviceOptions.getRemoteAdbPort());
                String errors = "";
                if (mGceAvdInfo != null) {
                    // We always return the GceAvdInfo describing the instance when possible
                    // The caller can decide actions to be taken.
                    mGceAvdInfo.setIpPreconfigured(ipDevice != null);
                    mGceAvdInfo.setDeviceOffset(offset);
                    mGceAvdInfo.setInstanceUser(user);
                    return mGceAvdInfo;
                } else {
                    errors =
                            "Could not get a valid instance name, check the gce driver's output."
                                    + "The instance may not have booted up at all.";
                    InfraErrorIdentifier errorId = InfraErrorIdentifier.NO_ACLOUD_REPORT;
                    CLog.e(errors);
                    if (cmd.getStderr() != null
                            && cmd.getStderr().contains("Invalid JWT Signature")) {
                        errorId = InfraErrorIdentifier.ACLOUD_INVALID_SERVICE_ACCOUNT_KEY;
                    }
                    throw new TargetSetupError(
                            String.format(
                                    "acloud errors: %s\nGCE driver stderr: %s",
                                    errors, cmd.getStderr()),
                            mDeviceDescriptor,
                            errorId);
                }
            }
            mGceAvdInfo =
                    GceAvdInfo.parseGceInfoFromFile(
                            reportFile, mDeviceDescriptor, mDeviceOptions.getRemoteAdbPort());
            mGceAvdInfo.setIpPreconfigured(ipDevice != null);
            mGceAvdInfo.setDeviceOffset(offset);
            mGceAvdInfo.setInstanceUser(user);
            return mGceAvdInfo;
        } catch (IOException e) {
            throw new TargetSetupError(
                    "failed to create log file",
                    e,
                    mDeviceDescriptor,
                    InfraErrorIdentifier.FAIL_TO_CREATE_FILE);
        } finally {
            logCloudDeviceMetadata();
            FileUtil.deleteFile(reportFile);
        }
    }

    /**
     * Retrieve the instance name from the gce boot logs. Search for the 'name': 'gce-<name>'
     * pattern to extract the name of it. We extract from the logs instead of result file because on
     * gce boot failure, the attempted instance name won't show in json.
     */
    protected String extractInstanceName(String bootupLogs) {
        if (bootupLogs != null) {
            final String pattern = "'name': u?'(((gce-)|(ins-))(.*?))'";
            Pattern namePattern = Pattern.compile(pattern);
            Matcher matcher = namePattern.matcher(bootupLogs);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    /** Build and return the command to launch GCE. Exposed for testing. */
    protected List<String> buildGceCmd(
            File reportFile,
            IBuildInfo b,
            String ipDevice,
            String user,
            Integer offset,
            MultiMap<String, String> attributes) {
        List<String> gceArgs =
                ArrayUtil.list(getTestDeviceOptions().getAvdDriverBinary().getAbsolutePath());
        gceArgs.add(
                TestDeviceOptions.getCreateCommandByInstanceType(
                        getTestDeviceOptions().getInstanceType()));

        if (TestDeviceOptions.InstanceType.CHEEPS.equals(
                getTestDeviceOptions().getInstanceType())) {
            gceArgs.add("--avd-type");
            gceArgs.add("cheeps");

            if (getTestDeviceOptions().getCrosUser() != null
                    && getTestDeviceOptions().getCrosPassword() != null) {
                gceArgs.add("--user");
                gceArgs.add(getTestDeviceOptions().getCrosUser());
                gceArgs.add("--password");
                gceArgs.add(getTestDeviceOptions().getCrosPassword());
            }
        }

        /* If args passed by gce-driver-param contain build-target or build_target, or
        test device options include local-image and cvd-host-package to side load prebuilt virtual
        device images, there is no need to pass the build info from device BuildInfo to gce
        arguments. Otherwise, generate gce args from device BuildInfo. Please refer to acloud
        arguments for the supported format:
        https://android.googlesource.com/platform/tools/acloud/+/refs/heads/master/create/create_args.py  */
        List<String> gceDriverParams = getTestDeviceOptions().getGceDriverParams();
        MultiMap<String, File> gceDriverFileParams =
                getTestDeviceOptions().getGceDriverFileParams();
        if (!gceDriverParams.contains("--build-target")
                && !gceDriverParams.contains("--build_target")
                && !(gceDriverFileParams.containsKey("local-image")
                        && gceDriverFileParams.containsKey("cvd-host-package"))) {
            gceArgs.add("--build-target");
            if (b.getBuildAttributes().containsKey("build_target")) {
                // If BuildInfo contains the attribute for a build target, use that.
                gceArgs.add(b.getBuildAttributes().get("build_target"));
            } else {
                gceArgs.add(b.getBuildFlavor());
            }
            gceArgs.add("--branch");
            gceArgs.add(b.getBuildBranch());
            gceArgs.add("--build-id");
            gceArgs.add(b.getBuildId());
        }

        for (Map.Entry<String, File> entry : gceDriverFileParams.entries()) {
            gceArgs.add("--" + entry.getKey());
            gceArgs.add(entry.getValue().getAbsolutePath());
        }

        // process any info in the invocation context that should be passed onto GCE driver
        // as meta data to be associated with the VM instance
        if (attributes != null) {
            for (String key : getTestDeviceOptions().getInvocationAttributeToMetadata()) {
                for (String value : attributes.get(key)) {
                    gceArgs.add("--gce-metadata");
                    gceArgs.add(String.format("%s:%s", key, value));
                }
            }
        }

        MultiMap<File, String> extraFiles = getTestDeviceOptions().getExtraFiles();
        if (!extraFiles.isEmpty()) {
            gceArgs.add("--extra-files");
            for (File local : extraFiles.keySet()) {
                for (String remoteDestination : extraFiles.get(local)) {
                    gceArgs.add(local.getAbsolutePath() + "," + remoteDestination);
                }
            }
        }

        // Add additional args passed by gce-driver-param.
        gceArgs.addAll(gceDriverParams);
        // Get extra params by instance type
        gceArgs.addAll(
                TestDeviceOptions.getExtraParamsByInstanceType(
                        getTestDeviceOptions().getInstanceType(),
                        getTestDeviceOptions().getBaseImage()));
        if (getAvdConfigFile() != null) {
            gceArgs.add("--config_file");
            gceArgs.add(getAvdConfigFile().getAbsolutePath());
        }
        if (getTestDeviceOptions().getServiceAccountJsonKeyFile() != null) {
            gceArgs.add("--service-account-json-private-key-path");
            gceArgs.add(getTestDeviceOptions().getServiceAccountJsonKeyFile().getAbsolutePath());
        }

        if (ipDevice != null) {
            gceArgs.add("--host");
            gceArgs.add(ipDevice);
            gceArgs.add("--host-user");
            if (user != null) {
                gceArgs.add(user);
            } else {
                gceArgs.add(getTestDeviceOptions().getInstanceUser());
            }
            gceArgs.add("--host-ssh-private-key-path");
            gceArgs.add(getTestDeviceOptions().getSshPrivateKeyPath().getAbsolutePath());
        }
        gceArgs.add("--report_file");
        gceArgs.add(reportFile.getAbsolutePath());

        // Add base-instance-num args with offset, and override the remote adb port.
        // When offset is 1, base-instance-num=2 and virtual device adb forward port is 6521.
        if (offset != null) {
            getTestDeviceOptions().setRemoteAdbPort(6520 + offset);
            gceArgs.add("--base-instance-num");
            gceArgs.add(String.valueOf(offset + 1));
        }
        switch (getTestDeviceOptions().getGceDriverLogLevel()) {
            case DEBUG:
                gceArgs.add("-v");
                break;
            case VERBOSE:
                gceArgs.add("-vv");
                break;
            default:
                break;
        }
        if (getTestDeviceOptions().getGceAccount() != null) {
            gceArgs.add("--email");
            gceArgs.add(getTestDeviceOptions().getGceAccount());
        }
        // Do not pass flags --logcat_file and --serial_log_file to collect logcat and serial logs.

        return gceArgs;
    }

    /**
     * Shutdown the Gce instance associated with the {@link #startGce()}.
     *
     * @return returns true if gce shutdown was requested as non-blocking.
     */
    public boolean shutdownGce() {
        if (getTestDeviceOptions().useOxygen()) {
            return shutdownGceWithOxygen();
        } else {
            return shutdownGceWithAcloud();
        }
    }

    /**
     * Shutdown the Oxygen Gce instance.
     *
     * @return returns true if gce shutdown was requested as non-blocking.
     */
    private boolean shutdownGceWithOxygen() {
        try {
            OxygenClient oxygenClient =
                    new OxygenClient(getTestDeviceOptions().getAvdDriverBinary());
            return oxygenClient.release(mGceAvdInfo, getTestDeviceOptions());
        } finally {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.OXYGEN_DEVICE_DIRECT_RELEASE_COUNT, 1);
            mGceAvdInfo = null;
        }
    }

    /**
     * Shutdown the Acloud Gce instance.
     *
     * @return returns true if gce shutdown was requested as non-blocking.
     */
    private boolean shutdownGceWithAcloud() {
        if (!getTestDeviceOptions().getAvdDriverBinary().canExecute()) {
            mGceAvdInfo = null;
            throw new HarnessRuntimeException(
                    String.format(
                            "GCE launcher %s is invalid",
                            getTestDeviceOptions().getAvdDriverBinary()),
                    InfraErrorIdentifier.CONFIGURED_ARTIFACT_NOT_FOUND);
        }
        String instanceName = null;
        boolean notFromGceAvd = false;
        if (mGceAvdInfo != null) {
            instanceName = mGceAvdInfo.instanceName();
        }
        if (instanceName == null) {
            instanceName = mBuildInfo.getBuildAttributes().get(GCE_INSTANCE_NAME_KEY);
            notFromGceAvd = true;
        }
        if (instanceName == null) {
            CLog.d("No instance to shutdown.");
            return false;
        }
        // hostname is needed for Oxygen cuttlefish to shutdown.
        String hostname = null;
        if (mGceAvdInfo != null && mGceAvdInfo.hostAndPort() != null) {
            hostname = mGceAvdInfo.hostAndPort().getHost();
        }
        boolean ipPreconfigured = false;
        if (mGceAvdInfo != null) {
            ipPreconfigured = mGceAvdInfo.isIpPreconfigured();
        }
        try {
            boolean res =
                    AcloudShutdown(
                            getTestDeviceOptions(),
                            getRunUtil(),
                            instanceName,
                            hostname,
                            ipPreconfigured);
            // Be more lenient if instance name was not reported officially and we still attempt
            // to clean it.
            if (res || notFromGceAvd) {
                mBuildInfo.addBuildAttribute(GCE_INSTANCE_CLEANED_KEY, "true");
            }
            return res;
        } finally {
            mGceAvdInfo = null;
        }
    }

    protected static List<String> buildShutdownCommand(
            File config,
            TestDeviceOptions options,
            String instanceName,
            String hostname,
            boolean isIpPreconfigured) {
        List<String> gceArgs = ArrayUtil.list(options.getAvdDriverBinary().getAbsolutePath());
        gceArgs.add("delete");
        if (options.getServiceAccountJsonKeyFile() != null) {
            gceArgs.add("--service-account-json-private-key-path");
            gceArgs.add(options.getServiceAccountJsonKeyFile().getAbsolutePath());
        }
        if (isIpPreconfigured) {
            gceArgs.add("--host");
            gceArgs.add(hostname);
            gceArgs.add("--host-user");
            gceArgs.add(options.getInstanceUser());
            gceArgs.add("--host-ssh-private-key-path");
            gceArgs.add(options.getSshPrivateKeyPath().getAbsolutePath());
        } else if (config != null) {
            gceArgs.add("--config_file");
            gceArgs.add(config.getAbsolutePath());
        }
        gceArgs.add("--instance_names");
        gceArgs.add(instanceName);
        return gceArgs;
    }

    /**
     * Actual Acloud run to shutdown the virtual device.
     *
     * @param options The {@link TestDeviceOptions} for the Acloud options
     * @param runUtil The {@link IRunUtil} to run Acloud
     * @param instanceName The instance to shutdown.
     * @param hostname hostname of the instance, only used for Oxygen cuttlefish.
     * @param isIpPreconfigured whether the AVD was created on a remote device with preconfigured IP
     * @return True if successful
     */
    public static boolean AcloudShutdown(
            TestDeviceOptions options,
            IRunUtil runUtil,
            String instanceName,
            String hostname,
            boolean isIpPreconfigured) {
        // Add extra args.
        File config = null;
        try {
            File originalConfig = options.getAvdConfigFile();
            if (originalConfig != null) {
                config = FileUtil.createTempFile(originalConfig.getName(), "config");
                // Copy the config in case it comes from a dynamic file. In order to ensure Acloud
                // has the file until it's done with it.
                FileUtil.copyFile(originalConfig, config);
            }
            List<String> gceArgs =
                    buildShutdownCommand(
                            config, options, instanceName, hostname, isIpPreconfigured);
            if (gceArgs == null) {
                CLog.w("Shutdown command as <null>, see earlier logs for reasons.");
                return false;
            }
            CLog.i("Tear down of GCE with %s", gceArgs.toString());
            if (options.waitForGceTearDown()) {
                CommandResult cmd =
                        runUtil.runTimedCmd(
                                options.getGceCmdTimeout(),
                                gceArgs.toArray(new String[gceArgs.size()]));
                FileUtil.deleteFile(config);
                CLog.i(
                        "GCE driver teardown output:\nstdout:%s\nstderr:%s",
                        cmd.getStdout(), cmd.getStderr());
                if (!CommandStatus.SUCCESS.equals(cmd.getStatus())) {
                    CLog.w(
                            "Failed to tear down GCE %s with the following arg: %s.",
                            instanceName, gceArgs);
                    return false;
                }
            } else {
                // Discard the output so the process is not linked to the parent and doesn't die
                // if the JVM exit.
                Process p = runUtil.runCmdInBackground(Redirect.DISCARD, gceArgs);
                if (config != null) {
                    new AcloudDeleteCleaner(p, config).start();
                }
            }
        } catch (IOException ioe) {
            CLog.e("failed to create log file for GCE Teardown");
            CLog.e(ioe);
            FileUtil.deleteFile(config);
            return false;
        }
        return true;
    }

    /**
     * Get a bugreportz from the device using ssh to avoid any adb connection potential issue.
     *
     * @param gceAvd The {@link GceAvdInfo} that describe the device.
     * @param options a {@link TestDeviceOptions} describing the device options to be used for the
     *     GCE device.
     * @param runUtil a {@link IRunUtil} to execute commands.
     * @return A file pointing to the zip bugreport, or null if an issue occurred.
     * @throws IOException
     */
    public static File getBugreportzWithSsh(
            GceAvdInfo gceAvd, TestDeviceOptions options, IRunUtil runUtil) throws IOException {
        String output = remoteSshCommandExec(gceAvd, options, runUtil, "bugreportz");
        Matcher match = BUGREPORTZ_RESPONSE_PATTERN.matcher(output);
        if (!match.find()) {
            CLog.e("Something went wrong during bugreportz collection: '%s'", output);
            return null;
        }
        String remoteFilePath = match.group(2);
        File localTmpFile = FileUtil.createTempFile("bugreport-ssh", ".zip");
        if (!RemoteFileUtil.fetchRemoteFile(
                gceAvd, options, runUtil, REMOTE_FILE_OP_TIMEOUT, remoteFilePath, localTmpFile)) {
            FileUtil.deleteFile(localTmpFile);
            return null;
        }
        return localTmpFile;
    }

    /**
     * Get a bugreport via ssh for a nested instance. This requires requesting the adb in the nested
     * virtual instance.
     *
     * @param gceAvd The {@link GceAvdInfo} that describe the device.
     * @param options a {@link TestDeviceOptions} describing the device options to be used for the
     *     GCE device.
     * @param runUtil a {@link IRunUtil} to execute commands.
     * @return A file pointing to the zip bugreport, or null if an issue occurred.
     * @throws IOException
     */
    public static File getNestedDeviceSshBugreportz(
            GceAvdInfo gceAvd, TestDeviceOptions options, IRunUtil runUtil) throws IOException {
        if (gceAvd == null || gceAvd.hostAndPort() == null) {
            return null;
        }
        String adbTool = "./bin/adb";
        String output;
        if (options.useOxygen()) {
            adbTool = "./tools/dynamic_adb_tool";
            // Make sure the Oxygen device is connected.
            output =
                    remoteSshCommandExec(
                            gceAvd, options, runUtil, adbTool, "connect", "localhost:6520");
            if (output.contains("failed to connect to")) {
                CLog.e("Bugreport collection skipped due to device can't be connected: %s", output);
                return null;
            }
        }

        // TODO(b/280177749): Remove the special logic after Oxygen side is cleaned up.
        if (options.useOxygen()) {
            output =
                    remoteSshCommandExec(
                            gceAvd,
                            options,
                            runUtil,
                            adbTool,
                            "-s",
                            "localhost:6520",
                            "wait-for-device",
                            "shell",
                            "bugreportz");
        } else {
            output =
                    remoteSshCommandExec(
                            gceAvd,
                            options,
                            runUtil,
                            adbTool,
                            "wait-for-device",
                            "shell",
                            "bugreportz");
        }
        Matcher match = BUGREPORTZ_RESPONSE_PATTERN.matcher(output);
        if (!match.find()) {
            CLog.e("Something went wrong during bugreportz collection: '%s'", output);
            return null;
        }
        String deviceFilePath = match.group(2);
        String pullOutput;
        if (options.useOxygen()) {
            pullOutput =
                    remoteSshCommandExec(
                            gceAvd,
                            options,
                            runUtil,
                            adbTool,
                            "-s",
                            "localhost:6520",
                            "pull",
                            deviceFilePath);
        } else {
            pullOutput =
                    remoteSshCommandExec(gceAvd, options, runUtil, adbTool, "pull", deviceFilePath);
        }
        CLog.d(pullOutput);
        String remoteFilePath = "./" + new File(deviceFilePath).getName();
        File localTmpFile = FileUtil.createTempFile("bugreport-ssh", ".zip");
        if (!RemoteFileUtil.fetchRemoteFile(
                gceAvd, options, runUtil, REMOTE_FILE_OP_TIMEOUT, remoteFilePath, localTmpFile)) {
            FileUtil.deleteFile(localTmpFile);
            return null;
        }
        return localTmpFile;
    }

    /**
     * Fetch a remote file from a nested instance and log it.
     *
     * @param logger The {@link ITestLogger} where to log the file.
     * @param gceAvd The {@link GceAvdInfo} that describe the device.
     * @param options a {@link TestDeviceOptions} describing the device options to be used for the
     *     GCE device.
     * @param runUtil a {@link IRunUtil} to execute commands.
     * @param remoteFilePath The remote path where to find the file.
     * @param type the {@link LogDataType} of the logged file.
     * @return whether the file is logged successfully.
     */
    public static boolean logNestedRemoteFile(
            ITestLogger logger,
            GceAvdInfo gceAvd,
            TestDeviceOptions options,
            IRunUtil runUtil,
            String remoteFilePath,
            LogDataType type) {
        return logNestedRemoteFile(logger, gceAvd, options, runUtil, remoteFilePath, type, null);
    }

    /**
     * Fetch a remote file from a nested instance and log it.
     *
     * @param logger The {@link ITestLogger} where to log the file.
     * @param gceAvd The {@link GceAvdInfo} that describe the device.
     * @param options a {@link TestDeviceOptions} describing the device options to be used for the
     *     GCE device.
     * @param runUtil a {@link IRunUtil} to execute commands.
     * @param remoteFilePath The remote path where to find the file.
     * @param type the {@link LogDataType} of the logged file.
     * @param baseName The base name to use to log the file. If null the actual file name will be
     *     used.
     * @return whether the file is logged successfully.
     */
    public static boolean logNestedRemoteFile(
            ITestLogger logger,
            GceAvdInfo gceAvd,
            TestDeviceOptions options,
            IRunUtil runUtil,
            String remoteFilePath,
            LogDataType type,
            String baseName) {
        File remoteFile;
        if (type == LogDataType.DIR) {
            remoteFile =
                    RemoteFileUtil.fetchRemoteDir(
                            gceAvd, options, runUtil, REMOTE_FILE_OP_TIMEOUT, remoteFilePath);

            if (remoteFile != null && remoteFile.listFiles().length == 0) {
                // If the retrieved directory is empty, delete it as there is no file to log anyway
                FileUtil.recursiveDelete(remoteFile);
                return false;
            }
            // Search log files for known failures for devices hosted by Oxygen
            if (options.useOxygen() && remoteFile != null) {
                try (CloseableTraceScope ignore =
                        new CloseableTraceScope("avd:collectErrorSignature")) {
                    List<String> signatures = OxygenUtil.collectErrorSignatures(remoteFile);
                    if (signatures.size() > 0) {
                        InvocationMetricLogger.addInvocationMetrics(
                                InvocationMetricKey.DEVICE_ERROR_SIGNATURES,
                                String.join(",", signatures));
                    }
                }
                try (CloseableTraceScope ignore =
                        new CloseableTraceScope("avd:collectDeviceLaunchMetrics")) {
                    long[] launchMetrics = OxygenUtil.collectDeviceLaunchMetrics(remoteFile);
                    if (launchMetrics[0] > 0) {
                        InvocationMetricLogger.addInvocationMetrics(
                                InvocationMetricKey.CF_FETCH_ARTIFACT_TIME, launchMetrics[0]);
                        InvocationMetricLogger.addInvocationMetrics(
                                InvocationMetricKey.CF_LAUNCH_CVD_TIME, launchMetrics[1]);
                    }
                }
                try (CloseableTraceScope ignore =
                        new CloseableTraceScope("avd:collectOxygenVersion")) {
                    String oxygenVersion = OxygenUtil.collectOxygenVersion(remoteFile);
                    if (!Strings.isNullOrEmpty(oxygenVersion)) {
                        InvocationMetricLogger.addInvocationMetrics(
                                InvocationMetricKey.CF_OXYGEN_VERSION, oxygenVersion);
                    }
                }
            }

            // Default files under a directory to be CUTTLEFISH_LOG to avoid compression.
            type = LogDataType.CUTTLEFISH_LOG;
            if (remoteFile != null) {
                // If we happened to fetch a directory, log all the subfiles
                logDirectory(remoteFile, baseName, logger, type);
                return true;
            }
        } else {
            remoteFile =
                    RemoteFileUtil.fetchRemoteFile(
                            gceAvd, options, runUtil, REMOTE_FILE_OP_TIMEOUT, remoteFilePath);
            if (remoteFile != null) {
                logFile(remoteFile, baseName, logger, type);
                return true;
            }
        }
        return false;
    }

    private static void logDirectory(
            File remoteDirectory, String baseName, ITestLogger logger, LogDataType type) {
        for (File f : remoteDirectory.listFiles()) {
            if (f.isFile()) {
                LogDataType typeFromName = OxygenUtil.getDefaultLogType(f.getName());
                if (!typeFromName.equals(LogDataType.UNKNOWN)) {
                    type = typeFromName;
                }
                logFile(f, baseName, logger, type);
            } else if (f.isDirectory()) {
                logDirectory(f, baseName, logger, type);
            }
        }
    }

    private static void logFile(
            File remoteFile, String baseName, ITestLogger logger, LogDataType type) {
            try (InputStreamSource remoteFileStream = new FileInputStreamSource(remoteFile, true)) {
                String name = baseName;
                if (name == null) {
                    name = remoteFile.getName();
                }
                logger.testLog(name, type, remoteFileStream);
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.CF_LOG_SIZE, remoteFileStream.size());
            }
    }

    /**
     * Execute the remote command via ssh on an instance.
     *
     * @param gceAvd The {@link GceAvdInfo} that describe the device.
     * @param options a {@link TestDeviceOptions} describing the device options to be used for the
     *     GCE device.
     * @param runUtil a {@link IRunUtil} to execute commands.
     * @param timeoutMs The timeout in millisecond for the command. 0 means no timeout.
     * @param command The remote command to execute.
     * @return {@link CommandResult} containing the result of the execution.
     */
    public static CommandResult remoteSshCommandExecution(
            GceAvdInfo gceAvd,
            TestDeviceOptions options,
            IRunUtil runUtil,
            long timeoutMs,
            String... command) {
        return RemoteSshUtil.remoteSshCommandExec(gceAvd, options, runUtil, timeoutMs, command);
    }

    private static String remoteSshCommandExec(
            GceAvdInfo gceAvd, TestDeviceOptions options, IRunUtil runUtil, String... command) {
        CommandResult res =
                remoteSshCommandExecution(gceAvd, options, runUtil, BUGREPORT_TIMEOUT, command);
        // We attempt to get a clean output from our command
        String output = res.getStdout().trim();
        if (!CommandStatus.SUCCESS.equals(res.getStatus())) {
            CLog.e("issue when attempting to execute '%s':", Arrays.asList(command));
            CLog.e("Stderr: %s", res.getStderr());
        } else if (output.isEmpty()) {
            CLog.e("Stdout from '%s' was empty", Arrays.asList(command));
            CLog.e("Stderr: %s", res.getStderr());
        }
        return output;
    }

    /**
     * Reads the current content of the Gce Avd instance serial log.
     *
     * @param infos The {@link GceAvdInfo} describing the instance.
     * @param avdConfigFile the avd config file
     * @param jsonKeyFile the service account json key file.
     * @param runUtil a {@link IRunUtil} to execute commands.
     * @return The serial log output or null if something goes wrong.
     */
    public static String getInstanceSerialLog(
            GceAvdInfo infos, File avdConfigFile, File jsonKeyFile, IRunUtil runUtil) {
        AcloudConfigParser config = AcloudConfigParser.parseConfig(avdConfigFile);
        if (config == null) {
            CLog.e("Failed to parse our acloud config.");
            return null;
        }
        if (infos == null) {
            return null;
        }
        try {
            Credential credential = createCredential(config, jsonKeyFile);
            String project = config.getValueForKey(AcloudKeys.PROJECT);
            String zone = config.getValueForKey(AcloudKeys.ZONE);
            String instanceName = infos.instanceName();
            Compute compute =
                    new Compute.Builder(
                                    GoogleNetHttpTransport.newTrustedTransport(),
                                    JSON_FACTORY,
                                    null)
                            .setApplicationName(project)
                            .setHttpRequestInitializer(credential)
                            .build();
            GetSerialPortOutput outputPort =
                    compute.instances().getSerialPortOutput(project, zone, instanceName);
            SerialPortOutput output = outputPort.execute();
            return output.getContents();
        } catch (GeneralSecurityException | IOException e) {
            CLog.e(e);
            return null;
        }
    }

    private static Credential createCredential(AcloudConfigParser config, File jsonKeyFile)
            throws GeneralSecurityException, IOException {
        if (jsonKeyFile != null) {
            return GoogleApiClientUtil.createCredentialFromJsonKeyFile(jsonKeyFile, SCOPES);
        } else if (config.getValueForKey(AcloudKeys.SERVICE_ACCOUNT_JSON_PRIVATE_KEY) != null) {
            jsonKeyFile =
                    new File(config.getValueForKey(AcloudKeys.SERVICE_ACCOUNT_JSON_PRIVATE_KEY));
            return GoogleApiClientUtil.createCredentialFromJsonKeyFile(jsonKeyFile, SCOPES);
        } else {
            String serviceAccount = config.getValueForKey(AcloudKeys.SERVICE_ACCOUNT_NAME);
            String serviceKey = config.getValueForKey(AcloudKeys.SERVICE_ACCOUNT_PRIVATE_KEY);
            return GoogleApiClientUtil.createCredentialFromP12File(
                    serviceAccount, new File(serviceKey), SCOPES);
        }
    }

    public void cleanUp() {
        // Clean up logs file if any was created.
    }

    /** Returns the instance of the {@link IRunUtil}. */
    @VisibleForTesting
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * Log the serial output of a device described by {@link GceAvdInfo}.
     *
     * @param infos The {@link GceAvdInfo} describing the instance.
     * @param logger The {@link ITestLogger} where to log the serial log.
     */
    public void logSerialOutput(GceAvdInfo infos, ITestLogger logger) {
        if (mSkipSerialLogCollection) {
            CLog.d("Serial log collection is skipped");
            return;
        }
        String output =
                GceManager.getInstanceSerialLog(
                        infos,
                        getAvdConfigFile(),
                        getTestDeviceOptions().getServiceAccountJsonKeyFile(),
                        getRunUtil());
        if (output == null) {
            CLog.w("Failed to collect the instance serial logs.");
            return;
        }
        try (ByteArrayInputStreamSource source =
                new ByteArrayInputStreamSource(output.getBytes())) {
            logger.testLog("gce_full_serial_log", LogDataType.TEXT, source);
        }
    }

    /** Log the information related to the acloud config. */
    private void logCloudDeviceMetadata() {
        AcloudConfigParser config = AcloudConfigParser.parseConfig(getAvdConfigFile());
        if (config == null) {
            CLog.e("Failed to parse our acloud config.");
            return;
        }
        if (config.getValueForKey(AcloudKeys.STABLE_HOST_IMAGE_NAME) != null) {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.CLOUD_DEVICE_STABLE_HOST_IMAGE,
                    config.getValueForKey(AcloudKeys.STABLE_HOST_IMAGE_NAME));
        }
        if (config.getValueForKey(AcloudKeys.STABLE_HOST_IMAGE_PROJECT) != null) {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.CLOUD_DEVICE_STABLE_HOST_IMAGE_PROJECT,
                    config.getValueForKey(AcloudKeys.STABLE_HOST_IMAGE_PROJECT));
        }
        if (config.getValueForKey(AcloudKeys.PROJECT) != null) {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.CLOUD_DEVICE_PROJECT,
                    config.getValueForKey(AcloudKeys.PROJECT));
        }
        if (config.getValueForKey(AcloudKeys.ZONE) != null) {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.CLOUD_DEVICE_ZONE, config.getValueForKey(AcloudKeys.ZONE));
        }
        if (config.getValueForKey(AcloudKeys.MACHINE_TYPE) != null) {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.CLOUD_DEVICE_MACHINE_TYPE,
                    config.getValueForKey(AcloudKeys.MACHINE_TYPE));
        }
    }

    /**
     * Returns the {@link TestDeviceOptions} associated with the device that the gce manager was
     * initialized with.
     */
    private TestDeviceOptions getTestDeviceOptions() {
        return mDeviceOptions;
    }

    @VisibleForTesting
    File getAvdConfigFile() {
        return getTestDeviceOptions().getAvdConfigFile();
    }

    /**
     * Thread that helps cleaning the copied config when the process is done. This ensures acloud is
     * not missing its config until its done.
     */
    private static class AcloudDeleteCleaner extends Thread {
        private Process mProcess;
        private File mConfigFile;

        public AcloudDeleteCleaner(Process p, File config) {
            setDaemon(true);
            setName("acloud-delete-cleaner");
            mProcess = p;
            mConfigFile = config;
        }

        @Override
        public void run() {
            try {
                mProcess.waitFor();
            } catch (InterruptedException e) {
                CLog.e(e);
            }
            FileUtil.deleteFile(mConfigFile);
        }
    }
}
