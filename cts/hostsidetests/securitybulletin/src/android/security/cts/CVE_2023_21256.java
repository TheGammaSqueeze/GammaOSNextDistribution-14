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

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.UserInfo;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2023_21256 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 268193384)
    @Test
    public void testPocCVE_2023_21256() {
        ITestDevice device = null;
        int workUserId = -1;
        try {
            final String testPkg = "android.security.cts.CVE_2023_21256";
            device = getDevice();

            assumeTrue(device.isMultiUserSupported());

            AdbUtils.runCommandLine(
                    "pm create-user --profileOf 0 --managed CVE_2023_21256_work_user", device);

            Map<Integer, UserInfo> mapOfUserInfos = device.getUserInfos();
            for (UserInfo userInfo : mapOfUserInfos.values()) {
                if (userInfo.userName().equals("CVE_2023_21256_work_user")) {
                    workUserId = userInfo.userId();
                }
            }
            assumeFalse(workUserId == -1);

            assumeTrue(device.startUser(workUserId, true /* waitFlag */));

            // Install the test app in work profile
            installPackage("CVE-2023-21256.apk", "--user " + workUserId);

            runDeviceTests(
                    new DeviceTestRunOptions(testPkg)
                            .setDevice(device)
                            .setTestClassName(testPkg + ".DeviceTest")
                            .setTestMethodName("testSettingsHomePageActivityFromWorkProfile")
                            .setUserId(workUserId));
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            if (workUserId != -1) {
                try {
                    device.stopUser(workUserId, true /* waitFlag */, true /* forceFlag */);
                    device.removeUser(workUserId);
                } catch (Exception e) {
                    // Ignore exceptions as the test has finished
                }
            }
        }
    }
}
