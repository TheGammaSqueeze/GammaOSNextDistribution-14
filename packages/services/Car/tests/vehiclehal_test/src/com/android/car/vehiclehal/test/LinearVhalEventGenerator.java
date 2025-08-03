/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.car.vehiclehal.test;

import static com.google.common.truth.Truth.assertThat;

import android.car.test.CarTestManager;

import java.time.Duration;
import java.util.List;

class LinearVhalEventGenerator implements VhalEventGenerator {

    private final CarTestManager mCarTestManager;
    private final long mVhalDumpTimeoutMs;

    private int mProp;
    private Duration mInterval;
    private float mInitialValue;
    private float mDispersion;
    private float mIncrement;

    LinearVhalEventGenerator(CarTestManager carTestManager, long vhalDumpTimeoutMs) {
        mCarTestManager = carTestManager;
        mVhalDumpTimeoutMs = vhalDumpTimeoutMs;
        reset();
    }

    LinearVhalEventGenerator reset() {
        mProp = 0;
        mInterval = Duration.ofSeconds(1);
        mInitialValue = 1000;
        mDispersion = 0;
        mInitialValue = 0;
        return this;
    }

    LinearVhalEventGenerator setIntervalMs(long intervalMs) {
        mInterval = Duration.ofMillis(intervalMs);
        return this;
    }

    LinearVhalEventGenerator setInitialValue(float initialValue) {
        mInitialValue = initialValue;
        return this;
    }

    LinearVhalEventGenerator setDispersion(float dispersion) {
        mDispersion = dispersion;
        return this;
    }

    LinearVhalEventGenerator setIncrement(float increment) {
        mIncrement = increment;
        return this;
    }

    LinearVhalEventGenerator setProp(int prop) {
        mProp = prop;
        return this;
    }

    @Override
    public void start() throws Exception {
        List<String> options = List.of("--genfakedata", "--startlinear", String.format("%d", mProp),
                String.format("%f", mInitialValue), String.format("%f", mInitialValue),
                String.format("%f", mDispersion), String.format("%f", mIncrement),
                String.format("%d", mInterval.toNanos()));

        String output = mCarTestManager.dumpVhal(options, mVhalDumpTimeoutMs);

        assertThat(output).contains("started successfully");
    }

    @Override
    public void stop() throws Exception {
        List<String> options = List.of("--genfakedata", "--stoplinear", String.format("%d", mProp));

        // Ignore output since the generator might have already stopped.
        mCarTestManager.dumpVhal(options, mVhalDumpTimeoutMs);
    }
}
