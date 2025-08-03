/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.timezone.location.provider.core.OfflineLocationTimeZoneDelegate.LOCATION_LISTEN_MODE_ACTIVE;
import static com.android.timezone.location.provider.core.OfflineLocationTimeZoneDelegate.LOCATION_LISTEN_MODE_PASSIVE;
import static com.android.timezone.location.provider.core.TimeZoneProviderResult.RESULT_TYPE_SUGGESTION;
import static com.android.timezone.location.provider.core.TimeZoneProviderResult.RESULT_TYPE_UNCERTAIN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.timezone.location.lookup.GeoTimeZonesFinder;
import com.android.timezone.location.provider.core.Environment.LocationListeningResult;
import com.android.timezone.location.provider.core.LocationListeningAccountant.ListeningInstruction;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Tests for {@link OfflineLocationTimeZoneDelegate}. */
public class OfflineLocationTimeZoneDelegateTest {

    private FakeEnvironment mTestEnvironment;
    private FakeGeoTimeZonesFinder mTestGeoTimeZoneFinder;
    private FakeLocationListeningAccountant mTestLocationListeningAccountant;
    private OfflineLocationTimeZoneDelegate mDelegate;

    @Before
    public void setUp() {
        mTestEnvironment = new FakeEnvironment();
        mTestGeoTimeZoneFinder = mTestEnvironment.mFakeGeoTimeZonesFinder;
        mTestLocationListeningAccountant = mTestEnvironment.mLocationListeningAccountant;
        mDelegate = new OfflineLocationTimeZoneDelegate(mTestEnvironment);
    }

    @Test
    public void locationFoundImmediately() throws Exception {
        // Prime the accountant with instructions.
        ListeningInstruction activeInstruction = new ListeningInstruction(
                LOCATION_LISTEN_MODE_ACTIVE, Duration.ofSeconds(15));
        ListeningInstruction passiveInstruction = new ListeningInstruction(
                LOCATION_LISTEN_MODE_PASSIVE, Duration.ofSeconds(25));
        mTestLocationListeningAccountant.addInstruction(activeInstruction)
                .addInstruction(passiveInstruction);

        // Prime the time zone finder with answers.
        double latDegrees = 1.0;
        double lngDegrees = 1.0;
        List<String> timeZoneIds = Arrays.asList("Europe/London");
        mTestGeoTimeZoneFinder.setTimeZonesForLocation(latDegrees, lngDegrees, timeZoneIds);

        // Start of the test

        assertEquals(Mode.MODE_STOPPED, mDelegate.getCurrentModeEnumForTests());
        mTestEnvironment.assertIsNotListening();
        mTestEnvironment.assertNoActiveTimeouts();
        mTestEnvironment.assertNoResultReported();

        final Duration initializationTimeout = Duration.ofSeconds(20);
        mDelegate.onStartUpdates(initializationTimeout);

        // The provider should have started an initialization timeout and followed the first
        // instruction from the accountant.
        FakeEnvironment.TestTimeoutState<?> initializationTimeoutState =
                mTestEnvironment.getActiveTimeoutState(0);
        initializationTimeoutState.assertDelay(initializationTimeout);
        assertEquals(Mode.MODE_STARTED, mDelegate.getCurrentModeEnumForTests());
        mTestEnvironment.assertIsLocationListening(
                activeInstruction.listenMode, activeInstruction.duration);

        // Simulate the passage of time and a location being detected within the listening
        // duration.
        Duration timeUntilLocationFound = Duration.ofSeconds(5);
        assertTrue(activeInstruction.duration.compareTo(timeUntilLocationFound) > 0);
        mTestEnvironment.simulateTimePassing(timeUntilLocationFound)
                .simulateLocationKnown(latDegrees, lngDegrees);

        // Check the location was used to report a time zone / initialization timeout has been
        // cancelled.
        mTestEnvironment.assertSuggestionResultReported(timeZoneIds);
        assertEquals(Mode.MODE_STARTED, mDelegate.getCurrentModeEnumForTests());
        initializationTimeoutState.assertCancelled();

        // Unused active listening budget should be returned to the accountant. The amount is
        // dependent on how long the active listening result takes.
        Duration maximumReturned = activeInstruction.duration.minus(timeUntilLocationFound);
        mTestLocationListeningAccountant.assertActiveBudgetReturned(
                amount -> amount.compareTo(maximumReturned) <= 0);

        // Check the provider has moved onto the next instruction.
        mTestEnvironment.assertIsLocationListening(
                passiveInstruction.listenMode, passiveInstruction.duration);
    }

