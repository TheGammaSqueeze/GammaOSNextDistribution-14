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
import com.android.sts.common.util.TombstoneUtils;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.RunUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class Poc17_11 extends NonRootSecurityTestCase {

    /**
     * b/36075131
     */
    @Test
    @AsbSecurityTest(cveBugId = 36075131)
    public void testPocCVE_2017_0859() throws Exception {
        AdbUtils.pushResource("/cve_2017_0859.mp4", "/sdcard/cve_2017_0859.mp4", getDevice());
        TombstoneUtils.Config config = new TombstoneUtils.Config().setProcessPatterns("mediaserver");
        try (AutoCloseable a = TombstoneUtils.withAssertNoSecurityCrashes(getDevice(), config)) {
            AdbUtils.runCommandLine("am start -a android.intent.action.VIEW " +
                                        "-d file:///sdcard/cve_2017_0859.mp4" +
                                        " -t audio/amr", getDevice());
            // Wait for intent to be processed before checking logcat
            RunUtil.getDefault().sleep(5000);
        }
    }
}
