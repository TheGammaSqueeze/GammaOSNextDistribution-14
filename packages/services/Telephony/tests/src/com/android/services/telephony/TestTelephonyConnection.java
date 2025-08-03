/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.services.telephony;

import android.content.AttributionSource;
import android.content.ContentResolver;
import android.os.Process;
import android.os.UserHandle;
import android.telephony.TelephonyManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.telecom.PhoneAccountHandle;
import android.telecom.VideoProfile;
import android.telephony.CarrierConfigManager;

import com.android.ims.ImsCall;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.emergency.EmergencyNumberTracker;
import com.android.internal.telephony.imsphone.ImsExternalConnection;
import com.android.internal.telephony.imsphone.ImsPhoneConnection;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock Telephony Connection used in TelephonyConferenceController.java for testing purpose
 */

public class TestTelephonyConnection extends TelephonyConnection {

    @Mock
    com.android.internal.telephony.Connection mMockRadioConnection;

    @Mock
    Call mMockCall;

    @Mock
    Context mMockContext;

    @Mock
    ContentResolver mMockContentResolver;

    @Mock
    Resources mMockResources;

    @Mock
    TelephonyManager mMockTelephonyManager;

    @Mock
    EmergencyNumberTracker mEmergencyNumberTracker;

    @Mock
    ImsPhoneConnection mImsPhoneConnection;

    @Mock
    ImsExternalConnection mImsExternalConnection;

    @Mock
    ImsCall mImsCall;

    @Mock
    TelecomAccountRegistry mTelecomAccountRegistry;

    @Mock
    CarrierConfigManager mCarrierConfigManager;

    private boolean mIsImsConnection;
    private boolean mIsImsExternalConnection;
    private boolean mIsConferenceSupported = true;
    private Phone mMockPhone;
    private int mNotifyPhoneAccountChangedCount = 0;
    private List<String> mLastConnectionEvents = new ArrayList<>();
    private List<Bundle> mLastConnectionEventExtras = new ArrayList<>();
    private Object mLock = new Object();
    private PersistableBundle mCarrierConfig = new PersistableBundle();
    private boolean mOriginalConnectionCleared;

    @Override
    public com.android.internal.telephony.Connection getOriginalConnection() {
        if (mIsImsExternalConnection) {
            return mImsExternalConnection;
        } else if (mIsImsConnection) {
            return mImsPhoneConnection;
        } else {
            return mMockRadioConnection;
        }
    }

    @Override
    protected Call getCall() {
        return mMockCall;
    }

