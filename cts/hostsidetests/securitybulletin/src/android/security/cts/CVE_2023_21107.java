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

package android.security.cts;

import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.UserInfo;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2023_21107 extends NonRootSecurityTestCase {
    private int mWorkUserId = -1;

    private AutoCloseable withManagedUser() throws Exception {
        final ITestDevice device = getDevice();
        String output =
                AdbUtils.runCommandLine(
                        "pm create-user --profileOf 0 --managed CVE_2023_21107_TestUser", device);
        Map<Integer, UserInfo> mapOfUserInfos = device.getUserInfos();
        for (UserInfo userInfo : mapOfUserInfos.values()) {
            if (userInfo.userName().equals("CVE_2023_21107_TestUser")) {
                mWorkUserId = userInfo.userId();
            }
        }
        assumeTrue(mWorkUserId != -1);
        assumeTrue(device.startUser(mWorkUserId, true /* waitFlag */));

        return new AutoCloseable() {
            @Override
            public void close() throws Exception {
                assumeTrue(device.stopUser(mWorkUserId, true /* waitFlag */, true /* forceFlag */));
                assumeTrue(device.removeUser(mWorkUserId));
                AdbUtils.runCommandLine("input keyevent KEYCODE_HOME", device);
            }
        };
    }

    // b/259385017
    // Vulnerable module : Settings.apk
    @AsbSecurityTest(cveBugId = 259385017)
    @Test
    public void testPocCVE_2023_21107() {
        try {
            final String testPkg = "android.security.cts.CVE_2023_21107_test";
            try (AutoCloseable managedUser = withManagedUser()) {
                // Install the helper app
                installPackage("CVE-2023-21107-helper.apk", "--user " + mWorkUserId);

                // Install the test app
                installPackage("CVE-2023-21107-test.apk");

                // Run the test "testCVE_2023_21107"
                runDeviceTests(testPkg, testPkg + ".DeviceTest", "testCVE_2023_21107");
            }
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
