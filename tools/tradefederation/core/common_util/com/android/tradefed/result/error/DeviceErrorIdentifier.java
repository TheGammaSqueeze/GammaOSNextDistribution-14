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
package com.android.tradefed.result.error;

import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;

import javax.annotation.Nonnull;

/** Error Identifiers from Device errors and device reported errors. */
public enum DeviceErrorIdentifier implements ErrorIdentifier {

    // ********************************************************************************************
    // Device Errors: 520_001 ~ 530_000
    // ********************************************************************************************
    APK_INSTALLATION_FAILED(520_001, FailureStatus.DEPENDENCY_ISSUE),
    FAIL_ACTIVATE_APEX(520_002, FailureStatus.DEPENDENCY_ISSUE),
    APEX_ROLLBACK_FAILED(520_003, FailureStatus.DEPENDENCY_ISSUE),

    AAPT_PARSER_FAILED(520_050, FailureStatus.DEPENDENCY_ISSUE),

    SHELL_COMMAND_ERROR(520_100, FailureStatus.DEPENDENCY_ISSUE),
    DEVICE_UNEXPECTED_RESPONSE(520_101, FailureStatus.DEPENDENCY_ISSUE),
    FAIL_PUSH_FILE(520_102, FailureStatus.DEPENDENCY_ISSUE),
    FAIL_PULL_FILE(520_103, FailureStatus.DEPENDENCY_ISSUE),
    DEVICE_FAILED_TO_RESET(520_104, FailureStatus.DEPENDENCY_ISSUE),
    DEVICE_FAILED_TO_REMOUNT(520_105, FailureStatus.DEPENDENCY_ISSUE),
    DEVICE_FAILED_BLUETOOTH_PAIRING(520_106, FailureStatus.DEPENDENCY_ISSUE),
    DEVICE_FAILED_TO_SNAPSHOT(520_107, FailureStatus.DEPENDENCY_ISSUE),
    DEVICE_FAILED_TO_RESTORE_SNAPSHOT(520_108, FailureStatus.DEPENDENCY_ISSUE),
    DEVICE_FAILED_TO_SUSPEND(520_109, FailureStatus.DEPENDENCY_ISSUE),
    DEVICE_FAILED_TO_RESUME(520_110, FailureStatus.DEPENDENCY_ISSUE),
    DEVICE_FAILED_TO_STOP(520_111, FailureStatus.DEPENDENCY_ISSUE),

    INSTRUMENTATION_CRASH(520_200, FailureStatus.SYSTEM_UNDER_TEST_CRASHED),
    ADB_DISCONNECT(520_201, FailureStatus.DEPENDENCY_ISSUE),
    INSTRUMENTATION_LOWMEMORYKILLER(520_202, FailureStatus.SYSTEM_UNDER_TEST_CRASHED),

    FAILED_TO_LAUNCH_GCE(520_500, FailureStatus.LOST_SYSTEM_UNDER_TEST),
    FAILED_TO_CONNECT_TO_GCE(520_501, FailureStatus.LOST_SYSTEM_UNDER_TEST),
    ERROR_AFTER_FLASHING(520_502, FailureStatus.LOST_SYSTEM_UNDER_TEST),
    FAILED_TO_CONNECT_TO_TCP_DEVICE(520_503, FailureStatus.LOST_SYSTEM_UNDER_TEST),

    DEVICE_UNAVAILABLE(520_750, FailureStatus.LOST_SYSTEM_UNDER_TEST),
    DEVICE_UNRESPONSIVE(520_751, FailureStatus.LOST_SYSTEM_UNDER_TEST),
    DEVICE_CRASHED(520_752, FailureStatus.SYSTEM_UNDER_TEST_CRASHED),
    UNEXPECTED_REBOOT(520_753, FailureStatus.SYSTEM_UNDER_TEST_CRASHED),
    // Failures of the device action tool.
    DEVICE_ACTION_EXECUTION_FAILURE(520_754, FailureStatus.DEPENDENCY_ISSUE);

    private final long code;
    private final @Nonnull FailureStatus status;

    DeviceErrorIdentifier(int code, FailureStatus status) {
        this.code = code;
        this.status = (status == null ? FailureStatus.UNSET : status);
    }

    @Override
    public long code() {
        return code;
    }

    @Override
    public @Nonnull FailureStatus status() {
        return status;
    }
}
