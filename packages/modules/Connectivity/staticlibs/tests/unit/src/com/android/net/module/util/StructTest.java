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

package com.android.net.module.util;

import static com.android.testutils.MiscAsserts.assertThrows;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.annotation.SuppressLint;
import android.net.InetAddresses;
import android.net.IpPrefix;
import android.net.MacAddress;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.Struct.Field;
import com.android.net.module.util.Struct.Type;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class StructTest {

    // IPv6, 0 bytes of options, ifindex 15715755: 0x00efcdab, type 134 (RA), code 0, padding.
    private static final String HDR_EMPTY = "0a00" + "0000" + "abcdef00" + "8600000000000000";

    // UBE16: 0xfeff, UBE32: 0xfeffffff, UBE64: 0xfeffffffffffffff, UBE63: 0x7effffffffffffff
    private static final String NETWORK_ORDER_MSG = "feff" + "feffffff" + "feffffffffffffff"
            + "7effffffffffffff";

    // S8: 0x7f, S16: 0x7fff, S32: 0x7fffffff, S64: 0x7fffffffffffffff
    private static final String SIGNED_DATA = "7f" + "ff7f" + "ffffff7f" + "ffffffffffffff7f";

    // nS8: 0x81, nS16: 0x8001, nS32: 0x80000001, nS64: 800000000000000001
    private static final String SIGNED_NEGATIVE_DATA = "81" + "0180" + "01000080"
            + "0100000000000080";

    // U8: 0xff, U16: 0xffff, U32: 0xffffffff, U64: 0xffffffffffffffff, U63: 0x7fffffffffffffff,
    // U63: 0xffffffffffffffff(-1L)
    private static final String UNSIGNED_DATA = "ff" + "ffff" + "ffffffff" + "ffffffffffffffff"
            + "ffffffffffffff7f" + "ffffffffffffffff";

    // PREF64 option, 2001:db8:3:4:5:6::/96, lifetime: 10064
    private static final String OPT_PREF64 = "2750" + "20010db80003000400050006";
    private static final byte[] TEST_PREFIX64 = new byte[]{
            (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8, (byte) 0x00, (byte) 0x03,
            (byte) 0x00, (byte) 0x04, (byte) 0x00, (byte) 0x05, (byte) 0x00, (byte) 0x06,
    };

    private static final Inet4Address TEST_IPV4_ADDRESS =
            (Inet4Address) InetAddresses.parseNumericAddress("192.168.100.1");
    private static final Inet6Address TEST_IPV6_ADDRESS =
            (Inet6Address) InetAddresses.parseNumericAddress("2001:db8:3:4:5:6:7:8");

    private <T> T doParsingMessageTest(final String hexString, final Class<T> clazz,
            final ByteOrder order) {
        final ByteBuffer buf = toByteBuffer(hexString);
        buf.order(order);
        return Struct.parse(clazz, buf);
    }

    public static class HeaderMsgWithConstructor extends Struct {
        static int sType;
        static int sLength;

        @Field(order = 0, type = Type.U8, padding = 1)
        public final short mFamily;
        @Field(order = 1, type = Type.U16)
        public final int mLen;
        @Field(order = 2, type = Type.S32)
        public final int mIfindex;
        @Field(order = 3, type = Type.U8)
        public final short mIcmpType;
        @Field(order = 4, type = Type.U8, padding = 6)
        public final short mIcmpCode;

        HeaderMsgWithConstructor(final short family, final int len, final int ifindex,
                final short type, final short code) {
            mFamily = family;
            mLen = len;
            mIfindex = ifindex;
            mIcmpType = type;
            mIcmpCode = code;
        }
    }

    private void verifyHeaderParsing(final HeaderMsgWithConstructor msg) {
        assertEquals(10, msg.mFamily);
        assertEquals(0, msg.mLen);
        assertEquals(15715755, msg.mIfindex);
        assertEquals(134, msg.mIcmpType);
        assertEquals(0, msg.mIcmpCode);

        assertEquals(16, Struct.getSize(HeaderMsgWithConstructor.class));
        assertArrayEquals(toByteBuffer(HDR_EMPTY).array(),
                msg.writeToBytes(ByteOrder.LITTLE_ENDIAN));
    }

    @Test
    public void testClassWithExplicitConstructor() {
        final HeaderMsgWithConstructor msg = doParsingMessageTest(HDR_EMPTY,
                HeaderMsgWithConstructor.class, ByteOrder.LITTLE_ENDIAN);
        verifyHeaderParsing(msg);
    }

    public static class HeaderMsgWithoutConstructor extends Struct {
        static int sType;
        static int sLength;

        @Field(order = 0, type = Type.U8, padding = 1)
        public short mFamily;
        @Field(order = 1, type = Type.U16)
        public int mLen;
        @Field(order = 2, type = Type.S32)
        public int mIfindex;
        @Field(order = 3, type = Type.U8)
        public short mIcmpType;
        @Field(order = 4, type = Type.U8, padding = 6)
        public short mIcmpCode;
    }

    @Test
    public void testClassWithDefaultConstructor() {
        final HeaderMsgWithoutConstructor msg = doParsingMessageTest(HDR_EMPTY,
                HeaderMsgWithoutConstructor.class, ByteOrder.LITTLE_ENDIAN);
        assertEquals(10, msg.mFamily);
        assertEquals(0, msg.mLen);
        assertEquals(15715755, msg.mIfindex);
        assertEquals(134, msg.mIcmpType);
        assertEquals(0, msg.mIcmpCode);

        assertEquals(16, Struct.getSize(HeaderMsgWithoutConstructor.class));
        assertArrayEquals(toByteBuffer(HDR_EMPTY).array(),
                msg.writeToBytes(ByteOrder.LITTLE_ENDIAN));
    }

    public static class HeaderMessage {
        @Field(order = 0, type = Type.U8, padding = 1)
        short mFamily;
        @Field(order = 1, type = Type.U16)
        int mLen;
        @Field(order = 2, type = Type.S32)
        int mIfindex;
        @Field(order = 3, type = Type.U8)
        short mIcmpType;
        @Field(order = 4, type = Type.U8, padding = 6)
        short mIcmpCode;
    }

    @Test
    public void testInvalidClass_NotSubClass() {
        final ByteBuffer buf = toByteBuffer(HDR_EMPTY);
        assertThrows(IllegalArgumentException.class, () -> Struct.parse(HeaderMessage.class, buf));
    }

    public static class HeaderMessageMissingAnnotation extends Struct {
        @Field(order = 0, type = Type.U8, padding = 1)
        short mFamily;
        @Field(order = 1, type = Type.U16)
        int mLen;
        int mIfindex;
        @Field(order = 2, type = Type.U8)
        short mIcmpType;
        @Field(order = 3, type = Type.U8, padding = 6)
        short mIcmpCode;
    }

    @Test
    public void testInvalidClass_MissingAnnotationField() {
        final ByteBuffer buf = toByteBuffer(HDR_EMPTY);
        assertThrows(IllegalArgumentException.class,
                () -> Struct.parse(HeaderMessageMissingAnnotation.class, buf));
    }

    public static class NetworkOrderMessage extends Struct {
        @Field(order = 0, type = Type.UBE16)
        public final int mUBE16;
        @Field(order = 1, type = Type.UBE32)
        public final long mUBE32;
        @Field(order = 2, type = Type.UBE64)
        public final BigInteger mUBE64;
        @Field(order = 3, type = Type.UBE63)
        public final long mUBE63;

        NetworkOrderMessage(final int be16, final long be32, final BigInteger be64,
                final long be63) {
            mUBE16 = be16;
            mUBE32 = be32;
            mUBE64 = be64;
            mUBE63 = be63;
        }
    }

    @Test
    public void testNetworkOrder() {
        final NetworkOrderMessage msg = doParsingMessageTest(NETWORK_ORDER_MSG,
                NetworkOrderMessage.class, ByteOrder.LITTLE_ENDIAN);
        assertEquals(65279, msg.mUBE16);
        assertEquals(4278190079L, msg.mUBE32);
        assertEquals(new BigInteger("18374686479671623679"), msg.mUBE64);
        assertEquals(9151314442816847871L, msg.mUBE63);

        assertEquals(22, Struct.getSize(NetworkOrderMessage.class));
        assertArrayEquals(toByteBuffer(NETWORK_ORDER_MSG).array(),
                msg.writeToBytes(ByteOrder.LITTLE_ENDIAN));
    }

    public static class UnsignedDataMessage extends Struct {
        @Field(order = 0, type = Type.U8)
        public final short mU8;
        @Field(order = 1, type = Type.U16)
        public final int mU16;
        @Field(order = 2, type = Type.U32)
        public final long mU32;
        @Field(order = 3, type = Type.U64)
        public final BigInteger mU64;
        @Field(order = 4, type = Type.U63)
        public final long mU63;
        @Field(order = 5, type = Type.U63)
        public final long mLU64; // represent U64 data with U63 type

        UnsignedDataMessage(final short u8, final int u16, final long u32, final BigInteger u64,
                final long u63, final long lu64) {
            mU8 = u8;
            mU16 = u16;
            mU32 = u32;
            mU64 = u64;
            mU63 = u63;
            mLU64 = lu64;
        }
    }

    @Test
    public void testUnsignedData() {
        final UnsignedDataMessage msg = doParsingMessageTest(UNSIGNED_DATA,
                UnsignedDataMessage.class, ByteOrder.LITTLE_ENDIAN);
        assertEquals(255, msg.mU8);
        assertEquals(65535, msg.mU16);
        assertEquals(4294967295L, msg.mU32);
        assertEquals(new BigInteger("18446744073709551615"), msg.mU64);
        assertEquals(9223372036854775807L, msg.mU63);
        assertEquals(-1L, msg.mLU64);

        assertEquals(31, Struct.getSize(UnsignedDataMessage.class));
        assertArrayEquals(toByteBuffer(UNSIGNED_DATA).array(),
                msg.writeToBytes(ByteOrder.LITTLE_ENDIAN));
    }

    public static class U64DataMessage extends Struct {
        @Field(order = 0, type = Type.U64) long mU64;
    }

    @Test
    public void testInvalidType_U64WithLongPrimitive() {
        assertThrows(IllegalArgumentException.class,
                () -> Struct.parse(U64DataMessage.class, toByteBuffer("ffffffffffffffff")));
    }

    // BigInteger U64: 0x0000000000001234, BigInteger UBE64: 0x0000000000001234, BigInteger U64: 0
    private static final String SMALL_VALUE_BIGINTEGER = "3412000000000000" + "0000000000001234"
            + "0000000000000000";

    public static class SmallValueBigInteger extends Struct {
        @Field(order = 0, type = Type.U64) public final BigInteger mSmallValue;
        @Field(order = 1, type = Type.UBE64) public final BigInteger mBSmallValue;
        @Field(order = 2, type = Type.U64) public final BigInteger mZero;

        SmallValueBigInteger(final BigInteger smallValue, final BigInteger bSmallValue,
                final BigInteger zero) {
            mSmallValue = smallValue;
            mBSmallValue = bSmallValue;
            mZero = zero;
        }
    }

    @Test
    public void testBigIntegerSmallValueOrZero() {
        final SmallValueBigInteger msg = doParsingMessageTest(SMALL_VALUE_BIGINTEGER,
                SmallValueBigInteger.class, ByteOrder.LITTLE_ENDIAN);
        assertEquals(new BigInteger("4660"), msg.mSmallValue);
        assertEquals(new BigInteger("4660"), msg.mBSmallValue);
        assertEquals(new BigInteger("0"), msg.mZero);

        assertEquals(24, Struct.getSize(SmallValueBigInteger.class));
        assertArrayEquals(toByteBuffer(SMALL_VALUE_BIGINTEGER).array(),
                msg.writeToBytes(ByteOrder.LITTLE_ENDIAN));
    }

    public static class SignedDataMessage extends Struct {
        @Field(order = 0, type = Type.S8)
        public final byte mS8;
        @Field(order = 1, type = Type.S16)
        public final short mS16;
        @Field(order = 2, type = Type.S32)
        public final int mS32;
        @Field(order = 3, type = Type.S64)
        public final long mS64;

        SignedDataMessage(final byte s8, final short s16, final int s32, final long s64) {
            mS8 = s8;
            mS16 = s16;
            mS32 = s32;
            mS64 = s64;
        }
    }

    @Test
    public void testSignedPositiveData() {
        final SignedDataMessage msg = doParsingMessageTest(SIGNED_DATA, SignedDataMessage.class,
                ByteOrder.LITTLE_ENDIAN);
        assertEquals(127, msg.mS8);
        assertEquals(32767, msg.mS16);
        assertEquals(2147483647, msg.mS32);
        assertEquals(9223372036854775807L, msg.mS64);

        assertEquals(15, Struct.getSize(SignedDataMessage.class));
        assertArrayEquals(toByteBuffer(SIGNED_DATA).array(),
                msg.writeToBytes(ByteOrder.LITTLE_ENDIAN));
    }

    @Test
    public void testSignedNegativeData() {
        final SignedDataMessage msg = doParsingMessageTest(SIGNED_NEGATIVE_DATA,
                SignedDataMessage.class, ByteOrder.LITTLE_ENDIAN);
        assertEquals(-127, msg.mS8);
        assertEquals(-32767, msg.mS16);
        assertEquals(-2147483647, msg.mS32);
        assertEquals(-9223372036854775807L, msg.mS64);

        assertEquals(15, Struct.getSize(SignedDataMessage.class));
        assertArrayEquals(toByteBuffer(SIGNED_NEGATIVE_DATA).array(),
                msg.writeToBytes(ByteOrder.LITTLE_ENDIAN));
    }

    public static class HeaderMessageWithDuplicateOrder extends Struct {
        @Field(order = 0, type = Type.U8, padding = 1)
        short mFamily;
        @Field(order = 1, type = Type.U16)
        int mLen;
        @Field(order = 2, type = Type.S32)
        int mIfindex;
        @Field(order = 2, type = Type.U8)
        short mIcmpType;
        @Field(order = 3, type = Type.U8, padding = 6)
        short mIcmpCode;
    }

    @Test
    public void testInvalidClass_DuplicateFieldOrder() {
        final ByteBuffer buf = toByteBuffer(HDR_EMPTY);
        assertThrows(IllegalArgumentException.class,
                () -> Struct.parse(HeaderMessageWithDuplicateOrder.class, buf));
    }

    public static class HeaderMessageWithNegativeOrder extends Struct {
        @Field(order = 0, type = Type.U8, padding = 1)
        short mFamily;
        @Field(order = 1, type = Type.U16)
        int mLen;
        @Field(order = 2, type = Type.S32)
        int mIfindex;
        @Field(order = 3, type = Type.U8)
        short mIcmpType;
        @Field(order = -4, type = Type.U8, padding = 6)
        short mIcmpCode;
    }

    @Test
    public void testInvalidClass_NegativeFieldOrder() {
        final ByteBuffer buf = toByteBuffer(HDR_EMPTY);
        assertThrows(IllegalArgumentException.class,
                () -> Struct.parse(HeaderMessageWithNegativeOrder.class, buf));
    }

    public static class HeaderMessageOutOfIndexBounds extends Struct {
        @Field(order = 0, type = Type.U8, padding = 1)
        short mFamily;
        @Field(order = 1, type = Type.U16)
        int mLen;
        @Field(order = 2, type = Type.S32)
        int mIfindex;
        @Field(order = 3, type = Type.U8)
        short mIcmpType;
        @Field(order = 5, type = Type.U8, padding = 6)
        short mIcmpCode;
    }

    @Test
    public void testInvalidClass_OutOfIndexBounds() {
        final ByteBuffer buf = toByteBuffer(HDR_EMPTY);
        assertThrows(IllegalArgumentException.class,
                () -> Struct.parse(HeaderMessageOutOfIndexBounds.class, buf));
    }

    public static class HeaderMessageMismatchedPrimitiveType extends Struct {
        @Field(order = 0, type = Type.U8, padding = 1)
        short mFamily;
        @Field(order = 1, type = Type.U16)
        short mLen; // should be integer
        @Field(order = 2, type = Type.S32)
        int mIfindex;
        @Field(order = 3, type = Type.U8)
        short mIcmpType;
        @Field(order = 4, type = Type.U8, padding = 6)
        short mIcmpCode;
    }

    @Test
    public void testInvalidClass_MismatchedPrimitiveDataType() {
        final ByteBuffer buf = toByteBuffer(HDR_EMPTY);
        assertThrows(IllegalArgumentException.class,
                () -> Struct.parse(HeaderMessageMismatchedPrimitiveType.class, buf));
    }

    public static class PrefixMessage extends Struct {
        @Field(order = 0, type = Type.UBE16)
        public final int mLifetime;
        @Field(order = 1, type = Type.ByteArray, arraysize = 12)
        public final byte[] mPrefix;

        PrefixMessage(final int lifetime, final byte[] prefix) {
            mLifetime = lifetime;
            mPrefix = prefix;
        }
    }

    @SuppressLint("NewApi")
    private void verifyPrefixByteArrayParsing(final PrefixMessage msg) throws Exception {
        // The original PREF64 option message has just 12 bytes for prefix byte array
        // (Highest 96 bits of the Prefix), copyOf pads the 128-bits IPv6 address with
        // prefix and 4-bytes zeros.
        final InetAddress addr = InetAddress.getByAddress(Arrays.copyOf(msg.mPrefix, 16));
        final IpPrefix prefix = new IpPrefix(addr, 96);
        assertEquals(10064, msg.mLifetime);
        assertTrue(prefix.equals(new IpPrefix("2001:db8:3:4:5:6::/96")));

        assertEquals(14, Struct.getSize(PrefixMessage.class));
        assertArrayEquals(toByteBuffer(OPT_PREF64).array(),
                msg.writeToBytes(ByteOrder.LITTLE_ENDIAN));
    }

    public static class PrefixMessageWithZeroLengthArray extends Struct {
        @Field(order = 0, type = Type.UBE16)
        final int mLifetime;
        @Field(order = 1, type = Type.ByteArray, arraysize = 0)
        final byte[] mPrefix;

        PrefixMessageWithZeroLengthArray(final int lifetime, final byte[] prefix) {
            mLifetime = lifetime;
            mPrefix = prefix;
        }
    }

    @Test
    public void testInvalidClass_ZeroLengthByteArray() {
        final ByteBuffer buf = toByteBuffer(OPT_PREF64);
        assertThrows(IllegalArgumentException.class,
                () -> Struct.parse(PrefixMessageWithZeroLengthArray.class, buf));
    }

    @Test
    public void testPrefixArrayField() throws Exception {
        final PrefixMessage msg = doParsingMessageTest(OPT_PREF64, PrefixMessage.class,
                ByteOrder.LITTLE_ENDIAN);
        verifyPrefixByteArrayParsing(msg);
    }

    public static class HeaderMessageWithMutableField extends Struct {
        @Field(order = 0, type = Type.U8, padding = 1)
        final short mFamily;
        @Field(order = 1, type = Type.U16)
        final int mLen;
        @Field(order = 2, type = Type.S32)
        int mIfindex;
        @Field(order = 3, type = Type.U8)
        short mIcmpType;
        @Field(order = 4, type = Type.U8, padding = 6)
        final short mIcmpCode;

        HeaderMessageWithMutableField(final short family, final int len, final short code) {
            mFamily = family;
            mLen = len;
            mIcmpCode = code;
        }
    }

    @Test
    public void testMixMutableAndImmutableFields() {
        final ByteBuffer buf = toByteBuffer(HDR_EMPTY);
        assertThrows(IllegalArgumentException.class,
                () -> Struct.parse(HeaderMessageWithMutableField.class, buf));
    }

    public static class HeaderMsgWithStaticConstant extends Struct {
        private static final String TAG = "HeaderMessage";
        private static final int FIELD_COUNT = 5;

        @Field(order = 0, type = Type.U8, padding = 1)
        public final short mFamily;
        @Field(order = 1, type = Type.U16)
        public final int mLen;
        @Field(order = 2, type = Type.S32)
        public final int mIfindex;
        @Field(order = 3, type = Type.U8)
        public final short mIcmpType;
        @Field(order = 4, type = Type.U8, padding = 6)
        public final short mIcmpCode;

        HeaderMsgWithStaticConstant(final short family, final int len, final int ifindex,
                final short type, final short code) {
            mFamily = family;
            mLen = len;
            mIfindex = ifindex;
            mIcmpType = type;
            mIcmpCode = code;
        }
    }

    @Test
    public void testStaticConstantField() {
        final HeaderMsgWithStaticConstant msg = doParsingMessageTest(HDR_EMPTY,
                HeaderMsgWithStaticConstant.class, ByteOrder.LITTLE_ENDIAN);
        assertEquals(10, msg.mFamily);
        assertEquals(0, msg.mLen);
        assertEquals(15715755, msg.mIfindex);
        assertEquals(134, msg.mIcmpType);
        assertEquals(0, msg.mIcmpCode);

        assertEquals(16, Struct.getSize(HeaderMsgWithStaticConstant.class));
        assertArrayEquals(toByteBuffer(HDR_EMPTY).array(),
                msg.writeToBytes(ByteOrder.LITTLE_ENDIAN));
    }

    public static class MismatchedConstructor extends Struct {
        @Field(order = 0, type = Type.U16) final int mInt1;
        @Field(order = 1, type = Type.U16) final int mInt2;
        MismatchedConstructor(String int1, String int2) {
            mInt1 = Integer.valueOf(int1);
            mInt2 = Integer.valueOf(int2);
        }
    }

    @Test
    public void testMisMatchedConstructor() {
        final ByteBuffer buf = toByteBuffer("1234" + "5678");
        assertThrows(IllegalArgumentException.class,
                () -> Struct.parse(MismatchedConstructor.class, buf));
    }

    public static class ClassWithTwoConstructors extends Struct {
        @Field(order = 0, type = Type.U16) public final int mInt1;
        @Field(order = 1, type = Type.U16) public final int mInt2;
        ClassWithTwoConstructors(String int1, String int2) {
            mInt1 = Integer.valueOf(int1);
            mInt2 = Integer.valueOf(int2);
        }
        ClassWithTwoConstructors(int int1, int int2) {
            mInt1 = int1;
            mInt2 = int2;
        }
    }

    @Test
    public void testClassWithTwoConstructors() {
        final ClassWithTwoConstructors msg = doParsingMessageTest("1234" + "5678",
                ClassWithTwoConstructors.class, ByteOrder.LITTLE_ENDIAN);
        assertEquals(13330 /* 0x3412 */, msg.mInt1);
        assertEquals(30806 /* 0x7856 */, msg.mInt2);

        assertEquals(4, Struct.getSize(ClassWithTwoConstructors.class));
        assertArrayEquals(toByteBuffer("1234" + "5678").array(),
                msg.writeToBytes(ByteOrder.LITTLE_ENDIAN));
    }

    @Test
    public void testInvalidOutputByteBuffer_ZeroCapacity() {
        final ByteBuffer output = ByteBuffer.allocate(0);
        output.order(ByteOrder.LITTLE_ENDIAN);
        final HeaderMsgWithConstructor msg = doParsingMessageTest(HDR_EMPTY,
                HeaderMsgWithConstructor.class, ByteOrder.LITTLE_ENDIAN);
        assertThrows(BufferOverflowException.class, () -> msg.writeToByteBuffer(output));
    }

    @Test
    public void testConsecutiveWrites() {
        final HeaderMsgWithConstructor msg1 = doParsingMessageTest(HDR_EMPTY,
                HeaderMsgWithConstructor.class, ByteOrder.LITTLE_ENDIAN);
        final PrefixMessage msg2 = doParsingMessageTest(OPT_PREF64, PrefixMessage.class,
                ByteOrder.LITTLE_ENDIAN);

        int size = Struct.getSize(HeaderMsgWithConstructor.class)
                + Struct.getSize(PrefixMessage.class);
        final ByteBuffer output = ByteBuffer.allocate(size);
        output.order(ByteOrder.LITTLE_ENDIAN);

        msg1.writeToByteBuffer(output);
        msg2.writeToByteBuffer(output);
        output.flip();

        final ByteBuffer concat = ByteBuffer.allocate(size).put(toByteBuffer(HDR_EMPTY))
                .put(toByteBuffer(OPT_PREF64));
        assertArrayEquals(output.array(), concat.array());
    }

    @Test
    public void testClassesParsedFromCache() throws Exception {
        for (int i = 0; i < 100; i++) {
            final HeaderMsgWithConstructor msg1 = doParsingMessageTest(HDR_EMPTY,
                    HeaderMsgWithConstructor.class, ByteOrder.LITTLE_ENDIAN);
            verifyHeaderParsing(msg1);

            final PrefixMessage msg2 = doParsingMessageTest(OPT_PREF64, PrefixMessage.class,
                    ByteOrder.LITTLE_ENDIAN);
            verifyPrefixByteArrayParsing(msg2);
        }
    }

    public static class BigEndianDataMessage extends Struct {
        @Field(order = 0, type = Type.S32) public int mInt1;
        @Field(order = 1, type = Type.S32) public int mInt2;
        @Field(order = 2, type = Type.UBE16) public int mInt3;
        @Field(order = 3, type = Type.U16) public int mInt4;
        @Field(order = 4, type = Type.U64) public BigInteger mBigInteger1;
        @Field(order = 5, type = Type.UBE64) public BigInteger mBigInteger2;
        @Field(order = 6, type = Type.S64) public long mLong;
    }

    private static final String BIG_ENDIAN_DATA = "00000001" + "fffffffe" + "fffe" + "fffe"
            + "ff00004500002301" + "ff00004500002301" + "ff00004500002301";

    @Test
    public void testBigEndianByteBuffer() {
        final BigEndianDataMessage msg = doParsingMessageTest(BIG_ENDIAN_DATA,
                BigEndianDataMessage.class, ByteOrder.BIG_ENDIAN);

        assertEquals(1, msg.mInt1);
        assertEquals(-2, msg.mInt2);
        assertEquals(65534, msg.mInt3);
        assertEquals(65534, msg.mInt4);
        assertEquals(new BigInteger("18374686776024376065"), msg.mBigInteger1);
        assertEquals(new BigInteger("18374686776024376065"), msg.mBigInteger2);
        assertEquals(0xff00004500002301L, msg.mLong);

        assertEquals(36, Struct.getSize(BigEndianDataMessage.class));
        assertArrayEquals(toByteBuffer(BIG_ENDIAN_DATA).array(),
                msg.writeToBytes(ByteOrder.BIG_ENDIAN));
    }

    private ByteBuffer toByteBuffer(final String hexString) {
        return ByteBuffer.wrap(HexDump.hexStringToByteArray(hexString));
    }

    public static class MacAddressMessage extends Struct {
        public @Field(order = 0, type = Type.EUI48) final MacAddress mMac1;
        public @Field(order = 1, type = Type.EUI48) final MacAddress mMac2;

        MacAddressMessage(final MacAddress mac1, final MacAddress mac2) {
            this.mMac1 = mac1;
            this.mMac2 = mac2;
        }
    }

    @Test
    public void testMacAddressType() {
        final MacAddressMessage msg = doParsingMessageTest("001122334455" + "ffffffffffff",
                MacAddressMessage.class, ByteOrder.BIG_ENDIAN);

        assertEquals(MacAddress.fromString("00:11:22:33:44:55"), msg.mMac1);
        assertEquals(MacAddress.fromString("ff:ff:ff:ff:ff:ff"), msg.mMac2);

        assertEquals(12, Struct.getSize(MacAddressMessage.class));
        assertArrayEquals(toByteBuffer("001122334455" + "ffffffffffff").array(),
                msg.writeToBytes(ByteOrder.BIG_ENDIAN));
    }

    public static class BadMacAddressType extends Struct {
        @Field(order = 0, type = Type.EUI48) byte[] mMac;
    }

    @Test
    public void testIncorrectType_EUI48WithByteArray() {
        assertThrows(IllegalArgumentException.class,
                () -> Struct.parse(BadMacAddressType.class, toByteBuffer("ffffffffffff")));
    }

    @Test
    public void testStructToByteArrayRoundTrip() {
        final SignedDataMessage littleEndianMsg = doParsingMessageTest(SIGNED_DATA,
                SignedDataMessage.class, ByteOrder.LITTLE_ENDIAN);
        assertArrayEquals(toByteBuffer(SIGNED_DATA).array(),
                littleEndianMsg.writeToBytes(ByteOrder.LITTLE_ENDIAN));

        final SignedDataMessage bigEndianMsg = doParsingMessageTest(SIGNED_DATA,
                SignedDataMessage.class, ByteOrder.BIG_ENDIAN);
        assertArrayEquals(toByteBuffer(SIGNED_DATA).array(),
                bigEndianMsg.writeToBytes(ByteOrder.BIG_ENDIAN));

        final SignedDataMessage nativeOrderMsg = ByteOrder.nativeOrder().equals(
                ByteOrder.LITTLE_ENDIAN) ? littleEndianMsg : bigEndianMsg;
        assertArrayEquals(toByteBuffer(SIGNED_DATA).array(),
                nativeOrderMsg.writeToBytes());
    }

    @Test
    public void testStructToByteArray() {
        final SignedDataMessage msg = new SignedDataMessage((byte) -5, (short) 42, (int) 0xff000004,
                (long) 0xff000004ff000005L);
        final String leHexString = "fb" + "2a00" + "040000ff" + "050000ff040000ff";
        final String beHexString = "fb" + "002a" + "ff000004" + "ff000004ff000005";
        final String hexString = ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)
                ? leHexString : beHexString;
        assertArrayEquals(toByteBuffer(hexString).array(), msg.writeToBytes());
    }

    public static class IpAddressMessage extends Struct {
        @Field(order = 0, type = Type.Ipv4Address) public final Inet4Address ipv4Address;
        @Field(order = 1, type = Type.Ipv6Address) public final Inet6Address ipv6Address;

        IpAddressMessage(final Inet4Address ipv4Address, final Inet6Address ipv6Address) {
            this.ipv4Address = ipv4Address;
            this.ipv6Address = ipv6Address;
        }
    }

    @Test
    public void testIpAddressType() {
        final IpAddressMessage msg = doParsingMessageTest("c0a86401"
                + "20010db8000300040005000600070008", IpAddressMessage.class, ByteOrder.BIG_ENDIAN);

        assertEquals(TEST_IPV4_ADDRESS, msg.ipv4Address);
        assertEquals(TEST_IPV6_ADDRESS, msg.ipv6Address);

        assertEquals(20, Struct.getSize(IpAddressMessage.class));
        assertArrayEquals(toByteBuffer("c0a86401" + "20010db8000300040005000600070008").array(),
                msg.writeToBytes(ByteOrder.BIG_ENDIAN));
    }

    @Test
    public void testV4MappedV6Address() {
        final IpAddressMessage msg = doParsingMessageTest("c0a86401"
                + "00000000000000000000ffffc0a86401", IpAddressMessage.class, ByteOrder.BIG_ENDIAN);
        assertEquals(TEST_IPV4_ADDRESS, msg.ipv4Address);
        assertEquals(InetAddressUtils.v4MappedV6Address(TEST_IPV4_ADDRESS), msg.ipv6Address);
    }

    public static class WrongIpAddressType extends Struct {
        @Field(order = 0, type = Type.Ipv4Address) public byte[] ipv4Address;
        @Field(order = 1, type = Type.Ipv6Address) public byte[] ipv6Address;
    }

    @Test
    public void testIncorrectType_IpAddressWithByteArray() {
        assertThrows(IllegalArgumentException.class,
                () -> Struct.parse(WrongIpAddressType.class,
                                   toByteBuffer("c0a86401" + "20010db8000300040005000600070008")));
    }

    public static class FullTypeMessage extends Struct {
        @Field(order = 0, type = Type.U8) public final short u8;
        @Field(order = 1, type = Type.U16) public final int u16;
        @Field(order = 2, type = Type.U32) public final long u32;
        @Field(order = 3, type = Type.U63) public final long u63;
        @Field(order = 4, type = Type.U64) public final BigInteger u64;
        @Field(order = 5, type = Type.S8) public final byte s8;
        @Field(order = 6, type = Type.S16) public final short s16;
        @Field(order = 7, type = Type.S32) public final int s32;
        @Field(order = 8, type = Type.S64) public final long s64;
        @Field(order = 9, type = Type.UBE16) public final int ube16;
        @Field(order = 10, type = Type.UBE32) public final long ube32;
        @Field(order = 11, type = Type.UBE63) public final long ube63;
        @Field(order = 12, type = Type.UBE64) public final BigInteger ube64;
        @Field(order = 13, type = Type.ByteArray, arraysize = 12) public final byte[] bytes;
        @Field(order = 14, type = Type.EUI48) public final MacAddress eui48;
        @Field(order = 15, type = Type.Ipv4Address) public final Inet4Address ipv4Address;
        @Field(order = 16, type = Type.Ipv6Address) public final Inet6Address ipv6Address;

        FullTypeMessage(final short u8, final int u16, final long u32, final long u63,
                final BigInteger u64, final byte s8, final short s16, final int s32, final long s64,
                final int ube16, final long ube32, final long ube63, final BigInteger ube64,
                final byte[] bytes, final MacAddress eui48, final Inet4Address ipv4Address,
                final Inet6Address ipv6Address) {
            this.u8 = u8;
            this.u16 = u16;
            this.u32 = u32;
            this.u63 = u63;
            this.u64 = u64;
            this.s8 = s8;
            this.s16 = s16;
            this.s32 = s32;
            this.s64 = s64;
            this.ube16 = ube16;
            this.ube32 = ube32;
            this.ube63 = ube63;
            this.ube64 = ube64;
            this.bytes = bytes;
            this.eui48 = eui48;
            this.ipv4Address = ipv4Address;
            this.ipv6Address = ipv6Address;
        }
    }

    private static final String FULL_TYPE_DATA = "ff" + "ffff" + "ffffffff" + "7fffffffffffffff"
            + "ffffffffffffffff" + "7f" + "7fff" + "7fffffff" + "7fffffffffffffff" + "7fff"
            + "7fffffff" + "7fffffffffffffff" + "ffffffffffffffff" + "20010db80003000400050006"
            + "001122334455" + "c0a86401" + "20010db8000300040005000600070008";
    private static final String FULL_TYPE_DATA_DIFF_MAC = "ff" + "ffff" + "ffffffff"
            + "7fffffffffffffff" + "ffffffffffffffff" + "7f" + "7fff" + "7fffffff"
            + "7fffffffffffffff" + "7fff" + "7fffffff" + "7fffffffffffffff" + "ffffffffffffffff"
            + "20010db80003000400050006" + "112233445566"
            + "c0a86401" + "20010db8000300040005000600070008";
    private static final String FULL_TYPE_DATA_DIFF_LONG = "ff" + "ffff" + "ffffffff"
            + "7ffffffffffffffe" + "ffffffffffffffff" + "7f" + "7fff" + "7fffffff"
            + "7fffffffffffffff" + "7fff" + "7fffffff" + "7fffffffffffffff" + "ffffffffffffffff"
            + "20010db80003000400050006" + "001122334455"
            + "c0a86401" + "20010db8000300040005000600070008";
    private static final String FULL_TYPE_DATA_DIFF_INTEGER = "ff" + "ffff" + "ffffffff"
            + "7fffffffffffffff" + "ffffffffffffffff" + "7f" + "7fff" + "7fffffff"
            + "7fffffffffffffff" + "7fff" + "ffffff7f" + "7fffffffffffffff" + "ffffffffffffffff"
            + "20010db80003000400050006" + "001122334455"
            + "c0a86401" + "20010db8000300040005000600070008";
    private static final String FULL_TYPE_DATA_DIFF_IPV4 = "ff" + "ffff" + "ffffffff"
            + "7fffffffffffffff" + "ffffffffffffffff" + "7f" + "7fff" + "7fffffff"
            + "7fffffffffffffff" + "7fff" + "ffffff7f" + "7fffffffffffffff" + "ffffffffffffffff"
            + "20010db80003000400050006" + "001122334455"
            + "c0a81010" + "20010db8000300040005000600070008";
    private static final String FULL_TYPE_DATA_DIFF_IPV6 = "ff" + "ffff" + "ffffffff"
            + "7fffffffffffffff" + "ffffffffffffffff" + "7f" + "7fff" + "7fffffff"
            + "7fffffffffffffff" + "7fff" + "ffffff7f" + "7fffffffffffffff" + "ffffffffffffffff"
            + "20010db80003000400050006" + "001122334455"
            + "c0a86401" + "20010db800030004000500060007000a";
    @Test
    public void testStructClass_equals() {
        final FullTypeMessage msg = doParsingMessageTest(FULL_TYPE_DATA, FullTypeMessage.class,
                ByteOrder.BIG_ENDIAN);

        assertEquals(255, msg.u8);
        assertEquals(65535, msg.u16);
        assertEquals(4294967295L, msg.u32);
        assertEquals(9223372036854775807L, msg.u63);
        assertEquals(new BigInteger("18446744073709551615"), msg.u64);
        assertEquals(127, msg.s8);
        assertEquals(32767, msg.s16);
        assertEquals(2147483647, msg.s32);
        assertEquals(9223372036854775807L, msg.s64);
        assertEquals(32767, msg.ube16);
        assertEquals(2147483647, msg.ube32);
        assertEquals(9223372036854775807L, msg.ube63);
        assertEquals(new BigInteger("18446744073709551615"), msg.ube64);
        assertArrayEquals(TEST_PREFIX64, msg.bytes);
        assertEquals(MacAddress.fromString("00:11:22:33:44:55"), msg.eui48);
        assertEquals(TEST_IPV4_ADDRESS, msg.ipv4Address);
        assertEquals(TEST_IPV6_ADDRESS, msg.ipv6Address);

        assertEquals(98, msg.getSize(FullTypeMessage.class));
        assertArrayEquals(toByteBuffer(FULL_TYPE_DATA).array(),
                msg.writeToBytes(ByteOrder.BIG_ENDIAN));

        final FullTypeMessage msg1 = new FullTypeMessage((short) 0xff, (int) 0xffff,
                (long) 0xffffffffL, (long) 0x7fffffffffffffffL,
                new BigInteger("18446744073709551615"), (byte) 0x7f, (short) 0x7fff,
                (int) 0x7fffffff, (long) 0x7fffffffffffffffL, (int) 0x7fff, (long) 0x7fffffffL,
                (long) 0x7fffffffffffffffL, new BigInteger("18446744073709551615"), TEST_PREFIX64,
                MacAddress.fromString("00:11:22:33:44:55"), TEST_IPV4_ADDRESS, TEST_IPV6_ADDRESS);
        assertTrue(msg.equals(msg1));
    }

    public static class FullTypeMessageWithDupType extends Struct {
        @Field(order = 0, type = Type.U8) public final short u8;
        @Field(order = 1, type = Type.U16) public final int u16;
        @Field(order = 2, type = Type.U32) public final long u32;
        @Field(order = 3, type = Type.S64) public final long u63; // old: U63, new: S64
        @Field(order = 4, type = Type.UBE64) public final BigInteger u64; // old: U64, new: UBE64
        @Field(order = 5, type = Type.S8) public final byte s8;
        @Field(order = 6, type = Type.S16) public final short s16;
        @Field(order = 7, type = Type.S32) public final int s32;
        @Field(order = 8, type = Type.S64) public final long s64;
        @Field(order = 9, type = Type.U16) public final int ube16; // old:UBE16, new: U16
        @Field(order = 10, type = Type.UBE32) public final long ube32;
        @Field(order = 11, type = Type.UBE63) public final long ube63;
        @Field(order = 12, type = Type.UBE64) public final BigInteger ube64;
        @Field(order = 13, type = Type.ByteArray, arraysize = 12) public final byte[] bytes;
        @Field(order = 14, type = Type.EUI48) public final MacAddress eui48;
        @Field(order = 15, type = Type.Ipv4Address) public final Inet4Address ipv4Address;
        @Field(order = 16, type = Type.Ipv6Address) public final Inet6Address ipv6Address;

        FullTypeMessageWithDupType(final short u8, final int u16, final long u32, final long u63,
                final BigInteger u64, final byte s8, final short s16, final int s32, final long s64,
                final int ube16, final long ube32, final long ube63, final BigInteger ube64,
                final byte[] bytes, final MacAddress eui48, final Inet4Address ipv4Address,
                final Inet6Address ipv6Address) {
            this.u8 = u8;
            this.u16 = u16;
            this.u32 = u32;
            this.u63 = u63;
            this.u64 = u64;
            this.s8 = s8;
            this.s16 = s16;
            this.s32 = s32;
            this.s64 = s64;
            this.ube16 = ube16;
            this.ube32 = ube32;
            this.ube63 = ube63;
            this.ube64 = ube64;
            this.bytes = bytes;
            this.eui48 = eui48;
            this.ipv4Address = ipv4Address;
            this.ipv6Address = ipv6Address;
        }
    }

    @Test
    public void testStructClass_notEqualWithDifferentClass() {
        final FullTypeMessage msg = doParsingMessageTest(FULL_TYPE_DATA, FullTypeMessage.class,
                ByteOrder.BIG_ENDIAN);
        final FullTypeMessageWithDupType msg1 = doParsingMessageTest(FULL_TYPE_DATA,
                FullTypeMessageWithDupType.class, ByteOrder.BIG_ENDIAN);

        assertFalse(msg.equals(msg1));
    }

    @Test
    public void testStructClass_notEqualWithDifferentValue() {
        final FullTypeMessage msg = doParsingMessageTest(FULL_TYPE_DATA, FullTypeMessage.class,
                ByteOrder.BIG_ENDIAN);

        // With different MAC address.
        final FullTypeMessage msg1 = doParsingMessageTest(FULL_TYPE_DATA_DIFF_MAC,
                FullTypeMessage.class, ByteOrder.BIG_ENDIAN);
        assertNotEquals(msg.eui48, msg1.eui48);
        assertFalse(msg.equals(msg1));

        // With different byte array.
        final FullTypeMessage msg2 = doParsingMessageTest(FULL_TYPE_DATA, FullTypeMessage.class,
                ByteOrder.BIG_ENDIAN);
        msg2.bytes[5] = (byte) 42; // change one byte in the array.
        assertFalse(msg.equals(msg2));

        // With different Long primitive.
        final FullTypeMessage msg3 = doParsingMessageTest(FULL_TYPE_DATA_DIFF_LONG,
                FullTypeMessage.class, ByteOrder.BIG_ENDIAN);
        assertNotEquals(msg.u63, msg3.u63);
        assertFalse(msg.equals(msg3));

        // With different Integer primitive.
        final FullTypeMessage msg4 = doParsingMessageTest(FULL_TYPE_DATA_DIFF_INTEGER,
                FullTypeMessage.class, ByteOrder.BIG_ENDIAN);
        assertNotEquals(msg.ube32, msg4.ube32);
        assertFalse(msg.equals(msg4));

        // With different IPv4 address.
        final FullTypeMessage msg5 = doParsingMessageTest(FULL_TYPE_DATA_DIFF_IPV4,
                FullTypeMessage.class, ByteOrder.BIG_ENDIAN);
        assertNotEquals(msg.ipv4Address, msg5.ipv4Address);
        assertFalse(msg.equals(msg5));

        // With different IPv6 address.
        final FullTypeMessage msg6 = doParsingMessageTest(FULL_TYPE_DATA_DIFF_IPV6,
                FullTypeMessage.class, ByteOrder.BIG_ENDIAN);
        assertNotEquals(msg.ipv6Address, msg6.ipv6Address);
        assertFalse(msg.equals(msg6));
    }

    @Test
    public void testStructClass_toString() {
        final String expected = "u8: 255, u16: 65535, u32: 4294967295,"
                + " u63: 9223372036854775807, u64: 18446744073709551615, s8: 127, s16: 32767,"
                + " s32: 2147483647, s64: 9223372036854775807, ube16: 32767, ube32: 2147483647,"
                + " ube63: 9223372036854775807, ube64: 18446744073709551615,"
                + " bytes: 0x20010DB80003000400050006,"
                + " eui48: 00:11:22:33:44:55,"
                + " ipv4Address: 192.168.100.1,"
                + " ipv6Address: 2001:db8:3:4:5:6:7:8";

        final FullTypeMessage msg = doParsingMessageTest(FULL_TYPE_DATA, FullTypeMessage.class,
                ByteOrder.BIG_ENDIAN);
        assertEquals(expected, msg.toString());
    }

    @Test
    public void testStructClass_toStringWithNullMember() {
        final String expected = "u8: 255, u16: 65535, u32: 4294967295,"
                + " u63: 9223372036854775807, u64: null, s8: 127, s16: 32767,"
                + " s32: 2147483647, s64: 9223372036854775807, ube16: 32767, ube32: 2147483647,"
                + " ube63: 9223372036854775807, ube64: 18446744073709551615,"
                + " bytes: null, eui48: null, ipv4Address: 192.168.100.1,"
                + " ipv6Address: null";

        final FullTypeMessage msg = new FullTypeMessage((short) 0xff, (int) 0xffff,
                (long) 0xffffffffL, (long) 0x7fffffffffffffffL,
                null /* u64 */, (byte) 0x7f, (short) 0x7fff,
                (int) 0x7fffffff, (long) 0x7fffffffffffffffL, (int) 0x7fff, (long) 0x7fffffffL,
                (long) 0x7fffffffffffffffL, new BigInteger("18446744073709551615"),
                null /* bytes */, null /* eui48 */, TEST_IPV4_ADDRESS, null /* ipv6Address */);
        assertEquals(expected, msg.toString());
    }

    @Test
    public void testStructClass_hashcode() {
        final FullTypeMessage msg = doParsingMessageTest(FULL_TYPE_DATA, FullTypeMessage.class,
                ByteOrder.BIG_ENDIAN);
        final FullTypeMessage msg1 = doParsingMessageTest(FULL_TYPE_DATA, FullTypeMessage.class,
                ByteOrder.BIG_ENDIAN);

        assertNotEquals(0, msg.hashCode());
        assertNotEquals(0, msg1.hashCode());
        assertTrue(msg.equals(msg1));
        assertEquals(msg.hashCode(), msg1.hashCode());
    }

    public static class InvalidByteArray extends Struct {
        @Field(order = 0, type = Type.ByteArray, arraysize = 12) public byte[] bytes;
    }

    @Test
    public void testStructClass_WrongByteArraySize() {
        final InvalidByteArray msg = doParsingMessageTest("20010db80003000400050006",
                InvalidByteArray.class, ByteOrder.BIG_ENDIAN);

        // Actual byte array size doesn't match the size declared in the annotation.
        msg.bytes = new byte[16];
        assertThrows(IllegalStateException.class, () -> msg.writeToBytes());

        final ByteBuffer output = ByteBuffer.allocate(Struct.getSize(InvalidByteArray.class));
        output.order(ByteOrder.LITTLE_ENDIAN);
        assertThrows(IllegalStateException.class, () -> msg.writeToByteBuffer(output));
    }

    @Test
    public void testStructClass_NullByteArray() {
        final InvalidByteArray msg = doParsingMessageTest("20010db80003000400050006",
                InvalidByteArray.class, ByteOrder.BIG_ENDIAN);

        msg.bytes = null;
        assertThrows(NullPointerException.class, () -> msg.writeToBytes());

        final ByteBuffer output = ByteBuffer.allocate(Struct.getSize(InvalidByteArray.class));
        output.order(ByteOrder.LITTLE_ENDIAN);
        assertThrows(NullPointerException.class, () -> msg.writeToByteBuffer(output));
    }

    @Test
    public void testStructClass_ParsingByteArrayAfterInitialization() {
        InvalidByteArray msg = new InvalidByteArray();
        msg.bytes = new byte[]{(byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03};

        // Although bytes member has been initialized with the length different with
        // annotation size, parsing from ByteBuffer will get bytes member have the
        // reference to byte array with correct size.
        msg = doParsingMessageTest("20010db80003000400050006", InvalidByteArray.class,
                ByteOrder.BIG_ENDIAN);
        assertArrayEquals(TEST_PREFIX64, msg.bytes);
    }
}
