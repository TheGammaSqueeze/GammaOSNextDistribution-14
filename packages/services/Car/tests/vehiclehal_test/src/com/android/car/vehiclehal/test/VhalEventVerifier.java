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

import android.car.hardware.CarPropertyValue;
import android.os.ConditionVariable;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;

/**
 * The verifier class is used to verify received VHAL events against expected events on-the-fly.
 * It is initialized with a list of expected events and moving down the list to verify received
 * events. The verifier object not reusable and should be discarded once the verification is done.
 * The verifier will provide formatted result for all mismatched events in sequence.
 */
class VhalEventVerifier {
    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private List<CarPropertyValue> mExpectedEvents;
    @GuardedBy("mLock")
    private List<CarPropertyValue> mReceivedEvents;
    // A pointer to keep track of the next expected event in the list
    @GuardedBy("mLock")
    private int mIdx;
    // Condition variable to notify waiting threads when verification is done or timeout.
    private ConditionVariable mCond;

    VhalEventVerifier(List<CarPropertyValue> expectedEvents) {
        mExpectedEvents = expectedEvents;
        mReceivedEvents = new ArrayList<>();
        mIdx = 0;
        mCond = new ConditionVariable(expectedEvents.isEmpty());
    }

    /**
     * Verification method that checks the equality of received event against expected event. Once
     * it reaches to the end of list, it will unblock the waiting threads. Note, the verification
     * method is not thread-safe. It assumes only a single thread is calling the method at all time.
     *
     * @param nextEvent to be verified
     */
    public void verify(CarPropertyValue nextEvent) {
        synchronized (mLock) {
            mReceivedEvents.add(nextEvent);
            if (mIdx >= mExpectedEvents.size()) {
                return;
            }
            CarPropertyValue expectedEvent = mExpectedEvents.get(mIdx);
            if (!Utils.areCarPropertyValuesEqual(expectedEvent, nextEvent)) {
                // We are not expecting this event, ignore this.
                return;
            }
            mIdx++;
            if (mIdx == mExpectedEvents.size()) {
                mCond.open();
            }
        }
    }

    public boolean waitForEnd(long timeout) {
        return mCond.block(timeout);
    }

    public String getResultString() {
        synchronized (mLock) {
            if (mIdx >= mExpectedEvents.size()) {
                return "";
            }
            return "Expected event: " + mExpectedEvents.get(mIdx) + " never received\n"
                    + "Received events: \n" + mReceivedEvents + "\n"
                    + "Expected events: \n" + mExpectedEvents;
        }
    }
}
