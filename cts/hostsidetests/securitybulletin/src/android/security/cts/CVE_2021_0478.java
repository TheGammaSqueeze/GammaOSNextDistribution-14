/**
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

import com.android.tradefed.util.RunUtil;
import android.platform.test.annotations.AsbSecurityTest;
import android.platform.test.annotations.SecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2021_0478 extends NonRootSecurityTestCase {

    /**
     * b/169255797
     */
    @AsbSecurityTest(cveBugId = 169255797)
    @SecurityTest(minPatchLevel = "2021-06")
    @Test
    public void testPocCVE_2021_0478() throws Exception {
        final int SLEEP_INTERVAL_MILLISEC = 30 * 1000;
        String apkName = "CVE-2021-0478.apk";
        String appPath = AdbUtils.TMP_PATH + apkName;
        String packageName = "android.security.cts.cve_2021_0478";
        String crashPattern = "Canvas: trying to draw too large";
        ITestDevice device = getDevice();

        try {
            /* Push the app to /data/local/tmp */
            pocPusher.appendBitness(false);
            pocPusher.pushFile(apkName, appPath);

            /* Wake up the screen */
            AdbUtils.runCommandLine("input keyevent KEYCODE_WAKEUP", device);
            AdbUtils.runCommandLine("input keyevent KEYCODE_MENU", device);
            AdbUtils.runCommandLine("input keyevent KEYCODE_HOME", device);

            /* Install the application */
            AdbUtils.runCommandLine("pm install " + appPath, device);

            /* Start the application */
            AdbUtils.runCommandLine("am start -n " + packageName + "/.PocActivity", getDevice());
            RunUtil.getDefault().sleep(SLEEP_INTERVAL_MILLISEC);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            /* Un-install the app after the test */
            AdbUtils.runCommandLine("pm uninstall " + packageName, device);

            /* Check if System UI has crashed thereby indicating the presence */
            /* of the vulnerability */
            String logcat = AdbUtils.runCommandLine("logcat -d *:S AndroidRuntime:E", device);
            assertNotMatches(crashPattern, logcat);
        }
    }
}
