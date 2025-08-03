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

/** Error Identifiers from Trade Federation infra, and dependent infra (like Build infra). */
public enum InfraErrorIdentifier implements ErrorIdentifier {

    // ********************************************************************************************
    // Infra: 500_001 ~ 510_000
    // ********************************************************************************************
    // 500_001 - 500_500: General errors
    ARTIFACT_NOT_FOUND(500_001, FailureStatus.DEPENDENCY_ISSUE),
    FAIL_TO_CREATE_FILE(500_002, FailureStatus.INFRA_FAILURE),
    INVOCATION_CANCELLED(500_003, FailureStatus.CANCELLED),
    CODE_COVERAGE_ERROR(500_004, FailureStatus.INFRA_FAILURE),
    MODULE_SETUP_RUNTIME_EXCEPTION(500_005, FailureStatus.CUSTOMER_ISSUE),
    CONFIGURED_ARTIFACT_NOT_FOUND(500_006, FailureStatus.CUSTOMER_ISSUE),
    INVOCATION_TIMEOUT(500_007, FailureStatus.TIMED_OUT),
    OPTION_CONFIGURATION_ERROR(500_008, FailureStatus.CUSTOMER_ISSUE),
    RUNNER_ALLOCATION_ERROR(500_009, FailureStatus.INFRA_FAILURE),
    SCHEDULER_ALLOCATION_ERROR(500_010, FailureStatus.CUSTOMER_ISSUE),
    HOST_BINARY_FAILURE(500_011, FailureStatus.DEPENDENCY_ISSUE),
    MISMATCHED_BUILD_DEVICE(500_012, FailureStatus.CUSTOMER_ISSUE),
    LAB_HOST_FILESYSTEM_ERROR(500_013, FailureStatus.INFRA_FAILURE),
    TRADEFED_SHUTTING_DOWN(500_014, FailureStatus.INFRA_FAILURE),
    LAB_HOST_FILESYSTEM_FULL(500_015, FailureStatus.INFRA_FAILURE),
    TRADEFED_SKIPPED_TESTS_DURING_SHUTDOWN(500_016, FailureStatus.CANCELLED),
    SCHEDULING_ERROR(500_017, FailureStatus.INFRA_FAILURE),
    EVENT_PROCESSING_TIMEOUT(500_018, FailureStatus.INFRA_FAILURE),
    OUT_OF_MEMORY_ERROR(500_019, FailureStatus.INFRA_FAILURE),
    // Use a catch-all error during bring up of the new feature
    INCREMENTAL_FLASHING_ERROR(500_020, FailureStatus.INFRA_FAILURE),
    BLOCK_COMPARE_ERROR(500_021, FailureStatus.INFRA_FAILURE),
    FLASHSTATION_CACHE_PREPARATION_ERROR(500_022, FailureStatus.INFRA_FAILURE),

    // 500_400 - 500_500: General errors - subprocess related
    INTERRUPTED_DURING_SUBPROCESS_SHUTDOWN(500_401, FailureStatus.INFRA_FAILURE),

    // 500_501 - 501_000: Build, Artifacts download related errors
    ARTIFACT_REMOTE_PATH_NULL(500_501, FailureStatus.INFRA_FAILURE),
    ARTIFACT_UNSUPPORTED_PATH(500_502, FailureStatus.INFRA_FAILURE),
    ARTIFACT_DOWNLOAD_ERROR(500_503, FailureStatus.DEPENDENCY_ISSUE),
    GCS_ERROR(500_504, FailureStatus.DEPENDENCY_ISSUE),
    ANDROID_PARTNER_SERVER_ERROR(500_505, FailureStatus.DEPENDENCY_ISSUE),
    ARTIFACT_INVALID(500_506, FailureStatus.DEPENDENCY_ISSUE),
    SANDBOX_SETUP_ERROR(500_507, FailureStatus.INFRA_FAILURE),

    // 501_001 - 501_500: environment issues: For example: lab wifi
    WIFI_FAILED_CONNECT(501_001, FailureStatus.DEPENDENCY_ISSUE),
    GOOGLE_ACCOUNT_SETUP_FAILED(501_002, FailureStatus.DEPENDENCY_ISSUE),
    NO_WIFI(501_003, FailureStatus.DEPENDENCY_ISSUE),
    NO_DISK_SPACE(501_004, FailureStatus.DEPENDENCY_ISSUE),
    VIRTUAL_WIFI_FAILED_CONNECT(501_005, FailureStatus.DEPENDENCY_ISSUE),

    // 502_000 - 502_100: Test issues detected by infra
    EXPECTED_TESTS_MISMATCH(502_000, FailureStatus.TEST_FAILURE),

