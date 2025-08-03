/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.result.error.InfraErrorIdentifier;

import java.io.File;

/** A helper class for FUSE operations. */
public class FuseUtil {
    static final long FUSE_ZIP_TIMEOUT_MILLIS = 5 * 60 * 1000;

    private static Boolean sCanMountZip;

    @VisibleForTesting
    static void resetCanMountZip() {
        sCanMountZip = null;
    }

    @VisibleForTesting
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * Returns whether zip mounting is supported.
     *
     * <p>Zip mounting is only supported when fuse-zip is available.
     *
     * @return true if zip mounting is supported. Otherwise false.
     */
    public boolean canMountZip() {
        if (sCanMountZip == null) {
            CommandResult res =
                    getRunUtil().runTimedCmd(FUSE_ZIP_TIMEOUT_MILLIS, "test", "-c", "/dev/fuse");
            CommandResult res2 =
                    getRunUtil().runTimedCmd(FUSE_ZIP_TIMEOUT_MILLIS, "which", "fuse-zip");
            sCanMountZip =
                    res.getStatus().equals(CommandStatus.SUCCESS)
                            && res2.getStatus().equals(CommandStatus.SUCCESS)
                            && !res2.getStdout().isEmpty();
        }
        return sCanMountZip.booleanValue();
    }

    /**
     * Mount a zip file as a file system in read-only mode.
     *
     * @param zipFile a zip file to mount.
     * @param mountDir a mount point.
     */
    public void mountZip(File zipFile, File mountDir) {
        CommandResult res =
                getRunUtil()
                        .runTimedCmd(
                                FUSE_ZIP_TIMEOUT_MILLIS,
                                "fuse-zip",
                                "-r",
                                zipFile.getAbsolutePath(),
                                mountDir.getAbsolutePath());
        if (!res.getStatus().equals(CommandStatus.SUCCESS)) {
            throw new HarnessRuntimeException(
                    String.format("Failed to mount %s: %s", zipFile, res.getStderr()),
                    InfraErrorIdentifier.LAB_HOST_FILESYSTEM_ERROR);
        }
    }

    /**
     * Unmount a mounted zip file.
     *
     * @param mountDir a mount point.
     */
    public void unmountZip(File mountDir) {
        CommandResult res =
                getRunUtil()
                        .runTimedCmd(
                                FUSE_ZIP_TIMEOUT_MILLIS,
                                "fusermount",
                                "-u",
                                mountDir.getAbsolutePath());
        if (!res.getStatus().equals(CommandStatus.SUCCESS)) {
            LogUtil.CLog.w("Failed to unmount %s: %s", mountDir, res.getStderr());
        }
    }
}
