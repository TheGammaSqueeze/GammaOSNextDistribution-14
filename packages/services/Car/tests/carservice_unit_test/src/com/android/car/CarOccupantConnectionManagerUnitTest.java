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

package com.android.car;

import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER;
import static android.car.VehicleAreaSeat.SEAT_ROW_1_LEFT;
import static android.car.occupantconnection.CarOccupantConnectionManager.CONNECTION_ERROR_USER_REJECTED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.occupantconnection.CarOccupantConnectionManager;
import android.car.occupantconnection.CarOccupantConnectionManager.ConnectionRequestCallback;
import android.car.occupantconnection.CarOccupantConnectionManager.PayloadCallback;
import android.car.occupantconnection.CarOccupantConnectionManager.PayloadTransferException;
import android.car.occupantconnection.ICarOccupantConnection;
import android.car.occupantconnection.IConnectionRequestCallback;
import android.car.occupantconnection.IPayloadCallback;
import android.car.occupantconnection.Payload;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.Executor;

@RunWith(MockitoJUnitRunner.class)
public final class CarOccupantConnectionManagerUnitTest {

    private static final String PACKAGE_NAME = "my_package_name";
    private static final String RECEIVER_ENDPOINT_ID = "test_receiver_endpointId";
    private static final int TIMEOUT_MS = 1000;

    @Mock
    private Car mCar;
    @Mock
    private Context mContext;
    @Mock
    private IBinder mBinder;
    @Mock
    private ICarOccupantConnection mService;
    @Mock
    private Executor mCallbackExecutor;
    @Mock
    private ConnectionRequestCallback mConnectionRequestCallback;
    @Mock
    private PayloadCallback mPayloadCallback;

    private final OccupantZoneInfo mReceiverZone =
            new OccupantZoneInfo(/* zoneId= */ 0, OCCUPANT_TYPE_DRIVER, SEAT_ROW_1_LEFT);

    private CarOccupantConnectionManager mOccupantConnectionManager;

    @Before
    public void setUp() {
        when(mBinder.queryLocalInterface(anyString())).thenReturn(mService);
        when(mCar.getContext()).thenReturn(mContext);
        when(mContext.getPackageName()).thenReturn(PACKAGE_NAME);
        mOccupantConnectionManager = new CarOccupantConnectionManager(mCar, mBinder);
    }

    @Test
    public void testRequestConnectionWithNullParameters_throwsException() {
        assertThrows(NullPointerException.class,
                () -> mOccupantConnectionManager.requestConnection(
                        /* receiverZone= */ null, mCallbackExecutor, mConnectionRequestCallback));
        assertThrows(NullPointerException.class,
                () -> mOccupantConnectionManager.requestConnection(
                        mReceiverZone, /* executor= */ null, mConnectionRequestCallback));
        assertThrows(NullPointerException.class,
                () -> mOccupantConnectionManager.requestConnection(
                        mReceiverZone, mCallbackExecutor, /* callback= */ null));
    }

    @Test
    public void testRequestConnectionWithPendingConnection_throwsException() {
        mOccupantConnectionManager.requestConnection(mReceiverZone, mCallbackExecutor,
                mock(ConnectionRequestCallback.class));

        // The client shouldn't request another connection to the same occupant zone because there
        // is a pending connection request to that occupant zone.
        assertThrows(IllegalStateException.class,
                () -> mOccupantConnectionManager.requestConnection(
                        mReceiverZone, mCallbackExecutor, mConnectionRequestCallback));
    }

