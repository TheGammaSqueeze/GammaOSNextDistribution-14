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

import static com.android.timezone.location.provider.core.OfflineLocationTimeZoneDelegate.LOCATION_LISTEN_MODE_ACTIVE;
import static com.android.timezone.location.provider.core.OfflineLocationTimeZoneDelegate.LOCATION_LISTEN_MODE_PASSIVE;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.timezone.location.provider.core.Environment.LocationListeningResult;
import com.android.timezone.location.provider.core.LocationListeningAccountant;

import java.time.Duration;
import java.util.Objects;

/**
 * A real implementation of {@link LocationListeningAccountant}.
 *
 * <p> A newly created instance starts with an active listening balance of zero. An instance holds
 * the current active listening balance in memory (i.e. it is cleared if the device reboots or if
 * the process is stopped).
 */
final class RealLocationListeningAccountant implements LocationListeningAccountant {

    /**
     * An amount added to passive listening durations when accruing active time to ensure enough
     * active time will be accrued even if there is some measurement error.
     */
    private static final Duration ACCRUAL_INCREMENT = Duration.ofMinutes(1);

    @NonNull private final Duration mMinPassiveListeningDuration;
    @NonNull private final Duration mMinActiveListeningDuration;
    @NonNull private final Duration mMaxActiveListeningDuration;
    @NonNull private final Duration mMaxActiveListeningBalance;
    @NonNull private final Duration mLocationNotKnownAgeThreshold;
    @NonNull private final Duration mLocationKnownAgeThreshold;
    private final long mPassiveToActiveRatio;

    /**
     * The amount of time we are allowed to spend active listening.
     */
    @GuardedBy("this")
    private long mActiveListeningBalanceMillis;

    /**
     * Creates a new accountant with the various constants it uses.
     *
     * @param minPassiveListeningDuration the minimum time to spend in passive listening
     * @param maxActiveListeningBalance the cap on the amount of active listening that can be
     *        accrued
     * @param minActiveListeningDuration the shortest active listening time allowed
     * @param maxActiveListeningDuration the maximum time to stay active listening in one go
     * @param locationNotKnownAgeThreshold the age before a "location not known" result is
     *        considered too stale to use
     * @param locationKnownAgeThreshold the age before a "location known" result's location is
     *        considered too stale to use
     * @param passiveToActiveRatio the amount of passive listening before a unit of active listening
     *        is accrued
     */
    RealLocationListeningAccountant(
            @NonNull Duration minPassiveListeningDuration,
            @NonNull Duration maxActiveListeningBalance,
            @NonNull Duration minActiveListeningDuration,
            @NonNull Duration maxActiveListeningDuration,
            @NonNull Duration locationNotKnownAgeThreshold,
            @NonNull Duration locationKnownAgeThreshold,
            long passiveToActiveRatio) {
        mMinPassiveListeningDuration = Objects.requireNonNull(minPassiveListeningDuration);
        checkPositiveAndNotZero(mMinPassiveListeningDuration);

        mMaxActiveListeningBalance = Objects.requireNonNull(maxActiveListeningBalance);
        checkPositiveAndNotZero(mMaxActiveListeningBalance);

        mMinActiveListeningDuration =  Objects.requireNonNull(minActiveListeningDuration);
        mMaxActiveListeningDuration =  Objects.requireNonNull(maxActiveListeningDuration);
        checkPositiveAndNotZero(mMinActiveListeningDuration);
        checkPositiveAndNotZero(mMaxActiveListeningDuration);
        checkArgument(mMinActiveListeningDuration.compareTo(mMaxActiveListeningDuration) < 0);

        mLocationNotKnownAgeThreshold = Objects.requireNonNull(locationNotKnownAgeThreshold);
        checkPositiveAndNotZero(mLocationNotKnownAgeThreshold);

        mLocationKnownAgeThreshold = Objects.requireNonNull(locationKnownAgeThreshold);
        checkPositiveAndNotZero(mLocationKnownAgeThreshold);

        mPassiveToActiveRatio = passiveToActiveRatio;
        checkArgument(mPassiveToActiveRatio > 0);
    }

