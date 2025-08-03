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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.os.UserHandle;

public class AccountManagerCompat {
    private AccountManagerCompat(AccountManager accountManager) {
        mAccountManager = accountManager;
    }

    private final AccountManager mAccountManager;

    public static AccountManagerCompat get(Context context) {
        AccountManagerCompat accountManagerCompat = new AccountManagerCompat(
                AccountManager.get(context));
        return accountManagerCompat;
    }

    public Account[] getAccountsByTypeAsUser(String type, UserHandle userHandle) {
        return mAccountManager.getAccountsByTypeAsUser(type, userHandle);
    }

    public void addSharedAccountsFromParentUser(UserHandle parentUser, UserHandle user) {
        mAccountManager.addSharedAccountsFromParentUser(parentUser, user);
    }
}
