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

import static com.google.common.truth.Truth.assertThat;

import android.car.user.UserRemovalResult;

import org.junit.Test;

public final class RemoveUserResultTest {

    @Test
    public void testSuccess() {
        successTest(UserRemovalResult.STATUS_SUCCESSFUL,
                RemoveUserResult.STATUS_SUCCESS);
        successTest(UserRemovalResult.STATUS_SUCCESSFUL_LAST_ADMIN_REMOVED,
                RemoveUserResult.STATUS_SUCCESS_LAST_ADMIN_REMOVED);
        successTest(UserRemovalResult.STATUS_SUCCESSFUL_SET_EPHEMERAL,
                RemoveUserResult.STATUS_SUCCESS_SET_EPHEMERAL);
        successTest(UserRemovalResult.STATUS_SUCCESSFUL_LAST_ADMIN_SET_EPHEMERAL,
                RemoveUserResult.STATUS_SUCCESS_LAST_ADMIN_SET_EPHEMERAL);
    }

    private void successTest(int userRemovalStatus, int removeUserStatus) {
        RemoveUserResult result = new RemoveUserResult(userRemovalStatus);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo(removeUserStatus);
    }

    @Test
    public void testFailure() {
        failureTest(UserRemovalResult.STATUS_USER_DOES_NOT_EXIST,
                RemoveUserResult.STATUS_FAILURE_USER_DOES_NOT_EXIST);
        failureTest(UserRemovalResult.STATUS_INVALID_REQUEST,
                RemoveUserResult.STATUS_FAILURE_INVALID_ARGUMENTS);
        failureTest(UserRemovalResult.STATUS_ANDROID_FAILURE,
                RemoveUserResult.STATUS_FAILURE_GENERIC);
    }

    private void failureTest(int userRemovalStatus, int removeUserStatus) {
        RemoveUserResult result = new RemoveUserResult(userRemovalStatus);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(removeUserStatus);
    }
}
