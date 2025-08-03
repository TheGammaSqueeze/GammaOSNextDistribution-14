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

import java.util.Arrays;
import java.util.List;

/**
 * Tunnel command APDU, see CSML 8.2.2.14.2.7.
 * Command is used to send the payload to remote device through the secure channel.
 */
public class TunnelCommand extends FiRaCommand {
    private static final Tag TUNNEL_DATA_TAG = new Tag((byte) 0x81);

    @NonNull
    private final byte[] mTunnelData;

    private TunnelCommand(@NonNull byte[] tunnelData) {
        super();
        mTunnelData = tunnelData;
    }

    @Override
    protected byte getIns() {
        return (byte) 0x14;
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
        return Arrays.asList(
                new TlvDatum(FIRA_PROPRIETARY_COMMAND_TEMP_TAG,
                        new TlvDatum(TUNNEL_DATA_TAG, mTunnelData)));
    }

    /**
     * Builds the TunnelCommand.
     */
    @NonNull
    public static TunnelCommand build(@NonNull byte[] tunnelData) {
        return new TunnelCommand(tunnelData);
    }
}
