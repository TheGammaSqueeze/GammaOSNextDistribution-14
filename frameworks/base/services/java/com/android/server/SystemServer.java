/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.server;

import static android.net.NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK;
import static android.os.IServiceManager.DUMP_FLAG_PRIORITY_CRITICAL;
import static android.os.IServiceManager.DUMP_FLAG_PRIORITY_HIGH;
import static android.os.IServiceManager.DUMP_FLAG_PRIORITY_NORMAL;
import static android.os.IServiceManager.DUMP_FLAG_PROTO;
import static android.os.Process.SYSTEM_UID;
import static android.os.Process.myPid;
import static android.system.OsConstants.O_CLOEXEC;
import static android.system.OsConstants.O_RDONLY;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.server.utils.TimingsTraceAndSlog.SYSTEM_SERVER_TIMING_TAG;

import android.annotation.NonNull;
import android.annotation.StringRes;
import android.app.ActivityThread;
import android.app.AppCompatCallbacks;
import android.app.ApplicationErrorReport;
import android.app.INotificationManager;
import android.app.SystemServiceRegistry;
import android.app.admin.DevicePolicySafetyChecker;
import android.app.usage.UsageStatsManagerInternal;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.res.Configuration;
import android.content.res.Resources.Theme;
import android.credentials.CredentialManager;
import android.database.sqlite.SQLiteCompatibilityWalFlags;
import android.database.sqlite.SQLiteGlobal;
import android.graphics.GraphicsStatsService;
import android.graphics.Typeface;
import android.hardware.display.DisplayManagerInternal;
import android.net.ConnectivityManager;
import android.net.ConnectivityModuleConnector;
import android.net.NetworkStackClient;
import android.os.ArtModuleServiceManager;
import android.os.BaseBundle;
import android.os.Binder;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.FactoryTest;
import android.os.FileUtils;
import android.os.IBinder;
import android.os.IBinderCallback;
import android.os.IIncidentManager;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.PowerManager;
import android.os.Process;
import android.os.ServiceManager;
import android.os.StrictMode;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.IStorageManager;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.server.ServerProtoEnums;
import android.system.ErrnoException;
import android.system.Os;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.DisplayMetrics;
import android.util.Dumpable;
import android.util.EventLog;
import android.util.IndentingPrintWriter;
import android.util.Pair;
import android.util.Slog;
import android.util.TimeUtils;
import android.view.contentcapture.ContentCaptureManager;

import com.android.i18n.timezone.ZoneInfoDb;
import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.notification.SystemNotificationChannels;
import com.android.internal.os.BackgroundThread;
import com.android.internal.os.BinderInternal;
import com.android.internal.os.RuntimeInit;
import com.android.internal.policy.AttributeCache;
import com.android.internal.util.ConcurrentUtils;
import com.android.internal.util.EmergencyAffordanceManager;
import com.android.internal.util.FrameworkStatsLog;
import com.android.internal.widget.ILockSettings;
import com.android.internal.widget.LockSettingsInternal;
import com.android.server.adaptiveauth.AdaptiveAuthService;
import com.android.server.am.ActivityManagerService;
import com.android.server.appbinding.AppBindingService;
import com.android.server.appop.AppOpMigrationHelper;
import com.android.server.appop.AppOpMigrationHelperImpl;
import com.android.server.art.ArtModuleServiceInitializer;
import com.android.server.art.DexUseManagerLocal;
import com.android.server.attention.AttentionManagerService;
import com.android.server.audio.AudioService;
import com.android.server.biometrics.AuthService;
import com.android.server.biometrics.BiometricService;
import com.android.server.biometrics.sensors.face.FaceService;
import com.android.server.biometrics.sensors.fingerprint.FingerprintService;
import com.android.server.biometrics.sensors.iris.IrisService;
import com.android.server.broadcastradio.BroadcastRadioService;
import com.android.server.camera.CameraServiceProxy;
import com.android.server.clipboard.ClipboardService;
import com.android.server.compat.PlatformCompat;
import com.android.server.compat.PlatformCompatNative;
import com.android.server.connectivity.PacProxyService;
import com.android.server.contentcapture.ContentCaptureManagerInternal;
import com.android.server.coverage.CoverageService;
import com.android.server.cpu.CpuMonitorService;
import com.android.server.criticalevents.CriticalEventLog;
import com.android.server.devicepolicy.DevicePolicyManagerService;
import com.android.server.devicestate.DeviceStateManagerService;
import com.android.server.display.DisplayManagerService;
import com.android.server.display.color.ColorDisplayService;
import com.android.server.dreams.DreamManagerService;
import com.android.server.emergency.EmergencyAffordanceService;
import com.android.server.flags.FeatureFlagsService;
import com.android.server.gpu.GpuService;
import com.android.server.grammaticalinflection.GrammaticalInflectionService;
import com.android.server.graphics.fonts.FontManagerService;
import com.android.server.hdmi.HdmiControlService;
import com.android.server.incident.IncidentCompanionService;
import com.android.server.input.InputManagerService;
import com.android.server.inputmethod.InputMethodManagerService;
import com.android.server.integrity.AppIntegrityManagerService;
import com.android.server.lights.LightsService;
import com.android.server.locales.LocaleManagerService;
import com.android.server.location.LocationManagerService;
import com.android.server.location.altitude.AltitudeService;
import com.android.server.logcat.LogcatManagerService;
import com.android.server.media.MediaRouterService;
import com.android.server.media.metrics.MediaMetricsManagerService;
import com.android.server.media.projection.MediaProjectionManagerService;
import com.android.server.net.NetworkManagementService;
import com.android.server.net.NetworkPolicyManagerService;
import com.android.server.net.watchlist.NetworkWatchlistService;
import com.android.server.notification.NotificationManagerService;
import com.android.server.oemlock.OemLockService;
import com.android.server.om.OverlayManagerService;
import com.android.server.os.BugreportManagerService;
import com.android.server.os.DeviceIdentifiersPolicyService;
import com.android.server.os.NativeTombstoneManagerService;
import com.android.server.os.SchedulingPolicyService;
import com.android.server.pdb.PersistentDataBlockService;
import com.android.server.people.PeopleService;
import com.android.server.permission.access.AccessCheckingService;
import com.android.server.pm.ApexManager;
import com.android.server.pm.ApexSystemServiceInfo;
import com.android.server.pm.BackgroundInstallControlService;
import com.android.server.pm.CrossProfileAppsService;
import com.android.server.pm.DataLoaderManagerService;
import com.android.server.pm.DexOptHelper;
import com.android.server.pm.DynamicCodeLoggingService;
import com.android.server.pm.Installer;
import com.android.server.pm.LauncherAppsService;
import com.android.server.pm.OtaDexoptService;
import com.android.server.pm.PackageManagerService;
import com.android.server.pm.ShortcutService;
import com.android.server.pm.UserManagerService;
import com.android.server.pm.dex.OdsignStatsLogger;
import com.android.server.pm.permission.PermissionMigrationHelper;
import com.android.server.pm.permission.PermissionMigrationHelperImpl;
import com.android.server.pm.verify.domain.DomainVerificationService;
import com.android.server.policy.AppOpsPolicy;
import com.android.server.policy.PermissionPolicyService;
import com.android.server.policy.PhoneWindowManager;
import com.android.server.policy.role.RoleServicePlatformHelperImpl;
import com.android.server.power.PowerManagerService;
import com.android.server.power.ShutdownThread;
import com.android.server.power.ThermalManagerService;
import com.android.server.power.hint.HintManagerService;
import com.android.server.powerstats.PowerStatsService;
import com.android.server.profcollect.ProfcollectForwardingService;
import com.android.server.recoverysystem.RecoverySystemService;
import com.android.server.resources.ResourcesManagerService;
import com.android.server.restrictions.RestrictionsManagerService;
import com.android.server.role.RoleServicePlatformHelper;
import com.android.server.rotationresolver.RotationResolverManagerService;
import com.android.server.security.AttestationVerificationManagerService;
import com.android.server.security.FileIntegrityService;
import com.android.server.security.KeyAttestationApplicationIdProviderService;
import com.android.server.security.KeyChainSystemService;
import com.android.server.security.rkp.RemoteProvisioningService;
import com.android.server.selinux.SelinuxAuditLogsService;
import com.android.server.sensorprivacy.SensorPrivacyService;
import com.android.server.sensors.SensorService;
import com.android.server.signedconfig.SignedConfigService;
import com.android.server.soundtrigger.SoundTriggerService;
import com.android.server.soundtrigger_middleware.SoundTriggerMiddlewareService;
import com.android.server.statusbar.StatusBarManagerService;
import com.android.server.storage.DeviceStorageMonitorService;
import com.android.server.telecom.TelecomLoaderService;
import com.android.server.testharness.TestHarnessModeService;
import com.android.server.textclassifier.TextClassificationManagerService;
import com.android.server.textservices.TextServicesManagerService;
import com.android.server.timedetector.NetworkTimeUpdateService;
import com.android.server.tracing.TracingServiceProxy;
import com.android.server.trust.TrustManagerService;
import com.android.server.tv.TvInputManagerService;
import com.android.server.tv.TvRemoteService;
import com.android.server.tv.interactive.TvInteractiveAppManagerService;
import com.android.server.tv.tunerresourcemanager.TunerResourceManagerService;
import com.android.server.twilight.TwilightService;
import com.android.server.uri.UriGrantsManagerService;
import com.android.server.usage.UsageStatsService;
import com.android.server.utils.TimingsTraceAndSlog;
import com.android.server.vibrator.VibratorManagerService;
import com.android.server.vr.VrManagerService;
import com.android.server.wearable.WearableSensingManagerService;
import com.android.server.webkit.WebViewUpdateService;
import com.android.server.wm.ActivityTaskManagerService;
import com.android.server.wm.WindowManagerGlobalLock;
import com.android.server.wm.WindowManagerService;

import dalvik.system.VMRuntime;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

/**
 * Entry point to {@code system_server}.
 */
public final class SystemServer implements Dumpable {

    private static final String TAG = "SystemServer";

    private static final long SLOW_DISPATCH_THRESHOLD_MS = 100;
    private static final long SLOW_DELIVERY_THRESHOLD_MS = 200;

    /*
     * Implementation class names. TODO: Move them to a codegen class or load
     * them from the build system somehow.
     */
    private static final String BACKUP_MANAGER_SERVICE_CLASS =
            "com.android.server.backup.BackupManagerService$Lifecycle";
    private static final String APPWIDGET_SERVICE_CLASS =
            "com.android.server.appwidget.AppWidgetService";
    private static final String ARC_NETWORK_SERVICE_CLASS =
            "com.android.server.arc.net.ArcNetworkService";
    private static final String ARC_PERSISTENT_DATA_BLOCK_SERVICE_CLASS =
            "com.android.server.arc.persistent_data_block.ArcPersistentDataBlockService";
    private static final String ARC_SYSTEM_HEALTH_SERVICE =
            "com.android.server.arc.health.ArcSystemHealthService";
    private static final String VOICE_RECOGNITION_MANAGER_SERVICE_CLASS =
            "com.android.server.voiceinteraction.VoiceInteractionManagerService";
    private static final String APP_HIBERNATION_SERVICE_CLASS =
            "com.android.server.apphibernation.AppHibernationService";
    private static final String PRINT_MANAGER_SERVICE_CLASS =
            "com.android.server.print.PrintManagerService";
    private static final String COMPANION_DEVICE_MANAGER_SERVICE_CLASS =
            "com.android.server.companion.CompanionDeviceManagerService";
    private static final String VIRTUAL_DEVICE_MANAGER_SERVICE_CLASS =
            "com.android.server.companion.virtual.VirtualDeviceManagerService";
    private static final String STATS_COMPANION_APEX_PATH =
            "/apex/com.android.os.statsd/javalib/service-statsd.jar";
    private static final String SCHEDULING_APEX_PATH =
            "/apex/com.android.scheduling/javalib/service-scheduling.jar";
    private static final String REBOOT_READINESS_LIFECYCLE_CLASS =
            "com.android.server.scheduling.RebootReadinessManagerService$Lifecycle";
    private static final String CONNECTIVITY_SERVICE_APEX_PATH =
            "/apex/com.android.tethering/javalib/service-connectivity.jar";
    private static final String STATS_COMPANION_LIFECYCLE_CLASS =
            "com.android.server.stats.StatsCompanion$Lifecycle";
    private static final String STATS_PULL_ATOM_SERVICE_CLASS =
            "com.android.server.stats.pull.StatsPullAtomService";
    private static final String STATS_BOOTSTRAP_ATOM_SERVICE_LIFECYCLE_CLASS =
            "com.android.server.stats.bootstrap.StatsBootstrapAtomService$Lifecycle";
    private static final String USB_SERVICE_CLASS =
            "com.android.server.usb.UsbService$Lifecycle";
    private static final String MIDI_SERVICE_CLASS =
            "com.android.server.midi.MidiService$Lifecycle";
    private static final String WIFI_APEX_SERVICE_JAR_PATH =
            "/apex/com.android.wifi/javalib/service-wifi.jar";
    private static final String WIFI_SERVICE_CLASS =
            "com.android.server.wifi.WifiService";
    private static final String WIFI_SCANNING_SERVICE_CLASS =
            "com.android.server.wifi.scanner.WifiScanningService";
    private static final String WIFI_RTT_SERVICE_CLASS =
            "com.android.server.wifi.rtt.RttService";
    private static final String WIFI_AWARE_SERVICE_CLASS =
            "com.android.server.wifi.aware.WifiAwareService";
    private static final String WIFI_P2P_SERVICE_CLASS =
            "com.android.server.wifi.p2p.WifiP2pService";
    private static final String LOWPAN_SERVICE_CLASS =
            "com.android.server.lowpan.LowpanService";
    private static final String JOB_SCHEDULER_SERVICE_CLASS =
            "com.android.server.job.JobSchedulerService";
    private static final String LOCK_SETTINGS_SERVICE_CLASS =
            "com.android.server.locksettings.LockSettingsService$Lifecycle";
    private static final String RESOURCE_ECONOMY_SERVICE_CLASS =
            "com.android.server.tare.InternalResourceService";
    private static final String STORAGE_MANAGER_SERVICE_CLASS =
            "com.android.server.StorageManagerService$Lifecycle";
    private static final String STORAGE_STATS_SERVICE_CLASS =
            "com.android.server.usage.StorageStatsService$Lifecycle";
    private static final String SEARCH_MANAGER_SERVICE_CLASS =
            "com.android.server.search.SearchManagerService$Lifecycle";
    private static final String THERMAL_OBSERVER_CLASS =
            "com.android.clockwork.ThermalObserver";
    private static final String WEAR_CONNECTIVITY_SERVICE_CLASS =
            "com.android.clockwork.connectivity.WearConnectivityService";
    private static final String WEAR_POWER_SERVICE_CLASS =
            "com.android.clockwork.power.WearPowerService";
    private static final String HEALTH_SERVICE_CLASS =
            "com.android.clockwork.healthservices.HealthService";
    private static final String SYSTEM_STATE_DISPLAY_SERVICE_CLASS =
            "com.android.clockwork.systemstatedisplay.SystemStateDisplayService";
    private static final String WEAR_DISPLAYOFFLOAD_SERVICE_CLASS =
            "com.android.clockwork.displayoffload.DisplayOffloadService";
    private static final String WEAR_MODE_SERVICE_CLASS =
            "com.android.clockwork.modes.ModeManagerService";
    private static final String WEAR_DISPLAY_SERVICE_CLASS =
            "com.android.clockwork.display.WearDisplayService";
    private static final String WEAR_DEBUG_SERVICE_CLASS =
            "com.android.clockwork.debug.WearDebugService";
    private static final String WEAR_TIME_SERVICE_CLASS =
            "com.android.clockwork.time.WearTimeService";
    private static final String WEAR_SETTINGS_SERVICE_CLASS =
            "com.android.clockwork.settings.WearSettingsService";
    private static final String WRIST_ORIENTATION_SERVICE_CLASS =
            "com.android.clockwork.wristorientation.WristOrientationService";
    private static final String ACCOUNT_SERVICE_CLASS =
            "com.android.server.accounts.AccountManagerService$Lifecycle";
    private static final String CONTENT_SERVICE_CLASS =
            "com.android.server.content.ContentService$Lifecycle";
    private static final String WALLPAPER_SERVICE_CLASS =
            "com.android.server.wallpaper.WallpaperManagerService$Lifecycle";
    private static final String AUTO_FILL_MANAGER_SERVICE_CLASS =
            "com.android.server.autofill.AutofillManagerService";
    private static final String CREDENTIAL_MANAGER_SERVICE_CLASS =
            "com.android.server.credentials.CredentialManagerService";
    private static final String CONTENT_CAPTURE_MANAGER_SERVICE_CLASS =
            "com.android.server.contentcapture.ContentCaptureManagerService";
    private static final String TRANSLATION_MANAGER_SERVICE_CLASS =
            "com.android.server.translation.TranslationManagerService";
    private static final String MUSIC_RECOGNITION_MANAGER_SERVICE_CLASS =
            "com.android.server.musicrecognition.MusicRecognitionManagerService";
    private static final String AMBIENT_CONTEXT_MANAGER_SERVICE_CLASS =
            "com.android.server.ambientcontext.AmbientContextManagerService";
    private static final String SYSTEM_CAPTIONS_MANAGER_SERVICE_CLASS =
            "com.android.server.systemcaptions.SystemCaptionsManagerService";
    private static final String TEXT_TO_SPEECH_MANAGER_SERVICE_CLASS =
            "com.android.server.texttospeech.TextToSpeechManagerService";
    private static final String IOT_SERVICE_CLASS =
            "com.android.things.server.IoTSystemService";
    private static final String SLICE_MANAGER_SERVICE_CLASS =
            "com.android.server.slice.SliceManagerService$Lifecycle";
    private static final String CAR_SERVICE_HELPER_SERVICE_CLASS =
            "com.android.internal.car.CarServiceHelperService";
    private static final String TIME_DETECTOR_SERVICE_CLASS =
            "com.android.server.timedetector.TimeDetectorService$Lifecycle";
    private static final String TIME_ZONE_DETECTOR_SERVICE_CLASS =
            "com.android.server.timezonedetector.TimeZoneDetectorService$Lifecycle";
    private static final String LOCATION_TIME_ZONE_MANAGER_SERVICE_CLASS =
            "com.android.server.timezonedetector.location.LocationTimeZoneManagerService$Lifecycle";
    private static final String GNSS_TIME_UPDATE_SERVICE_CLASS =
            "com.android.server.timedetector.GnssTimeUpdateService$Lifecycle";
    private static final String ACCESSIBILITY_MANAGER_SERVICE_CLASS =
            "com.android.server.accessibility.AccessibilityManagerService$Lifecycle";
    private static final String ADB_SERVICE_CLASS =
            "com.android.server.adb.AdbService$Lifecycle";
    private static final String SPEECH_RECOGNITION_MANAGER_SERVICE_CLASS =
            "com.android.server.speech.SpeechRecognitionManagerService";
    private static final String WALLPAPER_EFFECTS_GENERATION_MANAGER_SERVICE_CLASS =
            "com.android.server.wallpapereffectsgeneration.WallpaperEffectsGenerationManagerService";
    private static final String APP_PREDICTION_MANAGER_SERVICE_CLASS =
            "com.android.server.appprediction.AppPredictionManagerService";
    private static final String CONTENT_SUGGESTIONS_SERVICE_CLASS =
            "com.android.server.contentsuggestions.ContentSuggestionsManagerService";
    private static final String SEARCH_UI_MANAGER_SERVICE_CLASS =
            "com.android.server.searchui.SearchUiManagerService";
    private static final String SMARTSPACE_MANAGER_SERVICE_CLASS =
            "com.android.server.smartspace.SmartspaceManagerService";
    private static final String DEVICE_IDLE_CONTROLLER_CLASS =
            "com.android.server.DeviceIdleController";
    private static final String BLOB_STORE_MANAGER_SERVICE_CLASS =
            "com.android.server.blob.BlobStoreManagerService";
    private static final String APPSEARCH_MODULE_LIFECYCLE_CLASS =
            "com.android.server.appsearch.AppSearchModule$Lifecycle";
    private static final String ISOLATED_COMPILATION_SERVICE_CLASS =
            "com.android.server.compos.IsolatedCompilationService";
    private static final String ROLLBACK_MANAGER_SERVICE_CLASS =
            "com.android.server.rollback.RollbackManagerService";
    private static final String ALARM_MANAGER_SERVICE_CLASS =
            "com.android.server.alarm.AlarmManagerService";
    private static final String MEDIA_SESSION_SERVICE_CLASS =
            "com.android.server.media.MediaSessionService";
    private static final String MEDIA_RESOURCE_MONITOR_SERVICE_CLASS =
            "com.android.server.media.MediaResourceMonitorService";
    private static final String CONNECTIVITY_SERVICE_INITIALIZER_CLASS =
            "com.android.server.ConnectivityServiceInitializer";
    private static final String NETWORK_STATS_SERVICE_INITIALIZER_CLASS =
            "com.android.server.NetworkStatsServiceInitializer";
    private static final String IP_CONNECTIVITY_METRICS_CLASS =
            "com.android.server.connectivity.IpConnectivityMetrics";
    private static final String MEDIA_COMMUNICATION_SERVICE_CLASS =
            "com.android.server.media.MediaCommunicationService";
    private static final String APP_COMPAT_OVERRIDES_SERVICE_CLASS =
            "com.android.server.compat.overrides.AppCompatOverridesService$Lifecycle";
    private static final String HEALTHCONNECT_MANAGER_SERVICE_CLASS =
            "com.android.server.healthconnect.HealthConnectManagerService";
    private static final String ROLE_SERVICE_CLASS = "com.android.role.RoleService";
    private static final String GAME_MANAGER_SERVICE_CLASS =
            "com.android.server.app.GameManagerService$Lifecycle";
    private static final String ENHANCED_CONFIRMATION_SERVICE_CLASS =
            "com.android.ecm.EnhancedConfirmationService";

    private static final String UWB_APEX_SERVICE_JAR_PATH =
            "/apex/com.android.uwb/javalib/service-uwb.jar";
    private static final String UWB_SERVICE_CLASS = "com.android.server.uwb.UwbService";
    private static final String BLUETOOTH_APEX_SERVICE_JAR_PATH =
            "/apex/com.android.btservices/javalib/service-bluetooth.jar";
    private static final String BLUETOOTH_SERVICE_CLASS =
            "com.android.server.bluetooth.BluetoothService";
    private static final String SAFETY_CENTER_SERVICE_CLASS =
            "com.android.safetycenter.SafetyCenterService";

    private static final String SDK_SANDBOX_MANAGER_SERVICE_CLASS =
            "com.android.server.sdksandbox.SdkSandboxManagerService$Lifecycle";
    private static final String AD_SERVICES_MANAGER_SERVICE_CLASS =
            "com.android.server.adservices.AdServicesManagerService$Lifecycle";
    private static final String ON_DEVICE_PERSONALIZATION_SYSTEM_SERVICE_CLASS =
            "com.android.server.ondevicepersonalization."
                    + "OnDevicePersonalizationSystemService$Lifecycle";
    private static final String UPDATABLE_DEVICE_CONFIG_SERVICE_CLASS =
            "com.android.server.deviceconfig.DeviceConfigInit$Lifecycle";
    private static final String DEVICE_LOCK_SERVICE_CLASS =
            "com.android.server.devicelock.DeviceLockService";
    private static final String DEVICE_LOCK_APEX_PATH =
            "/apex/com.android.devicelock/javalib/service-devicelock.jar";

    private static final String PROFILING_SERVICE_LIFECYCLE_CLASS =
            "android.os.profiling.ProfilingService$Lifecycle";
    private static final String PROFILING_SERVICE_JAR_PATH =
            "/apex/com.android.profiling/javalib/service-profiling.jar";

    private static final String TETHERING_CONNECTOR_CLASS = "android.net.ITetheringConnector";

    private static final String PERSISTENT_DATA_BLOCK_PROP = "ro.frp.pst";

    private static final String UNCRYPT_PACKAGE_FILE = "/cache/recovery/uncrypt_file";
    private static final String BLOCK_MAP_FILE = "/cache/recovery/block.map";

    // maximum number of binder threads used for system_server
    // will be higher than the system default
    private static final int sMaxBinderThreads = 31;

    /**
     * Default theme used by the system context. This is used to style system-provided dialogs, such
     * as the Power Off dialog, and other visual content.
     */
    private static final int DEFAULT_SYSTEM_THEME =
            com.android.internal.R.style.Theme_DeviceDefault_System;

    private final int mFactoryTestMode;
    private Timer mProfilerSnapshotTimer;

    private Context mSystemContext;
    private SystemServiceManager mSystemServiceManager;

    // GammaOS Nano: monotonically increasing token bumped after the app label/icon
    // cache is (re)written, so the native nano menu can watch its property serial and
    // live-refresh the Applications list on install / remove / update.
    private final java.util.concurrent.atomic.AtomicInteger mNanoAppsGeneration =
            new java.util.concurrent.atomic.AtomicInteger(0);
    // GammaOS Nano: bumped after the installed-browser list is (re)written, so the native
    // menu can live-refresh its "Default Browser" picker on install / remove / update.
    private final java.util.concurrent.atomic.AtomicInteger mNanoBrowsersGeneration =
            new java.util.concurrent.atomic.AtomicInteger(0);
    // GammaOS Nano: bumped after the launchable-activity list is (re)written, so the
    // native menu's gamepad "Launch Activity" action picker can live-refresh.
    private final java.util.concurrent.atomic.AtomicInteger mNanoActivitiesGeneration =
            new java.util.concurrent.atomic.AtomicInteger(0);
    // GammaOS Nano: bumped after an on-demand app Information file is written, so the
    // native menu can watch its serial and swap "Loading..." for the real details.
    private final java.util.concurrent.atomic.AtomicInteger mNanoAppInfoGeneration =
            new java.util.concurrent.atomic.AtomicInteger(0);
    // GammaOS Nano: the app-info request (pkg#nonce) currently being shown, so an async
    // grant/revoke/clear can rewrite the same file the menu is still waiting on.
    private volatile String mNanoInfoReq = "";

