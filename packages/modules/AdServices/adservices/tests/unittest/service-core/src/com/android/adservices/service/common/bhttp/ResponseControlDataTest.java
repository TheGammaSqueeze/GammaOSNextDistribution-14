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

package com.android.adservices.service.common.bhttp;

import static com.android.adservices.service.common.bhttp.BinaryHttpTestUtil.combineSections;
import static com.android.adservices.service.common.bhttp.Frc9000VariableLengthIntegerUtil.toFrc9000Int;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ResponseControlDataTest {

    @Test
    public void testNewAndDeserialize_normal() {
        ResponseControlData responseControlData =
                ResponseControlData.builder()
                        .setFinalStatusCode(200)
                        .addInformativeResponse(
                                InformativeResponse.builder()
                                        .setInformativeStatusCode(102)
                                        .appendHeaderField("Running", "\"sleep 15\"")
                                        .build())
                        .addInformativeResponse(
                                InformativeResponse.builder()
                                        .setInformativeStatusCode(103)
                                        .appendHeaderField(
                                                "Link", "</style.css>; rel=preload;" + " as=style")
                                        .appendHeaderField(
                                                "Link", "</script.js>; rel=preload;" + " as=script")
                                        .build())
                        .build();
        byte[] bytes = combineSections(responseControlData.knownLengthSerialize());

        assertEquals(
                responseControlData,
                BinaryHttpMessageDeserializer.deserializeKnownLengthResponseControlData(
                        new BinaryHttpMessageDeserializer.BinaryHttpByteArrayReader(
                                combineSections(new byte[1], bytes))));
    }

    @Test
    public void testNew_InvalidFinalResponseCode() {
        Exception e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ResponseControlData.builder().setFinalStatusCode(180).build());
        assertTrue(e.getMessage().contains("status code"));
    }

    @Test
    public void testBadInformationalResponseCode() {
        Exception e1 =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> InformativeResponse.builder().setInformativeStatusCode(50).build());
        assertTrue(e1.getMessage().contains("status code"));
        Exception e2 =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> InformativeResponse.builder().setInformativeStatusCode(300).build());
        assertTrue(e2.getMessage().contains("status code"));
    }

    @Test
    public void testDeserializeInvalidResponseCode() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        BinaryHttpMessageDeserializer.deserializeKnownLengthResponseControlData(
                                new BinaryHttpMessageDeserializer.BinaryHttpByteArrayReader(
                                        combineSections(
                                                new byte[] {1},
                                                toFrc9000Int(50),
                                                toFrc9000Int(0)))));
    }
}
