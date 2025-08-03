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

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2021_39700 extends NonRootSecurityTestCase {

    /**
     * b/201645790
     * This test is related to
     * "hostsidetests/appsecurity/src/android/appsecurity/cts/ListeningPortsTest.java"
     */
    @AsbSecurityTest(cveBugId = 201645790)
    @Test
    public void testPocCVE_2021_39700() throws Exception {
        ITestDevice device = getDevice();
        assumeTrue("Failed to unroot the device", device.disableAdbRoot());
        String procUdp6File = "/proc/net/udp6";
        File tempFile = File.createTempFile("CVE_2021_39700", "temp");
        assertTrue("Vulnerable to b/201645790 !!", device.pullFile(procUdp6File, tempFile));
        tempFile.deleteOnExit();
    }
}
