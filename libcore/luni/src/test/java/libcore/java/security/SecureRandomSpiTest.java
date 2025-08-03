/*
 * Copyright (C) 2024 The Android Open Source Project
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

package libcore.java.security;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.SecureRandomParameters;
import java.security.SecureRandomSpi;
import java.security.Security;

@RunWith(JUnit4.class)
public class SecureRandomSpiTest {

    public static class MySecureRandomSpi extends SecureRandomSpi {

        public final SecureRandomParameters myParams;

        public MySecureRandomSpi() {
            myParams = null;
        }

        public MySecureRandomSpi(SecureRandomParameters params) {
            super(params);
            myParams = params;
        }

        @Override
        protected void engineSetSeed(byte[] seed) {}

        @Override
        protected void engineNextBytes(byte[] bytes) {}

        @Override
        protected byte[] engineGenerateSeed(int numBytes) {
            return new byte[0];
        }

        public void engineReseed(SecureRandomParameters params) {
            super.engineReseed(params);
        }
        public SecureRandomParameters engineGetParameters() {
            return super.engineGetParameters();
        }

        @Override
        public String toString() {
            return myParams == null ? "null!" : myParams.toString();
        }
    }


    public static class MySecureRandomProvider extends Provider {

        public static final String NAME = "SecureRandomSpiTest.MySecureRandomProvider";

        public static final String ALGORITHM = "MySecureRandomSpi";
        MySecureRandomProvider() {
            super(NAME, 1.0, "Provider for SecureRandomSpi testing");
            putService(new Provider.Service(this, "SecureRandom", ALGORITHM,
                    MySecureRandomSpi.class.getName(), null, null));
        }
    }

    private static final SecureRandomParameters MY_PARAMS = new SecureRandomParameters() {
        @Override
        public String toString() {
            return "SecureRandomSpiTest.testGetInstance";
        }
    };

    private final MySecureRandomSpi mSpi = new MySecureRandomSpi();

    @Before
    public void setUp() {
        Security.addProvider(new MySecureRandomProvider());
    }

    @After
    public void teatDown() {
        Security.removeProvider(MySecureRandomProvider.NAME);
    }

    @Test
    public void testEngineReseed() {
        Assert.assertThrows(UnsupportedOperationException.class, () -> mSpi.engineReseed(null));
    }

    @Test
    public void testEngineGetParameters() {
        Assert.assertNull(mSpi.engineGetParameters());
    }

    @Test
    public void testGetParameters() throws NoSuchAlgorithmException {
        SecureRandom secureRandom = SecureRandom.getInstance(MySecureRandomProvider.ALGORITHM,
                MY_PARAMS);
        // It's testing the default implementation of engineGetParameters()
        // in java.security.SecureRandom, and MY_PARAMS is not used.
        Assert.assertNull(secureRandom.getParameters());
    }

    @Test
    public void testConstructor() {
        SecureRandomParameters myParams = new SecureRandomParameters() {};
        MySecureRandomSpi mySpi = new MySecureRandomSpi(myParams);
        Assert.assertNull(mySpi.engineGetParameters());
    }

    @Test
    public void testGetInstanceWithProviderAndSecureRandomParameters()
            throws NoSuchAlgorithmException {
        Provider provider = Security.getProvider(MySecureRandomProvider.NAME);
        SecureRandom secureRandom = SecureRandom.getInstance(MySecureRandomProvider.ALGORITHM,
                MY_PARAMS, provider);
        // Indirectly
        assertEquals(MY_PARAMS.toString(), secureRandom.toString());
    }

    @Test
    public void testGetInstanceWithSecureRandomParameters() throws NoSuchAlgorithmException {
        SecureRandom secureRandom = SecureRandom.getInstance(MySecureRandomProvider.ALGORITHM,
                MY_PARAMS);
        // Indirectly
        assertEquals(MY_PARAMS.toString(), secureRandom.toString());
    }

    @Test
    public void testGetInstanceWithNameAndSecureRandomParameters()
            throws NoSuchAlgorithmException, NoSuchProviderException {
        SecureRandom secureRandom = SecureRandom.getInstance(MySecureRandomProvider.ALGORITHM,
                MY_PARAMS, MySecureRandomProvider.NAME);
        // Indirectly
        assertEquals(MY_PARAMS.toString(), secureRandom.toString());
    }

    @Test
    public void testSecureRandomGetNextBytesWithSecureRandomParameters()
            throws NoSuchAlgorithmException {
        SecureRandom secureRandom = SecureRandom.getInstance(MySecureRandomProvider.ALGORITHM,
                MY_PARAMS);
        Assert.assertThrows(UnsupportedOperationException.class,
                () -> secureRandom.nextBytes(new byte[0], MY_PARAMS));
    }
}
