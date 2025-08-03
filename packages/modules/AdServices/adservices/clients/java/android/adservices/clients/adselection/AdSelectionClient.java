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

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionManager;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.adselection.GetAdSelectionDataOutcome;
import android.adservices.adselection.GetAdSelectionDataRequest;
import android.adservices.adselection.PersistAdSelectionResultRequest;
import android.adservices.adselection.ReportEventRequest;
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.adselection.SetAppInstallAdvertisersRequest;
import android.adservices.adselection.UpdateAdCounterHistogramRequest;
import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.OutcomeReceiver;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Objects;
import java.util.concurrent.Executor;


/**
 * This is the Ad Selection Client which will be used in the cts tests.
 */
// TODO: This should be in JetPack code.
public class AdSelectionClient {

    private AdSelectionManager mAdSelectionManager;
    private Context mContext;
    private Executor mExecutor;

    private AdSelectionClient(
            @NonNull Context context,
            @NonNull Executor executor,
            @NonNull AdSelectionManager adSelectionManager) {
        mContext = context;
        mExecutor = executor;
        mAdSelectionManager = adSelectionManager;
    }

    /**
     * Invokes the {@code selectAds} method of {@link AdSelectionManager}, and returns a future with
     * {@link AdSelectionOutcome} if succeeds, or an {@link Exception} if fails.
     */
    @NonNull
    public ListenableFuture<AdSelectionOutcome> selectAds(
            @NonNull AdSelectionConfig adSelectionConfig) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mAdSelectionManager.selectAds(
                            adSelectionConfig,
                            mExecutor,
                            new OutcomeReceiver<AdSelectionOutcome, Exception>() {
                                @Override
                                public void onResult(@NonNull AdSelectionOutcome result) {
                                    completer.set(result);
                                }

                                @Override
                                public void onError(@NonNull Exception error) {
                                    completer.setException(error);
                                }
                            });
                    return "Ad Selection";
                });
    }

    /**
     * Invokes the {@code selectAds} method of {@link AdSelectionManager}, and returns a future with
     * {@link AdSelectionOutcome} if succeeds, or an {@link Exception} if fails.
     */
    @NonNull
    public ListenableFuture<AdSelectionOutcome> selectAds(
            @NonNull AdSelectionFromOutcomesConfig config) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mAdSelectionManager.selectAds(
                            config,
                            mExecutor,
                            new OutcomeReceiver<AdSelectionOutcome, Exception>() {
                                @Override
                                public void onResult(@NonNull AdSelectionOutcome result) {
                                    completer.set(result);
                                }

                                @Override
                                public void onError(@NonNull Exception error) {
                                    completer.setException(error);
                                }
                            });
                    return "Ad Selection from outcomes";
                });
    }

    /**
     * Invokes the {@code reportImpression} method of {@link AdSelectionManager}, and returns a Void
     * future
     */
    @NonNull
    public ListenableFuture<Void> reportImpression(
            @NonNull ReportImpressionRequest input) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mAdSelectionManager.reportImpression(
                            input,
                            mExecutor,
                            new OutcomeReceiver<Object, Exception>() {
                                @Override
                                public void onResult(@NonNull Object ignoredResult) {
                                    completer.set(null);
                                }

                                @Override
                                public void onError(@NonNull Exception error) {
                                    completer.setException(error);
                                }
                            });
                    return "reportImpression";
                });
    }

    /** Invokes {@link AdSelectionManager#reportEvent} and returns a Void future */
    @NonNull
    public ListenableFuture<Void> reportEvent(@NonNull ReportEventRequest request) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mAdSelectionManager.reportEvent(
                            request,
                            mExecutor,
                            new OutcomeReceiver<Object, Exception>() {
                                @Override
                                public void onResult(@NonNull Object ignoredResult) {
                                    completer.set(null);
                                }

                                @Override
                                public void onError(@NonNull Exception error) {
                                    completer.setException(error);
                                }
                            });
                    return "reportEvent";
                });
    }

    /**
     * Invokes {@link AdSelectionManager#updateAdCounterHistogram(UpdateAdCounterHistogramRequest,
     * Executor, OutcomeReceiver)} and returns a {@link ListenableFuture} for the resulting call.
     *
     * @hide
     */
    @NonNull
    public ListenableFuture<Void> updateAdCounterHistogram(
            @NonNull UpdateAdCounterHistogramRequest request) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mAdSelectionManager.updateAdCounterHistogram(
                            request,
                            mExecutor,
                            new OutcomeReceiver<Object, Exception>() {
                                @Override
                                public void onResult(@NonNull Object ignoredResult) {
                                    completer.set(null);
                                }

                                @Override
                                public void onError(@NonNull Exception error) {
                                    completer.setException(error);
                                }
                            });
                    return "updateAdCounterHistogram";
                });
    }

    /**
     * Invokes the {@code setAppInstallAdvertiser} method of {@link AdSelectionManager}, and returns
     * a Void future.
     */
    @NonNull
    public ListenableFuture<Void> setAppInstallAdvertisers(
            @NonNull SetAppInstallAdvertisersRequest request) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mAdSelectionManager.setAppInstallAdvertisers(
                            request,
                            mExecutor,
                            new OutcomeReceiver<Object, Exception>() {
                                @Override
                                public void onResult(@NonNull Object ignoredResult) {
                                    completer.set(null);
                                }

                                @Override
                                public void onError(@NonNull Exception error) {
                                    completer.setException(error);
                                }
                            });
                    return "setAppInstallAdvertisers";
                });
    }

    /**
     * Invokes the {@getAdSelectionData} method of {@link AdSelectionManager}, and returns a
     * GetAdSelectionDataOutcome future.
     */
    @NonNull
    @SuppressLint("MissingPermission")
    public ListenableFuture<GetAdSelectionDataOutcome> getAdSelectionData(
            @NonNull GetAdSelectionDataRequest request) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mAdSelectionManager.getAdSelectionData(
                            request,
                            mExecutor,
                            new OutcomeReceiver<GetAdSelectionDataOutcome, Exception>() {
                                @Override
                                public void onResult(@NonNull GetAdSelectionDataOutcome result) {
                                    completer.set(result);
                                }

                                @Override
                                public void onError(@NonNull Exception error) {
                                    completer.setException(error);
                                }
                            });
                    return "getAdSelectionData";
                });
    }

    /**
     * Invokes the {@persistAdSelectionResult} method of {@link AdSelectionManager}, and returns a
     * AdSelectionOutcome future.
     */
    @NonNull
    @SuppressLint("MissingPermission")
    public ListenableFuture<AdSelectionOutcome> persistAdSelectionResult(
            @NonNull PersistAdSelectionResultRequest request) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mAdSelectionManager.persistAdSelectionResult(
                            request,
                            mExecutor,
                            new OutcomeReceiver<AdSelectionOutcome, Exception>() {
                                @Override
                                public void onResult(@NonNull AdSelectionOutcome result) {
                                    completer.set(result);
                                }

                                @Override
                                public void onError(@NonNull Exception error) {
                                    completer.setException(error);
                                }
                            });
                    return "persistAdSelectionResult";
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
        public AdSelectionClient.Builder setContext(@NonNull Context context) {
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
        public AdSelectionClient.Builder setExecutor(@NonNull Executor executor) {
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
        public AdSelectionClient build() {
            Objects.requireNonNull(mContext);
            Objects.requireNonNull(mExecutor);

            return new AdSelectionClient(mContext, mExecutor, createAdSelectionManager());
        }

        private AdSelectionManager createAdSelectionManager() {
            if (mUseGetMethodToCreateManagerInstance) {
                return AdSelectionManager.get(mContext);
            }

            return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    ? mContext.getSystemService(AdSelectionManager.class)
                    : AdSelectionManager.get(mContext);
        }
    }
}
