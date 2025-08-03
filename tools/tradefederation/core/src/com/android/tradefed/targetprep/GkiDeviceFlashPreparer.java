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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.host.IHostOptions;
import com.android.tradefed.host.IHostOptions.PermitLimitType;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.TarUtil;
import com.android.tradefed.util.ZipUtil2;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.io.PatternFilenameFilter;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A target preparer that flash the device with android common kernel generic image. Please see
 * https://source.android.com/devices/architecture/kernel/android-common for details.
 */
@OptionClass(alias = "gki-device-flash-preparer")
public class GkiDeviceFlashPreparer extends BaseTargetPreparer implements ILabPreparer {

    private static final String AVBTOOL = "bin/avbtool";
    private static final String MKBOOTIMG = "bin/mkbootimg";
    private static final String BUILD_IMAGE = "bin/build_image";
    private static final String MKE2FS = "bin/mke2fs";
    private static final String MKUSERIMG_MKE2FS = "bin/mkuserimg_mke2fs";
    private static final String E2FSDROID = "bin/e2fsdroid";
    private static final String OTATOOLS_ZIP = "otatools.zip";
    private static final String KERNEL_IMAGE = "Image.gz";
    // Wait time for device state to stablize in millisecond
    private static final int STATE_STABLIZATION_WAIT_TIME = 60000;

    @Option(
            name = "device-boot-time",
            description = "max time to wait for device to boot. Set as 5 minutes by default",
            isTimeVal = true)
    private long mDeviceBootTime = 5 * 60 * 1000;

    @Option(
            name = "gki-boot-image-name",
            description = "The file name in BuildInfo that provides GKI boot image.")
    private String mGkiBootImageName = "gki_boot.img";

    @Option(
            name = "ramdisk-image-name",
            description = "The file name in BuildInfo that provides ramdisk image.")
    private String mRamdiskImageName = "ramdisk.img";

    @Option(
            name = "vendor-boot-image-name",
            description = "The file name in BuildInfo that provides vendor boot image.")
    private String mVendorBootImageName = "vendor_boot.img";

    @Option(
            name = "vendor-kernel-boot-image-name",
            description = "The file name in BuildInfo that provides vendor kernel boot image.")
    private String mVendorKernelBootImageName = "vendor_kernel_boot.img";

    @Option(
            name = "dtbo-image-name",
            description = "The file name in BuildInfo that provides dtbo image.")
    private String mDtboImageName = "dtbo.img";

    @Option(
            name = "vendor-dlkm-image-name",
            description = "The file name in BuildInfo that provides vendor_dlkm image.")
    private String mVendorDlkmImageName = "vendor_dlkm.img";

    @Option(
            name = "system-dlkm-image-name",
            description = "The file name in BuildInfo that provides system_dlkm image.")
    private String mSystemDlkmImageName = "system_dlkm.img";

    @Option(
            name = "system-dlkm-archive-name",
            description =
                    "The file name in BuildInfo that provides system_dlkm_staging_archive.tar.gz.")
    private String mSystemDlkmArchiveName = "system_dlkm_staging_archive.tar.gz";

    @Option(
            name = "boot-image-file-name",
            description =
                    "The boot image file name to search for if gki-boot-image-name in "
                            + "BuildInfo is a zip file or directory, for example boot-5.4-gz.img.")
    private String mBootImageFileName = "boot(.*).img";

    @Option(
            name = "vendor-boot-image-file-name",
            description =
                    "The vendor boot image file name to search for if vendor-boot-image-name in "
                            + "BuildInfo is a zip file or directory, for example vendor_boot.img.")
    private String mVendorBootImageFileName = "vendor_boot.img";

    @Option(
            name = "vendor-kernel-boot-image-file-name",
            description =
                    "The vendor kernel boot image file name to search for if "
                            + "vendor-kernel-boot-image-name in BuildInfo is a zip file or "
                            + "directory, for example vendor_kernel_boot.img.")
    private String mVendorKernelBootImageFileName = "vendor_kernel_boot.img";

    @Option(
            name = "dtbo-image-file-name",
            description =
                    "The dtbo image file name to search for if dtbo-image-name in "
                            + "BuildInfo is a zip file or directory, for example dtbo.img.")
    private String mDtboImageFileName = "dtbo.img";

