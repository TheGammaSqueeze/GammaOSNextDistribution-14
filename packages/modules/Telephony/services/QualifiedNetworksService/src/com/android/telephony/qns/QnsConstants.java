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

package com.android.telephony.qns;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.Annotation;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsMmTelManager;

/** This class is a collection of constants */
class QnsConstants {

    static final String QNS_TAG = "QNS";

    static final int INVALID_VALUE = -1;
    static final int INVALID_ID = -1;
    static final int KEY_DEFAULT_VALUE = 0;

    static final int KEY_DEFAULT_HYST_TIMER = 30000;
    static final int CONFIG_DEFAULT_MIN_HANDOVER_GUARDING_TIMER = 3000;
    static final int CONFIG_DEFAULT_MIN_HANDOVER_GUARDING_TIMER_LIMIT = 5000;

    static final int KEY_DEFAULT_PACKET_LOSS_TIME_MILLIS = 3000;
    static final int KEY_DEFAULT_IWLAN_AVOID_TIME_LOW_RTP_QUALITY_MILLIS = 60000;
    static final int KEY_DEFAULT_WWAN_AVOID_TIME_LOW_RTP_QUALITY_MILLIS = 60000;

    static final int KEY_DEFAULT_THRESHOLD_SSRSRP_GOOD = -110;
    static final int KEY_DEFAULT_THRESHOLD_SSRSRP_BAD = -115;
    static final int KEY_DEFAULT_THRESHOLD_RSRP_GOOD = -115;
    static final int KEY_DEFAULT_THRESHOLD_RSRP_BAD = -120;
    static final int KEY_DEFAULT_THRESHOLD_RSCP_GOOD = -105;
    static final int KEY_DEFAULT_THRESHOLD_RSCP_BAD = -115;
    static final int KEY_DEFAULT_THRESHOLD_GERAN_RSSI_GOOD = -100;
    static final int KEY_DEFAULT_THRESHOLD_GERAN_RSSI_BAD = -105;
    static final int KEY_DEFAULT_THRESHOLD_WIFI_RSSI_GOOD = -75;
    static final int KEY_DEFAULT_THRESHOLD_WIFI_RSSI_BAD = -80;

    static final int CALL_TYPE_IDLE = 0;
    static final int CALL_TYPE_VOICE = 1;
    static final int CALL_TYPE_VIDEO = 2;
    static final int CALL_TYPE_EMERGENCY = 3;

    @IntDef(
            value = {
                CALL_TYPE_IDLE,
                CALL_TYPE_VOICE,
                CALL_TYPE_VIDEO,
                CALL_TYPE_EMERGENCY,
            })
    @interface QnsCallType {}

    static final int COVERAGE_HOME = 0;
    static final int COVERAGE_ROAM = 1;
    static final int COVERAGE_BOTH = 2;

    @IntDef(value = {COVERAGE_HOME, COVERAGE_ROAM, COVERAGE_BOTH})
    @interface CellularCoverage {}

    // These(RTP_LOW_QUALITY_) constants are used to @code worseThanBefore(),
    // be caution before change.
    static final int RTP_LOW_QUALITY_REASON_JITTER = 1;
    static final int RTP_LOW_QUALITY_REASON_PACKET_LOSS = 2;
    static final int RTP_LOW_QUALITY_REASON_NO_RTP = 3;

    @IntDef(
            value = {
                RTP_LOW_QUALITY_REASON_JITTER,
                RTP_LOW_QUALITY_REASON_PACKET_LOSS,
                RTP_LOW_QUALITY_REASON_NO_RTP,
            })
    @interface RtpLowQualityReason {}

    static final int FALLBACK_REASON_INVALID = -1;
    static final int FALLBACK_REASON_RTP_ONLY = 0;
    static final int FALLBACK_REASON_WIFI_ONLY = 1;
    static final int FALLBACK_REASON_RTP_OR_WIFI = 2;

    @IntDef(
            value = {
                FALLBACK_REASON_INVALID,
                FALLBACK_REASON_RTP_ONLY,
                FALLBACK_REASON_WIFI_ONLY,
                FALLBACK_REASON_RTP_OR_WIFI,
            })
    @interface QnsFallbackReason {}

