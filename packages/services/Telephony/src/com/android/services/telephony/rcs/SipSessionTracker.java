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

import android.telephony.ims.SipDialogState;
import android.telephony.ims.SipMessage;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.LocalLog;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.SipMessageParsingUtils;
import com.android.internal.telephony.metrics.RcsStats;
import com.android.internal.util.IndentingPrintWriter;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tracks the state of SIP sessions started by a SIP INVITE (see RFC 3261)
 * <p>
 * Each SIP session created will consist of one or more SIP with, each dialog in the session
 * having the same call-ID. Each SIP dialog will be in one of three states: EARLY, CONFIRMED, and
 * CLOSED.
 * <p>
 * The SIP session will be closed once all of the associated dialogs are closed.
 */
public class SipSessionTracker {
    private static final String TAG = "SessionT";

    /**
     * SIP request methods that will start a new SIP Dialog and move it into the PENDING state
     * while we wait for a response. Note: INVITE is not the only SIP dialog that will create a
     * dialog, however it is the only one that we wish to track for this use case.
     */
    public static final String[] SIP_REQUEST_DIALOG_START_METHODS = new String[] { "invite" };

    /**
     * The SIP request method that will close a SIP Dialog in the ACTIVE state with the same
     * Call-Id.
     */
    private static final String SIP_CLOSE_DIALOG_REQUEST_METHOD = "bye";

    private final LocalLog mLocalLog = new LocalLog(SipTransportController.LOG_SIZE);
    private final ArrayList<SipDialog> mTrackedDialogs = new ArrayList<>();
    // Operations that are pending an ack from the remote application processing the message before
    // they can be applied here. Maps the via header branch parameter of the message to the
    // associated pending operation.
    private final ArrayMap<String, Runnable> mPendingAck = new ArrayMap<>();

    private final RcsStats mRcsStats;
    int mSubId;
    private SipDialogsStateListener mSipDialogsListener;
    private String mDelegateKey;

    public SipSessionTracker(int subId, RcsStats rcsStats) {
        mSubId = subId;
        mRcsStats = rcsStats;
        mDelegateKey = String.valueOf(UUID.randomUUID());
    }

    /**
     * Filter a SIP message to determine if it will result in a new SIP dialog. This will need to be
     * successfully acknowledged by the remote IMS stack using
     * {@link #acknowledgePendingMessage(String)} before we do any further processing.
     *
     * @param message The Incoming SIP message.
     */
    public void filterSipMessage(int direction, SipMessage message) {
        final Runnable r;
        if (startsEarlyDialog(message)) {
            r = getCreateDialogRunnable(direction, message);
        } else if (closesDialog(message)) {
            r = getCloseDialogRunnable(message);
        } else if (SipMessageParsingUtils.isSipResponse(message.getStartLine())) {
            r = getDialogStateChangeRunnable(message);
        } else {
            r = null;
        }

        if (r != null) {
            if (mPendingAck.containsKey(message.getViaBranchParameter())) {
                Runnable lastEvent = mPendingAck.get(message.getViaBranchParameter());
                logw("Adding new message when there was already a pending event for branch: "
                        + message.getViaBranchParameter());
                Runnable concatRunnable = () -> {
                    // No choice but to concatenate the Runnables together.
                    if (lastEvent != null) lastEvent.run();
                    r.run();
                };
                mPendingAck.put(message.getViaBranchParameter(), concatRunnable);
            } else {
                mPendingAck.put(message.getViaBranchParameter(), r);
            }
        }
    }

    /**
     * The pending SIP message has been received by the remote IMS stack. We can now track dialogs
     * associated with this message.
     * message.
     * @param viaBranchId The SIP message's Via header's branch parameter, which is used as a
     *                    unique token.
     */
    public void acknowledgePendingMessage(String viaBranchId) {
        Runnable r = mPendingAck.get(viaBranchId);
        if (r != null) {
            mPendingAck.remove(viaBranchId);
            r.run();
        }
    }

    /**
     * The pending SIP message has failed to be sent to the remote so remove the pending task.
     * @param viaBranchId The failed message's Via header's branch parameter.
     */
    public void pendingMessageFailed(String viaBranchId) {
        mPendingAck.remove(viaBranchId);
    }

    /**
     * A SIP session tracked by the remote application's IMS stack has been closed, so we can stop
     * tracking it.
     * @param callId The callId of the SIP session that has been closed.
     */
    public void cleanupSession(String callId) {
        List<SipDialog> dialogsToCleanup = mTrackedDialogs.stream()
                .filter(d -> d.getCallId().equals(callId))
                .collect(Collectors.toList());
        if (dialogsToCleanup.isEmpty()) return;
        logi("Cleanup dialogs associated with call id: " + callId);
        for (SipDialog d : dialogsToCleanup) {
            mRcsStats.onSipTransportSessionClosed(mSubId, callId, 0,
                    d.getState() == d.STATE_CLOSED);
            d.close();
            logi("Dialog closed: " + d);
        }
        mTrackedDialogs.removeAll(dialogsToCleanup);
        notifySipDialogState();
    }

