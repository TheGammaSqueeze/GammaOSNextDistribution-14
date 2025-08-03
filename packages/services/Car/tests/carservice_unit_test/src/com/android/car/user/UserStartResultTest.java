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

import android.car.user.UserStartResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public final class UserStartResultTest {

    private final int mStatus;
    private final boolean mExpectedIsSuccess;

    public UserStartResultTest(int status, boolean expectedIsSuccess) {
        mStatus = status;
        mExpectedIsSuccess = expectedIsSuccess;
    }

    @Parameterized.Parameters
    public static Collection<?> statusToIsSuccessMapping() {
        return Arrays.asList(new Object[][]{
            { UserStartResult.STATUS_SUCCESSFUL, true },
            { UserStartResult.STATUS_SUCCESSFUL_USER_IS_CURRENT_USER, true},
            { UserStartResult.STATUS_ANDROID_FAILURE, false},
            { UserStartResult.STATUS_USER_DOES_NOT_EXIST, false}
        });
    }

    @Test
    public void testIsSuccess() {
        UserStartResult result = new UserStartResult(mStatus);

        assertWithMessage("result(%s).isSuccess()", UserStartResult.statusToString(mStatus))
                .that(result.isSuccess()).isEqualTo(mExpectedIsSuccess);
    }
}
