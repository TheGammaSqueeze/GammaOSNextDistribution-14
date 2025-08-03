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
package com.android.tradefed.util;

import com.android.tradefed.build.BuildInfoKey;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.remote.ExtendedFile;
import com.android.tradefed.invoker.logger.CurrentInvocation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.targetprep.DeviceFlashPreparer;
import com.android.tradefed.targetprep.FlashingResourcesParser;
import com.android.tradefed.targetprep.IFlashingResourcesParser;
import com.android.tradefed.targetprep.TargetSetupError;

import com.google.common.base.Strings;

import java.io.File;
import java.util.Map;

/**
 * This utility helps setting the appropriate version of artifacts so they can be flashed via {@link
 * DeviceFlashPreparer}.
 */
public class FlashingResourceUtil {

    /**
     * Handle the files needed for flashing and setting their appropriate value in the BuildInfo
     *
     * @param info The {IBuildInfo} constructed
     * @param keyAndFiles The files downloaded considered
     * @return True if flashing was setup
     * @throws BuildRetrievalError
     */
    public static boolean setUpFlashingResources(IBuildInfo info, Map<String, File> keyAndFiles)
            throws BuildRetrievalError {
        IFlashingResourcesParser flashingResourcesParser = null;
        String deviceImageFlavor = null;
        String buildId = "downloaded";

        // Handle device image first
        File deviceImage = keyAndFiles.get(BuildInfoKey.BuildInfoFileKey.DEVICE_IMAGE.getFileKey());
        if (deviceImage == null) {
            return false;
        }
        if (!(deviceImage instanceof ExtendedFile)) {
            return false;
        }
        String bid = ((ExtendedFile) deviceImage).getBuildId();
        if (!Strings.isNullOrEmpty(bid)) {
            buildId = bid;
        }
        try {
            flashingResourcesParser = new FlashingResourcesParser(deviceImage);
            deviceImageFlavor = ((ExtendedFile) deviceImage).getBuildTarget();
        } catch (TargetSetupError e) {
            throw new BuildRetrievalError(
                    "Failed to get image info from android-info.txt",
                    e,
                    InfraErrorIdentifier.ARTIFACT_DOWNLOAD_ERROR);
        }
        info.setFile(BuildInfoKey.BuildInfoFileKey.DEVICE_IMAGE, deviceImage, buildId);
        if (CurrentInvocation.getInvocationFiles() != null) {
            CurrentInvocation.getInvocationFiles()
                    .put(BuildInfoKey.BuildInfoFileKey.DEVICE_IMAGE.getFileKey(), deviceImage);
        }
        keyAndFiles.remove(BuildInfoKey.BuildInfoFileKey.DEVICE_IMAGE.getFileKey());
        // Handle bootloader
        File bootloader =
                keyAndFiles.get(BuildInfoKey.BuildInfoFileKey.BOOTLOADER_IMAGE.getFileKey());
        handleBootloader(info, bootloader, flashingResourcesParser);
        keyAndFiles.remove(BuildInfoKey.BuildInfoFileKey.BOOTLOADER_IMAGE.getFileKey());

        // Handle baseband
        File baseband = keyAndFiles.get(BuildInfoKey.BuildInfoFileKey.BASEBAND_IMAGE.getFileKey());
        handleBaseband(info, baseband, flashingResourcesParser);
        keyAndFiles.remove(BuildInfoKey.BuildInfoFileKey.BASEBAND_IMAGE.getFileKey());

        if (info instanceof IDeviceBuildInfo && deviceImageFlavor != null) {
            ((IDeviceBuildInfo) info).setDeviceBuildFlavor(deviceImageFlavor);
        }
        return true;
    }

    private static void handleBaseband(
            IBuildInfo info, File baseband, IFlashingResourcesParser flashingResourcesParser) {
        if (baseband == null) {
            return;
        }
        String buildId = flashingResourcesParser.getRequiredBasebandVersion();
        info.setFile(BuildInfoKey.BuildInfoFileKey.BASEBAND_IMAGE, baseband, buildId);
        if (CurrentInvocation.getInvocationFiles() != null) {
            CurrentInvocation.getInvocationFiles()
                    .put(BuildInfoKey.BuildInfoFileKey.BASEBAND_IMAGE.getFileKey(), baseband);
        }
    }

    private static void handleBootloader(
            IBuildInfo info, File bootloader, IFlashingResourcesParser flashingResourcesParser) {
        if (bootloader == null) {
            return;
        }
        String buildId = flashingResourcesParser.getRequiredBootloaderVersion();
        CLog.d("Bootloader from file: %s with version: %s", bootloader, buildId);
        info.setFile(BuildInfoKey.BuildInfoFileKey.BOOTLOADER_IMAGE, bootloader, buildId);
        if (CurrentInvocation.getInvocationFiles() != null) {
            CurrentInvocation.getInvocationFiles()
                    .put(BuildInfoKey.BuildInfoFileKey.BOOTLOADER_IMAGE.getFileKey(), bootloader);
        }
    }
}
