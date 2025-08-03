/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.bedstead.nene.appops;

/** AppOp helper methods common to host an device. */
public class CommonAppOps {

    CommonAppOps() {

    }

    /** See {@code AppOpsManager#OPSTR_COARSE_LOCATION}. */
    public static final String OPSTR_COARSE_LOCATION = "android:coarse_location";
    /** See {@code AppOpsManager#OPSTR_FINE_LOCATION}. */
    public static final String OPSTR_FINE_LOCATION = "android:fine_location";
    /** See {@code AppOpsManager#OPSTR_MONITOR_LOCATION}. */
    public static final String OPSTR_MONITOR_LOCATION = "android:monitor_location";
    /** See {@code AppOpsManager#OPSTR_MONITOR_HIGH_POWER_LOCATION}. */
    public static final String OPSTR_MONITOR_HIGH_POWER_LOCATION =
            "android:monitor_location_high_power";
    /** See {@code AppOpsManager#OPSTR_GET_USAGE_STATS}. */
    public static final String OPSTR_GET_USAGE_STATS = "android:get_usage_stats";
    /** See {@code AppOpsManager#OPSTR_ACTIVATE_VPN}. */
    public static final String OPSTR_ACTIVATE_VPN = "android:activate_vpn";
    /** See {@code AppOpsManager#OPSTR_READ_CONTACTS}. */
    public static final String OPSTR_READ_CONTACTS = "android:read_contacts";
    /** See {@code AppOpsManager#OPSTR_WRITE_CONTACTS}. */
    public static final String OPSTR_WRITE_CONTACTS = "android:write_contacts";
    /** See {@code AppOpsManager#OPSTR_READ_CALL_LOG}. */
    public static final String OPSTR_READ_CALL_LOG = "android:read_call_log";
    /** See {@code AppOpsManager#OPSTR_WRITE_CALL_LOG}. */
    public static final String OPSTR_WRITE_CALL_LOG = "android:write_call_log";
    /** See {@code AppOpsManager#OPSTR_READ_CALENDAR}. */
    public static final String OPSTR_READ_CALENDAR = "android:read_calendar";
    /** See {@code AppOpsManager#OPSTR_WRITE_CALENDAR}. */
    public static final String OPSTR_WRITE_CALENDAR = "android:write_calendar";
    /** See {@code AppOpsManager#OPSTR_CALL_PHONE}. */
    public static final String OPSTR_CALL_PHONE = "android:call_phone";
    /** See {@code AppOpsManager#OPSTR_READ_SMS}. */
    public static final String OPSTR_READ_SMS = "android:read_sms";
    /** See {@code AppOpsManager#OPSTR_RECEIVE_SMS}. */
    public static final String OPSTR_RECEIVE_SMS = "android:receive_sms";
    /** See {@code AppOpsManager#OPSTR_RECEIVE_MMS}. */
    public static final String OPSTR_RECEIVE_MMS = "android:receive_mms";
    /** See {@code AppOpsManager#OPSTR_RECEIVE_WAP_PUSH}. */
    public static final String OPSTR_RECEIVE_WAP_PUSH = "android:receive_wap_push";
    /** See {@code AppOpsManager#OPSTR_SEND_SMS}. */
    public static final String OPSTR_SEND_SMS = "android:send_sms";
    /** See {@code AppOpsManager#OPSTR_CAMERA}. */
    public static final String OPSTR_CAMERA = "android:camera";
    /** See {@code AppOpsManager#OPSTR_RECORD_AUDIO}. */
    public static final String OPSTR_RECORD_AUDIO = "android:record_audio";
    /** See {@code AppOpsManager#OPSTR_READ_PHONE_STATE}. */
    public static final String OPSTR_READ_PHONE_STATE = "android:read_phone_state";
    /** See {@code AppOpsManager#OPSTR_ADD_VOICEMAIL}. */
    public static final String OPSTR_ADD_VOICEMAIL = "android:add_voicemail";
    /** See {@code AppOpsManager#OPSTR_USE_SIP}. */
    public static final String OPSTR_USE_SIP = "android:use_sip";
    /** See {@code AppOpsManager#OPSTR_PROCESS_OUTGOING_CALLS}. */
    public static final String OPSTR_PROCESS_OUTGOING_CALLS = "android:process_outgoing_calls";
    /** See {@code AppOpsManager#OPSTR_USE_FINGERPRINT}. */
    public static final String OPSTR_USE_FINGERPRINT = "android:use_fingerprint";
    /** See {@code AppOpsManager#OPSTR_BODY_SENSORS}. */
    public static final String OPSTR_BODY_SENSORS = "android:body_sensors";
    /** See {@code AppOpsManager#OPSTR_READ_CELL_BROADCASTS}. */
    public static final String OPSTR_READ_CELL_BROADCASTS = "android:read_cell_broadcasts";
    /** See {@code AppOpsManager#OPSTR_MOCK_LOCATION}. */
    public static final String OPSTR_MOCK_LOCATION = "android:mock_location";
    /** See {@code AppOpsManager#OPSTR_READ_EXTERNAL_STORAGE}. */
    public static final String OPSTR_READ_EXTERNAL_STORAGE = "android:read_external_storage";
    /** See {@code AppOpsManager#OPSTR_WRITE_EXTERNAL_STORAGE}. */
    public static final String OPSTR_WRITE_EXTERNAL_STORAGE = "android:write_external_storage";
    /** See {@code AppOpsManager#OPSTR_SYSTEM_ALERT_WINDOW}. */
    public static final String OPSTR_SYSTEM_ALERT_WINDOW = "android:system_alert_window";
    /** See {@code AppOpsManager#OPSTR_WRITE_SETTINGS}. */
    public static final String OPSTR_WRITE_SETTINGS = "android:write_settings";
    /** See {@code AppOpsManager#OPSTR_GET_ACCOUNTS}. */
    public static final String OPSTR_GET_ACCOUNTS = "android:get_accounts";
    /** See {@code AppOpsManager#OPSTR_READ_PHONE_NUMBERS}. */
    public static final String OPSTR_READ_PHONE_NUMBERS = "android:read_phone_numbers";
    /** See {@code AppOpsManager#OPSTR_PICTURE_IN_PICTURE}. */
    public static final String OPSTR_PICTURE_IN_PICTURE = "android:picture_in_picture";
    /** See {@code AppOpsManager#OPSTR_INSTANT_APP_START_FOREGROUND}. */
    public static final String OPSTR_INSTANT_APP_START_FOREGROUND =
            "android:instant_app_start_foreground";
    /** See {@code AppOpsManager#OPSTR_ANSWER_PHONE_CALLS}. */
    public static final String OPSTR_ANSWER_PHONE_CALLS = "android:answer_phone_calls";
    /** See {@code AppOpsManager#OPSTR_ACCEPT_HANDOVER}. */
    public static final String OPSTR_ACCEPT_HANDOVER = "android:accept_handover";
    /** See {@code AppOpsManager#OPSTR_GPS}. */
    public static final String OPSTR_GPS = "android:gps";
    /** See {@code AppOpsManager#OPSTR_VIBRATE}. */
    public static final String OPSTR_VIBRATE = "android:vibrate";
    /** See {@code AppOpsManager#OPSTR_WIFI_SCAN}. */
    public static final String OPSTR_WIFI_SCAN = "android:wifi_scan";
    /** See {@code AppOpsManager#OPSTR_POST_NOTIFICATION}. */
    public static final String OPSTR_POST_NOTIFICATION = "android:post_notification";
    /** See {@code AppOpsManager#OPSTR_NEIGHBORING_CELLS}. */
    public static final String OPSTR_NEIGHBORING_CELLS = "android:neighboring_cells";
    /** See {@code AppOpsManager#OPSTR_WRITE_SMS}. */
    public static final String OPSTR_WRITE_SMS = "android:write_sms";
    /** See {@code AppOpsManager#OPSTR_RECEIVE_EMERGENCY_BROADCAST}. */
    public static final String OPSTR_RECEIVE_EMERGENCY_BROADCAST =
            "android:receive_emergency_broadcast";
    /** See {@code AppOpsManager#OPSTR_READ_ICC_SMS}. */
    public static final String OPSTR_READ_ICC_SMS = "android:read_icc_sms";
    /** See {@code AppOpsManager#OPSTR_WRITE_ICC_SMS}. */
    public static final String OPSTR_WRITE_ICC_SMS = "android:write_icc_sms";
    /** See {@code AppOpsManager#OPSTR_ACCESS_NOTIFICATIONS}. */
    public static final String OPSTR_ACCESS_NOTIFICATIONS = "android:access_notifications";
    /** See {@code AppOpsManager#OPSTR_PLAY_AUDIO}. */
    public static final String OPSTR_PLAY_AUDIO = "android:play_audio";
    /** See {@code AppOpsManager#OPSTR_READ_CLIPBOARD}. */
    public static final String OPSTR_READ_CLIPBOARD = "android:read_clipboard";
    /** See {@code AppOpsManager#OPSTR_WRITE_CLIPBOARD}. */
    public static final String OPSTR_WRITE_CLIPBOARD = "android:write_clipboard";
    /** See {@code AppOpsManager#OPSTR_TAKE_MEDIA_BUTTONS}. */
    public static final String OPSTR_TAKE_MEDIA_BUTTONS = "android:take_media_buttons";
    /** See {@code AppOpsManager#OPSTR_TAKE_AUDIO_FOCUS}. */
    public static final String OPSTR_TAKE_AUDIO_FOCUS = "android:take_audio_focus";
    /** See {@code AppOpsManager#OPSTR_AUDIO_MASTER_VOLUME}. */
    public static final String OPSTR_AUDIO_MASTER_VOLUME = "android:audio_master_volume";
    /** See {@code AppOpsManager#OPSTR_AUDIO_VOICE_VOLUME}. */
    public static final String OPSTR_AUDIO_VOICE_VOLUME = "android:audio_voice_volume";
    /** See {@code AppOpsManager#OPSTR_AUDIO_RING_VOLUME}. */
    public static final String OPSTR_AUDIO_RING_VOLUME = "android:audio_ring_volume";
    /** See {@code AppOpsManager#OPSTR_AUDIO_MEDIA_VOLUME}. */
    public static final String OPSTR_AUDIO_MEDIA_VOLUME = "android:audio_media_volume";
    /** See {@code AppOpsManager#OPSTR_AUDIO_ALARM_VOLUME}. */
    public static final String OPSTR_AUDIO_ALARM_VOLUME = "android:audio_alarm_volume";
    /** See {@code AppOpsManager#OPSTR_AUDIO_NOTIFICATION_VOLUME}. */
    public static final String OPSTR_AUDIO_NOTIFICATION_VOLUME =
            "android:audio_notification_volume";
    /** See {@code AppOpsManager#OPSTR_AUDIO_BLUETOOTH_VOLUME}. */
    public static final String OPSTR_AUDIO_BLUETOOTH_VOLUME = "android:audio_bluetooth_volume";
    /** See {@code AppOpsManager#OPSTR_WAKE_LOCK}. */
    public static final String OPSTR_WAKE_LOCK = "android:wake_lock";
    /** See {@code AppOpsManager#OPSTR_MUTE_MICROPHONE}. */
    public static final String OPSTR_MUTE_MICROPHONE = "android:mute_microphone";
    /** See {@code AppOpsManager#OPSTR_TOAST_WINDOW}. */
    public static final String OPSTR_TOAST_WINDOW = "android:toast_window";
    /** See {@code AppOpsManager#OPSTR_PROJECT_MEDIA}. */
    public static final String OPSTR_PROJECT_MEDIA = "android:project_media";
    /** See {@code AppOpsManager#OPSTR_WRITE_WALLPAPER}. */
    public static final String OPSTR_WRITE_WALLPAPER = "android:write_wallpaper";
    /** See {@code AppOpsManager#OPSTR_ASSIST_STRUCTURE}. */
    public static final String OPSTR_ASSIST_STRUCTURE = "android:assist_structure";
    /** See {@code AppOpsManager#OPSTR_ASSIST_SCREENSHOT}. */
    public static final String OPSTR_ASSIST_SCREENSHOT = "android:assist_screenshot";
    /** See {@code AppOpsManager#OPSTR_TURN_SCREEN_ON}. */
    public static final String OPSTR_TURN_SCREEN_ON = "android:turn_screen_on";
    /** See {@code AppOpsManager#OPSTR_RUN_IN_BACKGROUND}. */
    public static final String OPSTR_RUN_IN_BACKGROUND = "android:run_in_background";
    /** See {@code AppOpsManager#OPSTR_AUDIO_ACCESSIBILITY_VOLUME}. */
    public static final String OPSTR_AUDIO_ACCESSIBILITY_VOLUME =
            "android:audio_accessibility_volume";
    /** See {@code AppOpsManager#OPSTR_REQUEST_INSTALL_PACKAGES}. */
    public static final String OPSTR_REQUEST_INSTALL_PACKAGES = "android:request_install_packages";
    /** See {@code AppOpsManager#OPSTR_RUN_ANY_IN_BACKGROUND}. */
    public static final String OPSTR_RUN_ANY_IN_BACKGROUND = "android:run_any_in_background";
    /** See {@code AppOpsManager#OPSTR_CHANGE_WIFI_STATE}. */
    public static final String OPSTR_CHANGE_WIFI_STATE = "android:change_wifi_state";
    /** See {@code AppOpsManager#OPSTR_REQUEST_DELETE_PACKAGES}. */
    public static final String OPSTR_REQUEST_DELETE_PACKAGES = "android:request_delete_packages";
    /** See {@code AppOpsManager#OPSTR_BIND_ACCESSIBILITY_SERVICE}. */
    public static final String OPSTR_BIND_ACCESSIBILITY_SERVICE =
            "android:bind_accessibility_service";
    /** See {@code AppOpsManager#OPSTR_MANAGE_IPSEC_TUNNELS}. */
    public static final String OPSTR_MANAGE_IPSEC_TUNNELS = "android:manage_ipsec_tunnels";
    /** See {@code AppOpsManager#OPSTR_START_FOREGROUND}. */
    public static final String OPSTR_START_FOREGROUND = "android:start_foreground";
    /** See {@code AppOpsManager#OPSTR_BLUETOOTH_SCAN}. */
    public static final String OPSTR_BLUETOOTH_SCAN = "android:bluetooth_scan";
    /** See {@code AppOpsManager#OPSTR_BLUETOOTH_CONNECT}. */
    public static final String OPSTR_BLUETOOTH_CONNECT = "android:bluetooth_connect";
    /** See {@code AppOpsManager#OPSTR_BLUETOOTH_ADVERTISE}. */
    public static final String OPSTR_BLUETOOTH_ADVERTISE = "android:bluetooth_advertise";
    /** See {@code AppOpsManager#OPSTR_USE_BIOMETRIC}. */
    public static final String OPSTR_USE_BIOMETRIC = "android:use_biometric";
    /** See {@code AppOpsManager#OPSTR_ACTIVITY_RECOGNITION}. */
    public static final String OPSTR_ACTIVITY_RECOGNITION = "android:activity_recognition";
    /** See {@code AppOpsManager#OPSTR_SMS_FINANCIAL_TRANSACTIONS}. */
    public static final String OPSTR_SMS_FINANCIAL_TRANSACTIONS =
            "android:sms_financial_transactions";
    /** See {@code AppOpsManager#OPSTR_READ_MEDIA_AUDIO}. */
    public static final String OPSTR_READ_MEDIA_AUDIO = "android:read_media_audio";
    /** See {@code AppOpsManager#OPSTR_WRITE_MEDIA_AUDIO}. */
    public static final String OPSTR_WRITE_MEDIA_AUDIO = "android:write_media_audio";
    /** See {@code AppOpsManager#OPSTR_READ_MEDIA_VIDEO}. */
    public static final String OPSTR_READ_MEDIA_VIDEO = "android:read_media_video";
    /** See {@code AppOpsManager#OPSTR_WRITE_MEDIA_VIDEO}. */
    public static final String OPSTR_WRITE_MEDIA_VIDEO = "android:write_media_video";
    /** See {@code AppOpsManager#OPSTR_READ_MEDIA_IMAGES}. */
    public static final String OPSTR_READ_MEDIA_IMAGES = "android:read_media_images";
    /** See {@code AppOpsManager#OPSTR_WRITE_MEDIA_IMAGES}. */
    public static final String OPSTR_WRITE_MEDIA_IMAGES = "android:write_media_images";
    /** See {@code AppOpsManager#OPSTR_LEGACY_STORAGE}. */
    public static final String OPSTR_LEGACY_STORAGE = "android:legacy_storage";
    /** See {@code AppOpsManager#OPSTR_ACCESS_MEDIA_LOCATION}. */
    public static final String OPSTR_ACCESS_MEDIA_LOCATION = "android:access_media_location";
    /** See {@code AppOpsManager#OPSTR_ACCESS_ACCESSIBILITY}. */
    public static final String OPSTR_ACCESS_ACCESSIBILITY = "android:access_accessibility";
    /** See {@code AppOpsManager#OPSTR_READ_DEVICE_IDENTIFIERS}. */
    public static final String OPSTR_READ_DEVICE_IDENTIFIERS = "android:read_device_identifiers";
    /** See {@code AppOpsManager#OPSTR_QUERY_ALL_PACKAGES}. */
    public static final String OPSTR_QUERY_ALL_PACKAGES = "android:query_all_packages";
    /** See {@code AppOpsManager#OPSTR_MANAGE_EXTERNAL_STORAGE}. */
    public static final String OPSTR_MANAGE_EXTERNAL_STORAGE = "android:manage_external_storage";
    /** See {@code AppOpsManager#OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED}. */
    public static final String OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED =
            "android:auto_revoke_permissions_if_unused";
    /** See {@code AppOpsManager#OPSTR_AUTO_REVOKE_MANAGED_BY_INSTALLER}. */
    public static final String OPSTR_AUTO_REVOKE_MANAGED_BY_INSTALLER =
            "android:auto_revoke_managed_by_installer";
    /** See {@code AppOpsManager#OPSTR_INTERACT_ACROSS_PROFILES}. */
    public static final String OPSTR_INTERACT_ACROSS_PROFILES = "android:interact_across_profiles";
    /** See {@code AppOpsManager#OPSTR_ACTIVATE_PLATFORM_VPN}. */
    public static final String OPSTR_ACTIVATE_PLATFORM_VPN = "android:activate_platform_vpn";
    /** See {@code AppOpsManager#OPSTR_LOADER_USAGE_STATS}. */
    public static final String OPSTR_LOADER_USAGE_STATS = "android:loader_usage_stats";
    /** See {@code AppOpsManager#OPSTR_MANAGE_ONGOING_CALLS}. */
    public static final String OPSTR_MANAGE_ONGOING_CALLS = "android:manage_ongoing_calls";
    /** See {@code AppOpsManager#OPSTR_NO_ISOLATED_STORAGE}. */
    public static final String OPSTR_NO_ISOLATED_STORAGE = "android:no_isolated_storage";
    /** See {@code AppOpsManager#OPSTR_PHONE_CALL_MICROPHONE}. */
    public static final String OPSTR_PHONE_CALL_MICROPHONE = "android:phone_call_microphone";
    /** See {@code AppOpsManager#OPSTR_PHONE_CALL_CAMERA}. */
    public static final String OPSTR_PHONE_CALL_CAMERA = "android:phone_call_camera";
    /** See {@code AppOpsManager#OPSTR_RECORD_AUDIO_HOTWORD}. */
    public static final String OPSTR_RECORD_AUDIO_HOTWORD = "android:record_audio_hotword";
    /** See {@code AppOpsManager#OPSTR_MANAGE_CREDENTIALS}. */
    public static final String OPSTR_MANAGE_CREDENTIALS = "android:manage_credentials";
    /** See {@code AppOpsManager#OPSTR_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER}. */
    public static final String OPSTR_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER =
            "android:use_icc_auth_with_device_identifier";
    /** See {@code AppOpsManager#OPSTR_RECORD_AUDIO_OUTPUT}. */
    public static final String OPSTR_RECORD_AUDIO_OUTPUT = "android:record_audio_output";
    /** See {@code AppOpsManager#OPSTR_SCHEDULE_EXACT_ALARM}. */
    public static final String OPSTR_SCHEDULE_EXACT_ALARM = "android:schedule_exact_alarm";
    /** See {@code AppOpsManager#OPSTR_FINE_LOCATION_SOURCE}. */
    public static final String OPSTR_FINE_LOCATION_SOURCE = "android:fine_location_source";
    /** See {@code AppOpsManager#OPSTR_COARSE_LOCATION_SOURCE}. */
    public static final String OPSTR_COARSE_LOCATION_SOURCE = "android:coarse_location_source";
    /** See {@code AppOpsManager#OPSTR_MANAGE_MEDIA}. */
    public static final String OPSTR_MANAGE_MEDIA = "android:manage_media";
    /** See {@code AppOpsManager#OPSTR_UWB_RANGING}. */
    public static final String OPSTR_UWB_RANGING = "android:uwb_ranging";
    /** See {@code AppOpsManager#OPSTR_NEARBY_WIFI_DEVICES}. */
    public static final String OPSTR_NEARBY_WIFI_DEVICES = "android:nearby_wifi_devices";
    /** See {@code AppOpsManager#OPSTR_ACTIVITY_RECOGNITION_SOURCE}. */
    public static final String OPSTR_ACTIVITY_RECOGNITION_SOURCE =
            "android:activity_recognition_source";
    /** See {@code AppOpsManager#OPSTR_RECORD_INCOMING_PHONE_AUDIO}. */
    public static final String OPSTR_RECORD_INCOMING_PHONE_AUDIO =
            "android:record_incoming_phone_audio";
    /** See {@code AppOpsManager#OPSTR_ESTABLISH_VPN_SERVICE}. */
    public static final String OPSTR_ESTABLISH_VPN_SERVICE = "android:establish_vpn_service";
    /** See {@code AppOpsManager#OPSTR_ESTABLISH_VPN_MANAGER}. */
    public static final String OPSTR_ESTABLISH_VPN_MANAGER = "android:establish_vpn_manager";
    /** See {@code AppOpsManager#OPSTR_ACCESS_RESTRICTED_SETTINGS}. */
    public static final String OPSTR_ACCESS_RESTRICTED_SETTINGS =
            "android:access_restricted_settings";
    /** See {@code AppOpsManager#OPSTR_SYSTEM_EXEMPT_FROM_SUSPENSION}. */
    public static final String OPSTR_SYSTEM_EXEMPT_FROM_SUSPENSION =
            "android:system_exempt_from_suspension";
    /** See {@code AppOpsManager#OPSTR_SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS}. */
    public static final String OPSTR_SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS =
            "android:system_exempt_from_power_restrictions";
}
