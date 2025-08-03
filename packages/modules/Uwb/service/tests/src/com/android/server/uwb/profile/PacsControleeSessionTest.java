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

package com.android.server.uwb.profile;

import static com.android.server.uwb.data.UwbConfig.CONTROLEE_AND_RESPONDER;
import static com.android.server.uwb.data.UwbConfig.OOB_TYPE_BLE;
import static com.android.server.uwb.data.UwbConfig.PERIPHERAL;
import static com.android.server.uwb.pm.RangingSessionController.RANGING_ENDED;
import static com.android.server.uwb.pm.RangingSessionController.SECURE_SESSION_ESTABLISHED;
import static com.android.server.uwb.pm.RangingSessionController.SESSION_START;
import static com.android.server.uwb.pm.RangingSessionController.TRANSPORT_COMPLETED;
import static com.android.server.uwb.pm.RangingSessionController.TRANSPORT_INIT;

import static com.google.uwb.support.fira.FiraParams.MULTI_NODE_MODE_ONE_TO_MANY;
import static com.google.uwb.support.fira.FiraParams.RFRAME_CONFIG_SP3;
import static com.google.uwb.support.fira.FiraParams.STS_CONFIG_DYNAMIC;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertisingSetParameters;
import android.content.AttributionSource;
import android.content.Context;
import android.os.Handler;
import android.os.RemoteException;
import android.os.test.TestLooper;
import android.platform.test.annotations.Presubmit;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.SessionHandle;
import android.uwb.UwbAddress;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.UwbInjector;
import com.android.server.uwb.UwbServiceCore;
import com.android.server.uwb.data.ServiceProfileData.ServiceProfileInfo;
import com.android.server.uwb.data.UwbConfig;
import com.android.server.uwb.discovery.ble.DiscoveryAdvertisement;
import com.android.server.uwb.discovery.info.SecureComponentInfo;
import com.android.server.uwb.discovery.info.UwbIndicationData;
import com.android.server.uwb.discovery.info.VendorSpecificData;
import com.android.server.uwb.multchip.UwbMultichipData;
import com.android.server.uwb.pm.PacsControleeSession;
import com.android.server.uwb.util.ObjectIdentifier;

import com.google.common.collect.ImmutableList;
import com.google.uwb.support.fira.FiraSpecificationParams;
import com.google.uwb.support.generic.GenericSpecificationParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

@SmallTest
@RunWith(AndroidJUnit4.class)
@Presubmit
// TODO : Add unit tests for states during implementation
public class PacsControleeSessionTest {
    private static final String DEFAULT_CHIP_ID = "defaultChipId";

    @Mock
    private AttributionSource mAttributionSource;
    @Mock
    private Context mContext;
    @Mock
    BluetoothManager mMockBluetoothManager;
    @Mock
    private UwbInjector mUwbInjector;
    @Mock
    private ServiceProfileInfo mServiceProfileInfo;
    @Mock
    private IUwbRangingCallbacks mIUwbRangingCallbacks;
    @Mock
    private Handler mHandler;
    @Mock
    private UwbServiceCore mUwbServiceCore;
    @Mock
    private UwbMultichipData mUwbMultichipData;

    private TestLooper mLooper;
    private PacsControleeSession mRangingSessionController;
    @Mock
    private GenericSpecificationParams mGenericSpecificationParams;
    private FiraSpecificationParams mFiraSpecificationParams;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mLooper = new TestLooper();

        when(mHandler.getLooper()).thenReturn(mLooper.getLooper());
        when(mUwbInjector.getUwbServiceCore()).thenReturn(mUwbServiceCore);
        when(mUwbMultichipData.getDefaultChipId()).thenReturn(DEFAULT_CHIP_ID);
        when(mUwbInjector.getMultichipData()).thenReturn(mUwbMultichipData);
        when(mUwbServiceCore.getCachedSpecificationParams(DEFAULT_CHIP_ID)).thenReturn(
                mGenericSpecificationParams);
        mFiraSpecificationParams = new FiraSpecificationParams.Builder()
                .setSupportedChannels(ImmutableList.of(5, 9)).build();
        when(mGenericSpecificationParams.getFiraSpecificationParams()).thenReturn(
                mFiraSpecificationParams);
        when(mContext.createContext(any())).thenReturn(mContext);
        when(mContext.getSystemService(BluetoothManager.class))
                .thenReturn(mMockBluetoothManager);
        when(mServiceProfileInfo.getServiceAdfOid()).thenReturn(
                Optional.of(ObjectIdentifier.fromBytes(new byte[] {(byte) 1})));
        SessionHandle sessionHandle = mock(SessionHandle.class);

