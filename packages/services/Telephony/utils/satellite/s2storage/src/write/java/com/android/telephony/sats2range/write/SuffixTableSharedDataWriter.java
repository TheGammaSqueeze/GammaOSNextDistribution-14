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

package com.android.telephony.sats2range.write;

import com.android.storage.io.write.TypedOutputStream;
import com.android.telephony.sats2range.read.SuffixTableSharedData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Converts a {@link SuffixTableSharedData} to a byte[] for writing.
 * See also {@link SuffixTableSharedData#fromBytes(byte[])}.
 */
public final class SuffixTableSharedDataWriter {

    private SuffixTableSharedDataWriter() {
    }

    /** Returns the byte[] for the supplied {@link SuffixTableSharedData} */
    public static byte[] toBytes(SuffixTableSharedData suffixTableSharedData) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                TypedOutputStream tos = new TypedOutputStream(baos)) {
            tos.writeInt(suffixTableSharedData.getTablePrefix());
            tos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
