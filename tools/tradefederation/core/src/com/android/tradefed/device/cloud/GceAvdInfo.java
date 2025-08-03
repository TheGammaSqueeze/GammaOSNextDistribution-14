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

import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.error.ErrorIdentifier;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HostAndPort;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Structure to hold relevant data for a given GCE AVD instance. */
public class GceAvdInfo {

    // Patterns to match from Oxygen client's return message to identify error.
    private static final LinkedHashMap<InfraErrorIdentifier, String> OXYGEN_ERROR_PATTERN_MAP;

    static {
        OXYGEN_ERROR_PATTERN_MAP = new LinkedHashMap<InfraErrorIdentifier, String>();
        // Order the error message matching carefully so it can surface the expected error properly
        OXYGEN_ERROR_PATTERN_MAP.put(
                InfraErrorIdentifier.OXYGEN_SERVER_SHUTTING_DOWN, "server_shutting_down");
        OXYGEN_ERROR_PATTERN_MAP.put(
                InfraErrorIdentifier.OXYGEN_BAD_GATEWAY_ERROR, "UNAVAILABLE: HTTP status code 502");
        OXYGEN_ERROR_PATTERN_MAP.put(
                InfraErrorIdentifier.OXYGEN_REQUEST_TIMEOUT, "DeadlineExceeded");
        OXYGEN_ERROR_PATTERN_MAP.put(
                InfraErrorIdentifier.OXYGEN_RESOURCE_EXHAUSTED, "ResourceExhausted");
        OXYGEN_ERROR_PATTERN_MAP.put(
                InfraErrorIdentifier.OXYGEN_NOT_ENOUGH_RESOURCE,
                "Oxygen currently doesn't have enough resources to fulfil this request");
        OXYGEN_ERROR_PATTERN_MAP.put(
                InfraErrorIdentifier.OXYGEN_SERVER_CONNECTION_FAILURE, "Bad Gateway");
        OXYGEN_ERROR_PATTERN_MAP.put(
                InfraErrorIdentifier.OXYGEN_CLIENT_LEASE_ERROR, "OxygenClient");
        OXYGEN_ERROR_PATTERN_MAP.put(
                InfraErrorIdentifier.OXYGEN_DEVICE_LAUNCHER_TIMEOUT,
                "Lease aborted due to launcher failure: Timed out waiting for virtual device to"
                        + " start");
        OXYGEN_ERROR_PATTERN_MAP.put(
                InfraErrorIdentifier.OXYGEN_DEVICE_LAUNCHER_FAILURE,
                "Lease aborted due to launcher failure");
        OXYGEN_ERROR_PATTERN_MAP.put(
                InfraErrorIdentifier.OXYGEN_SERVER_LB_CONNECTION_ERROR, "desc = connection error");
    }

    // Error message for specify Oxygen error.
    private static final ImmutableMap<InfraErrorIdentifier, String> OXYGEN_ERROR_MESSAGE_MAP =
            ImmutableMap.of(
                    InfraErrorIdentifier.OXYGEN_DEVICE_LAUNCHER_FAILURE,
                            "AVD failed to boot up properly",
                    InfraErrorIdentifier.OXYGEN_SERVER_SHUTTING_DOWN,
                            "Unexpected error from Oxygen service",
                    InfraErrorIdentifier.OXYGEN_BAD_GATEWAY_ERROR,
                            "Unexpected error from Oxygen service",
                    InfraErrorIdentifier.OXYGEN_REQUEST_TIMEOUT,
                            "Unexpected error from Oxygen service. Request timed out.",
                    InfraErrorIdentifier.OXYGEN_RESOURCE_EXHAUSTED,
                            "Oxygen ran out of capacity to lease virtual device",
                    InfraErrorIdentifier.OXYGEN_SERVER_CONNECTION_FAILURE,
                            "Unexpected error from Oxygen service",
                    InfraErrorIdentifier.OXYGEN_CLIENT_LEASE_ERROR,
                            "Oxygen client failed to lease a device",
                    InfraErrorIdentifier.OXYGEN_DEVICE_LAUNCHER_TIMEOUT, "AVD boot timed out");

