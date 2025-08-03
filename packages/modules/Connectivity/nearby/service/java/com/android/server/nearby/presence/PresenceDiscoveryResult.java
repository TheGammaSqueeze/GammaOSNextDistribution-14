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

package com.android.server.nearby.presence;

import static com.android.server.nearby.NearbyService.TAG;

import android.annotation.NonNull;
import android.nearby.DataElement;
import android.nearby.NearbyDeviceParcelable;
import android.nearby.PresenceDevice;
import android.nearby.PresenceScanFilter;
import android.nearby.PublicCredential;
import android.util.ArraySet;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Represents a Presence discovery result. */
public class PresenceDiscoveryResult {

    /** Creates a {@link PresenceDiscoveryResult} from the scan data. */
    public static PresenceDiscoveryResult fromDevice(NearbyDeviceParcelable device) {
        PresenceDevice presenceDevice = device.getPresenceDevice();
        if (presenceDevice != null) {
            return new PresenceDiscoveryResult.Builder()
                    .setTxPower(device.getTxPower())
                    .setRssi(device.getRssi())
                    .setSalt(presenceDevice.getSalt())
                    .setPublicCredential(device.getPublicCredential())
                    .addExtendedProperties(presenceDevice.getExtendedProperties())
                    .setEncryptedIdentityTag(device.getEncryptionKeyTag())
                    .build();
        }
        byte[] salt = device.getSalt();
        if (salt == null) {
            salt = new byte[0];
        }

        PresenceDiscoveryResult.Builder builder = new PresenceDiscoveryResult.Builder();
        builder.setTxPower(device.getTxPower())
                .setRssi(device.getRssi())
                .setSalt(salt)
                .addPresenceAction(device.getAction())
                .setPublicCredential(device.getPublicCredential());
        if (device.getPresenceDevice() != null) {
            builder.addExtendedProperties(device.getPresenceDevice().getExtendedProperties());
        }
        return builder.build();
    }

    private final int mTxPower;
    private final int mRssi;
    private final byte[] mSalt;
    private final List<Integer> mPresenceActions;
    private final PublicCredential mPublicCredential;
    private final List<DataElement> mExtendedProperties;
    private final byte[] mEncryptedIdentityTag;

    private PresenceDiscoveryResult(
            int txPower,
            int rssi,
            byte[] salt,
            List<Integer> presenceActions,
            PublicCredential publicCredential,
            List<DataElement> extendedProperties,
            byte[] encryptedIdentityTag) {
        mTxPower = txPower;
        mRssi = rssi;
        mSalt = salt;
        mPresenceActions = presenceActions;
        mPublicCredential = publicCredential;
        mExtendedProperties = extendedProperties;
        mEncryptedIdentityTag = encryptedIdentityTag;
    }

    /** Returns whether the discovery result matches the scan filter. */
    public boolean matches(PresenceScanFilter scanFilter) {
        if (accountKeyMatches(scanFilter.getExtendedProperties())) {
            return true;
        }

        return pathLossMatches(scanFilter.getMaxPathLoss())
                && actionMatches(scanFilter.getPresenceActions())
                && identityMatches(scanFilter.getCredentials());
    }

    private boolean pathLossMatches(int maxPathLoss) {
        return (mTxPower - mRssi) <= maxPathLoss;
    }

    private boolean actionMatches(List<Integer> filterActions) {
        if (filterActions.isEmpty()) {
            return true;
        }
        return filterActions.stream().anyMatch(mPresenceActions::contains);
    }

    @VisibleForTesting
    boolean accountKeyMatches(List<DataElement> extendedProperties) {
        Set<byte[]> accountKeys = new ArraySet<>();
        for (DataElement requestedDe : mExtendedProperties) {
            if (requestedDe.getKey() != DataElement.DataType.ACCOUNT_KEY_DATA) {
                continue;
            }
            accountKeys.add(requestedDe.getValue());
        }
        for (DataElement scannedDe : extendedProperties) {
            if (scannedDe.getKey() != DataElement.DataType.ACCOUNT_KEY_DATA) {
                continue;
            }
            // If one account key matches, then returns true.
            for (byte[] key : accountKeys) {
                if (Arrays.equals(key, scannedDe.getValue())) {
                    return true;
                }
            }
        }

        return false;
    }

    @VisibleForTesting
    /** Gets presence {@link DataElement}s of the discovery result. */
    public List<DataElement> getExtendedProperties() {
        return mExtendedProperties;
    }

    private boolean identityMatches(List<PublicCredential> publicCredentials) {
        if (mEncryptedIdentityTag.length == 0) {
            return true;
        }
        for (PublicCredential publicCredential : publicCredentials) {
            if (Arrays.equals(
                    mEncryptedIdentityTag, publicCredential.getEncryptedMetadataKeyTag())) {
                return true;
            }
        }
        return false;
    }

    /** Builder for {@link PresenceDiscoveryResult}. */
    public static class Builder {
        private int mTxPower;
        private int mRssi;
        private byte[] mSalt;

        private PublicCredential mPublicCredential;
        private final List<Integer> mPresenceActions;
        private final List<DataElement> mExtendedProperties;
        private byte[] mEncryptedIdentityTag = new byte[0];

        public Builder() {
            mPresenceActions = new ArrayList<>();
            mExtendedProperties = new ArrayList<>();
        }

        /** Sets the calibrated tx power for the discovery result. */
        public Builder setTxPower(int txPower) {
            mTxPower = txPower;
            return this;
        }

        /** Sets the rssi for the discovery result. */
        public Builder setRssi(int rssi) {
            mRssi = rssi;
            return this;
        }

        /** Sets the salt for the discovery result. */
        public Builder setSalt(byte[] salt) {
            mSalt = salt;
            return this;
        }

        /** Sets the public credential for the discovery result. */
        public Builder setPublicCredential(PublicCredential publicCredential) {
            if (publicCredential != null) {
                mPublicCredential = publicCredential;
            }
            return this;
        }

        /** Sets the encrypted identity tag for the discovery result. Usually it is passed from
         * {@link NearbyDeviceParcelable} and the tag is calculated with authenticity key when
         * receiving an advertisement.
         */
        public Builder setEncryptedIdentityTag(byte[] encryptedIdentityTag) {
            mEncryptedIdentityTag = encryptedIdentityTag;
            return this;
        }

        /** Adds presence action of the discovery result. */
        public Builder addPresenceAction(int presenceAction) {
            mPresenceActions.add(presenceAction);
            return this;
        }

        /** Adds presence {@link DataElement}s of the discovery result. */
        public Builder addExtendedProperties(DataElement dataElement) {
            if (dataElement.getKey() == DataElement.DataType.ACTION) {
                byte[] value = dataElement.getValue();
                if (value.length == 1) {
                    addPresenceAction(Byte.toUnsignedInt(value[0]));
                } else {
                    Log.e(TAG, "invalid action data element");
                }
            } else {
                mExtendedProperties.add(dataElement);
            }
            return this;
        }

        /** Adds presence {@link DataElement}s of the discovery result. */
        public Builder addExtendedProperties(@NonNull List<DataElement> dataElements) {
            for (DataElement dataElement : dataElements) {
                addExtendedProperties(dataElement);
            }
            return this;
        }

        /** Builds a {@link PresenceDiscoveryResult}. */
        public PresenceDiscoveryResult build() {
            return new PresenceDiscoveryResult(
                    mTxPower, mRssi, mSalt, mPresenceActions,
                    mPublicCredential, mExtendedProperties, mEncryptedIdentityTag);
        }
    }
}
