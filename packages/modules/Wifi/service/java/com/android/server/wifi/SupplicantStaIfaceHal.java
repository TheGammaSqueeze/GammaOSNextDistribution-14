/*
 * Copyright (C) 2017 The Android Open Source Project
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
import android.annotation.NonNull;
import android.content.Context;
import android.net.MacAddress;
import android.net.wifi.MscsParams;
import android.net.wifi.QosPolicyParams;
import android.net.wifi.SecurityParams;
import android.net.wifi.WifiConfiguration;
import android.os.Handler;
import android.util.Log;
import android.util.Range;

import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.concurrent.ThreadSafe;

/**
 * To maintain thread-safety, the locking protocol is that every non-static method (regardless of
 * access level) acquires mLock.
 */
@ThreadSafe
public class SupplicantStaIfaceHal {
    private static final String TAG = "SupplicantStaIfaceHal";
    private final Object mLock = new Object();
    private final Context mContext;
    private final WifiMonitor mWifiMonitor;
    private final FrameworkFacade mFrameworkFacade;
    private final Handler mEventHandler;
    private final Clock mClock;
    private final WifiMetrics mWifiMetrics;
    private final WifiGlobals mWifiGlobals;
    private final @NonNull SsidTranslator mSsidTranslator;
    private final WifiInjector mWifiInjector;

    // HAL interface object - might be implemented by HIDL or AIDL
    private ISupplicantStaIfaceHal mStaIfaceHal;

    // Common enums declared here to be independent from HIDL/AIDL.
    // See HAL comments for more information on each.
    protected static class DppAkm {
        public static final int PSK = 0;
        public static final int PSK_SAE = 1;
        public static final int SAE = 2;
        public static final int DPP = 3;
    }

    protected static class DppCurve {
        public static final int PRIME256V1 = 0;
        public static final int SECP384R1 = 1;
        public static final int SECP521R1 = 2;
        public static final int BRAINPOOLP256R1 = 3;
        public static final int BRAINPOOLP384R1 = 4;
        public static final int BRAINPOOLP512R1 = 5;
    }

    protected static class DppNetRole {
        public static final int STA = 0;
        public static final int AP = 1;
    }

    protected static class DppEventType {
        public static final int CONFIGURATION_SENT = 0;
        public static final int CONFIGURATION_APPLIED = 1;
    }

    protected static class DppFailureCode {
        public static final int INVALID_URI = 0;
        public static final int AUTHENTICATION = 1;
        public static final int NOT_COMPATIBLE = 2;
        public static final int CONFIGURATION = 3;
        public static final int BUSY = 4;
        public static final int TIMEOUT = 5;
        public static final int FAILURE = 6;
        public static final int NOT_SUPPORTED = 7;
        public static final int CONFIGURATION_REJECTED = 8;
        public static final int CANNOT_FIND_NETWORK = 9;
        public static final int ENROLLEE_AUTHENTICATION = 10;
        public static final int URI_GENERATION = 11;
    }

    protected static class DppProgressCode {
        public static final int AUTHENTICATION_SUCCESS = 0;
        public static final int RESPONSE_PENDING = 1;
        public static final int CONFIGURATION_SENT_WAITING_RESPONSE = 2;
        public static final int CONFIGURATION_ACCEPTED = 3;
    }

    protected static class MboAssocDisallowedReasonCode {
        public static final byte RESERVED = 0;
        public static final byte UNSPECIFIED = 1;
        public static final byte MAX_NUM_STA_ASSOCIATED = 2;
        public static final byte AIR_INTERFACE_OVERLOADED = 3;
        public static final byte AUTH_SERVER_OVERLOADED = 4;
        public static final byte INSUFFICIENT_RSSI = 5;
    }

    protected static class StaIfaceReasonCode {
        public static final int UNSPECIFIED = 1;
        public static final int PREV_AUTH_NOT_VALID = 2;
        public static final int DEAUTH_LEAVING = 3;
        public static final int DISASSOC_DUE_TO_INACTIVITY = 4;
        public static final int DISASSOC_AP_BUSY = 5;
        public static final int CLASS2_FRAME_FROM_NONAUTH_STA = 6;
        public static final int CLASS3_FRAME_FROM_NONASSOC_STA = 7;
        public static final int DISASSOC_STA_HAS_LEFT = 8;
        public static final int STA_REQ_ASSOC_WITHOUT_AUTH = 9;
        public static final int PWR_CAPABILITY_NOT_VALID = 10;
        public static final int SUPPORTED_CHANNEL_NOT_VALID = 11;
        public static final int BSS_TRANSITION_DISASSOC = 12;
        public static final int INVALID_IE = 13;
        public static final int MICHAEL_MIC_FAILURE = 14;
        public static final int FOURWAY_HANDSHAKE_TIMEOUT = 15;
        public static final int GROUP_KEY_UPDATE_TIMEOUT = 16;
        public static final int IE_IN_4WAY_DIFFERS = 17;
        public static final int GROUP_CIPHER_NOT_VALID = 18;
        public static final int PAIRWISE_CIPHER_NOT_VALID = 19;
        public static final int AKMP_NOT_VALID = 20;
        public static final int UNSUPPORTED_RSN_IE_VERSION = 21;
        public static final int INVALID_RSN_IE_CAPAB = 22;
        public static final int IEEE_802_1X_AUTH_FAILED = 23;
        public static final int CIPHER_SUITE_REJECTED = 24;
        public static final int TDLS_TEARDOWN_UNREACHABLE = 25;
        public static final int TDLS_TEARDOWN_UNSPECIFIED = 26;
        public static final int SSP_REQUESTED_DISASSOC = 27;
        public static final int NO_SSP_ROAMING_AGREEMENT = 28;
        public static final int BAD_CIPHER_OR_AKM = 29;
        public static final int NOT_AUTHORIZED_THIS_LOCATION = 30;
        public static final int SERVICE_CHANGE_PRECLUDES_TS = 31;
        public static final int UNSPECIFIED_QOS_REASON = 32;
        public static final int NOT_ENOUGH_BANDWIDTH = 33;
        public static final int DISASSOC_LOW_ACK = 34;
        public static final int EXCEEDED_TXOP = 35;
        public static final int STA_LEAVING = 36;
        public static final int END_TS_BA_DLS = 37;
        public static final int UNKNOWN_TS_BA = 38;
        public static final int TIMEOUT = 39;
        public static final int PEERKEY_MISMATCH = 45;
        public static final int AUTHORIZED_ACCESS_LIMIT_REACHED = 46;
        public static final int EXTERNAL_SERVICE_REQUIREMENTS = 47;
        public static final int INVALID_FT_ACTION_FRAME_COUNT = 48;
        public static final int INVALID_PMKID = 49;
        public static final int INVALID_MDE = 50;
        public static final int INVALID_FTE = 51;
        public static final int MESH_PEERING_CANCELLED = 52;
        public static final int MESH_MAX_PEERS = 53;
        public static final int MESH_CONFIG_POLICY_VIOLATION = 54;
        public static final int MESH_CLOSE_RCVD = 55;
        public static final int MESH_MAX_RETRIES = 56;
        public static final int MESH_CONFIRM_TIMEOUT = 57;
        public static final int MESH_INVALID_GTK = 58;
        public static final int MESH_INCONSISTENT_PARAMS = 59;
        public static final int MESH_INVALID_SECURITY_CAP = 60;
        public static final int MESH_PATH_ERROR_NO_PROXY_INFO = 61;
        public static final int MESH_PATH_ERROR_NO_FORWARDING_INFO = 62;
        public static final int MESH_PATH_ERROR_DEST_UNREACHABLE = 63;
        public static final int MAC_ADDRESS_ALREADY_EXISTS_IN_MBSS = 64;
        public static final int MESH_CHANNEL_SWITCH_REGULATORY_REQ = 65;
        public static final int MESH_CHANNEL_SWITCH_UNSPECIFIED = 66;

