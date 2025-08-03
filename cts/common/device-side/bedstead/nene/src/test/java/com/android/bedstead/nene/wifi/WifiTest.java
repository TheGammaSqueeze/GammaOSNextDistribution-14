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

package com.android.bedstead.nene.wifi;

import static com.google.common.truth.Truth.assertThat;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureWifiDisabled;
import com.android.bedstead.harrier.annotations.EnsureWifiEnabled;
import com.android.bedstead.nene.TestApis;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class WifiTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Test
    @EnsureWifiEnabled
    public void isEnabled_isEnabled_returnsTrue() {
        assertThat(TestApis.wifi().isEnabled()).isTrue();
    }

    @Test
    @EnsureWifiDisabled
    public void isEnabled_isNotEnabled_returnsFalse() {
        assertThat(TestApis.wifi().isEnabled()).isFalse();
    }

    @Test
    @EnsureWifiEnabled
    public void setEnabled_false_isNotEnabled() {
        TestApis.wifi().setEnabled(false);

        assertThat(TestApis.wifi().isEnabled()).isFalse();
    }

    @Test
    @EnsureWifiDisabled
    public void setEnabled_true_isEnabled() {
        TestApis.wifi().setEnabled(true);

        assertThat(TestApis.wifi().isEnabled()).isTrue();
    }
}