    public static class LogFileEntry {
        public final String path;
        public final LogDataType type;
        // The name is optional and defaults to an empty string.
        public final String name;

        @VisibleForTesting
        LogFileEntry(String path, LogDataType type, String name) {
            this.path = path;
            this.type = type;
            this.name = name;
        }

        LogFileEntry(JSONObject log) throws JSONException {
            path = log.getString("path");
            type = parseLogDataType(log.getString("type"));
            name = log.optString("name", "");
        }

        private LogDataType parseLogDataType(String typeString) {
            try {
                return LogDataType.valueOf(typeString);
            } catch (IllegalArgumentException e) {
                CLog.w("Unknown log type in GCE AVD info: %s", typeString);
                return LogDataType.UNKNOWN;
            }
        }
    }

    public static final List<String> BUILD_VARS =
            Arrays.asList(
                    "build_id",
                    "build_target",
                    "branch",
                    "kernel_build_id",
                    "kernel_build_target",
                    "kernel_branch",
                    "system_build_id",
                    "system_build_target",
                    "system_branch",
                    "emulator_build_id",
                    "emulator_build_target",
                    "emulator_branch");

    private String mInstanceName;
    private HostAndPort mHostAndPort;
    private ErrorIdentifier mErrorType;
    private String mErrors;
    private GceStatus mStatus;
    private HashMap<String, String> mBuildVars;
    private List<LogFileEntry> mLogs;
    private boolean mIsIpPreconfigured = false;
    private Integer mDeviceOffset = null;
    private String mInstanceUser = null;
    // Skip collecting bugreport if set to true.
    private boolean mSkipBugreportCollection = false;

    public static enum GceStatus {
        SUCCESS,
        FAIL,
        BOOT_FAIL,
        DEVICE_OFFLINE,
    }

    public GceAvdInfo(String instanceName, HostAndPort hostAndPort) {
        mInstanceName = instanceName;
        mHostAndPort = hostAndPort;
        mBuildVars = new HashMap<String, String>();
        mLogs = new ArrayList<LogFileEntry>();
    }

    public GceAvdInfo(
            String instanceName,
            HostAndPort hostAndPort,
            ErrorIdentifier errorType,
            String errors,
            GceStatus status) {
        this(instanceName, hostAndPort);
        mErrorType = errorType;
        mErrors = errors;
        mStatus = status;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "GceAvdInfo [mInstanceName="
                + mInstanceName
                + ", mHostAndPort="
                + mHostAndPort
                + ", mDeviceOffset="
                + mDeviceOffset
                + ", mInstanceUser="
                + mInstanceUser
                + ", mErrorType="
                + mErrorType
                + ", mErrors="
                + mErrors
                + ", mStatus="
                + mStatus
                + ", mIsIpPreconfigured="
                + mIsIpPreconfigured
                + ", mBuildVars="
                + mBuildVars.toString()
                + ", mLogs="
                + mLogs.toString()
                + "]";
    }

    public String instanceName() {
        return mInstanceName;
    }

    public HostAndPort hostAndPort() {
        return mHostAndPort;
    }

    public ErrorIdentifier getErrorType() {
        return mErrorType;
    }

    public void setErrorType(ErrorIdentifier errorType) {
        mErrorType = errorType;
    }

    public String getErrors() {
        return mErrors;
    }

    public void setErrors(String errors) {
        mErrors = errors;
    }

    /** Return the map from local or remote log paths to types. */
    public List<LogFileEntry> getLogs() {
        return mLogs;
    }

    public GceStatus getStatus() {
        return mStatus;
    }

    public void setStatus(GceStatus status) {
        mStatus = status;
    }

    private void addBuildVar(String buildKey, String buildValue) {
        mBuildVars.put(buildKey, buildValue);
    }

    public void setIpPreconfigured(boolean isIpPreconfigured) {
        mIsIpPreconfigured = isIpPreconfigured;
    }

    public boolean isIpPreconfigured() {
        return mIsIpPreconfigured;
    }

    public void setDeviceOffset(Integer deviceOffset) {
        mDeviceOffset = deviceOffset;
    }

    public Integer getDeviceOffset() {
        return mDeviceOffset;
    }

