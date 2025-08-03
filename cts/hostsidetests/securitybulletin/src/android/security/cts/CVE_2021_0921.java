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

import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.platform.test.annotations.AsbSecurityTest;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.log.LogUtil.CLog;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2021_0921 extends NonRootSecurityTestCase {
    private static final String TEST_PKG = "android.security.cts.CVE_2021_0921";
    private static final String TEST_CLASS = TEST_PKG + "." + "DeviceTest";
    private static final String TEST_APP = "CVE-2021-0921.apk";

    @Before
    public void setUp() throws Exception {
        uninstallPackage(getDevice(), TEST_PKG);
    }

    @Test
    @AsbSecurityTest(cveBugId = 195962697)
    @AppModeFull
    public void testRunDeviceTest() throws Exception {

        CLog.i("testRunDeviceTest() start");
        installPackage();

        //ensure the screen is woken up.
        //KEYCODE_WAKEUP wakes up the screen
        //KEYCODE_MENU called twice unlocks the screen (if locked)
        getDevice().executeShellCommand("input keyevent KEYCODE_WAKEUP");
        getDevice().executeShellCommand("input keyevent KEYCODE_MENU");
        getDevice().executeShellCommand("input keyevent KEYCODE_HOME");
        getDevice().executeShellCommand("input keyevent KEYCODE_MENU");

        //run the test
        runDeviceTests(TEST_PKG, TEST_CLASS, "test");
        CLog.i("testRunDeviceTest() end");
    }

    private void installPackage() throws Exception {
        installPackage(TEST_APP, new String[0]);
    }
}

