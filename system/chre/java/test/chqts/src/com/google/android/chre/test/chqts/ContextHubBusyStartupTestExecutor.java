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
 * Executes the busy startup nanoapp test, which confirms that nanoappStart() can properly call
 * various methods. See the busy_startup.cc nanoapp code for details.
 *
 * Note that this nanoapp is not a general_test nanoapp, but uses the same protocol, so we use
 * the ContextHubGeneralTestExecutor class.
 */
public class ContextHubBusyStartupTestExecutor extends ContextHubGeneralTestExecutor {

    public ContextHubBusyStartupTestExecutor(ContextHubManager manager, ContextHubInfo info,
            NanoAppBinary binary) {
        // busy_startup nanoapp does not require a start message, since it does all the work during
        // nanoappStart().
        super(manager, info,
                new GeneralTestNanoApp(binary, ContextHubTestConstants.TestNames.INVALID_TEST,
                        true /* loadAtInit */,
                        false /* sendStartMessage */));
    }

    @Override
    protected void handleMessageFromNanoApp(long nanoAppId,
            ContextHubTestConstants.MessageType type, byte[] data) {
        // No specific messages for busy startup test.
        fail("Unexpected message type " + type);
    }
}
