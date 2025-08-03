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
package com.android.tradefed.targetprep;

import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.util.FileUtil;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;

/** Unit tests for {@link MixKernelTargetPreparer} */
@RunWith(JUnit4.class)
public class MixKernelTargetPreparerTest {
    private IInvocationContext mContext;
    private TestInformation mTestInfo;
    private IDeviceBuildInfo mBuildInfo;

    @Before
    public void setUp() {
        mContext = new InvocationContext();
        mBuildInfo = new DeviceBuildInfo();
        mBuildInfo.setBuildFlavor("flavor");
        mContext.addDeviceBuildInfo("device", mBuildInfo);
        mContext.addAllocatedDevice("device", Mockito.mock(ITestDevice.class));
        mTestInfo = TestInformation.newBuilder().setInvocationContext(mContext).build();
    }

    @Test
    public void testCopyDeviceImageToDir() throws Exception {
        String deviceImageName = "oriole-img-9432383";
        File oldDir = FileUtil.createTempDir("oldDir");
        File newDir = FileUtil.createTempDir("newDir");
        File srcFile = FileUtil.createTempFile(deviceImageName + "_", ".zip", oldDir);
        MixKernelTargetPreparer mk = new MixKernelTargetPreparer();
        try {
            mk.copyDeviceImageToDir(srcFile, newDir);
            if (FileUtil.findFile(newDir, deviceImageName + ".zip") == null) {
                Assert.fail(
                        String.format(
                                "Copy file %s to %s/%s.zip failed. Files in newDir: %s",
                                srcFile,
                                newDir,
                                deviceImageName,
                                newDir.listFiles()[0].toString()));
            }
        } finally {
            FileUtil.recursiveDelete(oldDir);
            FileUtil.recursiveDelete(newDir);
        }
    }

    @Test
    public void testCopyLabelFileToDir() throws Exception {
        File tmpDir = FileUtil.createTempDir("tmpdir");
        File srcFile1 = FileUtil.createTempFile("Image", ".gz", tmpDir);
        File srcFile2 = FileUtil.createTempFile("oriole-img-9089658", ".zip", tmpDir);
        MixKernelTargetPreparer mk = new MixKernelTargetPreparer();
        try {
            mk.copyLabelFileToDir("{kernel}Image.gz", srcFile1, tmpDir);
            mk.copyLabelFileToDir("{device}some-img-001{zip}", srcFile2, tmpDir);
            if (FileUtil.findFile(tmpDir, "Image.gz") == null) {
                Assert.fail(String.format("Copy file %s to %s/Imag.gz failed", srcFile1, tmpDir));
            }
            if (FileUtil.findFile(tmpDir, "some-img-001.zip") == null) {
                Assert.fail(
                        String.format("Copy file %s to %s/some-img-001 failed", srcFile1, tmpDir));
            }
        } finally {
            FileUtil.recursiveDelete(tmpDir);
        }
    }

    @Test
    public void testFailsOnMissingDeviceImage() throws Exception {
        MixKernelTargetPreparer mk = new MixKernelTargetPreparer();
        try {
            mk.setUp(mTestInfo);
            Assert.fail("Expected TargetSetupError");
        } catch (TargetSetupError e) {
            // expected.
        }
    }

    @Test
    public void testFailsOnMissingKernelImage() throws Exception {
        MixKernelTargetPreparer mk = new MixKernelTargetPreparer();
        File deviceImage = FileUtil.createTempFile("device-img-12345", "zip");
        mBuildInfo.setFile("device", deviceImage, "0");
        try {
            mContext.addDeviceBuildInfo("device", new DeviceBuildInfo());
            mk.setUp(mTestInfo);
            Assert.fail("Expected TargetSetupError");
        } catch (TargetSetupError e) {
            // expected.
        } finally {
            FileUtil.recursiveDelete(deviceImage);
        }
    }

    @Test
    public void testFailsOnMissingKernelMixingTool() throws Exception {
        MixKernelTargetPreparer mk = new MixKernelTargetPreparer();
        File deviceImage = FileUtil.createTempFile("device-img-12345", "zip");
        mBuildInfo.setFile("device", deviceImage, "0");
        File kernelImage = FileUtil.createTempFile("device-img-12345", "zip");
        mBuildInfo.setFile("{kernel}Image.gz", kernelImage, "0");
        try {
            mk.setUp(mTestInfo);
            Assert.fail("Expected TargetSetupError");
        } catch (TargetSetupError e) {
            // expected.
        } finally {
            FileUtil.recursiveDelete(deviceImage);
            FileUtil.recursiveDelete(kernelImage);
        }
    }

