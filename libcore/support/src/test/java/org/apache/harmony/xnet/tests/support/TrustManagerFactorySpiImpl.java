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

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;

public class TrustManagerFactorySpiImpl extends MyTrustManagerFactorySpi {

    private static boolean isengineInitCalled = false;
    private static boolean isEngineGetTrustManagersCalled = false;
    private static KeyStore ks = null;
    private static ManagerFactoryParameters spec = null;

    public void engineInit(KeyStore ks) throws KeyStoreException {
        isengineInitCalled = true;
        this.ks = ks;
    }

    public void engineInit(ManagerFactoryParameters spec) throws InvalidAlgorithmParameterException {
        isengineInitCalled = true;
        this.spec = spec;
    }

    public TrustManager[] engineGetTrustManagers() {
        isEngineGetTrustManagersCalled = true;
        return null;
    }

    public void reset() {
        isengineInitCalled = false;
        isEngineGetTrustManagersCalled = false;
    }

    public boolean isEngineGetTrustManagersCalled() {
        return isEngineGetTrustManagersCalled;
    }

    public boolean isEngineInitCalled() {
        return isengineInitCalled;
    }

    public Object getKs() {
        return ks;
    }

    public Object getSpec() {
        return spec;
    }
}
