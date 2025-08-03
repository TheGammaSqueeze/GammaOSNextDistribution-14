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

package com.android.bluetooth.gatt;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;

import android.content.Intent;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Test cases for {@link GattDebugUtils}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class GattDebugUtilsTest {

    @Mock
    private GattService mService;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void handleDebugAction() {
        Intent intent = new Intent(GattDebugUtils.ACTION_GATT_TEST_USAGE);

        boolean result = GattDebugUtils.handleDebugAction(mService, intent);
        assertThat(result).isTrue();

        intent = new Intent(GattDebugUtils.ACTION_GATT_TEST_ENABLE);
        GattDebugUtils.handleDebugAction(mService, intent);
        int bEnable = 1;
        verify(mService).gattTestCommand(0x01, null, null, bEnable, 0, 0, 0, 0);

        intent = new Intent(GattDebugUtils.ACTION_GATT_TEST_CONNECT);
        GattDebugUtils.handleDebugAction(mService, intent);
        int type = 2;
        verify(mService).gattTestCommand(0x02, null, null, type, 0, 0, 0, 0);

        intent = new Intent(GattDebugUtils.ACTION_GATT_TEST_DISCONNECT);
        GattDebugUtils.handleDebugAction(mService, intent);
        verify(mService).gattTestCommand(0x03, null, null, 0, 0, 0, 0, 0);

        intent = new Intent(GattDebugUtils.ACTION_GATT_TEST_DISCOVER);
        GattDebugUtils.handleDebugAction(mService, intent);
        int typeDiscover = 1;
        int shdl = 1;
        int ehdl = 0xFFFF;
        verify(mService).gattTestCommand(0x04, null, null, typeDiscover, shdl, ehdl, 0, 0);

        intent = new Intent(GattDebugUtils.ACTION_GATT_PAIRING_CONFIG);
        GattDebugUtils.handleDebugAction(mService, intent);
        int authReq = 5;
        int ioCap = 4;
        int initKey = 7;
        int respKey = 7;
        int maxKey = 16;
        verify(mService).gattTestCommand(0xF0, null, null, authReq, ioCap, initKey, respKey,
                maxKey);
    }
}
