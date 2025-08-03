/* Copyright (c) 2015, 2019-2021 The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Changes from Qualcomm Innovation Center are provided under the following license:
 *
 * Copyright (c) 2022-2023 Qualcomm Innovation Center, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted (subject to the limitations in the
 * disclaimer below) provided that the following conditions are met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *
 *   * Neither the name of Qualcomm Innovation Center, Inc. nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE
 * GRANTED BY THIS LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT
 * HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.codeaurora.ims;

import android.telephony.ims.ImsReasonInfo;
/**
 * The class contains definitions for Qti specific constants related to any
 * value added features for video telephony.
 */
/**
 * @hide
 */
public class QtiCallConstants {

    /**
     * Call substate bitmask values
     */

    /* Default case */
    public static final int CALL_SUBSTATE_NONE = 0;

    /* Indicates that the call is connected but audio attribute is suspended */
    public static final int CALL_SUBSTATE_AUDIO_CONNECTED_SUSPENDED = 0x1;

    /* Indicates that the call is connected but video attribute is suspended */
    public static final int CALL_SUBSTATE_VIDEO_CONNECTED_SUSPENDED = 0x2;

    /* Indicates that the call is established but media retry is needed */
    public static final int CALL_SUBSTATE_AVP_RETRY = 0x4;

    /* Indicates that the call is multitasking */
    public static final int CALL_SUBSTATE_MEDIA_PAUSED = 0x8;

    /* Mask containing all the call substate bits set */
    public static final int CALL_SUBSTATE_ALL = CALL_SUBSTATE_AUDIO_CONNECTED_SUSPENDED |
        CALL_SUBSTATE_VIDEO_CONNECTED_SUSPENDED | CALL_SUBSTATE_AVP_RETRY |
        CALL_SUBSTATE_MEDIA_PAUSED;

    /* Call substate extra key name */
    public static final String CALL_SUBSTATE_EXTRA_KEY = "CallSubstate";

    /* Call encryption status extra key. The value will be a boolean. */
    public static final String CALL_ENCRYPTION_EXTRA_KEY = "CallEncryption";

    /* Call fail code extra key name */
    public static final String EXTRAS_KEY_CALL_FAIL_EXTRA_CODE  = "CallFailExtraCode";

    /* Emergency service category extra key name */
    public static final String EXTRAS_KEY_EMERGENCY_SERVICE_CATEGORY  = "EmergencyServiceCategory";

    /*Key to check whether user can mark a call unwanted*/
    public static final String CAN_MARK_UNWANTED_CALL = "CanMarkUnwantedCall";

    /*Key for verstat verification status*/
    public static final String VERSTAT_VERIFICATION_STATUS = "VerstatVerificationStatus";

    public static final String VONR_INFO = "isCall5G";

    /* Call fail error code for handover not feasible */
    public static final int CALL_FAIL_EXTRA_CODE_LTE_3G_HA_FAILED = 149;

    /* Call fail error code for validate Video call number */
    public static final int CALL_FAIL_EXTRA_CODE_LOCAL_VALIDATE_NUMBER = 150;

    /* Call fail error code for Retry CS call*/
    public static final int CALL_FAIL_EXTRA_CODE_CALL_CS_RETRY_REQUIRED =
            ImsReasonInfo.CODE_LOCAL_CALL_CS_RETRY_REQUIRED;

    public static final String SIMLESS_RTT_DOWNGRADE_SUPPORTED = "simless_rtt_downgrade_supported";
    public static final String SIMLESS_RTT_SUPPORTED = "simless_rtt_supported";

    /* Calls are rejected due to low battery */
    public static final int CALL_FAIL_EXTRA_CODE_LOCAL_LOW_BATTERY =
            ImsReasonInfo.CODE_LOCAL_LOW_BATTERY;

    /* call fail error code to retry ims call without rtt */
    public static final int CODE_RETRY_ON_IMS_WITHOUT_RTT = 3001;

    /* Unknown disconnect cause */
    public static final int DISCONNECT_CAUSE_UNSPECIFIED = -1;

