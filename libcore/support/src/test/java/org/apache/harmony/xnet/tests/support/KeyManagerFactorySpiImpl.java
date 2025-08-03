/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.harmony.xnet.tests.support;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.ManagerFactoryParameters;

public class KeyManagerFactorySpiImpl extends MyKeyManagerFactorySpi {

    private boolean isInitialized = false;

    public void engineInit(KeyStore ks, char[] password)
            throws KeyStoreException, NoSuchAlgorithmException,
            UnrecoverableKeyException {

        if (ks == null && password == null) {
            throw new NoSuchAlgorithmException();
        }

        if (ks == null) {
            throw new KeyStoreException();
        }

        if (password == null) {
            throw new UnrecoverableKeyException();
        }

        isInitialized = true;
    }

    public void engineInit(ManagerFactoryParameters spec)
            throws InvalidAlgorithmParameterException {
        if (spec == null) {
            throw new InvalidAlgorithmParameterException("Incorrect parameter");
        }
        isInitialized = true;
    }

    public KeyManager[] engineGetKeyManagers() {
        if(!isInitialized)
            throw new IllegalStateException("KeyManagerFactoryImpl is not initialized");
        else
            return null;
    }
}
