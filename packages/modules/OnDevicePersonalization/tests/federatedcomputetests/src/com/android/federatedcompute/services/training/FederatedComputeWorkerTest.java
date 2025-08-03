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

package com.android.federatedcompute.services.training;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CLIENT_PLAN_SPEC_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ISOLATED_TRAINING_PROCESS_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE;
import static com.android.federatedcompute.services.common.FileUtils.createTempFile;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.federatedcompute.aidl.IExampleStoreCallback;
import android.federatedcompute.aidl.IExampleStoreService;
import android.federatedcompute.common.ClientConstants;
import android.federatedcompute.common.ExampleConsumption;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;

import com.android.federatedcompute.services.common.Constants;
import com.android.federatedcompute.services.common.MonotonicClock;
import com.android.federatedcompute.services.common.TrainingEventLogger;
import com.android.federatedcompute.services.data.FederatedComputeDbHelper;
import com.android.federatedcompute.services.data.FederatedComputeEncryptionKey;
import com.android.federatedcompute.services.data.FederatedTrainingTask;
import com.android.federatedcompute.services.data.FederatedTrainingTaskDao;
import com.android.federatedcompute.services.data.ODPAuthorizationTokenDao;
import com.android.federatedcompute.services.data.TaskHistory;
import com.android.federatedcompute.services.data.fbs.SchedulingMode;
import com.android.federatedcompute.services.data.fbs.SchedulingReason;
import com.android.federatedcompute.services.data.fbs.TrainingConstraints;
import com.android.federatedcompute.services.data.fbs.TrainingIntervalOptions;
import com.android.federatedcompute.services.encryption.FederatedComputeEncryptionKeyManager;
import com.android.federatedcompute.services.encryption.HpkeJniEncrypter;
import com.android.federatedcompute.services.examplestore.ExampleConsumptionRecorder;
import com.android.federatedcompute.services.http.CheckinResult;
import com.android.federatedcompute.services.http.HttpFederatedProtocol;
import com.android.federatedcompute.services.scheduling.FederatedComputeJobManager;
import com.android.federatedcompute.services.security.AuthorizationContext;
import com.android.federatedcompute.services.security.KeyAttestation;
import com.android.federatedcompute.services.statsd.ClientErrorLogger;
import com.android.federatedcompute.services.testutils.FakeExampleStoreIterator;
import com.android.federatedcompute.services.testutils.TrainingTestUtil;
import com.android.federatedcompute.services.training.ResultCallbackHelper.CallbackResult;
import com.android.federatedcompute.services.training.aidl.IIsolatedTrainingService;
import com.android.federatedcompute.services.training.aidl.ITrainingResultCallback;
import com.android.federatedcompute.services.training.util.ComputationResult;
import com.android.federatedcompute.services.training.util.TrainingConditionsChecker;
import com.android.federatedcompute.services.training.util.TrainingConditionsChecker.Condition;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.flatbuffers.FlatBufferBuilder;
import com.google.intelligence.fcp.client.FLRunnerResult;
import com.google.intelligence.fcp.client.FLRunnerResult.ContributionResult;
import com.google.intelligence.fcp.client.RetryInfo;
import com.google.intelligence.fcp.client.engine.TaskRetry;
import com.google.internal.federated.plan.ClientOnlyPlan;
import com.google.internal.federated.plan.ClientPhase;
import com.google.internal.federated.plan.TensorflowSpec;
import com.google.internal.federatedcompute.v1.AuthenticationMetadata;
import com.google.internal.federatedcompute.v1.KeyAttestationAuthMetadata;
import com.google.internal.federatedcompute.v1.RejectionInfo;
import com.google.internal.federatedcompute.v1.RetryWindow;
import com.google.ondevicepersonalization.federatedcompute.proto.CreateTaskAssignmentResponse;
import com.google.ondevicepersonalization.federatedcompute.proto.EligibilityPolicyEvalSpec;
import com.google.ondevicepersonalization.federatedcompute.proto.EligibilityTaskInfo;
import com.google.ondevicepersonalization.federatedcompute.proto.MinimumSeparationPolicy;
import com.google.ondevicepersonalization.federatedcompute.proto.TaskAssignment;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.quality.Strictness;
import org.tensorflow.example.BytesList;
import org.tensorflow.example.Example;
import org.tensorflow.example.Feature;
import org.tensorflow.example.Features;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutionException;

@RunWith(JUnit4.class)
@MockStatic(ClientErrorLogger.class)
public final class FederatedComputeWorkerTest {

    @Rule
    public final ExtendedMockitoRule extendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    private static final int JOB_ID = 1234;
    private static final String POPULATION_NAME = "barPopulation";
    private static final String TASK_ID = "task-id";
    private static final long CREATION_TIME_MS = 10000L;
    private static final long TASK_EARLIEST_NEXT_RUN_TIME_MS = 1234567L;
    private static final String PACKAGE_NAME = "com.android.federatedcompute.services.training";
    private static final String OWNER_ID = "com.android.pckg.name/com.android.class.name";
    private static final String OWNER_ID_CERT_DIGEST = "123SOME45DIGEST78";
    private static final String SERVER_ADDRESS = "https://server.com/";
    private static final byte[] DEFAULT_TRAINING_CONSTRAINTS =
            createTrainingConstraints(true, true, true);

    private static final TaskRetry TASK_RETRY =
            TaskRetry.newBuilder().setRetryToken("foobar").build();

