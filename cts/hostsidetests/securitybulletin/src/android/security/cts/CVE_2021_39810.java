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

package android.security.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2021_39810 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 212610736)
    @Test
    public void testPocCVE_2021_39810() {
        try {
            // clearing default payment app component if already set
            AdbUtils.runCommandLine("settings put secure nfc_payment_default_component null",
                    getDevice());
            installPackage("CVE-2021-39810.apk");
            String defaultComponent = AdbUtils.runCommandLine(
                    "settings get secure nfc_payment_default_component", getDevice());
            AdbUtils.runCommandLine("settings put secure nfc_payment_default_component null",
                    getDevice());
            assertFalse("Vulnerable to 212610736! Setting default payment app without user consent",
                    defaultComponent.contains("PocService"));
        } catch (Exception e) {
            // assumption failure if a generic exception is thrown by AdbUtils.runCommandLine()
            assumeNoException(e);
        }
    }
}
