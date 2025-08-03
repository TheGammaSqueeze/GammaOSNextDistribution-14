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

package android.adservices.clients.signals;

import android.adservices.signals.ProtectedSignalsManager;
import android.adservices.signals.UpdateSignalsRequest;
import android.annotation.NonNull;
import android.content.Context;
import android.os.Build;
import android.os.OutcomeReceiver;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Objects;
import java.util.concurrent.Executor;

/** ProtectedSignalsClient. Currently, this is for test purpose only, not exposing to the client. */
// TODO(b/320225623): This should be in JetPack code.
public class ProtectedSignalsClient {
    private final ProtectedSignalsManager mProtectedSignalsManager;

    private final Context mContext;
    private final Executor mExecutor;

    private ProtectedSignalsClient(
            @NonNull Context context,
            @NonNull Executor executor,
            @NonNull ProtectedSignalsManager protectedSignalsManager) {
        mContext = context;
        mExecutor = executor;
        mProtectedSignalsManager = protectedSignalsManager;
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

    /** Update signals. */
    @NonNull
    public ListenableFuture<Void> updateSignals(UpdateSignalsRequest updateSignalsRequest) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mProtectedSignalsManager.updateSignals(
                            updateSignalsRequest,
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
                    return "updateSignals";
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
         * Sets whether to use the ProtectedSignalsManager.get(context) method explicitly.
         *
         * @param value flag indicating whether to use the ProtectedSignalsManager.get(context)
         *     method explicitly. Default is {@code false}.
         */
        @VisibleForTesting
        @NonNull
        public Builder setUseGetMethodToCreateManagerInstance(boolean value) {
            mUseGetMethodToCreateManagerInstance = value;
            return this;
        }

        /** Builds a {@link ProtectedSignalsClient} instance */
        @NonNull
        public ProtectedSignalsClient build() {
            Objects.requireNonNull(mContext);
            Objects.requireNonNull(mExecutor);

            ProtectedSignalsManager protectedSignalsManager = createProtectedSignalsManager();
            return new ProtectedSignalsClient(mContext, mExecutor, protectedSignalsManager);
        }

        @NonNull
        private ProtectedSignalsManager createProtectedSignalsManager() {
            if (mUseGetMethodToCreateManagerInstance) {
                return ProtectedSignalsManager.get(mContext);
            }

            // By default, use getSystemService for T+ and get(context) for S-.
            return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    ? mContext.getSystemService(ProtectedSignalsManager.class)
                    : ProtectedSignalsManager.get(mContext);
        }
    }
}
