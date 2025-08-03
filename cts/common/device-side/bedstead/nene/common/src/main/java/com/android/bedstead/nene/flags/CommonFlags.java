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

package com.android.bedstead.nene.flags;

/**
 * Feature flags and namespaces.
 */
public final class CommonFlags {
    public static final String NAMESPACE_ACTIVITY_MANAGER = "activity_manager";
    public static final String NAMESPACE_ACTIVITY_MANAGER_COMPONENT_ALIAS = "activity_manager_ca";
    public static final String NAMESPACE_ACTIVITY_MANAGER_NATIVE_BOOT =
            "activity_manager_native_boot";
    public static final String NAMESPACE_ALARM_MANAGER = "alarm_manager";
    public static final String NAMESPACE_APP_COMPAT = "app_compat";
    public static final String NAMESPACE_APP_HIBERNATION = "app_hibernation";
    public static final String NAMESPACE_APPSEARCH = "appsearch";
    public static final String NAMESPACE_APP_STANDBY = "app_standby";
    public static final String NAMESPACE_ATTENTION_MANAGER_SERVICE = "attention_manager_service";
    public static final String NAMESPACE_AUTOFILL = "autofill";
    public static final String NAMESPACE_BATTERY_SAVER = "battery_saver";
    public static final String NAMESPACE_BLOBSTORE = "blobstore";
    public static final String NAMESPACE_BLUETOOTH = "bluetooth";
    public static final String NAMESPACE_CLIPBOARD = "clipboard";
    public static final String NAMESPACE_CONNECTIVITY = "connectivity";
    public static final String NAMESPACE_CAPTIVEPORTALLOGIN = "captive_portal_login";
    public static final String NAMESPACE_TETHERING = "tethering";
    public static final String NAMESPACE_NEARBY = "nearby";
    public static final String NAMESPACE_CONTENT_CAPTURE = "content_capture";
    public static final String NAMESPACE_DEVICE_IDLE = "device_idle";
    public static final String NAMESPACE_DEX_BOOT = "dex_boot";
    public static final String NAMESPACE_DISPLAY_MANAGER = "display_manager";
    public static final String NAMESPACE_GAME_DRIVER = "game_driver";
    public static final String NAMESPACE_INPUT_NATIVE_BOOT = "input_native_boot";
    public static final String NAMESPACE_INTELLIGENCE_ATTENTION = "intelligence_attention";
    public static final String NAMESPACE_INTELLIGENCE_CONTENT_SUGGESTIONS =
            "intelligence_content_suggestions";
    public static final String NAMESPACE_JOB_SCHEDULER = "jobscheduler";
    public static final String NAMESPACE_LMKD_NATIVE = "lmkd_native";
    public static final String NAMESPACE_LOCATION = "location";
    public static final String NAMESPACE_MEDIA = "media";
    public static final String NAMESPACE_MEDIA_NATIVE = "media_native";
    public static final String NAMESPACE_MGLRU_NATIVE = "mglru_native";
    public static final String NAMESPACE_NETD_NATIVE = "netd_native";
    public static final String NAMESPACE_NNAPI_NATIVE = "nnapi_native";
    public static final String NAMESPACE_ON_DEVICE_PERSONALIZATION = "on_device_personalization";
    public static final String NAMESPACE_PACKAGE_MANAGER_SERVICE = "package_manager_service";
    public static final String NAMESPACE_PROFCOLLECT_NATIVE_BOOT = "profcollect_native_boot";
    public static final String NAMESPACE_REBOOT_READINESS = "reboot_readiness";
    public static final String NAMESPACE_REMOTE_KEY_PROVISIONING_NATIVE =
            "remote_key_provisioning_native";
    public static final String NAMESPACE_ROLLBACK = "rollback";
    public static final String NAMESPACE_ROLLBACK_BOOT = "rollback_boot";
    public static final String NAMESPACE_ROTATION_RESOLVER = "rotation_resolver";
    public static final String NAMESPACE_RUNTIME = "runtime";
    public static final String NAMESPACE_RUNTIME_NATIVE = "runtime_native";
    public static final String NAMESPACE_RUNTIME_NATIVE_BOOT = "runtime_native_boot";
    public static final String NAMESPACE_SCHEDULER = "scheduler";
    public static final String NAMESPACE_SDK_SANDBOX = "sdk_sandbox";
    public static final String NAMESPACE_SETTINGS_STATS = "settings_stats";
    public static final String NAMESPACE_STATSD_JAVA = "statsd_java";
    public static final String NAMESPACE_STATSD_JAVA_BOOT = "statsd_java_boot";
    public static final String NAMESPACE_STATSD_NATIVE = "statsd_native";
    public static final String NAMESPACE_STATSD_NATIVE_BOOT = "statsd_native_boot";
    public static final String NAMESPACE_STORAGE = "storage";
    public static final String NAMESPACE_STORAGE_NATIVE_BOOT = "storage_native_boot";
    public static final String NAMESPACE_ADSERVICES = "adservices";
    public static final String NAMESPACE_SURFACE_FLINGER_NATIVE_BOOT =
            "surface_flinger_native_boot";
    public static final String NAMESPACE_SWCODEC_NATIVE = "swcodec_native";
    public static final String NAMESPACE_SYSTEMUI = "systemui";
    public static final String NAMESPACE_SYSTEM_TIME = "system_time";
    public static final String NAMESPACE_TARE = "tare";
    public static final String NAMESPACE_TELEPHONY = "telephony";
    public static final String NAMESPACE_TEXTCLASSIFIER = "textclassifier";
    public static final String NAMESPACE_CONTACTS_PROVIDER = "contacts_provider";
    public static final String NAMESPACE_SETTINGS_UI = "settings_ui";
    public static final String NAMESPACE_ANDROID = "android";
    public static final String NAMESPACE_WINDOW_MANAGER = "window_manager";
    public static final String NAMESPACE_WINDOW_MANAGER_NATIVE_BOOT = "window_manager_native_boot";
    public static final String NAMESPACE_SELECTION_TOOLBAR = "selection_toolbar";
    public static final String NAMESPACE_VOICE_INTERACTION = "voice_interaction";
    public static final String NAMESPACE_DEVICE_POLICY_MANAGER =
            "device_policy_manager";
    public static final String NAMESPACE_PRIVACY = "privacy";
    public static final String NAMESPACE_BIOMETRICS = "biometrics";
    public static final String NAMESPACE_PERMISSIONS = "permissions";
    public static final String NAMESPACE_OTA = "ota";
    public static final String NAMESPACE_WIDGET = "widget";
    public static final String NAMESPACE_CONNECTIVITY_THERMAL_POWER_MANAGER =
            "connectivity_thermal_power_manager";
    public static final String NAMESPACE_CONFIGURATION = "configuration";
    public static final String NAMESPACE_LATENCY_TRACKER = "latency_tracker";
    public static final String NAMESPACE_INTERACTION_JANK_MONITOR = "interaction_jank_monitor";
    public static final String NAMESPACE_GAME_OVERLAY = "game_overlay";
    public static final String NAMESPACE_VIRTUALIZATION_FRAMEWORK_NATIVE =
            "virtualization_framework_native";
    public static final String NAMESPACE_CONSTRAIN_DISPLAY_APIS = "constrain_display_apis";
    public static final String NAMESPACE_APP_COMPAT_OVERRIDES = "app_compat_overrides";
    public static final String NAMESPACE_UWB = "uwb";
    public static final String NAMESPACE_AMBIENT_CONTEXT_MANAGER_SERVICE =
            "ambient_context_manager_service";
    public static final String NAMESPACE_VENDOR_SYSTEM_NATIVE = "vendor_system_native";
    public static final String NAMESPACE_VENDOR_SYSTEM_NATIVE_BOOT = "vendor_system_native_boot";
    public static final String NAMESPACE_MEMORY_SAFETY_NATIVE_BOOT = "memory_safety_native_boot";
    public static final String NAMESPACE_MEMORY_SAFETY_NATIVE = "memory_safety_native";
    public static final String NAMESPACE_WEAR = "wear";
    public static final String NAMESPACE_TRANSPARENCY_METADATA = "transparency_metadata";
    public static final String NAMESPACE_INPUT_METHOD_MANAGER = "input_method_manager";
    public static final String NAMESPACE_BACKUP_AND_RESTORE = "backup_and_restore";
    public static final String NAMESPACE_ARC_APP_COMPAT = "arc_app_compat";

