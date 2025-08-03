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

package com.android.internal.net.eap.test.message.simaka.attributes;

import static com.android.internal.net.eap.test.message.simaka.EapSimAkaAttribute.EAP_AT_NEXT_REAUTH_ID;
import static com.android.internal.net.eap.test.message.simaka.attributes.EapTestAttributeDefinitions.AT_NEXT_REAUTH_IDENTITY;
import static com.android.internal.net.eap.test.message.simaka.attributes.EapTestAttributeDefinitions.REAUTH_IDENTITY;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.internal.net.eap.test.message.simaka.EapSimAkaAttribute;
import com.android.internal.net.eap.test.message.simaka.EapSimAkaAttribute.AtNextReauthId;
import com.android.internal.net.eap.test.message.simaka.EapSimAkaAttributeFactory;

import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;

public class AtNextReauthIdTest {
    private static final int ATTR_HEADER_LEN = 4;

    private EapSimAkaAttributeFactory mAttributeFactory;

    @Before
    public void setUp() {
        mAttributeFactory = new EapSimAkaAttributeFactory() {};
    }

    @Test
    public void testDecode() throws Exception {
        ByteBuffer input = ByteBuffer.wrap(AT_NEXT_REAUTH_IDENTITY);
        EapSimAkaAttribute result = mAttributeFactory.getAttribute(input);

        assertFalse(input.hasRemaining());
        assertTrue(result instanceof AtNextReauthId);
        AtNextReauthId atNextReauthId = (AtNextReauthId) result;
        assertEquals(EAP_AT_NEXT_REAUTH_ID, atNextReauthId.attributeType);
        assertEquals(AT_NEXT_REAUTH_IDENTITY.length, atNextReauthId.lengthInBytes);
        assertArrayEquals(REAUTH_IDENTITY, atNextReauthId.reauthId);
    }

    @Test
    public void testEncode() throws Exception {
        AtNextReauthId atNextReauthId = AtNextReauthId.getAtNextReauthId(REAUTH_IDENTITY);
        ByteBuffer result = ByteBuffer.allocate(ATTR_HEADER_LEN + REAUTH_IDENTITY.length);
        atNextReauthId.encode(result);

        assertArrayEquals(AT_NEXT_REAUTH_IDENTITY, result.array());
    }
}
