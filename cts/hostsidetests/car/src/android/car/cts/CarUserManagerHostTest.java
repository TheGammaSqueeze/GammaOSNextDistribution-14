/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Objects;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class CarUserManagerHostTest extends CarHostJUnit4TestCase {
    private static final String TAG = CarUserManagerHostTest.class.getSimpleName();
    private static final String STATUS_SUCCESSFUL = "STATUS_SUCCESSFUL";
    private static final String STATUS_INVALID_REQUEST = "STATUS_INVALID_REQUEST";
    private static final String STATUS_OK_USER_ALREADY_IN_FOREGROUND =
            "STATUS_OK_USER_ALREADY_IN_FOREGROUND";
    private static final String STATUS_UX_RESTRICTION_FAILURE = "STATUS_UX_RESTRICTION_FAILURE";
    private static final long TEST_WAIT_MS = 50;
    private static final long TEST_TIMEOUT_MS = 10_000;

    @Test
    public void testSwitchUserExists() throws Exception {
        int newUserid = createFullUser("CarUserManagerHostTest_User");

        switchUser(newUserid, STATUS_SUCCESSFUL);
    }

    @Test
    public void testSwitchUserDoesNotExist() throws Exception {
        switchUser(getNonExistentUser(), STATUS_INVALID_REQUEST);
    }

    @Test
    public void testSwitchUserAlreadyForeGroundUser() throws Exception {
        switchUser(getCurrentUserId(), STATUS_OK_USER_ALREADY_IN_FOREGROUND);
    }

    @Test
    public void testRemoveUser() throws Exception {
        int newUserid = createFullUser("CarUserManagerHostTest_User");

        String result = executeCommand("cmd car_service remove-user %d", newUserid);

        assertWithMessage("removeUser(%s)", newUserid).that(result).contains(STATUS_SUCCESSFUL);
    }

    @Test
    public void testSwitchUserIgnoringUxRestriction() throws Exception {
        int newUserid = createFullUser("CarUserManagerHostTest_User");

        switchUser(newUserid, STATUS_SUCCESSFUL, "--ignore-uxr");
    }

    /**
     * Switches the current user and checks that the expected result is emitted.
     */
    private void switchUser(int userId, String expected) throws Exception {
        switchUser(userId, expected, "");
    }

    /**
     * Switches the current user and checks that the expected result is emitted.
     */
    private void switchUser(int userId, String expected, String options) throws Exception {
        waitForCarServiceReady();
        String output = executeCommand("cmd car_service switch-user %d %s", userId, options);

        assertWithMessage("switchUser(%s) ", userId).that(output).contains(expected);

        if (Objects.equals(expected, STATUS_SUCCESSFUL)) {
            waitUntilCurrentUser(userId);
        }
    }

    /**
     * Returns the userId of a non-existent user.
     */
    private int getNonExistentUser() throws Exception {
        return onAllUsers(ArrayList::new).stream()
                .mapToInt((userInfo) -> userInfo.id)
                .max()
                .orElse(0) + 1;
    }
}
