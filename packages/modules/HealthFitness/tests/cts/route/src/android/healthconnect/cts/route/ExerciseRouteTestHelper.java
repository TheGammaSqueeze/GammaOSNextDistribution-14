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

package android.healthconnect.cts.route;

import static android.health.connect.HealthPermissions.READ_EXERCISE;
import static android.health.connect.HealthPermissions.WRITE_EXERCISE;
import static android.health.connect.HealthPermissions.WRITE_EXERCISE_ROUTE;
import static android.healthconnect.cts.utils.PermissionHelper.READ_EXERCISE_ROUTES;
import static android.healthconnect.cts.utils.PermissionHelper.READ_EXERCISE_ROUTE_PERMISSION;
import static android.healthconnect.cts.utils.PermissionHelper.READ_HEALTH_DATA_IN_BACKGROUND;
import static android.healthconnect.cts.utils.PermissionHelper.getGrantedHealthPermissions;
import static android.healthconnect.cts.utils.TestUtils.readRecords;
import static android.healthconnect.cts.utils.TestUtils.yesterdayAt;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static java.time.Duration.ofMinutes;

import android.content.Context;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.datatypes.ExerciseRoute;
import android.health.connect.datatypes.ExerciseSessionRecord;
import android.health.connect.datatypes.ExerciseSessionType;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.units.Length;
import android.healthconnect.cts.lib.TestAppProxy;

import androidx.test.core.app.ApplicationProvider;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

final class ExerciseRouteTestHelper {

    static final TestAppProxy ROUTE_WRITER_APP =
            TestAppProxy.forPackageName("android.healthconnect.cts.route.testapp.writer");

    static final TestAppProxy ROUTES_READER_WRITER_APP =
            TestAppProxy.forPackageName("android.healthconnect.cts.route.testapp.readerWriter");

    static final Instant START_TIME = yesterdayAt("11:00").truncatedTo(ChronoUnit.MILLIS);

    /**
     * Make sure test app permissions didn't change between the runs.
     *
     * <p>Fail quickly and explicitly if the permissions were not cleaned up by a previous test
     * case. Also because permissions in CTS tests are auto granted it's easy to misconfigure
     * permissions without noticing it, so this works as a double check.
     */
    static void assertCorrectHealthPermissions() {
        Context context = ApplicationProvider.getApplicationContext();
        assertThat(getGrantedHealthPermissions(context.getPackageName()))
                .containsExactly(READ_EXERCISE, WRITE_EXERCISE, WRITE_EXERCISE_ROUTE);
        assertThat(getGrantedHealthPermissions(ROUTE_WRITER_APP.getPackageName()))
                .containsExactly(WRITE_EXERCISE, WRITE_EXERCISE_ROUTE);
        assertThat(getGrantedHealthPermissions(ROUTES_READER_WRITER_APP.getPackageName()))
                .containsExactly(
                        READ_EXERCISE,
                        READ_EXERCISE_ROUTES,
                        READ_HEALTH_DATA_IN_BACKGROUND,
                        WRITE_EXERCISE,
                        WRITE_EXERCISE_ROUTE);
    }

    static ExerciseSessionRecord getExerciseSessionWithRoute(Metadata metadata) {
        return getExerciseSessionWithRoute(metadata, START_TIME);
    }

    static ExerciseSessionRecord getExerciseSessionWithRoute(Metadata metadata, Instant startTime) {
        return getExerciseSession(metadata, startTime, getRoute(startTime));
    }

    static ExerciseSessionRecord getExerciseSessionWithAnotherRoute(Metadata metadata) {
        return getExerciseSession(metadata, START_TIME, getAnotherRoute(START_TIME));
    }

    static ExerciseSessionRecord getExerciseSessionWithoutRoute(Metadata metadata) {
        return getExerciseSession(metadata, START_TIME, null);
    }

    static ExerciseSessionRecord getExerciseSessionWithoutRoute(
            Metadata metadata, Instant startTime) {
        return getExerciseSession(metadata, startTime, null);
    }

    static ExerciseSessionRecord getExerciseSession(
            Metadata metadata, Instant startTime, ExerciseRoute route) {
        return new ExerciseSessionRecord.Builder(
                        metadata,
                        startTime,
                        startTime.plus(ofMinutes(30)),
                        ExerciseSessionType.EXERCISE_SESSION_TYPE_RUNNING)
                .setRoute(route)
                .build();
    }

    static ExerciseRoute getRoute(Instant startTime) {
        return new ExerciseRoute(
                List.of(
                        new ExerciseRoute.Location.Builder(startTime, 52., 48.).build(),
                        new ExerciseRoute.Location.Builder(startTime.plusSeconds(5), -51., -49.)
                                .setAltitude(Length.fromMeters(14))
                                .setHorizontalAccuracy(Length.fromMeters(3))
                                .setVerticalAccuracy(Length.fromMeters(5))
                                .build()));
    }

    static ExerciseRoute getAnotherRoute(Instant startTime) {
        return new ExerciseRoute(
                List.of(
                        new ExerciseRoute.Location.Builder(startTime.plusSeconds(10), -53., -47.)
                                .setAltitude(Length.fromMeters(123))
                                .setHorizontalAccuracy(Length.fromMeters(7))
                                .setVerticalAccuracy(Length.fromMeters(11))
                                .build()));
    }

    static List<ExerciseSessionRecord> readAllExerciseSessionRecordsPrivileged() {
        return runWithShellPermissionIdentity(
                () ->
                        readRecords(
                                new ReadRecordsRequestUsingFilters.Builder<>(
                                                ExerciseSessionRecord.class)
                                        .build()),
                READ_EXERCISE_ROUTE_PERMISSION);
    }

    private ExerciseRouteTestHelper() {}
}
