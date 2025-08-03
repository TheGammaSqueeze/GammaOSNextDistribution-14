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
public class Poc18_10 extends NonRootSecurityTestCase {

    /**
     *  b/111641492
     */
    @Test
    @AsbSecurityTest(cveBugId = 111641492)
    public void testPocCVE_2018_9515() throws Exception {
        AdbUtils.runCommandLine("rm /sdcard/Android/data/CVE-2018-9515", getDevice());
        AdbUtils.runCommandLine("mkdir /sdcard/Android/data/CVE-2018-9515", getDevice());
        AdbUtils.runPocNoOutput("CVE-2018-9515", getDevice(), 300);
        boolean vulnerableBecauseCrashed = getDevice().waitForDeviceNotAvailable(10_000);
        if (vulnerableBecauseCrashed) {
            // wait for device to come online so we can clean up
            getDevice().waitForDeviceAvailable(120_000); // 2 minutes
        }
        AdbUtils.runCommandLine("rm -rf /sdcard/Android/data/CVE-2018-9515", getDevice());
    }
}
