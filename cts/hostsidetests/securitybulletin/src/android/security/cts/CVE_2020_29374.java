/*
 * Copyright (C) 2021 The Android Open Source Project
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
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.sts.common.util.KernelVersionHost;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2020_29374 extends NonRootSecurityTestCase {

   /**
     * b/174737879
     *
     */
    @AsbSecurityTest(cveBugId = 174737879)
    @Test
    public void testPocCVE_2020_29374() throws Exception {
        assumeTrue(KernelVersionHost.isKernelVersionGreaterThanEqualTo(getDevice(), "5.4.0"));
        AdbUtils.runPocAssertExitStatusNotVulnerable("CVE-2020-29374", getDevice(),60);
    }
}
