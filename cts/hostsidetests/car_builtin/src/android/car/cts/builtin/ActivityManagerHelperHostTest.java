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

package android.car.cts.builtin;

import static com.google.common.truth.Truth.assertThat;

import android.car.cts.builtin.user.CarInitialUserCommand;
import android.car.cts.builtin.user.InitializedUsersCommand;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class ActivityManagerHelperHostTest extends CarBuiltinApiHostCtsBase {
    private static final int SYSTEM_USER = 0;

    // The startUserInForeground, startUserInBackground and unlockUser ActivityManagerHelper
    // APIs are called by InitialUserSetter during device boot.Checking the default users
    // are properly initialized covers the API testing.
    @Test
    public void testDefaultUserInitialization() throws Exception {
        InitializedUsersCommand initUsersCommand = new InitializedUsersCommand(getDevice());
        initUsersCommand.executeWith();
        List<Integer> initUsers = initUsersCommand.getInitializedUsers();

        ArrayList<Integer> defaultUsers = new ArrayList<>();
        defaultUsers.add(SYSTEM_USER);
        if (initUsersCommand.hasHeadlessUser()) {
            CarInitialUserCommand initialUserCommand = new CarInitialUserCommand(getDevice());
            initialUserCommand.executeWith();
            int initialUser = initialUserCommand.getCarInitialUser();
            defaultUsers.add(initialUser);
        }

        assertThat(initUsers).containsAtLeastElementsIn(defaultUsers);
    }

    @Test
    public void testStopUserWithDelayedLocking() throws Exception {
        // CtsCarHostTestCases:CarGarageModeAtomTests covers the stopUserWithDelayedLocking
        // API call.
    }
}
