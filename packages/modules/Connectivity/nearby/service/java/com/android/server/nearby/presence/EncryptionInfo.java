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

package com.android.server.nearby.presence;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.nearby.DataElement;

import androidx.annotation.NonNull;

import com.android.internal.util.Preconditions;
import com.android.server.nearby.util.ArrayUtils;

import java.util.Arrays;

/**
 * Data Element that indicates the presence of extended 16 bytes salt instead of 2 byte salt and to
 * indicate which encoding scheme (MIC tag or Signature) is used for a section. If present, it must
 * be the first DE in a section.
 */
public class EncryptionInfo {

    @IntDef({EncodingScheme.MIC, EncodingScheme.SIGNATURE})
    public @interface EncodingScheme {
        int MIC = 0;
        int SIGNATURE = 1;
    }

    // 1st byte : encryption scheme
    // 2nd to 17th bytes: salt
    public static final int ENCRYPTION_INFO_LENGTH = 17;
    private static final int ENCODING_SCHEME_MASK = 0b01111000;
    private static final int ENCODING_SCHEME_OFFSET = 3;
    @EncodingScheme
    private final int mEncodingScheme;
    private final byte[] mSalt;

    /**
     * Constructs a {@link DataElement}.
     */
    public EncryptionInfo(@NonNull byte[] value) {
        Preconditions.checkArgument(value.length == ENCRYPTION_INFO_LENGTH);
        mEncodingScheme = (value[0] & ENCODING_SCHEME_MASK) >> ENCODING_SCHEME_OFFSET;
        Preconditions.checkArgument(isValidEncodingScheme(mEncodingScheme));
        mSalt = Arrays.copyOfRange(value, 1, ENCRYPTION_INFO_LENGTH);
    }

    private boolean isValidEncodingScheme(int scheme) {
        return scheme == EncodingScheme.MIC || scheme == EncodingScheme.SIGNATURE;
    }

    @EncodingScheme
    public int getEncodingScheme() {
        return mEncodingScheme;
    }

    public byte[] getSalt() {
        return mSalt;
    }

    /** Combines the encoding scheme and salt to a byte array
     * that represents an {@link EncryptionInfo}.
     */
    @Nullable
    public static byte[] toByte(@EncodingScheme int encodingScheme, byte[] salt) {
        if (ArrayUtils.isEmpty(salt)) {
            return null;
        }
        if (salt.length != ENCRYPTION_INFO_LENGTH - 1) {
            return null;
        }
        byte schemeByte =
                (byte) ((encodingScheme << ENCODING_SCHEME_OFFSET) & ENCODING_SCHEME_MASK);
        return ArrayUtils.append(schemeByte, salt);
    }
}
