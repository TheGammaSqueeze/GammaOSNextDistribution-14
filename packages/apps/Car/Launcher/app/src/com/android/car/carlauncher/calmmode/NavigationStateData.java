/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.carlauncher.calmmode;

import android.car.cluster.navigation.NavigationState;
import android.icu.number.Notation;
import android.icu.number.NumberFormatter;
import android.icu.number.Precision;
import android.icu.util.MeasureUnit;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

public class NavigationStateData {
    private static final boolean DEBUG = Build.isDebuggable();
    private static final String TAG = NavigationStateData.class.getSimpleName();
    @NonNull
    private final String mTimeToDestination;

    private double mDistanceToDestination;
    @NonNull
    private MeasureUnit mDistanceUnit;
    private NavigationStateData(
            @NonNull String timeToDestination,
            double distanceToDestination,
            @NonNull MeasureUnit distanceUnit) {
        this.mTimeToDestination = timeToDestination;
        this.mDistanceToDestination = distanceToDestination;
        this.mDistanceUnit = distanceUnit;
    }

    /**
     * @return new {@link Builder} instance for creating {@link NavigationStateData}
     */
    @NonNull
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Trip Status is a string containing time and distance remaining to reach next destination
     * Example: "1hr 52 min | 100mi"
     * @param navigationState {@link NavigationStateData} to use for building Trip Status string
     * @param locale          {@link Locale} to build Trip Status string
     * @param separator       Separator used to separate time and distance
     * @return String representing trip status with time and distance to next destination, returns
     * null if arguments are invalid, uses {@link Locale#getDefault()} if {@code locale} is null
     */
    @Nullable
    public static String buildTripStatusString(
            @NonNull NavigationStateData navigationState, @NonNull Locale locale,
            @NonNull String separator) {

        if (navigationState == null
                || navigationState.getTimeToDestination() == null
                || navigationState.getDistanceUnit() == null) {
            if (DEBUG) {
                Log.w(
                        TAG,
                        "buildTripStatusString: invalid argument navigationState= "
                                + navigationState
                                + ", returning null");
            }
            return null;
        }
        if (locale == null) {
            if (DEBUG) {
                Log.w(TAG, "buildTripStatusString: locale is null, returning null");
            }
            locale = Locale.getDefault();
        }
        StringBuilder navStateTextBuilder = new StringBuilder();
        navStateTextBuilder.append(navigationState.getTimeToDestination());
        navStateTextBuilder.append(separator);
        navStateTextBuilder.append(NumberFormatter.withLocale(locale)
                        .notation(Notation.compactShort())
                        .precision(Precision.integer())
                        .unit(navigationState.getDistanceUnit())
                        .format(navigationState.getDistanceToDestination())
                        .toString());
        return navStateTextBuilder.toString();
    }

    @NonNull
    public String getTimeToDestination() {
        return mTimeToDestination;
    }

    public double getDistanceToDestination() {
        return mDistanceToDestination;
    }
    @NonNull
    public MeasureUnit getDistanceUnit() {
        return mDistanceUnit;
    }

    @Override
    public String toString() {
        return "NavigationStateData{"
                + " timeToDestination='" + mTimeToDestination + '\''
                + ", distanceToDestination=" + mDistanceToDestination
                + ", distanceUnit=" + mDistanceUnit
                + '}';
    }

    public static class Builder {
        private String mTimeToDestination;
        private double mDistanceToDestination;
        private MeasureUnit mDistanceUnit;

        /**
         * @param timeToDestination String representation of time remaining to next destination
         *     Example: 1hr 52min
         * @return {@link Builder} object
         */
        public Builder setTimeToDestination(@NonNull String timeToDestination) {
            this.mTimeToDestination = timeToDestination;
            return this;
        }

        /**
         * @param distanceToDestination String representation of distance remaining to next
         *     destination. Example: "20" This is designed to be compatible with {@link
         *     android.car.cluster.navigation.NavigationState.Distance}
         * @return {@link Builder} object
         * @throws NumberFormatException if the {@code distanceToDestination} is not a
         *     number
         */
        public Builder setDistanceToDestination(@NonNull String distanceToDestination)
                throws NumberFormatException {
            this.mDistanceToDestination = Double.parseDouble(distanceToDestination);
            return this;
        }

        /**
         * @param distanceUnit {@link NavigationState.Distance.Unit} Unit for distance
         * @return {@link Builder} object
         * @throws IllegalArgumentException if the {@code distanceUnit} is not a recognized
         *     {@link NavigationState.Distance.Unit}
         */
        public Builder setDistanceUnit(@NonNull NavigationState.Distance.Unit distanceUnit)
                throws IllegalArgumentException {
            MeasureUnit unit = null;
            switch (distanceUnit) {
                case FEET: unit = MeasureUnit.FOOT; break;
                case YARDS: unit = MeasureUnit.YARD; break;
                case KILOMETERS: unit = MeasureUnit.KILOMETER; break;
                case METERS: unit = MeasureUnit.METER; break;
                case MILES: unit = MeasureUnit.MILE; break;
                default: break;
            }
            if (unit == null) {
                throw new IllegalArgumentException("Unrecognized NavigationState.Distance.Unit, "
                        + "unable to create NavigationStateData.Builder");
            }
            this.mDistanceUnit = unit;
            return this;
        }

        /** Builds a {@link NavigationStateData}
         * @return {@link NavigationStateData} object
         */
        @NonNull
        public NavigationStateData build() {
            return new NavigationStateData(
                    mTimeToDestination, mDistanceToDestination, mDistanceUnit);
        }

    }

}
