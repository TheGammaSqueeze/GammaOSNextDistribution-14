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

import static com.android.sts.common.NativePocStatusAsserter.assertNotVulnerableExitCode;

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.NativePoc;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2022_20357 extends NonRootSecurityTestCase {

    // b/214999987
    // Vulnerability Behaviour : EXIT_VULNERABLE
    // Vulnerable Function     : writeToParcel (As per AOSP code)
    // Vulnerable Library      : libgui (As per AOSP code)
    @AsbSecurityTest(cveBugId = 214999987)
    @Test
    public void testPocCVE_2022_20357() {
        try {
            NativePoc.builder()
                    .pocName("CVE-2022-20357")
                    .asserter(assertNotVulnerableExitCode())
                    .build()
                    .run(this);
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
