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

package com.android.services.telephony.domainselection;

import static android.telephony.DomainSelectionService.SELECTOR_TYPE_SMS;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PersistableBundle;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.AccessNetworkConstants.RadioAccessNetworkType;
import android.telephony.BarringInfo;
import android.telephony.CarrierConfigManager;
import android.telephony.DataSpecificRegistrationInfo;
import android.telephony.DomainSelectionService.SelectionAttributes;
import android.telephony.EmergencyRegistrationResult;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.TransportSelectorCallback;
import android.telephony.VopsSupportInfo;
import android.telephony.WwanSelectorCallback;
import android.testing.TestableLooper;
import android.util.SparseArray;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.TestContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.function.Consumer;

/**
 * Unit tests for EmergencySmsDomainSelector.
 */
@RunWith(AndroidJUnit4.class)
public class EmergencySmsDomainSelectorTest {
    private static final int SLOT_0 = 0;
    private static final int SUB_1 = 1;

    @Mock private ServiceState mServiceState;
    @Mock private TransportSelectorCallback mTransportSelectorCallback;
    @Mock private WwanSelectorCallback mWwanSelectorCallback;
    @Mock private VopsSupportInfo mVopsSupportInfo;
    @Mock private ImsStateTracker mImsStateTracker;
    @Mock private DomainSelectorBase.DestroyListener mDomainSelectorDestroyListener;

    private final SelectionAttributes mSelectionAttributes =
            new SelectionAttributes.Builder(SLOT_0, SUB_1, SELECTOR_TYPE_SMS)
            .setEmergency(true)
            .build();
    private Context mContext;
    private Looper mLooper;
    private TestableLooper mTestableLooper;
    private CarrierConfigManager mCarrierConfigManager;
    private NetworkRegistrationInfo mNetworkRegistrationInfo;
    private boolean mCarrierConfigManagerNullTest = false;
    private BarringInfo mBarringInfo = new BarringInfo();
    private ImsStateTracker.BarringInfoListener mBarringInfoListener;
    private ImsStateTracker.ServiceStateListener mServiceStateListener;
    private EmergencySmsDomainSelector mDomainSelector;
    private EmergencyRegistrationResult mEmergencyRegistrationResult;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = new TestContext() {
            @Override
            public Object getSystemService(String name) {
                if (name.equals(Context.CARRIER_CONFIG_SERVICE)) {
                    if (mCarrierConfigManagerNullTest) {
                        return null;
                    }
                }

                return super.getSystemService(name);
            }
        };

        HandlerThread handlerThread = new HandlerThread(
                EmergencySmsDomainSelectorTest.class.getSimpleName());
        handlerThread.start();
        mLooper = handlerThread.getLooper();
        mTestableLooper = new TestableLooper(mLooper);
        mCarrierConfigManager = mContext.getSystemService(CarrierConfigManager.class);

        mDomainSelector = new EmergencySmsDomainSelector(mContext, SLOT_0, SUB_1,
                mLooper, mImsStateTracker, mDomainSelectorDestroyListener);

        ArgumentCaptor<ImsStateTracker.ServiceStateListener> serviceStateListenerCaptor =
                ArgumentCaptor.forClass(ImsStateTracker.ServiceStateListener.class);
        verify(mImsStateTracker).addServiceStateListener(serviceStateListenerCaptor.capture());
        mServiceStateListener = serviceStateListenerCaptor.getValue();
        assertNotNull(mServiceStateListener);

