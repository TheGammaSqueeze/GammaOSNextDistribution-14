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

package com.android.services.telephony.rcs;

import static com.android.internal.telephony.TelephonyStatsLog.SIP_TRANSPORT_SESSION__SIP_MESSAGE_DIRECTION__INCOMING;
import static com.android.internal.telephony.TelephonyStatsLog.SIP_TRANSPORT_SESSION__SIP_MESSAGE_DIRECTION__OUTGOING;

import android.telephony.ims.DelegateRegistrationState;
import android.telephony.ims.FeatureTagState;
import android.telephony.ims.SipDelegateConfiguration;
import android.telephony.ims.SipDelegateImsConfiguration;
import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.SipMessage;
import android.util.LocalLog;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.SipMessageParsingUtils;
import com.android.internal.telephony.metrics.RcsStats;
import com.android.internal.util.IndentingPrintWriter;
import com.android.services.telephony.rcs.validator.IncomingTransportStateValidator;
import com.android.services.telephony.rcs.validator.MalformedSipMessageValidator;
import com.android.services.telephony.rcs.validator.OutgoingTransportStateValidator;
import com.android.services.telephony.rcs.validator.RestrictedOutgoingSipRequestValidator;
import com.android.services.telephony.rcs.validator.RestrictedOutgoingSubscribeValidator;
import com.android.services.telephony.rcs.validator.SipMessageValidator;
import com.android.services.telephony.rcs.validator.ValidationResult;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Track incoming and outgoing SIP messages passing through this delegate and verify these messages
 * by doing the following:
 *  <ul>
 *    <li>Track the SipDelegate's registration state to ensure that a registration event has
 *    occurred before allowing outgoing messages. Once it has occurred, filter outgoing SIP messages
 *    based on the open/restricted feature tag registration state.</li>
 *    <li>Track the SipDelegate's IMS configuration version and deny any outgoing SipMessages
 *    associated with a stale IMS configuration version.</li>
 *    <li>Track the SipDelegate open/close state to allow/deny outgoing messages based on the
 *    session's state.</li>
 *    <li>Validate outgoing SIP messages for both restricted request methods as well as restricted/
 *    malformed headers.</li>
 * </ul>
 */
public class TransportSipMessageValidator {

    private static final String LOG_TAG = "SipMessageV";

    /**
     * the time in milliseconds that we will wait for SIP sessions to close before we will timeout
     * and force the sessions to be cleaned up.
     */
    private static final int PENDING_CLOSE_TIMEOUT_MS = 1000;
    /**
     * time in milliseconds that we will wait for SIP sessions to be closed before we timeout and
     * force the sessions associated with the deregistering feature tags to be cleaned up.
     */
    private static final int PENDING_REGISTRATION_CHANGE_TIMEOUT_MS = 1000;

    /**
     * Timeouts used in this class that are visible for testing.
     */
    @VisibleForTesting
    public interface Timeouts {
        /**
         * @return the time in milliseconds that we will wait for SIP sessions to close before we
         * will timeout and force the sessions to be cleaned up.
         */
        int getPendingCloseTimeoutMs();

        /**
         * @return the time in milliseconds that we will wait for SIP sessions to be closed before
         * we timeout and force the sessions associated with the deregistering feature tags to be
         * cleaned up.
         */
        int getPendingRegistrationChangeTimeoutMs();
    }

    /**
     * Tracks a pending task that has been scheduled on the associated Executor.
     */
    private abstract static class PendingTask implements Runnable {

        private ScheduledFuture<?> mFuture;

        public void scheduleDelayed(ScheduledExecutorService executor, int timeMs) {
            mFuture = executor.schedule(this, timeMs, TimeUnit.MILLISECONDS);
        }

        public boolean isDone() {
            return mFuture != null && mFuture.isDone();
        }

        public void cancel() {
            if (mFuture == null) return;
            mFuture.cancel(false /*interrupt*/);
        }
    }

    /**
     * Tracks a pending reg cleanup task that has been scheduled on the associated Executor.
     */
    private abstract static class PendingRegCleanupTask extends PendingTask {
        public final Set<String> pendingCallIds;
        public final Set<String> featureTags;

