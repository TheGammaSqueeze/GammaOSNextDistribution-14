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
import android.telephony.ims.SipDelegateConnection;
import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.aidl.IImsRegistration;
import android.telephony.ims.aidl.ISipDelegate;
import android.telephony.ims.aidl.ISipDelegateConnectionStateCallback;
import android.telephony.ims.aidl.ISipDelegateMessageCallback;
import android.telephony.ims.aidl.ISipTransport;
import android.telephony.ims.stub.DelegateConnectionStateCallback;
import android.telephony.ims.stub.SipDelegate;
import android.util.ArraySet;
import android.util.LocalLog;
import android.util.Log;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.metrics.RcsStats;
import com.android.internal.util.IndentingPrintWriter;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

/**
 * Created when an IMS application wishes to open up a {@link SipDelegateConnection} and manages the
 * resulting {@link SipDelegate} that may be created on the ImsService side.
 */
public class SipDelegateController {
    static final String LOG_TAG = "SipDelegateC";

    private class BinderConnectionFactory implements DelegateBinderStateManager.Factory {

        private final ISipTransport mSipTransportImpl;
        private final IImsRegistration mImsRegistrationImpl;

        BinderConnectionFactory(ISipTransport transport, IImsRegistration registration) {
            mSipTransportImpl = transport;
            mImsRegistrationImpl = registration;
        }

        @Override
        public DelegateBinderStateManager create(int subId,
                DelegateRequest requestedConfig, Set<FeatureTagState> transportDeniedTags,
                Executor executor, List<DelegateBinderStateManager.StateCallback> stateCallbacks) {
            // We should not actually create a SipDelegate in this case.
            if (requestedConfig.getFeatureTags().isEmpty()) {
                return new SipDelegateBinderConnectionStub(transportDeniedTags, executor,
                        stateCallbacks);
            }
            return new SipDelegateBinderConnection(mSubId, mSipTransportImpl, mImsRegistrationImpl,
                    requestedConfig, transportDeniedTags, mExecutorService, stateCallbacks);
        }
    }

    private final int mSubId;
    private final int mUid;
    private final String mPackageName;
    private final DelegateRequest mInitialRequest;
    private final ScheduledExecutorService mExecutorService;
    private final MessageTransportWrapper mMessageTransportWrapper;
    private final DelegateStateTracker mDelegateStateTracker;
    private final DelegateBinderStateManager.Factory mBinderConnectionFactory;
    private final LocalLog mLocalLog = new LocalLog(SipTransportController.LOG_SIZE);

    private DelegateBinderStateManager mBinderConnection;
    private Set<String> mTrackedFeatureTags;

    public SipDelegateController(int subId, int uid, DelegateRequest initialRequest,
            String packageName, ISipTransport transportImpl, IImsRegistration registrationImpl,
            ScheduledExecutorService executorService,
            ISipDelegateConnectionStateCallback stateCallback,
            ISipDelegateMessageCallback messageCallback) {
        mSubId = subId;
        mUid = uid;
        mPackageName = packageName;
        mInitialRequest = initialRequest;
        mExecutorService = executorService;
        mBinderConnectionFactory = new BinderConnectionFactory(transportImpl, registrationImpl);

        mMessageTransportWrapper = new MessageTransportWrapper(mSubId, executorService,
                messageCallback);

        mDelegateStateTracker = new DelegateStateTracker(mSubId, mUid, stateCallback,
                mMessageTransportWrapper.getDelegateConnection(), RcsStats.getInstance());
    }

    /**
     * Inject dependencies for testing only.
     */
    @VisibleForTesting
    public SipDelegateController(int subId, int uid, DelegateRequest initialRequest,
            String packageName, ScheduledExecutorService executorService,
            MessageTransportWrapper messageTransportWrapper,
            DelegateStateTracker delegateStateTracker,
            DelegateBinderStateManager.Factory connectionFactory) {
        mSubId = subId;
        mUid = uid;
        mInitialRequest = initialRequest;
        mPackageName = packageName;
        mExecutorService = executorService;
        mMessageTransportWrapper = messageTransportWrapper;
        mDelegateStateTracker = delegateStateTracker;
        mBinderConnectionFactory = connectionFactory;
    }

    /**
     * @return The InitialRequest from the IMS application. The feature tags that are actually set
     * up may differ from this request based on the state of this controller.
     */
    public DelegateRequest getInitialRequest() {
        return mInitialRequest;
    }

    /**
     * @return The package name of the IMS application associated with this SipDelegateController.
     */
    public String getPackageName() {
        return mPackageName;
    }

