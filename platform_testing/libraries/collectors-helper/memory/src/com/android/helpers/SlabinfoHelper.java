/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.helpers;

import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;

import androidx.annotation.VisibleForTesting;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * SlabinfoHelper parses /proc/slabinfo and reports the rate of increase or decrease in allocation
 * sizes for each slab over the collection period. The rate is determined from the slope of a line
 * fit to all samples collected between startCollecting and getMetrics. These metrics are only
 * useful over long (hours) test durations. Samples are taken once per minute. getMetrics returns
 * null for tests shorter than one minute.
 */
public class SlabinfoHelper implements ICollectorHelper<Double> {
    private static final String SLABINFO_PATH = "/proc/slabinfo";
    private static final long PAGE_SIZE = Os.sysconf(OsConstants._SC_PAGESIZE);

    private final ScheduledExecutorService mScheduler = Executors.newScheduledThreadPool(1);

    @VisibleForTesting
    public static class SlabinfoSample {
        // seconds, monotonically increasing
        public long time;

        // Key: Slab name
        // Value: size in bytes
        public Map<String, Long> slabs;
    }

    private final List<SlabinfoSample> mSamples =
            Collections.synchronizedList(new ArrayList((int) TimeUnit.HOURS.toMinutes(8)));
    private ScheduledFuture<?> mReaderHandle;

    @Override
    public boolean startCollecting() {
        if (!slabinfoVersionIsSupported()) return false;

        mReaderHandle = mScheduler.scheduleAtFixedRate(mReader, 0, 1, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public boolean stopCollecting() {
        if (mReaderHandle != null) mReaderHandle.cancel(false);

        mScheduler.shutdownNow();

        try {
            mScheduler.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        mSamples.clear();
        return true;
    }

    @Override
    public Map<String, Double> getMetrics() {
        synchronized (mSamples) {
            if (mSamples.size() < 2) return null;

            return fitLinesToSamples(mSamples);
        }
    }

    private final Runnable mReader =
            new Runnable() {
                @Override
                public void run() {
                    SlabinfoSample sample = new SlabinfoSample();
                    try {
                        sample.time = getMonotonicSeconds();
                        sample.slabs = readSlabinfo();
                        synchronized (mSamples) {
                            mSamples.add(sample);
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            };

    private static long getMonotonicSeconds() throws IOException {
        // NOTE: This is a truncating conversion without rounding
        return TimeUnit.SECONDS.convert(SystemClock.elapsedRealtime(), TimeUnit.MILLISECONDS);
    }

    private static boolean slabinfoVersionIsSupported() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(SLABINFO_PATH));
            String line = reader.readLine();
            reader.close();
            return line.equals("slabinfo - version: 2.1");
        } catch (IOException ex) {
            ex.printStackTrace();
            return false;
        }
    }

    private static Map<String, Long> readSlabinfo() throws IOException {
        Map<String, Long> slabinfo = new TreeMap<>();

        BufferedReader reader = new BufferedReader(new FileReader(SLABINFO_PATH));

        // Discard the first two header lines
        reader.readLine();
        reader.readLine();

        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            // Convert multiple adjacent spaces into a single space for tokenization
            String tokens[] = line.replaceAll(" +", " ").split(" ");
            String name = tokens[0];
            long pagesPerSlab = Long.parseLong(tokens[5]), numSlabs = Long.parseLong(tokens[14]);
            long bytes = PAGE_SIZE * pagesPerSlab * numSlabs;

            // Nobody duplicates slab names except for device mapper. Keep the maximum if we
            // encounter a duplicate slab name.
            Long val = slabinfo.get(name);
            if (val != null) val = Math.max(val, bytes);
            else val = bytes;

            slabinfo.put(name, val);
        }
        reader.close();
        return slabinfo;
    }

    // Returns the slope (bytes/5 minutes) of a line fit to the samples for each slab using the
    // least squares method. Prefixes slab names with "slabinfo." for metric reporting. Adds an
    // entry: "slabinfo.duration_seconds" to record the duration of the collection period.
    @VisibleForTesting
    public static Map<String, Double> fitLinesToSamples(List<SlabinfoSample> samples) {
        // Grab slab names from the first entry
        Set<String> names = samples.get(0).slabs.keySet();

        // Compute averages for each dimension
        double xbar = 0;
        Map<String, Double> ybars = new TreeMap<>();
        for (String name : names) ybars.put(name, 0.0);

        for (SlabinfoSample sample : samples) {
            xbar += sample.time;
            for (String name : names) ybars.put(name, ybars.get(name) + sample.slabs.get(name));
        }
        xbar /= samples.size();
        for (String name : names) ybars.put(name, ybars.get(name) / samples.size());

        // Compute slopes
        Map<String, Double> slopes = new TreeMap<>();
        for (String name : names) {
            double num = 0, denom = 0;
            for (SlabinfoSample sample : samples) {
                double delta_x = sample.time - xbar;
                double delta_y = sample.slabs.get(name) - ybars.get(name);
                num += delta_x * delta_y;
                denom += delta_x * delta_x;
            }
            slopes.put("slabinfo." + name, num * TimeUnit.MINUTES.toSeconds(5) / denom);
        }

        slopes.put(
                "slabinfo.duration_seconds",
                (double) samples.get(samples.size() - 1).time - samples.get(0).time);

        return slopes;
    }
}
