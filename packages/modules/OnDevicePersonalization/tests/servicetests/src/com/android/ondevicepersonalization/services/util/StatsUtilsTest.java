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

package com.android.ondevicepersonalization.services.util;

import static org.junit.Assert.assertEquals;

import android.adservices.ondevicepersonalization.CalleeMetadata;
import android.adservices.ondevicepersonalization.Constants;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class StatsUtilsTest {
    @Test public void testServiceReturnsElapsedTime() {
        CalleeMetadata metadata = new CalleeMetadata.Builder().setElapsedTimeMillis(100).build();
        Bundle bundle = new Bundle();
        bundle.putParcelable(Constants.EXTRA_CALLEE_METADATA, metadata);
        assertEquals(50, StatsUtils.getOverheadLatencyMillis(150, bundle));
    }

    @Test public void testServiceReturnsNoResult() {
        assertEquals(0, StatsUtils.getOverheadLatencyMillis(150, null));
    }

    @Test public void testServiceReturnsNoMetadata() {
        assertEquals(0, StatsUtils.getOverheadLatencyMillis(150, new Bundle()));
    }

    @Test public void testServiceReturnsNegativeElapsedTime() {
        CalleeMetadata metadata = new CalleeMetadata.Builder().setElapsedTimeMillis(-1).build();
        Bundle bundle = new Bundle();
        bundle.putParcelable(Constants.EXTRA_CALLEE_METADATA, metadata);
        assertEquals(0, StatsUtils.getOverheadLatencyMillis(150, bundle));
    }

    @Test public void testServiceReturnsTooHighElapsedTime() {
        CalleeMetadata metadata = new CalleeMetadata.Builder().setElapsedTimeMillis(300).build();
        Bundle bundle = new Bundle();
        bundle.putParcelable(Constants.EXTRA_CALLEE_METADATA, metadata);
        assertEquals(0, StatsUtils.getOverheadLatencyMillis(150, bundle));
    }
}
