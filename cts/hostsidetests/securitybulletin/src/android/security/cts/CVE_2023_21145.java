/*
 * Copyright (C) 2023 The Android Open Source Project
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
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.ProcessUtil;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.RunUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Optional;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2023_21145 extends NonRootSecurityTestCase {
    private final int mNoPidFound = -1; /* Default pid */

    @AsbSecurityTest(cveBugId = 265293293)
    @Test
    public void testPocCVE_2023_21145() {
        try {
            ITestDevice device = getDevice();

            // Install poc and start PipActivity to invoke the vulnerability
            installPackage("CVE-2023-21145.apk");
            String pocPkg = "android.security.cts.CVE_2023_21145";
            device.executeShellCommand("am start-activity " + pocPkg + "/.PipActivity");

            // Wait for the PoC to start
            final int initialPid = waitAndGetPid(device, mNoPidFound /* initial pid */);
            assumeTrue("PoC process did not start", initialPid != mNoPidFound);

            // Wait for the PoC to be killed or restart
            final int latestPid = waitAndGetPid(device, initialPid);
            assumeTrue("PoC process did not die", latestPid != initialPid);

            // Without fix, the process restarts with new pid
            assertTrue("Device is vulnerable to b/265293293 !!", latestPid == mNoPidFound);
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    private int waitAndGetPid(ITestDevice device, int initialPid) throws Exception {
        final long timeout = 10_000L;
        final String processName = "android.security.cts.CVE_2023_21145:pipActivity";

        // Check if pid has changed
        int currentPid = mNoPidFound;
        long startTime = System.currentTimeMillis();
        while ((currentPid == mNoPidFound || currentPid == initialPid) // Check if pid has changed
                && System.currentTimeMillis() - startTime <= timeout) {
            Optional<Integer> pid = ProcessUtil.pidOf(device, processName);
            currentPid = pid.isPresent() ? pid.get() : mNoPidFound;
            RunUtil.getDefault().sleep(200); // Sleep for 200 ms before checking pid again
        }
        return currentPid;
    }
}
