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

package com.android.federatedcompute.services.common;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public final class BatteryInfoTest {
    private BatteryInfo mBatteryInfo;
    @Mock private Context mContext;
    @Mock private Flags mFlags;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mBatteryInfo = new BatteryInfo(mContext, mFlags);
        when(mFlags.getTrainingMinBatteryLevel()).thenReturn(20);
    }

    @Test
    public void batteryOkForTraining_returnOk() {
        Intent intent = new Intent(Intent.ACTION_BATTERY_CHANGED);
        intent.putExtra(BatteryManager.EXTRA_LEVEL, 60);
        intent.putExtra(BatteryManager.EXTRA_SCALE, 100);
        when(mContext.registerReceiver(any(), any(), anyInt())).thenReturn(intent);

        assertTrue(mBatteryInfo.batteryOkForTraining(true));
    }

    @Test
    public void batteryOkForTraining_batteryLevelTooLow_returnNotOk() {
        Intent intent = new Intent(Intent.ACTION_BATTERY_CHANGED);
        intent.putExtra(BatteryManager.EXTRA_LEVEL, 10);
        intent.putExtra(BatteryManager.EXTRA_SCALE, 100);
        when(mContext.registerReceiver(any(), any(), anyInt())).thenReturn(intent);

        assertFalse(mBatteryInfo.batteryOkForTraining(true));
    }

    @Test
    public void batteryOkForTraining_disableBatteryLevelNotLowCheck_returnOk() {
        Intent intent = new Intent(Intent.ACTION_BATTERY_CHANGED);
        intent.putExtra(BatteryManager.EXTRA_LEVEL, 10);
        intent.putExtra(BatteryManager.EXTRA_SCALE, 100);
        when(mContext.registerReceiver(any(), any(), anyInt())).thenReturn(intent);

        assertTrue(mBatteryInfo.batteryOkForTraining(false));
    }
}
