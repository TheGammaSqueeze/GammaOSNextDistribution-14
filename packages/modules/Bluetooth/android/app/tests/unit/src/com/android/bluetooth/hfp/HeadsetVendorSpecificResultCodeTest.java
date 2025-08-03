/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.hfp;

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class HeadsetVendorSpecificResultCodeTest {
    private static final String TEST_COMMAND = "test_command";
    private static final String TEST_ARG = "test_arg";

    private BluetoothAdapter mAdapter;
    private BluetoothDevice mTestDevice;

    @Before
    public void setUp() {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mTestDevice = mAdapter.getRemoteDevice("00:01:02:03:04:05");
    }

    @Test
    public void constructor() {
        HeadsetVendorSpecificResultCode code = new HeadsetVendorSpecificResultCode(mTestDevice,
                TEST_COMMAND, TEST_ARG);

        assertThat(code.mDevice).isEqualTo(mTestDevice);
        assertThat(code.mCommand).isEqualTo(TEST_COMMAND);
        assertThat(code.mArg).isEqualTo(TEST_ARG);
    }

    @Test
    public void buildString() {
        HeadsetVendorSpecificResultCode code = new HeadsetVendorSpecificResultCode(mTestDevice,
                TEST_COMMAND, TEST_ARG);
        StringBuilder builder = new StringBuilder();

        code.buildString(builder);

        String expectedString =
                code.getClass().getSimpleName() + "[device=" + mTestDevice + ", command="
                        + TEST_COMMAND + ", arg=" + TEST_ARG + "]";
        assertThat(builder.toString()).isEqualTo(expectedString);
    }
}
