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

package android.adservices.clients.adselection;

import android.adservices.adselection.AdSelectionManager;
import android.adservices.adselection.AddAdSelectionFromOutcomesOverrideRequest;
import android.adservices.adselection.AddAdSelectionOverrideRequest;
import android.adservices.adselection.RemoveAdSelectionFromOutcomesOverrideRequest;
import android.adservices.adselection.RemoveAdSelectionOverrideRequest;
import android.adservices.adselection.TestAdSelectionManager;
import android.annotation.NonNull;
import android.content.Context;
import android.os.Build;
import android.os.OutcomeReceiver;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Objects;
import java.util.concurrent.Executor;

/** This is the Test Ad Selection Client which will be used in the cts tests. */
// TODO: This should be in JetPack code.
public class TestAdSelectionClient {
    private TestAdSelectionManager mTestAdSelectionManager;
    private Context mContext;
    private Executor mExecutor;

    private TestAdSelectionClient(
            @NonNull Context context,
            @NonNull Executor executor,
            @NonNull TestAdSelectionManager testAdSelectionManager) {
        mContext = context;
        mExecutor = executor;
        mTestAdSelectionManager = testAdSelectionManager;
    }

    /**
     * Invokes the {@code overrideAdSelectionConfigRemoteInfo} method of {@link AdSelectionManager},
     * and returns a Void future
     *
     * <p>This method is only available when Developer mode is enabled and the app is debuggable.
     *
     * @hide
     */
    @NonNull
    public ListenableFuture<Void> overrideAdSelectionConfigRemoteInfo(
            @NonNull AddAdSelectionOverrideRequest request) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mTestAdSelectionManager.overrideAdSelectionConfigRemoteInfo(
                            request,
                            mExecutor,
                            new OutcomeReceiver<Object, Exception>() {

                                @Override
                                public void onResult(Object ignoredResult) {
                                    completer.set(null);
                                }

                                @Override
                                public void onError(@NonNull Exception error) {
                                    completer.setException(error);
                                }
                            });
                    return "overrideAdSelectionConfigRemoteInfo";
                });
    }

    /**
     * Invokes the {@code removeAdSelectionConfigRemoteInfoOverride} method of {@link
     * AdSelectionManager}, and returns a Void future
     *
     * <p>This method is only available when Developer mode is enabled and the app is debuggable.
     *
     * @hide
     */
    @NonNull
    public ListenableFuture<Void> removeAdSelectionConfigRemoteInfoOverride(
            @NonNull RemoveAdSelectionOverrideRequest request) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mTestAdSelectionManager.removeAdSelectionConfigRemoteInfoOverride(
                            request,
                            mExecutor,
                            new OutcomeReceiver<Object, Exception>() {

                                @Override
                                public void onResult(Object ignoredResult) {
                                    completer.set(null);
                                }

                                @Override
                                public void onError(@NonNull Exception error) {
                                    completer.setException(error);
                                }
                            });
                    return "removeAdSelectionConfigRemoteInfoOverride";
                });
    }

    /**
     * Invokes the {@code removeAdSelectionConfigRemoteInfoOverride} method of {@link
     * AdSelectionManager}, and returns a Void future
     *
     * <p>This method is only available when Developer mode is enabled and the app is debuggable.
     *
     * @hide
     */
    @NonNull
    public ListenableFuture<Void> resetAllAdSelectionConfigRemoteOverrides() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mTestAdSelectionManager.resetAllAdSelectionConfigRemoteOverrides(
                            mExecutor,
                            new OutcomeReceiver<Object, Exception>() {

                                @Override
                                public void onResult(Object ignoredResult) {
                                    completer.set(null);
                                }

                                @Override
                                public void onError(@NonNull Exception error) {
                                    completer.setException(error);
                                }
                            });
                    return "resetAllAdSelectionConfigRemoteOverrides";
                });
    }

    /**
     * Invokes the {@code overrideAdSelectionFromOutcomesConfigRemoteInfo} method of {@link
     * AdSelectionManager}, and returns a Void future
     *
     * <p>This method is only available when Developer mode is enabled and the app is debuggable.
     *
     * @hide
     */
    @NonNull
    public ListenableFuture<Void> overrideAdSelectionFromOutcomesConfigRemoteInfo(
            @NonNull AddAdSelectionFromOutcomesOverrideRequest request) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mTestAdSelectionManager.overrideAdSelectionFromOutcomesConfigRemoteInfo(
                            request,
                            mExecutor,
                            new OutcomeReceiver<Object, Exception>() {

                                @Override
                                public void onResult(Object ignoredResult) {
                                    completer.set(null);
                                }

                                @Override
                                public void onError(@NonNull Exception error) {
                                    completer.setException(error);
                                }
                            });
                    return "overrideAdSelectionConfigRemoteInfo";
                });
    }

    /**
     * Invokes the {@code removeAdSelectionConfigRemoteInfoOverride} method of {@link
     * AdSelectionManager}, and returns a Void future
     *
     * <p>This method is only available when Developer mode is enabled and the app is debuggable.
     *
     * @hide
     */
    @NonNull
    public ListenableFuture<Void> removeAdSelectionFromOutcomesConfigRemoteInfoOverride(
            @NonNull RemoveAdSelectionFromOutcomesOverrideRequest request) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mTestAdSelectionManager.removeAdSelectionFromOutcomesConfigRemoteInfoOverride(
                            request,
                            mExecutor,
                            new OutcomeReceiver<Object, Exception>() {

                                @Override
                                public void onResult(Object ignoredResult) {
                                    completer.set(null);
                                }

                                @Override
                                public void onError(@NonNull Exception error) {
                                    completer.setException(error);
                                }
                            });
                    return "removeAdSelectionConfigRemoteInfoOverride";
                });
    }

    /**
     * Invokes the {@code removeAdSelectionConfigRemoteInfoOverride} method of {@link
     * AdSelectionManager}, and returns a Void future
     *
     * <p>This method is only available when Developer mode is enabled and the app is debuggable.
     *
     * @hide
     */
    @NonNull
    public ListenableFuture<Void> resetAllAdSelectionFromOutcomesConfigRemoteOverrides() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mTestAdSelectionManager.resetAllAdSelectionFromOutcomesConfigRemoteOverrides(
                            mExecutor,
                            new OutcomeReceiver<Object, Exception>() {

                                @Override
                                public void onResult(Object ignoredResult) {
                                    completer.set(null);
                                }

                                @Override
                                public void onError(@NonNull Exception error) {
                                    completer.setException(error);
                                }
                            });
                    return "resetAllAdSelectionConfigRemoteOverrides";
                });
    }

    /** Builder class. */
    public static final class Builder {
        private Context mContext;
        private Executor mExecutor;
        private boolean mUseGetMethodToCreateManagerInstance;

        /** Empty-arg constructor with an empty body for Builder */
        public Builder() {}

        /** Sets the context. */
        @NonNull
        public TestAdSelectionClient.Builder setContext(@NonNull Context context) {
            Objects.requireNonNull(context);

            mContext = context;
            return this;
        }

        /**
         * Sets the worker executor.
         *
         * @param executor the worker executor used to run heavy background tasks.
         */
        @NonNull
        public TestAdSelectionClient.Builder setExecutor(@NonNull Executor executor) {
            Objects.requireNonNull(executor);

            mExecutor = executor;
            return this;
        }

        /**
         * Sets whether to use the AdSelectionManager.get(context) method explicitly.
         *
         * @param value flag indicating whether to use the AdSelectionManager.get(context) method
         *     explicitly. Default is {@code false}.
         */
        @VisibleForTesting
        @NonNull
        public Builder setUseGetMethodToCreateManagerInstance(boolean value) {
            mUseGetMethodToCreateManagerInstance = value;
            return this;
        }

        /**
         * Builds the Ad Selection Client.
         *
         * @throws NullPointerException if {@code mContext} is null or if {@code mExecutor} is null
         */
        @NonNull
        public TestAdSelectionClient build() {
            Objects.requireNonNull(mContext);
            Objects.requireNonNull(mExecutor);

            TestAdSelectionManager manager = createAdSelectionManager().getTestAdSelectionManager();
            return new TestAdSelectionClient(mContext, mExecutor, manager);
        }

        @NonNull
        private AdSelectionManager createAdSelectionManager() {
            if (mUseGetMethodToCreateManagerInstance) {
                return AdSelectionManager.get(mContext);
            }

            // By default, use getSystemService for T+ and get(context) for S-.
            return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    ? mContext.getSystemService(AdSelectionManager.class)
                    : AdSelectionManager.get(mContext);
        }
    }
}
