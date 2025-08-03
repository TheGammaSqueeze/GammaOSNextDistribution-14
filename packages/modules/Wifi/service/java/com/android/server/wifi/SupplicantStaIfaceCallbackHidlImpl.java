/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.server.wifi;

import static com.android.server.wifi.hotspot2.anqp.Constants.ANQPElementType.ANQP3GPPNetwork;
import static com.android.server.wifi.hotspot2.anqp.Constants.ANQPElementType.ANQPDomName;
import static com.android.server.wifi.hotspot2.anqp.Constants.ANQPElementType.ANQPIPAddrAvailability;
import static com.android.server.wifi.hotspot2.anqp.Constants.ANQPElementType.ANQPNAIRealm;
import static com.android.server.wifi.hotspot2.anqp.Constants.ANQPElementType.ANQPRoamingConsortium;
import static com.android.server.wifi.hotspot2.anqp.Constants.ANQPElementType.ANQPVenueName;
import static com.android.server.wifi.hotspot2.anqp.Constants.ANQPElementType.ANQPVenueUrl;
import static com.android.server.wifi.hotspot2.anqp.Constants.ANQPElementType.HSConnCapability;
import static com.android.server.wifi.hotspot2.anqp.Constants.ANQPElementType.HSFriendlyName;
import static com.android.server.wifi.hotspot2.anqp.Constants.ANQPElementType.HSOSUProviders;
import static com.android.server.wifi.hotspot2.anqp.Constants.ANQPElementType.HSWANMetrics;

import android.annotation.NonNull;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaIfaceCallback;
import android.hardware.wifi.supplicant.V1_4.ISupplicantStaIfaceCallback.MboAssocDisallowedReasonCode;
import android.net.MacAddress;
import android.net.wifi.SecurityParams;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiSsid;
import android.util.Log;

import com.android.server.wifi.hotspot2.AnqpEvent;
import com.android.server.wifi.hotspot2.IconEvent;
import com.android.server.wifi.hotspot2.WnmData;
import com.android.server.wifi.hotspot2.anqp.ANQPElement;
import com.android.server.wifi.hotspot2.anqp.ANQPParser;
import com.android.server.wifi.hotspot2.anqp.Constants;
import com.android.server.wifi.util.NativeUtil;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

abstract class SupplicantStaIfaceCallbackHidlImpl extends ISupplicantStaIfaceCallback.Stub {
    private static final String TAG = SupplicantStaIfaceCallbackHidlImpl.class.getSimpleName();
    private final SupplicantStaIfaceHalHidlImpl mStaIfaceHal;
    private final String mIfaceName;
    private final Object mLock;
    private final WifiMonitor mWifiMonitor;
    private final SsidTranslator mSsidTranslator;
    // Used to help check for PSK password mismatch & EAP connection failure.
    private int mStateBeforeDisconnect = State.INACTIVE;
    // Current SSID in WifiSsid.toString() format. May be translated to UTF-8 if SSID translation
    // is enabled.
    private String mCurrentSsid = null;

    SupplicantStaIfaceCallbackHidlImpl(@NonNull SupplicantStaIfaceHalHidlImpl staIfaceHal,
            @NonNull String ifaceName,
            @NonNull Object lock,
            @NonNull WifiMonitor wifiMonitor,
            @NonNull SsidTranslator ssidTranslator) {
        mStaIfaceHal = staIfaceHal;
        mIfaceName = ifaceName;
        mLock = lock;
        mWifiMonitor = wifiMonitor;
        mSsidTranslator = ssidTranslator;
    }

    /**
     * Converts the supplicant state received from HIDL to the equivalent framework state.
     */
    protected static SupplicantState supplicantHidlStateToFrameworkState(int state) {
        switch (state) {
            case ISupplicantStaIfaceCallback.State.DISCONNECTED:
                return SupplicantState.DISCONNECTED;
            case ISupplicantStaIfaceCallback.State.IFACE_DISABLED:
                return SupplicantState.INTERFACE_DISABLED;
            case ISupplicantStaIfaceCallback.State.INACTIVE:
                return SupplicantState.INACTIVE;
            case ISupplicantStaIfaceCallback.State.SCANNING:
                return SupplicantState.SCANNING;
            case ISupplicantStaIfaceCallback.State.AUTHENTICATING:
                return SupplicantState.AUTHENTICATING;
            case ISupplicantStaIfaceCallback.State.ASSOCIATING:
                return SupplicantState.ASSOCIATING;
            case ISupplicantStaIfaceCallback.State.ASSOCIATED:
                return SupplicantState.ASSOCIATED;
            case ISupplicantStaIfaceCallback.State.FOURWAY_HANDSHAKE:
                return SupplicantState.FOUR_WAY_HANDSHAKE;
            case ISupplicantStaIfaceCallback.State.GROUP_HANDSHAKE:
                return SupplicantState.GROUP_HANDSHAKE;
            case ISupplicantStaIfaceCallback.State.COMPLETED:
                return SupplicantState.COMPLETED;
            default:
                throw new IllegalArgumentException("Invalid state: " + state);
        }
    }


    /**
     * Parses the provided payload into an ANQP element.
     *
     * @param infoID  Element type.
     * @param payload Raw payload bytes.
     * @return AnqpElement instance on success, null on failure.
     */
    private ANQPElement parseAnqpElement(Constants.ANQPElementType infoID,
                                         ArrayList<Byte> payload) {
        synchronized (mLock) {
            try {
                return Constants.getANQPElementID(infoID) != null
                        ? ANQPParser.parseElement(
                        infoID, ByteBuffer.wrap(NativeUtil.byteArrayFromArrayList(payload)))
                        : ANQPParser.parseHS20Element(
                        infoID, ByteBuffer.wrap(NativeUtil.byteArrayFromArrayList(payload)));
            } catch (IOException | BufferUnderflowException e) {
                Log.e(TAG, "Failed parsing ANQP element payload: " + infoID, e);
                return null;
            }
        }
    }

