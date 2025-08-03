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

import java.util.Objects;

/**
 * A class for interacting with the {@code time_zone_detector} service via the shell "cmd"
 * command-line interface.
 */
public final class TimeZoneDetectorShellHelper {

    /**
     * The name of the service for shell commands.
     */
    private static final String SERVICE_NAME = "time_zone_detector";

    /**
     * A shell command that prints the current "auto time zone detection" global setting value.
     */
    private static final String SHELL_COMMAND_IS_AUTO_DETECTION_ENABLED =
            "is_auto_detection_enabled";

    /**
     * A shell command that sets the current "auto time zone detection" global setting value.
     */
    private static final String SHELL_COMMAND_SET_AUTO_DETECTION_ENABLED =
            "set_auto_detection_enabled";

    /**
     * A shell command that prints whether the telephony-based time zone detection feature is
     * supported on the device.
     */
    private static final String SHELL_COMMAND_IS_TELEPHONY_DETECTION_SUPPORTED =
            "is_telephony_detection_supported";

    /**
     * A shell command that prints whether the geolocation-based time zone detection feature is
     * supported on the device.
     */
    private static final String SHELL_COMMAND_IS_GEO_DETECTION_SUPPORTED =
            "is_geo_detection_supported";

    /**
     * A shell command that prints the current user's "location-based time zone detection enabled"
     * setting.
     */
    private static final String SHELL_COMMAND_IS_GEO_DETECTION_ENABLED = "is_geo_detection_enabled";

    /**
     * A shell command that sets the current user's "location-based time zone detection enabled"
     * setting.
     */
    private static final String SHELL_COMMAND_SET_GEO_DETECTION_ENABLED =
            "set_geo_detection_enabled";

    /**
     * A shell command that sets the current time zone state for testing.
     * @hide
     */
    private static final String SHELL_COMMAND_SET_TIME_ZONE_STATE = "set_time_zone_state_for_tests";

    private static final String SHELL_CMD_PREFIX = "cmd " + SERVICE_NAME + " ";

    private static final String SETTINGS_CMD_PREFIX = "settings ";
    private static final String SETTING_AUTO_TIME_ZONE_EXPLICIT = "auto_time_zone_explicit";
    private static final String SETTING_AUTO_TIME_ZONE_EXPLICIT_IS_SET = "1";
    private static final String SETTINGS_NAMESPACE_GLOBAL = "global";

    private final DeviceShellCommandExecutor mShellCommandExecutor;

    public TimeZoneDetectorShellHelper(DeviceShellCommandExecutor shellCommandExecutor) {
        mShellCommandExecutor = Objects.requireNonNull(shellCommandExecutor);
    }

    /** Executes "is_auto_detection_enabled" */
    public boolean isAutoDetectionEnabled() throws Exception {
        return mShellCommandExecutor.executeToBoolean(
                SHELL_CMD_PREFIX + SHELL_COMMAND_IS_AUTO_DETECTION_ENABLED);
    }

    /** Executes "set_auto_detection_enabled" */
    public void setAutoDetectionEnabled(boolean enabled) throws Exception {
        String cmd = String.format("%s %s", SHELL_COMMAND_SET_AUTO_DETECTION_ENABLED, enabled);
        mShellCommandExecutor.executeToTrimmedString(SHELL_CMD_PREFIX + cmd);
    }

    /** Executes "is_geo_detection_enabled" */
    public boolean isGeoDetectionEnabled() throws Exception {
        return mShellCommandExecutor.executeToBoolean(
                SHELL_CMD_PREFIX + SHELL_COMMAND_IS_GEO_DETECTION_ENABLED);
    }

    /** Executes "set_geo_detection_enabled" */
    public void setGeoDetectionEnabled(boolean enabled) throws Exception {
        String cmd = String.format("%s %s", SHELL_COMMAND_SET_GEO_DETECTION_ENABLED, enabled);
        mShellCommandExecutor.executeToTrimmedString(SHELL_CMD_PREFIX + cmd);
    }

    /** Executes "is_geo_detection_supported" */
    public boolean isGeoDetectionSupported() throws Exception {
        return mShellCommandExecutor.executeToBoolean(
                SHELL_CMD_PREFIX + SHELL_COMMAND_IS_GEO_DETECTION_SUPPORTED);
    }

    /** Executes "is_telephony_detection_supported" */
    public boolean isTelephonyDetectionSupported() throws Exception {
        return mShellCommandExecutor.executeToBoolean(
                SHELL_CMD_PREFIX + SHELL_COMMAND_IS_TELEPHONY_DETECTION_SUPPORTED);
    }

    /** Executes "set_time_zone_state_for_tests" */
    public void setTimeZoneState(String timeZoneId, boolean userShouldConfirmId) throws Exception {
        String cmd = String.format("%s --zone_id %s"
                        + " --user_should_confirm_id %s",
                SHELL_COMMAND_SET_TIME_ZONE_STATE, timeZoneId,
                userShouldConfirmId);
        mShellCommandExecutor.executeToTrimmedString(SHELL_CMD_PREFIX + cmd);
    }

    public boolean isAutoTimeZoneEnabledExplicitly() throws Exception {
        // This command uses the "settings" command line for simplicity, instead of requiring
        // dedicated command line support from the time_zone_detector.
        String cmd = String.format("get %s %s", SETTINGS_NAMESPACE_GLOBAL,
                SETTING_AUTO_TIME_ZONE_EXPLICIT);
        String result = mShellCommandExecutor.executeToString(SETTINGS_CMD_PREFIX + cmd);
        // Expect "null" or "1".
        return SETTING_AUTO_TIME_ZONE_EXPLICIT_IS_SET.equals(result);
    }

    public void clearAutoTimeZoneEnabledExplicitly() throws Exception {
        // This command uses the "settings" command line for simplicity, instead of requiring
        // dedicated command line support from the time_zone_detector.
        String cmd = String.format("delete %s %s", SETTINGS_NAMESPACE_GLOBAL,
                SETTING_AUTO_TIME_ZONE_EXPLICIT);
        mShellCommandExecutor.executeToString(SETTINGS_CMD_PREFIX + cmd);
    }

    public void setAutoTimeZoneEnabledExplicitly() throws Exception {
        // This command uses the "settings" command line for simplicity, instead of requiring
        // dedicated command line support from the time_zone_detector.
        String cmd = String.format("put %s %s %s", SETTINGS_NAMESPACE_GLOBAL,
                SETTING_AUTO_TIME_ZONE_EXPLICIT, SETTING_AUTO_TIME_ZONE_EXPLICIT_IS_SET);
        mShellCommandExecutor.executeToString(SETTINGS_CMD_PREFIX + cmd);
    }
}