    @Option(
            name = "vendor-dlkm-image-file-name",
            description =
                    "The vendor_dlkm image file name to search for if vendor-dlkm-image-name in "
                            + "BuildInfo is a zip file or directory, for example vendor_dlkm.img.")
    private String mVendorDlkmImageFileName = "vendor_dlkm.img";

    @Option(
            name = "system-dlkm-image-file-name",
            description =
                    "The system_dlkm image file name to search for if system-dlkm-image-name in "
                            + "BuildInfo is a zip file or directory, for example system_dlkm.img.")
    private String mSystemDlkmImageFileName = "system_dlkm.img";

    @Option(
            name = "post-reboot-device-into-user-space",
            description = "whether to boot the device in user space after flash.")
    private boolean mPostRebootDeviceIntoUserSpace = true;

    @Option(
            name = "wipe-device-after-gki-flash",
            description = "Whether to wipe device after GKI boot image flash.")
    private boolean mShouldWipeDevice = true;

    @Option(name = "oem-disable-verity", description = "Whether to run oem disable-verity.")
    private boolean mShouldDisableOemVerity = false;

    @Option(
            name = "boot-header-version",
            description = "The version of the boot.img header. Set to 3 by default.")
    private int mBootHeaderVersion = 3;

    @Option(
            name = "add-hash-footer",
            description =
                    "Add hash footer to GKI boot image. More info at "
                        + "https://android.googlesource.com/platform/external/avb/+/master/README.md")
    private boolean mAddHashFooter = false;

    private File mBootImg = null;
    private File mSystemDlkmImg = null;

    /** {@inheritDoc} */
    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        ITestDevice device = testInfo.getDevice();
        IBuildInfo buildInfo = testInfo.getBuildInfo();

        File tmpDir = null;
        try {
            tmpDir = FileUtil.createTempDir("gki_preparer");
            validateGkiBootImg(device, buildInfo, tmpDir);
            if (mAddHashFooter) {
                addHashFooter(device, buildInfo, tmpDir);
            }
            buildGkiSystemDlkmImg(device, buildInfo, tmpDir);
            flashGki(device, buildInfo, tmpDir);
        } catch (IOException ioe) {
            throw new TargetSetupError(ioe.getMessage(), ioe, device.getDeviceDescriptor());
        } finally {
            FileUtil.recursiveDelete(tmpDir);
        }

