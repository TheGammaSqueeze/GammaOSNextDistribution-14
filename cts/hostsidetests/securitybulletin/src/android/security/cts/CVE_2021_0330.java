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

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.sts.common.util.TombstoneUtils;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2021_0330 extends NonRootSecurityTestCase {

    /**
     * b/170732441
     * Vulnerability Behaviour: SIGSEGV in storaged
     */
    @Test
    @AsbSecurityTest(cveBugId = 170732441)
    public void testPocCVE_2021_0330() throws Exception {
        AdbUtils.pocConfig testConfig = new AdbUtils.pocConfig("CVE-2021-0330", getDevice());
        testConfig.config = new TombstoneUtils.Config().setProcessPatterns("storaged");
        testConfig.config.setIgnoreLowFaultAddress(false);
        AdbUtils.runPocAssertNoCrashesNotVulnerable(testConfig);
    }
}
