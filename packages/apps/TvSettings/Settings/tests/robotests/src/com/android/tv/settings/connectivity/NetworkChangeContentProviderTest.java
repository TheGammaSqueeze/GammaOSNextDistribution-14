/*
 * Copyright (C) 2024 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.tv.settings.connectivity;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Tests for {@link NetworkChangeContentProvider}.
 */
@RunWith(RobolectricTestRunner.class)
public class NetworkChangeContentProviderTest {

    private NetworkChangeContentProvider mContentProvider;
    private Context mContext;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mContentProvider = new NetworkChangeContentProvider();
        mContentProvider.attachInfo(mContext, null);
    }

    @Test
    public void onCreate_defaultState_returnsTrue() {
        assertThat(mContentProvider.onCreate()).isTrue();
    }

    @Test
    public void query_currentNetworkCount_returnsInt() {
        mContext.getSharedPreferences(NetworkChangeDetectionConfigs.SHARED_PREFERENCES_NAME,
                Context.MODE_PRIVATE).edit().putInt(NetworkChangeDetectionConfigs.PREFERENCE_KEY,
                1).commit();
        Uri contentUri = Uri.parse(
                "content://" + NetworkChangeDetectionConfigs.AUTHORITY + "/"
                        + NetworkChangeDetectionConfigs.PREFERENCE_KEY);
        Cursor cursor =
                mContentProvider.query(
                        contentUri,
                        /* projection= */ null,
                        /* selection= */ null,
                        /* selectionArgs= */ null,
                        /* sortOrder= */ null);

        assertThat(cursor).isNotNull();
        assertThat(cursor.getCount()).isEqualTo(1);
        assertThat(cursor.getColumnCount()).isEqualTo(1);
        assertThat(cursor.moveToFirst()).isTrue();
    }

    @Test(expected = IllegalArgumentException.class)
    public void query_incorrectContentUri_returnsNull() throws IllegalArgumentException {
        String wrongKey = "wrong_test_key";
        Uri contentUri = Uri.parse(
                "content://" + NetworkChangeDetectionConfigs.AUTHORITY + "/" + wrongKey);

        Cursor cursor =
                mContentProvider.query(
                        contentUri,
                        /* projection= */ null,
                        /* selection= */ null,
                        /* selectionArgs= */ null,
                        /* sortOrder= */ null);

        assertThat(cursor).isNull();
    }
}

