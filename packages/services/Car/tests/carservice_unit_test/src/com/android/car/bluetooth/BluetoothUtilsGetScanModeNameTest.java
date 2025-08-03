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

package com.android.car.bluetooth;

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothAdapter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public final class BluetoothUtilsGetScanModeNameTest {

    private final int mMode;
    private final String mName;

    public BluetoothUtilsGetScanModeNameTest(int mode, String name) {
        mMode = mode;
        mName = name;
    }

    @Test
    public void testGetScanModeName() {
        String result = BluetoothUtils.getScanModeName(mMode);
        assertThat(result).contains(String.valueOf(mMode));
        assertThat(result).ignoringCase().contains(mName);
    }

    @Parameterized.Parameters
    public static Collection provideParams() {
        return Arrays.asList(
            new Object[][] {
                {BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, "Connectable/Discoverable"},
                {BluetoothAdapter.SCAN_MODE_CONNECTABLE, "Connectable"},
                {BluetoothAdapter.SCAN_MODE_NONE, "None"},
                {BluetoothAdapter.ERROR, "Error"},
                {9, "Unknown"},
                {13, "Unknown"}
            });
    }
}
