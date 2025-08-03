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

package com.android.car.occupantconnection;

import static android.car.Car.CAR_INTENT_ACTION_RECEIVER_SERVICE;
import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER;
import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_FRONT_PASSENGER;
import static android.car.VehicleAreaSeat.SEAT_ROW_1_LEFT;
import static android.car.VehicleAreaSeat.SEAT_ROW_1_RIGHT;
import static android.car.occupantconnection.CarOccupantConnectionManager.CONNECTION_ERROR_NOT_READY;
import static android.car.occupantconnection.CarOccupantConnectionManager.CONNECTION_ERROR_PEER_APP_NOT_INSTALLED;
import static android.car.occupantconnection.CarOccupantConnectionManager.CONNECTION_ERROR_UNKNOWN;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.occupantconnection.IBackendReceiver;
import android.car.occupantconnection.IConnectionRequestCallback;
import android.car.occupantconnection.IPayloadCallback;
import android.car.occupantconnection.Payload;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;

import com.android.car.CarLocalServices;
import com.android.car.CarOccupantZoneService;
import com.android.car.internal.util.BinderKeyValueContainer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class CarOccupantConnectionServiceTest {

    private static final String PACKAGE_NAME = "my_package_name";
    private static final String FAKE_PACKAGE_NAME = "fake_package_name";
    private static final String RECEIVER_ENDPOINT_ID = "test_receiver_endpoint";
    private static final int USER_ID = 123;
    private static final int RECEIVER_USER_ID = 456;

    @Mock
    private Context mContext;
    @Mock
    private CarOccupantZoneService mOccupantZoneService;
    @Mock
    private CarRemoteDeviceService mRemoteDeviceService;
    @Mock
    private IPayloadCallback mPayloadCallback;
    @Mock
    private IBinder mPayloadCallbackBinder;
    @Mock
    private IConnectionRequestCallback mConnectionRequestCallback;
    @Mock
    private IBinder mConnectionRequestCallbackBinder;

    // TODO(b/272196149): make zone IDs constant.
    private final OccupantZoneInfo mReceiverZone =
            new OccupantZoneInfo(/* zoneId= */ 0, OCCUPANT_TYPE_DRIVER, SEAT_ROW_1_LEFT);
    private final OccupantZoneInfo mSenderZone =
            new OccupantZoneInfo(/* zoneId= */ 1, OCCUPANT_TYPE_FRONT_PASSENGER, SEAT_ROW_1_RIGHT);

    private final ArraySet<ClientId> mConnectingReceiverServices = new ArraySet<>();
    private final BinderKeyValueContainer<ClientId, IBackendReceiver>
            mConnectedReceiverServiceMap = new BinderKeyValueContainer<>();
    private final ArrayMap<ClientId, ServiceConnection> mReceiverServiceConnectionMap =
            new ArrayMap<>();
    private final BinderKeyValueContainer<ReceiverEndpointId, IPayloadCallback>
            mPreregisteredReceiverEndpointMap = new BinderKeyValueContainer<>();
    private final BinderKeyValueContainer<ReceiverEndpointId, IPayloadCallback>
            mRegisteredReceiverEndpointMap = new BinderKeyValueContainer<>();
    private final BinderKeyValueContainer<ConnectionId, IConnectionRequestCallback>
            mPendingConnectionRequestMap = new BinderKeyValueContainer<>();
    private final BinderKeyValueContainer<ConnectionId, IConnectionRequestCallback>
            mAcceptedConnectionRequestMap = new BinderKeyValueContainer<>();
    private final ArraySet<ConnectionRecord> mEstablishedConnections = new ArraySet<>();

    private CarOccupantConnectionService mService;

    @Before
    public void setUp() throws PackageManager.NameNotFoundException {
        // Stored as static: Other tests can leave things behind and fail this test in add call.
        // So just remove as safety guard.
        CarLocalServices.removeServiceForTest(CarOccupantZoneService.class);
        CarLocalServices.addService(CarOccupantZoneService.class, mOccupantZoneService);
        CarLocalServices.removeServiceForTest(CarRemoteDeviceService.class);
        CarLocalServices.addService(CarRemoteDeviceService.class, mRemoteDeviceService);

        mService = new CarOccupantConnectionService(mContext,
                mOccupantZoneService,
                mRemoteDeviceService,
                mConnectingReceiverServices,
                mConnectedReceiverServiceMap,
                mReceiverServiceConnectionMap,
                mPreregisteredReceiverEndpointMap,
                mRegisteredReceiverEndpointMap,
                mPendingConnectionRequestMap,
                mAcceptedConnectionRequestMap,
                mEstablishedConnections);
        mService.init();
        when(mPayloadCallback.asBinder()).thenReturn(mPayloadCallbackBinder);
        when(mConnectionRequestCallback.asBinder()).thenReturn(mConnectionRequestCallbackBinder);
        mockPackageName();
    }

    @After
    public void tearDown() {
        CarLocalServices.removeServiceForTest(CarRemoteDeviceService.class);
        CarLocalServices.removeServiceForTest(CarOccupantZoneService.class);
    }

    @Test
    public void testRegisterReceiverWithoutPermission_throwsException() {
        when(mContext.checkCallingOrSelfPermission(eq(Car.PERMISSION_MANAGE_OCCUPANT_CONNECTION)))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        assertThrows(SecurityException.class,
                () -> mService.registerReceiver(PACKAGE_NAME, RECEIVER_ENDPOINT_ID,
                        mPayloadCallback));
    }

    @Test
    public void testRegisterReceiverWithFakePackageName_throwsException() {
        assertThrows(SecurityException.class,
                () -> mService.registerReceiver(FAKE_PACKAGE_NAME, RECEIVER_ENDPOINT_ID,
                        mPayloadCallback));
    }

    @Test
    public void testRegisterReceiverWithDuplicateId_throwsException() {
        UserHandle receiverUserHandle = Binder.getCallingUserHandle();
        when(mOccupantZoneService.getOccupantZoneForUser(receiverUserHandle))
                .thenReturn(mReceiverZone);
        mService.registerReceiver(PACKAGE_NAME, RECEIVER_ENDPOINT_ID, mPayloadCallback);

        IPayloadCallback payloadCallback2 = mock(IPayloadCallback.class);
        IBinder payloadCallbackBinder2 = mock(IBinder.class);
        when(payloadCallback2.asBinder()).thenReturn(payloadCallbackBinder2);

        assertThrows(IllegalStateException.class,
                () -> mService.registerReceiver(PACKAGE_NAME, RECEIVER_ENDPOINT_ID,
                        payloadCallback2));
    }

    @Test
    public void testRegisterTwoReceiversWithoutReceiverServiceConnected() {
        UserHandle receiverUserHandle = Binder.getCallingUserHandle();
        when(mOccupantZoneService.getOccupantZoneForUser(receiverUserHandle))
                .thenReturn(mReceiverZone);
        mService.registerReceiver(PACKAGE_NAME, RECEIVER_ENDPOINT_ID, mPayloadCallback);

        // It should have a receiver service that is not connected yet, and a receiver endpoint
        // pending registration.
        assertThat(mConnectingReceiverServices.size()).isEqualTo(1);
        assertThat(mConnectedReceiverServiceMap.size()).isEqualTo(0);
        assertThat(mPreregisteredReceiverEndpointMap.size()).isEqualTo(1);
        assertThat(mRegisteredReceiverEndpointMap.size()).isEqualTo(0);

        ClientId clientId = mConnectingReceiverServices.valueAt(0);
        assertThat(clientId.packageName).isEqualTo(PACKAGE_NAME);

        ReceiverEndpointId receiverEndpointId =
                new ReceiverEndpointId(clientId, RECEIVER_ENDPOINT_ID);
        assertThat(mPreregisteredReceiverEndpointMap.get(receiverEndpointId))
                .isEqualTo(mPayloadCallback);

        // One more receiver endpoint pending registration.
        String endpointId2 = "ID2";
        IPayloadCallback payloadCallback2 = mock(IPayloadCallback.class);
        IBinder payloadCallbackBinder2 = mock(IBinder.class);
        when(payloadCallback2.asBinder()).thenReturn(payloadCallbackBinder2);
        mService.registerReceiver(PACKAGE_NAME, endpointId2, payloadCallback2);

        assertThat(mConnectingReceiverServices.size()).isEqualTo(1);
        assertThat(mConnectedReceiverServiceMap.size()).isEqualTo(0);
        assertThat(mPreregisteredReceiverEndpointMap.size()).isEqualTo(2);
        assertThat(mRegisteredReceiverEndpointMap.size()).isEqualTo(0);

        ReceiverEndpointId receiverEndpointId2 =
                new ReceiverEndpointId(clientId, endpointId2);
        assertThat(mPreregisteredReceiverEndpointMap.get(receiverEndpointId2))
                .isEqualTo(payloadCallback2);
    }

    @Test
    public void testRegisterReceiverThenConnectReceiverService() {
        ServiceConnection[] connection = new ServiceConnection[1];
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            Intent intent = (Intent) args[0];

            assertThat(intent.getAction()).isEqualTo(CAR_INTENT_ACTION_RECEIVER_SERVICE);

            connection[0] = (ServiceConnection) args[1];
            return null;
        }).when(mContext).bindServiceAsUser(any(Intent.class), any(ServiceConnection.class),
                anyInt(), any(UserHandle.class));

        // Since the receiver service is not connected, the receiver endpoint is
        // pending registration.
        UserHandle receiverUserHandle = Binder.getCallingUserHandle();
        when(mOccupantZoneService.getOccupantZoneForUser(receiverUserHandle))
                .thenReturn(mReceiverZone);
        mService.registerReceiver(PACKAGE_NAME, RECEIVER_ENDPOINT_ID, mPayloadCallback);

        // The receiver service is connected.
        ComponentName componentName = mock(ComponentName.class);
        IBinder binder = mock(IBinder.class);
        IBackendReceiver receiverService = mock(IBackendReceiver.class);
        when(binder.queryLocalInterface(anyString())).thenReturn(receiverService);
        when(receiverService.asBinder()).thenReturn(binder);
        connection[0].onServiceConnected(componentName, binder);

        // The receiver endpoint should be registered.
        assertThat(mConnectingReceiverServices.size()).isEqualTo(0);
        assertThat(mConnectedReceiverServiceMap.size()).isEqualTo(1);
        assertThat(mPreregisteredReceiverEndpointMap.size()).isEqualTo(0);
        assertThat(mRegisteredReceiverEndpointMap.size()).isEqualTo(1);

        ClientId clientId = mConnectedReceiverServiceMap.keyAt(0);
        assertThat(mConnectedReceiverServiceMap.get(clientId))
                .isEqualTo(receiverService);

        ReceiverEndpointId receiverEndpointId =
                new ReceiverEndpointId(clientId, RECEIVER_ENDPOINT_ID);
        assertThat(mRegisteredReceiverEndpointMap.get(receiverEndpointId))
                .isEqualTo(mPayloadCallback);
    }

    @Test
    public void testRegisterReceiverWithReceiverServiceConnected() {
        ServiceConnection[] connection = new ServiceConnection[1];
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            Intent intent = (Intent) args[0];

            assertThat(intent.getAction()).isEqualTo(CAR_INTENT_ACTION_RECEIVER_SERVICE);

            connection[0] = (ServiceConnection) args[1];
            return null;
        }).when(mContext).bindServiceAsUser(any(Intent.class), any(ServiceConnection.class),
                anyInt(), any(UserHandle.class));

        // Register the first receiver endpoint.
        UserHandle receiverUserHandle = Binder.getCallingUserHandle();
        when(mOccupantZoneService.getOccupantZoneForUser(receiverUserHandle))
                .thenReturn(mReceiverZone);
        mService.registerReceiver(PACKAGE_NAME, RECEIVER_ENDPOINT_ID, mPayloadCallback);

        // The receiver service is connected.
        ComponentName componentName = mock(ComponentName.class);
        IBinder binder = mock(IBinder.class);
        IBackendReceiver receiverService = mock(IBackendReceiver.class);
        when(binder.queryLocalInterface(anyString())).thenReturn(receiverService);
        when(receiverService.asBinder()).thenReturn(binder);
        connection[0].onServiceConnected(componentName, binder);

        // Register the second receiver endpoint.
        String endpointId2 = "ID2";
        IPayloadCallback payloadCallback2 = mock(IPayloadCallback.class);
        IBinder payloadCallbackBinder2 = mock(IBinder.class);
        when(payloadCallback2.asBinder()).thenReturn(payloadCallbackBinder2);
        mService.registerReceiver(PACKAGE_NAME, endpointId2, payloadCallback2);

        assertThat(mConnectingReceiverServices.size()).isEqualTo(0);
        assertThat(mConnectedReceiverServiceMap.size()).isEqualTo(1);
        assertThat(mPreregisteredReceiverEndpointMap.size()).isEqualTo(0);
        assertThat(mRegisteredReceiverEndpointMap.size()).isEqualTo(2);

        ClientId clientId = mConnectedReceiverServiceMap.keyAt(0);
        ReceiverEndpointId receiverEndpointId2 =
                new ReceiverEndpointId(clientId, endpointId2);
        assertThat(mRegisteredReceiverEndpointMap.get(receiverEndpointId2))
                .isEqualTo(payloadCallback2);

        // The receiver service is disconnected.
        connection[0].onServiceDisconnected(componentName);

        assertThat(mConnectingReceiverServices.size()).isEqualTo(0);
        assertThat(mConnectedReceiverServiceMap.size()).isEqualTo(0);
    }

    @Test
    public void testUnregisterReceiverWithoutPermission_throwsException() {
        when(mContext.checkCallingOrSelfPermission(eq(Car.PERMISSION_MANAGE_OCCUPANT_CONNECTION)))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        assertThrows(SecurityException.class,
                () -> mService.unregisterReceiver(PACKAGE_NAME, RECEIVER_ENDPOINT_ID));
    }

    @Test
    public void testUnregisterReceiverWithFakePackageName_throwsException() {
        assertThrows(SecurityException.class,
                () -> mService.unregisterReceiver(FAKE_PACKAGE_NAME, RECEIVER_ENDPOINT_ID));
    }

    @Test
    public void testUnregisterNonexistentReceiver_throwsException() {
        UserHandle receiverUserHandle = Binder.getCallingUserHandle();
        when(mOccupantZoneService.getOccupantZoneForUser(receiverUserHandle))
                .thenReturn(mReceiverZone);

        assertThrows(IllegalStateException.class,
                () -> mService.unregisterReceiver(PACKAGE_NAME, RECEIVER_ENDPOINT_ID));
    }

    @Test
    public void testUnregisterReceiverWithReceiverServiceBound() throws RemoteException {
        UserHandle receiverUserHandle = Binder.getCallingUserHandle();
        when(mOccupantZoneService.getOccupantZoneForUser(receiverUserHandle))
                .thenReturn(mReceiverZone);
        ClientId receiverClient = mService.getCallingClientId(PACKAGE_NAME);
        IBinder binder = mock(IBinder.class);
        IBackendReceiver receiverService = mock(IBackendReceiver.class);
        when(receiverService.asBinder()).thenReturn(binder);
        // The receiver service is connected already.
        mConnectedReceiverServiceMap.put(receiverClient, receiverService);
        ServiceConnection serviceConnection = mock(ServiceConnection.class);
        mReceiverServiceConnectionMap.put(receiverClient, serviceConnection);

        mService.registerReceiver(PACKAGE_NAME, RECEIVER_ENDPOINT_ID, mPayloadCallback);
        ReceiverEndpointId receiverEndpoint =
                new ReceiverEndpointId(receiverClient, RECEIVER_ENDPOINT_ID);

        assertThat(mRegisteredReceiverEndpointMap.get(receiverEndpoint))
                .isEqualTo(mPayloadCallback);
        assertThat(mPreregisteredReceiverEndpointMap.size()).isEqualTo(0);

        mService.unregisterReceiver(PACKAGE_NAME, RECEIVER_ENDPOINT_ID);

        // The receiver endpoint should be unregistered.
        assertThat(mRegisteredReceiverEndpointMap.size()).isEqualTo(0);
        assertThat(mPreregisteredReceiverEndpointMap.size()).isEqualTo(0);

        // The receiver service should be unbound since there is no receiver endpoint, no
        // established connection, and no pending connection request.
        // TODO(b/272196149): utilize assertWithMessage to incorporate the scenarios
        // described in the comments.
        assertThat(mConnectedReceiverServiceMap.size()).isEqualTo(0);
        assertThat(mReceiverServiceConnectionMap.size()).isEqualTo(0);

        verify(receiverService).unregisterReceiver(eq(RECEIVER_ENDPOINT_ID));
        verify(mContext).unbindService(serviceConnection);
    }

    @Test
    public void testUnregisterReceiverWithoutReceiverServiceBound() {
        UserHandle receiverUserHandle = Binder.getCallingUserHandle();
        when(mOccupantZoneService.getOccupantZoneForUser(receiverUserHandle))
                .thenReturn(mReceiverZone);
        mService.registerReceiver(PACKAGE_NAME, RECEIVER_ENDPOINT_ID, mPayloadCallback);

        assertThat(mPreregisteredReceiverEndpointMap.size()).isEqualTo(1);
        assertThat(mConnectingReceiverServices.size()).isEqualTo(1);

        ClientId receiverClient = mService.getCallingClientId(PACKAGE_NAME);
        ServiceConnection serviceConnection = mReceiverServiceConnectionMap.get(receiverClient);
        assertThat(serviceConnection).isNotNull();

        mService.unregisterReceiver(PACKAGE_NAME, RECEIVER_ENDPOINT_ID);

        // The receiver endpoint should be unregistered.
        assertThat(mPreregisteredReceiverEndpointMap.size()).isEqualTo(0);

        // The receiver service should be unbound since there is no receiver endpoint, no
        // established connection, and no pending connection request.
        assertThat(mConnectingReceiverServices.size()).isEqualTo(0);
        assertThat(mReceiverServiceConnectionMap.size()).isEqualTo(0);
        verify(mContext).unbindService(serviceConnection);
    }

    @Test
    public void testUnregisterReceiverWithOtherReceiversLeft() {
        UserHandle receiverUserHandle = Binder.getCallingUserHandle();
        when(mOccupantZoneService.getOccupantZoneForUser(receiverUserHandle))
                .thenReturn(mReceiverZone);
        // Register the first receiver endpoint.
        mService.registerReceiver(PACKAGE_NAME, RECEIVER_ENDPOINT_ID, mPayloadCallback);

        // Register the second receiver endpoint.
        String receiverEndpointId2 = "another_endpoint";
        IPayloadCallback payloadCallback2 = mock(IPayloadCallback.class);
        IBinder binder2 = mock(IBinder.class);
        when(payloadCallback2.asBinder()).thenReturn(binder2);
        mService.registerReceiver(PACKAGE_NAME, receiverEndpointId2, payloadCallback2);

        assertThat(mPreregisteredReceiverEndpointMap.size()).isEqualTo(2);

        // Unregister the first receiver endpoint.
        mService.unregisterReceiver(PACKAGE_NAME, RECEIVER_ENDPOINT_ID);

        // The first receiver endpoint should be unregistered.
        assertThat(mPreregisteredReceiverEndpointMap.size()).isEqualTo(1);
        ClientId receiverClient = mService.getCallingClientId(PACKAGE_NAME);
        ReceiverEndpointId receiverEndpoint2 =
                new ReceiverEndpointId(receiverClient, receiverEndpointId2);
        assertThat(mPreregisteredReceiverEndpointMap.get(receiverEndpoint2))
                .isEqualTo(payloadCallback2);

        // The receiver service should not be unbound since there is another receiver endpoint
        // registered.
        assertThat(mConnectingReceiverServices.size()).isEqualTo(1);
        assertThat(mReceiverServiceConnectionMap.size()).isEqualTo(1);
        verify(mContext, never()).unbindService(any());
    }

    @Test
    public void testRequestConnectionWithoutPermission_throwsException() {
        when(mContext.checkCallingOrSelfPermission(eq(Car.PERMISSION_MANAGE_OCCUPANT_CONNECTION)))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        assertThrows(SecurityException.class,
                () -> mService.requestConnection(PACKAGE_NAME, mReceiverZone,
                        mConnectionRequestCallback));
    }

    @Test
    public void testRequestConnectionWithFakePackageName_throwsException() {
        assertThrows(SecurityException.class,
                () -> mService.requestConnection(FAKE_PACKAGE_NAME, mReceiverZone,
                        mConnectionRequestCallback));
    }

    @Test
    public void testRequestConnectionWithoutReceiverServiceBoundBefore() throws RemoteException {
        // The receiver service is not bound yet before the sender requests a connection.
        UserHandle senderUserHandle = Binder.getCallingUserHandle();
        mockAppInstalled(mSenderZone, senderUserHandle);
        UserHandle receiverUserHandle = UserHandle.of(RECEIVER_USER_ID);
        mockAppInstalled(mReceiverZone, receiverUserHandle);

        mService.requestConnection(PACKAGE_NAME, mReceiverZone, mConnectionRequestCallback);

        // The sender endpoint should be saved in the cache.
        assertThat(mPendingConnectionRequestMap.size()).isEqualTo(1);
        ConnectionId connectionId = mPendingConnectionRequestMap.keyAt(0);
        assertThat(connectionId.senderClient.packageName).isEqualTo(PACKAGE_NAME);

        // It should start binding the receiver service automatically.
        assertThat(mReceiverServiceConnectionMap.size()).isEqualTo(1);
        assertThat(mConnectingReceiverServices.size()).isEqualTo(1);

        ClientId receiverClient = mReceiverServiceConnectionMap.keyAt(0);
        assertThat(receiverClient.packageName).isEqualTo(PACKAGE_NAME);
        assertThat(mConnectingReceiverServices.valueAt(0)).isEqualTo(receiverClient);

        // The receiver service is connected.
        ServiceConnection connection = mReceiverServiceConnectionMap.valueAt(0);
        ComponentName componentName = mock(ComponentName.class);
        IBinder binder = mock(IBinder.class);
        IBackendReceiver receiverService = mock(IBackendReceiver.class);
        when(binder.queryLocalInterface(anyString())).thenReturn(receiverService);
        when(receiverService.asBinder()).thenReturn(binder);
        connection.onServiceConnected(componentName, binder);

        assertThat(mConnectingReceiverServices.size()).isEqualTo(0);
        assertThat(mConnectedReceiverServiceMap.size()).isEqualTo(1);
        assertThat(mConnectedReceiverServiceMap.get(receiverClient)).isEqualTo(receiverService);

        assertThat(mPendingConnectionRequestMap.size()).isEqualTo(1);

        // The receiver service should be notified for the connection request.
        verify(receiverService).onConnectionInitiated(mSenderZone, /* senderVersion= */
                0, /* senderSigningInfo= */ null);
    }

    @Test
    public void testRequestConnectionWithReceiverServiceBoundAlready() throws RemoteException {
        // The receiver service is bound already before the sender requests a connection.
        UserHandle senderUserHandle = Binder.getCallingUserHandle();
        mockAppInstalled(mSenderZone, senderUserHandle);
        UserHandle receiverUserHandle = UserHandle.of(RECEIVER_USER_ID);
        mockAppInstalled(mReceiverZone, receiverUserHandle);
        ClientId receiverClient = new ClientId(mReceiverZone, RECEIVER_USER_ID, PACKAGE_NAME);
        IBinder binder = mock(IBinder.class);
        IBackendReceiver receiverService = mock(IBackendReceiver.class);
        when(receiverService.asBinder()).thenReturn(binder);

        // Pretend that the receiver service is bound already.
        mConnectedReceiverServiceMap.put(receiverClient, receiverService);
        ServiceConnection connection = mock(ServiceConnection.class);
        mReceiverServiceConnectionMap.put(receiverClient, connection);

        mService.requestConnection(PACKAGE_NAME, mReceiverZone, mConnectionRequestCallback);

        assertThat(mPendingConnectionRequestMap.size()).isEqualTo(1);
        ConnectionId connectionId = mPendingConnectionRequestMap.keyAt(0);
        assertThat(connectionId.senderClient.packageName).isEqualTo(PACKAGE_NAME);

        // The receiver service should be notified for the connection request.
        verify(receiverService).onConnectionInitiated(mSenderZone, /* senderVersion= */
                0, /* senderSigningInfo= */ null);
    }

    @Test
    public void testRequestConnectionAlreadyConnected() {
        // The sender client is already connected to the receiver client before requesting a
        // connection.
        UserHandle senderUserHandle = Binder.getCallingUserHandle();
        mockAppInstalled(mSenderZone, senderUserHandle);
        UserHandle receiverUserHandle = UserHandle.of(RECEIVER_USER_ID);
        mockAppInstalled(mReceiverZone, receiverUserHandle);
        ClientId senderClient =
                new ClientId(mSenderZone, senderUserHandle.getIdentifier(), PACKAGE_NAME);
        ClientId receiverClient = new ClientId(mReceiverZone, RECEIVER_USER_ID, PACKAGE_NAME);
        ConnectionId connectionId = new ConnectionId(senderClient, receiverClient);

        IConnectionRequestCallback callback = mock(IConnectionRequestCallback.class);
        IBinder callbackBinder = mock(IBinder.class);
        when(callback.asBinder()).thenReturn(callbackBinder);
        mAcceptedConnectionRequestMap.put(connectionId, callback);

        assertThrows(IllegalStateException.class,
                () -> mService.requestConnection(PACKAGE_NAME, mReceiverZone,
                        mConnectionRequestCallback));
    }

    @Test
    public void testRequestConnectionWithPendingConnection() {
        // The sender client is already connected to the receiver client before requesting a
        // connection.
        UserHandle senderUserHandle = Binder.getCallingUserHandle();
        mockAppInstalled(mSenderZone, senderUserHandle);
        UserHandle receiverUserHandle = UserHandle.of(RECEIVER_USER_ID);
        mockAppInstalled(mReceiverZone, receiverUserHandle);
        ClientId senderClient =
                new ClientId(mSenderZone, senderUserHandle.getIdentifier(), PACKAGE_NAME);
        ClientId receiverClient = new ClientId(mReceiverZone, RECEIVER_USER_ID, PACKAGE_NAME);
        ConnectionId connectionId = new ConnectionId(senderClient, receiverClient);

        IConnectionRequestCallback callback = mock(IConnectionRequestCallback.class);
        IBinder callbackBinder = mock(IBinder.class);
        when(callback.asBinder()).thenReturn(callbackBinder);
        mPendingConnectionRequestMap.put(connectionId, callback);

        assertThrows(IllegalStateException.class,
                () -> mService.requestConnection(PACKAGE_NAME, mReceiverZone,
                        mConnectionRequestCallback));
    }

    @Test
    public void testRequestConnectionReceiverZoneNotReady() throws RemoteException {
        UserHandle senderUserHandle = Binder.getCallingUserHandle();
        mockAppInstalled(mSenderZone, senderUserHandle);
        when(mRemoteDeviceService.isConnectionReady(mReceiverZone)).thenReturn(false);

        mService.requestConnection(PACKAGE_NAME, mReceiverZone, mConnectionRequestCallback);

        verify(mConnectionRequestCallback).onFailed(mReceiverZone, CONNECTION_ERROR_NOT_READY);
    }

    @Test
    public void testRequestConnectionReceiverAppNotInstalled() throws RemoteException {
        UserHandle senderUserHandle = Binder.getCallingUserHandle();
        mockAppInstalled(mSenderZone, senderUserHandle);
        when(mRemoteDeviceService.isConnectionReady(mReceiverZone)).thenReturn(true);

        mService.requestConnection(PACKAGE_NAME, mReceiverZone, mConnectionRequestCallback);

        verify(mConnectionRequestCallback)
                .onFailed(mReceiverZone, CONNECTION_ERROR_PEER_APP_NOT_INSTALLED);
    }

    @Test
    public void testCancelConnectionWithoutPermission_throwsException() {
        when(mContext.checkCallingOrSelfPermission(eq(Car.PERMISSION_MANAGE_OCCUPANT_CONNECTION)))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        assertThrows(SecurityException.class,
                () -> mService.cancelConnection(PACKAGE_NAME, any(OccupantZoneInfo.class)));
    }

    @Test
    public void testCancelConnectionWithFakePackageName_throwsException() {
        assertThrows(SecurityException.class,
                () -> mService.cancelConnection(FAKE_PACKAGE_NAME, any(OccupantZoneInfo.class)));
    }

    @Test
    public void testCancelConnection() throws RemoteException {
        when(mOccupantZoneService.getUserForOccupant(mReceiverZone.zoneId))
                .thenReturn(RECEIVER_USER_ID);
        ClientId receiverClient = new ClientId(mReceiverZone, RECEIVER_USER_ID, PACKAGE_NAME);

        UserHandle senderUserHandle = Binder.getCallingUserHandle();
        when(mOccupantZoneService.getOccupantZoneForUser(senderUserHandle))
                .thenReturn(mSenderZone);
        ClientId senderClient = mService.getCallingClientId(PACKAGE_NAME);

        ConnectionId connectionId = new ConnectionId(senderClient, receiverClient);
        mPendingConnectionRequestMap.put(connectionId, mConnectionRequestCallback);

        IConnectionRequestCallback connectionRequestCallback2 =
                mock(IConnectionRequestCallback.class);
        IBinder connectionRequestCallbackBinder2 = mock(IBinder.class);
        when(connectionRequestCallback2.asBinder()).thenReturn(connectionRequestCallbackBinder2);
        mPendingConnectionRequestMap.put(connectionId, connectionRequestCallback2);

        ServiceConnection serviceConnection = mock(ServiceConnection.class);
        mReceiverServiceConnectionMap.put(receiverClient, serviceConnection);
        IBinder binder = mock(IBinder.class);
        IBackendReceiver receiverService = mock(IBackendReceiver.class);
        when(receiverService.asBinder()).thenReturn(binder);
        mConnectedReceiverServiceMap.put(receiverClient, receiverService);

        mService.cancelConnection(PACKAGE_NAME, mReceiverZone);

        // All pending connection requests should be canceled.
        assertThat(mPendingConnectionRequestMap.size()).isEqualTo(0);

        // The receiver service should be notified of the cancellation, and should be unbound.
        verify(receiverService).onConnectionCanceled(mSenderZone);
        verify(mContext).unbindService(serviceConnection);
    }

    @Test
    public void testCancelConnectionWithoutPendingConnectionRequest_throwsException() {
        UserHandle senderUserHandle = Binder.getCallingUserHandle();
        when(mOccupantZoneService.getOccupantZoneForUser(senderUserHandle))
                .thenReturn(mSenderZone);

        // There is no pending connection request to cancel, so it should throw an
        // IllegalStateException.
        assertThrows(IllegalStateException.class,
                () -> mService.cancelConnection(PACKAGE_NAME, mReceiverZone));
    }

    @Test
    public void testCancelConnectionWithEstablishedConnection_throwsException() {
        UserHandle senderUserHandle = Binder.getCallingUserHandle();
        when(mOccupantZoneService.getOccupantZoneForUser(senderUserHandle))
                .thenReturn(mSenderZone);
        ConnectionRecord connectionRecord =
                new ConnectionRecord(PACKAGE_NAME, mSenderZone.zoneId, mReceiverZone.zoneId);
        mEstablishedConnections.add(connectionRecord);

        // The connection is established already, so canceling it should throw an
        // IllegalStateException.
        assertThrows(IllegalStateException.class,
                () -> mService.cancelConnection(PACKAGE_NAME, mReceiverZone));
    }

    @Test
    public void testReceiverServiceDisconnected() throws RemoteException {
        ServiceConnection[] connection = new ServiceConnection[1];
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            connection[0] = (ServiceConnection) args[1];
            return null;
        }).when(mContext).bindServiceAsUser(any(Intent.class), any(ServiceConnection.class),
                anyInt(), any(UserHandle.class));

        UserHandle receiverUserHandle = Binder.getCallingUserHandle();
        mockAppInstalled(mReceiverZone, receiverUserHandle);
        mService.registerReceiver(PACKAGE_NAME, RECEIVER_ENDPOINT_ID, mPayloadCallback);

        ClientId receiverClient = mService.getCallingClientId(PACKAGE_NAME);
        ReceiverEndpointId receiverEndpoint =
                new ReceiverEndpointId(receiverClient, RECEIVER_ENDPOINT_ID);
        ClientId senderClient = new ClientId(mSenderZone, USER_ID, PACKAGE_NAME);
        ConnectionId connectionId = new ConnectionId(senderClient, receiverClient);
        ConnectionRecord connectionRecord =
                new ConnectionRecord(PACKAGE_NAME, mSenderZone.zoneId, mReceiverZone.zoneId);

        mConnectingReceiverServices.add(receiverClient);

        IBinder binder = mock(IBinder.class);
        IBackendReceiver receiverService = mock(IBackendReceiver.class);
        when(receiverService.asBinder()).thenReturn(binder);
        mConnectedReceiverServiceMap.put(receiverClient, receiverService);

        mReceiverServiceConnectionMap.put(receiverClient, connection[0]);

        mPreregisteredReceiverEndpointMap.put(receiverEndpoint, mPayloadCallback);

        mRegisteredReceiverEndpointMap.put(receiverEndpoint, mPayloadCallback);
        mPendingConnectionRequestMap.put(connectionId, mConnectionRequestCallback);

        IConnectionRequestCallback callback2 = mock(IConnectionRequestCallback.class);
        Binder binder2 = mock(Binder.class);
        when(callback2.asBinder()).thenReturn(binder2);
        mAcceptedConnectionRequestMap.put(connectionId, callback2);

        mEstablishedConnections.add(connectionRecord);

        connection[0].onServiceDisconnected(mock(ComponentName.class));

        assertThat(mConnectingReceiverServices.isEmpty()).isTrue();
        assertThat(mConnectedReceiverServiceMap.size()).isEqualTo(0);
        assertThat(mReceiverServiceConnectionMap.isEmpty()).isTrue();
        assertThat(mPreregisteredReceiverEndpointMap.size()).isEqualTo(0);
        assertThat(mRegisteredReceiverEndpointMap.size()).isEqualTo(0);
        assertThat(mPendingConnectionRequestMap.size()).isEqualTo(0);
        assertThat(mAcceptedConnectionRequestMap.size()).isEqualTo(0);
        assertThat(mEstablishedConnections.isEmpty()).isTrue();

        verify(mConnectionRequestCallback).onFailed(receiverClient.occupantZone,
                CONNECTION_ERROR_UNKNOWN);
        verify(callback2).onDisconnected(receiverClient.occupantZone);
    }

    @Test
    public void testSendPayloadWithoutPermission_throwsException() {
        when(mContext.checkCallingOrSelfPermission(eq(Car.PERMISSION_MANAGE_OCCUPANT_CONNECTION)))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        assertThrows(SecurityException.class,
                () -> mService.sendPayload(PACKAGE_NAME, mReceiverZone, any(Payload.class)));
    }

    @Test
    public void testSendPayloadWithFakePackageName_throwsException() {
        assertThrows(SecurityException.class,
                () -> mService.sendPayload(FAKE_PACKAGE_NAME, mReceiverZone, any(Payload.class)));
    }

    @Test
    public void testSendPayloadWithoutConnection_throwsException() {
        UserHandle senderUserHandle = Binder.getCallingUserHandle();
        when(mOccupantZoneService.getOccupantZoneForUser(senderUserHandle)).thenReturn(mSenderZone);

        assertThrows(IllegalStateException.class,
                () -> mService.sendPayload(PACKAGE_NAME, mReceiverZone, any(Payload.class)));
    }

    @Test
    public void testSendPayloadSucceed() throws RemoteException {
        UserHandle senderUserHandle = Binder.getCallingUserHandle();
        when(mOccupantZoneService.getOccupantZoneForUser(senderUserHandle)).thenReturn(mSenderZone);
        ConnectionRecord connectionRecord =
                new ConnectionRecord(PACKAGE_NAME, mSenderZone.zoneId, mReceiverZone.zoneId);

        // It is connected.
        mEstablishedConnections.add(connectionRecord);

        when(mOccupantZoneService.getUserForOccupant(mReceiverZone.zoneId))
                .thenReturn(RECEIVER_USER_ID);
        ClientId receiverClient = new ClientId(mReceiverZone, RECEIVER_USER_ID, PACKAGE_NAME);
        IBinder binder = mock(IBinder.class);
        IBackendReceiver receiverService = mock(IBackendReceiver.class);
        when(receiverService.asBinder()).thenReturn(binder);

        // And the receiver service is bound already.
        mConnectedReceiverServiceMap.put(receiverClient, receiverService);

        Payload payload = mock(Payload.class);
        mService.sendPayload(PACKAGE_NAME, mReceiverZone, payload);

        // The receiver service should be notified for the payload.
        verify(receiverService).onPayloadReceived(mSenderZone, payload);
    }

    @Test
    public void testIsConnectedWithoutPermission_throwsException() {
        when(mContext.checkCallingOrSelfPermission(eq(Car.PERMISSION_MANAGE_OCCUPANT_CONNECTION)))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        assertThrows(SecurityException.class,
                () -> mService.isConnected(PACKAGE_NAME, any(OccupantZoneInfo.class)));
    }

    @Test
    public void testIsConnectedWithFakePackageName_throwsException() {
        assertThrows(SecurityException.class,
                () -> mService.isConnected(FAKE_PACKAGE_NAME, any(OccupantZoneInfo.class)));
    }

    @Test
    public void testIsConnected() {
        UserHandle senderUserHandle = Binder.getCallingUserHandle();
        when(mOccupantZoneService.getOccupantZoneForUser(senderUserHandle)).thenReturn(mSenderZone);

        assertThat(mService.isConnected(PACKAGE_NAME, mReceiverZone)).isFalse();

        ConnectionRecord connectionRecord =
                new ConnectionRecord(PACKAGE_NAME, mSenderZone.zoneId, mReceiverZone.zoneId);
        mEstablishedConnections.add(connectionRecord);

        assertThat(mService.isConnected(PACKAGE_NAME, mReceiverZone)).isTrue();
    }

    @Test
    public void testDisconnectWithoutPermission_throwsException() {
        when(mContext.checkCallingOrSelfPermission(eq(Car.PERMISSION_MANAGE_OCCUPANT_CONNECTION)))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        assertThrows(SecurityException.class,
                () -> mService.disconnect(PACKAGE_NAME, mReceiverZone));
    }

    @Test
    public void testDisconnectWithFakePackageName_throwsException() {
        assertThrows(SecurityException.class,
                () -> mService.disconnect(FAKE_PACKAGE_NAME, mReceiverZone));
    }

    @Test
    public void testDisconnect() throws RemoteException {
        UserHandle senderUserHandle = Binder.getCallingUserHandle();
        when(mOccupantZoneService.getOccupantZoneForUser(senderUserHandle)).thenReturn(mSenderZone);
        ConnectionRecord connectionRecord =
                new ConnectionRecord(PACKAGE_NAME, mSenderZone.zoneId, mReceiverZone.zoneId);
        mEstablishedConnections.add(connectionRecord);

        ClientId senderClient = mService.getCallingClientId(PACKAGE_NAME);
        when(mOccupantZoneService.getUserForOccupant(mReceiverZone.zoneId))
                .thenReturn(RECEIVER_USER_ID);
        ClientId receiverClient = new ClientId(mReceiverZone, RECEIVER_USER_ID, PACKAGE_NAME);
        ConnectionId connectionId = new ConnectionId(senderClient, receiverClient);
        mAcceptedConnectionRequestMap.put(connectionId, mConnectionRequestCallback);

        IBinder binder = mock(IBinder.class);
        IBackendReceiver receiverService = mock(IBackendReceiver.class);
        when(receiverService.asBinder()).thenReturn(binder);
        mConnectedReceiverServiceMap.put(receiverClient, receiverService);
        ServiceConnection serviceConnection = mock(ServiceConnection.class);
        mReceiverServiceConnectionMap.put(receiverClient, serviceConnection);

        mService.disconnect(PACKAGE_NAME, mReceiverZone);

        assertThat(mEstablishedConnections.size()).isEqualTo(0);
        assertThat(mAcceptedConnectionRequestMap.size()).isEqualTo(0);
        assertThat(mConnectedReceiverServiceMap.size()).isEqualTo(0);
        assertThat(mReceiverServiceConnectionMap.size()).isEqualTo(0);
        verify(receiverService).onDisconnected(mSenderZone);
        verify(mContext).unbindService(serviceConnection);
    }

    @Test
    public void testDisconnectWithoutEstablishedConnection_throwsException() {
        UserHandle senderUserHandle = Binder.getCallingUserHandle();
        when(mOccupantZoneService.getOccupantZoneForUser(senderUserHandle)).thenReturn(mSenderZone);

        // The connection is not established yet, so disconnecting should throw an
        // IllegalStateException.
        assertThrows(IllegalStateException.class,
                () -> mService.disconnect(PACKAGE_NAME, mReceiverZone));
    }

    @Test
    public void testConnectedSenderDied() throws RemoteException {
        UserHandle senderUserHandle = Binder.getCallingUserHandle();
        when(mOccupantZoneService.getOccupantZoneForUser(senderUserHandle)).thenReturn(mSenderZone);
        ConnectionRecord connectionRecord =
                new ConnectionRecord(PACKAGE_NAME, mSenderZone.zoneId, mReceiverZone.zoneId);
        mEstablishedConnections.add(connectionRecord);

        ClientId senderClient = mService.getCallingClientId(PACKAGE_NAME);
        int receiverUserId = 456;
        when(mOccupantZoneService.getUserForOccupant(mReceiverZone.zoneId))
                .thenReturn(receiverUserId);
        ClientId receiverClient = new ClientId(mReceiverZone, receiverUserId, PACKAGE_NAME);
        ConnectionId connectionId = new ConnectionId(senderClient, receiverClient);
        TestConnectionRequestCallback connectionRequestCallback =
                new TestConnectionRequestCallback();
        mAcceptedConnectionRequestMap.put(connectionId, connectionRequestCallback);

        IBinder binder = mock(IBinder.class);
        IBackendReceiver receiverService = mock(IBackendReceiver.class);
        when(receiverService.asBinder()).thenReturn(binder);
        mConnectedReceiverServiceMap.put(receiverClient, receiverService);
        ServiceConnection serviceConnection = mock(ServiceConnection.class);
        mReceiverServiceConnectionMap.put(receiverClient, serviceConnection);

        // The sender dies.
        connectionRequestCallback.die();

        assertThat(mEstablishedConnections.size()).isEqualTo(0);
        assertThat(mAcceptedConnectionRequestMap.size()).isEqualTo(0);
        assertThat(mConnectedReceiverServiceMap.size()).isEqualTo(0);
        assertThat(mReceiverServiceConnectionMap.size()).isEqualTo(0);
        verify(receiverService).onDisconnected(mSenderZone);
        verify(mContext).unbindService(serviceConnection);
    }

    @Test
    public void testConnectingSenderDiedWithoutReceiverServiceBound() throws RemoteException {
        UserHandle senderUserHandle = Binder.getCallingUserHandle();
        when(mOccupantZoneService.getOccupantZoneForUser(senderUserHandle)).thenReturn(mSenderZone);

        ClientId senderClient = mService.getCallingClientId(PACKAGE_NAME);
        int receiverUserId = 456;
        when(mOccupantZoneService.getUserForOccupant(mReceiverZone.zoneId))
                .thenReturn(receiverUserId);
        ClientId receiverClient = new ClientId(mReceiverZone, receiverUserId, PACKAGE_NAME);
        ConnectionId connectionId = new ConnectionId(senderClient, receiverClient);
        TestConnectionRequestCallback connectionRequestCallback =
                new TestConnectionRequestCallback();
        mPendingConnectionRequestMap.put(connectionId, connectionRequestCallback);

        mConnectingReceiverServices.add(receiverClient);
        ServiceConnection serviceConnection = mock(ServiceConnection.class);
        mReceiverServiceConnectionMap.put(receiverClient, serviceConnection);

        // The sender dies.
        connectionRequestCallback.die();

        assertThat(mPendingConnectionRequestMap.size()).isEqualTo(0);
        assertThat(mConnectingReceiverServices.size()).isEqualTo(0);
        assertThat(mReceiverServiceConnectionMap.size()).isEqualTo(0);
        verify(mContext).unbindService(serviceConnection);
    }

    @Test
    public void testConnectingSenderDiedWithReceiverServiceBound() throws RemoteException {
        UserHandle senderUserHandle = Binder.getCallingUserHandle();
        when(mOccupantZoneService.getOccupantZoneForUser(senderUserHandle)).thenReturn(mSenderZone);

        ClientId senderClient = mService.getCallingClientId(PACKAGE_NAME);
        int receiverUserId = 456;
        when(mOccupantZoneService.getUserForOccupant(mReceiverZone.zoneId))
                .thenReturn(receiverUserId);
        ClientId receiverClient = new ClientId(mReceiverZone, receiverUserId, PACKAGE_NAME);
        ConnectionId connectionId = new ConnectionId(senderClient, receiverClient);
        TestConnectionRequestCallback connectionRequestCallback =
                new TestConnectionRequestCallback();
        mPendingConnectionRequestMap.put(connectionId, connectionRequestCallback);

        IBinder binder = mock(IBinder.class);
        IBackendReceiver receiverService = mock(IBackendReceiver.class);
        when(receiverService.asBinder()).thenReturn(binder);
        mConnectedReceiverServiceMap.put(receiverClient, receiverService);
        ServiceConnection serviceConnection = mock(ServiceConnection.class);
        mReceiverServiceConnectionMap.put(receiverClient, serviceConnection);

        // The sender dies.
        connectionRequestCallback.die();

        assertThat(mPendingConnectionRequestMap.size()).isEqualTo(0);
        assertThat(mConnectedReceiverServiceMap.size()).isEqualTo(0);
        assertThat(mReceiverServiceConnectionMap.size()).isEqualTo(0);
        verify(receiverService).onConnectionCanceled(mSenderZone);
        verify(mContext).unbindService(serviceConnection);
    }

    private void mockPackageName() throws PackageManager.NameNotFoundException {
        PackageManager pm = mock(PackageManager.class);
        when(mContext.getPackageManager()).thenReturn(pm);
        when(pm.getPackageUidAsUser(eq(PACKAGE_NAME), anyInt())).thenReturn(Binder.getCallingUid());
    }

    private void mockAppInstalled(OccupantZoneInfo occupantZone, UserHandle userHandle) {
        when(mRemoteDeviceService.isConnectionReady(occupantZone)).thenReturn(true);
        PackageInfo packageInfo = mock(PackageInfo.class);
        when(mRemoteDeviceService.getEndpointPackageInfo(occupantZone.zoneId, PACKAGE_NAME))
                .thenReturn(packageInfo);
        when(mOccupantZoneService.getOccupantZoneForUser(userHandle))
                .thenReturn(occupantZone);
        when(mOccupantZoneService.getUserForOccupant(occupantZone.zoneId))
                .thenReturn(userHandle.getIdentifier());
        when(mRemoteDeviceService.getPackageInfoAsUser(PACKAGE_NAME, userHandle.getIdentifier()))
                .thenReturn(packageInfo);
    }

    private static final class TestConnectionRequestCallback extends android.os.Binder implements
            IConnectionRequestCallback {

        private DeathRecipient mRecipient;

        @Override
        public void linkToDeath(DeathRecipient recipient, int flags) {
            // In any situation, a single binder object should only have at most one death
            // recipient.
            assertThat(mRecipient).isNull();

            mRecipient = recipient;
        }

        @Override
        public boolean unlinkToDeath(DeathRecipient recipient, int flags) {
            assertThat(mRecipient).isSameInstanceAs(recipient);
            mRecipient = null;
            return true;
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        public void onConnected(OccupantZoneInfo receiverZone) {
        }

        @Override
        public void onFailed(OccupantZoneInfo receiverZone, int connectionError) {
        }

        @Override
        public void onDisconnected(OccupantZoneInfo receiverZone) {
        }

        private void die() {
            if (mRecipient != null) {
                mRecipient.binderDied(this);
            }
            mRecipient = null;
        }
    }
}
