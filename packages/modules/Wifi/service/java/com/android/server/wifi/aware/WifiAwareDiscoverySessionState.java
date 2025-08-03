/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.wifi.aware;

import static android.net.wifi.aware.WifiAwareManager.WIFI_AWARE_SUSPEND_INTERNAL_ERROR;

import static com.android.server.wifi.aware.WifiAwareStateManager.INSTANT_MODE_24GHZ;
import static com.android.server.wifi.aware.WifiAwareStateManager.INSTANT_MODE_5GHZ;
import static com.android.server.wifi.aware.WifiAwareStateManager.INSTANT_MODE_DISABLED;
import static com.android.server.wifi.aware.WifiAwareStateManager.NAN_PAIRING_REQUEST_TYPE_SETUP;
import static com.android.server.wifi.aware.WifiAwareStateManager.NAN_PAIRING_REQUEST_TYPE_VERIFICATION;

import android.annotation.NonNull;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.WifiScanner;
import android.net.wifi.aware.AwarePairingConfig;
import android.net.wifi.aware.IWifiAwareDiscoverySessionCallback;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.util.HexEncoding;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import com.android.server.wifi.hal.WifiNanIface.NanStatusCode;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

/**
 * Manages the state of a single Aware discovery session (publish or subscribe).
 * Primary state consists of a callback through which session callbacks are
 * executed as well as state related to currently active discovery sessions:
 * publish/subscribe ID, and MAC address caching (hiding) from clients.
 */
public class WifiAwareDiscoverySessionState {
    private static final String TAG = "WifiAwareDiscSessState";
    private boolean mDbg = false;

    private static int sNextPeerIdToBeAllocated = 100; // used to create a unique peer ID

    private final WifiAwareNativeApi mWifiAwareNativeApi;
    private int mSessionId;
    private byte mPubSubId;
    private IWifiAwareDiscoverySessionCallback mCallback;
    private boolean mIsPublishSession;
    private boolean mIsRangingEnabled;
    private final long mCreationTime;
    private long mUpdateTime;
    private boolean mInstantModeEnabled;
    private int mInstantModeBand;
    private AwarePairingConfig mPairingConfig;
    private boolean mIsSuspendable;
    private boolean mIsSuspended;

    static class PeerInfo {
        PeerInfo(int instanceId, byte[] mac) {
            mInstanceId = instanceId;
            mMac = mac;
        }