    private static final MinimumSeparationPolicy MIN_SEP_POLICY =
            MinimumSeparationPolicy.newBuilder()
                    .setMinimumSeparation(6)
                    .setCurrentIndex(10)
                    .build();
    private static final EligibilityTaskInfo ELIGIBILITY_TASK_INFO =
            EligibilityTaskInfo.newBuilder()
                    .addEligibilityPolicies(
                            EligibilityPolicyEvalSpec.newBuilder()
                                    .setMinSepPolicy(MIN_SEP_POLICY)
                                    .build())
                    .build();
    private static final TaskAssignment TASK_ASSIGNMENT =
            TaskAssignment.newBuilder()
                    .setTaskId(TASK_ID)
                    .setEligibilityTaskInfo(ELIGIBILITY_TASK_INFO)
                    .build();
    private static final CheckinResult FL_CHECKIN_RESULT =
            new CheckinResult(
                    createTempFile("input", ".ckp"),
                    TrainingTestUtil.createFakeFederatedLearningClientPlan(),
                    TASK_ASSIGNMENT);

    private static final CheckinResult FA_CHECKIN_RESULT =
            new CheckinResult(
                    createTempFile("input", ".ckp"),
                    TrainingTestUtil.createFederatedAnalyticClientPlan(),
                    TaskAssignment.newBuilder().setTaskId(TASK_ID).build());

    public static final RejectionInfo RETRY_REJECTION_INFO =
            RejectionInfo.newBuilder()
                    .setRetryWindow(
                            RetryWindow.newBuilder()
                                    .setDelayMin(Duration.newBuilder().setSeconds(3600).build())
                                    .build())
                    .build();
    private static final CheckinResult RETRY_REJECTION_CHECKIN_RESULT =
            new CheckinResult(RETRY_REJECTION_INFO);

    private static final byte[] CHALLENGE =
            ("AHXUDhoSEFikqOefmo8xE7kGp/xjVMRDYBecBiHGxCN8rTv9W0Z4L/14d0OLB"
                            + "vC1VVzXBAnjgHoKLZzuJifTOaBJwGNIQ2ejnx3n6ayoRchDNCgpK29T+EAhBWzH")
                    .getBytes();

    private static final RejectionInfo UNAUTHENTICATED_REJECTION_INFO =
            RejectionInfo.newBuilder()
                    .setAuthMetadata(
                            AuthenticationMetadata.newBuilder()
                                    .setKeyAttestationMetadata(
                                            KeyAttestationAuthMetadata.newBuilder()
                                                    .setChallenge(ByteString.copyFrom(CHALLENGE))
                                                    .build())
                                    .build())
                    .build();
    private static final CreateTaskAssignmentResponse CREATE_TASK_ASSIGNMENT_REJECTION_RESPONSE =
            CreateTaskAssignmentResponse.newBuilder()
                    .setRejectionInfo(RETRY_REJECTION_INFO)
                    .build();
    private static final CreateTaskAssignmentResponse CREATE_TASK_ASSIGNMENT_RESPONSE =
            CreateTaskAssignmentResponse.newBuilder().setTaskAssignment(TASK_ASSIGNMENT).build();
    private static final CreateTaskAssignmentResponse
            CREATE_TASK_ASSIGNMENT_RESPONSE_UNAUTHENTICATED_REJECTION =
                    CreateTaskAssignmentResponse.newBuilder()
                            .setRejectionInfo(UNAUTHENTICATED_REJECTION_INFO)
                            .build();

    private static final FLRunnerResult FL_RUNNER_FAILURE_RESULT =
            FLRunnerResult.newBuilder()
                    .setContributionResult(ContributionResult.FAIL)
                    .setErrorStatus(FLRunnerResult.ErrorStatus.INVALID_ARGUMENT)
                    .build();

    private static final FLRunnerResult FL_RUNNER_SUCCESS_RESULT =
            FLRunnerResult.newBuilder()
                    .setContributionResult(ContributionResult.SUCCESS)
                    .setRetryInfo(
                            RetryInfo.newBuilder()
                                    .setRetryToken(TASK_RETRY.getRetryToken())
                                    .build())
                    .build();
    private static final byte[] INTERVAL_OPTIONS = createDefaultTrainingIntervalOptions();
    private static final FederatedTrainingTask FEDERATED_TRAINING_TASK_1 =
            FederatedTrainingTask.builder()
                    .appPackageName(PACKAGE_NAME)
                    .creationTime(CREATION_TIME_MS)
                    .lastScheduledTime(TASK_EARLIEST_NEXT_RUN_TIME_MS)
                    .serverAddress(SERVER_ADDRESS)
                    .populationName(POPULATION_NAME)
                    .jobId(JOB_ID)
                    .ownerId(OWNER_ID)
                    .ownerIdCertDigest(OWNER_ID_CERT_DIGEST)
                    .intervalOptions(INTERVAL_OPTIONS)
                    .constraints(DEFAULT_TRAINING_CONSTRAINTS)
                    .earliestNextRunTime(TASK_EARLIEST_NEXT_RUN_TIME_MS)
                    .schedulingReason(SchedulingReason.SCHEDULING_REASON_NEW_TASK)
                    .build();
    private static final Example EXAMPLE_PROTO_1 =
            Example.newBuilder()
                    .setFeatures(
                            Features.newBuilder()
                                    .putFeature(
                                            "feature1",
                                            Feature.newBuilder()
                                                    .setBytesList(
                                                            BytesList.newBuilder()
                                                                    .addValue(
                                                                            ByteString.copyFromUtf8(
                                                                                    "f1_value1")))
                                                    .build()))
                    .build();
    private static final Any FAKE_CRITERIA = Any.newBuilder().setTypeUrl("baz.com").build();
    private static final ExampleConsumption EXAMPLE_CONSUMPTION_1 =
            new ExampleConsumption.Builder()
                    .setTaskId(TASK_ID)
                    .setSelectionCriteria(FAKE_CRITERIA.toByteArray())
                    .setExampleCount(100)
                    .build();
    @Mock TrainingConditionsChecker mTrainingConditionsChecker;
    @Mock FederatedComputeJobManager mMockJobManager;
    private Context mContext;
    private FederatedComputeWorker mSpyWorker;
    private HttpFederatedProtocol mSpyHttpFederatedProtocol;
    @Mock private ComputationRunner mMockComputationRunner;

