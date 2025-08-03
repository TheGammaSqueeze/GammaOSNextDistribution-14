/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.watchdog;

import static android.car.PlatformVersion.VERSION_CODES;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_POST_UNLOCKED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STARTING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STOPPED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKING;
import static android.content.Intent.ACTION_PACKAGE_CHANGED;
import static android.content.Intent.ACTION_REBOOT;
import static android.content.Intent.ACTION_SHUTDOWN;
import static android.content.Intent.ACTION_USER_REMOVED;

import static com.android.car.CarLog.TAG_WATCHDOG;
import static com.android.car.CarServiceUtils.assertAnyPermission;
import static com.android.car.CarServiceUtils.assertPermission;
import static com.android.car.CarServiceUtils.isEventAnyOfTypes;
import static com.android.car.CarServiceUtils.runOnMain;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;
import static com.android.car.internal.NotificationHelperBase.CAR_WATCHDOG_ACTION_DISMISS_RESOURCE_OVERUSE_NOTIFICATION;
import static com.android.car.internal.NotificationHelperBase.CAR_WATCHDOG_ACTION_LAUNCH_APP_SETTINGS;
import static com.android.car.internal.NotificationHelperBase.CAR_WATCHDOG_ACTION_RESOURCE_OVERUSE_DISABLE_APP;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.automotive.watchdog.internal.GarageMode;
import android.automotive.watchdog.internal.ICarWatchdogServiceForSystem;
import android.automotive.watchdog.internal.PackageInfo;
import android.automotive.watchdog.internal.PackageIoOveruseStats;
import android.automotive.watchdog.internal.PowerCycle;
import android.automotive.watchdog.internal.ResourceStats;
import android.automotive.watchdog.internal.StateType;
import android.automotive.watchdog.internal.UserPackageIoUsageStats;
import android.automotive.watchdog.internal.UserState;
import android.car.Car;
import android.car.builtin.util.Slogf;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.CarPowerPolicy;
import android.car.hardware.power.CarPowerPolicyFilter;
import android.car.hardware.power.ICarPowerPolicyListener;
import android.car.hardware.power.ICarPowerStateListener;
import android.car.hardware.power.PowerComponent;
import android.car.user.UserLifecycleEventFilter;
import android.car.watchdog.CarWatchdogManager;
import android.car.watchdog.ICarWatchdogService;
import android.car.watchdog.ICarWatchdogServiceCallback;
import android.car.watchdog.IResourceOveruseListener;
import android.car.watchdog.PackageKillableState;
import android.car.watchdog.ResourceOveruseConfiguration;
import android.car.watchdog.ResourceOveruseStats;
import android.car.watchdoglib.CarWatchdogDaemonHelper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArraySet;
import android.util.Log;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.CarServiceBase;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.ArrayUtils;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.power.CarPowerManagementService;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.user.CarUserService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.File;
import java.lang.ref.WeakReference;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Service to implement CarWatchdogManager API.
 */
public final class CarWatchdogService extends ICarWatchdogService.Stub implements CarServiceBase {
    static final String TAG = CarLog.tagFor(CarWatchdogService.class);
    static final boolean DEBUG = Slogf.isLoggable(TAG, Log.DEBUG);
    static final String ACTION_GARAGE_MODE_ON =
            "com.android.server.jobscheduler.GARAGE_MODE_ON";
    static final String ACTION_GARAGE_MODE_OFF =
            "com.android.server.jobscheduler.GARAGE_MODE_OFF";

    @VisibleForTesting
    static final int MISSING_ARG_VALUE = -1;

    private static final String FALLBACK_DATA_SYSTEM_CAR_DIR_PATH = "/data/system/car";
    private static final String WATCHDOG_DIR_NAME = "watchdog";

    private static final TimeSource SYSTEM_INSTANCE = new TimeSource() {
        @Override
        public Instant now() {
            return Instant.now();
        }

        @Override
        public String toString() {
            return "System time instance";
        }
    };

    private final Context mContext;
    private final ICarWatchdogServiceForSystemImpl mWatchdogServiceForSystem;
    private final PackageInfoHandler mPackageInfoHandler;
    private final WatchdogStorage mWatchdogStorage;
    private final WatchdogProcessHandler mWatchdogProcessHandler;
    private final WatchdogPerfHandler mWatchdogPerfHandler;
    private final CarWatchdogDaemonHelper.OnConnectionChangeListener mConnectionListener;

    private CarWatchdogDaemonHelper mCarWatchdogDaemonHelper;

