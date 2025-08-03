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

package com.android.server.wifi;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.test.TestLooper;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


/**
 * unit tests for {@link com.android.server.wifi.MboOceController}.
 */
@SmallTest
public class MboOceControllerTest extends WifiBaseTest {
    private static final String TAG = "MboOceControllerTest";
    private static final String INTERFACE_NAME = "wlan0";

    private MboOceController mMboOceController;
    private TestLooper mLooper;
    private WifiThreadRunner mWifiThreadRunner;
    @Mock ConcreteClientModeManager mClientModeManager;
    @Mock TelephonyManager mTelephonyManager;
    @Mock ActiveModeWarden mActiveModeWarden;

    /**
     * Initializes common state (e.g. mocks) needed by test cases.
     */
    @Before
    public void setUp() throws Exception {
        /* Ensure Looper exists */
        MockitoAnnotations.initMocks(this);

        mLooper = new TestLooper();
        mWifiThreadRunner = new WifiThreadRunner(new Handler(mLooper.getLooper()));
        mWifiThreadRunner.setTimeoutsAreErrors(true);
        when(mActiveModeWarden.getPrimaryClientModeManager())
                .thenReturn(mClientModeManager);
        when(mActiveModeWarden.getPrimaryClientModeManagerNullable())
                .thenReturn(mClientModeManager);

        mMboOceController = new MboOceController(mTelephonyManager, mActiveModeWarden,
                mWifiThreadRunner);
        mMboOceController.enableVerboseLogging(true);
    }

    /**
     * Helper function to initialize mboOceController
     */
    private PhoneStateListener enableMboOceController(boolean isMboEnabled, boolean isOceEnabled) {
        long featureSet = 0;
        PhoneStateListener dataConnectionStateListener = null;

        if (isMboEnabled) {
            featureSet |= WifiManager.WIFI_FEATURE_MBO;
        }
        if (isOceEnabled) {
            featureSet |= WifiManager.WIFI_FEATURE_OCE;
        }
        when(mClientModeManager.getSupportedFeatures()).thenReturn(featureSet);

        mMboOceController.enable();

        if (isMboEnabled) {
            /* Capture the PhoneStateListener */
            ArgumentCaptor<PhoneStateListener> phoneStateListenerCaptor =
                    ArgumentCaptor.forClass(PhoneStateListener.class);
            verify(mTelephonyManager).listen(phoneStateListenerCaptor.capture(),
                    eq(PhoneStateListener.LISTEN_DATA_CONNECTION_STATE));
            dataConnectionStateListener = phoneStateListenerCaptor.getValue();
            assertNotNull(dataConnectionStateListener);
        }

        return dataConnectionStateListener;

    }

    /**
     * Test that we do not register for cellular Data state change
     * events when MBO is disabled.
     */
    @Test
    public void testMboDisabledDoNotRegisterCellularDataStateChangeEvents()
            throws Exception {
        enableMboOceController(false, false);
        verify(mTelephonyManager, never()).listen(any(), anyInt());
    }

    /**
     * Test that we register for cellular Data state change
     * events and update cellular data status changes when MBO is enabled.
     */
    @Test
    public void testMboEnabledUpdateCellularDataStateChangeEvents() throws Exception {
        InOrder inOrder = inOrder(mClientModeManager);
        PhoneStateListener dataConnectionStateListener;
        dataConnectionStateListener = enableMboOceController(true, false);
        dataConnectionStateListener.onDataConnectionStateChanged(TelephonyManager.DATA_CONNECTED,
                TelephonyManager.NETWORK_TYPE_LTE);
        mLooper.dispatchAll();
        verify(mClientModeManager).setMboCellularDataStatus(true);

        dataConnectionStateListener.onDataConnectionStateChanged(
                TelephonyManager.DATA_DISCONNECTED, TelephonyManager.NETWORK_TYPE_LTE);
        mLooper.dispatchAll();
        verify(mClientModeManager).setMboCellularDataStatus(false);
    }

    /**
     * Test that we unregister data connection state events
     * when disable mMboOceController is called.
     */
    @Test
    public void testDisableMboOceControllerUnRegisterCellularDataStateChangeEvents()
            throws Exception {
        enableMboOceController(true, false);
        mMboOceController.disable();
        ArgumentCaptor<PhoneStateListener> phoneStateListenerCaptor =
                ArgumentCaptor.forClass(PhoneStateListener.class);
        verify(mTelephonyManager).listen(phoneStateListenerCaptor.capture(),
                eq(PhoneStateListener.LISTEN_NONE));
    }
}
