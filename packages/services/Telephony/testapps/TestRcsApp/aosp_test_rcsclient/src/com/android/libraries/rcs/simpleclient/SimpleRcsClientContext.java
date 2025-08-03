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

package com.android.libraries.rcs.simpleclient;

import com.android.libraries.rcs.simpleclient.protocol.sip.SipSession;
import com.android.libraries.rcs.simpleclient.provisioning.ProvisioningController;
import com.android.libraries.rcs.simpleclient.registration.RegistrationController;
import com.android.libraries.rcs.simpleclient.service.ImsService;

/**
 * State container for a {@link SimpleRcsClient} instance.
 */
public class SimpleRcsClientContext {

    private final ProvisioningController provisioningController;

    private final RegistrationController registrationController;

    private final ImsService imsService;

    private final SipSession sipSession;

    public SimpleRcsClientContext(
            ProvisioningController provisioningController,
            RegistrationController registrationController,
            ImsService imsService,
            SipSession sipSession) {
        this.provisioningController = provisioningController;
        this.registrationController = registrationController;
        this.imsService = imsService;
        this.sipSession = sipSession;
    }

    public ProvisioningController getProvisioningController() {
        return provisioningController;
    }

    public RegistrationController getRegistrationController() {
        return registrationController;
    }

    public ImsService getImsService() {
        return imsService;
    }

    public SipSession getSipSession() {
        return sipSession;
    }
}
