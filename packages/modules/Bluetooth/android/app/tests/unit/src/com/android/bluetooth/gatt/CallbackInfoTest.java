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

package com.android.bluetooth.gatt;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/**
 * Test cases for {@link CallbackInfo}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class CallbackInfoTest {

    @Test
    public void callbackInfoBuilder() {
        String address = "TestAddress";
        int status = 0;
        int handle = 1;
        byte[] value = "Test Value Byte Array".getBytes();

        CallbackInfo callbackInfo = new CallbackInfo.Builder(address, status)
                .setHandle(handle)
                .setValue(value)
                .build();

        assertThat(callbackInfo.address).isEqualTo(address);
        assertThat(callbackInfo.status).isEqualTo(status);
        assertThat(callbackInfo.handle).isEqualTo(handle);
        assertThat(Arrays.equals(callbackInfo.value, value)).isTrue();
    }
}
