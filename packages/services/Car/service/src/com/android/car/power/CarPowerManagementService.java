/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.car.power;

import static android.car.hardware.power.CarPowerManager.STATE_PRE_SHUTDOWN_PREPARE;
import static android.car.hardware.power.CarPowerManager.STATE_SHUTDOWN_PREPARE;
import static android.car.hardware.power.PowerComponentUtil.FIRST_POWER_COMPONENT;
import static android.car.hardware.power.PowerComponentUtil.LAST_POWER_COMPONENT;
import static android.net.ConnectivityManager.TETHERING_WIFI;

import static com.android.car.hal.PowerHalService.BOOTUP_REASON_SYSTEM_ENTER_GARAGE_MODE;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.automotive.powerpolicy.internal.ICarPowerPolicyDelegate;
import android.automotive.powerpolicy.internal.ICarPowerPolicyDelegateCallback;
import android.automotive.powerpolicy.internal.PowerPolicyFailureReason;
import android.automotive.powerpolicy.internal.PowerPolicyInitData;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.ICarResultReceiver;
import android.car.PlatformVersion;
import android.car.builtin.app.ActivityManagerHelper;
import android.car.builtin.os.BuildHelper;
import android.car.builtin.os.HandlerHelper;
import android.car.builtin.os.ServiceManagerHelper;
import android.car.builtin.util.EventLogHelper;
import android.car.builtin.util.Slogf;
import android.car.feature.FeatureFlags;
import android.car.feature.FeatureFlagsImpl;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.CarPowerPolicy;
import android.car.hardware.power.CarPowerPolicyFilter;
import android.car.hardware.power.ICarPower;
import android.car.hardware.power.ICarPowerPolicyListener;
import android.car.hardware.power.ICarPowerStateListener;
import android.car.hardware.power.PowerComponentUtil;
import android.car.remoteaccess.CarRemoteAccessManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.frameworks.automotive.powerpolicy.internal.ICarPowerPolicySystemNotification;
import android.frameworks.automotive.powerpolicy.internal.PolicyState;
import android.hardware.automotive.vehicle.VehicleApPowerStateReport;
import android.hardware.automotive.vehicle.VehicleApPowerStateReq;
import android.hardware.automotive.vehicle.VehicleApPowerStateShutdownParam;
import android.hardware.display.DisplayManager;
import android.net.TetheringManager;
import android.net.TetheringManager.TetheringRequest;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.SparseArray;
import android.util.proto.ProtoOutputStream;
import android.view.Display;

import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.CarOccupantZoneService;
import com.android.car.CarServiceBase;
import com.android.car.CarServiceUtils;
import com.android.car.CarStatsLogHelper;
import com.android.car.R;
import com.android.car.hal.PowerHalService;
import com.android.car.hal.PowerHalService.BootupReason;
import com.android.car.hal.PowerHalService.PowerState;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.DebugUtils;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.internal.util.Lists;
import com.android.car.power.CarPowerDumpProto.CpmsStateProto;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.user.CarUserNoticeService;
import com.android.car.user.CarUserService;
import com.android.car.user.UserHandleHelper;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Power Management service class for cars. Controls the power states and interacts with other
 * parts of the system to ensure its own state.
 */