    /**
     * @return The ImsService's SIP delegate binder impl associated with this controller.
     */
    public ISipDelegate getSipDelegateInterface() {
        return mMessageTransportWrapper.getDelegateConnection();
    }

    /**
     * @return The IMS app's message callback binder.
     */
    public ISipDelegateMessageCallback getAppMessageCallback() {
        return mMessageTransportWrapper.getAppMessageCallback();
    }

    /**
     * Create the underlying SipDelegate.
     * <p>
     * This may not happen instantly, The CompletableFuture returned will not complete until
     * {@link DelegateConnectionStateCallback#onCreated(SipDelegateConnection)} is called by the
     * SipDelegate or DelegateStateTracker state is updated in the case that all requested features
     * were denied.
     * @return A CompletableFuture that will complete once the SipDelegate has been created. If true
     * is returned, the SipDelegate has been created successfully. If false, the ImsService is not
     * reachable and the process should be aborted.
     */
    public CompletableFuture<Boolean> create(Set<String> supportedSet,
            Set<FeatureTagState> deniedSet) {
        logi("create, supported: " + supportedSet + ", denied: " + deniedSet);
        mTrackedFeatureTags = supportedSet;
        DelegateBinderStateManager connection =
                createBinderConnection(supportedSet, deniedSet);
        CompletableFuture<Pair<ISipDelegate, Set<FeatureTagState>>> pendingCreate =
                createSipDelegate(connection);
        // May need to implement special case handling where SipDelegate denies all in supportedSet,
        // however that should be a very rare case. For now, if that happens, just keep the
        // SipDelegate bound.
        // use thenApply here because we need this to happen on the same thread that it was called
        // on in order to ensure ordering of onCreated being called, followed by registration
        // state changed. If not, this is subject to race conditions where registered is queued
        // before the async processing of this future.
        return pendingCreate.thenApply((resultPair) -> {
            if (resultPair == null) {
                logw("create: resultPair returned null");
                return false;
            }
            mBinderConnection = connection;
            logi("create: created, delegate denied: " + resultPair.second);
            Set<String> allowedTags = new ArraySet<>(supportedSet);
            // Start with the supported set and remove all tags that were denied.
            allowedTags.removeAll(resultPair.second.stream().map(FeatureTagState::getFeatureTag)
                    .collect(Collectors.toSet()));
            mMessageTransportWrapper.openTransport(resultPair.first, allowedTags,
                    resultPair.second);
            mDelegateStateTracker.sipDelegateConnected(allowedTags, resultPair.second);
            return true;
        });
    }

    /**
     * Modify the SipTransport to reflect the new Feature Tag set that the IMS application has
     * access to.
     * <p>
     * This involves the following operations if the new supported tag set does not match the
     * the existing set:
     * 1) destroy the existing underlying SipDelegate. If there are SIP Dialogs that are active
     * on the SipDelegate that is pending to be destroyed, we must move the feature tags into a
     * deregistering state via
     * {@link DelegateRegistrationState#DEREGISTERING_REASON_FEATURE_TAGS_CHANGING} to signal to the
     * IMS application to close all dialogs before the operation can proceed. If any outgoing
     * out-of-dialog messages are sent at this time, they will also fail with reason
     * {@link SipDelegateManager#MESSAGE_FAILURE_REASON_INTERNAL_DELEGATE_STATE_TRANSITION}.
     * 2) create a new underlying SipDelegate and notify trackers, allowing the transport to
     * re-open.
     * @param newSupportedSet The new supported set of feature tags that the SipDelegate should
     *     be opened for.
     * @param deniedSet The new set of tags that have been denied as well as the reason for the
     *        denial to be reported back to the IMS Application.
     * @return A CompletableFuture containing the pending operation that will change the supported
     * feature tags. Any operations to change the supported feature tags of the associated
     * SipDelegate after this should not happen until this pending operation completes. Will
     * complete with {@code true} if the operation was successful or {@code false} if the
     * IMS service was unreachable.
     */
    public CompletableFuture<Boolean> changeSupportedFeatureTags(Set<String> newSupportedSet,
            Set<FeatureTagState> deniedSet) {
        logi("Received feature tag set change, old: [" + mTrackedFeatureTags + "], new: "
                + newSupportedSet + ",denied: [" + deniedSet + "]");
        if (mTrackedFeatureTags != null && mTrackedFeatureTags.equals(newSupportedSet)) {
            logi("changeSupportedFeatureTags: no change, returning");
            return CompletableFuture.completedFuture(true);
        }

        mTrackedFeatureTags = newSupportedSet;
        // Next perform the destroy operation.
        CompletableFuture<Integer> pendingDestroy = destroySipDelegate(false/*force*/,
                SipDelegateManager.MESSAGE_FAILURE_REASON_INTERNAL_DELEGATE_STATE_TRANSITION,
                SipDelegateManager.MESSAGE_FAILURE_REASON_INTERNAL_DELEGATE_STATE_TRANSITION,
                DelegateRegistrationState.DEREGISTERING_REASON_FEATURE_TAGS_CHANGING,
                SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);

        // Next perform the create operation with the new set of supported feature tags.
        return pendingDestroy.thenComposeAsync((reasonFromService) -> {
            logi("changeSupportedFeatureTags: destroy stage complete, reason reported: "
                    + reasonFromService);
            return create(newSupportedSet, deniedSet);
        }, mExecutorService);
    }

