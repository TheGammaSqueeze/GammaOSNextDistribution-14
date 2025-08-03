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

import static java.util.Objects.requireNonNull;

import android.content.Context;
import android.os.UserHandle;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.android.managedprovisioning.ManagedProvisioningBaseApplication;
import com.android.managedprovisioning.common.ErrorWrapper;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.provisioning.DownloadRoleHolderController;
import com.android.managedprovisioning.provisioning.ProvisioningControllerCallback;
import com.android.managedprovisioning.provisioning.ProvisioningManagerHelper;

/**
 * A {@link ViewModel} which manages the state for the download role holder screen.
 */
public class DownloadRoleHolderViewModel extends ViewModel implements
        ProvisioningControllerCallback {
    public static final int STATE_IDLE = 1;
    public static final int STATE_DOWNLOADING = 2;
    public static final int STATE_DOWNLOADED = 3;
    public static final int STATE_ERROR = 4;

    private final ProvisioningManagerHelper mProvisioningManagerHelper =
            new ProvisioningManagerHelper();
    private final MutableLiveData<Integer> mState = new MutableLiveData<>(STATE_IDLE);
    private final ProvisioningParams mParams;
    private final Utils mUtils;
    private final SettingsFacade mSettingsFacade;
    private final String mRoleHolderPackageName;
    private ErrorWrapper mErrorWrapper;

    public DownloadRoleHolderViewModel(
            ProvisioningParams params,
            Utils utils,
            SettingsFacade settingsFacade,
            String roleHolderPackageName) {
        mParams = requireNonNull(params);
        mUtils = requireNonNull(utils);
        mSettingsFacade = requireNonNull(settingsFacade);
        mRoleHolderPackageName = requireNonNull(roleHolderPackageName);
    }

    /**
     * Returns {@link MutableLiveData} describing the state.
     */
    public MutableLiveData<Integer> observeState() {
        return mState;
    }

    /**
     * Connects to wifi or mobile data if needed, and downloads the role holder.
     */
    public void connectToNetworkAndDownloadRoleHolder(Context context) {
        mProvisioningManagerHelper.startNewProvisioningLocked(
                DownloadRoleHolderController.createInstance(
                        context,
                        mParams,
                        UserHandle.USER_SYSTEM,
                        this,
                        mUtils,
                        mSettingsFacade,
                        mRoleHolderPackageName));
        updateState(STATE_DOWNLOADING);
    }

    @Override
    public void cleanUpCompleted() {
        mProvisioningManagerHelper.clearResourcesLocked();
    }

    @Override
    public void provisioningTasksCompleted() {
        updateState(STATE_DOWNLOADED);
    }

    @Override
    public void error(int dialogTitleId, int errorMessageId, boolean factoryResetRequired) {
        mErrorWrapper = new ErrorWrapper(dialogTitleId, errorMessageId, factoryResetRequired);
        updateState(STATE_ERROR);
    }

    @Override
    public void error(int dialogTitleId, String errorMessage, boolean factoryResetRequired) {
        // We don't assign ErrorWrapper here since all errors would come as errorMessageId in the
        // other override. This specific override is only meant for cases when the OEM returns
        // a string error during the tasks. Today this only happens for the provisioning DPM APIs.
        updateState(STATE_ERROR);
    }

    @Override
    public void preFinalizationCompleted() {}

    /**
     * Returns an {@link ErrorWrapper} which describes the last error that happened. This will
     * only be non-{@code null} if {@link #observeState()} returns {@link #STATE_ERROR}.
     */
    public ErrorWrapper getError() {
        return mErrorWrapper;
    }

    private void updateState(int stateDownloading) {
        if (stateDownloading != STATE_ERROR) {
            mErrorWrapper = null;
        }
        mState.postValue(stateDownloading);
    }

    /**
     * A factory for {@link DownloadRoleHolderViewModel}.
     */
    public static class DownloadRoleHolderViewModelFactory implements ViewModelProvider.Factory {
        private final ProvisioningParams mParams;
        private final Utils mUtils;
        private final SettingsFacade mSettingsFacade;
        private final String mRoleHolderPackageName;

        public DownloadRoleHolderViewModelFactory(
                ManagedProvisioningBaseApplication application,
                ProvisioningParams params,
                Utils utils,
                SettingsFacade settingsFacade,
                String roleHolderPackageName) {
            mParams = requireNonNull(params);
            mUtils = requireNonNull(utils);
            mSettingsFacade = requireNonNull(settingsFacade);
            mRoleHolderPackageName = requireNonNull(roleHolderPackageName);
        }

        @Override
        public <T extends ViewModel> T create(Class<T> modelClass) {
            if (!DownloadRoleHolderViewModel.class.isAssignableFrom(modelClass)) {
                throw new IllegalArgumentException("Invalid class for creating a "
                        + "DownloadRoleHolderViewModel: " + modelClass);
            }
            return (T) new DownloadRoleHolderViewModel(
                    mParams, mUtils, mSettingsFacade, mRoleHolderPackageName);
        }
    }
}
