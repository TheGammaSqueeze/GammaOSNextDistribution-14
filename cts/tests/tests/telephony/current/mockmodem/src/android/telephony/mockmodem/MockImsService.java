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

package android.telephony.mockmodem;

import static android.hardware.radio.ims.EpsFallbackReason.NO_NETWORK_RESPONSE;
import static android.hardware.radio.ims.EpsFallbackReason.NO_NETWORK_TRIGGER;
import static android.telephony.ims.feature.MmTelFeature.EPS_FALLBACK_REASON_INVALID;
import static android.telephony.ims.feature.MmTelFeature.EPS_FALLBACK_REASON_NO_NETWORK_RESPONSE;
import static android.telephony.ims.feature.MmTelFeature.EPS_FALLBACK_REASON_NO_NETWORK_TRIGGER;
import static android.telephony.ims.feature.MmTelFeature.IMS_TRAFFIC_TYPE_UT_XCAP;

import android.telephony.ims.feature.MmTelFeature;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MockImsService {
    private static final String TAG = "MockImsService";
    private static final int INVALID = -1;

    public static final int LATCH_WAIT_FOR_SRVCC_CALL_INFO = 0;
    public static final int LATCH_WAIT_FOR_TRIGGER_EPS_FALLBACK = 1;
    public static final int LATCH_WAIT_FOR_START_IMS_TRAFFIC = 2;
    public static final int LATCH_WAIT_FOR_STOP_IMS_TRAFFIC = 3;
    private static final int LATCH_MAX = 4;

    private final CountDownLatch[] mLatches = new CountDownLatch[LATCH_MAX];
    private final List<MockSrvccCall> mSrvccCalls = new ArrayList<>();
    private int mEpsFallbackReason = INVALID;

    private final int[] mStartImsTrafficSerial = new int[IMS_TRAFFIC_TYPE_UT_XCAP + 1];
    private final boolean[] mImsTrafficState = new boolean[IMS_TRAFFIC_TYPE_UT_XCAP + 1];
    private final int[] mImsTrafficToken = new int[IMS_TRAFFIC_TYPE_UT_XCAP + 1];
    private int[] mAnbrValues = new int[3];

    public MockImsService() {
        for (int i = 0; i < LATCH_MAX; i++) {
            mLatches[i] = new CountDownLatch(1);
        }
    }

    /**
     * Sets SRVCC call information.
     * @param srvccCalls The list of call information.
     */
    public void setSrvccCallInfo(android.hardware.radio.ims.SrvccCall[] srvccCalls) {
        mSrvccCalls.clear();

        if (srvccCalls != null) {
            for (android.hardware.radio.ims.SrvccCall call : srvccCalls) {
                mSrvccCalls.add(new MockSrvccCall(call));
            }
        }
        countDownLatch(LATCH_WAIT_FOR_SRVCC_CALL_INFO);
    }

    /** @return The list of {@link MockSrvccCall} instances. */
    public List<MockSrvccCall> getSrvccCalls() {
        return mSrvccCalls;
    }

    /**
     * Sets the reason that caused EPS fallback.
     *
     * @param reason The reason that caused EPS fallback.
     */
    public void setEpsFallbackReason(int reason) {
        mEpsFallbackReason = reason;
        countDownLatch(LATCH_WAIT_FOR_TRIGGER_EPS_FALLBACK);
    }

    /**
     * Returns the reason that caused EPS fallback.
     *
     * @return the reason that caused EPS fallback.
     */
    public @MmTelFeature.EpsFallbackReason int getEpsFallbackReason() {
        switch (mEpsFallbackReason) {
            case NO_NETWORK_TRIGGER: return EPS_FALLBACK_REASON_NO_NETWORK_TRIGGER;
            case NO_NETWORK_RESPONSE: return EPS_FALLBACK_REASON_NO_NETWORK_RESPONSE;
            default: return EPS_FALLBACK_REASON_INVALID;
        }
    }

    /**
     * Clears the EPS fallback reason.
     */
    public void resetEpsFallbackReason() {
        mEpsFallbackReason = INVALID;
    }

    /**
     * Notifies the type of upcoming IMS traffic.
     *
     * @param serial Serial number of request.
     * @param token A nonce to identify the request.
     * @param trafficType IMS traffic type like registration, voice, video, SMS, emergency, and etc.
     */
    public void startImsTraffic(int serial, int token, int trafficType) {
        mStartImsTrafficSerial[trafficType] = serial;
        mImsTrafficState[trafficType] = true;
        mImsTrafficToken[trafficType] = token;
        countDownLatch(LATCH_WAIT_FOR_START_IMS_TRAFFIC);
    }

    /**
     * Notifies IMS traffic has been stopped.
     *
     * @param token The token assigned by startImsTraffic.
     */
    public void stopImsTraffic(int token) {
        for (int i = 0; i < mImsTrafficToken.length; i++) {
            if (mImsTrafficToken[i] == token) {
                mImsTrafficState[i] = false;
                mImsTrafficToken[i] = INVALID;
                break;
            }
        }
        countDownLatch(LATCH_WAIT_FOR_STOP_IMS_TRAFFIC);
    }

    /**
     * Returns whether the given IMS traffic type is started or not.
     *
     * @param trafficType The IMS traffic type.
     * @return boolean true if the given IMS traffic type is started.
     */
    public boolean isImsTrafficStarted(@MmTelFeature.ImsTrafficType int trafficType) {
        if (trafficType < 0 || trafficType >= mImsTrafficState.length) return false;
        return mImsTrafficState[trafficType];
    }

    /**
     * Gets the token of the given IMS traffic type.
     *
     * @param trafficType The IMS traffic type.
     * @return The token associated with given traffic type.
     */
    public int getImsTrafficToken(@MmTelFeature.ImsTrafficType int trafficType) {
        if (trafficType < 0 || trafficType >= mImsTrafficState.length) return 0;
        return mImsTrafficToken[trafficType];
    }

    /**
     * Gets the token of the given IMS traffic type.
     *
     * @param trafficType The IMS traffic type.
     * @return The serial of startImsTraffic request.
     */
    public int getImsTrafficSerial(@MmTelFeature.ImsTrafficType int trafficType) {
        if (trafficType < 0 || trafficType >= mImsTrafficState.length) return 0;
        return mStartImsTrafficSerial[trafficType];
    }

    /**
     * Clears the IMS traffic state.
     */
    public void clearImsTrafficState() {
        for (int i = 0; i < mImsTrafficToken.length; i++) {
            mStartImsTrafficSerial[i] = 0;
            mImsTrafficState[i] = false;
            mImsTrafficToken[i] = INVALID;
        }
    }

    /**
     * Access Network Bitrate Recommendation Query (ANBRQ), see 3GPP TS 26.114.
     * This API triggers radio to send ANBRQ message to the access network to query the
     * desired bitrate.
     *
     * @param mediaType MediaType is used to identify media stream such as audio or video.
     * @param direction Direction of this packet stream (e.g. uplink or downlink).
     * @param bitsPerSecond This value is the bitrate requested by the other party UE through
     *        RTP CMR, RTCPAPP or TMMBR, and ImsStack converts this value to the MAC bitrate
     *        (defined in TS36.321, range: 0 ~ 8000 kbit/s).
     */
    public void sendAnbrQuery(int mediaType, int direction, int bitsPerSecond) {
        Log.d(TAG, "mockImsService - sendAnbrQuery mediaType=" + mediaType + ", direction="
                + direction + ", bitsPerSecond" + bitsPerSecond);

        try {
            mAnbrValues[0] = mediaType;
            mAnbrValues[1] = direction;
            mAnbrValues[2] = bitsPerSecond;
        } catch (Exception e) {
            Log.e(TAG, "SendAnbrQuery is not called");
        }
    }

    /**
     * Clears the Anbr values.
     */
    public void resetAnbrValues() {
        mAnbrValues[0] = INVALID;
        mAnbrValues[1] = INVALID;
        mAnbrValues[2] = INVALID;
    }

    /**
     * Returns the Anbr values triggered by Anbr Query.
     *
     * @return the Anbr values triggered by Anbr Query.
     */
    public int[] getAnbrValues() {
        return mAnbrValues;
    }

    private void countDownLatch(int latchIndex) {
        synchronized (mLatches) {
            mLatches[latchIndex].countDown();
        }
    }

    private void resetLatch(int latchIndex) {
        synchronized (mLatches) {
            mLatches[latchIndex] = new CountDownLatch(1);
        }
    }

    /**
     * Waits for the event of voice service.
     *
     * @param latchIndex The index of the event.
     * @param waitMs The timeout in milliseconds.
     * @return {@code true} if the event happens.
     */
    public boolean waitForLatchCountdown(int latchIndex, long waitMs) {
        boolean complete = false;
        try {
            CountDownLatch latch;
            synchronized (mLatches) {
                latch = mLatches[latchIndex];
            }
            long startTime = System.currentTimeMillis();
            complete = latch.await(waitMs, TimeUnit.MILLISECONDS);
            Log.i(TAG, "Latch " + latchIndex + " took "
                    + (System.currentTimeMillis() - startTime) + " ms to count down.");
        } catch (InterruptedException e) {
        }
        synchronized (mLatches) {
            mLatches[latchIndex] = new CountDownLatch(1);
        }
        return complete;
    }

    /**
     * Resets the CountDownLatches
     */
    public void resetAllLatchCountdown() {
        synchronized (mLatches) {
            for (int i = 0; i < LATCH_MAX; i++) {
                mLatches[i] = new CountDownLatch(1);
            }
        }
    }
}
