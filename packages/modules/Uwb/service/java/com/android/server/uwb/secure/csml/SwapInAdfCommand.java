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

package com.android.server.uwb.secure.csml;

import android.annotation.NonNull;

import com.android.server.uwb.secure.iso7816.StatusWord;
import com.android.server.uwb.secure.iso7816.TlvDatum;
import com.android.server.uwb.secure.iso7816.TlvDatum.Tag;
import com.android.server.uwb.util.ObjectIdentifier;

import java.util.Arrays;
import java.util.List;

/**
 * Swap in the secure blob for imported ADF used by dynamic STS. static STS is not supported.
 */
public class SwapInAdfCommand extends FiRaCommand {
    private static final Tag SECURE_BLOB_TAG = new Tag((byte) 0xDF, (byte) 0x51);
    private static final Tag UWB_CONTROLEE_INFO_TAG = new Tag((byte) 0xBF, (byte) 0x70);

    // the secure blob should have OID and its ADF contents.
    @NonNull
    private final byte[] mSecureBlob;

    @NonNull
    private final ObjectIdentifier mOid;

    @NonNull
    private final byte[] mUwbControleeInfo;

    private SwapInAdfCommand(@NonNull byte[] secureBlob, @NonNull ObjectIdentifier oid,
            @NonNull byte[] uwbControleeInfo) {
        super();
        mSecureBlob = secureBlob;
        mOid = oid;
        mUwbControleeInfo = uwbControleeInfo;
    }

    @Override
    protected byte getIns() {
        return (byte) 0x40;
    }

    @Override
    protected byte getP1() {
        // acquire slot
        return (byte) 0x00;
    }

    @Override
    @NonNull
    protected StatusWord[] getExpectedSw() {
        return new StatusWord[] {
                StatusWord.SW_NO_ERROR,
                StatusWord.SW_WRONG_LENGTH,
                StatusWord.SW_CONDITIONS_NOT_SATISFIED,
                StatusWord.SW_FILE_NOT_FOUND,
                StatusWord.SW_NOT_ENOUGH_MEMORY,
                StatusWord.SW_INCORRECT_P1P2 };
    }

    @Override
    @NonNull
    protected List<TlvDatum> getTlvPayload() {
        return Arrays.asList(new TlvDatum(SECURE_BLOB_TAG, mSecureBlob),
                CsmlUtil.encodeObjectIdentifierAsTlv(mOid),
                new TlvDatum(UWB_CONTROLEE_INFO_TAG, mUwbControleeInfo));
    }

    /**
     * Builds the SwapInAdfCommand.
     */
    @NonNull
    public static SwapInAdfCommand build(@NonNull byte[] secureBlob, @NonNull ObjectIdentifier oid,
            @NonNull byte[] uwbControleeInfo) {
        return new SwapInAdfCommand(secureBlob, oid, uwbControleeInfo);
    }
}
