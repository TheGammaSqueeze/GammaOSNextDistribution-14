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

package android.federatedcompute.aidl;

import android.os.Bundle;
import android.federatedcompute.aidl.IExampleStoreCallback;

/**
* The interface that client apps must implement to provide training examples
* or aggregation reports for federated computation job.
* @hide
*/
interface IExampleStoreService {
    /**
    * Start a new example query for the data in the given collection using
    * the given selection criteria.
    */
    void startQuery(in Bundle params, in IExampleStoreCallback callback);
}