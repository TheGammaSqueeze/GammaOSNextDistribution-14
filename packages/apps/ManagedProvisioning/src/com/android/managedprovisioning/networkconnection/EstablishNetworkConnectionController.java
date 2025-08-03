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

import static java.util.Objects.requireNonNull;

import android.content.Context;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.provisioning.AbstractProvisioningController;
import com.android.managedprovisioning.provisioning.ProvisioningControllerCallback;
import com.android.managedprovisioning.task.AbstractProvisioningTask;
import com.android.managedprovisioning.task.AddWifiNetworkTask;
import com.android.managedprovisioning.task.TaskFactory;

/**
 * Controller which establishes network connection.
 */
public final class EstablishNetworkConnectionController extends AbstractProvisioningController {

    private final Utils mUtils;
    private final SettingsFacade mSettingsFacade;
    private final TaskFactory mTaskFactory;

    /**
     * Instantiates a new {@link EstablishNetworkConnectionController} instance and creates the
     * relevant tasks.
     *
     * @return the newly created instance
     */
    public static EstablishNetworkConnectionController createInstance(
            Context context,
            ProvisioningParams params,
            int userId,
            ProvisioningControllerCallback callback,
            Utils utils,
            SettingsFacade settingsFacade,
            TaskFactory taskFactory) {
        EstablishNetworkConnectionController controller =
                new EstablishNetworkConnectionController(context, params, userId, callback,
                        utils, settingsFacade, taskFactory);
        controller.setUpTasks();
        return controller;
    }

    private EstablishNetworkConnectionController(Context context,
            ProvisioningParams params, int userId,
            ProvisioningControllerCallback callback,
            Utils utils,
            SettingsFacade settingsFacade,
            TaskFactory taskFactory) {
        super(context, params, userId, callback);
        mUtils = requireNonNull(utils);
        mSettingsFacade = requireNonNull(settingsFacade);
        mTaskFactory = requireNonNull(taskFactory);
    }

    @Override
    protected void setUpTasks() {
        if (mParams.wifiInfo != null) {
            addTasks(mTaskFactory.createAddWifiNetworkTask(mContext, mParams, this));
        } else if (mParams.useMobileData) {
            addTasks(mTaskFactory.createConnectMobileNetworkTask(mContext, mParams, this));
        }
    }

    @Override
    protected int getErrorTitle() {
        return R.string.cant_set_up_device;
    }

    @Override
    protected String getErrorMsgId(AbstractProvisioningTask task, int errorCode) {
        if (task instanceof AddWifiNetworkTask) {
            return mContext.getString(R.string.error_wifi);
        }
        return mContext.getString(R.string.cant_set_up_device);
    }

    @Override
    protected boolean getRequireFactoryReset(AbstractProvisioningTask task, int errorCode) {
        // No irreversible action was done since this controller only handles network connection.
        // No need to factory reset.
        return false;
    }
}
