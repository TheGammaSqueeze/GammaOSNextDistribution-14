/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.tradefed.device;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.config.Option;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.MultiMap;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Container for {@link ITestDevice} {@link Option}s
 */
public class TestDeviceOptions {

    public enum InstanceType {
        /** A device that we remotely access via ssh and adb connect */
        GCE,
        REMOTE_AVD,
        /**
         * A remote device inside an emulator that we access via ssh to the instance hosting the
         * emulator then adb connect.
         */
        CUTTLEFISH,
        REMOTE_NESTED_AVD,
        /** An android emulator. */
        EMULATOR,
        /** Chrome OS VM (betty) */
        CHEEPS,
    }

    /** The size of the host which Oxygen virtual device will be running on. */
    private enum DeviceSize {
        STANDARD,
        LARGE,
        EXTRA_LARGE,
    }

    public static final int DEFAULT_ADB_PORT = 5555;
    public static final String INSTANCE_TYPE_OPTION = "instance-type";

    /** Do not provide a setter method for that Option as it might be misused. */
    @Option(name = "enable-root", description = "enable adb root on boot.")
    private boolean mEnableAdbRoot = true;

    @Option(name = "disable-keyguard",
            description = "attempt to disable keyguard once boot is complete.")
    private boolean mDisableKeyguard = true;

    @Option(name = "enable-logcat", description =
            "Enable background logcat capture when invocation is running.")
    private boolean mEnableLogcat = true;

    @Option(name = "max-tmp-logcat-file", description =
        "The maximum size of tmp logcat data to retain, in bytes. " +
        "Only used if --enable-logcat is set")
    private long mMaxLogcatDataSize = 20 * 1024 * 1024;

    @Option(name = "logcat-options", description =
            "Options to be passed down to logcat command, if unspecified, \"-v threadtime\" will " +
            "be used. Only used if --enable-logcat is set")
    private String mLogcatOptions = null;

    @Option(name = "fastboot-timeout", description =
            "time in ms to wait for a device to boot into fastboot.")
    private int mFastbootTimeout = 1 * 60 * 1000;

    @Option(name = "adb-command-timeout", description =
            "time to wait for an adb command.", isTimeVal = true)
    private long mAdbCommandTimeout = 2 * 60 * 1000;

    @Option(name = "adb-recovery-timeout", description =
            "time in ms to wait for a device to boot into recovery.")
    private int mAdbRecoveryTimeout = 1 * 60 * 1000;

    @Option(name = "reboot-timeout", description =
            "time in ms to wait for a device to reboot to full system.")
    private int mRebootTimeout = 2 * 60 * 1000;

    @Option(
            name = "device-fastboot-binary",
            description =
                    "The fastboot binary to use for the test session. If null, will use "
                            + "the same fastboot binary as DeviceManager.")
    private File mFastbootBinary = null;

    @Option(name = "use-fastboot-erase", description =
            "use fastboot erase instead of fastboot format to wipe partitions")
    private boolean mUseFastbootErase = false;

    @Option(name = "unencrypt-reboot-timeout", description = "time in ms to wait for the device to "
            + "format the filesystem and reboot after unencryption")
    private int mUnencryptRebootTimeout = 0;

    @Option(name = "online-timeout", description = "default time in ms to wait for the device to "
            + "be visible on adb.", isTimeVal = true)
    private long mOnlineTimeout = 1 * 60 * 1000;

    @Option(name = "available-timeout", description = "default time in ms to wait for the device "
            + "to be available aka fully boot.")
    private long mAvailableTimeout = 6 * 60 * 1000;

    @Option(
            name = "adb-root-unavailable-timeout",
            description = "time in ms to wait for a device to become unavailable after adb root.",
            isTimeVal = true)
    private long mAdbRootUnavailableTimeout = 2 * 1000;

    @Option(name = "conn-check-url",
            description = "default URL to be used for connectivity checks.")
    private String mConnCheckUrl = "http://www.google.com";

    @Option(
            name = "wifi-attempts",
            description = "default number of attempts to connect to wifi network.")
    private int mWifiAttempts = 4;

    @Option(
            name = "wifi-retry-wait-time",
            description =
                    "the base wait time in ms between wifi connect retries. "
                            + "The actual wait time would be a multiple of this value.")
    private int mWifiRetryWaitTime = 15 * 1000;

