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

package com.android.devicelockcontroller.receivers;

import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_RESUME;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.policy.PolicyObjectsProvider;
import com.android.devicelockcontroller.policy.ProvisionStateController;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ResumeProvisionReceiverTest {


    @Test
    public void onReceive_setStateSuccess() {
        ResumeProvisionReceiver resumeProvisionReceiver = new ResumeProvisionReceiver();
        TestDeviceLockControllerApplication testApp = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(testApp, ResumeProvisionReceiver.class);
        ProvisionStateController stateController =
                ((PolicyObjectsProvider) testApp).getProvisionStateController();

        resumeProvisionReceiver.onReceive(testApp, intent);

        verify(stateController).postSetNextStateForEventRequest(eq(PROVISION_RESUME));
    }
}
