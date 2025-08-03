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
import static com.android.timezone.location.provider.core.OfflineLocationTimeZoneDelegate.LOCATION_LISTEN_MODE_ACTIVE;
import static com.android.timezone.location.provider.core.OfflineLocationTimeZoneDelegate.LOCATION_LISTEN_MODE_NA;
import static com.android.timezone.location.provider.core.OfflineLocationTimeZoneDelegate.LOCATION_LISTEN_MODE_PASSIVE;

import android.os.SystemClock;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.timezone.location.common.PiiLoggable;
import com.android.timezone.location.common.PiiLoggables;
import com.android.timezone.location.provider.core.OfflineLocationTimeZoneDelegate.ListenModeEnum;

import java.util.Objects;

/**
 * Holds state associated with the {@link OfflineLocationTimeZoneDelegate}'s mode. This class exists
 * to make it clear during debugging what transitions the {@link OfflineLocationTimeZoneDelegate}
 * has gone through to arrive at its current state.
 *
 * <p>This class is not thread safe: {@link OfflineLocationTimeZoneDelegate} handles all
 * synchronization.
 *
 * <p>See the docs for each {@code MODE_} constant for an explanation of each mode.
 *
 * <pre>
 * The initial mode is {@link #MODE_STOPPED}.
 *
 * Valid transitions:
 *
 * {@link #MODE_STOPPED}
 *   -> {@link #MODE_STARTED}
 *       - when the LTZP first receives a "start" request it starts listening for the current
 *         location.
 * {@link #MODE_STARTED}
 *   -> {@link #MODE_STOPPED}
 *       - when the system server sends a "stopped" request it stops listening for the current
 *         location.
 *
 * {States except MODE_DESTROYED}
 *   -> {@link #MODE_FAILED} (terminal state)
 *       - when there is a fatal error.
 * {All states}
 *   -> {@link #MODE_DESTROYED} (terminal state)
 *       - when the provider's service is destroyed, perhaps as part of the current user changing
 * </pre>
 */
class Mode implements PiiLoggable {

    @IntDef({ MODE_STOPPED, MODE_STARTED, MODE_FAILED, MODE_DESTROYED })
    @interface ModeEnum {}

    /**
     * An inactive state. The LTZP may not have received a request yet, or it has and the LTZP has
     * been explicitly stopped.
     */
    @ModeEnum
    static final int MODE_STOPPED = 1;

    /**
     * The LTZP has been started by the system server, and is listening for the current location.
     * The {@link #mListenMode} records the current type of listening.
     */
    @ModeEnum
    static final int MODE_STARTED = 2;

    /**
     * The LTZP's service has been destroyed.
     */
    @ModeEnum
    static final int MODE_DESTROYED = 3;

    /**
     * The LTZP encountered a failure it cannot recover from.
     */
    @ModeEnum
    static final int MODE_FAILED = 4;

    /** The current mode. */
    @ModeEnum
    final int mModeEnum;

    /**
     * The current location listen mode. Only used when mModeEnum == {@link #MODE_STARTED}.
     */
    final @ListenModeEnum int mListenMode;

    /**
     * Debug information: The elapsed realtime recorded when the mode was created.
     */
    private final long mCreationElapsedRealtimeMillis;

    /**
     * Debug information: Information about why the mode was entered.
     */
    @NonNull
    private final PiiLoggable mEntryCause;

    /**
     * Used when mModeEnum == {@link #MODE_STARTED}. The {@link Cancellable} that can be
     * used to stop listening for the current location.
     */
    @Nullable
    private final Cancellable mLocationListenerCancellable;

    Mode(@ModeEnum int modeEnum, @NonNull PiiLoggable entryCause) {
        this(modeEnum, entryCause, LOCATION_LISTEN_MODE_NA, null);
    }

    Mode(@ModeEnum int modeEnum, @NonNull PiiLoggable entryCause, @ListenModeEnum int listenMode,
            @Nullable Cancellable listeningCancellable) {
        mModeEnum = validateModeEnum(modeEnum);
        mListenMode = validateListenModeEnum(modeEnum, listenMode);
        mEntryCause = Objects.requireNonNull(entryCause);
        mLocationListenerCancellable = listeningCancellable;

        // Information useful for logging / debugging.
        mCreationElapsedRealtimeMillis = SystemClock.elapsedRealtime();
    }

    /** Returns the stopped mode which is the starting state for a provider. */
    @NonNull
    static Mode createStoppedMode() {
        return new Mode(MODE_STOPPED, PiiLoggables.fromString("init") /* entryCause */);
    }

    /**
     * If this mode is associated with location listening, this invokes {@link
     * Cancellable#cancel()}. If this mode is not associated with location listening, this is a
     * no-op.
     */
    void cancelLocationListening() {
        if (mLocationListenerCancellable != null) {
            mLocationListenerCancellable.cancel();
        }
    }

    @Override
    public String toPiiString() {
        String template = toStringTemplate();
        return PiiLoggables.formatPiiString(template, mEntryCause);
    }

    @Override
    public String toString() {
        String template = toStringTemplate();
        return String.format(template, mEntryCause);
    }

    private String toStringTemplate() {
        return "Mode{"
                + "mModeEnum=" + prettyPrintModeEnum(mModeEnum)
                + ", mListenMode=" + prettyPrintListenModeEnum(mListenMode)
                + ", mCreationElapsedRealtimeMillis="
                + formatElapsedRealtimeMillis(mCreationElapsedRealtimeMillis)
                + ", mEntryCause={%s}"
                + ", mLocationListenerCancellable=" + mLocationListenerCancellable
                + '}';
    }

    /** Returns a string representation of the {@link ModeEnum} value provided. */
    static String prettyPrintModeEnum(@ModeEnum int modeEnum) {
        switch (modeEnum) {
            case MODE_STOPPED:
                return "MODE_STOPPED";
            case MODE_STARTED:
                return "MODE_STARTED";
            case MODE_DESTROYED:
                return "MODE_DESTROYED";
            case MODE_FAILED:
                return "MODE_FAILED";
            default:
                return modeEnum + " (Unknown)";
        }
    }

    /** Returns a string representation of the {@link ListenModeEnum} value provided. */
    static String prettyPrintListenModeEnum(@ListenModeEnum int listenMode) {
        switch (listenMode) {
            case LOCATION_LISTEN_MODE_NA:
                return "LISTEN_MODE_NA";
            case LOCATION_LISTEN_MODE_ACTIVE:
                return "LOCATION_LISTEN_MODE_ACTIVE";
            case LOCATION_LISTEN_MODE_PASSIVE:
                return "LOCATION_LISTEN_MODE_PASSIVE";
            default:
                return listenMode + " (Unknown)";
        }
    }

    private static @ModeEnum int validateModeEnum(@ModeEnum int modeEnum) {
        if (modeEnum < MODE_STOPPED || modeEnum > MODE_FAILED) {
            throw new IllegalArgumentException("modeEnum=" + modeEnum);
        }
        return modeEnum;
    }

    private static @ListenModeEnum int validateListenModeEnum(
            @ModeEnum int modeEnum, @ListenModeEnum int listenMode) {
        if (modeEnum == MODE_STARTED) {
            if (listenMode != LOCATION_LISTEN_MODE_ACTIVE
                    && listenMode != LOCATION_LISTEN_MODE_PASSIVE) {
                throw new IllegalArgumentException();
            }
        } else {
            if (listenMode != LOCATION_LISTEN_MODE_NA) {
                throw new IllegalArgumentException();
            }
        }
        return listenMode;
    }
}
