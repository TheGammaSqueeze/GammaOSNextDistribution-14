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
import com.android.server.uwb.util.ObjectIdentifier;

import java.util.Arrays;
import java.util.List;

// TODO: this is customized, need to make it be standardized in CSML
/**
 * Delete ADF C-APDU.
 */
public class DeleteAdfCommand extends FiRaCommand {
    private final ObjectIdentifier mAdfOid;

    private DeleteAdfCommand(@NonNull ObjectIdentifier adfOid) {
        super();
        this.mAdfOid = adfOid;
    }

    @Override
    protected byte getIns() {
        return (byte) 0xE4;
    }

    @Override
    @NonNull
    protected StatusWord[] getExpectedSw() {
        return new StatusWord[] {
                StatusWord.SW_NO_ERROR,
                StatusWord.SW_WARNING_STATE_UNCHANGED, // OID not found,
                StatusWord.SW_WRONG_LENGTH,
                StatusWord.SW_CONDITIONS_NOT_SATISFIED,
                StatusWord.SW_FUNCTION_NOT_SUPPORTED,
                StatusWord.SW_WRONG_DATA,
                StatusWord.SW_INCORRECT_P1P2 };
    }

    @Override
    @NonNull
    protected List<TlvDatum> getTlvPayload() {
        return Arrays.asList(CsmlUtil.encodeObjectIdentifierAsTlv(mAdfOid));
    }

    /**
     * Builds the APDU command of DeleteAdfCommand.
     */
    @NonNull
    public static DeleteAdfCommand build(@NonNull ObjectIdentifier adfOid) {
        return new DeleteAdfCommand(adfOid);
    }
}
