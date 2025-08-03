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

import static com.android.car.telemetry.AtomsProto.AppStartMemoryStateCaptured.ACTIVITY_NAME_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.AppStartMemoryStateCaptured.RSS_IN_BYTES_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.AppStartMemoryStateCaptured.UID_FIELD_NUMBER;
import static com.android.car.telemetry.publisher.Constants.STATS_BUNDLE_KEY_ELAPSED_TIMESTAMP;
import static com.android.car.telemetry.publisher.Constants.STATS_BUNDLE_KEY_PREFIX;

import static com.google.common.truth.Truth.assertThat;

import android.os.PersistableBundle;
import android.util.SparseArray;

import com.android.car.telemetry.AtomsProto.AppStartMemoryStateCaptured;
import com.android.car.telemetry.AtomsProto.Atom;
import com.android.car.telemetry.StatsLogProto.AggregatedAtomInfo;
import com.android.car.telemetry.StatsLogProto.EventMetricData;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;

@RunWith(JUnit4.class)
public class EventMetricDataConverterTest {
    @Test
    public void testConvertEventDataList_putsCorrectDataIntoPersistableBundle()
            throws StatsConversionException {
        List<EventMetricData> eventDataList = Arrays.asList(
                EventMetricData.newBuilder()
                        .setElapsedTimestampNanos(12345678L)
                        .setAtom(Atom.newBuilder()
                                .setAppStartMemoryStateCaptured(
                                        AppStartMemoryStateCaptured.newBuilder()
                                                .setUid(1000)
                                                .setActivityName("activityName1")
                                                .setRssInBytes(1234L)))
                        .build(),
                EventMetricData.newBuilder()
                        .setElapsedTimestampNanos(23456789L)
                        .setAtom(Atom.newBuilder()
                                .setAppStartMemoryStateCaptured(
                                        AppStartMemoryStateCaptured.newBuilder()
                                                .setUid(1100)
                                                .setActivityName("activityName2")
                                                .setRssInBytes(2345L)))
                        .build()
        );
        SparseArray<AtomFieldAccessor<AppStartMemoryStateCaptured, ?>> accessorMap =
                new AppStartMemoryStateCapturedConverter().getAtomFieldAccessorMap();

        PersistableBundle bundle = EventMetricDataConverter.convertEventDataList(eventDataList);

        assertThat(bundle.size()).isEqualTo(4);
        assertThat(bundle.getLongArray(STATS_BUNDLE_KEY_ELAPSED_TIMESTAMP))
            .asList().containsExactly(12345678L, 23456789L).inOrder();
        assertThat(bundle.getIntArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(UID_FIELD_NUMBER).getFieldName()))
            .asList().containsExactly(1000, 1100).inOrder();
        assertThat(Arrays.asList(bundle.getStringArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        ACTIVITY_NAME_FIELD_NUMBER).getFieldName())))
            .containsExactly("activityName1", "activityName2").inOrder();
        assertThat(bundle.getLongArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        RSS_IN_BYTES_FIELD_NUMBER).getFieldName()))
            .asList().containsExactly(1234L, 2345L).inOrder();
    }

    @Test
    public void testConvertEventDataList_forAggregatedAtoms_putsCorrectDataIntoPersistableBundle()
            throws StatsConversionException {
        List<EventMetricData> eventDataList = Arrays.asList(
                EventMetricData.newBuilder()
                        .setAggregatedAtomInfo(AggregatedAtomInfo.newBuilder()
                                .setAtom(Atom.newBuilder()
                                        .setAppStartMemoryStateCaptured(
                                                AppStartMemoryStateCaptured.newBuilder()
                                                        .setUid(1000)
                                                        .setActivityName("activityName1")
                                                        .setRssInBytes(1234L)))
                                .addElapsedTimestampNanos(12345678L)
                                .addElapsedTimestampNanos(12345679L))
                        .build(),
                EventMetricData.newBuilder()
                        .setElapsedTimestampNanos(23456789L)
                        .setAggregatedAtomInfo(AggregatedAtomInfo.newBuilder()
                                .setAtom(Atom.newBuilder()
                                        .setAppStartMemoryStateCaptured(
                                                AppStartMemoryStateCaptured.newBuilder()
                                                        .setUid(1100)
                                                        .setActivityName("activityName2")
                                                        .setRssInBytes(2345L)))
                                .addElapsedTimestampNanos(23456789L))
                        .build());

        SparseArray<AtomFieldAccessor<AppStartMemoryStateCaptured, ?>> accessorMap =
                new AppStartMemoryStateCapturedConverter().getAtomFieldAccessorMap();

        PersistableBundle bundle = EventMetricDataConverter.convertEventDataList(eventDataList);

        assertThat(bundle.size()).isEqualTo(4);
        assertThat(bundle.getLongArray(STATS_BUNDLE_KEY_ELAPSED_TIMESTAMP))
            .asList().containsExactly(12345678L, 12345679L, 23456789L).inOrder();
        assertThat(bundle.getIntArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(UID_FIELD_NUMBER).getFieldName()))
            .asList().containsExactly(1000, 1000, 1100).inOrder();
        assertThat(Arrays.asList(bundle.getStringArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        ACTIVITY_NAME_FIELD_NUMBER).getFieldName())))
            .containsExactly("activityName1", "activityName1", "activityName2").inOrder();
        assertThat(bundle.getLongArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        RSS_IN_BYTES_FIELD_NUMBER).getFieldName()))
            .asList().containsExactly(1234L, 1234L, 2345L).inOrder();
    }
}
