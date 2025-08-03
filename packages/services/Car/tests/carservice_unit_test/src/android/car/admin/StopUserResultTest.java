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

import static com.google.common.truth.Truth.assertThat;

import android.car.user.UserStopResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public final class StopUserResultTest {

    private final int mUserStopStatus;
    private final int mStopUserStatus;
    private final boolean mIsSuccess;

    public StopUserResultTest(int userStopStatus, int stopUserStatus, boolean isSuccess) {
        mUserStopStatus = userStopStatus;
        mStopUserStatus = stopUserStatus;
        mIsSuccess = isSuccess;
    }

    @Parameterized.Parameters
    public static Collection provideParams() {
        return Arrays.asList(
                new Object[][] {
                        {UserStopResult.STATUS_SUCCESSFUL, StopUserResult.STATUS_SUCCESS, true},
                        {UserStopResult.STATUS_FAILURE_CURRENT_USER,
                                StopUserResult.STATUS_FAILURE_CURRENT_USER, false},
                        {UserStopResult.STATUS_FAILURE_SYSTEM_USER,
                                StopUserResult.STATUS_FAILURE_SYSTEM_USER, false},
                        {UserStopResult.STATUS_USER_DOES_NOT_EXIST,
                                StopUserResult.STATUS_FAILURE_USER_DOES_NOT_EXIST, false},
                        {UserStopResult.STATUS_ANDROID_FAILURE,
                                StopUserResult.STATUS_FAILURE_GENERIC, false}
                });
    }

    @Test
    public void testStatus() {
        StopUserResult result = new StopUserResult(mUserStopStatus);

        assertThat(result.getStatus()).isEqualTo(mStopUserStatus);
        assertThat(result.isSuccess()).isEqualTo(mIsSuccess);
    }
}
