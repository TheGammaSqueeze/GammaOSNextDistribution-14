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

import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.os.Bundle;

import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.utils.Poll;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Class used set properties for adding an account.
 */
public final class AccountBuilder {

    private final AccountManager<?> mAccountManager;
    private String mType;
    private String mName = UUID.randomUUID().toString();
    private Set<String> mFeatures = new HashSet<>();
    private Consumer<AccountReference> mRemoveFunction;

    AccountBuilder(AccountManager<?> accountManager) {
        mAccountManager = accountManager;
        // TODO: Remove the removeFunction and just always use the accountManager
        mRemoveFunction = (account) -> {
            try {
                mAccountManager.accountManager().removeAccount(
                        account.account(),
                        /* activity= */ null,
                        /* callback= */ null,
                        /* handler= */ null
                ).getResult();
            } catch (AuthenticatorException | IOException | OperationCanceledException e) {
                throw new NeneException("Error removing account " + account, e);
            }
        };
    }

    /**
     * Set the type of the account.
     */
    public AccountBuilder type(String type) {
        mType = type;
        return this;
    }

    /**
     * Set the function to be called when a test tries to remove this account.
     */
    public AccountBuilder removeFunction(Consumer<AccountReference> removeFunction) {
        mRemoveFunction = removeFunction;
        return this;
    }

    /**
     * Set the name of the account. Defaults to a random name.
     */
    public AccountBuilder name(String name) {
        mName = name;
        return this;
    }

    /**
     * Set the features of the account. Defaults to none.
     */
    public AccountBuilder features(Set<String> features) {
        mFeatures = new HashSet<>(features);
        return this;
    }

    /**
     * Add a feature to the account.
     */
    public AccountBuilder addFeature(String feature) {
        mFeatures.add(feature);
        return this;
    }

    /**
     * Add features to the account.
     */
    public AccountBuilder addFeatures(String... feature) {
        mFeatures.addAll(new HashSet<>(Arrays.asList(feature)));
        return this;
    }

    /**
     * Add this account.
     */
    public AccountReference add() {
        return AccountReference.fromAddResult(
                mAccountManager.user(), addAccount(), mRemoveFunction);
    }

    /**
     * Blocks until an account of {@code type} is added.
     */
    // TODO(b/199077745): Remove poll once AccountManager race condition is fixed
    private Bundle addAccount() {
        return Poll.forValue("created account bundle", this::addAccountOnce)
                .toNotBeNull()
                .errorOnFail()
                .await();
    }

    private Bundle addAccountOnce() throws Exception {
        Bundle options = new Bundle();
        options.putString("name", mName);
        options.putStringArrayList("features", new ArrayList<>(mFeatures));
        return mAccountManager.accountManager().addAccount(
                mType,
                /* authTokenType= */ null,
                /* requiredFeatures= */ null,
                /* addAccountOptions= */ options,
                /* activity= */ null,
                /* callback= */ null,
                /* handler= */ null).getResult();
    }
}
