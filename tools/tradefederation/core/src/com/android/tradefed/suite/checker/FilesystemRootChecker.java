/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tradefed.suite.checker;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.suite.checker.StatusCheckerResult.CheckStatus;
import com.android.tradefed.util.CommandResult;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FilesystemRootChecker implements ISystemStatusChecker {

    static final String FIND_ROOT_COMMAND = "find /data/local/tmp/ -user root";

    /** {@inheritDoc} */
    @Override
    public StatusCheckerResult preExecutionCheck(ITestDevice device)
            throws DeviceNotAvailableException {
        List<String> files = getRootPaths(device);
        if (!files.isEmpty()) {
            StatusCheckerResult result = new StatusCheckerResult(CheckStatus.FAILED);
            result.setErrorMessage(
                    String.format(
                            "This module started with root-owned paths: %s", files.toString()));
            return result;
        }
        return new StatusCheckerResult(CheckStatus.SUCCESS);
    }

    /** {@inheritDoc} */
    @Override
    public StatusCheckerResult postExecutionCheck(ITestDevice device)
            throws DeviceNotAvailableException {
        List<String> files = getRootPaths(device);
        if (!files.isEmpty()) {
            StatusCheckerResult result = new StatusCheckerResult(CheckStatus.FAILED);
            result.setErrorMessage(
                    String.format(
                            "This module ended with root-owned paths; check tests or module setup:"
                                    + " %s",
                            files.toString()));
            return result;
        }
        return new StatusCheckerResult(CheckStatus.SUCCESS);
    }

    /** Return a list of files and folders owned by root in /data/local/tmp/ */
    private static List<String> getRootPaths(ITestDevice device)
            throws DeviceNotAvailableException {
        CommandResult rootOwnedPathsResult = device.executeShellV2Command(FIND_ROOT_COMMAND);
        String rootOwnedPaths = rootOwnedPathsResult.getStdout().trim();
        if (rootOwnedPaths.isEmpty()) {
            // "".split("\n") returns [""] so return empty list instead
            return Collections.emptyList();
        }
        return Arrays.asList(rootOwnedPaths.split("\n"));
    }
}
