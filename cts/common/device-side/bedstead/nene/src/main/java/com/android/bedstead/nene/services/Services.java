/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.bedstead.nene.services;

import android.accounts.AccountManager;
import android.app.ActivityManager;
//import android.app.ActivityTaskManager;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.DownloadManager;
import android.app.DreamManager;
import android.app.GameManager;
import android.app.GrammaticalInflectionManager;
import android.app.KeyguardManager;
import android.app.LocaleManager;
import android.app.NotificationManager;
import android.app.SearchManager;
import android.app.StatusBarManager;
import android.app.UiModeManager;
//import android.app.UriGrantsManager;
import android.app.VrManager;
import android.app.WallpaperManager;
import android.app.admin.DevicePolicyManager;
import android.app.ambientcontext.AmbientContextManager;
import android.app.contentsuggestions.ContentSuggestionsManager;
import android.app.people.PeopleManager;
import android.app.prediction.AppPredictionManager;
import android.app.search.SearchUiManager;
//import android.app.slice.SliceManager;
import android.app.smartspace.SmartspaceManager;
import android.app.time.TimeManager;
//import android.app.timedetector.TimeDetector;
//import android.app.timezonedetector.TimeZoneDetector;
//import android.app.trust.TrustManager;
import android.app.usage.StorageStatsManager;
import android.app.usage.UsageStatsManager;
import android.app.wallpapereffectsgeneration.WallpaperEffectsGenerationManager;
import android.app.wearable.WearableSensingManager;
import android.apphibernation.AppHibernationManager;
import android.appwidget.AppWidgetManager;
import android.companion.CompanionDeviceManager;
import android.companion.virtual.VirtualDeviceManager;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.RestrictionsManager;
import android.content.integrity.AppIntegrityManager;
import android.content.om.OverlayManager;
import android.content.pm.CrossProfileApps;
//import android.content.pm.DataLoaderManager;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutManager;
import android.content.pm.verify.domain.DomainVerificationManager;
import android.credentials.CredentialManager;
//import android.debug.AdbManager;
import android.graphics.fonts.FontManager;
import android.hardware.ConsumerIrManager;
import android.hardware.SensorManager;
//import android.hardware.SensorPrivacyManager;
//import android.hardware.SerialManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.camera2.CameraManager;
//import android.hardware.devicestate.DeviceStateManager;
//import android.hardware.display.ColorDisplayManager;
import android.hardware.display.DisplayManager;
//import android.hardware.face.FaceManager;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.hdmi.HdmiControlManager;
import android.hardware.input.InputManager;
//import android.hardware.iris.IrisManager;
//import android.hardware.lights.LightsManager;
import android.hardware.location.ContextHubManager;
//import android.hardware.radio.RadioManager;
import android.hardware.usb.UsbManager;
//import android.location.CountryDetector;
import android.location.LocationManager;
import android.media.AudioDeviceVolumeManager;
import android.media.AudioManager;
import android.media.MediaRouter;
import android.media.metrics.MediaMetricsManager;
import android.media.midi.MidiManager;
import android.media.musicrecognition.MusicRecognitionManager;
import android.media.projection.MediaProjectionManager;
//import android.media.soundtrigger.SoundTriggerManager;
import android.media.tv.TvInputManager;
import android.media.tv.interactive.TvInteractiveAppManager;
//import android.media.tv.tunerresourcemanager.TunerResourceManager;
//import android.net.NetworkPolicyManager;
import android.net.NetworkScoreManager;
//import android.net.NetworkWatchlistManager;
//import android.net.PacProxyManager;
import android.net.TetheringManager;
import android.net.VpnManager;
//import android.net.vcn.VcnManager;
import android.net.wifi.nl80211.WifiNl80211Manager;
import android.net.wifi.sharedconnectivity.app.SharedConnectivityManager;
import android.os.BatteryManager;
import android.os.BatteryStatsManager;
import android.os.BugreportManager;
import android.os.DropBoxManager;
import android.os.HardwarePropertiesManager;
import android.os.IBinder;
//import android.os.IncidentManager;
import android.os.PerformanceHintManager;
//import android.os.PermissionEnforcer;
import android.os.PowerManager;
//import android.os.RecoverySystem;
import android.os.SystemConfigManager;
import android.os.SystemUpdateManager;
import android.os.UserManager;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.os.health.SystemHealthManager;
//import android.os.image.DynamicSystemManager;
//import android.os.incremental.IncrementalManager;
import android.os.storage.StorageManager;
//import android.permission.LegacyPermissionManager;
//import android.permission.PermissionCheckerManager;
import android.permission.PermissionControllerManager;
import android.permission.PermissionManager;
import android.print.PrintManager;
import android.security.FileIntegrityManager;
//import android.security.attestationverification.AttestationVerificationManager;
import android.service.oemlock.OemLockManager;
import android.service.persistentdata.PersistentDataBlockManager;
import android.telecom.TelecomManager;
//import android.telephony.MmsManager;
//import android.telephony.TelephonyRegistryManager;
//import android.transparency.BinaryTransparencyManager;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.CaptioningManager;
//import android.view.autofill.AutofillManager;
import android.view.contentcapture.ContentCaptureManager;
import android.view.displayhash.DisplayHashManager;
import android.view.inputmethod.InputMethodManager;
//import android.view.selectiontoolbar.SelectionToolbarManager;
import android.view.textclassifier.TextClassificationManager;
import android.view.textservice.TextServicesManager;
import android.view.translation.TranslationManager;
import android.view.translation.UiTranslationManager;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.utils.ShellCommand;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TestApis related to system services.
 */
