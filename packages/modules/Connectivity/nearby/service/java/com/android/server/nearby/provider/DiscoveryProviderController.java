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

package com.android.server.nearby.provider;

import android.annotation.Nullable;
import android.nearby.ScanFilter;
import android.nearby.ScanRequest;

import java.util.List;

/** Interface for controlling discovery providers. */
public interface DiscoveryProviderController {

    /**
     * Sets the listener which can expect to receive all state updates from after this point. May be
     * invoked at any time.
     */
    void setListener(@Nullable AbstractDiscoveryProvider.Listener listener);

    /** Returns true if in the started state. */
    boolean isStarted();

    /**
     * Starts the discovery provider. Must be invoked before any other method (except {@link
     * #setListener(AbstractDiscoveryProvider.Listener)} (Listener)}).
     */
    void start();

    /**
     * Stops the discovery provider. No other methods may be invoked after this method (except
     * {@link #setListener(AbstractDiscoveryProvider.Listener)} (Listener)}), until {@link #start()}
     * is called again.
     */
    void stop();

    /** Sets the desired scan mode. */
    void setProviderScanMode(@ScanRequest.ScanMode int scanMode);

    /** Gets the controller scan mode. */
    @ScanRequest.ScanMode
    int getProviderScanMode();

    /** Sets the scan filters. */
    void setProviderScanFilters(List<ScanFilter> filters);
}
