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

package com.android.server.healthconnect.storage.datatypehelpers;

import static com.android.server.healthconnect.storage.datatypehelpers.SeriesRecordHelper.PARENT_KEY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.REAL_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorDouble;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorLong;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.internal.datatypes.ExerciseRouteInternal;
import android.util.Pair;

import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class ExerciseRouteRecordHelper {
    static final String EXERCISE_ROUTE_RECORD_TABLE_NAME = "exercise_route_table";

    // Route locations columns names
    static final String ROUTE_LOCATION_TIME_IN_MILLIS_COLUMN_NAME = "timestamp_millis";
    static final String ROUTE_LOCATION_LATITUDE_COLUMN_NAME = "latitude";
    static final String ROUTE_LOCATION_LONGITUDE_COLUMN_NAME = "longitude";
    static final String ROUTE_LOCATION_VERTICAL_ACCURACY_COLUMN_NAME = "vertical_accuracy";
    static final String ROUTE_LOCATION_HORIZONTAL_ACCURACY_COLUMN_NAME = "horizontal_accuracy";
    static final String ROUTE_LOCATION_ALTITUDE_COLUMN_NAME = "altitude";

    static ExerciseRouteInternal.LocationInternal populateLocation(@NonNull Cursor cursor) {
        return new ExerciseRouteInternal.LocationInternal()
                .setTime(getCursorLong(cursor, ROUTE_LOCATION_TIME_IN_MILLIS_COLUMN_NAME))
                .setLatitude(getCursorDouble(cursor, ROUTE_LOCATION_LATITUDE_COLUMN_NAME))
                .setLongitude(getCursorDouble(cursor, ROUTE_LOCATION_LONGITUDE_COLUMN_NAME))
                .setHorizontalAccuracy(
                        getCursorDouble(cursor, ROUTE_LOCATION_HORIZONTAL_ACCURACY_COLUMN_NAME))
                .setVerticalAccuracy(
                        getCursorDouble(cursor, ROUTE_LOCATION_VERTICAL_ACCURACY_COLUMN_NAME))
                .setAltitude(getCursorDouble(cursor, ROUTE_LOCATION_ALTITUDE_COLUMN_NAME));
    }

    static CreateTableRequest getCreateRouteTableRequest(String parentTableName) {
        return new CreateTableRequest(
                        EXERCISE_ROUTE_RECORD_TABLE_NAME,
                        ExerciseRouteRecordHelper.getRouteTableColumnInfo())
                .addForeignKey(
                        parentTableName,
                        Collections.singletonList(PARENT_KEY_COLUMN_NAME),
                        Collections.singletonList(RecordHelper.PRIMARY_COLUMN_NAME));
    }

    static List<UpsertTableRequest> getRouteUpsertRequests(ExerciseRouteInternal route) {
        List<UpsertTableRequest> requests = new ArrayList<>(route.getRouteLocations().size());
        route.getRouteLocations()
                .forEach(
                        (sample -> {
                            ContentValues contentValues = new ContentValues();
                            populateRouteLocationTo(contentValues, sample);
                            requests.add(
                                    new UpsertTableRequest(
                                                    EXERCISE_ROUTE_RECORD_TABLE_NAME, contentValues)
                                            .setParentColumnForChildTables(PARENT_KEY_COLUMN_NAME));
                        }));
        return requests;
    }

    private static List<Pair<String, String>> getRouteTableColumnInfo() {
        List<Pair<String, String>> columnInfo = new ArrayList<>();
        columnInfo.add(new Pair<>(PARENT_KEY_COLUMN_NAME, INTEGER_NOT_NULL));
        columnInfo.add(new Pair<>(ROUTE_LOCATION_TIME_IN_MILLIS_COLUMN_NAME, INTEGER_NOT_NULL));
        columnInfo.add(new Pair<>(ROUTE_LOCATION_LONGITUDE_COLUMN_NAME, REAL_NOT_NULL));
        columnInfo.add(new Pair<>(ROUTE_LOCATION_LATITUDE_COLUMN_NAME, REAL_NOT_NULL));
        columnInfo.add(new Pair<>(ROUTE_LOCATION_HORIZONTAL_ACCURACY_COLUMN_NAME, REAL_NOT_NULL));
        columnInfo.add(new Pair<>(ROUTE_LOCATION_VERTICAL_ACCURACY_COLUMN_NAME, REAL_NOT_NULL));
        columnInfo.add(new Pair<>(ROUTE_LOCATION_ALTITUDE_COLUMN_NAME, REAL_NOT_NULL));
        return columnInfo;
    }

    private static void populateRouteLocationTo(
            ContentValues contentValues, ExerciseRouteInternal.LocationInternal location) {
        contentValues.put(ROUTE_LOCATION_TIME_IN_MILLIS_COLUMN_NAME, location.getTime());
        contentValues.put(ROUTE_LOCATION_LONGITUDE_COLUMN_NAME, location.getLongitude());
        contentValues.put(ROUTE_LOCATION_LATITUDE_COLUMN_NAME, location.getLatitude());
        contentValues.put(
                ROUTE_LOCATION_HORIZONTAL_ACCURACY_COLUMN_NAME, location.getHorizontalAccuracy());
        contentValues.put(
                ROUTE_LOCATION_VERTICAL_ACCURACY_COLUMN_NAME, location.getVerticalAccuracy());
        contentValues.put(ROUTE_LOCATION_ALTITUDE_COLUMN_NAME, location.getAltitude());
    }
}
