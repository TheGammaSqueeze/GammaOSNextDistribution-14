/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.UserInfo;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.Map;

/**
 * A {@link ITargetPreparer} that switches to the specified user kind in setUp. By default it
 * remains in the current user, and no switching is performed.
 *
 * <p>Tries to restore device user state by switching back to the pre-execution current user.
 */
@OptionClass(alias = "switch-user-target-preparer")
public class SwitchUserTargetPreparer extends BaseTargetPreparer {
    @Option(
        name = "user-type",
        description = "The type of user to switch to before the module run."
    )
    private UserInfo.UserType mUserToSwitchTo = UserInfo.UserType.CURRENT;

    private int mPreExecutionCurrentUser;

    @Override
    public void setUp(TestInformation testInformation)
            throws TargetSetupError, DeviceNotAvailableException {
        ITestDevice device = testInformation.getDevice();
        setUserToSwitchTo(device);

        mPreExecutionCurrentUser = device.getCurrentUser();
        Map<Integer, UserInfo> userInfos = device.getUserInfos();

        if (userInfos
                .get(mPreExecutionCurrentUser)
                .isUserType(mUserToSwitchTo, mPreExecutionCurrentUser)) {
            CLog.i(
                    "User %d is already user type %s, no action.",
                    mPreExecutionCurrentUser, mUserToSwitchTo.toString());
            return;
        }

        for (UserInfo userInfo : userInfos.values()) {
            if (userInfo.isUserType(mUserToSwitchTo, mPreExecutionCurrentUser)) {
                CLog.i(
                        "User %d is user type %s, switching from %d",
                        userInfo.userId(), mUserToSwitchTo.toString(), mPreExecutionCurrentUser);
                if (!device.switchUser(userInfo.userId())) {
                    throw new TargetSetupError(
                            String.format("Device failed to switch to user %d", userInfo.userId()),
                            device.getDeviceDescriptor());
                }
                return;
            }
        }

        throw new TargetSetupError(
                String.format(
                        "Failed switch to user type %s, no user of that type exists",
                        mUserToSwitchTo),
                device.getDeviceDescriptor());
    }

    @Override
    public void tearDown(TestInformation testInformation, Throwable e)
            throws DeviceNotAvailableException {
        // Restore the previous user as the foreground.
        if (testInformation.getDevice().switchUser(mPreExecutionCurrentUser)) {
            CLog.d("Successfully switched back to user id: %d", mPreExecutionCurrentUser);
        } else {
            CLog.w("Could not switch back to the user id: %d", mPreExecutionCurrentUser);
        }
    }

    /**
     * In some form factors running on headless system user mode, it is restricted to switch to the
     * {@link UserInfo.UserType#SYSTEM SYSTEM} user. In such cases, change the {@link
     * #mUserToSwitchTo} to the {@link UserInfo.UserType#MAIN MAIN} user.
     */
    private void setUserToSwitchTo(ITestDevice device) throws DeviceNotAvailableException {
        try {
            if (UserInfo.UserType.SYSTEM.equals(mUserToSwitchTo)
                    && device.isHeadlessSystemUserMode()
                    && !device.canSwitchToHeadlessSystemUser()) {
                mUserToSwitchTo = UserInfo.UserType.MAIN;
                CLog.i("SwitchUserTargetPreparer is configured to switch to the MAIN user.");
            }
        } catch (HarnessRuntimeException e) {
            CLog.w("Unable to get the main user switch-ability. Error: ", e);
        }
    }
}
