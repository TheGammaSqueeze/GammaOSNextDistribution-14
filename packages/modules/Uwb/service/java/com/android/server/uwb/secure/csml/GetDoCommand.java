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

import java.util.Arrays;
import java.util.List;

/**
 * Get data object command APDU, see CSML 7.2.1.5
 */
public class GetDoCommand extends FiRaCommand {
    @NonNull
    private final TlvDatum mQueryDataObject;

    private GetDoCommand(@NonNull TlvDatum queryDataObject) {
        super();
        mQueryDataObject = queryDataObject;
    }

    @Override
    protected byte getCla() {
        return (byte) 0x00;
    }

    @Override
    protected byte getIns() {
        return (byte) 0xCB;
    }

    @Override
    protected byte getP1() {
        return (byte) 0x3F;
    }

    @Override
    protected byte getP2() {
        return (byte) 0xFF;
    }

    @Override
    @NonNull
    protected StatusWord[] getExpectedSw() {
        return new StatusWord[] {
                StatusWord.SW_NO_ERROR,
                StatusWord.SW_SECURITY_STATUS_NOT_SATISFIED,
                StatusWord.SW_WRONG_DATA,
                StatusWord.SW_INCORRECT_P1P2 };
    }

    @Override
    @NonNull
    protected List<TlvDatum> getTlvPayload() {
        return Arrays.asList(mQueryDataObject);
    }

    /**
     * Builds the GetDoCommand.
     */
    @NonNull
    public static GetDoCommand build(@NonNull TlvDatum queryDataObject) {
        return new GetDoCommand(queryDataObject);
    }
}
