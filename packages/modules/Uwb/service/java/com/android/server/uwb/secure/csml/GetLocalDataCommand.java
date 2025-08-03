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

import java.util.ArrayList;
import java.util.List;

/**
 * Get data command APDU, see CSML 1.0 8.2.2.14.4.1
 */
public class GetLocalDataCommand extends FiRaCommand {

    private final byte mP1;
    private final byte mP2;

    private GetLocalDataCommand(byte p1, byte p2) {
        super();
        mP1 = p1;
        mP2 = p2;
    }

    /**
     * Generates the instance of GetLocalDataCommand to get the data of the PA list.
     */
    @NonNull
    public static GetLocalDataCommand getPaListCommand() {
        return GetLocalDataCommand.build((byte) 0x00, (byte) 0xB0);
    }

    /**
     * Generates the instance of GetLocalDataCommand to get the data of
     * the FiRa applet certificates.
     */
    @NonNull
    public static GetLocalDataCommand getFiRaAppletCertificatesCommand() {
        return GetLocalDataCommand.build((byte) 0xBF, (byte) 0x21);
    }

    @Override
    protected byte getIns() {
        return (byte) 0xCA;
    }

    @Override
    protected byte getP1() {
        return mP1;
    }

    @Override
    protected byte getP2() {
        return mP2;
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
        return new ArrayList<TlvDatum>();
    }

    /**
     * Builds the GetLocalDataCommand.
     */
    @NonNull
    public static GetLocalDataCommand build(byte p1, byte p2) {
        return new GetLocalDataCommand(p1, p2);
    }
}