    static final int MAX_COUNT_INVALID = -1;

    static final int MIN_THRESHOLD_GAP = 3;

    static final int WIFI_ONLY = ImsMmTelManager.WIFI_MODE_WIFI_ONLY;
    static final int WIFI_PREF = ImsMmTelManager.WIFI_MODE_WIFI_PREFERRED;
    static final int CELL_PREF = ImsMmTelManager.WIFI_MODE_CELLULAR_PREFERRED;

    @IntDef(
            value = {
                WIFI_ONLY, WIFI_PREF, CELL_PREF,
            })
    @interface WfcModePreference {}

    static final int ROVE_OUT = 0;
    static final int ROVE_IN = 1;

    @IntDef(
            value = {
                ROVE_OUT, ROVE_IN,
            })
    @interface RoveDirection {}

    static final int POLICY_GOOD = 0;
    static final int POLICY_BAD = 1;
    static final int POLICY_TOLERABLE = 2;

    @IntDef(
            value = {
                POLICY_GOOD,
                POLICY_BAD,
                POLICY_TOLERABLE,
            })
    @interface QnsQualityType {}

    static final int GUARDING_NONE = 0;
    static final int GUARDING_CELLULAR = 1;
    static final int GUARDING_WIFI = 2;

    @IntDef(
            value = {
                GUARDING_NONE,
                GUARDING_WIFI,
                GUARDING_CELLULAR,
            })
    @interface QnsGuarding {}

    static final int IMS_REGISTRATION_CHANGED_UNREGISTERED = 0;
    static final int IMS_REGISTRATION_CHANGED_ACCESS_NETWORK_CHANGE_FAILED = 1;
    static final int IMS_REGISTRATION_CHANGED_REGISTERED = 2;

    @IntDef(
            value = {
                IMS_REGISTRATION_CHANGED_UNREGISTERED,
                IMS_REGISTRATION_CHANGED_ACCESS_NETWORK_CHANGE_FAILED,
                IMS_REGISTRATION_CHANGED_REGISTERED,
            })
    @interface QnsImsRegiEvent {}

    static final int SIP_DIALOG_SESSION_POLICY_NONE = 0;
    static final int SIP_DIALOG_SESSION_POLICY_FOLLOW_VOICE_CALL = 1;
    static final int SIP_DIALOG_SESSION_POLICY_FOLLOW_VIDEO_CALL = 2;

    @IntDef(
            value = {
                    SIP_DIALOG_SESSION_POLICY_NONE,
                    SIP_DIALOG_SESSION_POLICY_FOLLOW_VOICE_CALL,
                    SIP_DIALOG_SESSION_POLICY_FOLLOW_VIDEO_CALL,
            })
    @interface QnsSipDialogSessionPolicy {}

    static final int THRESHOLD_MATCH_TYPE_EQUAL_TO = 0;
    static final int THRESHOLD_EQUAL_OR_LARGER = 1;
    static final int THRESHOLD_EQUAL_OR_SMALLER = 2;

    static final int SIGNAL_MEASUREMENT_AVAILABILITY = 1 << 7;

    static final int SIGNAL_UNAVAILABLE = 0;
    static final int SIGNAL_AVAILABLE = 1;

    static final int DEFAULT_WIFI_BACKHAUL_TIMER = 3000;

    static final int TRANSPORT_TYPE_ALLOWED_WWAN = 0;
    static final int TRANSPORT_TYPE_ALLOWED_IWLAN = 1;
    static final int TRANSPORT_TYPE_ALLOWED_BOTH = 2;

    /** Type of Rat Preference. Default value , Follow the system preference. */
    static final int RAT_PREFERENCE_DEFAULT = 0;
    /** Type of Rat Preference. choose Wi-Fi always */
    static final int RAT_PREFERENCE_WIFI_ONLY = 1;
    /**
     * Type of Rat Preference. choose Wi-Fi when the Wi-Fi Calling is available.(when IMS is
     * registered through the Wi-Fi)
     */
    static final int RAT_PREFERENCE_WIFI_WHEN_WFC_AVAILABLE = 2;
    /** Type of Rat Preference. choose Wi-Fi when no cellular */
    static final int RAT_PREFERENCE_WIFI_WHEN_NO_CELLULAR = 3;
    /** Type of Rat Preference. choose Wi-Fi when cellular is available at home network. */
    static final int RAT_PREFERENCE_WIFI_WHEN_HOME_IS_NOT_AVAILABLE = 4;

