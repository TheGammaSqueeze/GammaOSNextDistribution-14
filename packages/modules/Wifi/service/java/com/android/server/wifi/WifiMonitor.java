/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.annotation.IntDef;
import android.net.MacAddress;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiSsid;
import android.os.Handler;
import android.os.Message;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Protocol;
import com.android.server.wifi.MboOceController.BtmFrameData;
import com.android.server.wifi.SupplicantStaIfaceHal.QosPolicyRequest;
import com.android.server.wifi.SupplicantStaIfaceHal.SupplicantEventCode;
import com.android.server.wifi.WifiCarrierInfoManager.SimAuthRequestData;
import com.android.server.wifi.hotspot2.AnqpEvent;
import com.android.server.wifi.hotspot2.IconEvent;
import com.android.server.wifi.hotspot2.WnmData;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Listen for events from the wpa_supplicant & wificond and broadcast them on
 * to the various {@link ClientModeImpl} modules interested in handling these events.
 */
public class WifiMonitor {
    private static final String TAG = "WifiMonitor";

    /* Supplicant events reported to a state machine */
    private static final int BASE = Protocol.BASE_WIFI_MONITOR;

   /* Network connection completed */
    public static final int NETWORK_CONNECTION_EVENT             = BASE + 3;
    /* Network disconnection completed */
    public static final int NETWORK_DISCONNECTION_EVENT          = BASE + 4;
    /* Scan results are available */
    public static final int SCAN_RESULTS_EVENT                   = BASE + 5;
    /* Supplicate state changed */
    public static final int SUPPLICANT_STATE_CHANGE_EVENT        = BASE + 6;
    /* Password failure and EAP authentication failure */
    public static final int AUTHENTICATION_FAILURE_EVENT         = BASE + 7;
    /* WPS success detected */
    public static final int WPS_SUCCESS_EVENT                    = BASE + 8;
    /* WPS failure detected */
    public static final int WPS_FAIL_EVENT                       = BASE + 9;
     /* WPS overlap detected */
    public static final int WPS_OVERLAP_EVENT                    = BASE + 10;
     /* WPS timeout detected */
    public static final int WPS_TIMEOUT_EVENT                    = BASE + 11;

    /* Request Identity */
    public static final int SUP_REQUEST_IDENTITY                 = BASE + 15;

    /* Request SIM Auth */
    public static final int SUP_REQUEST_SIM_AUTH                 = BASE + 16;

    public static final int SCAN_FAILED_EVENT                    = BASE + 17;
    /* Pno scan results are available */
    public static final int PNO_SCAN_RESULTS_EVENT               = BASE + 18;


    /* Indicates assoc reject event */
    public static final int ASSOCIATION_REJECTION_EVENT          = BASE + 43;
    public static final int ANQP_DONE_EVENT                      = BASE + 44;
    public static final int ASSOCIATED_BSSID_EVENT               = BASE + 45;
    public static final int TARGET_BSSID_EVENT                   = BASE + 46;
    public static final int NETWORK_NOT_FOUND_EVENT              = BASE + 47;

    /* Passpoint ANQP events */
    public static final int GAS_QUERY_START_EVENT                = BASE + 51;
    public static final int GAS_QUERY_DONE_EVENT                 = BASE + 52;
    public static final int RX_HS20_ANQP_ICON_EVENT              = BASE + 53;

    /* Passpoint events */
    public static final int HS20_REMEDIATION_EVENT               = BASE + 61;
    public static final int HS20_DEAUTH_IMMINENT_EVENT           = BASE + 62;
    public static final int HS20_TERMS_AND_CONDITIONS_ACCEPTANCE_REQUIRED_EVENT = BASE + 63;

    /* MBO/OCE events */
    public static final int MBO_OCE_BSS_TM_HANDLING_DONE         = BASE + 71;

    /* Transition Disable Indication */
    public static final int TRANSITION_DISABLE_INDICATION        = BASE + 72;

    /* Trust On First Use incoming certificate event */
    public static final int TOFU_CERTIFICATE_EVENT               = BASE + 73;

