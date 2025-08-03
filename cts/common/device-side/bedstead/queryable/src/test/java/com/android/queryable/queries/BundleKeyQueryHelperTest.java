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

import android.os.Bundle;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.queryable.Queryable;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

@RunWith(BedsteadJUnit4.class)
public final class BundleKeyQueryHelperTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String KEY = "Key";
    private static final String KEY2 = "Key2";
    private static final String STRING_VALUE = "String";
    private static final String DIFFERENT_STRING_VALUE = "String2";
    private static final int INTEGER_VALUE = 1;
    private static final int DIFFERENT_INTEGER_VALUE = 2;
    private static final long LONG_VALUE = 1;
    private static final int DIFFERENT_LONG_VALUE = 2;
    private static final ArrayList<String> STRING_LIST_VALUE = new ArrayList<String>(
            Arrays.asList(".", "..", "..."));
    private static final ArrayList<String> DIFFERENT_STRING_LIST_VALUE = new ArrayList<String>(
            Arrays.asList("|||", "||", "|"));
    private static final ArrayList<Integer> INTEGER_LIST_VALUE = new ArrayList<Integer>(
            Arrays.asList(1, 2, 3));
    private static final ArrayList<Integer> DIFFERENT_INTEGER_LIST_VALUE = new ArrayList<Integer>(
            Arrays.asList(4, 5, 6));

    private final Queryable mQuery = null;
    private final Bundle mBundle = new Bundle();
    private final Bundle mBundle2 = new Bundle();
    private final Serializable mSerializable = "SerializableString";
    private final Serializable mDifferentSerializable = "SerializableString2";

    @Test
    public void matches_noRestrictions_returnsTrue() {
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        assertThat(bundleKeyQueryHelper.matches(mBundle, KEY)).isTrue();
    }

    @Test
    public void matches_stringValueRestriction_meetsRestriction_returnsTrue() {
        mBundle.putString(KEY, STRING_VALUE);
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.stringValue().isEqualTo(STRING_VALUE);

        assertThat(bundleKeyQueryHelper.matches(mBundle, KEY)).isTrue();
    }

    @Test
    public void matches_stringValueRestriction_doesNotMeetRestriction_returnsFalse() {
        mBundle.putString(KEY, STRING_VALUE);
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.stringValue().isEqualTo(DIFFERENT_STRING_VALUE);

        assertThat(bundleKeyQueryHelper.matches(mBundle, KEY)).isFalse();
    }

    @Test
    public void matches_bundleValueRestriction_meetsRestriction_returnsTrue() {
        mBundle.putBundle(KEY, mBundle2);
        mBundle2.putString(KEY2, STRING_VALUE);
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.bundleValue().key(KEY2).exists();

        assertThat(bundleKeyQueryHelper.matches(mBundle, KEY)).isTrue();
    }

    @Test
    public void matches_bundleValueRestriction_doesNotMeetRestriction_returnsFalse() {
        mBundle.putBundle(KEY, mBundle2);
        mBundle2.remove(KEY2);
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.bundleValue().key(KEY2).exists();

        assertThat(bundleKeyQueryHelper.matches(mBundle, KEY)).isFalse();
    }

    @Test
    public void matches_serializableValueRestriction_meetsRestriction_returnsTrue() {
        mBundle.putSerializable(KEY, mSerializable);
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.serializableValue().isEqualTo(mSerializable);

        assertThat(bundleKeyQueryHelper.matches(mBundle, KEY)).isTrue();
    }

    @Test
    public void matches_serializableValueRestriction_doesNotMeetRestriction_returnsFalse() {
        mBundle.putSerializable(KEY, mSerializable);
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.serializableValue().isEqualTo(mDifferentSerializable);

        assertThat(bundleKeyQueryHelper.matches(mBundle, KEY)).isFalse();
    }

    @Test
    public void matches_existsRestriction_meetsRestriction_returnsTrue() {
        mBundle.putString(KEY, STRING_VALUE);
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.exists();

        assertThat(bundleKeyQueryHelper.matches(mBundle, KEY)).isTrue();
    }

    @Test
    public void matches_existsRestriction_doesNotMeetRestriction_returnsFalse() {
        mBundle.remove(KEY);
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.exists();

        assertThat(bundleKeyQueryHelper.matches(mBundle, KEY)).isFalse();
    }

    @Test
    public void matches_doesNotExistRestriction_meetsRestriction_returnsTrue() {
        mBundle.remove(KEY);
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.doesNotExist();

        assertThat(bundleKeyQueryHelper.matches(mBundle, KEY)).isTrue();
    }

    @Test
    public void matches_doesNotExistRestriction_doesNotMeetRestriction_returnsFalse() {
        mBundle.putString(KEY, STRING_VALUE);
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.doesNotExist();

        assertThat(bundleKeyQueryHelper.matches(mBundle, KEY)).isFalse();
    }

    @Test
    public void matches_integerValueRestriction_meetsRestriction_returnsTrue() {
        mBundle.putInt(KEY, INTEGER_VALUE);
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.integerValue().isEqualTo(INTEGER_VALUE);

        assertThat(bundleKeyQueryHelper.matches(mBundle, KEY)).isTrue();
    }

    @Test
    public void matches_integerValueRestriction_doesNotMeetRestriction_returnsFalse() {
        mBundle.putInt(KEY, INTEGER_VALUE);
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.integerValue().isEqualTo(DIFFERENT_INTEGER_VALUE);

        assertThat(bundleKeyQueryHelper.matches(mBundle, KEY)).isFalse();
    }

    @Test
    public void matches_LongValueRestriction_meetsRestriction_returnsTrue() {
        mBundle.putLong(KEY, LONG_VALUE);
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.longValue().isEqualTo(LONG_VALUE);

        assertThat(bundleKeyQueryHelper.matches(mBundle, KEY)).isTrue();
    }

    @Test
    public void matches_longValueRestriction_doesNotMeetRestriction_returnsFalse() {
        mBundle.putLong(KEY, LONG_VALUE);
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.longValue().isEqualTo(DIFFERENT_LONG_VALUE);

        assertThat(bundleKeyQueryHelper.matches(mBundle, KEY)).isFalse();
    }

    @Test
    public void matches_booleanValueRestriction_meetsRestriction_returnsTrue() {
        mBundle.putBoolean(KEY, true);
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.booleanValue().isTrue();

        assertThat(bundleKeyQueryHelper.matches(mBundle, KEY)).isTrue();
    }

    @Test
    public void matches_booleanValueRestriction_doesNotMeetRestriction_returnsFalse() {
        mBundle.putBoolean(KEY, true);
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.booleanValue().isFalse();

        assertThat(bundleKeyQueryHelper.matches(mBundle, KEY)).isFalse();
    }

    @Test
    public void matches_stringListValueRestriction_meetsRestriction_returnsTrue() {
        mBundle.putStringArrayList(KEY, STRING_LIST_VALUE);
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.stringListValue().containsAll(STRING_LIST_VALUE);

        assertThat(bundleKeyQueryHelper.matches(mBundle, KEY)).isTrue();
    }

    @Test
    public void matches_stringListValueRestriction_doesNotMeetRestriction_returnsFalse() {
        mBundle.putStringArrayList(KEY, STRING_LIST_VALUE);
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.stringListValue().containsAll(DIFFERENT_STRING_LIST_VALUE);

        assertThat(bundleKeyQueryHelper.matches(mBundle, KEY)).isFalse();
    }

    @Test
    public void matches_integerListValueRestriction_meetsRestriction_returnsTrue() {
        mBundle.putIntegerArrayList(KEY, INTEGER_LIST_VALUE);
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.integerListValue().containsAll(INTEGER_LIST_VALUE);

        assertThat(bundleKeyQueryHelper.matches(mBundle, KEY)).isTrue();
    }

    @Test
    public void matches_integerListValueRestriction_doesNotMeetRestriction_returnsFalse() {
        mBundle.putIntegerArrayList(KEY, INTEGER_LIST_VALUE);
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.integerListValue().containsAll(DIFFERENT_INTEGER_LIST_VALUE);

        assertThat(bundleKeyQueryHelper.matches(mBundle, KEY)).isFalse();
    }

    @Test
    public void parcel_parcelsCorrectly() {
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.exists();
        bundleKeyQueryHelper.stringValue().isEqualTo("");

        assertParcelsCorrectly(BundleKeyQueryHelper.class, bundleKeyQueryHelper);
    }

    @Test
    public void isEmptyQuery_isEmpty_returnsTrue() {
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        assertThat(bundleKeyQueryHelper.isEmptyQuery()).isTrue();
    }

    @Test
    public void isEmptyQuery_hasStringQuery_returnsFalse() {
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.stringValue().isNotNull();

        assertThat(bundleKeyQueryHelper.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasSerializableQuery_returnsFalse() {
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.serializableValue().isEqualTo(new Serializable() {});

        assertThat(bundleKeyQueryHelper.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasBundleQuery_returnsFalse() {
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.bundleValue().key("").stringValue().isNotNull();

        assertThat(bundleKeyQueryHelper.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasIntegerQuery_returnsFalse() {
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.integerValue().isEqualTo(0);

        assertThat(bundleKeyQueryHelper.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasLongQuery_returnsFalse() {
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.longValue().isEqualTo(0);

        assertThat(bundleKeyQueryHelper.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasBooleanQuery_returnsFalse() {
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.booleanValue().isTrue();

        assertThat(bundleKeyQueryHelper.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasStringListQuery_returnsFalse() {
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.stringListValue().size().isEqualTo(0);

        assertThat(bundleKeyQueryHelper.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasIntegerListQuery_returnsFalse() {
        BundleKeyQueryHelper<Queryable> bundleKeyQueryHelper =
                new BundleKeyQueryHelper<>(mQuery);

        bundleKeyQueryHelper.integerListValue().size().isEqualTo(0);

        assertThat(bundleKeyQueryHelper.isEmptyQuery()).isFalse();
    }
}
