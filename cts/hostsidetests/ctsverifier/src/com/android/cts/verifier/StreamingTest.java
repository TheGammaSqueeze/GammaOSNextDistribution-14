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

package com.android.cts.verifier;

import com.android.compatibility.common.util.CddTest;
import com.android.interactive.annotations.Interactive;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class StreamingTest extends CtsVerifierTest {

    @Interactive
    @Test
    // SingleDisplayMode
    @CddTest(requirements = "5.7/C-1-1,C-1-2,C-1-3")
    public void StreamingVideoTest() throws Exception {
        excludeFeatures("android.hardware.type.watch");

        runTest(".streamquality.StreamingVideoActivity");
    }
}