    /* Auxiliary supplicant event */
    public static final int AUXILIARY_SUPPLICANT_EVENT           = BASE + 74;

    /* Quality of Service (QoS) events */
    public static final int QOS_POLICY_RESET_EVENT               = BASE + 75;
    public static final int QOS_POLICY_REQUEST_EVENT             = BASE + 76;

    /* MLO links change event */
    public static final int MLO_LINKS_INFO_CHANGED              = BASE + 77;

    /* the AT_PERMANENT_ID_REQ denied indication for EAP-SIM/AKA/AKA' */
    public static final int PERMANENT_ID_REQ_DENIED_INDICATION = BASE + 78;

    /* BSS frequency changed event (when STA switches the channel due to CSA) */
    public static final int BSS_FREQUENCY_CHANGED_EVENT = BASE + 79;

    /* WPS config errrors */
    private static final int CONFIG_MULTIPLE_PBC_DETECTED = 12;
    private static final int CONFIG_AUTH_FAILURE = 18;

    /* WPS error indications */
    private static final int REASON_TKIP_ONLY_PROHIBITED = 1;
    private static final int REASON_WEP_PROHIBITED = 2;

    /* Transition disable indication */
    public static final int TDI_USE_WPA3_PERSONAL = 1 << 0;
    public static final int TDI_USE_SAE_PK = 1 << 1;
    public static final int TDI_USE_WPA3_ENTERPRISE = 1 << 2;
    public static final int TDI_USE_ENHANCED_OPEN = 1 << 3;

