/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.util.Log;

import com.android.internal.util.LatencyTracker;
import com.android.os.nano.AtomsProto;
import com.android.os.nano.StatsLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper consisting of helper methods to set system interactions configs in statsd and retrieve the
 * necessary information from statsd using the config id.
 */
public class UiActionLatencyHelper implements ICollectorHelper<StringBuilder> {

    private static final String LOG_TAG = UiActionLatencyHelper.class.getSimpleName();

    private final StatsdHelper mStatsdHelper = new StatsdHelper();

    /** Set up the system actions latency statsd config. */
    @Override
    public boolean startCollecting() {
        Log.i(LOG_TAG, "Enabling metric collection and disabling sampling.");
        DeviceConfigHelper.setConfigValue("latency_tracker", "enabled", "true");
        DeviceConfigHelper.setConfigValue("latency_tracker", "sampling_interval", "1");
        Log.i(LOG_TAG, "Adding system actions latency config to statsd.");
        List<Integer> atomIdList = new ArrayList<>();
        atomIdList.add(AtomsProto.Atom.UI_ACTION_LATENCY_REPORTED_FIELD_NUMBER);
        return mStatsdHelper.addEventConfig(atomIdList);
    }

    /** Collect the system actions latency metrics from the statsd. */
    @Override
    public Map<String, StringBuilder> getMetrics() {
        Log.i(LOG_TAG, "get metrics.");
        Map<String, StringBuilder> latenciesMap = new HashMap<>();
        for (StatsLog.EventMetricData dataItem : mStatsdHelper.getEventMetrics()) {
            final AtomsProto.Atom atom = dataItem.atom;
            if (atom.hasUiActionLatencyReported()) {
                final AtomsProto.UIActionLatencyReported uiActionLatencyReported =
                        atom.getUiActionLatencyReported();
                final String action =
                        LatencyTracker.getNameOfAction(uiActionLatencyReported.action);
                MetricUtility.addMetric(
                        MetricUtility.constructKey("latency", action),
                        uiActionLatencyReported.latencyMillis,
                        latenciesMap);
            }
        }

        return latenciesMap;
    }

    /** Remove the statsd config. */
    @Override
    public boolean stopCollecting() {
        return mStatsdHelper.removeStatsConfig();
    }
}
