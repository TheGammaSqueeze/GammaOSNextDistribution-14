/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.data.enrollment;

import static com.android.adservices.mockito.ExtendedMockitoExpectations.doNothingOnErrorLogUtilError;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.verifyErrorLogUtilErrorWithAnyException;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DATA_INSERT_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.content.Context;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.shared.SharedDbHelper;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.enrollment.EnrollmentStatus;
import com.android.adservices.service.enrollment.EnrollmentUtil;
import com.android.adservices.service.proto.PrivacySandboxApi;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class EnrollmentDaoTest extends AdServicesExtendedMockitoTestCase {

    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private SharedDbHelper mDbHelper;
    private EnrollmentDao mEnrollmentDao;

    @Mock private Flags mMockFlags;
    @Mock private AdServicesLogger mLogger;
    @Mock private EnrollmentUtil mEnrollmentUtil;
    @Mock private SharedDbHelper mMockDbHelper;

    public static final EnrollmentData ENROLLMENT_DATA1 =
            new EnrollmentData.Builder()
                    .setEnrollmentId("1")
                    .setEnrolledAPIs(
                            "PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING"
                                    + " PRIVACY_SANDBOX_API_TOPICS"
                                    + " PRIVACY_SANDBOX_API_PROTECTED_APP_SIGNALS")
                    .setSdkNames("1sdk")
                    .setAttributionSourceRegistrationUrl(Arrays.asList("https://1test.com/source"))
                    .setAttributionTriggerRegistrationUrl(
                            Arrays.asList("https://1test.com/trigger"))
                    .setAttributionReportingUrl(Arrays.asList("https://1test.com"))
                    .setRemarketingResponseBasedRegistrationUrl(Arrays.asList("https://1test.com"))
                    .setEncryptionKeyUrl("https://1test.com/keys")
                    .build();

    private static final EnrollmentData ENROLLMENT_DATA2 =
            new EnrollmentData.Builder()
                    .setEnrollmentId("2")
                    .setEnrolledAPIs(
                            "PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING"
                                    + " PRIVACY_SANDBOX_API_PROTECTED_AUDIENCE")
                    .setSdkNames(Arrays.asList("2sdk", "anotherSdk"))
                    .setAttributionSourceRegistrationUrl(
                            Arrays.asList(
                                    "https://2test.com/source",
                                    "https://2test-middle.com/source",
                                    "https://2test2.com/source"))
                    .setAttributionTriggerRegistrationUrl(
                            Arrays.asList(
                                    "https://2test.com/trigger",
                                    "https://2test.com/trigger/extra/path"))
                    .setAttributionReportingUrl(Arrays.asList("https://2test.com"))
                    .setRemarketingResponseBasedRegistrationUrl(Arrays.asList("https://2test.com"))
                    .setEncryptionKeyUrl("https://2test.com/keys")
                    .build();

    private static final EnrollmentData ENROLLMENT_DATA3 =
            new EnrollmentData.Builder()
                    .setEnrollmentId("3")
                    .setEnrolledAPIs(
                            "PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING PRIVACY_SANDBOX_API_TOPICS"
                                    + " PRIVACY_SANDBOX_API_PRIVATE_AGGREGATION")
                    .setSdkNames("3sdk 31sdk")
                    .setAttributionSourceRegistrationUrl(
                            Arrays.asList("https://2test.com/source", "https://2test2.com/source"))
                    .setAttributionTriggerRegistrationUrl(
                            Arrays.asList("https://2test.com/trigger"))
                    .setAttributionReportingUrl(Arrays.asList("https://2test.com"))
                    .setRemarketingResponseBasedRegistrationUrl(Arrays.asList("https://2test.com"))
                    .setEncryptionKeyUrl("https://2test.com/keys")
                    .build();

    private static final EnrollmentData ENROLLMENT_DATA4 =
            new EnrollmentData.Builder()
                    .setEnrollmentId("4")
                    .setEnrolledAPIs(
                            "PRIVACY_SANDBOX_API_PROTECTED_AUDIENCE PRIVACY_SANDBOX_API_TOPICS"
                                    + " PRIVACY_SANDBOX_API_PROTECTED_APP_SIGNALS")
                    .setSdkNames("4sdk 41sdk")
                    .setAttributionSourceRegistrationUrl(
                            Arrays.asList("https://4test.com", "https://prefix.test-prefix.com"))
                    .setAttributionTriggerRegistrationUrl(Arrays.asList("https://4test.com"))
                    .setAttributionReportingUrl(Arrays.asList("https://4test.com"))
                    .setRemarketingResponseBasedRegistrationUrl(Arrays.asList("https://4test.com"))
                    .setEncryptionKeyUrl("https://4test.com/keys")
                    .build();

    private static final EnrollmentData ENROLLMENT_DATA5 =
            new EnrollmentData.Builder()
                    .setEnrollmentId("5")
                    .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                    .setSdkNames("5sdk 51sdk")
                    .setAttributionSourceRegistrationUrl(
                            Arrays.asList(
                                    "https://us.5test.com/source",
                                    "https://us.5test2.com/source",
                                    "https://port-test.5test3.com:443/source"))
                    .setAttributionTriggerRegistrationUrl(
                            Arrays.asList(
                                    "https://us.5test.com/trigger",
                                    "https://port-test.5test3.com:443/trigger"))
                    .setAttributionReportingUrl(Arrays.asList("https://us.5test.com"))
                    .setRemarketingResponseBasedRegistrationUrl(
                            Arrays.asList("https://us.5test.com"))
                    .setEncryptionKeyUrl("https://us.5test.com/keys")
                    .build();

    private static final EnrollmentData DUPLICATE_ID_ENROLLMENT_DATA =
            new EnrollmentData.Builder()
                    .setEnrollmentId("1")
                    .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                    .setSdkNames("4sdk")
                    .setAttributionSourceRegistrationUrl(Arrays.asList("https://4test.com/source"))
                    .setAttributionTriggerRegistrationUrl(
                            Arrays.asList("https://4test.com/trigger"))
                    .setAttributionReportingUrl(Arrays.asList("https://4test.com"))
                    .setRemarketingResponseBasedRegistrationUrl(Arrays.asList("https://4test.com"))
                    .setEncryptionKeyUrl("https://4test.com/keys")
                    .build();

    private static final EnrollmentData ENROLLMENT_DATA_MULTIPLE_FLEDGE_RBR =
            new EnrollmentData.Builder()
                    .setEnrollmentId("6")
                    .setEnrolledAPIs("PRIVACY_SANDBOX_API_PROTECTED_AUDIENCE")
                    .setSdkNames("6sdk")
                    .setAttributionSourceRegistrationUrl(Arrays.asList("https://6test.com/source"))
                    .setAttributionTriggerRegistrationUrl(
                            Arrays.asList("https://6test.com/trigger"))
                    .setAttributionReportingUrl(Arrays.asList("https://6test.com"))
                    .setRemarketingResponseBasedRegistrationUrl(
                            Arrays.asList(
                                    CommonFixture.getUri(CommonFixture.VALID_BUYER_1, "")
                                            .toString(),
                                    CommonFixture.getUri(CommonFixture.VALID_BUYER_2, "")
                                            .toString()))
                    .setEncryptionKeyUrl("https://6test.com/keys")
                    .build();

    @Before
    public void setup() {
        mDbHelper = DbTestUtil.getSharedDbHelperForTest();
        when(mMockFlags.isEnableEnrollmentTestSeed()).thenReturn(false);
        when(mEnrollmentUtil.getBuildId()).thenReturn(1);
        mEnrollmentDao =
                new EnrollmentDao(
                        sContext,
                        mDbHelper,
                        mMockFlags,
                        mMockFlags.isEnableEnrollmentTestSeed(),
                        mLogger,
                        mEnrollmentUtil);
        // We want to clear the shared pref boolean value before each test.
        mEnrollmentDao.unSeed();
    }

    @After
    public void cleanup() {
        clearAllTables();
    }

    private void clearAllTables() {
        for (String table : EnrollmentTables.ENROLLMENT_TABLES) {
            mDbHelper.safeGetWritableDatabase().delete(table, null, null);
        }
    }

    @Test
    public void testInitialization() {
        // Check seeded
        EnrollmentDao spyEnrollmentDao =
                Mockito.spy(
                        new EnrollmentDao(
                                sContext,
                                mDbHelper,
                                mMockFlags,
                                mMockFlags.isEnableEnrollmentTestSeed(),
                                mLogger,
                                mEnrollmentUtil));
        Mockito.doReturn(false).when(spyEnrollmentDao).isSeeded();

        spyEnrollmentDao.seed();
        long count =
                DatabaseUtils.queryNumEntries(
                        mDbHelper.getReadableDatabase(),
                        EnrollmentTables.EnrollmentDataContract.TABLE,
                        null);
        assertNotEquals(count, 0);

        // Check that seeded enrollments are in the table.
        EnrollmentData e = spyEnrollmentDao.getEnrollmentData("E1");
        assertNotNull(e);
        assertEquals(e.getSdkNames().get(0), "sdk1");
        EnrollmentData e2 = spyEnrollmentDao.getEnrollmentData("E2");
        assertNotNull(e2);
        assertEquals(e2.getSdkNames().get(0), "sdk2");
        EnrollmentData e3 = spyEnrollmentDao.getEnrollmentData("E3");
        assertNotNull(e3);
        assertEquals(e3.getSdkNames().get(0), "sdk3");
        spyEnrollmentDao.deleteAll();
    }

    @Test
    public void testDelete() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e = mEnrollmentDao.getEnrollmentData("1");
        assertEquals(e, ENROLLMENT_DATA1);

        mEnrollmentDao.delete("1");
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e2 = mEnrollmentDao.getEnrollmentData("1");
        assertNull(e2);
    }

    @Test
    public void testDeleteAll() {
        // Insert a record
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        long count =
                DatabaseUtils.queryNumEntries(
                        mDbHelper.getReadableDatabase(),
                        EnrollmentTables.EnrollmentDataContract.TABLE,
                        null);
        assertNotEquals(count, 0);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        // Delete the whole table
        assertTrue(mEnrollmentDao.deleteAll());
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        // Check seeded enrollments are deleted.
        count =
                DatabaseUtils.queryNumEntries(
                        mDbHelper.getReadableDatabase(),
                        EnrollmentTables.EnrollmentDataContract.TABLE,
                        null);
        assertEquals(count, 0);

        // Check unseeded.
        assertFalse(mEnrollmentDao.isSeeded());
    }

    @Test
    @SpyStatic(ErrorLogUtil.class)
    public void testDeleteAllDoesNotThrowException() {
        SharedDbHelper helper = Mockito.mock(SharedDbHelper.class);
        SQLiteDatabase db = mock(SQLiteDatabase.class);

        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
        EnrollmentDao enrollmentDao =
                new EnrollmentDao(
                        sContext,
                        helper,
                        mMockFlags,
                        mMockFlags.isEnableEnrollmentTestSeed(),
                        mLogger,
                        mEnrollmentUtil);
        when(helper.safeGetWritableDatabase()).thenReturn(db);
        when(db.delete(eq(EnrollmentTables.EnrollmentDataContract.TABLE), eq(null), eq(null)))
                .thenThrow(SQLiteException.class);

        boolean result = enrollmentDao.deleteAll();
        assertFalse(result);
    }

    @Test
    public void testOverwriteEnrollmentData() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        long count =
                DatabaseUtils.queryNumEntries(
                        mDbHelper.getReadableDatabase(),
                        EnrollmentTables.EnrollmentDataContract.TABLE,
                        null);
        assertNotEquals(0, count);

        mEnrollmentDao.overwriteData(Arrays.asList(ENROLLMENT_DATA2, ENROLLMENT_DATA3));
        count =
                DatabaseUtils.queryNumEntries(
                        mDbHelper.getReadableDatabase(),
                        EnrollmentTables.EnrollmentDataContract.TABLE,
                        null);
        assertEquals(2, count);
        assertNull(mEnrollmentDao.getEnrollmentData(ENROLLMENT_DATA1.getEnrollmentId()));
        assertEquals(
                mEnrollmentDao.getEnrollmentData(ENROLLMENT_DATA2.getEnrollmentId()),
                ENROLLMENT_DATA2);
        assertEquals(
                mEnrollmentDao.getEnrollmentData(ENROLLMENT_DATA3.getEnrollmentId()),
                ENROLLMENT_DATA3);
    }

    @Test
    public void testGetEnrollmentData() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        EnrollmentData e = mEnrollmentDao.getEnrollmentData("1");
        assertEquals(e, ENROLLMENT_DATA1);
    }

    @Test
    public void testGetAllEnrollmentData() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        mEnrollmentDao.insert(ENROLLMENT_DATA2);

        List<EnrollmentData> enrollmentDataList = mEnrollmentDao.getAllEnrollmentData();
        assertEquals(2, enrollmentDataList.size());
    }

    @Test
    public void initEnrollmentDao_ForEnrollmentSeedFlagOn_PerformsSeed() {
        when(mMockFlags.isEnableEnrollmentTestSeed()).thenReturn(true);
        EnrollmentDao enrollmentDao =
                new EnrollmentDao(
                        sContext,
                        mDbHelper,
                        mMockFlags,
                        mMockFlags.isEnableEnrollmentTestSeed(),
                        mLogger,
                        mEnrollmentUtil);

        for (EnrollmentData enrollmentData : PreEnrolledAdTechForTest.getList()) {
            EnrollmentData e = enrollmentDao.getEnrollmentData(enrollmentData.getEnrollmentId());
            assertEquals(enrollmentData, e);
        }
        enrollmentDao.deleteAll();
    }

    @Test
    public void initEnrollmentDao_ForEnrollmentSeedFlagOff_SkipsSeed() {
        when(mMockFlags.isEnableEnrollmentTestSeed()).thenReturn(false);
        for (EnrollmentData enrollmentData : PreEnrolledAdTechForTest.getList()) {
            EnrollmentData e = mEnrollmentDao.getEnrollmentData(enrollmentData.getEnrollmentId());
            assertNull(e);
        }
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForOriginMatchAndSameOrigin_isMatch() {
        when(mMockFlags.getEnforceEnrollmentOriginMatch()).thenReturn(true);
        mEnrollmentDao.insert(ENROLLMENT_DATA5);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://us.5test.com/source"));

        EnrollmentData e2 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://us.5test.com"));

        EnrollmentData e3 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://us.5test.com/anotherPath"));

        EnrollmentData e4 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://us.5test2.com/source"));

        EnrollmentData e5 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://us.5test.com/trigger"));

        assertEquals(e1, ENROLLMENT_DATA5);
        assertEquals(e2, ENROLLMENT_DATA5);
        assertEquals(e3, ENROLLMENT_DATA5);
        assertEquals(e4, ENROLLMENT_DATA5);
        assertEquals(e5, ENROLLMENT_DATA5);
        verify(mEnrollmentUtil, times(5))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(5)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForOriginMatchAndSamePort_isMatch() {
        when(mMockFlags.getEnforceEnrollmentOriginMatch()).thenReturn(true);
        mEnrollmentDao.insert(ENROLLMENT_DATA5);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://port-test.5test3.com:443/source"));
        EnrollmentData e2 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://port-test.5test3.com:443/trigger"));

        assertEquals(e1, ENROLLMENT_DATA5);
        assertEquals(e2, ENROLLMENT_DATA5);
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(2)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForOriginMatchAndDifferentPort_isNotMatch() {
        when(mMockFlags.getEnforceEnrollmentOriginMatch()).thenReturn(true);
        mEnrollmentDao.insert(ENROLLMENT_DATA5);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://port-test.5test3.com:8080/source"));
        EnrollmentData e2 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://port-test.5test3.com:8080/trigger"));

        assertNull(e1);
        assertNull(e2);
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(2)).logEnrollmentMatchStats(eq(mLogger), eq(false), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndAnyPort_isMatch() {
        when(mMockFlags.getEnforceEnrollmentOriginMatch()).thenReturn(false);
        mEnrollmentDao.insert(ENROLLMENT_DATA5);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://port-test.5test3.com:443/source"));
        EnrollmentData e2 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://port-test.5test3.com:8080/source"));
        EnrollmentData e3 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://port-test.5test3.com/source"));
        EnrollmentData e4 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://5test3.com/source"));

        assertEquals(e1, ENROLLMENT_DATA5);
        assertEquals(e2, ENROLLMENT_DATA5);
        assertEquals(e3, ENROLLMENT_DATA5);
        assertEquals(e4, ENROLLMENT_DATA5);
        verify(mEnrollmentUtil, times(4))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(4)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void
            getEnrollmentDataFromMeasurementUrl_ForOriginMatchAndDifferentOriginUri_isNotMatch() {
        when(mMockFlags.getEnforceEnrollmentOriginMatch()).thenReturn(true);
        mEnrollmentDao.insert(ENROLLMENT_DATA5);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://eu.5test.com/source"));

        EnrollmentData e2 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(Uri.parse("https://5test.com"));

        EnrollmentData e3 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://eu.5test2.com"));

        EnrollmentData e4 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://eu.5test.com/trigger"));

        assertNull(e1);
        assertNull(e2);
        assertNull(e3);
        assertNull(e4);
        verify(mEnrollmentUtil, times(4))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(4)).logEnrollmentMatchStats(eq(mLogger), eq(false), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndSameSiteUri_isMatch() {
        when(mMockFlags.getEnforceEnrollmentOriginMatch()).thenReturn(false);
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.com/source"));
        assertEquals(e1, ENROLLMENT_DATA2);

        EnrollmentData e2 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test2.com/source"));
        assertEquals(e2, ENROLLMENT_DATA2);

        EnrollmentData e3 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.com/trigger"));
        assertEquals(e3, ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(3))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(3)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndSameETLD_isMatch() {
        when(mMockFlags.getEnforceEnrollmentOriginMatch()).thenReturn(false);
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://prefix.2test.com/source"));
        assertEquals(e1, ENROLLMENT_DATA2);

        EnrollmentData e2 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://prefix.2test2.com/source"));
        assertEquals(e2, ENROLLMENT_DATA2);

        EnrollmentData e3 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://prefix.2test.com/trigger"));
        assertEquals(e3, ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(3))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(3)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndSamePath_isMatch() {
        when(mMockFlags.getEnforceEnrollmentOriginMatch()).thenReturn(false);
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.com/source"));

        EnrollmentData e2 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.com/trigger"));

        assertEquals(e1, ENROLLMENT_DATA2);
        assertEquals(e2, ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(2)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndIncompletePath_isMatch() {
        when(mMockFlags.getEnforceEnrollmentOriginMatch()).thenReturn(false);
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.com/so"));
        assertEquals(e, ENROLLMENT_DATA2);
        EnrollmentData e2 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test2.com/so"));
        assertEquals(e2, ENROLLMENT_DATA2);
        EnrollmentData e3 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.com/tri"));
        assertEquals(e3, ENROLLMENT_DATA2);
        EnrollmentData e4 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.com/trigger/extra"));
        assertEquals(e4, ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(4))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(4)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndExtraPath_isMatch() {
        when(mMockFlags.getEnforceEnrollmentOriginMatch()).thenReturn(false);
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.com/source/viewId/123"));
        assertEquals(e1, ENROLLMENT_DATA2);

        EnrollmentData e2 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test2.com/source/viewId/123"));
        assertEquals(e2, ENROLLMENT_DATA2);

        EnrollmentData e3 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.com/trigger/clickId/123"));
        assertEquals(e3, ENROLLMENT_DATA2);

        EnrollmentData e4 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.com/trigger/extra/path/clickId/123"));
        assertEquals(e4, ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(4))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(4)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndDifferentUri_isMatch() {
        when(mMockFlags.getEnforceEnrollmentOriginMatch()).thenReturn(false);
        mEnrollmentDao.insert(ENROLLMENT_DATA4);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://4test.com/path"));
        assertEquals(e1, ENROLLMENT_DATA4);

        EnrollmentData e2 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://prefix.test-prefix.com/path"));
        assertEquals(e2, ENROLLMENT_DATA4);

        EnrollmentData e3 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://test-prefix.com/path"));
        assertEquals(e3, ENROLLMENT_DATA4);
        verify(mEnrollmentUtil, times(3))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(3)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndOneUrlInEnrollment_isMatch() {
        when(mMockFlags.getEnforceEnrollmentOriginMatch()).thenReturn(false);
        EnrollmentData data =
                new EnrollmentData.Builder()
                        .setEnrollmentId("5")
                        .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                        .setSdkNames("5sdk 51sdk")
                        .setAttributionSourceRegistrationUrl(
                                Arrays.asList("https://prefix.test-prefix.com"))
                        .setAttributionTriggerRegistrationUrl(Arrays.asList("https://5test.com"))
                        .setAttributionReportingUrl(Arrays.asList("https://5test.com"))
                        .setRemarketingResponseBasedRegistrationUrl(
                                Arrays.asList("https://5test.com"))
                        .setEncryptionKeyUrl("https://5test.com/keys")
                        .build();
        mEnrollmentDao.insert(data);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://prefix.test-prefix.com"));
        assertEquals(e1, data);

        EnrollmentData e2 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://another-prefix.prefix.test-prefix.com"));
        assertEquals(e2, data);

        EnrollmentData e3 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://prefix.test-prefix.com/path"));
        assertEquals(e3, data);
        verify(mEnrollmentUtil, times(3))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(3)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndDiffSchemeUrl_matchesScheme() {
        when(mMockFlags.getEnforceEnrollmentOriginMatch()).thenReturn(false);
        EnrollmentData data =
                new EnrollmentData.Builder()
                        .setEnrollmentId("4")
                        .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                        .setSdkNames("4sdk 41sdk")
                        .setAttributionSourceRegistrationUrl(
                                Arrays.asList("http://4test.com", "https://prefix.test-prefix.com"))
                        .setAttributionTriggerRegistrationUrl(Arrays.asList("https://4test.com"))
                        .setAttributionReportingUrl(Arrays.asList("https://4test.com"))
                        .setRemarketingResponseBasedRegistrationUrl(
                                Arrays.asList("https://4test.com"))
                        .setEncryptionKeyUrl("https://4test.com/keys")
                        .build();
        mEnrollmentDao.insert(data);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://prefix.test-prefix.com"));
        assertEquals(e1, data);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(1)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndSameSubdomainChild_isMatch() {
        when(mMockFlags.getEnforceEnrollmentOriginMatch()).thenReturn(false);
        mEnrollmentDao.insert(ENROLLMENT_DATA4);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://test-prefix.com"));
        assertEquals(e, ENROLLMENT_DATA4);

        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://other-prefix.test-prefix.com"));
        assertEquals(e1, ENROLLMENT_DATA4);
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(2)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndDifferentDomain_doesNotMatch() {
        when(mMockFlags.getEnforceEnrollmentOriginMatch()).thenReturn(false);
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://abc2test.com/source"));
        assertNull(e);
        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://abc2test.com/trigger"));
        assertNull(e1);
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(2)).logEnrollmentMatchStats(eq(mLogger), eq(false), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndDifferentScheme_doesNotMatch() {
        when(mMockFlags.getEnforceEnrollmentOriginMatch()).thenReturn(false);
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("http://2test.com/source"));
        assertNull(e);
        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("http://2test.com/trigger"));
        assertNull(e1);
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(2)).logEnrollmentMatchStats(eq(mLogger), eq(false), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndDifferentETld_doesNotMatch() {
        when(mMockFlags.getEnforceEnrollmentOriginMatch()).thenReturn(false);
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(Uri.parse("https://2test.co"));

        assertNull(e);
        EnrollmentData e2 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.co/source"));
        assertNull(e2);
        EnrollmentData e3 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.co/trigger"));
        assertNull(e3);
        verify(mEnrollmentUtil, times(3))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(3)).logEnrollmentMatchStats(eq(mLogger), eq(false), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndInvalidPublicSuffix_isNoMatch() {
        when(mMockFlags.getEnforceEnrollmentOriginMatch()).thenReturn(false);
        mEnrollmentDao.insert(ENROLLMENT_DATA4);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://4test.invalid"));
        assertNull(e);
        verifyZeroInteractions(mLogger);

        EnrollmentData enrollmentData =
                new EnrollmentData.Builder()
                        .setEnrollmentId("4")
                        .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                        .setSdkNames("4sdk 41sdk")
                        .setAttributionSourceRegistrationUrl(Arrays.asList("https://4test.invalid"))
                        .setAttributionTriggerRegistrationUrl(
                                Arrays.asList("https://4test.invalid"))
                        .setAttributionReportingUrl(Arrays.asList("https://4test.invalid"))
                        .setRemarketingResponseBasedRegistrationUrl(
                                Arrays.asList("https://4test.invalid"))
                        .setEncryptionKeyUrl("https://4test.invalid/keys")
                        .build();
        mEnrollmentDao.insert(enrollmentData);
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://4test.invalid"));
        assertNull(e1);
        verifyZeroInteractions(mLogger);
    }

    @Test
    public void testGetEnrollmentDataForFledgeByAdTechIdentifier() {
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        AdTechIdentifier adtechIdentifier = AdTechIdentifier.fromString("2test.com", false);
        EnrollmentData e =
                mEnrollmentDao.getEnrollmentDataForFledgeByAdTechIdentifier(adtechIdentifier);
        assertEquals(e, ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(1)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void testGetAllFledgeEnrolledAdTechs_noEntries() {
        // Delete any entries in the database
        clearAllTables();

        assertThat(mEnrollmentDao.getAllFledgeEnrolledAdTechs()).isEmpty();
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
    }

    @Test
    public void testGetAllFledgeEnrolledAdTechs_multipleEntries() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        mEnrollmentDao.insert(ENROLLMENT_DATA3);
        verify(mEnrollmentUtil, times(3))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        Set<AdTechIdentifier> enrolledFledgeAdTechIdentifiers =
                mEnrollmentDao.getAllFledgeEnrolledAdTechs();

        assertThat(enrolledFledgeAdTechIdentifiers).hasSize(2);
        assertThat(enrolledFledgeAdTechIdentifiers)
                .containsExactly(
                        AdTechIdentifier.fromString("1test.com"),
                        AdTechIdentifier.fromString("2test.com"));
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
    }

    @Test
    public void testGetEnrollmentDataForFledgeByMatchingAdTechIdentifier_nullUri() {
        assertWithMessage("Returned enrollment pair")
                .that(mEnrollmentDao.getEnrollmentDataForFledgeByMatchingAdTechIdentifier(null))
                .isNull();
        verifyZeroInteractions(mLogger);
    }

    @Test
    public void testGetEnrollmentDataForFledgeByMatchingAdTechIdentifier_emptyUri() {
        assertWithMessage("Returned enrollment pair")
                .that(
                        mEnrollmentDao.getEnrollmentDataForFledgeByMatchingAdTechIdentifier(
                                Uri.EMPTY))
                .isNull();
        verifyZeroInteractions(mLogger);
    }

    @Test
    public void testGetEnrollmentDataForFledgeByMatchingAdTechIdentifier_noMatchFound() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        Uri nonMatchingUri =
                CommonFixture.getUri(CommonFixture.VALID_BUYER_1, "/path/for/resource");

        Pair<AdTechIdentifier, EnrollmentData> enrollmentResult =
                mEnrollmentDao.getEnrollmentDataForFledgeByMatchingAdTechIdentifier(nonMatchingUri);

        assertWithMessage("Returned enrollment result").that(enrollmentResult).isNull();
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(1)).logEnrollmentMatchStats(eq(mLogger), eq(false), eq(1));
    }

    @Test
    public void testGetEnrollmentDataForFledgeByMatchingAdTechIdentifier_matchesHostExactly() {
        mEnrollmentDao.insert(ENROLLMENT_DATA_MULTIPLE_FLEDGE_RBR);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        Uri exactMatchUri = CommonFixture.getUri(CommonFixture.VALID_BUYER_1, "/path/for/resource");

        Pair<AdTechIdentifier, EnrollmentData> enrollmentResult =
                mEnrollmentDao.getEnrollmentDataForFledgeByMatchingAdTechIdentifier(exactMatchUri);

        assertWithMessage("Returned EnrollmentData")
                .that(enrollmentResult.second)
                .isEqualTo(ENROLLMENT_DATA_MULTIPLE_FLEDGE_RBR);
        assertWithMessage("Returned AdTechIdentifier")
                .that(enrollmentResult.first)
                .isEqualTo(CommonFixture.VALID_BUYER_1);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(1)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void testGetEnrollmentDataForFledgeByMatchingAdTechIdentifier_matchesHostSubdomain() {
        mEnrollmentDao.insert(ENROLLMENT_DATA_MULTIPLE_FLEDGE_RBR);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        Uri subdomainMatchUri =
                CommonFixture.getUriWithValidSubdomain(
                        CommonFixture.VALID_BUYER_2.toString(), "/path/for/resource");

        Pair<AdTechIdentifier, EnrollmentData> enrollmentResult =
                mEnrollmentDao.getEnrollmentDataForFledgeByMatchingAdTechIdentifier(
                        subdomainMatchUri);

        assertWithMessage("Returned EnrollmentData")
                .that(enrollmentResult.second)
                .isEqualTo(ENROLLMENT_DATA_MULTIPLE_FLEDGE_RBR);
        assertWithMessage("Returned AdTechIdentifier")
                .that(enrollmentResult.first)
                .isEqualTo(CommonFixture.VALID_BUYER_2);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(1)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void testGetEnrollmentDataForFledgeByMatchingAdTechIdentifier_nonMatchingSubstring() {
        mEnrollmentDao.insert(ENROLLMENT_DATA_MULTIPLE_FLEDGE_RBR);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        // Note this URI is missing a "." separating the prefix from the expected host
        Uri nonMatchingSubstringUri =
                CommonFixture.getUri(
                        "prefixstring" + CommonFixture.VALID_BUYER_2, "/path/for/resource");

        Pair<AdTechIdentifier, EnrollmentData> enrollmentResult =
                mEnrollmentDao.getEnrollmentDataForFledgeByMatchingAdTechIdentifier(
                        nonMatchingSubstringUri);

        assertWithMessage("Returned enrollment result").that(enrollmentResult).isNull();
    }

    @Test
    public void testGetEnrollmentDataFromSdkName() {
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e = mEnrollmentDao.getEnrollmentDataFromSdkName("2sdk");
        assertEquals(e, ENROLLMENT_DATA2);
        EnrollmentData e2 = mEnrollmentDao.getEnrollmentDataFromSdkName("anotherSdk");
        assertEquals(e2, ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(2)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));

        mEnrollmentDao.insert(ENROLLMENT_DATA3);
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e3 = mEnrollmentDao.getEnrollmentDataFromSdkName("31sdk");
        assertEquals(e3, ENROLLMENT_DATA3);
        verify(mEnrollmentUtil, times(3))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(3)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void testDuplicateEnrollmentData() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e = mEnrollmentDao.getEnrollmentData("1");
        assertEquals(ENROLLMENT_DATA1, e);

        mEnrollmentDao.insert(DUPLICATE_ID_ENROLLMENT_DATA);
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        e = mEnrollmentDao.getEnrollmentData("1");
        assertEquals(DUPLICATE_ID_ENROLLMENT_DATA, e);
    }

    @Test
    public void testGetEnrollmentRecordsCountForLogging_insertionsMatchCount() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        mEnrollmentDao.insert(ENROLLMENT_DATA3);
        mEnrollmentDao.insert(DUPLICATE_ID_ENROLLMENT_DATA);
        verify(mEnrollmentUtil, times(4))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        int enrollmentRecordsCount = mEnrollmentDao.getEnrollmentRecordCountForLogging();
        assertEquals(enrollmentRecordsCount, 3);
    }

    @Test
    public void testGetEnrollmentRecordsCountForLogging_limitedEnrollmentLoggingEnabled() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        mEnrollmentDao.insert(ENROLLMENT_DATA3);
        when(mMockFlags.getEnrollmentEnableLimitedLogging()).thenReturn(true);
        int enrollmentRecordsCount = mEnrollmentDao.getEnrollmentRecordCountForLogging();
        assertEquals(-2, enrollmentRecordsCount);
    }

    @Test
    public void testGetEnrollmentRecordsCountForLogging_databaseError() {
        EnrollmentDao enrollmentDao =
                new EnrollmentDao(
                        sContext,
                        mMockDbHelper,
                        mMockFlags,
                        mMockFlags.isEnableEnrollmentTestSeed(),
                        mLogger,
                        mEnrollmentUtil);
        enrollmentDao.insert(ENROLLMENT_DATA1);
        enrollmentDao.insert(ENROLLMENT_DATA2);
        enrollmentDao.insert(ENROLLMENT_DATA3);
        when(mMockFlags.getEnrollmentEnableLimitedLogging()).thenReturn(false);
        when(mMockDbHelper.safeGetWritableDatabase()).thenReturn(null);
        int enrollmentRecordsCount = enrollmentDao.getEnrollmentRecordCountForLogging();
        assertEquals(-1, enrollmentRecordsCount);
    }

    @Test
    @SpyStatic(ErrorLogUtil.class)
    public void testInsert_throwsSQLException_logsCEL() throws Exception {
        SQLiteDatabase db = mock(SQLiteDatabase.class);
        EnrollmentData enrollmentData = mock(EnrollmentData.class);

        doNothingOnErrorLogUtilError();

        when(mMockDbHelper.safeGetWritableDatabase()).thenReturn(db);
        when(db.insertWithOnConflict(
                        eq(EnrollmentTables.EnrollmentDataContract.TABLE), any(), any(), anyInt()))
                .thenThrow(new SQLException());

        assertThat(mEnrollmentDao.insert(enrollmentData)).isFalse();

        verifyErrorLogUtilErrorWithAnyException(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DATA_INSERT_ERROR,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
    }

    @Test
    public void testGetEnrollmentData_enrollmentDataGetEnrolledAPIs() {
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        mEnrollmentDao.insert(ENROLLMENT_DATA3);
        // Checking ENROLLMENT_DATA2
        EnrollmentData enrollmentData2 = mEnrollmentDao.getEnrollmentData("2");

        String enrolledAPIsString2 =
                "PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING"
                        + " PRIVACY_SANDBOX_API_PROTECTED_AUDIENCE";
        assertEquals(enrolledAPIsString2, enrollmentData2.getEnrolledAPIsString());

        List<PrivacySandboxApi> enrolledAPIs2 =
                ImmutableList.of(
                        PrivacySandboxApi.PRIVACY_SANDBOX_API_PROTECTED_AUDIENCE,
                        PrivacySandboxApi.PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING);
        assertEquals(2, enrollmentData2.getEnrolledAPIs().size());
        assertThat(enrollmentData2.getEnrolledAPIs()).containsExactlyElementsIn(enrolledAPIs2);

        // Checking ENROLLMENT_DATA3
        EnrollmentData enrollmentData3 = mEnrollmentDao.getEnrollmentData("3");

        String enrolledAPIsString3 =
                "PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING PRIVACY_SANDBOX_API_TOPICS"
                        + " PRIVACY_SANDBOX_API_PRIVATE_AGGREGATION";
        assertEquals(enrolledAPIsString3, enrollmentData3.getEnrolledAPIsString());

        List<PrivacySandboxApi> enrolledAPIs3 =
                ImmutableList.of(
                        PrivacySandboxApi.PRIVACY_SANDBOX_API_TOPICS,
                        PrivacySandboxApi.PRIVACY_SANDBOX_API_PRIVATE_AGGREGATION,
                        PrivacySandboxApi.PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING);
        assertEquals(3, enrollmentData3.getEnrolledAPIs().size());
        assertThat(enrollmentData3.getEnrolledAPIs()).containsExactlyElementsIn(enrolledAPIs3);
    }

    @Test
    public void getEnrollmentDataForPASByMatchingAdTechIdentifier_uriIsMatch() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        mEnrollmentDao.insert(ENROLLMENT_DATA4);
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        Pair<AdTechIdentifier, EnrollmentData> paired =
                mEnrollmentDao.getEnrollmentDataForPASByMatchingAdTechIdentifier(
                        Uri.parse("https://4test.com/"));

        assertEquals(paired.first, AdTechIdentifier.fromString("4test.com"));
        assertEquals(paired.second, ENROLLMENT_DATA4);
    }

    @Test
    public void getEnrollmentDataForPASByMatchingAdTechIdentifier_uriIsNotMatch() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        Pair<AdTechIdentifier, EnrollmentData> enrollmentResult =
                mEnrollmentDao.getEnrollmentDataForPASByMatchingAdTechIdentifier(
                        Uri.parse("https://2test.com/"));
        assertWithMessage("Returned enrollment result").that(enrollmentResult).isNull();
    }

    @Test
    public void getEnrollmentDataForPASByMatchingAdTechIdentifier_uriIsMatchWithSubdomain() {
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        mEnrollmentDao.insert(ENROLLMENT_DATA4);
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        Uri subdomainMatchUri = Uri.parse("https://example.abc.4test.com/path/for/resource");

        Pair<AdTechIdentifier, EnrollmentData> enrollmentResult =
                mEnrollmentDao.getEnrollmentDataForPASByMatchingAdTechIdentifier(subdomainMatchUri);

        assertEquals(enrollmentResult.first, AdTechIdentifier.fromString("4test.com"));
        assertEquals(enrollmentResult.second, ENROLLMENT_DATA4);
    }

    @Test
    public void getEnrollmentDataForPASByMatchingAdTechIdentifier_uriIsMatchNoPASEnrollments() {
        mEnrollmentDao.insert(ENROLLMENT_DATA3);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        Pair<AdTechIdentifier, EnrollmentData> enrollmentResult =
                mEnrollmentDao.getEnrollmentDataForPASByMatchingAdTechIdentifier(
                        Uri.parse("https://2test.com"));
        assertWithMessage("Returned enrollment result").that(enrollmentResult).isNull();
    }

    @Test
    public void getEnrollmentDataForPASByMatchingAdTechIdentifier_uriIsNotMatchPASEnrollments() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        Pair<AdTechIdentifier, EnrollmentData> enrollmentResult =
                mEnrollmentDao.getEnrollmentDataForPASByMatchingAdTechIdentifier(
                        Uri.parse("https://test2.com"));
        assertWithMessage("Returned enrollment result").that(enrollmentResult).isNull();
    }

    @Test
    public void getEnrollmentDataForPASByAdTechIdentifier_isMatch() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        AdTechIdentifier adTechIdentifier = AdTechIdentifier.fromString("1test.com");
        EnrollmentData enrollmentResult =
                mEnrollmentDao.getEnrollmentDataForPASByAdTechIdentifier(adTechIdentifier);
        assertEquals(ENROLLMENT_DATA1, enrollmentResult);
    }

    @Test
    public void getEnrollmentDataForPASByAdTechIdentifier_isNotMatch() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        AdTechIdentifier adTechIdentifier = AdTechIdentifier.fromString("2test.com");
        EnrollmentData enrollmentResult =
                mEnrollmentDao.getEnrollmentDataForPASByAdTechIdentifier(adTechIdentifier);
        assertNull(enrollmentResult);
    }

    @Test
    public void getAllPASEnrolledAdTechs_multipleEntries() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        mEnrollmentDao.insert(ENROLLMENT_DATA3);
        mEnrollmentDao.insert(ENROLLMENT_DATA4);
        verify(mEnrollmentUtil, times(4))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        Set<AdTechIdentifier> enrolledPASAdTechIdentifiers =
                mEnrollmentDao.getAllPASEnrolledAdTechs();

        assertThat(enrolledPASAdTechIdentifiers).hasSize(2);
        assertThat(enrolledPASAdTechIdentifiers)
                .containsExactly(
                        AdTechIdentifier.fromString("1test.com"),
                        AdTechIdentifier.fromString("4test.com"));
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
    }

    @Test
    public void getAllPASEnrolledAdTechs_noEntries() {
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        mEnrollmentDao.insert(ENROLLMENT_DATA3);
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        Set<AdTechIdentifier> enrolledPASAdTechIdentifiers =
                mEnrollmentDao.getAllPASEnrolledAdTechs();

        assertThat(enrolledPASAdTechIdentifiers).isEmpty();
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
    }
}
