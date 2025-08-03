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

package com.android.libraries.rcs.simpleclient.provisioning;

import android.telephony.ims.ImsException;

/**
 * Access to provisioning functionality and data.
 */
public interface ProvisioningController {

    /**
     * Triggers a new provisioning request. If the device is not already provisioned, it requests
     * the
     * provisioning flow and sets up callbacks. If the provisioning is already present, it
     * requests a
     * new provisioning config from the server.
     *
     * @throws ImsException if there is an error.
     */
    void triggerProvisioning() throws ImsException;

    /** Is Single-Reg enabled for the default call SIM ? */
    boolean isRcsVolteSingleRegistrationCapable() throws ImsException;

    void onConfigurationChange(ProvisioningStateChangeCallback cb);

    // Unregister the callback to the framework's provisioning change.
    void unRegister();
}
