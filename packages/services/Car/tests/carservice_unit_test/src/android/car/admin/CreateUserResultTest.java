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

import static android.car.test.util.UserTestingHelper.UserInfoBuilder;

import static com.google.common.truth.Truth.assertThat;

import android.car.user.UserCreationResult;
import android.content.pm.UserInfo;
import android.os.UserHandle;

import org.junit.Test;

public final class CreateUserResultTest {

    @Test
    public void testNullConstructor() {
        CreateUserResult result = new CreateUserResult(null);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(RemoveUserResult.STATUS_FAILURE_GENERIC);
        assertThat(result.getUserHandle()).isNull();
    }

    @Test
    public void testSuccess() {
        UserInfo user = new UserInfoBuilder(100).build();

        CreateUserResult result = new CreateUserResult(
                new UserCreationResult(UserCreationResult.STATUS_SUCCESSFUL, user.getUserHandle()));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo(RemoveUserResult.STATUS_SUCCESS);
        assertThat(result.getUserHandle().getIdentifier()).isEqualTo(100);
    }

    @Test
    public void testFailure_nullUserInfo() {
        CreateUserResult result = new CreateUserResult(
                new UserCreationResult(UserCreationResult.STATUS_SUCCESSFUL));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(RemoveUserResult.STATUS_FAILURE_GENERIC);
        assertThat(result.getUserHandle()).isNull();
    }

    @Test
    public void testFailure_nullUserHandle() {
        @SuppressWarnings("deprecation")
        UserInfo user = new UserInfo() {
            @Override
            public UserHandle getUserHandle() {
                return null;
            }
        };
        CreateUserResult result = new CreateUserResult(
                new UserCreationResult(UserCreationResult.STATUS_SUCCESSFUL, user.getUserHandle()));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(RemoveUserResult.STATUS_FAILURE_GENERIC);
        assertThat(result.getUserHandle()).isNull();
    }

    @Test
    public void testFailure() {
        failureTest(UserCreationResult.STATUS_SUCCESSFUL, CreateUserResult.STATUS_FAILURE_GENERIC);
        failureTest(UserCreationResult.STATUS_ANDROID_FAILURE,
                CreateUserResult.STATUS_FAILURE_GENERIC);
        failureTest(UserCreationResult.STATUS_HAL_INTERNAL_FAILURE,
                CreateUserResult.STATUS_FAILURE_GENERIC);
        failureTest(UserCreationResult.STATUS_INVALID_REQUEST,
                CreateUserResult.STATUS_FAILURE_INVALID_ARGUMENTS);
    }

    private void failureTest(int userCreationStatus, int removeUserStatus) {
        CreateUserResult result = new CreateUserResult(new UserCreationResult(userCreationStatus));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(removeUserStatus);
        assertThat(result.getUserHandle()).isNull();
    }
}
