/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.server.wifi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.MacAddress;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.SoftApConfiguration.Builder;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for HostapdHal
 */
@SmallTest
public class HostapdHalTest extends WifiBaseTest {
    private static final String IFACE_NAME = "mock-wlan0";
    private HostapdHalSpy mHostapdHal;
    private IHostapdHal mIHostapd;
    private @Mock HostapdHalAidlImp mIHostapdAidlMock;
    private @Mock HostapdHalHidlImp mIHostapdHidlMock;
    private @Mock Context mContext;
    private @Mock WifiNative.HostapdDeathEventHandler mHostapdHalDeathHandler;
    private @Mock WifiNative.SoftApHalCallback mSoftApHalCallback;
    private TestLooper mLooper = new TestLooper();

    private class HostapdHalSpy extends HostapdHal {
        HostapdHalSpy() {
            super(mContext, new Handler(mLooper.getLooper()));
        }

        @Override
        protected IHostapdHal createIHostapdHalMockable() {
            return mIHostapd;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mHostapdHal = new HostapdHalSpy();
    }

    private void setIHostapdImp(IHostapdHal imp) {
        mIHostapd = imp;
    }

    private void initializeWithAidlImp(boolean shouldSucceed) {
        setIHostapdImp(mIHostapdAidlMock);
        when(mIHostapdAidlMock.initialize()).thenReturn(shouldSucceed);
        assertEquals(mHostapdHal.initialize(), shouldSucceed);
        verify(mIHostapdAidlMock).initialize();
        verify(mIHostapdHidlMock, never()).initialize();
    }

    private void initializeWithHidlImp(boolean shouldSucceed) {
        setIHostapdImp(mIHostapdHidlMock);
        when(mIHostapdHidlMock.initialize()).thenReturn(shouldSucceed);
        assertEquals(mHostapdHal.initialize(), shouldSucceed);
        verify(mIHostapdHidlMock).initialize();
        verify(mIHostapdAidlMock, never()).initialize();
    }

    private void resetHostapdHal() {
        mHostapdHal = new HostapdHalSpy();
        reset(mIHostapdAidlMock, mIHostapdHidlMock);
    }

    /**
     * Tests successful init of HostapdHal with an AIDL implementation
     */
    @Test
    public void testInitSuccessAidl() {
        initializeWithAidlImp(true);
    }

    /**
     * Tests successful init of HostapdHal with a HIDL implementation
     */
    @Test
    public void testInitSuccessHidl() {
        initializeWithHidlImp(true);
    }

    /**
     * Tests failed init of HostapdHal with an AIDL implementation
     */
    @Test
    public void testInitFailureAidl() {
        initializeWithAidlImp(false);
    }

    /**
     * Tests failed init of HostapdHal with a HIDL implementation
     */
    @Test
    public void testInitFailureHidl() {
        initializeWithHidlImp(false);
    }

    /**
     * Check that initialize() returns false if we receive a null hostapd implementation
     */
    @Test
    public void testInitFailure_null() {
        setIHostapdImp(null);
        assertFalse(mHostapdHal.initialize());
        verify(mIHostapdHidlMock, never()).initialize();
        verify(mIHostapdAidlMock, never()).initialize();
    }

    /**
     * Check that other functions cannot be called if initialize() failed
     */
    @Test
    public void testCallAfterInitFailure() {
        initializeWithAidlImp(false);
        when(mIHostapdAidlMock.isApInfoCallbackSupported()).thenReturn(true);
        assertFalse(mHostapdHal.isApInfoCallbackSupported());
        verify(mIHostapdAidlMock, never()).isApInfoCallbackSupported();
    }

    /**
     * Check that HostapdHal.isApInfoCallbackSupported() returns the implementation's result
     */
    @Test
    public void testIsApInfoCallbackSupported() {
        initializeWithAidlImp(true);
        when(mIHostapdAidlMock.isApInfoCallbackSupported()).thenReturn(true);
        assertTrue(mHostapdHal.isApInfoCallbackSupported());
        verify(mIHostapdAidlMock).isApInfoCallbackSupported();
    }

    /**
     * Check that HostapdHal.registerApCallback() returns the implementation's result
     * and that the implementation receives the expected arguments
     */
    @Test
    public void testRegisterApCallback() {
        initializeWithAidlImp(true);
        when(mIHostapdAidlMock.registerApCallback(anyString(),
                any(WifiNative.SoftApHalCallback.class)))
                .thenReturn(true);
        assertTrue(mHostapdHal.registerApCallback(IFACE_NAME, mSoftApHalCallback));
        verify(mIHostapdAidlMock).registerApCallback(eq(IFACE_NAME), eq(mSoftApHalCallback));
    }

