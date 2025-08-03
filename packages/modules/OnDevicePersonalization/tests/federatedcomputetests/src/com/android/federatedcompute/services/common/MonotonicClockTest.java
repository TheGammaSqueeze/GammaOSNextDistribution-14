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

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class MonotonicClockTest {
    @Test
    public void testCurrentTimeMillis() throws Exception {
        Clock clock = MonotonicClock.getInstance();
        long start = clock.currentTimeMillis();
        Thread.sleep(200);
        long end = clock.currentTimeMillis();
        // Processing/Calling function may cost time, so elapse time sometimes is a little larger
        // than sleep time.
        assertThat(end - start).isAtLeast(200);
    }
}
