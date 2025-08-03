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

package com.android.car.bluetooth;

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothProfile;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public final class BluetoothUtilsGetProfileNameTest {

    private final int mProfile;
    private final String mName;

    public BluetoothUtilsGetProfileNameTest(int profile, String name) {
        mProfile = profile;
        mName = name;
    }

    @Test
    public void testGetProfileName() {
        String result = BluetoothUtils.getProfileName(mProfile);

        assertThat(result).contains(String.valueOf(mProfile));
        assertThat(result).ignoringCase().contains(mName);
    }

    @Parameterized.Parameters
    public static Collection provideParams() {
        return Arrays.asList(
            new Object[][] {
                {BluetoothProfile.PAN, "PAN"},
                {BluetoothProfile.A2DP, "A2DP Source"},
                {BluetoothProfile.A2DP_SINK, "A2DP Sink"},
                {BluetoothProfile.AVRCP_CONTROLLER, "AVRCP Controller"},
                {BluetoothProfile.HEADSET_CLIENT, "HFP Client"},
                {BluetoothProfile.PBAP_CLIENT, "PBAP Client"},
                {BluetoothProfile.MAP_CLIENT, "MAP Client"},
                {0, "unknown"},
                {22, "unknown"}
            });
    }
}
