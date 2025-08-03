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

package com.android.adservices.service.measurement.inputverification;

import android.annotation.NonNull;
import android.content.Context;
import android.hardware.input.InputManager;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VerifiedInputEvent;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.MeasurementClickVerificationStats;
import com.android.internal.annotations.VisibleForTesting;

import com.google.auto.value.AutoValue;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.util.concurrent.TimeUnit;

/** Class for handling navigation event verification. */
public class ClickVerifier {
    @NonNull private final InputManager mInputManager;
    @NonNull private final Flags mFlags;

    @NonNull private final AdServicesLogger mAdServicesLogger;

    @NonNull private LoadingCache<VerifiedInputEvent, Long> mVerifiedInputEventsPreviouslyUsed;
    @NonNull private LoadingCache<MotionEventWrapper, Long> mUnverifiedMotionEventsPreviouslyUsed;
    @NonNull private LoadingCache<KeyEventWrapper, Long> mUnverifiedKeyEventsPreviouslyUsed;

    public ClickVerifier(Context context) {
        mInputManager = context.getSystemService(InputManager.class);
        mFlags = FlagsFactory.getFlags();
        mAdServicesLogger = AdServicesLoggerImpl.getInstance();
        mVerifiedInputEventsPreviouslyUsed =
                CacheBuilder.newBuilder()
                        .expireAfterWrite(
                                mFlags.getMeasurementRegistrationInputEventValidWindowMs(),
                                TimeUnit.MILLISECONDS)
                        .build(
                                new CacheLoader<>() {
                                    @NonNull
                                    @Override
                                    public Long load(VerifiedInputEvent key) {
                                        return 0L;
                                    }
                                });

        mUnverifiedMotionEventsPreviouslyUsed =
                CacheBuilder.newBuilder()
                        .expireAfterWrite(
                                mFlags.getMeasurementRegistrationInputEventValidWindowMs(),
                                TimeUnit.MILLISECONDS)
                        .build(
                                new CacheLoader<>() {
                                    @NonNull
                                    @Override
                                    public Long load(MotionEventWrapper key) {
                                        return 0L;
                                    }
                                });

        mUnverifiedKeyEventsPreviouslyUsed =
                CacheBuilder.newBuilder()
                        .expireAfterWrite(
                                mFlags.getMeasurementRegistrationInputEventValidWindowMs(),
                                TimeUnit.MILLISECONDS)
                        .build(
                                new CacheLoader<>() {
                                    @NonNull
                                    @Override
                                    public Long load(KeyEventWrapper key) {
                                        return 0L;
                                    }
                                });
    }

    @VisibleForTesting
    ClickVerifier(
            @NonNull InputManager inputManager,
            @NonNull Flags flags,
            @NonNull AdServicesLogger adServicesLogger) {
        mInputManager = inputManager;
        mFlags = flags;
        mAdServicesLogger = adServicesLogger;
        mVerifiedInputEventsPreviouslyUsed =
                CacheBuilder.newBuilder()
                        .expireAfterWrite(
                                mFlags.getMeasurementRegistrationInputEventValidWindowMs(),
                                TimeUnit.MILLISECONDS)
                        .build(
                                new CacheLoader<>() {
                                    @NonNull
                                    @Override
                                    public Long load(VerifiedInputEvent key) {
                                        return 0L;
                                    }
                                });

        mUnverifiedMotionEventsPreviouslyUsed =
                CacheBuilder.newBuilder()
                        .expireAfterWrite(
                                mFlags.getMeasurementRegistrationInputEventValidWindowMs(),
                                TimeUnit.MILLISECONDS)
                        .build(
                                new CacheLoader<>() {
                                    @NonNull
                                    @Override
                                    public Long load(MotionEventWrapper key) {
                                        return 0L;
                                    }
                                });

        mUnverifiedKeyEventsPreviouslyUsed =
                CacheBuilder.newBuilder()
                        .expireAfterWrite(
                                mFlags.getMeasurementRegistrationInputEventValidWindowMs(),
                                TimeUnit.MILLISECONDS)
                        .build(
                                new CacheLoader<>() {
                                    @NonNull
                                    @Override
                                    public Long load(KeyEventWrapper key) {
                                        return 0L;
                                    }
                                });
    }

