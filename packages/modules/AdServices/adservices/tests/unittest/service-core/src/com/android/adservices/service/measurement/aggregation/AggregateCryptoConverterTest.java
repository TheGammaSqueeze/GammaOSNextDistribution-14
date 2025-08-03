/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.measurement.aggregation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.filters.SmallTest;

import com.android.adservices.HpkeJni;
import com.android.adservices.service.exception.CryptoException;
import com.android.adservices.service.measurement.PrivacyParams;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnicodeString;

@SmallTest
public class AggregateCryptoConverterTest {
    private static final String PLAIN_TEXT = "plain_text";
    private static final String SHARED_INFO = "{\"shared_info\":\"example\"}";
    private static final String DEFAULT_PAYLOAD =
            "{\"operation\":\"histogram\", "
                    + "\"data\": ["
                    + "{\"bucket\": \"1\", \"value\":2},"
                    + "{\"bucket\": \"3\", \"value\":4}"
                    + "]}";

    private static final String PADDED_PAYLOAD1 =
            "{\"operation\":\"histogram\", "
                    + "\"data\": ["
                    + "{\"bucket\": \"1\", \"value\":2},"
                    + "{\"bucket\": \"3\", \"value\":4},"
                    + "{\"bucket\": \"0\", \"value\":0},"
                    + "{\"bucket\": \"0\", \"value\":0},"
                    + "{\"bucket\": \"0\", \"value\":0}"
                    + "]}";

    private static final String PADDED_PAYLOAD2 =
            "{\"operation\":\"histogram\", "
                    + "\"data\": ["
                    + "{\"bucket\": \"1\", \"value\":2},"
                    + "{\"bucket\": \"3\", \"value\":4},"
                    + "{\"bucket\": \"5\", \"value\":6},"
                    + "{\"bucket\": \"0\", \"value\":0},"
                    + "{\"bucket\": \"0\", \"value\":0}"
                    + "]}";

    @Test
    public void testEncrypt_successfully() throws Exception {
        String result =
                AggregateCryptoConverter.encrypt(
                        AggregateCryptoFixture.getPublicKeyBase64(), DEFAULT_PAYLOAD, SHARED_INFO);
        assertNotNull(result);
        assertEncryptedPayload(result, SHARED_INFO);
    }

    @Test
    public void testEncrypt_payloadPadding_sameSize() throws Exception {
        String result1 =
                AggregateCryptoConverter.encrypt(
                        AggregateCryptoFixture.getPublicKeyBase64(), PADDED_PAYLOAD1, SHARED_INFO);
        String result2 =
                AggregateCryptoConverter.encrypt(
                        AggregateCryptoFixture.getPublicKeyBase64(), PADDED_PAYLOAD2, SHARED_INFO);
        assertEquals(result1.length(), result2.length());
    }

    @Test
    public void testEncrypt_sharedInfoEmpty_success() throws Exception {
        String result =
                AggregateCryptoConverter.encrypt(
                        AggregateCryptoFixture.getPublicKeyBase64(), DEFAULT_PAYLOAD, null);
        assertNotNull(result);
        assertEncryptedPayload(result, "");
    }

    @Test
    public void testEncrypt_sharedInfoNull_success() throws Exception {
        String result =
                AggregateCryptoConverter.encrypt(
                        AggregateCryptoFixture.getPublicKeyBase64(), DEFAULT_PAYLOAD, null);
        assertNotNull(result);
        assertEncryptedPayload(result, null);
    }

    @Test
    public void testEncrypt_invalidHistogramMalformed() {
        try {
            AggregateCryptoConverter.encrypt(
                    AggregateCryptoFixture.getPublicKeyBase64(),
                    "{{\"operation\":\"histogram\", "
                            + "\"data\": [{"
                            + "\"bucket\": \"1\", \"value\":2"
                            + "}]}",
                    null);
            fail();
        } catch (CryptoException e) {
            // succeed
        }
    }

    @Test
    public void testEncrypt_invalidHistogramMissingOperation() {
        try {
            AggregateCryptoConverter.encrypt(
                    AggregateCryptoFixture.getPublicKeyBase64(),
                    "{{\"ops\":\"histogram\", "
                            + "\"data\": [{"
                            + "\"bucket\": \"1\", \"value\":2"
                            + "}]}",
                    null);
            fail();
        } catch (CryptoException e) {
            // succeed
        }
    }

    @Test
    public void testEncrypt_invalidHistogramMissingData() {
        try {
            AggregateCryptoConverter.encrypt(
                    AggregateCryptoFixture.getPublicKeyBase64(),
                    "{{\"operation\":\"histogram\", "
                            + "\"info\": [{"
                            + "\"bucket\": \"1\", \"value\":2"
                            + "}]}",
                    null);
            fail();
        } catch (CryptoException e) {
            // succeed
        }
    }

