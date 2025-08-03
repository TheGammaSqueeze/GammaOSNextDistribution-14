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

package com.android.car.user;

import static com.google.common.truth.Truth.assertWithMessage;

import android.car.user.UserCreationResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public final class UserCreationResultIsSuccessTest {

    private final int mStatus;
    private final boolean mExpectedIsSuccess;

    public UserCreationResultIsSuccessTest(int status, boolean expectedIsSuccess) {
        mStatus = status;
        mExpectedIsSuccess = expectedIsSuccess;
    }

    @Parameterized.Parameters
    public static Collection<?> statusToIsSuccessMapping() {
        return Arrays.asList(new Object[][]{
            { UserCreationResult.STATUS_SUCCESSFUL, true},
            { UserCreationResult.STATUS_ANDROID_FAILURE, false},
            { UserCreationResult.STATUS_HAL_FAILURE, false},
            { UserCreationResult.STATUS_HAL_INTERNAL_FAILURE, false},
            { UserCreationResult.STATUS_INVALID_REQUEST, false}
        });
    }

    @Test
    public void testIsSuccess() {
        UserCreationResult result = new UserCreationResult(mStatus);

        assertWithMessage("result(%s).isSuccess()", UserCreationResult.statusToString(mStatus))
                .that(result.isSuccess()).isEqualTo(mExpectedIsSuccess);
    }
}
