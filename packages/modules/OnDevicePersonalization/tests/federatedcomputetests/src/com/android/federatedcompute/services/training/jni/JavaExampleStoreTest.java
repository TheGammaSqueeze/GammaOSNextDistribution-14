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

import static com.android.federatedcompute.services.common.ErrorStatusException.buildStatus;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.android.federatedcompute.services.common.ErrorStatusException;
import com.android.federatedcompute.services.examplestore.ExampleIterator;

import com.google.common.collect.ImmutableList;
import com.google.internal.federated.plan.ExampleSelector;
import com.google.internal.federatedcompute.v1.Code;
import com.google.protobuf.ByteString;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.List;

@RunWith(JUnit4.class)
public final class JavaExampleStoreTest {
    private static final String COLLECTION_URI = "app://test_collection";
    private static final ExampleSelector SELECTOR =
            ExampleSelector.newBuilder().setCollectionUri(COLLECTION_URI).build();

    @Mock private com.android.federatedcompute.services.examplestore.ExampleIterator mIterator;
    private JavaExampleStore mJavaExampleStore;
    @Mock UncaughtExceptionHandler mUncaughtExceptionHandler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testCreateExampleIterator_success() throws Exception {
        ByteString data1 = ByteString.copyFromUtf8("data1");
        ByteString data2 = ByteString.copyFromUtf8("data2");
        List<ByteString> data = ImmutableList.of(data1, data2);
        mJavaExampleStore = new JavaExampleStore(new FakeExampleIterator(data));
        JavaExampleIterator nativeIterator =
                mJavaExampleStore.createExampleIteratorWithContext(
                        SELECTOR.toByteArray(), new byte[] {});

        // Verify all the data returns correctly.
        assertThat(nativeIterator.next()).isEqualTo(data1.toByteArray());
        assertThat(nativeIterator.next()).isEqualTo(data2.toByteArray());

        // Verify all subsequent next() calls return empty result array which indicates reach to the
        // end of iterator.
        assertThat(nativeIterator.next()).hasLength(0);
    }

    @Test
    public void testCreateExampleIterator_interruptedIterator() throws Exception {
        when(mIterator.hasNext()).thenThrow(new InterruptedException("Interrupted"));

        mJavaExampleStore = new JavaExampleStore(mIterator);

        JavaExampleIterator iterator =
                mJavaExampleStore.createExampleIteratorWithContext(
                        SELECTOR.toByteArray(), new byte[] {});

        assertThrows(InterruptedException.class, () -> iterator.next());
    }

    @Test
    public void testCreateExampleIterator_throwingIterator() throws Exception {
        when(mIterator.hasNext())
                .thenThrow(
                        new ErrorStatusException(
                                buildStatus(Code.UNAVAILABLE_VALUE, "can't get next example")));

        mJavaExampleStore = new JavaExampleStore(mIterator);
        JavaExampleIterator javaExampleIterator =
                mJavaExampleStore.createExampleIteratorWithContext(
                        SELECTOR.toByteArray(), new byte[] {});
        assertThrows(ErrorStatusException.class, () -> javaExampleIterator.next());
    }

    private static class FakeExampleIterator implements ExampleIterator {
        private final List<ByteString> mData;
        private int mNext;

        FakeExampleIterator(List<ByteString> data) {
            this.mData = data;
            this.mNext = 0;
        }

        @Override
        public boolean hasNext() {
            return this.mNext < this.mData.size();
        }

        @Override
        public byte[] next() {
            return this.mData.get(this.mNext++).toByteArray();
        }

        @Override
        public void close() {}
    }
}
