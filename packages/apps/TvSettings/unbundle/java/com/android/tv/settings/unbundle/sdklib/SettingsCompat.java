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

import android.content.ContentResolver;
import android.provider.Settings;

public class SettingsCompat {
    public static String ACTION_SYSTEM_UPDATE_SETTINGS = Settings.ACTION_SYSTEM_UPDATE_SETTINGS;

    public static String ENABLED_ACCESSIBILITY_AUDIO_DESCRIPTION_BY_DEFAULT =
            Settings.Secure.ENABLED_ACCESSIBILITY_AUDIO_DESCRIPTION_BY_DEFAULT;

    public static String ACCESSIBILITY_HIGH_TEXT_CONTRAST_ENABLED =
            Settings.Secure.ACCESSIBILITY_HIGH_TEXT_CONTRAST_ENABLED;

    public static String getStringForUser(ContentResolver resolver, String name, int userHandle) {
        return Settings.Secure.getStringForUser(resolver, name, userHandle);
    }

    public static boolean putStringForUser(
            ContentResolver resolver, String name, String value, int userHandle) {
        return Settings.Secure.putStringForUser(resolver, name, value, userHandle);
    }

    public static String ATTENTIVE_TIMEOUT = Settings.Secure.ATTENTIVE_TIMEOUT;
    public static String SLEEP_TIMEOUT = Settings.Secure.SLEEP_TIMEOUT;
    public static String SCREENSAVER_ACTIVATE_ON_DOCK =
            Settings.Secure.SCREENSAVER_ACTIVATE_ON_DOCK;
    public static String SCREENSAVER_ACTIVATE_ON_SLEEP =
            Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP;
    public static String SCREENSAVER_ENABLED = Settings.Secure.SCREENSAVER_ENABLED;
    public static String WIFI_SCAN_ALWAYS_AVAILABLE = Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE;
    public static final int MATCH_CONTENT_FRAMERATE_ALWAYS =
            Settings.Secure.MATCH_CONTENT_FRAMERATE_ALWAYS;
    public static String MATCH_CONTENT_FRAME_RATE = Settings.Secure.MATCH_CONTENT_FRAME_RATE;
    public static final int MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY =
            Settings.Secure.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY;
    public static final int MATCH_CONTENT_FRAMERATE_NEVER =
            Settings.Secure.MATCH_CONTENT_FRAMERATE_NEVER;
    public static String MINIMAL_POST_PROCESSING_ALLOWED =
            Settings.Secure.MINIMAL_POST_PROCESSING_ALLOWED;
    public static final String ENCODED_SURROUND_OUTPUT = Settings.Global.ENCODED_SURROUND_OUTPUT;
    public static final int ENCODED_SURROUND_OUTPUT_AUTO =
            Settings.Global.ENCODED_SURROUND_OUTPUT_AUTO;
    public static final int ENCODED_SURROUND_OUTPUT_MANUAL =
            Settings.Global.ENCODED_SURROUND_OUTPUT_MANUAL;
    public static final int ENCODED_SURROUND_OUTPUT_NEVER =
            Settings.Global.ENCODED_SURROUND_OUTPUT_NEVER;
    public static String DISABLED_SYSTEM_INPUT_METHODS =
            Settings.Secure.DISABLED_SYSTEM_INPUT_METHODS;
    public static int LOCATION_MODE_ON = Settings.Secure.LOCATION_MODE_ON;
    public static int LOCATION_MODE_OFF = Settings.Secure.LOCATION_MODE_OFF;
    public static final String ACCESSIBILITY_CAPTIONING_TYPEFACE =
            "accessibility_captioning_typeface";
    public static final String ACCESSIBILITY_DISPLAY_DALTONIZER_ENABLED =
            "accessibility_display_daltonizer_enabled";
    public static String ACTION_ENTERPRISE_PRIVACY_SETTINGS =
            Settings.ACTION_ENTERPRISE_PRIVACY_SETTINGS;
    public static String ENABLED_NOTIFICATION_LISTENERS =
            Settings.Secure.ENABLED_NOTIFICATION_LISTENERS;

    public static final String ACCESSIBILITY_CAPTIONING_FONT_SCALE =
            "accessibility_captioning_font_scale";
    public static final String ACCESSIBILITY_CAPTIONING_LOCALE = "accessibility_captioning_locale";
    public static final String ACCESSIBILITY_CAPTIONING_PRESET = "accessibility_captioning_preset";
    public static final String ACCESSIBILITY_CAPTIONING_ENABLED =
            "accessibility_captioning_enabled";
    public static String KEEP_PROFILE_IN_BACKGROUND = Settings.Global.KEEP_PROFILE_IN_BACKGROUND;
    public static final String ENCODED_SURROUND_OUTPUT_ENABLED_FORMATS =
            Settings.Global.ENCODED_SURROUND_OUTPUT_ENABLED_FORMATS;
    public static String AUTOFILL_SERVICE = Settings.Secure.AUTOFILL_SERVICE;

    public static final String ACCESSIBILITY_SHORTCUT_TARGET_SERVICE =
            "accessibility_shortcut_target_service";

    public static final String PACKAGE_VERIFIER_SETTING_VISIBLE = "verifier_setting_visible";

    public static final String ACCESSIBILITY_CAPTIONING_FOREGROUND_COLOR =
            "accessibility_captioning_foreground_color";

    public static final String ACCESSIBILITY_CAPTIONING_EDGE_TYPE =
            "accessibility_captioning_edge_type";

    public static int getIntForUser(ContentResolver cr, String name, int def, int userHandle) {
        return Settings.System.getIntForUser(cr, name, def, userHandle);
    }

    public static final String ACCESSIBILITY_CAPTIONING_EDGE_COLOR =
            "accessibility_captioning_edge_color";

    public static final String ACCESSIBILITY_CAPTIONING_BACKGROUND_COLOR =
            "accessibility_captioning_background_color";

    public static final String ACCESSIBILITY_CAPTIONING_WINDOW_COLOR =
            "accessibility_captioning_window_color";

    public static final String DEBUG_VIEW_ATTRIBUTES = "debug_view_attributes";

    public static final String FORCE_ALLOW_ON_EXTERNAL = "force_allow_on_external";

    public static final String ANR_SHOW_BACKGROUND = "anr_show_background";

    public static final String OVERLAY_DISPLAY_DEVICES = "overlay_display_devices";

    public static final String WIFI_DISPLAY_CERTIFICATION_ON = "wifi_display_certification_on";

    public static final String DISABLE_WINDOW_BLURS = "disable_window_blurs";

    public static final String MOBILE_DATA_ALWAYS_ON = "mobile_data_always_on";

    public static final String POINTER_LOCATION = "pointer_location";

    public static final String SHOW_TOUCHES = "show_touches";

    public static final String PACKAGE_VERIFIER_INCLUDE_ADB = "verifier_verify_adb_installs";

    public static final String ACCESSIBILITY_DISPLAY_DALTONIZER =
            "accessibility_display_daltonizer";

    public static final String USB_AUDIO_AUTOMATIC_ROUTING_DISABLED =
            "usb_audio_automatic_routing_disabled";

    public static final String DEVELOPMENT_FORCE_RESIZABLE_ACTIVITIES =
            "force_resizable_activities";

    public static final String DEVELOPMENT_FORCE_RTL = "debug.force_rtl";

    public static final String ACTION_DATA_SAVER_SETTINGS = "android.settings.DATA_SAVER_SETTINGS";
}
