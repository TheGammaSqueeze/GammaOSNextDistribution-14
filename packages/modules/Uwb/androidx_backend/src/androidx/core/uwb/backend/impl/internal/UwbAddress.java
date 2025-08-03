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

package androidx.core.uwb.backend.impl.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.common.primitives.Longs;
import com.google.common.primitives.Shorts;

import java.security.SecureRandom;
import java.util.Arrays;

/**
 * UWB supports two addressing formats: 16-bit short address or 64-bit extended address, This class
 * provides a way to support both formats in one class.
 */
public class UwbAddress {

    public static final int SHORT_ADDRESS_LENGTH = 2;
    public static final int EXTENDED_ADDRESS_LENGTH = 8;

    /** SHORT means 16-bit address EXTENDED means 64-bit address */
    public enum AddressingMode {
        SHORT,
        EXTENDED
    }

    private final AddressingMode mAddressingMode;
    private final byte[] mAddressBytes;

    private UwbAddress(AddressingMode mode, byte[] addressBytes) {
        this.mAddressingMode = mode;
        this.mAddressBytes = addressBytes;
    }

    /** 2 bytes will be converted to short address, 8 bytes to full */
    public static UwbAddress fromBytes(byte[] address) {
        if (address.length == SHORT_ADDRESS_LENGTH) {
            return new UwbAddress(AddressingMode.SHORT, address);
        }

        if (address.length == EXTENDED_ADDRESS_LENGTH) {
            return new UwbAddress(AddressingMode.EXTENDED, address);
        }

        throw new IllegalArgumentException(
                String.format(
                        "the address length only can be 2 bytes (SHORT) or 8 bytes (EXTENDED),"
                                + " passed in %d bytes",
                        address.length));
    }

    /** This method provides a way to convert short to/from short address bytes */
    public static UwbAddress fromShort(short address) {
        return new UwbAddress(AddressingMode.SHORT, Shorts.toByteArray(address));
    }

    /** Convert the short address to a short */
    public static short toShort(UwbAddress address) {
        if (address.getAddressingMode() != AddressingMode.SHORT) {
            throw new IllegalArgumentException();
        }

        return Shorts.fromByteArray(address.mAddressBytes);
    }

    /** This method provides a way to convert long to/from extended address bytes */
    public static UwbAddress fromLong(long address) {
        return new UwbAddress(AddressingMode.EXTENDED, Longs.toByteArray(address));
    }

    /** Convert the extended address to a long */
    public static long toLong(UwbAddress address) {
        if (address.getAddressingMode() != AddressingMode.EXTENDED) {
            throw new IllegalArgumentException();
        }

        return Longs.fromByteArray(address.mAddressBytes);
    }

    private static byte[] generateRandomByteArray(int len, SecureRandom secureRandom) {
        byte[] bytes = new byte[len];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    /** Get a randomized short address */
    public static UwbAddress getRandomizedShortAddress() {
        SecureRandom secureRandom = new SecureRandom();
        return fromBytes(generateRandomByteArray(SHORT_ADDRESS_LENGTH, secureRandom));
    }

    /** Get a randomized extended address */
    public static UwbAddress getRandomizedExtendedAddress() {
        SecureRandom secureRandom = new SecureRandom();
        return fromBytes(generateRandomByteArray(EXTENDED_ADDRESS_LENGTH, secureRandom));
    }

    public AddressingMode getAddressingMode() {
        return mAddressingMode;
    }

    /** Get the address byte array */
    public byte[] toBytes() {
        return mAddressBytes.clone();
    }

    /** How many bytes the address takes */
    public int size() {
        if (mAddressingMode == AddressingMode.SHORT) {
            return SHORT_ADDRESS_LENGTH;
        }

        return EXTENDED_ADDRESS_LENGTH;
    }

    /** return the address in hex format */
    public String toHexString() {
        StringBuilder stringBuilder = new StringBuilder("0X");
        for (byte b : mAddressBytes) {
            stringBuilder.append(String.format("%02X", b));
        }

        return stringBuilder.toString();
    }

    @NonNull
    @Override
    public String toString() {
        return toHexString();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof UwbAddress) {
            UwbAddress that = (UwbAddress) obj;
            return Objects.equal(mAddressingMode, that.getAddressingMode())
                    && Arrays.equals(mAddressBytes, that.toBytes());
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mAddressingMode, Arrays.hashCode(mAddressBytes));
    }
}
