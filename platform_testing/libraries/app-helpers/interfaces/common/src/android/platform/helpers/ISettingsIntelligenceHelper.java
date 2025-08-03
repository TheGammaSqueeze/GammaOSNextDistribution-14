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

package android.platform.helpers;

/** Extends for Settings Intelligence */
public interface ISettingsIntelligenceHelper extends IAppHelper {

    /**
     * DATA_URI_STRING* can be appended after PAGE_ACTION_*. It can be opened by "adb shell am start
     * -a <PAGE_ACTION_*> -d <DATA_URI_STRING_*>". For example, the following command can open "App
     * info" page: adb shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d
     * package:com.android.settings
     */
    public static final String DATA_URI_STRING_APP_INFO = "package:com.android.settings";

    public static final String PAGE_ACTION_HOME = "";
    public static final String PAGE_ACTION_ABOUT_PHONE = "android.settings.DEVICE_INFO_SETTINGS";
    public static final String PAGE_ACTION_ACCESSIBILITY =
            "android.settings.ACCESSIBILITY_SETTINGS";
    public static final String PAGE_ACTION_ACCOUNT = "android.settings.SYNC_SETTINGS";
    public static final String PAGE_ACTION_APPLICATION = "android.settings.APPLICATION_SETTINGS";
    public static final String PAGE_ACTION_APP_INFO =
            "android.settings.APPLICATION_DETAILS_SETTINGS";
    public static final String PAGE_ACTION_APP_NOTIFICATIONS =
            "android.settings.NOTIFICATION_SETTINGS";
    public static final String PAGE_ACTION_AUTO_ROTATE_SCREEN =
            "android.settings.AUTO_ROTATE_SETTINGS";
    public static final String PAGE_ACTION_BATTERY = "android.intent.action.POWER_USAGE_SUMMARY";
    public static final String PAGE_ACTION_BLUETOOTH = "android.settings.BLUETOOTH_SETTINGS";
    public static final String PAGE_ACTION_CHOOSE_SCREEN_LOCK = "android.settings.BIOMETRIC_ENROLL";
    public static final String PAGE_ACTION_CONNECTED_CAST = "android.settings.CAST_SETTINGS";
    public static final String PAGE_ACTION_DARK_THEME = "android.settings.DARK_THEME_SETTINGS";
    public static final String PAGE_ACTION_DATA_SAVER = "android.settings.DATA_SAVER_SETTINGS";
    public static final String PAGE_ACTION_DATE = "android.settings.DATE_SETTINGS";
    public static final String PAGE_ACTION_DIGITAL_WELLBEING =
            "com.google.android.apps.wellbeing/.settings.TopLevelSettingsActivity";
    public static final String PAGE_ACTION_DISPLAY = "android.settings.DISPLAY_SETTINGS";
    public static final String PAGE_ACTION_DO_NOT_DISTURB = "android.settings.ZEN_MODE_SETTINGS";
    public static final String PAGE_ACTION_HOTSPOT_AND_TETHERING =
            "android.settings.TETHER_SETTINGS";
    public static final String PAGE_ACTION_LOCATION = "android.settings.LOCATION_SOURCE_SETTINGS";
    public static final String PAGE_ACTION_MULTIPLE_USERS = "android.settings.USER_SETTINGS";
    public static final String PAGE_ACTION_NETWORK_INTERNET = "android.settings.WIRELESS_SETTINGS";
    public static final String PAGE_ACTION_NIGHT_LIGHT = "android.settings.NIGHT_DISPLAY_SETTINGS";
    public static final String PAGE_ACTION_PRIVACY = "android.settings.PRIVACY_SETTINGS";
    public static final String PAGE_ACTION_SEARCH = "android.settings.APP_SEARCH_SETTINGS";
    public static final String PAGE_ACTION_SECURITY = "android.settings.SECURITY_SETTINGS";
    public static final String PAGE_ACTION_SOUND = "android.settings.SOUND_SETTINGS";
    public static final String PAGE_ACTION_SOUND_RINGTONE = "android.intent.action.RINGTONE_PICKER";
    public static final String PAGE_ACTION_STORAGE = "android.settings.INTERNAL_STORAGE_SETTINGS";
    public static final String PAGE_ACTION_SYSTEM_UPDATE =
            "com.google.android.gms/.update.SystemUpdateActivity";
    public static final String PAGE_ACTION_WIFI = "android.settings.WIFI_SETTINGS";
    public static final String PAGE_ACTION_WIFI_TETHERING_HOTSPOT =
            "com.android.settings.WIFI_TETHER_SETTINGS";

    String PAGE_ACTION_BLUETOOTH_PAIRING = "android.settings.BLUETOOTH_PAIRING_SETTINGS";
    String PAGE_ACTION_CONNECTED_ADVANCED =
            "com.android.settings.ADVANCED_CONNECTED_DEVICE_SETTINGS";
    String PAGE_ACTION_DISPLAY_LOCKSCRN = "android.settings.LOCK_SCREEN_SETTINGS";
    String PAGE_ACTION_DISPLAY_TEXT = "android.settings.TEXT_READING_SETTINGS";
    String PAGE_ACTION_LOCKSCRN_PLAYING = "com.google.intelligence.sense.NOW_PLAYING_SETTINGS";
    String PAGE_ACTION_NETWORK_INSTALL_CERT = "android.credentials.INSTALL";
    String PAGE_ACTION_NETWORK_VPN = "android.settings.VPN_SETTINGS";
    String PAGE_ACTION_SOUND_MEDIA = "android.settings.ACTION_MEDIA_CONTROLS_SETTINGS";
    String PAGE_ACTION_WIFI_IP = "android.settings.WIFI_IP_SETTINGS";
    String PAGE_ACTION_WIFI_SAVED = "android.settings.WIFI_SAVED_NETWORK_SETTINGS";

    /**
     * Sets the intent data uri representing the Settings page to open when open() is called.
     *
     * @param dataUriString One of the DATA_URI_STRING* constants.
     */
    void setDataUri(String dataUriString);

    /**
     * Sets the action representing the Settings page to open when open() is called.
     *
     * @param pageAction One of the PAGE_ACTION* constants.
     */
    void setPageAction(String pageAction);

    /**
     * Setup expectations: Settings search page is open
     *
     * This method performs a text query and expects to see "no result" image.
     */
    void performQuery();

    /**
     * Setup expectations: Settings homepage is open
     *
     * <p>This method opens search page.
     */
    void openSearch();

    /** This method opens Settings homepage */
    void goToHome();
}
