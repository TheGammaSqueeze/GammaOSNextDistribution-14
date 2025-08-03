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

import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_FAILURE;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_KIOSK;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_PAUSE;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_READY;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_RESUME;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_RETRY;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_SUCCESS;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.KIOSK_PROVISIONED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_FAILED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_IN_PROGRESS;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_PAUSED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_SUCCEEDED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.UNPROVISIONED;

import com.google.common.truth.Truth;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;

import java.util.Arrays;
import java.util.List;

@RunWith(ParameterizedRobolectricTestRunner.class)
public class ProvisionStateControllerStateTransitionTest {

    @ParameterizedRobolectricTestRunner.Parameter
    @ProvisionStateController.ProvisionState
    public int mState;

    @ParameterizedRobolectricTestRunner.Parameter(1)
    @ProvisionStateController.ProvisionEvent
    public int mEvent;

    @ParameterizedRobolectricTestRunner.Parameter(2)
    @ProvisionStateController.ProvisionState
    public int mNextState;

    @ParameterizedRobolectricTestRunner.Parameters(name =
            "Transition from {0} to {2} when {1} event happens")
    public static List<Object[]> parameters() {
        return Arrays.asList(new Object[][]{
                {UNPROVISIONED, PROVISION_READY, PROVISION_IN_PROGRESS},
                {PROVISION_FAILED, PROVISION_RETRY, PROVISION_IN_PROGRESS},
                {PROVISION_IN_PROGRESS, PROVISION_KIOSK, KIOSK_PROVISIONED},
                {PROVISION_IN_PROGRESS, PROVISION_FAILURE, PROVISION_FAILED},
                {PROVISION_IN_PROGRESS, PROVISION_PAUSE, PROVISION_PAUSED},
                {KIOSK_PROVISIONED, PROVISION_SUCCESS, PROVISION_SUCCEEDED},
                {PROVISION_PAUSED, PROVISION_RESUME, PROVISION_IN_PROGRESS}
        });
    }

    @Test
    public void getNextState_nextStateIsExpectedBasedOnInputStateAndEvent() {
        Truth.assertThat(ProvisionStateControllerImpl.getNextState(mState, mEvent)).isEqualTo(
                mNextState);
    }
}
