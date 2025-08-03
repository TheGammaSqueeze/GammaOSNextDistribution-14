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

import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.invoker.ExecutionFiles.FilesKey;
import com.android.tradefed.log.LogUtil.CLog;

import java.io.File;

/** A utility class for adb operations. */
public class AdbUtils {

    private static final String PATH_VAR = "PATH";
    private static final long PATH_TIMEOUT_MS = 60000L;

    /**
     * Updates $PATH if a special adb version is used.
     *
     * @param testInfo A {@link TestInformation} object.
     * @param runUtil An {@link IRunUtil} object used to run system command.
     * @param adbPath The path to the adb binary.
     */
    public static void updateAdb(TestInformation testInfo, IRunUtil runUtil, String adbPath) {
        File updatedAdb = testInfo.executionFiles().get(FilesKey.ADB_BINARY);
        if (updatedAdb == null) {
            // Don't check if it's the adb on the $PATH
            if (!adbPath.equals("adb")) {
                updatedAdb = new File(adbPath);
                if (!updatedAdb.exists()) {
                    CLog.w(
                            String.format(
                                    "adb path %s doesn't exist. Fall back on the abd in $PATH",
                                    adbPath));
                    updatedAdb = null;
                }
            } else {
                CLog.d("Use the adb in the $PATH.");
            }
        }
        if (updatedAdb == null) {
            return;
        }
        CLog.d("Testing with adb binary at: %s", updatedAdb);
        // If a special adb version is used, pass it to the PATH
        CommandResult pathResult =
                runUtil.runTimedCmd(PATH_TIMEOUT_MS, "/bin/bash", "-c", "echo $" + PATH_VAR);
        if (!CommandStatus.SUCCESS.equals(pathResult.getStatus())) {
            throw new RuntimeException(
                    String.format(
                            "Failed to get the $PATH. status: %s, stdout: %s, stderr: %s",
                            pathResult.getStatus(),
                            pathResult.getStdout(),
                            pathResult.getStderr()));
        }
        // Include the directory of the adb on the PATH to be used.
        String path =
                String.format(
                        "%s:%s",
                        updatedAdb.getParentFile().getAbsolutePath(),
                        pathResult.getStdout().trim());
        CLog.d("Using $PATH with updated adb: %s", path);
        runUtil.setEnvVariable(PATH_VAR, path);
        // Log the version of adb seen
        CommandResult versionRes = runUtil.runTimedCmd(PATH_TIMEOUT_MS, "adb", "version");
        CLog.d("%s", versionRes.getStdout());
        CLog.d("%s", versionRes.getStderr());
    }
}
