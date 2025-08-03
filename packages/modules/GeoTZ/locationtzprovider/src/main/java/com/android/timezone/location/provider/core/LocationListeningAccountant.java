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

package com.android.timezone.location.provider.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.timezone.location.provider.core.Environment.LocationListeningResult;
import com.android.timezone.location.provider.core.OfflineLocationTimeZoneDelegate.ListenModeEnum;

import java.time.Duration;
import java.util.Objects;

/**
 * A class that keeps track of the amount of active listening the provider is permitted to do and
 * issues instructions for the {@link OfflineLocationTimeZoneDelegate} to follow.
 *
 * <p>This logic has been extracted from {@link OfflineLocationTimeZoneDelegate} to enable
 * independent unit testing of the logic.
 *
 * <p>The goal of this class is to ensure that power usage stays below an upper bound. Passive
 * location listening and periods in doze are treated as "free", but active location listening is
 * treated as expensive and must be limited. The <em>actual</em> expense of active listening is
 * dependent on factors that are hard to account for, so it is treated consistently as "worst case".
 *
 * <p>The accountant stores a "balance" of the amount of active listening it is allowed to do. The
 * accountant ensures the balance stays between zero and an upper bound (cap). In order to accrue
 * active listening time, time must be spent <em>not</em> active listening.
 *
 * <p>The balance is reduced by an amount when the {@link OfflineLocationTimeZoneDelegate} calls
 * {@link #getNextListeningInstruction(long, LocationListeningResult)} and is given an instruction
 * to perform active listening for that amount of time.
 *
 * <p>The active listening balance can be increased directly via {@link
 * #depositActiveListeningAmount(Duration)} (e.g. to return previously allocated but unused
 * active listening time), or indirectly via {@link #accrueActiveListeningBudget(Duration)}, which
 * is called to record time elapsed while <em>not</em> active listening.
 */
public interface LocationListeningAccountant {

    /**
     * Deposit an amount of active listening. Used when budget previously allocated with
     * {@link #getNextListeningInstruction(long, LocationListeningResult)} is not all used. The
     * balance after this operation will not exceed the cap.
     */
    void depositActiveListeningAmount(@NonNull Duration amount);

    /**
     * Accrue an amount of active listening by reporting the amount of time spent in passive
     * listening mode. The balance after this operation will not exceed the cap.
     */
    void accrueActiveListeningBudget(@NonNull Duration timeInPassiveMode);

    /** An instruction for the {@link OfflineLocationTimeZoneDelegate} to follow. */
    final class ListeningInstruction {

        /** The mode to listen in. */
        @ListenModeEnum
        public final int listenMode;

        /** How long to listen for. */
        @NonNull
        public final Duration duration;

        public ListeningInstruction(@ListenModeEnum int listenMode, @NonNull Duration duration) {
            this.listenMode = listenMode;
            this.duration = Objects.requireNonNull(duration);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ListeningInstruction that = (ListeningInstruction) o;
            return listenMode == that.listenMode
                    && duration.equals(that.duration);
        }

        @Override
        public int hashCode() {
            return Objects.hash(listenMode, duration);
        }

        @Override
        public String toString() {
            return "ListeningInstruction{"
                    + "listenMode=" + listenMode
                    + ", duration=" + duration
                    + '}';
        }
    }

    /** Returns the next listening instruction. */
    @NonNull
    ListeningInstruction getNextListeningInstruction(long elapsedRealtimeMillis,
            @Nullable LocationListeningResult lastLocationListeningResult);

    /**
     * Withdraws the current active listening balance, leaving it at zero. Used when transferring
     * budget from one accountant to another.
     */
    @NonNull
    Duration withdrawActiveListeningBalance();
}
