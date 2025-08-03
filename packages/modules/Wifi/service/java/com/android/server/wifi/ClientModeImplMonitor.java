/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.wifi;

import android.annotation.NonNull;
import android.util.ArraySet;

import java.util.Set;

/**
 * This class is used for other modules to monitor events occurring in {@link ClientModeImpl},
 * without putting that code directly in ClientModeImpl and cluttering it.
 *
 * TODO(b/175896748): Eventually, our goal is to make ClientModeImpl contain only code that is
 *  critical to its main functionality (connection tracking), and move other auxiliary code
 *  elsewhere (such as here).
 */
public class ClientModeImplMonitor implements ClientModeImplListener {

    private final Set<ClientModeImplListener> mListeners = new ArraySet<>();

    /** Register a listener. */
    public void registerListener(@NonNull ClientModeImplListener listener) {
        mListeners.add(listener);
    }

    /** Unregister a listener. */
    public void unregisterListener(@NonNull ClientModeImplListener listener) {
        mListeners.remove(listener);
    }

    @Override
    public void onL2Connected(@NonNull ConcreteClientModeManager clientModeManager) {
        for (ClientModeImplListener listener : mListeners) {
            listener.onL2Connected(clientModeManager);
        }
    }

    // TODO(b/175896748): not yet triggered by ClientModeImpl
    @Override
    public void onL3Connected(@NonNull ConcreteClientModeManager clientModeManager) {
        for (ClientModeImplListener listener : mListeners) {
            listener.onL3Connected(clientModeManager);
        }
    }

    @Override
    public void onInternetValidated(@NonNull ConcreteClientModeManager clientModeManager) {
        for (ClientModeImplListener listener : mListeners) {
            listener.onInternetValidated(clientModeManager);
        }
    }

    @Override
    public void onConnectionStart(@NonNull ConcreteClientModeManager clientModeManager) {
        for (ClientModeImplListener listener : mListeners) {
            listener.onConnectionStart(clientModeManager);
        }
    }

    @Override
    public void onConnectionEnd(@NonNull ConcreteClientModeManager clientModeManager) {
        for (ClientModeImplListener listener : mListeners) {
            listener.onConnectionEnd(clientModeManager);
        }
    }

    @Override
    public void onCaptivePortalDetected(@NonNull ConcreteClientModeManager clientModeManager) {
        for (ClientModeImplListener listener : mListeners) {
            listener.onCaptivePortalDetected(clientModeManager);
        }
    }
}
