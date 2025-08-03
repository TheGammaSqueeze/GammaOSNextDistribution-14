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
package com.android.server.uwb.discovery;

import androidx.annotation.WorkerThread;

import com.android.server.uwb.discovery.info.FiraConnectorCapabilities;

/** Abstract class for Transport Server Provider */
@WorkerThread
public abstract class TransportServerProvider extends TransportProvider {
    private static final String TAG = TransportServerProvider.class.getSimpleName();

    /** Callback for listening to transport server events. */
    @WorkerThread
    public interface TransportServerCallback extends TransportCallback {
        /**
         * Called when the server receive new capabilites from the remote device.
         *
         * @param capabilities new capabilities.
         */
        void onCapabilitesUpdated(FiraConnectorCapabilities capabilities);
    }

    protected TransportServerProvider(int secid) {
        super(secid);
    }
}