    // TODO: remove all of these references by improving dependency resolution and boot phases
    private PowerManagerService mPowerManagerService;
    private ActivityManagerService mActivityManagerService;
    private WindowManagerGlobalLock mWindowManagerGlobalLock;
    private WebViewUpdateService mWebViewUpdateService;
    private DisplayManagerService mDisplayManagerService;
    private PackageManagerService mPackageManagerService;
    private PackageManager mPackageManager;
    private ContentResolver mContentResolver;
    private EntropyMixer mEntropyMixer;
    private DataLoaderManagerService mDataLoaderManagerService;
    private long mIncrementalServiceHandle = 0;

    private boolean mFirstBoot;
    private final int mStartCount;
    private final boolean mRuntimeRestart;
    private final long mRuntimeStartElapsedTime;
    private final long mRuntimeStartUptime;

    private static final String START_HIDL_SERVICES = "StartHidlServices";
    private static final String START_SENSOR_MANAGER_SERVICE = "StartISensorManagerService";
    private static final String START_BLOB_STORE_SERVICE = "startBlobStoreManagerService";

    private static final String SYSPROP_START_COUNT = "sys.system_server.start_count";
    private static final String SYSPROP_START_ELAPSED = "sys.system_server.start_elapsed";
    private static final String SYSPROP_START_UPTIME = "sys.system_server.start_uptime";

    private Future<?> mZygotePreload;

    private final SystemServerDumper mDumper = new SystemServerDumper();

    /**
     * The pending WTF to be logged into dropbox.
     */
    private static LinkedList<Pair<String, ApplicationErrorReport.CrashInfo>> sPendingWtfs;

    /** Start the IStats services. This is a blocking call and can take time. */
    private static native void startIStatsService();

    /** Start the ISensorManager service. This is a blocking call and can take time. */
    private static native void startISensorManagerService();

    /**
     * Start the memtrack proxy service.
     */
    private static native void startMemtrackProxyService();

    /**
     * Start all HIDL services that are run inside the system server. This may take some time.
     */
    private static native void startHidlServices();

    /**
     * Mark this process' heap as profileable. Only for debug builds.
     */
    private static native void initZygoteChildHeapProfiling();

    private static final String SYSPROP_FDTRACK_ENABLE_THRESHOLD =
            "persist.sys.debug.fdtrack_enable_threshold";
    private static final String SYSPROP_FDTRACK_ABORT_THRESHOLD =
            "persist.sys.debug.fdtrack_abort_threshold";
    private static final String SYSPROP_FDTRACK_INTERVAL =
            "persist.sys.debug.fdtrack_interval";

    private static int getMaxFd() {
        FileDescriptor fd = null;
        try {
            fd = Os.open("/dev/null", O_RDONLY | O_CLOEXEC, 0);
            return fd.getInt$();
        } catch (ErrnoException ex) {
            Slog.e("System", "Failed to get maximum fd: " + ex);
        } finally {
            if (fd != null) {
                try {
                    Os.close(fd);
                } catch (ErrnoException ex) {
                    // If Os.close threw, something went horribly wrong.
                    throw new RuntimeException(ex);
                }
            }
        }

        return Integer.MAX_VALUE;
    }

    private static native void fdtrackAbort();

    private static final File HEAP_DUMP_PATH = new File("/data/system/heapdump/");
    private static final int MAX_HEAP_DUMPS = 2;

    /**
     * Dump system_server's heap.
     *
     * For privacy reasons, these aren't automatically pulled into bugreports:
     * they must be manually pulled by the user.
     */
    private static void dumpHprof() {
        // hprof dumps are rather large, so ensure we don't fill the disk by generating
        // hundreds of these that will live forever.
        TreeSet<File> existingTombstones = new TreeSet<>();
        for (File file : HEAP_DUMP_PATH.listFiles()) {
            if (!file.isFile()) {
                continue;
            }
            if (!file.getName().startsWith("fdtrack-")) {
                continue;
            }
            existingTombstones.add(file);
        }
        if (existingTombstones.size() >= MAX_HEAP_DUMPS) {
            for (int i = 0; i < MAX_HEAP_DUMPS - 1; ++i) {
                // Leave the newest `MAX_HEAP_DUMPS - 1` tombstones in place.
                existingTombstones.pollLast();
            }
            for (File file : existingTombstones) {
                if (!file.delete()) {
                    Slog.w("System", "Failed to clean up hprof " + file);
                }
            }
        }

        try {
            String date = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
            String filename = "/data/system/heapdump/fdtrack-" + date + ".hprof";
            Debug.dumpHprofData(filename);
        } catch (IOException ex) {
            Slog.e("System", "Failed to dump fdtrack hprof", ex);
        }
    }

    /**
     * Spawn a thread that monitors for fd leaks.
     */
    private static void spawnFdLeakCheckThread() {
        final int enableThreshold = SystemProperties.getInt(SYSPROP_FDTRACK_ENABLE_THRESHOLD, 1600);
        final int abortThreshold = SystemProperties.getInt(SYSPROP_FDTRACK_ABORT_THRESHOLD, 3000);
        final int checkInterval = SystemProperties.getInt(SYSPROP_FDTRACK_INTERVAL, 120);

        new Thread(() -> {
            boolean enabled = false;
            long nextWrite = 0;

            while (true) {
                int maxFd = getMaxFd();
                if (maxFd > enableThreshold) {
                    // Do a manual GC to clean up fds that are hanging around as garbage.
                    System.gc();
                    System.runFinalization();
                    maxFd = getMaxFd();
                }

                if (maxFd > enableThreshold && !enabled) {
                    Slog.i("System", "fdtrack enable threshold reached, enabling");
                    FrameworkStatsLog.write(FrameworkStatsLog.FDTRACK_EVENT_OCCURRED,
                            FrameworkStatsLog.FDTRACK_EVENT_OCCURRED__EVENT__ENABLED,
                            maxFd);

                    System.loadLibrary("fdtrack");
                    enabled = true;
                } else if (maxFd > abortThreshold) {
                    Slog.i("System", "fdtrack abort threshold reached, dumping and aborting");
                    FrameworkStatsLog.write(FrameworkStatsLog.FDTRACK_EVENT_OCCURRED,
                            FrameworkStatsLog.FDTRACK_EVENT_OCCURRED__EVENT__ABORTING,
                            maxFd);

                    dumpHprof();
                    fdtrackAbort();
                } else {
                    // Limit this to once per hour.
                    long now = SystemClock.elapsedRealtime();
                    if (now > nextWrite) {
                        nextWrite = now + 60 * 60 * 1000;
                        FrameworkStatsLog.write(FrameworkStatsLog.FDTRACK_EVENT_OCCURRED,
                                enabled ? FrameworkStatsLog.FDTRACK_EVENT_OCCURRED__EVENT__ENABLED
                                        : FrameworkStatsLog.FDTRACK_EVENT_OCCURRED__EVENT__DISABLED,
                                maxFd);
                    }
                }

                try {
                    Thread.sleep(checkInterval * 1000);
                } catch (InterruptedException ex) {
                    continue;
                }
            }
        }).start();
    }

    /**
     * Start native Incremental Service and get its handle.
     */
    private static native long startIncrementalService();

    /**
     * Inform Incremental Service that system is ready.
     */
    private static native void setIncrementalServiceSystemReady(long incrementalServiceHandle);

    /**
     * The main entry point from zygote.
     */
    public static void main(String[] args) {
        new SystemServer().run();
    }

    public SystemServer() {
        // Check for factory test mode.
        mFactoryTestMode = FactoryTest.getMode();

        // Record process start information.
        mStartCount = SystemProperties.getInt(SYSPROP_START_COUNT, 0) + 1;
        mRuntimeStartElapsedTime = SystemClock.elapsedRealtime();
        mRuntimeStartUptime = SystemClock.uptimeMillis();
        Process.setStartTimes(mRuntimeStartElapsedTime, mRuntimeStartUptime,
                mRuntimeStartElapsedTime, mRuntimeStartUptime);

        // Remember if it's runtime restart or reboot.
        mRuntimeRestart = mStartCount > 1;
    }

    @Override
    public String getDumpableName() {
        return SystemServer.class.getSimpleName();
    }

    @Override
    public void dump(PrintWriter pw, String[] args) {
        pw.printf("Runtime restart: %b\n", mRuntimeRestart);
        pw.printf("Start count: %d\n", mStartCount);
        pw.print("Runtime start-up time: ");
        TimeUtils.formatDuration(mRuntimeStartUptime, pw); pw.println();
        pw.print("Runtime start-elapsed time: ");
        TimeUtils.formatDuration(mRuntimeStartElapsedTime, pw); pw.println();
    }

    /**
     * Service used to dump {@link SystemServer} state that is not associated with any service.
     *
     * <p>To dump all services:
     *
     * <pre><code>adb shell dumpsys system_server_dumper</code></pre>
     *
     * <p>To get a list of all services:
     *
     * <pre><code>adb shell dumpsys system_server_dumper --list</code></pre>
     *
     * <p>To dump a specific service (use {@code --list} above to get service names):
     *
     * <pre><code>adb shell dumpsys system_server_dumper --name NAME</code></pre>
     */
    private final class SystemServerDumper extends Binder {

        @GuardedBy("mDumpables")
        private final ArrayMap<String, Dumpable> mDumpables = new ArrayMap<>(4);

        @Override
        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            final boolean hasArgs = args != null && args.length > 0;

            synchronized (mDumpables) {
                if (hasArgs && "--list".equals(args[0])) {
                    final int dumpablesSize = mDumpables.size();
                    for (int i = 0; i < dumpablesSize; i++) {
                        pw.println(mDumpables.keyAt(i));
                    }
                    return;
                }

                if (hasArgs && "--name".equals(args[0])) {
                    if (args.length < 2) {
                        pw.println("Must pass at least one argument to --name");
                        return;
                    }
                    final String name = args[1];
                    final Dumpable dumpable = mDumpables.get(name);
                    if (dumpable == null) {
                        pw.printf("No dumpable named %s\n", name);
                        return;
                    }

                    try (IndentingPrintWriter ipw = new IndentingPrintWriter(pw, "  ")) {
                        // Strip --name DUMPABLE from args
                        final String[] actualArgs = Arrays.copyOfRange(args, 2, args.length);
                        dumpable.dump(ipw, actualArgs);
                    }
                    return;
                }

                final int dumpablesSize = mDumpables.size();
                try (IndentingPrintWriter ipw = new IndentingPrintWriter(pw, "  ")) {
                    for (int i = 0; i < dumpablesSize; i++) {
                        final Dumpable dumpable = mDumpables.valueAt(i);
                        ipw.printf("%s:\n", dumpable.getDumpableName());
                        ipw.increaseIndent();
                        dumpable.dump(ipw, args);
                        ipw.decreaseIndent();
                        ipw.println();
                    }
                }
            }
        }

