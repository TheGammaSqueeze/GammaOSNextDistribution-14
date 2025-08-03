/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.ims.rcs.uce.eab;

import static android.telephony.ims.RcsContactUceCapability.CAPABILITY_MECHANISM_OPTIONS;
import static android.telephony.ims.RcsContactUceCapability.CAPABILITY_MECHANISM_PRESENCE;
import static android.telephony.ims.RcsContactUceCapability.REQUEST_RESULT_FOUND;

import static com.android.ims.rcs.uce.eab.EabProvider.ALL_DATA_URI;
import static com.android.ims.rcs.uce.eab.EabProvider.COMMON_URI;
import static com.android.ims.rcs.uce.eab.EabProvider.CONTACT_URI;
import static com.android.ims.rcs.uce.eab.EabProvider.OPTIONS_URI;
import static com.android.ims.rcs.uce.eab.EabProvider.PRESENCE_URI;

import static org.junit.Assert.assertEquals;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.test.mock.MockContentResolver;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsTestBase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class EabProviderTest extends ImsTestBase {
    EabProviderTestable mEabProviderTestable = new EabProviderTestable();

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockContentResolver mockContentResolver =
                (MockContentResolver) mContext.getContentResolver();
        mEabProviderTestable.initializeForTesting(mContext);
        mockContentResolver.addProvider(EabProvider.AUTHORITY, mEabProviderTestable);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testQueryContactInfo() {
        ContentValues data = new ContentValues();
        data.put(EabProvider.ContactColumns.PHONE_NUMBER, "123456");
        data.put(EabProvider.ContactColumns.RAW_CONTACT_ID, 1);
        mContext.getContentResolver().insert(CONTACT_URI, data);

        Cursor cursor = mContext.getContentResolver().query(CONTACT_URI,
                null,
                null,
                null,
                null);

        assertEquals(1, cursor.getCount());
    }

    @Test
    @SmallTest
    public void testContactIsUnique() {
        ContentValues data = new ContentValues();
        data.put(EabProvider.ContactColumns.PHONE_NUMBER, "123456");
        mContext.getContentResolver().insert(CONTACT_URI, data);

        data = new ContentValues();
        data.put(EabProvider.ContactColumns.PHONE_NUMBER, "123456");
        mContext.getContentResolver().insert(CONTACT_URI, data);

        Cursor cursor = mContext.getContentResolver().query(CONTACT_URI,
                null,
                null,
                null,
                null);

        assertEquals(1, cursor.getCount());
    }

    @Test
    @SmallTest
    public void testQueryCommonInfo() {
        ContentValues data = new ContentValues();
        data.put(EabProvider.EabCommonColumns.EAB_CONTACT_ID, 1);
        data.put(EabProvider.EabCommonColumns.MECHANISM, CAPABILITY_MECHANISM_PRESENCE);
        data.put(EabProvider.EabCommonColumns.REQUEST_RESULT, REQUEST_RESULT_FOUND);
        mContext.getContentResolver().insert(COMMON_URI, data);

        Cursor cursor = mContext.getContentResolver().query(COMMON_URI,
                null,
                null,
                null,
                null);

        assertEquals(1, cursor.getCount());
    }

    @Test
    @SmallTest
    public void testCommonIsNotUnique() {
        ContentValues data = new ContentValues();
        data.put(EabProvider.EabCommonColumns.EAB_CONTACT_ID, 1);
        data.put(EabProvider.EabCommonColumns.MECHANISM, CAPABILITY_MECHANISM_PRESENCE);
        data.put(EabProvider.EabCommonColumns.REQUEST_RESULT, REQUEST_RESULT_FOUND);
        data.put(EabProvider.EabCommonColumns.SUBSCRIPTION_ID, 1);
        mContext.getContentResolver().insert(COMMON_URI, data);

        data = new ContentValues();
        data.put(EabProvider.EabCommonColumns.EAB_CONTACT_ID, 1);
        data.put(EabProvider.EabCommonColumns.MECHANISM, CAPABILITY_MECHANISM_PRESENCE);
        data.put(EabProvider.EabCommonColumns.REQUEST_RESULT, REQUEST_RESULT_FOUND);
        data.put(EabProvider.EabCommonColumns.SUBSCRIPTION_ID, 2);
        mContext.getContentResolver().insert(COMMON_URI, data);

        Cursor cursor = mContext.getContentResolver().query(COMMON_URI,
                null,
                null,
                null,
                null);

        assertEquals(2, cursor.getCount());
    }

    @Test
    @SmallTest
    public void testQueryPresentInfo() {
        ContentValues data = new ContentValues();
        data.put(EabProvider.PresenceTupleColumns.EAB_COMMON_ID, 1);
        data.put(EabProvider.PresenceTupleColumns.AUDIO_CAPABLE, false);
        data.put(EabProvider.PresenceTupleColumns.VIDEO_CAPABLE, true);
        mContext.getContentResolver().insert(PRESENCE_URI, data);

        Cursor cursor = mContext.getContentResolver().query(PRESENCE_URI,
                null,
                null,
                null,
                null);

        assertEquals(1, cursor.getCount());
    }

    @Test
    @SmallTest
    public void testPresentTupleIsNotUnique() {
        ContentValues data = new ContentValues();
        data.put(EabProvider.PresenceTupleColumns.EAB_COMMON_ID, 1);
        data.put(EabProvider.PresenceTupleColumns.AUDIO_CAPABLE, false);
        data.put(EabProvider.PresenceTupleColumns.VIDEO_CAPABLE, true);
        mContext.getContentResolver().insert(PRESENCE_URI, data);

        data = new ContentValues();
        data.put(EabProvider.PresenceTupleColumns.EAB_COMMON_ID, 1);
        data.put(EabProvider.PresenceTupleColumns.SERVICE_ID, "Android is the best.");
        data.put(EabProvider.PresenceTupleColumns.SERVICE_VERSION, "Android is the best.");
        mContext.getContentResolver().insert(PRESENCE_URI, data);

        Cursor cursor = mContext.getContentResolver().query(PRESENCE_URI,
                null,
                null,
                null,
                null);

        assertEquals(2, cursor.getCount());
    }

    @Test
    @SmallTest
    public void testQueryOptionInfo() {
        ContentValues data = new ContentValues();
        data.put(EabProvider.OptionsColumns.EAB_COMMON_ID, 1);
        data.put(EabProvider.OptionsColumns.FEATURE_TAG, "Android is the best.");
        mContext.getContentResolver().insert(OPTIONS_URI, data);

        Cursor cursor = mContext.getContentResolver().query(OPTIONS_URI,
                null,
                null,
                null,
                null);

        assertEquals(1, cursor.getCount());
    }

    @Test
    @SmallTest
    public void testOptionIsNotUnique() {
        ContentValues data = new ContentValues();
        data.put(EabProvider.OptionsColumns.EAB_COMMON_ID, 1);
        data.put(EabProvider.OptionsColumns.FEATURE_TAG, "Android is the best.");
        mContext.getContentResolver().insert(OPTIONS_URI, data);

        data = new ContentValues();
        data.put(EabProvider.OptionsColumns.EAB_COMMON_ID, 1);
        data.put(EabProvider.OptionsColumns.FEATURE_TAG, "Android is the best!");
        mContext.getContentResolver().insert(OPTIONS_URI, data);

        Cursor cursor = mContext.getContentResolver().query(OPTIONS_URI,
                null,
                null,
                null,
                null);

        assertEquals(2, cursor.getCount());
    }


    @Test
    @SmallTest
    public void testQueryByAllDataURI() {

        ContentValues data = new ContentValues();
        data.put(EabProvider.ContactColumns._ID, 1);
        data.put(EabProvider.ContactColumns.PHONE_NUMBER, "123456");
        data.put(EabProvider.ContactColumns.RAW_CONTACT_ID, 1);
        mContext.getContentResolver().insert(CONTACT_URI, data);

        data = new ContentValues();
        data.put(EabProvider.EabCommonColumns.EAB_CONTACT_ID, 1);
        data.put(EabProvider.EabCommonColumns.MECHANISM, CAPABILITY_MECHANISM_PRESENCE);
        data.put(EabProvider.EabCommonColumns.REQUEST_RESULT, REQUEST_RESULT_FOUND);
        mContext.getContentResolver().insert(COMMON_URI, data);

        data = new ContentValues();
        data.put(EabProvider.PresenceTupleColumns.EAB_COMMON_ID, 1);
        data.put(EabProvider.PresenceTupleColumns.AUDIO_CAPABLE, false);
        data.put(EabProvider.PresenceTupleColumns.VIDEO_CAPABLE, true);
        mContext.getContentResolver().insert(PRESENCE_URI, data);

        Cursor cursor = mContext.getContentResolver().query(ALL_DATA_URI,
                null,
                null,
                null,
                null);

        assertEquals(1, cursor.getCount());
    }

    @Test
    @SmallTest
    public void testQueryBySubIdAndPhoneNumber() {
        int subid = 1;
        int incorrectSubid = 2;

        // Insert a contact that request by presence
        ContentValues data = new ContentValues();
        data.put(EabProvider.ContactColumns._ID, 1);
        data.put(EabProvider.ContactColumns.PHONE_NUMBER, "123456");
        data.put(EabProvider.ContactColumns.RAW_CONTACT_ID, 1);
        mContext.getContentResolver().insert(CONTACT_URI, data);

        data = new ContentValues();
        data.put(EabProvider.EabCommonColumns.EAB_CONTACT_ID, 1);
        data.put(EabProvider.EabCommonColumns.MECHANISM, CAPABILITY_MECHANISM_PRESENCE);
        data.put(EabProvider.EabCommonColumns.REQUEST_RESULT, REQUEST_RESULT_FOUND);
        data.put(EabProvider.EabCommonColumns.SUBSCRIPTION_ID, subid);
        data.put(EabProvider.EabCommonColumns.ENTITY_URI, "sip:456789@test.com");
        mContext.getContentResolver().insert(COMMON_URI, data);

        data = new ContentValues();
        data.put(EabProvider.PresenceTupleColumns.EAB_COMMON_ID, 1);
        data.put(EabProvider.PresenceTupleColumns.AUDIO_CAPABLE, false);
        data.put(EabProvider.PresenceTupleColumns.VIDEO_CAPABLE, true);
        mContext.getContentResolver().insert(PRESENCE_URI, data);

        // Insert a contact that request by option
        data = new ContentValues();
        data.put(EabProvider.ContactColumns._ID, 2);
        data.put(EabProvider.ContactColumns.PHONE_NUMBER, "654321");
        data.put(EabProvider.ContactColumns.RAW_CONTACT_ID, 2);
        mContext.getContentResolver().insert(CONTACT_URI, data);

        data = new ContentValues();
        data.put(EabProvider.EabCommonColumns.EAB_CONTACT_ID, 2);
        data.put(EabProvider.EabCommonColumns.SUBSCRIPTION_ID, incorrectSubid);
        data.put(EabProvider.EabCommonColumns.MECHANISM, CAPABILITY_MECHANISM_OPTIONS);
        data.put(EabProvider.EabCommonColumns.REQUEST_RESULT, REQUEST_RESULT_FOUND);
        mContext.getContentResolver().insert(COMMON_URI, data);

        data = new ContentValues();
        data.put(EabProvider.OptionsColumns.EAB_COMMON_ID, 2);
        data.put(EabProvider.OptionsColumns.FEATURE_TAG, "Android is the best.");
        mContext.getContentResolver().insert(OPTIONS_URI, data);

        Uri testUri = Uri.withAppendedPath(
                Uri.withAppendedPath(ALL_DATA_URI, String.valueOf(1)), "123456");
        Cursor cursor = mContext.getContentResolver().query(testUri,
                null,
                null,
                null,
                null);

        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        assertEquals(1, cursor.getInt(cursor.getColumnIndex(
                EabProvider.PresenceTupleColumns.VIDEO_CAPABLE)));
        assertEquals("sip:456789@test.com", cursor.getString(cursor.getColumnIndex(
                EabProvider.EabCommonColumns.ENTITY_URI)));
    }

    @Test
    @SmallTest
    public void testBulkInsert() {
        ContentValues[] data = new ContentValues[2];
        ContentValues insertData = new ContentValues();
        insertData.put(EabProvider.ContactColumns._ID, 1);
        insertData.put(EabProvider.ContactColumns.PHONE_NUMBER, "123456");
        insertData.put(EabProvider.ContactColumns.RAW_CONTACT_ID, 1);
        data[0] = insertData;
        insertData = new ContentValues();
        insertData.put(EabProvider.ContactColumns._ID, 2);
        insertData.put(EabProvider.ContactColumns.PHONE_NUMBER, "1234567");
        insertData.put(EabProvider.ContactColumns.RAW_CONTACT_ID, 2);
        data[1] = insertData;

        mContext.getContentResolver().bulkInsert(CONTACT_URI, data);

        Cursor cursor = mContext.getContentResolver().query(CONTACT_URI,
                null,
                null,
                null,
                null);
        assertEquals(2, cursor.getCount());
    }
}