    /**
     * Destroy this SipDelegate. This controller should be disposed of after this method is
     * called.
     * <p>
     * This may not happen instantly if there are SIP Dialogs that are active on this SipDelegate.
     * In this case, the CompletableFuture will not complete until
     * {@link DelegateConnectionStateCallback#onDestroyed(int)} is called by the SipDelegate.
     * @param force If set true, we will close the transport immediately and call
     * {@link SipDelegate#closeDialog(String)} on any open dialogs. If false, we will wait for the
     *         SIP Dialogs to close or the close timer to timeout before destroying the underlying
     *         SipDelegate.
     * @param destroyReason The reason for why this SipDelegate is being destroyed.
     * @return A CompletableFuture that will complete once the SipDelegate has been destroyed.
     */
    public CompletableFuture<Integer> destroy(boolean force, int destroyReason) {
        logi("destroy, forced " + force + ", destroyReason: " + destroyReason);

        CompletableFuture<Integer> pendingOperationComplete =
                destroySipDelegate(force, SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED,
                        getMessageFailReasonFromDestroyReason(destroyReason),
                        DelegateRegistrationState.DEREGISTERING_REASON_DESTROY_PENDING,
                        destroyReason);
        return pendingOperationComplete.thenApplyAsync((reasonFromDelegate) -> {
            logi("destroy, operation complete, notifying trackers, reason " + reasonFromDelegate);
            mDelegateStateTracker.sipDelegateDestroyed(reasonFromDelegate);
            return reasonFromDelegate;
        }, mExecutorService);
    };

    /**
     * The IMS application is notifying the ImsService that it has received a response to a request
     * that will require that the IMS registration be torn down and brought back up.
     *<p>
     * See {@link SipDelegateManager#triggerFullNetworkRegistration} for more information.
     */
    public void triggerFullNetworkRegistration(int sipCode, String sipReason) {
        logi("triggerFullNetworkRegistration, code=" + sipCode + ", reason=" + sipReason);
        if (mBinderConnection != null) {
            mBinderConnection.triggerFullNetworkRegistration(sipCode, sipReason);
        } else {
            logw("triggerFullNetworkRegistration called when binder connection is null");
        }
    }

    private static int getMessageFailReasonFromDestroyReason(int destroyReason) {
        switch (destroyReason) {
            case SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SERVICE_DEAD:
                return SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_DEAD;
            case SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP:
            case SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_USER_DISABLED_RCS:
                return SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_CLOSED;
            default:
                return SipDelegateManager.MESSAGE_FAILURE_REASON_UNKNOWN;
        }
    }