    @Test
    public void testFailsOnRunningKernelMixingTool() throws Exception {
        MixKernelTargetPreparer mk =
                new MixKernelTargetPreparer() {
                    @Override
                    protected void runMixKernelTool(
                            ITestDevice device,
                            File oldDeviceDir,
                            File kernelDir,
                            File gkiDir,
                            File newDeviceDir)
                            throws TargetSetupError {
                        throw new TargetSetupError("Failed to run mixing tool");
                    }
                };
        File deviceImage = FileUtil.createTempFile("device-img-12345", "zip");
        File kernelImage = FileUtil.createTempFile("dtbo", "img");
        File testsDir = FileUtil.createTempDir("testsdir");
        OptionSetter setter = new OptionSetter(mk);
        setter.setOptionValue("mix-kernel-tool-name", "build_mixed_kernels_ramdisk");
        File mixKernelTool = FileUtil.createTempFile("build_mixed_kernels_ramdisk", null, testsDir);
        mixKernelTool.renameTo(new File(testsDir, "build_mixed_kernels_ramdisk"));
        mBuildInfo.setFile("device", deviceImage, "0");
        mBuildInfo.setFile("{kernel}Image.gz", kernelImage, "0");
        mBuildInfo.setTestsDir(testsDir, "0");
        try {
            mk.setUp(mTestInfo);
            Assert.fail("Expected TargetSetupError");
        } catch (TargetSetupError e) {
            // expected.
        } finally {
            FileUtil.recursiveDelete(deviceImage);
            FileUtil.recursiveDelete(kernelImage);
            FileUtil.recursiveDelete(testsDir);
        }
    }

    @Test
    public void testFailsOnNoNewDeviceImage() throws Exception {
        MixKernelTargetPreparer mk =
                new MixKernelTargetPreparer() {
                    @Override
                    protected void runMixKernelTool(
                            ITestDevice device,
                            File oldDeviceDir,
                            File kernelDir,
                            File gkiDir,
                            File newDeviceDir)
                            throws TargetSetupError {
                        return;
                    }
                };

        File deviceImage = FileUtil.createTempFile("device-img-12345", "zip");
        File kernelImage = FileUtil.createTempFile("dtbo", "img");
        OptionSetter setter = new OptionSetter(mk);
        setter.setOptionValue("mix-kernel-tool-name", "build_mixed_kernels_ramdisk");
        File testsDir = FileUtil.createTempDir("testsdir");
        File mixKernelTool = FileUtil.createTempFile("build_mixed_kernels_ramdisk", null, testsDir);
        mixKernelTool.renameTo(new File(testsDir, "build_mixed_kernels_ramdisk"));
        mBuildInfo.setFile("device", deviceImage, "0");
        mBuildInfo.setFile("{kernel}Image.gz", kernelImage, "0");
        mBuildInfo.setTestsDir(testsDir, "0");
        try {
            mk.setUp(mTestInfo);
            Assert.fail("Expected TargetSetupError");
        } catch (TargetSetupError e) {
            // expected.
        } finally {
            FileUtil.recursiveDelete(deviceImage);
            FileUtil.recursiveDelete(kernelImage);
            FileUtil.recursiveDelete(testsDir);
        }
    }

    @Test
    public void testSuccessfulMixKernel() throws Exception {
        MixKernelTargetPreparer mk =
                new MixKernelTargetPreparer() {
                    @Override
                    protected void runMixKernelTool(
                            ITestDevice device,
                            File oldDeviceDir,
                            File kernelDir,
                            File gkiDir,
                            File newDeviceDir)
                            throws TargetSetupError {
                        try {
                            File newFile =
                                    FileUtil.createTempFile(
                                            "new-device-img-12345", "zip", newDeviceDir);
                            newFile.renameTo(new File(newDeviceDir, "device-img-12345.zip"));
                        } catch (IOException e) {
                            throw new TargetSetupError(
                                    "Could not create file in " + newDeviceDir.toString());
                        }
                    }
                };
        mk.setConfiguration(new Configuration("name", "desc"));
        File deviceImage = FileUtil.createTempFile("device-img-12345", "zip");
        File kernelImage = FileUtil.createTempFile("dtbo", "img");
        File testsDir = FileUtil.createTempDir("testsdir");
        OptionSetter setter = new OptionSetter(mk);
        setter.setOptionValue("mix-kernel-tool-name", "my_tool_12345");
        File mixKernelTool = FileUtil.createTempFile("my_tool_12345", null, testsDir);
        mixKernelTool.renameTo(new File(testsDir, "my_tool_12345"));
        mBuildInfo.setFile("device", deviceImage, "0");
        mBuildInfo.setFile("{kernel}Image.gz", kernelImage, "0");
        mBuildInfo.setTestsDir(testsDir, "0");
        try {
            mk.setUp(mTestInfo);
        } finally {
            FileUtil.recursiveDelete(deviceImage);
            FileUtil.recursiveDelete(kernelImage);
            FileUtil.recursiveDelete(testsDir);
        }
    }

