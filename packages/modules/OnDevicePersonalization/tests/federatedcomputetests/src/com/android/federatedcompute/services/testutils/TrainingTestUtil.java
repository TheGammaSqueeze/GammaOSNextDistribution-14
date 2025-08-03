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

package com.android.federatedcompute.services.testutils;

import com.google.internal.federated.plan.AggregationConfig;
import com.google.internal.federated.plan.ClientOnlyPlan;
import com.google.internal.federated.plan.ClientPhase;
import com.google.internal.federated.plan.ExampleQuerySpec;
import com.google.internal.federated.plan.ExampleQuerySpec.ExampleQuery;
import com.google.internal.federated.plan.ExampleQuerySpec.OutputVectorSpec;
import com.google.internal.federated.plan.ExampleQuerySpec.OutputVectorSpec.DataType;
import com.google.internal.federated.plan.ExampleSelector;
import com.google.internal.federated.plan.FederatedExampleQueryIORouter;
import com.google.internal.federated.plan.TFV1CheckpointAggregation;
import com.google.internal.federated.plan.TensorflowSpec;
import com.google.protobuf.ByteString;

/** The utility class for federated learning related tests. */
public class TrainingTestUtil {
    public static final String STRING_VECTOR_NAME = "vector1";
    public static final String INT_VECTOR_NAME = "vector2";
    public static final String STRING_TENSOR_NAME = "tensor1";
    public static final String INT_TENSOR_NAME = "tensor2";
    public static final String COLLECTION_URI = "app:/test_collection";

    private TrainingTestUtil() {}

    public static ClientOnlyPlan createFederatedAnalyticClientPlan() {
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
                                        .setCollectionUri(COLLECTION_URI)
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
        return clientOnlyPlan;
    }

    public static ClientOnlyPlan createFakeFederatedLearningClientPlan() {
        TensorflowSpec tensorflowSpec =
                TensorflowSpec.newBuilder()
                        .setDatasetTokenTensorName("dataset")
                        .addTargetNodeNames("target")
                        .build();
        ClientOnlyPlan clientOnlyPlan =
                ClientOnlyPlan.newBuilder()
                        .setTfliteGraph(ByteString.copyFromUtf8("tflite_graph"))
                        .setPhase(
                                ClientPhase.newBuilder().setTensorflowSpec(tensorflowSpec).build())
                        .build();
        return clientOnlyPlan;
    }
}
