/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.tradefed.targetprep;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.host.IHostOptions;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.targetprep.IDeviceFlasher.UserDataFlashOption;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.FuseUtil;
import com.android.tradefed.util.IRunUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/** Unit tests for {@link FastbootDeviceFlasher}. */
@RunWith(JUnit4.class)
public class FastbootDeviceFlasherTest {

    /** a temp 'don't care value' string to use */
    private static final String TEST_STRING = "foo";

    private FastbootDeviceFlasher mFlasher;
    @Mock ITestDevice mMockDevice;
    private IDeviceBuildInfo mMockBuildInfo;
    @Mock IFlashingResourcesRetriever mMockRetriever;
    @Mock IFlashingResourcesParser mMockParser;
    @Mock IRunUtil mMockRunUtil;
    @Mock FuseUtil mMockFuseUtil;
    @Mock IHostOptions mMockHostOptions;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mMockDevice.getSerialNumber()).thenReturn(TEST_STRING);
        when(mMockDevice.getProductType()).thenReturn(TEST_STRING);
        when(mMockDevice.getBuildId()).thenReturn("1");
        when(mMockDevice.getBuildFlavor()).thenReturn("test-debug");
        when(mMockDevice.getDeviceDescriptor()).thenReturn(null);
        mMockBuildInfo = new DeviceBuildInfo("0", TEST_STRING);
        mMockBuildInfo.setDeviceImageFile(new File(TEST_STRING), "0");
        mMockBuildInfo.setUserDataImageFile(new File(TEST_STRING), "0");

        mFlasher =
                new FastbootDeviceFlasher() {
                    @Override
                    protected IFlashingResourcesParser createFlashingResourcesParser(
                            IDeviceBuildInfo localBuild, DeviceDescriptor descriptor) {
                        return mMockParser;
                    }

                    @Override
                    protected IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    protected FuseUtil getFuseUtil() {
                        return mMockFuseUtil;
                    }

                    @Override
                    IHostOptions getHostOptions() {
                        return mMockHostOptions;
                    }
                };
        mFlasher.setFlashingResourcesRetriever(mMockRetriever);
        mFlasher.setUserDataFlashOption(UserDataFlashOption.RETAIN);
    }

    /**
     * Test {@link FastbootDeviceFlasher#flash(ITestDevice, IDeviceBuildInfo)} when device is not
     * available.
     */
    @Test
    public void testFlash_deviceNotAvailable() throws DeviceNotAvailableException {
        try {
            mMockDevice.rebootIntoBootloader();
            // TODO: this is fixed to two arguments - how to set to expect a variable arg amount ?
            doThrow(new DeviceNotAvailableException("test", "serial"))
                    .when(mMockDevice)
                    .executeFastbootCommand((String) Mockito.any(), (String) Mockito.any());

            mFlasher.preFlashOperations(mMockDevice, mMockBuildInfo);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /**
     * Test DeviceFlasher#flash(ITestDevice, IDeviceBuildInfo)} when required board info is not
     * present.
     */
    @Test
    public void testFlash_missingBoard() throws DeviceNotAvailableException {

        when(mMockParser.getRequiredBoards()).thenReturn(null);

        try {
            mFlasher.preFlashOperations(mMockDevice, mMockBuildInfo);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /** Test {@link FastbootDeviceFlasher#getImageVersion(ITestDevice, String)} */
    @Test
    public void testGetImageVersion() throws DeviceNotAvailableException, TargetSetupError {
        CommandResult fastbootResult = new CommandResult();
        fastbootResult.setStatus(CommandStatus.SUCCESS);
        // output of getvar is on stderr for some unknown reason
        fastbootResult.setStderr("version-bootloader: 1.0.1\nfinished. total time: 0.001s");
        fastbootResult.setStdout("");
        when(mMockDevice.executeFastbootCommand("getvar", "version-bootloader"))
                .thenReturn(fastbootResult);

        String actualVersion = mFlasher.getImageVersion(mMockDevice, "bootloader");
        assertEquals("1.0.1", actualVersion);
    }

    /**
     * Test {@link FastbootDeviceFlasher#getCurrentSlot(ITestDevice)} when device is in fastboot.
     */
    @Test
    public void testGetCurrentSlot_fastboot() throws DeviceNotAvailableException, TargetSetupError {
        CommandResult fastbootResult = new CommandResult();
        fastbootResult.setStatus(CommandStatus.SUCCESS);
        fastbootResult.setStderr("current-slot: _a\nfinished. total time 0.001s");
        fastbootResult.setStdout("");
        when(mMockDevice.getDeviceState()).thenReturn(TestDeviceState.FASTBOOT);
        when(mMockDevice.executeFastbootCommand("getvar", "current-slot"))
                .thenReturn(fastbootResult);

        String currentSlot = mFlasher.getCurrentSlot(mMockDevice);
        assertEquals("a", currentSlot);
    }

    /** Test {@link FastbootDeviceFlasher#getCurrentSlot(ITestDevice)} when device is in adb. */
    @Test
    public void testGetCurrentSlot_adb() throws DeviceNotAvailableException, TargetSetupError {
        String adbResult = "[ro.boot.slot_suffix]: [_b]\n";
        when(mMockDevice.getDeviceState()).thenReturn(TestDeviceState.ONLINE);
        when(mMockDevice.executeShellCommand("getprop ro.boot.slot_suffix")).thenReturn(adbResult);

        String currentSlot = mFlasher.getCurrentSlot(mMockDevice);
        assertEquals("b", currentSlot);
    }

    /**
     * Test {@link FastbootDeviceFlasher#getCurrentSlot(ITestDevice)} when device does not support
     * A/B.
     */
    @Test
    public void testGetCurrentSlot_null() throws DeviceNotAvailableException, TargetSetupError {
        String adbResult = "\n";
        when(mMockDevice.getDeviceState()).thenReturn(TestDeviceState.ONLINE);
        when(mMockDevice.executeShellCommand("getprop ro.boot.slot_suffix")).thenReturn(adbResult);

        String currentSlot = mFlasher.getCurrentSlot(mMockDevice);
        assertNull(currentSlot);
    }

    /** Test that a fastboot command is retried if it does not output anything. */
    @Test
    public void testRetryGetVersionCommand() throws DeviceNotAvailableException, TargetSetupError {
        // The first time command is tried, make it return an empty string.
        CommandResult fastbootInValidResult = new CommandResult();
        fastbootInValidResult.setStatus(CommandStatus.SUCCESS);
        // output of getvar is on stderr for some unknown reason
        fastbootInValidResult.setStderr("");
        fastbootInValidResult.setStdout("");

        // Return the correct value on second attempt.
        CommandResult fastbootValidResult = new CommandResult();
        fastbootValidResult.setStatus(CommandStatus.SUCCESS);
        fastbootValidResult.setStderr("version-baseband: 1.0.1\nfinished. total time: 0.001s");
        fastbootValidResult.setStdout("");

        when(mMockDevice.executeFastbootCommand("getvar", "version-baseband"))
                .thenReturn(fastbootInValidResult)
                .thenReturn(fastbootValidResult);

        String actualVersion = mFlasher.getImageVersion(mMockDevice, "baseband");
        verify(mMockRunUtil, times(1)).sleep(Mockito.anyLong());
        assertEquals("1.0.1", actualVersion);
    }

    /** Test that baseband can be flashed when current baseband version is empty */
    @Test
    public void testFlashBaseband_noVersion() throws DeviceNotAvailableException, TargetSetupError {
        final String newBasebandVersion = "1.0.1";
        ITestDevice mockDevice = mock(ITestDevice.class);
        // expect a fastboot getvar version-baseband command
        setFastbootResponseExpectations(mockDevice, "version-baseband: \n");
        setFastbootResponseExpectations(mockDevice, "version-baseband: \n");
        // expect a 'flash radio' command
        setFastbootFlashExpectations(mockDevice, "radio");

        FastbootDeviceFlasher flasher =
                getFlasherWithParserData(
                        String.format("require version-baseband=%s", newBasebandVersion));

        IDeviceBuildInfo build = new DeviceBuildInfo("1234", "build-name");
        build.setBasebandImage(new File("tmp"), newBasebandVersion);
        flasher.checkAndFlashBaseband(mockDevice, build);

        verify(mockDevice).rebootIntoBootloader();
    }

    /**
     * Test flashing of user data with a tests zip
     *
     * @throws TargetSetupError
     */
    @Test
    public void testFlashUserData_testsZip() throws DeviceNotAvailableException, TargetSetupError {
        mFlasher.setUserDataFlashOption(UserDataFlashOption.TESTS_ZIP);

        ITestsZipInstaller mockZipInstaller = mock(ITestsZipInstaller.class);
        mFlasher.setTestsZipInstaller(mockZipInstaller);
        // expect

        // expect

        when(mMockDevice.isEncryptionSupported()).thenReturn(Boolean.FALSE);

        mFlasher.flashUserData(mMockDevice, mMockBuildInfo);

        verify(mockZipInstaller)
                .pushTestsZipOntoData(Mockito.eq(mMockDevice), Mockito.eq(mMockBuildInfo));
        verify(mMockDevice).rebootUntilOnline();
        verify(mMockDevice).rebootIntoBootloader();
    }

    /**
     * Verify that correct fastboot command is called with WIPE data option
     *
     * @throws DeviceNotAvailableException
     * @throws TargetSetupError
     */
    @Test
    public void testFlashUserData_wipe() throws DeviceNotAvailableException, TargetSetupError {
        mFlasher.setUserDataFlashOption(UserDataFlashOption.WIPE);
        doTestFlashWithWipe();
    }

    /**
     * Verify that correct fastboot command is called with FORCE_WIPE data option
     *
     * @throws DeviceNotAvailableException
     * @throws TargetSetupError
     */
    @Test
    public void testFlashUserData_forceWipe() throws DeviceNotAvailableException, TargetSetupError {
        mFlasher.setUserDataFlashOption(UserDataFlashOption.FORCE_WIPE);
        doTestFlashWithWipe();
    }

    /**
     * Verify call sequence when wiping cache on devices with cache partition
     *
     * @throws DeviceNotAvailableException
     * @throws TargetSetupError
     */
    @Test
    public void testWipeCache_exists() throws DeviceNotAvailableException, TargetSetupError {
        mFlasher.setUserDataFlashOption(UserDataFlashOption.WIPE);
        CommandResult fastbootOutput = new CommandResult();
        fastbootOutput.setStatus(CommandStatus.SUCCESS);
        fastbootOutput.setStderr(
                "(bootloader) slot-count: not found\n"
                        + "(bootloader) slot-suffixes: not found\n"
                        + "(bootloader) slot-suffixes: not found\n"
                        + "partition-type:cache: ext4\n"
                        + "finished. total time: 0.002s\n");
        fastbootOutput.setStdout("");

        when(mMockDevice.executeFastbootCommand("getvar", "partition-type:cache"))
                .thenReturn(fastbootOutput);
        when(mMockDevice.getUseFastbootErase()).thenReturn(false);
        fastbootOutput = new CommandResult();
        fastbootOutput.setStatus(CommandStatus.SUCCESS);
        fastbootOutput.setStderr(
                "Creating filesystem with parameters:\n"
                        + "    Size: 104857600\n"
                        + "    Block size: 4096\n"
                        + "    Blocks per group: 32768\n"
                        + "    Inodes per group: 6400\n"
                        + "    Inode size: 256\n"
                        + "    Journal blocks: 1024\n"
                        + "    Label: \n"
                        + "    Blocks: 25600\n"
                        + "    Block groups: 1\n"
                        + "    Reserved block group size: 7\n"
                        + "Created filesystem with 11/6400 inodes and 1438/25600 blocks\n"
                        + "target reported max download size of 494927872 bytes\n"
                        + "erasing 'cache'...\n"
                        + "OKAY [  0.024s]\n"
                        + "sending 'cache' (5752 KB)...\n"
                        + "OKAY [  0.178s]\n"
                        + "writing 'cache'...\n"
                        + "OKAY [  0.107s]\n"
                        + "finished. total time: 0.309s\n");
        when(mMockDevice.fastbootWipePartition("cache")).thenReturn(fastbootOutput);

        mFlasher.wipeCache(mMockDevice);
    }

    /**
     * Verify call sequence when wiping cache on devices without cache partition
     *
     * @throws DeviceNotAvailableException
     * @throws TargetSetupError
     */
    @Test
    public void testWipeCache_not_exists() throws DeviceNotAvailableException, TargetSetupError {
        mFlasher.setUserDataFlashOption(UserDataFlashOption.WIPE);
        CommandResult fastbootOutput = new CommandResult();
        fastbootOutput.setStatus(CommandStatus.SUCCESS);
        fastbootOutput.setStderr(
                "(bootloader) slot-count: not found\n"
                        + "(bootloader) slot-suffixes: not found\n"
                        + "(bootloader) slot-suffixes: not found\n"
                        + "partition-type:cache: \n"
                        + "finished. total time: 0.002s\n");
        fastbootOutput.setStdout("");

        when(mMockDevice.executeFastbootCommand("getvar", "partition-type:cache"))
                .thenReturn(fastbootOutput);

        mFlasher.wipeCache(mMockDevice);
    }

    /**
     * Verify call sequence when wiping cache on devices without cache partition
     *
     * @throws DeviceNotAvailableException
     * @throws TargetSetupError
     */
    @Test
    public void testWipeCache_not_exists_error()
            throws DeviceNotAvailableException, TargetSetupError {
        mFlasher.setUserDataFlashOption(UserDataFlashOption.WIPE);
        CommandResult fastbootOutput = new CommandResult();
        fastbootOutput.setStatus(CommandStatus.SUCCESS);
        fastbootOutput.setStderr(
                "getvar:partition-type:cache FAILED (remote: unknown command)\n"
                        + "finished. total time: 0.051s\n");
        fastbootOutput.setStdout("");

        when(mMockDevice.executeFastbootCommand("getvar", "partition-type:cache"))
                .thenReturn(fastbootOutput);

        mFlasher.wipeCache(mMockDevice);
    }

    /**
     * Convenience function to set expectations for `fastboot -w` and execute test
     *
     * @throws DeviceNotAvailableException
     * @throws TargetSetupError
     */
    private void doTestFlashWithWipe() throws DeviceNotAvailableException, TargetSetupError {
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.SUCCESS);
        result.setStderr("");
        result.setStdout("");
        when(mMockDevice.executeFastbootCommand(Mockito.anyLong(), Mockito.eq("-w")))
                .thenReturn(result);

        mFlasher.handleUserDataFlashing(mMockDevice, mMockBuildInfo);
    }

    /**
     * Test doing a user data with with rm
     *
     * @throws TargetSetupError
     */
    @Test
    public void testFlashUserData_wipeRm() throws DeviceNotAvailableException, TargetSetupError {
        mFlasher.setUserDataFlashOption(UserDataFlashOption.WIPE_RM);

        ITestsZipInstaller mockZipInstaller = mock(ITestsZipInstaller.class);
        mFlasher.setTestsZipInstaller(mockZipInstaller);
        // expect

        // expect

        mFlasher.flashUserData(mMockDevice, mMockBuildInfo);

        verify(mockZipInstaller).deleteData(Mockito.eq(mMockDevice));
        verify(mMockDevice).rebootUntilOnline();
        verify(mMockDevice).rebootIntoBootloader();
    }

    /**
     * Test that {@link FastbootDeviceFlasher#downloadFlashingResources(ITestDevice,
     * IDeviceBuildInfo)} throws an exception when device product is null.
     */
    @Test
    public void testDownloadFlashingResources_nullDeviceProduct() throws Exception {
        mMockDevice = mock(ITestDevice.class);
        when(mMockDevice.getDeviceDescriptor()).thenReturn(null);
        when(mMockDevice.getProductType()).thenReturn(null);
        when(mMockDevice.getSerialNumber()).thenReturn(TEST_STRING);
        when(mMockParser.getRequiredBoards()).thenReturn(new ArrayList<String>());

        try {
            mFlasher.downloadFlashingResources(mMockDevice, mMockBuildInfo);
            fail("Should have thrown an exception");
        } catch (DeviceNotAvailableException expected) {
            assertEquals(
                    String.format("Could not determine product type for device %s", TEST_STRING),
                    expected.getMessage());
        } finally {
        }
    }

    /**
     * Test that {@link FastbootDeviceFlasher#downloadFlashingResources(ITestDevice,
     * IDeviceBuildInfo)} throws an exception when the device product is not found in required
     * board.
     */
    @Test
    public void testDownloadFlashingResources_NotFindBoard() throws Exception {
        final String boardName = "AWESOME_PRODUCT";
        mMockDevice = mock(ITestDevice.class);
        when(mMockDevice.getDeviceDescriptor()).thenReturn(null);
        when(mMockDevice.getProductType()).thenReturn("NOT_FOUND");
        when(mMockDevice.getSerialNumber()).thenReturn(TEST_STRING);
        when(mMockParser.getRequiredBoards()).thenReturn(ArrayUtil.list(boardName));

        try {
            mFlasher.downloadFlashingResources(mMockDevice, mMockBuildInfo);
            fail("Should have thrown an exception");
        } catch (TargetSetupError expected) {
            assertEquals(
                    String.format(
                            "Device %s is NOT_FOUND. Expected %s",
                            TEST_STRING, ArrayUtil.list(boardName)),
                    expected.getMessage());
        } finally {
        }
    }

    /**
     * Test that {@link FastbootDeviceFlasher#downloadFlashingResources(ITestDevice,
     * IDeviceBuildInfo)} proceeds to the end without throwing.
     */
    @Test
    public void testDownloadFlashingResources() throws Exception {
        final String boardName = "AWESOME_PRODUCT";
        mMockDevice = mock(ITestDevice.class);
        when(mMockDevice.getDeviceDescriptor()).thenReturn(null);
        when(mMockDevice.getProductType()).thenReturn(boardName);
        when(mMockDevice.getSerialNumber()).thenReturn(TEST_STRING);
        when(mMockParser.getRequiredBoards()).thenReturn(ArrayUtil.list(boardName));

        mFlasher.downloadFlashingResources(mMockDevice, mMockBuildInfo);
    }

    /**
     * Test that {@link FastbootDeviceFlasher#checkAndFlashBootloader(ITestDevice,
     * IDeviceBuildInfo)} returns false because bootloader version is already good.
     */
    @Test
    public void testCheckAndFlashBootloader_SkippingFlashing() throws Exception {
        final String version = "version 5";
        mFlasher =
                new FastbootDeviceFlasher() {
                    @Override
                    protected String getImageVersion(ITestDevice device, String imageName)
                            throws DeviceNotAvailableException, TargetSetupError {
                        return version;
                    }
                };
        IDeviceBuildInfo mockBuild = mock(IDeviceBuildInfo.class);
        when(mockBuild.getBootloaderVersion()).thenReturn(version);

        assertFalse(mFlasher.checkAndFlashBootloader(mMockDevice, mockBuild));
    }

    /**
     * Test that {@link FastbootDeviceFlasher#checkAndFlashBootloader(ITestDevice,
     * IDeviceBuildInfo)} returns true after flashing the device bootloader.
     */
    @Test
    public void testCheckAndFlashBootloader() throws Exception {
        final String version = "version 5";
        mFlasher =
                new FastbootDeviceFlasher() {
                    @Override
                    protected String getImageVersion(ITestDevice device, String imageName)
                            throws DeviceNotAvailableException, TargetSetupError {
                        return "version 6";
                    }
                };
        IDeviceBuildInfo mockBuild = mock(IDeviceBuildInfo.class);
        when(mockBuild.getBootloaderVersion()).thenReturn(version);
        File bootloaderFake = FileUtil.createTempFile("fakeBootloader", "");
        try {
            when(mockBuild.getBootloaderImageFile()).thenReturn(bootloaderFake);
            CommandResult res = new CommandResult(CommandStatus.SUCCESS);
            res.setStderr("flashing");
            when(mMockDevice.executeFastbootCommand(
                            Mockito.eq("flash"),
                            Mockito.eq("bootloader"),
                            Mockito.eq(bootloaderFake.getAbsolutePath())))
                    .thenReturn(res);

            assertTrue(mFlasher.checkAndFlashBootloader(mMockDevice, mockBuild));
            verify(mMockDevice, times(1)).rebootIntoBootloader();
        } finally {
            FileUtil.deleteFile(bootloaderFake);
        }
    }

    /**
     * Test {@link FastbootDeviceFlasher#checkAndFlashSystem(ITestDevice, String, String,
     * IDeviceBuildInfo)} when there is no need to flash the system.
     */
    @Test
    public void testCheckAndFlashSystem_noFlashing() throws Exception {
        final String buildId = "systemBuildId";
        final String buildFlavor = "systemBuildFlavor";
        IDeviceBuildInfo mockBuild = mock(IDeviceBuildInfo.class);
        when(mockBuild.getDeviceBuildId()).thenReturn(buildId);
        when(mockBuild.getBuildFlavor()).thenReturn(buildFlavor);
        when(mockBuild.getDeviceSerial()).thenReturn("serial");

        assertFalse(mFlasher.checkAndFlashSystem(mMockDevice, buildId, buildFlavor, mockBuild));
        verify(mMockDevice, times(1)).rebootUntilOnline();
        assertNull(
                "system flash status should be null when partitions are not flashed",
                mFlasher.getSystemFlashingStatus());
    }

    /**
     * Test {@link FastbootDeviceFlasher#checkAndFlashSystem(ITestDevice, String, String,
     * IDeviceBuildInfo)} when it needs to be flashed.
     */
    @Test
    public void testCheckAndFlashSystem_flashing() throws Exception {
        final String buildId = "systemBuildId";
        IDeviceBuildInfo mockBuild = mock(IDeviceBuildInfo.class);
        when(mockBuild.getDeviceBuildId()).thenReturn(buildId);
        File deviceImage = FileUtil.createTempFile("fakeDeviceImage", "");
        try {
            when(mockBuild.getDeviceImageFile()).thenReturn(deviceImage);
            CommandResult res = new CommandResult(CommandStatus.SUCCESS);
            res.setStderr("flashing");
            when(mMockDevice.executeLongFastbootCommand(
                            Mockito.anyMap(),
                            Mockito.eq("update"),
                            Mockito.eq(deviceImage.getAbsolutePath())))
                    .thenReturn(res);

            assertTrue(mFlasher.checkAndFlashSystem(mMockDevice, buildId, null, mockBuild));

            assertEquals(
                    "system flashing status should be \"SUCCESS\"",
                    CommandStatus.SUCCESS,
                    mFlasher.getSystemFlashingStatus());
        } finally {
            FileUtil.deleteFile(deviceImage);
        }
    }

    /**
     * Test {@link FastbootDeviceFlasher#checkAndFlashSystem(ITestDevice, String, String,
     * IDeviceBuildInfo)} with fuse-zip to mount device image and flash with fastboot flashall.
     */
    @Test
    public void testCheckAndFlashSystemWithFlashall() throws Exception {
        final String buildId = "systemBuildId";
        IDeviceBuildInfo mockBuild = mock(IDeviceBuildInfo.class);
        when(mockBuild.getDeviceBuildId()).thenReturn(buildId);
        File deviceImage = FileUtil.createTempFile("fakeDeviceImage", "");
        when(mMockHostOptions.shouldFlashWithFuseZip()).thenReturn(true);
        when(mMockFuseUtil.canMountZip()).thenReturn(true);
        try {
            when(mockBuild.getDeviceImageFile()).thenReturn(deviceImage);
            CommandResult res = new CommandResult(CommandStatus.SUCCESS);
            res.setStderr("flashing");
            Mockito.doAnswer(
                            new Answer<Object>() {
                                @Override
                                public Object answer(InvocationOnMock mock) throws Throwable {
                                    Map<String, String> envVarMap = mock.getArgument(0);
                                    String cmd = mock.getArgument(1);
                                    assertTrue(envVarMap.containsKey("ANDROID_PRODUCT_OUT"));
                                    assertEquals("flashall", cmd);
                                    return res;
                                }
                            })
                    .when(mMockDevice)
                    .executeLongFastbootCommand(Mockito.anyMap(), Mockito.anyString());

            doNothing()
                    .when(mMockFuseUtil)
                    .mountZip(Mockito.eq(deviceImage.getAbsoluteFile()), isA(File.class));

            assertTrue(mFlasher.checkAndFlashSystem(mMockDevice, buildId, null, mockBuild));

            assertEquals(
                    "system flashing status should be \"SUCCESS\"",
                    CommandStatus.SUCCESS,
                    mFlasher.getSystemFlashingStatus());
        } finally {
            FileUtil.deleteFile(deviceImage);
        }
    }

    /**
     * Test the fastboot flashing with ramdisk interaction flow
     *
     * @throws Exception
     */
    @Test
    public void testFlashingSystemWithRamdisk() throws Exception {
        final String buildId = "systemBuildId";
        mFlasher.setShouldFlashRamdisk(true);
        mFlasher.setRamdiskPartition("boot");
        IDeviceBuildInfo mockBuild = mock(IDeviceBuildInfo.class);
        when(mockBuild.getDeviceBuildId()).thenReturn(buildId);
        File deviceImage = FileUtil.createTempFile("fakeDeviceImage", "");
        File ramdisk = FileUtil.createTempFile("fakeRamdisk", "");
        when(mockBuild.getRamdiskFile()).thenReturn(ramdisk);
        try {
            when(mockBuild.getDeviceImageFile()).thenReturn(deviceImage);
            CommandResult res = new CommandResult(CommandStatus.SUCCESS);
            res.setStderr("flashing");
            when(mMockDevice.executeLongFastbootCommand(
                            Mockito.anyMap(),
                            Mockito.eq("--skip-reboot"),
                            Mockito.eq("update"),
                            Mockito.eq(deviceImage.getAbsolutePath())))
                    .thenReturn(res);

            when(mMockDevice.executeLongFastbootCommand(
                            Mockito.anyMap(),
                            Mockito.eq("flash"),
                            Mockito.eq("boot"),
                            Mockito.eq(ramdisk.getAbsolutePath())))
                    .thenReturn(res);

            assertTrue(mFlasher.checkAndFlashSystem(mMockDevice, buildId, null, mockBuild));
            verify(mMockDevice, times(1)).reboot();
            verify(mMockDevice).rebootIntoBootloader();
            assertEquals(
                    "system flashing status should be \"SUCCESS\"",
                    CommandStatus.SUCCESS,
                    mFlasher.getSystemFlashingStatus());
        } finally {
            FileUtil.deleteFile(deviceImage);
            FileUtil.deleteFile(ramdisk);
        }
    }

    /**
     * Test that ramdisk is still flashed even system partition flashing is skipped
     *
     * @throws Exception
     */
    @Test
    public void testSkipFlashingSystemWithRamdisk() throws Exception {
        final String buildId = "systemBuildId";
        final String buildFlavor = "systemBuildFlavor";
        mFlasher.setShouldFlashRamdisk(true);
        mFlasher.setRamdiskPartition("vendor_boot");
        IDeviceBuildInfo mockBuild = mock(IDeviceBuildInfo.class);
        File ramdisk = FileUtil.createTempFile("fakeRamdisk", "");
        when(mockBuild.getRamdiskFile()).thenReturn(ramdisk);
        try {
            when(mockBuild.getDeviceBuildId()).thenReturn(buildId);
            when(mockBuild.getBuildFlavor()).thenReturn(buildFlavor);
            when(mockBuild.getDeviceSerial()).thenReturn("serial");

            CommandResult res = new CommandResult(CommandStatus.SUCCESS);
            res.setStderr("flashing");
            when(mMockDevice.executeLongFastbootCommand(
                            Mockito.anyMap(),
                            Mockito.eq("flash"),
                            Mockito.eq("vendor_boot"),
                            Mockito.eq(ramdisk.getAbsolutePath())))
                    .thenReturn(res);

            assertFalse(mFlasher.checkAndFlashSystem(mMockDevice, buildId, buildFlavor, mockBuild));
            verify(mMockDevice, times(1)).rebootUntilOnline();
            verify(mMockDevice, times(1)).reboot();
            verify(mMockDevice).rebootIntoBootloader();
            assertNull(
                    "system flash status should be null when partitions are not flashed",
                    mFlasher.getSystemFlashingStatus());
        } finally {
            FileUtil.deleteFile(ramdisk);
        }
    }

    /**
     * Test {@link FastbootDeviceFlasher#checkAndFlashSystem(ITestDevice, String, String,
     * IDeviceBuildInfo)} with flash options
     */
    @Test
    public void testCheckAndFlashSystem_withFlashOptions() throws Exception {
        mFlasher.setFlashOptions(Arrays.asList("--foo", " --bar"));
        final String buildId = "systemBuildId";
        IDeviceBuildInfo mockBuild = mock(IDeviceBuildInfo.class);
        when(mockBuild.getDeviceBuildId()).thenReturn(buildId);
        File deviceImage = FileUtil.createTempFile("fakeDeviceImage", "");
        try {
            when(mockBuild.getDeviceImageFile()).thenReturn(deviceImage);
            CommandResult res = new CommandResult(CommandStatus.SUCCESS);
            res.setStderr("flashing");
            when(mMockDevice.executeLongFastbootCommand(
                            Mockito.anyMap(),
                            Mockito.eq("--foo"),
                            Mockito.eq("--bar"),
                            Mockito.eq("update"),
                            Mockito.eq(deviceImage.getAbsolutePath())))
                    .thenReturn(res);

            assertTrue(mFlasher.checkAndFlashSystem(mMockDevice, buildId, null, mockBuild));

            assertEquals(
                    "system flashing status should be \"SUCCESS\"",
                    CommandStatus.SUCCESS,
                    mFlasher.getSystemFlashingStatus());
        } finally {
            FileUtil.deleteFile(deviceImage);
        }
    }

    /**
     * Test {@link FastbootDeviceFlasher#checkAndFlashSystem(ITestDevice, String, String,
     * IDeviceBuildInfo)} when it needs to be flashed but throws an exception.
     */
    @Test
    public void testCheckAndFlashSystem_exception() throws Exception {
        final String buildId = "systemBuildId";
        IDeviceBuildInfo mockBuild = mock(IDeviceBuildInfo.class);
        when(mockBuild.getDeviceBuildId()).thenReturn(buildId);
        File deviceImage = FileUtil.createTempFile("fakeDeviceImage", "");
        try {
            when(mockBuild.getDeviceImageFile()).thenReturn(deviceImage);
            CommandResult res = new CommandResult(CommandStatus.SUCCESS);
            res.setStderr("flashing");
            when(mMockDevice.executeLongFastbootCommand(
                            Mockito.anyMap(),
                            Mockito.eq("update"),
                            Mockito.eq(deviceImage.getAbsolutePath())))
                    .thenThrow(new DeviceNotAvailableException("test", "serial"));

            try {
                mFlasher.checkAndFlashSystem(mMockDevice, buildId, null, mockBuild);
                fail("Expected DeviceNotAvailableException not thrown");
            } catch (DeviceNotAvailableException dnae) {
                // expected

                assertEquals(
                        "system flashing status should be \"EXCEPTION\"",
                        CommandStatus.EXCEPTION,
                        mFlasher.getSystemFlashingStatus());
            }
        } finally {
            FileUtil.deleteFile(deviceImage);
        }
    }

    /**
     * Test {@link FastbootDeviceFlasher#handleFastbootResult(ITestDevice, CommandResult,
     * String...)}.
     */
    @Test
    public void testHandleFastbootResult() throws Exception {
        CommandResult res = new CommandResult(CommandStatus.SUCCESS);
        res.setStderr("");
        res.setStdout("");
        assertEquals("", mFlasher.handleFastbootResult(mMockDevice, res, "update"));
    }

    /**
     * Test {@link FastbootDeviceFlasher#handleFastbootResult(ITestDevice, CommandResult,
     * String...)} when fastboot failed.
     */
    @Test
    public void testHandleFastbootResult_fastbootFailed() throws Exception {
        CommandResult res = new CommandResult(CommandStatus.SUCCESS);
        res.setStderr("FAILED to flash device.");
        res.setStdout("");

        try {
            mFlasher.handleFastbootResult(mMockDevice, res, "update");
            fail("Expected TargetSetupError not thrown.");
        } catch (TargetSetupError e) {
            assertEquals(DeviceErrorIdentifier.ERROR_AFTER_FLASHING, e.getErrorId());
        }
    }

    /**
     * Test {@link FastbootDeviceFlasher#handleFastbootResult(ITestDevice, CommandResult,
     * String...)} when no disk space.
     */
    @Test
    public void testHandleFastbootResult_noDiskSpace() throws Exception {
        CommandResult res = new CommandResult(CommandStatus.FAILED);
        res.setStderr(
                "fastboot: error: failed to create temporary file for"
                    + " /fastboot-ramdisk/fastboot_userdata_AxRPjp with template boot.img: No such"
                    + " file or directory");
        res.setStdout("");

        try {
            mFlasher.handleFastbootResult(mMockDevice, res, "update");
            fail("Expected TargetSetupError not thrown.");
        } catch (TargetSetupError e) {
            assertEquals(InfraErrorIdentifier.NO_DISK_SPACE, e.getErrorId());
        }
    }

    /**
     * Set EasyMock expectations to simulate the response to some fastboot command
     *
     * @param mockDevice the EasyMock mock {@link ITestDevice} to configure
     * @param response the fastboot command response to inject
     */
    private static void setFastbootResponseExpectations(ITestDevice mockDevice, String response)
            throws DeviceNotAvailableException {
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.SUCCESS);
        result.setStderr(response);
        result.setStdout("");
        when(mockDevice.executeFastbootCommand((String) Mockito.any(), (String) Mockito.any()))
                .thenReturn(result);
    }

    /**
     * Set EasyMock expectations to simulate the response to a fastboot flash command
     *
     * @param image the expected image name to flash
     * @param mockDevice the EasyMock mock {@link ITestDevice} to configure
     */
    private static void setFastbootFlashExpectations(ITestDevice mockDevice, String image)
            throws DeviceNotAvailableException {
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.SUCCESS);
        result.setStderr("success");
        result.setStdout("");
        when(mockDevice.executeLongFastbootCommand(
                        Mockito.anyMap(),
                        Mockito.eq("flash"),
                        Mockito.eq(image),
                        (String) Mockito.any()))
                .thenReturn(result);
    }

    private FastbootDeviceFlasher getFlasherWithParserData(final String androidInfoData) {
        FastbootDeviceFlasher flasher =
                new FastbootDeviceFlasher() {
                    @Override
                    protected IFlashingResourcesParser createFlashingResourcesParser(
                            IDeviceBuildInfo localBuild, DeviceDescriptor desc)
                            throws TargetSetupError {
                        BufferedReader reader =
                                new BufferedReader(new StringReader(androidInfoData));
                        try {
                            return new FlashingResourcesParser(reader);
                        } catch (IOException e) {
                            return null;
                        }
                    }

                    @Override
                    protected void flashBootloader(ITestDevice device, File bootloaderImageFile)
                            throws DeviceNotAvailableException, TargetSetupError {
                        throw new DeviceNotAvailableException("error", "fakeserial");
                    }
                };
        flasher.setFlashingResourcesRetriever(mock(IFlashingResourcesRetriever.class));
        return flasher;
    }
}