    public TestTelephonyConnection() {
        super(null, null, android.telecom.Call.Details.DIRECTION_INCOMING);
        MockitoAnnotations.initMocks(this);

        AttributionSource attributionSource = new AttributionSource.Builder(
                Process.myUid()).build();

        mIsImsConnection = false;
        mIsImsExternalConnection = false;
        mMockPhone = mock(Phone.class);
        mMockContext = mock(Context.class);
        mMockTelephonyManager = mock(TelephonyManager.class);
        mOriginalConnection = mMockRadioConnection;
        // Set up mMockRadioConnection and mMockPhone to contain an active call
        when(mMockRadioConnection.getState()).thenReturn(Call.State.ACTIVE);
        when(mOriginalConnection.getState()).thenReturn(Call.State.ACTIVE);
        when(mMockRadioConnection.getAudioCodec()).thenReturn(
                android.telecom.Connection.AUDIO_CODEC_AMR);
        when(mImsPhoneConnection.getAudioCodec()).thenReturn(
                android.telecom.Connection.AUDIO_CODEC_AMR);
        when(mMockRadioConnection.getCall()).thenReturn(mMockCall);
        when(mMockRadioConnection.getPhoneType()).thenReturn(PhoneConstants.PHONE_TYPE_IMS);
        when(mImsPhoneConnection.getPhoneType()).thenReturn(PhoneConstants.PHONE_TYPE_IMS);
        doNothing().when(mMockRadioConnection).addListener(any(Connection.Listener.class));
        doNothing().when(mMockRadioConnection).addPostDialListener(
                any(Connection.PostDialListener.class));
        when(mEmergencyNumberTracker.getEmergencyNumber(anyString())).thenReturn(null);
        when(mMockPhone.getEmergencyNumberTracker()).thenReturn(mEmergencyNumberTracker);
        when(mMockPhone.getRingingCall()).thenReturn(mMockCall);
        when(mMockPhone.getContext()).thenReturn(mMockContext);
        when(mMockPhone.getCurrentSubscriberUris()).thenReturn(null);
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockContext.getContentResolver()).thenReturn(mMockContentResolver);
        when(mMockContext.getSystemService(Context.TELEPHONY_SERVICE))
                .thenReturn(mMockTelephonyManager);
        when(mMockContext.getAttributionSource()).thenReturn(attributionSource);
        when(mMockContentResolver.getUserId()).thenReturn(UserHandle.USER_CURRENT);
        when(mMockContentResolver.getAttributionSource()).thenReturn(attributionSource);
        when(mMockResources.getBoolean(anyInt())).thenReturn(false);
        when(mMockPhone.getDefaultPhone()).thenReturn(mMockPhone);
        when(mMockPhone.getPhoneType()).thenReturn(PhoneConstants.PHONE_TYPE_IMS);
        when(mMockCall.getState()).thenReturn(Call.State.ACTIVE);
        when(mMockCall.getPhone()).thenReturn(mMockPhone);
        when(mMockPhone.getDefaultPhone()).thenReturn(mMockPhone);
        when(mImsPhoneConnection.getImsCall()).thenReturn(mImsCall);
        when(mTelecomAccountRegistry.isMergeCallSupported(notNull(PhoneAccountHandle.class)))
                .thenReturn(mIsConferenceSupported);
        when(mTelecomAccountRegistry.isMergeImsCallSupported(notNull(PhoneAccountHandle.class)))
                .thenReturn(mIsImsConnection);
        when(mTelecomAccountRegistry
                .isVideoConferencingSupported(notNull(PhoneAccountHandle.class))).thenReturn(false);
        when(mTelecomAccountRegistry
                .isMergeOfWifiCallsAllowedWhenVoWifiOff(notNull(PhoneAccountHandle.class)))
                .thenReturn(false);
        try {
            doNothing().when(mMockCall).hangup();
        } catch (CallStateException e) {
            e.printStackTrace();
        }
    }

    public void setMockPhone(Phone newPhone) {
        mMockPhone = newPhone;
    }

    @Override
    public Phone getPhone() {
        return mMockPhone;
    }

    public TelephonyConnection cloneConnection() {
        return this;
    }

    @Override
    public void notifyPhoneAccountChanged(PhoneAccountHandle pHandle) {
        mNotifyPhoneAccountChangedCount++;
    }

    @Override
    public void sendConnectionEvent(String event, Bundle extras) {
        mLastConnectionEvents.add(event);
        mLastConnectionEventExtras.add(extras);
    }

    @Override
    void clearOriginalConnection() {
        mOriginalConnectionCleared = true;
    }

    boolean isOriginalConnectionCleared() {
        return mOriginalConnectionCleared;
    }

    void resetOriginalConnectionCleared() {
        mOriginalConnectionCleared = false;
    }

    @Override
    public PersistableBundle getCarrierConfig() {
        // Depends on PhoneGlobals for context in TelephonyConnection, do not implement during
        // testing.
        return mCarrierConfig;
    }

    @Override
    public void refreshConferenceSupported() {
        if (mIsImsConnection) {
            super.refreshConferenceSupported();
        }
    }

    @Override
    public CharSequence getResourceText(int messageId) {
        return "TEST";
    }

    @Override
    public String getResourceString(int id) {
        return "TEST";
    }

    @Override
    public void setConferenceSupported(boolean conferenceSupported) {
        mIsConferenceSupported = conferenceSupported;
    }

    @Override
    public boolean isConferenceSupported() {
        return mIsConferenceSupported;
    }

    @Override
    public TelecomAccountRegistry getTelecomAccountRegistry(Context context) {
        return mTelecomAccountRegistry;
    }

    public void setIsVideoCall(boolean isVideoCall) {
        if (isVideoCall) {
            setVideoState(VideoProfile.STATE_TX_ENABLED);
        } else {
            setVideoState(VideoProfile.STATE_AUDIO_ONLY);
        }
    }

    public void setWasVideoCall(boolean wasVideoCall) {
        when(mImsCall.wasVideoCall()).thenReturn(wasVideoCall);
    }

    @Override
    boolean isWfcEnabled(Phone phone) {
        // Requires ImsManager dependencies, mock for test.
        return true;
    }

    public int getNotifyPhoneAccountChangedCount() {
        return mNotifyPhoneAccountChangedCount;
    }

    public List<String> getLastConnectionEvents() {
        return mLastConnectionEvents;
    }

    public List<Bundle> getLastConnectionEventExtras() {
        return mLastConnectionEventExtras;
    }

    public void setIsImsConnection(boolean isImsConnection) {
        mIsImsConnection = isImsConnection;
        when(mTelecomAccountRegistry.isMergeImsCallSupported(notNull(PhoneAccountHandle.class)))
                .thenReturn(isImsConnection && mIsConferenceSupported);
    }

    public void setIsImsExternalConnection(boolean isExternalConnection) {
        mIsImsExternalConnection = isExternalConnection;
    }

    public void setDownGradeVideoCall(boolean downgrade) {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_TREAT_DOWNGRADED_VIDEO_CALLS_AS_VIDEO_CALLS_BOOL,
                downgrade);
        when(mMockContext.getSystemService(Context.CARRIER_CONFIG_SERVICE))
                .thenReturn(mCarrierConfigManager);
        when(mCarrierConfigManager.getConfigForSubId(anyInt())).thenReturn(bundle);
    }

    public PersistableBundle getCarrierConfigBundle() {
        return mCarrierConfig;
    }

    public ImsPhoneConnection getMockImsPhoneConnection() {
        return mImsPhoneConnection;
    }

    public void setMockImsPhoneConnection(ImsPhoneConnection connection) {
        mImsPhoneConnection = connection;
    }
}