    @Option(
            name = "max-wifi-connect-time",
            isTimeVal = true,
            description = "the maximum amount of time to attempt to connect to wifi.")
    private long mMaxWifiConnectTime = 5 * 60 * 1000;

    @Option(
            name = "wifi-exponential-retry",
            description =
                    "Change the wifi connection retry strategy from a linear wait time into"
                            + " a binary exponential back-offs when retrying.")
    private boolean mWifiExpoRetryEnabled = false;

    @Option(name = "wifiutil-apk-path", description = "path to the wifiutil APK file")
    private String mWifiUtilAPKPath = null;

    @Option(
            name = "default-network-type",
            description =
                    "default network type to fallback to, if wifi helper fails to find the correct"
                            + " network type for the specified network.")
    private String mDefaultNetworkType = "wpa2";

    @Option(name = "post-boot-command",
            description = "shell command to run after reboots during invocation")
    private List<String> mPostBootCommands = new ArrayList<String>();

    @Option(name = "disable-reboot",
            description = "disables device reboots globally, making them no-ops")
    private boolean mDisableReboot = false;

    @Option(name = "cutoff-battery", description =
            "the minimum battery level required to continue the invocation. Scale: 0-100")
    private Integer mCutoffBattery = null;

    @Option(
        name = "use-content-provider",
        description =
                "Allow to disable the use of the content provider at the device level. "
                        + "This results in falling back to standard adb push/pull."
    )
    private boolean mUseContentProvider = true;

    @Option(
            name = "exit-status-workaround",
            description =
                    "On older devices that do not support ADB shell v2, use a workaround "
                            + "to get the exit status of shell commands")
    private boolean mExitStatusWorkaround = false;

    @Option(
            name = "use-updated-bootloader-status",
            description =
                    "Feature flag to test out an updated approach to bootloader state status.")
    private boolean mUpdatedBootloaderStatus = true;

    @Option(
            name = "bugreportz-timeout",
            description = "Timeout applied to bugreportz capture.",
            isTimeVal = true)
    private long mBugreportzTimeout = 5 * 60 * 1000;

    @Option(
            name = "enable-device-connection",
            description = "Use the new Connection descriptor for devices.")
    private boolean mEnableConnectionFeature = true;

    // ====================== Options Related to Virtual Devices ======================
    @Option(
            name = INSTANCE_TYPE_OPTION,
            description = "The type of virtual device instance to create")
    private InstanceType mInstanceType = InstanceType.GCE;

    @Option(
            name = "gce-boot-timeout",
            description = "timeout to wait in ms for GCE to be online.",
            isTimeVal = true)
    private long mGceCmdTimeout = 30 * 60 * 1000; // 30 minutes.

    @Option(
            name = "allow-gce-boot-timeout-override",
            description =
                    "Acloud can take boot-timeout as an arg already, this flag allows to use "
                            + "the Acloud value as a basis instead of the gce-boot-timeout option.")
    private boolean mAllowGceCmdTimeoutOverride = false;

    @Option(name = "gce-driver-path", description = "path of the binary to launch GCE devices")
    private File mAvdDriverBinary = null;

    @Option(
            name = "gce-driver-config-path",
            description = "path of the config to use to launch GCE devices.")
    private File mAvdConfigFile = null;

    @Option(
            name = "gce-driver-service-account-json-key-path",
            description = "path to the service account json key location.")
    private File mJsonKeyFile = null;

    @Option(
            name = "gce-private-key-path",
            description = "path to the ssh key private key location.")
    private File mSshPrivateKeyPath = new File("~/.ssh/id_rsa");

    @Option(name = "gce-driver-log-level", description = "Log level for gce driver")
    private LogLevel mGceDriverLogLevel = LogLevel.DEBUG;

    @Option(
        name = "gce-driver-param",
        description = "Additional args to pass to gce driver as parameters."
    )
    private List<String> mGceDriverParams = new ArrayList<>();

    @Option(
            name = "gce-driver-file-param",
            description =
                    "Additional file paths to pass to gce driver as parameters. For example, "
                            + "local-image=/path/to/image is converted to "
                            + "--local-image /path/to/image.")
    private MultiMap<String, File> mGceDriverFileParams = new MultiMap<>();

    @Deprecated
    @Option(
        name = "gce-driver-build-id-param",
        description =
                "The parameter to be paired with "
                        + "build id from build info when passed down to gce driver"
    )
    private String mGceDriverBuildIdParam = "build_id";

