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

package com.android.bedstead.testapp;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * An account authenticator which can be configured by tests.
 */
public final class TestAppAccountAuthenticator extends AbstractAccountAuthenticator {
    private static TestAppAccountAuthenticator sMockAuthenticator = null;
    private static final String ACCOUNT_NAME
            = "com.android.bedstead.testapp.AccountManagementApp.account.name";
    private static final String AUTH_TOKEN = "mockAuthToken";
    private static final String AUTH_TOKEN_LABEL = "mockAuthTokenLabel";
    private static final String ACCOUNT_PASSWORD = "password";

    public static synchronized TestAppAccountAuthenticator getAuthenticator(Context context) {
        if (null == sMockAuthenticator) {
            sMockAuthenticator = new TestAppAccountAuthenticator(context);
        }
        return sMockAuthenticator;
    }

    private final Context mContext;

    private TestAppAccountAuthenticator(Context context) {
        super(context);
        mContext = context;
    }

    private Bundle createResultBundle(String accountType) {
        return createResultBundle(accountType, ACCOUNT_NAME);
    }

    private Bundle createResultBundle(String accountType, String name) {
        Bundle result = new Bundle();
        result.putString(AccountManager.KEY_ACCOUNT_NAME, name);
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, accountType);
        result.putString(AccountManager.KEY_AUTHTOKEN, AUTH_TOKEN);
        return result;
    }

    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response, String accountType,
            String authTokenType, String[] requiredFeatures, Bundle options)
            throws NetworkErrorException {

        String name = options.getString("name", ACCOUNT_NAME);
        String password = options.getString("password", ACCOUNT_PASSWORD);
        ArrayList<String> features = options.getStringArrayList("features");
        if (features == null) {
            features = new ArrayList<>();
        }

        Account account = new Account(name, accountType);
        AccountManager accountManager = mContext.getSystemService(AccountManager.class);
        accountManager.addAccountExplicitly(account, password, new Bundle());

        accountManager.setUserData(account, "features", String.join(",", features));

        return createResultBundle(accountType, name);
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
        return createResultBundle(accountType);
    }

    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account,
            String authTokenType, Bundle options) throws NetworkErrorException {
        AccountManager accountManager = mContext.getSystemService(AccountManager.class);
        if (options.containsKey("features")) {
            accountManager.setUserData(account, "features",
                    String.join(",", options.getStringArrayList("features")));
        }

        return createResultBundle(/* accountType= */ null);
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
        return createResultBundle(/* accountType= */ null);
    }

    @Override
    public String getAuthTokenLabel(String authTokenType) {
        return AUTH_TOKEN_LABEL;
    }

    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account,
            String[] features) throws NetworkErrorException {
        boolean hasFeatures;
        AccountManager accountManager = mContext.getSystemService(AccountManager.class);
        if (accountManager.getUserData(account, "features") == null) {
            hasFeatures = false;
        } else {
            hasFeatures = Arrays.asList(accountManager.getUserData(account, "features")
                    .split(",")).containsAll(new HashSet<>(Arrays.asList(features)));
        }

        Bundle result = new Bundle();
        result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, hasFeatures);
        return result;
    }
}

