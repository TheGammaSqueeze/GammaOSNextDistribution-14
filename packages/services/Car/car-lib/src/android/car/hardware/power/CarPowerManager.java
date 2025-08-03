/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.car.hardware.power;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;


import android.annotation.CallbackExecutor;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.car.Car;
import android.car.CarManagerBase;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;
import android.util.SparseIntArray;

import com.android.internal.annotations.GuardedBy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * API to receive power policy change notifications.
 */
public class CarPowerManager extends CarManagerBase {

    /** @hide */
    public static final String TAG = CarPowerManager.class.getSimpleName();

    private final Object mLock = new Object();
    private final ICarPower mService;
    @GuardedBy("mLock")
    private final ArrayMap<CarPowerPolicyListener, Pair<Executor, CarPowerPolicyFilter>>
            mPolicyListenerMap = new ArrayMap<>();
    // key: power component, value: number of listeners to have interest in the component
    @GuardedBy("mLock")
    private final SparseIntArray mInterestedComponentMap = new SparseIntArray();
    private final ICarPowerPolicyListener mPolicyChangeBinderCallback =
            new ICarPowerPolicyListener.Stub() {
        @Override
        public void onPolicyChanged(CarPowerPolicy appliedPolicy,
                CarPowerPolicy accumulatedPolicy) {
            long identityToken = Binder.clearCallingIdentity();
            notifyPowerPolicyListeners(appliedPolicy, accumulatedPolicy);
            Binder.restoreCallingIdentity(identityToken);
        }
    };

    @GuardedBy("mLock")
    private CarPowerStateListener mListener;
    @GuardedBy("mLock")
    private CarPowerStateListenerWithCompletion mListenerWithCompletion;
    @GuardedBy("mLock")
    private CompletablePowerStateChangeFutureImpl mFuture;
    @GuardedBy("mLock")
    private ICarPowerStateListener mListenerToService;
    @GuardedBy("mLock")
    private Executor mExecutor;

