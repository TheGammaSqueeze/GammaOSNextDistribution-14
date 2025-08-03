/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.managedprovisioning.provisioning;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;
import static android.app.admin.DevicePolicyManager.FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.task.AbstractProvisioningTask;
import com.android.managedprovisioning.task.AddWifiNetworkTask;
import com.android.managedprovisioning.task.CreateAndProvisionManagedProfileTask;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(JUnit4.class)
public final class AdminIntegratedFlowPrepareControllerTest {
    private static final String ADMIN_PACKAGE = "com.test.admin";
    private static final ComponentName ADMIN = new ComponentName(ADMIN_PACKAGE, ".Receiver");
    private static final ProvisioningParams ORG_OWNED_PARAMS = new ProvisioningParams.Builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
            .setDeviceAdminComponentName(ADMIN)
            .setInitiatorRequestedProvisioningModes(FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED)
            .build();
    private static final ProvisioningParams PERSONALLY_OWNED_PARAMS =
            new ProvisioningParams.Builder()
                    .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
                    .setDeviceAdminComponentName(ADMIN)
                    .build();
    private static final ProvisioningParams PARAMS = PERSONALLY_OWNED_PARAMS;
    private static final int USER_ID = UserHandle.USER_SYSTEM;
    private static final Utils mUtils = new Utils();
    private static final int ERROR_CODE = 0;
    private static final String TEST_ERROR_MESSAGE = "test error message";

    private final Context mContext = InstrumentationRegistry.getTargetContext();
    @Mock
    private SettingsFacade mSettingsFacade;
    private final AbstractProvisioningTask mTask =
            new CreateAndProvisionManagedProfileTask(
                    mContext, PARAMS, createProvisioningTaskCallback());
    private final AddWifiNetworkTask mWifiTask =
            new AddWifiNetworkTask(mContext, PARAMS, createProvisioningTaskCallback());
    private String mErrorMessage;
    private HandlerThread mHandlerThread;
    private Handler mHandler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mHandlerThread = new HandlerThread("TestHandler");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    @After
    public void tearDown() {
        mHandlerThread.quitSafely();
    }

    @Test
    public void getRequireFactoryReset_deviceProvisioned_returnsFalse() {
        AdminIntegratedFlowPrepareController mController =
                createController(PARAMS, /* isDeviceProvisioned= */ true);

        assertThat(mController.getRequireFactoryReset(mTask, ERROR_CODE)).isFalse();
    }

    @Test
    public void getRequireFactoryReset_isPersonallyOwned_returnsFalse() {
        AdminIntegratedFlowPrepareController mController =
                createController(PERSONALLY_OWNED_PARAMS);

        assertThat(mController.getRequireFactoryReset(mTask, ERROR_CODE)).isFalse();
    }

    @Test
    public void getRequireFactoryReset_isWifiNetworkTask_returnsFalse() {
        AdminIntegratedFlowPrepareController mController = createController(ORG_OWNED_PARAMS);

        assertThat(mController.getRequireFactoryReset(mWifiTask, ERROR_CODE)).isFalse();
    }

    @Test
    public void getRequireFactoryReset_works() {
        AdminIntegratedFlowPrepareController controller = createController(ORG_OWNED_PARAMS);

        assertThat(controller.getRequireFactoryReset(mTask, ERROR_CODE)).isTrue();
    }

    @Test
    public void testErrorWithStringMessage() {
        AdminIntegratedFlowPrepareController controller = createController(PARAMS);
        controller.start(mHandler);

        controller.onError(mTask, /* errorCode= */ 0, TEST_ERROR_MESSAGE);

        assertThat(mErrorMessage).isEqualTo(TEST_ERROR_MESSAGE);
    }

    private AdminIntegratedFlowPrepareController createController(
            ProvisioningParams params) {
        return createController(params, /* isDeviceProvisioned= */ false);
    }

    private AdminIntegratedFlowPrepareController createController(
            ProvisioningParams params,
            boolean isDeviceProvisioned) {
        AdminIntegratedFlowPrepareController mController =
                AdminIntegratedFlowPrepareController.createInstance(
                        mContext,
                        params,
                        USER_ID,
                        createProvisioningControllerCallback(),
                        mUtils,
                        mSettingsFacade);
        when(mSettingsFacade.isDeviceProvisioned(mContext)).thenReturn(isDeviceProvisioned);
        return mController;
    }

    private ProvisioningControllerCallback createProvisioningControllerCallback() {
        return new ProvisioningControllerCallback() {
            @Override
            public void cleanUpCompleted() {}

            @Override
            public void provisioningTasksCompleted() {}

            @Override
            public void error(int dialogTitleId, int errorMessageId,
                    boolean factoryResetRequired) {}

            @Override
            public void error(int dialogTitleId, String errorMessage,
                    boolean factoryResetRequired) {
                mErrorMessage = errorMessage;
            }

            @Override
            public void preFinalizationCompleted() {}
        };
    }

    private AbstractProvisioningTask.Callback createProvisioningTaskCallback() {
        return new AbstractProvisioningTask.Callback() {
            @Override
            public void onSuccess(AbstractProvisioningTask task) {}

            @Override
            public void onError(
                    AbstractProvisioningTask task, int errorCode, String errorMessage) {}
        };
    }
}
