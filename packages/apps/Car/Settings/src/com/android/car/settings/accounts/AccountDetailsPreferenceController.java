/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.settings.accounts;

import static android.os.UserManager.DISALLOW_MODIFY_ACCOUNTS;

import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;
import static com.android.car.settings.enterprise.EnterpriseUtils.hasUserRestrictionByDpm;
import static com.android.car.settings.enterprise.EnterpriseUtils.hasUserRestrictionByUm;

import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.VisibleForTesting;

import com.android.car.settings.R;
import com.android.car.settings.common.ActivityResultCallback;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.ErrorDialog;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.enterprise.EnterpriseUtils;
import com.android.car.settings.profiles.ProfileHelper;

import java.io.IOException;

/**
 * Controller for the preference that shows the details of an account. It also handles a secondary
 * button for account removal.
 */
public class AccountDetailsPreferenceController extends AccountDetailsBasePreferenceController
        implements ActivityResultCallback {
    private static final Logger LOG = new Logger(AccountDetailsPreferenceController.class);
    private static final int REMOVE_ACCOUNT_REQUEST = 101;

    private AccountManagerCallback<Bundle> mCallback =
            future -> {
                // If already out of this screen, don't proceed.
                if (!isStarted()) {
                    return;
                }

                boolean done = true;
                boolean success = false;
                try {
                    Bundle result = future.getResult();
                    Intent removeIntent = result.getParcelable(AccountManager.KEY_INTENT);
                    if (removeIntent != null) {
                        done = false;
                        getFragmentController().startActivityForResult(new Intent(removeIntent),
                                REMOVE_ACCOUNT_REQUEST, this);
                    } else {
                        success = future.getResult().getBoolean(AccountManager.KEY_BOOLEAN_RESULT);
                    }
                } catch (OperationCanceledException | IOException | AuthenticatorException e) {
                    LOG.v("removeAccount error: " + e);
                }
                if (done) {
                    if (!success) {
                        showErrorDialog();
                    } else {
                        getFragmentController().goBack();
                    }
                }
            };

    private ConfirmationDialogFragment.ConfirmListener mConfirmListener = arguments -> {
        AccountManager.get(getContext()).removeAccountAsUser(getAccount(), /* activity= */ null,
                mCallback, null, getUserHandle());
        ConfirmationDialogFragment dialog =
                (ConfirmationDialogFragment) getFragmentController().findDialogByTag(
                        ConfirmationDialogFragment.TAG);
        if (dialog != null) {
            dialog.dismiss();
        }
    };

    public AccountDetailsPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    @CallSuper
    protected void onCreateInternal() {
        super.onCreateInternal();
        ConfirmationDialogFragment dialog =
                (ConfirmationDialogFragment) getFragmentController().findDialogByTag(
                        ConfirmationDialogFragment.TAG);
        ConfirmationDialogFragment.resetListeners(
                dialog,
                mConfirmListener,
                /* rejectListener= */ null,
                /* neutralListener= */ null);

        getPreference().setSecondaryActionVisible(
                getSecondaryActionAvailabilityStatus() == AVAILABLE
                        || getSecondaryActionAvailabilityStatus() == AVAILABLE_FOR_VIEWING);
        getPreference().setSecondaryActionEnabled(
                getSecondaryActionAvailabilityStatus() != DISABLED_FOR_PROFILE);

        getPreference().setOnSecondaryActionClickListener(this::onRemoveAccountClicked);
    }

    @Override
    public void processActivityResult(int requestCode, int resultCode, Intent data) {
        if (!isStarted()) {
            return;
        }
        if (requestCode == REMOVE_ACCOUNT_REQUEST) {
            // Activity result code may not adequately reflect the account removal status, so
            // KEY_BOOLEAN_RESULT is used here instead. If the intent does not have this value
            // included, a no-op will be performed and the account update listener in
            // {@link AccountDetailsFragment} will still handle the back navigation upon removal.
            if (data != null && data.hasExtra(AccountManager.KEY_BOOLEAN_RESULT)) {
                boolean success = data.getBooleanExtra(AccountManager.KEY_BOOLEAN_RESULT, false);
                if (success) {
                    getFragmentController().goBack();
                } else {
                    showErrorDialog();
                }
            }
        }
    }

    private int getSecondaryActionAvailabilityStatus() {
        ProfileHelper profileHelper = getProfileHelper();
        if (profileHelper.canCurrentProcessModifyAccounts()) {
            return AVAILABLE;
        }
        if (profileHelper.isDemoOrGuest()
                || hasUserRestrictionByUm(getContext(), DISALLOW_MODIFY_ACCOUNTS)) {
            return DISABLED_FOR_PROFILE;
        }
        return AVAILABLE_FOR_VIEWING;
    }

    private void onRemoveAccountClicked() {
        if (hasUserRestrictionByDpm(getContext(), DISALLOW_MODIFY_ACCOUNTS)) {
            showActionDisabledByAdminDialog();
        }
        ConfirmationDialogFragment dialog =
                new ConfirmationDialogFragment.Builder(getContext())
                        .setTitle(R.string.really_remove_account_title)
                        .setMessage(R.string.really_remove_account_message)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.remove_account_title, mConfirmListener)
                        .build();

        getFragmentController().showDialog(dialog, ConfirmationDialogFragment.TAG);
    }

    private void showErrorDialog() {
        getFragmentController().showDialog(
                ErrorDialog.newInstance(R.string.remove_account_error_title), /* tag= */ null);
    }

    private void showActionDisabledByAdminDialog() {
        getFragmentController().showDialog(
                EnterpriseUtils.getActionDisabledByAdminDialog(getContext(),
                        DISALLOW_MODIFY_ACCOUNTS),
                DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG);
    }

    @VisibleForTesting
    ProfileHelper getProfileHelper() {
        return ProfileHelper.getInstance(getContext());
    }
}
