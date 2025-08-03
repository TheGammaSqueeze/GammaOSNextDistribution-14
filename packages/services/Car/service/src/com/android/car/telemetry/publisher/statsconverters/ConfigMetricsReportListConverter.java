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

package com.android.car.telemetry.publisher.statsconverters;

import android.annotation.NonNull;
import android.os.PersistableBundle;

import com.android.car.telemetry.StatsLogProto;
import com.android.car.telemetry.publisher.HashUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class for converting metrics report list data to {@link PersistableBundle} compatible format.
 */
public class ConfigMetricsReportListConverter {
    /**
     * Converts metrics report list to map of metric_id to {@link PersistableBundle} format where
     * each PersistableBundle containing arrays of metric fields data. Multiple reports with the
     * same metrics are combined on the metrics.
     *
     * Example:
     * Given a ConfigMetricsReportList like this:
     * {
     *   reports: {
     *     metrics: {
     *       metric_id: 1234
     *       event_metrics: {
     *         data: {...}
     *       }
     *       metric_id: 2345
     *       event_metrics: {
     *         data: {...}
     *       }
     *       metric_id: 3456
     *       gauge_metrics: {
     *         data: {...}
     *       }
     *     }
     *     metrics: {
     *       metric_id: 3456
     *       gauge_metrics: {
     *         data: {...}
     *       }
     *     }
     *   }
     * }
     * Will result in a map of this form (note metric 3456 is combined from the two reports):
     * {
     *   "1234" : {...}  // PersistableBundle containing metric 1234's data
     *   "2345" : {...}
     *   "3456" : {...}
     * }
     *
     * @param reportList the {@link StatsLogProto.ConfigMetricsReportList} to be converted.
     * @return a {@link PersistableBundle} containing mapping of metric id to metric data.
     * @throws StatsConversionException if atom field mismatch or can't convert dimension value.
     */
    @NonNull
    public static Map<Long, PersistableBundle> convert(
            @NonNull StatsLogProto.ConfigMetricsReportList reportList)
            throws StatsConversionException {
        // Map metric id to StatsLogReport list so that separate reports can be combined.
        Map<Long, List<StatsLogProto.StatsLogReport>> metricsStatsReportMap = new HashMap<>();
        Set<String> stringsSet = new HashSet<>();
        // ConfigMetricsReportList is for one config. Normally only 1 report exists unless
        // the previous report did not upload after shutdown, then at most 2 reports can exist.
        for (StatsLogProto.ConfigMetricsReport report : reportList.getReportsList()) {
            stringsSet.addAll(report.getStringsList());
            // Each statsReport is for a different metric in the report.
            for (StatsLogProto.StatsLogReport statsReport : report.getMetricsList()) {
                if (statsReport.getDataCase()
                        == StatsLogProto.StatsLogReport.DataCase.DATA_NOT_SET) {
                    continue;
                }
                Long metricId = statsReport.getMetricId();
                if (!metricsStatsReportMap.containsKey(metricId)) {
                    metricsStatsReportMap.put(
                            metricId, new ArrayList<StatsLogProto.StatsLogReport>());
                }
                metricsStatsReportMap.get(metricId).add(statsReport);
            }
        }
        Map<Long, PersistableBundle> metricIdBundleMap = new HashMap<>();
        // For each metric extract the metric data list from the combined stats reports,
        // convert to bundle data.
        for (Map.Entry<Long, List<StatsLogProto.StatsLogReport>>
                    entry : metricsStatsReportMap.entrySet()) {
            PersistableBundle statsReportBundle = null;
            List<StatsLogProto.StatsLogReport> statsReportList = entry.getValue();
            switch (statsReportList.get(0).getDataCase()) {
                case EVENT_METRICS:
                    List<StatsLogProto.EventMetricData> eventDataList = new ArrayList<>();
                    for (StatsLogProto.StatsLogReport statsReport : statsReportList) {
                        eventDataList.addAll(statsReport.getEventMetrics().getDataList());
                    }
                    statsReportBundle =
                            EventMetricDataConverter.convertEventDataList(eventDataList);
                    break;
                case GAUGE_METRICS:
                    List<StatsLogProto.GaugeMetricData> gaugeDataList = new ArrayList<>();
                    for (StatsLogProto.StatsLogReport statsReport : statsReportList) {
                        gaugeDataList.addAll(statsReport.getGaugeMetrics().getDataList());
                    }
                    statsReportBundle = GaugeMetricDataConverter.convertGaugeDataList(
                            gaugeDataList,
                            extractDimensionFieldsIds(
                                    statsReportList.get(0).getDimensionsPathInWhat()),
                            createDimensionHashToStringMap(stringsSet));
                    break;
                default:
                    break;
            }
            if (statsReportBundle != null) {
                metricIdBundleMap.put(entry.getKey(), statsReportBundle);
            }
        }
        return metricIdBundleMap;
    }

    /**
     * Creates a hash to string mapping for decoding {@link StatsLogProto.DimensionsValue}.
     *
     * <p> The mapping is created using murmur2 hashing algorithm.
     *
     * @param dimensionStrings the strings that were encoded in dimension values.
     * @return hash to string mapping.
     */
    @NonNull
    private static Map<Long, String> createDimensionHashToStringMap(
            @NonNull Set<String> dimensionStrings) {
        Map<Long, String> hashToStringMap = new HashMap<>();
        for (String str : dimensionStrings) {
            Long hash = HashUtils.murmur2Hash64(str);
            hashToStringMap.put(hash, str);
        }
        return hashToStringMap;
    }

    /**
     * Extracts the field ids of atom fields that were encoded in the dimension values.
     *
     * @param dimensionsPath the root level DimensionsValue. Contains field ids instead of values.
     * @return list of atom field ids.
     */
    @NonNull
    private static List<Integer> extractDimensionFieldsIds(
            @NonNull StatsLogProto.DimensionsValue dimensionsPath) {
        List<Integer> dimensionsFieldsIds = new ArrayList<>();
        StatsLogProto.DimensionsValueTuple dimensionTuple = dimensionsPath.getValueTuple();
        for (StatsLogProto.DimensionsValue dv : dimensionTuple.getDimensionsValueList()) {
            dimensionsFieldsIds.add(dv.getField());
        }
        return dimensionsFieldsIds;
    }
}
