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

package android.host.multiuser;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.LargeTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(DeviceJUnit4ClassRunner.class)
public class UserManagerHostTest extends BaseMultiUserTest {

    @Rule
    public final SupportsMultiUserRule mSupportsMultiUserRule = new SupportsMultiUserRule(this);

    @Test
    @ApiTest(apis = {"android.os.UserManager#getPreviousForegroundUser"})
    public void getPreviousForegroundUser_correctAfterReboot() throws Exception {
        assumeNewUsersCanBeAdded(2);

        final int userId1 = getDevice().createUser("test_user_1");
        assertSwitchToUser(userId1);

        final int userId2 = getDevice().createUser("test_user_2");
        assertSwitchToUser(userId2);
        assertPreviousUserIs(userId1);

        getDevice().reboot();
        if (getDevice().getCurrentUser() == userId2) {
            assertPreviousUserIs(userId1);
        } else {
            assertPreviousUserIs(userId2);
        }
    }

    private void assertPreviousUserIs(int expected) throws Exception {
        final DeviceTestRunOptions options = new DeviceTestRunOptions(TEST_APP_PKG_NAME)
                .setDevice(getDevice())
                .setApkFileName(TEST_APP_PKG_APK)
                .setTestClassName(TEST_APP_PKG_NAME + ".UserManagerAppTest")
                .setTestMethodName("getPreviousForegroundUserReturnsExpected")
                .addInstrumentationArg("expectedResult", String.valueOf(expected));
        installPackage(options);
        final boolean testResult = runDeviceTests(options);
        assertThat(testResult).isTrue();
    }

    private void assumeNewUsersCanBeAdded(int noOfUsers) throws DeviceNotAvailableException {
        assumeTrue("Cannot allow adding " + noOfUsers + " new users.",
                noOfUsers <= remainingUsersAllowedToBeCreated());
    }

    private int remainingUsersAllowedToBeCreated() throws DeviceNotAvailableException {
        int nonGuestUsersCount =  (int) getDevice().getUserInfos().values().stream()
                .filter(userInfo -> !userInfo.isGuest())
                .count();
        return getDevice().getMaxNumberOfUsersSupported() - nonGuestUsersCount;
    }

}