    @Option(name = "gce-account", description = "email account to use with GCE driver.")
    private String mGceAccount = null;

    @Option(
        name = "max-gce-attempt",
        description = "Maximum number of attempts to start Gce before throwing an exception."
    )
    private int mGceMaxAttempt = 1;

    @Option(
            name = "skip-gce-teardown",
            description =
                    "Whether or not to skip the GCE tear down. Skipping tear down will "
                            + "result in the instance being left.")
    private boolean mSkipTearDown = false;

    @Option(
            name = "use-oxygen",
            description = "Whether or not to use virtual devices created by Oxygen.")
    private boolean mUseOxygen = false;

    @Deprecated
    @Option(
            name = "use-oxygen-client",
            description = "Whether or not to use Oxygen client tool to create virtual devices.")
    private boolean mUseOxygenClient = true;

    @Option(name = "oxygen-target-region", description = "Oxygen device target region.")
    private String mOxygenTargetRegion = null;

    @Option(
            name = "oxygen-lease-length",
            description = "Oxygen device lease length.",
            isTimeVal = true)
    private long mOxygenLeaseLength = 600 * 60 * 1000;

    @Option(
            name = "oxygen-device-size",
            description = "The size of the host which Oxygen virtual device will be running on.")
    private DeviceSize mOxygenDeviceSize = DeviceSize.STANDARD;

    @Option(name = "oxygen-service-address", description = "Oxygen service address.")
    private String mOxygenServiceAddress = null;

    @Option(name = "oxygen-accounting-user", description = "Oxygen account user.")
    private String mOxygenAccountingUser = null;

    @Option(
            name = "extra-oxygen-args",
            description = "Extra arguments passed to Oxygen client to lease a device.")
    private Map<String, String> mExtraOxygenArgs = new LinkedHashMap<>();

    @Option(
            name = "wait-gce-teardown",
            description = "Whether or not to block on gce teardown before proceeding.")
    private boolean mWaitForGceTearDown = false;

    @Option(
            name = "instance-user",
            description =
                    "The account to be used to interact with the "
                            + "outer layer of the GCE VM, e.g. to SSH in")
    private String mInstanceUser = "root";

    @Option(
            name = "remote-adb-port",
            description = "The port on remote instance where the adb " + "server listens to.")
    private int mRemoteAdbPort = DEFAULT_ADB_PORT;

    @Option(
            name = "base-host-image",
            description = "The base image to be used for the GCE VM to host emulator.")
    private String mBaseImage = null;

    @Option(
        name = "remote-fetch-file-pattern",
        description =
                "Only for remote VM devices. Allows to specify patterns to fetch file on the "
                        + "remote VM via scp. Pattern must follow the scp notations."
    )
    private Set<String> mRemoteFetchFilePattern = new HashSet<>();

    @Option(name = "cros-user", description = "(CHEEPS ONLY) Account to log in to Chrome OS with.")
    private String mCrosUser = null;

    @Option(
        name = "cros-password",
        description =
                "(CHEEPS ONLY) Password to log in to Chrome OS with. Only used if cros-user "
                        + "is specified."
    )
    private String mCrosPassword = null;

    @Option(
            name = "invocation-attribute-to-metadata",
            description =
                    "Pass the named attribute found in invocation context to GCE driver (acloud)"
                            + " as metadata to be associated with the GCE VM. e.g. if invocation"
                            + " context has form_factor=phone, it'll be added to GCE VM as metadata"
                            + " form_factor=phone.")
    private List<String> mInvocationAttributeToMetadata = new ArrayList<>();

    @Option(
            name = "gce-extra-files",
            description =
                    "Path of extra files need to upload GCE instance during Acloud create."
                            + "Key is local file, value is GCE destination path.")
    private MultiMap<File, String> mGceExtraFiles = new MultiMap<>();

    @Option(
            name = "use-cmd-wifi",
            description = "Feature flag to switch the wifi connection to using cmd commands.")
    private boolean mUseCmdWidi = false;
    // END ====================== Options Related to Virtual Devices ======================

    // Option related to Remote Device only

    public static final String REMOTE_TF_VERSION_OPTION = "remote-tf-version";

    @Option(
        name = REMOTE_TF_VERSION_OPTION,
        description =
                "The TF to push to the remote VM to drive the invocation. If null, current TF "
                        + "will be pushed."
    )
    private File mRemoteTFVersion = null;

