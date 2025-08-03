/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.devicelockcontroller.storage;

import static com.android.devicelockcontroller.storage.IGlobalParametersService.Stub.asInterface;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.devicelockcontroller.DeviceLockControllerApplication;
import com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState;
import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;
import com.android.devicelockcontroller.policy.FinalizationControllerImpl.FinalizationState;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.Executors;

/**
 * A class used to access Global Parameters from any user.
 */
public final class GlobalParametersClient extends DlcClient {

    private static final Object sInstanceLock = new Object();

    @SuppressLint("StaticFieldLeak") // Only holds application context.
    @GuardedBy("sInstanceLock")
    private static GlobalParametersClient sClient;

    private GlobalParametersClient(@NonNull Context context,
            ListeningExecutorService executorService) {
        super(context, new ComponentName(context, GlobalParametersService.class), executorService);
    }

    /**
     * Get the GlobalParametersClient singleton instance.
     */
    public static GlobalParametersClient getInstance() {
        return getInstance(DeviceLockControllerApplication.getAppContext(),
                /* executorService= */ null);
    }

    /**
     * Get the GlobalParametersClient singleton instance.
     */
    @VisibleForTesting
    public static GlobalParametersClient getInstance(Context appContext,
            @Nullable ListeningExecutorService executorService) {
        synchronized (sInstanceLock) {
            if (sClient == null) {
                sClient = new GlobalParametersClient(
                        appContext,
                        executorService == null
                                ? MoreExecutors.listeningDecorator(Executors.newCachedThreadPool())
                                : executorService);
            }
            return sClient;
        }
    }

    /**
     * Reset the Client singleton instance
     */
    @VisibleForTesting
    public static void reset() {
        synchronized (sInstanceLock) {
            if (sClient != null) {
                sClient.tearDown();
                sClient = null;
            }
        }
    }

    /**
     * Clear any existing global parameters.
     * Note that this API can only be called in debuggable build for debugging purpose.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Void> clear() {
        return call(() -> {
            asInterface(getService()).clear();
            return null;
        });
    }

    /**
     * Dump current values of SetupParameters to logcat.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Void> dump() {
        return call(() -> {
            asInterface(getService()).dump();
            return null;
        });
    }

    /**
     * Checks if provision is ready.
     *
     * @return true if device is ready to be provisioned.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Boolean> isProvisionReady() {
        return call(() -> asInterface(getService()).isProvisionReady());
    }

    /**
     * Sets the value of whether this device is ready for provision.
     *
     * @param isProvisionReady new state of whether the device is ready for provision.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Void> setProvisionReady(boolean isProvisionReady) {
        return call(() -> {
            asInterface(getService()).setProvisionReady(isProvisionReady);
            return null;
        });
    }

    /**
     * Gets the unique identifier that is registered to DeviceLock backend server.
     *
     * @return The registered device unique identifier; null if device has never checked in with
     * backed server.
     */
    @Nullable
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<String> getRegisteredDeviceId() {
        return call(() -> asInterface(getService()).getRegisteredDeviceId());
    }

    /**
     * Set the unique identifier that is registered to DeviceLock backend server.
     *
     * @param registeredDeviceId The registered device unique identifier.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Void> setRegisteredDeviceId(String registeredDeviceId) {
        return call(() -> {
            asInterface(getService()).setRegisteredDeviceId(registeredDeviceId);
            return null;
        });
    }

    /**
     * Check if provision should be forced.
     *
     * @return True if the provision should be forced without any delays.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Boolean> isProvisionForced() {
        return call(() -> asInterface(getService()).isProvisionForced());
    }

    /**
     * Set provision is forced
     *
     * @param isForced The new value of the forced provision flag.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Void> setProvisionForced(boolean isForced) {
        return call(() -> {
            asInterface(getService()).setProvisionForced(isForced);
            return null;
        });
    }

    /**
     * Gets the current device state.
     *
     * @return current device state
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<@DeviceState Integer> getDeviceState() {
        return call(() -> asInterface(getService()).getDeviceState());
    }

    /**
     * Sets the current device state.
     *
     * @param state New state.
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Void> setDeviceState(@DeviceState int state) {
        return call(() -> {
            asInterface(getService()).setDeviceState(state);
            return null;
        });
    }

    /**
     * Gets the current {@link FinalizationState}.
     *
     * @return current finalization state
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<@FinalizationState Integer> getFinalizationState() {
        return call(() -> asInterface(getService()).getFinalizationState());
    }

    /**
     * Sets the current {@link FinalizationState}.
     *
     * @param state new finalization state
     */
    @SuppressWarnings("GuardedBy") // mLock already held in "call" (error prone).
    public ListenableFuture<Void> setFinalizationState(@FinalizationState int state) {
        return call(() -> {
            asInterface(getService()).setFinalizationState(state);
            return null;
        });
    }

    /**
     * Get the last received provision state determined by device lock server.
     *
     * @return one of {@link DeviceProvisionState}.
     */
    public ListenableFuture<Integer> getLastReceivedProvisionState() {
        return call(() -> asInterface(getService()).getLastReceivedProvisionState());
    }

    /**
     * Set the last received provision state determined by device lock server.
     *
     * @param provisionState The provision state determined by device lock server
     */
    public ListenableFuture<Void> setLastReceivedProvisionState(
            @DeviceProvisionState int provisionState) {
        return call(() -> {
            asInterface(getService()).setLastReceivedProvisionState(provisionState);
            return null;
        });
    }
}
