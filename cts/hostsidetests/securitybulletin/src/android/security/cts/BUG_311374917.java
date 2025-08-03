/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
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
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class BUG_311374917 extends NonRootSecurityTestCase {
    @AsbSecurityTest(cveBugId = 311374917)
    @Test
    public void testPocBUG_311374917() {
        try {
            // Install the test app
            installPackage("BUG-311374917.apk");

            // Run the test "testBUG_311374917"
            final String testPkg = "android.security.cts.BUG_311374917";
            runDeviceTests(testPkg, testPkg + ".DeviceTest", "testBUG_311374917");
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
