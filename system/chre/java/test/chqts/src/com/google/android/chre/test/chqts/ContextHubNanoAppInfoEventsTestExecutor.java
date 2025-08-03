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

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Verify chreConfigureNanoAppInfoEvents.
 *
 * Protocol:
 * There are two nanoapps here, so we'll talk in terms of the observer
 * and the performer. All data involving the host is sent little endian
 *
 * Host to observer: NANOAPP_INFO_EVENTS_OBSERVER, no data
 * observer to Host: CONTINUE
 * [Host starts performer]
 * performer to Host: CONTINUE, 64-bit app ID, 32-bit instance ID
 * [Host stops performer]
 * Host to observer: CONTINUE, performer's 32-bit instance ID
 * observer to host: SUCCESS
 */
public class ContextHubNanoAppInfoEventsTestExecutor extends ContextHubGeneralTestExecutor {
    private final long mObserverNanoAppId;
    private final NanoAppBinary mPerformerNanoAppBinary;

    public ContextHubNanoAppInfoEventsTestExecutor(ContextHubManager manager, ContextHubInfo info,
            NanoAppBinary observerBinary, NanoAppBinary performerBinary) {
        // Note that the performer nanoapp is not loaded/started at init, since it will be loaded
        // via the message from the observer nanoapp.
        super(manager, info, new GeneralTestNanoApp(observerBinary,
                        ContextHubTestConstants.TestNames.NANO_APP_INFO_EVENTS_OBSERVER),
                new GeneralTestNanoApp(performerBinary,
                        ContextHubTestConstants.TestNames.NANO_APP_INFO_EVENTS_PERFORMER,
                        false /* loadAtInit */));
        mObserverNanoAppId = observerBinary.getNanoAppId();
        mPerformerNanoAppBinary = performerBinary;
    }

    @Override
    protected void handleMessageFromNanoApp(long nanoAppId,
            ContextHubTestConstants.MessageType type, byte[] data) {
        if (type != ContextHubTestConstants.MessageType.CONTINUE) {
            fail("Unexpected message type " + type);
            return;
        }

        if (nanoAppId == mObserverNanoAppId) {
            if (!ChreTestUtil.loadNanoApp(getContextHubManager(), getContextHubInfo(),
                    mPerformerNanoAppBinary)) {
                fail("Failed to load performer nanoapp");
                return;
            }

            // Send message to have the PERFORMER actually start
            sendMessageToNanoAppOrFail(mPerformerNanoAppBinary.getNanoAppId(),
                    ContextHubTestConstants.TestNames.NANO_APP_INFO_EVENTS_PERFORMER.asInt(),
                    new byte[0] /* data */);
        } else {
            ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

            int performerInstanceId = 0;

            try {
                // Ignore the nanoApp ID
                buffer.getLong();
                performerInstanceId = buffer.getInt();
            } catch (BufferUnderflowException ex) {
                fail("Not enough data provided in CONTINUE message");
            }

            assertFalse("Too much data provided in CONTINUE message", buffer.hasRemaining());

            buffer = ByteBuffer.allocate(4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(performerInstanceId);

            if (!ChreTestUtil.unloadNanoApp(getContextHubManager(), getContextHubInfo(),
                    mPerformerNanoAppBinary.getNanoAppId())) {
                fail("Failed to unload performer nanoapp");
            }

            sendMessageToNanoAppOrFail(mObserverNanoAppId,
                    ContextHubTestConstants.MessageType.CONTINUE.asInt(), buffer.array());
        }
    }
}
