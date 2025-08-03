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
import android.telephony.ims.FeatureTagState;
import android.telephony.ims.aidl.ISipDelegate;
import android.telephony.ims.aidl.ISipDelegateMessageCallback;
import android.telephony.ims.stub.SipDelegate;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Stub implementation used when a SipDelegate needs to be set up in specific cases, but there
 * is no underlying implementation in the ImsService.
 *
 * This is used in cases where all of the requested feature tags were denied for various reasons
 * from the SipTransportController. In this case, we will "connect", send a update to include the
 * denied feature tags, and then do nothing until this stub is torn down.
 */
public class SipDelegateBinderConnectionStub implements DelegateBinderStateManager {
    protected final Set<FeatureTagState> mDeniedTags;
    protected final Executor mExecutor;
    protected final List<StateCallback> mStateCallbacks;

    /**
     * Create a new Connection object to manage the creation and destruction of a
     * {@link SipDelegate}.
     * @param transportDeniedTags The feature tags that have already been denied by the
     *                            SipTransportController and should not be requested.
     * @param executor The Executor that all binder calls from the remote process will be executed
     *                on.
     * @param stateCallbacks A list of callbacks that will each be called when the state of the
     *                       SipDelegate changes. This will be called on the supplied executor.
     */
    public SipDelegateBinderConnectionStub(Set<FeatureTagState> transportDeniedTags,
            Executor executor, List<StateCallback> stateCallbacks) {
        mDeniedTags = transportDeniedTags;
        mExecutor = executor;
        mStateCallbacks = stateCallbacks;
    }

    @Override
    public boolean create(ISipDelegateMessageCallback cb,
            BiConsumer<ISipDelegate, Set<FeatureTagState>> createdConsumer) {
        mExecutor.execute(() -> {
            createdConsumer.accept(null, (mDeniedTags));
            for (SipDelegateBinderConnection.StateCallback  c: mStateCallbacks) {
                c.onRegistrationStateChanged(new DelegateRegistrationState.Builder().build());
            }
        });
        return true;
    }

    @Override
    public void destroy(int reason, Consumer<Integer> destroyedConsumer) {
        mExecutor.execute(() -> {
            mStateCallbacks.clear();
            destroyedConsumer.accept(reason);
        });
    }

    @Override
    public void triggerFullNetworkRegistration(int sipCode, String sipReason) {
        // This stub is not connected to an ImsService, so this method is intentionally not
        // implemented.
    }
}