    /** Check whether adb root should be enabled on boot for this device */
    public boolean isEnableAdbRoot() {
        return mEnableAdbRoot;
    }

    /**
     * Check whether or not we should attempt to disable the keyguard once boot has completed
     */
    public boolean isDisableKeyguard() {
        return mDisableKeyguard;
    }

    /**
     * Set whether or not we should attempt to disable the keyguard once boot has completed
     */
    public void setDisableKeyguard(boolean disableKeyguard) {
        mDisableKeyguard = disableKeyguard;
    }

    /**
     * Get the approximate maximum size of a tmp logcat data to retain, in bytes.
     */
    public long getMaxLogcatDataSize() {
        return mMaxLogcatDataSize;
    }

    /**
     * Set the approximate maximum size of a tmp logcat to retain, in bytes
     */
    public void setMaxLogcatDataSize(long maxLogcatDataSize) {
        mMaxLogcatDataSize = maxLogcatDataSize;
    }

    /**
     * @return the timeout to send a command in msecs.
     */
    public long getAdbCommandTimeout() {
        return mAdbCommandTimeout;
    }

    /** Sets the timeout to send a command in msecs. */
    public void setAdbCommandTimeout(long adbCommandTimeout) {
        mAdbCommandTimeout = adbCommandTimeout;
    }

    /**
     * @return the timeout to boot into fastboot mode in msecs.
     */
    public int getFastbootTimeout() {
        return mFastbootTimeout;
    }

    /**
     * @param fastbootTimeout the timout in msecs to boot into fastboot mode.
     */
    public void setFastbootTimeout(int fastbootTimeout) {
        mFastbootTimeout = fastbootTimeout;
    }

    /**
     * @return the timeout in msecs to boot into recovery mode.
     */
    public int getAdbRecoveryTimeout() {
        return mAdbRecoveryTimeout;
    }

    /**
     * @param adbRecoveryTimeout the timeout in msecs to boot into recovery mode.
     */
    public void setAdbRecoveryTimeout(int adbRecoveryTimeout) {
        mAdbRecoveryTimeout = adbRecoveryTimeout;
    }

    /**
     * @return the timeout in msecs for the full system boot.
     */
    public int getRebootTimeout() {
        return mRebootTimeout;
    }

    /**
     * @param rebootTimeout the timeout in msecs for the system to fully boot.
     */
    public void setRebootTimeout(int rebootTimeout) {
        mRebootTimeout = rebootTimeout;
    }

    /**
     * @return whether to use fastboot erase instead of fastboot format to wipe partitions.
     */
    public boolean getUseFastbootErase() {
        return mUseFastbootErase;
    }

    /**
     * @param useFastbootErase whether to use fastboot erase instead of fastboot format to wipe
     * partitions.
     */
    public void setUseFastbootErase(boolean useFastbootErase) {
        mUseFastbootErase = useFastbootErase;
    }

    /** Returns a specified fastboot binary to be used. if null, use the DeviceManager one. */
    public File getFastbootBinary() {
        return mFastbootBinary;
    }

    /**
     * @return the timeout in msecs for the filesystem to be formatted and the device to reboot
     * after unencryption.
     */
    public int getUnencryptRebootTimeout() {
        return mUnencryptRebootTimeout;
    }

    /**
     * @param unencryptRebootTimeout the timeout in msecs for the filesystem to be formatted and
     * the device to reboot after unencryption.
     */
    public void setUnencryptRebootTimeout(int unencryptRebootTimeout) {
        mUnencryptRebootTimeout = unencryptRebootTimeout;
    }

    /**
     * @return the default time in ms to to wait for a device to be online.
     */
    public long getOnlineTimeout() {
        return mOnlineTimeout;
    }

    public void setOnlineTimeout(long onlineTimeout) {
        mOnlineTimeout = onlineTimeout;
    }

    /**
     * @return the default time in ms to to wait for a device to be available.
     */
    public long getAvailableTimeout() {
        return mAvailableTimeout;
    }

    /**
     * @return the time in ms to wait for a device to become unavailable after adb root.
     */
    public long getAdbRootUnavailableTimeout() {
        return mAdbRootUnavailableTimeout;
    }

