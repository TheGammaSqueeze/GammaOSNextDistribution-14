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

import static com.android.car.telemetry.AtomsProto.ProcessMemoryState.PAGE_FAULT_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.ProcessMemoryState.PROCESS_NAME_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.ProcessMemoryState.RSS_IN_BYTES_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.ProcessMemoryState.SWAP_IN_BYTES_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.ProcessMemoryState.UID_FIELD_NUMBER;
import static com.android.car.telemetry.publisher.Constants.STATS_BUNDLE_KEY_ELAPSED_TIMESTAMP;
import static com.android.car.telemetry.publisher.Constants.STATS_BUNDLE_KEY_PREFIX;

import static com.google.common.truth.Truth.assertThat;

import android.os.PersistableBundle;
import android.util.SparseArray;

import com.android.car.telemetry.AtomsProto;
import com.android.car.telemetry.StatsLogProto;
import com.android.car.telemetry.StatsLogProto.AggregatedAtomInfo;
import com.android.car.telemetry.publisher.HashUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RunWith(JUnit4.class)
public class GaugeMetricDataConverterTest {
    @Test
    public void testConvertGaugeDataList_putsCorrectDataIntoPersistableBundle()
            throws StatsConversionException {
        Long hash1 = HashUtils.murmur2Hash64("process.name.A");
        Long hash2 = HashUtils.murmur2Hash64("process.name.B");
        List<StatsLogProto.GaugeMetricData> gaugeDataList = Arrays.asList(
                StatsLogProto.GaugeMetricData.newBuilder()
                        .addBucketInfo(StatsLogProto.GaugeBucketInfo.newBuilder()
                                .addAtom(AtomsProto.Atom.newBuilder()
                                        .setProcessMemoryState(
                                                AtomsProto.ProcessMemoryState.newBuilder()
                                                    .setPageFault(1000L)
                                                    .setRssInBytes(1234L)))
                                .addElapsedTimestampNanos(12345678L)
                                .addAtom(AtomsProto.Atom.newBuilder()
                                        .setProcessMemoryState(
                                                AtomsProto.ProcessMemoryState.newBuilder()
                                                    .setPageFault(1100L)
                                                    .setRssInBytes(2345L)))
                                .addElapsedTimestampNanos(23456789L))
                        .addBucketInfo(StatsLogProto.GaugeBucketInfo.newBuilder()
                                .addAtom(AtomsProto.Atom.newBuilder()
                                        .setProcessMemoryState(
                                                AtomsProto.ProcessMemoryState.newBuilder()
                                                    .setPageFault(1200L)
                                                    .setRssInBytes(3456L)))
                                .addElapsedTimestampNanos(34567890L))
                        .addDimensionLeafValuesInWhat(StatsLogProto.DimensionsValue.newBuilder()
                                .setValueInt(123))
                        .addDimensionLeafValuesInWhat(StatsLogProto.DimensionsValue.newBuilder()
                                .setValueStrHash(hash1))
                        .addDimensionLeafValuesInWhat(StatsLogProto.DimensionsValue.newBuilder()
                                .setValueLong(11111111L))
                        .build(),
                StatsLogProto.GaugeMetricData.newBuilder()
                        .addBucketInfo(StatsLogProto.GaugeBucketInfo.newBuilder()
                                .addAtom(AtomsProto.Atom.newBuilder()
                                        .setProcessMemoryState(
                                                AtomsProto.ProcessMemoryState.newBuilder()
                                                    .setPageFault(1300L)
                                                    .setRssInBytes(4567L)))
                                .addElapsedTimestampNanos(445678901L))
                        .addDimensionLeafValuesInWhat(StatsLogProto.DimensionsValue.newBuilder()
                                .setValueInt(234))
                        .addDimensionLeafValuesInWhat(StatsLogProto.DimensionsValue.newBuilder()
                                .setValueStrHash(hash2))
                        .addDimensionLeafValuesInWhat(StatsLogProto.DimensionsValue.newBuilder()
                                .setValueLong(22222222L))
                        .build()
        );
        List<Integer> dimensionsFieldsIds = Arrays.asList(1, 2, 8);
        Map<Long, String> hashToStringMap = Map.of(
                hash1, "process.name.1",
                hash2, "process.name.2");
        SparseArray<AtomFieldAccessor<AtomsProto.ProcessMemoryState, ?>> accessorMap =
                new ProcessMemoryStateConverter().getAtomFieldAccessorMap();

        PersistableBundle bundle = GaugeMetricDataConverter.convertGaugeDataList(
                gaugeDataList, dimensionsFieldsIds, hashToStringMap);

        // For each atom 2 fields were set, additionally 3 fields were encoded in dimension values,
        // and 1 elapsed time array, so 6 arrays are expected in the bundle.
        assertThat(bundle.size()).isEqualTo(6);
        assertThat(bundle.getIntArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(UID_FIELD_NUMBER).getFieldName()))
            .asList().containsExactly(123, 123, 123, 234).inOrder();
        assertThat(Arrays.asList(bundle.getStringArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        PROCESS_NAME_FIELD_NUMBER).getFieldName())))
            .containsExactly("process.name.1", "process.name.1",
                        "process.name.1", "process.name.2").inOrder();
        assertThat(bundle.getLongArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(PAGE_FAULT_FIELD_NUMBER).getFieldName()))
            .asList().containsExactly(1000L, 1100L, 1200L, 1300L).inOrder();
        assertThat(bundle.getLongArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        RSS_IN_BYTES_FIELD_NUMBER).getFieldName()))
            .asList().containsExactly(1234L, 2345L, 3456L, 4567L).inOrder();
        assertThat(bundle.getLongArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        SWAP_IN_BYTES_FIELD_NUMBER).getFieldName()))
            .asList().containsExactly(11111111L, 11111111L, 11111111L, 22222222L).inOrder();
        assertThat(bundle.getLongArray(STATS_BUNDLE_KEY_ELAPSED_TIMESTAMP))
            .asList().containsExactly(12345678L, 23456789L, 34567890L, 445678901L).inOrder();
    }

    @Test
    public void testConvertGaugeDataList_forAggregatedAtoms_putsCorrectDataIntoPersistableBundle()
            throws StatsConversionException {
        Long hash1 = HashUtils.murmur2Hash64("process.name.A");
        Long hash2 = HashUtils.murmur2Hash64("process.name.B");
        List<StatsLogProto.GaugeMetricData> gaugeDataList = Arrays.asList(
                StatsLogProto.GaugeMetricData.newBuilder()
                        .addBucketInfo(StatsLogProto.GaugeBucketInfo.newBuilder()
                                .addAggregatedAtomInfo(AggregatedAtomInfo.newBuilder()
                                        .setAtom(AtomsProto.Atom.newBuilder()
                                                .setProcessMemoryState(
                                                        AtomsProto.ProcessMemoryState.newBuilder()
                                                                .setPageFault(1000L)
                                                                .setRssInBytes(1234L)))
                                        .addElapsedTimestampNanos(12345678L)
                                        .addElapsedTimestampNanos(12345679L))
                                .addAggregatedAtomInfo(AggregatedAtomInfo.newBuilder()
                                        .setAtom(AtomsProto.Atom.newBuilder()
                                                .setProcessMemoryState(
                                                        AtomsProto.ProcessMemoryState.newBuilder()
                                                                .setPageFault(1100L)
                                                                .setRssInBytes(2345L)))
                                        .addElapsedTimestampNanos(23456789L)))
                        .addBucketInfo(StatsLogProto.GaugeBucketInfo.newBuilder()
                                .addAggregatedAtomInfo(AggregatedAtomInfo.newBuilder()
                                        .setAtom(AtomsProto.Atom.newBuilder()
                                                .setProcessMemoryState(
                                                        AtomsProto.ProcessMemoryState.newBuilder()
                                                                .setPageFault(1200L)
                                                                .setRssInBytes(3456L)))
                                        .addElapsedTimestampNanos(34567890L)
                                        .addElapsedTimestampNanos(34567899L)))
                        .addDimensionLeafValuesInWhat(StatsLogProto.DimensionsValue.newBuilder()
                                .setValueInt(123))
                        .addDimensionLeafValuesInWhat(StatsLogProto.DimensionsValue.newBuilder()
                                .setValueStrHash(hash1))
                        .addDimensionLeafValuesInWhat(StatsLogProto.DimensionsValue.newBuilder()
                                .setValueLong(11111111L))
                        .build(),
                StatsLogProto.GaugeMetricData.newBuilder()
                        .addBucketInfo(StatsLogProto.GaugeBucketInfo.newBuilder()
                                .addAggregatedAtomInfo(AggregatedAtomInfo.newBuilder()
                                        .setAtom(AtomsProto.Atom.newBuilder()
                                                .setProcessMemoryState(
                                                        AtomsProto.ProcessMemoryState.newBuilder()
                                                                .setPageFault(1300L)
                                                                .setRssInBytes(4567L)))
                                        .addElapsedTimestampNanos(445678901L)))
                        .addDimensionLeafValuesInWhat(StatsLogProto.DimensionsValue.newBuilder()
                                .setValueInt(234))
                        .addDimensionLeafValuesInWhat(StatsLogProto.DimensionsValue.newBuilder()
                                .setValueStrHash(hash2))
                        .addDimensionLeafValuesInWhat(StatsLogProto.DimensionsValue.newBuilder()
                                .setValueLong(22222222L))
                        .build()
        );
        List<Integer> dimensionsFieldsIds = Arrays.asList(1, 2, 8);
        Map<Long, String> hashToStringMap = Map.of(
                hash1, "process.name.1",
                hash2, "process.name.2");
        SparseArray<AtomFieldAccessor<AtomsProto.ProcessMemoryState, ?>> accessorMap =
                new ProcessMemoryStateConverter().getAtomFieldAccessorMap();

        PersistableBundle bundle = GaugeMetricDataConverter.convertGaugeDataList(
                gaugeDataList, dimensionsFieldsIds, hashToStringMap);

        // For each atom 2 fields were set, additionally 3 fields were encoded in dimension values,
        // and 1 elapsed time array, so 6 arrays are expected in the bundle.
        assertThat(bundle.size()).isEqualTo(6);
        assertThat(bundle.getIntArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(UID_FIELD_NUMBER).getFieldName()))
            .asList().containsExactly(123, 123, 123, 123, 123, 234).inOrder();
        assertThat(Arrays.asList(bundle.getStringArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        PROCESS_NAME_FIELD_NUMBER).getFieldName())))
            .containsExactly("process.name.1", "process.name.1", "process.name.1",
                    "process.name.1", "process.name.1", "process.name.2").inOrder();
        assertThat(bundle.getLongArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(PAGE_FAULT_FIELD_NUMBER).getFieldName()))
            .asList().containsExactly(1000L, 1000L, 1100L, 1200L, 1200L, 1300L).inOrder();
        assertThat(bundle.getLongArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        RSS_IN_BYTES_FIELD_NUMBER).getFieldName()))
            .asList().containsExactly(1234L, 1234L, 2345L, 3456L, 3456L, 4567L).inOrder();
        assertThat(bundle.getLongArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        SWAP_IN_BYTES_FIELD_NUMBER).getFieldName()))
            .asList().containsExactly(
                    11111111L, 11111111L, 11111111L, 11111111L, 11111111L, 22222222L).inOrder();
        assertThat(bundle.getLongArray(STATS_BUNDLE_KEY_ELAPSED_TIMESTAMP))
            .asList().containsExactly(
                    12345678L, 12345679L, 23456789L, 34567890L, 34567899L, 445678901L).inOrder();
    }
}
