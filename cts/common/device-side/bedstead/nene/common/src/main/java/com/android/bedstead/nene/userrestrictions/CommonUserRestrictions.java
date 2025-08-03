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

package com.android.bedstead.nene.userrestrictions;

/** User restrictions helper methods common to host and device. */
public final class CommonUserRestrictions {

    private CommonUserRestrictions() {}

    /** See {@code android.os.UserManager#DISALLOW_CONFIG_WIFI} */
    public static final String DISALLOW_CONFIG_WIFI = "no_config_wifi";

    /** See {@code android.os.UserManager#DISALLOW_CONFIG_LOCALE} */
    public static final String DISALLOW_CONFIG_LOCALE = "no_config_locale";

    /** See {@code android.os.UserManager#DISALLOW_MODIFY_ACCOUNTS} */
    public static final String DISALLOW_MODIFY_ACCOUNTS = "no_modify_accounts";

    /** See {@code android.os.UserManager#DISALLOW_INSTALL_APPS} */
    public static final String DISALLOW_INSTALL_APPS = "no_install_apps";

    /** See {@code android.os.UserManager#DISALLOW_UNINSTALL_APPS} */
    public static final String DISALLOW_UNINSTALL_APPS = "no_uninstall_apps";

    /** See {@code android.os.UserManager#DISALLOW_SHARE_LOCATION} */
    public static final String DISALLOW_SHARE_LOCATION = "no_share_location";

    /** See {@code android.os.UserManager#DISALLOW_INSTALL_UNKNOWN_SOURCES} */
    public static final String DISALLOW_INSTALL_UNKNOWN_SOURCES = "no_install_unknown_sources";

    /** See {@code android.os.UserManager#DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY} */
    public static final String DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY =
            "no_install_unknown_sources_globally";

    /** See {@code android.os.UserManager#DISALLOW_CONFIG_BLUETOOTH} */
    public static final String DISALLOW_CONFIG_BLUETOOTH = "no_config_bluetooth";

    /** See {@code android.os.UserManager#DISALLOW_BLUETOOTH} */
    public static final String DISALLOW_BLUETOOTH = "no_bluetooth";

    /** See {@code android.os.UserManager#DISALLOW_BLUETOOTH_SHARING} */
    public static final String DISALLOW_BLUETOOTH_SHARING = "no_bluetooth_sharing";

    /** See {@code android.os.UserManager#DISALLOW_USB_FILE_TRANSFER} */
    public static final String DISALLOW_USB_FILE_TRANSFER = "no_usb_file_transfer";

    /** See {@code android.os.UserManager#DISALLOW_CONFIG_CREDENTIALS} */
    public static final String DISALLOW_CONFIG_CREDENTIALS = "no_config_credentials";

    /** See {@code android.os.UserManager#DISALLOW_REMOVE_USER} */
    public static final String DISALLOW_REMOVE_USER = "no_remove_user";

    /** See {@code android.os.UserManager#DISALLOW_REMOVE_MANAGED_PROFILE} */
    public static final String DISALLOW_REMOVE_MANAGED_PROFILE = "no_remove_managed_profile";

    /** See {@code android.os.UserManager#DISALLOW_DEBUGGING_FEATURES} */
    public static final String DISALLOW_DEBUGGING_FEATURES = "no_debugging_features";

    /** See {@code android.os.UserManager#DISALLOW_CONFIG_VPN} */
    public static final String DISALLOW_CONFIG_VPN = "no_config_vpn";

    /** See {@code android.os.UserManager#DISALLOW_CONFIG_DATE_TIME} */
    public static final String DISALLOW_CONFIG_DATE_TIME = "no_config_date_time";

    /** See {@code android.os.UserManager#DISALLOW_CONFIG_TETHERING} */
    public static final String DISALLOW_CONFIG_TETHERING = "no_config_tethering";

    /** See {@code android.os.UserManager#DISALLOW_NETWORK_RESET} */
    public static final String DISALLOW_NETWORK_RESET = "no_network_reset";

    /** See {@code android.os.UserManager#DISALLOW_FACTORY_RESET} */
    public static final String DISALLOW_FACTORY_RESET = "no_factory_reset";

    /** See {@code android.os.UserManager#DISALLOW_ADD_USER} */
    public static final String DISALLOW_ADD_USER = "no_add_user";

    /** See {@code android.os.UserManager#DISALLOW_ADD_MANAGED_PROFILE} */
    public static final String DISALLOW_ADD_MANAGED_PROFILE = "no_add_managed_profile";

    /** See {@code android.os.UserManager#DISALLOW_ADD_CLONE_PROFILE} */
    public static final String DISALLOW_ADD_CLONE_PROFILE = "no_add_clone_profile";

