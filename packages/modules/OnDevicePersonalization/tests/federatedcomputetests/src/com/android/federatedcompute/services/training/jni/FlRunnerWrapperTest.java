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

package com.android.federatedcompute.services.training.jni;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.test.core.app.ApplicationProvider;

import com.android.federatedcompute.services.examplestore.ExampleIterator;
import com.android.federatedcompute.services.testutils.ResettingExampleIterator;
import com.android.federatedcompute.services.testutils.TrainingTestUtil;
import com.android.federatedcompute.services.training.util.ListenableSupplier;

import com.google.intelligence.fcp.client.ExampleQueryResult;
import com.google.intelligence.fcp.client.ExampleQueryResult.VectorData;
import com.google.intelligence.fcp.client.ExampleQueryResult.VectorData.Int64Values;
import com.google.intelligence.fcp.client.ExampleQueryResult.VectorData.StringValues;
import com.google.intelligence.fcp.client.ExampleQueryResult.VectorData.Values;
import com.google.intelligence.fcp.client.FLRunnerResult;
import com.google.intelligence.fcp.client.FLRunnerResult.ContributionResult;
import com.google.internal.federated.plan.AggregationConfig;
import com.google.internal.federated.plan.ClientOnlyPlan;
import com.google.internal.federated.plan.ClientPhase;
import com.google.internal.federated.plan.Dataset;
import com.google.internal.federated.plan.Dataset.ClientDataset;
import com.google.internal.federated.plan.ExampleQuerySpec;
import com.google.internal.federated.plan.ExampleQuerySpec.ExampleQuery;
import com.google.internal.federated.plan.ExampleQuerySpec.OutputVectorSpec;
import com.google.internal.federated.plan.ExampleQuerySpec.OutputVectorSpec.DataType;
import com.google.internal.federated.plan.ExampleSelector;
import com.google.internal.federated.plan.FederatedExampleQueryIORouter;
import com.google.internal.federated.plan.TFV1CheckpointAggregation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

@RunWith(JUnit4.class)
public final class FlRunnerWrapperTest {
    private static final String SESSION_NAME = "session_name";
    private static final String TASK_NAME = "task_name";
    private static final String POPULATION_NAME = "population_name";
    private static final String STRING_VECTOR_NAME = "vector1";
    private static final String INT_VECTOR_NAME = "vector2";
    private static final String STRING_TENSOR_NAME = "tensor1";
    private static final String INT_TENSOR_NAME = "tensor2";
    private static final String TEST_URI_PREFIX =
            "android.resource://com.android.ondevicepersonalization.federatedcomputetests/raw/";

    @Mock ListenableSupplier<Boolean> mInterruptionFlag;
    @Mock ExampleIterator mExampleIterator;

    private FlRunnerWrapper mFlRunnerWrapper;

