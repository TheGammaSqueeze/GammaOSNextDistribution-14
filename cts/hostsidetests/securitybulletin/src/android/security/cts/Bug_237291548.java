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

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class Bug_237291548 extends NonRootSecurityTestCase {

    private static final String TEST_PKG = "android.security.cts.BUG_237291548";
    private static final String TEST_CLASS = TEST_PKG + ".DeviceTest";
    private static final String TEST_APP = "BUG-237291548.apk";
    private static final String TEST_FAIL_INSTALL_APP = "BUG-237291548-FAIL-INSTALL.apk";

    @Before
    public void setUp() throws Exception {
        super.setUp();
        uninstallPackage(getDevice(), TEST_PKG);
    }

    @Test
    @AsbSecurityTest(cveBugId = 237291548)
    public void testRunDeviceTestsPassesFull() throws Exception {
        installPackage(TEST_APP);

        runDeviceTests(TEST_PKG, TEST_CLASS, "testExceedGroupLimit");
        runDeviceTests(TEST_PKG, TEST_CLASS, "testExceedMimeLengthLimit");
    }

    @Test(expected = TargetSetupError.class)
    @AsbSecurityTest(cveBugId = 237291548)
    public void testInvalidApkFails() throws Exception {
        try {
            installPackage(TEST_FAIL_INSTALL_APP);
        } catch (TargetSetupError e) {
            assertThat(e.getMessage(),
                    containsString("Max limit on number of MIME Groups reached"));
            throw e;
        }
    }
}
