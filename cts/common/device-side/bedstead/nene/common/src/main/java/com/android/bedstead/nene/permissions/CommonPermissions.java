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

package com.android.bedstead.nene.permissions;

/** Permissions helper methods common to host and device. */
public class CommonPermissions {

    CommonPermissions() {

    }

    /** See {@code Manifest#READ_CONTACTS} */
    public static final String READ_CONTACTS = "android.permission.READ_CONTACTS";

    /** See {@code Manifest#WRITE_CONTACTS} */
    public static final String WRITE_CONTACTS = "android.permission.WRITE_CONTACTS";

    /** See {@code Manifest#SET_DEFAULT_ACCOUNT_FOR_CONTACTS} */
    public static final String SET_DEFAULT_ACCOUNT_FOR_CONTACTS =
            "android.permission.SET_DEFAULT_ACCOUNT_FOR_CONTACTS";

    /** See {@code Manifest#READ_CALENDAR} */
    public static final String READ_CALENDAR = "android.permission.READ_CALENDAR";

    /** See {@code Manifest#WRITE_CALENDAR} */
    public static final String WRITE_CALENDAR = "android.permission.WRITE_CALENDAR";

    /** See {@code Manifest#ACCESS_MESSAGES_ON_ICC} */
    public static final String ACCESS_MESSAGES_ON_ICC = "android.permission"
            + ".ACCESS_MESSAGES_ON_ICC";

    /** See {@code Manifest#SEND_SMS} */
    public static final String SEND_SMS = "android.permission.SEND_SMS";

    /** See {@code Manifest#RECEIVE_SMS} */
    public static final String RECEIVE_SMS = "android.permission.RECEIVE_SMS";

    /** See {@code Manifest#READ_SMS} */
    public static final String READ_SMS = "android.permission.READ_SMS";

    /** See {@code Manifest#RECEIVE_WAP_PUSH} */
    public static final String RECEIVE_WAP_PUSH = "android.permission.RECEIVE_WAP_PUSH";

    /** See {@code Manifest#RECEIVE_MMS} */
    public static final String RECEIVE_MMS = "android.permission.RECEIVE_MMS";

    /** See {@code Manifest#BIND_CELL_BROADCAST_SERVICE} */
    public static final String BIND_CELL_BROADCAST_SERVICE = "android.permission"
            + ".BIND_CELL_BROADCAST_SERVICE";

    /** See {@code Manifest#READ_CELL_BROADCASTS} */
    public static final String READ_CELL_BROADCASTS = "android.permission.READ_CELL_BROADCASTS";

    /** See {@code Manifest#WRITE_EXTERNAL_STORAGE} */
    public static final String WRITE_EXTERNAL_STORAGE = "android.permission.WRITE_EXTERNAL_STORAGE";

    /** See {@code Manifest#ACCESS_MEDIA_LOCATION} */
    public static final String ACCESS_MEDIA_LOCATION = "android.permission.ACCESS_MEDIA_LOCATION";

    /** See {@code Manifest#WRITE_OBB} */
    public static final String WRITE_OBB = "android.permission.WRITE_OBB";

    /** See {@code Manifest#MANAGE_EXTERNAL_STORAGE} */
    public static final String MANAGE_EXTERNAL_STORAGE = "android.permission"
            + ".MANAGE_EXTERNAL_STORAGE";

    /** See {@code Manifest#MANAGE_MEDIA} */
    public static final String MANAGE_MEDIA = "android.permission.MANAGE_MEDIA";

    /** See {@code Manifest#ACCESS_FINE_LOCATION} */
    public static final String ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION";

    /** See {@code Manifest#ACCESS_COARSE_LOCATION} */
    public static final String ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION";

    /** See {@code Manifest#ACCESS_BACKGROUND_LOCATION} */
    public static final String ACCESS_BACKGROUND_LOCATION =
            "android.permission.ACCESS_BACKGROUND_LOCATION";

    /** See {@code Manifest#ACCESS_IMS_CALL_SERVICE} */
    public static final String ACCESS_IMS_CALL_SERVICE = "android.permission"
            + ".ACCESS_IMS_CALL_SERVICE";

    /** See {@code Manifest#PERFORM_IMS_SINGLE_REGISTRATION} */
    public static final String PERFORM_IMS_SINGLE_REGISTRATION = "android.permission"
            + ".PERFORM_IMS_SINGLE_REGISTRATION";

    /** See {@code Manifest#READ_CALL_LOG} */
    public static final String READ_CALL_LOG = "android.permission.READ_CALL_LOG";

    /** See {@code Manifest#PROCESS_OUTGOING_CALLS} */
    public static final String PROCESS_OUTGOING_CALLS = "android.permission.PROCESS_OUTGOING_CALLS";

    /** See {@code Manifest#READ_PHONE_STATE} */
    public static final String READ_PHONE_STATE = "android.permission.READ_PHONE_STATE";

    /** See {@code Manifest#READ_BASIC_PHONE_STATE} */
    public static final String READ_BASIC_PHONE_STATE = "android.permission.READ_BASIC_PHONE_STATE";

    /** See {@code Manifest#READ_PHONE_NUMBERS} */
    public static final String READ_PHONE_NUMBERS = "android.permission.READ_PHONE_NUMBERS";

    /** See {@code Manifest#CALL_PHONE} */
    public static final String CALL_PHONE = "android.permission.CALL_PHONE";

    /** See {@code Manifest#ADD_VOICEMAIL} */
    public static final String ADD_VOICEMAIL = "com.android.voicemail.permission.ADD_VOICEMAIL";

    /** See {@code Manifest#USE_SIP} */
    public static final String USE_SIP = "android.permission.USE_SIP";

    /** See {@code Manifest#ANSWER_PHONE_CALLS} */
    public static final String ANSWER_PHONE_CALLS = "android.permission.ANSWER_PHONE_CALLS";

    /** See {@code Manifest#MANAGE_OWN_CALLS} */
    public static final String MANAGE_OWN_CALLS = "android.permission.MANAGE_OWN_CALLS";

    /** See {@code Manifest#CALL_COMPANION_APP} */
    public static final String CALL_COMPANION_APP = "android.permission.CALL_COMPANION_APP";

    /** See {@code Manifest#EXEMPT_FROM_AUDIO_RECORD_RESTRICTIONS} */
    public static final String EXEMPT_FROM_AUDIO_RECORD_RESTRICTIONS = "android.permission"
            + ".EXEMPT_FROM_AUDIO_RECORD_RESTRICTIONS";

    /** See {@code Manifest#ACCEPT_HANDOVER} */
    public static final String ACCEPT_HANDOVER = "android.permission.ACCEPT_HANDOVER";

    /** See {@code Manifest#RECORD_AUDIO} */
    public static final String RECORD_AUDIO = "android.permission.RECORD_AUDIO";

    /** See {@code Manifest#RECORD_BACKGROUND_AUDIO} */
    public static final String RECORD_BACKGROUND_AUDIO =
            "android.permission.RECORD_BACKGROUND_AUDIO";

    /** See {@code Manifest#ACTIVITY_RECOGNITION} */
    public static final String ACTIVITY_RECOGNITION = "android.permission.ACTIVITY_RECOGNITION";

    /** See {@code Manifest#ACCESS_UCE_PRESENCE_SERVICE} */
    public static final String ACCESS_UCE_PRESENCE_SERVICE =
            "android.permission.ACCESS_UCE_PRESENCE_SERVICE";

    /** See {@code Manifest#ACCESS_UCE_OPTIONS_SERVICE} */
    public static final String ACCESS_UCE_OPTIONS_SERVICE =
            "android.permission.ACCESS_UCE_OPTIONS_SERVICE";

    /** See {@code Manifest#CAMERA} */
    public static final String CAMERA = "android.permission.CAMERA";

    /** See {@code Manifest#BACKGROUND_CAMERA} */
    public static final String BACKGROUND_CAMERA = "android.permission.BACKGROUND_CAMERA";

    /** See {@code Manifest#SYSTEM_CAMERA} */
    public static final String SYSTEM_CAMERA = "android.permission.SYSTEM_CAMERA";

    /** See {@code Manifest#CAMERA_OPEN_CLOSE_LISTENER} */
    public static final String CAMERA_OPEN_CLOSE_LISTENER = "android.permission"
            + ".CAMERA_OPEN_CLOSE_LISTENER";

    /** See {@code Manifest#HIGH_SAMPLING_RATE_SENSORS} */
    public static final String HIGH_SAMPLING_RATE_SENSORS =
            "android.permission.HIGH_SAMPLING_RATE_SENSORS";

    /** See {@code Manifest#BODY_SENSORS} */
    public static final String BODY_SENSORS = "android.permission.BODY_SENSORS";

    /** See {@code Manifest#USE_FINGERPRINT} */
    public static final String USE_FINGERPRINT = "android.permission.USE_FINGERPRINT";

    /** See {@code Manifest#USE_BIOMETRIC} */
    public static final String USE_BIOMETRIC = "android.permission.USE_BIOMETRIC";

    /** See {@code Manifest#POST_NOTIFICATIONS} */
    public static final String POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS";

    /** See {@code Manifest#READ_PROFILE} */
    public static final String READ_PROFILE = "android.permission.READ_PROFILE";

    /** See {@code Manifest#WRITE_PROFILE} */
    public static final String WRITE_PROFILE = "android.permission.WRITE_PROFILE";

    /** See {@code Manifest#READ_SOCIAL_STREAM} */
    public static final String READ_SOCIAL_STREAM = "android.permission.READ_SOCIAL_STREAM";

    /** See {@code Manifest#WRITE_SOCIAL_STREAM} */
    public static final String WRITE_SOCIAL_STREAM = "android.permission.WRITE_SOCIAL_STREAM";

    /** See {@code Manifest#READ_USER_DICTIONARY} */
    public static final String READ_USER_DICTIONARY = "android.permission.READ_USER_DICTIONARY";

    /** See {@code Manifest#WRITE_USER_DICTIONARY} */
    public static final String WRITE_USER_DICTIONARY = "android.permission.WRITE_USER_DICTIONARY";

    /** See {@code Manifest#WRITE_SMS} */
    public static final String WRITE_SMS = "android.permission.WRITE_SMS";

    /** See {@code Manifest#READ_HISTORY_BOOKMARKS} */
    public static final String READ_HISTORY_BOOKMARKS =
            "com.android.browser.permission.READ_HISTORY_BOOKMARKS";

    /** See {@code Manifest#WRITE_HISTORY_BOOKMARKS} */
    public static final String WRITE_HISTORY_BOOKMARKS =
            "com.android.browser.permission.WRITE_HISTORY_BOOKMARKS";

    /** See {@code Manifest#AUTHENTICATE_ACCOUNTS} */
    public static final String AUTHENTICATE_ACCOUNTS = "android.permission.AUTHENTICATE_ACCOUNTS";

    /** See {@code Manifest#MANAGE_ACCOUNTS} */
    public static final String MANAGE_ACCOUNTS = "android.permission.MANAGE_ACCOUNTS";

    /** See {@code Manifest#USE_CREDENTIALS} */
    public static final String USE_CREDENTIALS = "android.permission.USE_CREDENTIALS";

    /** See {@code Manifest#SUBSCRIBED_FEEDS_READ} */
    public static final String SUBSCRIBED_FEEDS_READ = "android.permission.SUBSCRIBED_FEEDS_READ";

    /** See {@code Manifest#SUBSCRIBED_FEEDS_WRITE} */
    public static final String SUBSCRIBED_FEEDS_WRITE = "android.permission"
            + ".SUBSCRIBED_FEEDS_WRITE";

    /** See {@code Manifest#FLASHLIGHT} */
    public static final String FLASHLIGHT = "android.permission.FLASHLIGHT";

    /** See {@code Manifest#SEND_RESPOND_VIA_MESSAGE} */
    public static final String SEND_RESPOND_VIA_MESSAGE =
            "android.permission.SEND_RESPOND_VIA_MESSAGE";

    /** See {@code Manifest#SEND_SMS_NO_CONFIRMATION} */
    public static final String SEND_SMS_NO_CONFIRMATION = "android.permission"
            + ".SEND_SMS_NO_CONFIRMATION";

    /** See {@code Manifest#CARRIER_FILTER_SMS} */
    public static final String CARRIER_FILTER_SMS = "android.permission.CARRIER_FILTER_SMS";

    /** See {@code Manifest#RECEIVE_EMERGENCY_BROADCAST} */
    public static final String RECEIVE_EMERGENCY_BROADCAST =
            "android.permission.RECEIVE_EMERGENCY_BROADCAST";

    /** See {@code Manifest#RECEIVE_BLUETOOTH_MAP} */
    public static final String RECEIVE_BLUETOOTH_MAP = "android.permission.RECEIVE_BLUETOOTH_MAP";

    /** See {@code Manifest#MODIFY_CELL_BROADCASTS} */
    public static final String MODIFY_CELL_BROADCASTS =
            "android.permission.MODIFY_CELL_BROADCASTS";

    /** See {@code Manifest#SET_ALARM} */
    public static final String SET_ALARM = "com.android.alarm.permission.SET_ALARM";

    /** See {@code Manifest#WRITE_VOICEMAIL} */
    public static final String WRITE_VOICEMAIL = "com.android.voicemail.permission.WRITE_VOICEMAIL";

