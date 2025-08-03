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

import static com.android.car.telemetry.AtomsProto.AppCrashOccurred.ERROR_SOURCE_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.AppCrashOccurred.PACKAGE_NAME_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.AppCrashOccurred.PID_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.AppCrashOccurred.UID_FIELD_NUMBER;
import static com.android.car.telemetry.publisher.Constants.STATS_BUNDLE_KEY_PREFIX;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.PersistableBundle;
import android.util.SparseArray;

import com.android.car.telemetry.AtomsProto;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;

@RunWith(JUnit4.class)
public class AppCrashOccurredConverterTest {
    private static final String PACKAGE_NAME_1 = "com.example.package1";
    private static final String PACKAGE_NAME_2 = "com.example.package2";
    private static final AtomsProto.Atom ATOM_A =
            AtomsProto.Atom.newBuilder()
                    .setAppCrashOccurred(AtomsProto.AppCrashOccurred.newBuilder()
                            .setUid(1000)
                            .setPid(12345)
                            .setPackageName(PACKAGE_NAME_1)
                            .setErrorSource(AtomsProto.ErrorSource.DATA_APP))
                    .build();
    private static final AtomsProto.Atom ATOM_B =
            AtomsProto.Atom.newBuilder()
                    .setAppCrashOccurred(AtomsProto.AppCrashOccurred.newBuilder()
                            .setUid(2000)
                            .setPid(67890)
                            .setPackageName(PACKAGE_NAME_2)
                            .setErrorSource(AtomsProto.ErrorSource.SYSTEM_APP))
                    .build();
    private static final AtomsProto.Atom ATOM_MISMATCH =
            AtomsProto.Atom.newBuilder()
                    .setAppCrashOccurred(AtomsProto.AppCrashOccurred.newBuilder()
                            .setEventType("test"))
                    .build();

    // Subject of the test.
    private AppCrashOccurredConverter mConverter = new AppCrashOccurredConverter();

    @Test
    public void testConvertAtomsListWithDimensionValues_putsCorrectDataToPersistableBundle()
            throws StatsConversionException {
        List<AtomsProto.Atom> atomsList = Arrays.asList(ATOM_A, ATOM_B);

        SparseArray<AtomFieldAccessor<AtomsProto.AppCrashOccurred, ?>> accessorMap =
                mConverter.getAtomFieldAccessorMap();

        PersistableBundle bundle = mConverter.convert(atomsList, null, null, null);

        assertThat(bundle.size()).isEqualTo(4);
        assertThat(bundle.getIntArray(
                    STATS_BUNDLE_KEY_PREFIX + accessorMap.get(UID_FIELD_NUMBER).getFieldName()))
                .asList().containsExactly(1000, 2000).inOrder();
        assertThat(bundle.getIntArray(
                    STATS_BUNDLE_KEY_PREFIX + accessorMap.get(PID_FIELD_NUMBER).getFieldName()))
                .asList().containsExactly(12345, 67890).inOrder();
        assertThat(bundle.getStringArray(
                    STATS_BUNDLE_KEY_PREFIX + accessorMap.get(PACKAGE_NAME_FIELD_NUMBER)
                            .getFieldName()))
                .asList().containsExactly(PACKAGE_NAME_1, PACKAGE_NAME_2).inOrder();
        assertThat(bundle.getIntArray(
                    STATS_BUNDLE_KEY_PREFIX + accessorMap.get(ERROR_SOURCE_FIELD_NUMBER)
                            .getFieldName()))
                .asList().containsExactly(1, 2).inOrder();  // DATA_APP=1, SYSTEM_APP=2
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
