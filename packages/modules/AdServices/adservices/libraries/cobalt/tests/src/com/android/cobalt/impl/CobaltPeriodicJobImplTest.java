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

package com.android.cobalt.impl;

import static com.android.cobalt.collect.ImmutableHelpers.toImmutableList;
import static com.android.cobalt.collect.ImmutableHelpers.toImmutableMap;

import static com.google.common.truth.Truth.assertThat;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.cobalt.CobaltPeriodicJob;
import com.android.cobalt.crypto.Encrypter;
import com.android.cobalt.crypto.testing.NoOpEncrypter;
import com.android.cobalt.data.CobaltDatabase;
import com.android.cobalt.data.DataService;
import com.android.cobalt.data.EventVector;
import com.android.cobalt.data.ReportKey;
import com.android.cobalt.data.TestOnlyDao;
import com.android.cobalt.domain.Project;
import com.android.cobalt.observations.PrivacyGenerator;
import com.android.cobalt.observations.testing.ConstantFakeSecureRandom;
import com.android.cobalt.system.SystemData;
import com.android.cobalt.system.testing.FakeSystemClock;
import com.android.cobalt.upload.testing.NoOpUploader;

import com.google.cobalt.Envelope;
import com.google.cobalt.IndexHistogram;
import com.google.cobalt.IntegerObservation;
import com.google.cobalt.MetricDefinition;
import com.google.cobalt.MetricDefinition.Metadata;
import com.google.cobalt.MetricDefinition.MetricDimension;
import com.google.cobalt.MetricDefinition.MetricType;
import com.google.cobalt.MetricDefinition.TimeZonePolicy;
import com.google.cobalt.Observation;
import com.google.cobalt.ObservationBatch;
import com.google.cobalt.ObservationMetadata;
import com.google.cobalt.ObservationToEncrypt;
import com.google.cobalt.PrivateIndexObservation;
import com.google.cobalt.ReleaseStage;
import com.google.cobalt.ReportDefinition;
import com.google.cobalt.ReportDefinition.PrivacyLevel;
import com.google.cobalt.ReportDefinition.ReportType;
import com.google.cobalt.ReportParticipationObservation;
import com.google.cobalt.StringHistogramObservation;
import com.google.cobalt.SystemProfile;
import com.google.cobalt.SystemProfileField;
import com.google.cobalt.SystemProfileSelectionPolicy;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@RunWith(AndroidJUnit4.class)
public class CobaltPeriodicJobImplTest {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final ScheduledExecutorService SCHEDULED_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor();
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final Duration UPLOAD_DONE_DELAY = Duration.ofMillis(10);

    private static final String API_KEY = "12345678";
    private static final Instant LOG_TIME = Instant.parse("2022-07-28T14:15:30.00Z");
    private static final int LOG_TIME_DAY = 19201;
    private static final Instant ENABLED_TIME = LOG_TIME.minus(Duration.ofDays(32));
    private static final Instant UPLOAD_TIME = Instant.parse("2022-07-29T14:15:30.00Z");
    private static final Instant CLEANUP_TIME =
            UPLOAD_TIME.plus(Duration.ofDays(CobaltPeriodicJobImpl.LARGEST_AGGREGATION_WINDOW + 2));
    private static final int CLEANUP_DAY =
            LOG_TIME_DAY + CobaltPeriodicJobImpl.LARGEST_AGGREGATION_WINDOW + 2;
    private static final ReportKey REPORT_1 = ReportKey.create(1, 1, 1, 1);
    private static final ReportKey REPORT_2 =
            ReportKey.create(REPORT_1.customerId(), REPORT_1.projectId(), 2, 2);
    private static final ReportKey REPORT_3 =
            ReportKey.create(REPORT_1.customerId(), REPORT_1.projectId(), REPORT_2.metricId(), 3);
    private static final ReportKey REPORT_4 =
            ReportKey.create(REPORT_1.customerId(), REPORT_1.projectId(), REPORT_2.metricId(), 4);
    private static final int WRONG_TYPE_METRIC = 3;
    private static final String APP_VERSION = "0.1.2";
    private static final ReleaseStage RELEASE_STAGE = ReleaseStage.DOGFOOD;
    private static final SystemProfile SYSTEM_PROFILE_1 =
            SystemProfile.newBuilder().setSystemVersion("1.2.3").build();
    private static final SystemProfile SYSTEM_PROFILE_2 =
            SystemProfile.newBuilder().setSystemVersion("2.4.8").build();
    private static final ObservationMetadata REPORT_1_METADATA =
            ObservationMetadata.newBuilder()
                    .setCustomerId((int) REPORT_1.customerId())
                    .setProjectId((int) REPORT_1.projectId())
                    .setMetricId((int) REPORT_1.metricId())
                    .setReportId((int) REPORT_1.reportId())
                    .setDayIndex(LOG_TIME_DAY)
                    .setSystemProfile(SYSTEM_PROFILE_1)
                    .build();
    private static final ObservationMetadata REPORT_1_METADATA_2 =
            ObservationMetadata.newBuilder()
                    .setCustomerId((int) REPORT_1.customerId())
                    .setProjectId((int) REPORT_1.projectId())
                    .setMetricId((int) REPORT_1.metricId())
                    .setReportId((int) REPORT_1.reportId())
                    .setDayIndex(LOG_TIME_DAY)
                    .setSystemProfile(SYSTEM_PROFILE_2)
                    .build();
    private static final ObservationMetadata REPORT_2_METADATA =
            ObservationMetadata.newBuilder()
                    .setCustomerId((int) REPORT_2.customerId())
                    .setProjectId((int) REPORT_2.projectId())
                    .setMetricId((int) REPORT_2.metricId())
                    .setReportId((int) REPORT_2.reportId())
                    .setDayIndex(LOG_TIME_DAY)
                    .setSystemProfile(SYSTEM_PROFILE_1)
                    .build();
    private static final ObservationMetadata REPORT_3_METADATA =
            ObservationMetadata.newBuilder()
                    .setCustomerId((int) REPORT_3.customerId())
                    .setProjectId((int) REPORT_3.projectId())
                    .setMetricId((int) REPORT_3.metricId())
                    .setReportId((int) REPORT_3.reportId())
                    .setDayIndex(LOG_TIME_DAY)
                    .setSystemProfile(SYSTEM_PROFILE_2)
                    .build();
    private static final ObservationMetadata REPORT_4_METADATA =
            ObservationMetadata.newBuilder()
                    .setCustomerId((int) REPORT_4.customerId())
                    .setProjectId((int) REPORT_4.projectId())
                    .setMetricId((int) REPORT_4.metricId())
                    .setReportId((int) REPORT_4.reportId())
                    .setDayIndex(LOG_TIME_DAY)
                    .setSystemProfile(SYSTEM_PROFILE_2)
                    .build();
    private static final EventVector EVENT_VECTOR_1 = EventVector.create(1, 5);
    private static final EventVector EVENT_VECTOR_2 = EventVector.create(2, 6);
    private static final EventVector EVENT_VECTOR_3 = EventVector.create(3, 7);
    private static final long EVENT_COUNT_1 = 1;
    private static final long EVENT_COUNT_2 = 2;
    private static final long EVENT_COUNT_3 = 3;
    // Deterministic randomly generated bytes due to the ConstantFakeSecureRandom.
    private static final ByteString RANDOM_BYTES =
            ByteString.copyFrom(new byte[] {1, 1, 1, 1, 1, 1, 1, 1});
    private static final Observation OBSERVATION_1 =
            Observation.newBuilder()
                    .setInteger(
                            IntegerObservation.newBuilder()
                                    .addValues(
                                            IntegerObservation.Value.newBuilder()
                                                    .setValue(EVENT_COUNT_1)
                                                    .addAllEventCodes(EVENT_VECTOR_1.eventCodes())))
                    .setRandomId(RANDOM_BYTES)
                    .build();
    private static final Observation OBSERVATION_2 =
            Observation.newBuilder()
                    .setInteger(
                            IntegerObservation.newBuilder()
                                    .addValues(
                                            IntegerObservation.Value.newBuilder()
                                                    .setValue(EVENT_COUNT_2)
                                                    .addAllEventCodes(EVENT_VECTOR_2.eventCodes())))
                    .setRandomId(RANDOM_BYTES)
                    .build();
    private static final Observation OBSERVATION_3 =
            Observation.newBuilder()
                    .setInteger(
                            IntegerObservation.newBuilder()
                                    .addValues(
                                            IntegerObservation.Value.newBuilder()
                                                    .setValue(EVENT_COUNT_3)
                                                    .addAllEventCodes(EVENT_VECTOR_3.eventCodes())))
                    .setRandomId(RANDOM_BYTES)
                    .build();

