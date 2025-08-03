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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.TrainingExampleRecord;
import android.content.Context;
import android.federatedcompute.ExampleStoreIterator;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;

import com.android.ondevicepersonalization.services.data.events.EventsDao;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public class OdpExampleStoreIteratorFactoryTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    private boolean mIteratorCallbackOnSuccessCalled = false;
    private boolean mIteratorCallbackOnFailureCalled = false;

    @Before
    public void setup() {
        EventsDao.getInstanceForTest(mContext);
    }

    @Test
    public void testNext() {
        TrainingExampleRecord record =
                new TrainingExampleRecord.Builder()
                        .setTrainingExample(new byte[] {1})
                        .setResumptionToken(new byte[] {2})
                        .build();
        List<TrainingExampleRecord> exampleList = new ArrayList<>();
        exampleList.add(record);
        OdpExampleStoreIterator it =
                OdpExampleStoreIteratorFactory.getInstance().createIterator(exampleList);
        it.next(new TestIteratorCallback());
        assertTrue(mIteratorCallbackOnSuccessCalled);
        assertFalse(mIteratorCallbackOnFailureCalled);
    }

    public class TestIteratorCallback implements ExampleStoreIterator.IteratorCallback {

        @Override
        public boolean onIteratorNextSuccess(Bundle result) {
            mIteratorCallbackOnSuccessCalled = true;
            return true;
        }

        @Override
        public void onIteratorNextFailure(int errorCode) {
            mIteratorCallbackOnFailureCalled = true;
        }
    }
}
