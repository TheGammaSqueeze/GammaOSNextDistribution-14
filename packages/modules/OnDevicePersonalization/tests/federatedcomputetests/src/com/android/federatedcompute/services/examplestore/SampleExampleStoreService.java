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

package com.android.federatedcompute.services.examplestore;

import static android.federatedcompute.common.ClientConstants.EXTRA_EXAMPLE_ITERATOR_RESULT;
import static android.federatedcompute.common.ClientConstants.EXTRA_TASK_ID;
import static android.federatedcompute.common.ClientConstants.STATUS_INTERNAL_ERROR;

import android.annotation.NonNull;
import android.federatedcompute.ExampleStoreIterator;
import android.federatedcompute.ExampleStoreService;
import android.os.Bundle;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;

import org.tensorflow.example.BytesList;
import org.tensorflow.example.Example;
import org.tensorflow.example.Feature;
import org.tensorflow.example.Features;

import java.util.Iterator;
import java.util.List;

/** A sample ExampleStoreService implementation. */
public class SampleExampleStoreService extends ExampleStoreService {
    private static final String EXPECTED_TASK_NAME = "test_task";
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

    @Override
    public void startQuery(@NonNull Bundle params, @NonNull QueryCallback callback) {
        String collection = params.getString(EXTRA_TASK_ID);
        if (!collection.equals(EXPECTED_TASK_NAME)) {
            callback.onStartQueryFailure(STATUS_INTERNAL_ERROR);
            return;
        }
        callback.onStartQuerySuccess(
                new ListExampleStoreIterator(ImmutableList.of(EXAMPLE_PROTO_1)));
    }

    @Override
    protected boolean checkCallerPermission() {
        return true;
    }

    /**
     * A simple {@link ExampleStoreIterator} that returns the contents of the {@link List} it's
     * constructed with.
     */
    private static class ListExampleStoreIterator implements ExampleStoreIterator {
        private final Iterator<Example> mExampleIterator;

        ListExampleStoreIterator(List<Example> examples) {
            mExampleIterator = examples.iterator();
        }

        @Override
        public synchronized void next(IteratorCallback callback) {
            if (mExampleIterator.hasNext()) {
                Bundle bundle = new Bundle();
                bundle.putByteArray(
                        EXTRA_EXAMPLE_ITERATOR_RESULT, mExampleIterator.next().toByteArray());
                callback.onIteratorNextSuccess(bundle);
            } else {
                callback.onIteratorNextSuccess(null);
            }
        }

        @Override
        public void close() {}
    }
}