    /**
     * @param adbRootUnavailableTimeout time in ms to wait for a device to become unavailable after
     *     adb root.
     */
    public void setAdbRootUnavailableTimeout(long adbRootUnavailableTimeout) {
        mAdbRootUnavailableTimeout = adbRootUnavailableTimeout;
    }

    /**
     * @return the default URL to be used for connectivity tests.
     */
    public String getConnCheckUrl() {
        return mConnCheckUrl;
    }

    public void setConnCheckUrl(String url) {
      mConnCheckUrl = url;
    }

    /**
     * @return true if background logcat capture is enabled
     */
    public boolean isLogcatCaptureEnabled() {
        return mEnableLogcat;
    }

    /**
     * @return the default number of attempts to connect to wifi network.
     */
    public int getWifiAttempts() {
        return mWifiAttempts;
    }

    public void setWifiAttempts(int wifiAttempts) {
        mWifiAttempts = wifiAttempts;
    }

    /**
     * @return the base wait time between wifi connect retries.
     */
    public int getWifiRetryWaitTime() {
        return mWifiRetryWaitTime;
    }

    /** @return the maximum time to attempt to connect to wifi. */
    public long getMaxWifiConnectTime() {
        return mMaxWifiConnectTime;
    }

    /**
     * @return a list of shell commands to run after reboots.
     */
    public List<String> getPostBootCommands() {
        return mPostBootCommands;
    }

    /**
     * @return the minimum battery level to continue the invocation.
     */
    public Integer getCutoffBattery() {
        return mCutoffBattery;
    }

    /**
     * set the minimum battery level to continue the invocation.
     */
    public void setCutoffBattery(int cutoffBattery) {
        if (cutoffBattery < 0 || cutoffBattery > 100) {
            // Prevent impossible value.
            throw new RuntimeException(String.format("Battery cutoff wasn't changed,"
                    + "the value %s isn't within possible range (0-100).", cutoffBattery));
        }
        mCutoffBattery = cutoffBattery;
    }

    /**
     * @return the configured logcat options
     */
    public String getLogcatOptions() {
        return mLogcatOptions;
    }

    /**
     * Set the options to be passed down to logcat
     */
    public void setLogcatOptions(String logcatOptions) {
        mLogcatOptions = logcatOptions;
    }

    /**
     * @return if device reboot should be disabled
     */
    public boolean shouldDisableReboot() {
        return mDisableReboot;
    }

    /**
     * @return if the exponential retry strategy should be used.
     */
    public boolean isWifiExpoRetryEnabled() {
        return mWifiExpoRetryEnabled;
    }

    /** @return the wifiutil apk path */
    public String getWifiUtilAPKPath() {
        return mWifiUtilAPKPath;
    }

    /** Returns the instance type of virtual device that should be created */
    public InstanceType getInstanceType() {
        return mInstanceType;
    }

    /** Sets the instance type of virtual device that should be created */
    public void setInstanceType(InstanceType type) {
        mInstanceType = type;
    }

    /** Returns whether or not the Tradefed content provider can be used to push/pull files. */
    public boolean shouldUseContentProvider() {
        return mUseContentProvider;
    }

    /**
     * Returns whether to use a workaround to get shell exit status on older devices without shell
     * v2.
     */
    public boolean useExitStatusWorkaround() {
        return mExitStatusWorkaround;
    }

    // =========================== Getter and Setter for Virtual Devices
    /** Return the Gce Avd timeout for the instance to come online. */
    public long getGceCmdTimeout() {
        return mGceCmdTimeout;
    }

    /** Set the Gce Avd timeout for the instance to come online. */
    public void setGceCmdTimeout(long gceCmdTimeout) {
        mGceCmdTimeout = gceCmdTimeout;
    }

    /** Returns whether or not we should rely on the boot-timeout args from acloud if present. */
    public boolean allowGceCmdTimeoutOverride() {
        return mAllowGceCmdTimeoutOverride;
    }

