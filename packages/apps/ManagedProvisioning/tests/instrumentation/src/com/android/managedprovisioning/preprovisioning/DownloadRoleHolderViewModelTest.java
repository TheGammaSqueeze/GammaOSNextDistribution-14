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

package com.android.managedprovisioning.preprovisioning;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;

import static com.android.managedprovisioning.preprovisioning.DownloadRoleHolderViewModel.STATE_DOWNLOADED;
import static com.android.managedprovisioning.preprovisioning.DownloadRoleHolderViewModel.STATE_DOWNLOADING;
import static com.android.managedprovisioning.preprovisioning.DownloadRoleHolderViewModel.STATE_ERROR;
import static com.android.managedprovisioning.preprovisioning.DownloadRoleHolderViewModel.STATE_IDLE;

import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.content.ComponentName;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DownloadRoleHolderViewModelTest {

    private static final ComponentName ADMIN = new ComponentName("com.test.admin", ".Receiver");
    private static final ProvisioningParams PROVISIONING_PARAMS = new ProvisioningParams.Builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
            .setDeviceAdminComponentName(ADMIN)
            .build();
    private static final String ROLE_HOLDER_PACKAGE_NAME = "test.roleholder.package";
    private static final int DIALOG_TITLE_RES_ID = 1;
    private static final int DIALOG_MESSAGE_RES_ID = 2;
    private static final String DIALOG_MESSAGE = "dialog message";
    private DownloadRoleHolderViewModel mViewModel;
    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();

    @Before
    public void setUp() {
        mViewModel = new DownloadRoleHolderViewModel(
                PROVISIONING_PARAMS, new Utils(), new SettingsFacade(), ROLE_HOLDER_PACKAGE_NAME);
    }

    @Test
    public void constructor_initialStateIdle() {
        blockUntilNextUiThreadCycle();

        assertThat(mViewModel.observeState().getValue()).isEqualTo(STATE_IDLE);
    }

    @Test
    public void connectToNetworkAndDownloadRoleHolder_goesToDownloadingState() {
        mViewModel.connectToNetworkAndDownloadRoleHolder(
                 mInstrumentation.getContext());
        blockUntilNextUiThreadCycle();

        assertThat(mViewModel.observeState().getValue()).isEqualTo(STATE_DOWNLOADING);
    }

    @Test
    public void provisioningTasksCompleted_goesToDownloadedState() {
        mViewModel.provisioningTasksCompleted();
        blockUntilNextUiThreadCycle();

        assertThat(mViewModel.observeState().getValue()).isEqualTo(STATE_DOWNLOADED);
    }

    @Test
    public void error_withMessageResId_goesToErrorState() {
        mViewModel.error(DIALOG_TITLE_RES_ID, DIALOG_MESSAGE_RES_ID, false);
        blockUntilNextUiThreadCycle();

        assertThat(mViewModel.observeState().getValue()).isEqualTo(STATE_ERROR);
    }

    @Test
    public void error_withMessageText_goesToErrorState() {
        mViewModel.error(DIALOG_TITLE_RES_ID, DIALOG_MESSAGE, false);
        blockUntilNextUiThreadCycle();

        assertThat(mViewModel.observeState().getValue()).isEqualTo(STATE_ERROR);
    }

    @Test
    public void getError_withMessageResId_works() {
        mInstrumentation.runOnMainSync(
                () -> {
                    mViewModel.error(DIALOG_TITLE_RES_ID, DIALOG_MESSAGE_RES_ID,
                            /* factoryResetRequired= */ true);

                    assertThat(mViewModel.getError().dialogTitleId).isEqualTo(DIALOG_TITLE_RES_ID);
                    assertThat(mViewModel.getError().errorMessageResId)
                            .isEqualTo(DIALOG_MESSAGE_RES_ID);
                    assertThat(mViewModel.getError().factoryResetRequired).isTrue();
                });
    }

    @Test
    public void getError_errorStateFollowedByNonErrorState_isNull() {
        mInstrumentation.runOnMainSync(
                () -> {
                    mViewModel.error(DIALOG_TITLE_RES_ID, DIALOG_MESSAGE_RES_ID,
                            /* factoryResetRequired= */ true);
                    mViewModel.connectToNetworkAndDownloadRoleHolder(mInstrumentation.getContext());

                    assertThat(mViewModel.getError()).isNull();
                });
    }

    @Test
    public void getError_withMessageText_isNull() {
        mInstrumentation.runOnMainSync(
                () -> {
                    mViewModel.error(DIALOG_TITLE_RES_ID, DIALOG_MESSAGE,
                            /* factoryResetRequired= */ true);

                    assertThat(mViewModel.getError()).isNull();
                });
    }

    @Test
    public void getError_nullByDefault() {
        assertThat(mViewModel.getError()).isNull();
    }

    private void blockUntilNextUiThreadCycle() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {});
    }
}
