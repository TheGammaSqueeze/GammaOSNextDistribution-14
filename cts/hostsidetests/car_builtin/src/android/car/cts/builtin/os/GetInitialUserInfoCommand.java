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

package android.car.cts.builtin.os;

import android.car.cts.builtin.CtsCarShellCommand;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

public final class GetInitialUserInfoCommand extends CtsCarShellCommand {

    public static final String OK_STATUS_RETURN_HEADER = "Call status: OK";
    private static final String COMMAND_NAME = "cmd car_service get-initial-user-info FIRST_BOOT";

    public GetInitialUserInfoCommand(ITestDevice device) {
        super(COMMAND_NAME, device);
    }

    @Override
    protected void parseCommandReturn() throws Exception {
        if (mCommandArgs.length != 0) {
            throw new IllegalArgumentException("No argument is expected");
        }
        CLog.d(mCommand + " command returns: " + mCommandReturn);
    }
}
