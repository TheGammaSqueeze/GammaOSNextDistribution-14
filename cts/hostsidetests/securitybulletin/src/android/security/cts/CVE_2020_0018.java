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

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;
import static org.junit.matchers.JUnitMatchers.containsString;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Scanner;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2020_0018 extends NonRootSecurityTestCase {

    /**
     * b/139945049
     */
    @Test
    @AsbSecurityTest(cveBugId = 139945049)
    public void testPocCVE_2020_0018() throws Exception {
        ITestDevice device = getDevice();
        String debuggable = device.getProperty("ro.debuggable").trim();

        /* If ro.debuggable is 1, KeyEvent info can be disclosed, and no need to check  */
        /* If ro.debuggable is 0, KeyEvent info should not be disclosed                 */
        /* The vulnerability is KeyEvent info is disclosed even if ro.debuggable is 0   */
        assumeThat("The debugger needs to be disabled for this vulnerability."+
                    "Skipping the test since ro.debuggable is enabled", debuggable, is("0"));

        /* Simulate MotionEvent */
        device.executeShellCommand("input tap 0 0");

        /* Simulate KeyEvent */
        device.executeShellCommand("input keyboard text \"f\"");

        String cmdOut = device.executeShellCommand("dumpsys input | grep -A 10 RecentQueue ");
        Scanner scanResult = new Scanner(cmdOut).useDelimiter("\\n+");
        while (scanResult.hasNext()) {
            String eventEntry = scanResult.nextLine();
            if (eventEntry.contains("MotionEvent") || eventEntry.contains("KeyEvent")) {
                assertThat("MotionEvent/ KeyEvent info leaked in dumpsys!", eventEntry,
                           not(containsString("deviceId")));
            }
        }
    }
}
