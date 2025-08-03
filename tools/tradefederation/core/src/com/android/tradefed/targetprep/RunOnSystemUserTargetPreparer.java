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

package com.android.tradefed.targetprep;

import static com.android.tradefed.targetprep.UserHelper.RUN_TESTS_AS_USER_KEY;

import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;

/**
 * An {@link ITargetPreparer} that marks that tests should be run on the user (rather than the
 * current user).
 *
 * <p>By default, this will switch user so the system user is in the foreground.
 *
 * <p>When running on a device with a headless system user, the user will not be switched by
 * default, but the tests will still run on that user.
 */
@OptionClass(alias = "run-on-system-user")
public class RunOnSystemUserTargetPreparer extends BaseTargetPreparer {

    private Integer mUserToSwitchTo;

    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, DeviceNotAvailableException {
        if (!testInfo.getDevice().isHeadlessSystemUserMode()) {
            ensureSwitchedToUser(testInfo.getDevice(), 0);
        }

        testInfo.properties().put(RUN_TESTS_AS_USER_KEY, "0");
    }

    @Override
    public void tearDown(TestInformation testInfo, Throwable e) throws DeviceNotAvailableException {
        testInfo.properties().remove(RUN_TESTS_AS_USER_KEY);
        if (mUserToSwitchTo != null) {
            ensureSwitchedToUser(testInfo.getDevice(), mUserToSwitchTo);
        }
    }

    private void ensureSwitchedToUser(ITestDevice device, int userId)
            throws DeviceNotAvailableException {
        int currentUser = device.getCurrentUser();

        if (currentUser == userId) {
            return;
        }

        if (!device.switchUser(userId)) {
            throw new IllegalStateException("Could not switch to user " + userId);
        }
        mUserToSwitchTo = currentUser;
    }
}
