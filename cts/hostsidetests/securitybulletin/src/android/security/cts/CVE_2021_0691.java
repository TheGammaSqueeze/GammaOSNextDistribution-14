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

import android.platform.test.annotations.AppModeInstant;
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
import static org.hamcrest.CoreMatchers.*;

/**
 * Test installs sample app and then tries to overwrite *.apk file
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2021_0691 extends NonRootSecurityTestCase {
    private static final String TEST_PKG = "android.security.cts.CVE_2021_0691";
    private static final String TEST_APP = "CVE-2021-0691.apk";
    private static final String DEVICE_TMP_DIR = "/data/local/tmp/";
    private static final String DEVICE_APP_DIR = "/data/app/";
    private static final String SCRIPT_NAME = "cve_2021_0691.sh";

    @Before
    public void setUp() throws Exception {
        uninstallPackage(getDevice(), TEST_PKG);
    }

    @Test
    @AsbSecurityTest(cveBugId = 188554048)
    @AppModeFull
    public void testRunDeviceTest() throws Exception {
        String cmd;
        String result;

        //push repro script and install test app
        AdbUtils.pushResource("/" + SCRIPT_NAME, DEVICE_TMP_DIR + SCRIPT_NAME, getDevice());
        AdbUtils.runCommandLine("chmod +x " + DEVICE_TMP_DIR + SCRIPT_NAME, getDevice());
        installPackage();

        //see repro script in log
        cmd = "cd " + DEVICE_TMP_DIR + "; cat " + SCRIPT_NAME;
        CLog.i("repro script: " + cmd);
        CLog.i(AdbUtils.runCommandLine(cmd, getDevice()));

        /*
         look for a location of test package dir
         for example: /data/app/
         ~~stRisM1TaNKYDnrHq9PHJg==/android.security.cts.CVE_2021_0691-borrWKTczXhO86vR9vwNJg==
        */
        cmd = "find " + DEVICE_APP_DIR + " -name \"*" + TEST_PKG + "*==\"";
        String appDir =  AdbUtils.runCommandLine(cmd, getDevice()).trim();
        CLog.i("look for appDir: " + cmd);
        CLog.i("appDir=" + appDir);

        //run repro steps as 'system' user (su 1000)
        cmd = "su 1000 " + DEVICE_TMP_DIR + SCRIPT_NAME + " " + appDir;
        result =  AdbUtils.runCommandLine(cmd, getDevice());
        CLog.i("command: " + cmd);
        CLog.i("result: " + result);

        //compare base.apk against aaa
        //if differs test passes
        cmd = "echo aaa | cmp -s " + appDir + "/base.apk; echo $?";
        result =  AdbUtils.runCommandLine(cmd, getDevice()).trim();
        CLog.i("command: " + cmd);
        CLog.i("result: " + result);
        assertThat(result, not(is("0")));
    }

    private void installPackage() throws Exception {
        installPackage(TEST_APP, new String[0]);
    }
}

