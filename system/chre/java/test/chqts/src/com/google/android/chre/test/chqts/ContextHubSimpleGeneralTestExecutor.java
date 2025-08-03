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

/**
 * A class that can execute the CHQTS simple "general" nanoapp tests.
 */
public class ContextHubSimpleGeneralTestExecutor extends ContextHubGeneralTestExecutor {

    public ContextHubSimpleGeneralTestExecutor(ContextHubManager manager, ContextHubInfo info,
            NanoAppBinary binary, ContextHubTestConstants.TestNames testName) {
        super(manager, info, new GeneralTestNanoApp(binary, testName));
    }

    @Override
    protected void handleMessageFromNanoApp(long nanoAppId,
            ContextHubTestConstants.MessageType type, byte[] data) {
        // No specific messages for simple "general" tests.
        fail("Unexpected message type " + type);
    }
}