    @Test
    public void testRequestConnectionWithEstablishedConnection_throwsException()
            throws RemoteException {
        IConnectionRequestCallback[] binderCallback = new IConnectionRequestCallback[1];
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            binderCallback[0] = (IConnectionRequestCallback) args[2];
            return null;
        }).when(mService).requestConnection(anyString(), any(OccupantZoneInfo.class), any());

        mOccupantConnectionManager.requestConnection(mReceiverZone, mCallbackExecutor,
                mock(ConnectionRequestCallback.class));
        binderCallback[0].onConnected(mReceiverZone);

        // The client shouldn't request another connection to the same occupant zone because there
        // is an established connection to that occupant zone.
        assertThrows(IllegalStateException.class,
                () -> mOccupantConnectionManager.requestConnection(
                        mReceiverZone, mCallbackExecutor, mConnectionRequestCallback));
    }

    @Test
    public void testRequestConnectionWithRejectedConnection() throws RemoteException {
        IConnectionRequestCallback[] binderCallback = new IConnectionRequestCallback[1];
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            binderCallback[0] = (IConnectionRequestCallback) args[2];
            return null;
        }).when(mService).requestConnection(anyString(), any(OccupantZoneInfo.class), any());

        mOccupantConnectionManager.requestConnection(mReceiverZone, mCallbackExecutor,
                mock(ConnectionRequestCallback.class));
        binderCallback[0].onFailed(mReceiverZone, CONNECTION_ERROR_USER_REJECTED);

        // The client can request another connection to the same occupant zone since the previous
        // request was rejected.
        mOccupantConnectionManager.requestConnection(mReceiverZone, mCallbackExecutor,
                mConnectionRequestCallback);
    }

    @Test
    public void testRequestConnectionWithRemoteException() throws RemoteException {
        // The first call fails due to a RemoteException.
        doThrow(new RemoteException())
                .when(mService).requestConnection(anyString(), any(OccupantZoneInfo.class), any());
        mOccupantConnectionManager.requestConnection(mReceiverZone, mCallbackExecutor,
                mConnectionRequestCallback);

        // The second call succeeds. It should not trigger IllegalStateException because
        // mConnectionRequestCallback was not added previously.
        doNothing()
                .when(mService).requestConnection(anyString(), any(OccupantZoneInfo.class), any());
        mOccupantConnectionManager.requestConnection(mReceiverZone, mCallbackExecutor,
                mConnectionRequestCallback);
    }

    @Test
    public void testRequestConnectionCallbackInvoked() throws RemoteException {
        IConnectionRequestCallback[] binderCallback = new IConnectionRequestCallback[1];
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            String packageName = (String) args[0];
            assertThat(packageName).isEqualTo(PACKAGE_NAME);
            OccupantZoneInfo receiverZone = (OccupantZoneInfo) args[1];
            assertThat(receiverZone).isEqualTo(mReceiverZone);
            binderCallback[0] = (IConnectionRequestCallback) args[2];
            // Don't call binderCallback[0].onConnected() here, because mConnectionRequestCallback
            // is added AFTER this method returns. So if we call binderCallback.onConnected() here,
            // mConnectionRequestCallback will not be invoked.
            return null;
        }).when(mService).requestConnection(anyString(), any(OccupantZoneInfo.class), any());

        mOccupantConnectionManager.requestConnection(mReceiverZone, command -> {
            command.run();
            // Verify that mConnectionRequestCallback is invoked on the Executor.
            verify(mConnectionRequestCallback, timeout(1000)).onConnected(mReceiverZone);
        }, mConnectionRequestCallback);
        binderCallback[0].onConnected(mReceiverZone);
    }

    @Test
    public void testCancelConnectionWithNullParameter_throwsException() {
        assertThrows(NullPointerException.class,
                () -> mOccupantConnectionManager.cancelConnection(/* receiverZone= */ null));

    }

    @Test
    public void testCancelConnectionWithoutConnectionRequest_throwsException() {
        assertThrows(IllegalStateException.class,
                () -> mOccupantConnectionManager.cancelConnection(mReceiverZone));
    }

    @Test
    public void testCancelConnection() throws RemoteException {
        mOccupantConnectionManager.requestConnection(mReceiverZone, mCallbackExecutor,
                mConnectionRequestCallback);
        mOccupantConnectionManager.cancelConnection(mReceiverZone);
        mOccupantConnectionManager.requestConnection(mReceiverZone, mCallbackExecutor,
                mConnectionRequestCallback);
        mOccupantConnectionManager.cancelConnection(mReceiverZone);

        verify(mService, times(2)).cancelConnection(PACKAGE_NAME, mReceiverZone);
    }

    @Test
    public void testRegisterReceiverWithNullParameters_throwsException() {
        assertThrows(NullPointerException.class,
                () -> mOccupantConnectionManager.registerReceiver(
                        /* receiverEndpointId= */ null, mCallbackExecutor, mPayloadCallback));
        assertThrows(NullPointerException.class,
                () -> mOccupantConnectionManager.registerReceiver(
                        RECEIVER_ENDPOINT_ID, /* executor= */ null, mPayloadCallback));
        assertThrows(NullPointerException.class,
                () -> mOccupantConnectionManager.registerReceiver(
                        RECEIVER_ENDPOINT_ID, mCallbackExecutor, /* callback= */ null));
    }

    @Test
    public void testRegisterReceiverWithDuplicateReceiverId_throwsException()
            throws RemoteException {
        // The first registerReceiver() call should run normally, while the second
        // registerReceiver() call should throw an exception because the same ID has been
        // registered.
        doNothing().doThrow(IllegalStateException.class)
                .when(mService).registerReceiver(eq(PACKAGE_NAME), eq(RECEIVER_ENDPOINT_ID), any());
        mOccupantConnectionManager.registerReceiver(RECEIVER_ENDPOINT_ID, mCallbackExecutor,
                mPayloadCallback);

        assertThrows(IllegalStateException.class,
                () -> mOccupantConnectionManager.registerReceiver(
                        RECEIVER_ENDPOINT_ID, mCallbackExecutor, mPayloadCallback));
    }

    @Test
    public void testRegisterReceiverWithRemoteException() throws RemoteException {
        // The first call fails due to a RemoteException.
        doThrow(new RemoteException())
                .when(mService).registerReceiver(anyString(), anyString(), any());
        mOccupantConnectionManager.registerReceiver(RECEIVER_ENDPOINT_ID, mCallbackExecutor,
                mPayloadCallback);

        // The second call succeeds. It should not trigger IllegalStateException because
        // mPayloadCallback was not added previously.
        doNothing().when(mService).registerReceiver(anyString(), anyString(), any());
        mOccupantConnectionManager.registerReceiver(RECEIVER_ENDPOINT_ID, mCallbackExecutor,
                mPayloadCallback);
    }

    @Test
    public void testRegisterReceiverCallbackInvoked() throws RemoteException {
        OccupantZoneInfo senderZone = mock(OccupantZoneInfo.class);
        Payload payload = mock(Payload.class);
        IPayloadCallback[] binderCallback = new IPayloadCallback[1];
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            String packageName = (String) args[0];
            assertThat(packageName).isEqualTo(PACKAGE_NAME);
            String receiverEndpointId = (String) args[1];
            assertThat(receiverEndpointId).isEqualTo(RECEIVER_ENDPOINT_ID);
            binderCallback[0] = (IPayloadCallback) args[2];
            // Don't call binderCallback[0].onPayloadReceived() here, because mPayloadCallback
            // is added AFTER this method returns. So if we call binderCallback.onPayloadReceived()
            // here, mPayloadCallback will not be invoked.
            return null;
        }).when(mService).registerReceiver(anyString(), anyString(), any());

        mOccupantConnectionManager.registerReceiver(RECEIVER_ENDPOINT_ID, command -> {
            command.run();
            // Verify that mPayloadCallback is invoked on the Executor.
            verify(mPayloadCallback, timeout(TIMEOUT_MS)).onPayloadReceived(senderZone, payload);
        }, mPayloadCallback);
        binderCallback[0].onPayloadReceived(senderZone, RECEIVER_ENDPOINT_ID, payload);
    }

    @Test
    public void testUnregisterReceiverWithNullParameter_throwsException() throws RemoteException {
        assertThrows(NullPointerException.class,
                () -> mOccupantConnectionManager.unregisterReceiver(
                        /* receiverEndpointId= */ null));

    }

    @Test
    public void testUnregisterReceiver() throws RemoteException {
        mOccupantConnectionManager.unregisterReceiver(RECEIVER_ENDPOINT_ID);

        verify(mService).unregisterReceiver(PACKAGE_NAME, RECEIVER_ENDPOINT_ID);
    }

    @Test
    public void testSendPayloadNullParameters_throwsException() {
        assertThrows(NullPointerException.class,
                () -> mOccupantConnectionManager
                        .sendPayload(/* receiverZone= */ null, mock(Payload.class)));
        assertThrows(NullPointerException.class,
                () -> mOccupantConnectionManager.sendPayload(
                        mReceiverZone, /* payload= */ null));
    }

    @Test
    public void testSendPayloadWithoutConnection_throwsException() throws RemoteException {
        doThrow(IllegalStateException.class)
                .when(mService).sendPayload(eq(PACKAGE_NAME), any(), any());

        assertThrows(PayloadTransferException.class,
                () -> mOccupantConnectionManager.sendPayload(mReceiverZone, mock(Payload.class)));
    }

    @Test
    public void testSendPayload() throws PayloadTransferException, RemoteException {
        Payload payload = mock(Payload.class);
        mOccupantConnectionManager.sendPayload(mReceiverZone, payload);

        verify(mService).sendPayload(PACKAGE_NAME, mReceiverZone, payload);
    }

    @Test
    public void testIsConnectedWithNullParameters_throwsException() {
        assertThrows(NullPointerException.class,
                () -> mOccupantConnectionManager.isConnected(/* receiverZone= */ null));
    }

    @Test
    public void testIsConnected() throws RemoteException {
        when(mService.isConnected(PACKAGE_NAME, mReceiverZone)).thenReturn(true);
        boolean isConnected = mOccupantConnectionManager.isConnected(mReceiverZone);

        verify(mService).isConnected(PACKAGE_NAME, mReceiverZone);
        assertThat(isConnected).isTrue();
    }

    @Test
    public void testDisconnectWithNullParameters_throwsException() {
        assertThrows(NullPointerException.class,
                () -> mOccupantConnectionManager.disconnect(/* receiverZone= */ null));
    }

    @Test
    public void testDisconnect() throws RemoteException {
        mOccupantConnectionManager.disconnect(mReceiverZone);

        verify(mService).disconnect(PACKAGE_NAME, mReceiverZone);
    }
}