    @Mock private TrainingEventLogger mMockTrainingEventLogger;
    private ResultCallbackHelper mSpyResultCallbackHelper;
    private FederatedTrainingTaskDao mTrainingTaskDao;

    @Mock private FederatedComputeEncryptionKeyManager mMockKeyManager;

    private static KeyAttestation sSpyKeyAttestation;

    @Mock private ClientErrorLogger mMockClientErrorLogger;

    @Mock private FederatedJobService.OnJobFinishedCallback mMockJobServiceOnFinishCallback;

    private static final FederatedComputeEncryptionKey ENCRYPTION_KEY =
            new FederatedComputeEncryptionKey.Builder()
                    .setPublicKey("rSJBSUYG0ebvfW1AXCWO0CMGMJhDzpfQm3eLyw1uxX8=")
                    .setKeyIdentifier("0962201a-5abd-4e25-a486-2c7bd1ee1887")
                    .setKeyType(FederatedComputeEncryptionKey.KEY_TYPE_ENCRYPTION)
                    .setCreationTime(1L)
                    .setExpiryTime(1L)
                    .build();

    private static byte[] createTrainingConstraints(
            boolean requiresSchedulerIdle,
            boolean requiresSchedulerBatteryNotLow,
            boolean requiresSchedulerUnmeteredNetwork) {
        FlatBufferBuilder builder = new FlatBufferBuilder();
        builder.finish(
                TrainingConstraints.createTrainingConstraints(
                        builder,
                        requiresSchedulerIdle,
                        requiresSchedulerBatteryNotLow,
                        requiresSchedulerUnmeteredNetwork));
        return builder.sizedByteArray();
    }

    private static byte[] createDefaultTrainingIntervalOptions() {
        FlatBufferBuilder builder = new FlatBufferBuilder();
        builder.finish(
                TrainingIntervalOptions.createTrainingIntervalOptions(
                        builder, SchedulingMode.ONE_TIME, 0));
        return builder.sizedByteArray();
    }

