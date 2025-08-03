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

package com.android.devicelockcontroller.storage;

import static com.android.devicelockcontroller.policy.FinalizationControllerImpl.FinalizationState.FINALIZED;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class GlobalParametersTest extends AbstractGlobalParametersTestBase {
    private Context mContext;

    @Before
    public void setup() {
        mContext = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void getRegisteredId_shouldReturnExpectedResult() {
        assertThat(GlobalParameters.getRegisteredDeviceId(mContext)).isNull();

        GlobalParameters.setRegisteredDeviceId(mContext, REGISTERED_DEVICE_ID);

        assertThat(GlobalParameters.getRegisteredDeviceId(mContext)).isEqualTo(
                REGISTERED_DEVICE_ID);
    }

    @Test
    public void isProvisionForced_shouldReturnExpectedResult() {
        assertThat(GlobalParameters.isProvisionForced(mContext)).isNotEqualTo(FORCED_PROVISION);

        GlobalParameters.setProvisionForced(mContext, FORCED_PROVISION);

        assertThat(GlobalParameters.isProvisionForced(mContext)).isEqualTo(FORCED_PROVISION);
    }

    @Test
    public void getLastReceivedProvisionState_shouldReturnExpectedResult() {
        assertThat(GlobalParameters.getLastReceivedProvisionState(mContext)).isNotEqualTo(
                LAST_RECEIVED_PROVISION_STATE);

        GlobalParameters.setLastReceivedProvisionState(mContext, LAST_RECEIVED_PROVISION_STATE);

        assertThat(GlobalParameters.getLastReceivedProvisionState(mContext)).isEqualTo(
                LAST_RECEIVED_PROVISION_STATE);
    }

    @Test
    public void getFinalizationState_shouldReturnExpectedResult() {
        assertThat(GlobalParameters.getFinalizationState(mContext)).isNotEqualTo(FINALIZED);

        GlobalParameters.setFinalizationState(mContext, FINALIZED);

        assertThat(GlobalParameters.getFinalizationState(mContext)).isEqualTo(FINALIZED);
    }
}