        private void addDumpable(@NonNull Dumpable dumpable) {
            synchronized (mDumpables) {
                mDumpables.put(dumpable.getDumpableName(), dumpable);
            }
        }
    }

    private void run() {
        TimingsTraceAndSlog t = new TimingsTraceAndSlog();
        try {
            t.traceBegin("InitBeforeStartServices");

            // Record the process start information in sys props.
            SystemProperties.set(SYSPROP_START_COUNT, String.valueOf(mStartCount));
            SystemProperties.set(SYSPROP_START_ELAPSED, String.valueOf(mRuntimeStartElapsedTime));
            SystemProperties.set(SYSPROP_START_UPTIME, String.valueOf(mRuntimeStartUptime));

            EventLog.writeEvent(EventLogTags.SYSTEM_SERVER_START,
                    mStartCount, mRuntimeStartUptime, mRuntimeStartElapsedTime);

            // Set the device's time zone (a system property) if it is not set or is invalid.
            SystemTimeZone.initializeTimeZoneSettingsIfRequired();

            // If the system has "persist.sys.language" and friends set, replace them with
            // "persist.sys.locale". Note that the default locale at this point is calculated
            // using the "-Duser.locale" command line flag. That flag is usually populated by
            // AndroidRuntime using the same set of system properties, but only the system_server
            // and system apps are allowed to set them.
            //
            // NOTE: Most changes made here will need an equivalent change to
            // core/jni/AndroidRuntime.cpp
            if (!SystemProperties.get("persist.sys.language").isEmpty()) {
                final String languageTag = Locale.getDefault().toLanguageTag();

                SystemProperties.set("persist.sys.locale", languageTag);
                SystemProperties.set("persist.sys.language", "");
                SystemProperties.set("persist.sys.country", "");
                SystemProperties.set("persist.sys.localevar", "");
            }

            // The system server should never make non-oneway calls
            Binder.setWarnOnBlocking(true);
            // The system server should always load safe labels
            PackageItemInfo.forceSafeLabels();

            // Default to FULL within the system server.
            SQLiteGlobal.sDefaultSyncMode = SQLiteGlobal.SYNC_MODE_FULL;

            // Deactivate SQLiteCompatibilityWalFlags until settings provider is initialized
            SQLiteCompatibilityWalFlags.init(null);

            // Here we go!
            Slog.i(TAG, "Entered the Android system server!");
            final long uptimeMillis = SystemClock.elapsedRealtime();
            EventLog.writeEvent(EventLogTags.BOOT_PROGRESS_SYSTEM_RUN, uptimeMillis);
            if (!mRuntimeRestart) {
                FrameworkStatsLog.write(FrameworkStatsLog.BOOT_TIME_EVENT_ELAPSED_TIME_REPORTED,
                        FrameworkStatsLog
                                .BOOT_TIME_EVENT_ELAPSED_TIME__EVENT__SYSTEM_SERVER_INIT_START,
                        uptimeMillis);
            }

            // In case the runtime switched since last boot (such as when
            // the old runtime was removed in an OTA), set the system
            // property so that it is in sync. We can't do this in
            // libnativehelper's JniInvocation::Init code where we already
            // had to fallback to a different runtime because it is
            // running as root and we need to be the system user to set
            // the property. http://b/11463182
            SystemProperties.set("persist.sys.dalvik.vm.lib.2", VMRuntime.getRuntime().vmLibrary());

            // Mmmmmm... more memory!
            VMRuntime.getRuntime().clearGrowthLimit();

            // Some devices rely on runtime fingerprint generation, so make sure
            // we've defined it before booting further.
            Build.ensureFingerprintProperty();

            // Within the system server, it is an error to access Environment paths without
            // explicitly specifying a user.
            Environment.setUserRequired(true);

            // Within the system server, any incoming Bundles should be defused
            // to avoid throwing BadParcelableException.
            BaseBundle.setShouldDefuse(true);

            // Within the system server, when parceling exceptions, include the stack trace
            Parcel.setStackTraceParceling(true);

            // Ensure binder calls into the system always run at foreground priority.
            BinderInternal.disableBackgroundScheduling(true);

            // Increase the number of binder threads in system_server
            BinderInternal.setMaxThreads(sMaxBinderThreads);

            // Prepare the main looper thread (this thread).
            android.os.Process.setThreadPriority(
                    android.os.Process.THREAD_PRIORITY_FOREGROUND);
            android.os.Process.setCanSelfBackground(false);
            Looper.prepareMainLooper();
            Looper.getMainLooper().setSlowLogThresholdMs(
                    SLOW_DISPATCH_THRESHOLD_MS, SLOW_DELIVERY_THRESHOLD_MS);

            SystemServiceRegistry.sEnableServiceNotFoundWtf = true;

            // Initialize native services.
            System.loadLibrary("android_servers");

            // Allow heap / perf profiling.
            initZygoteChildHeapProfiling();

            // Debug builds - spawn a thread to monitor for fd leaks.
            if (Build.IS_ENG) {
                spawnFdLeakCheckThread();
            }

            // Check whether we failed to shut down last time we tried.
            // This call may not return.
            performPendingShutdown();

            // Initialize the system context.
            createSystemContext();

            // Call per-process mainline module initialization.
            ActivityThread.initializeMainlineModules();

            // Sets the dumper service
            ServiceManager.addService("system_server_dumper", mDumper);
            mDumper.addDumpable(this);

            // Create the system service manager.
            mSystemServiceManager = new SystemServiceManager(mSystemContext);
            mSystemServiceManager.setStartInfo(mRuntimeRestart,
                    mRuntimeStartElapsedTime, mRuntimeStartUptime);
            mDumper.addDumpable(mSystemServiceManager);

            LocalServices.addService(SystemServiceManager.class, mSystemServiceManager);
            // Prepare the thread pool for init tasks that can be parallelized
            SystemServerInitThreadPool tp = SystemServerInitThreadPool.start();
            mDumper.addDumpable(tp);

            // Lazily load the pre-installed system font map in SystemServer only if we're not doing
            // the optimized font loading in the FontManagerService.
            if (!com.android.text.flags.Flags.useOptimizedBoottimeFontLoading()
                    && Typeface.ENABLE_LAZY_TYPEFACE_INITIALIZATION) {
                Slog.i(TAG, "Loading pre-installed system font map.");
                Typeface.loadPreinstalledSystemFontMap();
            }

            // Attach JVMTI agent if this is a debuggable build and the system property is set.
            if (Build.IS_DEBUGGABLE) {
                // Property is of the form "library_path=parameters".
                String jvmtiAgent = SystemProperties.get("persist.sys.dalvik.jvmtiagent");
                if (!jvmtiAgent.isEmpty()) {
                    int equalIndex = jvmtiAgent.indexOf('=');
                    String libraryPath = jvmtiAgent.substring(0, equalIndex);
                    String parameterList =
                            jvmtiAgent.substring(equalIndex + 1, jvmtiAgent.length());
                    // Attach the agent.
                    try {
                        Debug.attachJvmtiAgent(libraryPath, parameterList, null);
                    } catch (Exception e) {
                        Slog.e("System", "*************************************************");
                        Slog.e("System", "********** Failed to load jvmti plugin: " + jvmtiAgent);
                    }
                }
            }
        } finally {
            t.traceEnd();  // InitBeforeStartServices
        }

        // Setup the default WTF handler
        RuntimeInit.setDefaultApplicationWtfHandler(SystemServer::handleEarlySystemWtf);

        // Start services.
        try {
            t.traceBegin("StartServices");
            startBootstrapServices(t);
            startCoreServices(t);
            startOtherServices(t);
            startApexServices(t);
            // Only update the timeout after starting all the services so that we use
            // the default timeout to start system server.
            updateWatchdogTimeout(t);
            CriticalEventLog.getInstance().logSystemServerStarted();
        } catch (Throwable ex) {
            Slog.e("System", "******************************************");
            Slog.e("System", "************ Failure starting system services", ex);
            throw ex;
        } finally {
            t.traceEnd(); // StartServices
        }

        StrictMode.initVmDefaults(null);

        if (!mRuntimeRestart && !isFirstBootOrUpgrade()) {
            final long uptimeMillis = SystemClock.elapsedRealtime();
            FrameworkStatsLog.write(FrameworkStatsLog.BOOT_TIME_EVENT_ELAPSED_TIME_REPORTED,
                    FrameworkStatsLog.BOOT_TIME_EVENT_ELAPSED_TIME__EVENT__SYSTEM_SERVER_READY,
                    uptimeMillis);
            final long maxUptimeMillis = 60 * 1000;
            if (uptimeMillis > maxUptimeMillis) {
                Slog.wtf(SYSTEM_SERVER_TIMING_TAG,
                        "SystemServer init took too long. uptimeMillis=" + uptimeMillis);
            }
        }

        // Set binder transaction callback after starting system services
        Binder.setTransactionCallback(new IBinderCallback() {
            @Override
            public void onTransactionError(int pid, int code, int flags, int err) {
                mActivityManagerService.frozenBinderTransactionDetected(pid, code, flags, err);
            }
        });

        // Loop forever.
        Looper.loop();
        throw new RuntimeException("Main thread loop unexpectedly exited");
    }

    private static boolean isValidTimeZoneId(String timezoneProperty) {
        return timezoneProperty != null
                && !timezoneProperty.isEmpty()
                && ZoneInfoDb.getInstance().hasTimeZone(timezoneProperty);
    }

    private boolean isFirstBootOrUpgrade() {
        return mPackageManagerService.isFirstBoot() || mPackageManagerService.isDeviceUpgrading();
    }

    private void reportWtf(String msg, Throwable e) {
        Slog.w(TAG, "***********************************************");
        Slog.wtf(TAG, "BOOT FAILURE " + msg, e);
    }

    private void performPendingShutdown() {
        final String shutdownAction = SystemProperties.get(
                ShutdownThread.SHUTDOWN_ACTION_PROPERTY, "");
        if (shutdownAction != null && shutdownAction.length() > 0) {
            boolean reboot = (shutdownAction.charAt(0) == '1');

            final String reason;
            if (shutdownAction.length() > 1) {
                reason = shutdownAction.substring(1, shutdownAction.length());
            } else {
                reason = null;
            }

            // If it's a pending reboot into recovery to apply an update,
            // always make sure uncrypt gets executed properly when needed.
            // If '/cache/recovery/block.map' hasn't been created, stop the
            // reboot which will fail for sure, and get a chance to capture a
            // bugreport when that's still feasible. (Bug: 26444951)
            if (reason != null && reason.startsWith(PowerManager.REBOOT_RECOVERY_UPDATE)) {
                File packageFile = new File(UNCRYPT_PACKAGE_FILE);
                if (packageFile.exists()) {
                    String filename = null;
                    try {
                        filename = FileUtils.readTextFile(packageFile, 0, null);
                    } catch (IOException e) {
                        Slog.e(TAG, "Error reading uncrypt package file", e);
                    }

                    if (filename != null && filename.startsWith("/data")) {
                        if (!new File(BLOCK_MAP_FILE).exists()) {
                            Slog.e(TAG, "Can't find block map file, uncrypt failed or " +
                                    "unexpected runtime restart?");
                            return;
                        }
                    }
                }
            }
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    ShutdownThread.rebootOrShutdown(null, reboot, reason);
                }
            };

            // ShutdownThread must run on a looper capable of displaying the UI.
            Message msg = Message.obtain(UiThread.getHandler(), runnable);
            msg.setAsynchronous(true);
            UiThread.getHandler().sendMessage(msg);

        }
    }

    private void createSystemContext() {
        ActivityThread activityThread = ActivityThread.systemMain();
        mSystemContext = activityThread.getSystemContext();
        mSystemContext.setTheme(DEFAULT_SYSTEM_THEME);

        final Context systemUiContext = activityThread.getSystemUiContext();
        systemUiContext.setTheme(DEFAULT_SYSTEM_THEME);
    }

    /**
     * Starts the small tangle of critical services that are needed to get the system off the
     * ground.  These services have complex mutual dependencies which is why we initialize them all
     * in one place here.  Unless your service is also entwined in these dependencies, it should be
     * initialized in one of the other functions.
     */
    private void startBootstrapServices(@NonNull TimingsTraceAndSlog t) {
        t.traceBegin("startBootstrapServices");

        t.traceBegin("ArtModuleServiceInitializer");
        // This needs to happen before DexUseManagerLocal init. We do it here to avoid colliding
        // with a GC. ArtModuleServiceInitializer is a class from a separate dex file
        // "service-art.jar", so referencing it involves the class linker. The class linker and the
        // GC are mutually exclusive (b/263486535). Therefore, we do this here to force trigger the
        // class linker earlier. If we did this later, especially after PackageManagerService init,
        // the class linker would be consistently blocked by a GC because PackageManagerService
        // allocates a lot of memory and almost certainly triggers a GC.
        ArtModuleServiceInitializer.setArtModuleServiceManager(new ArtModuleServiceManager());
        t.traceEnd();

        // Start the watchdog as early as possible so we can crash the system server
        // if we deadlock during early boot
        t.traceBegin("StartWatchdog");
        final Watchdog watchdog = Watchdog.getInstance();
        watchdog.start();
        mDumper.addDumpable(watchdog);
        t.traceEnd();

        Slog.i(TAG, "Reading configuration...");
        final String TAG_SYSTEM_CONFIG = "ReadingSystemConfig";
        t.traceBegin(TAG_SYSTEM_CONFIG);
        SystemServerInitThreadPool.submit(SystemConfig::getInstance, TAG_SYSTEM_CONFIG);
        t.traceEnd();

        // Platform compat service is used by ActivityManagerService, PackageManagerService, and
        // possibly others in the future. b/135010838.
        t.traceBegin("PlatformCompat");
        PlatformCompat platformCompat = new PlatformCompat(mSystemContext);
        ServiceManager.addService(Context.PLATFORM_COMPAT_SERVICE, platformCompat);
        ServiceManager.addService(Context.PLATFORM_COMPAT_NATIVE_SERVICE,
                new PlatformCompatNative(platformCompat));
        AppCompatCallbacks.install(new long[0]);
        t.traceEnd();

        // FileIntegrityService responds to requests from apps and the system. It needs to run after
        // the source (i.e. keystore) is ready, and before the apps (or the first customer in the
        // system) run.
        t.traceBegin("StartFileIntegrityService");
        mSystemServiceManager.startService(FileIntegrityService.class);
        t.traceEnd();

        // Wait for installd to finish starting up so that it has a chance to
        // create critical directories such as /data/user with the appropriate
        // permissions.  We need this to complete before we initialize other services.
        t.traceBegin("StartInstaller");
        Installer installer = mSystemServiceManager.startService(Installer.class);
        t.traceEnd();

        // In some cases after launching an app we need to access device identifiers,
        // therefore register the device identifier policy before the activity manager.
        t.traceBegin("DeviceIdentifiersPolicyService");
        mSystemServiceManager.startService(DeviceIdentifiersPolicyService.class);
        t.traceEnd();

        // Starts a service for reading runtime flag overrides, and keeping processes
        // in sync with one another.
        t.traceBegin("StartFeatureFlagsService");
        mSystemServiceManager.startService(FeatureFlagsService.class);
        t.traceEnd();

        // Uri Grants Manager.
        t.traceBegin("UriGrantsManagerService");
        mSystemServiceManager.startService(UriGrantsManagerService.Lifecycle.class);
        t.traceEnd();

        t.traceBegin("StartPowerStatsService");
        // Tracks rail data to be used for power statistics.
        mSystemServiceManager.startService(PowerStatsService.class);
        t.traceEnd();

        t.traceBegin("StartIStatsService");
        startIStatsService();
        t.traceEnd();

        // Start MemtrackProxyService before ActivityManager, so that early calls
        // to Memtrack::getMemory() don't fail.
        t.traceBegin("MemtrackProxyService");
        startMemtrackProxyService();
        t.traceEnd();

        // Start AccessCheckingService which provides new implementation for permission and app op.
        t.traceBegin("StartAccessCheckingService");
        LocalServices.addService(PermissionMigrationHelper.class,
                new PermissionMigrationHelperImpl());
        LocalServices.addService(AppOpMigrationHelper.class,
                new AppOpMigrationHelperImpl());
        mSystemServiceManager.startService(AccessCheckingService.class);
        t.traceEnd();

        // Activity manager runs the show.
        t.traceBegin("StartActivityManager");
        // TODO: Might need to move after migration to WM.
        ActivityTaskManagerService atm = mSystemServiceManager.startService(
                ActivityTaskManagerService.Lifecycle.class).getService();
        mActivityManagerService = ActivityManagerService.Lifecycle.startService(
                mSystemServiceManager, atm);
        mActivityManagerService.setSystemServiceManager(mSystemServiceManager);
        mActivityManagerService.setInstaller(installer);
        mWindowManagerGlobalLock = atm.getGlobalLock();
        t.traceEnd();

        // Data loader manager service needs to be started before package manager
        t.traceBegin("StartDataLoaderManagerService");
        mDataLoaderManagerService = mSystemServiceManager.startService(
                DataLoaderManagerService.class);
        t.traceEnd();

        // Incremental service needs to be started before package manager
        t.traceBegin("StartIncrementalService");
        mIncrementalServiceHandle = startIncrementalService();
        t.traceEnd();

        // Power manager needs to be started early because other services need it.
        // Native daemons may be watching for it to be registered so it must be ready
        // to handle incoming binder calls immediately (including being able to verify
        // the permissions for those calls).
        t.traceBegin("StartPowerManager");
        mPowerManagerService = mSystemServiceManager.startService(PowerManagerService.class);
        t.traceEnd();

        t.traceBegin("StartThermalManager");
        mSystemServiceManager.startService(ThermalManagerService.class);
        t.traceEnd();

        t.traceBegin("StartHintManager");
        mSystemServiceManager.startService(HintManagerService.class);
        t.traceEnd();

        // Now that the power manager has been started, let the activity manager
        // initialize power management features.
        t.traceBegin("InitPowerManagement");
        mActivityManagerService.initPowerManagement();
        t.traceEnd();

        // Bring up recovery system in case a rescue party needs a reboot
        t.traceBegin("StartRecoverySystemService");
        mSystemServiceManager.startService(RecoverySystemService.Lifecycle.class);
        t.traceEnd();

        // Now that we have the bare essentials of the OS up and running, take
        // note that we just booted, which might send out a rescue party if
        // we're stuck in a runtime restart loop.
        RescueParty.registerHealthObserver(mSystemContext);
        PackageWatchdog.getInstance(mSystemContext).noteBoot();

        // Manages LEDs and display backlight so we need it to bring up the display.
        t.traceBegin("StartLightsService");
        mSystemServiceManager.startService(LightsService.class);
        t.traceEnd();

        t.traceBegin("StartDisplayOffloadService");
        // Package manager isn't started yet; need to use SysProp not hardware feature
        if (SystemProperties.getBoolean("config.enable_display_offload", false)) {
            mSystemServiceManager.startService(WEAR_DISPLAYOFFLOAD_SERVICE_CLASS);
        }
        t.traceEnd();

        // Display manager is needed to provide display metrics before package manager
        // starts up.
        t.traceBegin("StartDisplayManager");
        mDisplayManagerService = mSystemServiceManager.startService(DisplayManagerService.class);
        t.traceEnd();

        // We need the default display before we can initialize the package manager.
        t.traceBegin("WaitForDisplay");
        mSystemServiceManager.startBootPhase(t, SystemService.PHASE_WAIT_FOR_DEFAULT_DISPLAY);
        t.traceEnd();

        // Start the package manager.
        if (!mRuntimeRestart) {
            FrameworkStatsLog.write(FrameworkStatsLog.BOOT_TIME_EVENT_ELAPSED_TIME_REPORTED,
                    FrameworkStatsLog
                            .BOOT_TIME_EVENT_ELAPSED_TIME__EVENT__PACKAGE_MANAGER_INIT_START,
                    SystemClock.elapsedRealtime());
        }

        t.traceBegin("StartDomainVerificationService");
        DomainVerificationService domainVerificationService = new DomainVerificationService(
                mSystemContext, SystemConfig.getInstance(), platformCompat);
        mSystemServiceManager.startService(domainVerificationService);
        t.traceEnd();

        t.traceBegin("StartPackageManagerService");
        try {
            Watchdog.getInstance().pauseWatchingCurrentThread("packagemanagermain");
            mPackageManagerService = PackageManagerService.main(
                    mSystemContext, installer, domainVerificationService,
                    mFactoryTestMode != FactoryTest.FACTORY_TEST_OFF);
        } finally {
            Watchdog.getInstance().resumeWatchingCurrentThread("packagemanagermain");
        }

        mFirstBoot = mPackageManagerService.isFirstBoot();
        mPackageManager = mSystemContext.getPackageManager();
        t.traceEnd();

        t.traceBegin("DexUseManagerLocal");
        // DexUseManagerLocal needs to be loaded after PackageManagerLocal has been registered, but
        // before PackageManagerService starts processing binder calls to notifyDexLoad.
        LocalManagerRegistry.addManager(
                DexUseManagerLocal.class, DexUseManagerLocal.createInstance(mSystemContext));
        t.traceEnd();

        if (!mRuntimeRestart && !isFirstBootOrUpgrade()) {
            FrameworkStatsLog.write(FrameworkStatsLog.BOOT_TIME_EVENT_ELAPSED_TIME_REPORTED,
                    FrameworkStatsLog
                            .BOOT_TIME_EVENT_ELAPSED_TIME__EVENT__PACKAGE_MANAGER_INIT_READY,
                    SystemClock.elapsedRealtime());
        }
        // Manages A/B OTA dexopting. This is a bootstrap service as we need it to rename
        // A/B artifacts after boot, before anything else might touch/need them.
        boolean disableOtaDexopt = SystemProperties.getBoolean("config.disable_otadexopt", false);
        if (!disableOtaDexopt) {
            t.traceBegin("StartOtaDexOptService");
            try {
                Watchdog.getInstance().pauseWatchingCurrentThread("moveab");
                OtaDexoptService.main(mSystemContext, mPackageManagerService);
            } catch (Throwable e) {
                reportWtf("starting OtaDexOptService", e);
            } finally {
                Watchdog.getInstance().resumeWatchingCurrentThread("moveab");
                t.traceEnd();
            }
        }

        if (Build.IS_ARC) {
            t.traceBegin("StartArcSystemHealthService");
            mSystemServiceManager.startService(ARC_SYSTEM_HEALTH_SERVICE);
            t.traceEnd();
        }

        t.traceBegin("StartUserManagerService");
        mSystemServiceManager.startService(UserManagerService.LifeCycle.class);
        t.traceEnd();

        // Initialize attribute cache used to cache resources from packages.
        t.traceBegin("InitAttributerCache");
        AttributeCache.init(mSystemContext);
        t.traceEnd();

        // Set up the Application instance for the system process and get started.
        t.traceBegin("SetSystemProcess");
        mActivityManagerService.setSystemProcess();
        t.traceEnd();

        // The package receiver depends on the activity service in order to get registered.
        platformCompat.registerPackageReceiver(mSystemContext);

        // Complete the watchdog setup with an ActivityManager instance and listen for reboots
        // Do this only after the ActivityManagerService is properly started as a system process
        t.traceBegin("InitWatchdog");
        watchdog.init(mSystemContext, mActivityManagerService);
        t.traceEnd();

        // DisplayManagerService needs to setup android.display scheduling related policies
        // since setSystemProcess() would have overridden policies due to setProcessGroup
        mDisplayManagerService.setupSchedulerPolicies();

        // Manages Overlay packages
        t.traceBegin("StartOverlayManagerService");
        mSystemServiceManager.startService(new OverlayManagerService(mSystemContext));
        t.traceEnd();

        // Manages Resources packages
        t.traceBegin("StartResourcesManagerService");
        ResourcesManagerService resourcesService = new ResourcesManagerService(mSystemContext);
        resourcesService.setActivityManagerService(mActivityManagerService);
        mSystemServiceManager.startService(resourcesService);
        t.traceEnd();

        t.traceBegin("StartSensorPrivacyService");
        mSystemServiceManager.startService(new SensorPrivacyService(mSystemContext));
        t.traceEnd();

        if (SystemProperties.getInt("persist.sys.displayinset.top", 0) > 0) {
            // DisplayManager needs the overlay immediately.
            mActivityManagerService.updateSystemUiContext();
            LocalServices.getService(DisplayManagerInternal.class).onOverlayChanged();
        }

        // The sensor service needs access to package manager service, app ops
        // service, and permissions service, therefore we start it after them.
        // GammaOS Nano: SensorService must be available even in minimal_boot.
        // SDL-based standalone emulators (vita3k, PPSSPP) call
        // ASensorManager_getInstance() during SDL_InitSubSystem, which blocks
        // forever in waitForSensorService() if the service is missing.
        t.traceBegin("StartSensorService");
        mSystemServiceManager.startService(SensorService.class);
        t.traceEnd();
        t.traceEnd(); // startBootstrapServices
    }

    /**
     * Starts some essential services that are not tangled up in the bootstrap process.
     */
    private void startCoreServices(@NonNull TimingsTraceAndSlog t) {
        t.traceBegin("startCoreServices");

        final boolean minimalBootEarly = SystemProperties.getBoolean(
                "sys.gammaos.minimal_boot", false);

        // Service for system config
        t.traceBegin("StartSystemConfigService");
        mSystemServiceManager.startService(SystemConfigService.class);
        t.traceEnd();

        t.traceBegin("StartBatteryService");
        // Tracks the battery level.  Requires LightService.
        mSystemServiceManager.startService(BatteryService.class);
        t.traceEnd();

        // Tracks application usage stats.
        t.traceBegin("StartUsageService");
        mSystemServiceManager.startService(UsageStatsService.class);
        mActivityManagerService.setUsageStatsManager(
                LocalServices.getService(UsageStatsManagerInternal.class));
        t.traceEnd();

        // Tracks whether the updatable WebView is in a ready state and watches for update installs.
        // GammaOS Nano: minimal_boot normally skips this to save memory, but the nano XMB
        // Internet Browser needs a WebView provider, so start it in minimal_boot too unless
        // persist.gammaos.nano.webview is explicitly turned off. The heavy renderer only
        // loads in app processes that use WebView (the browser, when open); idle cost here
        // is just the service + the shared relro.
        boolean webViewWanted = !minimalBootEarly
                || SystemProperties.getBoolean("persist.gammaos.nano.webview", true);
        if (webViewWanted && mPackageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)) {
            t.traceBegin("StartWebViewUpdateService");
            mWebViewUpdateService = mSystemServiceManager.startService(WebViewUpdateService.class);
            t.traceEnd();
        }

        if (!minimalBootEarly) {
        // Tracks and caches the device state.
        t.traceBegin("StartCachedDeviceStateService");
        mSystemServiceManager.startService(CachedDeviceStateService.class);
        t.traceEnd();

        // Tracks cpu time spent in binder calls
        t.traceBegin("StartBinderCallsStatsService");
        mSystemServiceManager.startService(BinderCallsStatsService.LifeCycle.class);
        t.traceEnd();

        // Tracks time spent in handling messages in handlers.
        t.traceBegin("StartLooperStatsService");
        mSystemServiceManager.startService(LooperStatsService.Lifecycle.class);
        t.traceEnd();

        // Manages apk rollbacks.
        t.traceBegin("StartRollbackManagerService");
        mSystemServiceManager.startService(ROLLBACK_MANAGER_SERVICE_CLASS);
        t.traceEnd();

        // Service to capture bugreports.
        t.traceBegin("StartBugreportManagerService");
        mSystemServiceManager.startService(BugreportManagerService.class);
        t.traceEnd();
        } // !minimalBootEarly: CachedDeviceState through Bugreport

        // NativeTombstoneManager must start even in nano boot — apps with ad SDKs
        // call getHistoricalProcessExitReasons() which NPEs without it.
        t.traceBegin("StartNativeTombstoneManagerService");
        mSystemServiceManager.startService(NativeTombstoneManagerService.class);
        t.traceEnd();

        // Service for GPU and GPU driver.
        t.traceBegin("GpuService");
        mSystemServiceManager.startService(GpuService.class);
        t.traceEnd();

        if (!minimalBootEarly) {
        // Handles system process requests for remotely provisioned keys & data.
        t.traceBegin("StartRemoteProvisioningService");
        mSystemServiceManager.startService(RemoteProvisioningService.class);
        t.traceEnd();
        } // !minimalBootEarly: RemoteProvisioning

        // TODO(b/277600174): Start CpuMonitorService on all builds and not just on debuggable
        // builds once the Android JobScheduler starts using this service.
        if (Build.IS_DEBUGGABLE || Build.IS_ENG) {
          // Service for CPU monitor.
          t.traceBegin("CpuMonitorService");
          mSystemServiceManager.startService(CpuMonitorService.class);
          t.traceEnd();
        }

        t.traceEnd(); // startCoreServices
    }

    /**
     * Starts a miscellaneous grab bag of stuff that has yet to be refactored and organized.
     */
    private void startOtherServices(@NonNull TimingsTraceAndSlog t) {
        t.traceBegin("startOtherServices");
        mSystemServiceManager.updateOtherServicesStartIndex();

        final Context context = mSystemContext;
        DynamicSystemService dynamicSystem = null;
        IStorageManager storageManager = null;
        NetworkManagementService networkManagement = null;
        VpnManagerService vpnManager = null;
        VcnManagementService vcnManagement = null;
        NetworkPolicyManagerService networkPolicy = null;
        WindowManagerService wm = null;
        NetworkTimeUpdateService networkTimeUpdater = null;
        InputManagerService inputManager = null;
        TelephonyRegistry telephonyRegistry = null;
        ConsumerIrService consumerIr = null;
        MmsServiceBroker mmsService = null;
        HardwarePropertiesManagerService hardwarePropertiesService = null;
        PacProxyService pacProxyService = null;

        boolean disableSystemTextClassifier = SystemProperties.getBoolean(
                "config.disable_systemtextclassifier", false);

        boolean disableNetworkTime = SystemProperties.getBoolean("config.disable_networktime",
                false);
        boolean disableCameraService = SystemProperties.getBoolean("config.disable_cameraservice",
                false);

        boolean isWatch = context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_WATCH);

        boolean isArc = context.getPackageManager().hasSystemFeature(
                "org.chromium.arc");

        boolean isTv = context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_LEANBACK);

        boolean enableVrService = context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE);

        // GammaOS Nano: minimal boot mode - skip non-essential services for direct app launch
        final boolean minimalBoot = SystemProperties.getBoolean(
                "sys.gammaos.minimal_boot", false);
        if (minimalBoot) {
            Slog.i(TAG, "GammaOS Nano: MINIMAL BOOT MODE - skipping non-essential services");
        }

        final boolean leanBoot = SystemProperties.getBoolean(
                "ro.gammaos.lean_boot", false);
        if (leanBoot) {
            Slog.i(TAG, "GammaOS Core: LEAN BOOT MODE - skipping non-essential services");
        }

        // For debugging RescueParty
        if (Build.IS_DEBUGGABLE && SystemProperties.getBoolean("debug.crash_system", false)) {
            throw new RuntimeException();
        }

        try {
            final String SECONDARY_ZYGOTE_PRELOAD = "SecondaryZygotePreload";
            // We start the preload ~1s before the webview factory preparation, to
            // ensure that it completes before the 32 bit relro process is forked
            // from the zygote. In the event that it takes too long, the webview
            // RELRO process will block, but it will do so without holding any locks.
            if (!minimalBoot) {
            mZygotePreload = SystemServerInitThreadPool.submit(() -> {
                try {
                    Slog.i(TAG, SECONDARY_ZYGOTE_PRELOAD);
                    TimingsTraceAndSlog traceLog = TimingsTraceAndSlog.newAsyncLog();
                    traceLog.traceBegin(SECONDARY_ZYGOTE_PRELOAD);
                    String[] abis32 = Build.SUPPORTED_32_BIT_ABIS;
                    if (abis32.length > 0 && !Process.ZYGOTE_PROCESS.preloadDefault(abis32[0])) {
                        Slog.e(TAG, "Unable to preload default resources for secondary");
                    }
                    traceLog.traceEnd();
                } catch (Exception ex) {
                    Slog.e(TAG, "Exception preloading default resources", ex);
                }
            }, SECONDARY_ZYGOTE_PRELOAD);
            } // !minimalBoot

            t.traceBegin("StartKeyAttestationApplicationIdProviderService");
            ServiceManager.addService("sec_key_att_app_id_provider",
                    new KeyAttestationApplicationIdProviderService(context));
            t.traceEnd();

            t.traceBegin("StartKeyChainSystemService");
            mSystemServiceManager.startService(KeyChainSystemService.class);
            t.traceEnd();

            // GammaOS Nano: skip binary transparency in nano mode. It hashes system
            // binaries at boot_completed (a measurement/attestation job, not latency
            // critical) and drives a large system_server heap spike during the nano
            // warmup window. Normal Android still starts it.
            if (!minimalBoot) {
                t.traceBegin("StartBinaryTransparencyService");
                mSystemServiceManager.startService(BinaryTransparencyService.class);
                t.traceEnd();
            }

            t.traceBegin("StartSchedulingPolicyService");
            ServiceManager.addService("scheduling_policy", new SchedulingPolicyService());
            t.traceEnd();

            // TelecomLoader hooks into classes with defined HFP logic,
            // so check for either telephony or microphone.
            if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
                    || mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELECOM)
                    || mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                t.traceBegin("StartTelecomLoaderService");
                mSystemServiceManager.startService(TelecomLoaderService.class);
                t.traceEnd();
            }

            t.traceBegin("StartTelephonyRegistry");
            telephonyRegistry = new TelephonyRegistry(
                    context, new TelephonyRegistry.ConfigurationProvider());
            ServiceManager.addService("telephony.registry", telephonyRegistry);
            t.traceEnd();

            t.traceBegin("StartEntropyMixer");
            mEntropyMixer = new EntropyMixer(context);
            t.traceEnd();

            mContentResolver = context.getContentResolver();

            // The AccountManager must come before the ContentService
            t.traceBegin("StartAccountManagerService");
            mSystemServiceManager.startService(ACCOUNT_SERVICE_CLASS);
            t.traceEnd();

            t.traceBegin("StartContentService");
            mSystemServiceManager.startService(CONTENT_SERVICE_CLASS);
            t.traceEnd();

            t.traceBegin("InstallSystemProviders");
            mActivityManagerService.getContentProviderHelper().installSystemProviders();
            // Device configuration used to be part of System providers
            mSystemServiceManager.startService(UPDATABLE_DEVICE_CONFIG_SERVICE_CLASS);
            // Now that SettingsProvider is ready, reactivate SQLiteCompatibilityWalFlags
            SQLiteCompatibilityWalFlags.reset();
            t.traceEnd();

            // Records errors and logs, for example wtf()
            // Currently this service indirectly depends on SettingsProvider so do this after
            // InstallSystemProviders.
            t.traceBegin("StartDropBoxManager");
            mSystemServiceManager.startService(DropBoxManagerService.class);
            t.traceEnd();

            if (android.permission.flags.Flags.enhancedConfirmationModeApisEnabled()) {
                t.traceBegin("StartEnhancedConfirmationService");
                mSystemServiceManager.startService(ENHANCED_CONFIRMATION_SERVICE_CLASS);
                t.traceEnd();
            }

            // Grants default permissions and defines roles
            t.traceBegin("StartRoleManagerService");
            LocalManagerRegistry.addManager(RoleServicePlatformHelper.class,
                    new RoleServicePlatformHelperImpl(mSystemContext));
            mSystemServiceManager.startService(ROLE_SERVICE_CLASS);
            t.traceEnd();

            // Start unconditionally: our nano target is a Leanback/TV product
            // (isTv == true) but the handheld has a real vibrator, so the stock
            // !isTv gate would wrongly skip haptics.
            {
                t.traceBegin("StartVibratorManagerService");
                mSystemServiceManager.startService(VibratorManagerService.Lifecycle.class);
                t.traceEnd();
            }

            t.traceBegin("StartDynamicSystemService");
            dynamicSystem = new DynamicSystemService(context);
            ServiceManager.addService("dynamic_system", dynamicSystem);
            t.traceEnd();

            if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CONSUMER_IR)) {
                t.traceBegin("StartConsumerIrService");
                consumerIr = new ConsumerIrService(context);
                ServiceManager.addService(Context.CONSUMER_IR_SERVICE, consumerIr);
                t.traceEnd();
            }

            // TODO(aml-jobscheduler): Think about how to do it properly.
            t.traceBegin("StartResourceEconomy");
            mSystemServiceManager.startService(RESOURCE_ECONOMY_SERVICE_CLASS);
            t.traceEnd();

            // TODO(aml-jobscheduler): Think about how to do it properly.
            t.traceBegin("StartAlarmManagerService");
            mSystemServiceManager.startService(ALARM_MANAGER_SERVICE_CLASS);
            t.traceEnd();

            t.traceBegin("StartInputManagerService");
            inputManager = new InputManagerService(context);
            t.traceEnd();

            t.traceBegin("DeviceStateManagerService");
            mSystemServiceManager.startService(DeviceStateManagerService.class);
            t.traceEnd();

            if (!disableCameraService && !minimalBoot) {
                t.traceBegin("StartCameraServiceProxy");
                mSystemServiceManager.startService(CameraServiceProxy.class);
                t.traceEnd();
            }

            t.traceBegin("StartWindowManagerService");
            // WMS needs sensor service ready
            if (!minimalBoot) {
                mSystemServiceManager.startBootPhase(t, SystemService.PHASE_WAIT_FOR_SENSOR_SERVICE);
            }
            wm = WindowManagerService.main(context, inputManager, !mFirstBoot,
                    new PhoneWindowManager(), mActivityManagerService.mActivityTaskManager);
            ServiceManager.addService(Context.WINDOW_SERVICE, wm, /* allowIsolated= */ false,
                    DUMP_FLAG_PRIORITY_CRITICAL | DUMP_FLAG_PROTO);
            ServiceManager.addService(Context.INPUT_SERVICE, inputManager,
                    /* allowIsolated= */ false, DUMP_FLAG_PRIORITY_CRITICAL);
            t.traceEnd();

            t.traceBegin("SetWindowManagerService");
            mActivityManagerService.setWindowManager(wm);
            t.traceEnd();

            t.traceBegin("WindowManagerServiceOnInitReady");
            wm.onInitReady();
            t.traceEnd();

            // Start receiving calls from SensorManager services. Start in a separate thread
            // because it need to connect to SensorManager. This has to start
            // after PHASE_WAIT_FOR_SENSOR_SERVICE is done.
            SystemServerInitThreadPool.submit(() -> {
                TimingsTraceAndSlog traceLog = TimingsTraceAndSlog.newAsyncLog();
                traceLog.traceBegin(START_SENSOR_MANAGER_SERVICE);
                startISensorManagerService();
                traceLog.traceEnd();
            }, START_SENSOR_MANAGER_SERVICE);

            SystemServerInitThreadPool.submit(() -> {
                TimingsTraceAndSlog traceLog = TimingsTraceAndSlog.newAsyncLog();
                traceLog.traceBegin(START_HIDL_SERVICES);
                startHidlServices();
                traceLog.traceEnd();
            }, START_HIDL_SERVICES);

            if (!isWatch && enableVrService) {
                t.traceBegin("StartVrManagerService");
                mSystemServiceManager.startService(VrManagerService.class);
                t.traceEnd();
            }

            t.traceBegin("StartInputManager");
            inputManager.setWindowManagerCallbacks(wm.getInputManagerCallback());
            inputManager.start();
            t.traceEnd();

            // TODO: Use service dependencies instead.
            t.traceBegin("DisplayManagerWindowManagerAndInputReady");
            mDisplayManagerService.windowManagerAndInputReady();
            t.traceEnd();

            // ======================================================================
            // GammaOS Nano: In minimal boot mode, skip everything from here to the
            // systemReady callback except AudioService. This eliminates ~120 services
            // that are not needed for a single-app (RetroArch) launch.
            // ======================================================================
            if (minimalBoot) {
                Slog.i(TAG, "GammaOS Nano: minimal boot - skipping Bluetooth and heavy services");
            }

            if (!minimalBoot) {
                if (mFactoryTestMode == FactoryTest.FACTORY_TEST_LOW_LEVEL) {
                    Slog.i(TAG, "No Bluetooth Service (factory test)");
                } else if (!context.getPackageManager().hasSystemFeature
                        (PackageManager.FEATURE_BLUETOOTH)) {
                    Slog.i(TAG, "No Bluetooth Service (Bluetooth Hardware Not Present)");
                } else {
                    t.traceBegin("StartBluetoothService");
                    mSystemServiceManager.startServiceFromJar(BLUETOOTH_SERVICE_CLASS,
                        BLUETOOTH_APEX_SERVICE_JAR_PATH);
                    t.traceEnd();
                }
            }

            if (!minimalBoot) {
            t.traceBegin("IpConnectivityMetrics");
            mSystemServiceManager.startService(IP_CONNECTIVITY_METRICS_CLASS);
            t.traceEnd();

            t.traceBegin("NetworkWatchlistService");
            mSystemServiceManager.startService(NetworkWatchlistService.Lifecycle.class);
            t.traceEnd();

            t.traceBegin("PinnerService");
            mSystemServiceManager.startService(PinnerService.class);
            t.traceEnd();

            if (Build.IS_DEBUGGABLE && ProfcollectForwardingService.enabled()) {
                t.traceBegin("ProfcollectForwardingService");
                mSystemServiceManager.startService(ProfcollectForwardingService.class);
                t.traceEnd();
            }

            t.traceBegin("SignedConfigService");
            SignedConfigService.registerUpdateReceiver(mSystemContext);
            t.traceEnd();

            t.traceBegin("AppIntegrityService");
            mSystemServiceManager.startService(AppIntegrityManagerService.class);
            t.traceEnd();

            t.traceBegin("StartLogcatManager");
            mSystemServiceManager.startService(LogcatManagerService.class);
            t.traceEnd();
            } // !minimalBoot: IpConnectivity through Logcat

            if (minimalBoot) {
                // logd's reader thread calls waitForService("logcat") when a
                // privileged app (GMS, etc.) connects to /dev/socket/logdr.
                // Without LogcatManagerService that call blocks forever while
                // holding logd_lock, permanently breaking logcat.
                t.traceBegin("StartLogcatManager");
                mSystemServiceManager.startService(LogcatManagerService.class);
                t.traceEnd();
            }

        } catch (Throwable e) {
            Slog.e("System", "******************************************");
            Slog.e("System", "************ Failure starting core service");
            throw e;
        }

        // Before things start rolling, be sure we have decided whether
        // we are in safe mode.
        final boolean safeMode = minimalBoot ? false : wm.detectSafeMode();
        if (safeMode) {
            // If yes, immediately turn on the global setting for airplane mode.
            // Note that this does not send broadcasts at this stage because
            // subsystems are not yet up. We will send broadcasts later to ensure
            // all listeners have the chance to react with special handling.
            Settings.Global.putInt(context.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 1);
        } else if (context.getResources().getBoolean(R.bool.config_autoResetAirplaneMode)) {
            Settings.Global.putInt(context.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0);
        }

        StatusBarManagerService statusBar = null;

        INotificationManager notification = null;
        CountryDetectorService countryDetector = null;
        ILockSettings lockSettings = null;
        MediaRouterService mediaRouter = null;
        DevicePolicyManagerService.Lifecycle dpms = null;
        HsumBootUserInitializer hsumBootUserInitializer = null;

        // InputMethodManagerService is required even in nano mode (apps need IInputMethodManager)
        if (mFactoryTestMode != FactoryTest.FACTORY_TEST_LOW_LEVEL) {
            t.traceBegin("StartInputMethodManagerLifecycle");
            String immsClassName = context.getResources().getString(
                    R.string.config_deviceSpecificInputMethodManagerService);
            if (immsClassName.isEmpty()) {
                mSystemServiceManager.startService(InputMethodManagerService.Lifecycle.class);
            } else {
                try {
                    Slog.i(TAG, "Starting custom IMMS: " + immsClassName);
                    mSystemServiceManager.startService(immsClassName);
                } catch (Throwable e) {
                    reportWtf("starting " + immsClassName, e);
                }
            }
            t.traceEnd();
        }

        // Bring up remaining UI services (not needed in nano mode).
        if (!minimalBoot && mFactoryTestMode != FactoryTest.FACTORY_TEST_LOW_LEVEL) {
            t.traceBegin("StartAccessibilityManagerService");
            try {
                mSystemServiceManager.startService(ACCESSIBILITY_MANAGER_SERVICE_CLASS);
            } catch (Throwable e) {
                reportWtf("starting Accessibility Manager", e);
            }
            t.traceEnd();
        }

        t.traceBegin("MakeDisplayReady");
        try {
            wm.displayReady();
        } catch (Throwable e) {
            reportWtf("making display ready", e);
        }
        t.traceEnd();

        if (mFactoryTestMode != FactoryTest.FACTORY_TEST_LOW_LEVEL) {
            if (!"0".equals(SystemProperties.get("system_init.startmountservice"))) {
                t.traceBegin("StartStorageManagerService");
                try {
                    /*
                     * NotificationManagerService is dependant on StorageManagerService,
                     * (for media / usb notifications) so we must start StorageManagerService first.
                     */
                    mSystemServiceManager.startService(STORAGE_MANAGER_SERVICE_CLASS);
                    storageManager = IStorageManager.Stub.asInterface(
                            ServiceManager.getService("mount"));
                } catch (Throwable e) {
                    reportWtf("starting StorageManagerService", e);
                }
                t.traceEnd();

                t.traceBegin("StartStorageStatsService");
                try {
                    mSystemServiceManager.startService(STORAGE_STATS_SERVICE_CLASS);
                } catch (Throwable e) {
                    reportWtf("starting StorageStatsService", e);
                }
                t.traceEnd();
            }
        }

        // UiModeManager must start even in nano boot — many apps call
        // UiModeManager.getCurrentModeType() in onCreate and crash if it's null.
        t.traceBegin("StartUiModeManager");
        mSystemServiceManager.startService(UiModeManagerService.class);
        t.traceEnd();

        // LocaleManagerService + GrammaticalInflectionService must start even in nano
        // boot (like UiModeManager above). Apps resolve getSystemService(LocaleManager.class)
        // / GrammaticalInflectionManager and crash if the service is not published: Disney+
        // does a Kotlin non-null cast on LocaleManager and dies with a NullPointerException
        // ("null cannot be cast to non-null type android.app.LocaleManager") on minimal boot.
        // Both are lightweight per-app-language framework services (a binder publish plus a
        // package monitor), so starting them adds negligible boot time.
        t.traceBegin("StartLocaleManagerService");
        try {
            mSystemServiceManager.startService(LocaleManagerService.class);
        } catch (Throwable e) {
            reportWtf("starting LocaleManagerService service", e);
        }
        t.traceEnd();

        t.traceBegin("StartGrammarInflectionService");
        try {
            mSystemServiceManager.startService(GrammaticalInflectionService.class);
        } catch (Throwable e) {
            reportWtf("starting GrammarInflectionService service", e);
        }
        t.traceEnd();

        // GammaOS Nano: AppHibernationService must run even in minimal_boot. TvSettings'
        // per-app management screen (ForceStopPreference) resolves AppHibernationManager and
        // NPE-crashed when the service was absent in nano mode (e.g. uninstalling an app from
        // the nano home). It is lightweight, so start it in both modes; the ForceStopPreference
        // null-guard remains as defense.
        t.traceBegin("StartAppHibernationService");
        mSystemServiceManager.startService(APP_HIBERNATION_SERVICE_CLASS);
        t.traceEnd();

        if (!minimalBoot) {
        t.traceBegin("ArtManagerLocal");
        DexOptHelper.initializeArtManagerLocal(context, mPackageManagerService);
        t.traceEnd();

        t.traceBegin("UpdatePackagesIfNeeded");
        try {
            Watchdog.getInstance().pauseWatchingCurrentThread("dexopt");
            mPackageManagerService.updatePackagesIfNeeded();
        } catch (Throwable e) {
            reportWtf("update packages", e);
        } finally {
            Watchdog.getInstance().resumeWatchingCurrentThread("dexopt");
        }
        t.traceEnd();

        t.traceBegin("PerformFstrimIfNeeded");
        try {
            mPackageManagerService.performFstrimIfNeeded();
        } catch (Throwable e) {
            reportWtf("performing fstrim", e);
        }
        t.traceEnd();
        } // !minimalBoot: Locale through Fstrim

        if (mFactoryTestMode == FactoryTest.FACTORY_TEST_LOW_LEVEL) {
            // dpms already null
        } else {
            t.traceBegin("StartLockSettingsService");
            try {
                mSystemServiceManager.startService(LOCK_SETTINGS_SERVICE_CLASS);
                lockSettings = ILockSettings.Stub.asInterface(
                        ServiceManager.getService("lock_settings"));
            } catch (Throwable e) {
                reportWtf("starting LockSettingsService service", e);
            }
            t.traceEnd();

            // GammaOS Nano: make LockSettings ready and unlock CE immediately,
            // before AudioService (which takes ~1s).  In normal boot, this
            // happens much later at MakeLockSettingsServiceReady.
            if (minimalBoot && lockSettings != null) {
                t.traceBegin("MakeLockSettingsServiceReady_Nano");
                try {
                    lockSettings.systemReady();
                } catch (Throwable e) {
                    reportWtf("making Lock Settings Service ready (nano)", e);
                }
                t.traceEnd();

                try {
                    com.android.internal.widget.LockPatternUtils lockPatternUtils =
                            new com.android.internal.widget.LockPatternUtils(context);
                    lockPatternUtils.unlockUserKeyIfUnsecured(
                            android.os.UserHandle.USER_SYSTEM);
                    Slog.i(TAG, "GammaOS Nano: early CE unlock (right after LockSettingsStart)");
                } catch (Exception e) {
                    Slog.w(TAG, "GammaOS Nano: early CE unlock failed: " + e);
                }

                // GammaOS Nano: If DE cache exists and QR prepared, set up bind
                // mounts over CE paths and trigger early RetroArch launch.
                // This runs on a background thread so AudioService can proceed
                // on the main thread in parallel.
                new Thread(() -> {
                    try {
                        if (!"1".equals(SystemProperties.get(
                                "persist.gammaos.nano.qr_prepared", "0"))) {
                            Slog.i(TAG, "GammaOS Nano: no QR prepared, skipping cache mount");
                            return;
                        }
                        // GammaOS: Drastic QR handles its own handoff
                        // from NanoMenu's render loop (do_launch when
                        // the fade completes). This early-launch path
                        // is only for libretro QR → RetroArch. Skip
                        // entirely for drastic so we don't set
                        // bootanim.exit=1 (which tells RWC "nano has
                        // exited") while NanoMenu is still rendering
                        // the drastic preview.
                        if ("drastic".equals(SystemProperties.get(
                                "persist.gammaos.nano.qr_core", ""))) {
                            Slog.i(TAG, "GammaOS Nano: drastic QR active, "
                                    + "skipping early RetroArch launch");
                            return;
                        }
                        java.io.File manifest = new java.io.File(
                                "/data/system/nano_cache/manifest");
                        if (!manifest.exists()) {
                            Slog.i(TAG, "GammaOS Nano: no cache manifest, skipping mount");
                            return;
                        }
                        // Wait for CE paths to be accessible (fscrypt key just installed).
                        // No FUSE wait needed — we pass DE cache paths directly in
                        // the intent, bypassing FUSE entirely.
                        for (int i = 0; i < 20; i++) {
                            if (new java.io.File("/data/user/0").canRead()) break;
                            try { Thread.sleep(100); } catch (InterruptedException ie) {}
                        }
                        if (!new java.io.File("/data/user/0").canRead()) {
                            Slog.w(TAG, "GammaOS Nano: CE paths not accessible, skipping");
                            return;
                        }
                        // Cache exists and CE is ready. The native libretro
                        // runner in NanoMenu reads from cache directly — no
                        // need to set cache_mounted (which would cause RA to
                        // use DE cache paths instead of real FUSE paths).
                        Slog.i(TAG, "GammaOS Nano: DE cache verified, "
                                + "native libretro will use it directly");
                        // DE cache verified. Do NOT set bootanim.exit here --
                        // NanoMenu IS the bootanim, and it needs to keep
                        // rendering QR until the user either unpauses (fires
                        // handoff) or cancels. Setting bootanim.exit=1 early
                        // would flip startHomeOnTaskDisplayArea's "nano has
                        // exited" gate and cause RetroArch to launch behind
                        // the still-running NanoMenu, which races the QR
                        // handoff and produces a black screen on the primary.
                        // The handoff path in NanoMenu will set
                        // nano_retroarch=1 -> init.rc sets bootanim.exit=1
                        // at the right moment.
                        Slog.i(TAG, "GammaOS Nano: DE cache verified, "
                                + "awaiting do_launch from NanoMenu");
                    } catch (Exception e) {
                        Slog.w(TAG, "GammaOS Nano: early cache mount failed: " + e);
                    }
                }, "NanoCacheEarlyLaunch").start();
            }

            // FontManagerService must start before any UI dialog (including nano mode power menu).
            // GammaOS Nano: in minimal_boot, defer to a background thread since QR doesn't
            // need fonts until user opens power menu — save ~1.9s on critical path.
            if (minimalBoot) {
                final Context fontCtx = context;
                final boolean fontSafeMode = safeMode;
                new Thread(() -> {
                    try {
                        mSystemServiceManager.startService(
                                new FontManagerService.Lifecycle(fontCtx, fontSafeMode));
                        Slog.i(TAG, "GammaOS Nano: FontManagerService started (deferred)");
                    } catch (Throwable thr) {
                        Slog.w(TAG, "GammaOS Nano: deferred FontManagerService failed", thr);
                    }
                }, "NanoFontDefer").start();
            } else {
                t.traceBegin("StartFontManagerService");
                mSystemServiceManager.startService(new FontManagerService.Lifecycle(context, safeMode));
                t.traceEnd();
            }

            // GammaOS Nano: DeviceIdleController must start even in minimal boot —
            // NetworkPolicyManager (needed by ConnectivityService) depends on it.
            t.traceBegin("StartDeviceIdleController");
            mSystemServiceManager.startService(DEVICE_IDLE_CONTROLLER_CLASS);
            t.traceEnd();

            if (!minimalBoot) { // GammaOS Nano: skip PersistentDataBlock through Smartspace
            final boolean hasPdb = !SystemProperties.get(PERSISTENT_DATA_BLOCK_PROP).equals("");
            if (hasPdb) {
                t.traceBegin("StartPersistentDataBlock");
                mSystemServiceManager.startService(PersistentDataBlockService.class);
                t.traceEnd();
            }

            if (Build.IS_ARC && SystemProperties.getInt("ro.boot.dev_mode", 0) == 1) {
                t.traceBegin("StartArcPersistentDataBlock");
                mSystemServiceManager.startService(ARC_PERSISTENT_DATA_BLOCK_SERVICE_CLASS);
                t.traceEnd();
            }

            t.traceBegin("StartTestHarnessMode");
            mSystemServiceManager.startService(TestHarnessModeService.class);
            t.traceEnd();

            if (hasPdb || OemLockService.isHalPresent()) {
                // Implementation depends on pdb or the OemLock HAL
                t.traceBegin("StartOemLockService");
                mSystemServiceManager.startService(OemLockService.class);
                t.traceEnd();
            }

            // DeviceIdleController already started above (before !minimalBoot block)

            // Always start the Device Policy Manager, so that the API is compatible with
            // API8.
            t.traceBegin("StartDevicePolicyManager");
            dpms = mSystemServiceManager.startService(DevicePolicyManagerService.Lifecycle.class);
            t.traceEnd();

            t.traceBegin("StartStatusBarManagerService");
            try {
                statusBar = new StatusBarManagerService(context);
                statusBar.publishGlobalActionsProvider();
                ServiceManager.addService(Context.STATUS_BAR_SERVICE, statusBar, false,
                        DUMP_FLAG_PRIORITY_NORMAL | DUMP_FLAG_PROTO);
            } catch (Throwable e) {
                reportWtf("starting StatusBarManagerService", e);
            }
            t.traceEnd();

            if (deviceHasConfigString(context,
                    R.string.config_defaultMusicRecognitionService)) {
                t.traceBegin("StartMusicRecognitionManagerService");
                mSystemServiceManager.startService(MUSIC_RECOGNITION_MANAGER_SERVICE_CLASS);
                t.traceEnd();
            } else {
                Slog.d(TAG,
                        "MusicRecognitionManagerService not defined by OEM or disabled by flag");
            }

            startContentCaptureService(context, t);
            startAttentionService(context, t);
            startRotationResolverService(context, t);
            startSystemCaptionsManagerService(context, t);
            startTextToSpeechManagerService(context, t);
            startWearableSensingService(t);

            if (deviceHasConfigString(
                    context, R.string.config_defaultAmbientContextDetectionService)) {
                t.traceBegin("StartAmbientContextService");
                mSystemServiceManager.startService(AMBIENT_CONTEXT_MANAGER_SERVICE_CLASS);
                t.traceEnd();
            } else {
                Slog.d(TAG, "AmbientContextManagerService not defined by OEM or disabled by flag");
            }

            // System Speech Recognition Service
            t.traceBegin("StartSpeechRecognitionManagerService");
            mSystemServiceManager.startService(SPEECH_RECOGNITION_MANAGER_SERVICE_CLASS);
            t.traceEnd();

            // App prediction manager service
            if (deviceHasConfigString(context, R.string.config_defaultAppPredictionService)) {
                t.traceBegin("StartAppPredictionService");
                mSystemServiceManager.startService(APP_PREDICTION_MANAGER_SERVICE_CLASS);
                t.traceEnd();
            } else {
                Slog.d(TAG, "AppPredictionService not defined by OEM");
            }

            // Content suggestions manager service
            if (deviceHasConfigString(context, R.string.config_defaultContentSuggestionsService)) {
                t.traceBegin("StartContentSuggestionsService");
                mSystemServiceManager.startService(CONTENT_SUGGESTIONS_SERVICE_CLASS);
                t.traceEnd();
            } else {
                Slog.d(TAG, "ContentSuggestionsService not defined by OEM");
            }

            // Search UI manager service
            if (deviceHasConfigString(context, R.string.config_defaultSearchUiService)) {
                t.traceBegin("StartSearchUiService");
                mSystemServiceManager.startService(SEARCH_UI_MANAGER_SERVICE_CLASS);
                t.traceEnd();
            }

            // Smartspace manager service
            if (deviceHasConfigString(context, R.string.config_defaultSmartspaceService)) {
                t.traceBegin("StartSmartspaceService");
                mSystemServiceManager.startService(SMARTSPACE_MANAGER_SERVICE_CLASS);
                t.traceEnd();
            } else {
                Slog.d(TAG, "SmartspaceManagerService not defined by OEM or disabled by flag");
            }
            } // !minimalBoot: PersistentDataBlock through Smartspace

            // GammaOS Nano: start the Device Policy Manager in minimal boot too, and
            // crucially BEFORE NotificationManagerService below. Full boot starts it
            // unconditionally inside the block above; in minimal boot it was only
            // started later, from the WiFi opt-in block, which runs after NMS.
            // NotificationManagerService.onStart builds VisibilityExtractor, which
            // caches DevicePolicyManager at construction time, so a missing or late
            // device_policy binder leaves that cache null and notification ranking
            // NPEs on the first ranked notification. Starting it here keeps the
            // binder present when NMS captures it.
            if (minimalBoot && dpms == null) {
                t.traceBegin("StartDevicePolicyManager");
                try {
                    dpms = mSystemServiceManager.startService(
                            DevicePolicyManagerService.Lifecycle.class);
                } catch (Throwable e) {
                    Slog.w(TAG, "GammaOS Nano: DevicePolicyManager failed", e);
                }
                t.traceEnd();
            }

            // GammaOS Nano: NotificationManager must start even in minimal boot —
            // many apps need it for foreground services (notification channels).
            if (minimalBoot) {
                t.traceBegin("StartNotificationManager");
                try {
                    mSystemServiceManager.startService(NotificationManagerService.class);
                    SystemNotificationChannels.removeDeprecated(context);
                    SystemNotificationChannels.createAll(context);
                } catch (Throwable e) {
                    Slog.w(TAG, "GammaOS Nano: NotificationManager failed", e);
                }
                t.traceEnd();
            }

            if (minimalBoot) {
                // GammaOS Nano: start ConnectivityService and its dependency chain
                // on a background thread — apps like Firefox and Daijisho call
                // getSystemService(CONNECTIVITY_SERVICE) in onCreate() and NPE if
                // it's null.  DeviceIdleController is started above so
                // NetworkPolicyManager can be constructed.
                final Context nanoCtx = context;
                new Thread(() -> {
                    try {
                        Slog.i(TAG, "GammaOS Nano: starting connectivity stack (bg)");
                        ConnectivityModuleConnector.getInstance().init(nanoCtx);
                        NetworkStackClient.getInstance().init();
                        NetworkManagementService nm = NetworkManagementService.create(nanoCtx);
                        ServiceManager.addService(Context.NETWORKMANAGEMENT_SERVICE, nm);
                        mSystemServiceManager.startServiceFromJar(
                                NETWORK_STATS_SERVICE_INITIALIZER_CLASS,
                                CONNECTIVITY_SERVICE_APEX_PATH);
                        NetworkPolicyManagerService np = new NetworkPolicyManagerService(
                                nanoCtx, mActivityManagerService, nm);
                        ServiceManager.addService(Context.NETWORK_POLICY_SERVICE, np);
                        mSystemServiceManager.startServiceFromJar(
                                CONNECTIVITY_SERVICE_INITIALIZER_CLASS,
                                CONNECTIVITY_SERVICE_APEX_PATH);
                        np.bindConnectivityManager();
                        Slog.i(TAG, "GammaOS Nano: connectivity stack ready");
                    } catch (Throwable e) {
                        Slog.e(TAG, "GammaOS Nano: connectivity stack failed", e);
                    }
                }, "NanoConnectivity").start();
            }

            if (!minimalBoot) {
            t.traceBegin("InitConnectivityModuleConnector");
            try {
                ConnectivityModuleConnector.getInstance().init(context);
            } catch (Throwable e) {
                reportWtf("initializing ConnectivityModuleConnector", e);
            }
            t.traceEnd();

            t.traceBegin("InitNetworkStackClient");
            try {
                NetworkStackClient.getInstance().init();
            } catch (Throwable e) {
                reportWtf("initializing NetworkStackClient", e);
            }
            t.traceEnd();

            t.traceBegin("StartNetworkManagementService");
            try {
                networkManagement = NetworkManagementService.create(context);
                ServiceManager.addService(Context.NETWORKMANAGEMENT_SERVICE, networkManagement);
            } catch (Throwable e) {
                reportWtf("starting NetworkManagement Service", e);
            }
            t.traceEnd();

            t.traceBegin("StartTextServicesManager");
            mSystemServiceManager.startService(TextServicesManagerService.Lifecycle.class);
            t.traceEnd();

            if (!disableSystemTextClassifier) {
                t.traceBegin("StartTextClassificationManagerService");
                mSystemServiceManager
                        .startService(TextClassificationManagerService.Lifecycle.class);
                t.traceEnd();
            }

            t.traceBegin("StartNetworkScoreService");
            mSystemServiceManager.startService(NetworkScoreService.Lifecycle.class);
            t.traceEnd();

            t.traceBegin("StartNetworkStatsService");
            // This has to be called before NetworkPolicyManager because NetworkPolicyManager
            // needs to take NetworkStatsService to initialize.
            mSystemServiceManager.startServiceFromJar(NETWORK_STATS_SERVICE_INITIALIZER_CLASS,
                    CONNECTIVITY_SERVICE_APEX_PATH);
            t.traceEnd();

            t.traceBegin("StartNetworkPolicyManagerService");
            try {
                networkPolicy = new NetworkPolicyManagerService(context, mActivityManagerService,
                        networkManagement);
                ServiceManager.addService(Context.NETWORK_POLICY_SERVICE, networkPolicy);
            } catch (Throwable e) {
                reportWtf("starting NetworkPolicy Service", e);
            }
            t.traceEnd();

            if (context.getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_WIFI)) {
                // Wifi Service must be started first for wifi-related services.
                if (!isArc) {
                    t.traceBegin("StartWifi");
                    mSystemServiceManager.startServiceFromJar(
                            WIFI_SERVICE_CLASS, WIFI_APEX_SERVICE_JAR_PATH);
                    t.traceEnd();
                    t.traceBegin("StartWifiScanning");
                    mSystemServiceManager.startServiceFromJar(
                            WIFI_SCANNING_SERVICE_CLASS, WIFI_APEX_SERVICE_JAR_PATH);
                    t.traceEnd();
                }
            }

            // ARC - ArcNetworkService registers the ARC network stack and replaces the
            // stock WiFi service in both ARC++ container and ARCVM. Always starts the ARC network
            // stack regardless of whether FEATURE_WIFI is enabled/disabled (b/254755875).
            if (isArc) {
                t.traceBegin("StartArcNetworking");
                mSystemServiceManager.startService(ARC_NETWORK_SERVICE_CLASS);
                t.traceEnd();
            }

            if (context.getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_WIFI_RTT)) {
                t.traceBegin("StartRttService");
                mSystemServiceManager.startServiceFromJar(
                        WIFI_RTT_SERVICE_CLASS, WIFI_APEX_SERVICE_JAR_PATH);
                t.traceEnd();
            }

            if (context.getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_WIFI_AWARE)) {
                t.traceBegin("StartWifiAware");
                mSystemServiceManager.startServiceFromJar(
                        WIFI_AWARE_SERVICE_CLASS, WIFI_APEX_SERVICE_JAR_PATH);
                t.traceEnd();
            }

            if (context.getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_WIFI_DIRECT)) {
                t.traceBegin("StartWifiP2P");
                mSystemServiceManager.startServiceFromJar(
                        WIFI_P2P_SERVICE_CLASS, WIFI_APEX_SERVICE_JAR_PATH);
                t.traceEnd();
            }

            if (context.getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_LOWPAN)) {
                t.traceBegin("StartLowpan");
                mSystemServiceManager.startService(LOWPAN_SERVICE_CLASS);
                t.traceEnd();
            }

            t.traceBegin("StartPacProxyService");
            try {
                pacProxyService = new PacProxyService(context);
                ServiceManager.addService(Context.PAC_PROXY_SERVICE, pacProxyService);
            } catch (Throwable e) {
                reportWtf("starting PacProxyService", e);
            }
            t.traceEnd();

            t.traceBegin("StartConnectivityService");
            // This has to be called after NetworkManagementService, NetworkStatsService
            // and NetworkPolicyManager because ConnectivityService needs to take these
            // services to initialize.
            mSystemServiceManager.startServiceFromJar(CONNECTIVITY_SERVICE_INITIALIZER_CLASS,
                    CONNECTIVITY_SERVICE_APEX_PATH);
            networkPolicy.bindConnectivityManager();
            t.traceEnd();
            } // !minimalBoot: full networking stack

            if (!minimalBoot) { // GammaOS Nano: skip SecurityState through WallpaperEffects
            t.traceBegin("StartSecurityStateManagerService");
            try {
                ServiceManager.addService(Context.SECURITY_STATE_SERVICE,
                        new SecurityStateManagerService(context));
            } catch (Throwable e) {
                reportWtf("starting SecurityStateManagerService", e);
            }
            t.traceEnd();

            t.traceBegin("StartVpnManagerService");
            try {
                vpnManager = VpnManagerService.create(context);
                ServiceManager.addService(Context.VPN_MANAGEMENT_SERVICE, vpnManager);
            } catch (Throwable e) {
                reportWtf("starting VPN Manager Service", e);
            }
            t.traceEnd();

            t.traceBegin("StartVcnManagementService");
            try {
                vcnManagement = VcnManagementService.create(context);
                ServiceManager.addService(Context.VCN_MANAGEMENT_SERVICE, vcnManagement);
            } catch (Throwable e) {
                reportWtf("starting VCN Management Service", e);
            }
            t.traceEnd();

            t.traceBegin("StartSystemUpdateManagerService");
            try {
                ServiceManager.addService(Context.SYSTEM_UPDATE_SERVICE,
                        new SystemUpdateManagerService(context));
            } catch (Throwable e) {
                reportWtf("starting SystemUpdateManagerService", e);
            }
            t.traceEnd();

            t.traceBegin("StartUpdateLockService");
            try {
                ServiceManager.addService(Context.UPDATE_LOCK_SERVICE,
                        new UpdateLockService(context));
            } catch (Throwable e) {
                reportWtf("starting UpdateLockService", e);
            }
            t.traceEnd();

            t.traceBegin("StartNotificationManager");
            mSystemServiceManager.startService(NotificationManagerService.class);
            SystemNotificationChannels.removeDeprecated(context);
            SystemNotificationChannels.createAll(context);
            notification = INotificationManager.Stub.asInterface(
                    ServiceManager.getService(Context.NOTIFICATION_SERVICE));
            t.traceEnd();

            t.traceBegin("StartDeviceMonitor");
            mSystemServiceManager.startService(DeviceStorageMonitorService.class);
            t.traceEnd();

            t.traceBegin("StartTimeDetectorService");
            try {
                mSystemServiceManager.startService(TIME_DETECTOR_SERVICE_CLASS);
            } catch (Throwable e) {
                reportWtf("starting TimeDetectorService service", e);
            }
            t.traceEnd();

            t.traceBegin("StartLocationManagerService");
            mSystemServiceManager.startService(LocationManagerService.Lifecycle.class);
            t.traceEnd();

            t.traceBegin("StartCountryDetectorService");
            try {
                countryDetector = new CountryDetectorService(context);
                ServiceManager.addService(Context.COUNTRY_DETECTOR, countryDetector);
            } catch (Throwable e) {
                reportWtf("starting Country Detector", e);
            }
            t.traceEnd();

            t.traceBegin("StartTimeZoneDetectorService");
            try {
                mSystemServiceManager.startService(TIME_ZONE_DETECTOR_SERVICE_CLASS);
            } catch (Throwable e) {
                reportWtf("starting TimeZoneDetectorService service", e);
            }
            t.traceEnd();

            t.traceBegin("StartAltitudeService");
            try {
                mSystemServiceManager.startService(AltitudeService.Lifecycle.class);
            } catch (Throwable e) {
                reportWtf("starting AltitudeService service", e);
            }
            t.traceEnd();

            t.traceBegin("StartLocationTimeZoneManagerService");
            try {
                mSystemServiceManager.startService(LOCATION_TIME_ZONE_MANAGER_SERVICE_CLASS);
            } catch (Throwable e) {
                reportWtf("starting LocationTimeZoneManagerService service", e);
            }
            t.traceEnd();

            if (context.getResources().getBoolean(R.bool.config_enableGnssTimeUpdateService)) {
                t.traceBegin("StartGnssTimeUpdateService");
                try {
                    mSystemServiceManager.startService(GNSS_TIME_UPDATE_SERVICE_CLASS);
                } catch (Throwable e) {
                    reportWtf("starting GnssTimeUpdateService service", e);
                }
                t.traceEnd();
            }

            if (!isWatch) {
                t.traceBegin("StartSearchManagerService");
                try {
                    mSystemServiceManager.startService(SEARCH_MANAGER_SERVICE_CLASS);
                } catch (Throwable e) {
                    reportWtf("starting Search Service", e);
                }
                t.traceEnd();
            }

            if (context.getResources().getBoolean(R.bool.config_enableWallpaperService)) {
                t.traceBegin("StartWallpaperManagerService");
                mSystemServiceManager.startService(WALLPAPER_SERVICE_CLASS);
                t.traceEnd();
            }

            // WallpaperEffectsGeneration manager service
            if (deviceHasConfigString(context,
                R.string.config_defaultWallpaperEffectsGenerationService)) {
                t.traceBegin("StartWallpaperEffectsGenerationService");
                mSystemServiceManager.startService(
                    WALLPAPER_EFFECTS_GENERATION_MANAGER_SERVICE_CLASS);
                t.traceEnd();
            }
            } // !minimalBoot: SecurityState through WallpaperEffects

            if (minimalBoot) {
                Slog.i(TAG, "GammaOS Nano: starting AudioService (cache signal runs in parallel)");
            }
            {
                t.traceBegin("StartAudioService");
                if (!isArc) {
                    mSystemServiceManager.startService(AudioService.Lifecycle.class);
                } else {
                    String className = context.getResources()
                            .getString(R.string.config_deviceSpecificAudioService);
                    try {
                        mSystemServiceManager.startService(className + "$Lifecycle");
                    } catch (Throwable e) {
                        reportWtf("starting " + className, e);
                    }
                }
                t.traceEnd();
            }

            // GammaOS Nano: wired/USB headset detection (WiredAccessoryManager) normally starts
            // in the skipped !minimalBoot block below, so in minimal_boot a 3.5mm headset insert
            // (SW_HEADPHONE_INSERT) was never reported to the audio policy and playback stayed on
            // the speaker. Start it here too (same code the full boot runs), right after
            // AudioService so the policy can act on the headset state.
            if (minimalBoot && !isWatch) {
                t.traceBegin("StartWiredAccessoryManager-minimal");
                try {
                    inputManager.setWiredAccessoryCallbacks(
                            new WiredAccessoryManager(context, inputManager));
                } catch (Throwable e) {
                    reportWtf("starting WiredAccessoryManager (minimal)", e);
                }
                t.traceEnd();
            }

            if (!minimalBoot) { // GammaOS Nano: skip SoundTrigger through MIDI
            t.traceBegin("StartSoundTriggerMiddlewareService");
            mSystemServiceManager.startService(SoundTriggerMiddlewareService.Lifecycle.class);
            t.traceEnd();

            if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_BROADCAST_RADIO)) {
                t.traceBegin("StartBroadcastRadioService");
                mSystemServiceManager.startService(BroadcastRadioService.class);
                t.traceEnd();
            }

            if (!isTv) {
                t.traceBegin("StartDockObserver");
                mSystemServiceManager.startService(DockObserver.class);
                t.traceEnd();
            }

            if (isWatch) {
                t.traceBegin("StartThermalObserver");
                mSystemServiceManager.startService(THERMAL_OBSERVER_CLASS);
                t.traceEnd();
            }

            if (!isWatch) {
                t.traceBegin("StartWiredAccessoryManager");
                try {
                    // Listen for wired headset changes
                    inputManager.setWiredAccessoryCallbacks(
                            new WiredAccessoryManager(context, inputManager));
                } catch (Throwable e) {
                    reportWtf("starting WiredAccessoryManager", e);
                }
                t.traceEnd();
            }

            if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
                // Start MIDI Manager service
                t.traceBegin("StartMidiManager");
                mSystemServiceManager.startService(MIDI_SERVICE_CLASS);
                t.traceEnd();
            }
            } // !minimalBoot: SoundTrigger through MIDI

            // Start ADB Debugging Service
            t.traceBegin("StartAdbService");
            try {
                mSystemServiceManager.startService(ADB_SERVICE_CLASS);
            } catch (Throwable e) {
                Slog.e(TAG, "Failure starting AdbService");
            }
            t.traceEnd();

            if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
                    || mPackageManager.hasSystemFeature(
                    PackageManager.FEATURE_USB_ACCESSORY)
                    || Build.IS_EMULATOR) {
                // Manage USB host and device support
                t.traceBegin("StartUsbService");
                mSystemServiceManager.startService(USB_SERVICE_CLASS);
                t.traceEnd();
            }

            // GammaOS Nano: opt-in WiFi + Bluetooth startup in minimal_boot.
            // Enable with persist.gammaos.nano.wifi=1 on a booted device.
            //
            // Placement is AFTER UsbService so adb has a chance to come up
            // before any WiFi dependency can crash.
            //
            // Dependency chain that must be satisfied before WifiService can
            // start safely (all of these are in the `!minimalBoot` block
            // above, so we re-create them here when the gate is on):
            //   - StatsCompanion  (WifiService.handleBootCompleted calls
            //     StatsManager.setPullAtomCallback unconditionally)
            //   - LocationManagerService  (WifiPermissionsUtil.isLocationModeEnabled
            //     throws ServiceNotFoundException("location") without it)
            //   - CountryDetectorService  (WiFi regulatory domain lookup)
            //
            // Synchronous on the main thread because WifiService's
            // PhoneStateListener captures Looper.myLooper() and because
            // SystemServiceManager.sealStartedServices() runs later.
            if (minimalBoot && SystemProperties.getBoolean(
                    "persist.gammaos.nano.wifi", true)) {
                // Install a tolerant uncaught-exception handler on android.bg
                // for nano WiFi/BT startup.  The location + wifi + bluetooth
                // stacks post callbacks onto android.bg that may throw
                // DeadSystemException when a sibling service (gnss HAL,
                // FusedLocation bind target, etc.) isn't up in minimal boot.
                // Default RuntimeInit.KillApplicationHandler would kill
                // system_server, causing zygote restart and a bootloop.  We
                // log and swallow instead.
                try {
                    Thread androidBg = BackgroundThread.get();
                    final Thread.UncaughtExceptionHandler chain =
                            androidBg.getUncaughtExceptionHandler();
                    androidBg.setUncaughtExceptionHandler((th, ex) -> {
                        Throwable cursor = ex;
                        while (cursor != null) {
                            if (cursor instanceof android.os.DeadSystemException
                                    || cursor instanceof android.os.DeadObjectException) {
                                Slog.w(TAG, "GammaOS Nano: swallowed "
                                        + cursor.getClass().getSimpleName()
                                        + " on " + th.getName(), ex);
                                return;
                            }
                            cursor = cursor.getCause();
                        }
                        if (chain != null) {
                            chain.uncaughtException(th, ex);
                        }
                    });
                    Slog.i(TAG, "GammaOS Nano: android.bg tolerant handler armed");
                } catch (Throwable e) {
                    Slog.e(TAG, "GammaOS Nano: failed to arm android.bg handler", e);
                }
                try {
                    Slog.i(TAG, "GammaOS Nano: starting StatsCompanion");
                    mSystemServiceManager.startServiceFromJar(
                            STATS_COMPANION_LIFECYCLE_CLASS,
                            STATS_COMPANION_APEX_PATH);
                    mSystemServiceManager.startService(
                            STATS_PULL_ATOM_SERVICE_CLASS);
                    Slog.i(TAG, "GammaOS Nano: StatsCompanion ready");
                } catch (Throwable e) {
                    Slog.e(TAG, "GammaOS Nano: StatsCompanion failed", e);
                }
                try {
                    Slog.i(TAG, "GammaOS Nano: starting LocationManagerService");
                    mSystemServiceManager.startService(
                            LocationManagerService.Lifecycle.class);
                    Slog.i(TAG, "GammaOS Nano: LocationManagerService ready");
                } catch (Throwable e) {
                    Slog.e(TAG, "GammaOS Nano: LocationManagerService failed", e);
                }
                try {
                    Slog.i(TAG, "GammaOS Nano: starting CountryDetectorService");
                    countryDetector = new CountryDetectorService(context);
                    ServiceManager.addService(Context.COUNTRY_DETECTOR,
                            countryDetector);
                    Slog.i(TAG, "GammaOS Nano: CountryDetectorService ready");
                } catch (Throwable e) {
                    Slog.e(TAG, "GammaOS Nano: CountryDetectorService failed", e);
                }
                // WifiService.ClientModeImpl.L2ConnectedState.enter() fetches
                // VcnManager via getSystemService(), which requireNonNull()'s
                // the binder and NPE-crashes WifiHandlerThread (uncaught,
                // kills system_server) if VcnManagementService isn't
                // published in ServiceManager.  Publish the binder here so
                // the constructor succeeds; systemReady() needs
                // ConnectivityManager which the NanoConnectivity bg thread
                // brings up asynchronously, so we defer it until the binder
                // is actually queried.
                try {
                    Slog.i(TAG, "GammaOS Nano: starting VcnManagementService");
                    vcnManagement = VcnManagementService.create(context);
                    ServiceManager.addService(Context.VCN_MANAGEMENT_SERVICE,
                            vcnManagement);
                    Slog.i(TAG, "GammaOS Nano: VcnManagementService ready");
                } catch (Throwable e) {
                    Slog.e(TAG, "GammaOS Nano: VcnManagementService failed", e);
                }
                try {
                    if (context.getPackageManager().hasSystemFeature(
                            PackageManager.FEATURE_WIFI)) {
                        Slog.i(TAG, "GammaOS Nano: starting WiFi stack");
                        mSystemServiceManager.startServiceFromJar(
                                WIFI_SERVICE_CLASS, WIFI_APEX_SERVICE_JAR_PATH);
                        mSystemServiceManager.startServiceFromJar(
                                WIFI_SCANNING_SERVICE_CLASS,
                                WIFI_APEX_SERVICE_JAR_PATH);
                        Slog.i(TAG, "GammaOS Nano: WiFi stack ready");
                    }
                } catch (Throwable e) {
                    Slog.e(TAG, "GammaOS Nano: WiFi stack failed", e);
                }
                // com.android.bluetooth.btservice.AdapterService.onCreate()
                // resolves CompanionDeviceManager and DevicePolicyManager via
                // getNonNullSystemService() and NPE-crashes (repeatedly) if
                // either binder isn't published yet, which leaves the BT
                // stack in a perpetual "enabling" state that never
                // transitions to ON. Start both here so the adapter
                // onCreate() can complete.
                try {
                    if (context.getPackageManager().hasSystemFeature(
                            PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) {
                        Slog.i(TAG, "GammaOS Nano: starting CompanionDeviceManager");
                        mSystemServiceManager.startService(
                                COMPANION_DEVICE_MANAGER_SERVICE_CLASS);
                        Slog.i(TAG, "GammaOS Nano: CompanionDeviceManager ready");
                    }
                } catch (Throwable e) {
                    Slog.e(TAG, "GammaOS Nano: CompanionDeviceManager failed", e);
                }
                // DevicePolicyManager is now started earlier (before NMS); only
                // start it here if that earlier start did not run, so the BT
                // adapter's onCreate() still finds the binder without us
                // double-registering the service.
                if (dpms == null) {
                    try {
                        Slog.i(TAG, "GammaOS Nano: starting DevicePolicyManager");
                        dpms = mSystemServiceManager.startService(
                                DevicePolicyManagerService.Lifecycle.class);
                        Slog.i(TAG, "GammaOS Nano: DevicePolicyManager ready");
                    } catch (Throwable e) {
                        Slog.e(TAG, "GammaOS Nano: DevicePolicyManager failed", e);
                    }
                }
                // MediaSessionService: BT's AvrcpTargetService calls
                // MediaSessionManager.addOnActiveSessionsChangedListener
                // during startProfileServices(), which NPEs when no
                // ISessionManager is registered. Without this the BT
                // stack crashes immediately after turning on, so
                // pairing and scanning never work.
                try {
                    Slog.i(TAG, "GammaOS Nano: starting MediaSessionService");
                    mSystemServiceManager.startService(
                            MEDIA_SESSION_SERVICE_CLASS);
                    Slog.i(TAG, "GammaOS Nano: MediaSessionService ready");
                } catch (Throwable e) {
                    Slog.e(TAG, "GammaOS Nano: MediaSessionService failed", e);
                }
                // MediaRouterService: apps that resolve getSystemService(MEDIA_ROUTER_SERVICE)
                // (Disney+ does during startup, for cast/route selection) crash inside
                // MediaRouter's constructor with a NullPointerException on IMediaRouterService
                // when the media_router service is not registered. Start it in nano boot too
                // (full boot starts it below in the !minimalBoot block); assigning mediaRouter
                // here keeps the later MakeMediaRouterServiceReady/systemRunning call valid.
                try {
                    Slog.i(TAG, "GammaOS Nano: starting MediaRouterService");
                    mediaRouter = new MediaRouterService(context);
                    ServiceManager.addService(Context.MEDIA_ROUTER_SERVICE, mediaRouter);
                    Slog.i(TAG, "GammaOS Nano: MediaRouterService ready");
                } catch (Throwable e) {
                    Slog.e(TAG, "GammaOS Nano: MediaRouterService failed", e);
                }
                try {
                    if (context.getPackageManager().hasSystemFeature(
                            PackageManager.FEATURE_BLUETOOTH)) {
                        Slog.i(TAG, "GammaOS Nano: starting Bluetooth stack");
                        mSystemServiceManager.startServiceFromJar(
                                BLUETOOTH_SERVICE_CLASS,
                                BLUETOOTH_APEX_SERVICE_JAR_PATH);
                        Slog.i(TAG, "GammaOS Nano: Bluetooth stack ready");
                    }
                } catch (Throwable e) {
                    Slog.e(TAG, "GammaOS Nano: Bluetooth stack failed", e);
                }
            }

            // GammaOS Nano: AppWidgetService must run in minimal boot too. It used
            // to live inside the !minimalBoot "ColorDisplay through MediaSession"
            // block, so with the feature still declared (android.software.app_widgets)
            // the "appwidget" binder was absent and AppWidgetManager.getInstance()
            // returned null, which crashes apps that build a widget path
            // unconditionally (for example Firefox's first-run onboarding "add
            // search widget" page). Start it at top level, whenever the feature is
            // declared, so it runs in both minimal and full boot.
            if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS)
                    || context.getResources().getBoolean(R.bool.config_enableAppWidgetService)) {
                t.traceBegin("StartAppWidgetService");
                mSystemServiceManager.startService(APPWIDGET_SERVICE_CLASS);
                t.traceEnd();
            }

            // GammaOS Nano: TrustManagerService must run in minimal boot too.
            // KeyguardManager's constructor resolves the "trust" binder via
            // ServiceManager.getServiceOrThrow(TRUST_SERVICE), so without it
            // getSystemService(KeyguardManager.class) throws and returns null,
            // which NPEs notification ranking (NotificationRecord.isKeyguardLocked).
            // Full boot starts it inside the !minimalBoot block below; add a
            // minimal-boot copy here without disturbing the full-boot ordering.
            if (minimalBoot) {
                t.traceBegin("StartTrustManager");
                try {
                    mSystemServiceManager.startService(TrustManagerService.class);
                } catch (Throwable e) {
                    Slog.w(TAG, "GammaOS Nano: TrustManager failed", e);
                }
                t.traceEnd();
            }

            // GammaOS Nano: ColorDisplayService must run in minimal boot too. It owns the display
            // colour transform behind LiveDisplay's colour temperature (night display) and the
            // nano menu's saturation control, so without it those rows would have nothing to talk
            // to. Full boot starts it inside the !minimalBoot block below; add a minimal-boot copy
            // here without disturbing the full-boot ordering (same pattern as TrustManager above).
            if (minimalBoot) {
                t.traceBegin("StartColorDisplay");
                try {
                    mSystemServiceManager.startService(ColorDisplayService.class);
                } catch (Throwable e) {
                    Slog.w(TAG, "GammaOS Nano: ColorDisplay failed", e);
                }
                t.traceEnd();
            }

            // GammaOS Nano: SliceManagerService must run in minimal boot too. TvSettings backs
            // several preference screens (Accessories/Bluetooth, etc.) with Slices whose provider
            // lives in TvSettings itself. Those screens call getSystemService(SliceManager.class)
            // and pinSlice()/unpinSlice(); without this service getSystemService returns null and
            // TvSettings crashes with an NPE (SliceFragment.onResume observing the slice live data).
            // Full boot starts it inside the !minimalBoot block below, guarded on FEATURE_SLICES_DISABLED;
            // add a minimal-boot copy here with the same guard, without disturbing the full-boot ordering
            // (same pattern as TrustManager / ColorDisplay above).
            if (minimalBoot
                    && !mPackageManager.hasSystemFeature(PackageManager.FEATURE_SLICES_DISABLED)) {
                t.traceBegin("StartSliceManagerService");
                try {
                    mSystemServiceManager.startService(SLICE_MANAGER_SERVICE_CLASS);
                } catch (Throwable e) {
                    Slog.w(TAG, "GammaOS Nano: SliceManagerService failed", e);
                }
                t.traceEnd();
            }

            if (!minimalBoot) { // GammaOS Nano: skip Serial through BackgroundInstall
            if (!isWatch) {
                t.traceBegin("StartSerialService");
                mSystemServiceManager.startService(SerialService.Lifecycle.class);
                t.traceEnd();
            }

            t.traceBegin("StartHardwarePropertiesManagerService");
            try {
                hardwarePropertiesService = new HardwarePropertiesManagerService(context);
                ServiceManager.addService(Context.HARDWARE_PROPERTIES_SERVICE,
                        hardwarePropertiesService);
            } catch (Throwable e) {
                Slog.e(TAG, "Failure starting HardwarePropertiesManagerService", e);
            }
            t.traceEnd();

            if (!isWatch) {
                t.traceBegin("StartTwilightService");
                mSystemServiceManager.startService(TwilightService.class);
                t.traceEnd();
            }

            // TODO(aml-jobscheduler): Think about how to do it properly.
            // GammaOS Nano: in minimal boot, JobScheduler starts on the bg
            // connectivity thread (needs ConnectivityManager).
            t.traceBegin("StartJobScheduler");
            mSystemServiceManager.startService(JOB_SCHEDULER_SERVICE_CLASS);
            t.traceEnd();

            if (!minimalBoot) { // GammaOS Nano: skip ColorDisplay through MediaSession
            t.traceBegin("StartColorDisplay");
            mSystemServiceManager.startService(ColorDisplayService.class);
            t.traceEnd();

            t.traceBegin("StartSoundTrigger");
            mSystemServiceManager.startService(SoundTriggerService.class);
            t.traceEnd();

            t.traceBegin("StartTrustManager");
            mSystemServiceManager.startService(TrustManagerService.class);
            t.traceEnd();

            if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_BACKUP)) {
                t.traceBegin("StartBackupManager");
                mSystemServiceManager.startService(BACKUP_MANAGER_SERVICE_CLASS);
                t.traceEnd();
            }

            // AppWidgetService is started earlier, before the !minimalBoot block,
            // so it also runs in minimal boot; see the note there.

            // We need to always start this service, regardless of whether the
            // FEATURE_VOICE_RECOGNIZERS feature is set, because it needs to take care
            // of initializing various settings.  It will internally modify its behavior
            if (!leanBoot) {
                t.traceBegin("StartVoiceRecognitionManager");
                mSystemServiceManager.startService(VOICE_RECOGNITION_MANAGER_SERVICE_CLASS);
                t.traceEnd();
            }

            if (GestureLauncherService.isGestureLauncherEnabled(context.getResources())) {
                t.traceBegin("StartGestureLauncher");
                mSystemServiceManager.startService(GestureLauncherService.class);
                t.traceEnd();
            }
            t.traceBegin("StartSensorNotification");
            mSystemServiceManager.startService(SensorNotificationService.class);
            t.traceEnd();

            if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_CONTEXT_HUB)) {
                t.traceBegin("StartContextHubSystemService");
                mSystemServiceManager.startService(ContextHubSystemService.class);
                t.traceEnd();
            }

            t.traceBegin("StartDiskStatsService");
            try {
                ServiceManager.addService("diskstats", new DiskStatsService(context));
            } catch (Throwable e) {
                reportWtf("starting DiskStats Service", e);
            }
            t.traceEnd();

            t.traceBegin("RuntimeService");
            try {
                ServiceManager.addService("runtime", new RuntimeService(context));
            } catch (Throwable e) {
                reportWtf("starting RuntimeService", e);
            }
            t.traceEnd();

            if (!isWatch && !disableNetworkTime) {
                t.traceBegin("StartNetworkTimeUpdateService");
                try {
                    networkTimeUpdater = new NetworkTimeUpdateService(context);
                    ServiceManager.addService("network_time_update_service", networkTimeUpdater);
                } catch (Throwable e) {
                    reportWtf("starting NetworkTimeUpdate service", e);
                }
                t.traceEnd();
            }

            t.traceBegin("CertBlacklister");
            try {
                CertBlacklister blacklister = new CertBlacklister(context);
            } catch (Throwable e) {
                reportWtf("starting CertBlacklister", e);
            }
            t.traceEnd();

            if (EmergencyAffordanceManager.ENABLED) {
                // EmergencyMode service
                t.traceBegin("StartEmergencyAffordanceService");
                mSystemServiceManager.startService(EmergencyAffordanceService.class);
                t.traceEnd();
            }

            t.traceBegin(START_BLOB_STORE_SERVICE);
            mSystemServiceManager.startService(BLOB_STORE_MANAGER_SERVICE_CLASS);
            t.traceEnd();

            t.traceBegin("StartDreamManager");
            mSystemServiceManager.startService(DreamManagerService.class);
            t.traceEnd();

            t.traceBegin("AddGraphicsStatsService");
            ServiceManager.addService(GraphicsStatsService.GRAPHICS_STATS_SERVICE,
                    new GraphicsStatsService(context));
            t.traceEnd();

            if (CoverageService.ENABLED) {
                t.traceBegin("AddCoverageService");
                ServiceManager.addService(CoverageService.COVERAGE_SERVICE, new CoverageService());
                t.traceEnd();
            }

            if (!leanBoot && mPackageManager.hasSystemFeature(PackageManager.FEATURE_PRINTING)) {
                t.traceBegin("StartPrintManager");
                mSystemServiceManager.startService(PRINT_MANAGER_SERVICE_CLASS);
                t.traceEnd();
            }

            if (!leanBoot) {
                t.traceBegin("StartAttestationVerificationService");
                mSystemServiceManager.startService(AttestationVerificationManagerService.class);
                t.traceEnd();
            }

            if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) {
                t.traceBegin("StartCompanionDeviceManager");
                mSystemServiceManager.startService(COMPANION_DEVICE_MANAGER_SERVICE_CLASS);
                t.traceEnd();
            }

            if (context.getResources().getBoolean(R.bool.config_enableVirtualDeviceManager)) {
                t.traceBegin("StartVirtualDeviceManager");
                mSystemServiceManager.startService(VIRTUAL_DEVICE_MANAGER_SERVICE_CLASS);
                t.traceEnd();
            }

            t.traceBegin("StartRestrictionManager");
            mSystemServiceManager.startService(RestrictionsManagerService.class);
            t.traceEnd();

            t.traceBegin("StartMediaSessionService");
            mSystemServiceManager.startService(MEDIA_SESSION_SERVICE_CLASS);
            t.traceEnd();
            } // !minimalBoot: ColorDisplay through MediaSession

            if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_HDMI_CEC)) {
                t.traceBegin("StartHdmiControlService");
                mSystemServiceManager.startService(HdmiControlService.class);
                t.traceEnd();
            }

            if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_LIVE_TV)
                    || mPackageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
                t.traceBegin("StartTvInteractiveAppManager");
                mSystemServiceManager.startService(TvInteractiveAppManagerService.class);
                t.traceEnd();
            }

            if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_LIVE_TV)
                    || mPackageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
                t.traceBegin("StartTvInputManager");
                mSystemServiceManager.startService(TvInputManagerService.class);
                t.traceEnd();
            }

            if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_TUNER)) {
                t.traceBegin("StartTunerResourceManager");
                mSystemServiceManager.startService(TunerResourceManagerService.class);
                t.traceEnd();
            }

            if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                t.traceBegin("StartMediaResourceMonitor");
                mSystemServiceManager.startService(MEDIA_RESOURCE_MONITOR_SERVICE_CLASS);
                t.traceEnd();
            }

            if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
                t.traceBegin("StartTvRemoteService");
                mSystemServiceManager.startService(TvRemoteService.class);
                t.traceEnd();
            }

            t.traceBegin("StartMediaRouterService");
            try {
                mediaRouter = new MediaRouterService(context);
                ServiceManager.addService(Context.MEDIA_ROUTER_SERVICE, mediaRouter);
            } catch (Throwable e) {
                reportWtf("starting MediaRouterService", e);
            }
            t.traceEnd();

            final boolean hasFeatureFace
                    = mPackageManager.hasSystemFeature(PackageManager.FEATURE_FACE);
            final boolean hasFeatureIris
                    = mPackageManager.hasSystemFeature(PackageManager.FEATURE_IRIS);
            final boolean hasFeatureFingerprint
                    = mPackageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT);

            if (hasFeatureFace) {
                t.traceBegin("StartFaceSensor");
                final FaceService faceService =
                        mSystemServiceManager.startService(FaceService.class);
                t.traceEnd();
            }

            if (hasFeatureIris) {
                t.traceBegin("StartIrisSensor");
                mSystemServiceManager.startService(IrisService.class);
                t.traceEnd();
            }

            if (hasFeatureFingerprint) {
                t.traceBegin("StartFingerprintSensor");
                final FingerprintService fingerprintService =
                        mSystemServiceManager.startService(FingerprintService.class);
                t.traceEnd();
            }

            // Start this service after all biometric sensor services are started.
            t.traceBegin("StartBiometricService");
            mSystemServiceManager.startService(BiometricService.class);
            t.traceEnd();

            t.traceBegin("StartAuthService");
            mSystemServiceManager.startService(AuthService.class);
            t.traceEnd();

            if (android.adaptiveauth.Flags.enableAdaptiveAuth()) {
                t.traceBegin("StartAdaptiveAuthService");
                mSystemServiceManager.startService(AdaptiveAuthService.class);
                t.traceEnd();
            }

            if (!isWatch) {
                // We don't run this on watches as there are no plans to use the data logged
                // on watch devices.
                t.traceBegin("StartDynamicCodeLoggingService");
                try {
                    DynamicCodeLoggingService.schedule(context);
                } catch (Throwable e) {
                    reportWtf("starting DynamicCodeLoggingService", e);
                }
                t.traceEnd();
            }

            if (!isWatch) {
                t.traceBegin("StartPruneInstantAppsJobService");
                try {
                    PruneInstantAppsJobService.schedule(context);
                } catch (Throwable e) {
                    reportWtf("StartPruneInstantAppsJobService", e);
                }
                t.traceEnd();
            }

            t.traceBegin("StartSelinuxAuditLogsService");
            try {
                SelinuxAuditLogsService.schedule(context);
            } catch (Throwable e) {
                reportWtf("starting SelinuxAuditLogsService", e);
            }
            t.traceEnd();

            // LauncherAppsService uses ShortcutService.
            t.traceBegin("StartShortcutServiceLifecycle");
            mSystemServiceManager.startService(ShortcutService.Lifecycle.class);
            t.traceEnd();

            t.traceBegin("StartLauncherAppsService");
            mSystemServiceManager.startService(LauncherAppsService.class);
            t.traceEnd();

            t.traceBegin("StartCrossProfileAppsService");
            mSystemServiceManager.startService(CrossProfileAppsService.class);
            t.traceEnd();

            t.traceBegin("StartPeopleService");
            mSystemServiceManager.startService(PeopleService.class);
            t.traceEnd();

            t.traceBegin("StartMediaMetricsManager");
            mSystemServiceManager.startService(MediaMetricsManagerService.class);
            t.traceEnd();

            t.traceBegin("StartBackgroundInstallControlService");
            mSystemServiceManager.startService(BackgroundInstallControlService.class);
            t.traceEnd();
            } // !minimalBoot: Serial through BackgroundInstall
        }

        // GammaOS Nano: ClipboardService must start even in minimal boot —
        // apps like Firefox call getSystemService(CLIPBOARD_SERVICE) and crash if null.
        t.traceBegin("StartClipboardService");
        mSystemServiceManager.startService(ClipboardService.class);
        t.traceEnd();

        // GammaOS Nano: ShortcutService (and the LauncherApps service it backs) must
        // start even in minimal boot - apps like Aurora Store call
        // getSystemService(ShortcutManager).isRequestPinShortcutSupported() (to offer a
        // home-screen shortcut after an install) and crash with an NPE if it is null.
        // Full boot already started them in the !minimalBoot block above, so only do it
        // here for minimal_boot; guarded so a failure cannot crash-loop system_server.
        if (minimalBoot) {
            t.traceBegin("StartShortcutServiceMinimal");
            try {
                mSystemServiceManager.startService(ShortcutService.Lifecycle.class);
                mSystemServiceManager.startService(LauncherAppsService.class);
            } catch (Throwable e) {
                reportWtf("starting ShortcutService/LauncherApps in minimal boot", e);
            }
            t.traceEnd();
        }

        if (!minimalBoot) { // GammaOS Nano: skip MediaProjection through Lineage
        t.traceBegin("StartMediaProjectionManager");
        mSystemServiceManager.startService(MediaProjectionManagerService.class);
        t.traceEnd();

        if (isWatch) {
            // Must be started before services that depend it, e.g. WearConnectivityService
            t.traceBegin("StartWearPowerService");
            mSystemServiceManager.startService(WEAR_POWER_SERVICE_CLASS);
            t.traceEnd();

            t.traceBegin("StartHealthService");
            mSystemServiceManager.startService(HEALTH_SERVICE_CLASS);
            t.traceEnd();

            t.traceBegin("StartSystemStateDisplayService");
            mSystemServiceManager.startService(SYSTEM_STATE_DISPLAY_SERVICE_CLASS);
            t.traceEnd();

            t.traceBegin("StartWearConnectivityService");
            mSystemServiceManager.startService(WEAR_CONNECTIVITY_SERVICE_CLASS);
            t.traceEnd();

            t.traceBegin("StartWearDisplayService");
            mSystemServiceManager.startService(WEAR_DISPLAY_SERVICE_CLASS);
            t.traceEnd();

            if (Build.IS_DEBUGGABLE) {
                t.traceBegin("StartWearDebugService");
                mSystemServiceManager.startService(WEAR_DEBUG_SERVICE_CLASS);
                t.traceEnd();
            }

            t.traceBegin("StartWearTimeService");
            mSystemServiceManager.startService(WEAR_TIME_SERVICE_CLASS);
            t.traceEnd();

            t.traceBegin("StartWearSettingsService");
            mSystemServiceManager.startService(WEAR_SETTINGS_SERVICE_CLASS);
            t.traceEnd();

            t.traceBegin("StartWearModeService");
            mSystemServiceManager.startService(WEAR_MODE_SERVICE_CLASS);
            t.traceEnd();

            boolean enableWristOrientationService = SystemProperties.getBoolean(
                    "config.enable_wristorientation", false);
            if (enableWristOrientationService) {
                t.traceBegin("StartWristOrientationService");
                mSystemServiceManager.startService(WRIST_ORIENTATION_SERVICE_CLASS);
                t.traceEnd();
            }
        }

        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_SLICES_DISABLED)) {
            t.traceBegin("StartSliceManagerService");
            mSystemServiceManager.startService(SLICE_MANAGER_SERVICE_CLASS);
            t.traceEnd();
        }

        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_EMBEDDED)) {
            t.traceBegin("StartIoTSystemService");
            mSystemServiceManager.startService(IOT_SERVICE_CLASS);
            t.traceEnd();
        }

        // Statsd helper
        t.traceBegin("StartStatsCompanion");
        mSystemServiceManager.startServiceFromJar(
                STATS_COMPANION_LIFECYCLE_CLASS, STATS_COMPANION_APEX_PATH);
        t.traceEnd();

        // Reboot Readiness
        t.traceBegin("StartRebootReadinessManagerService");
        mSystemServiceManager.startServiceFromJar(
                REBOOT_READINESS_LIFECYCLE_CLASS, SCHEDULING_APEX_PATH);
        t.traceEnd();

        // Statsd pulled atoms
        t.traceBegin("StartStatsPullAtomService");
        mSystemServiceManager.startService(STATS_PULL_ATOM_SERVICE_CLASS);
        t.traceEnd();

        // Log atoms to statsd from bootstrap processes.
        t.traceBegin("StatsBootstrapAtomService");
        mSystemServiceManager.startService(STATS_BOOTSTRAP_ATOM_SERVICE_LIFECYCLE_CLASS);
        t.traceEnd();

        if (!leanBoot) {
            t.traceBegin("StartIncidentCompanionService");
            mSystemServiceManager.startService(IncidentCompanionService.class);
            t.traceEnd();
        }

        // SdkSandboxManagerService
        t.traceBegin("StarSdkSandboxManagerService");
        mSystemServiceManager.startService(SDK_SANDBOX_MANAGER_SERVICE_CLASS);
        t.traceEnd();

        // AdServicesManagerService (PP API service)
        t.traceBegin("StartAdServicesManagerService");
        mSystemServiceManager.startService(AD_SERVICES_MANAGER_SERVICE_CLASS);
        t.traceEnd();

        // OnDevicePersonalizationSystemService
        t.traceBegin("StartOnDevicePersonalizationSystemService");
        mSystemServiceManager.startService(ON_DEVICE_PERSONALIZATION_SYSTEM_SERVICE_CLASS);
        t.traceEnd();

        // Profiling
        if (android.server.Flags.telemetryApisService()) {
            t.traceBegin("StartProfilingCompanion");
            mSystemServiceManager.startServiceFromJar(PROFILING_SERVICE_LIFECYCLE_CLASS,
                    PROFILING_SERVICE_JAR_PATH);
            t.traceEnd();
        }

        if (safeMode) {
            mActivityManagerService.enterSafeMode();
        }

        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            // MMS service broker
            t.traceBegin("StartMmsService");
            mmsService = mSystemServiceManager.startService(MmsServiceBroker.class);
            t.traceEnd();
        }

        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOFILL)) {
            t.traceBegin("StartAutoFillService");
            mSystemServiceManager.startService(AUTO_FILL_MANAGER_SERVICE_CLASS);
            t.traceEnd();
        }

        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_CREDENTIALS)) {
            boolean credentialManagerEnabled =
                    DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_CREDENTIAL,
                    CredentialManager.DEVICE_CONFIG_ENABLE_CREDENTIAL_MANAGER, true);
            if (credentialManagerEnabled) {
                if(isWatch &&
                  !android.credentials.flags.Flags.wearCredentialManagerEnabled()) {
                  Slog.d(TAG, "CredentialManager disabled on wear.");
                } else {
                  t.traceBegin("StartCredentialManagerService");
                  mSystemServiceManager.startService(CREDENTIAL_MANAGER_SERVICE_CLASS);
                  t.traceEnd();
                }
            } else {
                Slog.d(TAG, "CredentialManager disabled.");
            }
        }

        // Translation manager service
        if (deviceHasConfigString(context, R.string.config_defaultTranslationService)) {
            t.traceBegin("StartTranslationManagerService");
            mSystemServiceManager.startService(TRANSLATION_MANAGER_SERVICE_CLASS);
            t.traceEnd();
        } else {
            Slog.d(TAG, "TranslationService not defined by OEM");
        }

        // ClipboardService already started above (before !minimalBoot block)

        t.traceBegin("AppServiceManager");
        mSystemServiceManager.startService(AppBindingService.Lifecycle.class);
        t.traceEnd();

        // Perfetto TracingServiceProxy
        t.traceBegin("startTracingServiceProxy");
        mSystemServiceManager.startService(TracingServiceProxy.class);
        t.traceEnd();
        } // !minimalBoot: MediaProjection through TracingServiceProxy

        // Lineage Services. In minimal boot (nano) these used to be skipped entirely, which left
        // LiveDisplay with no service at all: nano could not offer colour calibration / reading
        // mode, and LineageParts' LiveDisplay screen crashed on a null LiveDisplayConfig. The
        // external server now runs in nano too, but LineageSystemServer itself starts only the
        // display-related subset there (see its minimal-boot whitelist), so nano's boot does not
        // pay for Profiles, Trust, Health and the rest.
        {
        // Lineage Services
        String externalServer = context.getResources().getString(
                org.lineageos.platform.internal.R.string.config_externalSystemServer);
        final Class<?> serverClazz;
        try {
            serverClazz = Class.forName(externalServer);
            final Constructor<?> constructor = serverClazz.getDeclaredConstructor(Context.class);
            constructor.setAccessible(true);
            final Object baseObject = constructor.newInstance(mSystemContext);
            final Method method = baseObject.getClass().getDeclaredMethod("run");
            method.setAccessible(true);
            method.invoke(baseObject);
        } catch (ClassNotFoundException
                | IllegalAccessException
                | InvocationTargetException
                | InstantiationException
                | NoSuchMethodException e) {
            reportWtf("Making " + externalServer + " ready", e);
        }
        } // !minimalBoot: Lineage services

        // It is now time to start up the app processes...

        if (!minimalBoot) {
        t.traceBegin("MakeLockSettingsServiceReady");
        if (lockSettings != null) {
            try {
                lockSettings.systemReady();
            } catch (Throwable e) {
                reportWtf("making Lock Settings Service ready", e);
            }
        }
        t.traceEnd();
        } // !minimalBoot: MakeLockSettingsServiceReady (done earlier in nano)

        // Needed by DevicePolicyManager for initialization
        t.traceBegin("StartBootPhaseLockSettingsReady");
        mSystemServiceManager.startBootPhase(t, SystemService.PHASE_LOCK_SETTINGS_READY);
        t.traceEnd();

        if (!minimalBoot) {
        // Create initial user if needed, which should be done early since some system services rely
        // on it in their setup, but likely needs to be done after LockSettingsService is ready.
        hsumBootUserInitializer =
                HsumBootUserInitializer.createInstance(
                        mActivityManagerService, mPackageManagerService, mContentResolver,
                        context.getResources().getBoolean(R.bool.config_isMainUserPermanentAdmin));
        if (hsumBootUserInitializer != null) {
            t.traceBegin("HsumBootUserInitializer.init");
            hsumBootUserInitializer.init(t);
            t.traceEnd();
        }

        CommunalProfileInitializer communalProfileInitializer = null;
        if (UserManager.isCommunalProfileEnabled()) {
            t.traceBegin("CommunalProfileInitializer.init");
            communalProfileInitializer =
                    new CommunalProfileInitializer(mActivityManagerService);
            communalProfileInitializer.init(t);
            t.traceEnd();
        } else {
            t.traceBegin("CommunalProfileInitializer.removeCommunalProfileIfPresent");
            CommunalProfileInitializer.removeCommunalProfileIfPresent();
            t.traceEnd();
        }
        } // !minimalBoot: HsumBootUserInitializer + CommunalProfile

        t.traceBegin("StartBootPhaseSystemServicesReady");
        mSystemServiceManager.startBootPhase(t, SystemService.PHASE_SYSTEM_SERVICES_READY);
        t.traceEnd();

        if (leanBoot) {
            SystemProperties.set("sys.gammaos.start_audio", "1");
        }

        t.traceBegin("MakeWindowManagerServiceReady");
        try {
            wm.systemReady();
        } catch (Throwable e) {
            reportWtf("making Window Manager Service ready", e);
        }
        t.traceEnd();

        t.traceBegin("RegisterLogMteState");
        try {
            LogMteState.register(context);
        } catch (Throwable e) {
            reportWtf("RegisterLogMteState", e);
        }
        t.traceEnd();

        // Emit any pending system_server WTFs
        synchronized (SystemService.class) {
            if (sPendingWtfs != null) {
                mActivityManagerService.schedulePendingSystemServerWtfs(sPendingWtfs);
                sPendingWtfs = null;
            }
        }

        if (safeMode) {
            mActivityManagerService.showSafeModeOverlay();
        }

        // Update the configuration for this context by hand, because we're going
        // to start using it before the config change done in wm.systemReady() will
        // propagate to it.
        final Configuration config = wm.computeNewConfiguration(DEFAULT_DISPLAY);
        DisplayMetrics metrics = new DisplayMetrics();
        context.getDisplay().getMetrics(metrics);
        context.getResources().updateConfiguration(config, metrics);

        // The system context's theme may be configuration-dependent.
        final Theme systemTheme = context.getTheme();
        if (systemTheme.getChangingConfigurations() != 0) {
            systemTheme.rebase();
        }

        // Permission policy service
        t.traceBegin("StartPermissionPolicyService");
        mSystemServiceManager.startService(PermissionPolicyService.class);
        t.traceEnd();

        t.traceBegin("MakePackageManagerServiceReady");
        mPackageManagerService.systemReady();
        t.traceEnd();

        t.traceBegin("MakeDisplayManagerServiceReady");
        try {
            // TODO: use boot phase and communicate this flag some other way
            mDisplayManagerService.systemReady(safeMode);
        } catch (Throwable e) {
            reportWtf("making Display Manager Service ready", e);
        }
        t.traceEnd();

        mSystemServiceManager.setSafeMode(safeMode);

        if (!minimalBoot) { // GammaOS Nano: skip DeviceSpecific through SensitiveContent
        // Start device specific services
        t.traceBegin("StartDeviceSpecificServices");
        final String[] classes = mSystemContext.getResources().getStringArray(
                R.array.config_deviceSpecificSystemServices);
        for (final String className : classes) {
            t.traceBegin("StartDeviceSpecificServices " + className);
            try {
                mSystemServiceManager.startService(className);
            } catch (Throwable e) {
                reportWtf("starting " + className, e);
            }
            t.traceEnd();
        }
        t.traceEnd();

        t.traceBegin("GameManagerService");
        mSystemServiceManager.startService(GAME_MANAGER_SERVICE_CLASS);
        t.traceEnd();

        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_UWB)) {
            t.traceBegin("UwbService");
            mSystemServiceManager.startServiceFromJar(UWB_SERVICE_CLASS, UWB_APEX_SERVICE_JAR_PATH);
            t.traceEnd();
        }

        t.traceBegin("StartBootPhaseDeviceSpecificServicesReady");
        mSystemServiceManager.startBootPhase(t, SystemService.PHASE_DEVICE_SPECIFIC_SERVICES_READY);
        t.traceEnd();

        t.traceBegin("StartSafetyCenterService");
        mSystemServiceManager.startService(SAFETY_CENTER_SERVICE_CLASS);
        t.traceEnd();

        if (!leanBoot) {
            t.traceBegin("AppSearchModule");
            mSystemServiceManager.startService(APPSEARCH_MODULE_LIFECYCLE_CLASS);
            t.traceEnd();
        }

        if (SystemProperties.getBoolean("ro.config.isolated_compilation_enabled", false)) {
            t.traceBegin("IsolatedCompilationService");
            mSystemServiceManager.startService(ISOLATED_COMPILATION_SERVICE_CLASS);
            t.traceEnd();
        }

        t.traceBegin("StartMediaCommunicationService");
        mSystemServiceManager.startService(MEDIA_COMMUNICATION_SERVICE_CLASS);
        t.traceEnd();

        t.traceBegin("AppCompatOverridesService");
        mSystemServiceManager.startService(APP_COMPAT_OVERRIDES_SERVICE_CLASS);
        t.traceEnd();

        if (!leanBoot) {
            t.traceBegin("HealthConnectManagerService");
            mSystemServiceManager.startService(HEALTHCONNECT_MANAGER_SERVICE_CLASS);
            t.traceEnd();
        }

        if (!leanBoot && mPackageManager.hasSystemFeature(PackageManager.FEATURE_DEVICE_LOCK)) {
            t.traceBegin("DeviceLockService");
            mSystemServiceManager.startServiceFromJar(DEVICE_LOCK_SERVICE_CLASS,
                    DEVICE_LOCK_APEX_PATH);
            t.traceEnd();
        }

        if (android.permission.flags.Flags.sensitiveNotificationAppProtection()
                || android.view.flags.Flags.sensitiveContentAppProtection()) {
            t.traceBegin("StartSensitiveContentProtectionManager");
            mSystemServiceManager.startService(SensitiveContentProtectionManagerService.class);
            t.traceEnd();
        }
        } // !minimalBoot: DeviceSpecific through SensitiveContent

        // These are needed to propagate to the runnable below.
        final DevicePolicyManagerService.Lifecycle dpmsF = dpms;
        final HsumBootUserInitializer hsumBootUserInitializerF = hsumBootUserInitializer;
        final NetworkManagementService networkManagementF = networkManagement;
        final NetworkPolicyManagerService networkPolicyF = networkPolicy;
        final CountryDetectorService countryDetectorF = countryDetector;
        final NetworkTimeUpdateService networkTimeUpdaterF = networkTimeUpdater;
        final InputManagerService inputManagerF = inputManager;
        final TelephonyRegistry telephonyRegistryF = telephonyRegistry;
        final MediaRouterService mediaRouterF = mediaRouter;
        final MmsServiceBroker mmsServiceF = mmsService;
        final VpnManagerService vpnManagerF = vpnManager;
        final VcnManagementService vcnManagementF = vcnManagement;
        final WindowManagerService windowManagerF = wm;
        final ConnectivityManager connectivityF = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);

        // We now tell the activity manager it is okay to run third party
        // code.  It will call back into us once it has gotten to the state
        // where third party code can really run (but before it has actually
        // started launching the initial applications), for us to complete our
        // initialization.
        mActivityManagerService.systemReady(() -> {
            Slog.i(TAG, "Making services ready");
            t.traceBegin("StartActivityManagerReadyPhase");
            mSystemServiceManager.startBootPhase(t, SystemService.PHASE_ACTIVITY_MANAGER_READY);
            t.traceEnd();

            t.traceBegin("StartObservingNativeCrashes");
            try {
                mActivityManagerService.startObservingNativeCrashes();
            } catch (Throwable e) {
                reportWtf("observing native crashes", e);
            }
            t.traceEnd();

            t.traceBegin("RegisterAppOpsPolicy");
            try {
                mActivityManagerService.setAppOpsPolicy(new AppOpsPolicy(mSystemContext));
            } catch (Throwable e) {
                reportWtf("registering app ops policy", e);
            }
            t.traceEnd();

            // No dependency on Webview preparation in system server. But this should
            // be completed before allowing 3rd party
            // GammaOS Nano: prepareWebViewInSystemServer must run even in
            // minimal_boot, otherwise the WebView relro file is never created
            // and any later app that uses WebView (SmartTube, etc) hangs in
            // WebViewUpdateService waiting for relro, then dies with
            // "Timed out waiting for relro creation, relros started 2 relros
            // finished 0". The preparation is async on SystemServerInitThreadPool
            // so it does not block the rest of systemReady().
            final String WEBVIEW_PREPARATION = "WebViewFactoryPreparation";
            Future<?> webviewPrep = null;
            if (mWebViewUpdateService != null) {
                webviewPrep = SystemServerInitThreadPool.submit(() -> {
                    Slog.i(TAG, WEBVIEW_PREPARATION);
                    TimingsTraceAndSlog traceLog = TimingsTraceAndSlog.newAsyncLog();
                    traceLog.traceBegin(WEBVIEW_PREPARATION);
                    // GammaOS Nano: minimal_boot skips the zygote preload, so mZygotePreload
                    // is null here. Guard the wait or it NPEs and crash-loops system_server.
                    if (mZygotePreload != null) {
                        ConcurrentUtils.waitForFutureNoInterrupt(mZygotePreload, "Zygote preload");
                        mZygotePreload = null;
                    }
                    mWebViewUpdateService.prepareWebViewInSystemServer();
                    traceLog.traceEnd();
                }, WEBVIEW_PREPARATION);
            }

            boolean isAutomotive = mPackageManager
                    .hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
            if (isAutomotive) {
                t.traceBegin("StartCarServiceHelperService");
                final SystemService cshs = mSystemServiceManager
                        .startService(CAR_SERVICE_HELPER_SERVICE_CLASS);
                if (cshs instanceof Dumpable) {
                    mDumper.addDumpable((Dumpable) cshs);
                }
                if (cshs instanceof DevicePolicySafetyChecker) {
                    dpmsF.setDevicePolicySafetyChecker((DevicePolicySafetyChecker) cshs);
                }
                t.traceEnd();
            }

            if (isWatch) {
                t.traceBegin("StartWearService");
                String wearServiceComponentNameString =
                    context.getString(R.string.config_wearServiceComponent);

                if (!TextUtils.isEmpty(wearServiceComponentNameString)) {
                    ComponentName wearServiceComponentName = ComponentName.unflattenFromString(
                        wearServiceComponentNameString);

                    if (wearServiceComponentName != null) {
                        Intent intent = new Intent();
                        intent.setComponent(wearServiceComponentName);
                        intent.addFlags(Intent.FLAG_DIRECT_BOOT_AUTO);
                        context.startServiceAsUser(intent, UserHandle.SYSTEM);
                    } else {
                        Slog.d(TAG, "Null wear service component name.");
                    }
                }
                t.traceEnd();
            }

            // Enable airplane mode in safe mode. setAirplaneMode() cannot be called
            // earlier as it sends broadcasts to other services.
            // TODO: This may actually be too late if radio firmware already started leaking
            // RF before the respective services start. However, fixing this requires changes
            // to radio firmware and interfaces.
            if (safeMode) {
                t.traceBegin("EnableAirplaneModeInSafeMode");
                try {
                    connectivityF.setAirplaneMode(true);
                } catch (Throwable e) {
                    reportWtf("enabling Airplane Mode during Safe Mode bootup", e);
                }
                t.traceEnd();
            }
            if (!minimalBoot) { // GammaOS Nano: skip network readiness (bg thread handles it)
            t.traceBegin("MakeNetworkManagementServiceReady");
            try {
                if (networkManagementF != null) {
                    networkManagementF.systemReady();
                }
            } catch (Throwable e) {
                reportWtf("making Network Managment Service ready", e);
            }
            CountDownLatch networkPolicyInitReadySignal = null;
            if (networkPolicyF != null) {
                networkPolicyInitReadySignal = networkPolicyF
                        .networkScoreAndNetworkManagementServiceReady();
            }
            t.traceEnd();
            t.traceBegin("MakeConnectivityServiceReady");
            try {
                if (connectivityF != null) {
                    connectivityF.systemReady();
                }
            } catch (Throwable e) {
                reportWtf("making Connectivity Service ready", e);
            }
            t.traceEnd();
            t.traceBegin("MakeVpnManagerServiceReady");
            try {
                if (vpnManagerF != null) {
                    vpnManagerF.systemReady();
                }
            } catch (Throwable e) {
                reportWtf("making VpnManagerService ready", e);
            }
            t.traceEnd();
            t.traceBegin("MakeVcnManagementServiceReady");
            try {
                if (vcnManagementF != null) {
                    vcnManagementF.systemReady();
                }
            } catch (Throwable e) {
                reportWtf("making VcnManagementService ready", e);
            }
            t.traceEnd();
            t.traceBegin("MakeNetworkPolicyServiceReady");
            try {
                if (networkPolicyF != null) {
                    networkPolicyF.systemReady(networkPolicyInitReadySignal);
                }
            } catch (Throwable e) {
                reportWtf("making Network Policy Service ready", e);
            }
            t.traceEnd();
            } // !minimalBoot: network readiness

            // Wait for all packages to be prepared
            mPackageManagerService.waitForAppDataPrepared();

            // It is now okay to let the various system services start their
            // third party code...
            t.traceBegin("PhaseThirdPartyAppsCanStart");
            // confirm webview completion before starting 3rd party
            if (webviewPrep != null) {
                ConcurrentUtils.waitForFutureNoInterrupt(webviewPrep, WEBVIEW_PREPARATION);
            }
            mSystemServiceManager.startBootPhase(t, SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);
            t.traceEnd();

            // GammaOS Nano: start JobScheduler here in minimal boot — it needs
            // ConnectivityManager (bg thread) and PowerManager (main thread).
            // By this point the bg connectivity thread has had time to finish.
            if (minimalBoot) {
                t.traceBegin("StartJobScheduler_Nano");
                try {
                    SystemService jobScheduler =
                            mSystemServiceManager.startService(JOB_SCHEDULER_SERVICE_CLASS);
                    // GammaOS Nano: JobScheduler is started here, well after
                    // PHASE_SYSTEM_SERVICES_READY has already been dispatched, so
                    // SystemServiceManager never delivers it the boot phases that wire up
                    // its dependencies. The most important is PHASE_SYSTEM_SERVICES_READY,
                    // which assigns mAppStateTracker; without it JobScheduler.schedule()
                    // NPEs in isUidActive() for EVERY app that schedules a job (observed:
                    // WebView's ComponentsProviderService crashes on the first browser
                    // launch). Drive the phases JobScheduler handles now that all the
                    // services they need (AppStateTracker, DeviceIdleInternal,
                    // StorageManagerInternal) are up.
                    if (jobScheduler != null) {
                        jobScheduler.onBootPhase(SystemService.PHASE_LOCK_SETTINGS_READY);
                        jobScheduler.onBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY);
                        jobScheduler.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);
                    }
                } catch (Throwable e) {
                    Slog.w(TAG, "GammaOS Nano: JobScheduler failed", e);
                }
                t.traceEnd();
            }

            if (hsumBootUserInitializerF != null) {
                t.traceBegin("HsumBootUserInitializer.systemRunning");
                hsumBootUserInitializerF.systemRunning(t);
                t.traceEnd();
            }

            // GammaOS Nano: NetworkStack must start when the Nano Wi-Fi feature
            // set is enabled, otherwise IpClient can't be bound and WifiService
            // drops every START_CONNECT with "IpClient is not ready". Tethering
            // stays skipped in minimal_boot -- it's not needed for station mode.
            boolean nanoWifiEnabled = android.os.SystemProperties.getBoolean(
                    "persist.gammaos.nano.wifi", true);
            if (!minimalBoot || nanoWifiEnabled) {
            t.traceBegin("StartNetworkStack");
            try {
                // Note : the network stack is creating on-demand objects that need to send
                // broadcasts, which means it currently depends on being started after
                // ActivityManagerService.mSystemReady and ActivityManagerService.mProcessesReady
                // are set to true. Be careful if moving this to a different place in the
                // startup sequence.
                NetworkStackClient.getInstance().start();
            } catch (Throwable e) {
                reportWtf("starting Network Stack", e);
            }
            t.traceEnd();
            }

            if (!minimalBoot) { // GammaOS Nano: skip tethering in station-only mode
            t.traceBegin("StartTethering");
            try {
                // TODO: hide implementation details, b/146312721.
                ConnectivityModuleConnector.getInstance().startModuleService(
                        TETHERING_CONNECTOR_CLASS,
                        PERMISSION_MAINLINE_NETWORK_STACK, service -> {
                            ServiceManager.addService(Context.TETHERING_SERVICE, service,
                                    false /* allowIsolated */,
                                    DUMP_FLAG_PRIORITY_HIGH | DUMP_FLAG_PRIORITY_NORMAL);
                        });
            } catch (Throwable e) {
                reportWtf("starting Tethering", e);
            }
            t.traceEnd();
            } // !minimalBoot: tethering

            if (!minimalBoot) { // GammaOS Nano: skip non-essential service readiness
            t.traceBegin("MakeCountryDetectionServiceReady");
            try {
                if (countryDetectorF != null) {
                    countryDetectorF.systemRunning();
                }
            } catch (Throwable e) {
                reportWtf("Notifying CountryDetectorService running", e);
            }
            t.traceEnd();
            t.traceBegin("MakeNetworkTimeUpdateReady");
            try {
                if (networkTimeUpdaterF != null) {
                    networkTimeUpdaterF.systemRunning();
                }
            } catch (Throwable e) {
                reportWtf("Notifying NetworkTimeService running", e);
            }
            t.traceEnd();
            } // !minimalBoot: CountryDetector + NetworkTimeUpdate
            t.traceBegin("MakeInputManagerServiceReady");
            try {
                // TODO(BT) Pass parameter to input manager
                if (inputManagerF != null) {
                    inputManagerF.systemRunning();
                }
            } catch (Throwable e) {
                reportWtf("Notifying InputManagerService running", e);
            }
            t.traceEnd();
            if (!minimalBoot) { // GammaOS Nano: skip Telephony through Incident
            t.traceBegin("MakeTelephonyRegistryReady");
            try {
                if (telephonyRegistryF != null) {
                    telephonyRegistryF.systemRunning();
                }
            } catch (Throwable e) {
                reportWtf("Notifying TelephonyRegistry running", e);
            }
            t.traceEnd();
            t.traceBegin("MakeMediaRouterServiceReady");
            try {
                if (mediaRouterF != null) {
                    mediaRouterF.systemRunning();
                }
            } catch (Throwable e) {
                reportWtf("Notifying MediaRouterService running", e);
            }
            t.traceEnd();
            if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                t.traceBegin("MakeMmsServiceReady");
                try {
                    if (mmsServiceF != null) mmsServiceF.systemRunning();
                } catch (Throwable e) {
                    reportWtf("Notifying MmsService running", e);
                }
                t.traceEnd();
            }

            t.traceBegin("IncidentDaemonReady");
            try {
                // TODO: Switch from checkService to getService once it's always
                // in the build and should reliably be there.
                final IIncidentManager incident = IIncidentManager.Stub.asInterface(
                        ServiceManager.getService(Context.INCIDENT_SERVICE));
                if (incident != null) {
                    incident.systemRunning();
                }
            } catch (Throwable e) {
                reportWtf("Notifying incident daemon running", e);
            }
            t.traceEnd();

            if (mIncrementalServiceHandle != 0) {
                t.traceBegin("MakeIncrementalServiceReady");
                setIncrementalServiceSystemReady(mIncrementalServiceHandle);
                t.traceEnd();
            }
            } // !minimalBoot: Telephony through Incident

            t.traceBegin("OdsignStatsLogger");
            try {
                OdsignStatsLogger.triggerStatsWrite();
            } catch (Throwable e) {
                reportWtf("Triggering OdsignStatsLogger", e);
            }
            t.traceEnd();
        }, t);

        t.traceBegin("LockSettingsThirdPartyAppsStarted");
        LockSettingsInternal lockSettingsInternal =
            LocalServices.getService(LockSettingsInternal.class);
        if (lockSettingsInternal != null) {
            lockSettingsInternal.onThirdPartyAppsStarted();
        }
        t.traceEnd();

        // GammaOS Nano: the gammapad vibration bridge carries PWM gamepad rumble AND
        // drives the per-app gamepad profile tracker (sys.gammaos.gamepad.fg_pkg), so it
        // must run even in nano minimal_boot - only SystemUI is skipped. It degrades
        // gracefully if the vibrator is unavailable, and its task-stack listener is
        // try-guarded, so it is safe to start here.
        t.traceBegin("StartGammapadVibrationBridge");
        try {
            mSystemServiceManager.startService(
                    com.android.server.gamepad.GammapadVibrationBridge.class);
        } catch (Throwable e) {
            reportWtf("starting GammapadVibrationBridge", e);
        }
        t.traceEnd();

        // GammaOS Nano: the media bridge owns an AVRCP-eligible MediaSession and
        // forwards Bluetooth transport keys (play/pause/skip) to the native nano
        // launcher, which has no MediaSession of its own. Like the gammapad bridge it
        // must run even in nano minimal_boot; it self-gates on the launcher actually
        // playing (its session stays inactive otherwise) so it is harmless elsewhere.
        t.traceBegin("StartNanoMediaBridge");
        try {
            mSystemServiceManager.startService(
                    com.android.server.media.NanoMediaBridge.class);
        } catch (Throwable e) {
            reportWtf("starting NanoMediaBridge", e);
        }
        t.traceEnd();

        if (!minimalBoot) {
        t.traceBegin("StartSystemUI");
        try {
            startSystemUi(context, windowManagerF);
        } catch (Throwable e) {
            reportWtf("starting System UI", e);
        }
        t.traceEnd();
        } else {
            Slog.i(TAG, "GammaOS Nano: skipping SystemUI (gammapad vibration bridge started)");
            // Preload RetroArch behind the nano menu: call finishBooting (which
            // triggers user unlock + home activity launch) but NOT enableScreenAfterBoot
            // (which would kill the bootanim overlay).  The nano menu stays visible
            // until the user makes a selection.
            final WindowManagerService wmsRef = windowManagerF;
            SystemProperties.set("sys.gammaos.nano.do_launch", "0");
            // No preload — boot Android services but don't launch RetroArch
            // until the user explicitly selects it from the nano menu.
            // This avoids force-stop cascades and zombie processes.
            SystemProperties.set("sys.gammaos.nano.preload", "0");
            // Post finishBooting to the main handler so it runs after
            // startOtherServices completes — calling it directly is too early
            // and can leave service.bootanim.exit=0 (enableScreenAfterBoot
            // bails out if WMS isn't fully ready yet).
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                Slog.i(TAG, "GammaOS Nano: finishing boot (no preload)");
                if (wmsRef != null) {
                    wmsRef.enableScreenAfterBoot();
                }
                try {
                    com.android.server.LocalServices.getService(
                            android.app.ActivityManagerInternal.class).finishBooting();
                } catch (Exception e) {
                    Slog.w(TAG, "GammaOS Nano: finishBooting failed: " + e);
                }
            });

            // Wait for user to select RetroArch, then enable screen (kills overlay).
            new Thread(() -> {
                while (!"1".equals(SystemProperties.get("service.bootanim.nano_retroarch"))) {
                    if ("1".equals(SystemProperties.get("service.bootanim.nano_boot"))) {
                        Slog.i(TAG, "GammaOS Nano: user chose full boot");
                        return;
                    }
                    try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                }
                Slog.i(TAG, "GammaOS Nano: user selected RetroArch, enabling screen");
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (wmsRef != null) {
                        wmsRef.enableScreenIfNeeded();
                    }
                });
                // Cache is only used for native libretro preview — no bind
                // mounts to tear down. RetroArch uses real FUSE paths.
            }, "NanoScreenThread").start();

            // Persistent polling thread: watches for do_launch=1 (relaunch case)
            // and triggers startHomeOnAllDisplays so RootWindowContainer launches the app.
            //
            // GammaOS: start monitoring as soon as user 0 CE storage is ready
            // (sys.user.0.ce_available=true) rather than waiting for full boot_completed.
            // This lets Quick Resume hand off to RetroArch several seconds earlier.
            // ATMS.startHomeOnAllDisplays is safe once CE storage is unlocked.
            new Thread(() -> {
                // Fast-path: if do_launch was already set by QR (racing ahead of unlock),
                // the monitor picks it up as soon as CE storage is ready.
                while (!"true".equals(SystemProperties.get("sys.user.0.ce_available"))
                        && !"1".equals(SystemProperties.get("sys.boot_completed"))) {
                    try { Thread.sleep(25); } catch (InterruptedException ignored) {}
                }
                Slog.i(TAG, "GammaOS Nano: relaunch monitor active");
                // GammaOS Nano: publish the app-visible external-storage state
                // for the QR handoff. NanoMenu's isQrRomStorageReady() probes
                // storage as root in its own mount namespace, which passes
                // seconds before the storage session APPS see is served; a
                // RetroArch launched in that window resolves its storage paths
                // to garbage ("<garbage>/saves") and hangs on a black screen.
                // Poll the same signal apps get and publish
                // sys.gammaos.nano.ext_storage_ready=1 once it reports
                // mounted; nano's QR preview keeps rendering until it flips.
                boolean extStorageReady = false;
                while (true) {
                    if (!extStorageReady) {
                        try {
                            if (android.os.Environment.MEDIA_MOUNTED.equals(
                                    android.os.Environment.getExternalStorageState())) {
                                SystemProperties.set(
                                        "sys.gammaos.nano.ext_storage_ready", "1");
                                extStorageReady = true;
                                Slog.i(TAG, "GammaOS Nano: external storage mounted,"
                                        + " ext_storage_ready=1");
                            }
                        } catch (Exception ignored) {
                            // Storage service not registered yet: not ready.
                        }
                    }
                    if (!"1".equals(SystemProperties.get("sys.gammaos.nano.do_launch"))) {
                        try { Thread.sleep(25); } catch (InterruptedException ignored) {}
                        continue;
                    }
                    SystemProperties.set("sys.gammaos.nano.do_launch", "0");
                    Slog.i(TAG, "GammaOS Nano: do_launch detected, triggering relaunch");
                    // GammaOS Nano: never start the app before its storage is
                    // served. The native-preview handoff already gates on
                    // ext_storage_ready, but the no-preview fallback and the
                    // init trigger chain fire do_launch with no storage gate,
                    // and an early RetroArch hangs black on garbage paths.
                    // Bounded so a storage failure degrades to the old
                    // behavior instead of never launching.
                    for (int i = 0; i < 1200 && !extStorageReady; i++) {
                        try {
                            if (android.os.Environment.MEDIA_MOUNTED.equals(
                                    android.os.Environment.getExternalStorageState())) {
                                SystemProperties.set(
                                        "sys.gammaos.nano.ext_storage_ready", "1");
                                extStorageReady = true;
                                break;
                            }
                        } catch (Exception ignored) { }
                        try { Thread.sleep(25); } catch (InterruptedException ignored) {}
                    }
                    if (!extStorageReady) {
                        Slog.w(TAG, "GammaOS Nano: external storage still not"
                                + " mounted after 30s, launching anyway");
                    }
                    // Signal RootWindowContainer to reset the launch grace period
                    // BEFORE posting to main looper — prevents the "app exited" check
                    // from firing before the new app process has started.
                    SystemProperties.set("sys.gammaos.nano.launch_pending", "1");
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        try {
                            com.android.server.LocalServices.getService(
                                    com.android.server.wm.ActivityTaskManagerInternal.class)
                                    .startHomeOnAllDisplays(
                                            android.os.UserHandle.USER_SYSTEM, "nano-relaunch");
                        } catch (Exception e) {
                            Slog.w(TAG, "GammaOS Nano: startHomeOnAllDisplays failed: " + e);
                        }
                    });
                }
            }, "NanoRelaunchMonitor").start();

            // Write app label + icon cache for the nano menu (the C++ side can't
            // resolve resource-based labels or render drawables). The cache is
            // written once after user unlock, and again whenever a package is
            // installed / removed / updated, so the Applications list stays live.
            // Labels go to /data/system/nano_app_labels.txt; real app icons are
            // rendered to /data/system/nano_app_icons/<pkg>.png (cached on DE so it
            // is available on early boot). See writeNanoAppCache().
            new Thread(() -> {
                // Wait for user unlock so PM can resolve resource labels
                while (!"1".equals(SystemProperties.get("sys.boot_completed"))) {
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}

                // Initial boot-time write (also establishes apps_generation=1).
                writeNanoAppCache("boot");

                // Carry the nano menu's LiveDisplay choices (colour calibration, reading mode)
                // into the provider it cannot write itself, and re-apply them after this boot.
                startNanoDisplayBridge(mSystemContext);

                // Live refresh on package changes. We are past sys.boot_completed, so
                // AMS/PMS are up and registerReceiver cannot race system-ready. A
                // dedicated HandlerThread both dispatches the receiver and runs the
                // rebuild, so it is never on the main thread and a burst of events is
                // serialized (single-flight) with a trailing debounce.
                android.os.HandlerThread ht = new android.os.HandlerThread("NanoAppCache");
                ht.start();
                final android.os.Handler h = new android.os.Handler(ht.getLooper());
                final Runnable refresh = () -> writeNanoAppCache("pkg-change");
                android.content.BroadcastReceiver rcvr = new android.content.BroadcastReceiver() {
                    @Override public void onReceive(android.content.Context c,
                                                    android.content.Intent i) {
                        // Ignore packages the nano list never shows, so frequent
                        // system-component / Play-services updates never cause churn.
                        // Prefix filter always; the system-flag filter only when the
                        // package still resolves (i.e. not a full removal).
                        android.net.Uri data = i.getData();
                        String pkg = (data != null) ? data.getSchemeSpecificPart() : null;
                        if (pkg == null
                                || pkg.startsWith("com.android.")
                                || pkg.startsWith("org.lineageos.")
                                || pkg.startsWith("com.gammaos.")
                                || pkg.startsWith("com.topjohnwu.")
                                || pkg.startsWith("com.retroarch.aarch64")) {
                            return;
                        }
                        String action = i.getAction();
                        if (!android.content.Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(action)
                                && !android.content.Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                            try {
                                android.content.pm.ApplicationInfo ai =
                                        c.getPackageManager().getApplicationInfo(pkg, 0);
                                if ((ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                                 || (ai.flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                                    return;
                                }
                            } catch (Exception ignored) { /* unresolvable -> proceed */ }
                        }
                        // Trailing debounce: coalesce an install/restore burst into one
                        // rebuild that snapshots PackageManager at execution time.
                        h.removeCallbacks(refresh);
                        h.postDelayed(refresh, 1500);
                    }
                };
                android.content.IntentFilter f = new android.content.IntentFilter();
                f.addAction(android.content.Intent.ACTION_PACKAGE_ADDED);
                f.addAction(android.content.Intent.ACTION_PACKAGE_REMOVED);
                f.addAction(android.content.Intent.ACTION_PACKAGE_FULLY_REMOVED);
                f.addAction(android.content.Intent.ACTION_PACKAGE_REPLACED);
                f.addDataScheme("package");
                // Dispatch on the HandlerThread, not the main looper.
                mSystemContext.registerReceiver(rcvr, f, null, h);

                // Serve the nano menu's app Information + Uninstall requests. nano sets
                // sys.gammaos.nano.appinfo_req=<pkg>#<nonce> to ask for an app's details
                // and sys.gammaos.nano.app_uninstall=<pkg> to remove it. Poll for them on
                // this (now long-lived) thread, mirroring the do_launch monitor above -
                // the proven signalling pattern here. The work runs off the main thread;
                // 150ms is imperceptible for a user-initiated menu action.
                String lastInfoReq = "", lastUninstall = "", lastAction = "";
                while (true) {
                    try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                    String req = SystemProperties.get("sys.gammaos.nano.appinfo_req", "");
                    if (!req.isEmpty() && !req.equals(lastInfoReq)) {
                        lastInfoReq = req; writeNanoAppInfo(req);
                    }
                    String un = SystemProperties.get("sys.gammaos.nano.app_uninstall", "");
                    if (un.isEmpty()) {
                        lastUninstall = "";   // doNanoUninstall cleared it: allow a re-uninstall
                    } else if (!un.equals(lastUninstall)) {
                        lastUninstall = un; doNanoUninstall(un);
                    }
                    String act = SystemProperties.get("sys.gammaos.nano.app_action", "");
                    if (act.isEmpty()) {
                        lastAction = "";   // doNanoAppAction cleared it: allow a repeat
                    } else if (!act.equals(lastAction)) {
                        lastAction = act; doNanoAppAction(act);
                    }
                }
            }, "NanoLabelCache").start();

        }

        t.traceEnd(); // startOtherServices
    }

    /**
     * GammaOS Nano: (re)write the app label + icon cache the native nano menu reads,
     * then bump the generation prop so nano live-refreshes the Applications list.
     * Idempotent; safe to call at boot and on every package change. MUST run off the
     * main thread (it renders drawables and does file IO). Every file is written
     * temp+rename so a concurrent nano reader never sees a partial file, and the
     * generation prop is bumped strictly last, after every file has landed.
     */
    private void writeNanoAppCache(String reason) {
        try {
            android.content.pm.PackageManager pm = mSystemContext.getPackageManager();
            java.util.List<android.content.pm.ApplicationInfo> apps =
                    pm.getInstalledApplications(android.content.pm.PackageManager.MATCH_ALL);
            java.io.File iconDir = new java.io.File("/data/system/nano_app_icons");
            iconDir.mkdirs();
            iconDir.setReadable(true, false);
            iconDir.setExecutable(true, false);
            final int ICON_PX = 144;
            StringBuilder sb = new StringBuilder();
            int iconCount = 0;
            // <pkg>.png files we still want; everything else in the dir is pruned.
            java.util.HashSet<String> liveIcons = new java.util.HashSet<>();
            for (android.content.pm.ApplicationInfo info : apps) {
                CharSequence label = pm.getApplicationLabel(info);
                if (label != null && label.length() > 0) {
                    sb.append(info.packageName).append('|').append(label).append('\n');
                }
                // Render the real icon only for the user-installed apps the nano
                // Applications list shows (non-system, minus the same prefixes nano skips).
                String pkg = info.packageName;
                boolean isSystem =
                        (info.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                     || (info.flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                // Exception: nano force-includes the Files app (com.android.documentsui) in its
                // Applications grid, so its icon must be cached too - otherwise nano draws a blank
                // grey tile for it. Render its icon despite the system / com.android. skips below.
                boolean forceIcon = pkg.equals("com.android.documentsui");
                if (!forceIcon
                        && (isSystem
                        || pkg.startsWith("com.android.")
                        || pkg.startsWith("org.lineageos.")
                        || pkg.startsWith("com.gammaos.")
                        || pkg.startsWith("com.topjohnwu.")
                        || pkg.startsWith("com.retroarch.aarch64"))) {
                    continue;
                }
                liveIcons.add(pkg + ".png");
                try {
                    android.graphics.drawable.Drawable d = pm.getApplicationIcon(info);
                    if (d != null) {
                        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                                ICON_PX, ICON_PX, android.graphics.Bitmap.Config.ARGB_8888);
                        try {
                            android.graphics.Canvas c = new android.graphics.Canvas(bmp);
                            d.setBounds(0, 0, ICON_PX, ICON_PX);
                            d.draw(c);
                            java.io.File dst = new java.io.File(iconDir, pkg + ".png");
                            java.io.File tmp = new java.io.File(iconDir, pkg + ".png.tmp");
                            java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp);
                            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos);
                            fos.close();
                            tmp.setReadable(true, false);
                            tmp.renameTo(dst);   // atomic replace; no partial read
                            iconCount++;
                        } finally {
                            bmp.recycle();
                        }
                    }
                } catch (Exception e) {
                    // Skip a single bad/corrupt icon; keep going.
                }
            }
            // Prune icons for apps uninstalled (or now excluded) since the last write.
            // Self-healing, so uninstall/update need no special-casing in the receiver.
            java.io.File[] existing = iconDir.listFiles();
            if (existing != null) {
                for (java.io.File ef : existing) {
                    String n = ef.getName();
                    if (n.endsWith(".png") && !liveIcons.contains(n)) {
                        ef.delete();
                    }
                }
            }
            // Label cache, temp+rename so it lands atomically, after all icons.
            java.io.File dstLabels = new java.io.File("/data/system/nano_app_labels.txt");
            java.io.File tmpLabels = new java.io.File("/data/system/nano_app_labels.txt.tmp");
            java.io.FileWriter fw = new java.io.FileWriter(tmpLabels);
            fw.write(sb.toString());
            fw.close();
            tmpLabels.setReadable(true, false);
            tmpLabels.renameTo(dstLabels);
            // Signal LAST: every file is on disk before the serial advances, so nano's
            // reload always sees the complete new label + icon set.
            int gen = mNanoAppsGeneration.incrementAndGet();
            SystemProperties.set("sys.gammaos.nano.apps_generation", Integer.toString(gen));
            Slog.i(TAG, "GammaOS Nano: wrote app cache (" + reason + "): "
                    + apps.size() + " apps, " + iconCount + " icons, gen=" + gen);
            // The installed-browser list rides the same triggers (boot + package change).
            writeNanoBrowserCache(pm, reason);
            // The launchable-activity list (for the gamepad remap "Launch Activity"
            // action) rides the same triggers too.
            writeNanoActivityCache(pm, reason);
        } catch (Exception e) {
            Slog.w(TAG, "GammaOS Nano: failed to write app label/icon cache", e);
        }
    }

    /**
     * GammaOS Nano: mirror the nano menu's display choices into LiveDisplay.
     *
     * nano is native and runs in the bootanim SELinux domain, where the "content" tool (the only
     * shell route to the LineageSettings provider) cannot run, so it cannot write those settings
     * itself. It therefore records the user's choice as a plain property and we translate here:
     * the three per-channel calibration percentages become LiveDisplay's single "R G B" string,
     * and the reading-mode toggle is passed straight through. LiveDisplayService observes the
     * provider, so the panel follows immediately.
     */
    private void startNanoDisplayBridge(Context context) {
        final android.content.ContentResolver cr = context.getContentResolver();
        final String[] last = { null, null };
        final Runnable sync = () -> {
            try {
                int r = SystemProperties.getInt("persist.gammaos.nano.display.cal_r", 100);
                int g = SystemProperties.getInt("persist.gammaos.nano.display.cal_g", 100);
                int b = SystemProperties.getInt("persist.gammaos.nano.display.cal_b", 100);
                r = Math.max(0, Math.min(100, r));
                g = Math.max(0, Math.min(100, g));
                b = Math.max(0, Math.min(100, b));
                String rgb = String.format(java.util.Locale.US, "%.3f %.3f %.3f",
                        r / 100f, g / 100f, b / 100f);
                if (!rgb.equals(last[0])) {
                    last[0] = rgb;
                    lineageos.providers.LineageSettings.System.putString(cr,
                            lineageos.providers.LineageSettings.System.DISPLAY_COLOR_ADJUSTMENT,
                            rgb);
                }
                String reading = SystemProperties.get("persist.gammaos.nano.display.reading", "0");
                if (!reading.equals(last[1])) {
                    last[1] = reading;
                    lineageos.providers.LineageSettings.System.putInt(cr,
                            lineageos.providers.LineageSettings.System.DISPLAY_READING_MODE,
                            "1".equals(reading) ? 1 : 0);
                }
            } catch (Throwable e) {
                Slog.w(TAG, "GammaOS Nano: display bridge sync failed", e);
            }
        };
        // Poll rather than use SystemProperties.addChangeCallback: that callback did not fire for
        // these writes on this build (verified on device - only the initial sync ran), and the
        // menu needs the panel to follow the slider. Each pass is a handful of property reads and
        // only touches the provider when one of our values actually moved, so an idle system does
        // no work beyond the reads.
        sync.run();
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
                sync.run();
            }
        }, "NanoDisplayBridge");
        t.setDaemon(true);
        t.start();
        Slog.i(TAG, "GammaOS Nano: display bridge started");
    }

    /**
     * GammaOS Nano: write the list of installed web browsers for the nano menu's
     * "Default Browser" picker, as "pkg|Label|pkg/Activity" lines in
     * /data/system/nano_browsers.txt.
     *
     * A browser is anything that handles ACTION_VIEW on an http/https URI, which is what
     * every real browser declares. This deliberately does NOT reuse the app cache above:
     * that one drops system apps and the com.android.* / com.gammaos.* prefixes, which
     * would remove Chrome and the shipped GammaBrowser. The activity component is included
     * because nano hands the launch an explicit VIEW intent, and the framework needs the
     * component to resolve ActivityInfo (a package-only intent falls back to LAUNCHER and
     * the URL would be dropped).
     */
    private void writeNanoBrowserCache(android.content.pm.PackageManager pm, String reason) {
        try {
            StringBuilder sb = new StringBuilder();
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            // The shipped browser is always offered first, even if the query below misses
            // it, so the picker and the launch fallback are never empty.
            sb.append("com.gammaos.browser|GammaBrowser|com.gammaos.browser/.MainActivity\n");
            seen.add("com.gammaos.browser");
            for (String scheme : new String[] {"https", "http"}) {
                Intent probe = new Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse(scheme + "://example.com"));
                probe.addCategory(Intent.CATEGORY_BROWSABLE);
                java.util.List<android.content.pm.ResolveInfo> ris =
                        pm.queryIntentActivities(probe,
                                android.content.pm.PackageManager.MATCH_ALL);
                if (ris == null) continue;
                for (android.content.pm.ResolveInfo ri : ris) {
                    if (ri.activityInfo == null) continue;
                    String pkg = ri.activityInfo.packageName;
                    if (pkg == null || !seen.add(pkg)) continue;
                    CharSequence label = ri.loadLabel(pm);
                    String name = (label != null && label.length() > 0) ? label.toString() : pkg;
                    // Keep the format parseable: labels are user-visible and could in
                    // principle contain the delimiter or a newline.
                    name = name.replace('|', ' ').replace('\n', ' ').trim();
                    if (name.isEmpty()) name = pkg;
                    sb.append(pkg).append('|').append(name).append('|')
                      .append(pkg).append('/').append(ri.activityInfo.name).append('\n');
                }
            }
            java.io.File dst = new java.io.File("/data/system/nano_browsers.txt");
            java.io.File tmp = new java.io.File("/data/system/nano_browsers.txt.tmp");
            java.io.FileWriter fw = new java.io.FileWriter(tmp);
            fw.write(sb.toString());
            fw.close();
            tmp.setReadable(true, false);
            tmp.renameTo(dst);   // atomic replace; nano never reads a partial list
            int gen = mNanoBrowsersGeneration.incrementAndGet();
            SystemProperties.set("sys.gammaos.nano.browsers_generation", Integer.toString(gen));
            Slog.i(TAG, "GammaOS Nano: wrote browser list (" + reason + "): "
                    + seen.size() + " browsers, gen=" + gen);
        } catch (Exception e) {
            Slog.w(TAG, "GammaOS Nano: failed to write browser list", e);
        }
    }

    /**
     * GammaOS Nano: write the list of launchable activities for the nano menu's
     * gamepad "Launch Activity" remap-action picker, as "pkg|Label|pkg/Activity"
     * lines in /data/system/nano_activities.txt.
     *
     * Uses ACTION_MAIN + CATEGORY_LAUNCHER (the launcher-visible entry points),
     * keeping one row per activity (not deduped by package like the browser
     * cache) so the user can target a specific activity. nano launches it via an
     * explicit "-n pkg/Activity" intent, which the framework resolves directly.
     */
    private void writeNanoActivityCache(android.content.pm.PackageManager pm, String reason) {
        try {
            StringBuilder sb = new StringBuilder();
            Intent probe = new Intent(Intent.ACTION_MAIN);
            probe.addCategory(Intent.CATEGORY_LAUNCHER);
            java.util.List<android.content.pm.ResolveInfo> ris =
                    pm.queryIntentActivities(probe,
                            android.content.pm.PackageManager.MATCH_ALL);
            int count = 0;
            if (ris != null) {
                // Sort by visible label so the picker reads alphabetically.
                java.util.Collections.sort(ris, new java.util.Comparator<
                        android.content.pm.ResolveInfo>() {
                    @Override
                    public int compare(android.content.pm.ResolveInfo a,
                                       android.content.pm.ResolveInfo b) {
                        CharSequence la = a.loadLabel(pm), lb = b.loadLabel(pm);
                        return String.valueOf(la).compareToIgnoreCase(String.valueOf(lb));
                    }
                });
                for (android.content.pm.ResolveInfo ri : ris) {
                    if (ri.activityInfo == null) continue;
                    String pkg = ri.activityInfo.packageName;
                    String cls = ri.activityInfo.name;
                    if (pkg == null || cls == null) continue;
                    CharSequence label = ri.loadLabel(pm);
                    String name = (label != null && label.length() > 0)
                            ? label.toString() : pkg;
                    name = name.replace('|', ' ').replace('\n', ' ').trim();
                    if (name.isEmpty()) name = pkg;
                    sb.append(pkg).append('|').append(name).append('|')
                      .append(pkg).append('/').append(cls).append('\n');
                    count++;
                }
            }
            java.io.File dst = new java.io.File("/data/system/nano_activities.txt");
            java.io.File tmp = new java.io.File("/data/system/nano_activities.txt.tmp");
            java.io.FileWriter fw = new java.io.FileWriter(tmp);
            fw.write(sb.toString());
            fw.close();
            tmp.setReadable(true, false);
            tmp.renameTo(dst);   // atomic replace; nano never reads a partial list
            int gen = mNanoActivitiesGeneration.incrementAndGet();
            SystemProperties.set("sys.gammaos.nano.activities_generation",
                    Integer.toString(gen));
            Slog.i(TAG, "GammaOS Nano: wrote activity list (" + reason + "): "
                    + count + " activities, gen=" + gen);
        } catch (Exception e) {
            Slog.w(TAG, "GammaOS Nano: failed to write activity list", e);
        }
    }

    private static String nanoPad(String s) { return String.format("%-16s", s); }
    private String nanoSize(long b) {
        return android.text.format.Formatter.formatFileSize(mSystemContext, b);
    }

    /**
     * GammaOS Nano: write the Information details for one package to
     * /data/system/nano_app_info.txt on request, then bump sys.gammaos.nano.appinfo_gen
     * so the native menu swaps its "Loading..." placeholder for the real facts. The
     * request is "<pkg>#<nonce>"; the nonce is echoed on line 1 so nano ignores a stale
     * reply from an earlier request. Always writes a file and bumps the gen (even on
     * failure) so the menu never hangs on "Loading...". Mirrors Settings / TvSettings.
     */
    private void writeNanoAppInfo(String req) {
        String pkg = req;
        int hash = req.lastIndexOf('#');
        if (hash >= 0) pkg = req.substring(0, hash);
        mNanoInfoReq = req;   // remember for async action refreshes (grant/revoke/clear)
        StringBuilder sb = new StringBuilder();
        sb.append("req|").append(req).append('\n');
        try {
            android.content.pm.PackageManager pm = mSystemContext.getPackageManager();
            android.content.pm.PackageInfo pi = pm.getPackageInfo(pkg,
                    android.content.pm.PackageManager.GET_PERMISSIONS);
            android.content.pm.ApplicationInfo ai = pi.applicationInfo;
            boolean system = (ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                          || (ai.flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            String installer = "";
            try {
                String s = pm.getInstallSourceInfo(pkg).getInstallingPackageName();
                if (s != null) installer = s;
            } catch (Exception ignored) {}
            java.text.DateFormat df = java.text.DateFormat.getDateInstance();
            // Tagged lines the native menu parses into a navigable page: F| = a display
            // fact, CACHE|/DATA| = sizes for the Clear rows, PERM|<perm>|<label>|<0|1> = a
            // runtime-permission toggle (granted AND denied).
            sb.append("F|Package|").append(pkg).append('\n');
            sb.append("F|Version|").append(pi.versionName)
              .append(" (").append(pi.getLongVersionCode()).append(")\n");
            long cacheBytes = -1, dataBytes = -1;
            try {
                android.app.usage.StorageStatsManager ssm =
                        mSystemContext.getSystemService(android.app.usage.StorageStatsManager.class);
                android.app.usage.StorageStats st = ssm.queryStatsForPackage(
                        ai.storageUuid, pkg,
                        android.os.UserHandle.of(android.os.UserHandle.myUserId()));
                long app = st.getAppBytes(); dataBytes = st.getDataBytes(); cacheBytes = st.getCacheBytes();
                // Total only; the Clear Cache / Clear Data rows show the cache/data sizes.
                sb.append("F|Size|").append(nanoSize(app + dataBytes)).append('\n');
            } catch (Exception e) {
                sb.append("F|Size|Unavailable\n");
            }
            sb.append("F|Type|").append(system ? "System app" : "User app").append('\n');
            if (!installer.isEmpty()) sb.append("F|Installer|").append(installer).append('\n');
            sb.append("F|Target SDK|").append(ai.targetSdkVersion).append('\n');
            sb.append("F|Min SDK|").append(ai.minSdkVersion).append('\n');
            sb.append("F|Installed|").append(df.format(new java.util.Date(pi.firstInstallTime))).append('\n');
            sb.append("F|Updated|").append(df.format(new java.util.Date(pi.lastUpdateTime))).append('\n');
            sb.append("CACHE|").append(cacheBytes >= 0 ? nanoSize(cacheBytes) : "").append('\n');
            sb.append("DATA|").append(dataBytes >= 0 ? nanoSize(dataBytes) : "").append('\n');
            if (pi.requestedPermissions != null) {
                for (int i = 0; i < pi.requestedPermissions.length; i++) {
                    String p = pi.requestedPermissions[i];
                    try {
                        android.content.pm.PermissionInfo info = pm.getPermissionInfo(p, 0);
                        if (info.getProtection()
                                != android.content.pm.PermissionInfo.PROTECTION_DANGEROUS) continue;
                        boolean granted = (pi.requestedPermissionsFlags[i]
                                & android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
                        CharSequence lbl = info.loadLabel(pm);
                        String name = (lbl != null ? lbl.toString() : p).replace('|', ' ');
                        sb.append("PERM|").append(p).append('|').append(name)
                          .append('|').append(granted ? '1' : '0').append('\n');
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            sb.append("Information unavailable.").append('\n');
        }
        try {
            java.io.File dst = new java.io.File("/data/system/nano_app_info.txt");
            java.io.File tmp = new java.io.File("/data/system/nano_app_info.txt.tmp");
            try (java.io.FileWriter fw = new java.io.FileWriter(tmp)) { fw.write(sb.toString()); }
            tmp.setReadable(true, false);
            tmp.renameTo(dst);
        } catch (Exception e) {
            Slog.w(TAG, "GammaOS Nano: app-info write failed for " + req, e);
            return;
        }
        // Bump last, so nano only reads a complete file.
        SystemProperties.set("sys.gammaos.nano.appinfo_gen",
                Integer.toString(mNanoAppInfoGeneration.incrementAndGet()));
    }

    /**
     * GammaOS Nano: silently uninstall a user package the menu asked to remove. Runs in
     * system_server (which holds DELETE_PACKAGES), so no new sepolicy and the confirm
     * dialog in nano is the sole user gate. Refuses system / updated-system / excluded
     * packages authoritatively against the live PackageManager. The resulting
     * ACTION_PACKAGE_REMOVED drives the normal cache rewrite + apps_generation bump, so
     * the Applications list refreshes itself. Clears the request prop when done.
     */
    private void doNanoUninstall(String pkg) {
        try {
            if (pkg.startsWith("com.android.") || pkg.startsWith("org.lineageos.")
                    || pkg.startsWith("com.gammaos.") || pkg.startsWith("com.topjohnwu.")
                    || pkg.startsWith("com.retroarch.aarch64")) return;
            android.content.pm.PackageManager pm = mSystemContext.getPackageManager();
            android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            if ((ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    || (ai.flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) return;
            android.content.Intent it = new android.content.Intent(
                    "com.gammaos.nano.UNINSTALL_RESULT").setPackage("android");
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
                    mSystemContext, 0, it,
                    android.app.PendingIntent.FLAG_IMMUTABLE
                  | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
            pm.getPackageInstaller().uninstall(pkg, pi.getIntentSender());
            Slog.i(TAG, "GammaOS Nano: uninstalling " + pkg);
        } catch (Exception e) {
            Slog.w(TAG, "GammaOS Nano: uninstall failed for " + pkg, e);
        } finally {
            // Clear so a later re-install of the same package can be removed again.
            SystemProperties.set("sys.gammaos.nano.app_uninstall", "");
        }
    }

    /**
     * GammaOS Nano: grant/revoke a runtime permission or clear cache/data for an app the
     * Information page is showing. Runs in system_server (holds the runtime-permission and
     * clear-data/cache powers), same trust model as doNanoUninstall - no new sepolicy.
     * Refuses protected packages. After the change, rewrites nano_app_info.txt against the
     * request nonce the menu is still waiting on so the page reflects the new state (the
     * async clear observers refresh once deletion really completes). spec =
     * "<pkg>|<grant|revoke|clearcache|cleardata>|<perm?>".
     */
    private void doNanoAppAction(String spec) {
        final String reqAtDispatch = mNanoInfoReq;   // the nonce the menu is waiting on
        try {
            String[] p = spec.split("\\|", -1);
            String pkg  = p.length > 0 ? p[0] : "";
            String op   = p.length > 1 ? p[1] : "";
            String perm = p.length > 2 ? p[2] : "";
            if (pkg.isEmpty()) return;
            if (pkg.startsWith("com.android.") || pkg.startsWith("org.lineageos.")
                    || pkg.startsWith("com.gammaos.") || pkg.startsWith("com.topjohnwu.")
                    || pkg.startsWith("com.retroarch.aarch64")) return;
            android.content.pm.PackageManager pm = mSystemContext.getPackageManager();
            android.os.UserHandle user = android.os.UserHandle.of(android.os.UserHandle.myUserId());
            switch (op) {
                case "grant":
                    if (!perm.isEmpty()) pm.grantRuntimePermission(pkg, perm, user);
                    if (!reqAtDispatch.isEmpty()) writeNanoAppInfo(reqAtDispatch);
                    break;
                case "revoke":
                    if (!perm.isEmpty()) pm.revokeRuntimePermission(pkg, perm, user);
                    if (!reqAtDispatch.isEmpty()) writeNanoAppInfo(reqAtDispatch);
                    break;
                case "clearcache":
                    pm.deleteApplicationCacheFiles(pkg,
                            new android.content.pm.IPackageDataObserver.Stub() {
                        public void onRemoveCompleted(String pn, boolean ok) {
                            if (!reqAtDispatch.isEmpty()) writeNanoAppInfo(reqAtDispatch);
                        }
                    });
                    break;
                case "cleardata":
                    pm.clearApplicationUserData(pkg,
                            new android.content.pm.IPackageDataObserver.Stub() {
                        public void onRemoveCompleted(String pn, boolean ok) {
                            if (!reqAtDispatch.isEmpty()) writeNanoAppInfo(reqAtDispatch);
                        }
                    });
                    break;
            }
            Slog.i(TAG, "GammaOS Nano: app action " + spec);
        } catch (Exception e) {
            Slog.w(TAG, "GammaOS Nano: app action failed for " + spec, e);
        } finally {
            SystemProperties.set("sys.gammaos.nano.app_action", "");   // consumed; allow repeats
        }
    }

    /**
     * Starts system services defined in apexes.
     *
     * <p>Apex services must be the last category of services to start. No other service must be
     * starting after this point. This is to prevent unnecessary stability issues when these apexes
     * are updated outside of OTA; and to avoid breaking dependencies from system into apexes.
     */
    private void startApexServices(@NonNull TimingsTraceAndSlog t) {
        t.traceBegin("startApexServices");
        // TODO(b/192880996): get the list from "android" package, once the manifest entries
        // are migrated to system manifest.
        List<ApexSystemServiceInfo> services = ApexManager.getInstance().getApexSystemServices();
        for (ApexSystemServiceInfo info : services) {
            String name = info.getName();
            String jarPath = info.getJarPath();
            t.traceBegin("starting " + name);
            if (TextUtils.isEmpty(jarPath)) {
                mSystemServiceManager.startService(name);
            } else {
                mSystemServiceManager.startServiceFromJar(name, jarPath);
            }
            t.traceEnd();
        }

        // make sure no other services are started after this point
        mSystemServiceManager.sealStartedServices();

        t.traceEnd(); // startApexServices
    }

    private void updateWatchdogTimeout(@NonNull TimingsTraceAndSlog t) {
        t.traceBegin("UpdateWatchdogTimeout");
        Watchdog.getInstance().registerSettingsObserver(mSystemContext);
        t.traceEnd();
    }

    private boolean deviceHasConfigString(@NonNull Context context, @StringRes int resId) {
        String serviceName = context.getString(resId);
        return !TextUtils.isEmpty(serviceName);
    }

    private void startSystemCaptionsManagerService(@NonNull Context context,
            @NonNull TimingsTraceAndSlog t) {
        if (!deviceHasConfigString(context, R.string.config_defaultSystemCaptionsManagerService)) {
            Slog.d(TAG, "SystemCaptionsManagerService disabled because resource is not overlaid");
            return;
        }

        t.traceBegin("StartSystemCaptionsManagerService");
        mSystemServiceManager.startService(SYSTEM_CAPTIONS_MANAGER_SERVICE_CLASS);
        t.traceEnd();
    }

    private void startTextToSpeechManagerService(@NonNull Context context,
            @NonNull TimingsTraceAndSlog t) {
        t.traceBegin("StartTextToSpeechManagerService");
        mSystemServiceManager.startService(TEXT_TO_SPEECH_MANAGER_SERVICE_CLASS);
        t.traceEnd();
    }

    private void startContentCaptureService(@NonNull Context context,
            @NonNull TimingsTraceAndSlog t) {
        // First check if it was explicitly enabled by DeviceConfig
        boolean explicitlyEnabled = false;
        String settings = DeviceConfig.getProperty(DeviceConfig.NAMESPACE_CONTENT_CAPTURE,
                ContentCaptureManager.DEVICE_CONFIG_PROPERTY_SERVICE_EXPLICITLY_ENABLED);
        if (settings != null && !settings.equalsIgnoreCase("default")) {
            explicitlyEnabled = Boolean.parseBoolean(settings);
            if (explicitlyEnabled) {
                Slog.d(TAG, "ContentCaptureService explicitly enabled by DeviceConfig");
            } else {
                Slog.d(TAG, "ContentCaptureService explicitly disabled by DeviceConfig");
                return;
            }
        }

        // Then check if OEM overlaid the resource that defines the service.
        if (!explicitlyEnabled) {
            if (!deviceHasConfigString(context, R.string.config_defaultContentCaptureService)) {
                Slog.d(TAG, "ContentCaptureService disabled because resource is not overlaid");
                return;
            }
            if (!deviceHasConfigString(context, R.string.config_defaultContentProtectionService)) {
                Slog.d(
                        TAG,
                        "ContentProtectionService disabled because resource is not overlaid,"
                            + " ContentCaptureService still enabled");
            }
        }

        t.traceBegin("StartContentCaptureService");
        mSystemServiceManager.startService(CONTENT_CAPTURE_MANAGER_SERVICE_CLASS);

        ContentCaptureManagerInternal ccmi =
                LocalServices.getService(ContentCaptureManagerInternal.class);
        if (ccmi != null && mActivityManagerService != null) {
            mActivityManagerService.setContentCaptureManager(ccmi);
        }

        t.traceEnd();
    }

    private void startAttentionService(@NonNull Context context, @NonNull TimingsTraceAndSlog t) {
        if (!AttentionManagerService.isServiceConfigured(context)) {
            Slog.d(TAG, "AttentionService is not configured on this device");
            return;
        }

        t.traceBegin("StartAttentionManagerService");
        mSystemServiceManager.startService(AttentionManagerService.class);
        t.traceEnd();
    }

    private void startRotationResolverService(@NonNull Context context,
            @NonNull TimingsTraceAndSlog t) {
        if (!RotationResolverManagerService.isServiceConfigured(context)) {
            Slog.d(TAG, "RotationResolverService is not configured on this device");
            return;
        }

        t.traceBegin("StartRotationResolverService");
        mSystemServiceManager.startService(RotationResolverManagerService.class);
        t.traceEnd();

    }

    private void startWearableSensingService(@NonNull TimingsTraceAndSlog t) {
        t.traceBegin("startWearableSensingService");
        mSystemServiceManager.startService(WearableSensingManagerService.class);
        t.traceEnd();
    }

    private static void startSystemUi(Context context, WindowManagerService windowManager) {
        PackageManagerInternal pm = LocalServices.getService(PackageManagerInternal.class);
        Intent intent = new Intent();
        intent.setComponent(pm.getSystemUiServiceComponent());
        intent.addFlags(Intent.FLAG_DEBUG_TRIAGED_MISSING);
        //Slog.d(TAG, "Starting service: " + intent);
        context.startServiceAsUser(intent, UserHandle.SYSTEM);
        windowManager.onSystemUiStarted();
    }

    /**
     * Handle the serious errors during early system boot, used by {@link Log} via
     * {@link com.android.internal.os.RuntimeInit}.
     */
    private static boolean handleEarlySystemWtf(final IBinder app, final String tag, boolean system,
            final ApplicationErrorReport.ParcelableCrashInfo crashInfo, int immediateCallerPid) {
        final String processName = "system_server";
        final int myPid = myPid();

        com.android.server.am.EventLogTags.writeAmWtf(UserHandle.getUserId(SYSTEM_UID), myPid,
                processName, -1, tag, crashInfo.exceptionMessage);

        FrameworkStatsLog.write(FrameworkStatsLog.WTF_OCCURRED, SYSTEM_UID, tag, processName,
                myPid, ServerProtoEnums.SYSTEM_SERVER);

        synchronized (SystemServer.class) {
            if (sPendingWtfs == null) {
                sPendingWtfs = new LinkedList<>();
            }
            sPendingWtfs.add(new Pair<>(tag, crashInfo));
        }
        return false;
    }

}
