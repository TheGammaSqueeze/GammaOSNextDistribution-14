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

package com.android.bedstead.nene.bluetooth;

import static com.google.common.truth.Truth.assertThat;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureBluetoothDisabled;
import com.android.bedstead.harrier.annotations.EnsureBluetoothEnabled;
import com.android.bedstead.nene.TestApis;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class BluetoothTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Test
    @EnsureBluetoothDisabled
    public void setEnabled_true_bluetoothIsEnabled() throws Exception {
        TestApis.bluetooth().setEnabled(true);

        assertThat(TestApis.bluetooth().isEnabled()).isTrue();
    }

    @Test
    @EnsureBluetoothEnabled
    public void setEnabled_false_bluetoothIsDisabled() {
        TestApis.bluetooth().setEnabled(false);

        assertThat(TestApis.bluetooth().isEnabled()).isFalse();
    }
}
