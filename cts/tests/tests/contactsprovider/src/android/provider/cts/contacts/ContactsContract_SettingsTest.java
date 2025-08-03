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

package android.provider.cts.contacts;

import static com.google.common.truth.Truth.assertThat;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.provider.ContactsContract.Settings;
import android.provider.ContactsContract.SimAccount;
import android.provider.ContactsContract.SimContacts;
import android.provider.cts.contacts.account.StaticAccountAuthenticator;
import android.test.AndroidTestCase;

import androidx.test.filters.MediumTest;

import com.android.compatibility.common.util.SystemUtil;

@MediumTest
public class ContactsContract_SettingsTest extends AndroidTestCase {
    // Using unique account name and type because these tests may break or be broken by
    // other tests running. No other tests should use the following accounts.
    private static final Account ACCT_1 = new Account("test for default account1",
            StaticAccountAuthenticator.TYPE);
    private static final Account ACCT_2 = new Account("test for default account2",
            StaticAccountAuthenticator.TYPE);

    private static final String SIM_ACCT_NAME = "sim account name for default account test";
    private static final String SIM_ACCT_TYPE = "sim account type for default account test";
    private static final int SIM_SLOT_0 = 0;

    private ContentResolver mResolver;
    private AccountManager mAccountManager;
    private Account mInitialDefaultAccount;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mResolver = getContext().getContentResolver();
        mAccountManager = AccountManager.get(getContext());

        mAccountManager.addAccountExplicitly(ACCT_1, null, null);
        mAccountManager.addAccountExplicitly(ACCT_2, null, null);
        SystemUtil.runWithShellPermissionIdentity(() -> {
            SimContacts.addSimAccount(mResolver, SIM_ACCT_NAME, SIM_ACCT_TYPE, SIM_SLOT_0,
                    SimAccount.ADN_EF_TYPE);
        });

        mInitialDefaultAccount = Settings.getDefaultAccount(mResolver);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mAccountManager.removeAccount(ACCT_1, null, null);
        mAccountManager.removeAccount(ACCT_2, null, null);
        SystemUtil.runWithShellPermissionIdentity(() -> {
            SimContacts.removeSimAccounts(mResolver, SIM_SLOT_0);
        });

        SystemUtil.runWithShellPermissionIdentity(() -> {
            Settings.setDefaultAccount(mResolver, mInitialDefaultAccount);
        });

    }

    /**
     * Default account set by
     * {@link Settings#setDefaultAccount(ContentResolver, Account)} should be
     * returned by {@link Settings#getDefaultAccount(ContentResolver)}
     */
    public void testSetDefaultAccount_returnedByGetDefaultAccount() {
        // Set default account to a system account and call get to check.
        SystemUtil.runWithShellPermissionIdentity(() -> {
            Settings.setDefaultAccount(mResolver, ACCT_1);
        });

        Account defaultAccount = Settings.getDefaultAccount(mResolver);
        assertThat(defaultAccount.name).isEqualTo("test for default account1");
        assertThat(defaultAccount.type).isEqualTo(StaticAccountAuthenticator.TYPE);

        // Update default account to system account and call get to check.
        SystemUtil.runWithShellPermissionIdentity(() -> {
            Settings.setDefaultAccount(mResolver, ACCT_2);
        });

        defaultAccount = Settings.getDefaultAccount(mResolver);
        assertThat(defaultAccount.name).isEqualTo("test for default account2");
        assertThat(defaultAccount.type).isEqualTo(StaticAccountAuthenticator.TYPE);

        // Update default account to NULL and check.
        SystemUtil.runWithShellPermissionIdentity(() -> {
            Settings.setDefaultAccount(mResolver, null);
        });

        defaultAccount = Settings.getDefaultAccount(mResolver);
        assertThat(defaultAccount).isNull();

        // Update default account to sim account and check.
        SystemUtil.runWithShellPermissionIdentity(() -> {
            Settings.setDefaultAccount(mResolver, new Account(SIM_ACCT_NAME, SIM_ACCT_TYPE));
        });

        defaultAccount = Settings.getDefaultAccount(mResolver);
        assertThat(defaultAccount.name).isEqualTo(SIM_ACCT_NAME);
        assertThat(defaultAccount.type).isEqualTo(SIM_ACCT_TYPE);
    }

    public void testSetDefaultAccount_invalidAccount() {
        // Setting an invalid account will throw exception.
        try {
            SystemUtil.runWithShellPermissionIdentity(() -> {
                Settings.setDefaultAccount(mResolver, new Account("a", "b"));
            });
            fail();
        } catch (RuntimeException expected) {
        }

        Account defaultAccount = Settings.getDefaultAccount(mResolver);
        assertThat(defaultAccount).isEqualTo(mInitialDefaultAccount);
    }
}

