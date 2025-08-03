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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.location.Location;

import com.android.timezone.location.provider.core.Environment.LocationListeningResult;
import com.android.timezone.location.provider.core.LocationListeningAccountant.ListeningInstruction;

import org.junit.Before;
import org.junit.Test;

import java.time.Duration;

/** Unit tests for {@link RealLocationListeningAccountant}. */
public class RealLocationListeningAccountantTest {

    private static final Duration MAX_ACTIVE_LISTENING_BALANCE = seconds(500);
    private static final Duration MIN_PASSIVE_LISTENING_DURATION = seconds(3);
    private static final Duration MIN_ACTIVE_LISTENING_DURATION = seconds(5);
    private static final Duration MAX_ACTIVE_LISTENING_DURATION = seconds(17);
    private static final Duration LOCATION_KNOWN_AGE_THRESHOLD = seconds(13);
    private static final Duration LOCATION_NOT_KNOWN_AGE_THRESHOLD = seconds(7);
    private static final long PASSIVE_TO_ACTIVE_RATIO = 57;

    // Values used in tests when the value is not important.
    private static final long ARBITRARY_ELAPSED_REALTIME_MILLIS = 54321;
    private static final Duration ARBITRARY_LISTENING_DURATION = seconds(5);

    private RealLocationListeningAccountant mAccountant;


    @Before
    public void setUp() {
        mAccountant = new RealLocationListeningAccountant(
                MIN_PASSIVE_LISTENING_DURATION, MAX_ACTIVE_LISTENING_BALANCE,
                MIN_ACTIVE_LISTENING_DURATION, MAX_ACTIVE_LISTENING_DURATION,
                LOCATION_NOT_KNOWN_AGE_THRESHOLD, LOCATION_KNOWN_AGE_THRESHOLD,
                PASSIVE_TO_ACTIVE_RATIO);
    }

    @Test
    public void depositActiveListeningAmount_capped() {
        assertEquals(seconds(0), mAccountant.getActiveListeningBalanceForTests());

        mAccountant.depositActiveListeningAmount(seconds(1));
        assertEquals(seconds(1), mAccountant.getActiveListeningBalanceForTests());

        mAccountant.depositActiveListeningAmount(seconds(2));
        assertEquals(seconds(3), mAccountant.getActiveListeningBalanceForTests());

        Duration increment = seconds(50);
        while (mAccountant.getActiveListeningBalanceForTests().toMillis()
                < MAX_ACTIVE_LISTENING_BALANCE.toMillis()) {
            mAccountant.depositActiveListeningAmount(increment);
        }
        assertEquals(MAX_ACTIVE_LISTENING_BALANCE, mAccountant.getActiveListeningBalanceForTests());

        // One more for luck
        mAccountant.depositActiveListeningAmount(increment);
        assertEquals(MAX_ACTIVE_LISTENING_BALANCE, mAccountant.getActiveListeningBalanceForTests());
    }

    @Test
    public void accrueActiveListeningBudget_capped() {
        assertEquals(seconds(0), mAccountant.getActiveListeningBalanceForTests());

        // The ratio is applied in millis. Smaller units will not be observed.
        long tooSmall = PASSIVE_TO_ACTIVE_RATIO - 1;
        mAccountant.accrueActiveListeningBudget(millis(tooSmall));
        assertEquals(millis(0), mAccountant.getActiveListeningBalanceForTests());

        mAccountant.accrueActiveListeningBudget(millis(PASSIVE_TO_ACTIVE_RATIO));
        assertEquals(millis(1), mAccountant.getActiveListeningBalanceForTests());

        mAccountant.accrueActiveListeningBudget(millis(PASSIVE_TO_ACTIVE_RATIO).multipliedBy(5));
        assertEquals(millis(1 + 5), mAccountant.getActiveListeningBalanceForTests());

        Duration increment = seconds(50);
        while (mAccountant.getActiveListeningBalanceForTests().toMillis()
                < MAX_ACTIVE_LISTENING_BALANCE.toMillis()) {
            mAccountant.accrueActiveListeningBudget(increment);
        }
        assertEquals(MAX_ACTIVE_LISTENING_BALANCE, mAccountant.getActiveListeningBalanceForTests());

        // One more for luck
        mAccountant.accrueActiveListeningBudget(increment);
        assertEquals(MAX_ACTIVE_LISTENING_BALANCE, mAccountant.getActiveListeningBalanceForTests());
    }

