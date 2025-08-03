/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.net.cts;

import static android.system.OsConstants.IPPROTO_IPV6;
import static android.system.OsConstants.IPPROTO_UDP;

import android.util.ArraySet;

import com.android.internal.net.ipsec.ike.crypto.AesXCbcImpl;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class PacketUtils {
    private static final String TAG = PacketUtils.class.getSimpleName();

    private static final int DATA_BUFFER_LEN = 4096;

    static final int IP4_HDRLEN = 20;
    static final int IP6_HDRLEN = 40;
    static final int UDP_HDRLEN = 8;
    static final int TCP_HDRLEN = 20;
    static final int TCP_HDRLEN_WITH_TIMESTAMP_OPT = TCP_HDRLEN + 12;
    static final int ESP_HDRLEN = 8;
    static final int ESP_BLK_SIZE = 4; // ESP has to be 4-byte aligned
    static final int ESP_TRAILER_LEN = 2;

    // Not defined in OsConstants
    static final int IPPROTO_IPV4 = 4;
    static final int IPPROTO_ESP = 50;

    // Encryption parameters
    static final int AES_CBC_IV_LEN = 16;
    static final int AES_CBC_BLK_SIZE = 16;
    static final int AES_CTR_SALT_LEN = 4;

    static final int AES_CTR_KEY_LEN_20 = 20;
    static final int AES_CTR_KEY_LEN_28 = 28;
    static final int AES_CTR_KEY_LEN_36 = 36;
    static final int AES_CTR_BLK_SIZE = ESP_BLK_SIZE;
    static final int AES_CTR_IV_LEN = 8;

    // AEAD parameters
    static final int AES_GCM_IV_LEN = 8;
    static final int AES_GCM_BLK_SIZE = 4;
    static final int CHACHA20_POLY1305_KEY_LEN = 36;
    static final int CHACHA20_POLY1305_BLK_SIZE = ESP_BLK_SIZE;
    static final int CHACHA20_POLY1305_IV_LEN = 8;
    static final int CHACHA20_POLY1305_SALT_LEN = 4;
    static final int CHACHA20_POLY1305_ICV_LEN = 16;

    // Authentication parameters
    static final int HMAC_SHA256_ICV_LEN = 16;
    static final int HMAC_SHA512_KEY_LEN = 64;
    static final int HMAC_SHA512_ICV_LEN = 32;
    static final int AES_XCBC_KEY_LEN = 16;
    static final int AES_XCBC_ICV_LEN = 12;
    static final int AES_CMAC_KEY_LEN = 16;
    static final int AES_CMAC_ICV_LEN = 12;

    // Block counter field should be 32 bits and starts from value one as per RFC 3686
    static final byte[] AES_CTR_INITIAL_COUNTER = new byte[] {0x00, 0x00, 0x00, 0x01};

    // Encryption algorithms
    static final String AES = "AES";
    static final String AES_CBC = "AES/CBC/NoPadding";
    static final String AES_CTR = "AES/CTR/NoPadding";

    // AEAD algorithms
    static final String CHACHA20_POLY1305 = "ChaCha20/Poly1305/NoPadding";

    // Authentication algorithms
    static final String HMAC_MD5 = "HmacMD5";
    static final String HMAC_SHA1 = "HmacSHA1";
    static final String HMAC_SHA_256 = "HmacSHA256";
    static final String HMAC_SHA_384 = "HmacSHA384";
    static final String HMAC_SHA_512 = "HmacSHA512";
    static final String AES_CMAC = "AESCMAC";
    static final String AES_XCBC = "AesXCbc";

    public interface Payload {
        byte[] getPacketBytes(IpHeader header) throws Exception;

        void addPacketBytes(IpHeader header, ByteBuffer resultBuffer) throws Exception;

        short length();

        int getProtocolId();
    }

    public abstract static class IpHeader {

        public final byte proto;
        public final InetAddress srcAddr;
        public final InetAddress dstAddr;
        public final Payload payload;

        public IpHeader(int proto, InetAddress src, InetAddress dst, Payload payload) {
            this.proto = (byte) proto;
            this.srcAddr = src;
            this.dstAddr = dst;
            this.payload = payload;
        }

        public abstract byte[] getPacketBytes() throws Exception;

        public abstract int getProtocolId();
    }

    public static class Ip4Header extends IpHeader {
        private short checksum;

        public Ip4Header(int proto, Inet4Address src, Inet4Address dst, Payload payload) {
            super(proto, src, dst, payload);
        }

        public byte[] getPacketBytes() throws Exception {
            ByteBuffer resultBuffer = buildHeader();
            payload.addPacketBytes(this, resultBuffer);

            return getByteArrayFromBuffer(resultBuffer);
        }

        public ByteBuffer buildHeader() {
            ByteBuffer bb = ByteBuffer.allocate(DATA_BUFFER_LEN);

            // Version, IHL
            bb.put((byte) (0x45));

            // DCSP, ECN
            bb.put((byte) 0);

            // Total Length
            bb.putShort((short) (IP4_HDRLEN + payload.length()));

            // Empty for Identification, Flags and Fragment Offset
            bb.putShort((short) 0);
            bb.put((byte) 0x40);
            bb.put((byte) 0x00);

            // TTL
            bb.put((byte) 64);

            // Protocol
            bb.put(proto);

            // Header Checksum
            final int ipChecksumOffset = bb.position();
            bb.putShort((short) 0);

            // Src/Dst addresses
            bb.put(srcAddr.getAddress());
            bb.put(dstAddr.getAddress());

            bb.putShort(ipChecksumOffset, calculateChecksum(bb));

            return bb;
        }

        private short calculateChecksum(ByteBuffer bb) {
            int checksum = 0;

            // Calculate sum of 16-bit values, excluding checksum. IPv4 headers are always 32-bit
            // aligned, so no special cases needed for unaligned values.
            ShortBuffer shortBuffer = ByteBuffer.wrap(getByteArrayFromBuffer(bb)).asShortBuffer();
            while (shortBuffer.hasRemaining()) {
                short val = shortBuffer.get();

                // Wrap as needed
                checksum = addAndWrapForChecksum(checksum, val);
            }

            return onesComplement(checksum);
        }

        public int getProtocolId() {
            return IPPROTO_IPV4;
        }
    }

    public static class Ip6Header extends IpHeader {
        public Ip6Header(int nextHeader, Inet6Address src, Inet6Address dst, Payload payload) {
            super(nextHeader, src, dst, payload);
        }

        public byte[] getPacketBytes() throws Exception {
            ByteBuffer bb = ByteBuffer.allocate(DATA_BUFFER_LEN);

            // Version | Traffic Class (First 4 bits)
            bb.put((byte) 0x60);

            // Traffic class (Last 4 bits), Flow Label
            bb.put((byte) 0);
            bb.put((byte) 0);
            bb.put((byte) 0);

            // Payload Length
            bb.putShort((short) payload.length());

            // Next Header
            bb.put(proto);

            // Hop Limit
            bb.put((byte) 64);

            // Src/Dst addresses
            bb.put(srcAddr.getAddress());
            bb.put(dstAddr.getAddress());

            // Payload
            payload.addPacketBytes(this, bb);

            return getByteArrayFromBuffer(bb);
        }

        public int getProtocolId() {
            return IPPROTO_IPV6;
        }
    }

    public static class BytePayload implements Payload {
        public final byte[] payload;

        public BytePayload(byte[] payload) {
            this.payload = payload;
        }

        public int getProtocolId() {
            return -1;
        }

        public byte[] getPacketBytes(IpHeader header) {
            ByteBuffer bb = ByteBuffer.allocate(DATA_BUFFER_LEN);

            addPacketBytes(header, bb);
            return getByteArrayFromBuffer(bb);
        }

        public void addPacketBytes(IpHeader header, ByteBuffer resultBuffer) {
            resultBuffer.put(payload);
        }

        public short length() {
            return (short) payload.length;
        }
    }

    public static class UdpHeader implements Payload {

        public final short srcPort;
        public final short dstPort;
        public final Payload payload;

        public UdpHeader(int srcPort, int dstPort, Payload payload) {
            this.srcPort = (short) srcPort;
            this.dstPort = (short) dstPort;
            this.payload = payload;
        }

        public int getProtocolId() {
            return IPPROTO_UDP;
        }

        public short length() {
            return (short) (payload.length() + 8);
        }

        public byte[] getPacketBytes(IpHeader header) throws Exception {
            ByteBuffer bb = ByteBuffer.allocate(DATA_BUFFER_LEN);

            addPacketBytes(header, bb);
            return getByteArrayFromBuffer(bb);
        }

        public void addPacketBytes(IpHeader header, ByteBuffer resultBuffer) throws Exception {
            // Source, Destination port
            resultBuffer.putShort(srcPort);
            resultBuffer.putShort(dstPort);

            // Payload Length
            resultBuffer.putShort(length());

            // Get payload bytes for checksum + payload
            ByteBuffer payloadBuffer = ByteBuffer.allocate(DATA_BUFFER_LEN);
            payload.addPacketBytes(header, payloadBuffer);
            byte[] payloadBytes = getByteArrayFromBuffer(payloadBuffer);

            // Checksum
            resultBuffer.putShort(calculateChecksum(header, payloadBytes));

            // Payload
            resultBuffer.put(payloadBytes);
        }

        private short calculateChecksum(IpHeader header, byte[] payloadBytes) throws Exception {
            int newChecksum = 0;
            ShortBuffer srcBuffer = ByteBuffer.wrap(header.srcAddr.getAddress()).asShortBuffer();
            ShortBuffer dstBuffer = ByteBuffer.wrap(header.dstAddr.getAddress()).asShortBuffer();

            while (srcBuffer.hasRemaining() || dstBuffer.hasRemaining()) {
                short val = srcBuffer.hasRemaining() ? srcBuffer.get() : dstBuffer.get();

                // Wrap as needed
                newChecksum = addAndWrapForChecksum(newChecksum, val);
            }

            // Add pseudo-header values. Proto is 0-padded, so just use the byte.
            newChecksum = addAndWrapForChecksum(newChecksum, header.proto);
            newChecksum = addAndWrapForChecksum(newChecksum, length());
            newChecksum = addAndWrapForChecksum(newChecksum, srcPort);
            newChecksum = addAndWrapForChecksum(newChecksum, dstPort);
            newChecksum = addAndWrapForChecksum(newChecksum, length());

            ShortBuffer payloadShortBuffer = ByteBuffer.wrap(payloadBytes).asShortBuffer();
            while (payloadShortBuffer.hasRemaining()) {
                newChecksum = addAndWrapForChecksum(newChecksum, payloadShortBuffer.get());
            }
            if (payload.length() % 2 != 0) {
                newChecksum =
                        addAndWrapForChecksum(
                                newChecksum, (payloadBytes[payloadBytes.length - 1] << 8));
            }

            return onesComplement(newChecksum);
        }
    }

    public static class EspHeader implements Payload {
        public final int nextHeader;
        public final int spi;
        public final int seqNum;
        public final byte[] payload;
        public final EspCipher cipher;
        public final EspAuth auth;

        /**
         * Generic constructor for ESP headers.
         *
         * <p>For Tunnel mode, payload will be a full IP header + attached payloads
         *
         * <p>For Transport mode, payload will be only the attached payloads, but with the checksum
         * calculated using the pre-encryption IP header
         */
        public EspHeader(int nextHeader, int spi, int seqNum, byte[] key, byte[] payload) {
            this(nextHeader, spi, seqNum, payload, getDefaultCipher(key), getDefaultAuth(key));
        }

        /**
         * Generic constructor for ESP headers that allows configuring encryption and authentication
         * algortihms.
         *
         * <p>For Tunnel mode, payload will be a full IP header + attached payloads
         *
         * <p>For Transport mode, payload will be only the attached payloads, but with the checksum
         * calculated using the pre-encryption IP header
         */
        public EspHeader(
                int nextHeader,
                int spi,
                int seqNum,
                byte[] payload,
                EspCipher cipher,
                EspAuth auth) {
            this.nextHeader = nextHeader;
            this.spi = spi;
            this.seqNum = seqNum;
            this.payload = payload;
            this.cipher = cipher;
            this.auth = auth;

            if (cipher instanceof EspCipherNull && auth instanceof EspAuthNull) {
                throw new IllegalArgumentException("No algorithm is provided");
            }

            if (cipher instanceof EspAeadCipher && !(auth instanceof EspAuthNull)) {
                throw new IllegalArgumentException(
                        "AEAD is provided with an authentication" + " algorithm.");
            }
        }

        private static EspCipher getDefaultCipher(byte[] key) {
            return new EspCryptCipher(AES_CBC, AES_CBC_BLK_SIZE, key, AES_CBC_IV_LEN);
        }

        private static EspAuth getDefaultAuth(byte[] key) {
            return new EspAuth(HMAC_SHA_256, key, HMAC_SHA256_ICV_LEN);
        }

        public int getProtocolId() {
            return IPPROTO_ESP;
        }

        public short length() {
            final int icvLen =
                    cipher instanceof EspAeadCipher ? ((EspAeadCipher) cipher).icvLen : auth.icvLen;
            return calculateEspPacketSize(
                    payload.length, cipher.ivLen, cipher.blockSize, icvLen * 8);
        }

        public byte[] getPacketBytes(IpHeader header) throws Exception {
            ByteBuffer bb = ByteBuffer.allocate(DATA_BUFFER_LEN);

            addPacketBytes(header, bb);
            return getByteArrayFromBuffer(bb);
        }

        public void addPacketBytes(IpHeader header, ByteBuffer resultBuffer) throws Exception {
            ByteBuffer espPayloadBuffer = ByteBuffer.allocate(DATA_BUFFER_LEN);
            espPayloadBuffer.putInt(spi);
            espPayloadBuffer.putInt(seqNum);

            espPayloadBuffer.put(cipher.getCipherText(nextHeader, payload, spi, seqNum));
            espPayloadBuffer.put(auth.getIcv(getByteArrayFromBuffer(espPayloadBuffer)));

            resultBuffer.put(getByteArrayFromBuffer(espPayloadBuffer));
        }
    }

    private static int addAndWrapForChecksum(int currentChecksum, int value) {
        currentChecksum += value & 0x0000ffff;

        // Wrap anything beyond the first 16 bits, and add to lower order bits
        return (currentChecksum >>> 16) + (currentChecksum & 0x0000ffff);
    }

    private static short onesComplement(int val) {
        val = (val >>> 16) + (val & 0xffff);

        if (val == 0) return 0;
        return (short) ((~val) & 0xffff);
    }

    public static short calculateEspPacketSize(
            int payloadLen, int cryptIvLength, int cryptBlockSize, int authTruncLen) {
        final int ICV_LEN = authTruncLen / 8; // Auth trailer; based on truncation length

        // Align to block size of encryption algorithm
        payloadLen = calculateEspEncryptedLength(payloadLen, cryptBlockSize);
        payloadLen += cryptIvLength; // Initialization Vector
        return (short) (payloadLen + ESP_HDRLEN + ICV_LEN);
    }

    private static int calculateEspEncryptedLength(int payloadLen, int cryptBlockSize) {
        payloadLen += 2; // ESP trailer

        // Align to block size of encryption algorithm
        return payloadLen + calculateEspPadLen(payloadLen, cryptBlockSize);
    }

    private static int calculateEspPadLen(int payloadLen, int cryptBlockSize) {
        return (cryptBlockSize - (payloadLen % cryptBlockSize)) % cryptBlockSize;
    }

    private static byte[] getByteArrayFromBuffer(ByteBuffer buffer) {
        return Arrays.copyOfRange(buffer.array(), 0, buffer.position());
    }

    public static IpHeader getIpHeader(
            int protocol, InetAddress src, InetAddress dst, Payload payload) {
        if ((src instanceof Inet6Address) != (dst instanceof Inet6Address)) {
            throw new IllegalArgumentException("Invalid src/dst address combination");
        }

        if (src instanceof Inet6Address) {
            return new Ip6Header(protocol, (Inet6Address) src, (Inet6Address) dst, payload);
        } else {
            return new Ip4Header(protocol, (Inet4Address) src, (Inet4Address) dst, payload);
        }
    }

    public abstract static class EspCipher {
        protected static final int SALT_LEN_UNUSED = 0;

        public final String algoName;
        public final int blockSize;
        public final byte[] key;
        public final int ivLen;
        public final int saltLen;
        protected byte[] mIv;

        public EspCipher(String algoName, int blockSize, byte[] key, int ivLen, int saltLen) {
            this.algoName = algoName;
            this.blockSize = blockSize;
            this.key = key;
            this.ivLen = ivLen;
            this.saltLen = saltLen;
            this.mIv = getIv(ivLen);
        }

        public void updateIv(byte[] iv) {
            this.mIv = iv;
        }

        public static byte[] getPaddedPayload(int nextHeader, byte[] payload, int blockSize) {
            final int paddedLen = calculateEspEncryptedLength(payload.length, blockSize);
            final ByteBuffer paddedPayload = ByteBuffer.allocate(paddedLen);
            paddedPayload.put(payload);

            // Add padding - consecutive integers from 0x01
            byte pad = 1;
            while (paddedPayload.position() < paddedPayload.limit() - ESP_TRAILER_LEN) {
                paddedPayload.put((byte) pad++);
            }

            // Add padding length and next header
            paddedPayload.put((byte) (paddedLen - ESP_TRAILER_LEN - payload.length));
            paddedPayload.put((byte) nextHeader);

            return getByteArrayFromBuffer(paddedPayload);
        }

        private static byte[] getIv(int ivLen) {
            final byte[] iv = new byte[ivLen];
            new SecureRandom().nextBytes(iv);
            return iv;
        }

        public abstract byte[] getCipherText(int nextHeader, byte[] payload, int spi, int seqNum)
                throws GeneralSecurityException;
    }

    public static final class EspCipherNull extends EspCipher {
        private static final String CRYPT_NULL = "CRYPT_NULL";
        private static final int IV_LEN_UNUSED = 0;
        private static final byte[] KEY_UNUSED = new byte[0];

        private static final EspCipherNull sInstance = new EspCipherNull();

        private EspCipherNull() {
            super(CRYPT_NULL, ESP_BLK_SIZE, KEY_UNUSED, IV_LEN_UNUSED, SALT_LEN_UNUSED);
        }

        public static EspCipherNull getInstance() {
            return sInstance;
        }

        @Override
        public byte[] getCipherText(int nextHeader, byte[] payload, int spi, int seqNum)
                throws GeneralSecurityException {
            return getPaddedPayload(nextHeader, payload, blockSize);
        }
    }

    public static final class EspCryptCipher extends EspCipher {
        public EspCryptCipher(String algoName, int blockSize, byte[] key, int ivLen) {
            this(algoName, blockSize, key, ivLen, SALT_LEN_UNUSED);
        }

        public EspCryptCipher(String algoName, int blockSize, byte[] key, int ivLen, int saltLen) {
            super(algoName, blockSize, key, ivLen, saltLen);
        }

        @Override
        public byte[] getCipherText(int nextHeader, byte[] payload, int spi, int seqNum)
                throws GeneralSecurityException {
            final IvParameterSpec ivParameterSpec;
            final SecretKeySpec secretKeySpec;

            if (AES_CBC.equals(algoName)) {
                ivParameterSpec = new IvParameterSpec(mIv);
                secretKeySpec = new SecretKeySpec(key, algoName);
            } else if (AES_CTR.equals(algoName)) {
                // Provided key consists of encryption/decryption key plus 4-byte salt. Salt is used
                // with ESP payload IV and initial block counter value to build IvParameterSpec.
                final byte[] secretKey = Arrays.copyOfRange(key, 0, key.length - saltLen);
                final byte[] salt = Arrays.copyOfRange(key, secretKey.length, key.length);
                secretKeySpec = new SecretKeySpec(secretKey, algoName);

                final ByteBuffer ivParameterBuffer =
                        ByteBuffer.allocate(mIv.length + saltLen + AES_CTR_INITIAL_COUNTER.length);
                ivParameterBuffer.put(salt);
                ivParameterBuffer.put(mIv);
                ivParameterBuffer.put(AES_CTR_INITIAL_COUNTER);
                ivParameterSpec = new IvParameterSpec(ivParameterBuffer.array());
            } else {
                throw new IllegalArgumentException("Invalid algorithm " + algoName);
            }

            // Encrypt payload
            final Cipher cipher = Cipher.getInstance(algoName);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);
            final byte[] encrypted =
                    cipher.doFinal(getPaddedPayload(nextHeader, payload, blockSize));

            // Build ciphertext
            final ByteBuffer cipherText = ByteBuffer.allocate(mIv.length + encrypted.length);
            cipherText.put(mIv);
            cipherText.put(encrypted);

            return getByteArrayFromBuffer(cipherText);
        }
    }

    public static final class EspAeadCipher extends EspCipher {
        public final int icvLen;

        public EspAeadCipher(
                String algoName, int blockSize, byte[] key, int ivLen, int icvLen, int saltLen) {
            super(algoName, blockSize, key, ivLen, saltLen);
            this.icvLen = icvLen;
        }

        @Override
        public byte[] getCipherText(int nextHeader, byte[] payload, int spi, int seqNum)
                throws GeneralSecurityException {
            // Provided key consists of encryption/decryption key plus salt. Salt is used
            // with ESP payload IV to build IvParameterSpec.
            final byte[] secretKey = Arrays.copyOfRange(key, 0, key.length - saltLen);
            final byte[] salt = Arrays.copyOfRange(key, secretKey.length, key.length);

            final SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey, algoName);

            final ByteBuffer ivParameterBuffer = ByteBuffer.allocate(saltLen + mIv.length);
            ivParameterBuffer.put(salt);
            ivParameterBuffer.put(mIv);
            final IvParameterSpec ivParameterSpec = new IvParameterSpec(ivParameterBuffer.array());

            final ByteBuffer aadBuffer = ByteBuffer.allocate(ESP_HDRLEN);
            aadBuffer.putInt(spi);
            aadBuffer.putInt(seqNum);

            // Encrypt payload
            final Cipher cipher = Cipher.getInstance(algoName);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);
            cipher.updateAAD(aadBuffer.array());
            final byte[] encryptedTextAndIcv =
                    cipher.doFinal(getPaddedPayload(nextHeader, payload, blockSize));

            // Build ciphertext
            final ByteBuffer cipherText =
                    ByteBuffer.allocate(mIv.length + encryptedTextAndIcv.length);
            cipherText.put(mIv);
            cipherText.put(encryptedTextAndIcv);

            return getByteArrayFromBuffer(cipherText);
        }
    }

    public static class EspAuth {
        public final String algoName;
        public final byte[] key;
        public final int icvLen;

        private static final Set<String> JCE_SUPPORTED_MACS = new ArraySet<>();

        static {
            JCE_SUPPORTED_MACS.add(HMAC_MD5);
            JCE_SUPPORTED_MACS.add(HMAC_SHA1);
            JCE_SUPPORTED_MACS.add(HMAC_SHA_256);
            JCE_SUPPORTED_MACS.add(HMAC_SHA_384);
            JCE_SUPPORTED_MACS.add(HMAC_SHA_512);
            JCE_SUPPORTED_MACS.add(AES_CMAC);
        }

        public EspAuth(String algoName, byte[] key, int icvLen) {
            this.algoName = algoName;
            this.key = key;
            this.icvLen = icvLen;
        }

        public byte[] getIcv(byte[] authenticatedSection) throws GeneralSecurityException {
            if (AES_XCBC.equals(algoName)) {
                final Cipher aesCipher = Cipher.getInstance(AES_CBC);
                return new AesXCbcImpl().mac(key, authenticatedSection, true /* needTruncation */);
            } else if (JCE_SUPPORTED_MACS.contains(algoName)) {
                final Mac mac = Mac.getInstance(algoName);
                final SecretKeySpec authKey = new SecretKeySpec(key, algoName);
                mac.init(authKey);

                final ByteBuffer buffer = ByteBuffer.wrap(mac.doFinal(authenticatedSection));
                final byte[] icv = new byte[icvLen];
                buffer.get(icv);
                return icv;
            } else {
                throw new IllegalArgumentException("Invalid algorithm: " + algoName);
            }
        }
    }

    public static final class EspAuthNull extends EspAuth {
        private static final String AUTH_NULL = "AUTH_NULL";
        private static final int ICV_LEN_UNUSED = 0;
        private static final byte[] KEY_UNUSED = new byte[0];
        private static final byte[] ICV_EMPTY = new byte[0];

        private static final EspAuthNull sInstance = new EspAuthNull();

        private EspAuthNull() {
            super(AUTH_NULL, KEY_UNUSED, ICV_LEN_UNUSED);
        }

        public static EspAuthNull getInstance() {
            return sInstance;
        }

        @Override
        public byte[] getIcv(byte[] authenticatedSection) throws GeneralSecurityException {
            return ICV_EMPTY;
        }
    }

    /*
     * Debug printing
     */
    private static final char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(hexArray[b >>> 4]);
            sb.append(hexArray[b & 0x0F]);
            sb.append(' ');
        }
        return sb.toString();
    }
}