    @IntDef(
            value = {
                RAT_PREFERENCE_DEFAULT,
                RAT_PREFERENCE_WIFI_ONLY,
                RAT_PREFERENCE_WIFI_WHEN_WFC_AVAILABLE,
                RAT_PREFERENCE_WIFI_WHEN_NO_CELLULAR,
                RAT_PREFERENCE_WIFI_WHEN_HOME_IS_NOT_AVAILABLE,
            })
    @interface RatPreference {}

    static String callTypeToString(@QnsConstants.QnsCallType int callType) {
        switch (callType) {
            case CALL_TYPE_IDLE:
                return "IDLE";
            case CALL_TYPE_VOICE:
                return "VOICE";
            case CALL_TYPE_VIDEO:
                return "VIDEO";
            case CALL_TYPE_EMERGENCY:
                return "SOS";
        }
        return "";
    }

    static String coverageToString(@QnsConstants.CellularCoverage int coverage) {
        switch (coverage) {
            case COVERAGE_HOME:
                return "HOME";
            case COVERAGE_ROAM:
                return "ROAM";
        }
        return "";
    }

    static String preferenceToString(@QnsConstants.WfcModePreference int preference) {
        switch (preference) {
            case WIFI_ONLY:
                return "WIFI_ONLY";
            case WIFI_PREF:
                return "WIFI_PREF";
            case CELL_PREF:
                return "CELL_PREF";
        }
        return "";
    }

    static String directionToString(@QnsConstants.RoveDirection int direction) {
        if (direction == ROVE_IN) {
            return "ROVE_IN";
        }
        return "ROVE_OUT";
    }

    static String guardingToString(@QnsConstants.QnsGuarding int guarding) {
        switch (guarding) {
            case GUARDING_NONE:
                return "GUARDING_NONE";
            case GUARDING_CELLULAR:
                return "GUARDING_CELL";
            case GUARDING_WIFI:
                return "GUARDING_WIFI";
        }
        return "";
    }

    /**
     * This method coverts call state value from int to string
     *
     * @param state int value of call state.
     * @return returns the string value for the given int call state in parameter.
     */
    static String callStateToString(@Annotation.CallState int state) {
        switch (state) {
            case TelephonyManager.CALL_STATE_IDLE:
                return "CALL_STATE_IDLE";
            case TelephonyManager.CALL_STATE_RINGING:
                return "CALL_STATE_RINGING";
            case TelephonyManager.CALL_STATE_OFFHOOK:
                return "CALL_STATE_OFFHOOK";
            default:
                return "CALL_STATE_UNKNOWN_" + state;
        }
    }

    static String imsRegistrationEventToString(@QnsConstants.QnsImsRegiEvent int event) {
        switch (event) {
            case IMS_REGISTRATION_CHANGED_UNREGISTERED:
                return "IMS_REGISTRATION_CHANGED_UNREGISTERED";
            case IMS_REGISTRATION_CHANGED_ACCESS_NETWORK_CHANGE_FAILED:
                return "IMS_REGISTRATION_CHANGED_ACCESS_NETWORK_CHANGE_FAILED";
            case IMS_REGISTRATION_CHANGED_REGISTERED:
                return "IMS_REGISTRATION_CHANGED_REGISTERED";
        }
        return "";
    }

    /**
     * This method converts AccessNetworkType from int to string.
     *
     * @param type int value of AccessNetworkType
     * @return String value of the access network type.
     */
    static String accessNetworkTypeToString(int type) {
        switch (type) {
            case AccessNetworkType.UNKNOWN:
                return "UNKNOWN";
            case AccessNetworkType.GERAN:
                return "GERAN";
            case AccessNetworkType.UTRAN:
                return "UTRAN";
            case AccessNetworkType.EUTRAN:
                return "EUTRAN";
            case AccessNetworkType.CDMA2000:
                return "CDMA2000";
            case AccessNetworkType.IWLAN:
                return "IWLAN";
            case AccessNetworkType.NGRAN:
                return "NGRAN";
            default:
                return Integer.toString(type);
        }
    }

