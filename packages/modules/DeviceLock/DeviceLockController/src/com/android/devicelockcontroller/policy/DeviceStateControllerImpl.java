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

package com.android.devicelockcontroller.policy;

import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.CLEARED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.LOCKED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.UNDEFINED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.UNLOCKED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_SUCCESS;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.KIOSK_PROVISIONED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_SUCCEEDED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.UNPROVISIONED;

import com.android.devicelockcontroller.storage.GlobalParametersClient;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.Executor;

/** An implementation of the {@link DeviceStateController} */
public final class DeviceStateControllerImpl implements DeviceStateController {
    private final ProvisionStateController mProvisionStateController;
    private final DevicePolicyController mPolicyController;
    private final GlobalParametersClient mGlobalParametersClient;
    private final Executor mExecutor;
    // Used to exercising APIs under CTS without actually applying any policies.
    // This is not persistent across controller restarts, but should be good enough for the
    // intended purpose.
    private volatile @DeviceState int mPseudoDeviceState;

    public DeviceStateControllerImpl(DevicePolicyController policyController,
            ProvisionStateController provisionStateController, Executor executor) {
        mPolicyController = policyController;
        mProvisionStateController = provisionStateController;
        mGlobalParametersClient = GlobalParametersClient.getInstance();
        mExecutor = executor;
        mPseudoDeviceState = UNDEFINED;
    }

    @Override
    public ListenableFuture<Void> lockDevice() {
        return setDeviceState(LOCKED);
    }

    @Override
    public ListenableFuture<Void> unlockDevice() {
        return setDeviceState(UNLOCKED);
    }

    @Override
    public ListenableFuture<Void> clearDevice() {
        return setDeviceState(CLEARED);
    }

    /**
     * Set the global device state to be the input {@link DeviceState}. The returned
     * {@link ListenableFuture} will complete when both the state change and policies enforcement
     * for new state are done.
     */
    private ListenableFuture<Void> setDeviceState(@DeviceState int deviceState) {
        if (deviceState == UNDEFINED) {
            throw new IllegalArgumentException("Cannot set device state to UNDEFINED");
        }
        return Futures.transformAsync(mProvisionStateController.getState(),
                provisionState -> {
                    final ListenableFuture<Void> maybeSetProvisioningSuccess;
                    if (provisionState == KIOSK_PROVISIONED) {
                        maybeSetProvisioningSuccess =
                                mProvisionStateController.setNextStateForEvent(PROVISION_SUCCESS);
                    } else if (provisionState == PROVISION_SUCCEEDED) {
                        maybeSetProvisioningSuccess = Futures.immediateVoidFuture();
                    } else if (provisionState == UNPROVISIONED && (deviceState == LOCKED
                            || deviceState == UNLOCKED)) {
                        // During normal operation, we should not get lock/unlock requests in
                        // the UNPROVISIONED state. Used for CTS compliance.
                        mPseudoDeviceState = deviceState;
                        // Do not apply any policies
                        return Futures.immediateVoidFuture();
                    } else {
                        throw new RuntimeException(
                                "User has not been provisioned! Current state " + provisionState);
                    }
                    return Futures.transformAsync(maybeSetProvisioningSuccess,
                            unused -> Futures.transformAsync(isCleared(),
                                    isCleared -> {
                                        if (isCleared) {
                                            throw new RuntimeException("Device has been cleared!");
                                        }
                                        return Futures.transformAsync(
                                                mGlobalParametersClient.setDeviceState(deviceState),
                                                state -> mPolicyController.enforceCurrentPolicies(),
                                                mExecutor);
                                    }, mExecutor),
                            mExecutor);
                }, mExecutor);
    }

    @Override
    public ListenableFuture<Boolean> isLocked() {
        return Futures.transformAsync(mProvisionStateController.getState(),
                provisionState -> {
                    if (provisionState == UNPROVISIONED) {
                        // Used for CTS compliance.
                        return Futures.immediateFuture(mPseudoDeviceState == LOCKED);
                    } else {
                        return Futures.transform(mGlobalParametersClient.getDeviceState(),
                                s -> {
                                    if (s == UNDEFINED) {
                                        throw new IllegalStateException("isLocked called before "
                                                + "setting the locked state "
                                                + "(lockDevice/unlockDevice)");
                                    }
                                    return s == LOCKED;
                                }, mExecutor);
                    }
                }, mExecutor);
    }

    @Override
    public ListenableFuture<Integer> getDeviceState() {
        return mGlobalParametersClient.getDeviceState();
    }

    @Override
    public ListenableFuture<Boolean> isCleared() {
        return Futures.transform(mGlobalParametersClient.getDeviceState(),
                s -> s == CLEARED, MoreExecutors.directExecutor());
    }
}