    /**
     * @param force If set true, we will close the transport immediately and call
     * {@link SipDelegate#closeDialog(String)} on any open dialogs. If false, we will wait for the
     *         SIP Dialogs to close or the close timer to timeout before destroying the underlying
     *         SipDelegate.
     * @param messageDestroyingReason The reason to send back to the IMS application in the case
     *         that a new outgoing SIP message is sent that is out-of-dialog while the message
     *         transport is closing.
     * @param messageDestroyedReason The reason to send back to the IMS application in the case
     *         that a new outgoing SIP message is sent once the underlying transport is closed.
     * @param deregisteringReason The deregistering state reported to the IMS application for all
     *         registered feature tags.
     * @param delegateDestroyedReason The reason to send to the underlying SipDelegate that is being
     *         destroyed.
     * @return A CompletableFuture containing the reason from the SipDelegate for why it was
     * destroyed.
     */
    private CompletableFuture<Integer> destroySipDelegate(boolean force,
            int messageDestroyingReason, int messageDestroyedReason, int deregisteringReason,
            int delegateDestroyedReason) {
        if (mBinderConnection == null) {
            logi("destroySipDelegate, called when binder connection is already null");
            return CompletableFuture.completedFuture(delegateDestroyedReason);
        }
        // First, bring down the message transport.
        CompletableFuture<Boolean> pendingTransportClosed = new CompletableFuture<>();
        if (force) {
            logi("destroySipDelegate, forced");
            mMessageTransportWrapper.close(messageDestroyedReason);
            pendingTransportClosed.complete(true);
        } else {
            mMessageTransportWrapper.closeGracefully(messageDestroyingReason,
                    messageDestroyedReason, pendingTransportClosed::complete);
        }

        // Do not send an intermediate pending state to app if there are no open SIP dialogs to
        // worry about.
        if (!pendingTransportClosed.isDone()) {
            mDelegateStateTracker.sipDelegateChanging(deregisteringReason);
        } else {
            logi("destroySipDelegate, skip DEREGISTERING_REASON_DESTROY_PENDING");
        }

        // Next, destroy the SipDelegate.
        return pendingTransportClosed.thenComposeAsync((wasGraceful) -> {
            logi("destroySipDelegate, transport gracefully closed = " + wasGraceful);
            CompletableFuture<Integer> pendingDestroy = new CompletableFuture<>();
            mBinderConnection.destroy(delegateDestroyedReason, pendingDestroy::complete);
            return pendingDestroy;
        }, mExecutorService);
    }

    /**
     * @return a CompletableFuture that returns a Pair containing SipDelegate Binder interface as
     * well as rejected feature tags or a {@code null} Pair instance if the ImsService is not
     * available.
     */
    private CompletableFuture<Pair<ISipDelegate, Set<FeatureTagState>>> createSipDelegate(
            DelegateBinderStateManager connection) {
        CompletableFuture<Pair<ISipDelegate, Set<FeatureTagState>>> createdFuture =
                new CompletableFuture<>();
        boolean isStarted = connection.create(mMessageTransportWrapper.getMessageCallback(),
                (delegate, delegateDeniedTags) ->
                        createdFuture.complete(new Pair<>(delegate, delegateDeniedTags)));
        if (!isStarted) {
            logw("Couldn't create binder connection, ImsService is not available.");
            connection.destroy(SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_SERVICE_DEAD, null);
            return CompletableFuture.completedFuture(null);
        }
        return createdFuture;
    }

    private DelegateBinderStateManager createBinderConnection(Set<String> supportedSet,
            Set<FeatureTagState> deniedSet) {

        List<DelegateBinderStateManager.StateCallback> stateCallbacks = new ArrayList<>(2);
        stateCallbacks.add(mDelegateStateTracker);
        stateCallbacks.add(mMessageTransportWrapper);

        return mBinderConnectionFactory.create(mSubId,
                new DelegateRequest(supportedSet), deniedSet, mExecutorService, stateCallbacks);
    }

    /**
     * This is a listener to handle SipDialog state of delegate
     * @param listener {@link SipDialogsStateListener}
     * @param isNeedNotify It indicates whether the current dialogs state should be notified.
     */
    public void setSipDialogsListener(SipDialogsStateListener listener,
            boolean isNeedNotify) {
        mMessageTransportWrapper.setSipDialogsListener(listener, isNeedNotify);
    }

    /**
     * Write the current state of this controller in String format using the PrintWriter provided
     * for dumpsys.
     */
    public void dump(PrintWriter printWriter) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println("SipDelegateController" + "[" + mSubId + "]:");
        pw.increaseIndent();
        pw.println("Most recent logs:");
        pw.increaseIndent();
        mLocalLog.dump(pw);
        pw.decreaseIndent();

        pw.println();
        pw.println("DelegateStateTracker:");
        pw.increaseIndent();
        mDelegateStateTracker.dump(pw);
        pw.decreaseIndent();

        pw.println();
        pw.println("MessageStateTracker:");
        pw.increaseIndent();
        mMessageTransportWrapper.dump(pw);
        pw.decreaseIndent();

        pw.decreaseIndent();
    }

    private void logi(String log) {
        Log.i(SipTransportController.LOG_TAG, LOG_TAG + "[" + mSubId + "] " + log);
        mLocalLog.log("[I] " + log);
    }

    private void logw(String log) {
        Log.w(SipTransportController.LOG_TAG, LOG_TAG + "[" + mSubId + "] " + log);
        mLocalLog.log("[W] " + log);
    }
}