    @Test
    public void locationNotFoundImmediately() throws Exception {
        // Prime the accountant with instructions.
        ListeningInstruction activeInstruction = new ListeningInstruction(
                LOCATION_LISTEN_MODE_ACTIVE, Duration.ofSeconds(15));
        ListeningInstruction passiveInstruction = new ListeningInstruction(
                LOCATION_LISTEN_MODE_PASSIVE, Duration.ofSeconds(25));
        mTestLocationListeningAccountant.addInstruction(activeInstruction)
                .addInstruction(passiveInstruction);

        // Start of the test

        assertEquals(Mode.MODE_STOPPED, mDelegate.getCurrentModeEnumForTests());
        mTestEnvironment.assertIsNotListening();
        mTestEnvironment.assertNoActiveTimeouts();
        mTestEnvironment.assertNoResultReported();

        final Duration initializationTimeout = Duration.ofSeconds(20);
        mDelegate.onStartUpdates(initializationTimeout);

        // The provider should have started an initialization timeout and followed the first
        // instruction from the accountant.
        FakeEnvironment.TestTimeoutState<?> initializationTimeoutState =
                mTestEnvironment.getActiveTimeoutState(0);
        initializationTimeoutState.assertDelay(initializationTimeout);
        assertEquals(Mode.MODE_STARTED, mDelegate.getCurrentModeEnumForTests());
        mTestEnvironment.assertIsLocationListening(
                activeInstruction.listenMode, activeInstruction.duration);

        // Simulate the passage of time and the location not being detected.
        mTestEnvironment.simulateTimePassing(activeInstruction.duration)
                .simulateActiveLocationNotKnown();
        mTestLocationListeningAccountant.assertActiveBudgetReturned(x -> x.equals(Duration.ZERO));

        // activeInstruction.duration < initialization timeout, so no report should have been made
        // yet.
        assertTrue(activeInstruction.duration.compareTo(initializationTimeout) < 0);
        initializationTimeoutState.assertNotCancelled();

        // Check the provider has moved onto the next instruction.
        mTestEnvironment.assertIsLocationListening(
                passiveInstruction.listenMode, passiveInstruction.duration);
    }

    @Test
    public void onDestroy_neverStarted() throws Exception {
        assertEquals(Mode.MODE_STOPPED, mDelegate.getCurrentModeEnumForTests());
        mTestEnvironment.assertIsNotListening();
        mTestEnvironment.assertNoActiveTimeouts();
        mTestEnvironment.assertNoResultReported();

        mDelegate.onDestroy();

        assertEquals(Mode.MODE_DESTROYED, mDelegate.getCurrentModeEnumForTests());
        mTestEnvironment.assertIsNotListening();
        mTestEnvironment.assertNoActiveTimeouts();
        mTestEnvironment.assertNoResultReported();
    }

