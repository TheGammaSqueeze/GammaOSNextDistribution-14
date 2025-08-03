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

package com.android.performance.tests;

import com.android.tradefed.log.LogUtil;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.proto.TfMetricProtoUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/** A simple data structure that used to capture performance metrics and report median values */
public class DataRecorder {

    private final String mRunName;
    private Map<String, List<Float>> mFloatMetrics = new HashMap<>();
    private Map<String, List<Long>> mLongMetrics = new HashMap<>();

    public DataRecorder(String runName) {
        mRunName = runName;
    }

    public void recordMetric(String name, Long value) {
        List<Long> list = mLongMetrics.computeIfAbsent(name, k -> new ArrayList<>());
        list.add(value);
    }

    public void recordMetric(String name, Float value) {
        List<Float> list = mFloatMetrics.computeIfAbsent(name, k -> new ArrayList<>());
        list.add(value);
    }

    public void captureTime(String key, Callable<Void> action) throws Exception {
        long startTime = System.currentTimeMillis();
        action.call();
        recordMetric(key, System.currentTimeMillis() - startTime);
    }

    public void reportMetrics(ITestInvocationListener listener, Map<String, String> metrics) {
        for (Map.Entry<String, List<Long>> entry : mLongMetrics.entrySet()) {
            metrics.put(entry.getKey(), getLongMedian(entry.getValue()).toString());
        }
        for (Map.Entry<String, List<Float>> entry : mFloatMetrics.entrySet()) {
            metrics.put(entry.getKey(), getFloatMedian(entry.getValue()).toString());
        }

        LogUtil.CLog.i("About to report metrics: %s", metrics);
        listener.testRunStarted(mRunName, 0);
        listener.testRunEnded(0, TfMetricProtoUtil.upgradeConvert(metrics));
    }

    private static Long getLongMedian(List<Long> items) {
        Collections.sort(items);
        int medianEntry = items.size() / 2;
        return items.get(medianEntry);
    }

    private static Float getFloatMedian(List<Float> items) {
        Collections.sort(items);
        int medianEntry = items.size() / 2;
        return items.get(medianEntry);
    }

    @Override
    public String toString() {
        return mLongMetrics.toString() + "," + mFloatMetrics.toString();
    }
}
