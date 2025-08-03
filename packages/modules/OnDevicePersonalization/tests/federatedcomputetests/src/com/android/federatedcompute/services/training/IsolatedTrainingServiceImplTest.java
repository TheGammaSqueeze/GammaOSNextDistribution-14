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

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import android.federatedcompute.common.ClientConstants;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.federatedcompute.services.common.Constants;
import com.android.federatedcompute.services.common.FederatedComputeExecutors;
import com.android.federatedcompute.services.common.FileUtils;
import com.android.federatedcompute.services.testutils.FakeExampleStoreIterator;
import com.android.federatedcompute.services.testutils.TrainingTestUtil;
import com.android.federatedcompute.services.training.aidl.ITrainingResultCallback;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.intelligence.fcp.client.FLRunnerResult;
import com.google.intelligence.fcp.client.FLRunnerResult.ContributionResult;
import com.google.intelligence.fcp.client.RetryInfo;
import com.google.intelligence.fcp.client.engine.TaskRetry;
import com.google.internal.federated.plan.ClientOnlyPlan;
import com.google.internal.federated.plan.ExampleSelector;
import com.google.protobuf.Duration;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.quality.Strictness;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
@MockStatic(FederatedComputeExecutors.class)
public final class IsolatedTrainingServiceImplTest {

    @Rule
    public final ExtendedMockitoRule extendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    private static final String POPULATION_NAME = "population_name";
    private static final String TASK_NAME = "task_name";
    private static final long RUN_ID = 12345L;
    private static final long TIMEOUT_MILLI = 5000;
    private static final FakeExampleStoreIterator FAKE_EXAMPLE_STORE_ITERATOR =
            new FakeExampleStoreIterator(ImmutableList.of());
    private static final ExampleSelector EXAMPLE_SELECTOR =
            ExampleSelector.newBuilder().setCollectionUri("collection_uri").build();
    private static final TaskRetry TASK_RETRY =
            TaskRetry.newBuilder().setRetryToken("foobar").build();
    private static final FLRunnerResult FL_RUNNER_SUCCESS_RESULT =
            FLRunnerResult.newBuilder()
                    .setContributionResult(ContributionResult.SUCCESS)
                    .setRetryInfo(
                            RetryInfo.newBuilder()
                                    .setRetryToken(TASK_RETRY.getRetryToken())
                                    .build())
                    .build();
    private static final FLRunnerResult FL_RUNNER_FAIL_RESULT =
            FLRunnerResult.newBuilder().setContributionResult(ContributionResult.FAIL).build();
    private IsolatedTrainingServiceImpl mIsolatedTrainingService;
    private Bundle mCallbackResult;
    @Mock private ComputationRunner mComputationRunner;
    private ParcelFileDescriptor mInputCheckpointFd;
    private ParcelFileDescriptor mOutputCheckpointFd;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        ExtendedMockito.doReturn(MoreExecutors.newDirectExecutorService())
                .when(FederatedComputeExecutors::getBackgroundExecutor);
        ExtendedMockito.doReturn(MoreExecutors.newDirectExecutorService())
                .when(FederatedComputeExecutors::getLightweightExecutor);

