/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8044445 8194307 8207318
 * @summary test new methods from JEP-229: Create PKCS12 Keystores by Default
 */
package test.java.security.KeyStore;

import java.io.*;
import java.security.*;
import java.security.KeyStore.*;
import java.security.cert.*;
import javax.crypto.*;
import javax.security.auth.callback.*;
import org.testng.annotations.Test;
import static org.junit.Assert.fail;

public class ProbeKeystores {
    // BEGIN Android-changed: adjusted tests to account for multiple implementation details.

    private static final char[] PASSWORD = "changeit".toCharArray();
    private static final char[] BAD_PASSWORD = "badpasword".toCharArray();
    private static final LoadStoreParameter LOAD_STORE_PARAM =
            new MyLoadStoreParameter(new PasswordProtection(PASSWORD));

    private static class MyLoadStoreParameter implements LoadStoreParameter {

        private ProtectionParameter protection;

        MyLoadStoreParameter(ProtectionParameter protection) {
            this.protection = protection;
        }

        public ProtectionParameter getProtectionParameter() {
            return protection;
        }
    }

    @Test
    public void testKeystores() throws Exception {
        File bcFile = File.createTempFile("empty", "bc");
        File p12File = File.createTempFile("empty", "p12");

        // Testing empty keystores

        init(bcFile, "BouncyCastle");
        init(p12File, "PKCS12");


        load(bcFile, "BouncyCastle");
        load(p12File, "PKCS12");
        loadNonCompat(bcFile, "PKCS12"); // test compatibility mode
        loadNonCompat(p12File, "BouncyCastle"); // test compatibility mode

        probe(bcFile, "BOUNCYCASTLE");
        probeFails(p12File);

        build(bcFile, "BOUNCYCASTLE", true);
        build(bcFile, "BOUNCYCASTLE", false);
        // engineProbe does not work for PKCS12

        // PKCS12 does not support keys
        File onekeyBcFile = File.createTempFile("onekey", "bc");
        SecretKey key = generateSecretKey("AES", 128);
        init(onekeyBcFile, "BouncyCastle", key);

        load(onekeyBcFile, "BouncyCastle");

        probe(onekeyBcFile, "BOUNCYCASTLE");

        build(onekeyBcFile, "BOUNCYCASTLE", true);
        build(onekeyBcFile, "BOUNCYCASTLE", false);
    }

    // Instantiate an empty keystore using the supplied keystore type
    private static void init(File file, String type) throws Exception {
        KeyStore ks = KeyStore.getInstance(type);
        ks.load(null, null);
        try (OutputStream stream = new FileOutputStream(file)) {
            ks.store(stream, PASSWORD);
        }
    }

    // Instantiate a keystore using the supplied keystore type & create an entry
    private static void init(File file, String type, SecretKey key)
            throws Exception {
        KeyStore ks = KeyStore.getInstance(type);
        ks.load(null, null);
        ks.setEntry("mykey", new KeyStore.SecretKeyEntry(key),
                new PasswordProtection(PASSWORD));
        try (OutputStream stream = new FileOutputStream(file)) {
            ks.store(stream, PASSWORD);
        }
    }

    // Instantiate a keystore by probing the supplied file for the keystore type
    private static void probe(File file, String type) throws Exception {
        KeyStore ks;
        // First try with the correct password
        ks = KeyStore.getInstance(file, PASSWORD);
        if (!type.equalsIgnoreCase(ks.getType())) {
            throw new Exception("ERROR: expected a " + type + " keystore, " +
                    "got a " + ks.getType() + " keystore instead");
        }

        // Next try with an incorrect password
        try {
            ks = KeyStore.getInstance(file, BAD_PASSWORD);
            fail("ERROR: expected an exception but got success");
        } catch (IOException e) {
            // Expected
        }

        // Next try with a password within a LoadStoreParameter (still unsupported)
        try {
            ks = KeyStore.getInstance(file, LOAD_STORE_PARAM);
            fail("ERROR: expected an exception but got success");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }
    // Instantiate a keystore by probing the supplied file for the keystore type
    private static void probeFails(File file) throws Exception {
        KeyStore ks;
        // Next try with an incorrect password
        try {
            ks = KeyStore.getInstance(file, PASSWORD);
            fail("ERROR: expected an exception but got success");
        } catch (KeyStoreException e) {
            // Expected
        }
    }

    // Instantiate a keystore by probing the supplied file for the keystore type
    private static void build(File file, String type, boolean usePassword) throws Exception {
        Builder builder;
        if (usePassword) {
            builder = Builder.newInstance(file,
                    new PasswordProtection(PASSWORD));
        } else {
            builder = Builder.newInstance(file,
                    new CallbackHandlerProtection(new DummyHandler()));
        }
        KeyStore ks = builder.getKeyStore();
        if (!type.equalsIgnoreCase(ks.getType())) {
            throw new Exception("ERROR: expected a " + type + " keystore, " +
                    "got a " + ks.getType() + " keystore instead");
        }
    }

    // Load the keystore entries
    private static void load(File file, String type) throws Exception {
        Security.setProperty("keystore.type.compat", "true");
        KeyStore ks = KeyStore.getInstance(type);
        try (InputStream stream = new FileInputStream(file)) {
            ks.load(stream, PASSWORD);
        }
    }

    // Load the keystore entries (with compatibility mode disabled)
    private static void loadNonCompat(File file, String type)
            throws Exception {
        try {
            load(file, type);
            fail("ERROR: expected load to fail but it didn't");
        } catch (IOException e) {
            // Expected
        }
    }

    // END Android-changed:  adjusted tests to account for multiple implementation details.

    // Generate a secret key using the supplied algorithm name and key size
    private static SecretKey generateSecretKey(String algorithm, int size)
            throws NoSuchAlgorithmException {
        KeyGenerator generator = KeyGenerator.getInstance(algorithm);
        generator.init(size);
        return generator.generateKey();
    }

    private static class DummyHandler implements CallbackHandler {
        public void handle(Callback[] callbacks)
                throws IOException, UnsupportedCallbackException {
            for (int i = 0; i < callbacks.length; i++) {
                Callback cb = callbacks[i];
                if (cb instanceof PasswordCallback) {
                    PasswordCallback pcb = (PasswordCallback)cb;
                    pcb.setPassword(PASSWORD);
                    break;
                }
            }
        }
    }
}