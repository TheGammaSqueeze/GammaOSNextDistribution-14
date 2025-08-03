/*
 * Copyright (C) 2023 The Android Open Source Project
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
package android.app.cts.shortfgstest;

import static android.app.cts.shortfgstesthelper.ShortFgsHelper.TAG;

import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.util.Log;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Containing a test that ensures the default timeouts used for
 * {@link android.content.pm.ServiceInfo#FOREGROUND_SERVICE_TYPE_SHORT_SERVICE} are larger than
 * the intended values.
 *
 * It's not in the main test class ({@link ActivityManagerShortFgsTest}), because
 * {@link ActivityManagerShortFgsTest} changes the device config settings.
 *
 * Note, this test can fail if `activity_manager` device_config has been changed already on the
 * device.
 */
@Presubmit
public class ActivityManagerShortFgsTimeoutTest {
    @BeforeClass
    public static void setUpClass() throws Exception {
        Log.d(TAG, "setUpClass() started");

        ShellUtils.runShellCommand(
                "cmd device_config reset trusted_defaults activity_manager");

        Log.d(TAG, "setUpClass() done");
    }

    /**
     * Extract `short_fgs_*` settings from `dumpsys activity settings` and return them
     * as a map.
     * @return
     */
    private Map<String, Long> extractShortFgsSettings() {
        final String dumpsys = ShellUtils.runShellCommand("dumpsys activity settings");

        final var res = new HashMap<String, Long>();

        final Pattern shortFgsSettingsMatcher = Pattern.compile(
                "^\\s*(short_fgs[a-zA-Z_]+)\\=(\\d+)");
        for (String line : dumpsys.split("\\n", -1)) {
            try {
                final Matcher m = shortFgsSettingsMatcher.matcher(line);
                if (!m.matches()) {
                    continue;
                }
                final String key = m.group(1);
                final long value = Long.parseLong(m.group(2)); // Should always succeed.
                Log.d(TAG, "Found setting: " + key + " = " + value);

                res.put(key, value);
            } catch (Exception e) {
                throw new RuntimeException(
                        "Failed to parse a setting line. Last line read: " + line, e);
            }
        }
        return res;
    }

    private void assertConfigAtLeast(Map<String, Long> configs, String config, long minimum) {
        final Long value = configs.get(config);
        if (value == null) {
            Assert.fail("Unable to find config \"" + config + "\" from dumpsys activity settings");
        }
        if (value < minimum) {
            Assert.fail("Config \"" + config + "\" expected to be >= " + minimum + ", but was: "
                    + value);
        }
    }

    /**
     * See the class javadoc.
     */
    @Test
    public void testDefaultTimeouts() throws Exception {
        // When the main test class resets the device config values, it's propagated asynchronously
        // to ActivityManagerConstants, so we'll just retry up to this many seconds.
        final int timeoutSecond = 30;

        int sleep = 125;
        final long timeout = SystemClock.uptimeMillis() + timeoutSecond * 1000;
        while (true) {

            final var keyValues = extractShortFgsSettings();
            try {
                assertConfigAtLeast(keyValues, "short_fgs_timeout_duration", 3 * 60_000);
                assertConfigAtLeast(keyValues, "short_fgs_proc_state_extra_wait_duration", 5_000);
                assertConfigAtLeast(keyValues, "short_fgs_anr_extra_wait_duration", 10_000);
                return;
            } catch (Throwable th) {
                if (SystemClock.uptimeMillis() >= timeout) {
                    throw th;
                }
            }

            Thread.sleep(sleep);
            sleep *= 5;
            sleep = Math.min(2000, sleep);
        }
    }
}
