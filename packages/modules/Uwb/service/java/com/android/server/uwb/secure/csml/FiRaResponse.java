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

import static com.android.server.uwb.secure.iso7816.StatusWord.SW_NO_ERROR;

import com.android.server.uwb.secure.iso7816.StatusWord;
import com.android.server.uwb.secure.iso7816.TlvDatum.Tag;

/**
 * The base class of all responses for APDU commands defined by FiRa.
 */
public class FiRaResponse {
    public static final Tag PROPRIETARY_RESPONSE_TAG = new Tag((byte) 0x71);

    /**
     * The sw of APDU response.
     */
    public final StatusWord statusWord;

    protected FiRaResponse(int sw) {
        this.statusWord = StatusWord.fromInt(sw);
    }

    /**
     * Check if the APDU command is processed by the applet successfully.
     */
    public boolean isSuccess() {
        return statusWord.equals(SW_NO_ERROR);
    }

}
