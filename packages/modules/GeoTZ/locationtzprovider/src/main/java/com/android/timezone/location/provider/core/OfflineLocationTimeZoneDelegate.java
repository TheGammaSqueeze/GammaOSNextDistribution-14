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

import static com.android.timezone.location.provider.core.LogUtils.formatElapsedRealtimeMillis;
import static com.android.timezone.location.provider.core.LogUtils.formatUtcTime;
import static com.android.timezone.location.provider.core.LogUtils.logDebug;
import static com.android.timezone.location.provider.core.LogUtils.logWarn;
import static com.android.timezone.location.provider.core.Mode.MODE_DESTROYED;
import static com.android.timezone.location.provider.core.Mode.MODE_FAILED;
import static com.android.timezone.location.provider.core.Mode.MODE_STARTED;
import static com.android.timezone.location.provider.core.Mode.MODE_STOPPED;
import static com.android.timezone.location.provider.core.Mode.prettyPrintListenModeEnum;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import android.location.Location;
import android.service.timezone.TimeZoneProviderSuggestion;

import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.timezone.location.common.PiiLoggable;
import com.android.timezone.location.common.PiiLoggables;
import com.android.timezone.location.lookup.GeoTimeZonesFinder;
import com.android.timezone.location.lookup.GeoTimeZonesFinder.LocationToken;
import com.android.timezone.location.provider.core.Environment.LocationListeningResult;
import com.android.timezone.location.provider.core.LocationListeningAccountant.ListeningInstruction;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * A class encapsulating the time zone detection logic for an Offline location-based
 * {@link android.service.timezone.TimeZoneProviderService}. It has been decoupled from the Android
 * environment and many API via the {@link Environment} interface to enable easier unit testing.
 *
 * <p>The overall goal of this class is to balance power consumption with responsiveness.
 *
 * <p>Implementation details:
 *
 * <p>The instance interacts with multiple threads, but state changes occur in a single-threaded
 * manner through the use of a lock object, {@link #mLock}. Because multiple threads are involved,
 * service lifecycle calls like {@link #onDestroy()} may be invoked by different threads than binder
 * calls like {@link #onStopUpdates()}, leading to unintuitive ordering. See
 * {@link android.service.timezone.TimeZoneProviderService} for details.
 *
 * <p>There are two listening modes:
 * <ul>
 * <li>{@link #LOCATION_LISTEN_MODE_PASSIVE}: used most of the time and consumes a negligible amount
 * of power. It provides an indication of current location but no indication of when location is
 * unknown.</li>
 * <li>{@link #LOCATION_LISTEN_MODE_ACTIVE}: used in short bursts, is expected to be high power (may
 * use GNSS hardware), though it may also not cost much, depending on user settings. This mode
 * obtains a single location or an "unknown location" response.</li>
 * </ul>
 *
 * <p>When first started, the provider is given an initialization timeout. It is expected to produce
 * a time zone suggestion within this period. The timeout is configured and the {@link
 * #mInitializationTimeoutCancellable} field is set. The timeout is cancelled / cleared if a
 * location is determined (or if the provider is stopped). If the timeout is left to trigger, an
 * uncertain suggestion is made.
 *
 * <p>The provider starts in {@link #LOCATION_LISTEN_MODE_ACTIVE} and remains in that mode for a
 * short duration. Independently of whether a location is detected, the provider always moves from
 * {@link #LOCATION_LISTEN_MODE_ACTIVE} to {@link #LOCATION_LISTEN_MODE_PASSIVE}.
 *
 * <p>When entering {@link #LOCATION_LISTEN_MODE_PASSIVE}, a mode timeout is set. If a location is
 * detected while in {@link #LOCATION_LISTEN_MODE_PASSIVE}, the provider may stay in {@link
 * #LOCATION_LISTEN_MODE_PASSIVE}. If no location has been detected, then the provider may move into
 * into {@link #LOCATION_LISTEN_MODE_ACTIVE}.
 *
 * <p>Generally, when the location is determined, the time zones for the location are looked
 * up and an {@link TimeZoneProviderResult result} is submitted via {@link
 * Environment#reportTimeZoneProviderResult(TimeZoneProviderResult)}. When a location cannot be
 * determined and a suggestion is required, a {@link TimeZoneProviderResult#RESULT_TYPE_UNCERTAIN}
 * {@link TimeZoneProviderResult result} is submitted.
 */
public final class OfflineLocationTimeZoneDelegate {

    @IntDef({ LOCATION_LISTEN_MODE_ACTIVE, LOCATION_LISTEN_MODE_PASSIVE })
    public @interface ListenModeEnum {}

    /** Use when location listen mode is not applicable. */
    @ListenModeEnum
    public static final int LOCATION_LISTEN_MODE_NA = 0;

    /** Actively listen for a location, once, for a short period. */
    @ListenModeEnum
    public static final int LOCATION_LISTEN_MODE_ACTIVE = 1;

    /** Passively listen for a location until cancelled, possibly for a long period. */
    @ListenModeEnum
    public static final int LOCATION_LISTEN_MODE_PASSIVE = 2;

    @NonNull
    private final Environment mEnvironment;

    @NonNull
    private final LocationListeningAccountant mLocationListeningAccountant;

    private final Object mLock = new Object();

    /** The current mode of the provider. See {@link Mode} for details. */
    @GuardedBy("mLock")
    private final ReferenceWithHistory<Mode> mCurrentMode =
            new ReferenceWithHistory<>(10, PiiLoggables.toPiiStringFunction());

    /**
     * The last location listening result. Holds {@code null} if location listening hasn't started
     * or produced a result yet. The {@link LocationListeningResult#getLocation()} value can be
     * {@code null} when location is uncertain.
     */
    @GuardedBy("mLock")
    private final ReferenceWithHistory<LocationListeningResult> mLastLocationListeningResult =
            new ReferenceWithHistory<>(10, PiiLoggables.toPiiStringFunction());

    /**
     * A token associated with the last location time zone lookup. Used to avoid unnecessary time
     * zone lookups. Can be {@code null} when location is uncertain.
     */
    @GuardedBy("mLock")
    @Nullable
    private LocationToken mLastLocationToken;

    /**
     * The last time zone provider result determined by the provider. This is used to determine
     * whether suggestions need to be made to revoke a previous suggestion. It is cleared when the
     * provider stops.
     */
    @GuardedBy("mLock")
    private final ReferenceWithHistory<TimeZoneProviderResult> mLastTimeZoneProviderResult =
            new ReferenceWithHistory<>(10);

    /**
     * Indicates whether the provider is still within its initialization period. When it times out,
     * if no suggestion has yet been made then an uncertain suggestion must be made. The reference
     * can (and must) be used to cancel the timeout if it is no longer required. The reference
     * must be cleared to indicate the initialization period is over.
     */
    @GuardedBy("mLock")
    @Nullable
    private Cancellable mInitializationTimeoutCancellable;

    /** Creates a new instance that uses the supplied {@link Environment}. */
    @NonNull
    public static OfflineLocationTimeZoneDelegate create(@NonNull Environment environment) {
        return new OfflineLocationTimeZoneDelegate(environment);
    }

    // @VisibleForTesting
    OfflineLocationTimeZoneDelegate(@NonNull Environment environment) {
        mEnvironment = Objects.requireNonNull(environment);
        mLocationListeningAccountant = environment.getLocationListeningAccountant();

        synchronized (mLock) {
            mCurrentMode.set(Mode.createStoppedMode());
        }
    }

    /** Called during {@link android.service.timezone.TimeZoneProviderService#onDestroy}. */
    public void onDestroy() {
        PiiLoggable entryCause = PiiLoggables.fromString("onDestroy() called");
        logDebug(entryCause);

        synchronized (mLock) {
            cancelTimeoutsAndLocationCallbacks();

            Mode currentMode = mCurrentMode.get();
            if (currentMode.mModeEnum == MODE_STARTED) {
                sendTimeZoneUncertainResultIfNeeded();
            }
            // The current mode can be set to MODE_DESTROYED in all cases, even from MODE_FAILED.
            Mode newMode = new Mode(MODE_DESTROYED, entryCause);
            mCurrentMode.set(newMode);
        }
    }

    /** Called during {@link android.service.timezone.TimeZoneProviderService#onStartUpdates}. */
    public void onStartUpdates(@NonNull Duration initializationTimeout) {
        Objects.requireNonNull(initializationTimeout);

        PiiLoggable debugInfo = PiiLoggables.fromString("onStartUpdates(),"
                + " initializationTimeout=" + initializationTimeout);
        logDebug(debugInfo);

        synchronized (mLock) {
            Mode currentMode = mCurrentMode.get();
            if (currentMode.mModeEnum == MODE_STOPPED) {
                enterStartedMode(initializationTimeout, debugInfo);
            } else {
                logWarn("Unexpected onStarted() received when in currentMode=" + currentMode);
            }
        }
    }

    /** Called during {@link android.service.timezone.TimeZoneProviderService#onStopUpdates}. */
    public void onStopUpdates() {
        PiiLoggable debugInfo = PiiLoggables.fromString("onStopUpdates()");
        logDebug(debugInfo);

        synchronized (mLock) {
            Mode currentMode = mCurrentMode.get();
            if (currentMode.mModeEnum == MODE_STARTED) {
                enterStoppedMode(debugInfo);
            } else if (currentMode.mModeEnum == MODE_DESTROYED) {
                // This can happen because onDestroy() and onStopUpdates() are handled by different
                // threads: it is still logged, but at a lower priority than other unexpected
                // transitions.
                logDebug("Unexpected onStopUpdates() when currentMode=" + currentMode);
            } else {
                logWarn("Unexpected onStopUpdates() when currentMode=" + currentMode);
            }
        }
    }

    /** Called during {@link android.service.timezone.TimeZoneProviderService#dump}. */
    public void dump(@NonNull PrintWriter pw) {
        synchronized (mLock) {
            // Output useful "current time" information to help with debugging.
            pw.println("System clock=" + formatUtcTime(System.currentTimeMillis()));
            pw.println("Elapsed realtime clock="
                    + formatElapsedRealtimeMillis(mEnvironment.elapsedRealtimeMillis()));

            // State and constants.
            pw.println("mInitializationTimeoutCancellable=" + mInitializationTimeoutCancellable);
            pw.println("mLocationListeningAccountant=" + mLocationListeningAccountant);
            String locationTokenString =
                    mLastLocationToken == null ? "null" : mLastLocationToken.toPiiString();
            pw.println("mLastLocationToken=" + locationTokenString);
            pw.println();
            pw.println("Mode history:");
            mCurrentMode.dump(pw);
            pw.println();
            pw.println("Location history:");
            mLastLocationListeningResult.dump(pw);
            pw.println();
            pw.println("TimeZoneProviderResult history:");
            mLastTimeZoneProviderResult.dump(pw);
        }
    }

    /** Returns the current mode. Only intended for use in tests. */
    public int getCurrentModeEnumForTests() {
        synchronized (mLock) {
            return mCurrentMode.get().mModeEnum;
        }
    }

    /**
     * Handles a {@link LocationListeningResult} from a period of active listening. The result may
     * contain a location or {@code null}.
     */
    private void onActiveListeningResult(@NonNull LocationListeningResult activeListeningResult) {
        synchronized (mLock) {
            Mode currentMode = mCurrentMode.get();
            if (currentMode.mModeEnum != MODE_STARTED
                    || currentMode.mListenMode != LOCATION_LISTEN_MODE_ACTIVE) {
                String unexpectedStateDebugInfo = "Unexpected call to onActiveListeningResult(),"
                        + " activeListeningResult=" + activeListeningResult
                        + ", currentMode=" + currentMode;
                reportUnexpectedLocationCallback(unexpectedStateDebugInfo);
                return;
            }

            PiiLoggable debugInfo = PiiLoggables.fromTemplate("onActiveListeningResult(),"
                            + " activeListeningResult=%s", activeListeningResult);
            logDebug(debugInfo);

            // Recover any active listening budget we didn't use.
            Duration timeListening = activeListeningResult.getTotalEstimatedTimeListening();
            Duration activeListeningDuration =
                    activeListeningResult.getRequestedListeningDuration();
            Duration activeListeningDurationNotUsed = activeListeningDuration.minus(timeListening);
            if (!activeListeningDurationNotUsed.isNegative()) {
                mLocationListeningAccountant.depositActiveListeningAmount(
                        activeListeningDurationNotUsed);
            }

            // Handle the result.
            if (activeListeningResult.isLocationKnown()) {
                handleLocationKnown(activeListeningResult);
            } else {
                handleLocationNotKnown(activeListeningResult);
            }

            // Active listening returns only a single location and self-cancels so we need to start
            // listening again.
            startNextLocationListening(debugInfo);
        }
    }

    /**
     * Accepts the current location from passive listening.
     */
    private void onPassiveListeningResult(@NonNull LocationListeningResult passiveListeningResult) {
        synchronized (mLock) {
            Mode currentMode = mCurrentMode.get();
            if (currentMode.mModeEnum != MODE_STARTED
                    || currentMode.mListenMode != LOCATION_LISTEN_MODE_PASSIVE) {
                String unexpectedStateDebugInfo = "Unexpected call to onPassiveListeningResult(),"
                        + " passiveListeningResult=" + passiveListeningResult
                        + ", currentMode=" + currentMode;
                reportUnexpectedLocationCallback(unexpectedStateDebugInfo);
                return;
            }
            logDebug("onPassiveListeningResult()"
                    + ", passiveListeningResult=" + passiveListeningResult);

            handleLocationKnown(passiveListeningResult);
        }
    }

    @GuardedBy("mLock")
    private void handleLocationKnown(@NonNull LocationListeningResult locationResult) {
        Objects.requireNonNull(locationResult);
        Objects.requireNonNull(locationResult.getLocation());

        mLastLocationListeningResult.set(locationResult);

        // Receiving a location means we will definitely send a suggestion, so the initialization
        // timeout is not required. This is a no-op if the initialization timeout is already
        // cancelled.
        cancelInitializationTimeout();

        Mode currentMode = mCurrentMode.get();
        PiiLoggable debugInfo = PiiLoggables.fromTemplate(
                "handleLocationKnown(), locationResult=%s"
                + ", currentMode.mListenMode=" + prettyPrintListenModeEnum(currentMode.mListenMode),
                locationResult);
        logDebug(debugInfo);

        try {
            sendTimeZoneCertainResultIfNeeded(locationResult.getLocation());
        } catch (IOException e) {
            // This should never happen.
            PiiLoggable lookupFailureDebugInfo = PiiLoggables.fromTemplate(
                    "IOException while looking up location. previous debugInfo=%s", debugInfo);
            logWarn(lookupFailureDebugInfo, e);

            enterFailedMode(new IOException(lookupFailureDebugInfo.toString(), e),
                    lookupFailureDebugInfo);
        }
    }

    /**
     * Handles an explicit location not known. This can only happen with active listening; passive
     * only returns non-null locations.
     */
    @GuardedBy("mLock")
    private void handleLocationNotKnown(@NonNull LocationListeningResult locationResult) {
        Objects.requireNonNull(locationResult);
        if (locationResult.isLocationKnown()) {
            throw new IllegalArgumentException();
        }

        mLastLocationListeningResult.set(locationResult);

        Mode currentMode = mCurrentMode.get();
        String debugInfo = "handleLocationNotKnown()"
                + ", currentMode.mListenMode=" + prettyPrintListenModeEnum(currentMode.mListenMode);
        logDebug(debugInfo);

        sendTimeZoneUncertainResultIfNeeded();
    }

    /**
     * Handles a passive listening period ending naturally, i.e. not cancelled.
     *
     * @param duration the duration that listening took place for
     */
    private void onPassiveListeningEnded(@NonNull Duration duration) {
        PiiLoggable debugInfo = PiiLoggables.fromString(
                "onPassiveListeningEnded(), duration=" + duration);
        logDebug(debugInfo);

        synchronized (mLock) {
            Mode currentMode = mCurrentMode.get();
            if (currentMode.mModeEnum != MODE_STARTED
                    || currentMode.mListenMode != LOCATION_LISTEN_MODE_PASSIVE) {
                reportUnexpectedLocationCallback("Unexpected call to onPassiveListeningEnded()"
                        + ", currentMode=" + currentMode);
                return;
            }

            // Track how long passive listening took place since this is what allows us to
            // actively listen.
            mLocationListeningAccountant.accrueActiveListeningBudget(duration);

            // Begin the next period of listening.
            startNextLocationListening(debugInfo);
        }
    }

    /**
     * Handles the timeout callback that fires when initialization period has elapsed without a
     * location being detected.
     */
    private void onInitializationTimeout(@NonNull String timeoutToken) {
        synchronized (mLock) {
            Mode currentMode = mCurrentMode.get();
            String debugInfo = "onInitializationTimeout() timeoutToken=" + timeoutToken
                    + ", currentMode=" + currentMode;
            logDebug(debugInfo);

            mInitializationTimeoutCancellable = null;

            // If the initialization timeout has been allowed to trigger without being cancelled
            // then that should mean no location has been detected during the initialization period
            // and the provider must declare it is uncertain.
            if (mLastTimeZoneProviderResult.get() == null) {
                TimeZoneProviderResult result = TimeZoneProviderResult.createUncertain();
                reportTimeZoneProviderResultInternal(result, null /* locationToken */);
            }
        }
    }

    /** Cancels the initialization timeout, if it is still set. */
    @GuardedBy("mLock")
    private void cancelInitializationTimeout() {
        if (mInitializationTimeoutCancellable != null) {
            mInitializationTimeoutCancellable.cancel();
            mInitializationTimeoutCancellable = null;
        }
    }

    @GuardedBy("mLock")
    private void sendTimeZoneCertainResultIfNeeded(@NonNull Location location)
            throws IOException {
        try (GeoTimeZonesFinder geoTimeZonesFinder = mEnvironment.createGeoTimeZoneFinder()) {
            // Convert the location to a LocationToken.
            LocationToken locationToken = geoTimeZonesFinder.createLocationTokenForLatLng(
                    location.getLatitude(), location.getLongitude());

            // If the location token is the same as the last lookup, there is no need to do the
            // lookup / send another suggestion.
            if (locationToken.equals(mLastLocationToken)) {
                logDebug("Location token has not changed.");
            } else {
                List<String> tzIds =
                        geoTimeZonesFinder.findTimeZonesForLocationToken(locationToken);
                logDebug("tzIds found for locationToken=" + locationToken + ", tzIds=" + tzIds);
                // Rather than use the current elapsed realtime clock, use the time associated with
                // the location since that gives a more accurate answer.
                long elapsedRealtimeMillis =
                        NANOSECONDS.toMillis(location.getElapsedRealtimeNanos());
                TimeZoneProviderSuggestion suggestion = new TimeZoneProviderSuggestion.Builder()
                        .setTimeZoneIds(tzIds)
                        .setElapsedRealtimeMillis(elapsedRealtimeMillis)
                        .build();

                TimeZoneProviderResult result =
                        TimeZoneProviderResult.createSuggestion(suggestion);
                reportTimeZoneProviderResultInternal(result, locationToken);
            }
        }
    }

    @GuardedBy("mLock")
    private void sendTimeZoneUncertainResultIfNeeded() {
        TimeZoneProviderResult lastResult = mLastTimeZoneProviderResult.get();

        if (mInitializationTimeoutCancellable != null) {
            // If we're within the initialization timeout period, then the provider doesn't report
            // uncertainty. When the initialization timeout triggers, then an uncertain suggestion
            // will be sent if it's needed.
            return;
        }

        // If the last result was uncertain, there is no need to send another.
        if (lastResult == null
                || lastResult.getType() != TimeZoneProviderResult.RESULT_TYPE_UNCERTAIN) {
            TimeZoneProviderResult result = TimeZoneProviderResult.createUncertain();
            reportTimeZoneProviderResultInternal(result, null /* locationToken */);
        } else {
            logDebug("sendTimeZoneUncertainResultIfNeeded(): Last result=" + lastResult
                    + ", no need to send another.");
        }
    }

    @GuardedBy("mLock")
    private void sendPermanentFailureResult(@NonNull Throwable cause) {
        TimeZoneProviderResult result = TimeZoneProviderResult.createPermanentFailure(cause);
        reportTimeZoneProviderResultInternal(result, null /* locationToken */);
    }

    @GuardedBy("mLock")
    private void reportTimeZoneProviderResultInternal(
            @NonNull TimeZoneProviderResult result,
            @Nullable LocationToken locationToken) {
        mLastTimeZoneProviderResult.set(result);
        mLastLocationToken = locationToken;
        mEnvironment.reportTimeZoneProviderResult(result);
    }

    @GuardedBy("mLock")
    private void clearLocationState() {
        mLastLocationListeningResult.set(null);
        mLastLocationToken = null;
        mLastTimeZoneProviderResult.set(null);
    }

    /** Called when leaving the current mode to cancel all pending asynchronous operations. */
    @GuardedBy("mLock")
    private void cancelTimeoutsAndLocationCallbacks() {
        cancelInitializationTimeout();

        Mode currentMode = mCurrentMode.get();
        currentMode.cancelLocationListening();
    }

    @GuardedBy("mLock")
    private void reportUnexpectedLocationCallback(@NonNull String debugInfo) {
        // Unexpected location callbacks can occur when location listening is cancelled, but a
        // location is already available (e.g. the callback is already invoked but blocked or
        // sitting in a handler queue). This is logged but generally ignored.
        logDebug(debugInfo);
    }

    @GuardedBy("mLock")
    private void enterStartedMode(
            @NonNull Duration initializationTimeout, @NonNull PiiLoggable entryCause) {
        Objects.requireNonNull(initializationTimeout);
        Objects.requireNonNull(entryCause);

        // The request contains the initialization time in which the LTZP is given to provide the
        // first result. We set a timeout to try to ensure that we do send a result.
        String initializationToken = "initialization:" + initializationTimeout + "@"
                + formatElapsedRealtimeMillis(mEnvironment.elapsedRealtimeMillis());
        mInitializationTimeoutCancellable = mEnvironment.requestDelayedCallback(
                this::onInitializationTimeout, initializationToken,
                initializationTimeout);

        startNextLocationListening(entryCause);
    }

    @GuardedBy("mLock")
    private void enterFailedMode(@NonNull Throwable failure, @NonNull PiiLoggable entryCause) {
        logDebug(entryCause);

        cancelTimeoutsAndLocationCallbacks();

        // Avoid a transition from MODE_DESTROYED -> MODE_FAILED.
        if (mCurrentMode.get().mModeEnum != MODE_DESTROYED) {
            sendPermanentFailureResult(failure);

            Mode newMode = new Mode(MODE_FAILED, entryCause);
            mCurrentMode.set(newMode);
        }
    }

    @GuardedBy("mLock")
    private void enterStoppedMode(@NonNull PiiLoggable entryCause) {
        logDebug("Provider entering stopped mode, entryCause=" + entryCause);

        cancelTimeoutsAndLocationCallbacks();

        // Clear all location-derived state. The provider may be stopped due to the current user
        // changing.
        clearLocationState();

        Mode newMode = new Mode(MODE_STOPPED, entryCause);
        mCurrentMode.set(newMode);
    }

    @GuardedBy("mLock")
    private void startNextLocationListening(@NonNull PiiLoggable entryCause) {
        logDebug("Provider entering location listening mode entryCause=" + entryCause);

        Mode currentMode = mCurrentMode.get();
        // This method is safe to call on any mode, even when the mode doesn't use it.
        currentMode.cancelLocationListening();

        // Obtain the instruction for what mode to use.
        ListeningInstruction nextModeInstruction;
        try {
            // Hold a wake lock to prevent doze while the accountant does elapsed realtime millis
            // calculations for things like last location result age, etc. and start the next
            // period of listening.
            mEnvironment.acquireWakeLock();

            long elapsedRealtimeMillis = mEnvironment.elapsedRealtimeMillis();
            nextModeInstruction = mLocationListeningAccountant.getNextListeningInstruction(
                    elapsedRealtimeMillis, mLastLocationListeningResult.get());

            Cancellable listeningCancellable;
            if (nextModeInstruction.listenMode == LOCATION_LISTEN_MODE_PASSIVE) {
                listeningCancellable = mEnvironment.startPassiveLocationListening(
                        nextModeInstruction.duration,
                        this::onPassiveListeningResult,
                        this::onPassiveListeningEnded);
            } else {
                listeningCancellable = mEnvironment.startActiveGetCurrentLocation(
                        nextModeInstruction.duration, this::onActiveListeningResult);
            }
            Mode newMode = new Mode(
                    MODE_STARTED, entryCause, nextModeInstruction.listenMode, listeningCancellable);
            mCurrentMode.set(newMode);
        } finally {
            mEnvironment.releaseWakeLock();
        }
    }
}