    @Test
    public void onDestroy_afterStarted() throws Exception {
        // Prime the accountant with instructions.
        ListeningInstruction activeInstruction = new ListeningInstruction(
                LOCATION_LISTEN_MODE_ACTIVE, Duration.ofSeconds(15));
        ListeningInstruction passiveInstruction = new ListeningInstruction(
                LOCATION_LISTEN_MODE_PASSIVE, Duration.ofSeconds(25));
        mTestLocationListeningAccountant.addInstruction(activeInstruction)
                .addInstruction(passiveInstruction);

        // Start of the test

        assertEquals(Mode.MODE_STOPPED, mDelegate.getCurrentModeEnumForTests());
        mTestEnvironment.assertIsNotListening();
        mTestEnvironment.assertNoActiveTimeouts();
        mTestEnvironment.assertNoResultReported();

        final Duration initializationTimeout = Duration.ofSeconds(20);
        mDelegate.onStartUpdates(initializationTimeout);

        // The provider should have started an initialization timeout and followed the first
        // instruction from the accountant.
        FakeEnvironment.TestTimeoutState<?> initializationTimeoutState =
                mTestEnvironment.getActiveTimeoutState(0);
        initializationTimeoutState.assertDelay(initializationTimeout);
        assertEquals(Mode.MODE_STARTED, mDelegate.getCurrentModeEnumForTests());
        mTestEnvironment.assertIsLocationListening(
                activeInstruction.listenMode, activeInstruction.duration);

        // Destroyed without first being stopped.
        mDelegate.onDestroy();

        assertEquals(Mode.MODE_DESTROYED, mDelegate.getCurrentModeEnumForTests());
        mTestEnvironment.assertIsNotListening();
        mTestEnvironment.assertNoActiveTimeouts();
        mTestEnvironment.assertUncertainResultReported();
    }

    @Test
    public void onDestroy_afterStartedAndStopped() throws Exception {
        // Prime the accountant with instructions.
        ListeningInstruction activeInstruction = new ListeningInstruction(
                LOCATION_LISTEN_MODE_ACTIVE, Duration.ofSeconds(15));
        ListeningInstruction passiveInstruction = new ListeningInstruction(
                LOCATION_LISTEN_MODE_PASSIVE, Duration.ofSeconds(25));
        mTestLocationListeningAccountant.addInstruction(activeInstruction)
                .addInstruction(passiveInstruction);

        // Start of the test
        assertEquals(Mode.MODE_STOPPED, mDelegate.getCurrentModeEnumForTests());
        mTestEnvironment.assertIsNotListening();
        mTestEnvironment.assertNoActiveTimeouts();
        mTestEnvironment.assertNoResultReported();

        final Duration initializationTimeout = Duration.ofSeconds(20);
        mDelegate.onStartUpdates(initializationTimeout);

        // The provider should have started an initialization timeout and followed the first
        // instruction from the accountant.
        FakeEnvironment.TestTimeoutState<?> initializationTimeoutState =
                mTestEnvironment.getActiveTimeoutState(0);
        initializationTimeoutState.assertDelay(initializationTimeout);
        assertEquals(Mode.MODE_STARTED, mDelegate.getCurrentModeEnumForTests());
        mTestEnvironment.assertIsLocationListening(
                activeInstruction.listenMode, activeInstruction.duration);

        mDelegate.onStopUpdates();
        assertEquals(Mode.MODE_STOPPED, mDelegate.getCurrentModeEnumForTests());
        mTestEnvironment.assertIsNotListening();
        mTestEnvironment.assertNoActiveTimeouts();
        mTestEnvironment.assertNoResultReported();

        // Destroyed without first being stopped.
        mDelegate.onDestroy();

        assertEquals(Mode.MODE_DESTROYED, mDelegate.getCurrentModeEnumForTests());
        mTestEnvironment.assertIsNotListening();
        mTestEnvironment.assertNoActiveTimeouts();
        mTestEnvironment.assertNoResultReported();
    }

    private static class FakeEnvironment implements Environment {

        private final FakeGeoTimeZonesFinder mFakeGeoTimeZonesFinder = new FakeGeoTimeZonesFinder();
        private final FakeLocationListeningAccountant mLocationListeningAccountant =
                new FakeLocationListeningAccountant();

        private long mElapsedRealtimeMillis;
        private PassiveLocationListenerState mPassiveLocationListeningState;
        private ActiveLocationListenerState mActiveLocationListeningState;
        private final List<TestTimeoutState<?>> mTimeouts = new ArrayList<>();
        private TimeZoneProviderResult mLastResultReported;

        @NonNull
        @Override
        public LocationListeningAccountant getLocationListeningAccountant() {
            return mLocationListeningAccountant;
        }

        @NonNull
        @Override
        public <T> Cancellable requestDelayedCallback(@NonNull Consumer<T> callback,
                @Nullable T callbackToken, @NonNull Duration delay) {
            TestTimeoutState<T> timeoutState =
                    new TestTimeoutState<>(callback, callbackToken, delay);
            mTimeouts.add(timeoutState);
            return timeoutState;
        }