    @Override
    public void depositActiveListeningAmount(@NonNull Duration amount) {
        incrementActiveListeningBalanceWithCeiling(amount);
    }

    @Override
    public void accrueActiveListeningBudget(@NonNull Duration timeInPassiveMode) {
        checkPositiveOrZero(timeInPassiveMode);
        Duration activeTimeAccrued = timeInPassiveMode.dividedBy(mPassiveToActiveRatio);
        incrementActiveListeningBalanceWithCeiling(activeTimeAccrued);
    }

    @Override
    @NonNull
    public synchronized ListeningInstruction getNextListeningInstruction(
            long elapsedRealtimeMillis,
            @Nullable LocationListeningResult lastLocationListeningResult) {

        Duration timeBeforeLastResultIsTooOld = calculateTimeBeforeLastResultIsTooOld(
                elapsedRealtimeMillis, lastLocationListeningResult);
        boolean shouldActiveListen = timeBeforeLastResultIsTooOld.isZero();

        // Convert the active listening budget to duration for type-safe calculations.
        Duration activeListeningBudget = Duration.ofMillis(mActiveListeningBalanceMillis);

        // Work out how much additional active listening budget is required before we can do any
        // active listening.
        // A > 0 value indicates there isn't enough in the balance to cover a period of active
        // listening. Zero indicates there is enough.
        Duration additionalActiveListeningBudgetRequired =
                calculateAdditionalActiveListeningBudgetRequired(activeListeningBudget);
        boolean canActiveListen = additionalActiveListeningBudgetRequired.isZero();

        if (shouldActiveListen && canActiveListen) {
            // Use as much budget as possible without exceeding mMaxActiveListeningDuration.
            Duration activeModeDuration = min(activeListeningBudget, mMaxActiveListeningDuration);

            decrementActiveListeningBalanceWithFloor(activeModeDuration);

            return new ListeningInstruction(LOCATION_LISTEN_MODE_ACTIVE, activeModeDuration);
        } else {
            // Work out how long to listen in passive mode.
            // We want to be responsive: if there is active listening budget available, then spend
            // as little time in passive as allowed. If we don't have enough budget to do active
            // listening, we need to accrue some active listening budget by staying in passive
            // mode.
            Duration passiveModeDuration = mMinPassiveListeningDuration;
            if (!additionalActiveListeningBudgetRequired.isNegative()) {
                Duration passiveTimeNeeded =
                        additionalActiveListeningBudgetRequired.multipliedBy(mPassiveToActiveRatio);

                // Add a fudge factor to account for elapsed time measurement errors that would mean
                // passively listening might be too short.
                passiveTimeNeeded = passiveTimeNeeded.plus(ACCRUAL_INCREMENT);

                // We want to listen for at least mMinPassiveListeningDuration.
                passiveModeDuration = max(passiveModeDuration, passiveTimeNeeded);
            }

            // If we have a recent location result, there's no point in trying active mode again
            // until the location would be considered too old to use, so this sets a lower bound.
            // Use the larger of the two durations.
            passiveModeDuration = max(passiveModeDuration, timeBeforeLastResultIsTooOld);

            return new ListeningInstruction(LOCATION_LISTEN_MODE_PASSIVE, passiveModeDuration);
        }
    }

    @Override
    @NonNull
    public synchronized Duration withdrawActiveListeningBalance() {
        long toReturn = mActiveListeningBalanceMillis;
        mActiveListeningBalanceMillis = 0;
        return Duration.ofMillis(toReturn);
    }

    @NonNull
    private Duration calculateAdditionalActiveListeningBudgetRequired(
            @NonNull Duration activeListeningBudget) {
        Duration budgetRequired = mMinActiveListeningDuration.minus(activeListeningBudget);
        // Guard against negative.
        return max(Duration.ZERO, budgetRequired);
    }

