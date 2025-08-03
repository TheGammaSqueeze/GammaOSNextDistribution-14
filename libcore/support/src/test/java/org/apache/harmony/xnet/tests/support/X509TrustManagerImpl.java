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

import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateException;

public class X509TrustManagerImpl implements X509TrustManager {

    public void checkClientTrusted(X509Certificate[] ax509certificate, String s)
                                   throws CertificateException {

        if(ax509certificate == null || ax509certificate.length == 0)
            throw new IllegalArgumentException("null or zero-length certificate chain");
        if(s == null || s.length() == 0)
            throw new IllegalArgumentException("null or zero-length authentication type");

        for (int i = 0; i < ax509certificate.length; i++) {
            if (ax509certificate[i].getVersion() != 3) {
                throw new CertificateException();
            }
        }
    }

    public void checkServerTrusted(X509Certificate[] ax509certificate, String s)
                                   throws CertificateException {

        if(ax509certificate == null || ax509certificate.length == 0)
            throw new IllegalArgumentException("null or zero-length certificate chain");
        if(s == null || s.length() == 0)
            throw new IllegalArgumentException("null or zero-length authentication type");

        for (int i = 0; i < ax509certificate.length; i++) {
            if (ax509certificate[i].getVersion() != 3) {
                throw new CertificateException();
            }
        }
    }

    public X509Certificate[] getAcceptedIssuers() {
        X509Certificate[] cert = new X509Certificate[0];
        return cert;
    }
}
