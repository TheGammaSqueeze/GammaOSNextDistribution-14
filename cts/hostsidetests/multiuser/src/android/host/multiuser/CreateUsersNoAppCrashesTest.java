/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.platform.test.annotations.Presubmit;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

/**
 * Test verifies that users can be created/switched to without error dialogs shown to the user
 *
 * Run: atest CreateUsersNoAppCrashesTest
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class CreateUsersNoAppCrashesTest extends BaseMultiUserTest {

    @Rule
    public final RuleChain mLookAllThoseRules = RuleChain.outerRule(new SupportsMultiUserRule(this))
            .around(new AppCrashRetryRule());

    @Presubmit
    @Test
    public void testCanCreateGuestUser() throws Exception {
        // The device may already have a guest present if it is configured with
        // config_guestUserAutoCreated. If so, skip the test.
        assumeGuestDoesNotExist();

        int userId = createGuestUser();
        assertSwitchToUser(userId);
        assertSwitchToUser(mInitialUserId);
    }

    @Presubmit
    @Test
    public void testCanCreateSecondaryUser() throws Exception {
        int userId = getDevice().createUser(
                "TestUser_" + System.currentTimeMillis() /* name */,
                false /* guest */,
                false /* ephemeral */);
        assertSwitchToUser(userId);
        assertSwitchToUser(mInitialUserId);
    }
}