    @IntDef(flag = true, prefix = { "TDI_" }, value = {
            TDI_USE_WPA3_PERSONAL,
            TDI_USE_SAE_PK,
            TDI_USE_WPA3_ENTERPRISE,
            TDI_USE_ENHANCED_OPEN,
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface TransitionDisableIndication{}

    /* MLO links change event reason codes */
    public enum MloLinkInfoChangeReason {
        UNKNOWN,
        TID_TO_LINK_MAP,
        MULTI_LINK_RECONFIG_AP_REMOVAL,
    }

    /**
     * Use this key to get the interface name of the message sent by WifiMonitor,
     * or null if not available.
     *
     * <br />
     * Sample code:
     * <code>
     * message.getData().getString(KEY_IFACE)
     * </code>
     */
    public static final String KEY_IFACE = "com.android.server.wifi.WifiMonitor.KEY_IFACE";

    private boolean mVerboseLoggingEnabled = false;

    void enableVerboseLogging(boolean verbose) {
        mVerboseLoggingEnabled = verbose;
    }

    private final Map<String, SparseArray<Set<Handler>>> mHandlerMap = new HashMap<>();
    public synchronized void registerHandler(String iface, int what, Handler handler) {
        SparseArray<Set<Handler>> ifaceHandlers = mHandlerMap.get(iface);
        if (ifaceHandlers == null) {
            ifaceHandlers = new SparseArray<>();
            mHandlerMap.put(iface, ifaceHandlers);
        }
        Set<Handler> ifaceWhatHandlers = ifaceHandlers.get(what);
        if (ifaceWhatHandlers == null) {
            ifaceWhatHandlers = new ArraySet<>();
            ifaceHandlers.put(what, ifaceWhatHandlers);
        }
        ifaceWhatHandlers.add(handler);
    }

    /**
     * Deregister the given |handler|
     * @param iface
     * @param what
     * @param handler
     */
    public synchronized void deregisterHandler(String iface, int what, Handler handler) {
        SparseArray<Set<Handler>> ifaceHandlers = mHandlerMap.get(iface);
        if (ifaceHandlers == null) {
            return;
        }
        Set<Handler> ifaceWhatHandlers = ifaceHandlers.get(what);
        if (ifaceWhatHandlers == null) {
            return;
        }
        ifaceWhatHandlers.remove(handler);
    }

    private final Map<String, Boolean> mMonitoringMap = new HashMap<>();
    private boolean isMonitoring(String iface) {
        Boolean val = mMonitoringMap.get(iface);
        if (val == null) {
            return false;
        } else {
            return val.booleanValue();
        }
    }

    /**
     * Enable/Disable monitoring for the provided iface.
     *
     * @param iface Name of the iface.
     * @param enabled true to enable, false to disable.
     */
    @VisibleForTesting
    public void setMonitoring(String iface, boolean enabled) {
        mMonitoringMap.put(iface, enabled);
    }

    /**
     * Start Monitoring for wpa_supplicant events.
     *
     * @param iface Name of iface.
     */
    public synchronized void startMonitoring(String iface) {
        if (mVerboseLoggingEnabled) Log.d(TAG, "startMonitoring(" + iface + ")");
        setMonitoring(iface, true);
    }

    /**
     * Stop Monitoring for wpa_supplicant events.
     *
     * @param iface Name of iface.
     */
    public synchronized void stopMonitoring(String iface) {
        if (mVerboseLoggingEnabled) Log.d(TAG, "stopMonitoring(" + iface + ")");
        setMonitoring(iface, true);
        setMonitoring(iface, false);
    }

    /**
     * Returns the names of any interfaces that are being monitored.
     */
    public List<String> getMonitoredIfaceNames() {
        List<String> monitoringIfaceList = new ArrayList<>();
        for (String iface : mMonitoringMap.keySet()) {
            if (mMonitoringMap.get(iface)) {
                monitoringIfaceList.add(iface);
            }
        }
        return monitoringIfaceList;
    }

    /**
     * Similar functions to Handler#sendMessage that send the message to the registered handler
     * for the given interface and message what.
     * All of these should be called with the WifiMonitor class lock
     */
    private void sendMessage(String iface, int what) {
        sendMessage(iface, Message.obtain(null, what));
    }

    private void sendMessage(String iface, int what, Object obj) {
        sendMessage(iface, Message.obtain(null, what, obj));
    }

    private void sendMessage(String iface, int what, int arg1) {
        sendMessage(iface, Message.obtain(null, what, arg1, 0));
    }

    private void sendMessage(String iface, int what, int arg1, int arg2) {
        sendMessage(iface, Message.obtain(null, what, arg1, arg2));
    }

    private void sendMessage(String iface, int what, int arg1, int arg2, Object obj) {
        sendMessage(iface, Message.obtain(null, what, arg1, arg2, obj));
    }

    private void sendMessage(String iface, Message message) {
        SparseArray<Set<Handler>> ifaceHandlers = mHandlerMap.get(iface);
        if (iface != null && ifaceHandlers != null) {
            if (isMonitoring(iface)) {
                Set<Handler> ifaceWhatHandlers = ifaceHandlers.get(message.what);
                if (ifaceWhatHandlers != null) {
                    for (Handler handler : ifaceWhatHandlers) {
                        if (handler != null) {
                            sendMessage(iface, handler, Message.obtain(message));
                        }
                    }
                }
            } else {
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "Dropping event because (" + iface + ") is stopped");
                }
            }
        } else {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "Sending to all monitors because there's no matching iface");
            }
            for (Map.Entry<String, SparseArray<Set<Handler>>> entry : mHandlerMap.entrySet()) {
                iface = entry.getKey();
                if (isMonitoring(iface)) {
                    Set<Handler> ifaceWhatHandlers = entry.getValue().get(message.what);
                    if (ifaceWhatHandlers == null) continue;
                    for (Handler handler : ifaceWhatHandlers) {
                        if (handler != null) {
                            sendMessage(iface, handler, Message.obtain(message));
                        }
                    }
                }
            }
        }

        message.recycle();
    }

    private void sendMessage(String iface, Handler handler, Message message) {
        message.setTarget(handler);
        // getData() will return the existing Bundle if it exists, or create a new one
        // This prevents clearing the existing data.
        message.getData().putString(KEY_IFACE, iface);
        message.sendToTarget();
    }

    /**
     * Broadcast the WPS fail event to all the handlers registered for this event.
     *
     * @param iface Name of iface on which this occurred.
     * @param cfgError Configuration error code.
     * @param vendorErrorCode Vendor specific error indication code.
     */
    public void broadcastWpsFailEvent(String iface, int cfgError, int vendorErrorCode) {
        int reason = 0;
        switch(vendorErrorCode) {
            case REASON_TKIP_ONLY_PROHIBITED:
                sendMessage(iface, WPS_FAIL_EVENT, WifiManager.WPS_TKIP_ONLY_PROHIBITED);
                return;
            case REASON_WEP_PROHIBITED:
                sendMessage(iface, WPS_FAIL_EVENT, WifiManager.WPS_WEP_PROHIBITED);
                return;
            default:
                reason = vendorErrorCode;
                break;
        }
        switch(cfgError) {
            case CONFIG_AUTH_FAILURE:
                sendMessage(iface, WPS_FAIL_EVENT, WifiManager.WPS_AUTH_FAILURE);
                return;
            case CONFIG_MULTIPLE_PBC_DETECTED:
                sendMessage(iface, WPS_FAIL_EVENT, WifiManager.WPS_OVERLAP_ERROR);
                return;
            default:
                if (reason == 0) {
                    reason = cfgError;
                }
                break;
        }
        //For all other errors, return a generic internal error
        sendMessage(iface, WPS_FAIL_EVENT, WifiManager.ActionListener.FAILURE_INTERNAL_ERROR,
                reason);
    }

   /**
    * Broadcast the WPS succes event to all the handlers registered for this event.
    *
    * @param iface Name of iface on which this occurred.
    */
    public void broadcastWpsSuccessEvent(String iface) {
        sendMessage(iface, WPS_SUCCESS_EVENT);
    }

    /**
     * Broadcast the WPS overlap event to all the handlers registered for this event.
     *
     * @param iface Name of iface on which this occurred.
     */
    public void broadcastWpsOverlapEvent(String iface) {
        sendMessage(iface, WPS_OVERLAP_EVENT);
    }

    /**
     * Broadcast the WPS timeout event to all the handlers registered for this event.
     *
     * @param iface Name of iface on which this occurred.
     */
    public void broadcastWpsTimeoutEvent(String iface) {
        sendMessage(iface, WPS_TIMEOUT_EVENT);
    }

    /**
     * Broadcast the ANQP done event to all the handlers registered for this event.
     *
     * @param iface Name of iface on which this occurred.
     * @param anqpEvent ANQP result retrieved.
     */
    public void broadcastAnqpDoneEvent(String iface, AnqpEvent anqpEvent) {
        sendMessage(iface, ANQP_DONE_EVENT, anqpEvent);
    }

    /**
     * Broadcast the Icon done event to all the handlers registered for this event.
     *
     * @param iface Name of iface on which this occurred.
     * @param iconEvent Instance of IconEvent containing the icon data retrieved.
     */
    public void broadcastIconDoneEvent(String iface, IconEvent iconEvent) {
        sendMessage(iface, RX_HS20_ANQP_ICON_EVENT, iconEvent);
    }

    /**
     * Broadcast the WNM event to all the handlers registered for this event.
     *
     * @param iface Name of iface on which this occurred.
     * @param wnmData Instance of WnmData containing the event data.
     */
    public void broadcastWnmEvent(String iface, WnmData wnmData) {
        if (mVerboseLoggingEnabled) Log.d(TAG, "WNM-Notification " + wnmData.getEventType());
        switch (wnmData.getEventType()) {
            case WnmData.HS20_REMEDIATION_EVENT:
                sendMessage(iface, HS20_REMEDIATION_EVENT, wnmData);
                break;

            case WnmData.HS20_DEAUTH_IMMINENT_EVENT:
                sendMessage(iface, HS20_DEAUTH_IMMINENT_EVENT, wnmData);
                break;

            case WnmData.HS20_TERMS_AND_CONDITIONS_ACCEPTANCE_REQUIRED_EVENT:
                sendMessage(iface, HS20_TERMS_AND_CONDITIONS_ACCEPTANCE_REQUIRED_EVENT, wnmData);
                break;

            default:
                Log.e(TAG, "Broadcast request for an unknown WNM-notification "
                        + wnmData.getEventType());
                break;
        }
    }

    /**
     * Broadcast the Network identity request event to all the handlers registered for this event.
     *
     * @param iface Name of iface on which this occurred.
     * @param networkId ID of the network in wpa_supplicant.
     * @param ssid SSID of the network.
     */
    public void broadcastNetworkIdentityRequestEvent(String iface, int networkId, String ssid) {
        sendMessage(iface, SUP_REQUEST_IDENTITY, 0, networkId, ssid);
    }

    /**
     * Broadcast the transition disable event to all the handlers registered for this event.
     *
     * @param iface Name of iface on which this occurred.
     * @param networkId ID of the network in wpa_supplicant.
     * @param indicationBits bits of the disable indication.
     */
    public void broadcastTransitionDisableEvent(
            String iface, int networkId,
            @TransitionDisableIndication int indicationBits) {
        sendMessage(iface, TRANSITION_DISABLE_INDICATION, networkId, indicationBits);
    }

    /**
     * Broadcast the Network Gsm Sim auth request event to all the handlers registered for this
     * event.
     *
     * @param iface Name of iface on which this occurred.
     * @param networkId ID of the network in wpa_supplicant.
     * @param ssid SSID of the network.
     * @param data Accompanying event data.
     */
    public void broadcastNetworkGsmAuthRequestEvent(String iface, int networkId, String ssid,
                                                    String[] data) {
        sendMessage(iface, SUP_REQUEST_SIM_AUTH,
                new SimAuthRequestData(networkId, WifiEnterpriseConfig.Eap.SIM, ssid, data));
    }

    /**
     * Broadcast the Network Umts Sim auth request event to all the handlers registered for this
     * event.
     *
     * @param iface Name of iface on which this occurred.
     * @param networkId ID of the network in wpa_supplicant.
     * @param ssid SSID of the network.
     * @param data Accompanying event data.
     */
    public void broadcastNetworkUmtsAuthRequestEvent(String iface, int networkId, String ssid,
                                                     String[] data) {
        sendMessage(iface, SUP_REQUEST_SIM_AUTH,
                new SimAuthRequestData(networkId, WifiEnterpriseConfig.Eap.AKA, ssid, data));
    }

    /**
     * Broadcast scan result event to all the handlers registered for this event.
     * @param iface Name of iface on which this occurred.
     */
    public void broadcastScanResultEvent(String iface) {
        sendMessage(iface, SCAN_RESULTS_EVENT);
    }

    /**
     * Broadcast pno scan result event to all the handlers registered for this event.
     * @param iface Name of iface on which this occurred.
     */
    public void broadcastPnoScanResultEvent(String iface) {
        sendMessage(iface, PNO_SCAN_RESULTS_EVENT);
    }

    /**
     * Broadcast scan failed event to all the handlers registered for this event.
     * @param iface Name of iface on which this occurred.
     */
    public void broadcastScanFailedEvent(String iface, int errorCode) {
        sendMessage(iface, SCAN_FAILED_EVENT, errorCode);
    }

    /**
     * Broadcast the authentication failure event to all the handlers registered for this event.
     *
     * @param iface Name of iface on which this occurred.
     * @param reason Reason for authentication failure. This has to be one of the
     *               {@link android.net.wifi.WifiManager#ERROR_AUTH_FAILURE_NONE},
     *               {@link android.net.wifi.WifiManager#ERROR_AUTH_FAILURE_TIMEOUT},
     *               {@link android.net.wifi.WifiManager#ERROR_AUTH_FAILURE_WRONG_PSWD},
     *               {@link android.net.wifi.WifiManager#ERROR_AUTH_FAILURE_EAP_FAILURE}
     * @param errorCode Error code associated with the authentication failure event.
     *               A value of -1 is used when no error code is reported.
     */
    public void broadcastAuthenticationFailureEvent(String iface, int reason, int errorCode,
            String ssid, MacAddress bssid) {
        sendMessage(iface, AUTHENTICATION_FAILURE_EVENT,
                new AuthenticationFailureEventInfo(ssid, bssid, reason, errorCode));
    }

    /**
     * Broadcast the association rejection event to all the handlers registered for this event.
     *
     * @param iface Name of iface on which this occurred.
     * @param assocRejectInfo Instance of AssocRejectEventInfo containing the association
     *                        rejection info.
     */
    public void broadcastAssociationRejectionEvent(String iface,
            AssocRejectEventInfo assocRejectInfo) {
        sendMessage(iface, ASSOCIATION_REJECTION_EVENT, assocRejectInfo);
    }

    /**
     * Broadcast the association success event to all the handlers registered for this event.
     *
     * @param iface Name of iface on which this occurred.
     * @param bssid BSSID of the access point.
     */
    public void broadcastAssociatedBssidEvent(String iface, String bssid) {
        sendMessage(iface, ASSOCIATED_BSSID_EVENT, 0, 0, bssid);
    }

    /**
     * Broadcast the start of association event to all the handlers registered for this event.
     *
     * @param iface Name of iface on which this occurred.
     * @param bssid BSSID of the access point.
     */
    public void broadcastTargetBssidEvent(String iface, String bssid) {
        sendMessage(iface, TARGET_BSSID_EVENT, 0, 0, bssid);
    }

    /**
     * Broadcast the network connection event to all the handlers registered for this event.
     *
     * @param iface Name of iface on which this occurred.
     * @param networkId ID of the network in wpa_supplicant.
     * @param filsHlpSent Whether the connection used FILS.
     * @param bssid BSSID of the access point.
     */
    public void broadcastNetworkConnectionEvent(String iface, int networkId, boolean filsHlpSent,
            WifiSsid ssid, String bssid) {
        broadcastNetworkConnectionEvent(iface, networkId, filsHlpSent, ssid, bssid, null);
    }

    /**
     * Broadcast the network connection event to all the handlers registered for this event.
     *
     * @param iface Name of iface on which this occurred.
     * @param networkId ID of the network in wpa_supplicant.
     * @param filsHlpSent Whether the connection used FILS.
     * @param bssid BSSID of the access point.
     * @param keyMgmtMask Current used key management mask.
     */
    public void broadcastNetworkConnectionEvent(String iface, int networkId, boolean filsHlpSent,
            WifiSsid ssid, String bssid, BitSet keyMgmtMask) {
        sendMessage(iface, NETWORK_CONNECTION_EVENT,
                new NetworkConnectionEventInfo(networkId, ssid, bssid, filsHlpSent, keyMgmtMask));
    }

    /**
     * Broadcast the network disconnection event to all the handlers registered for this event.
     *
     * @param iface Name of iface on which this occurred.
     * @param locallyGenerated Whether the disconnect was locally triggered.
     * @param reason Disconnect reason code.
     * @param ssid SSID of the access point.
     * @param bssid BSSID of the access point.
     */
    public void broadcastNetworkDisconnectionEvent(String iface, boolean locallyGenerated,
            int reason, String ssid, String bssid) {
        sendMessage(iface, NETWORK_DISCONNECTION_EVENT,
                new DisconnectEventInfo(ssid, bssid, reason, locallyGenerated));
    }

    /**
     * Broadcast the supplicant state change event to all the handlers registered for this event.
     *
     * @param iface Name of iface on which this occurred.
     * @param networkId ID of the network in wpa_supplicant.
     * @param bssid BSSID of the access point.
     * @param frequencyMhz Frequency of the connected channel in MHz
     * @param newSupplicantState New supplicant state.
     */
    public void broadcastSupplicantStateChangeEvent(String iface, int networkId, WifiSsid wifiSsid,
                                                    String bssid, int frequencyMhz,
                                                    SupplicantState newSupplicantState) {
        sendMessage(iface, SUPPLICANT_STATE_CHANGE_EVENT, 0, 0,
                new StateChangeResult(networkId, wifiSsid, bssid, frequencyMhz,
                        newSupplicantState));
    }

    /**
     * Broadcast the bss transition management frame handling event
     * to all the handlers registered for this event.
     *
     * @param iface Name of iface on which this occurred.
     */
    public void broadcastBssTmHandlingDoneEvent(String iface, BtmFrameData btmFrmData) {
        sendMessage(iface, MBO_OCE_BSS_TM_HANDLING_DONE, btmFrmData);
    }

    /**
     * Broadcast network not found event
     * to all the handlers registered for this event.
     *
     * @param iface Name of iface on which this occurred.
     */
    public void broadcastNetworkNotFoundEvent(String iface, String ssid) {
        sendMessage(iface, NETWORK_NOT_FOUND_EVENT, ssid);
    }

    /**
     * Broadcast the certification event which takes place during TOFU process.
     *
     * @param iface Name of iface on which this occurred.
     * @param networkId ID of the network in wpa_supplicant.
     * @param ssid SSID of the network.
     * @param depth the depth of this cert in the chain, 0 is the leaf, i.e. the server cert.
     * @param certificateEventInfo the certificate data.
     */
    public void broadcastCertificationEvent(String iface, int networkId, String ssid,
            int depth, CertificateEventInfo certificateEventInfo) {
        sendMessage(iface, TOFU_CERTIFICATE_EVENT, networkId, depth, certificateEventInfo);
    }

    /**
     * Broadcast the indication of AT_PERMANENT_ID_REQ denied for EAP-SIM/AKA/AKA'
     * when the strict conservative peer mode is enabled.
     *
     * @param iface Name of iface on which this occurred.
     * @param networkId ID of the network in wpa_supplicant.
     * @param ssid SSID of the network.
     */
    public void broadcastPermanentIdReqDenied(String iface, int networkId,
            String ssid) {
        sendMessage(iface, PERMANENT_ID_REQ_DENIED_INDICATION, 0, networkId, ssid);
    }

    /**
     * Broadcast an auxiliary supplicant event to all handlers registered for this event.
     *
     * @param iface Name of iface on which this occurred.
     * @param eventCode SupplicantEventCode for the event that occurred.
     * @param bssid BSSID of the network.
     * @param reasonString Optional string containing more information about why the
     *                     event occurred.
     */
    public void broadcastAuxiliarySupplicantEvent(String iface, @SupplicantEventCode int eventCode,
            MacAddress bssid, String reasonString) {
        sendMessage(iface, AUXILIARY_SUPPLICANT_EVENT, 0, 0,
                new SupplicantEventInfo(eventCode, bssid, reasonString));
    }

    /**
     * Broadcast the QoS policy reset event to all the handlers
     * registered for this event.
     *
     * @param iface Name of iface on which this occurred.
     */
    public void broadcastQosPolicyResetEvent(String iface) {
        sendMessage(iface, QOS_POLICY_RESET_EVENT);
    }

    /**
     * Broadcast the QoS policy request event to all the handlers
     * registered for this event.
     *
     * @param iface Name of iface on which this occurred.
     * @param qosPolicyRequestId Dialog token to identify the request.
     * @param qosPolicyData List of QoS policies requested by the AP.
     */
    public void broadcastQosPolicyRequestEvent(String iface, int qosPolicyRequestId,
            List<QosPolicyRequest> qosPolicyData) {
        sendMessage(iface, QOS_POLICY_REQUEST_EVENT, qosPolicyRequestId, 0, qosPolicyData);
    }

    /**
     * Broadcast the MLO link changes with reason code to all handlers registered for this event.
     *
     * @param iface Name of the iface on which this occurred.
     * @param reason Reason code for the MLO link info change.
     */
    public void broadcastMloLinksInfoChanged(String iface,
            WifiMonitor.MloLinkInfoChangeReason reason) {
        sendMessage(iface, MLO_LINKS_INFO_CHANGED, reason);
    }

    /**
     * Broadcast the BSS frequency changed event.
     * This message is sent when STA switches the channel due to channel
     * switch announcement from the connected access point.
     *
     * @param iface Name of the iface on which this occurred.
     * @param frequencyMhz New frequency in MHz.
     */
    public void broadcastBssFrequencyChanged(String iface, int frequencyMhz) {
        sendMessage(iface, BSS_FREQUENCY_CHANGED_EVENT, frequencyMhz);
    }
}