    @Test
    public void testSuccessfulMixKernelWithLocalToolPath() throws Exception {
        MixKernelTargetPreparer mk =
                new MixKernelTargetPreparer() {
                    @Override
                    protected void runMixKernelTool(
                            ITestDevice device,
                            File oldDeviceDir,
                            File kernelDir,
                            File gkiDir,
                            File newDeviceDir)
                            throws TargetSetupError {
                        try {
                            File newFile =
                                    FileUtil.createTempFile(
                                            "new-device-img-12345", "zip", newDeviceDir);
                            newFile.renameTo(new File(newDeviceDir, "device-img-12345.zip"));
                        } catch (IOException e) {
                            throw new TargetSetupError(
                                    "Could not create file in " + newDeviceDir.toString());
                        }
                    }
                };
        mk.setConfiguration(new Configuration("name", "desc"));
        File deviceImage = FileUtil.createTempFile("device-img-12345", "zip");
        File kernelImage = FileUtil.createTempFile("dtbo", "img");
        File toolDir = FileUtil.createTempDir("tooldir");
        File mixKernelTool = FileUtil.createTempFile("my_tool_12345", null, toolDir);
        mBuildInfo.setFile("device", deviceImage, "0");
        mBuildInfo.setFile("{kernel}Image.gz", kernelImage, "0");
        OptionSetter setter = new OptionSetter(mk);
        setter.setOptionValue("mix-kernel-tool-path", mixKernelTool.getAbsolutePath());
        try {
            mk.setUp(mTestInfo);
        } finally {
            FileUtil.recursiveDelete(deviceImage);
            FileUtil.recursiveDelete(kernelImage);
            FileUtil.recursiveDelete(toolDir);
        }
    }

    @Test
    public void testSetNewDeviceImage() throws Exception {
        MixKernelTargetPreparer mk = new MixKernelTargetPreparer();
        mk.setConfiguration(new Configuration("name", "desc"));
        // Create a device image in lc cache
        File deviceImageInLcCache = FileUtil.createTempFile("device-img-12345", ".zip");
        FileUtil.writeToFile("old_image_12345", deviceImageInLcCache);
        // Create the device image with hardlink to device image in lc cache
        File tmpImg = FileUtil.createTempFile("device-img-hard-link", ".zip");
        tmpImg.delete();
        FileUtil.hardlinkFile(deviceImageInLcCache, tmpImg);
        // Add the device image with hardlink to device build info
        mBuildInfo.setFile("device", tmpImg, "0");
        // Create a new device image in the device_dir
        File newImageDir = FileUtil.createTempDir("device_dir");
        File deviceImage = new File(newImageDir, "device-img-12345.zip");
        FileUtil.writeToFile("new_image", deviceImage);
        try {
            if (deviceImageInLcCache.length() != mBuildInfo.getDeviceImageFile().length()) {
                Assert.fail(
                        "Device image in lc cache is not of the same size as device image"
                                + " in device build info before calling setNewDeviceImage");
            }
            mk.setNewDeviceImage(mBuildInfo, newImageDir);
            if (mBuildInfo.getDeviceImageFile() == null
                    || !mBuildInfo.getDeviceImageFile().exists()) {
                Assert.fail("New device image is not set");
            }
            if (deviceImageInLcCache == null || !deviceImageInLcCache.exists()) {
                Assert.fail("Device image in lc cache is gone");
            }
            if (deviceImageInLcCache.length() == mBuildInfo.getDeviceImageFile().length()) {
                Assert.fail(
                        "Device image in lc cache is of the same size as device image "
                                + "in device build info after calling setNewDeviceImage");
            }
        } finally {
            FileUtil.recursiveDelete(deviceImageInLcCache);
            FileUtil.recursiveDelete(tmpImg);
            FileUtil.recursiveDelete(newImageDir);
        }
    }
}
