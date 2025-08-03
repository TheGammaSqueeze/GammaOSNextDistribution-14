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

import java.util.Arrays;

public class UwbConfigStatusData {
    public final int status;
    public final int length;
    public final byte[] cgfStatus;

    public UwbConfigStatusData(int status, int length, byte[] cgfStatus) {
        this.status = status;
        this.length = length;
        this.cgfStatus = cgfStatus;
    }

    public int getStatus() {
        return status;
    }

    public int getLength() {
        return length;
    }

    public byte[] getCfgStatus() {
        return cgfStatus;
    }

    @Override
    public String toString() {
        return "UwbConfigStatusData { "
                + " status = " + status
                + " length = " + length
                + ", tlvs = [" + Arrays.toString(cgfStatus)
                + "] }";
    }
}