        public static String toString(int code) {
            switch(code) {
                case UNSPECIFIED:
                    return "UNSPECIFIED";
                case PREV_AUTH_NOT_VALID:
                    return "PREV_AUTH_NOT_VALID";
                case DEAUTH_LEAVING:
                    return "DEAUTH_LEAVING";
                case DISASSOC_DUE_TO_INACTIVITY:
                    return "DISASSOC_DUE_TO_INACTIVITY";
                case DISASSOC_AP_BUSY:
                    return "DISASSOC_AP_BUSY";
                case CLASS2_FRAME_FROM_NONAUTH_STA:
                    return "CLASS2_FRAME_FROM_NONAUTH_STA";
                case CLASS3_FRAME_FROM_NONASSOC_STA:
                    return "CLASS3_FRAME_FROM_NONASSOC_STA";
                case DISASSOC_STA_HAS_LEFT:
                    return "DISASSOC_STA_HAS_LEFT";
                case STA_REQ_ASSOC_WITHOUT_AUTH:
                    return "STA_REQ_ASSOC_WITHOUT_AUTH";
                case PWR_CAPABILITY_NOT_VALID:
                    return "PWR_CAPABILITY_NOT_VALID";
                case SUPPORTED_CHANNEL_NOT_VALID:
                    return "SUPPORTED_CHANNEL_NOT_VALID";
                case BSS_TRANSITION_DISASSOC:
                    return "BSS_TRANSITION_DISASSOC";
                case INVALID_IE:
                    return "INVALID_IE";
                case MICHAEL_MIC_FAILURE:
                    return "MICHAEL_MIC_FAILURE";
                case FOURWAY_HANDSHAKE_TIMEOUT:
                    return "FOURWAY_HANDSHAKE_TIMEOUT";
                case GROUP_KEY_UPDATE_TIMEOUT:
                    return "GROUP_KEY_UPDATE_TIMEOUT";
                case IE_IN_4WAY_DIFFERS:
                    return "IE_IN_4WAY_DIFFERS";
                case GROUP_CIPHER_NOT_VALID:
                    return "GROUP_CIPHER_NOT_VALID";
                case PAIRWISE_CIPHER_NOT_VALID:
                    return "PAIRWISE_CIPHER_NOT_VALID";
                case AKMP_NOT_VALID:
                    return "AKMP_NOT_VALID";
                case UNSUPPORTED_RSN_IE_VERSION:
                    return "UNSUPPORTED_RSN_IE_VERSION";
                case INVALID_RSN_IE_CAPAB:
                    return "INVALID_RSN_IE_CAPAB";
                case IEEE_802_1X_AUTH_FAILED:
                    return "IEEE_802_1X_AUTH_FAILED";
                case CIPHER_SUITE_REJECTED:
                    return "CIPHER_SUITE_REJECTED";
                case TDLS_TEARDOWN_UNREACHABLE:
                    return "TDLS_TEARDOWN_UNREACHABLE";
                case TDLS_TEARDOWN_UNSPECIFIED:
                    return "TDLS_TEARDOWN_UNSPECIFIED";
                case SSP_REQUESTED_DISASSOC:
                    return "SSP_REQUESTED_DISASSOC";
                case NO_SSP_ROAMING_AGREEMENT:
                    return "NO_SSP_ROAMING_AGREEMENT";
                case BAD_CIPHER_OR_AKM:
                    return "BAD_CIPHER_OR_AKM";
                case NOT_AUTHORIZED_THIS_LOCATION:
                    return "NOT_AUTHORIZED_THIS_LOCATION";
                case SERVICE_CHANGE_PRECLUDES_TS:
                    return "SERVICE_CHANGE_PRECLUDES_TS";
                case UNSPECIFIED_QOS_REASON:
                    return "UNSPECIFIED_QOS_REASON";
                case NOT_ENOUGH_BANDWIDTH:
                    return "NOT_ENOUGH_BANDWIDTH";
                case DISASSOC_LOW_ACK:
                    return "DISASSOC_LOW_ACK";
                case EXCEEDED_TXOP:
                    return "EXCEEDED_TXOP";
                case STA_LEAVING:
                    return "STA_LEAVING";
                case END_TS_BA_DLS:
                    return "END_TS_BA_DLS";
                case UNKNOWN_TS_BA:
                    return "UNKNOWN_TS_BA";
                case TIMEOUT:
                    return "TIMEOUT";
                case PEERKEY_MISMATCH:
                    return "PEERKEY_MISMATCH";
                case AUTHORIZED_ACCESS_LIMIT_REACHED:
                    return "AUTHORIZED_ACCESS_LIMIT_REACHED";
                case EXTERNAL_SERVICE_REQUIREMENTS:
                    return "EXTERNAL_SERVICE_REQUIREMENTS";
                case INVALID_FT_ACTION_FRAME_COUNT:
                    return "INVALID_FT_ACTION_FRAME_COUNT";
                case INVALID_PMKID:
                    return "INVALID_PMKID";
                case INVALID_MDE:
                    return "INVALID_MDE";
                case INVALID_FTE:
                    return "INVALID_FTE";
                case MESH_PEERING_CANCELLED:
                    return "MESH_PEERING_CANCELLED";
                case MESH_MAX_PEERS:
                    return "MESH_MAX_PEERS";
                case MESH_CONFIG_POLICY_VIOLATION:
                    return "MESH_CONFIG_POLICY_VIOLATION";
                case MESH_CLOSE_RCVD:
                    return "MESH_CLOSE_RCVD";
                case MESH_MAX_RETRIES:
                    return "MESH_MAX_RETRIES";
                case MESH_CONFIRM_TIMEOUT:
                    return "MESH_CONFIRM_TIMEOUT";
                case MESH_INVALID_GTK:
                    return "MESH_INVALID_GTK";
                case MESH_INCONSISTENT_PARAMS:
                    return "MESH_INCONSISTENT_PARAMS";
                case MESH_INVALID_SECURITY_CAP:
                    return "MESH_INVALID_SECURITY_CAP";
                case MESH_PATH_ERROR_NO_PROXY_INFO:
                    return "MESH_PATH_ERROR_NO_PROXY_INFO";
                case MESH_PATH_ERROR_NO_FORWARDING_INFO:
                    return "MESH_PATH_ERROR_NO_FORWARDING_INFO";
                case MESH_PATH_ERROR_DEST_UNREACHABLE:
                    return "MESH_PATH_ERROR_DEST_UNREACHABLE";
                case MAC_ADDRESS_ALREADY_EXISTS_IN_MBSS:
                    return "MAC_ADDRESS_ALREADY_EXISTS_IN_MBSS";
                case MESH_CHANNEL_SWITCH_REGULATORY_REQ:
                    return "MESH_CHANNEL_SWITCH_REGULATORY_REQ";
                case MESH_CHANNEL_SWITCH_UNSPECIFIED:
                    return "MESH_CHANNEL_SWITCH_UNSPECIFIED";
                default:
                    return "Unknown StaIfaceReasonCode: " + code;
            }
        }
    }

    protected static class StaIfaceStatusCode {
        public static final int SUCCESS = 0;
        public static final int UNSPECIFIED_FAILURE = 1;
        public static final int TDLS_WAKEUP_ALTERNATE = 2;
        public static final int TDLS_WAKEUP_REJECT = 3;
        public static final int SECURITY_DISABLED = 5;
        public static final int UNACCEPTABLE_LIFETIME = 6;
        public static final int NOT_IN_SAME_BSS = 7;
        public static final int CAPS_UNSUPPORTED = 10;
        public static final int REASSOC_NO_ASSOC = 11;
        public static final int ASSOC_DENIED_UNSPEC = 12;
        public static final int NOT_SUPPORTED_AUTH_ALG = 13;
        public static final int UNKNOWN_AUTH_TRANSACTION = 14;
        public static final int CHALLENGE_FAIL = 15;
        public static final int AUTH_TIMEOUT = 16;
        public static final int AP_UNABLE_TO_HANDLE_NEW_STA = 17;
        public static final int ASSOC_DENIED_RATES = 18;
        public static final int ASSOC_DENIED_NOSHORT = 19;
        public static final int SPEC_MGMT_REQUIRED = 22;
        public static final int PWR_CAPABILITY_NOT_VALID = 23;
        public static final int SUPPORTED_CHANNEL_NOT_VALID = 24;
        public static final int ASSOC_DENIED_NO_SHORT_SLOT_TIME = 25;
        public static final int ASSOC_DENIED_NO_HT = 27;
        public static final int R0KH_UNREACHABLE = 28;
        public static final int ASSOC_DENIED_NO_PCO = 29;
        public static final int ASSOC_REJECTED_TEMPORARILY = 30;
        public static final int ROBUST_MGMT_FRAME_POLICY_VIOLATION = 31;
        public static final int UNSPECIFIED_QOS_FAILURE = 32;
        public static final int DENIED_INSUFFICIENT_BANDWIDTH = 33;
        public static final int DENIED_POOR_CHANNEL_CONDITIONS = 34;
        public static final int DENIED_QOS_NOT_SUPPORTED = 35;
        public static final int REQUEST_DECLINED = 37;
        public static final int INVALID_PARAMETERS = 38;
        public static final int REJECTED_WITH_SUGGESTED_CHANGES = 39;
        public static final int INVALID_IE = 40;
        public static final int GROUP_CIPHER_NOT_VALID = 41;
        public static final int PAIRWISE_CIPHER_NOT_VALID = 42;
        public static final int AKMP_NOT_VALID = 43;
        public static final int UNSUPPORTED_RSN_IE_VERSION = 44;
        public static final int INVALID_RSN_IE_CAPAB = 45;
        public static final int CIPHER_REJECTED_PER_POLICY = 46;
        public static final int TS_NOT_CREATED = 47;
        public static final int DIRECT_LINK_NOT_ALLOWED = 48;
        public static final int DEST_STA_NOT_PRESENT = 49;
        public static final int DEST_STA_NOT_QOS_STA = 50;
        public static final int ASSOC_DENIED_LISTEN_INT_TOO_LARGE = 51;
        public static final int INVALID_FT_ACTION_FRAME_COUNT = 52;
        public static final int INVALID_PMKID = 53;
        public static final int INVALID_MDIE = 54;
        public static final int INVALID_FTIE = 55;
        public static final int REQUESTED_TCLAS_NOT_SUPPORTED = 56;
        public static final int INSUFFICIENT_TCLAS_PROCESSING_RESOURCES = 57;
        public static final int TRY_ANOTHER_BSS = 58;
        public static final int GAS_ADV_PROTO_NOT_SUPPORTED = 59;
        public static final int NO_OUTSTANDING_GAS_REQ = 60;
        public static final int GAS_RESP_NOT_RECEIVED = 61;
        public static final int STA_TIMED_OUT_WAITING_FOR_GAS_RESP = 62;
        public static final int GAS_RESP_LARGER_THAN_LIMIT = 63;
        public static final int REQ_REFUSED_HOME = 64;
        public static final int ADV_SRV_UNREACHABLE = 65;
        public static final int REQ_REFUSED_SSPN = 67;
        public static final int REQ_REFUSED_UNAUTH_ACCESS = 68;
        public static final int INVALID_RSNIE = 72;
        public static final int U_APSD_COEX_NOT_SUPPORTED = 73;
        public static final int U_APSD_COEX_MODE_NOT_SUPPORTED = 74;
        public static final int BAD_INTERVAL_WITH_U_APSD_COEX = 75;
        public static final int ANTI_CLOGGING_TOKEN_REQ = 76;
        public static final int FINITE_CYCLIC_GROUP_NOT_SUPPORTED = 77;
        public static final int CANNOT_FIND_ALT_TBTT = 78;
        public static final int TRANSMISSION_FAILURE = 79;
        public static final int REQ_TCLAS_NOT_SUPPORTED = 80;
        public static final int TCLAS_RESOURCES_EXCHAUSTED = 81;
        public static final int REJECTED_WITH_SUGGESTED_BSS_TRANSITION = 82;
        public static final int REJECT_WITH_SCHEDULE = 83;
        public static final int REJECT_NO_WAKEUP_SPECIFIED = 84;
        public static final int SUCCESS_POWER_SAVE_MODE = 85;
        public static final int PENDING_ADMITTING_FST_SESSION = 86;
        public static final int PERFORMING_FST_NOW = 87;
        public static final int PENDING_GAP_IN_BA_WINDOW = 88;
        public static final int REJECT_U_PID_SETTING = 89;
        public static final int REFUSED_EXTERNAL_REASON = 92;
        public static final int REFUSED_AP_OUT_OF_MEMORY = 93;
        public static final int REJECTED_EMERGENCY_SERVICE_NOT_SUPPORTED = 94;
        public static final int QUERY_RESP_OUTSTANDING = 95;
        public static final int REJECT_DSE_BAND = 96;
        public static final int TCLAS_PROCESSING_TERMINATED = 97;
        public static final int TS_SCHEDULE_CONFLICT = 98;
        public static final int DENIED_WITH_SUGGESTED_BAND_AND_CHANNEL = 99;
        public static final int MCCAOP_RESERVATION_CONFLICT = 100;
        public static final int MAF_LIMIT_EXCEEDED = 101;
        public static final int MCCA_TRACK_LIMIT_EXCEEDED = 102;
        public static final int DENIED_DUE_TO_SPECTRUM_MANAGEMENT = 103;
        public static final int ASSOC_DENIED_NO_VHT = 104;
        public static final int ENABLEMENT_DENIED = 105;
        public static final int RESTRICTION_FROM_AUTHORIZED_GDB = 106;
        public static final int AUTHORIZATION_DEENABLED = 107;
        public static final int FILS_AUTHENTICATION_FAILURE = 112;
        public static final int UNKNOWN_AUTHENTICATION_SERVER = 113;

