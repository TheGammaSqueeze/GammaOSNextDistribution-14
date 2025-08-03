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

package com.android.car.remoteaccess;

import static android.car.remoteaccess.CarRemoteAccessManager.TASK_TYPE_CUSTOM;
import static android.car.remoteaccess.CarRemoteAccessManager.TASK_TYPE_ENTER_GARAGE_MODE;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;
import static android.content.Context.BIND_AUTO_CREATE;
import static android.content.Context.RECEIVER_NOT_EXPORTED;

import static com.android.car.CarServiceUtils.isEventOfType;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;
import static com.android.car.internal.common.CommonConstants.EMPTY_INT_ARRAY;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.car.Car;
import android.car.builtin.os.HandlerHelper;
import android.car.builtin.util.Slogf;
import android.car.feature.FeatureFlags;
import android.car.feature.FeatureFlagsImpl;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.ICarPowerStateListener;
import android.car.remoteaccess.CarRemoteAccessManager;
import android.car.remoteaccess.ICarRemoteAccessCallback;
import android.car.remoteaccess.ICarRemoteAccessService;
import android.car.remoteaccess.RemoteTaskClientRegistrationInfo;
import android.car.remoteaccess.TaskScheduleInfo;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.UserLifecycleEventFilter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.PackageInfoFlags;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.XmlResourceParser;
import android.hardware.automotive.remoteaccess.IRemoteAccess;
import android.hardware.automotive.remoteaccess.ScheduleInfo;
import android.hardware.automotive.remoteaccess.TaskType;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.CarServiceBase;
import com.android.car.CarServiceUtils;
import com.android.car.R;
import com.android.car.hal.PowerHalService;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.power.CarPowerManagementService;
import com.android.car.remoteaccess.RemoteAccessStorage.ClientIdEntry;
import com.android.car.remoteaccess.hal.RemoteAccessHalCallback;
import com.android.car.remoteaccess.hal.RemoteAccessHalWrapper;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.user.CarUserService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service to implement CarRemoteAccessManager API.
 */
