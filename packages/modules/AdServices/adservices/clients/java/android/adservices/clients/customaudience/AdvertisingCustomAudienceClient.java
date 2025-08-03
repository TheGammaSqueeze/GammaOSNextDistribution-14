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

package android.adservices.clients.customaudience;

import android.adservices.common.AdServicesOutcomeReceiver;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceManager;
import android.adservices.customaudience.FetchAndJoinCustomAudienceRequest;
import android.adservices.customaudience.JoinCustomAudienceRequest;
import android.adservices.customaudience.LeaveCustomAudienceRequest;
import android.adservices.customaudience.ScheduleCustomAudienceUpdateRequest;
import android.annotation.NonNull;
import android.content.Context;
import android.os.Build;
import android.os.OutcomeReceiver;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * AdvertisingCustomAudienceClient. Currently, this is for test purpose only, not exposing to the
 * client.
 */
// TODO: This should be in JetPack code.
public class AdvertisingCustomAudienceClient {
    private final CustomAudienceManager mCustomAudienceManager;

    private final Context mContext;
    private final Executor mExecutor;

    private AdvertisingCustomAudienceClient(
            @NonNull Context context,
            @NonNull Executor executor,
            @NonNull CustomAudienceManager customAudienceManager) {
        mContext = context;
        mExecutor = executor;
        mCustomAudienceManager = customAudienceManager;
    }

    /** Gets the context. */
    @NonNull
    public Context getContext() {
        return mContext;
    }

    /** Gets the worker executor. */
    @NonNull
    public Executor getExecutor() {
        return mExecutor;
    }

    /** Join custom audience. */
    @NonNull
    public ListenableFuture<Void> joinCustomAudience(CustomAudience customAudience) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    JoinCustomAudienceRequest request =
                            new JoinCustomAudienceRequest.Builder()
                                    .setCustomAudience(customAudience)
                                    .build();
                    mCustomAudienceManager.joinCustomAudience(
                            request,
                            mExecutor,
                            new OutcomeReceiver<Object, Exception>() {
                                @Override
                                public void onResult(Object ignoredResult) {
                                    completer.set(null);
                                }

                                @Override
                                public void onError(Exception error) {
                                    completer.setException(error);
                                }
                            });
                    // This value is used only for debug purposes: it will be used in toString()
                    // of returned future or error cases.
                    return "joinCustomAudience";
                });
    }

    /** Fetch and Join custom audience. */
    @NonNull
    public ListenableFuture<Void> fetchAndJoinCustomAudience(
            FetchAndJoinCustomAudienceRequest request) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mCustomAudienceManager.fetchAndJoinCustomAudience(
                            request,
                            mExecutor,
                            new OutcomeReceiver<Object, Exception>() {
                                @Override
                                public void onResult(Object ignoredResult) {
                                    completer.set(null);
                                }

                                @Override
                                public void onError(Exception error) {
                                    completer.setException(error);
                                }
                            });
                    // This value is used only for debug purposes: it will be used in toString()
                    // of returned future or error cases.
                    return "fetchAndjoinCustomAudience";
                });
    }

    /** Schedules a delayed update for Custom Audience */
    @NonNull
    public ListenableFuture<Void> scheduleCustomAudienceUpdate(
            ScheduleCustomAudienceUpdateRequest request) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mCustomAudienceManager.scheduleCustomAudienceUpdate(
                            request,
                            mExecutor,
                            new AdServicesOutcomeReceiver<Object, Exception>() {
                                @Override
                                public void onResult(Object ignoredResult) {
                                    completer.set(null);
                                }

                                @Override
                                public void onError(Exception error) {
                                    completer.setException(error);
                                }
                            });
                    // This value is used only for debug purposes: it will be used in toString()
                    // of returned future or error cases.
                    return "scheduleCustomAudienceUpdate";
                });
    }

    /** Leave custom audience. */
    @NonNull
    public ListenableFuture<Void> leaveCustomAudience(
            @NonNull AdTechIdentifier buyer, @NonNull String name) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    LeaveCustomAudienceRequest request =
                            new LeaveCustomAudienceRequest.Builder()
                                    .setBuyer(buyer)
                                    .setName(name)
                                    .build();
                    mCustomAudienceManager.leaveCustomAudience(
                            request,
                            mExecutor,
                            new OutcomeReceiver<Object, Exception>() {
                                @Override
                                public void onResult(Object ignoredResult) {
                                    completer.set(null);
                                }

                                @Override
                                public void onError(Exception error) {
                                    completer.setException(error);
                                }
                            });
                    // This value is used only for debug purposes: it will be used in toString()
                    // of returned future or error cases.
                    return "leaveCustomAudience";
                });
    }

    /** Builder class. */
    public static final class Builder {
        private Context mContext;
        private Executor mExecutor;
        private boolean mUseGetMethodToCreateManagerInstance;

        /** Empty-arg constructor with an empty body for Builder */
        public Builder() {
        }

        /** Sets the context. */
        @NonNull
        public Builder setContext(@NonNull Context context) {
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
        public Builder setExecutor(@NonNull Executor executor) {
            Objects.requireNonNull(executor);
            mExecutor = executor;
            return this;
        }

        /**
         * Sets whether to use the CustomAudienceManager.get(context) method explicitly.
         *
         * @param value flag indicating whether to use the CustomAudienceManager.get(context) method
         *     explicitly. Default is {@code false}.
         */
        @VisibleForTesting
        @NonNull
        public Builder setUseGetMethodToCreateManagerInstance(boolean value) {
            mUseGetMethodToCreateManagerInstance = value;
            return this;
        }

        /** Builds a {@link AdvertisingCustomAudienceClient} instance */
        @NonNull
        public AdvertisingCustomAudienceClient build() {
            Objects.requireNonNull(mContext);
            Objects.requireNonNull(mExecutor);

            CustomAudienceManager customAudienceManager = createCustomAudienceManager();
            return new AdvertisingCustomAudienceClient(mContext, mExecutor, customAudienceManager);
        }

        @NonNull
        private CustomAudienceManager createCustomAudienceManager() {
            if (mUseGetMethodToCreateManagerInstance) {
                return CustomAudienceManager.get(mContext);
            }

            // By default, use getSystemService for T+ and get(context) for S-.
            return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    ? mContext.getSystemService(CustomAudienceManager.class)
                    : CustomAudienceManager.get(mContext);
        }
    }
}