    /**
     * Parse the ANQP element data and add to the provided elements map if successful.
     *
     * @param elementsMap Map to add the parsed out element to.
     * @param infoID  Element type.
     * @param payload Raw payload bytes.
     */
    private void addAnqpElementToMap(Map<Constants.ANQPElementType, ANQPElement> elementsMap,
                                     Constants.ANQPElementType infoID,
                                     ArrayList<Byte> payload) {
        synchronized (mLock) {
            if (payload == null || payload.isEmpty()) return;
            ANQPElement element = parseAnqpElement(infoID, payload);
            if (element != null) {
                elementsMap.put(infoID, element);
            }
        }
    }

    @Override
    public void onNetworkAdded(int id) {
        synchronized (mLock) {
            mStaIfaceHal.logCallback("onNetworkAdded id=" + id);
        }
    }

    @Override
    public void onNetworkRemoved(int id) {
        synchronized (mLock) {
            mStaIfaceHal.logCallback("onNetworkRemoved id=" + id);
            // Reset state since network has been removed.
            mStateBeforeDisconnect = State.INACTIVE;
        }
    }

    /**
     * Added to plumb the new {@code filsHlpSent} param from the V1.3 callback version.
     */
    public void onStateChanged(int newState, byte[/* 6 */] bssid, int id, ArrayList<Byte> ssid,
            boolean filsHlpSent) {
        synchronized (mLock) {
            mStaIfaceHal.logCallback("onStateChanged");
            SupplicantState newSupplicantState =
                    supplicantHidlStateToFrameworkState(newState);
            WifiSsid wifiSsid = mSsidTranslator.getTranslatedSsidForStaIface(
                    WifiSsid.fromBytes(NativeUtil.byteArrayFromArrayList(ssid)), mIfaceName);
            String bssidStr = NativeUtil.macAddressFromByteArray(bssid);
            if (newState != State.DISCONNECTED) {
                // onStateChanged(DISCONNECTED) may come before onDisconnected(), so add this
                // cache to track the state before the disconnect.
                mStateBeforeDisconnect = newState;
            }

            if (newState == State.ASSOCIATING || newState == State.ASSOCIATED
                    || newState == State.COMPLETED) {
                mStaIfaceHal.updateOnLinkedNetworkRoaming(mIfaceName, id, false);
            }

            if (newState == State.COMPLETED) {
                mWifiMonitor.broadcastNetworkConnectionEvent(
                        mIfaceName, mStaIfaceHal.getCurrentNetworkId(mIfaceName), filsHlpSent,
                        wifiSsid, bssidStr);
            } else if (newState == State.ASSOCIATING) {
                mCurrentSsid = wifiSsid.toString();
            }
            mWifiMonitor.broadcastSupplicantStateChangeEvent(
                    mIfaceName, mStaIfaceHal.getCurrentNetworkId(mIfaceName), wifiSsid,
                    bssidStr, 0, newSupplicantState);
        }
    }

    @Override
    public void onStateChanged(int newState, byte[/* 6 */] bssid, int id, ArrayList<Byte> ssid) {
        onStateChanged(newState, bssid, id, ssid, false);
    }

    public void onAnqpQueryDone(byte[/* 6 */] bssid,
            ISupplicantStaIfaceCallback.AnqpData data,
            ISupplicantStaIfaceCallback.Hs20AnqpData hs20Data,
            android.hardware.wifi.supplicant.V1_4.ISupplicantStaIfaceCallback.AnqpData dataV14) {
        Map<Constants.ANQPElementType, ANQPElement> elementsMap = new HashMap<>();
        addAnqpElementToMap(elementsMap, ANQPVenueName, data.venueName);
        addAnqpElementToMap(elementsMap, ANQPRoamingConsortium, data.roamingConsortium);
        addAnqpElementToMap(
                elementsMap, ANQPIPAddrAvailability, data.ipAddrTypeAvailability);
        addAnqpElementToMap(elementsMap, ANQPNAIRealm, data.naiRealm);
        addAnqpElementToMap(elementsMap, ANQP3GPPNetwork, data.anqp3gppCellularNetwork);
        addAnqpElementToMap(elementsMap, ANQPDomName, data.domainName);
        if (dataV14 != null) {
            addAnqpElementToMap(elementsMap, ANQPVenueUrl, dataV14.venueUrl);
        }
        addAnqpElementToMap(elementsMap, HSFriendlyName, hs20Data.operatorFriendlyName);
        addAnqpElementToMap(elementsMap, HSWANMetrics, hs20Data.wanMetrics);
        addAnqpElementToMap(elementsMap, HSConnCapability, hs20Data.connectionCapability);
        addAnqpElementToMap(elementsMap, HSOSUProviders, hs20Data.osuProvidersList);
        mWifiMonitor.broadcastAnqpDoneEvent(
                mIfaceName, new AnqpEvent(NativeUtil.macAddressToLong(bssid), elementsMap));
    }
    @Override
    public void onAnqpQueryDone(byte[/* 6 */] bssid,
                                ISupplicantStaIfaceCallback.AnqpData data,
                                ISupplicantStaIfaceCallback.Hs20AnqpData hs20Data) {
        synchronized (mLock) {
            mStaIfaceHal.logCallback("onAnqpQueryDone");
            onAnqpQueryDone(bssid, data, hs20Data, null /* v1.4 element */);
        }
    }

