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

import static com.android.sts.common.SystemUtil.withSetting;

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2023_21000 extends NonRootSecurityTestCase {
    private static final String TAG = "CVE_2023_21000";

    // b/236688380
    // Is Play Managed    : No
    @AsbSecurityTest(cveBugId = 236688380)
    @ApiTest(apis = "com.android.server.am.ActivityManagerService#openContentUri")
    @Test
    public void testPocCVE_2023_21000() {
        try (AutoCloseable a = withSetting(getDevice(), "global", "hidden_api_policy", "1")) {
            final String testPkg = "android.security.cts.CVE_2023_21000_test";

            // Install the test app
            installPackage("CVE-2023-21000-test.apk", "-t");

            // Run the test "testCVE_2023_21000"
            runDeviceTests(testPkg, testPkg + ".DeviceTest", "testCVE_2023_21000");
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
