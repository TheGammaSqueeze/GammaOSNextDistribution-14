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
import android.os.RemoteException;
import android.telephony.ims.DelegateMessageCallback;
import android.telephony.ims.DelegateRegistrationState;
import android.telephony.ims.FeatureTagState;
import android.telephony.ims.SipDelegateConfiguration;
import android.telephony.ims.SipDelegateImsConfiguration;
import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.SipMessage;
import android.telephony.ims.aidl.ISipDelegate;
import android.telephony.ims.aidl.ISipDelegateMessageCallback;
import android.telephony.ims.stub.SipDelegate;
import android.util.LocalLog;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.SipMessageParsingUtils;
import com.android.internal.util.IndentingPrintWriter;
import com.android.phone.RcsProvisioningMonitor;
import com.android.services.telephony.rcs.validator.ValidationResult;

import java.io.PrintWriter;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

/**
 * Wraps the SIP message path both from the IMS application to the SipDelegate and from the
 * SipDelegate back to the IMS Application.
 * <p>
 * Queues incoming and outgoing SIP messages on an Executor and deliver to IMS application and
 * SipDelegate in order. If there is an error delivering the message, the caller is notified.
 * Uses {@link TransportSipMessageValidator} to track ongoing SIP dialogs and verify outgoing
 * messages.
 * <p>
 * Note: This handles incoming binder calls, so all calls from other processes should be handled on
 * the provided Executor.
 */
public class MessageTransportWrapper implements DelegateBinderStateManager.StateCallback {
    private static final String TAG = "MessageTW";

    // SipDelegateConnection(IMS Application) -> SipDelegate(ImsService)
    private final ISipDelegate.Stub mSipDelegateConnection = new ISipDelegate.Stub() {
        /**
         * The IMS application is acknowledging that it has successfully received and processed an
         * incoming SIP message sent by the SipDelegate in
         * {@link ISipDelegateMessageCallback#onMessageReceived(SipMessage)}.
         */
        @Override
        public void notifyMessageReceived(String viaTransactionId) {
            long token = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> {
                    if (mSipDelegate == null) {
                        logw("notifyMessageReceived called when SipDelegate is not associated for "
                                + "transaction id: " + viaTransactionId);
                        return;
                    }
                    try {
                        mSipSessionTracker.acknowledgePendingMessage(viaTransactionId);
                        mSipDelegate.notifyMessageReceived(viaTransactionId);
                    } catch (RemoteException e) {
                        logw("SipDelegate not available when notifyMessageReceived was called "
                                + "for transaction id: " + viaTransactionId);
                    }
                });
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        /**
         * The IMS application is acknowledging that it received an incoming SIP message sent by the
         * SipDelegate in {@link ISipDelegateMessageCallback#onMessageReceived(SipMessage)} but it
         * was unable to process it.
         */
        @Override
        public void notifyMessageReceiveError(String viaTransactionId, int reason) {
            long token = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> {
                    if (mSipDelegate == null) {
                        logw("notifyMessageReceiveError called when SipDelegate is not associated "
                                + "for transaction id: " + viaTransactionId);
                        return;
                    }
                    try {
                        mSipSessionTracker.notifyPendingMessageFailed(viaTransactionId);
                        mSipDelegate.notifyMessageReceiveError(viaTransactionId, reason);
                    } catch (RemoteException e) {
                        logw("SipDelegate not available when notifyMessageReceiveError was called "
                                + "for transaction id: " + viaTransactionId);
                    }
                });
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        /**
         * The IMS application is sending an outgoing SIP message to the SipDelegate to be processed
         * and sent over the network.
         */
        @Override
        public void sendMessage(SipMessage sipMessage, long configVersion) {
            long token = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> {
                    ValidationResult result =
                            mSipSessionTracker.verifyOutgoingMessage(sipMessage, configVersion);
                    result = maybeOverrideValidationForTesting(result);
                    if (!result.isValidated) {
                        notifyDelegateSendError("Outgoing - " + result.logReason,
                                sipMessage, result.restrictedReason);
                        return;
                    }
                    try {
                        if (mSipDelegate == null) {
                            logw("sendMessage called when SipDelegate is not associated."
                                    + sipMessage);
                            notifyDelegateSendError("No SipDelegate", sipMessage,
                                    SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_DEAD);

                            return;
                        }
                        mSipDelegate.sendMessage(sipMessage, configVersion);
                    } catch (RemoteException e) {
                        notifyDelegateSendError("RemoteException: " + e, sipMessage,
                                SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_DEAD);
                    }
                });
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        /**
         * The SipDelegateConnection is requesting that the resources associated with an ongoing SIP
         * dialog be released as the SIP dialog is now closed.
         */
        @Override
        public void cleanupSession(String callId) {
            long token = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> cleanupSessionInternal(callId));
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
    };

