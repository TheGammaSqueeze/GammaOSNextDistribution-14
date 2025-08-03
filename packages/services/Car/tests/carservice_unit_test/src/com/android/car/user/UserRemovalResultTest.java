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

package com.android.car.user;

import static com.google.common.truth.Truth.assertWithMessage;

import android.car.user.UserRemovalResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public final class UserRemovalResultTest {

    private final int mStatus;
    private final boolean mExpectedIsSuccess;

    public UserRemovalResultTest(int status, boolean expectedIsSuccess) {
        mStatus = status;
        mExpectedIsSuccess = expectedIsSuccess;
    }

    @Parameterized.Parameters
    public static Collection<?> statusToIsSuccessMapping() {
        return Arrays.asList(new Object[][]{
            { UserRemovalResult.STATUS_SUCCESSFUL, true},
            { UserRemovalResult.STATUS_SUCCESSFUL_SET_EPHEMERAL, true},
            { UserRemovalResult.STATUS_SUCCESSFUL_LAST_ADMIN_REMOVED, true},
            { UserRemovalResult.STATUS_SUCCESSFUL_LAST_ADMIN_SET_EPHEMERAL, true},
            { UserRemovalResult.STATUS_ANDROID_FAILURE, false},
            { UserRemovalResult.STATUS_INVALID_REQUEST, false},
            { UserRemovalResult.STATUS_USER_DOES_NOT_EXIST, false}
        });
    }

    @Test
    public void testIsSuccess() {
        UserRemovalResult result = new UserRemovalResult(mStatus);

        assertWithMessage("result(%s).isSuccess()", UserRemovalResult.statusToString(mStatus))
                .that(result.isSuccess()).isEqualTo(mExpectedIsSuccess);
    }
}