    /** See {@code android.os.UserManager#DISALLOW_ADD_PRIVATE_PROFILE} */
    public static final String DISALLOW_ADD_PRIVATE_PROFILE = "no_add_private_profile";

    /** See {@code android.os.UserManager#ENSURE_VERIFY_APPS} */
    public static final String ENSURE_VERIFY_APPS = "ensure_verify_apps";

    /** See {@code android.os.UserManager#DISALLOW_CONFIG_CELL_BROADCASTS} */
    public static final String DISALLOW_CONFIG_CELL_BROADCASTS = "no_config_cell_broadcasts";

    /** See {@code android.os.UserManager#DISALLOW_CONFIG_MOBILE_NETWORKS} */
    public static final String DISALLOW_CONFIG_MOBILE_NETWORKS = "no_config_mobile_networks";

    /** See {@code android.os.UserManager#DISALLOW_APPS_CONTROL} */
    public static final String DISALLOW_APPS_CONTROL = "no_control_apps";

    /** See {@code android.os.UserManager#DISALLOW_MOUNT_PHYSICAL_MEDIA} */
    public static final String DISALLOW_MOUNT_PHYSICAL_MEDIA = "no_physical_media";

    /** See {@code android.os.UserManager#DISALLOW_UNMUTE_MICROPHONE} */
    public static final String DISALLOW_UNMUTE_MICROPHONE = "no_unmute_microphone";

    /** See {@code android.os.UserManager#DISALLOW_ADJUST_VOLUME} */
    public static final String DISALLOW_ADJUST_VOLUME = "no_adjust_volume";

    /** See {@code android.os.UserManager#DISALLOW_OUTGOING_CALLS} */
    public static final String DISALLOW_OUTGOING_CALLS = "no_outgoing_calls";

    /** See {@code android.os.UserManager#DISALLOW_SMS} */
    public static final String DISALLOW_SMS = "no_sms";

    /** See {@code android.os.UserManager#DISALLOW_FUN} */
    public static final String DISALLOW_FUN = "no_fun";

    /** See {@code android.os.UserManager#DISALLOW_CREATE_WINDOWS} */
    public static final String DISALLOW_CREATE_WINDOWS = "no_create_windows";

    /** See {@code android.os.UserManager#DISALLOW_SYSTEM_ERROR_DIALOGS} */
    public static final String DISALLOW_SYSTEM_ERROR_DIALOGS = "no_system_error_dialogs";

    /** See {@code android.os.UserManager#DISALLOW_CROSS_PROFILE_COPY_PASTE} */
    public static final String DISALLOW_CROSS_PROFILE_COPY_PASTE = "no_cross_profile_copy_paste";

    /** See {@code android.os.UserManager#DISALLOW_OUTGOING_BEAM} */
    public static final String DISALLOW_OUTGOING_BEAM = "no_outgoing_beam";

    /** See {@code android.os.UserManager#DISALLOW_WALLPAPER} */
    public static final String DISALLOW_WALLPAPER = "no_wallpaper";

    /** See {@code android.os.UserManager#DISALLOW_SET_WALLPAPER} */
    public static final String DISALLOW_SET_WALLPAPER = "no_set_wallpaper";

    /** See {@code android.os.UserManager#DISALLOW_SAFE_BOOT} */
    public static final String DISALLOW_SAFE_BOOT = "no_safe_boot";

    /** See {@code android.os.UserManager#DISALLOW_RECORD_AUDIO} */
    public static final String DISALLOW_RECORD_AUDIO = "no_record_audio";

    /** See {@code android.os.UserManager#DISALLOW_RUN_IN_BACKGROUND} */
    public static final String DISALLOW_RUN_IN_BACKGROUND = "no_run_in_background";

    /** See {@code android.os.UserManager#DISALLOW_CAMERA} */
    public static final String DISALLOW_CAMERA = "no_camera";

    /** See {@code android.os.UserManager#DISALLOW_UNMUTE_DEVICE} */
    public static final String DISALLOW_UNMUTE_DEVICE = "disallow_unmute_device";

    /** See {@code android.os.UserManager#DISALLOW_DATA_ROAMING} */
    public static final String DISALLOW_DATA_ROAMING = "no_data_roaming";

    /** See {@code android.os.UserManager#DISALLOW_SET_USER_ICON} */
    public static final String DISALLOW_SET_USER_ICON = "no_set_user_icon";

    /** See {@code android.os.UserManager#DISALLOW_OEM_UNLOCK} */
    public static final String DISALLOW_OEM_UNLOCK = "no_oem_unlock";

