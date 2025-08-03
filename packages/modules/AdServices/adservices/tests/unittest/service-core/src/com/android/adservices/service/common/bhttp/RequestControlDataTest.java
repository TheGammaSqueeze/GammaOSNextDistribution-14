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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class RequestControlDataTest {
    private static final byte[] METHOD = new byte[] {'G', 'E', 'T'};
    private static final byte[] SCHEME = new byte[] {'h', 't', 't', 'p', 's'};
    private static final byte[] AUTHORITY =
            new byte[] {'w', 'w', 'w', '.', 'a', '.', 'c', 'o', 'm'};
    private static final byte[] PATH = new byte[] {'/', 'p', 'a', 't', 'h'};

    @Test
    public void testEncodeAndDecode_normal() {
        RequestControlData requestControlData =
                RequestControlData.builder()
                        .setMethod(new String(METHOD))
                        .setScheme(new String(SCHEME))
                        .setAuthority(new String(AUTHORITY))
                        .setPath(new String(PATH))
                        .build();

        assertArrayEquals(
                new byte[][] {
                    toFrc9000Int(METHOD.length),
                    METHOD,
                    toFrc9000Int(SCHEME.length),
                    SCHEME,
                    toFrc9000Int(AUTHORITY.length),
                    AUTHORITY,
                    toFrc9000Int(PATH.length),
                    PATH
                },
                requestControlData.knownLengthSerialize());

        assertEquals(
                BinaryHttpMessageDeserializer.deserializeKnownLengthRequestControlData(
                        new BinaryHttpMessageDeserializer.BinaryHttpByteArrayReader(
                                combineSections(
                                        new byte[] {0},
                                        toFrc9000Int(METHOD.length),
                                        METHOD,
                                        toFrc9000Int(SCHEME.length),
                                        SCHEME,
                                        toFrc9000Int(AUTHORITY.length),
                                        AUTHORITY,
                                        toFrc9000Int(PATH.length),
                                        PATH))),
                requestControlData);
    }

    @Test
    public void deserialize_NotEnoughData() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        BinaryHttpMessageDeserializer.deserializeKnownLengthRequestControlData(
                                new BinaryHttpMessageDeserializer.BinaryHttpByteArrayReader(
                                        combineSections(
                                                new byte[] {0},
                                                toFrc9000Int(METHOD.length),
                                                METHOD,
                                                toFrc9000Int(SCHEME.length),
                                                SCHEME,
                                                toFrc9000Int(AUTHORITY.length),
                                                AUTHORITY))));
    }
}
