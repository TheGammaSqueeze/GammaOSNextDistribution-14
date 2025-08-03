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
import com.android.tradefed.device.TestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;


/** Target preparer for creating user and cleaning it up at the end. */
@OptionClass(alias = "create-user-preparer")
public class CreateUserPreparer extends BaseTargetPreparer {

    @Option(
            name = "reuse-test-user",
            description =
                    "Whether or not to reuse already created tradefed test user, or remove them "
                            + " and re-create them between module runs.")
    private boolean mReuseTestUser;

    private Integer mOriginalUser;
    private Integer mCreatedUserId;

    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        ITestDevice device = testInfo.getDevice();
        mOriginalUser = device.getCurrentUser();
        if (mOriginalUser == TestDevice.INVALID_USER_ID) {
            mOriginalUser = null;
            throw new TargetSetupError(
                    "Failed to get the current user.", device.getDeviceDescriptor());
        }
        CLog.i("setUp(): mOriginalUser=%d, mReuseTestUser=%b", mOriginalUser, mReuseTestUser);

        mCreatedUserId = UserHelper.createUser(device, mReuseTestUser);

        switchCurrentUser(device, mCreatedUserId);

        device.waitForDeviceAvailable();
        device.postBootSetup();
    }

    private void switchCurrentUser(ITestDevice device, int userId)
            throws TargetSetupError, DeviceNotAvailableException {
        if (!device.startUser(mCreatedUserId, true)) {
            throw new TargetSetupError(
                    String.format("Failed to start to user '%s'", mCreatedUserId),
                    device.getDeviceDescriptor());
        }
        if (!device.switchUser(mCreatedUserId)) {
            throw new TargetSetupError(
                    String.format("Failed to switch to user '%s'", mCreatedUserId),
                    device.getDeviceDescriptor());
        }
    }

    @Override
    public void tearDown(TestInformation testInfo, Throwable e) throws DeviceNotAvailableException {
        if (mCreatedUserId == null) {
            CLog.d("Skipping teardown because no user was created");
            return;
        }
        if (e instanceof DeviceNotAvailableException) {
            CLog.d("Skipping teardown due to dnae: %s", e.getMessage());
            return;
        }
        ITestDevice device = testInfo.getDevice();

        if (mOriginalUser == null) {
            CLog.d("Skipping teardown because original user is null");
            return;
        }
        switchBackToOriginalUser(device);

        if (!mReuseTestUser) {
            device.removeUser(mCreatedUserId);
        }
    }

    private void switchBackToOriginalUser(ITestDevice device) throws DeviceNotAvailableException {
        CLog.d(
                "switchBackToOriginalUser(): switching current user from %d to user %d ",
                mCreatedUserId, mOriginalUser);
        if (!device.switchUser(mOriginalUser)) {
            CLog.e("Failed to switch back to original user '%s'", mOriginalUser);
        }
    }
}
