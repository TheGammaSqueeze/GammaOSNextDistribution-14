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

/** Error identifier from tests and tests runners. */
public enum TestErrorIdentifier implements ErrorIdentifier {
    MODULE_DID_NOT_EXECUTE(530_001, FailureStatus.NOT_EXECUTED),
    INSTRUMENTATION_NULL_METHOD(530_002, FailureStatus.TEST_FAILURE),
    INSTRUMENTATION_TIMED_OUT(530_003, FailureStatus.TIMED_OUT),
    MODULE_CHANGED_SYSTEM_STATUS(530_004, FailureStatus.TEST_FAILURE),
    TEST_ABORTED(530_005, FailureStatus.TEST_FAILURE),
    OUTPUT_PARSER_ERROR(530_006, FailureStatus.TEST_FAILURE),
    TEST_BINARY_EXIT_CODE_ERROR(530_007, FailureStatus.TEST_FAILURE),
    TEST_BINARY_TIMED_OUT(530_008, FailureStatus.TIMED_OUT),
    MODIFIED_FOLDABLE_STATE(530_009, FailureStatus.TEST_FAILURE),
    UNEXPECTED_MOBLY_CONFIG(530_010, FailureStatus.CUSTOMER_ISSUE),
    UNEXPECTED_MOBLY_BEHAVIOR(530_011, FailureStatus.CUSTOMER_ISSUE),
    HOST_COMMAND_FAILED(530_012, FailureStatus.CUSTOMER_ISSUE),
    TEST_PHASE_TIMED_OUT(530_013, FailureStatus.TIMED_OUT),
    TEST_FILTER_NEEDS_UPDATE(530_014, FailureStatus.SYSTEM_UNDER_TEST_CRASHED),
    TEST_TIMEOUT(530_015, FailureStatus.TIMED_OUT);

    private final long code;
    private final @Nonnull FailureStatus status;

    TestErrorIdentifier(int code, FailureStatus status) {
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
