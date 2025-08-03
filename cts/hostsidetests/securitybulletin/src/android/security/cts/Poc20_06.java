/*
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
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.sts.common.util.TombstoneUtils;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class Poc20_06 extends NonRootSecurityTestCase {

    /**
     * CVE-2020-3635
     */
    @Test
    @AsbSecurityTest(cveBugId = 148817146)
    public void testPocCVE_2020_3635() throws Exception {
        String isApplicable = AdbUtils.runCommandLine("service list", getDevice());
        assumeTrue(isApplicable.contains("com.qualcomm.qti.IPerfManager"));
        TombstoneUtils.Config config = new TombstoneUtils.Config().setProcessPatterns("perfservice");
        try (AutoCloseable a = TombstoneUtils.withAssertNoSecurityCrashes(getDevice(), config)) {
            AdbUtils.runCommandLine(
                    "service call vendor.perfservice 4 i32 1 i64 4702394920265069920", getDevice());
        }
    }

    /**
     * CVE-2020-3626
     */
    @Test
    @AsbSecurityTest(cveBugId = 150697952)
    public void testPocCVE_2020_3626() throws Exception {
        String isApplicable =
                AdbUtils.runCommandLine("pm list package com.qualcomm.qti.lpa", getDevice());
        if (!isApplicable.isEmpty()) {
            String result =
                    AdbUtils.runCommandLine("dumpsys package com.qualcomm.qti.lpa", getDevice());
            assertTrue(result.contains("com.qti.permission.USE_UIM_LPA_SERVICE"));
        }
    }

    /**
     * CVE-2020-3628
     */
    @Test
    @AsbSecurityTest(cveBugId = 150695508)
    public void testPocCVE_2020_3628() throws Exception {
        String result = AdbUtils.runCommandLine(
                "pm list package com.qualcomm.qti.logkit",getDevice());
        assertFalse(result.contains("com.qualcomm.qti.logkit"));
    }

    /**
     * CVE-2020-3676
     */
    @Test
    @AsbSecurityTest(cveBugId = 152310294)
    public void testPocCVE_2020_3676() throws Exception {
        String isApplicable = AdbUtils.runCommandLine("service list", getDevice());
        assumeTrue(isApplicable.contains("com.qualcomm.qti.IPerfManager"));
        TombstoneUtils.Config config = new TombstoneUtils.Config().setProcessPatterns("perfservice");
        try (AutoCloseable a = TombstoneUtils.withAssertNoSecurityCrashes(getDevice(), config)) {
            AdbUtils.runCommandLine(
                    "service call vendor.perfservice 4 i32 2442302356 i64 -2", getDevice());
        }
    }
}
