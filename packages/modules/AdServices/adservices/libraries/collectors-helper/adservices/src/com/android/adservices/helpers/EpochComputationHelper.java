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

/** Utility class which computes epoch computation duration. */
public class EpochComputationHelper {

    public static LatencyHelper getLogcatCollector() {
        return LatencyHelper.getLogcatLatencyHelper(new ProcessEpochForLatencyMetrics());
    }

    /** Used by the test to pass the inputstream. */
    @VisibleForTesting
    public static LatencyHelper getCollector(LatencyHelper.InputStreamFilter inputStreamFilter) {
        return new LatencyHelper(new ProcessEpochForLatencyMetrics(), inputStreamFilter);
    }

    private static class ProcessEpochForLatencyMetrics
            implements LatencyHelper.ProcessInputForLatencyMetrics {
        private static final String EPOCH_COMPUTATION_DURATION = "EPOCH_COMPUTATION_DURATION";

        @Override
        public String getTestLabel() {
            return "TopicsEpochComputation";
        }

        @Override
        public Map<String, Long> processInput(InputStream inputStream) throws IOException {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            Pattern pattern = Pattern.compile(getTestLabel() + ": \\((.*): (\\d+)\\)");

            Map<String, Long> output = new HashMap<String, Long>();
            String line = "";
            while ((line = bufferedReader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                while (matcher.find()) {
                    /**
                     * The lines from Logcat looks like: 06-13 18:09:24.058 20765 20781 I
                     * EpochComputationTest: (EPOCH_COMPUTATION_DURATION: 14)
                     */
                    String metric = matcher.group(1);
                    Long latency = Long.parseLong(matcher.group(2));
                    if (metric.equals(EPOCH_COMPUTATION_DURATION)) {
                        output.put(metric, latency);
                    }
                }
            }

            return output;
        }
    }
}
