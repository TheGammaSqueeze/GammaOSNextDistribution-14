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

package com.android.helpers;

import static com.android.helpers.MetricUtility.constructKey;

import android.util.Log;
import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** TimeInStateHelper is a helper to collect time_in_state frequency stats. */
public class TimeInStateHelper implements ICollectorHelper<Long> {
    private static final String LOG_TAG = TimeInStateHelper.class.getSimpleName();
    private static final String METRIC_KEY_PREFIX = "time_in_state";
    private static final String AVG_FREQ_KEY_SUFFIX = "avg_freq";
    private static final Pattern TIME_IN_STATE_PATTERN = Pattern.compile("(\\d+)\\s+(\\d+)");
    private static final String KEY_SOURCE_SEPARATOR = "@";

    private static final String METRICS_LOG_FMT = "metrics key: %s, value: %d";
    private static final String READ_FILE_CMD = "cat %s";
    private static final String CHECK_FILE_EXIST_CMD = "file -b %s";

    private List<String> mMetricKeys = new ArrayList<>();
    private List<String> mSourceLocations = new ArrayList<>();
    private List<Map<String, Long>> mBeforeFreqStats;
    private List<Map<String, Long>> mAfterFreqStats;
    private UiDevice mDevice;

    public void setUp(String... freqKeys) {
        if (freqKeys == null) {
            return;
        }
        for (int i = 0; i < freqKeys.length; i++) {
            String[] keys = freqKeys[i].split(KEY_SOURCE_SEPARATOR);
            if (keys.length != 2) {
                Log.e(LOG_TAG, "Failed to parse " + freqKeys[i]);
                throw new RuntimeException("Failed to parse " + freqKeys[i]);
            }
            String key = keys[0].trim();
            String source = keys[1].trim();
            Log.i(LOG_TAG, "key: " + key + ", source: " + source);

            String cmd = String.format(CHECK_FILE_EXIST_CMD, source);
            String result = null;
            try {
                result = getDevice().executeShellCommand(cmd);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Error when checking source file " + source);
            }
            if (result == null || result.contains("No such file or directory")) {
                Log.e(LOG_TAG, "Source " + source + " does not exist");
            } else {
                mMetricKeys.add(key);
                mSourceLocations.add(source);
            }
        }
    }

    @Override
    public boolean startCollecting() {
        Log.i(LOG_TAG, "start collecting...");
        try {
            mBeforeFreqStats = readAllFreqStats();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to collect frequency stats at the start time.", e);
            throw new RuntimeException(e);
        }
        return true;
    }

    @Override
    public boolean stopCollecting() {
        Log.i(LOG_TAG, "stop collecting...");
        return true;
    }

    protected UiDevice getDevice() {
        if (mDevice == null) {
            mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        }
        return mDevice;
    }

    @Override
    public Map<String, Long> getMetrics() {
        Log.i(LOG_TAG, "get metrics...");

        try {
            mAfterFreqStats = readAllFreqStats();
            return calculateFreqStatsMetrics();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to collect frequency stats at the end time.", e);
            throw new RuntimeException(e);
        }
    }

    private List<Map<String, Long>> readAllFreqStats() {
        List<Map<String, Long>> freqStatsMapList = new ArrayList<Map<String, Long>>();
        for (String source : mSourceLocations) {
            Map<String, Long> result = readFreqStats(source);
            freqStatsMapList.add(result);
        }
        return freqStatsMapList;
    }

    private Map<String, Long> readFreqStats(String source) {
        Map<String, Long> freqStatsMap = new HashMap<String, Long>();
        String timeInStateData = "";
        try {
            String cmd = String.format(READ_FILE_CMD, source);
            timeInStateData = getDevice().executeShellCommand(cmd);
            Matcher m = TIME_IN_STATE_PATTERN.matcher(timeInStateData);
            while (m.find()) {
                String freq = m.group(1);
                String time = m.group(2);
                Log.i(LOG_TAG, source + ": freq= " + freq + ", time= " + time);

                freqStatsMap.put(freq, Long.parseLong(time));
            }
        } catch (Exception e) {
            Log.e(
                    LOG_TAG,
                    "Failed to read time_in_state from "
                            + source
                            + ", content:\n"
                            + timeInStateData,
                    e);
            throw new RuntimeException(e);
        }

        if (freqStatsMap.isEmpty()) {
            Log.e(
                    LOG_TAG,
                    "Can't parse time_in_state from " + source + ", content:\n" + timeInStateData);
            throw new RuntimeException("Failed to collect freq metrics.");
        }
        return freqStatsMap;
    }

    private Map<String, Long> calculateFreqStatsMetrics() {
        Log.i(LOG_TAG, "Collect frequency stats during the test");

        Map<String, Long> freqStatsMetrics = new HashMap<String, Long>();
        for (int i = 0; i < mSourceLocations.size(); i++) {
            long totalWeightedFreq = 0;
            long totalTime = 0;

            Map<String, Long> afterFreqStatsMap = mAfterFreqStats.get(i);
            Map<String, Long> beforeFreqStatsMap = mBeforeFreqStats.get(i);
            String metricsPrefixKey = constructKey(METRIC_KEY_PREFIX, mMetricKeys.get(i));
            for (Map.Entry<String, Long> entry : afterFreqStatsMap.entrySet()) {
                String freq = entry.getKey();
                long endTime = entry.getValue();
                long startTime = beforeFreqStatsMap.getOrDefault(freq, 0L);
                long runTime = endTime - startTime;
                if (runTime == 0) {
                    continue;
                }
                long freqValue = Long.parseLong(freq);
                totalWeightedFreq += runTime * freqValue;
                totalTime += runTime;
                String metricsKey = constructKey(metricsPrefixKey, freq);
                freqStatsMetrics.put(metricsKey, runTime);
                Log.i(LOG_TAG, String.format(METRICS_LOG_FMT, metricsKey, runTime));
            }

            if (totalTime > 0) {
                String avgFreqMetricsKey = constructKey(metricsPrefixKey, AVG_FREQ_KEY_SUFFIX);
                long avgFreq = totalWeightedFreq / totalTime;
                freqStatsMetrics.put(avgFreqMetricsKey, avgFreq);
                Log.i(LOG_TAG, String.format(METRICS_LOG_FMT, avgFreqMetricsKey, avgFreq));
            }
        }

        return freqStatsMetrics;
    }
}
