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

import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.ims.DelegateRegistrationState;
import android.telephony.ims.DelegateRequest;
import android.telephony.ims.FeatureTagState;
import android.telephony.ims.SipDelegateConfiguration;
import android.telephony.ims.SipDelegateImsConfiguration;
import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.aidl.IImsRegistration;
import android.telephony.ims.aidl.ISipDelegate;
import android.telephony.ims.aidl.ISipDelegateMessageCallback;
import android.telephony.ims.aidl.ISipDelegateStateCallback;
import android.telephony.ims.aidl.ISipTransport;
import android.telephony.ims.stub.SipDelegate;
import android.util.LocalLog;
import android.util.Log;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Container for the active connection to the {@link SipDelegate} active on the ImsService.
 * <p>
 * New instances of this class will be created and destroyed new {@link SipDelegate}s are created
 * and destroyed by the {@link SipDelegateController}.
 */
public class SipDelegateBinderConnection implements DelegateBinderStateManager,
        IBinder.DeathRecipient {
    private static final String LOG_TAG = "BinderConn";

    protected final int mSubId;
    protected final Set<FeatureTagState> mDeniedTags;
    protected final Executor mExecutor;
    protected final List<StateCallback> mStateCallbacks;

    private final LocalLog mLocalLog = new LocalLog(SipTransportController.LOG_SIZE);

    // Callback interface from ImsService to this Connection. State Events will be forwarded to IMS
    // application through DelegateStateTracker.
    private final ISipDelegateStateCallback mSipDelegateStateCallback =
            new ISipDelegateStateCallback.Stub() {
                @Override
                public void onCreated(ISipDelegate delegate,
                        List<FeatureTagState> deniedFeatureTags) {
                    long token = Binder.clearCallingIdentity();
                    try {
                        mExecutor.execute(() ->
                                notifySipDelegateCreated(delegate, deniedFeatureTags));
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }

                @Override
                public void onFeatureTagRegistrationChanged(
                        DelegateRegistrationState registrationState) {
                    long token = Binder.clearCallingIdentity();
                    try {
                        mExecutor.execute(() -> {
                            logi("onFeatureTagRegistrationChanged:" + registrationState);
                            for (StateCallback c : mStateCallbacks) {
                                c.onRegistrationStateChanged(registrationState);
                            }
                        });
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }

                @Override
                public void onImsConfigurationChanged(
                        SipDelegateImsConfiguration registeredSipConfig) {
                    long token = Binder.clearCallingIdentity();
                    try {
                        mExecutor.execute(() -> {
                            logi("onImsConfigurationChanged: version="
                                    + registeredSipConfig.getVersion());
                            for (StateCallback c : mStateCallbacks) {
                                c.onImsConfigurationChanged(registeredSipConfig);
                            }
                        });
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }

                @Override
                public void onConfigurationChanged(
                        SipDelegateConfiguration registeredSipConfig) {
                    long token = Binder.clearCallingIdentity();
                    try {
                        mExecutor.execute(() -> {
                            logi("onConfigurationChanged");
                            for (StateCallback c : mStateCallbacks) {
                                c.onConfigurationChanged(registeredSipConfig);
                            }
                        });
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }

                @Override
                public void onDestroyed(int reason) {
                    long token = Binder.clearCallingIdentity();
                    try {
                        mExecutor.execute(() -> notifySipDelegateDestroyed(reason));
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                }
            };

    private final ISipTransport mSipTransport;
    private final IImsRegistration mImsRegistration;
    private final DelegateRequest mRequestedConfig;

    private ISipDelegate mDelegateBinder;
    private BiConsumer<ISipDelegate, Set<FeatureTagState>> mPendingCreatedConsumer;
    private Consumer<Integer> mPendingDestroyedConsumer;

    /**
     * Create a new Connection object to manage the creation and destruction of a
     * {@link SipDelegate}.
     * @param subId The subid that this SipDelegate is being created for.
     * @param sipTransport The SipTransport implementation that will be used to manage SipDelegates.
     * @param registrationImpl The ImsRegistration implementation that will be used to manage
     *                         registration changes in relation to the SipDelegates.
     * @param requestedConfig The DelegateRequest to be sent to the ImsService.
     * @param transportDeniedTags The feature tags that have already been denied by the
     *                            SipTransportController and should not be requested.
     * @param executor The Executor that all binder calls from the remote process will be executed
     *                on.
     * @param stateCallbacks A list of callbacks that will each be called when the state of the
     *                       SipDelegate changes. This will be called on the supplied executor.
     */
    public SipDelegateBinderConnection(int subId, ISipTransport sipTransport,
            IImsRegistration registrationImpl, DelegateRequest requestedConfig,
            Set<FeatureTagState> transportDeniedTags, Executor executor,
            List<StateCallback> stateCallbacks) {
        mSubId = subId;
        mSipTransport = sipTransport;
        mImsRegistration = registrationImpl;
        mRequestedConfig = requestedConfig;
        mDeniedTags = transportDeniedTags;
        mExecutor = executor;
        mStateCallbacks = stateCallbacks;
    }

    @Override
    public boolean create(ISipDelegateMessageCallback cb,
            BiConsumer<ISipDelegate, Set<FeatureTagState>> createdConsumer) {
        try {
            mSipTransport.createSipDelegate(mSubId, mRequestedConfig, mSipDelegateStateCallback,
                    cb);
            mSipTransport.asBinder().linkToDeath(this, 0);
        } catch (RemoteException e) {
            logw("create called on unreachable SipTransport:" + e);
            return false;
        }
        mPendingCreatedConsumer = createdConsumer;
        return true;
    }

    @Override
    public void destroy(int reason, Consumer<Integer> destroyedConsumer) {
        mPendingDestroyedConsumer = destroyedConsumer;
        try {
            if (mDelegateBinder != null) {
                mSipTransport.destroySipDelegate(mDelegateBinder, reason);
            } else {
                mExecutor.execute(() -> notifySipDelegateDestroyed(reason));
            }
            mStateCallbacks.clear();
        } catch (RemoteException e) {
            logw("destroy called on unreachable SipTransport:" + e);
            mExecutor.execute(() -> notifySipDelegateDestroyed(reason));
        }
        try {
            mSipTransport.asBinder().unlinkToDeath(this, 0);
        } catch (NoSuchElementException e) {
            logw("unlinkToDeath called on already unlinked binder" + e);
        }
    }

    @Override
    public void triggerFullNetworkRegistration(int sipCode, String sipReason) {
        try {
            mImsRegistration.triggerFullNetworkRegistration(sipCode, sipReason);
        } catch (RemoteException e) {
            logw("triggerFullNetworkRegistration called on unreachable ImsRegistration:" + e);
        }
    }

    private void notifySipDelegateCreated(ISipDelegate delegate,
            List<FeatureTagState> deniedFeatureTags) {
        logi("Delegate Created: " + delegate + ", deniedTags:" + deniedFeatureTags);
        if (delegate == null) {
            logw("Invalid null delegate returned!");
        }
        mDelegateBinder = delegate;
        // Add denied feature tags from SipDelegate to the ones denied by the transport
        if (deniedFeatureTags != null) {
            mDeniedTags.addAll(deniedFeatureTags);
        }
        if (mPendingCreatedConsumer == null) return;
        mPendingCreatedConsumer.accept(delegate, mDeniedTags);
        mPendingCreatedConsumer = null;
    }

    private void notifySipDelegateDestroyed(int reason) {
        logi("Delegate Destroyed, reason: " + reason);
        mDelegateBinder = null;
        if (mPendingDestroyedConsumer == null) return;
        mPendingDestroyedConsumer.accept(reason);
        mPendingDestroyedConsumer = null;
    }

    /** Dump state about this binder connection that should be included in the dumpsys. */
    public void dump(PrintWriter printWriter) {
        mLocalLog.dump(printWriter);
    }

    protected final void logi(String log) {
        Log.i(SipTransportController.LOG_TAG, LOG_TAG + "[" + mSubId + "] " + log);
        mLocalLog.log("[I] " + log);
    }

    protected final void logw(String log) {
        Log.w(SipTransportController.LOG_TAG, LOG_TAG + "[" + mSubId + "] " + log);
        mLocalLog.log("[W] " + log);
    }

    @Override
    public void binderDied() {
        mExecutor.execute(() -> {
            logw("binderDied!");
            // Unblock any pending create/destroy operations.
            // SipTransportController will handle the overall destruction/teardown.
            notifySipDelegateCreated(null, Collections.emptyList());
            notifySipDelegateDestroyed(SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SERVICE_DEAD);
        });
    }
}
