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

import static android.nearby.ScanRequest.SCAN_TYPE_NEARBY_PRESENCE;

import static com.android.server.nearby.NearbyService.TAG;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.location.NanoAppMessage;
import android.nearby.DataElement;
import android.nearby.NearbyDevice;
import android.nearby.NearbyDeviceParcelable;
import android.nearby.OffloadCapability;
import android.nearby.PresenceDevice;
import android.nearby.PresenceScanFilter;
import android.nearby.PublicCredential;
import android.nearby.ScanFilter;
import android.nearby.aidl.IOffloadCallback;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.nearby.NearbyConfiguration;

import com.google.protobuf.ByteString;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import service.proto.Blefilter;

/** Discovery provider that uses CHRE Nearby Nanoapp to do scanning. */
public class ChreDiscoveryProvider extends AbstractDiscoveryProvider {
    // Nanoapp ID reserved for Nearby Presence.
    /** @hide */
    @VisibleForTesting
    public static final long NANOAPP_ID = 0x476f6f676c001031L;
    /** @hide */
    @VisibleForTesting
    public static final int NANOAPP_MESSAGE_TYPE_FILTER = 3;
    /** @hide */
    @VisibleForTesting
    public static final int NANOAPP_MESSAGE_TYPE_FILTER_RESULT = 4;
    /** @hide */
    @VisibleForTesting
    public static final int NANOAPP_MESSAGE_TYPE_CONFIG = 5;

    private final ChreCommunication mChreCommunication;
    private final ChreCallback mChreCallback;
    private final Object mLock = new Object();

    private boolean mChreStarted = false;
    private Context mContext;
    private NearbyConfiguration mNearbyConfiguration;
    private final IntentFilter mIntentFilter;
    // Null when CHRE not started and the filters are never set. Empty the list every time the scan
    // stops.
    @GuardedBy("mLock")
    @Nullable
    private List<ScanFilter> mScanFilters;

