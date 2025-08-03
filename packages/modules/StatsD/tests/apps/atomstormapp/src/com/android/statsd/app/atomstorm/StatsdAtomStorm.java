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

package com.android.statsd.app.atomstorm;

import android.util.StatsLog;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class StatsdAtomStorm {
    private static final int EventStormAtomsCount = 100000;

    /** Tests socket overflow. */
    @Test
    public void testLogManyAtomsBackToBack() throws Exception {
        // logging back to back many atoms to force socket overflow
        performAtomStorm(EventStormAtomsCount);
        // make pause to resolve socket overflow
        Thread.sleep(100);
        // give chance for libstatssocket send loss stats to statsd triggering successful logging
        performAtomStorm(1);
    }

    private void performAtomStorm(int iterations) {
        // single atom logging takes ~2us excluding JNI interactions
        for (int i = 0; i < iterations; i++) {
            StatsLog.logStart(i);
            StatsLog.logStop(i);
        }
    }
}
