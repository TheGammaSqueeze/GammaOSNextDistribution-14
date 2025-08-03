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

package com.android.server.cts.device.statsdatom;

import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.NonApiTest;

import org.junit.Before;
import org.junit.Test;

/**
 * These tests are run by VibratorStatsTest to generate some stats by exercising vibrator APIs.
 *
 * <p>They only trigger the APIs, but don't test anything themselves.
 */
@NonApiTest(exemptionReasons = {}, justification = "METRIC")
public class VibratorTests {
    private Vibrator mVibrator;

    @Before
    public void setUp() {
        mVibrator = InstrumentationRegistry.getContext().getSystemService(Vibrator.class);
    }

    @Test
    public void testOneShotVibration() throws Exception {
        mVibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
        // Sleep so that the app does not get killed while it's vibrating.
        Thread.sleep(1000);
    }

    @Test
    public void testPredefinedClickVibration() throws Exception {
        mVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
        // Sleep so that the app does not get killed while it's vibrating.
        Thread.sleep(500);
    }

    @Test
    public void testComposedTickThenClickVibration() throws Exception {
        mVibrator.vibrate(
                VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK,
                                /* scale= */ 0.5f, /* delay= */ 50)
                        .compose());

        // Sleep so that the app does not get killed while it's vibrating.
        Thread.sleep(500);
    }

    @Test
    public void testRepeatedWaveformVibration() throws Exception {
        mVibrator.vibrate(VibrationEffect.createWaveform(new long[] { 50, 50 }, /* repeat= */ 0));
        // Let the vibration play for a while.
        Thread.sleep(500);
        mVibrator.cancel();
        // Sleep so that the app does not get killed while it's cancelling.
        Thread.sleep(100);
    }
}
