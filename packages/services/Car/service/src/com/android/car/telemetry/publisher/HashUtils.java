/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.telemetry.publisher;

import android.annotation.NonNull;

import com.android.internal.util.Preconditions;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for computing hash code.
 *
 * <p>Most of the methods are copied from {@code external/guava/}.
 */
public class HashUtils {
    private static final long M = 0xC6A4A7935BD1E995L;
    private static final int R = 47;
    private static final long SEED = 0xDECAFCAFFEL;

    /**
     * Returns the hash code of the given string using SHA-256 algorithm. Returns only the first
     * 8 bytes if the hash code, as SHA-256 is uniformly distributed.
     */
    public static long sha256(@NonNull String data) {
        try {
            return asLong(MessageDigest.getInstance("SHA-256").digest(data.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            // unreachable
            throw new RuntimeException("SHA-256 algorithm not found.", e);
        }
    }

    /**
     * Returns the Murmur2 hash of the provided string.
     *
     * <p> This algorithm works the same way as Hash64() in
     * packages/modules/StatsD/statsd/src/hash.h
     *
     * @param str the string to be hashed.
     * @return hash of the string.
     */
    public static long murmur2Hash64(@NonNull String str) {
        final byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        long h = SEED ^ (buf.remaining() * M);
        while (buf.remaining() >= 8) {
            long k = buf.getLong();
            k *= M;
            k ^= k >>> R;
            k *= M;
            h ^= k;
            h *= M;
        }

        if (buf.hasRemaining()) {
            for (int i = 0; buf.hasRemaining(); i += 8) {
                h ^= (buf.get() & 0xFFL) << i;
            }
            h *= M;
        }

        h ^= h >>> R;
        h *= M;
        h ^= h >>> R;

        return h;
    }

    /**
     * Returns the first eight bytes of {@code hashCode}, converted to a {@code long} value in
     * little-endian order.
     *
     * <p>Copied from Guava's {@code HashCode#asLong()}.
     *
     * @throws IllegalStateException if {@code hashCode bytes < 8}
     */
    private static long asLong(@NonNull byte[] hashCode) {
        Preconditions.checkState(hashCode.length >= 8, "requires >= 8 bytes (it only has %s bytes)",
                hashCode.length);
        long retVal = (hashCode[0] & 0xFF);
        for (int i = 1; i < Math.min(hashCode.length, 8); i++) {
            retVal |= (hashCode[i] & 0xFFL) << (i * 8);
        }
        return retVal;
    }
}
