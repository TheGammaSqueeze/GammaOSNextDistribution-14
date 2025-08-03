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
import android.os.RemoteException;

import com.android.ondevicepersonalization.libraries.plugin.FailureType;
import com.android.ondevicepersonalization.libraries.plugin.PluginCallback;
import com.android.ondevicepersonalization.libraries.plugin.PluginState;
import com.android.ondevicepersonalization.libraries.plugin.PluginStateCallback;

/**
 * Util to convert between public callback types like {@link PluginCallback} & {@link
 * PluginStateCallback}, and Parcelable callback types like {@link IPluginCallback} & {@link
 * IPluginStateCallback}.
 */
public final class CallbackConverter {
    /** Converts {@link PluginCallback} to {@link IPluginCallback} */
    public static IPluginCallback toIPluginCallback(PluginCallback callback) {
        return new IPluginCallback.Stub() {
            @Override
            public void onSuccess(Bundle input) throws RemoteException {
                callback.onSuccess(input);
            }

            @Override
            public void onFailure(FailureType failureType) throws RemoteException {
                callback.onFailure(failureType);
            }
        };
    }

    /** Converts {@link IPluginCallback} to {@link PluginCallback} */
    public static PluginCallback toPublicCallback(IPluginCallback callback) {
        return new PluginCallback() {
            @Override
            public void onSuccess(Bundle input) throws RemoteException {
                callback.onSuccess(input);
            }

            @Override
            public void onFailure(FailureType failureType) throws RemoteException {
                callback.onFailure(failureType);
            }
        };
    }

    /** Converts {@link PluginStateCallback} to {@link IPluginStateCallback} */
    public static IPluginStateCallback toIPluginStateCallback(PluginStateCallback callback) {
        return new IPluginStateCallback.Stub() {
            @Override
            public void onState(PluginState state) {
                callback.onState(state);
            }
        };
    }

    private CallbackConverter() {}
}