    @Before
    public void doBeforeEachTest() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();
        when(ClientErrorLogger.getInstance()).thenReturn(mMockClientErrorLogger);
        mSpyHttpFederatedProtocol =
                spy(
                        HttpFederatedProtocol.create(
                                SERVER_ADDRESS,
                                "349990000",
                                POPULATION_NAME,
                                new HpkeJniEncrypter(),
                                mMockTrainingEventLogger));
        mSpyResultCallbackHelper = spy(new ResultCallbackHelper(mContext));
        mTrainingTaskDao = FederatedTrainingTaskDao.getInstanceForTest(mContext);
        sSpyKeyAttestation = spy(KeyAttestation.getInstance(mContext));
        mSpyWorker =
                spy(
                        new FederatedComputeWorker(
                                mContext,
                                mMockJobManager,
                                mTrainingConditionsChecker,
                                mMockComputationRunner,
                                mSpyResultCallbackHelper,
                                mMockKeyManager,
                                new TestInjector()));
        when(mTrainingConditionsChecker.checkAllConditionsForFlTraining(any()))
                .thenReturn(EnumSet.noneOf(Condition.class));
        doReturn(Futures.immediateFuture(CallbackResult.SUCCESS))
                .when(mSpyResultCallbackHelper)
                .callHandleResult(eq(TASK_ID), any(), any());
        when(mMockJobManager.onTrainingStarted(anyInt())).thenReturn(FEDERATED_TRAINING_TASK_1);
        when(mMockComputationRunner.runTaskWithNativeRunner(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(FL_RUNNER_SUCCESS_RESULT);
        doReturn(List.of(ENCRYPTION_KEY))
                .when(mMockKeyManager)
                .getOrFetchActiveKeys(anyInt(), anyInt());
    }

    @After
    public void tearDown() {
        FederatedComputeDbHelper dbHelper = FederatedComputeDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }

    @Test
    public void testJobNonExist_returnsFail() throws Exception {
        when(mMockJobManager.onTrainingStarted(anyInt())).thenReturn(null);

        FLRunnerResult result =
                mSpyWorker.startTrainingRun(JOB_ID, mMockJobServiceOnFinishCallback).get();

        assertNull(result);
        verify(mMockJobManager, never())
                .onTrainingCompleted(eq(JOB_ID), eq(POPULATION_NAME), any(), any(), any());
        verify(mMockJobServiceOnFinishCallback).callJobFinished(eq(false));
    }

    @Test
    public void testTrainingConditionsCheckFailed_returnsFail() throws Exception {
        when(mTrainingConditionsChecker.checkAllConditionsForFlTraining(any()))
                .thenReturn(ImmutableSet.of(Condition.BATTERY_NOT_OK));

        FLRunnerResult result =
                mSpyWorker.startTrainingRun(JOB_ID, mMockJobServiceOnFinishCallback).get();

        assertNull(result);
        verify(mMockJobManager)
                .onTrainingCompleted(eq(JOB_ID), eq(POPULATION_NAME), any(), any(), any());
        verify(mMockTrainingEventLogger).logTaskNotStarted();
        verify(mMockJobServiceOnFinishCallback).callJobFinished(eq(false));
    }

    @Test
    public void testCheckinFails_throwsException() throws Exception {
        setUpExampleStoreService();

        doReturn(
                        FluentFuture.from(
                                immediateFailedFuture(
                                        new ExecutionException(
                                                "issue checkin failed",
                                                new IllegalStateException("http 404")))))
                .when(mSpyHttpFederatedProtocol)
                .createTaskAssignment(any());
        doReturn(FluentFuture.from(immediateFuture(null)))
                .when(mSpyHttpFederatedProtocol)
                .reportResult(any(), any(), any());

        assertThrows(
                ExecutionException.class,
                () -> mSpyWorker.startTrainingRun(JOB_ID, mMockJobServiceOnFinishCallback).get());

        verify(mMockJobServiceOnFinishCallback, never()).callJobFinished(eq(false));
        mSpyWorker.finish(null, ContributionResult.FAIL, false);
        verify(mMockJobManager)
                .onTrainingCompleted(
                        anyInt(), anyString(), any(), any(), eq(ContributionResult.FAIL));
        verify(mMockJobServiceOnFinishCallback).callJobFinished(eq(false));
    }

    @Test
    public void testCheckinWithRetryRejection() throws Exception {
        setUpExampleStoreService();
        doReturn(FluentFuture.from(immediateFuture(CREATE_TASK_ASSIGNMENT_REJECTION_RESPONSE)))
                .when(mSpyHttpFederatedProtocol)
                .createTaskAssignment(any());

        FLRunnerResult result =
                mSpyWorker
                        .startTrainingRun(JOB_ID, mMockJobServiceOnFinishCallback)
                        .get();

        assertNull(result);
        verify(mMockJobManager)
                .onTrainingCompleted(
                        anyInt(), anyString(), any(), any(), eq(ContributionResult.FAIL));
        verify(mMockJobServiceOnFinishCallback).callJobFinished(eq(false));
        mSpyWorker.finish(null, ContributionResult.FAIL, false);
    }

    @Test
    public void testCheckinWithUnAuthRejection_fails() {
        setUpExampleStoreService();
        // Always return Unauthenticated during checkin. The second request with auth will fail.
        doReturn(
                        FluentFuture.from(
                                immediateFuture(
                                        CREATE_TASK_ASSIGNMENT_RESPONSE_UNAUTHENTICATED_REJECTION)))
                .when(mSpyHttpFederatedProtocol)
                .createTaskAssignment(any());

        // The second auth request will throw exception as http status 401 is not allowed.
        assertThrows(
                ExecutionException.class,
                () -> mSpyWorker.startTrainingRun(JOB_ID, mMockJobServiceOnFinishCallback).get());

        // verify two issueCheckin calls.
        verify(mSpyHttpFederatedProtocol, times(2)).createTaskAssignment(any());
        mSpyWorker.finish(null, ContributionResult.FAIL, false);
        verify(mMockJobServiceOnFinishCallback).callJobFinished(eq(false));
    }

    @Test
    public void testCheckinWithUnAuthRejection_success() throws Exception {
        setUpExampleStoreService();
        doReturn(FluentFuture.from(immediateFuture(null)))
                .when(mSpyHttpFederatedProtocol)
                .reportResult(any(), any(), any());
        doReturn(immediateFuture(FL_CHECKIN_RESULT))
                .when(mSpyHttpFederatedProtocol)
                .downloadTaskAssignment(any());
        // When allowing unauthenticated, return 401 UNAUTHENTICATED rejection info and return
        // successful checkin result.
        doReturn(
                        FluentFuture.from(
                                immediateFuture(
                                        CREATE_TASK_ASSIGNMENT_RESPONSE_UNAUTHENTICATED_REJECTION)))
                .doReturn(FluentFuture.from(immediateFuture(CREATE_TASK_ASSIGNMENT_RESPONSE)))
                .when(mSpyHttpFederatedProtocol)
                .createTaskAssignment(any());

        doReturn(new FakeIsolatedTrainingService()).when(mSpyWorker).getIsolatedTrainingService();
        doNothing().when(mSpyWorker).unbindFromIsolatedTrainingService();

        FLRunnerResult result =
                mSpyWorker.startTrainingRun(JOB_ID, mMockJobServiceOnFinishCallback).get();
        mSpyWorker.finish(result);

        verify(mMockJobServiceOnFinishCallback, never()).callJobFinished(eq(false));
        // succeed
        assertNotNull(result);
        // Verify first issueCheckin call.
        verify(mSpyHttpFederatedProtocol, times(2)).createTaskAssignment(any());
        // After the first issueCheckin, the FederatedComputeWorker would do the key attestation.
        verify(sSpyKeyAttestation).generateAttestationRecord(eq(CHALLENGE), anyString());
        assertThat(result.getContributionResult()).isEqualTo(ContributionResult.SUCCESS);
        verify(mMockJobManager)
                .onTrainingCompleted(
                        anyInt(), anyString(), any(), any(), eq(ContributionResult.SUCCESS));
        verify(mSpyWorker).unbindFromIsolatedTrainingService();
        verify(mSpyWorker).unbindFromExampleStoreService();
        verify(mMockJobServiceOnFinishCallback).callJobFinished(eq(true));
    }

    @Test
    public void testReportResultWithRejection() throws Exception {
        setUpExampleStoreService();
        setUpIssueCheckin(FA_CHECKIN_RESULT);
        doReturn(FluentFuture.from(immediateFuture(RETRY_REJECTION_INFO)))
                .when(mSpyHttpFederatedProtocol)
                .reportResult(any(), any(), any());
        doCallRealMethod().when(mSpyResultCallbackHelper).callHandleResult(any(), any(), any());
        ArgumentCaptor<ComputationResult> computationResultCaptor =
                ArgumentCaptor.forClass(ComputationResult.class);

        FLRunnerResult result =
                mSpyWorker.startTrainingRun(JOB_ID, mMockJobServiceOnFinishCallback).get();

        verify(mMockJobServiceOnFinishCallback).callJobFinished(eq(false));
        assertNull(result);
        verify(mMockJobManager)
                .onTrainingCompleted(
                        anyInt(), anyString(), any(), any(), eq(ContributionResult.FAIL));
        verify(mSpyResultCallbackHelper)
                .callHandleResult(any(), any(), computationResultCaptor.capture());
        ComputationResult computationResult = computationResultCaptor.getValue();
        assertNotNull(computationResult.getFlRunnerResult());
        assertEquals(
                ContributionResult.FAIL,
                computationResult.getFlRunnerResult().getContributionResult());
        mSpyWorker.finish(null, ContributionResult.FAIL, false);
    }

    @Test
    public void testReportResultFails_throwsException() throws Exception {
        setUpExampleStoreService();
        setUpIssueCheckin(FA_CHECKIN_RESULT);
        doReturn(
                        FluentFuture.from(
                                immediateFailedFuture(
                                        new ExecutionException(
                                                "report result failed",
                                                new IllegalStateException("http 404")))))
                .when(mSpyHttpFederatedProtocol)
                .reportResult(any(), any(), any());

        assertThrows(
                ExecutionException.class,
                () -> mSpyWorker.startTrainingRun(JOB_ID, mMockJobServiceOnFinishCallback).get());

        verify(mMockJobServiceOnFinishCallback, never()).callJobFinished(eq(false));
        mSpyWorker.finish(null, ContributionResult.FAIL, false);
        verify(mMockJobManager)
                .onTrainingCompleted(
                        anyInt(), anyString(), any(), any(), eq(ContributionResult.FAIL));
        verify(mSpyWorker).unbindFromExampleStoreService();
    }

    @Test
    public void testReportResultUnauthenticated_throws() throws Exception {
        setUpExampleStoreService();
        setUpIssueCheckin(FA_CHECKIN_RESULT);
        doReturn(FluentFuture.from(immediateFuture(UNAUTHENTICATED_REJECTION_INFO)))
                .when(mSpyHttpFederatedProtocol)
                .reportResult(any(), any(), any());

        // the second call to reportResultWithAuthentication would throw exception
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mSpyWorker
                                        .startTrainingRun(JOB_ID, mMockJobServiceOnFinishCallback)
                                        .get());

        assertThat(exception.getCause())
                .hasMessageThat()
                .contains(
                        "Unknown rejection Info from FCP server when "
                                + "solving authentication challenge");
    }

