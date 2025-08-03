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

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2023_35667 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 282932362)
    @Test
    public void testPocCVE_2023_35667() {
        try {
            installPackage("CVE-2023-35667-test.apk");
            installPackage("CVE-2023-35667-helper1.apk");
            installPackage("CVE-2023-35667-helper2.apk");

            // Sufficiently large component name to invoke vulnerability.
            final String sufficientlyLargeComponentName =
                    String.format("%0" + (215 /* ServiceClassNameLength */) + "d", 0)
                            .replace("0", "A");
            final String testPkg = "android.security.cts.CVE_2023_35667";
            final String testServicePkg = testPkg + "." + sufficientlyLargeComponentName;

            // Allowing NotificationListenerService.
            ITestDevice uiDevice = getDevice();
            String cmdToAllowListenerService = "cmd notification allow_listener %s/%s.%s";
            uiDevice.executeShellV2Command(
                    String.format(
                            cmdToAllowListenerService,
                            testPkg + ".aaaaab",
                            testPkg + ".aaaaab",
                            "HelperListenerService1"));
            uiDevice.executeShellV2Command(
                    String.format(
                            cmdToAllowListenerService,
                            testPkg,
                            testServicePkg,
                            sufficientlyLargeComponentName));
            uiDevice.executeShellV2Command(
                    String.format(
                            cmdToAllowListenerService,
                            testPkg + ".aaaaaa",
                            testPkg + ".aaaaaa",
                            "HelperListenerService2"));

            runDeviceTests(testPkg, testPkg + ".DeviceTest", "testPocCVE_2023_35667");
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
