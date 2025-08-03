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
package android.app.time.cts.shell;

import static org.junit.Assume.assumeTrue;

import java.io.BufferedReader;
import java.io.StringReader;
import java.net.URI;
import java.util.Objects;

/**
 * A class for interacting with the {@code network_time_update_service} service via the shell "cmd"
 * command-line interface.
 */
public class NetworkTimeUpdateServiceShellHelper {

    /**
     * The name of the service for shell commands.
     */
    private static final String SERVICE_NAME = "network_time_update_service";

    private static final String SHELL_CMD_PREFIX = "cmd " + SERVICE_NAME + " ";

    private final DeviceShellCommandExecutor mShellCommandExecutor;

    public NetworkTimeUpdateServiceShellHelper(DeviceShellCommandExecutor shellCommandExecutor) {
        mShellCommandExecutor = Objects.requireNonNull(shellCommandExecutor);
    }

    /**
     * Throws an {@link org.junit.AssumptionViolatedException} if the network_time_update_service
     * service is not found.
     */
    public void assumeNetworkTimeUpdateServiceIsPresent() throws Exception {
        assumeTrue(isNetworkTimeUpdateServicePresent());
    }

    /**
     * Returns {@code false} if the network_time_update_service service is not found.
     */
    public boolean isNetworkTimeUpdateServicePresent() throws Exception {
        // Look for the service name in "cmd -l".
        String serviceList = mShellCommandExecutor.executeToString("cmd -l");
        try (BufferedReader reader = new BufferedReader(new StringReader(serviceList))) {
            String serviceName;
            while ((serviceName = reader.readLine()) != null) {
                serviceName = serviceName.trim();
                if (SERVICE_NAME.equals(serviceName)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Sets time server configuration for use during tests.
     * See {@link #resetServerConfigForTests()}.
     */
    public void setServerConfigForTests(URI uri, long timeoutMillis) throws Exception {
        mShellCommandExecutor.executeToTrimmedString(
                SHELL_CMD_PREFIX + " set_server_config_for_tests --server " + uri
                        + " --timeout_millis " + timeoutMillis);
    }

    /** Restores time server configuration after {@link #setServerConfigForTests(URI, long)}. */
    public void resetServerConfigForTests() throws Exception {
        mShellCommandExecutor.executeToTrimmedString(
                SHELL_CMD_PREFIX + " reset_server_config_for_tests");
    }

    /** Tries to refresh the time from the time server. Returns true if successful. */
    public boolean forceRefresh() throws Exception {
        return mShellCommandExecutor.executeToBoolean(SHELL_CMD_PREFIX + " force_refresh");
    }
}
