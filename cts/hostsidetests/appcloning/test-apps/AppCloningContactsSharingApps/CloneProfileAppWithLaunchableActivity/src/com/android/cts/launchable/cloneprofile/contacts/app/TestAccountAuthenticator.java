/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.cts.launchable.cloneprofile.contacts.app;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.os.Bundle;


public class TestAccountAuthenticator extends AbstractAccountAuthenticator {
    private final Bundle mAccountBundle = new Bundle();
    private static final String TEST_ACCOUNT_NAME = "test@test.com";
    public static final String TEST_ACCOUNT_TYPE = "test.com";
    private static final String LABEL = "test_auth_token_label";
    private static final String TOKEN = "random_token_string";


    public TestAccountAuthenticator(Context context) {
        super(context);
        mAccountBundle.putString(AccountManager.KEY_ACCOUNT_NAME, TEST_ACCOUNT_NAME);
        mAccountBundle.putString(AccountManager.KEY_ACCOUNT_TYPE, TEST_ACCOUNT_TYPE);
        mAccountBundle.putString(AccountManager.KEY_AUTHTOKEN, TOKEN);
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
        return mAccountBundle;
    }

    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response, String accountType,
            String authTokenType, String[] requiredFeatures, Bundle options)
            throws NetworkErrorException {
        return mAccountBundle;
    }

    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account,
            Bundle options) throws NetworkErrorException {
        Bundle result = new Bundle();
        result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true);
        return result;
    }

    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account,
            String authTokenType, Bundle options) throws NetworkErrorException {
        return mAccountBundle;
    }

    @Override
    public String getAuthTokenLabel(String authTokenType) {
        return LABEL;
    }

    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account,
            String authTokenType, Bundle options) throws NetworkErrorException {
        return mAccountBundle;
    }

    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account,
            String[] features) throws NetworkErrorException {
        return new Bundle();
    }
}