    @Test
    public void getNextListeningInstruction_noActiveBudget_noLastLocationResult() {
        assertEquals(seconds(0), mAccountant.getActiveListeningBalanceForTests());

        LocationListeningResult lastLocationResult = null;

        Duration balanceBeforeCall = mAccountant.getActiveListeningBalanceForTests();
        ListeningInstruction instruction =
                mAccountant.getNextListeningInstruction(
                        ARBITRARY_ELAPSED_REALTIME_MILLIS, lastLocationResult);
        assertActiveListeningBudget(balanceBeforeCall);

        // The accountant should be consistent when there's no budget.
        assertEquals(instruction,
                mAccountant.getNextListeningInstruction(ARBITRARY_ELAPSED_REALTIME_MILLIS,
                        lastLocationResult));

        // It should tell us to listen passively because there's no active budget.
        assertEquals(LOCATION_LISTEN_MODE_PASSIVE, instruction.listenMode);

        // We're not precise in our expectations, but we do expect it to be valid.
        assertValidPassiveListeningDuration(instruction.duration);

        // If we do as we're told, the next instruction should be active.
        mAccountant.accrueActiveListeningBudget(instruction.duration);

        ListeningInstruction instruction2 =
                mAccountant.getNextListeningInstruction(ARBITRARY_ELAPSED_REALTIME_MILLIS,
                        lastLocationResult);
        assertEquals(LOCATION_LISTEN_MODE_ACTIVE, instruction2.listenMode);
    }

    @Test
    public void getNextListeningInstruction_withSmallActiveBudget_noLastLocationResult() {
        mAccountant.setActiveListeningBalanceForTests(MIN_ACTIVE_LISTENING_DURATION);

        LocationListeningResult lastLocationResult = null;

        Duration balanceBeforeCall = mAccountant.getActiveListeningBalanceForTests();
        ListeningInstruction instruction =
                mAccountant.getNextListeningInstruction(
                        ARBITRARY_ELAPSED_REALTIME_MILLIS, lastLocationResult);
        assertActiveListeningBudget(balanceBeforeCall.minus(instruction.duration));

        // It should tell us to listen actively because there was active budget.
        assertEquals(seconds(0), mAccountant.getActiveListeningBalanceForTests());
        assertEquals(LOCATION_LISTEN_MODE_ACTIVE, instruction.listenMode);
        assertValidActiveListeningDuration(instruction.duration);
    }

    @Test
    public void getNextListeningInstruction_withLargeActiveBudget_noLastLocationResult() {
        Duration largeBalance = MAX_ACTIVE_LISTENING_DURATION.multipliedBy(5);
        mAccountant.setActiveListeningBalanceForTests(largeBalance);

        LocationListeningResult lastLocationResult = null;

        Duration balanceBeforeCall = mAccountant.getActiveListeningBalanceForTests();
        ListeningInstruction instruction =
                mAccountant.getNextListeningInstruction(
                        ARBITRARY_ELAPSED_REALTIME_MILLIS, lastLocationResult);
        assertActiveListeningBudget(balanceBeforeCall.minus(instruction.duration));

        // It should tell us to listen actively because there was active budget.
        assertEquals(LOCATION_LISTEN_MODE_ACTIVE, instruction.listenMode);
        assertValidActiveListeningDuration(instruction.duration);
    }

    @Test
    public void getNextListeningInstruction_noActiveBudget_lastKnownLocationResult() {
        mAccountant.setActiveListeningBalanceForTests(seconds(0));
        doLastKnownLocationResultTest();
    }

    @Test
    public void getNextListeningInstruction_withActiveBudget_lastKnownLocationResult() {
        mAccountant.setActiveListeningBalanceForTests(MIN_ACTIVE_LISTENING_DURATION);
        doLastKnownLocationResultTest();
    }

    private void doLastKnownLocationResultTest() {
        long baseTimeMillis = ARBITRARY_ELAPSED_REALTIME_MILLIS;
        long locationTimeMillis = baseTimeMillis + 5000;
        long resultTimeMillis = locationTimeMillis + 1000;
        long nextInstructionTimeMillis = resultTimeMillis + 5000;

        Location location = createLocationWithElapsedRealtimeMillis(locationTimeMillis);
        LocationListeningResult lastLocationResult = new LocationListeningResult(
                LOCATION_LISTEN_MODE_PASSIVE,
                ARBITRARY_LISTENING_DURATION,
                baseTimeMillis,
                resultTimeMillis,
                location);

        // The following is required for the test to make sense.
        long resultAgeMillis = nextInstructionTimeMillis - resultTimeMillis;
        assertTrue(resultAgeMillis < LOCATION_KNOWN_AGE_THRESHOLD.toMillis());

        Duration balanceBeforeCall = mAccountant.getActiveListeningBalanceForTests();
        ListeningInstruction instruction =
                mAccountant.getNextListeningInstruction(
                        nextInstructionTimeMillis, lastLocationResult);
        assertActiveListeningBudget(balanceBeforeCall);

        // It should tell us to listen passively for a period because the last "location known"
        // result was new enough.
        assertEquals(LOCATION_LISTEN_MODE_PASSIVE, instruction.listenMode);
        assertValidPassiveListeningDuration(instruction.duration);
    }