    private static final MetricDefinition METRIC_1 =
            MetricDefinition.newBuilder()
                    .setId((int) REPORT_1.metricId())
                    .setMetricType(MetricType.OCCURRENCE)
                    .setTimeZonePolicy(TimeZonePolicy.OTHER_TIME_ZONE)
                    .setOtherTimeZone("America/Los_Angeles")
                    .addReports(
                            ReportDefinition.newBuilder()
                                    .setId((int) REPORT_1.reportId())
                                    .setReportType(ReportType.FLEETWIDE_OCCURRENCE_COUNTS)
                                    .setSystemProfileSelection(
                                            SystemProfileSelectionPolicy.REPORT_ALL)
                                    .setPrivacyLevel(PrivacyLevel.NO_ADDED_PRIVACY))
                    .setMetaData(Metadata.newBuilder().setMaxReleaseStage(ReleaseStage.DOGFOOD))
                    .build();
    private static final MetricDefinition METRIC_2 =
            MetricDefinition.newBuilder()
                    .setId((int) REPORT_2.metricId())
                    .setMetricType(MetricType.OCCURRENCE)
                    .addReports(
                            ReportDefinition.newBuilder()
                                    .setId((int) REPORT_2.reportId())
                                    .setReportType(ReportType.FLEETWIDE_OCCURRENCE_COUNTS)
                                    .setSystemProfileSelection(
                                            SystemProfileSelectionPolicy.REPORT_ALL)
                                    .setPrivacyLevel(PrivacyLevel.NO_ADDED_PRIVACY))
                    .addReports(
                            ReportDefinition.newBuilder()
                                    .setId((int) REPORT_3.reportId())
                                    .setReportType(ReportType.FLEETWIDE_OCCURRENCE_COUNTS)
                                    .setSystemProfileSelection(
                                            SystemProfileSelectionPolicy.REPORT_ALL)
                                    .setPrivacyLevel(PrivacyLevel.NO_ADDED_PRIVACY))
                    .addReports(
                            ReportDefinition.newBuilder()
                                    .setId((int) REPORT_4.reportId())
                                    .setReportType(ReportType.FLEETWIDE_OCCURRENCE_COUNTS)
                                    .setPrivacyLevel(PrivacyLevel.NO_ADDED_PRIVACY))
                    .setMetaData(Metadata.newBuilder().setMaxReleaseStage(ReleaseStage.DOGFOOD))
                    .build();
    private static final MetricDefinition METRIC_3 =
            MetricDefinition.newBuilder()
                    .setId(WRONG_TYPE_METRIC)
                    .setMetricType(MetricType.INTEGER)
                    .setMetaData(Metadata.newBuilder().setMaxReleaseStage(ReleaseStage.DOGFOOD))
                    .build();

    private Project mProject =
            Project.create(
                    (int) REPORT_1.customerId(),
                    (int) REPORT_1.projectId(),
                    List.of(METRIC_1, METRIC_2, METRIC_3));

    private CobaltDatabase mCobaltDatabase;
    private TestOnlyDao mTestOnlyDao;
    private DataService mDataService;
    private SecureRandom mSecureRandom;
    private PrivacyGenerator mPrivacyGenerator;
    private FakeSystemClock mClock;
    private SystemData mSystemData;
    private NoOpUploader mUploader;
    private Encrypter mEncrypter;
    private CobaltPeriodicJob mPeriodicJob;
    private boolean mEnabled = true;

    private static ImmutableList<String> apiKeysOf(ImmutableList<Envelope> envelopes) {
        return ImmutableList.copyOf(
                envelopes.stream().map(e -> e.getApiKey().toStringUtf8()).collect(toList()));
    }

    private static ImmutableMap<ObservationMetadata, ImmutableList<ObservationToEncrypt>>
            getObservationsIn(Envelope envelope) {
        return envelope.getBatchList().stream()
                .collect(
                        toImmutableMap(
                                ObservationBatch::getMetaData,
                                CobaltPeriodicJobImplTest::getObservationsIn));
    }

    private static ImmutableList<ObservationToEncrypt> getObservationsIn(ObservationBatch batch) {
        return batch.getEncryptedObservationList().stream()
                .map(
                        e -> {
                            try {
                                return ObservationToEncrypt.newBuilder()
                                        .setContributionId(e.getContributionId())
                                        .setObservation(Observation.parseFrom(e.getCiphertext()))
                                        .build();
                            } catch (InvalidProtocolBufferException x) {
                                return ObservationToEncrypt.getDefaultInstance();
                            }
                        })
                .collect(toImmutableList());
    }

