/**
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

package android.telephony.imsmedia;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.net.DatagramSocket;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ImsMediaManagerTest {
    private Executor mExecutor;
    private final Object mLock = new Object();

    @Mock
    Context mMockContext;
    @Mock
    DatagramSocket mMockRtpSocket, mMockRtcpSocket;
    @Mock
    ImsMediaManager.OnConnectedCallback mMockConnectedCallback;
    @Mock
    RtpConfig mMockRtpConfig;
    @Mock
    ImsMediaSession mMockImsMediaSession;
    @Mock
    ImsMediaManager.SessionCallback mMockSessionCallback;
    @Mock
    IMediaStubTest mMockImsMedia;

    @Before
    public void setUp() throws Exception {
        mExecutor = Executors.newSingleThreadExecutor();

        MockitoAnnotations.initMocks(this);
        doReturn(mMockImsMedia).when(mMockImsMediaSession).getBinder();
        doReturn(mMockImsMedia).when(mMockImsMedia).queryLocalInterface(any(String.class));
        doReturn(true).when(mMockImsMedia).transact(anyInt(), any(Parcel.class), any(), anyInt());

        doAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                boolean bServiceConnectionFound = false;
                boolean bIntentFound = false;
                for (Object arg : args) {
                    if (arg instanceof ServiceConnection) {
                        synchronized (arg) {
                            bServiceConnectionFound = true;
                            ((ServiceConnection) arg).onServiceConnected(null,
                                    (IBinder) mMockImsMedia);
                            arg.notify();
                        }
                    } else if (arg instanceof Intent) {
                        bIntentFound = true;
                        Intent intent = (Intent) arg;
                        assertEquals(intent.getAction(), IImsMedia.class.getName());
                        assertEquals(intent.getComponent(), ComponentName.createRelative(
                                ImsMediaManager.MEDIA_SERVICE_PACKAGE,
                                ImsMediaManager.MEDIA_SERVICE_CLASS));
                    }
                }

                assertEquals(bServiceConnectionFound, true);
                assertEquals(bIntentFound, true);
                return true;
            }
        }).when(mMockContext).bindService(any(Intent.class), any(ServiceConnection.class),
                eq(Context.BIND_AUTO_CREATE));

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                for (Object arg : args) {
                    if (arg instanceof ServiceConnection) {
                        synchronized (arg) {
                            ((ServiceConnection) arg).onServiceDisconnected(null);
                            arg.notify();
                        }
                    }
                }
                return null;
            }
        }).when(mMockContext).unbindService(any(ServiceConnection.class));
    }

    @Test
    public void testServiceBindingAndUnbinding() {
        ImsMediaManager imsMediaManager =
                new ImsMediaManager(mMockContext, mExecutor, mMockConnectedCallback);

        synchronized (mLock) {
            try {
                //Wait for connection callback
                Thread.sleep(1000);

                //Unbind from service
                imsMediaManager.release();

                //Wait for disconnect callback
                Thread.sleep(1000);
            } catch (Exception e) {
                fail(e.getMessage());
            }
        }

        verify(mMockConnectedCallback).onConnected();
        verify(mMockConnectedCallback).onDisconnected();
    }

    @Test
    public void testOpenAndCloseSession() {
        ImsMediaManager imsMediaManager =
                new ImsMediaManager(mMockContext, mExecutor, mMockConnectedCallback);

        synchronized (mLock) {
            try {
                //Wait for connection callback
                Thread.sleep(1000);

                imsMediaManager.openSession(mMockRtpSocket, mMockRtcpSocket,
                        ImsMediaSession.SESSION_TYPE_AUDIO, mMockRtpConfig, mExecutor,
                        mMockSessionCallback);

                imsMediaManager.closeSession(mMockImsMediaSession);

                //Unbind from service
                imsMediaManager.release();

                //Wait for disconnect callback
                Thread.sleep(1000);

            } catch (Exception e) {
                fail(e.getMessage());
            }
        }

        try {
            verify(mMockConnectedCallback).onConnected();
            verify(mMockImsMedia, times(2)).transact(anyInt(), any(Parcel.class), any(), anyInt());
            verify(mMockConnectedCallback).onDisconnected();
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    public abstract static class IMediaStubBase implements IBinder {
    }

    public abstract static class IMediaStubTest extends IMediaStubBase implements IInterface {
        public abstract boolean transact(int code, Parcel data, Parcel reply, int flags);
    }
}
