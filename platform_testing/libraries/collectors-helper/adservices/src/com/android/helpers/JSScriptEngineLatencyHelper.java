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
import com.google.common.collect.ImmutableMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JSScriptEngineLatencyHelper {

    public static LatencyHelper getLogcatCollector() {
        return LatencyHelper.getLogcatLatencyHelper(
                new JSScriptEngineProcessInputForLatencyMetrics());
    }

    @VisibleForTesting
    public static LatencyHelper getCollector(
            LatencyHelper.InputStreamFilter inputStreamFilter, Clock clock) {
        return new LatencyHelper(
                new JSScriptEngineProcessInputForLatencyMetrics(), inputStreamFilter, clock);
    }

    private static class JSScriptEngineProcessInputForLatencyMetrics
            implements LatencyHelper.ProcessInputForLatencyMetrics {
        private static final String SANDBOX_INIT_TIME_AVG = "SANDBOX_INIT_TIME_AVG";
        private static final String ISOLATE_CREATE_TIME_AVG = "ISOLATE_CREATE_TIME_AVG";
        private static final String WEBVIEW_EXECUTION_TIME_AVG = "WEBVIEW_EXECUTION_TIME_AVG";
        private static final String JAVA_EXECUTION_TIME_AVG = "JAVA_EXECUTION_TIME_AVG";
        private static final String NUM_ITERATIONS = "NUM_ITERATIONS";

        private static final String SANDBOX_INIT_TIME = "SANDBOX_INIT_TIME";
        private static final String ISOLATE_CREATE_TIME = "ISOLATE_CREATE_TIME";
        private static final String WEBVIEW_EXECUTION_TIME = "WEBVIEW_EXECUTION_TIME";
        private static final String JAVA_EXECUTION_TIME = "JAVA_EXECUTION_TIME";

        @Override
        public String getTestLabel() {
            return "JSScriptEngine";
    }

        @Override
        public Map<String, Long> processInput(InputStream inputStream) throws IOException {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            Pattern latencyMetricPattern = Pattern.compile(getTestLabel() + ": \\((.*): (\\d+)\\)");

            List<Long> sandboxInitTimes = new ArrayList<>();
            List<Long> isolateCreateTimes = new ArrayList<>();
            List<Long> javaProcessTimes = new ArrayList<>();
            List<Long> webviewProcessTimes = new ArrayList<>();

            String line = "";
            while ((line = bufferedReader.readLine()) != null) {
                Matcher matcher = latencyMetricPattern.matcher(line);
                while (matcher.find()) {
                    /**
                     * The lines from Logcat will look like: 06-13 18:09:24.058 20765 20781 D
                     * JSScriptEngine: (JAVA_PROCESS_TIME: 43)
                     */
                    String metric = matcher.group(1);
                    long latency = Long.parseLong(matcher.group(2));
                    if (SANDBOX_INIT_TIME.equals(metric)) {
                        sandboxInitTimes.add(latency);
                    } else if (ISOLATE_CREATE_TIME.equals(metric)) {
                        isolateCreateTimes.add(latency);
                    } else if (JAVA_EXECUTION_TIME.equals(metric)) {
                        javaProcessTimes.add(latency);
                    } else if (WEBVIEW_EXECUTION_TIME.equals(metric)) {
                        webviewProcessTimes.add(latency);
                    }
        }
            }

            // Just getting average for now.
            int defaultMetricVal = 0;
            long sandboxInitTimeAvg = getAverage(sandboxInitTimes, defaultMetricVal);
            long isolateCreateTimeAvg = getAverage(isolateCreateTimes, defaultMetricVal);
            long webviewProcessTimeAvg = getAverage(webviewProcessTimes, defaultMetricVal);
            long javaProcessTimeAvg = getAverage(javaProcessTimes, defaultMetricVal);

            return ImmutableMap.of(
                    SANDBOX_INIT_TIME_AVG, sandboxInitTimeAvg,
                    ISOLATE_CREATE_TIME_AVG, isolateCreateTimeAvg,
                    WEBVIEW_EXECUTION_TIME_AVG, webviewProcessTimeAvg,
                    JAVA_EXECUTION_TIME_AVG, javaProcessTimeAvg,
                    NUM_ITERATIONS, (long) javaProcessTimes.size());
    }

    private Long getAverage(List<Long> list, long defaultValue) {
            return (long) list.stream().mapToDouble(d -> d).average().orElse(defaultValue);
    }
    }
}
