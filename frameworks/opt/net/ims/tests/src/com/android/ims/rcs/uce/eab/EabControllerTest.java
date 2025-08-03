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

import static android.telephony.CarrierConfigManager.Ims.KEY_NON_RCS_CAPABILITIES_CACHE_EXPIRATION_SEC_INT;
import static android.telephony.ims.RcsContactUceCapability.CAPABILITY_MECHANISM_PRESENCE;
import static android.telephony.ims.RcsContactUceCapability.REQUEST_RESULT_FOUND;
import static android.telephony.ims.RcsContactUceCapability.REQUEST_RESULT_NOT_FOUND;
import static android.telephony.ims.RcsContactUceCapability.SOURCE_TYPE_NETWORK;

import static com.android.ims.rcs.uce.eab.EabProvider.COMMON_URI;
import static com.android.ims.rcs.uce.eab.EabProvider.CONTACT_URI;
import static com.android.ims.rcs.uce.eab.EabProvider.OPTIONS_URI;
import static com.android.ims.rcs.uce.eab.EabProvider.PRESENCE_URI;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Looper;
import android.os.PersistableBundle;
import android.telephony.ims.RcsContactPresenceTuple;
import android.telephony.ims.RcsContactUceCapability;
import android.test.mock.MockContentResolver;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsTestBase;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class EabControllerTest extends ImsTestBase {
    EabProviderTestable mEabProviderTestable = new EabProviderTestable();
    EabControllerImpl mEabControllerSub1;
    EabControllerImpl mEabControllerSub2;
    PersistableBundle mBundle;
    ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    @Mock
    EabControllerImpl.ExpirationTimeFactory mExpirationTimeFactory;

    private static final int TEST_SUB_ID = 1;
    private static final int TEST_SECOND_SUB_ID = 2;
    private static final String TEST_PHONE_NUMBER = "16661234567";
    private static final String TEST_SERVICE_STATUS = "status";
    private static final String TEST_SERVICE_SERVICE_ID = "serviceId";
    private static final String TEST_SERVICE_VERSION = "version";
    private static final String TEST_SERVICE_DESCRIPTION = "description";
    private static final boolean TEST_AUDIO_CAPABLE = true;
    private static final boolean TEST_VIDEO_CAPABLE = false;

    private static final int TIME_OUT_IN_SEC = 5;
    private static final Uri TEST_CONTACT_URI = Uri.parse(TEST_PHONE_NUMBER + "@android.test");

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockContentResolver mockContentResolver =
                (MockContentResolver) mContext.getContentResolver();
        mEabProviderTestable.initializeForTesting(mContext);
        mockContentResolver.addProvider(EabProvider.AUTHORITY, mEabProviderTestable);

        insertContactInfoToDB();
        mEabControllerSub1 = new EabControllerImpl(
                mContext, TEST_SUB_ID, null, Looper.getMainLooper());
        mEabControllerSub1.setExpirationTimeFactory(mExpirationTimeFactory);

        mEabControllerSub2 = new EabControllerImpl(
                mContext, TEST_SECOND_SUB_ID, null, Looper.getMainLooper());
        mEabControllerSub2.setExpirationTimeFactory(mExpirationTimeFactory);

        doReturn(Instant.now().getEpochSecond()).when(mExpirationTimeFactory).getExpirationTime();

        mBundle = mContextFixture.getTestCarrierConfigBundle();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testGetAvailability() {
        List<RcsContactUceCapability> contactList = new ArrayList<>();
        contactList.add(createPresenceCapability());

        mEabControllerSub1.saveCapabilities(contactList);

        EabCapabilityResult result = mEabControllerSub1.getAvailability(TEST_CONTACT_URI);
        Assert.assertEquals(EabCapabilityResult.EAB_QUERY_SUCCESSFUL, result.getStatus());
        Assert.assertEquals(TEST_CONTACT_URI,
                result.getContactCapabilities().getContactUri());
        Assert.assertEquals(TEST_CONTACT_URI,
                result.getContactCapabilities().getEntityUri());
    }

    @Test
    @SmallTest
    public void testGetCapability() {
        List<RcsContactUceCapability> contactList = new ArrayList<>();
        contactList.add(createPresenceCapability());

        mEabControllerSub1.saveCapabilities(contactList);

        List<Uri> contactUriList = new ArrayList<>();
        contactUriList.add(TEST_CONTACT_URI);
        Assert.assertEquals(1,
                mEabControllerSub1.getCapabilities(contactUriList).size());
        Assert.assertEquals(EabCapabilityResult.EAB_QUERY_SUCCESSFUL,
                mEabControllerSub1.getCapabilities(contactUriList).get(0).getStatus());
        Assert.assertEquals(TEST_CONTACT_URI, mEabControllerSub1.getCapabilities(contactUriList).
                get(0).getContactCapabilities().getEntityUri());
    }

    @Test
    @SmallTest
    public void testGetExpiredCapability() {
        List<RcsContactUceCapability> contactList = new ArrayList<>();
        doReturn(0L).when(mExpirationTimeFactory).getExpirationTime();

        contactList.add(createPresenceCapability());

        mEabControllerSub1.saveCapabilities(contactList);

        List<Uri> contactUriList = new ArrayList<>();
        contactUriList.add(TEST_CONTACT_URI);
        Assert.assertEquals(1,
                mEabControllerSub1.getCapabilities(contactUriList).size());
        Assert.assertEquals(EabCapabilityResult.EAB_CONTACT_EXPIRED_FAILURE,
                mEabControllerSub1.getCapabilities(contactUriList).get(0).getStatus());
    }

    @Test
    @SmallTest
    public void testNonRcsCapability() {
        // Set non-rcs capabilities expiration to 121 days
        mBundle.putInt(KEY_NON_RCS_CAPABILITIES_CACHE_EXPIRATION_SEC_INT, 121 * 24 * 60 * 60);

        List<RcsContactUceCapability> contactList = new ArrayList<>();
        contactList.add(createPresenceNonRcsCapability());

        mEabControllerSub1.saveCapabilities(contactList);

        List<Uri> contactUriList = new ArrayList<>();
        contactUriList.add(TEST_CONTACT_URI);

        // Verify result is not expired
        Assert.assertEquals(1,
                mEabControllerSub1.getCapabilities(contactUriList).size());
        Assert.assertEquals(EabCapabilityResult.EAB_QUERY_SUCCESSFUL,
                mEabControllerSub1.getCapabilities(contactUriList).get(0).getStatus());
    }

    @Test
    @SmallTest
    public void testNonRcsCapabilityExpired() {
        // Set non-rcs capabilities expiration to 119 days
        mBundle.putInt(KEY_NON_RCS_CAPABILITIES_CACHE_EXPIRATION_SEC_INT, 119 * 24 * 60 * 60);
        // Set timestamp to 120 days age
        long timestamp = Instant.now().minus(120, ChronoUnit.DAYS).getEpochSecond();
        doReturn(timestamp).when(mExpirationTimeFactory).getExpirationTime();

        List<RcsContactUceCapability> contactList = new ArrayList<>();
        contactList.add(createPresenceNonRcsCapability());
        mEabControllerSub1.saveCapabilities(contactList);

        // Verify result is expired
        List<Uri> contactUriList = new ArrayList<>();
        contactUriList.add(TEST_CONTACT_URI);
        Assert.assertEquals(1,
                mEabControllerSub1.getCapabilities(contactUriList).size());
        Assert.assertEquals(EabCapabilityResult.EAB_CONTACT_EXPIRED_FAILURE,
                mEabControllerSub1.getCapabilities(contactUriList).get(0).getStatus());
    }

    @Test
    @SmallTest
    public void testSaveCapabilityAndCleanupInvalidDataInCommonTable() throws InterruptedException {
        // Insert invalid data in common table
        ContentValues data = new ContentValues();
        data.put(EabProvider.EabCommonColumns.EAB_CONTACT_ID, -1);
        data.put(EabProvider.EabCommonColumns.MECHANISM, CAPABILITY_MECHANISM_PRESENCE);
        data.put(EabProvider.EabCommonColumns.REQUEST_RESULT, REQUEST_RESULT_FOUND);
        data.put(EabProvider.EabCommonColumns.SUBSCRIPTION_ID, -1);
        mContext.getContentResolver().insert(COMMON_URI, data);

        List<RcsContactUceCapability> contactList = new ArrayList<>();
        contactList.add(createPresenceCapability());
        mEabControllerSub1.saveCapabilities(contactList);

        mExecutor.awaitTermination(TIME_OUT_IN_SEC, TimeUnit.SECONDS);

        // Verify the entry that cannot map to presence/option table has been removed
        Cursor cursor = mContext.getContentResolver().query(COMMON_URI, null, null, null, null);
        while(cursor.moveToNext()) {
            int contactId = cursor.getInt(
                    cursor.getColumnIndex(EabProvider.EabCommonColumns.EAB_CONTACT_ID));
            if (contactId == -1) {
                fail("Invalid data didn't been cleared");
            }
        }
    }

    @Test
    @SmallTest
    public void testCleanupInvalidDataInCommonTable() throws InterruptedException {
        // Insert invalid data in common table
        ContentValues data = new ContentValues();
        data.put(EabProvider.EabCommonColumns.EAB_CONTACT_ID, -1);
        data.put(EabProvider.EabCommonColumns.MECHANISM, CAPABILITY_MECHANISM_PRESENCE);
        data.put(EabProvider.EabCommonColumns.REQUEST_RESULT, REQUEST_RESULT_FOUND);
        data.put(EabProvider.EabCommonColumns.SUBSCRIPTION_ID, -1);
        mContext.getContentResolver().insert(COMMON_URI, data);

        mEabControllerSub1.cleanupOrphanedRows();
        mExecutor.execute(mEabControllerSub1.mCapabilityCleanupRunnable);
        mExecutor.awaitTermination(TIME_OUT_IN_SEC, TimeUnit.SECONDS);

        // Verify the entry that cannot map to presence/option table has been removed
        Cursor cursor = mContext.getContentResolver().query(COMMON_URI, null, null, null, null);
        while(cursor.moveToNext()) {
            int contactId = cursor.getInt(
                    cursor.getColumnIndex(EabProvider.EabCommonColumns.EAB_CONTACT_ID));
            if (contactId == -1) {
                fail("Invalid data didn't been cleared");
            }
        }
    }

    @Test
    @SmallTest
    public void testCleanupInvalidDataInPresenceTable() throws InterruptedException {
        String expiredContact = "expiredContact";
        GregorianCalendar expiredDate = new GregorianCalendar();
        expiredDate.setTimeZone(TimeZone.getTimeZone("UTC"));
        expiredDate.add(Calendar.DATE, -120);
        // Insert invalid data in presence table
        ContentValues data = new ContentValues();
        data.put(EabProvider.EabCommonColumns.REQUEST_RESULT, REQUEST_RESULT_FOUND);
        Uri commonUri = mContext.getContentResolver().insert(COMMON_URI, data);

        data = new ContentValues();
        data.put(EabProvider.PresenceTupleColumns.EAB_COMMON_ID, commonUri.getLastPathSegment());
        data.put(EabProvider.PresenceTupleColumns.CONTACT_URI, expiredContact);
        data.put(EabProvider.PresenceTupleColumns.REQUEST_TIMESTAMP,
                expiredDate.getTime().getTime() / 1000);
        mContext.getContentResolver().insert(PRESENCE_URI, data);

        mEabControllerSub1.cleanupOrphanedRows();
        mExecutor.execute(mEabControllerSub1.mCapabilityCleanupRunnable);
        mExecutor.awaitTermination(TIME_OUT_IN_SEC, TimeUnit.SECONDS);

        // Verify the invalid data has been removed after save capabilities
        Cursor cursor = mContext.getContentResolver().query(PRESENCE_URI, null, null, null, null);
        while(cursor.moveToNext()) {
            String contactUri = cursor.getString(
                    cursor.getColumnIndex(EabProvider.PresenceTupleColumns.CONTACT_URI));
            if (contactUri.equals(expiredContact)) {
                fail("Invalid data didn't been cleared");
            }
        }
    }

    @Test
    @SmallTest
    public void testCleanupInvalidDataInOptionTable() throws InterruptedException {
        String expiredFeatureTag = "expiredFeatureTag";
        GregorianCalendar expiredDate = new GregorianCalendar();
        expiredDate.setTimeZone(TimeZone.getTimeZone("UTC"));
        expiredDate.add(Calendar.DATE, -120);
        // Insert invalid data in presence table
        ContentValues data = new ContentValues();
        data.put(EabProvider.EabCommonColumns.REQUEST_RESULT, REQUEST_RESULT_NOT_FOUND);
        Uri commonUri = mContext.getContentResolver().insert(COMMON_URI, data);

        data = new ContentValues();
        data.put(EabProvider.PresenceTupleColumns.EAB_COMMON_ID, commonUri.getLastPathSegment());
        data.put(EabProvider.OptionsColumns.FEATURE_TAG, expiredFeatureTag);
        data.put(EabProvider.OptionsColumns.REQUEST_TIMESTAMP,
                expiredDate.getTime().getTime() / 1000);
        mContext.getContentResolver().insert(OPTIONS_URI, data);

        mEabControllerSub1.cleanupOrphanedRows();
        mExecutor.execute(mEabControllerSub1.mCapabilityCleanupRunnable);
        mExecutor.awaitTermination(TIME_OUT_IN_SEC, TimeUnit.SECONDS);

        // Verify the invalid data has been removed after save capabilities
        Cursor cursor = mContext.getContentResolver().query(OPTIONS_URI, null, null, null, null);
        while(cursor.moveToNext()) {
            String featureTag = cursor.getString(
                    cursor.getColumnIndex(EabProvider.OptionsColumns.FEATURE_TAG));
            if (featureTag.equals(expiredFeatureTag)) {
                fail("Invalid data didn't been cleared");
            }
        }
    }

    @Test
    @SmallTest
    public void testSaveCapabilityForDifferentSubId() {
        List<RcsContactUceCapability> contactList = new ArrayList<>();
        contactList.add(createPresenceCapability());

        mEabControllerSub1.saveCapabilities(contactList);
        mEabControllerSub2.saveCapabilities(contactList);

        List<Uri> contactUriList = new ArrayList<>();
        contactUriList.add(TEST_CONTACT_URI);
        Assert.assertEquals(1,
                mEabControllerSub1.getCapabilities(contactUriList).size());
        Assert.assertEquals(1,
                mEabControllerSub2.getCapabilities(contactUriList).size());
    }

    @Test
    @SmallTest
    public void testSaveCapabilityWithEmptyTuple() {
        List<RcsContactUceCapability> contactList = new ArrayList<>();
        contactList.add(createEmptyTuplePresenceCapability());

        mEabControllerSub1.saveCapabilities(contactList);

        List<Uri> contactUriList = new ArrayList<>();
        contactUriList.add(TEST_CONTACT_URI);
        List<EabCapabilityResult> capabilityResults =
                mEabControllerSub1.getCapabilities(contactUriList);
        Assert.assertEquals(1, capabilityResults.size());
        Assert.assertEquals(EabCapabilityResult.EAB_QUERY_SUCCESSFUL,
                capabilityResults.get(0).getStatus());
        Assert.assertEquals(0,
                capabilityResults.get(0).getContactCapabilities().getCapabilityTuples().size());
    }

    @Test
    @SmallTest
    public void testGetCapabilityWithEmptyTuple() {
        List<RcsContactUceCapability> contactList = new ArrayList<>();
        contactList.add(createEmptyTuplePresenceCapability());

        mEabControllerSub1.saveCapabilities(contactList);

        List<Uri> contactUriList = new ArrayList<>();
        contactUriList.add(TEST_CONTACT_URI);
        Assert.assertEquals(1,
                mEabControllerSub1.getCapabilities(contactUriList).size());
        Assert.assertEquals(EabCapabilityResult.EAB_QUERY_SUCCESSFUL,
                mEabControllerSub1.getCapabilities(contactUriList).get(0).getStatus());
    }

    @Test
    @SmallTest
    public void testGetExpiredCapabilityWithEmptyTuple() {
        List<RcsContactUceCapability> contactList = new ArrayList<>();
        doReturn(0L).when(mExpirationTimeFactory).getExpirationTime();

        contactList.add(createEmptyTuplePresenceCapability());

        mEabControllerSub1.saveCapabilities(contactList);

        List<Uri> contactUriList = new ArrayList<>();
        contactUriList.add(TEST_CONTACT_URI);
        Assert.assertEquals(1,
                mEabControllerSub1.getCapabilities(contactUriList).size());
        Assert.assertEquals(EabCapabilityResult.EAB_CONTACT_EXPIRED_FAILURE,
                mEabControllerSub1.getCapabilities(contactUriList).get(0).getStatus());
    }

    private RcsContactUceCapability createPresenceCapability() {
        RcsContactPresenceTuple.ServiceCapabilities.Builder serviceCapabilitiesBuilder =
                new RcsContactPresenceTuple.ServiceCapabilities.Builder(TEST_AUDIO_CAPABLE,
                        TEST_VIDEO_CAPABLE);
        RcsContactPresenceTuple tupleWithServiceCapabilities =
                new RcsContactPresenceTuple.Builder(TEST_SERVICE_STATUS, TEST_SERVICE_SERVICE_ID,
                        TEST_SERVICE_VERSION)
                        .setServiceDescription(TEST_SERVICE_DESCRIPTION)
                        .setContactUri(TEST_CONTACT_URI)
                        .setServiceCapabilities(serviceCapabilitiesBuilder.build())
                        .build();

        RcsContactPresenceTuple tupleWithEmptyServiceCapabilities =
                new RcsContactPresenceTuple.Builder(TEST_SERVICE_STATUS, TEST_SERVICE_SERVICE_ID,
                        TEST_SERVICE_VERSION)
                        .setServiceDescription(TEST_SERVICE_DESCRIPTION)
                        .setContactUri(TEST_CONTACT_URI)
                        .build();

        RcsContactUceCapability.PresenceBuilder builder =
                new RcsContactUceCapability.PresenceBuilder(
                        TEST_CONTACT_URI, SOURCE_TYPE_NETWORK, REQUEST_RESULT_FOUND);
        builder.addCapabilityTuple(tupleWithServiceCapabilities);
        builder.addCapabilityTuple(tupleWithEmptyServiceCapabilities);
        builder.setEntityUri(TEST_CONTACT_URI);
        return builder.build();
    }

    private RcsContactUceCapability createPresenceNonRcsCapability() {
        RcsContactPresenceTuple.ServiceCapabilities.Builder serviceCapabilitiesBuilder =
                new RcsContactPresenceTuple.ServiceCapabilities.Builder(false, false);
        RcsContactPresenceTuple tupleWithServiceCapabilities =
                new RcsContactPresenceTuple.Builder(TEST_SERVICE_STATUS, TEST_SERVICE_SERVICE_ID,
                        TEST_SERVICE_VERSION)
                        .setServiceDescription(TEST_SERVICE_DESCRIPTION)
                        .setContactUri(TEST_CONTACT_URI)
                        .setServiceCapabilities(serviceCapabilitiesBuilder.build())
                        .build();

        RcsContactUceCapability.PresenceBuilder builder =
                new RcsContactUceCapability.PresenceBuilder(
                        TEST_CONTACT_URI, SOURCE_TYPE_NETWORK, REQUEST_RESULT_NOT_FOUND);
        builder.addCapabilityTuple(tupleWithServiceCapabilities);
        return builder.build();
    }

    private RcsContactUceCapability createEmptyTuplePresenceCapability() {
        RcsContactUceCapability.PresenceBuilder builder =
                new RcsContactUceCapability.PresenceBuilder(
                        TEST_CONTACT_URI, SOURCE_TYPE_NETWORK, REQUEST_RESULT_FOUND);
        return builder.build();
    }

    private void insertContactInfoToDB() {
        ContentValues data = new ContentValues();
        data.put(EabProvider.ContactColumns.PHONE_NUMBER, TEST_PHONE_NUMBER);
        data.put(EabProvider.ContactColumns.RAW_CONTACT_ID, 1);
        mContext.getContentResolver().insert(CONTACT_URI, data);
    }
}
