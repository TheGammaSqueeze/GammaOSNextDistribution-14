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

package com.android.bedstead.nene.accounts;

import android.accounts.RemoteAccountManager;

import com.android.bedstead.nene.users.UserReference;

/**
 * Wrapper for {@link android.accounts.AccountManager} and
 * {@link android.accounts.RemoteAccountManager} to add helper functionality.
 */
public class AccountManager<E> {

    private final UserReference mUser;
    private final E mAccountManagerInstance;
    private final RemoteAccountManager mAccountManager;

    protected AccountManager(
            UserReference user, E accountManagerInstance, RemoteAccountManager accountManager) {
        mUser = user;
        mAccountManagerInstance = accountManagerInstance;
        mAccountManager = accountManager;
    }

    /**
     * Add an account.
     */
    public AccountBuilder addAccount() {
        return new AccountBuilder(this);
    }

    RemoteAccountManager accountManager() {
        return mAccountManager;
    }

    UserReference user() {
        return mUser;
    }
}