    /**
     * @return the call IDs of the dialogs associated with the provided feature tags.
     */
    public Set<String> getCallIdsAssociatedWithFeatureTag(Set<String> featureTags) {
        if (featureTags.isEmpty()) return Collections.emptySet();
        Set<String> associatedIds = new ArraySet<>();
        for (String featureTag : featureTags) {
            for (SipDialog dialog : mTrackedDialogs) {
                boolean isAssociated = dialog.getAcceptContactFeatureTags().stream().anyMatch(
                        d -> d.equalsIgnoreCase(featureTag));
                if (isAssociated) associatedIds.add(dialog.getCallId());
            }
        }
        return associatedIds;
    }

    /**
     * @return All dialogs that have not received a final response yet 2XX or 3XX+.
     */
    public Set<SipDialog> getEarlyDialogs() {
        return mTrackedDialogs.stream().filter(d -> d.getState() == SipDialog.STATE_EARLY)
                .collect(Collectors.toSet());
    }

    /**
     * @return All confirmed dialogs that have received a 2XX response and are active.
     */
    public Set<SipDialog> getConfirmedDialogs() {
        return mTrackedDialogs.stream().filter(d -> d.getState() == SipDialog.STATE_CONFIRMED)
                .collect(Collectors.toSet());
    }

    /**
     * @return Dialogs that have been closed via a BYE or 3XX+ response and
     * {@link #cleanupSession(String)} has not been called yet.
     */
    @VisibleForTesting
    public Set<SipDialog> getClosedDialogs() {
        return mTrackedDialogs.stream().filter(d -> d.getState() == SipDialog.STATE_CLOSED)
                .collect(Collectors.toSet());
    }

    /**
     * @return All of the tracked dialogs, even the ones that have been closed but
     * {@link #cleanupSession(String)} has not been called.
     */
    public Set<SipDialog> getTrackedDialogs() {
        return new ArraySet<>(mTrackedDialogs);
    }

    /**
     * Clears all tracked sessions.
     */
    public void clearAllSessions() {
        for (SipDialog d : mTrackedDialogs) {
            mRcsStats.onSipTransportSessionClosed(mSubId, d.getCallId(), 0, false);
        }
        mTrackedDialogs.clear();
        mPendingAck.clear();
        notifySipDialogState();
    }

