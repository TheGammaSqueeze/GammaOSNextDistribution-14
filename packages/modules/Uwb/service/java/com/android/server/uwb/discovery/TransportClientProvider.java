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

/** Abstract class for Transport Client Provider */
@WorkerThread
public abstract class TransportClientProvider extends TransportProvider {
    private static final String TAG = TransportClientProvider.class.getSimpleName();

    /** Callback for listening to transport client events. */
    @WorkerThread
    public interface TransportClientCallback extends TransportCallback {}

    protected TransportClientProvider(int secid) {
        super(secid);
    }

    /**
     * Set and sent new FiRa connector capabilites.
     *
     * @param capabilities new capabilities.
     * @return indicates if successfully set.
     */
    public abstract boolean setCapabilites(FiraConnectorCapabilities capabilities);
}
