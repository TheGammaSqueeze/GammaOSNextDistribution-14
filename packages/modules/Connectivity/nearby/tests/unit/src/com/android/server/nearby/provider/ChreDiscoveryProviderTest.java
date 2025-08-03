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

package com.android.server.nearby.provider;

import static android.Manifest.permission.READ_DEVICE_CONFIG;
import static android.Manifest.permission.WRITE_DEVICE_CONFIG;

import static com.android.server.nearby.NearbyConfiguration.NEARBY_SUPPORT_TEST_APP;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.hardware.location.NanoAppMessage;
import android.nearby.DataElement;
import android.nearby.NearbyDeviceParcelable;
import android.nearby.OffloadCapability;
import android.nearby.aidl.IOffloadCallback;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.DeviceConfig;

import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.nearby.NearbyConfiguration;
import com.android.server.nearby.presence.PresenceDiscoveryResult;

import com.google.protobuf.ByteString;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import service.proto.Blefilter;

public class ChreDiscoveryProviderTest {
    @Mock AbstractDiscoveryProvider.Listener mListener;
    @Mock ChreCommunication mChreCommunication;
    @Mock IBinder mIBinder;

    @Captor ArgumentCaptor<ChreCommunication.ContextHubCommsCallback> mChreCallbackCaptor;
    @Captor ArgumentCaptor<NearbyDeviceParcelable> mNearbyDevice;

    private static final String NAMESPACE = NearbyConfiguration.getNamespace();
    private static final int DATA_TYPE_CONNECTION_STATUS_KEY = 10;
    private static final int DATA_TYPE_BATTERY_KEY = 11;
    private static final int DATA_TYPE_TX_POWER_KEY = 5;
    private static final int DATA_TYPE_BLUETOOTH_ADDR_KEY = 101;
    private static final int DATA_TYPE_FP_ACCOUNT_KEY = 9;
    private static final int DATA_TYPE_BLE_SERVICE_DATA_KEY = 100;
    private static final int DATA_TYPE_TEST_DE_BEGIN_KEY = 2147483520;
    private static final int DATA_TYPE_TEST_DE_END_KEY = 2147483647;

    private final Object mLock = new Object();
    private ChreDiscoveryProvider mChreDiscoveryProvider;
    private OffloadCapability mOffloadCapability;

    @Before
    public void setUp() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(WRITE_DEVICE_CONFIG, READ_DEVICE_CONFIG);

