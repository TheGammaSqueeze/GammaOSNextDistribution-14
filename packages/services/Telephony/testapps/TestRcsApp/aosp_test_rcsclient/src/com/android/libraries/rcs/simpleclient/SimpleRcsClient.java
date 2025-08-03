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

import android.os.Build.VERSION_CODES;
import android.telephony.ims.ImsException;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.libraries.rcs.simpleclient.protocol.sip.SipSession;
import com.android.libraries.rcs.simpleclient.provisioning.ProvisioningController;
import com.android.libraries.rcs.simpleclient.registration.RegistrationController;
import com.android.libraries.rcs.simpleclient.registration.RegistrationStateChangeCallback;
import com.android.libraries.rcs.simpleclient.service.ImsService;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Simple RCS client implementation.
 *
 * State is covered by a context instance.
 */
@RequiresApi(api = VERSION_CODES.R)
public class SimpleRcsClient {
    private static final String TAG = SimpleRcsClient.class.getSimpleName();
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);
    private ProvisioningController provisioningController;
    private RegistrationController registrationController;
    private ImsService imsService;
    private SimpleRcsClientContext context;
    private StateChangedCallback stateChangedCallback;

    public static Builder newBuilder() {
        return new Builder();
    }

    public SimpleRcsClientContext getContext() {
        return context;
    }

    public void start() {
        provision();
    }

    public void stop() {
        Log.i(TAG, "stop..");
        registrationController.deregister();
        provisioningController.unRegister();
        provisioningController = null;
        registrationController = null;
        imsService = null;
    }

    public void onStateChanged(StateChangedCallback cb) {
        this.stateChangedCallback = cb;
    }

    private boolean enterState(State expected, State newState) {
        boolean result = state.compareAndSet(expected, newState);

        if (result && stateChangedCallback != null) {
            try {
                stateChangedCallback.notifyStateChange(expected, newState);
            } catch (Exception e) {
                Log.e(TAG, "Exception on calling state change callback", e);
            }
        }
        Log.i(TAG, "expected:" + expected + " new:" + newState + " res:" + result);
        return result;
    }

    private void provision() {
        if (!enterState(State.NEW, State.PROVISIONING)) {
            return;
        }
        provisioningController.onConfigurationChange(configXml -> {
            register();
        });
        try {
            provisioningController.triggerProvisioning();
        } catch (ImsException e) {
            // TODO: ...
        }
    }

    private void register() {
        if (!enterState(State.PROVISIONING, State.REGISTERING)) {
            return;
        }

        registrationController.register(imsService,
                new RegistrationStateChangeCallback() {
                    @Override
                    public void notifyRegStateChanged(ImsService imsService) {

                    }

                    @Override
                    public void onSuccess(SipSession sipSession) {
                        Log.i(TAG, "onSuccess");
                        registered(sipSession);
                    }

                    @Override
                    public void onFailure(String reason) {
                        Log.i(TAG, "onFailure reason:" + reason);
                        notRegistered();
                    }
                });
    }

    private void registered(SipSession session) {
        if (state.get().equals(State.REGISTERING) || state.get().equals(State.NOT_REGISTERED)) {
            enterState(state.get(), State.REGISTERED);
        }

        context = new SimpleRcsClientContext(provisioningController, registrationController,
                imsService,
                session);

        imsService.start(context);
    }

    private void notRegistered() {
        enterState(State.REGISTERED, State.NOT_REGISTERED);
    }

    /**
     * Possible client states.
     */
    public enum State {
        NEW,
        PROVISIONING,
        REGISTERING,
        REGISTERED,
        NOT_REGISTERED,
    }

    /**
     * Builder for creating new SimpleRcsClient instances.
     */
    public static class Builder {

        private ProvisioningController provisioningController;
        private RegistrationController registrationController;
        private ImsService imsService;

        public Builder provisioningController(ProvisioningController controller) {
            this.provisioningController = controller;
            return this;
        }

        public Builder registrationController(RegistrationController controller) {
            this.registrationController = controller;
            return this;
        }

        public Builder imsService(ImsService imsService) {
            this.imsService = imsService;
            return this;
        }

        public SimpleRcsClient build() {
            SimpleRcsClient client = new SimpleRcsClient();
            client.registrationController = registrationController;
            client.provisioningController = provisioningController;
            client.imsService = imsService;

            return client;
        }
    }
}