    // 505_000 - 505_250: Acloud errors
    // The error codes should be aligned with errors defined in
    // tools/acloud/internal/constants.py
    NO_ACLOUD_REPORT(505_000, FailureStatus.DEPENDENCY_ISSUE),
    ACLOUD_UNDETERMINED(505_001, FailureStatus.DEPENDENCY_ISSUE),
    ACLOUD_TIMED_OUT(505_002, FailureStatus.DEPENDENCY_ISSUE),
    ACLOUD_UNRECOGNIZED_ERROR_TYPE(505_003, FailureStatus.DEPENDENCY_ISSUE),
    ACLOUD_INIT_ERROR(505_004, FailureStatus.DEPENDENCY_ISSUE),
    ACLOUD_CREATE_GCE_ERROR(505_005, FailureStatus.DEPENDENCY_ISSUE),
    ACLOUD_DOWNLOAD_ARTIFACT_ERROR(505_006, FailureStatus.DEPENDENCY_ISSUE),
    ACLOUD_BOOT_UP_ERROR(505_007, FailureStatus.LOST_SYSTEM_UNDER_TEST),
    GCE_QUOTA_ERROR(505_008, FailureStatus.DEPENDENCY_ISSUE),
    ACLOUD_SSH_CONNECT_ERROR(505_009, FailureStatus.DEPENDENCY_ISSUE),
    ACLOUD_OXYGEN_LEASE_ERROR(505_010, FailureStatus.DEPENDENCY_ISSUE),
    ACLOUD_OXYGEN_RELEASE_ERROR(505_011, FailureStatus.DEPENDENCY_ISSUE),
    OXYGEN_DEVICE_LAUNCHER_FAILURE(505_012, FailureStatus.LOST_SYSTEM_UNDER_TEST),
    OXYGEN_SERVER_SHUTTING_DOWN(505_013, FailureStatus.DEPENDENCY_ISSUE),
    OXYGEN_BAD_GATEWAY_ERROR(505_014, FailureStatus.DEPENDENCY_ISSUE),
    OXYGEN_REQUEST_TIMEOUT(505_015, FailureStatus.DEPENDENCY_ISSUE),
    OXYGEN_RESOURCE_EXHAUSTED(505_016, FailureStatus.DEPENDENCY_ISSUE),
    OXYGEN_SERVER_CONNECTION_FAILURE(505_017, FailureStatus.DEPENDENCY_ISSUE),
    OXYGEN_CLIENT_BINARY_TIMEOUT(505_018, FailureStatus.INFRA_FAILURE),
    OXYGEN_CLIENT_BINARY_ERROR(505_019, FailureStatus.INFRA_FAILURE),
    OXYGEN_CLIENT_LEASE_ERROR(505_020, FailureStatus.INFRA_FAILURE),
    OXYGEN_NOT_ENOUGH_RESOURCE(505_021, FailureStatus.INFRA_FAILURE),
    OXYGEN_DEVICE_LAUNCHER_TIMEOUT(505_022, FailureStatus.INFRA_FAILURE),
    OXYGEN_SERVER_LB_CONNECTION_ERROR(505_023, FailureStatus.INFRA_FAILURE),
    ACLOUD_INVALID_SERVICE_ACCOUNT_KEY(505_024, FailureStatus.DEPENDENCY_ISSUE),
    ACLOUD_QUOTA_EXCEED_GPU(505_025, FailureStatus.DEPENDENCY_ISSUE),

    // 505_251 - 505_300: Configuration errors
    INTERNAL_CONFIG_ERROR(505_251, FailureStatus.INFRA_FAILURE),
    CLASS_NOT_FOUND(505_252, FailureStatus.CUSTOMER_ISSUE),
    CONFIGURATION_NOT_FOUND(505_253, FailureStatus.CUSTOMER_ISSUE),
    UNEXPECTED_DEVICE_CONFIGURED(505_254, FailureStatus.CUSTOMER_ISSUE),
    KEYSTORE_CONFIG_ERROR(505_255, FailureStatus.DEPENDENCY_ISSUE),
    TEST_MAPPING_PATH_COLLISION(505_256, FailureStatus.DEPENDENCY_ISSUE),
    TEST_MAPPING_FILE_FORMAT_ISSUE(505_257, FailureStatus.CUSTOMER_ISSUE),
    TEST_MAPPING_FILE_NOT_EXIST(505_258, FailureStatus.CUSTOMER_ISSUE),

    UNDETERMINED(510_000, FailureStatus.UNSET);

    private final long code;
    private final @Nonnull FailureStatus status;

    InfraErrorIdentifier(int code, FailureStatus status) {
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
