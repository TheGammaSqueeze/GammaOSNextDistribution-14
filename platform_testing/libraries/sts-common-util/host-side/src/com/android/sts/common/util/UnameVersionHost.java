/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.sts.common.util;

import com.android.sts.common.CommandUtil;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.CommandResult;

import java.time.LocalDate;
import java.util.Optional;

public final class UnameVersionHost {

    private final ITestDevice device;
    private String unameVersion = null;

    public UnameVersionHost(ITestDevice device) {
        this.device = device;
    }

    public final String getUnameVersion() throws DeviceNotAvailableException {
        if (this.unameVersion != null) {
            return this.unameVersion;
        }

        // https://android.googlesource.com/platform/system/core/+/master/shell_and_utilities/README.md
        // uname is part of Android since 6.0 Marshmallow
        CommandResult res = CommandUtil.runAndCheck(device, "uname -v");
        this.unameVersion = res.getStdout().trim();
        return this.unameVersion;
    }

    public final Optional<LocalDate> parseBuildTimestamp() throws DeviceNotAvailableException {
        return UnameVersion.parseBuildTimestamp(getUnameVersion());
    }
}
