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

package com.android.adservices.cts;

import static com.android.adservices.common.AndroidSdk.PRE_T;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.adservices.common.AdServicesHostSideFlagsSetterRule;
import com.android.adservices.common.AdServicesHostSideTestCase;
import com.android.adservices.common.BackgroundLogReceiver;
import com.android.adservices.common.HostSideSdkLevelSupportRule;
import com.android.adservices.common.RequiresSdkRange;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Test to check if com.google.android.ext.services failed to mount */
@RunWith(DeviceJUnit4ClassRunner.class)
@RequiresSdkRange(atMost = PRE_T, reason = "It's for S only")
public class AdExtServicesFailedToMountHostTest extends AdServicesHostSideTestCase {
    private static final String LOGCAT_COMMAND = "logcat";
    private static final String PATTERN_TO_MATCH =
            "com\\.google\\.android\\.ext\\.services"
                    + ".*Failed to mount.*No such file or directory";

    @Rule(order = 0)
    public final HostSideSdkLevelSupportRule sdkLevel = HostSideSdkLevelSupportRule.forAnyLevel();

    // Sets flags used in the test (and automatically reset them at the end)
    @Rule(order = 1)
    public final AdServicesHostSideFlagsSetterRule flags =
            AdServicesHostSideFlagsSetterRule.forCompatModeEnabledTests();

    @Test
    public void testLogcatDoesNotContainError() throws Exception {
        // reboot the device
        mDevice.reboot();
        mDevice.waitForDeviceAvailable();

        Pattern errorPattern = Pattern.compile(PATTERN_TO_MATCH);

        // Wait for up to 5 minutes, looking for the error log
        BackgroundLogReceiver logcatReceiver =
                new BackgroundLogReceiver.Builder()
                        .setDevice(mDevice)
                        .setLogCatCommand(LOGCAT_COMMAND)
                        .setEarlyStopCondition(stopIfErrorLogOccurs(errorPattern))
                        .build();
        logcatReceiver.collectLogs(/* timeoutMilliseconds= */ 5 * 60 * 1000);

        assertWithMessage("logcat matches regex (%s)", PATTERN_TO_MATCH)
                .that(logcatReceiver.patternMatches(errorPattern))
                .isFalse();
    }

    private Predicate<String[]> stopIfErrorLogOccurs(Pattern errorPattern) {
        return (s) -> errorPattern.matcher(String.join("\n", s)).find();
    }
}
