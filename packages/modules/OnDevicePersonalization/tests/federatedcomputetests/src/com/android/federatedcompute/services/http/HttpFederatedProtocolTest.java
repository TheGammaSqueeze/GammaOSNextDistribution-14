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

package com.android.federatedcompute.services.http;

import static com.android.federatedcompute.services.http.HttpClientUtil.ACCEPT_ENCODING_HDR;
import static com.android.federatedcompute.services.http.HttpClientUtil.CONTENT_ENCODING_HDR;
import static com.android.federatedcompute.services.http.HttpClientUtil.CONTENT_LENGTH_HDR;
import static com.android.federatedcompute.services.http.HttpClientUtil.CONTENT_TYPE_HDR;
import static com.android.federatedcompute.services.http.HttpClientUtil.FCP_OWNER_ID_DIGEST;
import static com.android.federatedcompute.services.http.HttpClientUtil.GZIP_ENCODING_HDR;
import static com.android.federatedcompute.services.http.HttpClientUtil.HTTP_UNAUTHENTICATED_STATUS;
import static com.android.federatedcompute.services.http.HttpClientUtil.ODP_AUTHENTICATION_KEY;
import static com.android.federatedcompute.services.http.HttpClientUtil.ODP_AUTHORIZATION_KEY;
import static com.android.federatedcompute.services.http.HttpClientUtil.ODP_IDEMPOTENCY_KEY;
import static com.android.federatedcompute.services.http.HttpClientUtil.PROTOBUF_CONTENT_TYPE;
import static com.android.federatedcompute.services.http.HttpClientUtil.compressWithGzip;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.federatedcompute.services.common.Clock;
import com.android.federatedcompute.services.common.MonotonicClock;
import com.android.federatedcompute.services.common.NetworkStats;
import com.android.federatedcompute.services.common.PhFlags;
import com.android.federatedcompute.services.common.TrainingEventLogger;
import com.android.federatedcompute.services.data.FederatedComputeDbHelper;
import com.android.federatedcompute.services.data.FederatedComputeEncryptionKey;
import com.android.federatedcompute.services.data.ODPAuthorizationToken;
import com.android.federatedcompute.services.data.ODPAuthorizationTokenDao;
import com.android.federatedcompute.services.encryption.HpkeJniEncrypter;
import com.android.federatedcompute.services.http.HttpClientUtil.HttpMethod;
import com.android.federatedcompute.services.security.AuthorizationContext;
import com.android.federatedcompute.services.security.KeyAttestation;
import com.android.federatedcompute.services.testutils.TrainingTestUtil;
import com.android.federatedcompute.services.training.util.ComputationResult;
import com.android.modules.utils.testing.ExtendedMockitoRule;

import com.google.common.collect.BoundType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.intelligence.fcp.client.FLRunnerResult;
import com.google.intelligence.fcp.client.FLRunnerResult.ContributionResult;
import com.google.internal.federatedcompute.v1.AuthenticationMetadata;
import com.google.internal.federatedcompute.v1.ClientVersion;
import com.google.internal.federatedcompute.v1.KeyAttestationAuthMetadata;
import com.google.internal.federatedcompute.v1.RejectionInfo;
import com.google.internal.federatedcompute.v1.RejectionReason;
import com.google.internal.federatedcompute.v1.Resource;
import com.google.internal.federatedcompute.v1.ResourceCapabilities;
import com.google.internal.federatedcompute.v1.ResourceCompressionFormat;
import com.google.ondevicepersonalization.federatedcompute.proto.CreateTaskAssignmentRequest;
import com.google.ondevicepersonalization.federatedcompute.proto.CreateTaskAssignmentResponse;
import com.google.ondevicepersonalization.federatedcompute.proto.ReportResultRequest;
import com.google.ondevicepersonalization.federatedcompute.proto.ReportResultRequest.Result;
import com.google.ondevicepersonalization.federatedcompute.proto.ReportResultResponse;
import com.google.ondevicepersonalization.federatedcompute.proto.TaskAssignment;
import com.google.ondevicepersonalization.federatedcompute.proto.UploadInstruction;
import com.google.protobuf.ByteString;

import org.json.JSONArray;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

@RunWith(Parameterized.class)
@ExtendedMockitoRule.MockStatic(PhFlags.class)
public final class HttpFederatedProtocolTest {

    @Rule
    public final ExtendedMockitoRule extendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    private static final String TASK_ASSIGNMENT_TARGET_URI = "https://test-server.com/";
    private static final String PLAN_URI = "https://fake.uri/plan";
    private static final String CHECKPOINT_URI = "https://fake.uri/checkpoint";
    private static final String START_TASK_ASSIGNMENT_URI =
            "https://test-server.com/taskassignment/v1/population/test_population:create-task"
                    + "-assignment";
    private static final String REPORT_RESULT_URI =
            "https://test-server.com/taskassignment/v1/population/test_population/task/task-id/"
                    + "aggregation/aggregation-id/task-assignment/assignment-id:report-result";
    private static final String UPLOAD_LOCATION_URI = "https://dataupload.uri";
    private static final String POPULATION_NAME = "test_population";
    private static final byte[] CHECKPOINT = "INIT_CHECKPOINT".getBytes(UTF_8);
    private static final String CLIENT_VERSION = "CLIENT_VERSION";
    private static final String TASK_ID = "task-id";
    private static final String ASSIGNMENT_ID = "assignment-id";
    private static final String AGGREGATION_ID = "aggregation-id";
    private static final String OCTET_STREAM = "application/octet-stream";
    private static final String OWNER_ID = "com.android.pckg.name/com.android.class.name";
    private static final String OWNER_ID_CERT_DIGEST = "123SOME45DIGEST78";

    private static final byte[] CHALLENGE =
            ("AHXUDhoSEFikqOefmo8xE7kGp/xjVMRDYBecBiHGxCN8rTv9W0Z4L/14d0OLB"
                            + "vC1VVzXBAnjgHoKLZzuJifTOaBJwGNIQ2ejnx3n6ayoRchDNCgpK29T+EAhBWzH")
                    .getBytes();

    private static final String TOKEN = "638c9108-ff36-4795-b55e-6a482983bf82";

