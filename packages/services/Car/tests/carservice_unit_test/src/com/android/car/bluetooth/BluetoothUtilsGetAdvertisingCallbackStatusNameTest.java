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

import android.bluetooth.le.AdvertisingSetCallback;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public final class BluetoothUtilsGetAdvertisingCallbackStatusNameTest {

    private final int mStatus;
    private final String mName;

    public BluetoothUtilsGetAdvertisingCallbackStatusNameTest(int status, String name) {
        mStatus = status;
        mName = name;
    }

    @Test
    public void testGetAdvertisingCallbackStatusName() {
        String result = BluetoothUtils.getAdvertisingCallbackStatusName(mStatus);

        assertThat(result).contains(String.valueOf(mStatus));
        assertThat(result).ignoringCase().contains(mName);
    }

    @Parameterized.Parameters
    public static Collection provideParams() {
        return Arrays.asList(
            new Object[][] {

                {AdvertisingSetCallback.ADVERTISE_FAILED_ALREADY_STARTED,
                        "ADVERTISE_FAILED_ALREADY_STARTED"},
                {AdvertisingSetCallback.ADVERTISE_FAILED_DATA_TOO_LARGE,
                        "ADVERTISE_FAILED_DATA_TOO_LARGE"},
                {AdvertisingSetCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED,
                        "ADVERTISE_FAILED_FEATURE_UNSUPPORTED"},
                {AdvertisingSetCallback.ADVERTISE_FAILED_INTERNAL_ERROR,
                        "ADVERTISE_FAILED_INTERNAL_ERROR"},
                {AdvertisingSetCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS,
                        "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS"},
                {AdvertisingSetCallback.ADVERTISE_SUCCESS,
                        "ADVERTISE_SUCCESS"},
                {-1, "unknown"},
                {15, "unknown"}
            });
    }
}