        public static String toString(int code) {
            switch(code) {
                case SUCCESS:
                    return "SUCCESS";
                case UNSPECIFIED_FAILURE:
                    return "UNSPECIFIED_FAILURE";
                case TDLS_WAKEUP_ALTERNATE:
                    return "TDLS_WAKEUP_ALTERNATE";
                case TDLS_WAKEUP_REJECT:
                    return "TDLS_WAKEUP_REJECT";
                case SECURITY_DISABLED:
                    return "SECURITY_DISABLED";
                case UNACCEPTABLE_LIFETIME:
                    return "UNACCEPTABLE_LIFETIME";
                case NOT_IN_SAME_BSS:
                    return "NOT_IN_SAME_BSS";
                case CAPS_UNSUPPORTED:
                    return "CAPS_UNSUPPORTED";
                case REASSOC_NO_ASSOC:
                    return "REASSOC_NO_ASSOC";
                case ASSOC_DENIED_UNSPEC:
                    return "ASSOC_DENIED_UNSPEC";
                case NOT_SUPPORTED_AUTH_ALG:
                    return "NOT_SUPPORTED_AUTH_ALG";
                case UNKNOWN_AUTH_TRANSACTION:
                    return "UNKNOWN_AUTH_TRANSACTION";
                case CHALLENGE_FAIL:
                    return "CHALLENGE_FAIL";
                case AUTH_TIMEOUT:
                    return "AUTH_TIMEOUT";
                case AP_UNABLE_TO_HANDLE_NEW_STA:
                    return "AP_UNABLE_TO_HANDLE_NEW_STA";
                case ASSOC_DENIED_RATES:
                    return "ASSOC_DENIED_RATES";
                case ASSOC_DENIED_NOSHORT:
                    return "ASSOC_DENIED_NOSHORT";
                case SPEC_MGMT_REQUIRED:
                    return "SPEC_MGMT_REQUIRED";
                case PWR_CAPABILITY_NOT_VALID:
                    return "PWR_CAPABILITY_NOT_VALID";
                case SUPPORTED_CHANNEL_NOT_VALID:
                    return "SUPPORTED_CHANNEL_NOT_VALID";
                case ASSOC_DENIED_NO_SHORT_SLOT_TIME:
                    return "ASSOC_DENIED_NO_SHORT_SLOT_TIME";
                case ASSOC_DENIED_NO_HT:
                    return "ASSOC_DENIED_NO_HT";
                case R0KH_UNREACHABLE:
                    return "R0KH_UNREACHABLE";
                case ASSOC_DENIED_NO_PCO:
                    return "ASSOC_DENIED_NO_PCO";
                case ASSOC_REJECTED_TEMPORARILY:
                    return "ASSOC_REJECTED_TEMPORARILY";
                case ROBUST_MGMT_FRAME_POLICY_VIOLATION:
                    return "ROBUST_MGMT_FRAME_POLICY_VIOLATION";
                case UNSPECIFIED_QOS_FAILURE:
                    return "UNSPECIFIED_QOS_FAILURE";
                case DENIED_INSUFFICIENT_BANDWIDTH:
                    return "DENIED_INSUFFICIENT_BANDWIDTH";
                case DENIED_POOR_CHANNEL_CONDITIONS:
                    return "DENIED_POOR_CHANNEL_CONDITIONS";
                case DENIED_QOS_NOT_SUPPORTED:
                    return "DENIED_QOS_NOT_SUPPORTED";
                case REQUEST_DECLINED:
                    return "REQUEST_DECLINED";
                case INVALID_PARAMETERS:
                    return "INVALID_PARAMETERS";
                case REJECTED_WITH_SUGGESTED_CHANGES:
                    return "REJECTED_WITH_SUGGESTED_CHANGES";
                case INVALID_IE:
                    return "INVALID_IE";
                case GROUP_CIPHER_NOT_VALID:
                    return "GROUP_CIPHER_NOT_VALID";
                case PAIRWISE_CIPHER_NOT_VALID:
                    return "PAIRWISE_CIPHER_NOT_VALID";
                case AKMP_NOT_VALID:
                    return "AKMP_NOT_VALID";
                case UNSUPPORTED_RSN_IE_VERSION:
                    return "UNSUPPORTED_RSN_IE_VERSION";
                case INVALID_RSN_IE_CAPAB:
                    return "INVALID_RSN_IE_CAPAB";
                case CIPHER_REJECTED_PER_POLICY:
                    return "CIPHER_REJECTED_PER_POLICY";
                case TS_NOT_CREATED:
                    return "TS_NOT_CREATED";
                case DIRECT_LINK_NOT_ALLOWED:
                    return "DIRECT_LINK_NOT_ALLOWED";
                case DEST_STA_NOT_PRESENT:
                    return "DEST_STA_NOT_PRESENT";
                case DEST_STA_NOT_QOS_STA:
                    return "DEST_STA_NOT_QOS_STA";
                case ASSOC_DENIED_LISTEN_INT_TOO_LARGE:
                    return "ASSOC_DENIED_LISTEN_INT_TOO_LARGE";
                case INVALID_FT_ACTION_FRAME_COUNT:
                    return "INVALID_FT_ACTION_FRAME_COUNT";
                case INVALID_PMKID:
                    return "INVALID_PMKID";
                case INVALID_MDIE:
                    return "INVALID_MDIE";
                case INVALID_FTIE:
                    return "INVALID_FTIE";
                case REQUESTED_TCLAS_NOT_SUPPORTED:
                    return "REQUESTED_TCLAS_NOT_SUPPORTED";
                case INSUFFICIENT_TCLAS_PROCESSING_RESOURCES:
                    return "INSUFFICIENT_TCLAS_PROCESSING_RESOURCES";
                case TRY_ANOTHER_BSS:
                    return "TRY_ANOTHER_BSS";
                case GAS_ADV_PROTO_NOT_SUPPORTED:
                    return "GAS_ADV_PROTO_NOT_SUPPORTED";
                case NO_OUTSTANDING_GAS_REQ:
                    return "NO_OUTSTANDING_GAS_REQ";
                case GAS_RESP_NOT_RECEIVED:
                    return "GAS_RESP_NOT_RECEIVED";
                case STA_TIMED_OUT_WAITING_FOR_GAS_RESP:
                    return "STA_TIMED_OUT_WAITING_FOR_GAS_RESP";
                case GAS_RESP_LARGER_THAN_LIMIT:
                    return "GAS_RESP_LARGER_THAN_LIMIT";
                case REQ_REFUSED_HOME:
                    return "REQ_REFUSED_HOME";
                case ADV_SRV_UNREACHABLE:
                    return "ADV_SRV_UNREACHABLE";
                case REQ_REFUSED_SSPN:
                    return "REQ_REFUSED_SSPN";
                case REQ_REFUSED_UNAUTH_ACCESS:
                    return "REQ_REFUSED_UNAUTH_ACCESS";
                case INVALID_RSNIE:
                    return "INVALID_RSNIE";
                case U_APSD_COEX_NOT_SUPPORTED:
                    return "U_APSD_COEX_NOT_SUPPORTED";
                case U_APSD_COEX_MODE_NOT_SUPPORTED:
                    return "U_APSD_COEX_MODE_NOT_SUPPORTED";
                case BAD_INTERVAL_WITH_U_APSD_COEX:
                    return "BAD_INTERVAL_WITH_U_APSD_COEX";
                case ANTI_CLOGGING_TOKEN_REQ:
                    return "ANTI_CLOGGING_TOKEN_REQ";
                case FINITE_CYCLIC_GROUP_NOT_SUPPORTED:
                    return "FINITE_CYCLIC_GROUP_NOT_SUPPORTED";
                case CANNOT_FIND_ALT_TBTT:
                    return "CANNOT_FIND_ALT_TBTT";
                case TRANSMISSION_FAILURE:
                    return "TRANSMISSION_FAILURE";
                case REQ_TCLAS_NOT_SUPPORTED:
                    return "REQ_TCLAS_NOT_SUPPORTED";
                case TCLAS_RESOURCES_EXCHAUSTED:
                    return "TCLAS_RESOURCES_EXCHAUSTED";
                case REJECTED_WITH_SUGGESTED_BSS_TRANSITION:
                    return "REJECTED_WITH_SUGGESTED_BSS_TRANSITION";
                case REJECT_WITH_SCHEDULE:
                    return "REJECT_WITH_SCHEDULE";
                case REJECT_NO_WAKEUP_SPECIFIED:
                    return "REJECT_NO_WAKEUP_SPECIFIED";
                case SUCCESS_POWER_SAVE_MODE:
                    return "SUCCESS_POWER_SAVE_MODE";
                case PENDING_ADMITTING_FST_SESSION:
                    return "PENDING_ADMITTING_FST_SESSION";
                case PERFORMING_FST_NOW:
                    return "PERFORMING_FST_NOW";
                case PENDING_GAP_IN_BA_WINDOW:
                    return "PENDING_GAP_IN_BA_WINDOW";
                case REJECT_U_PID_SETTING:
                    return "REJECT_U_PID_SETTING";
                case REFUSED_EXTERNAL_REASON:
                    return "REFUSED_EXTERNAL_REASON";
                case REFUSED_AP_OUT_OF_MEMORY:
                    return "REFUSED_AP_OUT_OF_MEMORY";
                case REJECTED_EMERGENCY_SERVICE_NOT_SUPPORTED:
                    return "REJECTED_EMERGENCY_SERVICE_NOT_SUPPORTED";
                case QUERY_RESP_OUTSTANDING:
                    return "QUERY_RESP_OUTSTANDING";
                case REJECT_DSE_BAND:
                    return "REJECT_DSE_BAND";
                case TCLAS_PROCESSING_TERMINATED:
                    return "TCLAS_PROCESSING_TERMINATED";
                case TS_SCHEDULE_CONFLICT:
                    return "TS_SCHEDULE_CONFLICT";
                case DENIED_WITH_SUGGESTED_BAND_AND_CHANNEL:
                    return "DENIED_WITH_SUGGESTED_BAND_AND_CHANNEL";
                case MCCAOP_RESERVATION_CONFLICT:
                    return "MCCAOP_RESERVATION_CONFLICT";
                case MAF_LIMIT_EXCEEDED:
                    return "MAF_LIMIT_EXCEEDED";
                case MCCA_TRACK_LIMIT_EXCEEDED:
                    return "MCCA_TRACK_LIMIT_EXCEEDED";
                case DENIED_DUE_TO_SPECTRUM_MANAGEMENT:
                    return "DENIED_DUE_TO_SPECTRUM_MANAGEMENT";
                case ASSOC_DENIED_NO_VHT:
                    return "ASSOC_DENIED_NO_VHT";
                case ENABLEMENT_DENIED:
                    return "ENABLEMENT_DENIED";
                case RESTRICTION_FROM_AUTHORIZED_GDB:
                    return "RESTRICTION_FROM_AUTHORIZED_GDB";
                case AUTHORIZATION_DEENABLED:
                    return "AUTHORIZATION_DEENABLED";
                case FILS_AUTHENTICATION_FAILURE:
                    return "FILS_AUTHENTICATION_FAILURE";
                case UNKNOWN_AUTHENTICATION_SERVER:
                    return "UNKNOWN_AUTHENTICATION_SERVER";
                default:
                    return "Unknown StaIfaceStatusCode: " + code;
            }
        }
    }

