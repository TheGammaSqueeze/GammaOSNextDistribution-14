/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.tradefed.targetprep;

import com.android.ddmlib.IDevice;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.dependencies.ExternalDependency;
import com.android.tradefed.dependencies.IExternalDependency;
import com.android.tradefed.dependencies.connectivity.BluetoothDependency;
import com.android.tradefed.dependencies.connectivity.EthernetDependency;
import com.android.tradefed.dependencies.connectivity.NetworkDependency;
import com.android.tradefed.dependencies.connectivity.TelephonyDependency;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.LocalAndroidVirtualDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.device.TestDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.device.cloud.NestedRemoteDevice;
import com.android.tradefed.device.cloud.RemoteAndroidVirtualDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.util.BinaryState;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.executor.ParallelDeviceExecutor;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * A {@link ITargetPreparer} that configures a device for testing based on provided {@link Option}s.
 *
 * <p>Requires a device where 'adb root' is possible, typically a userdebug build type.
 *
 * <p>Should be performed <strong>after</strong> a new build is flashed.
 *
 * <p><strong>Note:</strong> this preparer is meant for continuous testing labs and assumes that the
 * device under test will be flashed and wiped before the next run. As such, it does minimal clean
 * up during teardown and should not be used in a test module.
 */
@OptionClass(alias = "device-setup")
public class DeviceSetup extends BaseTargetPreparer implements IExternalDependency {

    // Networking
    @Option(name = "airplane-mode",
            description = "Turn airplane mode on or off")
    protected BinaryState mAirplaneMode = BinaryState.IGNORE;
    // ON:  settings put global airplane_mode_on 1
    //      am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true
    // OFF: settings put global airplane_mode_on 0
    //      am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false

    @Option(name = "data", description = "Turn mobile data on or off")
    protected BinaryState mData = BinaryState.IGNORE;
    // ON:  settings put global mobile_data 1
    //      svc data enable
    // OFF: settings put global mobile_data 0
    //      svc data disable

    @Option(name = "cell", description = "Turn cellular radio on or off")
    protected BinaryState mCell = BinaryState.IGNORE;
    // ON:  settings put global cell_on 1
    // OFF: settings put global cell_on 0

    @Option(name = "cell-auto-setting", description = "Turn wear cellular mediator on or off")
    protected BinaryState mCellAutoSetting = BinaryState.IGNORE;
    // ON:  settings put global clockwork_cell_auto_setting 1
    // OFF: settings put global clockwork_cell_auto_setting 0

    @Option(name = "wifi", description = "Turn wifi on or off")
    protected BinaryState mWifi = BinaryState.IGNORE;
    // ON:  settings put global wifi_on 1
    //      svc wifi enable
    // OFF: settings put global wifi_off 0
    //      svc wifi disable

    @Option(
            name = "skip-wifi-connection",
            description = "Whether or not to completely skip connecting to wifi.")
    private boolean mSkipWifi = false;

    @Option(name = "wifi-network",
            description = "The SSID of the network to connect to. Will only attempt to " +
            "connect to a network if set")
    protected String mWifiSsid = null;

    @Option(name = "wifi-psk",
            description = "The passphrase used to connect to a secured network")
    protected String mWifiPsk = null;

    @Option(name = "wifi-ssid-to-psk", description = "A map of wifi SSIDs to passwords.")
    protected Map<String, String> mWifiSsidToPsk = new LinkedHashMap<>();

    @Option(name = "wifi-watchdog",
            description = "Turn wifi watchdog on or off")
    protected BinaryState mWifiWatchdog = BinaryState.IGNORE;
    // ON:  settings put global wifi_watchdog 1
    // OFF: settings put global wifi_watchdog 0

    @Option(name = "disable-cw-wifi-mediator", description = "Turn wifi mediator on or off")
    protected BinaryState mDisableCwWifiMediator = BinaryState.IGNORE;
    // ON:  settings put global cw_disable_wifimediator 1
    // OFF: settings put global cw_disable_wifimediator 0

    @Option(
        name = "wifi-scan-always-enabled",
        description = "Turn wifi scan always enabled on or off"
    )
    protected BinaryState mWifiScanAlwaysEnabled = BinaryState.IGNORE;
    // ON:  settings put global wifi_scan_always_enabled 1
    // OFF: settings put global wifi_scan_always_enabled 0

    @Option(name = "ethernet",
            description = "Turn ethernet on or off")
    protected BinaryState mEthernet = BinaryState.IGNORE;
    // ON:  ifconfig eth0 up
    // OFF: ifconfig eth0 down

    @Option(name = "bluetooth",
            description = "Turn bluetooth on or off")
    protected BinaryState mBluetooth = BinaryState.IGNORE;
    // ON:  svc bluetooth enable
    // OFF: svc bluetooth disable

    @Option(name = "nfc",
            description = "Turn nfc on or off")
    protected BinaryState mNfc = BinaryState.IGNORE;
    // ON:  svc nfc enable
    // OFF: svc nfc disable

    // Screen
    @Option(name = "screen-adaptive-brightness",
            description = "Turn screen adaptive brightness on or off")
    protected BinaryState mScreenAdaptiveBrightness = BinaryState.IGNORE;
    // ON:  settings put system screen_brightness_mode 1
    // OFF: settings put system screen_brightness_mode 0

    @Option(name = "screen-brightness",
            description = "Set the screen brightness. This is uncalibrated from product to product")
    protected Integer mScreenBrightness = null;
    // settings put system screen_brightness $N

    @Option(name = "screen-always-on",
            description = "Turn 'screen always on' on or off. If ON, then screen-timeout-secs " +
            "must be unset. Will only work when the device is plugged in")
    protected BinaryState mScreenAlwaysOn = BinaryState.ON;
    // ON:  svc power stayon true
    // OFF: svc power stayon false

    @Option(name = "screen-timeout-secs",
            description = "Set the screen timeout in seconds. If set, then screen-always-on must " +
            "be OFF or DEFAULT")
    protected Long mScreenTimeoutSecs = null;
    // settings put system screen_off_timeout $(N * 1000)

    @Option(name = "screen-ambient-mode",
            description = "Turn screen ambient mode on or off")
    protected BinaryState mScreenAmbientMode = BinaryState.IGNORE;
    // ON:  settings put secure doze_enabled 1
    // OFF: settings put secure doze_enabled 0

    @Option(name = "wake-gesture",
            description = "Turn wake gesture on or off")
    protected BinaryState mWakeGesture = BinaryState.IGNORE;
    // ON:  settings put secure wake_gesture_enabled 1
    // OFF: settings put secure wake_gesture_enabled 0

    @Option(name = "screen-saver",
            description = "Turn screen saver on or off")
    protected BinaryState mScreenSaver = BinaryState.IGNORE;
    // ON:  settings put secure screensaver_enabled 1
    // OFF: settings put secure screensaver_enabled 0

    @Option(name = "notification-led",
            description = "Turn the notification led on or off")
    protected BinaryState mNotificationLed = BinaryState.IGNORE;
    // ON:  settings put system notification_light_pulse 1
    // OFF: settings put system notification_light_pulse 0

    @Option(name = "install-non-market-apps",
            description = "Allow or prevent non-market app to initiate an apk install request")
    protected BinaryState mInstallNonMarketApps = BinaryState.IGNORE;
    // ON:  settings put secure install_non_market_apps 1
    // OFF: settings put secure install_non_market_apps 0

    // Media
    @Option(name = "trigger-media-mounted",
            description = "Trigger a MEDIA_MOUNTED broadcast")
    protected boolean mTriggerMediaMounted = false;
    // am broadcast -a android.intent.action.MEDIA_MOUNTED -d file://${EXTERNAL_STORAGE}
    // --receiver-include-background

