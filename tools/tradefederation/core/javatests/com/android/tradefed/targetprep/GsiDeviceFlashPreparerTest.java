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

package com.android.tradefed.targetprep;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.host.HostOptions;
import com.android.tradefed.host.IHostOptions;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.ZipUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.List;

/** Unit tests for {@link GsiDeviceFlashPreparer}. */
@RunWith(JUnit4.class)
public class GsiDeviceFlashPreparerTest {

    private GsiDeviceFlashPreparer mPreparer;
    @Mock ITestDevice mMockDevice;
    private IDeviceBuildInfo mBuildInfo;
    private File mTmpDir;
    private TestInformation mTestInfo;
    private CommandResult mSuccessResult;
    private CommandResult mFailureResult;
    @Mock IRunUtil mMockRunUtil;
    private DeviceDescriptor mDeviceDescriptor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mDeviceDescriptor =
                new DeviceDescriptor(
                        "serial_1",
                        false,
                        DeviceAllocationState.Available,
                        "unknown",
                        "unknown",
                        "unknown",
                        "unknown",
                        "unknown");

        when(mMockDevice.getSerialNumber()).thenReturn("serial_1");
        when(mMockDevice.getDeviceDescriptor()).thenReturn(mDeviceDescriptor);
        when(mMockDevice.getOptions()).thenReturn(new TestDeviceOptions());

