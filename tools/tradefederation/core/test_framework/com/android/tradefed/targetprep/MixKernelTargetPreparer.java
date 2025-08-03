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

import com.android.tradefed.build.BuildInfoKey.BuildInfoFileKey;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A {@link ITargetPreparer} that allows to mix a kernel image with the device image. */
@OptionClass(alias = "mix-kernel-target-preparer")
public class MixKernelTargetPreparer extends BaseTargetPreparer
        implements ILabPreparer, IConfigurationReceiver {

    @Option(
            name = "device-file-label",
            description = "The label for the test device that stores device images.")
    private String mDeviceLabel = "device";

    @Option(
            name = "kernel-file-label",
            description =
                    "The file key prefix that is attached to the kernel images, "
                            + " for example {kernel}Image.gz.")
    private String mKernelLabel = "kernel";

    @Option(
            name = "gki-file-label",
            description =
                    "The file key prefix that is attached the GKI images, "
                            + "for example {gki}Image.gz.")
    private String mGkiLabel = "gki";

    /*
    To use mix kernel tool on your local file system, use flag --mix-kernel-tool-path
    For example: --mix-kernel-tool-path
    /the_path_to_internal_android_tree/vendor/google/tools/build_mixed_kernels

    A file directory path can also be used. The mixing tool will search the mix-kernel-tool-name in
    the specified file directory
    For example the tool path can be specified with flexible download as:
    --mix-kernel-tool-path ab://git_master/flame-userdebug/LATEST/.*flame-tests-.*.zip?unzip=true

    If mix-kernel-tool-path is not specified, testsdir of the device build will be used as the
    mix-kernel-tool-path.
    */
    @Option(
            name = "mix-kernel-tool-path",
            description =
                    "The file path of mix kernel tool. It can be the absolute path of the tool"
                        + " path, or the absolute directory path that contains the tool. It can be"
                        + " used with flexible download feature for example --mix-kernel-tool-path"
                        + " ab://git_master/flame-userdebug/LATEST/.*flame-tests-.*.zip?unzip=true")
    private File mMixKernelToolPath = null;

    @Option(
            name = "mix-kernel-tool-name",
            description =
                    "The mixing kernel tool file name, defaulted to build_mixed_kernels. "
                            + "If mix-kernel-tool-path is a directory, the mix-kernel-tool-name "
                            + "will be used to locate the tool in the directory of "
                            + "mix-kernel-tool-path.")
    private String mMixKernelToolName = "build_mixed_kernels_ramdisk";

    @Option(
            name = "mix-kernel-script-wait-time",
            description = "The maximum wait time for mix kernel script. By default is 20 minutes. ")
    private Duration mMixingWaitTime = Duration.ofMinutes(20);

    @Option(
            name = "mix-kernel-arg",
            description =
                    "Additional arguments to be passed to mix-kernel-script command, "
                            + "including leading dash, e.g. \"--nocompress\"")
    private Collection<String> mMixKernelArgs = new ArrayList<>();

    private IConfiguration mConfig;

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfig = configuration;
    }

    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        ITestDevice device = testInfo.getDevice();
        IBuildInfo buildInfo = testInfo.getBuildInfo();

        File tmpDeviceDir = null;
        File tmpKernelDir = null;
        File tmpGkiDir = null;
        File tmpNewDeviceDir = null;
        try {
            // Find the kernel mixing tool
            findMixKernelTool(buildInfo);

            // Create temp dir for original device build, kernel build, and new device build
            tmpDeviceDir = FileUtil.createTempDir("device_dir");
            tmpKernelDir = FileUtil.createTempDir("kernel_dir");
            tmpNewDeviceDir = FileUtil.createTempDir("new_device_dir");
            tmpGkiDir = FileUtil.createTempDir("gki_dir");

            for (String fileKey : buildInfo.getVersionedFileKeys()) {
                CLog.i("Processing file %s", fileKey);
                File srcFile = buildInfo.getFile(fileKey);
                if (fileKey.contains("{" + mKernelLabel + "}")) {
                    // Copy kernel image to tmpKernelDir
                    copyLabelFileToDir(fileKey, srcFile, tmpKernelDir);
                } else if (fileKey.contains("{" + mGkiLabel + "}")) {
                    // Copy GKI image to tmpGkilDir
                    copyLabelFileToDir(fileKey, srcFile, tmpGkiDir);
                } else if (fileKey.contains("{" + mDeviceLabel + "}")) {
                    // Copy device image to tmpDeviceDir
                    copyLabelFileToDir(fileKey, srcFile, tmpDeviceDir);
                } else if (BuildInfoFileKey.DEVICE_IMAGE.getFileKey().equals(fileKey)) {
                    copyDeviceImageToDir(srcFile, tmpDeviceDir);
                }
            }

            if (tmpDeviceDir.listFiles().length == 0) {
                throw new TargetSetupError(
                        "Could not find device images", device.getDeviceDescriptor());
            }
            if (tmpKernelDir.listFiles().length == 0) {
                throw new TargetSetupError(
                        "Could not find kernel images", device.getDeviceDescriptor());
            }
            // Run the mix kernel tool and generate new device image into tmpNewDeviceDir
            CLog.i("running mixkernel tool from %s", mMixKernelToolPath);
            runMixKernelTool(device, tmpDeviceDir, tmpKernelDir, tmpGkiDir, tmpNewDeviceDir);
            // Find the new device image and copy it to device build info's device image file
            setNewDeviceImage(buildInfo, tmpNewDeviceDir);

        } catch (IOException e) {
            throw new TargetSetupError(
                    "Could not mix device and kernel images", e, device.getDeviceDescriptor());
        } finally {
            FileUtil.recursiveDelete(tmpDeviceDir);
            FileUtil.recursiveDelete(tmpKernelDir);
            FileUtil.recursiveDelete(tmpGkiDir);
            FileUtil.recursiveDelete(tmpNewDeviceDir);
        }
    }

    /**
     * Copy device image from source file to the specified destination directory.
     *
     * @param srcFile the device image source file will be copied from
     * @param destDir the destination directory {@link File} that the files will be copied to
     * @throws IOException if hit IOException
     */
    @VisibleForTesting
    void copyDeviceImageToDir(File srcFile, File destDir) throws IOException {
        // Recover the original file name
        String srcFileName = srcFile.getName();
        String suffix = FileUtil.getExtension(srcFileName);
        String base = FileUtil.getBaseName(srcFileName);
        String newFileName;
        Pattern pattern = Pattern.compile(".*-img-\\d+");
        Matcher matcher = pattern.matcher(base);
        if (matcher.find()) {
            newFileName = matcher.group();
        } else {
            newFileName = new String("device-img-001");
        }
        if (!suffix.isEmpty()) {
            newFileName = newFileName + suffix;
        } else {
            newFileName = newFileName + ".zip";
        }
        File dstFile = new File(destDir, newFileName);
        CLog.i("Copy %s to %s", srcFile.toString(), dstFile.toString());
        FileUtil.hardlinkFile(srcFile, dstFile);
    }

    /**
     * Copy files with labelled filekey to the specified destination directory, with {prefix}
     * stripped. Also replaces {zip} with ".zip" due to MTT cannot handle .zip file natively. See
     * test for examples.
     *
     * @param fileKey the fileKey of the source file will be copied from
     * @param srcFile the source file will be copied from
     * @param destDir the destination directory {@link File} that the files will be copied to
     * @throws IOException if hit IOException
     */
    @VisibleForTesting
    void copyLabelFileToDir(String fileKey, File srcFile, File destDir) throws IOException {
        // Recover the original file name
        String newFileName = fileKey.replace("{zip}", ".zip").replaceAll("\\{\\w+\\}", "");
        File dstFile = new File(destDir, newFileName);
        CLog.i("Copy %s %s to %s", fileKey, srcFile.toString(), dstFile.toString());
        FileUtil.hardlinkFile(srcFile, dstFile);
    }

    /**
     * Find the mix kernel tool if mMixKernelToolPath does not exist. If mix-kernel-tool-path
     * provides a valid mixing tool, use it. Otherwise, try to find the mixing tool from the build
     * provider
     *
     * @param buildInfo the {@link IBuildInfo} where to search for mix kernel script
     * @throws TargetSetupError if flashing script missing or fails
     * @throws IOException if hit IOException
     */
    private void findMixKernelTool(IBuildInfo buildInfo) throws TargetSetupError, IOException {
        if (mMixKernelToolPath == null) {
            CLog.i(
                    "File mix-kernel-tool-path is not configured. Use devices build's testsdir"
                            + " as the mix kernel tool path.");
            mMixKernelToolPath = buildInfo.getFile(BuildInfoFileKey.TESTDIR_IMAGE.getFileKey());
            if (mMixKernelToolPath == null || !mMixKernelToolPath.isDirectory()) {
                throw new TargetSetupError(
                        String.format(
                                "There is no %s to search for mix kernel tool. Please assign a test"
                                        + " artifact with name %s and type TEST_PACKAGE",
                                BuildInfoFileKey.TESTDIR_IMAGE.getFileKey(),
                                BuildInfoFileKey.TESTDIR_IMAGE.getFileKey()),
                        InfraErrorIdentifier.OPTION_CONFIGURATION_ERROR);
            }
        }
        if (mMixKernelToolPath.isDirectory()) {
            File mixKernelTool = FileUtil.findFile(mMixKernelToolPath, mMixKernelToolName);
            if (mixKernelTool == null || !mixKernelTool.exists()) {
                throw new TargetSetupError(
                        String.format(
                                "Could not find the mix kernel tool %s from %s",
                                mMixKernelToolName, mMixKernelToolPath),
                        InfraErrorIdentifier.OPTION_CONFIGURATION_ERROR);
            }
            mMixKernelToolPath = mixKernelTool;
        }
        if (!mMixKernelToolPath.canExecute()) {
            FileUtil.chmodGroupRWX(mMixKernelToolPath);
        }
    }

    /**
     * Find the new device image and set it as device image for the device.
     *
     * @param buildInfo the build info
     * @param newDeviceDir the directory {@link File} where to find the new device image
     * @throws TargetSetupError if fail to get the new device image
     * @throws IOException if hit IOException
     */
    @VisibleForTesting
    void setNewDeviceImage(IBuildInfo buildInfo, File newDeviceDir)
            throws TargetSetupError, IOException {
        CLog.i(
                "Before mixing kernel, the device image %s is of size %d",
                buildInfo.getFile(BuildInfoFileKey.DEVICE_IMAGE.getFileKey()).toString(),
                buildInfo.getFile(BuildInfoFileKey.DEVICE_IMAGE.getFileKey()).length());

        File newDeviceImage = FileUtil.findFile(newDeviceDir, ".*-img-.*.zip$");
        if (newDeviceImage == null || !newDeviceImage.exists()) {
            throw new TargetSetupError(
                    "Failed to get a new device image after mixing",
                    InfraErrorIdentifier.OPTION_CONFIGURATION_ERROR);
        }
        CLog.i(
                "Successfully generated new device image %s of size %d",
                newDeviceImage, newDeviceImage.length());
        String deviceImagePath = buildInfo.getFile(BuildInfoFileKey.DEVICE_IMAGE).getAbsolutePath();
        buildInfo.getFile(BuildInfoFileKey.DEVICE_IMAGE.getFileKey()).delete();
        FileUtil.hardlinkFile(newDeviceImage, new File(deviceImagePath));

        try {
            // Modifying device image cannot work with incremental flashing so
            // self disable it.
            CLog.d("Disabling incremental flashing.");
            mConfig.injectOptionValue("incremental-flashing", "false");
            mConfig.injectOptionValue("force-disable-incremental-flashing", "true");
        } catch (ConfigurationException ignore) {
            CLog.e(ignore);
        }

        CLog.i(
                "After mixing kernel, the device image %s is of size %d",
                buildInfo.getFile(BuildInfoFileKey.DEVICE_IMAGE.getFileKey()).toString(),
                buildInfo.getFile(BuildInfoFileKey.DEVICE_IMAGE.getFileKey()).length());
    }

    /**
     * Run mix kernel tool to generate the new device build
     *
     * <p>Mixing tool Usage: build_mixed_kernels device_dir out_dir target flavor kernel_dir
     *
     * @param device the test device
     * @param oldDeviceDir the directory {@link File} contains old device images
     * @param kernelDir the directory {@link File} contains kernel images destination
     * @param gkiDir the directory {@link File} contains GKI kernel images destination
     * @param newDeviceDir the directory {@link File} where new device images will be generated to
     * @throws TargetSetupError if fails to run mix kernel tool
     * @throws IOException
     */
    protected void runMixKernelTool(
            ITestDevice device, File oldDeviceDir, File kernelDir, File gkiDir, File newDeviceDir)
            throws TargetSetupError {
        List<String> cmd = ArrayUtil.list(mMixKernelToolPath.getAbsolutePath());
        // Tool command line: $0 [<options>] --gki_dir gkiDir oldDeviceDir kernelDir newDeviceDir
        cmd.addAll(mMixKernelArgs);
        if (gkiDir.listFiles().length > 0) {
            cmd.add("--gki_dir");
            cmd.add(gkiDir.toString());
        }
        cmd.add(oldDeviceDir.toString());
        cmd.add(kernelDir.toString());
        cmd.add(newDeviceDir.toString());
        CLog.i("Run %s to mix kernel and device build", mMixKernelToolPath.toString());
        CommandResult result =
                RunUtil.getDefault()
                        .runTimedCmd(
                                mMixingWaitTime.toMillis(), cmd.toArray(new String[cmd.size()]));
        if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
            CLog.e(
                    "Failed to run mix kernel tool. Exit code: %s, stdout: %s, stderr: %s",
                    result.getStatus(), result.getStdout(), result.getStderr());
            throw new TargetSetupError(
                    "Failed to run mix kernel tool. Stderr: " + result.getStderr());
        }
        CLog.i(
                "Successfully mixed kernel to new device image in %s with files %s",
                newDeviceDir.toString(), Arrays.toString(newDeviceDir.list()));
    }
}
