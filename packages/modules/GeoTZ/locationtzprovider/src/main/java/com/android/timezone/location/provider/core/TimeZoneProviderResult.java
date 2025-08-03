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

import android.service.timezone.TimeZoneProviderService;
import android.service.timezone.TimeZoneProviderSuggestion;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * A result of a period of time zone detection.
 */
public final class TimeZoneProviderResult {

    @IntDef({ RESULT_TYPE_PERMANENT_FAILURE, RESULT_TYPE_SUGGESTION, RESULT_TYPE_UNCERTAIN })
    public @interface ResultType {}

    /**
     * The provider failed permanently. See {@link
     * TimeZoneProviderService#reportPermanentFailure(Throwable)}
     */
    public static final int RESULT_TYPE_PERMANENT_FAILURE = 1;

    /**
     * The provider made a suggestion. See {@link
     * TimeZoneProviderService#reportSuggestion(TimeZoneProviderSuggestion)}
     */
    public static final int RESULT_TYPE_SUGGESTION = 2;

    /**
     * The provider was uncertain about the time zone. See {@link
     * TimeZoneProviderService#reportUncertain()}
     */
    public static final int RESULT_TYPE_UNCERTAIN = 3;

    private static final TimeZoneProviderResult UNCERTAIN_RESULT =
            new TimeZoneProviderResult(RESULT_TYPE_UNCERTAIN, null, null);

    @ResultType
    private final int mType;

    @Nullable
    private final TimeZoneProviderSuggestion mSuggestion;

    @Nullable
    private final Throwable mFailureCause;

    private TimeZoneProviderResult(@ResultType int type,
            @Nullable TimeZoneProviderSuggestion suggestion,
            @Nullable Throwable failureCause) {
        mType = type;
        mSuggestion = suggestion;
        mFailureCause = failureCause;
    }

    /** Returns a result of type {@link #RESULT_TYPE_SUGGESTION}. */
    public static TimeZoneProviderResult createSuggestion(
            @NonNull TimeZoneProviderSuggestion suggestion) {
        return new TimeZoneProviderResult(RESULT_TYPE_SUGGESTION,
                Objects.requireNonNull(suggestion), null);
    }

    /** Returns a result of type {@link #RESULT_TYPE_UNCERTAIN}. */
    public static TimeZoneProviderResult createUncertain() {
        return UNCERTAIN_RESULT;
    }

    /** Returns a result of type {@link #RESULT_TYPE_PERMANENT_FAILURE}. */
    public static TimeZoneProviderResult createPermanentFailure(@NonNull Throwable cause) {
        return new TimeZoneProviderResult(RESULT_TYPE_PERMANENT_FAILURE, null,
                Objects.requireNonNull(cause));
    }

    /**
     * Returns the result type.
     */
    public @ResultType int getType() {
        return mType;
    }

    /**
     * Returns the suggestion. Populated for {@link #RESULT_TYPE_SUGGESTION}.
     */
    @NonNull
    public TimeZoneProviderSuggestion getSuggestion() {
        return mSuggestion;
    }

    /**
     * Returns the failure cause. Populated for {@link #RESULT_TYPE_PERMANENT_FAILURE}.
     */
    @Nullable
    public Throwable getFailureCause() {
        return mFailureCause;
    }

    @Override
    public String toString() {
        return "TimeZoneProviderResult{"
                + "mResultType=" + mType
                + ", mSuggestion=" + mSuggestion
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TimeZoneProviderResult that = (TimeZoneProviderResult) o;
        return mType == that.mType
                && Objects.equals(mSuggestion, that.mSuggestion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mType, mSuggestion);
    }
}
