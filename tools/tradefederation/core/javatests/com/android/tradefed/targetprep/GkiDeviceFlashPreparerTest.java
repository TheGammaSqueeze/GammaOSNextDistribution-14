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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
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

import com.google.common.io.PatternFilenameFilter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Unit tests for {@link GkiDeviceFlashPreparer}. */
@RunWith(JUnit4.class)
public class GkiDeviceFlashPreparerTest {

    private GkiDeviceFlashPreparer mPreparer;
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
                new GkiDeviceFlashPreparer() {
                    @Override
                    protected IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    protected IHostOptions getHostOptions() {
                        return new HostOptions();
                    }

                    /**
                     * Create a system_dlkm staging directory with 3 kernel modules to test moving
                     * the kernel artifacts under the /lib/modules folder directly. Here is what the
                     * file structure should look like:
                     *
                     * <p>lib/modules/<kernel-version>/modules.load
                     * lib/modules/<kernel-version>/modules.order
                     * lib/modules/<kernel-version>/modules.dep.bin
                     * lib/modules/<kernel-version>/modules.dep
                     * lib/modules/<kernel-version>/modules.alias.bin
                     * lib/modules/<kernel-version>/modules.alias
                     * lib/modules/<kernel-version>/kernel/a.ko
                     * lib/modules/<kernel-version>/kernel/b.ko
                     * lib/modules/<kernel-version>/kernel/foo/c.ko
                     *
                     * @param systemDlkmArchive the system_dlkm tar gzip file containing GKI
                     *     modules.
                     * @return File containing the system_dlkm tar gzip contents.
                     * @throws IOException
                     */
                    @Override
                    protected File extractSystemDlkmTarGzip(File systemDlkmArchive)
                            throws IOException {
                        File systemDlkmStagingDir =
                                FileUtil.createTempDir("system_dlkm_staging", mTmpDir);
                        File stagingLibDir =
                                FileUtil.createNamedTempDir(systemDlkmStagingDir, "lib");
                        File stagingLibModulesDir =
                                FileUtil.createNamedTempDir(stagingLibDir, "modules");
                        File stagingVersionDir =
                                FileUtil.createNamedTempDir(
                                        stagingLibModulesDir,
                                        "5.15.110-android14-11-g3abb2ec8d273-ab10716785");
                        File stagingKernelDir =
                                FileUtil.createNamedTempDir(stagingVersionDir, "kernel");
                        File stagingKernelFooDir =
                                FileUtil.createNamedTempDir(stagingKernelDir, "foo");
                        File modulesLoad = new File(stagingVersionDir, "modules.load");
                        File modulesOrder = new File(stagingVersionDir, "modules.order");
                        File modulesDepBin = new File(stagingVersionDir, "modules.dep.bin");
                        File modulesDep = new File(stagingVersionDir, "modules.dep");
                        File modulesAliasBin = new File(stagingVersionDir, "modules.alias.bin");
                        File modulesAlias = new File(stagingVersionDir, "modules.alias");
                        File moduleA = new File(stagingKernelDir, "a.ko");
                        File moduleB = new File(stagingKernelDir, "b.ko");
                        File moduleFooC = new File(stagingKernelFooDir, "c.ko");
                        FileUtil.writeToFile("mmm", modulesLoad);
                        FileUtil.writeToFile("mmm", modulesOrder);
                        FileUtil.writeToFile("mmm", modulesDepBin);
                        FileUtil.writeToFile("mmm", modulesDep);
                        FileUtil.writeToFile("mmm", modulesAliasBin);
                        FileUtil.writeToFile("mmm", modulesAlias);
                        FileUtil.writeToFile("aaa", moduleA);
                        FileUtil.writeToFile("bbb", moduleB);
                        FileUtil.writeToFile("ccc", moduleFooC);

                        return systemDlkmStagingDir;
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

    /* Verifies that validateGkiBootImg will throw TargetSetupError if there is no BuildInfo files */
    @Test
    public void testValidateGkiBootImg_NoBuildInfoFiles() throws Exception {

        try {
            mPreparer.validateGkiBootImg(mMockDevice, mBuildInfo, mTmpDir);
            fail("TargetSetupError is expected");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /* Verifies that validateGkiBootImg will throw exception when there is no ramdisk-recovery.img*/
    @Test
    public void testValidateGkiBootImg_NoRamdiskRecoveryImg() throws Exception {
        File kernelImage = new File(mTmpDir, "Image.gz");
        mBuildInfo.setFile("Image.gz", kernelImage, "0");

        try {
            mPreparer.validateGkiBootImg(mMockDevice, mBuildInfo, mTmpDir);
            fail("TargetSetupError is expected");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /* Verifies that validateGkiBootImg will throw exception when there is no otatools.zip*/
    @Test
    public void testValidateGkiBootImg_NoOtatoolsZip() throws Exception {
        File kernelImage = new File(mTmpDir, "Image.gz");
        mBuildInfo.setFile("Image.gz", kernelImage, "0");
        File ramdiskRecoveryImage = new File(mTmpDir, "ramdisk-recovery.img");
        mBuildInfo.setFile("ramdisk.img", ramdiskRecoveryImage, "0");

        try {
            mPreparer.validateGkiBootImg(mMockDevice, mBuildInfo, mTmpDir);
            fail("TargetSetupError is expected");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /* Verifies that validateGkiBootImg will throw exception when there is no valid mkbootimg in otatools.zip*/
    @Test
    public void testValidateGkiBootImg_NoMkbootimgInOtatoolsZip() throws Exception {
        File kernelImage = new File(mTmpDir, "Image.gz");
        mBuildInfo.setFile("Image.gz", kernelImage, "0");
        File ramdiskRecoveryImage = new File(mTmpDir, "ramdisk-recovery.img");
        mBuildInfo.setFile("ramdisk.img", ramdiskRecoveryImage, "0");
        File otaDir = FileUtil.createTempDir("otatool_folder", mTmpDir);
        File tmpFile = new File(otaDir, "test");
        File otatoolsZip = FileUtil.createTempFile("otatools", ".zip", mTmpDir);
        ZipUtil.createZip(List.of(tmpFile), otatoolsZip);
        mBuildInfo.setFile("otatools.zip", otatoolsZip, "0");

        try {
            mPreparer.validateGkiBootImg(mMockDevice, mBuildInfo, mTmpDir);
            fail("TargetSetupError is expected");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /* Verifies that validateGkiBootImg will throw exception when fail to generate GKI boot.img*/
    @Test
    public void testValidateGkiBootImg_FailToGenerateBootImg() throws Exception {
        File kernelImage = new File(mTmpDir, "Image.gz");
        mBuildInfo.setFile("Image.gz", kernelImage, "0");
        File ramdiskRecoveryImage = new File(mTmpDir, "ramdisk-recovery.img");
        mBuildInfo.setFile("ramdisk.img", ramdiskRecoveryImage, "0");
        File otaDir = FileUtil.createTempDir("otatool_folder", mTmpDir);
        File mkbootimgFile = new File(otaDir, "mkbootimg");
        File otatoolsZip = FileUtil.createTempFile("otatools", ".zip", mTmpDir);
        ZipUtil.createZip(List.of(mkbootimgFile), otatoolsZip);
        mBuildInfo.setFile("otatools.zip", otatoolsZip, "0");
        CommandResult cmdResult = new CommandResult();
        cmdResult.setStatus(CommandStatus.FAILED);
        cmdResult.setStdout("output");
        cmdResult.setStderr("error");
        when(mMockRunUtil.runTimedCmd(
                        anyLong(),
                        matches(".*mkbootimg.*"),
                        eq("--kernel"),
                        eq(kernelImage.getAbsolutePath()),
                        eq("--header_version"),
                        eq("3"),
                        eq("--base"),
                        eq("0x00000000"),
                        eq("--pagesize"),
                        eq("4096"),
                        eq("--ramdisk"),
                        eq(ramdiskRecoveryImage.getAbsolutePath()),
                        eq("-o"),
                        matches(".*boot.*img.*")))
                .thenReturn(cmdResult);

        try {
            mPreparer.validateGkiBootImg(mMockDevice, mBuildInfo, mTmpDir);
            fail("TargetSetupError is expected");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /* Test add hash footer to GKI boot.img*/
    @Test
    public void testAddHashFooter() throws Exception {
        File bootImg = FileUtil.createTempFile("boot", ".img", mTmpDir);
        bootImg.renameTo(new File(mTmpDir, "boot.img"));
        FileUtil.writeToFile("ddd", bootImg);
        mBuildInfo.setFile("gki_boot.img", bootImg, "0");

        File otaDir = FileUtil.createTempDir("otatool_folder", mTmpDir);
        File otaBinDir = FileUtil.createNamedTempDir(otaDir, "bin");
        File avbtoolFile = new File(otaBinDir, "avbtool");
        FileUtil.writeToFile("ddd", avbtoolFile);
        File otatoolsZip = FileUtil.createTempFile("otatools", ".zip", mTmpDir);
        ZipUtil.createZip(List.of(otaDir.listFiles()), otatoolsZip);
        mBuildInfo.setFile("otatools.zip", otatoolsZip, "0");

        when(mMockDevice.getProperty("ro.build.version.release")).thenReturn("13");
        when(mMockDevice.getProperty("ro.build.version.security_patch")).thenReturn("2022-08-05");
        CommandResult res = new CommandResult();
        res.setStatus(CommandStatus.SUCCESS);
        res.setStdout("53477376\n");
        when(mMockRunUtil.runTimedCmd(anyLong(), eq("du"), eq("-b"), eq(bootImg.getAbsolutePath())))
                .thenReturn(res);
        when(mMockRunUtil.runTimedCmd(
                        anyLong(),
                        matches(".*avbtool"),
                        eq("add_hash_footer"),
                        eq("--image"),
                        eq(bootImg.getAbsolutePath()),
                        eq("--partition_size"),
                        eq("53477376"),
                        eq("--partition_name"),
                        eq("boot"),
                        eq("--prop"),
                        eq("com.android.build.boot.os_version:13"),
                        eq("--prop"),
                        eq("com.android.build.boot.security_patch:2022-08-05")))
                .thenReturn(mSuccessResult);

        mPreparer.validateGkiBootImg(mMockDevice, mBuildInfo, mTmpDir);
        mPreparer.addHashFooter(mMockDevice, mBuildInfo, mTmpDir);
    }

    /* Verifies that setUp will throw TargetSetupError if there is no gki boot.img */
    @Test
    public void testSetUp_NoGkiBootImg() throws Exception {

        try {
            mPreparer.setUp(mTestInfo);
            fail("TargetSetupError is expected");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /* Verifies that setUp will throw exception when there is no valid mkbootimg in otatools.zip*/
    @Test
    public void testSetUp_NoMkbootimgInOtatoolsZip() throws Exception {
        File kernelImage = new File(mTmpDir, "Image.gz");
        mBuildInfo.setFile("Image.gz", kernelImage, "0");
        File ramdiskRecoveryImage = new File(mTmpDir, "ramdisk-recovery.img");
        mBuildInfo.setFile("ramdisk-recovery.img", ramdiskRecoveryImage, "0");
        File otaDir = FileUtil.createTempDir("otatool_folder", mTmpDir);
        File tmpFile = new File(otaDir, "test");
        File otatoolsZip = FileUtil.createTempFile("otatools", ".zip", mTmpDir);
        ZipUtil.createZip(List.of(tmpFile), otatoolsZip);
        mBuildInfo.setFile("otatools.zip", otatoolsZip, "0");

        try {
            mPreparer.setUp(mTestInfo);
            fail("TargetSetupError is expected");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /* Verifies that preparer can flash GKI boot image */
    @Test
    public void testSetup_Success() throws Exception {
        File bootImg = FileUtil.createTempFile("boot", ".img", mTmpDir);
        bootImg.renameTo(new File(mTmpDir, "boot.img"));
        FileUtil.writeToFile("ddd", bootImg);
        mBuildInfo.setFile("gki_boot.img", bootImg, "0");

        when(mMockDevice.executeLongFastbootCommand(
                        "flash", "boot", mBuildInfo.getFile("gki_boot.img").getAbsolutePath()))
                .thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand("-w")).thenReturn(mSuccessResult);

        when(mMockDevice.enableAdbRoot()).thenReturn(Boolean.TRUE);

        mPreparer.setUp(mTestInfo);
        mPreparer.tearDown(mTestInfo, null);

        verify(mMockDevice).rebootIntoBootloader();
        verify(mMockRunUtil).allowInterrupt(false);
        verify(mMockRunUtil).allowInterrupt(true);
        verify(mMockRunUtil).sleep(anyLong());
        verify(mMockDevice).rebootUntilOnline();
        verify(mMockDevice).setDate(null);
        verify(mMockDevice).waitForDeviceAvailable(anyLong());
        verify(mMockDevice).setRecoveryMode(RecoveryMode.AVAILABLE);
        verify(mMockDevice).postBootSetup();
    }

    /* Verifies that preparer can flash GKI boot image and vendor_boot,
     * vendor_kernel_boot, vendor_dlkm, and dtbo images */
    @Test
    public void testSetup_vendor_img_Success() throws Exception {
        File imgDir = FileUtil.createTempDir("img_folder", mTmpDir);
        File bootImg = new File(imgDir, "boot-5.4.img");
        File vendorBootImg = new File(imgDir, "vendor_boot.img");
        File vendorKernelBootImg = new File(imgDir, "vendor_kernel_boot.img");
        File dtboImg = new File(imgDir, "dtbo.img");
        File vendorDlkmImg = new File(imgDir, "vendor_dlkm.img");
        FileUtil.writeToFile("ddd", bootImg);
        FileUtil.writeToFile("123", vendorBootImg);
        FileUtil.writeToFile("321", vendorKernelBootImg);
        FileUtil.writeToFile("456", dtboImg);
        FileUtil.writeToFile("789", vendorDlkmImg);
        mBuildInfo.setFile("gki_boot.img", bootImg, "0");
        mBuildInfo.setFile("vendor_boot.img", vendorBootImg, "0");
        mBuildInfo.setFile("vendor_kernel_boot.img", vendorKernelBootImg, "0");
        mBuildInfo.setFile("vendor_dlkm.img", vendorDlkmImg, "0");
        mBuildInfo.setFile("dtbo.img", dtboImg, "0");

        when(mMockDevice.executeLongFastbootCommand(
                        "flash", "boot", mBuildInfo.getFile("gki_boot.img").getAbsolutePath()))
                .thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand(
                        "flash",
                        "vendor_boot",
                        mBuildInfo.getFile("vendor_boot.img").getAbsolutePath()))
                .thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand(
                        "flash",
                        "vendor_kernel_boot",
                        mBuildInfo.getFile("vendor_kernel_boot.img").getAbsolutePath()))
                .thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand(
                        "flash",
                        "vendor_dlkm",
                        mBuildInfo.getFile("vendor_dlkm.img").getAbsolutePath()))
                .thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand(
                        "flash", "dtbo", mBuildInfo.getFile("dtbo.img").getAbsolutePath()))
                .thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand("-w")).thenReturn(mSuccessResult);

        when(mMockDevice.enableAdbRoot()).thenReturn(Boolean.TRUE);

        mPreparer.setUp(mTestInfo);
        mPreparer.tearDown(mTestInfo, null);

        verify(mMockDevice).rebootIntoBootloader();
        verify(mMockRunUtil).allowInterrupt(false);
        verify(mMockRunUtil).allowInterrupt(true);
        verify(mMockDevice).rebootIntoFastbootd();
        verify(mMockRunUtil).sleep(anyLong());
        verify(mMockDevice).rebootUntilOnline();
        verify(mMockDevice).setDate(null);
        verify(mMockDevice).waitForDeviceAvailable(anyLong());
        verify(mMockDevice).setRecoveryMode(RecoveryMode.AVAILABLE);
        verify(mMockDevice).postBootSetup();
    }

    /* Verifies that preparer can flash GKI boot image from a Zip file*/
    @Test
    public void testSetup_Success_FromZip() throws Exception {
        File imgDir = FileUtil.createTempDir("img_folder", mTmpDir);
        File bootImg = new File(imgDir, "boot-5.4.img");
        File vendorBootImg = new File(imgDir, "vendor_boot.img");
        File vendorKernelBootImg = new File(imgDir, "vendor_kernel_boot.img");
        File dtboImg = new File(imgDir, "dtbo.img");
        FileUtil.writeToFile("ddd", bootImg);
        FileUtil.writeToFile("aaa", vendorBootImg);
        FileUtil.writeToFile("ccc", vendorKernelBootImg);
        FileUtil.writeToFile("bbb", dtboImg);
        File imgZip = FileUtil.createTempFile("gki_image", ".zip", mTmpDir);
        ZipUtil.createZip(List.of(bootImg, vendorBootImg, vendorKernelBootImg, dtboImg), imgZip);
        mBuildInfo.setFile("gki_boot.img", imgZip, "0");
        mBuildInfo.setFile("vendor_boot.img", imgZip, "0");
        mBuildInfo.setFile("dtbo.img", imgZip, "0");

        when(mMockDevice.executeLongFastbootCommand(
                        eq("flash"), eq("boot"), matches(".*boot-5.4.img")))
                .thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand(
                        eq("flash"), eq("vendor_boot"), matches(".*vendor_boot.img")))
                .thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand(
                        eq("flash"), eq("vendor_kernel_boot"), matches(".*vendor_kernel_boot.img")))
                .thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand(eq("flash"), eq("dtbo"), matches(".*dtbo.img")))
                .thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand("-w")).thenReturn(mSuccessResult);

        when(mMockDevice.enableAdbRoot()).thenReturn(Boolean.TRUE);

        mPreparer.setUp(mTestInfo);
        mPreparer.tearDown(mTestInfo, null);

        verify(mMockDevice).rebootIntoBootloader();
        verify(mMockRunUtil).allowInterrupt(false);
        verify(mMockRunUtil).allowInterrupt(true);
        verify(mMockRunUtil).sleep(anyLong());
        verify(mMockDevice).rebootUntilOnline();
        verify(mMockDevice).setDate(null);
        verify(mMockDevice).waitForDeviceAvailable(anyLong());
        verify(mMockDevice).setRecoveryMode(RecoveryMode.AVAILABLE);
        verify(mMockDevice).postBootSetup();
    }

    /* Verifies that preparer will throw TargetSetupError with GKI flash failure*/
    @Test
    public void testSetUp_GkiFlashFailure() throws Exception {
        File bootImg = FileUtil.createTempFile("boot", ".img", mTmpDir);
        bootImg.renameTo(new File(mTmpDir, "boot.img"));
        FileUtil.writeToFile("ddd", bootImg);
        mBuildInfo.setFile("gki_boot.img", bootImg, "0");
        File deviceImg = FileUtil.createTempFile("device_image", ".zip", mTmpDir);
        FileUtil.writeToFile("not an empty file", deviceImg);
        mBuildInfo.setDeviceImageFile(deviceImg, "0");

        when(mMockDevice.executeLongFastbootCommand(
                        "flash", "boot", mBuildInfo.getFile("gki_boot.img").getAbsolutePath()))
                .thenReturn(mFailureResult);

        try {
            mPreparer.setUp(mTestInfo);
            fail("Expect to get TargetSetupError from setUp");
        } catch (TargetSetupError e) {
            // expected
        }

        verify(mMockDevice).rebootIntoBootloader();
        verify(mMockRunUtil).allowInterrupt(false);
        verify(mMockRunUtil).allowInterrupt(true);
    }

    /* Verifies that preparer will throw DeviceNotAvailableException if device fails to boot up */
    @Test
    public void testSetUp_BootFailure() throws Exception {
        File bootImg = FileUtil.createTempFile("boot", ".img", mTmpDir);
        bootImg.renameTo(new File(mTmpDir, "boot.img"));
        FileUtil.writeToFile("ddd", bootImg);
        mBuildInfo.setFile("gki_boot.img", bootImg, "0");
        File deviceImg = FileUtil.createTempFile("device_image", ".zip", mTmpDir);
        FileUtil.writeToFile("not an empty file", deviceImg);
        mBuildInfo.setDeviceImageFile(deviceImg, "0");

        when(mMockDevice.executeLongFastbootCommand(
                        "flash", "boot", mBuildInfo.getFile("gki_boot.img").getAbsolutePath()))
                .thenReturn(mSuccessResult);
        when(mMockDevice.executeLongFastbootCommand("-w")).thenReturn(mSuccessResult);

        doThrow(new DeviceNotAvailableException("test", "serial"))
                .when(mMockDevice)
                .rebootUntilOnline();

        try {
            mPreparer.setUp(mTestInfo);
            fail("Expect to get DeviceNotAvailableException from setUp");
        } catch (DeviceNotAvailableException e) {
            // expected
        }

        verify(mMockDevice).rebootIntoBootloader();
        verify(mMockRunUtil).allowInterrupt(false);
        verify(mMockRunUtil).allowInterrupt(true);
        verify(mMockRunUtil).sleep(anyLong());
    }

    /* Verifies that getRequestedFile() extracts files properly from a zip
     * file. */
    @Test
    public void testGetRequestedFile_VerifyExtractingFromZip() throws Exception {
        File otaDir = FileUtil.createTempDir("otatool_folder", mTmpDir);
        File otaFooDir = FileUtil.createNamedTempDir(otaDir, "foo");
        File otaBarDir = FileUtil.createNamedTempDir(otaDir, "bar");

        List<File> fileList = new ArrayList<>();
        fileList.add(new File(otaDir, "test"));
        fileList.add(new File(otaFooDir, "buzz"));
        fileList.add(new File(otaBarDir, "baz"));
        fileList.add(new File(otaBarDir, "withSuffix.zip"));
        for (File f : fileList) {
            FileUtil.writeToFile("ddd", f);
        }

        List<String> fileListNames = new ArrayList<>();
        try (Stream<Path> otaDirPaths = Files.walk(otaDir.toPath())) {
            otaDirPaths
                    .filter(path -> path.toFile().isFile())
                    .forEach(
                            path -> {
                                fileListNames.add(otaDir.toPath().relativize(path).toString());
                            });
        }
        // Test using a regex to find the file "foo/buzz"
        fileListNames.add(".*buzz");

        File otatoolsZip = FileUtil.createTempFile("otatools", ".zip", mTmpDir);
        ZipUtil.createZip(List.of(otaDir.listFiles()), otatoolsZip);
        mBuildInfo.setFile("otatools.zip", otatoolsZip, "0");

        for (String filename : fileListNames) {
            assertTrue(
                    String.format("Expected to extract the file: %s", filename),
                    mPreparer
                            .getRequestedFile(mMockDevice, filename, otatoolsZip, mTmpDir)
                            .exists());
        }
    }

    /* Verifies a system_dlkm tarball is flattened correctly. */
    @Test
    public void testFlattenSystemDlkm() throws Exception {
        /* Create the system_dlkm staging directory with 3 modules */
        File systemDlkmArchive =
                FileUtil.createTempFile("system_dlkm_staging_archive", ".tar.gz", mTmpDir);
        mBuildInfo.setFile("system_dlkm_staging_archive.tar.gz", systemDlkmArchive, "0");

        File systemDlkmStagingDir = mPreparer.extractSystemDlkmTarGzip(systemDlkmArchive);
        File stagingLibModulesDir = new File(systemDlkmStagingDir, "lib/modules");
        mPreparer.flattenSystemDlkm(mMockDevice, systemDlkmStagingDir);
        assertTrue(
                "Expected to find 3 modules under /lib/modules",
                stagingLibModulesDir.listFiles(new PatternFilenameFilter(".*\\.ko")).length == 3);

        /* Tests flattening an already flat system_dlkm staging directory. */
        mPreparer.flattenSystemDlkm(mMockDevice, systemDlkmStagingDir);
        assertTrue(
                "Expected to find 3 modules under /lib/modules",
                stagingLibModulesDir.listFiles(new PatternFilenameFilter(".*\\.ko")).length == 3);
    }

    /* Verifies building a GKI system_dlkm image from a tarball. */
    @Test
    public void testBuildGkiSystemDlkmImg() throws Exception {
        File systemDlkmArchive =
                FileUtil.createTempFile("system_dlkm_staging_archive", ".tar.gz", mTmpDir);
        mBuildInfo.setFile("system_dlkm_staging_archive.tar.gz", systemDlkmArchive, "0");

        File otaDir = FileUtil.createTempDir("otatool_folder", mTmpDir);
        File otaBinDir = FileUtil.createNamedTempDir(otaDir, "bin");
        File avbtoolFile = new File(otaBinDir, "avbtool");
        File buildImageFile = new File(otaBinDir, "build_image");
        File mkuserimgFile = new File(otaBinDir, "mkuserimg_mke2fs");
        File mke2fsFile = new File(otaBinDir, "mke2fs");
        File e2fsdroidFile = new File(otaBinDir, "e2fsdroid");
        FileUtil.writeToFile("ddd", avbtoolFile);
        FileUtil.writeToFile("ddd", buildImageFile);
        FileUtil.writeToFile("ddd", mkuserimgFile);
        FileUtil.writeToFile("ddd", mke2fsFile);
        FileUtil.writeToFile("ddd", e2fsdroidFile);
        File otatoolsZip = FileUtil.createTempFile("otatools", ".zip", mTmpDir);
        ZipUtil.createZip(List.of(otaDir.listFiles()), otatoolsZip);
        mBuildInfo.setFile("otatools.zip", otatoolsZip, "0");

        when(mMockRunUtil.runTimedCmd(
                        anyLong(),
                        matches(".*build_image"),
                        matches(".*system_dlkm_staging.*"),
                        matches(".*system_dlkm.props"),
                        matches(".*system_dlkm.img"),
                        eq("/dev/null")))
                .thenReturn(mSuccessResult);

        try {
            mPreparer.buildGkiSystemDlkmImg(mMockDevice, mBuildInfo, mTmpDir);
            fail("TargetSetupError is expected");
        } catch (TargetSetupError e) {
            // Double check the error message
            if (!Pattern.matches(".*build_image tool.*system_dlkm.*size=0.*", e.toString())) {
                throw e;
            }
        }
    }
}
