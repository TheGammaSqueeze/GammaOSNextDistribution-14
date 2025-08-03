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

package com.android.managedprovisioning.common;

import com.android.managedprovisioning.networkconnection.EstablishNetworkConnectionActivity;

/**
 * An interface which describes methods to keep data in store and retrieve it.
 */
public interface SharedPreferences {

    /**
     * Writes whether the provisioning flow is delegated to the device management role holder.
     */
    void setIsProvisioningFlowDelegatedToRoleHolder(boolean value);

    /**
     * Returns {@code true} if the provisioning flow is delegated to the device management
     * role holder.
     */
    boolean isProvisioningFlowDelegatedToRoleHolder();

    /**
     * Marks that {@link EstablishNetworkConnectionActivity} was run prior to provisioning.
     */
    void setIsEstablishNetworkConnectionRun(boolean value);

    /**
     * Returns {@code true} if {@link EstablishNetworkConnectionActivity} was previously run.
     */
    boolean isEstablishNetworkConnectionRun();
}
