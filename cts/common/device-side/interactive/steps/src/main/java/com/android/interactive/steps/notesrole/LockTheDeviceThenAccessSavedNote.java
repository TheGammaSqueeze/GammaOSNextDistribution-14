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

package com.android.interactive.steps.notesrole;

import android.app.KeyguardManager;

import com.android.bedstead.nene.TestApis;
import com.android.interactive.steps.ActAndWaitStep;

/** A step to lock the device before attempting to access a saved note on the lock screen. */
public final class LockTheDeviceThenAccessSavedNote extends ActAndWaitStep {

    public LockTheDeviceThenAccessSavedNote() {
        super("Lock the device, the test will then launch the OEM-provided default notes app on "
              + "the lock screen. Then perform an operation to access a saved note on the lock "
              + "screen.",
                      () -> {
                          KeyguardManager keyguardManager =
                                  TestApis.context().instrumentedContext().getSystemService(
                                          KeyguardManager.class);
                          return keyguardManager.isKeyguardLocked();
                      });
    }
}