    @Test
    public void testReportResultWithUnAuthRejection_success() throws Exception {
        setUpExampleStoreService();
        setUpIssueCheckin(FL_CHECKIN_RESULT);
        // Return 401 UNAUTHENTICATED rejection info and then return successful checkin result.
        doReturn(FluentFuture.from(immediateFuture(UNAUTHENTICATED_REJECTION_INFO)))
                .doReturn(FluentFuture.from(immediateFuture(null)))
                .when(mSpyHttpFederatedProtocol)
                .reportResult(any(), any(), any());
        doReturn(new FakeIsolatedTrainingService()).when(mSpyWorker).getIsolatedTrainingService();
        doNothing().when(mSpyWorker).unbindFromIsolatedTrainingService();

        FLRunnerResult result =
                mSpyWorker.startTrainingRun(JOB_ID, mMockJobServiceOnFinishCallback).get();
        mSpyWorker.finish(result);

        // succeed
        assertNotNull(result);
        // Verify two reportResult calls.
        verify(mSpyHttpFederatedProtocol, times(2)).reportResult(any(), any(), any());
        // After the first reportResult, the FederatedComputeWorker would do the key attestation.
        verify(sSpyKeyAttestation).generateAttestationRecord(eq(CHALLENGE), anyString());
        assertThat(result.getContributionResult()).isEqualTo(ContributionResult.SUCCESS);
        verify(mMockJobManager)
                .onTrainingCompleted(
                        anyInt(), anyString(), any(), any(), eq(ContributionResult.SUCCESS));
        verify(mSpyWorker).unbindFromIsolatedTrainingService();
        verify(mSpyWorker).unbindFromExampleStoreService();
    }

    @Test
    public void testBindToExampleStoreFails_throwsException() throws Exception {
        ArgumentCaptor<ComputationResult> computationResultCaptor =
                ArgumentCaptor.forClass(ComputationResult.class);
        setUpHttpFederatedProtocol(FL_CHECKIN_RESULT);
        // Mock failure bind to ExampleStoreService.
        doReturn(null).when(mSpyWorker).getExampleStoreService(anyString());

        assertThrows(
                ExecutionException.class,
                () -> mSpyWorker.startTrainingRun(JOB_ID, mMockJobServiceOnFinishCallback).get());
        mSpyWorker.finish(null, ContributionResult.FAIL, false);

        verify(mMockJobManager)
                .onTrainingCompleted(
                        anyInt(), anyString(), any(), any(), eq(ContributionResult.FAIL));
        verify(mSpyWorker, times(0)).unbindFromExampleStoreService();
        verify(mSpyWorker, times(1))
                .reportFailureResultToServer(computationResultCaptor.capture(), any());
        ComputationResult computationResult = computationResultCaptor.getValue();
        assertNotNull(computationResult.getFlRunnerResult());
        assertEquals(
                ContributionResult.FAIL,
                computationResult.getFlRunnerResult().getContributionResult());
    }