    protected static final int SUPPLICANT_EVENT_CONNECTED = 0;
    protected static final int SUPPLICANT_EVENT_DISCONNECTED = 1;
    protected static final int SUPPLICANT_EVENT_ASSOCIATING = 2;
    protected static final int SUPPLICANT_EVENT_ASSOCIATED = 3;
    protected static final int SUPPLICANT_EVENT_EAP_METHOD_SELECTED = 4;
    protected static final int SUPPLICANT_EVENT_EAP_FAILURE = 5;
    protected static final int SUPPLICANT_EVENT_SSID_TEMP_DISABLED = 6;
    protected static final int SUPPLICANT_EVENT_OPEN_SSL_FAILURE = 7;

    @IntDef(prefix = { "SUPPLICANT_EVENT_" }, value = {
            SUPPLICANT_EVENT_CONNECTED,
            SUPPLICANT_EVENT_DISCONNECTED,
            SUPPLICANT_EVENT_ASSOCIATING,
            SUPPLICANT_EVENT_ASSOCIATED,
            SUPPLICANT_EVENT_EAP_METHOD_SELECTED,
            SUPPLICANT_EVENT_EAP_FAILURE,
            SUPPLICANT_EVENT_SSID_TEMP_DISABLED,
            SUPPLICANT_EVENT_OPEN_SSL_FAILURE,
    })
    @Retention(RetentionPolicy.SOURCE)
    protected @interface SupplicantEventCode {}

    protected static String supplicantEventCodeToString(@SupplicantEventCode int eventCode) {
        switch (eventCode) {
            case SUPPLICANT_EVENT_CONNECTED:
                return "CONNECTED";
            case SUPPLICANT_EVENT_DISCONNECTED:
                return "DISCONNECTED";
            case SUPPLICANT_EVENT_ASSOCIATING:
                return "ASSOCIATING";
            case SUPPLICANT_EVENT_ASSOCIATED:
                return "ASSOCIATED";
            case SUPPLICANT_EVENT_EAP_METHOD_SELECTED:
                return "EAP_METHOD_SELECTED";
            case SUPPLICANT_EVENT_EAP_FAILURE:
                return "EAP_FAILURE";
            case SUPPLICANT_EVENT_SSID_TEMP_DISABLED:
                return "SSID_TEMP_DISABLED";
            case SUPPLICANT_EVENT_OPEN_SSL_FAILURE:
                return "OPEN_SSL_FAILURE";
            default:
                return "Invalid SupplicantEventCode: " + eventCode;
        }
    }

    protected static final int QOS_POLICY_REQUEST_ADD = 0;
    protected static final int QOS_POLICY_REQUEST_REMOVE = 1;

    @IntDef(prefix = { "QOS_POLICY_REQUEST_" }, value = {
            QOS_POLICY_REQUEST_ADD,
            QOS_POLICY_REQUEST_REMOVE
    })
    @Retention(RetentionPolicy.SOURCE)
    protected @interface QosPolicyRequestType {}

    protected static class QosPolicyRequest {
        public final byte policyId;
        public final @QosPolicyRequestType int requestType;
        public final byte dscp;
        public final QosPolicyClassifierParams classifierParams;

        public QosPolicyRequest(byte halPolicyId, @QosPolicyRequestType int halRequestType,
                byte halDscp, @NonNull QosPolicyClassifierParams frameworkClassifierParams) {
            policyId = halPolicyId;
            dscp = halDscp;
            requestType = halRequestType;
            classifierParams = frameworkClassifierParams;
        }

        public boolean isAddRequest() {
            return requestType == QOS_POLICY_REQUEST_ADD;
        }

        public boolean isRemoveRequest() {
            return requestType == QOS_POLICY_REQUEST_REMOVE;
        }

        @Override
        public String toString() {
            return "policyId: " + policyId + ", isAddRequest: " + this.isAddRequest()
                    + ", isRemoveRequest: " + this.isRemoveRequest() + ", dscp: " + dscp
                    + ", classifierParams: {" + classifierParams + "}";
        }
    }

    protected static class QosPolicyClassifierParams {
        public InetAddress srcIp = null;
        public InetAddress dstIp = null;
        public Range dstPortRange = null;
        public final int srcPort;
        public final int protocol;

        public final boolean hasSrcIp;
        public final boolean hasDstIp;
        public boolean isValid = true;

        public QosPolicyClassifierParams(boolean halHasSrcIp, byte[] halSrcIp, boolean halHasDstIp,
                byte[] halDstIp, int halSrcPort, int[] halDstPortRange, int halProtocol) {
            srcPort = halSrcPort;
            protocol = halProtocol;

            hasSrcIp = halHasSrcIp;
            if (hasSrcIp) {
                try {
                    srcIp = InetAddress.getByAddress(halSrcIp);
                } catch (UnknownHostException e) {
                    isValid = false;
                }
            }

            hasDstIp = halHasDstIp;
            if (hasDstIp) {
                try {
                    dstIp = InetAddress.getByAddress(halDstIp);
                } catch (UnknownHostException e) {
                    isValid = false;
                }
            }

            if (halDstPortRange != null) {
                if (halDstPortRange[0] > halDstPortRange[1]) {
                    isValid = false;
                } else {
                    dstPortRange = new Range(halDstPortRange[0], halDstPortRange[1]);
                }
            }
        }

        @Override
        public String toString() {
            return "isValid: " + isValid + ", hasSrcIp: " + hasSrcIp + ", hasDstIp: " + hasDstIp
                    + ", srcIp: " + srcIp + ", dstIp: " + dstIp + ", dstPortRange: " + dstPortRange
                    + ", srcPort: " + srcPort + ", protocol: " + protocol;
        }
    }

    protected static class QosPolicyStatus {
        public final int policyId;
        public final int statusCode;

        public QosPolicyStatus(int id, int status) {
            policyId = id;
            statusCode = status;
        }

        @Override
        public String toString() {
            return "{policyId: " + policyId + ", statusCode: " + statusCode + "}";
        }
    }

    protected static final int QOS_POLICY_SCS_REQUEST_STATUS_ERROR_UNKNOWN = -1;
    protected static final int QOS_POLICY_SCS_REQUEST_STATUS_SENT = 0;
    protected static final int QOS_POLICY_SCS_REQUEST_STATUS_ALREADY_ACTIVE = 1;
    protected static final int QOS_POLICY_SCS_REQUEST_STATUS_NOT_EXIST = 2;
    protected static final int QOS_POLICY_SCS_REQUEST_STATUS_INVALID = 3;

    @IntDef(prefix = { "QOS_POLICY_SCS_REQUEST_STATUS_" }, value = {
            QOS_POLICY_SCS_REQUEST_STATUS_ERROR_UNKNOWN,
            QOS_POLICY_SCS_REQUEST_STATUS_SENT,
            QOS_POLICY_SCS_REQUEST_STATUS_ALREADY_ACTIVE,
            QOS_POLICY_SCS_REQUEST_STATUS_NOT_EXIST,
            QOS_POLICY_SCS_REQUEST_STATUS_INVALID
    })
    @Retention(RetentionPolicy.SOURCE)
    protected @interface QosPolicyScsRequestStatusCode {}

    protected static final int QOS_POLICY_SCS_RESPONSE_STATUS_ERROR_UNKNOWN = -1;
    protected static final int QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS = 0;
    protected static final int QOS_POLICY_SCS_RESPONSE_STATUS_TCLAS_REQUEST_DECLINED = 1;
    protected static final int QOS_POLICY_SCS_RESPONSE_STATUS_TCLAS_NOT_SUPPORTED_BY_AP = 2;
    protected static final int QOS_POLICY_SCS_RESPONSE_STATUS_TCLAS_INSUFFICIENT_RESOURCES = 3;
    protected static final int QOS_POLICY_SCS_RESPONSE_STATUS_TCLAS_RESOURCES_EXHAUSTED = 4;
    protected static final int
            QOS_POLICY_SCS_RESPONSE_STATUS_TCLAS_PROCESSING_TERMINATED_INSUFFICIENT_QOS = 5;
    protected static final int
            QOS_POLICY_SCS_RESPONSE_STATUS_TCLAS_PROCESSING_TERMINATED_POLICY_CONFLICT = 6;
    protected static final int QOS_POLICY_SCS_RESPONSE_STATUS_TCLAS_PROCESSING_TERMINATED = 7;
    protected static final int QOS_POLICY_SCS_RESPONSE_STATUS_TIMEOUT = 8;

    @IntDef(prefix = { "QOS_POLICY_SCS_RESPONSE_STATUS_" }, value = {
            QOS_POLICY_SCS_RESPONSE_STATUS_ERROR_UNKNOWN,
            QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS,
            QOS_POLICY_SCS_RESPONSE_STATUS_TCLAS_REQUEST_DECLINED,
            QOS_POLICY_SCS_RESPONSE_STATUS_TCLAS_NOT_SUPPORTED_BY_AP,
            QOS_POLICY_SCS_RESPONSE_STATUS_TCLAS_INSUFFICIENT_RESOURCES,
            QOS_POLICY_SCS_RESPONSE_STATUS_TCLAS_RESOURCES_EXHAUSTED,
            QOS_POLICY_SCS_RESPONSE_STATUS_TCLAS_PROCESSING_TERMINATED_INSUFFICIENT_QOS,
            QOS_POLICY_SCS_RESPONSE_STATUS_TCLAS_PROCESSING_TERMINATED_POLICY_CONFLICT,
            QOS_POLICY_SCS_RESPONSE_STATUS_TCLAS_PROCESSING_TERMINATED,
            QOS_POLICY_SCS_RESPONSE_STATUS_TIMEOUT
    })
    @Retention(RetentionPolicy.SOURCE)
    protected @interface QosPolicyScsResponseStatusCode {}