    private static final List<String> KA_RECORD =
            List.of("aasldkgjlaskdjgalskj", "aldkjglasdkjlasjg");
    private static final AuthenticationMetadata AUTH_METADATA =
            AuthenticationMetadata.newBuilder()
                    .setKeyAttestationMetadata(
                            KeyAttestationAuthMetadata.newBuilder()
                                    .setChallenge(ByteString.copyFrom(CHALLENGE)))
                    .build();
    private static final FederatedComputeEncryptionKey ENCRYPTION_KEY =
            new FederatedComputeEncryptionKey.Builder()
                    .setPublicKey("rSJBSUYG0ebvfW1AXCWO0CMGMJhDzpfQm3eLyw1uxX8=")
                    .setKeyIdentifier("0962201a-5abd-4e25-a486-2c7bd1ee1887")
                    .setKeyType(FederatedComputeEncryptionKey.KEY_TYPE_ENCRYPTION)
                    .setCreationTime(1L)
                    .setExpiryTime(1L)
                    .build();
    private static final FLRunnerResult FL_RUNNER_SUCCESS_RESULT =
            FLRunnerResult.newBuilder().setContributionResult(ContributionResult.SUCCESS).build();

    private static final FLRunnerResult FL_RUNNER_FAIL_RESULT =
            FLRunnerResult.newBuilder().setContributionResult(ContributionResult.FAIL).build();

    private static final FLRunnerResult FL_RUNNER_NOT_ELIGIBLE_RESULT =
            FLRunnerResult.newBuilder()
                    .setContributionResult(ContributionResult.FAIL)
                    .setErrorStatus(FLRunnerResult.ErrorStatus.NOT_ELIGIBLE)
                    .build();
    private static final CreateTaskAssignmentRequest
            START_TASK_ASSIGNMENT_REQUEST_WITH_COMPRESSION =
                    CreateTaskAssignmentRequest.newBuilder()
                            .setClientVersion(
                                    ClientVersion.newBuilder().setVersionCode(CLIENT_VERSION))
                            .setResourceCapabilities(
                                    ResourceCapabilities.newBuilder()
                                            .addSupportedCompressionFormats(
                                                    ResourceCompressionFormat
                                                            .RESOURCE_COMPRESSION_FORMAT_GZIP)
                                            .build())
                            .build();

    private static final FederatedComputeHttpResponse SUCCESS_EMPTY_HTTP_RESPONSE =
            new FederatedComputeHttpResponse.Builder().setStatusCode(200).build();
    private static final long ODP_AUTHORIZATION_TOKEN_TTL = 30 * 24 * 60 * 60 * 1000L;

    @Captor private ArgumentCaptor<FederatedComputeHttpRequest> mHttpRequestCaptor;

    @Mock private HttpClient mMockHttpClient;

