/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tradefed.util.proto;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.DataType;
import com.android.tradefed.metrics.proto.MetricMeasurement.Directionality;
import com.android.tradefed.metrics.proto.MetricMeasurement.Measurements;
import com.android.tradefed.metrics.proto.MetricMeasurement.Measurements.MeasurementCase;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric.Builder;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Utility class to help with the Map<String, String> to Map<String, Metric> transition. */
public class TfMetricProtoUtil {

    /**
     * Conversion of Map<String, Metric> to Map<String, String>. All the single value string
     * representation are used, list representation are not converted and will be lost.
     */
    public static Map<String, String> compatibleConvert(Map<String, Metric> map) {
        Map<String, String> oldFormat = new LinkedHashMap<>();
        for (String key : map.keySet()) {
            Measurements measures = map.get(key).getMeasurements();
            MeasurementCase set = measures.getMeasurementCase();
            String value = "";
            switch (set) {
                case SINGLE_DOUBLE:
                    value = Double.toString(measures.getSingleDouble());
                    break;
                case SINGLE_INT:
                    value = Long.toString(measures.getSingleInt());
                    break;
                case SINGLE_STRING:
                    value = measures.getSingleString();
                    break;
                case MEASUREMENT_NOT_SET:
                    CLog.d("No measurements was set for key '%s'", key);
                    continue;
                default:
                    CLog.d(
                            "Could not convert complex '%s' type to String. Use the new metric "
                                    + "interface.",
                            set);
                    continue;
            }
            oldFormat.put(key, value);
        }
        return oldFormat;
    }

    /**
     * Conversion from Map<String, String> to HashMap<String, Metric>. In order to go to the new
     * interface. Information might only be partially populated because of the old format
     * limitations.
     */
    public static HashMap<String, Metric> upgradeConvert(Map<String, String> metrics) {
        return upgradeConvert(metrics, false);
    }

    /**
     * Conversion from Map<String, String> to HashMap<String, Metric>. In order to go to the new
     * interface. Information might only be partially populated because of the old format
     * limitations.
     *
     * @param smartNumbers convert numbers to int metrics
     */
    public static HashMap<String, Metric> upgradeConvert(
            Map<String, String> metrics, boolean smartNumbers) {
        HashMap<String, Metric> newFormat = new LinkedHashMap<>();
        for (String key : metrics.keySet()) {
            Metric metric = null;
            String stringMetric = metrics.get(key);
            if (smartNumbers) {
                Long numMetric = isLong(stringMetric);
                if (numMetric != null) {
                    metric = createSingleValue(numMetric.longValue(), null);
                }
            }
            // Default to String metric
            if (metric == null) {
                metric = stringToMetric(stringMetric);
            }
            newFormat.put(key, metric);
        }
        return newFormat;
    }

    private static Long isLong(String strNum) {
        if (strNum == null) {
            return null;
        }
        try {
            return Long.parseLong(strNum);
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    /**
     * Convert a simple String metric (old format) to a {@link Metric} (new format).
     *
     * @param metric The string containing a metric.
     * @return The created {@link Metric}
     */
    public static Metric stringToMetric(String metric) {
        Measurements measures = Measurements.newBuilder().setSingleString(metric).build();
        Metric m =
                Metric.newBuilder()
                        .setMeasurements(measures)
                        .setDirection(Directionality.DIRECTIONALITY_UNSPECIFIED)
                        .setType(DataType.RAW)
                        .build();
        return m;
    }

    /**
     * Create a {@link Metric} for a single long/int value, and optionally provide a unit.
     *
     * @param value The value that will be stored.
     * @param unit the unit of the value, or null if no unit.
     * @return a {@link Metric} populated with the informations.
     */
    public static Metric createSingleValue(long value, String unit) {
        Measurements measure = Measurements.newBuilder().setSingleInt(value).build();
        Builder metricBuilder = Metric.newBuilder().setType(DataType.RAW).setMeasurements(measure);
        if (unit != null) {
            metricBuilder.setUnit(unit);
        }
        return metricBuilder.build();
    }
}
