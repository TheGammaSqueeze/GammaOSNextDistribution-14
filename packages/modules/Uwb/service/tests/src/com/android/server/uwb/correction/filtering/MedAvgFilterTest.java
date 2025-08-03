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
package com.android.server.uwb.correction.filtering;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.Presubmit;

import org.junit.Test;

@Presubmit
public class MedAvgFilterTest {

    @Test
    public void averageTest() {
        MedAvgFilter filter = new MedAvgFilter(3, 1);
        filter.add(1, 0, 1);
        filter.add(2, 0, 1);
        filter.add(3, 0, 1);
        assertThat(filter.getResult().value).isEqualTo((1 + 2 + 3) / 3f);
    }

    // Test when the sliding window is full and it needs to slide.
    @Test
    public void slideTest() {
        MedAvgFilter filter = new MedAvgFilter(3, 1);
        filter.add(1, 0, 1);
        filter.add(2, 0, 1);
        filter.add(3, 0, 1);
        filter.add(4, 0, 1);
        assertThat(filter.getResult().value).isEqualTo((2 + 3 + 4) / 3f);
    }

    @Test
    public void remapTest() {
        MedAvgFilter filter = new MedAvgFilter(3, 1);
        filter.add(1, 0, 1);
        filter.add(2, 0, 1);
        filter.add(3, 0, 1);
        filter.compensate(66);
        assertThat(filter.getResult().value).isEqualTo((1 + 2 + 3) / 3f + 66);
    }

    // Ensures that a pure median with an even-sized window uses the center two values.
    @Test
    public void evenMedianTest() {
        MedAvgFilter filter = new MedAvgFilter(4, 0);
        filter.add(1, 0, 1);
        filter.add(3, 0, 1);
        filter.add(4, 0, 1);
        filter.add(2, 0, 1);
        assertThat(filter.getResult().value).isEqualTo((2f + 3f) / 2f);
    }

    // Ensures that a filter operates properly even when its window is not full.
    @Test
    public void shortFilterTest() {
        MedAvgFilter filter = new MedAvgFilter(4, 0); // median
        filter.add(5, 0, 1);
        assertThat(filter.getResult().value).isEqualTo(5);
        filter.add(6, 0, 1);
        assertThat(filter.getResult().value).isEqualTo((5f + 6f) / 2);
        filter.add(7, 0, 1);
        assertThat(filter.getResult().value).isEqualTo(6);
    }

    @Test
    public void mixCutTest() {
        MedAvgFilter filter = new MedAvgFilter(5, 0.5f); // Average half
        filter.add(3, 0, 1);
        filter.add(13, 0, 1);
        filter.add(7, 0, 1);
        filter.add(11, 0, 1);
        filter.add(2, 0, 1);
        assertThat(filter.getResult().value).isEqualTo((3 + 7 + 11) / 3f);
    }

    @Test
    public void getTest() {
        MedAvgFilter filter = new MedAvgFilter(5, 0.5f); // Average half
        assertThat(filter.getCut()).isEqualTo(0.5f);
        assertThat(filter.getWindowSize()).isEqualTo(5);
    }

    @Test
    public void weightedTest() {
        MedAvgFilter filter = new MedAvgFilter(5, 1f); // Average
        filter.add(3, 0, 0.5f);
        filter.add(13, 0, 0.5f);
        filter.add(7, 0, 0.25f);
        filter.add(11, 0, 1f);
        filter.add(2, 0, 0.25f);
        float weightedResult = (float) (
                3 * 0.5
                + 13 * 0.5
                + 7 * 0.25
                + 11
                + 2 * 0.25f
                )
                / (0.5f + 0.5f + 0.25f + 1f + 0.25f);
        assertThat(filter.getResult().value).isWithin(0.000001f).of(weightedResult);
    }
}