    /**
     * Checks if the {@link InputEvent} passed with a click registration can be verified. In order
     * for an InputEvent to be verified:
     *
     * <p>1. The event time of the InputEvent has to be within {@link
     * com.android.adservices.service.PhFlags#MEASUREMENT_REGISTRATION_INPUT_EVENT_VALID_WINDOW_MS }
     * of the API call.
     *
     * <p>2. The InputEvent has to be verified by the system {@link InputManager}.
     *
     * <p>3. The InputEvent must be used less than {@link
     * com.android.adservices.service.PhFlags#MEASUREMENT_MAX_SOURCES_PER_CLICK} times previously.
     *
     * @param event The InputEvent passed with the registration call.
     * @param registerTimestamp The time of the registration call.
     * @return Whether the InputEvent can be verified.
     */
    public boolean isInputEventVerifiable(
            @NonNull InputEvent event, long registerTimestamp, String sourceRegistrant) {
        boolean isInputEventVerified = true;
        MeasurementClickVerificationStats.Builder clickVerificationStatsBuilder =
                MeasurementClickVerificationStats.builder();
        clickVerificationStatsBuilder.setInputEventPresent(true);

        if (!isInputEventVerifiableBySystem(event, clickVerificationStatsBuilder)) {
            isInputEventVerified = false;
        }

        if (!isInputEventWithinValidTimeRange(
                registerTimestamp, event, clickVerificationStatsBuilder)) {
            isInputEventVerified = false;
        }

        if (!isInputEventUnderUsageLimit(event, clickVerificationStatsBuilder)) {
            isInputEventVerified = false;
        }

        clickVerificationStatsBuilder.setSourceType(
                (isInputEventVerified ? Source.SourceType.NAVIGATION : Source.SourceType.EVENT)
                        .getIntValue());
        clickVerificationStatsBuilder.setSourceRegistrant(sourceRegistrant);

        logClickVerificationStats(clickVerificationStatsBuilder, mAdServicesLogger);

        return isInputEventVerified;
    }

    /** Checks whether the InputEvent can be verified by the system. */
    @VisibleForTesting
    boolean isInputEventVerifiableBySystem(
            InputEvent event, MeasurementClickVerificationStats.Builder stats) {
        boolean isVerifiedBySystem = mInputManager.verifyInputEvent(event) != null;

        stats.setSystemClickVerificationEnabled(mFlags.getMeasurementIsClickVerifiedByInputEvent());
        stats.setSystemClickVerificationSuccessful(isVerifiedBySystem);
        return !mFlags.getMeasurementIsClickVerifiedByInputEvent() || isVerifiedBySystem;
    }

    /**
     * Checks whether the timestamp on the InputEvent and the time of the API call are within the
     * accepted range defined at {@link
     * com.android.adservices.service.PhFlags#MEASUREMENT_REGISTRATION_INPUT_EVENT_VALID_WINDOW_MS}
     */
    @VisibleForTesting
    boolean isInputEventWithinValidTimeRange(
            long registerTimestamp,
            InputEvent event,
            MeasurementClickVerificationStats.Builder stats) {
        long inputEventDelay = registerTimestamp - event.getEventTime();
        stats.setInputEventDelayMillis(inputEventDelay);
        stats.setValidDelayWindowMillis(mFlags.getMeasurementRegistrationInputEventValidWindowMs());
        return inputEventDelay <= mFlags.getMeasurementRegistrationInputEventValidWindowMs();
    }

