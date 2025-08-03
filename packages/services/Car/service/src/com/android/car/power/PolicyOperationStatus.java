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

package com.android.car.power;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;

final class PolicyOperationStatus {
    static final int OK = 0;
    static final int ERROR_DEFINE_POWER_POLICY = 1;
    static final int ERROR_APPLY_POWER_POLICY = 2;
    static final int ERROR_SET_POWER_POLICY_GROUP = 3;
    static final int ERROR_INVALID_POWER_POLICY_ID = 4;
    static final int ERROR_DOUBLE_REGISTERED_POWER_POLICY_ID = 5;
    static final int ERROR_NOT_REGISTERED_POWER_POLICY_ID = 6;
    static final int ERROR_INVALID_POWER_POLICY_GROUP_ID = 7;
    static final int ERROR_DOUBLE_REGISTERED_POWER_POLICY_GROUP_ID = 8;
    static final int ERROR_INVALID_POWER_COMPONENT = 9;
    static final int ERROR_DUPLICATED_POWER_COMPONENT = 10;

    @IntDef(value = {
        OK,
        ERROR_DEFINE_POWER_POLICY,
        ERROR_APPLY_POWER_POLICY,
        ERROR_SET_POWER_POLICY_GROUP,
        ERROR_INVALID_POWER_POLICY_ID,
        ERROR_DOUBLE_REGISTERED_POWER_POLICY_ID,
        ERROR_NOT_REGISTERED_POWER_POLICY_ID,
        ERROR_INVALID_POWER_POLICY_GROUP_ID,
        ERROR_DOUBLE_REGISTERED_POWER_POLICY_GROUP_ID,
        ERROR_INVALID_POWER_COMPONENT,
        ERROR_DUPLICATED_POWER_COMPONENT
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface ErrorCode{}

    private static final ArrayList<String> ERROR_CODE_DESCRIPTION = new ArrayList<>(Arrays.asList(
            "Power policy operation is successful", // OK
            "Failed to define power policy",        // ERROR_DEFINE_POWER_POLICY
            "Failed to apply power policy",         // ERROR_APPLY_POWER_POLICY
            "Failed to set power policy group",     // ERROR_SET_POWER_POLICY_GROUP
            "Invalid power policy ID",              // ERROR_INVALID_POWER_POLICY_ID
            "Already registered power policy ID",   // ERROR_DOUBLE_REGISTERED_POWER_POLICY_ID
            "Not registered power policy ID",       // ERROR_NOT_REGISTERED_POWER_POLICY_ID
            "Invalid power policy group ID",        // ERROR_INVALID_POWER_POLICY_GROUP_ID
            "Already registered policy group ID",   // ERROR_DOUBLE_REGISTERED_POWER_POLICY_GROUP_ID
            "Invalid power component",              // ERROR_INVALID_POWER_COMPONENT
            "Duplicated power component"            // ERROR_DUPLICATED_POWER_COMPONENT
    ));

    private PolicyOperationStatus() {}

    public static String errorCodeToString(@ErrorCode int code) {
        try {
            return ERROR_CODE_DESCRIPTION.get(code);
        } catch (IndexOutOfBoundsException e) {
            return "Unknown error code: " + code;
        }
    }

    public static String errorCodeToString(@ErrorCode int code, String moreDescription) {
        try {
            return ERROR_CODE_DESCRIPTION.get(code) + ": " + moreDescription;
        } catch (IndexOutOfBoundsException e) {
            return "Unknown error code: " + code;
        }
    }
}
