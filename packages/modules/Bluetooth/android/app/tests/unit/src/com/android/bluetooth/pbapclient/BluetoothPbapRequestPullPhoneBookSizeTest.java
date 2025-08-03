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

package com.android.bluetooth.pbapclient;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.obex.HeaderSet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothPbapRequestPullPhoneBookSizeTest {

    BluetoothPbapRequestPullPhoneBookSize mRequest;

    @Before
    public void setUp() {
        mRequest = new BluetoothPbapRequestPullPhoneBookSize(/*pbName=*/"phonebook", /*filter=*/1);
    }

    @Test
    public void readResponseHeaders() {
        try {
            HeaderSet headerSet = new HeaderSet();
            mRequest.readResponseHeaders(headerSet);
            assertThat(mRequest.getSize()).isEqualTo(0);
        } catch (Exception e) {
            assertWithMessage("Exception should not happen.").fail();
        }
    }
}
