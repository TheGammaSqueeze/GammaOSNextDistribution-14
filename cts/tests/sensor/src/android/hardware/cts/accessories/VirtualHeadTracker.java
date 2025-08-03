/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.hardware.cts.accessories;

import static org.junit.Assert.*;

import com.android.cts.input.HidResultData;

/** Declares virtual head trackers for SensorHeadTrackerTest registered through /dev/uhid/ */
public class VirtualHeadTracker extends HidCommand {
    private static final String TAG = "VirtualHeadTracker";
    private boolean mIsHeadTrackerEnabled = false;
    private VirtualHtRunnable mVirtualHtRunnable = new VirtualHtRunnable();

    /*
        The orientation, angular velocity and discontinuity
        count for the virtual head tracker.
        In C++ this would be:
                struct __attribute__((packed)) {
                    int16_t orientation[3];
                    int16_t angularVelocity[3];
                    uint8_t discontinuityCount;
                };
    */
    private static final float[] ORIENTATION = {
        (float) Math.PI / 2, (float) -Math.PI / 3, (float) Math.PI / 4
    };
    private static final float[] ANGULAR_VELOCITY = {
        (float) Math.PI, (float) -Math.PI * 2, (float) Math.PI * 3
    };
    private byte mDiscontinuityCount = 0x00;
    public static final float[] HEAD_TRACKER_VALUES = {
        ORIENTATION[0],
        ORIENTATION[1],
        ORIENTATION[2],
        ANGULAR_VELOCITY[0],
        ANGULAR_VELOCITY[1],
        ANGULAR_VELOCITY[2]
    };

    public void incDiscontinuityCount() {
        mDiscontinuityCount++;
    }

    private String generateReport() {
        byte report = 1;

        // HEAD_TRACKER_VALUES is the end results when it goes through sendHidReport.
        return "["
                + String.format("0x%02x,", report)
                + orientationFloatToStr(ORIENTATION)
                + angularVelocityFloatToStr(ANGULAR_VELOCITY)
                + String.format("0x%02x", mDiscontinuityCount)
                + "]";
    }

    /**
     * Convert an orientation array from float to the int16 HID format as a String of bytes.
     *
     * @param orientation a rotation vector represented as float[3] of x, y, z in radians with range
     *     [-pi, pi]; magnitude must be in range [0, pi]
     */
    private static String orientationFloatToStr(float[] orientation) {
        String stringOfBytes = new String();
        for (float orient : orientation) {
            int hidValue = (int) Math.round(orient / Math.PI * Short.MAX_VALUE);
            stringOfBytes += intToShortByteStr(hidValue);
        }
        return stringOfBytes;
    }

    /**
     * Convert an orientation array from float to the int16 HID format as a String of bytes.
     *
     * @param angularVelocity is radians per second, in range [-32, 32]
     */
    private static String angularVelocityFloatToStr(float[] angularVelocity) {
        String stringOfBytes = new String();
        for (float angle : angularVelocity) {
            int hidValue = Math.round(angle / 32 * Short.MAX_VALUE);
            stringOfBytes += intToShortByteStr(hidValue);
        }
        return stringOfBytes;
    }

    /** Clamps an int to short range and returns it as a string in little endian order */
    private static String intToShortByteStr(int hidValue) {
        hidValue = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, hidValue));
        return String.format("0x%02x,0x%02x,", hidValue & 0xff, (hidValue & 0xff00) >> 8);
    }

    @Override
    protected void processResults(HidResultData results) {
        if (mIsHeadTrackerEnabled) {
            mVirtualHtRunnable.stop();
        }
        mIsHeadTrackerEnabled = (results.reportData[1] & 0x03) == 0x03;
        setGetReportResponse(results);
        sendSetReportReply(true);
        if (mIsHeadTrackerEnabled) {
            mVirtualHtRunnable.start();
        }
    }

    private class VirtualHtRunnable implements Runnable {
        private Thread mVirtualHeadTrackerThread;

        public void start() {
            if (mVirtualHeadTrackerThread != null) {
                fail("mVirtualHeadTrackerThread should be null");
            } else {
                mVirtualHeadTrackerThread = new Thread(this);
            }
            mVirtualHeadTrackerThread.start();
        }

        public void stop() {
            mVirtualHeadTrackerThread.interrupt();
            try {
                mVirtualHeadTrackerThread.join();
            } catch (InterruptedException ex) {

            }
            mVirtualHeadTrackerThread = null;
        }

        public void run() {
            int count = 0;
            while (!mVirtualHeadTrackerThread.isInterrupted()) {
                try {
                    Thread.sleep(10);
                    sendHidReport(generateReport());
                } catch (InterruptedException ex) {
                    return;
                }
            }
        }
    }
}