    // Location
    @Option(name = "location-gps", description = "Turn the GPS location on or off")
    protected BinaryState mLocationGps = BinaryState.IGNORE;
    // ON:  settings put secure location_providers_allowed +gps
    // OFF: settings put secure location_providers_allowed -gps

    @Option(name = "location-network",
            description = "Turn the network location on or off")
    protected BinaryState mLocationNetwork = BinaryState.IGNORE;
    // ON:  settings put secure location_providers_allowed +network
    // OFF: settings put secure location_providers_allowed -network

    // Sensor
    @Option(name = "auto-rotate",
            description = "Turn auto rotate on or off")
    protected BinaryState mAutoRotate = BinaryState.IGNORE;
    // ON:  settings put system accelerometer_rotation 1
    // OFF: settings put system accelerometer_rotation 0

    // Power
    @Option(name = "battery-saver-mode",
            description = "Turn battery saver mode manually on or off. If OFF but battery is " +
            "less battery-saver-trigger, the device will still go into battery saver mode")
    protected BinaryState mBatterySaver = BinaryState.IGNORE;
    // ON:  dumpsys battery set usb 0
    //      settings put global low_power 1
    // OFF: settings put global low_power 0

    @Option(name = "battery-saver-trigger",
            description = "Set the battery saver trigger level. Should be [1-99] to enable, or " +
            "0 to disable automatic battery saver mode")
    protected Integer mBatterySaverTrigger = null;
    // settings put global low_power_trigger_level $N

    @Option(name = "enable-full-battery-stats-history",
            description = "Enable full history for batterystats. This option is only " +
            "applicable for L+")
    protected boolean mEnableFullBatteryStatsHistory = false;
    // dumpsys batterystats --enable full-history

    @Option(name = "disable-doze",
            description = "Disable device from going into doze mode. This option is only " +
            "applicable for M+")
    protected boolean mDisableDoze = false;
    // dumpsys deviceidle disable

    // Time
    @Option(name = "auto-update-time",
            description = "Turn auto update time on or off")
    protected BinaryState mAutoUpdateTime = BinaryState.IGNORE;
    // ON:  settings put global auto_time 1
    // OFF: settings put global auto_time 0

    @Option(name = "auto-update-timezone", description = "Turn auto update timezone on or off")
    protected BinaryState mAutoUpdateTimezone = BinaryState.IGNORE;
    // ON:  settings put global auto_timezone 1
    // OFF: settings put global auto_timezone 0

    @Option(
            name = "set-timezone",
            description =
                    "Set timezone property by TZ name "
                            + "(http://en.wikipedia.org/wiki/List_of_tz_database_time_zones)")
    protected String mTimezone = null;

    @Option(name = "sync-timezone-with-host",
            description =
                    "Turn on or off that make the time zone of device sync with host")
    protected BinaryState mSyncTimezoneWithHost = BinaryState.IGNORE;
    // ON:  settings put global sync_timezone 1
    // OFF: settings put global sync_timezone 0

    // Calling
    @Option(name = "disable-dialing",
            description = "Disable dialing")
    protected boolean mDisableDialing = true;
    // setprop ro.telephony.disable-call true"

    @Option(name = "default-sim-data",
            description = "Set the default sim card slot for data. Leave unset for single SIM " +
            "devices")
    protected Integer mDefaultSimData = null;
    // settings put global multi_sim_data_call $N

    @Option(name = "default-sim-voice",
            description = "Set the default sim card slot for voice calls. Leave unset for single " +
            "SIM devices")
    protected Integer mDefaultSimVoice = null;
    // settings put global multi_sim_voice_call $N

    @Option(name = "default-sim-sms",
            description = "Set the default sim card slot for SMS. Leave unset for single SIM " +
            "devices")
    protected Integer mDefaultSimSms = null;
    // settings put global multi_sim_sms $N

    // Audio
    private static final boolean DEFAULT_DISABLE_AUDIO = true;
    @Option(name = "disable-audio",
            description = "Disable the audio")
    protected boolean mDisableAudio = DEFAULT_DISABLE_AUDIO;
    // setprop ro.audio.silent 1"

    @Option(name = "force-skip-system-props",
            description = "Force setup to not modify any device system properties. All other " +
            "system property options will be ignored")
    protected boolean mForceSkipSystemProps = false;

    @Option(
            name = "force-root-setup",
            description =
                    "Force switching to root before the setup.Root should only be need for system"
                        + " props, but adding this flag while transitioning in case someone reports"
                        + " issues.")
    private boolean mForceRoot = true;

    @Option(name = "force-skip-settings",
            description = "Force setup to not modify any device settings. All other setting " +
            "options will be ignored.")
    protected boolean mForceSkipSettings = false;

    @Option(name = "force-skip-run-commands",
            description = "Force setup to not run any additional commands. All other commands " +
            "will be ignored.")
    protected boolean mForceSkipRunCommands = false;

    @Option(name = "set-test-harness",
            description = "Set the read-only test harness flag on boot")
    protected boolean mSetTestHarness = true;
    // setprop ro.monkey 1
    // setprop ro.test_harness 1
    // setprop persist.sys.test_harness 1

    @Option(name = "hide-error-dialogs", description = "Turn on or off the error dialogs.")
    protected BinaryState mHideErrorDialogs = BinaryState.ON;
    // ON:  settings put global hide_error_dialogs 1
    // OFF: settings put global hide_error_dialogs 0

    @Option(
            name = "disable-dalvik-verifier",
            description =
                    "Disable the dalvik verifier on device. Allows package-private "
                            + "framework tests to run.")
    protected boolean mDisableDalvikVerifier = false;
    // setprop dalvik.vm.dexopt-flags v=n

    @Option(name = "set-property",
            description = "Set the specified property on boot. Option may be repeated but only " +
            "the last value for a given key will be set.")
    protected Map<String, String> mSetProps = new HashMap<>();

    @Option(
            name = "restore-properties",
            description =
                    "Restore previous /data/local.prop on tear down, restoring any properties"
                            + " DeviceSetup changed by modifying /data/local.prop.")
    protected boolean mRestoreProperties = false;

    protected File mPreviousProperties;

    @Option(name = "set-system-setting",
            description = "Change a system (non-secure) setting. Option may be repeated and all " +
            "key/value pairs will be set in order.")
    // Use a Multimap since it is possible for a setting to have multiple values for the same key
    protected MultiMap<String, String> mSystemSettings = new MultiMap<>();

    @Option(name = "set-secure-setting",
            description = "Change a secure setting. Option may be repeated and all key/value " +
            "pairs will be set in order.")
    // Use a Multimap since it is possible for a setting to have multiple values for the same key
    protected MultiMap<String, String> mSecureSettings = new MultiMap<>();

    @Option(name = "set-global-setting",
            description = "Change a global setting. Option may be repeated and all key/value " +
            "pairs will be set in order.")
    // Use a Multimap since it is possible for a setting to have multiple values for the same key
    protected MultiMap<String, String> mGlobalSettings = new MultiMap<>();

    @Option(
        name = "restore-settings",
        description = "Restore settings modified by this preparer on tear down."
    )
    protected boolean mRestoreSettings = false;

    @Option(
            name = "optimized-non-persistent-setup",
            description = "Feature to evaluate a faster non-persistent props setup.")
    private boolean mOptimizeNonPersistentSetup = true;

    @Option(
            name = "dismiss-setup-wizard",
            description = "Attempt to dismiss the setup wizard if present.")
    private boolean mDismissSetupWizard = true;

