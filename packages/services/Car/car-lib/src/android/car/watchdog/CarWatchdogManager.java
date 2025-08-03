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

package android.car.watchdog;

import android.annotation.CallbackExecutor;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.car.Car;
import android.car.CarManagerBase;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.util.SparseIntArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * CarWatchdogManager allows applications to collect latest system resource overuse statistics, add
 * listener for resource overuse notifications, and update resource overuse configurations.
 */
public final class CarWatchdogManager extends CarManagerBase {

    private static final String TAG = CarWatchdogManager.class.getSimpleName();
    private static final boolean DEBUG = false; // STOPSHIP if true
    private static final int INVALID_SESSION_ID = -1;
    private static final int NUMBER_OF_CONDITIONS_TO_BE_MET = 2;
    private static final int MAX_UNREGISTER_CLIENT_WAIT_MILLIS = 200;

    private final Runnable mMainThreadCheck = () -> checkMainThread();

    /**
     * Timeout for services which should be responsive. The length is 3,000 milliseconds.
     *
     * @hide
     */
    @SystemApi
    public static final int TIMEOUT_CRITICAL = 0;

    /**
     * Timeout for services which are relatively responsive. The length is 5,000 milliseconds.
     *
     * @hide
     */
    @SystemApi
    public static final int TIMEOUT_MODERATE = 1;