    /** See {@code Manifest#READ_VOICEMAIL} */
    public static final String READ_VOICEMAIL = "com.android.voicemail.permission.READ_VOICEMAIL";
    /** See {@code Manifest#ACCESS_LOCATION_EXTRA_COMMANDS} */
    public static final String ACCESS_LOCATION_EXTRA_COMMANDS = "android.permission"
            + ".ACCESS_LOCATION_EXTRA_COMMANDS";
    /** See {@code Manifest#INSTALL_LOCATION_PROVIDER} */
    public static final String INSTALL_LOCATION_PROVIDER = "android.permission"
            + ".INSTALL_LOCATION_PROVIDER";
    /** See {@code Manifest#INSTALL_LOCATION_TIME_ZONE_PROVIDER_SERVICE} */
    public static final String INSTALL_LOCATION_TIME_ZONE_PROVIDER_SERVICE = "android"
            + ".permission.INSTALL_LOCATION_TIME_ZONE_PROVIDER_SERVICE";
    /** See {@code Manifest#BIND_TIME_ZONE_PROVIDER_SERVICE} */
    public static final String BIND_TIME_ZONE_PROVIDER_SERVICE =
            "android.permission.BIND_TIME_ZONE_PROVIDER_SERVICE";
    /** See {@code Manifest#HDMI_CEC} */
    public static final String HDMI_CEC = "android.permission.HDMI_CEC";
    /** See {@code Manifest#LOCATION_HARDWARE} */
    public static final String LOCATION_HARDWARE = "android.permission.LOCATION_HARDWARE";
    /** See {@code Manifest#ACCESS_CONTEXT_HUB} */
    public static final String ACCESS_CONTEXT_HUB = "android.permission.ACCESS_CONTEXT_HUB";
    /** See {@code Manifest#ACCESS_MOCK_LOCATION} */
    public static final String ACCESS_MOCK_LOCATION = "android.permission.ACCESS_MOCK_LOCATION";
    /** See {@code Manifest#AUTOMOTIVE_GNSS_CONTROLS} */
    public static final String AUTOMOTIVE_GNSS_CONTROLS =
            "android.permission.AUTOMOTIVE_GNSS_CONTROLS";
    /** See {@code Manifest#INTERNET} */
    public static final String INTERNET = "android.permission.INTERNET";
    /** See {@code Manifest#ACCESS_NETWORK_STATE} */
    public static final String ACCESS_NETWORK_STATE = "android.permission.ACCESS_NETWORK_STATE";
    /** See {@code Manifest#ACCESS_WIFI_STATE} */
    public static final String ACCESS_WIFI_STATE = "android.permission.ACCESS_WIFI_STATE";
    /** See {@code Manifest#CHANGE_WIFI_STATE} */
    public static final String CHANGE_WIFI_STATE = "android.permission.CHANGE_WIFI_STATE";
    /** See {@code Manifest#MANAGE_WIFI_AUTO_JOIN} */
    public static final String MANAGE_WIFI_AUTO_JOIN = "android.permission.MANAGE_WIFI_AUTO_JOIN";
    /** See {@code Manifest#MANAGE_IPSEC_TUNNELS} */
    public static final String MANAGE_IPSEC_TUNNELS = "android.permission.MANAGE_IPSEC_TUNNELS";
    /** See {@code Manifest#MANAGE_TEST_NETWORKS} */
    public static final String MANAGE_TEST_NETWORKS = "android.permission.MANAGE_TEST_NETWORKS";
    /** See {@code Manifest#READ_WIFI_CREDENTIAL} */
    public static final String READ_WIFI_CREDENTIAL = "android.permission.READ_WIFI_CREDENTIAL";
    /** See {@code Manifest#TETHER_PRIVILEGED} */
    public static final String TETHER_PRIVILEGED = "android.permission.TETHER_PRIVILEGED";
    /** See {@code Manifest#RECEIVE_WIFI_CREDENTIAL_CHANGE} */
    public static final String RECEIVE_WIFI_CREDENTIAL_CHANGE = "android.permission"
            + ".RECEIVE_WIFI_CREDENTIAL_CHANGE";
    /** See {@code Manifest#OVERRIDE_WIFI_CONFIG} */
    public static final String OVERRIDE_WIFI_CONFIG = "android.permission.OVERRIDE_WIFI_CONFIG";
    /** See {@code Manifest#SCORE_NETWORKS} */
    public static final String SCORE_NETWORKS = "android.permission.SCORE_NETWORKS";
    /** See {@code Manifest#REQUEST_NETWORK_SCORES} */
    public static final String REQUEST_NETWORK_SCORES = "android.permission.REQUEST_NETWORK_SCORES";
    /** See {@code Manifest#RESTART_WIFI_SUBSYSTEM} */
    public static final String RESTART_WIFI_SUBSYSTEM = "android.permission"
            + ".RESTART_WIFI_SUBSYSTEM";
    /** See {@code Manifest#NETWORK_AIRPLANE_MODE} */
    public static final String NETWORK_AIRPLANE_MODE = "android.permission.NETWORK_AIRPLANE_MODE";
    /** See {@code Manifest#NETWORK_STACK} */
    public static final String NETWORK_STACK = "android.permission.NETWORK_STACK";
    /** See {@code Manifest#OBSERVE_NETWORK_POLICY} */
    public static final String OBSERVE_NETWORK_POLICY = "android.permission"
            + ".OBSERVE_NETWORK_POLICY";
    /** See {@code Manifest#NETWORK_FACTORY} */
    public static final String NETWORK_FACTORY = "android.permission.NETWORK_FACTORY";
    /** See {@code Manifest#NETWORK_STATS_PROVIDER} */
    public static final String NETWORK_STATS_PROVIDER = "android.permission.NETWORK_STATS_PROVIDER";
    /** See {@code Manifest#NETWORK_SETTINGS} */
    public static final String NETWORK_SETTINGS = "android.permission.NETWORK_SETTINGS";
    /** See {@code Manifest#RADIO_SCAN_WITHOUT_LOCATION} */
    public static final String RADIO_SCAN_WITHOUT_LOCATION =
            "android.permission.RADIO_SCAN_WITHOUT_LOCATION";
    /** See {@code Manifest#NETWORK_SETUP_WIZARD} */
    public static final String NETWORK_SETUP_WIZARD = "android.permission.NETWORK_SETUP_WIZARD";
    /** See {@code Manifest#NETWORK_MANAGED_PROVISIONING} */
    public static final String NETWORK_MANAGED_PROVISIONING = "android.permission"
            + ".NETWORK_MANAGED_PROVISIONING";
    /** See {@code Manifest#NETWORK_CARRIER_PROVISIONING} */
    public static final String NETWORK_CARRIER_PROVISIONING =
            "android.permission.NETWORK_CARRIER_PROVISIONING";
    /** See {@code Manifest#ACCESS_LOWPAN_STATE} */
    public static final String ACCESS_LOWPAN_STATE = "android.permission.ACCESS_LOWPAN_STATE";
    /** See {@code Manifest#CHANGE_LOWPAN_STATE} */
    public static final String CHANGE_LOWPAN_STATE = "android.permission.CHANGE_LOWPAN_STATE";
    /** See {@code Manifest#READ_LOWPAN_CREDENTIAL} */
    public static final String READ_LOWPAN_CREDENTIAL = "android.permission.READ_LOWPAN_CREDENTIAL";
    /** See {@code Manifest#MANAGE_LOWPAN_INTERFACES} */
    public static final String MANAGE_LOWPAN_INTERFACES = "android.permission"
            + ".MANAGE_LOWPAN_INTERFACES";
    /** See {@code Manifest#NETWORK_BYPASS_PRIVATE_DNS} */
    public static final String NETWORK_BYPASS_PRIVATE_DNS =
            "android.permission.NETWORK_BYPASS_PRIVATE_DNS";
    /** See {@code Manifest#WIFI_SET_DEVICE_MOBILITY_STATE} */
    public static final String WIFI_SET_DEVICE_MOBILITY_STATE =
            "android.permission.WIFI_SET_DEVICE_MOBILITY_STATE";
    /** See {@code Manifest#WIFI_UPDATE_USABILITY_STATS_SCORE} */
    public static final String WIFI_UPDATE_USABILITY_STATS_SCORE = "android.permission"
            + ".WIFI_UPDATE_USABILITY_STATS_SCORE";
    /** See {@code Manifest#WIFI_UPDATE_COEX_UNSAFE_CHANNELS} */
    public static final String WIFI_UPDATE_COEX_UNSAFE_CHANNELS = "android.permission"
            + ".WIFI_UPDATE_COEX_UNSAFE_CHANNELS";
    /** See {@code Manifest#WIFI_ACCESS_COEX_UNSAFE_CHANNELS} */
    public static final String WIFI_ACCESS_COEX_UNSAFE_CHANNELS = "android.permission"
            + ".WIFI_ACCESS_COEX_UNSAFE_CHANNELS";
    /** See {@code Manifest#MANAGE_WIFI_COUNTRY_CODE} */
    public static final String MANAGE_WIFI_COUNTRY_CODE =
            "android.permission.MANAGE_WIFI_COUNTRY_CODE";
    /** See {@code Manifest#CONTROL_OEM_PAID_NETWORK_PREFERENCE} */
    public static final String CONTROL_OEM_PAID_NETWORK_PREFERENCE =
            "android.permission.CONTROL_OEM_PAID_NETWORK_PREFERENCE";
    /** See {@code Manifest#BLUETOOTH} */
    public static final String BLUETOOTH = "android.permission.BLUETOOTH";
    /** See {@code Manifest#BLUETOOTH_SCAN} */
    public static final String BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN";
    /** See {@code Manifest#BLUETOOTH_CONNECT} */
    public static final String BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT";
    /** See {@code Manifest#BLUETOOTH_ADVERTISE} */
    public static final String BLUETOOTH_ADVERTISE = "android.permission.BLUETOOTH_ADVERTISE";
    /** See {@code Manifest#UWB_RANGING} */
    public static final String UWB_RANGING = "android.permission.UWB_RANGING";
    /** See {@code Manifest#NEARBY_WIFI_DEVICES} */
    public static final String NEARBY_WIFI_DEVICES = "android.permission.NEARBY_WIFI_DEVICES";
    /** See {@code Manifest#SUSPEND_APPS} */
    public static final String SUSPEND_APPS = "android.permission.SUSPEND_APPS";
    /** See {@code Manifest#BLUETOOTH_ADMIN} */
    public static final String BLUETOOTH_ADMIN = "android.permission.BLUETOOTH_ADMIN";
    /** See {@code Manifest#BLUETOOTH_PRIVILEGED} */
    public static final String BLUETOOTH_PRIVILEGED = "android.permission.BLUETOOTH_PRIVILEGED";
    /** See {@code Manifest#BLUETOOTH_MAP} */
    public static final String BLUETOOTH_MAP = "android.permission.BLUETOOTH_MAP";
    /** See {@code Manifest#BLUETOOTH_STACK} */
    public static final String BLUETOOTH_STACK = "android.permission.BLUETOOTH_STACK";
    /** See {@code Manifest#VIRTUAL_INPUT_DEVICE} */
    public static final String VIRTUAL_INPUT_DEVICE = "android.permission.VIRTUAL_INPUT_DEVICE";
    /** See {@code Manifest#NFC} */
    public static final String NFC = "android.permission.NFC";
    /** See {@code Manifest#NFC_TRANSACTION_EVENT} */
    public static final String NFC_TRANSACTION_EVENT = "android.permission.NFC_TRANSACTION_EVENT";
    /** See {@code Manifest#NFC_PREFERRED_PAYMENT_INFO} */
    public static final String NFC_PREFERRED_PAYMENT_INFO =
            "android.permission.NFC_PREFERRED_PAYMENT_INFO";
    /** See {@code Manifest#NFC_SET_CONTROLLER_ALWAYS_ON} */
    public static final String NFC_SET_CONTROLLER_ALWAYS_ON = "android.permission"
            + ".NFC_SET_CONTROLLER_ALWAYS_ON";
    /** See {@code Manifest#SECURE_ELEMENT_PRIVILEGED_OPERATION} */
    public static final String SECURE_ELEMENT_PRIVILEGED_OPERATION = "android.permission"
            + ".SECURE_ELEMENT_PRIVILEGED_OPERATION";
    /** See {@code Manifest#CONNECTIVITY_INTERNAL} */
    public static final String CONNECTIVITY_INTERNAL = "android.permission.CONNECTIVITY_INTERNAL";
    /** See {@code Manifest#CONNECTIVITY_USE_RESTRICTED_NETWORKS} */
    public static final String CONNECTIVITY_USE_RESTRICTED_NETWORKS =
            "android.permission.CONNECTIVITY_USE_RESTRICTED_NETWORKS";
    /** See {@code Manifest#NETWORK_SIGNAL_STRENGTH_WAKEUP} */
    public static final String NETWORK_SIGNAL_STRENGTH_WAKEUP =
            "android.permission.NETWORK_SIGNAL_STRENGTH_WAKEUP";
    /** See {@code Manifest#PACKET_KEEPALIVE_OFFLOAD} */
    public static final String PACKET_KEEPALIVE_OFFLOAD =
            "android.permission.PACKET_KEEPALIVE_OFFLOAD";
    /** See {@code Manifest#RECEIVE_DATA_ACTIVITY_CHANGE} */
    public static final String RECEIVE_DATA_ACTIVITY_CHANGE =
            "android.permission.RECEIVE_DATA_ACTIVITY_CHANGE";
    /** See {@code Manifest#LOOP_RADIO} */
    public static final String LOOP_RADIO = "android.permission.LOOP_RADIO";
    /** See {@code Manifest#NFC_HANDOVER_STATUS} */
    public static final String NFC_HANDOVER_STATUS = "android.permission.NFC_HANDOVER_STATUS";
    /** See {@code Manifest#MANAGE_BLUETOOTH_WHEN_WIRELESS_CONSENT_REQUIRED} */
    public static final String MANAGE_BLUETOOTH_WHEN_WIRELESS_CONSENT_REQUIRED =
            "android.permission.MANAGE_BLUETOOTH_WHEN_WIRELESS_CONSENT_REQUIRED";
    /** See {@code Manifest#ENABLE_TEST_HARNESS_MODE} */
    public static final String ENABLE_TEST_HARNESS_MODE =
            "android.permission.ENABLE_TEST_HARNESS_MODE";
    /** See {@code Manifest#GET_ACCOUNTS} */
    public static final String GET_ACCOUNTS = "android.permission.GET_ACCOUNTS";
    /** See {@code Manifest#ACCOUNT_MANAGER} */
    public static final String ACCOUNT_MANAGER = "android.permission.ACCOUNT_MANAGER";
    /** See {@code Manifest#CHANGE_WIFI_MULTICAST_STATE} */
    public static final String CHANGE_WIFI_MULTICAST_STATE = "android.permission"
            + ".CHANGE_WIFI_MULTICAST_STATE";
    /** See {@code Manifest#VIBRATE} */
    public static final String VIBRATE = "android.permission.VIBRATE";
    /** See {@code Manifest#VIBRATE_ALWAYS_ON} */
    public static final String VIBRATE_ALWAYS_ON = "android.permission.VIBRATE_ALWAYS_ON";
    /** See {@code Manifest#ACCESS_VIBRATOR_STATE} */
    public static final String ACCESS_VIBRATOR_STATE = "android.permission.ACCESS_VIBRATOR_STATE";
    /** See {@code Manifest#WAKE_LOCK} */
    public static final String WAKE_LOCK = "android.permission.WAKE_LOCK";
    /** See {@code Manifest#TRANSMIT_IR} */
    public static final String TRANSMIT_IR = "android.permission.TRANSMIT_IR";
    /** See {@code Manifest#MODIFY_AUDIO_SETTINGS} */
    public static final String MODIFY_AUDIO_SETTINGS = "android.permission.MODIFY_AUDIO_SETTINGS";
    /** See {@code Manifest#MANAGE_FACTORY_RESET_PROTECTION} */
    public static final String MANAGE_FACTORY_RESET_PROTECTION = "android.permission"
            + ".MANAGE_FACTORY_RESET_PROTECTION";
    /** See {@code Manifest#MANAGE_USB} */
    public static final String MANAGE_USB = "android.permission.MANAGE_USB";
    /** See {@code Manifest#MANAGE_DEBUGGING} */
    public static final String MANAGE_DEBUGGING = "android.permission.MANAGE_DEBUGGING";
    /** See {@code Manifest#ACCESS_MTP} */
    public static final String ACCESS_MTP = "android.permission.ACCESS_MTP";
    /** See {@code Manifest#HARDWARE_TEST} */
    public static final String HARDWARE_TEST = "android.permission.HARDWARE_TEST";
    /** See {@code Manifest#MANAGE_DYNAMIC_SYSTEM} */
    public static final String MANAGE_DYNAMIC_SYSTEM = "android.permission.MANAGE_DYNAMIC_SYSTEM";
    /** See {@code Manifest#INSTALL_DYNAMIC_SYSTEM} */
    public static final String INSTALL_DYNAMIC_SYSTEM = "android.permission"
            + ".INSTALL_DYNAMIC_SYSTEM";
    /** See {@code Manifest#ACCESS_BROADCAST_RADIO} */
    public static final String ACCESS_BROADCAST_RADIO = "android.permission"
            + ".ACCESS_BROADCAST_RADIO";
    /** See {@code Manifest#ACCESS_FM_RADIO} */
    public static final String ACCESS_FM_RADIO = "android.permission.ACCESS_FM_RADIO";
    /** See {@code Manifest#NET_ADMIN} */
    public static final String NET_ADMIN = "android.permission.NET_ADMIN";
    /** See {@code Manifest#REMOTE_AUDIO_PLAYBACK} */
    public static final String REMOTE_AUDIO_PLAYBACK = "android.permission.REMOTE_AUDIO_PLAYBACK";
    /** See {@code Manifest#TV_INPUT_HARDWARE} */
    public static final String TV_INPUT_HARDWARE = "android.permission.TV_INPUT_HARDWARE";
    /** See {@code Manifest#CAPTURE_TV_INPUT} */
    public static final String CAPTURE_TV_INPUT = "android.permission.CAPTURE_TV_INPUT";
    /** See {@code Manifest#DVB_DEVICE} */
    public static final String DVB_DEVICE = "android.permission.DVB_DEVICE";
    /** See {@code Manifest#MANAGE_CARRIER_OEM_UNLOCK_STATE} */
    public static final String MANAGE_CARRIER_OEM_UNLOCK_STATE = "android.permission"
            + ".MANAGE_CARRIER_OEM_UNLOCK_STATE";
    /** See {@code Manifest#MANAGE_USER_OEM_UNLOCK_STATE} */
    public static final String MANAGE_USER_OEM_UNLOCK_STATE = "android.permission"
            + ".MANAGE_USER_OEM_UNLOCK_STATE";
    /** See {@code Manifest#READ_OEM_UNLOCK_STATE} */
    public static final String READ_OEM_UNLOCK_STATE = "android.permission.READ_OEM_UNLOCK_STATE";
    /** See {@code Manifest#OEM_UNLOCK_STATE} */
    public static final String OEM_UNLOCK_STATE = "android.permission.OEM_UNLOCK_STATE";
    /** See {@code Manifest#ACCESS_PDB_STATE} */
    public static final String ACCESS_PDB_STATE = "android.permission.ACCESS_PDB_STATE";
    /** See {@code Manifest#TEST_BLACKLISTED_PASSWORD} */
    public static final String TEST_BLACKLISTED_PASSWORD =
            "android.permission.TEST_BLACKLISTED_PASSWORD";
    /** See {@code Manifest#NOTIFY_PENDING_SYSTEM_UPDATE} */
    public static final String NOTIFY_PENDING_SYSTEM_UPDATE =
            "android.permission.NOTIFY_PENDING_SYSTEM_UPDATE";
    /** See {@code Manifest#CAMERA_DISABLE_TRANSMIT_LED} */
    public static final String CAMERA_DISABLE_TRANSMIT_LED =
            "android.permission.CAMERA_DISABLE_TRANSMIT_LED";
    /** See {@code Manifest#CAMERA_SEND_SYSTEM_EVENTS} */
    public static final String CAMERA_SEND_SYSTEM_EVENTS =
            "android.permission.CAMERA_SEND_SYSTEM_EVENTS";
    /** See {@code Manifest#CAMERA_INJECT_EXTERNAL_CAMERA} */
    public static final String CAMERA_INJECT_EXTERNAL_CAMERA =
            "android.permission.CAMERA_INJECT_EXTERNAL_CAMERA";
    /** See {@code Manifest#GRANT_RUNTIME_PERMISSIONS_TO_TELEPHONY_DEFAULTS} */
    public static final String GRANT_RUNTIME_PERMISSIONS_TO_TELEPHONY_DEFAULTS = "android"
            + ".permission.GRANT_RUNTIME_PERMISSIONS_TO_TELEPHONY_DEFAULTS";
    /** See {@code Manifest#MODIFY_PHONE_STATE} */
    public static final String MODIFY_PHONE_STATE = "android.permission.MODIFY_PHONE_STATE";
    /** See {@code Manifest#READ_PRECISE_PHONE_STATE} */
    public static final String READ_PRECISE_PHONE_STATE =
            "android.permission.READ_PRECISE_PHONE_STATE";
    /** See {@code Manifest#READ_PRIVILEGED_PHONE_STATE} */
    public static final String READ_PRIVILEGED_PHONE_STATE = "android.permission"
            + ".READ_PRIVILEGED_PHONE_STATE";
    /** See {@code Manifest#USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER} */
    public static final String USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER =
            "android.permission.USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER";
    /** See {@code Manifest#READ_ACTIVE_EMERGENCY_SESSION} */
    public static final String READ_ACTIVE_EMERGENCY_SESSION = "android.permission"
            + ".READ_ACTIVE_EMERGENCY_SESSION";
    /** See {@code Manifest#LISTEN_ALWAYS_REPORTED_SIGNAL_STRENGTH} */
    public static final String LISTEN_ALWAYS_REPORTED_SIGNAL_STRENGTH = "android.permission"
            + ".LISTEN_ALWAYS_REPORTED_SIGNAL_STRENGTH";
    /** See {@code Manifest#REGISTER_SIM_SUBSCRIPTION} */
    public static final String REGISTER_SIM_SUBSCRIPTION =
            "android.permission.REGISTER_SIM_SUBSCRIPTION";
    /** See {@code Manifest#REGISTER_CALL_PROVIDER} */
    public static final String REGISTER_CALL_PROVIDER = "android.permission"
            + ".REGISTER_CALL_PROVIDER";
    /** See {@code Manifest#REGISTER_CONNECTION_MANAGER} */
    public static final String REGISTER_CONNECTION_MANAGER =
            "android.permission.REGISTER_CONNECTION_MANAGER";
    /** See {@code Manifest#BIND_INCALL_SERVICE} */
    public static final String BIND_INCALL_SERVICE = "android.permission.BIND_INCALL_SERVICE";
    /** See {@code Manifest#MANAGE_ONGOING_CALLS} */
    public static final String MANAGE_ONGOING_CALLS = "android.permission.MANAGE_ONGOING_CALLS";
    /** See {@code Manifest#NETWORK_SCAN} */
    public static final String NETWORK_SCAN = "android.permission.NETWORK_SCAN";
    /** See {@code Manifest#BIND_VISUAL_VOICEMAIL_SERVICE} */
    public static final String BIND_VISUAL_VOICEMAIL_SERVICE = "android.permission"
            + ".BIND_VISUAL_VOICEMAIL_SERVICE";
    /** See {@code Manifest#BIND_SCREENING_SERVICE} */
    public static final String BIND_SCREENING_SERVICE = "android.permission.BIND_SCREENING_SERVICE";
    /** See {@code Manifest#BIND_PHONE_ACCOUNT_SUGGESTION_SERVICE} */
    public static final String BIND_PHONE_ACCOUNT_SUGGESTION_SERVICE =
            "android.permission.BIND_PHONE_ACCOUNT_SUGGESTION_SERVICE";
    /** See {@code Manifest#BIND_CALL_DIAGNOSTIC_SERVICE} */
    public static final String BIND_CALL_DIAGNOSTIC_SERVICE = "android.permission"
            + ".BIND_CALL_DIAGNOSTIC_SERVICE";
    /** See {@code Manifest#BIND_CALL_REDIRECTION_SERVICE} */
    public static final String BIND_CALL_REDIRECTION_SERVICE =
            "android.permission.BIND_CALL_REDIRECTION_SERVICE";
    /** See {@code Manifest#BIND_CONNECTION_SERVICE} */
    public static final String BIND_CONNECTION_SERVICE =
            "android.permission.BIND_CONNECTION_SERVICE";
    /** See {@code Manifest#BIND_TELECOM_CONNECTION_SERVICE} */
    public static final String BIND_TELECOM_CONNECTION_SERVICE = "android.permission"
            + ".BIND_TELECOM_CONNECTION_SERVICE";
    /** See {@code Manifest#CONTROL_INCALL_EXPERIENCE} */
    public static final String CONTROL_INCALL_EXPERIENCE = "android.permission"
            + ".CONTROL_INCALL_EXPERIENCE";
    /** See {@code Manifest#RECEIVE_STK_COMMANDS} */
    public static final String RECEIVE_STK_COMMANDS = "android.permission.RECEIVE_STK_COMMANDS";
    /** See {@code Manifest#SEND_EMBMS_INTENTS} */
    public static final String SEND_EMBMS_INTENTS = "android.permission.SEND_EMBMS_INTENTS";
    /** See {@code Manifest#MANAGE_SENSORS} */
    public static final String MANAGE_SENSORS = "android.permission.MANAGE_SENSORS";
    /** See {@code Manifest#BIND_IMS_SERVICE} */
    public static final String BIND_IMS_SERVICE = "android.permission.BIND_IMS_SERVICE";
    /** See {@code Manifest#BIND_TELEPHONY_DATA_SERVICE} */
    public static final String BIND_TELEPHONY_DATA_SERVICE =
            "android.permission.BIND_TELEPHONY_DATA_SERVICE";
    /** See {@code Manifest#BIND_TELEPHONY_NETWORK_SERVICE} */
    public static final String BIND_TELEPHONY_NETWORK_SERVICE =
            "android.permission.BIND_TELEPHONY_NETWORK_SERVICE";
    /** See {@code Manifest#WRITE_EMBEDDED_SUBSCRIPTIONS} */
    public static final String WRITE_EMBEDDED_SUBSCRIPTIONS = "android.permission"
            + ".WRITE_EMBEDDED_SUBSCRIPTIONS";
    /** See {@code Manifest#BIND_EUICC_SERVICE} */
    public static final String BIND_EUICC_SERVICE = "android.permission.BIND_EUICC_SERVICE";
    /** See {@code Manifest#READ_CARRIER_APP_INFO} */
    public static final String READ_CARRIER_APP_INFO = "android.permission.READ_CARRIER_APP_INFO";
    /** See {@code Manifest#BIND_GBA_SERVICE} */
    public static final String BIND_GBA_SERVICE = "android.permission.BIND_GBA_SERVICE";
    /** See {@code Manifest#ACCESS_RCS_USER_CAPABILITY_EXCHANGE} */
    public static final String ACCESS_RCS_USER_CAPABILITY_EXCHANGE =
            "android.permission.ACCESS_RCS_USER_CAPABILITY_EXCHANGE";
    /** See {@code Manifest#WRITE_MEDIA_STORAGE} */
    public static final String WRITE_MEDIA_STORAGE = "android.permission.WRITE_MEDIA_STORAGE";
    /** See {@code Manifest#MANAGE_DOCUMENTS} */
    public static final String MANAGE_DOCUMENTS = "android.permission.MANAGE_DOCUMENTS";
    /** See {@code Manifest#CACHE_CONTENT} */
    public static final String CACHE_CONTENT = "android.permission.CACHE_CONTENT";
    /** See {@code Manifest#ALLOCATE_AGGRESSIVE} */
    public static final String ALLOCATE_AGGRESSIVE = "android.permission.ALLOCATE_AGGRESSIVE";
    /** See {@code Manifest#USE_RESERVED_DISK} */
    public static final String USE_RESERVED_DISK = "android.permission.USE_RESERVED_DISK";
    /** See {@code Manifest#DISABLE_KEYGUARD} */
    public static final String DISABLE_KEYGUARD = "android.permission.DISABLE_KEYGUARD";
    /** See {@code Manifest#REQUEST_PASSWORD_COMPLEXITY} */
    public static final String REQUEST_PASSWORD_COMPLEXITY =
            "android.permission.REQUEST_PASSWORD_COMPLEXITY";
    /** See {@code Manifest#GET_TASKS} */
    public static final String GET_TASKS = "android.permission.GET_TASKS";
    /** See {@code Manifest#REAL_GET_TASKS} */
    public static final String REAL_GET_TASKS = "android.permission.REAL_GET_TASKS";
    /** See {@code Manifest#START_TASKS_FROM_RECENTS} */
    public static final String START_TASKS_FROM_RECENTS =
            "android.permission.START_TASKS_FROM_RECENTS";
    /** See {@code Manifest#INTERACT_ACROSS_USERS} */
    public static final String INTERACT_ACROSS_USERS = "android.permission.INTERACT_ACROSS_USERS";
    /** See {@code Manifest#INTERACT_ACROSS_USERS_FULL} */
    public static final String INTERACT_ACROSS_USERS_FULL =
            "android.permission.INTERACT_ACROSS_USERS_FULL";
    /** See {@code Manifest#START_CROSS_PROFILE_ACTIVITIES} */
    public static final String START_CROSS_PROFILE_ACTIVITIES =
            "android.permission.START_CROSS_PROFILE_ACTIVITIES";
    /** See {@code Manifest#INTERACT_ACROSS_PROFILES} */
    public static final String INTERACT_ACROSS_PROFILES = "android.permission"
            + ".INTERACT_ACROSS_PROFILES";
    /** See {@code Manifest#CONFIGURE_INTERACT_ACROSS_PROFILES} */
    public static final String CONFIGURE_INTERACT_ACROSS_PROFILES =
            "android.permission.CONFIGURE_INTERACT_ACROSS_PROFILES";
    /** See {@code Manifest#MANAGE_USERS} */
    public static final String MANAGE_USERS = "android.permission.MANAGE_USERS";
    /** See {@code Manifest#CREATE_USERS} */
    public static final String CREATE_USERS = "android.permission.CREATE_USERS";
    /** See {@code Manifest#QUERY_USERS} */
    public static final String QUERY_USERS = "android.permission.QUERY_USERS";
    /** See {@code Manifest#ACCESS_BLOBS_ACROSS_USERS} */
    public static final String ACCESS_BLOBS_ACROSS_USERS = "android.permission"
            + ".ACCESS_BLOBS_ACROSS_USERS";
    /** See {@code Manifest#MANAGE_PROFILE_AND_DEVICE_OWNERS} */
    public static final String MANAGE_PROFILE_AND_DEVICE_OWNERS = "android.permission"
            + ".MANAGE_PROFILE_AND_DEVICE_OWNERS";
    /** See {@code Manifest#QUERY_ADMIN_POLICY} */
    public static final String QUERY_ADMIN_POLICY = "android.permission.QUERY_ADMIN_POLICY";
    /** See {@code Manifest#CLEAR_FREEZE_PERIOD} */
    public static final String CLEAR_FREEZE_PERIOD = "android.permission.CLEAR_FREEZE_PERIOD";
    /** See {@code Manifest#FORCE_DEVICE_POLICY_MANAGER_LOGS} */
    public static final String FORCE_DEVICE_POLICY_MANAGER_LOGS = "android.permission"
            + ".FORCE_DEVICE_POLICY_MANAGER_LOGS";
    /** See {@code Manifest#GET_DETAILED_TASKS} */
    public static final String GET_DETAILED_TASKS = "android.permission.GET_DETAILED_TASKS";
    /** See {@code Manifest#REORDER_TASKS} */
    public static final String REORDER_TASKS = "android.permission.REORDER_TASKS";
    /** See {@code Manifest#REMOVE_TASKS} */
    public static final String REMOVE_TASKS = "android.permission.REMOVE_TASKS";
    /** See {@code Manifest#MANAGE_ACTIVITY_STACKS} */
    public static final String MANAGE_ACTIVITY_STACKS = "android.permission.MANAGE_ACTIVITY_STACKS";
    /** See {@code Manifest#MANAGE_ACTIVITY_TASKS} */
    public static final String MANAGE_ACTIVITY_TASKS = "android.permission.MANAGE_ACTIVITY_TASKS";
    /** See {@code Manifest#ACTIVITY_EMBEDDING} */
    public static final String ACTIVITY_EMBEDDING = "android.permission.ACTIVITY_EMBEDDING";
    /** See {@code Manifest#START_ANY_ACTIVITY} */
    public static final String START_ANY_ACTIVITY = "android.permission.START_ANY_ACTIVITY";
    /** See {@code Manifest#START_ACTIVITIES_FROM_BACKGROUND} */
    public static final String START_ACTIVITIES_FROM_BACKGROUND = "android.permission"
            + ".START_ACTIVITIES_FROM_BACKGROUND";
    /** See {@code Manifest#START_FOREGROUND_SERVICES_FROM_BACKGROUND} */
    public static final String START_FOREGROUND_SERVICES_FROM_BACKGROUND = "android.permission"
            + ".START_FOREGROUND_SERVICES_FROM_BACKGROUND";
    /** See {@code Manifest#SEND_SHOW_SUSPENDED_APP_DETAILS} */
    public static final String SEND_SHOW_SUSPENDED_APP_DETAILS = "android.permission"
            + ".SEND_SHOW_SUSPENDED_APP_DETAILS";
    /** See {@code Manifest#START_ACTIVITY_AS_CALLER} */
    public static final String START_ACTIVITY_AS_CALLER = "android.permission"
            + ".START_ACTIVITY_AS_CALLER";
    /** See {@code Manifest#RESTART_PACKAGES} */
    public static final String RESTART_PACKAGES = "android.permission.RESTART_PACKAGES";
    /** See {@code Manifest#GET_PROCESS_STATE_AND_OOM_SCORE} */
    public static final String GET_PROCESS_STATE_AND_OOM_SCORE =
            "android.permission.GET_PROCESS_STATE_AND_OOM_SCORE";
    /** See {@code Manifest#GET_INTENT_SENDER_INTENT} */
    public static final String GET_INTENT_SENDER_INTENT =
            "android.permission.GET_INTENT_SENDER_INTENT";
    /** See {@code Manifest#SYSTEM_ALERT_WINDOW} */
    public static final String SYSTEM_ALERT_WINDOW = "android.permission.SYSTEM_ALERT_WINDOW";
    /** See {@code Manifest#SYSTEM_APPLICATION_OVERLAY} */
    public static final String SYSTEM_APPLICATION_OVERLAY =
            "android.permission.SYSTEM_APPLICATION_OVERLAY";
    /** See {@code Manifest#RUN_IN_BACKGROUND} */
    public static final String RUN_IN_BACKGROUND = "android.permission.RUN_IN_BACKGROUND";
    /** See {@code Manifest#USE_DATA_IN_BACKGROUND} */
    public static final String USE_DATA_IN_BACKGROUND = "android.permission"
            + ".USE_DATA_IN_BACKGROUND";
    /** See {@code Manifest#SET_DISPLAY_OFFSET} */
    public static final String SET_DISPLAY_OFFSET = "android.permission.SET_DISPLAY_OFFSET";
    /** See {@code Manifest#REQUEST_COMPANION_RUN_IN_BACKGROUND} */
    public static final String REQUEST_COMPANION_RUN_IN_BACKGROUND = "android.permission"
            + ".REQUEST_COMPANION_RUN_IN_BACKGROUND";
    /** See {@code Manifest#REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND} */
    public static final String REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND =
            "android.permission.REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND";
    /** See {@code Manifest#REQUEST_COMPANION_USE_DATA_IN_BACKGROUND} */
    public static final String REQUEST_COMPANION_USE_DATA_IN_BACKGROUND = "android.permission"
            + ".REQUEST_COMPANION_USE_DATA_IN_BACKGROUND";
    /** See {@code Manifest#REQUEST_COMPANION_PROFILE_WATCH} */
    public static final String REQUEST_COMPANION_PROFILE_WATCH =
            "android.permission.REQUEST_COMPANION_PROFILE_WATCH";
    /** See {@code Manifest#REQUEST_COMPANION_PROFILE_APP_STREAMING} */
    public static final String REQUEST_COMPANION_PROFILE_APP_STREAMING = "android.permission"
            + ".REQUEST_COMPANION_PROFILE_APP_STREAMING";
    /** See {@code Manifest#REQUEST_COMPANION_PROFILE_AUTOMOTIVE_PROJECTION} */
    public static final String REQUEST_COMPANION_PROFILE_AUTOMOTIVE_PROJECTION =
            "android.permission.REQUEST_COMPANION_PROFILE_AUTOMOTIVE_PROJECTION";
    /** See {@code Manifest#REQUEST_COMPANION_SELF_MANAGED} */
    public static final String REQUEST_COMPANION_SELF_MANAGED = "android.permission"
            + ".REQUEST_COMPANION_SELF_MANAGED";
    /** See {@code Manifest#COMPANION_APPROVE_WIFI_CONNECTIONS} */
    public static final String COMPANION_APPROVE_WIFI_CONNECTIONS = "android.permission"
            + ".COMPANION_APPROVE_WIFI_CONNECTIONS";
    /** See {@code Manifest#READ_PROJECTION_STATE} */
    public static final String READ_PROJECTION_STATE = "android.permission.READ_PROJECTION_STATE";
    /** See {@code Manifest#TOGGLE_AUTOMOTIVE_PROJECTION} */
    public static final String TOGGLE_AUTOMOTIVE_PROJECTION =
            "android.permission.TOGGLE_AUTOMOTIVE_PROJECTION";
    /** See {@code Manifest#HIDE_OVERLAY_WINDOWS} */
    public static final String HIDE_OVERLAY_WINDOWS = "android.permission.HIDE_OVERLAY_WINDOWS";
    /** See {@code Manifest#SET_WALLPAPER} */
    public static final String SET_WALLPAPER = "android.permission.SET_WALLPAPER";
    /** See {@code Manifest#SET_WALLPAPER_HINTS} */
    public static final String SET_WALLPAPER_HINTS = "android.permission.SET_WALLPAPER_HINTS";
    /** See {@code Manifest#READ_WALLPAPER_INTERNAL} */
    public static final String READ_WALLPAPER_INTERNAL = "android.permission"
            + ".READ_WALLPAPER_INTERNAL";
    /** See {@code Manifest#SET_TIME} */
    public static final String SET_TIME = "android.permission.SET_TIME";
    /** See {@code Manifest#SET_TIME_ZONE} */
    public static final String SET_TIME_ZONE = "android.permission.SET_TIME_ZONE";
    /** See {@code Manifest#SUGGEST_TELEPHONY_TIME_AND_ZONE} */
    public static final String SUGGEST_TELEPHONY_TIME_AND_ZONE =
            "android.permission.SUGGEST_TELEPHONY_TIME_AND_ZONE";
    /** See {@code Manifest#SUGGEST_MANUAL_TIME_AND_ZONE} */
    public static final String SUGGEST_MANUAL_TIME_AND_ZONE = "android.permission"
            + ".SUGGEST_MANUAL_TIME_AND_ZONE";
    /** See {@code Manifest#SUGGEST_EXTERNAL_TIME} */
    public static final String SUGGEST_EXTERNAL_TIME = "android.permission.SUGGEST_EXTERNAL_TIME";
    /** See {@code Manifest#MANAGE_TIME_AND_ZONE_DETECTION} */
    public static final String MANAGE_TIME_AND_ZONE_DETECTION = "android.permission"
            + ".MANAGE_TIME_AND_ZONE_DETECTION";
    /** See {@code Manifest#EXPAND_STATUS_BAR} */
    public static final String EXPAND_STATUS_BAR = "android.permission.EXPAND_STATUS_BAR";
    /** See {@code Manifest#INSTALL_SHORTCUT} */
    public static final String INSTALL_SHORTCUT = "com.android.launcher.permission"
            + ".INSTALL_SHORTCUT";
    /** See {@code Manifest#READ_SYNC_SETTINGS} */
    public static final String READ_SYNC_SETTINGS = "android.permission.READ_SYNC_SETTINGS";
    /** See {@code Manifest#WRITE_SYNC_SETTINGS} */
    public static final String WRITE_SYNC_SETTINGS = "android.permission.WRITE_SYNC_SETTINGS";
    /** See {@code Manifest#READ_SYNC_STATS} */
    public static final String READ_SYNC_STATS = "android.permission.READ_SYNC_STATS";
    /** See {@code Manifest#SET_SCREEN_COMPATIBILITY} */
    public static final String SET_SCREEN_COMPATIBILITY = "android.permission"
            + ".SET_SCREEN_COMPATIBILITY";
    /** See {@code Manifest#CHANGE_CONFIGURATION} */
    public static final String CHANGE_CONFIGURATION = "android.permission.CHANGE_CONFIGURATION";
    /** See {@code Manifest#WRITE_GSERVICES} */
    public static final String WRITE_GSERVICES = "android.permission.WRITE_GSERVICES";
    /** See {@code Manifest#WRITE_ALLOWLISTED_DEVICE_CONFIG} */
    public static final String WRITE_ALLOWLISTED_DEVICE_CONFIG =
            "android.permission.WRITE_ALLOWLISTED_DEVICE_CONFIG";
    /** See {@code Manifest#READ_DEVICE_CONFIG} */
    public static final String READ_DEVICE_CONFIG = "android.permission.READ_DEVICE_CONFIG";
    /** See {@code Manifest#READ_APP_SPECIFIC_LOCALES} */
    public static final String READ_APP_SPECIFIC_LOCALES =
            "android.permission.READ_APP_SPECIFIC_LOCALES";
    /** See {@code Manifest#MONITOR_DEVICE_CONFIG_ACCESS} */
    public static final String MONITOR_DEVICE_CONFIG_ACCESS =
            "android.permission.MONITOR_DEVICE_CONFIG_ACCESS";
    /** See {@code Manifest#FORCE_STOP_PACKAGES} */
    public static final String FORCE_STOP_PACKAGES = "android.permission.FORCE_STOP_PACKAGES";
    /** See {@code Manifest#RETRIEVE_WINDOW_CONTENT} */
    public static final String RETRIEVE_WINDOW_CONTENT =
            "android.permission.RETRIEVE_WINDOW_CONTENT";
    /** See {@code Manifest#SET_ANIMATION_SCALE} */
    public static final String SET_ANIMATION_SCALE = "android.permission.SET_ANIMATION_SCALE";
    /** See {@code Manifest#PERSISTENT_ACTIVITY} */
    public static final String PERSISTENT_ACTIVITY = "android.permission.PERSISTENT_ACTIVITY";
    /** See {@code Manifest#GET_PACKAGE_SIZE} */
    public static final String GET_PACKAGE_SIZE = "android.permission.GET_PACKAGE_SIZE";
    /** See {@code Manifest#RECEIVE_BOOT_COMPLETED} */
    public static final String RECEIVE_BOOT_COMPLETED = "android.permission.RECEIVE_BOOT_COMPLETED";
    /** See {@code Manifest#BROADCAST_STICKY} */
    public static final String BROADCAST_STICKY = "android.permission.BROADCAST_STICKY";
    /** See {@code Manifest#MOUNT_UNMOUNT_FILESYSTEMS} */
    public static final String MOUNT_UNMOUNT_FILESYSTEMS = "android.permission"
            + ".MOUNT_UNMOUNT_FILESYSTEMS";
    /** See {@code Manifest#MOUNT_FORMAT_FILESYSTEMS} */
    public static final String MOUNT_FORMAT_FILESYSTEMS = "android.permission"
            + ".MOUNT_FORMAT_FILESYSTEMS";
    /** See {@code Manifest#STORAGE_INTERNAL} */
    public static final String STORAGE_INTERNAL = "android.permission.STORAGE_INTERNAL";
    /** See {@code Manifest#ASEC_ACCESS} */
    public static final String ASEC_ACCESS = "android.permission.ASEC_ACCESS";
    /** See {@code Manifest#ASEC_CREATE} */
    public static final String ASEC_CREATE = "android.permission.ASEC_CREATE";
    /** See {@code Manifest#ASEC_DESTROY} */
    public static final String ASEC_DESTROY = "android.permission.ASEC_DESTROY";
    /** See {@code Manifest#ASEC_MOUNT_UNMOUNT} */
    public static final String ASEC_MOUNT_UNMOUNT = "android.permission.ASEC_MOUNT_UNMOUNT";
    /** See {@code Manifest#ASEC_RENAME} */
    public static final String ASEC_RENAME = "android.permission.ASEC_RENAME";
    /** See {@code Manifest#WRITE_APN_SETTINGS} */
    public static final String WRITE_APN_SETTINGS = "android.permission.WRITE_APN_SETTINGS";
    /** See {@code Manifest#CHANGE_NETWORK_STATE} */
    public static final String CHANGE_NETWORK_STATE = "android.permission.CHANGE_NETWORK_STATE";
    /** See {@code Manifest#CLEAR_APP_CACHE} */
    public static final String CLEAR_APP_CACHE = "android.permission.CLEAR_APP_CACHE";
    /** See {@code Manifest#ALLOW_ANY_CODEC_FOR_PLAYBACK} */
    public static final String ALLOW_ANY_CODEC_FOR_PLAYBACK = "android.permission"
            + ".ALLOW_ANY_CODEC_FOR_PLAYBACK";
    /** See {@code Manifest#MANAGE_CA_CERTIFICATES} */
    public static final String MANAGE_CA_CERTIFICATES = "android.permission"
            + ".MANAGE_CA_CERTIFICATES";
    /** See {@code Manifest#RECOVERY} */
    public static final String RECOVERY = "android.permission.RECOVERY";
    /** See {@code Manifest#BIND_RESUME_ON_REBOOT_SERVICE} */
    public static final String BIND_RESUME_ON_REBOOT_SERVICE = "android.permission"
            + ".BIND_RESUME_ON_REBOOT_SERVICE";
    /** See {@code Manifest#READ_SYSTEM_UPDATE_INFO} */
    public static final String READ_SYSTEM_UPDATE_INFO = "android.permission"
            + ".READ_SYSTEM_UPDATE_INFO";
    /** See {@code Manifest#BIND_JOB_SERVICE} */
    public static final String BIND_JOB_SERVICE = "android.permission.BIND_JOB_SERVICE";
    /** See {@code Manifest#UPDATE_CONFIG} */
    public static final String UPDATE_CONFIG = "android.permission.UPDATE_CONFIG";
    /** See {@code Manifest#RESET_SHORTCUT_MANAGER_THROTTLING} */
    public static final String RESET_SHORTCUT_MANAGER_THROTTLING =
            "android.permission.RESET_SHORTCUT_MANAGER_THROTTLING";
    /** See {@code Manifest#BIND_NETWORK_RECOMMENDATION_SERVICE} */
    public static final String BIND_NETWORK_RECOMMENDATION_SERVICE =
            "android.permission.BIND_NETWORK_RECOMMENDATION_SERVICE";
    /** See {@code Manifest#MANAGE_CREDENTIAL_MANAGEMENT_APP} */
    public static final String MANAGE_CREDENTIAL_MANAGEMENT_APP = "android.permission"
            + ".MANAGE_CREDENTIAL_MANAGEMENT_APP";
    /** See {@code Manifest#UPDATE_FONTS} */
    public static final String UPDATE_FONTS = "android.permission.UPDATE_FONTS";
    /** See {@code Manifest#USE_ATTESTATION_VERIFICATION_SERVICE} */
    public static final String USE_ATTESTATION_VERIFICATION_SERVICE =
            "android.permission.USE_ATTESTATION_VERIFICATION_SERVICE";
    /** See {@code Manifest#VERIFY_ATTESTATION} */
    public static final String VERIFY_ATTESTATION = "android.permission.VERIFY_ATTESTATION";
    /** See {@code Manifest#BIND_ATTESTATION_VERIFICATION_SERVICE} */
    public static final String BIND_ATTESTATION_VERIFICATION_SERVICE = "android.permission"
            + ".BIND_ATTESTATION_VERIFICATION_SERVICE";
    /** See {@code Manifest#WRITE_SECURE_SETTINGS} */
    public static final String WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS";
    /** See {@code Manifest#DUMP} */
    public static final String DUMP = "android.permission.DUMP";
    /** See {@code Manifest#CONTROL_UI_TRACING} */
    public static final String CONTROL_UI_TRACING = "android.permission.CONTROL_UI_TRACING";
    /** See {@code Manifest#READ_LOGS} */
    public static final String READ_LOGS = "android.permission.READ_LOGS";
    /** See {@code Manifest#SET_DEBUG_APP} */
    public static final String SET_DEBUG_APP = "android.permission.SET_DEBUG_APP";
    /** See {@code Manifest#SET_PROCESS_LIMIT} */
    public static final String SET_PROCESS_LIMIT = "android.permission.SET_PROCESS_LIMIT";
    /** See {@code Manifest#SET_ALWAYS_FINISH} */
    public static final String SET_ALWAYS_FINISH = "android.permission.SET_ALWAYS_FINISH";
    /** See {@code Manifest#SIGNAL_PERSISTENT_PROCESSES} */
    public static final String SIGNAL_PERSISTENT_PROCESSES =
            "android.permission.SIGNAL_PERSISTENT_PROCESSES";
    /** See {@code Manifest#APPROVE_INCIDENT_REPORTS} */
    public static final String APPROVE_INCIDENT_REPORTS =
            "android.permission.APPROVE_INCIDENT_REPORTS";
    /** See {@code Manifest#REQUEST_INCIDENT_REPORT_APPROVAL} */
    public static final String REQUEST_INCIDENT_REPORT_APPROVAL = "android.permission"
            + ".REQUEST_INCIDENT_REPORT_APPROVAL";
    /** See {@code Manifest#GET_ACCOUNTS_PRIVILEGED} */
    public static final String GET_ACCOUNTS_PRIVILEGED = "android.permission"
            + ".GET_ACCOUNTS_PRIVILEGED";
    /** See {@code Manifest#GET_PASSWORD} */
    public static final String GET_PASSWORD = "android.permission.GET_PASSWORD";
    /** See {@code Manifest#DIAGNOSTIC} */
    public static final String DIAGNOSTIC = "android.permission.DIAGNOSTIC";
    /** See {@code Manifest#STATUS_BAR} */
    public static final String STATUS_BAR = "android.permission.STATUS_BAR";
    /** See {@code Manifest#TRIGGER_SHELL_BUGREPORT} */
    public static final String TRIGGER_SHELL_BUGREPORT = "android.permission"
            + ".TRIGGER_SHELL_BUGREPORT";
    /** See {@code Manifest#TRIGGER_SHELL_PROFCOLLECT_UPLOAD} */
    public static final String TRIGGER_SHELL_PROFCOLLECT_UPLOAD = "android.permission"
            + ".TRIGGER_SHELL_PROFCOLLECT_UPLOAD";
    /** See {@code Manifest#STATUS_BAR_SERVICE} */
    public static final String STATUS_BAR_SERVICE = "android.permission.STATUS_BAR_SERVICE";
    /** See {@code Manifest#BIND_QUICK_SETTINGS_TILE} */
    public static final String BIND_QUICK_SETTINGS_TILE =
            "android.permission.BIND_QUICK_SETTINGS_TILE";
    /** See {@code Manifest#BIND_CONTROLS} */
    public static final String BIND_CONTROLS = "android.permission.BIND_CONTROLS";
    /** See {@code Manifest#FORCE_BACK} */
    public static final String FORCE_BACK = "android.permission.FORCE_BACK";
    /** See {@code Manifest#UPDATE_DEVICE_STATS} */
    public static final String UPDATE_DEVICE_STATS = "android.permission.UPDATE_DEVICE_STATS";
    /** See {@code Manifest#GET_APP_OPS_STATS} */
    public static final String GET_APP_OPS_STATS = "android.permission.GET_APP_OPS_STATS";
    /** See {@code Manifest#GET_HISTORICAL_APP_OPS_STATS} */
    public static final String GET_HISTORICAL_APP_OPS_STATS = "android.permission"
            + ".GET_HISTORICAL_APP_OPS_STATS";
    /** See {@code Manifest#UPDATE_APP_OPS_STATS} */
    public static final String UPDATE_APP_OPS_STATS = "android.permission.UPDATE_APP_OPS_STATS";
    /** See {@code Manifest#MANAGE_APP_OPS_RESTRICTIONS} */
    public static final String MANAGE_APP_OPS_RESTRICTIONS = "android.permission"
            + ".MANAGE_APP_OPS_RESTRICTIONS";
    /** See {@code Manifest#MANAGE_APP_OPS_MODES} */
    public static final String MANAGE_APP_OPS_MODES = "android.permission.MANAGE_APP_OPS_MODES";
    /** See {@code Manifest#INTERNAL_SYSTEM_WINDOW} */
    public static final String INTERNAL_SYSTEM_WINDOW = "android.permission"
            + ".INTERNAL_SYSTEM_WINDOW";
    /** See {@code Manifest#UNLIMITED_TOASTS} */
    public static final String UNLIMITED_TOASTS = "android.permission.UNLIMITED_TOASTS";
    /** See {@code Manifest#HIDE_NON_SYSTEM_OVERLAY_WINDOWS} */
    public static final String HIDE_NON_SYSTEM_OVERLAY_WINDOWS =
            "android.permission.HIDE_NON_SYSTEM_OVERLAY_WINDOWS";
    /** See {@code Manifest#MANAGE_APP_TOKENS} */
    public static final String MANAGE_APP_TOKENS = "android.permission.MANAGE_APP_TOKENS";
    /** See {@code Manifest#REGISTER_WINDOW_MANAGER_LISTENERS} */
    public static final String REGISTER_WINDOW_MANAGER_LISTENERS = "android.permission"
            + ".REGISTER_WINDOW_MANAGER_LISTENERS";
    /** See {@code Manifest#FREEZE_SCREEN} */
    public static final String FREEZE_SCREEN = "android.permission.FREEZE_SCREEN";
    /** See {@code Manifest#INJECT_EVENTS} */
    public static final String INJECT_EVENTS = "android.permission.INJECT_EVENTS";
    /** See {@code Manifest#FILTER_EVENTS} */
    public static final String FILTER_EVENTS = "android.permission.FILTER_EVENTS";
    /** See {@code Manifest#RETRIEVE_WINDOW_TOKEN} */
    public static final String RETRIEVE_WINDOW_TOKEN = "android.permission.RETRIEVE_WINDOW_TOKEN";
    /** See {@code Manifest#MODIFY_ACCESSIBILITY_DATA} */
    public static final String MODIFY_ACCESSIBILITY_DATA =
            "android.permission.MODIFY_ACCESSIBILITY_DATA";
    /** See {@code Manifest#ACT_AS_PACKAGE_FOR_ACCESSIBILITY} */
    public static final String ACT_AS_PACKAGE_FOR_ACCESSIBILITY = "android.permission"
            + ".ACT_AS_PACKAGE_FOR_ACCESSIBILITY";
    /** See {@code Manifest#CHANGE_ACCESSIBILITY_VOLUME} */
    public static final String CHANGE_ACCESSIBILITY_VOLUME =
            "android.permission.CHANGE_ACCESSIBILITY_VOLUME";
    /** See {@code Manifest#FRAME_STATS} */
    public static final String FRAME_STATS = "android.permission.FRAME_STATS";
    /** See {@code Manifest#TEMPORARY_ENABLE_ACCESSIBILITY} */
    public static final String TEMPORARY_ENABLE_ACCESSIBILITY = "android.permission"
            + ".TEMPORARY_ENABLE_ACCESSIBILITY";
    /** See {@code Manifest#OPEN_ACCESSIBILITY_DETAILS_SETTINGS} */
    public static final String OPEN_ACCESSIBILITY_DETAILS_SETTINGS = "android.permission"
            + ".OPEN_ACCESSIBILITY_DETAILS_SETTINGS";
    /** See {@code Manifest#SET_ACTIVITY_WATCHER} */
    public static final String SET_ACTIVITY_WATCHER = "android.permission.SET_ACTIVITY_WATCHER";
    /** See {@code Manifest#SHUTDOWN} */
    public static final String SHUTDOWN = "android.permission.SHUTDOWN";
    /** See {@code Manifest#STOP_APP_SWITCHES} */
    public static final String STOP_APP_SWITCHES = "android.permission.STOP_APP_SWITCHES";
    /** See {@code Manifest#GET_TOP_ACTIVITY_INFO} */
    public static final String GET_TOP_ACTIVITY_INFO = "android.permission.GET_TOP_ACTIVITY_INFO";
    /** See {@code Manifest#READ_INPUT_STATE} */
    public static final String READ_INPUT_STATE = "android.permission.READ_INPUT_STATE";
    /** See {@code Manifest#BIND_INPUT_METHOD} */
    public static final String BIND_INPUT_METHOD = "android.permission.BIND_INPUT_METHOD";
    /** See {@code Manifest#BIND_MIDI_DEVICE_SERVICE} */
    public static final String BIND_MIDI_DEVICE_SERVICE = "android.permission"
            + ".BIND_MIDI_DEVICE_SERVICE";
    /** See {@code Manifest#BIND_ACCESSIBILITY_SERVICE} */
    public static final String BIND_ACCESSIBILITY_SERVICE =
            "android.permission.BIND_ACCESSIBILITY_SERVICE";
    /** See {@code Manifest#BIND_PRINT_SERVICE} */
    public static final String BIND_PRINT_SERVICE = "android.permission.BIND_PRINT_SERVICE";
    /** See {@code Manifest#BIND_PRINT_RECOMMENDATION_SERVICE} */
    public static final String BIND_PRINT_RECOMMENDATION_SERVICE = "android.permission.BIND_PRINT_RECOMMENDATION_SERVICE";
    /** See {@code Manifest#READ_PRINT_SERVICES} */
    public static final String READ_PRINT_SERVICES = "android.permission.READ_PRINT_SERVICES";
    /** See {@code Manifest#READ_PRINT_SERVICE_RECOMMENDATIONS} */
    public static final String READ_PRINT_SERVICE_RECOMMENDATIONS = "android.permission"
            + ".READ_PRINT_SERVICE_RECOMMENDATIONS";
    /** See {@code Manifest#BIND_NFC_SERVICE} */
    public static final String BIND_NFC_SERVICE = "android.permission.BIND_NFC_SERVICE";
    /** See {@code Manifest#BIND_QUICK_ACCESS_WALLET_SERVICE} */
    public static final String BIND_QUICK_ACCESS_WALLET_SERVICE =
            "android.permission.BIND_QUICK_ACCESS_WALLET_SERVICE";
    /** See {@code Manifest#BIND_PRINT_SPOOLER_SERVICE} */
    public static final String BIND_PRINT_SPOOLER_SERVICE = "android.permission"
            + ".BIND_PRINT_SPOOLER_SERVICE";
    /** See {@code Manifest#BIND_COMPANION_DEVICE_MANAGER_SERVICE} */
    public static final String BIND_COMPANION_DEVICE_MANAGER_SERVICE =
            "android.permission.BIND_COMPANION_DEVICE_MANAGER_SERVICE";
    /** See {@code Manifest#BIND_COMPANION_DEVICE_SERVICE} */
    public static final String BIND_COMPANION_DEVICE_SERVICE = "android.permission"
            + ".BIND_COMPANION_DEVICE_SERVICE";
    /** See {@code Manifest#BIND_RUNTIME_PERMISSION_PRESENTER_SERVICE} */
    public static final String BIND_RUNTIME_PERMISSION_PRESENTER_SERVICE = "android.permission"
            + ".BIND_RUNTIME_PERMISSION_PRESENTER_SERVICE";
    /** See {@code Manifest#BIND_TEXT_SERVICE} */
    public static final String BIND_TEXT_SERVICE = "android.permission.BIND_TEXT_SERVICE";
    /** See {@code Manifest#BIND_ATTENTION_SERVICE} */
    public static final String BIND_ATTENTION_SERVICE = "android.permission"
            + ".BIND_ATTENTION_SERVICE";
    /** See {@code Manifest#BIND_ROTATION_RESOLVER_SERVICE} */
    public static final String BIND_ROTATION_RESOLVER_SERVICE = "android.permission"
            + ".BIND_ROTATION_RESOLVER_SERVICE";
    /** See {@code Manifest#BIND_VPN_SERVICE} */
    public static final String BIND_VPN_SERVICE = "android.permission.BIND_VPN_SERVICE";
    /** See {@code Manifest#BIND_WALLPAPER} */
    public static final String BIND_WALLPAPER = "android.permission.BIND_WALLPAPER";
    /** See {@code Manifest#BIND_GAME_SERVICE} */
    public static final String BIND_GAME_SERVICE = "android.permission.BIND_GAME_SERVICE";
    /** See {@code Manifest#BIND_VOICE_INTERACTION} */
    public static final String BIND_VOICE_INTERACTION = "android.permission"
            + ".BIND_VOICE_INTERACTION";
    /** See {@code Manifest#BIND_HOTWORD_DETECTION_SERVICE} */
    public static final String BIND_HOTWORD_DETECTION_SERVICE = "android.permission"
            + ".BIND_HOTWORD_DETECTION_SERVICE";
    /** See {@code Manifest#MANAGE_HOTWORD_DETECTION} */
    public static final String MANAGE_HOTWORD_DETECTION = "android.permission"
            + ".MANAGE_HOTWORD_DETECTION";
    /** See {@code Manifest#BIND_AUTOFILL_SERVICE} */
    public static final String BIND_AUTOFILL_SERVICE = "android.permission.BIND_AUTOFILL_SERVICE";
    /** See {@code Manifest#BIND_AUTOFILL} */
    public static final String BIND_AUTOFILL = "android.permission.BIND_AUTOFILL";
    /** See {@code Manifest#BIND_AUTOFILL_FIELD_CLASSIFICATION_SERVICE} */
    public static final String BIND_AUTOFILL_FIELD_CLASSIFICATION_SERVICE = "android.permission"
            + ".BIND_AUTOFILL_FIELD_CLASSIFICATION_SERVICE";
    /** See {@code Manifest#BIND_INLINE_SUGGESTION_RENDER_SERVICE} */
    public static final String BIND_INLINE_SUGGESTION_RENDER_SERVICE =
            "android.permission.BIND_INLINE_SUGGESTION_RENDER_SERVICE";
    /** See {@code Manifest#BIND_TEXTCLASSIFIER_SERVICE} */
    public static final String BIND_TEXTCLASSIFIER_SERVICE =
            "android.permission.BIND_TEXTCLASSIFIER_SERVICE";
    /** See {@code Manifest#BIND_CONTENT_CAPTURE_SERVICE} */
    public static final String BIND_CONTENT_CAPTURE_SERVICE = "android.permission"
            + ".BIND_CONTENT_CAPTURE_SERVICE";
    /** See {@code Manifest#BIND_TRANSLATION_SERVICE} */
    public static final String BIND_TRANSLATION_SERVICE =
            "android.permission.BIND_TRANSLATION_SERVICE";
    /** See {@code Manifest#MANAGE_UI_TRANSLATION} */
    public static final String MANAGE_UI_TRANSLATION =  "android.permission.MANAGE_UI_TRANSLATION";
    /** See {@code Manifest#BIND_CONTENT_SUGGESTIONS_SERVICE} */
    public static final String BIND_CONTENT_SUGGESTIONS_SERVICE = "android.permission"
            + ".BIND_CONTENT_SUGGESTIONS_SERVICE";
    /** See {@code Manifest#BIND_MUSIC_RECOGNITION_SERVICE} */
    public static final String BIND_MUSIC_RECOGNITION_SERVICE =
            "android.permission.BIND_MUSIC_RECOGNITION_SERVICE";
    /** See {@code Manifest#BIND_AUGMENTED_AUTOFILL_SERVICE} */
    public static final String BIND_AUGMENTED_AUTOFILL_SERVICE = "android.permission"
            + ".BIND_AUGMENTED_AUTOFILL_SERVICE";
    /** See {@code Manifest#MANAGE_VOICE_KEYPHRASES} */
    public static final String MANAGE_VOICE_KEYPHRASES = "android.permission"
            + ".MANAGE_VOICE_KEYPHRASES";
    /** See {@code Manifest#KEYPHRASE_ENROLLMENT_APPLICATION} */
    public static final String KEYPHRASE_ENROLLMENT_APPLICATION =
            "android.permission.KEYPHRASE_ENROLLMENT_APPLICATION";
    /** See {@code Manifest#BIND_REMOTE_DISPLAY} */
    public static final String BIND_REMOTE_DISPLAY = "android.permission.BIND_REMOTE_DISPLAY";
    /** See {@code Manifest#BIND_TV_INPUT} */
    public static final String BIND_TV_INPUT = "android.permission.BIND_TV_INPUT";
    /** See {@code Manifest#BIND_TV_REMOTE_SERVICE} */
    public static final String BIND_TV_REMOTE_SERVICE = "android.permission"
            + ".BIND_TV_REMOTE_SERVICE";
    /** See {@code Manifest#TV_VIRTUAL_REMOTE_CONTROLLER} */
    public static final String TV_VIRTUAL_REMOTE_CONTROLLER = "android.permission"
            + ".TV_VIRTUAL_REMOTE_CONTROLLER";
    /** See {@code Manifest#CHANGE_HDMI_CEC_ACTIVE_SOURCE} */
    public static final String CHANGE_HDMI_CEC_ACTIVE_SOURCE = "android.permission"
            + ".CHANGE_HDMI_CEC_ACTIVE_SOURCE";
    /** See {@code Manifest#MODIFY_PARENTAL_CONTROLS} */
    public static final String MODIFY_PARENTAL_CONTROLS = "android.permission"
            + ".MODIFY_PARENTAL_CONTROLS";
    /** See {@code Manifest#READ_CONTENT_RATING_SYSTEMS} */
    public static final String READ_CONTENT_RATING_SYSTEMS = "android.permission"
            + ".READ_CONTENT_RATING_SYSTEMS";
    /** See {@code Manifest#NOTIFY_TV_INPUTS} */
    public static final String NOTIFY_TV_INPUTS = "android.permission.NOTIFY_TV_INPUTS";
    /** See {@code Manifest#TUNER_RESOURCE_ACCESS} */
    public static final String TUNER_RESOURCE_ACCESS = "android.permission.TUNER_RESOURCE_ACCESS";
    /** See {@code Manifest#MEDIA_RESOURCE_OVERRIDE_PID} */
    public static final String MEDIA_RESOURCE_OVERRIDE_PID = "android.permission"
            + ".MEDIA_RESOURCE_OVERRIDE_PID";
    /** See {@code Manifest#REGISTER_MEDIA_RESOURCE_OBSERVER} */
    public static final String REGISTER_MEDIA_RESOURCE_OBSERVER = "android.permission"
            + ".REGISTER_MEDIA_RESOURCE_OBSERVER";
    /** See {@code Manifest#BIND_ROUTE_PROVIDER} */
    public static final String BIND_ROUTE_PROVIDER = "android.permission.BIND_ROUTE_PROVIDER";
    /** See {@code Manifest#BIND_DEVICE_ADMIN} */
    public static final String BIND_DEVICE_ADMIN = "android.permission.BIND_DEVICE_ADMIN";
    /** See {@code Manifest#MANAGE_DEVICE_ADMINS} */
    public static final String MANAGE_DEVICE_ADMINS = "android.permission.MANAGE_DEVICE_ADMINS";
    /** See {@code Manifest#RESET_PASSWORD} */
    public static final String RESET_PASSWORD = "android.permission.RESET_PASSWORD";
    /** See {@code Manifest#LOCK_DEVICE} */
    public static final String LOCK_DEVICE = "android.permission.LOCK_DEVICE";
    /** See {@code Manifest#SET_ORIENTATION} */
    public static final String SET_ORIENTATION = "android.permission.SET_ORIENTATION";
    /** See {@code Manifest#SET_POINTER_SPEED} */
    public static final String SET_POINTER_SPEED = "android.permission.SET_POINTER_SPEED";
    /** See {@code Manifest#SET_INPUT_CALIBRATION} */
    public static final String SET_INPUT_CALIBRATION = "android.permission.SET_INPUT_CALIBRATION";
    /** See {@code Manifest#SET_KEYBOARD_LAYOUT} */
    public static final String SET_KEYBOARD_LAYOUT = "android.permission.SET_KEYBOARD_LAYOUT";
    /** See {@code Manifest#SCHEDULE_PRIORITIZED_ALARM} */
    public static final String SCHEDULE_PRIORITIZED_ALARM =
            "android.permission.SCHEDULE_PRIORITIZED_ALARM";
    /** See {@code Manifest#SCHEDULE_EXACT_ALARM} */
    public static final String SCHEDULE_EXACT_ALARM = "android.permission.SCHEDULE_EXACT_ALARM";
    /** See {@code Manifest#TABLET_MODE} */
    public static final String TABLET_MODE = "android.permission.TABLET_MODE";
    /** See {@code Manifest#REQUEST_INSTALL_PACKAGES} */
    public static final String REQUEST_INSTALL_PACKAGES = "android.permission"
            + ".REQUEST_INSTALL_PACKAGES";
    /** See {@code Manifest#REQUEST_DELETE_PACKAGES} */
    public static final String REQUEST_DELETE_PACKAGES = "android.permission"
            + ".REQUEST_DELETE_PACKAGES";
    /** See {@code Manifest#INSTALL_PACKAGES} */
    public static final String INSTALL_PACKAGES = "android.permission.INSTALL_PACKAGES";
    /** See {@code Manifest#INSTALL_SELF_UPDATES} */
    public static final String INSTALL_SELF_UPDATES = "android.permission.INSTALL_SELF_UPDATES";
    /** See {@code Manifest#INSTALL_PACKAGE_UPDATES} */
    public static final String INSTALL_PACKAGE_UPDATES = "android.permission"
            + ".INSTALL_PACKAGE_UPDATES";
    /** See {@code Manifest#INSTALL_EXISTING_PACKAGES} */
    public static final String INSTALL_EXISTING_PACKAGES = "com.android.permission"
            + ".INSTALL_EXISTING_PACKAGES";
    /** See {@code Manifest#USE_INSTALLER_V2} */
    public static final String USE_INSTALLER_V2 = "com.android.permission.USE_INSTALLER_V2";
    /** See {@code Manifest#INSTALL_TEST_ONLY_PACKAGE} */
    public static final String INSTALL_TEST_ONLY_PACKAGE = "android.permission"
            + ".INSTALL_TEST_ONLY_PACKAGE";
    /** See {@code Manifest#INSTALL_DPC_PACKAGES} */
    public static final String INSTALL_DPC_PACKAGES = "android.permission.INSTALL_DPC_PACKAGES";
    /** See {@code Manifest#USE_SYSTEM_DATA_LOADERS} */
    public static final String USE_SYSTEM_DATA_LOADERS = "com.android.permission"
            + ".USE_SYSTEM_DATA_LOADERS";
    /** See {@code Manifest#CLEAR_APP_USER_DATA} */
    public static final String CLEAR_APP_USER_DATA = "android.permission.CLEAR_APP_USER_DATA";
    /** See {@code Manifest#GET_APP_GRANTED_URI_PERMISSIONS} */
    public static final String GET_APP_GRANTED_URI_PERMISSIONS = "android.permission"
            + ".GET_APP_GRANTED_URI_PERMISSIONS";
    /** See {@code Manifest#CLEAR_APP_GRANTED_URI_PERMISSIONS} */
    public static final String CLEAR_APP_GRANTED_URI_PERMISSIONS =
            "android.permission.CLEAR_APP_GRANTED_URI_PERMISSIONS";
    /** See {@code Manifest#MANAGE_SCOPED_ACCESS_DIRECTORY_PERMISSIONS} */
    public static final String MANAGE_SCOPED_ACCESS_DIRECTORY_PERMISSIONS =
            "android.permission.MANAGE_SCOPED_ACCESS_DIRECTORY_PERMISSIONS";
    /** See {@code Manifest#FORCE_PERSISTABLE_URI_PERMISSIONS} */
    public static final String FORCE_PERSISTABLE_URI_PERMISSIONS = "android.permission"
            + ".FORCE_PERSISTABLE_URI_PERMISSIONS";
    /** See {@code Manifest#DELETE_CACHE_FILES} */
    public static final String DELETE_CACHE_FILES = "android.permission.DELETE_CACHE_FILES";
    /** See {@code Manifest#INTERNAL_DELETE_CACHE_FILES} */
    public static final String INTERNAL_DELETE_CACHE_FILES =
            "android.permission.INTERNAL_DELETE_CACHE_FILES";
    /** See {@code Manifest#DELETE_PACKAGES} */
    public static final String DELETE_PACKAGES = "android.permission.DELETE_PACKAGES";
    /** See {@code Manifest#MOVE_PACKAGE} */
    public static final String MOVE_PACKAGE = "android.permission.MOVE_PACKAGE";
    /** See {@code Manifest#KEEP_UNINSTALLED_PACKAGES} */
    public static final String KEEP_UNINSTALLED_PACKAGES =
            "android.permission.KEEP_UNINSTALLED_PACKAGES";
    /** See {@code Manifest#CHANGE_COMPONENT_ENABLED_STATE} */
    public static final String CHANGE_COMPONENT_ENABLED_STATE =
            "android.permission.CHANGE_COMPONENT_ENABLED_STATE";
    /** See {@code Manifest#GRANT_RUNTIME_PERMISSIONS} */
    public static final String GRANT_RUNTIME_PERMISSIONS =
            "android.permission.GRANT_RUNTIME_PERMISSIONS";
    /** See {@code Manifest#INSTALL_GRANT_RUNTIME_PERMISSIONS} */
    public static final String INSTALL_GRANT_RUNTIME_PERMISSIONS = "android.permission"
            + ".INSTALL_GRANT_RUNTIME_PERMISSIONS";
    /** See {@code Manifest#REVOKE_RUNTIME_PERMISSIONS} */
    public static final String REVOKE_RUNTIME_PERMISSIONS = "android.permission"
            + ".REVOKE_RUNTIME_PERMISSIONS";
    /** See {@code Manifest#GET_RUNTIME_PERMISSIONS} */
    public static final String GET_RUNTIME_PERMISSIONS = "android.permission"
            + ".GET_RUNTIME_PERMISSIONS";
    /** See {@code Manifest#RESTORE_RUNTIME_PERMISSIONS} */
    public static final String RESTORE_RUNTIME_PERMISSIONS = "android.permission"
            + ".RESTORE_RUNTIME_PERMISSIONS";
    /** See {@code Manifest#ADJUST_RUNTIME_PERMISSIONS_POLICY} */
    public static final String ADJUST_RUNTIME_PERMISSIONS_POLICY = "android.permission"
            + ".ADJUST_RUNTIME_PERMISSIONS_POLICY";
    /** See {@code Manifest#UPGRADE_RUNTIME_PERMISSIONS} */
    public static final String UPGRADE_RUNTIME_PERMISSIONS =
            "android.permission.UPGRADE_RUNTIME_PERMISSIONS";
    /** See {@code Manifest#WHITELIST_RESTRICTED_PERMISSIONS} */
    public static final String WHITELIST_RESTRICTED_PERMISSIONS = "android.permission"
            + ".WHITELIST_RESTRICTED_PERMISSIONS";
    /** See {@code Manifest#WHITELIST_AUTO_REVOKE_PERMISSIONS} */
    public static final String WHITELIST_AUTO_REVOKE_PERMISSIONS = "android.permission"
            + ".WHITELIST_AUTO_REVOKE_PERMISSIONS";
    /** See {@code Manifest#OBSERVE_GRANT_REVOKE_PERMISSIONS} */
    public static final String OBSERVE_GRANT_REVOKE_PERMISSIONS =
            "android.permission.OBSERVE_GRANT_REVOKE_PERMISSIONS";
    /** See {@code Manifest#MANAGE_ONE_TIME_PERMISSION_SESSIONS} */
    public static final String MANAGE_ONE_TIME_PERMISSION_SESSIONS =
            "android.permission.MANAGE_ONE_TIME_PERMISSION_SESSIONS";
    /** See {@code Manifest#MANAGE_ROLE_HOLDERS} */
    public static final String MANAGE_ROLE_HOLDERS = "android.permission.MANAGE_ROLE_HOLDERS";
    /** See {@code Manifest#BYPASS_ROLE_QUALIFICATION} */
    public static final String BYPASS_ROLE_QUALIFICATION = "android.permission"
            + ".BYPASS_ROLE_QUALIFICATION";
    /** See {@code Manifest#OBSERVE_ROLE_HOLDERS} */
    public static final String OBSERVE_ROLE_HOLDERS = "android.permission.OBSERVE_ROLE_HOLDERS";
    /** See {@code Manifest#MANAGE_COMPANION_DEVICES} */
    public static final String MANAGE_COMPANION_DEVICES =
            "android.permission.MANAGE_COMPANION_DEVICES";
    /** See {@code Manifest#REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE} */
    public static final String REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE =
            "android.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE";
    /** See {@code Manifest#DELIVER_COMPANION_MESSAGES} */
    public static final String DELIVER_COMPANION_MESSAGES = "android.permission"
            + ".DELIVER_COMPANION_MESSAGES";
    /** See {@code Manifest#ACCESS_SURFACE_FLINGER} */
    public static final String ACCESS_SURFACE_FLINGER = "android.permission.ACCESS_SURFACE_FLINGER";
    /** See {@code Manifest#ROTATE_SURFACE_FLINGER} */
    public static final String ROTATE_SURFACE_FLINGER = "android.permission"
            + ".ROTATE_SURFACE_FLINGER";
    /** See {@code Manifest#READ_FRAME_BUFFER} */
    public static final String READ_FRAME_BUFFER = "android.permission.READ_FRAME_BUFFER";
    /** See {@code Manifest#ACCESS_INPUT_FLINGER} */
    public static final String ACCESS_INPUT_FLINGER = "android.permission.ACCESS_INPUT_FLINGER";
    /** See {@code Manifest#DISABLE_INPUT_DEVICE} */
    public static final String DISABLE_INPUT_DEVICE = "android.permission.DISABLE_INPUT_DEVICE";
    /** See {@code Manifest#CONFIGURE_WIFI_DISPLAY} */
    public static final String CONFIGURE_WIFI_DISPLAY =
            "android.permission.CONFIGURE_WIFI_DISPLAY";
    /** See {@code Manifest#CONTROL_WIFI_DISPLAY} */
    public static final String CONTROL_WIFI_DISPLAY = "android.permission.CONTROL_WIFI_DISPLAY";
    /** See {@code Manifest#CONFIGURE_DISPLAY_COLOR_MODE} */
    public static final String CONFIGURE_DISPLAY_COLOR_MODE =
            "android.permission.CONFIGURE_DISPLAY_COLOR_MODE";
    /** See {@code Manifest#CONTROL_DEVICE_LIGHTS} */
    public static final String CONTROL_DEVICE_LIGHTS = "android.permission.CONTROL_DEVICE_LIGHTS";
    /** See {@code Manifest#CONTROL_DISPLAY_SATURATION} */
    public static final String CONTROL_DISPLAY_SATURATION = "android.permission"
            + ".CONTROL_DISPLAY_SATURATION";
    /** See {@code Manifest#CONTROL_DISPLAY_COLOR_TRANSFORMS} */
    public static final String CONTROL_DISPLAY_COLOR_TRANSFORMS =
            "android.permission.CONTROL_DISPLAY_COLOR_TRANSFORMS";
    /** See {@code Manifest#BRIGHTNESS_SLIDER_USAGE} */
    public static final String BRIGHTNESS_SLIDER_USAGE = "android.permission"
            + ".BRIGHTNESS_SLIDER_USAGE";
    /** See {@code Manifest#ACCESS_AMBIENT_LIGHT_STATS} */
    public static final String ACCESS_AMBIENT_LIGHT_STATS =
            "android.permission.ACCESS_AMBIENT_LIGHT_STATS";
    /** See {@code Manifest#CONFIGURE_DISPLAY_BRIGHTNESS} */
    public static final String CONFIGURE_DISPLAY_BRIGHTNESS =
            "android.permission.CONFIGURE_DISPLAY_BRIGHTNESS";
    /** See {@code Manifest#CONTROL_DISPLAY_BRIGHTNESS} */
    public static final String CONTROL_DISPLAY_BRIGHTNESS = "android.permission"
            + ".CONTROL_DISPLAY_BRIGHTNESS";
    /** See {@code Manifest#OVERRIDE_DISPLAY_MODE_REQUESTS} */
    public static final String OVERRIDE_DISPLAY_MODE_REQUESTS = "android.permission"
            + ".OVERRIDE_DISPLAY_MODE_REQUESTS";
    /** See {@code Manifest#MODIFY_REFRESH_RATE_SWITCHING_TYPE} */
    public static final String MODIFY_REFRESH_RATE_SWITCHING_TYPE =
            "android.permission.MODIFY_REFRESH_RATE_SWITCHING_TYPE";
    /** See {@code Manifest#CONTROL_VPN} */
    public static final String CONTROL_VPN = "android.permission.CONTROL_VPN";
    /** See {@code Manifest#CONTROL_ALWAYS_ON_VPN} */
    public static final String CONTROL_ALWAYS_ON_VPN = "android.permission.CONTROL_ALWAYS_ON_VPN";
    /** See {@code Manifest#CAPTURE_TUNER_AUDIO_INPUT} */
    public static final String CAPTURE_TUNER_AUDIO_INPUT =
            "android.permission.CAPTURE_TUNER_AUDIO_INPUT";
    /** See {@code Manifest#CAPTURE_AUDIO_OUTPUT} */
    public static final String CAPTURE_AUDIO_OUTPUT = "android.permission.CAPTURE_AUDIO_OUTPUT";
    /** See {@code Manifest#CAPTURE_MEDIA_OUTPUT} */
    public static final String CAPTURE_MEDIA_OUTPUT = "android.permission.CAPTURE_MEDIA_OUTPUT";
    /** See {@code Manifest#CAPTURE_VOICE_COMMUNICATION_OUTPUT} */
    public static final String CAPTURE_VOICE_COMMUNICATION_OUTPUT = "android.permission"
            + ".CAPTURE_VOICE_COMMUNICATION_OUTPUT";
    /** See {@code Manifest#CAPTURE_AUDIO_HOTWORD} */
    public static final String CAPTURE_AUDIO_HOTWORD = "android.permission.CAPTURE_AUDIO_HOTWORD";
    /** See {@code Manifest#SOUNDTRIGGER_DELEGATE_IDENTITY} */
    public static final String SOUNDTRIGGER_DELEGATE_IDENTITY =
            "android.permission.SOUNDTRIGGER_DELEGATE_IDENTITY";
    /** See {@code Manifest#MODIFY_AUDIO_ROUTING} */
    public static final String MODIFY_AUDIO_ROUTING = "android.permission.MODIFY_AUDIO_ROUTING";
    /** See {@code Manifest#CALL_AUDIO_INTERCEPTION} */
    public static final String CALL_AUDIO_INTERCEPTION =
            "android.permission.CALL_AUDIO_INTERCEPTION";
    /** See {@code Manifest#QUERY_AUDIO_STATE} */
    public static final String QUERY_AUDIO_STATE = "android.permission.QUERY_AUDIO_STATE";
    /** See {@code Manifest#MODIFY_DEFAULT_AUDIO_EFFECTS} */
    public static final String MODIFY_DEFAULT_AUDIO_EFFECTS = "android.permission"
            + ".MODIFY_DEFAULT_AUDIO_EFFECTS";
    /** See {@code Manifest#DISABLE_SYSTEM_SOUND_EFFECTS} */
    public static final String DISABLE_SYSTEM_SOUND_EFFECTS = "android.permission"
            + ".DISABLE_SYSTEM_SOUND_EFFECTS";
    /** See {@code Manifest#REMOTE_DISPLAY_PROVIDER} */
    public static final String REMOTE_DISPLAY_PROVIDER =
            "android.permission.REMOTE_DISPLAY_PROVIDER";
    /** See {@code Manifest#CAPTURE_SECURE_VIDEO_OUTPUT} */
    public static final String CAPTURE_SECURE_VIDEO_OUTPUT =
            "android.permission.CAPTURE_SECURE_VIDEO_OUTPUT";
    /** See {@code Manifest#MEDIA_CONTENT_CONTROL} */
    public static final String MEDIA_CONTENT_CONTROL = "android.permission.MEDIA_CONTENT_CONTROL";
    /** See {@code Manifest#SET_VOLUME_KEY_LONG_PRESS_LISTENER} */
    public static final String SET_VOLUME_KEY_LONG_PRESS_LISTENER =
            "android.permission.SET_VOLUME_KEY_LONG_PRESS_LISTENER";
    /** See {@code Manifest#SET_MEDIA_KEY_LISTENER} */
    public static final String SET_MEDIA_KEY_LISTENER = "android.permission"
            + ".SET_MEDIA_KEY_LISTENER";
    /** See {@code Manifest#BRICK} */
    public static final String BRICK = "android.permission.BRICK";
    /** See {@code Manifest#REBOOT} */
    public static final String REBOOT = "android.permission.REBOOT";
    /** See {@code Manifest#DEVICE_POWER} */
    public static final String DEVICE_POWER = "android.permission.DEVICE_POWER";
    /** See {@code Manifest#POWER_SAVER} */
    public static final String POWER_SAVER = "android.permission.POWER_SAVER";
    /** See {@code Manifest#BATTERY_PREDICTION} */
    public static final String BATTERY_PREDICTION = "android.permission.BATTERY_PREDICTION";
    /** See {@code Manifest#USER_ACTIVITY} */
    public static final String USER_ACTIVITY = "android.permission.USER_ACTIVITY";
    /** See {@code Manifest#NET_TUNNELING} */
    public static final String NET_TUNNELING = "android.permission.NET_TUNNELING";
    /** See {@code Manifest#FACTORY_TEST} */
    public static final String FACTORY_TEST = "android.permission.FACTORY_TEST";
    /** See {@code Manifest#BROADCAST_CLOSE_SYSTEM_DIALOGS} */
    public static final String BROADCAST_CLOSE_SYSTEM_DIALOGS =
            "android.permission.BROADCAST_CLOSE_SYSTEM_DIALOGS";
    /** See {@code Manifest#BROADCAST_PACKAGE_REMOVED} */
    public static final String BROADCAST_PACKAGE_REMOVED =
            "android.permission.BROADCAST_PACKAGE_REMOVED";
    /** See {@code Manifest#BROADCAST_SMS} */
    public static final String BROADCAST_SMS = "android.permission.BROADCAST_SMS";
    /** See {@code Manifest#BROADCAST_WAP_PUSH} */
    public static final String BROADCAST_WAP_PUSH = "android.permission.BROADCAST_WAP_PUSH";
    /** See {@code Manifest#BROADCAST_NETWORK_PRIVILEGED} */
    public static final String BROADCAST_NETWORK_PRIVILEGED = "android.permission"
            + ".BROADCAST_NETWORK_PRIVILEGED";
    /** See {@code Manifest#MASTER_CLEAR} */
    public static final String MASTER_CLEAR = "android.permission.MASTER_CLEAR";
    /** See {@code Manifest#CALL_PRIVILEGED} */
    public static final String CALL_PRIVILEGED = "android.permission.CALL_PRIVILEGED";
    /** See {@code Manifest#PERFORM_CDMA_PROVISIONING} */
    public static final String PERFORM_CDMA_PROVISIONING =
            "android.permission.PERFORM_CDMA_PROVISIONING";
    /** See {@code Manifest#PERFORM_SIM_ACTIVATION} */
    public static final String PERFORM_SIM_ACTIVATION = "android.permission.PERFORM_SIM_ACTIVATION";
    /** See {@code Manifest#CONTROL_LOCATION_UPDATES} */
    public static final String CONTROL_LOCATION_UPDATES =
            "android.permission.CONTROL_LOCATION_UPDATES";
    /** See {@code Manifest#ACCESS_CHECKIN_PROPERTIES} */
    public static final String ACCESS_CHECKIN_PROPERTIES = "android.permission"
            + ".ACCESS_CHECKIN_PROPERTIES";
    /** See {@code Manifest#PACKAGE_USAGE_STATS} */
    public static final String PACKAGE_USAGE_STATS = "android.permission.PACKAGE_USAGE_STATS";
    /** See {@code Manifest#LOADER_USAGE_STATS} */
    public static final String LOADER_USAGE_STATS = "android.permission.LOADER_USAGE_STATS";
    /** See {@code Manifest#OBSERVE_APP_USAGE} */
    public static final String OBSERVE_APP_USAGE = "android.permission.OBSERVE_APP_USAGE";
    /** See {@code Manifest#CHANGE_APP_IDLE_STATE} */
    public static final String CHANGE_APP_IDLE_STATE = "android.permission.CHANGE_APP_IDLE_STATE";
    /** See {@code Manifest#CHANGE_APP_LAUNCH_TIME_ESTIMATE} */
    public static final String CHANGE_APP_LAUNCH_TIME_ESTIMATE =
            "android.permission.CHANGE_APP_LAUNCH_TIME_ESTIMATE";
    /** See {@code Manifest#CHANGE_DEVICE_IDLE_TEMP_WHITELIST} */
    public static final String CHANGE_DEVICE_IDLE_TEMP_WHITELIST =
            "android.permission.CHANGE_DEVICE_IDLE_TEMP_WHITELIST";
    /** See {@code Manifest#REQUEST_IGNORE_BATTERY_OPTIMIZATIONS} */
    public static final String REQUEST_IGNORE_BATTERY_OPTIMIZATIONS =
            "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS";
    /** See {@code Manifest#BATTERY_STATS} */
    public static final String BATTERY_STATS = "android.permission.BATTERY_STATS";
    /** See {@code Manifest#STATSCOMPANION} */
    public static final String STATSCOMPANION = "android.permission.STATSCOMPANION";
    /** See {@code Manifest#REGISTER_STATS_PULL_ATOM} */
    public static final String REGISTER_STATS_PULL_ATOM = "android.permission"
            + ".REGISTER_STATS_PULL_ATOM";
    /** See {@code Manifest#BACKUP} */
    public static final String BACKUP = "android.permission.BACKUP";
    /** See {@code Manifest#MODIFY_SETTINGS_OVERRIDEABLE_BY_RESTORE} */
    public static final String MODIFY_SETTINGS_OVERRIDEABLE_BY_RESTORE = "android.permission"
            + ".MODIFY_SETTINGS_OVERRIDEABLE_BY_RESTORE";
    /** See {@code Manifest#RECOVER_KEYSTORE} */
    public static final String RECOVER_KEYSTORE = "android.permission.RECOVER_KEYSTORE";
    /** See {@code Manifest#CONFIRM_FULL_BACKUP} */
    public static final String CONFIRM_FULL_BACKUP = "android.permission.CONFIRM_FULL_BACKUP";
    /** See {@code Manifest#BIND_REMOTEVIEWS} */
    public static final String BIND_REMOTEVIEWS = "android.permission.BIND_REMOTEVIEWS";
    /** See {@code Manifest#BIND_APPWIDGET} */
    public static final String BIND_APPWIDGET = "android.permission.BIND_APPWIDGET";
    /** See {@code Manifest#MANAGE_SLICE_PERMISSIONS} */
    public static final String MANAGE_SLICE_PERMISSIONS =
            "android.permission.MANAGE_SLICE_PERMISSIONS";
    /** See {@code Manifest#BIND_KEYGUARD_APPWIDGET} */
    public static final String BIND_KEYGUARD_APPWIDGET = "android.permission"
            + ".BIND_KEYGUARD_APPWIDGET";
    /** See {@code Manifest#MODIFY_APPWIDGET_BIND_PERMISSIONS} */
    public static final String MODIFY_APPWIDGET_BIND_PERMISSIONS =
            "android.permission.MODIFY_APPWIDGET_BIND_PERMISSIONS";
    /** See {@code Manifest#CHANGE_BACKGROUND_DATA_SETTING} */
    public static final String CHANGE_BACKGROUND_DATA_SETTING =
            "android.permission.CHANGE_BACKGROUND_DATA_SETTING";
    /** See {@code Manifest#GLOBAL_SEARCH} */
    public static final String GLOBAL_SEARCH = "android.permission.GLOBAL_SEARCH";
    /** See {@code Manifest#GLOBAL_SEARCH_CONTROL} */
    public static final String GLOBAL_SEARCH_CONTROL = "android.permission.GLOBAL_SEARCH_CONTROL";
    /** See {@code Manifest#READ_SEARCH_INDEXABLES} */
    public static final String READ_SEARCH_INDEXABLES = "android.permission"
            + ".READ_SEARCH_INDEXABLES";
    /** See {@code Manifest#BIND_SETTINGS_SUGGESTIONS_SERVICE} */
    public static final String BIND_SETTINGS_SUGGESTIONS_SERVICE = "android.permission"
            + ".BIND_SETTINGS_SUGGESTIONS_SERVICE";
    /** See {@code Manifest#WRITE_SETTINGS_HOMEPAGE_DATA} */
    public static final String WRITE_SETTINGS_HOMEPAGE_DATA = "android.permission"
            + ".WRITE_SETTINGS_HOMEPAGE_DATA";
    /** See {@code Manifest#LAUNCH_MULTI_PANE_SETTINGS_DEEP_LINK} */
    public static final String LAUNCH_MULTI_PANE_SETTINGS_DEEP_LINK = "android.permission"
            + ".LAUNCH_MULTI_PANE_SETTINGS_DEEP_LINK";
    /** See {@code Manifest#ALLOW_PLACE_IN_MULTI_PANE_SETTINGS} */
    public static final String ALLOW_PLACE_IN_MULTI_PANE_SETTINGS =
            "android.permission.ALLOW_PLACE_IN_MULTI_PANE_SETTINGS";
    /** See {@code Manifest#SET_WALLPAPER_COMPONENT} */
    public static final String SET_WALLPAPER_COMPONENT = "android.permission"
            + ".SET_WALLPAPER_COMPONENT";
    /** See {@code Manifest#READ_DREAM_STATE} */
    public static final String READ_DREAM_STATE = "android.permission.READ_DREAM_STATE";
    /** See {@code Manifest#WRITE_DREAM_STATE} */
    public static final String WRITE_DREAM_STATE = "android.permission.WRITE_DREAM_STATE";
    /** See {@code Manifest#READ_DREAM_SUPPRESSION} */
    public static final String READ_DREAM_SUPPRESSION = "android.permission"
            + ".READ_DREAM_SUPPRESSION";
    /** See {@code Manifest#ACCESS_CACHE_FILESYSTEM} */
    public static final String ACCESS_CACHE_FILESYSTEM = "android.permission"
            + ".ACCESS_CACHE_FILESYSTEM";
    /** See {@code Manifest#COPY_PROTECTED_DATA} */
    public static final String COPY_PROTECTED_DATA = "android.permission.COPY_PROTECTED_DATA";
    /** See {@code Manifest#CRYPT_KEEPER} */
    public static final String CRYPT_KEEPER = "android.permission.CRYPT_KEEPER";
    /** See {@code Manifest#READ_NETWORK_USAGE_HISTORY} */
    public static final String READ_NETWORK_USAGE_HISTORY =
            "android.permission.READ_NETWORK_USAGE_HISTORY";
    /** See {@code Manifest#MANAGE_NETWORK_POLICY} */
    public static final String MANAGE_NETWORK_POLICY = "android.permission.MANAGE_NETWORK_POLICY";
    /** See {@code Manifest#MODIFY_NETWORK_ACCOUNTING} */
    public static final String MODIFY_NETWORK_ACCOUNTING =
            "android.permission.MODIFY_NETWORK_ACCOUNTING";
    /** See {@code Manifest#MANAGE_SUBSCRIPTION_PLANS} */
    public static final String MANAGE_SUBSCRIPTION_PLANS = "android.permission"
            + ".MANAGE_SUBSCRIPTION_PLANS";
    /** See {@code Manifest#C2D_MESSAGE} */
    public static final String C2D_MESSAGE = "android.intent.category.MASTER_CLEAR.permission"
            + ".C2D_MESSAGE";
    /** See {@code Manifest#PACKAGE_VERIFICATION_AGENT} */
    public static final String PACKAGE_VERIFICATION_AGENT =
            "android.permission.PACKAGE_VERIFICATION_AGENT";
    /** See {@code Manifest#BIND_PACKAGE_VERIFIER} */
    public static final String BIND_PACKAGE_VERIFIER = "android.permission.BIND_PACKAGE_VERIFIER";
    /** See {@code Manifest#PACKAGE_ROLLBACK_AGENT} */
    public static final String PACKAGE_ROLLBACK_AGENT = "android.permission.PACKAGE_ROLLBACK_AGENT";
    /** See {@code Manifest#MANAGE_ROLLBACKS} */
    public static final String MANAGE_ROLLBACKS = "android.permission.MANAGE_ROLLBACKS";
    /** See {@code Manifest#TEST_MANAGE_ROLLBACKS} */
    public static final String TEST_MANAGE_ROLLBACKS = "android.permission.TEST_MANAGE_ROLLBACKS";
    /** See {@code Manifest#SET_HARMFUL_APP_WARNINGS} */
    public static final String SET_HARMFUL_APP_WARNINGS =
            "android.permission.SET_HARMFUL_APP_WARNINGS";
    /** See {@code Manifest#INTENT_FILTER_VERIFICATION_AGENT} */
    public static final String INTENT_FILTER_VERIFICATION_AGENT =
            "android.permission.INTENT_FILTER_VERIFICATION_AGENT";
    /** See {@code Manifest#BIND_INTENT_FILTER_VERIFIER} */
    public static final String BIND_INTENT_FILTER_VERIFIER = "android.permission"
            + ".BIND_INTENT_FILTER_VERIFIER";
    /** See {@code Manifest#DOMAIN_VERIFICATION_AGENT} */
    public static final String DOMAIN_VERIFICATION_AGENT = "android.permission"
            + ".DOMAIN_VERIFICATION_AGENT";
    /** See {@code Manifest#BIND_DOMAIN_VERIFICATION_AGENT} */
    public static final String BIND_DOMAIN_VERIFICATION_AGENT =
            "android.permission.BIND_DOMAIN_VERIFICATION_AGENT";
    /** See {@code Manifest#UPDATE_DOMAIN_VERIFICATION_USER_SELECTION} */
    public static final String UPDATE_DOMAIN_VERIFICATION_USER_SELECTION =
            "android.permission.UPDATE_DOMAIN_VERIFICATION_USER_SELECTION";
    /** See {@code Manifest#SERIAL_PORT} */
    public static final String SERIAL_PORT = "android.permission.SERIAL_PORT";
    /** See {@code Manifest#ACCESS_CONTENT_PROVIDERS_EXTERNALLY} */
    public static final String ACCESS_CONTENT_PROVIDERS_EXTERNALLY = "android.permission"
            + ".ACCESS_CONTENT_PROVIDERS_EXTERNALLY";
    /** See {@code Manifest#UPDATE_LOCK} */
    public static final String UPDATE_LOCK = "android.permission.UPDATE_LOCK";
    /** See {@code Manifest#REQUEST_NOTIFICATION_ASSISTANT_SERVICE} */
    public static final String REQUEST_NOTIFICATION_ASSISTANT_SERVICE = "android.permission"
            + ".REQUEST_NOTIFICATION_ASSISTANT_SERVICE";
    /** See {@code Manifest#ACCESS_NOTIFICATIONS} */
    public static final String ACCESS_NOTIFICATIONS = "android.permission.ACCESS_NOTIFICATIONS";
    /** See {@code Manifest#ACCESS_NOTIFICATION_POLICY} */
    public static final String ACCESS_NOTIFICATION_POLICY =
            "android.permission.ACCESS_NOTIFICATION_POLICY";
    /** See {@code Manifest#MANAGE_NOTIFICATIONS} */
    public static final String MANAGE_NOTIFICATIONS = "android.permission.MANAGE_NOTIFICATIONS";
    /** See {@code Manifest#MANAGE_NOTIFICATION_LISTENERS} */
    public static final String MANAGE_NOTIFICATION_LISTENERS =
            "android.permission.MANAGE_NOTIFICATION_LISTENERS";
    /** See {@code Manifest#USE_COLORIZED_NOTIFICATIONS} */
    public static final String USE_COLORIZED_NOTIFICATIONS =
            "android.permission.USE_COLORIZED_NOTIFICATIONS";
    /** See {@code Manifest#ACCESS_KEYGUARD_SECURE_STORAGE} */
    public static final String ACCESS_KEYGUARD_SECURE_STORAGE = "android.permission"
            + ".ACCESS_KEYGUARD_SECURE_STORAGE";
    /** See {@code Manifest#SET_INITIAL_LOCK} */
    public static final String SET_INITIAL_LOCK = "android.permission.SET_INITIAL_LOCK";
    /** See {@code Manifest#SET_AND_VERIFY_LOCKSCREEN_CREDENTIALS} */
    public static final String SET_AND_VERIFY_LOCKSCREEN_CREDENTIALS =
            "android.permission.SET_AND_VERIFY_LOCKSCREEN_CREDENTIALS";
    /** See {@code Manifest#MANAGE_FINGERPRINT} */
    public static final String MANAGE_FINGERPRINT = "android.permission.MANAGE_FINGERPRINT";
    /** See {@code Manifest#RESET_FINGERPRINT_LOCKOUT} */
    public static final String RESET_FINGERPRINT_LOCKOUT = "android.permission"
            + ".RESET_FINGERPRINT_LOCKOUT";
    /** See {@code Manifest#TEST_BIOMETRIC} */
    public static final String TEST_BIOMETRIC = "android.permission.TEST_BIOMETRIC";
    /** See {@code Manifest#MANAGE_BIOMETRIC} */
    public static final String MANAGE_BIOMETRIC = "android.permission.MANAGE_BIOMETRIC";
    /** See {@code Manifest#USE_BIOMETRIC_INTERNAL} */
    public static final String USE_BIOMETRIC_INTERNAL = "android.permission"
            + ".USE_BIOMETRIC_INTERNAL";
    /** See {@code Manifest#MANAGE_BIOMETRIC_DIALOG} */
    public static final String MANAGE_BIOMETRIC_DIALOG =
            "android.permission.MANAGE_BIOMETRIC_DIALOG";
    /** See {@code Manifest#CONTROL_KEYGUARD} */
    public static final String CONTROL_KEYGUARD = "android.permission.CONTROL_KEYGUARD";
    /** See {@code Manifest#CONTROL_KEYGUARD_SECURE_NOTIFICATIONS} */
    public static final String CONTROL_KEYGUARD_SECURE_NOTIFICATIONS = "android.permission"
            + ".CONTROL_KEYGUARD_SECURE_NOTIFICATIONS";
    /** See {@code Manifest#TRUST_LISTENER} */
    public static final String TRUST_LISTENER = "android.permission.TRUST_LISTENER";
    /** See {@code Manifest#PROVIDE_TRUST_AGENT} */
    public static final String PROVIDE_TRUST_AGENT = "android.permission.PROVIDE_TRUST_AGENT";
    /** See {@code Manifest#SHOW_KEYGUARD_MESSAGE} */
    public static final String SHOW_KEYGUARD_MESSAGE = "android.permission.SHOW_KEYGUARD_MESSAGE";
    /** See {@code Manifest#LAUNCH_TRUST_AGENT_SETTINGS} */
    public static final String LAUNCH_TRUST_AGENT_SETTINGS = "android.permission"
            + ".LAUNCH_TRUST_AGENT_SETTINGS";
    /** See {@code Manifest#BIND_TRUST_AGENT} */
    public static final String BIND_TRUST_AGENT = "android.permission.BIND_TRUST_AGENT";
    /** See {@code Manifest#BIND_NOTIFICATION_LISTENER_SERVICE} */
    public static final String BIND_NOTIFICATION_LISTENER_SERVICE =
            "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE";
    /** See {@code Manifest#BIND_NOTIFICATION_ASSISTANT_SERVICE} */
    public static final String BIND_NOTIFICATION_ASSISTANT_SERVICE =
            "android.permission.BIND_NOTIFICATION_ASSISTANT_SERVICE";
    /** See {@code Manifest#BIND_CHOOSER_TARGET_SERVICE} */
    public static final String BIND_CHOOSER_TARGET_SERVICE =
            "android.permission.BIND_CHOOSER_TARGET_SERVICE";
    /** See {@code Manifest#PROVIDE_RESOLVER_RANKER_SERVICE} */
    public static final String PROVIDE_RESOLVER_RANKER_SERVICE = "android.permission"
            + ".PROVIDE_RESOLVER_RANKER_SERVICE";
    /** See {@code Manifest#BIND_RESOLVER_RANKER_SERVICE} */
    public static final String BIND_RESOLVER_RANKER_SERVICE =
            "android.permission.BIND_RESOLVER_RANKER_SERVICE";
    /** See {@code Manifest#BIND_CONDITION_PROVIDER_SERVICE} */
    public static final String BIND_CONDITION_PROVIDER_SERVICE =
            "android.permission.BIND_CONDITION_PROVIDER_SERVICE";
    /** See {@code Manifest#BIND_DREAM_SERVICE} */
    public static final String BIND_DREAM_SERVICE = "android.permission.BIND_DREAM_SERVICE";
    /** See {@code Manifest#BIND_CACHE_QUOTA_SERVICE} */
    public static final String BIND_CACHE_QUOTA_SERVICE = "android.permission"
            + ".BIND_CACHE_QUOTA_SERVICE";
    /** See {@code Manifest#INVOKE_CARRIER_SETUP} */
    public static final String INVOKE_CARRIER_SETUP = "android.permission.INVOKE_CARRIER_SETUP";
    /** See {@code Manifest#ACCESS_NETWORK_CONDITIONS} */
    public static final String ACCESS_NETWORK_CONDITIONS =
            "android.permission.ACCESS_NETWORK_CONDITIONS";
    /** See {@code Manifest#ACCESS_DRM_CERTIFICATES} */
    public static final String ACCESS_DRM_CERTIFICATES =
            "android.permission.ACCESS_DRM_CERTIFICATES";
    /** See {@code Manifest#MANAGE_MEDIA_PROJECTION} */
    public static final String MANAGE_MEDIA_PROJECTION =
            "android.permission.MANAGE_MEDIA_PROJECTION";
    /** See {@code Manifest#READ_INSTALL_SESSIONS} */
    public static final String READ_INSTALL_SESSIONS = "android.permission.READ_INSTALL_SESSIONS";
    /** See {@code Manifest#REMOVE_DRM_CERTIFICATES} */
    public static final String REMOVE_DRM_CERTIFICATES = "android.permission"
            + ".REMOVE_DRM_CERTIFICATES";
    /** See {@code Manifest#BIND_CARRIER_MESSAGING_SERVICE} */
    public static final String BIND_CARRIER_MESSAGING_SERVICE =
            "android.permission.BIND_CARRIER_MESSAGING_SERVICE";
    /** See {@code Manifest#ACCESS_VOICE_INTERACTION_SERVICE} */
    public static final String ACCESS_VOICE_INTERACTION_SERVICE =
            "android.permission.ACCESS_VOICE_INTERACTION_SERVICE";
    /** See {@code Manifest#BIND_CARRIER_SERVICES} */
    public static final String BIND_CARRIER_SERVICES = "android.permission.BIND_CARRIER_SERVICES";
    /** See {@code Manifest#START_VIEW_PERMISSION_USAGE} */
    public static final String START_VIEW_PERMISSION_USAGE = "android.permission"
            + ".START_VIEW_PERMISSION_USAGE";
    /** See {@code Manifest#QUERY_DO_NOT_ASK_CREDENTIALS_ON_BOOT} */
    public static final String QUERY_DO_NOT_ASK_CREDENTIALS_ON_BOOT = "android.permission"
            + ".QUERY_DO_NOT_ASK_CREDENTIALS_ON_BOOT";
    /** See {@code Manifest#KILL_UID} */
    public static final String KILL_UID = "android.permission.KILL_UID";
    /** See {@code Manifest#LOCAL_MAC_ADDRESS} */
    public static final String LOCAL_MAC_ADDRESS = "android.permission.LOCAL_MAC_ADDRESS";
    /** See {@code Manifest#PEERS_MAC_ADDRESS} */
    public static final String PEERS_MAC_ADDRESS = "android.permission.PEERS_MAC_ADDRESS";
    /** See {@code Manifest#DISPATCH_NFC_MESSAGE} */
    public static final String DISPATCH_NFC_MESSAGE = "android.permission.DISPATCH_NFC_MESSAGE";
    /** See {@code Manifest#MODIFY_DAY_NIGHT_MODE} */
    public static final String MODIFY_DAY_NIGHT_MODE = "android.permission.MODIFY_DAY_NIGHT_MODE";
    /** See {@code Manifest#ENTER_CAR_MODE_PRIORITIZED} */
    public static final String ENTER_CAR_MODE_PRIORITIZED = "android.permission"
            + ".ENTER_CAR_MODE_PRIORITIZED";
    /** See {@code Manifest#HANDLE_CAR_MODE_CHANGES} */
    public static final String HANDLE_CAR_MODE_CHANGES = "android.permission"
            + ".HANDLE_CAR_MODE_CHANGES";
    /** See {@code Manifest#SEND_CATEGORY_CAR_NOTIFICATIONS} */
    public static final String SEND_CATEGORY_CAR_NOTIFICATIONS =
            "android.permission.SEND_CATEGORY_CAR_NOTIFICATIONS";
    /** See {@code Manifest#ACCESS_INSTANT_APPS} */
    public static final String ACCESS_INSTANT_APPS = "android.permission.ACCESS_INSTANT_APPS";
    /** See {@code Manifest#VIEW_INSTANT_APPS} */
    public static final String VIEW_INSTANT_APPS = "android.permission.VIEW_INSTANT_APPS";
    /** See {@code Manifest#WRITE_COMMUNAL_STATE} */
    public static final String WRITE_COMMUNAL_STATE = "android.permission.WRITE_COMMUNAL_STATE";
    /** See {@code Manifest#READ_COMMUNAL_STATE} */
    public static final String READ_COMMUNAL_STATE = "android.permission.READ_COMMUNAL_STATE";
    /** See {@code Manifest#MANAGE_BIND_INSTANT_SERVICE} */
    public static final String MANAGE_BIND_INSTANT_SERVICE =
            "android.permission.MANAGE_BIND_INSTANT_SERVICE";
    /** See {@code Manifest#RECEIVE_MEDIA_RESOURCE_USAGE} */
    public static final String RECEIVE_MEDIA_RESOURCE_USAGE = "android.permission.RECEIVE_MEDIA_RESOURCE_USAGE";
    /** See {@code Manifest#MANAGE_SOUND_TRIGGER} */
    public static final String MANAGE_SOUND_TRIGGER = "android.permission.MANAGE_SOUND_TRIGGER";
    /** See {@code Manifest#SOUND_TRIGGER_RUN_IN_BATTERY_SAVER} */
    public static final String SOUND_TRIGGER_RUN_IN_BATTERY_SAVER = "android.permission"
            + ".SOUND_TRIGGER_RUN_IN_BATTERY_SAVER";
    /** See {@code Manifest#BIND_SOUND_TRIGGER_DETECTION_SERVICE} */
    public static final String BIND_SOUND_TRIGGER_DETECTION_SERVICE = "android.permission"
            + ".BIND_SOUND_TRIGGER_DETECTION_SERVICE";
    /** See {@code Manifest#DISPATCH_PROVISIONING_MESSAGE} */
    public static final String DISPATCH_PROVISIONING_MESSAGE = "android.permission"
            + ".DISPATCH_PROVISIONING_MESSAGE";
    /** See {@code Manifest#READ_BLOCKED_NUMBERS} */
    public static final String READ_BLOCKED_NUMBERS = "android.permission.READ_BLOCKED_NUMBERS";
    /** See {@code Manifest#WRITE_BLOCKED_NUMBERS} */
    public static final String WRITE_BLOCKED_NUMBERS = "android.permission.WRITE_BLOCKED_NUMBERS";
    /** See {@code Manifest#BIND_VR_LISTENER_SERVICE} */
    public static final String BIND_VR_LISTENER_SERVICE = "android.permission"
            + ".BIND_VR_LISTENER_SERVICE";
    /** See {@code Manifest#RESTRICTED_VR_ACCESS} */
    public static final String RESTRICTED_VR_ACCESS = "android.permission.RESTRICTED_VR_ACCESS";
    /** See {@code Manifest#ACCESS_VR_MANAGER} */
    public static final String ACCESS_VR_MANAGER = "android.permission.ACCESS_VR_MANAGER";
    /** See {@code Manifest#ACCESS_VR_STATE} */
    public static final String ACCESS_VR_STATE = "android.permission.ACCESS_VR_STATE";
    /** See {@code Manifest#UPDATE_LOCK_TASK_PACKAGES} */
    public static final String UPDATE_LOCK_TASK_PACKAGES =
            "android.permission.UPDATE_LOCK_TASK_PACKAGES";
    /** See {@code Manifest#SUBSTITUTE_NOTIFICATION_APP_NAME} */
    public static final String SUBSTITUTE_NOTIFICATION_APP_NAME = "android.permission"
            + ".SUBSTITUTE_NOTIFICATION_APP_NAME";
    /** See {@code Manifest#NOTIFICATION_DURING_SETUP} */
    public static final String NOTIFICATION_DURING_SETUP =
            "android.permission.NOTIFICATION_DURING_SETUP";
    /** See {@code Manifest#MANAGE_AUTO_FILL} */
    public static final String MANAGE_AUTO_FILL = "android.permission.MANAGE_AUTO_FILL";
    /** See {@code Manifest#MANAGE_CONTENT_CAPTURE} */
    public static final String MANAGE_CONTENT_CAPTURE = "android.permission.MANAGE_CONTENT_CAPTURE";
    /** See {@code Manifest#MANAGE_ROTATION_RESOLVER} */
    public static final String MANAGE_ROTATION_RESOLVER = "android.permission"
            + ".MANAGE_ROTATION_RESOLVER";
    /** See {@code Manifest#MANAGE_MUSIC_RECOGNITION} */
    public static final String MANAGE_MUSIC_RECOGNITION =
            "android.permission.MANAGE_MUSIC_RECOGNITION";
    /** See {@code Manifest#MANAGE_SPEECH_RECOGNITION} */
    public static final String MANAGE_SPEECH_RECOGNITION =
            "android.permission.MANAGE_SPEECH_RECOGNITION";
    /** See {@code Manifest#MANAGE_CONTENT_SUGGESTIONS} */
    public static final String MANAGE_CONTENT_SUGGESTIONS = "android.permission"
            + ".MANAGE_CONTENT_SUGGESTIONS";
    /** See {@code Manifest#MANAGE_APP_PREDICTIONS} */
    public static final String MANAGE_APP_PREDICTIONS = "android.permission.MANAGE_APP_PREDICTIONS";
    /** See {@code Manifest#MANAGE_SEARCH_UI} */
    public static final String MANAGE_SEARCH_UI = "android.permission.MANAGE_SEARCH_UI";
    /** See {@code Manifest#MANAGE_SMARTSPACE} */
    public static final String MANAGE_SMARTSPACE = "android.permission.MANAGE_SMARTSPACE";
    /** See {@code Manifest#MODIFY_THEME_OVERLAY} */
    public static final String MODIFY_THEME_OVERLAY = "android.permission.MODIFY_THEME_OVERLAY";
    /** See {@code Manifest#INSTANT_APP_FOREGROUND_SERVICE} */
    public static final String INSTANT_APP_FOREGROUND_SERVICE =
            "android.permission.INSTANT_APP_FOREGROUND_SERVICE";
    /** See {@code Manifest#FOREGROUND_SERVICE} */
    public static final String FOREGROUND_SERVICE = "android.permission.FOREGROUND_SERVICE";
    /** See {@code Manifest#ACCESS_SHORTCUTS} */
    public static final String ACCESS_SHORTCUTS = "android.permission.ACCESS_SHORTCUTS";
    /** See {@code Manifest#UNLIMITED_SHORTCUTS_API_CALLS} */
    public static final String UNLIMITED_SHORTCUTS_API_CALLS =
            "android.permission.UNLIMITED_SHORTCUTS_API_CALLS";
    /** See {@code Manifest#READ_RUNTIME_PROFILES} */
    public static final String READ_RUNTIME_PROFILES = "android.permission.READ_RUNTIME_PROFILES";
    /** See {@code Manifest#MANAGE_AUDIO_POLICY} */
    public static final String MANAGE_AUDIO_POLICY = "android.permission.MANAGE_AUDIO_POLICY";
    /** See {@code Manifest#MODIFY_QUIET_MODE} */
    public static final String MODIFY_QUIET_MODE = "android.permission.MODIFY_QUIET_MODE";
    /** See {@code Manifest#MANAGE_CAMERA} */
    public static final String MANAGE_CAMERA = "android.permission.MANAGE_CAMERA";
    /** See {@code Manifest#CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS} */
    public static final String CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS =
            "android.permission.CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS";
    /** See {@code Manifest#WATCH_APPOPS} */
    public static final String WATCH_APPOPS = "android.permission.WATCH_APPOPS";
    /** See {@code Manifest#DISABLE_HIDDEN_API_CHECKS} */
    public static final String DISABLE_HIDDEN_API_CHECKS =
            "android.permission.DISABLE_HIDDEN_API_CHECKS";
    /** See {@code Manifest#MONITOR_DEFAULT_SMS_PACKAGE} */
    public static final String MONITOR_DEFAULT_SMS_PACKAGE =
            "android.permission.MONITOR_DEFAULT_SMS_PACKAGE";
    /** See {@code Manifest#BIND_CARRIER_MESSAGING_CLIENT_SERVICE} */
    public static final String BIND_CARRIER_MESSAGING_CLIENT_SERVICE =
            "android.permission.BIND_CARRIER_MESSAGING_CLIENT_SERVICE";
    /** See {@code Manifest#BIND_EXPLICIT_HEALTH_CHECK_SERVICE} */
    public static final String BIND_EXPLICIT_HEALTH_CHECK_SERVICE =
            "android.permission.BIND_EXPLICIT_HEALTH_CHECK_SERVICE";
    /** See {@code Manifest#BIND_EXTERNAL_STORAGE_SERVICE} */
    public static final String BIND_EXTERNAL_STORAGE_SERVICE = "android.permission"
            + ".BIND_EXTERNAL_STORAGE_SERVICE";
    /** See {@code Manifest#MANAGE_APPOPS} */
    public static final String MANAGE_APPOPS = "android.permission.MANAGE_APPOPS";
    /** See {@code Manifest#READ_CLIPBOARD_IN_BACKGROUND} */
    public static final String READ_CLIPBOARD_IN_BACKGROUND = "android.permission"
            + ".READ_CLIPBOARD_IN_BACKGROUND";
    /** See {@code Manifest#MANAGE_ACCESSIBILITY} */
    public static final String MANAGE_ACCESSIBILITY = "android.permission.MANAGE_ACCESSIBILITY";
    /** See {@code Manifest#GRANT_PROFILE_OWNER_DEVICE_IDS_ACCESS} */
    public static final String GRANT_PROFILE_OWNER_DEVICE_IDS_ACCESS =
            "android.permission.GRANT_PROFILE_OWNER_DEVICE_IDS_ACCESS";
    /** See {@code Manifest#MARK_DEVICE_ORGANIZATION_OWNED} */
    public static final String MARK_DEVICE_ORGANIZATION_OWNED = "android.permission"
            + ".MARK_DEVICE_ORGANIZATION_OWNED";
    /** See {@code Manifest#SMS_FINANCIAL_TRANSACTIONS} */
    public static final String SMS_FINANCIAL_TRANSACTIONS =
            "android.permission.SMS_FINANCIAL_TRANSACTIONS";
    /** See {@code Manifest#USE_FULL_SCREEN_INTENT} */
    public static final String USE_FULL_SCREEN_INTENT = "android.permission.USE_FULL_SCREEN_INTENT";
    /** See {@code Manifest#SEND_DEVICE_CUSTOMIZATION_READY} */
    public static final String SEND_DEVICE_CUSTOMIZATION_READY =
            "android.permission.SEND_DEVICE_CUSTOMIZATION_READY";
    /** See {@code Manifest#RECEIVE_DEVICE_CUSTOMIZATION_READY} */
    public static final String RECEIVE_DEVICE_CUSTOMIZATION_READY =
            "android.permission.RECEIVE_DEVICE_CUSTOMIZATION_READY";
    /** See {@code Manifest#AMBIENT_WALLPAPER} */
    public static final String AMBIENT_WALLPAPER = "android.permission.AMBIENT_WALLPAPER";
    /** See {@code Manifest#MANAGE_SENSOR_PRIVACY} */
    public static final String MANAGE_SENSOR_PRIVACY = "android.permission.MANAGE_SENSOR_PRIVACY";
    /** See {@code Manifest#OBSERVE_SENSOR_PRIVACY} */
    public static final String OBSERVE_SENSOR_PRIVACY = "android.permission.OBSERVE_SENSOR_PRIVACY";
    /** See {@code Manifest#REVIEW_ACCESSIBILITY_SERVICES} */
    public static final String REVIEW_ACCESSIBILITY_SERVICES =
            "android.permission.REVIEW_ACCESSIBILITY_SERVICES";
    /** See {@code Manifest#SUBSTITUTE_SHARE_TARGET_APP_NAME_AND_ICON} */
    public static final String SUBSTITUTE_SHARE_TARGET_APP_NAME_AND_ICON =
            "android.permission.SUBSTITUTE_SHARE_TARGET_APP_NAME_AND_ICON";
    /** See {@code Manifest#ACCESS_SHARED_LIBRARIES} */
    public static final String ACCESS_SHARED_LIBRARIES =
            "android.permission.ACCESS_SHARED_LIBRARIES";
    /** See {@code Manifest#LOG_COMPAT_CHANGE} */
    public static final String LOG_COMPAT_CHANGE = "android.permission.LOG_COMPAT_CHANGE";
    /** See {@code Manifest#READ_COMPAT_CHANGE_CONFIG} */
    public static final String READ_COMPAT_CHANGE_CONFIG =
            "android.permission.READ_COMPAT_CHANGE_CONFIG";
    /** See {@code Manifest#OVERRIDE_COMPAT_CHANGE_CONFIG} */
    public static final String OVERRIDE_COMPAT_CHANGE_CONFIG =
            "android.permission.OVERRIDE_COMPAT_CHANGE_CONFIG";
    /** See {@code Manifest#OVERRIDE_COMPAT_CHANGE_CONFIG_ON_RELEASE_BUILD} */
    public static final String OVERRIDE_COMPAT_CHANGE_CONFIG_ON_RELEASE_BUILD =
            "android.permission.OVERRIDE_COMPAT_CHANGE_CONFIG_ON_RELEASE_BUILD";
    /** See {@code Manifest#MONITOR_INPUT} */
    public static final String MONITOR_INPUT = "android.permission.MONITOR_INPUT";
    /** See {@code Manifest#ASSOCIATE_INPUT_DEVICE_TO_DISPLAY} */
    public static final String ASSOCIATE_INPUT_DEVICE_TO_DISPLAY =
            "android.permission.ASSOCIATE_INPUT_DEVICE_TO_DISPLAY";
    /** See {@code Manifest#QUERY_ALL_PACKAGES} */
    public static final String QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES";
    /** See {@code Manifest#PEEK_DROPBOX_DATA} */
    public static final String PEEK_DROPBOX_DATA = "android.permission.PEEK_DROPBOX_DATA";
    /** See {@code Manifest#ACCESS_TV_TUNER} */
    public static final String ACCESS_TV_TUNER = "android.permission.ACCESS_TV_TUNER";
    /** See {@code Manifest#ACCESS_TV_DESCRAMBLER} */
    public static final String ACCESS_TV_DESCRAMBLER = "android.permission.ACCESS_TV_DESCRAMBLER";
    /** See {@code Manifest#ACCESS_TV_SHARED_FILTER} */
    public static final String ACCESS_TV_SHARED_FILTER =
            "android.permission.ACCESS_TV_SHARED_FILTER";
    /** See {@code Manifest#ADD_TRUSTED_DISPLAY} */
    public static final String ADD_TRUSTED_DISPLAY = "android.permission.ADD_TRUSTED_DISPLAY";
    /** See {@code Manifest#ADD_ALWAYS_UNLOCKED_DISPLAY} */
    public static final String ADD_ALWAYS_UNLOCKED_DISPLAY =
            "android.permission.ADD_ALWAYS_UNLOCKED_DISPLAY";
    /** See {@code Manifest#ACCESS_LOCUS_ID_USAGE_STATS} */
    public static final String ACCESS_LOCUS_ID_USAGE_STATS =
            "android.permission.ACCESS_LOCUS_ID_USAGE_STATS";
    /** See {@code Manifest#MANAGE_APP_HIBERNATION} */
    public static final String MANAGE_APP_HIBERNATION = "android.permission.MANAGE_APP_HIBERNATION";
    /** See {@code Manifest#RESET_APP_ERRORS} */
    public static final String RESET_APP_ERRORS = "android.permission.RESET_APP_ERRORS";
    /** See {@code Manifest#INPUT_CONSUMER} */
    public static final String INPUT_CONSUMER = "android.permission.INPUT_CONSUMER";
    /** See {@code Manifest#CONTROL_DEVICE_STATE} */
    public static final String CONTROL_DEVICE_STATE = "android.permission.CONTROL_DEVICE_STATE";
    /** See {@code Manifest#BIND_DISPLAY_HASHING_SERVICE} */
    public static final String BIND_DISPLAY_HASHING_SERVICE =
            "android.permission.BIND_DISPLAY_HASHING_SERVICE";
    /** See {@code Manifest#MANAGE_TOAST_RATE_LIMITING} */
    public static final String MANAGE_TOAST_RATE_LIMITING =
            "android.permission.MANAGE_TOAST_RATE_LIMITING";
    /** See {@code Manifest#MANAGE_GAME_MODE} */
    public static final String MANAGE_GAME_MODE = "android.permission.MANAGE_GAME_MODE";
    /** See {@code Manifest#SIGNAL_REBOOT_READINESS} */
    public static final String SIGNAL_REBOOT_READINESS =
            "android.permission.SIGNAL_REBOOT_READINESS";
    /** See {@code Manifest#GET_PEOPLE_TILE_PREVIEW} */
    public static final String GET_PEOPLE_TILE_PREVIEW =
            "android.permission.GET_PEOPLE_TILE_PREVIEW";
    /** See {@code Manifest#READ_PEOPLE_DATA} */
    public static final String READ_PEOPLE_DATA = "android.permission.READ_PEOPLE_DATA";
    /** See {@code Manifest#RENOUNCE_PERMISSIONS} */
    public static final String RENOUNCE_PERMISSIONS = "android.permission.RENOUNCE_PERMISSIONS";
    /** See {@code Manifest#READ_NEARBY_STREAMING_POLICY} */
    public static final String READ_NEARBY_STREAMING_POLICY =
            "android.permission.READ_NEARBY_STREAMING_POLICY";
    /** See {@code Manifest#SET_CLIP_SOURCE} */
    public static final String SET_CLIP_SOURCE = "android.permission.SET_CLIP_SOURCE";
    /** See {@code Manifest#ACCESS_TUNED_INFO} */
    public static final String ACCESS_TUNED_INFO = "android.permission.ACCESS_TUNED_INFO";
    /** See {@code Manifest#UPDATE_PACKAGES_WITHOUT_USER_ACTION} */
    public static final String UPDATE_PACKAGES_WITHOUT_USER_ACTION =
            "android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION";
    /** See {@code Manifest#CAPTURE_BLACKOUT_CONTENT} */
    public static final String CAPTURE_BLACKOUT_CONTENT =
            "android.permission.CAPTURE_BLACKOUT_CONTENT";
    /** See {@code Manifest#READ_GLOBAL_APP_SEARCH_DATA} */
    public static final String READ_GLOBAL_APP_SEARCH_DATA =
            "android.permission.READ_GLOBAL_APP_SEARCH_DATA";
    /** See {@code Manifest#CREATE_VIRTUAL_DEVICE} */
    public static final String CREATE_VIRTUAL_DEVICE = "android.permission.CREATE_VIRTUAL_DEVICE";
    /** See {@code Manifest#SEND_SAFETY_CENTER_UPDATE} */
    public static final String SEND_SAFETY_CENTER_UPDATE =
            "android.permission.SEND_SAFETY_CENTER_UPDATE";
    /** See {@code Manifest#TRIGGER_LOST_MODE} */
    public static final String TRIGGER_LOST_MODE = "android.permission.TRIGGER_LOST_MODE";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_APP_EXEMPTIONS} */
    public static final String MANAGE_DEVICE_POLICY_APP_EXEMPTIONS = "android.permission.MANAGE_DEVICE_POLICY_APP_EXEMPTIONS";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_TIME} */
    public static final String MANAGE_DEVICE_POLICY_TIME = "android.permission.MANAGE_DEVICE_POLICY_TIME";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_RUNTIME_PERMISSIONS} */
    public static final String MANAGE_DEVICE_POLICY_RUNTIME_PERMISSIONS = "android.permission.MANAGE_DEVICE_POLICY_RUNTIME_PERMISSIONS";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_ORGANIZATION_IDENTITY} */public static final String MANAGE_DEVICE_POLICY_ORGANIZATION_IDENTITY =
            "android.permission.MANAGE_DEVICE_POLICY_ORGANIZATION_IDENTITY";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_SUPPORT_MESSAGE} */
    public static final String MANAGE_DEVICE_POLICY_SUPPORT_MESSAGE = "android.permission.MANAGE_DEVICE_POLICY_SUPPORT_MESSAGE";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_BACKUP_SERVICE} */
    public static final String MANAGE_DEVICE_POLICY_BACKUP_SERVICE = "android.permission.MANAGE_DEVICE_POLICY_BACKUP_SERVICE";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_LOCK_TASK} */
    public static final String MANAGE_DEVICE_POLICY_LOCK_TASK = "android.permission.MANAGE_DEVICE_POLICY_LOCK_TASK";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_APPS_CONTROL} */
    public static final String MANAGE_DEVICE_POLICY_APPS_CONTROL = "android.permission.MANAGE_DEVICE_POLICY_APPS_CONTROL";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_INSTALL_UNKNOWN_SOURCES} */public static final String MANAGE_DEVICE_POLICY_INSTALL_UNKNOWN_SOURCES =
            "android.permission.MANAGE_DEVICE_POLICY_INSTALL_UNKNOWN_SOURCES";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_APP_RESTRICTIONS} */
    public static final String MANAGE_DEVICE_POLICY_APP_RESTRICTIONS = "android.permission.MANAGE_DEVICE_POLICY_APP_RESTRICTIONS";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_CALLS} */
    public static final String MANAGE_DEVICE_POLICY_CALLS = "android.permission.MANAGE_DEVICE_POLICY_CALLS";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_DEBUGGING_FEATURES} */
    public static final String MANAGE_DEVICE_POLICY_DEBUGGING_FEATURES = "android.permission.MANAGE_DEVICE_POLICY_DEBUGGING_FEATURES";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_MODIFY_USERS} */
    public static final String MANAGE_DEVICE_POLICY_MODIFY_USERS = "android.permission.MANAGE_DEVICE_POLICY_MODIFY_USERS";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_SAFE_BOOT} */
    public static final String MANAGE_DEVICE_POLICY_SAFE_BOOT = "android.permission.MANAGE_DEVICE_POLICY_SAFE_BOOT";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_MICROPHONE} */
    public static final String MANAGE_DEVICE_POLICY_MICROPHONE = "android.permission.MANAGE_DEVICE_POLICY_MICROPHONE";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_CAMERA} */
    public static final String MANAGE_DEVICE_POLICY_CAMERA = "android.permission.MANAGE_DEVICE_POLICY_CAMERA";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_KEYGUARD} */
    public static final String MANAGE_DEVICE_POLICY_KEYGUARD = "android.permission.MANAGE_DEVICE_POLICY_KEYGUARD";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_ACCOUNT_MANAGEMENT} */
    public static final String MANAGE_DEVICE_POLICY_ACCOUNT_MANAGEMENT = "android.permission.MANAGE_DEVICE_POLICY_ACCOUNT_MANAGEMENT";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_PACKAGE_STATE} */
    public static final String MANAGE_DEVICE_POLICY_PACKAGE_STATE = "android.permission.MANAGE_DEVICE_POLICY_PACKAGE_STATE";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_RESET_PASSWORD} */
    public static final String MANAGE_DEVICE_POLICY_RESET_PASSWORD = "android.permission.MANAGE_DEVICE_POLICY_RESET_PASSWORD";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_STATUS_BAR} */
    public static final String MANAGE_DEVICE_POLICY_STATUS_BAR = "android.permission.MANAGE_DEVICE_POLICY_STATUS_BAR";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_BLUETOOTH} */
    public static final String MANAGE_DEVICE_POLICY_BLUETOOTH = "android.permission.MANAGE_DEVICE_POLICY_BLUETOOTH";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_FUN} */
    public static final String MANAGE_DEVICE_POLICY_FUN = "android.permission.MANAGE_DEVICE_POLICY_FUN";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_AIRPLANE_MODE} */
    public static final String MANAGE_DEVICE_POLICY_AIRPLANE_MODE = "android.permission.MANAGE_DEVICE_POLICY_AIRPLANE_MODE";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_MOBILE_NETWORK} */
    public static final String MANAGE_DEVICE_POLICY_MOBILE_NETWORK = "android.permission.MANAGE_DEVICE_POLICY_MOBILE_NETWORK";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_MANAGED_SUBSCRIPTIONS}*/
    public static final String MANAGE_DEVICE_POLICY_MANAGED_SUBSCRIPTIONS =
            "android.permission.MANAGE_DEVICE_POLICY_MANAGED_SUBSCRIPTIONS";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_PHYSICAL_MEDIA} */
    public static final String MANAGE_DEVICE_POLICY_PHYSICAL_MEDIA = "android.permission.MANAGE_DEVICE_POLICY_PHYSICAL_MEDIA";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_SMS} */
    public static final String MANAGE_DEVICE_POLICY_SMS = "android.permission.MANAGE_DEVICE_POLICY_SMS";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_USB_FILE_TRANSFER} */
    public static final String MANAGE_DEVICE_POLICY_USB_FILE_TRANSFER = "android.permission.MANAGE_DEVICE_POLICY_USB_FILE_TRANSFER";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_LOCK_CREDENTIALS} */
    public static final String MANAGE_DEVICE_POLICY_LOCK_CREDENTIALS = "android.permission.MANAGE_DEVICE_POLICY_LOCK_CREDENTIALS";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_WIFI} */
    public static final String MANAGE_DEVICE_POLICY_WIFI = "android.permission.MANAGE_DEVICE_POLICY_WIFI";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_SCREEN_CAPTURE} */
    public static final String MANAGE_DEVICE_POLICY_SCREEN_CAPTURE = "android.permission.MANAGE_DEVICE_POLICY_SCREEN_CAPTURE";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_INPUT_METHODS} */
    public static final String MANAGE_DEVICE_POLICY_INPUT_METHODS = "android.permission.MANAGE_DEVICE_POLICY_INPUT_METHODS";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_RESTRICT_PRIVATE_DNS} */
    public static final String MANAGE_DEVICE_POLICY_RESTRICT_PRIVATE_DNS = "android.permission.MANAGE_DEVICE_POLICY_RESTRICT_PRIVATE_DNS";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_DEFAULT_SMS} */
    public static final String MANAGE_DEVICE_POLICY_DEFAULT_SMS = "android.permission.MANAGE_DEVICE_POLICY_DEFAULT_SMS";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_PROFILES} */
    public static final String MANAGE_DEVICE_POLICY_PROFILES = "android.permission.MANAGE_DEVICE_POLICY_PROFILES";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_PROFILE_INTERACTION} */
    public static final String MANAGE_DEVICE_POLICY_PROFILE_INTERACTION = "android.permission.MANAGE_DEVICE_POLICY_PROFILE_INTERACTION";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_VPN} */
    public static final String MANAGE_DEVICE_POLICY_VPN = "android.permission.MANAGE_DEVICE_POLICY_VPN";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_AUDIO_OUTPUT} */
    public static final String MANAGE_DEVICE_POLICY_AUDIO_OUTPUT = "android.permission.MANAGE_DEVICE_POLICY_AUDIO_OUTPUT";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_DISPLAY} */
    public static final String MANAGE_DEVICE_POLICY_DISPLAY = "android.permission.MANAGE_DEVICE_POLICY_DISPLAY";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_LOCATION} */
    public static final String MANAGE_DEVICE_POLICY_LOCATION = "android.permission.MANAGE_DEVICE_POLICY_LOCATION";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_FACTORY_RESET} */
    public static final String MANAGE_DEVICE_POLICY_FACTORY_RESET = "android.permission.MANAGE_DEVICE_POLICY_FACTORY_RESET";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_WALLPAPER} */
    public static final String MANAGE_DEVICE_POLICY_WALLPAPER = "android.permission.MANAGE_DEVICE_POLICY_WALLPAPER";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_SCREEN_CONTENT} */
    public static final String MANAGE_DEVICE_POLICY_SCREEN_CONTENT = "android.permission.MANAGE_DEVICE_POLICY_SCREEN_CONTENT";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_SYSTEM_DIALOGS} */
    public static final String MANAGE_DEVICE_POLICY_SYSTEM_DIALOGS = "android.permission.MANAGE_DEVICE_POLICY_SYSTEM_DIALOGS";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_RUN_IN_BACKGROUND} */
    public static final String MANAGE_DEVICE_POLICY_RUN_IN_BACKGROUND = "android.permission.MANAGE_DEVICE_POLICY_RUN_IN_BACKGROUND";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_PRINTING} */
    public static final String MANAGE_DEVICE_POLICY_PRINTING = "android.permission.MANAGE_DEVICE_POLICY_PRINTING";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_NEARBY_COMMUNICATION} */
    public static final String MANAGE_DEVICE_POLICY_NEARBY_COMMUNICATION = "android.permission.MANAGE_DEVICE_POLICY_NEARBY_COMMUNICATION";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_WINDOWS} */
    public static final String MANAGE_DEVICE_POLICY_WINDOWS = "android.permission.MANAGE_DEVICE_POLICY_WINDOWS";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_LOCALE} */
    public static final String MANAGE_DEVICE_POLICY_LOCALE = "android.permission.MANAGE_DEVICE_POLICY_LOCALE";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_AUTOFILL} */
    public static final String MANAGE_DEVICE_POLICY_AUTOFILL = "android.permission.MANAGE_DEVICE_POLICY_AUTOFILL";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_USERS} */
    public static final String MANAGE_DEVICE_POLICY_USERS = "android.permission.MANAGE_DEVICE_POLICY_USERS";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_CERTIFICATES} */
    public static final String MANAGE_DEVICE_POLICY_CERTIFICATES = "android.permission.MANAGE_DEVICE_POLICY_CERTIFICATES";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_OVERRIDE_APN} */
    public static final String MANAGE_DEVICE_POLICY_OVERRIDE_APN = "android.permission.MANAGE_DEVICE_POLICY_OVERRIDE_APN";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_SECURITY_LOGGING} */
    public static final String MANAGE_DEVICE_POLICY_SECURITY_LOGGING = "android.permission.MANAGE_DEVICE_POLICY_SECURITY_LOGGING";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_AUDIT_LOGGING} */
    public static final String MANAGE_DEVICE_POLICY_AUDIT_LOGGING = "android.permission.MANAGE_DEVICE_POLICY_AUDIT_LOGGING";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_SYSTEM_UPDATES} */
    public static final String MANAGE_DEVICE_POLICY_SYSTEM_UPDATES = "android.permission.MANAGE_DEVICE_POLICY_SYSTEM_UPDATES";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_QUERY_SYSTEM_UPDATES} */
    public static final String MANAGE_DEVICE_POLICY_QUERY_SYSTEM_UPDATES = "android.permission.MANAGE_DEVICE_POLICY_QUERY_SYSTEM_UPDATES";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_PRIVATE_DNS} */
    public static final String MANAGE_DEVICE_POLICY_PRIVATE_DNS = "android.permission.MANAGE_DEVICE_POLICY_PRIVATE_DNS";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_SETTINGS} */
    public static final String MANAGE_DEVICE_POLICY_SETTINGS = "android.permission.MANAGE_DEVICE_POLICY_SETTINGS";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_NETWORK_LOGGING} */
    public static final String MANAGE_DEVICE_POLICY_NETWORK_LOGGING = "android.permission.MANAGE_DEVICE_POLICY_NETWORK_LOGGING";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_USB_DATA_SIGNALLING} */
    public static final String MANAGE_DEVICE_POLICY_USB_DATA_SIGNALLING = "android.permission.MANAGE_DEVICE_POLICY_USB_DATA_SIGNALLING";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_SUSPEND_PERSONAL_APPS} */public static final String MANAGE_DEVICE_POLICY_SUSPEND_PERSONAL_APPS =
            "android.permission.MANAGE_DEVICE_POLICY_SUSPEND_PERSONAL_APPS";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_KEEP_UNINSTALLED_PACKAGES} */public static final String MANAGE_DEVICE_POLICY_KEEP_UNINSTALLED_PACKAGES =
            "android.permission.MANAGE_DEVICE_POLICY_KEEP_UNINSTALLED_PACKAGES";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_ACCESSIBILITY} */
    public static final String MANAGE_DEVICE_POLICY_ACCESSIBILITY = "android.permission.MANAGE_DEVICE_POLICY_ACCESSIBILITY";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_COMMON_CRITERIA_MODE} */
    public static final String MANAGE_DEVICE_POLICY_COMMON_CRITERIA_MODE = "android.permission.MANAGE_DEVICE_POLICY_COMMON_CRITERIA_MODE";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_METERED_DATA} */
    public static final String MANAGE_DEVICE_POLICY_METERED_DATA = "android.permission.MANAGE_DEVICE_POLICY_METERED_DATA";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_PROXY} */
    public static final String MANAGE_DEVICE_POLICY_PROXY = "android.permission.MANAGE_DEVICE_POLICY_PROXY";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_BUGREPORT} */
    public static final String MANAGE_DEVICE_POLICY_BUGREPORT = "android.permission.MANAGE_DEVICE_POLICY_BUGREPORT";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_APP_USER_DATA} */
    public static final String MANAGE_DEVICE_POLICY_APP_USER_DATA = "android.permission.MANAGE_DEVICE_POLICY_APP_USER_DATA";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_LOCK} */
    public static final String MANAGE_DEVICE_POLICY_LOCK = "android.permission.MANAGE_DEVICE_POLICY_LOCK";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_THEFT_DETECTION} */
    public static final String MANAGE_DEVICE_POLICY_THEFT_DETECTION = "android.permission.MANAGE_DEVICE_POLICY_THEFT_DETECTION";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_SYSTEM_APPS} */
    public static final String MANAGE_DEVICE_POLICY_SYSTEM_APPS = "android.permission.MANAGE_DEVICE_POLICY_SYSTEM_APPS";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_WIPE_DATA} */
    public static final String MANAGE_DEVICE_POLICY_WIPE_DATA = "android.permission.MANAGE_DEVICE_POLICY_WIPE_DATA";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_MTE} */
    public static final String MANAGE_DEVICE_POLICY_MTE = "android.permission.MANAGE_DEVICE_POLICY_MTE";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_ACROSS_USERS_SECURITY_CRITICAL} */public static final String MANAGE_DEVICE_POLICY_ACROSS_USERS_SECURITY_CRITICAL =
            "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_SECURITY_CRITICAL";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_ACROSS_USERS} */
    public static final String MANAGE_DEVICE_POLICY_ACROSS_USERS = "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS";
    /** See {@link Manifest#MANAGE_DEVICE_POLICY_ACROSS_USERS_FULL} */
    public static final String MANAGE_DEVICE_POLICY_ACROSS_USERS_FULL = "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_FULL";

    public static final String MANAGE_DEFAULT_APPLICATIONS = "android.permission.MANAGE_DEFAULT_APPLICATIONS";
}