    @Override
    public void onHs20IconQueryDone(byte[/* 6 */] bssid, String fileName,
                                    ArrayList<Byte> data) {
        synchronized (mLock) {
            mStaIfaceHal.logCallback("onHs20IconQueryDone");
            mWifiMonitor.broadcastIconDoneEvent(
                    mIfaceName,
                    new IconEvent(NativeUtil.macAddressToLong(bssid), fileName, data.size(),
                            NativeUtil.byteArrayFromArrayList(data)));
        }
    }

    @Override
    public void onHs20SubscriptionRemediation(byte[/* 6 */] bssid, byte osuMethod, String url) {
        synchronized (mLock) {
            mStaIfaceHal.logCallback("onHs20SubscriptionRemediation");
            mWifiMonitor.broadcastWnmEvent(
                    mIfaceName,
                    WnmData.createRemediationEvent(NativeUtil.macAddressToLong(bssid), url,
                            osuMethod));
        }
    }

    @Override
    public void onHs20DeauthImminentNotice(byte[/* 6 */] bssid, int reasonCode,
                                           int reAuthDelayInSec, String url) {
        synchronized (mLock) {
            mStaIfaceHal.logCallback("onHs20DeauthImminentNotice");
            mWifiMonitor.broadcastWnmEvent(
                    mIfaceName,
                    WnmData.createDeauthImminentEvent(NativeUtil.macAddressToLong(bssid), url,
                            reasonCode == WnmData.ESS, reAuthDelayInSec));
        }
    }

    @Override
    public void onDisconnected(byte[/* 6 */] bssid, boolean locallyGenerated, int reasonCode) {
        synchronized (mLock) {
            mStaIfaceHal.logCallback("onDisconnected");
            if (mStaIfaceHal.isVerboseLoggingEnabled()) {
                Log.e(TAG, "onDisconnected state=" + mStateBeforeDisconnect
                        + " locallyGenerated=" + locallyGenerated
                        + " reasonCode=" + reasonCode);
            }
            WifiConfiguration curConfiguration =
                    mStaIfaceHal.getCurrentNetworkLocalConfig(mIfaceName);
            if (curConfiguration != null) {
                // In case of PSK networks the disconnection event in the middle of key exchange
                // happens due to PSK mismatch. But filter out the de-authentication/disassociation
                // frame from AP with known reason codes which are not related to PSK mismatch from
                // reporting wrong password error.
                if (mStateBeforeDisconnect == State.FOURWAY_HANDSHAKE
                        && (WifiConfigurationUtil.isConfigForPskNetwork(curConfiguration)
                                || WifiConfigurationUtil.isConfigForWapiPskNetwork(
                                        curConfiguration))
                        && (!locallyGenerated
                                || (reasonCode != ReasonCode.IE_IN_4WAY_DIFFERS
                                        && reasonCode != ReasonCode.DISASSOC_AP_BUSY))) {
                    mWifiMonitor.broadcastAuthenticationFailureEvent(
                            mIfaceName, WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD, -1,
                            mCurrentSsid, MacAddress.fromBytes(bssid));
                } else if (mStateBeforeDisconnect == State.ASSOCIATED
                        && WifiConfigurationUtil.isConfigForEnterpriseNetwork(curConfiguration)) {
                    mWifiMonitor.broadcastAuthenticationFailureEvent(
                            mIfaceName, WifiManager.ERROR_AUTH_FAILURE_EAP_FAILURE, -1,
                            mCurrentSsid, MacAddress.fromBytes(bssid));
                }
            }
            mWifiMonitor.broadcastNetworkDisconnectionEvent(
                    mIfaceName, locallyGenerated, halToFrameworkReasonCode(reasonCode),
                    mCurrentSsid, NativeUtil.macAddressFromByteArray(bssid));
        }
    }

    private void handleAssocRejectEvent(AssocRejectEventInfo assocRejectInfo) {
        boolean isWrongPwd = false;
        WifiConfiguration curConfiguration =
                mStaIfaceHal.getCurrentNetworkLocalConfig(mIfaceName);
        if (curConfiguration != null && !assocRejectInfo.timedOut) {
            int statusCode = assocRejectInfo.statusCode;
            Log.d(TAG, "flush PMK cache due to association rejection for config id "
                    + curConfiguration.networkId + ".");
            mStaIfaceHal.removePmkCacheEntry(curConfiguration.networkId);
            // Network Selection status is guaranteed to be initialized.
            SecurityParams params = curConfiguration.getNetworkSelectionStatus()
                    .getCandidateSecurityParams();
            if (params != null) {
                if (params.getSecurityType() == WifiConfiguration.SECURITY_TYPE_WEP
                        && statusCode == SupplicantStaIfaceHal.StaIfaceStatusCode.CHALLENGE_FAIL) {
                    mStaIfaceHal.logCallback("WEP incorrect password");
                    isWrongPwd = true;
                } else if (params.getSecurityType() == WifiConfiguration.SECURITY_TYPE_SAE) {
                    // Special handling for WPA3-Personal networks. In SAE networks, the password
                    // authentication is not related to the 4-way handshake. If the password is
                    // incorrect, the HAL will send association rejection, with status code 1
                    // (unspecified failure) or 15 (challenge fail).
                    // As per IEEE80211-2020 specification section - 12.4.7.6, an SAE message that
                    // was not successfully verified is indicated with a status code of 15. This
                    // typically happens when the entered password is wrong. So treat status code
                    // of 15 as incorrect password.
                    // Some implementations also send status code of 1 for incorrect password. But
                    // this is a generic status code and can't be treated as incorrect password all
                    // the time. So treat status code of 1 as incorrect password only if the STA
                    // was not connected to this network before. In this case, we will
                    // send an authentication failure event up.
                    if (statusCode == SupplicantStaIfaceHal.StaIfaceStatusCode.CHALLENGE_FAIL
                            || (statusCode
                            == SupplicantStaIfaceHal.StaIfaceStatusCode.UNSPECIFIED_FAILURE
                            && !curConfiguration.getNetworkSelectionStatus().hasEverConnected())) {
                        mStaIfaceHal.logCallback("SAE incorrect password");
                        isWrongPwd = true;
                    } else {
                        mStaIfaceHal.logCallback("SAE association rejection");
                    }
                }
            }
        }

        if (isWrongPwd) {
            MacAddress bssidAsMacAddress;
            try {
                bssidAsMacAddress = MacAddress.fromString(assocRejectInfo.bssid);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid bssid obtained from supplicant " + assocRejectInfo.bssid);
                bssidAsMacAddress = WifiManager.ALL_ZEROS_MAC_ADDRESS;
            }
            mWifiMonitor.broadcastAuthenticationFailureEvent(
                    mIfaceName, WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD, -1,
                    assocRejectInfo.ssid, bssidAsMacAddress);
        }
        mWifiMonitor.broadcastAssociationRejectionEvent(mIfaceName, assocRejectInfo);
        mStateBeforeDisconnect = State.INACTIVE;
    }