    /** Method to manually set up state before a test begins. */
    public void manualSetUp() throws ExecutionException, InterruptedException {
        mCobaltDatabase = Room.inMemoryDatabaseBuilder(CONTEXT, CobaltDatabase.class).build();
        mTestOnlyDao = mCobaltDatabase.testOnlyDao();
        mDataService = new DataService(EXECUTOR, mCobaltDatabase);
        mSecureRandom = new ConstantFakeSecureRandom();
        mPrivacyGenerator = new PrivacyGenerator(mSecureRandom);
        mClock = new FakeSystemClock();
        mSystemData = new SystemData(APP_VERSION);
        mUploader = new NoOpUploader();
        mEncrypter = new NoOpEncrypter();
        mPeriodicJob =
                new CobaltPeriodicJobImpl(
                        mProject,
                        RELEASE_STAGE,
                        mDataService,
                        EXECUTOR,
                        SCHEDULED_EXECUTOR,
                        mClock,
                        mSystemData,
                        mPrivacyGenerator,
                        mSecureRandom,
                        mUploader,
                        mEncrypter,
                        ByteString.copyFrom(API_KEY.getBytes(UTF_8)),
                        UPLOAD_DONE_DELAY,
                        mEnabled);

        mClock.set(LOG_TIME);
        mDataService.loggerEnabled(ENABLED_TIME).get();

        // Initialize all reports as up to date for sending observations up to the previous day.
        mTestOnlyDao.insertLastSentDayIndex(REPORT_1, LOG_TIME_DAY - 1);
        mTestOnlyDao.insertLastSentDayIndex(REPORT_2, LOG_TIME_DAY - 1);
        mTestOnlyDao.insertLastSentDayIndex(REPORT_3, LOG_TIME_DAY - 1);
        mTestOnlyDao.insertLastSentDayIndex(REPORT_4, LOG_TIME_DAY - 1);
    }

    @After
    public void closeDb() throws IOException {
        mCobaltDatabase.close();
    }

    @Test
    public void generateAggregatedObservations_dayWasAlreadyGenerated_nothingUploaded()
            throws Exception {
        // Setup the classes.
        manualSetUp();

        // Trigger the CobaltPeriodicJob for the LOG_TIME day.
        mPeriodicJob.generateAggregatedObservations().get();

        // Verify no observations were generated, but the upload was marked done and logger was
        // recorded as enabled.
        assertThat(mUploader.getSentEnvelopes()).isEmpty();
        assertThat(mUploader.getUploadDoneCount()).isEqualTo(1);
        assertThat(mTestOnlyDao.getInitialEnabledTime()).isEqualTo(Optional.of(ENABLED_TIME));
    }

