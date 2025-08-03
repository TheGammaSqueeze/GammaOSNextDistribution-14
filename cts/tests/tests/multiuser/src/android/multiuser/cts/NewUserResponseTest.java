/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.multiuser.cts;

import static com.google.common.truth.Truth.assertThat;

import android.os.NewUserResponse;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;

public final class NewUserResponseTest {

    @Test
    @ApiTest(apis = {"android.os.NewUserResponse#isSuccessful",
            "android.os.NewUserResponse#getUser",
            "android.os.NewUserResponse#getOperationResult"})
    public void testNewUserResponseSuccessful() {
        UserHandle userHandle = UserHandle.of(100);

        NewUserResponse response =
                new NewUserResponse(userHandle, UserManager.USER_OPERATION_SUCCESS);

        assertThat(response.isSuccessful()).isTrue();
        assertThat(response.getUser()).isEqualTo(userHandle);
        assertThat(response.getOperationResult()).isEqualTo(UserManager.USER_OPERATION_SUCCESS);
        assertThat(response.toString()).isEqualTo(
                "NewUserResponse{mUser=UserHandle{100}, mOperationResult="
                        + UserManager.USER_OPERATION_SUCCESS + '}');
    }

    @Test
    @ApiTest(apis = {"android.os.NewUserResponse#isSuccessful",
            "android.os.NewUserResponse#getUser",
            "android.os.NewUserResponse#getOperationResult"})
    public void testNewUserResponseUnsuccessful() {
        NewUserResponse response = new NewUserResponse(/* user= */ null,
                UserManager.USER_OPERATION_ERROR_UNKNOWN);

        assertThat(response.isSuccessful()).isFalse();
        assertThat(response.getUser()).isNull();
        assertThat(response.getOperationResult())
                .isEqualTo(UserManager.USER_OPERATION_ERROR_UNKNOWN);
        assertThat(response.toString()).isEqualTo("NewUserResponse{mUser=null, mOperationResult="
                + UserManager.USER_OPERATION_ERROR_UNKNOWN + '}');
    }
}