    /**
     * Check that HostapdHal.addAccessPoint() returns the implementation's result
     * and that the implementation receives the expected arguments
     */
    @Test
    public void testAddAccessPoint() {
        initializeWithAidlImp(true);
        when(mIHostapdAidlMock.addAccessPoint(anyString(), any(SoftApConfiguration.class),
                anyBoolean(), any(Runnable.class)))
                .thenReturn(true);
        boolean isMetered = true;
        Builder configurationBuilder = new SoftApConfiguration.Builder();
        SoftApConfiguration config = configurationBuilder.build();
        assertTrue(mHostapdHal.addAccessPoint(IFACE_NAME, config,
                isMetered, () -> mSoftApHalCallback.onFailure()));
        verify(mIHostapdAidlMock).addAccessPoint(eq(IFACE_NAME), eq(config),
                eq(isMetered), any(Runnable.class));
    }

    /**
     * Check that HostapdHal.removeAccessPoint() returns the implementation's result
     * and that the implementation receives the expected arguments
     */
    @Test
    public void testRemoveAccessPoint() {
        initializeWithAidlImp(true);
        when(mIHostapdAidlMock.removeAccessPoint(anyString())).thenReturn(true);
        assertTrue(mHostapdHal.removeAccessPoint(IFACE_NAME));
        verify(mIHostapdAidlMock).removeAccessPoint(eq(IFACE_NAME));
    }

    /**
     * Check that HostapdHal.forceClientDisconnect() returns the implementation's result
     * and that the implementation receives the expected arguments
     */
    @Test
    public void testForceClientDisconnect() {
        initializeWithAidlImp(true);
        when(mIHostapdAidlMock.forceClientDisconnect(anyString(), any(MacAddress.class),
                anyInt())).thenReturn(true);
        MacAddress test_client = MacAddress.fromString("da:a1:19:0:0:0");
        assertTrue(mHostapdHal.forceClientDisconnect(IFACE_NAME, test_client,
                WifiManager.SAP_CLIENT_BLOCK_REASON_CODE_BLOCKED_BY_USER));
        verify(mIHostapdAidlMock).forceClientDisconnect(eq(IFACE_NAME), eq(test_client),
                eq(WifiManager.SAP_CLIENT_BLOCK_REASON_CODE_BLOCKED_BY_USER));
    }

    /**
     * Check that HostapdHal.registerDeathHandler() returns the implementation's result
     * and that the implementation receives the expected arguments
     */
    @Test
    public void testRegisterDeathHandler() {
        initializeWithAidlImp(true);
        when(mIHostapdAidlMock.registerDeathHandler(any(WifiNative.HostapdDeathEventHandler.class)))
                .thenReturn(true);
        assertTrue(mHostapdHal.registerDeathHandler(mHostapdHalDeathHandler));
        verify(mIHostapdAidlMock).registerDeathHandler(eq(mHostapdHalDeathHandler));
    }

    /**
     * Check that HostapdHal.deregisterDeathHandler() returns the implementation's result
     */
    @Test
    public void testDeregisterDeathHandler() {
        initializeWithAidlImp(true);
        when(mIHostapdAidlMock.deregisterDeathHandler()).thenReturn(true);
        assertTrue(mHostapdHal.deregisterDeathHandler());
        verify(mIHostapdAidlMock).deregisterDeathHandler();
    }

    /**
     * Check that HostapdHal.isInitializationStarted() returns the implementation's result
     */
    @Test
    public void testIsInitializationStarted() {
        initializeWithAidlImp(true);
        when(mIHostapdAidlMock.isInitializationStarted()).thenReturn(true);
        assertTrue(mHostapdHal.isInitializationStarted());
        verify(mIHostapdAidlMock).isInitializationStarted();
    }

    /**
     * Check that HostapdHal.isInitializationComplete() returns the implementation's result
     */
    @Test
    public void testIsInitializationComplete() {
        initializeWithAidlImp(true);
        when(mIHostapdAidlMock.isInitializationComplete()).thenReturn(true);
        assertTrue(mHostapdHal.isInitializationComplete());
        verify(mIHostapdAidlMock).isInitializationComplete();
    }

    /**
     * Check that HostapdHal.startDaemon() returns the implementation's result
     */
    @Test
    public void testStartDaemon() {
        initializeWithAidlImp(true);
        when(mIHostapdAidlMock.startDaemon()).thenReturn(true);
        assertTrue(mHostapdHal.startDaemon());
        verify(mIHostapdAidlMock).startDaemon();
    }

    /**
     * Check that HostapdHal.terminate() calls the corresponding implementation method
     */
    @Test
    public void testTerminate() {
        initializeWithAidlImp(true);
        mHostapdHal.startDaemon();
        verify(mIHostapdAidlMock).startDaemon();
    }

    /**
     * Check that isSoftApInstanceDiedHandlerSupported() only returns true if the
     * AIDL implementation is being used.
     */
    @Test
    public void testIsSoftApInstanceDiedHandlerSupported() {
        setIHostapdImp(null);
        assertFalse(mHostapdHal.isSoftApInstanceDiedHandlerSupported());

        resetHostapdHal();
        initializeWithHidlImp(true);
        assertFalse(mHostapdHal.isSoftApInstanceDiedHandlerSupported());

        resetHostapdHal();
        initializeWithAidlImp(true);
        assertTrue(mHostapdHal.isSoftApInstanceDiedHandlerSupported());
    }
}
