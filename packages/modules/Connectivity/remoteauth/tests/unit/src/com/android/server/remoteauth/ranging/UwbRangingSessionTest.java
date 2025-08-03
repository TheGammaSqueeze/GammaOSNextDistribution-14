/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.server.remoteauth.ranging;

import static androidx.core.uwb.backend.impl.internal.RangingDevice.SESSION_ID_UNSET;
import static androidx.core.uwb.backend.impl.internal.RangingMeasurement.CONFIDENCE_HIGH;
import static androidx.core.uwb.backend.impl.internal.Utils.CONFIG_PROVISIONED_UNICAST_DS_TWR;
import static androidx.core.uwb.backend.impl.internal.Utils.DURATION_1_MS;
import static androidx.core.uwb.backend.impl.internal.Utils.NORMAL;
import static androidx.core.uwb.backend.impl.internal.Utils.STATUS_ERROR;
import static androidx.core.uwb.backend.impl.internal.Utils.STATUS_OK;

import static com.android.server.remoteauth.ranging.RangingCapabilities.RANGING_METHOD_UWB;
import static com.android.server.remoteauth.ranging.RangingReport.PROXIMITY_STATE_INSIDE;
import static com.android.server.remoteauth.ranging.RangingSession.RANGING_ERROR_FAILED_TO_START;
import static com.android.server.remoteauth.ranging.RangingSession.RANGING_ERROR_FAILED_TO_STOP;
import static com.android.server.remoteauth.ranging.SessionParameters.DEVICE_ROLE_INITIATOR;
import static com.android.server.remoteauth.ranging.SessionParameters.DEVICE_ROLE_RESPONDER;

import static com.google.uwb.support.fira.FiraParams.UWB_CHANNEL_9;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.core.uwb.backend.impl.internal.RangingControlee;
import androidx.core.uwb.backend.impl.internal.RangingController;
import androidx.core.uwb.backend.impl.internal.RangingMeasurement;
import androidx.core.uwb.backend.impl.internal.RangingPosition;
import androidx.core.uwb.backend.impl.internal.RangingSessionCallback;
import androidx.core.uwb.backend.impl.internal.UwbAddress;
import androidx.core.uwb.backend.impl.internal.UwbComplexChannel;
import androidx.core.uwb.backend.impl.internal.UwbDevice;
import androidx.core.uwb.backend.impl.internal.UwbRangeDataNtfConfig;
import androidx.core.uwb.backend.impl.internal.UwbServiceImpl;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.remoteauth.ranging.RangingCapabilities.RangingMethod;
import com.android.server.remoteauth.ranging.RangingSession.RangingCallback;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.List;
import java.util.concurrent.Executor;

/** Unit test for {@link UwbRangingSession}. */
@RunWith(AndroidJUnit4.class)
public class UwbRangingSessionTest {

