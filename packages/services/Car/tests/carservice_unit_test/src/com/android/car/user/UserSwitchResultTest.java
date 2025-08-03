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

import android.car.user.UserSwitchResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public final class UserSwitchResultTest {

    private final int mStatus;
    private final boolean mExpectedIsSuccess;

    public UserSwitchResultTest(int status, boolean expectedIsSuccess) {
        mStatus = status;
        mExpectedIsSuccess = expectedIsSuccess;
    }

    @Parameterized.Parameters
    public static Collection<?> statusToIsSuccessMapping() {
        return Arrays.asList(new Object[][]{
            { UserSwitchResult.STATUS_SUCCESSFUL, true },
            { UserSwitchResult.STATUS_OK_USER_ALREADY_IN_FOREGROUND, true },
            { UserSwitchResult.STATUS_ANDROID_FAILURE, false},
            { UserSwitchResult.STATUS_HAL_FAILURE, false},
            { UserSwitchResult.STATUS_HAL_INTERNAL_FAILURE, false},
            { UserSwitchResult.STATUS_INVALID_REQUEST, false},
            { UserSwitchResult.STATUS_UX_RESTRICTION_FAILURE, false},
            { UserSwitchResult.STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO, false},
            { UserSwitchResult.STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST, false},
            { UserSwitchResult.STATUS_NOT_SWITCHABLE, false},
            { UserSwitchResult.STATUS_NOT_LOGGED_IN, false}
        });
    }

    @Test
    public void testIsSuccess() {
        UserSwitchResult result = new UserSwitchResult(mStatus, /* errorMessage= */ null);

        assertWithMessage("result(%s).isSuccess()", UserSwitchResult.statusToString(mStatus))
                .that(result.isSuccess()).isEqualTo(mExpectedIsSuccess);
    }
}