    public void onAssociationRejected(android.hardware.wifi.supplicant.V1_4
            .ISupplicantStaIfaceCallback.AssociationRejectionData assocRejectData) {
        assocRejectData.statusCode = halToFrameworkStatusCode(assocRejectData.statusCode);
        assocRejectData.mboAssocDisallowedReason = halToFrameworkMboAssocDisallowedReasonCode(
                assocRejectData.mboAssocDisallowedReason);
        assocRejectData.ssid = NativeUtil.byteArrayToArrayList(
                mSsidTranslator.getTranslatedSsidForStaIface(
                        WifiSsid.fromBytes(NativeUtil.byteArrayFromArrayList(assocRejectData.ssid)),
                        mIfaceName).getBytes());
        AssocRejectEventInfo assocRejectInfo = new AssocRejectEventInfo(assocRejectData);
        handleAssocRejectEvent(assocRejectInfo);
    }

    @Override
    public void onAssociationRejected(byte[/* 6 */] bssid, int statusCode, boolean timedOut) {
        synchronized (mLock) {
            AssocRejectEventInfo assocRejectInfo = new AssocRejectEventInfo(
                    mCurrentSsid,
                    NativeUtil.macAddressFromByteArray(bssid),
                    halToFrameworkStatusCode(statusCode), timedOut);
            handleAssocRejectEvent(assocRejectInfo);
        }
    }

    @Override
    public void onAuthenticationTimeout(byte[/* 6 */] bssid) {
        synchronized (mLock) {
            mStaIfaceHal.logCallback("onAuthenticationTimeout");
            WifiConfiguration curConfiguration =
                    mStaIfaceHal.getCurrentNetworkLocalConfig(mIfaceName);
            if (curConfiguration != null
                    && (WifiConfigurationUtil.isConfigForPskNetwork(curConfiguration)
                    || WifiConfigurationUtil.isConfigForWapiPskNetwork(curConfiguration))
                    && !curConfiguration.getNetworkSelectionStatus().hasEverConnected()
                    && mStateBeforeDisconnect == State.FOURWAY_HANDSHAKE) {
                // Some AP implementations doesn't send de-authentication or dis-association
                // frame after EAPOL failure. They keep retry EAPOL M1 frames. This leads to
                // authentication timeout in supplicant. If this network was not connected before,
                // the authentication timeout may be due to wrong password.
                Log.d(TAG, "EAPOL H/S failed in first connect attempt. Possibly a wrong password");
                mWifiMonitor.broadcastAuthenticationFailureEvent(
                        mIfaceName, WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD, -1,
                        mCurrentSsid, MacAddress.fromBytes(bssid));
            } else {
                mWifiMonitor.broadcastAuthenticationFailureEvent(
                        mIfaceName, WifiManager.ERROR_AUTH_FAILURE_TIMEOUT, -1,
                        mCurrentSsid, MacAddress.fromBytes(bssid));
            }
        }
    }

    @Override
    public void onBssidChanged(byte reason, byte[/* 6 */] bssid) {
        synchronized (mLock) {
            mStaIfaceHal.logCallback("onBssidChanged");
            if (reason == BssidChangeReason.ASSOC_START) {
                mWifiMonitor.broadcastTargetBssidEvent(
                        mIfaceName, NativeUtil.macAddressFromByteArray(bssid));
            } else if (reason == BssidChangeReason.ASSOC_COMPLETE) {
                mWifiMonitor.broadcastAssociatedBssidEvent(
                        mIfaceName, NativeUtil.macAddressFromByteArray(bssid));
            }
        }
    }

    public void onEapFailure(int errorCode) {
        synchronized (mLock) {
            mStaIfaceHal.logCallback("onEapFailure");
            mWifiMonitor.broadcastAuthenticationFailureEvent(
                    mIfaceName, WifiManager.ERROR_AUTH_FAILURE_EAP_FAILURE, errorCode, mCurrentSsid,
                    MacAddress.BROADCAST_ADDRESS);
            mStateBeforeDisconnect = State.INACTIVE;
        }
    }


    @Override
    public void onEapFailure() {
        onEapFailure(-1);
    }

    @Override
    public void onWpsEventSuccess() {
        mStaIfaceHal.logCallback("onWpsEventSuccess");
        synchronized (mLock) {
            mWifiMonitor.broadcastWpsSuccessEvent(mIfaceName);
        }
    }