    private static final String TEST_DEVICE_ID = "test_device_id";
    @RangingMethod private static final int TEST_RANGING_METHOD = RANGING_METHOD_UWB;
    private static final float TEST_LOWER_PROXIMITY_BOUNDARY_M = 1.0f;
    private static final float TEST_UPPER_PROXIMITY_BOUNDARY_M = 2.5f;
    private static final boolean TEST_AUTO_DERIVE_PARAMS = true;
    private static final byte[] TEST_BASE_KEY =
            new byte[] {
                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d,
                0x0e, 0x0f
            };
    private static final byte[] TEST_SYNC_DATA =
            new byte[] {
                0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e,
                0x0f, 0x00
            };
    private static final SessionParameters TEST_SESSION_PARAMETER_INITIATOR =
            new SessionParameters.Builder()
                    .setDeviceId(TEST_DEVICE_ID)
                    .setRangingMethod(TEST_RANGING_METHOD)
                    .setDeviceRole(DEVICE_ROLE_INITIATOR)
                    .setLowerProximityBoundaryM(TEST_LOWER_PROXIMITY_BOUNDARY_M)
                    .setUpperProximityBoundaryM(TEST_UPPER_PROXIMITY_BOUNDARY_M)
                    .build();
    private static final SessionParameters TEST_SESSION_PARAMETER_RESPONDER =
            new SessionParameters.Builder()
                    .setDeviceId(TEST_DEVICE_ID)
                    .setRangingMethod(TEST_RANGING_METHOD)
                    .setDeviceRole(DEVICE_ROLE_RESPONDER)
                    .setLowerProximityBoundaryM(TEST_LOWER_PROXIMITY_BOUNDARY_M)
                    .setUpperProximityBoundaryM(TEST_UPPER_PROXIMITY_BOUNDARY_M)
                    .build();
    private static final SessionParameters TEST_SESSION_PARAMETER_INITIATOR_W_AD =
            new SessionParameters.Builder()
                    .setDeviceId(TEST_DEVICE_ID)
                    .setRangingMethod(TEST_RANGING_METHOD)
                    .setDeviceRole(DEVICE_ROLE_INITIATOR)
                    .setLowerProximityBoundaryM(TEST_LOWER_PROXIMITY_BOUNDARY_M)
                    .setUpperProximityBoundaryM(TEST_UPPER_PROXIMITY_BOUNDARY_M)
                    .setAutoDeriveParams(TEST_AUTO_DERIVE_PARAMS)
                    .setBaseKey(TEST_BASE_KEY)
                    .setSyncData(TEST_SYNC_DATA)
                    .build();
    private static final UwbAddress TEST_UWB_LOCAL_ADDRESS =
            UwbAddress.fromBytes(new byte[] {0x00, 0x01});
    private static final UwbAddress TEST_UWB_PEER_ADDRESS =
            UwbAddress.fromBytes(new byte[] {0x00, 0x02});
    private static final UwbComplexChannel TEST_UWB_COMPLEX_CHANNEL =
            new UwbComplexChannel(UWB_CHANNEL_9, /* preambleIndex= */ 9);
    private static final androidx.core.uwb.backend.impl.internal.RangingParameters
            TEST_UWB_RANGING_PARAMETERS =
                    new androidx.core.uwb.backend.impl.internal.RangingParameters(
                            CONFIG_PROVISIONED_UNICAST_DS_TWR,
                            /* sessionId= */ SESSION_ID_UNSET,
                            /* subSessionId= */ SESSION_ID_UNSET,
                            /* SessionInfo= */ new byte[] {},
                            /* subSessionInfo= */ new byte[] {},
                            TEST_UWB_COMPLEX_CHANNEL,
                            List.of(TEST_UWB_PEER_ADDRESS),
                            NORMAL,
                            new UwbRangeDataNtfConfig.Builder().build(),
                            DURATION_1_MS,
                            /* isAoaDisabled= */ false);
    private static final RangingParameters TEST_RANGING_PARAMETERS =
            new RangingParameters.Builder()
                    .setUwbLocalAddress(TEST_UWB_LOCAL_ADDRESS)
                    .setUwbRangingParameters(TEST_UWB_RANGING_PARAMETERS)
                    .build();
    private static final UwbAddress TEST_DERIVED_UWB_LOCAL_ADDRESS =
            UwbAddress.fromBytes(new byte[] {0x4C, (byte) 0xB4});
    private static final UwbAddress TEST_DERIVED_UWB_PEER_ADDRESS =
            UwbAddress.fromBytes(new byte[] {(byte) 0xAE, 0x2E});
    private static final UwbComplexChannel TEST_DERIVED_UWB_COMPLEX_CHANNEL =
            new UwbComplexChannel(UWB_CHANNEL_9, /* preambleIndex= */ 12);
    private static final byte[] TEST_DERIVED_STS_KEY =
            new byte[] {
                0x76,
                (byte) 0xD7,
                (byte) 0xB6,
                0x1A,
                (byte) 0x8D,
                0x29,
                0x1A,
                0x52,
                (byte) 0xBB,
                (byte) 0xBF,
                (byte) 0xE6,
                0x28,
                (byte) 0xAD,
                0x44,
                (byte) 0xFB,
                0x2E
            };

    private static final UwbDevice TEST_UWB_DEVICE =
            UwbDevice.createForAddress(TEST_UWB_PEER_ADDRESS.toBytes());
    private static final float TEST_DISTANCE = 1.5f;
    private static final RangingMeasurement TEST_RANGING_MEASUREMENT =
            new RangingMeasurement(
                    /* confidence= */ CONFIDENCE_HIGH,
                    /* value= */ TEST_DISTANCE,
                    /* valid= */ true);
    private static final RangingPosition TEST_RANGING_POSITION =
            new RangingPosition(
                    TEST_RANGING_MEASUREMENT,
                    /* azimuth= */ null,
                    /* elevation= */ null,
                    /* dlTdoaMeasurement= */ null,
                    /* elapsedRealtimeNanos= */ 0,
                    /* rssi= */ 0);

    @Mock private Context mContext;
    @Mock private UwbServiceImpl mUwbServiceImpl;
    @Mock private RangingController mRangingController;
    @Mock private RangingControlee mRangingControlee;
    @Mock private RangingCallback mRangingCallback;
    @Mock private Executor mCallbackExecutor;

