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

package com.android.cts.tagging;

import static com.google.common.truth.Truth.assertThat;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assume.assumeThat;

import com.android.bedstead.harrier.annotations.enterprise.DevicePolicyRelevant;
import com.android.compatibility.common.util.CddTest;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

// Test the protocol described in
// https://source.android.com/docs/security/test/memory-safety/bootloader-support.
// This will reboot the device multiple times, which is perfectly normal.

@RunWith(DeviceJUnit4ClassRunner.class)
@DevicePolicyRelevant
public class MemtagBootctlTest extends BaseHostJUnit4Test {
    protected static final String TEST_PKG = "android.cts.tagging.is_mte_policy_enforced";
    protected static final String DEVICE_TEST_CLASS_NAME = "TaggingTest";
    private static String mPreviousState = null;

    @BeforeClassWithInfo
    public static void setUp(TestInformation testInfo) throws Exception {
        assumeThat(
                testInfo.getDevice().getProperty("ro.arm64.memtag.bootctl_supported"),
                equalTo("1"));
        mPreviousState = testInfo.getDevice().getProperty("arm64.memtag.bootctl");
        if (mPreviousState == null) {
            mPreviousState = "";
        }
    }

    @AfterClassWithInfo
    public static void tearDown(TestInformation testInfo) throws Exception {
        if (mPreviousState != null) {
            testInfo.getDevice().setProperty("arm64.memtag.bootctl", mPreviousState);
            testInfo.getDevice().reboot();
        }
    }

    private void runDeviceTest(String method) throws Exception {
        var options = new DeviceTestRunOptions(TEST_PKG);
        options.setTestClassName(TEST_PKG + "." + DEVICE_TEST_CLASS_NAME);
        options.setTestMethodName(method);
        runDeviceTests(options);
    }

    void checkMteOn() throws Exception {
        assertThat(getDevice().pullFileContents("/proc/cpuinfo")).contains(" mte");
        runDeviceTest("testMtePolicyEnforcedTrue");
    }

    void checkMteOff() throws Exception {
        assertThat(getDevice().pullFileContents("/proc/cpuinfo")).doesNotContain(" mte");
        runDeviceTest("testMtePolicyEnforcedFalse");
    }

    @Test
    @CddTest(requirements = {"9.7/C-3-1,9.7/C-3-2,9.7/C-3-3"})
    public void testMemtagOnce() throws Exception {
        getDevice().setProperty("arm64.memtag.bootctl", "memtag-once");
        getDevice().reboot();
        assertThat(getDevice().getProperty("arm64.memtag.bootctl")).isAnyOf("", "none", null);
        checkMteOn();
        getDevice().reboot();
        assertThat(getDevice().getProperty("arm64.memtag.bootctl")).isAnyOf("", "none", null);
        checkMteOff();
    }

    @Test
    @CddTest(requirements = {"9.7/C-3-1,9.7/C-3-2,9.7/C-3-3"})
    public void testMemtagOff() throws Exception {
        getDevice().setProperty("arm64.memtag.bootctl", "memtag-off");
        getDevice().reboot();
        assertThat(getDevice().getProperty("arm64.memtag.bootctl")).isEqualTo("memtag-off");
        checkMteOff();
        getDevice().reboot();
        assertThat(getDevice().getProperty("arm64.memtag.bootctl")).isEqualTo("memtag-off");
        checkMteOff();
    }

    @Test
    @CddTest(requirements = {"9.7/C-3-1,9.7/C-3-2,9.7/C-3-3,9.7/C-3-4"})
    public void testMemtag() throws Exception {
        getDevice().setProperty("arm64.memtag.bootctl", "memtag");
        getDevice().reboot();
        assertThat(getDevice().getProperty("arm64.memtag.bootctl")).isEqualTo("memtag");
        checkMteOn();
        getDevice().reboot();
        assertThat(getDevice().getProperty("arm64.memtag.bootctl")).isEqualTo("memtag");
        checkMteOn();
    }

    @Test
    @CddTest(requirements = {"9.7/C-3-1,9.7/C-3-2,9.7/C-3-3,9.7/C-3-4"})
    public void testBoth() throws Exception {
        getDevice().setProperty("arm64.memtag.bootctl", "memtag,memtag-once");
        getDevice().reboot();
        assertThat(getDevice().getProperty("arm64.memtag.bootctl")).isEqualTo("memtag");
        checkMteOn();
        getDevice().reboot();
        assertThat(getDevice().getProperty("arm64.memtag.bootctl")).isEqualTo("memtag");
        checkMteOn();
    }
}
