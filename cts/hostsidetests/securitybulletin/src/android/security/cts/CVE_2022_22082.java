/**
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

import static org.junit.Assume.*;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.sts.common.util.TombstoneUtils;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.RunUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2022_22082 extends NonRootSecurityTestCase {

    /**
     * CVE-2022-22082
     */
    @AsbSecurityTest(cveBugId = 223211217)
    @Test
    public void testPocCVE_2022_22082() throws Exception {
        /*
         * Non StageFright test.
         */
        safeReboot();
        AdbUtils.pushResource("/cve_2022_22082.dsf", "/sdcard/cve_2022_22082.dsf", getDevice());
        TombstoneUtils.Config config = new TombstoneUtils.Config().setProcessPatterns("media.extractor");
        try (AutoCloseable a = TombstoneUtils.withAssertNoSecurityCrashes(getDevice(), config)) {
            AdbUtils.runCommandLine(
                    "am start -a android.intent.action.VIEW -t audio/dsf -d"
                            + " file:///sdcard/cve_2022_22082.dsf",
                    getDevice());
            RunUtil.getDefault().sleep(10000);
        }
    }
}