    @Option(
            name = "dismiss-setup-wizard-timeout",
            description = "Set the timeout for dismissing setup wizard in milli seconds.")
    private Long mDismissSetupWizardTimeout = 60 * 1000L;

    @Option(
            name = "dismiss-setup-wizard-retry-count",
            description = "Number of times to retry to dismiss setup wizard.")
    private int mDismissSetupWizardRetry = 2;

    private Map<String, String> mPreviousSystemSettings = new HashMap<>();
    private Map<String, String> mPreviousSecureSettings = new HashMap<>();
    private Map<String, String> mPreviousGlobalSettings = new HashMap<>();

    protected List<String> mRunCommandBeforeSettings = new ArrayList<>();

    @Option(name = "run-command",
            description = "Run an adb shell command. Option may be repeated")
    protected List<String> mRunCommandAfterSettings = new ArrayList<>();

    @Option(name = "disconnect-wifi-after-test",
            description = "Disconnect from wifi network after test completes.")
    private boolean mDisconnectWifiAfterTest = true;

    private static final long DEFAULT_MIN_EXTERNAL_STORAGE_KB = 500;
    @Option(name = "min-external-storage-kb",
            description="The minimum amount of free space in KB that must be present on device's " +
            "external storage.")
    protected long mMinExternalStorageKb = DEFAULT_MIN_EXTERNAL_STORAGE_KB;

    @Option(name = "local-data-path",
            description = "Optional local file path of test data to sync to device's external " +
            "storage. Use --remote-data-path to set remote location.")
    protected File mLocalDataFile = null;

    @Option(name = "remote-data-path",
            description = "Optional file path on device's external storage to sync test data. " +
            "Must be used with --local-data-path.")
    protected String mRemoteDataPath = null;

    @Option(
            name = "optimized-property-setting",
            description =
                    "If a property is already set to the desired value, don't reboot the device")
    protected boolean mOptimizedPropertySetting = true;

    // Deprecated options follow
    /**
     * @deprecated use min-external-storage-kb instead.
     */
    @Option(name = "min-external-store-space",
            description = "deprecated, use option min-external-storage-kb. The minimum amount of " +
            "free space in KB that must be present on device's external storage.")
    @Deprecated
    private long mDeprecatedMinExternalStoreSpace = DEFAULT_MIN_EXTERNAL_STORAGE_KB;

    /**
     * @deprecated use option disable-audio instead.
     */
    @Option(name = "audio-silent",
            description = "deprecated, use option disable-audio. set ro.audio.silent on boot.")
    @Deprecated
    private boolean mDeprecatedSetAudioSilent = DEFAULT_DISABLE_AUDIO;

    /**
     * @deprecated use option set-property instead.
     */
    @Option(name = "setprop",
            description = "deprecated, use option set-property. set the specified property on " +
            "boot. Format: --setprop key=value. May be repeated.")
    @Deprecated
    private Collection<String> mDeprecatedSetProps = new ArrayList<String>();

    @Option(
            name = "skip-virtual-device-teardown",
            description = "Whether or not to skip the teardown if it's a virtual device.")
    private boolean mSkipVirtualDeviceTeardown = true;

    @Option(
            name = "disable-device-config-sync",
            description = "Disable syncing device config with remote configuration server.")
    private boolean mDisableDeviceConfigSync = false;
    // device_config set_sync_disabled_for_tests persistent

    @Option(
            name = "disable-ramdump",
            description = "Will set the flag to disable ramdump on the device.")
    private boolean mDisableRamdump = false;

    @Option(name = "parallelize-core-setup")
    private boolean mParallelCoreSetup = false;

    private static final String PERSIST_PREFIX = "persist.";
    private static final String MEMTAG_BOOTCTL = "arm64.memtag.bootctl";

    private static final List<String> PROPERTIES_NEEDING_REBOOT =
            List.of(
                    // MEMTAG_BOOTCTL stores a value in the misc partition that gets applied on
                    // reboot.
                    MEMTAG_BOOTCTL,
                    // Zygote caches the value of this property because it's expected to reboot the
                    // system whenever this property changes.
                    "persist.debug.dalvik.vm.jdwp.enabled");

    public ITestDevice getDevice(TestInformation testInfo) {
        return testInfo.getDevice();
    }

