/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.hardware.consumerir.cts;

import static android.content.pm.PackageManager.FEATURE_CONSUMER_IR;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.hardware.ConsumerIrManager;
import android.os.SystemClock;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Very basic test, just of the static methods of {@link
 * android.hardware.ConsumerIrManager}.
 */

@RunWith(AndroidJUnit4.class)
public class ConsumerIrTest {
    @Rule
    public RequiredFeatureRule mConsumerIrRule = new RequiredFeatureRule(FEATURE_CONSUMER_IR);
    private ConsumerIrManager mCIR;

    @Before
    public void setUp() {
        mCIR = (ConsumerIrManager) InstrumentationRegistry.getContext()
                .getSystemService(Context.CONSUMER_IR_SERVICE);
    }

    @Test
    @ApiTest(apis = {"android.hardware.ConsumerIrManager#hasIrEmitter"})
    public void test_hasIrEmitter() {
        assertTrue(mCIR.hasIrEmitter());
    }

    @Test
    @ApiTest(apis = {"android.hardware.ConsumerIrManager#getCarrierFrequencies",
            "android.hardware.ConsumerIrManager.CarrierFrequencyRange#getMinFrequency",
            "android.hardware.ConsumerIrManager.CarrierFrequencyRange#getMaxFrequency"})
    public void test_getCarrierFrequencies() {
        ConsumerIrManager.CarrierFrequencyRange[] freqs = mCIR.getCarrierFrequencies();

        assertTrue(freqs.length > 0);
        for (ConsumerIrManager.CarrierFrequencyRange range : freqs) {
            // Each range must be valid
            assertTrue(range.getMinFrequency() > 0);
            assertTrue(range.getMaxFrequency() > 0);
            assertTrue(range.getMinFrequency() <= range.getMaxFrequency());
        }
    }

    @Test
    @ApiTest(apis = {"android.hardware.ConsumerIrManager#getCarrierFrequencies",
            "android.hardware.ConsumerIrManager#transmit"})
    public void test_timing() {
        ConsumerIrManager.CarrierFrequencyRange[] freqs = mCIR.getCarrierFrequencies();
        // Transmit two seconds for min and max for each frequency range
        int[] pattern = {11111, 22222, 33333, 44444, 55555, 66666, 77777, 88888, 99999};
        long totalXmitTimeNanos = 0; // get the length of the pattern
        for (int slice : pattern) {
            totalXmitTimeNanos += slice * 1000; // add the time in nanoseconds
        }
        double margin = 0.5; // max fraction xmit is allowed to be off timing

        for (ConsumerIrManager.CarrierFrequencyRange range : freqs) {
            // test min freq
            long currentTime = SystemClock.elapsedRealtimeNanos();
            mCIR.transmit(range.getMinFrequency(), pattern);
            long newTime = SystemClock.elapsedRealtimeNanos();
            String msg = String.format("Pattern length pattern:%d, actual:%d",
                    totalXmitTimeNanos, newTime - currentTime);
            assertTrue(msg, newTime - currentTime >= totalXmitTimeNanos * (1.0 - margin));
            assertTrue(msg, newTime - currentTime <= totalXmitTimeNanos * (1.0 + margin));

            // test max freq
            currentTime = SystemClock.elapsedRealtimeNanos();
            mCIR.transmit(range.getMaxFrequency(), pattern);
            newTime = SystemClock.elapsedRealtimeNanos();
            msg = String.format("Pattern length pattern:%d, actual:%d",
                    totalXmitTimeNanos, newTime - currentTime);
            assertTrue(msg, newTime - currentTime >= totalXmitTimeNanos * (1.0 - margin));
            assertTrue(msg, newTime - currentTime <= totalXmitTimeNanos * (1.0 + margin));
        }
    }

    @Test
    @ApiTest(apis = {"android.hardware.ConsumerIrManager#getCarrierFrequencies",
            "android.hardware.ConsumerIrManager#transmit"})
    public void test_transmit() {
        ConsumerIrManager.CarrierFrequencyRange[] freqs = mCIR.getCarrierFrequencies();

        int[] pattern = {1901, 4453, 625, 1614, 625, 1588, 625, 1614, 625, 442, 625, 442, 625,
                468, 625, 442, 625, 494, 572, 1614, 625, 1588, 625, 1614, 625, 494, 572, 442, 651,
                442, 625, 442, 625, 442, 625, 1614, 625, 1588, 651, 1588, 625, 442, 625, 494, 598,
                442, 625, 442, 625, 520, 572, 442, 625, 442, 625, 442, 651, 1588, 625, 1614, 625,
                1588, 625, 1614, 625, 1588, 625, 48958};

        // just use the first frequency in the range
        mCIR.transmit(freqs[0].getMinFrequency(), pattern);
    }
}
