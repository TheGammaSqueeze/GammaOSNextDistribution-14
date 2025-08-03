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

package android.telephony.gba.cts;


import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.annotation.FlaggedApi;
import android.content.pm.PackageManager;
import android.os.Parcel;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.gba.TlsParams;
import android.telephony.gba.UaSecurityProtocolIdentifier;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.telephony.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

@RunWith(AndroidJUnit4.class)
public final class UaSecurityProtocolIdentifierTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = "UaSecurityProtocolIdentifierTest";
    private static final int PROTO_SIZE = 5;
    private static final byte[] PROTO_DEFAULT = {0x00, 0x00, 0x00, 0x00, 0x00};
    private static final int[] PROTO_3GPP_PLAIN_ID = {
        UaSecurityProtocolIdentifier.UA_SECURITY_PROTOCOL_3GPP_SUBSCRIBER_CERTIFICATE,
        UaSecurityProtocolIdentifier.UA_SECURITY_PROTOCOL_3GPP_MBMS,
        UaSecurityProtocolIdentifier.UA_SECURITY_PROTOCOL_3GPP_HTTP_DIGEST_AUTHENTICATION,
        UaSecurityProtocolIdentifier.UA_SECURITY_PROTOCOL_3GPP_HTTP_BASED_MBMS,
        UaSecurityProtocolIdentifier.UA_SECURITY_PROTOCOL_3GPP_SIP_BASED_MBMS,
        UaSecurityProtocolIdentifier.UA_SECURITY_PROTOCOL_3GPP_GENERIC_PUSH_LAYER,
        UaSecurityProtocolIdentifier.UA_SECURITY_PROTOCOL_3GPP_IMS_MEDIA_PLANE,
        UaSecurityProtocolIdentifier.UA_SECURITY_PROTOCOL_3GPP_GENERATION_TMPI};
    private static final byte[][] PROTO_3GPP_PLAIN = {
        {0x01, 0x00, 0x00, 0x00, 0x00},
        {0x01, 0x00, 0x00, 0x00, 0x01},
        {0x01, 0x00, 0x00, 0x00, 0x02},
        {0x01, 0x00, 0x00, 0x00, 0x03},
        {0x01, 0x00, 0x00, 0x00, 0x04},
        {0x01, 0x00, 0x00, 0x00, 0x05},
        {0x01, 0x00, 0x00, 0x00, 0x06},
        {0x01, 0x00, 0x00, 0x01, 0x00}};
    private static final int[] PROTO_3GPP_TLS_ID = {
        UaSecurityProtocolIdentifier.UA_SECURITY_PROTOCOL_3GPP_TLS_DEFAULT,
        UaSecurityProtocolIdentifier.UA_SECURITY_PROTOCOL_3GPP_TLS_BROWSER};
    private static final byte[][] PROTO_3GPP_TLS = {
        {0x01, 0x00, 0x01, 0x00, 0x00},
        {0x01, 0x00, 0x02, 0x00, 0x00}};

    private static final int[] TLS_CS_ID_SUPPORTED = {
        0x0000, 0x0001, 0x0002, 0x0004, 0x0005, 0x000A, 0x000D, 0x0010, 0x0013, 0x0016, 0x0018,
        0x001B, 0x002F, 0x0030, 0x0031, 0x0032, 0x0033, 0x0034, 0x0035, 0x0036, 0x0037, 0x0038,
        0x0039, 0x003A, 0x003B, 0x003C, 0x003D, 0x003E, 0x003F, 0x0040, 0x0067, 0x0068, 0x0069,
        0x006A, 0x006B, 0x006C, 0x006D, 0x009E, 0x009F, 0x00AA, 0x00AB, 0x1301, 0x1302, 0x1303,
        0x1304, 0xC02B, 0xC02C, 0xC02F, 0xC030, 0xC09E, 0xC09F, 0xC0A6, 0xC0A7, 0xCCA8, 0xCCA9,
        0xCCAA, 0xCCAC, 0xCCAD, 0xD001, 0xD002, 0xD005};

    @Test
    public void testDefaultId() {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(isFeatureSupported());
        }

        UaSecurityProtocolIdentifier.Builder builder = new UaSecurityProtocolIdentifier.Builder();
        UaSecurityProtocolIdentifier sp = builder.build();
        assertNotNull(sp);
        assertEquals(UaSecurityProtocolIdentifier.ORG_NONE, sp.getOrg());
        assertArrayEquals(sp.toByteArray(), PROTO_DEFAULT);
    }

    @Test
    public void testValid3gppId() {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(isFeatureSupported());
        }

        for (int i = 0; i < PROTO_3GPP_PLAIN_ID.length; i++) {
            UaSecurityProtocolIdentifier sp = testCreate3GppSpId(
                    PROTO_3GPP_PLAIN_ID[i], null, false);
            assertNotNull(sp);
            assertEquals(UaSecurityProtocolIdentifier.ORG_3GPP, sp.getOrg());
            assertEquals(PROTO_3GPP_PLAIN_ID[i], sp.getProtocol());
            assertEquals(0, sp.getTlsCipherSuite());
            assertArrayEquals(sp.toByteArray(), PROTO_3GPP_PLAIN[i]);
        }
    }

    @Test
    public void testValid3gppIdWithTls() {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(isFeatureSupported());
        }

        for (int i = 0; i < PROTO_3GPP_TLS_ID.length; i++) {
            for (int j = 0; j < TLS_CS_ID_SUPPORTED.length; j++) {
                UaSecurityProtocolIdentifier sp = testCreate3GppSpId(
                        PROTO_3GPP_TLS_ID[i], TLS_CS_ID_SUPPORTED[j], false);
                assertNotNull(sp);
                assertEquals(UaSecurityProtocolIdentifier.ORG_3GPP, sp.getOrg());
                assertEquals(PROTO_3GPP_TLS_ID[i], sp.getProtocol());
                assertEquals(TLS_CS_ID_SUPPORTED[j], sp.getTlsCipherSuite());
                byte[] targetData = new byte[PROTO_SIZE];
                ByteBuffer buf = ByteBuffer.wrap(targetData);
                buf.put(PROTO_3GPP_TLS[i]);
                buf.putShort(PROTO_SIZE - 2, (short) TLS_CS_ID_SUPPORTED[j]);
                assertArrayEquals(targetData, sp.toByteArray());
            }
        }
    }

    @Test
    public void testInvalidId() {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(isFeatureSupported());
        }

        Random rand = new Random();
        HashSet<Integer> validIds = new HashSet<>();
        for (int id : PROTO_3GPP_PLAIN_ID) {
            validIds.add(id);
        }
        for (int id : PROTO_3GPP_TLS_ID) {
            validIds.add(id);
        }
        for (int i = 0; i < 200; i++) {
            int r = rand.nextInt();
            UaSecurityProtocolIdentifier sp = testCreate3GppSpId(
                    r, TlsParams.TLS_NULL_WITH_NULL_NULL, !validIds.contains(r));
        }
    }

    @Test
    public void testInvalid3gppIdWithTls() {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(isFeatureSupported());
        }

        Random rand = new Random();
        for (int i = 0; i < PROTO_3GPP_TLS_ID.length; i++) {
            for (int j = 0; j < 200; j++) {
                int r = rand.nextInt(Integer.MAX_VALUE);
                int index = Arrays.binarySearch(TLS_CS_ID_SUPPORTED, r);
                boolean isFailExpected = index < 0;
                UaSecurityProtocolIdentifier sp = testCreate3GppSpId(
                        PROTO_3GPP_TLS_ID[i], r, isFailExpected);
            }
        }
    }

    @Test
    public void testParcelUnparcel() {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(isFeatureSupported());
        }

        UaSecurityProtocolIdentifier sp = testCreate3GppSpId(
                PROTO_3GPP_TLS_ID[0], TLS_CS_ID_SUPPORTED[0], false);
        Parcel parcel = Parcel.obtain();
        sp.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        UaSecurityProtocolIdentifier sp2 =
                UaSecurityProtocolIdentifier.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        assertTrue(sp.equals(sp2));
    }

    @Test
    public void testIsTlsCipherSuiteSupported() {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(isFeatureSupported());
        }

        Random rand = new Random();

        for (int i = 0; i < TLS_CS_ID_SUPPORTED.length; i++) {
            assertTrue(TlsParams.isTlsCipherSuiteSupported(TLS_CS_ID_SUPPORTED[i]));
        }

        for (int i = 0; i < 100; i++) {
            int val = rand.nextInt();
            if (Arrays.binarySearch(TLS_CS_ID_SUPPORTED, val) < 0) {
                assertFalse(TlsParams.isTlsCipherSuiteSupported(val));
            }
        }
    }

    @Test
    public void testUaSecurityProtocolIdentifierBuilder() {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(isFeatureSupported());
        }

        UaSecurityProtocolIdentifier sp = testCreate3GppSpId(
                PROTO_3GPP_TLS_ID[0], TLS_CS_ID_SUPPORTED[0], false);
        UaSecurityProtocolIdentifier.Builder builder =
                new UaSecurityProtocolIdentifier.Builder(sp);

        assertTrue(sp.equals(builder.build()));
    }

    private UaSecurityProtocolIdentifier testCreate3GppSpId(
            Integer id, Integer cs, boolean nullExpected) {
        boolean isFail = false;
        UaSecurityProtocolIdentifier sp = null;
        UaSecurityProtocolIdentifier.Builder builder = new UaSecurityProtocolIdentifier.Builder();
        builder.setOrg(UaSecurityProtocolIdentifier.ORG_3GPP);
        try {
            if (id != null) {
                builder.setProtocol(id);
            }
            if (cs != null) {
                builder.setTlsCipherSuite(cs);
            }
            sp = builder.build();
        } catch (IllegalArgumentException e) {
        }
        if (nullExpected) {
            assertNull(sp);
        } else {
            assertNotNull(sp);
        }
        return sp;
    }

    private String getRandomString(Random rand) {
        int size = rand.nextInt(64);
        byte[] arr = new byte[size];
        rand.nextBytes(arr);
        return new String(arr);
    }

    @FlaggedApi(Flags.FLAG_ENFORCE_TELEPHONY_FEATURE_MAPPING_FOR_PUBLIC_APIS)
    private static boolean isFeatureSupported() {
        if (!InstrumentationRegistry.getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION)) {
            return false;
        }

        return true;
    }
}
