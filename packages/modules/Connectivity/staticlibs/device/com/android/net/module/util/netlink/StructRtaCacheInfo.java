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

package com.android.net.module.util.netlink;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.net.module.util.Struct;
import com.android.net.module.util.Struct.Field;
import com.android.net.module.util.Struct.Type;

import java.nio.ByteBuffer;

/**
 * struct rta_cacheinfo
 *
 * see also:
 *
 *     include/uapi/linux/rtnetlink.h
 *
 * @hide
 */
public class StructRtaCacheInfo extends Struct {
    // Already aligned.
    public static final int STRUCT_SIZE = 32;

    @Field(order = 0, type = Type.U32)
    public final long clntref;
    @Field(order = 1, type = Type.U32)
    public final long lastuse;
    @Field(order = 2, type = Type.S32)
    public final int expires;
    @Field(order = 3, type = Type.U32)
    public final long error;
    @Field(order = 4, type = Type.U32)
    public final long used;
    @Field(order = 5, type = Type.U32)
    public final long id;
    @Field(order = 6, type = Type.U32)
    public final long ts;
    @Field(order = 7, type = Type.U32)
    public final long tsage;

    StructRtaCacheInfo(long clntref, long lastuse, int expires, long error, long used, long id,
            long ts, long tsage) {
        this.clntref = clntref;
        this.lastuse = lastuse;
        this.expires = expires;
        this.error = error;
        this.used = used;
        this.id = id;
        this.ts = ts;
        this.tsage = tsage;
    }

    /**
     * Parse an rta_cacheinfo struct from a {@link ByteBuffer}.
     *
     * @param byteBuffer The buffer from which to parse the rta_cacheinfo.
     * @return the parsed rta_cacheinfo struct, or {@code null} if the rta_cacheinfo struct
     *         could not be parsed successfully (for example, if it was truncated).
     */
    @Nullable
    public static StructRtaCacheInfo parse(@NonNull final ByteBuffer byteBuffer) {
        if (byteBuffer.remaining() < STRUCT_SIZE) return null;

        // The ByteOrder must already have been set to native order.
        return Struct.parse(StructRtaCacheInfo.class, byteBuffer);
    }

    /**
     * Write a rta_cacheinfo struct to {@link ByteBuffer}.
     */
    public void pack(@NonNull final ByteBuffer byteBuffer) {
        // The ByteOrder must already have been set to native order.
        this.writeToByteBuffer(byteBuffer);
    }
}
