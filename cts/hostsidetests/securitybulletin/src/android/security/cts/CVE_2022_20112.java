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

import com.android.sts.common.UserUtils;
import com.android.sts.common.tradefed.testtype.RootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2022_20112 extends RootSecurityTestCase {
    private static final String FEATURE_AUTOMOTIVE = "android.hardware.type.automotive";

    // b/206987762
    // Vulnerable module : com.android.settings
    // Vulnerable apk : Settings.apk
    // Is play managed : No
    @AsbSecurityTest(cveBugId = 206987762)
    @Test
    public void testPocCVE_2022_20112() {
        try {
            // This setting is not supported on automotive
            if (isAutomotive()) return;

            final ITestDevice device = getDevice();
            try (AutoCloseable asGuestUser =
                    new UserUtils.SecondaryUser(device)
                            .name("CTSUser")
                            .guest()
                            .doSwitch()
                            .withUser()) {
                // Install PoC application in guest user
                installPackageAsUser(
                        "CVE-2022-20112.apk", false /* grantPermission */, device.getCurrentUser());

                final String testPkg = "android.security.cts.CVE_2022_20112";
                runDeviceTests(
                        testPkg, testPkg + ".DeviceTest", "testPrivateDnsPreferenceController");
            }
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    private boolean isAutomotive() throws Exception {
        return getDevice().hasFeature(FEATURE_AUTOMOTIVE);
    }
}