    @Test
    public void testEncrypt_invalidHistogramMissingValue() {
        try {
            AggregateCryptoConverter.encrypt(
                    AggregateCryptoFixture.getPublicKeyBase64(),
                    "{{\"operation\":\"histogram\", "
                            + "\"data\": [{"
                            + "\"bucket\": \"1\", \"v\":2"
                            + "}]}",
                    null);
            fail();
        } catch (CryptoException e) {
            // succeed
        }
    }

    @Test
    public void testEncrypt_invalidHistogramMissingBucket() {
        try {
            AggregateCryptoConverter.encrypt(
                    AggregateCryptoFixture.getPublicKeyBase64(),
                    "{{\"operation\":\"histogram\", "
                            + "\"data\": [{"
                            + "\"b\": 1, \"value\":2"
                            + "}]}",
                    null);
            fail();
        } catch (CryptoException e) {
            // succeed
        }
    }

    @Test
    public void testEncode_successfully() throws Exception {
        String result = AggregateCryptoConverter.encode(DEFAULT_PAYLOAD);
        assertNotNull(result);
        assertEncodedPayload(result);
    }

    @Test
    public void testEncode_invalidHistogramMalformed() {
        try {
            AggregateCryptoConverter.encode(
                    "{{\"operation\":\"histogram\", "
                            + "\"data\": [{"
                            + "\"bucket\": \"1\", \"value\":2"
                            + "}]}");
            fail();
        } catch (CryptoException e) {
            // succeed
        }
    }

    @Test
    public void testEncode_invalidHistogramMissingOperation() {
        try {
            AggregateCryptoConverter.encode(
                    "{{\"ops\":\"histogram\", "
                            + "\"data\": [{"
                            + "\"bucket\": \"1\", \"value\":2"
                            + "}]}");
            fail();
        } catch (CryptoException e) {
            // succeed
        }
    }

    @Test
    public void testEncode_invalidHistogramMissingData() {
        try {
            AggregateCryptoConverter.encode(
                    "{{\"operation\":\"histogram\", "
                            + "\"info\": [{"
                            + "\"bucket\": \"1\", \"value\":2"
                            + "}]}");
            fail();
        } catch (CryptoException e) {
            // succeed
        }
    }

    @Test
    public void testEncode_invalidHistogramMissingValue() {
        try {
            AggregateCryptoConverter.encode(
                    "{{\"operation\":\"histogram\", "
                            + "\"data\": [{"
                            + "\"bucket\": \"1\", \"v\":2"
                            + "}]}");
            fail();
        } catch (CryptoException e) {
            // succeed
        }
    }

    @Test
    public void testEncode_invalidHistogramMissingBucket() {
        try {
            AggregateCryptoConverter.encode(
                    "{{\"operation\":\"histogram\", "
                            + "\"data\": [{"
                            + "\"b\": 1, \"value\":2"
                            + "}]}");
            fail();
        } catch (CryptoException e) {
            // succeed
        }
    }

    @Test
    public void testEncodeWithCbor_successfully() throws Exception {
        final List<AggregateHistogramContribution> contributions = new ArrayList<>();
        final AggregateHistogramContribution firstContribution =
                new AggregateHistogramContribution.Builder()
                        .setKey(new BigInteger("1"))
                        .setValue(2)
                        .build();
        final AggregateHistogramContribution secondContribution =
                new AggregateHistogramContribution.Builder()
                        .setKey(new BigInteger("3"))
                        .setValue(4)
                        .build();
        contributions.add(firstContribution);
        contributions.add(secondContribution);

        final byte[] encoded = AggregateCryptoConverter.encodeWithCbor(contributions);
        final List<DataItem> dataItems =
                new CborDecoder(new ByteArrayInputStream(encoded)).decode();

        final Map payload = (Map) dataItems.get(0);
        final Array payloadArray = (Array) payload.get(new UnicodeString("data"));

        assertEquals(2, payloadArray.getDataItems().size());
        assertEquals("histogram", payload.get(new UnicodeString("operation")).toString());
        assertTrue(
                payloadArray.getDataItems().stream()
                        .anyMatch(
                                i ->
                                        isFound((Map) i, "bucket", "1")
                                                && isFound((Map) i, "value", "2")));

        assertTrue(
                payloadArray.getDataItems().stream()
                        .anyMatch(
                                i ->
                                        isFound((Map) i, "bucket", "3")
                                                && isFound((Map) i, "value", "4")));
    }

