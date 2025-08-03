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


import static com.android.server.deviceconfig.Flags.FLAG_ENABLE_CUSTOM_REBOOT_TIME_CONFIGURATIONS;
import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.Pair;

import androidx.test.filters.SmallTest;

import com.android.server.deviceconfig.resources.R;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Optional;

@SmallTest
public class RebootTimingConfigurationTest {

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock private Context mContext;
    @Mock private Resources mResources;

    @Before
    public void setUp() throws Exception {
        mSetFlagsRule.enableFlags(FLAG_ENABLE_CUSTOM_REBOOT_TIME_CONFIGURATIONS);
        when(mContext.createPackageContext("com.android.server.deviceconfig.resources", 0))
                .thenReturn(mContext);
        when(mContext.getResources()).thenReturn(mResources);
    }

    @Test
    public void validSameDayConfiguration() {
        mockRebootWindowConfig(1, 3);
        mockRebootFrequencyDays(4);

        RebootTimingConfiguration config = new RebootTimingConfiguration(mContext);

        assertThat(config.getRebootWindowStartEndHour()).isEqualTo(Optional.of(Pair.create(1, 3)));
        assertThat(config.getRebootFrequencyDays()).isEqualTo(4);
        assertHoursWithinWindow(config, 1, 2);
        assertHoursOutsideWindow(config, 0, 3, 4);
    }

    @Test
    public void validNextDayConfiguration() {
        mockRebootWindowConfig(22, 3);
        mockRebootFrequencyDays(4);

        RebootTimingConfiguration config = new RebootTimingConfiguration(mContext);

        assertThat(config.getRebootWindowStartEndHour()).isEqualTo(Optional.of(Pair.create(22, 3)));
        assertThat(config.getRebootFrequencyDays()).isEqualTo(4);
        assertHoursWithinWindow(config, 22, 23, 0, 1, 2);
        assertHoursOutsideWindow(config, 20, 3, 4);
    }

    @Test
    public void flagDisabled_usesDefaultWindowAndFrequency() {
        mSetFlagsRule.disableFlags(FLAG_ENABLE_CUSTOM_REBOOT_TIME_CONFIGURATIONS);
        mockRebootWindowConfig(1, 9);
        mockRebootFrequencyDays(4);

        RebootTimingConfiguration config = new RebootTimingConfiguration(mContext);

        assertThat(config.getRebootWindowStartEndHour()).isEqualTo(Optional.of(Pair.create(3, 5)));
        assertThat(config.getRebootFrequencyDays()).isEqualTo(2);
    }

    @Test
    public void invalidWindowHoursWithEqualWindowTimes_usesDefaultWindow() {
        mockRebootWindowConfig(1, 1);
        mockRebootFrequencyDays(4);

        RebootTimingConfiguration config = new RebootTimingConfiguration(mContext);

        assertThat(config.getRebootWindowStartEndHour()).isEqualTo(Optional.of(Pair.create(3, 5)));
        assertThat(config.getRebootFrequencyDays()).isEqualTo(4);
    }

    @Test
    public void invalidWindowHoursWithNegativeWindowTimes_usesDefaultWindow() {
        mockRebootWindowConfig(-1, 1);
        mockRebootFrequencyDays(4);

        RebootTimingConfiguration config = new RebootTimingConfiguration(mContext);

        assertThat(config.getRebootWindowStartEndHour()).isEqualTo(Optional.of(Pair.create(3, 5)));
        assertThat(config.getRebootFrequencyDays()).isEqualTo(4);
    }

    @Test
    public void invalidFrequencyDay_usesDefaultFrequencyDay() {
        mockRebootWindowConfig(2, 3);
        mockRebootFrequencyDays(-4);

        RebootTimingConfiguration config = new RebootTimingConfiguration(mContext);

        assertThat(config.getRebootWindowStartEndHour()).isEqualTo(Optional.of(Pair.create(2, 3)));
        assertThat(config.getRebootFrequencyDays()).isEqualTo(2);
    }

    @Test
    public void disableRebootWindowConfiguration() {
        mockRebootWindowConfig(-1, -1);
        mockRebootFrequencyDays(4);

        RebootTimingConfiguration config = new RebootTimingConfiguration(mContext);

        assertThat(config.getRebootWindowStartEndHour()).isEqualTo(Optional.empty());
        assertThat(config.getRebootFrequencyDays()).isEqualTo(4);
        for (int i = 0; i < 24; i++) {
            assertHoursWithinWindow(config, i);
        }
    }

    private void mockRebootWindowConfig(int windowStartHour, int windowEndHour) {
        when(mResources.getInteger(R.integer.config_unattendedRebootStartHour))
                .thenReturn(windowStartHour);
        when(mResources.getInteger(R.integer.config_unattendedRebootEndHour))
                .thenReturn(windowEndHour);
    }

    private void mockRebootFrequencyDays(int days) {
        when(mResources.getInteger(R.integer.config_unattendedRebootFrequencyDays))
                .thenReturn(days);
    }

    private void assertHoursWithinWindow(RebootTimingConfiguration config, int... hours) {
        for (int hour : hours) {
            assertThat(config.isHourWithinRebootHourWindow(hour)).isTrue();
        }
    }

    private void assertHoursOutsideWindow(RebootTimingConfiguration config, int... hours) {
        for (int hour : hours) {
            assertThat(config.isHourWithinRebootHourWindow(hour)).isFalse();
        }
    }
}