        PendingRegCleanupTask(Set<String> tags, Set<String> callIds) {
            featureTags = tags;
            pendingCallIds = callIds;
        }
    }

    private final int mSubId;
    private final ScheduledExecutorService mExecutor;
    private final LocalLog mLocalLog = new LocalLog(SipTransportController.LOG_SIZE);
    private final SipSessionTracker mSipSessionTracker;
    // Validators
    private final IncomingTransportStateValidator mIncomingTransportStateValidator;
    private final OutgoingTransportStateValidator mOutgoingTransportStateValidator;
    private final SipMessageValidator mOutgoingMessageValidator;
    private final SipMessageValidator mIncomingMessageValidator;

    private Set<String> mSupportedFeatureTags;
    private Set<FeatureTagState> mDeniedFeatureTags;
    private long mConfigVersion = -1;
    private Consumer<Set<String>> mClosingCompleteConsumer;
    private PendingTask mPendingClose;
    private PendingRegCleanupTask mPendingRegCleanup;
    private Consumer<Set<String>> mRegistrationAppliedConsumer;
    private final RcsStats mRcsStats;

    public TransportSipMessageValidator(int subId, ScheduledExecutorService executor) {
        mSubId = subId;
        mExecutor = executor;
        mRcsStats = RcsStats.getInstance();
        mSipSessionTracker = new SipSessionTracker(subId, mRcsStats);
        mOutgoingTransportStateValidator = new OutgoingTransportStateValidator(mSipSessionTracker);
        mIncomingTransportStateValidator = new IncomingTransportStateValidator();
        mOutgoingMessageValidator = new MalformedSipMessageValidator().andThen(
                new RestrictedOutgoingSipRequestValidator()).andThen(
                new RestrictedOutgoingSubscribeValidator()).andThen(
                        mOutgoingTransportStateValidator);
        mIncomingMessageValidator = mIncomingTransportStateValidator;
    }

    @VisibleForTesting
    public TransportSipMessageValidator(int subId, ScheduledExecutorService executor,
            SipSessionTracker sipSessionTracker,
            OutgoingTransportStateValidator outgoingStateValidator,
            IncomingTransportStateValidator incomingStateValidator, RcsStats rcsStats) {
        mSubId = subId;
        mExecutor = executor;
        mSipSessionTracker = sipSessionTracker;
        mOutgoingTransportStateValidator = outgoingStateValidator;
        mIncomingTransportStateValidator = incomingStateValidator;
        mOutgoingMessageValidator = mOutgoingTransportStateValidator;
        mIncomingMessageValidator = mIncomingTransportStateValidator;
        mRcsStats = rcsStats;
    }

    /**
     * Notify this tracker that a registration state change has occurred.
     * <p>
     * In some scenarios, this will require that existing SIP dialogs are closed (for example, when
     * moving a feature tag from REGISTERED->DEREGISTERING). This method allows the caller to
     * provide a Consumer that will be called when either there are no SIP dialogs active on
     * DEREGISTERING feature tags, or a timeout has occurred. In the case that a timeout has
     * occurred, this Consumer will accept a list of callIds that will be manually closed by the
     * framework to unblock the IMS stack.
     * <p>
     * @param stateChangeComplete A one time Consumer that when completed, will contain a List of
     *         callIds corresponding to SIP Dialogs that have not been closed yet. It is the callers
     *         responsibility to close the dialogs associated with the provided callIds. If another
     *         state update occurs before the previous was completed, the previous consumer will be
     *         completed with an empty list and the new Consumer will be executed when the new state
     *         changes.
     * @param regState The new registration state.
     */
    public void onRegistrationStateChanged(Consumer<Set<String>> stateChangeComplete,
            DelegateRegistrationState regState) {
        if (mRegistrationAppliedConsumer != null) {
            logw("onRegistrationStateChanged: pending registration change, completing now.");
            // complete the pending consumer with no dialogs pending, this will be re-evaluated
            // and new configuration will be applied.
            cleanupAndNotifyRegistrationAppliedConsumer(Collections.emptySet());
        }
        Set<String> restrictedTags = Stream.concat(
                regState.getDeregisteringFeatureTags().stream(),
                regState.getDeregisteredFeatureTags().stream()).map(FeatureTagState::getFeatureTag)
                .collect(Collectors.toSet());
        mOutgoingTransportStateValidator.restrictFeatureTags(restrictedTags);
        mRegistrationAppliedConsumer = stateChangeComplete;
        if (mPendingClose == null || mPendingClose.isDone()) {
            // Only update the pending registration cleanup task if we do not already have a pending
            // close in progress.
            updatePendingRegCleanupTask(restrictedTags);
        } else {
            logi("skipping update reg cleanup due to pending close task.");
        }
    }

