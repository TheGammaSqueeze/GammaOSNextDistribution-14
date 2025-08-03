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

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.ErrorStatusException;
import com.android.federatedcompute.services.examplestore.ExampleIterator;

import com.google.intelligence.fcp.client.SelectorContext;
import com.google.internal.federated.plan.ExampleSelector;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.concurrent.GuardedBy;

/**
 * Java class that forms the connective tissue between C++ code and Java example stores. C++ code
 * (e.g. tflite plan engine, or Dataset op) uses this class to create example iterators and retrieve
 * examples, and the class takes care of closing leftover iterators as well as storing unexpected
 * exceptions for later re-throwing.
 */
public class JavaExampleStore implements Closeable {
    private static final String TAG = JavaExampleStore.class.getSimpleName();

    private final ExampleIterator mExampleIterator;
    private final Object mIteratorLock = new Object();

    @GuardedBy("mIteratorLock")
    private final List<ExampleIterator> mServedExampleIterators;

    public JavaExampleStore(ExampleIterator exampleIterator) {
        this.mExampleIterator = exampleIterator;
        this.mServedExampleIterators = new ArrayList<>();
    }

    /** Creates an ExampleIterator based on provided contexts. */
    public JavaExampleIterator createExampleIteratorWithContext(
            byte[] exampleSelector, byte[] selectorContext) {
        ExampleSelector selector;
        // 1. Deserialize the ExampleSelector. The ExampleIterator is already validated
        // and created ahead. We only do validation here but not crash if it goes wrong.
        try {
            selector = ExampleSelector.parseFrom(exampleSelector);
            SelectorContext.parseFrom(selectorContext);
        } catch (InvalidProtocolBufferException e) {
            LogUtil.e(TAG, "Invalid protobuf message", e);
        }

        // 2. Stores ExampleIterator in a list so we can close it after training.
        synchronized (mIteratorLock) {
            mServedExampleIterators.add(this.mExampleIterator);
        }
        // 3. Wrap the ExampleIterator in a {@link JavaExampleIterator} that translates
        // exceptions.
        JavaExampleIterator javaExampleIterator =
                new JavaExampleIterator() {
                    @GuardedBy("mIteratorLock")
                    final com.android.federatedcompute.services.examplestore.ExampleIterator
                            mIterator = mExampleIterator;

                    @Override
                    public byte[] next() throws InterruptedException, ErrorStatusException {
                        synchronized (mIteratorLock) {
                            try {
                                boolean hasNext = mIterator.hasNext();
                                if (!hasNext) {
                                    return new byte[0];
                                }
                                return mIterator.next();
                            } catch (InterruptedException e) {
                                LogUtil.e(TAG, "ExampleStore.next()", e);
                                throw e;
                            } catch (ErrorStatusException e) {
                                LogUtil.e(TAG, "ExampleStore.next()", e);
                                throw e;
                            }
                        }
                    }

                    @Override
                    public void close() {

                        // Avoid closing an iterator twice. We do this by
                        // keeping open iterators  in {@link
                        // mServedExampleIterators} and removing them when
                        // closing. If the list does not contain the
                        // iterator anymore, it has already been closed, and
                        // we avoid closing it again.
                        boolean iteratorOpen;
                        synchronized (mIteratorLock) {
                            iteratorOpen = mServedExampleIterators.remove(mExampleIterator);
                            if (iteratorOpen) {
                                mIterator.close();
                            }
                        }
                    }
                };
        return javaExampleIterator;
    }

    @Override
    public void close() {
        // Close remaining open iterators, if any. This can happen when C++ code fails
        // and returns via an error path that does not close the iterators.
        synchronized (mIteratorLock) {
            for (ExampleIterator exampleIterator : mServedExampleIterators) {
                // TODO(b/283309324): add metrics to track iterator left open case.
                LogUtil.e(TAG, "Close left open iterator");
                exampleIterator.close();
            }
        }
    }
}