    /** {@inheritDoc} */
    @Override
    public void setUp(TestInformation testInfo)
            throws DeviceNotAvailableException, BuildError, TargetSetupError {
        ITestDevice device = getDevice(testInfo);
        CLog.i("Performing setup on %s", device.getSerialNumber());

        if (mForceRoot && device.getOptions().isEnableAdbRoot()) {
            if (!device.enableAdbRoot()) {
                throw new TargetSetupError(
                        String.format("Failed to enable adb root on %s", device.getSerialNumber()),
                        device.getDeviceDescriptor(),
                        DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
            }
        }

        // Convert deprecated options into current options
        processDeprecatedOptions(device);
        // Convert options into settings and run commands
        processOptions(device);
        // Change system props (will reboot device)
        changeSystemProps(device);
        // Run commands designated to be run before changing settings
        runCommands(device, mRunCommandBeforeSettings);
        List<Callable<Boolean>> callableTasks = new ArrayList<>();

        callableTasks.add(
                () -> {
                    // Handle screen always on setting
                    handleScreenAlwaysOnSetting(device);
                    return true;
                });
        callableTasks.add(
                () -> {
                    // Change settings
                    changeSettings(device);
                    return true;
                });
        callableTasks.add(
                () -> {
                    // Connect wifi after settings since this may take a while
                    connectWifi(device);
                    return true;
                });
        callableTasks.add(
                () -> {
                    // Sync data after settings since this may take a while
                    syncTestData(device);
                    return true;
                });
        callableTasks.add(
                () -> {
                    // Throw an error if there is not enough storage space
                    checkExternalStoreSpace(device);
                    return true;
                });
        if (mDismissSetupWizard) {
            callableTasks.add(
                    () -> {
                        dismissSetupWizard(device);
                        return true;
                    });
        }
        if (mParallelCoreSetup) {
            ParallelDeviceExecutor<Boolean> executor =
                    new ParallelDeviceExecutor<Boolean>(callableTasks.size());
            executor.invokeAll(callableTasks, 5, TimeUnit.MINUTES);
            if (executor.hasErrors()) {
                List<Throwable> errors = executor.getErrors();
                // TODO: Handle throwing multi-exceptions, right now throw the first one.
                for (Throwable error : errors) {
                    if (error instanceof TargetSetupError) {
                        throw (TargetSetupError) error;
                    }
                    if (error instanceof BuildError) {
                        throw (BuildError) error;
                    }
                    if (error instanceof DeviceNotAvailableException) {
                        throw (DeviceNotAvailableException) error;
                    }
                    throw new RuntimeException(error);
                }
            }
        } else {
            // Handle screen always on setting
            handleScreenAlwaysOnSetting(device);
            // Change settings
            changeSettings(device);
            // Connect wifi after settings since this may take a while
            connectWifi(device);
            // Sync data after settings since this may take a while
            syncTestData(device);
            // Throw an error if there is not enough storage space
            checkExternalStoreSpace(device);
            if (mDismissSetupWizard) {
                dismissSetupWizard(device);
            }
        }
        // Run commands designated to be run after changing settings
        runCommands(device, mRunCommandAfterSettings);

        device.clearErrorDialogs();
    }

    /** {@inheritDoc} */
    @Override
    public void tearDown(TestInformation testInfo, Throwable e) throws DeviceNotAvailableException {
        ITestDevice device = testInfo.getDevice();
        // ignore tearDown if it's a stub device, since there is no real device to clean.
        if (device.getIDevice() instanceof StubDevice) {
            return;
        }
        if (device instanceof RemoteAndroidVirtualDevice && mSkipVirtualDeviceTeardown) {
            CLog.d("Skipping teardown on virtual device that will be deleted.");
            return;
        }
        if (e instanceof DeviceFailedToBootError) {
            CLog.d("boot failure: skipping teardown");
            return;
        }
        if (e instanceof DeviceNotAvailableException) {
            CLog.d("device not available: skipping teardown");
            return;
        }
        if (!TestDeviceState.ONLINE.equals(device.getDeviceState())) {
            CLog.d("device offline: skipping teardown");
            return;
        }
        CLog.i("Performing teardown on %s", device.getSerialNumber());

        // Only try to disconnect if wifi ssid is set since isWifiEnabled() is a heavy operation
        // which should be avoided when possible
        boolean wifiSet = mWifiSsid != null || !mWifiSsidToPsk.isEmpty();
        if (mDisconnectWifiAfterTest && wifiSet && device.isWifiEnabled()) {
            boolean result = device.disconnectFromWifi();
            if (result) {
                CLog.i("Successfully disconnected from wifi network on %s",
                        device.getSerialNumber());
            } else {
                CLog.w("Failed to disconnect from wifi network on %s", device.getSerialNumber());
            }
        }

        if (mRestoreProperties) {
            if (mPreviousProperties != null) {
                device.pushFile(mPreviousProperties, "/data/local.prop");
            } else {
                device.deleteFile("/data/local.prop");
            }
            device.reboot();
        }

        if (mRestoreSettings) {
            for (Map.Entry<String, String> entry : mPreviousSystemSettings.entrySet()) {
                device.setSetting("system", entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, String> entry : mPreviousGlobalSettings.entrySet()) {
                device.setSetting("global", entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, String> entry : mPreviousSecureSettings.entrySet()) {
                device.setSetting("secure", entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Processes the deprecated options converting them into the currently used options.
     * <p>
     * This method should be run before any other processing methods. Will throw a
     * {@link TargetSetupError} if the deprecated option overrides a specified non-deprecated
     * option.
     * </p>
     * @throws TargetSetupError if there is a conflict
     */
    public void processDeprecatedOptions(ITestDevice device) throws TargetSetupError {
        if (mDeprecatedMinExternalStoreSpace != DEFAULT_MIN_EXTERNAL_STORAGE_KB) {
            if (mMinExternalStorageKb != DEFAULT_MIN_EXTERNAL_STORAGE_KB) {
                throw new TargetSetupError("Deprecated option min-external-store-space conflicts " +
                        "with option min-external-storage-kb", device.getDeviceDescriptor());
            }
            mMinExternalStorageKb = mDeprecatedMinExternalStoreSpace;
        }

        if (mDeprecatedSetAudioSilent != DEFAULT_DISABLE_AUDIO) {
            if (mDisableAudio != DEFAULT_DISABLE_AUDIO) {
                throw new TargetSetupError("Deprecated option audio-silent conflicts with " +
                        "option disable-audio", device.getDeviceDescriptor());
            }
            mDisableAudio = mDeprecatedSetAudioSilent;
        }

        if (!mDeprecatedSetProps.isEmpty()) {
            if (!mSetProps.isEmpty()) {
                throw new TargetSetupError("Deprecated option setprop conflicts with option " +
                        "set-property ", device.getDeviceDescriptor());
            }
            for (String prop : mDeprecatedSetProps) {
                String[] parts = prop.split("=", 2);
                String key = parts[0].trim();
                String value = parts.length == 2 ? parts[1].trim() : "";
                mSetProps.put(key, value);
            }
        }
    }

    /**
     * Process all the {@link Option}s and turn them into system props, settings, or run commands.
     * Does not run any commands on the device at this time.
     * <p>
     * Exposed so that children classes may override this.
     * </p>
     *
     * @param device The {@link ITestDevice}
     * @throws DeviceNotAvailableException if the device is not available
     * @throws TargetSetupError if the {@link Option}s conflict
     */
    public void processOptions(ITestDevice device) throws DeviceNotAvailableException,
            TargetSetupError {
        setSettingForBinaryState(mData, mGlobalSettings, "mobile_data", "1", "0");
        setCommandForBinaryState(
                mData, mRunCommandAfterSettings, "svc data enable", "svc data disable");

        setSettingForBinaryState(mCell, mGlobalSettings, "cell_on", "1", "0");
        setSettingForBinaryState(
                mCellAutoSetting, mGlobalSettings, "clockwork_cell_auto_setting", "1", "0");

        setSettingForBinaryState(mWifi, mGlobalSettings, "wifi_on", "1", "0");
        setCommandForBinaryState(
                mWifi, mRunCommandAfterSettings, "svc wifi enable", "svc wifi disable");

        setSettingForBinaryState(mWifiWatchdog, mGlobalSettings, "wifi_watchdog", "1", "0");
        setSettingForBinaryState(
                mDisableCwWifiMediator, mGlobalSettings, "cw_disable_wifimediator", "1", "0");

        setSettingForBinaryState(mWifiScanAlwaysEnabled, mGlobalSettings,
                "wifi_scan_always_enabled", "1", "0");

        setCommandForBinaryState(mEthernet, mRunCommandAfterSettings,
                "ifconfig eth0 up", "ifconfig eth0 down");

        setCommandForBinaryState(
                mBluetooth,
                mRunCommandAfterSettings,
                "cmd bluetooth_manager enable && cmd bluetooth_manager wait-for-state:STATE_ON",
                "cmd bluetooth_manager disable && cmd bluetooth_manager wait-for-state:STATE_OFF");

        setCommandForBinaryState(mNfc, mRunCommandAfterSettings,
                "svc nfc enable", "svc nfc disable");

        if (mScreenBrightness != null && BinaryState.ON.equals(mScreenAdaptiveBrightness)) {
            throw new TargetSetupError("Option screen-brightness cannot be set when " +
                    "screen-adaptive-brightness is set to ON", device.getDeviceDescriptor());
        }

        setSettingForBinaryState(mScreenAdaptiveBrightness, mSystemSettings,
                "screen_brightness_mode", "1", "0");

        if (mScreenBrightness != null) {
            mSystemSettings.put("screen_brightness", Integer.toString(mScreenBrightness));
        }

        if (mScreenTimeoutSecs != null) {
            mSystemSettings.put("screen_off_timeout", Long.toString(mScreenTimeoutSecs * 1000));
        }

        setSettingForBinaryState(mScreenAmbientMode, mSecureSettings, "doze_enabled", "1", "0");

        setSettingForBinaryState(mWakeGesture, mSecureSettings, "wake_gesture_enabled", "1", "0");

        setSettingForBinaryState(mScreenSaver, mSecureSettings, "screensaver_enabled", "1", "0");

        setSettingForBinaryState(mNotificationLed, mSystemSettings,
                "notification_light_pulse", "1", "0");

        setSettingForBinaryState(mInstallNonMarketApps, mSecureSettings,
                "install_non_market_apps", "1", "0");

        if (mTriggerMediaMounted) {
            mRunCommandAfterSettings.add(
                    "am broadcast -a android.intent.action.MEDIA_MOUNTED -d "
                            + "file://${EXTERNAL_STORAGE} --receiver-include-background");
        }

        setSettingForBinaryState(mLocationGps, mSecureSettings,
                "location_providers_allowed", "+gps", "-gps");

        setSettingForBinaryState(mLocationNetwork, mSecureSettings,
                "location_providers_allowed", "+network", "-network");

        setSettingForBinaryState(mAutoRotate, mSystemSettings, "accelerometer_rotation", "1", "0");

        if (device.getApiLevel() < 22) {
            setCommandForBinaryState(mBatterySaver, mRunCommandBeforeSettings,
                "dumpsys battery set usb 0", null);
        } else {
            setCommandForBinaryState(mBatterySaver, mRunCommandBeforeSettings,
                "dumpsys battery unplug", null);
        }
        setSettingForBinaryState(mBatterySaver, mGlobalSettings, "low_power", "1", "0");

        if (mBatterySaverTrigger != null) {
            mGlobalSettings.put("low_power_trigger_level", Integer.toString(mBatterySaverTrigger));
        }

        if (mEnableFullBatteryStatsHistory) {
            mRunCommandAfterSettings.add("dumpsys batterystats --enable full-history");
        }

        if (mDisableDoze) {
            mRunCommandAfterSettings.add("dumpsys deviceidle disable");
        }

        setSettingForBinaryState(mAutoUpdateTime, mGlobalSettings, "auto_time", "1", "0");

        setSettingForBinaryState(mAutoUpdateTimezone, mGlobalSettings, "auto_timezone", "1", "0");

        if (BinaryState.ON.equals(mSyncTimezoneWithHost)) {
            if (mTimezone != null) {
                throw new TargetSetupError("Option set-timezone cannot be set when " +
                                               "sync-timezone-with-host is set to ON",
                                           device.getDeviceDescriptor(),
                                           InfraErrorIdentifier.OPTION_CONFIGURATION_ERROR);
            } else {
                mTimezone = TimeZone.getDefault().getID();
            }
        }

        setSettingForBinaryState(
                mHideErrorDialogs, mGlobalSettings, "hide_error_dialogs", "1", "0");

        if (mTimezone != null) {
            CLog.i("The actual timezone we set here is  %s", mTimezone);
            mSetProps.put("persist.sys.timezone", mTimezone);
        }

        if (mDisableDialing) {
            mSetProps.put("ro.telephony.disable-call", "true");
        }

        if (mDefaultSimData != null) {
            mGlobalSettings.put("multi_sim_data_call", Integer.toString(mDefaultSimData));
        }

        if (mDefaultSimVoice != null) {
            mGlobalSettings.put("multi_sim_voice_call", Integer.toString(mDefaultSimVoice));
        }

        if (mDefaultSimSms != null) {
            mGlobalSettings.put("multi_sim_sms", Integer.toString(mDefaultSimSms));
        }

        if (mDisableAudio) {
            mSetProps.put("ro.audio.silent", "1");
        }

        if (mSetTestHarness) {
            // set both ro.monkey, ro.test_harness, persist.sys.test_harness, for compatibility with
            // older platforms
            mSetProps.put("ro.monkey", "1");
            mSetProps.put("ro.test_harness", "1");
            mSetProps.put("persist.sys.test_harness", "1");
        }

        if (mDisableDalvikVerifier) {
            mSetProps.put("dalvik.vm.dexopt-flags", "v=n");
        }

        if (mDisableDeviceConfigSync) {
            mRunCommandBeforeSettings.add("device_config set_sync_disabled_for_tests persistent");
        }
    }

    /**
     * Change the system properties on the device.
     *
     * @param device The {@link ITestDevice}
     * @throws DeviceNotAvailableException if the device is not available
     * @throws TargetSetupError if there was a failure setting the system properties
     */
    private void changeSystemProps(ITestDevice device) throws DeviceNotAvailableException,
            TargetSetupError {
        if (mForceSkipSystemProps) {
            CLog.d("Skipping system props due to force-skip-system-props");
            return;
        }

        if (mSetProps.size() > 0 && !device.enableAdbRoot()) {
            throw new TargetSetupError(
                    String.format(
                            "Cannot set system props %s on %s without adb root. Setting "
                                    + "'force-skip-system-props' or 'enable-root' to avoid error",
                            mSetProps.toString(), device.getSerialNumber()),
                    device.getDeviceDescriptor(),
                    InfraErrorIdentifier.OPTION_CONFIGURATION_ERROR);
        }

        boolean needsReboot = false;
        // Set persistent props and build a map of all the nonpersistent ones
        Map<String, String> nonpersistentProps = new HashMap<String, String>();
        for (Map.Entry<String, String> prop : mSetProps.entrySet()) {
            // MEMTAG_BOOTCTL is essentially a persist property. It triggers an action that
            // stores the value in the misc partition, and gets applied and restored on
            // reboot.
            boolean isPersistProperty =
                    prop.getKey().startsWith(PERSIST_PREFIX)
                            || prop.getKey().equals(MEMTAG_BOOTCTL);

            if (isPersistProperty || mOptimizeNonPersistentSetup) {
                device.setProperty(prop.getKey(), prop.getValue());
            }

            if (!isPersistProperty) {
                nonpersistentProps.put(prop.getKey(), prop.getValue());
            }

            if (PROPERTIES_NEEDING_REBOOT.contains(prop.getKey())) {
                needsReboot = true;
            }
        }

        // If the reboot optimization is enabled, only set nonpersistent props if
        // there are changed values from what the device is running.
        boolean shouldSetProps = true;
        if (!mOptimizeNonPersistentSetup
                && mOptimizedPropertySetting
                && !nonpersistentProps.isEmpty()) {
            boolean allPropsAlreadySet = true;
            for (Map.Entry<String, String> prop : nonpersistentProps.entrySet()) {
                if (!prop.getValue().equals(device.getProperty(prop.getKey()))) {
                    allPropsAlreadySet = false;
                    break;
                }
            }
            if (allPropsAlreadySet) {
                shouldSetProps = false;
                CLog.i(
                        "All properties appear to already be set to desired values, skipping"
                                + " set stage");
            }
        }

        // Set the nonpersistent properties if needed.
        if (!nonpersistentProps.isEmpty() && shouldSetProps) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> prop : nonpersistentProps.entrySet()) {
                sb.append(String.format("%s=%s\n", prop.getKey(), prop.getValue()));
            }

            if (mRestoreProperties) {
                mPreviousProperties = device.pullFile("/data/local.prop");
            }
            CLog.d("Pushing the following properties to /data/local.prop:\n%s", sb.toString());
            boolean result = device.pushString(sb.toString(), "/data/local.prop");
            if (!result) {
                throw new TargetSetupError(
                        String.format(
                                "Failed to push /data/local.prop to %s", device.getSerialNumber()),
                        device.getDeviceDescriptor(),
                        DeviceErrorIdentifier.FAIL_PUSH_FILE);
            }
            // Set reasonable permissions for /data/local.prop
            device.executeShellCommand("chmod 644 /data/local.prop");

            if (mDisableRamdump) {
                device.rebootIntoBootloader();
                CLog.i("Disabling ramdump.");
                CommandResult resultRampdump =
                        device.executeFastbootCommand("oem", "ramdump", "disable");
                if (!CommandStatus.SUCCESS.equals(resultRampdump.getStatus())) {
                    CLog.w(
                            "Failed to run ramdump disable: status: %s\nstdout: %s\nstderr: %s",
                            resultRampdump.getStatus(),
                            resultRampdump.getStdout(),
                            resultRampdump.getStderr());
                }
            }
            if (!mOptimizeNonPersistentSetup) {
                // non-persistent properties do not trigger a reboot in this
                // new setup, if not explicitly set.
                needsReboot = true;
            }
        }

        if (needsReboot) {
            CLog.i("Rebooting %s due to system property change", device.getSerialNumber());
            device.reboot();
        }

        // Log nonpersistent device properties (that change/lose values after reboot).
        String deviceType = device.getClass().getTypeName();
        for (Map.Entry<String, String> prop : mSetProps.entrySet()) {
            String expected = prop.getValue();
            String actual = device.getProperty(prop.getKey());
            if ((expected != null && !expected.equals(actual))
                    || (expected == null && actual != null)) {
                String entry =
                        String.format("%s-%s(%s:%s)", deviceType, prop.getKey(), expected, actual);
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.NONPERSISTENT_DEVICE_PROPERTIES, entry);
            } else {
                String entry = String.format("%s-%s(%s)", deviceType, prop.getKey(), actual);
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.PERSISTENT_DEVICE_PROPERTIES, entry);
            }
        }
    }

    /**
     * Handles screen always on settings.
     * <p>
     * This is done in a dedicated function because special handling is required in case of setting
     * screen to always on.
     * @throws DeviceNotAvailableException
     */
    private void handleScreenAlwaysOnSetting(ITestDevice device)
            throws DeviceNotAvailableException {
        String cmd = "svc power stayon %s";
        switch (mScreenAlwaysOn) {
            case ON:
                CLog.d("Setting screen always on to true");
                device.executeShellCommand(String.format(cmd, "true"));
                // send MENU press in case keygaurd needs to be dismissed again
                device.executeShellCommand("input keyevent 82");
                // send HOME press in case keyguard was already dismissed, so we bring device back
                // to home screen
                // No need for this on Wear OS, since that causes the launcher to show
                // instead of the home screen
                if ((device instanceof TestDevice)
                        && !device.hasFeature("android.hardware.type.watch")) {
                    device.executeShellCommand("input keyevent 3");
                }
                break;
            case OFF:
                CLog.d("Setting screen always on to false");
                device.executeShellCommand(String.format(cmd, "false"));
                break;
            case IGNORE:
                break;
        }
    }

    /**
     * Change the settings on the device.
     * <p>
     * Exposed so children classes may override.
     * </p>
     *
     * @param device The {@link ITestDevice}
     * @throws DeviceNotAvailableException if the device is not available
     * @throws TargetSetupError if there was a failure setting the settings
     */
    public void changeSettings(ITestDevice device) throws DeviceNotAvailableException,
            TargetSetupError {
        if (mForceSkipSettings) {
            CLog.d("Skipping settings due to force-skip-setttings");
            return;
        }

        if (mSystemSettings.isEmpty() && mSecureSettings.isEmpty() && mGlobalSettings.isEmpty() &&
                BinaryState.IGNORE.equals(mAirplaneMode)) {
            CLog.d("No settings to change");
            return;
        }

        if (device.getApiLevel() < 22) {
            throw new TargetSetupError(String.format("Changing setting not supported on %s, " +
                    "must be API 22+", device.getSerialNumber()), device.getDeviceDescriptor());
        }

        // Special case airplane mode since it needs to be set before other connectivity settings
        // For example, it is possible to enable airplane mode and then turn wifi on
        String command = "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state %s";
        switch (mAirplaneMode) {
            case ON:
                CLog.d("Changing global setting airplane_mode_on to 1");
                device.setSetting("global", "airplane_mode_on", "1");
                if (!mForceSkipRunCommands) {
                    device.executeShellCommand(String.format(command, "true"));
                }
                break;
            case OFF:
                CLog.d("Changing global setting airplane_mode_on to 0");
                device.setSetting("global", "airplane_mode_on", "0");
                if (!mForceSkipRunCommands) {
                    device.executeShellCommand(String.format(command, "false"));
                }
                break;
            case IGNORE:
                // No-op
                break;
        }

        for (String key : mSystemSettings.keySet()) {
            for (String value : mSystemSettings.get(key)) {
                if (mRestoreSettings) {
                    String previousSetting = device.getSetting("system", key);
                    mPreviousSystemSettings.put(key, previousSetting);
                }
                CLog.d("Changing system setting %s to %s", key, value);
                device.setSetting("system", key, value);
            }
        }
        for (String key : mSecureSettings.keySet()) {
            for (String value : mSecureSettings.get(key)) {
                if (mRestoreSettings) {
                    String previousSetting = device.getSetting("secure", key);
                    mPreviousSecureSettings.put(key, previousSetting);
                }
                CLog.d("Changing secure setting %s to %s", key, value);
                device.setSetting("secure", key, value);
            }
        }

        for (String key : mGlobalSettings.keySet()) {
            for (String value : mGlobalSettings.get(key)) {
                if (mRestoreSettings) {
                    String previousSetting = device.getSetting("global", key);
                    mPreviousGlobalSettings.put(key, previousSetting);
                }
                CLog.d("Changing global setting %s to %s", key, value);
                device.setSetting("global", key, value);
            }
        }
    }

    /**
     * Execute additional commands on the device.
     *
     * @param device The {@link ITestDevice}
     * @param commands The list of commands to run
     * @throws DeviceNotAvailableException if the device is not available
     * @throws TargetSetupError if there was a failure setting the settings
     */
    private void runCommands(ITestDevice device, List<String> commands)
            throws DeviceNotAvailableException, TargetSetupError {
        if (mForceSkipRunCommands) {
            CLog.d("Skipping run commands due to force-skip-run-commands");
            return;
        }

        for (String command : commands) {
            device.executeShellCommand(command);
        }
    }

    /**
     * Connects device to Wifi if SSID is specified.
     *
     * @param device The {@link ITestDevice}
     * @throws DeviceNotAvailableException if the device is not available
     * @throws TargetSetupError if there was a failure setting the settings
     */
    private void connectWifi(ITestDevice device) throws DeviceNotAvailableException,
            TargetSetupError {
        if (mForceSkipRunCommands) {
            CLog.d("Skipping connect wifi due to force-skip-run-commands");
            return;
        }
        if ((mWifiSsid == null || mWifiSsid.isEmpty()) && mWifiSsidToPsk.isEmpty()) {
            return;
        }
        if (mSkipWifi) {
            CLog.d("Skipping wifi connection due to skip-wifi-connection");
            return;
        }

        if (mWifiSsid != null) {
            mWifiSsidToPsk.put(mWifiSsid, mWifiPsk);
        }
        if (device.connectToWifiNetwork(mWifiSsidToPsk)) {
            return;
        }

        if (mWifiSsid != null || !mWifiSsidToPsk.isEmpty()) {
            String network = (mWifiSsid == null) ? mWifiSsidToPsk.toString() : mWifiSsid;
            InfraErrorIdentifier errorIdentifier = InfraErrorIdentifier.WIFI_FAILED_CONNECT;
            if (device instanceof RemoteAndroidVirtualDevice
                    || device instanceof NestedRemoteDevice
                    || device instanceof LocalAndroidVirtualDevice) {
                // Error identifier for virtual devices.
                errorIdentifier = InfraErrorIdentifier.VIRTUAL_WIFI_FAILED_CONNECT;
            }
            throw new TargetSetupError(
                    String.format(
                            "Failed to connect to wifi network %s on %s",
                            network, device.getSerialNumber()),
                    device.getDeviceDescriptor(),
                    errorIdentifier);
        }
    }

    /**
     * Syncs a set of test data files, specified via local-data-path, to devices external storage.
     *
     * @param device The {@link ITestDevice}
     * @throws DeviceNotAvailableException if the device is not available
     * @throws TargetSetupError if data fails to sync
     */
    private void syncTestData(ITestDevice device) throws DeviceNotAvailableException,
            TargetSetupError {
        if (mLocalDataFile == null) {
            return;
        }

        if (!mLocalDataFile.exists() || !mLocalDataFile.isDirectory()) {
            throw new TargetSetupError(String.format(
                    "local-data-path %s is not a directory", mLocalDataFile.getAbsolutePath()),
                    device.getDeviceDescriptor());
        }
        String fullRemotePath = device.getIDevice().getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        if (fullRemotePath == null) {
            throw new TargetSetupError(String.format(
                    "failed to get external storage path on device %s", device.getSerialNumber()),
                    device.getDeviceDescriptor());
        }
        if (mRemoteDataPath != null) {
            fullRemotePath = String.format("%s/%s", fullRemotePath, mRemoteDataPath);
        }
        boolean result = device.syncFiles(mLocalDataFile, fullRemotePath);
        if (!result) {
            // TODO: get exact error code and respond accordingly
            throw new TargetSetupError(String.format(
                    "failed to sync test data from local-data-path %s to %s on device %s",
                    mLocalDataFile.getAbsolutePath(), fullRemotePath, device.getSerialNumber()),
                    device.getDeviceDescriptor());
        }
    }

    /**
     * Check that device external store has the required space
     *
     * @param device The {@link ITestDevice}
     * @throws DeviceNotAvailableException if the device is not available or if the device does not
     * have the required space
     */
    private void checkExternalStoreSpace(ITestDevice device) throws DeviceNotAvailableException {
        if (mMinExternalStorageKb <= 0) {
            return;
        }
        if (!(device instanceof TestDevice)) {
            // TODO: instead check that sdcard exists
            return;
        }
        // Wait for device available to ensure the mounting of sdcard
        device.waitForDeviceAvailable();
        long freeSpace = device.getExternalStoreFreeSpace();
        if (freeSpace < mMinExternalStorageKb) {
            throw new DeviceNotAvailableException(
                    String.format(
                            "External store free space %dK is less than required %dK for device %s",
                            freeSpace, mMinExternalStorageKb, device.getSerialNumber()),
                    device.getSerialNumber(),
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
        }
    }

    private void dismissSetupWizard(ITestDevice device) throws DeviceNotAvailableException {
        for (int i = 0; i < mDismissSetupWizardRetry; i++) {
            CommandResult cmd1 =
                    device.executeShellV2Command(
                            "am start -a com.android.setupwizard.FOUR_CORNER_EXIT"); // Android
            // UDC+
            CommandResult cmd2 =
                    device.executeShellV2Command(
                            "am start -a com.android.setupwizard.EXIT"); // Android L - T
            // if either of the command is successful, count it as success. Otherwise, retry.
            if (CommandStatus.SUCCESS.equals(cmd1.getStatus())
                    || CommandStatus.SUCCESS.equals(cmd2.getStatus())) {
                break;
            }
        }
        // verify setup wizard is dismissed
        CLog.d("Waiting %d ms for setup wizard to be dismissed.", mDismissSetupWizardTimeout);
        boolean dismissed = false;
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < mDismissSetupWizardTimeout) {
            CommandResult cmdOut =
                    device.executeShellV2Command("dumpsys window displays | grep mCurrentFocus");
            if (CommandStatus.SUCCESS.equals(cmdOut.getStatus())
                    && !cmdOut.getStdout().contains("setupwizard")) {
                CLog.d("Setup wizard is dismissed.");
                dismissed = true;
                break;
            } else {
                RunUtil.getDefault().sleep(2 * 1000);
            }
        }
        if (!dismissed) {
            CLog.w(
                    "Setup wizard was not dismissed within the timeout limit: %d ms.",
                    mDismissSetupWizardTimeout);
        }
    }

    /**
     * Helper method to add an ON/OFF setting to a setting map.
     *
     * @param state The {@link BinaryState}
     * @param settingsMap The {@link MultiMap} used to store the settings.
     * @param setting The setting key
     * @param onValue The value if ON
     * @param offValue The value if OFF
     */
    public static void setSettingForBinaryState(BinaryState state,
            MultiMap<String, String> settingsMap, String setting, String onValue, String offValue) {
        switch (state) {
            case ON:
                settingsMap.put(setting, onValue);
                break;
            case OFF:
                settingsMap.put(setting, offValue);
                break;
            case IGNORE:
                // Do nothing
                break;
        }
    }

    /**
     * Helper method to add an ON/OFF run command to be executed on the device.
     *
     * @param state The {@link BinaryState}
     * @param commands The list of commands to add the on or off command to.
     * @param onCommand The command to run if ON. Ignored if the command is {@code null}
     * @param offCommand The command to run if OFF. Ignored if the command is {@code null}
     */
    public static void setCommandForBinaryState(BinaryState state, List<String> commands,
            String onCommand, String offCommand) {
        switch (state) {
            case ON:
                if (onCommand != null) {
                    commands.add(onCommand);
                }
                break;
            case OFF:
                if (offCommand != null) {
                    commands.add(offCommand);
                }
                break;
            case IGNORE:
                // Do nothing
                break;
        }
    }

    /** Exposed for unit testing */
    protected void setForceSkipSystemProps(boolean force) {
        mForceSkipSystemProps = force;
    }

    protected void setForceRootSetup(boolean force) {
        mForceRoot = force;
    }

    public boolean isForceSkipSystemProps() {
        return mForceSkipSystemProps;
    }

    /**
     * Exposed for unit testing
     */
    protected void setAirplaneMode(BinaryState airplaneMode) {
        mAirplaneMode = airplaneMode;
    }

    /* Exposed for unit testing */
    @VisibleForTesting
    protected void setData(BinaryState data) {
        mData = data;
    }

    /* Exposed for unit testing */
    @VisibleForTesting
    protected void setCell(BinaryState cell) {
        mCell = cell;
    }

    /* Exposed for unit testing */
    @VisibleForTesting
    protected void setCellAutoSetting(BinaryState cellAutoSetting) {
        mCellAutoSetting = cellAutoSetting;
    }

    /**
     * Exposed for unit testing
     */
    protected void setWifi(BinaryState wifi) {
        mWifi = wifi;
    }

    /**
     * Exposed for unit testing
     */
    protected void setWifiNetwork(String wifiNetwork) {
        mWifiSsid = wifiNetwork;
    }

    /* Exposed for unit testing */
    @VisibleForTesting
    protected void setWifiPsk(String wifiPsk) {
        mWifiPsk = wifiPsk;
    }

    /* Exposed for unit testing */
    @VisibleForTesting
    protected void setWifiSsidToPsk(Map<String, String> wifiSssidToPsk) {
        mWifiSsidToPsk = wifiSssidToPsk;
    }

    /**
     * Exposed for unit testing
     */
    protected void setWifiWatchdog(BinaryState wifiWatchdog) {
        mWifiWatchdog = wifiWatchdog;
    }

    /* Exposed for unit testing */
    @VisibleForTesting
    protected void setDisableCwWifiMediator(BinaryState disableCwWifiMediator) {
        mDisableCwWifiMediator = disableCwWifiMediator;
    }

    /**
     * Exposed for unit testing
     */
    protected void setWifiScanAlwaysEnabled(BinaryState wifiScanAlwaysEnabled) {
        mWifiScanAlwaysEnabled = wifiScanAlwaysEnabled;
    }

    /**
     * Exposed for unit testing
     */
    protected void setEthernet(BinaryState ethernet) {
        mEthernet = ethernet;
    }

    /**
     * Exposed for unit testing
     */
    protected void setBluetooth(BinaryState bluetooth) {
        mBluetooth = bluetooth;
    }

    /**
     * Exposed for unit testing
     */
    protected void setNfc(BinaryState nfc) {
        mNfc = nfc;
    }

    /**
     * Exposed for unit testing
     */
    protected void setScreenAdaptiveBrightness(BinaryState screenAdaptiveBrightness) {
        mScreenAdaptiveBrightness = screenAdaptiveBrightness;
    }

    /**
     * Exposed for unit testing
     */
    protected void setScreenBrightness(Integer screenBrightness) {
        mScreenBrightness = screenBrightness;
    }

    /**
     * Exposed for unit testing
     */
    protected void setScreenAlwaysOn(BinaryState screenAlwaysOn) {
        mScreenAlwaysOn = screenAlwaysOn;
    }

    /**
     * Exposed for unit testing
     */
    protected void setScreenTimeoutSecs(Long screenTimeoutSecs) {
        mScreenTimeoutSecs = screenTimeoutSecs;
    }

    /**
     * Exposed for unit testing
     */
    protected void setScreenAmbientMode(BinaryState screenAmbientMode) {
        mScreenAmbientMode = screenAmbientMode;
    }

    /**
     * Exposed for unit testing
     */
    protected void setWakeGesture(BinaryState wakeGesture) {
        mWakeGesture = wakeGesture;
    }

    /**
     * Exposed for unit testing
     */
    protected void setScreenSaver(BinaryState screenSaver) {
        mScreenSaver = screenSaver;
    }

    /**
     * Exposed for unit testing
     */
    protected void setNotificationLed(BinaryState notificationLed) {
        mNotificationLed = notificationLed;
    }

    /**
     * Exposed for unit testing
     */
    protected void setInstallNonMarketApps(BinaryState installNonMarketApps) {
        mInstallNonMarketApps = installNonMarketApps;
    }

    /**
     * Exposed for unit testing
     */
    protected void setTriggerMediaMounted(boolean triggerMediaMounted) {
        mTriggerMediaMounted = triggerMediaMounted;
    }

    /**
     * Exposed for unit testing
     */
    protected void setLocationGps(BinaryState locationGps) {
        mLocationGps = locationGps;
    }

    /**
     * Exposed for unit testing
     */
    protected void setLocationNetwork(BinaryState locationNetwork) {
        mLocationNetwork = locationNetwork;
    }

    /**
     * Exposed for unit testing
     */
    protected void setAutoRotate(BinaryState autoRotate) {
        mAutoRotate = autoRotate;
    }

    /**
     * Exposed for unit testing
     */
    protected void setBatterySaver(BinaryState batterySaver) {
        mBatterySaver = batterySaver;
    }

    /**
     * Exposed for unit testing
     */
    protected void setBatterySaverTrigger(Integer batterySaverTrigger) {
        mBatterySaverTrigger = batterySaverTrigger;
    }

    /**
     * Exposed for unit testing
     */
    protected void setEnableFullBatteryStatsHistory(boolean enableFullBatteryStatsHistory) {
        mEnableFullBatteryStatsHistory = enableFullBatteryStatsHistory;
    }

    /**
     * Exposed for unit testing
     */
    protected void setDisableDoze(boolean disableDoze) {
        mDisableDoze = disableDoze;
    }

    /**
     * Exposed for unit testing
     */
    protected void setAutoUpdateTime(BinaryState autoUpdateTime) {
        mAutoUpdateTime = autoUpdateTime;
    }

    /**
     * Exposed for unit testing
     */
    protected void setAutoUpdateTimezone(BinaryState autoUpdateTimezone) {
        mAutoUpdateTimezone = autoUpdateTimezone;
    }

    /**
     * Exposed for unit testing
     */
    protected void setTimezone(String timezone) {
        mTimezone = timezone;
    }

    /**
     * Exposed for unit testing
     */
    protected void setDisableDialing(boolean disableDialing) {
        mDisableDialing = disableDialing;
    }

    /**
     * Exposed for unit testing
     */
    protected void setDefaultSimData(Integer defaultSimData) {
        mDefaultSimData = defaultSimData;
    }

    /**
     * Exposed for unit testing
     */
    protected void setDefaultSimVoice(Integer defaultSimVoice) {
        mDefaultSimVoice = defaultSimVoice;
    }

    /**
     * Exposed for unit testing
     */
    protected void setDefaultSimSms(Integer defaultSimSms) {
        mDefaultSimSms = defaultSimSms;
    }

    /**
     * Exposed for unit testing
     */
    protected void setDisableAudio(boolean disable) {
        mDisableAudio = disable;
    }

    /**
     * Exposed for unit testing
     */
    protected void setTestHarness(boolean setTestHarness) {
        mSetTestHarness = setTestHarness;
    }

    /**
     * Exposed for unit testing
     */
    protected void setDisableDalvikVerifier(boolean disableDalvikVerifier) {
        mDisableDalvikVerifier = disableDalvikVerifier;
    }

    /**
     * Exposed for unit testing
     */
    protected void setLocalDataPath(File path) {
        mLocalDataFile = path;
    }

    /**
     * Exposed for unit testing
     */
    protected void setMinExternalStorageKb(long storageKb) {
        mMinExternalStorageKb = storageKb;
    }

    /**
     * Exposed for unit testing
     */
    protected void setProperty(String key, String value) {
        mSetProps.put(key, value);
    }

    /** Exposed for unit testing */
    public void setGlobalSetting(String key, String value) {
        mGlobalSettings.put(key, value);
    }

    /** Exposed for unit testing */
    public void setSecureSetting(String key, String value) {
        mSecureSettings.put(key, value);
    }

    /** Exposed for unit testing */
    public void setSystemSetting(String key, String value) {
        mSystemSettings.put(key, value);
    }

    /** Exposed for unit testing */
    protected void setRestoreProperties(boolean restoreProperties) {
        mRestoreProperties = restoreProperties;
    }

    /** Exposed for unit testing */
    protected void setRestoreSettings(boolean restoreSettings) {
        mRestoreSettings = restoreSettings;
    }

    /**
     * Exposed for unit testing
     * @deprecated use {@link #setMinExternalStorageKb(long)} instead.
     */
    @Deprecated
    protected void setDeprecatedMinExternalStoreSpace(long storeSpace) {
        mDeprecatedMinExternalStoreSpace = storeSpace;
    }

    /**
     * Exposed for unit testing
     * @deprecated use {@link #setDisableAudio(boolean)} instead.
     */
    @Deprecated
    protected void setDeprecatedAudioSilent(boolean silent) {
        mDeprecatedSetAudioSilent = silent;
    }

    /**
     * Exposed for unit testing
     * @deprecated use {@link #setProperty(String, String)} instead.
     */
    @Deprecated
    protected void setDeprecatedSetProp(String prop) {
        mDeprecatedSetProps.add(prop);
    }

    @Override
    public Set<ExternalDependency> getDependencies() {
        Set<ExternalDependency> externalDependencies = new LinkedHashSet<>();
        // check if we need mobile data
        if (BinaryState.ON.equals(mData)) {
            externalDependencies.add(new TelephonyDependency());
        }
        // check if we need wifi
        if (!mSkipWifi && !(Strings.isNullOrEmpty(mWifiSsid) && mWifiSsidToPsk.isEmpty())) {
            externalDependencies.add(new NetworkDependency());
        }
        // check if we need ethernet
        if (BinaryState.ON.equals(mEthernet)) {
            externalDependencies.add(new EthernetDependency());
        }
        // check if we need bluetooth
        if (BinaryState.ON.equals(mBluetooth)) {
            externalDependencies.add(new BluetoothDependency());
        }
        // check if we need location-network
        if (BinaryState.ON.equals(mLocationNetwork)) {
            externalDependencies.add(new NetworkDependency());
        }
        return externalDependencies;
    }
}
