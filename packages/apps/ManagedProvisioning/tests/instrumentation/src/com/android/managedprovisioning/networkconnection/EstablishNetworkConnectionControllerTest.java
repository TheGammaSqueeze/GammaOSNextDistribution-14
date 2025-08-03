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

package com.android.managedprovisioning.networkconnection;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;

import androidx.test.core.app.ApplicationProvider;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.model.WifiInfo;
import com.android.managedprovisioning.provisioning.ProvisioningControllerCallback;
import com.android.managedprovisioning.task.AbstractProvisioningTask;
import com.android.managedprovisioning.task.AddWifiNetworkTask;
import com.android.managedprovisioning.task.ConnectMobileNetworkTask;
import com.android.managedprovisioning.task.MockTask;
import com.android.managedprovisioning.task.TaskFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

@RunWith(JUnit4.class)
public final class EstablishNetworkConnectionControllerTest {
    private static final String ADMIN_PACKAGE = "com.test.admin";
    private static final ComponentName ADMIN = new ComponentName(ADMIN_PACKAGE, ".Receiver");
    private static final int USER_ID = UserHandle.USER_SYSTEM;
    private static final String TEST_SSID = "\"test ssid\"";
    private static final ProvisioningParams PARAMS =
            new ProvisioningParams.Builder(/* skipValidation= */ false)
                    .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
                    .setDeviceAdminComponentName(ADMIN)
                    .build();
    private static final int ERROR_CODE = 1;
    private static final ProvisioningParams PARAMS_WITH_WIFI_INFO =
            new ProvisioningParams.Builder(/* skipValidation= */ false)
                    .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
                    .setDeviceAdminComponentName(ADMIN)
                    .setWifiInfo(
                            new WifiInfo.Builder()
                                    .setSsid(TEST_SSID)
                                    .build())
                    .build();
    private static final ProvisioningParams PARAMS_WITH_MOBILE_DATA_ENABLED =
            new ProvisioningParams.Builder(/* skipValidation= */ false)
                    .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
                    .setDeviceAdminComponentName(ADMIN)
                    .setUseMobileData(true)
                    .build();
    private static final Utils UTILS = new Utils();

    @Mock
    private SettingsFacade mSettingsFacade;
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private final AddWifiNetworkTask mWifiTask =
            new AddWifiNetworkTask(mContext, PARAMS, createProvisioningTaskCallback());
    private final ConnectMobileNetworkTask mConnectMobileNetworkTask =
            new ConnectMobileNetworkTask(mContext, PARAMS, createProvisioningTaskCallback());
    private final ConnectMobileNetworkTask mTask = mConnectMobileNetworkTask;
    private List<Class<? extends AbstractProvisioningTask>> mCompletedTasks;
    private Semaphore mSemaphore;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mCompletedTasks = new ArrayList<>();
        mHandlerThread = new HandlerThread("TestHandler");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    @Test
    public void getErrorTitle_works() {
        assertThat(createController(PARAMS).getErrorTitle())
                .isEqualTo(R.string.cant_set_up_device);
    }

    @Test
    public void getErrorMsgId_wifiTask_works() {
        assertThat(createController(PARAMS).getErrorMsgId(mWifiTask, ERROR_CODE))
                .isEqualTo(mContext.getString(R.string.error_wifi));
    }

    @Test
    public void getErrorMsgId_connectMobileNetworkTask_works() {
        assertThat(createController(PARAMS).getErrorMsgId(mConnectMobileNetworkTask, ERROR_CODE))
                .isEqualTo(mContext.getString(R.string.cant_set_up_device));
    }

    @Test
    public void getRequireFactoryReset_works() {
        assertThat(createController(PARAMS)
                .getRequireFactoryReset(mTask, ERROR_CODE))
                .isFalse();
    }

    @Test
    public void run_withWifiInfo_runsAddWifiNetworkTask() throws InterruptedException {
        EstablishNetworkConnectionController controller = createController(PARAMS_WITH_WIFI_INFO);

        startControllerAndWait(controller);

        assertThat(mCompletedTasks).containsExactly(AddWifiNetworkTask.class);
    }

    @Test
    public void run_withMobileDataAllowed_runsConnectMobileNetworkTask()
            throws InterruptedException {
        EstablishNetworkConnectionController controller =
                createController(PARAMS_WITH_MOBILE_DATA_ENABLED);

        startControllerAndWait(controller);

        assertThat(mCompletedTasks).containsExactly(ConnectMobileNetworkTask.class);
    }

    @Test
    public void run_noWifiOrMobileDataParams_noTasksExecuted() throws InterruptedException {
        EstablishNetworkConnectionController controller = createController(PARAMS);

        startControllerAndWait(controller);

        assertThat(mCompletedTasks).isEmpty();
    }

    @After
    public void tearDown() {
        mHandlerThread.quitSafely();
    }

    private void startControllerAndWait(EstablishNetworkConnectionController controller)
            throws InterruptedException {
        mSemaphore = new Semaphore(0);
        controller.start(mHandler.getLooper());

        mSemaphore.acquire();
    }

    private EstablishNetworkConnectionController createController(
            ProvisioningParams params) {
        return EstablishNetworkConnectionController.createInstance(
                mContext,
                params,
                USER_ID,
                createProvisioningControllerCallback(),
                UTILS,
                mSettingsFacade,
                new MockTaskFactory());
    }

    private class MockTaskFactory extends TaskFactory {
        @Override
        public AbstractProvisioningTask createConnectMobileNetworkTask(Context context,
                ProvisioningParams provisioningParams, AbstractProvisioningTask.Callback callback) {
            return new MockTask(context, provisioningParams, callback,
                    task -> {
                        mCompletedTasks.add(ConnectMobileNetworkTask.class);
                        callback.onSuccess(task);
                    });
        }

        @Override
        public AbstractProvisioningTask createAddWifiNetworkTask(Context context,
                ProvisioningParams provisioningParams, AbstractProvisioningTask.Callback callback) {
            return new MockTask(context, provisioningParams, callback,
                    task -> {
                        mCompletedTasks.add(AddWifiNetworkTask.class);
                        callback.onSuccess(task);
                    });
        }
    }

    private ProvisioningControllerCallback createProvisioningControllerCallback() {
        return new ProvisioningControllerCallback() {
            @Override
            public void cleanUpCompleted() {

            }

            @Override
            public void provisioningTasksCompleted() {
                mSemaphore.release();
            }

            @Override
            public void error(int dialogTitleId, int errorMessageId,
                    boolean factoryResetRequired) {
                mSemaphore.release();
            }

            @Override
            public void error(int dialogTitleId, String errorMessage,
                    boolean factoryResetRequired) {
                mSemaphore.release();
            }

            @Override
            public void preFinalizationCompleted() {

            }
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
