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
import static com.android.queryable.queries.ListQuery.list;

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

import java.util.List;

@RunWith(BedsteadJUnit4.class)
public final class ListQueryHelperTest {

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
        ListQueryHelper<Queryable, Bundle> listQueryHelper =
                new ListQueryHelper<>(mQuery);

        listQueryHelper.size().isEqualTo(1);

        assertThat(listQueryHelper.matches(List.of(new Bundle()))).isTrue();
    }

    @Test
    public void matches_size_doesNotMatch_returnsFalse() {
        ListQueryHelper<Queryable, Bundle> listQueryHelper =
                new ListQueryHelper<>(mQuery);

        listQueryHelper.size().isEqualTo(1);

        assertThat(listQueryHelper.matches(List.of())).isFalse();
    }

    @Test
    public void matches_contains_withQuery_doesContain_returnsTrue() {
        ListQueryHelper<Queryable, Bundle> listQueryHelper =
                new ListQueryHelper<>(mQuery);

        listQueryHelper.contains(
                bundle().where().key(BUNDLE_KEY).exists()
        );

        assertThat(listQueryHelper.matches(
                List.of(BUNDLE_CONTAINING_KEY, BUNDLE_NOT_CONTAINING_KEY))).isTrue();
    }

    @Test
    public void matches_contains_withQuery_doesNotContain_returnsFalse() {
        ListQueryHelper<Queryable, Bundle> listQueryHelper =
                new ListQueryHelper<>(mQuery);

        listQueryHelper.contains(
                bundle().where().key(BUNDLE_KEY).exists()
        );

        assertThat(listQueryHelper.matches(List.of(BUNDLE_NOT_CONTAINING_KEY))).isFalse();
    }

    @Test
    public void matches_doesNotContain_withQuery_doesContain_returnsFalse() {
        ListQueryHelper<Queryable, Bundle> listQueryHelper =
                new ListQueryHelper<>(mQuery);

        listQueryHelper.doesNotContain(
                bundle().where().key(BUNDLE_KEY).exists()
        );

        assertThat(listQueryHelper.matches(
                List.of(BUNDLE_CONTAINING_KEY, BUNDLE_NOT_CONTAINING_KEY))).isFalse();
    }

    @Test
    public void matches_doesNotContain_withQuery_doesNotContain_returnsTrue() {
        ListQueryHelper<Queryable, Bundle> listQueryHelper =
                new ListQueryHelper<>(mQuery);

        listQueryHelper.doesNotContain(
                bundle().where().key(BUNDLE_KEY).exists()
        );

        assertThat(listQueryHelper.matches(List.of(BUNDLE_NOT_CONTAINING_KEY))).isTrue();
    }

    @Test
    public void matches_contains_withNonQuery_doesContain_returnsTrue() {
        ListQueryHelper<Queryable, Integer> listQueryHelper =
                new ListQueryHelper<>(mQuery);

        listQueryHelper.contains(INTEGER);

        assertThat(listQueryHelper.matches(
                List.of(INTEGER, DIFFERENT_INTEGER))).isTrue();
    }

    @Test
    public void matches_contains_withNonQuery_doesNotContain_returnsFalse() {
        ListQueryHelper<Queryable, Integer> listQueryHelper =
                new ListQueryHelper<>(mQuery);

        listQueryHelper.contains(INTEGER);

        assertThat(listQueryHelper.matches(List.of(DIFFERENT_INTEGER))).isFalse();
    }

    @Test
    public void matches_doesNotContain_withNonQuery_doesContain_returnsFalse() {
        ListQueryHelper<Queryable, Integer> listQueryHelper =
                new ListQueryHelper<>(mQuery);

        listQueryHelper.doesNotContain(INTEGER);

        assertThat(listQueryHelper.matches(
                List.of(INTEGER, DIFFERENT_INTEGER))).isFalse();
    }

    @Test
    public void matches_doesNotContain_withNonQuery_doesNotContain_returnsTrue() {
        ListQueryHelper<Queryable, Integer> listQueryHelper =
                new ListQueryHelper<>(mQuery);

        listQueryHelper.doesNotContain(INTEGER);

        assertThat(listQueryHelper.matches(List.of(DIFFERENT_INTEGER))).isTrue();
    }

    @Test
    public void matches_containsAll_withNonQueries_doesContain_returnsTrue() {
        ListQueryHelper<Queryable, Integer> listQueryHelper =
                new ListQueryHelper<>(mQuery);

        listQueryHelper.containsAll(List.of(INTEGER, DIFFERENT_INTEGER));

        assertThat(listQueryHelper.matches(
                List.of(INTEGER, DIFFERENT_INTEGER, ANOTHER_DIFFERENT_INTEGER))).isTrue();
    }

    @Test
    public void matches_containsAll_withNonQueries_doesNotContain_returnsFalse() {
        ListQueryHelper<Queryable, Integer> listQueryHelper =
                new ListQueryHelper<>(mQuery);

        listQueryHelper.containsAll(List.of(INTEGER, DIFFERENT_INTEGER));

        assertThat(listQueryHelper.matches(
                List.of(DIFFERENT_INTEGER, ANOTHER_DIFFERENT_INTEGER))).isFalse();
    }

    @Test
    public void matches_doesNotContainAny_withNonQueries_doesContain_returnsFalse() {
        ListQueryHelper<Queryable, Integer> listQueryHelper =
                new ListQueryHelper<>(mQuery);

        listQueryHelper.doesNotContainAny(List.of(INTEGER, DIFFERENT_INTEGER));

        assertThat(listQueryHelper.matches(
                List.of(INTEGER, ANOTHER_DIFFERENT_INTEGER))).isFalse();
    }

    @Test
    public void matches_doesNotContainAny_withNonQueries_doesNotContain_returnsTrue() {
        ListQueryHelper<Queryable, Integer> listQueryHelper =
                new ListQueryHelper<>(mQuery);

        listQueryHelper.doesNotContainAny(List.of(INTEGER, DIFFERENT_INTEGER));

        assertThat(listQueryHelper.matches(
                List.of(ANOTHER_DIFFERENT_INTEGER))).isTrue();
    }

    @Test
    public void parcel_parcelsCorrectly() {
        ListQueryHelper<Queryable, Integer> listQueryHelper =
                new ListQueryHelper<>(mQuery);

        listQueryHelper.size().isEqualTo(1);
        listQueryHelper.contains(1);
        listQueryHelper.doesNotContain(1);
        listQueryHelper.contains(integer().where().isEqualTo(1));
        listQueryHelper.doesNotContain(integer().where().isEqualTo(1));

        assertParcelsCorrectly(ListQueryHelper.class, listQueryHelper);
    }

    @Test
    public void listQueryHelper_queries() {
        assertThat(list(Integer.class)
                .where().contains(1)
                .matches(List.of(1))).isTrue();
    }

    @Test
    public void isEmptyQuery_isEmpty_returnsTrue() {
        ListQueryHelper<Queryable, Integer> listQueryHelper =
                new ListQueryHelper<>(mQuery);

        assertThat(listQueryHelper.isEmptyQuery()).isTrue();
    }

    @Test
    public void isEmptyQuery_hasContainsByQueryQuery_returnsFalse() {
        ListQueryHelper<Queryable, Integer> listQueryHelper =
                new ListQueryHelper<>(mQuery);

        listQueryHelper.contains(integer().where().isEqualTo(1));

        assertThat(listQueryHelper.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasDoesNotContainByQueryQuery_returnsFalse() {
        ListQueryHelper<Queryable, Integer> listQueryHelper =
                new ListQueryHelper<>(mQuery);

        listQueryHelper.doesNotContain(integer().where().isEqualTo(1));

        assertThat(listQueryHelper.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasContainsByTypeQuery_returnsFalse() {
        ListQueryHelper<Queryable, Integer> listQueryHelper =
                new ListQueryHelper<>(mQuery);

        listQueryHelper.contains(1);

        assertThat(listQueryHelper.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasDoesNotContainByType_returnsFalse() {
        ListQueryHelper<Queryable, Integer> listQueryHelper =
                new ListQueryHelper<>(mQuery);

        listQueryHelper.doesNotContain(1);

        assertThat(listQueryHelper.isEmptyQuery()).isFalse();
    }
}
