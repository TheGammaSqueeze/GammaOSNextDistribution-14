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
package com.android.adservices.common;

import static com.android.adservices.common.TestDeviceHelper.getProperty;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

/** Host-side implementation of {@link SystemPropertiesHelper.Interface}. */
final class HostSideSystemPropertiesHelper extends SystemPropertiesHelper.Interface {

    private static final Logger sLogger =
            new Logger(ConsoleLogger.getInstance(), HostSideSystemPropertiesHelper.class);

    private static final HostSideSystemPropertiesHelper sInstance =
            new HostSideSystemPropertiesHelper();

    static HostSideSystemPropertiesHelper getInstance() {
        return sInstance;
    }

    private HostSideSystemPropertiesHelper() {
        super(ConsoleLogger.getInstance());
    }

    @Override
    public String get(String name) {
        return getProperty(name);
    }

    // cmdFmt must be final because it's being passed to a method taking @FormatString
    @Override
    @FormatMethod
    protected String runShellCommand(
            @FormatString final String cmdFmt, @Nullable Object... cmdArgs) {
        return TestDeviceHelper.runShellCommand(cmdFmt, cmdArgs);
    }

    @Override
    public String toString() {
        return HostSideSystemPropertiesHelper.class.getSimpleName();
    }
}
