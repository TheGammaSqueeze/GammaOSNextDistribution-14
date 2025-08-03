/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.cts.encryptionapp;

import android.content.Context;
import android.util.Log;
import android.service.resumeonreboot.ResumeOnRebootService;

import javax.annotation.Nullable;

/** A implementation for {@link IResumeOnRebootService}
 *
 * This class provides a fake implementation for the server based resume on reboot service.
 * It's used for cts test to verify the functionality of platform code, and it won't talk to
 * the server when wrap/unwrap is called.
 */
public class RebootEscrowFakeService extends ResumeOnRebootService {
  static final String TAG = "RebootEscrowFakeService";

  // Name of the shared preference for service interaction
  static final String SERVICE_PREFS = "SERVICE_PREFERENCES";

  @Nullable
  @Override
  public byte[] onWrap(byte[] blob, long lifeTimeInMillis) {
    // Tests can this flag to verify that unwrap is called.
    Context context =
            getApplication().getApplicationContext().createDeviceProtectedStorageContext();
    context.getSharedPreferences(SERVICE_PREFS, 0).edit()
            .putBoolean("WRAP_CALLED", true).commit();

    return blob;
  }

  @Nullable
  @Override
  public byte[] onUnwrap(byte[] wrappedBlob) {
    // Tests can this flag to verify that unwrap is called.
    Context context =
            getApplication().getApplicationContext().createDeviceProtectedStorageContext();
    context.getSharedPreferences(SERVICE_PREFS, 0).edit()
            .putBoolean("UNWRAP_CALLED", true).commit();

    return wrappedBlob;
  }
}
