/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.deviceconfig;

import static com.android.server.deviceconfig.Flags.enableCustomRebootTimeConfigurations;

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.deviceconfig.resources.R;

import java.util.Optional;

/**
 * Contains the timing configuration for unattended reboot.
 *
 * @hide
 */
public class RebootTimingConfiguration {
    private static final String RESOURCES_PACKAGE =
        "com.android.server.deviceconfig.resources";

    /**
     * Special value that can be set for both the window start and end hour configs to disable
     * reboot time window constraint.
     */
    private static final int ALLOW_ALL_HOURS = -1;

    private static final Pair<Integer, Integer> DEFAULT_REBOOT_WINDOW_HOURS = Pair.create(3, 5);

    private static final int DEFAULT_REBOOT_FREQUENCY_DAYS = 2;

    private final Optional<Pair<Integer, Integer>> mRebootWindowStartEndHour;
    private final int mRebootFrequencyDays;

    public RebootTimingConfiguration(Context context) {
        if (enableCustomRebootTimeConfigurations()) {
            Context resourcesContext = getResourcesContext(context);
            if (resourcesContext != null) {
                Resources res = resourcesContext.getResources();
                mRebootWindowStartEndHour = getRebootWindowStartEndHour(res);
                mRebootFrequencyDays = getRebootFrequencyDays(res);
                return;
            }
        }

        mRebootWindowStartEndHour = Optional.of(DEFAULT_REBOOT_WINDOW_HOURS);
        mRebootFrequencyDays = DEFAULT_REBOOT_FREQUENCY_DAYS;
    }

    @VisibleForTesting
    RebootTimingConfiguration(
            int rebootWindowStartHour, int rebootWindowEndHour, int rebootFrequencyDays) {
        mRebootWindowStartEndHour =
                getRebootWindowStartEndHour(rebootWindowStartHour, rebootWindowEndHour);
        assert(isDayValid(rebootFrequencyDays));
        mRebootFrequencyDays = rebootFrequencyDays;
    }

    /**
     * Returns a {@link Pair} of integers, where the first element represents the reboot window
     * start hour (inclusive), and the second element represents the reboot window end hour
     * (exclusive). If the start hour is bigger than the end hour, it means that the end hour falls
     * on the next day.
     *
     * <p>Returns an empty {@link Optional} if there is no reboot window constraint (i.e. if all
     * hours are valid).
     *
     * <p>Use {@link #isHourWithinRebootHourWindow(int)} to validate if an hour falls within the
     * window defined in this configuration.
     */
    public Optional<Pair<Integer, Integer>> getRebootWindowStartEndHour() {
        return mRebootWindowStartEndHour;
    }

    /** Returns the reboot frequency in days. */
    public int getRebootFrequencyDays() {
        return mRebootFrequencyDays;
    }

    /**
     * Returns {@code true} if the provided {@code hour} falls within the reboot window defined in
     * this configuration.
     */
    public boolean isHourWithinRebootHourWindow(int hour) {
        if (!isHourValid(hour)) {
            return false;
        }
        if (mRebootWindowStartEndHour.isEmpty()) {
            return true;
        }
        Pair<Integer, Integer> rebootWindowStartEndHour = mRebootWindowStartEndHour.get();
        if (rebootWindowStartEndHour.first < rebootWindowStartEndHour.second) {
            // Window lies in a single day.
            return hour >= rebootWindowStartEndHour.first && hour < rebootWindowStartEndHour.second;
        }
        // Window ends on the next day.
        return hour >= rebootWindowStartEndHour.first || hour < rebootWindowStartEndHour.second;
    }

    private static Optional<Pair<Integer, Integer>> getRebootWindowStartEndHour(Resources res) {
        return getRebootWindowStartEndHour(
                res.getInteger(R.integer.config_unattendedRebootStartHour),
                res.getInteger(R.integer.config_unattendedRebootEndHour));
    }

    private static Optional<Pair<Integer, Integer>> getRebootWindowStartEndHour(
            int configStartHour, int configEndHour) {
        if (configStartHour == ALLOW_ALL_HOURS && configEndHour == ALLOW_ALL_HOURS) {
            return Optional.empty();
        }
        if (!isHourValid(configStartHour)
                || !isHourValid(configEndHour)
                || configStartHour == configEndHour) {
            return Optional.of(DEFAULT_REBOOT_WINDOW_HOURS);
        }
        return Optional.of(Pair.create(configStartHour, configEndHour));
    }

    private static int getRebootFrequencyDays(Resources res) {
        int frequencyDays = res.getInteger(R.integer.config_unattendedRebootFrequencyDays);
        if (!isDayValid(frequencyDays)) {
            frequencyDays = DEFAULT_REBOOT_FREQUENCY_DAYS;
        }
        return frequencyDays;
    }

    private static boolean isHourValid(int hour) {
        return hour >= 0 && hour <= 23;
    }

    private static boolean isHourWindowValid(int startHour, int endHour) {
        return isHourValid(startHour) && isHourValid(endHour) && startHour != endHour;
    }

    private static boolean isDayValid(int day) {
        return day > 0;
    }

    @Nullable
    private static Context getResourcesContext(Context context) {
        try {
            return context.createPackageContext(RESOURCES_PACKAGE, 0);
        } catch (NameNotFoundException e) {
            return null;
        }
    }
}