    /**
     * Dump the state of this tracker to the provided PrintWriter.
     */
    public void dump(PrintWriter printWriter) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println("SipSessionTracker:");
        pw.increaseIndent();
        pw.print("Early Call IDs: ");
        pw.println(getEarlyDialogs().stream().map(SipDialog::getCallId)
                .collect(Collectors.toSet()));
        pw.print("Confirmed Call IDs: ");
        pw.println(getConfirmedDialogs().stream().map(SipDialog::getCallId)
                .collect(Collectors.toSet()));
        pw.print("Closed Call IDs: ");
        pw.println(getClosedDialogs().stream().map(SipDialog::getCallId)
                .collect(Collectors.toSet()));
        pw.println("Tracked Dialogs:");
        pw.increaseIndent();
        for (SipDialog d : mTrackedDialogs) {
            pw.println(d);
        }
        pw.decreaseIndent();
        pw.println();
        pw.println("Local Logs");
        mLocalLog.dump(pw);
        pw.decreaseIndent();
    }

    /**
     * @return {@code true}, if the SipMessage passed in should start a new SIP dialog,
     * {@code false} if it should not.
     */
    private boolean startsEarlyDialog(SipMessage m) {
        if (!SipMessageParsingUtils.isSipRequest(m.getStartLine())) {
            return false;
        }
        String[] startLineSegments = SipMessageParsingUtils.splitStartLineAndVerify(
                m.getStartLine());
        if (startLineSegments == null) {
            return false;
        }
        return Arrays.stream(SIP_REQUEST_DIALOG_START_METHODS)
                .anyMatch(r -> r.equalsIgnoreCase(startLineSegments[0]));
    }

    /**
     * @return {@code true}, if the SipMessage passed in should close a confirmed dialog,
     * {@code false} if it should not.
     */
    private boolean closesDialog(SipMessage m) {
        if (!SipMessageParsingUtils.isSipRequest(m.getStartLine())) {
            return false;
        }
        String[] startLineSegments = SipMessageParsingUtils.splitStartLineAndVerify(
                m.getStartLine());
        if (startLineSegments == null) {
            return false;
        }
        return SIP_CLOSE_DIALOG_REQUEST_METHOD.equalsIgnoreCase(startLineSegments[0]);
    }

    private Runnable getCreateDialogRunnable(int direction, SipMessage m) {
        return () -> {
            List<SipDialog> duplicateDialogs = mTrackedDialogs.stream()
                    .filter(d -> d.getCallId().equals(m.getCallIdParameter()))
                    .collect(Collectors.toList());
            if (duplicateDialogs.size() > 0) {
                logi("trying to create a dialog for a call ID that already exists, skip: "
                        + duplicateDialogs);
                return;
            }
            SipDialog dialog = SipDialog.fromSipMessage(m);
            String[] startLineSegments =
                    SipMessageParsingUtils.splitStartLineAndVerify(m.getStartLine());
            mRcsStats.earlySipTransportSession(startLineSegments[0], dialog.getCallId(),
                    direction);
            logi("Starting new SipDialog: " + dialog);
            mTrackedDialogs.add(dialog);
        };
    }

    private Runnable getCloseDialogRunnable(SipMessage m) {
        return () -> {
            List<SipDialog> dialogsToClose = mTrackedDialogs.stream()
                    .filter(d -> d.isRequestAssociatedWithDialog(m))
                    .collect(Collectors.toList());
            if (dialogsToClose.isEmpty()) return;
            logi("Closing dialogs associated with: " + m);
            mRcsStats.onSipTransportSessionClosed(mSubId, m.getCallIdParameter(), 0, true);
            for (SipDialog d : dialogsToClose) {
                d.close();
                logi("Dialog closed: " + d);
            }
            notifySipDialogState();
        };
    }

    private Runnable getDialogStateChangeRunnable(SipMessage m) {
        return () -> {
            // This will return a dialog and all of its potential forks
            List<SipDialog> associatedDialogs = mTrackedDialogs.stream()
                    .filter(d -> d.isResponseAssociatedWithDialog(m))
                    .collect(Collectors.toList());
            if (associatedDialogs.isEmpty()) return;
            String messageToTag = SipMessageParsingUtils.getToTag(m.getHeaderSection());
            // If the to tag matches (or message to tag doesn't exist in dialog yet because this is
            // the first response), then we are done.
            SipDialog match = associatedDialogs.stream()
                    .filter(d -> d.getToTag() == null || d.getToTag().equals(messageToTag))
                    .findFirst().orElse(null);
            if (match == null) {
                // If it doesn't then we have a situation where we need to fork the existing dialog.
                // The dialog used to fork doesn't matter, since the required params are the same,
                // so simply use the first one in the returned list.
                logi("Dialog forked");
                match = associatedDialogs.get(0).forkDialog();
                mTrackedDialogs.add(match);
            }
            if (match != null) {
                logi("Dialog: " + match + " is associated with: " + m);
                updateSipDialogState(match, m);
                logi("Dialog state updated to " + match);
            } else {
                logi("No Dialogs are associated with: " + m);
            }
        };
    }

    private void updateSipDialogState(SipDialog d, SipMessage m) {
        String[] startLineSegments = SipMessageParsingUtils.splitStartLineAndVerify(
                m.getStartLine());
        if (startLineSegments == null) {
            logw("Could not parse start line for SIP message: " + m.getStartLine());
            return;
        }
        int statusCode = 0;
        try {
            statusCode = Integer.parseInt(startLineSegments[1]);
        } catch (NumberFormatException e) {
            logw("Could not parse status code for SIP message: " + m.getStartLine());
            return;
        }
        String toTag = SipMessageParsingUtils.getToTag(m.getHeaderSection());
        logi("updateSipDialogState: message has statusCode: " + statusCode + ", and to tag: "
                + toTag);
        // If specifically 100 Trying, then do not do anything.
        if (statusCode <= 100) return;
        // If 300+, then this dialog has received an error response and should move to closed state.
        if (statusCode >= 300) {
            mRcsStats.onSipTransportSessionClosed(mSubId, m.getCallIdParameter(), statusCode, true);
            d.close();
            notifySipDialogState();
            return;
        }
        if (toTag == null) logw("updateSipDialogState: No to tag for message: " + m);
        if (statusCode >= 200) {
            mRcsStats.confirmedSipTransportSession(m.getCallIdParameter(), statusCode);
            d.confirm(toTag);
            notifySipDialogState();
            return;
        }
        // 1XX responses still require updates to dialogs.
        d.earlyResponse(toTag);
        notifySipDialogState();
    }

    /**
     * This is a listener to handle SipDialog state of delegate
     * @param listener {@link SipDialogsStateListener}
     * @param isNeedNotify It indicates whether the current dialogs state should be notified.
     */
    public void setSipDialogsListener(SipDialogsStateListener listener,
            boolean isNeedNotify) {
        mSipDialogsListener = listener;
        if (listener == null) {
            return;
        }
        if (isNeedNotify) {
            notifySipDialogState();
        }
    }

    private void notifySipDialogState() {
        if (mSipDialogsListener == null) {
            return;
        }
        List<SipDialogState> dialogStates = new ArrayList<>();
        for (SipDialog d : mTrackedDialogs) {
            SipDialogState dialog = new SipDialogState.Builder(d.getState()).build();
            dialogStates.add(dialog);
        }
        mSipDialogsListener.reMappingSipDelegateState(mDelegateKey, dialogStates);
    }

    private void logi(String log) {
        Log.i(SipTransportController.LOG_TAG, TAG + ": " + log);
        mLocalLog.log("[I] " + log);
    }

    private void logw(String log) {
        Log.w(SipTransportController.LOG_TAG, TAG + ": " + log);
        mLocalLog.log("[W] " + log);
    }
}
