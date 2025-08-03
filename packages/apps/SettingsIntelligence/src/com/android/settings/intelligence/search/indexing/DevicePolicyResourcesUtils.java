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

package com.android.settings.intelligence.search.indexing;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;

import java.util.HashMap;
import java.util.Map;

// TODO(b/232188488): Remove once we switch to using FRROs.
public class DevicePolicyResourcesUtils {

    public static String DEVICE_POLICY_RESOURCES_VERSION_KEY = "DEVICE_POLICY_RESOURCES_VERSION";

    private static Map<String, String> DEVICE_POLICY_RESOURCES = new HashMap<>();

    static {
        DEVICE_POLICY_RESOURCES.put("security_settings_face_profile_preference_title",
                "Settings.FACE_SETTINGS_FOR_WORK_TITLE");
        DEVICE_POLICY_RESOURCES.put("fingerprint_last_delete_message_profile_challenge",
                "Settings.WORK_PROFILE_FINGERPRINT_LAST_DELETE_MESSAGE");
        DEVICE_POLICY_RESOURCES.put("lock_settings_picker_admin_restricted_personal_message",
                "Settings.WORK_PROFILE_IT_ADMIN_CANT_RESET_SCREEN_LOCK");
        DEVICE_POLICY_RESOURCES.put("lock_settings_picker_profile_message",
                "Settings.WORK_PROFILE_SCREEN_LOCK_SETUP_MESSAGE");
        DEVICE_POLICY_RESOURCES.put("unlock_set_unlock_launch_picker_title_profile",
                "Settings.WORK_PROFILE_SET_UNLOCK_LAUNCH_PICKER_TITLE");
        DEVICE_POLICY_RESOURCES.put("lock_last_pattern_attempt_before_wipe_profile",
                "Settings.WORK_PROFILE_LAST_PATTERN_ATTEMPT_BEFORE_WIPE");
        DEVICE_POLICY_RESOURCES.put("lock_last_pin_attempt_before_wipe_profile",
                "Settings.WORK_PROFILE_LAST_PIN_ATTEMPT_BEFORE_WIPE");
        DEVICE_POLICY_RESOURCES.put("lock_last_password_attempt_before_wipe_profile",
                "Settings.WORK_PROFILE_LAST_PASSWORD_ATTEMPT_BEFORE_WIPE");
        DEVICE_POLICY_RESOURCES.put("lock_failed_attempts_now_wiping_profile",
                "Settings.WORK_PROFILE_LOCK_ATTEMPTS_FAILED");
        DEVICE_POLICY_RESOURCES.put("accessibility_category_work",
                "Settings.ACCESSIBILITY_CATEGORY_WORK");
        DEVICE_POLICY_RESOURCES.put("accessibility_category_personal",
                "Settings.ACCESSIBILITY_CATEGORY_PERSONAL");
        DEVICE_POLICY_RESOURCES.put("accessibility_work_account_title",
                "Settings.ACCESSIBILITY_WORK_ACCOUNT_TITLE");
        DEVICE_POLICY_RESOURCES.put("accessibility_personal_account_title",
                "Settings.ACCESSIBILITY_PERSONAL_ACCOUNT_TITLE");
        DEVICE_POLICY_RESOURCES.put("managed_profile_location_switch_title",
                "Settings.WORK_PROFILE_LOCATION_SWITCH_TITLE");
        DEVICE_POLICY_RESOURCES.put("lockpassword_choose_your_profile_password_header",
                "Settings.SET_WORK_PROFILE_PASSWORD_HEADER");
        DEVICE_POLICY_RESOURCES.put("lockpassword_choose_your_profile_pin_header",
                "Settings.SET_WORK_PROFILE_PIN_HEADER");
        DEVICE_POLICY_RESOURCES.put("lockpassword_choose_your_profile_pattern_header",
                "Settings.SET_WORK_PROFILE_PATTERN_HEADER");
        DEVICE_POLICY_RESOURCES.put("lockpassword_reenter_your_profile_password_header",
                "Settings.REENTER_WORK_PROFILE_PASSWORD_HEADER");
        DEVICE_POLICY_RESOURCES.put("lockpassword_confirm_your_work_password_header",
                "Settings.CONFIRM_WORK_PROFILE_PASSWORD_HEADER");
        DEVICE_POLICY_RESOURCES.put("lockpassword_confirm_your_work_pattern_header",
                "Settings.CONFIRM_WORK_PROFILE_PATTERN_HEADER");
        DEVICE_POLICY_RESOURCES.put("lockpassword_confirm_your_work_pin_header",
                "Settings.CONFIRM_WORK_PROFILE_PIN_HEADER");
        DEVICE_POLICY_RESOURCES.put("lockpassword_reenter_your_profile_pin_header",
                "Settings.REENTER_WORK_PROFILE_PIN_HEADER");
        DEVICE_POLICY_RESOURCES.put("lockpassword_strong_auth_required_work_pattern",
                "Settings.WORK_PROFILE_PATTERN_REQUIRED");
        DEVICE_POLICY_RESOURCES.put("lockpassword_confirm_your_pattern_generic_profile",
                "Settings.WORK_PROFILE_CONFIRM_PATTERN");
        DEVICE_POLICY_RESOURCES.put("lockpassword_strong_auth_required_work_pin",
                "Settings.WORK_PROFILE_PIN_REQUIRED");
        DEVICE_POLICY_RESOURCES.put("lockpassword_strong_auth_required_work_password",
                "Settings.WORK_PROFILE_PASSWORD_REQUIRED");
        DEVICE_POLICY_RESOURCES.put("lock_settings_profile_title",
                "Settings.WORK_PROFILE_SECURITY_TITLE");
        DEVICE_POLICY_RESOURCES.put("lock_settings_profile_screen_lock_title",
                "Settings.WORK_PROFILE_UNIFY_LOCKS_TITLE");
        DEVICE_POLICY_RESOURCES.put("lock_settings_profile_unification_summary",
                "Settings.WORK_PROFILE_UNIFY_LOCKS_SUMMARY");
        DEVICE_POLICY_RESOURCES.put("lock_settings_profile_unification_dialog_body",
                "Settings.WORK_PROFILE_UNIFY_LOCKS_DETAIL");
        DEVICE_POLICY_RESOURCES.put("lock_settings_profile_unification_dialog_uncompliant_body",
                "Settings.WORK_PROFILE_UNIFY_LOCKS_NONCOMPLIANT");
        DEVICE_POLICY_RESOURCES.put("language_and_input_for_work_category_title",
                "Settings.WORK_PROFILE_KEYBOARDS_AND_TOOLS");
        DEVICE_POLICY_RESOURCES.put("managed_profile_not_available_label",
                "Settings.WORK_PROFILE_NOT_AVAILABLE");
        DEVICE_POLICY_RESOURCES.put("work_mode_label", "Settings.WORK_PROFILE_SETTING");
        DEVICE_POLICY_RESOURCES.put("work_mode_on_summary",
                "Settings.WORK_PROFILE_SETTING_ON_SUMMARY");
        DEVICE_POLICY_RESOURCES.put("work_mode_off_summary",
                "Settings.WORK_PROFILE_SETTING_OFF_SUMMARY");
        DEVICE_POLICY_RESOURCES.put("remove_managed_profile_label", "Settings.REMOVE_WORK_PROFILE");
        DEVICE_POLICY_RESOURCES.put("ssl_ca_cert_info_message_device_owner",
                "Settings.DEVICE_OWNER_INSTALLED_CERTIFICATE_AUTHORITY_WARNING");
        DEVICE_POLICY_RESOURCES.put("ssl_ca_cert_info_message",
                "Settings.WORK_PROFILE_INSTALLED_CERTIFICATE_AUTHORITY_WARNING");
        DEVICE_POLICY_RESOURCES.put("work_profile_confirm_remove_title",
                "Settings.WORK_PROFILE_CONFIRM_REMOVE_TITLE");
        DEVICE_POLICY_RESOURCES.put("work_profile_confirm_remove_message",
                "Settings.WORK_PROFILE_CONFIRM_REMOVE_MESSAGE");
        DEVICE_POLICY_RESOURCES.put("notification_settings_work_profile",
                "Settings.WORK_APPS_CANNOT_ACCESS_NOTIFICATION_SETTINGS");
        DEVICE_POLICY_RESOURCES.put("sound_work_settings",
                "Settings.WORK_PROFILE_SOUND_SETTINGS_SECTION_HEADER");
        DEVICE_POLICY_RESOURCES.put("work_use_personal_sounds_title",
                "Settings.WORK_PROFILE_USE_PERSONAL_SOUNDS_TITLE");
        DEVICE_POLICY_RESOURCES.put("work_use_personal_sounds_summary",
                "Settings.WORK_PROFILE_USE_PERSONAL_SOUNDS_SUMMARY");
        DEVICE_POLICY_RESOURCES.put("work_ringtone_title", "Settings.WORK_PROFILE_RINGTONE_TITLE");
        DEVICE_POLICY_RESOURCES.put("work_notification_ringtone_title",
                "Settings.WORK_PROFILE_NOTIFICATION_RINGTONE_TITLE");
        DEVICE_POLICY_RESOURCES.put("work_alarm_ringtone_title",
                "Settings.WORK_PROFILE_ALARM_RINGTONE_TITLE");
        DEVICE_POLICY_RESOURCES.put("work_sound_same_as_personal",
                "Settings.WORK_PROFILE_SYNC_WITH_PERSONAL_SOUNDS_ACTIVE_SUMMARY");
        DEVICE_POLICY_RESOURCES.put("work_sync_dialog_title",
                "Settings.ENABLE_WORK_PROFILE_SYNC_WITH_PERSONAL_SOUNDS_DIALOG_TITLE");
        DEVICE_POLICY_RESOURCES.put("work_sync_dialog_message",
                "Settings.ENABLE_WORK_PROFILE_SYNC_WITH_PERSONAL_SOUNDS_DIALOG_MESSAGE");
        DEVICE_POLICY_RESOURCES.put("profile_section_header",
                "Settings.WORK_PROFILE_NOTIFICATIONS_SECTION_HEADER");
        DEVICE_POLICY_RESOURCES.put("locked_work_profile_notification_title",
                "Settings.WORK_PROFILE_LOCKED_NOTIFICATION_TITLE");
        DEVICE_POLICY_RESOURCES.put("lock_screen_notifs_redact_work",
                "Settings.WORK_PROFILE_LOCK_SCREEN_REDACT_NOTIFICATION_TITLE");
        DEVICE_POLICY_RESOURCES.put("lock_screen_notifs_redact_work_summary",
                "Settings.WORK_PROFILE_LOCK_SCREEN_REDACT_NOTIFICATION_SUMMARY");
        DEVICE_POLICY_RESOURCES.put("work_profile_notification_access_blocked_summary",
                "Settings.WORK_PROFILE_NOTIFICATION_LISTENER_BLOCKED");
        DEVICE_POLICY_RESOURCES.put("interact_across_profiles_title",
                "Settings.CONNECTED_WORK_AND_PERSONAL_APPS_TITLE");
        DEVICE_POLICY_RESOURCES.put("interact_across_profiles_summary_1",
                "Settings.CONNECTED_APPS_SHARE_PERMISSIONS_AND_DATA");
        DEVICE_POLICY_RESOURCES.put("interact_across_profiles_summary_2",
                "Settings.ONLY_CONNECT_TRUSTED_APPS");
        DEVICE_POLICY_RESOURCES.put("interact_across_profiles_summary_3",
                "Settings.HOW_TO_DISCONNECT_APPS");
        DEVICE_POLICY_RESOURCES.put("interact_across_profiles_consent_dialog_title",
                "Settings.CONNECT_APPS_DIALOG_TITLE");
        DEVICE_POLICY_RESOURCES.put("interact_across_profiles_consent_dialog_summary",
                "Settings.CONNECT_APPS_DIALOG_SUMMARY");
        DEVICE_POLICY_RESOURCES.put("interact_across_profiles_consent_dialog_app_data_summary",
                "Settings.APP_CAN_ACCESS_PERSONAL_DATA");
        DEVICE_POLICY_RESOURCES.put("interact_across_profiles_consent_dialog_permissions_summary",
                "Settings.APP_CAN_ACCESS_PERSONAL_PERMISSIONS");
        DEVICE_POLICY_RESOURCES.put("interact_across_profiles_install_work_app_title",
                "Settings.INSTALL_IN_WORK_PROFILE_TO_CONNECT_PROMPT");
        DEVICE_POLICY_RESOURCES.put("interact_across_profiles_install_personal_app_title",
                "Settings.INSTALL_IN_PERSONAL_PROFILE_TO_CONNECT_PROMPT");
        DEVICE_POLICY_RESOURCES.put("opening_paragraph_delete_profile_unknown_company",
                "Settings.WORK_PROFILE_MANAGED_BY");
        DEVICE_POLICY_RESOURCES.put("managing_admin", "Settings.MANAGED_BY");
        DEVICE_POLICY_RESOURCES.put("work_profile_usage_access_warning",
                "Settings.WORK_PROFILE_DISABLE_USAGE_ACCESS_WARNING");
        DEVICE_POLICY_RESOURCES.put("disabled_by_policy_title",
                "Settings.DISABLED_BY_IT_ADMIN_TITLE");
        DEVICE_POLICY_RESOURCES.put("default_admin_support_msg", "Settings.CONTACT_YOUR_IT_ADMIN");
        DEVICE_POLICY_RESOURCES.put("admin_profile_owner_message",
                "Settings.WORK_PROFILE_ADMIN_POLICIES_WARNING");
        DEVICE_POLICY_RESOURCES.put("admin_profile_owner_user_message",
                "Settings.USER_ADMIN_POLICIES_WARNING");
        DEVICE_POLICY_RESOURCES.put("admin_device_owner_message",
                "Settings.DEVICE_ADMIN_POLICIES_WARNING");
        DEVICE_POLICY_RESOURCES.put("condition_work_title",
                "Settings.WORK_PROFILE_OFF_CONDITION_TITLE");
        DEVICE_POLICY_RESOURCES.put("managed_profile_settings_title",
                "Settings.MANAGED_PROFILE_SETTINGS_TITLE");
        DEVICE_POLICY_RESOURCES.put("managed_profile_contact_search_title",
                "Settings.WORK_PROFILE_CONTACT_SEARCH_TITLE");
        DEVICE_POLICY_RESOURCES.put("managed_profile_contact_search_summary",
                "Settings.WORK_PROFILE_CONTACT_SEARCH_SUMMARY");
        DEVICE_POLICY_RESOURCES.put("cross_profile_calendar_title",
                "Settings.CROSS_PROFILE_CALENDAR_TITLE");
        DEVICE_POLICY_RESOURCES.put("cross_profile_calendar_summary",
                "Settings.CROSS_PROFILE_CALENDAR_SUMMARY");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_always_on_vpn_personal",
                "Settings.ALWAYS_ON_VPN_PERSONAL_PROFILE");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_always_on_vpn_device",
                "Settings.ALWAYS_ON_VPN_DEVICE");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_always_on_vpn_work",
                "Settings.ALWAYS_ON_VPN_WORK_PROFILE");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_ca_certs_personal",
                "Settings.CA_CERTS_PERSONAL_PROFILE");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_ca_certs_work",
                "Settings.CA_CERTS_WORK_PROFILE");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_ca_certs_device",
                "Settings.CA_CERTS_DEVICE");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_lock_device",
                "Settings.ADMIN_CAN_LOCK_DEVICE");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_wipe_device",
                "Settings.ADMIN_CAN_WIPE_DEVICE");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_failed_password_wipe_device",
                "Settings.ADMIN_CONFIGURED_FAILED_PASSWORD_WIPE_DEVICE");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_failed_password_wipe_work",
                "Settings.ADMIN_CONFIGURED_FAILED_PASSWORD_WIPE_WORK_PROFILE");
        DEVICE_POLICY_RESOURCES.put("do_disclosure_generic",
                "Settings.DEVICE_MANAGED_WITHOUT_NAME");
        DEVICE_POLICY_RESOURCES.put("do_disclosure_with_name", "Settings.DEVICE_MANAGED_WITH_NAME");
        DEVICE_POLICY_RESOURCES.put("work_profile_app_subtext",
                "Settings.WORK_PROFILE_APP_SUBTEXT");
        DEVICE_POLICY_RESOURCES.put("personal_profile_app_subtext",
                "Settings.PERSONAL_PROFILE_APP_SUBTEXT");
        DEVICE_POLICY_RESOURCES.put("security_settings_work_fingerprint_preference_title",
                "Settings.FINGERPRINT_FOR_WORK");
        DEVICE_POLICY_RESOURCES.put(
                "security_settings_face_enroll_introduction_message_unlock_disabled",
                "Settings.FACE_UNLOCK_DISABLED");
        DEVICE_POLICY_RESOURCES.put(
                "security_settings_fingerprint_enroll_introduction_message_unlock_disabled",
                "Settings.FINGERPRINT_UNLOCK_DISABLED");
        DEVICE_POLICY_RESOURCES.put("security_fingerprint_disclaimer_lockscreen_disabled_1",
                "Settings.FINGERPRINT_UNLOCK_DISABLED_EXPLANATION");
        DEVICE_POLICY_RESOURCES.put("lockpassword_pin_recently_used", "Settings.PIN_RECENTLY_USED");
        DEVICE_POLICY_RESOURCES.put("lockpassword_password_recently_used",
                "Settings.PASSWORD_RECENTLY_USED");
        DEVICE_POLICY_RESOURCES.put("manage_device_admin", "Settings.MANAGE_DEVICE_ADMIN_APPS");
        DEVICE_POLICY_RESOURCES.put("number_of_device_admins_none",
                "Settings.NUMBER_OF_DEVICE_ADMINS_NONE");
        DEVICE_POLICY_RESOURCES.put("number_of_device_admins", "Settings.NUMBER_OF_DEVICE_ADMINS");
        DEVICE_POLICY_RESOURCES.put("forgot_password_title", "Settings.FORGOT_PASSWORD_TITLE");
        DEVICE_POLICY_RESOURCES.put("forgot_password_text", "Settings.FORGOT_PASSWORD_TEXT");
        DEVICE_POLICY_RESOURCES.put("move_error_device_admin", "Settings.ERROR_MOVE_DEVICE_ADMIN");
        DEVICE_POLICY_RESOURCES.put("device_admin_settings_title",
                "Settings.DEVICE_ADMIN_SETTINGS_TITLE");
        DEVICE_POLICY_RESOURCES.put("remove_device_admin", "Settings.REMOVE_DEVICE_ADMIN");
        DEVICE_POLICY_RESOURCES.put("uninstall_device_admin", "Settings.UNINSTALL_DEVICE_ADMIN");
        DEVICE_POLICY_RESOURCES.put("remove_and_uninstall_device_admin",
                "Settings.REMOVE_AND_UNINSTALL_DEVICE_ADMIN");
        DEVICE_POLICY_RESOURCES.put("select_device_admin_msg", "Settings.SELECT_DEVICE_ADMIN_APPS");
        DEVICE_POLICY_RESOURCES.put("no_device_admins", "Settings.NO_DEVICE_ADMINS");
        DEVICE_POLICY_RESOURCES.put("add_device_admin_msg", "Settings.ACTIVATE_DEVICE_ADMIN_APP");
        DEVICE_POLICY_RESOURCES.put("add_device_admin", "Settings.ACTIVATE_THIS_DEVICE_ADMIN_APP");
        DEVICE_POLICY_RESOURCES.put("device_admin_add_title",
                "Settings.ACTIVATE_DEVICE_ADMIN_APP_TITLE");
        DEVICE_POLICY_RESOURCES.put("device_admin_warning", "Settings.NEW_DEVICE_ADMIN_WARNING");
        DEVICE_POLICY_RESOURCES.put("device_admin_warning_simplified",
                "Settings.NEW_DEVICE_ADMIN_WARNING_SIMPLIFIED");
        DEVICE_POLICY_RESOURCES.put("device_admin_status", "Settings.ACTIVE_DEVICE_ADMIN_WARNING");
        DEVICE_POLICY_RESOURCES.put("profile_owner_add_title", "Settings.SET_PROFILE_OWNER_TITLE");
        DEVICE_POLICY_RESOURCES.put("profile_owner_add_title_simplified",
                "Settings.SET_PROFILE_OWNER_DIALOG_TITLE");
        DEVICE_POLICY_RESOURCES.put("adding_profile_owner_warning",
                "Settings.SET_PROFILE_OWNER_POSTSETUP_WARNING");
        DEVICE_POLICY_RESOURCES.put("admin_disabled_other_options",
                "Settings.OTHER_OPTIONS_DISABLED_BY_ADMIN");
        DEVICE_POLICY_RESOURCES.put("remove_account_failed",
                "Settings.REMOVE_ACCOUNT_FAILED_ADMIN_RESTRICTION");
        DEVICE_POLICY_RESOURCES.put("help_url_action_disabled_by_it_admin",
                "Settings.IT_ADMIN_POLICY_DISABLING_INFO_URL");
        DEVICE_POLICY_RESOURCES.put("share_remote_bugreport_dialog_title",
                "Settings.SHARE_REMOTE_BUGREPORT_DIALOG_TITLE");
        DEVICE_POLICY_RESOURCES.put("share_remote_bugreport_dialog_message_finished",
                "Settings.SHARE_REMOTE_BUGREPORT_FINISHED_REQUEST_CONSENT");
        DEVICE_POLICY_RESOURCES.put("share_remote_bugreport_dialog_message",
                "Settings.SHARE_REMOTE_BUGREPORT_NOT_FINISHED_REQUEST_CONSENT");
        DEVICE_POLICY_RESOURCES.put("sharing_remote_bugreport_dialog_message",
                "Settings.SHARING_REMOTE_BUGREPORT_MESSAGE");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_settings", "Settings.MANAGED_DEVICE_INFO");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_settings_summary_generic",
                "Settings.MANAGED_DEVICE_INFO_SUMMARY");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_settings_summary_with_name",
                "Settings.MANAGED_DEVICE_INFO_SUMMARY_WITH_NAME");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_header",
                "Settings.ENTERPRISE_PRIVACY_HEADER");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_exposure_category",
                "Settings.INFORMATION_YOUR_ORGANIZATION_CAN_SEE_TITLE");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_exposure_changes_category",
                "Settings.CHANGES_MADE_BY_YOUR_ORGANIZATION_ADMIN_TITLE");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_device_access_category",
                "Settings.YOUR_ACCESS_TO_THIS_DEVICE_TITLE");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_enterprise_data",
                "Settings.ADMIN_CAN_SEE_WORK_DATA_WARNING");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_installed_packages",
                "Settings.ADMIN_CAN_SEE_APPS_WARNING");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_usage_stats",
                "Settings.ADMIN_CAN_SEE_USAGE_WARNING");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_network_logs",
                "Settings.ADMIN_CAN_SEE_NETWORK_LOGS_WARNING");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_bug_reports",
                "Settings.ADMIN_CAN_SEE_BUG_REPORT_WARNING");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_security_logs",
                "Settings.ADMIN_CAN_SEE_SECURITY_LOGS_WARNING");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_none", "Settings.ADMIN_ACTION_NONE");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_enterprise_installed_packages",
                "Settings.ADMIN_ACTION_APPS_INSTALLED");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_apps_count_estimation_info",
                "Settings.ADMIN_ACTION_APPS_COUNT_ESTIMATED");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_number_packages_lower_bound",
                "Settings.ADMIN_ACTIONS_APPS_COUNT_MINIMUM");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_location_access",
                "Settings.ADMIN_ACTION_ACCESS_LOCATION");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_microphone_access",
                "Settings.ADMIN_ACTION_ACCESS_MICROPHONE");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_camera_access",
                "Settings.ADMIN_ACTION_ACCESS_CAMERA");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_enterprise_set_default_apps",
                "Settings.ADMIN_ACTION_SET_DEFAULT_APPS");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_number_packages",
                "Settings.ADMIN_ACTIONS_APPS_COUNT");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_input_method",
                "Settings.ADMIN_ACTION_SET_CURRENT_INPUT_METHOD");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_input_method_name",
                "Settings.ADMIN_ACTION_SET_INPUT_METHOD_NAME");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_global_http_proxy",
                "Settings.ADMIN_ACTION_SET_HTTP_PROXY");
        DEVICE_POLICY_RESOURCES.put("work_policy_privacy_settings_summary",
                "Settings.WORK_PROFILE_PRIVACY_POLICY_INFO_SUMMARY");
        DEVICE_POLICY_RESOURCES.put("work_policy_privacy_settings",
                "Settings.WORK_PROFILE_PRIVACY_POLICY_INFO");
        DEVICE_POLICY_RESOURCES.put("interact_across_profiles_keywords",
                "Settings.CONNECTED_APPS_SEARCH_KEYWORDS");
        DEVICE_POLICY_RESOURCES.put("keywords_unification",
                "Settings.WORK_PROFILE_UNIFICATION_SEARCH_KEYWORDS");
        DEVICE_POLICY_RESOURCES.put("keywords_accounts", "Settings.ACCOUNTS_SEARCH_KEYWORDS");
        DEVICE_POLICY_RESOURCES.put("category_personal", "Settings.PERSONAL_CATEGORY_HEADER");
        DEVICE_POLICY_RESOURCES.put("lock_screen_notifications_summary_show_profile",
                "Settings.LOCK_SCREEN_SHOW_WORK_NOTIFICATION_CONTENT");
        DEVICE_POLICY_RESOURCES.put("lock_screen_notifications_summary_hide_profile",
                "Settings.LOCK_SCREEN_HIDE_WORK_NOTIFICATION_CONTENT");
        DEVICE_POLICY_RESOURCES.put("account_settings_menu_auto_sync_personal",
                "Settings.AUTO_SYNC_PERSONAL_DATA");
        DEVICE_POLICY_RESOURCES.put("account_settings_menu_auto_sync_work",
                "Settings.AUTO_SYNC_WORK_DATA");
        DEVICE_POLICY_RESOURCES.put("security_advanced_settings_work_profile_settings_summary",
                "Settings.MORE_SECURITY_SETTINGS_WORK_PROFILE_SUMMARY");
        DEVICE_POLICY_RESOURCES.put("lock_settings_picker_new_profile_lock_title",
                "Settings.LOCK_SETTINGS_NEW_PROFILE_LOCK_TITLE");
        DEVICE_POLICY_RESOURCES.put("lock_settings_picker_update_profile_lock_title",
                "Settings.LOCK_SETTINGS_UPDATE_PROFILE_LOCK_TITLE");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_exposure_category",
                "Settings.INFORMATION_SEEN_BY_ORGANIZATION_TITLE");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_exposure_changes_category",
                "Settings.CHANGES_BY_ORGANIZATION_TITLE");
        DEVICE_POLICY_RESOURCES.put("enterprise_privacy_header",
                "Settings.ENTERPRISE_PRIVACY_FOOTER");
        DEVICE_POLICY_RESOURCES.put("spellcheckers_settings_for_work_title",
                "Settings.SPELL_CHECKER_FOR_WORK");
        DEVICE_POLICY_RESOURCES.put("user_dict_settings_for_work_title",
                "Settings.PERSONAL_DICTIONARY_FOR_WORK");
        DEVICE_POLICY_RESOURCES.put("lock_settings_picker_admin_restricted_personal_message_action",
                "Settings.WORK_PROFILE_IT_ADMIN_CANT_RESET_SCREEN_LOCK_ACTION");
        DEVICE_POLICY_RESOURCES.put("disabled_by_admin_summary_text",
                "Settings.CONTROLLED_BY_ADMIN_SUMMARY");
        DEVICE_POLICY_RESOURCES.put("managed_user_title", "Settings.WORK_PROFILE_USER_LABEL");
        DEVICE_POLICY_RESOURCES.put("category_work", "Settings.WORK_CATEGORY_HEADER");
        DEVICE_POLICY_RESOURCES.put("category_personal", "Settings.PERSONAL_CATEGORY_HEADER");
        DEVICE_POLICY_RESOURCES.put("disabled_by_admin",
                "Settings.DISABLED_BY_ADMIN_SWITCH_SUMMARY");
        DEVICE_POLICY_RESOURCES.put("enabled_by_admin", "Settings.ENABLED_BY_ADMIN_SWITCH_SUMMARY");
    }

    public static boolean isDevicePolicyResource(Context context, int resId) {
        try {
            String resName = context.getResources().getResourceEntryName(resId);
            return DEVICE_POLICY_RESOURCES.containsKey(resName);
        } catch (Resources.NotFoundException e) {
            return false;
        }
    }

    public static boolean isDevicePolicyResource(
            Context context, TypedArray typedArray, int resId) {
        try {
            String resName = context.getResources().getResourceEntryName(
                    typedArray.getResourceId(resId, -1));
            return DEVICE_POLICY_RESOURCES.containsKey(resName);
        } catch (Resources.NotFoundException e) {
            return false;
        }
    }

    public static String getDevicePolicyResource(Context context, int resId) {
        try {
            String resName = context.getResources().getResourceEntryName(resId);
            if (!DEVICE_POLICY_RESOURCES.containsKey(resName)) {
                return context.getString(resId);
            }
            return context.getSystemService(DevicePolicyManager.class).getResources().getString(
                    DEVICE_POLICY_RESOURCES.get(resName), () -> context.getString(resId));
        } catch (Resources.NotFoundException e) {
            return context.getString(resId);
        }
    }

    public static String getDevicePolicyResource(
            Context context, TypedArray typedArray, int resId) {
        try {
            String resName = context.getResources().getResourceEntryName(
                    typedArray.getResourceId(resId, -1));
            if (!DEVICE_POLICY_RESOURCES.containsKey(resName)) {
                return typedArray.getString(resId);
            }
            return context.getSystemService(DevicePolicyManager.class).getResources()
                    .getString(DEVICE_POLICY_RESOURCES.get(resName), () ->
                            typedArray.getString(resId));
        } catch (Resources.NotFoundException e) {
            return typedArray.getString(resId);
        }
    }
}
