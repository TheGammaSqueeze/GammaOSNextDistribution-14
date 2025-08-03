/*
 * Copyright (C) 2021 The Android Open Source Project
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
package android.app.time.cts.shell;

import static android.app.time.cts.shell.DeviceShellCommandExecutor.parseBytesAsBoolean;

import java.util.Objects;

/**
 * A class for interacting with the {@code location} service via the shell "cmd" command-line
 * interface.
 */
public class LocationShellHelper {

    /**
     * The name of the service for shell commands,
     */
    private static final String SERVICE_NAME = "location";

    /**
     * A shell command that sets the current user's "location enabled" setting value.
     */
    private static final String SHELL_COMMAND_SET_LOCATION_ENABLED = "set-location-enabled";

    /**
     * A shell command that gets the current user's "location enabled" setting value.
     */
    private static final String SHELL_COMMAND_IS_LOCATION_ENABLED = "is-location-enabled";

    private static final String SHELL_CMD_PREFIX = "cmd " + SERVICE_NAME + " ";

    private final DeviceShellCommandExecutor mShellCommandExecutor;

    public LocationShellHelper(DeviceShellCommandExecutor shellCommandExecutor) {
        mShellCommandExecutor = Objects.requireNonNull(shellCommandExecutor);
    }

    /** Executes "is-location-enabled". */
    public boolean isLocationEnabledForCurrentUser() throws Exception {
        return mShellCommandExecutor.executeToBoolean(
                SHELL_CMD_PREFIX + SHELL_COMMAND_IS_LOCATION_ENABLED);
    }

    /** Executes "set-location-enabled". */
    public void setLocationEnabledForCurrentUser(boolean enabled) throws Exception {
        String cmd = String.format("%s %s", SHELL_COMMAND_SET_LOCATION_ENABLED, enabled);
        mShellCommandExecutor.executeToTrimmedString(SHELL_CMD_PREFIX + cmd);
    }
}
