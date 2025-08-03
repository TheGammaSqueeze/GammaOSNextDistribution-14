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

import static com.android.car.telemetry.AtomsProto.ProcessStartTime.HOSTING_NAME_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.ProcessStartTime.PID_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.ProcessStartTime.PROCESS_NAME_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.ProcessStartTime.PROCESS_START_DELAY_MILLIS_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.ProcessStartTime.UID_FIELD_NUMBER;
import static com.android.car.telemetry.publisher.Constants.STATS_BUNDLE_KEY_PREFIX;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.PersistableBundle;
import android.util.SparseArray;

import com.android.car.telemetry.AtomsProto;
import com.android.car.telemetry.AtomsProto.ProcessStartTime;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;

@RunWith(JUnit4.class)
public class ProcessStartTimeConverterTest {
    private static final String HOSTING_NAME_1 = "com.example.process1.MainActivity";
    private static final String HOSTING_NAME_2 = "com.example.process2.MainActivity";

    private static final String PROCESS_NAME_1 = "com.example.process1";
    private static final String PROCESS_NAME_2 = "com.example.process2";

    private static final AtomsProto.Atom ATOM_A =
            AtomsProto.Atom.newBuilder()
                    .setProcessStartTime(ProcessStartTime.newBuilder()
                            .setUid(1000000)
                            .setPid(1000)
                            .setHostingName(HOSTING_NAME_1)
                            .setProcessName(PROCESS_NAME_1)
                            .setProcessStartDelayMillis(100))
                    .build();

    private static final AtomsProto.Atom ATOM_B =
            AtomsProto.Atom.newBuilder()
                    .setProcessStartTime(ProcessStartTime.newBuilder()
                            .setUid(2000000)
                            .setPid(2000)
                            .setHostingName(HOSTING_NAME_2)
                            .setProcessName(PROCESS_NAME_2)
                            .setProcessStartDelayMillis(200))
                    .build();

    private static final AtomsProto.Atom ATOM_MISMATCH =
            AtomsProto.Atom.newBuilder()
                    .setProcessStartTime(ProcessStartTime.newBuilder()
                    .setUid(1000)
                    .setProcessStartTimeMillis(500000))
            .build();
    // Subject of the test.
    private ProcessStartTimeConverter mConverter = new ProcessStartTimeConverter();

    @Test
    public void testConvertAtomsListWithDimensionValues_putsCorrectDataToPersistableBundle()
            throws StatsConversionException {
        List<AtomsProto.Atom> atomsList = Arrays.asList(ATOM_A, ATOM_B);

        SparseArray<AtomFieldAccessor<ProcessStartTime, ?>> accessorMap =
                mConverter.getAtomFieldAccessorMap();

        PersistableBundle bundle = mConverter.convert(atomsList, null, null, null);

        assertThat(bundle.size()).isEqualTo(5);
        assertThat(bundle.getIntArray(
                    STATS_BUNDLE_KEY_PREFIX + accessorMap.get(UID_FIELD_NUMBER).getFieldName()))
                .asList().containsExactly(1000000, 2000000).inOrder();
        assertThat(bundle.getIntArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(PID_FIELD_NUMBER).getFieldName()))
                .asList().containsExactly(1000, 2000);
        assertThat(Arrays.asList(bundle.getStringArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        PROCESS_NAME_FIELD_NUMBER).getFieldName())))
                .containsExactly(PROCESS_NAME_1, PROCESS_NAME_2).inOrder();
        assertThat(Arrays.asList(bundle.getStringArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        HOSTING_NAME_FIELD_NUMBER).getFieldName())))
                .containsExactly(HOSTING_NAME_1, HOSTING_NAME_2).inOrder();
        assertThat(bundle.getIntArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        PROCESS_START_DELAY_MILLIS_FIELD_NUMBER).getFieldName()))
                .asList().containsExactly(100, 200);
    }

    @Test
    public void testAtomSetFieldInconsistency_throwsException() {
        List<AtomsProto.Atom> atomsList = Arrays.asList(ATOM_A, ATOM_MISMATCH);

        assertThrows(
                StatsConversionException.class,
                () -> mConverter.convert(
                        atomsList,
                        null,
                        null,
                        null));
    }
}
