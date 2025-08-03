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
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.car.user.UserCreationResult;
import android.os.UserHandle;
import android.util.Log;

import com.android.car.internal.util.DebugUtils;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Result of a {@link CarDevicePolicyManager#createUser(String, int)} operation.
 *
 * @hide
 */
@SystemApi
public final class CreateUserResult {

    private static final String TAG = CreateUserResult.class.getSimpleName();

    /**
     * User was created.
     */
    public static final int STATUS_SUCCESS = 1;

    /**
     * User was not created because arguments passed to the method were invalid.
     */
    public static final int STATUS_FAILURE_INVALID_ARGUMENTS = 2;

    /**
     * User was not created for some other reason not described above.
     */
    public static final int STATUS_FAILURE_GENERIC = 100;

    /** @hide */
    @IntDef(prefix = "STATUS_", value = {
            STATUS_SUCCESS,
            STATUS_FAILURE_INVALID_ARGUMENTS,
            STATUS_FAILURE_GENERIC
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {
    }

    private final @Status int mStatus;
    private final @Nullable UserHandle mUserHandle;

    /**
     * Must mark as public even though unit test is on the same package, as actual classes are
     * provided by different jar files.
     *
     * @hide
     */
    @VisibleForTesting
    public CreateUserResult(@Nullable UserCreationResult result) {
        if (result == null) {
            mStatus = STATUS_FAILURE_GENERIC;
            mUserHandle = null;
            return;
        }
        int status = result.getStatus();
        if (status == UserCreationResult.STATUS_SUCCESSFUL) {
            mUserHandle = result.getUser();
            if (mUserHandle == null) {
                Log.w(TAG, "Successful UserCreationResult with no user: " + result);
                mStatus = STATUS_FAILURE_GENERIC;
            } else {
                mStatus = STATUS_SUCCESS;
            }
            return;
        }

        mUserHandle = null;
        switch (status) {
            case UserCreationResult.STATUS_INVALID_REQUEST:
                mStatus = STATUS_FAILURE_INVALID_ARGUMENTS;
                break;
            default:
                mStatus = STATUS_FAILURE_GENERIC;
        }
    }

    /**
     * Return {@code CreateUserResult} with generic error.
     *
     * @hide
     */
    public static CreateUserResult forGenericError() {
        return new CreateUserResult(null);
    }

    /**
     * Gets the specific result of the operation.
     *
     * @return either {@link CreateUserResult#STATUS_SUCCESS} or
     *         {@link CreateUserResult#STATUS_FAILURE_GENERIC}.
     */
    public @Status int getStatus() {
        return mStatus;
    }

    /**
     * Gets whether the operation was successful or not.
     */
    public boolean isSuccess() {
        return mStatus == STATUS_SUCCESS;
    }

    /**
     * Gets the {@link UserHandle} of the created user (or {@code null} in case of failure).
     */
    @Nullable
    public UserHandle getUserHandle() {
        return mUserHandle;
    }

    @Override
    public String toString() {
        StringBuilder string = new StringBuilder("CreateUserResult[")
                .append(statusToString(mStatus));
        if (mUserHandle != null) {
            string.append(":").append(mUserHandle.getIdentifier());
        }

        return string.append(']').toString();
    }

    private static String statusToString(int status) {
        return DebugUtils.valueToString(CreateUserResult.class, "STATUS_", status);
    }
}
