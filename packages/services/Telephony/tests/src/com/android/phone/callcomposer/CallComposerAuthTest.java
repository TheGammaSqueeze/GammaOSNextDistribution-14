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

package com.android.phone.callcomposer;

import static org.junit.Assert.assertEquals;

import com.android.phone.callcomposer.DigestAuthUtils;

import org.junit.Test;

public class CallComposerAuthTest {
    @Test
    public void testResponseGeneration() {
        String username = "test1";
        String realm = "test@test.com";
        byte[] password = "12345678".getBytes();
        String sNonce = "aaaabbbbcccc";
        String cNonce = "ccccbbbbaaaa";
        String ncValue = "00000001";
        String method = "POST";
        String uri = "/test/test1?a=b";
        String qop = "auth";

        String response = DigestAuthUtils.computeResponse(sNonce, cNonce, qop, username,
                realm, password, method, uri);
        // precomputed response value from a known-good implementation
        assertEquals("744d63d6fb11aa132dc906ec95306960", response);
    }
}
