/*
 * Copyright (C) 2021 The Android Open Source Project
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

/** Helper class for manipulating the errors to align with the common storage. */
public class ErrorStorageUtil {

    /**
     * Map Tradefed errors to our infrastructure common storage errors. They map 1:1 but have
     * slightly different names so we re-align them here.
     */
    public static String mapStatus(FailureStatus status) {
        if (status == null) {
            return "ERROR_TYPE_UNSPECIFIED";
        }
        switch (status) {
            case TEST_FAILURE:
                return "TEST_FAILURE";
            case TIMED_OUT:
                return "TIMEOUT";
            case CANCELLED:
                return "TEST_CANCELLED";
            case INFRA_FAILURE:
                return "INFRA_ERROR";
            case SYSTEM_UNDER_TEST_CRASHED:
                return "SYSTEM_UNDER_TEST_CRASHED";
            case NOT_EXECUTED:
                return "NOT_EXECUTED";
            case LOST_SYSTEM_UNDER_TEST:
                return "LOST_SYSTEM_UNDER_TEST";
            case DEPENDENCY_ISSUE:
                return "DEPENDENCY_ISSUE";
            case CUSTOMER_ISSUE:
                return "CUSTOMER_ISSUE";
            default:
                return "ERROR_TYPE_UNSPECIFIED";
        }
    }
}
