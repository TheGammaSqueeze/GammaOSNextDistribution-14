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

import android.car.user.UserStartResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public final class StartUserInBackgroundResultTest {
    private final int mUserStartStatus;
    private final int mStartUserStatus;
    private final boolean mIsSuccess;

    public StartUserInBackgroundResultTest(
            int userStartStatus, int startUserStatus, boolean isSuccess) {
        mUserStartStatus = userStartStatus;
        mStartUserStatus = startUserStatus;
        mIsSuccess = isSuccess;
    }

    @Parameterized.Parameters
    public static Collection provideParams() {
        return Arrays.asList(
                new Object[][] {
                        {UserStartResult.STATUS_SUCCESSFUL,
                                StartUserInBackgroundResult.STATUS_SUCCESS, true},
                        {UserStartResult.STATUS_SUCCESSFUL_USER_IS_CURRENT_USER,
                                StartUserInBackgroundResult.STATUS_SUCCESS_CURRENT_USER, true},
                        {UserStartResult.STATUS_ANDROID_FAILURE,
                                StartUserInBackgroundResult.STATUS_FAILURE_GENERIC, false},
                        {UserStartResult.STATUS_USER_DOES_NOT_EXIST,
                                StartUserInBackgroundResult.STATUS_FAILURE_USER_DOES_NOT_EXIST,
                                false}
                });
    }

    @Test
    public void testStatus() {
        StartUserInBackgroundResult result = new StartUserInBackgroundResult(mUserStartStatus);

        assertThat(result.getStatus()).isEqualTo(mStartUserStatus);
        assertThat(result.isSuccess()).isEqualTo(mIsSuccess);
    }
}
