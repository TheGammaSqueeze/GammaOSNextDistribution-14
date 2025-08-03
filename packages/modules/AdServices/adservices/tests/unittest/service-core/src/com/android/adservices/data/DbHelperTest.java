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

package com.android.adservices.data;

import static com.android.adservices.data.DbHelper.DATABASE_VERSION_7;
import static com.android.adservices.data.DbTestUtil.assertMeasurementTablesDoNotExist;
import static com.android.adservices.data.DbTestUtil.doesIndexExist;
import static com.android.adservices.data.DbTestUtil.doesTableExist;
import static com.android.adservices.data.DbTestUtil.doesTableExistAndColumnCountMatch;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_WRITE_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.measurement.DbHelperV1;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.data.topics.migration.TopicsDbMigratorV7;
import com.android.adservices.data.topics.migration.TopicsDbMigratorV8;
import com.android.adservices.data.topics.migration.TopicsDbMigratorV9;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.FileCompatUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class DbHelperTest {
    protected static final Context sContext = ApplicationProvider.getApplicationContext();

    private MockitoSession mStaticMockSession;

    @Mock private Flags mMockFlags;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(ErrorLogUtil.class)
                        .strictness(Strictness.WARN)
                        .startMocking();
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
    }

    @After
    public void teardown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testOnCreate() {
        SQLiteDatabase db = DbTestUtil.getDbHelperForTest().safeGetReadableDatabase();
        assertNotNull(db);
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_taxonomy", 4));
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_app_classification_topics", 6));
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_caller_can_learn_topic", 6));
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_top_topics", 10));
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_returned_topics", 8));
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_usage_history", 3));
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_app_usage_history", 3));
        assertTrue(doesTableExistAndColumnCountMatch(db, "enrollment_data", 8));
        assertMeasurementTablesDoNotExist(db);
    }

    public static void assertEnrollmentTableDoesNotExist(SQLiteDatabase db) {
        assertFalse(doesTableExist(db, "enrollment-data"));
    }

    @Test
    public void testGetDbFileSize() {
        final String databaseName = FileCompatUtils.getAdservicesFilename("testsize.db");
        DbHelper dbHelper = new DbHelper(sContext, databaseName, 1);

        // Create database
        dbHelper.getReadableDatabase();

        // Verify size should be more than 0 bytes as database was created
        Assert.assertTrue(dbHelper.getDbFileSize() > 0);

        // Delete database file
        sContext.getDatabasePath(databaseName).delete();

        // Verify database does not exist anymore
        Assert.assertEquals(-1, dbHelper.getDbFileSize());
    }

    @Test
    public void onOpen_appliesForeignKeyConstraint() {
        // dbHelper.onOpen gets called implicitly
        SQLiteDatabase db = DbTestUtil.getDbHelperForTest().safeGetReadableDatabase();
        try (Cursor cursor = db.rawQuery("PRAGMA foreign_keys", null)) {
            cursor.moveToNext();
            assertEquals(1, cursor.getLong(0));
        }
    }

    @Test
    public void testOnUpgrade_topicsV7Migration() {
        DbHelper dbHelper = spy(DbTestUtil.getDbHelperForTest());
        SQLiteDatabase db = mock(SQLiteDatabase.class);

        // Do not actually perform queries but verify the invocation.
        TopicsDbMigratorV7 topicsDbMigratorV7 = Mockito.spy(new TopicsDbMigratorV7());
        Mockito.doNothing().when(topicsDbMigratorV7).performMigration(db);

        // Ignore Measurement Migrators
        doReturn(false).when(dbHelper).hasV1MeasurementTables(db);
        doReturn(List.of()).when(dbHelper).getOrderedDbMigrators();
        doReturn(List.of(topicsDbMigratorV7)).when(dbHelper).topicsGetOrderedDbMigrators();

        // Negative case - target version 5 is not in (oldVersion, newVersion]
        dbHelper.onUpgrade(db, /* oldVersion */ 1, /* new Version */ 5);
        Mockito.verify(topicsDbMigratorV7, Mockito.never()).performMigration(db);

        // Positive case - target version 5 is in (oldVersion, newVersion]
        dbHelper.onUpgrade(db, /* oldVersion */ 1, /* new Version */ 7);
        Mockito.verify(topicsDbMigratorV7).performMigration(db);
    }

    @Test
    public void testOnUpgrade_topicsV8Migration() {
        DbHelper dbHelper = spy(DbTestUtil.getDbHelperForTest());
        SQLiteDatabase db = mock(SQLiteDatabase.class);

        // Do not actually perform queries but verify the invocation.
        TopicsDbMigratorV8 topicsDbMigratorV8 = Mockito.spy(new TopicsDbMigratorV8());
        Mockito.doNothing().when(topicsDbMigratorV8).performMigration(db);

        // Ignore Measurement Migrators
        doReturn(false).when(dbHelper).hasV1MeasurementTables(db);
        doReturn(List.of()).when(dbHelper).getOrderedDbMigrators();
        doReturn(List.of(topicsDbMigratorV8)).when(dbHelper).topicsGetOrderedDbMigrators();

        // Negative case - target version 5 is not in (oldVersion, newVersion]
        dbHelper.onUpgrade(db, /* oldVersion */ 1, /* new Version */ 5);
        Mockito.verify(topicsDbMigratorV8, Mockito.never()).performMigration(db);

        // Positive case - target version 5 is in (oldVersion, newVersion]
        dbHelper.onUpgrade(db, /* oldVersion */ 1, /* new Version */ 8);
        Mockito.verify(topicsDbMigratorV8).performMigration(db);
    }

    @Test
    public void testOnUpgrade_topicsV8Migration_loggedTopicColumnExist() {
        String dbName = FileCompatUtils.getAdservicesFilename("test_db");
        DbHelperV1 dbHelperV1 = new DbHelperV1(sContext, dbName, /* dbVersion */ 1);
        SQLiteDatabase db = dbHelperV1.safeGetWritableDatabase();

        assertEquals(1, db.getVersion());

        DbHelper dbHelper = new DbHelper(sContext, dbName, /* dbVersion */ 7);
        dbHelper.onUpgrade(db, /* oldDbVersion */ 7, /* newDbVersion */ 8);

        // ReturnTopics table should have 8 columns in version 8 database
        assertTrue(doesTableExistAndColumnCountMatch(
                db, "topics_returned_topics", /* columnCount */8));
    }

    @Test
    public void testOnUpgrade_topicsV9Migration() {
        DbHelper dbHelper = spy(DbTestUtil.getDbHelperForTest());
        SQLiteDatabase db = mock(SQLiteDatabase.class);

        // Do not actually perform queries but verify the invocation.
        TopicsDbMigratorV9 topicsDbMigratorV9 = Mockito.spy(new TopicsDbMigratorV9());
        Mockito.doNothing().when(topicsDbMigratorV9).performMigration(db);

        // Ignore Measurement Migrators
        doReturn(false).when(dbHelper).hasV1MeasurementTables(db);
        doReturn(List.of()).when(dbHelper).getOrderedDbMigrators();
        doReturn(List.of(topicsDbMigratorV9)).when(dbHelper).topicsGetOrderedDbMigrators();

        // Negative case - target version 8 is not in (oldVersion, newVersion]
        dbHelper.onUpgrade(db, /* oldVersion */ 1, /* new Version */ 8);
        Mockito.verify(topicsDbMigratorV9, Mockito.never()).performMigration(db);

        // Positive case - target version 9 is in (oldVersion, newVersion]
        dbHelper.onUpgrade(db, /* oldVersion */ 1, /* new Version */ 9);
        Mockito.verify(topicsDbMigratorV9, times(1)).performMigration(db);

        // Positive case - target version 9 is in (oldVersion, newVersion]
        dbHelper.onUpgrade(db, /* oldVersion */ 8, /* new Version */ 9);
        Mockito.verify(topicsDbMigratorV9, times(2)).performMigration(db);

        // Don't expect interaction when we try upgrading from 9 to 9.
        dbHelper.onUpgrade(db, /* oldVersion */ 9, /* new Version */ 9);
        Mockito.verify(topicsDbMigratorV9, times(2)).performMigration(db);
    }

    @Test
    public void testOnUpgrade_topicsMigration_V7_V8_V9() {
        DbHelper dbHelper = spy(DbTestUtil.getDbHelperForTest());
        SQLiteDatabase db = mock(SQLiteDatabase.class);

        // Do not actually perform queries but verify the invocation.
        TopicsDbMigratorV7 topicsDbMigratorV7 = Mockito.spy(new TopicsDbMigratorV7());
        TopicsDbMigratorV8 topicsDbMigratorV8 = Mockito.spy(new TopicsDbMigratorV8());
        TopicsDbMigratorV9 topicsDbMigratorV9 = Mockito.spy(new TopicsDbMigratorV9());
        Mockito.doNothing().when(topicsDbMigratorV7).performMigration(db);
        Mockito.doNothing().when(topicsDbMigratorV8).performMigration(db);
        Mockito.doNothing().when(topicsDbMigratorV9).performMigration(db);

        // Ignore Measurement Migrators
        doReturn(false).when(dbHelper).hasV1MeasurementTables(db);
        doReturn(List.of()).when(dbHelper).getOrderedDbMigrators();
        doReturn(List.of(topicsDbMigratorV7, topicsDbMigratorV8, topicsDbMigratorV9))
                .when(dbHelper)
                .topicsGetOrderedDbMigrators();

        // Negative case - target version 6 is not in (oldVersion, newVersion]
        dbHelper.onUpgrade(db, /* oldVersion */ 1, /* new Version */ 6);
        Mockito.verify(topicsDbMigratorV7, Mockito.never()).performMigration(db);
        Mockito.verify(topicsDbMigratorV8, Mockito.never()).performMigration(db);
        Mockito.verify(topicsDbMigratorV9, Mockito.never()).performMigration(db);

        // Positive case - 1 -> 9 should use all migrators
        dbHelper.onUpgrade(db, /* oldVersion */ 5, /* new Version */ 9);
        Mockito.verify(topicsDbMigratorV7, times(1)).performMigration(db);
        Mockito.verify(topicsDbMigratorV8, times(1)).performMigration(db);
        Mockito.verify(topicsDbMigratorV9, times(1)).performMigration(db);

        // Positive case - 6 -> 8 should use V7, V8 migrators only
        dbHelper.onUpgrade(db, /* oldVersion */ 6, /* new Version */ 8);
        Mockito.verify(topicsDbMigratorV7, times(2)).performMigration(db);
        Mockito.verify(topicsDbMigratorV8, times(2)).performMigration(db);
        Mockito.verify(topicsDbMigratorV9, times(1)).performMigration(db);

        // Positive case - 8 -> 9 should use V9 migrator only
        dbHelper.onUpgrade(db, /* oldVersion */ 8, /* new Version */ 9);
        Mockito.verify(topicsDbMigratorV7, times(2)).performMigration(db);
        Mockito.verify(topicsDbMigratorV8, times(2)).performMigration(db);
        Mockito.verify(topicsDbMigratorV9, times(2)).performMigration(db);
    }

    @Test
    public void testOnDowngrade() {
        DbHelper dbHelper = spy(DbTestUtil.getDbHelperForTest());
        SQLiteDatabase db = mock(SQLiteDatabase.class);

        // Verify no error if downgrading db from current version to V1
        dbHelper.onDowngrade(db, DATABASE_VERSION_7, 1);
    }

    @Test
    public void testSafeGetReadableDatabase_exceptionOccurs_validatesErrorLogging() {
        DbHelper dbHelper = spy(DbTestUtil.getDbHelperForTest());
        Throwable tr = new SQLiteException();
        Mockito.doThrow(tr).when(dbHelper).getReadableDatabase();
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));

        SQLiteDatabase db = dbHelper.safeGetReadableDatabase();

        assertNull(db);
        ExtendedMockito.verify(
                () ->
                        ErrorLogUtil.e(
                                tr,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON));
    }

    @Test
    public void testSafeGetWriteDatabase_exceptionOccurs_validatesErrorLogging() {
        DbHelper dbHelper = spy(DbTestUtil.getDbHelperForTest());
        Throwable tr = new SQLiteException();
        Mockito.doThrow(tr).when(dbHelper).getWritableDatabase();
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));

        SQLiteDatabase db = dbHelper.safeGetWritableDatabase();

        assertNull(db);
        ExtendedMockito.verify(
                () ->
                        ErrorLogUtil.e(
                                tr,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_WRITE_EXCEPTION,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON));
    }

    @Test
    public void testOnUpgrade_measurementMigration_tablesExist() {
        String dbName = FileCompatUtils.getAdservicesFilename("test_db");
        DbHelperV1 dbHelperV1 = new DbHelperV1(sContext, dbName, 1);
        SQLiteDatabase db = dbHelperV1.safeGetWritableDatabase();

        assertEquals(1, db.getVersion());

        DbHelper dbHelper = new DbHelper(sContext, dbName, DATABASE_VERSION_7);
        dbHelper.onUpgrade(db, 1, DATABASE_VERSION_7);
        assertMeasurementSchema(db);
    }

    @Test
    public void testOnUpgrade_measurementMigration_tablesDoNotExist() {
        String dbName = FileCompatUtils.getAdservicesFilename("test_db_2");
        DbHelperV1 dbHelperV1 = new DbHelperV1(sContext, dbName, 1);
        SQLiteDatabase db = dbHelperV1.safeGetWritableDatabase();

        assertEquals(1, db.getVersion());
        Arrays.stream(MeasurementTables.V1_TABLES).forEach((table) -> dropTable(db, table));

        DbHelper dbHelper = new DbHelper(sContext, dbName, DATABASE_VERSION_7);
        dbHelper.onUpgrade(db, 1, DATABASE_VERSION_7);
        assertMeasurementTablesDoNotExist(db);
    }

    private void dropTable(SQLiteDatabase db, String table) {
        db.execSQL("DROP TABLE IF EXISTS '" + table + "'");
    }

    private void assertMeasurementSchema(SQLiteDatabase db) {
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_source", 31));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_trigger", 19));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_async_registration_contract", 18));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_event_report", 17));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_attribution", 10));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_aggregate_report", 14));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_aggregate_encryption_key", 4));
        assertTrue(doesTableExistAndColumnCountMatch(db, "enrollment_data", 8));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_debug_report", 4));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_xna_ignored_sources", 2));
        assertTrue(doesIndexExist(db, "idx_msmt_source_ad_ei_et"));
        assertTrue(doesIndexExist(db, "idx_msmt_source_p_ad_wd_s_et"));
        assertTrue(doesIndexExist(db, "idx_msmt_trigger_ad_ei_tt"));
        assertTrue(doesIndexExist(db, "idx_msmt_source_et"));
        assertTrue(doesIndexExist(db, "idx_msmt_trigger_tt"));
        assertTrue(doesIndexExist(db, "idx_msmt_attribution_ss_so_ds_do_ei_tt"));
    }
}