    public void setInstanceUser(String instanceUser) {
        mInstanceUser = instanceUser;
    }

    public String getInstanceUser() {
        return mInstanceUser;
    }

    /**
     * Return build variable information hash of GCE AVD device.
     *
     * <p>Possible build variables keys are described in BUILD_VARS for example: build_id,
     * build_target, branch, kernel_build_id, kernel_build_target, kernel_branch, system_build_id,
     * system_build_target, system_branch, emulator_build_id, emulator_build_target,
     * emulator_branch.
     */
    public HashMap<String, String> getBuildVars() {
        return new HashMap<String, String>(mBuildVars);
    }

    public boolean getSkipBugreportCollection() {
        return mSkipBugreportCollection;
    }

    public void setSkipBugreportCollection(boolean skipBugreportCollection) {
        mSkipBugreportCollection = skipBugreportCollection;
    }

    /**
     * Parse a given file to obtain the GCE AVD device info.
     *
     * @param f {@link File} file to read the JSON output from GCE Driver.
     * @param descriptor the descriptor of the device that needs the info.
     * @param remoteAdbPort the remote port that should be used for adb connection
     * @return the {@link GceAvdInfo} of the device if found, or null if error.
     */
    public static GceAvdInfo parseGceInfoFromFile(
            File f, DeviceDescriptor descriptor, int remoteAdbPort) throws TargetSetupError {
        String data;
        try {
            data = FileUtil.readStringFromFile(f);
        } catch (IOException e) {
            CLog.e("Failed to read result file from GCE driver:");
            CLog.e(e);
            return null;
        }
        return parseGceInfoFromString(data, descriptor, remoteAdbPort);
    }

    /**
     * Parse a given string to obtain the GCE AVD device info.
     *
     * @param data JSON string.
     * @param descriptor the descriptor of the device that needs the info.
     * @param remoteAdbPort the remote port that should be used for adb connection
     * @return the {@link GceAvdInfo} of the device if found, or null if error.
     */
    public static GceAvdInfo parseGceInfoFromString(
            String data, DeviceDescriptor descriptor, int remoteAdbPort) throws TargetSetupError {
        if (Strings.isNullOrEmpty(data)) {
            CLog.w("No data provided");
            return null;
        }
        InfraErrorIdentifier errorId = null;
        String errors = data;
        try {
            errors = parseErrorField(data);
            JSONObject res = new JSONObject(data);
            String status = res.getString("status");
            GceStatus gceStatus = GceStatus.valueOf(status);
            String errorType = res.has("error_type") ? res.getString("error_type") : null;
            if (errorType == null) {
                // Parse more detailed error type if we can.
                if (errors.contains("QUOTA_EXCEED") && errors.contains("GPU")) {
                    errorType = "ACLOUD_QUOTA_EXCEED_GPU";
                }
            }
            errorId =
                    GceStatus.SUCCESS.equals(gceStatus)
                            ? null
                            : determineAcloudErrorType(errorType);
            if (errorId == InfraErrorIdentifier.ACLOUD_OXYGEN_LEASE_ERROR) {
                errorId = refineOxygenErrorType(errors);
            }
            JSONArray devices = null;
            if (GceStatus.FAIL.equals(gceStatus) || GceStatus.BOOT_FAIL.equals(gceStatus)) {
                // In case of failure we still look for instance name to shutdown if needed.
                if (res.getJSONObject("data").has("devices_failing_boot")) {
                    devices = res.getJSONObject("data").getJSONArray("devices_failing_boot");
                }
            } else {
                devices = res.getJSONObject("data").getJSONArray("devices");
            }
            if (devices != null) {
                if (devices.length() == 1) {
                    JSONObject d = (JSONObject) devices.get(0);
                    addCfStartTimeMetrics(d);
                    String ip = d.getString("ip");
                    String instanceName = d.getString("instance_name");
                    GceAvdInfo avdInfo =
                            new GceAvdInfo(
                                    instanceName,
                                    HostAndPort.fromString(ip).withDefaultPort(remoteAdbPort),
                                    errorId,
                                    errors,
                                    gceStatus);
                    avdInfo.mLogs.addAll(parseLogField(d));
                    for (String buildVar : BUILD_VARS) {
                        if (d.has(buildVar) && !d.getString(buildVar).trim().isEmpty()) {
                            avdInfo.addBuildVar(buildVar, d.getString(buildVar).trim());
                        }
                    }
                    return avdInfo;
                } else {
                    CLog.w("Expected only one device to return but found %d", devices.length());
                }
            } else {
                CLog.w("No device information, device was not started.");
            }
        } catch (JSONException e) {
            CLog.e("Failed to parse JSON %s:", data);
            CLog.e(e);
        }

        // If errors are found throw an exception with the acloud message.
        if (errorId == null) {
            errorId = InfraErrorIdentifier.ACLOUD_UNDETERMINED;
        }
        throw new TargetSetupError(
                String.format("acloud errors: %s", !errors.isEmpty() ? errors : data),
                descriptor,
                errorId);
    }