        mIsolatedTrainingService = new IsolatedTrainingServiceImpl(mComputationRunner);
        mInputCheckpointFd = getInputCheckpointFd();
        mOutputCheckpointFd = getOutputCheckpointFd();
    }

    @After
    public void tearDown() throws Exception {
        mInputCheckpointFd.close();
        mOutputCheckpointFd.close();
    }

    @Test
    public void runFlTrainingSuccess() throws Exception {
        when(mComputationRunner.runTaskWithNativeRunner(
                        anyString(), anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(FL_RUNNER_SUCCESS_RESULT);
        Bundle bundle = buildInputBundle();

        var callback = new TestServiceCallback();
        mIsolatedTrainingService.runFlTraining(bundle, callback);

        assertTrue(callback.mLatch.await(TIMEOUT_MILLI, TimeUnit.MILLISECONDS));
        byte[] flRunnerResultBytes = mCallbackResult.getByteArray(Constants.EXTRA_FL_RUNNER_RESULT);
        FLRunnerResult flRunnerResult = FLRunnerResult.parseFrom(flRunnerResultBytes);
        assertThat(flRunnerResult).isEqualTo(FL_RUNNER_SUCCESS_RESULT);
    }

    @Test
    public void runFlTrainingFailure() throws Exception {
        when(mComputationRunner.runTaskWithNativeRunner(
                        anyString(), anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(FL_RUNNER_FAIL_RESULT);
        Bundle bundle = buildInputBundle();

        var callback = new TestServiceCallback();
        mIsolatedTrainingService.runFlTraining(bundle, callback);

        assertTrue(callback.mLatch.await(TIMEOUT_MILLI, TimeUnit.MILLISECONDS));
        assertFailResult();
    }

    @Test
    public void runFlTrainingFailureWithRte() throws Exception {
        when(mComputationRunner.runTaskWithNativeRunner(
                        anyString(), anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(RuntimeException.class);
        Bundle bundle = buildInputBundle();

        var callback = new TestServiceCallback();
        mIsolatedTrainingService.runFlTraining(bundle, callback);

        assertTrue(callback.mLatch.await(TIMEOUT_MILLI, TimeUnit.MILLISECONDS));
        assertFailResult();
    }

    @Test
    public void runFlTrainingFailureWithIae() throws Exception {
        when(mComputationRunner.runTaskWithNativeRunner(
                        anyString(), anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(IllegalArgumentException.class);
        Bundle bundle = buildInputBundle();

        var callback = new TestServiceCallback();
        mIsolatedTrainingService.runFlTraining(bundle, callback);

        assertTrue(callback.mLatch.await(TIMEOUT_MILLI, TimeUnit.MILLISECONDS));
        assertFailResult();
    }

    @Test
    public void runFlTrainingNullBundle() {
        var callback = new TestServiceCallback();
        assertThrows(
                NullPointerException.class,
                () -> mIsolatedTrainingService.runFlTraining(null, callback));
    }

    @Test
    public void runFlTrainingNullCallback() {
        Bundle bundle = new Bundle();
        assertThrows(
                NullPointerException.class,
                () -> mIsolatedTrainingService.runFlTraining(bundle, null));
    }

    @Test
    public void runFlTrainingMissingExampleSelector_returnsFailure() throws Exception {
        Bundle bundle = new Bundle();
        bundle.putString(ClientConstants.EXTRA_POPULATION_NAME, POPULATION_NAME);
        bundle.putParcelable(Constants.EXTRA_INPUT_CHECKPOINT_FD, mInputCheckpointFd);
        bundle.putParcelable(Constants.EXTRA_OUTPUT_CHECKPOINT_FD, mOutputCheckpointFd);
        bundle.putBinder(
                Constants.EXTRA_EXAMPLE_STORE_ITERATOR_BINDER, FAKE_EXAMPLE_STORE_ITERATOR);

        var callback = new TestServiceCallback();
        mIsolatedTrainingService.runFlTraining(bundle, callback);

        assertTrue(callback.mLatch.await(TIMEOUT_MILLI, TimeUnit.MILLISECONDS));
        assertFailResult();
    }

    @Test
    public void runFlTrainingInvalidExampleSelector_returnsFailure() throws Exception {
        Bundle bundle = new Bundle();
        bundle.putString(ClientConstants.EXTRA_POPULATION_NAME, POPULATION_NAME);
        bundle.putParcelable(Constants.EXTRA_INPUT_CHECKPOINT_FD, mInputCheckpointFd);
        bundle.putParcelable(Constants.EXTRA_OUTPUT_CHECKPOINT_FD, mOutputCheckpointFd);
        bundle.putBinder(
                Constants.EXTRA_EXAMPLE_STORE_ITERATOR_BINDER, FAKE_EXAMPLE_STORE_ITERATOR);

        bundle.putByteArray(Constants.EXTRA_EXAMPLE_SELECTOR, "exampleselector".getBytes());

        var callback = new TestServiceCallback();
        mIsolatedTrainingService.runFlTraining(bundle, callback);

        assertTrue(callback.mLatch.await(TIMEOUT_MILLI, TimeUnit.MILLISECONDS));
        assertFailResult();
    }

    @Test
    public void runFlTrainingNullPlan_returnsFailure() throws Exception {
        Bundle bundle = new Bundle();
        bundle.putString(ClientConstants.EXTRA_POPULATION_NAME, POPULATION_NAME);
        bundle.putParcelable(Constants.EXTRA_INPUT_CHECKPOINT_FD, mInputCheckpointFd);
        bundle.putParcelable(Constants.EXTRA_OUTPUT_CHECKPOINT_FD, mOutputCheckpointFd);
        bundle.putBinder(
                Constants.EXTRA_EXAMPLE_STORE_ITERATOR_BINDER, FAKE_EXAMPLE_STORE_ITERATOR);
        bundle.putByteArray(Constants.EXTRA_EXAMPLE_SELECTOR, EXAMPLE_SELECTOR.toByteArray());

        var callback = new TestServiceCallback();
        mIsolatedTrainingService.runFlTraining(bundle, callback);

        assertTrue(callback.mLatch.await(TIMEOUT_MILLI, TimeUnit.MILLISECONDS));
        assertFailResult();
    }

    @Test
    public void runCancelFlTraining() {
        assertThat(mIsolatedTrainingService.mInterruptState.get()).isFalse();
        mIsolatedTrainingService.cancelTraining(RUN_ID);

        assertThat(mIsolatedTrainingService.mInterruptState.get()).isTrue();
    }

    private void assertFailResult() throws Exception {
        byte[] flRunnerResultBytes = mCallbackResult.getByteArray(Constants.EXTRA_FL_RUNNER_RESULT);
        FLRunnerResult flRunnerResult = FLRunnerResult.parseFrom(flRunnerResultBytes);
        assertThat(flRunnerResult)
                .isEqualTo(
                        FL_RUNNER_FAIL_RESULT.toBuilder()
                                .setRetryInfo(
                                        RetryInfo.newBuilder()
                                                .setMinimumDelay(
                                                        Duration.newBuilder().setSeconds(86400)))
                                .build());
    }

    private Bundle buildInputBundle() throws Exception {
        Bundle bundle = new Bundle();
        bundle.putString(ClientConstants.EXTRA_POPULATION_NAME, POPULATION_NAME);
        bundle.putString(ClientConstants.EXTRA_TASK_ID, TASK_NAME);
        bundle.putParcelable(Constants.EXTRA_INPUT_CHECKPOINT_FD, mInputCheckpointFd);
        bundle.putParcelable(Constants.EXTRA_OUTPUT_CHECKPOINT_FD, mOutputCheckpointFd);
        bundle.putByteArray(Constants.EXTRA_EXAMPLE_SELECTOR, EXAMPLE_SELECTOR.toByteArray());
        bundle.putBinder(
                Constants.EXTRA_EXAMPLE_STORE_ITERATOR_BINDER, FAKE_EXAMPLE_STORE_ITERATOR);
        ClientOnlyPlan clientOnlyPlan = TrainingTestUtil.createFederatedAnalyticClientPlan();
        String clientPlanFile =
                FileUtils.createTempFile(Constants.EXTRA_CLIENT_ONLY_PLAN_FD, ".pb");
        FileUtils.writeToFile(clientPlanFile, clientOnlyPlan.toByteArray());
        bundle.putParcelable(
                Constants.EXTRA_CLIENT_ONLY_PLAN_FD,
                FileUtils.createTempFileDescriptor(
                        clientPlanFile, ParcelFileDescriptor.MODE_READ_ONLY));
        return bundle;
    }

    private ParcelFileDescriptor getInputCheckpointFd() throws Exception {
        File inputCheckpointFile = File.createTempFile("input", ".ckp");
        return ParcelFileDescriptor.open(inputCheckpointFile, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private ParcelFileDescriptor getOutputCheckpointFd() throws Exception {
        File outputCheckpointFile = File.createTempFile("output", ".ckp");
        return ParcelFileDescriptor.open(
                outputCheckpointFile, ParcelFileDescriptor.MODE_WRITE_ONLY);
    }

    class TestServiceCallback extends ITrainingResultCallback.Stub {
        private final CountDownLatch mLatch = new CountDownLatch(1);

        @Override
        public void onResult(Bundle result) {
            mCallbackResult = result;
            mLatch.countDown();
        }
    }
}
