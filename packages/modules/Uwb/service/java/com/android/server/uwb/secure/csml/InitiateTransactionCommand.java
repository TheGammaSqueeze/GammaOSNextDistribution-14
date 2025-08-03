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

import static com.android.server.uwb.util.Constants.UWB_SESSION_TYPE_MULTICAST;
import static com.android.server.uwb.util.Constants.UWB_SESSION_TYPE_UNICAST;

import android.annotation.NonNull;

import com.android.server.uwb.secure.iso7816.StatusWord;
import com.android.server.uwb.secure.iso7816.TlvDatum;
import com.android.server.uwb.secure.iso7816.TlvDatum.Tag;
import com.android.server.uwb.util.Constants.UwbSessionType;
import com.android.server.uwb.util.ObjectIdentifier;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Initiate Transaction Command APDU, see CSML 1.0 8.2.2.14.2.8
 */
public class InitiateTransactionCommand extends FiRaCommand {
    private static final Tag UWB_SESSION_ID_TAG = new Tag((byte) 0x80);

    @UwbSessionType
    private final int mUwbSessionType;

    @NonNull
    private final Optional<Integer> mUwbSessionId;

    @NonNull
    private final List<ObjectIdentifier> mAdfOids;

    private InitiateTransactionCommand(@NonNull List<ObjectIdentifier> adfOids,
            Optional<Integer> uwbSessionId) {
        super();
        this.mAdfOids = adfOids;
        this.mUwbSessionId = uwbSessionId;
        if (uwbSessionId.isPresent()) {
            mUwbSessionType = UWB_SESSION_TYPE_MULTICAST;
        } else {
            mUwbSessionType = UWB_SESSION_TYPE_UNICAST;
        }
    }

    @Override
    protected byte getIns() {
        return (byte) 0x12;
    }

    protected byte getP1() {
        if (mUwbSessionType == UWB_SESSION_TYPE_UNICAST) {
            return (byte) 0x00;
        } else {
            return (byte) 0x01;
        }
    }

    @Override
    @NonNull
    protected StatusWord[] getExpectedSw() {
        return new StatusWord[] {
                StatusWord.SW_NO_ERROR,
                StatusWord.SW_CONDITIONS_NOT_SATISFIED,
                StatusWord.SW_FUNCTION_NOT_SUPPORTED,
                StatusWord.SW_INCORRECT_P1P2 };
    }
    @Override
    @NonNull
    protected List<TlvDatum> getTlvPayload() {
        List<TlvDatum> tlvs = new ArrayList<>();
        mUwbSessionId.ifPresent(sessionId -> {
            TlvDatum sessionIdTlv = new TlvDatum(UWB_SESSION_ID_TAG, sessionId);
            tlvs.add(sessionIdTlv);
        });
        for (ObjectIdentifier adfOid : mAdfOids) {
            tlvs.add(CsmlUtil.encodeObjectIdentifierAsTlv(adfOid));
        }
        return tlvs;
    }

    /**
     * Build the InitiateTransactionCommand for unicast UWB session.
     */
    public static InitiateTransactionCommand buildForUnicast(List<ObjectIdentifier> adfOids) {
        Preconditions.checkArgument(adfOids.size() > 0);
        return new InitiateTransactionCommand(adfOids, Optional.empty());
    }

    /**
     * Build the InitiateTransactionCommand for multicast UWB session.
     */
    @NonNull
    public static InitiateTransactionCommand buildForMulticast(
            List<ObjectIdentifier> adfOids, int uwbSessionId) {
        Preconditions.checkArgument(adfOids.size() > 0);
        return new InitiateTransactionCommand(adfOids, Optional.of(uwbSessionId));
    }
}
