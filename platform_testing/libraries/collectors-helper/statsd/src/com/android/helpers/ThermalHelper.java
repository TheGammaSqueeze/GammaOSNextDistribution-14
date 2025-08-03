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

import static android.os.nano.OsProtoEnums.TEMPERATURE_TYPE_BATTERY;
import static android.os.nano.OsProtoEnums.TEMPERATURE_TYPE_CPU;
import static android.os.nano.OsProtoEnums.TEMPERATURE_TYPE_GPU;
import static android.os.nano.OsProtoEnums.TEMPERATURE_TYPE_POWER_AMPLIFIER;
import static android.os.nano.OsProtoEnums.TEMPERATURE_TYPE_SKIN;
import static android.os.nano.OsProtoEnums.TEMPERATURE_TYPE_USB_PORT;

import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.os.nano.AtomsProto;
import com.android.os.nano.StatsLog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ThermalHelper is a helper class to collect thermal events from statsd. Currently, it identifies
 * severity state changes.
 */
public class ThermalHelper implements ICollectorHelper<StringBuilder> {
    private static final String LOG_TAG = ThermalHelper.class.getSimpleName();

    @VisibleForTesting static final String DUMP_THERMALSERVICE_CMD = "dumpsys thermalservice";
    private static final String METRIC_KEY_TEMPERATURE = "temperature";
    private static final String METRIC_KEY_VALUE = "value";
    private static final String METRIC_KEY_TYPE = "type";
    private static final String METRIC_KEY_STATUS = "status";

    private static final int UNDEFINED_SEVERITY = -1;
    private static final Pattern SEVERITY_DUMPSYS_PATTERN =
            Pattern.compile("Thermal Status: (\\d+)");
    private static final Pattern TEMPERATURE_DUMPSYS_PATTERN =
            Pattern.compile(".*mValue=(.*), mType=(.*), mName=(.*), mStatus=(\\d+).*");

    private StatsdHelper mStatsdHelper;
    private UiDevice mDevice;
    private int mInitialSeverity;

    /** Set up the statsd config to track thermal events. */
    @Override
    public boolean startCollecting() {
        // Add an initial value because this only detects changes.
        mInitialSeverity = UNDEFINED_SEVERITY;
        try {
            String[] output = getDevice().executeShellCommand(DUMP_THERMALSERVICE_CMD).split("\n");
            for (String line : output) {
                Matcher severityMatcher = SEVERITY_DUMPSYS_PATTERN.matcher(line);
                if (severityMatcher.matches()) {
                    mInitialSeverity = Integer.parseInt(severityMatcher.group(1));
                    Log.v(LOG_TAG, String.format("Initial severity: %s.", mInitialSeverity));
                }
            }
        } catch (NumberFormatException nfe) {
            Log.e(LOG_TAG, String.format("Couldn't identify severity. Error parsing: %s", nfe));
            return false;
        } catch (IOException ioe) {
            Log.e(LOG_TAG, String.format("Failed to query thermalservice. Error: %s", ioe));
            return false;
        }

        // Skip this monitor if severity isn't identified.
        if (mInitialSeverity == UNDEFINED_SEVERITY) {
            Log.w(LOG_TAG, "Did not find an initial severity. Quitting.");
            return false;
        }

        // Register the thermal event config to statsd.
        List<Integer> atomIdList = new ArrayList<>();
        atomIdList.add(AtomsProto.Atom.THERMAL_THROTTLING_SEVERITY_STATE_CHANGED_FIELD_NUMBER);
        return getStatsdHelper().addEventConfig(atomIdList);
    }

    /** Collect the thermal events that occurred during the test. */
    @Override
    public Map<String, StringBuilder> getMetrics() {
        Map<String, StringBuilder> results = new HashMap<>();

        // Add the initial severity value every time metrics are collected.
        String severityKey = MetricUtility.constructKey("thermal", "throttling", "severity");
        MetricUtility.addMetric(severityKey, mInitialSeverity, results);

        List<StatsLog.EventMetricData> eventMetricData = getStatsdHelper().getEventMetrics();
        Log.i(LOG_TAG, String.format("%d thermal data points found.", eventMetricData.size()));
        // Collect all thermal throttling severity state change events.
        for (StatsLog.EventMetricData dataItem : eventMetricData) {
            if (dataItem.atom.hasThermalThrottlingSeverityStateChanged()) {
                // TODO(b/137878503): Add elapsed_timestamp_nanos for timpestamp data.
                // Get thermal throttling severity state change data point.
                int severity = dataItem.atom.getThermalThrottlingSeverityStateChanged().severity;
                // Store the severity state change ignoring where the measurement came from.
                MetricUtility.addMetric(severityKey, severity, results);
                // Set the initial severity to the last value, in case #getMetrics is called again.
                mInitialSeverity = severity;
            }
        }

        updateTemperatureMetrics(results);

        return results;
    }

