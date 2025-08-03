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

import static android.healthconnect.cts.route.ExerciseRouteTestHelper.ROUTE_WRITER_APP;
import static android.healthconnect.cts.route.ExerciseRouteTestHelper.assertCorrectHealthPermissions;
import static android.healthconnect.cts.route.ExerciseRouteTestHelper.getExerciseSessionWithRoute;
import static android.healthconnect.cts.utils.DataFactory.getEmptyMetadata;
import static android.healthconnect.cts.utils.PermissionHelper.READ_EXERCISE_ROUTE_PERMISSION;
import static android.healthconnect.cts.utils.TestUtils.deleteAllStagedRemoteData;
import static android.healthconnect.cts.utils.TestUtils.getChangeLogToken;
import static android.healthconnect.cts.utils.TestUtils.getChangeLogs;
import static android.healthconnect.cts.utils.TestUtils.readRecords;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

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

public class ReadExerciseRoutePermissionTest {

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Before
    public void setUp() throws Exception {
        assertCorrectHealthPermissions();
    }

    @After
    public void tearDown() throws InterruptedException {
        deleteAllStagedRemoteData();
    }

    @Test
    public void readRecords_usingFilters_canAccessOtherAppRoute() throws Exception {
        ExerciseSessionRecord sessionWithRoute = getExerciseSessionWithRoute(getEmptyMetadata());
        ROUTE_WRITER_APP.insertRecords(sessionWithRoute);

        List<ExerciseSessionRecord> records =
                runWithShellPermissionIdentity(
                        () ->
                                readRecords(
                                        new ReadRecordsRequestUsingFilters.Builder<>(
                                                        ExerciseSessionRecord.class)
                                                .build()),
                        READ_EXERCISE_ROUTE_PERMISSION);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isTrue();
        assertThat(records.get(0).getRoute()).isEqualTo(sessionWithRoute.getRoute());
    }

    @Test
    public void readRecords_usingIds_canAccessOtherAppRoute() throws Exception {
        ExerciseSessionRecord otherAppSession = getExerciseSessionWithRoute(getEmptyMetadata());
        String sessionId = ROUTE_WRITER_APP.insertRecords(otherAppSession).get(0);

        List<ExerciseSessionRecord> records =
                runWithShellPermissionIdentity(
                        () ->
                                readRecords(
                                        new ReadRecordsRequestUsingIds.Builder<>(
                                                        ExerciseSessionRecord.class)
                                                .addId(sessionId)
                                                .build()),
                        READ_EXERCISE_ROUTE_PERMISSION);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isTrue();
        assertThat(records.get(0).getRoute()).isEqualTo(otherAppSession.getRoute());
    }

    @Test
    public void getChangelogs_canAccessOtherAppRoute() throws Exception {
        String token =
                getChangeLogToken(
                                new ChangeLogTokenRequest.Builder()
                                        .addRecordType(ExerciseSessionRecord.class)
                                        .build())
                        .getToken();
        ExerciseSessionRecord otherAppSession = getExerciseSessionWithRoute(getEmptyMetadata());
        ROUTE_WRITER_APP.insertRecords(otherAppSession);

        ChangeLogsResponse response =
                runWithShellPermissionIdentity(
                        () -> getChangeLogs(new ChangeLogsRequest.Builder(token).build()),
                        READ_EXERCISE_ROUTE_PERMISSION);

        List<ExerciseSessionRecord> records =
                response.getUpsertedRecords().stream()
                        .map(ExerciseSessionRecord.class::cast)
                        .toList();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isTrue();
        assertThat(records.get(0).getRoute()).isEqualTo(otherAppSession.getRoute());
    }
}
