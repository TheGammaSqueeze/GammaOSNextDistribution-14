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

import static android.healthconnect.cts.route.ExerciseRouteTestHelper.assertCorrectHealthPermissions;
import static android.healthconnect.cts.route.ExerciseRouteTestHelper.getAnotherRoute;
import static android.healthconnect.cts.route.ExerciseRouteTestHelper.getExerciseSession;
import static android.healthconnect.cts.route.ExerciseRouteTestHelper.getExerciseSessionWithAnotherRoute;
import static android.healthconnect.cts.route.ExerciseRouteTestHelper.getExerciseSessionWithRoute;
import static android.healthconnect.cts.route.ExerciseRouteTestHelper.getExerciseSessionWithoutRoute;
import static android.healthconnect.cts.route.ExerciseRouteTestHelper.getRoute;
import static android.healthconnect.cts.route.ExerciseRouteTestHelper.readAllExerciseSessionRecordsPrivileged;
import static android.healthconnect.cts.utils.DataFactory.getEmptyMetadata;
import static android.healthconnect.cts.utils.DataFactory.getMetadataForClientId;
import static android.healthconnect.cts.utils.DataFactory.getMetadataForClientIdAndVersion;
import static android.healthconnect.cts.utils.DataFactory.getMetadataForId;
import static android.healthconnect.cts.utils.TestUtils.connectAppsWithGrantedPermissions;
import static android.healthconnect.cts.utils.TestUtils.deleteAllStagedRemoteData;
import static android.healthconnect.cts.utils.TestUtils.insertRecords;
import static android.healthconnect.cts.utils.TestUtils.updateRecords;
import static android.healthconnect.cts.utils.TestUtils.yesterdayAt;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.datatypes.ExerciseSessionRecord;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ExerciseRouteWriteTest {

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
    public void insertRecords_withRoute_routeGetsInserted() throws Exception {
        ExerciseSessionRecord sessionWithRoute = getExerciseSessionWithRoute(getEmptyMetadata());

        insertRecords(List.of(sessionWithRoute));

        List<ExerciseSessionRecord> records = readAllExerciseSessionRecordsPrivileged();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isTrue();
        assertThat(records.get(0).getRoute()).isEqualTo(sessionWithRoute.getRoute());
    }

    @Test
    public void insertRecords_withClientId_withoutRoute_routeGetsDeleted() throws Exception {
        ExerciseSessionRecord sessionWithRoute =
                getExerciseSessionWithRoute(getMetadataForClientId("client id"));
        insertRecords(List.of(sessionWithRoute));
        ExerciseSessionRecord updatedSessionWithoutRoute =
                getExerciseSessionWithoutRoute(getMetadataForClientId("client id"));

        insertRecords(List.of(updatedSessionWithoutRoute));

        List<ExerciseSessionRecord> records = readAllExerciseSessionRecordsPrivileged();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isFalse();
        assertThat(records.get(0).getRoute()).isNull();
    }

    @Test
    public void insertRecords_withClientId_withUpdatedRoute_routeGetsUpdated() throws Exception {
        ExerciseSessionRecord sessionWithRoute =
                getExerciseSessionWithRoute(getMetadataForClientId("client id"));
        insertRecords(List.of(sessionWithRoute));
        ExerciseSessionRecord sessionWithUpdatedRoute =
                getExerciseSessionWithAnotherRoute(getMetadataForClientId("client id"));

        insertRecords(List.of(sessionWithUpdatedRoute));

        List<ExerciseSessionRecord> records = readAllExerciseSessionRecordsPrivileged();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isTrue();
        assertThat(records.get(0).getRoute()).isEqualTo(sessionWithUpdatedRoute.getRoute());
    }

    @Test
    public void insertRecords_withClientId_withAddedRoute_routeGetsInserted() throws Exception {
        ExerciseSessionRecord sessionWithoutRoute =
                getExerciseSessionWithoutRoute(getMetadataForClientId("client id"));
        insertRecords(List.of(sessionWithoutRoute));
        ExerciseSessionRecord updatedSessionWithRoute =
                getExerciseSessionWithRoute(getMetadataForClientId("client id"));

        insertRecords(List.of(updatedSessionWithRoute));

        List<ExerciseSessionRecord> records = readAllExerciseSessionRecordsPrivileged();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isTrue();
        assertThat(records.get(0).getRoute()).isEqualTo(updatedSessionWithRoute.getRoute());
    }

    @Test
    public void insertRecords_withClientId_withHigherVersionId_routeGetsUpdated() throws Exception {
        ExerciseSessionRecord sessionWithRoute =
                getExerciseSessionWithRoute(getMetadataForClientIdAndVersion("client id", 5));
        insertRecords(List.of(sessionWithRoute));
        ExerciseSessionRecord sessionWithUpdatedRoute =
                getExerciseSessionWithAnotherRoute(
                        getMetadataForClientIdAndVersion("client id", 6));

        insertRecords(List.of(sessionWithUpdatedRoute));

        List<ExerciseSessionRecord> records = readAllExerciseSessionRecordsPrivileged();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isTrue();
        assertThat(records.get(0).getRoute()).isEqualTo(sessionWithUpdatedRoute.getRoute());
    }

    @Test
    public void insertRecords_withClientId_withSameVersionId_routeGetsUpdated() throws Exception {
        ExerciseSessionRecord sessionWithRoute =
                getExerciseSessionWithRoute(getMetadataForClientIdAndVersion("client id", 5));
        insertRecords(List.of(sessionWithRoute));
        ExerciseSessionRecord sessionWithUpdatedRoute =
                getExerciseSessionWithAnotherRoute(
                        getMetadataForClientIdAndVersion("client id", 5));

        insertRecords(List.of(sessionWithUpdatedRoute));

        List<ExerciseSessionRecord> records = readAllExerciseSessionRecordsPrivileged();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isTrue();
        assertThat(records.get(0).getRoute()).isEqualTo(sessionWithUpdatedRoute.getRoute());
    }

    @Test
    public void insertRecords_withClientId_withLowerVersionId_routeDoesNotGetUpdated()
            throws Exception {
        ExerciseSessionRecord sessionWithRoute =
                getExerciseSessionWithRoute(getMetadataForClientIdAndVersion("client id", 5));
        insertRecords(List.of(sessionWithRoute));
        ExerciseSessionRecord sessionWithUpdatedRoute =
                getExerciseSessionWithAnotherRoute(
                        getMetadataForClientIdAndVersion("client id", 4));

        insertRecords(List.of(sessionWithUpdatedRoute));

        List<ExerciseSessionRecord> records = readAllExerciseSessionRecordsPrivileged();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isTrue();
        assertThat(records.get(0).getRoute()).isEqualTo(sessionWithRoute.getRoute());
    }

    @Test
    public void updateRecords_withoutRoute_routeGetsDeleted() throws Exception {
        ExerciseSessionRecord sessionWithRoute = getExerciseSessionWithRoute(getEmptyMetadata());
        String exerciseRecordId =
                insertRecords(List.of(sessionWithRoute)).get(0).getMetadata().getId();
        ExerciseSessionRecord updatedSessionWithoutRoute =
                getExerciseSessionWithoutRoute(getMetadataForId(exerciseRecordId));

        updateRecords(List.of(updatedSessionWithoutRoute));

        List<ExerciseSessionRecord> records = readAllExerciseSessionRecordsPrivileged();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isFalse();
        assertThat(records.get(0).getRoute()).isNull();
    }

    @Test
    public void updateRecords_withRoute_routeGetsInserted() throws Exception {
        ExerciseSessionRecord sessionWithoutRoute =
                getExerciseSessionWithoutRoute(getEmptyMetadata());
        String exerciseRecordId =
                insertRecords(List.of(sessionWithoutRoute)).get(0).getMetadata().getId();
        ExerciseSessionRecord updatedSessionWithRoute =
                getExerciseSessionWithRoute(getMetadataForId(exerciseRecordId));

        updateRecords(List.of(updatedSessionWithRoute));

        List<ExerciseSessionRecord> records = readAllExerciseSessionRecordsPrivileged();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isTrue();
        assertThat(records.get(0).getRoute()).isEqualTo(updatedSessionWithRoute.getRoute());
    }

    @Test
    public void updateRecords_withUpdatedRoute_routeGetsUpdated() throws Exception {
        ExerciseSessionRecord sessionWithRoute = getExerciseSessionWithRoute(getEmptyMetadata());
        String exerciseRecordId =
                insertRecords(List.of(sessionWithRoute)).get(0).getMetadata().getId();
        ExerciseSessionRecord sessionWithUpdatedRoute =
                getExerciseSessionWithAnotherRoute(getMetadataForId(exerciseRecordId));

        updateRecords(List.of(sessionWithUpdatedRoute));

        List<ExerciseSessionRecord> records = readAllExerciseSessionRecordsPrivileged();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isTrue();
        assertThat(records.get(0).getRoute()).isEqualTo(sessionWithUpdatedRoute.getRoute());
    }

    @Test
    public void insertRecords_multipleSessions() throws Exception {
        ExerciseSessionRecord sessionWithRoute =
                getExerciseSession(
                        getEmptyMetadata(), yesterdayAt("09:00"), getRoute(yesterdayAt("09:00")));
        ExerciseSessionRecord sessionWithAnotherRoute =
                getExerciseSession(
                        getEmptyMetadata(),
                        yesterdayAt("10:00"),
                        getAnotherRoute(yesterdayAt("10:00")));
        ExerciseSessionRecord sessionWithoutRoute =
                getExerciseSessionWithoutRoute(getEmptyMetadata(), yesterdayAt("11:00"));

        List<String> ids =
                insertRecords(
                                List.of(
                                        sessionWithRoute,
                                        sessionWithAnotherRoute,
                                        sessionWithoutRoute))
                        .stream()
                        .map(r -> r.getMetadata().getId())
                        .toList();

        String sessionWithRouteId = ids.get(0);
        String sessionWithAnotherRouteId = ids.get(1);
        String sessionWithoutRouteId = ids.get(2);
        List<ExerciseSessionRecord> records = readAllExerciseSessionRecordsPrivileged();
        Map<String, ExerciseSessionRecord> idToRecord =
                records.stream().collect(Collectors.toMap(r -> r.getMetadata().getId(), r -> r));
        assertThat(idToRecord).hasSize(3);
        assertThat(idToRecord.get(sessionWithRouteId).hasRoute()).isTrue();
        assertThat(idToRecord.get(sessionWithRouteId).getRoute())
                .isEqualTo(sessionWithRoute.getRoute());
        assertThat(idToRecord.get(sessionWithAnotherRouteId).hasRoute()).isTrue();
        assertThat(idToRecord.get(sessionWithAnotherRouteId).getRoute())
                .isEqualTo(sessionWithAnotherRoute.getRoute());
        assertThat(idToRecord.get(sessionWithoutRouteId).hasRoute()).isFalse();
        assertThat(idToRecord.get(sessionWithoutRouteId).getRoute()).isNull();
    }
}