        MockitoAnnotations.initMocks(this);
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mChreDiscoveryProvider =
                new ChreDiscoveryProvider(context, mChreCommunication, new InLineExecutor());
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testInit() {
        mChreDiscoveryProvider.init();
        verify(mChreCommunication).start(mChreCallbackCaptor.capture(), any());
        mChreCallbackCaptor.getValue().started(true);
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void test_queryInvalidVersion() {
        when(mChreCommunication.queryNanoAppVersion()).thenReturn(
                (long) ChreCommunication.INVALID_NANO_APP_VERSION);
        IOffloadCallback callback = new IOffloadCallback() {
            @Override
            public void onQueryComplete(OffloadCapability capability) throws RemoteException {
                synchronized (mLock) {
                    mOffloadCapability = capability;
                    mLock.notify();
                }
            }

            @Override
            public IBinder asBinder() {
                return mIBinder;
            }
        };
        mChreDiscoveryProvider.queryOffloadCapability(callback);
        OffloadCapability capability =
                new OffloadCapability
                        .Builder()
                        .setFastPairSupported(false)
                        .setVersion(ChreCommunication.INVALID_NANO_APP_VERSION)
                        .build();
        assertThat(mOffloadCapability).isEqualTo(capability);
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void test_queryVersion() {
        long version = 500L;
        when(mChreCommunication.queryNanoAppVersion()).thenReturn(version);
        IOffloadCallback callback = new IOffloadCallback() {
            @Override
            public void onQueryComplete(OffloadCapability capability) throws RemoteException {
                synchronized (mLock) {
                    mOffloadCapability = capability;
                    mLock.notify();
                }
            }

            @Override
            public IBinder asBinder() {
                return mIBinder;
            }
        };
        mChreDiscoveryProvider.queryOffloadCapability(callback);
        OffloadCapability capability =
                new OffloadCapability
                        .Builder()
                        .setFastPairSupported(true)
                        .setVersion(version)
                        .build();
        assertThat(mOffloadCapability).isEqualTo(capability);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testOnNearbyDeviceDiscovered() {
        Blefilter.PublicCredential credential =
                Blefilter.PublicCredential.newBuilder()
                        .setSecretId(ByteString.copyFrom(new byte[] {1}))
                        .setAuthenticityKey(ByteString.copyFrom(new byte[2]))
                        .setPublicKey(ByteString.copyFrom(new byte[3]))
                        .setEncryptedMetadata(ByteString.copyFrom(new byte[4]))
                        .setEncryptedMetadataTag(ByteString.copyFrom(new byte[5]))
                        .build();
        Blefilter.BleFilterResult result =
                Blefilter.BleFilterResult.newBuilder()
                        .setTxPower(2)
                        .setRssi(1)
                        .setPublicCredential(credential)
                        .build();
        Blefilter.BleFilterResults results =
                Blefilter.BleFilterResults.newBuilder().addResult(result).build();
        NanoAppMessage chre_message =
                NanoAppMessage.createMessageToNanoApp(
                        ChreDiscoveryProvider.NANOAPP_ID,
                        ChreDiscoveryProvider.NANOAPP_MESSAGE_TYPE_FILTER_RESULT,
                        results.toByteArray());
        mChreDiscoveryProvider.getController().setListener(mListener);
        mChreDiscoveryProvider.init();
        mChreDiscoveryProvider.onStart();
        verify(mChreCommunication).start(mChreCallbackCaptor.capture(), any());
        mChreCallbackCaptor.getValue().onMessageFromNanoApp(chre_message);
        verify(mListener).onNearbyDeviceDiscovered(any());
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testOnNearbyDeviceDiscoveredWithDataElements_TIME() {
        // The feature only supports user-debug builds.
        if (!Build.isDebuggable()) {
            return;
        }

        // Disables the setting of test app support
        boolean isSupportedTestApp = getDeviceConfigBoolean(
                NEARBY_SUPPORT_TEST_APP, false /* defaultValue */);
        if (isSupportedTestApp) {
            DeviceConfig.setProperty(NAMESPACE, NEARBY_SUPPORT_TEST_APP, "false", false);
        }
        assertThat(new NearbyConfiguration().isTestAppSupported()).isFalse();

        final byte [] connectionStatus = new byte[] {1, 2, 3};
        final byte [] batteryStatus = new byte[] {4, 5, 6};
        final byte [] txPower = new byte[] {2};
        final byte [] bluetoothAddr = new byte[] {1, 2, 3, 4, 5, 6};
        final byte [] fastPairAccountKey = new byte[16];
        // First byte is length of service data, padding zeros should be thrown away.
        final byte [] bleServiceData = new byte[] {5, 1, 2, 3, 4, 5, 0, 0, 0, 0};
        final byte [] testData = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        final long timestampNs = 1697765417070000000L;

        final List<DataElement> expectedExtendedProperties = new ArrayList<>();
        expectedExtendedProperties.add(new DataElement(DATA_TYPE_CONNECTION_STATUS_KEY,
                connectionStatus));
        expectedExtendedProperties.add(new DataElement(DATA_TYPE_BATTERY_KEY, batteryStatus));
        expectedExtendedProperties.add(new DataElement(DATA_TYPE_TX_POWER_KEY, txPower));
        expectedExtendedProperties.add(
                new DataElement(DATA_TYPE_BLUETOOTH_ADDR_KEY, bluetoothAddr));
        expectedExtendedProperties.add(
                new DataElement(DATA_TYPE_FP_ACCOUNT_KEY, fastPairAccountKey));
        expectedExtendedProperties.add(
                new DataElement(DATA_TYPE_BLE_SERVICE_DATA_KEY, new byte[] {1, 2, 3, 4, 5}));

        Blefilter.PublicCredential credential =
                Blefilter.PublicCredential.newBuilder()
                        .setSecretId(ByteString.copyFrom(new byte[] {1}))
                        .setAuthenticityKey(ByteString.copyFrom(new byte[2]))
                        .setPublicKey(ByteString.copyFrom(new byte[3]))
                        .setEncryptedMetadata(ByteString.copyFrom(new byte[4]))
                        .setEncryptedMetadataTag(ByteString.copyFrom(new byte[5]))
                        .build();
        Blefilter.BleFilterResult result =
                Blefilter.BleFilterResult.newBuilder()
                        .setTxPower(2)
                        .setRssi(1)
                        .setBluetoothAddress(ByteString.copyFrom(bluetoothAddr))
                        .setBleServiceData(ByteString.copyFrom(bleServiceData))
                        .setPublicCredential(credential)
                        .addDataElement(Blefilter.DataElement.newBuilder()
                                .setKey(DATA_TYPE_CONNECTION_STATUS_KEY)
                                .setValue(ByteString.copyFrom(connectionStatus))
                                .setValueLength(connectionStatus.length)
                        )
                        .addDataElement(Blefilter.DataElement.newBuilder()
                                .setKey(DATA_TYPE_BATTERY_KEY)
                                .setValue(ByteString.copyFrom(batteryStatus))
                                .setValueLength(batteryStatus.length)
                        )
                        .addDataElement(Blefilter.DataElement.newBuilder()
                                .setKey(DATA_TYPE_FP_ACCOUNT_KEY)
                                .setValue(ByteString.copyFrom(fastPairAccountKey))
                                .setValueLength(fastPairAccountKey.length)
                        )
                        .addDataElement(Blefilter.DataElement.newBuilder()
                                .setKey(DATA_TYPE_TEST_DE_BEGIN_KEY)
                                .setValue(ByteString.copyFrom(testData))
                                .setValueLength(testData.length)
                        )
                        .addDataElement(Blefilter.DataElement.newBuilder()
                                .setKey(DATA_TYPE_TEST_DE_END_KEY)
                                .setValue(ByteString.copyFrom(testData))
                                .setValueLength(testData.length)
                        )
                        .setTimestampNs(timestampNs)
                        .build();
        Blefilter.BleFilterResults results =
                Blefilter.BleFilterResults.newBuilder().addResult(result).build();
        NanoAppMessage chre_message =
                NanoAppMessage.createMessageToNanoApp(
                        ChreDiscoveryProvider.NANOAPP_ID,
                        ChreDiscoveryProvider.NANOAPP_MESSAGE_TYPE_FILTER_RESULT,
                        results.toByteArray());
        mChreDiscoveryProvider.getController().setListener(mListener);
        mChreDiscoveryProvider.init();
        mChreDiscoveryProvider.onStart();
        verify(mChreCommunication).start(mChreCallbackCaptor.capture(), any());
        mChreCallbackCaptor.getValue().onMessageFromNanoApp(chre_message);
        verify(mListener).onNearbyDeviceDiscovered(mNearbyDevice.capture());

        List<DataElement> extendedProperties = PresenceDiscoveryResult
                .fromDevice(mNearbyDevice.getValue()).getExtendedProperties();
        assertThat(extendedProperties).containsExactlyElementsIn(expectedExtendedProperties);
        // Reverts the setting of test app support
        if (isSupportedTestApp) {
            DeviceConfig.setProperty(NAMESPACE, NEARBY_SUPPORT_TEST_APP, "true", false);
            assertThat(new NearbyConfiguration().isTestAppSupported()).isTrue();
        }
        // Nanoseconds to Milliseconds
        assertThat((mNearbyDevice.getValue().getPresenceDevice())
                .getDiscoveryTimestampMillis()).isEqualTo(timestampNs / 1000000);
    }

    @Test
    @SdkSuppress(minSdkVersion = 33, codeName = "T")
    public void testOnNearbyDeviceDiscoveredWithTestDataElements() {
        // The feature only supports user-debug builds.
        if (!Build.isDebuggable()) {
            return;
        }
        // Enables the setting of test app support
        boolean isSupportedTestApp = getDeviceConfigBoolean(
                NEARBY_SUPPORT_TEST_APP, false /* defaultValue */);
        if (!isSupportedTestApp) {
            DeviceConfig.setProperty(NAMESPACE, NEARBY_SUPPORT_TEST_APP, "true", false);
        }
        assertThat(new NearbyConfiguration().isTestAppSupported()).isTrue();

        final byte [] connectionStatus = new byte[] {1, 2, 3};
        final byte [] batteryStatus = new byte[] {4, 5, 6};
        final byte [] txPower = new byte[] {2};
        final byte [] bluetoothAddr = new byte[] {1, 2, 3, 4, 5, 6};
        final byte [] fastPairAccountKey = new byte[16];
        // First byte is length of service data, padding zeros should be thrown away.
        final byte [] bleServiceData = new byte[] {5, 1, 2, 3, 4, 5, 0, 0, 0, 0};
        final byte [] testData = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};

        final List<DataElement> expectedExtendedProperties = new ArrayList<>();
        expectedExtendedProperties.add(new DataElement(DATA_TYPE_CONNECTION_STATUS_KEY,
                connectionStatus));
        expectedExtendedProperties.add(new DataElement(DATA_TYPE_BATTERY_KEY, batteryStatus));
        expectedExtendedProperties.add(new DataElement(DATA_TYPE_TX_POWER_KEY, txPower));
        expectedExtendedProperties.add(
                new DataElement(DATA_TYPE_BLUETOOTH_ADDR_KEY, bluetoothAddr));
        expectedExtendedProperties.add(
                new DataElement(DATA_TYPE_FP_ACCOUNT_KEY, fastPairAccountKey));
        expectedExtendedProperties.add(
                new DataElement(DATA_TYPE_BLE_SERVICE_DATA_KEY, new byte[] {1, 2, 3, 4, 5}));
        expectedExtendedProperties.add(
                new DataElement(DATA_TYPE_TEST_DE_BEGIN_KEY, testData));
        expectedExtendedProperties.add(
                new DataElement(DATA_TYPE_TEST_DE_END_KEY, testData));

        Blefilter.PublicCredential credential =
                Blefilter.PublicCredential.newBuilder()
                        .setSecretId(ByteString.copyFrom(new byte[] {1}))
                        .setAuthenticityKey(ByteString.copyFrom(new byte[2]))
                        .setPublicKey(ByteString.copyFrom(new byte[3]))
                        .setEncryptedMetadata(ByteString.copyFrom(new byte[4]))
                        .setEncryptedMetadataTag(ByteString.copyFrom(new byte[5]))
                        .build();
        Blefilter.BleFilterResult result =
                Blefilter.BleFilterResult.newBuilder()
                        .setTxPower(2)
                        .setRssi(1)
                        .setBluetoothAddress(ByteString.copyFrom(bluetoothAddr))
                        .setBleServiceData(ByteString.copyFrom(bleServiceData))
                        .setPublicCredential(credential)
                        .addDataElement(Blefilter.DataElement.newBuilder()
                                .setKey(DATA_TYPE_CONNECTION_STATUS_KEY)
                                .setValue(ByteString.copyFrom(connectionStatus))
                                .setValueLength(connectionStatus.length)
                        )
                        .addDataElement(Blefilter.DataElement.newBuilder()
                                .setKey(DATA_TYPE_BATTERY_KEY)
                                .setValue(ByteString.copyFrom(batteryStatus))
                                .setValueLength(batteryStatus.length)
                        )
                        .addDataElement(Blefilter.DataElement.newBuilder()
                                .setKey(DATA_TYPE_FP_ACCOUNT_KEY)
                                .setValue(ByteString.copyFrom(fastPairAccountKey))
                                .setValueLength(fastPairAccountKey.length)
                        )
                        .addDataElement(Blefilter.DataElement.newBuilder()
                                .setKey(DATA_TYPE_TEST_DE_BEGIN_KEY)
                                .setValue(ByteString.copyFrom(testData))
                                .setValueLength(testData.length)
                        )
                        .addDataElement(Blefilter.DataElement.newBuilder()
                                .setKey(DATA_TYPE_TEST_DE_END_KEY)
                                .setValue(ByteString.copyFrom(testData))
                                .setValueLength(testData.length)
                        )
                        .build();
        Blefilter.BleFilterResults results =
                Blefilter.BleFilterResults.newBuilder().addResult(result).build();
        NanoAppMessage chre_message =
                NanoAppMessage.createMessageToNanoApp(
                        ChreDiscoveryProvider.NANOAPP_ID,
                        ChreDiscoveryProvider.NANOAPP_MESSAGE_TYPE_FILTER_RESULT,
                        results.toByteArray());
        mChreDiscoveryProvider.getController().setListener(mListener);
        mChreDiscoveryProvider.init();
        mChreDiscoveryProvider.onStart();
        verify(mChreCommunication).start(mChreCallbackCaptor.capture(), any());
        mChreCallbackCaptor.getValue().onMessageFromNanoApp(chre_message);
        verify(mListener).onNearbyDeviceDiscovered(mNearbyDevice.capture());

        List<DataElement> extendedProperties = PresenceDiscoveryResult
                .fromDevice(mNearbyDevice.getValue()).getExtendedProperties();
        assertThat(extendedProperties).containsExactlyElementsIn(expectedExtendedProperties);
        // Reverts the setting of test app support
        if (!isSupportedTestApp) {
            DeviceConfig.setProperty(NAMESPACE, NEARBY_SUPPORT_TEST_APP, "false", false);
            assertThat(new NearbyConfiguration().isTestAppSupported()).isFalse();
        }
    }

    private boolean getDeviceConfigBoolean(final String name, final boolean defaultValue) {
        final String value = getDeviceConfigProperty(name);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    private String getDeviceConfigProperty(String name) {
        return DeviceConfig.getProperty(NAMESPACE, name);
    }

    private static class InLineExecutor implements Executor {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
