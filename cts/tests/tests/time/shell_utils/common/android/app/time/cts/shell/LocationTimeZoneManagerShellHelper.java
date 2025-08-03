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

import static org.junit.Assume.assumeTrue;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Objects;

/**
 * A class for interacting with the {@code location_time_zone_manager} service via the shell "cmd"
 * command-line interface.
 */
public class LocationTimeZoneManagerShellHelper {

    /**
     * The name of the service for shell commands.
     */
    private static final String SERVICE_NAME = "location_time_zone_manager";

    /**
     * A shell command that starts the service (after stop).
     */
    private static final String SHELL_COMMAND_START = "start";

    /**
     * A shell command that stops the service.
     */
    private static final String SHELL_COMMAND_STOP = "stop";

    /**
     * A shell command that clears recorded provider state information during tests.
     */
    private static final String SHELL_COMMAND_CLEAR_RECORDED_PROVIDER_STATES =
            "clear_recorded_provider_states";

    /**
     * A shell command that tells the service to dump its current state.
     */
    private static final String SHELL_COMMAND_DUMP_STATE = "dump_state";

    /**
     * Option for {@link #SHELL_COMMAND_DUMP_STATE} that tells it to dump state as a binary proto.
     */
    private static final String DUMP_STATE_OPTION_PROTO = "proto";

    /** A shell command that starts the location_time_zone_manager with named test providers. */
    public static final String SHELL_COMMAND_START_WITH_TEST_PROVIDERS =
            "start_with_test_providers";

    /**
     * The token that can be passed to {@link #SHELL_COMMAND_START_WITH_TEST_PROVIDERS} to indicate
     * there is no provider.
     */
    public static final String NULL_PACKAGE_NAME_TOKEN = "@null";

    private static final String SHELL_CMD_PREFIX = "cmd " + SERVICE_NAME + " ";

    private final DeviceShellCommandExecutor mShellCommandExecutor;

    public LocationTimeZoneManagerShellHelper(DeviceShellCommandExecutor shellCommandExecutor) {
        mShellCommandExecutor = Objects.requireNonNull(shellCommandExecutor);
    }

    /**
     * Throws an {@link org.junit.AssumptionViolatedException} if the location_time_zone_manager
     * service is not found. The service can be turned off in config, so this can be used to prevent
     * CTS tests that need it from running.
     */
    public void assumeLocationTimeZoneManagerIsPresent() throws Exception {
        assumeTrue(isLocationTimeZoneManagerPresent());
    }

    /**
     * Returns {@code false} if the location_time_zone_manager service is not found.
     */
    public boolean isLocationTimeZoneManagerPresent() throws Exception {
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

    /** Executes "start". Starts the service. */
    public void start() throws Exception {
        mShellCommandExecutor.executeToTrimmedString(SHELL_CMD_PREFIX + SHELL_COMMAND_START);
    }

    /** Executes "stop". Stops the service. */
    public void stop() throws Exception {
        mShellCommandExecutor.executeToTrimmedString(SHELL_CMD_PREFIX + SHELL_COMMAND_STOP);
    }

    /** Executes "clear_recorded_provider_states". */
    public void clearRecordedProviderStates() throws Exception {
        String cmd = SHELL_COMMAND_CLEAR_RECORDED_PROVIDER_STATES;
        mShellCommandExecutor.executeToTrimmedString(SHELL_CMD_PREFIX + cmd);
    }

    /**
     * Executes "dump_state". Raw proto bytes are returned as host protos tend to use "full" proto,
     * device protos use "lite".
     **/
    public byte[] dumpState() throws Exception {
        String cmd = String.format("%s --%s", SHELL_COMMAND_DUMP_STATE, DUMP_STATE_OPTION_PROTO);
        return mShellCommandExecutor.executeToBytes(SHELL_CMD_PREFIX + cmd);
    }

    /** Executes "start_with_test_providers". */
    public void startWithTestProviders(String testPrimaryLocationTimeZoneProviderPackageName,
            String testSecondaryLocationTimeZoneProviderPackageName, boolean recordProviderStates)
            throws Exception {
        testPrimaryLocationTimeZoneProviderPackageName =
                replaceNullPackageNameWithToken(testPrimaryLocationTimeZoneProviderPackageName);
        testSecondaryLocationTimeZoneProviderPackageName =
                replaceNullPackageNameWithToken(testSecondaryLocationTimeZoneProviderPackageName);
        String cmd = String.format("%s %s %s %s",
                SHELL_COMMAND_START_WITH_TEST_PROVIDERS,
                testPrimaryLocationTimeZoneProviderPackageName,
                testSecondaryLocationTimeZoneProviderPackageName,
                recordProviderStates);
        mShellCommandExecutor.executeToBytes(SHELL_CMD_PREFIX + cmd);
    }

    private static String replaceNullPackageNameWithToken(String packageName) {
        return packageName == null ? NULL_PACKAGE_NAME_TOKEN : packageName;
    }
}
