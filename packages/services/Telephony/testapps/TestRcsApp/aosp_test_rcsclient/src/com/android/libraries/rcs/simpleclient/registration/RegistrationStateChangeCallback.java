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

package com.android.libraries.rcs.simpleclient.registration;

import com.android.libraries.rcs.simpleclient.protocol.sip.SipSession;
import com.android.libraries.rcs.simpleclient.service.ImsService;

/**
 * Callback for Registration state changes.
 */
public interface RegistrationStateChangeCallback {

    /**
     * The given feature tags are registered with the backend and the service would be able to
     * send and receive messages.
     *
     * @param imsService the newly registered service.
     */
    void notifyRegStateChanged(ImsService imsService);

    /**callback for successful session creation */
    void onSuccess(SipSession sipSession);

    /**callback for failed session creation. */
    void onFailure(String reason);
}
