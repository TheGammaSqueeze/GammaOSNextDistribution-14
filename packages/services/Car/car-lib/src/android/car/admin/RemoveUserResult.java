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

package android.car.admin;

import android.annotation.IntDef;
import android.annotation.SystemApi;
import android.car.user.UserRemovalResult;

import com.android.car.internal.util.DebugUtils;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Result of a {@link CarDevicePolicyManager#removeUser(android.os.UserHandle)} operation.
 *
 * @hide
 */
@SystemApi
public final class RemoveUserResult {

    /**
     * User was removed.
     */
    public static final int STATUS_SUCCESS = 1;

    /**
     * User was removed, and it was the last admin user.
     */
    public static final int STATUS_SUCCESS_LAST_ADMIN_REMOVED = 2;

    /**
     * When the user is set as ephemeral so that it is scheduled for removal. This occurs when the
     * user can't be immediately removed, such as when the current user is being removed.
     */
    public static final int STATUS_SUCCESS_SET_EPHEMERAL = 3;

    /**
     * User was not removed because it doesn't exist.
     */
    public static final int STATUS_FAILURE_USER_DOES_NOT_EXIST = 4;

    /**
     * User was not removed because arguments passed to the method were invalid.
     */
    public static final int STATUS_FAILURE_INVALID_ARGUMENTS = 5;

    /**
     * When last admin user has been set as ephemeral so that it is scheduled for removal. This
     * occurs when the user can't be immediately removed, such as when the current user is being
     * removed.
     */
    public static final int STATUS_SUCCESS_LAST_ADMIN_SET_EPHEMERAL = 6;

    /**
     * User was not removed for some other reason not described above.
     */
    public static final int STATUS_FAILURE_GENERIC = 100;

    /** @hide */
    @IntDef(prefix = "STATUS_", value = {
            STATUS_SUCCESS,
            STATUS_SUCCESS_LAST_ADMIN_REMOVED,
            STATUS_SUCCESS_SET_EPHEMERAL,
            STATUS_FAILURE_USER_DOES_NOT_EXIST,
            STATUS_FAILURE_INVALID_ARGUMENTS,
            STATUS_FAILURE_GENERIC,
            STATUS_SUCCESS_LAST_ADMIN_SET_EPHEMERAL
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {
    }

    private final @Status int mStatus;

    /**
     * Must mark as public even though unit test is on the same package, as actual classes are
     * provided by different jar files.
     *
     * @hide
     */
    @VisibleForTesting
    public RemoveUserResult(@UserRemovalResult.Status int status) {
        switch (status) {
            case UserRemovalResult.STATUS_SUCCESSFUL:
                mStatus = STATUS_SUCCESS;
                break;
            case UserRemovalResult.STATUS_SUCCESSFUL_LAST_ADMIN_REMOVED:
                mStatus = STATUS_SUCCESS_LAST_ADMIN_REMOVED;
                break;
            case UserRemovalResult.STATUS_SUCCESSFUL_SET_EPHEMERAL:
                mStatus = STATUS_SUCCESS_SET_EPHEMERAL;
                break;
            case UserRemovalResult.STATUS_USER_DOES_NOT_EXIST:
                mStatus = STATUS_FAILURE_USER_DOES_NOT_EXIST;
                break;
            case UserRemovalResult.STATUS_INVALID_REQUEST:
                mStatus = STATUS_FAILURE_INVALID_ARGUMENTS;
                break;
            case UserRemovalResult.STATUS_SUCCESSFUL_LAST_ADMIN_SET_EPHEMERAL:
                mStatus = STATUS_SUCCESS_LAST_ADMIN_SET_EPHEMERAL;
                break;
            default:
                mStatus = STATUS_FAILURE_GENERIC;
        }
    }

    /**
     * Gets the specific result of the operation.
     *
     * @return either {@link RemoveUserResult#STATUS_SUCCESS},
     *         {@link RemoveUserResult#STATUS_SUCCESS_LAST_ADMIN_REMOVED},
     *         {@link RemoveUserResult#STATUS_SUCCESS_SET_EPHEMERAL},
     *         {@link RemoveUserResult#STATUS_FAILURE_USER_DOES_NOT_EXIST},
     *         {@link RemoveUserResult#STATUS_FAILURE_INVALID_ARGUMENTS},
     *         {@link RemoveUserResult#STATUS_FAILURE_GENERIC}, or
     *         {@link RemoveUserResult#STATUS_SUCCESS_LAST_ADMIN_SET_EPHEMERAL}.
     */
    public @Status int getStatus() {
        return mStatus;
    }

    /**
     * Gets whether the operation was successful or not.
     */
    public boolean isSuccess() {
        return mStatus == STATUS_SUCCESS || mStatus == STATUS_SUCCESS_LAST_ADMIN_REMOVED
                || mStatus == STATUS_SUCCESS_SET_EPHEMERAL
                || mStatus == STATUS_SUCCESS_LAST_ADMIN_SET_EPHEMERAL;
    }

    @Override
    public String toString() {
        return "RemoveUserResult[" + statusToString(mStatus) + "]";
    }

    /** @hide */
    public static String statusToString(@Status int status) {
        return DebugUtils.valueToString(RemoveUserResult.class, "STATUS_", status);
    }
}
