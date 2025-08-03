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

import static android.telephony.DomainSelectionService.SELECTOR_TYPE_CALLING;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PersistableBundle;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telephony.AccessNetworkConstants;
import android.telephony.CarrierConfigManager;
import android.telephony.DisconnectCause;
import android.telephony.DomainSelectionService;
import android.telephony.DomainSelector;
import android.telephony.EmergencyRegistrationResult;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TransportSelectorCallback;
import android.telephony.WwanSelectorCallback;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsReasonInfo;
import android.util.Log;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Unit tests for DomainSelectorBase.
 */
@RunWith(AndroidJUnit4.class)
public class NormalCallDomainSelectorTest {
    private static final String TAG = "NormalCallDomainSelectorTest";

    private static final int SELECTOR_TYPE_UT = 3;
    private static final int SLOT_ID = 0;
    private static final int SUB_ID_1 = 1;
    private static final int SUB_ID_2 = 2;
    private static final String TEST_CALLID = "01234";
    private static final Uri TEST_URI = Uri.fromParts(PhoneAccount.SCHEME_TEL, "123456789", null);

    private HandlerThread mHandlerThread;
    private NormalCallDomainSelector mNormalCallDomainSelector;

    @Mock private Context mMockContext;
    @Mock private CarrierConfigManager mMockCarrierConfigMgr;
    @Mock private ImsManager mMockImsManager;
    @Mock private ImsMmTelManager mMockMmTelManager;
    @Mock private ImsStateTracker mMockImsStateTracker;
    @Mock private DomainSelectorBase.DestroyListener mMockDestroyListener;
    @Mock private TelecomManager mMockTelecomManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        doReturn(Context.TELEPHONY_IMS_SERVICE).when(mMockContext)
                .getSystemServiceName(ImsManager.class);
        doReturn(mMockImsManager).when(mMockContext)
                .getSystemService(Context.TELEPHONY_IMS_SERVICE);

        doReturn(Context.CARRIER_CONFIG_SERVICE).when(mMockContext)
                .getSystemServiceName(CarrierConfigManager.class);
        doReturn(mMockCarrierConfigMgr).when(mMockContext)
                .getSystemService(Context.CARRIER_CONFIG_SERVICE);

        doReturn(Context.TELECOM_SERVICE).when(mMockContext)
                .getSystemServiceName(TelecomManager.class);
        doReturn(mMockTelecomManager).when(mMockContext)
                .getSystemService(Context.TELECOM_SERVICE);

        doReturn(mMockMmTelManager).when(mMockImsManager).getImsMmTelManager(SUB_ID_1);
        doReturn(mMockMmTelManager).when(mMockImsManager).getImsMmTelManager(SUB_ID_2);
        doNothing().when(mMockImsStateTracker).removeServiceStateListener(any());
        doNothing().when(mMockImsStateTracker).removeImsStateListener(any());
        doReturn(true).when(mMockImsStateTracker).isMmTelFeatureAvailable();

        // Set up the looper if it does not exist on the test thread.
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        mHandlerThread = new HandlerThread(
                NormalCallDomainSelectorTest.class.getSimpleName());
        mHandlerThread.start();

