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

import android.federatedcompute.common.TrainingOptions;
import android.federatedcompute.common.ExampleConsumption;
import android.federatedcompute.aidl.IFederatedComputeCallback;

/**
  * The interface used to communicate with app's implementation of
  * ResultHandlingService.
  *
  * @hide
  */
interface IResultHandlingService {

  /** The app will implement this method to handle results. */
  oneway void handleResult(in Bundle params, in IFederatedComputeCallback callback);
}