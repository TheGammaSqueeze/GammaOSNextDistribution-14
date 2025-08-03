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
 * limitations under the License.
 */

package com.android.server.healthconnect.storage.datatypehelpers;

import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.SKIN_TEMPERATURE_RECORD_DELTA_AVG;
import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.SKIN_TEMPERATURE_RECORD_DELTA_MAX;
import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.SKIN_TEMPERATURE_RECORD_DELTA_MIN;

import static com.android.server.healthconnect.storage.HealthConnectDatabase.createTable;
import static com.android.server.healthconnect.storage.datatypehelpers.SeriesRecordHelper.PARENT_KEY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.REAL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.REAL_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorDouble;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorInt;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorLong;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorUUID;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.health.connect.AggregateResult;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.datatypes.units.Temperature;
import android.health.connect.internal.datatypes.SkinTemperatureRecordInternal;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.android.server.healthconnect.storage.request.AggregateParams;
import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.SqlJoin;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Helper class for SkinTemperatureRecord.
 *
 * @hide
 */
public final class SkinTemperatureRecordHelper
        extends IntervalRecordHelper<SkinTemperatureRecordInternal> {

    private static final String TABLE_NAME = "skin_temperature_record_table";

    private static final String SKIN_TEMPERATURE_BASELINE_COLUMN_NAME = "baseline";

    private static final String SKIN_TEMPERATURE_MEASUREMENT_LOCATION_COLUMN_NAME =
            "measurement_location";
    private static final String SERIES_TABLE_NAME = "skin_temperature_delta_table";

    private static final String SKIN_TEMPERATURE_DELTA_COLUMN_NAME = "delta";
    private static final String EPOCH_MILLIS_COLUMN_NAME = "epoch_millis";

    public SkinTemperatureRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_SKIN_TEMPERATURE);
    }

    @NonNull
    @Override
    String getMainTableName() {
        return TABLE_NAME;
    }

    @NonNull
    String getSeriesDataTableName() {
        return SERIES_TABLE_NAME;
    }

    @Override
    List<CreateTableRequest> getChildTableCreateRequests() {
        return List.of(
                new CreateTableRequest(getSeriesDataTableName(), getSeriesTableColumnInfo())
                        .addForeignKey(
                                getMainTableName(),
                                List.of(PARENT_KEY_COLUMN_NAME),
                                List.of(PRIMARY_COLUMN_NAME)));
    }

    @Override
    SqlJoin getJoinForReadRequest() {
        return new SqlJoin(
                getMainTableName(),
                getSeriesDataTableName(),
                PRIMARY_COLUMN_NAME,
                PARENT_KEY_COLUMN_NAME);
    }

    @Override
    @SuppressWarnings("unchecked")
    List<UpsertTableRequest> getChildTableUpsertRequests(
            @NonNull SkinTemperatureRecordInternal record) {
        return record.getSamples().stream()
                .map(
                        sample -> {
                            ContentValues contentValues = new ContentValues();
                            populateSampleTo(
                                    contentValues,
                                    (SkinTemperatureRecordInternal.SkinTemperatureDeltaSample)
                                            sample);
                            return new UpsertTableRequest(getSeriesDataTableName(), contentValues)
                                    .setParentColumnForChildTables(PARENT_KEY_COLUMN_NAME);
                        })
                .toList();
    }

    @Override
    void populateSpecificRecordValue(
            @NonNull Cursor cursor, @NonNull SkinTemperatureRecordInternal recordInternal) {
        int measurementLocation =
                getCursorInt(cursor, SKIN_TEMPERATURE_MEASUREMENT_LOCATION_COLUMN_NAME);
        double baseline = getCursorDouble(cursor, SKIN_TEMPERATURE_BASELINE_COLUMN_NAME);

        recordInternal.setMeasurementLocation(measurementLocation);
        recordInternal.setBaseline(Temperature.fromCelsius(baseline));

        HashSet<SkinTemperatureRecordInternal.SkinTemperatureDeltaSample>
                skinTemperatureDeltaSamples = new HashSet<>();
        UUID uuid = getCursorUUID(cursor, UUID_COLUMN_NAME);
        do {
            skinTemperatureDeltaSamples.add(
                    new SkinTemperatureRecordInternal.SkinTemperatureDeltaSample(
                            getCursorDouble(cursor, SKIN_TEMPERATURE_DELTA_COLUMN_NAME),
                            getCursorLong(cursor, EPOCH_MILLIS_COLUMN_NAME)));
        } while (cursor.moveToNext() && uuid.equals(getCursorUUID(cursor, UUID_COLUMN_NAME)));
        // In case we hit another record, move the cursor back to read next record in outer
        // RecordHelper#getInternalRecords loop.
        cursor.moveToPrevious();
        recordInternal.setSamples(skinTemperatureDeltaSamples);
    }

    @Override
    void populateSpecificContentValues(
            @NonNull ContentValues contentValues, @NonNull SkinTemperatureRecordInternal record) {
        contentValues.put(
                SKIN_TEMPERATURE_MEASUREMENT_LOCATION_COLUMN_NAME, record.getMeasurementLocation());
        contentValues.put(
                SKIN_TEMPERATURE_BASELINE_COLUMN_NAME, record.getBaseline().getInCelsius());
    }

    @NonNull
    @Override
    List<Pair<String, String>> getIntervalRecordColumnInfo() {
        return List.of(
                Pair.create(SKIN_TEMPERATURE_MEASUREMENT_LOCATION_COLUMN_NAME, INTEGER_NOT_NULL),
                Pair.create(SKIN_TEMPERATURE_BASELINE_COLUMN_NAME, REAL));
    }

    @NonNull
    List<Pair<String, String>> getSeriesTableColumnInfo() {
        return List.of(
                Pair.create(PARENT_KEY_COLUMN_NAME, INTEGER),
                Pair.create(SKIN_TEMPERATURE_DELTA_COLUMN_NAME, REAL_NOT_NULL),
                Pair.create(EPOCH_MILLIS_COLUMN_NAME, INTEGER_NOT_NULL));
    }

    void populateSampleTo(
            @NonNull ContentValues contentValues,
            @NonNull SkinTemperatureRecordInternal.SkinTemperatureDeltaSample sample) {
        contentValues.put(SKIN_TEMPERATURE_DELTA_COLUMN_NAME, sample.mTemperatureDeltaInCelsius());
        contentValues.put(EPOCH_MILLIS_COLUMN_NAME, sample.mEpochMillis());
    }

    /** Adds the skin temperature tables. */
    public void applySkinTemperatureUpgrade(@NonNull SQLiteDatabase db) {
        createTable(db, getCreateTableRequest());
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    AggregateParams getAggregateParams(AggregationType<?> aggregateRequest) {
        switch (aggregateRequest.getAggregationTypeIdentifier()) {
            case SKIN_TEMPERATURE_RECORD_DELTA_AVG:
            case SKIN_TEMPERATURE_RECORD_DELTA_MIN:
            case SKIN_TEMPERATURE_RECORD_DELTA_MAX:
                return new AggregateParams(
                                SERIES_TABLE_NAME,
                                // Aggregation on the delta column.
                                List.of(SKIN_TEMPERATURE_DELTA_COLUMN_NAME))
                        .setJoin(
                                new SqlJoin(
                                        SERIES_TABLE_NAME,
                                        TABLE_NAME,
                                        PARENT_KEY_COLUMN_NAME,
                                        PRIMARY_COLUMN_NAME));
            default:
                return null;
        }
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @Override
    public AggregateResult<?> getAggregateResult(
            Cursor results, AggregationType<?> aggregationType) {
        switch (aggregationType.getAggregationTypeIdentifier()) {
            case SKIN_TEMPERATURE_RECORD_DELTA_AVG:
            case SKIN_TEMPERATURE_RECORD_DELTA_MIN:
            case SKIN_TEMPERATURE_RECORD_DELTA_MAX:
                return new AggregateResult<>(
                                results.getDouble(
                                        results.getColumnIndex(SKIN_TEMPERATURE_DELTA_COLUMN_NAME)))
                        .setZoneOffset(getZoneOffset(results));
            default:
                return null;
        }
    }
}