    /** See {@code android.os.UserManager#DISALLOW_UNIFIED_PASSWORD} */
    public static final String DISALLOW_UNIFIED_PASSWORD = "no_unified_password";

    /** See {@code android.os.UserManager#ALLOW_PARENT_PROFILE_APP_LINKING} */
    public static final String ALLOW_PARENT_PROFILE_APP_LINKING =
            "allow_parent_profile_app_linking";

    /** See {@code android.os.UserManager#DISALLOW_AUTOFILL} */
    public static final String DISALLOW_AUTOFILL = "no_autofill";

    /** See {@code android.os.UserManager#DISALLOW_CONTENT_CAPTURE} */
    public static final String DISALLOW_CONTENT_CAPTURE = "no_content_capture";

    /** See {@code android.os.UserManager#DISALLOW_CONTENT_SUGGESTIONS} */
    public static final String DISALLOW_CONTENT_SUGGESTIONS = "no_content_suggestions";

    /** See {@code android.os.UserManager#DISALLOW_USER_SWITCH} */
    public static final String DISALLOW_USER_SWITCH = "no_user_switch";

    /** See {@code android.os.UserManager#DISALLOW_SHARE_INTO_MANAGED_PROFILE} */
    public static final String DISALLOW_SHARE_INTO_MANAGED_PROFILE = "no_sharing_into_profile";

    /** See {@code android.os.UserManager#DISALLOW_PRINTING} */
    public static final String DISALLOW_PRINTING = "no_printing";

    /** See {@code android.os.UserManager#DISALLOW_CONFIG_PRIVATE_DNS} */
    public static final String DISALLOW_CONFIG_PRIVATE_DNS =
            "disallow_config_private_dns";

    /** See {@code android.os.UserManager#DISALLOW_MICROPHONE_TOGGLE} */
    public static final String DISALLOW_MICROPHONE_TOGGLE =
            "disallow_microphone_toggle";

    /** See {@code android.os.UserManager#DISALLOW_CAMERA_TOGGLE} */
    public static final String DISALLOW_CAMERA_TOGGLE =
            "disallow_camera_toggle";

    /** See {@code android.os.UserManager#DISALLOW_BIOMETRIC} */
    public static final String DISALLOW_BIOMETRIC = "disallow_biometric";

    /** See {@code android.os.UserManager#DISALLOW_CHANGE_WIFI_STATE} */
    public static final String DISALLOW_CHANGE_WIFI_STATE = "no_change_wifi_state";

    /** See {@code android.os.UserManager#DISALLOW_WIFI_TETHERING} */
    public static final String DISALLOW_WIFI_TETHERING = "no_wifi_tethering";

    /** See {@code android.os.UserManager#DISALLOW_SHARING_ADMIN_CONFIGURED_WIFI} */
    public static final String DISALLOW_SHARING_ADMIN_CONFIGURED_WIFI =
            "no_sharing_admin_configured_wifi";

    /** See {@code android.os.UserManager#DISALLOW_WIFI_DIRECT} */
    public static final String DISALLOW_WIFI_DIRECT = "no_wifi_direct";

    /** See {@code android.os.UserManager#DISALLOW_ADD_WIFI_CONFIG} */
    public static final String DISALLOW_ADD_WIFI_CONFIG = "no_add_wifi_config";

    /** See {@code android.os.UserManager#DISALLOW_CONFIG_LOCATION} */
    public static final String DISALLOW_CONFIG_LOCATION = "no_config_location";

    /** See {@code android.os.UserManager#DISALLOW_AIRPLANE_MODE} */
    public static final String DISALLOW_AIRPLANE_MODE = "no_airplane_mode";

    /** See {@code android.os.UserManager#DISALLOW_CONFIG_BRIGHTNESS} */
    public static final String DISALLOW_CONFIG_BRIGHTNESS = "no_config_brightness";

    /** See {@code android.os.UserManager#DISALLOW_AMBIENT_DISPLAY} */
    public static final String DISALLOW_AMBIENT_DISPLAY = "no_ambient_display";

    /** See {@code android.os.UserManager#DISALLOW_CONFIG_SCREEN_TIMEOUT} */
    public static final String DISALLOW_CONFIG_SCREEN_TIMEOUT = "no_config_screen_timeout";

    /** See {@code android.os.UserManager#DISALLOW_CELLULAR_2G} */
    public static final String DISALLOW_CELLULAR_2G = "no_cellular_2g";

    /** See {@code android.os.UserManager#DISALLOW_ULTRA_WIDEBAND_RADIO} */
    public static final String DISALLOW_ULTRA_WIDEBAND_RADIO = "no_ultra_wideband_radio";

    /** See {@code android.os.UserManager#DISALLOW_CONFIG_DEFAULT_APPS} */
    public static final String DISALLOW_CONFIG_DEFAULT_APPS = "disallow_config_default_apps";