public final class CarRemoteAccessService extends ICarRemoteAccessService.Stub
        implements CarServiceBase {

    private static final String TAG = CarLog.tagFor(CarRemoteAccessService.class);
    private static final boolean DEBUG = Slogf.isLoggable(TAG, Log.DEBUG);

    // Tag names for remote_access_serverless_client_map.xml.
    private static final String XML_TAG_CLIENT_ID = "ClientId";
    private static final String XML_TAG_PACKAGE_NAME = "PackageName";
    private static final String XML_TAG_SERVERLESS_CLIENT = "ServerlessClient";
    private static final String XML_TAG_SERVERLESS_CLIENT_MAP = "ServerlessClientMap";

    private static final int MILLI_TO_SECOND = 1000;
    private static final String TASK_PREFIX = "task";
    private static final String CLIENT_PREFIX = "client";
    private static final int RANDOM_STRING_LENGTH = 12;
    private static final int MIN_SYSTEM_UPTIME_FOR_REMOTE_ACCESS_IN_SEC = 30;
    private static final Duration PACKAGE_SEARCH_DELAY = Duration.ofSeconds(1);
    // Add some randomness to package search delay to avoid thundering herd issue.
    private static final Duration PACKAGE_SEARCH_DELAY_RAND_RANGE = Duration.ofMillis(100);
    // Remote task client can use up to 30 seconds to initialize and upload necessary info to the
    // server.
    private static final long ALLOWED_TIME_FOR_REMOTE_TASK_CLIENT_INIT_MS = 30_000;
    private static final long SHUTDOWN_WARNING_MARGIN_IN_MS = 5000;
    private static final long INVALID_ALLOWED_SYSTEM_UPTIME = -1;
    private static final int DEFAULT_NOTIFY_AP_STATE_RETRY_SLEEP_IN_MS = 100;
    private static final int DEFAULT_NOTIFY_AP_STATE_MAX_RETRY = 10;
    // The buffer time after all the tasks for a specific remote task client service is completed
    // before we unbind the service.
    private static final int TASK_UNBIND_DELAY_MS = 1000;
    // The max time in ms allowed for a task after it is received by the car service, before the
    // remote task client service is started and it is delivered to that service. This period will
    // include waiting for the remote task client service to be started if it is not already bound.
    private static final int MAX_TASK_PENDING_MS = 60_000;

    private final Object mLock = new Object();
    private final Context mContext;
    private final PackageManager mPackageManager;
    private final HandlerThread mHandlerThread =
            CarServiceUtils.getHandlerThread(getClass().getSimpleName());
    private final RemoteTaskClientServiceHandler mHandler =
            new RemoteTaskClientServiceHandler(mHandlerThread.getLooper(), this);
    private long mAllowedTimeForRemoteTaskClientInitMs =
            ALLOWED_TIME_FOR_REMOTE_TASK_CLIENT_INIT_MS;
    private long mTaskUnbindDelayMs = TASK_UNBIND_DELAY_MS;
    private long mMaxTaskPendingMs = MAX_TASK_PENDING_MS;
    private final AtomicLong mTaskCount = new AtomicLong(/* initialValue= */ 0);
    private final AtomicLong mClientCount = new AtomicLong(/* initialValue= */ 0);
    // Whether in vehicle task scheduling is supported by remote access HAL. Only set during init.
    private boolean mIsHalTaskScheduleSupported;
    @GuardedBy("mLock")
    private final ArrayMap<String, String> mUidByClientId = new ArrayMap<>();
    @GuardedBy("mLock")
    private final ArrayMap<String, ArrayList<RemoteTask>> mTasksToBeNotifiedByClientId =
            new ArrayMap<>();
    @GuardedBy("mLock")
    private final ArrayMap<String, ClientToken> mClientTokenByUidName = new ArrayMap<>();
    @GuardedBy("mLock")
    private final ArrayMap<String, RemoteTaskClientServiceInfo> mClientServiceInfoByUid =
            new ArrayMap<>();
    @GuardedBy("mLock")
    private boolean mIsReadyForRemoteTask;
    @GuardedBy("mLock")
    private boolean mIsWakeupRequired;
    @GuardedBy("mLock")
    private int mNotifyApStateChangeRetryCount;
    @GuardedBy("mLock")
    private final ArrayMap<String, Integer> mUidByName = new ArrayMap<>();
    @GuardedBy("mLock")
    private final SparseArray<String> mNameByUid = new SparseArray<>();
    @GuardedBy("mLock")
    private ArrayMap<String, String> mServerlessClientIdsByPackageName = new ArrayMap<>();

    private final RemoteAccessStorage mRemoteAccessStorage;

    private FeatureFlags mFeatureFlags = new FeatureFlagsImpl();

    /**
     * Sets fake feature flag for unit testing.
     */
    @VisibleForTesting
    public void setFeatureFlags(FeatureFlags fakeFeatureFlags) {
        mFeatureFlags = fakeFeatureFlags;
    }

    private final ICarPowerStateListener mCarPowerStateListener =
            new ICarPowerStateListener.Stub() {
        @Override
        public void onStateChanged(int state, long expirationTimeMs) {
            // isReadyForRemoteTask and isWakeupRequired are only valid when apStateChangeRequired
            // is true.
            Slogf.i(TAG, "power state change, new state: %d", state);
            boolean apStateChangeRequired = false;
            boolean isReadyForRemoteTask = false;
            boolean isWakeupRequired = false;
            boolean needsComplete = false;

            switch (state) {
                case CarPowerManager.STATE_SHUTDOWN_PREPARE:
                    apStateChangeRequired = true;
                    isReadyForRemoteTask = false;
                    isWakeupRequired = false;

                    needsComplete = true;
                    // If this shutdown is initiated by remote access service, then all remote task
                    // client services should already be unbound and this will do nothing. This is
                    // useful for cases when the shutdown is not initiated by us (e.g. by user).
                    unbindAllServices();
                    break;
                case CarPowerManager.STATE_WAIT_FOR_VHAL:
                case CarPowerManager.STATE_SUSPEND_EXIT:
                case CarPowerManager.STATE_HIBERNATION_EXIT:
                    apStateChangeRequired = true;
                    isReadyForRemoteTask = true;
                    isWakeupRequired = false;
                    break;
                case CarPowerManager.STATE_POST_SHUTDOWN_ENTER:
                case CarPowerManager.STATE_POST_SUSPEND_ENTER:
                case CarPowerManager.STATE_POST_HIBERNATION_ENTER:
                    apStateChangeRequired = true;
                    isReadyForRemoteTask = false;
                    isWakeupRequired = true;

                    needsComplete = true;
                    break;
            }
            if (apStateChangeRequired) {
                synchronized (mLock) {
                    mIsReadyForRemoteTask = isReadyForRemoteTask;
                    mIsWakeupRequired = isWakeupRequired;
                }
                mHandler.cancelNotifyApStateChange();
                if (!mRemoteAccessHalWrapper.notifyApStateChange(
                        isReadyForRemoteTask, isWakeupRequired)) {
                    Slogf.e(TAG, "Cannot notify AP state change according to power state(%d)",
                            state);
                }
            }
            if (needsComplete) {
                mPowerService.finished(state, this);
            }
        }
    };

    @GuardedBy("mLock")
    private boolean isServerlessClientLocked(String clientId) {
        for (int i = 0; i < mServerlessClientIdsByPackageName.size(); i++) {
            if (mServerlessClientIdsByPackageName.valueAt(i).equals(clientId)) {
                return true;
            }
        }
        return false;
    }

    private void maybeStartNewRemoteTask(String clientId) {
        ICarRemoteAccessCallback callback;
        List<RemoteTask> remoteTasksToNotify = null;
        RemoteTaskClientServiceInfo serviceInfo = null;
        int taskMaxDurationInSec;
        long taskMaxDurationInMs;
        String uidName;
        ClientToken token;

        synchronized (mLock) {
            if (mTasksToBeNotifiedByClientId.get(clientId) == null) {
                return;
            }
            taskMaxDurationInMs = calcTaskMaxDurationInMsLocked();
            taskMaxDurationInSec = (int) (taskMaxDurationInMs / MILLI_TO_SECOND);
            if (taskMaxDurationInSec <= 0) {
                Slogf.w(TAG, "onRemoteTaskRequested: system shutdown was supposed to start, "
                            + "but still on: expected shutdown time=%d, current time=%d",
                            mShutdownTimeInMs, SystemClock.uptimeMillis());
                // Remove all tasks for this client ID.
                Slogf.w(TAG, "Removing all the pending tasks for client ID: %s", clientId);
                mTasksToBeNotifiedByClientId.remove(clientId);
                return;
            }
            // If this clientId has never been stored on this device before,
            uidName = mUidByClientId.get(clientId);
            // uidName might be null for serverless remote task client if the device just booted up
            // and the client has not called addCarRemoteTaskClient yet. For regular remote task
            // client, the client ID must already be stored in database, so during
            // populatePackageClientIdMapping the mUidByClientId will contain clientId and uidName
            // must not be null.
            if (!isServerlessClientLocked(clientId) && uidName == null) {
                Slogf.w(TAG, "Cannot notify task: client(%s) is not registered.", clientId);
                Slogf.w(TAG, "Removing all the pending tasks for client ID: %s", clientId);
                mTasksToBeNotifiedByClientId.remove(clientId);
                return;
            }
            if (uidName != null) {
                serviceInfo = mClientServiceInfoByUid.get(uidName);
            }
            if (serviceInfo == null) {
                Slogf.w(TAG, "Notifying task is delayed: the remote client service information "
                        + "for %s is not registered yet", clientId);
                // We don't have to start the service explicitly because it will be started
                // after searching for remote task client service is done.
                return;
            }
            // Token must not be null if mUidByClientId contains clientId.
            token = mClientTokenByUidName.get(uidName);
            callback = token.getCallback();
            if (callback != null) {
                remoteTasksToNotify = popTasksFromPendingQueueLocked(clientId);
            }
        }
        // Always try to bind the remote task client service. This will starts the service if
        // it is not active. This will also keep the service alive during the task period.
        startRemoteTaskClientService(serviceInfo, uidName, taskMaxDurationInMs);

        if (callback == null) {
            Slogf.w(TAG, "Notifying task is delayed: the callback for token: %s "
                    + "is not registered yet", token);
            return;
        }

        if (remoteTasksToNotify != null && !remoteTasksToNotify.isEmpty()) {
            invokeTaskRequestCallbacks(serviceInfo.getServiceConnection(), callback, clientId,
                    remoteTasksToNotify, taskMaxDurationInSec);
        }
    }

    private final RemoteAccessHalCallback mHalCallback = new RemoteAccessHalCallback() {
        @Override
        public void onRemoteTaskRequested(String clientId, byte[] data) {
            if (DEBUG) {
                Slogf.d(TAG, "Remote task is requested through the HAL to client(%s)", clientId);
            }
            String taskId = generateNewTaskId();
            long now = SystemClock.uptimeMillis();
            long timeoutInMs = now + mMaxTaskPendingMs;
            synchronized (mLock) {
                pushTaskToPendingQueueLocked(clientId, new RemoteTask(taskId, data, clientId,
                        timeoutInMs));
            }
            maybeStartNewRemoteTask(clientId);
        }
    };
    private RemoteAccessHalWrapper mRemoteAccessHalWrapper;
    private PowerHalService mPowerHalService;
    private final UserManager mUserManager;
    private final long mShutdownTimeInMs;
    private final long mAllowedSystemUptimeMs;
    private final int mNotifyApStateChangeMaxRetry;
    private final int mNotifyApStateChangeRetrySleepInMs;
    private final BroadcastReceiver mPackageRemovedReceiver;

    private String mWakeupServiceName = "";
    private String mVehicleId = "";
    private String mProcessorId = "";
    @GuardedBy("mLock")
    private int mNextPowerState;
    @GuardedBy("mLock")
    private boolean mRunGarageMode;
    private CarPowerManagementService mPowerService;
    private AtomicBoolean mInitialized;

    private CarRemoteAccessServiceDep mDep;

    public CarRemoteAccessService(Context context, SystemInterface systemInterface,
            PowerHalService powerHalService) {
        this(context, systemInterface, powerHalService, /* dep= */ null,
                /* remoteAccessHal= */ null, /* remoteAccessStorage= */ null,
                INVALID_ALLOWED_SYSTEM_UPTIME, /* inMemoryStorage= */ false);
    }

    /**
     * Dependencies for stubbing.
     */
    @VisibleForTesting
    public interface CarRemoteAccessServiceDep {
        /**
         * Gets the calling UID. Must be used in a binder context.
         */
        int getCallingUid();

        /**
         * Gets the current user.
         */
        int getCurrentUser();
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    private class CarRemoteAccessServiceDepImpl implements CarRemoteAccessServiceDep {
        public int getCallingUid() {
            return Binder.getCallingUid();
        }

        public int getCurrentUser() {
            return ActivityManager.getCurrentUser();
        }
    }

    @VisibleForTesting
    public CarRemoteAccessService(Context context, SystemInterface systemInterface,
            PowerHalService powerHalService, @Nullable CarRemoteAccessServiceDep dep,
            @Nullable IRemoteAccess remoteAccessHal,
            @Nullable RemoteAccessStorage remoteAccessStorage, long allowedSystemUptimeMs,
            boolean inMemoryStorage) {
        mContext = context;
        mUserManager = mContext.getSystemService(UserManager.class);
        mPowerHalService = powerHalService;
        mDep = dep != null ? dep : new CarRemoteAccessServiceDepImpl();
        mPackageManager = mContext.getPackageManager();
        mRemoteAccessHalWrapper = new RemoteAccessHalWrapper(mHalCallback, remoteAccessHal);
        mAllowedSystemUptimeMs = allowedSystemUptimeMs == INVALID_ALLOWED_SYSTEM_UPTIME
                ? getAllowedSystemUptimeForRemoteTaskInMs() : allowedSystemUptimeMs;
        mShutdownTimeInMs = SystemClock.uptimeMillis() + mAllowedSystemUptimeMs;
        mRemoteAccessStorage = remoteAccessStorage != null ? remoteAccessStorage :
                new RemoteAccessStorage(context, systemInterface, inMemoryStorage);
        // TODO(b/263807920): CarService restart should be handled.
        systemInterface.scheduleActionForBootCompleted(() -> searchForRemoteTaskClientPackages(),
                PACKAGE_SEARCH_DELAY, PACKAGE_SEARCH_DELAY_RAND_RANGE);
        mNotifyApStateChangeMaxRetry = getNotifyApStateChangeMaxRetry();
        mNotifyApStateChangeRetrySleepInMs = getNotifyApStateChangeRetrySleepInMs();
        mPackageRemovedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onReceiveIntent(context, intent);
            }
        };
    }

    /**
     * handles {@code ACTION_PACKAGE_REMOVED} intent.
     */
    @SuppressWarnings("unused")
    private void onReceiveIntent(Context context, Intent intent) {
        if (!intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED)) {
            Slogf.w(TAG, "Received an unknown intent, ignore: " + intent);
            return;
        }
        String packageName = Uri.parse(intent.getDataString()).getSchemeSpecificPart();
        String clientId;
        synchronized (mLock) {
            if (!mServerlessClientIdsByPackageName.containsKey(packageName)) {
                // Not a serverless client, ignore.
                return;
            }
            clientId = mServerlessClientIdsByPackageName.get(packageName);
        }
        try {
            mRemoteAccessHalWrapper.unscheduleAllTasks(clientId);
        } catch (ServiceSpecificException | RemoteException e) {
            Slogf.e(TAG, e, "failed to unschedule tasks for package: %s when it is removed",
                    packageName);
        }
    }

    @VisibleForTesting
    public void setRemoteAccessHalWrapper(RemoteAccessHalWrapper remoteAccessHalWrapper) {
        mRemoteAccessHalWrapper = remoteAccessHalWrapper;
    }

    @VisibleForTesting
    public void setPowerHal(PowerHalService powerHalService) {
        mPowerHalService = powerHalService;
    }

    @VisibleForTesting
    public void setAllowedTimeForRemoteTaskClientInitMs(long allowedTimeForRemoteTaskClientInitMs) {
        mAllowedTimeForRemoteTaskClientInitMs = allowedTimeForRemoteTaskClientInitMs;
    }

    @VisibleForTesting
    public void setTaskUnbindDelayMs(long taskUnbindDelayMs) {
        mTaskUnbindDelayMs = taskUnbindDelayMs;
    }

    @VisibleForTesting
    public void setMaxTaskPendingMs(long maxTaskPendingMs) {
        mMaxTaskPendingMs = maxTaskPendingMs;
    }

    @Override
    public void init() {
        mPowerService = CarLocalServices.getService(CarPowerManagementService.class);
        populatePackageClientIdMapping();
        mRemoteAccessHalWrapper.init();
        // This must be called after remoteAccessHalWrapper init complete.
        mIsHalTaskScheduleSupported = mRemoteAccessHalWrapper.isTaskScheduleSupported();
        try {
            mWakeupServiceName = mRemoteAccessHalWrapper.getWakeupServiceName();
            mVehicleId = mRemoteAccessHalWrapper.getVehicleId();
            mProcessorId = mRemoteAccessHalWrapper.getProcessorId();
        } catch (IllegalStateException e) {
            Slogf.e(TAG, e, "Cannot get vehicle/processor/service info from remote access HAL");
        }
        synchronized (mLock) {
            mNextPowerState = getLastShutdownState();
            mIsReadyForRemoteTask = true;
            mIsWakeupRequired = false;
        }

        mPowerService.registerListenerWithCompletion(mCarPowerStateListener);

        long delayForShutdowWarningMs = mAllowedSystemUptimeMs - SHUTDOWN_WARNING_MARGIN_IN_MS;
        if (delayForShutdowWarningMs > 0) {
            mHandler.postNotifyShutdownStarting(delayForShutdowWarningMs);
        }
        mHandler.postWrapUpRemoteAccessService(mAllowedSystemUptimeMs);
        mHandler.postNotifyApStateChange(0);

        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        // We are registering receiver for a system broadcast so the flag for NOT_EXPORTED does
        // not really matter. But we are not expecting intents from other apps, so set it to
        // NOT_EXPORTED to be safe.
        mContext.registerReceiver(mPackageRemovedReceiver, filter, RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void release() {
        Slogf.i(TAG, "release CarRemoteAccessService");
        mHandler.cancelAll();
        mContext.unregisterReceiver(mPackageRemovedReceiver);
        mRemoteAccessHalWrapper.release();
        mRemoteAccessStorage.release();
    }

    private void printMap(IndentingPrintWriter writer, ArrayMap<?, ?> map) {
        writer.increaseIndent();
        for (int i = 0; i < map.size(); i++) {
            writer.printf("%d: %s ==> %s\n", i, map.keyAt(i), map.valueAt(i));
        }
        writer.decreaseIndent();
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        synchronized (mLock) {
            writer.println("*Car Remote Access Service*");
            writer.printf("mShutdownTimeInMs: %d\n", mShutdownTimeInMs);
            writer.printf("mNextPowerState: %d\n", mNextPowerState);
            writer.printf("mRunGarageMode: %b\n", mRunGarageMode);
            writer.printf("mWakeupServiceName: %s\n", mWakeupServiceName);
            writer.printf("mVehicleId: %s\n", mVehicleId);
            writer.printf("mProcessorId: %s\n", mProcessorId);
            writer.println("mClientTokenByUidName:");
            printMap(writer, mClientTokenByUidName);
            writer.println("mClientServiceInfoByUid:");
            printMap(writer, mClientServiceInfoByUid);
            writer.println("mUidAByName:");
            printMap(writer, mUidByName);
            writer.println("mUidByClientId:");
            printMap(writer, mUidByClientId);
            writer.println("mServerlessClientIdsByPackageName:");
            printMap(writer, mServerlessClientIdsByPackageName);
            writer.println("mTasksToBeNotifiedByClientId");
            writer.increaseIndent();
            for (int i = 0; i < mTasksToBeNotifiedByClientId.size(); i++) {
                String clientId = mTasksToBeNotifiedByClientId.keyAt(i);
                List<String> taskIds = new ArrayList<>();
                List<RemoteTask> tasks = mTasksToBeNotifiedByClientId.valueAt(i);
                for (int j = 0; j < tasks.size(); j++) {
                    taskIds.add(tasks.get(j).id);
                }
                writer.printf("%d: %s ==> %s\n", i, clientId, taskIds);
            }
            writer.decreaseIndent();
            writer.println("active task count by Uid:");
            writer.increaseIndent();
            for (int i = 0; i < mClientServiceInfoByUid.size(); i++) {
                String uidName = mClientServiceInfoByUid.keyAt(i);
                RemoteTaskClientServiceInfo serviceInfo = mClientServiceInfoByUid.valueAt(i);
                int count = 0;
                if (serviceInfo.getServiceConnection() != null) {
                    count = serviceInfo.getServiceConnection().getActiveTaskCount();
                }
                writer.printf("%d: %s ==> %d\n", i, uidName, count);
            }
            writer.decreaseIndent();
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {
        synchronized (mLock) {
            for (int i = 0; i < mServerlessClientIdsByPackageName.size(); i++) {
                long fieldToken = proto.start(CarRemoteAccessDumpProto.SERVERLESS_CLIENTS);
                proto.write(CarRemoteAccessDumpProto.ServerlessClientInfo.CLIENT_ID,
                        mServerlessClientIdsByPackageName.valueAt(i));
                proto.write(CarRemoteAccessDumpProto.ServerlessClientInfo.PACKAGE_NAME,
                        mServerlessClientIdsByPackageName.keyAt(i));
                proto.end(fieldToken);
            }
        }
    }

    private String getPackageNameForCallingUid(int callingUid) {
        String[] packageNames = mPackageManager.getPackagesForUid(callingUid);
        if (packageNames.length != 1) {
            throw new IllegalStateException("Failed to get package name, "
                    + "sharedUserId must not be used by app.");
        }
        return packageNames[0];
    }

    /**
     * Registers {@code ICarRemoteAccessCallback}.
     *
     * <p>When a callback is registered for a package, calling {@code addCarRemoteTaskClient} will
     * replace the registered callback with the new one.
     *
     * @param callback {@code ICarRemoteAccessCallback} that listens to remote access events.
     * @throws IllegalArgumentException When {@code callback} is {@code null}.
     */
    @Override
    public void addCarRemoteTaskClient(ICarRemoteAccessCallback callback) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_USE_REMOTE_ACCESS);
        Preconditions.checkArgument(callback != null, "callback cannot be null");
        int callingUid = mDep.getCallingUid();
        ClientToken token;
        String uidName;
        synchronized (mLock) {
            uidName = getNameForUidLocked(callingUid);
            Slogf.i(TAG, "addCarRemoteTaskClient from uid: %s", uidName);

            String packageName = getPackageNameForCallingUid(callingUid);
            boolean isServerless = mServerlessClientIdsByPackageName.containsKey(packageName);

            if (isServerless) {
                Slogf.i(TAG, "addCarRemoteTaskClient called from a serverless remote access "
                        + "client: " + packageName);
            }

            token = mClientTokenByUidName.get(uidName);

            if (isServerless && token != null
                    && !(token.getClientId().equals(
                            mServerlessClientIdsByPackageName.get(packageName)))) {
                Slogf.w(TAG, "client: " + packageName + " is a serverless remote access client "
                        + "but has a different client ID, clear the previous client ID record");
                // In a rare case if the same client was previously not configured as a serverless
                // client, hence had a different client ID. But now it has become a serverless
                // remote access client. We must clear the previous record.
                mUidByClientId.remove(token.getClientId());
                mClientTokenByUidName.remove(uidName);
                if (token.getCallback() != null) {
                    token.getCallback().asBinder().unlinkToDeath(token, /* flags= */ 0);
                }
                token = null;
            }

            if (token != null) {
                // This is an already registered client.
                ICarRemoteAccessCallback oldCallback = token.getCallback();
                if (oldCallback != null) {
                    oldCallback.asBinder().unlinkToDeath(token, /* flags= */ 0);
                }
            } else {
                // This is a new client.
                String clientId;
                if (isServerless) {
                    // This is a serverless remote task client. Use the preconfigured client ID.
                    clientId = mServerlessClientIdsByPackageName.get(packageName);
                } else {
                    // For regular remote task client, create a new random client ID.
                    clientId = generateNewClientId();
                }
                // Creates a new client token with a null callback. The callback will be registered
                // in postRegistrationUpdated.
                token = new ClientToken(clientId, System.currentTimeMillis(), isServerless);
                mClientTokenByUidName.put(uidName, token);
                mUidByClientId.put(token.getClientId(), uidName);
            }
            try {
                callback.asBinder().linkToDeath(token, /* flags= */ 0);
            } catch (RemoteException e) {
                token.setCallback(null);
                throw new IllegalStateException("Failed to linkToDeath callback");
            }
        }
        saveClientIdInDb(token, uidName);
        postRegistrationUpdated(callback, token);
    }

    /**
     * Unregisters {@code ICarRemoteAccessCallback}.
     *
     * @param callback {@code ICarRemoteAccessCallback} that listens to remote access events.
     * @throws IllegalArgumentException When {@code callback} is {@code null}.
     */
    @Override
    public void removeCarRemoteTaskClient(ICarRemoteAccessCallback callback) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_USE_REMOTE_ACCESS);
        Preconditions.checkArgument(callback != null, "callback cannot be null");
        int callingUid = mDep.getCallingUid();
        RemoteTaskClientServiceConnection connection = null;
        String uidName;
        synchronized (mLock) {
            uidName = getNameForUidLocked(callingUid);
            Slogf.i(TAG, "removeCarRemoteTaskClient from uid: %s", uidName);
            ClientToken token = mClientTokenByUidName.get(uidName);
            if (token == null) {
                Slogf.w(TAG, "Cannot remove callback. Callback has not been registered for %s",
                        uidName);
                return;
            }
            if (token.getCallback() == null) {
                Slogf.w(TAG, "The callback to remove is already dead, do nothing");
                return;
            }
            if (token.getCallback().asBinder() != callback.asBinder()) {
                Slogf.w(TAG, "Cannot remove callback. Provided callback is not the same as the "
                        + "registered callback for %s", uidName);
                return;
            }
            callback.asBinder().unlinkToDeath(token, /* flags= */ 0);
            token.setCallback(null);
            connection = getServiceConnectionLocked(uidName);
        }
        if (connection == null) {
            Slogf.w(TAG, "No active service connection for uid: %s", uidName);
            return;
        }
        connection.removeAllActiveTasks();
        Slogf.i(TAG, "All active tasks removed for uid: %s, after %d ms, check whether we should "
                + "shutdown", uidName, mTaskUnbindDelayMs);
        mHandler.postMaybeShutdown(/* delayMs= */ mTaskUnbindDelayMs);
    }

    @GuardedBy("mLock")
    private String getUidNameForPackageNameLocked(String packageName) {
        String uidName = "";
        try {
            uidName = getNameForUidLocked(mPackageManager.getPackageUidAsUser(
                    packageName, PackageInfoFlags.of(0), /* uid= */ 0));
        } catch (Exception e) {
            // Do thing.
        }
        return uidName;
    }

    /**
     * Used in testing only. Clear up the stored client token information for the package.
     */
    @GuardedBy("mLock")
    private void clearUpClientTokenForPackageLocked(String packageName) {
        String uidName = getUidNameForPackageNameLocked(packageName);
        String clientId = "";
        for (int i = 0; i < mClientTokenByUidName.size(); i++) {
            if (!mClientTokenByUidName.keyAt(i).equals(uidName)) {
                continue;
            }
            clientId = mClientTokenByUidName.valueAt(i).getClientId();
            break;
        }
        if (!clientId.isEmpty()) {
            Slogf.i(TAG, "Clearing stored client token for uidName: %s because the client is now a "
                    + "serverless remote task client", uidName);
            mClientTokenByUidName.remove(uidName);
            mUidByClientId.remove(clientId);
        }
    }

    /**
     * {@link android.car.remoteaccess.CarRemoteAccessManager#addServerlessRemoteTaskClient}
     */
    @Override
    public void addServerlessRemoteTaskClient(String packageName, String clientId) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CONTROL_REMOTE_ACCESS);
        Preconditions.checkArgument(packageName != null, "packageName must not be null");
        Preconditions.checkArgument(clientId != null, "clientId must not be null");
        synchronized (mLock) {
            Slogf.i(TAG, "Adding a new test serverless remote task client, packageName: %s, "
                    + "clientId: %s", packageName, clientId);
            if (mServerlessClientIdsByPackageName.containsKey(packageName)) {
                String message = "The package: " + packageName
                        + " is already an serverless remote task client";
                Slogf.w(TAG, message);
                throw new IllegalArgumentException(message);
            }
            for (int i = 0; i < mServerlessClientIdsByPackageName.size(); i++) {
                if (mServerlessClientIdsByPackageName.valueAt(i).equals(clientId)) {
                    String message = "The client ID: " + clientId
                            + " is already used, pick a different client ID";
                    Slogf.w(TAG, message);
                    throw new IllegalArgumentException(message);
                }
            }
            mServerlessClientIdsByPackageName.put(packageName, clientId);
            clearUpClientTokenForPackageLocked(packageName);
        }
    }

    /**
     * {@link android.car.remoteaccess.CarRemoteAccessManager#removeServerlessRemoteTaskClient}
     */
    @Override
    public void removeServerlessRemoteTaskClient(String packageName) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CONTROL_REMOTE_ACCESS);
        Preconditions.checkArgument(packageName != null, "packageName must not be null");
        synchronized (mLock) {
            mServerlessClientIdsByPackageName.remove(packageName);
            clearUpClientTokenForPackageLocked(packageName);
        }
    }

    /**
     * {@link android.car.remoteaccess.CarRemoteAccessManager#isTaskScheduleSupported}
     */
    @Override
    public boolean isTaskScheduleSupported() {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CONTROL_REMOTE_ACCESS);
        if (!mIsHalTaskScheduleSupported) {
            Slogf.i(TAG, "isTaskScheduleSupported returns false because remote access HAL does not"
                    + " support task scheduling");
            return false;
        }
        int callingUid = mDep.getCallingUid();
        String packageName = getPackageNameForCallingUid(callingUid);
        synchronized (mLock) {
            if (!mServerlessClientIdsByPackageName.containsKey(packageName)) {
                Slogf.i(TAG, "isTaskScheduleSupported returns false the client is not a serverless"
                        + " remote task client");
                return false;
            }
        }
        Slogf.i(TAG, "isTaskScheduleSupported returns true");
        return true;

    }

    private String getServerlessCallerClientId() throws IllegalStateException {
        if (!mIsHalTaskScheduleSupported) {
            throw new IllegalStateException("task schedule is not supported by HAL");
        }
        int callingUid = mDep.getCallingUid();
        String packageName = getPackageNameForCallingUid(callingUid);
        synchronized (mLock) {
            if (mServerlessClientIdsByPackageName.containsKey(packageName)) {
                return mServerlessClientIdsByPackageName.get(packageName);
            }
        }
        throw new IllegalStateException("Caller is not a serverless remote task client");
    }

    /**
     * {@link android.car.remoteaccess.CarRemoteAccessManager#scheduleTask}
     */
    @Override
    public void scheduleTask(TaskScheduleInfo scheduleInfo) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CONTROL_REMOTE_ACCESS);
        Preconditions.checkArgument(scheduleInfo != null, "scheduleInfo must not be null");
        Preconditions.checkArgument(scheduleInfo.scheduleId != null, "scheduleId must not be null");
        Preconditions.checkArgument(scheduleInfo.taskData != null, "task data must not be null");
        Preconditions.checkArgument(scheduleInfo.count >= 0, "count must >= 0");
        Preconditions.checkArgument(scheduleInfo.startTimeInEpochSeconds > 0,
                "startTimeInEpochSeconds must > 0");
        Preconditions.checkArgument(scheduleInfo.periodicInSeconds >= 0,
                "periodicInSeconds must >= 0");
        ScheduleInfo halScheduleInfo = new ScheduleInfo();
        String clientId = getServerlessCallerClientId();
        halScheduleInfo.clientId = clientId;
        switch (scheduleInfo.taskType) {
            case TASK_TYPE_CUSTOM:
                halScheduleInfo.taskType = TaskType.CUSTOM;
                break;
            case TASK_TYPE_ENTER_GARAGE_MODE:
                halScheduleInfo.taskType = TaskType.ENTER_GARAGE_MODE;
                break;
            default:
                throw new IllegalArgumentException("Unsupported task type: "
                        + scheduleInfo.taskType);
        }
        halScheduleInfo.scheduleId = scheduleInfo.scheduleId;
        halScheduleInfo.taskData = scheduleInfo.taskData;
        halScheduleInfo.count = scheduleInfo.count;
        halScheduleInfo.startTimeInEpochSeconds = scheduleInfo.startTimeInEpochSeconds;
        halScheduleInfo.periodicInSeconds = scheduleInfo.periodicInSeconds;
        try {
            mRemoteAccessHalWrapper.scheduleTask(halScheduleInfo);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new ServiceSpecificException(SERVICE_ERROR_CODE_GENERAL,
                    "failed to call IRemoteAccess.scheduleTask with scheduleInfo: "
                    + halScheduleInfo + ", error: " + e);
        }
    }

    /**
     * {@link android.car.remoteaccess.CarRemoteAccessManager#unscheduleTask}
     */
    @Override
    public void unscheduleTask(String scheduleId) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CONTROL_REMOTE_ACCESS);
        Preconditions.checkArgument(scheduleId != null, "scheduleId must not be null");
        String clientId = getServerlessCallerClientId();
        try {
            mRemoteAccessHalWrapper.unscheduleTask(clientId, scheduleId);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new ServiceSpecificException(SERVICE_ERROR_CODE_GENERAL,
                    "failed to call IRemoteAccess.unscheduleTask with clientId: "
                    + clientId + ", scheduleId: " + scheduleId+ ", error: " + e);
        }
    }

    /**
     * {@link android.car.remoteaccess.CarRemoteAccessManager#unscheduleAllTasks}
     */
    @Override
    public void unscheduleAllTasks() {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CONTROL_REMOTE_ACCESS);
        String clientId = getServerlessCallerClientId();
        try {
            mRemoteAccessHalWrapper.unscheduleAllTasks(clientId);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new ServiceSpecificException(SERVICE_ERROR_CODE_GENERAL,
                    "failed to call IRemoteAccess.unscheduleAllTasks with clientId: "
                    + clientId + ", error: " + e);
        }
    }

    /**
     * {@link android.car.remoteaccess.CarRemoteAccessManager#isTaskScheduled}
     */
    @Override
    public boolean isTaskScheduled(String scheduleId) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CONTROL_REMOTE_ACCESS);
        Preconditions.checkArgument(scheduleId != null, "scheduleId must not be null");
        String clientId = getServerlessCallerClientId();
        try {
            return mRemoteAccessHalWrapper.isTaskScheduled(clientId, scheduleId);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new ServiceSpecificException(SERVICE_ERROR_CODE_GENERAL,
                    "failed to call IRemoteAccess.isTaskScheduled with clientId: "
                    + clientId + ", scheduleId: " + scheduleId+ ", error: " + e);
        }
    }

    /**
     * {@link android.car.remoteaccess.CarRemoteAccessManager#getAllPendingScheduledTasks}
     */
    @Override
    public List<TaskScheduleInfo> getAllPendingScheduledTasks() {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CONTROL_REMOTE_ACCESS);
        String clientId = getServerlessCallerClientId();
        List<TaskScheduleInfo> taskScheduleInfoList = new ArrayList<>();
        try {
            List<ScheduleInfo> halScheduleInfoList =
                    mRemoteAccessHalWrapper.getAllPendingScheduledTasks(clientId);
            for (int i = 0; i < halScheduleInfoList.size(); i++) {
                ScheduleInfo halScheduleInfo = halScheduleInfoList.get(i);
                TaskScheduleInfo taskScheduleInfo = new TaskScheduleInfo();
                taskScheduleInfo.scheduleId = halScheduleInfo.scheduleId;
                taskScheduleInfo.taskData = halScheduleInfo.taskData;
                taskScheduleInfo.count = halScheduleInfo.count;
                taskScheduleInfo.startTimeInEpochSeconds = halScheduleInfo.startTimeInEpochSeconds;
                taskScheduleInfo.periodicInSeconds = halScheduleInfo.periodicInSeconds;
                switch (halScheduleInfo.taskType) {
                    case TaskType.CUSTOM:
                        taskScheduleInfo.taskType = TASK_TYPE_CUSTOM;
                        break;
                    case TaskType.ENTER_GARAGE_MODE:
                        taskScheduleInfo.taskType = TASK_TYPE_ENTER_GARAGE_MODE;
                        break;
                    default:
                        Slogf.e(TAG, "Unknown task type returned by remote access HAL for: "
                                + taskScheduleInfo + ", default to TASK_TYPE_CUSTOM");
                        taskScheduleInfo.taskType = TASK_TYPE_CUSTOM;
                }
                taskScheduleInfoList.add(taskScheduleInfo);
            }
            return taskScheduleInfoList;
        } catch (RemoteException | ServiceSpecificException e) {
            throw new ServiceSpecificException(SERVICE_ERROR_CODE_GENERAL,
                    "failed to call IRemoteAccess.getAllPendingScheduledTasks with clientId: "
                    + clientId + ", error: " + e);
        }
    }

    @Override
    public int[] getSupportedTaskTypesForScheduling() {
        if (!isTaskScheduleSupported()) {
            Slogf.i(TAG, "task scheduling is not supported, return empty array for "
                    + "getSupportedTaskTypesForScheduling");
            return EMPTY_INT_ARRAY;
        }
        int[] supportedHalTaskTypes;
        try {
            supportedHalTaskTypes = mRemoteAccessHalWrapper.getSupportedTaskTypesForScheduling();
        } catch (RemoteException | ServiceSpecificException e) {
            throw new ServiceSpecificException(SERVICE_ERROR_CODE_GENERAL,
                    "failed to call IRemoteAccess.getSupportedTaskTypesForScheduling, error: " + e);
        }
        ArraySet<Integer> supportedMgrTaskTypes = new ArraySet<>();
        for (int i = 0; i < supportedHalTaskTypes.length; i++) {
            switch (supportedHalTaskTypes[i]) {
                case TaskType.CUSTOM:
                    supportedMgrTaskTypes.add(TASK_TYPE_CUSTOM);
                    break;
                case TaskType.ENTER_GARAGE_MODE:
                    supportedMgrTaskTypes.add(TASK_TYPE_ENTER_GARAGE_MODE);
                    break;
                default:
                    Slogf.e(TAG, "Unknown supported task type returned from remote access HAL, "
                            + "ignore: " + supportedHalTaskTypes[i]);
            }
        }
        // Convert to int array.
        int[] result = new int[supportedMgrTaskTypes.size()];
        for (int i = 0; i < supportedMgrTaskTypes.size(); i++) {
            result[i] = supportedMgrTaskTypes.valueAt(i);
        }
        return result;
    }

    @GuardedBy("mLock")
    private ClientToken getTokenForUidNameAndCheckClientIdLocked(String uidName, String clientId)
            throws IllegalArgumentException {
        ClientToken token = mClientTokenByUidName.get(uidName);
        if (token == null) {
            throw new IllegalArgumentException("Callback has not been registered");
        }
        // TODO(b/252698817): Update the validity checking logic.
        if (!clientId.equals(token.getClientId())) {
            throw new IllegalArgumentException("Client ID(" + clientId + ") doesn't match the "
                    + "registered one(" + token.getClientId() + ")");
        }
        return token;
    }

    @GuardedBy("mLock")
    private @Nullable RemoteTaskClientServiceConnection getServiceConnectionLocked(String uidName) {
        if (DEBUG) {
            Slogf.d(TAG, "getServiceConnectionLocked for uid: %s", uidName);
        }
        RemoteTaskClientServiceInfo serviceInfo = mClientServiceInfoByUid.get(uidName);
        if (serviceInfo == null) {
            if (DEBUG) {
                Slogf.d(TAG, "the service info for uid: %s is null", uidName);
            }
            return null;
        }
        return serviceInfo.getServiceConnection();
    }

    /**
     * Reports that the task of {@code taskId} is completed.
     *
     * @param clientId ID of a client that has completed the task.
     * @param taskId ID of a task that has been completed.
     * @throws IllegalArgumentException When {@code clientId} is not valid, or {@code taskId} is not
     *         valid.
     */
    @Override
    public void reportRemoteTaskDone(String clientId, String taskId) {
        Slogf.i(TAG, "reportRemoteTaskDone for client: %s, task: %s", clientId, taskId);
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_USE_REMOTE_ACCESS);
        Preconditions.checkArgument(clientId != null, "clientId cannot be null");
        Preconditions.checkArgument(taskId != null, "taskId cannot be null");
        int callingUid = mDep.getCallingUid();
        String uidName;
        RemoteTaskClientServiceConnection serviceConnection;
        synchronized (mLock) {
            uidName = getNameForUidLocked(callingUid);
            getTokenForUidNameAndCheckClientIdLocked(uidName, clientId);
            serviceConnection = getServiceConnectionLocked(uidName);
        }
        if (serviceConnection == null) {
            throw new IllegalArgumentException("No active service connection, uidName: " + uidName
                    + ", clientId: " + clientId);
        }
        if (!serviceConnection.removeActiveTasks(new ArraySet<>(Set.of(taskId)))) {
            throw new IllegalArgumentException("Task ID(" + taskId + ") is not valid");
        }
        if (DEBUG) {
            Slogf.d(TAG, "Task: %s complete, after %d ms, check whether we should shutdown",
                    taskId, mTaskUnbindDelayMs);
        }
        mHandler.postMaybeShutdown(/* delayMs= */ mTaskUnbindDelayMs);
    }

    @Override
    public void setPowerStatePostTaskExecution(int nextPowerState, boolean runGarageMode) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CONTROL_REMOTE_ACCESS);
        Slogf.i(TAG, "setPowerStatePostTaskExecution, nextPowerState: %d, runGarageMode: %B",
                nextPowerState, runGarageMode);

        synchronized (mLock) {
            mNextPowerState = nextPowerState;
            mRunGarageMode = runGarageMode;
        }
    }

    @Override
    public void confirmReadyForShutdown(String clientId) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_USE_REMOTE_ACCESS);
        Preconditions.checkArgument(clientId != null, "clientId cannot be null");
        int callingUid = mDep.getCallingUid();
        boolean isAllClientReadyForShutDown = true;
        synchronized (mLock) {
            String uidName = getNameForUidLocked(callingUid);
            Slogf.i(TAG, "confirmReadyForShutdown from client: %s, uidName: %s", clientId, uidName);
            ClientToken token = getTokenForUidNameAndCheckClientIdLocked(uidName, clientId);
            token.setIsReadyForShutdown();

            for (int i = 0; i < mClientTokenByUidName.size(); i++) {
                ClientToken clientToken = mClientTokenByUidName.valueAt(i);
                if (clientToken.getCallback() == null) {
                    continue;
                }
                if (!clientToken.isReadyForShutdown()) {
                    isAllClientReadyForShutDown = false;
                }
            }
        }

        if (isAllClientReadyForShutDown) {
            mHandler.postWrapUpRemoteAccessService(/* delayMs= */ 0);
        }
    }

    @VisibleForTesting
    RemoteAccessHalCallback getRemoteAccessHalCallback() {
        return mHalCallback;
    }

    @VisibleForTesting
    long getAllowedSystemUptimeMs() {
        return mAllowedSystemUptimeMs;
    }

    @VisibleForTesting
    ArrayMap<String, String> getServerlessClientIdsByPackageName() {
        synchronized (mLock) {
            return mServerlessClientIdsByPackageName;
        }
    }

    private ArrayMap<String, String> parseServerlessClientIdsByPackageName() {
        ArrayMap<String, String> clientIdsByPackageName = new ArrayMap<>();

        if (!mFeatureFlags.serverlessRemoteAccess()) {
            Slogf.i(TAG, "Serverless remote access flag is disabled, skip parsing "
                    + "remote_access_serverless_client_map.xml");
            return clientIdsByPackageName;
        }

        try (XmlResourceParser parser = mContext.getResources().getXml(
                R.xml.remote_access_serverless_client_map)) {
            // Get to the first start tag.
            while (parser.getEventType() != XmlPullParser.START_TAG) {
                if (parser.next() == XmlPullParser.END_DOCUMENT) {
                    break;
                }
            }

            // Check the root tag.
            parser.require(XmlPullParser.START_TAG, /* namespace= */ null,
                    XML_TAG_SERVERLESS_CLIENT_MAP);
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() != XmlPullParser.START_TAG) {
                    continue;
                }
                String name = parser.getName();
                if (name.equals(XML_TAG_SERVERLESS_CLIENT)) {
                    readServerlessClient(parser, clientIdsByPackageName);
                } else {
                    skip(parser);
                }
            }
        } catch (Exception e) {
            Slogf.e(TAG, "Failed to parse remote_access_serverless_client_map.xml, disabled all "
                    + "serverless remote task client.", e);
        }

        return clientIdsByPackageName;
    }

    private void readServerlessClient(XmlPullParser parser,
            ArrayMap<String, String> clientIdsByPackageName)
            throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, /* namespace= */ null, XML_TAG_SERVERLESS_CLIENT);
        String clientId = null;
        String packageName = null;
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals(XML_TAG_CLIENT_ID)) {
                clientId = readClientId(parser);
            } else if (name.equals(XML_TAG_PACKAGE_NAME)) {
                packageName = readPackageName(parser);
            } else {
                skip(parser);
            }
        }
        if (clientId == null) {
            throw new IllegalStateException("Missing ClientId field");
        }
        if (packageName == null) {
            throw new IllegalStateException("Missing PackageName field");
        }
        clientIdsByPackageName.put(packageName, clientId);
    }

    private String readClientId(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, /* namespace= */ null, XML_TAG_CLIENT_ID);
        String clientId = readText(parser);
        parser.require(XmlPullParser.END_TAG, /* namespace= */ null, XML_TAG_CLIENT_ID);
        return clientId;
    }

    private String readPackageName(XmlPullParser parser)
            throws XmlPullParserException, IOException  {
        parser.require(XmlPullParser.START_TAG, /* namespace= */ null, XML_TAG_PACKAGE_NAME);
        String packageName = readText(parser);
        parser.require(XmlPullParser.END_TAG, /* namespace= */ null, XML_TAG_PACKAGE_NAME);
        return packageName;
    }

    // For the tags title and summary, extracts their text values.
    private String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        return result;
    }

    // Skip uninterested tags.
    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException("Invalid xml");
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }

    private void populatePackageClientIdMapping() {
        List<ClientIdEntry> clientIdEntries = mRemoteAccessStorage.getClientIdEntries();
        ArrayMap<String, String> clientIdsByPackageName = parseServerlessClientIdsByPackageName();

        synchronized (mLock) {
            if (clientIdEntries != null) {
                for (int i = 0; i < clientIdEntries.size(); i++) {
                    ClientIdEntry entry = clientIdEntries.get(i);
                    mUidByClientId.put(entry.clientId, entry.uidName);
                    mClientTokenByUidName.put(entry.uidName,
                            new ClientToken(entry.clientId, entry.idCreationTime));
                }
            }
            mServerlessClientIdsByPackageName = clientIdsByPackageName;
            Slogf.i(TAG, "mServerlessClientIdsByPackageName: " + clientIdsByPackageName);
        }
    }

    private void saveClientIdInDb(ClientToken token, String uidName) {
        if (token.isServerless()) {
            // This token is static and no need to be stored in db.
            return;
        }
        ClientIdEntry entry = new ClientIdEntry(token.getClientId(), token.getIdCreationTime(),
                uidName);
        if (!mRemoteAccessStorage.updateClientId(entry)) {
            Slogf.e(TAG, "Failed to save %s for %s in the database", token, uidName);
        }
    }

    private void postRegistrationUpdated(ICarRemoteAccessCallback callback, ClientToken token) {
        String clientId = token.getClientId();
        mHandler.post(() -> {
            if (DEBUG) {
                Slogf.d(TAG, "Calling onClientRegistrationUpdated: serviceName=%s, "
                        + "vehicleId=%s, processorId=%s, clientId=%s, isServerless=%s",
                        mWakeupServiceName, mVehicleId, mProcessorId, clientId,
                        token.isServerless());
            }
            if (!token.isServerless()) {
                try {
                    callback.onClientRegistrationUpdated(new RemoteTaskClientRegistrationInfo(
                            mWakeupServiceName, mVehicleId, mProcessorId, clientId));
                } catch (RemoteException e) {
                    Slogf.e(TAG, e, "Calling onClientRegistrationUpdated() failed: clientId = %s",
                            clientId);
                }
            } else {
                try {
                    callback.onServerlessClientRegistered(clientId);
                } catch (RemoteException e) {
                    Slogf.e(TAG, e, "Calling onServerlessClientRegistered() failed");
                }
            }

            // After notify the client about the registration info, the callback is registered.
            token.setCallback(callback);

            // Just after a registration callback is invoked, let's call onRemoteTaskRequested
            // callback if there are pending tasks.
            maybeStartNewRemoteTask(clientId);
        });
    }

    private void shutdownIfNeeded(boolean force) {
        if (DEBUG) {
            Slogf.d(TAG, "shutdownIfNeeded, force: %B", force);
        }
        int nextPowerState;
        boolean runGarageMode;
        synchronized (mLock) {
            if (mNextPowerState == CarRemoteAccessManager.NEXT_POWER_STATE_ON) {
                Slogf.i(TAG, "Will not shutdown. The next power state is ON.");
                return;
            }
            if (mPowerHalService.isVehicleInUse()) {
                Slogf.i(TAG, "Will not shutdown. The vehicle is in use.");
                return;
            }
            int taskCount = getActiveTaskCountLocked();
            if (!force && taskCount > 0) {
                Slogf.i(TAG, "Will not shutdown. The activen task count is %d.", taskCount);
                return;
            }
            nextPowerState = mNextPowerState;
            runGarageMode = mRunGarageMode;
        }
        if (DEBUG) {
            Slogf.d(TAG, "unbindAllServices before shutdown");
        }
        unbindAllServices();
        // Send SHUTDOWN_REQUEST to VHAL.
        Slogf.i(TAG, "Requesting shutdown of AP: nextPowerState = %d, runGarageMode = %b",
                nextPowerState, runGarageMode);
        try {
            mPowerService.requestShutdownAp(nextPowerState, runGarageMode);
        } catch (Exception e) {
            Slogf.e(TAG, e, "Cannot shutdown to %s", nextPowerStateToString(nextPowerState));
        }
    }

    /**
     * Unbinds all the remote task client services.
     */
    public void unbindAllServices() {
        Slogf.i(TAG, "unbind all the remote task client services");
        ArrayMap<String, RemoteTaskClientServiceInfo> clientServiceInfoByUid;
        synchronized (mLock) {
            clientServiceInfoByUid = new ArrayMap<>(mClientServiceInfoByUid);
        }
        for (int i = 0; i < clientServiceInfoByUid.size(); i++) {
            unbindRemoteTaskClientService(clientServiceInfoByUid.valueAt(i),
                    /* force= */ true);
        }
    }

    @GuardedBy("mLock")
    private long calcTaskMaxDurationInMsLocked() {
        long taskMaxDurationInMs;
        if (mNextPowerState == CarRemoteAccessManager.NEXT_POWER_STATE_ON
                || mPowerHalService.isVehicleInUse()) {
            // If next power state is ON or vehicle is in use, the mShutdownTimeInMs does not make
            // sense because shutdown will not happen. We always allow task to execute for
            // mAllowedSystemUptimMs.
            taskMaxDurationInMs = mAllowedSystemUptimeMs;
        } else {
            taskMaxDurationInMs = mShutdownTimeInMs - SystemClock.uptimeMillis();
        }
        if (DEBUG) {
            Slogf.d(TAG, "Task max duration in ms: %d", taskMaxDurationInMs);
        }
        return taskMaxDurationInMs;
    }

    @VisibleForTesting
    int getActiveTaskCount() {
        synchronized (mLock) {
            return getActiveTaskCountLocked();
        }
    }

    @GuardedBy("mLock")
    private int getActiveTaskCountLocked() {
        int count = 0;
        for (int i = 0; i < mClientServiceInfoByUid.size(); i++) {
            RemoteTaskClientServiceInfo serviceInfo = mClientServiceInfoByUid.valueAt(i);
            if (serviceInfo.getServiceConnection() != null) {
                count += serviceInfo.getServiceConnection().getActiveTaskCount();
            }
        }
        return count;
    }

    private int getLastShutdownState() {
        return mPowerService.getLastShutdownState();
    }

    private long getAllowedSystemUptimeForRemoteTaskInMs() {
        long timeout = mContext.getResources()
                .getInteger(R.integer.config_allowedSystemUptimeForRemoteAccess);
        if (timeout < MIN_SYSTEM_UPTIME_FOR_REMOTE_ACCESS_IN_SEC) {
            timeout = MIN_SYSTEM_UPTIME_FOR_REMOTE_ACCESS_IN_SEC;
            Slogf.w(TAG, "config_allowedSystemUptimeForRemoteAccess(%d) should be no less than %d",
                    timeout, MIN_SYSTEM_UPTIME_FOR_REMOTE_ACCESS_IN_SEC);
        }
        return timeout * MILLI_TO_SECOND;
    }

    private int getNotifyApStateChangeRetrySleepInMs() {
        int notifyApStateChangeRetrySleepInMs = mContext.getResources()
                .getInteger(R.integer.config_notifyApStateChange_retry_sleep_ms);
        if (notifyApStateChangeRetrySleepInMs < 0) {
            Slogf.e(TAG, "Invalid config_notifyApStateChange_retry_sleep_ms from RRO: "
                    + notifyApStateChangeRetrySleepInMs + ", must be a positive integer");
            notifyApStateChangeRetrySleepInMs = DEFAULT_NOTIFY_AP_STATE_RETRY_SLEEP_IN_MS;
        }
        return notifyApStateChangeRetrySleepInMs;
    }

    private int getNotifyApStateChangeMaxRetry() {
        int notifyApStateChangeMaxRetry = mContext.getResources()
                .getInteger(R.integer.config_notifyApStateChange_max_retry);
        if (notifyApStateChangeMaxRetry < 0) {
            Slogf.e(TAG, "Invalid config_notifyApStateChange_max_retry from RRO: "
                    + notifyApStateChangeMaxRetry + ", must be a positive integer");
            notifyApStateChangeMaxRetry = DEFAULT_NOTIFY_AP_STATE_MAX_RETRY;
        }
        return notifyApStateChangeMaxRetry;
    }

    // Gets the UID name for the specified UID. Read from a cached map if exists. Uses package
    // manager to get UID if it does not exist in cached map.
    @GuardedBy("mLock")
    private String getNameForUidLocked(int uid) {
        String uidName = mNameByUid.get(uid);
        if (uidName != null) {
            return uidName;
        }
        uidName = mPackageManager.getNameForUid(uid);
        mUidByName.put(uidName, uid);
        mNameByUid.put(uid, uidName);
        return uidName;
    }

    // Searchs for all remote task client service packages accroding to the declared intent and
    // filter them according to their permissions.
    // This will start the found remote task client services (as system user) for init registration.
    private void searchForRemoteTaskClientPackages() {
        // TODO(b/266129982): Query for all users.
        if (DEBUG) {
            Slogf.d(TAG, "searchForRemoteTaskClientPackages");
        }
        List<ResolveInfo> services = mPackageManager.queryIntentServicesAsUser(
                new Intent(Car.CAR_REMOTEACCESS_REMOTE_TASK_CLIENT_SERVICE), /* flags= */ 0,
                UserHandle.SYSTEM);
        ArrayMap<String, RemoteTaskClientServiceInfo> serviceInfoByUidName = new ArrayMap<>();
        synchronized (mLock) {
            for (int i = 0; i < services.size(); i++) {
                ServiceInfo info = services.get(i).serviceInfo;
                String packageName = info.packageName;
                ComponentName componentName = new ComponentName(packageName, info.name);
                // We require client to has PERMISSION_CONTROL_REMOTE_ACCESS which is a system API
                // so that they can be launched as system user.
                if (mPackageManager.checkPermission(Car.PERMISSION_CONTROL_REMOTE_ACCESS,
                        packageName) != PackageManager.PERMISSION_GRANTED) {
                    Slogf.w(TAG, "Component(%s) has %s intent but doesn't have %s permission",
                            componentName.flattenToString(),
                            Car.CAR_REMOTEACCESS_REMOTE_TASK_CLIENT_SERVICE,
                            Car.PERMISSION_CONTROL_REMOTE_ACCESS);
                    continue;
                }
                // TODO(b/263798644): mClientServiceInfoByUid should be updated when packages
                // are added, removed, updated.
                RemoteTaskClientServiceInfo serviceInfo =
                        new RemoteTaskClientServiceInfo(componentName);
                String uidName = getNameForUidLocked(info.applicationInfo.uid);
                mClientServiceInfoByUid.put(uidName, serviceInfo);
                if (DEBUG) {
                    Slogf.d(TAG, "Package(%s) is found as a remote task client service",
                            packageName);
                }
                // Store the service info to be started later outside the lock.
                serviceInfoByUidName.put(uidName, serviceInfo);
            }
        }
        // Start the remote task client services outside the lock since binding might be a slow
        // operation.
        for (int i = 0; i < serviceInfoByUidName.size(); i++) {
            startRemoteTaskClientService(serviceInfoByUidName.valueAt(i),
                    serviceInfoByUidName.keyAt(i), mAllowedTimeForRemoteTaskClientInitMs);
        }
    }

    // Starts the remote task client service if not already started and extends its liftime.
    private void startRemoteTaskClientService(RemoteTaskClientServiceInfo serviceInfo,
            String uidName, long taskDurationMs) {
        ComponentName serviceName = serviceInfo.getServiceComponentName();

        // Critical section to protect modification to serviceInfo.
        RemoteTaskClientServiceConnection serviceConnection;
        synchronized (mLock) {
            // TODO(b/266129982): Start a service for the user under which the task needs to be
            // executed.
            if (serviceInfo.getServiceConnection() != null) {
                serviceConnection = serviceInfo.getServiceConnection();
            } else {
                int uid = mUidByName.get(uidName);
                serviceConnection = new RemoteTaskClientServiceConnection(mContext, mHandler,
                        mUserManager, serviceName, UserHandle.SYSTEM, uid, mTaskUnbindDelayMs);
                serviceInfo.setServiceConnection(serviceConnection);
            }
        }
        long taskTimeoutMs = SystemClock.uptimeMillis() + taskDurationMs;
        Slogf.i(TAG, "Service(%s) is bound to give a time to register as a remote task client",
                serviceName.flattenToString());

        serviceConnection.bindServiceAndExtendTaskTimeoutMs(taskTimeoutMs);
    }

    private void onServiceTimeout(int uid) {
        RemoteTaskClientServiceInfo serviceInfo;
        synchronized (mLock) {
            String uidName = getNameForUidLocked(uid);
            serviceInfo = mClientServiceInfoByUid.get(uidName);
            if (serviceInfo == null) {
                Slogf.e(TAG, "No service connection info for %s, must not happen", uidName);
                return;
            }
        }
        Slogf.w(TAG, "The service: %s timeout, clearing all pending tasks and "
                + "unbind", serviceInfo.getServiceComponentName());
        unbindRemoteTaskClientService(serviceInfo, /* force= */ false);
    }

    // Stops the remote task client service. If {@code force} is true, the service will always be
    // unbound if it is binding or already bound. If it is false, the service will be unbound if
    // the service passed its liftime.
    private void unbindRemoteTaskClientService(RemoteTaskClientServiceInfo serviceInfo,
            boolean force) {
        if (DEBUG) {
            Slogf.d(TAG, "Unbinding remote task client service, force: %B", force);
        }
        RemoteTaskClientServiceConnection connection = serviceInfo.getServiceConnection();
        if (connection == null) {
            Slogf.w(TAG, "Cannot unbind remote task client service: no service connection");
            return;
        }
        ComponentName serviceName = serviceInfo.getServiceComponentName();
        if (connection.unbindService(force)) {
            Slogf.i(TAG, "Service(%s) is unbound from CarRemoteAccessService", serviceName);
        } else {
            Slogf.w(TAG, "Failed to unbind remote task client service(%s)", serviceName);
        }
    }

    private void notifyApStateChange() {
        if (DEBUG) {
            Slogf.d(TAG, "notify ap state change");
        }
        boolean isReadyForRemoteTask;
        boolean isWakeupRequired;
        synchronized (mLock) {
            isReadyForRemoteTask = mIsReadyForRemoteTask;
            isWakeupRequired = mIsWakeupRequired;
            mNotifyApStateChangeRetryCount++;
            if (mNotifyApStateChangeRetryCount > mNotifyApStateChangeMaxRetry) {
                Slogf.e(TAG, "Reached max retry count for trying to notify AP state change, "
                        + "Failed to notify AP state Change!!!");
                return;
            }
        }
        if (!mRemoteAccessHalWrapper.notifyApStateChange(isReadyForRemoteTask, isWakeupRequired)) {
            Slogf.e(TAG, "Cannot notify AP state change, waiting for "
                    + mNotifyApStateChangeRetrySleepInMs + "ms and retry");
            mHandler.postNotifyApStateChange(mNotifyApStateChangeRetrySleepInMs);
            return;
        }
        synchronized (mLock) {
            mNotifyApStateChangeRetryCount = 0;
        }
        if (DEBUG) {
            Slogf.d(TAG, "Notified AP about new state, isReadyForRemoteTask: %B, "
                    + "isWakeupRequired: %B", isReadyForRemoteTask, isWakeupRequired);
        }
    }

    private void notifyShutdownStarting() {
        List<ICarRemoteAccessCallback> callbacks = new ArrayList<>();
        Slogf.i(TAG, "notifyShutdownStarting");
        synchronized (mLock) {
            if (mNextPowerState == CarRemoteAccessManager.NEXT_POWER_STATE_ON) {
                Slogf.i(TAG, "Skipping notifyShutdownStarting because the next power state is ON");
                return;
            }
            if (mPowerHalService.isVehicleInUse()) {
                Slogf.i(TAG, "Skipping notifyShutdownStarting because vehicle is currently in use");
                return;
            }
            for (int i = 0; i < mClientTokenByUidName.size(); i++) {
                ClientToken token = mClientTokenByUidName.valueAt(i);
                if (token.getCallback() == null) {
                    Slogf.w(TAG, "Notifying client(%s) of shutdownStarting is skipped: invalid "
                            + "client token", token);
                    continue;
                }
                callbacks.add(token.getCallback());
            }
        }
        for (int i = 0; i < callbacks.size(); i++) {
            ICarRemoteAccessCallback callback = callbacks.get(i);
            try {
                callback.onShutdownStarting();
            } catch (RemoteException e) {
                Slogf.e(TAG, "Calling onShutdownStarting() failed: package", e);
            }
        }
    }

    private void wrapUpRemoteAccessServiceIfNeeded() {
        synchronized (mLock) {
            if (mNextPowerState != CarRemoteAccessManager.NEXT_POWER_STATE_ON
                    && !mPowerHalService.isVehicleInUse()) {
                Slogf.i(TAG, "Remote task execution time has expired: wrapping up the service");
            }
        }
        shutdownIfNeeded(/* force= */ true);
    }

    @Nullable
    RemoteTaskClientServiceInfo getRemoteTaskClientServiceInfo(String clientId) {
        synchronized (mLock) {
            String uidName = mUidByClientId.get(clientId);
            if (uidName == null) {
                Slogf.w(TAG, "Cannot get package name for client ID(%s)", clientId);
                return null;
            }
            return mClientServiceInfoByUid.get(uidName);
        }
    }

    private void invokeTaskRequestCallbacks(RemoteTaskClientServiceConnection serviceConnection,
            ICarRemoteAccessCallback callback, String clientId, List<RemoteTask> tasks,
            int taskMaxDurationInSec) {
        ArraySet<String> taskIds = new ArraySet<>();
        for (int i = 0; i < tasks.size(); i++) {
            taskIds.add(tasks.get(i).id);
        }
        serviceConnection.addActiveTasks(taskIds);

        ArraySet<String> failedTaskIds = new ArraySet<>();
        for (int i = 0; i < tasks.size(); i++) {
            RemoteTask task = tasks.get(i);
            try {
                Slogf.i(TAG, "Delivering remote task, clientId: %s, taskId: %s, "
                        + "max duration: %d sec", clientId, task.id, taskMaxDurationInSec);
                callback.onRemoteTaskRequested(clientId, task.id, task.data, taskMaxDurationInSec);
            } catch (RemoteException e) {
                Slogf.e(TAG, e, "Calling onRemoteTaskRequested() failed: clientId = %s, "
                        + "taskId = %s, data size = %d, taskMaxDurationInSec = %d", clientId,
                        task.id, task.data != null ? task.data.length : 0, taskMaxDurationInSec);
                failedTaskIds.add(task.id);
            }
        }
        if (!failedTaskIds.isEmpty()) {
            serviceConnection.removeActiveTasks(failedTaskIds);
        }
    }

    @GuardedBy("mLock")
    private void pushTaskToPendingQueueLocked(String clientId, RemoteTask task) {
        if (DEBUG) {
            Slogf.d(TAG, "received a new remote task: %s", task);
        }

        ArrayList remoteTasks = mTasksToBeNotifiedByClientId.get(clientId);
        if (remoteTasks == null) {
            remoteTasks = new ArrayList<RemoteTask>();
            mTasksToBeNotifiedByClientId.put(clientId, remoteTasks);
        }
        remoteTasks.add(task);
        mHandler.postPendingTaskTimeout(task, task.timeoutInMs);
    }

    private void onPendingTaskTimeout(RemoteTask task) {
        long now = SystemClock.uptimeMillis();
        Slogf.w(TAG, "Pending task: %s timeout at %d", task, now);
        synchronized (mLock) {
            List<RemoteTask> pendingTasks = mTasksToBeNotifiedByClientId.get(task.clientId);
            if (pendingTasks == null) {
                // The task is already delivered. Do nothing.
                return;
            }
            pendingTasks.remove(task);
        }
    }

    @GuardedBy("mLock")
    @Nullable
    private List<RemoteTask> popTasksFromPendingQueueLocked(String clientId) {
        if (DEBUG) {
            Slogf.d(TAG, "Pop pending remote tasks from queue for client ID: %s", clientId);
        }
        List<RemoteTask> pendingTasks = mTasksToBeNotifiedByClientId.get(clientId);
        if (pendingTasks == null) {
            return null;
        }
        mTasksToBeNotifiedByClientId.remove(clientId);
        for (int i = 0; i < pendingTasks.size(); i++) {
            if (DEBUG) {
                Slogf.d(TAG, "Prepare to deliver remote task: %s", pendingTasks.get(i));
            }
            mHandler.cancelPendingTaskTimeout(pendingTasks.get(i));
        }
        return pendingTasks;
    }

    private String generateNewTaskId() {
        return TASK_PREFIX + "_" + mTaskCount.incrementAndGet() + "_"
                + CarServiceUtils.generateRandomAlphaNumericString(RANDOM_STRING_LENGTH);
    }

    private String generateNewClientId() {
        return CLIENT_PREFIX + "_" + mClientCount.incrementAndGet() + "_"
                + CarServiceUtils.generateRandomAlphaNumericString(RANDOM_STRING_LENGTH);
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private static String nextPowerStateToString(int nextPowerState) {
        switch (nextPowerState) {
            case CarRemoteAccessManager.NEXT_POWER_STATE_ON:
                return "ON";
            case CarRemoteAccessManager.NEXT_POWER_STATE_OFF:
                return "OFF";
            case CarRemoteAccessManager.NEXT_POWER_STATE_SUSPEND_TO_RAM:
                return "Suspend-to-RAM";
            case CarRemoteAccessManager.NEXT_POWER_STATE_SUSPEND_TO_DISK:
                return "Suspend-to-disk";
            default:
                return "Unknown(" + nextPowerState + ")";
        }
    }

    private static final class RemoteTaskClientServiceInfo {
        private final ComponentName mServiceComponentName;
        private final AtomicReference<RemoteTaskClientServiceConnection> mConnection =
                new AtomicReference<>(null);

        private RemoteTaskClientServiceInfo(ComponentName componentName) {
            mServiceComponentName = componentName;
        }

        public ComponentName getServiceComponentName() {
            return mServiceComponentName;
        }

        public RemoteTaskClientServiceConnection getServiceConnection() {
            return mConnection.get();
        }

        public void setServiceConnection(@Nullable RemoteTaskClientServiceConnection connection) {
            mConnection.set(connection);
        }

        @Override
        public String toString() {
            return new StringBuilder()
                    .append("RemoteTaskClientServiceInfo[")
                    .append("Component name=")
                    .append(mServiceComponentName)
                    .append(", hasConnection=")
                    .append(mConnection.get() != null)
                    .append("]")
                    .toString();
        }
    }

    private static final class RemoteTask {
        public final String id;
        public final byte[] data;
        public final String clientId;
        public final long timeoutInMs;

        private RemoteTask(String id, byte[] data, String clientId, long timeoutInMs) {
            this.id = id;
            this.data = data;
            this.clientId = clientId;
            this.timeoutInMs = timeoutInMs;
        }

        @Override
        public String toString() {
            return new StringBuilder()
                    .append("RemoteTask[")
                    .append("taskId=")
                    .append(id)
                    .append("clientId=")
                    .append(clientId)
                    .append("timeoutInMs=")
                    .append(timeoutInMs)
                    .append("]")
                    .toString();
        }
    }

    private static final class ClientToken implements IBinder.DeathRecipient {

        private final Object mTokenLock = new Object();
        private final String mClientId;
        private final long mIdCreationTimeInMs;
        private final boolean mIsServerless;

        @GuardedBy("mTokenLock")
        private ICarRemoteAccessCallback mCallback;
        @GuardedBy("mTokenLock")
        private boolean mIsReadyForShutdown;

        private ClientToken(String clientId, long idCreationTimeInMs) {
            this(clientId, idCreationTimeInMs, /* isServerless= */ false);
        }

        private ClientToken(String clientId, long idCreationTimeInMs, boolean isServerless) {
            mClientId = clientId;
            mIdCreationTimeInMs = idCreationTimeInMs;
            mIsServerless = isServerless;
        }

        public boolean isServerless() {
            return mIsServerless;
        }

        public String getClientId() {
            return mClientId;
        }

        public long getIdCreationTime() {
            return mIdCreationTimeInMs;
        }

        public ICarRemoteAccessCallback getCallback() {
            synchronized (mTokenLock) {
                return mCallback;
            }
        }

        public void setCallback(ICarRemoteAccessCallback callback) {
            synchronized (mTokenLock) {
                mCallback = callback;
            }
        }

        public void setIsReadyForShutdown() {
            synchronized (mTokenLock) {
                mIsReadyForShutdown = true;
            }
        }

        public boolean isReadyForShutdown() {
            synchronized (mTokenLock) {
                return mIsReadyForShutdown;
            }
        }

        @Override
        public void binderDied() {
            synchronized (mTokenLock) {
                Slogf.w(TAG, "Client token callback binder died");
                mCallback.asBinder().unlinkToDeath(this, /* flags= */ 0);
                mCallback = null;
            }
        }

        @Override
        public String toString() {
            synchronized (mTokenLock) {
                return new StringBuilder()
                        .append("ClientToken[")
                        .append("mClientId=").append(mClientId)
                        .append(", mIdCreationTimeInMs=").append(mIdCreationTimeInMs)
                        .append(", hasCallback=").append(mCallback != null)
                        .append(", isServerless=").append(mIsServerless)
                        .append(']')
                        .toString();
            }
        }
    }

    private static final class RemoteTaskClientServiceConnection implements ServiceConnection {

        private static final String TAG = RemoteTaskClientServiceConnection.class.getSimpleName();

        private final Object mServiceLock = new Object();
        private final Context mContext;
        private final Intent mIntent;
        private final UserHandle mUser;
        private final RemoteTaskClientServiceHandler mHandler;
        private final UserManager mUserManager;
        private final int mUid;
        private final long mTaskUnbindDelayMs;

        private final CarUserService mCarUserService;

        // The following three variables represent the state machine of this connection:
        // 1. Init state (Binding: F, Bound: F, WaitingForUserUnlock: F)
        // 2. Waiting for user unlock (Binding: F, Bound: F, WaitingForUserUnlock: T)
        // 3. Binding state (Binding: T, Bound: F, WaitingForUserUnlock: F)
        // 4. Bound state (Binding: F, Bound: T, WaitingForUserUnlock: F)
        //
        // 1->2 If user is currently locked
        // 2->3 Aftr receiving user unlock intent.
        // 1->3 If user is currently unlocked
        // 3->4 After onNullBinding callback.
        @GuardedBy("mServiceLock")
        private boolean mBound;
        @GuardedBy("mServiceLock")
        private boolean mBinding;
        @GuardedBy("mServiceLock")
        private boolean mWaitingForUserUnlock;
        @GuardedBy("mServiceLock")
        private long mTaskTimeoutMs;
        @GuardedBy("mServiceLock")
        private final Set<String> mActiveTasks = new ArraySet<>();

        private final UserLifecycleListener mUserLifecycleListener;

        private RemoteTaskClientServiceConnection(Context context,
                RemoteTaskClientServiceHandler handler, UserManager userManager,
                ComponentName serviceName, UserHandle user, int uid, long taskUnbindDelayMs) {
            mContext = context;
            mHandler = handler;
            mUserManager = userManager;
            mIntent = new Intent();
            mIntent.setComponent(serviceName);
            mUser = user;
            mUid = uid;
            mTaskUnbindDelayMs = taskUnbindDelayMs;
            mCarUserService = CarLocalServices.getService(CarUserService.class);
            mUserLifecycleListener = event -> {
                if (!isEventOfType(TAG, event, USER_LIFECYCLE_EVENT_TYPE_UNLOCKED)) {
                    return;
                }

                if (event.getUserId() == mUser.getIdentifier()) {
                    onReceiveUserUnlock();
                }
            };
        }

        @Override
        public void onNullBinding(ComponentName name) {
            synchronized (mServiceLock) {
                mBound = true;
                mBinding = false;
            }
            Slogf.i(TAG, "Service(%s) is bound", name.flattenToShortString());
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // Do nothing.
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // Do nothing.
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Slogf.w(TAG, "Service(%s) died", name.flattenToShortString());
            unbindService(/* force= */ true);
        }

        private void onReceiveUserUnlock() {
            synchronized (mServiceLock) {
                mWaitingForUserUnlock = false;
                Slogf.i(TAG, "received user unlock notification");
                if (mBinding || mBound) {
                    // bindService is called again after user is unlocked, which caused binding to
                    // happen, so we don't need to do anything here.
                    if (DEBUG) {
                        Slogf.d(TAG, "a binding is already created, ignore the user unlock intent");
                    }
                    return;
                }
                bindServiceLocked();
            }
        }

        @GuardedBy("mServiceLock")
        private void waitForUserUnlockServiceLocked() {
            UserLifecycleEventFilter userUnlockEventFilter = new UserLifecycleEventFilter.Builder()
                    .addEventType(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED).addUser(mUser).build();
            mCarUserService.addUserLifecycleListener(userUnlockEventFilter, mUserLifecycleListener);
            mWaitingForUserUnlock = true;
        }

        @GuardedBy("mServiceLock")
        private void cancelWaitForUserUnlockServiceLocked() {
            mCarUserService.removeUserLifecycleListener(mUserLifecycleListener);
            mWaitingForUserUnlock = false;
        }

        @GuardedBy("mServiceLock")
        private void bindServiceLocked() {
            Slogf.i(TAG, "Bind service %s as user %s", mIntent, mUser);
            boolean status = mContext.bindServiceAsUser(mIntent, /* conn= */ this,
                    BIND_AUTO_CREATE, mUser);
            if (!status) {
                Slogf.w(TAG, "Failed to bind service %s as user %s", mIntent, mUser);
                mContext.unbindService(/* conn= */ this);
                return;
            }
            mBinding = true;
        }

        @GuardedBy("mServiceLock")
        private void bindServiceIfUserUnlockedLocked() {
            if (DEBUG) {
                Slogf.d(TAG, "Try to bind service %s as user %s if unlocked", mIntent, mUser);
            }
            if (mBinding) {
                Slogf.w(TAG, "%s binding is already ongoing, ignore the new bind request",
                        mIntent);
                return;
            }
            if (mWaitingForUserUnlock) {
                Slogf.w(TAG,
                        "%s binding is waiting for user unlock, ignore the new bind request",
                        mIntent);
                return;
            }
            if (mBound) {
                Slogf.w(TAG, "%s is already bound", mIntent);
                return;
            }
            // Start listening for unlock event before checking so that we don't miss any
            // unlock intent.
            waitForUserUnlockServiceLocked();
            if (mUserManager.isUserUnlocked(mUser)) {
                if (DEBUG) {
                    Slogf.d(TAG, "User %s is unlocked, start binding", mUser);
                }
                cancelWaitForUserUnlockServiceLocked();
            } else {
                Slogf.w(TAG, "User %s is not unlocked, waiting for it to be unlocked", mUser);
                return;
            }
            bindServiceLocked();
        }

        public boolean unbindService(boolean force) {
            synchronized (mServiceLock) {
                return unbindServiceLocked(force);
            }
        }

        @GuardedBy("mServiceLock")
        private boolean unbindServiceLocked(boolean force) {
            long currentTimeMs = SystemClock.uptimeMillis();
            if (!force && currentTimeMs < mTaskTimeoutMs) {
                Slogf.w(TAG, "Unbind request is out-dated and is ignored");
                return false;
            }
            Slogf.i(TAG, "unbindServiceLocked");
            mActiveTasks.clear();
            mHandler.cancelServiceTimeout(mUid);
            if (mWaitingForUserUnlock) {
                cancelWaitForUserUnlockServiceLocked();
                Slogf.w(TAG, "Still waiting for user unlock and bind has not started, "
                        + "ignore unbind");
                return false;
            }
            if (!mBound && !mBinding) {
                // If we do not have an active bounding.
                Slogf.w(TAG, "No active binding, ignore unbind");
                return false;
            }
            mBinding = false;
            mBound = false;
            try {
                mContext.unbindService(/* conn= */ this);
            } catch (Exception e) {
                Slogf.e(TAG, e, "failed to unbind service");
            }
            return true;
        }

        // Bind the service if user is unlocked or waiting for user unlock. Extend the task timeout
        // to be taskTimeoutMs.
        public void bindServiceAndExtendTaskTimeoutMs(long taskTimeoutMs) {
            Slogf.i(TAG, "Try to bind service %s as user %s if unlocked, timeout: %d", mIntent,
                    mUser, taskTimeoutMs);
            synchronized (mServiceLock) {
                // Always bind the service to extend its lifetime. If the service is already bound,
                // it will do nothing.
                bindServiceIfUserUnlockedLocked();
                if (mTaskTimeoutMs >= taskTimeoutMs) {
                    if (DEBUG) {
                        Slogf.d(TAG, "The service: %s new task timeout: %d ms is <= existing"
                                + " task timeout: %d ms, ignore", mIntent, taskTimeoutMs,
                                mTaskTimeoutMs);
                    }
                    return;
                }
                mTaskTimeoutMs = taskTimeoutMs;
                mHandler.postServiceTimeout(mUid, taskTimeoutMs);
            }
        }

        public int getActiveTaskCount() {
            synchronized (mServiceLock) {
                return mActiveTasks.size();
            }
        }

        public void addActiveTasks(ArraySet<String> tasks) {
            if (DEBUG) {
                Slogf.d(TAG, "Add active tasks: %s for service %s", tasks, mIntent);
            }
            synchronized (mServiceLock) {
                for (int i = 0; i < tasks.size(); i++) {
                    mActiveTasks.add(tasks.valueAt(i));
                }
            }
        }

        // Remove the task IDs from the active tasks list for this connection. Return false if
        // one of the provided task is not in the list.
        public boolean removeActiveTasks(ArraySet<String> tasks) {
            synchronized (mServiceLock) {
                if (DEBUG) {
                    Slogf.d(TAG, "Remove active tasks: %s for service %s, current active tasks %s",
                            tasks, mIntent, mActiveTasks);
                }
                for (int i = 0; i < tasks.size(); i++) {
                    if (!mActiveTasks.contains(tasks.valueAt(i))) {
                        return false;
                    }
                    mActiveTasks.remove(tasks.valueAt(i));
                }
                if (mActiveTasks.isEmpty()) {
                    handleAllTasksCompletionLocked();
                }
            }
            return true;
        }

        public void removeAllActiveTasks() {
            synchronized (mServiceLock) {
                mActiveTasks.clear();
                handleAllTasksCompletionLocked();
            }
        }

        @GuardedBy("mServiceLock")
        private void handleAllTasksCompletionLocked() {
            // All active tasks are completed for this package, we can now unbind the service after
            // mTaskUnbindDelayMs.
            Slogf.i(TAG, "All tasks completed for service: %s", mIntent);
            long currentTimeMs = SystemClock.uptimeMillis();
            if (DEBUG) {
                Slogf.d(TAG, "Unbind remote task client service: %s after %d ms, "
                        + "current time: %d ms", mIntent, mTaskUnbindDelayMs,
                        currentTimeMs);
            }
            mTaskTimeoutMs = currentTimeMs + mTaskUnbindDelayMs;
            mHandler.postServiceTimeout(mUid, mTaskTimeoutMs);
        }
    }

    private static final class RemoteTaskClientServiceHandler extends Handler {

        private static final String TAG = RemoteTaskClientServiceHandler.class.getSimpleName();
        private static final int MSG_SERVICE_TIMEOUT = 1;
        private static final int MSG_WRAP_UP_REMOTE_ACCESS_SERVICE = 2;
        private static final int MSG_NOTIFY_SHUTDOWN_STARTING = 3;
        private static final int MSG_NOTIFY_AP_STATE_CHANGE = 4;
        private static final int MSG_MAYBE_SHUTDOWN = 5;
        private static final int MSG_PENDING_TASK_TIMEOUT = 6;

        // Lock to synchronize remove messages and add messages.
        private final Object mHandlerLock = new Object();
        private final WeakReference<CarRemoteAccessService> mService;

        private RemoteTaskClientServiceHandler(Looper looper, CarRemoteAccessService service) {
            super(looper);
            mService = new WeakReference<>(service);
        }

        // Must use uid instead of uidName here because message object is compared using "=="
        // instead fo equals, so two same string might not "==" each other.
        private void postServiceTimeout(Integer uid, long msgTimeMs) {
            synchronized (mHandlerLock) {
                HandlerHelper.removeEqualMessages(this, MSG_SERVICE_TIMEOUT, uid);
                Message msg = obtainMessage(MSG_SERVICE_TIMEOUT, uid);
                sendMessageAtTime(msg, msgTimeMs);
            }
        }

        private void postNotifyShutdownStarting(long delayMs) {
            synchronized (mHandlerLock) {
                removeMessages(MSG_NOTIFY_SHUTDOWN_STARTING);
                Message msg = obtainMessage(MSG_NOTIFY_SHUTDOWN_STARTING);
                sendMessageDelayed(msg, delayMs);
            }
        }

        private void postWrapUpRemoteAccessService(long delayMs) {
            synchronized (mHandlerLock) {
                removeMessages(MSG_WRAP_UP_REMOTE_ACCESS_SERVICE);
                Message msg = obtainMessage(MSG_WRAP_UP_REMOTE_ACCESS_SERVICE);
                sendMessageDelayed(msg, delayMs);
            }
        }

        private void postNotifyApStateChange(long delayMs) {
            synchronized (mHandlerLock) {
                removeMessages(MSG_NOTIFY_AP_STATE_CHANGE);
                Message msg = obtainMessage(MSG_NOTIFY_AP_STATE_CHANGE);
                sendMessageDelayed(msg, delayMs);
            }
        }

        private void postMaybeShutdown(long delayMs) {
            synchronized (mHandlerLock) {
                removeMessages(MSG_MAYBE_SHUTDOWN);
                Message msg = obtainMessage(MSG_MAYBE_SHUTDOWN);
                sendMessageDelayed(msg, delayMs);
            }
        }

        private void postPendingTaskTimeout(RemoteTask task, long msgTimeMs) {
            Message msg = obtainMessage(MSG_PENDING_TASK_TIMEOUT, task);
            sendMessageAtTime(msg, msgTimeMs);
        }

        private void cancelServiceTimeout(int uid) {
            HandlerHelper.removeEqualMessages(this, MSG_SERVICE_TIMEOUT, uid);
        }

        private void cancelAllServiceTimeout() {
            removeMessages(MSG_SERVICE_TIMEOUT);
        }

        private void cancelNotifyShutdownStarting() {
            removeMessages(MSG_NOTIFY_SHUTDOWN_STARTING);
        }

        private void cancelWrapUpRemoteAccessService() {
            removeMessages(MSG_WRAP_UP_REMOTE_ACCESS_SERVICE);
        }

        private void cancelNotifyApStateChange() {
            removeMessages(MSG_NOTIFY_AP_STATE_CHANGE);
        }

        private void cancelMaybeShutdown() {
            removeMessages(MSG_MAYBE_SHUTDOWN);
        }

        private void cancelPendingTaskTimeout(RemoteTask task) {
            removeMessages(MSG_PENDING_TASK_TIMEOUT, task);
        }

        private void cancelAllPendingTaskTimeout() {
            removeMessages(MSG_PENDING_TASK_TIMEOUT);
        }

        private void cancelAll() {
            cancelAllServiceTimeout();
            cancelNotifyShutdownStarting();
            cancelWrapUpRemoteAccessService();
            cancelNotifyApStateChange();
            cancelMaybeShutdown();
            cancelAllPendingTaskTimeout();
        }

        @Override
        public void handleMessage(Message msg) {
            CarRemoteAccessService service = mService.get();
            if (service == null) {
                Slogf.w(TAG, "CarRemoteAccessService is not available");
                return;
            }
            switch (msg.what) {
                case MSG_SERVICE_TIMEOUT:
                    service.onServiceTimeout((Integer) msg.obj);
                    break;
                case MSG_WRAP_UP_REMOTE_ACCESS_SERVICE:
                    service.wrapUpRemoteAccessServiceIfNeeded();
                    break;
                case MSG_NOTIFY_SHUTDOWN_STARTING:
                    service.notifyShutdownStarting();
                    break;
                case MSG_NOTIFY_AP_STATE_CHANGE:
                    service.notifyApStateChange();
                    break;
                case MSG_MAYBE_SHUTDOWN:
                    service.shutdownIfNeeded(/* force= */ false);
                    break;
                case MSG_PENDING_TASK_TIMEOUT:
                    service.onPendingTaskTimeout((RemoteTask) msg.obj);
                    break;
                default:
                    Slogf.w(TAG, "Unknown(%d) message", msg.what);
            }
        }
    }
}
