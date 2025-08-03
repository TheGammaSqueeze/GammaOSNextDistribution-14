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

package com.android.car.settings.system;

import android.Manifest;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;

/**
 * Controller which invokes the reboot service provided by PowerManager.
 *
 * @see PowerManager#reboot(String)
 * @see PowerManager#isRebootingUserspaceSupported()
 */
public class RestartSystemPreferenceController extends PreferenceController<Preference> {
    private static final Logger LOG = new Logger(RestartSystemPreferenceController.class);
    private static final String REBOOT_PERMISSIONS_TAG = Manifest.permission.REBOOT;
    private final PowerManager mPowerManager;
    private final boolean mIsRebootPermissionGranted;
    @VisibleForTesting
    final ConfirmationDialogFragment.ConfirmListener mRestartSystemConfirmListener;
    @VisibleForTesting
    final ConfirmationDialogFragment mDialogFragment;
    @VisibleForTesting
    static final String RESTART_SYSTEM_CONFIRM_DIALOG_TAG =
            "com.android.car.settings.system.RestartSystemConfirmDialog";

    public RestartSystemPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        this(context, preferenceKey, fragmentController, uxRestrictions,
                /* isRebootPermissionGranted*/ (ContextCompat.checkSelfPermission(context,
                        REBOOT_PERMISSIONS_TAG) == PackageManager.PERMISSION_GRANTED));
    }

    @VisibleForTesting
    RestartSystemPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions,
            boolean isRebootPermissionGranted) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mPowerManager = context.getSystemService(PowerManager.class);
        mRestartSystemConfirmListener = getRestartSystemConfirmationListener();
        mDialogFragment = getConfirmationDialogFragment();

        mIsRebootPermissionGranted = isRebootPermissionGranted;
    }

    private ConfirmationDialogFragment.ConfirmListener getRestartSystemConfirmationListener() {
        return new ConfirmationDialogFragment.ConfirmListener() {
            @Override
            public void onConfirm(@Nullable Bundle arguments) {
                if (isRebootEnabled()) {
                    LOG.d("Restarting infotainment system");
                    mPowerManager.reboot(null);
                } else {
                    LOG.e("Unable to access PowerManager system service or reboot permission is"
                            + "not granted in the current package");
                }
            }
        };
    }

    private ConfirmationDialogFragment getConfirmationDialogFragment() {
        return new ConfirmationDialogFragment.Builder(getContext())
                .setMessage(R.string.restart_infotainment_system_dialog_text)
                .setPositiveButton(R.string.continue_confirmation,
                        /* confirmListener= */ mRestartSystemConfirmListener)
                .setNegativeButton(android.R.string.cancel,
                        /* rejectListener= */ null)
                .build();
    }

    private boolean isRebootEnabled() {
        return (mPowerManager != null) && mIsRebootPermissionGranted;
    }

    @Override
    protected boolean handlePreferenceClicked(Preference preference) {
        getFragmentController().showDialog(mDialogFragment, RESTART_SYSTEM_CONFIRM_DIALOG_TAG);
        return true;
    }

    @Override
    protected void onCreateInternal() {
        ConfirmationDialogFragment.resetListeners(
                (ConfirmationDialogFragment) getFragmentController().findDialogByTag(
                        RESTART_SYSTEM_CONFIRM_DIALOG_TAG),
                mRestartSystemConfirmListener,
                /* rejectListener= */ null,
                /* neutralListener= */ null);
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        int availabilityStatus = super.getDefaultAvailabilityStatus();
        if (availabilityStatus == AVAILABLE && !isRebootEnabled()) {
            return UNSUPPORTED_ON_DEVICE;
        }
        return availabilityStatus;
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

}
