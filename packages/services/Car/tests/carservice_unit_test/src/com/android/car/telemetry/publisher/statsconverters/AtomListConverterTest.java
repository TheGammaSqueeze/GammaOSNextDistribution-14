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

import static com.android.car.telemetry.publisher.Constants.STATS_BUNDLE_KEY_PREFIX;

import static com.google.common.truth.Truth.assertThat;

import android.os.PersistableBundle;
import android.util.SparseArray;

import com.android.car.telemetry.AtomsProto.AppStartMemoryStateCaptured;
import com.android.car.telemetry.AtomsProto.Atom;
import com.android.car.telemetry.AtomsProto.ProcessMemoryState;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;

@RunWith(JUnit4.class)
public class AtomListConverterTest {
    @Test
    public void testConvertPushedAtomsListWithUnsetFields_putsCorrectDataToPersistableBundle()
            throws StatsConversionException {
        List<Atom> pushedAtomsList = Arrays.asList(
                Atom.newBuilder()
                        .setAppStartMemoryStateCaptured(
                                AppStartMemoryStateCaptured.newBuilder()
                                        .setUid(1000)
                                        .setActivityName("activityName1")
                                        .setRssInBytes(1234L))
                        .build(),
                Atom.newBuilder()
                        .setAppStartMemoryStateCaptured(
                                AppStartMemoryStateCaptured.newBuilder()
                                        .setUid(1100)
                                        .setActivityName("activityName2")
                                        .setRssInBytes(2345L))
                        .build()
        );
        SparseArray<AtomFieldAccessor<AppStartMemoryStateCaptured, ?>> accessorMap =
                new AppStartMemoryStateCapturedConverter().getAtomFieldAccessorMap();

        PersistableBundle bundle = AtomListConverter.convert(pushedAtomsList, null, null, null);

        assertThat(bundle.size()).isEqualTo(3);
        assertThat(bundle.getIntArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        AppStartMemoryStateCaptured.UID_FIELD_NUMBER).getFieldName()))
            .asList().containsExactly(1000, 1100).inOrder();
        assertThat(Arrays.asList(bundle.getStringArray(
                    STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                            AppStartMemoryStateCaptured.ACTIVITY_NAME_FIELD_NUMBER)
                            .getFieldName())))
            .containsExactly("activityName1", "activityName2").inOrder();
        assertThat(bundle.getLongArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        AppStartMemoryStateCaptured.RSS_IN_BYTES_FIELD_NUMBER).getFieldName()))
            .asList().containsExactly(1234L, 2345L).inOrder();
    }

    @Test
    public void testConvertPulledAtomsListWithUnsetFields_putsCorrectDataToPersistableBundle()
            throws StatsConversionException {
        List<Atom> pulledAtomsList = Arrays.asList(
                Atom.newBuilder()
                        .setProcessMemoryState(ProcessMemoryState.newBuilder()
                                .setUid(1000)
                                .setProcessName("processName1")
                                .setRssInBytes(1234L))
                        .build(),
                Atom.newBuilder()
                        .setProcessMemoryState(ProcessMemoryState.newBuilder()
                                .setUid(1100)
                                .setProcessName("processName2")
                                .setRssInBytes(2345L))
                        .build()
        );
        SparseArray<AtomFieldAccessor<ProcessMemoryState, ?>> accessorMap =
                new ProcessMemoryStateConverter().getAtomFieldAccessorMap();

        PersistableBundle bundle = AtomListConverter.convert(pulledAtomsList, null, null, null);

        assertThat(bundle.size()).isEqualTo(3);
        assertThat(bundle.getIntArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(ProcessMemoryState.UID_FIELD_NUMBER)
                        .getFieldName()))
            .asList().containsExactly(1000, 1100).inOrder();
        assertThat(Arrays.asList(bundle.getStringArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        ProcessMemoryState.PROCESS_NAME_FIELD_NUMBER).getFieldName())))
            .containsExactly("processName1", "processName2").inOrder();
        assertThat(bundle.getLongArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        ProcessMemoryState.RSS_IN_BYTES_FIELD_NUMBER).getFieldName()))
            .asList().containsExactly(1234L, 2345L).inOrder();
    }
}