    /**
     * Flags in the DevicePolicyManager namespace.
     */
    public static final class DevicePolicyManager {
        public static final String DISABLE_RESOURCES_UPDATABILITY_FLAG =
                "disable_resources_updatability";
        public static final String DEPRECATE_USERMANAGERINTERNAL_DEVICEPOLICY_FLAG =
                "deprecate_usermanagerinternal_devicepolicy";
        public static final String PERMISSION_BASED_ACCESS_EXPERIMENT_FLAG =
                "enable_permission_based_access";
        public static final String ENABLE_COEXISTENCE_FLAG = "enable_coexistence";
        public static final String ENABLE_DEVICE_POLICY_ENGINE_FLAG =
                "enable_device_policy_engine";
        public static final String ENABLE_WORK_PROFILE_TELEPHONY_FLAG =
                "enable_work_profile_telephony";
    }


    /**
     * see {@code DevicePolicyManager.KEYGUARD_DISABLE_SECURE_CAMERA}
     */
    public static final int KEYGUARD_DISABLE_SECURE_CAMERA = 1 << 1;

    /**
     * see {@code DevicePolicyManager.KEYGUARD_DISABLE_SECURE_NOTIFICATIONS}
     */
    public static final int KEYGUARD_DISABLE_SECURE_NOTIFICATIONS = 1 << 2;

    /**
     * see {@code DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS}
     */
    public static final int KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS = 1 << 3;

    /**
     * see {@code DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS}
     */
    public static final int KEYGUARD_DISABLE_TRUST_AGENTS = 1 << 4;

    /**
     * see {@code DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT}
     */
    public static final int KEYGUARD_DISABLE_FINGERPRINT = 1 << 5;

    /**
     * see {@code DevicePolicyManager.KEYGUARD_DISABLE_REMOTE_INPUT}
     */
    public static final int KEYGUARD_DISABLE_REMOTE_INPUT = 1 << 6;

    /**
     * see {@code DevicePolicyManager.KEYGUARD_DISABLE_FACE}
     */
    public static final int KEYGUARD_DISABLE_FACE = 1 << 7;

    /**
     * see {@code DevicePolicyManager.KEYGUARD_DISABLE_IRIS}
     */
    public static final int KEYGUARD_DISABLE_IRIS = 1 << 8;

}
