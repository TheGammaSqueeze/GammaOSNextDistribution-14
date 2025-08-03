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

import static com.android.server.uwb.secure.iso7816.Iso7816Constants.CLA_PROPRIETARY;

import android.annotation.NonNull;

import com.android.server.uwb.secure.iso7816.CommandApdu;
import com.android.server.uwb.secure.iso7816.StatusWord;
import com.android.server.uwb.secure.iso7816.TlvDatum;
import com.android.server.uwb.secure.iso7816.TlvDatum.Tag;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * The base class of all C-APDU defined by FiRa.
 */
public abstract class FiRaCommand {
    public static final Tag FIRA_PROPRIETARY_COMMAND_TEMP_TAG =
            new Tag((byte) 0x71);

    protected FiRaCommand(){
    }

    protected byte getCla() {
        // logical channel number is not available. Use the default value.
        return CLA_PROPRIETARY;
    }

    protected abstract byte getIns();

    protected byte getP1() {
        return (byte) 0x00;
    }

    protected byte getP2() {
        return (byte) 0x00;
    }

    protected byte getLe() {
        return (byte) 0x00;
    }

    protected abstract StatusWord[] getExpectedSw();

    @NonNull
    protected abstract List<TlvDatum> getTlvPayload();

    @NonNull
    private byte[] buildPayload(@NonNull List<TlvDatum> tlvData) {
        if (tlvData.size() == 0) {
            return new byte[0];
        }
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
        try {
            for (TlvDatum tlv : tlvData) {
                dataOutputStream.write(tlv.toBytes());
            }

            dataOutputStream.flush();
        } catch (IOException e) {
            return new byte[0];
        }
        return byteArrayOutputStream.toByteArray();
    }

    /**
     * Converts the FiRa command to the CommandApdu of ISO7816-4.
     */
    @NonNull
    public CommandApdu getCommandApdu() {
        CommandApdu commandApdu = CommandApdu.builder(getCla(), getIns(), getP1(), getP2())
                .setCdata(buildPayload(getTlvPayload()))
                .setLe(getLe())
                .setExpected(getExpectedSw())
                .build();
        return commandApdu;
    }
}
