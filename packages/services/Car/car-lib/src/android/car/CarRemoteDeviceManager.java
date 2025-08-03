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

package android.car;


import android.annotation.CallbackExecutor;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.builtin.util.Slogf;
import android.car.occupantconnection.ICarRemoteDevice;
import android.car.occupantconnection.IStateCallback;
import android.content.pm.PackageInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * API for monitoring the states of occupant zones in the car, managing their power, and monitoring
 * peer clients in those occupant zones.
 * <p>
 * Unless specified explicitly, a client means an app that uses this API and runs as a
 * foreground user in an occupant zone, while a peer client means an app that has the same package
 * name as the caller app and runs as another foreground user (in another occupant zone or even
 * another Android system).
 *
 * @hide
 */
@SystemApi
public final class CarRemoteDeviceManager extends CarManagerBase {

    private static final String TAG = CarRemoteDeviceManager.class.getSimpleName();

    /**
     * Flag to indicate whether all the displays of the occupant zone are powered on. If any of
     * them is not powered on, the caller can power them on by {@link #setOccupantZonePower}.
     */
    public static final int FLAG_OCCUPANT_ZONE_POWER_ON = 1 << 0;

    /**
     * Flag to indicate whether the main display of the occupant zone is unlocked. When it is
     * locked, it can't display UI. If UI is needed to establish the connection (for example, it
     * needs to show a dialog to get user approval), the caller shouldn't request a connection to
     * the occupant zone when it's locked.
     */
    public static final int FLAG_OCCUPANT_ZONE_SCREEN_UNLOCKED = 1 << 1;

    /**
     * Flag to indicate whether the occupant zone is ready for connection.
     * If it is ready and the peer app is installed in it, the caller can call {@link
     * android.car.occupantconnection.CarOccupantConnectionManager#requestConnection} to connect to
     * the client app in the occupant zone. Note: if UI is needed, the caller should make sure the
     * main display of the occupant zone is unlocked and the client app is running in the foreground
     * before requesting a connection to it.
     */
    public static final int FLAG_OCCUPANT_ZONE_CONNECTION_READY = 1 << 2;

    /**
     * Flag to indicate whether the client app is installed in the occupant zone. If it's not
     * installed, the caller may show a Dialog to promote the user to install the app.
     */
    public static final int FLAG_CLIENT_INSTALLED = 1 << 0;

    /**
     * Flag to indicate whether the client app with the same long version code ({@link
     * PackageInfo#getLongVersionCode} is installed in the occupant zone. If it's not installed,
     * the caller may show a Dialog to promote the user to install or update the app. To get
     * detailed package info of the client app, the caller can call {@link #getEndpointPackageInfo}.
     */
    public static final int FLAG_CLIENT_SAME_LONG_VERSION = 1 << 1;

    /**
     * Flag to indicate whether the client app with the same signing info ({@link
     * PackageInfo#signingInfo} is installed in the occupant zone. If it's not installed, the caller
     * may show a Dialog to promote the user to install or update the app. To get detailed
     * package info of the client app, the caller can call {@link #getEndpointPackageInfo}.
     */
    public static final int FLAG_CLIENT_SAME_SIGNATURE = 1 << 2;

    /**
     * Flag to indicate whether the client app in the occupant zone is running. If it's not running,
     * the caller may show a Dialog to promote the user to start the app.
     */
    public static final int FLAG_CLIENT_RUNNING = 1 << 3;

    /**
     * Flag to indicate whether the client app in the occupant zone is running in the foreground
     * (vs background). If UI is needed, the caller shouldn't request a connection to the client app
     * when the client app is running in the background.
     */
    public static final int FLAG_CLIENT_IN_FOREGROUND = 1 << 4;

