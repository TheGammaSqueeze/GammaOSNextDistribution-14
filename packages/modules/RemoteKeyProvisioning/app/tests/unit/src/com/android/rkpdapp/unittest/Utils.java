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


import static com.google.crypto.tink.subtle.EllipticCurves.EcdsaEncoding.IEEE_P1363;
import static com.google.crypto.tink.subtle.Enums.HashType.SHA256;

import com.google.crypto.tink.subtle.EcdsaSignJce;
import com.google.crypto.tink.subtle.Ed25519Sign;
import com.google.crypto.tink.subtle.EllipticCurves;

import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.x509.X509V3CertificateGenerator;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import javax.security.auth.x500.X500Principal;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;

/**
 * Utility class for unit testing.
 */
public class Utils {
    private static final int KEY_TYPE = 1;
    private static final int KEY_TYPE_OKP = 1;
    private static final int KEY_TYPE_EC2 = 2;
    private static final int KID = 2;
    private static final int ALGORITHM = 3;
    private static final int ALGORITHM_EDDSA = -8;
    private static final int ALGORITHM_ES256 = -7;
    private static final int ALGORITHM_ECDH_ES_HKDF_256 = -25;
    private static final int CURVE = -1;
    public  static final int CURVE_X25519 = 4;
    public static final int CURVE_ED25519 = 6;
    public static final int CURVE_P256 = 1;
    private static final int X_COORDINATE = -2;
    private static final int Y_COORDINATE = -3;

    public static PublicKey getP256PubKeyFromBytes(byte[] xPub, byte[] yPub) throws Exception {
        BigInteger x = new BigInteger(1, xPub);
        BigInteger y = new BigInteger(1, yPub);
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec ecParameters = parameters.getParameterSpec(ECParameterSpec.class);
        ECPoint point = new ECPoint(x, y);
        ECPublicKeySpec keySpec = new ECPublicKeySpec(point, ecParameters);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        return keyFactory.generatePublic(keySpec);
    }

    public static byte[] getBytesFromP256PrivateKey(ECPrivateKey privateKey) throws Exception {
        int keySizeBytes = (privateKey.getParams().getOrder().bitLength() + Byte.SIZE - 1)
                / Byte.SIZE;
        final byte[] rawPublicKey = new byte[keySizeBytes];

        final byte[] priv = privateKey.getS().toByteArray();
        if (priv.length <= keySizeBytes) {
            System.arraycopy(priv, 0, rawPublicKey,  keySizeBytes
                    - priv.length, priv.length);
        } else if (priv.length == keySizeBytes + 1 && priv[0] == 0) {
            System.arraycopy(priv, 1, rawPublicKey, 0, keySizeBytes);
        } else {
            throw new IllegalStateException("private value is too large");
        }
        return rawPublicKey;
    }

    public static byte[] getBytesFromP256PublicKey(ECPublicKey publicKey) throws Exception {
        int keySizeBytes =
                (publicKey.getParams().getOrder().bitLength() + Byte.SIZE - 1) / Byte.SIZE;

        final byte[] rawPublicKey = new byte[2 * keySizeBytes];
        int offset = 0;

        final byte[] x = publicKey.getW().getAffineX().toByteArray();
        if (x.length <= keySizeBytes) {
            System.arraycopy(x, 0, rawPublicKey, offset + keySizeBytes
                    - x.length, x.length);
        } else if (x.length == keySizeBytes + 1 && x[0] == 0) {
            System.arraycopy(x, 1, rawPublicKey, offset, keySizeBytes);
        } else {
            throw new IllegalStateException("x value is too large");
        }
        offset += keySizeBytes;

        final byte[] y = publicKey.getW().getAffineY().toByteArray();
        if (y.length <= keySizeBytes) {
            System.arraycopy(y, 0, rawPublicKey, offset + keySizeBytes
                    - y.length, y.length);
        } else if (y.length == keySizeBytes + 1 && y[0] == 0) {
            System.arraycopy(y, 1, rawPublicKey, offset, keySizeBytes);
        } else {
            throw new IllegalStateException("y value is too large");
        }
        return rawPublicKey;
    }

