/*
 * Copyright 2016, The Android Open Source Project
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

import android.content.Context;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.task.AbstractProvisioningTask;
import com.android.managedprovisioning.task.CreateAndProvisionManagedProfileTask;

/**
 * Controller for Profile Owner provisioning.
 */
public class ProfileOwnerProvisioningController extends AbstractProvisioningController {
    /**
     * Instantiates a new {@link ProfileOwnerProvisioningController} instance and creates the
     * relevant tasks.
     *
     * @return the newly created instance
     */
    static ProfileOwnerProvisioningController createInstance(
            Context context,
            ProvisioningParams params,
            int userId,
            ProvisioningControllerCallback callback) {
        ProfileOwnerProvisioningController controller =
                new ProfileOwnerProvisioningController(context, params, userId, callback);
        controller.setUpTasks();
        return controller;
    }

    private ProfileOwnerProvisioningController(
            Context context,
            ProvisioningParams params,
            int userId,
            ProvisioningControllerCallback callback) {
        super(context, params, userId, callback);
    }

    protected void setUpTasks() {
        addTasks(new CreateAndProvisionManagedProfileTask(mContext, mParams, this));
    }

    @Override
    public synchronized void onSuccess(AbstractProvisioningTask task) {
        if (task instanceof CreateAndProvisionManagedProfileTask) {
            // If the task was creating a managed profile, store the profile id
            mUserId = ((CreateAndProvisionManagedProfileTask) task).getProfileUserId();
        }
        super.onSuccess(task);
    }

    @Override protected int getErrorTitle() {
        return R.string.cant_set_up_profile;
    }

    @Override
    protected String getErrorMsgId(AbstractProvisioningTask task, int errorCode) {
        return mContext.getString(R.string.managed_provisioning_error_text);
    }

    @Override
    protected boolean getRequireFactoryReset(AbstractProvisioningTask task, int errorCode) {
        if (mParams.isOrganizationOwnedProvisioning) {
            // Do factory reset if any error occurs during PO provisioning in the admin
            // integrated flow.
            return true;
        } else {
            return false;
        }
    }
}