    @Override
    public void onWpsEventFail(byte[/* 6 */] bssid, short configError, short errorInd) {
        synchronized (mLock) {
            mStaIfaceHal.logCallback("onWpsEventFail");
            if (configError == WpsConfigError.MSG_TIMEOUT
                    && errorInd == WpsErrorIndication.NO_ERROR) {
                mWifiMonitor.broadcastWpsTimeoutEvent(mIfaceName);
            } else {
                mWifiMonitor.broadcastWpsFailEvent(mIfaceName, configError, errorInd);
            }
        }
    }

    @Override
    public void onWpsEventPbcOverlap() {
        synchronized (mLock) {
            mStaIfaceHal.logCallback("onWpsEventPbcOverlap");
            mWifiMonitor.broadcastWpsOverlapEvent(mIfaceName);
        }
    }

    @Override
    public void onExtRadioWorkStart(int id) {
        synchronized (mLock) {
            mStaIfaceHal.logCallback("onExtRadioWorkStart");
        }
    }

    @Override
    public void onExtRadioWorkTimeout(int id) {
        synchronized (mLock) {
            mStaIfaceHal.logCallback("onExtRadioWorkTimeout");
        }
    }

    private byte halToFrameworkMboAssocDisallowedReasonCode(byte reasonCode) {
        switch (reasonCode) {
            case MboAssocDisallowedReasonCode.RESERVED:
                return SupplicantStaIfaceHal.MboAssocDisallowedReasonCode.RESERVED;
            case MboAssocDisallowedReasonCode.UNSPECIFIED:
                return SupplicantStaIfaceHal.MboAssocDisallowedReasonCode.UNSPECIFIED;
            case MboAssocDisallowedReasonCode.MAX_NUM_STA_ASSOCIATED:
                return SupplicantStaIfaceHal.MboAssocDisallowedReasonCode.MAX_NUM_STA_ASSOCIATED;
            case MboAssocDisallowedReasonCode.AIR_INTERFACE_OVERLOADED:
                return SupplicantStaIfaceHal.MboAssocDisallowedReasonCode.AIR_INTERFACE_OVERLOADED;
            case MboAssocDisallowedReasonCode.AUTH_SERVER_OVERLOADED:
                return SupplicantStaIfaceHal.MboAssocDisallowedReasonCode.AUTH_SERVER_OVERLOADED;
            case MboAssocDisallowedReasonCode.INSUFFICIENT_RSSI:
                return SupplicantStaIfaceHal.MboAssocDisallowedReasonCode.INSUFFICIENT_RSSI;
            default:
                Log.e(TAG, "Invalid MboAssocDisallowedReasonCode received");
                return -1;
        }
    }