public class CarPowerManagementService extends ICarPower.Stub implements
        CarServiceBase, PowerHalService.PowerEventListener {
    public static final String SILENT_MODE_FORCED_SILENT =
            SilentModeHandler.SILENT_MODE_FORCED_SILENT;
    public static final String SILENT_MODE_FORCED_NON_SILENT =
            SilentModeHandler.SILENT_MODE_FORCED_NON_SILENT;
    public static final String SILENT_MODE_NON_FORCED = SilentModeHandler.SILENT_MODE_NON_FORCED;

    public static final long INVALID_TIMEOUT = -1L;

    public static final int NO_WAKEUP_BY_TIMER = -1;

    static final String TAG = CarLog.tagFor(CarPowerManagementService.class);

    private static final String WIFI_STATE_FILENAME = "wifi_state";
    private static final String TETHERING_STATE_FILENAME = "tethering_state";
    private static final String COMPONENT_STATE_MODIFIED = "forcibly_disabled";
    private static final String COMPONENT_STATE_ORIGINAL = "original";
    // If Suspend to RAM fails, we retry with an exponential back-off:
    // The wait interval will be 10 msec, 20 msec, 40 msec, ...
    // Once the wait interval goes beyond 100 msec, it is fixed at 100 msec.
    private static final long INITIAL_SUSPEND_RETRY_INTERVAL_MS = 10;
    private static final long MAX_RETRY_INTERVAL_MS = 100;
    // Minimum and maximum wait duration before the system goes into Suspend to RAM.
    private static final long MIN_SUSPEND_WAIT_DURATION_MS = 0;
    private static final long MAX_SUSPEND_WAIT_DURATION_MS = 3 * 60 * 1000;

    private static final long CAR_POWER_POLICY_DAEMON_FIND_MARGINAL_TIME_MS = 300;
    private static final long CAR_POWER_POLICY_DAEMON_BIND_RETRY_INTERVAL_MS = 500;
    private static final int CAR_POWER_POLICY_DAEMON_BIND_MAX_RETRY = 3;
    // TODO(b/286303350): remove once power policy refactor complete, replace w/refactored version
    private static final String CAR_POWER_POLICY_DAEMON_INTERFACE =
            "android.frameworks.automotive.powerpolicy.internal.ICarPowerPolicySystemNotification/"
                    + "default";
    private static final String REFACTORED_CAR_POWER_POLICY_DAEMON_INTERFACE =
            "android.automotive.powerpolicy.internal.ICarPowerPolicyDelegate/default";

    // TODO:  Make this OEM configurable.
    private static final int SHUTDOWN_POLLING_INTERVAL_MS = 2000;
    private static final int SHUTDOWN_EXTEND_MAX_MS = 5000;

    // maxGarageModeRunningDurationInSecs should be equal or greater than this. 15 min for now.
    private static final int MIN_MAX_GARAGE_MODE_DURATION_MS = 15 * 60 * 1000;

    // in secs
    private static final String PROP_MAX_GARAGE_MODE_DURATION_OVERRIDE =
            "android.car.garagemodeduration";
    // Constants for action on finish
    private static final int ACTION_ON_FINISH_SHUTDOWN = 0;
    private static final int ACTION_ON_FINISH_DEEP_SLEEP = 1;
    private static final int ACTION_ON_FINISH_HIBERNATION = 2;

    // Default timeout for listener completion during shutdown.
    private static final int DEFAULT_COMPLETION_WAIT_TIMEOUT = 5_000;

    // Default timeout for power policy change requests
    private static final int DEFAULT_POWER_POLICY_REQUEST_TIMEOUT_MS = 5_000;

    private static final int INDEX_WAIT_FOR_VHAL = 0;
    private static final int INDEX_ON = 1;

    private final Object mLock = new Object();
    private final Object mSimulationWaitObject = new Object();

    private final Context mContext;
    private final PowerHalService mHal;
    private final SystemInterface mSystemInterface;
    private final HandlerThread mHandlerThread = CarServiceUtils.getHandlerThread(
            getClass().getSimpleName());
    private final PowerHandler mHandler = new PowerHandler(mHandlerThread.getLooper(), this);
    private final HandlerThread mBroadcastHandlerThread = CarServiceUtils.getHandlerThread(
            getClass().getSimpleName() + " broadcasts");
    private final Handler mBroadcastHandler = new Handler(mBroadcastHandlerThread.getLooper());
    // The listeners that complete simply by returning from onStateChanged()
    private final PowerManagerCallbackList<ICarPowerStateListener> mPowerManagerListeners =
            new PowerManagerCallbackList<>(
                    l -> CarPowerManagementService.this.doUnregisterListener(l));
    // The listeners that must indicate asynchronous completion by calling finished().
    private final PowerManagerCallbackList<ICarPowerStateListener>
            mPowerManagerListenersWithCompletion = new PowerManagerCallbackList<>(
                    l -> CarPowerManagementService.this.doUnregisterListener(l));
    private final AtomicInteger mPolicyRequestIdCounter = new AtomicInteger(0);
    // The internal listeners that must indicates asynchronous completion by calling
    // completeStateChangeHandling(). Note that they are not binder objects.
    @GuardedBy("mLock")
    private final ArrayList<ICarPowerStateListener> mInternalPowerListeners = new ArrayList<>();

    @GuardedBy("mLock")
    private final ArraySet<IBinder> mListenersWeAreWaitingFor = new ArraySet<>();
    @GuardedBy("mLock")
    private final LinkedList<CpmsState> mPendingPowerStates = new LinkedList<>();
    @GuardedBy("mLock")
    private final SparseArray<AsyncPolicyRequest> mRequestIdToPolicyRequest = new SparseArray<>();
    private final UserManager mUserManager;
    private final CarUserService mUserService;
    private final WifiManager mWifiManager;
    private final TetheringManager mTetheringManager;
    private final AtomicFile mWifiStateFile;
    private final AtomicFile mTetheringStateFile;
    private final boolean mWifiAdjustmentForSuspend;

    // This is a temp work-around to reduce user switching delay after wake-up.
    private final boolean mSwitchGuestUserBeforeSleep;

    // CPMS tries to enter Suspend to RAM within the duration specified at
    // mMaxSuspendWaitDurationMs. The default max duration is MAX_SUSPEND_WAIT_DRATION, and can be
    // overridden by setting config_maxSuspendWaitDuration in an overrlay resource.
    // The valid range is MIN_SUSPEND_WAIT_DRATION to MAX_SUSPEND_WAIT_DURATION.
    private final long mMaxSuspendWaitDurationMs;

    @GuardedBy("mSimulationWaitObject")
    private boolean mWakeFromSimulatedSleep;
    @GuardedBy("mSimulationWaitObject")
    private boolean mInSimulatedDeepSleepMode;
    @GuardedBy("mSimulationWaitObject")
    private int mResumeDelayFromSimulatedSuspendSec = NO_WAKEUP_BY_TIMER;
    @GuardedBy("mSimulationWaitObject")
    private boolean mFreeMemoryBeforeSuspend;

    @GuardedBy("mLock")
    private CpmsState mCurrentState;
    @GuardedBy("mLock")
    private long mShutdownStartTime;
    @GuardedBy("mLock")
    private long mLastSleepEntryTime;

    @GuardedBy("mLock")
    private int mNextWakeupSec;
    @GuardedBy("mLock")
    private int mActionOnFinish;
    @GuardedBy("mLock")
    private boolean mShutdownOnNextSuspend;
    @GuardedBy("mLock")
    private boolean mShouldResumeUserService;
    @GuardedBy("mLock")
    private int mShutdownPrepareTimeMs = MIN_MAX_GARAGE_MODE_DURATION_MS;
    @GuardedBy("mLock")
    private int mShutdownPollingIntervalMs = SHUTDOWN_POLLING_INTERVAL_MS;
    @GuardedBy("mLock")
    private boolean mRebootAfterGarageMode;
    @GuardedBy("mLock")
    private boolean mGarageModeShouldExitImmediately;
    // TODO(b/286303350): remove once power policy refactor complete
    @GuardedBy("mLock")
    private ICarPowerPolicySystemNotification mCarPowerPolicyDaemon;
    // TODO(b/286303350): rename "mCarPowerPolicyDaemon" once power policy refactor complete
    @GuardedBy("mLock")
    private ICarPowerPolicyDelegate mRefactoredCarPowerPolicyDaemon;
    @GuardedBy("mLock")
    private boolean mConnectionInProgress;
    // After ICarPowerPolicyDelegateCallback is set, mReadyForCallback is set to true;
    private AtomicBoolean mReadyForCallback = new AtomicBoolean(false);
    private BinderHandler mBinderHandler;
    // TODO(b/286303350): remove after policy refactor, since daemon will be source of truth
    @GuardedBy("mLock")
    private String mCurrentPowerPolicyId;
    // TODO(b/286303350): remove after policy refactor, since daemon will control power policy
    @GuardedBy("mLock")
    private String mPendingPowerPolicyId;
    @GuardedBy("mLock")
    private String mCurrentPowerPolicyGroupId;
    @GuardedBy("mLock")
    private boolean mIsPowerPolicyLocked;
    // TODO(b/286303350): remove after policy refactor, since daemon will control power policy
    @GuardedBy("mLock")
    private boolean mHasControlOverDaemon;
    @GuardedBy("mLock")
    private CarPowerPolicy mCurrentAccumulatedPowerPolicy = getInitialAccumulatedPowerPolicy();
    private AtomicBoolean mIsListenerWaitingCancelled = new AtomicBoolean(false);
    private final Semaphore mListenerCompletionSem = new Semaphore(/* permits= */ 0);
    @GuardedBy("mLock")
    @CarPowerManager.CarPowerState
    private int mStateForCompletion = CarPowerManager.STATE_INVALID;
    @GuardedBy("mLock")
    @CarRemoteAccessManager.NextPowerState
    private int mLastShutdownState = CarRemoteAccessManager.NEXT_POWER_STATE_OFF;

    @GuardedBy("mLock")
    @Nullable
    private ICarResultReceiver mFactoryResetCallback;
    @GuardedBy("mLock")
    private boolean mIsEmergencyShutdown;

    private final PowerManagerCallbackList<ICarPowerPolicyListener> mPowerPolicyListeners =
            new PowerManagerCallbackList<>(
                    l -> CarPowerManagementService.this.mPowerPolicyListeners.unregister(l));

    private final PowerComponentHandler mPowerComponentHandler;
    private final PolicyReader mPolicyReader = new PolicyReader();
    private final SilentModeHandler mSilentModeHandler;
    private final ScreenOffHandler mScreenOffHandler;

    // Allows for injecting feature flag values during testing
    private FeatureFlags mFeatureFlags = new FeatureFlagsImpl();

    @VisibleForTesting
    void readPowerPolicyFromXml(InputStream inputStream)
            throws IOException, PolicyReader.PolicyXmlException, XmlPullParserException {
        mPolicyReader.readPowerPolicyFromXml(inputStream);
        Integer[] customComponents =
                new Integer[mPolicyReader.getCustomComponents().values().size()];
        mPolicyReader.getCustomComponents().values().toArray(customComponents);
        mPowerComponentHandler.registerCustomComponents(customComponents);
    }

    interface ActionOnDeath<T extends IInterface> {
        void take(T listener);
    }

    private final class PowerManagerCallbackList<T extends IInterface> extends
            RemoteCallbackList<T> {
        private ActionOnDeath<T> mActionOnDeath;

        PowerManagerCallbackList(ActionOnDeath<T> action) {
            mActionOnDeath = action;
        }

        /**
         * Old version of {@link #onCallbackDied(E, Object)} that
         * does not provide a cookie.
         */
        @Override
        public void onCallbackDied(T listener) {
            Slogf.i(TAG, "binderDied %s", listener.asBinder());
            mActionOnDeath.take(listener);
        }
    }

    public CarPowerManagementService(Context context, PowerHalService powerHal,
            SystemInterface systemInterface, CarUserService carUserService,
            IInterface powerPolicyDaemon) {
        this(context, context.getResources(), powerHal, systemInterface,
                context.getSystemService(UserManager.class), carUserService, powerPolicyDaemon,
                new PowerComponentHandler(context, systemInterface), /* featureFlags= */ null,
                /* screenOffHandler= */ null, /* silentModeHwStatePath= */ null,
                /* silentModeKernelStatePath= */ null, /* bootReason= */ null);
    }

    @VisibleForTesting
    public CarPowerManagementService(Context context, Resources resources, PowerHalService powerHal,
            SystemInterface systemInterface, UserManager userManager, CarUserService carUserService,
            IInterface powerPolicyDaemon, PowerComponentHandler powerComponentHandler,
            @Nullable FeatureFlags featureFlags, @Nullable ScreenOffHandler screenOffHandler,
            @Nullable String silentModeHwStatePath, @Nullable String silentModeKernelStatePath,
            @Nullable String bootReason) {
        mContext = context;
        mHal = powerHal;
        mSystemInterface = systemInterface;
        mUserManager = userManager;
        mShutdownPrepareTimeMs = resources.getInteger(
                R.integer.maxGarageModeRunningDurationInSecs) * 1000;
        mSwitchGuestUserBeforeSleep = resources.getBoolean(
                R.bool.config_switchGuestUserBeforeGoingSleep);
        if (mShutdownPrepareTimeMs < MIN_MAX_GARAGE_MODE_DURATION_MS) {
            Slogf.w(TAG,
                    "maxGarageModeRunningDurationInSecs smaller than minimum required, "
                            + "resource:%d(ms) while should exceed:%d(ms), Ignore resource.",
                    mShutdownPrepareTimeMs, MIN_MAX_GARAGE_MODE_DURATION_MS);
            mShutdownPrepareTimeMs = MIN_MAX_GARAGE_MODE_DURATION_MS;
        }
        mUserService = carUserService;
        if (featureFlags != null) {
            mFeatureFlags = featureFlags;
        }
        if (mFeatureFlags.carPowerPolicyRefactoring()) {
            mRefactoredCarPowerPolicyDaemon = (ICarPowerPolicyDelegate) powerPolicyDaemon;
        } else {
            mCarPowerPolicyDaemon = (ICarPowerPolicySystemNotification) powerPolicyDaemon;
            if (powerPolicyDaemon != null) {
                // For testing purpose
                mHasControlOverDaemon = true;
            }
        }
        mWifiManager = context.getSystemService(WifiManager.class);
        mTetheringManager = mContext.getSystemService(TetheringManager.class);
        mWifiStateFile = new AtomicFile(
                new File(mSystemInterface.getSystemCarDir(), WIFI_STATE_FILENAME));
        mTetheringStateFile = new AtomicFile(
                new File(mSystemInterface.getSystemCarDir(), TETHERING_STATE_FILENAME));
        mWifiAdjustmentForSuspend = isWifiAdjustmentForSuspendConfig();
        mPowerComponentHandler = powerComponentHandler;
        mSilentModeHandler = new SilentModeHandler(this, mFeatureFlags, silentModeHwStatePath,
                silentModeKernelStatePath, bootReason);
        mMaxSuspendWaitDurationMs = Math.max(MIN_SUSPEND_WAIT_DURATION_MS,
                Math.min(getMaxSuspendWaitDurationConfig(), MAX_SUSPEND_WAIT_DURATION_MS));
        mScreenOffHandler = Objects.requireNonNullElseGet(screenOffHandler, () ->
                new ScreenOffHandler(mContext, mSystemInterface, mHandler.getLooper()));
    }

    /**
     * Overrides timers to keep testing time short.
     *
     * <p>Passing in {@code 0} resets the value to the default.
     */
    @VisibleForTesting
    public void setShutdownTimersForTest(int pollingIntervalMs, int shutdownTimeoutMs) {
        synchronized (mLock) {
            mShutdownPollingIntervalMs =
                    (pollingIntervalMs == 0) ? SHUTDOWN_POLLING_INTERVAL_MS : pollingIntervalMs;
            mShutdownPrepareTimeMs =
                    (shutdownTimeoutMs == 0) ? SHUTDOWN_EXTEND_MAX_MS : shutdownTimeoutMs;
        }
    }

    @VisibleForTesting
    protected HandlerThread getHandlerThread() {
        return mHandlerThread;
    }

    @Override
    public void init() {
        mPolicyReader.init(mFeatureFlags);
        mPowerComponentHandler.init(mPolicyReader.getCustomComponents());
        mHal.setListener(this);
        mSystemInterface.init(this, mUserService);
        mScreenOffHandler.init();
        if (mHal.isPowerStateSupported()) {
            // Initialize CPMS in WAIT_FOR_VHAL state
            onApPowerStateChange(CpmsState.WAIT_FOR_VHAL, CarPowerManager.STATE_WAIT_FOR_VHAL);
        } else {
            Slogf.w(TAG, "Vehicle hal does not support power state yet.");
            onApPowerStateChange(CpmsState.ON, CarPowerManager.STATE_ON);
        }
        mSystemInterface.startDisplayStateMonitoring();
        connectToPowerPolicyDaemon();
    }

    @Override
    public void release() {
        if (mBinderHandler != null) {
            mBinderHandler.unlinkToDeath();
        }
        mReadyForCallback.set(false);
        synchronized (mLock) {
            clearWaitingForCompletion(/*clearQueue=*/false);
            mCurrentState = null;
            if (mFeatureFlags.carPowerPolicyRefactoring()) {
                mRefactoredCarPowerPolicyDaemon = null;
            } else {
                mCarPowerPolicyDaemon = null;
            }
            mHandler.cancelAll();
            mListenersWeAreWaitingFor.clear();
        }
        mSystemInterface.stopDisplayStateMonitoring();
        mPowerManagerListeners.kill();
        mPowerPolicyListeners.kill();
        forEachDisplay(mContext, mSystemInterface::releaseAllWakeLocks);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        synchronized (mLock) {
            writer.println("*CarPowerManagementService*");
            writer.printf("mCurrentState: %s\n", mCurrentState);
            writer.printf("mShutdownStartTime: %d\n", mShutdownStartTime);
            writer.printf("mLastSleepEntryTime: %d\n", mLastSleepEntryTime);
            writer.printf("mNextWakeupSec: %d\n", mNextWakeupSec);
            writer.printf("mShutdownOnNextSuspend: %b\n", mShutdownOnNextSuspend);
            writer.printf("mActionOnFinish: %s\n", actionOnFinishToString(mActionOnFinish));
            writer.printf("mShutdownPollingIntervalMs: %d\n", mShutdownPollingIntervalMs);
            writer.printf("mShutdownPrepareTimeMs: %d\n", mShutdownPrepareTimeMs);
            writer.printf("mRebootAfterGarageMode: %b\n", mRebootAfterGarageMode);
            writer.printf("mSwitchGuestUserBeforeSleep: %b\n", mSwitchGuestUserBeforeSleep);
            writer.printf("mCurrentPowerPolicyId: %s\n", mCurrentPowerPolicyId);
            writer.printf("mPendingPowerPolicyId: %s\n", mPendingPowerPolicyId);
            writer.printf("mCurrentPowerPolicyGroupId: %s\n", mCurrentPowerPolicyGroupId);
            writer.printf("mIsPowerPolicyLocked: %b\n", mIsPowerPolicyLocked);
            writer.printf("mMaxSuspendWaitDurationMs: %d\n", mMaxSuspendWaitDurationMs);
            writer.printf("config_maxSuspendWaitDuration: %d\n", getMaxSuspendWaitDurationConfig());
            writer.printf("mWifiStateFile: %s\n", mWifiStateFile);
            writer.printf("mTetheringStateFile: %s\n", mTetheringStateFile);
            writer.printf("mWifiAdjustmentForSuspend: %b\n", mWifiAdjustmentForSuspend);
            writer.printf("# of power policy change listener: %d\n",
                    mPowerPolicyListeners.getRegisteredCallbackCount());
            writer.printf("mFactoryResetCallback: %s\n", mFactoryResetCallback);
            writer.printf("mIsListenerWaitingCancelled: %b\n", mIsListenerWaitingCancelled.get());
            writer.printf("kernel support S2R: %b\n",
                    mSystemInterface.isSystemSupportingDeepSleep());
            writer.printf("kernel support S2D: %b\n",
                    mSystemInterface.isSystemSupportingHibernation());
            writer.printf("mLastShutdownState: %d\n", mLastShutdownState);
            writer.printf("mReadyForCallback: %b\n", mReadyForCallback.get());
        }

        synchronized (mSimulationWaitObject) {
            writer.printf("mWakeFromSimulatedSleep: %b\n", mWakeFromSimulatedSleep);
            writer.printf("mInSimulatedDeepSleepMode: %b\n", mInSimulatedDeepSleepMode);
            writer.printf("mResumeDelayFromSimulatedSuspendSec: %d\n",
                    mResumeDelayFromSimulatedSuspendSec);
            writer.printf("mFreeMemoryBeforeSuspend: %b\n", mFreeMemoryBeforeSuspend);
        }

        mPolicyReader.dump(writer);
        mPowerComponentHandler.dump(writer);
        mSilentModeHandler.dump(writer);
        mScreenOffHandler.dump(writer);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {
        synchronized (mLock) {
            long currentStateToken = proto.start(CarPowerDumpProto.CURRENT_STATE);
            proto.write(CpmsStateProto.CAN_POSTPONE, mCurrentState.mCanPostpone);
            proto.write(CpmsStateProto.CAR_POWER_MANAGER_STATE,
                    mCurrentState.mCarPowerStateListenerState);
            proto.write(CpmsStateProto.SHUTDOWN_TYPE, mCurrentState.mShutdownType);
            proto.write(CpmsStateProto.STATE, mCurrentState.mState);
            proto.write(CpmsStateProto.STATE_NAME, mCurrentState.stateToString());
            proto.end(currentStateToken);
            proto.write(CarPowerDumpProto.SHUTDOWN_START_TIME, mShutdownStartTime);
            proto.write(CarPowerDumpProto.LAST_SLEEP_ENTRY_TIME, mLastSleepEntryTime);
            proto.write(CarPowerDumpProto.NEXT_WAKEUP_SEC, mNextWakeupSec);
            proto.write(CarPowerDumpProto.SHUTDOWN_ON_NEXT_SUSPEND, mShutdownOnNextSuspend);
            proto.write(
                    CarPowerDumpProto.ACTION_ON_FINISH, actionOnFinishToString(mActionOnFinish));
            proto.write(CarPowerDumpProto.SHUTDOWN_POLLING_INTERVAL_MS, mShutdownPollingIntervalMs);
            proto.write(CarPowerDumpProto.SHUTDOWN_PREPARE_TIME_MS, mShutdownPrepareTimeMs);
            proto.write(CarPowerDumpProto.REBOOT_AFTER_GARAGE_MODE, mRebootAfterGarageMode);
            proto.write(
                    CarPowerDumpProto.SWITCH_GUEST_USER_BEFORE_SLEEP, mSwitchGuestUserBeforeSleep);
            proto.write(CarPowerDumpProto.CURRENT_POWER_POLICY_ID, mCurrentPowerPolicyId);
            proto.write(CarPowerDumpProto.PENDING_POWER_POLICY_ID, mPendingPowerPolicyId);
            proto.write(
                    CarPowerDumpProto.CURRENT_POWER_POLICY_GROUP_ID, mCurrentPowerPolicyGroupId);
            proto.write(CarPowerDumpProto.IS_POWER_POLICY_LOCKED, mIsPowerPolicyLocked);
            proto.write(CarPowerDumpProto.MAX_SUSPEND_WAIT_DURATION_MS, mMaxSuspendWaitDurationMs);
            proto.write(CarPowerDumpProto.MAX_SUSPEND_WAIT_DURATION_CONFIG,
                    getMaxSuspendWaitDurationConfig());
            proto.write(CarPowerDumpProto.WIFI_STATE_FILE, mWifiStateFile.toString());
            proto.write(CarPowerDumpProto.TETHERING_STATE_FILE, mTetheringStateFile.toString());
            proto.write(CarPowerDumpProto.WIFI_ADJUSTMENT_FOR_SUSPEND, mWifiAdjustmentForSuspend);
            proto.write(CarPowerDumpProto.POWER_POLICY_LISTENERS,
                    mPowerPolicyListeners.getRegisteredCallbackCount());
            proto.write(CarPowerDumpProto.FACTORY_RESET_CALLBACK,
                    mFactoryResetCallback != null ? mFactoryResetCallback.toString() : "");
            proto.write(CarPowerDumpProto.IS_LISTENER_WAITING_CANCELLED,
                    mIsListenerWaitingCancelled.get());
            proto.write(CarPowerDumpProto.KERNEL_SUPPORTS_DEEP_SLEEP,
                    mSystemInterface.isSystemSupportingDeepSleep());
            proto.write(CarPowerDumpProto.KERNEL_SUPPORTS_HIBERNATION,
                    mSystemInterface.isSystemSupportingHibernation());
        }

        synchronized (mSimulationWaitObject) {
            proto.write(CarPowerDumpProto.WAKE_FROM_SIMULATED_SLEEP, mWakeFromSimulatedSleep);
            proto.write(CarPowerDumpProto.IN_SIMULATED_DEEP_SLEEP_MODE, mInSimulatedDeepSleepMode);
            proto.write(CarPowerDumpProto.RESUME_DELAY_FROM_SIMULATED_SUSPEND_SEC,
                    mResumeDelayFromSimulatedSuspendSec);
            proto.write(CarPowerDumpProto.FREE_MEMORY_BEFORE_SUSPEND, mFreeMemoryBeforeSuspend);
        }

        mPolicyReader.dumpProto(proto);
        mPowerComponentHandler.dumpProto(proto);
        mSilentModeHandler.dumpProto(proto);
        mScreenOffHandler.dumpProto(proto);
    }

    @Override
    public void onApPowerStateChange(PowerState state) {
        EventLogHelper.writeCarPowerManagerStateRequest(state.mState, state.mParam);
        synchronized (mLock) {
            mPendingPowerStates.addFirst(new CpmsState(state));
            mLock.notifyAll();
        }
        mHandler.handlePowerStateChange();
    }

    @VisibleForTesting
    void setStateForWakeUp() {
        mSilentModeHandler.init();
        synchronized (mLock) {
            mShouldResumeUserService = true;
        }
        handleWaitForVhal(new CpmsState(CpmsState.WAIT_FOR_VHAL,
                CarPowerManager.STATE_WAIT_FOR_VHAL, /* canPostpone= */ false));
        Slogf.d(TAG,
                "setStateForTesting(): mShouldResumeUserService is set to false and power state "
                        + "is switched "
                        + "to Wait For Vhal");
    }

    /**
     * Initiate state change from CPMS directly.
     */
    private void onApPowerStateChange(int apState,
            @CarPowerManager.CarPowerState int carPowerStateListenerState) {
        CpmsState newState = new CpmsState(apState, carPowerStateListenerState,
                /* canPostpone= */ false);
        BiFunction<CpmsState, CpmsState, Boolean> eventFilter = null;

        // We are ready to shut down. Suppress this transition if
        // there is a request to cancel the shutdown (WAIT_FOR_VHAL).
        // Completely ignore this WAIT_FOR_FINISH
        if (newState.mState == CpmsState.WAIT_FOR_FINISH) {
            eventFilter = (stateToAdd, pendingSate) ->
                    stateToAdd.mState == CpmsState.WAIT_FOR_FINISH
                    && pendingSate.mState == CpmsState.WAIT_FOR_VHAL;
        }

        // Check if there is another pending SHUTDOWN_PREPARE.
        // This could happen, when another SHUTDOWN_PREPARE request is received from VHAL
        // while notifying PRE_SHUTDOWN_PREPARE.
        // If SHUTDOWN_PREPARE request already exist in the queue, and it skips Garage Mode,
        // then newState is ignored .
        if (newState.mState == CpmsState.SHUTDOWN_PREPARE) {
            eventFilter = (stateToAdd, pendingState) ->
                    pendingState.mState == CpmsState.SHUTDOWN_PREPARE
                            && !pendingState.mCanPostpone
                            && pendingState.mCarPowerStateListenerState
                            == STATE_PRE_SHUTDOWN_PREPARE;
        }

        synchronized (mLock) {
            // If eventFilter exists, lets check if event that satisfies filter is in queue.
            if (eventFilter != null) {
                for (int idx = 0; idx < mPendingPowerStates.size(); idx++) {
                    CpmsState pendingState = mPendingPowerStates.get(idx);
                    if (eventFilter.apply(newState, pendingState)) {
                        return;
                    }
                }
            }
            mPendingPowerStates.addFirst(newState);
            mLock.notifyAll();
        }
        mHandler.handlePowerStateChange();
    }

    private void doHandlePowerStateChange() {
        CpmsState newState;
        CpmsState prevState;
        synchronized (mLock) {
            prevState = mCurrentState;
            newState = mPendingPowerStates.pollFirst();
            if (newState == null) {
                Slogf.w(TAG, "No more power state to process");
                return;
            }
            Slogf.i(TAG, "doHandlePowerStateChange: newState=%s", newState.name());
            if (!needPowerStateChangeLocked(newState)) {
                // We may need to process the pending power state request.
                if (!mPendingPowerStates.isEmpty()) {
                    Slogf.i(TAG, "There is a pending power state change request. requesting the "
                            + "processing...");
                    mHandler.handlePowerStateChange();
                }
                return;
            }

            // now real power change happens. Whatever was queued before should be all cancelled.
            mPendingPowerStates.clear();

            // Received updated SHUTDOWN_PREPARE there could be several reasons for that
            //  1. CPMS is in SHUTDOWN_PREPARE, and received state change to perform transition
            //     from PRE_SHUTDOWN_PREPARE into SHUTDOWN_PREPARE
            //  2. New SHUTDOWN_PREPARE request is received, and it is different from existing one.
            if (newState.mState == CpmsState.SHUTDOWN_PREPARE && newState.mState == prevState.mState
                    && newState.mCarPowerStateListenerState == STATE_PRE_SHUTDOWN_PREPARE) {
                // Nothing to do here, skipping clearing completion queue
            } else {
                clearWaitingForCompletion(/*clearQueue=*/false);
            }

            mCurrentState = newState;
        }
        mHandler.cancelProcessingComplete();

        Slogf.i(TAG, "setCurrentState %s", newState);
        CarStatsLogHelper.logPowerState(newState.mState);
        EventLogHelper.writeCarPowerManagerStateChange(newState.mState);
        switch (newState.mState) {
            case CpmsState.WAIT_FOR_VHAL:
                handleWaitForVhal(newState);
                break;
            case CpmsState.ON:
                handleOn();
                break;
            case CpmsState.SHUTDOWN_PREPARE:
                handleShutdownPrepare(newState, prevState);
                break;
            case CpmsState.SIMULATE_SLEEP:
            case CpmsState.SIMULATE_HIBERNATION:
                simulateShutdownPrepare(newState, prevState);
                break;
            case CpmsState.WAIT_FOR_FINISH:
                handleWaitForFinish(newState);
                break;
            case CpmsState.SUSPEND:
                // Received FINISH from VHAL
                handleFinish();
                break;
            default:
                // Illegal state
                // TODO(b/202414427): Add handling of illegal state
                break;
        }
    }

    private void notifyPowerStateChangeToDaemon(@CarPowerManager.CarPowerState int powerState) {
        ICarPowerPolicyDelegate daemon;
        synchronized (mLock) {
            daemon = mRefactoredCarPowerPolicyDaemon;
        }
        if (daemon == null) {
            Slogf.e(TAG, "Failed to notify car power policy daemon of power state change, "
                    + "daemon unavailable");
            return;
        }
        notifyPowerStateChangeToDaemon(daemon, powerState);
    }

    private void notifyPowerStateChangeToDaemon(ICarPowerPolicyDelegate daemon,
            @CarPowerManager.CarPowerState int powerState) {
        Slogf.i(TAG, "Notifying CPPD of power state(%s)", powerStateToString(powerState));

        String powerStateName = powerStateToString(powerState);
        if (!mReadyForCallback.get()) {
            Slogf.w(TAG, "Cannot notify power state(%S) of CPPD: not ready for calling to CPPD",
                    powerStateName);
            return;
        }
        AsyncPolicyRequest request = generateAsyncPolicyRequest(
                DEFAULT_POWER_POLICY_REQUEST_TIMEOUT_MS);
        int requestId = request.getRequestId();
        synchronized (mLock) {
            mRequestIdToPolicyRequest.put(requestId, request);
        }
        try {
            Slogf.i(TAG, "Request(%d) of applying power policy for power state(%s) to CPPD in "
                    + "async", requestId, powerStateName);
            daemon.applyPowerPolicyPerPowerStateChangeAsync(requestId, (byte) powerState);
            boolean policyRequestServed = request.await();
            if (!policyRequestServed) {
                Slogf.w(TAG, "Power policy request (ID: %d) for power state(%s) timed out after %d "
                        + "ms", requestId, powerStateName, DEFAULT_POWER_POLICY_REQUEST_TIMEOUT_MS);
                return;
            }
        } catch (IllegalArgumentException e) {
            Slogf.w(TAG, e, "Failed to apply power policy for power state(%s)", powerStateName);
            return;
        } catch (InterruptedException e) {
            Slogf.w(TAG, e, "Wait for power policy change request for power state(%s) interrupted",
                    powerStateName);
            Thread.currentThread().interrupt();
            return;
        } catch (RemoteException e) {
            Slogf.w(TAG, e, "Failed to apply power policy for power state(%s), connection issue",
                    powerStateName);
            return;
        } finally {
            synchronized (mLock) {
                mRequestIdToPolicyRequest.remove(requestId);
            }
        }
        if (!request.isSuccessful()) {
            Slogf.w(TAG, "Failed to apply power policy for power state(%s), failure reason = %d",
                    powerStateName, request.getFailureReason());
            return;
        }
        CarPowerPolicy accumulatedPolicy = request.getAccumulatedPolicy();
        updateCurrentPowerPolicy(accumulatedPolicy);
        notifyPowerPolicyChange(accumulatedPolicy);
    }

    private void handleWaitForVhal(CpmsState state) {
        @CarPowerManager.CarPowerState int carPowerStateListenerState =
                state.mCarPowerStateListenerState;
        // TODO(b/177478420): Restore Wifi, Audio, Location, and Bluetooth, if they are artificially
        // modified for S2R.
        mSilentModeHandler.querySilentModeHwState();

        if (mFeatureFlags.carPowerPolicyRefactoring()) {
            notifyPowerStateChangeToDaemon(CarPowerManager.STATE_WAIT_FOR_VHAL);
        } else {
            applyDefaultPowerPolicyForState(CarPowerManager.STATE_WAIT_FOR_VHAL,
                    PolicyReader.POWER_POLICY_ID_INITIAL_ON);
            if (!mSilentModeHandler.isSilentMode()) {
                cancelPreemptivePowerPolicy();
            }
        }

        sendPowerManagerEvent(carPowerStateListenerState, INVALID_TIMEOUT);
        // Inspect CarPowerStateListenerState to decide which message to send via VHAL
        int lastShutdownState = CarRemoteAccessManager.NEXT_POWER_STATE_OFF;
        switch (carPowerStateListenerState) {
            case CarPowerManager.STATE_WAIT_FOR_VHAL:
                lastShutdownState = CarRemoteAccessManager.NEXT_POWER_STATE_OFF;
                mHal.sendWaitForVhal();
                break;
            case CarPowerManager.STATE_SHUTDOWN_CANCELLED:
                synchronized (mLock) {
                    mShutdownOnNextSuspend = false; // This cancels the "NextSuspend"
                    mIsEmergencyShutdown = false; // TODO add cancel test
                }
                mHal.sendShutdownCancel();
                Slogf.d(TAG, "reset mIsEmergencyShutdown");
                break;
            case CarPowerManager.STATE_SUSPEND_EXIT:
                lastShutdownState = CarRemoteAccessManager.NEXT_POWER_STATE_SUSPEND_TO_RAM;
                mHal.sendSleepExit();
                break;
            case CarPowerManager.STATE_HIBERNATION_EXIT:
                lastShutdownState = CarRemoteAccessManager.NEXT_POWER_STATE_SUSPEND_TO_DISK;
                mHal.sendHibernationExit();
                break;
            default:
                Slogf.w(TAG, "Invalid action when handling wait for VHAL: %d",
                        carPowerStateListenerState);
                break;
        }
        synchronized (mLock) {
            mLastShutdownState = lastShutdownState;
        }
        if (mWifiAdjustmentForSuspend) {
            restoreWifiFully();
        }
    }

    private void updateCarUserNoticeServiceIfNecessary() {
        try {
            int currentUserId = ActivityManager.getCurrentUser();
            UserHandleHelper userHandleHelper = new UserHandleHelper(mContext, mUserManager);
            UserHandle currentUser = userHandleHelper.getExistingUserHandle(currentUserId);
            CarUserNoticeService carUserNoticeService =
                    CarLocalServices.getService(CarUserNoticeService.class);
            if (currentUser != null && userHandleHelper.isGuestUser(currentUser)
                    && carUserNoticeService != null) {
                Slogf.i(TAG, "Car user notice service will ignore all messages before user "
                        + "switch.");
                Intent intent = new Intent();
                intent.setComponent(ComponentName.unflattenFromString(
                        mContext.getResources().getString(R.string.continuousBlankActivity)));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivityAsUser(intent, UserHandle.CURRENT);
                carUserNoticeService.ignoreUserNotice(currentUserId);
            }
        } catch (Exception e) {
            Slogf.w(TAG, e, "Cannot ignore user notice for current user");
        }
    }

    @VisibleForTesting
    void handleOn() {
        if (factoryResetIfNeeded()) return;

        // If current user is a Guest User, we want to inform CarUserNoticeService not to show
        // notice for current user, and show user notice only for the target user.
        if (!mSwitchGuestUserBeforeSleep) {
            updateCarUserNoticeServiceIfNecessary();
        }

        if (mFeatureFlags.carPowerPolicyRefactoring()) {
            notifyPowerStateChangeToDaemon(CarPowerManager.STATE_ON);
        } else {
            if (!mSilentModeHandler.isSilentMode()) {
                cancelPreemptivePowerPolicy();
            }
            applyDefaultPowerPolicyForState(VehicleApPowerStateReport.ON,
                    PolicyReader.POWER_POLICY_ID_ALL_ON);
        }

        sendPowerManagerEvent(CarPowerManager.STATE_ON, INVALID_TIMEOUT);

        mHal.sendOn();

        synchronized (mLock) {
            if (!mShouldResumeUserService) {
                Slogf.d(TAG, "handleOn(): called on boot");
                return;
            } else {
                mShouldResumeUserService = false;
            }
        }

        try {
            mUserService.onResume();
        } catch (Exception e) {
            Slogf.e(TAG, e, "Could not switch user on resume");
        }
    }

    private boolean factoryResetIfNeeded() {
        ICarResultReceiver callback;
        synchronized (mLock) {
            if (mFactoryResetCallback == null) return false;
            callback = mFactoryResetCallback;
        }

        try {
            Slogf.i(TAG, "Factory resetting as it was delayed by user");
            callback.send(/* resultCode= */ 0, /* resultData= */ null);
            return true;
        } catch (Exception e) {
            Slogf.wtf(TAG, e, "Should have factory reset, but failed");
            return false;
        }
    }

    // TODO(b/286303350): remove once power policy refactor is complete, CPPD will handle applying
    //                    default power policies according to state
    private void applyDefaultPowerPolicyForState(@CarPowerManager.CarPowerState int state,
            @Nullable String fallbackPolicyId) {
        Slogf.i(TAG, "Applying the default power policy for %s", powerStateToString(state));
        CarPowerPolicy policy;
        synchronized (mLock) {
            policy = mPolicyReader
                    .getDefaultPowerPolicyForState(mCurrentPowerPolicyGroupId, state);
        }
        if (policy == null && fallbackPolicyId == null) {
            Slogf.w(TAG, "No default power policy for %s is found", powerStateToString(state));
            return;
        }
        String policyId = policy == null ? fallbackPolicyId : policy.getPolicyId();
        applyPowerPolicy(policyId, /* delayNotification= */ false, /* upToDaemon= */ true,
                /* force= */ false);
    }

    /**
     * Sets the callback used to factory reset the device on resume when the user delayed it.
     */
    public void setFactoryResetCallback(ICarResultReceiver callback) {
        synchronized (mLock) {
            mFactoryResetCallback = callback;
        }
    }

    /**
     * Tells Garage Mode if it should run normally, or just
     * exit immediately without indicating 'idle'
     * @return True if no idle jobs should be run
     * @hide
     */
    public boolean garageModeShouldExitImmediately() {
        synchronized (mLock) {
            return mGarageModeShouldExitImmediately;
        }
    }

    /**
     * This method is required for testing
     * Gets {@code PolicyReader} that reads {@code power_policy.xml}.
     * @return {@link PolicyReader}
     */
    @VisibleForTesting
    public PolicyReader getPolicyReader() {
        return mPolicyReader;
    }

    private void handleShutdownPrepare(CpmsState currentState, CpmsState prevState) {
        switch (currentState.mCarPowerStateListenerState) {
            case CarPowerManager.STATE_PRE_SHUTDOWN_PREPARE:
                updateShutdownPrepareStatus(currentState);
                if (prevState.mCarPowerStateListenerState == STATE_SHUTDOWN_PREPARE) {
                    // Received request to update SHUTDOWN target
                    currentState = new CpmsState(currentState.mState,
                            prevState.mCarPowerStateListenerState,
                            prevState.mCanPostpone, currentState.mShutdownType);
                    synchronized (mLock) {
                        mCurrentState = currentState;
                    }
                    clearWaitingForCompletion(/*clearQueue=*/true);
                } else if (prevState.mCarPowerStateListenerState == STATE_PRE_SHUTDOWN_PREPARE) {
                    // Update of state occurred while in PRE_SHUTDOWN_PREPARE
                    boolean areListenersEmpty;
                    synchronized (mLock) {
                        areListenersEmpty = mListenersWeAreWaitingFor.isEmpty();
                    }
                    if (areListenersEmpty) {
                        handleCoreShutdownPrepare();
                    } else {
                        // PRE_SHUTDOWN_PREPARE is still being processed, no actions required
                        return;
                    }
                } else {
                    handlePreShutdownPrepare();
                }
                break;
            case CarPowerManager.STATE_SHUTDOWN_PREPARE:
                handleCoreShutdownPrepare();
                break;
            default:
                Slogf.w(TAG, "Not supported listener state(%d)",
                        currentState.mCarPowerStateListenerState);
        }
    }

    private void updateShutdownPrepareStatus(CpmsState newState) {
        // Shutdown on finish if the system doesn't support deep sleep/hibernation
        // or doesn't allow it.
        synchronized (mLock) {
            if (mShutdownOnNextSuspend || (
                    newState.mShutdownType == PowerState.SHUTDOWN_TYPE_POWER_OFF
                            || newState.mShutdownType == PowerState.SHUTDOWN_TYPE_EMERGENCY)) {
                mActionOnFinish = ACTION_ON_FINISH_SHUTDOWN;
            } else if (newState.mShutdownType == PowerState.SHUTDOWN_TYPE_DEEP_SLEEP) {
                boolean isDeepSleepOnFinish =
                        isDeepSleepAvailable() || newState.mState == CpmsState.SIMULATE_SLEEP;
                mActionOnFinish = isDeepSleepOnFinish ? ACTION_ON_FINISH_DEEP_SLEEP
                        : ACTION_ON_FINISH_SHUTDOWN;
            } else if (newState.mShutdownType == PowerState.SHUTDOWN_TYPE_HIBERNATION) {
                boolean isHibernationOnFinish = isHibernationAvailable()
                        || newState.mState == CpmsState.SIMULATE_HIBERNATION;
                mActionOnFinish = isHibernationOnFinish ? ACTION_ON_FINISH_HIBERNATION
                        : ACTION_ON_FINISH_SHUTDOWN;
            } else {
                Slogf.wtf(TAG, "handleShutdownPrepare - incorrect state " + newState);
            }
            mGarageModeShouldExitImmediately = !newState.mCanPostpone;
            if (!mIsEmergencyShutdown) {
                mIsEmergencyShutdown = newState.mShutdownType == PowerState.SHUTDOWN_TYPE_EMERGENCY;
                Slogf.d(TAG, "set mIsEmergencyShutdown to " + mIsEmergencyShutdown);
            } else {
                // Emergency shutdown can be cancelled only via SHUTDOWN_CANCEL request");
                Slogf.d(TAG, "mIsEmergencyShutdown is already set");
            }
        }
    }

    private void handlePreShutdownPrepare() {
        int intervalMs;
        long timeoutMs;
        synchronized (mLock) {
            intervalMs = mShutdownPollingIntervalMs;
            Slogf.i(TAG, mGarageModeShouldExitImmediately
                    ? "starting shutdown prepare without Garage Mode"
                    : "starting shutdown prepare with Garage Mode");

            // in case of emergency shutdown CPMS will not wait for
            timeoutMs = mIsEmergencyShutdown ? 0 : getPreShutdownPrepareTimeoutConfig();
        }

        int state = CarPowerManager.STATE_PRE_SHUTDOWN_PREPARE;
        sendPowerManagerEvent(state, timeoutMs);
        Runnable taskAtCompletion = () -> {
            // The next power state is still SHUTDOWN_PREPARE, and the listener state is
            // SHUTDOW_PREPARE.
            Slogf.i(TAG, "All listeners completed for %s", powerStateToString(state));
            onApPowerStateChange(CpmsState.SHUTDOWN_PREPARE,
                    CarPowerManager.STATE_SHUTDOWN_PREPARE);
        };

        waitForCompletionWithShutdownPostpone(state, timeoutMs, taskAtCompletion, intervalMs);
    }

    private void handleCoreShutdownPrepare() {
        Slogf.i(TAG, "Handling core part of shutdown prepare");
        doShutdownPrepare();
    }

    // Simulates system shutdown to suspend
    private void simulateShutdownPrepare(CpmsState newState, CpmsState oldState) {
        Slogf.i(TAG, "Simulating shutdown prepare");
        handleShutdownPrepare(newState, oldState);
    }

    private void doShutdownPrepare() {
        long timeoutMs;
        long intervalMs;
        boolean isEmergencyShutdown;
        synchronized (mLock) {
            timeoutMs = mShutdownPrepareTimeMs;
            intervalMs = mShutdownPollingIntervalMs;
            mShutdownStartTime = SystemClock.elapsedRealtime();
            isEmergencyShutdown = mIsEmergencyShutdown;
        }

        if (isEmergencyShutdown) {
            timeoutMs = 0;  // do not wait for listeners to complete during emergency shutdown
        } else if (BuildHelper.isUserDebugBuild() || BuildHelper.isEngBuild()) {
            int shutdownPrepareTimeOverrideInSecs =
                    SystemProperties.getInt(PROP_MAX_GARAGE_MODE_DURATION_OVERRIDE, -1);
            if (shutdownPrepareTimeOverrideInSecs >= 0) {
                timeoutMs = shutdownPrepareTimeOverrideInSecs * 1000L;
            }
        }
        makeSureNoUserInteraction();
        sendPowerManagerEvent(CarPowerManager.STATE_SHUTDOWN_PREPARE, timeoutMs);
        mHal.sendShutdownPrepare();
        waitForShutdownPrepareListenersToComplete(timeoutMs, intervalMs);
    }

    private void handleWaitForFinish(CpmsState state) {
        int timeoutMs = getShutdownEnterTimeoutConfig();
        sendPowerManagerEvent(state.mCarPowerStateListenerState, timeoutMs);
        Runnable taskAtCompletion = () -> {
            Slogf.i(TAG, "All listeners completed for %s",
                    powerStateToString(state.mCarPowerStateListenerState));
            int wakeupSec;
            synchronized (mLock) {
                // If we're shutting down immediately, don't schedule a wakeup time.
                wakeupSec = mGarageModeShouldExitImmediately ? 0 : mNextWakeupSec;
            }
            switch (state.mCarPowerStateListenerState) {
                case CarPowerManager.STATE_SUSPEND_ENTER:
                    mHal.sendSleepEntry(wakeupSec);
                    break;
                case CarPowerManager.STATE_SHUTDOWN_ENTER:
                    mHal.sendShutdownStart(wakeupSec);
                    break;
                case CarPowerManager.STATE_HIBERNATION_ENTER:
                    mHal.sendHibernationEntry(wakeupSec);
                    break;
                default:
                    Slogf.w(TAG, "Invalid action when handling wait for finish: %d",
                            state.mCarPowerStateListenerState);
                    break;
            }
        };

        int intervalMs;
        synchronized (mLock) {
            intervalMs = mShutdownPollingIntervalMs;
        }

        waitForCompletionWithShutdownPostpone(state.mCarPowerStateListenerState, timeoutMs,
                taskAtCompletion, intervalMs);
    }

    private void handleFinish() {
        int listenerState;
        synchronized (mLock) {
            switch (mActionOnFinish) {
                case ACTION_ON_FINISH_SHUTDOWN:
                    listenerState = CarPowerManager.STATE_POST_SHUTDOWN_ENTER;
                    break;
                case ACTION_ON_FINISH_DEEP_SLEEP:
                    listenerState = CarPowerManager.STATE_POST_SUSPEND_ENTER;
                    break;
                case ACTION_ON_FINISH_HIBERNATION:
                    listenerState = CarPowerManager.STATE_POST_HIBERNATION_ENTER;
                    break;
                default:
                    Slogf.w(TAG, "Invalid action on finish: %d", mActionOnFinish);
                    return;
            }
        }
        int timeoutMs = getPostShutdownEnterTimeoutConfig();
        sendPowerManagerEvent(listenerState, timeoutMs);
        Runnable taskAtCompletion = () -> {
            Slogf.i(TAG, "All listeners completed for %s", powerStateToString(listenerState));
            doHandleFinish();
        };
        Slogf.i(TAG, "Start waiting for listener completion for %s",
                powerStateToString(listenerState));
        waitForCompletion(taskAtCompletion, /* taskAtInterval= */ null, timeoutMs,
                /* intervalMs= */ -1);
    }

    private void doHandleFinish() {
        boolean simulatedMode;
        synchronized (mSimulationWaitObject) {
            simulatedMode = mInSimulatedDeepSleepMode;
        }
        boolean mustShutDown;
        boolean forceReboot;
        synchronized (mLock) {
            mustShutDown = (mActionOnFinish == ACTION_ON_FINISH_SHUTDOWN) && !simulatedMode;
            forceReboot = mRebootAfterGarageMode;
            mRebootAfterGarageMode = false;
        }
        if (forceReboot) {
            PowerManager powerManager = mContext.getSystemService(PowerManager.class);
            if (powerManager == null) {
                Slogf.wtf(TAG, "No PowerManager. Cannot reboot.");
            } else {
                Slogf.i(TAG, "GarageMode has completed. Forcing reboot.");
                powerManager.reboot("GarageModeReboot");
                throw new AssertionError("Should not return from PowerManager.reboot()");
            }
        }
        // To make Kernel implementation simpler when going into sleep.
        if (mWifiAdjustmentForSuspend) {
            disableWifiFully();
        }

        if (mustShutDown) {
            // shutdown HU
            mSystemInterface.shutdown();
        } else {
            doHandleSuspend(simulatedMode);
        }
        synchronized (mLock) {
            mShutdownOnNextSuspend = false;
        }
    }

    private void disableWifiFully() {
        disableWifi();
        disableTethering();
    }

    private void restoreWifiFully() {
        restoreTethering();
        restoreWifi();
    }

    private void restoreWifi() {
        boolean needToRestore = readWifiModifiedState(mWifiStateFile);
        if (!needToRestore) return;
        if (!mWifiManager.isWifiEnabled()) {
            Slogf.i(TAG, "Wifi has been enabled to restore the last setting");
            mWifiManager.setWifiEnabled(true);
        }
        // Update the persistent data as wifi is not modified by car framework.
        saveWifiModifiedState(mWifiStateFile, /* forciblyDisabled= */ false);
    }

    private void disableWifi() {
        boolean wifiEnabled = mWifiManager.isWifiEnabled();
        boolean wifiModifiedState = readWifiModifiedState(mWifiStateFile);
        if (wifiEnabled != wifiModifiedState) {
            Slogf.i(TAG, "Saving the current Wifi state");
            saveWifiModifiedState(mWifiStateFile, wifiEnabled);
        }

        // In some devices, enabling a tether temporarily turns off Wifi. To make sure that Wifi is
        // disabled, we call this method in all cases.
        mWifiManager.setWifiEnabled(false);
        Slogf.i(TAG, "Wifi has been disabled and the last setting was saved");
    }

    private void restoreTethering() {
        boolean needToRestore = readWifiModifiedState(mTetheringStateFile);
        if (!needToRestore) return;
        if (!mWifiManager.isWifiApEnabled()) {
            Slogf.i(TAG, "Tethering has been enabled to restore the last setting");
            startTethering();
        }
        // Update the persistent data as wifi is not modified by car framework.
        saveWifiModifiedState(mTetheringStateFile, /*forciblyDisabled= */ false);
    }

    private void disableTethering() {
        boolean tetheringEnabled = mWifiManager.isWifiApEnabled();
        boolean tetheringModifiedState = readWifiModifiedState(mTetheringStateFile);
        if (tetheringEnabled != tetheringModifiedState) {
            Slogf.i(TAG, "Saving the current tethering state: tetheringEnabled=%b",
                    tetheringEnabled);
            saveWifiModifiedState(mTetheringStateFile, tetheringEnabled);
        }
        if (!tetheringEnabled) return;

        mTetheringManager.stopTethering(TETHERING_WIFI);
        Slogf.i(TAG, "Tethering has been disabled and the last setting was saved");
    }

    private void saveWifiModifiedState(AtomicFile file, boolean forciblyDisabled) {
        FileOutputStream fos;
        try {
            fos = file.startWrite();
        } catch (IOException e) {
            Slogf.e(TAG, e, "Cannot create %s", file);
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {
            writer.write(forciblyDisabled ? COMPONENT_STATE_MODIFIED : COMPONENT_STATE_ORIGINAL);
            writer.newLine();
            writer.flush();
            file.finishWrite(fos);
        } catch (IOException e) {
            file.failWrite(fos);
            Slogf.e(TAG, e, "Writing %s failed", file);
        }
    }

    private boolean readWifiModifiedState(AtomicFile file) {
        boolean needToRestore = false;
        boolean invalidState = false;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.openRead(), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            if (line == null) {
                needToRestore = false;
                invalidState = true;
            } else {
                line = line.trim();
                needToRestore = COMPONENT_STATE_MODIFIED.equals(line);
                invalidState = !(needToRestore || COMPONENT_STATE_ORIGINAL.equals(line));
            }
        } catch (IOException e) {
            // If a file named wifi_state doesn't exist, we will not modify Wifi at system start.
            Slogf.w(TAG, "Failed to read %s: %s", file, e);
            return false;
        }
        if (invalidState) {
            file.delete();
        }

        return needToRestore;
    }

    private void startTethering() {
        TetheringRequest request = new TetheringRequest.Builder(TETHERING_WIFI)
                .setShouldShowEntitlementUi(false).build();
        mTetheringManager.startTethering(request, mContext.getMainExecutor(),
                new TetheringManager.StartTetheringCallback() {
                    @Override
                    public void onTetheringFailed(int error) {
                        Slogf.w(TAG, "Starting tethering failed: %d", error);
                    }
                });
    }

    private void waitForShutdownPrepareListenersToComplete(long timeoutMs, long intervalMs) {
        int state = CarPowerManager.STATE_SHUTDOWN_PREPARE;
        Runnable taskAtCompletion = () -> {
            finishShutdownPrepare();
            Slogf.i(TAG, "All listeners completed for %s", powerStateToString(state));
        };

        waitForCompletionWithShutdownPostpone(state, timeoutMs, taskAtCompletion, intervalMs);

        // allowUserSwitch value doesn't matter for onSuspend = true
        mUserService.onSuspend();
        synchronized (mLock) {
            mShouldResumeUserService = true;
        }
    }

    private void waitForCompletion(Runnable taskAtCompletion, Runnable taskAtInterval,
            long timeoutMs, long intervalMs) {
        boolean isComplete = false;
        synchronized (mLock) {
            isComplete = mListenersWeAreWaitingFor.isEmpty();
        }
        if (isComplete) {
            taskAtCompletion.run();
        } else {
            // Reset a flag to signal that waiting for completion is cancelled.
            mIsListenerWaitingCancelled.set(false);
            waitForCompletionAsync(taskAtCompletion, taskAtInterval, timeoutMs, intervalMs);
        }
    }

    // Waits for listeners to complete.
    // If {@code intervalMs} is non-positive value, it is ignored and the method waits up to
    // {@code timeoutMs}.
    private void waitForCompletionAsync(Runnable taskAtCompletion, Runnable taskAtInterval,
            long timeoutMs, long intervalMs) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            long startTimeMs = SystemClock.elapsedRealtime();
            while (true) {
                try {
                    long waitTimeMs = timeoutMs - (SystemClock.elapsedRealtime() - startTimeMs);
                    boolean isLastWait = true;
                    if (intervalMs > 0 && waitTimeMs > intervalMs) {
                        isLastWait = false;
                        waitTimeMs = intervalMs;
                    }
                    boolean isNotified = mListenerCompletionSem.tryAcquire(waitTimeMs,
                            TimeUnit.MILLISECONDS);
                    mListenerCompletionSem.drainPermits();
                    if (!isNotified) {
                        if (isLastWait) {
                            Slogf.w(TAG, "Waiting for listener completion is timeout(%d)",
                                    waitTimeMs);
                            taskAtCompletion.run();
                            return;
                        } else if (taskAtInterval != null) {
                            taskAtInterval.run();
                        }
                    }
                    boolean isComplete = false;
                    synchronized (mLock) {
                        if (mIsListenerWaitingCancelled.get()) {
                            Slogf.i(TAG, "Waiting for listener completion is cancelled");
                            mIsListenerWaitingCancelled.set(false);
                            return;
                        }
                        isComplete = mListenersWeAreWaitingFor.isEmpty();
                    }
                    if (isComplete) {
                        Slogf.i(TAG, "All listeners completed");
                        taskAtCompletion.run();
                        mIsListenerWaitingCancelled.set(false);
                        return;
                    }
                } catch (InterruptedException e) {
                    Slogf.w(TAG, e, "Thread interrupted while waiting for listener completion");
                    Thread.currentThread().interrupt();
                }
            }
        });
        executor.shutdown();
    }

    private void clearWaitingForCompletion(boolean clearQueue) {
        if (clearQueue) {
            synchronized (mLock) {
                mListenersWeAreWaitingFor.clear();
            }
        } else {
            mIsListenerWaitingCancelled.set(true);
        }

        mListenerCompletionSem.release();
    }

    private void sendPowerManagerEvent(@CarPowerManager.CarPowerState int newState,
            long timeoutMs) {
        // Broadcasts to the listeners that do not signal completion.
        notifyListeners(mPowerManagerListeners, newState, INVALID_TIMEOUT);

        boolean allowCompletion;
        boolean isShutdownPrepare = newState == CarPowerManager.STATE_SHUTDOWN_PREPARE;
        long internalListenerExpirationTimeMs = INVALID_TIMEOUT;
        long binderListenerExpirationTimeMs = INVALID_TIMEOUT;

        // Fully populates mListenersWeAreWaitingFor before calling any onStateChanged()
        // for the listeners that signal completion.
        // Otherwise, if the first listener calls finish() synchronously, we will
        // see the list go empty and we will think that we are done.
        PowerManagerCallbackList<ICarPowerStateListener> completingInternalListeners =
                new PowerManagerCallbackList(l -> { });
        PowerManagerCallbackList<ICarPowerStateListener> completingBinderListeners =
                new PowerManagerCallbackList(l -> { });
        synchronized (mLock) {
            if (isCompletionAllowed(newState)) {
                if (timeoutMs < 0) {
                    Slogf.wtf(TAG, "Completion timeout(%d) for state(%d) should be "
                            + "non-negative", timeoutMs, newState);
                    return;
                }
                mStateForCompletion = newState;
                allowCompletion = true;
                internalListenerExpirationTimeMs = SystemClock.elapsedRealtime() + timeoutMs;
                binderListenerExpirationTimeMs =
                        isShutdownPrepare ? INVALID_TIMEOUT : internalListenerExpirationTimeMs;
            } else {
                allowCompletion = false;
                mStateForCompletion = CarPowerManager.STATE_INVALID;
            }

            mListenersWeAreWaitingFor.clear();
            for (int i = 0; i < mInternalPowerListeners.size(); i++) {
                ICarPowerStateListener listener = mInternalPowerListeners.get(i);
                completingInternalListeners.register(listener);
                if (allowCompletion) {
                    mListenersWeAreWaitingFor.add(listener.asBinder());
                }
            }
            mBroadcastHandler.post(() -> {
                int idx = mPowerManagerListenersWithCompletion.beginBroadcast();
                while (idx-- > 0) {
                    ICarPowerStateListener listener =
                            mPowerManagerListenersWithCompletion.getBroadcastItem(idx);
                    completingBinderListeners.register(listener);
                    // For binder listeners, listener completion is not allowed for SHUTDOWN_PREPARE
                    if (allowCompletion && !isShutdownPrepare) {
                        synchronized (mLock) {
                            mListenersWeAreWaitingFor.add(listener.asBinder());
                        }
                    }
                }
                mPowerManagerListenersWithCompletion.finishBroadcast();
            });
        }
        // Resets the semaphore's available permits to 0.
        mListenerCompletionSem.drainPermits();
        // Broadcasts to the listeners that DO signal completion.
        notifyListeners(completingInternalListeners, newState, internalListenerExpirationTimeMs);
        notifyListeners(completingBinderListeners, newState, binderListenerExpirationTimeMs);

        // Call unlinkToDeath inside RemoteCallbackList
        completingInternalListeners.kill();
        completingBinderListeners.kill();
    }

    private void notifyListeners(PowerManagerCallbackList<ICarPowerStateListener> listenerList,
            @CarPowerManager.CarPowerState int newState, long expirationTimeMs) {
        CountDownLatch listenerLatch = new CountDownLatch(1);
        mBroadcastHandler.post(() -> {
            int idx = listenerList.beginBroadcast();
            while (idx-- > 0) {
                ICarPowerStateListener listener = listenerList.getBroadcastItem(idx);
                try {
                    listener.onStateChanged(newState, expirationTimeMs);
                } catch (RemoteException e) {
                    // It's likely the connection snapped. Let binder death handle the situation.
                    Slogf.e(TAG, e, "onStateChanged() call failed");
                }
            }
            listenerList.finishBroadcast();
            listenerLatch.countDown();
        });
        try {
            listenerLatch.await(DEFAULT_COMPLETION_WAIT_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Slogf.w(TAG, e, "Wait for power state listener completion interrupted");
            Thread.currentThread().interrupt();
        }
    }

    private void doHandleSuspend(boolean simulatedMode) {
        int status;
        if (mFeatureFlags.carPowerPolicyRefactoring()) {
            status = applyPowerPolicy(PolicyReader.POWER_POLICY_ID_SUSPEND_PREP,
                    /* delayNotification= */ false, /* upToDaemon= */ false, /* force= */ true);
        } else {
            status = applyPreemptivePowerPolicy(PolicyReader.POWER_POLICY_ID_SUSPEND_PREP);
        }
        if (status != PolicyOperationStatus.OK) {
            Slogf.w(TAG, PolicyOperationStatus.errorCodeToString(status));
        }
        // Keeps holding partial wakelock to prevent entering sleep before enterDeepSleep/
        // enterHibernation call. enterDeepSleep/enterHibernation should force sleep entry even if
        // wake lock is kept.
        forEachDisplay(mContext, mSystemInterface::switchToPartialWakeLock);
        mHandler.cancelProcessingComplete();
        synchronized (mLock) {
            mLastSleepEntryTime = SystemClock.elapsedRealtime();
        }
        @CarPowerManager.CarPowerState int nextListenerState;
        if (simulatedMode) {
            simulateSleepByWaiting();
            nextListenerState = CarPowerManager.STATE_SHUTDOWN_CANCELLED;
        } else {
            boolean sleepSucceeded = suspendWithRetries();
            if (!sleepSucceeded) {
                // Suspend failed and we shut down instead.
                // We either won't get here at all or we will power off very soon.
                return;
            }
            synchronized (mLock) {
                // We suspended and have now resumed
                nextListenerState = (mActionOnFinish == ACTION_ON_FINISH_DEEP_SLEEP)
                        ? CarPowerManager.STATE_SUSPEND_EXIT
                        : CarPowerManager.STATE_HIBERNATION_EXIT;
            }
        }
        synchronized (mLock) {
            // Any wakeup time from before is no longer valid.
            mNextWakeupSec = 0;
        }
        Slogf.i(TAG, "Resuming after suspending");
        forEachDisplay(mContext, mSystemInterface::refreshDisplayBrightness);
        onApPowerStateChange(CpmsState.WAIT_FOR_VHAL, nextListenerState);
    }

    @GuardedBy("mLock")
    private boolean needPowerStateChangeLocked(@NonNull CpmsState newState) {
        if (mCurrentState == null) {
            return true;
        } else if (mCurrentState.equals(newState)) {
            Slogf.d(TAG, "Requested state is already in effect: %s", newState.name());
            return false;
        }

        // The following switch/case enforces the allowed state transitions.
        boolean transitionAllowed = false;
        switch (mCurrentState.mState) {
            case CpmsState.WAIT_FOR_VHAL:
                transitionAllowed = (newState.mState == CpmsState.ON)
                    || (newState.mState == CpmsState.SHUTDOWN_PREPARE);
                break;
            case CpmsState.SUSPEND:
                transitionAllowed = newState.mState == CpmsState.WAIT_FOR_VHAL;
                break;
            case CpmsState.ON:
                transitionAllowed = (newState.mState == CpmsState.SHUTDOWN_PREPARE)
                    || (newState.mState == CpmsState.SIMULATE_SLEEP)
                    || (newState.mState == CpmsState.SIMULATE_HIBERNATION);
                break;
            case CpmsState.SHUTDOWN_PREPARE:
                // If VHAL sends SHUTDOWN_IMMEDIATELY or SLEEP_IMMEDIATELY while in
                // SHUTDOWN_PREPARE state, do it.
                transitionAllowed =
                        ((newState.mState == CpmsState.SHUTDOWN_PREPARE) && !newState.mCanPostpone)
                                || (newState.mState == CpmsState.WAIT_FOR_FINISH)
                                || (newState.mState == CpmsState.WAIT_FOR_VHAL);
                break;
            case CpmsState.SIMULATE_SLEEP:
            case CpmsState.SIMULATE_HIBERNATION:
                transitionAllowed = true;
                break;
            case CpmsState.WAIT_FOR_FINISH:
                transitionAllowed = (newState.mState == CpmsState.SUSPEND
                        || newState.mState == CpmsState.WAIT_FOR_VHAL);
                break;
            default:
                Slogf.e(TAG, "Unexpected current state: currentState=%s, newState=%s",
                        mCurrentState.name(), newState.name());
                transitionAllowed = true;
        }
        if (!transitionAllowed) {
            Slogf.e(TAG, "Requested power transition is not allowed: %s --> %s",
                    mCurrentState.name(), newState.name());
        }
        return transitionAllowed;
    }

    private void doHandleProcessingComplete() {
        int listenerState = CarPowerManager.STATE_SHUTDOWN_ENTER;
        synchronized (mLock) {
            clearWaitingForCompletion(/*clearQueue=*/false);
            boolean shutdownOnFinish = (mActionOnFinish == ACTION_ON_FINISH_SHUTDOWN);
            if (!shutdownOnFinish && mLastSleepEntryTime > mShutdownStartTime) {
                // entered sleep after processing start. So this could be duplicate request.
                Slogf.w(TAG, "Duplicate sleep entry request, ignore");
                return;
            }
            if (shutdownOnFinish) {
                listenerState = CarPowerManager.STATE_SHUTDOWN_ENTER;
            } else if (mActionOnFinish == ACTION_ON_FINISH_DEEP_SLEEP) {
                listenerState = CarPowerManager.STATE_SUSPEND_ENTER;
            } else if (mActionOnFinish == ACTION_ON_FINISH_HIBERNATION) {
                listenerState = CarPowerManager.STATE_HIBERNATION_ENTER;
            }
        }

        onApPowerStateChange(CpmsState.WAIT_FOR_FINISH, listenerState);
    }

    @Override
    public void onDisplayBrightnessChange(int brightness) {
        mHandler.handleDisplayBrightnessChange(Display.DEFAULT_DISPLAY, brightness);
    }

    @Override
    public void onDisplayBrightnessChange(int displayId, int brightness) {
        mHandler.handleDisplayBrightnessChange(displayId, brightness);
    }

    private void doHandleDisplayBrightnessChange(int displayId, int brightness) {
        mSystemInterface.setDisplayBrightness(displayId, brightness);
    }

    private void doHandleDisplayStateChange(int displayId, boolean on) {
        mScreenOffHandler.handleDisplayStateChange(displayId, on);
    }

    private void doHandlePowerPolicyNotification(String policyId) {
        // Sending notification of power policy change triggered through CarPowerManager API.
        notifyPowerPolicyChange(policyId, /* upToDaemon= */ true, /* force= */ false);
    }

    private void doHandlePowerPolicyNotification(CarPowerPolicy accumulatedPolicy) {
        notifyPowerPolicyChange(accumulatedPolicy);
    }

    /**
     * Handles when display changes.
     */
    public void handleDisplayChanged(int displayId, boolean on) {
        mHandler.handleDisplayStateChange(displayId, on);
    }

    /**
     * Gets display display power mode to turn on display.
     *
     * @return {@code false}, if the display power mode is OFF. Otherwise, {@code true}.
     */
    public boolean canTurnOnDisplay(int displayId) {
        return mScreenOffHandler.canTurnOnDisplay(displayId);
    }

    /**
     * Notifies that user activity has happened.
     */
    public void notifyUserActivity(int displayId, long eventTime) {
        mScreenOffHandler.updateUserActivity(displayId, eventTime);
    }

    @Override
    public void notifyUserActivity(int displayId) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CAR_POWER);
        notifyUserActivity(displayId, SystemClock.uptimeMillis());
    }

    /**
     * Sends display brightness to VHAL.
     * @param brightness value 0-100%
     */
    public void sendDisplayBrightness(int brightness) {
        mHal.sendDisplayBrightness(brightness);
    }

    /**
     * Sends display brightness to VHAL.
     * @param displayId the target display
     * @param brightness value 0-100%
     */
    public void sendDisplayBrightness(int displayId, int brightness) {
        mHal.sendDisplayBrightness(displayId, brightness);
    }

    /**
     * Gets the PowerHandler that we use to change power states
     */
    public Handler getHandler() {
        return mHandler;

    }

    /**
     * Registers power state change listeners running in CarService, which is not a binder
     * interfaces.
     */
    public void registerInternalListener(ICarPowerStateListener listener) {
        CarServiceUtils.assertCallingFromSystemProcessOrSelf();
        synchronized (mLock) {
            mInternalPowerListeners.add(listener);
        }
    }

    /**
     * Unregisters power state change listeners running in CarService, which is not a binder
     * interface.
     */
    public void unregisterInternalListener(ICarPowerStateListener listener) {
        CarServiceUtils.assertCallingFromSystemProcessOrSelf();
        boolean found = false;
        synchronized (mLock) {
            found = mInternalPowerListeners.remove(listener);
        }
        if (found) {
            removeListenerFromWaitingList(listener.asBinder());
        }
    }

    /**
     * Tells {@link CarPowerManagementService} that the listener running in CarService completes
     * handling power state change.
     */
    public void completeHandlingPowerStateChange(int state, ICarPowerStateListener listener) {
        CarServiceUtils.assertCallingFromSystemProcessOrSelf();
        handleListenerCompletion(state, listener,
                new ArraySet(new Integer[] {CarPowerManager.STATE_INVALID}));
    }

    // Binder interface for general use.
    // The listener is not required (or allowed) to call finished().
    @Override
    public void registerListener(ICarPowerStateListener listener) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CAR_POWER);
        mPowerManagerListeners.register(listener);
    }

    // Binder interface for Car services only.
    // After the listener completes its processing, it must call finished().
    @Override
    public void registerListenerWithCompletion(ICarPowerStateListener listener) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CONTROL_SHUTDOWN_PROCESS);

        mPowerManagerListenersWithCompletion.register(listener);
        // TODO: Need to send current state to newly registered listener? If so, need to handle
        //       completion for SHUTDOWN_PREPARE state
    }

    @Override
    public void unregisterListener(ICarPowerStateListener listener) {
        CarServiceUtils.assertAnyPermission(mContext, Car.PERMISSION_CAR_POWER,
                Car.PERMISSION_CONTROL_SHUTDOWN_PROCESS);
        doUnregisterListener(listener);
    }

    @Override
    public void requestShutdownOnNextSuspend() {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CAR_POWER);
        synchronized (mLock) {
            mShutdownOnNextSuspend = true;
        }
    }

    @Override
    public void finished(int state, ICarPowerStateListener listener) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CONTROL_SHUTDOWN_PROCESS);
        handleListenerCompletion(state, listener, new ArraySet(new Integer[]
                {CarPowerManager.STATE_INVALID, CarPowerManager.STATE_SHUTDOWN_PREPARE}));
    }

    @Override
    public void scheduleNextWakeupTime(int seconds) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CAR_POWER);
        if (seconds < 0) {
            Slogf.w(TAG, "Next wake up time is negative. Ignoring!");
            return;
        }
        boolean timedWakeupAllowed = mHal.isTimedWakeupAllowed();
        synchronized (mLock) {
            if (!timedWakeupAllowed) {
                Slogf.w(TAG, "Setting timed wakeups are disabled in HAL. Skipping");
                mNextWakeupSec = 0;
                return;
            }
            if (mNextWakeupSec == 0 || mNextWakeupSec > seconds) {
                // The new value is sooner than the old value. Take the new value.
                mNextWakeupSec = seconds;
            } else {
                Slogf.d(TAG, "Tried to schedule next wake up, but already had shorter "
                        + "scheduled time");
            }
        }
    }

    @Override
    public @CarPowerManager.CarPowerState int getPowerState() {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CAR_POWER);
        synchronized (mLock) {
            return (mCurrentState == null) ? CarPowerManager.STATE_INVALID
                    : mCurrentState.mCarPowerStateListenerState;
        }
    }

    /**
     * @see android.car.hardware.power.CarPowerManager#getCurrentPowerPolicy
     */
    @Override
    public CarPowerPolicy getCurrentPowerPolicy() {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_READ_CAR_POWER_POLICY);
        if (mFeatureFlags.carPowerPolicyRefactoring()) {
            synchronized (mLock) {
                return mCurrentAccumulatedPowerPolicy;
            }
        } else {
            return mPowerComponentHandler.getAccumulatedPolicy();
        }
    }

    /**
     * @see android.car.hardware.power.CarPowerManager#applyPowerPolicy
     */
    @Override
    public void applyPowerPolicy(String policyId) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CONTROL_CAR_POWER_POLICY);
        Preconditions.checkArgument(policyId != null, "policyId cannot be null");
        Preconditions.checkArgument(!policyId.startsWith(PolicyReader.SYSTEM_POWER_POLICY_PREFIX),
                "System power policy cannot be applied by apps");
        // notify daemon of power policy change not needed after policy refactor
        boolean upToDaemon = !mFeatureFlags.carPowerPolicyRefactoring();
        int status = applyPowerPolicy(policyId, /* delayNotification= */ true, upToDaemon,
                /* force= */ false);
        if (status != PolicyOperationStatus.OK) {
            throw new IllegalArgumentException(PolicyOperationStatus.errorCodeToString(status));
        }
    }

    /**
     * @see android.car.hardware.power.CarPowerManager#setPowerPolicyGroup
     */
    @Override
    public void setPowerPolicyGroup(String policyGroupId) throws RemoteException {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CONTROL_CAR_POWER_POLICY);
        Preconditions.checkArgument(policyGroupId != null, "policyGroupId cannot be null");
        if (mFeatureFlags.carPowerPolicyRefactoring()) {
            ICarPowerPolicyDelegate daemon;
            synchronized (mLock) {
                daemon = mRefactoredCarPowerPolicyDaemon;
            }
            try {
                daemon.setPowerPolicyGroup(policyGroupId);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Policy group ID is invalid");
            } catch (SecurityException e) {
                Slogf.e(TAG, e, "Failed to set power policy group, insufficient permissions");
            }
            synchronized (mLock) {
                mCurrentPowerPolicyGroupId = policyGroupId;
            }
        } else {
            int status = setCurrentPowerPolicyGroup(policyGroupId);
            if (status != PolicyOperationStatus.OK) {
                throw new IllegalArgumentException(PolicyOperationStatus.errorCodeToString(status));
            }
        }
    }

    @VisibleForTesting
    String getCurrentPowerPolicyGroupId() {
        synchronized (mLock) {
            return mCurrentPowerPolicyGroupId;
        }
    }

    @VisibleForTesting
    int getNumberOfCurrentPolicyRequests() {
        synchronized (mLock) {
            return mRequestIdToPolicyRequest.size();
        }
    }

    /**
     * @see android.car.hardware.power.CarPowerManager#addPowerPolicyListener
     */
    @Override
    public void addPowerPolicyListener(CarPowerPolicyFilter filter,
            ICarPowerPolicyListener listener) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_READ_CAR_POWER_POLICY);
        mPowerPolicyListeners.register(listener, filter);
    }

    /**
     * @see android.car.hardware.power.CarPowerManager#removePowerPolicyListener
     */
    @Override
    public void removePowerPolicyListener(ICarPowerPolicyListener listener) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_READ_CAR_POWER_POLICY);
        mPowerPolicyListeners.unregister(listener);
    }

    /**
     * @see android.car.hardware.power.CarPowerManager#setDisplayPowerState
     */
    @Override
    public void setDisplayPowerState(int displayId, boolean enable) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CAR_POWER);
        boolean isNotSelf = Binder.getCallingUid() != Process.myUid();
        CarOccupantZoneService occupantZoneService =
                CarLocalServices.getService(CarOccupantZoneService.class);
        long token = Binder.clearCallingIdentity();
        try {
            int driverDisplayId = occupantZoneService.getDisplayIdForDriver(
                    CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
            if (displayId == driverDisplayId && isNotSelf) {
                throw new UnsupportedOperationException("Driver display control is not supported");
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
        mSystemInterface.setDisplayState(displayId, enable);
    }

    // TODO(b/286303350): remove once power policy refactor complete; CPPD will handle power policy
    //                    change in response to silent mode status changes
    void notifySilentModeChange(boolean silent) {
        Slogf.i(TAG, "Silent mode is set to %b", silent);
        if (silent) {
            applyPreemptivePowerPolicy(PolicyReader.POWER_POLICY_ID_NO_USER_INTERACTION);
        } else {
            cancelPreemptivePowerPolicy();
        }
    }

    private void handleListenerCompletion(int state, ICarPowerStateListener listener,
            ArraySet<Integer> notAllowedStates) {
        synchronized (mLock) {
            if (notAllowedStates.contains(mStateForCompletion)) {
                Slogf.w(TAG, "The current state(%d) doesn't allow listener completion",
                        mStateForCompletion);
                return;
            }
            if (state != mStateForCompletion) {
                Slogf.w(TAG, "Given state(%d) doesn't match the current state(%d) for completion",
                        state, mStateForCompletion);
                return;
            }
        }
        removeListenerFromWaitingList(listener.asBinder());
    }


    private void doUnregisterListener(ICarPowerStateListener listener) {
        mPowerManagerListeners.unregister(listener);
        boolean found = mPowerManagerListenersWithCompletion.unregister(listener);
        if (found) {
            // Remove this from the completion list (if it's there)
            removeListenerFromWaitingList(listener.asBinder());
        }
    }

    private void removeListenerFromWaitingList(IBinder binderListener) {
        synchronized (mLock) {
            mListenersWeAreWaitingFor.remove(binderListener);
        }
        // Signals a thread to check if all listeners complete.
        mListenerCompletionSem.release();
    }

    private void finishShutdownPrepare() {
        boolean shouldHandleProcessingComplete = false;
        synchronized (mLock) {
            if (mCurrentState != null
                    && (mCurrentState.mState == CpmsState.SHUTDOWN_PREPARE
                            || mCurrentState.mState == CpmsState.SIMULATE_SLEEP
                            || mCurrentState.mState == CpmsState.SIMULATE_HIBERNATION)) {
                // All apps are ready to shutdown/suspend.
                if (mActionOnFinish != ACTION_ON_FINISH_SHUTDOWN) {
                    if (mLastSleepEntryTime > mShutdownStartTime
                            && mLastSleepEntryTime < SystemClock.elapsedRealtime()) {
                        Slogf.d(TAG, "finishShutdownPrepare: Already slept!");
                        return;
                    }
                }
                shouldHandleProcessingComplete = true;
            }
        }

        if (shouldHandleProcessingComplete) {
            Slogf.i(TAG, "Apps are finished, call handleProcessingComplete()");
            mHandler.handleProcessingComplete();
        }
    }

    /**
     * Converts a given power policy from power policy type returned by the car power policy daemon
     * to the one used within car service.
     * @param policy of type android.frameworks.automotive.powerpolicy.CarPowerPolicy
     * @return policy converted to type android.car.hardware.power.CarPowerPolicy
     */
    private CarPowerPolicy convertPowerPolicyFromDaemon(
            android.frameworks.automotive.powerpolicy.CarPowerPolicy policy) {
        return new CarPowerPolicy(policy.policyId, policy.enabledComponents,
                policy.disabledComponents);
    }

    private final class PowerPolicyCallback extends ICarPowerPolicyDelegateCallback.Stub {
        @Override
        public void updatePowerComponents(
                android.frameworks.automotive.powerpolicy.CarPowerPolicy policy) {
            mPowerComponentHandler.applyPowerPolicy(convertPowerPolicyFromDaemon(policy));
        }

        @Override
        public void onApplyPowerPolicySucceeded(int requestId,
                android.frameworks.automotive.powerpolicy.CarPowerPolicy accumulatedPolicy) {
            Slogf.i(TAG, "onApplyPowerPolicySucceeded: requestId = %d, policyId = %s", requestId,
                    accumulatedPolicy.policyId);
            AsyncPolicyRequest policyRequest;
            synchronized (mLock) {
                policyRequest = mRequestIdToPolicyRequest.get(requestId);
            }
            if (policyRequest == null) {
                Slogf.e(TAG, "No power policy request exists for request ID %d", requestId);
                return;
            }
            CarPowerPolicy accumulatedPowerPolicy = convertPowerPolicyFromDaemon(accumulatedPolicy);
            policyRequest.onPolicyRequestSucceeded(accumulatedPowerPolicy);
        }

        @Override
        public void onApplyPowerPolicyFailed(int requestId, int reason) {
            Slogf.i(TAG, "onApplyPowerPolicyFailed: requestId = %d, reason = %d", requestId,
                    reason);
            AsyncPolicyRequest policyRequest;
            synchronized (mLock) {
                policyRequest = mRequestIdToPolicyRequest.get(requestId);
            }
            if (policyRequest == null) {
                Slogf.e(TAG, "No power policy request exists for request ID %d", requestId);
                return;
            }
            policyRequest.onPolicyRequestFailed(reason);
        }

        @Override
        public void onPowerPolicyChanged(
                android.frameworks.automotive.powerpolicy.CarPowerPolicy accumulatedPolicy) {
            CarPowerPolicy currentAccumulatedPolicy =
                    convertPowerPolicyFromDaemon(accumulatedPolicy);
            updateCurrentPowerPolicy(currentAccumulatedPolicy);
            String policyId = accumulatedPolicy.policyId;
            Slogf.i(TAG, "Queueing power policy notification (ID: %s) in the handler", policyId);
            mHandler.handlePowerPolicyNotification(currentAccumulatedPolicy);
        }

        @Override
        public String getInterfaceHash() {
            return ICarPowerPolicyDelegateCallback.HASH;
        }

        @Override
        public int getInterfaceVersion() {
            return ICarPowerPolicyDelegateCallback.VERSION;
        }
    }

    private static CarPowerPolicy getInitialAccumulatedPowerPolicy() {
        String initialPolicyId = "";
        int[] enabledComponents = new int[0];
        int[] disabledComponents = new int[LAST_POWER_COMPONENT - FIRST_POWER_COMPONENT + 1];
        int disabledIndex = 0;
        for (int component = FIRST_POWER_COMPONENT; component <= LAST_POWER_COMPONENT;
                component++) {
            disabledComponents[disabledIndex++] = component;
        }
        return new CarPowerPolicy(initialPolicyId, enabledComponents, disabledComponents);
    }

    private void initializeRegisteredPowerPolicies(
            android.frameworks.automotive.powerpolicy.CarPowerPolicy[] policies) {
        for (int i = 0; i < policies.length; i++) {
            CarPowerPolicy policy = convertPowerPolicyFromDaemon(policies[i]);
            List<String> enabledComponents = PowerComponentUtil.powerComponentsToStrings(
                    Lists.asImmutableList(policy.getEnabledComponents()));
            List<String> disabledComponents = PowerComponentUtil.powerComponentsToStrings(
                    Lists.asImmutableList(policy.getDisabledComponents()));
            mPolicyReader.definePowerPolicy(policy.getPolicyId(),
                    enabledComponents.toArray(String[]::new),
                    disabledComponents.toArray(String[]::new));
        }
    }

    @VisibleForTesting
    public void initializePowerPolicy() {
        if (mFeatureFlags.carPowerPolicyRefactoring()) {
            ICarPowerPolicyDelegate daemon;
            synchronized (mLock) {
                daemon = mRefactoredCarPowerPolicyDaemon;
            }
            PowerPolicyInitData powerPolicyInitData;
            if (daemon != null) {
                try {
                    PowerPolicyCallback powerPolicyCallback = new PowerPolicyCallback();
                    powerPolicyInitData = daemon.notifyCarServiceReady(powerPolicyCallback);
                } catch (RemoteException e) {
                    Slogf.e(TAG, e, "Failed to tell car power policy daemon that CarService is"
                            + " ready");
                    return;
                }
            } else {
                Slogf.w(TAG, "Failed to notify car service is ready, car power policy daemon"
                                + " is not available");
                return;
            }
            mReadyForCallback.set(true);
            int[] registeredCustomComponents = powerPolicyInitData.registeredCustomComponents;
            Integer[] customComponents = new Integer[registeredCustomComponents.length];
            for (int i = 0; i < customComponents.length; i++) {
                customComponents[i] = registeredCustomComponents[i];
            }
            mPowerComponentHandler.registerCustomComponents(customComponents);
            initializeRegisteredPowerPolicies(powerPolicyInitData.registeredPolicies);
            CarPowerPolicy currentPowerPolicy = convertPowerPolicyFromDaemon(
                    powerPolicyInitData.currentPowerPolicy);
            updateCurrentPowerPolicy(currentPowerPolicy);
            mPowerComponentHandler.applyPowerPolicy(currentPowerPolicy);
            notifyPowerPolicyChange(currentPowerPolicy);
            // To cover the case where power state changed before connecting to CPPD.
            notifyPowerStateChangeToDaemon(daemon, getPowerState());
        } else {
            Slogf.i(TAG, "CPMS is taking control from carpowerpolicyd");
            ICarPowerPolicySystemNotification daemon;
            synchronized (mLock) {
                daemon = mCarPowerPolicyDaemon;
            }
            PolicyState state;
            if (daemon != null) {
                try {
                    state = daemon.notifyCarServiceReady();
                } catch (RemoteException e) {
                    Slogf.e(TAG, e,
                            "Failed to tell car power policy daemon that CarService is ready");
                    return;
                }
            } else {
                Slogf.w(TAG,
                        "Failed to notify car service is ready. car power policy daemon is not "
                                + "available");
                return;
            }

            String currentPowerPolicyId;
            String currentPolicyGroupId;
            synchronized (mLock) {
                mHasControlOverDaemon = true;
                currentPowerPolicyId = mCurrentPowerPolicyId;
                currentPolicyGroupId = mCurrentPowerPolicyGroupId;
            }
            // If the current power policy or the policy group has been modified by CPMS, we ignore
            // the power policy or the policy group passed from car power policy daemon, and
            // notifies the current power policy to the daemon.
            if (currentPowerPolicyId == null || currentPowerPolicyId.isEmpty()) {
                Slogf.i(TAG, "Attempting to apply the power policy(%s) from the daemon",
                        state.policyId);
                int status = applyPowerPolicy(state.policyId, /* delayNotification= */ false,
                        /* upToDaemon= */ false, /* force= */ false);
                if (status != PolicyOperationStatus.OK) {
                    Slogf.w(TAG, PolicyOperationStatus.errorCodeToString(status));
                }
            } else {
                Slogf.i(TAG,
                        "CPMS applied power policy(%s) before connecting to the daemon. Notifying "
                                + "to the daemon...", currentPowerPolicyId);
                notifyPowerPolicyChangeToDaemon(currentPowerPolicyId, /* force= */ true);
            }
            if (currentPolicyGroupId == null || currentPolicyGroupId.isEmpty()) {
                int status = setCurrentPowerPolicyGroup(state.policyGroupId);
                if (status != PolicyOperationStatus.OK) {
                    Slogf.w(TAG, PolicyOperationStatus.errorCodeToString(status));
                }
            }
        }
        mSilentModeHandler.init();
    }

    @PolicyOperationStatus.ErrorCode
    private int setCurrentPowerPolicyGroup(String policyGroupId) {
        if (!mPolicyReader.isPowerPolicyGroupAvailable(policyGroupId)) {
            int error = PolicyOperationStatus.ERROR_SET_POWER_POLICY_GROUP;
            Slogf.w(TAG, PolicyOperationStatus.errorCodeToString(error,
                    policyGroupId + " is not registered"));
            return error;
        }
        synchronized (mLock) {
            mCurrentPowerPolicyGroupId = policyGroupId;
        }
        return PolicyOperationStatus.OK;
    }

    private static final class AsyncPolicyRequest {
        private final Object mLock = new Object();
        private final int mRequestId;
        private final long mTimeoutMs;
        private final CountDownLatch mPolicyRequestLatch = new CountDownLatch(1);
        @GuardedBy("mLock")
        private boolean mPolicyRequestSucceeded;
        @GuardedBy("mLock")
        private CarPowerPolicy mAccumulatedPolicy;
        @GuardedBy("mLock")
        @PowerPolicyFailureReason
        private int mFailureReason;

        AsyncPolicyRequest(int requestId, long timeoutMs) {
            mRequestId = requestId;
            mTimeoutMs = timeoutMs;
        }

        public int getRequestId() {
            return mRequestId;
        }

        public boolean isSuccessful() {
            synchronized (mLock) {
                return mPolicyRequestSucceeded;
            }
        }

        public CarPowerPolicy getAccumulatedPolicy() {
            synchronized (mLock) {
                return mAccumulatedPolicy;
            }
        }

        @PowerPolicyFailureReason
        public int getFailureReason() {
            synchronized (mLock) {
                return mFailureReason;
            }
        }

        public boolean await() throws InterruptedException {
            return mPolicyRequestLatch.await(mTimeoutMs, TimeUnit.MILLISECONDS);
        }

        public void onPolicyRequestSucceeded(CarPowerPolicy accumulatedPolicy) {
            synchronized (mLock) {
                mPolicyRequestSucceeded = true;
                mAccumulatedPolicy = accumulatedPolicy;
            }
            mPolicyRequestLatch.countDown();
        }

        public void onPolicyRequestFailed(@PowerPolicyFailureReason int reason) {
            synchronized (mLock) {
                mFailureReason = reason;
            }
            mPolicyRequestLatch.countDown();
        }
    }

    private AsyncPolicyRequest generateAsyncPolicyRequest(long timeoutMs) {
        int requestId = mPolicyRequestIdCounter.getAndIncrement();
        return new AsyncPolicyRequest(requestId, timeoutMs);
    }

    @PolicyOperationStatus.ErrorCode
    private int getPolicyRequestError(int requestId, @PowerPolicyFailureReason int reason) {
        switch(reason) {
            case PowerPolicyFailureReason.POWER_POLICY_FAILURE_UNKNOWN:
                Slogf.w(TAG, "Power policy request %d failed for unknown reason",
                        requestId);
                return PolicyOperationStatus.ERROR_APPLY_POWER_POLICY;
            case PowerPolicyFailureReason.POWER_POLICY_FAILURE_NOT_REGISTERED_ID:
                Slogf.w(TAG, "Power policy request %d failed due to unregistered"
                        + "power policy ID", requestId);
                return PolicyOperationStatus.ERROR_NOT_REGISTERED_POWER_POLICY_ID;
            case PowerPolicyFailureReason.POWER_POLICY_FAILURE_CANNOT_OVERRIDE:
                Slogf.w(TAG, "Power policy request %d failed because current non-"
                        + "preemptive power policy cannot be overridden", requestId);
                return PolicyOperationStatus.ERROR_APPLY_POWER_POLICY;
            default:
                Slogf.w(TAG, "Reason for power policy request %d failing is "
                        + "undefined", requestId);
                return PolicyOperationStatus.ERROR_APPLY_POWER_POLICY;
        }
    }

    @PolicyOperationStatus.ErrorCode
    private int applyPowerPolicy(@Nullable String policyId, boolean delayNotification,
            boolean upToDaemon, boolean force) {
        if (mFeatureFlags.carPowerPolicyRefactoring()) {
            AsyncPolicyRequest request = generateAsyncPolicyRequest(
                    DEFAULT_POWER_POLICY_REQUEST_TIMEOUT_MS);
            int requestId = request.getRequestId();
            ICarPowerPolicyDelegate daemon;
            synchronized (mLock) {
                daemon = mRefactoredCarPowerPolicyDaemon;
                mRequestIdToPolicyRequest.put(requestId, request);
            }
            if (daemon == null) {
                Slogf.w(TAG, "Cannot call applyPowerPolicyAsync(requestId=%d, policyId=%s) to CPPD:"
                        + " CPPD is not available", requestId, policyId);
                return PolicyOperationStatus.ERROR_APPLY_POWER_POLICY;
            }
            if (!mReadyForCallback.get()) {
                Slogf.w(TAG, "Cannot call applyPowerPolicyAsync(requestId=%d, policyId=%s) to CPPD:"
                        + " not ready for calling to CPPD", requestId, policyId);
                return PolicyOperationStatus.ERROR_APPLY_POWER_POLICY;
            }
            try {
                Slogf.i(TAG, "Request(%d) of applying power policy(%s) to CPPD in async", requestId,
                        policyId);
                daemon.applyPowerPolicyAsync(requestId, policyId, force);
                boolean policyRequestServed = request.await();
                if (!policyRequestServed) {
                    Slogf.w(TAG, "Power policy request (ID: %d) successful application timed out"
                            + " after %d ms", requestId, DEFAULT_POWER_POLICY_REQUEST_TIMEOUT_MS);
                    return PolicyOperationStatus.ERROR_APPLY_POWER_POLICY;
                }
            } catch (IllegalArgumentException e) {
                int error = PolicyOperationStatus.ERROR_INVALID_POWER_POLICY_ID;
                Slogf.w(TAG, PolicyOperationStatus.errorCodeToString(error, policyId));
                return error;
            } catch (IllegalStateException e) {
                int error = PolicyOperationStatus.ERROR_APPLY_POWER_POLICY;
                Slogf.w(TAG, PolicyOperationStatus.errorCodeToString(error, policyId));
                return error;
            } catch (SecurityException e) {
                Slogf.w(TAG, e, "Failed to apply power policy, insufficient permissions");
                return PolicyOperationStatus.ERROR_APPLY_POWER_POLICY;
            } catch (InterruptedException e) {
                Slogf.w(TAG, e, "Wait for power policy change request interrupted");
                Thread.currentThread().interrupt();
                return PolicyOperationStatus.ERROR_APPLY_POWER_POLICY;
            } catch (RemoteException e) {
                Slogf.w(TAG, e, "Failed to apply power policy, connection issue");
                return PolicyOperationStatus.ERROR_APPLY_POWER_POLICY;
            } finally {
                synchronized (mLock) {
                    mRequestIdToPolicyRequest.remove(requestId);
                }
            }
            if (!request.isSuccessful()) {
                Slogf.w(TAG, "Failed to apply power policy, failure reason = %d",
                        request.getFailureReason());
                return getPolicyRequestError(requestId, request.getFailureReason());
            }
            CarPowerPolicy accumulatedPolicy = request.getAccumulatedPolicy();
            updateCurrentPowerPolicy(accumulatedPolicy);
            if (delayNotification) {
                Slogf.d(TAG,
                        "Queueing power policy notification (id: %s) in the handler", policyId);
                mHandler.handlePowerPolicyNotification(accumulatedPolicy);
            } else {
                notifyPowerPolicyChange(accumulatedPolicy);
            }
        } else {
            CarPowerPolicy policy = mPolicyReader.getPowerPolicy(policyId);
            if (policy == null) {
                int error = PolicyOperationStatus.ERROR_NOT_REGISTERED_POWER_POLICY_ID;
                Slogf.w(TAG, PolicyOperationStatus.errorCodeToString(error, policyId));
                return error;
            }
            synchronized (mLock) {
                if (mIsPowerPolicyLocked) {
                    Slogf.i(TAG, "Power policy is locked. The request policy(%s) will be applied "
                            + "when power policy becomes unlocked", policyId);
                    mPendingPowerPolicyId = policyId;
                    return PolicyOperationStatus.OK;
                }
                mCurrentPowerPolicyId = policyId;
            }
            mPowerComponentHandler.applyPowerPolicy(policy);
            if (delayNotification) {
                Slogf.d(TAG,
                        "Queueing power policy notification (id: %s) in the handler", policyId);
                mHandler.handlePowerPolicyNotification(policyId);
            } else {
                notifyPowerPolicyChange(policyId, upToDaemon, force);
            }
        }
        Slogf.i(TAG, "The current power policy is %s", policyId);
        return PolicyOperationStatus.OK;
    }

    @PolicyOperationStatus.ErrorCode
    private int applyPreemptivePowerPolicy(String policyId) {
        CarPowerPolicy policy = mPolicyReader.getPreemptivePowerPolicy(policyId);
        if (policy == null) {
            int error = PolicyOperationStatus.ERROR_NOT_REGISTERED_POWER_POLICY_ID;
            Slogf.w(TAG, PolicyOperationStatus.errorCodeToString(error, policyId));
            return error;
        }
        synchronized (mLock) {
            mIsPowerPolicyLocked = true;
            if (!mPolicyReader.isPreemptivePowerPolicy(mCurrentPowerPolicyId)) {
                mPendingPowerPolicyId = mCurrentPowerPolicyId;
            }
            mCurrentPowerPolicyId = policyId;
        }
        mPowerComponentHandler.applyPowerPolicy(policy);
        notifyPowerPolicyChange(policyId, /* upToDaemon= */ true, /* force= */ true);
        Slogf.i(TAG, "The current power policy is %s", policyId);
        return PolicyOperationStatus.OK;
    }

    private void cancelPreemptivePowerPolicy() {
        String policyId;
        synchronized (mLock) {
            if (!mIsPowerPolicyLocked) {
                Slogf.w(TAG, "Failed to cancel system power policy: the current policy is not the "
                        + "system power policy");
                return;
            }
            mIsPowerPolicyLocked = false;
            policyId = mPendingPowerPolicyId;
            mPendingPowerPolicyId = null;
        }
        if (policyId != null) { // Pending policy exist
            int status = applyPowerPolicy(policyId, /* delayNotification= */ false,
                    /* upToDaemon= */ true, /* force= */ true);
            if (status != PolicyOperationStatus.OK) {
                Slogf.w(TAG, "Failed to cancel system power policy: %s",
                        PolicyOperationStatus.errorCodeToString(status));
            }
        } else {
            Slogf.w(TAG, "cancelPreemptivePowerPolicy(), no pending power policy");
        }
    }

    private void notifyPowerPolicyChangeToDaemon(String policyId, boolean force) {
        ICarPowerPolicySystemNotification daemon;
        synchronized (mLock) {
            daemon = mCarPowerPolicyDaemon;
            if (daemon == null) {
                Slogf.e(TAG, "Failed to notify car power policy daemon: the daemon is not ready");
                return;
            }
            if (!mHasControlOverDaemon) {
                Slogf.w(TAG, "Notifying policy change is deferred: CPMS has not yet taken control");
                return;
            }
        }
        try {
            daemon.notifyPowerPolicyChange(policyId, force);
        } catch (RemoteException | IllegalStateException e) {
            Slogf.e(TAG, e, "Failed to notify car power policy daemon of a new power policy(%s)",
                    policyId);
        }
    }

    // TODO(b/286303350): remove after power policy refactor is complete - will only use version
    //                    that takes 'accumulatedPowerPolicy' as input
    private void notifyPowerPolicyChange(String policyId, boolean upToDaemon, boolean force) {
        // Notify system clients
        if (upToDaemon) {
            notifyPowerPolicyChangeToDaemon(policyId, force);
        }
        // Notify Java clients
        CarPowerPolicy appliedPolicy = mPolicyReader.isPreemptivePowerPolicy(policyId)
                ? mPolicyReader.getPreemptivePowerPolicy(policyId)
                : mPolicyReader.getPowerPolicy(policyId);
        CarPowerPolicy accumulatedPolicy = mPowerComponentHandler.getAccumulatedPolicy();
        notifyPowerPolicyChange(policyId, appliedPolicy, accumulatedPolicy);
    }

    private void notifyPowerPolicyChange(CarPowerPolicy accumulatedPowerPolicy) {
        String policyId = accumulatedPowerPolicy.getPolicyId();
        CarPowerPolicy appliedPolicy = mPolicyReader.getPowerPolicy(policyId);
        notifyPowerPolicyChange(policyId, appliedPolicy, accumulatedPowerPolicy);
        Slogf.i(TAG, "Power policy change to %s is notified to apps", policyId);
    }

    // TODO(b/286303350): after power policy refactor is complete, remove this function and replace
    //                    the inner call to it by notifyPowerPolicyChange(accumulatedPowerPolicy)
    //                    with this function's code contents
    private void notifyPowerPolicyChange(String policyId, CarPowerPolicy appliedPolicy,
            CarPowerPolicy accumulatedPolicy) {
        EventLogHelper.writePowerPolicyChange(policyId);
        if (appliedPolicy == null) {
            Slogf.wtf(TAG, "The new power policy(%s) should exist", policyId);
        }
        mBroadcastHandler.post(() -> {
            int idx = mPowerPolicyListeners.beginBroadcast();

            while (idx-- > 0) {
                ICarPowerPolicyListener listener = mPowerPolicyListeners.getBroadcastItem(idx);
                CarPowerPolicyFilter filter =
                        (CarPowerPolicyFilter) mPowerPolicyListeners.getBroadcastCookie(idx);
                if (!mPowerComponentHandler.isComponentChanged(filter)) {
                    continue;
                }
                try {
                    listener.onPolicyChanged(appliedPolicy, accumulatedPolicy);
                } catch (RemoteException e) {
                    // It's likely the connection snapped. Let binder death handle the situation.
                    Slogf.e(TAG, e, "onPolicyChanged() call failed: policyId = %s", policyId);
                }
            }
            mPowerPolicyListeners.finishBroadcast();
        });
    }

    private void makeSureNoUserInteraction() {
        mSilentModeHandler.updateKernelSilentMode(true);
        int status;
        if (mFeatureFlags.carPowerPolicyRefactoring()) {
            status = applyPowerPolicy(PolicyReader.POWER_POLICY_ID_NO_USER_INTERACTION,
                    /* delayNotification= */ false, /* upToDaemon= */ false, /* force= */ true);
        } else {
            status = applyPreemptivePowerPolicy(
                    PolicyReader.POWER_POLICY_ID_NO_USER_INTERACTION);
        }
        if (status != PolicyOperationStatus.OK) {
            Slogf.w(TAG, PolicyOperationStatus.errorCodeToString(status));
        }
    }

    @GuardedBy("mLock")
    private android.os.IInterface getPowerPolicyDaemonLocked() {
        if (mFeatureFlags.carPowerPolicyRefactoring()) {
            return mRefactoredCarPowerPolicyDaemon;
        } else {
            return mCarPowerPolicyDaemon;
        }
    }

    private void connectToPowerPolicyDaemon() {
        synchronized (mLock) {
            android.os.IInterface powerPolicyDaemon = getPowerPolicyDaemonLocked();
            if (powerPolicyDaemon != null || mConnectionInProgress) {
                return;
            }
            mConnectionInProgress = true;
        }
        connectToDaemonHelper(CAR_POWER_POLICY_DAEMON_BIND_MAX_RETRY);
    }

    private void connectToDaemonHelper(int retryCount) {
        if (retryCount <= 0) {
            synchronized (mLock) {
                mConnectionInProgress = false;
            }
            Slogf.e(TAG, "Cannot reconnect to car power policyd daemon after retrying %d times",
                    CAR_POWER_POLICY_DAEMON_BIND_MAX_RETRY);
            return;
        }
        if (makeBinderConnection()) {
            Slogf.i(TAG, "Connected to car power policy daemon");
            initializePowerPolicy();
            return;
        }
        final int numRetry = retryCount - 1;
        mHandler.postDelayed(() -> connectToDaemonHelper(numRetry),
                CAR_POWER_POLICY_DAEMON_BIND_RETRY_INTERVAL_MS);
    }

    private boolean makeBinderConnection() {
        long currentTimeMs = SystemClock.uptimeMillis();
        IBinder binder;
        if (mFeatureFlags.carPowerPolicyRefactoring()) {
            binder = ServiceManagerHelper.getService(REFACTORED_CAR_POWER_POLICY_DAEMON_INTERFACE);
        } else {
            binder = ServiceManagerHelper.getService(CAR_POWER_POLICY_DAEMON_INTERFACE);
        }
        if (binder == null) {
            Slogf.w(TAG, "Finding car power policy daemon failed. Power policy management is not "
                    + "supported");
            return false;
        }
        long elapsedTimeMs = SystemClock.uptimeMillis() - currentTimeMs;
        if (elapsedTimeMs > CAR_POWER_POLICY_DAEMON_FIND_MARGINAL_TIME_MS) {
            Slogf.wtf(TAG, "Finding car power policy daemon took too long(%dms)", elapsedTimeMs);
        }

        if (mFeatureFlags.carPowerPolicyRefactoring()) {
            ICarPowerPolicyDelegate daemon = ICarPowerPolicyDelegate.Stub.asInterface(binder);
            if (daemon == null) {
                Slogf.w(TAG, "Getting car power policy daemon interface failed. Power policy "
                        + "management is not supported");
                return false;
            }
            synchronized (mLock) {
                mRefactoredCarPowerPolicyDaemon = daemon;
                mConnectionInProgress = false;
            }
            mBinderHandler = new BinderHandler(daemon);
        } else {
            ICarPowerPolicySystemNotification daemon =
                    ICarPowerPolicySystemNotification.Stub.asInterface(binder);
            if (daemon == null) {
                Slogf.w(TAG, "Getting car power policy daemon interface failed. Power policy "
                        + "management is not supported");
                return false;
            }
            synchronized (mLock) {
                mCarPowerPolicyDaemon = daemon;
                mConnectionInProgress = false;
            }
            mBinderHandler = new BinderHandler(daemon);
        }
        mBinderHandler.linkToDeath();
        return true;
    }

    private void updateCurrentPowerPolicy(CarPowerPolicy accumulatedPolicy) {
        synchronized (mLock) {
            mCurrentPowerPolicyId = accumulatedPolicy.getPolicyId();
            mCurrentAccumulatedPowerPolicy = accumulatedPolicy;
        }
    }

    private final class BinderHandler implements IBinder.DeathRecipient {
        // TODO(b/286303350): replace with refactored daemon once power policy refactor is complete
        private ICarPowerPolicySystemNotification mDaemon;
        private ICarPowerPolicyDelegate mRefactoredDaemon;

        private BinderHandler(ICarPowerPolicySystemNotification daemon) {
            mDaemon = daemon;
        }

        private BinderHandler(ICarPowerPolicyDelegate daemon) {
            mRefactoredDaemon = daemon;
        }

        @Override
        public void binderDied() {
            Slogf.w(TAG, "Car power policy daemon died: reconnecting");
            unlinkToDeath();
            if (mFeatureFlags.carPowerPolicyRefactoring()) {
                mRefactoredDaemon = null;
            } else {
                mDaemon = null;
            }
            mReadyForCallback.set(false);
            synchronized (mLock) {
                if (mFeatureFlags.carPowerPolicyRefactoring()) {
                    mRefactoredCarPowerPolicyDaemon = null;
                } else {
                    mCarPowerPolicyDaemon = null;
                    mHasControlOverDaemon = false;
                }
            }
            mHandler.postDelayed(
                    () -> connectToDaemonHelper(CAR_POWER_POLICY_DAEMON_BIND_MAX_RETRY),
                    CAR_POWER_POLICY_DAEMON_BIND_RETRY_INTERVAL_MS);
        }

        private void linkToDeath() {
            if (mFeatureFlags.carPowerPolicyRefactoring()) {
                if (mRefactoredDaemon == null) {
                    return;
                }
            } else {
                if (mDaemon == null) {
                    return;
                }
            }
            IBinder binder;
            if (mFeatureFlags.carPowerPolicyRefactoring()) {
                binder = mRefactoredDaemon.asBinder();
            } else {
                binder = mDaemon.asBinder();
            }
            if (binder == null) {
                Slogf.w(TAG, "Linking to binder death recipient skipped");
                return;
            }
            try {
                binder.linkToDeath(this, 0);
            } catch (RemoteException e) {
                if (mFeatureFlags.carPowerPolicyRefactoring()) {
                    mRefactoredDaemon = null;
                } else {
                    mDaemon = null;
                }
                Slogf.w(TAG, e, "Linking to binder death recipient failed: %s");
            }
        }

        private void unlinkToDeath() {
            if (mFeatureFlags.carPowerPolicyRefactoring()) {
                if (mRefactoredDaemon == null) {
                    return;
                }
            } else {
                if (mDaemon == null) {
                    return;
                }
            }
            IBinder binder;
            if (mFeatureFlags.carPowerPolicyRefactoring()) {
                binder = mRefactoredDaemon.asBinder();
            } else {
                binder = mDaemon.asBinder();
            }
            if (binder == null) {
                Slogf.w(TAG, "Unlinking from binder death recipient skipped");
                return;
            }
            binder.unlinkToDeath(this, 0);
        }
    }

    private final class PowerHandler extends Handler {
        private static final String TAG = PowerHandler.class.getSimpleName();
        private static final int MSG_POWER_STATE_CHANGE = 0;
        private static final int MSG_DISPLAY_BRIGHTNESS_CHANGE = 1;
        private static final int MSG_DISPLAY_STATE_CHANGE = 2;
        private static final int MSG_PROCESSING_COMPLETE = 3;
        private static final int MSG_POWER_POLICY_NOTIFICATION = 4;

        // Do not handle this immediately but with some delay as there can be a race between
        // display off due to rear view camera and delivery to here.
        private static final long MAIN_DISPLAY_EVENT_DELAY_MS = 500;

        private final WeakReference<CarPowerManagementService> mService;

        private PowerHandler(Looper looper, CarPowerManagementService service) {
            super(looper);
            mService = new WeakReference<CarPowerManagementService>(service);
        }

        private void handlePowerStateChange() {
            Message msg = obtainMessage(MSG_POWER_STATE_CHANGE);
            sendMessage(msg);
        }

        private void handleDisplayBrightnessChange(int displayId, int brightness) {
            Message msg = obtainMessage(MSG_DISPLAY_BRIGHTNESS_CHANGE, displayId, brightness);
            sendMessage(msg);
        }

        private void handleDisplayStateChange(int displayId, boolean on) {
            HandlerHelper.removeEqualMessages(this, MSG_DISPLAY_STATE_CHANGE, displayId);
            Message msg = obtainMessage(MSG_DISPLAY_STATE_CHANGE, displayId);
            msg.arg1 = on ? Display.STATE_ON : Display.STATE_OFF;
            sendMessageDelayed(msg, MAIN_DISPLAY_EVENT_DELAY_MS);
        }

        private void handleProcessingComplete() {
            removeMessages(MSG_PROCESSING_COMPLETE);
            Message msg = obtainMessage(MSG_PROCESSING_COMPLETE);
            sendMessage(msg);
        }

        private void cancelProcessingComplete() {
            removeMessages(MSG_PROCESSING_COMPLETE);
        }

        private void handlePowerPolicyNotification(String policyId) {
            Message msg = obtainMessage(MSG_POWER_POLICY_NOTIFICATION, policyId);
            sendMessage(msg);
        }

        private void handlePowerPolicyNotification(CarPowerPolicy accumulatedPolicy) {
            Message msg = obtainMessage(MSG_POWER_POLICY_NOTIFICATION, accumulatedPolicy);
            sendMessage(msg);
        }

        private void cancelAll() {
            removeMessages(MSG_POWER_STATE_CHANGE);
            removeMessages(MSG_DISPLAY_BRIGHTNESS_CHANGE);
            removeMessages(MSG_DISPLAY_STATE_CHANGE);
            removeMessages(MSG_PROCESSING_COMPLETE);
            removeMessages(MSG_POWER_POLICY_NOTIFICATION);
        }

        @Override
        public void handleMessage(Message msg) {
            CarPowerManagementService service = mService.get();
            if (service == null) {
                Slogf.i(TAG, "handleMessage null service");
                return;
            }
            switch (msg.what) {
                case MSG_POWER_STATE_CHANGE:
                    service.doHandlePowerStateChange();
                    break;
                case MSG_DISPLAY_BRIGHTNESS_CHANGE:
                    service.doHandleDisplayBrightnessChange(
                            /* displayId= */ msg.arg1, /* brightness= */ msg.arg2);
                    break;
                case MSG_DISPLAY_STATE_CHANGE:
                    int displayId = (Integer) msg.obj;
                    boolean on = msg.arg1 == Display.STATE_ON;
                    service.doHandleDisplayStateChange(displayId, on);
                    break;
                case MSG_PROCESSING_COMPLETE:
                    service.doHandleProcessingComplete();
                    break;
                case MSG_POWER_POLICY_NOTIFICATION:
                    if (mFeatureFlags.carPowerPolicyRefactoring()) {
                        service.doHandlePowerPolicyNotification((CarPowerPolicy) msg.obj);
                    } else {
                        service.doHandlePowerPolicyNotification((String) msg.obj);
                    }
                    break;
                default:
                    Slogf.w(TAG, "handleMessage invalid message type: %d", msg.what);
                    break;
            }
        }
    }

    // Send the command to enter Suspend to RAM.
    // If the command is not successful, try again with an exponential back-off.
    // If it fails repeatedly, send the command to shut down.
    // If we decide to go to a different power state, abort this retry mechanism.
    // Returns true if we successfully suspended.
    private boolean suspendWithRetries() {
        boolean isSuspendToDisk;
        synchronized (mLock) {
            isSuspendToDisk = mActionOnFinish == ACTION_ON_FINISH_HIBERNATION;
        }

        String suspendTarget = isSuspendToDisk ? "Suspend-to-Disk" : "Suspend-to-RAM";
        long retryIntervalMs = INITIAL_SUSPEND_RETRY_INTERVAL_MS;
        long totalWaitDurationMs = 0;
        while (true) {
            Slogf.i(TAG, "Entering %s", suspendTarget);
            if (isSuspendToDisk) {
                freeMemory();
            }
            boolean suspendSucceeded = isSuspendToDisk ? mSystemInterface.enterHibernation()
                    : mSystemInterface.enterDeepSleep();

            if (suspendSucceeded) {
                return true;
            }
            if (totalWaitDurationMs >= mMaxSuspendWaitDurationMs) {
                break;
            }
            // We failed to suspend. Block the thread briefly and try again.
            synchronized (mLock) {
                if (!mPendingPowerStates.isEmpty()) {
                    // Check for a new power state now, before going around the loop again.
                    CpmsState state = mPendingPowerStates.peekFirst();
                    if (state != null && needPowerStateChangeLocked(state)) {
                        Slogf.i(TAG, "Terminating the attempt to suspend target = %s,"
                                        + " currentState = %s, pendingState = %s", suspendTarget,
                                mCurrentState.stateToString(), state.stateToString());
                        return false;
                    }
                }

                Slogf.w(TAG, "Failed to Suspend; will retry after %dms", retryIntervalMs);
                try {
                    mLock.wait(retryIntervalMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                totalWaitDurationMs += retryIntervalMs;
                retryIntervalMs = Math.min(retryIntervalMs * 2, MAX_RETRY_INTERVAL_MS);
            }
        }
        // Too many failures trying to suspend. Shut down.
        Slogf.w(TAG, "Could not %s after %dms long trial. Shutting down.", suspendTarget,
                totalWaitDurationMs);
        mSystemInterface.shutdown();
        return false;
    }

    private static final class CpmsState {
        // NOTE: When modifying states below, make sure to update CarPowerStateChanged.State in
        //   frameworks/proto_logging/stats/atoms.proto also.
        public static final int WAIT_FOR_VHAL = 0;
        public static final int ON = 1;
        public static final int SHUTDOWN_PREPARE = 2;
        public static final int WAIT_FOR_FINISH = 3;
        public static final int SUSPEND = 4;
        public static final int SIMULATE_SLEEP = 5;
        public static final int SIMULATE_HIBERNATION = 6;

        /* Config values from AP_POWER_STATE_REQ */
        public final boolean mCanPostpone;

        @PowerState.ShutdownType
        public final int mShutdownType;

        /* Message sent to CarPowerStateListener in response to this state */
        @CarPowerManager.CarPowerState
        public final int mCarPowerStateListenerState;
        /* One of the above state variables */
        public final int mState;

        /**
          * This constructor takes a PowerHalService.PowerState object and creates the corresponding
          * CPMS state from it.
          */
        CpmsState(PowerState halPowerState) {
            switch (halPowerState.mState) {
                case VehicleApPowerStateReq.ON:
                    this.mCanPostpone = false;
                    this.mShutdownType = PowerState.SHUTDOWN_TYPE_UNDEFINED;
                    this.mCarPowerStateListenerState = cpmsStateToPowerStateListenerState(ON);
                    this.mState = ON;
                    break;
                case VehicleApPowerStateReq.SHUTDOWN_PREPARE:
                    this.mCanPostpone = halPowerState.canPostponeShutdown();
                    this.mShutdownType = halPowerState.getShutdownType();
                    this.mCarPowerStateListenerState = cpmsStateToPowerStateListenerState(
                            SHUTDOWN_PREPARE);
                    this.mState = SHUTDOWN_PREPARE;
                    break;
                case VehicleApPowerStateReq.CANCEL_SHUTDOWN:
                    this.mCanPostpone = false;
                    this.mShutdownType = PowerState.SHUTDOWN_TYPE_UNDEFINED;
                    this.mCarPowerStateListenerState = CarPowerManager.STATE_SHUTDOWN_CANCELLED;
                    this.mState = WAIT_FOR_VHAL;
                    break;
                case VehicleApPowerStateReq.FINISHED:
                    this.mCanPostpone = false;
                    this.mShutdownType = PowerState.SHUTDOWN_TYPE_UNDEFINED;
                    this.mCarPowerStateListenerState = cpmsStateToPowerStateListenerState(SUSPEND);
                    this.mState = SUSPEND;
                    break;
                default:
                    // Illegal state from PowerState.  Throw an exception?
                    // TODO(b/202414427): Add handling of illegal state
                    this.mCanPostpone = false;
                    this.mShutdownType = PowerState.SHUTDOWN_TYPE_UNDEFINED;
                    this.mCarPowerStateListenerState = 0;
                    this.mState = 0;
                    break;
            }
        }

        CpmsState(int state, int carPowerStateListenerState, boolean canPostpone) {
            this.mCanPostpone = canPostpone;
            this.mCarPowerStateListenerState = carPowerStateListenerState;
            this.mState = state;
            this.mShutdownType = state == SIMULATE_SLEEP ? PowerState.SHUTDOWN_TYPE_DEEP_SLEEP :
                    (state == SIMULATE_HIBERNATION ? PowerState.SHUTDOWN_TYPE_HIBERNATION
                            : PowerState.SHUTDOWN_TYPE_POWER_OFF);
        }

        CpmsState(int state, int carPowerStateListenerState, boolean canPostpone,
                int shutdownType) {
            this.mCanPostpone = canPostpone;
            this.mCarPowerStateListenerState = carPowerStateListenerState;
            this.mState = state;
            this.mShutdownType = shutdownType;
        }

        public String name() {
            return new StringBuilder()
                    .append(stateToString())
                    .append('(')
                    .append(mState)
                    .append(')')
                    .toString();
        }

        private String stateToString() {
            String baseName;
            switch(mState) {
                case WAIT_FOR_VHAL:         baseName = "WAIT_FOR_VHAL";        break;
                case ON:                    baseName = "ON";                   break;
                case SHUTDOWN_PREPARE:      baseName = "SHUTDOWN_PREPARE";     break;
                case WAIT_FOR_FINISH:       baseName = "WAIT_FOR_FINISH";      break;
                case SUSPEND:               baseName = "SUSPEND";              break;
                case SIMULATE_SLEEP:        baseName = "SIMULATE_SLEEP";       break;
                case SIMULATE_HIBERNATION:  baseName = "SIMULATE_HIBERNATION"; break;
                default:                    baseName = "<unknown>";            break;
            }
            return baseName;
        }

        private static int cpmsStateToPowerStateListenerState(int state) {
            int powerStateListenerState = 0;

            // Set the CarPowerStateListenerState based on current state
            switch (state) {
                case ON:
                    powerStateListenerState = CarPowerManager.STATE_ON;
                    break;
                case SHUTDOWN_PREPARE:
                    powerStateListenerState = CarPowerManager.STATE_PRE_SHUTDOWN_PREPARE;
                    break;
                case SUSPEND:
                    powerStateListenerState = CarPowerManager.STATE_SUSPEND_ENTER;
                    break;
                case WAIT_FOR_VHAL:
                case WAIT_FOR_FINISH:
                default:
                    // Illegal state for this constructor. Throw an exception?
                    break;
            }
            return powerStateListenerState;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof CpmsState)) {
                return false;
            }
            CpmsState that = (CpmsState) o;
            return this.mState == that.mState
                    && this.mShutdownType == that.mShutdownType
                    && this.mCanPostpone == that.mCanPostpone
                    && this.mCarPowerStateListenerState == that.mCarPowerStateListenerState;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mCanPostpone, mShutdownType, mCarPowerStateListenerState, mState);
        }

        // PowerPolicyHostTest uses the dump output of {@code CarPowerManagementService}. If the
        // {@code CpmsState.toString} is modifed, PowerPolicyHostTest should be updated accordingly.
        // TODO(b/184862429): Remove the above comment once dump in proto buffer is done.
        @Override
        public String toString() {
            return "CpmsState canPostpone=" + mCanPostpone
                    + ", carPowerStateListenerState=" + mCarPowerStateListenerState
                    + ", mShutdownType=" + mShutdownType
                    + ", CpmsState=" + name();
        }
    }

    /**
     * Resume after a manually-invoked suspend.
     * Invoked using "adb shell dumpsys cmd car_service resume".
     */
    public void forceSimulatedResume() {
        synchronized (mLock) {
            // Cancel Garage Mode in case it's running
            mPendingPowerStates.addFirst(new CpmsState(CpmsState.WAIT_FOR_VHAL,
                    CarPowerManager.STATE_SHUTDOWN_CANCELLED, /* canPostpone= */ false));
            mLock.notifyAll();
        }
        mHandler.handlePowerStateChange();

        synchronized (mSimulationWaitObject) {
            mWakeFromSimulatedSleep = true;
            mSimulationWaitObject.notifyAll();
        }
    }

    /**
     * Manually enters simulated suspend (deep sleep or hibernation) mode, trigging Garage mode.
     *
     * <p>If {@code shouldReboot} is 'true', reboots the system when Garage Mode completes.
     *
     * Can be invoked using
     * {@code "adb shell cmd car_service suspend --simulate"} or
     * {@code "adb shell cmd car_service hibernate --simulate"} or
     * {@code "adb shell cmd car_service garage-mode reboot"}.
     *
     * This is similar to {@code 'onApPowerStateChange()'} except that it needs to create a
     * {@code CpmsState} that is not directly derived from a {@code VehicleApPowerStateReq}.
     */
    // TODO(b/274895468): Add tests
    public void simulateSuspendAndMaybeReboot(@PowerState.ShutdownType int shutdownType,
            boolean shouldReboot, boolean skipGarageMode, int wakeupAfter, boolean freeMemory) {
        boolean isDeepSleep = shutdownType == PowerState.SHUTDOWN_TYPE_DEEP_SLEEP;
        synchronized (mSimulationWaitObject) {
            mInSimulatedDeepSleepMode = true;
            mWakeFromSimulatedSleep = false;
            mResumeDelayFromSimulatedSuspendSec = wakeupAfter;
            mFreeMemoryBeforeSuspend = freeMemory;
        }
        synchronized (mLock) {
            mRebootAfterGarageMode = shouldReboot;
            mPendingPowerStates.addFirst(new CpmsState(isDeepSleep ? CpmsState.SIMULATE_SLEEP
                            : CpmsState.SIMULATE_HIBERNATION,
                    CarPowerManager.STATE_PRE_SHUTDOWN_PREPARE, !skipGarageMode));
        }
        mHandler.handlePowerStateChange();
    }

    /**
     * Manually defines a power policy.
     *
     * <p>If the given ID already exists or specified power components are invalid, it fails.
     *
     * @return {@code true}, if successful. Otherwise, {@code false}.
     */
    public boolean definePowerPolicyFromCommand(String[] args, IndentingPrintWriter writer) {
        if (args.length < 2) {
            writer.println("Too few arguments");
            return false;
        }
        String powerPolicyId = args[1];
        int index = 2;
        String[] enabledComponents = new String[0];
        String[] disabledComponents = new String[0];
        while (index < args.length) {
            switch (args[index]) {
                case "--enable":
                    if (index == args.length - 1) {
                        writer.println("No components for --enable");
                        return false;
                    }
                    enabledComponents = args[index + 1].split(",");
                    break;
                case "--disable":
                    if (index == args.length - 1) {
                        writer.println("No components for --disabled");
                        return false;
                    }
                    disabledComponents = args[index + 1].split(",");
                    break;
                default:
                    writer.printf("Unrecognized argument: %s\n", args[index]);
                    return false;
            }
            index += 2;
        }
        int status = definePowerPolicy(powerPolicyId, enabledComponents, disabledComponents);
        if (status != PolicyOperationStatus.OK) {
            writer.println(PolicyOperationStatus.errorCodeToString(status));
            return false;
        }
        writer.printf("Power policy(%s) is successfully defined.\n", powerPolicyId);
        return true;
    }

    /**
     * Defines a power policy with the given id and components.
     *
     * <p> A policy defined with this method is valid until the system is rebooted/restarted.
     */
    @VisibleForTesting
    @PolicyOperationStatus.ErrorCode
    public int definePowerPolicy(String powerPolicyId, String[] enabledComponents,
            String[] disabledComponents) {
        Preconditions.checkArgument(!powerPolicyId.startsWith(
                PolicyReader.SYSTEM_POWER_POLICY_PREFIX),
                "System power policy cannot be defined by apps");
        int status = mPolicyReader.definePowerPolicy(powerPolicyId,
                enabledComponents, disabledComponents);
        if (status != PolicyOperationStatus.OK) {
            return status;
        }
        // Get custom power components and update components list in PowerComponentHandler
        Collection<Integer> customComponents = mPolicyReader.getCustomComponents().values();
        if (customComponents.size() > 0) {
            mPowerComponentHandler.registerCustomComponents(
                    customComponents.toArray(new Integer[customComponents.size()]));
        }
        if (mFeatureFlags.carPowerPolicyRefactoring()) {
            ICarPowerPolicyDelegate daemon;
            synchronized (mLock) {
                daemon = mRefactoredCarPowerPolicyDaemon;
            }
            try {
                daemon.notifyPowerPolicyDefinition(powerPolicyId, enabledComponents,
                        disabledComponents);
            } catch (IllegalArgumentException e) {
                int policyError = PolicyOperationStatus.ERROR_INVALID_POWER_POLICY_ID;
                int componentError = PolicyOperationStatus.ERROR_INVALID_POWER_COMPONENT;
                Slogf.w(TAG, e, "%s and/or %s",
                        PolicyOperationStatus.errorCodeToString(policyError),
                        PolicyOperationStatus.errorCodeToString(componentError));
                return policyError;
            } catch (IllegalStateException | RemoteException e) {
                int error = PolicyOperationStatus.ERROR_DEFINE_POWER_POLICY;
                Slogf.w(TAG, e, PolicyOperationStatus.errorCodeToString(error));
                return error;
            } catch (SecurityException e) {
                int error = PolicyOperationStatus.ERROR_DEFINE_POWER_POLICY;
                Slogf.w(TAG, e, "%s; insufficient permissions",
                        PolicyOperationStatus.errorCodeToString(error));
                return error;
            }
        } else {
            ICarPowerPolicySystemNotification daemon;
            synchronized (mLock) {
                daemon = mCarPowerPolicyDaemon;
            }
            try {
                daemon.notifyPowerPolicyDefinition(powerPolicyId, enabledComponents,
                        disabledComponents);
            } catch (RemoteException e) {
                int error = PolicyOperationStatus.ERROR_DEFINE_POWER_POLICY;
                Slogf.w(TAG, e, PolicyOperationStatus.errorCodeToString(error));
                return error;
            }
        }
        return PolicyOperationStatus.OK;
    }

    /**
     * Manually applies a power policy.
     *
     * <p>If the given ID is not defined, it fails.
     *
     * @return {@code true}, if successful. Otherwise, {@code false}.
     */
    public boolean applyPowerPolicyFromCommand(String[] args, IndentingPrintWriter writer) {
        if (args.length != 2) {
            writer.println("Power policy ID should be given");
            return false;
        }
        String powerPolicyId = args[1];
        if (powerPolicyId == null) {
            writer.println("Policy ID cannot be null");
            return false;
        }
        int status;
        if (mFeatureFlags.carPowerPolicyRefactoring()) {
            status = applyPowerPolicy(powerPolicyId, /* delayNotification= */ false,
                    /* upToDaemon= */ false, /* force= */ false);
        } else {
            boolean isPreemptive = mPolicyReader.isPreemptivePowerPolicy(powerPolicyId);
            status = isPreemptive ? applyPreemptivePowerPolicy(powerPolicyId)
                    : applyPowerPolicy(powerPolicyId, /* delayNotification= */ false,
                            /* upToDaemon= */ true, /* force= */ false);
        }
        if (status != PolicyOperationStatus.OK) {
            writer.println(PolicyOperationStatus.errorCodeToString(status));
            return false;
        }
        writer.printf("Power policy(%s) is successfully applied.\n", powerPolicyId);
        return true;
    }

    /**
     * Manually defines a power policy group.
     *
     * <p>If the given ID already exists, a wrong power state is given, or specified power policy ID
     * doesn't exist, it fails.
     *
     * @return {@code true}, if successful. Otherwise, {@code false}.
     */
    public boolean definePowerPolicyGroupFromCommand(String[] args, IndentingPrintWriter writer) {
        if (args.length < 3 || args.length > 4) {
            writer.println("Invalid syntax");
            return false;
        }
        String policyGroupId = args[1];
        int index = 2;
        SparseArray<String> defaultPolicyPerState = new SparseArray<>();
        String[] powerPolicyPerState = {"", ""};
        while (index < args.length) {
            String[] tokens = args[index].split(":");
            if (tokens.length != 2) {
                writer.println("Invalid syntax");
                return false;
            }
            int state = PolicyReader.toPowerState(tokens[0]);
            if (state == PolicyReader.INVALID_POWER_STATE) {
                writer.printf("Invalid power state: %s\n", tokens[0]);
                return false;
            }
            defaultPolicyPerState.put(state, tokens[1]);
            switch (state) {
                case VehicleApPowerStateReport.WAIT_FOR_VHAL:
                    powerPolicyPerState[INDEX_WAIT_FOR_VHAL] = tokens[1];
                    break;
                case VehicleApPowerStateReport.ON:
                    powerPolicyPerState[INDEX_ON] = tokens[1];
                    break;
            }
            index++;
        }
        if (mFeatureFlags.carPowerPolicyRefactoring()) {
            ICarPowerPolicyDelegate daemon;
            synchronized (mLock) {
                daemon = mRefactoredCarPowerPolicyDaemon;
            }
            try {
                daemon.notifyPowerPolicyGroupDefinition(policyGroupId, powerPolicyPerState);
            } catch (IllegalArgumentException e) {
                Slogf.w(TAG, e, "The given policy group ID(%s) or mapping between power state and"
                        + " policy is invalid", policyGroupId);
                return false;
            } catch (RemoteException e) {
                Slogf.w(TAG, e, "Calling ICarPowerPolicyDelegate.notifyPowerPolicyGroupDefinition"
                        + " failed");
                return false;
            }
        }
        int status = mPolicyReader.definePowerPolicyGroup(policyGroupId,
                defaultPolicyPerState);
        if (status != PolicyOperationStatus.OK) {
            writer.println(PolicyOperationStatus.errorCodeToString(status));
            return false;
        }
        writer.printf("Power policy group(%s) is successfully defined.\n", policyGroupId);
        return true;
    }

    /**
     * Manually sets a power policy group.
     *
     * <p>If the given ID is not defined, it fails.
     *
     * @return {@code true}, if successful. Otherwise, {@code false}.
     */
    public boolean setPowerPolicyGroupFromCommand(String[] args, IndentingPrintWriter writer) {
        if (args.length != 2) {
            writer.println("Power policy group ID should be given");
            return false;
        }
        String policyGroupId = args[1];
        if (mFeatureFlags.carPowerPolicyRefactoring()) {
            try {
                setPowerPolicyGroup(policyGroupId);
            } catch (RemoteException e) {
                writer.println("RemoteException encountered when setting power policy group");
                return false;
            }
        } else {
            int status = setCurrentPowerPolicyGroup(policyGroupId);
            if (status != PolicyOperationStatus.OK) {
                writer.println(PolicyOperationStatus.errorCodeToString(status));
                return false;
            }
        }
        writer.printf("Setting power policy group(%s) is successful.\n", policyGroupId);
        return true;
    }

    /**
     * Suspends the device.
     *
     * <p>According to the argument, the device is suspended to RAM or disk.
     */
    public void suspendFromCommand(boolean isHibernation, boolean skipGarageMode) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CAR_POWER);
        int param = 0;
        if (isHibernation) {
            if (!isHibernationAvailable()) {
                throw new IllegalStateException("The device doesn't support hibernation");
            }
            param = skipGarageMode ? VehicleApPowerStateShutdownParam.HIBERNATE_IMMEDIATELY
                    : VehicleApPowerStateShutdownParam.CAN_HIBERNATE;
        } else {
            if (!isDeepSleepAvailable()) {
                throw new IllegalStateException("The device doesn't support deep sleep");
            }
            param = skipGarageMode ? VehicleApPowerStateShutdownParam.SLEEP_IMMEDIATELY
                    : VehicleApPowerStateShutdownParam.CAN_SLEEP;
        }
        PowerState state = new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE, param);
        synchronized (mLock) {
            mRebootAfterGarageMode = false;
            mPendingPowerStates.addFirst(new CpmsState(state));
            mLock.notifyAll();
        }
        mHandler.handlePowerStateChange();
    }

    /**
     * Powers off the device.
     */
    public void powerOffFromCommand(boolean skipGarageMode, boolean reboot) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CAR_POWER);
        Slogf.i(TAG, "%s %s Garage Mode", reboot ? "Rebooting" : "Powering off",
                skipGarageMode ? "with" : "without");
        int param = skipGarageMode ? VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY
                : VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY;
        PowerState state = new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE, param);
        synchronized (mLock) {
            mRebootAfterGarageMode = reboot;
            mPendingPowerStates.addFirst(new CpmsState(state));
            mLock.notifyAll();
        }
        mHandler.handlePowerStateChange();
    }

    /**
     * Returns the last shutdown state.
     */
    public int getLastShutdownState() {
        synchronized (mLock) {
            return mLastShutdownState;
        }
    }

    /**
     * Changes Silent Mode to the given mode.
     */
    public void setSilentMode(String silentMode) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CAR_POWER);
        mSilentModeHandler.setSilentMode(silentMode);
    }

    /**
     * Dumps the current Silent Mode.
     */
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpSilentMode(IndentingPrintWriter writer) {
        mSilentModeHandler.dump(writer);
    }

    /**
     * Returns whether a listener completion is allowed for the given state.
     *
     * <p>This method is used internally and is different from
     * {@link CarPowerManager.isCompletionAllowed} in that listener completion is allowed for
     * SHUTDOWN_PREPARE.
     */
    public static boolean isCompletionAllowed(@CarPowerManager.CarPowerState int state) {
        return CarPowerManager.isCompletionAllowed(state);
    }

    /**
     * Returns a corresponding string of the given power state.
     */
    public static String powerStateToString(int state) {
        return DebugUtils.valueToString(CarPowerManager.class, "STATE_", state);
    }

    /**
     * Requests VHAL to shutdown the head unit.
     *
     * @throws UnsupportedOperationException If the system doesn't not support
     *         {@code nextPowerState}.
     */
    public void requestShutdownAp(int nextPowerState, boolean runGarageMode) {
        int shutdownParam = PowerState.SHUTDOWN_TYPE_POWER_OFF;
        switch (nextPowerState) {
            case CarRemoteAccessManager.NEXT_POWER_STATE_ON:
                // Do nothing.
                return;
            case CarRemoteAccessManager.NEXT_POWER_STATE_OFF:
                shutdownParam = PowerState.SHUTDOWN_TYPE_POWER_OFF;
                break;
            case CarRemoteAccessManager.NEXT_POWER_STATE_SUSPEND_TO_RAM:
                if (!isDeepSleepAvailable()) {
                    throw new UnsupportedOperationException("Suspend-to-RAM is not supported");
                }
                shutdownParam = PowerState.SHUTDOWN_TYPE_DEEP_SLEEP;
                break;
            case CarRemoteAccessManager.NEXT_POWER_STATE_SUSPEND_TO_DISK:
                if (!isHibernationAvailable()) {
                    throw new UnsupportedOperationException("Suspend-to-disk is not supported");
                }
                shutdownParam = PowerState.SHUTDOWN_TYPE_HIBERNATION;
                break;
            default:
                Slogf.w(TAG, "Unknown power state(%d)", nextPowerState);
                return;
        }
        mHal.requestShutdownAp(shutdownParam, runGarageMode);
    }

    /**
     * Returns whether suspend (deep sleep or hibernation) is available on the device.
     */
    public boolean isSuspendAvailable(boolean isHibernation) {
        return isHibernation ? isHibernationAvailable() : isDeepSleepAvailable();
    }

    /**
     * Enters garage mode if the bootup reason is ENTER_GARAGE_MODE.
     */
    @Override
    public void onInitComplete() {
        if (mFeatureFlags.serverlessRemoteAccess()) {
            maybeEnterGarageModeOnBoot();
        }
    }

    /**
     * Shutdown the device to run garage mode if the bootup reason is ENTER_GARAGE_MODE.
     */
    private void maybeEnterGarageModeOnBoot() {
        @BootupReason int bootupReason = mHal.getVehicleApBootupReason();
        Slogf.i(TAG, "Vehicle AP power bootup reason: " + bootupReason);
        if (bootupReason != BOOTUP_REASON_SYSTEM_ENTER_GARAGE_MODE) {
            return;
        }
        if (mHal.isVehicleInUse()) {
            Slogf.i(TAG, "Bootup reason is ENTER_GARAGE_MODE but vehicle is currently in use"
                    + ", skip entering garage mode");
            return;
        }
        try {
            requestShutdownAp(getLastShutdownState(), /* runGarageMode= */ true);
        } catch (Exception e) {
            Slogf.wtf(TAG, "Failed to call requestShutdownAp", e);
        }
    }

    private boolean isDeepSleepAvailable() {
        return mHal.isDeepSleepAllowed() && mSystemInterface.isSystemSupportingDeepSleep();
    }

    private boolean isHibernationAvailable() {
        return mHal.isHibernationAllowed() && mSystemInterface.isSystemSupportingHibernation();
    }

    // In a real Deep Sleep, the hardware removes power from the CPU (but retains power
    // on the RAM). This puts the processor to sleep. Upon some external signal, power
    // is re-applied to the CPU, and processing resumes right where it left off.
    // We simulate this behavior by calling wait().
    // We continue from wait() when forceSimulatedResume() is called.
    private void simulateSleepByWaiting() {
        Slogf.i(TAG, "Starting to simulate Deep Sleep by waiting");
        synchronized (mSimulationWaitObject) {
            if (mFreeMemoryBeforeSuspend) {
                freeMemory();
            }
            if (mResumeDelayFromSimulatedSuspendSec >= 0) {
                Slogf.i(TAG, "Scheduling a wakeup after %d seconds",
                        mResumeDelayFromSimulatedSuspendSec);
                Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(() -> forceSimulatedResume(),
                        mResumeDelayFromSimulatedSuspendSec * 1000);
            }
            while (!mWakeFromSimulatedSleep) {
                try {
                    mSimulationWaitObject.wait();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt(); // Restore interrupted status
                }
            }
            mInSimulatedDeepSleepMode = false;
        }
        Slogf.i(TAG, "Exit Deep Sleep simulation");
    }

    private int getMaxSuspendWaitDurationConfig() {
        return mContext.getResources().getInteger(R.integer.config_maxSuspendWaitDuration);
    }

    private boolean isWifiAdjustmentForSuspendConfig() {
        return mContext.getResources().getBoolean(R.bool.config_wifiAdjustmentForSuspend);
    }

    private int getPreShutdownPrepareTimeoutConfig() {
        return getCompletionWaitTimeoutConfig(R.integer.config_preShutdownPrepareTimeout);
    }

    private int getShutdownEnterTimeoutConfig() {
        return getCompletionWaitTimeoutConfig(R.integer.config_shutdownEnterTimeout);
    }

    private int getPostShutdownEnterTimeoutConfig() {
        return getCompletionWaitTimeoutConfig(R.integer.config_postShutdownEnterTimeout);
    }

    private int getCompletionWaitTimeoutConfig(int resourceId) {
        int timeout = mContext.getResources().getInteger(resourceId);
        return timeout >= 0 ? timeout : DEFAULT_COMPLETION_WAIT_TIMEOUT;
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private static String actionOnFinishToString(int actionOnFinish) {
        switch (actionOnFinish) {
            case ACTION_ON_FINISH_SHUTDOWN:
                return "Shutdown";
            case ACTION_ON_FINISH_DEEP_SLEEP:
                return "Deep sleep";
            case ACTION_ON_FINISH_HIBERNATION:
                return "Hibernation";
            default:
                return "Unknown";
        }
    }

    private void waitForCompletionWithShutdownPostpone(
            @CarPowerManager.CarPowerState int carPowerStateListenerState, long timeoutMs,
            Runnable taskAtCompletion, long intervalMs) {
        Runnable taskAtInterval = () -> {
            mHal.sendShutdownPostpone(SHUTDOWN_EXTEND_MAX_MS);
        };

        Slogf.i(TAG, "Start waiting for listener completion for %s",
                powerStateToString(carPowerStateListenerState));

        waitForCompletion(taskAtCompletion, taskAtInterval, timeoutMs, intervalMs);
    }

    /**
     * Utility method to help with memory freeing before entering Suspend-To-Disk
     */
    static void freeMemory() {
        PlatformVersion platformVersion = Car.getPlatformVersion();
        ActivityManagerHelper.killAllBackgroundProcesses();
    }

    /**
     * Helper method to accept function with one displayId argument to run on all displays.
     */
    private static void forEachDisplay(@NonNull Context context,
            @NonNull Consumer<Integer> consumer) {
        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        for (Display display : displayManager.getDisplays()) {
            int displayId = display.getDisplayId();
            consumer.accept(displayId);
        }
    }
}