        mRangingSessionController = new PacsControleeSession(sessionHandle,
                mAttributionSource,
                mContext,
                mUwbInjector,
                mServiceProfileInfo,
                mIUwbRangingCallbacks,
                mHandler,
                DEFAULT_CHIP_ID);
        AdvertisingSetParameters parameters =
                new AdvertisingSetParameters.Builder()
                        .setInterval(AdvertisingSetParameters.INTERVAL_HIGH)
                        .setIncludeTxPower(true)
                        .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
                        .build();
        DiscoveryAdvertisement advertisement =
                new DiscoveryAdvertisement(
                        new UwbIndicationData(
                                /*firaUwbSupport=*/ true,
                                /*iso14443Support=*/ true,
                                /*uwbRegulartoryInfoAvailableInAd=*/ true,
                                /*uwbRegulartoryInfoAvailableInOob=*/ false,
                                /*firaProfileInfoAvailableInAd=*/ true,
                                /*firaProfileInfoAvailableInOob=*/ false,
                                /*dualGapRoleSupport=*/ true,
                                /*bluetoothRssiThresholdDbm=*/ -100,
                                new SecureComponentInfo[]{}),
                        /*regulatoryInfo=*/ null,
                        /*firaProfileSupportInfo=*/ null,
                        new VendorSpecificData[]{
                                new VendorSpecificData(/*vendorId=*/ 117, new byte[]{0x02, 0x15}),
                        });
        mRangingSessionController.setDiscoveryAdvertisement(advertisement);
        mRangingSessionController.setAdvertisingSetParameters(parameters);
    }

    @Test
    public void testOpenRangingSession() throws RemoteException {

        UwbConfig uwbConfig = new UwbConfig.Builder()
                .setUwbRole(CONTROLEE_AND_RESPONDER)
                .setStsConfig(STS_CONFIG_DYNAMIC)
                .setMultiNodeMode(MULTI_NODE_MODE_ONE_TO_MANY)
                .setRframeConfig(RFRAME_CONFIG_SP3)
                .setTofReport(true)
                .setOobType(OOB_TYPE_BLE)
                .setOobBleRole(PERIPHERAL)
                .build();
        mRangingSessionController.mSessionInfo.setSessionId(1);

        mRangingSessionController.mSessionInfo
                .setUwbAddress(UwbAddress.fromBytes(new byte[]{0x0A, 0x01}));
        mRangingSessionController.mSessionInfo.mDestAddressList
                .add(UwbAddress.fromBytes(new byte[]{0x0B, 0x01}));

        mRangingSessionController.openRangingSession();

        assertEquals(uwbConfig.mUwbRole, CONTROLEE_AND_RESPONDER);

        verify(mUwbServiceCore).openRanging(eq(mAttributionSource),
                eq(mRangingSessionController.mSessionInfo.mSessionHandle),
                eq(mIUwbRangingCallbacks), any(), eq(DEFAULT_CHIP_ID));
    }

    @Test
    public void testAllStateTransitions() {
        assertEquals(mRangingSessionController.getCurrentState().getName(),
                mRangingSessionController.getIdleState().getName());
        mRangingSessionController.sendMessage(SESSION_START);

        mLooper.dispatchAll();

        verify(mContext, times(1)).getSystemService(BluetoothManager.class);

        assertEquals(mRangingSessionController.getCurrentState().getName(),
                mRangingSessionController.getDiscoveryState().getName());

        mRangingSessionController.sendMessage(TRANSPORT_INIT);

        mLooper.dispatchAll();

        assertEquals(mRangingSessionController.getCurrentState().getName(),
                mRangingSessionController.getTransportState().getName());

        mRangingSessionController.sendMessage(TRANSPORT_COMPLETED);

        mLooper.dispatchAll();

        assertEquals(mRangingSessionController.getCurrentState().getName(),
                mRangingSessionController.getSecureState().getName());

        mRangingSessionController.sendMessage(SECURE_SESSION_ESTABLISHED);

        mLooper.dispatchAll();

        assertEquals(mRangingSessionController.getCurrentState().getName(),
                mRangingSessionController.getRangingState().getName());

        mRangingSessionController.sendMessage(RANGING_ENDED);

        mLooper.dispatchAll();

        assertEquals(mRangingSessionController.getCurrentState().getName(),
                mRangingSessionController.getEndingState().getName());

    }
}
