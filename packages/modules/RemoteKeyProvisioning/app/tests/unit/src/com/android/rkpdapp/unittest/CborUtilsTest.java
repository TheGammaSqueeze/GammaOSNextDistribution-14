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

package com.android.rkpdapp.unittest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.os.Build;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.rkpdapp.GeekResponse;
import com.android.rkpdapp.utils.CborUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.MajorType;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnicodeString;
import co.nstant.in.cbor.model.UnsignedInteger;

@RunWith(AndroidJUnit4.class)
public class CborUtilsTest {

    private ByteArrayOutputStream mBaos;
    private Array mGeekChain1;
    private byte[] mEncodedmGeekChain1;
    private Array mGeekChain2;
    private byte[] mEncodedGeekChain2;
    private Map mDeviceConfig;
    private static final byte[] CHALLENGE = new byte[]{0x0a, 0x0b, 0x0c};
    private static final int TEST_EXTRA_KEYS = 18;
    private static final int TEST_TIME_TO_REFRESH_HOURS = 42;
    private static final String TEST_URL = "https://www.wonderifthisisvalid.combutjustincase";

    private byte[] encodeDataItem(DataItem toEncode) throws Exception {
        new CborEncoder(mBaos).encode(new CborBuilder()
                        .add(toEncode)
                        .build());
        byte[] encoded = mBaos.toByteArray();
        mBaos.reset();
        return encoded;
    }

    @Before
    public void setUp() throws Exception {
        mBaos = new ByteArrayOutputStream();

        mGeekChain1 = new Array();
        mGeekChain1.add(new ByteString(new byte[] {0x01, 0x02, 0x03}))
                   .add(new ByteString(new byte[] {0x04, 0x05, 0x06}))
                   .add(new ByteString(new byte[] {0x07, 0x08, 0x09}));
        mEncodedmGeekChain1 = encodeDataItem(mGeekChain1);

        mGeekChain2 = new Array();
        mGeekChain2.add(new ByteString(new byte[] {0x09, 0x08, 0x07}))
                   .add(new ByteString(new byte[] {0x06, 0x05, 0x04}))
                   .add(new ByteString(new byte[] {0x03, 0x02, 0x01}));
        mEncodedGeekChain2 = encodeDataItem(mGeekChain2);

        mDeviceConfig = new Map();
        mDeviceConfig.put(new UnicodeString(CborUtils.EXTRA_KEYS),
                          new UnsignedInteger(TEST_EXTRA_KEYS))
                     .put(new UnicodeString(CborUtils.TIME_TO_REFRESH),
                          new UnsignedInteger(TEST_TIME_TO_REFRESH_HOURS))
                     .put(new UnicodeString(CborUtils.PROVISIONING_URL),
                          new UnicodeString(TEST_URL));
    }

    @Presubmit
    @Test
    public void testParseSignedCertificatesFakeData() throws Exception {
        new CborEncoder(mBaos).encode(new CborBuilder()
                .addArray()
                    .add(new byte[] {0x01, 0x02, 0x03})
                    .addArray()
                        .add(new byte[] {0x04, 0x05, 0x06})
                        .add(new byte[] {0x07, 0x08, 0x09})
                        .end()
                    .end()
                .build());
        byte[] encodedBytes = mBaos.toByteArray();
        ArrayList<byte[]> certChains =
                new ArrayList<>(CborUtils.parseSignedCertificates(encodedBytes));
        assertArrayEquals(new byte[] {0x04, 0x05, 0x06, 0x01, 0x02, 0x03}, certChains.get(0));
        assertArrayEquals(new byte[] {0x07, 0x08, 0x09, 0x01, 0x02, 0x03}, certChains.get(1));
    }

    @Test
    public void testParseSignedCertificatesWrongSize() throws Exception {
        new CborEncoder(mBaos).encode(new CborBuilder()
                .addArray()
                    .add(1)
                    .end()
                .build());
        assertNull(CborUtils.parseSignedCertificates(mBaos.toByteArray()));
    }

