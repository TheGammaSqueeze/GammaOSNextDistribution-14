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
public class Poc16_11 extends NonRootSecurityTestCase {

    /**
     *  b/29149404
     */
    @Test
    @AsbSecurityTest(cveBugId = 72498387)
    public void testPocCVE_2012_6702() throws Exception {
        AdbUtils.runCommandLine("logcat -c", getDevice());
        AdbUtils.runPoc("CVE-2012-6702", getDevice(), 60);
        String logcat = AdbUtils.runCommandLine("logcat -d", getDevice());
        assertNotMatchesMultiLine("fail: encountered same random values!", logcat);
    }

    /**
     *  b/30904789
     */
    @Test
    @AsbSecurityTest(cveBugId = 30904789)
    public void testPocCVE_2016_6730() throws Exception {
        if(containsDriver(getDevice(), "/dev/dri/renderD129")) {
            AdbUtils.runPoc("CVE-2016-6730", getDevice(), 60);
        }
    }

    /**
     *  b/30906023
     */
    @Test
    @AsbSecurityTest(cveBugId = 30906023)
    public void testPocCVE_2016_6731() throws Exception {
        if(containsDriver(getDevice(), "/dev/dri/renderD129")) {
            AdbUtils.runPoc("CVE-2016-6731", getDevice(), 60);
        }
    }

    /**
     *  b/30906599
     */
    @Test
    @AsbSecurityTest(cveBugId = 30906599)
    public void testPocCVE_2016_6732() throws Exception {
        if(containsDriver(getDevice(), "/dev/dri/renderD129")) {
            AdbUtils.runPoc("CVE-2016-6732", getDevice(), 60);
        }
    }

    /**
     *  b/30906694
     */
    @Test
    @AsbSecurityTest(cveBugId = 30906694)
    public void testPocCVE_2016_6733() throws Exception {
        if(containsDriver(getDevice(), "/dev/dri/renderD129")) {
            AdbUtils.runPoc("CVE-2016-6733", getDevice(), 60);
        }
    }

    /**
     *  b/30907120
     */
    @Test
    @AsbSecurityTest(cveBugId = 30907120)
    public void testPocCVE_2016_6734() throws Exception {
        if(containsDriver(getDevice(), "/dev/dri/renderD129")) {
            AdbUtils.runPoc("CVE-2016-6734", getDevice(), 60);
        }
    }

    /**
     *  b/30907701
     */
    @Test
    @AsbSecurityTest(cveBugId = 30907701)
    public void testPocCVE_2016_6735() throws Exception {
        if(containsDriver(getDevice(), "/dev/dri/renderD129")) {
            AdbUtils.runPoc("CVE-2016-6735", getDevice(), 60);
        }
    }

    /**
     *  b/30953284
     */
    @Test
    @AsbSecurityTest(cveBugId = 30953284)
    public void testPocCVE_2016_6736() throws Exception {
        if(containsDriver(getDevice(), "/dev/dri/renderD129")) {
            AdbUtils.runPoc("CVE-2016-6736", getDevice(), 60);
        }
    }
}