    public static KeyPair generateEcdsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        ECGenParameterSpec params = new ECGenParameterSpec("secp256r1");
        generator.initialize(params);
        return generator.generateKeyPair();
    }

    public static X509Certificate signPublicKey(KeyPair issuerKeyPair, PublicKey publicKeyToSign)
            throws Exception {
        return signPublicKey(issuerKeyPair, publicKeyToSign,
                Instant.now().plus(Duration.ofDays(1)));
    }

    public static X509Certificate signPublicKey(KeyPair issuerKeyPair, PublicKey publicKeyToSign,
            Instant expirationInstant) throws Exception {
        X500Principal issuer = new X500Principal("CN=TEE");
        BigInteger serial = BigInteger.ONE;
        X500Principal subject = new X500Principal("CN=TEE");

        Instant now = Instant.now();
        X509V3CertificateGenerator certificateBuilder = new X509V3CertificateGenerator();
        certificateBuilder.setIssuerDN(issuer);
        certificateBuilder.setSerialNumber(serial);
        certificateBuilder.setNotBefore(Date.from(now));
        certificateBuilder.setNotAfter(Date.from(expirationInstant));
        certificateBuilder.setSignatureAlgorithm("SHA256WITHECDSA");
        certificateBuilder.setSubjectDN(subject);
        certificateBuilder.setPublicKey(publicKeyToSign);
        certificateBuilder.addExtension(
                Extension.basicConstraints, /*isCritical=*/ true, new BasicConstraints(true));
        certificateBuilder.addExtension(
                Extension.keyUsage, /*isCritical=*/ true, new KeyUsage(KeyUsage.keyCertSign));
        return certificateBuilder.generate(issuerKeyPair.getPrivate());
    }

    public static Array encodeAndSignSign1Ed25519(byte[] encodedPublicKey, byte[] privateKey)
            throws Exception {
        byte[] encodedProtectedHeaders = encodeSimpleMap(1, -8);
        return (Array) (new CborBuilder()
            .addArray()
                .add(encodedProtectedHeaders)      // Protected headers
                .addMap()                          // Empty unprotected Headers
                    .end()
                .add(encodedPublicKey)
                .add(encodeAndSignSigStructure(
                        encodedProtectedHeaders, encodedPublicKey, privateKey, CURVE_ED25519))
            .end()
            .build().get(0));
    }

    public static Array encodeAndSignSign1Ecdsa256(byte[] encodedPublicKey, byte[] privateKey)
            throws Exception {
        byte[] encodedProtectedHeaders = encodeSimpleMap(1, -7);
        return (Array) (new CborBuilder()
            .addArray()
                .add(encodedProtectedHeaders)      // Protected headers
                .addMap()                          // Empty unprotected Headers
                    .end()
                .add(encodedPublicKey)
                .add(encodeAndSignSigStructure(
                        encodedProtectedHeaders, encodedPublicKey, privateKey, CURVE_P256))
            .end()
            .build().get(0));
    }

    private static byte[] encodeAndSignSigStructure(
            byte[] protectedHeaders, byte[] payload, byte[] privateKey,
            int curve) throws Exception {
        return encodeAndSignSigStructure(protectedHeaders, null, payload,
                privateKey, curve);
    }

    private static byte[] encodeAndSignSigStructure(byte[] protectedHeaders, byte[] externalAad,
            byte[] payload, byte[] privateKey, int curve)
            throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new CborBuilder()
                .addArray()
                .add("Signature1")                                      // context string
                .add(protectedHeaders)                                  // protected headers
                .add(null == externalAad ? new byte[0] : externalAad)   // external aad
                .add(payload)                                           // payload
                .end()
                .build());
        if (curve == CURVE_ED25519) {
            Ed25519Sign signer = new Ed25519Sign(privateKey);
            return signer.sign(baos.toByteArray());
        } else {
            ECPrivateKey privKey = EllipticCurves.getEcPrivateKey(
                    EllipticCurves.CurveType.NIST_P256, privateKey);
            EcdsaSignJce ecdsaSigner = new EcdsaSignJce(privKey, SHA256, IEEE_P1363);
            return ecdsaSigner.sign(baos.toByteArray());
        }
    }

    public static byte[] encodeEd25519PubKey(byte[] publicKey) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new CborBuilder()
                .addMap()
                    .put(KEY_TYPE, KEY_TYPE_OKP)
                    .put(ALGORITHM, ALGORITHM_EDDSA)
                    .put(CURVE, CURVE_ED25519)
                    .put(X_COORDINATE, publicKey)
                    .end()
                .build());
        return baos.toByteArray();
    }

    public static byte[] encodeP256PubKey(byte[] pubX, byte[] pubY, boolean isEek)
            throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MapBuilder<CborBuilder> cborBuilder = new CborBuilder()
                .addMap()
                    .put(KEY_TYPE, KEY_TYPE_EC2)
                    .put(ALGORITHM, isEek ? ALGORITHM_ECDH_ES_HKDF_256 : ALGORITHM_ES256)
                    .put(CURVE, CURVE_P256)
                    .put(X_COORDINATE, pubX)
                    .put(Y_COORDINATE, pubY);
        List<DataItem> coseKey;
        if (isEek) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(pubX);
            byte[] kid = digest.digest(pubY);
            coseKey = cborBuilder.put(KID, kid).end().build();
        } else {
            coseKey = cborBuilder.end().build();
        }
        new CborEncoder(baos).encode(coseKey);
        return baos.toByteArray();
    }


    public static byte[] encodeX25519PubKey(byte[] publicKey) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] kid = digest.digest(publicKey);
        new CborEncoder(baos).encode(new CborBuilder()
                .addMap()
                    .put(KEY_TYPE, KEY_TYPE_OKP)
                    .put(KID, kid)
                    .put(ALGORITHM, ALGORITHM_ECDH_ES_HKDF_256)
                    .put(CURVE, CURVE_X25519)
                    .put(X_COORDINATE, publicKey)
                    .end()
                .build());
        return baos.toByteArray();
    }

    private static byte[] encodeSimpleMap(int key, int value) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new CborBuilder()
                .addMap()
                    .put(key, value)
                    .end()
                .build());
        return baos.toByteArray();
    }
}