    /**
     * Timeout for all other services. The length is 10,000 milliseconds.
     *
     * @hide
     */
    @SystemApi
    public static final int TIMEOUT_NORMAL = 2;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "TIMEOUT_", value = {
            TIMEOUT_CRITICAL,
            TIMEOUT_MODERATE,
            TIMEOUT_NORMAL,
    })
    @Target({ElementType.TYPE_USE})
    public @interface TimeoutLengthEnum {}

    private final ICarWatchdogService mService;
    private final ICarWatchdogClientImpl mClientImpl;
    private final IResourceOveruseListenerImpl mResourceOveruseListenerImpl;
    private final IResourceOveruseListenerImpl mResourceOveruseListenerForSystemImpl;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final SessionInfo mSession = new SessionInfo(INVALID_SESSION_ID, INVALID_SESSION_ID);
    @GuardedBy("mLock")
    private final List<ResourceOveruseListenerInfo> mResourceOveruseListenerInfos;
    @GuardedBy("mLock")
    private final List<ResourceOveruseListenerInfo> mResourceOveruseListenerForSystemInfos;
    @GuardedBy("mLock")
    private final ClientInfo mHealthCheckingClient = new ClientInfo();
    @GuardedBy("mLock")
    private int mRemainingConditions;

    /**
     * CarWatchdogClientCallback is implemented by the clients which want to be health-checked by
     * car watchdog server. Every time onCheckHealthStatus is called, they are expected to
     * respond by calling {@link #tellClientAlive} within timeout. If they don't
     * respond, car watchdog server reports the current state and kills them.
     *
     * <p>Before car watchdog server kills the client, it calls onPrepareProcessTermination to allow
     * them to prepare the termination. They will be killed in 1 second.
     *
     * @hide
     */
    @SystemApi
    public abstract static class CarWatchdogClientCallback {
        /**
         * Car watchdog server pings the client to check if it is alive.
         *
         * <p>The callback method is called at the Executor which is specified in {@link
         * CarWatchdogManager#registerClient}.
         *
         * @param sessionId Unique id to distinguish each health checking.
         * @param timeout Time duration within which the client should respond.
         *
         * @return whether the response is immediately acknowledged. If {@code true}, car watchdog
         *         server considers that the response is acknowledged already. If {@code false},
         *         the client should call {@link CarWatchdogManager#tellClientAlive} later to tell
         *         that it is alive.
         */
        public boolean onCheckHealthStatus(int sessionId, @TimeoutLengthEnum int timeout) {
            return false;
        }

        /**
         * Car watchdog server notifies the client that it will be terminated in 1 second.
         *
         * <p>The callback method is called at the Executor which is specified in {@link
         * CarWatchdogManager#registerClient}.
         */
        public void onPrepareProcessTermination() {}
    }

    /** @hide */
    public CarWatchdogManager(Car car, IBinder service) {
        super(car);
        mService = ICarWatchdogService.Stub.asInterface(service);
        mClientImpl = new ICarWatchdogClientImpl(this);
        mResourceOveruseListenerImpl = new IResourceOveruseListenerImpl(this, /* isSystem= */false);
        mResourceOveruseListenerForSystemImpl = new IResourceOveruseListenerImpl(this,
                /* isSystem= */true);
        mResourceOveruseListenerInfos = new ArrayList<>();
        mResourceOveruseListenerForSystemInfos = new ArrayList<>();
    }

    /**
     * Registers the car watchdog clients to {@link CarWatchdogManager}.
     *
     * <p>It is allowed to register a client from any thread, but only one client can be
     * registered. If two or more clients are needed, create a new {@link Car} and register a client
     * to it.
     *
     * @param client Watchdog client implementing {@link CarWatchdogClientCallback} interface.
     * @param timeout The time duration within which the client desires to respond. The actual
     *        timeout is decided by watchdog server.
     * @throws IllegalStateException if at least one client is already registered.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_USE_CAR_WATCHDOG)
    public void registerClient(@NonNull @CallbackExecutor Executor executor,
            @NonNull CarWatchdogClientCallback client, @TimeoutLengthEnum int timeout) {
        Objects.requireNonNull(client, "Client must be non-null");
        Objects.requireNonNull(executor, "Executor must be non-null");
        synchronized (CarWatchdogManager.this.mLock) {
            if (mHealthCheckingClient.hasClientLocked()) {
                if (mHealthCheckingClient.callback == client) {
                    return;
                }
                throw new IllegalStateException(
                        "Cannot register the client. Only one client can be registered.");
            }
            mHealthCheckingClient.setClientLocked(client, executor);
        }
        try {
            mService.registerClient(mClientImpl, timeout);
            if (DEBUG) {
                Log.d(TAG, "Car watchdog client is successfully registered");
            }
        } catch (RemoteException e) {
            synchronized (mLock) {
                mHealthCheckingClient.resetClientLocked();
            }
            handleRemoteExceptionFromCarService(e);
        } finally {
            synchronized (mLock) {
                if (mHealthCheckingClient.hasClientLocked()) {
                    mHealthCheckingClient.setRegistrationCompletedLocked();
                }
            }
        }
    }

    /**
     * Unregisters the car watchdog client from {@link CarWatchdogManager}.
     *
     * @param client Watchdog client implementing {@link CarWatchdogClientCallback} interface.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_USE_CAR_WATCHDOG)
    public void unregisterClient(@NonNull CarWatchdogClientCallback client) {
        Objects.requireNonNull(client, "Client must be non-null");
        synchronized (CarWatchdogManager.this.mLock) {
            if (!mHealthCheckingClient.hasClientLocked()
                    || mHealthCheckingClient.callback != client) {
                Log.w(TAG, "Cannot unregister the client. It has not been registered.");
                return;
            }
            if (mHealthCheckingClient.isRegistrationInProgressLocked()) {
                throwIllegalStateExceptionOnTargetSdkPostUdc(
                        "Cannot unregister the client while a registration is in progress");
                return;
            }
            mHealthCheckingClient.resetClientLocked();
        }
        try {
            mService.unregisterClient(mClientImpl);
            if (DEBUG) {
                Log.d(TAG, "Car watchdog client is successfully unregistered");
            }
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Tells {@link CarWatchdogManager} that the client is alive.
     *
     * @param client Watchdog client implementing {@link CarWatchdogClientCallback} interface.
     * @param sessionId Session id given by {@link CarWatchdogManager}.
     * @throws IllegalStateException if {@code client} is not registered.
     * @throws IllegalArgumentException if {@code sessionId} is not correct.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_USE_CAR_WATCHDOG)
    public void tellClientAlive(@NonNull CarWatchdogClientCallback client, int sessionId) {
        Objects.requireNonNull(client, "Client must be non-null");
        boolean shouldReport;
        synchronized (CarWatchdogManager.this.mLock) {
            if (!mHealthCheckingClient.hasClientLocked()
                    || mHealthCheckingClient.callback != client) {
                throw new IllegalStateException(
                        "Cannot report client status. The client has not been registered.");
            }
            Preconditions.checkArgument(sessionId != -1 && mSession.currentId == sessionId,
                    "Cannot report client status. Received session id (" + sessionId
                            + ") doesn't match the current one (" + mSession.currentId + ").");
            if (mSession.lastReportedId == sessionId) {
                Log.w(TAG, "The given session id is already reported.");
                return;
            }
            mSession.lastReportedId = sessionId;
            mRemainingConditions--;
            shouldReport = checkConditionLocked();
        }
        if (shouldReport) {
            reportToService(sessionId);
        }
    }

    /** @hide */
    @IntDef(flag = false, prefix = { "STATS_PERIOD_" }, value = {
        STATS_PERIOD_CURRENT_DAY,
        STATS_PERIOD_PAST_3_DAYS,
        STATS_PERIOD_PAST_7_DAYS,
        STATS_PERIOD_PAST_15_DAYS,
        STATS_PERIOD_PAST_30_DAYS,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface StatsPeriod {}

    /** @hide */
    @IntDef(flag = true, prefix = { "FLAG_RESOURCE_OVERUSE_" }, value = {
            FLAG_RESOURCE_OVERUSE_IO,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ResourceOveruseFlag {}

    /** @hide */
    @IntDef(flag = true, prefix = { "FLAG_MINIMUM_STATS_" }, value = {
            FLAG_MINIMUM_STATS_IO_1_MB,
            FLAG_MINIMUM_STATS_IO_100_MB,
            FLAG_MINIMUM_STATS_IO_1_GB,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface MinimumStatsFlag {}

    /** @hide */
    @IntDef(flag = true, prefix = { "RETURN_CODE_" }, value = {
            RETURN_CODE_SUCCESS,
            RETURN_CODE_ERROR,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ReturnCode {}

    /**
     * Constants that define the stats period in days.
     */
    public static final int STATS_PERIOD_CURRENT_DAY = 1;
    public static final int STATS_PERIOD_PAST_3_DAYS = 2;
    public static final int STATS_PERIOD_PAST_7_DAYS = 3;
    public static final int STATS_PERIOD_PAST_15_DAYS = 4;
    public static final int STATS_PERIOD_PAST_30_DAYS = 5;

    /**
     * Constants that define the type of resource overuse.
     */
    public static final int FLAG_RESOURCE_OVERUSE_IO = 1 << 0;

    /**
     * Constants that define the minimum stats for each resource type.
     *
     * Below constants specify the minimum amount of data written to disk.
     *
     * @hide
     */
    @SystemApi
    public static final int FLAG_MINIMUM_STATS_IO_1_MB = 1 << 0;
    /** @hide */
    @SystemApi
    public static final int FLAG_MINIMUM_STATS_IO_100_MB = 1 << 1;
    /** @hide */
    @SystemApi
    public static final int FLAG_MINIMUM_STATS_IO_1_GB = 1 << 2;

    // Return codes used to indicate the result of a request.
    /** @hide */
    @SystemApi
    public static final int RETURN_CODE_SUCCESS = 0;
    /** @hide */
    @SystemApi
    public static final int RETURN_CODE_ERROR = -1;

    /**
     * Returns resource overuse stats for the calling package. Returns {@code null}, if no stats.
     *
     * @param resourceOveruseFlag Flag to indicate the types of resource overuse stats to return.
     * @param maxStatsPeriod Maximum period to aggregate the resource overuse stats.
     *
     * @return Resource overuse stats for the calling package. If the calling package has no stats
     *         for a specified resource overuse type, null value is returned for the corresponding
     *         resource overuse stats. If the calling package doesn't have sufficient stats for
     *         {@code maxStatsPeriod} for a specified resource overuse type, the stats are returned
     *         only for the period returned in the individual resource overuse stats.
     */
    @NonNull
    public ResourceOveruseStats getResourceOveruseStats(
            @ResourceOveruseFlag int resourceOveruseFlag,
            @StatsPeriod int maxStatsPeriod) {
        try {
            return mService.getResourceOveruseStats(resourceOveruseFlag, maxStatsPeriod);
        } catch (RemoteException e) {
            ResourceOveruseStats.Builder builder =
                    new ResourceOveruseStats.Builder("", UserHandle.CURRENT);
            return handleRemoteExceptionFromCarService(e, builder.build());
        }
    }

    /**
     * Returns resource overuse stats for all monitored packages.
     *
     * @param resourceOveruseFlag Flag to indicate the types of resource overuse stats to return.
     * @param minimumStatsFlag Flag to specify the minimum stats for each resource overuse type.
     *                         Only stats above the specified minimum stats for a resource overuse
     *                         type will be returned. May provide only one minimum stats flag for
     *                         each resource overuse type. When no minimum stats flag is specified,
     *                         all stats are returned.
     * @param maxStatsPeriod Maximum period to aggregate the resource overuse stats.
     *
     * @return Resource overuse stats for all monitored packages. If any package doesn't have stats
     *         for a specified resource type, null value is returned for the corresponding resource
     *         overuse stats. If any package doesn't have sufficient stats for
     *         {@code maxStatsPeriod} for a specified resource overuse type, the stats are returned
     *         only for the period returned in the individual resource stats.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_COLLECT_CAR_WATCHDOG_METRICS)
    @NonNull
    public List<ResourceOveruseStats> getAllResourceOveruseStats(
            @ResourceOveruseFlag int resourceOveruseFlag,
            @MinimumStatsFlag int minimumStatsFlag,
            @StatsPeriod int maxStatsPeriod) {
        try {
            return mService.getAllResourceOveruseStats(resourceOveruseFlag, minimumStatsFlag,
                    maxStatsPeriod);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, new ArrayList<>());
        }
    }

    /**
     * Returns resource overuse stats for a specific user package.
     *
     * @param packageName Name of the package whose stats should be returned.
     * @param userHandle Handle of the user whose stats should be returned.
     * @param resourceOveruseFlag Flag to indicate the types of resource overuse stats to return.
     * @param maxStatsPeriod Maximum period to aggregate the resource overuse stats.
     *
     * @return Resource overuse stats for the specified user package. If the user package has no
     *         stats for a specified resource overuse type, null value is returned for the
     *         corresponding resource overuse stats. If the user package doesn't have sufficient
     *         stats for {@code maxStatsPeriod} for a specified resource overuse type, the stats are
     *         returned only for the period returned in the individual resource overuse stats.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_COLLECT_CAR_WATCHDOG_METRICS)
    @NonNull
    public ResourceOveruseStats getResourceOveruseStatsForUserPackage(
            @NonNull String packageName, @NonNull UserHandle userHandle,
            @ResourceOveruseFlag int resourceOveruseFlag,
            @StatsPeriod int maxStatsPeriod) {
        try {
            return mService.getResourceOveruseStatsForUserPackage(packageName, userHandle,
                    resourceOveruseFlag, maxStatsPeriod);
        } catch (RemoteException e) {
            ResourceOveruseStats.Builder builder =
                    new ResourceOveruseStats.Builder("", userHandle);
            return handleRemoteExceptionFromCarService(e, builder.build());
        }
    }

    /**
     * Listener to get resource overuse notifications.
     *
     * <p>Applications implement the listener method to take action and/or log on resource overuse.
     */
    public interface ResourceOveruseListener {
        /**
         * Called when a package either overuses a resource or about to overuse a resource.
         *
         * <p>The listener is called at the executor which is specified in {@link
         * CarWatchdogManager#addResourceOveruseListener} or
         * {@code addResourceOveruseListenerForSystem}.
         *
         * <p>The listener is called only on overusing one of the resources specified at the
         * {@code resourceOveruseFlag} in {@link CarWatchdogManager#addResourceOveruseListener} or
         * {@code addResourceOveruseListenerForSystem}.
         *
         * @param resourceOveruseStats Resource overuse stats containing stats only for resources
         *                             overuse types that are either overused or about to be
         *                             overused by the package. Implementations must check for null
         *                             value in each resource overuse stats before reading the
         *                             stats.
         */
        void onOveruse(@NonNull ResourceOveruseStats resourceOveruseStats);
    }

    /**
     * Adds the {@link ResourceOveruseListener} for the calling package.
     *
     * <p>Resource overuse notifications are sent only for the calling package's resource overuse.
     *
     * @param listener Listener implementing {@link ResourceOveruseListener} interface.
     * @param resourceOveruseFlag Flag to indicate the types of resource overuses to listen.
     *
     * @throws IllegalStateException if (@code listener} is already added.
     */
    public void addResourceOveruseListener(
            @NonNull @CallbackExecutor Executor executor,
            @ResourceOveruseFlag int resourceOveruseFlag,
            @NonNull ResourceOveruseListener listener) {
        Objects.requireNonNull(listener, "Listener must be non-null");
        Objects.requireNonNull(executor, "Executor must be non-null");
        Preconditions.checkArgument((resourceOveruseFlag > 0),
                "Must provide valid resource overuse flag");
        boolean shouldRemoveFromService;
        boolean shouldAddToService;
        synchronized (mLock) {
            ResourceOveruseListenerInfo listenerInfo =
                    new ResourceOveruseListenerInfo(listener, executor, resourceOveruseFlag);
            if (mResourceOveruseListenerInfos.contains(listenerInfo)) {
                throw new IllegalStateException(
                        "Cannot add the listener as it is already added");
            }
            shouldRemoveFromService = mResourceOveruseListenerImpl.hasListeners();
            shouldAddToService =  mResourceOveruseListenerImpl.maybeAppendFlag(resourceOveruseFlag);
            mResourceOveruseListenerInfos.add(listenerInfo);
        }
        if (shouldAddToService) {
            if (shouldRemoveFromService) {
                removeResourceOveruseListenerImpl();
            }
            addResourceOveruseListenerImpl();
        }
    }

    /**
     * Removes the {@link ResourceOveruseListener} for the calling package.
     *
     * @param listener Listener implementing {@link ResourceOveruseListener} interface.
     */
    public void removeResourceOveruseListener(@NonNull ResourceOveruseListener listener) {
        Objects.requireNonNull(listener, "Listener must be non-null");
        boolean shouldRemoveFromService;
        boolean shouldReAddToService;
        synchronized (mLock) {
            int index = 0;
            int resourceOveruseFlag = 0;
            for (; index != mResourceOveruseListenerInfos.size(); ++index) {
                ResourceOveruseListenerInfo listenerInfo = mResourceOveruseListenerInfos.get(index);
                if (listenerInfo.listener == listener) {
                    resourceOveruseFlag = listenerInfo.resourceOveruseFlag;
                    break;
                }
            }
            if (index == mResourceOveruseListenerInfos.size()) {
                Log.w(TAG, "Cannot remove the listener. It has not been added.");
                return;
            }
            mResourceOveruseListenerInfos.remove(index);
            shouldRemoveFromService =
                    mResourceOveruseListenerImpl.maybeRemoveFlag(resourceOveruseFlag);
            shouldReAddToService = mResourceOveruseListenerImpl.hasListeners();
        }
        if (shouldRemoveFromService) {
            removeResourceOveruseListenerImpl();
            if (shouldReAddToService) {
                addResourceOveruseListenerImpl();
            }
        }
    }

    /**
     * Adds {@link ResourceOveruseListener} to get resource overuse notifications for all packages.
     *
     * <p>Listening system services will get notified on any package overusing one of the resources
     * specified at {@code resourceOveruseFlag}.
     *
     * @param listener Listener implementing {@link ResourceOveruseListener} interface.
     * @param resourceOveruseFlag Flag to indicate the types of resource overuses to listen.
     *
     * @throws IllegalStateException if (@code listener} is already added.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_COLLECT_CAR_WATCHDOG_METRICS)
    public void addResourceOveruseListenerForSystem(
            @NonNull @CallbackExecutor Executor executor,
            @ResourceOveruseFlag int resourceOveruseFlag,
            @NonNull ResourceOveruseListener listener) {
        Objects.requireNonNull(listener, "Listener must be non-null");
        Objects.requireNonNull(executor, "Executor must be non-null");
        Preconditions.checkArgument((resourceOveruseFlag > 0),
                "Must provide valid resource overuse flag");
        boolean shouldRemoveFromService;
        boolean shouldAddToService;
        synchronized (mLock) {
            ResourceOveruseListenerInfo listenerInfo =
                    new ResourceOveruseListenerInfo(listener, executor, resourceOveruseFlag);
            if (mResourceOveruseListenerForSystemInfos.contains(listenerInfo)) {
                throw new IllegalStateException(
                        "Cannot add the listener as it is already added");
            }
            shouldRemoveFromService = mResourceOveruseListenerForSystemImpl.hasListeners();
            shouldAddToService =
                    mResourceOveruseListenerForSystemImpl.maybeAppendFlag(resourceOveruseFlag);
            mResourceOveruseListenerForSystemInfos.add(listenerInfo);
        }
        if (shouldAddToService) {
            if (shouldRemoveFromService) {
                removeResourceOveruseListenerForSystemImpl();
            }
            addResourceOveruseListenerForSystemImpl();
        }
    }

    /**
     * Removes {@link ResourceOveruseListener} from receiving system resource overuse notifications.
     *
     * @param listener Listener implementing {@link ResourceOveruseListener} interface.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_COLLECT_CAR_WATCHDOG_METRICS)
    public void removeResourceOveruseListenerForSystem(
            @NonNull ResourceOveruseListener listener) {
        Objects.requireNonNull(listener, "Listener must be non-null");
        boolean shouldRemoveFromService;
        boolean shouldReAddToService;
        synchronized (mLock) {
            int index = 0;
            int resourceOveruseFlag = 0;
            for (; index != mResourceOveruseListenerForSystemInfos.size(); ++index) {
                ResourceOveruseListenerInfo listenerInfo =
                        mResourceOveruseListenerForSystemInfos.get(index);
                if (listenerInfo.listener == listener) {
                    resourceOveruseFlag = listenerInfo.resourceOveruseFlag;
                    break;
                }
            }
            if (index == mResourceOveruseListenerForSystemInfos.size()) {
                Log.w(TAG, "Cannot remove the listener. It has not been added.");
                return;
            }
            mResourceOveruseListenerForSystemInfos.remove(index);
            shouldRemoveFromService =
                    mResourceOveruseListenerForSystemImpl.maybeRemoveFlag(resourceOveruseFlag);
            shouldReAddToService = mResourceOveruseListenerForSystemImpl.hasListeners();
        }
        if (shouldRemoveFromService) {
            removeResourceOveruseListenerForSystemImpl();
            if (shouldReAddToService) {
                addResourceOveruseListenerForSystemImpl();
            }
        }
    }

    /**
     * Sets whether or not a package is killable on resource overuse.
     *
     * <p>Updating killable setting for package, whose state cannot be changed, will result in
     * exception. This API may be used by CarSettings application or UI notification.
     *
     * @param packageName Name of the package whose setting should to be updated.
     *                    Note: All packages under shared UID share the killable state as well. Thus
     *                    setting the killable state for one package will set the killable state for
     *                    all other packages that share a UID.
     * @param userHandle  User whose setting should be updated.
     * @param isKillable  Whether or not the package for the specified user is killable on resource
     *                    overuse.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_WATCHDOG_CONFIG)
    public void setKillablePackageAsUser(@NonNull String packageName,
            @NonNull UserHandle userHandle, boolean isKillable) {
        try {
            mService.setKillablePackageAsUser(packageName, userHandle, isKillable);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Returns the list of package killable states on resource overuse for the user.
     *
     * <p>This API may be used by CarSettings application or UI notification.
     *
     * @param userHandle User whose killable states for all packages should be returned.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_WATCHDOG_CONFIG)
    @NonNull
    public List<PackageKillableState> getPackageKillableStatesAsUser(
            @NonNull UserHandle userHandle) {
        try {
            return mService.getPackageKillableStatesAsUser(userHandle);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, new ArrayList<>());
        }
    }

    /**
     * Sets the resource overuse configurations for the components provided in the configurations.
     *
     * <p>Must provide only one configuration per component. System services should set the
     * configurations only for system and third-party components. Vendor services should set the
     * configuration only for the vendor component.
     *
     * @param configurations List of resource overuse configurations. One configuration per
     *                       component.
     * @param resourceOveruseFlag Flag to indicate the types of resource overuse configurations to
     *                            set.
     *
     * @return - {@link #RETURN_CODE_SUCCESS} if the set request is successful.
     *         - {@link #RETURN_CODE_ERROR} if the set request cannot be completed and the client
     *         should retry later.
     *
     * @throws IllegalArgumentException if {@code configurations} are invalid.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_WATCHDOG_CONFIG)
    @ReturnCode
    public int setResourceOveruseConfigurations(
            @NonNull List<ResourceOveruseConfiguration> configurations,
            @ResourceOveruseFlag int resourceOveruseFlag) {
        try {
            return mService.setResourceOveruseConfigurations(configurations, resourceOveruseFlag);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
            return RETURN_CODE_ERROR;
        }
    }

    /**
     * Returns the current resource overuse configurations for all components.
     *
     * <p>This call is blocking and may take few seconds to return if the service is temporarily
     * unavailable.
     *
     * @param resourceOveruseFlag Flag to indicate the types of resource overuse configurations to
     *                            return.
     *
     * @return If the server process is alive and connected, returns list of available resource
     *         overuse configurations for all components. If the server process is dead,
     *         returns {@code null} value.
     *
     * @throws IllegalStateException if the system is in an invalid state.
     * @hide
     */
    @SystemApi
    @RequiresPermission(anyOf = {Car.PERMISSION_CONTROL_CAR_WATCHDOG_CONFIG,
            Car.PERMISSION_COLLECT_CAR_WATCHDOG_METRICS})
    @Nullable
    public List<ResourceOveruseConfiguration> getResourceOveruseConfigurations(
            @ResourceOveruseFlag int resourceOveruseFlag) {
        try {
            return mService.getResourceOveruseConfigurations(resourceOveruseFlag);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, null);
        }
    }

    /** @hide */
    @Override
    public void onCarDisconnected() {
        // nothing to do
    }

    private void throwIllegalStateExceptionOnTargetSdkPostUdc(String message) {
        if (getContext().getApplicationInfo().targetSdkVersion
                    > Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            throw new IllegalStateException(message);
        }
        Log.e(TAG, "Suppressing illegal state exception on target SDK <= UDC: " + message);
    }

    private void checkClientStatus(int sessionId, int timeout) {
        CarWatchdogClientCallback clientCallback;
        Executor executor;
        mMainHandler.removeCallbacks(mMainThreadCheck);
        synchronized (CarWatchdogManager.this.mLock) {
            if (!mHealthCheckingClient.hasClientLocked()) {
                Log.w(TAG, "Cannot check client status. The client has not been registered.");
                return;
            }
            mSession.currentId = sessionId;
            clientCallback = mHealthCheckingClient.callback;
            executor = mHealthCheckingClient.executor;
            mRemainingConditions = NUMBER_OF_CONDITIONS_TO_BE_MET;
        }
        // For a car watchdog client to be active, 1) its main thread is active and 2) the client
        // responds within timeout. When each condition is met, the remaining task counter is
        // decreased. If the remaining task counter is zero, the client is considered active.
        mMainHandler.post(mMainThreadCheck);
        // Call the client callback to check if the client is active.
        executor.execute(() -> {
            boolean checkDone = clientCallback.onCheckHealthStatus(sessionId, timeout);
            Log.e(TAG, "Called clientCallback.onCheckHealthStatus");
            if (checkDone) {
                boolean shouldReport;
                synchronized (mLock) {
                    if (mSession.lastReportedId == sessionId) {
                        return;
                    }
                    mSession.lastReportedId = sessionId;
                    mRemainingConditions--;
                    shouldReport = checkConditionLocked();
                }
                if (shouldReport) {
                    reportToService(sessionId);
                }
            }
        });
    }

    private void checkMainThread() {
        int sessionId;
        boolean shouldReport;
        synchronized (mLock) {
            mRemainingConditions--;
            sessionId = mSession.currentId;
            shouldReport = checkConditionLocked();
        }
        if (shouldReport) {
            reportToService(sessionId);
        }
    }

    @GuardedBy("mLock")
    private boolean checkConditionLocked() {
        if (mRemainingConditions < 0) {
            Log.wtf(TAG, "Remaining condition is less than zero: should not happen");
        }
        return mRemainingConditions == 0;
    }

    private void reportToService(int sessionId) {
        try {
            mService.tellClientAlive(mClientImpl, sessionId);
            if (DEBUG) {
                Log.d(TAG, "Informed CarService that client is alive");
            }
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    private void notifyProcessTermination() {
        CarWatchdogClientCallback clientCallback;
        Executor executor;
        synchronized (CarWatchdogManager.this.mLock) {
            if (!mHealthCheckingClient.hasClientLocked()) {
                Log.w(TAG, "Cannot notify the client. The client has not been registered.");
                return;
            }
            clientCallback = mHealthCheckingClient.callback;
            executor = mHealthCheckingClient.executor;
        }
        executor.execute(() -> clientCallback.onPrepareProcessTermination());
    }

    private void addResourceOveruseListenerImpl() {
        try {
            mService.addResourceOveruseListener(
                    mResourceOveruseListenerImpl.resourceOveruseFlag(),
                    mResourceOveruseListenerImpl);
            if (DEBUG) {
                Log.d(TAG, "Resource overuse listener implementation is successfully added to "
                        + "service");
            }
        } catch (RemoteException e) {
            synchronized (mLock) {
                mResourceOveruseListenerInfos.clear();
            }
            handleRemoteExceptionFromCarService(e);
        }
    }

    private void removeResourceOveruseListenerImpl() {
        try {
            mService.removeResourceOveruseListener(mResourceOveruseListenerImpl);
            if (DEBUG) {
                Log.d(TAG, "Resource overuse listener implementation is successfully removed "
                        + "from service");
            }
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    private void addResourceOveruseListenerForSystemImpl() {
        try {
            mService.addResourceOveruseListenerForSystem(
                    mResourceOveruseListenerForSystemImpl.resourceOveruseFlag(),
                    mResourceOveruseListenerForSystemImpl);
            if (DEBUG) {
                Log.d(TAG, "Resource overuse listener for system implementation is successfully "
                        + "added to service");
            }
        } catch (RemoteException e) {
            synchronized (mLock) {
                mResourceOveruseListenerForSystemInfos.clear();
            }
            handleRemoteExceptionFromCarService(e);
        }
    }

    private void removeResourceOveruseListenerForSystemImpl() {
        try {
            mService.removeResourceOveruseListenerForSystem(mResourceOveruseListenerForSystemImpl);
            if (DEBUG) {
                Log.d(TAG, "Resource overuse listener for system implementation is successfully "
                        + "removed from service");
            }
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    private void onResourceOveruse(ResourceOveruseStats resourceOveruseStats, boolean isSystem) {
        if (resourceOveruseStats.getIoOveruseStats() == null) {
            Log.w(TAG, "Skipping resource overuse notification as the stats are missing");
            return;
        }
        List<ResourceOveruseListenerInfo> listenerInfos;
        synchronized (mLock) {
            if (isSystem) {
                listenerInfos = mResourceOveruseListenerForSystemInfos;
            } else {
                listenerInfos = mResourceOveruseListenerInfos;
            }
        }
        if (listenerInfos.isEmpty()) {
            Log.w(TAG, "Cannot notify resource overuse listener " + (isSystem ? "for system " : "")
                    + "as it is not registered.");
            return;
        }
        for (ResourceOveruseListenerInfo listenerInfo : listenerInfos) {
            if ((listenerInfo.resourceOveruseFlag & FLAG_RESOURCE_OVERUSE_IO) == 1) {
                listenerInfo.executor.execute(() -> {
                    listenerInfo.listener.onOveruse(resourceOveruseStats);
                });
            }
        }
    }

    /** @hide */
    private final class ClientInfo {
        public CarWatchdogClientCallback callback;
        public Executor executor;
        private boolean mIsRegistrationInProgress;

        @GuardedBy("CarWatchdogManager.this.mLock")
        boolean hasClientLocked() {
            return callback != null && executor != null;
        }

        @GuardedBy("CarWatchdogManager.this.mLock")
        void setClientLocked(CarWatchdogClientCallback callback, Executor executor) {
            this.callback = callback;
            this.executor = executor;
            mIsRegistrationInProgress = true;
            if (DEBUG) {
                Log.d(TAG, "Set CarWatchdog client callback to " + callback);
            }
        }

        @GuardedBy("CarWatchdogManager.this.mLock")
        void resetClientLocked() {
            callback = null;
            executor = null;
            mIsRegistrationInProgress = false;
            if (DEBUG) {
                Log.d(TAG, "Reset CarWatchdog client callback");
            }
        }

        @GuardedBy("CarWatchdogManager.this.mLock")
        void setRegistrationCompletedLocked() {
            mIsRegistrationInProgress = false;
            mLock.notify();
            if (DEBUG) {
                Log.d(TAG, "Marked registration completed");
            }
        }

        @GuardedBy("CarWatchdogManager.this.mLock")
        boolean isRegistrationInProgressLocked() {
            long nowMillis = System.currentTimeMillis();
            long endMillis = nowMillis + MAX_UNREGISTER_CLIENT_WAIT_MILLIS;
            while (mIsRegistrationInProgress && nowMillis < endMillis) {
                try {
                    mLock.wait(endMillis - nowMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.w(TAG, "Interrupted while waiting for registration to complete. "
                            + "Continuing to wait");
                } finally {
                    nowMillis = System.currentTimeMillis();
                }
            }
            return mIsRegistrationInProgress;
        }
    }

    /** @hide */
    private static final class ICarWatchdogClientImpl extends ICarWatchdogServiceCallback.Stub {
        private final WeakReference<CarWatchdogManager> mManager;

        ICarWatchdogClientImpl(CarWatchdogManager manager) {
            mManager = new WeakReference<>(manager);
        }

        @Override
        public void onCheckHealthStatus(int sessionId, int timeout) {
            CarWatchdogManager manager = mManager.get();
            if (manager != null) {
                manager.checkClientStatus(sessionId, timeout);
            }
        }

        @Override
        public void onPrepareProcessTermination() {
            CarWatchdogManager manager = mManager.get();
            if (manager != null) {
                manager.notifyProcessTermination();
            }
        }
    }

    private static final class SessionInfo {
        public int currentId;
        public int lastReportedId;

        SessionInfo(int currentId, int lastReportedId) {
            this.currentId = currentId;
            this.lastReportedId = lastReportedId;
        }
    }

    /** @hide */
    private static final class IResourceOveruseListenerImpl extends IResourceOveruseListener.Stub {
        private static final int[] RESOURCE_OVERUSE_FLAGS = new int[]{ FLAG_RESOURCE_OVERUSE_IO };

        private final WeakReference<CarWatchdogManager> mManager;
        private final boolean mIsSystem;

        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private final SparseIntArray mNumListenersByResource;


        IResourceOveruseListenerImpl(CarWatchdogManager manager, boolean isSystem) {
            mManager = new WeakReference<>(manager);
            mIsSystem = isSystem;
            mNumListenersByResource = new SparseIntArray();
        }

        @Override
        public void onOveruse(ResourceOveruseStats resourceOveruserStats) {
            CarWatchdogManager manager = mManager.get();
            if (manager != null) {
                manager.onResourceOveruse(resourceOveruserStats, mIsSystem);
            }
        }

        public boolean hasListeners() {
            synchronized (mLock) {
                return mNumListenersByResource.size() != 0;
            }
        }

        public boolean maybeAppendFlag(int appendFlag) {
            boolean isChanged = false;
            synchronized (mLock) {
                for (int flag : RESOURCE_OVERUSE_FLAGS) {
                    if ((appendFlag & flag) != 1) {
                        continue;
                    }
                    int value = mNumListenersByResource.get(flag, 0);
                    isChanged = ++value == 1;
                    mNumListenersByResource.put(flag, value);
                }
            }
            return isChanged;
        }

        public boolean maybeRemoveFlag(int removeFlag) {
            boolean isChanged = false;
            synchronized (mLock) {
                for (int flag : RESOURCE_OVERUSE_FLAGS) {
                    if ((removeFlag & flag) != 1) {
                        continue;
                    }
                    int value = mNumListenersByResource.get(flag, 0);
                    if (value == 0) {
                        continue;
                    }
                    if (--value == 0) {
                        isChanged = true;
                        mNumListenersByResource.delete(flag);
                    } else {
                        mNumListenersByResource.put(flag, value);
                    }
                }
            }
            return isChanged;
        }

        public int resourceOveruseFlag() {
            synchronized (mLock) {
                int flag = 0;
                for (int i = 0; i < mNumListenersByResource.size(); ++i) {
                    flag |= mNumListenersByResource.valueAt(i) > 0 ? mNumListenersByResource.keyAt(
                            i) : 0;
                }
                return flag;
            }
        }
    }

    /** @hide */
    private static final class ResourceOveruseListenerInfo {
        public final ResourceOveruseListener listener;
        public final Executor executor;
        public final int resourceOveruseFlag;

        ResourceOveruseListenerInfo(ResourceOveruseListener listener,
                Executor executor, int resourceOveruseFlag) {
            this.listener = listener;
            this.executor = executor;
            this.resourceOveruseFlag = resourceOveruseFlag;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof ResourceOveruseListenerInfo)) {
                return false;
            }
            ResourceOveruseListenerInfo listenerInfo = (ResourceOveruseListenerInfo) obj;
            // The ResourceOveruseListenerInfo equality is solely based on the listener because
            // the clients shouldn't register the same listener multiple times. When checking
            // whether a listener is previously registered, this equality check is used.
            return listenerInfo.listener == listener;
        }

        @Override
        public int hashCode() {
            // Similar to equality check, the hash generator uses only the listener.
            return Objects.hash(listener);
        }
    }
}