        @NonNull
        @Override
        public Cancellable startPassiveLocationListening(@NonNull Duration duration,
                @NonNull Consumer<LocationListeningResult> locationResultConsumer,
                @NonNull Consumer<Duration> passiveListeningCompletedCallback) {
            assertNull(mPassiveLocationListeningState);
            assertNull(mActiveLocationListeningState);

            mPassiveLocationListeningState = new PassiveLocationListenerState(
                    duration, locationResultConsumer, passiveListeningCompletedCallback);
            return mPassiveLocationListeningState;
        }

        @NonNull
        @Override
        public Cancellable startActiveGetCurrentLocation(@NonNull Duration duration,
                @NonNull Consumer<LocationListeningResult> locationResultConsumer) {
            assertNull(mPassiveLocationListeningState);
            assertNull(mActiveLocationListeningState);

            mActiveLocationListeningState = new ActiveLocationListenerState(
                    duration, locationResultConsumer);
            return mActiveLocationListeningState;
        }

        @NonNull
        @Override
        public GeoTimeZonesFinder createGeoTimeZoneFinder() throws IOException {
            return mFakeGeoTimeZonesFinder;
        }

        @Override
        public void reportTimeZoneProviderResult(@NonNull TimeZoneProviderResult result) {
            assertNotNull(result);
            mLastResultReported = result;
        }

        @Override
        public void acquireWakeLock() {
            // No-op in tests.
        }

        @Override
        public void releaseWakeLock() {
            // No-op in tests.
        }

        @Override
        public long elapsedRealtimeMillis() {
            return mElapsedRealtimeMillis++;
        }

        FakeEnvironment simulatePassiveLocationListeningEnding() {
            assertNotNull(mPassiveLocationListeningState);
            mPassiveLocationListeningState.simulateListeningEnded();
            return this;
        }

        FakeEnvironment simulateActiveLocationNotKnown() {
            assertNotNull(mActiveLocationListeningState);
            mActiveLocationListeningState.simulateLocationNotKnown();
            return this;
        }

        FakeEnvironment simulateLocationKnown(double latDegrees, double lngDegrees) {
            Location location = new Location("test");
            location.setLatitude(latDegrees);
            location.setLongitude(lngDegrees);

            location.setElapsedRealtimeNanos(MILLISECONDS.toNanos(mElapsedRealtimeMillis));
            if (mPassiveLocationListeningState != null) {
                mPassiveLocationListeningState.simulateLocationKnown(location);
            } else if (mActiveLocationListeningState != null) {
                mActiveLocationListeningState.simulateLocationKnown(location);

                // The active location listening is cancelled as soon as a location is known.
                mActiveLocationListeningState = null;
            } else {
                fail("No listening started.");
            }
            return this;
        }

        FakeEnvironment assertIsLocationListening(
                int expectedLocationListenMode, Duration expectedDuration) {
            if (expectedLocationListenMode == LOCATION_LISTEN_MODE_ACTIVE) {
                assertNull(mPassiveLocationListeningState);
                assertNotNull(mActiveLocationListeningState);
                assertEquals(expectedDuration, mActiveLocationListeningState.duration);
            } else {
                assertNull(mActiveLocationListeningState);
                assertNotNull(mPassiveLocationListeningState);
                assertEquals(expectedDuration, mPassiveLocationListeningState.duration);
            }
            return this;
        }

        FakeEnvironment assertIsNotListening() {
            assertNull(mPassiveLocationListeningState);
            assertNull(mActiveLocationListeningState);
            return this;
        }

        FakeEnvironment assertNoActiveTimeouts() {
            assertTrue(mTimeouts.isEmpty());
            return this;
        }

        TestTimeoutState<?> getActiveTimeoutState(int index) {
            return mTimeouts.get(index);
        }

        FakeEnvironment assertNoResultReported() {
            assertNull(mLastResultReported);
            return this;
        }

