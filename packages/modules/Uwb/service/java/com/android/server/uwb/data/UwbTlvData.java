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
package com.android.server.uwb.data;

import com.android.server.uwb.util.UwbUtil;

public class UwbTlvData {
    public final int status;
    public final int length;
    public final byte[] tlvs;

    public UwbTlvData(int status, int length, byte[] tlvs) {
        this.status = status;
        this.length = length;
        this.tlvs = tlvs;
    }

    public int getStatus() {
        return status;
    }

    public int getLength() {
        return length;
    }

    public byte[] getTlv() {
        return tlvs;
    }

    @Override
    public String toString() {
        return "UwbTlvData { "
                + " status = " + status
                + " length = " + length
                + ", tlvs = [" + UwbUtil.toHexString(tlvs)
                + "] }";
    }
}
