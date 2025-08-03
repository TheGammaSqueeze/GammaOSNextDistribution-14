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

package android.car.cts.builtin.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.car.builtin.util.ValidationHelper;
import android.os.Process;
import android.os.UserHandle;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ValidationHelperTest {
    // constants from android.os.UserHandle
    private static final int USER_NULL = -10000;
    private static final int USER_CURRENT_OR_SELF = -3;
    private static final int USER_CURRENT = -2;
    private static final int USER_ALL = -1;
    private static final int USER_SYSTEM = 0;
    private static final int PER_USER_RANGE = 100000;

    @Test
    public void testUserIdValidation() {
        // setup
        int maxUserId = Integer.MAX_VALUE / PER_USER_RANGE;
        int invalidUserId = maxUserId + 1;

        // assert pre-defined user ids
        assertTrue(ValidationHelper.isUserIdValid(USER_NULL));
        assertTrue(ValidationHelper.isUserIdValid(USER_CURRENT_OR_SELF));
        assertTrue(ValidationHelper.isUserIdValid(USER_CURRENT));
        assertTrue(ValidationHelper.isUserIdValid(USER_ALL));
        assertTrue(ValidationHelper.isUserIdValid(USER_SYSTEM));
        assertTrue(ValidationHelper.isUserIdValid(maxUserId));

        // assert dynamical user ids
        assertTrue(ValidationHelper.isUserIdValid(UserHandle.myUserId()));

        // assert boundary conditions
        assertFalse(ValidationHelper.isUserIdValid(invalidUserId));
    }

    @Test
    public void testAppIdValidation() {
        // setup
        int outOfRangeAppId = PER_USER_RANGE + 1;

        // assert pre-defined app ids
        assertTrue(ValidationHelper.isAppIdValid(Process.FIRST_APPLICATION_UID));
        assertTrue(ValidationHelper.isAppIdValid(Process.LAST_APPLICATION_UID));
        assertTrue(ValidationHelper.isAppIdValid(Process.ROOT_UID));
        assertTrue(ValidationHelper.isAppIdValid(Process.SYSTEM_UID));
        assertTrue(ValidationHelper.isAppIdValid(Process.SHELL_UID));

        // assert dynamical app ids
        assertTrue(ValidationHelper.isAppIdValid(Process.myUid() % PER_USER_RANGE));
        assertTrue(ValidationHelper.isAppIdValid(Process.FIRST_APPLICATION_UID + 1));
        assertTrue(ValidationHelper.isAppIdValid(Process.LAST_APPLICATION_UID - 1));

        // assert boundary conditions
        assertFalse(ValidationHelper.isAppIdValid(Process.INVALID_UID));
        assertFalse(ValidationHelper.isAppIdValid(outOfRangeAppId));
    }
}