    @Test
    public void getNextListeningInstruction_notEnoughActiveBudget_lastNotKnownLocationResult() {
        mAccountant.setActiveListeningBalanceForTests(
                MIN_ACTIVE_LISTENING_DURATION.minus(seconds(1)));
        doLastKnownLocationResultTest();
    }

    @Test
    public void getNextListeningInstruction_activeBudget_lastNotKnownLocationResult() {
        mAccountant.setActiveListeningBalanceForTests(
                MIN_ACTIVE_LISTENING_DURATION.plus(seconds(1)));
        doLastNotKnownLocationResultTest();
    }

    private void doLastNotKnownLocationResultTest() {
        long baseTimeMillis = ARBITRARY_ELAPSED_REALTIME_MILLIS;
        long resultTimeMillis = baseTimeMillis + 5000;
        long nextInstructionTimeMillis = resultTimeMillis + 5000;

        Location location = null;
        LocationListeningResult lastLocationResult = new LocationListeningResult(
                LOCATION_LISTEN_MODE_ACTIVE,
                ARBITRARY_LISTENING_DURATION,
                baseTimeMillis,
                resultTimeMillis,
                location);

        // The following is required for the test to make sense.
        long resultAgeMillis = nextInstructionTimeMillis - resultTimeMillis;
        assertTrue(resultAgeMillis < LOCATION_NOT_KNOWN_AGE_THRESHOLD.toMillis());

        Duration balanceBeforeCall = mAccountant.getActiveListeningBalanceForTests();
        ListeningInstruction instruction =
                mAccountant.getNextListeningInstruction(
                        nextInstructionTimeMillis, lastLocationResult);
        assertActiveListeningBudget(balanceBeforeCall);

        // It should tell us to listen passively for a period because the last "location not known"
        // result was new enough.
        assertEquals(LOCATION_LISTEN_MODE_PASSIVE, instruction.listenMode);
        assertValidPassiveListeningDuration(instruction.duration);
    }

    @Test
    public void getNextListeningInstruction_activeBudget_lastNotKnownLocationResultTooOld() {
        mAccountant.setActiveListeningBalanceForTests(
                MIN_ACTIVE_LISTENING_DURATION.plus(seconds(1)));

        long baseTimeMillis = ARBITRARY_ELAPSED_REALTIME_MILLIS;
        long resultTimeMillis = baseTimeMillis + 5000;
        long nextInstructionTimeMillis =
                resultTimeMillis + LOCATION_NOT_KNOWN_AGE_THRESHOLD.toMillis();

        Location location = null;
        LocationListeningResult lastLocationResult = new LocationListeningResult(
                LOCATION_LISTEN_MODE_ACTIVE,
                ARBITRARY_LISTENING_DURATION,
                baseTimeMillis,
                resultTimeMillis,
                location);

        // The following is required for the test to make sense.
        long resultAgeMillis = nextInstructionTimeMillis - resultTimeMillis;
        assertTrue(resultAgeMillis >= LOCATION_NOT_KNOWN_AGE_THRESHOLD.toMillis());

        Duration balanceBeforeCall = mAccountant.getActiveListeningBalanceForTests();
        ListeningInstruction instruction =
                mAccountant.getNextListeningInstruction(
                        nextInstructionTimeMillis, lastLocationResult);
        assertActiveListeningBudget(balanceBeforeCall.minus(instruction.duration));

        // It should tell us to listen actively for a period because the last "location not known"
        // result was not new enough.
        assertEquals(seconds(0), mAccountant.getActiveListeningBalanceForTests());
        assertEquals(LOCATION_LISTEN_MODE_ACTIVE, instruction.listenMode);
        assertValidActiveListeningDuration(instruction.duration);
    }

    private static void assertValidPassiveListeningDuration(Duration duration) {
        assertTrue(duration.compareTo(MIN_PASSIVE_LISTENING_DURATION) >= 0);
        // There is no maximum.
    }

    private static void assertValidActiveListeningDuration(Duration duration) {
        assertTrue(duration.compareTo(MIN_ACTIVE_LISTENING_DURATION) >= 0);
        assertTrue(duration.compareTo(MAX_ACTIVE_LISTENING_DURATION) <= 0);
    }

    private void assertActiveListeningBudget(Duration expectedBalance) {
        Duration balance = mAccountant.getActiveListeningBalanceForTests();
        assertEquals(expectedBalance, balance);
    }

    private static Location createLocationWithElapsedRealtimeMillis(
            long locationElapsedRealtimeMillis) {
        Location location = new Location("test location");
        location.setLongitude(1);
        location.setLatitude(1);
        location.setElapsedRealtimeNanos(MILLISECONDS.toNanos(locationElapsedRealtimeMillis));
        return location;
    }

    private static Duration seconds(long i) {
        return Duration.ofSeconds(i);
    }

    private static Duration millis(long i) {
        return Duration.ofMillis(i);
    }
}
