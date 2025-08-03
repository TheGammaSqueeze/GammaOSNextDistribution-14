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
import static com.android.queryable.queries.BooleanQuery.Boolean;

import static com.google.common.truth.Truth.assertThat;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.queryable.Queryable;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(BedsteadJUnit4.class)
public final class BooleanQueryHelperTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private final Queryable mQuery = null;

    @Test
    public void matches_noRestrictions_returnsTrue() {
        BooleanQueryHelper<Queryable> booleanQueryHelper = new BooleanQueryHelper<>(mQuery);

        assertThat(booleanQueryHelper.matches(true)).isTrue();
    }

    @Test
    public void matches_isTrue_valueIsTrue_returnsTrue() {
        BooleanQueryHelper<Queryable> booleanQueryHelper = new BooleanQueryHelper<>(mQuery);
        booleanQueryHelper.isTrue();

        assertThat(booleanQueryHelper.matches(true)).isTrue();
    }

    @Test
    public void matches_isTrue_valueIsFalse_returnsFalse() {
        BooleanQueryHelper<Queryable> booleanQueryHelper = new BooleanQueryHelper<>(mQuery);
        booleanQueryHelper.isTrue();

        assertThat(booleanQueryHelper.matches(false)).isFalse();
    }

    @Test
    public void matches_isFalse_valueIsTrue_returnsFalse() {
        BooleanQueryHelper<Queryable> booleanQueryHelper = new BooleanQueryHelper<>(mQuery);
        booleanQueryHelper.isFalse();

        assertThat(booleanQueryHelper.matches(true)).isFalse();
    }

    @Test
    public void matches_isFalse_valueIsFalse_returnsTrue() {
        BooleanQueryHelper<Queryable> booleanQueryHelper = new BooleanQueryHelper<>(mQuery);
        booleanQueryHelper.isFalse();

        assertThat(booleanQueryHelper.matches(false)).isTrue();
    }

    @Test
    public void matches_equalsTrue_valueIsTrue_returnsTrue() {
        BooleanQueryHelper<Queryable> booleanQueryHelper = new BooleanQueryHelper<>(mQuery);
        booleanQueryHelper.isEqualTo(true);

        assertThat(booleanQueryHelper.matches(true)).isTrue();
    }

    @Test
    public void matches_equalsTrue_valueIsFalse_returnsFalse() {
        BooleanQueryHelper<Queryable> booleanQueryHelper = new BooleanQueryHelper<>(mQuery);
        booleanQueryHelper.isEqualTo(true);

        assertThat(booleanQueryHelper.matches(false)).isFalse();
    }

    @Test
    public void matches_equalsFalse_valueIsTrue_returnsFalse() {
        BooleanQueryHelper<Queryable> booleanQueryHelper = new BooleanQueryHelper<>(mQuery);
        booleanQueryHelper.isEqualTo(false);

        assertThat(booleanQueryHelper.matches(true)).isFalse();
    }

    @Test
    public void matches_equalsFalse_valueIsFalse_returnsTrue() {
        BooleanQueryHelper<Queryable> booleanQueryHelper = new BooleanQueryHelper<>(mQuery);
        booleanQueryHelper.isEqualTo(false);

        assertThat(booleanQueryHelper.matches(false)).isTrue();
    }

    @Test
    public void parcel_parcelsCorrectly() {
        BooleanQueryHelper<Queryable> booleanQueryHelper = new BooleanQueryHelper<>(mQuery);

        booleanQueryHelper.isTrue();

        assertParcelsCorrectly(BooleanQueryHelper.class, booleanQueryHelper);
    }

    @Test
    public void booleanQueryBase_queries() {
        assertThat(Boolean().where().isEqualTo(true).matches(true)).isTrue();
    }

    @Test
    public void isEmptyQuery_isEmpty_returnsTrue() {
        BooleanQueryHelper<Queryable> booleanQueryHelper = new BooleanQueryHelper<>(mQuery);

        assertThat(booleanQueryHelper.isEmptyQuery()).isTrue();
    }

    @Test
    public void isEmptyQuery_hasTarget_returnsFalse() {
        BooleanQueryHelper<Queryable> booleanQueryHelper = new BooleanQueryHelper<>(mQuery);

        booleanQueryHelper.isFalse();

        assertThat(booleanQueryHelper.isEmptyQuery()).isFalse();
    }
}
