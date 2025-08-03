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

import java.util.List;

class JsonVhalEventGenerator implements VhalEventGenerator {

    // Exactly one iteration is required for JSON-based end-to-end test
    private static final String NUM_OF_ITERATION = "1";

    private final CarTestManager mCarTestManager;
    private final long mVhalDumpTimeoutMs;
    private String mContent;
    private String mId;

    JsonVhalEventGenerator(CarTestManager carTestManager, long vhalDumpTimeoutMs) {
        mCarTestManager = carTestManager;
        mVhalDumpTimeoutMs = vhalDumpTimeoutMs;
    }

    public JsonVhalEventGenerator setJson(String content) {
        mContent = content;
        return this;
    }

    @Override
    public void start() throws Exception {
        List<String> options = List.of("--genfakedata", "--startjson", "--content", mContent,
                NUM_OF_ITERATION);

        String output = mCarTestManager.dumpVhal(options, mVhalDumpTimeoutMs);

        assertThat(output).contains("started successfully");
        mId = output.substring(output.indexOf("ID: ") + 4);
    }

    @Override
    public void stop() throws Exception {
        List<String> options = List.of("--genfakedata", "--stopjson", mId);

        // Ignore output since the generator might have already stopped.
        mCarTestManager.dumpVhal(options, mVhalDumpTimeoutMs);
    }
}
