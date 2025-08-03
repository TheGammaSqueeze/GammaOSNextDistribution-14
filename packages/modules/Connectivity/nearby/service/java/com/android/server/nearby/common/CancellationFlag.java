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

package com.android.server.nearby.common;

import android.util.ArraySet;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A cancellation flag to mark an operation has been cancelled and should be cleaned up as soon as
 * possible.
 */
public class CancellationFlag {

    private final Set<OnCancelListener> mListeners = new ArraySet<>();
    private final AtomicBoolean mIsCancelled = new AtomicBoolean();

    public CancellationFlag() {
        this(false);
    }

    public CancellationFlag(boolean isCancelled) {
        this.mIsCancelled.set(isCancelled);
    }

    /** Set the flag as cancelled. */
    public void cancel() {
        if (mIsCancelled.getAndSet(true)) {
            // Someone already cancelled. Return immediately.
            return;
        }

        // Don't invoke OnCancelListener#onCancel inside the synchronization block, as it makes
        // deadlocks more likely.
        Set<OnCancelListener> clonedListeners;
        synchronized (this) {
            clonedListeners = new ArraySet<>(mListeners);
        }
        for (OnCancelListener listener : clonedListeners) {
            listener.onCancel();
        }
    }

    /** Returns {@code true} if the flag has been set to cancelled. */
    public synchronized boolean isCancelled() {
        return mIsCancelled.get();
    }

    /** Returns the flag as an {@link AtomicBoolean} object. */
    public synchronized AtomicBoolean asAtomicBoolean() {
        return mIsCancelled;
    }

    /** Registers a {@link OnCancelListener} to listen to cancel() event. */
    public synchronized void registerOnCancelListener(OnCancelListener listener) {
        mListeners.add(listener);
    }

    /**
     * Unregisters a {@link OnCancelListener} that was previously registed through {@link
     * #registerOnCancelListener(OnCancelListener)}.
     */
    public synchronized void unregisterOnCancelListener(OnCancelListener listener) {
        mListeners.remove(listener);
    }

    /** Listens to {@link CancellationFlag#cancel()}. */
    public interface OnCancelListener {
        /**
         * When CancellationFlag is canceled.
         */
        void onCancel();
    }
}
