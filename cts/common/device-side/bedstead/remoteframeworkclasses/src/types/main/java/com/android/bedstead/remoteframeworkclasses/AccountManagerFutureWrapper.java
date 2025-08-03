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

package com.android.bedstead.remoteframeworkclasses;

import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;

import com.google.android.enterprise.connectedapps.FutureWrapper;
import com.google.android.enterprise.connectedapps.Profile;
import com.google.android.enterprise.connectedapps.annotations.CustomFutureWrapper;
import com.google.android.enterprise.connectedapps.internal.Bundler;
import com.google.android.enterprise.connectedapps.internal.BundlerType;
import com.google.android.enterprise.connectedapps.internal.CrossProfileCallbackMultiMerger;
import com.google.android.enterprise.connectedapps.internal.FutureResultWriter;

import java.io.IOException;
import java.util.Map;

/**
 * Future wrapper for {@link AccountManagerFuture}.
 *
 * @param <V> Wrapped future result
 */
@CustomFutureWrapper(originalType = AccountManagerFuture.class)
public class AccountManagerFutureWrapper<V> extends FutureWrapper<V> {

    private final SimpleAccountManagerFuture<V> mFuture = new SimpleAccountManagerFuture<>();

    private AccountManagerFutureWrapper(Bundler bundler, BundlerType bundlerType) {
        super(bundler, bundlerType);
    }

    /** Create a {@link AccountManagerFutureWrapper}. */
    public static <V> AccountManagerFutureWrapper<V> create(
            Bundler bundler, BundlerType bundlerType) {
        return new AccountManagerFutureWrapper<>(bundler, bundlerType);
    }

    /** Write a result from an {@link AccountManagerFuture}. */
    public static <V> void writeFutureResult(
            AccountManagerFuture<V> future, FutureResultWriter<V> futureResultWriter) {
        try {
            futureResultWriter.onSuccess(future.getResult());
        } catch (OperationCanceledException | IOException | AuthenticatorException e) {
            futureResultWriter.onFailure(e);
        }
    }

    /** Group multiple results from {@link AccountManagerFuture}. */
    public static <E> AccountManagerFuture<Map<Profile, E>> groupResults(
            Map<Profile, AccountManagerFuture<E>> results) {
        SimpleAccountManagerFuture<Map<Profile, E>> m = new SimpleAccountManagerFuture<>();

        CrossProfileCallbackMultiMerger<E> merger =
                new CrossProfileCallbackMultiMerger<>(results.size(), m::setResult);
        for (Map.Entry<Profile, AccountManagerFuture<E>> result : results.entrySet()) {
            try {
                merger.onResult(result.getKey(), result.getValue().getResult());
            } catch (OperationCanceledException | IOException | AuthenticatorException e) {
                merger.missingResult(result.getKey());
            }
        }
        return m;
    }

    /** Get the wrapped future. */
    public AccountManagerFuture<V> getFuture() {
        return mFuture;
    }

    @Override
    public void onResult(V result) {
        mFuture.setResult(result);
    }

    @Override
    public void onException(Throwable throwable) {
        mFuture.setException(throwable);
    }
}