    /**
     * Parse a given command line output from Oxygen client binary to obtain leased AVD info.
     *
     * @param oxygenRes the {@link CommandResult} from Oxygen client command execution.
     * @param remoteAdbPort the remote port that should be used for adb connection
     * @return {@link List} of the devices successfully leased. Will throw {@link TargetSetupError}
     *     if failed to lease a device.
     */
    public static List<GceAvdInfo> parseGceInfoFromOxygenClientOutput(
            CommandResult oxygenRes, int remoteAdbPort) throws TargetSetupError {
        CommandStatus oxygenCliStatus = oxygenRes.getStatus();
        if (CommandStatus.SUCCESS.equals(oxygenCliStatus)) {
            return parseSucceedOxygenClientOutput(
                    oxygenRes.getStdout() + oxygenRes.getStderr(), remoteAdbPort);
        } else if (CommandStatus.TIMED_OUT.equals(oxygenCliStatus)) {
            return Arrays.asList(
                    new GceAvdInfo(
                            null,
                            null,
                            InfraErrorIdentifier.OXYGEN_CLIENT_BINARY_TIMEOUT,
                            "Oxygen client binary CLI timed out",
                            GceStatus.FAIL));
        } else {
            CLog.d(
                    "OxygenClient - CommandStatus: %s, output: %s\", output",
                    oxygenCliStatus, oxygenRes.getStdout() + " " + oxygenRes.getStderr());
            InfraErrorIdentifier identifier = refineOxygenErrorType(oxygenRes.getStderr());
            throw new TargetSetupError(
                    OXYGEN_ERROR_MESSAGE_MAP.getOrDefault(
                            identifier, "Oxygen client failed to lease a device"),
                    new Exception(
                            oxygenRes.getStderr()), // Include the original error message as cause.
                    identifier);
        }
    }

    private static List<GceAvdInfo> parseSucceedOxygenClientOutput(String output, int remoteAdbPort)
            throws TargetSetupError {
        CLog.d("Parsing oxygen client output: %s", output);

        Pattern pattern =
                Pattern.compile(
                        "session_id:\"(.*?)\".*?server_url:\"(.*?)\".*?oxygen_version:\"(.*?)\"",
                        Pattern.DOTALL);
        Matcher matcher = pattern.matcher(output);

        List<GceAvdInfo> gceAvdInfos = new ArrayList<>();
        int deviceOffset = 0;
        while (matcher.find()) {
            String sessionId = matcher.group(1);
            String serverUrl = matcher.group(2);
            String oxygenVersion = matcher.group(3);
            gceAvdInfos.add(
                    new GceAvdInfo(
                            sessionId,
                            HostAndPort.fromString(serverUrl)
                                    .withDefaultPort(remoteAdbPort + deviceOffset),
                            null,
                            null,
                            GceStatus.SUCCESS));
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.CF_OXYGEN_VERSION, oxygenVersion);
            deviceOffset++;
        }
        if (gceAvdInfos.isEmpty()) {
            throw new TargetSetupError(
                    String.format("Failed to parse the output: %s", output),
                    InfraErrorIdentifier.OXYGEN_CLIENT_BINARY_ERROR);
        }

