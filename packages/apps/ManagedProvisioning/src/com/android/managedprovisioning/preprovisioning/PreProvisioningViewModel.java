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

package com.android.managedprovisioning.preprovisioning;

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_PREPROVISIONING_ACTIVITY_TIME_MS;

import static java.util.Objects.requireNonNull;

import android.annotation.IntDef;
import android.annotation.MainThread;
import android.annotation.Nullable;
import android.content.Intent;
import android.os.PersistableBundle;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.android.managedprovisioning.ManagedProvisioningBaseApplication;
import com.android.managedprovisioning.analytics.TimeLogger;
import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.parser.MessageParser;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A {@link ViewModel} which maintains data related to preprovisioning.
 */
public final class PreProvisioningViewModel extends ViewModel {
    static final int STATE_PREPROVISIONING_INITIALIZING = 1;
    static final int STATE_PREPROVISIONING_INITIALIZED = 2;
    static final int STATE_GETTING_PROVISIONING_MODE = 3;
    static final int STATE_SHOWING_USER_CONSENT = 4;
    static final int STATE_PROVISIONING_STARTED = 5;
    static final int STATE_PROVISIONING_FINALIZED = 6;
    static final int STATE_UPDATING_ROLE_HOLDER = 7;
    static final int STATE_ROLE_HOLDER_PROVISIONING = 8;

