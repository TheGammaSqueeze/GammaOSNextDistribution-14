/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.car.settings.accounts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.common.SettingsFragment;
import com.android.car.ui.toolbar.ToolbarController;
import com.android.settingslib.accounts.AuthenticatorHelper;

import java.util.Arrays;

/**
 * Shows account details, and delete account option.
 */
public class AccountDetailsFragment extends SettingsFragment implements
        AuthenticatorHelper.OnAccountsUpdateListener {
    public static final String EXTRA_ACCOUNT = "extra_account";
    public static final String EXTRA_ACCOUNT_LABEL = "extra_account_label";
    public static final String EXTRA_USER_INFO = "extra_user_info";

    private Account mAccount;
    private UserInfo mUserInfo;
    private AuthenticatorHelper mAuthenticatorHelper;

    /**
     * Creates a new AccountDetailsFragment.
     *
     * <p>Passes the provided account, label, and user info to the fragment via fragment arguments.
     */
    public static AccountDetailsFragment newInstance(Account account, CharSequence label,
            UserInfo userInfo) {
        AccountDetailsFragment
                accountDetailsFragment = new AccountDetailsFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(EXTRA_ACCOUNT, account);
        bundle.putCharSequence(EXTRA_ACCOUNT_LABEL, label);
        bundle.putParcelable(EXTRA_USER_INFO, userInfo);
        accountDetailsFragment.setArguments(bundle);
        return accountDetailsFragment;
    }

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.account_details_fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        mAccount = getArguments().getParcelable(EXTRA_ACCOUNT);
        mUserInfo = getArguments().getParcelable(EXTRA_USER_INFO);

        use(AccountDetailsPreferenceController.class, R.string.pk_account_details)
                .setAccount(mAccount);
        use(AccountDetailsPreferenceController.class, R.string.pk_account_details)
                .setUserHandle(mUserInfo.getUserHandle());

        use(AccountSyncPreferenceController.class, R.string.pk_account_sync)
                .setAccount(mAccount);
        use(AccountDetailsSettingController.class, R.string.pk_account_settings)
                .setAccount(mAccount);
        use(AccountSyncPreferenceController.class, R.string.pk_account_sync)
                .setUserHandle(mUserInfo.getUserHandle());
    }

    @Override
    protected void setupToolbar(@NonNull ToolbarController toolbar) {
        super.setupToolbar(toolbar);

        // Set the fragment's title
        toolbar.setTitle(getArguments().getCharSequence(EXTRA_ACCOUNT_LABEL));
    }

    @Override
    public void onStart() {
        super.onStart();

        mAuthenticatorHelper = new AuthenticatorHelper(getContext(), mUserInfo.getUserHandle(),
                this);
        mAuthenticatorHelper.listenToAccountUpdates();
    }

    @Override
    public void onStop() {
        super.onStop();
        mAuthenticatorHelper.stopListeningToAccountUpdates();
    }

    @Override
    public void onAccountsUpdate(UserHandle userHandle) {
        if (!accountExists()) {
            // The account was deleted. Pop back.
            goBack();
        }
    }

    /** Returns whether the account being shown by this fragment still exists. */
    @VisibleForTesting
    boolean accountExists() {
        if (mAccount == null) {
            return false;
        }

        Account[] accounts = AccountManager.get(getContext()).getAccountsByTypeAsUser(mAccount.type,
                mUserInfo.getUserHandle());

        return Arrays.asList(accounts).contains(mAccount);
    }
}