        return gceAvdInfos;
    }

    /**
     * Search error message from Oxygen service for more accurate error code.
     *
     * @param errors error messages returned by Oxygen service.
     * @return InfraErrorIdentifier for the Oxygen service error.
     */
    @VisibleForTesting
    static InfraErrorIdentifier refineOxygenErrorType(String errors) {
        for (Map.Entry<InfraErrorIdentifier, String> entry : OXYGEN_ERROR_PATTERN_MAP.entrySet()) {
            if (errors.contains(entry.getValue()))
                return entry.getKey();
         }

        return InfraErrorIdentifier.ACLOUD_OXYGEN_LEASE_ERROR;
    }

    private static String parseErrorField(String data) throws JSONException {
        String res = "";
        JSONObject response = new JSONObject(data);
        JSONArray errors = response.getJSONArray("errors");
        for (int i = 0; i < errors.length(); i++) {
            res += (errors.getString(i) + "\n");
        }
        return res;
    }

    /**
     * Parse log paths from a device object.
     *
     * @param device the device object in JSON.
     * @return a list of {@link LogFileEntry}.
     * @throws JSONException if any required property is missing.
     */
    private static List<LogFileEntry> parseLogField(JSONObject device) throws JSONException {
        List<LogFileEntry> logs = new ArrayList<LogFileEntry>();
        JSONArray logArray = device.optJSONArray("logs");
        if (logArray == null) {
            return logs;
        }
        for (int i = 0; i < logArray.length(); i++) {
            JSONObject logObject = logArray.getJSONObject(i);
            logs.add(new LogFileEntry(logObject));
        }
        return logs;
    }

    @VisibleForTesting
    static InfraErrorIdentifier determineAcloudErrorType(String errorType) {
        InfraErrorIdentifier identifier;
        if (errorType == null || errorType.isEmpty()) {
            return InfraErrorIdentifier.ACLOUD_UNRECOGNIZED_ERROR_TYPE;
        }
        try {
            identifier = InfraErrorIdentifier.valueOf(errorType);
        } catch (Exception e) {
            identifier = InfraErrorIdentifier.ACLOUD_UNRECOGNIZED_ERROR_TYPE;
        }
        return identifier;
    }

    @VisibleForTesting
    static void addCfStartTimeMetrics(JSONObject json) {
        // These metrics may not be available for all GCE.
        String fetch_artifact_time = json.optString("fetch_artifact_time");
        if (!fetch_artifact_time.isEmpty()) {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.CF_FETCH_ARTIFACT_TIME,
                    Double.valueOf(Double.parseDouble(fetch_artifact_time) * 1000).longValue());
        }
        String gce_create_time = json.optString("gce_create_time");
        if (!gce_create_time.isEmpty()) {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.CF_GCE_CREATE_TIME,
                    Double.valueOf(Double.parseDouble(gce_create_time) * 1000).longValue());
        }
        String launch_cvd_time = json.optString("launch_cvd_time");
        if (!launch_cvd_time.isEmpty()) {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.CF_LAUNCH_CVD_TIME,
                    Double.valueOf(Double.parseDouble(launch_cvd_time) * 1000).longValue());
        }
        JSONObject fetch_cvd_wrapper_log = json.optJSONObject("fetch_cvd_wrapper_log");
        if (fetch_cvd_wrapper_log != null) {
            String cf_cache_wait_time_sec =
                    fetch_cvd_wrapper_log.optString("cf_cache_wait_time_sec");
            if (!Strings.isNullOrEmpty(cf_cache_wait_time_sec)) {
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.CF_CACHE_WAIT_TIME,
                        Integer.parseInt(cf_cache_wait_time_sec));
            }
            String cf_artifacts_fetch_source =
                    fetch_cvd_wrapper_log.optString("cf_artifacts_fetch_source");
            if (!Strings.isNullOrEmpty(cf_artifacts_fetch_source)) {
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.CF_ARTIFACTS_FETCH_SOURCE, cf_artifacts_fetch_source);
            }
        }

        if (!InvocationMetricLogger.getInvocationMetrics()
                .containsKey(InvocationMetricKey.CF_INSTANCE_COUNT.toString())) {
            InvocationMetricLogger.addInvocationMetrics(InvocationMetricKey.CF_INSTANCE_COUNT, 1);
        }
    }
}
