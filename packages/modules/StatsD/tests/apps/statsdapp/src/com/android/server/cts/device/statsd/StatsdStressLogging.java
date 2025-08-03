/*
 * Copyright (C) 2023 Google LLC.
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

package com.android.server.cts.device.statsd;

import android.os.SystemClock;
import android.util.StatsLog;

import org.junit.Test;

public class StatsdStressLogging {
    private static final int EVENT_STORM_ATOMS_COUNT = 100000;

    /** Tests that logging many atoms back to back leads to socket overflow and data loss. */
    @Test
    public void testLogAtomsBackToBack() throws Exception {
        // logging back to back many atoms to force socket overflow
        logAtoms(EVENT_STORM_ATOMS_COUNT);

        // Using sleep to allow bypass libstatsocket dumpAtomsLossStats() cooldown timer
        SystemClock.sleep(100);

        // Try to log atoms into socket successfully to trigger libstatsocket dumpAtomsLossStats()
        logAtoms(1);
    }

    private void logAtoms(int iterations) {
        // single atom logging takes ~2us excluding JNI interactions
        for (int i = 0; i < iterations; i++) {
            StatsLog.logStart(i);
            StatsLog.logStop(i);
        }
    }
}
