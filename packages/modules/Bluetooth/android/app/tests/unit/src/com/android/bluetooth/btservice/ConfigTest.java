/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.bluetooth.btservice;

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothProfile;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;

@RunWith(JUnit4.class)
public final class ConfigTest {
    @Test
    public void setProfileEnabled() {
        boolean enabled =
                Arrays.stream(Config.getSupportedProfiles())
                        .anyMatch(profileId -> profileId == BluetoothProfile.CSIP_SET_COORDINATOR);

        Config.setProfileEnabled(BluetoothProfile.CSIP_SET_COORDINATOR, false);
        assertThat(
                        Arrays.stream(Config.getSupportedProfiles())
                                .anyMatch(
                                        profileId ->
                                                profileId == BluetoothProfile.CSIP_SET_COORDINATOR))
                .isFalse();

        Config.setProfileEnabled(BluetoothProfile.CSIP_SET_COORDINATOR, true);
        assertThat(
                        Arrays.stream(Config.getSupportedProfiles())
                                .anyMatch(
                                        profileId ->
                                                profileId == BluetoothProfile.CSIP_SET_COORDINATOR))
                .isTrue();

        Config.setProfileEnabled(BluetoothProfile.CSIP_SET_COORDINATOR, enabled);
    }
}
