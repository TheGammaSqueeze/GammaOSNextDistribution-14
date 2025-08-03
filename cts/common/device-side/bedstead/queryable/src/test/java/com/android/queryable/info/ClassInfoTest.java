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

package com.android.queryable.info;

import static com.android.bedstead.nene.utils.ParcelTest.assertParcelsCorrectly;

import static com.google.common.truth.Truth.assertThat;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(BedsteadJUnit4.class)
public final class ClassInfoTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Class<?> TEST_CLASS = ClassInfoTest.class;
    private static final String TEST_CLASS_NAME = ClassInfoTest.class.getName();
    private static final String TEST_CLASS_SIMPLE_NAME = ClassInfoTest.class.getSimpleName();
    private final ClassInfoTest mTestClassInstance = this;

    @Test
    public void classConstructor_setsClassName() {
        ClassInfo classInfo = new ClassInfo(TEST_CLASS);

        assertThat(classInfo.className()).isEqualTo(TEST_CLASS_NAME);
    }

    @Test
    public void instanceConstructor_setsClassName() {
        ClassInfo classInfo = new ClassInfo(mTestClassInstance);

        assertThat(classInfo.className()).isEqualTo(TEST_CLASS_NAME);
    }

    @Test
    public void stringConstructor_setsClassName() {
        ClassInfo classInfo = new ClassInfo(TEST_CLASS_NAME);

        assertThat(classInfo.className()).isEqualTo(TEST_CLASS_NAME);
    }

    @Test
    public void simpleName_getsSimpleName() {
        ClassInfo classInfo = new ClassInfo(TEST_CLASS_NAME);

        assertThat(classInfo.simpleName()).isEqualTo(TEST_CLASS_SIMPLE_NAME);
    }

    @Test
    public void parcel_parcelsCorrectly() {
        ClassInfo classInfo = new ClassInfo(TEST_CLASS_NAME);

        assertParcelsCorrectly(ClassInfo.class, classInfo);
    }
}
