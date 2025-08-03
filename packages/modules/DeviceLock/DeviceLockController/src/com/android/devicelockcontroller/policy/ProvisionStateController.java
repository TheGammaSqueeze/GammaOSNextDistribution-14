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

package com.android.devicelockcontroller.policy;

import androidx.annotation.IntDef;

import com.google.common.util.concurrent.ListenableFuture;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Interface for the provision flow state machine.
 * Different user may have different provision states.
 */
public interface ProvisionStateController {

    /**
     * Returns the latest state of the provision.
     *
     * @return A {@link ListenableFuture} that will complete when pre-existing state changes, if
     * any, are done. The result is the latest provision state.
     */
    ListenableFuture<@ProvisionState Integer> getState();

    /**
     * A convenience method that calls {@link this#setNextStateForEvent} for callers that does not
     * need to know the result.
     *
     * @param event A {@link ProvisionEvent} that is used to identify the next state to move.
     */
    void postSetNextStateForEventRequest(@ProvisionEvent int event);

    /**
     * Move to next {@link ProvisionState} based on the input event {@link ProvisionEvent}.
     *
     * @param event A {@link ProvisionEvent} that is used to identify the next state to move.
     * @return A {@link ListenableFuture} that will complete when both the state change and policies
     * enforcement for next state are done. The result is the new state.
     */
    ListenableFuture<Void> setNextStateForEvent(@ProvisionEvent int event);

    /**
     * Notify that the device is ready for provisioning.
     */
    void notifyProvisioningReady();

    /** Get the instance for {@link DeviceStateController} */
    DeviceStateController getDeviceStateController();

    /** Get the instance for {@link DevicePolicyController} */
    DevicePolicyController getDevicePolicyController();

    /**
     * Called after user has unlocked to trigger provision or enforce policies.
     */
    ListenableFuture<Void> onUserUnlocked();

    /**
     * Called when a user has completed set-up wizard.
     */
    ListenableFuture<Void> onUserSetupCompleted();

    /**
     * State definitions related to provisioning flow.
     */
    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            ProvisionState.UNPROVISIONED,
            ProvisionState.PROVISION_IN_PROGRESS,
            ProvisionState.PROVISION_SUCCEEDED,
            ProvisionState.PROVISION_PAUSED,
            ProvisionState.PROVISION_FAILED,
            ProvisionState.KIOSK_PROVISIONED,
    })
    @interface ProvisionState {

        /* Not provisioned */
        int UNPROVISIONED = 0;

        /* Provisioning flow is in progress. This is where kiosk app will be installed. */
        int PROVISION_IN_PROGRESS = 1;

        /* Provisioning is paused */
        int PROVISION_PAUSED = 2;

        /* Kiosk app provisioned */
        int KIOSK_PROVISIONED = 3;

        /* Provisioning has succeeded */
        int PROVISION_SUCCEEDED = 4;

        /* Provisioning has failed */
        int PROVISION_FAILED = 5;
    }

    /**
     * Provision event definitions
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            ProvisionEvent.PROVISION_READY,
            ProvisionEvent.PROVISION_PAUSE,
            ProvisionEvent.PROVISION_SUCCESS,
            ProvisionEvent.PROVISION_FAILURE,
            ProvisionEvent.PROVISION_KIOSK,
            ProvisionEvent.PROVISION_RESUME,
            ProvisionEvent.PROVISION_RETRY,
    })
    @interface ProvisionEvent {

        /* Ready for provisioning */
        int PROVISION_READY = 0;

        /* Pause provisioning */
        int PROVISION_PAUSE = 1;

        /* Provisioning completed successfully */
        int PROVISION_SUCCESS = 2;

        /* Provisioning failed to complete */
        int PROVISION_FAILURE = 3;

        /* Provision Kiosk app */
        int PROVISION_KIOSK = 4;

        /* Resume provisioning */
        int PROVISION_RESUME = 5;

        /* Retry provision after failure */
        int PROVISION_RETRY = 6;
    }
}
