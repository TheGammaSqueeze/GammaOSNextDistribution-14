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

import static com.android.car.telemetry.publisher.Constants.STATS_BUNDLE_KEY_ELAPSED_TIMESTAMP;
import static com.android.car.telemetry.publisher.Constants.STATS_BUNDLE_KEY_PREFIX;

import static com.google.common.truth.Truth.assertThat;

import android.os.PersistableBundle;
import android.util.SparseArray;

import com.android.car.telemetry.AtomsProto.AppStartMemoryStateCaptured;
import com.android.car.telemetry.AtomsProto.Atom;
import com.android.car.telemetry.AtomsProto.ProcessMemoryState;
import com.android.car.telemetry.StatsLogProto.ConfigMetricsReport;
import com.android.car.telemetry.StatsLogProto.ConfigMetricsReportList;
import com.android.car.telemetry.StatsLogProto.DimensionsValue;
import com.android.car.telemetry.StatsLogProto.DimensionsValueTuple;
import com.android.car.telemetry.StatsLogProto.EventMetricData;
import com.android.car.telemetry.StatsLogProto.GaugeBucketInfo;
import com.android.car.telemetry.StatsLogProto.GaugeMetricData;
import com.android.car.telemetry.StatsLogProto.StatsLogReport;
import com.android.car.telemetry.publisher.HashUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

@RunWith(JUnit4.class)
public class ConfigMetricsReportListConverterTest {
    @Test
    public void testConvertMultipleReports_correctlySetsPersistableBundles()
            throws StatsConversionException {
        String testGaugeMetricProcessName = "process.name";
        Long hash = HashUtils.murmur2Hash64(testGaugeMetricProcessName);
        EventMetricData eventData = EventMetricData.newBuilder()
                .setElapsedTimestampNanos(99999999L)
                .setAtom(Atom.newBuilder()
                        .setAppStartMemoryStateCaptured(
                                AppStartMemoryStateCaptured.newBuilder()
                                        .setUid(1000)
                                        .setActivityName("activityName")
                                        .setRssInBytes(1234L)))
                .build();

        GaugeMetricData gaugeData = GaugeMetricData.newBuilder()
                .addBucketInfo(GaugeBucketInfo.newBuilder()
                        .addAtom(Atom.newBuilder()
                                .setProcessMemoryState(ProcessMemoryState.newBuilder()
                                        .setRssInBytes(4567L)))
                        .addElapsedTimestampNanos(445678901L))
                .addDimensionLeafValuesInWhat(DimensionsValue.newBuilder()
                        .setValueInt(234))
                .addDimensionLeafValuesInWhat(DimensionsValue.newBuilder()
                        .setValueStrHash(hash))
                .build();

        // This report list contains 2 metrics, an event and a gauge metric. There are empty
        // reports, these should be skipped.
        ConfigMetricsReportList reportList = ConfigMetricsReportList.newBuilder()
                .addReports(ConfigMetricsReport.newBuilder()  // Empty event metric report
                        .addMetrics(StatsLogReport.newBuilder()
                                .setMetricId(12345L)))
                .addReports(ConfigMetricsReport.newBuilder()  // Event metric report
                        .addMetrics(StatsLogReport.newBuilder()
                                .setMetricId(12345L)
                                .setEventMetrics(
                                        StatsLogReport.EventMetricDataWrapper.newBuilder()
                                                .addData(eventData))))
                .addReports(ConfigMetricsReport.newBuilder()  // Empty event metric report
                        .addMetrics(StatsLogReport.newBuilder()
                                .setMetricId(12345L)))
                .addReports(ConfigMetricsReport.newBuilder()  // Empty gauge metric report
                        .addMetrics(StatsLogReport.newBuilder()
                                .setMetricId(23456L)))
                .addReports(ConfigMetricsReport.newBuilder()  // Gauge metric report
                        .addMetrics(StatsLogReport.newBuilder()
                                .setMetricId(23456L)
                                .setGaugeMetrics(
                                        StatsLogReport.GaugeMetricDataWrapper.newBuilder()
                                                .addData(gaugeData))
                                .setDimensionsPathInWhat(DimensionsValue.newBuilder()
                                        .setValueTuple(DimensionsValueTuple.newBuilder()
                                                .addDimensionsValue(DimensionsValue.newBuilder()
                                                        .setField(1))
                                                .addDimensionsValue(DimensionsValue.newBuilder()
                                                        .setField(2)))))
                        .addStrings(testGaugeMetricProcessName))
                .addReports(ConfigMetricsReport.newBuilder()  // Empty gauge metric report
                        .addMetrics(StatsLogReport.newBuilder()
                                .setMetricId(23456L)))
                .build();
        SparseArray<AtomFieldAccessor<AppStartMemoryStateCaptured, ?>> appMemAccessorMap =
                new AppStartMemoryStateCapturedConverter().getAtomFieldAccessorMap();
        SparseArray<AtomFieldAccessor<ProcessMemoryState, ?>> procMemAccessorMap =
                new ProcessMemoryStateConverter().getAtomFieldAccessorMap();

        Map<Long, PersistableBundle> map = ConfigMetricsReportListConverter.convert(reportList);

        assertThat(new ArrayList<Long>(map.keySet())).containsExactly(12345L, 23456L);
        PersistableBundle eventBundle = map.get(12345L);
        assertThat(eventBundle.getLongArray(STATS_BUNDLE_KEY_ELAPSED_TIMESTAMP))
            .asList().containsExactly(99999999L);
        assertThat(eventBundle.getIntArray(
                STATS_BUNDLE_KEY_PREFIX + appMemAccessorMap.get(
                        AppStartMemoryStateCaptured.UID_FIELD_NUMBER).getFieldName()))
            .asList().containsExactly(1000);
        assertThat(Arrays.asList(eventBundle.getStringArray(
                STATS_BUNDLE_KEY_PREFIX + appMemAccessorMap.get(
                        AppStartMemoryStateCaptured.ACTIVITY_NAME_FIELD_NUMBER).getFieldName())))
            .containsExactly("activityName");
        assertThat(eventBundle.getLongArray(
                STATS_BUNDLE_KEY_PREFIX + appMemAccessorMap.get(
                        AppStartMemoryStateCaptured.RSS_IN_BYTES_FIELD_NUMBER).getFieldName()))
            .asList().containsExactly(1234L);
        PersistableBundle gaugeBundle = map.get(23456L);
        assertThat(gaugeBundle.getIntArray(
                STATS_BUNDLE_KEY_PREFIX + procMemAccessorMap.get(
                        ProcessMemoryState.UID_FIELD_NUMBER).getFieldName()))
            .asList().containsExactly(234);
        assertThat(Arrays.asList(gaugeBundle.getStringArray(
                STATS_BUNDLE_KEY_PREFIX + procMemAccessorMap.get(
                        ProcessMemoryState.PROCESS_NAME_FIELD_NUMBER).getFieldName())))
            .containsExactly("process.name");
        assertThat(gaugeBundle.getLongArray(
                STATS_BUNDLE_KEY_PREFIX + procMemAccessorMap.get(
                        ProcessMemoryState.RSS_IN_BYTES_FIELD_NUMBER).getFieldName()))
            .asList().containsExactly(4567L);
        assertThat(gaugeBundle.getLongArray(STATS_BUNDLE_KEY_ELAPSED_TIMESTAMP))
            .asList().containsExactly(445678901L);
    }
}
