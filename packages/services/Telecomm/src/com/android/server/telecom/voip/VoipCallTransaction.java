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

package com.android.server.telecom.voip;

import android.os.Handler;
import android.os.HandlerThread;
import android.telecom.Log;

import com.android.server.telecom.LoggedHandlerExecutor;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.flags.Flags;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class VoipCallTransaction {
    //TODO: add log events
    protected static final long TIMEOUT_LIMIT = 5000L;

    /**
     * Tracks stats about a transaction for logging purposes.
     */
    public static class Stats {
        // the logging visible timestamp for ease of debugging
        public final LocalDateTime addedTimeStamp;
        // the time in nS that the transaction was first created
        private final long mCreatedTimeNs;
        // the time that the transaction was started.
        private long mStartedTimeNs = -1L;
        // the time that the transaction was finished.
        private long mFinishedTimeNs = -1L;
        // If finished, did this transaction finish because it timed out?
        private boolean mIsTimedOut = false;
        private VoipCallTransactionResult  mTransactionResult = null;

        public Stats() {
            addedTimeStamp = LocalDateTime.now();
            mCreatedTimeNs = System.nanoTime();
        }

        /**
         * Mark the transaction as started and record the time.
         */
        public void markStarted() {
            if (mStartedTimeNs > -1) return;
            mStartedTimeNs = System.nanoTime();
        }

        /**
         * Mark the transaction as completed and record the time.
         */
        public void markComplete(boolean isTimedOut, VoipCallTransactionResult result) {
            if (mFinishedTimeNs > -1) return;
            mFinishedTimeNs = System.nanoTime();
            mIsTimedOut = isTimedOut;
            mTransactionResult = result;
        }

        /**
         * @return Time in mS since the transaction was created.
         */
        public long measureTimeSinceCreatedMs() {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - mCreatedTimeNs);
        }

        /**
         * @return Time in mS between when transaction was created and when it was marked as
         * started. Returns -1 if the transaction was not started yet.
         */
        public long measureCreatedToStartedMs() {
            return mStartedTimeNs > 0 ?
                    TimeUnit.NANOSECONDS.toMillis(mStartedTimeNs - mCreatedTimeNs) : -1;
        }

        /**
         * @return Time in mS since the transaction was marked started to the TransactionManager.
         * Returns -1 if the transaction hasn't been started yet.
         */
        public long measureTimeSinceStartedMs() {
            return mStartedTimeNs > 0 ?
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - mStartedTimeNs) : -1;
        }

        /**
         * @return Time in mS between when the transaction was marked as started and when it was
         * marked as completed. Returns -1 if the transaction hasn't started or finished yet.
         */
        public long measureStartedToCompletedMs() {
            return (mStartedTimeNs > 0 && mFinishedTimeNs > 0) ?
                    TimeUnit.NANOSECONDS.toMillis(mFinishedTimeNs - mStartedTimeNs) : -1;

        }

        /**
         * @return true if this transaction completed due to timing out, false if the transaction
         * hasn't completed yet or it completed and did not time out.
         */
        public boolean isTimedOut() {
            return mIsTimedOut;
        }

        /**
         * @return the result if the transaction completed, null if it timed out or hasn't completed
         * yet.
         */
        public VoipCallTransactionResult getTransactionResult() {
            return mTransactionResult;
        }
    }

    protected final AtomicBoolean mCompleted = new AtomicBoolean(false);
    protected final String mTransactionName = this.getClass().getSimpleName();
    private HandlerThread mHandlerThread;
    protected Handler mHandler;
    protected TransactionManager.TransactionCompleteListener mCompleteListener;
    protected List<VoipCallTransaction> mSubTransactions;
    protected TelecomSystem.SyncRoot mLock;
    protected final Stats mStats;

    public VoipCallTransaction(
            List<VoipCallTransaction> subTransactions, TelecomSystem.SyncRoot lock) {
        mSubTransactions = subTransactions;
        mHandlerThread = new HandlerThread(this.toString());
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mLock = lock;
        mStats = Flags.enableCallSequencing() ? new Stats() : null;
    }

    public VoipCallTransaction(TelecomSystem.SyncRoot lock) {
        this(null /** mSubTransactions */, lock);
    }

    public void start() {
        if (mStats != null) mStats.markStarted();
        // post timeout work
        CompletableFuture<Void> future = new CompletableFuture<>();
        mHandler.postDelayed(() -> future.complete(null), TIMEOUT_LIMIT);
        future.thenApplyAsync((x) -> {
            if (mCompleted.getAndSet(true)) {
                return null;
            }
            if (mCompleteListener != null) {
                mCompleteListener.onTransactionTimeout(mTransactionName);
            }
            timeout();
            return null;
        }, new LoggedHandlerExecutor(mHandler, mTransactionName + "@" + hashCode()
                + ".s", mLock));

        scheduleTransaction();
    }

    protected void scheduleTransaction() {
        LoggedHandlerExecutor executor = new LoggedHandlerExecutor(mHandler,
                mTransactionName + "@" + hashCode() + ".pT", mLock);
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        future.thenComposeAsync(this::processTransaction, executor)
                .thenApplyAsync((Function<VoipCallTransactionResult, Void>) result -> {
                    mCompleted.set(true);
                    if (mCompleteListener != null) {
                        mCompleteListener.onTransactionCompleted(result, mTransactionName);
                    }
                    finish(result);
                    return null;
                    }, executor)
                .exceptionallyAsync((throwable -> {
                    Log.e(this, throwable, "Error while executing transaction.");
                    return null;
                }), executor);
    }

    public CompletionStage<VoipCallTransactionResult> processTransaction(Void v) {
        return CompletableFuture.completedFuture(
                new VoipCallTransactionResult(VoipCallTransactionResult.RESULT_SUCCEED, null));
    }

    public void setCompleteListener(TransactionManager.TransactionCompleteListener listener) {
        mCompleteListener = listener;
    }

    public void timeout() {
        finish(true, null);
    }

    public void finish(VoipCallTransactionResult result) {
        finish(false, result);
    }

    public void finish(boolean isTimedOut, VoipCallTransactionResult result) {
        if (mStats != null) mStats.markComplete(isTimedOut, result);
        // finish all sub transactions
        if (mSubTransactions != null && !mSubTransactions.isEmpty()) {
            mSubTransactions.forEach( t -> t.finish(isTimedOut, result));
        }
        mHandlerThread.quit();
    }

    /**
     * @return Stats related to this transaction if stats are enabled, null otherwise.
     */
    public Stats getStats() {
        return mStats;
    }
}
