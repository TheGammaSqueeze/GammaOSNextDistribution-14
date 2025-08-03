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
package com.android.tradefed.result;

import com.android.tradefed.log.LogUtil.CLog;

/** Representation in Tradefed of possible statuses for test methods. */
public enum TestStatus {
    /** Test failed. */
    FAILURE,
    /** Test passed */
    PASSED,
    /** Test started but not ended */
    INCOMPLETE,
    /** Test assumption failure */
    ASSUMPTION_FAILURE,
    /** Test ignored */
    IGNORED,
    /** Test skipped, did not run for a reason */
    SKIPPED;

    /** Convert Tradefed status to ddmlib one during the transition of classes. */
    public static com.android.ddmlib.testrunner.TestResult.TestStatus convertToDdmlibType(
            TestStatus status) {
        switch (status) {
            case FAILURE:
                return com.android.ddmlib.testrunner.TestResult.TestStatus.FAILURE;
            case PASSED:
                return com.android.ddmlib.testrunner.TestResult.TestStatus.PASSED;
            case INCOMPLETE:
                return com.android.ddmlib.testrunner.TestResult.TestStatus.INCOMPLETE;
            case ASSUMPTION_FAILURE:
                return com.android.ddmlib.testrunner.TestResult.TestStatus.ASSUMPTION_FAILURE;
            case IGNORED:
                return com.android.ddmlib.testrunner.TestResult.TestStatus.IGNORED;
            case SKIPPED:
                CLog.w("Retrofit SKIPPED into PASSED");
                return com.android.ddmlib.testrunner.TestResult.TestStatus.PASSED;
            default:
                return com.android.ddmlib.testrunner.TestResult.TestStatus.PASSED;
        }
    }

    public static TestStatus convertFromDdmlibType(
            com.android.ddmlib.testrunner.TestResult.TestStatus ddmlibStatus) {
        switch (ddmlibStatus) {
            case ASSUMPTION_FAILURE:
                return TestStatus.ASSUMPTION_FAILURE;
            case FAILURE:
                return TestStatus.FAILURE;
            case IGNORED:
                return TestStatus.IGNORED;
            case INCOMPLETE:
                return TestStatus.INCOMPLETE;
            case PASSED:
                return TestStatus.PASSED;
            default:
                return TestStatus.PASSED;
        }
    }
}