    private int halToFrameworkReasonCode(int reason) {
        switch (reason) {
            case ReasonCode.UNSPECIFIED:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.UNSPECIFIED;
            case ReasonCode.PREV_AUTH_NOT_VALID:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.PREV_AUTH_NOT_VALID;
            case ReasonCode.DEAUTH_LEAVING:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.DEAUTH_LEAVING;
            case ReasonCode.DISASSOC_DUE_TO_INACTIVITY:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.DISASSOC_DUE_TO_INACTIVITY;
            case ReasonCode.DISASSOC_AP_BUSY:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.DISASSOC_AP_BUSY;
            case ReasonCode.CLASS2_FRAME_FROM_NONAUTH_STA:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.CLASS2_FRAME_FROM_NONAUTH_STA;
            case ReasonCode.CLASS3_FRAME_FROM_NONASSOC_STA:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.CLASS3_FRAME_FROM_NONASSOC_STA;
            case ReasonCode.DISASSOC_STA_HAS_LEFT:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.DISASSOC_STA_HAS_LEFT;
            case ReasonCode.STA_REQ_ASSOC_WITHOUT_AUTH:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.STA_REQ_ASSOC_WITHOUT_AUTH;
            case ReasonCode.PWR_CAPABILITY_NOT_VALID:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.PWR_CAPABILITY_NOT_VALID;
            case ReasonCode.SUPPORTED_CHANNEL_NOT_VALID:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.SUPPORTED_CHANNEL_NOT_VALID;
            case ReasonCode.BSS_TRANSITION_DISASSOC:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.BSS_TRANSITION_DISASSOC;
            case ReasonCode.INVALID_IE:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.INVALID_IE;
            case ReasonCode.MICHAEL_MIC_FAILURE:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MICHAEL_MIC_FAILURE;
            case ReasonCode.FOURWAY_HANDSHAKE_TIMEOUT:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.FOURWAY_HANDSHAKE_TIMEOUT;
            case ReasonCode.GROUP_KEY_UPDATE_TIMEOUT:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.GROUP_KEY_UPDATE_TIMEOUT;
            case ReasonCode.IE_IN_4WAY_DIFFERS:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.IE_IN_4WAY_DIFFERS;
            case ReasonCode.GROUP_CIPHER_NOT_VALID:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.GROUP_CIPHER_NOT_VALID;
            case ReasonCode.PAIRWISE_CIPHER_NOT_VALID:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.PAIRWISE_CIPHER_NOT_VALID;
            case ReasonCode.AKMP_NOT_VALID:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.AKMP_NOT_VALID;
            case ReasonCode.UNSUPPORTED_RSN_IE_VERSION:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.UNSUPPORTED_RSN_IE_VERSION;
            case ReasonCode.INVALID_RSN_IE_CAPAB:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.INVALID_RSN_IE_CAPAB;
            case ReasonCode.IEEE_802_1X_AUTH_FAILED:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.IEEE_802_1X_AUTH_FAILED;
            case ReasonCode.CIPHER_SUITE_REJECTED:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.CIPHER_SUITE_REJECTED;
            case ReasonCode.TDLS_TEARDOWN_UNREACHABLE:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.TDLS_TEARDOWN_UNREACHABLE;
            case ReasonCode.TDLS_TEARDOWN_UNSPECIFIED:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.TDLS_TEARDOWN_UNSPECIFIED;
            case ReasonCode.SSP_REQUESTED_DISASSOC:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.SSP_REQUESTED_DISASSOC;
            case ReasonCode.NO_SSP_ROAMING_AGREEMENT:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.NO_SSP_ROAMING_AGREEMENT;
            case ReasonCode.BAD_CIPHER_OR_AKM:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.BAD_CIPHER_OR_AKM;
            case ReasonCode.NOT_AUTHORIZED_THIS_LOCATION:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.NOT_AUTHORIZED_THIS_LOCATION;
            case ReasonCode.SERVICE_CHANGE_PRECLUDES_TS:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.SERVICE_CHANGE_PRECLUDES_TS;
            case ReasonCode.UNSPECIFIED_QOS_REASON:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.UNSPECIFIED_QOS_REASON;
            case ReasonCode.NOT_ENOUGH_BANDWIDTH:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.NOT_ENOUGH_BANDWIDTH;
            case ReasonCode.DISASSOC_LOW_ACK:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.DISASSOC_LOW_ACK;
            case ReasonCode.EXCEEDED_TXOP:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.EXCEEDED_TXOP;
            case ReasonCode.STA_LEAVING:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.STA_LEAVING;
            case ReasonCode.END_TS_BA_DLS:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.END_TS_BA_DLS;
            case ReasonCode.UNKNOWN_TS_BA:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.UNKNOWN_TS_BA;
            case ReasonCode.TIMEOUT:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.TIMEOUT;
            case ReasonCode.PEERKEY_MISMATCH:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.PEERKEY_MISMATCH;
            case ReasonCode.AUTHORIZED_ACCESS_LIMIT_REACHED:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.AUTHORIZED_ACCESS_LIMIT_REACHED;
            case ReasonCode.EXTERNAL_SERVICE_REQUIREMENTS:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.EXTERNAL_SERVICE_REQUIREMENTS;
            case ReasonCode.INVALID_FT_ACTION_FRAME_COUNT:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.INVALID_FT_ACTION_FRAME_COUNT;
            case ReasonCode.INVALID_PMKID:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.INVALID_PMKID;
            case ReasonCode.INVALID_MDE:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.INVALID_MDE;
            case ReasonCode.INVALID_FTE:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.INVALID_FTE;
            case ReasonCode.MESH_PEERING_CANCELLED:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_PEERING_CANCELLED;
            case ReasonCode.MESH_MAX_PEERS:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_MAX_PEERS;
            case ReasonCode.MESH_CONFIG_POLICY_VIOLATION:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_CONFIG_POLICY_VIOLATION;
            case ReasonCode.MESH_CLOSE_RCVD:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_CLOSE_RCVD;
            case ReasonCode.MESH_MAX_RETRIES:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_MAX_RETRIES;
            case ReasonCode.MESH_CONFIRM_TIMEOUT:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_CONFIRM_TIMEOUT;
            case ReasonCode.MESH_INVALID_GTK:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_INVALID_GTK;
            case ReasonCode.MESH_INCONSISTENT_PARAMS:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_INCONSISTENT_PARAMS;
            case ReasonCode.MESH_INVALID_SECURITY_CAP:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_INVALID_SECURITY_CAP;
            case ReasonCode.MESH_PATH_ERROR_NO_PROXY_INFO:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_PATH_ERROR_NO_PROXY_INFO;
            case ReasonCode.MESH_PATH_ERROR_NO_FORWARDING_INFO:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_PATH_ERROR_NO_FORWARDING_INFO;
            case ReasonCode.MESH_PATH_ERROR_DEST_UNREACHABLE:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_PATH_ERROR_DEST_UNREACHABLE;
            case ReasonCode.MAC_ADDRESS_ALREADY_EXISTS_IN_MBSS:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MAC_ADDRESS_ALREADY_EXISTS_IN_MBSS;
            case ReasonCode.MESH_CHANNEL_SWITCH_REGULATORY_REQ:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_CHANNEL_SWITCH_REGULATORY_REQ;
            case ReasonCode.MESH_CHANNEL_SWITCH_UNSPECIFIED:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_CHANNEL_SWITCH_UNSPECIFIED;
            default:
                Log.e(TAG, "Invalid reason code received");
                return -1;
        }
    }

