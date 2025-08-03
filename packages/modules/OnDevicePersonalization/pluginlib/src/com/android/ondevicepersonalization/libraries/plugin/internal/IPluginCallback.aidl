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

package com.android.ondevicepersonalization.libraries.plugin.internal;

import android.os.Bundle;

import com.android.ondevicepersonalization.libraries.plugin.FailureType;

/**
  * Callback when {@code IPluginExecutorService#runTask} has finished
  * (successfully or unsuccessfully).
  */
oneway interface IPluginCallback {
  /**
   * Indicates operation was successful and contains an output Bundle if the operation had any output.
   */
  void onSuccess(in Bundle output);

  /**
   * Called if runTask fails for any reason.
   */
  void onFailure(in FailureType failureType);
}