    /* Call fail error code due to concurrent calls not possible, {link@ ImsReasonInfo#
     * CODE_CONCURRENT_CALLS_NOT_POSSIBLE} */
    public static final int CODE_CONCURRENT_CALLS_NOT_POSSIBLE = 3002;

    /* Call fail error code to EPSFB failure */
    public static final int CODE_EPSFB_FAILURE = 3003;

    /* Call fail error code to TWAIT_EXPIRED */
    public static final int CODE_TWAIT_EXPIRED = 3004;

    /* Call fail error code to TCP_CONNECTION_REQ */
    public static final int CODE_TCP_CONNECTION_REQ = 3005;

    // Default code to use for additional call info code.
    public static final int CODE_UNSPECIFIED = -1;

    /**
     * Whether the IMS to CS retry is enabled
     * <p>
     * Type: int (0 for false, 1 for true)
     * @hide
     */
    public static final String IMS_TO_CS_RETRY_ENABLED = "qti.settings.cs_retry";

    /* Default camera zoom max */
    public static final int CAMERA_MAX_ZOOM = 100;

    /**
     * Controls dial request route for CS calls.
     * 0 - Use the default routing strategy.
     * 1 - Place the call over CS path
     * 2 - Place the call over PS path
     */
    public static final String EXTRA_CALL_DOMAIN =
            "org.codeaurora.extra.CALL_DOMAIN";
    public static final int DOMAIN_AUTOMATIC = 0;
    public static final int DOMAIN_CS = 1;
    public static final int DOMAIN_PS = 2;

    /**
     * Call supplementary services failures.
     * TODO: Rename the file to QtiCallConstants.java as generic IMS constants are added. This
     *  will be handled when we move the file to vendor/codeaurora/telephony project.
     */
    public static final int ERROR_CALL_CODE_UNSPECIFIED = -1;
    public static final int ERROR_CALL_SUPP_SVC_FAILED = 1;
    public static final int ERROR_CALL_SUPP_SVC_CANCELLED = 2;
    public static final int ERROR_CALL_SUPP_SVC_REINVITE_COLLISION = 3;

    /**
     * Private constructor. This class should not be instantiated.
     */
    private QtiCallConstants() {
    }

    /* UI Orientation Modes */
    public static final int ORIENTATION_MODE_UNSPECIFIED = -1;
    public static final int ORIENTATION_MODE_LANDSCAPE = 1;
    public static final int ORIENTATION_MODE_PORTRAIT = 2;
    public static final int ORIENTATION_MODE_DYNAMIC = 3;

    /* Orientation mode extra key name */
    public static final String ORIENTATION_MODE_EXTRA_KEY = "OrientationMode";

    /* Video call dataUsage Key that holds the data usage consumed by Video call
       on LTE/WLAN RATs */
    public static final String VIDEO_CALL_DATA_USAGE_KEY = "dataUsage";

    /* low battery extra key name that contains a boolean value,
       TRUE meaning battery is low else FALSE */
    public static final String LOW_BATTERY_EXTRA_KEY = "LowBattery";

   /* Upgrade/downgrade of a volte/vt call due to unknown reason. */
    public static final int CAUSE_CODE_UNSPECIFIED = 0;

    /* Upgrade of a volte call on request from local end */
    public static final int CAUSE_CODE_SESSION_MODIFY_UPGRADE_LOCAL_REQ = 1;

    /* Upgrade of a volte call on request from remote end */
    public static final int CAUSE_CODE_SESSION_MODIFY_UPGRADE_REMOTE_REQ = 2;

    /* Downgrade of a vt call on request from local end */
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_LOCAL_REQ = 3;

    /* Downgrade of a vt call on request from remote end */
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_REMOTE_REQ = 4;

    /* Downgrade of a vt call due to RTP/RTCP Timeout for Video stream */
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_RTP_TIMEOUT = 5;

    /* Downgrade of a vt call due to QOS for Video stream */
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_QOS = 6;

    /* Downgrade of a vt call due to PACKET LOSS for Video stream */
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_PACKET_LOSS = 7;