    /** Return the path to the binary to start the Gce Avd instance. */
    public File getAvdDriverBinary() {
        if (mAvdDriverBinary == null) {
            throw new HarnessRuntimeException(
                    "The avd driver binary is not specified.",
                    InfraErrorIdentifier.OPTION_CONFIGURATION_ERROR);
        }
        if (!mAvdDriverBinary.exists()) {
            throw new HarnessRuntimeException(
                    String.format(
                            "Could not find the avd driver binary at %s",
                            mAvdDriverBinary.getAbsolutePath()),
                    InfraErrorIdentifier.CONFIGURED_ARTIFACT_NOT_FOUND);
        }
        if (!FileUtil.ensureGroupRWX(mAvdDriverBinary)) {
            throw new HarnessRuntimeException(
                    String.format(
                            "Failed to change avd driver binary to be executable at %s",
                            mAvdDriverBinary.getAbsolutePath()),
                    InfraErrorIdentifier.CONFIGURED_ARTIFACT_NOT_FOUND);
        }
        return mAvdDriverBinary;
    }

    /** Set the path to the binary to start the Gce Avd instance. */
    public void setAvdDriverBinary(File avdDriverBinary) {
        mAvdDriverBinary = avdDriverBinary;
    }

    /** Return the Gce Avd config file to start the instance. */
    public File getAvdConfigFile() {
        return mAvdConfigFile;
    }

    /** Set the Gce Avd config file to start the instance. */
    public void setAvdConfigFile(File avdConfigFile) {
        mAvdConfigFile = avdConfigFile;
    }

    /** @return the service account json key file. */
    public File getServiceAccountJsonKeyFile() {
        return mJsonKeyFile;
    }

    /**
     * Set the service account json key file.
     *
     * @param jsonKeyFile the key file.
     */
    public void setServiceAccountJsonKeyFile(File jsonKeyFile) {
        mJsonKeyFile = jsonKeyFile;
    }

    /** Return the path of the ssh key to use for operations with the Gce Avd instance. */
    public File getSshPrivateKeyPath() {
        return mSshPrivateKeyPath;
    }

    /** Set the path of the ssh key to use for operations with the Gce Avd instance. */
    public void setSshPrivateKeyPath(File sshPrivateKeyPath) {
        mSshPrivateKeyPath = sshPrivateKeyPath;
    }

    /** Return the log level of the Gce Avd driver. */
    public LogLevel getGceDriverLogLevel() {
        return mGceDriverLogLevel;
    }

    /** Set the log level of the Gce Avd driver. */
    public void setGceDriverLogLevel(LogLevel mGceDriverLogLevel) {
        this.mGceDriverLogLevel = mGceDriverLogLevel;
    }

    /** Return the additional GCE driver parameters provided via option */
    public List<String> getGceDriverParams() {
        return mGceDriverParams;
    }

    /** Add a param to the gce driver params. */
    public void addGceDriverParams(String param) {
        mGceDriverParams.add(param);
    }

    /** Return the additional file paths as GCE driver parameters provided via option. */
    public MultiMap<String, File> getGceDriverFileParams() {
        return mGceDriverFileParams;
    }

    /** Set the GCE driver parameter that should be paired with the build id from build info */
    public void setGceDriverBuildIdParam(String gceDriverBuildIdParam) {
        mGceDriverBuildIdParam = gceDriverBuildIdParam;
    }

    /** Return the GCE driver parameter that should be paired with the build id from build info */
    public String getGceDriverBuildIdParam() {
        return mGceDriverBuildIdParam;
    }

    /** Return the gce email account to use with the driver */
    public String getGceAccount() {
        return mGceAccount;
    }

    /** Return the max number of attempts to start a gce device */
    public int getGceMaxAttempt() {
        if (mGceMaxAttempt < 1) {
            throw new RuntimeException("--max-gce-attempt cannot be bellow 1 attempt.");
        }
        return mGceMaxAttempt;
    }

    /** Set the max number of attempts to start a gce device */
    public void setGceMaxAttempt(int gceMaxAttempt) {
        mGceMaxAttempt = gceMaxAttempt;
    }

    /** Returns true if GCE tear down should be skipped. False otherwise. */
    public void setSkipTearDown(boolean shouldSkipTearDown) {
        mSkipTearDown = shouldSkipTearDown;
    }

    /** Returns true if GCE tear down should be skipped. False otherwise. */
    public boolean shouldSkipTearDown() {
        return mSkipTearDown;
    }

    /** Returns true if we should block on GCE tear down completion before proceeding. */
    public boolean waitForGceTearDown() {
        return mWaitForGceTearDown;
    }

    /** Returns true if use Oxygen to create virtual devices. False otherwise. */
    public boolean useOxygen() {
        return mUseOxygen;
    }

    /** Returns the instance user of GCE virtual device that should be created */
    public String getInstanceUser() {
        return mInstanceUser;
    }

