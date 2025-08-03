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

import static com.android.internal.net.TestUtils.hexStringToByteArray;
import static com.android.internal.net.eap.test.message.simaka.EapSimAkaAttribute.EAP_AT_IV;
import static com.android.internal.net.eap.test.message.simaka.attributes.EapTestAttributeDefinitions.AT_IV;
import static com.android.internal.net.eap.test.message.simaka.attributes.EapTestAttributeDefinitions.AT_IV_INVALID_LENGTH;
import static com.android.internal.net.eap.test.message.simaka.attributes.EapTestAttributeDefinitions.IV;
import static com.android.internal.net.eap.test.message.simaka.attributes.EapTestAttributeDefinitions.IV_BYTES;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.android.internal.net.eap.test.exceptions.simaka.EapSimAkaInvalidAttributeException;
import com.android.internal.net.eap.test.message.simaka.EapSimAkaAttribute;
import com.android.internal.net.eap.test.message.simaka.EapSimAkaAttribute.AtIv;
import com.android.internal.net.eap.test.message.simaka.EapSimAkaAttributeFactory;

import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.security.SecureRandom;

public class AtIvTest {
    private static final byte[] INVALID_IV = new byte[10];
    private static final int EXPECTED_LENGTH = 20;
    private static final int EXPECTED_IV_LENGTH = 16;

    private EapSimAkaAttributeFactory mAttributeFactory;
    private SecureRandom mMockSecureRandom;

    @Before
    public void setUp() {
        mAttributeFactory = new EapSimAkaAttributeFactory() {};
        mMockSecureRandom = mock(SecureRandom.class);
    }

    @Test
    public void testEncode() throws Exception {
        doAnswer(
                invocation -> {
                    byte[] dst = invocation.getArgument(0);
                    System.arraycopy(IV_BYTES, 0, dst, 0, IV_BYTES.length);
                    return null;
                })
                .when(mMockSecureRandom)
                .nextBytes(eq(new byte[IV_BYTES.length]));
        AtIv atIv = new AtIv(mMockSecureRandom);

        ByteBuffer result = ByteBuffer.allocate(EXPECTED_LENGTH);
        atIv.encode(result);
        assertArrayEquals(IV_BYTES, atIv.iv);
        assertArrayEquals(AT_IV, result.array());
    }

    @Test
    public void testDecode() throws Exception {
        ByteBuffer input = ByteBuffer.wrap(AT_IV);
        EapSimAkaAttribute result = mAttributeFactory.getAttribute(input);

        assertFalse(input.hasRemaining());
        assertTrue(result instanceof AtIv);
        AtIv atIv = (AtIv) result;
        assertEquals(EAP_AT_IV, atIv.attributeType);
        assertEquals(EXPECTED_LENGTH, atIv.lengthInBytes);
        assertArrayEquals(hexStringToByteArray(IV), atIv.iv);
    }

    @Test
    public void testDecodeInvalidLength() throws Exception {
        ByteBuffer input = ByteBuffer.wrap(AT_IV_INVALID_LENGTH);
        try {
            mAttributeFactory.getAttribute(input);
            fail("Expected EapSimAkaInvalidAttributeException for invalid length");
        } catch (EapSimAkaInvalidAttributeException expected) {
        }
    }
}
