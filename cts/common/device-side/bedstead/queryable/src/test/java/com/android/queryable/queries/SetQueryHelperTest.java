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
import static com.android.queryable.queries.BundleQuery.bundle;
import static com.android.queryable.queries.IntegerQuery.integer;
import static com.android.queryable.queries.SetQuery.set;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.queryable.Queryable;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.Set;

@RunWith(BedsteadJUnit4.class)
public final class SetQueryHelperTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private final Queryable mQuery = null;
    private static final String BUNDLE_KEY = "key";
    private static final Bundle BUNDLE_CONTAINING_KEY = new Bundle();
    private static final Bundle BUNDLE_NOT_CONTAINING_KEY = new Bundle();
    private static final Integer INTEGER = 1;
    private static final Integer DIFFERENT_INTEGER = 2;
    private static final Integer ANOTHER_DIFFERENT_INTEGER = 3;

    static {
        BUNDLE_CONTAINING_KEY.putString(BUNDLE_KEY, "value");
    }

    @Test
    public void matches_size_matches_returnsTrue() {
        SetQueryHelper<Queryable, Bundle> setQueryHelper =
                new SetQueryHelper<>(mQuery);

        setQueryHelper.size().isEqualTo(1);

        assertThat(setQueryHelper.matches(Set.of(new Bundle()))).isTrue();
    }

    @Test
    public void matches_size_doesNotMatch_returnsFalse() {
        SetQueryHelper<Queryable, Bundle> setQueryHelper =
                new SetQueryHelper<>(mQuery);

        setQueryHelper.size().isEqualTo(1);

        assertThat(setQueryHelper.matches(Set.of())).isFalse();
    }

    @Test
    public void matches_contains_withQuery_doesContain_returnsTrue() {
        SetQueryHelper<Queryable, Bundle> setQueryHelper =
                new SetQueryHelper<>(mQuery);

        setQueryHelper.contains(
                bundle().where().key(BUNDLE_KEY).exists()
        );

        assertThat(setQueryHelper.matches(Set.of(BUNDLE_CONTAINING_KEY, BUNDLE_NOT_CONTAINING_KEY))).isTrue();
    }

    @Test
    public void matches_contains_withQuery_doesNotContain_returnsFalse() {
        SetQueryHelper<Queryable, Bundle> setQueryHelper =
                new SetQueryHelper<>(mQuery);

        setQueryHelper.contains(
                bundle().where().key(BUNDLE_KEY).exists()
        );

        assertThat(setQueryHelper.matches(Set.of(BUNDLE_NOT_CONTAINING_KEY))).isFalse();
    }

    @Test
    public void matches_doesNotContain_withQuery_doesContain_returnsFalse() {
        SetQueryHelper<Queryable, Bundle> setQueryHelper =
                new SetQueryHelper<>(mQuery);

        setQueryHelper.doesNotContain(
                bundle().where().key(BUNDLE_KEY).exists()
        );

        assertThat(setQueryHelper.matches(Set.of(BUNDLE_CONTAINING_KEY, BUNDLE_NOT_CONTAINING_KEY))).isFalse();
    }

    @Test
    public void matches_doesNotContain_withQuery_doesNotContain_returnsTrue() {
        SetQueryHelper<Queryable, Bundle> setQueryHelper =
                new SetQueryHelper<>(mQuery);

        setQueryHelper.doesNotContain(
                bundle().where().key(BUNDLE_KEY).exists()
        );

        assertThat(setQueryHelper.matches(Set.of(BUNDLE_NOT_CONTAINING_KEY))).isTrue();
    }

    @Test
    public void matches_contains_withNonQuery_doesContain_returnsTrue() {
        SetQueryHelper<Queryable, Integer> setQueryHelper =
                new SetQueryHelper<>(mQuery);

        setQueryHelper.contains(INTEGER);

        assertThat(setQueryHelper.matches(
                Set.of(INTEGER, DIFFERENT_INTEGER))).isTrue();
    }

    @Test
    public void matches_contains_withNonQuery_doesNotContain_returnsFalse() {
        SetQueryHelper<Queryable, Integer> setQueryHelper =
                new SetQueryHelper<>(mQuery);

        setQueryHelper.contains(INTEGER);

        assertThat(setQueryHelper.matches(Set.of(DIFFERENT_INTEGER))).isFalse();
    }

    @Test
    public void matches_doesNotContain_withNonQuery_doesContain_returnsFalse() {
        SetQueryHelper<Queryable, Integer> setQueryHelper =
                new SetQueryHelper<>(mQuery);

        setQueryHelper.doesNotContain(INTEGER);

        assertThat(setQueryHelper.matches(
                Set.of(INTEGER, DIFFERENT_INTEGER))).isFalse();
    }

    @Test
    public void matches_doesNotContain_withNonQuery_doesNotContain_returnsTrue() {
        SetQueryHelper<Queryable, Integer> setQueryHelper =
                new SetQueryHelper<>(mQuery);

        setQueryHelper.doesNotContain(INTEGER);

        assertThat(setQueryHelper.matches(Set.of(DIFFERENT_INTEGER))).isTrue();
    }

    @Test
    public void matches_containsAll_withNonQueries_doesContain_returnsTrue() {
        SetQueryHelper<Queryable, Integer> setQueryHelper =
                new SetQueryHelper<>(mQuery);

        setQueryHelper.containsAll(Set.of(INTEGER, DIFFERENT_INTEGER));

        assertThat(setQueryHelper.matches(
                Set.of(INTEGER, DIFFERENT_INTEGER, ANOTHER_DIFFERENT_INTEGER))).isTrue();
    }

    @Test
    public void matches_containsAll_withNonQueries_doesNotContain_returnsFalse() {
        SetQueryHelper<Queryable, Integer> setQueryHelper =
                new SetQueryHelper<>(mQuery);

        setQueryHelper.containsAll(Set.of(INTEGER, DIFFERENT_INTEGER));

        assertThat(setQueryHelper.matches(
                Set.of(DIFFERENT_INTEGER, ANOTHER_DIFFERENT_INTEGER))).isFalse();
    }

    @Test
    public void matches_doesNotContainAny_withNonQueries_doesContain_returnsFalse() {
        SetQueryHelper<Queryable, Integer> setQueryHelper =
                new SetQueryHelper<>(mQuery);

        setQueryHelper.doesNotContainAny(Set.of(INTEGER, DIFFERENT_INTEGER));

        assertThat(setQueryHelper.matches(
                Set.of(INTEGER, ANOTHER_DIFFERENT_INTEGER))).isFalse();
    }

    @Test
    public void matches_doesNotContainAny_withNonQueries_doesNotContain_returnsTrue() {
        SetQueryHelper<Queryable, Integer> setQueryHelper =
                new SetQueryHelper<>(mQuery);

        setQueryHelper.doesNotContainAny(Set.of(INTEGER, DIFFERENT_INTEGER));

        assertThat(setQueryHelper.matches(
                Set.of(ANOTHER_DIFFERENT_INTEGER))).isTrue();
    }

    @Test
    public void parcel_parcelsCorrectly() {
        SetQueryHelper<Queryable, Integer> setQueryHelper =
                new SetQueryHelper<>(mQuery);

        setQueryHelper.contains(1);
        setQueryHelper.contains(IntegerQuery.integer().where().isEqualTo(1));
        setQueryHelper.doesNotContain(1);
        setQueryHelper.doesNotContain(IntegerQuery.integer().where().isEqualTo(1));
        setQueryHelper.containsAll(Arrays.asList(1, 2));
        setQueryHelper.doesNotContainAny(Arrays.asList(1, 2));

        assertParcelsCorrectly(SetQueryHelper.class, setQueryHelper);
    }

    @Test
    public void setQueryHelper_queries() {
        assertThat(set(Integer.class)
                .where().contains(1)
                .matches(Set.of(1))).isTrue();
    }

    @Test
    public void isEmptyQuery_isEmpty_returnsTrue() {
        SetQueryHelper<Queryable, Integer> setQueryHelper =
                new SetQueryHelper<>(mQuery);

        assertThat(setQueryHelper.isEmptyQuery()).isTrue();
    }

    @Test
    public void isEmptyQuery_hasContainsByQueryQuery_returnsFalse() {
        SetQueryHelper<Queryable, Integer> setQueryHelper =
                new SetQueryHelper<>(mQuery);

        setQueryHelper.contains(integer().where().isEqualTo(1));

        assertThat(setQueryHelper.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasDoesNotContainByQueryQuery_returnsFalse() {
        SetQueryHelper<Queryable, Integer> setQueryHelper =
                new SetQueryHelper<>(mQuery);

        setQueryHelper.doesNotContain(integer().where().isEqualTo(1));

        assertThat(setQueryHelper.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasContainsByTypeQuery_returnsFalse() {
        SetQueryHelper<Queryable, Integer> setQueryHelper =
                new SetQueryHelper<>(mQuery);

        setQueryHelper.contains(1);

        assertThat(setQueryHelper.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasDoesNotContainByType_returnsFalse() {
        SetQueryHelper<Queryable, Integer> setQueryHelper =
                new SetQueryHelper<>(mQuery);

        setQueryHelper.doesNotContain(1);

        assertThat(setQueryHelper.isEmptyQuery()).isFalse();
    }
}
