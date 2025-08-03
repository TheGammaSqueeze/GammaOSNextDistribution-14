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

import com.android.tradefed.util.RunUtil;
import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.sts.common.util.TombstoneUtils;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2021_30351 extends NonRootSecurityTestCase {

    /**
     * CVE-2021-30351
     */
    @AsbSecurityTest(cveBugId = 201430561)
    @Test
    public void testPocCVE_2021_30351() throws Exception {
        final int SLEEP_INTERVAL_MILLISEC = 5 * 1000;
        String apkName = "CVE-2021-30351.apk";
        String appPath = AdbUtils.TMP_PATH + apkName;
        String packageName = "android.security.cts.CVE_2021_30351";
        ITestDevice device = getDevice();

        TombstoneUtils.Config config = new TombstoneUtils.Config().setProcessPatterns("media.codec");
        try (AutoCloseable a = TombstoneUtils.withAssertNoSecurityCrashes(getDevice(), config)) {
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
            AdbUtils.runCommandLine("am start -n " + packageName + "/.MainActivity", getDevice());
            RunUtil.getDefault().sleep(SLEEP_INTERVAL_MILLISEC);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            /* Un-install the app after the test */
            AdbUtils.runCommandLine("pm uninstall " + packageName, device);
        }
    }
}
