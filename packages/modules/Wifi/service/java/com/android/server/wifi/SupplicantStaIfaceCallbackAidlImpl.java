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
import android.content.Context;
import android.hardware.wifi.supplicant.AnqpData;
import android.hardware.wifi.supplicant.AssociationRejectionData;
import android.hardware.wifi.supplicant.AuxiliarySupplicantEventCode;
import android.hardware.wifi.supplicant.BssTmData;
import android.hardware.wifi.supplicant.BssTmDataFlagsMask;
import android.hardware.wifi.supplicant.BssTmStatusCode;
import android.hardware.wifi.supplicant.BssidChangeReason;
import android.hardware.wifi.supplicant.DppAkm;
import android.hardware.wifi.supplicant.DppConfigurationData;
import android.hardware.wifi.supplicant.DppConnectionKeys;
import android.hardware.wifi.supplicant.DppEventType;
import android.hardware.wifi.supplicant.DppFailureCode;
import android.hardware.wifi.supplicant.DppProgressCode;
import android.hardware.wifi.supplicant.Hs20AnqpData;
import android.hardware.wifi.supplicant.ISupplicantStaIfaceCallback;
import android.hardware.wifi.supplicant.MboAssocDisallowedReasonCode;
import android.hardware.wifi.supplicant.MboCellularDataConnectionPrefValue;
import android.hardware.wifi.supplicant.MboTransitionReasonCode;
import android.hardware.wifi.supplicant.PmkSaCacheData;
import android.hardware.wifi.supplicant.QosPolicyData;
import android.hardware.wifi.supplicant.QosPolicyScsResponseStatus;
import android.hardware.wifi.supplicant.QosPolicyScsResponseStatusCode;
import android.hardware.wifi.supplicant.StaIfaceCallbackState;
import android.hardware.wifi.supplicant.StaIfaceReasonCode;
import android.hardware.wifi.supplicant.StaIfaceStatusCode;
import android.hardware.wifi.supplicant.SupplicantStateChangeData;
import android.hardware.wifi.supplicant.WpsConfigError;
import android.hardware.wifi.supplicant.WpsErrorIndication;
import android.net.MacAddress;
import android.net.wifi.SecurityParams;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiSsid;
import android.os.Process;
import android.util.Log;