    @NonNull
    private Duration calculateTimeBeforeLastResultIsTooOld(long elapsedRealtimeMillis,
            @Nullable LocationListeningResult lastLocationListeningResult) {
        Duration timeBeforeLastResultIsTooOld;
        if (lastLocationListeningResult == null) {
            timeBeforeLastResultIsTooOld = Duration.ZERO;
        } else {
            if (lastLocationListeningResult.isLocationKnown()) {
                // We are trying to be responsive so it's best to look at the age of the location
                // here. If the active listening budget doesn't allow a more up to date location
                // then passive listening will continue so it does not incur extra cost.
                Duration lastLocationListeningResultAge =
                        lastLocationListeningResult.getLocationAge(elapsedRealtimeMillis);
                timeBeforeLastResultIsTooOld =
                        mLocationKnownAgeThreshold.minus(lastLocationListeningResultAge);
            } else {
                Duration lastLocationListeningResultAge =
                        lastLocationListeningResult.getResultAge(elapsedRealtimeMillis);
                timeBeforeLastResultIsTooOld =
                        mLocationNotKnownAgeThreshold.minus(lastLocationListeningResultAge);
            }
            // Avoid a negative.
            timeBeforeLastResultIsTooOld = max(Duration.ZERO, timeBeforeLastResultIsTooOld);
        }
        return timeBeforeLastResultIsTooOld;
    }

    @Override
    public String toString() {
        return "LocationListeningAccountant{"
                + "mMinPassiveListeningDuration=" + mMinPassiveListeningDuration
                + ", mMinActiveListeningDuration=" + mMinActiveListeningDuration
                + ", mMaxActiveListeningDuration=" + mMaxActiveListeningDuration
                + ", mMaxActiveListeningBalance=" + mMaxActiveListeningBalance
                + ", mMaxLocationNotKnownAge=" + mLocationNotKnownAgeThreshold
                + ", mMaxLocationKnownAge=" + mLocationKnownAgeThreshold
                + ", mPassiveToActiveRatio=" + mPassiveToActiveRatio
                + ", mActiveListeningBalanceMillis=" + mActiveListeningBalanceMillis
                + '}';
    }

    // @VisibleForTesting
    @NonNull
    synchronized Duration getActiveListeningBalanceForTests() {
        return Duration.ofMillis(mActiveListeningBalanceMillis);
    }

    // @VisibleForTesting
    synchronized void setActiveListeningBalanceForTests(@NonNull Duration balance) {
        if (balance.isNegative()) {
            balance = Duration.ZERO;
        } else if (balance.compareTo(mMaxActiveListeningBalance) > 0) {
            balance = mMaxActiveListeningBalance;
        }
        mActiveListeningBalanceMillis = balance.toMillis();
    }

    private synchronized void decrementActiveListeningBalanceWithFloor(@NonNull Duration amount) {
        checkPositiveOrZero(amount);
        mActiveListeningBalanceMillis -= amount.toMillis();
        // Avoid negative balance values.
        mActiveListeningBalanceMillis = Math.max(0, mActiveListeningBalanceMillis);
    }

    private synchronized void incrementActiveListeningBalanceWithCeiling(@NonNull Duration amount) {
        checkPositiveOrZero(amount);
        mActiveListeningBalanceMillis += amount.toMillis();
        // Cap the amount of active balance we can hold.
        mActiveListeningBalanceMillis =
                Math.min(mActiveListeningBalanceMillis, mMaxActiveListeningBalance.toMillis());
    }

    private static void checkArgument(boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException();
        }
    }

    private static void checkPositiveAndNotZero(@NonNull Duration duration) {
        checkArgument(!duration.isZero());
        checkArgument(!duration.isNegative());
    }

    private static void checkPositiveOrZero(@NonNull Duration duration) {
        checkArgument(!duration.isNegative());
    }

    @NonNull
    private static Duration max(@NonNull Duration one, @NonNull Duration two) {
        return one.compareTo(two) >= 0 ? one : two;
    }

    @NonNull
    private static Duration min(@NonNull Duration one, @NonNull Duration two) {
        return one.compareTo(two) < 0 ? one : two;
    }
}
