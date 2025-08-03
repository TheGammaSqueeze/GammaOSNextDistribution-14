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

package com.android.adservices.common;

public final class HostSideAdServicesShellCommandHelper
        extends AbstractAdServicesShellCommandHelper {
    public HostSideAdServicesShellCommandHelper() {
        super(ConsoleLogger.getInstance());
    }

    @Override
    protected String runShellCommand(String cmd) {
        return TestDeviceHelper.runShellCommand("%s", cmd);
    }

    @Override
    protected CommandResult runShellCommandRwe(String cmd) {
        return TestDeviceHelper.runShellCommandRwe("%s", cmd);
    }

    @Override
    protected int getDeviceApiLevel() {
        return TestDeviceHelper.getApiLevel();
    }
}
