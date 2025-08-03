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

package com.android.server.wifi.hal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.hardware.wifi.IWifi;
import android.hardware.wifi.WifiStatusCode;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceSpecificException;

import com.android.server.wifi.SsidTranslator;
import com.android.server.wifi.WifiBaseTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class WifiHalAidlImplTest extends WifiBaseTest {
    private WifiHalAidlImpl mDut;

    @Mock private IWifi mIWifiMock;
    @Mock private IBinder mServiceBinderMock;
    @Mock private Context mContextMock;
    @Mock private SsidTranslator mSsidTranslatorMock;
    @Mock private WifiHal.DeathRecipient mFrameworkDeathRecipientMock;

    private ArgumentCaptor<IBinder.DeathRecipient> mDeathRecipientCaptor =
            ArgumentCaptor.forClass(IBinder.DeathRecipient.class);

    private class WifiHalAidlImplSpy extends WifiHalAidlImpl {
        WifiHalAidlImplSpy() {
            super(mContextMock, mSsidTranslatorMock);
        }

        @Override
        protected android.hardware.wifi.IWifi getWifiServiceMockable() {
            return mIWifiMock;
        }

        @Override
        protected IBinder getServiceBinderMockable() {
            return mServiceBinderMock;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDut = new WifiHalAidlImplSpy();
        executeAndValidateInitializationSequence();
    }

    private void executeAndValidateInitializationSequence() throws RemoteException {
        mDut.initialize(mFrameworkDeathRecipientMock);
        assertTrue(mDut.isInitializationComplete());
        verify(mServiceBinderMock).linkToDeath(mDeathRecipientCaptor.capture(), anyInt());
        verify(mIWifiMock).stop();
        verify(mIWifiMock).registerEventCallback(any());
    }

    @Test
    public void testIsStartedSuccess() throws RemoteException {
        when(mIWifiMock.isStarted()).thenReturn(true);
        assertTrue(mDut.isStarted());
        verify(mIWifiMock).isStarted();
    }

    @Test
    public void testIsStartedRemoteException() throws RemoteException {
        doThrow(new RemoteException()).when(mIWifiMock).isStarted();
        assertFalse(mDut.isStarted());
        verify(mIWifiMock).isStarted();
    }

    @Test
    public void testIsStartedServiceSpecificException() throws RemoteException {
        doThrow(new ServiceSpecificException(WifiStatusCode.ERROR_UNKNOWN))
                .when(mIWifiMock).isStarted();
        assertFalse(mDut.isStarted());
        verify(mIWifiMock).isStarted();
    }

    /**
     * Validate IWifi death listener and restart flow.
     */
    @Test
    public void testWifiDeathAndRestart() throws Exception {
        // IWifi service dies.
        mDeathRecipientCaptor.getValue().binderDied();
        assertFalse(mDut.isInitializationComplete());
        verify(mFrameworkDeathRecipientMock).onDeath();

        // Service restarts.
        reset(mIWifiMock, mServiceBinderMock);
        executeAndValidateInitializationSequence();
    }

    @Test
    public void testRegisterEventCallback() throws Exception {
        // Null framework callback
        assertFalse(mDut.registerEventCallback(null));

        // Valid framework callback, error in registering HAL callback
        final WifiHal.Callback frameworkCallback = mock(WifiHal.Callback.class);
        doThrow(new ServiceSpecificException(WifiStatusCode.ERROR_UNKNOWN))
                .when(mIWifiMock).registerEventCallback(any());
        assertFalse(mDut.registerEventCallback(frameworkCallback));

        // Valid framework callback, successful callback registration with the HAL
        doNothing().when(mIWifiMock).registerEventCallback(any());
        assertTrue(mDut.registerEventCallback(frameworkCallback));

        // Attempt to register a second framework callback
        assertFalse(mDut.registerEventCallback(mock(WifiHal.Callback.class)));
    }
}
