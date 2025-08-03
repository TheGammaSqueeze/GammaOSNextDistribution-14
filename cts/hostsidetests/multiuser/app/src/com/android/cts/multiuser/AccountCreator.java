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
package com.android.cts.multiuser;

import static com.google.common.truth.Truth.assertThat;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public class AccountCreator {
    private static final String TAG = "MultiuserAccountCreator";

    private static final int ACCOUNT_AUTHENTICATOR_TIMEOUT_MILLISECONDS = 180000; // 180 seconds

    private static final int ACCOUNT_AUTHENTICATOR_WAIT_TIME_MILLISECONDS = 5000; // 5 seconds

    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
    }

    @Test
    public void addMockAccountForCurrentUser() throws Exception {
        Log.i(TAG, "Running addMockAccountForCurrentUser");
        final AccountManager accountManager = mContext.getSystemService(AccountManager.class);

        Log.i(TAG, "Adding account");

        waitForAccountAuthenticator(MockAuthenticator.ACCOUNT_TYPE, accountManager);

        final AccountManagerFuture<Bundle> future = accountManager.addAccount(
                MockAuthenticator.ACCOUNT_TYPE, null, null, null, null, null, null);

        Log.i(TAG, "Waiting for account to be added");
        final Bundle result = future.getResult();

        Log.i(TAG, "Checking that adding account was successful");
        assertThat(result.getString(AccountManager.KEY_ACCOUNT_TYPE)).isEqualTo(
                MockAuthenticator.ACCOUNT_TYPE);
        assertThat(result.getString(AccountManager.KEY_ACCOUNT_NAME)).isEqualTo(
                MockAuthenticator.ACCOUNT_NAME);
        final Account[] accounts = accountManager.getAccounts();
        assertThat(accounts).hasLength(1);
        assertThat(accountManager.getAccountsByType(MockAuthenticator.ACCOUNT_TYPE)).hasLength(1);

        Log.i(TAG, "Successfully added account; all is good");
    }


    private void waitForAccountAuthenticator(String accountType, AccountManager am) {
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < ACCOUNT_AUTHENTICATOR_TIMEOUT_MILLISECONDS
                && !accountAuthenticatorExists(accountType, am)) {
            Log.w(TAG, "Account authenticator not found for accountType: " + accountType);
            sleep(ACCOUNT_AUTHENTICATOR_WAIT_TIME_MILLISECONDS);
        }

        Log.i(TAG, "Account authenticator found for accountType: " + accountType);
    }


    private boolean accountAuthenticatorExists(String accountType, AccountManager am) {

        Set<String> authenticatorTypes  = Arrays.stream(am.getAuthenticatorTypes())
                .map(authenticatorDescription -> authenticatorDescription.type)
                .collect(Collectors.toSet());

        return authenticatorTypes.contains(accountType);
    }

    private void sleep(int timeout) {
        try {
            Thread.sleep(timeout);
        } catch (InterruptedException ex) { }
    }
}
