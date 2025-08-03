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

package android.healthconnect.internal.datatypes;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.datatypes.SleepSessionRecord;
import android.health.connect.internal.datatypes.SleepSessionRecordInternal;
import android.health.connect.internal.datatypes.SleepStageInternal;
import android.os.Parcel;

import org.junit.Test;

import java.util.List;

public class SleepSessionInternalTest {
    @Test
    public void testSessionConvertToExternal_convertToExternal_fieldsIsEqual() {
        SleepSessionRecordInternal session = TestUtils.buildSleepSessionInternal();
        SleepSessionRecord externalSession = session.toExternalRecord();
        assertFieldsAreEqual(externalSession, session);
    }

    @Test
    public void testSessionConvertToExternal_convertToExternalNoExtra_fieldsIsEqual() {
        SleepSessionRecordInternal session = TestUtils.buildSleepSessionInternalNoExtraFields();
        SleepSessionRecord externalSession = session.toExternalRecord();
        assertFieldsAreEqual(externalSession, session);
    }

    @Test
    public void testSessionWriteToParcel_populateToParcelAndFrom_restoredFieldsAreIdentical() {
        SleepSessionRecordInternal session = TestUtils.buildSleepSessionInternal();
        SleepSessionRecordInternal restoredSession = writeAndRestoreFromParcel(session);
        assertFieldsAreEqual(session, restoredSession);
    }

    @Test
    public void
            testSessionWriteToParcel_populateToParcelAndFromNoExtra_restoredFieldsAreIdentical() {
        SleepSessionRecordInternal session = TestUtils.buildSleepSessionInternalNoExtraFields();
        SleepSessionRecordInternal restoredSession = writeAndRestoreFromParcel(session);
        assertFieldsAreEqual(session, restoredSession);
    }

    private SleepSessionRecordInternal writeAndRestoreFromParcel(
            SleepSessionRecordInternal session) {
        Parcel parcel = Parcel.obtain();
        session.writeToParcel(parcel);
        parcel.setDataPosition(0);
        SleepSessionRecordInternal restoredSession = new SleepSessionRecordInternal();
        restoredSession.populateUsing(parcel);
        parcel.recycle();
        return restoredSession;
    }

    private void assertFieldsAreEqual(
            SleepSessionRecord external, SleepSessionRecordInternal internal) {
        assertThat(internal.getStartTimeInMillis())
                .isEqualTo(external.getStartTime().toEpochMilli());
        assertThat(internal.getEndTimeInMillis()).isEqualTo(external.getEndTime().toEpochMilli());
        assertThat(internal.getStartZoneOffsetInSeconds())
                .isEqualTo(external.getStartZoneOffset().getTotalSeconds());
        assertThat(internal.getEndZoneOffsetInSeconds())
                .isEqualTo(external.getEndZoneOffset().getTotalSeconds());

        TestUtils.assertCharSequencesEqualToStringWithNull(
                internal.getTitle(), external.getTitle());
        TestUtils.assertCharSequencesEqualToStringWithNull(
                internal.getNotes(), external.getNotes());
        assertStagesAreEqual(internal.getSleepStages(), external.getStages());
    }

    private void assertFieldsAreEqual(
            SleepSessionRecordInternal internal, SleepSessionRecordInternal internal2) {
        assertThat(internal.getStartTimeInMillis()).isEqualTo(internal2.getStartTimeInMillis());
        assertThat(internal.getEndTimeInMillis()).isEqualTo(internal2.getEndTimeInMillis());
        assertThat(internal.getStartZoneOffsetInSeconds())
                .isEqualTo(internal2.getStartZoneOffsetInSeconds());
        assertThat(internal.getEndZoneOffsetInSeconds())
                .isEqualTo(internal2.getEndZoneOffsetInSeconds());
        TestUtils.assertCharSequencesEqualToStringWithNull(
                internal.getTitle(), internal2.getTitle());
        TestUtils.assertCharSequencesEqualToStringWithNull(
                internal.getNotes(), internal2.getNotes());
        assertStagesAreEqualInternal(internal.getSleepStages(), internal2.getSleepStages());
    }

    private void assertStagesAreEqual(
            List<SleepStageInternal> internal, List<SleepSessionRecord.Stage> external) {
        if (internal == null) {
            assertThat(external).isEmpty();
            return;
        }

        assertThat(internal.size()).isEqualTo(external.size());
        for (int i = 0; i < internal.size(); i++) {
            SleepStageInternal internalStage = internal.get(i);
            SleepSessionRecord.Stage externalStage = external.get(i);
            assertThat(internalStage.getStartTime())
                    .isEqualTo(externalStage.getStartTime().toEpochMilli());
            assertThat(internalStage.getEndTime())
                    .isEqualTo(externalStage.getEndTime().toEpochMilli());
            assertThat(internalStage.getStageType()).isEqualTo(externalStage.getType());
        }
    }

    private void assertStagesAreEqualInternal(
            List<SleepStageInternal> internal, List<SleepStageInternal> internal2) {
        if (internal == null) {
            assertThat(internal2).isNull();
            return;
        }

        assertThat(internal.size()).isEqualTo(internal2.size());
        for (int i = 0; i < internal.size(); i++) {
            SleepStageInternal internalStage = internal.get(i);
            SleepStageInternal internalStage2 = internal2.get(i);
            assertThat(internalStage).isEqualTo(internalStage2);
        }
    }
}
