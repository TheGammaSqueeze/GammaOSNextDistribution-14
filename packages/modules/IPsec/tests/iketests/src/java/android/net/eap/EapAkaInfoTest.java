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

package android.net.eap.test;

import static android.net.eap.test.EapSessionConfig.EapMethodConfig.EAP_TYPE_AKA;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class EapAkaInfoTest {
    private static final byte[] REAUTH_ID_BYTES =
        "4OLUpQCqFyhm1/UgD56anTzYTqJDckibqjU6PlS4sZaiuLc=".getBytes(StandardCharsets.UTF_8);

    @Test
    public void testBuild() {
        EapAkaInfo eapAkaInfo =
                new EapAkaInfo.Builder().setReauthId(REAUTH_ID_BYTES).build();

        assertEquals(EAP_TYPE_AKA, eapAkaInfo.getEapMethodType());
        assertArrayEquals(REAUTH_ID_BYTES, eapAkaInfo.getReauthId());
    }

    @Test
    public void testBuildNullReauthId() {
        EapAkaInfo eapAkaInfo =
                new EapAkaInfo.Builder().build();

        assertEquals(EAP_TYPE_AKA, eapAkaInfo.getEapMethodType());
        assertNull(eapAkaInfo.getReauthId());
    }
}
