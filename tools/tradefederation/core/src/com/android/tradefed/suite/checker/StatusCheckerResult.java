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
package com.android.tradefed.suite.checker;

import java.util.LinkedHashMap;
import java.util.Map;

/** Contains the result of a {@link ISystemStatusChecker} execution. */
public class StatusCheckerResult {

    public enum CheckStatus {
        /** status check was successful */
        SUCCESS,
        /** status check did not succeed. An error message might be available (optional). */
        FAILED,
    }

    public static final String SYSTEM_CHECKER = "system_checker";
    private CheckStatus mCheckStatus = CheckStatus.FAILED;
    private String mErrorMessage = null;
    private boolean mBugreportNeeded = false;
    private Map<String, String> mModuleProperties = new LinkedHashMap<>();

    /** Create a {@link StatusCheckerResult} with the default {@link CheckStatus#FAILED} status. */
    public StatusCheckerResult() {}

    /**
     * Create a {@link StatusCheckerResult} with the given status.
     *
     * @param status the {@link CheckStatus}
     */
    public StatusCheckerResult(CheckStatus status) {
        mCheckStatus = status;
    }

    /** Returns the {@link CheckStatus} of the checker. */
    public CheckStatus getStatus() {
        return mCheckStatus;
    }

    /** Sets the {@link CheckStatus} of the checker. */
    public void setStatus(CheckStatus status) {
        mCheckStatus = status;
    }

    /**
     * Returns the error message associated with a failure. Can be null even in case of failures.
     */
    public String getErrorMessage() {
        return mErrorMessage;
    }

    /** Sets the error message associated with a failure. */
    public void setErrorMessage(String message) {
        mErrorMessage = message;
    }

    /** Returns whether or not a bugreport is needed in case of checker failure. */
    public boolean isBugreportNeeded() {
        return mBugreportNeeded;
    }

    /** Sets whether or not a bugreport is needed in case of checker failure. */
    public void setBugreportNeeded(boolean need) {
        mBugreportNeeded = need;
    }

    /** Adds a module property reported by the checker. */
    public void addModuleProperty(String propertyName, String property) {
        mModuleProperties.put(SYSTEM_CHECKER + '_' + propertyName, property);
    }

    /** Returns the module properties reported by the checker. */
    public Map<String, String> getModuleProperties() {
        return mModuleProperties;
    }
}