    /** Collect temperature metrics into result map. */
    private void updateTemperatureMetrics(Map<String, StringBuilder> results) {

        try {
            String output = getDevice().executeShellCommand(DUMP_THERMALSERVICE_CMD);
            String[] lines = output.split("\n");
            boolean inCurrentTempSection = false;
            for (String line : lines) {
                Matcher temperatureMatcher = TEMPERATURE_DUMPSYS_PATTERN.matcher(line);
                if (inCurrentTempSection && temperatureMatcher.matches()) {
                    Log.v(LOG_TAG, "Matched " + line);
                    String name = temperatureMatcher.group(3);
                    MetricUtility.addMetric(
                            MetricUtility.constructKey(
                                    METRIC_KEY_TEMPERATURE, name, METRIC_KEY_VALUE),
                            Double.parseDouble(temperatureMatcher.group(1)), // value group
                            results);
                    MetricUtility.addMetric(
                            MetricUtility.constructKey(
                                    METRIC_KEY_TEMPERATURE, name, METRIC_KEY_TYPE),
                            Integer.parseInt(temperatureMatcher.group(2)), // type group
                            results);
                    MetricUtility.addMetric(
                            MetricUtility.constructKey(
                                    METRIC_KEY_TEMPERATURE, name, METRIC_KEY_STATUS),
                            Integer.parseInt(temperatureMatcher.group(4)), // status group
                            results);
                }

                if (line.contains("Current temperatures")) {
                    inCurrentTempSection = true;
                } else if (line.trim().startsWith("Current") || line.trim().startsWith("Cached")) {
                    // We're entering another section of data.
                    inCurrentTempSection = false;
                }
            }
        } catch (NumberFormatException nfe) {
            Log.e(
                    LOG_TAG,
                    String.format("Couldn't identify temperature info. Error parsing: %s", nfe));
        } catch (IOException ioe) {
            Log.e(LOG_TAG, String.format("Failed to query thermalservice. Error: %s", ioe));
        }
    }

    /**
     * Get latest temperature value for needed name. Return temperature value is in unit of degree
     * Celsius
     */
    public double getTemperature(String name) {
        Map<String, StringBuilder> results = new HashMap<>();
        updateTemperatureMetrics(results);
        String temperatureKey =
                MetricUtility.constructKey(METRIC_KEY_TEMPERATURE, name, METRIC_KEY_VALUE);
        List<Double> values = MetricUtility.getMetricDoubles(temperatureKey, results);
        if (values.size() > 0) {
            double value =
                    values.get(values.size() - 1).doubleValue(); // last value is the latest value.
            Log.v(LOG_TAG, String.format("Got temperature of %s: %,.6f", name, value));
            return value;
        } else {
            throw new IllegalArgumentException(
                    String.format("Failed to get temperature of %s", name));
        }
    }

    /** Remove the statsd config used to track thermal events. */
    @Override
    public boolean stopCollecting() {
        Log.i(LOG_TAG, "Unregistering thermal config from statsd.");
        return getStatsdHelper().removeStatsConfig();
    }

    /** A shorthand name for temperature sensor types used in metric keys. */
    @VisibleForTesting
    static String getShorthandSensorType(int type) {
        switch (type) {
            case TEMPERATURE_TYPE_CPU:
                return "cpu";

            case TEMPERATURE_TYPE_GPU:
                return "gpu";

            case TEMPERATURE_TYPE_BATTERY:
                return "battery";

            case TEMPERATURE_TYPE_SKIN:
                return "skin";

            case TEMPERATURE_TYPE_USB_PORT:
                return "usb_port";

            case TEMPERATURE_TYPE_POWER_AMPLIFIER:
                return "power_amplifier";

            default:
                return "unknown";
        }
    }

    private StatsdHelper getStatsdHelper() {
        if (mStatsdHelper == null) {
            mStatsdHelper = new StatsdHelper();
        }
        return mStatsdHelper;
    }

    @VisibleForTesting
    void setStatsdHelper(StatsdHelper helper) {
        mStatsdHelper = helper;
    }

    private UiDevice getDevice() {
        if (mDevice == null) {
            mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        }
        return mDevice;
    }

    @VisibleForTesting
    void setUiDevice(UiDevice device) {
        mDevice = device;
    }
}
