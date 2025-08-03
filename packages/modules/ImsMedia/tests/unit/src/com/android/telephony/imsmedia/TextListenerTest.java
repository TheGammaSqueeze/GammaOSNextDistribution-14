/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.telephony.imsmedia;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.telephony.imsmedia.IImsTextSessionCallback;
import android.telephony.imsmedia.ImsMediaSession;
import android.telephony.imsmedia.TextConfig;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class TextListenerTest extends ImsMediaTest {
    private static final int SESSION_ID = 1;
    private static final String TEXT_STREAM = "Hello";
    private static final long NATIVE_OBJECT = 1234L;
    private TextListener mTextListener;
    @Mock
    private TextService mTextService;
    @Mock
    private TextLocalSession mMockTextLocalSession;
    @Mock
    private ImsMediaController.OpenSessionCallback mMockCallback;
    @Mock
    private IImsTextSessionCallback mMockIImsTextSessionCallback;
    private TextConfig mTextConfig;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        TextSession textSession = new TextSession(SESSION_ID, mMockIImsTextSessionCallback,
                mTextService, mMockTextLocalSession, Looper.myLooper());
        TextSession.TextSessionHandler handler = textSession.getTextSessionHandler();
        mTextListener = new TextListener(handler);
        mTextListener.setMediaCallback(mMockCallback);
        mTextListener.setNativeObject(NATIVE_OBJECT);
        mTextConfig = TextConfigTest.createTextConfig();
        mTestClass = TextListenerTest.this;
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private Parcel createParcel(int event, int result, TextConfig config) {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(event);
        parcel.writeInt(result);
        if (config != null) {
            config.writeToParcel(parcel, 0);
        }
        parcel.setDataPosition(0);
        return parcel;
    }

    @Test
    public void testOpenSessionSuccess() {
        Parcel parcel = createParcel(TextSession.EVENT_OPEN_SESSION_SUCCESS, SESSION_ID,
                mTextConfig);
        mTextListener.onMessage(parcel);
        doNothing().when(mMockCallback).onOpenSessionSuccess(eq(SESSION_ID),
                eq(mMockTextLocalSession));
        parcel.recycle();
        verify(mMockCallback,
                times(1)).onOpenSessionSuccess(eq(SESSION_ID), any(TextLocalSession.class));
    }

    @Test
    public void testOpenSessionFailure() {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(TextSession.EVENT_OPEN_SESSION_FAILURE);
        parcel.writeInt(SESSION_ID);
        parcel.writeInt(ImsMediaSession.RESULT_INVALID_PARAM);
        mTextConfig.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        mTextListener.onMessage(parcel);
        doNothing().when(mMockCallback).onOpenSessionFailure(eq(SESSION_ID),
                eq(ImsMediaSession.RESULT_INVALID_PARAM));
        parcel.recycle();
        verify(mMockCallback, times(1)).onOpenSessionFailure(eq(SESSION_ID),
                eq(ImsMediaSession.RESULT_INVALID_PARAM));
    }

    @Test
    public void testEventModifySessionResponse() throws RemoteException {
        Parcel parcel = createParcel(TextSession.EVENT_MODIFY_SESSION_RESPONSE,
                ImsMediaSession.RESULT_NO_RESOURCES, mTextConfig);
        mTextListener.onMessage(parcel);
        processAllMessages();
        parcel.recycle();
        verify(mMockIImsTextSessionCallback,
                times(1)).onModifySessionResponse(eq(mTextConfig),
                eq(ImsMediaSession.RESULT_NO_RESOURCES));
    }

    @Test
    public void testEventMediaInactivityInd() throws RemoteException {
        Parcel parcel = createParcel(TextSession.EVENT_MEDIA_INACTIVITY_IND,
                ImsMediaSession.PACKET_TYPE_RTP, mTextConfig);
        mTextListener.onMessage(parcel);
        processAllMessages();
        parcel.recycle();
        verify(mMockIImsTextSessionCallback,
                times(1)).notifyMediaInactivity(eq(ImsMediaSession.PACKET_TYPE_RTP));
    }

    @Test
    public void testEventRttReceived() throws RemoteException {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(TextSession.EVENT_RTT_RECEIVED);
        parcel.writeString(TEXT_STREAM);
        mTextConfig.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        mTextListener.onMessage(parcel);
        processAllMessages();
        parcel.recycle();
        verify(mMockIImsTextSessionCallback,
                times(1)).onRttReceived(eq(TEXT_STREAM));
    }

    @Test
    public void testEventSessionClosed() {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(TextSession.EVENT_SESSION_CLOSED);
        parcel.writeInt(SESSION_ID);
        parcel.setDataPosition(0);
        doNothing().when(mMockCallback).onSessionClosed(eq(SESSION_ID));
        mTextListener.onMessage(parcel);
        parcel.recycle();
        verify(mMockCallback, times(1)).onSessionClosed(eq(SESSION_ID));
    }
}
