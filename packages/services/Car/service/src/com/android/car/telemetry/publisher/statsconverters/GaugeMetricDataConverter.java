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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.PersistableBundle;

import com.android.car.telemetry.AtomsProto.Atom;
import com.android.car.telemetry.StatsLogProto.AggregatedAtomInfo;
import com.android.car.telemetry.StatsLogProto.DimensionsValue;
import com.android.car.telemetry.StatsLogProto.GaugeBucketInfo;
import com.android.car.telemetry.StatsLogProto.GaugeMetricData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Class for converting gauge metric data to {@link PersistableBundle} compatible format.
 */
public class GaugeMetricDataConverter {
    /**
     * Converts a list of {@link StatsLogProto.GaugeMetricData} to {@link PersistableBundle}
     * format such that along with the elapsed time array each field of the atom has an associated
     * array containing the field's data in order received, matching the elapsed time array order.
     * The atoms are extracted out of the each bucket while preserving the order they had in the
     * bucket.
     *
     * Example:
     * {
     *   elapsed_timestamp_nanos: [32948395739, 45623453646, ...]
     *   uid: [1000, 1100, ...]
     *   ...
     * }
     *
     * @param gaugeDataList the list of {@link StatsLogProto.GaugeMetricData} to be converted.
     * @param dimensionsFieldsIds field ids for fields that are encoded in {@link DimensionsValue}.
     * @param hashToStringMap hash to string mapping for decoding the some dimension values.
     * @return {@link PersistableBundle} that holds the converted values.
     * @throws StatsConversionException if atom field mismatch or can't convert dimension value.
     */
    @NonNull
    static PersistableBundle convertGaugeDataList(
            @NonNull List<GaugeMetricData> gaugeDataList,
            @Nullable List<Integer> dimensionsFieldsIds,
            @Nullable Map<Long, String> hashToStringMap) throws StatsConversionException {
        List<Long> elapsedTimes = new ArrayList<>();
        List<Atom> atoms = new ArrayList<>();
        // This list contains dimensionsValues for each atom, matching in index and list size.
        List<List<DimensionsValue>> dimensionsValuesList = new ArrayList<>();
        for (GaugeMetricData gaugeData : gaugeDataList) {
            // The dimensionsValue is same for all stoms in the same GaugeMetricData.
            List<DimensionsValue> dimensionsValues = gaugeData.getDimensionLeafValuesInWhatList();
            for (GaugeBucketInfo bi : gaugeData.getBucketInfoList()) {
                if (bi.getAggregatedAtomInfoCount() > 0) {
                    List<AggregatedAtomInfo> aggregateList = bi.getAggregatedAtomInfoList();
                    for (AggregatedAtomInfo aggregate : aggregateList) {
                        Atom atom = aggregate.getAtom();
                        for (long elapsedTime : aggregate.getElapsedTimestampNanosList()) {
                            elapsedTimes.add(elapsedTime);
                            atoms.add(atom);
                            dimensionsValuesList.add(dimensionsValues);
                        }
                    }
                } else {
                    elapsedTimes.addAll(bi.getElapsedTimestampNanosList());
                    for (Atom atom : bi.getAtomList()) {
                        atoms.add(atom);
                        dimensionsValuesList.add(dimensionsValues);
                    }
                }
            }
        }
        PersistableBundle bundle = AtomListConverter.convert(
                atoms,
                dimensionsFieldsIds,
                dimensionsValuesList,
                hashToStringMap);
        long[] elapsedTimesArray = new long[elapsedTimes.size()];
        for (int i = 0; i < elapsedTimes.size(); ++i) {
            elapsedTimesArray[i] = elapsedTimes.get(i);
        }
        bundle.putLongArray(STATS_BUNDLE_KEY_ELAPSED_TIMESTAMP, elapsedTimesArray);
        return bundle;
    }
}
