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

import android.hardware.radio.voice.CdmaSignalInfoRecord;
import android.hardware.radio.voice.LastCallFailCause;
import android.hardware.radio.voice.LastCallFailCauseInfo;
import android.hardware.radio.voice.UusInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.GuardedBy;
import android.telephony.Annotation;
import android.telephony.DisconnectCause;
import android.util.Log;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class MockVoiceService {
    private static final int INVALID_CALL_ID = -1;
    private static final int MIN_CALL_ID = 1;
    private static final int MAX_CALL_ID = 9;
    private static final int MSG_REQUEST_DIALING_CALL = 1;
    private static final int MSG_REQUEST_RINGBACK_TONE = 2;
    private static final int MSG_REQUEST_ALERTING_CALL = 3;
    private static final int MSG_REQUEST_ACTIVATING_CALL = 4;
    private static final int MSG_REQUEST_DISCONNECTING_CALL = 5;
    private static final int MSG_REQUEST_INCOMING_CALL = 6;
    private static final int MSG_REQUEST_CALL_END = 7;

    private static final int EMERGENCY_TEMP_FAILURE = 325;
    private static final int EMERGENCY_PERM_FAILURE = 326;

    private String mTag = "MockVoiceService";
    private Handler mConfigHandler;
    private HandlerThread mCallStateHandlerThread;
    private MockCallStateHandler mCallStateHandler;

    @GuardedBy("mCallList")
    private final ArrayList<MockCallInfo> mCallList = new ArrayList<MockCallInfo>();

    private LastCallFailCauseInfo mLastCallEndInfo;
    private boolean mMuteMode;

    public class MockCallInfo {
        // Call state definition
        public static final int CALL_STATE_INIT = 0;
        public static final int CALL_STATE_ACTIVE = 1;
        public static final int CALL_STATE_HOLDING = 2;
        public static final int CALL_STATE_DIALING = 3;
        public static final int CALL_STATE_ALERTING = 4;
        public static final int CALL_STATE_INCOMING = 5;
        public static final int CALL_STATE_WAITING = 6;
        public static final int CALL_STATE_DISCONNECTING = 7;
        public static final int CALL_STATE_END = 8;

        // Call presentation definition
        public static final int CALL_PRESENTATION_ALLOWED = 0;
        public static final int CALL_PRESENTATION_RESTRICTED = 1;
        public static final int CALL_PRESENTATION_UNKNOWN = 2;
        public static final int CALL_PRESENTATION_PAYPHONE = 3;

        // Audio quality definition
        public static final int AUDIO_QUALITY_UNSPECIFIED = 0;
        public static final int AUDIO_QUALITY_AMR = 1;
        public static final int AUDIO_QUALITY_AMR_WB = 2;
        public static final int AUDIO_QUALITY_GSM_EFR = 3;
        public static final int AUDIO_QUALITY_GSM_FR = 4;
        public static final int AUDIO_QUALITY_GSM_HR = 5;
        public static final int AUDIO_QUALITY_EVRC = 6;
        public static final int AUDIO_QUALITY_EVRC_B = 7;
        public static final int AUDIO_QUALITY_EVRC_WB = 8;
        public static final int AUDIO_QUALITY_EVRC_NW = 9;

        // Call type definition
        public static final int CALL_TYPE_VOICE = 0;
        public static final int CALL_TYPE_VIDEO = 1;
        public static final int CALL_TYPE_EMERGENCY = 2;
        public static final int CALL_TYPE_CDMA_VOICE = 3;
        public static final int CALL_TYPE_CDMA_EMERGENCY = 4;

        // CLIR type definition
        public static final int CLIR_TYPE_DEFAULT = 0;
        public static final int CLIR_TYPE_INVOCATION = 1;
        public static final int CLIR_TYPE_SUPPRESSION = 2;

        // Default type of address
        private static final int DEFAULT_TOA = 145;

        private int mState;
        private int mIndex;
        private int mToa;
        private byte mAls;
        private boolean mIsMpty;
        private boolean mIsMT;
        private boolean mIsVoice;
        private boolean mIsVoicePrivacy;
        private String mNumber;
        private int mNumberPresentation;
        private String mName;
        private int mNamePresentation;
        private UusInfo[] mUusInfo;
        private int mAudioQuality;
        private String mForwardedNumber;
        private int mCallType;
        private int mClir;
        private CdmaSignalInfoRecord mCdmaSignalInfoRecord;
        private MockCallControlInfo mCallControlInfo;
        private int mCategories;
        private String[] mUrns;
        private int mRouting;

        @GuardedBy("mTimerList")
        private final ArrayList<Timer> mTimerList = new ArrayList<Timer>();

        private final class MockCallStateTimerTask extends TimerTask {
            private Timer mTimer;
            private int mCallId;
            private int mEvent;

            MockCallStateTimerTask(Timer timer, int callId, int event) {
                mTimer = timer;
                mCallId = callId;
                mEvent = event;
            }

            @Override
            public void run() {
                Log.d(
                        mTag,
                        "Timer task - triggering call state event = "
                                + getCallStateRequestEventStr(mEvent)
                                + " for call id = "
                                + mCallId);
                mCallStateHandler.obtainMessage(mEvent, mCallId).sendToTarget();
                synchronized (mTimerList) {
                    mTimerList.remove(mTimer);
                }
            }
        }

        public MockCallInfo(
                boolean isMT,
                String address,
                int clir,
                UusInfo[] uusInfo,
                int callType,
                MockCallControlInfo callControlInfo) {
            mState = CALL_STATE_INIT;
            mIndex = generateCallId();
            mToa = DEFAULT_TOA;
            mNumber = address;
            mIsMT = isMT;
            mClir = clir;
            mUusInfo = uusInfo;
            mCallType = callType;
            mCdmaSignalInfoRecord = null;
            if (callControlInfo == null) {
                mCallControlInfo = new MockCallControlInfo();
                Log.w(mTag, "No call control info. Using default instead.");
            } else {
                mCallControlInfo = callControlInfo;
            }
        }

        public MockCallInfo(
                boolean isMT,
                String address,
                int categories,
                String[] urns,
                int routing,
                int callType,
                MockCallControlInfo callControlInfo) {
            mState = CALL_STATE_INIT;
            mIndex = generateCallId();
            mToa = DEFAULT_TOA;
            mNumber = address;
            mIsMT = isMT;
            mUusInfo = new UusInfo[0];
            mCallType = callType;
            mCategories = categories;
            mUrns = urns;
            mRouting = routing;
            if (callControlInfo == null) {
                mCallControlInfo = new MockCallControlInfo();
                Log.w(mTag, "No call control info. Using default instead.");
            } else {
                mCallControlInfo = callControlInfo;
            }
        }

        public int getCallState() {
            return mState;
        }

        public void setCallState(int state) {
            mState = state;
        }

        public int getCallId() {
            return mIndex;
        }

        public void setCallId(int callId) {
            mIndex = callId;
        }

        public int getCallToa() {
            return mToa;
        }

        public void setCallToa(int toa) {
            mToa = toa;
        }

        public byte getCallAls() {
            return mAls;
        }

        public void setCallAls(byte als) {
            mAls = als;
        }

        public boolean isMpty() {
            return mIsMpty;
        }

        public void setMpty(boolean isMpty) {
            mIsMpty = isMpty;
        }

        public boolean isMT() {
            return mIsMT;
        }

        public void setMT(boolean isMT) {
            mIsMT = isMT;
        }

        public boolean isVoice() {
            return mIsVoice;
        }

        public void setVoice(boolean isVoice) {
            mIsVoice = isVoice;
        }

        public boolean isVoicePrivacy() {
            return mIsVoicePrivacy;
        }

        public void setVoicePrivacy(boolean isVoicePrivacy) {
            mIsVoicePrivacy = isVoicePrivacy;
        }

        public String getNumber() {
            return mNumber;
        }

        public void setNumber(String number) {
            mNumber = number;
        }

        public int getNumberPresentation() {
            return mNumberPresentation;
        }

        public void setNumberPresentation(int numberPresentation) {
            mNumberPresentation = numberPresentation;
        }

        public String getName() {
            return mName;
        }

        public void setName(String name) {
            mName = name;
        }

        public int getNamePresentation() {
            return mNamePresentation;
        }

        public void setNamePresentation(int namePresentation) {
            mNamePresentation = namePresentation;
        }

        public UusInfo[] getUusInfo() {
            return mUusInfo;
        }

        public void setUusInfo(UusInfo[] uusInfo) {
            mUusInfo = uusInfo;
        }

        public int getAudioQuality() {
            return mAudioQuality;
        }

        public void setAudioQuality(int audioQuality) {
            mAudioQuality = audioQuality;
        }

        public String getForwardedNumber() {
            return mForwardedNumber;
        }

        public void setForwardedNumber(String forwardedNumber) {
            mForwardedNumber = forwardedNumber;
        }

        public int getClir() {
            return mClir;
        }

        public void setClir(int clir) {
            mClir = clir;
        }

        public int getCallType() {
            return mCallType;
        }

        public void setCallType(int callType) {
            mCallType = callType;
        }

        public void dump() {
            Log.d(
                    mTag,
                    "mState = "
                            + mState
                            + ", mIndex = "
                            + mIndex
                            + ", mToa = "
                            + mToa
                            + ", mAls = "
                            + mAls
                            + ", mIsMpty = "
                            + mIsMpty
                            + ", mIsVoice = "
                            + mIsVoice
                            + ", mIsvoicePrivacy = "
                            + mIsVoicePrivacy
                            + ", mNumber = "
                            + mNumber
                            + ", mNumberPresentation = "
                            + mNumberPresentation
                            + ", mName = "
                            + mName
                            + ", mNamePresentation = "
                            + mNamePresentation
                            + ", mAudioQuality = "
                            + mAudioQuality
                            + ", mForwardedNumber = "
                            + mForwardedNumber
                            + ", mCallType = "
                            + mCallType
                            + ", mClir = "
                            + mClir);
        }

        public CdmaSignalInfoRecord getCdmaSignalInfoRecord() {
            return mCdmaSignalInfoRecord;
        }

        public void setCdmaSignalInfoRecord(CdmaSignalInfoRecord cdmaSignalInfoRecord) {
            mCdmaSignalInfoRecord = cdmaSignalInfoRecord;
        }

        public MockCallControlInfo getCallControlInfo() {
            return mCallControlInfo;
        }

        public void addCallStateTimerTask(int callId, int event, long duration) {
            Timer timer = new Timer(false);
            MockCallStateTimerTask timerTask = new MockCallStateTimerTask(timer, callId, event);
            if (timer != null && timerTask != null) {
                timer.schedule(timerTask, duration);
                synchronized (mTimerList) {
                    if (mTimerList != null) {
                        mTimerList.add(timer);
                    }
                }
            } else {
                Log.e(
                        mTag,
                        "Failed to start timer for event = " + getCallStateRequestEventStr(event));
            }
        }

        public void clearAllTimers() {
            synchronized (mTimerList) {
                if (mTimerList != null && mTimerList.size() > 0) {
                    for (int i = 0; i < mTimerList.size(); i++) {
                        mTimerList.get(i).cancel();
                    }
                    mTimerList.clear();
                }
            }
        }

        public void destroy() {
            clearAllTimers();
        }
    }

    public MockVoiceService(Handler handler) {
        mConfigHandler = handler;
        mLastCallEndInfo = new LastCallFailCauseInfo();
        initMockVoiceService();

        // Start call state handler
        mCallStateHandlerThread = new HandlerThread(mTag);
        mCallStateHandlerThread.start();
        mCallStateHandler = new MockCallStateHandler(mCallStateHandlerThread.getLooper());
    }

    public void destroy() {
        Log.e(mTag, "destroy");
        clearAllCalls();
        if (mCallStateHandlerThread != null) {
            mCallStateHandlerThread.quitSafely();
            mCallStateHandlerThread = null;
        }
    }

    private void initMockVoiceService() {
        clearAllCalls();
        mMuteMode = false;
    }

    private void clearAllCalls() {
        synchronized (mCallList) {
            if (mCallList != null && mCallList.size() > 0) {
                for (int i = 0; i < mCallList.size(); i++) {
                    mCallList.get(i).destroy();
                }
                mCallList.clear();
            }
        }
    }

    private int generateCallId() {
        int callId = INVALID_CALL_ID;
        int idx = 0;

        synchronized (mCallList) {
            for (callId = MIN_CALL_ID; callId <= MAX_CALL_ID; callId++) {
                for (idx = 0; idx < mCallList.size(); idx++) {
                    if (mCallList.get(idx).getCallId() == callId) {
                        break;
                    }
                }
                if (idx == mCallList.size()) {
                    break;
                }
            }
        }

        if (callId > MAX_CALL_ID) {
            callId = INVALID_CALL_ID;
            Log.e(mTag, "Exceed maximum number of call (" + MAX_CALL_ID + ").");
        }

        return callId;
    }

    private MockCallInfo getCallInfo(int callId) {
        MockCallInfo callInfo = null;
        if (callId >= MIN_CALL_ID && callId <= MAX_CALL_ID) {
            if (hasVoiceCalls()) {
                synchronized (mCallList) {
                    for (int idx = 0; idx < mCallList.size(); idx++) {
                        callInfo = mCallList.get(idx);
                        if (callInfo.getCallId() == callId) {
                            break;
                        } else {
                            callInfo = null;
                        }
                    }
                }
            } else {
                Log.w(mTag, "No any call in list.");
            }
        } else {
            Log.e(mTag, "Invalid call id.");
        }

        if (callInfo == null) {
            Log.e(mTag, "Not found any call info with call id " + callId + ".");
        }

        return callInfo;
    }

    private void removeCallInfo(int callId) {
        MockCallInfo callInfo = null;

        if (callId >= MIN_CALL_ID && callId <= MAX_CALL_ID) {
            if (hasVoiceCalls()) {
                synchronized (mCallList) {
                    for (int idx = 0; idx < mCallList.size(); idx++) {
                        callInfo = mCallList.get(idx);
                        if (callInfo.getCallId() == callId) {
                            mCallList.remove(idx);
                            break;
                        } else {
                            callInfo = null;
                        }
                    }
                }
            } else {
                Log.w(mTag, "No any call in list.");
            }
        } else {
            Log.e(mTag, "Invalid call id.");
        }

        if (callInfo == null) {
            Log.e(mTag, "Not found any call info with call id " + callId + ".");
        }

        return;
    }

    private MockCallInfo getIncomingCallInfo() {
        MockCallInfo callInfo = null;

        if (hasVoiceCalls()) {
            synchronized (mCallList) {
                for (int idx = 0; idx < mCallList.size(); idx++) {
                    callInfo = mCallList.get(idx);
                    if (callInfo.isMT()
                            && callInfo.getCallState() == MockCallInfo.CALL_STATE_INCOMING) {
                        break;
                    } else {
                        callInfo = null;
                    }
                }
            }
        } else {
            Log.w(mTag, "No any call in list.");
        }

        if (callInfo == null) {
            Log.e(mTag, "Not found any incoming call info.");
        }

        return callInfo;
    }

    private String getCallStateRequestEventStr(int event) {
        switch (event) {
            case MSG_REQUEST_DIALING_CALL:
                return "MSG_REQUEST_DIALING_CALL";
            case MSG_REQUEST_RINGBACK_TONE:
                return "MSG_REQUEST_RINGBACK_TONE";
            case MSG_REQUEST_ALERTING_CALL:
                return "MSG_REQUEST_ALERTING_CALL";
            case MSG_REQUEST_ACTIVATING_CALL:
                return "MSG_REQUEST_ACTIVATING_CALL";
            case MSG_REQUEST_DISCONNECTING_CALL:
                return "MSG_REQUEST_DISCONNECTING_CALL";
            case MSG_REQUEST_INCOMING_CALL:
                return "MSG_REQUEST_INCOMING_CALL";
            case MSG_REQUEST_CALL_END:
                return "MSG_REQUEST_CALL_END";
        }
        return "Unknown";
    }

    private void scheduleNextEventTimer(MockCallInfo callInfo, int nextEvent, long duration) {
        Log.d(
                mTag,
                "Schedule "
                        + getCallStateRequestEventStr(nextEvent)
                        + " for call id "
                        + callInfo.getCallId()
                        + " in "
                        + duration
                        + " ms.");
        if (nextEvent >= 0) {
            callInfo.addCallStateTimerTask(callInfo.getCallId(), nextEvent, duration);
        }
    }

    private boolean handleDialingCall(int callId) {
        Log.d(mTag, "handleDialingCall for call id: " + callId);
        boolean isCallStateChanged = false;

        synchronized (mCallList) {
            MockCallInfo callInfo = getCallInfo(callId);

            if (callInfo != null) {
                long dialing_duration_in_ms =
                        callInfo.getCallControlInfo().getDialingDurationInMs();
                long alerting_duration_in_ms =
                        callInfo.getCallControlInfo().getAlertingDurationInMs();
                long ringback_tone_in_ms = callInfo.getCallControlInfo().getRingbackToneTimeInMs();
                int call_state_fail_bitmask =
                        callInfo.getCallControlInfo().getCallStateFailBitMask();
                int next_event = -1;

                if (callInfo.getCallState() != MockCallInfo.CALL_STATE_DIALING) {
                    callInfo.setCallState(MockCallInfo.CALL_STATE_DIALING);
                    isCallStateChanged = true;
                    Log.d(mTag, "call id = " + callId + " call state = CALL_STATE_DIALING");
                }

                if (isCallStateChanged) {
                    if ((call_state_fail_bitmask & MockCallControlInfo.CALL_DIALING_FAIL_BITMASK)
                            != 0) {
                        if (dialing_duration_in_ms < 0) {
                            Log.d(mTag, "Dialing duration < 0, using default duration!");
                            dialing_duration_in_ms =
                                    MockCallControlInfo.DEFAULT_DIALING_FAIL_DURATION_IN_MS;
                        }
                        next_event = MSG_REQUEST_DISCONNECTING_CALL;
                        Log.d(
                                mTag,
                                "Start call disconnecting task after "
                                        + dialing_duration_in_ms
                                        + " ms.");
                    } else {
                        // Ringback tone start event
                        callInfo.getCallControlInfo().setRingbackToneState(true);
                        next_event = MSG_REQUEST_RINGBACK_TONE;
                        if (ringback_tone_in_ms < 0
                                || ringback_tone_in_ms
                                        > (dialing_duration_in_ms + alerting_duration_in_ms)) {
                            ringback_tone_in_ms = dialing_duration_in_ms;
                            Log.e(
                                    mTag,
                                    "ringback_tone_in_ms < 0 or > (dialing + alerting) duration ("
                                            + (dialing_duration_in_ms + alerting_duration_in_ms)
                                            + ") ms. Reset to dialing duration ("
                                            + dialing_duration_in_ms
                                            + ") ms");
                        }

                        Log.d(
                                mTag,
                                "Start ringback tone task after " + ringback_tone_in_ms + " ms.");

                        scheduleNextEventTimer(callInfo, next_event, ringback_tone_in_ms);

                        // Next call state change event
                        if (dialing_duration_in_ms >= 0) {
                            next_event = MSG_REQUEST_ALERTING_CALL;
                            Log.d(
                                    mTag,
                                    "Start alerting task after " + dialing_duration_in_ms + " ms.");
                        } else {
                            next_event = -1;
                            Log.d(mTag, "Call dialing forever....");
                        }
                    }

                    scheduleNextEventTimer(callInfo, next_event, dialing_duration_in_ms);
                }
            } else {
                Log.e(mTag, "No found call id = " + callId);
            }
        }

        return isCallStateChanged;
    }

    private boolean handleRingbackTone(int callId) {
        Log.d(mTag, "handleRingbackTone for call id: " + callId);

        synchronized (mCallList) {
            MockCallInfo callInfo = getCallInfo(callId);

            if (callInfo != null) {
                Message ringback_tone_msg =
                        mConfigHandler.obtainMessage(
                                MockModemConfigBase.EVENT_RINGBACK_TONE,
                                callInfo.getCallControlInfo().getRingbackToneState());
                mConfigHandler.sendMessage(ringback_tone_msg);
            } else {
                Log.e(mTag, "No found call id = " + callId);
            }
        }

        return false;
    }

    private boolean handleAlertingCall(int callId) {
        Log.d(mTag, "handleAlertingCall for call id: " + callId);

        boolean isCallStateChanged = false;

        synchronized (mCallList) {
            MockCallInfo callInfo = getCallInfo(callId);

            if (callInfo != null) {
                long alerting_duration_in_ms =
                        callInfo.getCallControlInfo().getAlertingDurationInMs();
                int call_state_fail_bitmask =
                        callInfo.getCallControlInfo().getCallStateFailBitMask();
                int next_event = -1;

                if (callInfo.getCallState() != MockCallInfo.CALL_STATE_ALERTING) {
                    callInfo.setCallState(MockCallInfo.CALL_STATE_ALERTING);
                    isCallStateChanged = true;
                    Log.d(mTag, "call id = " + callId + " call state = CALL_STATE_ALERTING");
                }

                if (isCallStateChanged) {
                    if ((call_state_fail_bitmask & MockCallControlInfo.CALL_ALERTING_FAIL_BITMASK)
                            != 0) {
                        if (alerting_duration_in_ms < 0) {
                            Log.d(mTag, "Alerting duration < 0, using default duration!");
                            alerting_duration_in_ms =
                                    MockCallControlInfo.DEFAULT_ALERTING_FAIL_DURATION_IN_MS;
                        }
                        next_event = MSG_REQUEST_DISCONNECTING_CALL;
                        Log.d(
                                mTag,
                                "Start call disconnecting task after "
                                        + alerting_duration_in_ms
                                        + " ms.");
                    } else {
                        if (alerting_duration_in_ms >= 0) {
                            next_event = MSG_REQUEST_ACTIVATING_CALL;
                            Log.d(
                                    mTag,
                                    "Start activating task after "
                                            + alerting_duration_in_ms
                                            + " ms.");
                        } else {
                            next_event = -1;
                            Log.d(mTag, "Call alerting forever....");
                        }
                    }

                    scheduleNextEventTimer(callInfo, next_event, alerting_duration_in_ms);
                }
            } else {
                Log.e(mTag, "No found call id = " + callId);
            }
        }

        return isCallStateChanged;
    }

    private boolean handleActivatingCall(int callId) {
        Log.d(mTag, "handleActivatingCall for call id: " + callId);

        boolean isCallStateChanged = false;

        synchronized (mCallList) {
            MockCallInfo callInfo = getCallInfo(callId);

            if (callInfo != null) {
                long active_duration_in_ms = callInfo.getCallControlInfo().getActiveDurationInMs();
                int next_event = -1;

                if (callInfo.getCallState() != MockCallInfo.CALL_STATE_ACTIVE) {
                    // Ringback tone stop event
                    callInfo.getCallControlInfo().setRingbackToneState(false);
                    next_event = MSG_REQUEST_RINGBACK_TONE;
                    Log.d(mTag, "Start ringback tone task immediately.");
                    scheduleNextEventTimer(callInfo, next_event, 0);
                    callInfo.setCallState(MockCallInfo.CALL_STATE_ACTIVE);
                    isCallStateChanged = true;
                    Log.d(mTag, "call id = " + callId + " call state = CALL_STATE_ACTIVE");
                }

                if (isCallStateChanged) {
                    // Next call state change event
                    if (active_duration_in_ms >= 0) {
                        next_event = MSG_REQUEST_DISCONNECTING_CALL;
                        Log.d(
                                mTag,
                                "Start call disconnecting task after "
                                        + active_duration_in_ms
                                        + " ms.");
                        scheduleNextEventTimer(callInfo, next_event, active_duration_in_ms);
                    } else {
                        Log.d(mTag, "Call active forever....");
                    }
                }
            } else {
                Log.e(mTag, "No found call id = " + callId);
            }
        }

        return isCallStateChanged;
    }

    private boolean handleDisconnectingCall(int callId) {
        Log.d(mTag, "handleDisconnectingCall for call id: " + callId);

        boolean isCallStateChanged = false;

        synchronized (mCallList) {
            MockCallInfo callInfo = getCallInfo(callId);

            if (callInfo != null) {
                long disconnecting_duration_in_ms =
                        callInfo.getCallControlInfo().getDisconnectingDurationInMs();
                int next_event = -1;

                if (callInfo.getCallState() != MockCallInfo.CALL_STATE_DISCONNECTING) {
                    callInfo.setCallState(MockCallInfo.CALL_STATE_DISCONNECTING);
                    callInfo.clearAllTimers();
                    isCallStateChanged = true;
                    Log.d(mTag, "call id = " + callId + " call state = CALL_STATE_DISCONNECTING");
                }

                if (isCallStateChanged) {
                    if (disconnecting_duration_in_ms >= 0) {
                        next_event = MSG_REQUEST_CALL_END;
                        Log.d(
                                mTag,
                                "Start call end task after "
                                        + disconnecting_duration_in_ms
                                        + " ms.");
                        scheduleNextEventTimer(callInfo, next_event, disconnecting_duration_in_ms);
                    } else {
                        Log.d(mTag, "Call disconnecting forever....");
                    }
                    // No need updating call disconnecting to upper layer
                    isCallStateChanged = false;
                }
            } else {
                Log.e(mTag, "No found call id = " + callId);
            }
        }

        return isCallStateChanged;
    }

    private boolean handleIncomingCall(int callId) {
        Log.d(mTag, "handleIncomingCall for call id: " + callId);

        boolean isCallStateChanged = false;

        synchronized (mCallList) {
            MockCallInfo callInfo = getCallInfo(callId);

            if (callInfo != null) {
                long incoming_duration_in_ms =
                        callInfo.getCallControlInfo().getIncomingDurationInMs();
                int call_state_fail_bitmask =
                        callInfo.getCallControlInfo().getCallStateFailBitMask();
                int next_event = -1;

                if (callInfo.getCallState() != MockCallInfo.CALL_STATE_INCOMING) {
                    callInfo.setCallState(MockCallInfo.CALL_STATE_INCOMING);
                    isCallStateChanged = true;
                    Log.d(mTag, "call id = " + callId + " call state = CALL_STATE_INCOMING");
                }

                if (isCallStateChanged) {
                    if ((call_state_fail_bitmask & MockCallControlInfo.CALL_INCOMING_FAIL_BITMASK)
                            != 0) {
                        if (incoming_duration_in_ms < 0) {
                            Log.d(mTag, "Incoming duration < 0, using default duration!");
                            incoming_duration_in_ms =
                                    MockCallControlInfo.DEFAULT_INCOMING_FAIL_DURATION_IN_MS;
                        }
                        next_event = MSG_REQUEST_DISCONNECTING_CALL;
                        Log.d(
                                mTag,
                                "Start call disconnecting task after "
                                        + incoming_duration_in_ms
                                        + " ms.");
                    } else {
                        if (incoming_duration_in_ms >= 0) {
                            next_event = MSG_REQUEST_ACTIVATING_CALL;
                            Log.d(
                                    mTag,
                                    "Start activating task after "
                                            + incoming_duration_in_ms
                                            + " ms.");
                        } else {
                            next_event = -1;
                            Log.d(mTag, "Call incoming forever....");
                        }
                    }

                    scheduleNextEventTimer(callInfo, next_event, incoming_duration_in_ms);
                }
            } else {
                Log.e(mTag, "No found call id = " + callId);
            }

            if (isCallStateChanged) {
                Message call_incoming_msg =
                        mConfigHandler.obtainMessage(
                                MockModemConfigBase.EVENT_CALL_INCOMING, callInfo);
                mConfigHandler.sendMessage(call_incoming_msg);
            }
        }

        return isCallStateChanged;
    }

    private boolean handleCallEnd(int callId) {
        Log.d(mTag, "handleCallEnd for call id: " + callId);

        boolean isCallStateChanged = false;

        synchronized (mCallList) {
            MockCallInfo callInfo = getCallInfo(callId);

            if (callInfo != null) {
                if (callInfo.getCallState() != MockCallInfo.CALL_STATE_END) {
                    callInfo.setCallState(MockCallInfo.CALL_STATE_END);
                    mLastCallEndInfo.causeCode =
                            callInfo.getCallControlInfo().getCallEndInfo().causeCode;
                    mLastCallEndInfo.vendorCause =
                            callInfo.getCallControlInfo().getCallEndInfo().vendorCause;
                    isCallStateChanged = true;
                    removeCallInfo(callId);
                    Log.d(
                            mTag,
                            "call id = "
                                    + callId
                                    + " call state = CALL_STATE_END with causeCode = "
                                    + mLastCallEndInfo.causeCode
                                    + ", vendorCause = "
                                    + mLastCallEndInfo.vendorCause);
                }
            } else {
                Log.e(mTag, "No found call id = " + callId);
            }
        }

        return isCallStateChanged;
    }

    private final class MockCallStateHandler extends Handler {
        MockCallStateHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Log.d(
                    mTag,
                    "Call state change handling begin for "
                            + getCallStateRequestEventStr(msg.what));
            boolean isCallStateChanged = false;

            try {
                switch (msg.what) {
                    case MSG_REQUEST_DIALING_CALL:
                        isCallStateChanged = handleDialingCall((int) msg.obj);
                        break;
                    case MSG_REQUEST_RINGBACK_TONE:
                        isCallStateChanged = handleRingbackTone((int) msg.obj);
                        break;
                    case MSG_REQUEST_ALERTING_CALL:
                        isCallStateChanged = handleAlertingCall((int) msg.obj);
                        break;
                    case MSG_REQUEST_ACTIVATING_CALL:
                        isCallStateChanged = handleActivatingCall((int) msg.obj);
                        break;
                    case MSG_REQUEST_DISCONNECTING_CALL:
                        isCallStateChanged = handleDisconnectingCall((int) msg.obj);
                        break;
                    case MSG_REQUEST_INCOMING_CALL:
                        isCallStateChanged = handleIncomingCall((int) msg.obj);
                        break;
                    case MSG_REQUEST_CALL_END:
                        isCallStateChanged = handleCallEnd((int) msg.obj);
                        break;
                    default:
                        Log.e(mTag, "Unknown message id.");
                        break;
                }
            } finally {
                Log.d(mTag, "Call state change handling complete");
                if (isCallStateChanged) {
                    synchronized (mCallList) {
                        Message call_state_changed_msg =
                                mConfigHandler.obtainMessage(
                                        MockModemConfigBase.EVENT_CALL_STATE_CHANGE, mCallList);
                        mConfigHandler.sendMessage(call_state_changed_msg);
                    }
                }
            }
        }
    }

    public int getNumberOfCalls() {
        int numOfCalls = 0;
        synchronized (mCallList) {
            numOfCalls = mCallList.size();
        }
        return numOfCalls;
    }

    public boolean hasVoiceCalls() {
        return (getNumberOfCalls() > 0 ? true : false);
    }

    public boolean hasDisconnectingCall() {
        boolean result = false;
        synchronized (mCallList) {
            for (int i = 0; i < mCallList.size(); i++) {
                if (mCallList.get(i).getCallState() == MockCallInfo.CALL_STATE_DISCONNECTING) {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    public boolean getCurrentCalls() {
        if (!hasDisconnectingCall()) {
            synchronized (mCallList) {
                Message current_calls_response_msg =
                        mConfigHandler.obtainMessage(
                                MockModemConfigBase.EVENT_CURRENT_CALLS_RESPONSE, mCallList);
                mConfigHandler.sendMessage(current_calls_response_msg);
            }
        } else {
            Log.d(mTag, "Has disconnecting calls, skip to trigger EVENT_CURRENT_CALLS_RESPONSE.");
        }
        return true;
    }

    public boolean dialVoiceCall(
            String address,
            int clir,
            UusInfo[] uusInfo,
            int callType,
            MockCallControlInfo callControlInfo) {
        boolean result = false;
        MockCallInfo newCall =
                new MockCallInfo(false, address, clir, uusInfo, callType, callControlInfo);

        if (newCall != null) {
            synchronized (mCallList) {
                mCallList.add(newCall);
                newCall.dump();
                newCall.getCallControlInfo().dump();
            }
            mCallStateHandler
                    .obtainMessage(MSG_REQUEST_DIALING_CALL, newCall.getCallId())
                    .sendToTarget();
            result = true;
        } else {
            Log.e(mTag, "Call info creation failed!");
        }
        return result;
    }

    public boolean dialEccVoiceCall(
            String address,
            int categories,
            String[] urns,
            int routing,
            int callType,
            MockCallControlInfo callControlInfo) {
        boolean result = false;
        MockCallInfo newCall =
                new MockCallInfo(false, address,
                        categories, urns, routing, callType, callControlInfo);

        if (newCall != null) {
            synchronized (mCallList) {
                mCallList.add(newCall);
                newCall.dump();
                newCall.getCallControlInfo().dump();
            }
            mCallStateHandler
                    .obtainMessage(MSG_REQUEST_DIALING_CALL, newCall.getCallId())
                    .sendToTarget();
            result = true;
        } else {
            Log.e(mTag, "Call info creation failed!");
        }
        return result;
    }

    public boolean hangupVoiceCall(int index) {
        boolean result = false;
        MockCallInfo callInfo = null;

        synchronized (mCallList) {
            callInfo = getCallInfo(index);

            if (callInfo != null) {
                mCallStateHandler
                        .obtainMessage(MSG_REQUEST_DISCONNECTING_CALL, index)
                        .sendToTarget();
                result = true;
            } else {
                Log.e(mTag, "Cannot find any call with id = " + index);
            }
        }

        return result;
    }

    public boolean rejectVoiceCall() {
        boolean result = false;
        MockCallInfo callInfo = null;

        synchronized (mCallList) {
            callInfo = getIncomingCallInfo();

            if (callInfo != null) {
                mCallStateHandler
                        .obtainMessage(MSG_REQUEST_DISCONNECTING_CALL, callInfo.getCallId())
                        .sendToTarget();
                result = true;
            } else {
                Log.e(mTag, "Cannot find any incoming call.");
            }
        }

        return result;
    }

    public boolean acceptVoiceCall() {
        boolean result = false;
        MockCallInfo callInfo = null;

        synchronized (mCallList) {
            callInfo = getIncomingCallInfo();

            if (callInfo != null) {
                mCallStateHandler
                        .obtainMessage(MSG_REQUEST_ACTIVATING_CALL, callInfo.getCallId())
                        .sendToTarget();
                result = true;
            } else {
                Log.e(mTag, "Cannot find any incoming call.");
            }
        }

        return result;
    }

    public boolean triggerIncomingVoiceCall(
            String address,
            UusInfo[] uusInfo,
            int callType,
            CdmaSignalInfoRecord cdmaSignalInfoRecord,
            MockCallControlInfo callControlInfo) {
        boolean result = false;
        MockCallInfo newCall =
                new MockCallInfo(
                        true,
                        address,
                        MockCallInfo.CLIR_TYPE_DEFAULT,
                        uusInfo,
                        callType,
                        callControlInfo);

        if (newCall != null) {
            if (cdmaSignalInfoRecord != null) {
                newCall.setCdmaSignalInfoRecord(cdmaSignalInfoRecord);
            }

            synchronized (mCallList) {
                mCallList.add(newCall);
                newCall.dump();
            }
            mCallStateHandler
                    .obtainMessage(MSG_REQUEST_INCOMING_CALL, newCall.getCallId())
                    .sendToTarget();
            result = true;
        } else {
            Log.e(mTag, "Call info creation failed!");
        }

        return result;
    }

    public boolean getMuteMode() {
        return mMuteMode;
    }

    public void setMuteMode(boolean isMute) {
        mMuteMode = isMute;
    }

    public LastCallFailCauseInfo getLastCallEndInfo() {
        return mLastCallEndInfo;
    }

    public void setLastCallFailCause(@Annotation.DisconnectCauses int cause) {
        mLastCallEndInfo.causeCode = convertToLastCallFailCause(cause);
    }

    public void clearAllCalls(@Annotation.DisconnectCauses int cause) {
        setLastCallFailCause(cause);
        synchronized (mCallList) {
            if (mCallList != null && mCallList.size() > 0) {
                clearAllCalls();
                Message call_state_changed_msg =
                        mConfigHandler.obtainMessage(
                                MockModemConfigBase.EVENT_CALL_STATE_CHANGE, mCallList);
                mConfigHandler.sendMessage(call_state_changed_msg);
            }
        }
    }

    /**
     * Converts {@link DisconnectCause} to {@link LastCallFailCause}.
     *
     * @param cause The disconnect cause code.
     * @return The converted call fail cause.
     */
    private @LastCallFailCause int convertToLastCallFailCause(
            @Annotation.DisconnectCauses int cause) {
        switch (cause) {
            case DisconnectCause.BUSY:
                return LastCallFailCause.BUSY;
            case DisconnectCause.CONGESTION:
                return LastCallFailCause.TEMPORARY_FAILURE;
            case DisconnectCause.NORMAL:
                return LastCallFailCause.NORMAL;
            case DisconnectCause.POWER_OFF:
                return LastCallFailCause.RADIO_OFF;
            case DisconnectCause.EMERGENCY_TEMP_FAILURE:
                return EMERGENCY_TEMP_FAILURE;
            case DisconnectCause.EMERGENCY_PERM_FAILURE:
                return EMERGENCY_PERM_FAILURE;
            default:
                return LastCallFailCause.ERROR_UNSPECIFIED;
        }
    }
}