    @Before
    public void doBeforeEachTest() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mInterruptionFlag.get()).thenReturn(false);
    }

    @After
    public void tearDown() throws Exception {
        mFlRunnerWrapper.close();
    }

    @Test
    public void testRunInvalidPlan_returnFail() throws Exception {
        File inputCheckpointFile = File.createTempFile("input", ".ckp");
        File outputCheckpointFile = File.createTempFile("output", ".ckp");
        ClientOnlyPlan invalidClientOnlyPlan = ClientOnlyPlan.getDefaultInstance();
        when(mExampleIterator.hasNext()).thenReturn(false);

        mFlRunnerWrapper =
                new FlRunnerWrapper(mInterruptionFlag, POPULATION_NAME, mExampleIterator);

        FLRunnerResult result =
                mFlRunnerWrapper.run(
                        TASK_NAME,
                        POPULATION_NAME,
                        invalidClientOnlyPlan,
                        inputCheckpointFile.getAbsolutePath(),
                        outputCheckpointFile.getAbsolutePath());

        assertThat(result.getContributionResult()).isEqualTo(ContributionResult.FAIL);
    }

    @Test
    public void testRunFederatedAnalytics_returnSuccess() throws Exception {
        ClientOnlyPlan clientOnlyPlan = TrainingTestUtil.createFederatedAnalyticClientPlan();
        Values stringValues =
                Values.newBuilder()
                        .setStringValues(
                                StringValues.newBuilder()
                                        .addValue("value1")
                                        .addValue("value2")
                                        .build())
                        .build();
        Values intValues =
                Values.newBuilder()
                        .setInt64Values(Int64Values.newBuilder().addValue(42).addValue(24).build())
                        .build();
        ExampleQueryResult queryResult =
                ExampleQueryResult.newBuilder()
                        .setVectorData(
                                VectorData.newBuilder()
                                        .putVectors(STRING_VECTOR_NAME, stringValues)
                                        .putVectors(INT_VECTOR_NAME, intValues))
                        .build();
        ClientDataset clientDataset =
                ClientDataset.newBuilder()
                        .setClientId("clientId")
                        .addExample(queryResult.toByteString())
                        .build();

        Dataset dataset = Dataset.newBuilder().addClientData(clientDataset).build();
        File inputCheckpointFile = File.createTempFile("input", ".ckp");
        File outputCheckpointFile = File.createTempFile("output", ".ckp");

        ResettingExampleIterator resettingExampleIterator =
                new ResettingExampleIterator(dataset.getClientDataCount(), dataset);

        mFlRunnerWrapper =
                new FlRunnerWrapper(mInterruptionFlag, POPULATION_NAME, resettingExampleIterator);

        FLRunnerResult result =
                mFlRunnerWrapper.run(
                        TASK_NAME,
                        POPULATION_NAME,
                        clientOnlyPlan,
                        inputCheckpointFile.getAbsolutePath(),
                        outputCheckpointFile.getAbsolutePath());

        assertThat(result.getContributionResult()).isEqualTo(ContributionResult.SUCCESS);
        byte[] content = Files.readAllBytes(outputCheckpointFile.toPath());
        assertTrue(content.length > 0);
    }

    @Test
    public void testInvalidCollection_returnsFail() throws Exception {
        OutputVectorSpec stringVectorSpec =
                OutputVectorSpec.newBuilder()
                        .setVectorName(STRING_VECTOR_NAME)
                        .setDataType(DataType.STRING)
                        .build();
        OutputVectorSpec intVectorSpec =
                OutputVectorSpec.newBuilder()
                        .setVectorName(INT_VECTOR_NAME)
                        .setDataType(DataType.INT64)
                        .build();

        ExampleQuery exampleQuery =
                ExampleQuery.newBuilder()
                        .setExampleSelector(
                                ExampleSelector.newBuilder()
                                        .setCollectionUri("app://com.foo.bar/inapp/collection1#")
                                        .build())
                        .putOutputVectorSpecs(STRING_TENSOR_NAME, stringVectorSpec)
                        .putOutputVectorSpecs(INT_TENSOR_NAME, intVectorSpec)
                        .build();
        AggregationConfig aggregationConfig =
                AggregationConfig.newBuilder()
                        .setTfV1CheckpointAggregation(
                                TFV1CheckpointAggregation.getDefaultInstance())
                        .build();
        FederatedExampleQueryIORouter ioRouter =
                FederatedExampleQueryIORouter.newBuilder()
                        .putAggregations(STRING_TENSOR_NAME, aggregationConfig)
                        .putAggregations(INT_TENSOR_NAME, aggregationConfig)
                        .build();
        ClientOnlyPlan clientOnlyPlan =
                ClientOnlyPlan.newBuilder()
                        .setPhase(
                                ClientPhase.newBuilder()
                                        .setFederatedExampleQuery(ioRouter)
                                        .setExampleQuerySpec(
                                                ExampleQuerySpec.newBuilder()
                                                        .addExampleQueries(exampleQuery)
                                                        .build()))
                        .build();

        File inputCheckpointFile = File.createTempFile("input", ".ckp");
        File outputCheckpointFile = File.createTempFile("output", ".ckp");
        ResettingExampleIterator resettingExampleIterator =
                new ResettingExampleIterator(0, Dataset.getDefaultInstance());

        mFlRunnerWrapper =
                new FlRunnerWrapper(mInterruptionFlag, POPULATION_NAME, resettingExampleIterator);

        FLRunnerResult result =
                mFlRunnerWrapper.run(
                        TASK_NAME,
                        POPULATION_NAME,
                        clientOnlyPlan,
                        inputCheckpointFile.getAbsolutePath(),
                        outputCheckpointFile.getAbsolutePath());

        assertThat(result.getContributionResult()).isEqualTo(ContributionResult.FAIL);
    }

    @Test
    public void testEmptyIterator_returnsFail() throws Exception {
        ClientOnlyPlan clientOnlyPlan = TrainingTestUtil.createFederatedAnalyticClientPlan();
        File inputCheckpointFile = File.createTempFile("input", ".ckp");
        File outputCheckpointFile = File.createTempFile("output", ".ckp");
        ResettingExampleIterator resettingExampleIterator =
                new ResettingExampleIterator(0, Dataset.getDefaultInstance());

        mFlRunnerWrapper =
                new FlRunnerWrapper(mInterruptionFlag, POPULATION_NAME, resettingExampleIterator);

        FLRunnerResult result =
                mFlRunnerWrapper.run(
                        TASK_NAME,
                        POPULATION_NAME,
                        clientOnlyPlan,
                        inputCheckpointFile.getAbsolutePath(),
                        outputCheckpointFile.getAbsolutePath());

        assertThat(result.getContributionResult()).isEqualTo(ContributionResult.FAIL);
    }

    @Test
    public void testRunFederatedLearning_returnsSuccess() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Uri checkpointUri = Uri.parse(TEST_URI_PREFIX + "federation_test_checkpoint_client");
        Uri clientOnlyPlanUri = Uri.parse(TEST_URI_PREFIX + "federation_client_only_plan");
        Uri trainExamplesUri = Uri.parse(TEST_URI_PREFIX + "federation_proxy_train_examples");
        File inputCheckpointFile = File.createTempFile("input", ".ckp");
        File outputCheckpointFile = File.createTempFile("output", ".ckp");
        InputStream in = context.getContentResolver().openInputStream(checkpointUri);
        Files.copy(in, inputCheckpointFile.toPath(), REPLACE_EXISTING);
        in.close();
        ParcelFileDescriptor inputCheckpointFd =
                ParcelFileDescriptor.open(inputCheckpointFile, ParcelFileDescriptor.MODE_READ_ONLY);
        ParcelFileDescriptor outputCheckpointFd =
                ParcelFileDescriptor.open(
                        outputCheckpointFile, ParcelFileDescriptor.MODE_WRITE_ONLY);

        in = context.getContentResolver().openInputStream(clientOnlyPlanUri);
        ClientOnlyPlan clientOnlyPlan = ClientOnlyPlan.parseFrom(in);
        in.close();

        in = context.getContentResolver().openInputStream(trainExamplesUri);
        Dataset dataset = Dataset.parseFrom(in);
        in.close();

        ResettingExampleIterator resettingExampleIterator =
                new ResettingExampleIterator(dataset.getClientDataCount(), dataset);
        mFlRunnerWrapper =
                new FlRunnerWrapper(mInterruptionFlag, POPULATION_NAME, resettingExampleIterator);

        FLRunnerResult result =
                mFlRunnerWrapper.run(
                        TASK_NAME,
                        POPULATION_NAME,
                        clientOnlyPlan,
                        getFileDescriptorForTensorflow(inputCheckpointFd),
                        getFileDescriptorForTensorflow(outputCheckpointFd));

        assertThat(result.getContributionResult()).isEqualTo(ContributionResult.SUCCESS);
    }

    // We implement a customized tensorflow filesystem which support file descriptor for read and
    // write. The file format is "fd:///${fd_number}".
    private String getFileDescriptorForTensorflow(ParcelFileDescriptor parcelFileDescriptor) {
        return "fd:///" + parcelFileDescriptor.getFd();
    }
}
