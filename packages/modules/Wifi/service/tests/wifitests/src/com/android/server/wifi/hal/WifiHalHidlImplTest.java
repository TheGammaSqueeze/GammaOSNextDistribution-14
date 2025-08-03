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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.hardware.wifi.V1_0.IWifi;
import android.hardware.wifi.V1_0.IWifiEventCallback;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.WifiStatusCode;
import android.hidl.manager.V1_0.IServiceNotification;
import android.hidl.manager.V1_2.IServiceManager;
import android.os.IHwBinder;
import android.os.RemoteException;

import com.android.server.wifi.SsidTranslator;
import com.android.server.wifi.WifiBaseTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;

public class WifiHalHidlImplTest extends WifiBaseTest {
    private WifiHalSpy mDut;
    private WifiStatus mStatusSuccess;

    @Mock android.hardware.wifi.V1_0.IWifi mIWifiMock;
    @Mock android.hardware.wifi.V1_5.IWifi mIWifiMockV15;
    @Mock IServiceManager mIServiceManagerMock;
    @Mock WifiHal.DeathRecipient mFrameworkDeathRecipientMock;
    @Mock Context mContextMock;
    @Mock SsidTranslator mSsidTranslatorMock;

    private ArgumentCaptor<IServiceNotification.Stub> mServiceNotificationCaptor =
            ArgumentCaptor.forClass(IServiceNotification.Stub.class);
    private ArgumentCaptor<IHwBinder.DeathRecipient> mDeathRecipientCaptor =
            ArgumentCaptor.forClass(IHwBinder.DeathRecipient.class);
    private ArgumentCaptor<IWifiEventCallback> mWifiEventCallbackCaptor =
            ArgumentCaptor.forClass(IWifiEventCallback.class);
    private ArgumentCaptor<android.hardware.wifi.V1_5.IWifiEventCallback>
            mWifiEventCallbackCaptorV15 = ArgumentCaptor.forClass(
            android.hardware.wifi.V1_5.IWifiEventCallback.class);

    private class WifiHalSpy extends WifiHalHidlImpl {
        private int mVersion;

        WifiHalSpy(int hidlVersion) {
            super(mContextMock, mSsidTranslatorMock);
            mVersion = hidlVersion;
        }

        public boolean isV15() {
            return mVersion >= 5;
        }

        @Override
        protected android.hardware.wifi.V1_0.IWifi getWifiServiceMockable() {
            return mIWifiMock;
        }

        @Override
        protected android.hardware.wifi.V1_5.IWifi getWifiV1_5Mockable() {
            return mVersion >= 5 ? mIWifiMockV15 : null;
        }

        @Override
        protected IServiceManager getServiceManagerMockable() {
            return mIServiceManagerMock;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDut = new WifiHalSpy(0);

        mStatusSuccess = new WifiStatus();
        mStatusSuccess.code = WifiStatusCode.SUCCESS;

        when(mIServiceManagerMock.linkToDeath(any(IHwBinder.DeathRecipient.class), anyLong()))
                .thenReturn(true);
        when(mIServiceManagerMock.registerForNotifications(anyString(), anyString(),
                any(IServiceNotification.Stub.class))).thenReturn(true);
        when(mIServiceManagerMock.listManifestByInterface(eq(IWifi.kInterfaceName)))
                .thenReturn(new ArrayList(Arrays.asList("default")));
        when(mIWifiMock.linkToDeath(any(IHwBinder.DeathRecipient.class), anyLong()))
                .thenReturn(true);
        when(mIWifiMock.stop()).thenReturn(mStatusSuccess);
        when(mIWifiMock.registerEventCallback(any())).thenReturn(mStatusSuccess);
        when(mIWifiMockV15.registerEventCallback_1_5(any())).thenReturn(mStatusSuccess);
    }

    /**
     * Test the service manager notification coming in late, after initWifiIfNecessaryLocked
     * has already been invoked as part of initialize.
     */
    @Test
    public void testServiceRegistrationAfterInitialize() throws Exception {
        executeAndValidateInitializationSequence();

        // Service notification should be ignored, since IWifi is already non-null.
        reset(mIWifiMock);
        mServiceNotificationCaptor.getValue().onRegistration(IWifi.kInterfaceName, "", true);
        verify(mIWifiMock, never()).linkToDeath(any(), anyLong());
    }

    /**
     * Validate IWifi death listener and registration flow.
     */
    @Test
    public void testWifiDeathAndRegistration() throws Exception {
        executeAndValidateInitializationSequence();

        // IWifi service dies.
        mDeathRecipientCaptor.getValue().serviceDied(0);
        assertFalse(mDut.isInitializationComplete());
        verify(mFrameworkDeathRecipientMock).onDeath();

        // Service restarts. linkToDeath should be called once during the first initialization,
        // and a second time after this service notification arrives.
        mServiceNotificationCaptor.getValue().onRegistration(IWifi.kInterfaceName, "", false);
        assertTrue(mDut.isInitializationComplete());
        verify(mIWifiMock, times(2)).linkToDeath(any(), anyLong());
    }

    private void executeAndValidateInitializationSequence() throws RemoteException {
        mDut.initialize(mFrameworkDeathRecipientMock);

        // Service manager initialization
        verify(mIServiceManagerMock).linkToDeath(any(), anyLong());
        verify(mIServiceManagerMock).registerForNotifications(eq(IWifi.kInterfaceName),
                eq(""), mServiceNotificationCaptor.capture());
        verify(mIServiceManagerMock).listManifestByInterface(eq(IWifi.kInterfaceName));

        // IWifi initialization
        verify(mIWifiMock).linkToDeath(mDeathRecipientCaptor.capture(), anyLong());
        verify(mIWifiMock).stop();
        if (mDut.isV15()) {
            verify(mIWifiMockV15).registerEventCallback_1_5(
                    mWifiEventCallbackCaptorV15.capture());
        } else {
            verify(mIWifiMock).registerEventCallback(
                    mWifiEventCallbackCaptor.capture());
        }
    }
}
