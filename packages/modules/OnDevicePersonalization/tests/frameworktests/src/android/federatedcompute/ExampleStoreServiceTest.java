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

package android.federatedcompute;

import static android.federatedcompute.common.ClientConstants.EXTRA_TASK_ID;
import static android.federatedcompute.common.ClientConstants.STATUS_INTERNAL_ERROR;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;

import android.federatedcompute.ExampleStoreQueryCallbackImpl.IteratorAdapter;
import android.federatedcompute.aidl.IExampleStoreCallback;
import android.federatedcompute.aidl.IExampleStoreIterator;
import android.federatedcompute.aidl.IExampleStoreService;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.tensorflow.example.BytesList;
import org.tensorflow.example.Example;
import org.tensorflow.example.Feature;
import org.tensorflow.example.Features;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import javax.annotation.Nonnull;

@RunWith(AndroidJUnit4.class)
public class ExampleStoreServiceTest {
    private static final String EXPECTED_TASK_NAME = "federated_task";
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
    private IExampleStoreIterator mCallbackResult;
    private int mCallbackErrorCode;
    private boolean mStartQueryCalled;
    private final CountDownLatch mLatch = new CountDownLatch(1);
    private final TestJavaExampleStoreService mTestExampleStoreService =
            new TestJavaExampleStoreService();
    private IExampleStoreService mBinder;

    @Before
    public void doBeforeEachTest() {
        mTestExampleStoreService.onCreate();
        mBinder = IExampleStoreService.Stub.asInterface(mTestExampleStoreService.onBind(null));
    }

    @Test
    public void testStartQuerySuccess() throws Exception {
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_TASK_ID, EXPECTED_TASK_NAME);
        mBinder.startQuery(bundle, new TestJavaExampleStoreServiceCallback());
        mLatch.await();
        assertTrue(mStartQueryCalled);
        assertThat(mCallbackResult).isInstanceOf(IteratorAdapter.class);
    }

    @Test
    public void testStartQueryFailure() throws Exception {
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_TASK_ID, "wrong_taskName");
        mBinder.startQuery(bundle, new TestJavaExampleStoreServiceCallback());
        mLatch.await();
        assertTrue(mStartQueryCalled);
        assertThat(mCallbackErrorCode).isEqualTo(STATUS_INTERNAL_ERROR);
        assertThat(mCallbackErrorCode).isEqualTo(STATUS_INTERNAL_ERROR);
    }

    class TestJavaExampleStoreService extends ExampleStoreService {
        @Override
        public void startQuery(@Nonnull Bundle params, @Nonnull QueryCallback callback) {
            mStartQueryCalled = true;
            String taskName = params.getString(EXTRA_TASK_ID);
            if (!taskName.equals(EXPECTED_TASK_NAME)) {
                callback.onStartQueryFailure(STATUS_INTERNAL_ERROR);
                return;
            }
            callback.onStartQuerySuccess(
                    new ListJavaExampleStoreIterator(ImmutableList.of(EXAMPLE_PROTO_1)));
        }

        @Override
        protected boolean checkCallerPermission() {
            return true;
        }
    }

    /**
     * A simple {@link ExampleStoreIterator} that returns the contents of the {@link List} it's
     * constructed with.
     */
    private static class ListJavaExampleStoreIterator implements ExampleStoreIterator {
        private final Iterator<Example> mExampleIterator;

        ListJavaExampleStoreIterator(List<Example> examples) {
            mExampleIterator = examples.iterator();
        }

        @Override
        public synchronized void next(IteratorCallback callback) {
            callback.onIteratorNextSuccess(null);
        }

        @Override
        public void close() {}
    }

    class TestJavaExampleStoreServiceCallback extends IExampleStoreCallback.Stub {
        @Override
        public void onStartQuerySuccess(IExampleStoreIterator iterator) {
            mCallbackResult = iterator;
            mLatch.countDown();
        }

        @Override
        public void onStartQueryFailure(int errorCode) {
            mCallbackErrorCode = errorCode;
            mLatch.countDown();
        }
    }
}
