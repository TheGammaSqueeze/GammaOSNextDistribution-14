/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.test;

import android.adservices.ondevicepersonalization.DownloadCompletedInput;
import android.adservices.ondevicepersonalization.DownloadCompletedOutput;
import android.adservices.ondevicepersonalization.IsolatedServiceException;
import android.adservices.ondevicepersonalization.IsolatedWorker;
import android.adservices.ondevicepersonalization.KeyValueStore;
import android.os.OutcomeReceiver;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

// TODO(b/249345663) Move this class and related manifest to separate APK for more realistic testing
public class TestPersonalizationHandler implements IsolatedWorker {
    public final String TAG = "TestIsolatedWorker";
    private final KeyValueStore mRemoteData;

    TestPersonalizationHandler(KeyValueStore remoteData) {
        mRemoteData = remoteData;
    }

    @Override
    public void onDownloadCompleted(
            DownloadCompletedInput input,
            OutcomeReceiver<DownloadCompletedOutput, IsolatedServiceException> receiver) {
        try {
            Log.d(TAG, "Starting filterData.");
            Log.d(TAG, "Existing keyExtra: "
                    + Arrays.toString(mRemoteData.get("keyExtra")));
            Log.d(TAG, "Existing keySet: " + mRemoteData.keySet());

            DownloadCompletedOutput result =
                    new DownloadCompletedOutput.Builder()
                            .setRetainedKeys(getFilteredKeys(input.getDownloadedContents()))
                            .build();
            receiver.onResult(result);
        } catch (Exception e) {
            Log.e(TAG, "Error occurred in onDownload", e);
        }
    }

    private List<String> getFilteredKeys(KeyValueStore data) {
        Set<String> filteredKeys = data.keySet();
        filteredKeys.remove("key3");
        return new ArrayList<>(filteredKeys);
    }
}