    @Test
    public void testEncodeWithCbor_differentSizesShouldMatchUpperBound() throws Exception {
        final List<AggregateHistogramContribution> contributions = new ArrayList<>();
        final AggregateHistogramContribution firstContribution =
                new AggregateHistogramContribution.Builder()
                        .setKey(new BigInteger("1"))
                        .setValue(1)
                        .build();
        final AggregateHistogramContribution secondContribution =
                new AggregateHistogramContribution.Builder()
                        .setKey(new BigInteger("1329227995784915872903807060280344576"))
                        .setValue(Integer.MAX_VALUE)
                        .build();
        contributions.add(firstContribution);
        contributions.add(secondContribution);

        final byte[] encoded = AggregateCryptoConverter.encodeWithCbor(contributions);
        final List<DataItem> dataItems =
                new CborDecoder(new ByteArrayInputStream(encoded)).decode();

        final Map payload = (Map) dataItems.get(0);
        final Array payloadArray = (Array) payload.get(new UnicodeString("data"));

        assertEquals(2, payloadArray.getDataItems().size());
        assertEquals(
                PrivacyParams.AGGREGATE_HISTOGRAM_BUCKET_BYTE_SIZE,
                getBytesLength((Map) payloadArray.getDataItems().get(0), "bucket"));
        assertEquals(
                getBytesLength((Map) payloadArray.getDataItems().get(0), "bucket"),
                getBytesLength((Map) payloadArray.getDataItems().get(1), "bucket"));

        assertEquals(
                PrivacyParams.AGGREGATE_HISTOGRAM_VALUE_BYTE_SIZE,
                getBytesLength((Map) payloadArray.getDataItems().get(0), "value"));
        assertEquals(
                getBytesLength((Map) payloadArray.getDataItems().get(0), "value"),
                getBytesLength((Map) payloadArray.getDataItems().get(1), "value"));
    }

    @Test
    public void testEncodeWithCbor_valuesAtUpperBoundLimit() throws Exception {
        final List<AggregateHistogramContribution> contributions = new ArrayList<>();
        final String bucketUpperBound = "340282366920938463463374607431768211455"; // 16 bytes
        final int valueUpperBound = 15; // 4 bytes
        final AggregateHistogramContribution contribution =
                new AggregateHistogramContribution.Builder()
                        .setKey(new BigInteger(bucketUpperBound))
                        .setValue(valueUpperBound)
                        .build();
        contributions.add(contribution);

        final byte[] encoded = AggregateCryptoConverter.encodeWithCbor(contributions);
        final List<DataItem> dataItems =
                new CborDecoder(new ByteArrayInputStream(encoded)).decode();

        final Map payload = (Map) dataItems.get(0);
        final Array payloadArray = (Array) payload.get(new UnicodeString("data"));

        assertEquals(1, payloadArray.getDataItems().size());
        assertEquals("histogram", payload.get(new UnicodeString("operation")).toString());
        assertTrue(
                payloadArray.getDataItems().stream()
                        .anyMatch(
                                i ->
                                        isFound((Map) i, "bucket", bucketUpperBound)
                                                && isFound(
                                                        (Map) i, "value", "" + valueUpperBound)));
    }

    @Test
    public void testEncodeWithCbor_withAndWithoutSignedBits_ignoreSignedBits() throws Exception {
        final List<AggregateHistogramContribution> contributions = new ArrayList<>();
        byte[] withoutSignedBit = new byte[] {10};
        byte[] withSignedBit = new byte[] {0, 20};
        byte[] withEmptyBucket = new byte[] {0};

        final AggregateHistogramContribution firstContribution =
                new AggregateHistogramContribution.Builder()
                        .setKey(new BigInteger(withoutSignedBit))
                        .setValue(1)
                        .build();
        final AggregateHistogramContribution secondContribution =
                new AggregateHistogramContribution.Builder()
                        .setKey(new BigInteger(withSignedBit))
                        .setValue(2)
                        .build();
        final AggregateHistogramContribution thirdContribution =
                new AggregateHistogramContribution.Builder()
                        .setKey(new BigInteger(withEmptyBucket))
                        .setValue(0)
                        .build();
        contributions.add(firstContribution);
        contributions.add(secondContribution);
        contributions.add(thirdContribution);

        final byte[] encoded = AggregateCryptoConverter.encodeWithCbor(contributions);
        final List<DataItem> dataItems =
                new CborDecoder(new ByteArrayInputStream(encoded)).decode();

        final Map payload = (Map) dataItems.get(0);
        final Array payloadArray = (Array) payload.get(new UnicodeString("data"));
        assertEquals(3, payloadArray.getDataItems().size());
        assertEquals("histogram", payload.get(new UnicodeString("operation")).toString());
        assertTrue(
                payloadArray.getDataItems().stream()
                        .anyMatch(
                                i ->
                                        isFound((Map) i, "bucket", "10")
                                                && isFound((Map) i, "value", "1")));
        assertTrue(
                payloadArray.getDataItems().stream()
                        .anyMatch(
                                i ->
                                        isFound((Map) i, "bucket", "20")
                                                && isFound((Map) i, "value", "2")));
        assertTrue(
                payloadArray.getDataItems().stream()
                        .anyMatch(
                                i ->
                                        isFound((Map) i, "bucket", "0")
                                                && isFound((Map) i, "value", "0")));
    }

