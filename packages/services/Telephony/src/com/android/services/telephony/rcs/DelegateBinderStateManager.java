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

package com.android.services.telephony.rcs;

import android.telephony.ims.DelegateRegistrationState;
import android.telephony.ims.DelegateRequest;
import android.telephony.ims.FeatureTagState;
import android.telephony.ims.SipDelegateConfiguration;
import android.telephony.ims.SipDelegateConnection;
import android.telephony.ims.SipDelegateImsConfiguration;
import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.aidl.ISipDelegate;
import android.telephony.ims.aidl.ISipDelegateMessageCallback;

import com.android.internal.annotations.VisibleForTesting;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Defines the interface to be used to manage the state of a SipDelegate on the ImsService side.
 */
public interface DelegateBinderStateManager {

    /**
     * Callback interface that allows listeners to listen to changes in registration or
     * configuration state.
     */
    interface StateCallback {
        /**
         * The SipDelegate has notified telephony that the registration state has changed.
         */
        void onRegistrationStateChanged(DelegateRegistrationState registrationState);

        /**
         * The SipDelegate has notified telephony that the IMS configuration has changed.
         */
        void onImsConfigurationChanged(SipDelegateImsConfiguration config);

        /**
         * The SipDelegate has notified telephony that the IMS configuration has changed.
         */
        void onConfigurationChanged(SipDelegateConfiguration config);
    }

    /** Allow for mocks to be created for testing. */
    @VisibleForTesting
    interface Factory {
        /**
         * Create a new instance of this interface, which may change depending on the tags being
         * denied. See {@link SipDelegateBinderConnectionStub} and
         * {@link SipDelegateBinderConnection}
         */
        DelegateBinderStateManager create(int subId, DelegateRequest requestedConfig,
                Set<FeatureTagState> transportDeniedTags,
                Executor executor, List<StateCallback> stateCallbacks);
    }

    /**
     * Start the process to create a SipDelegate on the ImsService.
     * @param cb The Binder interface that the SipDelegate should use to notify new incoming SIP
     *         messages as well as acknowledge whether or not an outgoing SIP message was
     *         successfully sent.
     * @param createdConsumer The consumer that will be notified when the creation process has
     *         completed. Contains the ISipDelegate interface to communicate with the SipDelegate
     *         and the feature tags the SipDelegate itself denied.
     * @return true if the creation process started, false if the remote process died. If false, the
     * consumers will not be notified.
     */
    boolean create(ISipDelegateMessageCallback cb,
            BiConsumer<ISipDelegate, Set<FeatureTagState>> createdConsumer);

    /**
     * Destroy the existing SipDelegate managed by this object.
     * <p>
     * This instance should be cleaned up after this call.
     * @param reason The reason for why this delegate is being destroyed.
     * @param destroyedConsumer The consumer that will be notified when this operation completes.
     *         Contains the reason the SipDelegate reported it was destroyed.
     */
    void destroy(int reason, Consumer<Integer> destroyedConsumer);

    /**
     * Called by IMS application, see
     * {@link SipDelegateManager#triggerFullNetworkRegistration(SipDelegateConnection, int, String)}
     * for more information about when this is called.
     */
    void triggerFullNetworkRegistration(int sipCode, String sipReason);
}
