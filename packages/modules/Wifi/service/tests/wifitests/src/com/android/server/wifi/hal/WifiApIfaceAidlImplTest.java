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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.hardware.wifi.IWifiApIface;
import android.hardware.wifi.WifiStatusCode;
import android.os.RemoteException;
import android.os.ServiceSpecificException;

import com.android.server.wifi.WifiBaseTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class WifiApIfaceAidlImplTest extends WifiBaseTest {
    private WifiApIfaceAidlImpl mDut;
    @Mock private IWifiApIface mIWifiApIfaceMock;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDut = new WifiApIfaceAidlImpl(mIWifiApIfaceMock);
    }

    @Test
    public void testGetNameSuccess() throws RemoteException {
        final String ifaceName = "wlan0";
        when(mIWifiApIfaceMock.getName()).thenReturn(ifaceName);
        assertEquals(ifaceName, mDut.getName());
        verify(mIWifiApIfaceMock).getName();
    }

    @Test
    public void testGetNameRemoteException() throws RemoteException {
        doThrow(new RemoteException()).when(mIWifiApIfaceMock).getName();
        assertNull(mDut.getName());
        verify(mIWifiApIfaceMock).getName();
    }

    @Test
    public void testGetNameServiceSpecificException() throws RemoteException {
        doThrow(new ServiceSpecificException(WifiStatusCode.ERROR_UNKNOWN))
                .when(mIWifiApIfaceMock).getName();
        assertNull(mDut.getName());
        verify(mIWifiApIfaceMock).getName();
    }
}