    @Test
    public void testParseSignedCertificatesWrongTypeSharedCerts() throws Exception {
        new CborEncoder(mBaos).encode(new CborBuilder()
                .addArray()
                    .add("Should be a bstr")
                    .addArray()
                        .add(new byte[] {0x04, 0x05, 0x06})
                        .add(new byte[] {0x07, 0x08, 0x09})
                        .end()
                    .end()
                .build());
        assertNull(CborUtils.parseSignedCertificates(mBaos.toByteArray()));
    }

    @Test
    public void testParseSignedCertificatesWrongTypeUniqueCerts() throws Exception {
        new CborEncoder(mBaos).encode(new CborBuilder()
                .addArray()
                    .add(new byte[] {0x01, 0x02, 0x03})
                    .addArray()
                        .add(new byte[] {0x04, 0x05, 0x06})
                        .add("Every entry should be a bstr")
                        .add(new byte[] {0x07, 0x08, 0x09})
                        .end()
                    .end()
                .build());
        assertNull(CborUtils.parseSignedCertificates(mBaos.toByteArray()));
    }

    @Presubmit
    @Test
    public void testParseGeekResponseFakeData() throws Exception {
        new CborEncoder(mBaos).encode(new CborBuilder()
                .addArray()
                    .addArray()                                       // GEEK Curve to Chains
                        .addArray()
                            .add(new UnsignedInteger(CborUtils.EC_CURVE_25519))
                            .add(mGeekChain1)
                            .end()
                        .addArray()
                            .add(new UnsignedInteger(CborUtils.EC_CURVE_P256))
                            .add(mGeekChain2)
                            .end()
                        .end()
                    .add(CHALLENGE)
                    .add(mDeviceConfig)
                    .end()
                .build());
        GeekResponse resp = CborUtils.parseGeekResponse(mBaos.toByteArray());
        mBaos.reset();
        assertArrayEquals(mEncodedmGeekChain1, resp.getGeekChain(CborUtils.EC_CURVE_25519));
        assertArrayEquals(mEncodedGeekChain2, resp.getGeekChain(CborUtils.EC_CURVE_P256));
        assertArrayEquals(CHALLENGE, resp.getChallenge());
        assertEquals(TEST_EXTRA_KEYS, resp.numExtraAttestationKeys);
        assertEquals(TEST_TIME_TO_REFRESH_HOURS, resp.timeToRefresh.toHours());
        assertEquals(TEST_URL, resp.provisioningUrl);
    }

    @Test
    public void testExtraDeviceConfigEntriesDontFail() throws Exception {
        new CborEncoder(mBaos).encode(new CborBuilder()
                .addArray()
                    .addArray()                                       // GEEK Curve to Chains
                        .addArray()
                            .add(new UnsignedInteger(CborUtils.EC_CURVE_25519))
                            .add(mGeekChain1)
                            .end()
                        .addArray()
                            .add(new UnsignedInteger(CborUtils.EC_CURVE_P256))
                            .add(mGeekChain2)
                            .end()
                        .end()
                    .add(CHALLENGE)
                    .add(mDeviceConfig.put(new UnicodeString("new_field"),
                                          new UnsignedInteger(84)))
                    .end()
                .build());
        GeekResponse resp = CborUtils.parseGeekResponse(mBaos.toByteArray());
        mBaos.reset();
        assertArrayEquals(mEncodedmGeekChain1, resp.getGeekChain(CborUtils.EC_CURVE_25519));
        assertArrayEquals(mEncodedGeekChain2, resp.getGeekChain(CborUtils.EC_CURVE_P256));
        assertArrayEquals(CHALLENGE, resp.getChallenge());
        assertEquals(TEST_EXTRA_KEYS, resp.numExtraAttestationKeys);
        assertEquals(TEST_TIME_TO_REFRESH_HOURS, resp.timeToRefresh.toHours());
        assertEquals(TEST_URL, resp.provisioningUrl);
    }

