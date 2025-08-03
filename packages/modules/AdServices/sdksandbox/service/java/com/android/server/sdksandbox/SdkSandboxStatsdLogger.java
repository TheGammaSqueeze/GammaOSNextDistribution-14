/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.sdksandbox;

import android.app.sdksandbox.SandboxLatencyInfo;
import android.os.Binder;

import com.android.sdksandbox.service.stats.SdkSandboxStatsLog;

/**
 * Helper class to handle StatsD metrics logging logic.
 *
 * @hide
 */
class SdkSandboxStatsdLogger {
    private static final String TAG = SdkSandboxStatsdLogger.class.getSimpleName();

    /**
     * Send sandbox API call latency data to StatsD. Corresponding StatsD atom is SandboxApiCalled.
     */
    public void logSandboxApiLatency(SandboxLatencyInfo sandboxLatencyInfo) {
        int method = convertToStatsLogMethodCode(sandboxLatencyInfo.getMethod());
        if (method == SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__METHOD_UNSPECIFIED) {
            return;
        }
        int callingUid = Binder.getCallingUid();

        logSandboxApiLatencyForStage(
                method,
                sandboxLatencyInfo.getAppToSystemServerLatency(),
                sandboxLatencyInfo.isSuccessfulAtAppToSystemServer(),
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER,
                callingUid);
        logSandboxApiLatencyForStage(
                method,
                sandboxLatencyInfo.getSystemServerAppToSandboxLatency(),
                sandboxLatencyInfo.isSuccessfulAtSystemServerAppToSandbox(),
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                callingUid);
        logSandboxApiLatencyForStage(
                method,
                sandboxLatencyInfo.getLoadSandboxLatency(),
                sandboxLatencyInfo.isSuccessfulAtLoadSandbox(),
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__LOAD_SANDBOX,
                callingUid);
        logSandboxApiLatencyForStage(
                method,
                sandboxLatencyInfo.getSystemServerToSandboxLatency(),
                sandboxLatencyInfo.isSuccessfulAtSystemServerToSandbox(),
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_TO_SANDBOX,
                callingUid);
        logSandboxApiLatencyForStage(
                method,
                sandboxLatencyInfo.getSandboxLatency(),
                sandboxLatencyInfo.isSuccessfulAtSandbox(),
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SANDBOX,
                callingUid);
        logSandboxApiLatencyForStage(
                method,
                sandboxLatencyInfo.getSdkLatency(),
                sandboxLatencyInfo.isSuccessfulAtSdk(),
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SDK,
                callingUid);
        logSandboxApiLatencyForStage(
                method,
                sandboxLatencyInfo.getSandboxToSystemServerLatency(),
                sandboxLatencyInfo.isSuccessfulAtSandboxToSystemServer(),
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SANDBOX_TO_SYSTEM_SERVER,
                callingUid);
        logSandboxApiLatencyForStage(
                method,
                sandboxLatencyInfo.getSystemServerSandboxToAppLatency(),
                sandboxLatencyInfo.isSuccessfulAtSystemServerSandboxToApp(),
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_SANDBOX_TO_APP,
                callingUid);
        logSandboxApiLatencyForStage(
                method,
                sandboxLatencyInfo.getSystemServerToAppLatency(),
                sandboxLatencyInfo.isSuccessfulAtSystemServerToApp(),
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_TO_APP,
                callingUid);

        int totalCallStage = SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__TOTAL;
        if (method == SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK
                && sandboxLatencyInfo.getLoadSandboxLatency() != -1) {
            totalCallStage = SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__TOTAL_WITH_LOAD_SANDBOX;
        }
        logSandboxApiLatencyForStage(
                method,
                sandboxLatencyInfo.getTotalCallLatency(),
                sandboxLatencyInfo.isTotalCallSuccessful(),
                totalCallStage,
                callingUid);
    }

    private int convertToStatsLogMethodCode(int method) {
        return switch (method) {
            case SandboxLatencyInfo.METHOD_LOAD_SDK -> SdkSandboxStatsLog
                    .SANDBOX_API_CALLED__METHOD__LOAD_SDK;
            case SandboxLatencyInfo.METHOD_GET_SANDBOXED_SDKS -> SdkSandboxStatsLog
                    .SANDBOX_API_CALLED__METHOD__GET_SANDBOXED_SDKS;
            case SandboxLatencyInfo.METHOD_SYNC_DATA_FROM_CLIENT -> SdkSandboxStatsLog
                    .SANDBOX_API_CALLED__METHOD__SYNC_DATA_FROM_CLIENT;
            case SandboxLatencyInfo.METHOD_REQUEST_SURFACE_PACKAGE -> SdkSandboxStatsLog
                    .SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE;
            case SandboxLatencyInfo
                    .METHOD_REGISTER_APP_OWNED_SDK_SANDBOX_INTERFACE -> SdkSandboxStatsLog
                    .SANDBOX_API_CALLED__METHOD__REGISTER_APP_OWNED_SDK_SANDBOX_INTERFACE;
            case SandboxLatencyInfo
                    .METHOD_UNREGISTER_APP_OWNED_SDK_SANDBOX_INTERFACE -> SdkSandboxStatsLog
                    .SANDBOX_API_CALLED__METHOD__UNREGISTER_APP_OWNED_SDK_SANDBOX_INTERFACE;
            case SandboxLatencyInfo
                    .METHOD_GET_APP_OWNED_SDK_SANDBOX_INTERFACES -> SdkSandboxStatsLog
                    .SANDBOX_API_CALLED__METHOD__GET_APP_OWNED_SDK_SANDBOX_INTERFACES;
            case SandboxLatencyInfo.METHOD_UNLOAD_SDK -> SdkSandboxStatsLog
                    .SANDBOX_API_CALLED__METHOD__UNLOAD_SDK;
            case SandboxLatencyInfo.METHOD_ADD_SDK_SANDBOX_LIFECYCLE_CALLBACK -> SdkSandboxStatsLog
                    .SANDBOX_API_CALLED__METHOD__ADD_SDK_SANDBOX_LIFECYCLE_CALLBACK;
            case SandboxLatencyInfo
                    .METHOD_REMOVE_SDK_SANDBOX_LIFECYCLE_CALLBACK -> SdkSandboxStatsLog
                    .SANDBOX_API_CALLED__METHOD__REMOVE_SDK_SANDBOX_LIFECYCLE_CALLBACK;
            default -> SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__METHOD_UNSPECIFIED;
        };
    }

    private void logSandboxApiLatencyForStage(
            int method, int latency, boolean success, int stage, int callingUid) {
        if (latency != -1) {
            SdkSandboxStatsLog.write(
                    SdkSandboxStatsLog.SANDBOX_API_CALLED,
                    method,
                    latency,
                    success,
                    stage,
                    callingUid);
        }
    }

    /**
     * Send sandbox activity API call latency data to StatsD. Corresponding StatsD atom is
     * SandboxActivityEventOccurred.
     */
    public void logSandboxActivityApiLatency(
            int method, int callResult, int latencyMillis, int clientUid) {
        SdkSandboxStatsLog.write(
                SdkSandboxStatsLog.SANDBOX_ACTIVITY_EVENT_OCCURRED,
                method,
                callResult,
                latencyMillis,
                clientUid,
                /*sdkUid=*/ -1);
    }
}
