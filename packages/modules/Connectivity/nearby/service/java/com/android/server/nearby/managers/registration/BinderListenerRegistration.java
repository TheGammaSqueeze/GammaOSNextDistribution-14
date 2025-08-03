/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.nearby.managers.registration;

import static com.android.server.nearby.NearbyService.TAG;

import android.annotation.Nullable;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.server.nearby.managers.ListenerMultiplexer;

import java.util.NoSuchElementException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A listener registration object which holds data associated with the listener, such as an optional
 * request, and an executor responsible for listener invocations. Key is the IBinder.
 *
 * @param <TListener> listener for the callback
 */
public abstract class BinderListenerRegistration<TListener> implements IBinder.DeathRecipient {

    private final AtomicBoolean mRemoved = new AtomicBoolean(false);
    private final Executor mExecutor;
    private final Object mListenerLock = new Object();
    @Nullable
    TListener mListener;
    @Nullable
    private final IBinder mKey;

    public BinderListenerRegistration(IBinder key, Executor executor, TListener listener) {
        this.mKey = key;
        this.mExecutor = executor;
        this.mListener = listener;
    }

    /**
     * Must be implemented to return the
     * {@link com.android.server.nearby.managers.ListenerMultiplexer} this registration is
     * registered
     * with. Often this is easiest to accomplish by defining registration subclasses as non-static
     * inner classes of the multiplexer they are to be used with.
     */
    public abstract ListenerMultiplexer<TListener, ?
            extends BinderListenerRegistration<TListener>, ?> getOwner();

    public final IBinder getBinder() {
        return mKey;
    }

    public final Executor getExecutor() {
        return mExecutor;
    }

    /**
     * Called when the registration is put in the Multiplexer.
     */
    public void onRegister() {
        try {
            getBinder().linkToDeath(this, 0);
        } catch (RemoteException e) {
            remove();
        }
    }

    /**
     * Called when the registration is removed in the Multiplexer.
     */
    public void onUnregister() {
        this.mListener = null;
        try {
            getBinder().unlinkToDeath(this, 0);
        } catch (NoSuchElementException e) {
            Log.w(TAG, "failed to unregister binder death listener", e);
        }
    }

    /**
     * Removes this registration. All pending listener invocations will fail.
     *
     * <p>Does nothing if invoked before {@link #onRegister()} or after {@link #onUnregister()}.
     */
    public final void remove() {
        IBinder key = mKey;
        if (key != null && !mRemoved.getAndSet(true)) {
            getOwner().removeRegistration(key);
        }
    }

    @Override
    public void binderDied() {
        remove();
    }

    /**
     * May be overridden by subclasses to handle listener operation failures. The default behavior
     * is
     * to further propagate any exceptions. Will always be invoked on the executor thread.
     */
    protected void onOperationFailure(Exception exception) {
        throw new AssertionError(exception);
    }

    /**
     * Executes the given listener operation on the registration executor, invoking {@link
     * #onOperationFailure(Exception)} in case the listener operation fails. If the registration is
     * removed prior to the operation running, the operation is considered canceled. If a null
     * operation is supplied, nothing happens.
     */
    public final void executeOperation(@Nullable ListenerOperation<TListener> operation) {
        if (operation == null) {
            return;
        }

        synchronized (mListenerLock) {
            if (mListener == null) {
                return;
            }

            AtomicBoolean complete = new AtomicBoolean(false);
            mExecutor.execute(() -> {
                TListener listener;
                synchronized (mListenerLock) {
                    listener = mListener;
                }

                Exception failure = null;
                if (listener != null) {
                    try {
                        operation.operate(listener);
                    } catch (Exception e) {
                        if (e instanceof RuntimeException) {
                            throw (RuntimeException) e;
                        } else {
                            failure = e;
                        }
                    }
                }

                operation.onComplete(failure == null);
                complete.set(true);

                if (failure != null) {
                    onOperationFailure(failure);
                }
            });
            operation.onScheduled(complete.get());
        }
    }

    /**
     * An listener operation to perform.
     *
     * @param <ListenerT> listener type
     */
    public interface ListenerOperation<ListenerT> {

        /**
         * Invoked after the operation has been scheduled for execution. The {@code complete}
         * argument
         * will be true if {@link #onComplete(boolean)} was invoked prior to this callback (such as
         * if
         * using a direct executor), or false if {@link #onComplete(boolean)} will be invoked after
         * this
         * callback. This method is always invoked on the calling thread.
         */
        default void onScheduled(boolean complete) {
        }

        /**
         * Invoked to perform an operation on the given listener. This method is always invoked on
         * the
         * executor thread. If this method throws a checked exception, the operation will fail and
         * result in {@link #onOperationFailure(Exception)} being invoked. If this method throws an
         * unchecked exception, this propagates normally and should result in a crash.
         */
        void operate(ListenerT listener) throws Exception;

        /**
         * Invoked after the operation is complete. The {@code success} argument will be true if
         * the
         * operation completed without throwing any exceptions, and false otherwise (such as if the
         * operation was canceled prior to executing, or if it threw an exception). This invocation
         * may
         * happen either before or after (but never during) the invocation of {@link
         * #onScheduled(boolean)}. This method is always invoked on the executor thread.
         */
        default void onComplete(boolean success) {
        }
    }
}
