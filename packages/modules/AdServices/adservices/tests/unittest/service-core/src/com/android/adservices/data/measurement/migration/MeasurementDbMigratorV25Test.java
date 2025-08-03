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

package com.android.adservices.data.measurement.migration;

import static com.android.adservices.data.DbTestUtil.getDbHelperForTest;
import static com.android.adservices.data.measurement.migration.MigrationTestHelper.populateDb;

import static org.junit.Assert.assertEquals;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.common.WebUtil;
import com.android.adservices.data.measurement.MeasurementDbHelper;
import com.android.adservices.data.measurement.MeasurementTables;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV25Test extends MeasurementDbMigratorTestBase {

    private static final String SOURCE_ID_1 = "source_id_1";
    private static final String TRIGGER_ID_1 = "trigger_id_1";
    private static final String REGISTRATION_URL_1 =
            WebUtil.validUrl("https://subdomain.example1.test");

    private static final String SOURCE_ID_2 = "source_id_2";
    private static final String TRIGGER_ID_2 = "trigger_id_12";
    private static final String REGISTRATION_URL_2 =
            WebUtil.validUrl("https://subdomain1.example2.test");

    private static final String SOURCE_ID_3 = "source_id_3";
    private static final String TRIGGER_ID_3 = "trigger_id_13";
    private static final String REGISTRATION_URL_3 =
            WebUtil.validUrl("https://subdomain2.example2.test");

    private static final String REGISTRATION_ORIGIN_COL = "registration_origin";

    @Test
    public void performMigration_v24ToV25WithData_maintainsDataIntegrity() {
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        24,
                        getDbHelperForTest());

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        populateSource(db, SOURCE_ID_1, REGISTRATION_URL_1);
        populateSource(db, SOURCE_ID_2, REGISTRATION_URL_2);
        populateSource(db, SOURCE_ID_3, REGISTRATION_URL_3);

        populateTrigger(db, TRIGGER_ID_1, REGISTRATION_URL_1);
        populateTrigger(db, TRIGGER_ID_2, REGISTRATION_URL_2);
        populateTrigger(db, TRIGGER_ID_3, REGISTRATION_URL_3);

        List<ContentValues> attributionRows = new ArrayList<>();
        attributionRows.add(buildAttributionV24("id1", SOURCE_ID_1, TRIGGER_ID_1));
        attributionRows.add(buildAttributionV24("id2", SOURCE_ID_2, TRIGGER_ID_2));
        Map<String, List<ContentValues>> fakeData =
                Map.of(MeasurementTables.AttributionContract.TABLE, attributionRows);

        populateDb(db, fakeData);
        getTestSubject().performMigration(db, 24, 25);
        MigrationTestHelper.verifyDataInDb(db, fakeData);

        verifyRegistrationOrigin(db);
    }

    private void populateSource(SQLiteDatabase db, String sourceId, String registrationOrigin) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.SourceContract.ID, sourceId);
        values.put(MeasurementTables.SourceContract.REGISTRATION_ORIGIN, registrationOrigin);
        db.insertWithOnConflict(
                MeasurementTables.SourceContract.TABLE,
                /*nullColumnHack=*/ null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    private void populateTrigger(SQLiteDatabase db, String triggerId, String registrationOrigin) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.TriggerContract.ID, triggerId);
        values.put(MeasurementTables.TriggerContract.REGISTRATION_ORIGIN, registrationOrigin);
        db.insertWithOnConflict(
                MeasurementTables.TriggerContract.TABLE,
                /*nullColumnHack=*/ null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    private ContentValues buildAttributionV24(String id, String sourceId, String triggerId) {
        ContentValues attribution = ContentValueFixtures.generateAttributionContentValuesV24();
        attribution.put(MeasurementTables.AttributionContract.ID, id);
        attribution.put(MeasurementTables.AttributionContract.SOURCE_ID, sourceId);
        attribution.put(MeasurementTables.AttributionContract.TRIGGER_ID, triggerId);
        return attribution;
    }

    private void verifyRegistrationOrigin(SQLiteDatabase db) {
        try (Cursor cursor =
                db.query(
                        MeasurementTables.AttributionContract.TABLE,
                        new String[] {
                            MeasurementTables.AttributionContract.ID, REGISTRATION_ORIGIN_COL
                        },
                        null,
                        null,
                        null,
                        null,
                        REGISTRATION_ORIGIN_COL + " ASC")) {
            assertEquals(2, cursor.getCount());
            cursor.moveToNext();
            String id =
                    cursor.getString(
                            cursor.getColumnIndex(MeasurementTables.AttributionContract.ID));
            String registrationOrigin =
                    cursor.getString(
                            cursor.getColumnIndex(
                                    MeasurementTables.AttributionContract.REGISTRATION_ORIGIN));
            assertEquals("id1", id);
            assertEquals(REGISTRATION_URL_1, registrationOrigin);

            cursor.moveToNext();
            id = cursor.getString(cursor.getColumnIndex(MeasurementTables.AttributionContract.ID));
            registrationOrigin =
                    cursor.getString(
                            cursor.getColumnIndex(
                                    MeasurementTables.AttributionContract.REGISTRATION_ORIGIN));
            assertEquals(id, "id2");
            assertEquals(REGISTRATION_URL_2, registrationOrigin);
        }
    }

    @Override
    int getTargetVersion() {
        return 25;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV25();
    }
}
