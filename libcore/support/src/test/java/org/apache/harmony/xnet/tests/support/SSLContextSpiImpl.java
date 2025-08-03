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

import java.security.KeyManagementException;
import java.security.SecureRandom;

import javax.net.ssl.KeyManager;
//import javax.net.ssl.SSLContextSpi;
import javax.net.ssl.SSLEngine;
/*import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;*/
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

public class SSLContextSpiImpl extends MySSLContextSpi {

    private boolean init = false;

    public void engineInit(KeyManager[] km, TrustManager[] tm,
            SecureRandom sr) throws KeyManagementException {
        if (sr == null) {
            throw new KeyManagementException(
                    "secureRandom is null");
        }
        init = true;
    }

    public SSLSocketFactory engineGetSocketFactory() {
        if (!init) {
            throw new RuntimeException("Not initialiazed");
        }
        return null;
    }

    public SSLServerSocketFactory engineGetServerSocketFactory() {
        if (!init) {
            throw new RuntimeException("Not initialiazed");
        }
        return null;
    }

    public SSLSessionContext engineGetServerSessionContext() {
        if (!init) {
            throw new RuntimeException("Not initialiazed");
        }
        return null;
    }

    public SSLSessionContext engineGetClientSessionContext() {
        if (!init) {
            throw new RuntimeException("Not initialiazed");
        }
        return null;
    }

    /*
     * FIXME: add these methods
     */
    public SSLEngine engineCreateSSLEngine(String host, int port) {
        int max = 65535;

        if (port < 0 || port > max) {
            throw new IllegalArgumentException("Illegal port");
        }

        if (!init) {
            throw new RuntimeException("Not initialiazed");
        }
        return new tmpSSLEngine(host, port);
    }

    public SSLEngine engineCreateSSLEngine() {
        if (!init) {
            throw new RuntimeException("Not initialiazed");
        }
        return new tmpSSLEngine();
    }
}

