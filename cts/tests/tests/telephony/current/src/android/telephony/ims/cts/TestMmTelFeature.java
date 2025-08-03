/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.telephony.ims.cts;

import android.os.Bundle;
import android.telephony.AccessNetworkConstants;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsCallProfile;
import android.telephony.ims.ImsCallSessionListener;
import android.telephony.ims.ImsStreamMediaProfile;
import android.telephony.ims.MediaQualityStatus;
import android.telephony.ims.MediaThreshold;
import android.telephony.ims.RtpHeaderExtensionType;
import android.telephony.ims.SrvccCall;
import android.telephony.ims.feature.CapabilityChangeRequest;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsCallSessionImplBase;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.util.Log;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class TestMmTelFeature extends MmTelFeature {

    private final TestImsService.RemovedListener mRemovedListener;
    private final TestImsService.ReadyListener mReadyListener;
    private final TestImsService.CapabilitiesSetListener mCapSetListener;

    private static final String TAG = "CtsTestImsService";
    public static ConferenceHelper sConferenceHelper = new ConferenceHelper();

    private MmTelCapabilities mCapabilities =
            new MmTelCapabilities(MmTelCapabilities.CAPABILITY_TYPE_SMS);
    private TestImsSmsImpl mSmsImpl;
    private Set<RtpHeaderExtensionType> mOfferedRtpHeaderExtensionTypes;
    private CountDownLatch mOfferedRtpHeaderExtensionLatch = new CountDownLatch(1);
    private MediaThreshold mSetMediaThreshold;
    private CountDownLatch mSetMediaThresholdLatch = new CountDownLatch(1);
    private int mTestPacketLossRateValue;
    private int mTestJitterValue;
    private long mTestInactivityTime;
    private TestImsCallSessionImpl mCallSession;
    private CountDownLatch mTerminalBasedCallWaitingLatch = new CountDownLatch(1);
    private boolean mIsTerminalBasedCallWaitingNotified = false;
    private boolean mIsTerminalBasedCallWaitingEnabled = false;
    private CountDownLatch mSrvccStateLatch = new CountDownLatch(1);
    private int mSrvccState = TelephonyManager.SRVCC_STATE_HANDOVER_NONE;
    private Consumer<List<SrvccCall>> mSrvccStartedCallback;
    private ImsStreamMediaProfile mImsStreamMediaProfileVt = null;

    TestMmTelFeature(TestImsService.ReadyListener readyListener,
            TestImsService.RemovedListener removedListener,
            TestImsService.CapabilitiesSetListener setListener) {
        Log.d(TAG, "TestMmTelFeature with default constructor");
        mReadyListener = readyListener;
        mRemovedListener = removedListener;
        mCapSetListener = setListener;
        mSmsImpl = new TestImsSmsImpl();
        // Must set the state to READY in the constructor - onFeatureReady depends on the state
        // being ready.
        setFeatureState(STATE_READY);
    }

    TestMmTelFeature(TestImsService.ReadyListener readyListener,
            TestImsService.RemovedListener removedListener,
            TestImsService.CapabilitiesSetListener setListener, Executor executor) {
        super(executor);
        Log.d(TAG, "TestMmTelFeature with Executor constructor");
        mReadyListener = readyListener;
        mRemovedListener = removedListener;
        mCapSetListener = setListener;
        mSmsImpl = new TestImsSmsImpl();
        // Must set the state to READY in the constructor - onFeatureReady depends on the state
        // being ready.
        setFeatureState(STATE_READY);
    }

    public TestImsSmsImpl getSmsImplementation() {
        return mSmsImpl;
    }

    @Override
    public boolean queryCapabilityConfiguration(int capability, int radioTech) {
        if (ImsUtils.VDBG) {
            Log.d(TAG, "queryCapabilityConfiguration called with capability: " + capability);
        }
        return mCapabilities.isCapable(capability);
    }

    @Override
    public void changeEnabledCapabilities(CapabilityChangeRequest request,
            CapabilityCallbackProxy c) {
        List<CapabilityChangeRequest.CapabilityPair> pairs = request.getCapabilitiesToEnable();
        for (CapabilityChangeRequest.CapabilityPair pair : pairs) {
            if (pair.getRadioTech() == ImsRegistrationImplBase.REGISTRATION_TECH_LTE) {
                mCapabilities.addCapabilities(pair.getCapability());
            }
        }
        pairs = request.getCapabilitiesToDisable();
        for (CapabilityChangeRequest.CapabilityPair pair : pairs) {
            if (pair.getRadioTech() == ImsRegistrationImplBase.REGISTRATION_TECH_LTE) {
                mCapabilities.removeCapabilities(pair.getCapability());
            }
        }
        mCapSetListener.onSet();
    }

    @Override
    public void onFeatureReady() {
        if (ImsUtils.VDBG) {
            Log.d(TAG, "TestMmTelFeature.onFeatureReady called");
        }
        mReadyListener.onReady();
    }

    @Override
    public void onFeatureRemoved() {
        if (ImsUtils.VDBG) {
            Log.d(TAG, "TestMmTelFeature.onFeatureRemoved called");
        }
        mRemovedListener.onRemoved();
    }

    @Override
    public void changeOfferedRtpHeaderExtensionTypes(Set<RtpHeaderExtensionType> extensionTypes) {
        mOfferedRtpHeaderExtensionTypes = extensionTypes;
        mOfferedRtpHeaderExtensionLatch.countDown();
    }

    @Override
    public void setMediaThreshold(int sessionType, MediaThreshold threshold) {
        Log.d(TAG, "setMediaThreshold" + threshold);
        int[] packetLossThreshold = threshold.getThresholdsRtpPacketLossRate();
        int[] jitterThreshold = threshold.getThresholdsRtpJitterMillis();
        long[] inactivityTimeThreshold = threshold.getThresholdsRtpInactivityTimeMillis();
        if (packetLossThreshold != null && packetLossThreshold.length == 1
                && packetLossThreshold[0] == mTestPacketLossRateValue
                && jitterThreshold[0] == mTestJitterValue
                && inactivityTimeThreshold[0] == mTestInactivityTime) {
            mSetMediaThreshold = threshold;
            mSetMediaThresholdLatch.countDown();
        } else {
            Log.d(TAG, "setMediaThreshold, this is not config update for test" + threshold);
        }
    }

    @Override
    public ImsCallProfile createCallProfile(int serviceType, int callType) {
        if (mImsStreamMediaProfileVt != null) {
            ImsCallProfile profile = new ImsCallProfile(serviceType, callType,
                    new Bundle(), mImsStreamMediaProfileVt);
            profile.setCallExtraInt(ImsCallProfile.EXTRA_CALL_NETWORK_TYPE,
                    TelephonyManager.NETWORK_TYPE_IWLAN);
            return profile;
        }

        ImsStreamMediaProfile mediaProfile = new ImsStreamMediaProfile(
                ImsStreamMediaProfile.AUDIO_QUALITY_AMR,
                ImsStreamMediaProfile.DIRECTION_INVALID,
                ImsStreamMediaProfile.VIDEO_QUALITY_NONE,
                ImsStreamMediaProfile.DIRECTION_INVALID,
                ImsStreamMediaProfile.RTT_MODE_DISABLED);
        ImsCallProfile profile = new ImsCallProfile(serviceType, callType,
                    new Bundle(), mediaProfile);
        return profile;
    }

    @Override
    public ImsCallSessionImplBase createCallSession(ImsCallProfile profile) {
        Log.d(TAG, "createCallSession");
        ImsCallSessionImplBase s = new TestImsCallSessionImpl(profile);
        mCallSession = (TestImsCallSessionImpl) s;
        onCallCreate(mCallSession);
        return s != null ? s : null;
    }

    @Override
    public void setTerminalBasedCallWaitingStatus(boolean enabled) {
        mIsTerminalBasedCallWaitingNotified = true;
        mIsTerminalBasedCallWaitingEnabled = enabled;
        mTerminalBasedCallWaitingLatch.countDown();
    }

    @Override
    public void notifySrvccStarted(Consumer<List<SrvccCall>> cb) {
        mSrvccState = TelephonyManager.SRVCC_STATE_HANDOVER_STARTED;
        mSrvccStartedCallback = cb;
    }

    @Override
    public void notifySrvccCompleted() {
        mSrvccState = TelephonyManager.SRVCC_STATE_HANDOVER_COMPLETED;
        mSrvccStartedCallback = null;
    }

    @Override
    public void notifySrvccFailed() {
        mSrvccState = TelephonyManager.SRVCC_STATE_HANDOVER_FAILED;
        mSrvccStartedCallback = null;
    }

    @Override
    public void notifySrvccCanceled() {
        mSrvccState = TelephonyManager.SRVCC_STATE_HANDOVER_CANCELED;
        mSrvccStartedCallback = null;
    }

    @Override
    public MediaQualityStatus queryMediaQualityStatus(int sessionType) {
        if (!mCallSession.isInCall()) {
            Log.d(TAG, "queryMediaQualityStatus: no call.");
            return null;
        }
        MediaQualityStatus status = new MediaQualityStatus(mCallSession.getCallId(),
                    MediaQualityStatus.MEDIA_SESSION_TYPE_AUDIO,
                    AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                    0 /*packetLossRate*/, 0 /*jitter*/, 0 /*inactivityTime*/);

        Log.d(TAG, "queryMediaQualityStatus: current status " + status);
        return status;
    }

    public void setCapabilities(MmTelCapabilities capabilities) {
        mCapabilities = capabilities;
    }

    public MmTelCapabilities getCapabilities() {
        return mCapabilities;
    }

    public Set<RtpHeaderExtensionType> getOfferedRtpHeaderExtensionTypes() {
        return mOfferedRtpHeaderExtensionTypes;
    }

    public CountDownLatch getOfferedRtpHeaderExtensionLatch() {
        return mOfferedRtpHeaderExtensionLatch;
    }

    public MediaThreshold getSetMediaThreshold() {
        Log.d(TAG, "getSetMediaThreshold: " + mSetMediaThreshold);
        return mSetMediaThreshold;
    }

    public CountDownLatch getSetMediaThresholdLatch(int testPacketLossRate, int testJitter,
            long testInactivityMillis) {
        mTestPacketLossRateValue = testPacketLossRate;
        mTestJitterValue = testJitter;
        mTestInactivityTime = testInactivityMillis;
        return mSetMediaThresholdLatch;
    }

    public TestImsCallSessionImpl getImsCallsession() {
        Log.d(TAG, "getImsCallsession");
        return mCallSession;
    }

    public boolean isCallSessionCreated() {
        return (mCallSession != null);
    }

    public void onIncomingCallReceived(Bundle extras) {
        Log.d(TAG, "onIncomingCallReceived");

        ImsStreamMediaProfile mediaProfile = new ImsStreamMediaProfile(
                ImsStreamMediaProfile.AUDIO_QUALITY_AMR,
                ImsStreamMediaProfile.DIRECTION_SEND_RECEIVE,
                ImsStreamMediaProfile.VIDEO_QUALITY_NONE,
                ImsStreamMediaProfile.DIRECTION_INVALID,
                ImsStreamMediaProfile.RTT_MODE_DISABLED);

        ImsCallProfile callProfile = new ImsCallProfile(ImsCallProfile.SERVICE_TYPE_NORMAL,
                ImsCallProfile.CALL_TYPE_VOICE, new Bundle(), mediaProfile);

        TestImsCallSessionImpl incomingSession = new TestImsCallSessionImpl(callProfile);
        mCallSession = incomingSession;

        Executor executor = incomingSession.getExecutor();
        executor.execute(() -> {
            notifyIncomingCall(incomingSession, extras);
        });
    }

    /**
     * Set ImsCallProfile and notify the framework of an incoming VT call.
     *
     * @param extras A bundle containing extra parameters related to the call.
     */
    public void onIncomingVtCallReceived(Bundle extras) {
        Log.d(TAG, "onIncomingVtCallReceived");

        ImsStreamMediaProfile mediaProfile = new ImsStreamMediaProfile(
                ImsStreamMediaProfile.AUDIO_QUALITY_AMR,
                ImsStreamMediaProfile.DIRECTION_SEND_RECEIVE,
                ImsStreamMediaProfile.VIDEO_QUALITY_QVGA_PORTRAIT,
                ImsStreamMediaProfile.DIRECTION_SEND_RECEIVE,
                ImsStreamMediaProfile.RTT_MODE_DISABLED);

        ImsCallProfile callProfile = new ImsCallProfile(ImsCallProfile.SERVICE_TYPE_NORMAL,
                ImsCallProfile.CALL_TYPE_VT, new Bundle(), mediaProfile);

        TestImsCallSessionImpl incomingSession = new TestImsCallSessionImpl(callProfile);
        mCallSession = incomingSession;

        Executor executor = incomingSession.getExecutor();
        executor.execute(() -> {
            notifyIncomingCall(incomingSession, extras);
        });
    }

    public ImsCallSessionListener onIncomingCallReceivedReturnListener(Bundle extras) {
        Log.d(TAG, "onIncomingCallReceivedReturnListener");

        ImsStreamMediaProfile mediaProfile = new ImsStreamMediaProfile(
                ImsStreamMediaProfile.AUDIO_QUALITY_AMR,
                ImsStreamMediaProfile.DIRECTION_SEND_RECEIVE,
                ImsStreamMediaProfile.VIDEO_QUALITY_NONE,
                ImsStreamMediaProfile.DIRECTION_INVALID,
                ImsStreamMediaProfile.RTT_MODE_DISABLED);

        ImsCallProfile callProfile = new ImsCallProfile(ImsCallProfile.SERVICE_TYPE_NORMAL,
                ImsCallProfile.CALL_TYPE_VOICE, new Bundle(), mediaProfile);

        TestImsCallSessionImpl incomingSession = new TestImsCallSessionImpl(callProfile);
        mCallSession = incomingSession;
        String callId = mCallSession.getCallId();

        CompletableFuture<ImsCallSessionListener> future =
                CompletableFuture.supplyAsync(()->
                        notifyIncomingCall(incomingSession, callId, extras),
                        incomingSession.getExecutor());
        try {
            ImsCallSessionListener isl = future.get();
            return future.get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void onCallCreate(TestImsCallSessionImpl session) {
        if (sConferenceHelper != null) {
            sConferenceHelper.addSession(session);
            session.setConferenceHelper(sConferenceHelper);
        }
    }

    public ConferenceHelper getConferenceHelper() {
        return sConferenceHelper;
    }

    public CountDownLatch getTerminalBasedCallWaitingLatch() {
        return mTerminalBasedCallWaitingLatch;
    }

    public CountDownLatch resetTerminalBasedCallWaitingLatch() {
        mIsTerminalBasedCallWaitingNotified = false;
        mTerminalBasedCallWaitingLatch = new CountDownLatch(1);
        return mTerminalBasedCallWaitingLatch;
    }

    public boolean isTerminalBasedCallWaitingNotified() {
        return mIsTerminalBasedCallWaitingNotified;
    }

    public boolean isTerminalBasedCallWaitingEnabled() {
        return mIsTerminalBasedCallWaitingEnabled;
    }

    public CountDownLatch getSrvccStateLatch() {
        return mSrvccStateLatch;
    }

    public int getSrvccState() {
        return mSrvccState;
    }

    public void notifySrvccCall(List<SrvccCall> profiles) {
        if (mSrvccStartedCallback != null) {
            mSrvccStartedCallback.accept(profiles);
            mSrvccStartedCallback = null;
        }
    }

    public void resetSrvccState() {
        mSrvccStateLatch = new CountDownLatch(1);
        mSrvccState = TelephonyManager.SRVCC_STATE_HANDOVER_NONE;
        mSrvccStartedCallback = null;
    }

    public void setImsStreamProfileForVt(ImsStreamMediaProfile imsStreamProfile) {
        mImsStreamMediaProfileVt = imsStreamProfile;
    }
}