        if (!mPostRebootDeviceIntoUserSpace) {
            return;
        }
        // Wait some time after flashing the image.
        getRunUtil().sleep(STATE_STABLIZATION_WAIT_TIME);
        device.rebootUntilOnline();
        if (device.enableAdbRoot()) {
            device.setDate(null);
        }
        try {
            device.setRecoveryMode(RecoveryMode.AVAILABLE);
            device.waitForDeviceAvailable(mDeviceBootTime);
        } catch (DeviceUnresponsiveException e) {
            // assume this is a build problem
            throw new DeviceFailedToBootError(
                    String.format(
                            "Device %s did not become available after flashing GKI. Exception: %s",
                            device.getSerialNumber(), e),
                    device.getDeviceDescriptor(),
                    DeviceErrorIdentifier.ERROR_AFTER_FLASHING);
        }
        device.postBootSetup();
        CLog.i("Device update completed on %s", device.getDeviceDescriptor());
    }

    /**
     * Get a reference to the {@link IHostOptions}
     *
     * @return the {@link IHostOptions} to use
     */
    @VisibleForTesting
    protected IHostOptions getHostOptions() {
        return GlobalConfiguration.getInstance().getHostOptions();
    }

    /**
     * Get the {@link IRunUtil} instance to use.
     *
     * @return the {@link IRunUtil} to use
     */
    @VisibleForTesting
    protected IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * Flash GKI images.
     *
     * @param device the {@link ITestDevice}
     * @param buildInfo the {@link IBuildInfo} the build info
     * @param tmpDir the temporary directory {@link File}
     * @throws TargetSetupError, DeviceNotAvailableException, IOException
     */
    private void flashGki(ITestDevice device, IBuildInfo buildInfo, File tmpDir)
            throws TargetSetupError, DeviceNotAvailableException {
        device.rebootIntoBootloader();
        if (mShouldDisableOemVerity) {
            executeFastbootCmd(device, "oem disable-verity");
        }
        long start = System.currentTimeMillis();
        getHostOptions().takePermit(PermitLimitType.CONCURRENT_FLASHER);
        CLog.v(
                "Flashing permit obtained after %ds",
                TimeUnit.MILLISECONDS.toSeconds((System.currentTimeMillis() - start)));
        // Don't allow interruptions during flashing operations.
        getRunUtil().allowInterrupt(false);
        try {
            if (buildInfo.getFile(mVendorBootImageName) != null) {
                File vendorBootImg =
                        getRequestedFile(
                                device,
                                mVendorBootImageFileName,
                                buildInfo.getFile(mVendorBootImageName),
                                tmpDir);
                executeFastbootCmd(device, "flash", "vendor_boot", vendorBootImg.getAbsolutePath());
            }
            if (buildInfo.getFile(mVendorKernelBootImageName) != null) {
                File vendorKernelBootImg =
                        getRequestedFile(
                                device,
                                mVendorKernelBootImageFileName,
                                buildInfo.getFile(mVendorKernelBootImageName),
                                tmpDir);
                executeFastbootCmd(device, "flash", "vendor_kernel_boot",
                                vendorKernelBootImg.getAbsolutePath());
            }
            if (buildInfo.getFile(mDtboImageName) != null) {
                File dtboImg =
                        getRequestedFile(
                                device,
                                mDtboImageFileName,
                                buildInfo.getFile(mDtboImageName),
                                tmpDir);
                executeFastbootCmd(device, "flash", "dtbo", dtboImg.getAbsolutePath());
            }

            executeFastbootCmd(device, "flash", "boot", mBootImg.getAbsolutePath());

            if (buildInfo.getFile(mVendorDlkmImageName) != null) {
                File vendorDlkmImg =
                        getRequestedFile(
                                device,
                                mVendorDlkmImageFileName,
                                buildInfo.getFile(mVendorDlkmImageName),
                                tmpDir);
                if (!TestDeviceState.FASTBOOTD.equals(device.getDeviceState())) {
                    device.rebootIntoFastbootd();
                }
                executeFastbootCmd(device, "flash", "vendor_dlkm", vendorDlkmImg.getAbsolutePath());
            }

            if (buildInfo.getFile(mSystemDlkmImageName) != null) {
                File systemDlkmImg =
                        getRequestedFile(
                                device,
                                mSystemDlkmImageFileName,
                                buildInfo.getFile(mSystemDlkmImageName),
                                tmpDir);
                if (!TestDeviceState.FASTBOOTD.equals(device.getDeviceState())) {
                    device.rebootIntoFastbootd();
                }
                executeFastbootCmd(device, "flash", "system_dlkm", systemDlkmImg.getAbsolutePath());
            }

            if (mShouldWipeDevice) {
                executeFastbootCmd(device, "-w");
            }
        } finally {
            getHostOptions().returnPermit(PermitLimitType.CONCURRENT_FLASHER);
            // Allow interruption at the end no matter what.
            getRunUtil().allowInterrupt(true);
            CLog.v(
                    "Flashing permit returned after %ds",
                    TimeUnit.MILLISECONDS.toSeconds((System.currentTimeMillis() - start)));
        }
    }

    /**
     * Validate GKI boot image is expected. (Obsoleted. Please call with tmpDir provided)
     *
     * @param device the {@link ITestDevice}
     * @param buildInfo the {@link IBuildInfo} the build info
     * @throws TargetSetupError if there is no valid gki boot.img
     */
    public void validateGkiBootImg(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError {
        throw new TargetSetupError(
                "Obsoleted. Please use validateGkiBootImg(ITestDevice, IBuildInfo, File)",
                device.getDeviceDescriptor());
    }

    /**
     * Validate GKI boot image is expected. Throw exception if there is no valid boot.img.
     *
     * @param device the {@link ITestDevice}
     * @param buildInfo the {@link IBuildInfo} the build info
     * @param tmpDir the temporary directory {@link File}
     * @throws TargetSetupError if there is no valid gki boot.img
     */
    @VisibleForTesting
    protected void validateGkiBootImg(ITestDevice device, IBuildInfo buildInfo, File tmpDir)
            throws TargetSetupError {
        if (buildInfo.getFile(mGkiBootImageName) != null && mBootImageFileName != null) {
            mBootImg =
                    getRequestedFile(
                            device,
                            mBootImageFileName,
                            buildInfo.getFile(mGkiBootImageName),
                            tmpDir);
            return;
        }
        if (buildInfo.getFile(KERNEL_IMAGE) == null) {
            throw new TargetSetupError(
                    KERNEL_IMAGE + " is not provided. Can not generate GKI boot.img.",
                    device.getDeviceDescriptor());
        }
        if (buildInfo.getFile(mRamdiskImageName) == null) {
            throw new TargetSetupError(
                    mRamdiskImageName + " is not provided. Can not generate GKI boot.img.",
                    device.getDeviceDescriptor());
        }
        if (buildInfo.getFile(OTATOOLS_ZIP) == null) {
            throw new TargetSetupError(
                    OTATOOLS_ZIP + " is not provided. Can not generate GKI boot.img.",
                    device.getDeviceDescriptor());
        }
        try {
            File mkbootimg =
                    getRequestedFile(device, MKBOOTIMG, buildInfo.getFile(OTATOOLS_ZIP), tmpDir);
            mkbootimg.setExecutable(true, false);
            mBootImg = FileUtil.createTempFile("boot", ".img", tmpDir);
            String cmd =
                    String.format(
                            "%s --kernel %s --header_version %d --base 0x00000000 "
                                    + "--pagesize 4096 --ramdisk %s -o %s",
                            mkbootimg.getAbsolutePath(),
                            buildInfo.getFile(KERNEL_IMAGE),
                            mBootHeaderVersion,
                            buildInfo.getFile(mRamdiskImageName),
                            mBootImg.getAbsolutePath());
            executeHostCommand(device, cmd);
            CLog.i("The GKI boot.img is of size %d", mBootImg.length());
            if (mBootImg.length() == 0) {
                throw new TargetSetupError(
                        "The mkbootimg tool didn't generate a valid boot.img.",
                        device.getDeviceDescriptor());
            }
            buildInfo.setFile(mGkiBootImageName, mBootImg, "0");
        } catch (IOException e) {
            throw new TargetSetupError(
                    "Fail to generate GKI boot.img.", e, device.getDeviceDescriptor());
        }
    }

    /**
     * Extracts the system_dlkm tar gzip file into the system_dlkm_staging folder. This function is
     * a wrapper around {@link TarUtil.extractTarGzipToTemp} in order to stub out the untarring for
     * unit testing.
     *
     * @param systemDlkmArchive the system_dlkm tar gzip file containing GKI modules.
     * @return File containing the system_dlkm tar gzip contents.
     * @throws IOException
     */
    @VisibleForTesting
    protected File extractSystemDlkmTarGzip(File systemDlkmArchive) throws IOException {
        return TarUtil.extractTarGzipToTemp(systemDlkmArchive, "system_dlkm_staging");
    }

    /**
     * Flatten the system_dlkm staging directory so that all the kernel modules are directly under
     * /lib/modules. This is necessary to match the expected system_dlkm file layout for platform
     * builds.
     *
     * @param device the {@link ITestDevice}
     * @param systemDlkmStagingDir the system_dlkm staging directory {@link File}
     * @throws IOException or TargetSetupError if there is an error flattening the system_dlkm.
     */
    @VisibleForTesting
    protected void flattenSystemDlkm(ITestDevice device, File systemDlkmStagingDir)
            throws IOException, TargetSetupError {
        File systemStagingLibModulesDir = new File(systemDlkmStagingDir, "lib/modules");

        // Move all modules from the kernel directory to /lib/modules
        Path libModulesPath = systemStagingLibModulesDir.toPath();
        File[] libModulesVersionFiles = systemStagingLibModulesDir.listFiles();
        File libModulesVersionDir = null;
        if (libModulesVersionFiles.length == 1) {
            // Move all the files under the kernel version folder to be
            // under lib/modules.
            libModulesVersionDir = libModulesVersionFiles[0];
            for (File file : libModulesVersionDir.listFiles()) {
                if (file.isFile()) {
                    File hardLink = new File(systemStagingLibModulesDir, file.getName());
                    try {
                        FileUtil.hardlinkFile(file, hardLink, true);
                    } catch (IOException e) {
                        throw new TargetSetupError(
                                String.format(
                                        "Failed to create hardlink of %s to %s",
                                        file.toString(), hardLink.toString()),
                                device.getDeviceDescriptor());
                    }
                }
            }
        }

        Path libModulesKernel =
                new File(
                                libModulesVersionDir != null
                                        ? libModulesVersionDir
                                        : systemStagingLibModulesDir,
                                "kernel")
                        .toPath();
        try (Stream<Path> allPaths = Files.walk(libModulesKernel)) {
            Path[] modulePaths =
                    allPaths.filter(path -> path.toString().endsWith(".ko")).toArray(Path[]::new);
            for (Path path : modulePaths) {
                File hardLink = new File(systemStagingLibModulesDir, path.toFile().getName());
                try {
                    FileUtil.hardlinkFile(path.toFile(), hardLink, true);
                } catch (IOException e) {
                    throw new TargetSetupError(
                            String.format(
                                    "Failed to create a hardlink of %s to %s",
                                    path.toString(), hardLink.toString()),
                            device.getDeviceDescriptor());
                }
            }
        } catch (NoSuchFileException e) {
            // Not a problem. Just means there's either no modules or the
            // tarball is already flat.
            CLog.i("Didn't find a kernel directory under lib/modules");
        }
        if (libModulesVersionDir != null) {
            FileUtil.recursiveDelete(libModulesVersionDir);
        } else if (libModulesKernel != null) {
            FileUtil.recursiveDelete(libModulesKernel.toFile());
        }

        // Remove modules.*.bin and modules.order. These aren't used or
        // included in the platform system_dlkm image.
        File[] files =
                libModulesPath.toFile().listFiles(new PatternFilenameFilter("modules\\..*\\.bin"));
        for (File f : files) {
            Files.deleteIfExists(f.toPath());
        }
        Files.deleteIfExists(libModulesPath.resolve("modules.order"));

        File[] depmodFiles =
                libModulesPath.toFile().listFiles(new PatternFilenameFilter("modules\\..*"));

        // Update the depmod files that reference the kernel modules to use the
        // new path.
        for (File f : depmodFiles) {
            String contents = FileUtil.readStringFromFile(f);
            contents =
                    Pattern.compile("kernel[^: \n\t]*/([^: \n\t]+\\.ko)")
                            .matcher(contents)
                            .replaceAll("$1");
            FileUtil.writeToFile(contents, f);
        }
    }

    /**
     * Build GKI system_dlkm image if the system_dlkm archive is provided.
     *
     * @param device the {@link ITestDevice}
     * @param buildInfo the {@link IBuildInfo} the build info
     * @param tmpDir the temporary directory {@link File}
     * @throws TargetSetupError if there is an error building the image file.
     */
    @VisibleForTesting
    protected void buildGkiSystemDlkmImg(ITestDevice device, IBuildInfo buildInfo, File tmpDir)
            throws TargetSetupError {
        File systemDlkmStagingDir = null;

        if (buildInfo.getFile(mSystemDlkmArchiveName) == null) {
            /* Nothing to do here */
            return;
        }

        File systemDlkmArchive =
                getRequestedFile(
                        device,
                        mSystemDlkmArchiveName,
                        buildInfo.getFile(mSystemDlkmArchiveName),
                        tmpDir);
        if (systemDlkmArchive == null) {
            throw new TargetSetupError(
                    mSystemDlkmArchiveName
                            + " is not provided. Can not generate GKI system_dlkm.img.",
                    device.getDeviceDescriptor());
        }

        if (buildInfo.getFile(OTATOOLS_ZIP) == null) {
            throw new TargetSetupError(
                    OTATOOLS_ZIP + " is not provided. Can not generate GKI system_dlkm.img.",
                    device.getDeviceDescriptor());
        }

        File build_image =
                getRequestedFile(device, BUILD_IMAGE, buildInfo.getFile(OTATOOLS_ZIP), tmpDir);
        // Get build_image dependencies
        File mkuserimg_mke2fs =
                getRequestedFile(device, MKUSERIMG_MKE2FS, buildInfo.getFile(OTATOOLS_ZIP), tmpDir);
        File mke2fs = getRequestedFile(device, MKE2FS, buildInfo.getFile(OTATOOLS_ZIP), tmpDir);
        File e2fsdroid =
                getRequestedFile(device, E2FSDROID, buildInfo.getFile(OTATOOLS_ZIP), tmpDir);
        build_image.setExecutable(true, false);
        mkuserimg_mke2fs.setExecutable(true, false);
        mke2fs.setExecutable(true, false);
        e2fsdroid.setExecutable(true, false);

        try {
            systemDlkmStagingDir = extractSystemDlkmTarGzip(systemDlkmArchive);
            flattenSystemDlkm(device, systemDlkmStagingDir);

            // Create temporary files for the system_dlkm properties and file contexts
            File systemDlkmPropsFile = new File(tmpDir, "system_dlkm.props");
            File systemDlkmFileContexts = new File(tmpDir, "system_dlkm_file_contexts");

            // These are defaults GKI uses. We might want to pull this file from
            // a device build if devices require different properties.
            PrintWriter systemDlkmFileContextsWriter =
                    new PrintWriter(new FileWriter(systemDlkmFileContexts));
            systemDlkmFileContextsWriter.println(
                    "/system_dlkm(/.*)? u:object_r:system_dlkm_file:s0");
            systemDlkmFileContextsWriter.close();

            PrintWriter systemDlkmPropsPrintWriter =
                    new PrintWriter(new FileWriter(systemDlkmPropsFile));
            systemDlkmPropsPrintWriter.println("fs_type=ext4");
            systemDlkmPropsPrintWriter.println("use_dynamic_partition_size=true");
            systemDlkmPropsPrintWriter.println("ext_mkuserimg=mkuserimg_mke2fs");
            systemDlkmPropsPrintWriter.println("ext4_share_dup_blocks=true");
            systemDlkmPropsPrintWriter.println("extfs_rsv_pct=0");
            systemDlkmPropsPrintWriter.println("journal_size=0");
            systemDlkmPropsPrintWriter.println("mount_point=system_dlkm");
            systemDlkmPropsPrintWriter.println(
                    String.format("selinux_fc=%s", systemDlkmFileContexts.getAbsolutePath()));
            systemDlkmPropsPrintWriter.close();

            mSystemDlkmImg = new File(tmpDir, "system_dlkm.img");
            String buildImageCmd =
                    String.format(
                            "%s %s %s %s /dev/null",
                            build_image.getAbsolutePath(),
                            systemDlkmStagingDir.getAbsolutePath(),
                            systemDlkmPropsFile.getAbsolutePath(),
                            mSystemDlkmImg.getAbsolutePath());
            executeHostCommand(device, buildImageCmd);
            CLog.i("The GKI system_dlkm.img is of size %d", mSystemDlkmImg.length());
            if (mSystemDlkmImg.length() == 0) {
                throw new TargetSetupError(
                        "The build_image tool didn't generate a valid system_dlkm.img. (size=0)",
                        device.getDeviceDescriptor());
            }
            buildInfo.setFile(mSystemDlkmImageName, mSystemDlkmImg, "0");
        } catch (IOException e) {
            throw new TargetSetupError(
                    "Failed to generate GKI system_dlkm.img.", e, device.getDeviceDescriptor());
        } finally {
            // Clean up the system dlkm staging dir
            FileUtil.recursiveDelete(systemDlkmStagingDir);
        }

        File avbtool = getRequestedFile(device, AVBTOOL, buildInfo.getFile(OTATOOLS_ZIP), tmpDir);
        avbtool.setExecutable(true, false);
        String cmd =
                String.format(
                        "%s add_hashtree_footer --do_not_generate_fec "
                                + "--image %s "
                                + "--partition_name system_dlkm",
                        avbtool.getAbsolutePath(), mSystemDlkmImg.getAbsolutePath());
        executeHostCommand(device, cmd);
    }

    /**
     * Validate GKI boot image is expected. Throw exception if there is no valid boot.img.
     *
     * @param device the {@link ITestDevice}
     * @param buildInfo the {@link IBuildInfo} the build info
     * @param tmpDir the temporary directory {@link File}
     * @throws TargetSetupError if there is no valid gki boot.img
     */
    @VisibleForTesting
    protected void addHashFooter(ITestDevice device, IBuildInfo buildInfo, File tmpDir)
            throws TargetSetupError, DeviceNotAvailableException {
        if (mBootImg == null) {
            throw new TargetSetupError(
                    mGkiBootImageName + " is not provided. Can not add hash footer to it.",
                    device.getDeviceDescriptor());
        }
        if (buildInfo.getFile(OTATOOLS_ZIP) == null) {
            throw new TargetSetupError(
                    OTATOOLS_ZIP + " is not provided. Can not add hash footer to GKI boot.img.",
                    device.getDeviceDescriptor());
        }
        File avbtool = getRequestedFile(device, AVBTOOL, buildInfo.getFile(OTATOOLS_ZIP), tmpDir);
        avbtool.setExecutable(true, false);

        String android_version = device.getProperty("ro.build.version.release");
        if (Strings.isNullOrEmpty(android_version)) {
            throw new TargetSetupError(
                    "Can not get android version from property ro.build.version.release.",
                    device.getDeviceDescriptor());
        }
        String security_path_version = device.getProperty("ro.build.version.security_patch");
        if (Strings.isNullOrEmpty(security_path_version)) {
            throw new TargetSetupError(
                    "Can not get security path version from property"
                            + " ro.build.version.security_patch.",
                    device.getDeviceDescriptor());
        }

        String command = String.format("du -b %s", mBootImg.getAbsolutePath());
        CommandResult cmdResult = executeHostCommand(device, command);
        String partition_size = cmdResult.getStdout().split("\\s+")[0];
        CLog.i("Boot image partition size: %s", partition_size);
        String cmd =
                String.format(
                        "%s add_hash_footer --image %s --partition_size %s "
                                + "--partition_name boot "
                                + "--prop com.android.build.boot.os_version:%s "
                                + "--prop com.android.build.boot.security_patch:%s",
                        avbtool.getAbsolutePath(),
                        mBootImg.getAbsolutePath(),
                        partition_size,
                        android_version,
                        security_path_version);
        executeHostCommand(device, cmd);
    }

    /**
     * Helper method to execute host command.
     *
     * @param device the {@link ITestDevice}
     * @param command the command string
     * @return the CommandResult
     * @throws TargetSetupError, DeviceNotAvailableException
     */
    private CommandResult executeHostCommand(ITestDevice device, final String command)
            throws TargetSetupError {
        final CommandResult result = getRunUtil().runTimedCmd(300000L, command.split("\\s+"));
        switch (result.getStatus()) {
            case SUCCESS:
                CLog.i(
                        "Command %s finished successfully, stdout = [%s].",
                        command, result.getStdout().trim());
                break;
            case FAILED:
                throw new TargetSetupError(
                        String.format(
                                "Command %s failed, stdout = [%s], stderr = [%s].",
                                command, result.getStdout().trim(), result.getStderr().trim()),
                        device.getDeviceDescriptor());
            case TIMED_OUT:
                throw new TargetSetupError(
                        String.format("Command %s timed out.", command),
                        device.getDeviceDescriptor());
            case EXCEPTION:
                throw new TargetSetupError(
                        String.format("Exception occurred when running command %s.", command),
                        device.getDeviceDescriptor());
        }
        return result;
    }

    /**
     * Get the requested file from the source file (zip or folder) by requested file name.
     *
     * <p>The provided source file can be a zip file. The method will unzip it to tempary directory
     * and find the requested file by the provided file name.
     *
     * <p>The provided source file can be a file folder. The method will find the requestd file by
     * the provided file name.
     *
     * @param device the {@link ITestDevice}
     * @param requestedFileName the requeste file name String
     * @param sourceFile the source file
     * @return the file that is specified by the requested file name
     * @throws TargetSetupError
     */
    @VisibleForTesting
    protected File getRequestedFile(
            ITestDevice device, String requestedFileName, File sourceFile, File tmpDir)
            throws TargetSetupError {
        File requestedFile = null;
        String baseFileName = new File(requestedFileName).getName();
        String subdirPathName = new File(requestedFileName).getParent();

        if (sourceFile.getName().endsWith(".zip")) {
            try (ZipFile sourceZipFile = new ZipFile(sourceFile)) {
                File destDir =
                        FileUtil.createNamedTempDir(
                                tmpDir, FileUtil.getBaseName(sourceFile.getName()) + "_zip");
                File subdir = null;
                if (subdirPathName != null && !subdirPathName.isEmpty()) {
                    subdir = FileUtil.createNamedTempDir(destDir, subdirPathName);
                }
                requestedFile = new File(subdir != null ? subdir : destDir, baseFileName);
                ZipUtil2.extractFileFromZip(sourceZipFile, requestedFileName, requestedFile);
                if (!requestedFile.exists()) {
                    /* Let's search for the file within the zip archive in case of a regex
                     * filename before giving up. */
                    final Enumeration<ZipArchiveEntry> entries = sourceZipFile.getEntries();
                    while (entries.hasMoreElements()) {
                        final ZipArchiveEntry entry = entries.nextElement();
                        if (entry.isDirectory() || !entry.getName().matches(requestedFileName)) {
                            continue;
                        }
                        requestedFile =
                                new File(subdir != null ? subdir : destDir, entry.getName());
                        FileUtil.writeToFile(sourceZipFile.getInputStream(entry), requestedFile);
                        break;
                    }
                }
            } catch (IOException e) {
                throw new TargetSetupError(
                        String.format("Fail to get %s from %s", requestedFileName, sourceFile),
                        e,
                        device.getDeviceDescriptor());
            }
        } else if (sourceFile.isDirectory()) {
            requestedFile = FileUtil.findFile(sourceFile, requestedFileName);
        } else {
            requestedFile = sourceFile;
        }
        if (requestedFile == null || !requestedFile.exists()) {
            throw new TargetSetupError(
                    String.format(
                            "Requested file with file_name %s does not exist in provided %s.",
                            requestedFileName, sourceFile),
                    device.getDeviceDescriptor());
        }
        return requestedFile;
    }

    /**
     * Helper method to execute a fastboot command.
     *
     * @param device the {@link ITestDevice} to execute command on
     * @param cmdArgs the arguments to provide to fastboot
     * @return String the stderr output from command if non-empty. Otherwise returns the stdout Some
     *     fastboot commands are weird in that they dump output to stderr on success case
     * @throws DeviceNotAvailableException if device is not available
     * @throws TargetSetupError if fastboot command fails
     */
    private String executeFastbootCmd(ITestDevice device, String... cmdArgs)
            throws DeviceNotAvailableException, TargetSetupError {
        CLog.i(
                "Execute fastboot command %s on %s",
                Arrays.toString(cmdArgs), device.getSerialNumber());
        CommandResult result = device.executeLongFastbootCommand(cmdArgs);
        CLog.v("fastboot stdout: " + result.getStdout());
        CLog.v("fastboot stderr: " + result.getStderr());
        CommandStatus cmdStatus = result.getStatus();
        // fastboot command line output is in stderr even for successful run
        if (result.getStderr().contains("FAILED")) {
            // if output contains "FAILED", just override to failure
            cmdStatus = CommandStatus.FAILED;
        }
        if (cmdStatus != CommandStatus.SUCCESS) {
            throw new TargetSetupError(
                    String.format(
                            "fastboot command %s failed in device %s. stdout: %s, stderr: %s",
                            Arrays.toString(cmdArgs),
                            device.getSerialNumber(),
                            result.getStdout(),
                            result.getStderr()),
                    device.getDeviceDescriptor());
        }
        if (result.getStderr().length() > 0) {
            return result.getStderr();
        } else {
            return result.getStdout();
        }
    }
}
