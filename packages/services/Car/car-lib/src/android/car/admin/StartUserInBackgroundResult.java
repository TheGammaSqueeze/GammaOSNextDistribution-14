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

package android.car.admin;

import android.annotation.IntDef;
import android.annotation.TestApi;
import android.car.user.UserStartResult;

import com.android.car.internal.util.DebugUtils;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Result of a {@link CarDevicePolicyManager#startUserInBackground operation.
 *
 * @hide
 */
@TestApi
public final class StartUserInBackgroundResult {

    /**
     * User was started.
     */
    public static final int STATUS_SUCCESS = 1;

    /**
     * User was the current user.
     */
    public static final int STATUS_SUCCESS_CURRENT_USER = 2;

    /**
     * User was not started because it does not exist.
     */
    public static final int STATUS_FAILURE_USER_DOES_NOT_EXIST = 3;

    /**
     * User was not started for some other reason not described above.
     */
    public static final int STATUS_FAILURE_GENERIC = 100;

    /** @hide */
    @IntDef(prefix = "STATUS_", value = {
            STATUS_SUCCESS,
            STATUS_SUCCESS_CURRENT_USER,
            STATUS_FAILURE_USER_DOES_NOT_EXIST,
            STATUS_FAILURE_GENERIC
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {
    }

    private final @Status int mStatus;

    /** @hide */
    @VisibleForTesting
    public StartUserInBackgroundResult(@UserStartResult.Status int status) {
        switch(status) {
            case UserStartResult.STATUS_SUCCESSFUL:
                mStatus = STATUS_SUCCESS;
                break;
            case UserStartResult.STATUS_SUCCESSFUL_USER_IS_CURRENT_USER:
                mStatus = STATUS_SUCCESS_CURRENT_USER;
                break;
            case UserStartResult.STATUS_USER_DOES_NOT_EXIST:
                mStatus = STATUS_FAILURE_USER_DOES_NOT_EXIST;
                break;
            default:
                mStatus = STATUS_FAILURE_GENERIC;
        }
    }

    /**
     * Gets the specific result of the operation.
     *
     * @return either {@link StartUserInBackgroundResult#STATUS_SUCCESS},
     *         {@link StartUserInBackgroundResult#STATUS_SUCCESS_CURRENT_USER},
     *         {@link StartUserInBackgroundResult#STATUS_FAILURE_USER_DOES_NOT_EXIST}, or
     *         {@link StartUserInBackgroundResult#STATUS_FAILURE_GENERIC}.
     */
    public @Status int getStatus() {
        return mStatus;
    }

    /**
     * Gets whether the operation was successful or not.
     */
    public boolean isSuccess() {
        return mStatus == STATUS_SUCCESS || mStatus == STATUS_SUCCESS_CURRENT_USER;
    }

    @Override
    public String toString() {
        return "StartUserInBackgroundResult[" + statusToString(mStatus) + "]";
    }

    private static String statusToString(int status) {
        return DebugUtils.valueToString(StartUserInBackgroundResult.class, "STATUS_", status);
    }
}
