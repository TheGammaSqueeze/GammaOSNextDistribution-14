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

package com.android.helpers;

import com.google.common.annotations.VisibleForTesting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MeasurementLatencyHelper {

    public static LatencyHelper getLogcatCollector() {
        return LatencyHelper.getLogcatLatencyHelper(new MeasurementProcessInputForLatencyMetrics());
    }

    @VisibleForTesting
    public static LatencyHelper getCollector(
            LatencyHelper.InputStreamFilter inputStreamFilter, Clock clock) {
        return new LatencyHelper(
                new MeasurementProcessInputForLatencyMetrics(), inputStreamFilter, clock);
    }

    private static class MeasurementProcessInputForLatencyMetrics
            implements LatencyHelper.ProcessInputForLatencyMetrics {

        @Override
        public String getTestLabel() {
            return "Measurement";
        }

        @Override
        public Map<String, Long> processInput(InputStream inputStream) throws IOException {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            Pattern latencyMetricPattern =
                    Pattern.compile(getTestLabel() + ": \\((MEASUREMENT_LATENCY_.*): (\\d+) ms\\)");

            String line = "";
            Map<String, Long> output = new HashMap<String, Long>();
            while ((line = bufferedReader.readLine()) != null) {
                Matcher matcher = latencyMetricPattern.matcher(line);
                while (matcher.find()) {
                    String metric = matcher.group(1);
                    long latency = Long.parseLong(matcher.group(2));
                    output.put(metric, latency);
                }
            }
            return output;
        }
    }
}
