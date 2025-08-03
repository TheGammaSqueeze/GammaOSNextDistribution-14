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

import static android.accounts.AccountManager.KEY_ACCOUNT_NAME;
import static android.accounts.AccountManager.KEY_ACCOUNT_TYPE;

import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.os.Bundle;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * An Account on the device.
 */
@Experimental
public final class AccountReference implements AutoCloseable {

    private final UserReference mUser;
    private final Account mAndroidAccount;
    private final Consumer<AccountReference> mRemoveFunction;

    /**
     * Create an {@link AccountReference} for the given account.
     *
     * <p>This account will not be removable using {@link #remove()}.
     */
    public static AccountReference of(UserReference user, Account androidAccount) {
        return new AccountReference(user, androidAccount);
    }

    /**
     * Create an {@link AccountReference} for the given account.
     *
     * <p>When {@link #remove()} is called, {@code removeFunction} will be called.
     */
    public static AccountReference of(
            UserReference user, Account androidAccount, Consumer<AccountReference> removeFunction) {
        return new AccountReference(user, androidAccount, removeFunction);
    }

    /**
     * Create an {@link AccountReference} for the given result.
     *
     * <p>This account will not be removable using {@link #remove()}.
     */
    public static AccountReference fromAddResult(UserReference user, Bundle result) {
        return new AccountReference(user, new Account(
                result.getString(KEY_ACCOUNT_NAME),
                result.getString(KEY_ACCOUNT_TYPE)
        ));
    }

    /**
     * Create an {@link AccountReference} for the given result.
     *
     * <p>When {@link #remove()} is called, {@code removeFunction} will be called.
     */
    public static AccountReference fromAddResult(
            UserReference user, Bundle result, Consumer<AccountReference> removeFunction) {
        return new AccountReference(user, new Account(
                result.getString(KEY_ACCOUNT_NAME),
                result.getString(KEY_ACCOUNT_TYPE)
        ), removeFunction);
    }

    AccountReference(
            UserReference user, Account androidAccount, Consumer<AccountReference> removeFunction) {
        mUser = user;
        mAndroidAccount = androidAccount;
        mRemoveFunction = removeFunction;
    }

    AccountReference(UserReference user, Account androidAccount) {
        this(user, androidAccount, /* removeFunction= */ null);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mUser, mAndroidAccount);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AccountReference)) {
            return false;
        }

        AccountReference other = (AccountReference) obj;

        return other.mUser.equals(mUser)
                && other.mAndroidAccount.equals(mAndroidAccount);
    }

    /**
     * Get the Android Account represented by this reference.
     */
    public Account account() {
        return mAndroidAccount;
    }

    /**
     * Get the name of the account.
     */
    public String name() {
        return mAndroidAccount.name;
    }

    /**
     * Get the type of the account.
     */
    public String type() {
        return mAndroidAccount.type;
    }

    /**
     * Get the user this account is on.
     */
    public UserReference user() {
        return mUser;
    }

    /**
     * True if Nene is able to remove this account.
     *
     * <p>This method returning false does not mean that nothing can remove this account, only that
     * Nene cannot.
     *
     * <p>In general, accounts created by test infrastructure will be removable.
     */
    public boolean canRemove() {
        return mRemoveFunction != null;
    }

    /**
     * True if this account has the given feature.
     */
    public boolean hasFeature(String feature) {
        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            return accountManager().hasFeatures(mAndroidAccount,
                    new String[]{feature}, /* callback= */ null, /* handler= */ null).getResult();
        } catch (OperationCanceledException | AuthenticatorException | IOException e) {
            throw new NeneException("Error checking feature " + feature + " for user " + mUser, e);
        }
    }

    /**
     * Remove the account.
     *
     * <p>If {@link #canRemove()} is false, this will throw an exception
     */
    public void remove() {
        if (!canRemove()) {
            throw new NeneException("Tried to remove an account which Nene cannot remove");
        }

        mRemoveFunction.accept(this);
    }

    /**
     * Remove the account.
     *
     * <p>This should only be used if {@link #canRemove()} is true
     */
    @Override
    public void close() {
        remove();
    }

    @Override
    public String toString() {
        return "AccountReference{"
                + "user=" + user()
                + ",account=" + account()
                + "}";
    }

    private android.accounts.AccountManager accountManager() {
        return TestApis.context().androidContextAsUser(mUser)
                .getSystemService(AccountManager.class);
    }
}