    // The following power state definitions must match the ones located in the native
    // CarPowerManager: packages/services/Car/car-lib/native/include/CarPowerManager.h
    /**
     * Power state to represent the current one is unavailable, unknown, or invalid.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_INVALID = 0;

    /**
     * Power state to represent Android is up, but waits for the vendor to give a signal to start
     * main functionality.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_WAIT_FOR_VHAL = 1;

    /**
     * Power state to represent the system enters deep sleep (suspend to RAM).
     *
     * <p>In case of using {@link CarPowerStateListenerWithCompletion}, the timeout for suspend
     * enter is 5 seconds by default and can be configured by setting
     * {@code config_shutdownEnterTimeout} in the car service resource.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_SUSPEND_ENTER = 2;

    /**
     * Power state to represent the system wakes up from suspend.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_SUSPEND_EXIT = 3;

    /**
     * Power state to represent the system enters shutdown state.
     *
     * <p>In case of using {@link CarPowerStateListenerWithCompletion}, the timeout for shutdown
     * enter is 5 seconds by default and can be configured by setting
     * {@code config_shutdownEnterTimeout} in the car service resource.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_SHUTDOWN_ENTER = 5;

    /**
     * Power state to represent the system is at on state.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_ON = 6;

    /**
     * Power state to represent the system is getting ready for shutdown or suspend. Application is
     * expected to cleanup and be ready to suspend.
     *
     * <p>The maximum duration of shutdown preprare is 15 minutes by default, and can be increased
     * by setting {@code maxGarageModeRunningDurationInSecs} in the car service resource.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_SHUTDOWN_PREPARE = 7;

    /**
     * Power state to represent shutdown is cancelled, returning to normal state.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_SHUTDOWN_CANCELLED = 8;

    /**
     * Power state to represent the system enters hibernation (suspend to disk) state.
     *
     * <p>In case of using {@link CarPowerStateListenerWithCompletion}, the timeout for hibernation
     * enter is 5 seconds by default and can be configured by setting
     * {@code config_shutdownEnterTimeout} in the car service resource.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_HIBERNATION_ENTER = 9;

    /**
     * Power state to represent the system wakes up from hibernation.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_HIBERNATION_EXIT = 10;

    /**
     * Power state to represent system shutdown is initiated, but output components such as display
     * is still on. UI to show a device is about to shutdown can be presented at this state.
     *
     * <p>In case of using {@link CarPowerStateListenerWithCompletion}, the timeout for pre shutdown
     * prepare is 5 seconds by default and can be configured by setting
     * {@code config_preShutdownPrepareTimeout} in the car service resource.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_PRE_SHUTDOWN_PREPARE = 11;

    /**
     * Power state to represent car power management service and VHAL finish processing to enter
     * deep sleep and the device is about to sleep.
     *
     * <p>In case of using {@link CarPowerStateListenerWithCompletion}, the timeout for post suspend
     * enter is 5 seconds by default and can be configured by setting
     * {@code config_postShutdownEnterTimeout} in the car service resource.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_POST_SUSPEND_ENTER = 12;

    /**
     * Power state to represent car power management service and VHAL finish processing to shutdown
     * and the device is about to power off.
     *
     * <p>In case of using {@link CarPowerStateListenerWithCompletion}, the timeout for post
     * shutdown enter is 5 seconds by default and can be configured by setting
     * {@code config_postShutdownEnterTimeout} in the car service resource.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_POST_SHUTDOWN_ENTER = 13;

    /**
     * Power state to represent car power management service and VHAL finish processing to enter
     * hibernation and the device is about to hibernate.
     *
     * <p>In case of using {@link CarPowerStateListenerWithCompletion}, the timeout for post
     * hibernation enter is 5 seconds by default and can be configured by setting
     * {@code config_postShutdownEnterTimeout} in the car service resource.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_POST_HIBERNATION_ENTER = 14;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "STATE_", value = {
            STATE_INVALID,
            STATE_WAIT_FOR_VHAL,
            STATE_SUSPEND_ENTER,
            STATE_SUSPEND_EXIT,
            STATE_SHUTDOWN_ENTER,
            STATE_ON,
            STATE_SHUTDOWN_PREPARE,
            STATE_SHUTDOWN_CANCELLED,
            STATE_HIBERNATION_ENTER,
            STATE_HIBERNATION_EXIT,
            STATE_PRE_SHUTDOWN_PREPARE,
            STATE_POST_SUSPEND_ENTER,
            STATE_POST_SHUTDOWN_ENTER,
            STATE_POST_HIBERNATION_ENTER,
    })
    @Target({ElementType.TYPE_USE})
    public @interface CarPowerState {}

    /**
     * An interface passed from {@link CarPowerStateListenerWithCompletion}.
     *
     * <p>The listener uses this interface to tell {@link CarPowerManager} that it completed the
     * task relevant to the power state change.
     *
     * @hide
     */
    @SystemApi
    public interface CompletablePowerStateChangeFuture {
        /**
         * Tells {@link CarPowerManager} that the listener completed the task to handle the power
         * state change.
         */
        void complete();

        /**
         * Gets the timestamp when the timeout happens.
         *
         * <p>The timestamp is system elapsed time in milliseconds.
         */
        long getExpirationTime();
    }

    /**
     * Applications set a {@link CarPowerStateListener} for power state event updates.
     *
     * @hide
     */
    @SystemApi
    public interface CarPowerStateListener {
        /**
         * Called when power state changes.
         *
         * @param state New power state of the system.
         */
        void onStateChanged(@CarPowerState int state);
    }

    /**
     * Applications set a {@link CarPowerStateListenerWithCompletion} for power state
     * event updates where a {@link CompletablePowerStateChangeFuture} is used.
     *
     * @hide
     */
    @SystemApi
    public interface CarPowerStateListenerWithCompletion {
        /**
         * Called when power state changes.
         *
         * <p>Some {@code state}s allow for completion and the listeners are supposed to tell the
         * completion of handling the power state change. Those states include:
         * <ul>
         * <li>{@link STATE_PRE_SHUTDOWN_PREPARE}</li>
         * <li>{@link STATE_SHUTDOWN_ENTER}</li>
         * <li>{@link STATE_SUSPEND_ENTER}</li>
         * <li>{@link STATE_HIBERNATION_ENTER}</li>
         * <li>{@link STATE_POST_SHUTDOWN_ENTER}</li>
         * <li>{@link STATE_POST_SUSPEND_ENTER}</li>
         * <li>{@link STATE_POST_HIBERNATION_ENTER}</li>
         * </ul>
         * If the listeners don't complete before the timeout expires, car power management service
         * moves to the next step, anyway. The timeout given to the listener can be queried by
         * {@link CompletablePowerStateChangeFuture#getExpirationTime()}.
         *
         * @param state New power state of the system.
         * @param future CompletablePowerStateChangeFuture used by listeners to notify
         *               CarPowerManager that they are ready to move to the next step. Car power
         *               management service waits until the listeners call
         *               {@code CompletablePowerStateChangeFuture#complete()} or timeout happens.
         *               In the case {@code state} doesn't allow for completion, {@code future} is
         *               {@code null}.
         */
        void onStateChanged(@CarPowerState int state,
                @Nullable CompletablePowerStateChangeFuture future);
    }

