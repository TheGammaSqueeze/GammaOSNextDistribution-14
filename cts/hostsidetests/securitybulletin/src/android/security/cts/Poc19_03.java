/**
 * Copyright (C) 2020 The Android Open Source Project
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

import static org.junit.Assert.*;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class Poc19_03 extends NonRootSecurityTestCase {
    /**
     * b/115739809
     */
    @Test
    @AsbSecurityTest(cveBugId = 115739809)
    public void testPocBug_115739809() throws Exception {
        assertFalse(AdbUtils.runPocCheckExitCode("Bug-115739809", getDevice(), 30));
    }

    /**
     * b/116855682
     */
    @Test
    @AsbSecurityTest(cveBugId = 116855682)
    public void testPocCVE_2019_2025() throws Exception {
        AdbUtils.runPocNoOutput("CVE-2019-2025", getDevice(), 300);
    }
}
