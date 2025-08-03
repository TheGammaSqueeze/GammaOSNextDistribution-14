/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.util;

/**
 * An utility class to dump transition events across different car service components.
 * The output will be of the form
 * <p>
 * "Time <svc name>: [optional context information] changed from <from state> to <to state>"
 * This can be used in conjunction with the dump() method to dump this information through
 * adb shell dumpsys activity service com.android.car
 * <p>
 * A specific service in CarService can choose to use a circular buffer of N records to keep
 * track of the last N transitions.
 */
public final class TransitionLog {
    private String mServiceName; // name of the service or tag
    private Object mFromState; // old state
    private Object mToState; // new state
    private long mTimestampMs; // System.currentTimeMillis()
    private String mExtra; // Additional information as a String

    public TransitionLog(String name, Object fromState, Object toState, long timestamp,
            String extra) {
        this(name, fromState, toState, timestamp);
        mExtra = extra;
    }

    public TransitionLog(String name, Object fromState, Object toState, long timeStamp) {
        mServiceName = name;
        mFromState = fromState;
        mToState = toState;
        mTimestampMs = timeStamp;
    }

    private CharSequence timeToLog(long timestamp) {
        return android.text.format.DateFormat.format("MM-dd HH:mm:ss", timestamp);
    }

    @Override
    public String toString() {
        return timeToLog(mTimestampMs) + " " + mServiceName + ": "
                + (mExtra != null ? mExtra + " " : "")
                + "changed from " + mFromState + " to " + mToState;
    }
}