    // SipDelegate(ImsService) -> SipDelegateConnection(IMS Application)
    private final ISipDelegateMessageCallback.Stub mDelegateConnectionMessageCallback =
            new ISipDelegateMessageCallback.Stub() {
        /**
         * An Incoming SIP Message has been received by the SipDelegate and is being routed
         * to the IMS application for processing.
         * <p>
         * IMS application will call {@link ISipDelegate#notifyMessageReceived(String)} to
         * acknowledge receipt of this incoming message.
         */
        @Override
        public void onMessageReceived(SipMessage message) {
            long token = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> {
                    ValidationResult result = mSipSessionTracker.verifyIncomingMessage(message);
                    if (!result.isValidated) {
                        notifyAppReceiveError("Incoming - " + result.logReason, message,
                                result.restrictedReason);
                        return;
                    }
                    try {
                        mAppCallback.onMessageReceived(message);
                    } catch (RemoteException e) {
                        notifyAppReceiveError("RemoteException: " + e, message,
                                SipDelegateManager.MESSAGE_FAILURE_REASON_DELEGATE_DEAD);
                    }
                });
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        /**
         * An outgoing SIP message sent previously by the SipDelegateConnection to the SipDelegate
         * using {@link ISipDelegate#sendMessage(SipMessage, long)} as been successfully sent.
         */
        @Override
        public void onMessageSent(String viaTransactionId) {
            long token = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> {
                    if (mSipDelegate == null) {
                        logw("Unexpected state, onMessageSent called when SipDelegate is not "
                                + "associated");
                    }
                    try {
                        mSipSessionTracker.acknowledgePendingMessage(viaTransactionId);
                        mAppCallback.onMessageSent(viaTransactionId);
                    } catch (RemoteException e) {
                        logw("Error sending onMessageSent to SipDelegateConnection, remote not"
                                + "available for transaction ID: " + viaTransactionId);
                    }
                });
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        /**
         * An outgoing SIP message sent previously by the SipDelegateConnection to the SipDelegate
         * using {@link ISipDelegate#sendMessage(SipMessage, long)} failed to be sent.
         */
        @Override
        public void onMessageSendFailure(String viaTransactionId, int reason) {
            long token = Binder.clearCallingIdentity();
            try {
                mExecutor.execute(() -> {
                    if (mSipDelegate == null) {
                        logw("Unexpected state, onMessageSendFailure called when SipDelegate is not"
                                + "associated");
                    }
                    try {
                        mSipSessionTracker.notifyPendingMessageFailed(viaTransactionId);
                        mAppCallback.onMessageSendFailure(viaTransactionId, reason);
                    } catch (RemoteException e) {
                        logw("Error sending onMessageSendFailure to SipDelegateConnection, remote"
                                + " not available for transaction ID: " + viaTransactionId);
                    }
                });
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
    };

    /**
     * Interface for injecting validator override dependencies for testing.
     */
    @VisibleForTesting
    public interface ValidatorOverride {
        /**
         * @return {@code null} if the validation result should not be overridden, {@code true} if
         * the validation result should always pass, {@code false} if the validation result should
         * always fail.
         */
        Boolean getValidatorOverrideState();
    }

    private final ValidatorOverride mValidatorOverride;
    private final ISipDelegateMessageCallback mAppCallback;
    private final Executor mExecutor;
    private final int mSubId;
    private final TransportSipMessageValidator mSipSessionTracker;
    private final LocalLog mLocalLog = new LocalLog(SipTransportController.LOG_SIZE);

    private ISipDelegate mSipDelegate;

    public MessageTransportWrapper(int subId, ScheduledExecutorService executor,
            ISipDelegateMessageCallback appMessageCallback) {
        mSubId = subId;
        mAppCallback = appMessageCallback;
        mExecutor = executor;
        mSipSessionTracker = new TransportSipMessageValidator(subId, executor);
        mValidatorOverride = () -> RcsProvisioningMonitor.getInstance()
                .getImsFeatureValidationOverride(mSubId);
    }

    /**
     * Mock out dependencies for unit testing.
     */
    @VisibleForTesting
    public MessageTransportWrapper(int subId, ScheduledExecutorService executor,
            ISipDelegateMessageCallback appMessageCallback,
            TransportSipMessageValidator sipSessionTracker) {
        mSubId = subId;
        mAppCallback = appMessageCallback;
        mExecutor = executor;
        mSipSessionTracker = sipSessionTracker;
        // Remove links to static methods calls querying overrides for testing.
        mValidatorOverride = () -> null;
    }

    @Override
    public void onRegistrationStateChanged(DelegateRegistrationState registrationState) {
        mSipSessionTracker.onRegistrationStateChanged((callIds) -> {
            for (String id : callIds)  {
                cleanupSessionInternal(id);
            }
        }, registrationState);
    }

    @Override
    public void onImsConfigurationChanged(SipDelegateImsConfiguration config) {
        mSipSessionTracker.onImsConfigurationChanged(config);
    }

    @Override
    public void onConfigurationChanged(SipDelegateConfiguration config) {
        mSipSessionTracker.onConfigurationChanged(config);
    }

    /**
     * Open the transport and allow SIP messages to be sent/received on the delegate specified.
     * @param delegate The delegate connection to send SIP messages to on the ImsService.
     * @param supportedFeatureTags Feature tags that are supported. Outgoing SIP messages relating
     *                             to these tags will be allowed.
     * @param deniedFeatureTags Feature tags that have been denied. Outgoing SIP messages relating
     *         to these tags will be denied.
     */
    public void openTransport(ISipDelegate delegate, Set<String> supportedFeatureTags,
            Set<FeatureTagState> deniedFeatureTags) {
        logi("openTransport: delegate=" + delegate + ", supportedTags=" + supportedFeatureTags
                + ", deniedTags=" + deniedFeatureTags);
        mSipSessionTracker.onTransportOpened(supportedFeatureTags, deniedFeatureTags);
        mSipDelegate = delegate;
    }

    /** Dump state about this tracker that should be included in the dumpsys */
    public void dump(PrintWriter printWriter) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println("Most recent logs:");
        mLocalLog.dump(printWriter);
        pw.println();
        pw.println("Dialog Tracker:");
        pw.increaseIndent();
        mSipSessionTracker.dump(pw);
        pw.decreaseIndent();
    }

    /**
     * @return SipDelegate implementation to be sent to IMS application.
     */
    public ISipDelegate getDelegateConnection() {
        return mSipDelegateConnection;
    }

    /**
     * @return The remote application's message callback.
     */
    public ISipDelegateMessageCallback getAppMessageCallback() {
        return mAppCallback;
    }

    /**
     * @return MessageCallback implementation to be sent to the ImsService.
     */
    public ISipDelegateMessageCallback getMessageCallback() {
        return mDelegateConnectionMessageCallback;
    }

    /**
     * Gradually close all SIP Sessions by:
     * 1) denying all new outgoing SIP Dialog requests with the reason specified and
     * 2) only allowing existing SIP Sessions to continue.
     * <p>
     * This will allow traffic to continue on existing SIP Sessions until a BYE is sent and the
     * corresponding SIP Dialogs are closed or a timeout is hit and
     * {@link SipDelegate#cleanupSession(String)} (String)} is forcefully called on all open SIP
     * sessions.
     * <p>
     * Any outgoing out-of-dialog traffic on this transport will be denied with the provided reason.
     * <p>
     * Incoming out-of-dialog traffic will continue to be set up until the SipDelegate is fully
     * closed.
     * @param delegateClosingReason The reason code to return to
     * {@link DelegateMessageCallback#onMessageSendFailure(String, int)} if a new out-of-dialog SIP
     *         message is received while waiting for existing Dialogs.
     * @param closedReason reason to return to new outgoing SIP messages via
     *         {@link SipDelegate#notifyMessageReceiveError(String, int)} once the transport
     *         transitions to the fully closed state.
     * @param resultConsumer The consumer called when the message transport has been closed. It will
     *         return {@code true} if the procedure completed successfully or {@link false} if the
     *         transport needed to be closed forcefully due to the application not responding before
     *         a timeout occurred.
     */
    public void closeGracefully(int delegateClosingReason, int closedReason,
            Consumer<Boolean> resultConsumer) {
        logi("closeGracefully: closingReason=" + delegateClosingReason + ", closedReason="
                + closedReason + ", resultConsumer(" + resultConsumer.hashCode() + ")");
        mSipSessionTracker.closeSessionsGracefully((openCallIds) -> {
            logi("closeGracefully resultConsumer(" + resultConsumer.hashCode()
                    + "): open call IDs:{" + openCallIds + "}");
            closeTransport(openCallIds);
            // propagate event to the consumer
            resultConsumer.accept(openCallIds.isEmpty() /*successfullyClosed*/);
        }, delegateClosingReason, closedReason);
    }

    /**
     * Close all ongoing SIP sessions immediately and respond to any incoming/outgoing messages with
     * the provided reason.
     * @param closedReason The failure reason to provide to incoming/outgoing SIP messages
     *         if an attempt is made to send/receive a message after this method is called.
     */
    public void close(int closedReason) {
        Set<String> openSessions = mSipSessionTracker.closeSessions(closedReason);
        logi("close: closedReason=" + closedReason + "open call IDs:{" + openSessions + "}");
        closeTransport(openSessions);
    }

    // Clean up all state related to the existing SipDelegate immediately.
    private void closeTransport(Set<String> openCallIds) {
        for (String id : openCallIds) {
            cleanupSessionInternal(id);
        }
        mSipDelegate = null;
    }

    private void cleanupSessionInternal(String callId) {
        logi("cleanupSessionInternal: clean up session with callId: " + callId);
        try {
            if (mSipDelegate == null) {
                logw("cleanupSessionInternal: SipDelegate is not associated, callId: " + callId);
            } else {
                // This will close the transport, so call cleanup on ImsService first.
                mSipDelegate.cleanupSession(callId);
            }
        } catch (RemoteException e) {
            logw("cleanupSessionInternal: remote not available when cleanupSession was called "
                    + "for call id: " + callId);
        }
        mSipSessionTracker.onSipSessionCleanup(callId);
    }

    private ValidationResult maybeOverrideValidationForTesting(ValidationResult result) {
        Boolean isValidatedOverride = mValidatorOverride.getValidatorOverrideState();
        if (isValidatedOverride == null) {
            return result;
        }
        if (isValidatedOverride) {
            return ValidationResult.SUCCESS;
        } else if (result.isValidated) {
            // if override is set to false and the original result was validated, return a new
            // restricted result with UNKNOWN reason.
            return new ValidationResult(SipDelegateManager.MESSAGE_FAILURE_REASON_UNKNOWN,
                    "validation failed due to a testing override being set");
        }
        return result;
    }

    private void notifyDelegateSendError(String logReason, SipMessage message, int reasonCode) {
        String transactionId = SipMessageParsingUtils.getTransactionId(message.getHeaderSection());
        logi("Error sending SipMessage[id: " + transactionId + ", code: " + reasonCode
                + "] -> SipDelegate for reason: " + logReason);
        try {
            mAppCallback.onMessageSendFailure(transactionId, reasonCode);
        } catch (RemoteException e) {
            logw("notifyDelegateSendError, SipDelegate is not available: " + e);
        }
    }

    private void notifyAppReceiveError(String logReason, SipMessage message, int reasonCode) {
        String transactionId = SipMessageParsingUtils.getTransactionId(message.getHeaderSection());
        logi("Error sending SipMessage[id: " + transactionId + ", code: " + reasonCode + "] -> "
                + "SipDelegateConnection for reason: " + logReason);
        try {
            mSipDelegate.notifyMessageReceiveError(transactionId, reasonCode);
        } catch (RemoteException e) {
            logw("notifyAppReceiveError, SipDelegate is not available: " + e);
        }
    }

    /**
     * This is a listener to handle SipDialog state of delegate
     * @param listener {@link SipDialogsStateListener}
     * @param isNeedNotify It indicates whether the current dialogs state should be notified.
     */
    public void setSipDialogsListener(SipDialogsStateListener listener,
            boolean isNeedNotify) {
        mSipSessionTracker.setSipDialogsListener(listener, isNeedNotify);
    }

    private void logi(String log) {
        Log.i(SipTransportController.LOG_TAG, TAG + "[" + mSubId + "] " + log);
        mLocalLog.log("[I] " + log);
    }

    private void logw(String log) {
        Log.w(SipTransportController.LOG_TAG, TAG + "[" + mSubId + "] " + log);
        mLocalLog.log("[W] " + log);
    }
}
