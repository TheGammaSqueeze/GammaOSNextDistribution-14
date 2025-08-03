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

package com.android.tradefed.device;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.ITestInformationReceiver;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;

/**
 * Functional tests for creating Microdroid {@link TestDevice}.
 *
 * <p>Requires a physical device to be connected.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class MicrodroidFuncTest implements IDeviceTest, ITestInformationReceiver {
    private static final String APK_NAME = "MicrodroidTestApp.apk";
    private static final String CONFIG_PATH = "assets/vm_config.json"; // path inside the APK

    private TestDevice mTestDevice;
    private TestInformation mTestInformation;

    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = (TestDevice) device;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    @Override
    public void setTestInformation(TestInformation testInformation) {
        mTestInformation = testInformation;
    }

    @Override
    public TestInformation getTestInformation() {
        return mTestInformation;
    }

    @Before
    public void setup() throws Exception {
        mTestDevice.waitForDeviceAvailable();
        assumeTrue(mTestDevice.supportsMicrodroid());
        mTestDevice.executeShellV2Command("logcat -c");
    }

    // tests if a microdroid TestDevice is created and stopped properly
    @Test
    public void testStartAndShutdownMicrodroid()
            throws DeviceNotAvailableException, FileNotFoundException {
        final ITestDevice microdroid =
                TestDevice.MicrodroidBuilder.fromFile(
                                findTestFile(mTestInformation, APK_NAME), CONFIG_PATH)
                        .debugLevel("full")
                        .memoryMib(0)
                        .numCpus(1)
                        .build(mTestDevice);
        assertNotNull(microdroid);

        MicrodroidHelper microdroidHelper = new MicrodroidHelper();

        // Test writing to /data partition
        runOnDevice(microdroid, "echo MicrodroidTest > /data/local/tmp/test.txt");
        assertThat(runOnDevice(microdroid, "cat /data/local/tmp/test.txt"), is("MicrodroidTest"));

        // Check if the APK & its idsig partitions exist
        final String apkPartition = "/dev/block/by-name/microdroid-apk";
        assertTrue(microdroid.doesFileExist(apkPartition));
        final String apkIdsigPartition = "/dev/block/by-name/microdroid-apk-idsig";
        assertTrue(microdroid.doesFileExist(apkIdsigPartition));
        // Check the vm-instance partition as well
        final String vmInstancePartition = "/dev/block/by-name/vm-instance";
        assertTrue(microdroid.doesFileExist(vmInstancePartition));

        // Check if the native library in the APK is has correct filesystem info
        final String[] abis =
                runOnDevice(microdroid, "getprop", "ro.product.cpu.abilist").split(",");
        assertThat(abis.length, is(1));
        final String testLib = "/mnt/apk/lib/" + abis[0] + "/MicrodroidTestNativeLib.so";
        final String label = "u:object_r:system_file:s0";
        assertThat(runOnDevice(microdroid, "ls", "-Z", testLib), is(label + " " + testLib));

        // Check if the command in vm_config.json was executed by examining the side effect of the
        // command
        assertThat(runOnDevice(microdroid, "getprop", "debug.microdroid.app.run"), is("true"));
        assertThat(
                runOnDevice(microdroid, "getprop", "debug.microdroid.app.sublib.run"), is("true"));

        // Check that no denials have happened so far
        assertThat(runOnDevice(microdroid, "logcat -d -e 'avc:[[:space:]]{1,2}denied'"), is(""));
        assertThat(
                runOnDevice(microdroid, "cat /proc/cpuinfo | grep processor | wc -l"),
                is(Integer.toString(1)));

        assertThat(
                microdroidHelper.runOnMicrodroid(microdroid.getSerialNumber(), "echo true"),
                is("true"));
        mTestDevice.shutdownMicrodroid(microdroid);
        assertNull(microdroidHelper.tryRunOnMicrodroid(microdroid.getSerialNumber(), "echo true"));
    }

    private String runOnDevice(ITestDevice device, String... cmd)
            throws DeviceNotAvailableException {
        CommandResult result = device.executeShellV2Command(join(cmd));
        if (result.getStatus() != CommandStatus.SUCCESS) {
            fail(join(cmd) + " has failed: " + result);
        }
        return result.getStdout().trim();
    }

    private static String join(String... strs) {
        return String.join(" ", Arrays.asList(strs));
    }

    private static File findTestFile(TestInformation testInformation, String name)
            throws FileNotFoundException {
        return testInformation.getDependencyFile(name, false);
    }
}