    /**
     * Notify this tracker that the IMS configuration has changed.
     *
     * Parameters contained in the IMS configuration will be used to validate outgoing messages,
     * such as the configuration version.
     * @param c The newest IMS configuration.
     */
    public void onImsConfigurationChanged(SipDelegateImsConfiguration c) {
        if (c.getVersion() == mConfigVersion) {
            return;
        }
        logi("onImsConfigurationChanged: " + mConfigVersion + "->" + c.getVersion());
        mConfigVersion = c.getVersion();
    }

    /**
     * Notify this tracker that the IMS configuration has changed.
     *
     * Parameters contained in the IMS configuration will be used to validate outgoing messages,
     * such as the configuration version.
     * @param c The newest IMS configuration.
     */
    public void onConfigurationChanged(SipDelegateConfiguration c) {
        if (c.getVersion() == mConfigVersion) {
            return;
        }
        logi("onConfigurationChanged: " + mConfigVersion + "->" + c.getVersion());
        mConfigVersion = c.getVersion();
    }

    /**
     * A new message transport has been opened to a SipDelegate.
     * <p>
     * Initializes this tracker and resets any state required to process messages.
     * @param supportedFeatureTags feature tags that are supported and should pass message
     *                             verification.
     * @param deniedFeatureTags feature tags that were denied and should fail message verification.
     */
    public void onTransportOpened(Set<String> supportedFeatureTags,
            Set<FeatureTagState> deniedFeatureTags) {
        logi("onTransportOpened: moving to open state");
        mSupportedFeatureTags = supportedFeatureTags;
        mDeniedFeatureTags = deniedFeatureTags;
        mOutgoingTransportStateValidator.open(supportedFeatureTags, deniedFeatureTags.stream().map(
                FeatureTagState::getFeatureTag).collect(Collectors.toSet()));
        mIncomingTransportStateValidator.open();
    }

    /**
     * A SIP session has been cleaned up and should no longer be tracked.
     * @param callId The call ID associated with the SIP session.
     */
    public void onSipSessionCleanup(String callId) {
        mSipSessionTracker.cleanupSession(callId);
        onCallIdsChanged();
    }

