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

package android.car.cts.builtin.user;

import android.car.cts.builtin.CtsCarShellCommand;

import com.android.tradefed.device.ITestDevice;

public final class CarInitialUserCommand extends CtsCarShellCommand {

    private static final String COMMAND_NAME = "cmd car_service get-initial-user";

    // the value from UserHandler.USER_NULL
    private static final int INVALID_USER_ID = -10_000;

    private int mCarInitialUser = INVALID_USER_ID;

    public CarInitialUserCommand(ITestDevice device) {
        super(COMMAND_NAME, device);
    }

    public int getCarInitialUser() {
        return mCarInitialUser;
    }

    @Override
    protected void parseCommandReturn() throws Exception {
        mCarInitialUser = Integer.parseInt(mCommandReturn.trim());
    }
}