    private final BroadcastReceiver mScreenBroadcastReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Boolean screenOn = intent.getAction().equals(Intent.ACTION_SCREEN_ON)
                            || intent.getAction().equals(Intent.ACTION_USER_PRESENT);
                    Log.d(TAG, String.format(
                            "[ChreDiscoveryProvider] update nanoapp screen status: %B", screenOn));
                    sendScreenUpdate(screenOn);
                }
            };

    public ChreDiscoveryProvider(
            Context context, ChreCommunication chreCommunication, Executor executor) {
        super(context, executor);
        mContext = context;
        mChreCommunication = chreCommunication;
        mChreCallback = new ChreCallback();
        mIntentFilter = new IntentFilter();
    }

    /** Initialize the CHRE discovery provider. */
    public void init() {
        mChreCommunication.start(mChreCallback, Collections.singleton(NANOAPP_ID));
        mNearbyConfiguration = new NearbyConfiguration();
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "Start CHRE scan");
        synchronized (mLock) {
            updateFiltersLocked();
        }
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "Stop CHRE scan");
        synchronized (mLock) {
            if (mScanFilters != null) {
                // Cleaning the filters by assigning an empty list
                mScanFilters = List.of();
            }
            updateFiltersLocked();
        }
    }

    @Override
    protected void onSetScanFilters(List<ScanFilter> filters) {
        synchronized (mLock) {
            mScanFilters = filters == null ? null : List.copyOf(filters);
        }
    }

    /**
     * @return {@code true} if CHRE is available and {@code null} when CHRE availability result
     * has not been returned
     */
    @Nullable
    public Boolean available() {
        return mChreCommunication.available();
    }

    /**
     * Query offload capability in a device.
     */
    public void queryOffloadCapability(IOffloadCallback callback) {
        OffloadCapability.Builder builder = new OffloadCapability.Builder();
        mExecutor.execute(() -> {
            long version = mChreCommunication.queryNanoAppVersion();
            builder.setVersion(version);
            builder.setFastPairSupported(version != ChreCommunication.INVALID_NANO_APP_VERSION);
            try {
                callback.onQueryComplete(builder.build());
            } catch (RemoteException | NullPointerException e) {
                e.printStackTrace();
            }
        });
    }

    @VisibleForTesting
    public List<ScanFilter> getFiltersLocked() {
        synchronized (mLock) {
            return mScanFilters == null ? null : List.copyOf(mScanFilters);
        }
    }

    @GuardedBy("mLock")
    private void updateFiltersLocked() {
        if (mScanFilters == null) {
            Log.e(TAG, "ScanFilters not set.");
            return;
        }
        Blefilter.BleFilters.Builder filtersBuilder = Blefilter.BleFilters.newBuilder();
        for (ScanFilter scanFilter : mScanFilters) {
            PresenceScanFilter presenceScanFilter = (PresenceScanFilter) scanFilter;
            Blefilter.BleFilter.Builder filterBuilder = Blefilter.BleFilter.newBuilder();
            for (PublicCredential credential : presenceScanFilter.getCredentials()) {
                filterBuilder.addCertificate(toProtoPublicCredential(credential));
            }
            for (DataElement dataElement : presenceScanFilter.getExtendedProperties()) {
                if (dataElement.getKey() == DataElement.DataType.ACCOUNT_KEY_DATA) {
                    filterBuilder.addDataElement(toProtoDataElement(dataElement));
                } else if (mNearbyConfiguration.isTestAppSupported()
                        && DataElement.isTestDeType(dataElement.getKey())) {
                    filterBuilder.addDataElement(toProtoDataElement(dataElement));
                }
            }
            if (!presenceScanFilter.getPresenceActions().isEmpty()) {
                filterBuilder.setIntent(presenceScanFilter.getPresenceActions().get(0));
            }
            filtersBuilder.addFilter(filterBuilder.build());
        }
        if (mChreStarted) {
            sendFilters(filtersBuilder.build());
        }
    }

    private Blefilter.PublicateCertificate toProtoPublicCredential(PublicCredential credential) {
        Log.d(TAG, String.format("Returns a PublicCertificate with authenticity key size %d and"
                        + " encrypted metadata key tag size %d",
                credential.getAuthenticityKey().length,
                credential.getEncryptedMetadataKeyTag().length));
        return Blefilter.PublicateCertificate.newBuilder()
                .setAuthenticityKey(ByteString.copyFrom(credential.getAuthenticityKey()))
                .setMetadataEncryptionKeyTag(
                        ByteString.copyFrom(credential.getEncryptedMetadataKeyTag()))
                .build();
    }

    private Blefilter.DataElement toProtoDataElement(DataElement dataElement) {
        return Blefilter.DataElement.newBuilder()
                .setKey(dataElement.getKey())
                .setValue(ByteString.copyFrom(dataElement.getValue()))
                .setValueLength(dataElement.getValue().length)
                .build();
    }

    private void sendFilters(Blefilter.BleFilters filters) {
        NanoAppMessage message =
                NanoAppMessage.createMessageToNanoApp(
                        NANOAPP_ID, NANOAPP_MESSAGE_TYPE_FILTER, filters.toByteArray());
        if (mChreCommunication.sendMessageToNanoApp(message)) {
            Log.v(TAG, "Successfully sent filters to CHRE.");
            return;
        }
        Log.e(TAG, "Failed to send filters to CHRE.");
    }

    private void sendScreenUpdate(Boolean screenOn) {
        Blefilter.BleConfig config = Blefilter.BleConfig.newBuilder().setScreenOn(screenOn).build();
        NanoAppMessage message =
                NanoAppMessage.createMessageToNanoApp(
                        NANOAPP_ID, NANOAPP_MESSAGE_TYPE_CONFIG, config.toByteArray());
        if (mChreCommunication.sendMessageToNanoApp(message)) {
            Log.v(TAG, "Successfully sent config to CHRE.");
            return;
        }
        Log.e(TAG, "Failed to send config to CHRE.");
    }

    private class ChreCallback implements ChreCommunication.ContextHubCommsCallback {

        @Override
        public void started(boolean success) {
            if (success) {
                synchronized (ChreDiscoveryProvider.this) {
                    Log.i(TAG, "CHRE communication started");
                    mIntentFilter.addAction(Intent.ACTION_SCREEN_ON);
                    mIntentFilter.addAction(Intent.ACTION_USER_PRESENT);
                    mIntentFilter.addAction(Intent.ACTION_SCREEN_OFF);
                    mContext.registerReceiver(mScreenBroadcastReceiver, mIntentFilter);
                    mChreStarted = true;
                }
            }
        }

        @Override
        public void onHubReset() {
            // TODO(b/221082271): hooked with upper level codes.
            Log.i(TAG, "CHRE reset.");
        }

        @Override
        public void onNanoAppRestart(long nanoAppId) {
            // TODO(b/221082271): hooked with upper level codes.
            Log.i(TAG, String.format("CHRE NanoApp %d restart.", nanoAppId));
        }

        @Override
        public void onMessageFromNanoApp(NanoAppMessage message) {
            if (message.getNanoAppId() != NANOAPP_ID) {
                Log.e(TAG, "Received message from unknown nano app.");
                return;
            }
            if (mListener == null) {
                Log.e(TAG, "the listener is not set in ChreDiscoveryProvider.");
                return;
            }
            if (message.getMessageType() == NANOAPP_MESSAGE_TYPE_FILTER_RESULT) {
                try {
                    Blefilter.BleFilterResults results =
                            Blefilter.BleFilterResults.parseFrom(message.getMessageBody());
                    for (Blefilter.BleFilterResult filterResult : results.getResultList()) {
                        // TODO(b/234653356): There are some duplicate fields set both in
                        //  PresenceDevice and NearbyDeviceParcelable, cleanup is needed.
                        byte[] salt = {1};
                        byte[] secretId = {1};
                        byte[] authenticityKey = {1};
                        byte[] publicKey = {1};
                        byte[] encryptedMetaData = {1};
                        byte[] encryptedMetaDataTag = {1};
                        if (filterResult.hasPublicCredential()) {
                            Blefilter.PublicCredential credential =
                                    filterResult.getPublicCredential();
                            secretId = credential.getSecretId().toByteArray();
                            authenticityKey = credential.getAuthenticityKey().toByteArray();
                            publicKey = credential.getPublicKey().toByteArray();
                            encryptedMetaData = credential.getEncryptedMetadata().toByteArray();
                            encryptedMetaDataTag =
                                    credential.getEncryptedMetadataTag().toByteArray();
                        }
                        PresenceDevice.Builder presenceDeviceBuilder =
                                new PresenceDevice.Builder(
                                        String.valueOf(filterResult.hashCode()),
                                        salt,
                                        secretId,
                                        encryptedMetaData)
                                        .setRssi(filterResult.getRssi())
                                        .addMedium(NearbyDevice.Medium.BLE);
                        // Data Elements reported from nanoapp added to Data Elements.
                        // i.e. Fast Pair account keys, connection status and battery
                        for (Blefilter.DataElement element : filterResult.getDataElementList()) {
                            addDataElementsToPresenceDevice(element, presenceDeviceBuilder);
                        }
                        // BlE address appended to Data Element.
                        if (filterResult.hasBluetoothAddress()) {
                            presenceDeviceBuilder.addExtendedProperty(
                                    new DataElement(
                                            DataElement.DataType.BLE_ADDRESS,
                                            filterResult.getBluetoothAddress().toByteArray()));
                        }
                        // BlE TX Power appended to Data Element.
                        if (filterResult.hasTxPower()) {
                            presenceDeviceBuilder.addExtendedProperty(
                                    new DataElement(
                                            DataElement.DataType.TX_POWER,
                                            new byte[]{(byte) filterResult.getTxPower()}));
                        }
                        // BLE Service data appended to Data Elements.
                        if (filterResult.hasBleServiceData()) {
                            // Retrieves the length of the service data from the first byte,
                            // and then skips the first byte and returns data[1 .. dataLength)
                            // as the DataElement value.
                            int dataLength = Byte.toUnsignedInt(
                                    filterResult.getBleServiceData().byteAt(0));
                            presenceDeviceBuilder.addExtendedProperty(
                                    new DataElement(
                                            DataElement.DataType.BLE_SERVICE_DATA,
                                            filterResult.getBleServiceData()
                                                    .substring(1, 1 + dataLength).toByteArray()));
                        }
                        // Add action
                        if (filterResult.hasIntent()) {
                            presenceDeviceBuilder.addExtendedProperty(
                                    new DataElement(
                                            DataElement.DataType.ACTION,
                                            new byte[]{(byte) filterResult.getIntent()}));
                        }
                        if (filterResult.hasTimestampNs()) {
                            presenceDeviceBuilder
                                    .setDiscoveryTimestampMillis(MILLISECONDS.convert(
                                            filterResult.getTimestampNs(), NANOSECONDS));
                        }
                        PublicCredential publicCredential =
                                new PublicCredential.Builder(
                                        secretId,
                                        authenticityKey,
                                        publicKey,
                                        encryptedMetaData,
                                        encryptedMetaDataTag)
                                        .build();

                        NearbyDeviceParcelable device =
                                new NearbyDeviceParcelable.Builder()
                                        .setDeviceId(Arrays.hashCode(secretId))
                                        .setScanType(SCAN_TYPE_NEARBY_PRESENCE)
                                        .setMedium(NearbyDevice.Medium.BLE)
                                        .setTxPower(filterResult.getTxPower())
                                        .setRssi(filterResult.getRssi())
                                        .setAction(filterResult.getIntent())
                                        .setPublicCredential(publicCredential)
                                        .setPresenceDevice(presenceDeviceBuilder.build())
                                        .setEncryptionKeyTag(encryptedMetaDataTag)
                                        .build();
                        mExecutor.execute(() -> mListener.onNearbyDeviceDiscovered(device));
                    }
                } catch (Exception e) {
                    Log.e(TAG, String.format("Failed to decode the filter result %s", e));
                }
            }
        }

        private void addDataElementsToPresenceDevice(Blefilter.DataElement element,
                PresenceDevice.Builder presenceDeviceBuilder) {
            int endIndex = element.hasValueLength() ? element.getValueLength() :
                    element.getValue().size();
            int key = element.getKey();
            switch (key) {
                case DataElement.DataType.ACCOUNT_KEY_DATA:
                case DataElement.DataType.CONNECTION_STATUS:
                case DataElement.DataType.BATTERY:
                    presenceDeviceBuilder.addExtendedProperty(
                            new DataElement(key,
                                    element.getValue().substring(0, endIndex).toByteArray()));
                    break;
                default:
                    if (mNearbyConfiguration.isTestAppSupported()
                            && DataElement.isTestDeType(key)) {
                        presenceDeviceBuilder.addExtendedProperty(
                                new DataElement(key,
                                        element.getValue().substring(0, endIndex).toByteArray()));
                    }
                    break;
            }
        }
    }
}
