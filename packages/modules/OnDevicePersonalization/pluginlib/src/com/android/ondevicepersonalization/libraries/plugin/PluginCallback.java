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

package com.android.ondevicepersonalization.libraries.plugin;

import android.os.Bundle;
import android.os.RemoteException;

/** Callback interface for plugin methods. */
public interface PluginCallback {
    /**
     * Indicates operation was successful and contains an output bundle if the operation had any
     * output. Throw a RemoteException if the callback fails to run.
     */
    void onSuccess(Bundle output) throws RemoteException;

    /**
     * Indicates operation was unsuccessful and contains a failureType indicating what the error is.
     * Throw a RemoteException if the callback fails to run.
     */
    void onFailure(FailureType failureType) throws RemoteException;
}