    private UwbRangingSession mUwbRangingSession;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mUwbServiceImpl.getController(mContext)).thenReturn(mRangingController);
        when(mUwbServiceImpl.getControlee(mContext)).thenReturn(mRangingControlee);
        when(mRangingController.startRanging(any(), any())).thenReturn(STATUS_OK);
        when(mRangingControlee.startRanging(any(), any())).thenReturn(STATUS_OK);
        doAnswer(
                invocation -> {
                    Runnable t = invocation.getArgument(0);
                    t.run();
                    return true;
                })
                .when(mCallbackExecutor)
                .execute(any(Runnable.class));
    }

    @Test
    public void testConstruction_nullArgument() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new UwbRangingSession(
                                null, TEST_SESSION_PARAMETER_INITIATOR, mUwbServiceImpl));
        assertThrows(
                NullPointerException.class,
                () -> new UwbRangingSession(mContext, null, mUwbServiceImpl));
        assertThrows(
                NullPointerException.class,
                () -> new UwbRangingSession(mContext, TEST_SESSION_PARAMETER_INITIATOR, null));
    }

    @Test
    public void testConstruction_initiatorSuccess() {
        mUwbRangingSession =
                new UwbRangingSession(mContext, TEST_SESSION_PARAMETER_INITIATOR, mUwbServiceImpl);
        verify(mUwbServiceImpl, times(1)).getController(mContext);
    }

    @Test
    public void testConstruction_responderSuccess() {
        mUwbRangingSession =
                new UwbRangingSession(mContext, TEST_SESSION_PARAMETER_RESPONDER, mUwbServiceImpl);
        verify(mUwbServiceImpl, times(1)).getControlee(mContext);
    }

    @Test
    public void testStart_nullArgument() {
        mUwbRangingSession =
                new UwbRangingSession(mContext, TEST_SESSION_PARAMETER_INITIATOR, mUwbServiceImpl);

        assertThrows(
                NullPointerException.class,
                () -> mUwbRangingSession.start(TEST_RANGING_PARAMETERS, mCallbackExecutor, null));
        assertThrows(
                NullPointerException.class,
                () -> mUwbRangingSession.start(null, mCallbackExecutor, mRangingCallback));
        assertThrows(
                NullPointerException.class,
                () -> mUwbRangingSession.start(TEST_RANGING_PARAMETERS, null, mRangingCallback));
        assertThrows(
                NullPointerException.class,
                () ->
                        mUwbRangingSession.start(
                                new RangingParameters.Builder().build(),
                                mCallbackExecutor,
                                mRangingCallback));
    }

    @Test
    public void testStart_initiatorWithoutADFailed() {
        when(mRangingController.startRanging(any(), any())).thenReturn(STATUS_ERROR);

        mUwbRangingSession =
                new UwbRangingSession(mContext, TEST_SESSION_PARAMETER_INITIATOR, mUwbServiceImpl);
        mUwbRangingSession.start(TEST_RANGING_PARAMETERS, mCallbackExecutor, mRangingCallback);

        verify(mRangingController, times(1)).setComplexChannel(TEST_UWB_COMPLEX_CHANNEL);
        verify(mRangingController, times(1)).setLocalAddress(TEST_UWB_LOCAL_ADDRESS);
        verify(mRangingController, times(1)).setRangingParameters(TEST_UWB_RANGING_PARAMETERS);
        verify(mRangingController, times(1)).startRanging(any(), any());
        ArgumentCaptor<SessionInfo> captor = ArgumentCaptor.forClass(SessionInfo.class);
        verify(mRangingCallback, times(1))
                .onError(captor.capture(), eq(RANGING_ERROR_FAILED_TO_START));
        assertEquals(captor.getValue().getDeviceId(), TEST_DEVICE_ID);
    }

    private void testRangingCallback() {
        Answer startRangingResponse =
                new Answer() {
                    public Object answer(InvocationOnMock invocation) {
                        Object[] args = invocation.getArguments();
                        RangingSessionCallback cb = (RangingSessionCallback) args[0];
                        cb.onRangingInitialized(TEST_UWB_DEVICE);
                        cb.onRangingResult(TEST_UWB_DEVICE, TEST_RANGING_POSITION);
                        return STATUS_OK;
                    }
                };
        doAnswer(startRangingResponse)
                .when(mRangingController)
                .startRanging(any(RangingSessionCallback.class), any());
    }

    @Test
    public void testStart_initiatorWithADSucceed() {
        testRangingCallback();
        mUwbRangingSession =
                new UwbRangingSession(
                        mContext, TEST_SESSION_PARAMETER_INITIATOR_W_AD, mUwbServiceImpl);
        mUwbRangingSession.start(TEST_RANGING_PARAMETERS, mCallbackExecutor, mRangingCallback);

        verify(mRangingController, times(1)).setComplexChannel(TEST_DERIVED_UWB_COMPLEX_CHANNEL);
        verify(mRangingController, times(1)).setLocalAddress(TEST_DERIVED_UWB_LOCAL_ADDRESS);
        ArgumentCaptor<androidx.core.uwb.backend.impl.internal.RangingParameters> captor =
                ArgumentCaptor.forClass(
                        androidx.core.uwb.backend.impl.internal.RangingParameters.class);
        verify(mRangingController, times(1)).setRangingParameters(captor.capture());
        assertEquals(
                captor.getValue().getUwbConfigId(), TEST_UWB_RANGING_PARAMETERS.getUwbConfigId());
        assertEquals(captor.getValue().getSessionId(), SESSION_ID_UNSET);
        assertEquals(captor.getValue().getSubSessionId(), SESSION_ID_UNSET);
        assertArrayEquals(captor.getValue().getSessionKeyInfo(), TEST_DERIVED_STS_KEY);
        assertArrayEquals(captor.getValue().getSubSessionKeyInfo(), new byte[] {});
        assertEquals(captor.getValue().getComplexChannel(), TEST_DERIVED_UWB_COMPLEX_CHANNEL);
        assertEquals(captor.getValue().getPeerAddresses().get(0), TEST_DERIVED_UWB_PEER_ADDRESS);
        assertEquals(
                captor.getValue().getRangingUpdateRate(),
                TEST_UWB_RANGING_PARAMETERS.getRangingUpdateRate());
        assertEquals(
                captor.getValue().getUwbRangeDataNtfConfig(),
                TEST_UWB_RANGING_PARAMETERS.getUwbRangeDataNtfConfig());
        assertEquals(
                captor.getValue().getSlotDuration(), TEST_UWB_RANGING_PARAMETERS.getSlotDuration());
        assertEquals(
                captor.getValue().isAoaDisabled(), TEST_UWB_RANGING_PARAMETERS.isAoaDisabled());
        verify(mRangingController, times(1)).startRanging(any(), any());
        ArgumentCaptor<SessionInfo> captor2 = ArgumentCaptor.forClass(SessionInfo.class);
        ArgumentCaptor<RangingReport> captor3 = ArgumentCaptor.forClass(RangingReport.class);
        verify(mRangingCallback, times(1)).onRangingReport(captor2.capture(), captor3.capture());
        assertEquals(captor2.getValue().getDeviceId(), TEST_DEVICE_ID);
        RangingReport rangingReport = captor3.getValue();
        assertEquals(rangingReport.getDistanceM(), TEST_DISTANCE, 0.0f);
        assertEquals(rangingReport.getProximityState(), PROXIMITY_STATE_INSIDE);
    }

    @Test
    public void testStop_sessionNotStarted() {
        when(mRangingController.stopRanging()).thenReturn(STATUS_ERROR);

        mUwbRangingSession =
                new UwbRangingSession(mContext, TEST_SESSION_PARAMETER_INITIATOR, mUwbServiceImpl);
        mUwbRangingSession.stop();

        verifyZeroInteractions(mRangingController);
        verifyZeroInteractions(mRangingCallback);
    }

    @Test
    public void testStop_failed() {
        when(mRangingController.stopRanging()).thenReturn(STATUS_ERROR);

        mUwbRangingSession =
                new UwbRangingSession(mContext, TEST_SESSION_PARAMETER_INITIATOR, mUwbServiceImpl);
        mUwbRangingSession.start(TEST_RANGING_PARAMETERS, mCallbackExecutor, mRangingCallback);
        mUwbRangingSession.stop();

        verify(mRangingController, times(1)).setComplexChannel(any());
        verify(mRangingController, times(1)).setLocalAddress(any());
        verify(mRangingController, times(1)).setRangingParameters(any());
        verify(mRangingController, times(1)).startRanging(any(), any());
        verify(mRangingController, times(1)).stopRanging();
        ArgumentCaptor<SessionInfo> captor = ArgumentCaptor.forClass(SessionInfo.class);
        verify(mRangingCallback, times(1))
                .onError(captor.capture(), eq(RANGING_ERROR_FAILED_TO_STOP));
        assertEquals(captor.getValue().getDeviceId(), TEST_DEVICE_ID);
    }
}
