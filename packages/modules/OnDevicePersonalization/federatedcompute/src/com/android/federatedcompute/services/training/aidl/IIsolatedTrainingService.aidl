
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

package com.android.federatedcompute.services.training.aidl;

import android.os.Bundle;
import com.android.federatedcompute.services.training.aidl.ITrainingResultCallback;

interface IIsolatedTrainingService {
  /**
   * Runs a single federated training round. Once started, the run may be
   * cancelled with a call to {@link #cancelTraining}.
   */
  void runFlTraining(
      in Bundle params,
      in ITrainingResultCallback resultCallback);

  /**
   * Cancels any in-progress training run started by a previous call to {@link #runFlTraining}. Does
   * nothing if no run is in progress.
   */
  void cancelTraining(long runId);
}
