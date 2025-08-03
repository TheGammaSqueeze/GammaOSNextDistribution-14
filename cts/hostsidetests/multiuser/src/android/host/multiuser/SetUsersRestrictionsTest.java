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
package android.host.multiuser;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test for user restrictions that DO NOT REQUIRE multi-user support.
 *
 * Run: atest SetUsersRestrictionsTest
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class SetUsersRestrictionsTest extends BaseMultiUserTest {

    /**
     * Tests that set-user-restriction is disabled on user builds.
     */
    @Test
    public void testCantSetUserRestriction() throws Exception {
        assumeNotRoot();

        final String setRestriction = "pm set-user-restriction no_fun ";
        final String output = getDevice().executeShellCommand(setRestriction + "1");
        final boolean isErrorOutput = output.contains("SecurityException")
                && output.contains("You need MANAGE_USERS permission");
        assertWithMessage("Trying to set user restriction should fail with SecurityException. "
                + "command output: %s", output).that(isErrorOutput).isTrue();
    }
}