    /* Downgrade of a vt call due to Low throughput for Video stream */
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_LOW_THRPUT = 8;

    /* Downgrade of a vt call due to Thermal Mitigation */
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_THERM_MITIGATION = 9;

    /* Downgrade of a vt call due to Lip-sync */
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_LIPSYNC = 10;

    /* Downgrade of a vt call due to generic error */
    public static final int CAUSE_CODE_SESSION_MODIFY_DOWNGRADE_GENERIC_ERROR = 11;

    /* Session modification cause extra key name */
    public static final String SESSION_MODIFICATION_CAUSE_EXTRA_KEY = "SessionModificationCause";

    /** Modify call error due to low battery
     *  Value should not conflict with videoProvider.
     *      {@link VideoProvider#SESSION_MODIFY_REQUEST_REJECTED*}
     */
    public static final int SESSION_MODIFY_REQUEST_FAILED_LOW_BATTERY = 50;

    /**
     * Local device supports downgrade to voice
     */
    public static final int CAPABILITY_SUPPORTS_DOWNGRADE_TO_VOICE_LOCAL = 0x00800000;

    /**
      * Remote device supports downgrade to voice
      */
    public static final int CAPABILITY_SUPPORTS_DOWNGRADE_TO_VOICE_REMOTE = 0x01000000;

    /**
     * Add participant in an active or conference call option
     */
    public static final int CAPABILITY_ADD_PARTICIPANT = 0x02000000;

    /* Invalid phone Id */
    public static final int INVALID_PHONE_ID = -1;

    /**
     * Extra indicating the Wifi Quality
     * <p>
     * Type: int (one of the VOWIFI_QUALITY_* values)
     */
    public static final String VOWIFI_CALL_QUALITY_EXTRA_KEY = "VoWiFiCallQuality";

    public static final int VOWIFI_QUALITY_NONE = 0;
    public static final int VOWIFI_QUALITY_EXCELLENT = 1;
    public static final int VOWIFI_QUALITY_FAIR = 2;
    public static final int VOWIFI_QUALITY_POOR = 4;

    public static final String EXTRA_PHONE_ID = "phoneId";

    //holds the call fail cause because of which redial is attempted
    public static final String EXTRA_RETRY_CALL_FAIL_REASON = "RetryCallFailReason";
    //holds the radiotech on which lower layers may try attempting redial
    public static final String EXTRA_RETRY_CALL_FAIL_RADIOTECH = "RetryCallFailRadioTech";


   /**
     * Whether RTT visibility is on or off
     * The value 1 - enable, 0 - disable
     * This is set through ImsSettings UI
     */
    public static final String QTI_IMS_RTT_VISIBILITY = "rtt_mode_visibility";

   /**
     * Property for RTT Operating mode
     * For TMO - 0 : Upon Request Mode (Disabled)
     *           1 : Automatic Mode (Full)
     * For Vzw - 1 : Automatic Mode (Full)
     */
    public static final String QTI_IMS_RTT_OPERATING_MODE = "qti.settings.rtt_operation";

    /**
     * Whether dialing normal call is ON or OFF
     * The value 1 - enable (Voice call), 0 - disable (RTT call)
     * This is set through ImsSettings UI
     */
    public static final String QTI_IMS_CAN_START_RTT_CALL =
            "qti.settings.can_start_rtt_call";

    // RTT default phone id
    public static final int RTT_DEFAULT_PHONE_ID = 0;

    // RTT Off
    public static final int RTT_MODE_DISABLED = 0;

    // RTT On
    public static final int RTT_MODE_FULL = 1;

   // RTT Visibility Off
    public static final int RTT_VISIBILITY_DISABLED = 0;

    // RTT Visibility On
    public static final int RTT_VISIBILITY_ENABLED = 1;

    // RTT Call Type Off
    public static final int RTT_CALL_TYPE_RTT = 0;

    // RTT Call Type On
    public static final int RTT_CALL_TYPE_VOICE = 1;

