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

import static com.google.common.truth.Truth.assertThat;

import static java.nio.charset.StandardCharsets.UTF_8;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpChunk.Continuation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MsrpChunkTest {

    private static MsrpChunk writeAndReadChunk(MsrpChunk chunk) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        MsrpSerializer.serialize(bo, chunk);

        ByteArrayInputStream bi = new ByteArrayInputStream(bo.toByteArray());
        return MsrpParser.parse(bi);
    }

    @Test
    public void whenSerializeParseRequest_success() throws IOException {
        MsrpChunk chunk = MsrpChunk.newBuilder()
                .method(MsrpChunk.Method.SEND)
                .addHeader("To-Path", "msrp://123.1.11:9/testreceiver;tcp")
                .addHeader("From-Path", "msrp://123.1.11:9/testsender;tcp")
                .addHeader("Byte-Range", "1-*/*")
                .transactionId("123123")
                .addHeader("Content-Type", "text/plain")
                .content("Hallo Welt\r\n".getBytes(UTF_8))
                .continuation(Continuation.COMPLETE)
                .build();

        MsrpChunk chunk2 = writeAndReadChunk(chunk);

        assertThat(chunk2).isEqualTo(chunk);
    }

    @Test
    public void whenSerializeParseEmptyRequest_success() throws IOException {
        MsrpChunk chunk = MsrpChunk.newBuilder()
                .method(MsrpChunk.Method.SEND)
                .transactionId("testtransaction")
                .addHeader("To-Path", "msrp://123.1.11:9/testreceiver;tcp")
                .addHeader("From-Path", "msrp://123.1.11:9/testsender;tcp")
                .continuation(Continuation.COMPLETE)
                .build();

        MsrpChunk chunk2 = writeAndReadChunk(chunk);

        assertThat(chunk2).isEqualTo(chunk);
    }

    @Test
    public void whenSerializeParseResponse_success() throws IOException {
        MsrpChunk chunk = MsrpChunk.newBuilder()
                .responseCode(200)
                .responseReason("OK")
                .transactionId("testtransaction")
                .addHeader("To-Path", "msrp://123.1.11:9/testreceiver;tcp")
                .addHeader("From-Path", "msrp://123.1.11:9/testsender;tcp")
                .continuation(Continuation.COMPLETE)
                .build();

        MsrpChunk chunk2 = writeAndReadChunk(chunk);

        assertThat(chunk2).isEqualTo(chunk);
    }

}
