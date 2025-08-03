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

package com.android.adservices.helpers;

import com.android.helpers.LatencyHelper;
import com.android.internal.annotations.VisibleForTesting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UiLatencyHelper consist of helper methods to collect ui operation latencies
 *
 * <p>TODO(b/234452723): Change metric collector to use either statsd or perfetto instead of logcat
 */
public class UiLatencyHelper {

    public static LatencyHelper getLogcatCollector() {
        return LatencyHelper.getLogcatLatencyHelper(new UiProcessInputForLatencyMetrics());
    }

    /** set up input stream for testing the log catch */
    @VisibleForTesting
    public static LatencyHelper getCollector(LatencyHelper.InputStreamFilter inputStreamFilter) {
        return new LatencyHelper(new UiProcessInputForLatencyMetrics(), inputStreamFilter);
    }

    private static class UiProcessInputForLatencyMetrics
            implements LatencyHelper.ProcessInputForLatencyMetrics {

        private static final String UI_NOTIFICATION_LATENCY_METRIC =
                "UI_NOTIFICATION_LATENCY_METRIC";
        private static final String UI_SETTINGS_LATENCY_METRIC = "UI_SETTINGS_LATENCY_METRIC";

        @Override
        public String getTestLabel() {
            return "UiTestLabel";
        }

        @Override
        public Map<String, Long> processInput(InputStream inputStream) throws IOException {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            Pattern latencyMetricPattern = Pattern.compile(getTestLabel() + ": \\((.*): (\\d+)\\)");

            String line = "";
            Map<String, Long> output = new HashMap<String, Long>();
            while ((line = bufferedReader.readLine()) != null) {
                Matcher matcher = latencyMetricPattern.matcher(line);
                while (matcher.find()) {
                    /**
                     * The lines from Logcat will look like: 06-13 18:09:24.058 20765 20781 D
                     * UiTestLabel: (UI_NOTIFICATION_LATENCY_METRIC: 14)
                     */
                    String metric = matcher.group(1);
                    long latency = Long.parseLong(matcher.group(2));
                    if (UI_NOTIFICATION_LATENCY_METRIC.equals(metric)) {
                        output.put(UI_NOTIFICATION_LATENCY_METRIC, latency);
                    } else if (UI_SETTINGS_LATENCY_METRIC.equals(metric)) {
                        output.put(UI_SETTINGS_LATENCY_METRIC, latency);
                    }
                }
            }
            return output;
        }
    }
}