        int mInstanceId;
        byte[] mMac;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("instanceId [");
            sb.append(mInstanceId).append(", mac=").append(HexEncoding.encode(mMac)).append("]");
            return sb.toString();
        }
    }

    private final SparseArray<PeerInfo> mPeerInfoByRequestorInstanceId = new SparseArray<>();

    public WifiAwareDiscoverySessionState(WifiAwareNativeApi wifiAwareNativeApi, int sessionId,
            byte pubSubId, IWifiAwareDiscoverySessionCallback callback, boolean isPublishSession,
            boolean isRangingEnabled, long creationTime, boolean instantModeEnabled,
            int instantModeBand, boolean isSuspendable,
            AwarePairingConfig pairingConfig) {
        mWifiAwareNativeApi = wifiAwareNativeApi;
        mSessionId = sessionId;
        mPubSubId = pubSubId;
        mCallback = callback;
        mIsPublishSession = isPublishSession;
        mIsRangingEnabled = isRangingEnabled;
        mCreationTime = creationTime;
        mUpdateTime = creationTime;
        mInstantModeEnabled = instantModeEnabled;
        mInstantModeBand = instantModeBand;
        mIsSuspendable = isSuspendable;
        mPairingConfig = pairingConfig;
    }

    /**
     * Enable verbose logging.
     */
    public void enableVerboseLogging(boolean verbose) {
        mDbg = verbose;
    }

    public int getSessionId() {
        return mSessionId;
    }

    public int getPubSubId() {
        return mPubSubId;
    }

    public boolean isPublishSession() {
        return mIsPublishSession;
    }

    public boolean isRangingEnabled() {
        return mIsRangingEnabled;
    }

    public void setRangingEnabled(boolean enabled) {
        mIsRangingEnabled = enabled;
    }

    public void setInstantModeEnabled(boolean enabled) {
        mInstantModeEnabled = enabled;
    }

    public void setInstantModeBand(int band) {
        mInstantModeBand = band;
    }

    public boolean isSuspendable() {
        return mIsSuspendable;
    }

    public boolean isSessionSuspended() {
        return mIsSuspended;
    }

    /**
     * Check if proposed method can be fulfilled by the configure.
     */
    public boolean acceptsBootstrappingMethod(int method) {
        if (mPairingConfig == null) {
            return false;
        }
        return (mPairingConfig.getBootstrappingMethods() & method) != 0;
    }

    /**
     * Check the instant communication mode of the client.
     * @param timeout Specify a interval when instant mode config timeout
     * @return current instant mode one of the {@code INSTANT_MODE_*}
     */
    public int getInstantMode(long timeout) {
        if (SystemClock.elapsedRealtime() - mUpdateTime > timeout || !mInstantModeEnabled) {
            return INSTANT_MODE_DISABLED;
        }
        if (mInstantModeBand == WifiScanner.WIFI_BAND_5_GHZ) {
            return INSTANT_MODE_5GHZ;
        }
        return INSTANT_MODE_24GHZ;
    }

    public long getCreationTime() {
        return mCreationTime;
    }

    public IWifiAwareDiscoverySessionCallback getCallback() {
        return mCallback;
    }

    /**
     * Return the peer information of the specified peer ID - or a null if no such peer ID is
     * registered.
     */
    public PeerInfo getPeerInfo(int peerId) {
        return mPeerInfoByRequestorInstanceId.get(peerId);
    }

    /**
     * Destroy the current discovery session - stops publishing or subscribing
     * if currently active.
     */
    public void terminate() {
        try {
            mCallback.onSessionTerminated(NanStatusCode.SUCCESS);
        } catch (RemoteException e) {
            Log.w(TAG,
                    "onSessionTerminatedLocal onSessionTerminated(): RemoteException (FYI): " + e);
        }
        mCallback = null;

        if (mIsPublishSession) {
            mWifiAwareNativeApi.stopPublish((short) 0, mPubSubId);
        } else {
            mWifiAwareNativeApi.stopSubscribe((short) 0, mPubSubId);
        }
    }

    /**
     * Indicates whether the publish/subscribe ID (a HAL ID) corresponds to this
     * session.
     *
     * @param pubSubId The publish/subscribe HAL ID to be tested.
     * @return true if corresponds to this session, false otherwise.
     */
    public boolean isPubSubIdSession(int pubSubId) {
        return mPubSubId == pubSubId;
    }

    /**
     * Modify a publish discovery session.
     *  @param transactionId Transaction ID for the transaction - used in the
     *            async callback to match with the original request.
     * @param config Configuration of the publish session.
     * @param nik
     */
    public boolean updatePublish(short transactionId, PublishConfig config, byte[] nik) {
        if (!mIsPublishSession) {
            Log.e(TAG, "A SUBSCRIBE session is being used to publish");
            try {
                mCallback.onSessionConfigFail(NanStatusCode.INTERNAL_FAILURE);
            } catch (RemoteException e) {
                Log.e(TAG, "updatePublish: RemoteException=" + e);
            }
            return false;
        }

        mUpdateTime = SystemClock.elapsedRealtime();
        boolean success = mWifiAwareNativeApi.publish(transactionId, mPubSubId, config, nik);
        if (!success) {
            try {
                mCallback.onSessionConfigFail(NanStatusCode.INTERNAL_FAILURE);
            } catch (RemoteException e) {
                Log.w(TAG, "updatePublish onSessionConfigFail(): RemoteException (FYI): " + e);
            }
        } else {
            mPairingConfig = config.getPairingConfig();
        }

        return success;
    }

    /**
     * Modify a subscribe discovery session.
     *  @param transactionId Transaction ID for the transaction - used in the
     *            async callback to match with the original request.
     * @param config Configuration of the subscribe session.
     * @param nik
     */
    public boolean updateSubscribe(short transactionId, SubscribeConfig config, byte[] nik) {
        if (mIsPublishSession) {
            Log.e(TAG, "A PUBLISH session is being used to subscribe");
            try {
                mCallback.onSessionConfigFail(NanStatusCode.INTERNAL_FAILURE);
            } catch (RemoteException e) {
                Log.e(TAG, "updateSubscribe: RemoteException=" + e);
            }
            return false;
        }

        mUpdateTime = SystemClock.elapsedRealtime();
        boolean success = mWifiAwareNativeApi.subscribe(transactionId, mPubSubId, config, nik);
        if (!success) {
            try {
                mCallback.onSessionConfigFail(NanStatusCode.INTERNAL_FAILURE);
            } catch (RemoteException e) {
                Log.w(TAG, "updateSubscribe onSessionConfigFail(): RemoteException (FYI): " + e);
            }
        } else {
            mPairingConfig = config.getPairingConfig();
        }

        return success;
    }

    /**
     * Send a message to a peer which is part of a discovery session.
     *
     * @param transactionId Transaction ID for the transaction - used in the
     *            async callback to match with the original request.
     * @param peerId ID of the peer. Obtained through previous communication (a
     *            match indication).
     * @param message Message byte array to send to the peer.
     * @param messageId A message ID provided by caller to be used in any
     *            callbacks related to the message (success/failure).
     */
    public boolean sendMessage(short transactionId, int peerId, byte[] message, int messageId) {
        PeerInfo peerInfo = mPeerInfoByRequestorInstanceId.get(peerId);
        if (peerInfo == null) {
            Log.e(TAG, "sendMessage: attempting to send a message to an address which didn't "
                    + "match/contact us");
            try {
                mCallback.onMessageSendFail(messageId, NanStatusCode.INTERNAL_FAILURE);
            } catch (RemoteException e) {
                Log.e(TAG, "sendMessage: RemoteException=" + e);
            }
            return false;
        }

        boolean success = mWifiAwareNativeApi.sendMessage(transactionId, mPubSubId,
                peerInfo.mInstanceId, peerInfo.mMac, message, messageId);
        if (!success) {
            try {
                mCallback.onMessageSendFail(messageId, NanStatusCode.INTERNAL_FAILURE);
            } catch (RemoteException e) {
                Log.e(TAG, "sendMessage: RemoteException=" + e);
            }
            return false;
        }

        return success;
    }

    /**
     * Request to suspend the current session.
     *
     * @param transactionId Transaction ID for the transaction - used in the async callback to match
     *     with the original request.
     */
    public boolean suspend(short transactionId) {
        if (!mWifiAwareNativeApi.suspendRequest(transactionId, mPubSubId)) {
            onSuspendFail(WIFI_AWARE_SUSPEND_INTERNAL_ERROR);
            return false;
        }
        return true;
    }

    /**
     * Notifies that session suspension has succeeded and updates the session state.
     */
    public void onSuspendSuccess() {
        mIsSuspended = true;
        try {
            mCallback.onSessionSuspendSucceeded();
        } catch (RemoteException e) {
            Log.e(TAG, "onSuspendSuccess: RemoteException=" + e);
        }
    }

    /**
     * Notify the session callback that suspension failed.
     * @param reason an {@link WifiAwareManager.SessionSuspensionFailedReasonCode} indicating why
     *               the session failed to be suspended.
     */
    public void onSuspendFail(@WifiAwareManager.SessionSuspensionFailedReasonCode int reason) {
        try {
            mCallback.onSessionSuspendFail(reason);
        } catch (RemoteException e) {
            Log.e(TAG, "onSuspendFail: RemoteException=" + e);
        }
    }

    /**
     * Request to resume the current (suspended) session.
     *
     * @param transactionId Transaction ID for the transaction - used in the async callback to match
     *     with the original request.
     */
    public boolean resume(short transactionId) {
        if (!mWifiAwareNativeApi.resumeRequest(transactionId, mPubSubId)) {
            onResumeFail(WIFI_AWARE_SUSPEND_INTERNAL_ERROR);
            return false;
        }
        return true;
    }

    /**
     * Notifies that has been resumed successfully and updates the session state.
     */
    public void onResumeSuccess() {
        mIsSuspended = false;
        try {
            mCallback.onSessionResumeSucceeded();
        } catch (RemoteException e) {
            Log.e(TAG, "onResumeSuccess: RemoteException=" + e);
        }
    }

    /**
     * Notify the session callback that the resumption of the session failed.
     * @param reason an {@link WifiAwareManager.SessionResumptionFailedReasonCode} indicating why
     *               the session failed to be resumed.
     */
    public void onResumeFail(@WifiAwareManager.SessionResumptionFailedReasonCode int reason) {
        try {
            mCallback.onSessionResumeFail(reason);
        } catch (RemoteException e) {
            Log.e(TAG, "onResumeFail: RemoteException=" + e);
        }
    }

    /**
     * Initiate a NAN pairing request for this publish/subscribe session
     * @param transactionId Transaction ID for the transaction - used in the
     *            async callback to match with the original request.
     * @param peerId ID of the peer. Obtained through previous communication (a
     *            match indication).
     * @param password credential for the pairing setup
     * @param requestType Setup or verification
     * @param nik NAN identity key
     * @param pmk credential for the pairing verification
     * @param akm Key exchange method is used for pairing
     * @return True if the request send succeed.
     */
    public boolean initiatePairing(short transactionId,
            int peerId, String password, int requestType, byte[] nik, byte[] pmk, int akm,
            int cipherSuite) {
        PeerInfo peerInfo = mPeerInfoByRequestorInstanceId.get(peerId);
        if (peerInfo == null) {
            Log.e(TAG, "initiatePairing: attempting to send pairing request to an address which"
                    + "didn't match/contact us");
            if (requestType == NAN_PAIRING_REQUEST_TYPE_VERIFICATION) {
                return false;
            }
            try {
                mCallback.onPairingSetupConfirmed(peerId, false, null);
            } catch (RemoteException e) {
                Log.e(TAG, "initiatePairing: RemoteException=" + e);
            }
            return false;
        }

        boolean success = mWifiAwareNativeApi.initiatePairing(transactionId,
                peerInfo.mInstanceId, peerInfo.mMac, nik,
                mPairingConfig != null && mPairingConfig.isPairingCacheEnabled(),
                requestType, pmk, password, akm, cipherSuite);
        if (!success) {
            if (requestType == NAN_PAIRING_REQUEST_TYPE_VERIFICATION) {
                return false;
            }
            try {
                mCallback.onPairingSetupConfirmed(peerId, false, null);
            } catch (RemoteException e) {
                Log.e(TAG, "initiatePairing: RemoteException=" + e);
            }
            return false;
        }

        return true;
    }

    /**
     * Response to a NAN pairing request for this from this session
     * @param transactionId Transaction ID for the transaction - used in the
     *            async callback to match with the original request.
     * @param peerId ID of the peer. Obtained through previous communication (a
     *            match indication).
     * @param pairingId The id of the current pairing session
     * @param accept True if accpect, false otherwise
     * @param password credential for the pairing setup
     * @param requestType Setup or verification
     * @param nik NAN identity key
     * @param pmk credential for the pairing verification
     * @param akm Key exchange method is used for pairing
     * @return True if the request send succeed.
     */
    public boolean respondToPairingRequest(short transactionId, int peerId, int pairingId,
            boolean accept, byte[] nik, int requestType, byte[] pmk, String password, int akm,
            int cipherSuite) {
        PeerInfo peerInfo = mPeerInfoByRequestorInstanceId.get(peerId);
        if (peerInfo == null) {
            Log.e(TAG, "respondToPairingRequest: attempting to response to message to an "
                    + "address which didn't match/contact us");
            if (requestType == NAN_PAIRING_REQUEST_TYPE_VERIFICATION) {
                return false;
            }
            try {
                mCallback.onPairingSetupConfirmed(peerId, false, null);
            } catch (RemoteException e) {
                Log.e(TAG, "respondToPairingRequest: RemoteException=" + e);
            }
            return false;
        }

        boolean success = mWifiAwareNativeApi.respondToPairingRequest(transactionId, pairingId,
                accept, nik, mPairingConfig != null && mPairingConfig.isPairingCacheEnabled(),
                requestType, pmk, password, akm, cipherSuite);
        if (!success) {
            if (requestType == NAN_PAIRING_REQUEST_TYPE_VERIFICATION) {
                return false;
            }
            try {
                mCallback.onPairingSetupConfirmed(peerId, false, null);
            } catch (RemoteException e) {
                Log.e(TAG, "respondToPairingRequest: RemoteException=" + e);
            }
            return false;
        }

        return true;
    }

    /**
     * Initiate an Aware bootstrapping request
     *
     * @param transactionId Transaction ID for the transaction - used in the
     *                      async callback to match with the original request.
     * @param peerId        ID of the peer. Obtained through previous communication (a
     *                      match indication).
     * @param method        proposed bootstrapping method
     * @param isComeBack    If the request is for a previous comeback response
     * @return True if the request send succeed.
     */
    public boolean initiateBootstrapping(short transactionId,
            int peerId, int method, byte[] cookie, boolean isComeBack) {
        PeerInfo peerInfo = mPeerInfoByRequestorInstanceId.get(peerId);
        if (peerInfo == null) {
            Log.e(TAG, "initiateBootstrapping: attempting to send pairing request to an address"
                    + " which didn't match/contact us");
            try {
                mCallback.onBootstrappingVerificationConfirmed(peerId, false, method);
            } catch (RemoteException e) {
                Log.e(TAG, "initiateBootstrapping: RemoteException=" + e);
            }
            return false;
        }

        boolean success = mWifiAwareNativeApi.initiateBootstrapping(transactionId,
                peerInfo.mInstanceId, peerInfo.mMac, method, cookie, mPubSubId, isComeBack);
        if (!success) {
            try {
                mCallback.onBootstrappingVerificationConfirmed(peerId, false, method);
            } catch (RemoteException e) {
                Log.e(TAG, "initiateBootstrapping: RemoteException=" + e);
            }
            return false;
        }

        return true;
    }

    /**
     * Respond to a bootstrapping request
     * @param transactionId Transaction ID for the transaction - used in the
     *            async callback to match with the original request.
     * @param peerId ID of the peer. Obtained through previous communication (a
     *            match indication).
     * @param bootstrappingId The id of current bootstrapping session
     * @param accept True if the method proposed by peer is accepted, false otherwise
     * @param method the accepted method
     * @return True if the send success
     */
    public boolean respondToBootstrapping(short transactionId,
            int peerId, int bootstrappingId, boolean accept, int method) {
        PeerInfo peerInfo = mPeerInfoByRequestorInstanceId.get(peerId);
        if (peerInfo == null) {
            Log.e(TAG, "initiateBootstrapping: attempting to send pairing request to"
                    + " an address which didn't match/contact us");
            return false;
        }

        boolean success = mWifiAwareNativeApi.respondToBootstrappingRequest(transactionId,
                bootstrappingId, accept, mPubSubId);
        return success;
    }

    /**
     * Callback from HAL when a discovery occurs - i.e. when a match to an
     * active subscription request or to a solicited publish request occurs.
     * Propagates to client if registered.
     * @param requestorInstanceId The ID used to identify the peer in this
     *            matched session.
     * @param peerMac The MAC address of the peer. Never propagated to client
     *            due to privacy concerns.
     * @param serviceSpecificInfo Information from the discovery advertisement
*            (usually not used in the match decisions).
     * @param matchFilter The filter from the discovery advertisement (which was
*            used in the match decision).
     * @param rangingIndication Bit mask indicating the type of ranging event triggered.
     * @param rangeMm The range to the peer in mm (valid if rangingIndication specifies ingress
     */
    public int onMatch(int requestorInstanceId, byte[] peerMac, byte[] serviceSpecificInfo,
            byte[] matchFilter, int rangingIndication, int rangeMm, int peerCipherSuite,
            byte[] scid, String pairingAlias,
            AwarePairingConfig pairingConfig, @NonNull List<OuiKeyedData> vendorDataList) {
        int peerId = getPeerIdOrAddIfNew(requestorInstanceId, peerMac);
        OuiKeyedData[] vendorDataArray = null;
        if (!vendorDataList.isEmpty()) {
            vendorDataArray = new OuiKeyedData[vendorDataList.size()];
            vendorDataList.toArray(vendorDataArray);
        }

        try {
            if (rangingIndication == 0) {
                mCallback.onMatch(peerId, serviceSpecificInfo, matchFilter, peerCipherSuite, scid,
                        pairingAlias, pairingConfig, vendorDataArray);
            } else {
                mCallback.onMatchWithDistance(peerId, serviceSpecificInfo, matchFilter, rangeMm,
                        peerCipherSuite, scid, pairingAlias, pairingConfig, vendorDataArray);
            }
        } catch (RemoteException e) {
            Log.w(TAG, "onMatch: RemoteException (FYI): " + e);
        }
        return peerId;
    }

    /**
     * Callback from HAL when a discovered peer is lost - i.e. when a discovered peer with a matched
     * session is no longer visible.
     *
     * @param requestorInstanceId The ID used to identify the peer in this matched session.
     */
    public void onMatchExpired(int requestorInstanceId) {
        int peerId = 0;
        for (int i = 0; i < mPeerInfoByRequestorInstanceId.size(); ++i) {
            PeerInfo peerInfo = mPeerInfoByRequestorInstanceId.valueAt(i);
            if (peerInfo.mInstanceId == requestorInstanceId) {
                peerId = mPeerInfoByRequestorInstanceId.keyAt(i);
                mPeerInfoByRequestorInstanceId.delete(peerId);
                break;
            }
        }
        if (peerId == 0) {
            return;
        }

        try {
            mCallback.onMatchExpired(peerId);
        } catch (RemoteException e) {
            Log.w(TAG, "onMatch: RemoteException (FYI): " + e);
        }
    }

    /**
     * Callback from HAL when a message is received from a peer in a discovery
     * session. Propagated to client if registered.
     *
     * @param requestorInstanceId An ID used to identify the peer.
     * @param peerMac The MAC address of the peer sending the message. This
     *            information is never propagated to the client due to privacy
     *            concerns.
     * @param message The received message.
     */
    public void onMessageReceived(int requestorInstanceId, byte[] peerMac, byte[] message) {
        int peerId = getPeerIdOrAddIfNew(requestorInstanceId, peerMac);

        try {
            mCallback.onMessageReceived(peerId, message);
        } catch (RemoteException e) {
            Log.w(TAG, "onMessageReceived: RemoteException (FYI): " + e);
        }
    }

    /**
     * Event that receive the pairing request from the peer
     */
    public void onPairingRequestReceived(int requestorInstanceId, byte[] peerMac,
            int pairingId) {
        int peerId = getPeerIdOrAddIfNew(requestorInstanceId, peerMac);
        try {
            mCallback.onPairingSetupRequestReceived(peerId, pairingId);
        } catch (RemoteException e) {
            Log.w(TAG, "onPairingRequestReceived: RemoteException (FYI): " + e);
        }
    }

    /**
     * Event that receive the pairing request finished
     */
    public void onPairingConfirmReceived(int peerId, boolean accept, String alias,
            int requestType) {
        try {
            if (requestType == NAN_PAIRING_REQUEST_TYPE_SETUP) {
                mCallback.onPairingSetupConfirmed(peerId, accept, alias);
            } else {
                mCallback.onPairingVerificationConfirmed(peerId, accept, alias);
            }
        } catch (RemoteException e) {
            Log.w(TAG, "onPairingConfirmReceived: RemoteException (FYI): " + e);
        }
    }

    /**
     * Event that receive the bootstrapping request finished
     */
    public void onBootStrappingConfirmReceived(int peerId, boolean accept, int method) {
        try {
            mCallback.onBootstrappingVerificationConfirmed(peerId, accept, method);
        } catch (RemoteException e) {
            Log.w(TAG, "onBootStrappingConfirmReceived: RemoteException (FYI): " + e);
        }
    }


    /**
     * Get the ID of the peer assign by the framework
     */
    public int getPeerIdOrAddIfNew(int requestorInstanceId, byte[] peerMac) {
        for (int i = 0; i < mPeerInfoByRequestorInstanceId.size(); ++i) {
            PeerInfo peerInfo = mPeerInfoByRequestorInstanceId.valueAt(i);
            if (peerInfo.mInstanceId == requestorInstanceId && Arrays.equals(peerMac,
                    peerInfo.mMac)) {
                return mPeerInfoByRequestorInstanceId.keyAt(i);
            }
        }

        int newPeerId = sNextPeerIdToBeAllocated++;
        PeerInfo newPeerInfo = new PeerInfo(requestorInstanceId, peerMac);
        mPeerInfoByRequestorInstanceId.put(newPeerId, newPeerInfo);
        Log.d(TAG, "New peer info: peerId=" + newPeerId + ", peerInfo=" + newPeerInfo);

        return newPeerId;
    }

    /**
     * Dump the internal state of the class.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("AwareSessionState:");
        pw.println("  mSessionId: " + mSessionId);
        pw.println("  mIsPublishSession: " + mIsPublishSession);
        pw.println("  mPubSubId: " + mPubSubId);
        pw.println("  mPeerInfoByRequestorInstanceId: [" + mPeerInfoByRequestorInstanceId + "]");
    }
}
