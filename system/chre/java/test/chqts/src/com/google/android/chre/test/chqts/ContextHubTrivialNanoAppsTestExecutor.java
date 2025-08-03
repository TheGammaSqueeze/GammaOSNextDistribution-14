/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.chre.test.chqts;

import android.hardware.location.ContextHubInfo;
import android.hardware.location.ContextHubManager;
import android.hardware.location.NanoAppBinary;

import com.google.android.utils.chre.ChreTestUtil;

import org.junit.Assert;

/**
 * Tests of the absolutely most basic valid nanoapps.
 *
 * This tests that a nanoapp loads successfully/unsuccessfully, as intended. For instance,
 * the "do_nothing" nanoapp is expected to succeed, and "fail_startup" is expected to fail.
 */
public class ContextHubTrivialNanoAppsTestExecutor {
    private final ContextHubManager mContextHubManager;

    private final ContextHubInfo mContextHubInfo;

    public ContextHubTrivialNanoAppsTestExecutor(ContextHubManager manager, ContextHubInfo info) {
        mContextHubManager = manager;
        mContextHubInfo = info;
    }

    /**
     * Run the test.
     */
    public void run(NanoAppBinary binary, boolean expectLoadSuccess) {
        boolean success = ChreTestUtil.loadNanoApp(mContextHubManager, mContextHubInfo, binary);

        if (success) {
            ChreTestUtil.unloadNanoAppAssertSuccess(mContextHubManager, mContextHubInfo,
                    binary.getNanoAppId());
        }

        Assert.assertEquals("Unexpected load result", expectLoadSuccess, success);
    }
}