        mNormalCallDomainSelector = new NormalCallDomainSelector(mMockContext, SLOT_ID, SUB_ID_1,
                mHandlerThread.getLooper(), mMockImsStateTracker, mMockDestroyListener);
    }

    @After
    public void tearDown() throws Exception {
        if (mHandlerThread != null) {
            mHandlerThread.quit();
        }
    }

    private void initialize(ServiceState serviceState, boolean isImsRegistered,
                            boolean isImsRegisteredOverWlan, boolean isImsVoiceCapable,
                            boolean isImsVideoCapable) {
        if (serviceState != null) mNormalCallDomainSelector.onServiceStateUpdated(serviceState);
        doReturn(isImsRegistered).when(mMockImsStateTracker).isImsStateReady();
        doReturn(isImsRegistered).when(mMockImsStateTracker).isImsRegistered();
        doReturn(isImsVoiceCapable).when(mMockImsStateTracker).isImsVoiceCapable();
        doReturn(isImsVideoCapable).when(mMockImsStateTracker).isImsVideoCapable();
        doReturn(isImsRegisteredOverWlan).when(mMockImsStateTracker).isImsRegisteredOverWlan();
        mNormalCallDomainSelector.onImsRegistrationStateChanged();
        mNormalCallDomainSelector.onImsMmTelCapabilitiesChanged();
    }

    @Test
    public void testInit() {
        assertEquals(SLOT_ID, mNormalCallDomainSelector.getSlotId());
        assertEquals(SUB_ID_1, mNormalCallDomainSelector.getSubId());
    }

    @Test
    public void testInitialState() {
        assertEquals(mNormalCallDomainSelector.getSelectorState(),
                NormalCallDomainSelector.SelectorState.INACTIVE);
    }

    @Test
    public void testDestroyedState() {
        mNormalCallDomainSelector.destroy();

        assertEquals(mNormalCallDomainSelector.getSelectorState(),
                NormalCallDomainSelector.SelectorState.DESTROYED);
    }

    @Test
    public void testDestroyedDuringActiveState() {
        MockTransportSelectorCallback transportSelectorCallback =
                new MockTransportSelectorCallback(mNormalCallDomainSelector);

        DomainSelectionService.SelectionAttributes attributes =
                new DomainSelectionService.SelectionAttributes.Builder(
                        SLOT_ID, SUB_ID_1, SELECTOR_TYPE_CALLING)
                        .setAddress(TEST_URI)
                        .setCallId(TEST_CALLID)
                        .setEmergency(false)
                        .setVideoCall(true)
                        .setExitedFromAirplaneMode(false)
                        .build();

        mNormalCallDomainSelector.selectDomain(attributes, transportSelectorCallback);

        assertEquals(mNormalCallDomainSelector.getSelectorState(),
                NormalCallDomainSelector.SelectorState.ACTIVE);

        mNormalCallDomainSelector.destroy();

        assertEquals(mNormalCallDomainSelector.getSelectorState(),
                NormalCallDomainSelector.SelectorState.DESTROYED);
    }

    @Test
    public void testSelectDomainInputParams() {
        MockTransportSelectorCallback transportSelectorCallback =
                new MockTransportSelectorCallback(mNormalCallDomainSelector);

        DomainSelectionService.SelectionAttributes attributes =
                new DomainSelectionService.SelectionAttributes.Builder(
                        SLOT_ID, SUB_ID_1, SELECTOR_TYPE_CALLING)
                        .setAddress(TEST_URI)
                        .setCallId(TEST_CALLID)
                        .setEmergency(false)
                        .setVideoCall(true)
                        .setExitedFromAirplaneMode(false)
                        .build();
        mNormalCallDomainSelector.selectDomain(attributes, transportSelectorCallback);

        assertEquals(mNormalCallDomainSelector.getSelectorState(),
                NormalCallDomainSelector.SelectorState.ACTIVE);

        // Case 1: null inputs
        try {
            mNormalCallDomainSelector.selectDomain(null, null);
        } catch (Exception e) {
            fail("Invalid input params not handled." + e.getMessage());
        }

        assertEquals(mNormalCallDomainSelector.getSelectorState(),
                NormalCallDomainSelector.SelectorState.INACTIVE);

        // Case 2: null TransportSelectorCallback
        try {
            mNormalCallDomainSelector.selectDomain(attributes, null);
        } catch (Exception e) {
            fail("Invalid params (SelectionAttributes) not handled." + e.getMessage());
        }

        assertEquals(mNormalCallDomainSelector.getSelectorState(),
                NormalCallDomainSelector.SelectorState.INACTIVE);

        // Case 3: null SelectionAttributes
        transportSelectorCallback.mSelectionTerminated = false;
        try {
            mNormalCallDomainSelector.selectDomain(null, transportSelectorCallback);
        } catch (Exception e) {
            fail("Invalid params (SelectionAttributes) not handled." + e.getMessage());
        }

        assertTrue(transportSelectorCallback
                .verifyOnSelectionTerminated(DisconnectCause.OUTGOING_FAILURE));

        assertEquals(mNormalCallDomainSelector.getSelectorState(),
                NormalCallDomainSelector.SelectorState.DESTROYED);

        // Case 4: Invalid Subscription-id
        attributes = new DomainSelectionService.SelectionAttributes.Builder(
                SLOT_ID, SubscriptionManager.INVALID_SUBSCRIPTION_ID, SELECTOR_TYPE_CALLING)
                .setAddress(TEST_URI)
                .setCallId(TEST_CALLID)
                .setEmergency(false)
                .setVideoCall(true)
                .setExitedFromAirplaneMode(false)
                .build();
        try {
            mNormalCallDomainSelector.selectDomain(attributes, transportSelectorCallback);
        } catch (Exception e) {
            fail("Invalid params (SelectionAttributes) not handled." + e.getMessage());
        }

        assertTrue(transportSelectorCallback
                .verifyOnSelectionTerminated(DisconnectCause.OUTGOING_FAILURE));

        assertEquals(mNormalCallDomainSelector.getSelectorState(),
                NormalCallDomainSelector.SelectorState.DESTROYED);

        // Case 5: Invalid SELECTOR_TYPE
        attributes =
                new DomainSelectionService.SelectionAttributes.Builder(
                        SLOT_ID, SUB_ID_1, SELECTOR_TYPE_UT)
                        .setAddress(TEST_URI)
                        .setCallId(TEST_CALLID)
                        .setEmergency(false)
                        .setVideoCall(true)
                        .setExitedFromAirplaneMode(false)
                        .build();
        try {
            mNormalCallDomainSelector.selectDomain(attributes, transportSelectorCallback);
        } catch (Exception e) {
            fail("Invalid params (SelectionAttributes) not handled." + e.getMessage());
        }

        assertTrue(transportSelectorCallback
                .verifyOnSelectionTerminated(DisconnectCause.OUTGOING_FAILURE));

        assertEquals(mNormalCallDomainSelector.getSelectorState(),
                NormalCallDomainSelector.SelectorState.DESTROYED);

        // Case 6: Emergency Call
        attributes = new DomainSelectionService.SelectionAttributes.Builder(
                SLOT_ID, SUB_ID_1, SELECTOR_TYPE_CALLING)
                .setAddress(TEST_URI)
                .setCallId(TEST_CALLID)
                .setEmergency(true)
                .setVideoCall(true)
                .setExitedFromAirplaneMode(false)
                .build();
        try {
            mNormalCallDomainSelector.selectDomain(attributes, transportSelectorCallback);
        } catch (Exception e) {
            fail("Invalid params (SelectionAttributes) not handled." + e.getMessage());
        }

        assertEquals(mNormalCallDomainSelector.getSelectorState(),
                NormalCallDomainSelector.SelectorState.DESTROYED);

        assertTrue(transportSelectorCallback
                .verifyOnSelectionTerminated(DisconnectCause.OUTGOING_FAILURE));
    }

    @Test
    public void testOutOfService() {
        MockTransportSelectorCallback transportSelectorCallback =
                new MockTransportSelectorCallback(mNormalCallDomainSelector);
        DomainSelectionService.SelectionAttributes attributes =
                new DomainSelectionService.SelectionAttributes.Builder(
                        SLOT_ID, SUB_ID_1, SELECTOR_TYPE_CALLING)
                        .setAddress(TEST_URI)
                        .setCallId(TEST_CALLID)
                        .setEmergency(false)
                        .setVideoCall(true)
                        .setExitedFromAirplaneMode(false)
                        .build();

        ServiceState serviceState = new ServiceState();
        serviceState.setStateOutOfService();
        initialize(serviceState, false, false, false, false);

        mNormalCallDomainSelector.selectDomain(attributes, transportSelectorCallback);

        assertTrue(transportSelectorCallback
                .verifyOnSelectionTerminated(DisconnectCause.OUT_OF_SERVICE));

        assertEquals(mNormalCallDomainSelector.getSelectorState(),
                NormalCallDomainSelector.SelectorState.DESTROYED);
    }

    @Test
    public void testDomainSelection() {
        MockTransportSelectorCallback transportSelectorCallback =
                new MockTransportSelectorCallback(mNormalCallDomainSelector);
        DomainSelectionService.SelectionAttributes attributes =
                new DomainSelectionService.SelectionAttributes.Builder(
                        SLOT_ID, SUB_ID_1, SELECTOR_TYPE_CALLING)
                        .setAddress(TEST_URI)
                        .setCallId(TEST_CALLID)
                        .setEmergency(false)
                        .setVideoCall(false)
                        .setExitedFromAirplaneMode(false)
                        .build();

        // Case 1: WLAN
        ServiceState serviceState = new ServiceState();
        serviceState.setState(ServiceState.STATE_IN_SERVICE);
        initialize(serviceState, true, true, true, true);

        mNormalCallDomainSelector.selectDomain(attributes, transportSelectorCallback);

        assertTrue(transportSelectorCallback.verifyOnWlanSelected());

        assertEquals(mNormalCallDomainSelector.getSelectorState(),
                NormalCallDomainSelector.SelectorState.INACTIVE);

        // Case 2: 5G
        mNormalCallDomainSelector.selectDomain(attributes, transportSelectorCallback);

        initialize(serviceState, true, false, true, true);
        mNormalCallDomainSelector.selectDomain(attributes, transportSelectorCallback);

        assertTrue(transportSelectorCallback.verifyOnWwanSelected());

        assertTrue(transportSelectorCallback
                .verifyOnDomainSelected(NetworkRegistrationInfo.DOMAIN_PS));

        assertEquals(mNormalCallDomainSelector.getSelectorState(),
                NormalCallDomainSelector.SelectorState.INACTIVE);

        // Case 3: PS -> CS redial
        ImsReasonInfo imsReasonInfo = new ImsReasonInfo();
        imsReasonInfo.mCode = ImsReasonInfo.CODE_LOCAL_CALL_CS_RETRY_REQUIRED;
        attributes = new DomainSelectionService.SelectionAttributes.Builder(
                SLOT_ID, SUB_ID_1, SELECTOR_TYPE_CALLING)
                .setAddress(TEST_URI)
                .setCallId(TEST_CALLID)
                .setEmergency(false)
                .setVideoCall(false)
                .setExitedFromAirplaneMode(false)
                .setPsDisconnectCause(imsReasonInfo)
                .build();

        mNormalCallDomainSelector.reselectDomain(attributes);

        assertTrue(transportSelectorCallback
                .verifyOnDomainSelected(NetworkRegistrationInfo.DOMAIN_CS));

        assertEquals(mNormalCallDomainSelector.getSelectorState(),
                NormalCallDomainSelector.SelectorState.INACTIVE);

        // Case 4: CS call
        NetworkRegistrationInfo nwRegistrationInfo = new NetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_CS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                AccessNetworkConstants.AccessNetworkType.UTRAN, 0, false,
                null, null, null, false, 0, 0, 0);
        serviceState.addNetworkRegistrationInfo(nwRegistrationInfo);

        mNormalCallDomainSelector.selectDomain(attributes, transportSelectorCallback);

        initialize(serviceState, false, false, false, false);
        mNormalCallDomainSelector.selectDomain(attributes, transportSelectorCallback);

        assertTrue(transportSelectorCallback.verifyOnWwanSelected());

        assertTrue(transportSelectorCallback
                .verifyOnDomainSelected(NetworkRegistrationInfo.DOMAIN_CS));

        assertEquals(mNormalCallDomainSelector.getSelectorState(),
                NormalCallDomainSelector.SelectorState.INACTIVE);

        //Case 5: Backup calling
        serviceState.setStateOutOfService();
        initialize(serviceState, true, true, true, true);

        mNormalCallDomainSelector.selectDomain(attributes, transportSelectorCallback);

        assertTrue(transportSelectorCallback.verifyOnWlanSelected());

        assertEquals(mNormalCallDomainSelector.getSelectorState(),
                NormalCallDomainSelector.SelectorState.ACTIVE);
    }

    @Test
    public void testWPSCallDomainSelection() {
        MockTransportSelectorCallback transportSelectorCallback =
                new MockTransportSelectorCallback(mNormalCallDomainSelector);
        DomainSelectionService.SelectionAttributes attributes =
                new DomainSelectionService.SelectionAttributes.Builder(
                        SLOT_ID, SUB_ID_1, SELECTOR_TYPE_CALLING)
                        .setAddress(Uri.fromParts(PhoneAccount.SCHEME_TEL, "*272121", null))
                        .setCallId(TEST_CALLID)
                        .setEmergency(false)
                        .setVideoCall(false)
                        .setExitedFromAirplaneMode(false)
                        .build();

        //Case 1: WPS not supported by IMS
        PersistableBundle config = new PersistableBundle();
        config.putBoolean(CarrierConfigManager.KEY_SUPPORT_WPS_OVER_IMS_BOOL, false);
        doReturn(config).when(mMockCarrierConfigMgr).getConfigForSubId(SUB_ID_1,
                new String[]{CarrierConfigManager.KEY_SUPPORT_WPS_OVER_IMS_BOOL});

        ServiceState serviceState = new ServiceState();
        serviceState.setState(ServiceState.STATE_IN_SERVICE);
        initialize(serviceState, true, true, true, true);

        mNormalCallDomainSelector.selectDomain(attributes, transportSelectorCallback);

        assertTrue(transportSelectorCallback.verifyOnWwanSelected());

        assertTrue(transportSelectorCallback
                .verifyOnDomainSelected(NetworkRegistrationInfo.DOMAIN_CS));

        assertEquals(mNormalCallDomainSelector.getSelectorState(),
                NormalCallDomainSelector.SelectorState.INACTIVE);

        //Case 2: WPS supported by IMS and WLAN registered
        config.putBoolean(CarrierConfigManager.KEY_SUPPORT_WPS_OVER_IMS_BOOL, true);
        serviceState.setState(ServiceState.STATE_IN_SERVICE);
        initialize(serviceState, true, true, true, true);

        mNormalCallDomainSelector.selectDomain(attributes, transportSelectorCallback);

        assertTrue(transportSelectorCallback.verifyOnWlanSelected());

        assertEquals(mNormalCallDomainSelector.getSelectorState(),
                NormalCallDomainSelector.SelectorState.INACTIVE);

        //Case 2: WPS supported by IMS and LTE registered
        config.putBoolean(CarrierConfigManager.KEY_SUPPORT_WPS_OVER_IMS_BOOL, true);
        serviceState.setState(ServiceState.STATE_IN_SERVICE);
        initialize(serviceState, true, false, true, true);

        mNormalCallDomainSelector.selectDomain(attributes, transportSelectorCallback);

        assertTrue(transportSelectorCallback.verifyOnWwanSelected());

        assertTrue(transportSelectorCallback
                .verifyOnDomainSelected(NetworkRegistrationInfo.DOMAIN_PS));

        assertEquals(mNormalCallDomainSelector.getSelectorState(),
                NormalCallDomainSelector.SelectorState.INACTIVE);
    }

    @Test
    public void testTtyCallDomainSelection() {
        MockTransportSelectorCallback transportSelectorCallback =
                new MockTransportSelectorCallback(mNormalCallDomainSelector);
        DomainSelectionService.SelectionAttributes attributes =
                new DomainSelectionService.SelectionAttributes.Builder(
                        SLOT_ID, SUB_ID_1, SELECTOR_TYPE_CALLING)
                        .setAddress(TEST_URI)
                        .setCallId(TEST_CALLID)
                        .setEmergency(false)
                        .setVideoCall(false)
                        .setExitedFromAirplaneMode(false)
                        .build();

        //Case 1: TTY not supported by IMS and TTY enabled
        doReturn(TelecomManager.TTY_MODE_FULL).when(mMockTelecomManager).getCurrentTtyMode();
        PersistableBundle config = new PersistableBundle();
        config.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_TTY_SUPPORTED_BOOL, false);
        doReturn(config).when(mMockCarrierConfigMgr).getConfigForSubId(SUB_ID_1,
                new String[]{CarrierConfigManager.KEY_CARRIER_VOLTE_TTY_SUPPORTED_BOOL});

        ServiceState serviceState = new ServiceState();
        serviceState.setState(ServiceState.STATE_IN_SERVICE);
        initialize(serviceState, true, false, true, true);

        mNormalCallDomainSelector.selectDomain(attributes, transportSelectorCallback);

        assertTrue(transportSelectorCallback.verifyOnWwanSelected());

        assertTrue(transportSelectorCallback
                .verifyOnDomainSelected(NetworkRegistrationInfo.DOMAIN_CS));

        assertEquals(mNormalCallDomainSelector.getSelectorState(),
                NormalCallDomainSelector.SelectorState.INACTIVE);

        //Case 2: TTY supported by IMS and TTY enabled
        config.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_TTY_SUPPORTED_BOOL, true);
        mNormalCallDomainSelector.selectDomain(attributes, transportSelectorCallback);

        assertTrue(transportSelectorCallback.verifyOnWwanSelected());

        assertTrue(transportSelectorCallback
                .verifyOnDomainSelected(NetworkRegistrationInfo.DOMAIN_PS));

        assertEquals(mNormalCallDomainSelector.getSelectorState(),
                NormalCallDomainSelector.SelectorState.INACTIVE);

        //Case 3: TTY supported by IMS and TTY disabled
        doReturn(TelecomManager.TTY_MODE_OFF).when(mMockTelecomManager).getCurrentTtyMode();
        mNormalCallDomainSelector.selectDomain(attributes, transportSelectorCallback);

        assertTrue(transportSelectorCallback.verifyOnWwanSelected());

        assertTrue(transportSelectorCallback
                .verifyOnDomainSelected(NetworkRegistrationInfo.DOMAIN_PS));

        assertEquals(mNormalCallDomainSelector.getSelectorState(),
                NormalCallDomainSelector.SelectorState.INACTIVE);
    }



    static class MockTransportSelectorCallback implements TransportSelectorCallback,
            WwanSelectorCallback {
        public boolean mCreated;
        public boolean mWlanSelected;
        public boolean mWwanSelected;
        public boolean mSelectionTerminated;
        public boolean mDomainSelected;
        int mCauseCode;
        int mSelectedDomain;
        NormalCallDomainSelector mNormalCallDomainSelector;

        MockTransportSelectorCallback(NormalCallDomainSelector normalCallDomainSelector) {
            mNormalCallDomainSelector = normalCallDomainSelector;
        }

        @Override
        public synchronized void onCreated(DomainSelector selector) {
            Log.d(TAG, "onCreated");
            mCreated = true;

            assertEquals(mNormalCallDomainSelector.getSelectorState(),
                    NormalCallDomainSelector.SelectorState.INACTIVE);

            notifyAll();
        }

        public boolean verifyOnCreated() {
            mCreated = false;
            Log.d(TAG, "verifyOnCreated");
            waitForCallback(mCreated);
            return mCreated;
        }

        @Override
        public synchronized void onWlanSelected(boolean useEmergencyPdn) {
            Log.d(TAG, "onWlanSelected");
            mWlanSelected = true;

            assertEquals(mNormalCallDomainSelector.getSelectorState(),
                    NormalCallDomainSelector.SelectorState.INACTIVE);

            notifyAll();
        }

        public boolean verifyOnWlanSelected() {
            Log.d(TAG, "verifyOnWlanSelected");
            waitForCallback(mWlanSelected);
            return mWlanSelected;
        }

        @Override
        public void onWwanSelected(final Consumer<WwanSelectorCallback> consumer) {
            mWwanSelected = true;
            Executors.newSingleThreadExecutor().execute(() -> {
                consumer.accept(this);
            });

            assertEquals(mNormalCallDomainSelector.getSelectorState(),
                    NormalCallDomainSelector.SelectorState.INACTIVE);
        }

        public boolean verifyOnWwanSelected() {
            waitForCallback(mWwanSelected);
            return mWwanSelected;
        }

        @Override
        public synchronized void onSelectionTerminated(int cause) {
            Log.i(TAG, "onSelectionTerminated - called");
            mCauseCode = cause;
            mSelectionTerminated = true;

            assertEquals(mNormalCallDomainSelector.getSelectorState(),
                    NormalCallDomainSelector.SelectorState.INACTIVE);

            notifyAll();
        }

        public boolean verifyOnSelectionTerminated(int cause) {
            Log.i(TAG, "verifyOnSelectionTerminated - called");
            waitForCallback(mSelectionTerminated);
            return (mSelectionTerminated && cause == mCauseCode);
        }

        private synchronized void waitForCallback(boolean condition) {
            long now = System.currentTimeMillis();
            long deadline = now + 1000;
            try {
                while (!condition && now < deadline) {
                    wait(deadline - now);
                    now = System.currentTimeMillis();
                }
            } catch (Exception e) {
                Log.i(TAG, e.getMessage());
            }
        }

        @Override
        public void onRequestEmergencyNetworkScan(@NonNull List<Integer> preferredNetworks,
                int scanType,
                boolean resetScan,
                @NonNull CancellationSignal signal,
                @NonNull Consumer<EmergencyRegistrationResult> consumer) {
            Log.i(TAG, "onRequestEmergencyNetworkScan - called");

        }

        public synchronized void onDomainSelected(@NetworkRegistrationInfo.Domain int domain,
                boolean useEmergencyPdn) {
            Log.i(TAG, "onDomainSelected - called");
            mSelectedDomain = domain;
            mDomainSelected = true;

            assertEquals(mNormalCallDomainSelector.getSelectorState(),
                    NormalCallDomainSelector.SelectorState.INACTIVE);

            notifyAll();
        }

        public boolean verifyOnDomainSelected(int domain) {
            Log.i(TAG, "verifyOnDomainSelected - called");
            mDomainSelected = false;
            waitForCallback(mDomainSelected);
            return (domain == mSelectedDomain);
        }
    }
}