    /**
     * Flags for the state of the occupant zone.
     *
     * @hide
     */
    @IntDef(flag = true, prefix = {"FLAG_OCCUPANT_ZONE_"}, value = {
            FLAG_OCCUPANT_ZONE_POWER_ON,
            FLAG_OCCUPANT_ZONE_SCREEN_UNLOCKED,
            FLAG_OCCUPANT_ZONE_CONNECTION_READY,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface OccupantZoneState {
    }

    /**
     * Flags for the state of client app in the occupant zone.
     *
     * @hide
     */
    @IntDef(flag = true, prefix = {"FLAG_CLIENT_"}, value = {
            FLAG_CLIENT_INSTALLED,
            FLAG_CLIENT_SAME_LONG_VERSION,
            FLAG_CLIENT_SAME_SIGNATURE,
            FLAG_CLIENT_RUNNING,
            FLAG_CLIENT_IN_FOREGROUND
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AppState {
    }

    /**
     * A callback to allow the client to monitor other occupant zones in the car and peer clients
     * in those occupant zones.
     * <p>
     * The caller can call {@link
     * android.car.occupantconnection.CarOccupantConnectionManager#requestConnection} to connect to
     * its peer client once the state of the peer occupant zone is {@link
     * #FLAG_OCCUPANT_ZONE_CONNECTION_READY}  and the state of the peer client becomes {@link
     * android.car.CarRemoteDeviceManager#FLAG_CLIENT_INSTALLED}. If UI is needed to establish
     * the connection, the caller must wait until {@link
     * android.car.CarRemoteDeviceManager#FLAG_OCCUPANT_ZONE_SCREEN_UNLOCKED} and {@link
     * android.car.CarRemoteDeviceManager#FLAG_CLIENT_IN_FOREGROUND}) before requesting a
     * connection.
     */
    public interface StateCallback {
        /**
         * Invoked when the callback is registered, or when the {@link OccupantZoneState} of the
         * occupant zone has changed.
         *
         * @param occupantZoneStates the state of the occupant zone. Multiple flags can be set in
         *                           the state.
         */
        void onOccupantZoneStateChanged(@NonNull OccupantZoneInfo occupantZone,
                @OccupantZoneState int occupantZoneStates);

        /**
         * Invoked when the callback is registered, or when the {@link AppState} of the peer app in
         * the given occupant zone has changed.
         * <p>
         * Note: Apps sharing the same user ID through the "sharedUserId" mechanism won't get
         * notified when the running state of their peer apps has changed.
         *
         * @param appStates the state of the peer app. Multiple flags can be set in the state.
         */
        void onAppStateChanged(@NonNull OccupantZoneInfo occupantZone,
                @AppState int appStates);
    }

    private final Object mLock = new Object();
    private final ICarRemoteDevice mService;
    private final String mPackageName;

    @GuardedBy("mLock")
    private StateCallback mCallback;
    @GuardedBy("mLock")
    private Executor mCallbackExecutor;

    private final IStateCallback mBinderCallback = new IStateCallback.Stub() {
        @Override
        public void onOccupantZoneStateChanged(OccupantZoneInfo occupantZone,
                int occupantZoneStates) {
            StateCallback callback;
            Executor callbackExecutor;
            synchronized (CarRemoteDeviceManager.this.mLock) {
                callback = mCallback;
                callbackExecutor = mCallbackExecutor;
            }
            if (callback == null || callbackExecutor == null) {
                // This should never happen, but let's be cautious.
                Slogf.e(TAG, "Failed to notify occupant zone state change because "
                        + "the callback was unregistered! callback: " + callback
                        + ", callbackExecutor: " + callbackExecutor);
                return;
            }
            long token = Binder.clearCallingIdentity();
            try {
                callbackExecutor.execute(() -> callback.onOccupantZoneStateChanged(
                        occupantZone, occupantZoneStates));
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        @Override
        public void onAppStateChanged(OccupantZoneInfo occupantZone, int appStates) {
            StateCallback callback;
            Executor callbackExecutor;
            synchronized (CarRemoteDeviceManager.this.mLock) {
                callback = mCallback;
                callbackExecutor = mCallbackExecutor;
            }
            if (callback == null || callbackExecutor == null) {
                // This should never happen, but let's be cautious.
                Slogf.e(TAG, "Failed to notify app state change because the "
                        + "callback was unregistered! callback: " + callback
                        + ", callbackExecutor: " + callbackExecutor);
                return;
            }
            long token = Binder.clearCallingIdentity();
            try {
                callbackExecutor.execute(() ->
                        callback.onAppStateChanged(occupantZone, appStates));
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
    };

    /** @hide */
    public CarRemoteDeviceManager(Car car, IBinder service) {
        super(car);
        mService = ICarRemoteDevice.Stub.asInterface(service);
        mPackageName = mCar.getContext().getPackageName();
    }

    /** @hide */
    @Override
    public void onCarDisconnected() {
        synchronized (mLock) {
            mCallback = null;
            mCallbackExecutor = null;
        }
    }

    /**
     * Registers the {@code callback} to monitor the states of other occupant zones in the car and
     * the peer clients in those occupant zones.
     * <p>
     * The client app can only register one {@link StateCallback}.
     * The client app should call this method before requesting connection to its peer clients in
     * other occupant zones.
     *
     * @param executor the Executor to run the callback
     * @throws IllegalStateException if this client already registered a {@link StateCallback}
     */
    @RequiresPermission(Car.PERMISSION_MANAGE_REMOTE_DEVICE)
    public void registerStateCallback(@NonNull @CallbackExecutor Executor executor,
            @NonNull StateCallback callback) {
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");
        synchronized (mLock) {
            Preconditions.checkState(mCallback == null,
                    "A StateCallback was registered already");
            try {
                mService.registerStateCallback(mPackageName, mBinderCallback);
                mCallback = callback;
                mCallbackExecutor = executor;
            } catch (RemoteException e) {
                Slogf.e(TAG, "Failed to register StateCallback");
                handleRemoteExceptionFromCarService(e);
            }
        }
    }

    /**
     * Unregisters the existing {@link StateCallback}.
     * <p>
     * This method can be called after calling {@link #registerStateCallback}, as soon
     * as this caller no longer needs to monitor other occupant zones or becomes inactive.
     * After monitoring ends, established connections won't be affected. In other words, {@link
     * android.car.occupantconnection.Payload} can still be sent.
     *
     * @throws IllegalStateException if no {@link StateCallback} was registered by
     *                               this {@link CarRemoteDeviceManager} before
     */
    @RequiresPermission(Car.PERMISSION_MANAGE_REMOTE_DEVICE)
    public void unregisterStateCallback() {
        synchronized (mLock) {
            Preconditions.checkState(mCallback != null, "There is no StateCallback "
                    + "registered by this CarRemoteDeviceManager");
            mCallback = null;
            mCallbackExecutor = null;
            try {
                mService.unregisterStateCallback(mPackageName);
            } catch (RemoteException e) {
                Slogf.e(TAG, "Failed to unregister StateCallback");
                handleRemoteExceptionFromCarService(e);
            }
        }
    }

    /**
     * Returns the {@link PackageInfo} of the client in {@code occupantZone}, or
     * {@code null} if there is no such client or an error occurred.
     */
    @RequiresPermission(Car.PERMISSION_MANAGE_REMOTE_DEVICE)
    @Nullable
    public PackageInfo getEndpointPackageInfo(@NonNull OccupantZoneInfo occupantZone) {
        Objects.requireNonNull(occupantZone, "occupantZone cannot be null");
        try {
            return mService.getEndpointPackageInfo(occupantZone.zoneId, mPackageName);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Failed to get peer endpoint PackageInfo in " + occupantZone);
            return handleRemoteExceptionFromCarService(e, null);
        }
    }

    /**
     * If {@code powerOn} is {@code true}, powers on all the displays of the given {@code
     * occupantZone}, and powers on the associated Android system. If {@code powerOn} is
     * {@code false}, powers off all the displays of given {@code occupantZone}, but doesn't
     * power off the associated Android system.
     * <p>
     * It is not allowed to control the power of the driver occupant zone.
     *
     * @throws UnsupportedOperationException if {@code occupantZone} represents the driver occupant
     *                                       zone
     */
    @RequiresPermission(allOf = {Car.PERMISSION_CAR_POWER, Car.PERMISSION_MANAGE_REMOTE_DEVICE})
    public void setOccupantZonePower(@NonNull OccupantZoneInfo occupantZone, boolean powerOn) {
        Objects.requireNonNull(occupantZone, "occupantZone cannot be null");
        try {
            mService.setOccupantZonePower(occupantZone, powerOn);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Failed to control the power of " + occupantZone);
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Returns {@code true} if the associated Android system AND all the displays of the given
     * {@code occupantZone} are powered on. Returns {@code false} otherwise.
     */
    @RequiresPermission(Car.PERMISSION_MANAGE_REMOTE_DEVICE)
    public boolean isOccupantZonePowerOn(@NonNull OccupantZoneInfo occupantZone) {
        Objects.requireNonNull(occupantZone, "occupantZone cannot be null");
        try {
            return mService.isOccupantZonePowerOn(occupantZone);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Failed to get power state of " + occupantZone);
            return handleRemoteExceptionFromCarService(e, false);
        }
    }
}
