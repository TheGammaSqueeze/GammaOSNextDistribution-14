/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.settings.accounts;

import static com.android.tv.settings.accounts.AccountsUtil.ACCOUNTS_FRAGMENT_DEFAULT;

import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.fragment.app.FragmentActivity;

import com.android.tv.settings.R;
import com.android.tv.settings.overlay.FlavorUtils;

import java.io.IOException;


public class AddAccountWithTypeActivity extends FragmentActivity {

    // Must match com.google.android.gms.common.AccountPicker.
    public static final String EXTRA_ALLOWABLE_ACCOUNT_TYPES_STRING_ARRAY = "allowableAccountTypes";

    private static final String TAG = "AddAccountWithType";

    private static final int REQUEST_CHOOSE_ACCOUNT_TYPE = 0;
    private static final int REQUEST_ADD_ACCOUNT = 1;
    private static final String CHOOSE_ACCOUNT_TYPE_ACTION =
            "com.google.android.gms.common.account.CHOOSE_ACCOUNT_TYPE";

    private final AccountManagerCallback<Bundle> mCallback = new AccountManagerCallback<Bundle>() {
        @Override
        public void run(AccountManagerFuture<Bundle> future) {
            try {
                Intent addAccountIntent = future.getResult()
                        .getParcelable(AccountManager.KEY_INTENT);
                if (addAccountIntent == null) {
                    Log.e(TAG, "Failed to retrieve add account intent from authenticator");
                    AlertDialog dialog = new AlertDialog.Builder(
                            AddAccountWithTypeActivity.this)
                            .setMessage(R.string.add_account_intent_not_available_dialog_message)
                            .setNegativeButton(android.R.string.ok,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            setResultAndFinish(Activity.RESULT_CANCELED);
                                        }
                                    })
                            .create();
                    dialog.show();
                } else {
                    startActivityForResult(new Intent(addAccountIntent), REQUEST_ADD_ACCOUNT);
                }
            } catch (IOException|AuthenticatorException|OperationCanceledException e) {
                Log.e(TAG, "Failed to get add account intent: ", e);
                AlertDialog dialog = new AlertDialog.Builder(AddAccountWithTypeActivity.this)
                        .setMessage(R.string.add_account_failed_dialog_message)
                        .setNegativeButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        setResultAndFinish(Activity.RESULT_CANCELED);
                                    }
                                })
                        .create();
                dialog.show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Show AccountsFragment if DO is active and this activity was started through
        // ADD_ACCOUNT_SETTINGS intent. This ensures that organization info. is displayed and
        // restrictions are enforced, if present.
        if (FlavorUtils.getFeatureFactory(this).getEnterprisePrivacyFeatureProvider(
                this).hasDeviceOwner() && Settings.ACTION_ADD_ACCOUNT.equals(
                getIntent().getAction())) {
            startAccountsActivity();
            setResultAndFinish(Activity.RESULT_CANCELED);
        } else {
            String accountType = getIntent().getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);
            if (accountType != null) {
                startAddAccount(accountType);
            } else {
                String[] allowedTypes = getIntent().getStringArrayExtra(
                        EXTRA_ALLOWABLE_ACCOUNT_TYPES_STRING_ARRAY);
                if (allowedTypes == null || allowedTypes.length == 0) {
                    allowedTypes = getIntent().getStringArrayExtra(Settings.EXTRA_ACCOUNT_TYPES);
                }
                startAccountTypePicker(allowedTypes);
            }
        }
    }

    @SuppressWarnings("MissingSuperCall") // TODO: fix me
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User selected an account type, so kick off the add account flow for that.
        if (requestCode == REQUEST_CHOOSE_ACCOUNT_TYPE && resultCode == Activity.RESULT_OK) {
            String accountType = data.getExtras().getString(AccountManager.KEY_ACCOUNT_TYPE);
            startAddAccount(accountType);
        } else {
            setResultAndFinish(resultCode);
        }
    }

    private void startAccountTypePicker(String[] allowedTypes) {
        Intent i = new Intent(CHOOSE_ACCOUNT_TYPE_ACTION);
        i.putExtra(EXTRA_ALLOWABLE_ACCOUNT_TYPES_STRING_ARRAY, allowedTypes);
        startActivityForResult(i, REQUEST_CHOOSE_ACCOUNT_TYPE);
    }

    private void startAddAccount(String accountType) {
        AccountManager.get(this).addAccount(
                accountType,
                null, /* authTokenType */
                null, /* requiredFeatures */
                null, /* accountOptions */
                null, mCallback, null);
    }

    private void setResultAndFinish(int resultCode) {
        setResult(resultCode);
        finish();
    }

    private void startAccountsActivity() {
        Intent intent = new Intent(this, AccountsActivity.class);
        intent.putExtra(AccountsActivity.EXTRA_FRAGMENT_TYPE, ACCOUNTS_FRAGMENT_DEFAULT);
        startActivity(intent);
    }
}