    @Test
    public void generateAggregatedObservations_noLoggedData_nothingUploaded() throws Exception {
        // Setup the classes.
        manualSetUp();

        // Trigger the CobaltPeriodicJob for the current day.
        mClock.set(UPLOAD_TIME);
        mPeriodicJob.generateAggregatedObservations().get();

        // Verify no observations were stored/sent but the uploader was told it's done and last sent
        // day index was updated.
        assertThat(mUploader.getSentEnvelopes()).isEmpty();
        assertThat(mUploader.getUploadDoneCount()).isEqualTo(1);
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_1))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_2))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_3))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.getObservationBatches()).isEmpty();
    }

    @Test
    public void generateAggregatedObservations_oneLoggedReport_observationSent() throws Exception {
        // Setup the classes.
        manualSetUp();

        // Mark a Count report as having occurred on the previous day.
        mDataService
                .aggregateCount(
                        REPORT_1,
                        LOG_TIME_DAY,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        /* count= */ EVENT_COUNT_1)
                .get();

        // Trigger the CobaltPeriodicJob for the current day.
        mClock.set(UPLOAD_TIME);
        mPeriodicJob.generateAggregatedObservations().get();

        // Verify the observations were all removed and the last sent day index updated.
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_1))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_2))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_3))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_4))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.getObservationBatches()).isEmpty();

        // Verify the envelope containing the generated observation was passed to Clearcut.
        ImmutableList<Envelope> sentEnvelopes = mUploader.getSentEnvelopes();
        assertThat(sentEnvelopes).hasSize(1);
        assertThat(apiKeysOf(sentEnvelopes)).containsExactly(API_KEY);
        assertThat(getObservationsIn(sentEnvelopes.get(0)))
                .containsExactly(
                        REPORT_1_METADATA,
                        ImmutableList.of(
                                ObservationToEncrypt.newBuilder()
                                        .setObservation(OBSERVATION_1)
                                        .setContributionId(RANDOM_BYTES)
                                        .build()));
        assertThat(mUploader.getUploadDoneCount()).isEqualTo(1);
    }

    @Test
    public void generateAggregatedObservations_threeLoggedReports_oneEnvelopeSent()
            throws Exception {
        // Setup the classes.
        manualSetUp();

        // Mark three Count reports as having occurred on the previous day.
        mDataService
                .aggregateCount(
                        REPORT_2,
                        LOG_TIME_DAY,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_1)
                .get();
        mDataService
                .aggregateCount(
                        REPORT_3,
                        LOG_TIME_DAY,
                        SYSTEM_PROFILE_2,
                        EVENT_VECTOR_2,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_2)
                .get();
        mDataService
                .aggregateCount(
                        REPORT_4,
                        LOG_TIME_DAY,
                        SYSTEM_PROFILE_2,
                        EVENT_VECTOR_3,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_3)
                .get();

        // Trigger the CobaltPeriodicJob for the current day.
        mClock.set(UPLOAD_TIME);
        mPeriodicJob.generateAggregatedObservations().get();

        // Verify the observations were all removed and the last sent day index updated.
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_1))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_2))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_3))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_4))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.getObservationBatches()).isEmpty();

        // Verify the one envelope containing the 3 generated observations was passed to Clearcut.
        ImmutableList<Envelope> sentEnvelopes = mUploader.getSentEnvelopes();
        assertThat(sentEnvelopes).hasSize(1);
        assertThat(apiKeysOf(sentEnvelopes)).containsExactly(API_KEY);
        assertThat(getObservationsIn(sentEnvelopes.get(0)))
                .containsExactly(
                        REPORT_2_METADATA,
                        ImmutableList.of(
                                ObservationToEncrypt.newBuilder()
                                        .setObservation(OBSERVATION_1)
                                        .setContributionId(RANDOM_BYTES)
                                        .build()),
                        REPORT_3_METADATA,
                        ImmutableList.of(
                                ObservationToEncrypt.newBuilder()
                                        .setObservation(OBSERVATION_2)
                                        .setContributionId(RANDOM_BYTES)
                                        .build()),
                        REPORT_4_METADATA,
                        ImmutableList.of(
                                ObservationToEncrypt.newBuilder()
                                        .setObservation(OBSERVATION_3)
                                        .setContributionId(RANDOM_BYTES)
                                        .build()));
        assertThat(mUploader.getUploadDoneCount()).isEqualTo(1);
    }

    @Test
    public void
            generateAggregatedObservations_twoObservationsOverByteLimit_sentInSeparateEnvelopes()
                    throws Exception {
        // Setup the classes.
        manualSetUp();

        // Mark a Count report as having occurred on the previous day. Create an event that will
        // cause an observation that will be larger than the envelope limit.
        SystemProfile large =
                SYSTEM_PROFILE_1.toBuilder()
                        .setChannel(
                                String.join(
                                        "",
                                        Collections.nCopies(
                                                CobaltPeriodicJobImpl.ENVELOPE_MAX_OBSERVATION_BYTES
                                                        + 10,
                                                "1")))
                        .build();
        mDataService
                .aggregateCount(
                        REPORT_2,
                        LOG_TIME_DAY,
                        large,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_1)
                .get();
        mDataService
                .aggregateCount(
                        REPORT_3,
                        LOG_TIME_DAY,
                        SYSTEM_PROFILE_2,
                        EVENT_VECTOR_2,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_2)
                .get();

        // Trigger the CobaltPeriodicJob for the current day.
        mClock.set(UPLOAD_TIME);
        mPeriodicJob.generateAggregatedObservations().get();

        // Verify the observations were all removed and the last sent day index updated.
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_1))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_2))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_3))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_4))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.getObservationBatches()).isEmpty();

        // Verify both envelopes were passed to Clearcut, each with a different observation.
        ImmutableList<Envelope> sentEnvelopes = mUploader.getSentEnvelopes();
        assertThat(sentEnvelopes).hasSize(2);
        assertThat(apiKeysOf(sentEnvelopes)).containsExactly(API_KEY, API_KEY);

        // The ordering of sentEnvelopes is inconsistent across test runs so compare the actual
        // envelopes are a subset of the expected results and not the same.
        ImmutableMap<ObservationMetadata, ImmutableList<ObservationToEncrypt>>
                expectedEnvelopeObservations =
                        ImmutableMap.of(
                                REPORT_2_METADATA.toBuilder().setSystemProfile(large).build(),
                                ImmutableList.of(
                                        ObservationToEncrypt.newBuilder()
                                                .setObservation(OBSERVATION_1)
                                                .setContributionId(RANDOM_BYTES)
                                                .build()),
                                REPORT_3_METADATA,
                                ImmutableList.of(
                                        ObservationToEncrypt.newBuilder()
                                                .setObservation(OBSERVATION_2)
                                                .setContributionId(RANDOM_BYTES)
                                                .build()));
        assertThat(expectedEnvelopeObservations)
                .containsAtLeastEntriesIn(getObservationsIn(sentEnvelopes.get(0)));
        assertThat(expectedEnvelopeObservations)
                .containsAtLeastEntriesIn(getObservationsIn(sentEnvelopes.get(1)));
        assertThat(getObservationsIn(sentEnvelopes.get(0)))
                .isNotEqualTo(getObservationsIn(sentEnvelopes.get(1)));
        assertThat(mUploader.getUploadDoneCount()).isEqualTo(1);
    }

    @Test
    public void
            generateAggregatedObservations_reportAllMultipleSystemProfiles_observationContainsBoth()
                    throws Exception {
        // Setup the classes.
        manualSetUp();

        // Mark a Count report as having occurred on the previous day with 2 system profiles
        mDataService
                .aggregateCount(
                        REPORT_1,
                        LOG_TIME_DAY,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_1)
                .get();
        mDataService
                .aggregateCount(
                        REPORT_1,
                        LOG_TIME_DAY,
                        SYSTEM_PROFILE_2,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_1)
                .get();

        // Trigger the CobaltPeriodicJob for the current day.
        mClock.set(UPLOAD_TIME);
        mPeriodicJob.generateAggregatedObservations().get();

        // Verify the observations were all removed and the last sent day index updated.
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_1))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.getObservationBatches()).isEmpty();

        // Verify the envelope containing the generated observation was passed to Clearcut.
        // There should be two batches with different system profiles but identical observations.
        ImmutableList<Envelope> sentEnvelopes = mUploader.getSentEnvelopes();
        assertThat(sentEnvelopes).hasSize(1);
        assertThat(apiKeysOf(sentEnvelopes)).containsExactly(API_KEY);

        assertThat(getObservationsIn(sentEnvelopes.get(0)))
                .containsExactly(
                        REPORT_1_METADATA,
                                ImmutableList.of(
                                        ObservationToEncrypt.newBuilder()
                                                .setObservation(OBSERVATION_1)
                                                .setContributionId(RANDOM_BYTES)
                                                .build()),
                        REPORT_1_METADATA_2,
                                ImmutableList.of(
                                        ObservationToEncrypt.newBuilder()
                                                .setObservation(
                                                        OBSERVATION_1.toBuilder()
                                                                .setRandomId(RANDOM_BYTES)
                                                                .build())
                                                .setContributionId(RANDOM_BYTES)
                                                .build()));
        assertThat(mUploader.getUploadDoneCount()).isEqualTo(1);
    }

    @Test
    public void generateAggregatedObservations_eventVectorBufferMax_olderEventVectorsDropped()
            throws Exception {
        // 7-day report with event_vector_buffer_max set to 1.
        MetricDefinition metric =
                MetricDefinition.newBuilder()
                        .setId((int) REPORT_1.metricId())
                        .setMetricType(MetricType.OCCURRENCE)
                        .setTimeZonePolicy(TimeZonePolicy.OTHER_TIME_ZONE)
                        .setOtherTimeZone("America/Los_Angeles")
                        .addReports(
                                ReportDefinition.newBuilder()
                                        .setId((int) REPORT_1.reportId())
                                        .setReportType(ReportType.FLEETWIDE_OCCURRENCE_COUNTS)
                                        .setEventVectorBufferMax(1)
                                        .setPrivacyLevel(PrivacyLevel.NO_ADDED_PRIVACY))
                        .setMetaData(Metadata.newBuilder().setMaxReleaseStage(ReleaseStage.DOGFOOD))
                        .build();
        mProject =
                Project.create(
                        (int) REPORT_1.customerId(), (int) REPORT_1.projectId(), List.of(metric));

        // Setup the classes.
        manualSetUp();

        // Mark a Count report as having occurred on the previous 2 days with different event
        // vectors.
        mDataService
                .aggregateCount(
                        REPORT_1,
                        LOG_TIME_DAY - 1,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_1)
                .get();
        mDataService
                .aggregateCount(
                        REPORT_1,
                        LOG_TIME_DAY,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_2,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_2)
                .get();

        // Trigger the CobaltPeriodicJob for the current day.
        mClock.set(UPLOAD_TIME);
        mPeriodicJob.generateAggregatedObservations().get();

        // Verify the observations were all removed and the last sent day index updated.
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_1))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.getObservationBatches()).isEmpty();

        // Verify the envelope containing the generated observation was passed to Clearcut.
        // There should be one batch and observation for the event vector that occurred first.
        ImmutableList<Envelope> sentEnvelopes = mUploader.getSentEnvelopes();
        assertThat(sentEnvelopes).hasSize(1);
        assertThat(apiKeysOf(sentEnvelopes)).containsExactly(API_KEY);

        assertThat(getObservationsIn(sentEnvelopes.get(0)))
                .containsExactly(
                        REPORT_1_METADATA,
                        ImmutableList.of(
                                ObservationToEncrypt.newBuilder()
                                        .setObservation(
                                                OBSERVATION_2.toBuilder()
                                                        .setRandomId(RANDOM_BYTES)
                                                        .build())
                                        .setContributionId(RANDOM_BYTES)
                                        .build()));
        assertThat(mUploader.getUploadDoneCount()).isEqualTo(1);
    }

    @Test
    public void generateAggregatedObservations_oneFabricatedObservation_usesCurrentSystemProfile()
            throws Exception {
        // Registry containing a single privacy-enabled report that will trigger a fabricated and
        // report participation observations.
        MetricDefinition metric =
                MetricDefinition.newBuilder()
                        .setId((int) REPORT_1.metricId())
                        .setMetricType(MetricType.OCCURRENCE)
                        .setTimeZonePolicy(TimeZonePolicy.OTHER_TIME_ZONE)
                        .setOtherTimeZone("America/Los_Angeles")
                        .addReports(
                                ReportDefinition.newBuilder()
                                        .setId((int) REPORT_1.reportId())
                                        .setReportType(ReportType.FLEETWIDE_OCCURRENCE_COUNTS)
                                        .addSystemProfileField(SystemProfileField.APP_VERSION)
                                        .setPrivacyLevel(PrivacyLevel.LOW_PRIVACY)
                                        .setMinValue(0)
                                        .setMaxValue(0)
                                        .setNumIndexPoints(1)
                                        .setPoissonMean(0.1))
                        .setMetaData(Metadata.newBuilder().setMaxReleaseStage(ReleaseStage.DOGFOOD))
                        .build();
        mProject =
                Project.create(
                        (int) REPORT_1.customerId(), (int) REPORT_1.projectId(), List.of(metric));

        // Setup the classes.
        manualSetUp();

        // Trigger the CobaltPeriodicJob for the current day.
        mClock.set(UPLOAD_TIME);
        mPeriodicJob.generateAggregatedObservations().get();

        // Verify the observations were all removed and the last sent day index updated.
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_1))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.getObservationBatches()).isEmpty();

        // Verify the envelope containing the fabricated/participation observations was passed to
        // Clearcut.
        ImmutableList<Envelope> sentEnvelopes = mUploader.getSentEnvelopes();
        assertThat(sentEnvelopes.size()).isEqualTo(1);
        assertThat(apiKeysOf(sentEnvelopes)).containsExactly(API_KEY);

        assertThat(getObservationsIn(sentEnvelopes.get(0)))
                .containsExactly(
                        ObservationMetadata.newBuilder()
                                .setCustomerId((int) REPORT_1.customerId())
                                .setProjectId((int) REPORT_1.projectId())
                                .setMetricId((int) REPORT_1.metricId())
                                .setReportId((int) REPORT_1.reportId())
                                .setDayIndex(LOG_TIME_DAY)
                                .setSystemProfile(
                                        SystemProfile.newBuilder()
                                                .setAppVersion(APP_VERSION)
                                                .build())
                                .build(),
                        ImmutableList.of(
                                ObservationToEncrypt.newBuilder()
                                        .setObservation(
                                                Observation.newBuilder()
                                                        .setPrivateIndex(
                                                                PrivateIndexObservation.newBuilder()
                                                                        .setIndex(0))
                                                        .setRandomId(RANDOM_BYTES)
                                                        .build())
                                        .setContributionId(RANDOM_BYTES)
                                        .build(),
                                ObservationToEncrypt.newBuilder()
                                        .setObservation(
                                                Observation.newBuilder()
                                                        .setReportParticipation(
                                                                ReportParticipationObservation
                                                                        .getDefaultInstance())
                                                        .setRandomId(RANDOM_BYTES)
                                                        .build())
                                        .build()));
        assertThat(mUploader.getUploadDoneCount()).isEqualTo(1);
    }

    @Test
    public void generateAggregatedObservations_loggerDisabled_loggedDataNotUploaded()
            throws Exception {
        mEnabled = false;

        // Setup the classes.
        manualSetUp();

        // Mark a Count report as having occurred on the previous day.
        mDataService
                .aggregateCount(
                        REPORT_1,
                        LOG_TIME_DAY,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_1)
                .get();

        // Mark a Count report as having occurred on the previous day.
        mDataService
                .aggregateCount(
                        REPORT_4,
                        LOG_TIME_DAY,
                        SYSTEM_PROFILE_2,
                        EVENT_VECTOR_3,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_3)
                .get();

        // Trigger the CobaltPeriodicJob for the current day.
        mClock.set(UPLOAD_TIME);
        mPeriodicJob.generateAggregatedObservations().get();

        // Verify no observations were stored/sent and the last sent day index was NOT updated.
        assertThat(mUploader.getSentEnvelopes()).isEmpty();
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_1))
                .isEqualTo(Optional.of(LOG_TIME_DAY - 1));
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_2))
                .isEqualTo(Optional.of(LOG_TIME_DAY - 1));
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_3))
                .isEqualTo(Optional.of(LOG_TIME_DAY - 1));
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_4))
                .isEqualTo(Optional.of(LOG_TIME_DAY - 1));
        assertThat(mTestOnlyDao.getObservationBatches()).isEmpty();
        assertThat(mTestOnlyDao.getStartDisabledTime()).isEqualTo(Optional.of(UPLOAD_TIME));
    }

    @Test
    public void generateAggregatedObservations_afterMaxAggregationWindowPasses_oldDataRemoved()
            throws Exception {
        // Setup the classes.
        manualSetUp();

        // Mark a Count report as having occurred on the previous day.
        mDataService
                .aggregateCount(
                        REPORT_1,
                        LOG_TIME_DAY,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_1,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_1)
                .get();

        // Mark a Count report as having occurred on the previous day.
        mDataService
                .aggregateCount(
                        REPORT_4,
                        LOG_TIME_DAY,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_3,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_3)
                .get();

        // Trigger the CobaltPeriodicJob for the current day.
        mClock.set(UPLOAD_TIME);
        mPeriodicJob.generateAggregatedObservations().get();

        // Verify the last sent day index updated and the aggregate still exists.
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_1))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_4))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.getDayIndices()).containsExactly(LOG_TIME_DAY, LOG_TIME_DAY);

        // Trigger the CobaltPeriodicJob for a day more than 1 days later when the cleanup occurs.
        mClock.set(CLEANUP_TIME);
        mPeriodicJob.generateAggregatedObservations().get();

        // Verify the report exists with the updated last sent day index and the aggregate is
        // removed.
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_1))
                .isEqualTo(Optional.of(CLEANUP_DAY));
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_4))
                .isEqualTo(Optional.of(CLEANUP_DAY));
        assertThat(mTestOnlyDao.getAggregatedReportIds()).isEmpty();
        assertThat(mTestOnlyDao.getDayIndices()).isEmpty();
    }

    @Test
    public void
            generateAggregatedObservations_oneLoggedCountReportForMetricInLaterReleaseStage_nothingSent()
                    throws Exception {
        // Update the first report's metric to only be collected in an earlier release stage.
        MetricDefinition newMetric =
                mProject.getMetrics().get(1).toBuilder()
                        .setMetaData(
                                Metadata.newBuilder().setMaxReleaseStage(ReleaseStage.FISHFOOD))
                        .build();
        mProject =
                Project.create(
                        mProject.getCustomerId(),
                        mProject.getProjectId(),
                        List.of(
                                mProject.getMetrics().get(0),
                                newMetric,
                                mProject.getMetrics().get(2)));

        // Setup the classes.
        manualSetUp();

        // Mark a Count report as having occurred on the previous day.
        mDataService
                .aggregateCount(
                        REPORT_4,
                        LOG_TIME_DAY,
                        SYSTEM_PROFILE_2,
                        EVENT_VECTOR_3,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_3)
                .get();

        // Trigger the CobaltPeriodicJob for the current day.
        mClock.set(UPLOAD_TIME);
        mPeriodicJob.generateAggregatedObservations().get();

        // Verify no observations were stored/sent, but the uploader was told it's done.
        assertThat(mUploader.getSentEnvelopes()).isEmpty();
        assertThat(mUploader.getUploadDoneCount()).isEqualTo(1);
        assertThat(mTestOnlyDao.getReportKeys()).doesNotContain(REPORT_3);
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_1))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.getObservationBatches()).isEmpty();
    }

    @Test
    public void
            generateAggregatedObservations_oneLoggedCountReportForReportInLaterReleaseStage_nothingSent()
                    throws Exception {
        // Update the first report to only be collected in an earlier release stage.
        MetricDefinition metric = mProject.getMetrics().get(1);
        ReportDefinition newReport =
                metric.getReports(2).toBuilder().setMaxReleaseStage(ReleaseStage.FISHFOOD).build();
        mProject =
                Project.create(
                        mProject.getCustomerId(),
                        mProject.getProjectId(),
                        List.of(
                                mProject.getMetrics().get(0),
                                metric.toBuilder().setReports(2, newReport).build(),
                                mProject.getMetrics().get(2)));

        // Setup the classes.
        manualSetUp();

        // Mark a Count report as having occurred on the previous day.
        mDataService
                .aggregateCount(
                        REPORT_4,
                        LOG_TIME_DAY,
                        SYSTEM_PROFILE_1,
                        EVENT_VECTOR_3,
                        /* eventVectorBufferMax= */ 0,
                        EVENT_COUNT_3)
                .get();

        // Trigger the CobaltPeriodicJob for the current day.
        mClock.set(UPLOAD_TIME);
        mPeriodicJob.generateAggregatedObservations().get();

        // Verify no observations were stored/sent the report to exclude is removed from the
        // database.
        assertThat(mUploader.getSentEnvelopes()).isEmpty();
        assertThat(mUploader.getUploadDoneCount()).isEqualTo(1);
        assertThat(mTestOnlyDao.getReportKeys()).doesNotContain(REPORT_4);
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_1))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_2))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.queryLastSentDayIndex(REPORT_3))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.getObservationBatches()).isEmpty();
    }

    @Test
    public void generateAggregatedObservations_stringCounts_observationsSent() throws Exception {
        // Create a registry with a STRING metric with 2 STRING_COUNTS reports: one with no system
        // profile fields and the other with the app version.
        ReportDefinition simpleReport =
                ReportDefinition.newBuilder()
                        .setId(103)
                        .setReportType(ReportType.STRING_COUNTS)
                        .setPrivacyLevel(PrivacyLevel.NO_ADDED_PRIVACY)
                        .build();
        ReportDefinition systemProfileReport =
                ReportDefinition.newBuilder()
                        .setId(104)
                        .setReportType(ReportType.STRING_COUNTS)
                        .setPrivacyLevel(PrivacyLevel.NO_ADDED_PRIVACY)
                        .addSystemProfileField(SystemProfileField.APP_VERSION)
                        .build();
        MetricDefinition metric =
                MetricDefinition.newBuilder()
                        .setId(102)
                        .setMetricType(MetricType.STRING)
                        .setTimeZonePolicy(TimeZonePolicy.OTHER_TIME_ZONE)
                        .setOtherTimeZone("America/Los_Angeles")
                        .setMetaData(Metadata.newBuilder().setMaxReleaseStage(ReleaseStage.DOGFOOD))
                        .addReports(simpleReport)
                        .addReports(systemProfileReport)
                        .build();
        mProject = Project.create(/* customerId= */ 100, /* projectId= */ 101, List.of(metric));

        // Set up the main test objects.
        manualSetUp();

        ReportKey simpleReportKey =
                ReportKey.create(
                        mProject.getCustomerId(),
                        mProject.getProjectId(),
                        metric.getId(),
                        simpleReport.getId());
        ReportKey systemProfileReportKey =
                ReportKey.create(
                        mProject.getCustomerId(),
                        mProject.getProjectId(),
                        metric.getId(),
                        systemProfileReport.getId());

        // Initialize all reports as up to date for sending observations up to the previous day.
        mTestOnlyDao.insertLastSentDayIndex(simpleReportKey, LOG_TIME_DAY - 1);
        mTestOnlyDao.insertLastSentDayIndex(systemProfileReportKey, LOG_TIME_DAY - 1);

        // Log "STRING_A" to both reports for EVENT_VECTOR_1.
        {
            mDataService
                    .aggregateString(
                            simpleReportKey,
                            LOG_TIME_DAY,
                            mSystemData.filteredSystemProfile(simpleReport),
                            EVENT_VECTOR_1,
                            /* eventVectorBufferMax= */ 0,
                            /* stringBufferMax= */ 0,
                            "STRING_A")
                    .get();
            mDataService
                    .aggregateString(
                            systemProfileReportKey,
                            LOG_TIME_DAY,
                            mSystemData.filteredSystemProfile(systemProfileReport),
                            EVENT_VECTOR_1,
                            /* eventVectorBufferMax= */ 0,
                            /* stringBufferMax= */ 0,
                            "STRING_A")
                    .get();
        }

        // Log "STRING_A" to both reports for EVENT_VECTOR_2.
        {
            mDataService
                    .aggregateString(
                            simpleReportKey,
                            LOG_TIME_DAY,
                            mSystemData.filteredSystemProfile(simpleReport),
                            EVENT_VECTOR_2,
                            /* eventVectorBufferMax= */ 0,
                            /* stringBufferMax= */ 0,
                            "STRING_A")
                    .get();
            mDataService
                    .aggregateString(
                            systemProfileReportKey,
                            LOG_TIME_DAY,
                            mSystemData.filteredSystemProfile(systemProfileReport),
                            EVENT_VECTOR_2,
                            /* eventVectorBufferMax= */ 0,
                            /* stringBufferMax= */ 0,
                            "STRING_A")
                    .get();
        }

        // Log "STRING_B" to both reports for EVENT_VECTOR_1 twice.
        {
            mDataService
                    .aggregateString(
                            simpleReportKey,
                            LOG_TIME_DAY,
                            mSystemData.filteredSystemProfile(simpleReport),
                            EVENT_VECTOR_1,
                            /* eventVectorBufferMax= */ 0,
                            /* stringBufferMax= */ 0,
                            "STRING_B")
                    .get();
            mDataService
                    .aggregateString(
                            simpleReportKey,
                            LOG_TIME_DAY,
                            mSystemData.filteredSystemProfile(simpleReport),
                            EVENT_VECTOR_1,
                            /* eventVectorBufferMax= */ 0,
                            /* stringBufferMax= */ 0,
                            "STRING_B")
                    .get();

            mDataService
                    .aggregateString(
                            systemProfileReportKey,
                            LOG_TIME_DAY,
                            mSystemData.filteredSystemProfile(systemProfileReport),
                            EVENT_VECTOR_1,
                            /* eventVectorBufferMax= */ 0,
                            /* stringBufferMax= */ 0,
                            "STRING_B")
                    .get();
            mDataService
                    .aggregateString(
                            systemProfileReportKey,
                            LOG_TIME_DAY,
                            mSystemData.filteredSystemProfile(systemProfileReport),
                            EVENT_VECTOR_1,
                            /* eventVectorBufferMax= */ 0,
                            /* stringBufferMax= */ 0,
                            "STRING_B")
                    .get();
        }

        // Trigger the CobaltPeriodicJob for the current day.
        mClock.set(UPLOAD_TIME);
        mPeriodicJob.generateAggregatedObservations().get();

        // Verify the observations were all removed and the last sent day index updated.
        assertThat(mTestOnlyDao.queryLastSentDayIndex(simpleReportKey))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.queryLastSentDayIndex(systemProfileReportKey))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.getObservationBatches()).isEmpty();

        // Verify a single observation was sent to Clearcut.
        ImmutableList<Envelope> sentEnvelopes = mUploader.getSentEnvelopes();
        assertThat(sentEnvelopes).hasSize(1);
        assertThat(apiKeysOf(sentEnvelopes)).containsExactly(API_KEY);
        assertThat(mUploader.getUploadDoneCount()).isEqualTo(1);

        // The reports share metadata excluding the report id and system profile fields.
        ObservationMetadata baseMetadata =
                ObservationMetadata.newBuilder()
                        .setCustomerId(mProject.getCustomerId())
                        .setProjectId(mProject.getProjectId())
                        .setMetricId(metric.getId())
                        .setDayIndex(LOG_TIME_DAY)
                        .build();

        ByteString stringAHash =
                ByteString.copyFrom(
                        Hashing.farmHashFingerprint64().hashBytes("STRING_A".getBytes()).asBytes());
        ByteString stringBHash =
                ByteString.copyFrom(
                        Hashing.farmHashFingerprint64().hashBytes("STRING_B".getBytes()).asBytes());

        // Both reports send the same histograms. The only difference is how system profiles are
        // reported.
        StringHistogramObservation stringHistogram =
                StringHistogramObservation.newBuilder()
                        .addStringHashesFf64(stringAHash)
                        .addStringHashesFf64(stringBHash)
                        .addStringHistograms(
                                IndexHistogram.newBuilder()
                                        .addAllEventCodes(EVENT_VECTOR_1.eventCodes())
                                        // "STRING_A" was logged once.
                                        .addBucketIndices(0)
                                        .addBucketCounts(1)
                                        // "STRING_B" was logged twice.
                                        .addBucketIndices(1)
                                        .addBucketCounts(2))
                        .addStringHistograms(
                                IndexHistogram.newBuilder()
                                        .addAllEventCodes(EVENT_VECTOR_2.eventCodes())
                                        // "STRING_A" was logged once.
                                        .addBucketIndices(0)
                                        .addBucketCounts(1))
                        .build();

        assertThat(getObservationsIn(sentEnvelopes.get(0)))
                .containsExactly(
                        baseMetadata.toBuilder()
                                .setReportId(simpleReport.getId())
                                .setSystemProfile(mSystemData.filteredSystemProfile(simpleReport))
                                .build(),
                        ImmutableList.of(
                                ObservationToEncrypt.newBuilder()
                                        .setObservation(
                                                Observation.newBuilder()
                                                        .setStringHistogram(stringHistogram)
                                                        .setRandomId(RANDOM_BYTES))
                                        .setContributionId(RANDOM_BYTES)
                                        .build()),
                        baseMetadata.toBuilder()
                                .setReportId(systemProfileReport.getId())
                                .setSystemProfile(
                                        mSystemData.filteredSystemProfile(systemProfileReport))
                                .build(),
                        ImmutableList.of(
                                ObservationToEncrypt.newBuilder()
                                        .setObservation(
                                                Observation.newBuilder()
                                                        .setStringHistogram(stringHistogram)
                                                        .setRandomId(RANDOM_BYTES))
                                        .setContributionId(RANDOM_BYTES)
                                        .build()));
    }

    @Test
    public void generateAggregatedObservations_multipleMetricTypes_observationsSent()
            throws Exception {
        // Create a registry with an OCCURRENCE metric and a STRING metric. The OCCURRENCE metric
        // has a privacy enabled FLEETWIDE_OCCCURRENCE_COUNTS report and the string metric has a
        // non-private STRING_COUNTS report.
        //
        // The OCCURRENCE report is set up to trigged one fabricated observation and one report
        // participation observation.
        //
        // Neither has system profile fields set.
        ReportDefinition occurrenceReport =
                ReportDefinition.newBuilder()
                        .setId(1)
                        .setReportType(ReportType.FLEETWIDE_OCCURRENCE_COUNTS)
                        .setPrivacyLevel(PrivacyLevel.LOW_PRIVACY)
                        .setMinValue(0)
                        .setMaxValue(10)
                        .setNumIndexPoints(11)
                        .setPoissonMean(0.03)
                        .build();
        MetricDefinition occurrenceMetric =
                MetricDefinition.newBuilder()
                        .setId(102)
                        .setMetricType(MetricType.OCCURRENCE)
                        .addMetricDimensions(MetricDimension.newBuilder().putEventCodes(1, "1"))
                        .addMetricDimensions(MetricDimension.newBuilder().putEventCodes(5, "5"))
                        .setTimeZonePolicy(TimeZonePolicy.OTHER_TIME_ZONE)
                        .setOtherTimeZone("America/Los_Angeles")
                        .setMetaData(Metadata.newBuilder().setMaxReleaseStage(ReleaseStage.DOGFOOD))
                        .addReports(occurrenceReport)
                        .build();
        ReportDefinition stringReport =
                ReportDefinition.newBuilder()
                        .setId(1)
                        .setReportType(ReportType.STRING_COUNTS)
                        .setPrivacyLevel(PrivacyLevel.NO_ADDED_PRIVACY)
                        .build();
        MetricDefinition stringMetric =
                MetricDefinition.newBuilder()
                        .setId(103)
                        .setMetricType(MetricType.STRING)
                        .setTimeZonePolicy(TimeZonePolicy.OTHER_TIME_ZONE)
                        .setOtherTimeZone("America/Los_Angeles")
                        .setMetaData(Metadata.newBuilder().setMaxReleaseStage(ReleaseStage.DOGFOOD))
                        .addReports(stringReport)
                        .build();

        mProject =
                Project.create(
                        /* customerId= */ 100,
                        /* projectId= */ 101,
                        List.of(occurrenceMetric, stringMetric));

        // Set up the main test objects.
        manualSetUp();

        ReportKey occurrenceReportKey =
                ReportKey.create(
                        mProject.getCustomerId(),
                        mProject.getProjectId(),
                        occurrenceMetric.getId(),
                        occurrenceReport.getId());
        ReportKey stringReportKey =
                ReportKey.create(
                        mProject.getCustomerId(),
                        mProject.getProjectId(),
                        stringMetric.getId(),
                        stringReport.getId());

        // Initialize all reports as up to date for sending observations up to the previous day.
        mTestOnlyDao.insertLastSentDayIndex(occurrenceReportKey, LOG_TIME_DAY - 1);
        mTestOnlyDao.insertLastSentDayIndex(stringReportKey, LOG_TIME_DAY - 1);

        // Log 10 to the occurrence report.
        {
            mDataService
                    .aggregateCount(
                            occurrenceReportKey,
                            LOG_TIME_DAY,
                            mSystemData.filteredSystemProfile(occurrenceReport),
                            EVENT_VECTOR_1,
                            /* eventVectorBufferMax= */ 0,
                            /* count= */ 10)
                    .get();
        }

        // Log "STRING_A" to the string report.
        {
            mDataService
                    .aggregateString(
                            stringReportKey,
                            LOG_TIME_DAY,
                            mSystemData.filteredSystemProfile(stringReport),
                            EVENT_VECTOR_2,
                            /* eventVectorBufferMax= */ 0,
                            /* stringBufferMax= */ 0,
                            "STRING_A")
                    .get();
        }

        // Trigger the CobaltPeriodicJob for the current day.
        mClock.set(UPLOAD_TIME);
        mPeriodicJob.generateAggregatedObservations().get();

        // Verify the observations were all removed and the last sent day index updated.
        assertThat(mTestOnlyDao.queryLastSentDayIndex(occurrenceReportKey))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.queryLastSentDayIndex(stringReportKey))
                .isEqualTo(Optional.of(LOG_TIME_DAY));
        assertThat(mTestOnlyDao.getObservationBatches()).isEmpty();

        // Verify a single observation was sent to Clearcut.
        ImmutableList<Envelope> sentEnvelopes = mUploader.getSentEnvelopes();
        assertThat(sentEnvelopes).hasSize(1);
        assertThat(apiKeysOf(sentEnvelopes)).containsExactly(API_KEY);
        assertThat(mUploader.getUploadDoneCount()).isEqualTo(1);

        // The reports share metadata excluding the metric id, report id, and system profile fields.
        ObservationMetadata baseMetadata =
                ObservationMetadata.newBuilder()
                        .setCustomerId(mProject.getCustomerId())
                        .setProjectId(mProject.getProjectId())
                        .setDayIndex(LOG_TIME_DAY)
                        .build();

        ByteString stringAHash =
                ByteString.copyFrom(
                        Hashing.farmHashFingerprint64().hashBytes("STRING_A".getBytes()).asBytes());

        // Both reports send the same histograms. The only difference is how system profiles are
        // reported.
        StringHistogramObservation stringHistogram =
                StringHistogramObservation.newBuilder()
                        .addStringHashesFf64(stringAHash)
                        .addStringHistograms(
                                IndexHistogram.newBuilder()
                                        .addAllEventCodes(EVENT_VECTOR_2.eventCodes())
                                        // "STRING_A" was logged once.
                                        .addBucketIndices(0)
                                        .addBucketCounts(1))
                        .build();

        assertThat(getObservationsIn(sentEnvelopes.get(0)))
                .containsExactly(
                        baseMetadata.toBuilder()
                                .setMetricId(occurrenceMetric.getId())
                                .setReportId(occurrenceReport.getId())
                                .setSystemProfile(
                                        mSystemData.filteredSystemProfile(occurrenceReport))
                                .build(),
                        ImmutableList.of(
                                // Real observation.
                                ObservationToEncrypt.newBuilder()
                                        .setObservation(
                                                Observation.newBuilder()
                                                        .setPrivateIndex(
                                                                PrivateIndexObservation.newBuilder()
                                                                        .setIndex(10))
                                                        .setRandomId(RANDOM_BYTES))
                                        .setContributionId(RANDOM_BYTES)
                                        .build(),
                                // Fabricated observation.
                                ObservationToEncrypt.newBuilder()
                                        .setObservation(
                                                Observation.newBuilder()
                                                        .setPrivateIndex(
                                                                PrivateIndexObservation.newBuilder()
                                                                        .setIndex(9))
                                                        .setRandomId(RANDOM_BYTES))
                                        .build(),
                                // Report participation observation.
                                ObservationToEncrypt.newBuilder()
                                        .setObservation(
                                                Observation.newBuilder()
                                                        .setReportParticipation(
                                                                ReportParticipationObservation
                                                                        .getDefaultInstance())
                                                        .setRandomId(RANDOM_BYTES))
                                        .build()),
                        baseMetadata.toBuilder()
                                .setMetricId(stringMetric.getId())
                                .setReportId(stringReport.getId())
                                .setSystemProfile(mSystemData.filteredSystemProfile(stringReport))
                                .build(),
                        ImmutableList.of(
                                ObservationToEncrypt.newBuilder()
                                        .setObservation(
                                                Observation.newBuilder()
                                                        .setStringHistogram(stringHistogram)
                                                        .setRandomId(RANDOM_BYTES))
                                        .setContributionId(RANDOM_BYTES)
                                        .build()));
    }
}