import com.android.server.wifi.SupplicantStaIfaceHal.QosPolicyRequest;
import com.android.server.wifi.SupplicantStaIfaceHal.SupplicantEventCode;
import com.android.server.wifi.hotspot2.AnqpEvent;
import com.android.server.wifi.hotspot2.IconEvent;
import com.android.server.wifi.hotspot2.WnmData;
import com.android.server.wifi.hotspot2.anqp.ANQPElement;
import com.android.server.wifi.hotspot2.anqp.ANQPParser;
import com.android.server.wifi.hotspot2.anqp.Constants;
import com.android.server.wifi.util.HalAidlUtil;
import com.android.server.wifi.util.NativeUtil;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class SupplicantStaIfaceCallbackAidlImpl extends ISupplicantStaIfaceCallback.Stub {
    private static final String TAG = "SupplicantStaIfaceCallbackAidlImpl";
    private final SupplicantStaIfaceHalAidlImpl mStaIfaceHal;
    private final String mIfaceName;
    private final Context mContext;
    private final WifiMonitor mWifiMonitor;
    private final SsidTranslator mSsidTranslator;
    private final Object mLock;
    // Used to help check for PSK password mismatch & EAP connection failure.
    private int mStateBeforeDisconnect = StaIfaceCallbackState.INACTIVE;
    // Current SSID in WifiSsid.toString() format. May be translated to UTF-8 if SSID translation
    // is enabled.
    private String mCurrentSsid = null;

    SupplicantStaIfaceCallbackAidlImpl(@NonNull SupplicantStaIfaceHalAidlImpl staIfaceHal,
            @NonNull String ifaceName, @NonNull Object lock,
            @NonNull Context context, @NonNull WifiMonitor wifiMonitor,
            @NonNull SsidTranslator ssidTranslator) {
        mStaIfaceHal = staIfaceHal;
        mIfaceName = ifaceName;
        mLock = lock;
        mContext = context;
        mWifiMonitor = wifiMonitor;
        mSsidTranslator = ssidTranslator;
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
            mStateBeforeDisconnect = StaIfaceCallbackState.INACTIVE;
        }
    }

    /**
     * Converts the supplicant state received from AIDL to the equivalent framework state.
     */
    protected static SupplicantState supplicantAidlStateToFrameworkState(int state) {
        switch (state) {
            case StaIfaceCallbackState.DISCONNECTED:
                return SupplicantState.DISCONNECTED;
            case StaIfaceCallbackState.IFACE_DISABLED:
                return SupplicantState.INTERFACE_DISABLED;
            case StaIfaceCallbackState.INACTIVE:
                return SupplicantState.INACTIVE;
            case StaIfaceCallbackState.SCANNING:
                return SupplicantState.SCANNING;
            case StaIfaceCallbackState.AUTHENTICATING:
                return SupplicantState.AUTHENTICATING;
            case StaIfaceCallbackState.ASSOCIATING:
                return SupplicantState.ASSOCIATING;
            case StaIfaceCallbackState.ASSOCIATED:
                return SupplicantState.ASSOCIATED;
            case StaIfaceCallbackState.FOURWAY_HANDSHAKE:
                return SupplicantState.FOUR_WAY_HANDSHAKE;
            case StaIfaceCallbackState.GROUP_HANDSHAKE:
                return SupplicantState.GROUP_HANDSHAKE;
            case StaIfaceCallbackState.COMPLETED:
                return SupplicantState.COMPLETED;
            default:
                throw new IllegalArgumentException("Invalid state: " + state);
        }
    }

    @Override
    public void onStateChanged(int newState, byte[/* 6 */] bssid, int id,
            byte[] ssid, boolean filsHlpSent) {
        handleSupplicantStateChangedEvent(newState, bssid, id, ssid, filsHlpSent, 0, 0);
    }

    @Override
    public void onSupplicantStateChanged(SupplicantStateChangeData stateChangeData) {
        handleSupplicantStateChangedEvent(stateChangeData.newState, stateChangeData.bssid,
                stateChangeData.id, stateChangeData.ssid, stateChangeData.filsHlpSent,
                stateChangeData.keyMgmtMask, stateChangeData.frequencyMhz);
    }

    private void handleSupplicantStateChangedEvent(int newState, byte[/* 6 */] bssid, int id,
            byte[] ssid, boolean filsHlpSent, int supplicantKeyMgmtMask, int frequencyMhz) {
        synchronized (mLock) {
            SupplicantState newSupplicantState =
                    supplicantAidlStateToFrameworkState(newState);
            WifiSsid wifiSsid = mSsidTranslator.getTranslatedSsidForStaIface(
                    WifiSsid.fromBytes(ssid), mIfaceName);
            String bssidStr = NativeUtil.macAddressFromByteArray(bssid);
            BitSet keyMgmtMask = null;
            if (supplicantKeyMgmtMask != 0) {
                try {
                    keyMgmtMask = HalAidlUtil.supplicantToWifiConfigurationKeyMgmtMask(
                            supplicantKeyMgmtMask);
                } catch (IllegalArgumentException ex) {
                    Log.w(TAG, "Failed convert supplicant key management mask to"
                            + " the framework value: " + ex.toString());
                    keyMgmtMask = null;
                }
            }
            mStaIfaceHal.logCallback("onStateChanged: newState=" + newSupplicantState
                    + ", bssid=" + bssidStr + ", ssid=" + wifiSsid.toString()
                    + ", filsHlpSent=" + filsHlpSent + ", supplicantKeyMgmtMask="
                    + String.format("0x%08X", supplicantKeyMgmtMask)
                    + ", frameworkKeyMgmtMask=" + keyMgmtMask
                    + ", frequencyMhz=" + frequencyMhz);
            if (newState != StaIfaceCallbackState.DISCONNECTED) {
                // onStateChanged(DISCONNECTED) may come before onDisconnected(), so add this
                // cache to track the state before the disconnect.
                mStateBeforeDisconnect = newState;
            }

            if (newState == StaIfaceCallbackState.ASSOCIATING
                    || newState == StaIfaceCallbackState.ASSOCIATED
                    || newState == StaIfaceCallbackState.COMPLETED) {
                mStaIfaceHal.updateOnLinkedNetworkRoaming(mIfaceName, id, false);
            }

            if (newState == StaIfaceCallbackState.COMPLETED) {
                mWifiMonitor.broadcastNetworkConnectionEvent(
                        mIfaceName, mStaIfaceHal.getCurrentNetworkId(mIfaceName), filsHlpSent,
                        wifiSsid, bssidStr, keyMgmtMask);
            } else if (newState == StaIfaceCallbackState.ASSOCIATING) {
                mCurrentSsid = wifiSsid.toString();
            }
            mWifiMonitor.broadcastSupplicantStateChangeEvent(
                    mIfaceName, mStaIfaceHal.getCurrentNetworkId(mIfaceName), wifiSsid,
                    bssidStr, frequencyMhz, newSupplicantState);
        }
    }

    /**
     * Parses the provided payload into an ANQP element.
     *
     * @param infoID  Element type.
     * @param payload Raw payload bytes.
     * @return AnqpElement instance on success, null on failure.
     */
    private ANQPElement parseAnqpElement(Constants.ANQPElementType infoID, byte[] payload) {
        synchronized (mLock) {
            try {
                return Constants.getANQPElementID(infoID) != null
                        ? ANQPParser.parseElement(infoID, ByteBuffer.wrap(payload))
                        : ANQPParser.parseHS20Element(infoID, ByteBuffer.wrap(payload));
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
            Constants.ANQPElementType infoID, byte[] payload) {
        synchronized (mLock) {
            if (payload == null || payload.length == 0) {
                return;
            }
            ANQPElement element = parseAnqpElement(infoID, payload);
            if (element != null) {
                elementsMap.put(infoID, element);
            }
        }
    }

    @Override
    public void onAnqpQueryDone(byte[/* 6 */] bssid, AnqpData data, Hs20AnqpData hs20Data) {
        synchronized (mLock) {
            mStaIfaceHal.logCallback("onAnqpQueryDone");
            Map<Constants.ANQPElementType, ANQPElement> elementsMap = new HashMap<>();
            addAnqpElementToMap(elementsMap, ANQPVenueName, data.venueName);
            addAnqpElementToMap(elementsMap, ANQPRoamingConsortium, data.roamingConsortium);
            addAnqpElementToMap(elementsMap, ANQPIPAddrAvailability, data.ipAddrTypeAvailability);
            addAnqpElementToMap(elementsMap, ANQPNAIRealm, data.naiRealm);
            addAnqpElementToMap(elementsMap, ANQP3GPPNetwork, data.anqp3gppCellularNetwork);
            addAnqpElementToMap(elementsMap, ANQPDomName, data.domainName);
            addAnqpElementToMap(elementsMap, ANQPVenueUrl, data.venueUrl);
            addAnqpElementToMap(elementsMap, HSFriendlyName, hs20Data.operatorFriendlyName);
            addAnqpElementToMap(elementsMap, HSWANMetrics, hs20Data.wanMetrics);
            addAnqpElementToMap(elementsMap, HSConnCapability, hs20Data.connectionCapability);
            addAnqpElementToMap(elementsMap, HSOSUProviders, hs20Data.osuProvidersList);
            mWifiMonitor.broadcastAnqpDoneEvent(
                    mIfaceName, new AnqpEvent(NativeUtil.macAddressToLong(bssid), elementsMap));
        }
    }

    @Override
    public void onHs20IconQueryDone(byte[/* 6 */] bssid, String fileName, byte[] data) {
        synchronized (mLock) {
            mStaIfaceHal.logCallback("onHs20IconQueryDone");
            mWifiMonitor.broadcastIconDoneEvent(
                    mIfaceName,
                    new IconEvent(NativeUtil.macAddressToLong(bssid), fileName, data.length, data));
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
                if (mStateBeforeDisconnect == StaIfaceCallbackState.FOURWAY_HANDSHAKE
                        && (WifiConfigurationUtil.isConfigForPskNetwork(curConfiguration)
                                || WifiConfigurationUtil.isConfigForWapiPskNetwork(
                                        curConfiguration))
                        && (!locallyGenerated
                                || (reasonCode != StaIfaceReasonCode.IE_IN_4WAY_DIFFERS
                                        && reasonCode != StaIfaceReasonCode.DISASSOC_AP_BUSY))) {
                    mWifiMonitor.broadcastAuthenticationFailureEvent(
                            mIfaceName, WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD, -1,
                            mCurrentSsid, MacAddress.fromBytes(bssid));
                } else if (mStateBeforeDisconnect == StaIfaceCallbackState.ASSOCIATED
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
        WifiConfiguration curConfiguration = mStaIfaceHal.getCurrentNetworkLocalConfig(mIfaceName);
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
        mStateBeforeDisconnect = StaIfaceCallbackState.INACTIVE;
    }

    @Override
    public void onAssociationRejected(AssociationRejectionData assocRejectData) {
        synchronized (mLock) {
            mStaIfaceHal.logCallback("onAssociationRejected");
            assocRejectData.statusCode = halToFrameworkStatusCode(assocRejectData.statusCode);
            assocRejectData.mboAssocDisallowedReason = halToFrameworkMboAssocDisallowedReasonCode(
                    assocRejectData.mboAssocDisallowedReason);
            assocRejectData.ssid = mSsidTranslator.getTranslatedSsidForStaIface(
                    WifiSsid.fromBytes(assocRejectData.ssid), mIfaceName).getBytes();
            AssocRejectEventInfo assocRejectInfo = new AssocRejectEventInfo(assocRejectData);
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
                    && mStateBeforeDisconnect == StaIfaceCallbackState.FOURWAY_HANDSHAKE) {
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

    @Override
    public void onEapFailure(byte[/* 6 */] bssid, int errorCode) {
        synchronized (mLock) {
            mStaIfaceHal.logCallback("onEapFailure");
            try {
                mWifiMonitor.broadcastAuthenticationFailureEvent(
                        mIfaceName, WifiManager.ERROR_AUTH_FAILURE_EAP_FAILURE, errorCode,
                        mCurrentSsid, MacAddress.fromBytes(bssid));
            } catch (IllegalArgumentException e) {
                Log.i(TAG, "Invalid bssid received");
            }
        }
    }

    @Override
    public void onWpsEventSuccess() {
        synchronized (mLock) {
            mStaIfaceHal.logCallback("onWpsEventSuccess");
            mWifiMonitor.broadcastWpsSuccessEvent(mIfaceName);
        }
    }

    @Override
    public void onWpsEventFail(byte[/* 6 */] bssid, int configError, int errorInd) {
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

    @Override
    public void onDppSuccessConfigReceived(byte[] ssid, String password,
            byte[] psk, int securityAkm, DppConnectionKeys keys) {
        processDppConfigReceivedEvent(ssid, password, psk, securityAkm, keys,
                false);
    }

    @Override
    public void onDppConfigReceived(DppConfigurationData configData) {
        processDppConfigReceivedEvent(configData.ssid, configData.password, configData.psk,
                configData.securityAkm, configData.dppConnectionKeys,
                configData.connStatusRequested);
    }

    private void processDppConfigReceivedEvent(byte[] ssid, String password,
            byte[] psk, int securityAkm, DppConnectionKeys keys, boolean connStatusRequested) {
        if (mStaIfaceHal.getDppCallback() == null) {
            Log.e(TAG, "onDppSuccessConfigReceived callback is null");
            return;
        }

        WifiConfiguration newWifiConfiguration = new WifiConfiguration();

        // Set up SSID
        WifiSsid wifiSsid = mSsidTranslator.getTranslatedSsid(WifiSsid.fromBytes(ssid));
        newWifiConfiguration.SSID = wifiSsid.toString();

        // Set up password or PSK
        if (password != null) {
            newWifiConfiguration.preSharedKey = "\"" + password + "\"";
        } else if (psk != null) {
            newWifiConfiguration.preSharedKey = Arrays.toString(psk);
        }

        // Set up key management: SAE or PSK or DPP
        if (securityAkm == DppAkm.SAE) {
            newWifiConfiguration.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);
        } else if (securityAkm == DppAkm.PSK_SAE || securityAkm == DppAkm.PSK) {
            newWifiConfiguration.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        } else if (securityAkm == DppAkm.DPP) {
            newWifiConfiguration.setSecurityParams(WifiConfiguration.SECURITY_TYPE_DPP);
        } else {
            // No other AKMs are currently supported
            onDppFailure(DppFailureCode.NOT_SUPPORTED, null, null, null);
            return;
        }

        // Set DPP connection Keys for SECURITY_TYPE_DPP
        if (keys != null && securityAkm == DppAkm.DPP) {
            newWifiConfiguration.setDppConnectionKeys(keys.connector, keys.cSign,
                    keys.netAccessKey);
        }

        // Set up default values
        newWifiConfiguration.creatorName = mContext.getPackageManager()
                .getNameForUid(Process.WIFI_UID);
        newWifiConfiguration.status = WifiConfiguration.Status.ENABLED;

        mStaIfaceHal.getDppCallback().onSuccessConfigReceived(newWifiConfiguration,
                connStatusRequested);
    }

    @Override
    public void onDppSuccessConfigSent() {
        if (mStaIfaceHal.getDppCallback() != null) {
            mStaIfaceHal.getDppCallback().onSuccess(
                    SupplicantStaIfaceHal.DppEventType.CONFIGURATION_SENT);
        } else {
            Log.e(TAG, "onSuccessConfigSent callback is null");
        }
    }

    @Override
    public void onDppConnectionStatusResultSent(int result) {
        if (mStaIfaceHal.getDppCallback() != null) {
            mStaIfaceHal.getDppCallback().onConnectionStatusResultSent(result);
        } else {
            Log.e(TAG, "onConnectionStatusResultSent callback is null");
        }
    }


    @Override
    public void onDppProgress(int code) {
        if (mStaIfaceHal.getDppCallback() != null) {
            mStaIfaceHal.getDppCallback().onProgress(halToFrameworkDppProgressCode(code));
        } else {
            Log.e(TAG, "onDppProgress callback is null");
        }
    }

    @Override
    public void onDppFailure(int code, String ssid, String channelList, char[] bandList) {
        if (mStaIfaceHal.getDppCallback() != null) {
            int[] bandListArray = null;

            // Convert char array to int array
            if (bandList != null) {
                bandListArray = new int[bandList.length];

                for (int i = 0; i < bandList.length; i++) {
                    bandListArray[i] = bandList[i];
                }
            }
            mStaIfaceHal.getDppCallback().onFailure(
                    halToFrameworkDppFailureCode(code), ssid, channelList, bandListArray);
        } else {
            Log.e(TAG, "onDppFailure callback is null");
        }
    }

    @Override
    public void onPmkCacheAdded(long expirationTimeInSec, byte[] serializedEntry) {
        handlePmkSaCacheAddedEvent(null, expirationTimeInSec, serializedEntry);
    }

    @Override
    public void onPmkSaCacheAdded(PmkSaCacheData pmkSaData) {
        handlePmkSaCacheAddedEvent(pmkSaData.bssid, pmkSaData.expirationTimeInSec,
                pmkSaData.serializedEntry);
    }

    private void handlePmkSaCacheAddedEvent(byte[/* 6 */] bssid, long expirationTimeInSec,
            byte[] serializedEntry) {
        WifiConfiguration curConfig = mStaIfaceHal.getCurrentNetworkLocalConfig(mIfaceName);
        if (curConfig == null) {
            return;
        }

        SecurityParams params = curConfig.getNetworkSelectionStatus().getCandidateSecurityParams();
        if (params == null || params.isSecurityType(WifiConfiguration.SECURITY_TYPE_PSK)
                || params.isSecurityType(WifiConfiguration.SECURITY_TYPE_DPP)) {
            return;
        }

        mStaIfaceHal.addPmkCacheEntry(mIfaceName, curConfig.networkId, bssid, expirationTimeInSec,
                NativeUtil.byteArrayToArrayList(serializedEntry));
        mStaIfaceHal.logCallback(
                "handlePmkSaCacheAddedEvent: update pmk cache for config id "
                        + curConfig.networkId + " on " + mIfaceName);
    }

    @Override
    public void onDppSuccess(int code) {
        if (mStaIfaceHal.getDppCallback() != null) {
            mStaIfaceHal.getDppCallback().onSuccess(halToFrameworkDppEventType(code));
        } else {
            Log.e(TAG, "onDppSuccess callback is null");
        }
    }

    private @MboOceConstants.BtmResponseStatus int halToFrameworkBtmResponseStatus(int status) {
        switch (status) {
            case BssTmStatusCode.ACCEPT:
                return MboOceConstants.BTM_RESPONSE_STATUS_ACCEPT;
            case BssTmStatusCode.REJECT_UNSPECIFIED:
                return MboOceConstants.BTM_RESPONSE_STATUS_REJECT_UNSPECIFIED;
            case BssTmStatusCode.REJECT_INSUFFICIENT_BEACON:
                return MboOceConstants.BTM_RESPONSE_STATUS_REJECT_INSUFFICIENT_BEACON;
            case BssTmStatusCode.REJECT_INSUFFICIENT_CAPABITY:
                return MboOceConstants.BTM_RESPONSE_STATUS_REJECT_INSUFFICIENT_CAPABITY;
            case BssTmStatusCode.REJECT_BSS_TERMINATION_UNDESIRED:
                return MboOceConstants.BTM_RESPONSE_STATUS_REJECT_BSS_TERMINATION_UNDESIRED;
            case BssTmStatusCode.REJECT_BSS_TERMINATION_DELAY_REQUEST:
                return MboOceConstants.BTM_RESPONSE_STATUS_REJECT_BSS_TERMINATION_DELAY_REQUEST;
            case BssTmStatusCode.REJECT_STA_CANDIDATE_LIST_PROVIDED:
                return MboOceConstants.BTM_RESPONSE_STATUS_REJECT_STA_CANDIDATE_LIST_PROVIDED;
            case BssTmStatusCode.REJECT_NO_SUITABLE_CANDIDATES:
                return MboOceConstants.BTM_RESPONSE_STATUS_REJECT_NO_SUITABLE_CANDIDATES;
            case BssTmStatusCode.REJECT_LEAVING_ESS:
                return MboOceConstants.BTM_RESPONSE_STATUS_REJECT_LEAVING_ESS;
            default:
                return MboOceConstants.BTM_RESPONSE_STATUS_REJECT_RESERVED;
        }
    }

    private int halToFrameworkBssTmDataFlagsMask(int flags) {
        int tmDataflags = 0;
        if ((flags & BssTmDataFlagsMask.WNM_MODE_PREFERRED_CANDIDATE_LIST_INCLUDED) != 0) {
            tmDataflags |= MboOceConstants.BTM_DATA_FLAG_PREFERRED_CANDIDATE_LIST_INCLUDED;
        }
        if ((flags & BssTmDataFlagsMask.WNM_MODE_ABRIDGED) != 0) {
            tmDataflags |= MboOceConstants.BTM_DATA_FLAG_MODE_ABRIDGED;
        }
        if ((flags & BssTmDataFlagsMask.WNM_MODE_DISASSOCIATION_IMMINENT) != 0) {
            tmDataflags |= MboOceConstants.BTM_DATA_FLAG_DISASSOCIATION_IMMINENT;
        }
        if ((flags & BssTmDataFlagsMask.WNM_MODE_BSS_TERMINATION_INCLUDED) != 0) {
            tmDataflags |= MboOceConstants.BTM_DATA_FLAG_BSS_TERMINATION_INCLUDED;
        }
        if ((flags & BssTmDataFlagsMask.WNM_MODE_ESS_DISASSOCIATION_IMMINENT) != 0) {
            tmDataflags |= MboOceConstants.BTM_DATA_FLAG_ESS_DISASSOCIATION_IMMINENT;
        }
        if ((flags & BssTmDataFlagsMask.MBO_TRANSITION_REASON_CODE_INCLUDED) != 0) {
            tmDataflags |= MboOceConstants.BTM_DATA_FLAG_MBO_TRANSITION_REASON_CODE_INCLUDED;
        }
        if ((flags & BssTmDataFlagsMask.MBO_ASSOC_RETRY_DELAY_INCLUDED) != 0) {
            tmDataflags |= MboOceConstants.BTM_DATA_FLAG_MBO_ASSOC_RETRY_DELAY_INCLUDED;
        }
        if ((flags & BssTmDataFlagsMask.MBO_CELLULAR_DATA_CONNECTION_PREFERENCE_INCLUDED) != 0) {
            tmDataflags |=
                    MboOceConstants.BTM_DATA_FLAG_MBO_CELL_DATA_CONNECTION_PREFERENCE_INCLUDED;
        }
        return tmDataflags;
    }

    private @MboOceConstants.MboTransitionReason int halToFrameworkMboTransitionReason(
            int reason) {
        switch (reason) {
            case MboTransitionReasonCode.UNSPECIFIED:
                return MboOceConstants.MBO_TRANSITION_REASON_UNSPECIFIED;
            case MboTransitionReasonCode.EXCESSIVE_FRAME_LOSS:
                return MboOceConstants.MBO_TRANSITION_REASON_EXCESSIVE_FRAME_LOSS;
            case MboTransitionReasonCode.EXCESSIVE_TRAFFIC_DELAY:
                return MboOceConstants.MBO_TRANSITION_REASON_EXCESSIVE_TRAFFIC_DELAY;
            case MboTransitionReasonCode.INSUFFICIENT_BANDWIDTH:
                return MboOceConstants.MBO_TRANSITION_REASON_INSUFFICIENT_BANDWIDTH;
            case MboTransitionReasonCode.LOAD_BALANCING:
                return MboOceConstants.MBO_TRANSITION_REASON_LOAD_BALANCING;
            case MboTransitionReasonCode.LOW_RSSI:
                return MboOceConstants.MBO_TRANSITION_REASON_LOW_RSSI;
            case MboTransitionReasonCode.RX_EXCESSIVE_RETRIES:
                return MboOceConstants.MBO_TRANSITION_REASON_RX_EXCESSIVE_RETRIES;
            case MboTransitionReasonCode.HIGH_INTERFERENCE:
                return MboOceConstants.MBO_TRANSITION_REASON_HIGH_INTERFERENCE;
            case MboTransitionReasonCode.GRAY_ZONE:
                return MboOceConstants.MBO_TRANSITION_REASON_GRAY_ZONE;
            default:
                return MboOceConstants.MBO_TRANSITION_REASON_RESERVED;
        }
    }

    private @MboOceConstants.MboTransitionReason int
            halToFrameworkMboCellularDataConnectionPreference(int cellPref) {
        switch (cellPref) {
            case MboCellularDataConnectionPrefValue.EXCLUDED:
                return MboOceConstants.MBO_CELLULAR_DATA_CONNECTION_EXCLUDED;
            case MboCellularDataConnectionPrefValue.NOT_PREFERRED:
                return MboOceConstants.MBO_CELLULAR_DATA_CONNECTION_NOT_PREFERRED;
            case MboCellularDataConnectionPrefValue.PREFERRED:
                return MboOceConstants.MBO_CELLULAR_DATA_CONNECTION_PREFERRED;
            default:
                return MboOceConstants.MBO_CELLULAR_DATA_CONNECTION_RESERVED;
        }
    }

    private int halToFrameworkDppEventType(int eventType) {
        switch(eventType) {
            case DppEventType.CONFIGURATION_SENT:
                return SupplicantStaIfaceHal.DppEventType.CONFIGURATION_SENT;
            case DppEventType.CONFIGURATION_APPLIED:
                return SupplicantStaIfaceHal.DppEventType.CONFIGURATION_APPLIED;
            default:
                Log.e(TAG, "Invalid DppEventType received");
                return -1;
        }
    }

    private int halToFrameworkDppProgressCode(int progressCode) {
        switch(progressCode) {
            case DppProgressCode.AUTHENTICATION_SUCCESS:
                return SupplicantStaIfaceHal.DppProgressCode.AUTHENTICATION_SUCCESS;
            case DppProgressCode.RESPONSE_PENDING:
                return SupplicantStaIfaceHal.DppProgressCode.RESPONSE_PENDING;
            case DppProgressCode.CONFIGURATION_SENT_WAITING_RESPONSE:
                return SupplicantStaIfaceHal.DppProgressCode.CONFIGURATION_SENT_WAITING_RESPONSE;
            case DppProgressCode.CONFIGURATION_ACCEPTED:
                return SupplicantStaIfaceHal.DppProgressCode.CONFIGURATION_ACCEPTED;
            default:
                Log.e(TAG, "Invalid DppProgressCode received");
                return -1;
        }
    }

    private int halToFrameworkDppFailureCode(int failureCode) {
        switch(failureCode) {
            case DppFailureCode.INVALID_URI:
                return SupplicantStaIfaceHal.DppFailureCode.INVALID_URI;
            case DppFailureCode.AUTHENTICATION:
                return SupplicantStaIfaceHal.DppFailureCode.AUTHENTICATION;
            case DppFailureCode.NOT_COMPATIBLE:
                return SupplicantStaIfaceHal.DppFailureCode.NOT_COMPATIBLE;
            case DppFailureCode.CONFIGURATION:
                return SupplicantStaIfaceHal.DppFailureCode.CONFIGURATION;
            case DppFailureCode.BUSY:
                return SupplicantStaIfaceHal.DppFailureCode.BUSY;
            case DppFailureCode.TIMEOUT:
                return SupplicantStaIfaceHal.DppFailureCode.TIMEOUT;
            case DppFailureCode.FAILURE:
                return SupplicantStaIfaceHal.DppFailureCode.FAILURE;
            case DppFailureCode.NOT_SUPPORTED:
                return SupplicantStaIfaceHal.DppFailureCode.NOT_SUPPORTED;
            case DppFailureCode.CONFIGURATION_REJECTED:
                return SupplicantStaIfaceHal.DppFailureCode.CONFIGURATION_REJECTED;
            case DppFailureCode.CANNOT_FIND_NETWORK:
                return SupplicantStaIfaceHal.DppFailureCode.CANNOT_FIND_NETWORK;
            case DppFailureCode.ENROLLEE_AUTHENTICATION:
                return SupplicantStaIfaceHal.DppFailureCode.ENROLLEE_AUTHENTICATION;
            case DppFailureCode.URI_GENERATION:
                return SupplicantStaIfaceHal.DppFailureCode.URI_GENERATION;
            default:
                Log.e(TAG, "Invalid DppFailureCode received");
                return -1;
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
            case StaIfaceReasonCode.UNSPECIFIED:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.UNSPECIFIED;
            case StaIfaceReasonCode.PREV_AUTH_NOT_VALID:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.PREV_AUTH_NOT_VALID;
            case StaIfaceReasonCode.DEAUTH_LEAVING:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.DEAUTH_LEAVING;
            case StaIfaceReasonCode.DISASSOC_DUE_TO_INACTIVITY:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.DISASSOC_DUE_TO_INACTIVITY;
            case StaIfaceReasonCode.DISASSOC_AP_BUSY:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.DISASSOC_AP_BUSY;
            case StaIfaceReasonCode.CLASS2_FRAME_FROM_NONAUTH_STA:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.CLASS2_FRAME_FROM_NONAUTH_STA;
            case StaIfaceReasonCode.CLASS3_FRAME_FROM_NONASSOC_STA:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.CLASS3_FRAME_FROM_NONASSOC_STA;
            case StaIfaceReasonCode.DISASSOC_STA_HAS_LEFT:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.DISASSOC_STA_HAS_LEFT;
            case StaIfaceReasonCode.STA_REQ_ASSOC_WITHOUT_AUTH:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.STA_REQ_ASSOC_WITHOUT_AUTH;
            case StaIfaceReasonCode.PWR_CAPABILITY_NOT_VALID:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.PWR_CAPABILITY_NOT_VALID;
            case StaIfaceReasonCode.SUPPORTED_CHANNEL_NOT_VALID:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.SUPPORTED_CHANNEL_NOT_VALID;
            case StaIfaceReasonCode.BSS_TRANSITION_DISASSOC:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.BSS_TRANSITION_DISASSOC;
            case StaIfaceReasonCode.INVALID_IE:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.INVALID_IE;
            case StaIfaceReasonCode.MICHAEL_MIC_FAILURE:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MICHAEL_MIC_FAILURE;
            case StaIfaceReasonCode.FOURWAY_HANDSHAKE_TIMEOUT:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.FOURWAY_HANDSHAKE_TIMEOUT;
            case StaIfaceReasonCode.GROUP_KEY_UPDATE_TIMEOUT:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.GROUP_KEY_UPDATE_TIMEOUT;
            case StaIfaceReasonCode.IE_IN_4WAY_DIFFERS:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.IE_IN_4WAY_DIFFERS;
            case StaIfaceReasonCode.GROUP_CIPHER_NOT_VALID:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.GROUP_CIPHER_NOT_VALID;
            case StaIfaceReasonCode.PAIRWISE_CIPHER_NOT_VALID:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.PAIRWISE_CIPHER_NOT_VALID;
            case StaIfaceReasonCode.AKMP_NOT_VALID:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.AKMP_NOT_VALID;
            case StaIfaceReasonCode.UNSUPPORTED_RSN_IE_VERSION:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.UNSUPPORTED_RSN_IE_VERSION;
            case StaIfaceReasonCode.INVALID_RSN_IE_CAPAB:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.INVALID_RSN_IE_CAPAB;
            case StaIfaceReasonCode.IEEE_802_1X_AUTH_FAILED:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.IEEE_802_1X_AUTH_FAILED;
            case StaIfaceReasonCode.CIPHER_SUITE_REJECTED:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.CIPHER_SUITE_REJECTED;
            case StaIfaceReasonCode.TDLS_TEARDOWN_UNREACHABLE:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.TDLS_TEARDOWN_UNREACHABLE;
            case StaIfaceReasonCode.TDLS_TEARDOWN_UNSPECIFIED:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.TDLS_TEARDOWN_UNSPECIFIED;
            case StaIfaceReasonCode.SSP_REQUESTED_DISASSOC:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.SSP_REQUESTED_DISASSOC;
            case StaIfaceReasonCode.NO_SSP_ROAMING_AGREEMENT:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.NO_SSP_ROAMING_AGREEMENT;
            case StaIfaceReasonCode.BAD_CIPHER_OR_AKM:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.BAD_CIPHER_OR_AKM;
            case StaIfaceReasonCode.NOT_AUTHORIZED_THIS_LOCATION:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.NOT_AUTHORIZED_THIS_LOCATION;
            case StaIfaceReasonCode.SERVICE_CHANGE_PRECLUDES_TS:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.SERVICE_CHANGE_PRECLUDES_TS;
            case StaIfaceReasonCode.UNSPECIFIED_QOS_REASON:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.UNSPECIFIED_QOS_REASON;
            case StaIfaceReasonCode.NOT_ENOUGH_BANDWIDTH:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.NOT_ENOUGH_BANDWIDTH;
            case StaIfaceReasonCode.DISASSOC_LOW_ACK:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.DISASSOC_LOW_ACK;
            case StaIfaceReasonCode.EXCEEDED_TXOP:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.EXCEEDED_TXOP;
            case StaIfaceReasonCode.STA_LEAVING:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.STA_LEAVING;
            case StaIfaceReasonCode.END_TS_BA_DLS:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.END_TS_BA_DLS;
            case StaIfaceReasonCode.UNKNOWN_TS_BA:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.UNKNOWN_TS_BA;
            case StaIfaceReasonCode.TIMEOUT:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.TIMEOUT;
            case StaIfaceReasonCode.PEERKEY_MISMATCH:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.PEERKEY_MISMATCH;
            case StaIfaceReasonCode.AUTHORIZED_ACCESS_LIMIT_REACHED:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.AUTHORIZED_ACCESS_LIMIT_REACHED;
            case StaIfaceReasonCode.EXTERNAL_SERVICE_REQUIREMENTS:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.EXTERNAL_SERVICE_REQUIREMENTS;
            case StaIfaceReasonCode.INVALID_FT_ACTION_FRAME_COUNT:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.INVALID_FT_ACTION_FRAME_COUNT;
            case StaIfaceReasonCode.INVALID_PMKID:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.INVALID_PMKID;
            case StaIfaceReasonCode.INVALID_MDE:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.INVALID_MDE;
            case StaIfaceReasonCode.INVALID_FTE:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.INVALID_FTE;
            case StaIfaceReasonCode.MESH_PEERING_CANCELLED:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_PEERING_CANCELLED;
            case StaIfaceReasonCode.MESH_MAX_PEERS:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_MAX_PEERS;
            case StaIfaceReasonCode.MESH_CONFIG_POLICY_VIOLATION:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_CONFIG_POLICY_VIOLATION;
            case StaIfaceReasonCode.MESH_CLOSE_RCVD:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_CLOSE_RCVD;
            case StaIfaceReasonCode.MESH_MAX_RETRIES:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_MAX_RETRIES;
            case StaIfaceReasonCode.MESH_CONFIRM_TIMEOUT:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_CONFIRM_TIMEOUT;
            case StaIfaceReasonCode.MESH_INVALID_GTK:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_INVALID_GTK;
            case StaIfaceReasonCode.MESH_INCONSISTENT_PARAMS:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_INCONSISTENT_PARAMS;
            case StaIfaceReasonCode.MESH_INVALID_SECURITY_CAP:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_INVALID_SECURITY_CAP;
            case StaIfaceReasonCode.MESH_PATH_ERROR_NO_PROXY_INFO:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_PATH_ERROR_NO_PROXY_INFO;
            case StaIfaceReasonCode.MESH_PATH_ERROR_NO_FORWARDING_INFO:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_PATH_ERROR_NO_FORWARDING_INFO;
            case StaIfaceReasonCode.MESH_PATH_ERROR_DEST_UNREACHABLE:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_PATH_ERROR_DEST_UNREACHABLE;
            case StaIfaceReasonCode.MAC_ADDRESS_ALREADY_EXISTS_IN_MBSS:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MAC_ADDRESS_ALREADY_EXISTS_IN_MBSS;
            case StaIfaceReasonCode.MESH_CHANNEL_SWITCH_REGULATORY_REQ:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_CHANNEL_SWITCH_REGULATORY_REQ;
            case StaIfaceReasonCode.MESH_CHANNEL_SWITCH_UNSPECIFIED:
                return SupplicantStaIfaceHal.StaIfaceReasonCode.MESH_CHANNEL_SWITCH_UNSPECIFIED;
            default:
                Log.e(TAG, "Invalid reason code received");
                return -1;
        }
    }

    protected static int halToFrameworkStatusCode(int reason) {
        switch (reason) {
            case StaIfaceStatusCode.SUCCESS:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.SUCCESS;
            case StaIfaceStatusCode.UNSPECIFIED_FAILURE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.UNSPECIFIED_FAILURE;
            case StaIfaceStatusCode.TDLS_WAKEUP_ALTERNATE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.TDLS_WAKEUP_ALTERNATE;
            case StaIfaceStatusCode.TDLS_WAKEUP_REJECT:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.TDLS_WAKEUP_REJECT;
            case StaIfaceStatusCode.SECURITY_DISABLED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.SECURITY_DISABLED;
            case StaIfaceStatusCode.UNACCEPTABLE_LIFETIME:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.UNACCEPTABLE_LIFETIME;
            case StaIfaceStatusCode.NOT_IN_SAME_BSS:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.NOT_IN_SAME_BSS;
            case StaIfaceStatusCode.CAPS_UNSUPPORTED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.CAPS_UNSUPPORTED;
            case StaIfaceStatusCode.REASSOC_NO_ASSOC:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REASSOC_NO_ASSOC;
            case StaIfaceStatusCode.ASSOC_DENIED_UNSPEC:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ASSOC_DENIED_UNSPEC;
            case StaIfaceStatusCode.NOT_SUPPORTED_AUTH_ALG:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.NOT_SUPPORTED_AUTH_ALG;
            case StaIfaceStatusCode.UNKNOWN_AUTH_TRANSACTION:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.UNKNOWN_AUTH_TRANSACTION;
            case StaIfaceStatusCode.CHALLENGE_FAIL:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.CHALLENGE_FAIL;
            case StaIfaceStatusCode.AUTH_TIMEOUT:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.AUTH_TIMEOUT;
            case StaIfaceStatusCode.AP_UNABLE_TO_HANDLE_NEW_STA:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.AP_UNABLE_TO_HANDLE_NEW_STA;
            case StaIfaceStatusCode.ASSOC_DENIED_RATES:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ASSOC_DENIED_RATES;
            case StaIfaceStatusCode.ASSOC_DENIED_NOSHORT:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ASSOC_DENIED_NOSHORT;
            case StaIfaceStatusCode.SPEC_MGMT_REQUIRED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.SPEC_MGMT_REQUIRED;
            case StaIfaceStatusCode.PWR_CAPABILITY_NOT_VALID:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.PWR_CAPABILITY_NOT_VALID;
            case StaIfaceStatusCode.SUPPORTED_CHANNEL_NOT_VALID:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.SUPPORTED_CHANNEL_NOT_VALID;
            case StaIfaceStatusCode.ASSOC_DENIED_NO_SHORT_SLOT_TIME:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ASSOC_DENIED_NO_SHORT_SLOT_TIME;
            case StaIfaceStatusCode.ASSOC_DENIED_NO_HT:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ASSOC_DENIED_NO_HT;
            case StaIfaceStatusCode.R0KH_UNREACHABLE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.R0KH_UNREACHABLE;
            case StaIfaceStatusCode.ASSOC_DENIED_NO_PCO:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ASSOC_DENIED_NO_PCO;
            case StaIfaceStatusCode.ASSOC_REJECTED_TEMPORARILY:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ASSOC_REJECTED_TEMPORARILY;
            case StaIfaceStatusCode.ROBUST_MGMT_FRAME_POLICY_VIOLATION:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ROBUST_MGMT_FRAME_POLICY_VIOLATION;
            case StaIfaceStatusCode.UNSPECIFIED_QOS_FAILURE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.UNSPECIFIED_QOS_FAILURE;
            case StaIfaceStatusCode.DENIED_INSUFFICIENT_BANDWIDTH:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.DENIED_INSUFFICIENT_BANDWIDTH;
            case StaIfaceStatusCode.DENIED_POOR_CHANNEL_CONDITIONS:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.DENIED_POOR_CHANNEL_CONDITIONS;
            case StaIfaceStatusCode.DENIED_QOS_NOT_SUPPORTED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.DENIED_QOS_NOT_SUPPORTED;
            case StaIfaceStatusCode.REQUEST_DECLINED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REQUEST_DECLINED;
            case StaIfaceStatusCode.INVALID_PARAMETERS:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.INVALID_PARAMETERS;
            case StaIfaceStatusCode.REJECTED_WITH_SUGGESTED_CHANGES:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REJECTED_WITH_SUGGESTED_CHANGES;
            case StaIfaceStatusCode.INVALID_IE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.INVALID_IE;
            case StaIfaceStatusCode.GROUP_CIPHER_NOT_VALID:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.GROUP_CIPHER_NOT_VALID;
            case StaIfaceStatusCode.PAIRWISE_CIPHER_NOT_VALID:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.PAIRWISE_CIPHER_NOT_VALID;
            case StaIfaceStatusCode.AKMP_NOT_VALID:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.AKMP_NOT_VALID;
            case StaIfaceStatusCode.UNSUPPORTED_RSN_IE_VERSION:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.UNSUPPORTED_RSN_IE_VERSION;
            case StaIfaceStatusCode.INVALID_RSN_IE_CAPAB:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.INVALID_RSN_IE_CAPAB;
            case StaIfaceStatusCode.CIPHER_REJECTED_PER_POLICY:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.CIPHER_REJECTED_PER_POLICY;
            case StaIfaceStatusCode.TS_NOT_CREATED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.TS_NOT_CREATED;
            case StaIfaceStatusCode.DIRECT_LINK_NOT_ALLOWED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.DIRECT_LINK_NOT_ALLOWED;
            case StaIfaceStatusCode.DEST_STA_NOT_PRESENT:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.DEST_STA_NOT_PRESENT;
            case StaIfaceStatusCode.DEST_STA_NOT_QOS_STA:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.DEST_STA_NOT_QOS_STA;
            case StaIfaceStatusCode.ASSOC_DENIED_LISTEN_INT_TOO_LARGE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ASSOC_DENIED_LISTEN_INT_TOO_LARGE;
            case StaIfaceStatusCode.INVALID_FT_ACTION_FRAME_COUNT:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.INVALID_FT_ACTION_FRAME_COUNT;
            case StaIfaceStatusCode.INVALID_PMKID:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.INVALID_PMKID;
            case StaIfaceStatusCode.INVALID_MDIE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.INVALID_MDIE;
            case StaIfaceStatusCode.INVALID_FTIE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.INVALID_FTIE;
            case StaIfaceStatusCode.REQUESTED_TCLAS_NOT_SUPPORTED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REQUESTED_TCLAS_NOT_SUPPORTED;
            case StaIfaceStatusCode.INSUFFICIENT_TCLAS_PROCESSING_RESOURCES:
                return SupplicantStaIfaceHal.StaIfaceStatusCode
                        .INSUFFICIENT_TCLAS_PROCESSING_RESOURCES;
            case StaIfaceStatusCode.TRY_ANOTHER_BSS:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.TRY_ANOTHER_BSS;
            case StaIfaceStatusCode.GAS_ADV_PROTO_NOT_SUPPORTED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.GAS_ADV_PROTO_NOT_SUPPORTED;
            case StaIfaceStatusCode.NO_OUTSTANDING_GAS_REQ:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.NO_OUTSTANDING_GAS_REQ;
            case StaIfaceStatusCode.GAS_RESP_NOT_RECEIVED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.GAS_RESP_NOT_RECEIVED;
            case StaIfaceStatusCode.STA_TIMED_OUT_WAITING_FOR_GAS_RESP:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.STA_TIMED_OUT_WAITING_FOR_GAS_RESP;
            case StaIfaceStatusCode.GAS_RESP_LARGER_THAN_LIMIT:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.GAS_RESP_LARGER_THAN_LIMIT;
            case StaIfaceStatusCode.REQ_REFUSED_HOME:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REQ_REFUSED_HOME;
            case StaIfaceStatusCode.ADV_SRV_UNREACHABLE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ADV_SRV_UNREACHABLE;
            case StaIfaceStatusCode.REQ_REFUSED_SSPN:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REQ_REFUSED_SSPN;
            case StaIfaceStatusCode.REQ_REFUSED_UNAUTH_ACCESS:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REQ_REFUSED_UNAUTH_ACCESS;
            case StaIfaceStatusCode.INVALID_RSNIE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.INVALID_RSNIE;
            case StaIfaceStatusCode.U_APSD_COEX_NOT_SUPPORTED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.U_APSD_COEX_NOT_SUPPORTED;
            case StaIfaceStatusCode.U_APSD_COEX_MODE_NOT_SUPPORTED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.U_APSD_COEX_MODE_NOT_SUPPORTED;
            case StaIfaceStatusCode.BAD_INTERVAL_WITH_U_APSD_COEX:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.BAD_INTERVAL_WITH_U_APSD_COEX;
            case StaIfaceStatusCode.ANTI_CLOGGING_TOKEN_REQ:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ANTI_CLOGGING_TOKEN_REQ;
            case StaIfaceStatusCode.FINITE_CYCLIC_GROUP_NOT_SUPPORTED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.FINITE_CYCLIC_GROUP_NOT_SUPPORTED;
            case StaIfaceStatusCode.CANNOT_FIND_ALT_TBTT:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.CANNOT_FIND_ALT_TBTT;
            case StaIfaceStatusCode.TRANSMISSION_FAILURE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.TRANSMISSION_FAILURE;
            case StaIfaceStatusCode.REQ_TCLAS_NOT_SUPPORTED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REQ_TCLAS_NOT_SUPPORTED;
            case StaIfaceStatusCode.TCLAS_RESOURCES_EXCHAUSTED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.TCLAS_RESOURCES_EXCHAUSTED;
            case StaIfaceStatusCode.REJECTED_WITH_SUGGESTED_BSS_TRANSITION:
                return SupplicantStaIfaceHal.StaIfaceStatusCode
                        .REJECTED_WITH_SUGGESTED_BSS_TRANSITION;
            case StaIfaceStatusCode.REJECT_WITH_SCHEDULE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REJECT_WITH_SCHEDULE;
            case StaIfaceStatusCode.REJECT_NO_WAKEUP_SPECIFIED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REJECT_NO_WAKEUP_SPECIFIED;
            case StaIfaceStatusCode.SUCCESS_POWER_SAVE_MODE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.SUCCESS_POWER_SAVE_MODE;
            case StaIfaceStatusCode.PENDING_ADMITTING_FST_SESSION:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.PENDING_ADMITTING_FST_SESSION;
            case StaIfaceStatusCode.PERFORMING_FST_NOW:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.PERFORMING_FST_NOW;
            case StaIfaceStatusCode.PENDING_GAP_IN_BA_WINDOW:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.PENDING_GAP_IN_BA_WINDOW;
            case StaIfaceStatusCode.REJECT_U_PID_SETTING:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REJECT_U_PID_SETTING;
            case StaIfaceStatusCode.REFUSED_EXTERNAL_REASON:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REFUSED_EXTERNAL_REASON;
            case StaIfaceStatusCode.REFUSED_AP_OUT_OF_MEMORY:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REFUSED_AP_OUT_OF_MEMORY;
            case StaIfaceStatusCode.REJECTED_EMERGENCY_SERVICE_NOT_SUPPORTED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode
                        .REJECTED_EMERGENCY_SERVICE_NOT_SUPPORTED;
            case StaIfaceStatusCode.QUERY_RESP_OUTSTANDING:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.QUERY_RESP_OUTSTANDING;
            case StaIfaceStatusCode.REJECT_DSE_BAND:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.REJECT_DSE_BAND;
            case StaIfaceStatusCode.TCLAS_PROCESSING_TERMINATED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.TCLAS_PROCESSING_TERMINATED;
            case StaIfaceStatusCode.TS_SCHEDULE_CONFLICT:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.TS_SCHEDULE_CONFLICT;
            case StaIfaceStatusCode.DENIED_WITH_SUGGESTED_BAND_AND_CHANNEL:
                return SupplicantStaIfaceHal.StaIfaceStatusCode
                        .DENIED_WITH_SUGGESTED_BAND_AND_CHANNEL;
            case StaIfaceStatusCode.MCCAOP_RESERVATION_CONFLICT:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.MCCAOP_RESERVATION_CONFLICT;
            case StaIfaceStatusCode.MAF_LIMIT_EXCEEDED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.MAF_LIMIT_EXCEEDED;
            case StaIfaceStatusCode.MCCA_TRACK_LIMIT_EXCEEDED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.MCCA_TRACK_LIMIT_EXCEEDED;
            case StaIfaceStatusCode.DENIED_DUE_TO_SPECTRUM_MANAGEMENT:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.DENIED_DUE_TO_SPECTRUM_MANAGEMENT;
            case StaIfaceStatusCode.ASSOC_DENIED_NO_VHT:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ASSOC_DENIED_NO_VHT;
            case StaIfaceStatusCode.ENABLEMENT_DENIED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.ENABLEMENT_DENIED;
            case StaIfaceStatusCode.RESTRICTION_FROM_AUTHORIZED_GDB:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.RESTRICTION_FROM_AUTHORIZED_GDB;
            case StaIfaceStatusCode.AUTHORIZATION_DEENABLED:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.AUTHORIZATION_DEENABLED;
            case StaIfaceStatusCode.FILS_AUTHENTICATION_FAILURE:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.FILS_AUTHENTICATION_FAILURE;
            case StaIfaceStatusCode.UNKNOWN_AUTHENTICATION_SERVER:
                return SupplicantStaIfaceHal.StaIfaceStatusCode.UNKNOWN_AUTHENTICATION_SERVER;
            default:
                Log.e(TAG, "Invalid status code received");
                return -1;
        }
    }

    private static @SupplicantEventCode int halAuxiliaryEventToFrameworkSupplicantEventCode(
            int eventCode) {
        switch (eventCode) {
            case AuxiliarySupplicantEventCode.EAP_METHOD_SELECTED:
                return SupplicantStaIfaceHal.SUPPLICANT_EVENT_EAP_METHOD_SELECTED;
            case AuxiliarySupplicantEventCode.SSID_TEMP_DISABLED:
                return SupplicantStaIfaceHal.SUPPLICANT_EVENT_SSID_TEMP_DISABLED;
            case AuxiliarySupplicantEventCode.OPEN_SSL_FAILURE:
                return SupplicantStaIfaceHal.SUPPLICANT_EVENT_OPEN_SSL_FAILURE;
            default:
                Log.e(TAG, "Invalid auxiliary event code received");
                return -1;
        }
    }

    @Override
    public void onBssTmHandlingDone(BssTmData tmData) {
        MboOceController.BtmFrameData btmFrmData = new MboOceController.BtmFrameData();

        btmFrmData.mStatus = halToFrameworkBtmResponseStatus(tmData.status);
        btmFrmData.mBssTmDataFlagsMask = halToFrameworkBssTmDataFlagsMask(tmData.flags);
        btmFrmData.mBlockListDurationMs = tmData.assocRetryDelayMs;
        if ((tmData.flags & BssTmDataFlagsMask.MBO_TRANSITION_REASON_CODE_INCLUDED) != 0) {
            btmFrmData.mTransitionReason = halToFrameworkMboTransitionReason(
                    tmData.mboTransitionReason);
        }
        if ((tmData.flags
                & BssTmDataFlagsMask.MBO_CELLULAR_DATA_CONNECTION_PREFERENCE_INCLUDED) != 0) {
            btmFrmData.mCellPreference =
                    halToFrameworkMboCellularDataConnectionPreference(tmData.mboCellPreference);
        }
        mStaIfaceHal.logCallback(
                "onBssTmHandlingDone: Handle BTM handling event");
        mWifiMonitor.broadcastBssTmHandlingDoneEvent(mIfaceName, btmFrmData);
    }

    @Override
    public void onHs20TermsAndConditionsAcceptanceRequestedNotification(byte[/* 6 */] bssid,
            String url) {
        synchronized (mLock) {
            mStaIfaceHal.logCallback("onHs20TermsAndConditionsAcceptanceRequestedNotification");
            mWifiMonitor.broadcastWnmEvent(mIfaceName,
                    WnmData.createTermsAndConditionsAccetanceRequiredEvent(
                            NativeUtil.macAddressToLong(bssid), url));
        }
    }

    @Override
    public void onNetworkNotFound(byte[] ssid) {
        mStaIfaceHal.logCallback("onNetworkNotFoundNotification");
        if (mStaIfaceHal.connectToFallbackSsid(mIfaceName)) {
            return;
        }
        mWifiMonitor.broadcastNetworkNotFoundEvent(mIfaceName,
                mSsidTranslator.getTranslatedSsidForStaIface(
                        WifiSsid.fromBytes(ssid), mIfaceName).toString());
    }

    @Override
    public void onQosPolicyReset() {
        synchronized (mLock) {
            mStaIfaceHal.logCallback("onQosPolicyReset");
            mWifiMonitor.broadcastQosPolicyResetEvent(mIfaceName);
        }
    }

    @Override
    public void onQosPolicyRequest(int qosPolicyRequestId, QosPolicyData[] qosPolicyData) {
        synchronized (mLock) {
            mStaIfaceHal.logCallback("onQosPolicyRequest");
            // Convert QoS policies from HAL to framework representation.
            List<QosPolicyRequest> frameworkQosPolicies = new ArrayList();
            if (qosPolicyData != null) {
                for (QosPolicyData halPolicy : qosPolicyData) {
                    frameworkQosPolicies.add(
                            SupplicantStaIfaceHalAidlImpl.halToFrameworkQosPolicy(halPolicy));
                }
            }
            mWifiMonitor.broadcastQosPolicyRequestEvent(mIfaceName, qosPolicyRequestId,
                    frameworkQosPolicies);
        }
    }

    @Override
    public void onAuxiliarySupplicantEvent(int eventCode, byte[] bssid,
            String reasonString) {
        synchronized (mLock) {
            @SupplicantEventCode int supplicantEventCode =
                    halAuxiliaryEventToFrameworkSupplicantEventCode(eventCode);
            mStaIfaceHal.logCallback("onAuxiliarySupplicantEvent event=" + supplicantEventCode);
            if (supplicantEventCode != -1) {
                try {
                    mWifiMonitor.broadcastAuxiliarySupplicantEvent(mIfaceName, supplicantEventCode,
                            MacAddress.fromBytes(bssid), reasonString);
                } catch (IllegalArgumentException e) {
                    Log.i(TAG, "Invalid bssid received");
                }
            }
        }
    }

    @Override
    public String getInterfaceHash() {
        return ISupplicantStaIfaceCallback.HASH;
    }

    @Override
    public int getInterfaceVersion() {
        return ISupplicantStaIfaceCallback.VERSION;
    }

    private String getMloLinksInfoChangedReasonStr(int reason) {
        switch (reason) {
            case ISupplicantStaIfaceCallback.MloLinkInfoChangeReason.TID_TO_LINK_MAP:
                return "TID_TO_LINK_MAP";
            case ISupplicantStaIfaceCallback.MloLinkInfoChangeReason.MULTI_LINK_RECONFIG_AP_REMOVAL:
                return "MULTI_LINK_RECONFIG_AP_REMOVAL";
            default:
                return "UNKNOWN";
        }
    }

    private WifiMonitor.MloLinkInfoChangeReason convertMloLinkInfoChangedReason(int reason) {
        switch (reason) {
            case ISupplicantStaIfaceCallback.MloLinkInfoChangeReason.TID_TO_LINK_MAP:
                return WifiMonitor.MloLinkInfoChangeReason.TID_TO_LINK_MAP;
            case ISupplicantStaIfaceCallback.MloLinkInfoChangeReason.MULTI_LINK_RECONFIG_AP_REMOVAL:
                return WifiMonitor.MloLinkInfoChangeReason.MULTI_LINK_RECONFIG_AP_REMOVAL;
            default:
                return WifiMonitor.MloLinkInfoChangeReason.UNKNOWN;
        }
    }

    @Override
    public void onMloLinksInfoChanged(int reason)
            throws android.os.RemoteException {
        synchronized (mLock) {
            mStaIfaceHal.logCallback(
                    "onMloLinksInfoChanged: reason " + Integer.toString(reason) + " ("
                            + getMloLinksInfoChangedReasonStr(reason) + ")");
            mWifiMonitor.broadcastMloLinksInfoChanged(mIfaceName,
                    convertMloLinkInfoChangedReason(reason));
        }
    }

    /**
     * Used to indicate that the operating frequency has changed for this BSS.
     * This event is triggered when STA switches the channel due to channel
     * switch announcement from the connected access point.
     *
     * @param frequencyMhz New frequency in MHz.
     */
    @Override
    public void onBssFrequencyChanged(int frequencyMhz)
            throws android.os.RemoteException {
        synchronized (mLock) {
            mStaIfaceHal.logCallback("onBssFrequencyChanged: frequency " + frequencyMhz);
            mWifiMonitor.broadcastBssFrequencyChanged(mIfaceName, frequencyMhz);
        }
    }

    private static @SupplicantStaIfaceHal.QosPolicyScsResponseStatusCode int
            halToFrameworkQosPolicyScsResponseStatusCode(int statusCode) {
        switch (statusCode) {
            case QosPolicyScsResponseStatusCode.SUCCESS:
                return SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS;
            case QosPolicyScsResponseStatusCode.TCLAS_REQUEST_DECLINED:
                return SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_TCLAS_REQUEST_DECLINED;
            case QosPolicyScsResponseStatusCode.TCLAS_NOT_SUPPORTED_BY_AP:
                return SupplicantStaIfaceHal
                        .QOS_POLICY_SCS_RESPONSE_STATUS_TCLAS_NOT_SUPPORTED_BY_AP;
            case QosPolicyScsResponseStatusCode.TCLAS_INSUFFICIENT_RESOURCES:
                return SupplicantStaIfaceHal
                        .QOS_POLICY_SCS_RESPONSE_STATUS_TCLAS_INSUFFICIENT_RESOURCES;
            case QosPolicyScsResponseStatusCode.TCLAS_RESOURCES_EXHAUSTED:
                return SupplicantStaIfaceHal
                        .QOS_POLICY_SCS_RESPONSE_STATUS_TCLAS_RESOURCES_EXHAUSTED;
            case QosPolicyScsResponseStatusCode.TCLAS_PROCESSING_TERMINATED_INSUFFICIENT_QOS:
                return SupplicantStaIfaceHal
                        .QOS_POLICY_SCS_RESPONSE_STATUS_TCLAS_PROCESSING_TERMINATED_INSUFFICIENT_QOS;
            case QosPolicyScsResponseStatusCode.TCLAS_PROCESSING_TERMINATED_POLICY_CONFLICT:
                return SupplicantStaIfaceHal
                        .QOS_POLICY_SCS_RESPONSE_STATUS_TCLAS_PROCESSING_TERMINATED_POLICY_CONFLICT;
            case QosPolicyScsResponseStatusCode.TCLAS_PROCESSING_TERMINATED:
                return SupplicantStaIfaceHal
                        .QOS_POLICY_SCS_RESPONSE_STATUS_TCLAS_PROCESSING_TERMINATED;
            case QosPolicyScsResponseStatusCode.TIMEOUT:
                return SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_TIMEOUT;
            default:
                Log.wtf(TAG, "Invalid QosPolicyScsResponseStatusCode: " + statusCode);
                return SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_ERROR_UNKNOWN;
        }
    }

    @Override
    public void onQosPolicyResponseForScs(QosPolicyScsResponseStatus[] halStatusList) {
        synchronized (mLock) {
            mStaIfaceHal.logCallback("onQosPolicyResponseForScs: size="
                    + halStatusList.length);
            SupplicantStaIfaceHal.QosScsResponseCallback frameworkCallback =
                    mStaIfaceHal.getQosScsResponseCallback();
            if (frameworkCallback == null) return;

            List<SupplicantStaIfaceHal.QosPolicyStatus> frameworkStatusList = new ArrayList<>();
            for (QosPolicyScsResponseStatus halStatus : halStatusList) {
                frameworkStatusList.add(new SupplicantStaIfaceHal.QosPolicyStatus(
                        halStatus.policyId,
                        halToFrameworkQosPolicyScsResponseStatusCode(
                                halStatus.qosPolicyScsResponseStatusCode)));
            }
            frameworkCallback.onApResponse(mIfaceName, frameworkStatusList);
        }
    }
}