    /**
     * Listeners to receive power policy change.
     *
     * <p>Applications interested in power policy change register
     * {@code CarPowerPolicyListener} and will be notified when power policy changes.
     */
    public interface CarPowerPolicyListener {
        /**
         * Called with {@link CarPowerPolicy} when power policy changes.
         *
         * @param policy The current power policy.
         */
        void onPolicyChanged(@NonNull CarPowerPolicy policy);
    }

    /**
     * Gets an instance of the CarPowerManager.
     *
     * <p>Should not be obtained directly by clients, use {@link Car#getCarManager(String)} instead.
     *
     * @hide
     */
    public CarPowerManager(Car car, IBinder service) {
        super(car);
        mService = ICarPower.Stub.asInterface(service);
    }

    /**
     * Requests power manager to shutdown in lieu of suspend at the next opportunity.
     *
     * @hide
     */
    @RequiresPermission(Car.PERMISSION_CAR_POWER)
    public void requestShutdownOnNextSuspend() {
        try {
            mService.requestShutdownOnNextSuspend();
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Schedules next wake up time in CarPowerManagementService.
     *
     * @hide
     */
    @RequiresPermission(Car.PERMISSION_CAR_POWER)
    public void scheduleNextWakeupTime(int seconds) {
        try {
            mService.scheduleNextWakeupTime(seconds);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Returns the current power state.
     *
     * @return One of the values defined in {@link CarPowerStateListener}.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CAR_POWER)
    public @CarPowerState int getPowerState() {
        try {
            return mService.getPowerState();
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, STATE_INVALID);
        }
    }

    /**
     * Sets a listener to receive power state changes. Only one listener may be set at a
     * time for an instance of CarPowerManager.
     *
     * <p>The listener is assumed to completely handle the {@code onStateChanged} before returning.
     *
     * @param listener The listener which will receive the power state change.
     * @throws IllegalStateException When a listener is already set for the power state change.
     * @throws IllegalArgumentException When the given listener is null.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CAR_POWER)
    public void setListener(@NonNull @CallbackExecutor Executor executor,
            @NonNull CarPowerStateListener listener) {
        checkArgument(executor != null, "excutor cannot be null");
        checkArgument(listener != null, "listener cannot be null");
        synchronized (mLock) {
            if (mListener != null || mListenerWithCompletion != null) {
                throw new IllegalStateException("Listener must be cleared first");
            }
            // Updates listener
            mListener = listener;
            mExecutor = executor;
            setServiceForListenerLocked(/* useCompletion= */ false);
        }
    }

    /**
     * Sets a listener to receive power state changes. Only one listener may be set at a time for an
     * instance of CarPowerManager.
     *
     * <p>For calls that require completion before continue, we attach a
     * {@link CompletablePowerStateChangeFuture} which is being used as a signal that caller is
     * finished and ready to proceed.
     * Once the future is completed, car power management service knows that the application has
     * handled the power state transition and moves to the next state.
     *
     * @param listener The listener which will receive the power state change.
     * @throws IllegalStateException When a listener is already set for the power state change.
     * @throws IllegalArgumentException When the given listener is null.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_SHUTDOWN_PROCESS)
    public void setListenerWithCompletion(@NonNull @CallbackExecutor Executor executor,
            @NonNull CarPowerStateListenerWithCompletion listener) {
        checkArgument(executor != null, "executor cannot be null");
        checkArgument(listener != null, "listener cannot be null");
        synchronized (mLock) {
            if (mListener != null || mListenerWithCompletion != null) {
                throw new IllegalStateException("Listener must be cleared first");
            }
            // Updates listener
            mListenerWithCompletion = listener;
            mExecutor = executor;
            setServiceForListenerLocked(/* useCompletion= */ true);
        }
    }

    /**
     * Removes the power state listener.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CAR_POWER)
    public void clearListener() {
        ICarPowerStateListener listenerToService;
        synchronized (mLock) {
            listenerToService = mListenerToService;
            mListenerToService = null;
            mListener = null;
            mListenerWithCompletion = null;
            mExecutor = null;
            cleanupFutureLocked();
        }

        if (listenerToService == null) {
            Log.w(TAG, "clearListener: listener was not registered");
            return;
        }

        try {
            mService.unregisterListener(listenerToService);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Gets the current power policy.
     *
     * <p>The returned power policy has ID of the power policy applied most recently. If no power
     * policy has been applied, the ID is an empty string. Note that enabled components and disabled
     * components might be different from those of the latest power policy applied. This is because
     * the returned power policy contains the current state of all power components determined by
     * applying power policies in an accumulative way.
     *
     * @return The power policy containing the latest state of all power components.
     */
    @RequiresPermission(Car.PERMISSION_READ_CAR_POWER_POLICY)
    @Nullable
    public CarPowerPolicy getCurrentPowerPolicy() {
        try {
            return mService.getCurrentPowerPolicy();
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, null);
        }
    }

    /**
     * Applies the given power policy.
     *
     * <p>Power components are turned on or off as specified in the given power policy. Power
     * policies are defined at {@code /vendor/etc/automotive/power_policy.xml}.
     * If the given power policy doesn't exist, this method throws
     * {@link java.lang.IllegalArgumentException}.
     *
     * @param policyId ID of power policy.
     * @throws IllegalArgumentException if {@code policyId} is null.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_POWER_POLICY)
    public void applyPowerPolicy(@NonNull String policyId) {
        checkArgument(policyId != null, "Null policyId");
        try {
            mService.applyPowerPolicy(policyId);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Sets the current power policy group.
     *
     * <p>Power policy group defines a rule to apply a certain power policy according to the power
     * state transition. For example, a power policy named "default_for_on" is supposed to be
     * applied when the power state becomes ON. This rule is specified in the power policy group.
     * Many power policy groups can be pre-defined, and one of them is set for the current one using
     * {@code setPowerPolicyGroup}.
     *
     * @param policyGroupId ID of power policy group.
     * @throws IllegalArgumentException if {@code policyGroupId} is null.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_POWER_POLICY)
    public void setPowerPolicyGroup(@NonNull String policyGroupId) {
        checkArgument(policyGroupId != null, "Null policyGroupId");
        try {
            mService.setPowerPolicyGroup(policyGroupId);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Subscribes to power policy change.
     *
     * <p>If the same listener is added with different filters, the listener is notified based on
     * the last added filter.
     *
     * @param executor Executor where the listener method is called.
     * @param listener Listener to be notified.
     * @param filter Filter specifying power components of interest.
     * @throws IllegalArgumentException if {@code executor}, {@code listener}, or {@code filter} is
     *                                  null.
     */
    @RequiresPermission(Car.PERMISSION_READ_CAR_POWER_POLICY)
    public void addPowerPolicyListener(@NonNull @CallbackExecutor Executor executor,
            @NonNull CarPowerPolicyFilter filter, @NonNull CarPowerPolicyListener listener) {
        assertPermission(Car.PERMISSION_READ_CAR_POWER_POLICY);
        checkArgument(executor != null, "Null executor");
        checkArgument(filter != null, "Null filter");
        checkArgument(listener != null, "Null listener");
        boolean updateCallbackNeeded = false;
        CarPowerPolicyFilter newFilter = null;
        synchronized (mLock) {
            mPolicyListenerMap.remove(listener);
            int[] filterComponents = filter.getComponents().clone();
            Pair<Executor, CarPowerPolicyFilter> pair =
                    new Pair<>(executor, new CarPowerPolicyFilter(filterComponents));
            mPolicyListenerMap.put(listener, pair);
            for (int i = 0; i < filterComponents.length; i++) {
                int key = filterComponents[i];
                int currentCount = mInterestedComponentMap.get(key);
                if (currentCount == 0) {
                    updateCallbackNeeded = true;
                    mInterestedComponentMap.put(key, 1);
                } else {
                    mInterestedComponentMap.put(key, currentCount + 1);
                }
            }
            if (updateCallbackNeeded) {
                newFilter = createFilterFromInterestedComponentsLocked();
            }
        }
        if (updateCallbackNeeded) {
            updatePowerPolicyChangeCallback(newFilter);
        }
    }

    /**
     * Unsubscribes from power policy change.
     *
     * @param listener Listener that will not be notified any more.
     * @throws IllegalArgumentException if {@code listener} is null.
     */
    @RequiresPermission(Car.PERMISSION_READ_CAR_POWER_POLICY)
    public void removePowerPolicyListener(@NonNull CarPowerPolicyListener listener) {
        assertPermission(Car.PERMISSION_READ_CAR_POWER_POLICY);
        checkArgument(listener != null, "Null listener");
        boolean updateCallbackNeeded = false;
        CarPowerPolicyFilter filter = null;
        synchronized (mLock) {
            Pair<Executor, CarPowerPolicyFilter> pair = mPolicyListenerMap.remove(listener);
            if (pair == null) {
                return;
            }
            int[] filterComponents = pair.second.getComponents();
            for (int i = 0; i < filterComponents.length; i++) {
                int key = filterComponents[i];
                int currentCount = mInterestedComponentMap.get(key);
                if (currentCount == 0 || currentCount == 1) {
                    mInterestedComponentMap.delete(key);
                    updateCallbackNeeded = true;
                } else {
                    mInterestedComponentMap.put(key, currentCount - 1);
                }
            }
            if (updateCallbackNeeded) {
                filter = createFilterFromInterestedComponentsLocked();
            }
        }
        if (updateCallbackNeeded) {
            updatePowerPolicyChangeCallback(filter);
        }
    }

    /**
     * Turns on or off the individual display.
     *
     * <p>Changing the driver display is not allowed.
     *
     * @param displayId ID of the display
     * @param enable Display power state to set
     * @throws UnsupportedOperationException When trying to change the driver display power state.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_CAR_POWER)
    public void setDisplayPowerState(int displayId, boolean enable) {
        try {
            mService.setDisplayPowerState(displayId, enable);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Notifies that user activity has happened in the given display.
     *
     * @param displayId ID of the display
     * @hide
     */
    @RequiresPermission(Car.PERMISSION_CAR_POWER)
    public void notifyUserActivity(int displayId) {
        try {
            mService.notifyUserActivity(displayId);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Returns whether listen completion is allowed for {@code state}.
     *
     * @hide
     */
    @TestApi
    public static boolean isCompletionAllowed(@CarPowerState int state) {
        switch (state) {
            case CarPowerManager.STATE_PRE_SHUTDOWN_PREPARE:
            case CarPowerManager.STATE_SHUTDOWN_PREPARE:
            case CarPowerManager.STATE_SHUTDOWN_ENTER:
            case CarPowerManager.STATE_SUSPEND_ENTER:
            case CarPowerManager.STATE_HIBERNATION_ENTER:
            case CarPowerManager.STATE_POST_SHUTDOWN_ENTER:
            case CarPowerManager.STATE_POST_SUSPEND_ENTER:
            case CarPowerManager.STATE_POST_HIBERNATION_ENTER:
                return true;
            default:
                return false;
        }
    }

    @GuardedBy("mLock")
    private void setServiceForListenerLocked(boolean useCompletion) {
        if (mListenerToService == null) {
            ICarPowerStateListener listenerToService = new ICarPowerStateListener.Stub() {
                @Override
                public void onStateChanged(int state, long expirationTimeMs)
                        throws RemoteException {
                    if (useCompletion) {
                        CarPowerStateListenerWithCompletion listenerWithCompletion;
                        CompletablePowerStateChangeFuture future;
                        Executor executor;
                        synchronized (mLock) {
                            // Updates CompletablePowerStateChangeFuture. This will recreate it or
                            // just clean it up.
                            updateFutureLocked(state, expirationTimeMs);
                            listenerWithCompletion = mListenerWithCompletion;
                            future = mFuture;
                            executor = mExecutor;
                        }
                        // Notifies the user that the state has changed and supply a future.
                        if (listenerWithCompletion != null && executor != null) {
                            long identityToken = Binder.clearCallingIdentity();
                            try {
                                executor.execute(
                                        () -> listenerWithCompletion.onStateChanged(state, future));
                            } finally {
                                Binder.restoreCallingIdentity(identityToken);
                            }
                        }
                    } else {
                        CarPowerStateListener listener;
                        Executor executor;
                        synchronized (mLock) {
                            listener = mListener;
                            executor = mExecutor;
                        }
                        // Notifies the user without supplying a future.
                        if (listener != null && executor != null) {
                            long identityToken = Binder.clearCallingIdentity();
                            try {
                                executor.execute(() -> listener.onStateChanged(state));
                            } finally {
                                Binder.restoreCallingIdentity(identityToken);
                            }
                        }
                    }
                }
            };
            try {
                if (useCompletion) {
                    mService.registerListenerWithCompletion(listenerToService);
                } else {
                    mService.registerListener(listenerToService);
                }
                mListenerToService = listenerToService;
            } catch (RemoteException e) {
                handleRemoteExceptionFromCarService(e);
            }
        }
    }

    @GuardedBy("mLock")
    private void updateFutureLocked(@CarPowerState int state, long expirationTimeMs) {
        cleanupFutureLocked();
        if (isCompletionAllowed(state)) {
            // Creates a CompletablePowerStateChangeFuture and passes it to the listener.
            // When the listener completes, tells CarPowerManagementService that this action is
            // finished.
            mFuture = new CompletablePowerStateChangeFutureImpl(() -> {
                ICarPowerStateListener listenerToService;
                synchronized (mLock) {
                    listenerToService = mListenerToService;
                }
                try {
                    mService.finished(state, listenerToService);
                } catch (RemoteException e) {
                    handleRemoteExceptionFromCarService(e);
                }
            }, expirationTimeMs);
        }
    }

    @GuardedBy("mLock")
    private void cleanupFutureLocked() {
        if (mFuture != null) {
            mFuture.invalidate();
            Log.w(TAG, "The current future becomes invalid");
            mFuture = null;
        }
    }

    @GuardedBy("mLock")
    private CarPowerPolicyFilter createFilterFromInterestedComponentsLocked() {
        CarPowerPolicyFilter newFilter = null;
        int componentCount = mInterestedComponentMap.size();
        if (componentCount != 0) {
            int[] components = new int[componentCount];
            for (int i = 0; i < componentCount; i++) {
                components[i] = mInterestedComponentMap.keyAt(i);
            }
            newFilter = new CarPowerPolicyFilter(components);
        }
        return newFilter;
    }

    private void updatePowerPolicyChangeCallback(CarPowerPolicyFilter filter) {
        try {
            if (filter == null) {
                mService.removePowerPolicyListener(mPolicyChangeBinderCallback);
            } else {
                mService.addPowerPolicyListener(filter, mPolicyChangeBinderCallback);
            }
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    private void notifyPowerPolicyListeners(CarPowerPolicy appliedPolicy,
            CarPowerPolicy accumulatedPolicy) {
        ArrayList<Pair<CarPowerPolicyListener, Executor>> listeners = new ArrayList<>();
        synchronized (mLock) {
            for (int i = 0; i < mPolicyListenerMap.size(); i++) {
                CarPowerPolicyListener listener = mPolicyListenerMap.keyAt(i);
                Pair<Executor, CarPowerPolicyFilter> pair = mPolicyListenerMap.valueAt(i);
                if (PowerComponentUtil.hasComponents(appliedPolicy, pair.second)) {
                    listeners.add(
                            new Pair<CarPowerPolicyListener, Executor>(listener, pair.first));
                }
            }
        }
        for (int i = 0; i < listeners.size(); i++) {
            Pair<CarPowerPolicyListener, Executor> pair = listeners.get(i);
            pair.second.execute(() -> pair.first.onPolicyChanged(accumulatedPolicy));
        }
    }

    private void assertPermission(String permission) {
        if (getContext().checkCallingOrSelfPermission(permission) != PERMISSION_GRANTED) {
            throw new SecurityException("requires " + permission);
        }
    }

    private void checkArgument(boolean test, String message) {
        if (!test) {
            throw new IllegalArgumentException(message);
        }
    }

    /** @hide */
    @Override
    public void onCarDisconnected() {
        synchronized (mLock) {
            mListener = null;
            mListenerWithCompletion = null;
        }
    }

    private static final class CompletablePowerStateChangeFutureImpl
            implements CompletablePowerStateChangeFuture {

        private final Runnable mRunnableForCompletion;
        private final long mExpirationTimeMs;
        private final Object mCompletionLock = new Object();

        @GuardedBy("mCompletionLock")
        private boolean mCanBeCompleted = true;

        private CompletablePowerStateChangeFutureImpl(Runnable runnable, long expirationTimeMs) {
            mRunnableForCompletion = Objects.requireNonNull(runnable);
            mExpirationTimeMs = expirationTimeMs;
        }

        @Override
        public void complete() {
            synchronized (mCompletionLock) {
                if (!mCanBeCompleted) {
                    Log.w(TAG, "Cannot complete: already completed or invalid state");
                    return;
                }
                // Once completed, this instance cannot be completed again.
                mCanBeCompleted = false;
            }
            mRunnableForCompletion.run();
        }

        @Override
        public long getExpirationTime() {
            return mExpirationTimeMs;
        }

        private void invalidate() {
            synchronized (mCompletionLock) {
                mCanBeCompleted = false;
            }
        }
    }
}
