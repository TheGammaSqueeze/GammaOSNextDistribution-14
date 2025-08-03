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

import static java.util.Collections.singletonMap;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

@RunWith(DeviceJUnit4ClassRunner.class)
public class Bug_261036568 extends NonRootSecurityTestCase {

    private static final String TEST_PKG = "android.security.cts.BUG_261036568_test";

    @Test
    @AsbSecurityTest(cveBugId = 261036568)
    public void testBug_261036568() {
        ITestDevice device = null;
        int newUser = -1;
        try {
            device = getDevice();
            assumeTrue("Test requires multiple users", device.isMultiUserSupported());

            newUser = device.createUser("CtsUser", /* guest */ true, /* ephemeral */ false);
            assumeTrue("Unable to create test user", device.startUser(newUser, /* wait */ true));

            installPackage("Bug-261036568-provider.apk", "--user " + newUser);
            installPackage("Bug-261036568-test.apk");

            Map<String, String> args = singletonMap("target_user", Integer.toString(newUser));
            runDeviceTestsWithArgs(TEST_PKG, TEST_PKG + ".DeviceTest",
                    "testShareUnownedUriAsPreview", args);
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            try {
                if (newUser != -1) {
                    // Stop user 'CTSUser'
                    device.stopUser(newUser);

                    // Remove user 'CTSUser'
                    device.removeUser(newUser);
                }
            } catch (Exception e) {
                CLog.e("failed to clean up guest user %d: %e", newUser, e);
            }
        }
    }

    private boolean runDeviceTestsWithArgs(String pkgName, String testClassName,
            String testMethodName, Map<String, String> testArgs)
            throws DeviceNotAvailableException {
        final String testRunner = "androidx.test.runner.AndroidJUnitRunner";
        final long defaultTestTimeoutMs = 60 * 1000L;
        final long defaultMaxTimeoutToOutputMs = 60 * 1000L; // 1min
        return runDeviceTests(getDevice(),
                testRunner,
                pkgName,
                testClassName,
                testMethodName,
                /* userId */ null,
                defaultTestTimeoutMs,
                defaultMaxTimeoutToOutputMs,
                /* maxInstrumentationTimeoutMillis */ 0L,
                /* checkResults */ true,
                /* isHiddenApiCheckDisabled */ false,
                testArgs);
    }
}