    @Parameterized.Parameter(0)
    public boolean mSupportCompression;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {{false}, {true}});
    }

    private HttpFederatedProtocol mHttpFederatedProtocol;

    @Mock private TrainingEventLogger mTrainingEventLogger;
    private ArgumentCaptor<NetworkStats> mNetworkStatsArgumentCaptor =
            ArgumentCaptor.forClass(NetworkStats.class);

    private ODPAuthorizationTokenDao mODPAuthorizationTokenDao;

    private Clock mClock = MonotonicClock.getInstance();

    @Mock private KeyAttestation mMockKeyAttestation;

    @Mock private PhFlags mMocKFlags;

    @Before
    public void setUp() throws Exception {
        mODPAuthorizationTokenDao =
                ODPAuthorizationTokenDao.getInstanceForTest(
                        ApplicationProvider.getApplicationContext());
        mHttpFederatedProtocol =
                new HttpFederatedProtocol(
                        TASK_ASSIGNMENT_TARGET_URI,
                        CLIENT_VERSION,
                        POPULATION_NAME,
                        mMockHttpClient,
                        new HpkeJniEncrypter(),
                        mTrainingEventLogger);
        doReturn(KA_RECORD).when(mMockKeyAttestation).generateAttestationRecord(any(), any());
        doNothing().when(mTrainingEventLogger).logReportResultUnauthorized();
        doNothing().when(mTrainingEventLogger).logReportResultAuthSucceeded();
        doNothing().when(mTrainingEventLogger).logTaskAssignmentUnauthorized();
        doNothing().when(mTrainingEventLogger).logTaskAssignmentAuthSucceeded();
        doReturn(true).when(mMocKFlags).isEncryptionEnabled();
        when(PhFlags.getInstance()).thenReturn(mMocKFlags);
    }

    @After
    public void cleanUp() {
        FederatedComputeDbHelper dbHelper =
                FederatedComputeDbHelper.getInstanceForTest(
                        ApplicationProvider.getApplicationContext());
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }

    @Test
    public void testIssueCheckinSuccess() throws Exception {
        setUpHttpFederatedProtocol();

        CreateTaskAssignmentResponse createTaskAssignmentResponse =
                mHttpFederatedProtocol.createTaskAssignment(createAuthContext()).get();
        mHttpFederatedProtocol
                .downloadTaskAssignment(createTaskAssignmentResponse.getTaskAssignment())
                .get();

        List<FederatedComputeHttpRequest> actualHttpRequests = mHttpRequestCaptor.getAllValues();

        // Verify task assignment request.
        FederatedComputeHttpRequest actualStartTaskAssignmentRequest = actualHttpRequests.get(0);
        checkActualTARequest(actualStartTaskAssignmentRequest, 4);

        // Verify fetch resource request.
        FederatedComputeHttpRequest actualFetchResourceRequest = actualHttpRequests.get(1);
        ImmutableSet<String> resourceUris = ImmutableSet.of(PLAN_URI, CHECKPOINT_URI);
        assertTrue(resourceUris.contains(actualFetchResourceRequest.getUri()));
        HashMap<String, String> expectedHeaders = new HashMap<>();
        if (mSupportCompression) {
            expectedHeaders.put(ACCEPT_ENCODING_HDR, GZIP_ENCODING_HDR);
        }
        assertThat(actualFetchResourceRequest.getExtraHeaders()).isEqualTo(expectedHeaders);
        verify(mTrainingEventLogger).logCheckinStarted();
        verify(mTrainingEventLogger)
                .logCheckinPlanUriReceived(mNetworkStatsArgumentCaptor.capture());
        NetworkStats networkStats = mNetworkStatsArgumentCaptor.getValue();
        assertThat(networkStats.getTotalBytesDownloaded()).isEqualTo(121);
        assertThat(networkStats.getTotalBytesUploaded()).isEqualTo(348);
    }

    @Test
    public void testIssueCheckin_withAuthToken_unauthenticated() throws Exception {
        insertAuthToken();
        setUpHttpFederatedProtocol(
                createUnauthenticatedResponse(),
                createPlanHttpResponse(),
                checkpointHttpResponse(),
                createReportResultHttpResponse(),
                SUCCESS_EMPTY_HTTP_RESPONSE);

        CreateTaskAssignmentResponse createTaskAssignmentResponse =
                mHttpFederatedProtocol.createTaskAssignment(createAuthContext()).get();

        List<FederatedComputeHttpRequest> actualHttpRequests = mHttpRequestCaptor.getAllValues();

        // Verify task assignment request.
        FederatedComputeHttpRequest actualStartTaskAssignmentRequest = actualHttpRequests.get(0);
        checkActualTARequest(actualStartTaskAssignmentRequest, 5);
        String authorizationKey =
                actualStartTaskAssignmentRequest.getExtraHeaders().get(ODP_AUTHORIZATION_KEY);
        assertNotNull(authorizationKey);
        assertThat(authorizationKey).isEqualTo(TOKEN);
        assertThat(
                        actualStartTaskAssignmentRequest
                                .getExtraHeaders()
                                .containsKey(ODP_AUTHENTICATION_KEY))
                .isEqualTo(false);

        assertThat(createTaskAssignmentResponse.getRejectionInfo().getReason())
                .isEqualTo(RejectionReason.Enum.UNAUTHENTICATED);
        assertThat(
                        createTaskAssignmentResponse
                                .getRejectionInfo()
                                .getAuthMetadata()
                                .getKeyAttestationMetadata()
                                .getChallenge()
                                .toByteArray())
                .isEqualTo(CHALLENGE);
    }

    @Test
    public void testIssueCheckin_withAuthToken_success() throws Exception {

        // insert authorization token
        ODPAuthorizationToken authToken = createAuthToken();
        mODPAuthorizationTokenDao.insertAuthorizationToken(authToken);
        assertThat(mODPAuthorizationTokenDao.getUnexpiredAuthorizationToken(OWNER_ID))
                .isEqualTo(authToken);
        setUpHttpFederatedProtocol();

        CreateTaskAssignmentResponse taskAssignmentResponse =
                mHttpFederatedProtocol.createTaskAssignment(createAuthContext()).get();
        mHttpFederatedProtocol
                .downloadTaskAssignment(taskAssignmentResponse.getTaskAssignment())
                .get();
        List<FederatedComputeHttpRequest> actualHttpRequests = mHttpRequestCaptor.getAllValues();

        // Verify task assignment request.
        FederatedComputeHttpRequest actualStartTaskAssignmentRequest = actualHttpRequests.get(0);
        checkActualTARequest(actualStartTaskAssignmentRequest, 5);
        String authorizationKey =
                actualStartTaskAssignmentRequest.getExtraHeaders().get(ODP_AUTHORIZATION_KEY);
        assertNotNull(authorizationKey);
        assertThat(authorizationKey).isEqualTo(TOKEN);
        assertThat(
                        actualStartTaskAssignmentRequest
                                .getExtraHeaders()
                                .containsKey(ODP_AUTHENTICATION_KEY))
                .isEqualTo(false);

        // the old authorization token is not deleted
        assertThat(mODPAuthorizationTokenDao.getUnexpiredAuthorizationToken(OWNER_ID))
                .isEqualTo(authToken);
    }

    @Test
    public void testIssueCheckin_unauthenticatedWithSecondTry() {
        insertAuthToken();
        setUpHttpFederatedProtocol(
                createUnauthenticatedResponse(),
                createPlanHttpResponse(),
                checkpointHttpResponse(),
                createReportResultHttpResponse(),
                SUCCESS_EMPTY_HTTP_RESPONSE);

        AuthorizationContext authContext = createAuthContext();
        authContext.updateAuthState(AUTH_METADATA, mTrainingEventLogger);
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> mHttpFederatedProtocol.createTaskAssignment(authContext).get());

        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
        assertThat(exception.getCause()).hasMessageThat().contains("failed: 401");
    }

    @Test
    public void testCreateTaskAssignmentFailed() {
        FederatedComputeHttpResponse httpResponse =
                new FederatedComputeHttpResponse.Builder().setStatusCode(404).build();
        when(mMockHttpClient.performRequestAsyncWithRetry(any()))
                .thenReturn(immediateFuture(httpResponse));

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mHttpFederatedProtocol
                                        .createTaskAssignment(createAuthContext())
                                        .get());

        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
        assertThat(exception.getCause())
                .hasMessageThat()
                .isEqualTo("Start task assignment failed: 404");
        verify(mTrainingEventLogger).logCheckinStarted();
        verify(mTrainingEventLogger, times(0)).logCheckinPlanUriReceived(any());
    }

    @Test
    public void testIssueCheckin_withAttestationRecord() throws Exception {
        setUpHttpFederatedProtocol();

        CreateTaskAssignmentResponse taskAssignmentResponse =
                mHttpFederatedProtocol
                        .createTaskAssignment(createAuthContextWithAttestationRecord())
                        .get();
        mHttpFederatedProtocol
                .downloadTaskAssignment(taskAssignmentResponse.getTaskAssignment())
                .get();

        List<FederatedComputeHttpRequest> actualHttpRequests = mHttpRequestCaptor.getAllValues();

        // Verify task assignment request.
        FederatedComputeHttpRequest actualStartTaskAssignmentRequest = actualHttpRequests.get(0);
        checkActualTARequest(actualStartTaskAssignmentRequest, 6);
        String authenticationKey =
                actualStartTaskAssignmentRequest.getExtraHeaders().get(ODP_AUTHENTICATION_KEY);
        JSONArray attestationArr = new JSONArray(KA_RECORD);
        assertThat(authenticationKey).isEqualTo(attestationArr.toString());
        assertThat(
                        actualStartTaskAssignmentRequest
                                .getExtraHeaders()
                                .containsKey(ODP_AUTHORIZATION_KEY))
                .isEqualTo(true);
        assertThat(actualStartTaskAssignmentRequest.getExtraHeaders().get(ODP_AUTHORIZATION_KEY))
                .isNotNull();
        verify(mTrainingEventLogger, times(1)).logTaskAssignmentAuthSucceeded();
        // A new authorization token is stored in DB
        assertThat(
                        mODPAuthorizationTokenDao
                                .getUnexpiredAuthorizationToken(OWNER_ID)
                                .getAuthorizationToken())
                .isNotNull();
        assertThat(
                        mODPAuthorizationTokenDao
                                .getUnexpiredAuthorizationToken(OWNER_ID)
                                .getOwnerIdentifier())
                .isEqualTo(OWNER_ID);

        // Verify fetch resource request.
        FederatedComputeHttpRequest actualFetchResourceRequest = actualHttpRequests.get(1);
        ImmutableSet<String> resourceUris = ImmutableSet.of(PLAN_URI, CHECKPOINT_URI);
        assertTrue(resourceUris.contains(actualFetchResourceRequest.getUri()));
        HashMap<String, String> expectedHeaders = new HashMap<>();
        if (mSupportCompression) {
            expectedHeaders.put(ACCEPT_ENCODING_HDR, GZIP_ENCODING_HDR);
        }
        assertThat(actualFetchResourceRequest.getExtraHeaders()).isEqualTo(expectedHeaders);
    }

    @Test
    public void testCreateTaskAssignment_unauthorized() {
        setUpHttpFederatedProtocol(
                createUnauthorizedResponse(),
                createPlanHttpResponse(),
                checkpointHttpResponse(),
                createReportResultHttpResponse(),
                SUCCESS_EMPTY_HTTP_RESPONSE);

        AuthorizationContext authContext = createAuthContextWithAttestationRecord();

        assertThrows(
                ExecutionException.class,
                () -> mHttpFederatedProtocol.createTaskAssignment(authContext).get());
        verify(mTrainingEventLogger, times(1)).logTaskAssignmentUnauthorized();
    }

    @Test
    public void testCreateTaskAssignmentRejection() throws Exception {
        CreateTaskAssignmentResponse createTaskAssignmentResponse =
                CreateTaskAssignmentResponse.newBuilder()
                        .setRejectionInfo(RejectionInfo.getDefaultInstance())
                        .build();
        FederatedComputeHttpResponse httpResponse =
                new FederatedComputeHttpResponse.Builder()
                        .setStatusCode(200)
                        .setPayload(createTaskAssignmentResponse.toByteArray())
                        .build();
        when(mMockHttpClient.performRequestAsyncWithRetry(any()))
                .thenReturn(immediateFuture(httpResponse));

        CreateTaskAssignmentResponse taskAssignmentResponse =
                mHttpFederatedProtocol.createTaskAssignment(createAuthContext()).get();

        assertThat(taskAssignmentResponse.hasRejectionInfo()).isTrue();
        verify(mTrainingEventLogger).logCheckinStarted();
        verify(mTrainingEventLogger).logCheckinRejected(mNetworkStatsArgumentCaptor.capture());
        NetworkStats networkStats = mNetworkStatsArgumentCaptor.getValue();
        assertThat(networkStats.getTotalBytesDownloaded()).isEqualTo(2);
        assertThat(networkStats.getTotalBytesUploaded()).isEqualTo(348);
    }

    @Test
    public void testTaskAssignmentSuccessPlanFetchFailed() throws Exception {
        FederatedComputeHttpResponse planHttpResponse =
                new FederatedComputeHttpResponse.Builder().setStatusCode(404).build();
        // The workflow: start task assignment success, download plan failed and download
        // checkpoint success.
        setUpHttpFederatedProtocol(
                createStartTaskAssignmentHttpResponse(),
                planHttpResponse,
                checkpointHttpResponse(),
                /** reportResultHttpResponse= */
                null,
                /** uploadResultHttpResponse= */
                null);

        CreateTaskAssignmentResponse taskAssignmentResponse =
                mHttpFederatedProtocol.createTaskAssignment(createAuthContext()).get();
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mHttpFederatedProtocol
                                        .downloadTaskAssignment(
                                                taskAssignmentResponse.getTaskAssignment())
                                        .get());

        assertThat(exception).hasCauseThat().isInstanceOf(IllegalStateException.class);
        assertThat(exception.getCause()).hasMessageThat().isEqualTo("Fetch plan failed: 404");
        verify(mTrainingEventLogger).logCheckinStarted();
        verify(mTrainingEventLogger)
                .logCheckinPlanUriReceived(mNetworkStatsArgumentCaptor.capture());
    }

    @Test
    public void testTaskAssignmentSuccessCheckpointDataFetchFailed() throws Exception {
        FederatedComputeHttpResponse checkpointHttpResponse =
                new FederatedComputeHttpResponse.Builder().setStatusCode(404).build();

        // The workflow: start task assignment success, download plan success and download
        // checkpoint failed.
        setUpHttpFederatedProtocol(
                createStartTaskAssignmentHttpResponse(),
                createPlanHttpResponse(),
                checkpointHttpResponse,
                /** reportResultHttpResponse= */
                null,
                /** uploadResultHttpResponse= */
                null);
        CreateTaskAssignmentResponse taskAssignmentResponse =
                mHttpFederatedProtocol.createTaskAssignment(createAuthContext()).get();
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mHttpFederatedProtocol
                                        .downloadTaskAssignment(
                                                taskAssignmentResponse.getTaskAssignment())
                                        .get());

        assertThat(exception).hasCauseThat().isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testReportFailedTrainingResult_returnSuccess() throws Exception {
        ComputationResult computationResult =
                new ComputationResult(createOutputCheckpointFile(), FL_RUNNER_FAIL_RESULT, null);

        setUpHttpFederatedProtocol();
        // Setup task id, aggregation id for report result.
        CreateTaskAssignmentResponse taskAssignmentResponse =
                mHttpFederatedProtocol.createTaskAssignment(createAuthContext()).get();
        mHttpFederatedProtocol
                .downloadTaskAssignment(taskAssignmentResponse.getTaskAssignment())
                .get();

        mHttpFederatedProtocol
                .reportResult(computationResult, ENCRYPTION_KEY, createAuthContext())
                .get();

        // Verify ReportResult request.
        List<FederatedComputeHttpRequest> actualHttpRequests = mHttpRequestCaptor.getAllValues();
        assertThat(actualHttpRequests).hasSize(4);
        FederatedComputeHttpRequest actualReportResultRequest = actualHttpRequests.get(3);
        ReportResultRequest reportResultRequest =
                ReportResultRequest.newBuilder().setResult(Result.FAILED).build();
        assertThat(actualReportResultRequest.getBody())
                .isEqualTo(reportResultRequest.toByteArray());
    }

    @Test
    public void testReportNotEligibleTrainingResult_returnSuccess() throws Exception {
        ComputationResult computationResult =
                new ComputationResult(
                        createOutputCheckpointFile(), FL_RUNNER_NOT_ELIGIBLE_RESULT, null);

        setUpHttpFederatedProtocol();
        // Setup task id, aggregation id for report result.
        CreateTaskAssignmentResponse taskAssignmentResponse =
                mHttpFederatedProtocol.createTaskAssignment(createAuthContext()).get();
        mHttpFederatedProtocol
                .downloadTaskAssignment(taskAssignmentResponse.getTaskAssignment())
                .get();

        mHttpFederatedProtocol
                .reportResult(computationResult, ENCRYPTION_KEY, createAuthContext())
                .get();

        // Verify ReportResult request.
        List<FederatedComputeHttpRequest> actualHttpRequests = mHttpRequestCaptor.getAllValues();
        assertThat(actualHttpRequests).hasSize(4);
        FederatedComputeHttpRequest actualReportResultRequest = actualHttpRequests.get(3);
        ReportResultRequest reportResultRequest =
                ReportResultRequest.newBuilder().setResult(Result.NOT_ELIGIBLE).build();
        checkActualReportResultRequest(actualReportResultRequest);
        assertThat(actualReportResultRequest.getBody())
                .isEqualTo(reportResultRequest.toByteArray());
        verify(mTrainingEventLogger).logFailureResultUploadStarted();
        verify(mTrainingEventLogger)
                .logFailureResultUploadCompleted(mNetworkStatsArgumentCaptor.capture());
        NetworkStats networkStats = mNetworkStatsArgumentCaptor.getValue();
        assertThat(networkStats.getTotalBytesDownloaded()).isEqualTo(mSupportCompression ? 96 : 68);
        assertThat(networkStats.getTotalBytesUploaded()).isEqualTo(226);
    }

    @Test
    public void testReportAndUploadResultSuccess() throws Exception {
        ComputationResult computationResult =
                new ComputationResult(createOutputCheckpointFile(), FL_RUNNER_SUCCESS_RESULT, null);

        setUpHttpFederatedProtocol();
        // Setup task id, aggregation id for report result.
        CreateTaskAssignmentResponse taskAssignmentResponse =
                mHttpFederatedProtocol.createTaskAssignment(createAuthContext()).get();
        mHttpFederatedProtocol
                .downloadTaskAssignment(taskAssignmentResponse.getTaskAssignment())
                .get();

        mHttpFederatedProtocol
                .reportResult(computationResult, ENCRYPTION_KEY, createAuthContext())
                .get();

        // Verify ReportResult request.
        List<FederatedComputeHttpRequest> actualHttpRequests = mHttpRequestCaptor.getAllValues();
        FederatedComputeHttpRequest actualReportResultRequest = actualHttpRequests.get(3);

        checkActualReportResultRequest(actualReportResultRequest);
        ReportResultRequest reportResultRequest =
                ReportResultRequest.newBuilder().setResult(Result.COMPLETED).build();
        assertThat(actualReportResultRequest.getBody())
                .isEqualTo(reportResultRequest.toByteArray());

        // Verify upload data request.
        FederatedComputeHttpRequest actualDataUploadRequest = actualHttpRequests.get(4);
        assertThat(actualDataUploadRequest.getUri()).isEqualTo(UPLOAD_LOCATION_URI);
        assertThat(actualReportResultRequest.getHttpMethod()).isEqualTo(HttpMethod.PUT);
        HashMap<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put(CONTENT_TYPE_HDR, OCTET_STREAM);
        if (mSupportCompression) {
            expectedHeaders.put(CONTENT_ENCODING_HDR, GZIP_ENCODING_HDR);
        }

        int actualContentLength =
                Integer.parseInt(
                        actualDataUploadRequest.getExtraHeaders().remove(CONTENT_LENGTH_HDR));
        assertThat(actualDataUploadRequest.getExtraHeaders()).isEqualTo(expectedHeaders);
        // The encryption is non-deterministic with BoringSSL JNI.
        // Only check the range of the content.
        if (mSupportCompression) {
            assertThat(actualContentLength)
                    .isIn(Range.range(500, BoundType.CLOSED, 550, BoundType.CLOSED));
        } else {
            assertThat(actualContentLength)
                    .isIn(Range.range(600, BoundType.CLOSED, 650, BoundType.CLOSED));
        }
        verify(mTrainingEventLogger).logResultUploadStarted();
        verify(mTrainingEventLogger)
                .logResultUploadCompleted(mNetworkStatsArgumentCaptor.capture());
        NetworkStats networkStats = mNetworkStatsArgumentCaptor.getValue();
        // The upload result size is non-deterministic so we only check it's positive value.
        assertTrue(networkStats.getTotalBytesDownloaded() > 0);
        assertTrue(networkStats.getTotalBytesUploaded() > 0);
    }

    @Test
    public void testReportResultFailed() throws Exception {
        FederatedComputeHttpResponse reportResultHttpResponse =
                new FederatedComputeHttpResponse.Builder().setStatusCode(503).build();
        ComputationResult computationResult =
                new ComputationResult(createOutputCheckpointFile(), FL_RUNNER_SUCCESS_RESULT, null);

        setUpHttpFederatedProtocol(
                createStartTaskAssignmentHttpResponse(),
                createPlanHttpResponse(),
                checkpointHttpResponse(),
                reportResultHttpResponse,
                null);

        CreateTaskAssignmentResponse taskAssignmentResponse =
                mHttpFederatedProtocol.createTaskAssignment(createAuthContext()).get();
        mHttpFederatedProtocol
                .downloadTaskAssignment(taskAssignmentResponse.getTaskAssignment())
                .get();
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mHttpFederatedProtocol
                                        .reportResult(
                                                computationResult,
                                                ENCRYPTION_KEY,
                                                createAuthContext())
                                        .get());

        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
        assertThat(exception.getCause()).hasMessageThat().isEqualTo("ReportResult failed: 503");
        verify(mTrainingEventLogger).logResultUploadStarted();
    }

    @Test
    public void testReportResult_unauthenticated() throws Exception {
        insertAuthToken();
        setUpHttpFederatedProtocol(
                createStartTaskAssignmentHttpResponse(),
                createPlanHttpResponse(),
                checkpointHttpResponse(),
                createUnauthenticatedResponse(),
                SUCCESS_EMPTY_HTTP_RESPONSE);
        ComputationResult computationResult =
                new ComputationResult(createOutputCheckpointFile(), FL_RUNNER_SUCCESS_RESULT, null);

        CreateTaskAssignmentResponse taskAssignmentResponse =
                mHttpFederatedProtocol.createTaskAssignment(createAuthContext()).get();
        mHttpFederatedProtocol
                .downloadTaskAssignment(taskAssignmentResponse.getTaskAssignment())
                .get();

        RejectionInfo reportResultRejection =
                mHttpFederatedProtocol
                        .reportResult(computationResult, ENCRYPTION_KEY, createAuthContext())
                        .get();

        // Verify ReportResult request.
        List<FederatedComputeHttpRequest> actualHttpRequests = mHttpRequestCaptor.getAllValues();
        FederatedComputeHttpRequest actualReportResultRequest = actualHttpRequests.get(3);
        checkActualReportResultRequest(actualReportResultRequest);
        ReportResultRequest reportResultRequest =
                ReportResultRequest.newBuilder().setResult(Result.COMPLETED).build();
        assertThat(actualReportResultRequest.getBody())
                .isEqualTo(reportResultRequest.toByteArray());
        assertThat(
                        reportResultRejection
                                .getAuthMetadata()
                                .getKeyAttestationMetadata()
                                .getChallenge())
                .isEqualTo(ByteString.copyFrom(CHALLENGE));
    }

    @Test
    public void testReportResult_withAttestation() throws Exception {
        setUpHttpFederatedProtocol();
        ComputationResult computationResult =
                new ComputationResult(createOutputCheckpointFile(), FL_RUNNER_SUCCESS_RESULT, null);

        CreateTaskAssignmentResponse taskAssignmentResponse =
                mHttpFederatedProtocol.createTaskAssignment(createAuthContext()).get();
        mHttpFederatedProtocol
                .downloadTaskAssignment(taskAssignmentResponse.getTaskAssignment())
                .get();

        RejectionInfo reportResultRejection =
                mHttpFederatedProtocol
                        .reportResult(
                                computationResult,
                                ENCRYPTION_KEY,
                                createAuthContextWithAttestationRecord())
                        .get();

        assertThat(reportResultRejection).isNull();
        // Verify ReportResult request.
        List<FederatedComputeHttpRequest> actualHttpRequests = mHttpRequestCaptor.getAllValues();
        FederatedComputeHttpRequest actualReportResultRequest = actualHttpRequests.get(3);
        checkActualReportResultRequest(actualReportResultRequest);
        ReportResultRequest reportResultRequest =
                ReportResultRequest.newBuilder().setResult(Result.COMPLETED).build();
        assertThat(actualReportResultRequest.getBody())
                .isEqualTo(reportResultRequest.toByteArray());
        assertThat(
                        actualReportResultRequest
                                .getExtraHeaders()
                                .getOrDefault(ODP_AUTHENTICATION_KEY, ""))
                .isEqualTo(new JSONArray(KA_RECORD).toString());
        assertThat(
                        actualReportResultRequest
                                .getExtraHeaders()
                                .getOrDefault(ODP_AUTHORIZATION_KEY, "")
                                .length())
                .isEqualTo(36); // UUID Length
        verify(mTrainingEventLogger).logReportResultAuthSucceeded();
    }

    @Test
    public void testReportResult_unauthorized() throws Exception {
        setUpHttpFederatedProtocol(
                createStartTaskAssignmentHttpResponse(),
                createPlanHttpResponse(),
                checkpointHttpResponse(),
                createUnauthorizedResponse(),
                SUCCESS_EMPTY_HTTP_RESPONSE);

        CreateTaskAssignmentResponse taskAssignmentResponse =
                mHttpFederatedProtocol.createTaskAssignment(createAuthContext()).get();
        mHttpFederatedProtocol
                .downloadTaskAssignment(taskAssignmentResponse.getTaskAssignment())
                .get();
        ComputationResult computationResult =
                new ComputationResult(createOutputCheckpointFile(), FL_RUNNER_SUCCESS_RESULT, null);

        assertThrows(
                ExecutionException.class,
                () ->
                        mHttpFederatedProtocol
                                .reportResult(
                                        computationResult,
                                        ENCRYPTION_KEY,
                                        createAuthContextWithAttestationRecord())
                                .get());
        verify(mTrainingEventLogger).logReportResultUnauthorized();
    }

    @Test
    public void testReportResult_throws() throws Exception {
        setUpHttpFederatedProtocol(
                createStartTaskAssignmentHttpResponse(),
                createPlanHttpResponse(),
                checkpointHttpResponse(),
                createUnauthenticatedResponse(),
                SUCCESS_EMPTY_HTTP_RESPONSE);
        ComputationResult computationResult =
                new ComputationResult(createOutputCheckpointFile(), FL_RUNNER_SUCCESS_RESULT, null);

        CreateTaskAssignmentResponse taskAssignmentResponse =
                mHttpFederatedProtocol.createTaskAssignment(createAuthContext()).get();
        mHttpFederatedProtocol
                .downloadTaskAssignment(taskAssignmentResponse.getTaskAssignment())
                .get();
        // Pretend 1st auth retry already failed.
        AuthorizationContext authContext = createAuthContext();
        authContext.updateAuthState(AUTH_METADATA, mTrainingEventLogger);

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mHttpFederatedProtocol
                                        .reportResult(
                                                computationResult, ENCRYPTION_KEY, authContext)
                                        .get());
        assertThat(exception.getMessage()).contains("401");
    }

    @Test
    public void testReportResultSuccessUploadFailed() throws Exception {
        FederatedComputeHttpResponse uploadResultHttpResponse =
                new FederatedComputeHttpResponse.Builder().setStatusCode(503).build();
        ComputationResult computationResult =
                new ComputationResult(createOutputCheckpointFile(), FL_RUNNER_SUCCESS_RESULT, null);

        setUpHttpFederatedProtocol(
                createStartTaskAssignmentHttpResponse(),
                createPlanHttpResponse(),
                checkpointHttpResponse(),
                createReportResultHttpResponse(),
                uploadResultHttpResponse);

        CreateTaskAssignmentResponse taskAssignmentResponse =
                mHttpFederatedProtocol.createTaskAssignment(createAuthContext()).get();
        mHttpFederatedProtocol
                .downloadTaskAssignment(taskAssignmentResponse.getTaskAssignment())
                .get();
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mHttpFederatedProtocol
                                        .reportResult(
                                                computationResult,
                                                ENCRYPTION_KEY,
                                                createAuthContext())
                                        .get());

        assertThat(exception).hasCauseThat().isInstanceOf(IllegalStateException.class);
        assertThat(exception.getCause()).hasMessageThat().isEqualTo("Upload result failed: 503");
    }

    private void insertAuthToken() {
        // insert authorization token
        ODPAuthorizationToken authToken = createAuthToken();
        mODPAuthorizationTokenDao.insertAuthorizationToken(authToken);
        assertThat(mODPAuthorizationTokenDao.getUnexpiredAuthorizationToken(OWNER_ID))
                .isEqualTo(authToken);
    }

    private AuthorizationContext createAuthContext() {
        return new AuthorizationContext(
                OWNER_ID,
                OWNER_ID_CERT_DIGEST,
                mODPAuthorizationTokenDao,
                mMockKeyAttestation,
                mClock);
    }

    private AuthorizationContext createAuthContextWithAttestationRecord() {
        AuthorizationContext authContext =
                new AuthorizationContext(
                        OWNER_ID,
                        OWNER_ID_CERT_DIGEST,
                        mODPAuthorizationTokenDao,
                        mMockKeyAttestation,
                        mClock);
        // Pretend 1st try failed.
        authContext.updateAuthState(AUTH_METADATA, mTrainingEventLogger);
        return authContext;
    }

    private String createOutputCheckpointFile() throws Exception {
        String testUriPrefix =
                "android.resource://com.android.ondevicepersonalization.federatedcomputetests/raw/";
        File outputCheckpointFile = File.createTempFile("output", ".ckp");
        Context context = ApplicationProvider.getApplicationContext();
        Uri checkpointUri = Uri.parse(testUriPrefix + "federation_test_checkpoint_client");
        InputStream in = context.getContentResolver().openInputStream(checkpointUri);
        java.nio.file.Files.copy(in, outputCheckpointFile.toPath(), REPLACE_EXISTING);
        in.close();
        outputCheckpointFile.deleteOnExit();
        return outputCheckpointFile.getAbsolutePath();
    }

    private FederatedComputeHttpResponse createPlanHttpResponse() {
        byte[] clientOnlyPlan = TrainingTestUtil.createFederatedAnalyticClientPlan().toByteArray();
        return new FederatedComputeHttpResponse.Builder()
                .setStatusCode(200)
                .setHeaders(mSupportCompression ? compressionHeaderList() : new HashMap<>())
                .setPayload(mSupportCompression ? compressWithGzip(clientOnlyPlan) : clientOnlyPlan)
                .build();
    }

    private void setUpHttpFederatedProtocol() {
        setUpHttpFederatedProtocol(
                createStartTaskAssignmentHttpResponse(),
                createPlanHttpResponse(),
                checkpointHttpResponse(),
                createReportResultHttpResponse(),
                SUCCESS_EMPTY_HTTP_RESPONSE);
    }

    private FederatedComputeHttpResponse checkpointHttpResponse() {
        return new FederatedComputeHttpResponse.Builder()
                .setStatusCode(200)
                .setPayload(mSupportCompression ? compressWithGzip(CHECKPOINT) : CHECKPOINT)
                .setHeaders(mSupportCompression ? compressionHeaderList() : new HashMap<>())
                .build();
    }

    private void setUpHttpFederatedProtocol(
            FederatedComputeHttpResponse createTaskAssignmentResponse,
            FederatedComputeHttpResponse planHttpResponse,
            FederatedComputeHttpResponse checkpointHttpResponse,
            FederatedComputeHttpResponse reportResultHttpResponse,
            FederatedComputeHttpResponse uploadResultHttpResponse) {
        doAnswer(
                        invocation -> {
                            FederatedComputeHttpRequest httpRequest = invocation.getArgument(0);
                            String uri = httpRequest.getUri();
                            if (uri.equals(PLAN_URI)) {
                                return immediateFuture(planHttpResponse);
                            } else if (uri.equals(CHECKPOINT_URI)) {
                                return immediateFuture(checkpointHttpResponse);
                            } else if (uri.equals(START_TASK_ASSIGNMENT_URI)) {
                                return immediateFuture(createTaskAssignmentResponse);
                            } else if (uri.equals(REPORT_RESULT_URI)) {
                                return immediateFuture(reportResultHttpResponse);
                            } else if (uri.equals(UPLOAD_LOCATION_URI)) {
                                return immediateFuture(uploadResultHttpResponse);
                            }
                            return immediateFuture(SUCCESS_EMPTY_HTTP_RESPONSE);
                        })
                .when(mMockHttpClient)
                .performRequestAsyncWithRetry(mHttpRequestCaptor.capture());
    }

    private HashMap<String, List<String>> compressionHeaderList() {
        HashMap<String, List<String>> headerList = new HashMap<>();
        headerList.put(CONTENT_ENCODING_HDR, ImmutableList.of(GZIP_ENCODING_HDR));
        headerList.put(CONTENT_TYPE_HDR, ImmutableList.of(OCTET_STREAM));
        return headerList;
    }

    private FederatedComputeHttpResponse createReportResultHttpResponse() {
        UploadInstruction.Builder uploadInstruction =
                UploadInstruction.newBuilder().setUploadLocation(UPLOAD_LOCATION_URI);
        uploadInstruction.putExtraRequestHeaders(CONTENT_TYPE_HDR, OCTET_STREAM);
        if (mSupportCompression) {
            uploadInstruction.putExtraRequestHeaders(CONTENT_ENCODING_HDR, GZIP_ENCODING_HDR);
            uploadInstruction.setCompressionFormat(
                    ResourceCompressionFormat.RESOURCE_COMPRESSION_FORMAT_GZIP);
        }
        ReportResultResponse reportResultResponse =
                ReportResultResponse.newBuilder()
                        .setUploadInstruction(uploadInstruction.build())
                        .build();
        return new FederatedComputeHttpResponse.Builder()
                .setStatusCode(200)
                .setPayload(reportResultResponse.toByteArray())
                .build();
    }

    private FederatedComputeHttpResponse createStartTaskAssignmentHttpResponse() {
        CreateTaskAssignmentResponse createTaskAssignmentResponse =
                createCreateTaskAssignmentResponse(
                        Resource.newBuilder()
                                .setUri(PLAN_URI)
                                .setCompressionFormat(
                                        mSupportCompression
                                                ? ResourceCompressionFormat
                                                        .RESOURCE_COMPRESSION_FORMAT_GZIP
                                                : ResourceCompressionFormat
                                                        .RESOURCE_COMPRESSION_FORMAT_UNSPECIFIED)
                                .build(),
                        Resource.newBuilder()
                                .setUri(CHECKPOINT_URI)
                                .setCompressionFormat(
                                        mSupportCompression
                                                ? ResourceCompressionFormat
                                                        .RESOURCE_COMPRESSION_FORMAT_GZIP
                                                : ResourceCompressionFormat
                                                        .RESOURCE_COMPRESSION_FORMAT_UNSPECIFIED)
                                .build());

        return new FederatedComputeHttpResponse.Builder()
                .setStatusCode(200)
                .setPayload(createTaskAssignmentResponse.toByteArray())
                .build();
    }

    private FederatedComputeHttpResponse createUnauthorizedResponse() {
        return new FederatedComputeHttpResponse.Builder().setStatusCode(403).build();
    }

    private CreateTaskAssignmentResponse createCreateTaskAssignmentResponse(
            Resource plan, Resource checkpoint) {
        TaskAssignment taskAssignment =
                TaskAssignment.newBuilder()
                        .setPopulationName(POPULATION_NAME)
                        .setAggregationId(AGGREGATION_ID)
                        .setTaskId(TASK_ID)
                        .setAssignmentId(ASSIGNMENT_ID)
                        .setPlan(plan)
                        .setInitCheckpoint(checkpoint)
                        .build();
        return CreateTaskAssignmentResponse.newBuilder().setTaskAssignment(taskAssignment).build();
    }

    private ODPAuthorizationToken createAuthToken() {
        return new ODPAuthorizationToken.Builder(
                        OWNER_ID,
                        TOKEN,
                        mClock.currentTimeMillis(),
                        mClock.currentTimeMillis() + ODP_AUTHORIZATION_TOKEN_TTL)
                .build();
    }

    private FederatedComputeHttpResponse createUnauthenticatedResponse() {
        CreateTaskAssignmentResponse payload =
                CreateTaskAssignmentResponse.newBuilder()
                        .setRejectionInfo(
                                RejectionInfo.newBuilder()
                                        .setAuthMetadata(AUTH_METADATA)
                                        .setReason(RejectionReason.Enum.UNAUTHENTICATED)
                                        .build())
                        .build();
        return new FederatedComputeHttpResponse.Builder()
                .setStatusCode(HTTP_UNAUTHENTICATED_STATUS)
                .setPayload(payload.toByteArray())
                .setHeaders(new HashMap<>())
                .build();
    }

    private void checkActualTARequest(
            FederatedComputeHttpRequest actualStartTaskAssignmentRequest, int headerSize) {
        assertThat(actualStartTaskAssignmentRequest.getUri()).isEqualTo(START_TASK_ASSIGNMENT_URI);
        assertThat(actualStartTaskAssignmentRequest.getHttpMethod()).isEqualTo(HttpMethod.POST);

        // check header
        HashMap<String, String> expectedHeaders = new HashMap<>();
        assertThat(actualStartTaskAssignmentRequest.getBody())
                .isEqualTo(START_TASK_ASSIGNMENT_REQUEST_WITH_COMPRESSION.toByteArray());
        expectedHeaders.put(CONTENT_LENGTH_HDR, String.valueOf(23));
        expectedHeaders.put(CONTENT_TYPE_HDR, PROTOBUF_CONTENT_TYPE);
        expectedHeaders.put(FCP_OWNER_ID_DIGEST, OWNER_ID + "-" + OWNER_ID_CERT_DIGEST);
        assertThat(actualStartTaskAssignmentRequest.getExtraHeaders())
                .containsAtLeastEntriesIn(expectedHeaders);
        assertThat(actualStartTaskAssignmentRequest.getExtraHeaders()).hasSize(headerSize);
        String idempotencyKey =
                actualStartTaskAssignmentRequest.getExtraHeaders().get(ODP_IDEMPOTENCY_KEY);
        assertNotNull(idempotencyKey);
        String timestamp = idempotencyKey.split(" - ")[0];
        assertThat(Long.parseLong(timestamp)).isLessThan(System.currentTimeMillis());
        assertThat(actualStartTaskAssignmentRequest.getExtraHeaders())
                .containsAtLeastEntriesIn(expectedHeaders);
    }

    private void checkActualReportResultRequest(
            FederatedComputeHttpRequest actualReportResultRequest) {
        assertThat(actualReportResultRequest.getUri()).isEqualTo(REPORT_RESULT_URI);
        assertThat(actualReportResultRequest.getHttpMethod()).isEqualTo(HttpMethod.PUT);
        HashMap<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put(CONTENT_LENGTH_HDR, String.valueOf(2));
        expectedHeaders.put(CONTENT_TYPE_HDR, PROTOBUF_CONTENT_TYPE);
        assertThat(actualReportResultRequest.getExtraHeaders())
                .containsAtLeastEntriesIn(expectedHeaders);
    }
}
