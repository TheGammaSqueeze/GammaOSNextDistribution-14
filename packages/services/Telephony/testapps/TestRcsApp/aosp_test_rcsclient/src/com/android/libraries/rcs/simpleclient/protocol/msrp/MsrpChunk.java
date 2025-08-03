/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.libraries.rcs.simpleclient.protocol.msrp;

import static com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpConstants.FLAG_ABORT_CHUNK;
import static com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpConstants.FLAG_LAST_CHUNK;
import static com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpConstants.FLAG_MORE_CHUNK;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

/**
 * Single MSRP chunk containing a request or a response.
 */
@AutoValue
public abstract class MsrpChunk {

    public static Builder newBuilder() {
        return new AutoValue_MsrpChunk.Builder()
                .method(Method.UNKNOWN)
                .responseCode(0)
                .responseReason("")
                .content(new byte[0])
                .continuation(Continuation.UNKNOWN);
    }

    public abstract Method method();

    public abstract String transactionId();

    public abstract Continuation continuation();

    public abstract int responseCode();

    public abstract String responseReason();

    public abstract ImmutableList<MsrpChunkHeader> headers();

    public abstract byte[] content();

    public MsrpChunkHeader header(String headerName) {
        for (MsrpChunkHeader header : headers()) {
            if (header.name().equals(headerName)) {
                return header;
            }
        }
        return null;
    }

    /**
     * Methods for requests
     */
    public enum Method {
        UNKNOWN,
        SEND,
        REPORT,
    }


    /**
     * Continuation flag for the chunk
     */
    public enum Continuation {
        UNKNOWN(0),
        COMPLETE(FLAG_LAST_CHUNK),
        MORE(FLAG_MORE_CHUNK),
        ABORTED(FLAG_ABORT_CHUNK);

        private final int value;

        Continuation(int value) {
            this.value = value;
        }

        public static Continuation valueOf(int read) {
            if (read == COMPLETE.value) {
                return COMPLETE;
            }
            if (read == MORE.value) {
                return MORE;
            }
            if (read == ABORTED.value) {
                return ABORTED;
            }
            return UNKNOWN;
        }

        public byte toByte() {
            return (byte) value;
        }
    }

    /**
     * Builder for new MSRP chunk.
     */
    @AutoValue.Builder
    public abstract static class Builder {

        public abstract Builder method(Method method);

        public abstract Builder transactionId(String id);

        public abstract String transactionId();

        public abstract Continuation continuation();

        public abstract Builder continuation(Continuation continuation);

        public abstract Builder responseCode(int continuation);

        public abstract Builder responseReason(String reason);

        public abstract Builder content(byte[] content);

        public Builder addHeader(MsrpChunkHeader header) {
            headersBuilder().add(header);
            return this;
        }

        public Builder addHeader(String name, String value) {
            headersBuilder().add(MsrpChunkHeader.newBuilder().name(name).value(value).build());
            return this;
        }

        abstract ImmutableList.Builder<MsrpChunkHeader> headersBuilder();

        MsrpChunkHeader header(String name) {
            for (MsrpChunkHeader header : headersBuilder().build()) {
                if (header.name().equals(name)) {
                    return header;
                }
            }
            return null;
        }

        public abstract MsrpChunk build();


    }
}
