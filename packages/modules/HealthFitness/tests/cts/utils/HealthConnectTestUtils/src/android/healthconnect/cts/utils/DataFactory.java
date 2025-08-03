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

package android.healthconnect.cts.utils;

import static android.health.connect.datatypes.Metadata.RECORDING_METHOD_ACTIVELY_RECORDED;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_BASAL_METABOLIC_RATE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_HEART_RATE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_STEPS;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.health.connect.changelog.ChangeLogTokenRequest;
import android.health.connect.datatypes.BasalMetabolicRateRecord;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Device;
import android.health.connect.datatypes.DistanceRecord;
import android.health.connect.datatypes.ExerciseLap;
import android.health.connect.datatypes.ExerciseRoute;
import android.health.connect.datatypes.ExerciseSegment;
import android.health.connect.datatypes.ExerciseSegmentType;
import android.health.connect.datatypes.ExerciseSessionRecord;
import android.health.connect.datatypes.ExerciseSessionType;
import android.health.connect.datatypes.HeartRateRecord;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.SleepSessionRecord;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.datatypes.TotalCaloriesBurnedRecord;
import android.health.connect.datatypes.WeightRecord;
import android.health.connect.datatypes.units.Energy;
import android.health.connect.datatypes.units.Length;
import android.health.connect.datatypes.units.Mass;
import android.health.connect.datatypes.units.Power;
import android.healthconnect.cts.utils.TestUtils.RecordAndIdentifier;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class DataFactory {
    // truncate to MILLIS because HC does, so reduce flakiness in some tests.
    public static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    public static final Instant SESSION_START_TIME = NOW.minus(10, ChronoUnit.DAYS);
    public static final Instant SESSION_END_TIME =
            NOW.minus(10, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS);

    public static Device buildDevice() {
        return new Device.Builder()
                .setManufacturer("google")
                .setModel("Pixel4a")
                .setType(2)
                .build();
    }

    public static Metadata generateMetadata() {
        return generateMetadata(UUID.randomUUID().toString());
    }

    /** Generates a {@link Metadata} with specific {@code id}. */
    public static Metadata generateMetadata(String id) {
        return generateMetadata(id, "clientRecordId" + Math.random());
    }

    /** Generates a {@link Metadata} with specific {@code id} and {@code clientId}. */
    public static Metadata generateMetadata(String id, String clientId) {
        Context context = ApplicationProvider.getApplicationContext();
        return new Metadata.Builder()
                .setDevice(buildDevice())
                .setId(id)
                .setClientRecordId(clientId)
                .setDataOrigin(
                        new DataOrigin.Builder().setPackageName(context.getPackageName()).build())
                .setRecordingMethod(Metadata.RECORDING_METHOD_UNKNOWN)
                .build();
    }

    /** Generates a {@link Metadata} with a specific {@code clientId}. */
    public static Metadata generateMetadataWithClientId(String clientId) {
        return generateMetadata(UUID.randomUUID().toString(), clientId);
    }

    public static Metadata getEmptyMetadata() {
        return new Metadata.Builder().build();
    }

    /** Creates a {@link Metadata} with the given record id. */
    public static Metadata getMetadataForId(String id) {
        return new Metadata.Builder().setId(id).build();
    }

    /** Creates a {@link Metadata} with the given record id and data origin. */
    public static Metadata getMetadataForId(String id, DataOrigin dataOrigin) {
        return new Metadata.Builder().setId(id).setDataOrigin(dataOrigin).build();
    }

    /** Creates a {@link Metadata} with the given client record id. */
    public static Metadata getMetadataForClientId(String clientId) {
        return new Metadata.Builder().setClientRecordId(clientId).build();
    }

    /** Creates a {@link Metadata} with the given client record id. */
    public static Metadata getMetadataForClientId(String clientId, DataOrigin dataOrigin) {
        return new Metadata.Builder().setClientRecordId(clientId).setDataOrigin(dataOrigin).build();
    }

    /** Creates a {@link Metadata} with the given client record id. */
    public static Metadata getMetadataForClientIdAndVersion(String clientId, long clientVersion) {
        return new Metadata.Builder()
                .setClientRecordId(clientId)
                .setClientRecordVersion(clientVersion)
                .build();
    }

    /** Creates a {@link Metadata} with the given data origin. */
    public static Metadata getMetadata(DataOrigin dataOrigin) {
        return new Metadata.Builder().setDataOrigin(dataOrigin).build();
    }

    /** Creates a {@link DataOrigin} with the given package name. */
    public static DataOrigin getDataOrigin(String packageName) {
        return new DataOrigin.Builder().setPackageName(packageName).build();
    }

    /** Creates a list of {@link DataOrigin} from a list of package names. */
    public static List<DataOrigin> getDataOrigins(String... packageNames) {
        return Arrays.stream(packageNames).map(DataFactory::getDataOrigin).toList();
    }

    public static SleepSessionRecord buildSleepSession() {
        return buildSleepSession(generateMetadata());
    }

    /** Builds a {@link SleepSessionRecord} with a specific {@code clientId}. */
    public static SleepSessionRecord buildSleepSessionWithClientId(String clientId) {
        return buildSleepSession(generateMetadataWithClientId(clientId));
    }

    /** Builds a {@link SleepSessionRecord} with empty {@link Metadata}. */
    public static SleepSessionRecord buildSleepSessionWithEmptyMetadata() {
        return buildSleepSession(getEmptyMetadata());
    }

    /** Builds a {@link SleepSessionRecord} with a specific {@link Metadata}. */
    public static SleepSessionRecord buildSleepSession(Metadata metadata) {
        return new SleepSessionRecord.Builder(metadata, SESSION_START_TIME, SESSION_END_TIME)
                .setNotes("warm")
                .setTitle("Afternoon nap")
                .setStages(
                        List.of(
                                new SleepSessionRecord.Stage(
                                        SESSION_START_TIME,
                                        SESSION_START_TIME.plusSeconds(300),
                                        SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_LIGHT),
                                new SleepSessionRecord.Stage(
                                        SESSION_START_TIME.plusSeconds(300),
                                        SESSION_START_TIME.plusSeconds(600),
                                        SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_REM),
                                new SleepSessionRecord.Stage(
                                        SESSION_START_TIME.plusSeconds(900),
                                        SESSION_START_TIME.plusSeconds(1200),
                                        SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_DEEP)))
                .build();
    }

    /** Builds a {@link ExerciseSessionRecord} with {@link #generateMetadata()}. */
    public static ExerciseSessionRecord buildExerciseSession() {
        return buildExerciseSession(generateMetadata());
    }

    /** Builds a {@link ExerciseSessionRecord} with an empty {@link Metadata}. */
    public static ExerciseSessionRecord buildExerciseSessionWithEmptyMetadata() {
        return buildExerciseSession(getEmptyMetadata());
    }

    /** Builds a {@link ExerciseSessionRecord} with a specific {@code clientId}. */
    public static ExerciseSessionRecord buildExerciseSessionWithClientId(String clientId) {
        return buildExerciseSession(generateMetadataWithClientId(clientId));
    }

    /** Builds a {@link ExerciseSessionRecord} with a specific {@link Metadata}. */
    public static ExerciseSessionRecord buildExerciseSession(Metadata metadata) {
        return new ExerciseSessionRecord.Builder(
                        metadata,
                        SESSION_START_TIME,
                        SESSION_END_TIME,
                        ExerciseSessionType.EXERCISE_SESSION_TYPE_OTHER_WORKOUT)
                .setRoute(buildExerciseRoute())
                .setLaps(
                        List.of(
                                new ExerciseLap.Builder(
                                                SESSION_START_TIME,
                                                SESSION_START_TIME.plusSeconds(20))
                                        .setLength(Length.fromMeters(10))
                                        .build(),
                                new ExerciseLap.Builder(
                                                SESSION_END_TIME.minusSeconds(20), SESSION_END_TIME)
                                        .build()))
                .setSegments(
                        List.of(
                                new ExerciseSegment.Builder(
                                                SESSION_START_TIME.plusSeconds(1),
                                                SESSION_START_TIME.plusSeconds(10),
                                                ExerciseSegmentType
                                                        .EXERCISE_SEGMENT_TYPE_BENCH_PRESS)
                                        .build(),
                                new ExerciseSegment.Builder(
                                                SESSION_START_TIME.plusSeconds(21),
                                                SESSION_START_TIME.plusSeconds(124),
                                                ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_BURPEE)
                                        .setRepetitionsCount(15)
                                        .build()))
                .setEndZoneOffset(ZoneOffset.MAX)
                .setStartZoneOffset(ZoneOffset.MIN)
                .setNotes("rain")
                .setTitle("Morning training")
                .build();
    }

    public static ExerciseRoute buildExerciseRoute() {
        return new ExerciseRoute(
                List.of(
                        buildLocationTimePoint(SESSION_START_TIME),
                        buildLocationTimePoint(SESSION_START_TIME),
                        buildLocationTimePoint(SESSION_START_TIME)));
    }

    public static ExerciseRoute.Location buildLocationTimePoint(Instant startTime) {
        return new ExerciseRoute.Location.Builder(
                        Instant.ofEpochMilli(
                                (long) (startTime.toEpochMilli() + 10 + Math.random() * 50)),
                        Math.random() * 50,
                        Math.random() * 50)
                .build();
    }

    /** Gets a {@link HeartRateRecord} with an empty {@link Metadata}. */
    public static HeartRateRecord getHeartRateRecordWithEmptyMetadata() {
        return getHeartRateRecord(72, getEmptyMetadata());
    }

    /** Gets a {@link HeartRateRecord} with a specific heart rate and {@link Metadata}. */
    public static HeartRateRecord getHeartRateRecord(int heartRate, Metadata metadata) {
        Instant instant = NOW;
        HeartRateRecord.HeartRateSample heartRateSample =
                new HeartRateRecord.HeartRateSample(heartRate, instant.plusMillis(10));
        return new HeartRateRecord.Builder(
                        metadata, instant, instant.plusMillis(1000), List.of(heartRateSample))
                .build();
    }

    public static HeartRateRecord getHeartRateRecord() {
        return getHeartRateRecord(72);
    }

    public static HeartRateRecord getHeartRateRecord(int heartRate, String clientId) {
        return getHeartRateRecord(heartRate, NOW.plusMillis(100), clientId);
    }

    public static HeartRateRecord getHeartRateRecord(int heartRate) {
        return getHeartRateRecord(heartRate, NOW.plusMillis(100));
    }

    public static HeartRateRecord getHeartRateRecord(int heartRate, Instant instant) {
        return getHeartRateRecord(heartRate, instant, "HR" + Math.random());
    }

    public static HeartRateRecord getHeartRateRecord(
            int heartRate, Instant instant, String clientId) {
        String packageName = ApplicationProvider.getApplicationContext().getPackageName();
        HeartRateRecord.HeartRateSample heartRateSample =
                new HeartRateRecord.HeartRateSample(heartRate, instant);
        ArrayList<HeartRateRecord.HeartRateSample> heartRateSamples = new ArrayList<>();
        heartRateSamples.add(heartRateSample);
        heartRateSamples.add(heartRateSample);
        Device device = buildDevice();
        DataOrigin dataOrigin = new DataOrigin.Builder().setPackageName(packageName).build();

        return new HeartRateRecord.Builder(
                        new Metadata.Builder()
                                .setDevice(device)
                                .setDataOrigin(dataOrigin)
                                .setClientRecordId(clientId)
                                .build(),
                        instant.minusMillis(100),
                        instant.plusMillis(100),
                        heartRateSamples)
                .build();
    }

    /** Creates and returns a {@link WeightRecord} with the specified arguments. */
    public static WeightRecord getWeightRecord(double grams, Instant time) {
        return new WeightRecord.Builder(new Metadata.Builder().build(), time, Mass.fromGrams(grams))
                .build();
    }

    public static StepsRecord getStepsRecordWithEmptyMetaData() {
        return getStepsRecord(10, getEmptyMetadata());
    }

    public static StepsRecord getStepsRecord() {
        return getStepsRecord(10);
    }

    /** Creates and returns a {@link StepsRecord} with the specified arguments. */
    public static StepsRecord getStepsRecord(long steps) {
        return getStepsRecord(steps, generateMetadata());
    }

    /** Creates and returns a {@link StepsRecord} with the specified arguments. */
    public static StepsRecord getStepsRecord(long steps, String clientId) {
        return getStepsRecord(steps, getMetadataForClientId(clientId));
    }

    /** Creates and returns a {@link StepsRecord} with the specified metadata. */
    public static StepsRecord getStepsRecord(long steps, Metadata metadata) {
        return new StepsRecord.Builder(metadata, NOW, NOW.plusMillis(1000), steps).build();
    }

    /** Creates and returns a {@link StepsRecord} with the specified arguments. */
    public static StepsRecord getStepsRecord(long steps, Instant start, Instant end) {
        return new StepsRecord.Builder(getEmptyMetadata(), start, end, steps).build();
    }

    /** Creates and returns a {@link StepsRecord} with the specified arguments. */
    public static StepsRecord getStepsRecord(
            long steps, Instant start, Instant end, String clientId) {
        return new StepsRecord.Builder(getMetadataForClientId(clientId), start, end, steps).build();
    }

    public static StepsRecord getStepsRecord(String id) {
        return new StepsRecord.Builder(generateMetadata(id), NOW, NOW.plusMillis(1000), 10).build();
    }

    /** Creates and returns a {@link StepsRecord} with default arguments. */
    public static StepsRecord getCompleteStepsRecord() {
        return getCompleteStepsRecord(
                NOW, NOW.plusMillis(1000), /* clientRecordId= */ "SR" + Math.random());
    }

    /** Creates and returns a {@link StepsRecord} with the specified arguments. */
    public static StepsRecord getCompleteStepsRecord(
            Instant startTime, Instant endTime, String clientRecordId) {
        return getCompleteStepsRecord(startTime, endTime, clientRecordId, /* count= */ 10);
    }

    /** Creates and returns a {@link StepsRecord} with the specified arguments. */
    public static StepsRecord getCompleteStepsRecord(
            Instant startTime, Instant endTime, String clientRecordId, int count) {
        return getCompleteStepsRecord(
                startTime, endTime, clientRecordId, /* clientRecordVersion= */ 0L, count);
    }

    /** Creates and returns a {@link StepsRecord} with the specified arguments. */
    public static StepsRecord getCompleteStepsRecord(
            Instant startTime,
            Instant endTime,
            String clientRecordId,
            long clientRecordVersion,
            int count) {
        Device device =
                new Device.Builder().setManufacturer("google").setModel("Pixel").setType(1).build();
        DataOrigin dataOrigin =
                new DataOrigin.Builder().setPackageName("android.healthconnect.cts").build();

        Metadata.Builder testMetadataBuilder = new Metadata.Builder();
        testMetadataBuilder.setDevice(device).setDataOrigin(dataOrigin);
        testMetadataBuilder.setClientRecordId(clientRecordId);
        testMetadataBuilder.setClientRecordVersion(clientRecordVersion);
        testMetadataBuilder.setRecordingMethod(RECORDING_METHOD_ACTIVELY_RECORDED);
        Metadata testMetaData = testMetadataBuilder.build();
        assertThat(testMetaData.getRecordingMethod()).isEqualTo(RECORDING_METHOD_ACTIVELY_RECORDED);
        return new StepsRecord.Builder(testMetaData, startTime, endTime, count).build();
    }

    public static StepsRecord getUpdatedStepsRecord(
            Record record, String id, String clientRecordId) {
        Metadata metadata = record.getMetadata();
        Metadata metadataWithId =
                new Metadata.Builder()
                        .setId(id)
                        .setClientRecordId(clientRecordId)
                        .setClientRecordVersion(metadata.getClientRecordVersion())
                        .setDataOrigin(metadata.getDataOrigin())
                        .setDevice(metadata.getDevice())
                        .setLastModifiedTime(metadata.getLastModifiedTime())
                        .build();
        return new StepsRecord.Builder(metadataWithId, NOW, NOW.plusMillis(2000), 20)
                .setStartZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(NOW))
                .setEndZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(NOW))
                .build();
    }

    /** Creates a {@link DistanceRecord}. */
    public static DistanceRecord getDistanceRecord() {
        return getDistanceRecord(10.0, NOW, NOW.plusMillis(1000));
    }

    /** Creates a {@link DistanceRecord} with a specified {@code clientId}. */
    public static DistanceRecord getDistanceRecordWithClientId(String clientId) {
        return getDistanceRecord(
                10,
                NOW,
                NOW.plusMillis(1000),
                /* startZoneOffset= */ null,
                /* endZoneOffset= */ null,
                generateMetadataWithClientId(clientId));
    }

    /** Create a {@link DistanceRecord} with non empty record ID. */
    public static DistanceRecord getDistanceRecordWithNonEmptyId() {
        return getDistanceRecord(
                10,
                NOW,
                NOW.plusMillis(1000),
                /* startZoneOffset= */ null,
                /* endZoneOffset= */ null,
                generateMetadata());
    }

    /** Create a {@link DistanceRecord} with empty {@link Metadata}. */
    public static DistanceRecord getDistanceRecordWithEmptyMetadata() {
        return getDistanceRecord(
                10,
                NOW,
                NOW.plusMillis(1000),
                /* startZoneOffset= */ null,
                /* endZoneOffset= */ null,
                getEmptyMetadata());
    }

    /** Create a {@link DistanceRecord} with the specified arguments. */
    public static DistanceRecord getDistanceRecord(double distance, Instant start, Instant end) {
        return getDistanceRecord(
                distance,
                start,
                end,
                /* startZoneOffset= */ null,
                /* endZoneOffset= */ null,
                getEmptyMetadata());
    }

    /** Create a {@link DistanceRecord} with the specified arguments. */
    public static DistanceRecord getDistanceRecord(
            double distance, Instant start, Instant end, ZoneOffset offset) {
        return getDistanceRecord(distance, start, end, offset, offset, getEmptyMetadata());
    }

    /** Create a {@link DistanceRecord} with the specified arguments. */
    public static DistanceRecord getDistanceRecord(
            double distance, Instant start, Instant end, String clientId) {
        return getDistanceRecord(
                distance,
                start,
                end,
                /* startZoneOffset= */ null,
                /* endZoneOffset= */ null,
                getMetadataForClientId(clientId));
    }

    /** Create a {@link DistanceRecord} with the specified arguments. */
    public static DistanceRecord getDistanceRecord(
            double distance,
            Instant start,
            Instant end,
            @Nullable ZoneOffset startZoneOffset,
            @Nullable ZoneOffset endZoneOffset,
            Metadata metadata) {
        DistanceRecord.Builder builder =
                new DistanceRecord.Builder(metadata, start, end, Length.fromMeters(distance));
        if (startZoneOffset != null) {
            builder.setStartZoneOffset(startZoneOffset);
        }
        if (endZoneOffset != null) {
            builder.setEndZoneOffset(endZoneOffset);
        }
        return builder.build();
    }

    /** Gets a {@link TotalCaloriesBurnedRecord} with a specific {@code clientId}. */
    public static TotalCaloriesBurnedRecord getTotalCaloriesBurnedRecord(String clientId) {
        return getTotalCaloriesBurnedRecord(getMetadataForClientId(clientId));
    }

    /** Gets a {@link TotalCaloriesBurnedRecord} with a specific {@link Metadata}. */
    public static TotalCaloriesBurnedRecord getTotalCaloriesBurnedRecord(Metadata metadata) {
        return new TotalCaloriesBurnedRecord.Builder(
                        metadata, NOW, NOW.plusMillis(1000), Energy.fromCalories(10.0))
                .build();
    }

    public static TotalCaloriesBurnedRecord getTotalCaloriesBurnedRecordWithEmptyMetadata() {
        return getTotalCaloriesBurnedRecord(getEmptyMetadata());
    }

    public static List<Record> getTestRecords() {
        return Arrays.asList(
                getStepsRecord(),
                getHeartRateRecord(),
                getBasalMetabolicRateRecord(),
                buildExerciseSession());
    }

    public static ChangeLogTokenRequest.Builder getChangeLogTokenRequestForTestRecordTypes() {
        return new ChangeLogTokenRequest.Builder()
                .addRecordType(StepsRecord.class)
                .addRecordType(HeartRateRecord.class)
                .addRecordType(BasalMetabolicRateRecord.class)
                .addRecordType(ExerciseSessionRecord.class);
    }

    public static List<RecordAndIdentifier> getRecordsAndIdentifiers() {
        return Arrays.asList(
                new RecordAndIdentifier(RECORD_TYPE_STEPS, getStepsRecord()),
                new RecordAndIdentifier(RECORD_TYPE_HEART_RATE, getHeartRateRecord()),
                new RecordAndIdentifier(
                        RECORD_TYPE_BASAL_METABOLIC_RATE, getBasalMetabolicRateRecord()));
    }

    public static BasalMetabolicRateRecord getBasalMetabolicRateRecord() {
        return new BasalMetabolicRateRecord.Builder(generateMetadata(), NOW, Power.fromWatts(100.0))
                .build();
    }
}