    /**
     * Callback to receive responses for QoS SCS transactions.
     */
    protected interface QosScsResponseCallback {
        /**
         * Called to indicate a response from the AP.
         *
         * @param ifaceName Name of the interface where the event occurred.
         * @param statusList List of {@link QosPolicyStatus} objects. Status code will be
         *                   one of {@link QosPolicyScsResponseStatusCode}.
         */
        void onApResponse(String ifaceName, List<QosPolicyStatus> statusList);
    }

    public SupplicantStaIfaceHal(Context context, WifiMonitor monitor,
            FrameworkFacade frameworkFacade, Handler handler,
            Clock clock, WifiMetrics wifiMetrics,
            WifiGlobals wifiGlobals,
            @NonNull SsidTranslator ssidTranslator, WifiInjector wifiInjector) {
        mContext = context;
        mWifiMonitor = monitor;
        mFrameworkFacade = frameworkFacade;
        mEventHandler = handler;
        mClock = clock;
        mWifiMetrics = wifiMetrics;
        mWifiGlobals = wifiGlobals;
        mSsidTranslator = ssidTranslator;
        mWifiInjector = wifiInjector;
        mStaIfaceHal = createStaIfaceHalMockable();
        if (mStaIfaceHal == null) {
            Log.wtf(TAG, "Failed to get internal ISupplicantStaIfaceHal instance.");
        }
    }

    /**
     * Check whether the HAL service is using AIDL.
     *
     * @return true if the AIDL service is being used, false otherwise.
     */
    public boolean isAidlService() {
        return mStaIfaceHal != null && mStaIfaceHal instanceof SupplicantStaIfaceHalAidlImpl;
    }

    /**
     * Check whether the AIDL service is running at least the expected version.
     *
     * @param expectedVersion Version number to check.
     * @return true if the AIDL service is available and >= the expected version, false otherwise.
     */
    public boolean isAidlServiceVersionAtLeast(int expectedVersion) {
        return isAidlService() && ((SupplicantStaIfaceHalAidlImpl) mStaIfaceHal)
                .isServiceVersionAtLeast(expectedVersion);
    }

    /**
     * Enable/Disable verbose logging.
     */
    void enableVerboseLogging(boolean verboseEnabled, boolean halVerboseEnabled) {
        synchronized (mLock) {
            if (mStaIfaceHal != null) {
                mStaIfaceHal.enableVerboseLogging(verboseEnabled, halVerboseEnabled);
            }
        }
    }

    /**
     * Initialize the STA Iface HAL. Creates the internal ISupplicantStaIfaceHal
     * object and calls its initialize method.
     *
     * @return true if the initialization succeeded
     */
    public boolean initialize() {
        synchronized (mLock) {
            if (mStaIfaceHal == null) {
                Log.wtf(TAG, "Internal ISupplicantStaIfaceHal instance does not exist.");
                return false;
            }
            if (!mStaIfaceHal.initialize()) {
                Log.e(TAG, "Failed to init ISupplicantStaIfaceHal, stopping startup.");
                return false;
            }
            return true;
        }
    }

    /**
     * Wrapper function to create the ISupplicantStaIfaceHal object.
     * Created to be mockable in unit tests.
     */
    @VisibleForTesting
    protected ISupplicantStaIfaceHal createStaIfaceHalMockable() {
        synchronized (mLock) {
            // Prefer AIDL implementation if service is declared.
            if (SupplicantStaIfaceHalAidlImpl.serviceDeclared()) {
                Log.i(TAG, "Initializing SupplicantStaIfaceHal using AIDL implementation.");
                return new SupplicantStaIfaceHalAidlImpl(mContext, mWifiMonitor,
                        mEventHandler, mClock, mWifiMetrics, mWifiGlobals, mSsidTranslator,
                        mWifiInjector);

            } else if (SupplicantStaIfaceHalHidlImpl.serviceDeclared()) {
                Log.i(TAG, "Initializing SupplicantStaIfaceHal using HIDL implementation.");
                return new SupplicantStaIfaceHalHidlImpl(mContext, mWifiMonitor, mFrameworkFacade,
                        mEventHandler, mClock, mWifiMetrics, mWifiGlobals, mSsidTranslator);
            }
            Log.e(TAG, "No HIDL or AIDL service available for SupplicantStaIfaceHal.");
            return null;
        }
    }

