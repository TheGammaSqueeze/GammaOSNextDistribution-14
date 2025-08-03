/*
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

package com.android.cts.net;

import static com.android.cts.net.arguments.InstrumentationArguments.ARG_WAIVE_BIND_PRIORITY;

import android.platform.test.annotations.FlakyTest;

import com.android.testutils.SkipPresubmit;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;

import org.junit.Test;

import java.util.Map;

@SkipPresubmit(reason = "Out of SLO flakiness")
public class HostsideConnOnActivityStartTest extends HostsideNetworkTestCase {
    private static final String TEST_CLASS = TEST_PKG + ".ConnOnActivityStartTest";

    @BeforeClassWithInfo
    public static void setUpOnce(TestInformation testInfo) throws Exception {
        uninstallPackage(testInfo, TEST_APP2_PKG, false);
        installPackage(testInfo, TEST_APP2_APK);
    }

    @AfterClassWithInfo
    public static void tearDownOnce(TestInformation testInfo) throws DeviceNotAvailableException {
        uninstallPackage(testInfo, TEST_APP2_PKG, true);
    }

    @Test
    public void testStartActivity_batterySaver() throws Exception {
        runDeviceTests(TEST_PKG, TEST_CLASS, "testStartActivity_batterySaver");
    }

    @Test
    public void testStartActivity_dataSaver() throws Exception {
        runDeviceTests(TEST_PKG, TEST_CLASS, "testStartActivity_dataSaver");
    }

    @FlakyTest(bugId = 231440256)
    @Test
    public void testStartActivity_doze() throws Exception {
        runDeviceTests(TEST_PKG, TEST_CLASS, "testStartActivity_doze");
    }

    @Test
    public void testStartActivity_appStandby() throws Exception {
        runDeviceTests(TEST_PKG, TEST_CLASS, "testStartActivity_appStandby");
    }

    // TODO(b/321848487): Annotate with @RequiresFlagsEnabled to mirror the device-side test.
    @Test
    public void testStartActivity_default() throws Exception {
        runDeviceTestsWithArgs(TEST_PKG, TEST_CLASS, "testStartActivity_default",
                Map.of(ARG_WAIVE_BIND_PRIORITY, "true"));
    }
}
