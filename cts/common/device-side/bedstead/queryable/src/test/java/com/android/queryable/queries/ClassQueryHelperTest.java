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

package com.android.queryable.queries;

import static com.android.bedstead.nene.utils.ParcelTest.assertParcelsCorrectly;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.queryable.Queryable;
import com.android.queryable.info.ClassInfo;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(BedsteadJUnit4.class)
public final class ClassQueryHelperTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();
    private final Queryable mQuery = null;

    private static final Class<?> CLASS_1 = Activity.class;
    private static final ClassInfo CLASS_1_CLASS_INFO = new ClassInfo(CLASS_1);
    private static final String CLASS_1_CLASS_NAME = CLASS_1.getName();
    private static final String CLASS_1_SIMPLE_NAME = CLASS_1.getSimpleName();
    private static final ClassInfo CLASS_2_CLASS_INFO = new ClassInfo("differentClassName");

    @Test
    public void matches_noRestrictions_returnsTrue() {
        ClassQueryHelper<Queryable> classQueryHelper =
                new ClassQueryHelper<>(mQuery);

        assertThat(classQueryHelper.matches(CLASS_1_CLASS_INFO)).isTrue();
    }

    @Test
    public void matches_isSameClassAs_doesMatch_returnsTrue() {
        ClassQueryHelper<Queryable> classQueryHelper =
                new ClassQueryHelper<>(mQuery);

        classQueryHelper.isSameClassAs(CLASS_1);

        assertThat(classQueryHelper.matches(CLASS_1_CLASS_INFO)).isTrue();
    }

    @Test
    public void matches_isSameClassAs_doesNotMatch_returnsFalse() {
        ClassQueryHelper<Queryable> classQueryHelper =
                new ClassQueryHelper<>(mQuery);

        classQueryHelper.isSameClassAs(CLASS_1);

        assertThat(classQueryHelper.matches(CLASS_2_CLASS_INFO)).isFalse();
    }

    @Test
    public void matches_className_doesMatch_returnsTrue() {
        ClassQueryHelper<Queryable> classQueryHelper =
                new ClassQueryHelper<>(mQuery);

        classQueryHelper.className().isEqualTo(CLASS_1_CLASS_NAME);

        assertThat(classQueryHelper.matches(CLASS_1_CLASS_INFO)).isTrue();
    }

    @Test
    public void matches_className_doesNotMatch_returnsFalse() {
        ClassQueryHelper<Queryable> classQueryHelper =
                new ClassQueryHelper<>(mQuery);

        classQueryHelper.className().isEqualTo(CLASS_1_CLASS_NAME);

        assertThat(classQueryHelper.matches(CLASS_2_CLASS_INFO)).isFalse();
    }

    @Test
    public void matches_simpleName_doesMatch_returnsTrue() {
        ClassQueryHelper<Queryable> classQueryHelper =
                new ClassQueryHelper<>(mQuery);

        classQueryHelper.simpleName().isEqualTo(CLASS_1_SIMPLE_NAME);

        assertThat(classQueryHelper.matches(CLASS_1_CLASS_INFO)).isTrue();
    }

    @Test
    public void matches_simpleName_doesNotMatch_returnsFalse() {
        ClassQueryHelper<Queryable> classQueryHelper =
                new ClassQueryHelper<>(mQuery);

        classQueryHelper.simpleName().isEqualTo(CLASS_1_SIMPLE_NAME);

        assertThat(classQueryHelper.matches(CLASS_2_CLASS_INFO)).isFalse();
    }

    @Test
    public void parcel_parcelsCorrectly() {
        ClassQueryHelper<Queryable> classQueryHelper = new ClassQueryHelper<>(mQuery);

        classQueryHelper.className().isEqualTo(CLASS_1_CLASS_NAME);
        classQueryHelper.simpleName().isEqualTo(CLASS_1_SIMPLE_NAME);

        assertParcelsCorrectly(ClassQueryHelper.class, classQueryHelper);
    }

    @Test
    public void classQueryHelper_queries() {
        assertThat(
                ClassQuery.Class().where().simpleName().isEqualTo(CLASS_1_SIMPLE_NAME)
                        .matches(CLASS_1_CLASS_INFO)).isTrue();
    }

    @Test
    public void isEmptyQuery_isEmpty_returnsTrue() {
        ClassQueryHelper<Queryable> classQueryHelper =
                new ClassQueryHelper<>(mQuery);

        assertThat(classQueryHelper.isEmptyQuery()).isTrue();
    }

    @Test
    public void isEmptyQuery_hasClassNameQuery_returnsFalse() {
        ClassQueryHelper<Queryable> classQueryHelper =
                new ClassQueryHelper<>(mQuery);

        classQueryHelper.className().isNotNull();

        assertThat(classQueryHelper.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasSimpleNameQuery_returnsFalse() {
        ClassQueryHelper<Queryable> classQueryHelper =
                new ClassQueryHelper<>(mQuery);

        classQueryHelper.simpleName().isNotNull();

        assertThat(classQueryHelper.isEmptyQuery()).isFalse();
    }
}
