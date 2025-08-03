/*
 * Copyright 2019, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.finalization;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;

import static com.android.internal.util.Preconditions.checkNotNull;

import static java.util.Objects.requireNonNull;

import android.annotation.IntDef;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.os.UserManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.analytics.DeferredMetricsReader;
import com.android.managedprovisioning.common.NotificationHelper;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.provisioning.Constants;

import java.io.File;

/**
 * Controller for the finalization of managed provisioning.  This class should be invoked after
 * {@link PreFinalizationController}.  Provisioning is finalized via calls to
 * {@link #provisioningFinalized()} and {@link #commitFinalizedState()}.  Different instances of
 * this class will be tailored to run these two methods at different points in the Setup Wizard user
 * flows, based on the type of FinalizationControllerLogic they are constructed with.
 */
public final class FinalizationController {

    static final int PROVISIONING_FINALIZED_RESULT_NO_CHILD_ACTIVITY_LAUNCHED = 1;
    static final int PROVISIONING_FINALIZED_RESULT_CHILD_ACTIVITY_LAUNCHED = 2;
    static final int PROVISIONING_FINALIZED_RESULT_SKIPPED = 3;
    static final int PROVISIONING_FINALIZED_RESULT_WAIT_FOR_WORK_PROFILE_AVAILABLE = 4;
    static final int PROVISIONING_FINALIZED_RESULT_WORK_PROFILE_NOT_FOUND = 5;

    @IntDef({
            PROVISIONING_FINALIZED_RESULT_NO_CHILD_ACTIVITY_LAUNCHED,
            PROVISIONING_FINALIZED_RESULT_CHILD_ACTIVITY_LAUNCHED,
            PROVISIONING_FINALIZED_RESULT_SKIPPED,
            PROVISIONING_FINALIZED_RESULT_WAIT_FOR_WORK_PROFILE_AVAILABLE,
            PROVISIONING_FINALIZED_RESULT_WORK_PROFILE_NOT_FOUND})
    @interface ProvisioningFinalizedResult {}

    private static final int DPC_SETUP_REQUEST_CODE = 1;
    private static final int FINAL_SCREEN_REQUEST_CODE = 2;

    private final FinalizationControllerLogic mFinalizationControllerLogic;
    private final Context mContext;
    private final Utils mUtils;
    private final SettingsFacade mSettingsFacade;
    private final UserProvisioningStateHelper mUserProvisioningStateHelper;
    private final ProvisioningIntentProvider mProvisioningIntentProvider;
    private final NotificationHelper mNotificationHelper;
    private final DeferredMetricsReader mDeferredMetricsReader;
    private @ProvisioningFinalizedResult int mProvisioningFinalizedResult;
    private ProvisioningParamsUtils mProvisioningParamsUtils;

    public FinalizationController(Context context,
            FinalizationControllerLogic finalizationControllerLogic,
            UserProvisioningStateHelper userProvisioningStateHelper) {
        this(
                context,
                finalizationControllerLogic,
                new Utils(),
                new SettingsFacade(),
                userProvisioningStateHelper,
                new NotificationHelper(context),
                new DeferredMetricsReader(
                        Constants.getDeferredMetricsFile(context)),
                new ProvisioningParamsUtils(
                        ProvisioningParamsUtils.DEFAULT_PROVISIONING_PARAMS_FILE_PROVIDER));
    }

    public FinalizationController(Context context,
            FinalizationControllerLogic finalizationControllerLogic) {
        this(
                context,
                finalizationControllerLogic,
                new Utils(),
                new SettingsFacade(),
                new UserProvisioningStateHelper(context),
                new NotificationHelper(context),
                new DeferredMetricsReader(
                        Constants.getDeferredMetricsFile(context)),
                new ProvisioningParamsUtils(
                        ProvisioningParamsUtils.DEFAULT_PROVISIONING_PARAMS_FILE_PROVIDER));
    }

    @VisibleForTesting
    FinalizationController(Context context,
            FinalizationControllerLogic finalizationControllerLogic,
            Utils utils,
            SettingsFacade settingsFacade,
            UserProvisioningStateHelper helper,
            NotificationHelper notificationHelper,
            DeferredMetricsReader deferredMetricsReader,
            ProvisioningParamsUtils provisioningParamsUtils) {
        mContext = checkNotNull(context);
        mFinalizationControllerLogic = checkNotNull(finalizationControllerLogic);
        mUtils = checkNotNull(utils);
        mSettingsFacade = checkNotNull(settingsFacade);
        mUserProvisioningStateHelper = checkNotNull(helper);
        mProvisioningIntentProvider = new ProvisioningIntentProvider();
        mNotificationHelper = checkNotNull(notificationHelper);
        mDeferredMetricsReader = checkNotNull(deferredMetricsReader);
        mProvisioningParamsUtils = provisioningParamsUtils;
    }

    @VisibleForTesting
    PrimaryProfileFinalizationHelper getPrimaryProfileFinalizationHelper(
            ProvisioningParams params) {
        return new PrimaryProfileFinalizationHelper(params.accountToMigrate,
                mUtils.getManagedProfile(mContext), params.inferDeviceAdminPackageName());
    }

