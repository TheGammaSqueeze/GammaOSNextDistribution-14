/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.timezone.location.provider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.timezone.location.provider.core.Environment;
import com.android.timezone.location.provider.core.LocationListeningAccountant;

import java.time.Duration;
import java.util.Objects;

/**
 * An implementation of {@link LocationListeningAccountant} that delegates to another instance.
 * The underlying instance can be swapped in a thread-safe fashion using {@link
 * #replaceDelegate(LocationListeningAccountant)}, i.e. so the class holding the reference doesn't
 * need to know.
 */
final class DelegatingLocationListeningAccountant implements LocationListeningAccountant {

    @NonNull
    private LocationListeningAccountant mDelegate;

    DelegatingLocationListeningAccountant(@NonNull LocationListeningAccountant delegate) {
        mDelegate = Objects.requireNonNull(delegate);
    }

    /**
     * Replaces the delegate with {@code replacement}, carrying over as much of the current balance
     * as allowed by the replacement's configuration.
     */
    synchronized void replaceDelegate(@NonNull LocationListeningAccountant replacement) {
        Objects.requireNonNull(replacement);

        Duration balanceToTransfer = mDelegate.withdrawActiveListeningBalance();
        replacement.depositActiveListeningAmount(balanceToTransfer);
        mDelegate = replacement;
    }

    @Override
    public synchronized void depositActiveListeningAmount(@NonNull Duration amount) {
        mDelegate.depositActiveListeningAmount(amount);
    }

    @Override
    public synchronized void accrueActiveListeningBudget(@NonNull Duration timeInPassiveMode) {
        mDelegate.accrueActiveListeningBudget(timeInPassiveMode);
    }

    @NonNull
    @Override
    public synchronized ListeningInstruction getNextListeningInstruction(
            long elapsedRealtimeMillis,
            @Nullable Environment.LocationListeningResult lastLocationListeningResult) {
        return mDelegate.getNextListeningInstruction(
                elapsedRealtimeMillis, lastLocationListeningResult);
    }

    @NonNull
    @Override
    public synchronized Duration withdrawActiveListeningBalance() {
        return mDelegate.withdrawActiveListeningBalance();
    }

    @Override
    public synchronized String toString() {
        return "DelegatingLocationListeningAccountant{"
                + "mDelegate=" + mDelegate
                + '}';
    }
}
