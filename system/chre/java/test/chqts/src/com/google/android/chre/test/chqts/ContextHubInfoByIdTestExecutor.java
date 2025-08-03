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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Verify NanoApp info by appId/instanceId
 *
 * Protocol:
 * Host to App: mTestName, no data
 * App to Host: CONTINUE, no data
 * Host to App: CONTINUE, 32-bit app version
 * App to Host: SUCCESS, no data
 */
public class ContextHubInfoByIdTestExecutor extends ContextHubGeneralTestExecutor {
    private boolean mFirstMessage = true;

    public ContextHubInfoByIdTestExecutor(ContextHubManager manager, ContextHubInfo info,
            NanoAppBinary binary, ContextHubTestConstants.TestNames testName) {
        super(manager, info, new GeneralTestNanoApp(binary, testName));
    }

    @Override
    protected void handleMessageFromNanoApp(long nanoAppId,
            ContextHubTestConstants.MessageType type, byte[] data) {
        if (type != ContextHubTestConstants.MessageType.CONTINUE) {
            fail("Unexpected message type " + type);
            return;
        }

        int version =
                ChreTestUtil.getNanoAppVersion(getContextHubManager(), getContextHubInfo(),
                        nanoAppId);
        ByteBuffer buffer = ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(version);

        sendMessageToNanoAppOrFail(nanoAppId, ContextHubTestConstants.MessageType.CONTINUE.asInt(),
                buffer.array());
    }
}