    protected static int halToFrameworkStatusCode(int reason) {
        switch (reason) {
            case StatusCode.SUCCESS:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.SUCCESS;
            case StatusCode.UNSPECIFIED_FAILURE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.UNSPECIFIED_FAILURE;
            case StatusCode.TDLS_WAKEUP_ALTERNATE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.TDLS_WAKEUP_ALTERNATE;
            case StatusCode.TDLS_WAKEUP_REJECT:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.TDLS_WAKEUP_REJECT;
            case StatusCode.SECURITY_DISABLED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.SECURITY_DISABLED;
            case StatusCode.UNACCEPTABLE_LIFETIME:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.UNACCEPTABLE_LIFETIME;
            case StatusCode.NOT_IN_SAME_BSS:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.NOT_IN_SAME_BSS;
            case StatusCode.CAPS_UNSUPPORTED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.CAPS_UNSUPPORTED;
            case StatusCode.REASSOC_NO_ASSOC:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REASSOC_NO_ASSOC;
            case StatusCode.ASSOC_DENIED_UNSPEC:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ASSOC_DENIED_UNSPEC;
            case StatusCode.NOT_SUPPORTED_AUTH_ALG:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.NOT_SUPPORTED_AUTH_ALG;
            case StatusCode.UNKNOWN_AUTH_TRANSACTION:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.UNKNOWN_AUTH_TRANSACTION;
            case StatusCode.CHALLENGE_FAIL:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.CHALLENGE_FAIL;
            case StatusCode.AUTH_TIMEOUT:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.AUTH_TIMEOUT;
            case StatusCode.AP_UNABLE_TO_HANDLE_NEW_STA:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.AP_UNABLE_TO_HANDLE_NEW_STA;
            case StatusCode.ASSOC_DENIED_RATES:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ASSOC_DENIED_RATES;
            case StatusCode.ASSOC_DENIED_NOSHORT:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ASSOC_DENIED_NOSHORT;
            case StatusCode.SPEC_MGMT_REQUIRED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.SPEC_MGMT_REQUIRED;
            case StatusCode.PWR_CAPABILITY_NOT_VALID:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.PWR_CAPABILITY_NOT_VALID;
            case StatusCode.SUPPORTED_CHANNEL_NOT_VALID:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.SUPPORTED_CHANNEL_NOT_VALID;
            case StatusCode.ASSOC_DENIED_NO_SHORT_SLOT_TIME:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ASSOC_DENIED_NO_SHORT_SLOT_TIME;
            case StatusCode.ASSOC_DENIED_NO_HT:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ASSOC_DENIED_NO_HT;
            case StatusCode.R0KH_UNREACHABLE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.R0KH_UNREACHABLE;
            case StatusCode.ASSOC_DENIED_NO_PCO:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ASSOC_DENIED_NO_PCO;
            case StatusCode.ASSOC_REJECTED_TEMPORARILY:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ASSOC_REJECTED_TEMPORARILY;
            case StatusCode.ROBUST_MGMT_FRAME_POLICY_VIOLATION:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ROBUST_MGMT_FRAME_POLICY_VIOLATION;
            case StatusCode.UNSPECIFIED_QOS_FAILURE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.UNSPECIFIED_QOS_FAILURE;
            case StatusCode.DENIED_INSUFFICIENT_BANDWIDTH:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.DENIED_INSUFFICIENT_BANDWIDTH;
            case StatusCode.DENIED_POOR_CHANNEL_CONDITIONS:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.DENIED_POOR_CHANNEL_CONDITIONS;
            case StatusCode.DENIED_QOS_NOT_SUPPORTED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.DENIED_QOS_NOT_SUPPORTED;
            case StatusCode.REQUEST_DECLINED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REQUEST_DECLINED;
            case StatusCode.INVALID_PARAMETERS:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.INVALID_PARAMETERS;
            case StatusCode.REJECTED_WITH_SUGGESTED_CHANGES:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REJECTED_WITH_SUGGESTED_CHANGES;
            case StatusCode.INVALID_IE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.INVALID_IE;
            case StatusCode.GROUP_CIPHER_NOT_VALID:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.GROUP_CIPHER_NOT_VALID;
            case StatusCode.PAIRWISE_CIPHER_NOT_VALID:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.PAIRWISE_CIPHER_NOT_VALID;
            case StatusCode.AKMP_NOT_VALID:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.AKMP_NOT_VALID;
            case StatusCode.UNSUPPORTED_RSN_IE_VERSION:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.UNSUPPORTED_RSN_IE_VERSION;
            case StatusCode.INVALID_RSN_IE_CAPAB:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.INVALID_RSN_IE_CAPAB;
            case StatusCode.CIPHER_REJECTED_PER_POLICY:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.CIPHER_REJECTED_PER_POLICY;
            case StatusCode.TS_NOT_CREATED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.TS_NOT_CREATED;
            case StatusCode.DIRECT_LINK_NOT_ALLOWED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.DIRECT_LINK_NOT_ALLOWED;
            case StatusCode.DEST_STA_NOT_PRESENT:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.DEST_STA_NOT_PRESENT;
            case StatusCode.DEST_STA_NOT_QOS_STA:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.DEST_STA_NOT_QOS_STA;
            case StatusCode.ASSOC_DENIED_LISTEN_INT_TOO_LARGE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ASSOC_DENIED_LISTEN_INT_TOO_LARGE;
            case StatusCode.INVALID_FT_ACTION_FRAME_COUNT:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.INVALID_FT_ACTION_FRAME_COUNT;
            case StatusCode.INVALID_PMKID:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.INVALID_PMKID;
            case StatusCode.INVALID_MDIE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.INVALID_MDIE;
            case StatusCode.INVALID_FTIE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.INVALID_FTIE;
            case StatusCode.REQUESTED_TCLAS_NOT_SUPPORTED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REQUESTED_TCLAS_NOT_SUPPORTED;
            case StatusCode.INSUFFICIENT_TCLAS_PROCESSING_RESOURCES:
                return SupplicantStaIfaceHal.StaIfaceStatusCode
                        .INSUFFICIENT_TCLAS_PROCESSING_RESOURCES;
            case StatusCode.TRY_ANOTHER_BSS:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.TRY_ANOTHER_BSS;
            case StatusCode.GAS_ADV_PROTO_NOT_SUPPORTED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.GAS_ADV_PROTO_NOT_SUPPORTED;
            case StatusCode.NO_OUTSTANDING_GAS_REQ:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.NO_OUTSTANDING_GAS_REQ;
            case StatusCode.GAS_RESP_NOT_RECEIVED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.GAS_RESP_NOT_RECEIVED;
            case StatusCode.STA_TIMED_OUT_WAITING_FOR_GAS_RESP:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.STA_TIMED_OUT_WAITING_FOR_GAS_RESP;
            case StatusCode.GAS_RESP_LARGER_THAN_LIMIT:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.GAS_RESP_LARGER_THAN_LIMIT;
            case StatusCode.REQ_REFUSED_HOME:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REQ_REFUSED_HOME;
            case StatusCode.ADV_SRV_UNREACHABLE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ADV_SRV_UNREACHABLE;
            case StatusCode.REQ_REFUSED_SSPN:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REQ_REFUSED_SSPN;
            case StatusCode.REQ_REFUSED_UNAUTH_ACCESS:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REQ_REFUSED_UNAUTH_ACCESS;
            case StatusCode.INVALID_RSNIE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.INVALID_RSNIE;
            case StatusCode.U_APSD_COEX_NOT_SUPPORTED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.U_APSD_COEX_NOT_SUPPORTED;
            case StatusCode.U_APSD_COEX_MODE_NOT_SUPPORTED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.U_APSD_COEX_MODE_NOT_SUPPORTED;
            case StatusCode.BAD_INTERVAL_WITH_U_APSD_COEX:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.BAD_INTERVAL_WITH_U_APSD_COEX;
            case StatusCode.ANTI_CLOGGING_TOKEN_REQ:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ANTI_CLOGGING_TOKEN_REQ;
            case StatusCode.FINITE_CYCLIC_GROUP_NOT_SUPPORTED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.FINITE_CYCLIC_GROUP_NOT_SUPPORTED;
            case StatusCode.CANNOT_FIND_ALT_TBTT:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.CANNOT_FIND_ALT_TBTT;
            case StatusCode.TRANSMISSION_FAILURE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.TRANSMISSION_FAILURE;
            case StatusCode.REQ_TCLAS_NOT_SUPPORTED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REQ_TCLAS_NOT_SUPPORTED;
            case StatusCode.TCLAS_RESOURCES_EXCHAUSTED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.TCLAS_RESOURCES_EXCHAUSTED;
            case StatusCode.REJECTED_WITH_SUGGESTED_BSS_TRANSITION:
                return SupplicantStaIfaceHal.StaIfaceStatusCode
                        .REJECTED_WITH_SUGGESTED_BSS_TRANSITION;
            case StatusCode.REJECT_WITH_SCHEDULE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REJECT_WITH_SCHEDULE;
            case StatusCode.REJECT_NO_WAKEUP_SPECIFIED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REJECT_NO_WAKEUP_SPECIFIED;
            case StatusCode.SUCCESS_POWER_SAVE_MODE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.SUCCESS_POWER_SAVE_MODE;
            case StatusCode.PENDING_ADMITTING_FST_SESSION:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.PENDING_ADMITTING_FST_SESSION;
            case StatusCode.PERFORMING_FST_NOW:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.PERFORMING_FST_NOW;
            case StatusCode.PENDING_GAP_IN_BA_WINDOW:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.PENDING_GAP_IN_BA_WINDOW;
            case StatusCode.REJECT_U_PID_SETTING:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REJECT_U_PID_SETTING;
            case StatusCode.REFUSED_EXTERNAL_REASON:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REFUSED_EXTERNAL_REASON;
            case StatusCode.REFUSED_AP_OUT_OF_MEMORY:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REFUSED_AP_OUT_OF_MEMORY;
            case StatusCode.REJECTED_EMERGENCY_SERVICE_NOT_SUPPORTED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode
                        .REJECTED_EMERGENCY_SERVICE_NOT_SUPPORTED;
            case StatusCode.QUERY_RESP_OUTSTANDING:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.QUERY_RESP_OUTSTANDING;
            case StatusCode.REJECT_DSE_BAND:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REJECT_DSE_BAND;
            case StatusCode.TCLAS_PROCESSING_TERMINATED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.TCLAS_PROCESSING_TERMINATED;
            case StatusCode.TS_SCHEDULE_CONFLICT:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.TS_SCHEDULE_CONFLICT;
            case StatusCode.DENIED_WITH_SUGGESTED_BAND_AND_CHANNEL:
                return SupplicantStaIfaceHal.StaIfaceStatusCode
                        .DENIED_WITH_SUGGESTED_BAND_AND_CHANNEL;
            case StatusCode.MCCAOP_RESERVATION_CONFLICT:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.MCCAOP_RESERVATION_CONFLICT;
            case StatusCode.MAF_LIMIT_EXCEEDED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.MAF_LIMIT_EXCEEDED;
            case StatusCode.MCCA_TRACK_LIMIT_EXCEEDED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.MCCA_TRACK_LIMIT_EXCEEDED;
            case StatusCode.DENIED_DUE_TO_SPECTRUM_MANAGEMENT:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.DENIED_DUE_TO_SPECTRUM_MANAGEMENT;
            case StatusCode.ASSOC_DENIED_NO_VHT:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ASSOC_DENIED_NO_VHT;
            case StatusCode.ENABLEMENT_DENIED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ENABLEMENT_DENIED;
            case StatusCode.RESTRICTION_FROM_AUTHORIZED_GDB:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.RESTRICTION_FROM_AUTHORIZED_GDB;
            case StatusCode.AUTHORIZATION_DEENABLED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.AUTHORIZATION_DEENABLED;
            case StatusCode.FILS_AUTHENTICATION_FAILURE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.FILS_AUTHENTICATION_FAILURE;
            case StatusCode.UNKNOWN_AUTHENTICATION_SERVER:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.UNKNOWN_AUTHENTICATION_SERVER;
            default:
                Log.e(TAG, "Invalid status code received");
                return -1;
        }
    }
}
