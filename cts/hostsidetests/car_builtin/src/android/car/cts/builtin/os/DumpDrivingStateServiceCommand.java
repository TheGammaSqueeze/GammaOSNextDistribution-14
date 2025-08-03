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

public final class DumpDrivingStateServiceCommand extends CtsCarShellCommand {
    private static final String COMMAND_NAME =
            "dumpsys car_service --services CarDrivingStateService";

    public static final String[] REMOTE_CALLBACK_LIST_DUMP_HEADERS = {
        "callbacks: ",
        "killed: ",
        "broadcasts count: "
    };

    private boolean mHasRemoteCallbackListDump;

    public DumpDrivingStateServiceCommand(ITestDevice device) {
        super(COMMAND_NAME, device);
    }

    public boolean hasRemoteCallbackListDump() {
        return mHasRemoteCallbackListDump;
    }

    @Override
    protected void parseCommandReturn() throws Exception {
        if (mCommandArgs.length != 0) {
            throw new IllegalArgumentException("No argument is expected");
        }

        CLog.d(mCommand + " command returns: " + mCommandReturn);

        for (String header : REMOTE_CALLBACK_LIST_DUMP_HEADERS) {
            if (!mCommandReturn.contains(header)) {
                mHasRemoteCallbackListDump = false;
                return;
            }
        }
        mHasRemoteCallbackListDump = true;
    }
}
