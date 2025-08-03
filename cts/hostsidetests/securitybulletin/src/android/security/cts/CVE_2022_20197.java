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

import static com.android.sts.common.SystemUtil.withSetting;

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2022_20197 extends NonRootSecurityTestCase {
    private static final String TEST_PKG = "android.security.cts.CVE_2022_20197";

    @AsbSecurityTest(cveBugId = 208279300)
    @Test
    public void testPocCVE_2022_20197() {
        try (AutoCloseable a = withSetting(getDevice(), "global", "hidden_api_policy", "1")) {
            installPackage("CVE-2022-20197.apk");
            runDeviceTests(TEST_PKG, TEST_PKG + ".DeviceTest", "testParcel");
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