    /** Set the instance user of GCE virtual device that should be created. */
    public void setInstanceUser(String instanceUser) {
        mInstanceUser = instanceUser;
    }

    /** Returns the remote port in instance that the adb server listens to */
    public int getRemoteAdbPort() {
        return mRemoteAdbPort;
    }

    /** Set the remote port in instance that the adb server listens to */
    public void setRemoteAdbPort(int remoteAdbPort) {
        mRemoteAdbPort = remoteAdbPort;
    }

    /** Returns the base image name to be used for the current instance */
    public String getBaseImage() {
        return mBaseImage;
    }

    /** Returns the list of pattern to attempt to fetch via scp. */
    public Set<String> getRemoteFetchFilePattern() {
        return mRemoteFetchFilePattern;
    }

    /** Returns the Chrome OS User to log in as. */
    public String getCrosUser() {
        return mCrosUser;
    }

    /** Returns the password to log in to Chrome OS with. */
    public String getCrosPassword() {
        return mCrosPassword;
    }

    /** The file pointing to the directory of the Tradefed version to be pushed to the remote. */
    public File getRemoteTf() {
        return mRemoteTFVersion;
    }

    /** Return the extra files need to upload to GCE during acloud create. */
    public MultiMap<File, String> getExtraFiles() {
        return mGceExtraFiles;
    }

    /** Set the extra files need to upload to GCE during acloud create. */
    public void setExtraFiles(MultiMap<File, String> extraFiles) {
        mGceExtraFiles = extraFiles;
    }

    /** Returns whether or not to use cmd wifi commands instead of apk. */
    public boolean useCmdWifiCommands() {
        return mUseCmdWidi;
    }

    public static String getCreateCommandByInstanceType(InstanceType type) {
        switch (type) {
            case CHEEPS:
            case GCE:
            case REMOTE_AVD:
            case CUTTLEFISH:
            case REMOTE_NESTED_AVD:
                return "create";
            case EMULATOR:
                return "create_gf";
        }
        throw new RuntimeException("Unexpected InstanceType: " + type);
    }

    public static List<String> getExtraParamsByInstanceType(InstanceType type, String baseImage) {
        if (InstanceType.EMULATOR.equals(type)) {
            // TODO(b/119440413) remove when base image can be passed via extra gce driver params
            List<String> params = ArrayUtil.list();
            if (baseImage != null) {
                params.add("--base_image");
                params.add(baseImage);
            }
            return params;
        }
        return Collections.emptyList();
    }

    public List<String> getInvocationAttributeToMetadata() {
        return mInvocationAttributeToMetadata;
    }

    /** Returns true if we want TradeFed directly call Oxygen to lease a device. */
    @Deprecated
    public boolean useOxygenProxy() {
        return mUseOxygenClient;
    }

    /** Returns the target region of the Oxygen device. */
    public String getOxygenTargetRegion() {
        return mOxygenTargetRegion;
    }

    /** Returns the length of leasing the Oxygen device in milliseconds. */
    public long getOxygenLeaseLength() {
        return mOxygenLeaseLength;
    }

    /** Returns The size of the host which Oxygen virtual device will be running on. */
    public DeviceSize getOxygenDeviceSize() {
        return mOxygenDeviceSize;
    }

    /** Returns the service address of the Oxygen device. */
    public String getOxygenServiceAddress() {
        return mOxygenServiceAddress;
    }

    /** Returns the accounting user of the Oxygen device. */
    public String getOxygenAccountingUser() {
        return mOxygenAccountingUser;
    }

    /** Returns the extra arguments to lease an Oxygen device. */
    public Map<String, String> getExtraOxygenArgs() {
        return mExtraOxygenArgs;
    }

    /** Returns whether or not to use the newer bootloader state status. */
    public boolean useUpdatedBootloaderStatus() {
        return mUpdatedBootloaderStatus;
    }

    /** Returns the timeout value to be applied to bugreportz capture. */
    public long getBugreportzTimeout() {
        return mBugreportzTimeout;
    }

    /** Return whether or not we should use the new connection feature. */
    public boolean shouldUseConnection() {
        return mEnableConnectionFeature;
    }

    public void setUseConnection(boolean useConnection) {
        mEnableConnectionFeature = useConnection;
    }

    public String getDefaultNetworkType() {
        return mDefaultNetworkType;
    }
}

