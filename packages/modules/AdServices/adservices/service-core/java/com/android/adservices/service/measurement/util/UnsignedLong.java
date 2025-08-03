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
package com.android.adservices.service.measurement.util;

import android.annotation.NonNull;

import java.math.BigInteger;
import java.util.Objects;

/**
 * A type that stores a numeric value as a Long, but is meant for representation in strings and
 * math operations as an unsigned 64 bit integer, using the 64th bit as part of its magnitude.
 */
public class UnsignedLong implements Comparable<UnsignedLong> {
    private final Long mValue;

    public UnsignedLong(@NonNull Long value) {
        Validation.validateNonNull(value);
        mValue = value;
    }

    public UnsignedLong(@NonNull String value) {
        Validation.validateNonNull(value);
        mValue = Long.parseUnsignedLong(value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mValue);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof UnsignedLong)) {
            return false;
        }
        UnsignedLong other = (UnsignedLong) obj;
        return Objects.equals(mValue, other.getValue());
    }

    @Override
    public int compareTo(UnsignedLong other) {
        if (mValue >= 0L) {
            if (other.getValue() >= 0) {
                return Long.compare(mValue, other.getValue());
            } else {
                return -1;
            }
        } else {
            if (other.getValue() >= 0) {
                return 1;
            } else {
                return Long.compare(mValue, other.getValue());
            }
        }
    }

    @Override
    public String toString() {
        return Long.toUnsignedString(mValue);
    }

    public Long getValue() {
        return mValue;
    }

    /**
     * Returns the {@code BigInteger} mod operation of the unsigned value and modulus as an
     * {@code UnsignedLong}.
     */
    public UnsignedLong mod(int modulus) {
        BigInteger truncated = new BigInteger(Long.toUnsignedString(mValue))
                .mod(BigInteger.valueOf(modulus));
        return new UnsignedLong(truncated.toString());
    }
}
