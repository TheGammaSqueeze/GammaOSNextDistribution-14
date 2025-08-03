/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.FileListingService;
import com.android.ddmlib.FileListingService.FileEntry;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.InstallException;
import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.SyncException.SyncError;
import com.android.ddmlib.SyncService;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.IWifiHelper.WifiConnectionResult;
import com.android.tradefed.device.cloud.GceAvdInfo;
import com.android.tradefed.device.connection.AbstractConnection;
import com.android.tradefed.device.connection.DefaultConnection;
import com.android.tradefed.device.connection.DefaultConnection.ConnectionBuilder;
import com.android.tradefed.device.contentprovider.ContentProviderHandler;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.host.IHostOptions;
import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.invoker.tracing.CloseableTraceScope;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestLifeCycleReceiver;
import com.android.tradefed.result.ITestLoggerReceiver;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.SnapshotInputStreamSource;
import com.android.tradefed.result.StubTestRunListener;
import com.android.tradefed.result.ddmlib.TestRunToTestInvocationForwarder;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.coverage.CoverageOptions.Toolchain;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.Bugreport;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.KeyguardControllerState;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.ProcessInfo;
import com.android.tradefed.util.QuotationAwareTokenizer;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.SizeLimitedOutputStream;
import com.android.tradefed.util.StringEscapeUtils;
import com.android.tradefed.util.SystemUtil;
import com.android.tradefed.util.TimeUtil;
import com.android.tradefed.util.ZipUtil2;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.FormatMethod;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/** Default implementation of a {@link ITestDevice} Non-full stack android devices. */
public class NativeDevice
        implements IManagedTestDevice, IConfigurationReceiver, ITestLoggerReceiver {

    protected static final String SD_CARD = "/sdcard/";
    protected static final String STORAGE_EMULATED = "/storage/emulated/";

    /** On-device path where we expect ANRs to be generated. */
    private static final String ANRS_PATH = "/data/anr";

    /**
     * Allow up to 2 minutes to receives the full logcat dump.
     */
    private static final int LOGCAT_DUMP_TIMEOUT = 2 * 60 * 1000;

    /** the default number of command retry attempts to perform */
    protected static final int MAX_RETRY_ATTEMPTS = 2;

    /** Value returned for any invalid/not found user id: UserHandle defined the -10000 value */
    public static final int INVALID_USER_ID = -10000;

    /** regex to match input dispatch readiness line **/
    static final Pattern INPUT_DISPATCH_STATE_REGEX =
            Pattern.compile("DispatchEnabled:\\s?([01])");
    /** regex to match build signing key type */
    private static final Pattern KEYS_PATTERN = Pattern.compile("^.*-keys$");

    private static final Pattern DF_PATTERN =
            Pattern.compile(
                    // Fs 1K-blks Used    Available Use%      Mounted on
                    "^/(\\S+)\\s+\\d+\\s+\\d+\\s+(\\d+)\\s+\\d+%\\s+/\\S*$", Pattern.MULTILINE);

    protected static final long MAX_HOST_DEVICE_TIME_OFFSET = 5 * 1000;

    /** The password for encrypting and decrypting the device. */
    private static final String ENCRYPTION_PASSWORD = "android";

    /** The maximum system_server start delay in seconds after device boot up */
    private static final int MAX_SYSTEM_SERVER_DELAY_AFTER_BOOT_UP_SEC = 25;

    /** The time in ms to wait before starting logcat for a device */
    private int mLogStartDelay = 5*1000;

    /** The time in ms to wait for a device to become unavailable. Should usually be short */
    private static final int DEFAULT_UNAVAILABLE_TIMEOUT = 20 * 1000;

    private static final String SIM_STATE_PROP = "gsm.sim.state";
    private static final String SIM_OPERATOR_PROP = "gsm.operator.alpha";

    static final String MAC_ADDRESS_PATTERN = "([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}";
    static final String MAC_ADDRESS_COMMAND = "su root cat /sys/class/net/wlan0/address";
    static final String ETHERNET_MAC_ADDRESS_COMMAND = "cat /sys/class/net/eth0/address";

    static final int ETHER_ADDR_LEN = 6;

    /** The network monitoring interval in ms. */
    private static final int NETWORK_MONITOR_INTERVAL = 10 * 1000;

    /** Wifi reconnect check interval in ms. */
    private static final int WIFI_RECONNECT_CHECK_INTERVAL = 1 * 1000;

    /** Wifi reconnect timeout in ms. */
    private static final int WIFI_RECONNECT_TIMEOUT = 60 * 1000;

    /** Pattern to find an executable file. */
    private static final Pattern EXE_FILE = Pattern.compile("^[-l]r.x.+");

    /** Path of the device containing the tombstones */
    private static final String TOMBSTONE_PATH = "/data/tombstones/";

    private static final long PROPERTY_GET_TIMEOUT = 45 * 1000L;

    public static final String DEBUGFS_PATH = "/sys/kernel/debug";
    private static final String CHECK_DEBUGFS_MNT_COMMAND =
            String.format("mountpoint -q %s", DEBUGFS_PATH);
    private static final String MOUNT_DEBUGFS_COMMAND =
            String.format("mount -t debugfs debugfs %s", DEBUGFS_PATH);
    private static final String UNMOUNT_DEBUGFS_COMMAND = String.format("umount %s", DEBUGFS_PATH);

    /** Version number for a current development build */
    private static final int CUR_DEVELOPMENT_VERSION = 10000;

    /** The time in ms to wait for a 'long' command to complete. */
    private long mLongCmdTimeout = 25 * 60 * 1000L;



    /**
     * The delimiter that separates the actual shell output and the exit status.
     *
     * <p>Used to determine the exit status of the command run by adb shell for devices that do not
     * support shell_v2.
     */
    private static final String EXIT_STATUS_DELIMITER = "x";

    private IConfiguration mConfiguration;
    private IDevice mIDevice;
    private IDeviceRecovery mRecovery = new WaitDeviceRecovery();
    protected final IDeviceStateMonitor mStateMonitor;
    private TestDeviceState mState = TestDeviceState.ONLINE;
    private final ReentrantLock mFastbootLock = new ReentrantLock();
    private LogcatReceiver mLogcatReceiver;
    private boolean mFastbootEnabled = true;
    private String mFastbootPath = "fastboot";

    protected TestDeviceOptions mOptions = new TestDeviceOptions();
    private Process mEmulatorProcess;
    private SizeLimitedOutputStream mEmulatorOutput;
    private Clock mClock = Clock.systemUTC();

    private RecoveryMode mRecoveryMode = RecoveryMode.AVAILABLE;

    private Boolean mIsEncryptionSupported = null;
    private ReentrantLock mAllocationStateLock = new ReentrantLock(true /*fair*/);

    @GuardedBy("mAllocationStateLock")
    private DeviceAllocationState mAllocationState = DeviceAllocationState.Unknown;
    private IDeviceMonitor mAllocationMonitor = null;

    private String mLastConnectedWifiSsid = null;
    private String mLastConnectedWifiPsk = null;
    private boolean mNetworkMonitorEnabled = false;

    private ContentProviderHandler mContentProvider = null;
    private boolean mShouldSkipContentProviderSetup = false;
    /** Keep track of the last time Tradefed itself triggered a reboot. */
    private long mLastTradefedRebootTime = 0L;

    private File mExecuteShellCommandLogs = null;

    private DeviceDescriptor mCachedDeviceDescriptor = null;
    private final Object mCacheLock = new Object();

    private String mFastbootSerialNumber = null;
    private File mUnpackedFastbootDir = null;
    // Connection for the device.
    private AbstractConnection mConnection;
    private GceAvdInfo mConnectionAvd;

    private ITestLogger mTestLogger;

    private List<IDeviceActionReceiver> mDeviceActionReceivers = new LinkedList<>();
    /**
     * Whether callback for reboot is currently executing or not. Use this flag to avoid dead loop
     * scenarios like calling reboot inside a callback happening for reboot.
     */
    private boolean inRebootCallback = false;

    /** If the device is a Microdroid, this refers to the VM process. Otherwise, it is null. */
    private Process mMicrodroidProcess = null;

    private final LoadingCache<String, String> mPropertiesCache;

    // If we increase the number of props, then increase the cache size of mPropertiesCache.
    private final Set<String> propsToPrefetch =
            ImmutableSet.of("ro.build.version.sdk", "ro.build.version.codename", "ro.build.id");
    // Avoid caching any properties in those namespace
    private static final Set<String> NEVER_CACHE_PROPERTIES =
            ImmutableSet.of("vendor.debug", "ro.boot");

    /** Interface for a generic device communication attempt. */
    abstract interface DeviceAction {

        /**
         * Execute the device operation.
         *
         * @return <code>true</code> if operation is performed successfully, <code>false</code>
         *     otherwise
         * @throws IOException, TimeoutException, AdbCommandRejectedException,
         *     ShellCommandUnresponsiveException, InstallException, SyncException if operation
         *     terminated abnormally
         */
        public boolean run()
                throws IOException, TimeoutException, AdbCommandRejectedException,
                        ShellCommandUnresponsiveException, InstallException, SyncException,
                        DeviceNotAvailableException;
    }

    /**
     * A {@link DeviceAction} for running a OS 'adb ....' command.
     */
    protected class AdbAction implements DeviceAction {
        /** the output from the command */
        String mOutput = null;
        private String[] mCmd;
        private long mTimeout;
        private boolean mIsShellCommand;
        private Map<String, String> mEnvMap;

        AdbAction(long timeout, String[] cmd, boolean isShell, Map<String, String> envMap) {
            mTimeout = timeout;
            mCmd = cmd;
            mIsShellCommand = isShell;
            mEnvMap = envMap;
        }

        private void logExceptionAndOutput(CommandResult result) {
            CLog.w("Command exited with status: %s", result.getStatus().toString());
            CLog.w("Command stdout:\n%s\n", result.getStdout());
            CLog.w("Command stderr:\n%s\n", result.getStderr());
        }

        @Override
        public boolean run() throws TimeoutException, IOException {
            IRunUtil runUtil = getRunUtil();
            if (!mEnvMap.isEmpty()) {
                runUtil = createRunUtil();
            }
            for (String key : mEnvMap.keySet()) {
                runUtil.setEnvVariable(key, mEnvMap.get(key));
            }
            CommandResult result = runUtil.runTimedCmd(mTimeout, mCmd);
            // TODO: how to determine device not present with command failing for other reasons
            if (result.getStatus() == CommandStatus.EXCEPTION) {
                logExceptionAndOutput(result);
                throw new IOException("CommandStatus was EXCEPTION, details in host log");
            } else if (result.getStatus() == CommandStatus.TIMED_OUT) {
                logExceptionAndOutput(result);
                throw new TimeoutException("CommandStatus was TIMED_OUT, details in host log");
            } else if (result.getStatus() == CommandStatus.FAILED) {

                logExceptionAndOutput(result);
                if (mIsShellCommand) {
                    // Interpret as communication failure for shell commands
                    throw new IOException("CommandStatus was FAILED, details in host log");
                } else {
                    mOutput = result.getStdout();
                    return false;
                }
            }
            mOutput = result.getStdout();
            return true;
        }
    }

    protected class AdbShellAction implements DeviceAction {
        /** the output from the command */
        CommandResult mResult = null;

        private String[] mCmd;
        private long mTimeout;
        private File mPipeAsInput; // Used in pushFile, uses local file as input to "content write"
        private OutputStream mPipeToOutput; // Used in pullFile, to pipe content from "content read"
        private OutputStream mPipeToError;

        AdbShellAction(
                String[] cmd,
                File pipeAsInput,
                OutputStream pipeToOutput,
                OutputStream pipeToError,
                long timeout) {
            mCmd = cmd;
            mPipeAsInput = pipeAsInput;
            mPipeToOutput = pipeToOutput;
            mPipeToError = pipeToError;
            mTimeout = timeout;
        }

        @Override
        public boolean run() throws TimeoutException, IOException {
            if (mPipeAsInput != null) {
                mResult = getRunUtil().runTimedCmdWithInputRedirect(mTimeout, mPipeAsInput, mCmd);
            } else {
                mResult = getRunUtil().runTimedCmd(mTimeout, mPipeToOutput, mPipeToError, mCmd);
            }
            if (mResult.getStatus() == CommandStatus.EXCEPTION) {
                throw new IOException(mResult.getStderr());
            } else if (mResult.getStatus() == CommandStatus.TIMED_OUT) {
                throw new TimeoutException(mResult.getStderr());
            }
            String stdErr = mResult.getStderr();
            if (stdErr != null) {
                stdErr = stdErr.trim();
                if (stdErr.contains("device offline")) {
                    throw new IOException(stdErr);
                }
            }
            // If it's not some issue with running the adb command, then we return the CommandResult
            // which will contain all the infos.
            return true;
        }
    }

    /** {@link DeviceAction} for rebooting a device. */
    protected class RebootDeviceAction implements DeviceAction {

        private final RebootMode mRebootMode;
        @Nullable private final String mReason;

        RebootDeviceAction(RebootMode rebootMode, @Nullable String reason) {
            mRebootMode = rebootMode;
            mReason = reason;
        }

        public boolean isFastbootOrBootloader() {
            return mRebootMode == RebootMode.REBOOT_INTO_BOOTLOADER
                    || mRebootMode == RebootMode.REBOOT_INTO_FASTBOOTD;
        }

        @Override
        public boolean run()
                throws TimeoutException, IOException, AdbCommandRejectedException,
                        DeviceNotAvailableException {
            // Notify of reboot started for all modes
            notifyRebootStarted();
            getIDevice().reboot(mRebootMode.formatRebootCommand(mReason));
            return true;
        }
    }

    /**
     * Creates a {@link TestDevice}.
     *
     * @param device the associated {@link IDevice}
     * @param stateMonitor the {@link IDeviceStateMonitor} mechanism to use
     * @param allocationMonitor the {@link IDeviceMonitor} to inform of allocation state changes.
     *            Can be null
     */
    public NativeDevice(IDevice device, IDeviceStateMonitor stateMonitor,
            IDeviceMonitor allocationMonitor) {
        throwIfNull(device);
        throwIfNull(stateMonitor);
        mIDevice = device;
        mStateMonitor = stateMonitor;
        mAllocationMonitor = allocationMonitor;
        // Keep a short timeout to expire key in case of large state changes
        // such as flashing
        mPropertiesCache =
                CacheBuilder.newBuilder()
                        .maximumSize(50)
                        .expireAfterAccess(2, TimeUnit.MINUTES)
                        .build(
                                new CacheLoader<String, String>() {
                                    @Override
                                    public String load(String key) {
                                        throw new IllegalStateException("Should never be called");
                                    }
                                });
    }

    /** Get the {@link RunUtil} instance to use. */
    @VisibleForTesting
    protected IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    protected IRunUtil createRunUtil() {
        return new RunUtil();
    }

    /** Set the Clock instance to use. */
    @VisibleForTesting
    protected void setClock(Clock clock) {
        mClock = clock;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setOptions(TestDeviceOptions options) {
        throwIfNull(options);
        mOptions = options;
        if (mOptions.getFastbootBinary() != null) {
            // Setup fastboot- if it's zipped, unzip it
            // TODO: Dedup the logic with DeviceManager
            if (".zip".equals(FileUtil.getExtension(mOptions.getFastbootBinary().getName()))) {
                // Unzip the fastboot files
                try {
                    mUnpackedFastbootDir =
                            ZipUtil2.extractZipToTemp(
                                    mOptions.getFastbootBinary(), "unpacked-fastboot");
                    File unpackedFastboot = FileUtil.findFile(mUnpackedFastbootDir, "fastboot");
                    if (unpackedFastboot == null) {
                        throw new HarnessRuntimeException(
                                String.format(
                                        "device-fastboot-binary was set, but didn't contain a"
                                                + " fastboot binary."),
                                InfraErrorIdentifier.OPTION_CONFIGURATION_ERROR);
                    }
                    setFastbootPath(unpackedFastboot.getAbsolutePath());
                } catch (IOException e) {
                    CLog.e("Failed to unpacked zipped fastboot.");
                    CLog.e(e);
                    FileUtil.recursiveDelete(mUnpackedFastbootDir);
                    mUnpackedFastbootDir = null;
                }
            } else {
                setFastbootPath(mOptions.getFastbootBinary().getAbsolutePath());
            }
        }
        mStateMonitor.setDefaultOnlineTimeout(options.getOnlineTimeout());
        mStateMonitor.setDefaultAvailableTimeout(options.getAvailableTimeout());
    }

    /**
     * Sets the max size of a tmp logcat file.
     *
     * @param size max byte size of tmp file
     */
    void setTmpLogcatSize(long size) {
        mOptions.setMaxLogcatDataSize(size);
    }

    /**
     * Sets the time in ms to wait before starting logcat capture for a online device.
     *
     * @param delay the delay in ms
     */
    public void setLogStartDelay(int delay) {
        mLogStartDelay = delay;
    }

    /** {@inheritDoc} */
    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfiguration = configuration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IDevice getIDevice() {
        synchronized (mIDevice) {
            return mIDevice;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIDevice(IDevice newDevice) {
        throwIfNull(newDevice);
        IDevice currentDevice = mIDevice;
        if (!getIDevice().equals(newDevice)) {
            synchronized (currentDevice) {
                mIDevice = newDevice;
            }
            mStateMonitor.setIDevice(mIDevice);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSerialNumber() {
        return getIDevice().getSerialNumber();
    }

    /**
     * Fetch a device property, from the ddmlib cache by default, and falling back to either `adb
     * shell getprop` or `fastboot getvar` depending on whether the device is in Fastboot or not.
     *
     * @param propName The name of the device property as returned by `adb shell getprop`
     * @param fastbootVar The name of the equivalent fastboot variable to query. if {@code null},
     *     fastboot query will not be attempted
     * @param description A simple description of the variable. First letter should be capitalized.
     * @return A string, possibly {@code null} or empty, containing the value of the given property
     */
    protected String internalGetProperty(String propName, String fastbootVar, String description)
            throws DeviceNotAvailableException, UnsupportedOperationException {
        if (isStateBootloaderOrFastbootd() && fastbootVar != null) {
            CLog.i("Device %s is in fastboot mode, re-querying with '%s' for %s", getSerialNumber(),
                   fastbootVar, description);
            return getFastbootVariable(fastbootVar);
        }
        String propValue = getProperty(propName);
        if (propValue != null) {
            return propValue;
        } else {
            CLog.d(
                    "property collection '%s' for device %s is null.",
                    description, getSerialNumber());
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProperty(final String name) throws DeviceNotAvailableException {
        return getPropertyWithRecovery(name, false);
    }

    /** Version of getProperty that allows to check device status and trigger recovery if needed. */
    private String getPropertyWithRecovery(final String name, boolean recovery)
            throws DeviceNotAvailableException {
        if (getIDevice() instanceof StubDevice) {
            return null;
        }
        String property = mPropertiesCache.getIfPresent(name);
        if (property != null) {
            CLog.d("Using property %s=%s from cache.", name, property);
            return property;
        }
        try (CloseableTraceScope getProp = new CloseableTraceScope("get_property:" + name)) {
            TestDeviceState state = getDeviceState();
            if (!TestDeviceState.ONLINE.equals(state) && !TestDeviceState.RECOVERY.equals(state)) {
                if (recovery) {
                    // Only query property for online device so trigger recovery before getting
                    // property.
                    recoverDevice();
                } else {
                    if (mStateMonitor.waitForDeviceOnline() == null) {
                        CLog.w(
                                "Waited for device %s to be online but it is in state '%s', cannot "
                                        + "get property %s.",
                                getSerialNumber(), getDeviceState(), name);
                        CLog.w(
                                new RuntimeException(
                                        "This is not an actual exception but to help"
                                                + " debugging. If this happens deterministically, "
                                                + " it means the caller has wrong assumption of "
                                                + " device state and is wasting time in waiting."));
                        return null;
                    }
                }
            }
            String cmd = String.format("getprop %s", name);
            CommandResult result =
                    executeShellV2Command(cmd, PROPERTY_GET_TIMEOUT, TimeUnit.MILLISECONDS, 0);
            if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                CLog.e(
                        "Failed to run '%s' returning null. stdout: %s\nstderr: %s\nexit code: %s",
                        cmd, result.getStdout(), result.getStderr(), result.getExitCode());
                if (result.getStderr().contains("device offline")) {
                    if (recovery) {
                        recoverDevice();
                        return getPropertyWithRecovery(name, false);
                    }
                    throw new DeviceNotAvailableException(
                            String.format("Device went offline when querying property: %s", name),
                            getSerialNumber(),
                            DeviceErrorIdentifier.DEVICE_UNAVAILABLE);
                }
                return null;
            }
            if (result.getStdout() == null || result.getStdout().trim().isEmpty()) {
                return null;
            }
            property = result.getStdout().trim();
            if (property != null) {
                if (!NEVER_CACHE_PROPERTIES.stream().anyMatch(p -> name.startsWith(p))) {
                    // Manage the cache manually to maintain exception handling
                    mPropertiesCache.put(name, property);
                }
            }
            return property;
        }
    }

    /**
     * Micro optimization (about 400 millis) by prefetching all props we need rather than call 'adb
     * getprop' for each one. i.e. It is just as fast to fetch all properties as it is to fetch one.
     * Things like device.getApiLevel(), checkApiLevelAgainstNextRelease and getBuildAlias all call
     * `adb getprop` under the hood. We fetch them in one call and call NativeDevice.setProperty.
     * Even if we don't do this, NativeDevice will itself call setProperty and cache the result for
     * future calls. We are just doing it slightly earlier. If the device is in recovery or there
     * are other errors fetching the props, we just ignore them.
     */
    public void batchPrefetchStartupBuildProps() {
        String cmd = "getprop";
        try (CloseableTraceScope ignored = new CloseableTraceScope("batchPrefetchProp")) {
            // Skip refetching if we already have the props by counting the ones in the cache
            // that we need to fetch.
            int propsAlreadyPresent = 0;
            for (String propName : propsToPrefetch) {
                if (mPropertiesCache.getIfPresent(propName) != null) {
                    propsAlreadyPresent++;
                } else {
                    break;
                }
            }
            if (propsAlreadyPresent == propsToPrefetch.size()) {
                return;
            }

            try {
                CommandResult result =
                        executeShellV2Command(cmd, PROPERTY_GET_TIMEOUT, TimeUnit.MILLISECONDS, 0);
                if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                    CLog.w(
                            "Failed to run '%s' returning null. stdout: %s\n"
                                    + "stderr: %s\n"
                                    + "exit code: %s",
                            cmd, result.getStdout(), result.getStderr(), result.getExitCode());
                    if (result.getStdout() == null || result.getStdout().trim().isEmpty()) {
                        return;
                    }
                }
                for (String line : result.getStdout().split("\n")) {
                    String[] parts = line.trim().split("]: \\[");
                    if (parts.length != 2) {
                        continue;
                    }
                    String propName = parts[0].substring(1).trim();
                    String propValue = parts[1].substring(0, parts[1].length() - 1).trim();
                    if (propValue != null) {
                        if (propsToPrefetch.contains(propName)) {
                            mPropertiesCache.put(propName, propValue);
                        }
                    }
                }
            } catch (DeviceNotAvailableException e) {
                // okay to ignore, the real get property will deal with it.
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public long getIntProperty(String name, long defaultValue) throws DeviceNotAvailableException {
        String value = getProperty(name);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static final List<String> TRUE_VALUES = Arrays.asList("1", "y", "yes", "on", "true");
    private static final List<String> FALSE_VALUES = Arrays.asList("0", "n", "no", "off", "false");

    /** {@inheritDoc} */
    @Override
    public boolean getBooleanProperty(String name, boolean defaultValue)
            throws DeviceNotAvailableException {
        String value = getProperty(name);
        if (value == null) {
            return defaultValue;
        }
        if (TRUE_VALUES.contains(value)) {
            return true;
        }
        if (FALSE_VALUES.contains(value)) {
            return false;
        }
        return defaultValue;
    }

    /** {@inheritDoc} */
    @Override
    public boolean setProperty(String propKey, String propValue)
            throws DeviceNotAvailableException {
        if (propKey == null || propValue == null) {
            throw new IllegalArgumentException("set property key or value cannot be null.");
        }
        String setPropCmd = String.format("\"setprop %s '%s'\"", propKey, propValue);
        CommandResult result = executeShellV2Command(setPropCmd);
        if (CommandStatus.SUCCESS.equals(result.getStatus())) {
            mPropertiesCache.invalidate(propKey);
            return true;
        }
        CLog.e(
                "Something went wrong went setting property %s (command: %s): %s",
                propKey, setPropCmd, result.getStderr());
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBootloaderVersion() throws UnsupportedOperationException,
            DeviceNotAvailableException {
        return internalGetProperty("ro.bootloader", "version-bootloader", "Bootloader");
    }

    @Override
    public String getBasebandVersion() throws DeviceNotAvailableException {
        return internalGetProperty("gsm.version.baseband", "version-baseband", "Baseband");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProductType() throws DeviceNotAvailableException {
        return internalGetProductType(MAX_RETRY_ATTEMPTS);
    }

    /**
     * {@link #getProductType()}
     *
     * @param retryAttempts The number of times to try calling {@link #recoverDevice()} if the
     *        device's product type cannot be found.
     */
    private String internalGetProductType(int retryAttempts) throws DeviceNotAvailableException {
        String productType = internalGetProperty(DeviceProperties.BOARD, "product", "Product type");
        // fallback to ro.hardware for legacy devices
        if (Strings.isNullOrEmpty(productType)) {
            productType = internalGetProperty(DeviceProperties.HARDWARE, "product", "Product type");
        }

        // Things will likely break if we don't have a valid product type.  Try recovery (in case
        // the device is only partially booted for some reason), and if that doesn't help, bail.
        if (Strings.isNullOrEmpty(productType)) {
            if (retryAttempts > 0) {
                recoverDevice();
                productType = internalGetProductType(retryAttempts - 1);
            }

            if (Strings.isNullOrEmpty(productType)) {
                throw new DeviceNotAvailableException(
                        String.format(
                                "Could not determine product type for device %s.",
                                getSerialNumber()),
                        getSerialNumber(),
                        DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
            }
        }

        return productType.toLowerCase();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFastbootProductType()
            throws DeviceNotAvailableException, UnsupportedOperationException {
        String prop = getFastbootVariable("product");
        if (prop != null) {
            prop = prop.toLowerCase();
        }
        return prop;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProductVariant() throws DeviceNotAvailableException {
        String prop = internalGetProperty(DeviceProperties.VARIANT, "variant", "Product variant");
        if (prop == null) {
            prop =
                    internalGetProperty(
                            DeviceProperties.VARIANT_LEGACY_O_MR1, "variant", "Product variant");
        }
        if (prop == null) {
            prop =
                    internalGetProperty(
                            DeviceProperties.VARIANT_LEGACY_LESS_EQUAL_O,
                            "variant",
                            "Product variant");
        }
        if (prop != null) {
            prop = prop.toLowerCase();
        }
        return prop;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFastbootProductVariant()
            throws DeviceNotAvailableException, UnsupportedOperationException {
        String prop = getFastbootVariable("variant");
        if (prop != null) {
            prop = prop.toLowerCase();
        }
        return prop;
    }

    /** {@inheritDoc} */
    @Override
    public String getFastbootVariable(String variableName)
            throws DeviceNotAvailableException, UnsupportedOperationException {
        CommandResult result = executeFastbootCommand("getvar", variableName);
        CLog.d(
                "(getvar %s) output:\nstdout%s\nstderr:%s",
                variableName, result.getStdout(), result.getStderr());
        if (result.getStatus() == CommandStatus.SUCCESS) {
            Pattern fastbootProductPattern = Pattern.compile(variableName + ":\\s(.*)\\s");
            // fastboot is weird, and may dump the output on stderr instead of stdout
            String resultText = result.getStdout();
            if (resultText == null || resultText.length() < 1) {
                resultText = result.getStderr();
            }
            Matcher matcher = fastbootProductPattern.matcher(resultText);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildAlias() throws DeviceNotAvailableException {
        String alias = getProperty(DeviceProperties.BUILD_ALIAS);
        if (alias == null || alias.isEmpty()) {
            return getBuildId();
        }
        return alias;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildId() throws DeviceNotAvailableException {
        String bid = getProperty(DeviceProperties.BUILD_ID);
        if (bid == null) {
            CLog.w("Could not get device %s build id.", getSerialNumber());
            return IBuildInfo.UNKNOWN_BUILD_ID;
        }
        return bid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildFlavor() throws DeviceNotAvailableException {
        String buildFlavor = getProperty(DeviceProperties.BUILD_FLAVOR);
        if (buildFlavor != null && !buildFlavor.isEmpty()) {
            return buildFlavor;
        }
        String productName = getProperty(DeviceProperties.PRODUCT);
        String buildType = getProperty(DeviceProperties.BUILD_TYPE);
        if (productName == null || buildType == null) {
            CLog.w("Could not get device %s build flavor.", getSerialNumber());
            return null;
        }
        return String.format("%s-%s", productName, buildType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void executeShellCommand(final String command, final IShellOutputReceiver receiver)
            throws DeviceNotAvailableException {
        DeviceAction action = new DeviceAction() {
            @Override
            public boolean run() throws TimeoutException, IOException,
                    AdbCommandRejectedException, ShellCommandUnresponsiveException {
                getIDevice().executeShellCommand(command, receiver,
                        getCommandTimeout(), TimeUnit.MILLISECONDS);
                return true;
            }
        };
        performDeviceAction(String.format("shell %s", command), action, MAX_RETRY_ATTEMPTS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void executeShellCommand(final String command, final IShellOutputReceiver receiver,
            final long maxTimeToOutputShellResponse, final TimeUnit timeUnit,
            final int retryAttempts) throws DeviceNotAvailableException {
        DeviceAction action = new DeviceAction() {
            @Override
            public boolean run() throws TimeoutException, IOException, AdbCommandRejectedException,
                    ShellCommandUnresponsiveException {
                getIDevice().executeShellCommand(command, receiver,
                        maxTimeToOutputShellResponse, timeUnit);
                return true;
            }
        };
        performDeviceAction(String.format("shell %s", command), action, retryAttempts);
    }

    /** {@inheritDoc} */
    @Override
    public void executeShellCommand(
            final String command,
            final IShellOutputReceiver receiver,
            final long maxTimeoutForCommand,
            final long maxTimeToOutputShellResponse,
            final TimeUnit timeUnit,
            final int retryAttempts)
            throws DeviceNotAvailableException {
        DeviceAction action =
                new DeviceAction() {
                    @Override
                    public boolean run()
                            throws TimeoutException, IOException, AdbCommandRejectedException,
                                    ShellCommandUnresponsiveException {
                        getIDevice()
                                .executeShellCommand(
                                        command,
                                        receiver,
                                        maxTimeoutForCommand,
                                        maxTimeToOutputShellResponse,
                                        timeUnit);
                        return true;
                    }
                };
        performDeviceAction(String.format("shell %s", command), action, retryAttempts);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String executeShellCommand(String command) throws DeviceNotAvailableException {
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        executeShellCommand(command, receiver);
        String output = receiver.getOutput();
        if (mExecuteShellCommandLogs != null) {
            // Log all output to a dedicated file as it can be very verbose.
            String formatted =
                    LogUtil.getLogFormatString(
                            LogLevel.VERBOSE,
                            "NativeDevice",
                            String.format(
                                    "%s on %s returned %s\n==== END OF OUTPUT ====\n",
                                    command, getSerialNumber(), output));
            try {
                FileUtil.writeToFile(formatted, mExecuteShellCommandLogs, true);
            } catch (IOException e) {
                // Ignore the full error
                CLog.e("Failed to log to executeShellCommand log: %s", e.getMessage());
            }
        }
        if (output.length() > 80) {
            CLog.v(
                    "%s on %s returned %s <truncated - See executeShellCommand log for full trace>",
                    command, getSerialNumber(), output.substring(0, 80));
        } else {
            CLog.v("%s on %s returned %s", command, getSerialNumber(), output);
        }
        return output;
    }

    /** {@inheritDoc} */
    @Override
    public CommandResult executeShellV2Command(String cmd) throws DeviceNotAvailableException {
        return executeShellV2Command(cmd, getCommandTimeout(), TimeUnit.MILLISECONDS);
    }

    /** {@inheritDoc} */
    @Override
    public CommandResult executeShellV2Command(String cmd, File pipeAsInput)
            throws DeviceNotAvailableException {
        return executeShellV2Command(
                cmd,
                pipeAsInput,
                null,
                getCommandTimeout(),
                TimeUnit.MILLISECONDS,
                MAX_RETRY_ATTEMPTS);
    }

    /** {@inheritDoc} */
    @Override
    public CommandResult executeShellV2Command(String cmd, OutputStream pipeToOutput)
            throws DeviceNotAvailableException {
        return executeShellV2Command(
                cmd,
                null,
                pipeToOutput,
                getCommandTimeout(),
                TimeUnit.MILLISECONDS,
                MAX_RETRY_ATTEMPTS);
    }

    /** {@inheritDoc} */
    @Override
    public CommandResult executeShellV2Command(
            String cmd, final long maxTimeoutForCommand, final TimeUnit timeUnit)
            throws DeviceNotAvailableException {
        return executeShellV2Command(
                cmd, null, null, maxTimeoutForCommand, timeUnit, MAX_RETRY_ATTEMPTS);
    }

    /** {@inheritDoc} */
    @Override
    public CommandResult executeShellV2Command(
            String cmd, final long maxTimeoutForCommand, final TimeUnit timeUnit, int retryAttempts)
            throws DeviceNotAvailableException {
        return executeShellV2Command(
                cmd, null, null, maxTimeoutForCommand, timeUnit, retryAttempts);
    }

    /** {@inheritDoc} */
    @Override
    public CommandResult executeShellV2Command(
            String cmd,
            File pipeAsInput,
            OutputStream pipeToOutput,
            final long maxTimeoutForCommand,
            final TimeUnit timeUnit,
            int retryAttempts)
            throws DeviceNotAvailableException {
        return executeShellV2Command(
                cmd,
                pipeAsInput,
                pipeToOutput,
                /*pipeToError=*/ null,
                maxTimeoutForCommand,
                timeUnit,
                retryAttempts);
    }

    /** {@inheritDoc} */
    @Override
    public CommandResult executeShellV2Command(
            String cmd,
            File pipeAsInput,
            OutputStream pipeToOutput,
            OutputStream pipeToError,
            final long maxTimeoutForCommand,
            final TimeUnit timeUnit,
            int retryAttempts)
            throws DeviceNotAvailableException {
        // If the device does not support the v2 shell features. Exit status will not be propagated
        // and stdout/stderr will be merged in stdout.
        //
        // There's nothing we can do to separate the two streams, but we can alter the command to
        // retrieve the exit status.
        //
        // Note that this does *not* call `adb features` on each invocation. ddmlib caches all the
        // adb features on the first query.
        boolean parseExitStatus =
                !getIDevice().supportsFeature(IDevice.Feature.SHELL_V2)
                        && getOptions().useExitStatusWorkaround();

        final String[] fullCmd = buildAdbShellCommand(cmd, parseExitStatus);
        AdbShellAction adbActionV2 =
                new AdbShellAction(
                        fullCmd,
                        pipeAsInput,
                        pipeToOutput,
                        pipeToError,
                        timeUnit.toMillis(maxTimeoutForCommand));
        performDeviceAction(String.format("adb %s", fullCmd[4]), adbActionV2, retryAttempts);
        if (parseExitStatus) {
            postProcessExitStatus(adbActionV2.mResult);
        }
        return adbActionV2.mResult;
    }

    private void postProcessExitStatus(@Nonnull CommandResult result) {
        String stdout = result.getStdout();
        int delimiterIndex = stdout.lastIndexOf(EXIT_STATUS_DELIMITER);
        if (delimiterIndex < 0) {
            result.setStatus(CommandStatus.FAILED);
            return;
        }
        String actualStdout = stdout.substring(0, delimiterIndex);
        String exitStatusText = stdout.substring(delimiterIndex + 1);
        result.setExitCode(Integer.parseUnsignedInt(exitStatusText.trim()));
        result.setStdout(actualStdout);
        if (result.getStatus() == CommandStatus.SUCCESS && result.getExitCode() != 0) {
            result.setStatus(CommandStatus.FAILED);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean runInstrumentationTests(
            final IRemoteAndroidTestRunner runner,
            final Collection<ITestLifeCycleReceiver> listeners)
            throws DeviceNotAvailableException {
        RunFailureListener failureListener = new RunFailureListener();
        List<ITestRunListener> runListeners = new ArrayList<>();
        runListeners.add(failureListener);
        runListeners.add(new TestRunToTestInvocationForwarder(listeners));

        if ((mConfiguration != null)
                && mConfiguration.getCoverageOptions().isCoverageEnabled()
                && mConfiguration
                        .getCoverageOptions()
                        .getCoverageToolchains()
                        .contains(Toolchain.JACOCO)) {
            runner.setCoverage(true);
        }

        DeviceAction runTestsAction =
                new DeviceAction() {
                    @Override
                    public boolean run()
                            throws IOException, TimeoutException, AdbCommandRejectedException,
                                    ShellCommandUnresponsiveException, InstallException,
                                    SyncException {
                        runner.run(runListeners);
                        return true;
                    }
                };
        boolean result = performDeviceAction(String.format("run %s instrumentation tests",
                runner.getPackageName()), runTestsAction, 0);
        if (failureListener.isRunFailure()) {
            waitForDeviceAvailable();
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean runInstrumentationTests(
            IRemoteAndroidTestRunner runner, ITestLifeCycleReceiver... listeners)
            throws DeviceNotAvailableException {
        List<ITestLifeCycleReceiver> listenerList = new ArrayList<>();
        listenerList.addAll(Arrays.asList(listeners));
        return runInstrumentationTests(runner, listenerList);
    }

    /** {@inheritDoc} */
    @Override
    public boolean runInstrumentationTestsAsUser(
            final IRemoteAndroidTestRunner runner,
            int userId,
            final Collection<ITestLifeCycleReceiver> listeners)
            throws DeviceNotAvailableException {
        String oldRunTimeOptions = appendUserRunTimeOptionToRunner(runner, userId);
        boolean result = runInstrumentationTests(runner, listeners);
        resetUserRunTimeOptionToRunner(runner, oldRunTimeOptions);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean runInstrumentationTestsAsUser(
            IRemoteAndroidTestRunner runner, int userId, ITestLifeCycleReceiver... listeners)
            throws DeviceNotAvailableException {
        String oldRunTimeOptions = appendUserRunTimeOptionToRunner(runner, userId);
        boolean result = runInstrumentationTests(runner, listeners);
        resetUserRunTimeOptionToRunner(runner, oldRunTimeOptions);
        return result;
    }

    /**
     * Helper method to add user run time option to {@link RemoteAndroidTestRunner}
     *
     * @param runner {@link IRemoteAndroidTestRunner}
     * @param userId the integer of the user id to run as.
     * @return original run time options.
     */
    private String appendUserRunTimeOptionToRunner(final IRemoteAndroidTestRunner runner, int userId) {
        if (runner instanceof RemoteAndroidTestRunner) {
            String original = ((RemoteAndroidTestRunner) runner).getRunOptions();
            String userRunTimeOption = String.format("--user %s", Integer.toString(userId));
            String updated = (original != null) ? (original + " " + userRunTimeOption)
                    : userRunTimeOption;
            ((RemoteAndroidTestRunner) runner).setRunOptions(updated);
            return original;
        } else {
            throw new IllegalStateException(String.format("%s runner does not support multi-user",
                    runner.getClass().getName()));
        }
    }

    /**
     * Helper method to reset the run time options to {@link RemoteAndroidTestRunner}
     *
     * @param runner {@link IRemoteAndroidTestRunner}
     * @param oldRunTimeOptions
     */
    private void resetUserRunTimeOptionToRunner(final IRemoteAndroidTestRunner runner,
            String oldRunTimeOptions) {
        if (runner instanceof RemoteAndroidTestRunner) {
            if (oldRunTimeOptions != null) {
                ((RemoteAndroidTestRunner) runner).setRunOptions(oldRunTimeOptions);
            }
        } else {
            throw new IllegalStateException(String.format("%s runner does not support multi-user",
                    runner.getClass().getName()));
        }
    }

    private static class RunFailureListener extends StubTestRunListener {
        private boolean mIsRunFailure = false;

        @Override
        public void testRunFailed(String message) {
            mIsRunFailure = true;
        }

        public boolean isRunFailure() {
            return mIsRunFailure;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRuntimePermissionSupported() throws DeviceNotAvailableException {
        int apiLevel = getApiLevel();
        boolean condition = apiLevel > 22;
        if (!condition) {
            CLog.w(
                    "isRuntimePermissionSupported requires api level above 22, device reported "
                            + "'%s'",
                    apiLevel);
        }
        return condition;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAppEnumerationSupported() throws DeviceNotAvailableException {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isBypassLowTargetSdkBlockSupported() throws DeviceNotAvailableException {
        return checkApiLevelAgainstNextRelease(34);
    }

    /**
     * helper method to throw exception if runtime permission isn't supported
     * @throws DeviceNotAvailableException
     */
    protected void ensureRuntimePermissionSupported() throws DeviceNotAvailableException {
        boolean runtimePermissionSupported = isRuntimePermissionSupported();
        if (!runtimePermissionSupported) {
            throw new UnsupportedOperationException(
                    "platform on device does not support runtime permission granting!");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String installPackage(final File packageFile, final boolean reinstall,
            final String... extraArgs) throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for Package Manager's features");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String installPackage(File packageFile, boolean reinstall, boolean grantPermissions,
            String... extraArgs) throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for Package Manager's features");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String installPackageForUser(File packageFile, boolean reinstall, int userId,
            String... extraArgs) throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for Package Manager's features");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String installPackageForUser(File packageFile, boolean reinstall,
            boolean grantPermissions, int userId, String... extraArgs)
                    throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for Package Manager's features");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String uninstallPackage(final String packageName) throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for Package Manager's features");
    }

    /** {@inheritDoc} */
    @Override
    public String uninstallPackageForUser(final String packageName, int userId)
            throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for Package Manager's features");
    }

    /** {@inheritDoc} */
    @Override
    public boolean pullFile(final String remoteFilePath, final File localFile, int userId)
            throws DeviceNotAvailableException {
        long startTime = System.currentTimeMillis();
        InvocationMetricLogger.addInvocationMetrics(InvocationMetricKey.PULL_FILE_COUNT, 1);

        try {
            if (isSdcardOrEmulated(remoteFilePath) && userId != 0) {
                ContentProviderHandler handler = getContentProvider();
                if (handler != null) {
                    return handler.pullFile(remoteFilePath, localFile);
                }
            }
            return pullFileInternal(remoteFilePath, localFile);
        } finally {
            long totalTime = System.currentTimeMillis() - startTime;
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.PULL_FILE_TIME, totalTime);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean pullFile(final String remoteFilePath, final File localFile)
            throws DeviceNotAvailableException {
        return pullFile(remoteFilePath, localFile, getCurrentUserCompatible());
    }

    /** {@inheritDoc} */
    @Override
    public File pullFile(String remoteFilePath, int userId) throws DeviceNotAvailableException {
        File localFile = null;
        boolean success = false;
        try {
            localFile = FileUtil.createTempFileForRemote(remoteFilePath, null);
            if (pullFile(remoteFilePath, localFile, userId)) {
                success = true;
                return localFile;
            }
        } catch (IOException e) {
            CLog.w("Encountered IOException while trying to pull '%s':", remoteFilePath);
            CLog.e(e);
        } finally {
            if (!success) {
                FileUtil.deleteFile(localFile);
            }
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public File pullFile(String remoteFilePath) throws DeviceNotAvailableException {
        return pullFile(remoteFilePath, getCurrentUserCompatible());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String pullFileContents(String remoteFilePath) throws DeviceNotAvailableException {
        File temp = pullFile(remoteFilePath);

        if (temp != null) {
            try {
                return FileUtil.readStringFromFile(temp);
            } catch (IOException e) {
                CLog.e(String.format("Could not pull file: %s", remoteFilePath));
            } finally {
                FileUtil.deleteFile(temp);
            }
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File pullFileFromExternal(String remoteFilePath) throws DeviceNotAvailableException {
        String externalPath = getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        String fullPath = new File(externalPath, remoteFilePath).getPath();
        return pullFile(fullPath);
    }

    protected boolean pullFileInternal(String remoteFilePath, File localFile)
            throws DeviceNotAvailableException {
        DeviceAction pullAction =
                new DeviceAction() {
                    @Override
                    public boolean run()
                            throws TimeoutException, IOException, AdbCommandRejectedException,
                                    SyncException {
                        SyncService syncService = null;
                        boolean status = false;
                        try {
                            syncService = getIDevice().getSyncService();
                            syncService.pullFile(
                                    interpolatePathVariables(remoteFilePath),
                                    localFile.getAbsolutePath(),
                                    SyncService.getNullProgressMonitor());
                            status = true;
                        } catch (SyncException e) {
                            CLog.w(
                                    "Failed to pull %s from %s to %s. Message %s",
                                    remoteFilePath,
                                    getSerialNumber(),
                                    localFile.getAbsolutePath(),
                                    e.getMessage());
                            throw e;
                        } finally {
                            if (syncService != null) {
                                syncService.close();
                            }
                        }
                        return status;
                    }
                };
        return performDeviceAction(
                String.format("pull %s to %s", remoteFilePath, localFile.getAbsolutePath()),
                pullAction,
                MAX_RETRY_ATTEMPTS);
    }

    /**
     * Helper function that watches for the string "${EXTERNAL_STORAGE}" and replaces it with the
     * pathname of the EXTERNAL_STORAGE mountpoint.  Specifically intended to be used for pathnames
     * that are being passed to SyncService, which does not support variables inside of filenames.
     */
    String interpolatePathVariables(String path) {
        final String esString = "${EXTERNAL_STORAGE}";
        if (path.contains(esString)) {
            final String esPath = getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
            path = path.replace(esString, esPath);
        }
        return path;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean pushFile(final File localFile, final String remoteFilePath)
            throws DeviceNotAvailableException {
        return pushFileInternal(localFile, remoteFilePath, false);
    }

    @Override
    public boolean pushFile(
            final File localFile,
            final String remoteFilePath,
            boolean evaluateContentProviderNeeded)
            throws DeviceNotAvailableException {
        boolean skipContentProvider = false;
        if (evaluateContentProviderNeeded) {
            skipContentProvider = getCurrentUserCompatible() == 0;
        }
        return pushFileInternal(localFile, remoteFilePath, skipContentProvider);
    }

    @VisibleForTesting
    boolean pushFileInternal(final File localFile, final String remoteFilePath,
            boolean skipContentProvider) throws DeviceNotAvailableException {
        long startTime = System.currentTimeMillis();
        InvocationMetricLogger.addInvocationMetrics(InvocationMetricKey.PUSH_FILE_COUNT, 1);
        try {
            if (!skipContentProvider) {
                if (isSdcardOrEmulated(remoteFilePath)) {
                    ContentProviderHandler handler = getContentProvider();
                    if (handler != null) {
                        return handler.pushFile(localFile, remoteFilePath);
                    }
                }
            }

            DeviceAction pushAction =
                    new DeviceAction() {
                        @Override
                        public boolean run()
                                throws TimeoutException, IOException, AdbCommandRejectedException,
                                        SyncException {
                            SyncService syncService = null;
                            boolean status = false;
                            try {
                                syncService = getIDevice().getSyncService();
                                if (syncService == null) {
                                    throw new IOException("SyncService returned null.");
                                }
                                syncService.pushFile(
                                        localFile.getAbsolutePath(),
                                        interpolatePathVariables(remoteFilePath),
                                        SyncService.getNullProgressMonitor());
                                status = true;
                            } catch (SyncException e) {
                                CLog.w(
                                        "Failed to push %s to %s on device %s. Message: '%s'. "
                                                + "Error code: %s",
                                        localFile.getAbsolutePath(),
                                        remoteFilePath,
                                        getSerialNumber(),
                                        e.getMessage(),
                                        e.getErrorCode());
                                // TODO: check if ddmlib can report a better error
                                if (SyncError.TRANSFER_PROTOCOL_ERROR.equals(e.getErrorCode())) {
                                    if (e.getMessage().contains("Permission denied")) {
                                        return false;
                                    }
                                }
                                throw e;
                            } finally {
                                if (syncService != null) {
                                    syncService.close();
                                }
                            }
                            return status;
                        }
                    };
            return performDeviceAction(
                    String.format("push %s to %s", localFile.getAbsolutePath(), remoteFilePath),
                    pushAction,
                    MAX_RETRY_ATTEMPTS);
        } finally {
            long totalTime = System.currentTimeMillis() - startTime;
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.PUSH_FILE_TIME, totalTime);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean pushString(final String contents, final String remoteFilePath)
            throws DeviceNotAvailableException {
        File tmpFile = null;
        try {
            tmpFile = FileUtil.createTempFile("temp", ".txt");
            FileUtil.writeToFile(contents, tmpFile);
            return pushFile(tmpFile, remoteFilePath);
        } catch (IOException e) {
            CLog.e(e);
            return false;
        } finally {
            FileUtil.deleteFile(tmpFile);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean doesFileExist(String deviceFilePath) throws DeviceNotAvailableException {
        return doesFileExist(deviceFilePath, getCurrentUserCompatible());
    }

    @Override
    public boolean doesFileExist(String deviceFilePath, int userId)
            throws DeviceNotAvailableException {
        long startTime = System.currentTimeMillis();
        try {
            // Skip ContentProvider for user 0
            if (isSdcardOrEmulated(deviceFilePath) && userId != 0) {
                ContentProviderHandler handler = getContentProvider();
                if (handler != null) {
                    CLog.d("Delegating check to ContentProvider doesFileExist(%s)", deviceFilePath);
                    return handler.doesFileExist(deviceFilePath);
                }
            }
            CLog.d("Using 'ls' to check doesFileExist(%s)", deviceFilePath);
            String lsGrep = executeShellCommand(String.format("ls \"%s\"", deviceFilePath));
            return !lsGrep.contains("No such file or directory");
        } finally {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DOES_FILE_EXISTS_TIME,
                    System.currentTimeMillis() - startTime);
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DOES_FILE_EXISTS_COUNT, 1);
        }
    }

    @Override
    public void registerDeviceActionReceiver(IDeviceActionReceiver deviceActionReceiver) {
        mDeviceActionReceivers.add(deviceActionReceiver);
    }

    @Override
    public void deregisterDeviceActionReceiver(IDeviceActionReceiver deviceActionReceiver) {
        mDeviceActionReceivers.remove(deviceActionReceiver);
    }

    /** {@inheritDoc} */
    @Override
    public void deleteFile(String deviceFilePath) throws DeviceNotAvailableException {
        long startTime = System.currentTimeMillis();
        try {
            if (isSdcardOrEmulated(deviceFilePath)) {
                int currentUser = getCurrentUserCompatible();
                if (currentUser != 0) {
                    ContentProviderHandler handler = getContentProvider();
                    if (handler != null) {
                        if (handler.deleteFile(deviceFilePath)) {
                            return;
                        }
                    }
                }
            }
            // Fallback to the direct command if content provider is unsuccessful
            String path = StringEscapeUtils.escapeShell(deviceFilePath);
            // Escape spaces to handle filename with spaces
            path = path.replaceAll(" ", "\\ ");
            executeShellCommand(String.format("rm -rf %s", StringEscapeUtils.escapeShell(path)));
        } finally {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DELETE_DEVICE_FILE_TIME,
                    System.currentTimeMillis() - startTime);
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DELETE_DEVICE_FILE_COUNT, 1);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getExternalStoreFreeSpace() throws DeviceNotAvailableException {
        String externalStorePath = getMountPoint(IDevice.MNT_EXTERNAL_STORAGE);
        return getPartitionFreeSpace(externalStorePath);
    }

    /** {@inheritDoc} */
    @Override
    public long getPartitionFreeSpace(String partition) throws DeviceNotAvailableException {
        CLog.i("Checking free space for %s on partition %s", getSerialNumber(), partition);
        String output = getDfOutput(partition);
        // Try coreutils/toybox style output first.
        Long available = parseFreeSpaceFromModernOutput(output);
        if (available != null) {
            return available;
        }
        // Then the two legacy toolbox formats.
        available = parseFreeSpaceFromAvailable(output);
        if (available != null) {
            return available;
        }
        available = parseFreeSpaceFromFree(partition, output);
        if (available != null) {
            return available;
        }

        CLog.e("free space command output \"%s\" did not match expected patterns", output);
        return 0;
    }

    /**
     * Run the 'df' shell command and return output, making multiple attempts if necessary.
     *
     * @param externalStorePath the path to check
     * @return the output from 'shell df path'
     * @throws DeviceNotAvailableException
     */
    private String getDfOutput(String externalStorePath) throws DeviceNotAvailableException {
        for (int i=0; i < MAX_RETRY_ATTEMPTS; i++) {
            String output = executeShellCommand(String.format("df %s", externalStorePath));
            if (output.trim().length() > 0) {
                return output;
            }
        }
        throw new DeviceUnresponsiveException(String.format(
                "Device %s not returning output from df command after %d attempts",
                getSerialNumber(), MAX_RETRY_ATTEMPTS), getSerialNumber());
    }

    /**
     * Parses a partition's available space from the legacy output of a 'df' command, used
     * pre-gingerbread.
     * <p/>
     * Assumes output format of:
     * <br>/
     * <code>
     * [partition]: 15659168K total, 51584K used, 15607584K available (block size 32768)
     * </code>
     * @param dfOutput the output of df command to parse
     * @return the available space in kilobytes or <code>null</code> if output could not be parsed
     */
    private Long parseFreeSpaceFromAvailable(String dfOutput) {
        final Pattern freeSpacePattern = Pattern.compile("(\\d+)K available");
        Matcher patternMatcher = freeSpacePattern.matcher(dfOutput);
        if (patternMatcher.find()) {
            String freeSpaceString = patternMatcher.group(1);
            try {
                return Long.parseLong(freeSpaceString);
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        return null;
    }

    /**
     * Parses a partition's available space from the 'table-formatted' output of a toolbox 'df'
     * command, used from gingerbread to lollipop.
     * <p/>
     * Assumes output format of:
     * <br/>
     * <code>
     * Filesystem             Size   Used   Free   Blksize
     * <br/>
     * [partition]:              3G   790M  2G     4096
     * </code>
     * @param dfOutput the output of df command to parse
     * @return the available space in kilobytes or <code>null</code> if output could not be parsed
     */
    Long parseFreeSpaceFromFree(String externalStorePath, String dfOutput) {
        Long freeSpace = null;
        final Pattern freeSpaceTablePattern = Pattern.compile(String.format(
                //fs   Size         Used         Free
                "%s\\s+[\\w\\d\\.]+\\s+[\\w\\d\\.]+\\s+([\\d\\.]+)(\\w)", externalStorePath));
        Matcher tablePatternMatcher = freeSpaceTablePattern.matcher(dfOutput);
        if (tablePatternMatcher.find()) {
            String numericValueString = tablePatternMatcher.group(1);
            String unitType = tablePatternMatcher.group(2);
            try {
                float freeSpaceFloat = Float.parseFloat(numericValueString);
                if (unitType.equals("M")) {
                    freeSpaceFloat = freeSpaceFloat * 1024;
                } else if (unitType.equals("G")) {
                    freeSpaceFloat = freeSpaceFloat * 1024 * 1024;
                }
                freeSpace = (long) freeSpaceFloat;
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        return freeSpace;
    }

    /**
     * Parses a partition's available space from the modern coreutils/toybox 'df' output, used
     * after lollipop.
     * <p/>
     * Assumes output format of:
     * <br/>
     * <code>
     * Filesystem      1K-blocks	Used  Available Use% Mounted on
     * <br/>
     * /dev/fuse        11585536    1316348   10269188  12% /mnt/shell/emulated
     * </code>
     * @param dfOutput the output of df command to parse
     * @return the available space in kilobytes or <code>null</code> if output could not be parsed
     */
    Long parseFreeSpaceFromModernOutput(String dfOutput) {
        Matcher matcher = DF_PATTERN.matcher(dfOutput);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(2));
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMountPoint(String mountName) {
        try {
            return mStateMonitor.getMountPoint(mountName);
        } catch (DeviceNotAvailableException e) {
            CLog.e(e);
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MountPointInfo> getMountPointInfo() throws DeviceNotAvailableException {
        final String mountInfo = executeShellCommand("cat /proc/mounts");
        final String[] mountInfoLines = mountInfo.split("\r?\n");
        List<MountPointInfo> list = new ArrayList<>(mountInfoLines.length);

        for (String line : mountInfoLines) {
            // We ignore the last two fields
            // /dev/block/mtdblock4 /cache yaffs2 rw,nosuid,nodev,relatime 0 0
            final String[] parts = line.split("\\s+", 5);
            list.add(new MountPointInfo(parts[0], parts[1], parts[2], parts[3]));
        }

        return list;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MountPointInfo getMountPointInfo(String mountpoint) throws DeviceNotAvailableException {
        // The overhead of parsing all of the lines should be minimal
        List<MountPointInfo> mountpoints = getMountPointInfo();
        for (MountPointInfo info : mountpoints) {
            if (mountpoint.equals(info.mountpoint)) {
                return info;
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IFileEntry getFileEntry(String path) throws DeviceNotAvailableException {
        path = interpolatePathVariables(path);
        String[] pathComponents = path.split(FileListingService.FILE_SEPARATOR);
        FileListingService service = getFileListingService();
        IFileEntry rootFile = new FileEntryWrapper(this, service.getRoot());
        return FileEntryWrapper.getDescendant(rootFile, Arrays.asList(pathComponents));
    }

    /**
     * Unofficial helper to get a {@link FileEntry} from a non-root path. FIXME: Refactor the
     * FileEntry system to have it available from any path. (even non root).
     *
     * @param entry a {@link FileEntry} not necessarily root as Ddmlib requires.
     * @return a {@link FileEntryWrapper} representing the FileEntry.
     * @throws DeviceNotAvailableException
     */
    public IFileEntry getFileEntry(FileEntry entry) throws DeviceNotAvailableException {
        // FileEntryWrapper is going to construct the list of child file internally.
        return new FileEntryWrapper(this, entry);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isExecutable(String fullPath) throws DeviceNotAvailableException {
        String fileMode = executeShellCommand(String.format("ls -l %s", fullPath));
        if (fileMode != null) {
            return EXE_FILE.matcher(fileMode).find();
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDirectory(String path) throws DeviceNotAvailableException {
        return executeShellCommand(String.format("ls -ld %s", path)).charAt(0) == 'd';
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String[] getChildren(String path) throws DeviceNotAvailableException {
        String lsOutput = executeShellCommand(String.format("ls -A1 %s", path));
        if (lsOutput.trim().isEmpty()) {
            return new String[0];
        }
        return lsOutput.split("\r?\n");
    }

    /**
     * Retrieve the {@link FileListingService} for the {@link IDevice}, making multiple attempts
     * and recovery operations if necessary.
     * <p/>
     * This is necessary because {@link IDevice#getFileListingService()} can return
     * <code>null</code> if device is in fastboot.  The symptom of this condition is that the
     * current {@link #getIDevice()} is a {@link StubDevice}.
     *
     * @return the {@link FileListingService}
     * @throws DeviceNotAvailableException if device communication is lost.
     */
    private FileListingService getFileListingService() throws DeviceNotAvailableException  {
        final FileListingService[] service = new FileListingService[1];
        DeviceAction serviceAction = new DeviceAction() {
            @Override
            public boolean run() throws IOException, TimeoutException, AdbCommandRejectedException,
                    ShellCommandUnresponsiveException, InstallException, SyncException {
                service[0] = getIDevice().getFileListingService();
                if (service[0] == null) {
                    // could not get file listing service - must be a stub device - enter recovery
                    throw new IOException("Could not get file listing service");
                }
                return true;
            }
        };
        performDeviceAction("getFileListingService", serviceAction, MAX_RETRY_ATTEMPTS);
        return service[0];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean pushDir(File localFileDir, String deviceFilePath)
            throws DeviceNotAvailableException {
        return pushDir(localFileDir, deviceFilePath, new HashSet<>());
    }

    /** {@inheritDoc} */
    @Override
    public boolean pushDir(
            File localFileDir, String deviceFilePath, Set<String> excludedDirectories)
            throws DeviceNotAvailableException {
        long startTime = System.currentTimeMillis();
        try {
            if (isSdcardOrEmulated(deviceFilePath)) {
                Integer currentUser = getCurrentUserCompatible();
                if (currentUser != 0) {
                    ContentProviderHandler handler = getContentProvider();
                    if (handler != null) {
                        return handler.pushDir(localFileDir, deviceFilePath, excludedDirectories);
                    }
                } else {
                    // Remove the special handling when content provider performance is better
                    CLog.d("Push without content provider for user '%s'", currentUser);
                }
            }
            return pushDirInternal(localFileDir, deviceFilePath, excludedDirectories);
        } finally {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.PUSH_DIR_TIME, System.currentTimeMillis() - startTime);
            InvocationMetricLogger.addInvocationMetrics(InvocationMetricKey.PUSH_DIR_COUNT, 1);
        }
    }

    private boolean pushDirInternal(
            File localFileDir, String deviceFilePath, Set<String> excludedDirectories)
            throws DeviceNotAvailableException {
        if (!localFileDir.isDirectory()) {
            CLog.e("file %s is not a directory", localFileDir.getAbsolutePath());
            return false;
        }
        File[] childFiles = localFileDir.listFiles();
        if (childFiles == null) {
            CLog.e("Could not read files in %s", localFileDir.getAbsolutePath());
            return false;
        }
        for (File childFile : childFiles) {
            String remotePath = String.format("%s/%s", deviceFilePath, childFile.getName());
            if (childFile.isDirectory()) {
                // If we encounter a filtered directory do not push it.
                if (excludedDirectories.contains(childFile.getName())) {
                    CLog.d(
                            "%s directory was not pushed because it was filtered.",
                            childFile.getAbsolutePath());
                    continue;
                }
                executeShellCommand(String.format("mkdir -p \"%s\"", remotePath));
                if (!pushDirInternal(childFile, remotePath, excludedDirectories)) {
                    return false;
                }
            } else if (childFile.isFile()) {
                if (!pushFileInternal(childFile, remotePath, true)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean pullDir(String deviceFilePath, File localDir)
            throws DeviceNotAvailableException {
        long startTime = System.currentTimeMillis();
        try {
            int currentUser = getCurrentUserCompatible();
            if (isSdcardOrEmulated(deviceFilePath)) {
                if (currentUser != 0) {
                    ContentProviderHandler handler = getContentProvider();
                    if (handler != null) {
                        return handler.pullDir(deviceFilePath, localDir);
                    }
                }
            }

            return pullDirInternal(deviceFilePath, localDir, currentUser);
        } finally {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.PULL_DIR_TIME, System.currentTimeMillis() - startTime);
            InvocationMetricLogger.addInvocationMetrics(InvocationMetricKey.PULL_DIR_COUNT, 1);
        }
    }

    private boolean pullDirInternal(String deviceFilePath, File localDir, int userId)
            throws DeviceNotAvailableException {
        if (!localDir.isDirectory()) {
            CLog.e("Local path %s is not a directory", localDir.getAbsolutePath());
            return false;
        }
        if (!doesFileExist(deviceFilePath, userId)) {
            CLog.e("Device path %s does not exist to be pulled.", deviceFilePath);
            return false;
        }
        if (!isDirectory(deviceFilePath)) {
            CLog.e("Device path %s is not a directory", deviceFilePath);
            return false;
        }
        FileEntry entryRoot =
                new FileEntry(null, deviceFilePath, FileListingService.TYPE_DIRECTORY, false);
        IFileEntry entry = getFileEntry(entryRoot);
        Collection<IFileEntry> children = entry.getChildren(false);
        if (children.isEmpty()) {
            CLog.i("Device path is empty, nothing to do.");
            return true;
        }
        for (IFileEntry item : children) {
            if (item.isDirectory()) {
                // handle sub dir
                File subDir = new File(localDir, item.getName());
                if (!subDir.mkdir()) {
                    CLog.w(
                            "Failed to create sub directory %s, aborting.",
                            subDir.getAbsolutePath());
                    return false;
                }
                String deviceSubDir = item.getFullPath();
                if (!pullDirInternal(deviceSubDir, subDir, userId)) {
                    CLog.w("Failed to pull sub directory %s from device, aborting", deviceSubDir);
                    return false;
                }
            } else {
                // handle regular file
                File localFile = new File(localDir, item.getName());
                String fullPath = item.getFullPath();
                if (!pullFileInternal(fullPath, localFile)) {
                    CLog.w("Failed to pull file %s from device, aborting", fullPath);
                    return false;
                }
            }
        }
        return true;
    }

    /** Checks whether path is external storage path. */
    private boolean isSdcardOrEmulated(String path) {
        return path.startsWith(SD_CARD) || path.startsWith(STORAGE_EMULATED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean syncFiles(File localFileDir, String deviceFilePath)
            throws DeviceNotAvailableException {
        if (localFileDir == null || deviceFilePath == null) {
            throw new IllegalArgumentException("syncFiles does not take null arguments");
        }
        CLog.i("Syncing %s to %s on device %s",
                localFileDir.getAbsolutePath(), deviceFilePath, getSerialNumber());
        if (!localFileDir.isDirectory()) {
            CLog.e("file %s is not a directory", localFileDir.getAbsolutePath());
            return false;
        }
        // get the real destination path. This is done because underlying syncService.push
        // implementation will add localFileDir.getName() to destination path
        deviceFilePath = String.format("%s/%s", interpolatePathVariables(deviceFilePath),
                localFileDir.getName());
        if (!doesFileExist(deviceFilePath)) {
            executeShellCommand(String.format("mkdir -p \"%s\"", deviceFilePath));
        }
        IFileEntry remoteFileEntry = getFileEntry(deviceFilePath);
        if (remoteFileEntry == null) {
            CLog.e("Could not find remote file entry %s ", deviceFilePath);
            return false;
        }

        return syncFiles(localFileDir, remoteFileEntry);
    }

    /**
     * Recursively sync newer files.
     *
     * @param localFileDir the local {@link File} directory to sync
     * @param remoteFileEntry the remote destination {@link IFileEntry}
     * @return <code>true</code> if files were synced successfully
     * @throws DeviceNotAvailableException
     */
    private boolean syncFiles(File localFileDir, final IFileEntry remoteFileEntry)
            throws DeviceNotAvailableException {
        CLog.d("Syncing %s to %s on %s", localFileDir.getAbsolutePath(),
                remoteFileEntry.getFullPath(), getSerialNumber());
        // find newer files to sync
        File[] localFiles = localFileDir.listFiles(new NoHiddenFilesFilter());
        ArrayList<String> filePathsToSync = new ArrayList<>();
        for (File localFile : localFiles) {
            IFileEntry entry = remoteFileEntry.findChild(localFile.getName());
            if (entry == null) {
                CLog.d("Detected missing file path %s", localFile.getAbsolutePath());
                filePathsToSync.add(localFile.getAbsolutePath());
            } else if (localFile.isDirectory()) {
                // This directory exists remotely. recursively sync it to sync only its newer files
                // contents
                if (!syncFiles(localFile, entry)) {
                    return false;
                }
            } else if (isNewer(localFile, entry)) {
                CLog.d("Detected newer file %s", localFile.getAbsolutePath());
                filePathsToSync.add(localFile.getAbsolutePath());
            }
        }

        if (filePathsToSync.size() == 0) {
            CLog.d("No files to sync");
            return true;
        }
        final String files[] = filePathsToSync.toArray(new String[filePathsToSync.size()]);
        DeviceAction syncAction = new DeviceAction() {
            @Override
            public boolean run() throws TimeoutException, IOException, AdbCommandRejectedException,
                    SyncException {
                SyncService syncService = null;
                boolean status = false;
                try {
                    syncService = getIDevice().getSyncService();
                    syncService.push(files, remoteFileEntry.getFileEntry(),
                            SyncService.getNullProgressMonitor());
                    status = true;
                } catch (SyncException e) {
                    CLog.w("Failed to sync files to %s on device %s. Message %s",
                            remoteFileEntry.getFullPath(), getSerialNumber(), e.getMessage());
                    throw e;
                } finally {
                    if (syncService != null) {
                        syncService.close();
                    }
                }
                return status;
            }
        };
        return performDeviceAction(String.format("sync files %s", remoteFileEntry.getFullPath()),
                syncAction, MAX_RETRY_ATTEMPTS);
    }

    /**
     * Queries the file listing service for a given directory
     *
     * @param remoteFileEntry
     * @throws DeviceNotAvailableException
     */
    FileEntry[] getFileChildren(final FileEntry remoteFileEntry)
            throws DeviceNotAvailableException {
        // time this operation because its known to hang
        FileQueryAction action = new FileQueryAction(remoteFileEntry,
                getIDevice().getFileListingService());
        performDeviceAction("buildFileCache", action, 1 /* one retry */);
        return action.mFileContents;
    }

    private class FileQueryAction implements DeviceAction {

        FileEntry[] mFileContents = null;
        private final FileEntry mRemoteFileEntry;
        private final FileListingService mService;

        FileQueryAction(FileEntry remoteFileEntry, FileListingService service) {
            throwIfNull(remoteFileEntry);
            throwIfNull(service);
            mRemoteFileEntry = remoteFileEntry;
            mService = service;
        }

        @Override
        public boolean run() throws TimeoutException, IOException, AdbCommandRejectedException,
                ShellCommandUnresponsiveException {
            mFileContents = mService.getChildrenSync(mRemoteFileEntry);
            return true;
        }
    }

    /**
     * A {@link FilenameFilter} that rejects hidden (ie starts with ".") files.
     */
    private static class NoHiddenFilesFilter implements FilenameFilter {
        /**
         * {@inheritDoc}
         */
        @Override
        public boolean accept(File dir, String name) {
            return !name.startsWith(".");
        }
    }

    /**
     * helper to get the timezone from the device. Example: "Europe/London"
     */
    private String getDeviceTimezone() {
        try {
            // This may not be set at first, default to GMT in this case.
            String timezone = getProperty("persist.sys.timezone");
            if (timezone != null) {
                return timezone.trim();
            }
        } catch (DeviceNotAvailableException e) {
            // Fall through on purpose
        }
        return "GMT";
    }

    /**
     * Return <code>true</code> if local file is newer than remote file. {@link IFileEntry} being
     * accurate to the minute, in case of equal times, the file will be considered newer.
     */
    @VisibleForTesting
    protected boolean isNewer(File localFile, IFileEntry entry) {
        final String entryTimeString = String.format("%s %s", entry.getDate(), entry.getTime());
        try {
            String timezone = getDeviceTimezone();
            // expected format of a FileEntry's date and time
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            format.setTimeZone(TimeZone.getTimeZone(timezone));
            Date remoteDate = format.parse(entryTimeString);

            long offset = 0;
            try {
                offset = getDeviceTimeOffset(null);
            } catch (DeviceNotAvailableException e) {
                offset = 0;
            }
            CLog.i("Device offset time: %s", offset);

            // localFile.lastModified has granularity of ms, but remoteDate.getTime only has
            // granularity of minutes. Shift remoteDate.getTime() backward by one minute so newly
            // modified files get synced
            return localFile.lastModified() > (remoteDate.getTime() - 60 * 1000 + offset);
        } catch (ParseException e) {
            CLog.e("Error converting remote time stamp %s for %s on device %s", entryTimeString,
                    entry.getFullPath(), getSerialNumber());
        }
        // sync file by default
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String executeAdbCommand(String... cmdArgs) throws DeviceNotAvailableException {
        return executeAdbCommand(getCommandTimeout(), cmdArgs);
    }

    /** {@inheritDoc} */
    @Override
    public String executeAdbCommand(long timeout, String... cmdArgs)
            throws DeviceNotAvailableException {
        return executeAdbCommand(getCommandTimeout(), new HashMap<>(), cmdArgs);
    }

    /** {@inheritDoc} */
    @Override
    public String executeAdbCommand(long timeout, Map<String, String> envMap, String... cmdArgs)
            throws DeviceNotAvailableException {
        final String[] fullCmd = buildAdbCommand(cmdArgs);
        AdbAction adbAction = new AdbAction(timeout, fullCmd, "shell".equals(cmdArgs[0]), envMap);
        performDeviceAction(String.format("adb %s", cmdArgs[0]), adbAction, MAX_RETRY_ATTEMPTS);
        return adbAction.mOutput;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResult executeFastbootCommand(String... cmdArgs)
            throws DeviceNotAvailableException, UnsupportedOperationException {
        // TODO: fix mixed use of fastboot timeout and command timeout
        return doFastbootCommand(getCommandTimeout(), cmdArgs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResult executeFastbootCommand(long timeout, String... cmdArgs)
            throws DeviceNotAvailableException, UnsupportedOperationException {
        return doFastbootCommand(timeout, cmdArgs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResult executeLongFastbootCommand(String... cmdArgs)
            throws DeviceNotAvailableException, UnsupportedOperationException {
        return executeLongFastbootCommand(new HashMap<>(), cmdArgs);
    }

    /** {@inheritDoc} */
    @Override
    public CommandResult executeLongFastbootCommand(
            Map<String, String> envVarMap, String... cmdArgs)
            throws DeviceNotAvailableException, UnsupportedOperationException {
        // TODO: fix mixed use of fastboot timeout and command timeout
        return doFastbootCommand(getLongCommandTimeout(), envVarMap, cmdArgs);
    }

    /**
     * Do a fastboot command with environment variables set
     *
     * @param timeout timeout for the fastboot command
     * @param envVarMap environment variables that needs to be set before execute the fastboot
     *     command
     * @param cmdArgs
     * @return {@link CommandResult} of the fastboot command
     * @throws DeviceNotAvailableException
     * @throws UnsupportedOperationException
     */
    private CommandResult doFastbootCommand(
            final long timeout, Map<String, String> envVarMap, String... cmdArgs)
            throws DeviceNotAvailableException, UnsupportedOperationException {
        if (!mFastbootEnabled) {
            throw new UnsupportedOperationException(String.format(
                    "Attempted to fastboot on device %s , but fastboot is not available. Aborting.",
                    getSerialNumber()));
        }

        File fastbootTmpDir = getHostOptions().getFastbootTmpDir();
        if (fastbootTmpDir != null) {
            envVarMap.put("TMPDIR", fastbootTmpDir.getAbsolutePath());
        }

        final String[] fullCmd = buildFastbootCommand(cmdArgs);

        for (int i = 0; i < MAX_RETRY_ATTEMPTS; i++) {
            try (CloseableTraceScope ignored = new CloseableTraceScope("fastboot " + cmdArgs[0])) {
                CommandResult result = simpleFastbootCommand(timeout, envVarMap, fullCmd);
                if (!isRecoveryNeeded(result)) {
                    return result;
                }
                CLog.w("Recovery needed after executing fastboot command");
                if (result != null) {
                    CLog.v(
                            "fastboot command output:\nstdout: %s\nstderr:%s",
                            result.getStdout(), result.getStderr());
                }
                recoverDeviceFromBootloader();
            }
        }
        throw new DeviceUnresponsiveException(
                String.format(
                        "Attempted fastboot %s multiple "
                                + "times on device %s without communication success. Aborting.",
                        cmdArgs[0], getSerialNumber()),
                getSerialNumber());
    }

    /**
     * Do a fastboot command
     *
     * @param timeout timeout for the fastboot command
     * @param cmdArgs
     * @return {@link CommandResult} of the fastboot command
     * @throws DeviceNotAvailableException
     * @throws UnsupportedOperationException
     */
    private CommandResult doFastbootCommand(final long timeout, String... cmdArgs)
            throws DeviceNotAvailableException, UnsupportedOperationException {
        return doFastbootCommand(timeout, new HashMap<>(), cmdArgs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getUseFastbootErase() {
        return mOptions.getUseFastbootErase();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUseFastbootErase(boolean useFastbootErase) {
        mOptions.setUseFastbootErase(useFastbootErase);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResult fastbootWipePartition(String partition)
            throws DeviceNotAvailableException {
        if (mOptions.getUseFastbootErase()) {
            return executeLongFastbootCommand("erase", partition);
        } else {
            return executeLongFastbootCommand("format", partition);
        }
    }

    /**
     * Evaluate the given fastboot result to determine if recovery mode needs to be entered
     *
     * @param fastbootResult the {@link CommandResult} from a fastboot command
     * @return <code>true</code> if recovery mode should be entered, <code>false</code> otherwise.
     */
    private boolean isRecoveryNeeded(CommandResult fastbootResult) {
        if (fastbootResult.getStatus().equals(CommandStatus.TIMED_OUT)) {
            // fastboot commands always time out if devices is not present
            return true;
        } else {
            // check for specific error messages in result that indicate bad device communication
            // and recovery mode is needed
            if (fastbootResult.getStderr() == null ||
                fastbootResult.getStderr().contains("data transfer failure (Protocol error)") ||
                fastbootResult.getStderr().contains("status read failed (No such device)")) {
                CLog.w("Bad fastboot response from device %s. stderr: %s. Entering recovery",
                        getSerialNumber(), fastbootResult.getStderr());
                return true;
            }
        }
        return false;
    }

    /** Get the max time allowed in ms for commands. */
    long getCommandTimeout() {
        return mOptions.getAdbCommandTimeout();
    }

    /**
     * Set the max time allowed in ms for commands.
     */
    void setLongCommandTimeout(long timeout) {
        mLongCmdTimeout = timeout;
    }

    /**
     * Get the max time allowed in ms for commands.
     */
    long getLongCommandTimeout() {
        return mLongCmdTimeout;
    }

    /** Set the max time allowed in ms for commands. */
    void setCommandTimeout(long timeout) {
        mOptions.setAdbCommandTimeout(timeout);
    }

    /**
     * Builds the OS command for the given adb command and args
     */
    private String[] buildAdbCommand(String... commandArgs) {
        return ArrayUtil.buildArray(new String[] {"adb", "-s", getSerialNumber()},
                commandArgs);
    }

    /** Builds the OS command for the given adb shell command session and args */
    protected String[] buildAdbShellCommand(String command, boolean forceExitStatusDetection) {
        // TODO: implement the shell v2 support in ddmlib itself.
        String[] commandArgs =
                QuotationAwareTokenizer.tokenizeLine(
                        command,
                        /** No logging */
                        false);

        String[] exitStatusProbe;
        if (forceExitStatusDetection) {
            exitStatusProbe = new String[] {";", "echo", EXIT_STATUS_DELIMITER + "$?"};
        } else {
            exitStatusProbe = new String[] {};
        }
        return ArrayUtil.buildArray(
                new String[] {"adb", "-s", getSerialNumber(), "shell"},
                commandArgs,
                exitStatusProbe);
    }

    /**
     * Builds the OS command for the given fastboot command and args
     */
    private String[] buildFastbootCommand(String... commandArgs) {
        return ArrayUtil.buildArray(
                new String[] {getFastbootPath(), "-s", getFastbootSerialNumber()}, commandArgs);
    }

    /**
     * Performs an action on this device. Attempts to recover device and optionally retry command if
     * action fails.
     *
     * @param actionDescription a short description of action to be performed. Used for logging
     *     purposes only.
     * @param action the action to be performed
     * @param retryAttempts the retry attempts to make for action if it fails but recovery succeeds
     * @return <code>true</code> if action was performed successfully
     * @throws DeviceNotAvailableException if recovery attempt fails or max attempts done without
     *     success
     */
    protected boolean performDeviceAction(
            String actionDescription, final DeviceAction action, int retryAttempts)
            throws DeviceNotAvailableException {
        Exception lastException = null;
        try (CloseableTraceScope ignored = new CloseableTraceScope(actionDescription)) {
            for (int i = 0; i < retryAttempts + 1; i++) {
                boolean shouldRecover = true;
                try {
                    return action.run();
                } catch (TimeoutException e) {
                    logDeviceActionException(actionDescription, e, false);
                    lastException = e;
                } catch (IOException e) {
                    logDeviceActionException(actionDescription, e, true);
                    lastException = e;
                } catch (InstallException e) {
                    logDeviceActionException(actionDescription, e, true);
                    lastException = e;
                } catch (SyncException e) {
                    logDeviceActionException(actionDescription, e, true);
                    lastException = e;
                    // a SyncException is not necessarily a device communication problem
                    // do additional diagnosis
                    if (!e.getErrorCode().equals(SyncError.BUFFER_OVERRUN)
                            && !e.getErrorCode().equals(SyncError.TRANSFER_PROTOCOL_ERROR)) {
                        // this is a logic problem, doesn't need recovery or to be retried
                        return false;
                    }
                } catch (AdbCommandRejectedException e) {
                    // Workaround to not recover device if TCP adb is used.
                    if (isAdbTcp()
                            && (action instanceof RebootDeviceAction)
                            && ((RebootDeviceAction) action).isFastbootOrBootloader()) {
                        CLog.d(
                                "Ignore AdbCommandRejectedException when TCP device is rebooted"
                                        + " into fastboot.");
                        return true;
                    }
                    lastException = e;
                    logDeviceActionException(actionDescription, e, false);
                } catch (ShellCommandUnresponsiveException e) {
                    // ShellCommandUnresponsiveException is thrown when no output occurs within the
                    // timeout. It doesn't necessarily mean the device is offline.
                    shouldRecover = false;
                    lastException = e;
                    CLog.w(
                            "Command: '%s' on '%s' went over its timeout for outputing a response.",
                            actionDescription, getSerialNumber());
                }
                if (shouldRecover) {
                    recoverDevice();
                }
            }
            if (retryAttempts > 0) {
                throw new DeviceUnresponsiveException(
                        String.format(
                                "Attempted %s multiple times "
                                        + "on device %s without communication success. Aborting.",
                                actionDescription, getSerialNumber()),
                        lastException,
                        getSerialNumber(),
                        DeviceErrorIdentifier.DEVICE_UNRESPONSIVE);
            }
            return false;
        }
    }

    /**
     * Log an entry for given exception
     *
     * @param actionDescription the action's description
     * @param e the exception
     * @param logFullTrace whether the full exception stack trace should be logged
     */
    private void logDeviceActionException(
            String actionDescription, Exception e, boolean logFullTrace) {
        CLog.w("%s (%s) when attempting %s on device %s", e.getClass().getSimpleName(),
                getExceptionMessage(e), actionDescription, getSerialNumber());
        if (logFullTrace) {
            CLog.w(e);
        }
    }

    /**
     * Make a best effort attempt to retrieve a meaningful short descriptive message for given
     * {@link Exception}
     *
     * @param e the {@link Exception}
     * @return a short message
     */
    private String getExceptionMessage(Exception e) {
        StringBuilder msgBuilder = new StringBuilder();
        if (e.getMessage() != null) {
            msgBuilder.append(e.getMessage());
        }
        if (e.getCause() != null) {
            msgBuilder.append(" cause: ");
            msgBuilder.append(e.getCause().getClass().getSimpleName());
            if (e.getCause().getMessage() != null) {
                msgBuilder.append(" (");
                msgBuilder.append(e.getCause().getMessage());
                msgBuilder.append(")");
            }
        }
        return msgBuilder.toString();
    }

    /**
     * Attempts to recover device communication.
     *
     * @throws DeviceNotAvailableException if device is no longer available
     */
    @Override
    public boolean recoverDevice() throws DeviceNotAvailableException {
        getConnection().reconnectForRecovery(getSerialNumber());
        if (mRecoveryMode.equals(RecoveryMode.NONE)) {
            CLog.i("Skipping recovery on %s", getSerialNumber());
            return false;
        }
        CLog.i("Attempting recovery on %s", getSerialNumber());
        InvocationMetricLogger.addInvocationMetrics(InvocationMetricKey.RECOVERY_ROUTINE_COUNT, 1);
        long startTime = System.currentTimeMillis();
        try {
            try {
                mRecovery.recoverDevice(mStateMonitor, mRecoveryMode.equals(RecoveryMode.ONLINE));
            } catch (DeviceUnresponsiveException due) {
                RecoveryMode previousRecoveryMode = mRecoveryMode;
                mRecoveryMode = RecoveryMode.NONE;
                try {
                    boolean enabled = enableAdbRoot();
                    CLog.d(
                            "Device Unresponsive during recovery, is root still enabled: %s",
                            enabled);
                } catch (DeviceUnresponsiveException e) {
                    // Ignore exception thrown here to rethrow original exception.
                    CLog.e("Exception occurred during recovery adb root:");
                    CLog.e(e);
                    Throwable cause = e.getCause();
                    if (cause != null && cause instanceof AdbCommandRejectedException) {
                        AdbCommandRejectedException adbException =
                                (AdbCommandRejectedException) cause;
                        if (adbException.isDeviceOffline()
                                || adbException.wasErrorDuringDeviceSelection()) {
                            // Upgrade exception to DNAE to reflect gravity
                            throw new DeviceNotAvailableException(
                                    cause.getMessage(),
                                    adbException,
                                    getSerialNumber(),
                                    DeviceErrorIdentifier.DEVICE_UNAVAILABLE);
                        }
                    }
                }
                mRecoveryMode = previousRecoveryMode;
                throw due;
            }
            if (mRecoveryMode.equals(RecoveryMode.AVAILABLE)) {
                // turn off recovery mode to prevent reentrant recovery
                // TODO: look for a better way to handle this, such as doing postBootUp steps in
                // recovery itself
                mRecoveryMode = RecoveryMode.NONE;
                // this might be a runtime reset - still need to run post boot setup steps
                if (isEncryptionSupported() && isDeviceEncrypted()) {
                    unlockDevice();
                }
                postBootSetup();
                mRecoveryMode = RecoveryMode.AVAILABLE;
            } else if (mRecoveryMode.equals(RecoveryMode.ONLINE)) {
                // turn off recovery mode to prevent reentrant recovery
                // TODO: look for a better way to handle this, such as doing postBootUp steps in
                // recovery itself
                mRecoveryMode = RecoveryMode.NONE;
                enableAdbRoot();
                mRecoveryMode = RecoveryMode.ONLINE;
            }
        } finally {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.RECOVERY_TIME, System.currentTimeMillis() - startTime);
        }
        CLog.i("Recovery successful for %s", getSerialNumber());
        return true;
    }

    /**
     * Attempts to recover device fastboot communication.
     *
     * @throws DeviceNotAvailableException if device is not longer available
     */
    private void recoverDeviceFromBootloader() throws DeviceNotAvailableException {
        CLog.i("Attempting recovery on %s in bootloader", getSerialNumber());
        mRecovery.recoverDeviceBootloader(mStateMonitor);
        CLog.i("Bootloader recovery successful for %s", getSerialNumber());
    }

    private void recoverDeviceFromFastbootd() throws DeviceNotAvailableException {
        CLog.i("Attempting recovery on %s in fastbootd", getSerialNumber());
        mRecovery.recoverDeviceFastbootd(mStateMonitor);
        CLog.i("Fastbootd recovery successful for %s", getSerialNumber());
    }

    private void recoverDeviceInRecovery() throws DeviceNotAvailableException {
        CLog.i("Attempting recovery on %s in recovery", getSerialNumber());
        mRecovery.recoverDeviceRecovery(mStateMonitor);
        CLog.i("Recovery mode recovery successful for %s", getSerialNumber());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startLogcat() {
        if (mLogcatReceiver != null) {
            CLog.d("Already capturing logcat for %s, ignoring", getSerialNumber());
            return;
        }
        mLogcatReceiver = createLogcatReceiver();
        mLogcatReceiver.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearLogcat() {
        if (mLogcatReceiver != null) {
            mLogcatReceiver.clear();
        }
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("MustBeClosedChecker")
    public InputStreamSource getLogcat() {
        if (mLogcatReceiver == null) {
            if (!(getIDevice() instanceof StubDevice)) {
                TestDeviceState state = getDeviceState();
                if (!TestDeviceState.ONLINE.equals(state)) {
                    CLog.w("Skipping logcat capture, no buffer and device state is '%s'", state);
                } else {
                    CLog.w(
                            "Not capturing logcat for %s in background, returning a logcat dump",
                            getSerialNumber());
                    return getLogcatDump();
                }
            }
            return new ByteArrayInputStreamSource(new byte[0]);
        } else {
            return mLogcatReceiver.getLogcatData();
        }
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("MustBeClosedChecker")
    public InputStreamSource getLogcat(int maxBytes) {
        if (mLogcatReceiver == null) {
            CLog.w("Not capturing logcat for %s in background, returning a logcat dump "
                    + "ignoring size", getSerialNumber());
            return getLogcatDump();
        } else {
            return mLogcatReceiver.getLogcatData(maxBytes);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStreamSource getLogcatSince(long date) {
        int deviceApiLevel;
        try {
            deviceApiLevel = getApiLevel();
            if (deviceApiLevel <= 22) {
                CLog.i("Api level too low to use logcat -t 'time' reverting to dump");
                return getLogcatDump();
            }
        } catch (DeviceNotAvailableException e) {
            // For convenience of interface, we catch the DNAE here.
            CLog.e(e);
            return getLogcatDump();
        }

        String dateFormatted;
        if (deviceApiLevel >= 24) {
            // Use 'sssss.mmm' epoch time format supported since API 24.
            dateFormatted = String.format(Locale.US, "%d.%03d", date / 1000, date % 1000);
        } else {
            // Convert date to format needed by the command:
            // 'MM-DD HH:mm:ss.mmm' or 'YYYY-MM-DD HH:mm:ss.mmm'
            SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");
            dateFormatted = format.format(new Date(date));
        }

        LargeOutputReceiver largeReceiver = null;
        try {
            // use IDevice directly because we don't want callers to handle
            // DeviceNotAvailableException for this method
            largeReceiver =
                    new LargeOutputReceiver(
                            "getLogcatSince",
                            getSerialNumber(),
                            getOptions().getMaxLogcatDataSize());
            String command =
                    String.format(
                            "%s -t '%s'", LogcatReceiver.getDefaultLogcatCmd(this), dateFormatted);
            getIDevice().executeShellCommand(command, largeReceiver);
            return largeReceiver.getData();
        } catch (IOException|AdbCommandRejectedException|
                ShellCommandUnresponsiveException|TimeoutException e) {
            CLog.w("Failed to get logcat dump from %s: %s", getSerialNumber(), e.getMessage());
            CLog.e(e);
        } finally {
            if (largeReceiver != null) {
                largeReceiver.cancel();
                largeReceiver.delete();
            }
        }
        return new ByteArrayInputStreamSource(new byte[0]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStreamSource getLogcatDump() {
        long startTime = System.currentTimeMillis();
        LargeOutputReceiver largeReceiver = null;
        try (CloseableTraceScope ignored = new CloseableTraceScope("getLogcatDump")) {
            // use IDevice directly because we don't want callers to handle
            // DeviceNotAvailableException for this method
            largeReceiver =
                    new LargeOutputReceiver(
                            "getLogcatDump",
                            getSerialNumber(),
                            getOptions().getMaxLogcatDataSize());
            // add -d parameter to make this a non blocking call
            getIDevice()
                    .executeShellCommand(
                            LogcatReceiver.getDefaultLogcatCmd(this) + " -d",
                            largeReceiver,
                            LOGCAT_DUMP_TIMEOUT,
                            LOGCAT_DUMP_TIMEOUT,
                            TimeUnit.MILLISECONDS);
            return largeReceiver.getData();
        } catch (IOException e) {
            CLog.w("Failed to get logcat dump from %s: ", getSerialNumber(), e.getMessage());
        } catch (TimeoutException e) {
            CLog.w("Failed to get logcat dump from %s: timeout", getSerialNumber());
        } catch (AdbCommandRejectedException e) {
            CLog.w("Failed to get logcat dump from %s: ", getSerialNumber(), e.getMessage());
        } catch (ShellCommandUnresponsiveException e) {
            CLog.w("Failed to get logcat dump from %s: ", getSerialNumber(), e.getMessage());
        } finally {
            if (largeReceiver != null) {
                largeReceiver.cancel();
                largeReceiver.delete();
            }
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.LOGCAT_DUMP_TIME, System.currentTimeMillis() - startTime);
            InvocationMetricLogger.addInvocationMetrics(InvocationMetricKey.LOGCAT_DUMP_COUNT, 1);
        }
        return new ByteArrayInputStreamSource(new byte[0]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopLogcat() {
        if (mLogcatReceiver != null) {
            mLogcatReceiver.stop();
            mLogcatReceiver = null;
        } else {
            CLog.w("Attempting to stop logcat when not capturing for %s", getSerialNumber());
        }
    }

    /** Factory method to create a {@link LogcatReceiver}. */
    @VisibleForTesting
    LogcatReceiver createLogcatReceiver() {
        String logcatOptions = mOptions.getLogcatOptions();
        if (SystemUtil.isLocalMode()) {
            mLogStartDelay = 0;
        }
        if (logcatOptions == null) {
            return new LogcatReceiver(this, mOptions.getMaxLogcatDataSize(), mLogStartDelay);
        } else {
            return new LogcatReceiver(
                    this,
                    String.format("%s %s", LogcatReceiver.getDefaultLogcatCmd(this), logcatOptions),
                    mOptions.getMaxLogcatDataSize(),
                    mLogStartDelay);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStreamSource getBugreport() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean logBugreport(String dataName, ITestLogger listener) {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bugreport takeBugreport() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStreamSource getBugreportz() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public boolean logAnrs(ITestLogger logger) throws DeviceNotAvailableException {
        if (!doesFileExist(ANRS_PATH)) {
            CLog.d("No ANRs at %s", ANRS_PATH);
            return true;
        }
        boolean root = enableAdbRoot();
        if (!root) {
            CLog.d("Skipping logAnrs, need to be root.");
        }
        File localDir = null;
        long startTime = System.currentTimeMillis();
        try {
            localDir = FileUtil.createTempDir("pulled-anrs");
            boolean success = pullDir(ANRS_PATH, localDir);
            if (!success) {
                CLog.w("Failed to pull %s", ANRS_PATH);
                return false;
            }
            if (localDir.listFiles().length == 0) {
                return true;
            }
            for (File f : localDir.listFiles()) {
                try (FileInputStreamSource source = new FileInputStreamSource(f)) {
                    String name = f.getName();
                    LogDataType type = LogDataType.ANRS;
                    if (name.startsWith("dumptrace")) {
                        type = LogDataType.DUMPTRACE;
                    }
                    logger.testLog(name, type, source);
                }
            }
        } catch (IOException e) {
            CLog.e(e);
            return false;
        } finally {
            FileUtil.recursiveDelete(localDir);
        }
        InvocationMetricLogger.addInvocationMetrics(
                InvocationMetricKey.ANR_TIME, System.currentTimeMillis() - startTime);
        InvocationMetricLogger.addInvocationMetrics(InvocationMetricKey.ANR_COUNT, 1);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStreamSource getScreenshot() throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for Screenshot");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStreamSource getScreenshot(String format) throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for Screenshot");
    }

    /** {@inheritDoc} */
    @Override
    public InputStreamSource getScreenshot(String format, boolean rescale)
            throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for Screenshot");
    }

    /** {@inheritDoc} */
    @Override
    public InputStreamSource getScreenshot(long displayId) throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for Screenshot");
    }

    /** {@inheritDoc} */
    @Override
    public void clearLastConnectedWifiNetwork() {
        mLastConnectedWifiSsid = null;
        mLastConnectedWifiPsk = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean connectToWifiNetwork(String wifiSsid, String wifiPsk)
            throws DeviceNotAvailableException {
        return connectToWifiNetwork(wifiSsid, wifiPsk, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean connectToWifiNetwork(String wifiSsid, String wifiPsk, boolean scanSsid)
            throws DeviceNotAvailableException {
        LinkedHashMap<String, String> ssidToPsk = new LinkedHashMap<>();
        ssidToPsk.put(wifiSsid, wifiPsk);
        return connectToWifiNetwork(ssidToPsk, scanSsid);
    }

    /** {@inheritDoc}f */
    @Override
    public boolean connectToWifiNetwork(Map<String, String> wifiSsidToPsk)
            throws DeviceNotAvailableException {
        return connectToWifiNetwork(wifiSsidToPsk, false);
    }

    /** {@inheritDoc} */
    @Override
    public boolean connectToWifiNetwork(Map<String, String> wifiSsidToPsk, boolean scanSsid)
            throws DeviceNotAvailableException {
        // Clears the last connected wifi network.
        mLastConnectedWifiSsid = null;
        mLastConnectedWifiPsk = null;

        // Connects to wifi network. It retries up to {@link TestDeviceOptions@getWifiAttempts()}
        // times
        Random rnd = new Random();
        int backoffSlotCount = 2;
        int slotTime = mOptions.getWifiRetryWaitTime();
        int waitTime = 0;
        long startTime = mClock.millis();
        try (CloseableTraceScope ignored = new CloseableTraceScope("connectToWifiNetwork")) {
            for (int i = 1; i <= mOptions.getWifiAttempts(); i++) {
                boolean failedToEnableWifi = false;
                for (Map.Entry<String, String> ssidToPsk : wifiSsidToPsk.entrySet()) {
                    String wifiSsid = ssidToPsk.getKey();
                    String wifiPsk = Strings.emptyToNull(ssidToPsk.getValue());

                    InvocationMetricLogger.addInvocationMetrics(
                            InvocationMetricKey.WIFI_CONNECT_RETRY_COUNT, i);
                    CLog.i("Connecting to wifi network %s on %s", wifiSsid, getSerialNumber());
                    IWifiHelper wifi = null;
                    if (!getOptions().useCmdWifiCommands()
                            || !enableAdbRoot()
                            || getApiLevel() < 31) {
                        wifi = createWifiHelper(false);
                    } else {
                        wifi = createWifiHelper(true);
                    }
                    WifiConnectionResult result =
                            wifi.connectToNetwork(
                                    wifiSsid,
                                    wifiPsk,
                                    mOptions.getConnCheckUrl(),
                                    scanSsid,
                                    mOptions.getDefaultNetworkType());

                    final Map<String, String> wifiInfo = wifi.getWifiInfo();
                    if (WifiConnectionResult.SUCCESS.equals(result)) {
                        CLog.i(
                                "Successfully connected to wifi network %s(%s) on %s",
                                wifiSsid, wifiInfo.get("bssid"), getSerialNumber());
                        InvocationMetricLogger.addInvocationMetrics(
                                InvocationMetricKey.WIFI_AP_NAME, wifiSsid);
                        mLastConnectedWifiSsid = wifiSsid;
                        mLastConnectedWifiPsk = wifiPsk;

                        return true;
                    } else if (WifiConnectionResult.FAILED_TO_ENABLE.equals(result)) {
                        CLog.w("Failed to enable wifi");
                        failedToEnableWifi = true;
                    } else {
                        failedToEnableWifi = false;
                        CLog.w(
                                "Failed to connect to wifi network %s(%s) on %s on attempt %d of"
                                        + " %d",
                                wifiSsid,
                                wifiInfo.get("bssid"),
                                getSerialNumber(),
                                i,
                                mOptions.getWifiAttempts());
                    }
                }
                if (mClock.millis() - startTime >= mOptions.getMaxWifiConnectTime()) {
                    CLog.e(
                            "Failed to connect to wifi after %d ms. Aborting.",
                            mOptions.getMaxWifiConnectTime());
                    break;
                }
                // Do not sleep for the last iteration and when failed to enable wifi.
                if (i < mOptions.getWifiAttempts() && !failedToEnableWifi) {
                    if (mOptions.isWifiExpoRetryEnabled()) {
                        // use binary exponential back-offs when retrying.
                        waitTime = rnd.nextInt(backoffSlotCount) * slotTime;
                        backoffSlotCount *= 2;
                    }
                    CLog.e(
                            "Waiting for %d ms before reconnecting to %s...",
                            waitTime, wifiSsidToPsk.keySet().toString());
                    getRunUtil().sleep(waitTime);
                }
            }
        } finally {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.WIFI_CONNECT_TIME, mClock.millis() - startTime);
            InvocationMetricLogger.addInvocationMetrics(InvocationMetricKey.WIFI_CONNECT_COUNT, 1);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkConnectivity() throws DeviceNotAvailableException {
        IWifiHelper wifi = createWifiHelper();
        return wifi.checkConnectivity(mOptions.getConnCheckUrl());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean connectToWifiNetworkIfNeeded(String wifiSsid, String wifiPsk)
            throws DeviceNotAvailableException {
        return connectToWifiNetworkIfNeeded(wifiSsid, wifiPsk, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean connectToWifiNetworkIfNeeded(String wifiSsid, String wifiPsk, boolean scanSsid)
            throws DeviceNotAvailableException {
        if (!checkConnectivity())  {
            return connectToWifiNetwork(wifiSsid, wifiPsk, scanSsid);
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isWifiEnabled() throws DeviceNotAvailableException {
        final IWifiHelper wifi = createWifiHelper();
        try {
            return wifi.isWifiEnabled();
        } catch (RuntimeException e) {
            CLog.w("Failed to create WifiHelper: %s", e.getMessage());
            return false;
        }
    }

    /**
     * Checks that the device is currently successfully connected to given wifi SSID.
     *
     * @param wifiSSID the wifi ssid
     * @return <code>true</code> if device is currently connected to wifiSSID and has network
     *         connectivity. <code>false</code> otherwise
     * @throws DeviceNotAvailableException if connection with device was lost
     */
    boolean checkWifiConnection(String wifiSSID) throws DeviceNotAvailableException {
        CLog.i("Checking connection with wifi network %s on %s", wifiSSID, getSerialNumber());
        final IWifiHelper wifi = createWifiHelper();
        // getSSID returns SSID as "SSID"
        final String quotedSSID = String.format("\"%s\"", wifiSSID);

        boolean test = wifi.isWifiEnabled();
        CLog.v("%s: wifi enabled? %b", getSerialNumber(), test);

        if (test) {
            final String actualSSID = wifi.getSSID();
            test = quotedSSID.equals(actualSSID);
            CLog.v("%s: SSID match (%s, %s, %b)", getSerialNumber(), quotedSSID, actualSSID, test);
        }
        if (test) {
            test = wifi.hasValidIp();
            CLog.v("%s: validIP? %b", getSerialNumber(), test);
        }
        if (test) {
            test = checkConnectivity();
            CLog.v("%s: checkConnectivity returned %b", getSerialNumber(), test);
        }
        return test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean disconnectFromWifi() throws DeviceNotAvailableException {
        CLog.i("Disconnecting from wifi on %s", getSerialNumber());
        // Clears the last connected wifi network.
        mLastConnectedWifiSsid = null;
        mLastConnectedWifiPsk = null;

        IWifiHelper wifi = createWifiHelper();
        return wifi.disconnectFromNetwork();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIpAddress() throws DeviceNotAvailableException {
        IWifiHelper wifi = createWifiHelper();
        return wifi.getIpAddress();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean enableNetworkMonitor() throws DeviceNotAvailableException {
        mNetworkMonitorEnabled = false;

        IWifiHelper wifi = createWifiHelper();
        wifi.stopMonitor();
        if (wifi.startMonitor(NETWORK_MONITOR_INTERVAL, mOptions.getConnCheckUrl())) {
            mNetworkMonitorEnabled = true;
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean disableNetworkMonitor() throws DeviceNotAvailableException {
        mNetworkMonitorEnabled = false;

        IWifiHelper wifi = createWifiHelper();
        List<Long> samples = wifi.stopMonitor();
        if (!samples.isEmpty()) {
            int failures = 0;
            long totalLatency = 0;
            for (Long sample : samples) {
                if (sample < 0) {
                    failures += 1;
                } else {
                    totalLatency += sample;
                }
            }
            double failureRate = failures * 100.0 / samples.size();
            double avgLatency = 0.0;
            if (failures < samples.size()) {
                avgLatency = totalLatency / (samples.size() - failures);
            }
            CLog.d("[metric] url=%s, window=%ss, failure_rate=%.2f%%, latency_avg=%.2f",
                    mOptions.getConnCheckUrl(), samples.size() * NETWORK_MONITOR_INTERVAL / 1000,
                    failureRate, avgLatency);
        }
        return true;
    }

    /**
     * Create a {@link WifiHelper} to use
     *
     * <p>
     *
     * @throws DeviceNotAvailableException
     */
    @VisibleForTesting
    IWifiHelper createWifiHelper() throws DeviceNotAvailableException {
        // current wifi helper won't work on AndroidNativeDevice
        // TODO: create a new Wifi helper with supported feature of AndroidNativeDevice when
        // we learn what is available.
        throw new UnsupportedOperationException("Wifi helper is not supported.");
    }

    /**
     * Create a {@link WifiHelper} to use
     *
     * @param useV2 Whether to use WifiHelper v2 which does not install any apk.
     *     <p>
     * @throws DeviceNotAvailableException
     */
    @VisibleForTesting
    IWifiHelper createWifiHelper(boolean useV2) throws DeviceNotAvailableException {
        // current wifi helper won't work on AndroidNativeDevice
        // TODO: create a new Wifi helper with supported feature of AndroidNativeDevice when
        // we learn what is available.
        throw new UnsupportedOperationException("Wifi helper is not supported.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean clearErrorDialogs() throws DeviceNotAvailableException {
        CLog.e("No support for Screen's features");
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public KeyguardControllerState getKeyguardState() throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for keyguard querying.");
    }

    IDeviceStateMonitor getDeviceStateMonitor() {
        return mStateMonitor;
    }

    /** {@inheritDoc} */
    @Override
    public void postBootSetup() throws DeviceNotAvailableException {
        if (getOptions().shouldDisableReboot()) {
            return;
        }
        getConnection().reconnect(getSerialNumber());
        CLog.d("postBootSetup started");
        long startTime = System.currentTimeMillis();
        try (CloseableTraceScope ignored = new CloseableTraceScope("postBootSetup")) {
            enableAdbRoot();
            prePostBootSetup();
            for (String command : mOptions.getPostBootCommands()) {
                long start = System.currentTimeMillis();
                try (CloseableTraceScope cmdTrace = new CloseableTraceScope(command)) {
                    executeShellCommand(command);
                }
                if (command.startsWith("sleep")) {
                    InvocationMetricLogger.addInvocationPairMetrics(
                            InvocationMetricKey.host_sleep, start, System.currentTimeMillis());
                }
            }
        } finally {
            long elapsed = System.currentTimeMillis() - startTime;
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.POSTBOOT_SETUP_TIME, elapsed);
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.POSTBOOT_SETUP_COUNT, 1);
            CLog.d("postBootSetup done: %s", TimeUtil.formatElapsedTime(elapsed));
        }
    }

    /**
     * Allows each device type (AndroidNativeDevice, TestDevice) to override this method for
     * specific post boot setup.
     *
     * @throws DeviceNotAvailableException
     */
    protected void prePostBootSetup() throws DeviceNotAvailableException {
        // Empty on purpose.
    }

    /**
     * Ensure wifi connection is re-established after boot. This is intended to be called after TF
     * initiated reboots(ones triggered by {@link #reboot()}) only.
     *
     * @throws DeviceNotAvailableException
     */
    void postBootWifiSetup() throws DeviceNotAvailableException {
        CLog.d("postBootWifiSetup started");
        long startTime = System.currentTimeMillis();
        try (CloseableTraceScope ignored = new CloseableTraceScope("postBootWifiSetup")) {
            if (mLastConnectedWifiSsid != null) {
                reconnectToWifiNetwork();
            }
            if (mNetworkMonitorEnabled) {
                if (!enableNetworkMonitor()) {
                    CLog.w(
                            "Failed to enable network monitor on %s after reboot",
                            getSerialNumber());
                }
            }
        } finally {
            long elapsed = System.currentTimeMillis() - startTime;
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.POSTBOOT_WIFI_SETUP_TIME, elapsed);
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.POSTBOOT_WIFI_SETUP_COUNT, 1);
            CLog.d("postBootWifiSetup done: %s", TimeUtil.formatElapsedTime(elapsed));
        }
    }

    void reconnectToWifiNetwork() throws DeviceNotAvailableException {
        // First, wait for wifi to re-connect automatically.
        long startTime = System.currentTimeMillis();
        boolean isConnected = checkConnectivity();
        while (!isConnected && (System.currentTimeMillis() - startTime) < WIFI_RECONNECT_TIMEOUT) {
            getRunUtil().sleep(WIFI_RECONNECT_CHECK_INTERVAL);
            isConnected = checkConnectivity();
        }

        if (isConnected) {
            return;
        }

        // If wifi is still not connected, try to re-connect on our own.
        final String wifiSsid = mLastConnectedWifiSsid;
        if (!connectToWifiNetworkIfNeeded(mLastConnectedWifiSsid, mLastConnectedWifiPsk)) {
            throw new NetworkNotAvailableException(
                    String.format("Failed to connect to wifi network %s on %s after reboot",
                            wifiSsid, getSerialNumber()));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void rebootIntoBootloader()
            throws DeviceNotAvailableException, UnsupportedOperationException {
        if (isInRebootCallback()) {
            CLog.d(
                    "'%s' action is disabled during reboot callback. Ignoring.",
                    "Reboot into Bootloader");
            return;
        }
        rebootIntoFastbootInternal(true);
    }

    /** {@inheritDoc} */
    @Override
    public void rebootIntoFastbootd()
            throws DeviceNotAvailableException, UnsupportedOperationException {
        if (isInRebootCallback()) {
            CLog.d(
                    "'%s' action is disabled during reboot callback. Ignoring.",
                    "Reboot into Fastbootd");
            return;
        }
        rebootIntoFastbootInternal(false);
    }

    /**
     * Reboots the device into bootloader or fastbootd mode.
     *
     * @param isBootloader true to boot the device into bootloader mode, false to boot the device
     *     into fastbootd mode.
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     *     recovered.
     */
    private void rebootIntoFastbootInternal(boolean isBootloader)
            throws DeviceNotAvailableException {
        invalidatePropertyCache();
        final RebootMode mode =
                isBootloader ? RebootMode.REBOOT_INTO_BOOTLOADER : RebootMode.REBOOT_INTO_FASTBOOTD;
        if (!mFastbootEnabled) {
            throw new UnsupportedOperationException(
                    String.format("Fastboot is not available and cannot reboot into %s", mode));
        }
        long startTime = System.currentTimeMillis();

        try (CloseableTraceScope ignored =
                new CloseableTraceScope("reboot_in_" + mode.toString())) {
            // Update fastboot serial number before entering fastboot mode
            mStateMonitor.setFastbootSerialNumber(getFastbootSerialNumber());

            // If we go to bootloader, it's probably for flashing so ensure we re-check the provider
            mShouldSkipContentProviderSetup = false;
            CLog.i(
                    "Rebooting device %s in state %s into %s",
                    getSerialNumber(), getDeviceState(), mode);
            if (isStateBootloaderOrFastbootd()) {
                CLog.i(
                        "device %s already in %s. Rebooting anyway",
                        getSerialNumber(), getDeviceState());
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.BOOTLOADER_SAME_STATE_REBOOT, 1);
                executeFastbootCommand(String.format("reboot-%s", mode));
            } else {
                CLog.i("Booting device %s into %s", getSerialNumber(), mode);
                doAdbReboot(mode, null);
            }

            // We want to wait on a command that verifies we've rebooted.
            // However, it is possible to issue this command too quickly and get
            // a response before the device has begun the reboot process (see
            // b/242200753).
            // While not as clean as we'd like, we wait 1.5 seconds before
            // issuing any waiting commands, as devices generally take much
            // longer than 1.5 seconds to reboot anyway.
            getRunUtil().sleep(1500);

            if (RebootMode.REBOOT_INTO_FASTBOOTD.equals(mode)
                    && getHostOptions().isFastbootdEnable()) {
                if (!mStateMonitor.waitForDeviceFastbootd(
                        getFastbootPath(), mOptions.getFastbootTimeout())) {
                    recoverDeviceFromFastbootd();
                }
            } else {
                waitForDeviceBootloader();
            }
        } finally {
            long elapsedTime = System.currentTimeMillis() - startTime;
            if (RebootMode.REBOOT_INTO_FASTBOOTD.equals(mode)) {
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.FASTBOOTD_REBOOT_TIME, elapsedTime);
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.FASTBOOTD_REBOOT_COUNT, 1);
            } else {
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.BOOTLOADER_REBOOT_TIME, elapsedTime);
                InvocationMetricLogger.addInvocationMetrics(
                        InvocationMetricKey.BOOTLOADER_REBOOT_COUNT, 1);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isStateBootloaderOrFastbootd() {
        return TestDeviceState.FASTBOOT.equals(getDeviceState())
                || TestDeviceState.FASTBOOTD.equals(getDeviceState());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reboot() throws DeviceNotAvailableException {
        reboot(null);
    }

    /** {@inheritDoc} */
    @Override
    public void reboot(@Nullable String reason) throws DeviceNotAvailableException {
        if (isInRebootCallback()) {
            CLog.d("'%s' action is disabled during reboot callback. Ignoring.", "Reboot");
            return;
        }
        internalRebootUntilOnline(reason);

        RecoveryMode cachedRecoveryMode = getRecoveryMode();
        setRecoveryMode(RecoveryMode.ONLINE);

        if (isEncryptionSupported() && isDeviceEncrypted()) {
            unlockDevice();
        }

        setRecoveryMode(cachedRecoveryMode);

        try (CloseableTraceScope ignored =
                new CloseableTraceScope("reboot_waitForDeviceAvailable")) {
            waitForDeviceAvailable(mOptions.getRebootTimeout());
        }
        postBootSetup();
        postBootWifiSetup();
        // notify of reboot end here. Full reboots will end here as well as reboots from Bootloader
        // or Fastboot mode.
        notifyRebootEnded();
    }

    @Override
    public void rebootUserspace() throws DeviceNotAvailableException {
        if (isInRebootCallback()) {
            CLog.d("'%s' action is disabled during reboot callback. Ignoring.", "Reboot Userspace");
            return;
        }
        rebootUserspaceUntilOnline();

        RecoveryMode cachedRecoveryMode = getRecoveryMode();
        setRecoveryMode(RecoveryMode.ONLINE);

        if (isEncryptionSupported()) {
            if (isDeviceEncrypted()) {
                CLog.e("Device is encrypted after userspace reboot!");
                unlockDevice();
            }
        }

        setRecoveryMode(cachedRecoveryMode);

        waitForDeviceAvailable(mOptions.getRebootTimeout());
        postBootSetup();
        postBootWifiSetup();
    }

    @Override
    public void rebootUntilOnline() throws DeviceNotAvailableException {
        if (isInRebootCallback()) {
            CLog.d(
                    "'%s' action is disabled during reboot callback. Ignoring.",
                    "Reboot Until Online");
            return;
        }
        try {
            internalRebootUntilOnline(null);
        } finally {
            if (!mDeviceActionReceivers.isEmpty()) {
                CLog.d(
                        "DeviceActionReceivers were not notified after rebootUntilOnline on %s.",
                        getSerialNumber());
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void rebootUntilOnline(@Nullable String reason) throws DeviceNotAvailableException {
        if (isInRebootCallback()) {
            CLog.d(
                    "'%s' action is disabled during reboot callback. Ignoring.",
                    "Reboot Until Online");
            return;
        }
        try {
            internalRebootUntilOnline(reason);
        } finally {
            if (!mDeviceActionReceivers.isEmpty()) {
                CLog.d(
                        "DeviceActionReceivers were not notified after rebootUntilOnline on %s.",
                        getSerialNumber());
            }
        }
    }

    private void internalRebootUntilOnline(@Nullable String reason)
            throws DeviceNotAvailableException {
        long rebootStart = System.currentTimeMillis();
        try (CloseableTraceScope ignored = new CloseableTraceScope("rebootUntilOnline")) {
            // Invalidate cache before reboots
            mPropertiesCache.invalidateAll();
            doReboot(RebootMode.REBOOT_FULL, reason);
            RecoveryMode cachedRecoveryMode = getRecoveryMode();
            setRecoveryMode(RecoveryMode.ONLINE);
            waitForDeviceOnline();
            enableAdbRoot();
            setRecoveryMode(cachedRecoveryMode);
        } finally {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.ADB_REBOOT_TIME, System.currentTimeMillis() - rebootStart);
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.ADB_REBOOT_ROUTINE_COUNT, 1);
        }
    }

    @Override
    public void rebootUserspaceUntilOnline() throws DeviceNotAvailableException {
        if (isInRebootCallback()) {
            CLog.d(
                    "'%s' action is disabled during reboot callback. Ignoring.",
                    "Reboot Userspace Until Online");
            return;
        }
        doReboot(RebootMode.REBOOT_USERSPACE, null);
        RecoveryMode cachedRecoveryMode = getRecoveryMode();
        setRecoveryMode(RecoveryMode.ONLINE);
        waitForDeviceOnline();
        enableAdbRoot();
        setRecoveryMode(cachedRecoveryMode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void rebootIntoRecovery() throws DeviceNotAvailableException {
        if (isInRebootCallback()) {
            CLog.d(
                    "'%s' action is disabled during reboot callback. Ignoring.",
                    "Reboot into Recovery");
            return;
        }
        if (isStateBootloaderOrFastbootd()) {
            CLog.w("device %s in fastboot when requesting boot to recovery. " +
                    "Rebooting to userspace first.", getSerialNumber());
            internalRebootUntilOnline(null);
        }
        doAdbReboot(RebootMode.REBOOT_INTO_RECOVERY, null);
        if (!waitForDeviceInRecovery(mOptions.getAdbRecoveryTimeout())) {
            recoverDeviceInRecovery();
        }
    }


    /** {@inheritDoc} */
    @Override
    public void rebootIntoSideload() throws DeviceNotAvailableException {
        rebootIntoSideload(false);
    }
    /** {@inheritDoc} */
    @Override
    public void rebootIntoSideload(boolean autoReboot) throws DeviceNotAvailableException {
        if (isInRebootCallback()) {
            CLog.d(
                    "'%s' action is disabled during reboot callback. Ignoring.",
                    "Reboot into Sideload");
            return;
        }
        if (isStateBootloaderOrFastbootd()) {
            CLog.w(
                    "device %s in fastboot when requesting boot to sideload. "
                            + "Rebooting to userspace first.",
                    getSerialNumber());
            internalRebootUntilOnline(null);
        }
        final RebootMode rebootMode;
        if (autoReboot) {
            rebootMode = RebootMode.REBOOT_INTO_SIDELOAD_AUTO_REBOOT;
        } else {
            rebootMode = RebootMode.REBOOT_INTO_SIDELOAD;
        }
        doAdbReboot(rebootMode, null);
        if (!waitForDeviceInSideload(mOptions.getAdbRecoveryTimeout())) {
            // using recovery mode because sideload is a sub-mode under recovery
            recoverDeviceInRecovery();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void nonBlockingReboot() throws DeviceNotAvailableException {
        if (isInRebootCallback()) {
            CLog.d(
                    "'%s' action is disabled during reboot callback. Ignoring.",
                    "Non Blocking Reboot");
            return;
        }
        try {
            doReboot(RebootMode.REBOOT_FULL, null);
        } finally {
            if (!mDeviceActionReceivers.isEmpty()) {
                CLog.d(
                        "DeviceActionReceivers were not notified after nonBlockingReboot on %s.",
                        getSerialNumber());
            }
        }
    }

    /**
     * A mode of a reboot.
     *
     * <p>Source of truth for available modes is defined in init.
     */
    @VisibleForTesting
    protected enum RebootMode {
        REBOOT_FULL(""),
        REBOOT_USERSPACE("userspace"),
        REBOOT_INTO_FASTBOOTD("fastboot"),
        REBOOT_INTO_BOOTLOADER("bootloader"),
        REBOOT_INTO_SIDELOAD("sideload"),
        REBOOT_INTO_SIDELOAD_AUTO_REBOOT("sideload-auto-reboot"),
        REBOOT_INTO_RECOVERY("recovery");

        private final String mRebootTarget;

        RebootMode(String rebootTarget) {
            mRebootTarget = rebootTarget;
        }

        @Nullable
        String formatRebootCommand(@Nullable String reason) {
            if (this == REBOOT_FULL) {
                return Strings.isNullOrEmpty(reason) ? null : reason;
            } else {
                return Strings.isNullOrEmpty(reason) ? mRebootTarget : mRebootTarget + "," + reason;
            }
        }

        @Override
        public String toString() {
            return mRebootTarget;
        }
    }

    /**
     * Trigger a reboot of the device, offers no guarantee of the device state after the call.
     *
     * @param rebootMode a mode of this reboot
     * @param reason reason for this reboot
     * @throws DeviceNotAvailableException
     * @throws UnsupportedOperationException
     */
    @VisibleForTesting
    void doReboot(RebootMode rebootMode, @Nullable final String reason)
            throws DeviceNotAvailableException, UnsupportedOperationException {
        // Track Tradefed reboot time
        mLastTradefedRebootTime = System.currentTimeMillis();

        if (isStateBootloaderOrFastbootd()) {
            CLog.i("device %s in %s. Rebooting to userspace.", getSerialNumber(), getDeviceState());
            executeFastbootCommand("reboot");
        } else {
            if (mOptions.shouldDisableReboot()) {
                CLog.i("Device reboot disabled by options, skipped.");
                return;
            }
            if (reason == null) {
                CLog.i("Rebooting device %s mode: %s", getSerialNumber(), rebootMode.name());
            } else {
                CLog.i(
                        "Rebooting device %s mode: %s reason: %s",
                        getSerialNumber(), rebootMode.name(), reason);
            }
            doAdbReboot(rebootMode, reason);
            postAdbReboot();
        }
    }

    /**
     * Possible extra actions that can be taken after a reboot.
     *
     * @throws DeviceNotAvailableException
     */
    protected void postAdbReboot() throws DeviceNotAvailableException {
        // Check if device shows as unavailable (as expected after reboot).
        boolean notAvailable = waitForDeviceNotAvailable(DEFAULT_UNAVAILABLE_TIMEOUT);
        if (!notAvailable) {
            CLog.w("Did not detect device %s becoming unavailable after reboot", getSerialNumber());
        }
        getConnection().reconnect(getSerialNumber());
    }

    /**
     * Perform a adb reboot.
     *
     * @param rebootMode a mode of this reboot.
     * @param reason for this reboot.
     * @throws DeviceNotAvailableException
     */
    protected void doAdbReboot(RebootMode rebootMode, @Nullable final String reason)
            throws DeviceNotAvailableException {
        getConnection().notifyAdbRebootCalled();
        DeviceAction rebootAction = createRebootDeviceAction(rebootMode, reason);
        performDeviceAction("reboot", rebootAction, MAX_RETRY_ATTEMPTS);
    }

    /**
     * Create a {@link RebootDeviceAction} to be used when performing a reboot action.
     *
     * @param rebootMode a mode of this reboot.
     * @param reason for this reboot.
     * @return the created {@link RebootDeviceAction}.
     */
    protected RebootDeviceAction createRebootDeviceAction(
            RebootMode rebootMode, @Nullable final String reason) {
        return new RebootDeviceAction(rebootMode, reason);
    }

    /**
     * Wait to see the device going unavailable (stop reporting to adb).
     *
     * @param operationDesc The name of the operation that is waiting for unavailable.
     * @param time The time to wait for unavailable to occur.
     * @return True if device did become unavailable.
     */
    protected boolean waitForDeviceNotAvailable(String operationDesc, long time) {
        // TODO: a bit of a race condition here. Would be better to start a
        // before the operation
        if (!mStateMonitor.waitForDeviceNotAvailable(time)) {
            // above check is flaky, ignore till better solution is found
            CLog.w("Did not detect device %s becoming unavailable after %s", getSerialNumber(),
                    operationDesc);
            return false;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean waitForDeviceNotAvailable(long waitTime) {
        return mStateMonitor.waitForDeviceNotAvailable(waitTime);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean enableAdbRoot() throws DeviceNotAvailableException {
        // adb root is a relatively intensive command, so do a brief check first to see
        // if its necessary or not
        if (isAdbRoot()) {
            CLog.i("adb is already running as root on %s", getSerialNumber());
            // Still check for online, in some case we could see the root, but device could be
            // very early in its cycle.
            waitForDeviceOnline();
            return true;
        }
        // Don't enable root if user requested no root
        if (!isEnableAdbRoot()) {
            CLog.i("\"enable-root\" set to false; ignoring 'adb root' request");
            return false;
        }
        InvocationMetricLogger.addInvocationMetrics(InvocationMetricKey.ADB_ROOT_ROUTINE_COUNT, 1);
        long startTime = System.currentTimeMillis();
        try (CloseableTraceScope ignored = new CloseableTraceScope("adb_root")) {
            CLog.i("adb root on device %s", getSerialNumber());
            int attempts = MAX_RETRY_ATTEMPTS + 1;
            for (int i = 1; i <= attempts; i++) {
                String output = executeAdbCommand("root");
                // wait for device to disappear from adb
                boolean res =
                        waitForDeviceNotAvailable(
                                "root", getOptions().getAdbRootUnavailableTimeout());
                if (!res && TestDeviceState.ONLINE.equals(getDeviceState())) {
                    if (isAdbRoot()) {
                        return true;
                    }
                }

                postAdbRootAction();

                // wait for device to be back online
                waitForDeviceOnline();

                if (isAdbRoot()) {
                    return true;
                }
                CLog.w(
                        "'adb root' on %s unsuccessful on attempt %d of %d. Output: '%s'",
                        getSerialNumber(), i, attempts, output);
            }
            return false;
        } finally {
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.ADB_ROOT_TIME, System.currentTimeMillis() - startTime);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean disableAdbRoot() throws DeviceNotAvailableException {
        if (!isAdbRoot()) {
            CLog.i("adb is already unroot on %s", getSerialNumber());
            return true;
        }

        CLog.i("adb unroot on device %s", getSerialNumber());
        int attempts = MAX_RETRY_ATTEMPTS + 1;
        for (int i=1; i <= attempts; i++) {
            String output = executeAdbCommand("unroot");
            // wait for device to disappear from adb
            waitForDeviceNotAvailable("unroot", 5 * 1000);

            postAdbUnrootAction();

            // wait for device to be back online
            waitForDeviceOnline();

            if (!isAdbRoot()) {
                return true;
            }
            CLog.w("'adb unroot' on %s unsuccessful on attempt %d of %d. Output: '%s'",
                    getSerialNumber(), i, attempts, output);
        }
        return false;
    }

    /**
     * Override if the device needs some specific actions to be taken after adb root and before the
     * device is back online.
     * Default implementation doesn't include any addition actions.
     * adb root is not guaranteed to be enabled at this stage.
     * @throws DeviceNotAvailableException
     */
    public void postAdbRootAction() throws DeviceNotAvailableException {
        getConnection().reconnect(getSerialNumber());
    }

    /**
     * Override if the device needs some specific actions to be taken after adb unroot and before
     * the device is back online.
     * Default implementation doesn't include any additional actions.
     * adb root is not guaranteed to be disabled at this stage.
     * @throws DeviceNotAvailableException
     */
    public void postAdbUnrootAction() throws DeviceNotAvailableException {
        getConnection().reconnect(getSerialNumber());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAdbRoot() throws DeviceNotAvailableException {
        String output = executeShellCommand("id");
        return output.contains("uid=0(root)");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean unlockDevice() throws DeviceNotAvailableException,
            UnsupportedOperationException {
        if (!isEncryptionSupported()) {
            throw new UnsupportedOperationException(String.format("Can't unlock device %s: "
                    + "encryption not supported", getSerialNumber()));
        }

        if (!isDeviceEncrypted()) {
            CLog.d("Device %s is not encrypted, skipping", getSerialNumber());
            return true;
        }
        String encryptionType = getProperty("ro.crypto.type");
        if (!"block".equals(encryptionType)) {
            CLog.d(
                    "Skipping unlockDevice since it's not encrypted. ro.crypto.type=%s",
                    encryptionType);
            return true;
        }

        CLog.i("Unlocking device %s", getSerialNumber());

        enableAdbRoot();

        // FIXME: currently, vcd checkpw can return an empty string when it never should.  Try 3
        // times.
        String output;
        int i = 0;
        do {
            // Enter the password. Output will be:
            // "200 [X] -1" if the password has already been entered correctly,
            // "200 [X] 0" if the password is entered correctly,
            // "200 [X] N" where N is any positive number if the password is incorrect,
            // any other string if there is an error.
            output = executeShellCommand(String.format("vdc cryptfs checkpw \"%s\"",
                    ENCRYPTION_PASSWORD)).trim();

            if (output.startsWith("200 ") && output.endsWith(" -1")) {
                return true;
            }

            if (!output.isEmpty() && !(output.startsWith("200 ") && output.endsWith(" 0"))) {
                CLog.e("checkpw gave output '%s' while trying to unlock device %s",
                        output, getSerialNumber());
                return false;
            }

            getRunUtil().sleep(500);
        } while (output.isEmpty() && ++i < 3);

        if (output.isEmpty()) {
            CLog.e("checkpw gave no output while trying to unlock device %s");
        }

        // Restart the framework. Output will be:
        // "200 [X] 0" if the user data partition can be mounted,
        // "200 [X] -1" if the user data partition can not be mounted (no correct password given),
        // any other string if there is an error.
        output = executeShellCommand("vdc cryptfs restart").trim();

        if (!(output.startsWith("200 ") &&  output.endsWith(" 0"))) {
            CLog.e("restart gave output '%s' while trying to unlock device %s", output,
                    getSerialNumber());
            return false;
        }

        waitForDeviceAvailable();

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDeviceEncrypted() throws DeviceNotAvailableException {
        String output = getProperty("ro.crypto.state");

        if (output == null && isEncryptionSupported()) {
            CLog.w("Property ro.crypto.state is null on device %s", getSerialNumber());
        }
        if (output == null) {
            return false;
        }
        return "encrypted".equals(output.trim());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEncryptionSupported() throws DeviceNotAvailableException {
        if (!isEnableAdbRoot()) {
            CLog.i("root is required for encryption");
            mIsEncryptionSupported = false;
            return mIsEncryptionSupported;
        }
        if (mIsEncryptionSupported != null) {
            return mIsEncryptionSupported.booleanValue();
        }
        enableAdbRoot();

        String output = getProperty("ro.crypto.state");
        if (output == null || "unsupported".equals(output.trim())) {
            mIsEncryptionSupported = false;
            return mIsEncryptionSupported;
        }
        mIsEncryptionSupported = true;
        return mIsEncryptionSupported;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void waitForDeviceOnline(long waitTime) throws DeviceNotAvailableException {
        if (mStateMonitor.waitForDeviceOnline(waitTime) == null) {
            recoverDevice();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void waitForDeviceOnline() throws DeviceNotAvailableException {
        if (mStateMonitor.waitForDeviceOnline() == null) {
            recoverDevice();
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean waitForDeviceAvailable(long waitTime) throws DeviceNotAvailableException {
        if (mStateMonitor.waitForDeviceAvailable(waitTime) == null) {
            return recoverDevice();
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean waitForDeviceAvailable() throws DeviceNotAvailableException {
        if (mStateMonitor.waitForDeviceAvailable() == null) {
            return recoverDevice();
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean waitForDeviceAvailableInRecoverPath(final long waitTime)
            throws DeviceNotAvailableException {
        return mStateMonitor.waitForDeviceAvailableInRecoverPath(waitTime) != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean waitForDeviceInRecovery(long waitTime) {
        return mStateMonitor.waitForDeviceInRecovery(waitTime);
    }

    /** {@inheritDoc} */
    @Override
    public void waitForDeviceBootloader() throws DeviceNotAvailableException {
        if (mOptions.useUpdatedBootloaderStatus()) {
            CommandResult commandResult =
                    simpleFastbootCommand(
                            mOptions.getFastbootTimeout(),
                            buildFastbootCommand("getvar", "product"));
            if (!CommandStatus.SUCCESS.equals(commandResult.getStatus())) {
                CLog.e(
                        "Waiting for device in bootloader. Status: %s.\nstdout:%s\nstderr:%s",
                        commandResult.getStatus(),
                        commandResult.getStdout(),
                        commandResult.getStderr());
                recoverDeviceFromBootloader();
            } else {
                setDeviceState(TestDeviceState.FASTBOOT);
            }
        } else {
            if (!mStateMonitor.waitForDeviceBootloader(mOptions.getFastbootTimeout())) {
                recoverDeviceFromBootloader();
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean waitForDeviceInSideload(long waitTime) {
        return mStateMonitor.waitForDeviceInSideload(waitTime);
    }

    /**
     * Small helper function to throw an NPE if the passed arg is null.  This should be used when
     * some value will be stored and used later, in which case it'll avoid hard-to-trace
     * asynchronous NullPointerExceptions by throwing the exception synchronously.  This is not
     * intended to be used where the NPE would be thrown synchronously -- just let the jvm take care
     * of it in that case.
     */
    private void throwIfNull(Object obj) {
        if (obj == null) {
            throw new NullPointerException();
        }
    }

    /** Retrieve this device's recovery mechanism. */
    @VisibleForTesting
    IDeviceRecovery getRecovery() {
        return mRecovery;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRecovery(IDeviceRecovery recovery) {
        throwIfNull(recovery);
        mRecovery = recovery;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRecoveryMode(RecoveryMode mode) {
        throwIfNull(mRecoveryMode);
        mRecoveryMode = mode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RecoveryMode getRecoveryMode() {
        return mRecoveryMode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFastbootEnabled(boolean fastbootEnabled) {
        mFastbootEnabled = fastbootEnabled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isFastbootEnabled() {
        return mFastbootEnabled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFastbootPath(String fastbootPath) {
        mFastbootPath = fastbootPath;
        // ensure the device and its associated recovery use the same fastboot version.
        mRecovery.setFastbootPath(fastbootPath);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFastbootPath() {
        return mFastbootPath;
    }

    /** {@inheritDoc} */
    @Override
    public String getFastbootVersion() {
        try {
            CommandResult res = executeFastbootCommand("--version");
            return res.getStdout().trim();
        } catch (DeviceNotAvailableException e) {
            // Ignored for host side request
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public String getFastbootSerialNumber() {
        if (mFastbootSerialNumber != null) {
            return mFastbootSerialNumber;
        }

        // Only devices which use TCP adb have different fastboot serial number because IPv6
        // link-local address will be used in fastboot mode.
        if (!isAdbTcp()) {
            mFastbootSerialNumber = getSerialNumber();
            CLog.i(
                    "Device %s's fastboot serial number is %s",
                    getSerialNumber(), mFastbootSerialNumber);
            return mFastbootSerialNumber;
        }

        mFastbootSerialNumber = getSerialNumber();
        byte[] macEui48Bytes;

        try {
            boolean adbRoot = isAdbRoot();
            if (!adbRoot) {
                enableAdbRoot();
            }
            macEui48Bytes = getEUI48MacAddressInBytes(ETHERNET_MAC_ADDRESS_COMMAND);
            if (!adbRoot) {
                disableAdbRoot();
            }
        } catch (DeviceNotAvailableException e) {
            CLog.e("Device %s isn't available when get fastboot serial number", getSerialNumber());
            CLog.e(e);
            return getSerialNumber();
        }

        String net_interface = getHostOptions().getNetworkInterface();
        if (net_interface == null || macEui48Bytes == null) {
            CLog.i(
                    "Device %s's fastboot serial number is %s",
                    getSerialNumber(), mFastbootSerialNumber);
            return mFastbootSerialNumber;
        }

        // Create a link-local Inet6Address from the MAC address. The EUI-48 MAC address
        // is converted to an EUI-64 MAC address per RFC 4291. The resulting EUI-64 is
        // used to construct a link-local IPv6 address per RFC 4862.
        byte[] addr = new byte[16];
        addr[0] = (byte) 0xfe;
        addr[1] = (byte) 0x80;
        addr[8] = (byte) (macEui48Bytes[0] ^ (byte) 0x02); // flip the link-local bit
        addr[9] = macEui48Bytes[1];
        addr[10] = macEui48Bytes[2];
        addr[11] = (byte) 0xff;
        addr[12] = (byte) 0xfe;
        addr[13] = macEui48Bytes[3];
        addr[14] = macEui48Bytes[4];
        addr[15] = macEui48Bytes[5];

        try {
            String host_addr = Inet6Address.getByAddress(null, addr, 0).getHostAddress();
            mFastbootSerialNumber = "tcp:" + host_addr.split("%")[0] + "%" + net_interface;
        } catch (UnknownHostException e) {
            CLog.w("Failed to get %s's IPv6 link-local address", getSerialNumber());
            CLog.w(e);
        }

        CLog.i(
                "Device %s's fastboot serial number is %s",
                getSerialNumber(), mFastbootSerialNumber);
        return mFastbootSerialNumber;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDeviceState(final TestDeviceState deviceState) {
        if (!deviceState.equals(getDeviceState())) {
            // disable state changes while fastboot lock is held, because issuing fastboot command
            // will disrupt state
            if (isStateBootloaderOrFastbootd() && mFastbootLock.isLocked()) {
                return;
            }
            mState = deviceState;
            if (!(getIDevice() instanceof StubDevice)) {
                CLog.logAndDisplay(
                        LogLevel.DEBUG,
                        "Device %s state is now %s",
                        getSerialNumber(),
                        deviceState);
            }
            mStateMonitor.setState(deviceState);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestDeviceState getDeviceState() {
        return mState;
    }

    @Override
    public boolean isAdbTcp() {
        return mStateMonitor.isAdbTcp();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String switchToAdbTcp() throws DeviceNotAvailableException {
        String ipAddress = getIpAddress();
        if (ipAddress == null) {
            CLog.e("connectToTcp failed: Device %s doesn't have an IP", getSerialNumber());
            return null;
        }
        String port = "5555";
        executeAdbCommand("tcpip", port);
        // TODO: analyze result? wait for device offline?
        return String.format("%s:%s", ipAddress, port);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean switchToAdbUsb() throws DeviceNotAvailableException {
        executeAdbCommand("usb");
        // TODO: analyze result? wait for device offline?
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEmulatorProcess(Process p) {
        mEmulatorProcess = p;

    }

    /**
     * For emulator set {@link SizeLimitedOutputStream} to log output
     * @param output to log the output
     */
    public void setEmulatorOutputStream(SizeLimitedOutputStream output) {
        mEmulatorOutput = output;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopEmulatorOutput() {
        if (mEmulatorOutput != null) {
            mEmulatorOutput.delete();
            mEmulatorOutput = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStreamSource getEmulatorOutput() {
        if (getIDevice().isEmulator()) {
            if (mEmulatorOutput == null) {
                CLog.w("Emulator output for %s was not captured in background",
                        getSerialNumber());
            } else {
                try {
                    return new SnapshotInputStreamSource(
                            "getEmulatorOutput", mEmulatorOutput.getData());
                } catch (IOException e) {
                    CLog.e("Failed to get %s data.", getSerialNumber());
                    CLog.e(e);
                }
            }
        }
        return new ByteArrayInputStreamSource(new byte[0]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Process getEmulatorProcess() {
        return mEmulatorProcess;
    }

    /**
     * @return <code>true</code> if adb root should be enabled on device
     */
    public boolean isEnableAdbRoot() {
        return mOptions.isEnableAdbRoot();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getInstalledPackageNames() throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for Package's feature");
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPackageInstalled(String packageName) throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for Package's feature");
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPackageInstalled(String packageName, String userId)
            throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for Package's feature");
    }

    /** {@inheritDoc} */
    @Override
    public Set<ApexInfo> getActiveApexes() throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for Package's feature");
    }

    /** {@inheritDoc} */
    @Override
    public List<PackageInfo> getAppPackageInfos() throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for Package's feature");
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> getMainlineModuleInfo() throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for Package's feature");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getUninstallablePackageNames() throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for Package's feature");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PackageInfo getAppPackageInfo(String packageName) throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for Package's feature");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestDeviceOptions getOptions() {
        return mOptions;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getApiLevel() throws DeviceNotAvailableException {
        int apiLevel = UNKNOWN_API_LEVEL;
        try {
            String prop = getProperty(DeviceProperties.SDK_VERSION);
            apiLevel = Integer.parseInt(prop);
        } catch (NumberFormatException nfe) {
            CLog.w(
                    "Unable to get API level from "
                            + DeviceProperties.SDK_VERSION
                            + ", falling back to UNKNOWN.",
                    nfe);
            // ignore, return unknown instead
        }
        return apiLevel;
    }

    /** {@inheritDoc} */
    @Override
    public boolean checkApiLevelAgainstNextRelease(int strictMinLevel)
            throws DeviceNotAvailableException {
        int apiLevel = getApiLevel();
        if (apiLevel > strictMinLevel) {
            return true;
        }
        String codeName = getPropertyWithRecovery(DeviceProperties.BUILD_CODENAME, true);
        if (codeName == null) {
            throw new DeviceRuntimeException(
                    String.format(
                            "Failed to query property '%s'. device returned null.",
                            DeviceProperties.BUILD_CODENAME),
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
        }
        codeName = codeName.trim();
        // CUR_DEVELOPMENT_VERSION is the code used by Android for a pre-finalized SDK
        if (strictMinLevel == CUR_DEVELOPMENT_VERSION && !"REL".equals(codeName)) {
            return true;
        }
        apiLevel = apiLevel + ("REL".equals(codeName) ? 0 : 1);
        if (strictMinLevel > apiLevel) {
            return false;
        }
        return true;
    }

    protected int getApiLevelSafe() {
        try {
            return getApiLevel();
        } catch (DeviceNotAvailableException e) {
            CLog.e(e);
            return UNKNOWN_API_LEVEL;
        }
    }

    /** {@inheritDoc} */
    @Override
    public int getLaunchApiLevel() throws DeviceNotAvailableException {
        try {
            String prop = getProperty(DeviceProperties.FIRST_API_LEVEL);
            return Integer.parseInt(prop);
        } catch (NumberFormatException nfe) {
            CLog.w(
                    "Unable to get first launch API level from "
                            + DeviceProperties.FIRST_API_LEVEL
                            + ", falling back to getApiLevel().",
                    nfe);
        }
        return getApiLevel();
    }

    @Override
    public IDeviceStateMonitor getMonitor() {
        return mStateMonitor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean waitForDeviceShell(long waitTime) {
        return mStateMonitor.waitForDeviceShell(waitTime);
    }

    @Override
    public DeviceAllocationState getAllocationState() {
        return mAllocationState;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Process the DeviceEvent, which may or may not transition this device to a new allocation
     * state.
     * </p>
     */
    @Override
    public DeviceEventResponse handleAllocationEvent(DeviceEvent event) {

        // keep track of whether state has actually changed or not
        boolean stateChanged = false;
        DeviceAllocationState newState;
        DeviceAllocationState oldState = mAllocationState;
        mAllocationStateLock.lock();
        try {
            // update oldState here, just in case in changed before we got lock
            oldState = mAllocationState;
            newState = mAllocationState.handleDeviceEvent(event);
            if (oldState != newState) {
                // state has changed! record this fact, and store the new state
                stateChanged = true;
                mAllocationState = newState;
            }
        } finally {
            mAllocationStateLock.unlock();
        }
        if (stateChanged && mAllocationMonitor != null) {
            // state has changed! Lets inform the allocation monitor listener
            mAllocationMonitor.notifyDeviceStateChange(getSerialNumber(), oldState, newState);
        }
        return new DeviceEventResponse(newState, stateChanged);
    }

    /** {@inheritDoc} */
    @Override
    public long getDeviceTimeOffset(Date date) throws DeviceNotAvailableException {
        long deviceTime = getDeviceDate();

        if (date == null) {
            date = new Date();
        }

        long offset = date.getTime() - deviceTime;
        CLog.d("Time offset = %d ms", offset);
        return offset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDate(Date date) throws DeviceNotAvailableException {
        if (date == null) {
            date = new Date();
        }
        long timeOffset = getDeviceTimeOffset(date);
        // no need to set date
        if (Math.abs(timeOffset) <= MAX_HOST_DEVICE_TIME_OFFSET) {
            return;
        }
        String dateString = null;
        if (getApiLevel() < 23) {
            // set date in epoch format
            dateString = Long.toString(date.getTime() / 1000); //ms to s
        } else {
            // set date with POSIX like params
            SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                    "MMddHHmmyyyy.ss");
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            dateString = sdf.format(date);
        }
        // best effort, no verification
        // Use TZ= to default to UTC timezone (b/128353510 for background)
        executeShellCommand("TZ=UTC date -u " + dateString);
    }

    /** {@inheritDoc} */
    @Override
    public long getDeviceDate() throws DeviceNotAvailableException {
        String deviceTimeString = executeShellCommand("date +%s");
        Long deviceTime = null;
        try {
            deviceTime = Long.valueOf(deviceTimeString.trim());
        } catch (NumberFormatException nfe) {
            CLog.i("Invalid device time: \"%s\", ignored.", nfe);
            return 0;
        }
        // Convert from seconds to milliseconds
        return deviceTime * 1000L;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean waitForBootComplete(long timeOut) throws DeviceNotAvailableException {
        return mStateMonitor.waitForBootComplete(timeOut);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ArrayList<Integer> listUsers() throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /** {@inheritDoc} */
    @Override
    public Map<Integer, UserInfo> getUserInfos() throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaxNumberOfUsersSupported() throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    @Override
    public int getMaxNumberOfRunningUsersSupported() throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isMultiUserSupported() throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    @Override
    public boolean isHeadlessSystemUserMode() throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    @Override
    public boolean canSwitchToHeadlessSystemUser() throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    @Override
    public boolean isMainUserPermanentAdmin() throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /** {@inheritDoc} */
    @Override
    public int createUserNoThrow(String name) throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int createUser(String name) throws DeviceNotAvailableException, IllegalStateException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int createUser(String name, boolean guest, boolean ephemeral)
            throws DeviceNotAvailableException, IllegalStateException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /** {@inheritDoc} */
    @Override
    public int createUser(String name, boolean guest, boolean ephemeral, boolean forTesting)
            throws DeviceNotAvailableException, IllegalStateException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeUser(int userId) throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean startUser(int userId) throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /** {@inheritDoc} */
    @Override
    public boolean startUser(int userId, boolean waitFlag) throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    @Override
    public boolean startVisibleBackgroundUser(int userId, int displayId, boolean waitFlag)
            throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean stopUser(int userId) throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean stopUser(int userId, boolean waitFlag, boolean forceFlag)
            throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    @Override
    public boolean isVisibleBackgroundUsersSupported() throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    @Override
    public boolean isVisibleBackgroundUsersOnDefaultDisplaySupported()
            throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remountSystemWritable() throws DeviceNotAvailableException {
        String verity = getProperty("partition.system.verified");
        // have the property set (regardless state) implies verity is enabled, so we send adb
        // command to disable verity
        if (verity != null && !verity.isEmpty()) {
            executeAdbCommand("disable-verity");
            mPropertiesCache.invalidate("partition.system.verified");
            reboot();
        }
        enableAdbRoot();
        executeAdbCommand("remount");
        waitForDeviceAvailable();
    }

    /** {@inheritDoc} */
    @Override
    public void remountVendorWritable() throws DeviceNotAvailableException {
        String verity = getProperty("partition.vendor.verified");
        // have the property set (regardless state) implies verity is enabled, so we send adb
        // command to disable verity
        if (verity != null && !verity.isEmpty()) {
            executeAdbCommand("disable-verity");
            mPropertiesCache.invalidate("partition.vendor.verified");
            reboot();
        }
        enableAdbRoot();
        executeAdbCommand("remount");
        waitForDeviceAvailable();
    }

    /** {@inheritDoc} */
    @Override
    public void remountSystemReadOnly() throws DeviceNotAvailableException {
        String verity = getProperty("partition.system.verified");
        // have the property set (regardless state) implies verity is enabled, so we send adb
        // command to disable verity
        if (verity == null || verity.isEmpty()) {
            executeAdbCommand("enable-verity");
            reboot();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void remountVendorReadOnly() throws DeviceNotAvailableException {
        String verity = getProperty("partition.vendor.verified");
        // have the property set (regardless state) implies verity is enabled, so we send adb
        // command to disable verity
        if (verity == null || verity.isEmpty()) {
            executeAdbCommand("enable-verity");
            reboot();
        }
    }

    /** {@inheritDoc} */
    @Override
    public Integer getPrimaryUserId() throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /** {@inheritDoc} */
    @Override
    public Integer getMainUserId() throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /** Used internally to fallback to non-user logic */
    private int getCurrentUserCompatible() throws DeviceNotAvailableException {
        try {
            return getCurrentUser();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCurrentUser() throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    @Override
    public boolean isUserVisible(int userId) throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    @Override
    public boolean isUserVisibleOnDisplay(int userId, int displayId)
            throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /** {@inheritDoc} */
    @Override
    public boolean isUserSecondary(int userId) throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int getUserFlags(int userId) throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getUserSerialNumber(int userId) throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean switchUser(int userId) throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean switchUser(int userId, long timeout) throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isUserRunning(int userId) throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasFeature(String feature) throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support pm's features.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSetting(String namespace, String key)
            throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for setting's feature.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSetting(int userId, String namespace, String key)
            throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for setting's feature.");
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, String> getAllSettings(String namespace) throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for setting's feature.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSetting(String namespace, String key, String value)
            throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for setting's feature.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSetting(int userId, String namespace, String key, String value)
            throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for setting's feature.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildSigningKeys() throws DeviceNotAvailableException {
        String buildTags = getProperty(DeviceProperties.BUILD_TAGS);
        if (buildTags != null) {
            String[] tags = buildTags.split(",");
            for (String tag : tags) {
                Matcher m = KEYS_PATTERN.matcher(tag);
                if (m.matches()) {
                    return tag;
                }
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAndroidId(int userId) throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Integer, String> getAndroidIds() throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /** {@inheritDoc} */
    @Override
    public boolean setDeviceOwner(String componentName, int userId)
            throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /** {@inheritDoc} */
    @Override
    public boolean removeAdmin(String componentName, int userId)
            throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /** {@inheritDoc} */
    @Override
    public void removeOwners() throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disableKeyguard() throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for Window Manager's features");
    }

    /** {@inheritDoc} */
    @Override
    public String getDeviceClass() {
        IDevice device = getIDevice();
        if (device == null) {
            CLog.w("No IDevice instance, cannot determine device class.");
            return "";
        }
        return device.getClass().getSimpleName();
    }

    /** {@inheritDoc} */
    @Override
    public void preInvocationSetup(IBuildInfo info, MultiMap<String, String> attributes)
            throws TargetSetupError, DeviceNotAvailableException {
        // Default implementation
        mContentProvider = null;
        mShouldSkipContentProviderSetup = false;
        try {
            mExecuteShellCommandLogs =
                    FileUtil.createTempFile("TestDevice_ExecuteShellCommands", ".txt");
        } catch (IOException e) {
            throw new TargetSetupError(
                    "Failed to create the executeShellCommand log file.",
                    e,
                    getDeviceDescriptor(),
                    InfraErrorIdentifier.FAIL_TO_CREATE_FILE);
        }
        initializeConnection(info, attributes);
    }

    protected void initializeConnection(IBuildInfo info, MultiMap<String, String> attributes)
            throws DeviceNotAvailableException, TargetSetupError {
        try (CloseableTraceScope ignored = new CloseableTraceScope("initializeConnection")) {
            ConnectionBuilder builder =
                    new ConnectionBuilder(getRunUtil(), this, info, getLogger());
            if (attributes != null) {
                builder.addAttributes(attributes);
            }
            addExtraConnectionBuilderArgs(builder);
            mConnection = DefaultConnection.createConnection(builder);
            CLog.d("Using connection: %s (%s)", mConnection, getIDevice());
            mConnection.initializeConnection();
        }
    }

    protected void addExtraConnectionBuilderArgs(ConnectionBuilder builder) {
        if (mConnectionAvd != null) {
            builder.setExistingAvdInfo(mConnectionAvd);
        }
    }

    public final void setConnectionAvdInfo(GceAvdInfo avdInfo) {
        mConnectionAvd = avdInfo;
    }

    /** {@inheritDoc} */
    @Override
    public void postInvocationTearDown(Throwable exception) {
        invalidatePropertyCache();
        mConfiguration = null;
        mIsEncryptionSupported = null;
        FileUtil.deleteFile(mExecuteShellCommandLogs);
        mExecuteShellCommandLogs = null;
        FileUtil.recursiveDelete(mUnpackedFastbootDir);
        getConnection().tearDownConnection();
        mConnectionAvd = null;
        mDeviceActionReceivers.clear();
        // Default implementation
        if (getIDevice() instanceof StubDevice) {
            return;
        }
        // Reset the Content Provider bit.
        mShouldSkipContentProviderSetup = false;
        try {
            // If we never installed it, don't even bother checking for it during tear down.
            if (mContentProvider == null) {
                return;
            }
            if (exception instanceof DeviceNotAvailableException) {
                CLog.e(
                        "Skip Tradefed Content Provider teardown due to"
                                + " DeviceNotAvailableException.");
                return;
            }
            if (TestDeviceState.ONLINE.equals(getDeviceState())) {
                mContentProvider.tearDown();
            }
        } catch (DeviceNotAvailableException e) {
            CLog.e(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isHeadless() throws DeviceNotAvailableException {
        if (getProperty(DeviceProperties.BUILD_HEADLESS) != null) {
            return true;
        }
        return false;
    }

    protected void checkApiLevelAgainst(String feature, int strictMinLevel) {
        try {
            if (getApiLevel() < strictMinLevel){
                throw new HarnessRuntimeException(
                        String.format(
                                "%s not supported on %s. " + "Must be API %d.",
                                feature, getSerialNumber(), strictMinLevel),
                        DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
            }
        } catch (DeviceNotAvailableException e) {
            throw new HarnessRuntimeException(
                    "Device became unavailable while checking API level",
                    e,
                    DeviceErrorIdentifier.DEVICE_UNAVAILABLE);
        }
    }

    @Override
    public DeviceDescriptor getCachedDeviceDescriptor() {
        return getCachedDeviceDescriptor(false);
    }

    /** {@inheritDoc} */
    @Override
    public DeviceDescriptor getCachedDeviceDescriptor(boolean shortDescriptor) {
        synchronized (mCacheLock) {
            if (DeviceAllocationState.Allocated.equals(getAllocationState())) {
                if (mCachedDeviceDescriptor == null) {
                    // Create the cache the very first time when it's allocated.
                    mCachedDeviceDescriptor = getDeviceDescriptor(false);
                    return mCachedDeviceDescriptor;
                }
                return mCachedDeviceDescriptor;
            }
            // If device is not allocated, just return current information
            mCachedDeviceDescriptor = null;
            return getDeviceDescriptor(shortDescriptor);
        }
    }

    @Override
    public DeviceDescriptor getDeviceDescriptor() {
        return getDeviceDescriptor(false);
    }

    /** {@inheritDoc} */
    @Override
    public DeviceDescriptor getDeviceDescriptor(boolean shortDescriptor) {
        IDeviceSelection selector = new DeviceSelectionOptions();
        IDevice idevice = getIDevice();
        try {
            boolean isTemporary = false;
            if (idevice instanceof NullDevice) {
                isTemporary = ((NullDevice) idevice).isTemporary();
            }
            if (shortDescriptor) {
                // Return only info that do not require device inspection
                return new DeviceDescriptor(
                        idevice.getSerialNumber(),
                        null,
                        idevice instanceof StubDevice,
                        idevice.getState(),
                        getAllocationState(),
                        getDeviceState(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        getDeviceClass(),
                        null,
                        null,
                        null,
                        isTemporary,
                        null,
                        null,
                        idevice);
            }
            // All the operations to create the descriptor need to be safe (should not trigger any
            // device side effects like recovery)
            String sdkVersion = null;
            String buildAlias = null;
            String hardwareRev = null;
            if (TestDeviceState.ONLINE.equals(getDeviceState())) {
                sdkVersion = getPropertyWithRecovery(DeviceProperties.SDK_VERSION, false);
                buildAlias = getPropertyWithRecovery(DeviceProperties.BUILD_ALIAS, false);
                hardwareRev = getPropertyWithRecovery(DeviceProperties.HARDWARE_REVISION, false);
            }
            return new DeviceDescriptor(
                    idevice.getSerialNumber(),
                    null,
                    idevice instanceof StubDevice,
                    idevice.getState(),
                    getAllocationState(),
                    getDeviceState(),
                    getDisplayString(selector.getDeviceProductType(idevice)),
                    getDisplayString(selector.getDeviceProductVariant(idevice)),
                    getDisplayString(sdkVersion),
                    getDisplayString(buildAlias),
                    getDisplayString(hardwareRev),
                    getDisplayString(getBattery()),
                    getDeviceClass(),
                    getDisplayString(getMacAddress()),
                    getDisplayString(getSimState()),
                    getDisplayString(getSimOperator()),
                    isTemporary,
                    null,
                    null,
                    idevice);
        } catch (RuntimeException|DeviceNotAvailableException e) {
            CLog.e("Exception while building device '%s' description:", getSerialNumber());
            CLog.e(e);
        }
        return null;
    }

    /**
     * Return the displayable string for given object
     */
    private String getDisplayString(Object o) {
        return o == null ? "unknown" : o.toString();
    }

    /** {@inheritDoc} */
    @Override
    public ProcessInfo getProcessByName(String processName) throws DeviceNotAvailableException {
        String pidString = getProcessPid(processName);
        if (pidString == null) {
            return null;
        }
        long startTime = getProcessStartTimeByPid(pidString);
        if (startTime == -1L) {
            return null;
        }
        return new ProcessInfo(
                getProcessUserByPid(pidString),
                Integer.parseInt(pidString),
                processName,
                startTime);
    }

    /** Return the process start time since epoch for the given pid string */
    private long getProcessStartTimeByPid(String pidString) throws DeviceNotAvailableException {
        String output = executeShellCommand(String.format("ps -p %s -o stime=", pidString));
        if (output != null && !output.trim().isEmpty()) {
            output = output.trim();

            String dateInSeconds;

            // On API 28 and lower, there is a bug in toybox that prevents date from parsing
            // timestamps containing a space, e.g. -D"%Y-%m-%d %H:%M:%S" cannot be used to parse
            // the stime:19 output from ps. Instead, we'll reconstruct the timestamp.
            if (getApiLevel() <= 28) {
                dateInSeconds =
                        executeShellCommand(
                                "date -d \"$(date +%Y:%m:%d):"
                                        + output
                                        + "\" +%s -D \"%Y:%m:%d:%H:%M:%S\"");
            } else {
                dateInSeconds = executeShellCommand("date -d\"" + output + "\" +%s");
            }
            if (Strings.isNullOrEmpty(dateInSeconds)) {
                return -1L;
            }
            try {
                return Long.parseLong(dateInSeconds.trim());
            } catch (NumberFormatException e) {
                CLog.e("Failed to parse the start time for process:");
                CLog.e(e);
                return -1L;
            }
        }
        return -1L;
    }

    /** Return the process user for the given pid string */
    private String getProcessUserByPid(String pidString) throws DeviceNotAvailableException {
        String output = executeShellCommand("stat -c%U /proc/" + pidString);
        if (output != null && !output.trim().isEmpty()) {
            try {
                return output.trim();
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Map<Long, String> getBootHistory() throws DeviceNotAvailableException {
        String output = getProperty(DeviceProperties.BOOT_REASON_HISTORY);
        /* Sample output:
        kernel_panic,1556587278
        reboot,,1556238008
        reboot,,1556237796
        reboot,,1556237725
        */
        Map<Long, String> bootHistory = new LinkedHashMap<Long, String>();
        if (Strings.isNullOrEmpty(output)) {
            return bootHistory;
        }
        for (String line : output.split("\\n")) {
            String infoStr[] = line.split(",");
            String startStr = infoStr[infoStr.length - 1];
            try {
                long startTime = Long.parseLong(startStr.trim());
                bootHistory.put(startTime, infoStr[0].trim());
            } catch (NumberFormatException e) {
                CLog.e("Fail to parse boot time from line %s", line);
            }
        }
        return bootHistory;
    }

    /** {@inheritDoc} */
    @Override
    public Map<Long, String> getBootHistorySince(long utcEpochTime, TimeUnit timeUnit)
            throws DeviceNotAvailableException {
        long utcEpochTimeSec = TimeUnit.SECONDS.convert(utcEpochTime, timeUnit);
        Map<Long, String> bootHistory = new LinkedHashMap<Long, String>();
        for (Map.Entry<Long, String> entry : getBootHistory().entrySet()) {
            if (entry.getKey() >= utcEpochTimeSec) {
                bootHistory.put(entry.getKey(), entry.getValue());
            }
        }
        return bootHistory;
    }

    private boolean hasNormalRebootSince(long utcEpochTime, TimeUnit timeUnit)
            throws DeviceNotAvailableException {
        Map<Long, String> bootHistory = getBootHistorySince(utcEpochTime, timeUnit);
        if (bootHistory.isEmpty()) {
            CLog.w("There is no reboot history since %s", utcEpochTime);
            return false;
        }

        CLog.i(
                "There are new boot history since %d. NewBootHistory = %s",
                utcEpochTime, bootHistory);
        // Check if there is reboot reason other than "reboot".
        // Raise RuntimeException if there is abnormal reboot.
        for (Map.Entry<Long, String> entry : bootHistory.entrySet()) {
            if (!"reboot".equals(entry.getValue())) {
                throw new HarnessRuntimeException(
                        String.format(
                                "Device %s has abnormal reboot reason %s at %d",
                                getSerialNumber(), entry.getValue(), entry.getKey()),
                        DeviceErrorIdentifier.UNEXPECTED_REBOOT);
            }
        }
        return true;
    }

    /**
     * Check current system process is restarted after last reboot
     *
     * @param systemServerProcess the system_server {@link ProcessInfo}
     * @return true if system_server process restarted after last reboot; false if not
     * @throws DeviceNotAvailableException
     */
    private boolean checkSystemProcessRestartedAfterLastReboot(ProcessInfo systemServerProcess)
            throws DeviceNotAvailableException {
        // If time gap from last reboot to current system_server process start time is more than
        // MAX_SYSTEM_SERVER_DELAY_AFTER_BOOT_UP seconds, we conclude the system_server restarted
        // after boot up.
        if (!hasNormalRebootSince(
                systemServerProcess.getStartTime() - MAX_SYSTEM_SERVER_DELAY_AFTER_BOOT_UP_SEC,
                TimeUnit.SECONDS)) {
            CLog.i(
                    "Device last reboot is more than %s seconds away from current system_server "
                            + "process start time. The system_server process restarted after "
                            + "last boot up",
                    MAX_SYSTEM_SERVER_DELAY_AFTER_BOOT_UP_SEC);
            return true;
        } else {
            // Current system_server start within MAX_SYSTEM_SERVER_DELAY_AFTER_BOOT_UP
            // seconds after device last boot up
            return false;
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean deviceSoftRestartedSince(long utcEpochTime, TimeUnit timeUnit)
            throws DeviceNotAvailableException {
        ProcessInfo currSystemServerProcess = getProcessByName("system_server");
        if (currSystemServerProcess == null) {
            CLog.i("The system_server process is not available on the device.");
            return true;
        }

        // The system_server process started at or before utcEpochTime, there is no soft-restart
        if (Math.abs(
                        currSystemServerProcess.getStartTime()
                                - TimeUnit.SECONDS.convert(utcEpochTime, timeUnit))
                <= 1) {
            return false;
        }

        // The system_server process restarted after device utcEpochTime in second.
        // Check if there is new reboot history, if no new reboot, device soft-restarted.
        // If there is no normal reboot, soft-restart is detected.
        if (!hasNormalRebootSince(utcEpochTime, timeUnit)) {
            return true;
        }

        // There is new reboot since utcEpochTime. Check if system_server restarted after boot up.
        return checkSystemProcessRestartedAfterLastReboot(currSystemServerProcess);
    }

    /** {@inheritDoc} */
    @Override
    public boolean deviceSoftRestarted(ProcessInfo prevSystemServerProcess)
            throws DeviceNotAvailableException {
        if (prevSystemServerProcess == null) {
            CLog.i("The given system_server process is null. Abort deviceSoftRestarted check.");
            return false;
        }
        ProcessInfo currSystemServerProcess = getProcessByName("system_server");
        if (currSystemServerProcess == null) {
            CLog.i("The system_server process is not available on the device.");
            return true;
        }

        // Compare the start time with a 1 seconds accuracy due to how the date is computed
        if (currSystemServerProcess.getPid() == prevSystemServerProcess.getPid()
                && Math.abs(
                                currSystemServerProcess.getStartTime()
                                        - prevSystemServerProcess.getStartTime())
                        <= 1) {
            return false;
        }

        CLog.v(
                "current system_server: %s; prev system_server: %s",
                currSystemServerProcess, prevSystemServerProcess);

        // The system_server process restarted.
        // Check boot history with previous system_server start time.
        // If there is no normal reboot, soft-restart is detected
        if (!hasNormalRebootSince(prevSystemServerProcess.getStartTime(), TimeUnit.SECONDS)) {
            return true;
        }

        // There is reboot since prevSystemServerProcess.getStartTime().
        // Check if system_server restarted after boot up.
        return checkSystemProcessRestartedAfterLastReboot(currSystemServerProcess);

    }

    /**
     * Validates that the given input is a valid MAC address
     *
     * @param address input to validate
     * @return true if the input is a valid MAC address
     */
    boolean isMacAddress(String address) {
        Pattern macPattern = Pattern.compile(MAC_ADDRESS_PATTERN);
        Matcher macMatcher = macPattern.matcher(address);
        return macMatcher.find();
    }

    /**
     * Query Mac address from the device
     *
     * @param command the query command
     * @return the MAC address of the device, null if it fails to query from the device
     */
    private String getMacAddress(String command) {
        if (getIDevice() instanceof StubDevice) {
            // Do not query MAC addresses from stub devices.
            return null;
        }
        if (!TestDeviceState.ONLINE.equals(mState)) {
            // Only query MAC addresses from online devices.
            return null;
        }
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        try {
            mIDevice.executeShellCommand(command, receiver);
        } catch (IOException | TimeoutException | AdbCommandRejectedException |
                ShellCommandUnresponsiveException e) {
            CLog.w(
                    "Failed to query MAC address for %s by '%s'",
                    mIDevice.getSerialNumber(), command);
            CLog.w(e);
        }
        String output = receiver.getOutput().trim();
        if (isMacAddress(output)) {
            return output;
        }
        CLog.d(
                "No valid MAC address queried from device %s by '%s'",
                mIDevice.getSerialNumber(), command);
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public String getMacAddress() {
        return getMacAddress(MAC_ADDRESS_COMMAND);
    }

    /**
     * Query EUI-48 MAC address from the device
     *
     * @param command the query command
     * @return the EUI-48 MAC address in long, 0 if it fails to query from the device
     * @throws IllegalArgumentException
     */
    long getEUI48MacAddressInLong(String command) {
        String addr = getMacAddress(command);
        if (addr == null) {
            return 0;
        }

        String[] parts = addr.split(":");
        if (parts.length != ETHER_ADDR_LEN) {
            throw new IllegalArgumentException(addr + " was not a valid MAC address");
        }
        long longAddr = 0;
        for (int i = 0; i < parts.length; i++) {
            int x = Integer.valueOf(parts[i], 16);
            if (x < 0 || 0xff < x) {
                throw new IllegalArgumentException(addr + "was not a valid MAC address");
            }
            longAddr = x + (longAddr << 8);
        }

        return longAddr;
    }

    /**
     * Query EUI-48 MAC address from the device
     *
     * @param command the query command
     * @return the EUI-48 MAC address in byte[], null if it fails to query from the device
     * @throws IllegalArgumentException
     */
    byte[] getEUI48MacAddressInBytes(String command) {
        long addr = getEUI48MacAddressInLong(command);
        if (addr == 0) {
            return null;
        }

        byte[] bytes = new byte[ETHER_ADDR_LEN];
        int index = ETHER_ADDR_LEN;
        while (index-- > 0) {
            bytes[index] = (byte) addr;
            addr = addr >> 8;
        }
        return bytes;
    }

    /** {@inheritDoc} */
    @Override
    public String getSimState() {
        if (getIDevice() instanceof StubDevice) {
            // Do not query SIM state from stub devices.
            return null;
        }
        if (!TestDeviceState.ONLINE.equals(mState)) {
            // Only query SIM state from online devices.
            return null;
        }
        try {
            return getPropertyWithRecovery(SIM_STATE_PROP, false);
        } catch (DeviceNotAvailableException dnae) {
            CLog.w("DeviceNotAvailableException while fetching SIM state");
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getSimOperator() {
        if (getIDevice() instanceof StubDevice) {
            // Do not query SIM operator from stub devices.
            return null;
        }
        if (!TestDeviceState.ONLINE.equals(mState)) {
            // Only query SIM operator from online devices.
            return null;
        }
        try {
            return getPropertyWithRecovery(SIM_OPERATOR_PROP, false);
        } catch (DeviceNotAvailableException dnae) {
            CLog.w("DeviceNotAvailableException while fetching SIM operator");
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public File dumpHeap(String process, String devicePath) throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("dumpHeap is not supported.");
    }

    /** {@inheritDoc} */
    @Override
    public String getProcessPid(String process) throws DeviceNotAvailableException {
        String output = executeShellCommand(String.format("pidof %s", process)).trim();
        if (checkValidPid(output)) {
            return output;
        }
        CLog.e("Failed to find a valid pid for process '%s'.", process);
        return null;
    }

    /** {@inheritDoc} */
    @Override
    @FormatMethod
    public void logOnDevice(String tag, LogLevel level, String format, Object... args) {
        String message = String.format(format, args);
        try {
            String levelLetter = logLevelToLogcatLevel(level);
            String command = String.format("log -t %s -p %s '%s'", tag, levelLetter, message);
            executeShellCommand(command);
        } catch (DeviceNotAvailableException e) {
            CLog.e("Device went not available when attempting to log '%s'", message);
            CLog.e(e);
        }
    }

    /** Convert the {@link LogLevel} to the letter used in log (see 'adb shell log --help'). */
    private String logLevelToLogcatLevel(LogLevel level) {
        switch (level) {
            case DEBUG:
                return "d";
            case ERROR:
                return "e";
            case INFO:
                return "i";
            case VERBOSE:
                return "v";
            case WARN:
                return "w";
            default:
                return "i";
        }
    }

    /** {@inheritDoc} */
    @Override
    public long getTotalMemory() {
        // "/proc/meminfo" always returns value in kilobytes.
        long totalMemory = 0;
        String output = null;
        try {
            output = executeShellCommand("cat /proc/meminfo | grep MemTotal");
        } catch (DeviceNotAvailableException e) {
            CLog.e(e);
            return -1;
        }
        if (output.isEmpty()) {
            return -1;
        }
        String[] results = output.split("\\s+");
        try {
            totalMemory = Long.parseLong(results[1].replaceAll("\\D+", ""));
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            CLog.e(e);
            return -1;
        }
        return totalMemory * 1024;
    }

    /** {@inheritDoc} */
    @Override
    public Integer getBattery() {
        if (getIDevice() instanceof StubDevice) {
            return null;
        }
        if (isStateBootloaderOrFastbootd()) {
            return null;
        }
        try {
            // Use default 5 minutes freshness
            Future<Integer> batteryFuture = getIDevice().getBattery();
            // Get cached value or wait up to 500ms for battery level query
            return batteryFuture.get(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException
                | ExecutionException
                | java.util.concurrent.TimeoutException e) {
            CLog.w(
                    "Failed to query battery level for %s: %s",
                    getIDevice().getSerialNumber(), e.toString());
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Set<Long> listDisplayIds() throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("dumpsys SurfaceFlinger is not supported.");
    }

    @Override
    public Set<Integer> listDisplayIdsForStartingVisibleBackgroundUsers()
            throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for user's feature.");
    }

    /** {@inheritDoc} */
    @Override
    public long getLastExpectedRebootTimeMillis() {
        return mLastTradefedRebootTime;
    }

    /** {@inheritDoc} */
    @Override
    public List<File> getTombstones() throws DeviceNotAvailableException {
        List<File> tombstones = new ArrayList<>();
        if (!isAdbRoot()) {
            CLog.w("Device was not root, cannot collect tombstones.");
            return tombstones;
        }
        for (String tombName : getChildren(TOMBSTONE_PATH)) {
            File tombFile = pullFile(TOMBSTONE_PATH + tombName);
            if (tombFile != null) {
                tombstones.add(tombFile);
            }
        }
        return tombstones;
    }

    @Override
    public Set<DeviceFoldableState> getFoldableStates() throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for foldable states.");
    }

    @Override
    public DeviceFoldableState getCurrentFoldableState() throws DeviceNotAvailableException {
        throw new UnsupportedOperationException("No support for foldable states.");
    }

    /** Validate that pid is an integer and not empty. */
    private boolean checkValidPid(String output) {
        if (output.isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(output);
        } catch (NumberFormatException e) {
            CLog.e(e);
            return false;
        }
        return true;
    }

    /** Gets the {@link IHostOptions} instance to use. */
    @VisibleForTesting
    IHostOptions getHostOptions() {
        return GlobalConfiguration.getInstance().getHostOptions();
    }

    /** Returns the {@link ContentProviderHandler} or null if not available. */
    @VisibleForTesting
    ContentProviderHandler getContentProvider() throws DeviceNotAvailableException {
        // If disabled at the device level, don't attempt any checks.
        if (!getOptions().shouldUseContentProvider()) {
            return null;
        }
        // Prevent usage of content provider before API 28 as it would not work well since content
        // tool is not working before P.
        if (getApiLevel() < 28) {
            return null;
        }
        if (mContentProvider == null) {
            mContentProvider = new ContentProviderHandler(this);
        }
        // Force the install if we saw an error with content provider installation.
        if (mContentProvider.contentProviderNotFound()) {
            mShouldSkipContentProviderSetup = false;
        }
        if (!mShouldSkipContentProviderSetup) {
            boolean res = mContentProvider.setUp();
            if (!res) {
                // TODO: once CP becomes a requirement, throw/fail the test if CP can't be found
                return null;
            }
            mShouldSkipContentProviderSetup = true;
        }
        return mContentProvider;
    }

    /** Reset the flag for content provider setup in order to trigger it again. */
    public void resetContentProviderSetup() {
        mShouldSkipContentProviderSetup = false;
    }

    /** The log that contains all the {@link #executeShellCommand(String)} logs. */
    public final File getExecuteShellCommandLog() {
        return mExecuteShellCommandLogs;
    }

    /** Executes a simple fastboot command and report the status of the command. */
    @VisibleForTesting
    protected CommandResult simpleFastbootCommand(final long timeout, String[] fullCmd)
            throws UnsupportedOperationException {
        return simpleFastbootCommand(timeout, new HashMap<>(), fullCmd);
    }

    /**
     * Executes a simple fastboot command with environment variables and report the status of the
     * command.
     */
    @VisibleForTesting
    protected CommandResult simpleFastbootCommand(
            final long timeout, Map<String, String> envVarMap, String[] fullCmd)
            throws UnsupportedOperationException {
        if (!mFastbootEnabled) {
            throw new UnsupportedOperationException(
                    String.format(
                            "Attempted to fastboot on device %s , but fastboot "
                                    + "is disabled. Aborting.",
                            getSerialNumber()));
        }
        IRunUtil runUtil;
        if (!envVarMap.isEmpty()) {
            runUtil = new RunUtil();
        } else {
            runUtil = getRunUtil();
        }
        for (Map.Entry<String, String> entry : envVarMap.entrySet()) {
            CLog.v(
                    String.format(
                            "Set environment variable %s to %s", entry.getKey(), entry.getValue()));
            runUtil.setEnvVariable(entry.getKey(), entry.getValue());
        }
        CommandResult result = new CommandResult(CommandStatus.EXCEPTION);
        // block state changes while executing a fastboot command, since
        // device will disappear from fastboot devices while command is being executed
        mFastbootLock.lock();
        try {
            result = runUtil.runTimedCmd(timeout, fullCmd);
        } finally {
            mFastbootLock.unlock();
        }
        return result;
    }

    /** The current connection associated with the device. */
    @Override
    public AbstractConnection getConnection() {
        if (mConnection == null) {
            mConnection =
                    DefaultConnection.createInopConnection(
                            new ConnectionBuilder(getRunUtil(), this, null, getLogger()));
        }
        return mConnection;
    }

    /** Check if debugfs is mounted. */
    @Override
    public boolean isDebugfsMounted() throws DeviceNotAvailableException {
        return CommandStatus.SUCCESS.equals(
                executeShellV2Command(CHECK_DEBUGFS_MNT_COMMAND).getStatus());
    }

    /** Mount debugfs. */
    @Override
    public void mountDebugfs() throws DeviceNotAvailableException {
        if (isDebugfsMounted()) {
            CLog.w("debugfs already mounted.");
            return;
        }

        CommandResult result = executeShellV2Command(MOUNT_DEBUGFS_COMMAND);
        if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
            CLog.e("Failed to mount debugfs. %s", result);
            throw new DeviceRuntimeException(
                    "'" + MOUNT_DEBUGFS_COMMAND + "' has failed: " + result,
                    DeviceErrorIdentifier.SHELL_COMMAND_ERROR);
        }
    }

    /** Unmount debugfs. */
    @Override
    public void unmountDebugfs() throws DeviceNotAvailableException {
        if (!isDebugfsMounted()) {
            CLog.w("debugfs not mounted to unmount.");
            return;
        }

        CommandResult result = executeShellV2Command(UNMOUNT_DEBUGFS_COMMAND);
        if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
            CLog.e("Failed to unmount debugfs. %s", result);
            throw new DeviceRuntimeException(
                    "'" + UNMOUNT_DEBUGFS_COMMAND + "' has failed: " + result,
                    DeviceErrorIdentifier.SHELL_COMMAND_ERROR);
        }
    }

    /**
     * Notifies all {@link IDeviceActionReceiver} about reboot start event.
     *
     * @throws DeviceNotAvailableException
     */
    protected void notifyRebootStarted() throws DeviceNotAvailableException {
        try (CloseableTraceScope ignored = new CloseableTraceScope("rebootStartedCallbacks")) {
            for (IDeviceActionReceiver dar : mDeviceActionReceivers) {
                try {
                    inRebootCallback = true;
                    dar.rebootStarted(this);
                } catch (DeviceNotAvailableException dnae) {
                    inRebootCallback = false;
                    throw dnae;
                } catch (Exception e) {
                    logDeviceActionException("notifyRebootStarted", e, true);
                } finally {
                    inRebootCallback = false;
                }
            }
        }
    }

    /**
     * Notifies all {@link IDeviceActionReceiver} about reboot end event.
     *
     * @throws DeviceNotAvailableException
     */
    protected void notifyRebootEnded() throws DeviceNotAvailableException {
        try (CloseableTraceScope ignored = new CloseableTraceScope("rebootEndedCallbacks")) {
            for (IDeviceActionReceiver dar : mDeviceActionReceivers) {
                try {
                    inRebootCallback = true;
                    dar.rebootEnded(this);
                } catch (DeviceNotAvailableException dnae) {
                    inRebootCallback = false;
                    throw dnae;
                } catch (Exception e) {
                    logDeviceActionException("notifyRebootEnded", e, true);
                } finally {
                    inRebootCallback = false;
                }
            }
        }
    }

    /**
     * Returns whether reboot callbacks is currently being executed or not. All public api's for
     * reboot should be disabled if true.
     */
    protected boolean isInRebootCallback() {
        return inRebootCallback;
    }

    @Override
    public void setTestLogger(ITestLogger testLogger) {
        mTestLogger = testLogger;
    }

    protected ITestLogger getLogger() {
        return mTestLogger;
    }

    /**
     * Marks the TestDevice as microdroid and sets its CID.
     *
     * @param process Process of the Microdroid VM.
     */
    protected void setMicrodroidProcess(Process process) {
        mMicrodroidProcess = process;
    }

    /**
     * @return Returns the Process of the Microdroid VM. If TestDevice is not a Microdroid, returns
     *     null.
     */
    public Process getMicrodroidProcess() {
        return mMicrodroidProcess;
    }

    protected void setTestDeviceOptions(Map<String, String> deviceOptions) {
        try {
            OptionSetter setter = new OptionSetter(this.getOptions());
            for (Map.Entry<String, String> optionsKeyValue : deviceOptions.entrySet()) {
                setter.setOptionValue(optionsKeyValue.getKey(), optionsKeyValue.getValue());
            }
        } catch (ConfigurationException e) {
            CLog.w(e);
        }
    }

    public void invalidatePropertyCache() {
        mPropertiesCache.invalidateAll();
    }
}
