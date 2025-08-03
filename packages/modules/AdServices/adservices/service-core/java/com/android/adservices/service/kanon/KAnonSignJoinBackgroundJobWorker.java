/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.kanon;

import android.annotation.RequiresApi;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.SingletonRunner;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;

import java.util.Objects;
import java.util.function.Supplier;

@RequiresApi(Build.VERSION_CODES.S)
public class KAnonSignJoinBackgroundJobWorker {
    public static final String JOB_DESCRIPTION = "FLEDGE KAnon Sign Join Background Job";
    private static final Object SINGLETON_LOCK = new Object();
    private static volatile KAnonSignJoinBackgroundJobWorker sKAnonSignJoinBackgroundJobWorker;

    private final KAnonSignJoinManager mKAnonSignJoinManager;
    private final Flags mFlags;
    private final SingletonRunner<Void> mSingletonRunner =
            new SingletonRunner<>(JOB_DESCRIPTION, this::doRun);

    /**
     * Gets an instance of a {@link KAnonSignJoinBackgroundJobWorker}. If an instance hasn't been
     * initialized, a new singleton will be created and returned.
     */
    public static KAnonSignJoinBackgroundJobWorker getInstance(@NonNull Context context) {
        Objects.requireNonNull(context);
        if (sKAnonSignJoinBackgroundJobWorker == null) {
            synchronized (SINGLETON_LOCK) {
                sKAnonSignJoinBackgroundJobWorker =
                        new KAnonSignJoinBackgroundJobWorker(
                                context,
                                FlagsFactory.getFlags(),
                                new KAnonSignJoinFactory(context).getKAnonSignJoinManager());
            }
        }
        return sKAnonSignJoinBackgroundJobWorker;
    }

    @VisibleForTesting
    public KAnonSignJoinBackgroundJobWorker(
            @NonNull Context context,
            @NonNull Flags flags,
            @NonNull KAnonSignJoinManager kAnonSignJoinManager) {
        Objects.requireNonNull(kAnonSignJoinManager);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(context);

        mKAnonSignJoinManager = kAnonSignJoinManager;
        mFlags = flags;
    }

    /**
     * Runs the k-anon sign job.
     *
     * @return A future to be used to check when the task has completed.
     */
    public FluentFuture<Void> runSignJoinBackgroundProcess() {
        return mSingletonRunner.runSingleInstance();
    }

    private FluentFuture<Void> doRun(@NonNull Supplier<Boolean> shouldStop) {
        if (shouldStop.get() || !mFlags.getFledgeKAnonBackgroundProcessEnabled()) {
            return FluentFuture.from(Futures.immediateVoidFuture());
        }
        return FluentFuture.from(Futures.immediateVoidFuture())
                .transform(
                        ignored -> {
                            mKAnonSignJoinManager.processMessagesFromDatabase(
                                    mFlags.getFledgeKAnonMessagesPerBackgroundProcess());
                            return null;
                        },
                        AdServicesExecutors.getLightWeightExecutor());
    }

    /** Requests that any ongoing work be stopped. */
    public void stopWork() {
        mSingletonRunner.stopWork();
    }
}
