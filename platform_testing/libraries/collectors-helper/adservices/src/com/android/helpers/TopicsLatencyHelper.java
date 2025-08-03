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

package com.android.helpers;

import com.google.common.annotations.VisibleForTesting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TopicsLatencyHelper consist of helper methods to collect Topics API call latencies
 *
 * <p>TODO(b/234452723): Change metric collector to use either statsd or perfetto instead of logcat
 */
public class TopicsLatencyHelper {

    public static LatencyHelper getLogcatCollector() {
        return LatencyHelper.getLogcatLatencyHelper(new TopicsProcessInputForLatencyMetrics());
    }

    @VisibleForTesting
    public static LatencyHelper getCollector(LatencyHelper.InputStreamFilter inputStreamFilter) {
        return new LatencyHelper(new TopicsProcessInputForLatencyMetrics(), inputStreamFilter);
    }

    private static class TopicsProcessInputForLatencyMetrics
            implements LatencyHelper.ProcessInputForLatencyMetrics {

        private static final String TOPICS_HOT_START_LATENCY_METRIC =
                "TOPICS_HOT_START_LATENCY_METRIC";
        private static final String TOPICS_COLD_START_LATENCY_METRIC =
                "TOPICS_COLD_START_LATENCY_METRIC";

        @Override
        public String getTestLabel() {
            return "GetTopicsApiCall";
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
                     * GetTopicsApiCall: (TOPICS_HOT_START_LATENCY_METRIC: 14)
                     */
                    String metric = matcher.group(1);
                    long latency = Long.parseLong(matcher.group(2));
                    if (TOPICS_HOT_START_LATENCY_METRIC.equals(metric)) {
                        output.put(TOPICS_HOT_START_LATENCY_METRIC, latency);
                    } else if (TOPICS_COLD_START_LATENCY_METRIC.equals(metric)) {
                        output.put(TOPICS_COLD_START_LATENCY_METRIC, latency);
                    }
        }
            }
            return output;
    }
    }
}
