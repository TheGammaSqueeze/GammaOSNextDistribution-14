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

package android.cts.gwp_asan;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public abstract class GwpAsanBaseTest extends BaseHostJUnit4Test {
    protected static final String TEST_PKG = "android.cts.gwp_asan";
    protected ITestDevice mDevice;

    protected abstract String getTestApk();

    @Before
    public void setUp() throws Exception {
        mDevice = getDevice();
        installPackage(getTestApk(), new String[0]);

        // Reset the rate-limiting counters inside DropBoxManager, which only allows up to six
        // dropbox entries with the same {process name + entry tag} within any 10 minute period. We
        // can run the full suite right now without hitting the limit, but running the tests
        // multiple times runs into the limit. See
        // `services/core/java/com/android/server/am/DropboxRateLimiter.java` for more information.
        mDevice.executeShellCommand("am reset-dropbox-rate-limiter");
    }

    @After
    public void tearDown() throws Exception {
        uninstallPackage(mDevice, TEST_PKG);
    }

    public void resetAppExitInfo() throws Exception {
        mDevice.executeShellCommand("am clear-exit-info");
    }

    public void runTest(String testClass, String testName) throws Exception {
        Assert.assertTrue(runDeviceTests(TEST_PKG, TEST_PKG + testClass, testName));
    }
}