    @Test
    public void testRunFAComputationReturnsFailResult() throws Exception {
        setUpExampleStoreService();
        setUpHttpFederatedProtocol(FA_CHECKIN_RESULT);

        // Mock return failed runner result from native fcp client.
        when(mMockComputationRunner.runTaskWithNativeRunner(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(FL_RUNNER_FAILURE_RESULT);

        FLRunnerResult result =
                mSpyWorker.startTrainingRun(JOB_ID, mMockJobServiceOnFinishCallback).get();
        assertThat(result.getContributionResult()).isEqualTo(ContributionResult.FAIL);

        mSpyWorker.finish(result);
        verify(mMockJobManager)
                .onTrainingCompleted(
                        anyInt(), anyString(), any(), any(), eq(ContributionResult.FAIL));
        verify(mSpyWorker).unbindFromExampleStoreService();
        verify(mMockTrainingEventLogger).logComputationInvalidArgument(any());
    }

    @Test
    public void testRunFAComputationThrows() throws Exception {
        setUpExampleStoreService();
        setUpHttpFederatedProtocol(FA_CHECKIN_RESULT);
        setUpReportFailureToServerCallback();
        doReturn(FluentFuture.from(immediateFuture(null)))
                .when(mSpyHttpFederatedProtocol)
                .reportResult(any(), any(), any());
        when(mMockComputationRunner.runTaskWithNativeRunner(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenThrow(new RuntimeException("Test failures!"));
        ArgumentCaptor<ComputationResult> computationResultCaptor =
                ArgumentCaptor.forClass(ComputationResult.class);

        assertThrows(
                ExecutionException.class,
                () -> mSpyWorker.startTrainingRun(JOB_ID, mMockJobServiceOnFinishCallback).get());
        mSpyWorker.finish(null, ContributionResult.FAIL, false);

        verify(mMockJobManager)
                .onTrainingCompleted(
                        anyInt(), anyString(), any(), any(), eq(ContributionResult.FAIL));
        verify(mSpyWorker).unbindFromExampleStoreService();
        verify(mSpyWorker, times(1))
                .reportFailureResultToServer(computationResultCaptor.capture(), any());
        ComputationResult computationResult = computationResultCaptor.getValue();
        assertNotNull(computationResult.getFlRunnerResult());
        assertEquals(
                ContributionResult.FAIL,
                computationResult.getFlRunnerResult().getContributionResult());
    }

    @Test
    public void testPublishToResultHandlingServiceFails_returnsSuccess() throws Exception {
        setUpExampleStoreService();
        setUpHttpFederatedProtocol(FA_CHECKIN_RESULT);

        // Mock publish to ResultHandlingService fails which is best effort and should not affect
        // final result.
        doReturn(Futures.immediateFuture(CallbackResult.FAIL))
                .when(mSpyResultCallbackHelper)
                .callHandleResult(eq(TASK_ID), any(), any());

        FLRunnerResult result =
                mSpyWorker.startTrainingRun(JOB_ID, mMockJobServiceOnFinishCallback).get();
        assertThat(result.getContributionResult()).isEqualTo(ContributionResult.SUCCESS);

        mSpyWorker.finish(result);
        verify(mMockJobManager)
                .onTrainingCompleted(
                        anyInt(), anyString(), any(), any(), eq(ContributionResult.SUCCESS));
        verify(mSpyWorker).unbindFromExampleStoreService();
    }

    @Test
    public void testPublishToResultHandlingServiceThrowsException_returnsSuccess()
            throws Exception {
        setUpExampleStoreService();
        setUpHttpFederatedProtocol(FA_CHECKIN_RESULT);

        // Mock publish to ResultHandlingService throws exception which is best effort and should
        // not affect final result.
        doReturn(
                        immediateFailedFuture(
                                new ExecutionException(
                                        "ResultHandlingService fail",
                                        new IllegalStateException("can't bind to service"))))
                .when(mSpyResultCallbackHelper)
                .callHandleResult(eq(TASK_ID), any(), any());

        FLRunnerResult result =
                mSpyWorker.startTrainingRun(JOB_ID, mMockJobServiceOnFinishCallback).get();
        assertThat(result.getContributionResult()).isEqualTo(ContributionResult.SUCCESS);

        mSpyWorker.finish(result);
        verify(mMockJobManager)
                .onTrainingCompleted(
                        anyInt(), anyString(), any(), any(), eq(ContributionResult.SUCCESS));
        verify(mSpyWorker).unbindFromExampleStoreService();
        verify(mSpyResultCallbackHelper).callHandleResult(eq(TASK_ID), any(), any());
    }

    @Test
    public void testRunFAComputation_returnsSuccess() throws Exception {
        setUpExampleStoreService();
        setUpHttpFederatedProtocol(FA_CHECKIN_RESULT);

        FLRunnerResult result =
                mSpyWorker.startTrainingRun(JOB_ID, mMockJobServiceOnFinishCallback).get();
        assertThat(result.getContributionResult()).isEqualTo(ContributionResult.SUCCESS);

        mSpyWorker.finish(result);
        verify(mMockJobManager).onTrainingCompleted(anyInt(), anyString(), any(), any(), any());
        verify(mMockTrainingEventLogger).logComputationCompleted(any());
    }

    @Test
    public void testBindToIsolatedTrainingServiceFail_returnsFail() throws Exception {
        setUpHttpFederatedProtocol(FL_CHECKIN_RESULT);
        setUpExampleStoreService();

        // Mock failure bind to IsolatedTrainingService.
        doReturn(null).when(mSpyWorker).getIsolatedTrainingService();
        doNothing().when(mSpyWorker).unbindFromIsolatedTrainingService();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mSpyWorker
                                        .startTrainingRun(JOB_ID, mMockJobServiceOnFinishCallback)
                                        .get());
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
        assertThat(exception.getCause())
                .hasMessageThat()
                .isEqualTo("Could not bind to IsolatedTrainingService");

        mSpyWorker.finish(null, ContributionResult.FAIL, false);
        verify(mMockJobManager)
                .onTrainingCompleted(
                        anyInt(), anyString(), any(), any(), eq(ContributionResult.FAIL));
        verify(mMockClientErrorLogger)
                .logErrorWithExceptionInfo(
                        any(IllegalStateException.class),
                        eq(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ISOLATED_TRAINING_PROCESS_ERROR),
                        eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE));
    }

    @Test
    public void testRunFLComputation_emptyTfliteGraph_returns() throws Exception {
        setUpExampleStoreService();
        TensorflowSpec tensorflowSpec =
                TensorflowSpec.newBuilder()
                        .setDatasetTokenTensorName("dataset")
                        .addTargetNodeNames("target")
                        .build();
        ClientOnlyPlan clientOnlyPlan =
                ClientOnlyPlan.newBuilder()
                        .setPhase(
                                ClientPhase.newBuilder().setTensorflowSpec(tensorflowSpec).build())
                        .build();
        CheckinResult checkinResultNoTfliteGraph =
                new CheckinResult(
                        createTempFile("input", ".ckp"),
                        clientOnlyPlan,
                        TaskAssignment.newBuilder().setTaskId(TASK_ID).build());
        setUpHttpFederatedProtocol(checkinResultNoTfliteGraph);

        // Mock bind to IsolatedTrainingService.
        doReturn(new FakeIsolatedTrainingService()).when(mSpyWorker).getIsolatedTrainingService();
        doNothing().when(mSpyWorker).unbindFromIsolatedTrainingService();

        assertThrows(
                ExecutionException.class,
                () -> mSpyWorker.startTrainingRun(JOB_ID, mMockJobServiceOnFinishCallback).get());

        mSpyWorker.finish(null, ContributionResult.FAIL, false);
        verify(mMockJobManager)
                .onTrainingCompleted(
                        anyInt(), anyString(), any(), any(), eq(ContributionResult.FAIL));
        verify(mMockClientErrorLogger)
                .logErrorWithExceptionInfo(
                        any(IllegalStateException.class),
                        eq(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CLIENT_PLAN_SPEC_ERROR),
                        eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE));
    }

    @Test
    public void testRunFLComputation_returnsSuccess() throws Exception {
        setUpExampleStoreService();
        setUpHttpFederatedProtocol(FL_CHECKIN_RESULT);

        // Mock bind to IsolatedTrainingService.
        doReturn(new FakeIsolatedTrainingService()).when(mSpyWorker).getIsolatedTrainingService();
        doNothing().when(mSpyWorker).unbindFromIsolatedTrainingService();

        FLRunnerResult result =
                mSpyWorker.startTrainingRun(JOB_ID, mMockJobServiceOnFinishCallback).get();
        assertThat(result.getContributionResult()).isEqualTo(ContributionResult.SUCCESS);

        mSpyWorker.finish(result);
        verify(mMockJobManager)
                .onTrainingCompleted(
                        anyInt(), anyString(), any(), any(), eq(ContributionResult.SUCCESS));
        verify(mSpyWorker).unbindFromIsolatedTrainingService();
        verify(mSpyWorker).unbindFromExampleStoreService();
        verify(mMockTrainingEventLogger).logComputationCompleted(any());
    }

    @Test
    public void testRunFLComputationNotEligible_returnsFail() throws Exception {
        mTrainingTaskDao.updateOrInsertTaskHistory(
                new TaskHistory.Builder()
                        .setJobId(JOB_ID)
                        .setTaskId(TASK_ID)
                        .setPopulationName(POPULATION_NAME)
                        .setContributionRound(9)
                        .setContributionTime(120L)
                        .build());
        setUpExampleStoreService();
        setUpIssueCheckin(FL_CHECKIN_RESULT);
        ArgumentCaptor<ComputationResult> captor = ArgumentCaptor.forClass(ComputationResult.class);
        doReturn(FluentFuture.from(immediateFuture(null)))
                .when(mSpyHttpFederatedProtocol)
                .reportResult(captor.capture(), eq(null), any());

        FLRunnerResult result =
                mSpyWorker.startTrainingRun(JOB_ID, mMockJobServiceOnFinishCallback).get();
        assertNull(result);
        ComputationResult actualResult = captor.getValue();
        assertThat(actualResult.getFlRunnerResult().getErrorStatus())
                .isEqualTo(FLRunnerResult.ErrorStatus.NOT_ELIGIBLE);
        // If device is not eligible, it should not download resources.
        verify(mSpyHttpFederatedProtocol, never()).downloadTaskAssignment(any());
    }

    @Test
    public void testRunFLComputationEligible_returnsSuccess() throws Exception {
        mTrainingTaskDao.updateOrInsertTaskHistory(
                new TaskHistory.Builder()
                        .setJobId(JOB_ID)
                        .setTaskId(TASK_ID)
                        .setPopulationName(POPULATION_NAME)
                        .setContributionRound(1)
                        .setContributionTime(120L)
                        .build());
        setUpExampleStoreService();
        setUpHttpFederatedProtocol(FL_CHECKIN_RESULT);

        // Mock bind to IsolatedTrainingService.
        doReturn(new FakeIsolatedTrainingService()).when(mSpyWorker).getIsolatedTrainingService();
        doNothing().when(mSpyWorker).unbindFromIsolatedTrainingService();

        FLRunnerResult result =
                mSpyWorker.startTrainingRun(JOB_ID, mMockJobServiceOnFinishCallback).get();
        assertThat(result.getContributionResult()).isEqualTo(ContributionResult.SUCCESS);
        verify(mSpyHttpFederatedProtocol).downloadTaskAssignment(any());
    }

    @Test
    public void testRunFLComputation_noKey_throws() throws Exception {
        setUpHttpFederatedProtocol(FL_CHECKIN_RESULT);
        doReturn(new ArrayList<FederatedComputeEncryptionKey>() {})
                .when(mMockKeyManager)
                .getOrFetchActiveKeys(anyInt(), anyInt());
        setUpReportFailureToServerCallback();

        assertThrows(
                ExecutionException.class,
                () -> mSpyWorker.startTrainingRun(JOB_ID, mMockJobServiceOnFinishCallback).get());

        verify(mSpyWorker).reportFailureResultToServer(any(), any());
    }

    private void setUpExampleStoreService() {
        TestExampleStoreService testExampleStoreService = new TestExampleStoreService();
        doReturn(testExampleStoreService).when(mSpyWorker).getExampleStoreService(anyString());
        doNothing().when(mSpyWorker).unbindFromExampleStoreService();
    }

    private void setUpHttpFederatedProtocol(CheckinResult checkinResult) {
        doReturn(FluentFuture.from(immediateFuture(CREATE_TASK_ASSIGNMENT_RESPONSE)))
                .when(mSpyHttpFederatedProtocol)
                .createTaskAssignment(any());
        doReturn(immediateFuture(checkinResult))
                .when(mSpyHttpFederatedProtocol)
                .downloadTaskAssignment(any());
        doReturn(FluentFuture.from(immediateFuture(null)))
                .when(mSpyHttpFederatedProtocol)
                .reportResult(any(), any(), any());
    }

    private void setUpReportFailureToServerCallback() {
        doNothing().when(mSpyWorker).reportFailureResultToServer(any(), any());
    }

    private static class TestExampleStoreService extends IExampleStoreService.Stub {
        @Override
        public void startQuery(Bundle params, IExampleStoreCallback callback)
                throws RemoteException {
            callback.onStartQuerySuccess(
                    new FakeExampleStoreIterator(ImmutableList.of(EXAMPLE_PROTO_1.toByteArray())));
        }
    }

    class TestInjector extends FederatedComputeWorker.Injector {
        @Override
        ExampleConsumptionRecorder getExampleConsumptionRecorder() {
            return new ExampleConsumptionRecorder() {
                @Override
                public synchronized ArrayList<ExampleConsumption> finishRecordingAndGet() {
                    ArrayList<ExampleConsumption> exampleList = new ArrayList<>();
                    exampleList.add(EXAMPLE_CONSUMPTION_1);
                    return exampleList;
                }
            };
        }

        @Override
        ListeningExecutorService getBgExecutor() {
            return MoreExecutors.newDirectExecutorService();
        }

        @Override
        TrainingEventLogger getTrainingEventLogger() {
            return mMockTrainingEventLogger;
        }

        @Override
        AuthorizationContext createAuthContext(Context context, String ownerId, String owerCert) {
            return new AuthorizationContext(
                    ownerId,
                    owerCert,
                    ODPAuthorizationTokenDao.getInstanceForTest(mContext),
                    sSpyKeyAttestation,
                    MonotonicClock.getInstance());
        }

        @Override
        HttpFederatedProtocol getHttpFederatedProtocol(
                String serverAddress,
                String clientVersion,
                String populationName,
                TrainingEventLogger trainingEventLogger) {
            return mSpyHttpFederatedProtocol;
        }

        @Override
        EligibilityDecider getEligibilityDecider(Context context) {
            return new EligibilityDecider(mTrainingTaskDao);
        }

        @Override
        boolean isEligibilityTaskEnabled() {
            return true;
        }
    }

    private void setUpIssueCheckin(CheckinResult checkinResult) {
        doReturn(FluentFuture.from(immediateFuture(CREATE_TASK_ASSIGNMENT_RESPONSE)))
                .when(mSpyHttpFederatedProtocol)
                .createTaskAssignment(any());
        doReturn(immediateFuture(checkinResult))
                .when(mSpyHttpFederatedProtocol)
                .downloadTaskAssignment(any());
    }

    private static final class FakeIsolatedTrainingService extends IIsolatedTrainingService.Stub {
        @Override
        public void runFlTraining(Bundle params, ITrainingResultCallback callback)
                throws RemoteException {
            Bundle bundle = new Bundle();
            bundle.putByteArray(
                    Constants.EXTRA_FL_RUNNER_RESULT, FL_RUNNER_SUCCESS_RESULT.toByteArray());
            ArrayList<ExampleConsumption> exampleConsumptionList = new ArrayList<>();
            exampleConsumptionList.add(EXAMPLE_CONSUMPTION_1);
            bundle.putParcelableArrayList(
                    ClientConstants.EXTRA_EXAMPLE_CONSUMPTION_LIST, exampleConsumptionList);
            callback.onResult(bundle);
        }

        @Override
        public void cancelTraining(long runId) {}
    }
}