        FakeEnvironment assertSuggestionResultReported(List<String> expectedTimeZoneIds) {
            assertNotNull(mLastResultReported);
            assertEquals(RESULT_TYPE_SUGGESTION, mLastResultReported.getType());
            assertEquals(expectedTimeZoneIds, mLastResultReported.getSuggestion().getTimeZoneIds());
            return this;
        }

        FakeEnvironment assertUncertainResultReported() {
            assertNotNull(mLastResultReported);
            assertEquals(RESULT_TYPE_UNCERTAIN, mLastResultReported.getType());
            return this;
        }

        FakeEnvironment simulateTimePassing(Duration duration) {
            mElapsedRealtimeMillis += duration.toMillis();
            return this;
        }

        private class PassiveLocationListenerState extends TestCancellable {
            public final Duration duration;
            public final long startElapsedRealtimeMillis;
            private final Consumer<LocationListeningResult> mLocationListener;
            private final Consumer<Duration> mListeningCompletedCallback;

            PassiveLocationListenerState(
                    Duration duration, Consumer<LocationListeningResult> locationListener,
                    Consumer<Duration> listeningCompletedCallback) {
                this.duration = duration;
                this.startElapsedRealtimeMillis = elapsedRealtimeMillis();
                mLocationListener = locationListener;
                mListeningCompletedCallback = listeningCompletedCallback;
            }

            @Override
            public void cancel() {
                super.cancel();
                assertSame(this, mPassiveLocationListeningState);
                mPassiveLocationListeningState = null;
            }

            void simulateListeningEnded() {
                mListeningCompletedCallback.accept(duration);
            }

            void simulateLocationKnown(Location location) {
                LocationListeningResult locationListeningResult = new LocationListeningResult(
                        LOCATION_LISTEN_MODE_PASSIVE,
                        duration,
                        startElapsedRealtimeMillis,
                        NANOSECONDS.toMillis(location.getElapsedRealtimeNanos()),
                        location);
                mLocationListener.accept(locationListeningResult);
            }
        }

        private class ActiveLocationListenerState extends TestCancellable {
            public final Duration duration;
            public final long startElapsedRealtimeMillis;
            private final Consumer<LocationListeningResult> mLocationListener;

            ActiveLocationListenerState(
                    Duration duration, Consumer<LocationListeningResult> locationListener) {
                this.duration = duration;
                this.startElapsedRealtimeMillis = elapsedRealtimeMillis();
                mLocationListener = locationListener;
            }

            @Override
            public void cancel() {
                super.cancel();
                assertSame(this, mActiveLocationListeningState);
                mActiveLocationListeningState = null;
            }

            void simulateLocationNotKnown() {
                LocationListeningResult locationListeningResult = new LocationListeningResult(
                        LOCATION_LISTEN_MODE_ACTIVE,
                        duration,
                        startElapsedRealtimeMillis,
                        elapsedRealtimeMillis(),
                        null /* location */);
                mLocationListener.accept(locationListeningResult);
            }

            void simulateLocationKnown(Location location) {
                LocationListeningResult locationListeningResult = new LocationListeningResult(
                        LOCATION_LISTEN_MODE_ACTIVE,
                        duration,
                        startElapsedRealtimeMillis,
                        NANOSECONDS.toMillis(location.getElapsedRealtimeNanos()),
                        location);
                mLocationListener.accept(locationListeningResult);
            }
        }

        private class TestTimeoutState<T> extends TestCancellable {

            private final Consumer<T> mCallback;
            private final T mCallbackToken;
            private final Duration mDelay;

            TestTimeoutState(Consumer<T> callback, T callbackToken, Duration delay) {
                mCallback = callback;
                mCallbackToken = callbackToken;
                mDelay = delay;
            }

            @Override
            public void cancel() {
                super.cancel();
                assertTrue(mTimeouts.remove(this));
            }

            void assertDelay(Duration expected) {
                assertEquals(expected, mDelay);
            }
        }

        private static class TestCancellable implements Cancellable {

            private boolean mCancelled;

            @Override
            public void cancel() {
                assertFalse(mCancelled);
                mCancelled = true;
            }

            boolean isCancelled() {
                return mCancelled;
            }