    @Test
    public void testMissingDeviceConfigDoesntFail() throws Exception {
        new CborEncoder(mBaos).encode(new CborBuilder()
                .addArray()
                    .addArray()                                       // GEEK Curve to Chains
                        .addArray()
                            .add(new UnsignedInteger(CborUtils.EC_CURVE_25519))
                            .add(mGeekChain1)
                            .end()
                        .addArray()
                            .add(new UnsignedInteger(CborUtils.EC_CURVE_P256))
                            .add(mGeekChain2)
                            .end()
                        .end()
                    .add(CHALLENGE)
                    .end()
                .build());
        GeekResponse resp = CborUtils.parseGeekResponse(mBaos.toByteArray());
        mBaos.reset();
        assertArrayEquals(mEncodedmGeekChain1, resp.getGeekChain(CborUtils.EC_CURVE_25519));
        assertArrayEquals(mEncodedGeekChain2, resp.getGeekChain(CborUtils.EC_CURVE_P256));
        assertArrayEquals(CHALLENGE, resp.getChallenge());
        assertEquals(GeekResponse.NO_EXTRA_KEY_UPDATE, resp.numExtraAttestationKeys);
        assertNull(resp.timeToRefresh);
        assertNull(resp.provisioningUrl);
    }

    @Test
    public void testMissingDeviceConfigEntriesDoesntFail() throws Exception {
        mDeviceConfig.remove(new UnicodeString(CborUtils.TIME_TO_REFRESH));
        new CborEncoder(mBaos).encode(new CborBuilder()
                .addArray()
                    .addArray()                                       // GEEK Curve to Chains
                        .addArray()
                            .add(new UnsignedInteger(CborUtils.EC_CURVE_25519))
                            .add(mGeekChain1)
                            .end()
                        .addArray()
                            .add(new UnsignedInteger(CborUtils.EC_CURVE_P256))
                            .add(mGeekChain2)
                            .end()
                        .end()
                    .add(CHALLENGE)
                    .add(mDeviceConfig)
                    .end()
                .build());
        GeekResponse resp = CborUtils.parseGeekResponse(mBaos.toByteArray());
        mBaos.reset();
        assertArrayEquals(mEncodedmGeekChain1, resp.getGeekChain(CborUtils.EC_CURVE_25519));
        assertArrayEquals(mEncodedGeekChain2, resp.getGeekChain(CborUtils.EC_CURVE_P256));
        assertArrayEquals(CHALLENGE, resp.getChallenge());
        assertEquals(TEST_EXTRA_KEYS, resp.numExtraAttestationKeys);
        assertNull(resp.timeToRefresh);
        assertEquals(TEST_URL, resp.provisioningUrl);
    }

    @Test
    public void testParseGeekResponseFailsOnWrongType() throws Exception {
        new CborEncoder(mBaos).encode(new CborBuilder()
                .addArray()
                    .addArray()
                        .addArray()
                            .add("String instead of curve enum")
                            .add(mGeekChain1)
                            .end()
                        .end()
                    .add(CHALLENGE)
                    .add(mDeviceConfig)
                    .end()
                .build());
        assertNull(CborUtils.parseGeekResponse(mBaos.toByteArray()));
        mBaos.reset();
        new CborEncoder(mBaos).encode(new CborBuilder()
                .addArray()
                    .addArray()
                        .addArray()
                            .add(new UnsignedInteger(CborUtils.EC_CURVE_25519))
                            .add(new ByteString(CHALLENGE)) // Must be an array of bstrs
                            .end()
                        .end()
                    .add(CHALLENGE)
                    .add(mDeviceConfig)
                    .end()
                .build());
        assertNull(CborUtils.parseGeekResponse(mBaos.toByteArray()));
        mBaos.reset();
        new CborEncoder(mBaos).encode(new CborBuilder()
                .addArray()
                    .addArray()
                        .addArray()
                            .add(new UnsignedInteger(CborUtils.EC_CURVE_25519))
                            .add(mGeekChain1)
                            .end()
                        .end()
                    .add(new UnicodeString("tstr instead of bstr"))
                    .add(mDeviceConfig)
                    .end()
                .build());
        assertNull(CborUtils.parseGeekResponse(mBaos.toByteArray()));
        mBaos.reset();
        new CborEncoder(mBaos).encode(new CborBuilder()
                .addArray()
                    .addArray()
                        .addArray()
                            .add(new UnsignedInteger(CborUtils.EC_CURVE_25519))
                            .add(mGeekChain1)
                            .end()
                        .end()
                    .add(CHALLENGE)
                    .add(CHALLENGE)
                    .end()
                .build());
        assertNull(CborUtils.parseGeekResponse(mBaos.toByteArray()));
    }

