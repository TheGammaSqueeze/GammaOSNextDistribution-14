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

import android.annotation.Nullable;
import android.nearby.BroadcastRequest;
import android.nearby.PresenceBroadcastRequest;
import android.nearby.PresenceCredential;

import com.android.internal.util.Preconditions;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;


/**
 * A Nearby Presence advertisement to be advertised on BT4.2 devices.
 *
 * <p>Serializable between Java object and bytes formats. Java object is used at the upper scanning
 * and advertising interface as an abstraction of the actual bytes. Bytes format is used at the
 * underlying BLE and mDNS stacks, which do necessary slicing and merging based on advertising
 * capacities.
 */
// The fast advertisement is defined in the format below:
// Header (1 byte) | salt (2 bytes) | identity (14 bytes) | tx_power (1 byte) | actions (1~ bytes)
// The header contains:
// version (3 bits) | provision_mode_flag (1 bit) | identity_type (3 bits) |
// extended_advertisement_mode (1 bit)
public class FastAdvertisement extends Advertisement {

    private static final int FAST_ADVERTISEMENT_MAX_LENGTH = 24;

    static final byte INVALID_TX_POWER = (byte) 0xFF;

    static final int HEADER_LENGTH = 1;

    static final int SALT_LENGTH = 2;

    static final int IDENTITY_LENGTH = 14;

    static final int TX_POWER_LENGTH = 1;

    private static final int MAX_ACTION_COUNT = 6;

    /**
     * Creates a {@link FastAdvertisement} from a Presence Broadcast Request.
     */
    public static FastAdvertisement createFromRequest(PresenceBroadcastRequest request) {
        byte[] salt = request.getSalt();
        byte[] identity = request.getCredential().getMetadataEncryptionKey();
        List<Integer> actions = request.getActions();
        Preconditions.checkArgument(
                salt.length == SALT_LENGTH,
                "FastAdvertisement's salt does not match correct length");
        Preconditions.checkArgument(
                identity.length == IDENTITY_LENGTH,
                "FastAdvertisement's identity does not match correct length");
        Preconditions.checkArgument(
                !actions.isEmpty(), "FastAdvertisement must contain at least one action");
        Preconditions.checkArgument(
                actions.size() <= MAX_ACTION_COUNT,
                "FastAdvertisement advertised actions cannot exceed max count " + MAX_ACTION_COUNT);

        return new FastAdvertisement(
                request.getCredential().getIdentityType(),
                identity,
                salt,
                actions,
                (byte) request.getTxPower());
    }

    /** Serialize a {@link FastAdvertisement} object into bytes. */
    @Override
    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(getLength());

        buffer.put(FastAdvertisementUtils.constructHeader(getVersion(), mIdentityType));
        buffer.put(mSalt);
        buffer.put(getIdentity());

        buffer.put(mTxPower == null ? INVALID_TX_POWER : mTxPower);
        for (int action : mActions) {
            buffer.put((byte) action);
        }
        return buffer.array();
    }

    private final int mLtvFieldCount;

    @Nullable
    private final Byte mTxPower;

    FastAdvertisement(
            @PresenceCredential.IdentityType int identityType,
            byte[] identity,
            byte[] salt,
            List<Integer> actions,
            @Nullable Byte txPower) {
        this.mVersion = BroadcastRequest.PRESENCE_VERSION_V0;
        this.mIdentityType = identityType;
        this.mIdentity = identity;
        this.mSalt = salt;
        this.mActions = actions;
        this.mTxPower = txPower;
        int ltvFieldCount = 3;
        int length =
                HEADER_LENGTH // header
                        + identity.length
                        + salt.length
                        + actions.size();
        length += TX_POWER_LENGTH;
        if (txPower != null) { // TX power
            ltvFieldCount += 1;
        }
        this.mLength = length;
        this.mLtvFieldCount = ltvFieldCount;
        Preconditions.checkArgument(
                length <= FAST_ADVERTISEMENT_MAX_LENGTH,
                "FastAdvertisement exceeds maximum length");
    }

    /** Returns the adjusted TX Power in the advertisement. Null if not available. */
    @Nullable
    public Byte getTxPower() {
        return mTxPower;
    }

    /** Returns the count of LTV fields in the advertisement. */
    public int getLtvFieldCount() {
        return mLtvFieldCount;
    }

    @Override
    public String toString() {
        return String.format(
                "FastAdvertisement:<VERSION: %s, length: %s, ltvFieldCount: %s, identityType: %s,"
                        + " identity: %s, salt: %s, actions: %s, txPower: %s",
                getVersion(),
                getLength(),
                getLtvFieldCount(),
                getIdentityType(),
                Arrays.toString(getIdentity()),
                Arrays.toString(getSalt()),
                getActions(),
                getTxPower());
    }
}