    @Test
    public void testEncryptWithHpke_successfully() {
        byte[] encoded =
                AggregateCryptoConverter.encryptWithHpke(
                        AggregateCryptoFixture.getPublicKey(),
                        PLAIN_TEXT.getBytes(),
                        SHARED_INFO.getBytes());
        assertNotNull(encoded);
        assertNotEquals(PLAIN_TEXT, new String(encoded));

        byte[] decoded =
                HpkeJni.decrypt(
                        AggregateCryptoFixture.getPrivateKey(), encoded, SHARED_INFO.getBytes());
        assertNotNull(decoded);
        assertEquals(PLAIN_TEXT, new String(decoded));
    }

    @Test
    public void testEncryptWithHpke_invalidPublicKey() {
        byte[] encoded =
                AggregateCryptoConverter.encryptWithHpke(
                        "".getBytes(), PLAIN_TEXT.getBytes(), SHARED_INFO.getBytes());
        assertNull(encoded);
    }

    @Test
    public void testEncodeWithBase64_successfully() {
        String encoded = AggregateCryptoConverter.encodeWithBase64("-".getBytes());
        assertNotNull(encoded);
        assertEquals("-", new String(Base64.getDecoder().decode(encoded)));
    }

    private void assertEncryptedPayload(String encryptedPayloadBase64, String sharedInfo)
            throws Exception {
        final byte[] associatedData =
                sharedInfo == null
                        ? AggregateCryptoFixture.getSharedInfoPrefix().getBytes()
                        : (AggregateCryptoFixture.getSharedInfoPrefix() + sharedInfo).getBytes();
        final byte[] decryptedCborEncoded =
                HpkeJni.decrypt(
                        AggregateCryptoFixture.getPrivateKey(),
                        Base64.getDecoder().decode(encryptedPayloadBase64),
                        associatedData);
        assertNotNull(decryptedCborEncoded);

        final List<DataItem> dataItems =
                new CborDecoder(new ByteArrayInputStream(decryptedCborEncoded)).decode();

        final Map payload = (Map) dataItems.get(0);
        assertEquals("histogram", payload.get(new UnicodeString("operation")).toString());

        final Array payloadArray = (Array) payload.get(new UnicodeString("data"));
        assertEquals(2, payloadArray.getDataItems().size());
        assertTrue(
                payloadArray.getDataItems().stream()
                        .anyMatch(
                                i ->
                                        isFound((Map) i, "bucket", "1")
                                                && isFound((Map) i, "value", "2")));

        assertTrue(
                payloadArray.getDataItems().stream()
                        .anyMatch(
                                i ->
                                        isFound((Map) i, "bucket", "3")
                                                && isFound((Map) i, "value", "4")));
    }

    private void assertEncodedPayload(String encodedPayloadBase64) throws Exception {
        final byte[] cborEncoded = Base64.getDecoder().decode(encodedPayloadBase64);
        assertNotNull(cborEncoded);

        final List<DataItem> dataItems =
                new CborDecoder(new ByteArrayInputStream(cborEncoded)).decode();

        final Map payload = (Map) dataItems.get(0);
        assertEquals("histogram", payload.get(new UnicodeString("operation")).toString());

        final Array payloadArray = (Array) payload.get(new UnicodeString("data"));
        assertEquals(2, payloadArray.getDataItems().size());
        assertTrue(
                payloadArray.getDataItems().stream()
                        .anyMatch(
                                i ->
                                        isFound((Map) i, "bucket", "1")
                                                && isFound((Map) i, "value", "2")));

        assertTrue(
                payloadArray.getDataItems().stream()
                        .anyMatch(
                                i ->
                                        isFound((Map) i, "bucket", "3")
                                                && isFound((Map) i, "value", "4")));
    }

    private boolean isFound(Map map, String name, String value) {
        return new BigInteger(value)
                .equals(
                        new BigInteger(
                                1, ((ByteString) map.get(new UnicodeString(name))).getBytes()));
    }

    private int getBytesLength(Map map, String keyName) {
        return ((ByteString) map.get(new UnicodeString(keyName))).getBytes().length;
    }
}