    @Test
    public void testParseGeekResponseWrongSize() throws Exception {
        new CborEncoder(mBaos).encode(new CborBuilder()
                .addArray()
                    .add("one entry")
                    .add("two entries")
                    .add("three entries")
                    .add("whoops")
                    .end()
                .build());
        assertNull(CborUtils.parseGeekResponse(mBaos.toByteArray()));
    }

    @Test
    public void testCreateCertificateRequest() throws Exception {
        new CborEncoder(mBaos).encode(new CborBuilder()
                .addMap()
                    .put("a", "b")
                    .put("cool", "yeah")
                    .put("testing", "123")
                    .put("str", "str")
                    .end()
                .build());
        byte[] deviceInfo = mBaos.toByteArray();
        mBaos.reset();
        byte[] challenge = new byte[] {0x01, 0x02, 0x03};
        new CborEncoder(mBaos).encode(new CborBuilder()
                .addArray()
                    .add("protected header")
                    .add("unprotected header")
                    .add("super secret payload")
                    .add("super not secret recipient")
                    .end()
                .build());
        byte[] protectedDataPayload = mBaos.toByteArray();
        mBaos.reset();
        new CborEncoder(mBaos).encode(new CborBuilder()
                .addArray()
                    .add("protected header")
                    .add("unprotected header")
                    .add("super not secret payload")
                    .add("mac tag")
                    .end()
                .build());
        byte[] macedKeysToSign = mBaos.toByteArray();
        byte[] certReq =
                CborUtils.buildCertificateRequest(deviceInfo,
                                                  challenge,
                                                  protectedDataPayload,
                                                  macedKeysToSign,
                                                  CborUtils.buildUnverifiedDeviceInfo());
        ByteArrayInputStream bais = new ByteArrayInputStream(certReq);
        List<DataItem> dataItems = new CborDecoder(bais).decode();
        assertEquals(1, dataItems.size());
        assertEquals(MajorType.ARRAY, dataItems.get(0).getMajorType());
        dataItems = ((Array) dataItems.get(0)).getDataItems();
        assertEquals(4, dataItems.size());
        // Array: DeviceInfo
        //      Map: VerifiedDeviceInfo
        //      Map: UnverifiedDeviceInfo
        assertEquals(MajorType.ARRAY, dataItems.get(0).getMajorType());
        assertEquals(MajorType.MAP,
                ((Array) dataItems.get(0)).getDataItems().get(0).getMajorType());
        assertEquals(MajorType.MAP,
                ((Array) dataItems.get(0)).getDataItems().get(1).getMajorType());
        // Challenge
        assertEquals(MajorType.BYTE_STRING, dataItems.get(1).getMajorType());
        // ProtectedData
        assertEquals(MajorType.ARRAY, dataItems.get(2).getMajorType());
        // MacedKeysToSign
        assertEquals(MajorType.ARRAY, dataItems.get(3).getMajorType());
    }

    @Test
    public void testBuildUnverifiedDeviceInfo() {
        Map devInfo = CborUtils.buildUnverifiedDeviceInfo();
        assertEquals("Unverified device info only has one entry.", 1, devInfo.getKeys().size());
        DataItem fingerprint = devInfo.get(new UnicodeString("fingerprint"));
        assertNotNull("Device info doesn't contain fingerprint", fingerprint);
        assertEquals(MajorType.UNICODE_STRING, fingerprint.getMajorType());
        assertEquals(Build.FINGERPRINT, fingerprint.toString());
    }
}
