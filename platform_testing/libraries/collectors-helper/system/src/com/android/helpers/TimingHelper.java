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

import android.app.UiAutomation;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import androidx.annotation.VisibleForTesting;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * TimingHelper is used to collect timing information that tests output into logcat with the
 * "ForTimingCollector" tag.
 */
public class TimingHelper implements ICollectorHelper<Integer> {

    private static final String TAG = TimingHelper.class.getSimpleName();
    private static final String FOR_TIMING_COLLECTOR_FILTER = "ForTimingCollector: ";
    private static final String LOGCAT_CMD = "logcat -d ForTimingCollector:I *:S";

    private Pattern durationPattern = Pattern.compile("[0-9]+");

    @Override
    public boolean startCollecting() {
        return true;
    }

    @Override
    public boolean stopCollecting() {
        return true;
    }

    @Override
    public Map<String, Integer> getMetrics() {
        Map<String, Integer> metrics = new HashMap<>();
        for (String log : getTimingLogs(streamLogcat())) {
            Log.d(TAG, "Found log line: " + log);
            String[] info = parseTimingInfo(log);
            if (info != null) {
                metrics.put(info[0], Integer.parseInt(info[1]));
            } else {
                Log.e(TAG, "Log did not contain a valid key-value pair: " + log);
            }
        }
        return metrics;
    }

    // Returns ForTimingCollector duration logs emitted by tests.
    @VisibleForTesting
    public ArrayList<String> getTimingLogs(InputStream is) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            ArrayList<String> lines = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                // This is technically a no-op because logs in the InputStream should be filtered
                // for the "ForTimingCollector" tag, but it wouldn't hurt to check.
                if (line.contains(FOR_TIMING_COLLECTOR_FILTER)) {
                    lines.add(line);
                }
            }
            return lines;
        } catch (IOException e) {
            Log.e(TAG, "Failed to get timing-related log line.");
            return null;
        }
    }

    // Returns a String[] containing the key and the value.
    @VisibleForTesting
    public String[] parseTimingInfo(String log) {
        // Gets the last contiguous set of characters and splits it by ':'.
        String[] split = log.trim().split("\\s");
        String[] info = split[split.length - 1].split(":");
        if (info.length != 2) {
            Log.w(TAG, "Final string doesn't have one key and one value.");
            return null;
        }
        if (!durationPattern.matcher(info[1]).matches()) {
            Log.w(TAG, "Value was not a number.");
            return null;
        }
        return info;
    }

    // Streams info-level (and higher) logs with the ForTimingCollector tag.
    private ParcelFileDescriptor.AutoCloseInputStream streamLogcat() {
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        return new ParcelFileDescriptor.AutoCloseInputStream(
                automation.executeShellCommand(LOGCAT_CMD));
    }
}
