/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */
package android.host.multiuser;

import static com.google.common.truth.Truth.assertWithMessage;

import android.host.multiuser.BaseMultiUserTest.SupportsMultiUserRule;

import com.android.compatibility.common.util.CddTest;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class CreateUsersPermissionTest extends BaseMultiUserTest {

    @Rule
    public final SupportsMultiUserRule mSupportsMultiUserRule = new SupportsMultiUserRule(this);

    @Test
    public void testCanCreateGuestUser() throws Exception {
        // The device may already have a guest present if it is configured with
        // config_guestUserAutoCreated. If so, skip the test.
        assumeGuestDoesNotExist();

        createGuestUser();
    }

    @Test
    public void testCanCreateEphemeralUser() throws Exception {
        getDevice().createUser(
                "TestUser_" + System.currentTimeMillis() /* name */,
                false /* guest */,
                true /* ephemeral */);
    }

    @Test
    public void testCanCreateRestrictedUser() throws Exception {
        createRestrictedProfile(mPrimaryUserId);
    }

    @CddTest(requirement="9.5/A-1-3")
    @Test
    public void testCanCreateGuestUserWhenUserLimitReached() throws Exception {
        assumeIsAutomotive();

        // Remove existing guest user
        int guestUserId = getGuestUser();
        if (guestUserId != -1) {
            getDevice().removeUser(guestUserId);
        }
        // Add new users until user limit reached
        int maxUsers = getDevice().getMaxNumberOfUsersSupported();
        int userCount = getDevice().listUsers().size();
        int numUsersToCreate = maxUsers - userCount;
        for (int i = 0; i < numUsersToCreate; i++) {
            // tearDown will removed non-fixed users
            getDevice().createUser(
                    "TestUser_" + System.currentTimeMillis() /* name */,
                    false /* guest */,
                    false /* ephemeral */);
        }
        createGuestUser();
        userCount = getDevice().listUsers().size();
        assertWithMessage("User count should be greater than max users due to added guest user")
                .that(userCount).isGreaterThan(maxUsers);
    }
}
