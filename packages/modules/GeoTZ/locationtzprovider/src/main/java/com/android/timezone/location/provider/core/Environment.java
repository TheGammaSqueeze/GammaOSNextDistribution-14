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

import static com.android.timezone.location.provider.core.LogUtils.formatElapsedRealtimeMillis;
import static com.android.timezone.location.provider.core.Mode.prettyPrintListenModeEnum;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import android.location.Location;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.timezone.location.common.PiiLoggable;
import com.android.timezone.location.common.PiiLoggables;
import com.android.timezone.location.common.PiiLoggables.PiiLoggableValue;
import com.android.timezone.location.lookup.GeoTimeZonesFinder;
import com.android.timezone.location.provider.core.OfflineLocationTimeZoneDelegate.ListenModeEnum;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * An interface that {@link OfflineLocationTimeZoneDelegate} uses to interact with its environment.
 *
 * <p>This interface exists to make the {@link OfflineLocationTimeZoneDelegate} more testable.
 */
public interface Environment {

    /**
     * Returns the {@link LocationListeningAccountant}. Callers may safely retain a reference.
     */
    @NonNull
    LocationListeningAccountant getLocationListeningAccountant();

    /**
     * Requests a callback to {@code callback} with {@code callbackToken} after at least
     * {@code delayMillis}. An object is returned that can be used to cancel the callback later.
     */
    @NonNull
    <T> Cancellable requestDelayedCallback(@NonNull Consumer<T> callback,
            @Nullable T callbackToken, @NonNull Duration delay);

    /**
     * The result of a location listening attempt started with {@link
     * Environment#startActiveGetCurrentLocation} or {@link
     * Environment#startPassiveLocationListening}}.
     *
     * <p>With active listening the {@link #getLocation() location} can be {@code null} (meaning
     * "location unknown"), with passive listening it is never {@code null}.
     */
    final class LocationListeningResult implements PiiLoggable {
        /**
         * The type of listening that produced the result. This is recorded for logging / debugging.
         */
        private final @ListenModeEnum int mListenMode;

        /** The duration argument passed to the listening method. */
        @NonNull private final Duration mListeningDuration;

        /** An approximate time when listening started. */
        private final long mStartElapsedRealtimeMillis;

        /**
         * An approximate time when the result was received (which might be different from the
         * time associated with any location).
         */
        private final long mResultElapsedRealtimeMillis;

        /** Holds the location, or {@code null} if the location is not known (active only). */
        @NonNull private final PiiLoggableValue<Location> mPiiLoggableLocation;

        public LocationListeningResult(
                @ListenModeEnum int listenMode,
                @NonNull Duration listeningDuration,
                long startElapsedRealtimeMillis,
                long resultElapsedRealtimeMillis,
                @Nullable Location location) {
            mListenMode = listenMode;
            mListeningDuration = Objects.requireNonNull(listeningDuration);
            mStartElapsedRealtimeMillis = startElapsedRealtimeMillis;
            mResultElapsedRealtimeMillis = resultElapsedRealtimeMillis;
            mPiiLoggableLocation = PiiLoggables.fromPiiValue(location);
        }

        /** Returns how long listening was requested for. */
        @NonNull
        public Duration getRequestedListeningDuration() {
            return mListeningDuration;
        }

        /** Returns whether result of listening was a known location. */
        public boolean isLocationKnown() {
            return mPiiLoggableLocation.get() != null;
        }

        /** Returns the location. See {@link #isLocationKnown()}. */
        @Nullable
        public Location getLocation() {
            return mPiiLoggableLocation.get();
        }

        /** Returns (an approximation) of when listening started. */
        public long getStartElapsedRealtimeTimeMillis() {
            return mStartElapsedRealtimeMillis;
        }

        /** Returns (an approximation) of when the result was obtained. */
        public long getResultElapsedRealtimeMillis() {
            return mResultElapsedRealtimeMillis;
        }

        /**
         * Returns the estimated time between when the listening session started and this result was
         * generated. This value is <em>not</em> incremental, i.e. if listening is continuous then
         * this returns the total time listening and not the time elapsed since the last result.
         */
        @NonNull
        public Duration getTotalEstimatedTimeListening() {
            Duration estimatedTimeListening =
                    Duration.ofMillis(mResultElapsedRealtimeMillis - mStartElapsedRealtimeMillis);
            // Guard against invalid times that could be caused if locations have an incorrect
            // elapsed realtime associated with them.
            return estimatedTimeListening.isNegative() ? Duration.ZERO : estimatedTimeListening;
        }