    /**
     * Move this tracker into a restricted state, where only outgoing SIP messages associated with
     * an ongoing SIP Session may be sent. Any out-of-dialog outgoing SIP messages will be denied.
     * This does not affect incoming SIP messages (for example, an incoming SIP INVITE).
     * <p>
     * This tracker will stay in this state until either all open SIP Sessions are closed by the
     * remote application, or a timeout occurs. Once this happens, the provided Consumer will accept
     * a List of call IDs associated with the open SIP Sessions that did not close before the
     * timeout. The caller must then close all open SIP Sessions before closing the transport.
     * @param closingCompleteConsumer A Consumer that will be called when the transport can be
     *         closed and may contain a list of callIds associated with SIP sessions that have not
     *         been closed.
     * @param closingReason The reason that will be provided if an outgoing out-of-dialog SIP
     *         message is sent while the transport is closing.
     * @param closedReason The reason that will be provided if any outgoing SIP message is sent
     *         once the transport is closed.
     */
    public void closeSessionsGracefully(Consumer<Set<String>> closingCompleteConsumer,
            int closingReason, int closedReason) {
        if (closingCompleteConsumer == null) {
            logw("closeSessionsGracefully: unexpected - called with null consumer... closing now");
            closeSessions(closedReason);
            return;
        }
        if (mClosingCompleteConsumer != null) {
            // In this case, all we can do is combine the consumers and wait for the other pending
            // close to complete, finishing both.
            logw("closeSessionsGracefully: unexpected - existing close pending, combining"
                    + " consumers.");
            mClosingCompleteConsumer = callIds -> {
                mClosingCompleteConsumer.accept(callIds);
                closingCompleteConsumer.accept(callIds);
            };
            return;
        } else {
            mClosingCompleteConsumer = closingCompleteConsumer;
        }
        if (getTrackedSipSessionCallIds().isEmpty()) {
            logi("closeSessionsGracefully: moving to closed state now, reason=" + closedReason);
            closeSessionsInternal(closedReason);
            cancelClosingTimeoutAndSendComplete(Collections.emptySet());
            return;
        }
        cancelPendingRegCleanupTask();
        logi("closeSessionsGracefully: moving to restricted state, reason=" + closingReason);
        mOutgoingTransportStateValidator.restrict(closingReason);
        mPendingClose = new PendingTask() {
            @Override
            public void run() {
                closeSessions(closingReason);
            }
        };
        mPendingClose.scheduleDelayed(mExecutor, PENDING_CLOSE_TIMEOUT_MS);
    }

    /**
     * Close the transport now. If there are any open SIP sessions and this is closed due to a
     * configuration change (SIM subscription change, user disabled RCS, the service is dead,
     * etc...) then we will return the call IDs of all open sessions and ask them to be closed.
     * @param closedReason The error reason for why the message transport was closed that will be
     *         sent back to the caller if a new SIP message is sent.
     * @return A List of call IDs associated with sessions that were still open at the time that the
     * tracker closed the transport.
     */
    public Set<String> closeSessions(int closedReason) {
        Set<String> openCallIds = getTrackedSipSessionCallIds();
        logi("closeSessions: moving to closed state, reason=" + closedReason + ", open call ids: "
                + openCallIds);
        closeSessionsInternal(closedReason);
        boolean consumerHandledPendingSessions = cancelClosingTimeoutAndSendComplete(openCallIds);
        if (consumerHandledPendingSessions) {
            logw("closeSessions: call ID closure handled through consumer");
            // sent the open call IDs through the pending complete mechanism to unblock any previous
            // graceful close command and close them early.
            return Collections.emptySet();
        }
        return openCallIds;
    }

    /**
     * Verify a new outgoing SIP message before sending to the SipDelegate (ImsService).
     * @param message The SIP message being verified
     * @return The result of verifying the outgoing message.
     */

    public ValidationResult verifyOutgoingMessage(SipMessage message, long configVersion) {
        if (mConfigVersion != configVersion) {
            return new ValidationResult(
                    SipDelegateManager.MESSAGE_FAILURE_REASON_STALE_IMS_CONFIGURATION,
                    "stale IMS configuration: "  + configVersion + ", expected: "
                            + mConfigVersion);
        }
        ValidationResult result = mOutgoingMessageValidator.validate(message);
        logi("verifyOutgoingMessage: " + result + ", message=" + message);
        if (result.isValidated) {
            mSipSessionTracker.filterSipMessage(
                    SIP_TRANSPORT_SESSION__SIP_MESSAGE_DIRECTION__OUTGOING, message);
        }
        updateForMetrics(SIP_TRANSPORT_SESSION__SIP_MESSAGE_DIRECTION__OUTGOING, message, result);
        return result;
    }

    /**
     * Verify a new incoming SIP message before sending it to the
     * DelegateConnectionMessageCallback (remote application).
     * @param message The SipMessage to verify.
     * @return The result of verifying the incoming message.
     */
    public ValidationResult verifyIncomingMessage(SipMessage message) {
        ValidationResult result = mIncomingMessageValidator.validate(message);
        logi("verifyIncomingMessage: " + result + ", message=" + message);
        if (result.isValidated) {
            mSipSessionTracker.filterSipMessage(
                    SIP_TRANSPORT_SESSION__SIP_MESSAGE_DIRECTION__INCOMING, message);
        }
        updateForMetrics(SIP_TRANSPORT_SESSION__SIP_MESSAGE_DIRECTION__INCOMING, message, result);
        return result;
    }

