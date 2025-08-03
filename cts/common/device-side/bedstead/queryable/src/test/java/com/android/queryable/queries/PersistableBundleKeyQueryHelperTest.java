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

import android.os.PersistableBundle;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.queryable.Queryable;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(BedsteadJUnit4.class)
public final class PersistableBundleKeyQueryHelperTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String KEY = "Key";
    private static final String KEY2 = "Key2";
    private static final String STRING_VALUE = "String";
    private static final String DIFFERENT_STRING_VALUE = "String2";

    private final Queryable mQuery = null;
    private final PersistableBundle mPersistableBundle = new PersistableBundle();
    private final PersistableBundle mPersistableBundle2 = new PersistableBundle();

    @Test
    public void matches_noRestrictions_returnsTrue() {
        PersistableBundleKeyQueryHelper<Queryable>
                persistableBundleKeyQueryHelper = new PersistableBundleKeyQueryHelper<>(mQuery);

        assertThat(persistableBundleKeyQueryHelper.matches(mPersistableBundle, KEY)).isTrue();
    }

    @Test
    public void matches_stringValueRestriction_meetsRestriction_returnsTrue() {
        mPersistableBundle.putString(KEY, STRING_VALUE);
        PersistableBundleKeyQueryHelper<Queryable>
                persistableBundleKeyQueryHelper = new PersistableBundleKeyQueryHelper<>(mQuery);

        persistableBundleKeyQueryHelper.stringValue().isEqualTo(STRING_VALUE);

        assertThat(persistableBundleKeyQueryHelper.matches(mPersistableBundle, KEY)).isTrue();
    }

    @Test
    public void matches_stringValueRestriction_doesNotMeetRestriction_returnsFalse() {
        mPersistableBundle.putString(KEY, STRING_VALUE);
        PersistableBundleKeyQueryHelper<Queryable>
                persistableBundleKeyQueryHelper = new PersistableBundleKeyQueryHelper<>(mQuery);

        persistableBundleKeyQueryHelper.stringValue().isEqualTo(DIFFERENT_STRING_VALUE);

        assertThat(persistableBundleKeyQueryHelper.matches(mPersistableBundle, KEY)).isFalse();
    }

    @Test
    public void matches_persistableBundleValueRestriction_meetsRestriction_returnsTrue() {
        mPersistableBundle.putPersistableBundle(KEY, mPersistableBundle2);
        mPersistableBundle2.putString(KEY2, STRING_VALUE);
        PersistableBundleKeyQueryHelper<Queryable>
                persistableBundleKeyQueryHelper = new PersistableBundleKeyQueryHelper<>(mQuery);

        persistableBundleKeyQueryHelper.persistableBundleValue().key(KEY2).exists();

        assertThat(persistableBundleKeyQueryHelper.matches(mPersistableBundle, KEY)).isTrue();
    }

    @Test
    public void matches_persistableBundleValueRestriction_doesNotMeetRestriction_returnsFalse() {
        mPersistableBundle.putPersistableBundle(KEY, mPersistableBundle2);
        mPersistableBundle2.remove(KEY2);
        PersistableBundleKeyQueryHelper<Queryable>
                persistableBundleKeyQueryHelper = new PersistableBundleKeyQueryHelper<>(mQuery);

        persistableBundleKeyQueryHelper.persistableBundleValue().key(KEY2).exists();

        assertThat(persistableBundleKeyQueryHelper.matches(mPersistableBundle, KEY)).isFalse();
    }

    @Test
    public void matches_existsRestriction_meetsRestriction_returnsTrue() {
        mPersistableBundle.putString(KEY, STRING_VALUE);
        PersistableBundleKeyQueryHelper<Queryable>
                persistableBundleKeyQueryHelper = new PersistableBundleKeyQueryHelper<>(mQuery);

        persistableBundleKeyQueryHelper.exists();

        assertThat(persistableBundleKeyQueryHelper.matches(mPersistableBundle, KEY)).isTrue();
    }

    @Test
    public void matches_existsRestriction_doesNotMeetRestriction_returnsFalse() {
        mPersistableBundle.remove(KEY);
        PersistableBundleKeyQueryHelper<Queryable>
                persistableBundleKeyQueryHelper = new PersistableBundleKeyQueryHelper<>(mQuery);

        persistableBundleKeyQueryHelper.exists();

        assertThat(persistableBundleKeyQueryHelper.matches(mPersistableBundle, KEY)).isFalse();
    }

    @Test
    public void matches_doesNotExistRestriction_meetsRestriction_returnsTrue() {
        mPersistableBundle.remove(KEY);
        PersistableBundleKeyQueryHelper<Queryable>
                persistableBundleKeyQueryHelper = new PersistableBundleKeyQueryHelper<>(mQuery);

        persistableBundleKeyQueryHelper.doesNotExist();

        assertThat(persistableBundleKeyQueryHelper.matches(mPersistableBundle, KEY)).isTrue();
    }

    @Test
    public void matches_doesNotExistRestriction_doesNotMeetRestriction_returnsFalse() {
        mPersistableBundle.putString(KEY, STRING_VALUE);
        PersistableBundleKeyQueryHelper<Queryable>
                persistableBundleKeyQueryHelper = new PersistableBundleKeyQueryHelper<>(mQuery);

        persistableBundleKeyQueryHelper.doesNotExist();

        assertThat(persistableBundleKeyQueryHelper.matches(mPersistableBundle, KEY)).isFalse();
    }

    @Test
    public void parcel_parcelsCorrectly() {
        PersistableBundleKeyQueryHelper<Queryable>
                persistableBundleKeyQueryHelper = new PersistableBundleKeyQueryHelper<>(mQuery);

        persistableBundleKeyQueryHelper.stringValue().isEqualTo("");

        assertParcelsCorrectly(PersistableBundleKeyQueryHelper.class,
                persistableBundleKeyQueryHelper);
    }

    @Test
    public void isEmptyQuery_isEmpty_returnsTrue() {
        PersistableBundleKeyQueryHelper<Queryable> persistableBundleKeyQueryHelper =
                new PersistableBundleKeyQueryHelper<>(mQuery);

        assertThat(persistableBundleKeyQueryHelper.isEmptyQuery()).isTrue();
    }

    @Test
    public void isEmptyQuery_hasExistsQuery_returnsFalse() {
        PersistableBundleKeyQueryHelper<Queryable> persistableBundleKeyQueryHelper =
                new PersistableBundleKeyQueryHelper<>(mQuery);

        persistableBundleKeyQueryHelper.exists();

        assertThat(persistableBundleKeyQueryHelper.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasDoesNotExistQuery_returnsFalse() {
        PersistableBundleKeyQueryHelper<Queryable> persistableBundleKeyQueryHelper =
                new PersistableBundleKeyQueryHelper<>(mQuery);

        persistableBundleKeyQueryHelper.doesNotExist();

        assertThat(persistableBundleKeyQueryHelper.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasStringQuery_returnsFalse() {
        PersistableBundleKeyQueryHelper<Queryable> persistableBundleKeyQueryHelper =
                new PersistableBundleKeyQueryHelper<>(mQuery);

        persistableBundleKeyQueryHelper.stringValue().isNotNull();

        assertThat(persistableBundleKeyQueryHelper.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasPersistableBundleQuery_returnsFalse() {
        PersistableBundleKeyQueryHelper<Queryable> persistableBundleKeyQueryHelper =
                new PersistableBundleKeyQueryHelper<>(mQuery);

        persistableBundleKeyQueryHelper.persistableBundleValue().key("A").stringValue().isNotNull();

        assertThat(persistableBundleKeyQueryHelper.isEmptyQuery()).isFalse();
    }
}
