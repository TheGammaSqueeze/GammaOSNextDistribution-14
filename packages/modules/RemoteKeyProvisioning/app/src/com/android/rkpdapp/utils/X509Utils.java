/**
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

package com.android.rkpdapp.utils;

import android.util.Base64;
import android.util.Log;

import com.android.rkpdapp.RkpdException;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;

/**
 * Provides convenience methods for parsing certificates and extracting information.
 */
public class X509Utils {

    private static final String TAG = "RkpdX509Utils";

    /**
     * Takes a byte array composed of DER encoded certificates and returns the X.509 certificates
     * contained within as an X509Certificate array.
     */
    public static X509Certificate[] formatX509Certs(byte[] certStream) throws RkpdException {
        try {
            CertificateFactory fact = CertificateFactory.getInstance("X.509");
            ByteArrayInputStream in = new ByteArrayInputStream(certStream);
            ArrayList<Certificate> certs = new ArrayList<>(fact.generateCertificates(in));
            X509Certificate[] certChain = certs.toArray(new X509Certificate[0]);
            if (isCertChainValid(certChain)) {
                return certChain;
            } else {
                throw new RkpdException(RkpdException.ErrorCode.INTERNAL_ERROR,
                        "Could not validate certificate chain.");
            }
        } catch (CertificateException | NoSuchAlgorithmException | NoSuchProviderException
                 | InvalidAlgorithmParameterException e) {
            Log.e(TAG, "Unable to parse certificate chain."
                    + Base64.encodeToString(certStream, Base64.DEFAULT), e);
            throw new RkpdException(RkpdException.ErrorCode.INTERNAL_ERROR,
                    "Failed to interpret DER encoded certificate chain", e);
        }
    }

    /**
     * Extracts an ECDSA-P256 key from a certificate and formats it so that it can be used to match
     * the certificate chain to the proper key when passed into the keystore database.
     */
    public static byte[] getAndFormatRawPublicKey(X509Certificate cert) {
        PublicKey pubKey = cert.getPublicKey();
        if (!(pubKey instanceof ECPublicKey)) {
            Log.e(TAG, "Certificate public key is not an instance of ECPublicKey");
            return null;
        }
        ECPublicKey key = (ECPublicKey) cert.getPublicKey();
        // Remote key provisioning internally supports the default, uncompressed public key
        // format for ECDSA. This defines the format as (s | x | y), where s is the byte
        // indicating if the key is compressed or not, and x and y make up the EC point.
        // However, the key as stored in a COSE_Key object is just the two points. As such,
        // the raw public key is stored in the database as (x | y), so the compression byte
        // should be dropped here. Leading 0's must be preserved.
        //
        // s: 1 byte, x: 32 bytes, y: 32 bytes
        BigInteger xCoord = key.getW().getAffineX();
        BigInteger yCoord = key.getW().getAffineY();
        byte[] formattedKey = new byte[64];
        byte[] xBytes = xCoord.toByteArray();
        // BigInteger returns the value as two's complement big endian byte encoding. This means
        // that a positive, 32-byte value with a leading 1 bit will be converted to a byte array of
        // length 33 in order to include a leading 0 bit.
        if (xBytes.length == 33) {
            System.arraycopy(xBytes, 1 /* offset */, formattedKey, 0 /* offset */, 32);
        } else {
            System.arraycopy(xBytes, 0 /* offset */,
                             formattedKey, 32 - xBytes.length, xBytes.length);
        }
        byte[] yBytes = yCoord.toByteArray();
        if (yBytes.length == 33) {
            System.arraycopy(yBytes, 1 /* offset */, formattedKey, 32 /* offset */, 32);
        } else {
            System.arraycopy(yBytes, 0 /* offset */,
                             formattedKey, 64 - yBytes.length, yBytes.length);
        }
        return formattedKey;
    }

    /**
     * Validates the X509 certificate chain and returns appropriate boolean result.
     */
    public static boolean isCertChainValid(X509Certificate[] certChain)
            throws NoSuchAlgorithmException, NoSuchProviderException,
            InvalidAlgorithmParameterException {
        X509Certificate rootCert = certChain[certChain.length - 1];
        return isSelfSignedCertificate(rootCert) && verifyCertChain(rootCert, certChain);
    }

    private static boolean verifyCertChain(X509Certificate rootCert, X509Certificate[] certChain)
            throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        try {
            // Only add the self-signed root certificate as trust anchor.
            // All the other certificates in the chain should be signed by the previous cert's key.
            Set<TrustAnchor> trustedAnchors = Set.of(new TrustAnchor(rootCert, null));

            CertificateFactory fact = CertificateFactory.getInstance("X.509");
            CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            PKIXParameters parameters = new PKIXParameters(trustedAnchors);
            parameters.setRevocationEnabled(false);
            validator.validate(fact.generateCertPath(Arrays.asList(certChain)), parameters);
            return true;
        } catch (CertificateException | CertPathValidatorException e) {
            Log.e(TAG, "certificate chain validation failed.", e);
            return false;
        }
    }

    /**
     * Verifies whether an X509Certificate is a self-signed certificate.
     */
    public static boolean isSelfSignedCertificate(X509Certificate certificate)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        try {
            certificate.verify(certificate.getPublicKey());
            return true;
        } catch (SignatureException | InvalidKeyException | CertificateException e) {
            Log.e(TAG, "Error verifying self signed certificate", e);
            return false;
        }
    }

    /**
     * Gets the date when the entire cert chain expires. This can be calculated by
     * checking the individual certificate times and returning the minimum of all.
     * @return Expiration time for the certificate chain
     */
    public static Date getExpirationTimeForCertificateChain(X509Certificate[] certChain) {
        return Arrays.stream(certChain)
                .map(X509Certificate::getNotAfter)
                .min(Date::compareTo)
                .orElse(null);
    }
}
