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

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class for interacting with the {@code time_detector} service via the shell "cmd" command-line
 * interface.
 */
public final class TimeDetectorShellHelper {

    /**
     * A simple stand-in for the platform {@link android.app.time.UnixEpochTime} class that can be
     * used in tests, even host-side ones.
     */
    public static final class TestUnixEpochTime {
        public final long elapsedRealtimeMillis;
        public final long unixEpochTimeMillis;

        public TestUnixEpochTime(long elapsedRealtimeMillis, long unixEpochTimeMillis) {
            this.elapsedRealtimeMillis = elapsedRealtimeMillis;
            this.unixEpochTimeMillis = unixEpochTimeMillis;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TestUnixEpochTime that = (TestUnixEpochTime) o;
            return elapsedRealtimeMillis == that.elapsedRealtimeMillis
                    && unixEpochTimeMillis == that.unixEpochTimeMillis;
        }

        @Override
        public int hashCode() {
            return Objects.hash(elapsedRealtimeMillis, unixEpochTimeMillis);
        }

        @Override
        public String toString() {
            return "TestUnixEpochTime{"
                    + "elapsedRealtimeMillis=" + elapsedRealtimeMillis
                    + ", unixEpochTimeMillis=" + unixEpochTimeMillis
                    + '}';
        }

