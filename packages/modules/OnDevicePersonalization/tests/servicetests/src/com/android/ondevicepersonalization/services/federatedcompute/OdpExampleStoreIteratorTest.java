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

package com.android.ondevicepersonalization.services.federatedcompute;

import static android.federatedcompute.common.ClientConstants.EXTRA_EXAMPLE_ITERATOR_RESULT;
import static android.federatedcompute.common.ClientConstants.EXTRA_EXAMPLE_ITERATOR_RESUMPTION_TOKEN;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.TrainingExampleRecord;
import android.federatedcompute.ExampleStoreIterator;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@RunWith(JUnit4.class)
public class OdpExampleStoreIteratorTest {
    private final CountDownLatch mLatch = new CountDownLatch(1);

    private boolean mIteratorCallbackOnSuccessCalled = false;
    private boolean mIteratorCallbackOnFailureCalled = false;

    @Test
    public void testNext() {
        TrainingExampleRecord record =
                new TrainingExampleRecord.Builder()
                        .setTrainingExample(new byte[] {1})
                        .setResumptionToken(new byte[] {2})
                        .build();
        List<TrainingExampleRecord> exampleList = new ArrayList<>();
        exampleList.add(record);
        OdpExampleStoreIterator it = new OdpExampleStoreIterator(exampleList);
        it.next(new TestIteratorCallback(new byte[] {1}, new byte[] {2}));
        assertTrue(mIteratorCallbackOnSuccessCalled);
        assertFalse(mIteratorCallbackOnFailureCalled);
        mIteratorCallbackOnSuccessCalled = false;
        it.next(new TestIteratorCallback(null, null));
        assertTrue(mIteratorCallbackOnSuccessCalled);
        assertFalse(mIteratorCallbackOnFailureCalled);
    }
    
    public class TestIteratorCallback implements ExampleStoreIterator.IteratorCallback {

        byte[] mExpectedExample;
        byte[] mExpectedResumptionToken;

        TestIteratorCallback(byte[] expectedExample, byte[] expectedResumptionToken) {
            mExpectedExample = expectedExample;
            mExpectedResumptionToken = expectedResumptionToken;
        }

        @Override
        public boolean onIteratorNextSuccess(Bundle result) {
            if (mExpectedExample == null) {
                assertNull(result);
            } else {
                assertArrayEquals(
                        mExpectedExample, result.getByteArray(EXTRA_EXAMPLE_ITERATOR_RESULT));
                assertArrayEquals(
                        mExpectedResumptionToken,
                        result.getByteArray(EXTRA_EXAMPLE_ITERATOR_RESUMPTION_TOKEN));
            }
            mIteratorCallbackOnSuccessCalled = true;
            mLatch.countDown();
            return true;
        }

        @Override
        public void onIteratorNextFailure(int errorCode) {
            mIteratorCallbackOnFailureCalled = true;
            mLatch.countDown();
        }
    }
}
