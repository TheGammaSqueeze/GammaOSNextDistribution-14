/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.cts.cloneprofile.contacts.app;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;

import androidx.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CloneContactsProviderDataTest {

    private Context mContext;
    private ContentResolver mContentResolver;


    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mContentResolver = mContext.getContentResolver();
    }

    @Test
    public void testCloneContactsProvider_rawContactsIsEmpty() {
        Cursor queryResult = mContentResolver.query(ContactsContract.RawContacts.CONTENT_URI,
                new String[]{ContactsContract.RawContacts._ID},
                null /* queryArgs */, null /* cancellationSignal */);
        assertThat(queryResult).isNotNull();
        assertThat(queryResult.getCount()).isEqualTo(0);
    }
}