@Experimental
public final class Services {

    public static final Services sInstance = new Services();

    // Mapping from SystemServiceRegistry.java
    private static final Map<String, Class<?>> sServiceMapping = new HashMap<>();
    static {
        sServiceMapping.put(Context.ACCESSIBILITY_SERVICE, AccessibilityManager.class);
        sServiceMapping.put(Context.CAPTIONING_SERVICE, CaptioningManager.class);
        sServiceMapping.put(Context.ACCOUNT_SERVICE, AccountManager.class);
        sServiceMapping.put(Context.ACTIVITY_SERVICE, ActivityManager.class);
//        sServiceMapping.put(Context.ACTIVITY_TASK_SERVICE, ActivityTaskManager.class);
//        sServiceMapping.put(Context.URI_GRANTS_SERVICE, UriGrantsManager.class);
        sServiceMapping.put(Context.ALARM_SERVICE, AlarmManager.class);
        sServiceMapping.put(Context.AUDIO_SERVICE, AudioManager.class);
        sServiceMapping.put(Context.AUDIO_DEVICE_VOLUME_SERVICE, AudioDeviceVolumeManager.class);
        sServiceMapping.put(Context.MEDIA_ROUTER_SERVICE, MediaRouter.class);
        sServiceMapping.put(Context.HDMI_CONTROL_SERVICE, HdmiControlManager.class);
        sServiceMapping.put(Context.TEXT_CLASSIFICATION_SERVICE, TextClassificationManager.class);
//        sServiceMapping.put(Context.SELECTION_TOOLBAR_SERVICE, SelectionToolbarManager.class);
        sServiceMapping.put(Context.FONT_SERVICE, FontManager.class);
        sServiceMapping.put(Context.CLIPBOARD_SERVICE, ClipboardManager.class);
//        sServiceMapping.put(Context.PAC_PROXY_SERVICE, PacProxyManager.class);
        sServiceMapping.put(Context.NETD_SERVICE, IBinder.class);
        sServiceMapping.put(Context.TETHERING_SERVICE, TetheringManager.class);
        sServiceMapping.put(Context.VPN_MANAGEMENT_SERVICE, VpnManager.class);
//        sServiceMapping.put(Context.VCN_MANAGEMENT_SERVICE, VcnManager.class);
//        sServiceMapping.put(Context.COUNTRY_DETECTOR, CountryDetector.class);
        sServiceMapping.put(Context.DEVICE_POLICY_SERVICE, DevicePolicyManager.class);
        sServiceMapping.put(Context.DOWNLOAD_SERVICE, DownloadManager.class);
        sServiceMapping.put(Context.BATTERY_SERVICE, BatteryManager.class);
        sServiceMapping.put(Context.DROPBOX_SERVICE, DropBoxManager.class);
//        sServiceMapping.put(Context.BINARY_TRANSPARENCY_SERVICE, BinaryTransparencyManager.class);
        sServiceMapping.put(Context.INPUT_SERVICE, InputManager.class);
        sServiceMapping.put(Context.DISPLAY_SERVICE, DisplayManager.class);
//        sServiceMapping.put(Context.COLOR_DISPLAY_SERVICE, ColorDisplayManager.class);
        sServiceMapping.put(Context.INPUT_METHOD_SERVICE, InputMethodManager.class);
        sServiceMapping.put(Context.TEXT_SERVICES_MANAGER_SERVICE, TextServicesManager.class);
        sServiceMapping.put(Context.KEYGUARD_SERVICE, KeyguardManager.class);
        sServiceMapping.put(Context.LAYOUT_INFLATER_SERVICE, LayoutInflater.class);
        sServiceMapping.put(Context.LOCATION_SERVICE, LocationManager.class);
//        sServiceMapping.put(Context.NETWORK_POLICY_SERVICE, NetworkPolicyManager.class);
        sServiceMapping.put(Context.NOTIFICATION_SERVICE, NotificationManager.class);
        sServiceMapping.put(Context.PEOPLE_SERVICE, PeopleManager.class);
        sServiceMapping.put(Context.POWER_SERVICE, PowerManager.class);
        sServiceMapping.put(Context.PERFORMANCE_HINT_SERVICE, PerformanceHintManager.class);
//        sServiceMapping.put(Context.RECOVERY_SERVICE, RecoverySystem.class);
        sServiceMapping.put(Context.SEARCH_SERVICE, SearchManager.class);
        sServiceMapping.put(Context.SENSOR_SERVICE, SensorManager.class);
//        sServiceMapping.put(Context.SENSOR_PRIVACY_SERVICE, SensorPrivacyManager.class);
        sServiceMapping.put(Context.STATUS_BAR_SERVICE, StatusBarManager.class);
        sServiceMapping.put(Context.STORAGE_SERVICE, StorageManager.class);
        sServiceMapping.put(Context.STORAGE_STATS_SERVICE, StorageStatsManager.class);
        sServiceMapping.put(Context.SYSTEM_UPDATE_SERVICE, SystemUpdateManager.class);
        sServiceMapping.put(Context.SYSTEM_CONFIG_SERVICE, SystemConfigManager.class);
//        sServiceMapping.put(Context.TELEPHONY_REGISTRY_SERVICE, TelephonyRegistryManager.class);
        sServiceMapping.put(Context.TELECOM_SERVICE, TelecomManager.class);
//        sServiceMapping.put(Context.MMS_SERVICE, MmsManager.class);
        sServiceMapping.put(Context.UI_MODE_SERVICE, UiModeManager.class);
        sServiceMapping.put(Context.USB_SERVICE, UsbManager.class);
//        sServiceMapping.put(Context.ADB_SERVICE, AdbManager.class);
//        sServiceMapping.put(Context.SERIAL_SERVICE, SerialManager.class);
        sServiceMapping.put(Context.VIBRATOR_MANAGER_SERVICE, VibratorManager.class);
        sServiceMapping.put(Context.VIBRATOR_SERVICE, Vibrator.class);
        sServiceMapping.put(Context.WALLPAPER_SERVICE, WallpaperManager.class);
        sServiceMapping.put(Context.WIFI_NL80211_SERVICE, WifiNl80211Manager.class);
        sServiceMapping.put(Context.WINDOW_SERVICE, WindowManager.class);
        sServiceMapping.put(Context.USER_SERVICE, UserManager.class);
        sServiceMapping.put(Context.APP_OPS_SERVICE, AppOpsManager.class);
        sServiceMapping.put(Context.CAMERA_SERVICE, CameraManager.class);
        sServiceMapping.put(Context.LAUNCHER_APPS_SERVICE, LauncherApps.class);
        sServiceMapping.put(Context.RESTRICTIONS_SERVICE, RestrictionsManager.class);
        sServiceMapping.put(Context.PRINT_SERVICE, PrintManager.class);
        sServiceMapping.put(Context.COMPANION_DEVICE_SERVICE, CompanionDeviceManager.class);
        sServiceMapping.put(Context.VIRTUAL_DEVICE_SERVICE, VirtualDeviceManager.class);
        sServiceMapping.put(Context.CONSUMER_IR_SERVICE, ConsumerIrManager.class);
//        sServiceMapping.put(Context.TRUST_SERVICE, TrustManager.class);
        sServiceMapping.put(Context.FINGERPRINT_SERVICE, FingerprintManager.class);
//        sServiceMapping.put(Context.FACE_SERVICE, FaceManager.class);
//        sServiceMapping.put(Context.IRIS_SERVICE, IrisManager.class);
        sServiceMapping.put(Context.BIOMETRIC_SERVICE, BiometricManager.class);
        sServiceMapping.put(Context.TV_INTERACTIVE_APP_SERVICE, TvInteractiveAppManager.class);
        sServiceMapping.put(Context.TV_INPUT_SERVICE, TvInputManager.class);
//        sServiceMapping.put(Context.TV_TUNER_RESOURCE_MGR_SERVICE, TunerResourceManager.class);
        sServiceMapping.put(Context.NETWORK_SCORE_SERVICE, NetworkScoreManager.class);
        sServiceMapping.put(Context.USAGE_STATS_SERVICE, UsageStatsManager.class);
        sServiceMapping.put(Context.PERSISTENT_DATA_BLOCK_SERVICE, PersistentDataBlockManager.class);
        sServiceMapping.put(Context.OEM_LOCK_SERVICE, OemLockManager.class);
        sServiceMapping.put(Context.MEDIA_PROJECTION_SERVICE, MediaProjectionManager.class);
        sServiceMapping.put(Context.APPWIDGET_SERVICE, AppWidgetManager.class);
        sServiceMapping.put(Context.MIDI_SERVICE, MidiManager.class);
//        sServiceMapping.put(Context.RADIO_SERVICE, RadioManager.class);
        sServiceMapping.put(Context.HARDWARE_PROPERTIES_SERVICE, HardwarePropertiesManager.class);
//        sServiceMapping.put(Context.SOUND_TRIGGER_SERVICE, SoundTriggerManager.class);
        sServiceMapping.put(Context.SHORTCUT_SERVICE, ShortcutManager.class);
        sServiceMapping.put(Context.OVERLAY_SERVICE, OverlayManager.class);
//        sServiceMapping.put(Context.NETWORK_WATCHLIST_SERVICE, NetworkWatchlistManager.class);
        sServiceMapping.put(Context.SYSTEM_HEALTH_SERVICE, SystemHealthManager.class);
        sServiceMapping.put(Context.CONTEXTHUB_SERVICE, ContextHubManager.class);
//        sServiceMapping.put(Context.INCIDENT_SERVICE, IncidentManager.class);
        sServiceMapping.put(Context.BUGREPORT_SERVICE, BugreportManager.class);
//        sServiceMapping.put(Context.AUTOFILL_MANAGER_SERVICE, AutofillManager.class);
        sServiceMapping.put(Context.CREDENTIAL_SERVICE, CredentialManager.class);
        sServiceMapping.put(Context.MUSIC_RECOGNITION_SERVICE, MusicRecognitionManager.class);
        sServiceMapping.put(Context.CONTENT_CAPTURE_MANAGER_SERVICE, ContentCaptureManager.class);
        sServiceMapping.put(Context.TRANSLATION_MANAGER_SERVICE, TranslationManager.class);
        sServiceMapping.put(Context.UI_TRANSLATION_SERVICE, UiTranslationManager.class);
        sServiceMapping.put(Context.SEARCH_UI_SERVICE, SearchUiManager.class);
        sServiceMapping.put(Context.SMARTSPACE_SERVICE, SmartspaceManager.class);
        sServiceMapping.put(Context.APP_PREDICTION_SERVICE, AppPredictionManager.class);
        sServiceMapping.put(Context.VR_SERVICE, VrManager.class);
        sServiceMapping.put(Context.CROSS_PROFILE_APPS_SERVICE, CrossProfileApps.class);
//        sServiceMapping.put(Context.SLICE_SERVICE, SliceManager.class);
//        sServiceMapping.put(Context.TIME_DETECTOR_SERVICE, TimeDetector.class);
//        sServiceMapping.put(Context.TIME_ZONE_DETECTOR_SERVICE, TimeZoneDetector.class);
        sServiceMapping.put(Context.TIME_MANAGER_SERVICE, TimeManager.class);
        sServiceMapping.put(Context.PERMISSION_SERVICE, PermissionManager.class);
//        sServiceMapping.put(Context.LEGACY_PERMISSION_SERVICE, LegacyPermissionManager.class);
        sServiceMapping.put(Context.PERMISSION_CONTROLLER_SERVICE, PermissionControllerManager.class);
//        sServiceMapping.put(Context.PERMISSION_CHECKER_SERVICE, PermissionCheckerManager.class);
//        sServiceMapping.put(Context.PERMISSION_ENFORCER_SERVICE, PermissionEnforcer.class);
//        sServiceMapping.put(Context.DYNAMIC_SYSTEM_SERVICE, DynamicSystemManager.class);
        sServiceMapping.put(Context.BATTERY_STATS_SERVICE, BatteryStatsManager.class);
//        sServiceMapping.put(Context.DATA_LOADER_MANAGER_SERVICE, DataLoaderManager.class);
//        sServiceMapping.put(Context.LIGHTS_SERVICE, LightsManager.class);
        sServiceMapping.put(Context.LOCALE_SERVICE, LocaleManager.class);
//        sServiceMapping.put(Context.INCREMENTAL_SERVICE, IncrementalManager.class);
        sServiceMapping.put(Context.FILE_INTEGRITY_SERVICE, FileIntegrityManager.class);
        sServiceMapping.put(Context.APP_INTEGRITY_SERVICE, AppIntegrityManager.class);
        sServiceMapping.put(Context.APP_HIBERNATION_SERVICE, AppHibernationManager.class);
        sServiceMapping.put(Context.DREAM_SERVICE, DreamManager.class);
//        sServiceMapping.put(Context.DEVICE_STATE_SERVICE, DeviceStateManager.class);
        sServiceMapping.put(Context.MEDIA_METRICS_SERVICE, MediaMetricsManager.class);
        sServiceMapping.put(Context.GAME_SERVICE, GameManager.class);
        sServiceMapping.put(Context.DOMAIN_VERIFICATION_SERVICE, DomainVerificationManager.class);
        sServiceMapping.put(Context.DISPLAY_HASH_SERVICE, DisplayHashManager.class);
        sServiceMapping.put(Context.AMBIENT_CONTEXT_SERVICE, AmbientContextManager.class);
        sServiceMapping.put(Context.WEARABLE_SENSING_SERVICE, WearableSensingManager.class);
        sServiceMapping.put(Context.GRAMMATICAL_INFLECTION_SERVICE, GrammaticalInflectionManager.class);
        sServiceMapping.put(Context.SHARED_CONNECTIVITY_SERVICE, SharedConnectivityManager.class);
        sServiceMapping.put(Context.CONTENT_SUGGESTIONS_SERVICE, ContentSuggestionsManager.class);
        sServiceMapping.put(Context.WALLPAPER_EFFECTS_GENERATION_SERVICE,
                WallpaperEffectsGenerationManager.class);
//        sServiceMapping.put(Context.ATTESTATION_VERIFICATION_SERVICE,
//                AttestationVerificationManager.class);
    }
    private static final Map<Class<?>, String> sServiceNameMapping =
            sServiceMapping.entrySet().stream().collect(
                    Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));


    private Services() {

    }

    public boolean serviceIsAvailable(String service) {
        if (!sServiceMapping.containsKey(service)) {
            throw new NeneException("Unknown service " + service + ". Check Nene Services map");
        }
        return serviceIsAvailable(service, sServiceMapping.get(service));
    }

    public boolean serviceIsAvailable(Class<?> serviceClass) {
        if (!sServiceNameMapping.containsKey(serviceClass)) {
            throw new NeneException("Unknown service " + serviceClass + ". Check Nene Services map");
        }
        return serviceIsAvailable(sServiceNameMapping.get(serviceClass), serviceClass);
    }

    private boolean serviceIsAvailable(String service, Class<?> serviceClass) {
        if (TestApis.context().instrumentedContext().getSystemService(serviceClass) == null) {
            return false;
        }

        return ShellCommand.builder("cmd -l")
                .executeOrThrowNeneException("Error getting service list")
                .contains(service);

    }

}