    /*
     * TODO(b/192481350): Listen for GarageMode change notification rather than depending on the
     *  system_server broadcast when the CarService internal API for listening GarageMode change is
     *  implemented.
     */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case CAR_WATCHDOG_ACTION_DISMISS_RESOURCE_OVERUSE_NOTIFICATION:
                case CAR_WATCHDOG_ACTION_LAUNCH_APP_SETTINGS:
                case CAR_WATCHDOG_ACTION_RESOURCE_OVERUSE_DISABLE_APP:
                    mWatchdogPerfHandler.processUserNotificationIntent(intent);
                    break;
                case ACTION_GARAGE_MODE_ON:
                case ACTION_GARAGE_MODE_OFF:
                    int garageMode;
                    synchronized (mLock) {
                        garageMode = mCurrentGarageMode = Objects.equals(action,
                                ACTION_GARAGE_MODE_ON)
                                ? GarageMode.GARAGE_MODE_ON : GarageMode.GARAGE_MODE_OFF;
                    }
                    mWatchdogPerfHandler.onGarageModeChange(garageMode);
                    if (garageMode == GarageMode.GARAGE_MODE_ON) {
                        mWatchdogStorage.shrinkDatabase();
                    }
                    notifyGarageModeChange(garageMode);
                    break;
                case ACTION_REBOOT:
                case ACTION_SHUTDOWN:
                    // FLAG_RECEIVER_FOREGROUND is checked to ignore the intent from UserController
                    // when a user is stopped.
                    if ((intent.getFlags() & Intent.FLAG_RECEIVER_FOREGROUND) == 0) {
                        break;
                    }
                    int powerCycle = PowerCycle.POWER_CYCLE_SHUTDOWN_ENTER;
                    try {
                        mCarWatchdogDaemonHelper.notifySystemStateChange(StateType.POWER_CYCLE,
                                powerCycle, /* arg2= */ 0);
                        if (DEBUG) {
                            Slogf.d(TAG, "Notified car watchdog daemon of power cycle(%d)",
                                    powerCycle);
                        }
                    } catch (Exception e) {
                        Slogf.w(TAG, e, "Notifying power cycle state change failed");
                    }
                    break;
                case ACTION_USER_REMOVED: {
                    UserHandle userHandle = intent.getParcelableExtra(Intent.EXTRA_USER);
                    int userId = userHandle.getIdentifier();
                    try {
                        mCarWatchdogDaemonHelper.notifySystemStateChange(StateType.USER_STATE,
                                userId, UserState.USER_STATE_REMOVED);
                        if (DEBUG) {
                            Slogf.d(TAG, "Notified car watchdog daemon of removed user %d",
                                    userId);
                        }
                    } catch (RemoteException e) {
                        Slogf.w(TAG, e, "Failed to notify car watchdog daemon of removed user %d",
                                userId);
                    }
                    mWatchdogPerfHandler.deleteUser(userId);
                    break;
                }
                case ACTION_PACKAGE_CHANGED: {
                    mWatchdogPerfHandler.processPackageChangedIntent(intent);
                    break;
                }
                default:
                    Slogf.i(TAG, "Ignoring unknown intent %s", intent);
            }
        }
    };

    private final ICarPowerStateListener mCarPowerStateListener =
            new ICarPowerStateListener.Stub() {
        @Override
        public void onStateChanged(int state, long expirationTimeMs) {
            CarPowerManagementService powerService =
                    CarLocalServices.getService(CarPowerManagementService.class);
            if (powerService == null) {
                return;
            }
            int powerCycle = carPowerStateToPowerCycle(powerService.getPowerState());
            switch (powerCycle) {
                case PowerCycle.POWER_CYCLE_SHUTDOWN_PREPARE:
                    // Perform time consuming disk I/O operation during shutdown prepare to avoid
                    // incomplete I/O.
                    mWatchdogPerfHandler.writeMetadataFile();
                    break;
                case PowerCycle.POWER_CYCLE_SHUTDOWN_ENTER:
                    // Watchdog service and daemon performs garage mode monitoring so delay writing
                    // to database until after shutdown enter.
                    mWatchdogPerfHandler.writeToDatabase();
                    break;
                case PowerCycle.POWER_CYCLE_SUSPEND_EXIT:
                    break;
                // ON covers resume.
                case PowerCycle.POWER_CYCLE_RESUME:
                    // There might be outdated & incorrect info. We should reset them before
                    // starting to do health check.
                    mWatchdogProcessHandler.prepareHealthCheck();
                    break;
                default:
                    return;
            }
            notifyPowerCycleChange(powerCycle);
        }
    };

    private final ICarPowerPolicyListener mCarDisplayPowerPolicyListener =
            new ICarPowerPolicyListener.Stub() {
                @Override
                public void onPolicyChanged(CarPowerPolicy appliedPolicy,
                        CarPowerPolicy accumulatedPolicy) {
                    boolean isDisplayEnabled =
                            appliedPolicy.isComponentEnabled(PowerComponent.DISPLAY);
                    boolean didStateChange = false;
                    synchronized (mLock) {
                        didStateChange = mIsDisplayEnabled != isDisplayEnabled;
                        mIsDisplayEnabled = isDisplayEnabled;
                    }
                    if (didStateChange) {
                        mWatchdogPerfHandler.onDisplayStateChanged(isDisplayEnabled);
                    }
                }
            };

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private boolean mReadyToRespond;
    @GuardedBy("mLock")
    private boolean mIsConnected;
    @GuardedBy("mLock")
    private @GarageMode int mCurrentGarageMode;
    @GuardedBy("mLock")
    private boolean mIsDisplayEnabled;

    public CarWatchdogService(Context context, Context carServiceBuiltinPackageContext) {
        this(context, carServiceBuiltinPackageContext,
                new WatchdogStorage(context, SYSTEM_INSTANCE), SYSTEM_INSTANCE);
    }

    @VisibleForTesting
    public CarWatchdogService(Context context, Context carServiceBuiltinPackageContext,
            WatchdogStorage watchdogStorage, TimeSource timeSource) {
        this(context, carServiceBuiltinPackageContext, watchdogStorage,
                timeSource, /*watchdogProcessHandler=*/ null, /*watchdogPerfHandler=*/ null);
    }

    @VisibleForTesting
    CarWatchdogService(Context context, Context carServiceBuiltinPackageContext,
            WatchdogStorage watchdogStorage, TimeSource timeSource,
            WatchdogProcessHandler watchdogProcessHandler,
            WatchdogPerfHandler watchdogPerfHandler) {
        mContext = context;
        mWatchdogStorage = watchdogStorage;
        mPackageInfoHandler = new PackageInfoHandler(mContext.getPackageManager());
        mCarWatchdogDaemonHelper = new CarWatchdogDaemonHelper(TAG_WATCHDOG);
        mWatchdogServiceForSystem = new ICarWatchdogServiceForSystemImpl(this);
        mWatchdogProcessHandler = watchdogProcessHandler != null ? watchdogProcessHandler
                : new WatchdogProcessHandler(mWatchdogServiceForSystem, mCarWatchdogDaemonHelper,
                        mPackageInfoHandler);
        mWatchdogPerfHandler =
                watchdogPerfHandler != null ? watchdogPerfHandler : new WatchdogPerfHandler(
                        mContext, carServiceBuiltinPackageContext,
                        mCarWatchdogDaemonHelper, mPackageInfoHandler, mWatchdogStorage,
                        timeSource);
        mConnectionListener = (isConnected) -> {
            mWatchdogPerfHandler.onDaemonConnectionChange(isConnected);
            synchronized (mLock) {
                mIsConnected = isConnected;
            }
            registerToDaemon();
        };
        mCurrentGarageMode = GarageMode.GARAGE_MODE_OFF;
        mIsDisplayEnabled = true;
    }

    @VisibleForTesting
    public void setCarWatchdogDaemonHelper(CarWatchdogDaemonHelper helper) {
        mCarWatchdogDaemonHelper = helper;
    }

    @Override
    public void init() {
        // TODO(b/266008677): The daemon reads the sendResourceUsageStatsEnabled sysprop at the
        // moment the CarWatchdogService connects to it. Therefore, the property must be set by
        // CarWatchdogService before connecting with the CarWatchdog daemon. Set the property to
        // true to enable the sending of resource usage stats from the daemon.
        mWatchdogProcessHandler.init();
        mWatchdogPerfHandler.init();
        subscribePowerManagementService();
        subscribeUserStateChange();
        subscribeBroadcastReceiver();
        mCarWatchdogDaemonHelper.addOnConnectionChangeListener(mConnectionListener);
        mCarWatchdogDaemonHelper.connect();
        // To make sure the main handler is ready for responding to car watchdog daemon, registering
        // to the daemon is done through the main handler. Once the registration is completed, we
        // can assume that the main handler is not too busy handling other stuffs.
        postRegisterToDaemonMessage();
        if (DEBUG) {
            Slogf.d(TAG, "CarWatchdogService is initialized");
        }
    }

    @Override
    public void release() {
        mContext.unregisterReceiver(mBroadcastReceiver);
        unsubscribePowerManagementService();
        mWatchdogPerfHandler.release();
        mWatchdogStorage.release();
        unregisterFromDaemon();
        mCarWatchdogDaemonHelper.disconnect();
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("*" + getClass().getSimpleName() + "*");
        writer.increaseIndent();
        synchronized (mLock) {
            writer.println("Current garage mode: " + toGarageModeString(mCurrentGarageMode));
        }
        mWatchdogProcessHandler.dump(writer);
        mWatchdogPerfHandler.dump(writer);
        writer.decreaseIndent();
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {
        synchronized (mLock) {
            proto.write(CarWatchdogDumpProto.CURRENT_GARAGE_MODE, mCurrentGarageMode);
        }
        mWatchdogProcessHandler.dumpProto(proto);
        mWatchdogPerfHandler.dumpProto(proto);
    }

    /**
     * Registers {@link android.car.watchdog.ICarWatchdogServiceCallback} to
     * {@link CarWatchdogService}.
     */
    @Override
    public void registerClient(ICarWatchdogServiceCallback client, int timeout) {
        assertPermission(mContext, Car.PERMISSION_USE_CAR_WATCHDOG);
        mWatchdogProcessHandler.registerClient(client, timeout);
    }

    /**
     * Unregisters {@link android.car.watchdog.ICarWatchdogServiceCallback} from
     * {@link CarWatchdogService}.
     */
    @Override
    public void unregisterClient(ICarWatchdogServiceCallback client) {
        assertPermission(mContext, Car.PERMISSION_USE_CAR_WATCHDOG);
        mWatchdogProcessHandler.unregisterClient(client);
    }

    /**
     * Tells {@link CarWatchdogService} that the client is alive.
     */
    @Override
    public void tellClientAlive(ICarWatchdogServiceCallback client, int sessionId) {
        assertPermission(mContext, Car.PERMISSION_USE_CAR_WATCHDOG);
        mWatchdogProcessHandler.tellClientAlive(client, sessionId);
    }

    /** Returns {@link android.car.watchdog.ResourceOveruseStats} for the calling package. */
    @Override
    @NonNull
    public ResourceOveruseStats getResourceOveruseStats(
            @CarWatchdogManager.ResourceOveruseFlag int resourceOveruseFlag,
            @CarWatchdogManager.StatsPeriod int maxStatsPeriod) {
        return mWatchdogPerfHandler.getResourceOveruseStats(resourceOveruseFlag, maxStatsPeriod);
    }

    /**
      *  Returns {@link android.car.watchdog.ResourceOveruseStats} for all packages for the maximum
      *  specified period, and the specified resource types with stats greater than or equal to the
      *  minimum specified stats.
      */
    @Override
    @NonNull
    public List<ResourceOveruseStats> getAllResourceOveruseStats(
            @CarWatchdogManager.ResourceOveruseFlag int resourceOveruseFlag,
            @CarWatchdogManager.MinimumStatsFlag int minimumStatsFlag,
            @CarWatchdogManager.StatsPeriod int maxStatsPeriod) {
        assertPermission(mContext, Car.PERMISSION_COLLECT_CAR_WATCHDOG_METRICS);
        return mWatchdogPerfHandler.getAllResourceOveruseStats(resourceOveruseFlag,
                minimumStatsFlag, maxStatsPeriod);
    }

    /** Returns {@link android.car.watchdog.ResourceOveruseStats} for the specified user package. */
    @Override
    @NonNull
    public ResourceOveruseStats getResourceOveruseStatsForUserPackage(
            @NonNull String packageName, @NonNull UserHandle userHandle,
            @CarWatchdogManager.ResourceOveruseFlag int resourceOveruseFlag,
            @CarWatchdogManager.StatsPeriod int maxStatsPeriod) {
        assertPermission(mContext, Car.PERMISSION_COLLECT_CAR_WATCHDOG_METRICS);
        return mWatchdogPerfHandler.getResourceOveruseStatsForUserPackage(packageName, userHandle,
                resourceOveruseFlag, maxStatsPeriod);
    }

    /**
     * Adds {@link android.car.watchdog.IResourceOveruseListener} for the calling package's resource
     * overuse notifications.
     */
    @Override
    public void addResourceOveruseListener(
            @CarWatchdogManager.ResourceOveruseFlag int resourceOveruseFlag,
            @NonNull IResourceOveruseListener listener) {
        mWatchdogPerfHandler.addResourceOveruseListener(resourceOveruseFlag, listener);
    }

    /**
     * Removes the previously added {@link android.car.watchdog.IResourceOveruseListener} for the
     * calling package's resource overuse notifications.
     */
    @Override
    public void removeResourceOveruseListener(@NonNull IResourceOveruseListener listener) {
        mWatchdogPerfHandler.removeResourceOveruseListener(listener);
    }

    /**
     * Adds {@link android.car.watchdog.IResourceOveruseListener} for all packages' resource overuse
     * notifications.
     */
    @Override
    public void addResourceOveruseListenerForSystem(
            @CarWatchdogManager.ResourceOveruseFlag int resourceOveruseFlag,
            @NonNull IResourceOveruseListener listener) {
        assertPermission(mContext, Car.PERMISSION_COLLECT_CAR_WATCHDOG_METRICS);
        mWatchdogPerfHandler.addResourceOveruseListenerForSystem(resourceOveruseFlag, listener);
    }

    /**
     * Removes the previously added {@link android.car.watchdog.IResourceOveruseListener} for all
     * packages' resource overuse notifications.
     */
    @Override
    public void removeResourceOveruseListenerForSystem(@NonNull IResourceOveruseListener listener) {
        assertPermission(mContext, Car.PERMISSION_COLLECT_CAR_WATCHDOG_METRICS);
        mWatchdogPerfHandler.removeResourceOveruseListenerForSystem(listener);
    }

    /** Sets whether or not a user package is killable on resource overuse. */
    @Override
    public void setKillablePackageAsUser(String packageName, UserHandle userHandle,
            boolean isKillable) {
        assertPermission(mContext, Car.PERMISSION_CONTROL_CAR_WATCHDOG_CONFIG);
        mWatchdogPerfHandler.setKillablePackageAsUser(packageName, userHandle, isKillable);
    }

    /**
     * Returns all {@link android.car.watchdog.PackageKillableState} on resource overuse for
     * the specified user.
     */
    @Override
    @NonNull
    public List<PackageKillableState> getPackageKillableStatesAsUser(UserHandle userHandle) {
        assertPermission(mContext, Car.PERMISSION_CONTROL_CAR_WATCHDOG_CONFIG);
        return mWatchdogPerfHandler.getPackageKillableStatesAsUser(userHandle);
    }

    /**
     * Sets {@link android.car.watchdog.ResourceOveruseConfiguration} for the specified resources.
     */
    @Override
    @CarWatchdogManager.ReturnCode
    public int setResourceOveruseConfigurations(
            List<ResourceOveruseConfiguration> configurations,
            @CarWatchdogManager.ResourceOveruseFlag int resourceOveruseFlag)
            throws RemoteException {
        assertPermission(mContext, Car.PERMISSION_CONTROL_CAR_WATCHDOG_CONFIG);
        return mWatchdogPerfHandler.setResourceOveruseConfigurations(configurations,
                resourceOveruseFlag);
    }

    /** Returns the available {@link android.car.watchdog.ResourceOveruseConfiguration}. */
    @Override
    @NonNull
    public List<ResourceOveruseConfiguration> getResourceOveruseConfigurations(
            @CarWatchdogManager.ResourceOveruseFlag int resourceOveruseFlag) {
        assertAnyPermission(mContext, Car.PERMISSION_CONTROL_CAR_WATCHDOG_CONFIG,
                Car.PERMISSION_COLLECT_CAR_WATCHDOG_METRICS);
        return mWatchdogPerfHandler.getResourceOveruseConfigurations(resourceOveruseFlag);
    }

    /**
     * Enables/disables the watchdog daemon client health check process.
     */
    public void controlProcessHealthCheck(boolean enable) {
        assertPermission(mContext, Car.PERMISSION_USE_CAR_WATCHDOG);
        mWatchdogProcessHandler.controlProcessHealthCheck(enable);
    }

    /**
     * Kills a specific package for a user due to resource overuse.
     *
     * @return whether package was killed
     */
    public boolean performResourceOveruseKill(String packageName, @UserIdInt int userId) {
        assertPermission(mContext, Car.PERMISSION_USE_CAR_WATCHDOG);
        return mWatchdogPerfHandler.disablePackageForUser(packageName, userId);
    }

    /**
     * Sets the thread priority for a specific thread.
     *
     * The thread must belong to the calling process.
     *
     * @throws IllegalArgumentException If the policy/priority is not valid.
     * @throws IllegalStateException If the provided tid does not belong to the calling process.
     * @throws RemoteException If binder error happens.
     * @throws ServiceSpecificException If car watchdog daemon failed to set the thread priority.
     * @throws UnsupportedOperationException If the current android release doesn't support the API.
     */
    public void setThreadPriority(int pid, int tid, int uid, int policy, int priority)
            throws RemoteException {
        mCarWatchdogDaemonHelper.setThreadPriority(pid, tid, uid, policy, priority);
    }

    /**
     * Gets the thread scheduling policy and priority for the specified thread.
     *
     * The thread must belong to the calling process.
     *
     * @throws IllegalStateException If the provided tid does not belong to the calling process or
     *         car watchdog daemon failed to get the priority.
     * @throws RemoteException If binder error happens.
     * @throws UnsupportedOperationException If the current android release doesn't support the API.
     */
    public int[] getThreadPriority(int pid, int tid, int uid) throws RemoteException {
        try {
            return mCarWatchdogDaemonHelper.getThreadPriority(pid, tid, uid);
        } catch (ServiceSpecificException e) {
            // Car watchdog daemon failed to get the priority.
            throw new IllegalStateException(e);
        }
    }

    @VisibleForTesting
    int getClientCount(int timeout) {
        return mWatchdogProcessHandler.getClientCount(timeout);
    }

    @VisibleForTesting
    void setOveruseHandlingDelay(long millis) {
        mWatchdogPerfHandler.setOveruseHandlingDelay(millis);
    }

    static File getWatchdogDirFile() {
        SystemInterface systemInterface = CarLocalServices.getService(SystemInterface.class);
        String systemCarDirPath = systemInterface == null ? FALLBACK_DATA_SYSTEM_CAR_DIR_PATH
                : systemInterface.getSystemCarDir().getAbsolutePath();
        return new File(systemCarDirPath, WATCHDOG_DIR_NAME);
    }

    private void notifyAllUserStates() {
        UserManager userManager = mContext.getSystemService(UserManager.class);
        List<UserHandle> users = userManager.getUserHandles(/* excludeDying= */ false);
        try {
            // TODO(b/152780162): reduce the number of RPC calls(isUserRunning).
            for (int i = 0; i < users.size(); ++i) {
                UserHandle user = users.get(i);
                int userState = userManager.isUserRunning(user)
                        ? UserState.USER_STATE_STARTED
                        : UserState.USER_STATE_STOPPED;
                mCarWatchdogDaemonHelper.notifySystemStateChange(StateType.USER_STATE,
                        user.getIdentifier(), userState);
                mWatchdogProcessHandler.updateUserState(user.getIdentifier(),
                        userState == UserState.USER_STATE_STOPPED);
            }
            if (DEBUG) {
                Slogf.d(TAG, "Notified car watchdog daemon of user states");
            }
        } catch (RemoteException | RuntimeException e) {
            // When car watchdog daemon is not connected, the {@link mCarWatchdogDaemonHelper}
            // throws IllegalStateException. Catch the exception to avoid crashing the process.
            Slogf.w(TAG, e, "Notifying latest user states failed");
        }
    }

    private void notifyPowerCycleChange(@PowerCycle int powerCycle) {
        // TODO(b/236876940): Change version check to TIRAMISU_2 when cherry picking to T-QPR2.
        if (!Car.getPlatformVersion().isAtLeast(VERSION_CODES.UPSIDE_DOWN_CAKE_0)
                && powerCycle == PowerCycle.POWER_CYCLE_SUSPEND_EXIT) {
            return;
        }
        try {
            mCarWatchdogDaemonHelper.notifySystemStateChange(
                    StateType.POWER_CYCLE, powerCycle, MISSING_ARG_VALUE);
            if (DEBUG) {
                Slogf.d(TAG, "Notified car watchdog daemon of power cycle(%d)", powerCycle);
            }
        } catch (RemoteException | RuntimeException e) {
            // When car watchdog daemon is not connected, the {@link mCarWatchdogDaemonHelper}
            // throws IllegalStateException. Catch the exception to avoid crashing the process.
            Slogf.w(TAG, e, "Notifying power cycle change to %d failed", powerCycle);
        }
    }

    private void notifyGarageModeChange(@GarageMode int garageMode) {
        try {
            mCarWatchdogDaemonHelper.notifySystemStateChange(
                    StateType.GARAGE_MODE, garageMode, MISSING_ARG_VALUE);
            if (DEBUG) {
                Slogf.d(TAG, "Notified car watchdog daemon of garage mode(%d)", garageMode);
            }
        } catch (RemoteException | RuntimeException e) {
            // When car watchdog daemon is not connected, the {@link mCarWatchdogDaemonHelper}
            // throws IllegalStateException. Catch the exception to avoid crashing the process.
            Slogf.w(TAG, e, "Notifying garage mode change to %d failed", garageMode);
        }
    }

    private void postRegisterToDaemonMessage() {
        runOnMain(() -> {
            synchronized (mLock) {
                mReadyToRespond = true;
            }
            registerToDaemon();
        });
    }

    private void registerToDaemon() {
        synchronized (mLock) {
            if (!mIsConnected || !mReadyToRespond) {
                return;
            }
        }
        try {
            mCarWatchdogDaemonHelper.registerCarWatchdogService(mWatchdogServiceForSystem);
            if (DEBUG) {
                Slogf.d(TAG, "CarWatchdogService registers to car watchdog daemon");
            }
        } catch (RemoteException | RuntimeException e) {
            // When car watchdog daemon is not connected, the {@link mCarWatchdogDaemonHelper}
            // throws IllegalStateException. Catch the exception to avoid crashing the process.
            Slogf.w(TAG, e, "Cannot register to car watchdog daemon");
        }
        notifyAllUserStates();
        CarPowerManagementService powerService =
                CarLocalServices.getService(CarPowerManagementService.class);
        if (powerService != null) {
            int powerState = powerService.getPowerState();
            int powerCycle = carPowerStateToPowerCycle(powerState);
            if (powerCycle >= 0) {
                notifyPowerCycleChange(powerCycle);
            } else {
                Slogf.i(TAG, "Skipping notifying %d power state", powerState);
            }
        }
        int garageMode;
        synchronized (mLock) {
            // To avoid race condition, fetch {@link mCurrentGarageMode} just before
            // the {@link notifyGarageModeChange} call. For instance, if {@code mCurrentGarageMode}
            // changes before the above {@link notifyPowerCycleChange} call returns,
            // the {@link garageMode}'s value will be out of date.
            garageMode = mCurrentGarageMode;
        }
        notifyGarageModeChange(garageMode);
    }

    private void unregisterFromDaemon() {
        try {
            mCarWatchdogDaemonHelper.unregisterCarWatchdogService(mWatchdogServiceForSystem);
            if (DEBUG) {
                Slogf.d(TAG, "CarWatchdogService unregisters from car watchdog daemon");
            }
        } catch (RemoteException | RuntimeException e) {
            // When car watchdog daemon is not connected, the {@link mCarWatchdogDaemonHelper}
            // throws IllegalStateException. Catch the exception to avoid crashing the process.
            Slogf.w(TAG, e, "Cannot unregister from car watchdog daemon");
        }
    }

    private void subscribePowerManagementService() {
        CarPowerManagementService powerService =
                CarLocalServices.getService(CarPowerManagementService.class);
        if (powerService == null) {
            Slogf.w(TAG, "Cannot get CarPowerManagementService");
            return;
        }
        powerService.registerListener(mCarPowerStateListener);
        powerService.addPowerPolicyListener(
                new CarPowerPolicyFilter.Builder().setComponents(PowerComponent.DISPLAY).build(),
                mCarDisplayPowerPolicyListener);
    }

    private void unsubscribePowerManagementService() {
        CarPowerManagementService powerService =
                CarLocalServices.getService(CarPowerManagementService.class);
        if (powerService == null) {
            Slogf.w(TAG, "Cannot get CarPowerManagementService");
            return;
        }
        powerService.unregisterListener(mCarPowerStateListener);
        powerService.removePowerPolicyListener(mCarDisplayPowerPolicyListener);
    }

    private void subscribeUserStateChange() {
        CarUserService userService = CarLocalServices.getService(CarUserService.class);
        if (userService == null) {
            Slogf.w(TAG, "Cannot get CarUserService");
            return;
        }
        UserLifecycleEventFilter userEventFilter =
                new UserLifecycleEventFilter.Builder()
                        .addEventType(USER_LIFECYCLE_EVENT_TYPE_STARTING)
                        .addEventType(USER_LIFECYCLE_EVENT_TYPE_SWITCHING)
                        .addEventType(USER_LIFECYCLE_EVENT_TYPE_UNLOCKING)
                        .addEventType(USER_LIFECYCLE_EVENT_TYPE_POST_UNLOCKED)
                        .addEventType(USER_LIFECYCLE_EVENT_TYPE_STOPPED).build();
        userService.addUserLifecycleListener(userEventFilter, (event) -> {
            if (!isEventAnyOfTypes(TAG, event, USER_LIFECYCLE_EVENT_TYPE_STARTING,
                    USER_LIFECYCLE_EVENT_TYPE_SWITCHING, USER_LIFECYCLE_EVENT_TYPE_UNLOCKING,
                    USER_LIFECYCLE_EVENT_TYPE_POST_UNLOCKED, USER_LIFECYCLE_EVENT_TYPE_STOPPED)) {
                return;
            }
            if (!Car.getPlatformVersion().isAtLeast(VERSION_CODES.TIRAMISU_1)
                    && !isEventAnyOfTypes(TAG, event,
                    USER_LIFECYCLE_EVENT_TYPE_STARTING, USER_LIFECYCLE_EVENT_TYPE_STOPPED)) {
                return;
            }

            int userId = event.getUserHandle().getIdentifier();
            int userState;
            String userStateDesc;
            switch (event.getEventType()) {
                case USER_LIFECYCLE_EVENT_TYPE_STARTING:
                    mWatchdogProcessHandler.updateUserState(userId, /*isStopped=*/ false);
                    userState = UserState.USER_STATE_STARTED;
                    userStateDesc = "STARTING";
                    break;
                case USER_LIFECYCLE_EVENT_TYPE_SWITCHING:
                    userState = UserState.USER_STATE_SWITCHING;
                    userStateDesc = "SWITCHING";
                    break;
                case USER_LIFECYCLE_EVENT_TYPE_UNLOCKING:
                    userState = UserState.USER_STATE_UNLOCKING;
                    userStateDesc = "UNLOCKING";
                    break;
                case USER_LIFECYCLE_EVENT_TYPE_POST_UNLOCKED:
                    userState = UserState.USER_STATE_POST_UNLOCKED;
                    userStateDesc = "POST_UNLOCKED";
                    break;
                case USER_LIFECYCLE_EVENT_TYPE_STOPPED:
                    mWatchdogProcessHandler.updateUserState(userId, /*isStopped=*/ true);
                    userState = UserState.USER_STATE_STOPPED;
                    userStateDesc = "STOPPING";
                    break;
                default:
                    return;
            }
            try {
                mCarWatchdogDaemonHelper.notifySystemStateChange(StateType.USER_STATE, userId,
                        userState);
                if (DEBUG) {
                    Slogf.d(TAG, "Notified car watchdog daemon user %d's user state, %s",
                            userId, userStateDesc);
                }
            } catch (RemoteException | RuntimeException e) {
                // When car watchdog daemon is not connected, the {@link mCarWatchdogDaemonHelper}
                // throws IllegalStateException. Catch the exception to avoid crashing the process.
                Slogf.w(TAG, e, "Notifying user state change failed");
            }
        });
    }

    private void subscribeBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(CAR_WATCHDOG_ACTION_DISMISS_RESOURCE_OVERUSE_NOTIFICATION);
        filter.addAction(ACTION_GARAGE_MODE_ON);
        filter.addAction(ACTION_GARAGE_MODE_OFF);
        filter.addAction(CAR_WATCHDOG_ACTION_LAUNCH_APP_SETTINGS);
        filter.addAction(CAR_WATCHDOG_ACTION_RESOURCE_OVERUSE_DISABLE_APP);
        filter.addAction(ACTION_USER_REMOVED);
        filter.addAction(ACTION_REBOOT);
        filter.addAction(ACTION_SHUTDOWN);

        mContext.registerReceiverForAllUsers(mBroadcastReceiver, filter,
                Car.PERMISSION_CONTROL_CAR_WATCHDOG_CONFIG, /* scheduler= */ null,
                Context.RECEIVER_NOT_EXPORTED);

        // The package data scheme applies only for the ACTION_PACKAGE_CHANGED action. So, add a
        // filter for this action separately. Otherwise, the broadcast receiver won't receive
        // notifications for other actions.
        IntentFilter packageChangedFilter = new IntentFilter();
        packageChangedFilter.addAction(ACTION_PACKAGE_CHANGED);
        packageChangedFilter.addDataScheme("package");

        mContext.registerReceiverForAllUsers(mBroadcastReceiver, packageChangedFilter,
                /* broadcastPermission= */ null, /* scheduler= */ null,
                Context.RECEIVER_NOT_EXPORTED);
    }

    private static int carPowerStateToPowerCycle(int powerState) {
        switch (powerState) {
            // SHUTDOWN_PREPARE covers suspend and shutdown.
            case CarPowerManager.STATE_SHUTDOWN_PREPARE:
                return PowerCycle.POWER_CYCLE_SHUTDOWN_PREPARE;
            case CarPowerManager.STATE_SHUTDOWN_ENTER:
            case CarPowerManager.STATE_SUSPEND_ENTER:
            case CarPowerManager.STATE_HIBERNATION_ENTER:
                return PowerCycle.POWER_CYCLE_SHUTDOWN_ENTER;
            case CarPowerManager.STATE_SUSPEND_EXIT:
            case CarPowerManager.STATE_HIBERNATION_EXIT:
                return PowerCycle.POWER_CYCLE_SUSPEND_EXIT;
            // ON covers resume.
            case CarPowerManager.STATE_ON:
                return PowerCycle.POWER_CYCLE_RESUME;
            default:
                Slogf.e(TAG, "Invalid power state: %d", powerState);
        }
        return -1;
    }

    private static String toGarageModeString(@GarageMode int garageMode) {
        switch (garageMode) {
            case GarageMode.GARAGE_MODE_OFF:
                return "GARAGE_MODE_OFF";
            case GarageMode.GARAGE_MODE_ON:
                return "GARAGE_MODE_ON";
            default:
                Slogf.e(TAG, "Invalid garage mode: %d", garageMode);
        }
        return "INVALID";
    }

    private static final class ICarWatchdogServiceForSystemImpl
            extends ICarWatchdogServiceForSystem.Stub {
        private final WeakReference<CarWatchdogService> mService;

        ICarWatchdogServiceForSystemImpl(CarWatchdogService service) {
            mService = new WeakReference<>(service);
        }

        @Override
        public void checkIfAlive(int sessionId, int timeout) {
            CarWatchdogService service = mService.get();
            if (service == null) {
                Slogf.w(TAG, "CarWatchdogService is not available");
                return;
            }
            service.mWatchdogProcessHandler.postHealthCheckMessage(sessionId);
        }

        @Override
        public void prepareProcessTermination() {
            Slogf.w(TAG, "CarWatchdogService is about to be killed by car watchdog daemon");
        }

        @Override
        public List<PackageInfo> getPackageInfosForUids(
                int[] uids, List<String> vendorPackagePrefixes) {
            if (ArrayUtils.isEmpty(uids)) {
                Slogf.w(TAG, "UID list is empty");
                return Collections.emptyList();
            }
            CarWatchdogService service = mService.get();
            if (service == null) {
                Slogf.w(TAG, "CarWatchdogService is not available");
                return Collections.emptyList();
            }
            return service.mPackageInfoHandler.getPackageInfosForUids(uids, vendorPackagePrefixes);
        }

        // TODO(b/269191275): This method was replaced by onLatestResourceStats in Android U.
        //  Make method no-op in Android W (N+2 releases).
        @Override
        public void latestIoOveruseStats(List<PackageIoOveruseStats> packageIoOveruseStats) {
            if (packageIoOveruseStats.isEmpty()) {
                Slogf.w(TAG, "Latest I/O overuse stats is empty");
                return;
            }
            CarWatchdogService service = mService.get();
            if (service == null) {
                Slogf.w(TAG, "CarWatchdogService is not available");
                return;
            }
            service.mWatchdogPerfHandler.latestIoOveruseStats(packageIoOveruseStats);
        }

        @Override
        public void onLatestResourceStats(List<ResourceStats> resourceStats) {
            // TODO(b/266008146): Handle the resourceUsageStats.
            if (resourceStats.isEmpty()) {
                Slogf.w(TAG, "Latest resource stats is empty");
                return;
            }
            CarWatchdogService service = mService.get();
            if (service == null) {
                Slogf.w(TAG, "CarWatchdogService is not available");
                return;
            }
            for (int i = 0; i < resourceStats.size(); i++) {
                ResourceStats stats = resourceStats.get(i);
                if (stats.resourceOveruseStats == null
                        || stats.resourceOveruseStats.packageIoOveruseStats.isEmpty()) {
                    Slogf.w(TAG, "Received latest I/O overuse stats is empty");
                    continue;
                }
                service.mWatchdogPerfHandler.latestIoOveruseStats(
                        stats.resourceOveruseStats.packageIoOveruseStats);
            }
        }

        @Override
        public void resetResourceOveruseStats(List<String> packageNames) {
            if (packageNames.isEmpty()) {
                Slogf.w(TAG, "Provided an empty package name to reset resource overuse stats");
                return;
            }
            CarWatchdogService service = mService.get();
            if (service == null) {
                Slogf.w(TAG, "CarWatchdogService is not available");
                return;
            }
            service.mWatchdogPerfHandler.resetResourceOveruseStats(new ArraySet<>(packageNames));
        }

        // TODO(b/273354756): This method was replaced by an async request/response pattern
        // Android U. Requests for the I/O stats are received through the requestTodayIoUsageStats
        // method. And responses are sent through the carwatchdog daemon via
        // ICarWatchdog#onTodayIoUsageStats. Make method no-op in Android W (N+2 releases).
        @Override
        public List<UserPackageIoUsageStats> getTodayIoUsageStats() {
            CarWatchdogService service = mService.get();
            if (service == null) {
                Slogf.w(TAG, "CarWatchdogService is not available");
                return Collections.emptyList();
            }
            return service.mWatchdogPerfHandler.getTodayIoUsageStats();
        }

        @Override
        public void requestAidlVhalPid() {
            CarWatchdogService service = mService.get();
            if (service == null) {
                Slogf.w(TAG, "CarWatchdogService is not available");
                return;
            }
            service.mWatchdogProcessHandler.asyncFetchAidlVhalPid();
        }

        @Override
        public void requestTodayIoUsageStats() {
            CarWatchdogService service = mService.get();
            if (service == null) {
                Slogf.w(TAG, "CarWatchdogService is not available");
                return;
            }
            service.mWatchdogPerfHandler.asyncFetchTodayIoUsageStats();
        }

        @Override
        public String getInterfaceHash() {
            return ICarWatchdogServiceForSystemImpl.HASH;
        }

        @Override
        public int getInterfaceVersion() {
            return ICarWatchdogServiceForSystemImpl.VERSION;
        }
    }
}
