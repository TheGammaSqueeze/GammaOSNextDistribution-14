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

package com.android.server.art.model;

import static com.android.server.art.ArtManagerLocal.AdjustCompilerFilterCallback;
import static com.android.server.art.ArtManagerLocal.BatchDexoptStartCallback;
import static com.android.server.art.ArtManagerLocal.DexoptDoneCallback;
import static com.android.server.art.ArtManagerLocal.ScheduleBackgroundDexoptJobCallback;

import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.internal.annotations.GuardedBy;
import com.android.server.art.ArtManagerLocal;

import com.google.auto.value.AutoValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * A class that stores the configurations set by the consumer of ART Service at runtime. This class
 * is thread-safe.
 *
 * @hide
 */
public class Config {
    /** @see ArtManagerLocal#setBatchDexoptStartCallback(Executor, BatchDexoptStartCallback) */
    @GuardedBy("this")
    @Nullable
    private Callback<BatchDexoptStartCallback, Void> mBatchDexoptStartCallback = null;

    /**
     * @see ArtManagerLocal#setScheduleBackgroundDexoptJobCallback(Executor,
     *         ScheduleBackgroundDexoptJobCallback)
     */
    @GuardedBy("this")
    @Nullable
    private Callback<ScheduleBackgroundDexoptJobCallback, Void>
            mScheduleBackgroundDexoptJobCallback = null;

    /**
     * @see ArtManagerLocal#addDexoptDoneCallback(Executor, DexoptDoneCallback)
     */
    @GuardedBy("this")
    @NonNull
    private LinkedHashMap<DexoptDoneCallback, Callback<DexoptDoneCallback, Boolean>>
            mDexoptDoneCallbacks = new LinkedHashMap<>();

    /**
     * @see ArtManagerLocal#setAdjustCompilerFilterCallback(Executor,
     *         AdjustCompilerFilterCallback)
     */
    @GuardedBy("this")
    @Nullable
    private Callback<AdjustCompilerFilterCallback, Void> mAdjustCompilerFilterCallback = null;

    public synchronized void setBatchDexoptStartCallback(
            @NonNull Executor executor, @NonNull BatchDexoptStartCallback callback) {
        mBatchDexoptStartCallback = Callback.create(callback, executor);
    }

    public synchronized void clearBatchDexoptStartCallback() {
        mBatchDexoptStartCallback = null;
    }

    @Nullable
    public synchronized Callback<BatchDexoptStartCallback, Void> getBatchDexoptStartCallback() {
        return mBatchDexoptStartCallback;
    }

    public synchronized void setScheduleBackgroundDexoptJobCallback(
            @NonNull Executor executor, @NonNull ScheduleBackgroundDexoptJobCallback callback) {
        mScheduleBackgroundDexoptJobCallback = Callback.create(callback, executor);
    }

    public synchronized void clearScheduleBackgroundDexoptJobCallback() {
        mScheduleBackgroundDexoptJobCallback = null;
    }

    @Nullable
    public synchronized Callback<ScheduleBackgroundDexoptJobCallback, Void>
    getScheduleBackgroundDexoptJobCallback() {
        return mScheduleBackgroundDexoptJobCallback;
    }

    public synchronized void addDexoptDoneCallback(boolean onlyIncludeUpdates,
            @NonNull Executor executor, @NonNull DexoptDoneCallback callback) {
        if (mDexoptDoneCallbacks.putIfAbsent(
                    callback, Callback.create(callback, executor, onlyIncludeUpdates))
                != null) {
            throw new IllegalStateException("callback already added");
        }
    }

    public synchronized void removeDexoptDoneCallback(@NonNull DexoptDoneCallback callback) {
        mDexoptDoneCallbacks.remove(callback);
    }

    @NonNull
    public synchronized List<Callback<DexoptDoneCallback, Boolean>> getDexoptDoneCallbacks() {
        return new ArrayList<>(mDexoptDoneCallbacks.values());
    }

    public synchronized void setAdjustCompilerFilterCallback(
            @NonNull Executor executor, @NonNull AdjustCompilerFilterCallback callback) {
        mAdjustCompilerFilterCallback = Callback.create(callback, executor);
    }

    public synchronized void clearAdjustCompilerFilterCallback() {
        mAdjustCompilerFilterCallback = null;
    }

    @Nullable
    public synchronized Callback<AdjustCompilerFilterCallback, Void>
    getAdjustCompilerFilterCallback() {
        return mAdjustCompilerFilterCallback;
    }

    @AutoValue
    public static abstract class Callback<CallbackType, ExtraType> {
        public abstract @NonNull CallbackType get();
        public abstract @NonNull Executor executor();
        public abstract @Nullable ExtraType extra();
        static <CallbackType, ExtraType> @NonNull Callback<CallbackType, ExtraType> create(
                @NonNull CallbackType callback, @NonNull Executor executor,
                @Nullable ExtraType extra) {
            return new AutoValue_Config_Callback<CallbackType, ExtraType>(
                    callback, executor, extra);
        }
        static <CallbackType> @NonNull Callback<CallbackType, Void> create(
                @NonNull CallbackType callback, @NonNull Executor executor) {
            return new AutoValue_Config_Callback<CallbackType, Void>(
                    callback, executor, null /* extra */);
        }
    }
}