        /** Returns the age of the result. */
        @NonNull
        public Duration getResultAge(long elapsedRealtimeMillis) {
            long ageMillis = elapsedRealtimeMillis - mResultElapsedRealtimeMillis;
            return Duration.ofMillis(ageMillis);
        }

        /**
         * Returns the age of the location in the result. Throws an exception if
         * {@link #isLocationKnown()} is {@code false}.
         */
        @NonNull
        public Duration getLocationAge(long elapsedRealtimeMillis) {
            Location location = mPiiLoggableLocation.get();
            if (location == null) {
                throw new IllegalStateException();
            }
            long locationAgeMillis = elapsedRealtimeMillis
                    - NANOSECONDS.toMillis(location.getElapsedRealtimeNanos());
            return Duration.ofMillis(locationAgeMillis);
        }

        @Override
        public String toPiiString() {
            String template = toStringTemplate();
            return PiiLoggables.formatPiiString(template, mPiiLoggableLocation);
        }

        @Override
        public String toString() {
            String template = toStringTemplate();
            return String.format(template, mPiiLoggableLocation);
        }

        private String toStringTemplate() {
            return "LocationListeningResult{"
                    + "mListenMode=" + prettyPrintListenModeEnum(mListenMode)
                    + ", mListeningDuration=" + mListeningDuration
                    + ", mStartElapsedRealtimeMillis="
                    + formatElapsedRealtimeMillis(mStartElapsedRealtimeMillis)
                    + ", mResultElapsedRealtimeMillis="
                    + formatElapsedRealtimeMillis(mResultElapsedRealtimeMillis)
                    + ", mPiiLoggableLocation=%s"
                    + ", getTotalEstimatedTimeListening()=" + getTotalEstimatedTimeListening()
                    + '}';
        }
    }

    /**
     * Starts a continuous passive async location lookup.
     *
     * <p>The location referenced by the {@link LocationListeningResult} passed to {@code
     * locationResultConsumer} will never be {@code null}.
     *
     * <p>After (at least) {@code duration} has elapsed then listening will be
     * automatically halted and {@code passiveListeningCompletedCallback} will be called with
     * an estimate of elapsed time spent listening.
     *
     * <p>Returns a {@link Cancellable} that can be used to stop listening early, if called then
     * {@code passiveListeningCompletedCallback} will not be called
     *
     * @param duration the duration to listen for
     */
    @NonNull
    Cancellable startPassiveLocationListening(@NonNull Duration duration,
            @NonNull Consumer<LocationListeningResult> locationResultConsumer,
            @NonNull Consumer<Duration> passiveListeningCompletedCallback);

    /**
     * Starts a one-off, async, active location lookup. The location referenced by the {@link
     * LocationListeningResult} passed to {@code locationResultConsumer} can be {@code null}.
     * Returns a {@link Cancellable} that can be used to stop listening.
     *
     * @param duration the length of time to actively seek location
     */
    @NonNull
    Cancellable startActiveGetCurrentLocation(@NonNull Duration duration,
            @NonNull Consumer<LocationListeningResult> locationResultConsumer);

    /**
     * Returns an object that can be used to lookup time zones for a location.
     *
     * @throws IOException if there is a problem loading the tz geolocation data files
     */
    @NonNull
    GeoTimeZonesFinder createGeoTimeZoneFinder() throws IOException;

    /**
     * Used to report location time zone information.
     */
    void reportTimeZoneProviderResult(@NonNull TimeZoneProviderResult result);

    /**
     * Acquires a (partial) wake lock. Used to ensure that calculations using
     * {@link #elapsedRealtimeMillis()} are reasonably accurate.
     * See also {@link #releaseWakeLock()}.
     */
    void acquireWakeLock();

    /**
     * Releases the (partial) wake lock acquired with {@link #acquireWakeLock()}.
     */
    void releaseWakeLock();

    /**
     * Returns the current elapsed time in milliseconds. See {@link
     * SystemClock#elapsedRealtime()}.
     *
     * <p>If it is being used for age calculations then use {@link #acquireWakeLock()} and
     * {@link #releaseWakeLock()} to avoid dozing that might throw off calculations.
     */
    long elapsedRealtimeMillis();
}
