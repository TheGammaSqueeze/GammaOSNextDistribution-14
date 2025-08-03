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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Checks that chreGetTime() is reasonable.
 *
 * We check that it's monotonically increasing, and mostly in line with
 * the application processor's notion of time passing.
 *
 * Protocol:
 * Host:    GET_TIME, no data
 * Nanoapp: CONTINUE, 64-bit timestamp (little endian)
 * [2.5 second pause]
 * Host:    CONTINUE, no data
 * Nanoapp: CONTINUE, 64-bit timestamp (little endian)
 * [Host declares pass]
 */
public class ContextHubGetTimeTestExecutor extends ContextHubGeneralTestExecutor {
    private static final ContextHubTestConstants.TestNames TEST_NAME =
            ContextHubTestConstants.TestNames.GET_TIME;

    // Since the CHRE timestamps are in nanoseconds, we do most of our times
    // in nanoseconds here (although the Timer.schedule() only works with
    // milliseconds; we elect for the shorter naming for our more common unit).

    // 2.5 seconds
    private static final long SLEEP_DURATION_MILLISECONDS = 2500;
    private static final long SLEEP_DURATION_NS =
            SLEEP_DURATION_MILLISECONDS * 1000 * 1000;

    // We expect some degree of inconsistency between our host/CHRE
    // transaction overheads, so we allow a healthy amount of tolerance
    // here.  This isn't a precision check, but should hopefully make
    // sure the CHRE is within the right ballpark.
    // 0.5 seconds
    private static final long TOLERANCE_NS = 500 * 1000 * 1000;

    private int mContinueCount = 0;
    private long mFirstTimestamp;

    private Timer mTimer = new Timer();

    public ContextHubGetTimeTestExecutor(ContextHubManager manager, ContextHubInfo info,
            NanoAppBinary binary) {
        super(manager, info,
                new GeneralTestNanoApp(binary, ContextHubTestConstants.TestNames.GET_TIME));
    }

    @Override
    protected void handleMessageFromNanoApp(long nanoAppId,
            ContextHubTestConstants.MessageType type, byte[] data) {
        if (type != ContextHubTestConstants.MessageType.CONTINUE) {
            fail("Unexpected message type " + type);
            return;
        }

        assertEquals("Expected exactly 8 bytes with CONTINUE message.",
                8, data.length);
        long timestamp = ByteBuffer.wrap(data)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getLong();

        mContinueCount++;
        if (mContinueCount == 1) {
            mFirstTimestamp = timestamp;
            // We perform our delay before the next message in a separate
            // thread, so we're not stalling the ContextHubManager/Service
            // from servicing any other messages or events.
            mTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    byte[] emptyData = new byte[0];
                    sendMessageToNanoAppOrFail(nanoAppId,
                            ContextHubTestConstants.MessageType.CONTINUE.asInt(),
                            emptyData);
                }
            }, SLEEP_DURATION_MILLISECONDS);
            // We got our first timestamp and started the (delayed) send
            // message.  Nothing more to do this time.
            return;
        }
        // This is our second call.
        long timeDifference = timestamp - mFirstTimestamp;
        assertTrue("Subsequent timestamps did not monotonically increase",
                timeDifference > 0);
        assertTrue("Expected timestamp difference around " + SLEEP_DURATION_NS
                        + " nanoseconds, but got " + timeDifference,
                ((timeDifference > (SLEEP_DURATION_NS - TOLERANCE_NS))
                        && (timeDifference
                        < (SLEEP_DURATION_NS + TOLERANCE_NS))));

        // The CHRE gave us two reasonable timestamps.  And the nanoapp didn't
        // signal any issues with its testing.  We're good.
        pass();
    }

    @Override
    public void deinit() {
        // If our test aborted for another reason, we want to make sure
        // we cancel any timer that we might have set, so we don't send
        // a spurious message to the CHRE after this test has completed.
        mTimer.cancel();

        super.deinit();
    }
}
