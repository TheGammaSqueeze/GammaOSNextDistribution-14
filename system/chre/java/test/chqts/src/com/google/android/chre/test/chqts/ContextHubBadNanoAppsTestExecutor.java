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

import java.util.Arrays;

/**
 * These are a series of tests which attempt to load various invalid NanoApps.
 *
 * We want to make sure these fail to load, and the system doesn't throw
 * unexpected exceptions or crash while attempting this.
 *
 * TODO (b/151159967): Adapt this test for < P builds.
 */
public class ContextHubBadNanoAppsTestExecutor {
    private final ContextHubManager mContextHubManager;

    private final ContextHubInfo mContextHubInfo;

    public ContextHubBadNanoAppsTestExecutor(ContextHubManager manager, ContextHubInfo info) {
        mContextHubManager = manager;
        mContextHubInfo = info;
    }

    public void noAppBinaryTest() {
        // TODO (b/151159967): Implement this test
    }

    public void noAppIdTest() {
        // TODO (b/151159967): Implement this test
    }

    public void emptyAppBinaryTest() {
        runInvalidBinaryTest(0 /* numBytes */);
    }

    public void smallInvalidAppBinaryTest() {
        runInvalidBinaryTest(64 /* numBytes */);
    }

    public void largeInvalidAppBinaryTest() {
        runInvalidBinaryTest(8192 /* numBytes */);
    }

    private void runInvalidBinaryTest(int numBytes) {
        byte[] buffer = new byte[numBytes];
        Arrays.fill(buffer, (byte) 0xFF);

        NanoAppBinary binary = new NanoAppBinary(buffer);
        Assert.assertFalse("Invalid nanoapp binary (" + numBytes
                + " bytes) returns true for hasValidHeader()", binary.hasValidHeader());

        boolean success = ChreTestUtil.loadNanoApp(mContextHubManager, mContextHubInfo, binary);
        Assert.assertFalse("Invalid nanoapp binary (" + numBytes + " bytes) successfully loaded",
                success);
    }
}