        mPreparer =
                new GsiDeviceFlashPreparer() {
                    @Override
                    protected IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    protected IHostOptions getHostOptions() {
                        return new HostOptions();
                    }
                };
        // Reset default settings
        mTmpDir = FileUtil.createTempDir("tmp");
        mBuildInfo = new DeviceBuildInfo("0", "");
        mBuildInfo.setBuildFlavor("flavor");
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockDevice);
        context.addDeviceBuildInfo("device", mBuildInfo);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
        mSuccessResult = new CommandResult(CommandStatus.SUCCESS);
        mSuccessResult.setStderr("OKAY [  0.043s]");
        mSuccessResult.setStdout("");
        mFailureResult = new CommandResult(CommandStatus.FAILED);
        mFailureResult.setStderr("FAILED (remote: 'Partition error')");
        mFailureResult.setStdout("");
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.recursiveDelete(mTmpDir);
    }

    /** Set EasyMock expectations for a normal setup call */
    private void doSetupExpectations() throws Exception {
        when(mMockDevice.enableAdbRoot()).thenReturn(Boolean.TRUE);
    }

    private void verifyExpectations() throws Exception {
        verify(mMockRunUtil).sleep(Mockito.anyLong());
        verify(mMockDevice).rebootUntilOnline();
        verify(mMockDevice).setDate(null);
        verify(mMockDevice).waitForDeviceAvailable(Mockito.anyLong());
        verify(mMockDevice).setRecoveryMode(RecoveryMode.AVAILABLE);
        verify(mMockDevice).postBootSetup();
    }

    /** Set EasyMock expectations for getting current slot */
    private void doGetSlotExpectation() throws Exception {
        CommandResult fastbootResult = new CommandResult();
        fastbootResult.setStatus(CommandStatus.SUCCESS);
        fastbootResult.setStderr("current-slot: _a\nfinished. total time 0.001s");
        fastbootResult.setStdout("");
        when(mMockDevice.executeLongFastbootCommand("getvar", "current-slot"))
                .thenReturn(fastbootResult);
    }

    /** Set EasyMock expectations for no current slot */
    private void doGetEmptySlotExpectation() throws Exception {
        CommandResult fastbootResult = new CommandResult();
        fastbootResult.setStatus(CommandStatus.SUCCESS);
        fastbootResult.setStderr("current-slot: \nfinished. total time 0.001s");
        fastbootResult.setStdout("");
        when(mMockDevice.executeLongFastbootCommand("getvar", "current-slot"))
                .thenReturn(fastbootResult);
    }

    /* Verifies that setUp will throw TargetSetupError if there is no gki boot.img */
    @Test
    public void testSetUp_NoGsiImg() throws Exception {

        try {
            mPreparer.setUp(mTestInfo);
            fail("TargetSetupError is expected");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /* Verifies that setUp will throw exception when there is no system.img in the zip file */
    @Test
    public void testSetUp_NoSystemImageInGsiZip() throws Exception {
        File gsiDir = FileUtil.createTempDir("gsi_folder", mTmpDir);
        File tmpFile = new File(gsiDir, "test");
        File gsiZip = FileUtil.createTempFile("gsi_image", ".zip", mTmpDir);
        ZipUtil.createZip(List.of(tmpFile), gsiZip);
        mBuildInfo.setFile("gsi_system.img", gsiZip, "0");

        try {
            mPreparer.setUp(mTestInfo);
            fail("TargetSetupError is expected");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /* Verifies that setUp can pass when there is no vbmeta.img is provided*/
    @Test
    public void testSetUp_Success_NoVbmetaImage() throws Exception {
        File gsiDir = FileUtil.createTempDir("gsi_folder", mTmpDir);
        File systemImg = new File(gsiDir, "system.img");
        FileUtil.writeToFile("ddd", systemImg);
        mBuildInfo.setFile("gsi_system.img", systemImg, "0");

        when(mMockDevice.getApiLevel()).thenReturn(29);

        doGetSlotExpectation();
        when(mMockDevice.executeLongFastbootCommand("delete-logical-partition", "product_a"))
                .thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand("-w")).thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand("erase", "system_a"))
                .thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand(
                        "flash", "system", mBuildInfo.getFile("gsi_system.img").getAbsolutePath()))
                .thenReturn(mSuccessResult);

        doSetupExpectations();

        mPreparer.setUp(mTestInfo);

        verifyExpectations();
        verify(mMockDevice).waitForDeviceOnline();
        verify(mMockDevice).rebootIntoBootloader();
        verify(mMockRunUtil).allowInterrupt(false);
        verify(mMockDevice).rebootIntoFastbootd();
        verify(mMockRunUtil).allowInterrupt(true);
    }

    /* Verifies that preparer can flash GSI image */
    @Test
    public void testSetup_Success() throws Exception {
        File gsiDir = FileUtil.createTempDir("gsi_folder", mTmpDir);
        File systemImg = new File(gsiDir, "system.img");
        File vbmetaImg = new File(gsiDir, "vbmeta.img");
        FileUtil.writeToFile("ddd", systemImg);
        FileUtil.writeToFile("ddd", vbmetaImg);
        mBuildInfo.setFile("gsi_system.img", systemImg, "0");
        mBuildInfo.setFile("gsi_vbmeta.img", vbmetaImg, "0");

        when(mMockDevice.getApiLevel()).thenReturn(29);

        when(mMockDevice.executeLongFastbootCommand(
                        "--disable-verification",
                        "flash",
                        "vbmeta",
                        mBuildInfo.getFile("gsi_vbmeta.img").getAbsolutePath()))
                .thenReturn(mSuccessResult);

        doGetSlotExpectation();
        when(mMockDevice.executeLongFastbootCommand("delete-logical-partition", "product_a"))
                .thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand("-w")).thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand("erase", "system_a"))
                .thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand(
                        "flash", "system", mBuildInfo.getFile("gsi_system.img").getAbsolutePath()))
                .thenReturn(mSuccessResult);

        doSetupExpectations();

        mPreparer.setUp(mTestInfo);

        verifyExpectations();
        verify(mMockDevice).waitForDeviceOnline();
        verify(mMockDevice).rebootIntoBootloader();
        verify(mMockRunUtil).allowInterrupt(false);
        verify(mMockDevice).rebootIntoFastbootd();
        verify(mMockRunUtil).allowInterrupt(true);
    }

    /* Verifies that preparer can flash GSI image from Zip file*/
    @Test
    public void testSetup_Success_FromZipFile() throws Exception {
        File gsiDir = FileUtil.createTempDir("gsi_folder", mTmpDir);
        File systemImg = new File(gsiDir, "system.img");
        File vbmetaImg = new File(gsiDir, "vbmeta.img");
        FileUtil.writeToFile("ddd", systemImg);
        FileUtil.writeToFile("ddd", vbmetaImg);
        File gsiZip = FileUtil.createTempFile("gsi_image", ".zip", mTmpDir);
        ZipUtil.createZip(List.of(systemImg, vbmetaImg), gsiZip);
        mBuildInfo.setFile("gsi_system.img", gsiZip, "0");
        mBuildInfo.setFile("gsi_vbmeta.img", gsiZip, "0");

        when(mMockDevice.getApiLevel()).thenReturn(29);

        when(mMockDevice.executeLongFastbootCommand(
                        Mockito.eq("--disable-verification"),
                        Mockito.eq("flash"),
                        Mockito.eq("vbmeta"),
                        Mockito.matches(".*vbmeta.img")))
                .thenReturn(mSuccessResult);

        doGetSlotExpectation();
        when(mMockDevice.executeLongFastbootCommand("delete-logical-partition", "product_a"))
                .thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand("-w")).thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand("erase", "system_a"))
                .thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand(
                        Mockito.eq("flash"), Mockito.eq("system"), Mockito.matches(".*system.img")))
                .thenReturn(mSuccessResult);

        doSetupExpectations();

        mPreparer.setUp(mTestInfo);

        verifyExpectations();
        verify(mMockDevice).waitForDeviceOnline();
        verify(mMockDevice).rebootIntoBootloader();
        verify(mMockRunUtil).allowInterrupt(false);
        verify(mMockDevice).rebootIntoFastbootd();
        verify(mMockRunUtil).allowInterrupt(true);
    }

    /* Verifies that preparer can flash GSI and GKI image */
    @Test
    public void testSetup_Success_WithGkiBootImg() throws Exception {
        File gsiDir = FileUtil.createTempDir("gsi_folder", mTmpDir);
        File systemImg = new File(gsiDir, "system.img");
        File vbmetaImg = new File(gsiDir, "vbmeta.img");
        File bootImg = new File(gsiDir, "boot-5.4.img");
        FileUtil.writeToFile("ddd", systemImg);
        FileUtil.writeToFile("ddd", vbmetaImg);
        FileUtil.writeToFile("ddd", bootImg);
        mBuildInfo.setFile("gsi_system.img", systemImg, "0");
        mBuildInfo.setFile("gsi_vbmeta.img", vbmetaImg, "0");
        mBuildInfo.setFile("gki_boot.img", bootImg, "0");

        when(mMockDevice.getApiLevel()).thenReturn(29);

        when(mMockDevice.executeLongFastbootCommand(
                        "--disable-verification",
                        "flash",
                        "vbmeta",
                        mBuildInfo.getFile("gsi_vbmeta.img").getAbsolutePath()))
                .thenReturn(mSuccessResult);

        doGetSlotExpectation();
        when(mMockDevice.executeLongFastbootCommand("delete-logical-partition", "product_a"))
                .thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand("-w")).thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand("erase", "system_a"))
                .thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand(
                        "flash", "system", mBuildInfo.getFile("gsi_system.img").getAbsolutePath()))
                .thenReturn(mSuccessResult);

        when(mMockDevice.executeLongFastbootCommand(
                        "flash", "boot", mBuildInfo.getFile("gki_boot.img").getAbsolutePath()))
                .thenReturn(mSuccessResult);

        doSetupExpectations();

        mPreparer.setUp(mTestInfo);

        verifyExpectations();
        InOrder inOrder = Mockito.inOrder(mMockDevice, mMockRunUtil);
        inOrder.verify(mMockDevice).waitForDeviceOnline();
        inOrder.verify(mMockDevice).rebootIntoBootloader();
        inOrder.verify(mMockRunUtil).allowInterrupt(false);
        inOrder.verify(mMockDevice).rebootIntoFastbootd();
        inOrder.verify(mMockDevice).rebootIntoBootloader();
        inOrder.verify(mMockRunUtil).allowInterrupt(true);
    }

    /* Verifies that preparer can flash GSI and GKI image from a Zip file*/
    @Test
    public void testSetup_Success_WithGkiBootImgInZip() throws Exception {
        File gsiDir = FileUtil.createTempDir("gsi_folder", mTmpDir);
        File systemImg = new File(gsiDir, "system.img");
        File vbmetaImg = new File(gsiDir, "vbmeta.img");
        File bootImg = new File(gsiDir, "boot-5.4.img");
        FileUtil.writeToFile("ddd", systemImg);
        FileUtil.writeToFile("ddd", vbmetaImg);
        FileUtil.writeToFile("ddd", bootImg);
        File gsiZip = FileUtil.createTempFile("gsi_image", ".zip", mTmpDir);
        ZipUtil.createZip(List.of(systemImg, vbmetaImg, bootImg), gsiZip);
        mBuildInfo.setFile("gsi_system.img", gsiZip, "0");
        mBuildInfo.setFile("gsi_vbmeta.img", gsiZip, "0");
        mBuildInfo.setFile("gki_boot.img", gsiZip, "0");

        when(mMockDevice.getApiLevel()).thenReturn(29);

        when(mMockDevice.executeLongFastbootCommand(
                        Mockito.eq("--disable-verification"),
                        Mockito.eq("flash"),
                        Mockito.eq("vbmeta"),
                        Mockito.matches(".*/vbmeta.img")))
                .thenReturn(mSuccessResult);

        doGetSlotExpectation();
        when(mMockDevice.executeLongFastbootCommand("delete-logical-partition", "product_a"))
                .thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand("-w")).thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand("erase", "system_a"))
                .thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand(
                        Mockito.eq("flash"),
                        Mockito.eq("system"),
                        Mockito.matches(".*/system.img")))
                .thenReturn(mSuccessResult);

        when(mMockDevice.executeLongFastbootCommand(
                        Mockito.eq("flash"),
                        Mockito.eq("boot"),
                        Mockito.matches(".*/boot-5.4.img")))
                .thenReturn(mSuccessResult);

        doSetupExpectations();

        mPreparer.setUp(mTestInfo);

        verifyExpectations();
        InOrder inOrder = Mockito.inOrder(mMockDevice, mMockRunUtil);
        inOrder.verify(mMockDevice).waitForDeviceOnline();
        inOrder.verify(mMockDevice).rebootIntoBootloader();
        inOrder.verify(mMockRunUtil).allowInterrupt(false);
        inOrder.verify(mMockDevice).rebootIntoFastbootd();
        inOrder.verify(mMockDevice).rebootIntoBootloader();
        inOrder.verify(mMockRunUtil).allowInterrupt(true);
    }

    /* Verifies that preparer can flash GSI image on Android 9 device*/
    @Test
    public void testSetup_Success_Api28() throws Exception {
        File gsiDir = FileUtil.createTempDir("gsi_folder", mTmpDir);
        File systemImg = new File(gsiDir, "system.img");
        File vbmetaImg = new File(gsiDir, "vbmeta.img");
        FileUtil.writeToFile("ddd", systemImg);
        FileUtil.writeToFile("ddd", vbmetaImg);
        mBuildInfo.setFile("gsi_system.img", systemImg, "0");
        mBuildInfo.setFile("gsi_vbmeta.img", vbmetaImg, "0");

        when(mMockDevice.getApiLevel()).thenReturn(28);

        when(mMockDevice.executeLongFastbootCommand(
                        "--disable-verification",
                        "flash",
                        "vbmeta",
                        mBuildInfo.getFile("gsi_vbmeta.img").getAbsolutePath()))
                .thenReturn(mSuccessResult);
        doGetEmptySlotExpectation();
        when(mMockDevice.executeLongFastbootCommand("-w")).thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand("erase", "system")).thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand(
                        "flash", "system", mBuildInfo.getFile("gsi_system.img").getAbsolutePath()))
                .thenReturn(mSuccessResult);

        doSetupExpectations();

        mPreparer.setUp(mTestInfo);

        verifyExpectations();
        verify(mMockDevice).waitForDeviceOnline();
        verify(mMockDevice).rebootIntoBootloader();
        verify(mMockRunUtil).allowInterrupt(false);
        verify(mMockRunUtil).allowInterrupt(true);
    }

    /* Verifies that preparer will throw TargetSetupError with GSI flash failure*/
    @Test
    public void testSetup_GsiFlashFailure() throws Exception {
        File gsiDir = FileUtil.createTempDir("gsi_folder", mTmpDir);
        File systemImg = new File(gsiDir, "system.img");
        File vbmetaImg = new File(gsiDir, "vbmeta.img");
        FileUtil.writeToFile("ddd", systemImg);
        FileUtil.writeToFile("ddd", vbmetaImg);
        mBuildInfo.setFile("gsi_system.img", systemImg, "0");
        mBuildInfo.setFile("gsi_vbmeta.img", vbmetaImg, "0");

        when(mMockDevice.getApiLevel()).thenReturn(29);

        when(mMockDevice.executeLongFastbootCommand(
                        "--disable-verification",
                        "flash",
                        "vbmeta",
                        mBuildInfo.getFile("gsi_vbmeta.img").getAbsolutePath()))
                .thenReturn(mSuccessResult);

        doGetSlotExpectation();
        when(mMockDevice.executeLongFastbootCommand("delete-logical-partition", "product_a"))
                .thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand("-w")).thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand("erase", "system_a"))
                .thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand(
                        "flash", "system", mBuildInfo.getFile("gsi_system.img").getAbsolutePath()))
                .thenReturn(mFailureResult);

        try {
            mPreparer.setUp(mTestInfo);
            fail("Expect to get TargetSetupError from setUp");
        } catch (TargetSetupError e) {
            // expected
        }

        verify(mMockDevice).waitForDeviceOnline();
        verify(mMockDevice).rebootIntoBootloader();
        verify(mMockRunUtil).allowInterrupt(false);
        verify(mMockDevice).rebootIntoFastbootd();
        verify(mMockRunUtil).allowInterrupt(true);
    }

    /* Verifies that preparer will throw DeviceNotAvailableException if device fails to boot up */
    @Test
    public void testSetUp_BootFailure() throws Exception {
        File gsiDir = FileUtil.createTempDir("gsi_folder", mTmpDir);
        File systemImg = new File(gsiDir, "system.img");
        File vbmetaImg = new File(gsiDir, "vbmeta.img");
        FileUtil.writeToFile("ddd", systemImg);
        FileUtil.writeToFile("ddd", vbmetaImg);
        mBuildInfo.setFile("gsi_system.img", systemImg, "0");
        mBuildInfo.setFile("gsi_vbmeta.img", vbmetaImg, "0");

        when(mMockDevice.getApiLevel()).thenReturn(29);

        when(mMockDevice.executeLongFastbootCommand(
                        "--disable-verification",
                        "flash",
                        "vbmeta",
                        mBuildInfo.getFile("gsi_vbmeta.img").getAbsolutePath()))
                .thenReturn(mSuccessResult);
        doGetSlotExpectation();

        when(mMockDevice.executeLongFastbootCommand("delete-logical-partition", "product_a"))
                .thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand("-w")).thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand("erase", "system_a"))
                .thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand(
                        "flash", "system", mBuildInfo.getFile("gsi_system.img").getAbsolutePath()))
                .thenReturn(mSuccessResult);

        doThrow(new DeviceNotAvailableException("test", "serial"))
                .when(mMockDevice)
                .rebootUntilOnline();

        try {
            mPreparer.setUp(mTestInfo);
            fail("Expect to get DeviceNotAvailableException from setUp");
        } catch (DeviceNotAvailableException e) {
            // expected
        }

        verify(mMockDevice).waitForDeviceOnline();
        verify(mMockDevice).rebootIntoBootloader();
        verify(mMockRunUtil).allowInterrupt(false);
        verify(mMockDevice).rebootIntoFastbootd();
        verify(mMockRunUtil).allowInterrupt(true);
        verify(mMockRunUtil).sleep(Mockito.anyLong());
    }
}
