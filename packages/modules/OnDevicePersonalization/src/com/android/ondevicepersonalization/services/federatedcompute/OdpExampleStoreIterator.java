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

import android.adservices.ondevicepersonalization.TrainingExampleRecord;
import android.federatedcompute.ExampleStoreIterator;
import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.ListIterator;

/** Implementation of ExampleStoreIterator for OnDevicePersonalization */
public class OdpExampleStoreIterator implements ExampleStoreIterator {

    ListIterator<TrainingExampleRecord> mExampleIterator;

    OdpExampleStoreIterator(List<TrainingExampleRecord> exampleRecordList) {
        mExampleIterator = exampleRecordList.listIterator();
    }

    @Override
    public void next(@NonNull IteratorCallback callback) {
        if (mExampleIterator.hasNext()) {
            TrainingExampleRecord record = mExampleIterator.next();
            byte[] example = record.getTrainingExample();
            byte[] resumptionToken = record.getResumptionToken();
            Bundle result = new Bundle();
            result.putByteArray(EXTRA_EXAMPLE_ITERATOR_RESULT, example);
            result.putByteArray(EXTRA_EXAMPLE_ITERATOR_RESUMPTION_TOKEN, resumptionToken);
            callback.onIteratorNextSuccess(result);
            return;
        }
        callback.onIteratorNextSuccess(null);
    }

    @Override
    public void close() {
        // No resources to close.
    }
}