    /** See {@code android.os.UserManager#DISALLOW_SIM_GLOBALLY} */
    public static final String DISALLOW_SIM_GLOBALLY = "no_sim_globally";

    /** See {@code Manifest#ACTION_USER_RESTRICTIONS_CHANGED} */
    public static final String ACTION_USER_RESTRICTIONS_CHANGED =
            "android.os.action.USER_RESTRICTIONS_CHANGED";

    /** Array of all user restrictions*/
    public static final String[] ALL_USER_RESTRICTIONS = new String[] {
            DISALLOW_CONFIG_WIFI,
            DISALLOW_CONFIG_LOCALE,
            DISALLOW_MODIFY_ACCOUNTS,
            DISALLOW_INSTALL_APPS,
            DISALLOW_UNINSTALL_APPS,
            DISALLOW_SHARE_LOCATION,
            DISALLOW_INSTALL_UNKNOWN_SOURCES,
            DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY,
            DISALLOW_CONFIG_BLUETOOTH,
            DISALLOW_BLUETOOTH,
            DISALLOW_BLUETOOTH_SHARING,
            DISALLOW_USB_FILE_TRANSFER,
            DISALLOW_CONFIG_CREDENTIALS,
            DISALLOW_REMOVE_USER,
            DISALLOW_REMOVE_MANAGED_PROFILE,
            DISALLOW_DEBUGGING_FEATURES,
            DISALLOW_CONFIG_VPN,
            DISALLOW_CONFIG_DATE_TIME,
            DISALLOW_CONFIG_TETHERING,
            DISALLOW_NETWORK_RESET,
            DISALLOW_FACTORY_RESET,
            DISALLOW_ADD_USER,
            DISALLOW_ADD_MANAGED_PROFILE,
            DISALLOW_ADD_CLONE_PROFILE,
            DISALLOW_ADD_PRIVATE_PROFILE,
            ENSURE_VERIFY_APPS,
            DISALLOW_CONFIG_CELL_BROADCASTS,
            DISALLOW_CONFIG_MOBILE_NETWORKS,
            DISALLOW_APPS_CONTROL,
            DISALLOW_MOUNT_PHYSICAL_MEDIA,
            DISALLOW_UNMUTE_MICROPHONE,
            DISALLOW_ADJUST_VOLUME,
            DISALLOW_OUTGOING_CALLS,
            DISALLOW_SMS,
            DISALLOW_FUN,
            DISALLOW_CREATE_WINDOWS,
            DISALLOW_SYSTEM_ERROR_DIALOGS,
            DISALLOW_CROSS_PROFILE_COPY_PASTE,
            DISALLOW_OUTGOING_BEAM,
            DISALLOW_WALLPAPER,
            DISALLOW_SAFE_BOOT,
            ALLOW_PARENT_PROFILE_APP_LINKING,
            DISALLOW_RECORD_AUDIO,
            DISALLOW_CAMERA,
            DISALLOW_RUN_IN_BACKGROUND,
            DISALLOW_DATA_ROAMING,
            DISALLOW_SET_USER_ICON,
            DISALLOW_SET_WALLPAPER,
            DISALLOW_OEM_UNLOCK,
            DISALLOW_UNMUTE_DEVICE,
            DISALLOW_AUTOFILL,
            DISALLOW_CONTENT_CAPTURE,
            DISALLOW_CONTENT_SUGGESTIONS,
            DISALLOW_USER_SWITCH,
            DISALLOW_UNIFIED_PASSWORD,
            DISALLOW_CONFIG_LOCATION,
            DISALLOW_AIRPLANE_MODE,
            DISALLOW_CONFIG_BRIGHTNESS,
            DISALLOW_SHARE_INTO_MANAGED_PROFILE,
            DISALLOW_AMBIENT_DISPLAY,
            DISALLOW_CONFIG_SCREEN_TIMEOUT,
            DISALLOW_PRINTING,
            DISALLOW_CONFIG_PRIVATE_DNS,
            DISALLOW_MICROPHONE_TOGGLE,
            DISALLOW_CAMERA_TOGGLE,
            DISALLOW_CHANGE_WIFI_STATE,
            DISALLOW_WIFI_TETHERING,
            DISALLOW_SHARING_ADMIN_CONFIGURED_WIFI,
            DISALLOW_WIFI_DIRECT,
            DISALLOW_ADD_WIFI_CONFIG,
            DISALLOW_CELLULAR_2G,
            DISALLOW_ULTRA_WIDEBAND_RADIO,
            DISALLOW_CONFIG_DEFAULT_APPS
    };
}
