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
public final class BluetoothUtilsGetProfileConnectionPolicyNameTest {

    private final int mPolicy;
    private final String mName;

    public BluetoothUtilsGetProfileConnectionPolicyNameTest(int policy, String name) {
        mPolicy = policy;
        mName = name;
    }

    @Test
    public void testGetProfilePriorityName() {
        String result = BluetoothUtils.getConnectionPolicyName(mPolicy);

        assertThat(result).contains(String.valueOf(mPolicy));
        assertThat(result).ignoringCase().contains(mName);
    }

    @Parameterized.Parameters
    public static Collection provideParams() {
        return Arrays.asList(
            new Object[][] {
                // CONNECTION_POLICY_ALLOWED maps to "CONNECTION_POLICY_ALLOWED".
                {100, "CONNECTION_POLICY_ALLOWED"},
                {BluetoothProfile.CONNECTION_POLICY_ALLOWED, "CONNECTION_POLICY_ALLOWED"},
                // CONNECTION_POLICY_FORBIDDEN maps to "CONNECTION_POLICY_FORBIDDEN".
                {0, "CONNECTION_POLICY_FORBIDDEN"},
                {BluetoothProfile.CONNECTION_POLICY_FORBIDDEN, "CONNECTION_POLICY_FORBIDDEN"},
                // CONNECTION_POLICY_UNKNOWN maps to "CONNECTION_POLICY_UNKNOWN".
                {-1, "CONNECTION_POLICY_UNKNOWN"},
                {BluetoothProfile.CONNECTION_POLICY_UNKNOWN, "CONNECTION_POLICY_UNKNOWN"}
            });
    }
}