    /**
     * Checks whether the provided InputEvent has been used fewer times than the limit defined at
     * {@link com.android.adservices.service.PhFlags#MEASUREMENT_MAX_SOURCES_PER_CLICK}
     */
    @VisibleForTesting
    boolean isInputEventUnderUsageLimit(
            InputEvent event, MeasurementClickVerificationStats.Builder stats) {
        stats.setClickDeduplicationEnabled(mFlags.getMeasurementIsClickDeduplicationEnabled());
        stats.setClickDeduplicationEnforced(mFlags.getMeasurementIsClickDeduplicationEnforced());
        stats.setMaxSourcesPerClick(mFlags.getMeasurementMaxSourcesPerClick());

        if (!mFlags.getMeasurementIsClickDeduplicationEnabled()) {
            stats.setCurrentRegistrationUnderClickDeduplicationLimit(/* value */ true);
            return true;
        }

        long numTimesPreviouslyUsed = 0;
        VerifiedInputEvent verifiedInputEvent = mInputManager.verifyInputEvent(event);
        if (verifiedInputEvent != null) {
            // VerifiedInputEvents will be equal even if the InputEvents passed to the InputManager
            // is not.
            numTimesPreviouslyUsed =
                    mVerifiedInputEventsPreviouslyUsed.getUnchecked(verifiedInputEvent);
            mVerifiedInputEventsPreviouslyUsed.put(verifiedInputEvent, numTimesPreviouslyUsed + 1);
        } else {
            // A copy of an InputEvent object won't be equal to the original. If we can't get a
            // VerifiedInputEvent, we have to use a wrapper class and compare the coordinates and
            // the down time for a MotionEvent or the key code and the downtime for a KeyEvent.
            if (event instanceof MotionEvent) {
                MotionEvent motionEvent = (MotionEvent) event;
                MotionEventWrapper unverifiedMotionEvent =
                        MotionEventWrapper.builder()
                                .setRawX(motionEvent.getRawX())
                                .setRawY(motionEvent.getRawY())
                                .setDownTime(motionEvent.getDownTime())
                                .setAction(motionEvent.getAction())
                                .build();
                numTimesPreviouslyUsed =
                        mUnverifiedMotionEventsPreviouslyUsed.getUnchecked(unverifiedMotionEvent);
                mUnverifiedMotionEventsPreviouslyUsed.put(
                        unverifiedMotionEvent, numTimesPreviouslyUsed + 1);
            } else if (event instanceof KeyEvent) {
                KeyEvent keyEvent = (KeyEvent) event;
                KeyEventWrapper unverifiedKeyEvent =
                        KeyEventWrapper.builder()
                                .setKeyCode(keyEvent.getKeyCode())
                                .setDownTime(keyEvent.getDownTime())
                                .setAction(keyEvent.getAction())
                                .build();
                numTimesPreviouslyUsed =
                        mUnverifiedKeyEventsPreviouslyUsed.getUnchecked(unverifiedKeyEvent);
                mUnverifiedKeyEventsPreviouslyUsed.put(
                        unverifiedKeyEvent, numTimesPreviouslyUsed + 1);
            }
        }

        stats.setCurrentRegistrationUnderClickDeduplicationLimit(
                (numTimesPreviouslyUsed < mFlags.getMeasurementMaxSourcesPerClick()));

        return !mFlags.getMeasurementIsClickDeduplicationEnforced()
                || (numTimesPreviouslyUsed < mFlags.getMeasurementMaxSourcesPerClick());
    }

    private void logClickVerificationStats(
            MeasurementClickVerificationStats.Builder stats, AdServicesLogger adServicesLogger) {
        adServicesLogger.logMeasurementClickVerificationStats(stats.build());
    }

    @AutoValue
    abstract static class MotionEventWrapper {
        abstract float rawX();

        abstract float rawY();

        abstract long downTime();

        abstract int action();

        static Builder builder() {
            return new AutoValue_ClickVerifier_MotionEventWrapper.Builder();
        }

        @AutoValue.Builder
        abstract static class Builder {
            abstract Builder setRawX(float value);

            abstract Builder setRawY(float value);

            abstract Builder setDownTime(long value);

            abstract Builder setAction(int value);

            abstract MotionEventWrapper build();
        }
    }

    @AutoValue
    abstract static class KeyEventWrapper {
        abstract int keyCode();

        abstract long downTime();

        abstract int action();

        static Builder builder() {
            return new AutoValue_ClickVerifier_KeyEventWrapper.Builder();
        }

        @AutoValue.Builder
        abstract static class Builder {
            abstract Builder setKeyCode(int value);

            abstract Builder setDownTime(long value);

            abstract Builder setAction(int value);

            abstract KeyEventWrapper build();
        }
    }
}
