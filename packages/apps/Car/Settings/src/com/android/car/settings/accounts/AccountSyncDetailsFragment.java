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

import android.accounts.Account;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.common.SettingsFragment;

/**
 * Shows details for syncing an account.
 */
public class AccountSyncDetailsFragment extends SettingsFragment {
    private static final String EXTRA_ACCOUNT = "extra_account";
    private static final String EXTRA_USER_HANDLE = "extra_user_handle";

    /**
     * Creates a new AccountSyncDetailsFragment.
     *
     * <p>Passes the provided account and user handle to the fragment via fragment arguments.
     */
    public static AccountSyncDetailsFragment newInstance(Account account, UserHandle userHandle) {
        AccountSyncDetailsFragment accountSyncDetailsFragment = new AccountSyncDetailsFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(EXTRA_ACCOUNT, account);
        bundle.putParcelable(EXTRA_USER_HANDLE, userHandle);
        accountSyncDetailsFragment.setArguments(bundle);
        return accountSyncDetailsFragment;
    }

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.account_sync_details_fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        Account account = getArguments().getParcelable(EXTRA_ACCOUNT);
        UserHandle userHandle = getArguments().getParcelable(EXTRA_USER_HANDLE);

        use(AccountDetailsWithSyncStatusPreferenceController.class,
                R.string.pk_account_details_with_sync)
                .setAccount(account);
        use(AccountDetailsWithSyncStatusPreferenceController.class,
                R.string.pk_account_details_with_sync)
                .setUserHandle(userHandle);

        use(AccountSyncDetailsPreferenceController.class, R.string.pk_account_sync_details)
                .setAccount(account);
        use(AccountSyncDetailsPreferenceController.class, R.string.pk_account_sync_details)
                .setUserHandle(userHandle);
    }
}
