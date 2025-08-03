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

import static android.content.Intent.ACTION_USER_UNLOCKED;

import static com.android.managedprovisioning.finalization.FinalizationController.PROVISIONING_FINALIZED_RESULT_CHILD_ACTIVITY_LAUNCHED;
import static com.android.managedprovisioning.finalization.FinalizationController.PROVISIONING_FINALIZED_RESULT_WAIT_FOR_WORK_PROFILE_AVAILABLE;
import static com.android.managedprovisioning.provisioning.Constants.PROVISIONING_SERVICE_INTENT;

import android.app.Activity;
import android.app.BackgroundServiceStartNotAllowedException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.WindowManager;

import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.TransitionHelper;

/**
 * Instances of this base class manage interactions with a Device Policy Controller app after it has
 * been set up as either a Device Owner or a Profile Owner.
 *
 * Once we are sure that the DPC app has had an opportunity to run its own setup activities, we
 * record in the system that provisioning has been finalized.
 *
 * Different instances of this class will be tailored to run at different points in the Setup
 * Wizard user flows.
 */
public abstract class FinalizationActivityBase extends Activity {

    private static final String CONTROLLER_STATE_KEY = "controller_state";

    private final TransitionHelper mTransitionHelper;
    private FinalizationController mFinalizationController;
    private boolean mIsReceiverRegistered;
    private boolean mRestoring;

    private final BroadcastReceiver mUserUnlockedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ACTION_USER_UNLOCKED.equals(intent.getAction())) {
                return;
            }
            int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, /* defaultValue= */ -1);
            UserManager userManager = getSystemService(UserManager.class);
            if (!userManager.isManagedProfile(userId)) {
                return;
            }
            tryFinalizeProvisioning();
            unregisterUserUnlockedReceiver();
        }
    };

    FinalizationActivityBase(TransitionHelper transitionHelper) {
        mTransitionHelper = transitionHelper;
    }

    @Override
    public final void onCreate(Bundle savedInstanceState) {
        // TODO(b/123987694): Investigate use of buffered i/o for provisioning params file
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .permitUnbufferedIo()
                .build());
        mTransitionHelper.applyContentScreenTransitions(this);
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mFinalizationController = createFinalizationController();

        mRestoring = savedInstanceState != null;
        if (mRestoring) {
            final Bundle controllerState = savedInstanceState.getBundle(CONTROLLER_STATE_KEY);
            if (controllerState != null) {
                mFinalizationController.restoreInstanceState(controllerState);
            }

            // If savedInstanceState is not null, we already executed the logic below, so don't do
            // it again now.  We likely just need to wait for a child activity to complete, so we
            // can execute an onActivityResult() callback before finishing this activity.
            return;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            getApplicationContext().startService(PROVISIONING_SERVICE_INTENT);
        } catch (BackgroundServiceStartNotAllowedException e) {
            ProvisionLogger.loge(e);
        }
        if (!mRestoring) {
            tryFinalizeProvisioning();
        }
    }

    protected TransitionHelper getTransitionHelper() {
        return mTransitionHelper;
    }

    private void tryFinalizeProvisioning() {
        // Register receiver first to avoid race condition when user becomes unlocked after
        // the user unlocked check. This will be unregistered if we don't need it
        mIsReceiverRegistered = false;
        registerUserUnlockedReceiver();
        mFinalizationController.provisioningFinalized();
        final int result = mFinalizationController.getProvisioningFinalizedResult();
        if (PROVISIONING_FINALIZED_RESULT_CHILD_ACTIVITY_LAUNCHED == result) {
            onFinalizationCompletedWithChildActivityLaunched();
        } else if (PROVISIONING_FINALIZED_RESULT_WAIT_FOR_WORK_PROFILE_AVAILABLE != result) {
            if (FinalizationController.PROVISIONING_FINALIZED_RESULT_SKIPPED != result
                    && FinalizationController.PROVISIONING_FINALIZED_RESULT_WORK_PROFILE_NOT_FOUND
                    != result) {
                mFinalizationController.commitFinalizedState();
            }
            setResult(RESULT_OK);
            getTransitionHelper().finishActivity(this);
        }

        if (PROVISIONING_FINALIZED_RESULT_WAIT_FOR_WORK_PROFILE_AVAILABLE != result) {
            unregisterUserUnlockedReceiver();
        }
    }

    private void registerUserUnlockedReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USER_UNLOCKED);
        registerReceiverAsUser(
                mUserUnlockedReceiver,
                UserHandle.ALL,
                filter,
                /* broadcastPermission= */ null,
                /* scheduler= */ null);
        mIsReceiverRegistered = true;
    }

    private void unregisterUserUnlockedReceiver() {
        if (!mIsReceiverRegistered) {
            return;
        }
        unregisterReceiver(mUserUnlockedReceiver);
        mIsReceiverRegistered = false;
    }

    @Override
    protected final void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        final Bundle controllerState = new Bundle();
        outState.putBundle(CONTROLLER_STATE_KEY, controllerState);
        mFinalizationController.saveInstanceState(controllerState);
    }

    @Override
    public final void onDestroy() {
        mFinalizationController.activityDestroyed(isFinishing());
        unregisterUserUnlockedReceiver();
        getApplicationContext().stopService(PROVISIONING_SERVICE_INTENT);
        super.onDestroy();
    }

    /**
     * Return the finalization controller instance that was constructed in {@link #onCreate}.
     */
    protected FinalizationController getFinalizationController() {
        return mFinalizationController;
    }

    /**
     * Construct the correct subtype of FinalizationController.
     */
    protected abstract FinalizationController createFinalizationController();

    /**
     * Hook for code that should run when the controller has launched a child activity.
     */
    protected abstract void onFinalizationCompletedWithChildActivityLaunched();
}
