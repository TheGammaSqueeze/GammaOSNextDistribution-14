/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.googlecode.android_scripting.facade;

import android.os.Environment;
import android.util.Log;

import com.android.internal.net.VpnProfile;
import com.android.internal.org.bouncycastle.asn1.ASN1InputStream;
import com.android.internal.org.bouncycastle.asn1.ASN1Sequence;
import com.android.internal.org.bouncycastle.asn1.DEROctetString;
import com.android.internal.org.bouncycastle.asn1.x509.BasicConstraints;

import libcore.io.Streams;

import junit.framework.Assert;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableEntryException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Certificate installer helper to extract information from a provided file
 * and install certificates to keystore.
 */
public class CertInstallerHelper {
    private static final String TAG = "CertInstallerHelper";
    /* Define a password to unlock keystore after it is reset */
    private PrivateKey mUserKey;  // private key
    private X509Certificate mUserCert;  // user certificate
    private List<X509Certificate> mCaCerts = new ArrayList<X509Certificate>();
    private final KeyStore mKeyStore;

    /**
     * Delete all key for caller.
     */
    public CertInstallerHelper() {
        try {
            mKeyStore = KeyStore.getInstance("AndroidKeyStore");
            mKeyStore.load(null);
            final Enumeration<String> aliases = mKeyStore.aliases();
            while (aliases.hasMoreElements()) {
                mKeyStore.deleteEntry(aliases.nextElement());
            }
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException
                | IOException e) {
            Log.e(TAG, "Failed to open and cleanup Keystore.", e);
            throw new RuntimeException("Failed to open and cleanup Keystore.", e);
        }
    }

    private void extractCertificate(String certFile, String password) {
        InputStream in = null;
        final byte[] raw;
        java.security.KeyStore keystore = null;
        try {
            // Read .p12 file from SDCARD and extract with password
            in = new FileInputStream(new File(
                    Environment.getExternalStorageDirectory(), certFile));
            raw = Streams.readFully(in);

            keystore = java.security.KeyStore.getInstance("PKCS12");
            PasswordProtection passwordProtection = new PasswordProtection(password.toCharArray());
            keystore.load(new ByteArrayInputStream(raw), passwordProtection.getPassword());

            // Install certificates and private keys
            Enumeration<String> aliases = keystore.aliases();
            if (!aliases.hasMoreElements()) {
                Assert.fail("key store failed to put in keychain");
            }
            ArrayList<String> aliasesList = Collections.list(aliases);
            // The keystore is initialized for each test case, there will
            // be only one alias in the keystore
            Assert.assertEquals(1, aliasesList.size());
            String alias = aliasesList.get(0);
            java.security.KeyStore.Entry entry = keystore.getEntry(alias, passwordProtection);
            Log.d(TAG, "extracted alias = " + alias + ", entry=" + entry.getClass());

            if (entry instanceof PrivateKeyEntry) {
                Assert.assertTrue(installFrom((PrivateKeyEntry) entry));
            }
        } catch (IOException e) {
            Assert.fail("Failed to read certficate: " + e);
        } catch (KeyStoreException e) {
            Log.e(TAG, "failed to extract certificate" + e);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "failed to extract certificate" + e);
        } catch (CertificateException e) {
            Log.e(TAG, "failed to extract certificate" + e);
        } catch (UnrecoverableEntryException e) {
            Log.e(TAG, "failed to extract certificate" + e);
        }
        finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    Log.e(TAG, "close FileInputStream error: " + e);
                }
            }
        }
    }

    /**
     * Extract private keys, user certificates and ca certificates
     */
    private synchronized boolean installFrom(PrivateKeyEntry entry) {
        mUserKey = entry.getPrivateKey();
        mUserCert = (X509Certificate) entry.getCertificate();

        Certificate[] certs = entry.getCertificateChain();
        Log.d(TAG, "# certs extracted = " + certs.length);
        mCaCerts = new ArrayList<X509Certificate>(certs.length);
        for (Certificate c : certs) {
            X509Certificate cert = (X509Certificate) c;
            if (isCa(cert)) {
                mCaCerts.add(cert);
            }
        }
        Log.d(TAG, "# ca certs extracted = " + mCaCerts.size());
        return true;
    }

    private boolean isCa(X509Certificate cert) {
        try {
            byte[] asn1EncodedBytes = cert.getExtensionValue("2.5.29.19");
            if (asn1EncodedBytes == null) {
                return false;
            }
            DEROctetString derOctetString = (DEROctetString)
                    new ASN1InputStream(asn1EncodedBytes).readObject();
            byte[] octets = derOctetString.getOctets();
            ASN1Sequence sequence = (ASN1Sequence)
                    new ASN1InputStream(octets).readObject();
            return BasicConstraints.getInstance(sequence).isCA();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Extract certificate from the given file, and install it to keystore
     * @param profile VpnProfile
     * @param certFile .p12 file which includes certificates
     * @param password password to extract the .p12 file
     */
    public void installCertificate(VpnProfile profile, String certFile, String password) {
        // extract private keys, certificates from the provided file
        extractCertificate(certFile, password);
        // install certificate to the keystore
        try {
            boolean caInstalledWithUserKey = false;

            if (mUserKey != null) {
                Log.v(TAG, "has private key");
                if (mUserCert == null) {
                    throw new AssertionError("Must have user cert if user key is installed.");
                }
                final List<Certificate> certChain = new ArrayList<Certificate>();
                certChain.add(mUserCert);
                if (profile.ipsecUserCert.equals(profile.ipsecCaCert)) {
                    // If the CA certs should be installed under the same alias they have to be
                    // added to the end of the certificate chain.
                    certChain.addAll(mCaCerts);
                    // Make a note that we have installed the CA cert chain along with the
                    // user key and cert.
                    caInstalledWithUserKey = true;
                }
                mKeyStore.setKeyEntry(profile.ipsecUserCert, mUserKey, null,
                        certChain.toArray(new Certificate[0]));
                Log.v(TAG, "install " + profile.ipsecUserCert + " is successful");
            }

            if (!(mCaCerts.isEmpty() || caInstalledWithUserKey)) {
                if (mCaCerts.size() != 1) {
                    throw new AssertionError("Trusted certificate cannot be a cert chain.");
                }
                mKeyStore.setCertificateEntry(profile.ipsecCaCert, mCaCerts.get(0));
                Log.v(TAG, " install " + profile.ipsecCaCert + " is successful");
            }
        } catch (KeyStoreException e) {
            Log.e(TAG, "Exception trying to import certificates. " + e);
            throw new AssertionError(e);
        }
    }
}
