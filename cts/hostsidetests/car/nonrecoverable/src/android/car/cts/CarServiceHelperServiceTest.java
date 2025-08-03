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

package android.car.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class CarServiceHelperServiceTest extends CarHostJUnit4TestCase {

    private static final int SYSTEM_USER_ID = 0;
    private static final int RESTART_AND_CREATE_USER_WAIT_TIME_MS = 300_000;
    private static final int RETRY_WAIT_TIME_MS = 1_000;

    /*
     * This test tests multiple calls from CarServiceHelperService -
     * {@code CarServiceHelperInterface.createUserEvenWhenDisallowed}
     * {@code CarServiceHelperServiceUpdatable.onStart} and
     * {@code CarServiceHelperServiceUpdatable.initBootUser}
     */
    @Test
    public void testUserCreatedOnStartUpForHeadlessSystemUser() throws Exception {
        assumeTrue("Skipping test on non-headless system user mode",
                isHeadlessSystemUserMode());

        doNotSwitchToInitialUserAfterTest();

        removeAllUsersExceptSystem(); // it will make the current user ephemeral

        restartSystemServer();

        // Makes sure new user was created and switched to
        waitUntilAtLeastNPersistentUsersAreAvailable(SYSTEM_RESTART_TIMEOUT_SEC, 2);

        waitUntilCurrentUserIsNotSystem(SYSTEM_RESTART_TIMEOUT_SEC);

        assertWithMessage("Current user id").that(getCurrentUserId()).isNotEqualTo(SYSTEM_USER_ID);
    }

    private void removeAllUsersExceptSystem() throws Exception {
        List<Integer> users = getAllPersistentUsers();
        for (int i = 0; i < users.size(); i++) {
            int userId = users.get(i);
            if (userId == SYSTEM_USER_ID) {
                continue;
            }
            assertWithMessage("removeUser(%s)", userId).that(removeUser(userId)).isTrue();
        }

        users = getAllPersistentUsers();
        assertWithMessage("Users").that(users).containsExactly(SYSTEM_USER_ID);
    }
}