    /**
     * This method coverts AccessNetworkType from string to int.
     *
     * @param str String value of AccessNetworkType
     * @return Integer value of AccessNetworkType.
     */
    static int accessNetworkTypeFromString(@NonNull String str) {
        switch (str.toUpperCase()) {
            case "GERAN":
                return AccessNetworkType.GERAN;
            case "UTRAN":
                return AccessNetworkType.UTRAN;
            case "EUTRAN":
                return AccessNetworkType.EUTRAN;
            case "CDMA2000":
                return AccessNetworkType.CDMA2000;
            case "IWLAN":
                return AccessNetworkType.IWLAN;
            case "NGRAN":
                return AccessNetworkType.NGRAN;
            default:
                return AccessNetworkType.UNKNOWN;
        }
    }

    /**
     * This method coverts TransportType from int to string.
     *
     * @param transportType Integer value of TransportType
     * @return String value of TransportType.
     */
    static String transportTypeToString(int transportType) {
        switch (transportType) {
            case AccessNetworkConstants.TRANSPORT_TYPE_WWAN:
                return "WWAN";
            case AccessNetworkConstants.TRANSPORT_TYPE_WLAN:
                return "WLAN";
            case AccessNetworkConstants.TRANSPORT_TYPE_INVALID:
                return "INVALID";
            default:
                return Integer.toString(transportType);
        }
    }

    /**
     * Convert data state to string
     *
     * @return The data state in string format.
     */
    static String dataStateToString(@Annotation.DataState int state) {
        switch (state) {
            case TelephonyManager.DATA_DISCONNECTED:
                return "DISCONNECTED";
            case TelephonyManager.DATA_CONNECTING:
                return "CONNECTING";
            case TelephonyManager.DATA_CONNECTED:
                return "CONNECTED";
            case TelephonyManager.DATA_SUSPENDED:
                return "SUSPENDED";
            case TelephonyManager.DATA_DISCONNECTING:
                return "DISCONNECTING";
            case TelephonyManager.DATA_HANDOVER_IN_PROGRESS:
                return "HANDOVERINPROGRESS";
            case TelephonyManager.DATA_UNKNOWN:
                return "UNKNOWN";
        }
        // This is the error case. The well-defined value for UNKNOWN is -1.
        return "UNKNOWN(" + state + ")";
    }

    /**
     * This method converts Network Type to AccessNetworkType.
     *
     * @param networkType integer value of network type
     * @return integer value of AccessNetworkType.
     */
    static int networkTypeToAccessNetworkType(int networkType) {
        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_GSM:
                return AccessNetworkType.GERAN;
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
                return AccessNetworkType.UTRAN;
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
                return AccessNetworkType.CDMA2000;
            case TelephonyManager.NETWORK_TYPE_LTE:
            case TelephonyManager.NETWORK_TYPE_LTE_CA:
                return AccessNetworkType.EUTRAN;
            case TelephonyManager.NETWORK_TYPE_NR:
                return AccessNetworkType.NGRAN;
            case TelephonyManager.NETWORK_TYPE_IWLAN:
                return AccessNetworkType.IWLAN;
            default:
                return AccessNetworkType.UNKNOWN;
        }
    }

    /**
     * This method converts QnsSipDialogSessionPolicy to string.
     *
     * @param policy int value of QnsSipDialogSessionPolicy.
     * @return String value of QnsSipDialogSessionPolicy.
     */
    static String qnsSipDialogSessionPolicyToString(
            @QnsConstants.QnsSipDialogSessionPolicy int policy) {
        switch (policy) {
            case SIP_DIALOG_SESSION_POLICY_NONE:
                return "POLICY_NONE";
            case SIP_DIALOG_SESSION_POLICY_FOLLOW_VOICE_CALL:
                return "POLICY_VOICE";
            case SIP_DIALOG_SESSION_POLICY_FOLLOW_VIDEO_CALL:
                return "POLICY_VIDEO";
            default:
                return "NONE";
        }
    }
}
