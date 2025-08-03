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

package android.healthconnect.cts.route;

import static android.health.connect.HealthPermissions.WRITE_EXERCISE_ROUTE;
import static android.healthconnect.cts.route.ExerciseRouteTestHelper.ROUTES_READER_WRITER_APP;
import static android.healthconnect.cts.route.ExerciseRouteTestHelper.ROUTE_WRITER_APP;
import static android.healthconnect.cts.route.ExerciseRouteTestHelper.assertCorrectHealthPermissions;
import static android.healthconnect.cts.route.ExerciseRouteTestHelper.getExerciseSessionWithAnotherRoute;
import static android.healthconnect.cts.route.ExerciseRouteTestHelper.getExerciseSessionWithRoute;
import static android.healthconnect.cts.route.ExerciseRouteTestHelper.getExerciseSessionWithoutRoute;
import static android.healthconnect.cts.route.ExerciseRouteTestHelper.readAllExerciseSessionRecordsPrivileged;
import static android.healthconnect.cts.utils.DataFactory.getEmptyMetadata;
import static android.healthconnect.cts.utils.DataFactory.getMetadataForClientId;
import static android.healthconnect.cts.utils.DataFactory.getMetadataForId;
import static android.healthconnect.cts.utils.PermissionHelper.READ_EXERCISE_ROUTES;
import static android.healthconnect.cts.utils.PermissionHelper.runWithRevokedPermissions;
import static android.healthconnect.cts.utils.TestUtils.connectAppsWithGrantedPermissions;
import static android.healthconnect.cts.utils.TestUtils.deleteAllStagedRemoteData;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.HealthConnectException;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.changelog.ChangeLogTokenRequest;
import android.health.connect.changelog.ChangeLogsRequest;
import android.health.connect.changelog.ChangeLogsResponse;
import android.health.connect.datatypes.ExerciseSessionRecord;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public class ExerciseRouteNoReadWritePermissionTest {

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Before
    public void setUp() throws Exception {
        assertCorrectHealthPermissions();
        connectAppsWithGrantedPermissions();
    }

    @After
    public void tearDown() throws InterruptedException {
        deleteAllStagedRemoteData();
    }

    @Test
    public void insertRecords_canNotInsertRoute() {
        ExerciseSessionRecord otherAppSession = getExerciseSessionWithRoute(getEmptyMetadata());

        HealthConnectException e =
                assertThrows(
                        HealthConnectException.class,
                        () ->
                                runWithRevokedPermissions(
                                        () -> ROUTE_WRITER_APP.insertRecords(otherAppSession),
                                        ROUTE_WRITER_APP.getPackageName(),
                                        WRITE_EXERCISE_ROUTE));

        assertThat(e.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
    }

    @Test
    public void insertRecords_withSameClientId_withoutRoute_routeDoesNotGetDeleted()
            throws Exception {
        ExerciseSessionRecord sessionWithRoute =
                getExerciseSessionWithRoute(getMetadataForClientId("client id"));
        ROUTE_WRITER_APP.insertRecords(sessionWithRoute);
        ExerciseSessionRecord updatedSessionWithoutRoute =
                getExerciseSessionWithoutRoute(getMetadataForClientId("client id"));

        runWithRevokedPermissions(
                () -> ROUTE_WRITER_APP.insertRecords(updatedSessionWithoutRoute),
                ROUTE_WRITER_APP.getPackageName(),
                WRITE_EXERCISE_ROUTE);

        List<ExerciseSessionRecord> records = readAllExerciseSessionRecordsPrivileged();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isTrue();
        assertThat(records.get(0).getRoute()).isEqualTo(sessionWithRoute.getRoute());
    }

    @Test
    public void insertRecords_withSameClientId_updatedRoute_throws_routeDoesNotGetUpdated()
            throws Exception {
        ExerciseSessionRecord sessionWithRoute =
                getExerciseSessionWithRoute(getMetadataForClientId("client id"));
        ROUTE_WRITER_APP.insertRecords(sessionWithRoute);
        ExerciseSessionRecord sessionWithUpdatedRoute =
                getExerciseSessionWithAnotherRoute(getMetadataForClientId("client id"));

        HealthConnectException e =
                assertThrows(
                        HealthConnectException.class,
                        () ->
                                runWithRevokedPermissions(
                                        () ->
                                                ROUTE_WRITER_APP.insertRecords(
                                                        sessionWithUpdatedRoute),
                                        ROUTE_WRITER_APP.getPackageName(),
                                        WRITE_EXERCISE_ROUTE));

        assertThat(e.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
        List<ExerciseSessionRecord> records = readAllExerciseSessionRecordsPrivileged();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isTrue();
        assertThat(records.get(0).getRoute()).isEqualTo(sessionWithRoute.getRoute());
    }

    @Test
    public void insertRecords_withSameClientId_addedRoute_throws_routeDoesNotGetAdded()
            throws Exception {
        ExerciseSessionRecord sessionWithoutRoute =
                getExerciseSessionWithoutRoute(getMetadataForClientId("client id"));
        ROUTE_WRITER_APP.insertRecords(sessionWithoutRoute);
        ExerciseSessionRecord sessionWithAddedRoute =
                getExerciseSessionWithRoute(getMetadataForClientId("client id"));

        HealthConnectException e =
                assertThrows(
                        HealthConnectException.class,
                        () ->
                                runWithRevokedPermissions(
                                        () -> ROUTE_WRITER_APP.insertRecords(sessionWithAddedRoute),
                                        ROUTE_WRITER_APP.getPackageName(),
                                        WRITE_EXERCISE_ROUTE));

        assertThat(e.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
        List<ExerciseSessionRecord> records = readAllExerciseSessionRecordsPrivileged();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isFalse();
        assertThat(records.get(0).getRoute()).isNull();
    }

    @Test
    public void updateRecords_withoutRoute_routeDoesNotGetDeleted() throws Exception {
        ExerciseSessionRecord sessionWithRoute = getExerciseSessionWithRoute(getEmptyMetadata());
        String sessionId = ROUTE_WRITER_APP.insertRecords(sessionWithRoute).get(0);
        ExerciseSessionRecord updatedSessionWithoutRoute =
                getExerciseSessionWithoutRoute(getMetadataForId(sessionId));

        runWithRevokedPermissions(
                ROUTE_WRITER_APP.getPackageName(),
                WRITE_EXERCISE_ROUTE,
                () -> ROUTE_WRITER_APP.updateRecords(updatedSessionWithoutRoute));

        List<ExerciseSessionRecord> records = readAllExerciseSessionRecordsPrivileged();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isTrue();
        assertThat(records.get(0).getRoute()).isEqualTo(sessionWithRoute.getRoute());
    }

    @Test
    public void updateRecords_withUpdatedRoute_throws_routeDoesNotGetUpdated() throws Exception {
        ExerciseSessionRecord sessionWithRoute = getExerciseSessionWithRoute(getEmptyMetadata());
        String sessionId = ROUTE_WRITER_APP.insertRecords(sessionWithRoute).get(0);
        ExerciseSessionRecord sessionWithUpdatedRoute =
                getExerciseSessionWithAnotherRoute(getMetadataForId(sessionId));

        HealthConnectException e =
                assertThrows(
                        HealthConnectException.class,
                        () ->
                                runWithRevokedPermissions(
                                        ROUTE_WRITER_APP.getPackageName(),
                                        WRITE_EXERCISE_ROUTE,
                                        () ->
                                                ROUTE_WRITER_APP.updateRecords(
                                                        sessionWithUpdatedRoute)));

        assertThat(e.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
        List<ExerciseSessionRecord> records = readAllExerciseSessionRecordsPrivileged();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isTrue();
        assertThat(records.get(0).getRoute()).isEqualTo(sessionWithRoute.getRoute());
    }

    @Test
    public void updateRecords_withAddedRoute_throws_routeDoesNotGetAdded() throws Exception {
        ExerciseSessionRecord sessionWithoutRoute =
                getExerciseSessionWithoutRoute(getEmptyMetadata());
        String sessionId = ROUTE_WRITER_APP.insertRecords(sessionWithoutRoute).get(0);
        ExerciseSessionRecord sessionWithAddedRoute =
                getExerciseSessionWithRoute(getMetadataForId(sessionId));

        HealthConnectException e =
                assertThrows(
                        HealthConnectException.class,
                        () ->
                                runWithRevokedPermissions(
                                        ROUTE_WRITER_APP.getPackageName(),
                                        WRITE_EXERCISE_ROUTE,
                                        () ->
                                                ROUTE_WRITER_APP.updateRecords(
                                                        sessionWithAddedRoute)));

        assertThat(e.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
        List<ExerciseSessionRecord> records = readAllExerciseSessionRecordsPrivileged();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isFalse();
        assertThat(records.get(0).getRoute()).isNull();
    }

    @Test
    public void readRecords_usingFilters_canAccessOwnRoute() throws Exception {
        ExerciseSessionRecord sessionWithRoute = getExerciseSessionWithRoute(getEmptyMetadata());
        ROUTES_READER_WRITER_APP.insertRecords(sessionWithRoute);

        List<ExerciseSessionRecord> records =
                runWithRevokedPermissions(
                        () ->
                                ROUTES_READER_WRITER_APP.readRecords(
                                        new ReadRecordsRequestUsingFilters.Builder<>(
                                                        ExerciseSessionRecord.class)
                                                .build()),
                        ROUTES_READER_WRITER_APP.getPackageName(),
                        READ_EXERCISE_ROUTES,
                        WRITE_EXERCISE_ROUTE);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isTrue();
        assertThat(records.get(0).getRoute()).isNull();
    }

    @Test
    public void readRecords_usingIds_canAccessOwnRoute() throws Exception {
        ExerciseSessionRecord sessionWithRoute = getExerciseSessionWithRoute(getEmptyMetadata());
        String sessionId = ROUTES_READER_WRITER_APP.insertRecords(sessionWithRoute).get(0);

        List<ExerciseSessionRecord> records =
                runWithRevokedPermissions(
                        () ->
                                ROUTES_READER_WRITER_APP.readRecords(
                                        new ReadRecordsRequestUsingIds.Builder<>(
                                                        ExerciseSessionRecord.class)
                                                .addId(sessionId)
                                                .build()),
                        ROUTES_READER_WRITER_APP.getPackageName(),
                        READ_EXERCISE_ROUTES,
                        WRITE_EXERCISE_ROUTE);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isTrue();
        assertThat(records.get(0).getRoute()).isNull();
    }

    @Test
    public void getChangelogs_canAccessOwnRoute() throws Exception {
        String token =
                ROUTES_READER_WRITER_APP.getChangeLogToken(
                        new ChangeLogTokenRequest.Builder()
                                .addRecordType(ExerciseSessionRecord.class)
                                .build());
        ExerciseSessionRecord sessionWithRoute = getExerciseSessionWithRoute(getEmptyMetadata());
        ROUTES_READER_WRITER_APP.insertRecords(sessionWithRoute);

        ChangeLogsResponse response =
                runWithRevokedPermissions(
                        () ->
                                ROUTES_READER_WRITER_APP.getChangeLogs(
                                        new ChangeLogsRequest.Builder(token).build()),
                        ROUTES_READER_WRITER_APP.getPackageName(),
                        READ_EXERCISE_ROUTES,
                        WRITE_EXERCISE_ROUTE);

        List<ExerciseSessionRecord> records =
                response.getUpsertedRecords().stream()
                        .map(ExerciseSessionRecord.class::cast)
                        .toList();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isTrue();
        assertThat(records.get(0).getRoute()).isNull();
    }
}
