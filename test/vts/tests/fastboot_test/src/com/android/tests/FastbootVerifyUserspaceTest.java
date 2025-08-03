/*
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

package com.android.tests.fastboot;

import android.platform.test.annotations.RequiresDevice;
import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.device.IManagedTestDevice;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import java.io.File;
import java.lang.Thread;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/* VTS test to verify userspace fastboot implementation. */
@RequiresDevice
@RunWith(DeviceJUnit4ClassRunner.class)
public class FastbootVerifyUserspaceTest extends BaseHostJUnit4Test {
    // Default maximum command run time is set to 90 seconds.
    private static final long MAX_CMD_RUN_TIME = 90000L;
    private static String executeShellKernelARM64 =
            "cat /proc/config.gz | gzip -d | grep CONFIG_ARM64=y";

    private IRunUtil mRunUtil = RunUtil.getDefault();
    private String mFuzzyFastbootPath;

    // IMPORTANT: Multiple instances of this class will be created while sharding
    // the tests across multiple devices. So it needs to use ConcurrentHashMap to
    // make these static variables thread-safe.
    private static ConcurrentHashMap<ITestDevice, Boolean> sDeviceIsGKI10 =
            new ConcurrentHashMap<>();

    @BeforeClassWithInfo
    public static void setUpClass(TestInformation testInfo) throws Exception {
        // Collects information while adb is available, prior to rebooting into fastbootd.
        boolean isKernelARM64 = testInfo.getDevice()
                                        .executeShellCommand(executeShellKernelARM64)
                                        .contains("CONFIG_ARM64");
        boolean isGKI10 = false;
        if (isKernelARM64) {
            String output = testInfo.getDevice().executeShellCommand("uname -r");
            Pattern p = Pattern.compile("^(\\d+)\\.(\\d+)");
            Matcher m1 = p.matcher(output);
            Assert.assertTrue(m1.find());
            isGKI10 = (Integer.parseInt(m1.group(1)) == 5 && Integer.parseInt(m1.group(2)) == 4);
        }

        // Saves the local variable to the static variable for later use, because adb
        // is not available in the following tests.
        sDeviceIsGKI10.put(testInfo.getDevice(), isGKI10);

        // Transfers from adb to fastbootd.
        if (!isGKI10) {
            testInfo.getDevice().rebootIntoFastbootd();
        }
    }

    @Before
    public void setUp() throws Exception {
        Assume.assumeFalse("Skipping test for fastbootd on GKI 1.0",
                sDeviceIsGKI10.get(getTestInformation().getDevice()));

        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
        File file = buildHelper.getTestFile("fuzzy_fastboot", getAbi());
        Assert.assertNotNull(file);
        mFuzzyFastbootPath = file.getAbsolutePath();
        CLog.d("Locate `fuzzy_fastboot` at %s", mFuzzyFastbootPath);
    }

    @AfterClassWithInfo
    public static void tearDownClass(TestInformation testInfo) throws Exception {
        if (!sDeviceIsGKI10.get(testInfo.getDevice())) {
            // Make sure the device state still is FASTBOOTD after the test class have
            // been executed, because fastboot commands in the tests will disrupt
            // device state
            if (!TestDeviceState.FASTBOOTD.equals(testInfo.getDevice().getDeviceState())) {
                ((IManagedTestDevice) testInfo.getDevice())
                        .setDeviceState(TestDeviceState.FASTBOOTD);
            }
            testInfo.getDevice().reboot(); // reboot from fastbootd to adb.
        }
        sDeviceIsGKI10.remove(testInfo.getDevice());
    }

    /* Runs fuzzy_fastboot gtest to verify slot operations in fastbootd implementation. */
    @Ignore("b/146589281")
    @Test
    public void testFastbootdSlotOperations() throws Exception {
        CommandResult result = mRunUtil.runTimedCmd(MAX_CMD_RUN_TIME, mFuzzyFastbootPath,
                String.format(
                        "--serial=%s", getTestInformation().getDevice().getFastbootSerialNumber()),
                "--gtest_filter=Conformance.Slots:Conformance.SetActive");
        Assert.assertEquals(CommandStatus.SUCCESS, result.getStatus());
    }

    /* Runs fuzzy_fastboot to verify getvar commands related to logical partitions. */
    @Test
    public void testLogicalPartitionCommands() throws Exception {
        CommandResult result = mRunUtil.runTimedCmd(MAX_CMD_RUN_TIME, mFuzzyFastbootPath,
                String.format(
                        "--serial=%s", getTestInformation().getDevice().getFastbootSerialNumber()),
                "--gtest_filter=LogicalPartitionCompliance.GetVarIsLogical:LogicalPartitionCompliance.SuperPartition");
        Assert.assertEquals(CommandStatus.SUCCESS, result.getStatus());
    }

    /* Devices launching with DAP must have a super partition named "super". */
    @Test
    public void testSuperPartitionName() throws Exception {
        String superPartitionName =
                getTestInformation().getDevice().getFastbootVariable("super-partition-name");
        Assert.assertEquals("super", superPartitionName);
    }

    /* Runs fuzzy_fastboot to verify the commands to reboot into fastbootd and bootloader. */
    @Test
    public void testFastbootReboot() throws Exception {
        CommandResult result = mRunUtil.runTimedCmd(MAX_CMD_RUN_TIME, mFuzzyFastbootPath,
                String.format(
                        "--serial=%s", getTestInformation().getDevice().getFastbootSerialNumber()),
                "--gtest_filter=LogicalPartitionCompliance.FastbootRebootTest");
        Assert.assertEquals(CommandStatus.SUCCESS, result.getStatus());
    }

    /* Runs fuzzy_fastboot to verify the commands to reboot into fastbootd and bootloader. */
    @Test
    public void testLogicalPartitionFlashing() throws Exception {
        CommandResult result = mRunUtil.runTimedCmd(MAX_CMD_RUN_TIME, mFuzzyFastbootPath,
                String.format(
                        "--serial=%s", getTestInformation().getDevice().getFastbootSerialNumber()),
                "--gtest_filter=LogicalPartitionCompliance.CreateResizeDeleteLP");
        Assert.assertEquals(CommandStatus.SUCCESS, result.getStatus());
    }
}
