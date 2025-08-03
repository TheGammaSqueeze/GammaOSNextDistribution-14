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

package android.car.occupantconnection;

import static android.car.Car.CAR_INTENT_ACTION_RECEIVER_SERVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.content.Intent;
import android.content.pm.SigningInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Pair;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public final class AbstractReceiverServiceUnitTest {

    private static final String RECEIVER_ENDPOINT_ID = "test_receiver_endpoint";

    private final TestReceiverService mService = new TestReceiverService();
    private final IBackendReceiver mBackendReceiver =
            IBackendReceiver.Stub.asInterface(
                    mService.onBind(new Intent(CAR_INTENT_ACTION_RECEIVER_SERVICE)));

    @Mock
    private IPayloadCallback mPayloadCallback;
    @Mock
    private IBinder mPayloadCallbackBinder;
    @Mock
    OccupantZoneInfo mSenderZone;
    @Mock
    IBackendConnectionResponder mResponder;
    @Mock
    Payload mPayload;

    @Before
    public void setUp() {
        when(mPayloadCallback.asBinder()).thenReturn(mPayloadCallbackBinder);
    }

    @Test
    public void testRegisterAndUnregisterReceiver() throws RemoteException {
        // Register the first endpoint.
        mBackendReceiver.registerReceiver(RECEIVER_ENDPOINT_ID, mPayloadCallback);

        // TODO(b/272196149): replace assertions with Truth's expect.
        assertThat(mService.onReceiverRegisteredInvokedRecords.contains(RECEIVER_ENDPOINT_ID))
                .isTrue();

        Set<String> receiverEndpoints = mService.getAllReceiverEndpoints();
        assertThat(receiverEndpoints.size()).isEqualTo(1);
        assertThat(receiverEndpoints.contains(RECEIVER_ENDPOINT_ID)).isTrue();

        // Register the second endpoint.
        String receiverEndpointId2 = "ID2";
        IPayloadCallback payloadCallback2 = mock(IPayloadCallback.class);
        IBinder payloadCallbackBinder2 = mock(IBinder.class);
        when(payloadCallback2.asBinder()).thenReturn(payloadCallbackBinder2);
        mBackendReceiver.registerReceiver(receiverEndpointId2, payloadCallback2);

        assertThat(mService.onReceiverRegisteredInvokedRecords.contains(RECEIVER_ENDPOINT_ID))
                .isTrue();
        receiverEndpoints = mService.getAllReceiverEndpoints();
        assertThat(receiverEndpoints.size()).isEqualTo(2);
        assertThat(receiverEndpoints.contains(RECEIVER_ENDPOINT_ID)).isTrue();
        assertThat(receiverEndpoints.contains(receiverEndpointId2)).isTrue();

        // Unregister the first endpoint.
        mBackendReceiver.unregisterReceiver(RECEIVER_ENDPOINT_ID);

        receiverEndpoints = mService.getAllReceiverEndpoints();
        assertThat(receiverEndpoints.size()).isEqualTo(1);
        assertThat(receiverEndpoints.contains(receiverEndpointId2)).isTrue();

        // Unregister the second endpoint.
        mBackendReceiver.unregisterReceiver(receiverEndpointId2);
        assertThat(receiverEndpoints.size()).isEqualTo(0);
    }

    @Test
    public void testOnPayloadReceived() throws RemoteException {
        mBackendReceiver.onPayloadReceived(mSenderZone, mPayload);

        assertThat(mService.onPayloadReceivedInvokedRecords
                .contains(new Pair(mSenderZone, mPayload))).isTrue();
    }

    @Test
    public void testOnConnectionInitiated() throws RemoteException {
        mBackendReceiver.onConnectionInitiated(mSenderZone, /* senderVersion= */ 0,
                mock(SigningInfo.class));

        assertThat(mService.onConnectionInitiatedInvokedRecords.contains(mSenderZone)).isTrue();
    }

    @Test
    public void testOnConnected() throws RemoteException {
        mBackendReceiver.onConnected(mSenderZone);

        assertThat(mService.onConnectedInvokedRecords.contains(mSenderZone)).isTrue();
    }

    @Test
    public void testOnConnectionCanceled() throws RemoteException {
        mBackendReceiver.onConnectionCanceled(mSenderZone);

        assertThat(mService.onConnectionCanceledInvokedRecords.contains(mSenderZone)).isTrue();
    }

    @Test
    public void testDisconnected() throws RemoteException {
        mBackendReceiver.onDisconnected(mSenderZone);

        assertThat(mService.onDisconnectedInvokedRecords.contains(mSenderZone)).isTrue();
    }

    @Test
    public void testOnBind()  {
        assertThat(mService.onBind(new Intent(CAR_INTENT_ACTION_RECEIVER_SERVICE)))
                .isEqualTo(mBackendReceiver.asBinder());

        assertThat(mService.onBind(new Intent("my_intent")))
                .isEqualTo(mService.localBinder);
    }

    @Test
    public void testAcceptConnection() throws RemoteException {
        mBackendReceiver.registerBackendConnectionResponder(mResponder);
        mService.acceptConnection(mSenderZone);

        verify(mResponder).acceptConnection(mSenderZone);
    }

    @Test
    public void testRejectConnection() throws RemoteException {
        mBackendReceiver.registerBackendConnectionResponder(mResponder);
        int rejectionReason = 123;
        mService.rejectConnection(mSenderZone, rejectionReason);

        verify(mResponder).rejectConnection(mSenderZone, rejectionReason);
    }

    @Test
    public void testForwardPayload() throws RemoteException {
        // The receiver endpoint is not registered yet, so it should fail to forward the mPayload.
        assertThat(mService.forwardPayload(mSenderZone, RECEIVER_ENDPOINT_ID, mPayload)).isFalse();

        // Register the receiver endpoint.
        mBackendReceiver.registerReceiver(RECEIVER_ENDPOINT_ID, mPayloadCallback);

        assertThat(mService.forwardPayload(mSenderZone, RECEIVER_ENDPOINT_ID, mPayload)).isTrue();
        verify(mPayloadCallback).onPayloadReceived(mSenderZone, RECEIVER_ENDPOINT_ID, mPayload);
    }
}
