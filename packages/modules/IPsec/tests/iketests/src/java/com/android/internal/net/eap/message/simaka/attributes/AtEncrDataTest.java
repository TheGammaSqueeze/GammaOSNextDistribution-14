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
import static com.android.internal.net.eap.test.message.simaka.EapSimAkaAttribute.EAP_AT_ENCR_DATA;
import static com.android.internal.net.eap.test.message.simaka.attributes.EapTestAttributeDefinitions.IV_BYTES;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.internal.net.eap.test.message.simaka.EapSimAkaAttribute;
import com.android.internal.net.eap.test.message.simaka.EapSimAkaAttribute.AtEncrData;
import com.android.internal.net.eap.test.message.simaka.EapSimAkaAttributeFactory;

import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;

public class AtEncrDataTest {
    private static final int ATTR_HEADER_LEN = 4;

    private static final String DECRYPTED_DATA_HEX =
            "85120041344550432B4244455636754E7A54664C4A6547385162644E32426C406E61692E6570632E6D6"
                + "E633030312E6D63633530352E336770706E6574776F726B2E6F72670000000602000000000000";
    private static final String ENCRYPTED_DATA_HEX =
            "EFF4B2B5483FFC019E477542BB8D78F7A3CFF390A816BEF67DCD9EF693F535CE052F4D28DDCD9BD0C5A9"
                + "BBB049DE9D38CAA4F6075A99AF16917789CE7A5A4A6504B16B3CC52D47F271B3F6FF42138825";

    private static final byte[] DECRYPTED_DATA = hexStringToByteArray(DECRYPTED_DATA_HEX);
    private static final byte[] ENCRYPTED_DATA = hexStringToByteArray(ENCRYPTED_DATA_HEX);
    private static final byte[] AT_ENCR_DATA =
            hexStringToByteArray("82150000" + ENCRYPTED_DATA_HEX);
    private static final byte[] KEY_ENCR = hexStringToByteArray("17596683DD558E219ED55DA14CC39262");

    private EapSimAkaAttributeFactory mAttributeFactory;

    @Before
    public void setUp() throws Exception {
        mAttributeFactory = new EapSimAkaAttributeFactory() {};
    }

    @Test
    public void testDecode() throws Exception {
        ByteBuffer input = ByteBuffer.wrap(AT_ENCR_DATA);
        EapSimAkaAttribute result = mAttributeFactory.getAttribute(input);

        assertFalse(input.hasRemaining());
        assertTrue(result instanceof AtEncrData);
        AtEncrData atEncrData = (AtEncrData) result;
        assertEquals(EAP_AT_ENCR_DATA, atEncrData.attributeType);
        assertEquals(AT_ENCR_DATA.length, atEncrData.lengthInBytes);
        assertArrayEquals(ENCRYPTED_DATA, atEncrData.encrData);
    }

    @Test
    public void testEncryptAndEncode() throws Exception {
        AtEncrData atEncrData = new AtEncrData(DECRYPTED_DATA, KEY_ENCR, IV_BYTES);
        ByteBuffer result = ByteBuffer.allocate(DECRYPTED_DATA.length + ATTR_HEADER_LEN);
        atEncrData.encode(result);

        assertArrayEquals(AT_ENCR_DATA, result.array());
    }

    @Test
    public void testDecryptEncrData() throws Exception {
        ByteBuffer input = ByteBuffer.wrap(AT_ENCR_DATA);
        EapSimAkaAttribute result = mAttributeFactory.getAttribute(input);

        AtEncrData atEncrData = (AtEncrData) result;
        byte[] decryptedData = atEncrData.getDecryptedData(KEY_ENCR, IV_BYTES);
        assertArrayEquals(DECRYPTED_DATA, decryptedData);
    }
}
