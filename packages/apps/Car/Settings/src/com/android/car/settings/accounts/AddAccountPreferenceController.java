/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.app.Activity.RESULT_OK;
import static android.os.UserManager.DISALLOW_MODIFY_ACCOUNTS;

import static com.android.car.settings.enterprise.EnterpriseUtils.hasUserRestrictionByUm;
import static com.android.car.settings.enterprise.EnterpriseUtils.isDeviceManaged;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;

import com.android.car.settings.admin.NewUserDisclaimerActivity;
import com.android.car.settings.common.ActivityResultCallback;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.profiles.ProfileHelper;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Business Logic for preference starts the add account flow.
 */
public class AddAccountPreferenceController extends PreferenceController<Preference>
        implements ActivityResultCallback {
    public static final int NEW_USER_DISCLAIMER_REQUEST = 1;
    private static final Logger LOG = new Logger(AddAccountPreferenceController.class);

    private String[] mAuthorities;
    private String[] mAccountTypes;

    public AddAccountPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    /** Sets the account authorities that are available. */
    public AddAccountPreferenceController setAuthorities(String[] authorities) {
        mAuthorities = authorities;
        return this;
    }

    /** Sets the account authorities that are available. */
    public AddAccountPreferenceController setAccountTypes(String[] accountTypes) {
        mAccountTypes = accountTypes;
        return this;
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected void onCreateInternal() {
        super.onCreateInternal();
        setClickableWhileDisabled(getPreference(), /* clickable= */ true, p -> {
            if (!getProfileHelper().isNewUserDisclaimerAcknolwedged(getContext())) {
                LOG.d("isNewUserDisclaimerAcknolwedged returns false, start activity");
                startNewUserDisclaimerActivityForResult();
                return;
            }
            getProfileHelper()
                    .runClickableWhileDisabled(getContext(), getFragmentController());
        });
    }

    @Override
    protected boolean handlePreferenceClicked(Preference preference) {
        AccountTypesHelper helper = getAccountTypesHelper();

        if (mAuthorities != null) {
            helper.setAuthorities(Arrays.asList(mAuthorities));
        }
        if (mAccountTypes != null) {
            helper.setAccountTypesFilter(
                    new HashSet<>(Arrays.asList(mAccountTypes)));
        }
        launchAddAccount();
        return true;
    }

    @Override
    public void processActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == NEW_USER_DISCLAIMER_REQUEST && resultCode == RESULT_OK) {
            LOG.d("New user disclaimer acknowledged, launching add account.");
            launchAddAccount();
        }  else {
            LOG.e("New user disclaimer failed with result " + resultCode);
        }
    }

    private void launchAddAccount() {
        Set<String> authorizedAccountTypes = getAccountTypesHelper().getAuthorizedAccountTypes();

        // Skip the choose account screen if there is only one account type and the device is not
        // managed by a device owner.
        // TODO (b/213894991) add check for profile owner when work profile is supported
        if (authorizedAccountTypes.size() == 1 && !isDeviceManaged(getContext())) {
            String accountType = authorizedAccountTypes.iterator().next();
            getContext().startActivity(
                    AddAccountActivity.createAddAccountActivityIntent(getContext(), accountType));
        } else {
            getFragmentController().launchFragment(new ChooseAccountFragment());
        }
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        if (isDeviceManaged(getContext())
                && !getProfileHelper().isNewUserDisclaimerAcknolwedged(getContext())) {
            return AVAILABLE_FOR_VIEWING;
        }
        if (getProfileHelper().canCurrentProcessModifyAccounts()) {
            return AVAILABLE;
        }
        if (getProfileHelper().isDemoOrGuest()
                || hasUserRestrictionByUm(getContext(), DISALLOW_MODIFY_ACCOUNTS)) {
            return DISABLED_FOR_PROFILE;
        }
        return AVAILABLE_FOR_VIEWING;
    }

    private void startNewUserDisclaimerActivityForResult() {
        getFragmentController().startActivityForResult(
                new Intent(getContext(), NewUserDisclaimerActivity.class),
                        NEW_USER_DISCLAIMER_REQUEST, /* callback= */ this);
    }

    @VisibleForTesting
    ProfileHelper getProfileHelper() {
        return ProfileHelper.getInstance(getContext());
    }

    @VisibleForTesting
    AccountTypesHelper getAccountTypesHelper() {
        return new AccountTypesHelper(getContext());
    }
}
