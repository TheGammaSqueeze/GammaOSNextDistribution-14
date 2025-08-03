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

package com.android.tests.scheduling.host;

import static com.google.common.truth.Truth.assertThat;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * Host side tests for Reboot Readiness detection.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class RebootReadinessHostTest extends BaseHostJUnit4Test {

    private static final String METRIC_TIME_TO_REBOOT_READY = "rebootReadyMs";
    private static final String METRIC_TIME_TO_FIRST_UNLOCK = "timeUntilFirstUnlockMs";
    private static final String METRIC_BLOCKED_BY_INTERACTIVITY = "blockedByInteractivity";
    private static final String METRIC_BLOCKED_BY_COMPONENTS = "blockedBySubsystems";
    private static final String METRIC_BLOCKED_BY_APP_ACTIVITY = "blockedByAppActivity";

    private void runPhase(String methodName) throws Exception {
        assertThat(runDeviceTests("com.android.tests.scheduling",
                "com.android.tests.scheduling.RebootReadinessTest",
                methodName)).isTrue();
    }

    /**
     * Ensures that the correct metric is logged when the device is in a reboot-ready state
     * immediately.
     */
    @Test
    public void testUnattendedRebootMetrics_Basic() throws Exception {
        runPhase("testRebootReadyBroadcastReceived");

        getDevice().reboot();
        getDevice().executeAdbCommand("logcat", "-c");

        // This command will unlock the device, which will cause the metric to be logged
        getDevice().executeShellCommand("wm dismiss-keyguard");

        // Wait a small amount of time for the metrics to be logged, before querying logcat
        RunUtil.getDefault().sleep(2000);
        String logs = getDevice().executeAdbCommand(
                "logcat", "-v", "brief", "-d", "RebootReadinessLogger:I", "*:S");
        UnattendedRebootMetricEvent event = null;
        Scanner in = new Scanner(logs);
        while (in.hasNextLine()) {
            String line = in.nextLine();
            if (line.contains("UnattendedRebootOccurred")) {
                event = parseUnattendedRebootEvent(line);
                break;
            }
        }
        in.close();

        assertThat(event).isNotNull();
        assertThat(event.mTimesBlockedByAppActivity).isEqualTo(0);
        assertThat(event.mTimesBlockedByComponents).isEqualTo(0);
        assertThat(event.mTimesBlockedByInteractivity).isEqualTo(0);
        assertThat(event.mTimeToFirstUnlockMs).isAtLeast(0);
        assertThat(event.mTimeToFirstUnlockMs).isLessThan(TimeUnit.MINUTES.toMillis(1));
        assertThat(event.mTimeToRebootReadyMs).isAtLeast(0);
        assertThat(event.mTimeToRebootReadyMs).isLessThan(TimeUnit.MINUTES.toMillis(1));
    }

    private UnattendedRebootMetricEvent parseUnattendedRebootEvent(String line) {
        String[] metricFields = line.split("UnattendedRebootOccurred")[1].trim().split(" ");
        UnattendedRebootMetricEvent event = new UnattendedRebootMetricEvent();
        for (String metric: metricFields) {
            // Each key-value pair will be of the form "key=value"
            event.setField(metric.split("=")[0], metric.split("=")[1]);
        }
        return event;
    }


    private static final class UnattendedRebootMetricEvent {

        long mTimeToFirstUnlockMs = -1;
        long mTimeToRebootReadyMs = -1;
        int mTimesBlockedByInteractivity = -1;
        int mTimesBlockedByComponents = -1;
        int mTimesBlockedByAppActivity = -1;

        UnattendedRebootMetricEvent() {
        }

        void setField(String value, String key) {
            switch (value) {
                case METRIC_TIME_TO_REBOOT_READY:
                    mTimeToRebootReadyMs = Long.parseLong(key);
                    break;
                case METRIC_TIME_TO_FIRST_UNLOCK:
                    mTimeToFirstUnlockMs = Long.parseLong(key);
                    break;
                case METRIC_BLOCKED_BY_INTERACTIVITY:
                    mTimesBlockedByInteractivity = Integer.parseInt(key);
                    break;
                case METRIC_BLOCKED_BY_COMPONENTS:
                    mTimesBlockedByComponents = Integer.parseInt(key);
                    break;
                case METRIC_BLOCKED_BY_APP_ACTIVITY:
                    mTimesBlockedByAppActivity = Integer.parseInt(key);
                    break;
                default:
                    break;
            }
        }
    }

}
