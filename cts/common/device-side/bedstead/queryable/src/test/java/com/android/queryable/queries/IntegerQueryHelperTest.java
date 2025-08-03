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
import static com.android.queryable.queries.IntegerQuery.integer;

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
public final class IntegerQueryHelperTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private final Queryable mQuery = null;
    private static final int INTEGER_VALUE = 100;
    private static final int GREATER_VALUE = 200;
    private static final int LESS_VALUE = 50;

    @Test
    public void matches_noRestrictions_returnsTrue() {
        IntegerQueryHelper<Queryable> integerQueryHelper =
                new IntegerQueryHelper<>(mQuery);

        assertThat(integerQueryHelper.matches(INTEGER_VALUE)).isTrue();
    }

    @Test
    public void matches_isEqualTo_meetsRestriction_returnsTrue() {
        IntegerQueryHelper<Queryable> integerQueryHelper =
                new IntegerQueryHelper<>(mQuery);

        integerQueryHelper.isEqualTo(INTEGER_VALUE);

        assertThat(integerQueryHelper.matches(INTEGER_VALUE)).isTrue();
    }

    @Test
    public void matches_isEqualTo_doesNotMeetRestriction_returnsFalse() {
        IntegerQueryHelper<Queryable> integerQueryHelper =
                new IntegerQueryHelper<>(mQuery);

        integerQueryHelper.isEqualTo(INTEGER_VALUE);

        assertThat(integerQueryHelper.matches(GREATER_VALUE)).isFalse();
    }

    @Test
    public void matches_isGreaterThan_meetsRestriction_returnsTrue() {
        IntegerQueryHelper<Queryable> integerQueryHelper =
                new IntegerQueryHelper<>(mQuery);

        integerQueryHelper.isGreaterThan(INTEGER_VALUE);

        assertThat(integerQueryHelper.matches(GREATER_VALUE)).isTrue();
    }

    @Test
    public void matches_isGreaterThan_doesNotMeetRestriction_returnsFalse() {
        IntegerQueryHelper<Queryable> integerQueryHelper =
                new IntegerQueryHelper<>(mQuery);

        integerQueryHelper.isGreaterThan(INTEGER_VALUE);

        assertThat(integerQueryHelper.matches(INTEGER_VALUE)).isFalse();
    }

    @Test
    public void matches_isGreaterThanOrEqualTo_greaterThan_returnsTrue() {
        IntegerQueryHelper<Queryable> integerQueryHelper =
                new IntegerQueryHelper<>(mQuery);

        integerQueryHelper.isGreaterThanOrEqualTo(INTEGER_VALUE);

        assertThat(integerQueryHelper.matches(GREATER_VALUE)).isTrue();
    }

    @Test
    public void matches_isGreaterThanOrEqualTo_equalTo_returnsTrue() {
        IntegerQueryHelper<Queryable> integerQueryHelper =
                new IntegerQueryHelper<>(mQuery);

        integerQueryHelper.isGreaterThanOrEqualTo(INTEGER_VALUE);

        assertThat(integerQueryHelper.matches(INTEGER_VALUE)).isTrue();
    }

    @Test
    public void matches_isGreaterThanOrEqualTo_doesNotMeetRestriction_returnsFalse() {
        IntegerQueryHelper<Queryable> integerQueryHelper =
                new IntegerQueryHelper<>(mQuery);

        integerQueryHelper.isGreaterThanOrEqualTo(INTEGER_VALUE);

        assertThat(integerQueryHelper.matches(LESS_VALUE)).isFalse();
    }

    @Test
    public void matches_isLessThan_meetsRestriction_returnsTrue() {
        IntegerQueryHelper<Queryable> integerQueryHelper =
                new IntegerQueryHelper<>(mQuery);

        integerQueryHelper.isLessThan(INTEGER_VALUE);

        assertThat(integerQueryHelper.matches(LESS_VALUE)).isTrue();
    }

    @Test
    public void matches_isLessThan_doesNotMeetRestriction_returnsFalse() {
        IntegerQueryHelper<Queryable> integerQueryHelper =
                new IntegerQueryHelper<>(mQuery);

        integerQueryHelper.isLessThan(INTEGER_VALUE);

        assertThat(integerQueryHelper.matches(INTEGER_VALUE)).isFalse();
    }

    @Test
    public void matches_isLessThanOrEqualTo_lessThan_returnsTrue() {
        IntegerQueryHelper<Queryable> integerQueryHelper =
                new IntegerQueryHelper<>(mQuery);

        integerQueryHelper.isLessThanOrEqualTo(INTEGER_VALUE);

        assertThat(integerQueryHelper.matches(LESS_VALUE)).isTrue();
    }

    @Test
    public void matches_isLessThanOrEqualTo_equalTo_returnsTrue() {
        IntegerQueryHelper<Queryable> integerQueryHelper =
                new IntegerQueryHelper<>(mQuery);

        integerQueryHelper.isLessThanOrEqualTo(INTEGER_VALUE);

        assertThat(integerQueryHelper.matches(INTEGER_VALUE)).isTrue();
    }

    @Test
    public void matches_isLessThanOrEqualTo_doesNotMeetRestriction_returnsFalse() {
        IntegerQueryHelper<Queryable> integerQueryHelper =
                new IntegerQueryHelper<>(mQuery);

        integerQueryHelper.isLessThanOrEqualTo(INTEGER_VALUE);

        assertThat(integerQueryHelper.matches(GREATER_VALUE)).isFalse();
    }

    @Test
    public void parcel_parcelsCorrectly() {
        IntegerQueryHelper<Queryable> integerQueryHelper =
                new IntegerQueryHelper<>(mQuery);

        integerQueryHelper.isEqualTo(1);
        integerQueryHelper.isGreaterThan(1);
        integerQueryHelper.isGreaterThanOrEqualTo(1);
        integerQueryHelper.isLessThan(1);
        integerQueryHelper.isLessThanOrEqualTo(1);

        assertParcelsCorrectly(IntegerQueryHelper.class, integerQueryHelper);
    }

    @Test
    public void integerQueryHelper_queries() {
        assertThat(
                integer()
                        .where().isEqualTo(1)
                        .matches(1)).isTrue();
    }

    @Test
    public void isEmptyQuery_isEmpty_returnsTrue() {
        IntegerQueryHelper<Queryable> integerQueryHelper =
                new IntegerQueryHelper<>(mQuery);

        assertThat(integerQueryHelper.isEmptyQuery()).isTrue();
    }

    @Test
    public void isEmptyQuery_hasIsEqualToQuery_returnsFalse() {
        IntegerQueryHelper<Queryable> integerQueryHelper =
                new IntegerQueryHelper<>(mQuery);

        integerQueryHelper.isEqualTo(0);

        assertThat(integerQueryHelper.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasIsGreaterThanQuery_returnsFalse() {
        IntegerQueryHelper<Queryable> integerQueryHelper =
                new IntegerQueryHelper<>(mQuery);

        integerQueryHelper.isGreaterThan(0);

        assertThat(integerQueryHelper.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasIsGreaterThanOrEqualToQuery_returnsFalse() {
        IntegerQueryHelper<Queryable> integerQueryHelper =
                new IntegerQueryHelper<>(mQuery);

        integerQueryHelper.isGreaterThanOrEqualTo(0);

        assertThat(integerQueryHelper.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasIsLessThanQuery_returnsFalse() {
        IntegerQueryHelper<Queryable> integerQueryHelper =
                new IntegerQueryHelper<>(mQuery);

        integerQueryHelper.isLessThan(0);

        assertThat(integerQueryHelper.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasIsLessThanOrEqualToQuery_returnsFalse() {
        IntegerQueryHelper<Queryable> integerQueryHelper =
                new IntegerQueryHelper<>(mQuery);

        integerQueryHelper.isLessThanOrEqualTo(0);

        assertThat(integerQueryHelper.isEmptyQuery()).isFalse();
    }
}