    // RTT Operating mode
    // Dials normal voice call by default and provides an option
    // to upgrade call to RTT in InCallUi.
    public static final int RTT_UPON_REQUEST_MODE = 0;
    // All the calls dialed are RTT calls by default.
    public static final int RTT_AUTOMATIC_MODE = 1;

    // Recorder Auto-Scaling Factor
    public static final int RECORDER_SCALING_FACTOR = 8;

    // Refer to ImsConfigImplBase CONFIG_RESULT_* codes
    public static final int CONFIG_RESULT_NOT_SUPPORTED = 2;
    /**
     * Auto reject call options - for IMS calls on a sub when high priority data
     * is on the other sub
     * Type: int (0 for default, 1 for auto reject, 2 for allow alerting)
     */
    public static final String IMS_AUTO_REJECT_MODE = "qti.settings.auto_reject";
    // auto reject call modes
    // user is notified of incoming call
    public static final int AR_MODE_ALLOW_INCOMING = 0;
    // incoming call is auto rejected
    public static final int AR_MODE_AUTO_REJECT = 1;
    // user is usually alerted of incoming call but auto rejected in certain cases
    // modem may have some optimization
    public static final int AR_MODE_ALLOW_ALERTING = 2;
    // Auto reject call UI item, avoid conflicting values from ImsCallUtils.ConfigItem
    public static final int AUTO_REJECT_CALL_MODE = 1000;
    public static final int QTI_CONFIG_SMS_APP = 1001;
    public static final int QTI_CONFIG_VVM_APP = 1002;
    public static final int QTI_CONFIG_VOWIFI_ROAMING_MODE_PREFERENCE = 1003;
    public static final int CALL_COMPOSER_MODE = 1004;
    public static final int B2C_ENRICHED_CALLING_MODE = 1005;
    public static final int DATA_CHANNEL_MODE = 1006;
    /**
     * An integer key representing the voice over LTE (VoLTE) provisioning restriction status for
     * home network for the associated subscription.
     * Use {@link #PROVISIONING_VALUE_ENABLED} to enable VoLTE provisioning restriction and
     * {@link #PROVISIONING_VALUE_DISABLED} to disable VoLTE provisioning restriction.
     * @see #setProvisioningIntValue(int, int)
     * @see #getProvisioningIntValue(int)
     */
    public static final int VOLTE_PROVISIONING_RESTRICT_HOME = 1007;

    /**
     * An integer key representing the voice over LTE (VoLTE) provisioning restriction status for
     * roaming network for the associated subscription.
     * Use {@link #PROVISIONING_VALUE_ENABLED} to enable VoLTE provisioning restriction and
     * {@link #PROVISIONING_VALUE_DISABLED} to disable VoLTE provisioning restriction.
     * @see #setProvisioningIntValue(int, int)
     * @see #getProvisioningIntValue(int)
     */
    public static final int VOLTE_PROVISIONING_RESTRICT_ROAMING = 1008;

    /**
     * Key values for the pre alerting call elements
     */
    // Intent action broadcasted when pre alerting call elements are available for MT
    public static final String ACTION_PRE_ALERTING_CALL_INFO =
            "org.codeaurora.intent.action.PRE_ALERTING_CALL_INFO";
    // Set for MT pre alerting call (unique ID to match each call)
    // Type: int
    public static String EXTRA_PRE_ALERTING_CALL_TOKEN = "pre_alerting_call_token";
    public static final int INVALID_TOKEN = -1;

    // Set for MT pre alerting call (unique ID to match subscription)
    // Type: int
    public static final String EXTRA_PRE_ALERTING_CALL_PHONE_ID = "pre_alerting_call_phoneId";

    // Set for MT pre alerting call (only set when the call has ended)
    // Type: boolean
    public static String EXTRA_PRE_ALERTING_CALL_ENDED = "pre_alerting_call_ended";

    // set for call composer call
    public static String EXTRA_CALL_COMPOSER_INFO = "call_composer_info";

    // set when subject is added to call composer call
    // Type: String
    public static final String EXTRA_CALL_COMPOSER_SUBJECT = "call_composer_subject";

