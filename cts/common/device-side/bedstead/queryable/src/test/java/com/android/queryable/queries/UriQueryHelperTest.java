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

import android.net.Uri;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.queryable.Queryable;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(BedsteadJUnit4.class)
public final class UriQueryHelperTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private final Queryable mQuery = null;
    private static final String URI_STRING_VALUE = "http://uri";
    private static final Uri URI_VALUE = Uri.parse(URI_STRING_VALUE);
    private static final String DIFFERENT_URI_STRING_VALUE = "http://uri2";
    private static final Uri DIFFERENT_URI_VALUE = Uri.parse(DIFFERENT_URI_STRING_VALUE);

    @Test
    public void matches_noRestrictions_returnsTrue() {
        UriQueryHelper<Queryable> uriQueryHelper = new UriQueryHelper<>(mQuery);

        assertThat(uriQueryHelper.matches(URI_VALUE)).isTrue();
    }

    @Test
    public void matches_isEqualTo_meetsRestriction_returnsTrue() {
        UriQueryHelper<Queryable> uriQueryHelper = new UriQueryHelper<>(mQuery);

        uriQueryHelper.isEqualTo(URI_VALUE);

        assertThat(uriQueryHelper.matches(URI_VALUE)).isTrue();
    }

    @Test
    public void matches_isEqualTo_doesNotMeetRestriction_returnsFalse() {
        UriQueryHelper<Queryable> uriQueryHelper = new UriQueryHelper<>(mQuery);

        uriQueryHelper.isEqualTo(DIFFERENT_URI_VALUE);

        assertThat(uriQueryHelper.matches(URI_VALUE)).isFalse();
    }

    @Test
    public void matches_stringValue_meetsRestriction_returnsTrue() {
        UriQueryHelper<Queryable> uriQueryHelper = new UriQueryHelper<>(mQuery);

        uriQueryHelper.stringValue().isEqualTo(URI_STRING_VALUE);

        assertThat(uriQueryHelper.matches(URI_VALUE)).isTrue();
    }

    @Test
    public void matches_stringValue_doesNotMeetRestriction_returnsFalse() {
        UriQueryHelper<Queryable> uriQueryHelper = new UriQueryHelper<>(mQuery);

        uriQueryHelper.stringValue().isEqualTo(DIFFERENT_URI_STRING_VALUE);

        assertThat(uriQueryHelper.matches(URI_VALUE)).isFalse();
    }

    @Test
    public void parcel_parcelsCorrectly() {
        UriQueryHelper<Queryable> uriQueryHelper = new UriQueryHelper<>(mQuery);

        uriQueryHelper.isEqualTo(null);
        uriQueryHelper.stringValue().isEqualTo(DIFFERENT_URI_STRING_VALUE);

        assertParcelsCorrectly(UriQueryHelper.class, uriQueryHelper);
    }

    @Test
    public void uriQueryHelper_queries() {
        assertThat(UriQuery.uri()
                .where().stringValue().isEqualTo(URI_STRING_VALUE)
                .matches(URI_VALUE)).isTrue();
    }

    @Test
    public void isEmptyQuery_isEmpty_returnsTrue() {
        UriQueryHelper<Queryable> uriQueryHelper =
                new UriQueryHelper<>(mQuery);

        assertThat(uriQueryHelper.isEmptyQuery()).isTrue();
    }

    @Test
    public void isEmptyQuery_hasEqualToQuery_returnsFalse() {
        UriQueryHelper<Queryable> uriQueryHelper =
                new UriQueryHelper<>(mQuery);

        uriQueryHelper.isEqualTo(URI_VALUE);

        assertThat(uriQueryHelper.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasStringValueQuery_returnsFalse() {
        UriQueryHelper<Queryable> uriQueryHelper =
                new UriQueryHelper<>(mQuery);

        uriQueryHelper.stringValue().isNull();

        assertThat(uriQueryHelper.isEmptyQuery()).isFalse();
    }
}
