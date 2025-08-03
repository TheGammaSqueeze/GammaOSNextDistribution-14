/*
 * Copyright (C) 2023 The Android Open Source Project
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

package androidx.core.uwb.backend.impl.internal;

/** Ranging parameters provided by controlee when a controller adds a controlee. */
public class RangingControleeParameters {
    private final UwbAddress mAddress;
    private final int mSubSessionId;
    private final byte[] mSubSessionKey;

    public RangingControleeParameters(UwbAddress address, int subSessionId, byte[] subSessionKey) {
        this.mAddress = address;
        this.mSubSessionId = subSessionId;
        this.mSubSessionKey = subSessionKey;
    }

    public RangingControleeParameters(UwbAddress address) {
        this.mAddress = address;
        this.mSubSessionId = 0;
        this.mSubSessionKey = null;
    }

    public UwbAddress getAddress() {
        return mAddress;
    }

    public int getSubSessionId() {
        return mSubSessionId;
    }

    public byte[] getSubSessionKey() {
        return mSubSessionKey;
    }
}
