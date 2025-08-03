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

package com.android.server.uwb.discovery.ble;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.ScanResult;
import android.content.AttributionSource;
import android.content.Context;
import android.uwb.UwbTestUtils;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.discovery.Transport.DataReceiver;
import com.android.server.uwb.discovery.TransportClientProvider.TransportClientCallback;
import com.android.server.uwb.discovery.TransportProvider;
import com.android.server.uwb.discovery.TransportProvider.TerminationReason;
import com.android.server.uwb.discovery.info.AdminErrorMessage;
import com.android.server.uwb.discovery.info.AdminErrorMessage.ErrorType;
import com.android.server.uwb.discovery.info.FiraConnectorCapabilities;
import com.android.server.uwb.discovery.info.FiraConnectorDataPacket;
import com.android.server.uwb.discovery.info.FiraConnectorMessage;
import com.android.server.uwb.discovery.info.FiraConnectorMessage.InstructionCode;
import com.android.server.uwb.discovery.info.FiraConnectorMessage.MessageType;
import com.android.server.uwb.discovery.info.SecureComponentInfo;
import com.android.server.uwb.discovery.info.TransportClientInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.concurrent.Executor;

/** Unit test for {@link GattTransportClientProvider} */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class GattTransportClientProviderTest {
    private static final String TAG = GattTransportClientProviderTest.class.getSimpleName();

    private static final Executor EXECUTOR = UwbTestUtils.getExecutor();

    private static final int SECID = 2;
    private static final int SECID2 = 3;
    private static final byte[] MESSAGE_PAYLOAD1 = new byte[] {(byte) 0xF4, 0x00, 0x40};
    private static final FiraConnectorMessage MESSAGE =
            new FiraConnectorMessage(
                    MessageType.COMMAND, InstructionCode.DATA_EXCHANGE, MESSAGE_PAYLOAD1);
    private static final FiraConnectorDataPacket DATA_PACKET =
            new FiraConnectorDataPacket(/*lastChainingPacket=*/ true, SECID, MESSAGE.toBytes());
    private static final byte[] DATA_PACKET_BYTES = DATA_PACKET.toBytes();
    private static final int OPTIMIZED_DATA_PACKET_SIZE = 21;
    private static final FiraConnectorCapabilities CAPABILITIES =
            new FiraConnectorCapabilities.Builder()
                    .setOptimizedDataPacketSize(OPTIMIZED_DATA_PACKET_SIZE)
                    .setMaxMessageBufferSize(265)
                    .addSecureComponentInfo(
                            new SecureComponentInfo(
                                    /*static_indication=*/ true,
                                    SECID,
                                    SecureComponentInfo.SecureComponentType.ESE_NONREMOVABLE,
                                    SecureComponentInfo.SecureComponentProtocolType
                                            .FIRA_OOB_ADMINISTRATIVE_PROTOCOL))
                    .build();
    private static final BluetoothGattCharacteristic IN_CHARACTERSTIC =
            new BluetoothGattCharacteristic(
                    UuidConstants.CP_IN_CONTROL_POINT_UUID.getUuid(),
                    BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE);
    private static final BluetoothGattCharacteristic CAPABILITIES_CHARACTERSTIC =
            new BluetoothGattCharacteristic(
                    UuidConstants.CP_FIRA_CONNECTOR_CAPABILITIES_UUID.getUuid(),
                    BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE);
    private BluetoothGattCharacteristic mOutCharacterstic =
            new BluetoothGattCharacteristic(
                    UuidConstants.CP_OUT_CONTROL_POINT_UUID.getUuid(),
                    BluetoothGattCharacteristic.PROPERTY_READ
                            | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_READ);
    private BluetoothGattDescriptor mCccdDescriptor =
            new BluetoothGattDescriptor(
                    UuidConstants.CCCD_UUID.getUuid(), BluetoothGattDescriptor.PERMISSION_READ);

    @Mock AttributionSource mMockAttributionSource;
    @Mock Context mMockContext;
    @Mock TransportClientCallback mMockTransportClientCallback;
    // @Mock TransportClientInfo mMockTransportClientInfo;
    @Mock ScanResult mMockScanResult;
    @Mock BluetoothDevice mMockBluetoothDevice;
    @Mock BluetoothGatt mMockBluetoothGatt;
    @Mock BluetoothGattService mMockBluetoothGattService;
    @Mock DataReceiver mMockDataReceiver;

    private TransportClientInfo mTransportClientInfo;
    private GattTransportClientProvider mGattTransportClientProvider;
    private BluetoothGattCallback mBluetoothGattCallback;
    private FiraConnectorCapabilities mDefaultCapabilities =
            new FiraConnectorCapabilities.Builder().build();

    // For matching the package name of a PackageInfo
    private static class CharacteristicMatcher
            implements ArgumentMatcher<BluetoothGattCharacteristic> {
        private final BluetoothGattCharacteristic mCharacteristic;

        CharacteristicMatcher(BluetoothGattCharacteristic characteristic) {
            mCharacteristic = characteristic;
        }

        @Override
        public boolean matches(BluetoothGattCharacteristic characteristic) {
            return characteristic.getUuid().equals(mCharacteristic.getUuid());
        }

        @Override
        public String toString() {
            return String.format(
                    "BluetoothGattCharacteristic with uuid %s'", mCharacteristic.getUuid());
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.createContext(any())).thenReturn(mMockContext);
        when(mMockBluetoothGatt.connect()).thenReturn(true);
        when(mMockScanResult.getDevice()).thenReturn(mMockBluetoothDevice);
        mTransportClientInfo = new TransportClientInfo(mMockScanResult);

        mGattTransportClientProvider =
                new GattTransportClientProvider(
                        mMockAttributionSource,
                        mMockContext,
                        EXECUTOR,
                        SECID,
                        mTransportClientInfo,
                        mMockTransportClientCallback);
        mGattTransportClientProvider.registerDataReceiver(mMockDataReceiver);
        assertThat(mOutCharacterstic.addDescriptor(mCccdDescriptor)).isTrue();
    }

    @Test
    public void testStartAndStop() {
        when(mMockBluetoothDevice.connectGatt(eq(mMockContext), anyBoolean(), any(), anyInt()))
                .thenReturn(mMockBluetoothGatt);

        assertThat(mGattTransportClientProvider.start()).isTrue();

        verify(mMockBluetoothDevice, times(1)).connectGatt(any(), anyBoolean(), any(), anyInt());
        assertThat(mGattTransportClientProvider.isStarted()).isTrue();
        assertThat(mGattTransportClientProvider.stop()).isTrue();
        verify(mMockBluetoothGatt, times(1)).disconnect();
        assertThat(mGattTransportClientProvider.isStarted()).isFalse();
        assertThat(mGattTransportClientProvider.start()).isTrue();
        verify(mMockBluetoothGatt, times(1)).connect();
    }

    @Test
    public void testStart_successAndRejectRestart() {
        when(mMockBluetoothDevice.connectGatt(eq(mMockContext), anyBoolean(), any(), anyInt()))
                .thenReturn(mMockBluetoothGatt);
        assertThat(mGattTransportClientProvider.start()).isTrue();
        verify(mMockBluetoothDevice, times(1)).connectGatt(any(), anyBoolean(), any(), anyInt());
        assertThat(mGattTransportClientProvider.start()).isFalse();
        verify(mMockBluetoothDevice, times(1)).connectGatt(any(), anyBoolean(), any(), anyInt());
        verify(mMockBluetoothGatt, never()).connect();
    }

    @Test
    public void testStop_successAndRejectRestop() {
        when(mMockBluetoothDevice.connectGatt(eq(mMockContext), anyBoolean(), any(), anyInt()))
                .thenReturn(mMockBluetoothGatt);
        assertThat(mGattTransportClientProvider.start()).isTrue();
        verify(mMockBluetoothDevice, times(1)).connectGatt(any(), anyBoolean(), any(), anyInt());
        assertThat(mGattTransportClientProvider.stop()).isTrue();
        verify(mMockBluetoothGatt, times(1)).disconnect();
        assertThat(mGattTransportClientProvider.stop()).isFalse();
        verify(mMockBluetoothGatt, times(1)).disconnect();
    }

    private void setupGattConnect() {
        Answer notifyConnectionStateChange =
                new Answer() {
                    public BluetoothGatt answer(InvocationOnMock invocation) {
                        mBluetoothGattCallback =
                                (BluetoothGattCallback) invocation.getArgument(/*index=*/ 2);
                        EXECUTOR.execute(
                                () ->
                                        mBluetoothGattCallback.onConnectionStateChange(
                                                mMockBluetoothGatt,
                                                BluetoothGatt.GATT_SUCCESS,
                                                BluetoothProfile.STATE_CONNECTED));
                        return mMockBluetoothGatt;
                    }
                };
        doAnswer(notifyConnectionStateChange)
                .when(mMockBluetoothDevice)
                .connectGatt(eq(mMockContext), anyBoolean(), any(), anyInt());
    }

    private void setupGattServicesDiscover() {
        when(mMockBluetoothGatt.getService(UuidConstants.FIRA_CP_PARCEL_UUID.getUuid()))
                .thenReturn(mMockBluetoothGattService);
        when(mMockBluetoothGattService.getCharacteristic(
                        UuidConstants.CP_IN_CONTROL_POINT_UUID.getUuid()))
                .thenReturn(IN_CHARACTERSTIC);
        when(mMockBluetoothGattService.getCharacteristic(
                        UuidConstants.CP_OUT_CONTROL_POINT_UUID.getUuid()))
                .thenReturn(mOutCharacterstic);
        when(mMockBluetoothGattService.getCharacteristic(
                        UuidConstants.CP_FIRA_CONNECTOR_CAPABILITIES_UUID.getUuid()))
                .thenReturn(CAPABILITIES_CHARACTERSTIC);
        Answer notifyGattServicesDiscovered =
                new Answer() {
                    public Boolean answer(InvocationOnMock invocation) {
                        EXECUTOR.execute(
                                () ->
                                        mBluetoothGattCallback.onServicesDiscovered(
                                                (BluetoothGatt) invocation.getMock(),
                                                BluetoothGatt.GATT_SUCCESS));
                        return true;
                    }
                };
        doAnswer(notifyGattServicesDiscovered).when(mMockBluetoothGatt).discoverServices();
    }

    private void setupGattWriteCharacteristic(BluetoothGattCharacteristic gattCharacteristic) {
        Answer notifyGattCharacteristicWrite =
                new Answer() {
                    public Integer answer(InvocationOnMock invocation) {
                        EXECUTOR.execute(
                                () ->
                                        mBluetoothGattCallback.onCharacteristicWrite(
                                                (BluetoothGatt) invocation.getMock(),
                                                (BluetoothGattCharacteristic)
                                                        invocation.getArgument(/*index=*/ 0),
                                                BluetoothGatt.GATT_SUCCESS));
                        return BluetoothStatusCodes.SUCCESS;
                    }
                };
        doAnswer(notifyGattCharacteristicWrite)
                .when(mMockBluetoothGatt)
                .writeCharacteristic(
                        argThat(new CharacteristicMatcher(gattCharacteristic)), any(), anyInt());
    }

    private void setupGattWriteDescriptor() {
        Answer notifyGattDescriptorWrite =
                new Answer() {
                    public Integer answer(InvocationOnMock invocation) {
                        EXECUTOR.execute(
                                () ->
                                        mBluetoothGattCallback.onDescriptorWrite(
                                                (BluetoothGatt) invocation.getMock(),
                                                (BluetoothGattDescriptor)
                                                        invocation.getArgument(/*index=*/ 0),
                                                BluetoothGatt.GATT_SUCCESS));
                        return BluetoothStatusCodes.SUCCESS;
                    }
                };
        doAnswer(notifyGattDescriptorWrite).when(mMockBluetoothGatt).writeDescriptor(any(), any());
    }

    private void startProcessing() {
        setupGattConnect();
        setupGattServicesDiscover();
        setupGattWriteCharacteristic(CAPABILITIES_CHARACTERSTIC);
        setupGattWriteDescriptor();
        setupGattWriteCharacteristic(IN_CHARACTERSTIC);

        assertThat(mGattTransportClientProvider.start()).isTrue();
        verify(mMockTransportClientCallback, times(1)).onProcessingStarted();
    }

    @Test
    public void testStartProcessing_serviceNotDiscovered() {
        setupGattConnect();
        when(mMockBluetoothGatt.discoverServices()).thenReturn(true);

        assertThat(mGattTransportClientProvider.start()).isTrue();
        verify(mMockBluetoothDevice, times(1)).connectGatt(any(), anyBoolean(), any(), anyInt());
        verify(mMockBluetoothGatt, times(1)).discoverServices();
        verifyZeroInteractions(mMockTransportClientCallback);
    }

    @Test
    public void testStartProcessing_capabilitiesNotWritten() {
        setupGattConnect();
        setupGattServicesDiscover();
        when(mMockBluetoothGatt.writeCharacteristic(any(), any(), anyInt()))
                .thenReturn(BluetoothStatusCodes.ERROR_UNKNOWN);

        assertThat(mGattTransportClientProvider.start()).isTrue();

        verify(mMockBluetoothDevice, times(1)).connectGatt(any(), anyBoolean(), any(), anyInt());
        verify(mMockBluetoothGatt, times(1)).discoverServices();
        verify(mMockBluetoothGatt, times(1))
                .getService(UuidConstants.FIRA_CP_PARCEL_UUID.getUuid());
        verify(mMockBluetoothGattService, times(3)).getCharacteristic(any());
        verify(mMockBluetoothGatt, times(1))
                .writeCharacteristic(
                        any(),
                        eq(mDefaultCapabilities.toBytes()),
                        eq(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT));
        verify(mMockTransportClientCallback, never()).onProcessingStarted();
        verify(mMockTransportClientCallback, times(1))
                .onTerminated(TerminationReason.CHARACTERSTIC_WRITE_FAILURE);
    }

    @Test
    public void testStartProcessing_notificationNotEnabled() {
        setupGattConnect();
        setupGattServicesDiscover();
        setupGattWriteCharacteristic(CAPABILITIES_CHARACTERSTIC);
        when(mMockBluetoothGatt.writeDescriptor(any(), any()))
                .thenReturn(BluetoothStatusCodes.ERROR_UNKNOWN);

        assertThat(mGattTransportClientProvider.start()).isTrue();
        verify(mMockBluetoothDevice, times(1)).connectGatt(any(), anyBoolean(), any(), anyInt());
        verify(mMockBluetoothGatt, times(1)).discoverServices();
        verify(mMockBluetoothGatt, times(1))
                .getService(UuidConstants.FIRA_CP_PARCEL_UUID.getUuid());
        verify(mMockBluetoothGattService, times(3)).getCharacteristic(any());
        verify(mMockBluetoothGatt, times(1))
                .writeCharacteristic(
                        any(),
                        eq(mDefaultCapabilities.toBytes()),
                        eq(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT));
        verify(mMockBluetoothGatt, times(1))
                .writeDescriptor(any(), eq(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE));
        verify(mMockTransportClientCallback, never()).onProcessingStarted();
        verify(mMockTransportClientCallback, times(1))
                .onTerminated(TerminationReason.DESCRIPTOR_WRITE_FAILURE);
    }

    @Test
    public void testStartProcessing_succeed() {
        startProcessing();

        verify(mMockBluetoothDevice, times(1)).connectGatt(any(), anyBoolean(), any(), anyInt());
        verify(mMockBluetoothGatt, times(1)).discoverServices();
        verify(mMockBluetoothGatt, times(1))
                .getService(UuidConstants.FIRA_CP_PARCEL_UUID.getUuid());
        verify(mMockBluetoothGattService, times(3)).getCharacteristic(any());
        verify(mMockBluetoothGatt, times(1))
                .writeCharacteristic(
                        any(),
                        eq(mDefaultCapabilities.toBytes()),
                        eq(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT));
        verify(mMockBluetoothGatt, times(1))
                .writeDescriptor(any(), eq(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE));
        verify(mMockTransportClientCallback, times(1)).onProcessingStarted();
    }

    @Test
    public void testConnectionStateChange_disconnected() {
        startProcessing();
        verify(mMockTransportClientCallback, times(1)).onProcessingStarted();

        mBluetoothGattCallback.onConnectionStateChange(
                mMockBluetoothGatt,
                BluetoothGatt.GATT_SUCCESS,
                BluetoothProfile.STATE_DISCONNECTED);
        verify(mMockTransportClientCallback, times(1)).onProcessingStopped();
        verify(mMockTransportClientCallback, times(1))
                .onTerminated(TerminationReason.REMOTE_DISCONNECTED);
        assertThat(mGattTransportClientProvider.isStarted()).isFalse();
    }

    @Test
    public void testConnectionStateChange_statusFailed() {
        startProcessing();
        verify(mMockTransportClientCallback, times(1)).onProcessingStarted();

        mBluetoothGattCallback.onConnectionStateChange(
                mMockBluetoothGatt, BluetoothGatt.GATT_FAILURE, BluetoothProfile.STATE_CONNECTED);
        verify(mMockTransportClientCallback, times(1)).onProcessingStopped();
        verify(mMockTransportClientCallback, times(1))
                .onTerminated(TerminationReason.REMOTE_DISCONNECTED);
        assertThat(mGattTransportClientProvider.isStarted()).isFalse();
    }

    @Test
    public void testServiceChanged() {
        startProcessing();
        mBluetoothGattCallback.onServiceChanged(mMockBluetoothGatt);
        verify(mMockBluetoothGatt, times(2)).discoverServices();
    }

    @Test
    public void testDescriptorWrite_statusFailed() {
        startProcessing();
        verify(mMockTransportClientCallback, times(1)).onProcessingStarted();

        mBluetoothGattCallback.onDescriptorWrite(
                mMockBluetoothGatt, mCccdDescriptor, BluetoothGatt.GATT_FAILURE);
        verify(mMockTransportClientCallback, times(1))
                .onTerminated(TerminationReason.DESCRIPTOR_WRITE_FAILURE);
        assertThat(mGattTransportClientProvider.isStarted()).isFalse();
    }

    @Test
    public void testCharacteristicWrite_statusFailed() {
        startProcessing();
        verify(mMockTransportClientCallback, times(1)).onProcessingStarted();

        mBluetoothGattCallback.onCharacteristicWrite(
                mMockBluetoothGatt, mOutCharacterstic, BluetoothGatt.GATT_FAILURE);
        verify(mMockTransportClientCallback, times(1))
                .onTerminated(TerminationReason.CHARACTERSTIC_WRITE_FAILURE);
        assertThat(mGattTransportClientProvider.isStarted()).isFalse();
    }

    @Test
    public void testCharacteristicRead_statusFailed() {
        startProcessing();
        verify(mMockTransportClientCallback, times(1)).onProcessingStarted();

        mBluetoothGattCallback.onCharacteristicRead(
                mMockBluetoothGatt, mOutCharacterstic, new byte[] {}, BluetoothGatt.GATT_FAILURE);
        verify(mMockTransportClientCallback, times(1))
                .onTerminated(TerminationReason.CHARACTERSTIC_READ_FAILURE);
        assertThat(mGattTransportClientProvider.isStarted()).isFalse();
    }

    @Test
    public void testSetCapabilities_emptyCapabilities() {
        assertThat(mGattTransportClientProvider.setCapabilites(null)).isFalse();
    }

    @Test
    public void testSetCapabilities_clientNotStarted() {
        assertThat(mGattTransportClientProvider.setCapabilites(CAPABILITIES)).isFalse();
    }

    @Test
    public void testSetCapabilities_serviceNotDiscovered() {
        setupGattConnect();
        when(mMockBluetoothGatt.discoverServices()).thenReturn(true);

        assertThat(mGattTransportClientProvider.start()).isTrue();
        assertThat(mGattTransportClientProvider.setCapabilites(CAPABILITIES)).isFalse();
    }

    @Test
    public void testSetCapabilities_charactersticWriteFailed() {
        startProcessing();

        doReturn(BluetoothStatusCodes.ERROR_UNKNOWN)
                .when(mMockBluetoothGatt)
                .writeCharacteristic(any(), any(), anyInt());
        assertThat(mGattTransportClientProvider.setCapabilites(CAPABILITIES)).isFalse();
    }

    @Test
    public void testSetCapabilities_succeed() {
        startProcessing();

        assertThat(mGattTransportClientProvider.setCapabilites(CAPABILITIES)).isTrue();
    }

    @Test
    public void testOnMtuChanged_failed() {
        startProcessing();
        mBluetoothGattCallback.onMtuChanged(
                mMockBluetoothGatt, /*mtu=*/ 40, BluetoothStatusCodes.ERROR_UNKNOWN);

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(mMockBluetoothGatt, times(1))
                .writeCharacteristic(
                        argThat(new CharacteristicMatcher(CAPABILITIES_CHARACTERSTIC)),
                        captor.capture(),
                        anyInt());
        assertThat(captor.getValue()).isEqualTo(mDefaultCapabilities.toBytes());
    }

    @Test
    public void testOnMtuChanged_succeed() {
        startProcessing();
        int mtu = 40;
        mBluetoothGattCallback.onMtuChanged(mMockBluetoothGatt, mtu, BluetoothGatt.GATT_SUCCESS);

        FiraConnectorCapabilities newCapabilities =
                new FiraConnectorCapabilities.Builder().setOptimizedDataPacketSize(mtu - 3).build();

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(mMockBluetoothGatt, times(2))
                .writeCharacteristic(
                        argThat(new CharacteristicMatcher(CAPABILITIES_CHARACTERSTIC)),
                        captor.capture(),
                        anyInt());
        assertThat(captor.getAllValues().get(0)).isEqualTo(mDefaultCapabilities.toBytes());
        assertThat(captor.getAllValues().get(1)).isEqualTo(newCapabilities.toBytes());
    }

    @Test
    public void testSendMessage_processingNotStarted() {
        assertThat(mGattTransportClientProvider.sendMessage(SECID, MESSAGE)).isFalse();
    }

    @Test
    public void testSendMessage_messageLengthGreaterThanCapabilitites() {
        startProcessing();

        byte[] bytes = new byte[270];
        Arrays.fill(bytes, (byte) 1);
        FiraConnectorMessage message =
                new FiraConnectorMessage(MessageType.EVENT, InstructionCode.DATA_EXCHANGE, bytes);

        // Capabilities set the max message length to 265, so a message of length 270 exceeded the
        // limit.
        assertThat(mGattTransportClientProvider.sendMessage(SECID, message)).isFalse();
    }

    @Test
    public void testSendMessage_charactersticWriteFailed() {
        startProcessing();

        doReturn(BluetoothStatusCodes.ERROR_UNKNOWN)
                .when(mMockBluetoothGatt)
                .writeCharacteristic(
                        argThat(new CharacteristicMatcher(IN_CHARACTERSTIC)), any(), anyInt());

        assertThat(mGattTransportClientProvider.sendMessage(SECID, MESSAGE)).isFalse();
        verify(mMockTransportClientCallback, times(1))
                .onTerminated(TerminationReason.CHARACTERSTIC_WRITE_FAILURE);
    }

    @Test
    public void testSendMessage_succeedOnePacket() {
        startProcessing();

        assertThat(mGattTransportClientProvider.sendMessage(SECID, MESSAGE)).isTrue();
        verify(mMockBluetoothGatt, times(1))
                .writeCharacteristic(
                        argThat(new CharacteristicMatcher(IN_CHARACTERSTIC)), any(), anyInt());
        verify(mMockBluetoothGatt, times(1))
                .writeCharacteristic(
                        argThat(new CharacteristicMatcher(IN_CHARACTERSTIC)),
                        eq(DATA_PACKET_BYTES),
                        eq(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT));
    }

    @Test
    public void testSendMessage_succeedThreePacket() {
        byte[] messagePayload = new byte[51];
        Arrays.fill(messagePayload, (byte) 3);
        FiraConnectorMessage message =
                new FiraConnectorMessage(
                        MessageType.EVENT, InstructionCode.DATA_EXCHANGE, messagePayload);
        byte[] messageBytes = message.toBytes();
        int payloadSize = mDefaultCapabilities.optimizedDataPacketSize - 1;
        FiraConnectorDataPacket packet1 =
                new FiraConnectorDataPacket(
                        /*lastChainingPacket=*/ false,
                        SECID,
                        Arrays.copyOf(messageBytes, payloadSize));
        byte[] packet_bytes2 =
                new FiraConnectorDataPacket(
                                /*lastChainingPacket=*/ false,
                                SECID,
                                Arrays.copyOfRange(messageBytes, payloadSize, 2 * payloadSize))
                        .toBytes();
        byte[] packet_bytes3 =
                new FiraConnectorDataPacket(
                                /*lastChainingPacket=*/ true,
                                SECID,
                                Arrays.copyOfRange(
                                        messageBytes, 2 * payloadSize, messageBytes.length))
                        .toBytes();

        startProcessing();

        assertThat(mGattTransportClientProvider.sendMessage(SECID, message)).isTrue();

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(mMockBluetoothGatt, times(3))
                .writeCharacteristic(
                        argThat(new CharacteristicMatcher(IN_CHARACTERSTIC)),
                        captor.capture(),
                        anyInt());
        assertThat(captor.getAllValues().get(0)).isEqualTo(packet1.toBytes());
        assertThat(captor.getAllValues().get(1)).isEqualTo(packet_bytes2);
        assertThat(captor.getAllValues().get(2)).isEqualTo(packet_bytes3);
        assertThat(FiraConnectorDataPacket.fromBytes(captor.getAllValues().get(0)).toString())
                .isEqualTo(packet1.toString());
    }

    private void notifyAndReadOutCharacteristic(byte[] bytes) {
        Answer notifyGattCharacteristicRead =
                new Answer() {
                    public Boolean answer(InvocationOnMock invocation) {
                        EXECUTOR.execute(
                                () ->
                                        mBluetoothGattCallback.onCharacteristicRead(
                                                (BluetoothGatt) invocation.getMock(),
                                                (BluetoothGattCharacteristic)
                                                        invocation.getArgument(/*index=*/ 0),
                                                bytes,
                                                BluetoothGatt.GATT_SUCCESS));
                        return true;
                    }
                };
        doAnswer(notifyGattCharacteristicRead)
                .when(mMockBluetoothGatt)
                .readCharacteristic(argThat(new CharacteristicMatcher(mOutCharacterstic)));

        mBluetoothGattCallback.onCharacteristicChanged(
                mMockBluetoothGatt, mOutCharacterstic, bytes);
    }

    @Test
    public void testOutCharactersticNotifyAndRead_readFailed() {
        startProcessing();
        when(mMockBluetoothGatt.readCharacteristic(any())).thenReturn(false);

        mBluetoothGattCallback.onCharacteristicChanged(
                mMockBluetoothGatt, mOutCharacterstic, DATA_PACKET_BYTES);

        verify(mMockBluetoothGatt, times(1))
                .readCharacteristic(argThat(new CharacteristicMatcher(mOutCharacterstic)));
        verify(mMockTransportClientCallback, times(1))
                .onTerminated(TerminationReason.CHARACTERSTIC_READ_FAILURE);
    }

    @Test
    public void testOutCharactersticNotifyAndRead_nullGatt() {
        startProcessing();

        mBluetoothGattCallback.onCharacteristicChanged(
                /*gatt=*/ null, mOutCharacterstic, DATA_PACKET_BYTES);

        verify(mMockBluetoothGatt, never()).readCharacteristic(any());
        verify(mMockTransportClientCallback, times(1))
                .onTerminated(TerminationReason.CHARACTERSTIC_READ_FAILURE);
    }

    @Test
    public void testOutCharactersticNotifyAndRead_emptyPacket() {
        startProcessing();
        notifyAndReadOutCharacteristic(new byte[] {});

        verify(mMockBluetoothGatt, times(1))
                .readCharacteristic(argThat(new CharacteristicMatcher(mOutCharacterstic)));
        verifyNoMoreInteractions(mMockTransportClientCallback);
    }

    @Test
    public void testOutCharactersticNotifyAndRead_succeedOnePacket() {
        startProcessing();
        notifyAndReadOutCharacteristic(DATA_PACKET_BYTES);

        verify(mMockBluetoothGatt, times(1))
                .readCharacteristic(argThat(new CharacteristicMatcher(mOutCharacterstic)));
        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(mMockDataReceiver, times(1)).onDataReceived(captor.capture());
        assertThat(captor.getValue()).isEqualTo(MESSAGE.payload);
    }

    @Test
    public void testOutCharactersticNotifyAndRead_succeedThreePackets() {
        byte[] messagePayload = new byte[51];
        Arrays.fill(messagePayload, (byte) 3);
        FiraConnectorMessage message =
                new FiraConnectorMessage(
                        MessageType.COMMAND, InstructionCode.DATA_EXCHANGE, messagePayload);
        byte[] messageBytes = message.toBytes();
        int payloadSize = OPTIMIZED_DATA_PACKET_SIZE - 1;
        byte[] packet_bytes1 =
                new FiraConnectorDataPacket(
                                /*lastChainingPacket=*/ false,
                                SECID,
                                Arrays.copyOf(messageBytes, payloadSize))
                        .toBytes();
        byte[] packet_bytes2 =
                new FiraConnectorDataPacket(
                                /*lastChainingPacket=*/ false,
                                SECID,
                                Arrays.copyOfRange(messageBytes, payloadSize, 2 * payloadSize))
                        .toBytes();
        byte[] packet_bytes3 =
                new FiraConnectorDataPacket(
                                /*lastChainingPacket=*/ true,
                                SECID,
                                Arrays.copyOfRange(
                                        messageBytes, 2 * payloadSize, messageBytes.length))
                        .toBytes();

        startProcessing();
        assertThat(mGattTransportClientProvider.setCapabilites(CAPABILITIES)).isTrue();
        notifyAndReadOutCharacteristic(packet_bytes1);
        notifyAndReadOutCharacteristic(packet_bytes2);
        notifyAndReadOutCharacteristic(packet_bytes3);

        verify(mMockBluetoothGatt, times(3))
                .readCharacteristic(argThat(new CharacteristicMatcher(mOutCharacterstic)));
        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(mMockDataReceiver, times(1)).onDataReceived(captor.capture());
        assertThat(captor.getValue()).isEqualTo(message.payload);
    }

    @Test
    public void testOutCharactersticNotifyAndRead_receiveAdminPacket() {
        byte[] packetBytes =
                new FiraConnectorDataPacket(
                                /*lastChainingPacket=*/ true,
                                TransportProvider.ADMIN_SECID,
                                new AdminErrorMessage(ErrorType.SECID_INVALID).toBytes())
                        .toBytes();
        startProcessing();
        notifyAndReadOutCharacteristic(packetBytes);

        verify(mMockBluetoothGatt, times(1))
                .readCharacteristic(argThat(new CharacteristicMatcher(mOutCharacterstic)));
        verify(mMockDataReceiver, never()).onDataReceived(any());
        verify(mMockTransportClientCallback, times(1))
                .onTerminated(TerminationReason.REMOTE_DEVICE_SECID_ERROR);
        assertThat(mGattTransportClientProvider.isStarted()).isFalse();
    }

    @Test
    public void testOutCharactersticNotifyAndRead_packetLengthOverflow() {
        byte[] messagePayload = new byte[OPTIMIZED_DATA_PACKET_SIZE + 1];
        Arrays.fill(messagePayload, (byte) 3);
        byte[] messageBytes =
                new FiraConnectorMessage(
                                MessageType.COMMAND, InstructionCode.DATA_EXCHANGE, messagePayload)
                        .toBytes();
        byte[] packet_bytes =
                new FiraConnectorDataPacket(
                                /*lastChainingPacket=*/ false,
                                SECID,
                                Arrays.copyOf(messageBytes, messageBytes.length))
                        .toBytes();

        FiraConnectorDataPacket expectedInPacket =
                new FiraConnectorDataPacket(
                        /*lastChainingPacket=*/ true,
                        TransportProvider.ADMIN_SECID,
                        new AdminErrorMessage(ErrorType.DATA_PACKET_LENGTH_OVERFLOW).toBytes());

        startProcessing();
        assertThat(mGattTransportClientProvider.setCapabilites(CAPABILITIES)).isTrue();
        notifyAndReadOutCharacteristic(packet_bytes);

        verify(mMockBluetoothGatt, times(1))
                .readCharacteristic(argThat(new CharacteristicMatcher(mOutCharacterstic)));
        verify(mMockDataReceiver, never()).onDataReceived(any());
        verify(mMockBluetoothGatt, times(1))
                .writeCharacteristic(
                        argThat(new CharacteristicMatcher(IN_CHARACTERSTIC)),
                        eq(expectedInPacket.toBytes()),
                        eq(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT));
    }

    @Test
    public void testOutCharactersticNotifyAndRead_tooManyConcurrentSessions() {
        byte[] messagePayload = new byte[24];
        Arrays.fill(messagePayload, (byte) 3);
        int payloadSize = OPTIMIZED_DATA_PACKET_SIZE - 1;
        byte[] messageBytes =
                new FiraConnectorMessage(
                                MessageType.COMMAND, InstructionCode.DATA_EXCHANGE, messagePayload)
                        .toBytes();
        byte[] packet_bytes1 =
                new FiraConnectorDataPacket(
                                /*lastChainingPacket=*/ false,
                                SECID,
                                Arrays.copyOf(messageBytes, payloadSize))
                        .toBytes();
        byte[] packet_bytes2 =
                new FiraConnectorDataPacket(
                                /*lastChainingPacket=*/ true,
                                SECID2,
                                Arrays.copyOfRange(messageBytes, payloadSize, messageBytes.length))
                        .toBytes();
        FiraConnectorDataPacket expectedInPacket =
                new FiraConnectorDataPacket(
                        /*lastChainingPacket=*/ true,
                        TransportProvider.ADMIN_SECID,
                        new AdminErrorMessage(
                                        ErrorType.TOO_MANY_CONCURRENT_FRAGMENTED_MESSAGE_SESSIONS)
                                .toBytes());

        startProcessing();
        assertThat(mGattTransportClientProvider.setCapabilites(CAPABILITIES)).isTrue();
        notifyAndReadOutCharacteristic(packet_bytes1);
        notifyAndReadOutCharacteristic(packet_bytes2);

        verify(mMockBluetoothGatt, times(2))
                .readCharacteristic(argThat(new CharacteristicMatcher(mOutCharacterstic)));
        verify(mMockDataReceiver, never()).onDataReceived(any());
        verify(mMockBluetoothGatt, times(1))
                .writeCharacteristic(
                        argThat(new CharacteristicMatcher(IN_CHARACTERSTIC)),
                        eq(expectedInPacket.toBytes()),
                        eq(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT));
    }

    @Test
    public void testOutCharactersticNotifyAndRead_messageLengthOverflow() {
        int payloadSize = 200;
        FiraConnectorCapabilities capabilities =
                new FiraConnectorCapabilities.Builder()
                        .setOptimizedDataPacketSize(payloadSize + 1)
                        .setMaxMessageBufferSize(264)
                        .build();
        byte[] messagePayload = new byte[265];
        Arrays.fill(messagePayload, (byte) 3);
        byte[] messageBytes =
                new FiraConnectorMessage(
                                MessageType.COMMAND, InstructionCode.DATA_EXCHANGE, messagePayload)
                        .toBytes();
        byte[] packet_bytes1 =
                new FiraConnectorDataPacket(
                                /*lastChainingPacket=*/ false,
                                SECID,
                                Arrays.copyOf(messageBytes, payloadSize))
                        .toBytes();
        byte[] packet_bytes2 =
                new FiraConnectorDataPacket(
                                /*lastChainingPacket=*/ true,
                                SECID,
                                Arrays.copyOfRange(messageBytes, payloadSize, messageBytes.length))
                        .toBytes();
        FiraConnectorDataPacket expectedInPacket =
                new FiraConnectorDataPacket(
                        /*lastChainingPacket=*/ true,
                        TransportProvider.ADMIN_SECID,
                        new AdminErrorMessage(ErrorType.MESSAGE_LENGTH_OVERFLOW).toBytes());

        startProcessing();
        assertThat(mGattTransportClientProvider.setCapabilites(capabilities)).isTrue();
        notifyAndReadOutCharacteristic(packet_bytes1);
        notifyAndReadOutCharacteristic(packet_bytes2);

        verify(mMockBluetoothGatt, times(2))
                .readCharacteristic(argThat(new CharacteristicMatcher(mOutCharacterstic)));
        verify(mMockDataReceiver, never()).onDataReceived(any());
        verify(mMockBluetoothGatt, times(1))
                .writeCharacteristic(
                        argThat(new CharacteristicMatcher(IN_CHARACTERSTIC)),
                        eq(expectedInPacket.toBytes()),
                        eq(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT));
    }
}