        /**
         * Creates a {@link TestUnixEpochTime} from the output of {@link
         * android.app.time.UnixEpochTime#toString()}.
         */
        static TestUnixEpochTime parseTestUnixEpochTimeFromToString(String value) {
            // Note: "}" has to be escaped on Android with "\\}" because the regexp library is not
            // based on OpenJDK code.
            Pattern pattern = Pattern.compile("UnixEpochTime\\{"
                    + "mElapsedRealtimeMillis=([^,]+)"
                    + ", mUnixEpochTimeMillis=([^}]+)"
                    + "\\}"
            );
            Matcher matcher = pattern.matcher(value);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Unable to parse: " + value);
            }
            long elapsedRealtimeMillis = Long.parseLong(matcher.group(1));
            long unixEpochTimeMillis = Long.parseLong(matcher.group(2));
            TestUnixEpochTime unixEpochTime =
                    new TestUnixEpochTime(elapsedRealtimeMillis, unixEpochTimeMillis);
            return unixEpochTime;
        }
    }

    /**
     * A simple stand-in for the platform {@link
     * com.android.server.timedetector.NetworkTimeSuggestion} class that can be used in tests, even
     * host-side ones.
     */
    public static final class TestNetworkTime {
        public final TestUnixEpochTime unixEpochTime;
        public final int uncertaintyMillis;

        public TestNetworkTime(TestUnixEpochTime unixEpochTime, int uncertaintyMillis) {
            this.unixEpochTime = Objects.requireNonNull(unixEpochTime);
            this.uncertaintyMillis = uncertaintyMillis;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TestNetworkTime that = (TestNetworkTime) o;
            return Objects.equals(unixEpochTime, that.unixEpochTime)
                    && uncertaintyMillis == that.uncertaintyMillis;
        }

        @Override
        public int hashCode() {
            return Objects.hash(unixEpochTime, uncertaintyMillis);
        }

        @Override
        public String toString() {
            return "TestNetworkTime{"
                    + "unixEpochTime=" + unixEpochTime
                    + ", uncertaintyMillis=" + uncertaintyMillis
                    + '}';
        }

        /**
         * Creates a {@link TestNetworkTime} from the output of {@link
         * android.app.time.UnixEpochTime#toString()}.
         */
        static TestNetworkTime parseFromNetworkTimeSuggestionToString(String value) {
            // Note: "}" has to be escaped on Android with "\\}" because the regexp library is not
            // based on OpenJDK code.
            Pattern pattern = Pattern.compile("NetworkTimeSuggestion\\{"
                    + "mUnixEpochTime=(UnixEpochTime\\{[^}]+\\})"
                    + ", mUncertaintyMillis=([^,]+)"
                    + ", mDebugInfo=\\[.+\\]"
                    + "\\}"
            );
            Matcher matcher = pattern.matcher(value);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Unable to parse: " + value);
            }
            String unixEpochTimeValue = matcher.group(1);
            Integer uncertaintyMillis = Integer.parseInt(matcher.group(2));
            TestUnixEpochTime unixEpochTime =
                    TestUnixEpochTime.parseTestUnixEpochTimeFromToString(unixEpochTimeValue);
            return new TestNetworkTime(unixEpochTime, uncertaintyMillis);
        }
    }

    /**
     * The name of the service for shell commands.
     */
    private static final String SERVICE_NAME = "time_detector";

    /**
     * A shell command that sets the current time state for testing.
     */
    private static final String SHELL_COMMAND_SET_TIME_STATE = "set_time_state_for_tests";

    /**
     * A shell command that prints the current network time information.
     */
    private static final String SHELL_COMMAND_GET_NETWORK_TIME = "get_network_time";

    /**
     * A shell command that clears the detector's network time information.
     */
    private static final String SHELL_COMMAND_CLEAR_NETWORK_TIME = "clear_network_time";

    /**
     * A shell command that clears the network time signal used by {@link
     * android.os.SystemClock#currentNetworkTimeClock()}.
     */
    private static final String SHELL_COMMAND_CLEAR_SYSTEM_CLOCK_NETWORK_TIME =
            "clear_system_clock_network_time";

    /**
     * A shell command that sets the network time signal used by {@link
     * android.os.SystemClock#currentNetworkTimeClock()}.
     */
    private static final String SHELL_COMMAND_SET_SYSTEM_CLOCK_NETWORK_TIME =
            "set_system_clock_network_time";

    private static final String SHELL_CMD_PREFIX = "cmd " + SERVICE_NAME + " ";

    private final DeviceShellCommandExecutor mShellCommandExecutor;

    public TimeDetectorShellHelper(DeviceShellCommandExecutor shellCommandExecutor) {
        mShellCommandExecutor = Objects.requireNonNull(shellCommandExecutor);
    }

    /** Executes "set_time_state_for_tests" */
    public void setTimeState(
            long elapsedRealtimeMillis, long unixEpochTimeMillis, boolean userShouldConfirmTime)
            throws Exception {
        String cmd = String.format("%s --elapsed_realtime %s"
                        + " --unix_epoch_time %s"
                        + " --user_should_confirm_time %s",
                SHELL_COMMAND_SET_TIME_STATE, elapsedRealtimeMillis,
                unixEpochTimeMillis, userShouldConfirmTime);
        mShellCommandExecutor.executeToTrimmedString(SHELL_CMD_PREFIX + cmd);
    }

    public TestNetworkTime getNetworkTime() throws Exception {
        String output = mShellCommandExecutor.executeToTrimmedString(
                SHELL_CMD_PREFIX + SHELL_COMMAND_GET_NETWORK_TIME);
        if ("null".equals(output)) {
            return null;
        }
        return TestNetworkTime.parseFromNetworkTimeSuggestionToString(output);
    }

    public void clearNetworkTime() throws Exception {
        mShellCommandExecutor.executeToTrimmedString(
                SHELL_CMD_PREFIX + SHELL_COMMAND_CLEAR_NETWORK_TIME);
    }

    // TODO(b/222295093) Remove these "system_clock" commands when
    //  SystemClock.currentNetworkTimeClock() is guaranteed to use the latest network
    //  suggestion. Then, commands above can be used instead (though they are async so some
    //  adjustment will be required).
    public void setSystemClockNetworkTime(TestNetworkTime networkTime) throws Exception {
        mShellCommandExecutor.executeToTrimmedString(
                SHELL_CMD_PREFIX + SHELL_COMMAND_SET_SYSTEM_CLOCK_NETWORK_TIME
                        + " --elapsed_realtime " + networkTime.unixEpochTime.elapsedRealtimeMillis
                        + " --unix_epoch_time " + networkTime.unixEpochTime.unixEpochTimeMillis
                        + " --uncertainty_millis " + networkTime.uncertaintyMillis
        );
    }

    // TODO(b/222295093) Remove these "system_clock" commands when
    //  SystemClock.currentNetworkTimeClock() is guaranteed to use the latest network
    //  suggestion. Then, commands above can be used instead (though they are async so some
    //  adjustment will be required).
    public void clearSystemClockNetworkTime() throws Exception {
        mShellCommandExecutor.executeToTrimmedString(
                SHELL_CMD_PREFIX + SHELL_COMMAND_CLEAR_SYSTEM_CLOCK_NETWORK_TIME);
    }
}
