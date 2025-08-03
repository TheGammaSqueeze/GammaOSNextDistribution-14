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

package android.car.cts.builtin.pm;

import android.car.cts.builtin.CtsCarShellCommand;

import com.android.tradefed.device.ITestDevice;

public final class ComponentEnabledStateCommand extends CtsCarShellCommand {
    public static final String COMPONENT_ENABLED_STATE_DEFAULT = "COMPONENT_ENABLED_STATE_DEFAULT";
    public static final String COMPONENT_ENABLED_STATE_ENABLED = "COMPONENT_ENABLED_STATE_ENABLED";
    public static final String COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED =
            "COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED";
    public static final String COMPONENT_ENABLED_STATE_UNSUPPORTED =
            "COMPONENT_ENABLED_STATE_UNSUPPORTED";

    public static final String COMMAND_ACTION_GET = "get";
    public static final String COMMAND_ACTION_ENABLE = "enable";
    public static final String COMMAND_ACTION_DISABLE_UNTIL_USED = "disable_until_used";
    public static final String COMMAND_ACTION_DEFAULT = "default";

    private static final String COMMAND_NAME = "cmd car_service control-component-enabled-state";
    private static final String NEW_STATE_RETURN_HEADER = "New State: ";
    private static final String GET_STATE_RETURN_HEADER = "Current State: ";

    private String mNewState;
    private String mCurrentState;

    public ComponentEnabledStateCommand(ITestDevice device) {
        super(COMMAND_NAME, device);
    }

    public String getNewState() {
        return mNewState;
    }

    public String getCurrentState() {
        return mCurrentState;
    }

    protected void parseCommandReturn() throws Exception {
        if (mCommandArgs.length != 2) {
            throw new IllegalArgumentException("Expected two arguments: action and pkg name");
        }

        switch (mCommandArgs[0]) {
            case COMMAND_ACTION_GET:
                if (!mCommandReturn.startsWith(GET_STATE_RETURN_HEADER)) {
                    throw new Exception("get enabled state error: " + mCommandReturn);
                }
                mCurrentState = mCommandReturn.substring(GET_STATE_RETURN_HEADER.length()).trim();
                break;
            case COMMAND_ACTION_ENABLE:
            case COMMAND_ACTION_DEFAULT:
            case COMMAND_ACTION_DISABLE_UNTIL_USED:
                if (!mCommandReturn.startsWith(NEW_STATE_RETURN_HEADER)) {
                    throw new Exception("change enabled state error: " + mCommandReturn);
                }
                mNewState = mCommandReturn.substring(NEW_STATE_RETURN_HEADER.length()).trim();
                break;
            default:
                throw new IllegalArgumentException("Unsupported action");
        }
    }
}
