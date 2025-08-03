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

/** Listener for events on ClientModeImpl. */
public interface ClientModeImplListener {
    /**
     * Called when a ClientModeImpl has been L2 connected.
     * @param clientModeManager the ClientModeManager associated with the ClientModeImpl
     */
    default void onL2Connected(@NonNull ConcreteClientModeManager clientModeManager) {}

    /**
     * Called when a ClientModeImpl has been L3 connected.
     * @param clientModeManager the ClientModeManager associated with the ClientModeImpl
     */
    default void onL3Connected(@NonNull ConcreteClientModeManager clientModeManager) {}

    /**
     * Called when a ClientModeImpl's internet connection has been validated.
     * @param clientModeManager the ClientModeManager associated with the ClientModeImpl
     */
    default void onInternetValidated(@NonNull ConcreteClientModeManager clientModeManager) {}

    /**
     * Called when a ClientModeImpl's internet connection validation failed.
     * @param clientModeManager the ClientModeManager associated with the ClientModeImpl
     * @param currentConnectionDetectedCaptivePortal whether current connection has detected
     *                                               captive portal.
     */
    default void onInternetValidationFailed(@NonNull ConcreteClientModeManager clientModeManager,
            boolean currentConnectionDetectedCaptivePortal) {}

    /**
     * Called when a ClientModeImpl starts a new connection attempt.
     * @param clientModeManager the ClientModeManager associated with the ClientModeImpl
     */
    default void onConnectionStart(@NonNull ConcreteClientModeManager clientModeManager) {}

    /**
     * Called when a ClientModeImpl ends a connection (could be result of disconnect from an active
     * connection or a connection attempt failure),
     *
     * @param clientModeManager the ClientModeManager associated with the ClientModeImpl
     */
    default void onConnectionEnd(@NonNull ConcreteClientModeManager clientModeManager) {}

    /**
     * Called when a Captive Portal is detected on this connection.
     *
     * @param clientModeManager the ClientModeManager associated with the ClientModeImpl
     */
    default void onCaptivePortalDetected(@NonNull ConcreteClientModeManager clientModeManager) {}
}