    /**
     * Setup a STA interface for the specified iface name.
     *
     * @param ifaceName Name of the interface.
     * @return true on success, false otherwise.
     */
    public boolean setupIface(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "setupIface";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.setupIface(ifaceName);
        }
    }

    /**
     * Teardown a STA interface for the specified iface name.
     *
     * @param ifaceName Name of the interface.
     * @return true on success, false otherwise.
     */
    public boolean teardownIface(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "teardownIface";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.teardownIface(ifaceName);
        }
    }

    /**
     * Registers a death notification for supplicant.
     * @return Returns true on success.
     */
    public boolean registerDeathHandler(@NonNull WifiNative.SupplicantDeathEventHandler handler) {
        synchronized (mLock) {
            String methodStr = "registerDeathHandler";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.registerDeathHandler(handler);
        }
    }

    /**
     * Deregisters a death notification for supplicant.
     * @return Returns true on success.
     */
    public boolean deregisterDeathHandler() {
        synchronized (mLock) {
            String methodStr = "deregisterDeathHandler";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.deregisterDeathHandler();
        }
    }

    /**
     * Signals whether initialization started successfully.
     */
    public boolean isInitializationStarted() {
        synchronized (mLock) {
            String methodStr = "isInitializationStarted";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.isInitializationStarted();
        }
    }

    /**
     * Signals whether initialization completed successfully.
     */
    public boolean isInitializationComplete() {
        synchronized (mLock) {
            String methodStr = "isInitializationComplete";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.isInitializationComplete();
        }
    }

    /**
     * Start the supplicant daemon.
     *
     * @return true on success, false otherwise.
     */
    public boolean startDaemon() {
        synchronized (mLock) {
            String methodStr = "startDaemon";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.startDaemon();
        }
    }

    /**
     * Terminate the supplicant daemon & wait for its death.
     */
    public void terminate() {
        synchronized (mLock) {
            String methodStr = "terminate";
            if (mStaIfaceHal == null) {
                handleNullHal(methodStr);
                return;
            }
            mStaIfaceHal.terminate();
        }
    }

    /**
     * Add the provided network configuration to wpa_supplicant and initiate connection to it.
     * This method does the following:
     * 1. If |config| is different to the current supplicant network, removes all supplicant
     * networks and saves |config|.
     * 2. Select the new network in wpa_supplicant.
     *
     * @param ifaceName Name of the interface.
     * @param config WifiConfiguration parameters for the provided network.
     * @return {@code true} if it succeeds, {@code false} otherwise
     */
    public boolean connectToNetwork(@NonNull String ifaceName, @NonNull WifiConfiguration config) {
        synchronized (mLock) {
            String methodStr = "connectToNetwork";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.connectToNetwork(ifaceName, config);
        }
    }

    /**
     * Initiates roaming to the already configured network in wpa_supplicant. If the network
     * configuration provided does not match the already configured network, then this triggers
     * a new connection attempt (instead of roam).
     *
     * @param ifaceName Name of the interface.
     * @param config WifiConfiguration parameters for the provided network.
     * @return {@code true} if it succeeds, {@code false} otherwise
     */
    public boolean roamToNetwork(@NonNull String ifaceName, WifiConfiguration config) {
        synchronized (mLock) {
            String methodStr = "roamToNetwork";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.roamToNetwork(ifaceName, config);
        }
    }

    /**
     * Clean HAL cached data for |networkId| in the framework.
     *
     * @param networkId Network id of the network to be removed from supplicant.
     */
    public void removeNetworkCachedData(int networkId) {
        synchronized (mLock) {
            String methodStr = "removeNetworkCachedData";
            if (mStaIfaceHal == null) {
                handleNullHal(methodStr);
                return;
            }
            mStaIfaceHal.removeNetworkCachedData(networkId);
        }
    }

    /**
     * Clear HAL cached data if MAC address is changed.
     *
     * @param networkId Network id of the network to be checked.
     * @param curMacAddress Current MAC address
     */
    public void removeNetworkCachedDataIfNeeded(int networkId, MacAddress curMacAddress) {
        synchronized (mLock) {
            String methodStr = "removeNetworkCachedDataIfNeeded";
            if (mStaIfaceHal == null) {
                handleNullHal(methodStr);
                return;
            }
            mStaIfaceHal.removeNetworkCachedDataIfNeeded(networkId, curMacAddress);
        }
    }

    /**
     * Remove all networks from supplicant
     *
     * @param ifaceName Name of the interface.
     */
    public boolean removeAllNetworks(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "removeAllNetworks";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.removeAllNetworks(ifaceName);
        }
    }

    /**
     * Disable the current network in supplicant
     *
     * @param ifaceName Name of the interface.
     */
    public boolean disableCurrentNetwork(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "disableCurrentNetwork";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.disableCurrentNetwork(ifaceName);
        }
    }

    /**
     * Set the currently configured network's bssid.
     *
     * @param ifaceName Name of the interface.
     * @param bssidStr Bssid to set in the form of "XX:XX:XX:XX:XX:XX"
     * @return true if succeeds, false otherwise.
     */
    public boolean setCurrentNetworkBssid(@NonNull String ifaceName, String bssidStr) {
        synchronized (mLock) {
            String methodStr = "setCurrentNetworkBssid";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.setCurrentNetworkBssid(ifaceName, bssidStr);
        }
    }

    /**
     * Get the currently configured network's WPS NFC token.
     *
     * @param ifaceName Name of the interface.
     * @return Hex string corresponding to the WPS NFC token.
     */
    public String getCurrentNetworkWpsNfcConfigurationToken(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "getCurrentNetworkWpsNfcConfigurationToken";
            if (mStaIfaceHal == null) {
                handleNullHal(methodStr);
                return null;
            }
            return mStaIfaceHal.getCurrentNetworkWpsNfcConfigurationToken(ifaceName);
        }
    }

    /**
     * Get the eap anonymous identity for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @return anonymous identity string if succeeds, null otherwise.
     */
    public String getCurrentNetworkEapAnonymousIdentity(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "getCurrentNetworkEapAnonymousIdentity";
            if (mStaIfaceHal == null) {
                handleNullHal(methodStr);
                return null;
            }
            return mStaIfaceHal.getCurrentNetworkEapAnonymousIdentity(ifaceName);
        }
    }

    /**
     * Send the eap identity response for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @param identity Identity used for EAP-Identity
     * @param encryptedIdentity Encrypted identity used for EAP-AKA/EAP-SIM
     * @return true if succeeds, false otherwise.
     */
    public boolean sendCurrentNetworkEapIdentityResponse(
            @NonNull String ifaceName, @NonNull String identity, String encryptedIdentity) {
        synchronized (mLock) {
            String methodStr = "sendCurrentNetworkEapIdentityResponse";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.sendCurrentNetworkEapIdentityResponse(
                    ifaceName, identity, encryptedIdentity);
        }
    }

    /**
     * Send the eap sim gsm auth response for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @param paramsStr String to send.
     * @return true if succeeds, false otherwise.
     */
    public boolean sendCurrentNetworkEapSimGsmAuthResponse(
            @NonNull String ifaceName, String paramsStr) {
        synchronized (mLock) {
            String methodStr = "sendCurrentNetworkEapSimGsmAuthResponse";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.sendCurrentNetworkEapSimGsmAuthResponse(ifaceName, paramsStr);
        }
    }

    /**
     * Send the eap sim gsm auth failure for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @return true if succeeds, false otherwise.
     */
    public boolean sendCurrentNetworkEapSimGsmAuthFailure(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "sendCurrentNetworkEapSimGsmAuthFailure";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.sendCurrentNetworkEapSimGsmAuthFailure(ifaceName);
        }
    }

    /**
     * Send the eap sim umts auth response for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @param paramsStr String to send.
     * @return true if succeeds, false otherwise.
     */
    public boolean sendCurrentNetworkEapSimUmtsAuthResponse(
            @NonNull String ifaceName, String paramsStr) {
        synchronized (mLock) {
            String methodStr = "sendCurrentNetworkEapSimUmtsAuthResponse";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.sendCurrentNetworkEapSimUmtsAuthResponse(ifaceName, paramsStr);
        }
    }

    /**
     * Send the eap sim umts auts response for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @param paramsStr String to send.
     * @return true if succeeds, false otherwise.
     */
    public boolean sendCurrentNetworkEapSimUmtsAutsResponse(
            @NonNull String ifaceName, String paramsStr) {
        synchronized (mLock) {
            String methodStr = "sendCurrentNetworkEapSimUmtsAutsResponse";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.sendCurrentNetworkEapSimUmtsAutsResponse(ifaceName, paramsStr);
        }
    }

    /**
     * Send the eap sim umts auth failure for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @return true if succeeds, false otherwise.
     */
    public boolean sendCurrentNetworkEapSimUmtsAuthFailure(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "sendCurrentNetworkEapSimUmtsAuthFailure";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.sendCurrentNetworkEapSimUmtsAuthFailure(ifaceName);
        }
    }

    /**
     * Set WPS device name.
     *
     * @param ifaceName Name of the interface.
     * @param deviceName String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setWpsDeviceName(@NonNull String ifaceName, String deviceName) {
        synchronized (mLock) {
            String methodStr = "setWpsDeviceName";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.setWpsDeviceName(ifaceName, deviceName);
        }
    }

    /**
     * Set WPS device type.
     *
     * @param ifaceName Name of the interface.
     * @param typeStr Type specified as a string. Used format: <categ>-<OUI>-<subcateg>
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setWpsDeviceType(@NonNull String ifaceName, String typeStr) {
        synchronized (mLock) {
            String methodStr = "setWpsDeviceType";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.setWpsDeviceType(ifaceName, typeStr);
        }
    }

    /**
     * Set WPS manufacturer.
     *
     * @param ifaceName Name of the interface.
     * @param manufacturer String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setWpsManufacturer(@NonNull String ifaceName, String manufacturer) {
        synchronized (mLock) {
            String methodStr = "setWpsManufacturer";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.setWpsManufacturer(ifaceName, manufacturer);
        }
    }

    /**
     * Set WPS model name.
     *
     * @param ifaceName Name of the interface.
     * @param modelName String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setWpsModelName(@NonNull String ifaceName, String modelName) {
        synchronized (mLock) {
            String methodStr = "setWpsModelName";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.setWpsModelName(ifaceName, modelName);
        }
    }

    /**
     * Set WPS model number.
     *
     * @param ifaceName Name of the interface.
     * @param modelNumber String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setWpsModelNumber(@NonNull String ifaceName, String modelNumber) {
        synchronized (mLock) {
            String methodStr = "setWpsModelNumber";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.setWpsModelNumber(ifaceName, modelNumber);
        }
    }

    /**
     * Set WPS serial number.
     *
     * @param ifaceName Name of the interface.
     * @param serialNumber String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setWpsSerialNumber(@NonNull String ifaceName, String serialNumber) {
        synchronized (mLock) {
            String methodStr = "setWpsSerialNumber";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.setWpsSerialNumber(ifaceName, serialNumber);
        }
    }

    /**
     * Set WPS config methods
     *
     * @param ifaceName Name of the interface.
     * @param configMethodsStr List of config methods.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setWpsConfigMethods(@NonNull String ifaceName, String configMethodsStr) {
        synchronized (mLock) {
            String methodStr = "setWpsConfigMethods";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.setWpsConfigMethods(ifaceName, configMethodsStr);
        }
    }

    /**
     * Trigger a reassociation even if the iface is currently connected.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean reassociate(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "reassociate";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.reassociate(ifaceName);
        }
    }

    /**
     * Trigger a reconnection if the iface is disconnected.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean reconnect(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "reconnect";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.reconnect(ifaceName);
        }
    }

    /**
     * Trigger a disconnection from the currently connected network.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean disconnect(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "disconnect";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.disconnect(ifaceName);
        }
    }

    /**
     * Enable or disable power save mode.
     *
     * @param ifaceName Name of the interface.
     * @param enable true to enable, false to disable.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setPowerSave(@NonNull String ifaceName, boolean enable) {
        synchronized (mLock) {
            String methodStr = "setPowerSave";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.setPowerSave(ifaceName, enable);
        }
    }

    /**
     * Initiate TDLS discover with the specified AP.
     *
     * @param ifaceName Name of the interface.
     * @param macAddress MAC Address of the AP.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean initiateTdlsDiscover(@NonNull String ifaceName, String macAddress) {
        synchronized (mLock) {
            String methodStr = "initiateTdlsDiscover";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.initiateTdlsDiscover(ifaceName, macAddress);
        }
    }

    /**
     * Initiate TDLS setup with the specified AP.
     *
     * @param ifaceName Name of the interface.
     * @param macAddress MAC Address of the AP.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean initiateTdlsSetup(@NonNull String ifaceName, String macAddress) {
        synchronized (mLock) {
            String methodStr = "initiateTdlsSetup";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.initiateTdlsSetup(ifaceName, macAddress);
        }
    }

    /**
     * Initiate TDLS teardown with the specified AP.
     * @param ifaceName Name of the interface.
     * @param macAddress MAC Address of the AP.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean initiateTdlsTeardown(@NonNull String ifaceName, String macAddress) {
        synchronized (mLock) {
            String methodStr = "initiateTdlsTeardown";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.initiateTdlsTeardown(ifaceName, macAddress);
        }
    }

    /**
     * Request the specified ANQP elements |elements| from the specified AP |bssid|.
     *
     * @param ifaceName Name of the interface.
     * @param bssid BSSID of the AP
     * @param infoElements ANQP elements to be queried. Refer to ISupplicantStaIface.AnqpInfoId.
     * @param hs20SubTypes HS subtypes to be queried. Refer to ISupplicantStaIface.Hs20AnqpSubTypes.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean initiateAnqpQuery(@NonNull String ifaceName, String bssid,
            ArrayList<Short> infoElements,
            ArrayList<Integer> hs20SubTypes) {
        synchronized (mLock) {
            String methodStr = "initiateAnqpQuery";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.initiateAnqpQuery(ifaceName, bssid, infoElements, hs20SubTypes);
        }
    }

    /**
     * Request Venue URL ANQP element from the specified AP |bssid|.
     *
     * @param ifaceName Name of the interface.
     * @param bssid BSSID of the AP
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean initiateVenueUrlAnqpQuery(@NonNull String ifaceName, String bssid) {
        synchronized (mLock) {
            String methodStr = "initiateVenueUrlAnqpQuery";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.initiateVenueUrlAnqpQuery(ifaceName, bssid);
        }
    }

    /**
     * Request the specified ANQP ICON from the specified AP |bssid|.
     *
     * @param ifaceName Name of the interface.
     * @param bssid BSSID of the AP
     * @param fileName Name of the file to request.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean initiateHs20IconQuery(@NonNull String ifaceName, String bssid, String fileName) {
        synchronized (mLock) {
            String methodStr = "initiateHs20IconQuery";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.initiateHs20IconQuery(ifaceName, bssid, fileName);
        }
    }

    /**
     * Gets MAC address from the supplicant.
     *
     * @param ifaceName Name of the interface.
     * @return string containing the MAC address, or null on a failed call
     */
    public String getMacAddress(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "getMacAddress";
            if (mStaIfaceHal == null) {
                handleNullHal(methodStr);
                return null;
            }
            return mStaIfaceHal.getMacAddress(ifaceName);
        }
    }

    /**
     * Start using the added RX filters.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean startRxFilter(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "startRxFilter";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.startRxFilter(ifaceName);
        }
    }

    /**
     * Stop using the added RX filters.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean stopRxFilter(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "stopRxFilter";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.stopRxFilter(ifaceName);
        }
    }

    /**
     * Add an RX filter.
     *
     * @param ifaceName Name of the interface.
     * @param type one of {@link WifiNative#RX_FILTER_TYPE_V4_MULTICAST}
     *        {@link WifiNative#RX_FILTER_TYPE_V6_MULTICAST} values.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean addRxFilter(@NonNull String ifaceName, int type) {
        synchronized (mLock) {
            String methodStr = "addRxFilter";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.addRxFilter(ifaceName, type);
        }
    }

    /**
     * Remove an RX filter.
     *
     * @param ifaceName Name of the interface.
     * @param type one of {@link WifiNative#RX_FILTER_TYPE_V4_MULTICAST}
     *        {@link WifiNative#RX_FILTER_TYPE_V6_MULTICAST} values.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean removeRxFilter(@NonNull String ifaceName, int type) {
        synchronized (mLock) {
            String methodStr = "removeRxFilter";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.removeRxFilter(ifaceName, type);
        }
    }

    /**
     * Set Bt coexistence mode.
     *
     * @param ifaceName Name of the interface.
     * @param mode one of the above {@link WifiNative#BLUETOOTH_COEXISTENCE_MODE_DISABLED},
     *             {@link WifiNative#BLUETOOTH_COEXISTENCE_MODE_ENABLED} or
     *             {@link WifiNative#BLUETOOTH_COEXISTENCE_MODE_SENSE}.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setBtCoexistenceMode(@NonNull String ifaceName, int mode) {
        synchronized (mLock) {
            String methodStr = "setBtCoexistenceMode";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.setBtCoexistenceMode(ifaceName, mode);
        }
    }

    /** Enable or disable BT coexistence mode.
     *
     * @param ifaceName Name of the interface.
     * @param enable true to enable, false to disable.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setBtCoexistenceScanModeEnabled(@NonNull String ifaceName, boolean enable) {
        synchronized (mLock) {
            String methodStr = "setBtCoexistenceScanModeEnabled";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.setBtCoexistenceScanModeEnabled(ifaceName, enable);
        }
    }

    /**
     * Enable or disable suspend mode optimizations.
     *
     * @param ifaceName Name of the interface.
     * @param enable true to enable, false otherwise.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setSuspendModeEnabled(@NonNull String ifaceName, boolean enable) {
        synchronized (mLock) {
            String methodStr = "setSuspendModeEnabled";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.setSuspendModeEnabled(ifaceName, enable);
        }
    }

    /**
     * Set country code.
     *
     * @param ifaceName Name of the interface.
     * @param codeStr 2 byte ASCII string. For ex: US, CA.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setCountryCode(@NonNull String ifaceName, String codeStr) {
        synchronized (mLock) {
            String methodStr = "setCountryCode";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.setCountryCode(ifaceName, codeStr);
        }
    }

    /**
     * Flush all previously configured HLPs.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean flushAllHlp(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "flushAllHlp";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.flushAllHlp(ifaceName);
        }
    }

    /**
     * Set FILS HLP packet.
     *
     * @param ifaceName Name of the interface.
     * @param dst Destination MAC address.
     * @param hlpPacket Hlp Packet data in hex.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean addHlpReq(@NonNull String ifaceName, byte [] dst, byte [] hlpPacket) {
        synchronized (mLock) {
            String methodStr = "addHlpReq";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.addHlpReq(ifaceName, dst, hlpPacket);
        }
    }

    /**
     * Start WPS pin registrar operation with the specified peer and pin.
     *
     * @param ifaceName Name of the interface.
     * @param bssidStr BSSID of the peer.
     * @param pin Pin to be used.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean startWpsRegistrar(@NonNull String ifaceName, String bssidStr, String pin) {
        synchronized (mLock) {
            String methodStr = "startWpsRegistrar";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.startWpsRegistrar(ifaceName, bssidStr, pin);
        }
    }

    /**
     * Start WPS pin display operation with the specified peer.
     *
     * @param ifaceName Name of the interface.
     * @param bssidStr BSSID of the peer. Use empty bssid to indicate wildcard.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean startWpsPbc(@NonNull String ifaceName, String bssidStr) {
        synchronized (mLock) {
            String methodStr = "startWpsPbc";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.startWpsPbc(ifaceName, bssidStr);
        }
    }

    /**
     * Start WPS pin keypad operation with the specified pin.
     *
     * @param ifaceName Name of the interface.
     * @param pin Pin to be used.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean startWpsPinKeypad(@NonNull String ifaceName, String pin) {
        synchronized (mLock) {
            String methodStr = "startWpsPinKeypad";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.startWpsPinKeypad(ifaceName, pin);
        }
    }

    /**
     * Start WPS pin display operation with the specified peer.
     *
     * @param ifaceName Name of the interface.
     * @param bssidStr BSSID of the peer. Use empty bssid to indicate wildcard.
     * @return new pin generated on success, null otherwise.
     */
    public String startWpsPinDisplay(@NonNull String ifaceName, String bssidStr) {
        synchronized (mLock) {
            String methodStr = "startWpsPinDisplay";
            if (mStaIfaceHal == null) {
                handleNullHal(methodStr);
                return null;
            }
            return mStaIfaceHal.startWpsPinDisplay(ifaceName, bssidStr);
        }
    }

    /**
     * Cancels any ongoing WPS requests.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean cancelWps(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "cancelWps";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.cancelWps(ifaceName);
        }
    }

    /**
     * Sets whether to use external sim for SIM/USIM processing.
     *
     * @param ifaceName Name of the interface.
     * @param useExternalSim true to enable, false otherwise.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setExternalSim(@NonNull String ifaceName, boolean useExternalSim) {
        synchronized (mLock) {
            String methodStr = "setExternalSim";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.setExternalSim(ifaceName, useExternalSim);
        }
    }

    /**
     * Enable/Disable auto reconnect to networks.
     * Use this to prevent wpa_supplicant from trying to connect to networks
     * on its own.
     *
     * @param enable true to enable, false to disable.
     * @return true if no exceptions occurred, false otherwise
     */
    public boolean enableAutoReconnect(@NonNull String ifaceName, boolean enable) {
        synchronized (mLock) {
            String methodStr = "enableAutoReconnect";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.enableAutoReconnect(ifaceName, enable);
        }
    }

    /**
     * Set the debug log level for wpa_supplicant
     *
     * @param turnOnVerbose Whether to turn on verbose logging or not.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setLogLevel(boolean turnOnVerbose) {
        synchronized (mLock) {
            String methodStr = "setLogLevel";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.setLogLevel(turnOnVerbose);
        }
    }

    /**
     * Set concurrency priority between P2P & STA operations.
     *
     * @param isStaHigherPriority Set to true to prefer STA over P2P during concurrency operations,
     *                            false otherwise.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setConcurrencyPriority(boolean isStaHigherPriority) {
        synchronized (mLock) {
            String methodStr = "setConcurrencyPriority";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.setConcurrencyPriority(isStaHigherPriority);
        }
    }


    /**
     * Returns a bitmask of advanced capabilities: WPA3 SAE/SUITE B and OWE
     * Bitmask used is:
     * - WIFI_FEATURE_WPA3_SAE
     * - WIFI_FEATURE_WPA3_SUITE_B
     * - WIFI_FEATURE_OWE
     *
     *  On error, or if these features are not supported, 0 is returned.
     */
    public long getAdvancedCapabilities(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "getAdvancedCapabilities";
            if (mStaIfaceHal == null) {
                handleNullHal(methodStr);
                return 0;
            }
            return mStaIfaceHal.getAdvancedCapabilities(ifaceName);
        }
    }

    /**
     * Get the driver supported features through supplicant.
     *
     * @param ifaceName Name of the interface.
     * @return bitmask defined by WifiManager.WIFI_FEATURE_*.
     */
    public long getWpaDriverFeatureSet(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "getWpaDriverFeatureSet";
            if (mStaIfaceHal == null) {
                handleNullHal(methodStr);
                return 0;
            }
            return mStaIfaceHal.getWpaDriverFeatureSet(ifaceName);
        }
    }

    /**
     * Returns connection capabilities of the current network
     *
     * @param ifaceName Name of the interface.
     * @return connection capabilities of the current network
     */
    public WifiNative.ConnectionCapabilities getConnectionCapabilities(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "getConnectionCapabilities";
            if (mStaIfaceHal == null) {
                handleNullHal(methodStr);
                return new WifiNative.ConnectionCapabilities();
            }
            return mStaIfaceHal.getConnectionCapabilities(ifaceName);
        }
    }

    /**
     * Returns signal poll results for all Wi-Fi links of the interface.
     *
     * @param ifaceName Name of the interface.
     * @return Signal poll results.
     */
    public WifiSignalPollResults getSignalPollResults(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "getSignalPollResults";
            if (mStaIfaceHal == null) {
                handleNullHal(methodStr);
                return null;
            }
            return mStaIfaceHal.getSignalPollResults(ifaceName);
        }
    }

    /**
     * Returns connection MLO links info.
     *
     * @param ifaceName Name of the interface.
     * @return connection MLO links info
     */
    public WifiNative.ConnectionMloLinksInfo getConnectionMloLinksInfo(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "getConnectionMloLinksInfo";
            if (mStaIfaceHal == null) {
                handleNullHal(methodStr);
                return null;
            }
            return mStaIfaceHal.getConnectionMloLinksInfo(ifaceName);
        }
    }

    /**
     * Adds a DPP peer URI to the URI list.
     *
     * Returns an ID to be used later to refer to this URI (>0).
     * On error, or if these features are not supported, -1 is returned.
     */
    public int addDppPeerUri(@NonNull String ifaceName, @NonNull String uri) {
        synchronized (mLock) {
            String methodStr = "addDppPeerUri";
            if (mStaIfaceHal == null) {
                handleNullHal(methodStr);
                return -1;
            }
            return mStaIfaceHal.addDppPeerUri(ifaceName, uri);
        }
    }

    /**
     * Removes a DPP URI to the URI list given an ID.
     *
     * Returns true when operation is successful
     * On error, or if these features are not supported, false is returned.
     */
    public boolean removeDppUri(@NonNull String ifaceName, int bootstrapId) {
        synchronized (mLock) {
            String methodStr = "removeDppUri";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.removeDppUri(ifaceName, bootstrapId);
        }
    }

    /**
     * Stops/aborts DPP Initiator request
     *
     * Returns true when operation is successful
     * On error, or if these features are not supported, false is returned.
     */
    public boolean stopDppInitiator(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "stopDppInitiator";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.stopDppInitiator(ifaceName);
        }
    }

    /**
     * Starts DPP Configurator-Initiator request
     *
     * Returns true when operation is successful
     * On error, or if these features are not supported, false is returned.
     */
    public boolean startDppConfiguratorInitiator(@NonNull String ifaceName, int peerBootstrapId,
            int ownBootstrapId, @NonNull String ssid, String password, String psk,
            int netRole, int securityAkm, byte[] privEcKey) {
        synchronized (mLock) {
            String methodStr = "startDppConfiguratorInitiator";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.startDppConfiguratorInitiator(ifaceName, peerBootstrapId,
                    ownBootstrapId, ssid, password, psk, netRole, securityAkm, privEcKey);
        }
    }

    /**
     * Starts DPP Enrollee-Initiator request
     *
     * Returns true when operation is successful
     * On error, or if these features are not supported, false is returned.
     */
    public boolean startDppEnrolleeInitiator(@NonNull String ifaceName, int peerBootstrapId,
            int ownBootstrapId) {
        synchronized (mLock) {
            String methodStr = "startDppEnrolleeInitiator";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.startDppEnrolleeInitiator(
                    ifaceName, peerBootstrapId, ownBootstrapId);
        }
    }

    /**
     * Generate a DPP QR code based on boot strap info
     *
     * Returns DppBootstrapQrCodeInfo
     */
    public WifiNative.DppBootstrapQrCodeInfo generateDppBootstrapInfoForResponder(
            @NonNull String ifaceName, String macAddress, @NonNull String deviceInfo,
            int dppCurve) {
        synchronized (mLock) {
            String methodStr = "generateDppBootstrapInfoForResponder";
            if (mStaIfaceHal == null) {
                handleNullHal(methodStr);
                return new WifiNative.DppBootstrapQrCodeInfo();
            }
            return mStaIfaceHal.generateDppBootstrapInfoForResponder(
                    ifaceName, macAddress, deviceInfo, dppCurve);
        }
    }

    /**
     * Starts DPP Enrollee-Responder request
     *
     * Returns true when operation is successful
     * On error, or if these features are not supported, false is returned.
     */
    public boolean startDppEnrolleeResponder(@NonNull String ifaceName, int listenChannel) {
        synchronized (mLock) {
            String methodStr = "startDppEnrolleeResponder";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.startDppEnrolleeResponder(ifaceName, listenChannel);
        }
    }

    /**
     * Stops/aborts DPP Responder request.
     *
     * Returns true when operation is successful
     * On error, or if these features are not supported, false is returned.
     */
    public boolean stopDppResponder(@NonNull String ifaceName, int ownBootstrapId) {
        synchronized (mLock) {
            String methodStr = "stopDppResponder";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.stopDppResponder(ifaceName, ownBootstrapId);
        }
    }

    /**
     * Register callbacks for DPP events.
     *
     * @param dppCallback DPP callback object.
     */
    public void registerDppCallback(WifiNative.DppEventCallback dppCallback) {
        synchronized (mLock) {
            String methodStr = "registerDppCallback";
            if (mStaIfaceHal == null) {
                handleNullHal(methodStr);
                return;
            }
            mStaIfaceHal.registerDppCallback(dppCallback);
        }
    }

    /**
     * Set MBO cellular data availability.
     *
     * @param ifaceName Name of the interface.
     * @param available true means cellular data available, false otherwise.
     * Returns true when operation is successful
     */
    public boolean setMboCellularDataStatus(@NonNull String ifaceName, boolean available) {
        synchronized (mLock) {
            String methodStr = "setMboCellularDataStatus";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.setMboCellularDataStatus(ifaceName, available);
        }
    }

    /**
     * Set whether the network-centric QoS policy feature is enabled or not for this interface.
     *
     * @param ifaceName name of the interface.
     * @param isEnabled true if feature is enabled, false otherwise.
     * @return true if operation is successful, false otherwise.
     */
    public boolean setNetworkCentricQosPolicyFeatureEnabled(@NonNull String ifaceName,
            boolean isEnabled) {
        synchronized (mLock) {
            String methodStr = "setNetworkCentricQosPolicyFeatureEnabled";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.setNetworkCentricQosPolicyFeatureEnabled(ifaceName, isEnabled);
        }
    }

    /**
     * Check if we've roamed to a linked network and make the linked network the current network
     * if we have.
     *
     * @param ifaceName Name of the interface.
     * @param newNetworkId Network id of the new network we've roamed to. If fromFramework is
     *                     {@code true}, this will be a framework network id. Otherwise, this will
     *                     be a remote network id.
     * @param fromFramework {@code true} if the network id is a framework network id, {@code false}
                            if the network id is a remote network id.
     * @return true if we've roamed to a linked network, false if not.
     */
    public boolean updateOnLinkedNetworkRoaming(
            @NonNull String ifaceName, int newNetworkId, boolean fromFramework) {
        synchronized (mLock) {
            String methodStr = "updateOnLinkedNetworkRoaming";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.updateOnLinkedNetworkRoaming(
                    ifaceName, newNetworkId, fromFramework);
        }
    }

    /**
     * Updates the linked networks for the current network and sends them to the supplicant.
     *
     * @param ifaceName Name of the interface.
     * @param networkId Network id of the network to link the configurations to.
     * @param linkedConfigurations Map of config profile key to config for linking.
     * @return true if networks were successfully linked, false otherwise.
     */
    public boolean updateLinkedNetworks(@NonNull String ifaceName, int networkId,
            Map<String, WifiConfiguration> linkedConfigurations) {
        synchronized (mLock) {
            String methodStr = "updateLinkedNetworks";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.updateLinkedNetworks(ifaceName, networkId, linkedConfigurations);
        }
    }

    /**
     * Gets the security params of the current network associated with this interface
     *
     * @param ifaceName Name of the interface
     * @return Security params of the current network associated with the interface
     */
    public SecurityParams getCurrentNetworkSecurityParams(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "getCurrentNetworkSecurityParams";
            if (mStaIfaceHal == null) {
                handleNullHal(methodStr);
                return null;
            }
            return mStaIfaceHal.getCurrentNetworkSecurityParams(ifaceName);
        }
    }

    /**
     * Sends a QoS policy response.
     *
     * @param ifaceName Name of the interface.
     * @param qosPolicyRequestId Dialog token to identify the request.
     * @param morePolicies Flag to indicate more QoS policies can be accommodated.
     * @param qosPolicyStatusList List of framework QosPolicyStatus objects.
     * @return true if response is sent successfully, false otherwise.
     */
    public boolean sendQosPolicyResponse(String ifaceName, int qosPolicyRequestId,
            boolean morePolicies, @NonNull List<QosPolicyStatus> qosPolicyStatusList) {
        String methodStr = "sendQosPolicyResponse";
        if (mStaIfaceHal == null) {
            handleNullHal(methodStr);
            return false;
        }
        return mStaIfaceHal.sendQosPolicyResponse(ifaceName, qosPolicyRequestId,
                morePolicies, qosPolicyStatusList);
    }

    /**
     * Indicates the removal of all active QoS policies configured by the AP.
     *
     * @param ifaceName Name of the interface.
     */
    public boolean removeAllQosPolicies(String ifaceName) {
        String methodStr = "removeAllQosPolicies";
        if (mStaIfaceHal == null) {
            return handleNullHal(methodStr);
        }
        return mStaIfaceHal.removeAllQosPolicies(ifaceName);
    }

    /**
     * See comments for {@link ISupplicantStaIfaceHal#addQosPolicyRequestForScs(String, List)}
     */
    public List<QosPolicyStatus> addQosPolicyRequestForScs(
            @NonNull String ifaceName, @NonNull List<QosPolicyParams> policies) {
        String methodStr = "addQosPolicyRequestForScs";
        if (mStaIfaceHal == null) {
            handleNullHal(methodStr);
            return null;
        }
        return mStaIfaceHal.addQosPolicyRequestForScs(ifaceName, policies);
    }

    /**
     * See comments for {@link ISupplicantStaIfaceHal#removeQosPolicyForScs(String, List)}
     */
    public List<QosPolicyStatus> removeQosPolicyForScs(
            @NonNull String ifaceName, @NonNull List<Byte> policyIds) {
        String methodStr = "removeQosPolicyForScs";
        if (mStaIfaceHal == null) {
            handleNullHal(methodStr);
            return null;
        }
        return mStaIfaceHal.removeQosPolicyForScs(ifaceName, policyIds);
    }

    /**
     * See comments for
     * {@link ISupplicantStaIfaceHal#registerQosScsResponseCallback(QosScsResponseCallback)}
     */
    public void registerQosScsResponseCallback(@NonNull QosScsResponseCallback callback) {
        String methodStr = "registerQosScsResponseCallback";
        if (mStaIfaceHal == null) {
            handleNullHal(methodStr);
            return;
        }
        mStaIfaceHal.registerQosScsResponseCallback(callback);
    }

    /**
     * Generate DPP credential for network access
     *
     * @param ifaceName Name of the interface.
     * @param ssid ssid of the network
     * @param privEcKey Private EC Key for DPP Configurator
     * Returns true when operation is successful. On error, false is returned.
     */
    public boolean generateSelfDppConfiguration(@NonNull String ifaceName, @NonNull String ssid,
            byte[] privEcKey) {
        synchronized (mLock) {
            String methodStr = "generateSelfDppConfiguration";
            if (mStaIfaceHal == null) {
                return handleNullHal(methodStr);
            }
            return mStaIfaceHal.generateSelfDppConfiguration(ifaceName, ssid, privEcKey);
        }
    }

    /**
     * This set anonymous identity to supplicant.
     *
     * @param ifaceName Name of the interface.
     * @param anonymousIdentity the anonymouns identity.
     * @param updateToNativeService write the data to the native service.
     * @return true if succeeds, false otherwise.
     */
    public boolean setEapAnonymousIdentity(@NonNull String ifaceName, String anonymousIdentity,
            boolean updateToNativeService) {
        String methodStr = "setEapAnonymousIdentity";
        if (mStaIfaceHal == null) {
            return handleNullHal(methodStr);
        }
        return mStaIfaceHal.setEapAnonymousIdentity(ifaceName, anonymousIdentity,
                updateToNativeService);
    }

    /**
     * See comments for {@link ISupplicantStaIfaceHal#enableMscs(MscsParams, String)}
     */
    public void enableMscs(@NonNull MscsParams mscsParams, String ifaceName) {
        String methodStr = "enableMscs";
        if (mStaIfaceHal == null) {
            handleNullHal(methodStr);
            return;
        }
        mStaIfaceHal.enableMscs(mscsParams, ifaceName);
    }

    /**
     * See comments for {@link ISupplicantStaIfaceHal#disableMscs(String)}
     */
    public void disableMscs(String ifaceName) {
        String methodStr = "disableMscs";
        if (mStaIfaceHal == null) {
            handleNullHal(methodStr);
            return;
        }
        mStaIfaceHal.disableMscs(ifaceName);
    }

    private boolean handleNullHal(String methodStr) {
        Log.e(TAG, "Cannot call " + methodStr + " because HAL object is null.");
        return false;
    }
}
