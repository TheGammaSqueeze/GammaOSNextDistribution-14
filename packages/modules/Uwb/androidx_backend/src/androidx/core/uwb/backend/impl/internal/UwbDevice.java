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

import androidx.annotation.Nullable;

import java.util.Objects;

/** Represents a UWB device. */
public class UwbDevice {

    private final UwbAddress mAddress;

    /** Creates a new UwbDevice from a given address. */
    public static UwbDevice createForAddress(byte[] address) {
        return new UwbDevice(UwbAddress.fromBytes(address));
    }

    private UwbDevice(UwbAddress address) {
        this.mAddress = address;
    }

    /** The device address (eg, MAC address). */
    public UwbAddress getAddress() {
        return mAddress;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UwbDevice)) {
            return false;
        }
        UwbDevice uwbDevice = (UwbDevice) o;
        return Objects.equals(mAddress, uwbDevice.mAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mAddress);
    }

    @Override
    public String toString() {
        return String.format("UwbDevice {%s}", mAddress);
    }
}
