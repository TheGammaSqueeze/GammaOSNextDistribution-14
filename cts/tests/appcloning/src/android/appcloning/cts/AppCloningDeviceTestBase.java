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

package android.appcloning.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import com.android.compatibility.common.util.SystemUtil;

public class AppCloningDeviceTestBase {

    /**
     * This method creates and starts a user/profile.
     * @param name of user/profile to be created
     * @param type this is applicable only for profile, this signifies type of profile to be created
     * @param parentUserId this is applicable only for profile, this signifies profile's parent's
     *                     userId
     * @return userId of the user/profile that is created
     */
    protected int createAndStartUser(String name, String type, String parentUserId) {
        assertThat(name).isNotEmpty();
        String command = "pm create-user " + name;

        //if user is profile
        if (type != null && !type.trim().isEmpty()) {
            command = "pm create-user --profileOf " + parentUserId + " --user-type " + type
                    + " " + name;
        }

        // create user
        String output = SystemUtil.runShellCommand(command);
        String userId = output.substring(output.lastIndexOf(' ') + 1)
                .replaceAll("[^0-9]", "");
        assumeTrue(userId != null && !userId.trim().isEmpty());

        SystemUtil.runShellCommandOrThrow("am start-user -w " + userId);
        return Integer.parseInt(userId);
    }

    /**
     * Removes user with mentioned userId. Should not be remove user "0"
     * @param userId user to be deleted
     */
    protected void removeUser(int userId) {
        assertThat(userId).isNotEqualTo(0);
        SystemUtil.runShellCommandOrThrow("pm remove-user " + userId);
    }
}
