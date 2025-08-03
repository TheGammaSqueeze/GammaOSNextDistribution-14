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

package com.android.adservices.service.measurement.registration;

import static com.android.adservices.service.measurement.attribution.TriggerContentProvider.TRIGGER_URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.RegistrationRequestFixture;
import android.adservices.measurement.SourceRegistrationRequest;
import android.adservices.measurement.SourceRegistrationRequestInternal;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import android.view.InputEvent;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.data.measurement.SQLDatastoreManager;
import com.android.adservices.data.measurement.SqliteObjectMapper;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class EnqueueAsyncRegistrationTest {

    private static final Context sDefaultContext = ApplicationProvider.getApplicationContext();
    private static final Uri REGISTRATION_URI_1 = Uri.parse("https://bar.test/bar?q=134");
    private static final Uri REGISTRATION_URI_2 = Uri.parse("https://foo.test/bar?q=256");
    private static final Uri INVALID_REGISTRATION_URI = Uri.parse("http://foo.test/bar?q=347");
    private static final String SDK_PACKAGE_NAME = "sdk.package.name";
    private static final boolean DEFAULT_AD_ID_PERMISSION = false;
    private static final WebSourceParams INPUT_SOURCE_REGISTRATION_1 =
            new WebSourceParams.Builder(REGISTRATION_URI_1).setDebugKeyAllowed(true).build();

    private static final WebSourceParams INPUT_SOURCE_REGISTRATION_2 =
            new WebSourceParams.Builder(REGISTRATION_URI_2).setDebugKeyAllowed(false).build();

    private static final WebSourceParams INVALID_SOURCE_REGISTRATION =
            getInvalidWebSourceParams(INVALID_REGISTRATION_URI);

    private static final WebTriggerParams INPUT_TRIGGER_REGISTRATION_1 =
            new WebTriggerParams.Builder(REGISTRATION_URI_1).setDebugKeyAllowed(true).build();

    private static final WebTriggerParams INPUT_TRIGGER_REGISTRATION_2 =
            new WebTriggerParams.Builder(REGISTRATION_URI_2).setDebugKeyAllowed(false).build();

    private static final WebTriggerParams INVALID_TRIGGER_REGISTRATION =
            getInvalidWebTriggerParams(INVALID_REGISTRATION_URI);

    private static final List<WebSourceParams> sSourceParamsList = new ArrayList<>();

    private static final List<WebTriggerParams> sTriggerParamsList = new ArrayList<>();

    private static final String PLATFORM_AD_ID_VALUE = "PLATFORM_AD_ID_VALUE";

    private static final String POST_BODY = "{\"ad_location\":\"bottom_right\"}";

    static {
        sSourceParamsList.add(INPUT_SOURCE_REGISTRATION_1);
        sSourceParamsList.add(INVALID_SOURCE_REGISTRATION);
        sSourceParamsList.add(INPUT_SOURCE_REGISTRATION_2);
        sTriggerParamsList.add(INPUT_TRIGGER_REGISTRATION_1);
        sTriggerParamsList.add(INVALID_TRIGGER_REGISTRATION);
        sTriggerParamsList.add(INPUT_TRIGGER_REGISTRATION_2);
    }

    @Mock private DatastoreManager mDatastoreManagerMock;
    @Mock private InputEvent mInputEvent;
    @Mock private ContentResolver mContentResolver;
    @Mock private ContentProviderClient mMockContentProviderClient;
    @Mock private AdServicesErrorLogger mErrorLogger;

    @Rule
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(FlagsFactory.class)
                    .setStrictness(Strictness.WARN)
                    .build();

    private static final WebSourceRegistrationRequest
            VALID_WEB_SOURCE_REGISTRATION_NULL_INPUT_EVENT =
                    new WebSourceRegistrationRequest.Builder(
                                    sSourceParamsList, Uri.parse("android-app://example.test/aD1"))
                            .setWebDestination(Uri.parse("android-app://example.test/aD1"))
                            .setAppDestination(Uri.parse("android-app://example.test/aD1"))
                            .setVerifiedDestination(Uri.parse("android-app://example.test/aD1"))
                            .build();

    private static final WebTriggerRegistrationRequest VALID_WEB_TRIGGER_REGISTRATION =
            new WebTriggerRegistrationRequest.Builder(
                            sTriggerParamsList, Uri.parse("android-app://test.e.abc"))
                    .build();

    @After
    public void cleanup() {
        SQLiteDatabase db = DbTestUtil.getMeasurementDbHelperForTest().safeGetWritableDatabase();
        for (String table : MeasurementTables.ALL_MSMT_TABLES) {
            db.delete(table, null, null);
        }
    }

    @Before
    public void before() throws RemoteException {
        ExtendedMockito.doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);
        MockitoAnnotations.initMocks(this);
        when(mContentResolver.acquireContentProviderClient(TRIGGER_URI))
                .thenReturn(mMockContentProviderClient);
        when(mMockContentProviderClient.insert(any(), any())).thenReturn(TRIGGER_URI);
    }

    @Test
    public void appSourceOrTriggerRegistrationRequest_sourceInvalidRegistrationUri_doesNotInsert() {
        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger);
        RegistrationRequest registrationRequest =
                RegistrationRequestFixture.getInvalidRegistrationRequest(
                        RegistrationRequest.REGISTER_SOURCE,
                        INVALID_REGISTRATION_URI,
                        sDefaultContext.getPackageName(),
                        SDK_PACKAGE_NAME);

        Assert.assertTrue(
                EnqueueAsyncRegistration.appSourceOrTriggerRegistrationRequest(
                        registrationRequest,
                        DEFAULT_AD_ID_PERMISSION,
                        Uri.parse("android-app://test.destination"),
                        System.currentTimeMillis(),
                        Source.SourceType.EVENT,
                        null,
                        datastoreManager,
                        mContentResolver));

        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {
            Assert.assertFalse(cursor.moveToNext());
        }
    }

    @Test
    public void testAppSourceRegistrationRequest_event_isValid() {
        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger);
        RegistrationRequest registrationRequest =
                new RegistrationRequest.Builder(
                                RegistrationRequest.REGISTER_SOURCE,
                                Uri.parse("https://baz.test"),
                                sDefaultContext.getPackageName(),
                                SDK_PACKAGE_NAME)
                        .build();

        Assert.assertTrue(
                EnqueueAsyncRegistration.appSourceOrTriggerRegistrationRequest(
                        registrationRequest,
                        DEFAULT_AD_ID_PERMISSION,
                        Uri.parse("android-app://test.destination"),
                        System.currentTimeMillis(),
                        Source.SourceType.EVENT,
                        null,
                        datastoreManager,
                        mContentResolver));

        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {
            Assert.assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            Assert.assertNotNull(asyncRegistration);
            Assert.assertNotNull(asyncRegistration.getRegistrationUri());
            Assert.assertEquals(
                    registrationRequest.getRegistrationUri(),
                    asyncRegistration.getRegistrationUri());
            Assert.assertEquals(
                    Uri.parse("android-app://test.destination"), asyncRegistration.getTopOrigin());
            Assert.assertNotNull(asyncRegistration.getRegistrationUri());
            Assert.assertNotNull(asyncRegistration.getRegistrant());
            Assert.assertEquals(
                    Uri.parse("android-app://test.destination"), asyncRegistration.getRegistrant());
            Assert.assertNotNull(asyncRegistration.getSourceType());
            Assert.assertEquals(Source.SourceType.EVENT, asyncRegistration.getSourceType());
            Assert.assertNotNull(asyncRegistration.getType());
            Assert.assertEquals(
                    AsyncRegistration.RegistrationType.APP_SOURCE, asyncRegistration.getType());
            Assert.assertEquals(
                    AsyncRedirect.RedirectBehavior.AS_IS, asyncRegistration.getRedirectBehavior());
        }
    }

    @Test
    public void testAppSourceRegistrationRequest_navigation_isValid() {
        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger);
        RegistrationRequest registrationRequest =
                new RegistrationRequest.Builder(
                                RegistrationRequest.REGISTER_SOURCE,
                                Uri.parse("https://baz.test"),
                                sDefaultContext.getPackageName(),
                                SDK_PACKAGE_NAME)
                        .setInputEvent(mInputEvent)
                        .build();

        Assert.assertTrue(
                EnqueueAsyncRegistration.appSourceOrTriggerRegistrationRequest(
                        registrationRequest,
                        DEFAULT_AD_ID_PERMISSION,
                        Uri.parse("android-app://test.destination"),
                        System.currentTimeMillis(),
                        Source.SourceType.NAVIGATION,
                        null,
                        datastoreManager,
                        mContentResolver));

        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {
            Assert.assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            Assert.assertNotNull(asyncRegistration);
            Assert.assertNotNull(asyncRegistration.getRegistrationUri());
            Assert.assertEquals(
                    registrationRequest.getRegistrationUri(),
                    asyncRegistration.getRegistrationUri());
            Assert.assertEquals(
                    Uri.parse("android-app://test.destination"), asyncRegistration.getTopOrigin());
            Assert.assertNotNull(asyncRegistration.getRegistrationUri());
            Assert.assertNotNull(asyncRegistration.getRegistrant());
            Assert.assertEquals(
                    Uri.parse("android-app://test.destination"), asyncRegistration.getRegistrant());
            Assert.assertNotNull(asyncRegistration.getSourceType());
            Assert.assertEquals(Source.SourceType.NAVIGATION, asyncRegistration.getSourceType());
            Assert.assertNotNull(asyncRegistration.getType());
            Assert.assertEquals(
                    AsyncRegistration.RegistrationType.APP_SOURCE, asyncRegistration.getType());
            Assert.assertEquals(
                    AsyncRedirect.RedirectBehavior.AS_IS, asyncRegistration.getRedirectBehavior());
        }
    }

    @Test
    public void
            appSourceOrTriggerRegistrationRequest_triggerInvalidRegistrationUri_doesNotInsert() {
        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger);
        RegistrationRequest registrationRequest =
                RegistrationRequestFixture.getInvalidRegistrationRequest(
                        RegistrationRequest.REGISTER_TRIGGER,
                        INVALID_REGISTRATION_URI,
                        sDefaultContext.getPackageName(),
                        SDK_PACKAGE_NAME);

        Assert.assertTrue(
                EnqueueAsyncRegistration.appSourceOrTriggerRegistrationRequest(
                        registrationRequest,
                        DEFAULT_AD_ID_PERMISSION,
                        Uri.parse("android-app://test.destination"),
                        System.currentTimeMillis(),
                        null,
                        null,
                        datastoreManager,
                        mContentResolver));

        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {
            Assert.assertFalse(cursor.moveToNext());
        }
    }

    @Test
    public void testAppTriggerRegistrationRequest_isValid() {
        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger);
        RegistrationRequest registrationRequest =
                new RegistrationRequest.Builder(
                                RegistrationRequest.REGISTER_TRIGGER,
                                Uri.parse("https://baz.test"),
                                sDefaultContext.getPackageName(),
                                SDK_PACKAGE_NAME)
                        .build();

        Assert.assertTrue(
                EnqueueAsyncRegistration.appSourceOrTriggerRegistrationRequest(
                        registrationRequest,
                        DEFAULT_AD_ID_PERMISSION,
                        Uri.parse("android-app://test.destination"),
                        System.currentTimeMillis(),
                        null,
                        null,
                        datastoreManager,
                        mContentResolver));

        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {
            Assert.assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            Assert.assertNotNull(asyncRegistration);
            Assert.assertNotNull(asyncRegistration.getRegistrationUri());
            Assert.assertEquals(
                    registrationRequest.getRegistrationUri(),
                    asyncRegistration.getRegistrationUri());
            Assert.assertEquals(
                    Uri.parse("android-app://test.destination"), asyncRegistration.getTopOrigin());
            Assert.assertNotNull(asyncRegistration.getRegistrationUri());
            Assert.assertNotNull(asyncRegistration.getRegistrant());
            Assert.assertEquals(
                    Uri.parse("android-app://test.destination"), asyncRegistration.getRegistrant());
            Assert.assertNull(asyncRegistration.getSourceType());
            Assert.assertEquals(
                    AsyncRedirect.RedirectBehavior.AS_IS, asyncRegistration.getRedirectBehavior());
        }
    }

    @Test
    public void testAppRegistrationRequestWithAdId_isValid() {
        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger);
        RegistrationRequest registrationRequest =
                new RegistrationRequest.Builder(
                                RegistrationRequest.REGISTER_TRIGGER,
                                Uri.parse("https://baz.test"),
                                sDefaultContext.getPackageName(),
                                SDK_PACKAGE_NAME)
                        .setAdIdValue(PLATFORM_AD_ID_VALUE)
                        .setAdIdPermissionGranted(true)
                        .build();

        Assert.assertTrue(
                EnqueueAsyncRegistration.appSourceOrTriggerRegistrationRequest(
                        registrationRequest,
                        registrationRequest.isAdIdPermissionGranted(),
                        Uri.parse("android-app://test.destination"),
                        System.currentTimeMillis(),
                        null,
                        null,
                        datastoreManager,
                        mContentResolver));

        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {
            Assert.assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            Assert.assertNotNull(asyncRegistration);
            Assert.assertTrue(asyncRegistration.hasAdIdPermission());
            Assert.assertNotNull(asyncRegistration.getPlatformAdId());
            Assert.assertEquals(PLATFORM_AD_ID_VALUE, asyncRegistration.getPlatformAdId());
        }
    }

    @Test
    public void testAppRegistrationRequestWithPostBody_isValid() {
        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger);
        RegistrationRequest registrationRequest =
                new RegistrationRequest.Builder(
                                RegistrationRequest.REGISTER_TRIGGER,
                                Uri.parse("https://baz.test"),
                                sDefaultContext.getPackageName(),
                                SDK_PACKAGE_NAME)
                        .setAdIdPermissionGranted(true)
                        .build();

        Assert.assertTrue(
                EnqueueAsyncRegistration.appSourceOrTriggerRegistrationRequest(
                        registrationRequest,
                        registrationRequest.isAdIdPermissionGranted(),
                        Uri.parse("android-app://test.destination"),
                        System.currentTimeMillis(),
                        null,
                        POST_BODY,
                        datastoreManager,
                        mContentResolver));

        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {
            Assert.assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            Assert.assertNotNull(asyncRegistration);
            Assert.assertTrue(asyncRegistration.hasAdIdPermission());
            Assert.assertNotNull(asyncRegistration.getPostBody());
            Assert.assertEquals(POST_BODY, asyncRegistration.getPostBody());
        }
    }

    @Test
    public void testWebSourceRegistrationRequest_event_isValid() {
        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger);
        Assert.assertTrue(
                EnqueueAsyncRegistration.webSourceRegistrationRequest(
                        VALID_WEB_SOURCE_REGISTRATION_NULL_INPUT_EVENT,
                        DEFAULT_AD_ID_PERMISSION,
                        Uri.parse("android-app://test.destination"),
                        System.currentTimeMillis(),
                        Source.SourceType.EVENT,
                        datastoreManager,
                        mContentResolver));

        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                // Order by registration URI
                                MeasurementTables.AsyncRegistrationContract.REGISTRATION_URI)) {

            // The invalid registration was not inserted
            Assert.assertEquals(2, cursor.getCount());

            Assert.assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration1 =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            Assert.assertEquals(REGISTRATION_URI_1, asyncRegistration1.getRegistrationUri());
            Assert.assertEquals(Source.SourceType.EVENT, asyncRegistration1.getSourceType());
            Assert.assertEquals(
                    VALID_WEB_SOURCE_REGISTRATION_NULL_INPUT_EVENT.getTopOriginUri(),
                    asyncRegistration1.getTopOrigin());
            assertEqualsWebSourceRegistrationCommon(asyncRegistration1);
            Assert.assertEquals(
                    AsyncRedirect.RedirectBehavior.AS_IS, asyncRegistration1.getRedirectBehavior());

            Assert.assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration2 =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            Assert.assertEquals(REGISTRATION_URI_2, asyncRegistration2.getRegistrationUri());
            Assert.assertEquals(Source.SourceType.EVENT, asyncRegistration2.getSourceType());
            Assert.assertEquals(
                    VALID_WEB_SOURCE_REGISTRATION_NULL_INPUT_EVENT.getTopOriginUri(),
                    asyncRegistration2.getTopOrigin());
            assertEqualsWebSourceRegistrationCommon(asyncRegistration2);
            Assert.assertEquals(
                    AsyncRedirect.RedirectBehavior.AS_IS, asyncRegistration2.getRedirectBehavior());

            Assert.assertEquals(
                    asyncRegistration1.getRegistrationId(),
                    asyncRegistration2.getRegistrationId());
        }
    }

    @Test
    public void testWebSourceRegistrationRequest_navigation_isValid() {
        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger);
        List<WebSourceParams> sourceParamsList = new ArrayList<>();
        sourceParamsList.add(INPUT_SOURCE_REGISTRATION_1);
        sourceParamsList.add(INVALID_SOURCE_REGISTRATION);
        sourceParamsList.add(INPUT_SOURCE_REGISTRATION_2);
        WebSourceRegistrationRequest validWebSourceRegistration =
                new WebSourceRegistrationRequest.Builder(
                                sourceParamsList, Uri.parse("android-app://example.test/aD1"))
                        .setWebDestination(Uri.parse("android-app://example.test/aD1"))
                        .setAppDestination(Uri.parse("android-app://example.test/aD1"))
                        .setVerifiedDestination(Uri.parse("android-app://example.test/aD1"))
                        .setInputEvent(mInputEvent)
                        .build();
        Assert.assertTrue(
                EnqueueAsyncRegistration.webSourceRegistrationRequest(
                        validWebSourceRegistration,
                        DEFAULT_AD_ID_PERMISSION,
                        Uri.parse("android-app://test.destination"),
                        System.currentTimeMillis(),
                        Source.SourceType.NAVIGATION,
                        datastoreManager,
                        mContentResolver));

        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                // Order by registration URI
                                MeasurementTables.AsyncRegistrationContract.REGISTRATION_URI)) {

            // The invalid registration was not inserted
            Assert.assertEquals(2, cursor.getCount());

            Assert.assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration1 =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            Assert.assertEquals(REGISTRATION_URI_1, asyncRegistration1.getRegistrationUri());
            Assert.assertEquals(Source.SourceType.NAVIGATION, asyncRegistration1.getSourceType());
            Assert.assertEquals(
                    validWebSourceRegistration.getTopOriginUri(),
                    asyncRegistration1.getTopOrigin());
            assertEqualsWebSourceRegistrationCommon(asyncRegistration1);
            Assert.assertEquals(
                    AsyncRedirect.RedirectBehavior.AS_IS, asyncRegistration1.getRedirectBehavior());

            Assert.assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration2 =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            Assert.assertEquals(REGISTRATION_URI_2, asyncRegistration2.getRegistrationUri());
            Assert.assertEquals(Source.SourceType.NAVIGATION, asyncRegistration2.getSourceType());
            Assert.assertEquals(
                    validWebSourceRegistration.getTopOriginUri(),
                    asyncRegistration2.getTopOrigin());
            assertEqualsWebSourceRegistrationCommon(asyncRegistration2);
            Assert.assertEquals(
                    AsyncRedirect.RedirectBehavior.AS_IS, asyncRegistration2.getRedirectBehavior());

            Assert.assertEquals(
                    asyncRegistration1.getRegistrationId(),
                    asyncRegistration2.getRegistrationId());
        }
    }

    @Test
    public void testWebTriggerRegistrationRequest_isValid() {
        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger);
        Assert.assertTrue(
                EnqueueAsyncRegistration.webTriggerRegistrationRequest(
                        VALID_WEB_TRIGGER_REGISTRATION,
                        DEFAULT_AD_ID_PERMISSION,
                        Uri.parse("android-app://test.destination"),
                        System.currentTimeMillis(),
                        datastoreManager,
                        mContentResolver));

        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                // Order by registration URI
                                MeasurementTables.AsyncRegistrationContract.REGISTRATION_URI)) {

            // The invalid registration was not inserted
            Assert.assertEquals(2, cursor.getCount());

            Assert.assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration1 =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            Assert.assertEquals(REGISTRATION_URI_1, asyncRegistration1.getRegistrationUri());
            assertEqualsWebTriggerRegistrationCommon(asyncRegistration1);
            Assert.assertEquals(
                    AsyncRedirect.RedirectBehavior.AS_IS, asyncRegistration1.getRedirectBehavior());

            Assert.assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration2 =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            Assert.assertEquals(REGISTRATION_URI_2, asyncRegistration2.getRegistrationUri());
            assertEqualsWebTriggerRegistrationCommon(asyncRegistration2);
            Assert.assertEquals(
                    AsyncRedirect.RedirectBehavior.AS_IS, asyncRegistration2.getRedirectBehavior());

            Assert.assertEquals(
                    asyncRegistration1.getRegistrationId(),
                    asyncRegistration2.getRegistrationId());
        }
    }

    @Test
    public void testRunInTransactionFail_invalid() {
        when(mDatastoreManagerMock.runInTransaction(any())).thenReturn(false);
        Assert.assertFalse(
                EnqueueAsyncRegistration.webTriggerRegistrationRequest(
                        VALID_WEB_TRIGGER_REGISTRATION,
                        DEFAULT_AD_ID_PERMISSION,
                        Uri.parse("android-app://test.destination"),
                        System.currentTimeMillis(),
                        mDatastoreManagerMock,
                        mContentResolver));
    }

    /** Test that the AsyncRegistration is inserted correctly. */
    @Test
    public void testVerifyAsyncRegistrationStoredCorrectly() {
        RegistrationRequest registrationRequest =
                new RegistrationRequest.Builder(
                                RegistrationRequest.REGISTER_SOURCE,
                                Uri.parse("https://baz.test"),
                                sDefaultContext.getPackageName(),
                                SDK_PACKAGE_NAME)
                        .build();

        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger);
        EnqueueAsyncRegistration.appSourceOrTriggerRegistrationRequest(
                registrationRequest,
                DEFAULT_AD_ID_PERMISSION,
                Uri.parse("android-app://test.destination"),
                System.currentTimeMillis(),
                Source.SourceType.EVENT,
                null,
                datastoreManager,
                mContentResolver);

        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {

            Assert.assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            Assert.assertNotNull(asyncRegistration);
            Assert.assertNotNull(asyncRegistration.getRegistrationUri());
            Assert.assertEquals(
                    registrationRequest.getRegistrationUri(),
                    asyncRegistration.getRegistrationUri());
            Assert.assertEquals(
                    Uri.parse("android-app://test.destination"), asyncRegistration.getTopOrigin());
            Assert.assertNotNull(asyncRegistration.getRegistrationUri());
            Assert.assertNotNull(asyncRegistration.getRegistrant());
            Assert.assertEquals(
                    Uri.parse("android-app://test.destination"), asyncRegistration.getRegistrant());
            Assert.assertNotNull(asyncRegistration.getSourceType());
            Assert.assertEquals(Source.SourceType.EVENT, asyncRegistration.getSourceType());
            Assert.assertNotNull(asyncRegistration.getType());
            Assert.assertEquals(
                    AsyncRegistration.RegistrationType.APP_SOURCE, asyncRegistration.getType());
            Assert.assertEquals(
                    AsyncRedirect.RedirectBehavior.AS_IS, asyncRegistration.getRedirectBehavior());
        }
    }

    @Test
    public void testAppSourcesRegistrationRequest_event_isValid() {
        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger);
        SourceRegistrationRequest sourceRegistrationRequest =
                new SourceRegistrationRequest.Builder(
                                List.of(REGISTRATION_URI_1, REGISTRATION_URI_2))
                        .build();
        SourceRegistrationRequestInternal sourceRegistrationRequestInternal =
                new SourceRegistrationRequestInternal.Builder(
                                sourceRegistrationRequest,
                                sDefaultContext.getPackageName(),
                                SDK_PACKAGE_NAME,
                                SystemClock.uptimeMillis())
                        .setAdIdValue(PLATFORM_AD_ID_VALUE)
                        .build();
        Assert.assertTrue(
                EnqueueAsyncRegistration.appSourcesRegistrationRequest(
                        sourceRegistrationRequestInternal,
                        /* adId permission*/ true,
                        Uri.parse(sDefaultContext.getPackageName()),
                        System.currentTimeMillis(),
                        Source.SourceType.EVENT,
                        POST_BODY,
                        datastoreManager,
                        mContentResolver));

        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                // Order by registration URI
                                MeasurementTables.AsyncRegistrationContract.REGISTRATION_URI)) {

            Assert.assertEquals(2, cursor.getCount());

            Assert.assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration1 =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            assertEqualsAppSourcesRegistrationCommon(asyncRegistration1);
            Assert.assertEquals(REGISTRATION_URI_1, asyncRegistration1.getRegistrationUri());
            Assert.assertEquals(Source.SourceType.EVENT, asyncRegistration1.getSourceType());
            Assert.assertEquals(
                    AsyncRedirect.RedirectBehavior.AS_IS, asyncRegistration1.getRedirectBehavior());

            Assert.assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration2 =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            assertEqualsAppSourcesRegistrationCommon(asyncRegistration2);
            Assert.assertEquals(REGISTRATION_URI_2, asyncRegistration2.getRegistrationUri());
            Assert.assertEquals(Source.SourceType.EVENT, asyncRegistration2.getSourceType());
            Assert.assertEquals(
                    AsyncRedirect.RedirectBehavior.AS_IS, asyncRegistration2.getRedirectBehavior());

            Assert.assertEquals(
                    asyncRegistration1.getRegistrationId(), asyncRegistration2.getRegistrationId());
        }
    }

    @Test
    public void testAppSourcesRegistrationRequest_navigation_isValid() {
        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger);
        SourceRegistrationRequest sourceRegistrationRequest =
                new SourceRegistrationRequest.Builder(
                                List.of(REGISTRATION_URI_1, REGISTRATION_URI_2))
                        .setInputEvent(mInputEvent)
                        .build();
        SourceRegistrationRequestInternal sourceRegistrationRequestInternal =
                new SourceRegistrationRequestInternal.Builder(
                                sourceRegistrationRequest,
                                sDefaultContext.getPackageName(),
                                SDK_PACKAGE_NAME,
                                SystemClock.uptimeMillis())
                        .setAdIdValue(PLATFORM_AD_ID_VALUE)
                        .build();
        Assert.assertTrue(
                EnqueueAsyncRegistration.appSourcesRegistrationRequest(
                        sourceRegistrationRequestInternal,
                        /* adId permission*/ true,
                        Uri.parse(sDefaultContext.getPackageName()),
                        System.currentTimeMillis(),
                        Source.SourceType.NAVIGATION,
                        POST_BODY,
                        datastoreManager,
                        mContentResolver));

        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                // Order by registration URI
                                MeasurementTables.AsyncRegistrationContract.REGISTRATION_URI)) {

            Assert.assertEquals(2, cursor.getCount());

            Assert.assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration1 =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            assertEqualsAppSourcesRegistrationCommon(asyncRegistration1);
            Assert.assertEquals(REGISTRATION_URI_1, asyncRegistration1.getRegistrationUri());
            Assert.assertEquals(Source.SourceType.NAVIGATION, asyncRegistration1.getSourceType());
            Assert.assertEquals(
                    AsyncRedirect.RedirectBehavior.AS_IS, asyncRegistration1.getRedirectBehavior());

            Assert.assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration2 =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            assertEqualsAppSourcesRegistrationCommon(asyncRegistration2);
            Assert.assertEquals(REGISTRATION_URI_2, asyncRegistration2.getRegistrationUri());
            Assert.assertEquals(Source.SourceType.NAVIGATION, asyncRegistration2.getSourceType());
            Assert.assertEquals(
                    AsyncRedirect.RedirectBehavior.AS_IS, asyncRegistration2.getRedirectBehavior());

            Assert.assertEquals(
                    asyncRegistration1.getRegistrationId(), asyncRegistration2.getRegistrationId());
        }
    }

    @Test
    public void testAppSourcesRegistrationRequest_navigationWithoutPostBody_isValid() {
        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger);
        SourceRegistrationRequest sourceRegistrationRequest =
                new SourceRegistrationRequest.Builder(
                                List.of(REGISTRATION_URI_1, REGISTRATION_URI_2))
                        .setInputEvent(mInputEvent)
                        .build();
        SourceRegistrationRequestInternal sourceRegistrationRequestInternal =
                new SourceRegistrationRequestInternal.Builder(
                                sourceRegistrationRequest,
                                sDefaultContext.getPackageName(),
                                SDK_PACKAGE_NAME,
                                SystemClock.uptimeMillis())
                        .setAdIdValue(PLATFORM_AD_ID_VALUE)
                        .build();
        Assert.assertTrue(
                EnqueueAsyncRegistration.appSourcesRegistrationRequest(
                        sourceRegistrationRequestInternal,
                        /* adId permission*/ true,
                        Uri.parse(sDefaultContext.getPackageName()),
                        System.currentTimeMillis(),
                        Source.SourceType.NAVIGATION,
                        null,
                        datastoreManager,
                        mContentResolver));

        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                // Order by registration URI
                                MeasurementTables.AsyncRegistrationContract.REGISTRATION_URI)) {

            Assert.assertEquals(2, cursor.getCount());

            Assert.assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration1 =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            assertEqualsAppSourcesRegistrationCommon(asyncRegistration1);
            Assert.assertEquals(REGISTRATION_URI_1, asyncRegistration1.getRegistrationUri());
            Assert.assertEquals(Source.SourceType.NAVIGATION, asyncRegistration1.getSourceType());
            Assert.assertNull(asyncRegistration1.getPostBody());
            Assert.assertEquals(
                    AsyncRedirect.RedirectBehavior.AS_IS, asyncRegistration1.getRedirectBehavior());

            Assert.assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration2 =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            assertEqualsAppSourcesRegistrationCommon(asyncRegistration2);
            Assert.assertEquals(REGISTRATION_URI_2, asyncRegistration2.getRegistrationUri());
            Assert.assertEquals(Source.SourceType.NAVIGATION, asyncRegistration2.getSourceType());
            Assert.assertNull(asyncRegistration2.getPostBody());
            Assert.assertEquals(
                    AsyncRedirect.RedirectBehavior.AS_IS, asyncRegistration2.getRedirectBehavior());

            Assert.assertEquals(
                    asyncRegistration1.getRegistrationId(), asyncRegistration2.getRegistrationId());
        }
    }

    private static void assertEqualsWebSourceRegistrationCommon(
            AsyncRegistration asyncRegistration) {
        Assert.assertEquals(
                Uri.parse("android-app://test.destination"),
                asyncRegistration.getRegistrant());
        Assert.assertEquals(
                Uri.parse("android-app://example.test/aD1"),
                asyncRegistration.getWebDestination());
        Assert.assertEquals(
                Uri.parse("android-app://example.test/aD1"),
                asyncRegistration.getOsDestination());
        Assert.assertEquals(
                Uri.parse("android-app://example.test/aD1"),
                asyncRegistration.getVerifiedDestination());
        Assert.assertEquals(
                AsyncRegistration.RegistrationType.WEB_SOURCE,
                asyncRegistration.getType());
    }

    private static void assertEqualsAppSourcesRegistrationCommon(
            AsyncRegistration asyncRegistration) {
        Assert.assertEquals(
                sDefaultContext.getPackageName(), asyncRegistration.getRegistrant().toString());
        Assert.assertEquals(
                sDefaultContext.getPackageName(), asyncRegistration.getTopOrigin().toString());
        Assert.assertEquals(
                AsyncRegistration.RegistrationType.APP_SOURCES, asyncRegistration.getType());
        Assert.assertFalse(Objects.requireNonNull(asyncRegistration.getRegistrationId()).isEmpty());
        Assert.assertNull(asyncRegistration.getWebDestination());
        Assert.assertNull(asyncRegistration.getOsDestination());
        Assert.assertNull(asyncRegistration.getVerifiedDestination());
    }

    private static void assertEqualsWebTriggerRegistrationCommon(
            AsyncRegistration asyncRegistration) {
        Assert.assertNull(asyncRegistration.getSourceType());
        Assert.assertEquals(
                Uri.parse("android-app://test.destination"),
                asyncRegistration.getRegistrant());
        Assert.assertEquals(
                VALID_WEB_TRIGGER_REGISTRATION.getDestination(),
                asyncRegistration.getTopOrigin());
        Assert.assertEquals(
                AsyncRegistration.RegistrationType.WEB_TRIGGER,
                asyncRegistration.getType());
    }

    private static WebSourceParams getInvalidWebSourceParams(Uri uri) {
        Parcel parcel = Parcel.obtain();
        uri.writeToParcel(parcel, 0);
        parcel.writeBoolean(false);
        parcel.setDataPosition(0);
        return WebSourceParams.CREATOR.createFromParcel(parcel);
    }

    private static WebTriggerParams getInvalidWebTriggerParams(Uri uri) {
        Parcel parcel = Parcel.obtain();
        uri.writeToParcel(parcel, 0);
        parcel.writeBoolean(false);
        parcel.setDataPosition(0);
        return WebTriggerParams.CREATOR.createFromParcel(parcel);
    }
}