    /**
     * Acknowledge that a pending incoming or outgoing SIP message has been delivered successfully
     * to the remote.
     * @param transactionId The transaction ID associated with the message.
     */
    public void acknowledgePendingMessage(String transactionId) {
        logi("acknowledgePendingMessage: id=" + transactionId);
        mSipSessionTracker.acknowledgePendingMessage(transactionId);
        onCallIdsChanged();
    }

    /**
     * A pending incoming or outgoing SIP message has failed and should not be tracked.
     * @param transactionId The transaction ID associated with the message.
     */
    public void notifyPendingMessageFailed(String transactionId) {
        logi("notifyPendingMessageFailed: id=" + transactionId);
        mSipSessionTracker.pendingMessageFailed(transactionId);
    }

    /** Dump state about this tracker that should be included in the dumpsys */
    public void dump(PrintWriter printWriter) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println("Supported Tags:" + mSupportedFeatureTags);
        pw.println("Denied Tags:" + mDeniedFeatureTags);
        pw.println(mOutgoingTransportStateValidator);
        pw.println(mIncomingTransportStateValidator);
        pw.println("Reg consumer pending: " + (mRegistrationAppliedConsumer != null));
        pw.println("Close consumer pending: " + (mClosingCompleteConsumer != null));
        pw.println();
        mSipSessionTracker.dump(pw);
        pw.println();
        pw.println("Most recent logs:");
        mLocalLog.dump(printWriter);
    }

    /**
     * A event has occurred that can change the list of active call IDs.
     */
    private void onCallIdsChanged() {
        if (getTrackedSipSessionCallIds().isEmpty() && mPendingClose != null
                && !mPendingClose.isDone()) {
            logi("onCallIdsChanged: no open sessions, completing any pending close events.");
            // do not wait for timeout if pending sessions closed.
            mPendingClose.cancel();
            mPendingClose.run();
        }
        if (mPendingRegCleanup != null && !mPendingRegCleanup.isDone()) {
            logi("onCallIdsChanged: updating pending reg cleanup task.");
            // Recalculate the open call IDs based on the same feature tag set in the case that the
            // call ID change has caused a change in pending reg cleanup task.
            updatePendingRegCleanupTask(mPendingRegCleanup.featureTags);
        }
    }

    /**
     * If there are any pending registration clean up tasks, cancel them and clean up consumers.
     */
    private void cancelPendingRegCleanupTask() {
        if (mPendingRegCleanup != null && !mPendingRegCleanup.isDone()) {
            logi("cancelPendingRegCleanupTask: cancelling...");
            mPendingRegCleanup.cancel();
        }
        cleanupAndNotifyRegistrationAppliedConsumer(Collections.emptySet());
    }

    /**
     * Update the pending registration change clean up task based on the new set of restricted
     * feature tags generated from the deregistering/deregistered feature tags.
     *
     * <p>
     * This set of restricted tags will generate a set of call IDs associated to dialogs that
     * are active and associated with the restricted tags. If there is no pending cleanup task, it
     * will create a new one. If there was already a pending reg cleanup task, it will compare them
     * and create a new one and cancel the old one if the new set of call ids is different from the
     * old one.
     */
    private void updatePendingRegCleanupTask(Set<String> restrictedTags) {
        Set<String> pendingCallIds = mSipSessionTracker.getCallIdsAssociatedWithFeatureTag(
                restrictedTags);
        if (pendingCallIds.isEmpty()) {
            if (mPendingRegCleanup != null && !mPendingRegCleanup.isDone()) {
                logi("updatePendingRegCleanupTask: no remaining call ids, finish cleanup task "
                        + "now.");
                mPendingRegCleanup.cancel();
                mPendingRegCleanup.run();
            } else {
                if (mRegistrationAppliedConsumer != null) {
                    logi("updatePendingRegCleanupTask: notify no pending call ids.");
                    cleanupAndNotifyRegistrationAppliedConsumer(Collections.emptySet());
                }
            }
            return;
        }
        if (mPendingRegCleanup != null && !mPendingRegCleanup.isDone()) {
            if (mPendingRegCleanup.pendingCallIds.equals(pendingCallIds)) {
                logi("updatePendingRegCleanupTask: pending reg change has same set of pending call"
                        + " IDs, so keeping pending task");
                return;
            }
            logi("updatePendingRegCleanupTask: cancelling, call ids have changed.");
            mPendingRegCleanup.cancel();
        }
        mPendingRegCleanup = new PendingRegCleanupTask(restrictedTags, pendingCallIds) {
            @Override
            public void run() {
                cleanupAndNotifyRegistrationAppliedConsumer(pendingCallIds);
            }
        };
        logi("updatePendingRegCleanupTask: scheduling for call ids: " + pendingCallIds);
        mPendingRegCleanup.scheduleDelayed(mExecutor, PENDING_REGISTRATION_CHANGE_TIMEOUT_MS);
    }

    /**
     * Notify the pending registration applied consumer of the call ids that need to be cleaned up.
     */
    private void cleanupAndNotifyRegistrationAppliedConsumer(Set<String> pendingCallIds) {
        if (mRegistrationAppliedConsumer != null) {
            mRegistrationAppliedConsumer.accept(pendingCallIds);
            mRegistrationAppliedConsumer = null;
        }
    }

    /**
     * Cancel any pending timeout to close pending sessions and send the provided call IDs to any
     * pending closing complete consumer.
     * @return {@code true} if a consumer was notified, {@code false} if there were no consumers.
     */
    private boolean cancelClosingTimeoutAndSendComplete(Set<String> openCallIds) {
        if (mPendingClose != null && !mPendingClose.isDone()) {
            logi("completing pending close consumer");
            mPendingClose.cancel();
        }
        // Complete the pending consumer with no open sessions.
        if (mClosingCompleteConsumer != null) {
            mClosingCompleteConsumer.accept(openCallIds);
            mClosingCompleteConsumer = null;
            return true;
        }
        return false;
    }

    /**
     * Close and clear all stateful trackers and validators.
     */
    private void closeSessionsInternal(int closedReason) {
        cancelPendingRegCleanupTask();
        mOutgoingTransportStateValidator.close(closedReason);
        mIncomingTransportStateValidator.close(closedReason);
        mSipSessionTracker.clearAllSessions();
    }

    private Set<String> getTrackedSipSessionCallIds() {
        return mSipSessionTracker.getTrackedDialogs().stream().map(SipDialog::getCallId)
                .collect(Collectors.toSet());
    }

    private void updateForMetrics(int direction, SipMessage m, ValidationResult result) {
        String[] startLineSegments = SipMessageParsingUtils
                .splitStartLineAndVerify(m.getStartLine());
        if (SipMessageParsingUtils.isSipRequest(m.getStartLine())) {
            if (result.isValidated) {
                // SipMessage add to list for Metrics stats
                mRcsStats.onSipMessageRequest(m.getCallIdParameter(), startLineSegments[0],
                        direction);
            } else {
                //Message sending fail and there is no response.
                mRcsStats.invalidatedMessageResult(m.getCallIdParameter(), mSubId,
                        startLineSegments[0], direction, result.restrictedReason);
            }
        } else if (SipMessageParsingUtils.isSipResponse(m.getStartLine())) {
            int statusCode = Integer.parseInt(startLineSegments[1]);
            mRcsStats.onSipMessageResponse(mSubId, m.getCallIdParameter(), statusCode,
                    result.restrictedReason);
        } else {
            logw("Message is Restricted");
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
        Log.i(SipTransportController.LOG_TAG, LOG_TAG + "[" + mSubId + "] " + log);
        mLocalLog.log("[I] " + log);
    }

    private void logw(String log) {
        Log.w(SipTransportController.LOG_TAG, LOG_TAG + "[" + mSubId + "] " + log);
        mLocalLog.log("[W] " + log);
    }
}