        ArgumentCaptor<ImsStateTracker.BarringInfoListener> barringInfoListenerCaptor =
                ArgumentCaptor.forClass(ImsStateTracker.BarringInfoListener.class);
        verify(mImsStateTracker).addBarringInfoListener(barringInfoListenerCaptor.capture());
        mBarringInfoListener = barringInfoListenerCaptor.getValue();
        assertNotNull(mBarringInfoListener);
    }

    @After
    public void tearDown() throws Exception {
        if (mTestableLooper != null) {
            mTestableLooper.destroy();
            mTestableLooper = null;
        }

        if (mDomainSelector != null) {
            mDomainSelector.destroy();
            verify(mImsStateTracker).removeImsStateListener(eq(mDomainSelector));
            verify(mImsStateTracker).removeBarringInfoListener(eq(mDomainSelector));
            verify(mImsStateTracker).removeServiceStateListener(eq(mDomainSelector));
        }

        if (mLooper != null) {
            mLooper.quit();
            mLooper = null;
        }

        mEmergencyRegistrationResult = null;
        mDomainSelector = null;
        mNetworkRegistrationInfo = null;
        mVopsSupportInfo = null;
        mDomainSelectorDestroyListener = null;
        mWwanSelectorCallback = null;
        mTransportSelectorCallback = null;
        mServiceState = null;
        mCarrierConfigManager = null;
        mCarrierConfigManagerNullTest = false;
    }

    @Test
    @SmallTest
    public void testFinishSelection() {
        setUpImsStateTracker(AccessNetworkType.EUTRAN);

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);
        mServiceStateListener.onServiceStateUpdated(mServiceState);
        mBarringInfoListener.onBarringInfoUpdated(mBarringInfo);

        assertTrue(mDomainSelector.isDomainSelectionRequested());
        assertTrue(mDomainSelector.isDomainSelectionReady());

        mDomainSelector.finishSelection();

        assertFalse(mDomainSelector.isDomainSelectionReady());
        assertFalse(mDomainSelector.isDomainSelectionRequested());
    }

    @Test
    @SmallTest
    public void testIsDomainSelectionReady() {
        setUpImsStateTracker(AccessNetworkType.EUTRAN);

        assertFalse(mDomainSelector.isDomainSelectionReady());

        mServiceStateListener.onServiceStateUpdated(mServiceState);
        mBarringInfoListener.onBarringInfoUpdated(mBarringInfo);

        assertTrue(mDomainSelector.isDomainSelectionReady());
        assertFalse(mDomainSelector.isDomainSelectionRequested());
    }

    @Test
    @SmallTest
    public void testIsDomainSelectionReadyAndSelectDomain() {
        setUpImsStateTracker(AccessNetworkType.EUTRAN);
        setUpWwanSelectorCallback();

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);
        mServiceStateListener.onServiceStateUpdated(mServiceState);
        mBarringInfoListener.onBarringInfoUpdated(mBarringInfo);

        assertTrue(mDomainSelector.isDomainSelectionRequested());
        assertTrue(mDomainSelector.isDomainSelectionReady());

        processAllMessages();

        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_PS),
                eq(false));
        assertFalse(mDomainSelector.isDomainSelectionRequested());
    }

    @Test
    @SmallTest
    public void testIsSmsOverImsAvailableWhenImsRegistered() {
        assertFalse(mDomainSelector.isSmsOverImsAvailable());

        setUpImsStateTracker(AccessNetworkType.EUTRAN);

        assertTrue(mDomainSelector.isSmsOverImsAvailable());
    }

    @Test
    @SmallTest
    public void testIsSmsOverImsAvailableWhenImsRegisteredAndConfigEnabledAndLteAvailable() {
        setUpImsStateTracker(AccessNetworkType.EUTRAN);
        setUpCarrierConfig(true);
        setUpLteInService(false, false, true, false, false);

        assertTrue(mDomainSelector.isSmsOverImsAvailable());
    }

    @Test
    @SmallTest
    public void testIsSmsOverImsAvailableWhenImsRegisteredAndConfigEnabledAndLteNotAvailable() {
        setUpImsStateTracker(AccessNetworkType.EUTRAN);
        setUpCarrierConfig(true);
        setUpLteInService(false, false, false, false, false);

        assertFalse(mDomainSelector.isSmsOverImsAvailable());
    }

    @Test
    @SmallTest
    public void testIsSmsOverImsAvailableWhenImsRegisteredAndConfigEnabledAndNrAvailable() {
        setUpImsStateTracker(AccessNetworkType.NGRAN);
        setUpCarrierConfig(true);
        setUpNrInService(false, false, true, false);

        assertTrue(mDomainSelector.isSmsOverImsAvailable());
    }

    @Test
    @SmallTest
    public void testIsSmsOverImsAvailableWhenImsRegisteredAndConfigEnabledAndNrNotAvailable() {
        setUpImsStateTracker(AccessNetworkType.NGRAN);
        setUpCarrierConfig(true);
        setUpNrInService(false, false, false, false);

        assertFalse(mDomainSelector.isSmsOverImsAvailable());
    }

    @Test
    @SmallTest
    public void testIsSmsOverImsAvailableWhenCarrierConfigManagerIsNull() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        mCarrierConfigManagerNullTest = true;

        assertFalse(mDomainSelector.isSmsOverImsAvailable());
    }

    @Test
    @SmallTest
    public void testIsSmsOverImsAvailableWhenCarrierConfigIsNull() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        when(mCarrierConfigManager.getConfigForSubId(anyInt())).thenReturn(null);

        assertFalse(mDomainSelector.isSmsOverImsAvailable());
    }

    @Test
    @SmallTest
    public void testIsSmsOverImsAvailableWhenCarrierConfigNotEnabled() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpCarrierConfig(false);

        assertFalse(mDomainSelector.isSmsOverImsAvailable());
    }

    @Test
    @SmallTest
    public void testIsSmsOverImsAvailableWhenMmTelFeatureUnavailable() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN, false, false);
        setUpCarrierConfig(true);

        assertFalse(mDomainSelector.isSmsOverImsAvailable());
    }

    @Test
    @SmallTest
    public void testIsSmsOverImsAvailableWhenServiceStateIsNull() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpCarrierConfig(true);

        assertFalse(mDomainSelector.isSmsOverImsAvailable());
    }

    @Test
    @SmallTest
    public void testIsSmsOverImsAvailableWhenNoLteOrNr() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpCarrierConfig(true);
        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_UMTS)
                .build();
        when(mServiceState.getNetworkRegistrationInfo(
                anyInt(), eq(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)))
                .thenReturn(mNetworkRegistrationInfo);

        mServiceStateListener.onServiceStateUpdated(mServiceState);

        assertFalse(mDomainSelector.isSmsOverImsAvailable());
    }

    @Test
    @SmallTest
    public void testIsSmsOverImsAvailableWhenLteNotRegisteredOrEmergencyNotEnabled() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpCarrierConfig(true);
        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .build();
        when(mServiceState.getNetworkRegistrationInfo(
                anyInt(), eq(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)))
                .thenReturn(mNetworkRegistrationInfo);

        mServiceStateListener.onServiceStateUpdated(mServiceState);

        assertFalse(mDomainSelector.isSmsOverImsAvailable());
    }

    @Test
    @SmallTest
    public void testIsSmsOverImsAvailableWhenLteInServiceAndNoDataSpecificRegistrationInfo() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpCarrierConfig(true);
        setUpLteInService(true, true, true, true, false);

        assertFalse(mDomainSelector.isSmsOverImsAvailable());
    }

    @Test
    @SmallTest
    public void testIsSmsOverImsAvailableWhenLteInServiceAndNoVopsSupportInfo() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpCarrierConfig(true);
        setUpLteInService(false, true, true, true, false);

        assertFalse(mDomainSelector.isSmsOverImsAvailable());
    }

    @Test
    @SmallTest
    public void testIsSmsOverImsAvailableWhenLteInServiceAndEmcBsNotSupported() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpCarrierConfig(true);
        setUpLteInService(false, false, false, true, false);

        assertFalse(mDomainSelector.isSmsOverImsAvailable());
    }

    @Test
    @SmallTest
    public void testIsSmsOverImsAvailableWhenLteInServiceAndEmcBsSupportedAndNoBarringInfo() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpCarrierConfig(true);
        setUpLteInService(false, false, true, true, false);

        assertTrue(mDomainSelector.isSmsOverImsAvailable());
    }

    @Test
    @SmallTest
    public void testIsSmsOverImsAvailableWhenLteInServiceAndEmcBsSupportedAndBarred() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpCarrierConfig(true);
        setUpLteInService(false, false, true, false, true);

        assertFalse(mDomainSelector.isSmsOverImsAvailable());
    }

    @Test
    @SmallTest
    public void testIsSmsOverImsAvailableWhenLteInServiceAndEmcBsSupportedAndNotBarred() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpCarrierConfig(true);
        setUpLteInService(false, false, true, false, false);

        assertTrue(mDomainSelector.isSmsOverImsAvailable());
    }

    @Test
    @SmallTest
    public void testIsSmsOverImsAvailableWhenLteInLimitedService() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpCarrierConfig(true);
        setUpLimitedLteService(false, false, true, false, false);

        assertTrue(mDomainSelector.isSmsOverImsAvailable());
    }

    @Test
    @SmallTest
    public void testIsSmsOverImsAvailableWhenNrNotRegisteredOrEmergencyNotEnabled() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpCarrierConfig(true);
        setUpNrInService(false, false, false, false);

        assertFalse(mDomainSelector.isSmsOverImsAvailable());
    }

    @Test
    @SmallTest
    public void testIsSmsOverImsAvailableWhenNrInServiceAndNoDataSpecificRegistrationInfo() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpCarrierConfig(true);
        setUpNrInService(true, true, false, false);

        assertFalse(mDomainSelector.isSmsOverImsAvailable());
    }

    @Test
    @SmallTest
    public void testIsSmsOverImsAvailableWhenNrInServiceAndNoVopsSupportInfo() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpCarrierConfig(true);
        setUpNrInService(false, true, false, false);

        assertFalse(mDomainSelector.isSmsOverImsAvailable());
    }

    @Test
    @SmallTest
    public void testIsSmsOverImsAvailableWhenNrInServiceAndEmergencyServiceSupported() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpCarrierConfig(true);
        setUpNrInService(false, false, true, false);

        assertTrue(mDomainSelector.isSmsOverImsAvailable());
    }

    @Test
    @SmallTest
    public void testIsSmsOverImsAvailableWhenNrInServiceAndEmergencyServiceFallbackSupported() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpCarrierConfig(true);
        setUpNrInService(false, false, false, true);

        assertTrue(mDomainSelector.isSmsOverImsAvailable());
    }

    @Test
    @SmallTest
    public void testSelectDomainWhilePreviousRequestInProgress() {
        setUpImsStateTracker(AccessNetworkType.EUTRAN);
        setUpWwanSelectorCallback();
        setUpCarrierConfig(true);
        setUpLteInService(false, false, true, false, false);

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);
        mServiceStateListener.onServiceStateUpdated(mServiceState);
        mBarringInfoListener.onBarringInfoUpdated(mBarringInfo);

        assertTrue(mDomainSelector.isDomainSelectionRequested());

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);

        processAllMessages();

        // onDomainSelected will be invoked only once
        // even though the domain selection was requested twice.
        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_PS),
                eq(true));
        assertFalse(mDomainSelector.isDomainSelectionRequested());
    }

    @Test
    @SmallTest
    public void testSelectDomainWhenImsNotRegisteredAndConfigDisabled() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpWwanSelectorCallback();
        setUpCarrierConfig(false);
        setUpLteInService(false, false, true, false, false);
        setUpImsStateListener(true, false, false);

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);
        processAllMessages();

        // Expected: CS network
        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_CS),
                eq(false));
    }

    @Test
    @SmallTest
    public void testSelectDomainWhenImsNotRegisteredAndUmtsNetwork() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpWwanSelectorCallback();
        setUpCarrierConfig(true);
        setUpNonLteService(TelephonyManager.NETWORK_TYPE_UMTS);
        setUpImsStateListener(true, false, false);

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);
        processAllMessages();

        // Expected: CS network
        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_CS),
                eq(false));
    }

    @Test
    @SmallTest
    public void testSelectDomainWhenImsNotRegisteredAndUnknownNetwork() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpWwanSelectorCallback();
        setUpCarrierConfig(true);
        setUpNonLteService(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        setUpImsStateListener(true, false, false);

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);
        processAllMessages();

        // Expected: CS network
        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_CS),
                eq(false));
    }

    @Test
    @SmallTest
    public void testSelectDomainWhenImsNotRegisteredAndLteInService() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpWwanSelectorCallback();
        setUpCarrierConfig(true);
        setUpLteInService(false, false, true, false, false);
        setUpImsStateListener(true, false, false);

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);
        processAllMessages();

        // Expected: PS network
        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_PS),
                eq(true));
    }

    @Test
    @SmallTest
    public void testSelectDomainWhenImsNotRegisteredAndLteEmcBsNotSupported() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpWwanSelectorCallback();
        setUpCarrierConfig(true);
        setUpLteInService(false, false, false, false, false);
        setUpImsStateListener(true, false, false);

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);
        processAllMessages();

        // Expected: CS network
        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_CS),
                eq(false));
    }

    @Test
    @SmallTest
    public void testSelectDomainWhenImsNotRegisteredAndLteEmergencyBarred() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpWwanSelectorCallback();
        setUpCarrierConfig(true);
        setUpLteInService(false, false, true, false, true);
        setUpImsStateListener(true, false, false);

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);
        processAllMessages();

        // Expected: CS network
        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_CS),
                eq(false));
    }

    @Test
    @SmallTest
    public void testSelectDomainWhenImsNotRegisteredAndLimitedLteService() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpWwanSelectorCallback();
        setUpCarrierConfig(true);
        setUpLimitedLteService(false, false, true, false, false);
        setUpImsStateListener(true, false, false);

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);
        processAllMessages();

        // Expected: PS network
        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_PS),
                eq(true));
    }

    @Test
    @SmallTest
    public void testSelectDomainWhenImsNotRegisteredAndLimitedLteEmcBsNotSupported() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpWwanSelectorCallback();
        setUpCarrierConfig(true);
        setUpLimitedLteService(false, false, false, false, false);
        setUpImsStateListener(true, false, false);

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);
        processAllMessages();

        // Expected: CS network
        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_CS),
                eq(false));
    }

    @Test
    @SmallTest
    public void testSelectDomainWhenImsNotRegisteredAndLimitedLteEmergencyBarred() {
        setUpImsStateTracker(AccessNetworkType.UNKNOWN);
        setUpWwanSelectorCallback();
        setUpCarrierConfig(true);
        setUpLimitedLteService(false, false, true, false, true);
        setUpImsStateListener(true, false, false);

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);
        processAllMessages();

        // Expected: CS network
        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_CS),
                eq(false));
    }

    @Test
    @SmallTest
    public void testSelectDomainWhenImsRegisteredOnLte() {
        setUpImsStateTracker(AccessNetworkType.EUTRAN);
        setUpWwanSelectorCallback();
        setUpCarrierConfig(true);
        setUpLteInService(false, false, true, false, false);
        setUpImsStateListener(true, true, true);

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);
        processAllMessages();

        // Expected: PS network
        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_PS),
                eq(true));
    }

    @Test
    @SmallTest
    public void testSelectDomainWhenImsRegisteredOnLteAndEmcBsNotSupported() {
        setUpImsStateTracker(AccessNetworkType.EUTRAN);
        setUpWwanSelectorCallback();
        setUpCarrierConfig(true);
        setUpLteInService(false, false, false, false, false);
        setUpImsStateListener(true, true, true);

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);
        processAllMessages();

        // Expected: CS network
        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_CS),
                eq(false));
    }

    @Test
    @SmallTest
    public void testSelectDomainWhenImsRegisteredOnLteAndEmergencyBarred() {
        setUpImsStateTracker(AccessNetworkType.EUTRAN);
        setUpWwanSelectorCallback();
        setUpCarrierConfig(true);
        setUpLteInService(false, false, true, false, true);
        setUpImsStateListener(true, true, true);

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);
        processAllMessages();

        // Expected: CS network
        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_CS),
                eq(false));
    }

    @Test
    @SmallTest
    public void testSelectDomainWhenImsRegisteredOnIwlanAndConfigDisabled() {
        setUpImsStateTracker(AccessNetworkType.IWLAN);
        setUpWwanSelectorCallback();
        setUpCarrierConfig(false);
        setUpLteInService(false, false, true, false, false);
        setUpImsStateListener(true, true, true);

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);
        processAllMessages();

        // Expected: WLAN
        verify(mTransportSelectorCallback).onWlanSelected(eq(false));
    }

    @Test
    @SmallTest
    public void testSelectDomainWhenImsRegisteredOnIwlanAndLteNotAvailable() {
        setUpImsStateTracker(AccessNetworkType.IWLAN);
        setUpWwanSelectorCallback();
        setUpCarrierConfig(true);
        setUpLteInService(false, false, false, false, false);
        setUpImsStateListener(true, true, true);

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);
        processAllMessages();

        // Expected: CS network - even though IMS is successfully registered over Wi-Fi,
        // if the emergency SMS messages over IMS is enabled in the carrier configuration and
        // the PS network does not allow the emergency service, this MO SMS should be routed to
        // CS domain.
        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_CS),
                eq(false));
    }

    @Test
    @SmallTest
    public void testSelectDomainWhenImsRegisteredOnIwlanAndLteAvailable() {
        setUpImsStateTracker(AccessNetworkType.IWLAN);
        setUpWwanSelectorCallback();
        setUpCarrierConfig(true);
        setUpLteInService(false, false, true, false, false);
        setUpImsStateListener(true, true, true);

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);
        processAllMessages();

        // Expected: PS network
        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_PS),
                eq(true));
    }

    @Test
    @SmallTest
    public void testSelectDomainWhileEmergencyNetworkScanInProgress() {
        setUpImsStateTracker(AccessNetworkType.NGRAN);
        setUpEmergencyRegResult(AccessNetworkType.NGRAN, NetworkRegistrationInfo.DOMAIN_PS, 1, 0);
        setUpWwanSelectorCallback();
        setUpCarrierConfig(true);
        setUpNrInService(false, false, false, true);
        setUpImsStateListener(true, true, true);

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);
        // Call the domain selection before completing the emergency network scan.
        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);
        processAllMessages();

        // onRequestEmergencyNetworkScan is invoked only once.
        verify(mWwanSelectorCallback).onRequestEmergencyNetworkScan(any(), anyInt(),
                anyBoolean(), any(), any());
    }

    @Test
    @SmallTest
    public void testSelectDomainWhenNrEmergencyServiceSupported() {
        setUpImsStateTracker(AccessNetworkType.NGRAN);
        setUpEmergencyRegResult(AccessNetworkType.NGRAN, NetworkRegistrationInfo.DOMAIN_PS, 1, 0);
        setUpWwanSelectorCallback();
        setUpCarrierConfig(true);
        setUpNrInService(false, false, true, false);
        setUpImsStateListener(true, true, true);

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);
        processAllMessages();

        // Expected: PS network
        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_PS),
                eq(true));
    }

    @Test
    @SmallTest
    public void testSelectDomainWhenEmergencyRegistrationResultNgranAndPsDomain() {
        setUpImsStateTracker(AccessNetworkType.NGRAN);
        setUpEmergencyRegResult(AccessNetworkType.NGRAN, NetworkRegistrationInfo.DOMAIN_PS, 1, 0);
        setUpWwanSelectorCallback();
        setUpCarrierConfig(true);
        setUpNrInService(false, false, false, true);
        setUpImsStateListener(true, true, true);

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);
        processAllMessages();

        // Expected: PS network
        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_PS),
                eq(true));
    }

    @Test
    @SmallTest
    public void testSelectDomainWhenEmergencyRegistrationResultEutranAndPsDomain() {
        setUpImsStateTracker(AccessNetworkType.NGRAN);
        setUpEmergencyRegResult(AccessNetworkType.EUTRAN, NetworkRegistrationInfo.DOMAIN_PS, 0, 0);
        setUpWwanSelectorCallback();
        setUpCarrierConfig(true);
        setUpNrInService(false, false, false, true);
        setUpImsStateListener(true, true, true);

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);
        processAllMessages();

        // Expected: PS network
        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_PS),
                eq(true));
    }

    @Test
    @SmallTest
    public void testSelectDomainWhenEmergencyRegistrationResultEutranAndCsDomain() {
        setUpImsStateTracker(AccessNetworkType.NGRAN);
        setUpEmergencyRegResult(AccessNetworkType.EUTRAN, NetworkRegistrationInfo.DOMAIN_CS, 0, 0);
        setUpWwanSelectorCallback();
        setUpCarrierConfig(true);
        setUpNrInService(false, false, false, true);
        setUpImsStateListener(true, true, true);

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);
        processAllMessages();

        // Expected: CS network
        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_CS),
                eq(false));
    }

    @Test
    @SmallTest
    public void testSelectDomainWhenEmergencyRegistrationResultUtranAndCsDomain() {
        setUpImsStateTracker(AccessNetworkType.NGRAN);
        setUpEmergencyRegResult(AccessNetworkType.UTRAN, NetworkRegistrationInfo.DOMAIN_CS, 0, 0);
        setUpWwanSelectorCallback();
        setUpCarrierConfig(true);
        setUpNrInService(false, false, false, true);
        setUpImsStateListener(true, true, true);

        mDomainSelector.selectDomain(mSelectionAttributes, mTransportSelectorCallback);
        processAllMessages();

        // Expected: CS network
        verify(mWwanSelectorCallback).onDomainSelected(eq(NetworkRegistrationInfo.DOMAIN_CS),
                eq(false));
    }

    private void setUpCarrierConfig(boolean supported) {
        PersistableBundle b = new PersistableBundle();
        b.putBoolean(CarrierConfigManager.KEY_SUPPORT_EMERGENCY_SMS_OVER_IMS_BOOL, supported);
        when(mCarrierConfigManager.getConfigForSubId(anyInt())).thenReturn(b);
    }

    private void setUpNonLteService(int networkType) {
        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(networkType)
                .setRegistrationState(networkType == TelephonyManager.NETWORK_TYPE_UNKNOWN
                        ? NetworkRegistrationInfo.REGISTRATION_STATE_UNKNOWN
                        : NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();
        when(mServiceState.getNetworkRegistrationInfo(
                anyInt(), eq(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)))
                .thenReturn(mNetworkRegistrationInfo);

        mServiceStateListener.onServiceStateUpdated(mServiceState);
        mBarringInfoListener.onBarringInfoUpdated(null);
    }

    private void setUpLteInService(boolean noDataSpecificRegistrationInfo,
            boolean noVopsSupportInfo, boolean emcBsSupported,
            boolean noBarringInfo, boolean barred) {
        DataSpecificRegistrationInfo dsri = noDataSpecificRegistrationInfo
                ? null : new DataSpecificRegistrationInfo(
                        8, false, false, false, noVopsSupportInfo ? null : mVopsSupportInfo);

        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .setDataSpecificInfo(dsri)
                .build();
        when(mServiceState.getNetworkRegistrationInfo(
                anyInt(), eq(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)))
                .thenReturn(mNetworkRegistrationInfo);
        when(mVopsSupportInfo.isEmergencyServiceSupported()).thenReturn(emcBsSupported);

        BarringInfo barringInfo = null;

        if (!noBarringInfo) {
            SparseArray<BarringInfo.BarringServiceInfo> barringServiceInfos = new SparseArray<>();
            barringServiceInfos.put(BarringInfo.BARRING_SERVICE_TYPE_EMERGENCY,
                    new BarringInfo.BarringServiceInfo(
                            barred ? BarringInfo.BarringServiceInfo.BARRING_TYPE_UNCONDITIONAL :
                                    BarringInfo.BarringServiceInfo.BARRING_TYPE_NONE, false, 0, 0));
            barringInfo = new BarringInfo(null, barringServiceInfos);
        }

        mServiceStateListener.onServiceStateUpdated(mServiceState);
        mBarringInfoListener.onBarringInfoUpdated(barringInfo);
    }

    private void setUpLimitedLteService(boolean noDataSpecificRegistrationInfo,
            boolean noVopsSupportInfo, boolean emcBsSupported,
            boolean noBarringInfo, boolean barred) {
        DataSpecificRegistrationInfo dsri = noDataSpecificRegistrationInfo
                ? null : new DataSpecificRegistrationInfo(
                        8, false, false, false, noVopsSupportInfo ? null : mVopsSupportInfo);

        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setEmergencyOnly(true)
                .setDataSpecificInfo(dsri)
                .build();
        when(mServiceState.getNetworkRegistrationInfo(
                anyInt(), eq(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)))
                .thenReturn(mNetworkRegistrationInfo);
        when(mVopsSupportInfo.isEmergencyServiceSupported()).thenReturn(emcBsSupported);

        BarringInfo barringInfo = null;

        if (!noBarringInfo) {
            SparseArray<BarringInfo.BarringServiceInfo> barringServiceInfos = new SparseArray<>();
            barringServiceInfos.put(BarringInfo.BARRING_SERVICE_TYPE_EMERGENCY,
                    new BarringInfo.BarringServiceInfo(
                            barred ? BarringInfo.BarringServiceInfo.BARRING_TYPE_UNCONDITIONAL :
                                    BarringInfo.BarringServiceInfo.BARRING_TYPE_NONE, false, 0, 0));
            barringInfo = new BarringInfo(null, barringServiceInfos);
        }

        mServiceStateListener.onServiceStateUpdated(mServiceState);
        mBarringInfoListener.onBarringInfoUpdated(barringInfo);
    }

    private void setUpNrInService(boolean noDataSpecificRegistrationInfo,
            boolean noVopsSupportInfo, boolean emergencyServiceSupported,
            boolean emergencyServiceFallbackSupported) {
        DataSpecificRegistrationInfo dsri = noDataSpecificRegistrationInfo
                ? null : new DataSpecificRegistrationInfo(
                        8, false, false, false, noVopsSupportInfo ? null : mVopsSupportInfo);

        mNetworkRegistrationInfo = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_NR)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .setDataSpecificInfo(dsri)
                .build();
        when(mServiceState.getNetworkRegistrationInfo(
                anyInt(), eq(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)))
                .thenReturn(mNetworkRegistrationInfo);
        when(mVopsSupportInfo.isEmergencyServiceSupported()).thenReturn(emergencyServiceSupported);
        when(mVopsSupportInfo.isEmergencyServiceFallbackSupported())
                .thenReturn(emergencyServiceFallbackSupported);

        mServiceStateListener.onServiceStateUpdated(mServiceState);
        mBarringInfoListener.onBarringInfoUpdated(null);
    }

    private void setUpImsStateTracker(@RadioAccessNetworkType int accessNetworkType) {
        setUpImsStateTracker(accessNetworkType, true, true);
    }

    private void setUpImsStateTracker(@RadioAccessNetworkType int accessNetworkType,
            boolean mmTelFeatureAvailable, boolean smsCapable) {
        when(mImsStateTracker.isMmTelFeatureAvailable()).thenReturn(mmTelFeatureAvailable);
        when(mImsStateTracker.isImsRegistered())
                .thenReturn(accessNetworkType != AccessNetworkType.UNKNOWN);
        when(mImsStateTracker.isImsRegisteredOverWlan())
                .thenReturn(accessNetworkType == AccessNetworkType.IWLAN);
        when(mImsStateTracker.getImsAccessNetworkType()).thenReturn(accessNetworkType);
        when(mImsStateTracker.isImsSmsCapable()).thenReturn(smsCapable);
    }

    private void setUpWwanSelectorCallback() {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            final Consumer<WwanSelectorCallback> callback =
                    (Consumer<WwanSelectorCallback>) args[0];
            callback.accept(mWwanSelectorCallback);
            return null;
        }).when(mTransportSelectorCallback).onWwanSelected(any(Consumer.class));

        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            final Consumer<EmergencyRegistrationResult> result =
                    (Consumer<EmergencyRegistrationResult>) args[4];
            result.accept(mEmergencyRegistrationResult);
            return null;
        }).when(mWwanSelectorCallback).onRequestEmergencyNetworkScan(
                any(), anyInt(), anyBoolean(), any(), any());
    }

    private void setUpEmergencyRegResult(
            @AccessNetworkConstants.RadioAccessNetworkType int accessNetwork,
            @NetworkRegistrationInfo.Domain int domain, int nrEs, int nrEsfb) {
        mEmergencyRegistrationResult = new EmergencyRegistrationResult(accessNetwork,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                domain, true, true, nrEs, nrEsfb, "001", "01", "");
    }

    private void setUpImsStateListener(boolean notifyMmTelFeatureAvailable,
            boolean notifyImsRegState, boolean notifyMmTelCapability) {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            final ImsStateTracker.ImsStateListener listener =
                    (ImsStateTracker.ImsStateListener) args[0];
            mDomainSelector.post(() -> {
                if (notifyMmTelFeatureAvailable) {
                    listener.onImsMmTelFeatureAvailableChanged();
                }
                if (notifyImsRegState) {
                    listener.onImsRegistrationStateChanged();
                }
                if (notifyMmTelCapability) {
                    listener.onImsMmTelCapabilitiesChanged();
                }
            });
            return null;
        }).when(mImsStateTracker).addImsStateListener(any(ImsStateTracker.ImsStateListener.class));
    }

    private void processAllMessages() {
        while (!mTestableLooper.getLooper().getQueue().isIdle()) {
            mTestableLooper.processAllMessages();
        }
    }
}
