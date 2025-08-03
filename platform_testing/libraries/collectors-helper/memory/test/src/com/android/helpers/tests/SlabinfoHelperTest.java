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

package com.android.helpers.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.runner.AndroidJUnit4;

import com.android.helpers.SlabinfoHelper;
import com.android.helpers.SlabinfoHelper.SlabinfoSample;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@RunWith(AndroidJUnit4.class)
public class SlabinfoHelperTest {
    private SlabinfoHelper mSlabinfoHelper;

    @Before
    public void setUp() {
        mSlabinfoHelper = new SlabinfoHelper();
    }

    @Test
    public void testStartStop() {
        assertTrue(
                "Reading /proc/slabinfo requires root and no selinux denial. Are you root?",
                mSlabinfoHelper.startCollecting());
        assertTrue(mSlabinfoHelper.stopCollecting());
    }

    @Test
    public void testSimpleExactFits() {
        List<SlabinfoSample> samples = new ArrayList<>();
        SlabinfoSample sample = new SlabinfoSample();
        sample.time = 0;
        sample.slabs = new TreeMap<String, Long>();
        sample.slabs.put("a", 0L);
        sample.slabs.put("b", 0L);
        sample.slabs.put("c", 0L);
        sample.slabs.put("d", 25L);
        sample.slabs.put("e", 50L);
        sample.slabs.put("f", 100L);
        samples.add(sample);

        sample = new SlabinfoSample();
        sample.time = 100;
        sample.slabs = new TreeMap<String, Long>();
        sample.slabs.put("a", 100L);
        sample.slabs.put("b", 50L);
        sample.slabs.put("c", 25L);
        sample.slabs.put("d", 0L);
        sample.slabs.put("e", 0L);
        sample.slabs.put("f", 0L);
        samples.add(sample);

        Map<String, Double> slopes = SlabinfoHelper.fitLinesToSamples(samples);
        assertEquals(Double.valueOf(300), slopes.get("slabinfo.a"));
        assertEquals(Double.valueOf(150), slopes.get("slabinfo.b"));
        assertEquals(Double.valueOf(75), slopes.get("slabinfo.c"));
        assertEquals(Double.valueOf(-75), slopes.get("slabinfo.d"));
        assertEquals(Double.valueOf(-150), slopes.get("slabinfo.e"));
        assertEquals(Double.valueOf(-300), slopes.get("slabinfo.f"));
    }

    @Test
    public void testNoSlopes() {
        List<SlabinfoSample> samples = new ArrayList<>();
        SlabinfoSample sample = new SlabinfoSample();
        sample.time = 0;
        sample.slabs = new TreeMap<String, Long>();
        sample.slabs.put("a", 0L);
        sample.slabs.put("b", 0L);
        samples.add(sample);

        sample = new SlabinfoSample();
        sample.time = 50;
        sample.slabs = new TreeMap<String, Long>();
        sample.slabs.put("a", 0L);
        sample.slabs.put("b", 50L);
        samples.add(sample);

        sample = new SlabinfoSample();
        sample.time = 100;
        sample.slabs = new TreeMap<String, Long>();
        sample.slabs.put("a", 0L);
        sample.slabs.put("b", 0L);
        samples.add(sample);

        Map<String, Double> slopes = SlabinfoHelper.fitLinesToSamples(samples);
        assertEquals(Double.valueOf(0), slopes.get("slabinfo.a"));
        assertEquals(Double.valueOf(0), slopes.get("slabinfo.b"));
    }

    @Test
    public void testNonexactFits() {
        List<SlabinfoSample> samples = new ArrayList<>();
        SlabinfoSample sample = new SlabinfoSample();
        sample.time = 37;
        sample.slabs = new TreeMap<String, Long>();
        sample.slabs.put("a", 83L);
        sample.slabs.put("b", 83L);
        samples.add(sample);

        sample = new SlabinfoSample();
        sample.time = 43;
        sample.slabs = new TreeMap<String, Long>();
        sample.slabs.put("a", 47L);
        sample.slabs.put("b", 47L);
        samples.add(sample);

        sample = new SlabinfoSample();
        sample.time = 97;
        sample.slabs = new TreeMap<String, Long>();
        sample.slabs.put("a", 71L);
        sample.slabs.put("b", 61L);
        samples.add(sample);

        Map<String, Double> slopes = SlabinfoHelper.fitLinesToSamples(samples);
        DecimalFormat df = new DecimalFormat("0.0000000");
        df.setRoundingMode(RoundingMode.HALF_UP);
        Double rounded_a = Double.valueOf(df.format(slopes.get("slabinfo.a")));
        Double rounded_b = Double.valueOf(df.format(slopes.get("slabinfo.b")));
        assertEquals(Double.valueOf(16.4835165), rounded_a);
        assertEquals(Double.valueOf(-35.7142857), rounded_b);
    }
}
