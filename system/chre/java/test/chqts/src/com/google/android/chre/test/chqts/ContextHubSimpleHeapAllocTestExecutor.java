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
 * Performs simple allocation and freeing from the heap.
 *
 * Requires the host to send an additional message to tell us to free.
 *
 * Protocol:
 * Host:    SIMPLE_HEAP_ALLOC
 * Nanoapp: CONTINUE, no data
 * Host:    CONTINUE, no data
 * Nanoapp: SUCCESS, no data
 *
 * This is paired with general_test/simple_heap_alloc_test.h.
 */
public class ContextHubSimpleHeapAllocTestExecutor extends ContextHubGeneralTestExecutor {
    private boolean mFirstMessage = true;

    public ContextHubSimpleHeapAllocTestExecutor(ContextHubManager manager, ContextHubInfo info,
            NanoAppBinary binary) {
        super(manager, info, new GeneralTestNanoApp(binary,
                ContextHubTestConstants.TestNames.SIMPLE_HEAP_ALLOC));
    }

    @Override
    protected void handleMessageFromNanoApp(long nanoAppId,
            ContextHubTestConstants.MessageType type, byte[] data) {
        if (type != ContextHubTestConstants.MessageType.CONTINUE) {
            fail("Unexpected message type " + type);
            return;
        }

        assertTrue("Multiple CONTINUE messages from nanoapp",
                mFirstMessage);
        mFirstMessage = false;
        assertEquals("Expected 0 bytes data from nanoapp CONTINUE; "
                        + "got " + data.length + " bytes",
                0, data.length);

        byte[] emptyData = new byte[0];
        sendMessageToNanoAppOrFail(nanoAppId, ContextHubTestConstants.MessageType.CONTINUE.asInt(),
                emptyData);
    }
}