    /**
     * This method is invoked when provisioning is finalized.
     *
     * <p>This method has to be invoked after
     * {@link PreFinalizationController#deviceManagementEstablished(ProvisioningParams)}
     * was called. It is commonly invoked at the end of the setup flow, if provisioning occurs
     * during the setup flow. It loads the provisioning params from the storage, notifies the DPC
     * about the completed provisioning and sets the right user provisioning states.
     *
     * <p>To retrieve the resulting state of this method, use
     * {@link #getProvisioningFinalizedResult()}
     *
     * <p>This method may be called multiple times.  {@link #commitFinalizedState()} ()} must be
     * called after the final call to this method.  If this method is called again after that, it
     * will return immediately without taking any action.
     */
    void provisioningFinalized() {
        mProvisioningFinalizedResult = PROVISIONING_FINALIZED_RESULT_SKIPPED;

        if (mUserProvisioningStateHelper.isStateUnmanagedOrFinalized()) {
            ProvisionLogger.logw("provisioningFinalized called, but state is finalized or "
                    + "unmanaged");
            return;
        }

        final ProvisioningParams params = loadProvisioningParams();
        if (params == null) {
            ProvisionLogger.logw("FinalizationController invoked, but no stored params");
            return;
        }

        mProvisioningFinalizedResult = PROVISIONING_FINALIZED_RESULT_NO_CHILD_ACTIVITY_LAUNCHED;
        if (params.provisioningAction.equals(ACTION_PROVISION_MANAGED_PROFILE)) {
            var userManager = requireNonNull(
                    /* obj= */ mContext.getSystemService(UserManager.class),
                    /* message= */ "Unable to obtain UserManager");
            var userHandle = mUtils.getManagedProfile(mContext);
            if (userHandle == null) {
                // DPC setup failed for whatever reason e.g. user cancelled
                mProvisioningFinalizedResult = PROVISIONING_FINALIZED_RESULT_WORK_PROFILE_NOT_FOUND;
                return;
            }

            if (!userManager.isUserUnlocked(userHandle)) {
                mProvisioningFinalizedResult =
                        PROVISIONING_FINALIZED_RESULT_WAIT_FOR_WORK_PROFILE_AVAILABLE;
            } else {
                mProvisioningFinalizedResult =
                        mFinalizationControllerLogic.notifyDpcManagedProfile(
                                params, DPC_SETUP_REQUEST_CODE);
            }
        } else {
            mProvisioningFinalizedResult =
                    mFinalizationControllerLogic.notifyDpcManagedDeviceOrUser(
                            params, DPC_SETUP_REQUEST_CODE);
        }
    }

    /**
     * @throws IllegalStateException if {@link #provisioningFinalized()} was not called before.
     */
    @ProvisioningFinalizedResult
    int getProvisioningFinalizedResult() {
        if (mProvisioningFinalizedResult == 0) {
            throw new IllegalStateException("provisioningFinalized() has not been called.");
        }
        return mProvisioningFinalizedResult;
    }

    @VisibleForTesting
    void clearParamsFile() {
        final File file = mProvisioningParamsUtils.getProvisioningParamsFile(mContext);
        if (file != null) {
            file.delete();
        }
    }

    private ProvisioningParams loadProvisioningParams() {
        final File file = mProvisioningParamsUtils.getProvisioningParamsFile(mContext);
        return ProvisioningParams.load(file);
    }

    /**
     * Update the system's provisioning state, and commit any other irreversible changes that
     * must wait until finalization is 100% completed.
     */
    private void commitFinalizedState(ProvisioningParams params) {
        if (ACTION_PROVISION_MANAGED_DEVICE.equals(params.provisioningAction)) {
            mNotificationHelper.showPrivacyReminderNotification(
                    mContext, NotificationManager.IMPORTANCE_DEFAULT);
        } else if (ACTION_PROVISION_MANAGED_PROFILE.equals(params.provisioningAction)
                && mFinalizationControllerLogic.shouldFinalizePrimaryProfile(params)) {
            getPrimaryProfileFinalizationHelper(params)
                    .finalizeProvisioningInPrimaryProfile(mContext, null);
        }

        mUserProvisioningStateHelper.markUserProvisioningStateFinalized(params);

        mDeferredMetricsReader.scheduleDumpMetrics(mContext);
        clearParamsFile();
    }

    /**
     * This method is called by the parent activity to force the final commit of all state changes.
     * After this is called, any further calls to {@link #provisioningFinalized()} will return
     * immediately without taking any action.
     */
    void commitFinalizedState() {
        final ProvisioningParams params = loadProvisioningParams();
        if (params == null) {
            ProvisionLogger.logw(
                    "Attempt to commitFinalizedState when params have already been deleted");
        } else {
            commitFinalizedState(loadProvisioningParams());
        }
    }

    /**
     * This method is called when onSaveInstanceState() executes on the finalization activity.
     */
    void saveInstanceState(Bundle outState) {
        mFinalizationControllerLogic.saveInstanceState(outState);
    }

    /**
     * When saved instance state is passed to the finalization activity in its onCreate() method,
     * that state is passed to the FinalizationControllerLogic object here so it can be restored.
     */
    void restoreInstanceState(Bundle savedInstanceState) {
        mFinalizationControllerLogic.restoreInstanceState(savedInstanceState,
                loadProvisioningParams());
    }

    /**
     * Cleanup that must happen when the finalization activity is destroyed, even if we haven't yet
     * called {@link #commitFinalizedState()} to finalize the system's provisioning state.
     */
    void activityDestroyed(boolean isFinishing) {
        mFinalizationControllerLogic.activityDestroyed(isFinishing);
    }
}
