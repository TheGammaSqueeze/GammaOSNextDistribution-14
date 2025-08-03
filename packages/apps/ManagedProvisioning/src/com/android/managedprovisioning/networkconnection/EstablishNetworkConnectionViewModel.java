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
import android.content.Intent;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.android.managedprovisioning.common.ErrorWrapper;
import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.SharedPreferences;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.parser.MessageParser;
import com.android.managedprovisioning.provisioning.ProvisioningControllerCallback;
import com.android.managedprovisioning.provisioning.ProvisioningManagerHelper;
import com.android.managedprovisioning.task.TaskFactory;

/**
 * A {@link ViewModel} which manages the state for the network connection establishing screen.
 */
public final class EstablishNetworkConnectionViewModel extends ViewModel implements
        ProvisioningControllerCallback {

    public static final int STATE_IDLE = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_SHOW_NETWORK_PICKER = 3;
    public static final int STATE_ERROR = 4;
    public static final int STATE_CONNECTED = 5;

    private final ProvisioningManagerHelper mProvisioningManagerHelper =
            new ProvisioningManagerHelper();
    private final MutableLiveData<Integer> mState = new MutableLiveData<>(STATE_IDLE);
    private final Utils mUtils;
    private final SettingsFacade mSettingsFacade;
    private final SharedPreferences mSharedPreferences;
    private ErrorWrapper mErrorWrapper;

    public EstablishNetworkConnectionViewModel(
            Utils utils,
            SettingsFacade settingsFacade,
            SharedPreferences sharedPreferences) {
        mUtils = requireNonNull(utils);
        mSettingsFacade = requireNonNull(settingsFacade);
        mSharedPreferences = requireNonNull(sharedPreferences);
    }

    /**
     * Parses and returns the provisioning extras contained in {@code intent}.
     */
    public ProvisioningParams parseExtras(Context context, Intent intent) {
        requireNonNull(context);
        requireNonNull(intent);
        ProvisioningParams params = null;
        try {
            params = new MessageParser(context, mUtils).parse(intent);
        } catch (IllegalProvisioningArgumentException e) {
            ProvisionLogger.loge("Error parsing intent extras", e);
        }
        return params;
    }

    /**
     * Returns {@link MutableLiveData} describing the state.
     */
    public MutableLiveData<Integer> observeState() {
        return mState;
    }

    /**
     * Connects to wifi or mobile data if needed.
     */
    public void connectToNetwork(Context context,
            ProvisioningParams params) {
        requireNonNull(context);
        requireNonNull(params);
        mSharedPreferences.setIsEstablishNetworkConnectionRun(true);
        var networkCapabilities = mUtils.getActiveNetworkCapabilities(context);
        if (networkCapabilities != null
                && mUtils.isNetworkConnectedToInternetViaEthernet(networkCapabilities)) {
            updateState(STATE_CONNECTED);
            return;
        } else if (params.wifiInfo == null
                && !params.useMobileData) {
            updateState(STATE_SHOW_NETWORK_PICKER);
            return;
        }
        mProvisioningManagerHelper.startNewProvisioningLocked(
                EstablishNetworkConnectionController.createInstance(
                        context,
                        params,
                        UserHandle.USER_SYSTEM,
                        this,
                        mUtils,
                        mSettingsFacade,
                        new TaskFactory()));
        updateState(STATE_CONNECTING);
    }

    @Override
    public void cleanUpCompleted() {
        mProvisioningManagerHelper.clearResourcesLocked();
    }

    @Override
    public void provisioningTasksCompleted() {
        updateState(STATE_CONNECTED);
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

    private void updateState(int stateDownloading) {
        if (stateDownloading != STATE_ERROR) {
            mErrorWrapper = null;
        }
        mState.postValue(stateDownloading);
    }

    /**
     * Returns an {@link ErrorWrapper} which describes the last error that happened. This will
     * only be non-{@code null} if {@link #observeState()} returns {@link #STATE_ERROR}.
     */
    public ErrorWrapper getError() {
        return mErrorWrapper;
    }

    @Override
    public void preFinalizationCompleted() {
    }

    static class EstablishNetworkConnectionViewModelFactory implements ViewModelProvider.Factory {
        private final Utils mUtils;
        private final SettingsFacade mSettingsFacade;
        private final SharedPreferences mSharedPreferences;

        EstablishNetworkConnectionViewModelFactory(
                Utils utils,
                SettingsFacade settingsFacade,
                SharedPreferences sharedPreferences) {
            mUtils = requireNonNull(utils);
            mSettingsFacade = requireNonNull(settingsFacade);
            mSharedPreferences = requireNonNull(sharedPreferences);
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (!EstablishNetworkConnectionViewModel.class.isAssignableFrom(modelClass)) {
                throw new IllegalArgumentException("Invalid class for creating a "
                        + "EstablishNetworkConnectionViewModel: " + modelClass);
            }
            return (T) new EstablishNetworkConnectionViewModel(
                    mUtils, mSettingsFacade, mSharedPreferences);
        }
    }
}
