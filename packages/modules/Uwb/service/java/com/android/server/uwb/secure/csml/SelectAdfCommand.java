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

import static com.android.server.uwb.secure.iso7816.Iso7816Constants.P1_SELECT_BY_DEDICATED_FILE_NAME;

import android.annotation.NonNull;

import com.android.server.uwb.secure.iso7816.StatusWord;
import com.android.server.uwb.secure.iso7816.TlvDatum;
import com.android.server.uwb.util.ObjectIdentifier;

import java.util.Arrays;
import java.util.List;

/**
 * SELECT ADF command APDU, see CSML 7.2.1.2
 * This is sent from the framework to FiRa applet locally, ignore the privacy protection bit.
 */
public class SelectAdfCommand extends FiRaCommand {
    private final ObjectIdentifier mAdfOid;

    private SelectAdfCommand(@NonNull ObjectIdentifier adfOid) {
        super();
        mAdfOid = adfOid;
    }

    @Override
    protected byte getIns() {
        return (byte) 0xA5;
    }

    @Override
    protected byte getP1() {
        return P1_SELECT_BY_DEDICATED_FILE_NAME;
    }

    @Override
    @NonNull
    protected StatusWord[] getExpectedSw() {
        return new StatusWord[] {
                StatusWord.SW_NO_ERROR,
                StatusWord.SW_APPLET_SELECT_FAILED,
                StatusWord.SW_FILE_NOT_FOUND,
                StatusWord.SW_INCORRECT_P1P2 };
    }

    @Override
    @NonNull
    protected List<TlvDatum> getTlvPayload() {
        return Arrays.asList(CsmlUtil.encodeObjectIdentifierAsTlv(mAdfOid));
    }

    /**
     * Builds the SelectAdfCommand.
     */
    @NonNull
    public static SelectAdfCommand build(ObjectIdentifier adfOid) {
        return new SelectAdfCommand(adfOid);
    }
}