    // set when priority is added to call composer call
    // Type: int (0 for urgent, 1 for normal)
    public static String EXTRA_CALL_COMPOSER_PRIORITY = "call_composer_priority";

    // set when image url is added to call composer call
    // Type: parcelable Uri
    public static String EXTRA_CALL_COMPOSER_IMAGE = "call_composer_image";

    // set when location is added to call composer call
    // Type: boolean
    public static String EXTRA_CALL_COMPOSER_LOCATION = "call_composer_location";

    // set for circle location
    // Type: float
    public static String EXTRA_CALL_COMPOSER_LOCATION_RADIUS = "call_composer_location_radius";

    // latitude of the location for the call composer call
    // Type: double
    public static String EXTRA_CALL_COMPOSER_LOCATION_LATITUDE = "call_composer_location_latitude";

    // longitude of the location for the call composer call
    // Type: double
    public static String EXTRA_CALL_COMPOSER_LOCATION_LONGITUDE =
            "call_composer_location_longitude";

    // Get when organization header is added to call composer call
    // Type: String
    public static final String EXTRA_CALL_COMPOSER_ORGANIZATION_HEADER =
            "call_composer_organization_header";

    // Set for eCnam info
    public static String EXTRA_CALL_ECNAM = "call_ecnam";

    // Set when ecnam display name is added
    // Type: String
    public static final String EXTRA_CALL_ECNAM_DISPLAY_NAME = "call_ecnam_display_name";

    // Set when ecnam icon url is added
    // Type: parcelable Uri
    public static final String EXTRA_CALL_ECNAM_ICON = "call_ecnam_icon";

    // Set when ecnam info url is added
    // Type: parcelable Uri
    public static final String EXTRA_CALL_ECNAM_INFO = "call_ecnam_info";

    // Set when ecnam card url is added
    // Type: parcelable Uri
    public static final String EXTRA_CALL_ECNAM_CARD = "call_ecnam_card";

    /**
     * User setting to control whether dialing call composer calls are allowed
     * Type: int (0 for disable, 1 for enabled);
     */
    public static final String IMS_CALL_COMPOSER = "qti.settings.call_composer";
    public static final int CALL_COMPOSER_DISABLED = 0;
    public static final int CALL_COMPOSER_ENABLED = 1;

    /**
     * User setting to control whether b2c enriched calling is allowed
     * Type: int (0 for disable, 1 for enabled);
     */
    public static final String B2C_ENRICHED_CALLING = "qti.settings.b2c_enriched_calling";
    public static final int B2C_ENRICHED_CALLING_DISABLED = 0;
    public static final int B2C_ENRICHED_CALLING_ENABLED = 1;

    /**
     * User setting to control whether ims data channel is allowed
     * Type: int (0 for disable, 1 for enabled);
     */
    public static final String DATA_CHANNEL = "qti.settings.data_channel";
    public static final int DATA_CHANNEL_DISABLED = 0;
    public static final int DATA_CHANNEL_ENABLED = 1;

    /**
     * Constants used by clients as part of registration status change indication.
     * Below constants will be notified when modem is unable to get the geo location information.
     */
    // This error will be sent when time out received to get the Longitude and Latitude
    // from GPS engine.
    public static final int REG_ERROR_GEO_LOCATION_STATUS_TIMEOUT = 2000;
    // This error will be sent when modem is able to get the Longitude, Latitude and unable to
    // get the information (country, country code, postal code etc.) from telephony.
    public static final int REG_ERROR_GEO_LOCATION_STATUS_NO_CIVIC_ADDRESS = 2001;
    // This error will be received if GPS is disabled from UI.
    public static final int REG_ERROR_GEO_LOCATION_STATUS_ENGINE_LOCK = 2002;
    // This is success case, received when all the GPS errors are resolved.
    public static final int REG_ERROR_GEO_LOCATION_STATUS_RESOLVED = 2003;