            void assertCancelled() {
                assertTrue(mCancelled);
            }

            void assertNotCancelled() {
                assertFalse(mCancelled);
            }
        }
    }

    private static class FakeGeoTimeZonesFinder extends GeoTimeZonesFinder {

        private boolean mFailureMode;

        private static class FakeLocationToken extends LocationToken {

            private final double mLngDegrees;
            private final double mLatDegrees;

            FakeLocationToken(double latDegrees, double lngDegrees) {
                mLatDegrees = latDegrees;
                mLngDegrees = lngDegrees;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                FakeLocationToken that = (FakeLocationToken) o;
                return Double.compare(that.mLngDegrees, mLngDegrees) == 0
                        && Double.compare(that.mLatDegrees, mLatDegrees) == 0;
            }

            @Override
            public int hashCode() {
                return Objects.hash(mLngDegrees, mLatDegrees);
            }

            @Override
            public String toString() {
                // Using the debug string is ok for test code.
                return toPiiString();
            }

            @Override
            public String toPiiString() {
                return "FakeLocationToken{"
                        + "mLngDegrees=" + mLngDegrees
                        + ", mLatDegrees=" + mLatDegrees
                        + '}';
            }
        }

        private final Map<LocationToken, List<String>> mTimeZoneLookup = new HashMap<>();

        @Override
        public LocationToken createLocationTokenForLatLng(double latDegrees, double lngDegrees)
                throws IOException {
            throwExceptionIfInFailureMore();
            return new FakeLocationToken(latDegrees, lngDegrees);
        }

        @Override
        public List<String> findTimeZonesForLatLng(double latDegrees, double lngDegrees)
                throws IOException {
            throwExceptionIfInFailureMore();
            return findTimeZonesForLocationToken(
                    createLocationTokenForLatLng(latDegrees, lngDegrees));
        }

        @Override
        public List<String> findTimeZonesForLocationToken(LocationToken locationToken)
                throws IOException {
            throwExceptionIfInFailureMore();
            return mTimeZoneLookup.get(locationToken);
        }

        @Override
        public void close() throws IOException {
            // No-op in the fake
        }

        void setTimeZonesForLocation(
                double latDegrees, double lngDegrees, List<String> timeZoneIds) {
            mTimeZoneLookup.put(new FakeLocationToken(latDegrees, lngDegrees), timeZoneIds);
        }

        void setFailureMode(boolean fail) {
            mFailureMode = fail;
        }

        private void throwExceptionIfInFailureMore() throws IOException {
            if (mFailureMode) {
                throw new IOException("Faked lookup failure");
            }
        }
    }

    /**
     * A fake for tests that returns pre-determined instructions and records any deposits.
     */
    private static class FakeLocationListeningAccountant implements LocationListeningAccountant {
        // A queue of instructions.
        private final LinkedList<ListeningInstruction> mListeningInstructions = new LinkedList<>();
        private long mBalanceReturned;
        private long mAccruedPassive;

        @Override
        public void depositActiveListeningAmount(@NonNull Duration amount) {
            mBalanceReturned += amount.toMillis();
        }

        @Override
        public void accrueActiveListeningBudget(@NonNull Duration timeInPassiveMode) {
            mAccruedPassive += timeInPassiveMode.toMillis();
        }

        @NonNull
        @Override
        public ListeningInstruction getNextListeningInstruction(long elapsedRealtimeMillis,
                @Nullable LocationListeningResult lastLocationListeningResult) {
            return mListeningInstructions.removeFirst();
        }

        @Override
        @NonNull
        public Duration withdrawActiveListeningBalance() {
            // Not implemented for tests.
            throw new UnsupportedOperationException();
        }

        FakeLocationListeningAccountant addInstruction(ListeningInstruction listeningInstruction) {
            mListeningInstructions.addLast(listeningInstruction);
            return this;
        }

        FakeLocationListeningAccountant assertActiveBudgetReturned(
                Predicate<Duration> expectedReturned) {
            assertTrue(expectedReturned.test(Duration.ofMillis(mBalanceReturned)));
            return this;
        }
    }
}
