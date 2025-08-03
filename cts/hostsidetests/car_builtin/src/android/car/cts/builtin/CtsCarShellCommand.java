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

package android.car.cts.builtin;

import com.android.tradefed.device.ITestDevice;

/**
 * Base class for all Car Builtin API test related shell command invocation.
 *
 * It is the extended subclass command to construct the shell command string and decide
 * if the command's return is successful or not. Further, it is the extended subclass command
 * to extra all necessary information from the return string if needed.
 */
public abstract class CtsCarShellCommand {
    private final ITestDevice mDevice;

    protected final String mCommand;
    protected String[] mCommandArgs;
    protected String mCommandReturn;

    protected CtsCarShellCommand(String commandName, ITestDevice device) {
        mCommand = commandName;
        mDevice = device;
    }

    public CtsCarShellCommand executeWith(String... args) throws Exception {
        mCommandArgs = args;

        String cmd = mCommand;
        if (mCommandArgs != null && mCommandArgs.length > 0) {
            cmd = mCommand + " " + String.join(" ", mCommandArgs);
        }
        mCommandReturn = mDevice.executeShellCommand(cmd).trim();
        parseCommandReturn();
        return this;
    }

    public boolean returnStartsWith(String str) throws Exception {
        if (mCommandReturn == null) {
            throw new Exception("command return is null. not executed?");
        }
        return mCommandReturn.startsWith(str);
    }

    protected abstract void parseCommandReturn() throws Exception;
}
