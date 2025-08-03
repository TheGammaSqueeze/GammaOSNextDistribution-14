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

import android.bluetooth.BluetoothDevice;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public final class BluetoothUtilsGetBondStateNameTest {

    private final int mState;
    private final String mName;

    public BluetoothUtilsGetBondStateNameTest(int state, String name) {
        mState = state;
        mName = name;
    }

    @Test
    public void testGetBondStateName() {
        String result = BluetoothUtils.getBondStateName(mState);

        assertThat(result).contains(String.valueOf(mState));
        assertThat(result).ignoringCase().contains(mName);
    }

    @Parameterized.Parameters
    public static Collection provideParams() {
        return Arrays.asList(
            new Object[][] {
                {BluetoothDevice.BOND_BONDED, "bonded"},
                {BluetoothDevice.BOND_BONDING, "bonding"},
                {BluetoothDevice.BOND_NONE, "unbonded"},
                {9, "unknown"},
                {13, "unknown"}
            });
    }
}
