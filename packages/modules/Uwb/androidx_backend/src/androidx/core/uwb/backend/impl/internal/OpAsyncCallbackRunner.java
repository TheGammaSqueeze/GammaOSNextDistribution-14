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

package androidx.core.uwb.backend.impl.internal;

import static androidx.core.uwb.backend.impl.internal.Utils.TAG;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.concurrent.futures.CallbackToFutureAdapter.Completer;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Execute an operation and wait for its completion.
 *
 * <p>Typical usage: Execute an operation that should trigger an asynchronous callback. When the
 * callback is invoked, inside the callback the opCompleter is set and unblocks the execution.
 *
 * @param <T> T is the type of the value that sets in operation's completion.
 */
public class OpAsyncCallbackRunner<T> {

    /** Default timeout value of an operation */
    private static final int DEFAULT_OPERATION_TIMEOUT_MILLIS = 3000;

    private int mOperationTimeoutMillis = DEFAULT_OPERATION_TIMEOUT_MILLIS;

    @Nullable private Completer<T> mOpCompleter;

    @Nullable private T mResult;

    private boolean mActive = false;

    /** Set the timeout value in Millis */
    public void setOperationTimeoutMillis(int timeoutMillis) {
        mOperationTimeoutMillis = timeoutMillis;
    }

    /** Completes the operation and set the result */
    public void complete(T result) {
        if (!mActive) {
            throw new IllegalStateException("Calling complete() without active operation.");
        }
        Completer<T> opCompleter = this.mOpCompleter;
        if (opCompleter != null) {
            opCompleter.set(result);
            this.mResult = result;
        }
    }

    /** Complete the operation if active, useful for unexpected callback. */
    public synchronized void completeIfActive(T result) {
        if (!mActive) {
            return;
        }
        Completer<T> opCompleter = this.mOpCompleter;
        if (opCompleter != null) {
            opCompleter.set(result);
            this.mResult = result;
        }
    }

    @Nullable
    public T getResult() {
        return mResult;
    }

    /**
     * Execute op in current thread and wait until the completer is set. Since this is a blocking
     * operation, make sure it's not running on main thread.
     */
    @WorkerThread
    public boolean execOperation(Runnable op, String opDescription) {
        mResult = null;
        if (mActive) {
            throw new IllegalStateException("Calling execOperation() while operation is running.");
        }
        mActive = true;
        ListenableFuture<T> opFuture =
                CallbackToFutureAdapter.getFuture(
                        completer -> {
                            mOpCompleter = completer;
                            op.run();
                            return "Async " + opDescription;
                        });
        try {
            mResult = opFuture.get(mOperationTimeoutMillis, MILLISECONDS);
            return mResult != null;
        } catch (TimeoutException e) {
            Log.w(TAG, String.format("Callback timeout in Op %s", opDescription), e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            Log.w(TAG, String.format("ExecutionException in Op %s", opDescription), e);
            return false;
        } finally {
            mOpCompleter = null;
            mActive = false;
        }
    }

    public boolean isActive() {
        return mActive;
    }
}
