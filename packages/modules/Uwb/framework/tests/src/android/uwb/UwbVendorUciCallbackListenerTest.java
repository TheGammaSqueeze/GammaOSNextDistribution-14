/*
 * Copyright 2022 The Android Open Source Project
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

package android.uwb;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.os.RemoteException;
import android.uwb.UwbManager.UwbVendorUciCallback;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.concurrent.Executor;

/**
 * Test of {@link UwbVendorUciCallbackListener}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class UwbVendorUciCallbackListenerTest {

    @Mock private IUwbAdapter mIUwbAdapter;
    @Mock private UwbVendorUciCallback mUwbVendorUciCallback;
    @Mock private UwbVendorUciCallback mUwbVendorUciCallback2;

    private static final Executor EXECUTOR = UwbTestUtils.getExecutor();
    private static final byte[] PAYLOAD = new byte[] {0x0, 0x1};
    private static final int GID = 9;
    private static final int OID = 1;

    private UwbVendorUciCallbackListener mUwbVendorUciCallbackListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mUwbVendorUciCallbackListener = new UwbVendorUciCallbackListener(mIUwbAdapter);
    }

    @Test
    public void testRegisterUnregister() throws Exception {
        // Register first callback
        mUwbVendorUciCallbackListener.register(EXECUTOR, mUwbVendorUciCallback);
        verify(mIUwbAdapter, times(1))
                .registerVendorExtensionCallback(mUwbVendorUciCallbackListener);
        // Register first callback again, no call to adapter register
        mUwbVendorUciCallbackListener.register(EXECUTOR, mUwbVendorUciCallback);
        verify(mIUwbAdapter, times(1))
                .registerVendorExtensionCallback(mUwbVendorUciCallbackListener);
        // Register second callback, no call to adapter register
        mUwbVendorUciCallbackListener.register(EXECUTOR, mUwbVendorUciCallback2);
        verify(mIUwbAdapter, times(1))
                .registerVendorExtensionCallback(mUwbVendorUciCallbackListener);
        // Unrgister first callback, no call to adapter unregister
        mUwbVendorUciCallbackListener.unregister(mUwbVendorUciCallback);
        verify(mIUwbAdapter, never())
                .unregisterVendorExtensionCallback(mUwbVendorUciCallbackListener);
        // Unrgister first callback again, no call to adapter unregister
        mUwbVendorUciCallbackListener.unregister(mUwbVendorUciCallback);
        verify(mIUwbAdapter, never())
                .unregisterVendorExtensionCallback(mUwbVendorUciCallbackListener);
        // Unregister second callback
        mUwbVendorUciCallbackListener.unregister(mUwbVendorUciCallback2);
        verify(mIUwbAdapter, times(1))
                .unregisterVendorExtensionCallback(mUwbVendorUciCallbackListener);
    }

    @Test
    public void testRegister_failedThrowsRuntimeException() throws Exception {
        doThrow(new RemoteException())
                .when(mIUwbAdapter)
                .registerVendorExtensionCallback(mUwbVendorUciCallbackListener);
        assertThrows(
                RuntimeException.class,
                () -> mUwbVendorUciCallbackListener.register(EXECUTOR, mUwbVendorUciCallback));
    }

    @Test
    public void testUnregister_failedThrowsRuntimeException() throws Exception {
        mUwbVendorUciCallbackListener.register(EXECUTOR, mUwbVendorUciCallback);
        doThrow(new RemoteException())
                .when(mIUwbAdapter)
                .unregisterVendorExtensionCallback(mUwbVendorUciCallbackListener);

        assertThrows(
                RuntimeException.class,
                () -> mUwbVendorUciCallbackListener.unregister(mUwbVendorUciCallback));
    }

    Answer mRegisterSuccessResponse =
            new Answer() {
                public Object answer(InvocationOnMock invocation) {
                    Object[] args = invocation.getArguments();
                    IUwbVendorUciCallback cb = (IUwbVendorUciCallback) args[0];
                    try {
                        cb.onVendorResponseReceived(GID, OID, PAYLOAD);
                    } catch (RemoteException e) {
                        // Nothing to do
                    }
                    return new Object();
                }
            };

    @Test
    public void testOnVendorResponseReceived_succeed() throws Exception {
        doAnswer(mRegisterSuccessResponse)
                .when(mIUwbAdapter)
                .registerVendorExtensionCallback(any());
        // Register first callback
        mUwbVendorUciCallbackListener.register(EXECUTOR, mUwbVendorUciCallback);
        verify(mUwbVendorUciCallback, times(1)).onVendorUciResponse(GID, OID, PAYLOAD);
        verifyZeroInteractions(mUwbVendorUciCallback2);
        // Register second callback
        mUwbVendorUciCallbackListener.register(EXECUTOR, mUwbVendorUciCallback2);
        verify(mUwbVendorUciCallback, times(1)).onVendorUciResponse(GID, OID, PAYLOAD);
        verify(mUwbVendorUciCallback2, never()).onVendorUciResponse(GID, OID, PAYLOAD);

        mUwbVendorUciCallbackListener.onVendorResponseReceived(GID, OID, PAYLOAD);
        verify(mUwbVendorUciCallback, times(2)).onVendorUciResponse(GID, OID, PAYLOAD);
        verify(mUwbVendorUciCallback2, times(1)).onVendorUciResponse(GID, OID, PAYLOAD);

        mUwbVendorUciCallbackListener.unregister(mUwbVendorUciCallback);
        mUwbVendorUciCallbackListener.onVendorResponseReceived(GID, OID, PAYLOAD);
        verifyNoMoreInteractions(mUwbVendorUciCallback);
        verify(mUwbVendorUciCallback2, times(2)).onVendorUciResponse(GID, OID, PAYLOAD);
    }

    @Test
    public void testOnVendorResponseReceived_failedThrowsRuntimeException() throws Exception {
        doAnswer(mRegisterSuccessResponse)
                .when(mIUwbAdapter)
                .registerVendorExtensionCallback(any());
        doThrow(new RuntimeException())
                .when(mUwbVendorUciCallback)
                .onVendorUciResponse(anyInt(), anyInt(), any());
        assertThrows(
                RuntimeException.class,
                () -> mUwbVendorUciCallbackListener.register(EXECUTOR, mUwbVendorUciCallback));
    }

    Answer mRegisterSuccessNotification =
            new Answer() {
                public Object answer(InvocationOnMock invocation) {
                    Object[] args = invocation.getArguments();
                    IUwbVendorUciCallback cb = (IUwbVendorUciCallback) args[0];
                    try {
                        cb.onVendorNotificationReceived(GID, OID, PAYLOAD);
                    } catch (RemoteException e) {
                        // Nothing to do
                    }
                    return new Object();
                }
            };

    @Test
    public void testOnVendorNotificationReceived_succeed() throws Exception {
        doAnswer(mRegisterSuccessNotification)
                .when(mIUwbAdapter)
                .registerVendorExtensionCallback(any());
        // Register first callback
        mUwbVendorUciCallbackListener.register(EXECUTOR, mUwbVendorUciCallback);
        verify(mUwbVendorUciCallback, times(1)).onVendorUciNotification(GID, OID, PAYLOAD);
        verifyZeroInteractions(mUwbVendorUciCallback2);
        // Register second callback
        mUwbVendorUciCallbackListener.register(EXECUTOR, mUwbVendorUciCallback2);
        verify(mUwbVendorUciCallback, times(1)).onVendorUciNotification(GID, OID, PAYLOAD);
        verify(mUwbVendorUciCallback2, never()).onVendorUciNotification(GID, OID, PAYLOAD);

        mUwbVendorUciCallbackListener.onVendorNotificationReceived(GID, OID, PAYLOAD);
        verify(mUwbVendorUciCallback, times(2)).onVendorUciNotification(GID, OID, PAYLOAD);
        verify(mUwbVendorUciCallback2, times(1)).onVendorUciNotification(GID, OID, PAYLOAD);

        mUwbVendorUciCallbackListener.unregister(mUwbVendorUciCallback);
        mUwbVendorUciCallbackListener.onVendorNotificationReceived(GID, OID, PAYLOAD);
        verifyNoMoreInteractions(mUwbVendorUciCallback);
        verify(mUwbVendorUciCallback2, times(2)).onVendorUciNotification(GID, OID, PAYLOAD);
    }

    @Test
    public void testOnVendorNotificationReceived_failedThrowsRuntimeException() throws Exception {
        doAnswer(mRegisterSuccessNotification)
                .when(mIUwbAdapter)
                .registerVendorExtensionCallback(any());
        doThrow(new RuntimeException())
                .when(mUwbVendorUciCallback)
                .onVendorUciNotification(anyInt(), anyInt(), any());
        assertThrows(
                RuntimeException.class,
                () -> mUwbVendorUciCallbackListener.register(EXECUTOR, mUwbVendorUciCallback));
    }
}