    /* CRS type extra key */
    public static final String EXTRA_CRS_TYPE = "crsType";
    /* Original call type extra key */
    public static final String EXTRA_ORIGINAL_CALL_TYPE = "originalCallType";
    /* Slience UI before CRS RTP come extra key */
    public static final String EXTRA_IS_PREPARATORY = "isPreparatory";
    /* Ims vendor callId extra key */
    public static final String EXTRA_IMS_CALL_ID = "callId";
    //INVALID if CrsData is invalid, play local ring.
    public static final int CRS_TYPE_INVALID = 0;
    //AUDIO if only audio will be played.
    public static final int CRS_TYPE_AUDIO = 1 << 0;
    //VIDEO if only video will be played.
    public static final int CRS_TYPE_VIDEO = 1 << 1;

    //Call progress info constants.
    public static final int CALL_PROGRESS_INFO_TYPE_INVALID = -1;
    public static final int CALL_PROGRESS_INFO_TYPE_CALL_REJ_Q850 = 0;
    public static final int CALL_PROGRESS_INFO_TYPE_CALL_WAITING = 1;
    public static final int CALL_PROGRESS_INFO_TYPE_CALL_FORWARDING = 2;
    public static final int CALL_PROGRESS_INFO_TYPE_REMOTE_AVAILABLE = 3;
    //Call progress info call rejection code
    public static final int CALL_REJECTION_CODE_INVALID = -1;
    //Call progress info extras
    public static final String EXTRAS_CALL_PROGRESS_INFO_TYPE = "CallProgInfoType";
    public static final String EXTRAS_CALL_PROGRESS_REASON_CODE = "CallProgReasonCode";
    public static final String EXTRAS_CALL_PROGRESS_REASON_TEXT = "CallProgReasonText";

    // Call audio quality constants.
    public static final int CALL_AUDIO_QUALITY_NO_HD = 0;
    public static final int CALL_AUDIO_QUALITY_HD = 1;
    public static final int CALL_AUDIO_QUALITY_HD_PLUS = 2;

    /*Sms call back constants*/
    public static final int SCBM_STATUS_EXIT = 0;
    public static final int SCBM_STATUS_ENTER = 1;

    /**
     * Intent action broadcasted when Sms Callback Mode is changed
     */
    public static final String ACTION_SMS_CALLBACK_MODE =
            "org.codeaurora.intent.action.SMS_CALLBACK_MODE";
    /**
     * Extra key for when modem enters/exits sms callback mode.
     * Type: boolean
     */
    public static String EXTRA_SMS_CALLBACK_MODE = "sms_callback_mode";

    /* Call Forward service related error.
     * This error code will be notified when user tries to
     *  - activate CFx before register operation
     *  - query CFNL while network does not support CFNL feature */
    public static final int CODE_UT_CF_SERVICE_NOT_REGISTERED = 850;
    // Data Channel call information
    public static final String EXTRA_DATA_CHANNEL_MODEM_CALL_ID = "modemCallId";
    public static final String EXTRA_IS_DATA_CHANNEL_CALL = "isDcCall";

    // Intent action broadcasted when essential records are loaded.
    // requires permission "com.qti.permission.RECEIVE_ESSENTIAL_RECORDS_LOADED".
    public static final String ACTION_ESSENTIAL_RECORDS_LOADED =
        "org.codeaurora.intent.action.ESSENTIAL_RECORDS_LOADED";

    // Video online service
    public static final String EXTRA_VIDEO_ONLINE_SERVICE_SUPPORTED = "isVosSupported";

    /**
     * The value of voice/video/text is defined according to the SRTP categories
     * and the name in the extra is defined by QC IMS service.
     */
    public static final String EXTRAS_SRTP_ENCRYPTION_CATEGORY = "srtpEncryptionCategory";
    public static final int SRTP_CATEGORY_UNENCRYPTED = 0;
    public static final int SRTP_CATEGORY_VOICE = 1 << 0;
    public static final int SRTP_CATEGORY_VIDEO = 1 << 1;
    public static final int SRTP_CATEGORY_TEXT = 1 << 2;

    // Call reason
    public static final String EXTRA_CALL_REASON = "callReason";
}
