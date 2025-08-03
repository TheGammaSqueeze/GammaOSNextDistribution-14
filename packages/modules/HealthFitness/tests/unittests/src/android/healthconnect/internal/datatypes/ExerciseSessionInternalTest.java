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

package android.healthconnect.internal.datatypes;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.datatypes.ExerciseLap;
import android.health.connect.datatypes.ExerciseRoute;
import android.health.connect.datatypes.ExerciseSegment;
import android.health.connect.datatypes.ExerciseSessionRecord;
import android.health.connect.internal.datatypes.ExerciseLapInternal;
import android.health.connect.internal.datatypes.ExerciseSegmentInternal;
import android.health.connect.internal.datatypes.ExerciseSessionRecordInternal;
import android.os.Parcel;

import org.junit.Test;

import java.util.List;

public class ExerciseSessionInternalTest {
    @Test
    public void testSessionConvertToExternal_convertToExternal_fieldsIsEqual() {
        ExerciseSessionRecordInternal session = TestUtils.buildExerciseSessionInternal();
        ExerciseSessionRecord externalSession = session.toExternalRecord();
        assertFieldsAreEqual(externalSession, session);
    }

    @Test
    public void testSessionConvertToExternal_convertToExternalNoExtra_fieldsIsEqual() {
        ExerciseSessionRecordInternal session =
                TestUtils.buildExerciseSessionInternalNoExtraFields();
        ExerciseSessionRecord externalSession = session.toExternalRecord();
        assertFieldsAreEqual(externalSession, session);
    }

    @Test
    public void testSessionWriteToParcel_populateToParcelAndFrom_restoredFieldsAreIdentical() {
        ExerciseSessionRecordInternal session = TestUtils.buildExerciseSessionInternal();
        ExerciseSessionRecordInternal restoredSession = writeAndRestoreFromParcel(session);

        assertFieldsAreEqual(session, restoredSession);
    }

    @Test
    public void
            testSessionWriteToParcel_populateToParcelAndFromNoExtra_restoredFieldsAreIdentical() {
        ExerciseSessionRecordInternal session =
                TestUtils.buildExerciseSessionInternalNoExtraFields();
        ExerciseSessionRecordInternal restoredSession = writeAndRestoreFromParcel(session);

        assertFieldsAreEqual(session, restoredSession);
    }

    @Test
    public void testSessionHashCode_getHashCode_noExceptionsAndNotNull() {
        assertThat(TestUtils.buildExerciseSessionInternal().toExternalRecord().hashCode())
                .isNotNull();
    }

    private ExerciseSessionRecordInternal writeAndRestoreFromParcel(
            ExerciseSessionRecordInternal session) {
        Parcel parcel = Parcel.obtain();
        session.writeToParcel(parcel);
        parcel.setDataPosition(0);
        ExerciseSessionRecordInternal restoredSession = new ExerciseSessionRecordInternal();
        restoredSession.populateUsing(parcel);
        parcel.recycle();
        return restoredSession;
    }

    private void assertFieldsAreEqual(
            ExerciseSessionRecord external, ExerciseSessionRecordInternal internal) {
        assertThat(internal.getStartTimeInMillis())
                .isEqualTo(external.getStartTime().toEpochMilli());
        assertThat(internal.getEndTimeInMillis()).isEqualTo(external.getEndTime().toEpochMilli());
        assertThat(internal.getStartZoneOffsetInSeconds())
                .isEqualTo(external.getStartZoneOffset().getTotalSeconds());
        assertThat(internal.getEndZoneOffsetInSeconds())
                .isEqualTo(external.getEndZoneOffset().getTotalSeconds());
        if (internal.getRoute() == null) {
            assertThat(external.getRoute()).isNull();
        } else {
            ExerciseRoute convertedRoute = internal.getRoute().toExternalRoute();
            assertThat(external.getRoute().getRouteLocations())
                    .isEqualTo(convertedRoute.getRouteLocations());
            assertThat(external.getRoute()).isEqualTo(convertedRoute);
        }
        TestUtils.assertCharSequencesEqualToStringWithNull(
                internal.getTitle(), external.getTitle());
        TestUtils.assertCharSequencesEqualToStringWithNull(
                internal.getNotes(), external.getNotes());
        assertLapsAreEqual(internal.getLaps(), external.getLaps());
        assertSegmentsAreEqual(internal.getSegments(), external.getSegments());
    }

    private void assertLapsAreEqual(
            List<ExerciseLapInternal> internalLaps, List<ExerciseLap> externalLaps) {
        if (internalLaps == null) {
            assertThat(externalLaps).isEmpty();
            return;
        }

        assertThat(internalLaps.size()).isEqualTo(externalLaps.size());
        for (int i = 0; i < internalLaps.size(); i++) {
            ExerciseLapInternal internalLap = internalLaps.get(i);
            ExerciseLap externalLap = externalLaps.get(i);
            assertThat(internalLap.getStartTime())
                    .isEqualTo(externalLap.getStartTime().toEpochMilli());
            assertThat(internalLap.getEndTime()).isEqualTo(externalLap.getEndTime().toEpochMilli());
            assertThat(internalLap.getLength()).isEqualTo(externalLap.getLength().getInMeters());
        }
    }

    private void assertSegmentsAreEqual(
            List<ExerciseSegmentInternal> internalSegments,
            List<ExerciseSegment> externalSegments) {
        if (internalSegments == null) {
            assertThat(externalSegments).isEmpty();
            return;
        }

        assertThat(internalSegments.size()).isEqualTo(externalSegments.size());
        for (int i = 0; i < internalSegments.size(); i++) {
            ExerciseSegmentInternal internalSegment = internalSegments.get(i);
            ExerciseSegment externalSegment = externalSegments.get(i);
            assertThat(internalSegment.getStartTime())
                    .isEqualTo(externalSegment.getStartTime().toEpochMilli());
            assertThat(internalSegment.getEndTime())
                    .isEqualTo(externalSegment.getEndTime().toEpochMilli());
            assertThat(internalSegment.getSegmentType())
                    .isEqualTo(externalSegment.getSegmentType());
            assertThat(internalSegment.getRepetitionsCount())
                    .isEqualTo(externalSegment.getRepetitionsCount());
        }
    }

    private void assertFieldsAreEqual(
            ExerciseSessionRecordInternal internal, ExerciseSessionRecordInternal internal2) {
        assertThat(internal.getStartTimeInMillis()).isEqualTo(internal2.getStartTimeInMillis());
        assertThat(internal.getEndTimeInMillis()).isEqualTo(internal2.getEndTimeInMillis());
        assertThat(internal.getStartZoneOffsetInSeconds())
                .isEqualTo(internal2.getStartZoneOffsetInSeconds());
        assertThat(internal.getEndZoneOffsetInSeconds())
                .isEqualTo(internal2.getEndZoneOffsetInSeconds());
        assertThat(internal.getRoute()).isEqualTo(internal2.getRoute());
        assertThat(internal.getExerciseType()).isEqualTo(internal2.getExerciseType());
        TestUtils.assertCharSequencesEqualToStringWithNull(
                internal.getNotes(), internal2.getNotes());
        TestUtils.assertCharSequencesEqualToStringWithNull(
                internal.getTitle(), internal2.getTitle());
    }
}
