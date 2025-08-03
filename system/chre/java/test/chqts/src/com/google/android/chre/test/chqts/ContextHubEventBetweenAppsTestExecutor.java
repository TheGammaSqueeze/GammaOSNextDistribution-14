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

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Verify sending a CHRE event between two nanoapps.
 *
 * We also check that we get the correct nanoApp IDs and sane nanoApp instance IDs.
 *
 * Protocol:
 * There are two nanoapps here, so we'll talk in term of app0 and app1.
 * All data involving the host is sent little endian.
 *
 * Host to app0:  EVENT_BETWEEN_APPS0, no data
 * Host to app1:  EVENT_BETWEEN_APPS1, no data
 * app0 to host:  CONTINUE, 64-bit app ID, 32-bit instance ID
 * app1 to host:  CONTINUE, 64-bit app ID, 32-bit instance ID
 * Host to app1:  CONTINUE, app0's 32-bit instance ID
 * Host to app0:  CONTINUE, app1's 32-bit instance ID
 * [app0 sends message to app1]
 * app1 to host:  SUCCESS
 */
public class ContextHubEventBetweenAppsTestExecutor extends ContextHubGeneralTestExecutor {
    /**
     * A container class to store information supplied by the nanoapps.
     */
    private class AppInfo {
        public long appId;
        public int instanceId;
        public boolean haveInfo = false;
    }

    private final long mNanoAppId0;
    private final long mNanoAppId1;
    private AppInfo[] mAppInfo = new AppInfo[]{new AppInfo(), new AppInfo()};

    public ContextHubEventBetweenAppsTestExecutor(ContextHubManager manager, ContextHubInfo info,
            NanoAppBinary binary0, NanoAppBinary binary1) {
        super(manager, info, new GeneralTestNanoApp(binary0,
                        ContextHubTestConstants.TestNames.EVENT_BETWEEN_APPS0),
                new GeneralTestNanoApp(binary1,
                        ContextHubTestConstants.TestNames.EVENT_BETWEEN_APPS1));
        mNanoAppId0 = binary0.getNanoAppId();
        mNanoAppId1 = binary1.getNanoAppId();
    }

    @Override
    protected void handleMessageFromNanoApp(long nanoAppId,
            ContextHubTestConstants.MessageType type, byte[] data) {
        if (type != ContextHubTestConstants.MessageType.CONTINUE) {
            fail("Unexpected message type " + type);
            return;
        }

        int index = (nanoAppId == mNanoAppId0) ? 0 : 1;
        assertFalse("Multiple CONTINUE messages from app 0x" + Long.toHexString(nanoAppId),
                mAppInfo[index].haveInfo);

        ByteBuffer buffer = ByteBuffer.wrap(data)
                .order(ByteOrder.LITTLE_ENDIAN);
        try {
            mAppInfo[index].appId = buffer.getLong();
            mAppInfo[index].instanceId = buffer.getInt();
            mAppInfo[index].haveInfo = true;
        } catch (BufferUnderflowException e) {
            fail("Not enough data provided in CONTINUE message from 0x" + Long.toHexString(
                    nanoAppId));
        }
        assertFalse(
                "Too much data provided in CONTINUE message from 0x" + Long.toHexString(nanoAppId),
                buffer.hasRemaining());

        int otherIndex = 1 - index;
        if (!mAppInfo[otherIndex].haveInfo) {
            // We need to wait to get the info from the other app.
            return;
        }

        assertEquals("Incorrect app ID given for 0x" + Long.toHexString(mNanoAppId0),
                mNanoAppId0,
                mAppInfo[0].appId);
        assertEquals("Incorrect app ID given for 0x" + Long.toHexString(mNanoAppId1),
                mNanoAppId1,
                mAppInfo[1].appId);
        assertTrue("Both nanoapps given identical instance IDs",
                mAppInfo[0].instanceId != mAppInfo[1].instanceId);

        // Consistency checks pass.  We'll send the data down.
        buffer = ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(mAppInfo[0].instanceId);
        sendMessageToNanoAppOrFail(mNanoAppId1,
                ContextHubTestConstants.MessageType.CONTINUE.asInt(), buffer.array());

        buffer.clear();
        buffer.putInt(mAppInfo[1].instanceId);
        sendMessageToNanoAppOrFail(mNanoAppId0,
                ContextHubTestConstants.MessageType.CONTINUE.asInt(), buffer.array());
    }
}