    private static final int DEFAULT_MAX_ROLE_HOLDER_UPDATE_RETRIES = 3;
    private PersistableBundle mRoleHolderState;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STATE_PREPROVISIONING_INITIALIZING,
            STATE_GETTING_PROVISIONING_MODE,
            STATE_SHOWING_USER_CONSENT,
            STATE_PROVISIONING_STARTED,
            STATE_PROVISIONING_FINALIZED,
            STATE_UPDATING_ROLE_HOLDER,
            STATE_ROLE_HOLDER_PROVISIONING})
    private @interface PreProvisioningState {}


    private ProvisioningParams mParams;
    private final MessageParser mMessageParser;
    private final TimeLogger mTimeLogger;
    private final EncryptionController mEncryptionController;
    private final MutableLiveData<Integer> mState =
            new MutableLiveData<>(STATE_PREPROVISIONING_INITIALIZING);
    private final Config mConfig;
    private int mRoleHolderUpdateRetries = 1;

    PreProvisioningViewModel(
            TimeLogger timeLogger,
            MessageParser messageParser,
            EncryptionController encryptionController,
            Config config) {
        mMessageParser = requireNonNull(messageParser);
        mTimeLogger = requireNonNull(timeLogger);
        mEncryptionController = requireNonNull(encryptionController);
        mConfig = requireNonNull(config);
    }

    /**
     * Updates state after provisioning has completed
     */
    @MainThread
    public void onReturnFromProvisioning() {
        setState(STATE_PROVISIONING_FINALIZED);
    }

    /**
     * Handles state when the admin-integrated flow was initiated
     */
    @MainThread
    public void onAdminIntegratedFlowInitiated() {
        setState(STATE_GETTING_PROVISIONING_MODE);
    }

    /**
     * Handles state when user consent is shown
     */
    @MainThread
    public void onShowUserConsent() {
        setState(STATE_SHOWING_USER_CONSENT);
    }

    /**
     * Handles state when provisioning is started after the user consent
     */
    @MainThread
    public void onProvisioningStartedAfterUserConsent() {
        setState(STATE_PROVISIONING_STARTED);
    }

    /**
     * Handles state when provisioning has initiated
     */
    @MainThread
    public void onPlatformProvisioningInitiated() {
        setState(STATE_PREPROVISIONING_INITIALIZED);
    }

    /**
     * Handles state when the role holder update has started
     */
    @MainThread
    public void onRoleHolderUpdateInitiated() {
        setState(STATE_UPDATING_ROLE_HOLDER);
    }

    /**
     * Handles state when provisioning is delegated to the role holder
     */
    @MainThread
    public void onRoleHolderProvisioningInitiated() {
        setState(STATE_ROLE_HOLDER_PROVISIONING);
    }

    /**
     * Sets a copy of the role holder state.
     * @param roleHolderState
     */
    public void setRoleHolderState(@Nullable PersistableBundle roleHolderState) {
        if (roleHolderState == null) {
            mRoleHolderState = null;
        } else {
            mRoleHolderState = new PersistableBundle(roleHolderState);
        }
    }

    /**
     * Retrieves a copy of the role holder state or {@link null} if one does not exist.
     * @return
     */
    public @Nullable PersistableBundle getRoleHolderState() {
        if (mRoleHolderState == null) {
            return null;
        } else {
            return new PersistableBundle(mRoleHolderState);
        }
    }

    /**
     * Returns the state as a {@link LiveData}.
     */
    LiveData<Integer> getState() {
        return mState;
    }

    /**
     * Loads the {@link ProvisioningParams} if they haven't been loaded before.
     *
     * @throws IllegalProvisioningArgumentException if the intent extras are invalid
     */
    void loadParamsIfNecessary(Intent intent) throws IllegalProvisioningArgumentException {
        if (mParams == null) {
            mParams = loadProvisioningParams(intent);
        }
    }

    /**
     * Returns the {@link ProvisioningParams} associated with this provisioning session.
     */
    ProvisioningParams getParams() {
        if (mParams == null) {
            throw new IllegalStateException("Trying to get params without loading them first. "
                    + "Did you run loadParamsIfNecessary(Intent)?");
        }
        return mParams;
    }

    /**
     * Replaces the cached {@link ProvisioningParams} with {@code params}.
     */
    void updateParams(ProvisioningParams params) {
        if (params == null) {
            throw new IllegalArgumentException("Cannot update params to null.");
        }
        mParams = params;
    }

    /**
     * Returns the cached {@link TimeLogger} instance.
     */
    TimeLogger getTimeLogger() {
        return mTimeLogger;
    }

    /**
     * Returns the cached {@link EncryptionController} instance.
     */
    EncryptionController getEncryptionController() {
        return mEncryptionController;
    }

    private ProvisioningParams loadProvisioningParams(Intent intent)
            throws IllegalProvisioningArgumentException {
        return mMessageParser.parse(intent);
    }

    /**
     * Sets the state which updates all the listening obervables.
     * <p>This method must be called from the UI thread.
     */
    @MainThread
    private void setState(@PreProvisioningState int state) {
        if (mState.getValue() != state) {
            ProvisionLogger.logi(
                    "Setting preprovisioning state to " + state + ", previous state was "
                            + mState.getValue());
            mState.setValue(state);
        } else {
            ProvisionLogger.logi(
                    "Attempt to set preprovisioning state to the same state " + mState);
        }
    }

    void incrementRoleHolderUpdateRetryCount() {
        mRoleHolderUpdateRetries++;
    }

    /**
     * Resets the update retry count
     */
    public void resetRoleHolderUpdateRetryCount() {
        mRoleHolderUpdateRetries = 0;
    }

    boolean canRetryRoleHolderUpdate() {
        return mRoleHolderUpdateRetries < mConfig.getMaxRoleHolderUpdateRetries();
    }

    interface Config {
        int getMaxRoleHolderUpdateRetries();
    }

    static class DefaultConfig implements Config {
        @Override
        public int getMaxRoleHolderUpdateRetries() {
            return DEFAULT_MAX_ROLE_HOLDER_UPDATE_RETRIES;
        }
    }

    static class PreProvisioningViewModelFactory implements ViewModelProvider.Factory {
        private final ManagedProvisioningBaseApplication mApplication;
        private final Config mConfig;
        private final Utils mUtils;

        PreProvisioningViewModelFactory(
                ManagedProvisioningBaseApplication application,
                Config config,
                Utils utils) {
            mApplication = requireNonNull(application);
            mConfig = requireNonNull(config);
            mUtils = requireNonNull(utils);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(Class<T> modelClass) {
            if (!PreProvisioningViewModel.class.isAssignableFrom(modelClass)) {
                throw new IllegalArgumentException("Invalid class for creating a "
                        + "PreProvisioningViewModel: " + modelClass);
            }
            return (T) new PreProvisioningViewModel(
                    new TimeLogger(mApplication, PROVISIONING_PREPROVISIONING_ACTIVITY_TIME_MS),
                    new MessageParser(mApplication, mUtils),
                    mApplication.getEncryptionController(),
                    mConfig);
        }
    }
}
