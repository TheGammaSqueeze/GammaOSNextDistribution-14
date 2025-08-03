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

package android.security.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;

import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionBindingEvent;
import javax.net.ssl.SSLSessionBindingListener;
import javax.net.ssl.SSLSessionContext;
import javax.security.cert.CertificateException;

// Taken reference from
// libcore/support/src/test/java/org/apache/harmony/xnet/tests/support/mySSLSession.java

class CVE_2021_0341_SSLSession implements SSLSession {

    private byte[] idData;
    private String nameHost = null;
    private int namePort = -1;
    private Hashtable table;
    private boolean invalidateDone = false;
    private Certificate[] certs = null;
    private javax.security.cert.X509Certificate[] xCerts = null;

    public CVE_2021_0341_SSLSession(Certificate[] xc)
            throws CertificateEncodingException, CertificateException {
        certs = xc;
        xCerts = new javax.security.cert.X509Certificate[xc.length];
        int i = 0;
        for (Certificate cert : xc) {
            xCerts[i++] = javax.security.cert.X509Certificate.getInstance(cert.getEncoded());
        }
    }

    public int getApplicationBufferSize() {
        return 1234567;
    }

    public String getCipherSuite() {
        return "SuiteName";
    }

    public long getCreationTime() {
        return 1000L;
    }

    public byte[] getId() {
        return idData;
    }

    public long getLastAccessedTime() {
        return 2000L;
    }

    public Certificate[] getLocalCertificates() {
        return null;
    }

    public Principal getLocalPrincipal() {
        return null;
    }

    public int getPacketBufferSize() {
        return 12345;
    }

    public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
        assumeFalse("peer not authenticated", (certs == null));
        return certs;
    }

    public javax.security.cert.X509Certificate[] getPeerCertificateChain()
            throws SSLPeerUnverifiedException {
        assumeFalse("peer not authenticated", (xCerts == null));
        return xCerts;
    }

    public String getPeerHost() {
        return nameHost;
    }

    public int getPeerPort() {
        return namePort;
    }

    public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
        return null;
    }

    public String getProtocol() {
        return "ProtocolName";
    }

    public SSLSessionContext getSessionContext() {
        return null;
    }

    public void putValue(String s, Object obj) {
        assumeFalse("arguments can not be null", (s == null || obj == null));
        Object obj1 = table.put(s, obj);
        if (obj1 instanceof SSLSessionBindingListener) {
            SSLSessionBindingEvent sslsessionbindingevent = new SSLSessionBindingEvent(this, s);
            ((SSLSessionBindingListener) obj1).valueUnbound(sslsessionbindingevent);
        }
        if (obj instanceof SSLSessionBindingListener) {
            SSLSessionBindingEvent sslsessionbindingevent1 = new SSLSessionBindingEvent(this, s);
            ((SSLSessionBindingListener) obj).valueBound(sslsessionbindingevent1);
        }
    }

    public void removeValue(String s) {
        assumeFalse("argument can not be null", (s == null));
        Object obj = table.remove(s);
        if (obj instanceof SSLSessionBindingListener) {
            SSLSessionBindingEvent sslsessionbindingevent = new SSLSessionBindingEvent(this, s);
            ((SSLSessionBindingListener) obj).valueUnbound(sslsessionbindingevent);
        }
    }

    public Object getValue(String s) {
        assumeFalse("argument can not be null", (s == null));
        return table.get(s);
    }

    public String[] getValueNames() {
        Vector vector = new Vector();
        Enumeration enumeration = table.keys();
        while (enumeration.hasMoreElements()) {
            vector.addElement(enumeration.nextElement());
        }
        String as[] = new String[vector.size()];
        vector.copyInto(as);
        return as;
    }

    public void invalidate() {
        invalidateDone = true;
    }

    public boolean isValid() {
        return invalidateDone;
    }
}


@RunWith(AndroidJUnit4.class)
public class CVE_2021_0341 extends StsExtraBusinessLogicTestCase  {

    public final static byte[] X509_TEST_CERTIFICATE = ("-----BEGIN CERTIFICATE-----\n"
            + "MIIC3DCCAcSgAwIBAgIURJspNgSx6GVbOLijqravWoGlm+0wDQYJKoZIhvcNAQEL\n"
            + "BQAwETEPMA0GA1UECgwGZ29vZ2xlMB4XDTIyMDIxNzExNTE1NFoXDTMxMTExNzEx\n"
            + "NTE1NFowETEPMA0GA1UECgwGZ29vZ2xlMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A\n"
            + "MIIBCgKCAQEA2PxVfeoY/uA66aVRXpuZXodTBFBGowTt/lAJxR8fVjDwRTOrRTrr\n"
            + "2qdLPPK40lFQOSfHw/g6+9WjNjjSDBP+U2Agrvo8cU5R1DwJWyK2wcHOtBcL2bsj\n"
            + "kRx18CZtZUu51a8KEhMCaIoHgGzwGMZkJnfmfO9ABbMfFsyn6KxFf0MXG3bRcQU7\n"
            + "LyCXyQbo2Lal68QiTMXZs9rXN/a8ex+RmP9PKaXIEsIOeDrtLhzcWyNjrtTuDRoR\n"
            + "K49xHOpz4EmqHLDzIKuhqyyo9tLR+okK0BRJoNxmfvRTbxNbjzpTTFgyB4KrKBCO\n"
            + "VQXJROlBf7594xlCMn0QSwElVT4bMaMw/QIDAQABoywwKjAoBgNVHREEITAfggkq\n"
            + "LmJhci5jb22CEiou44Kw44O844Kw44OrLmNvbTANBgkqhkiG9w0BAQsFAAOCAQEA\n"
            + "piIwY84InjX4BUmAmM+D9CHD/9euucGxgdXqL6kKG1HRL6lHfwZAIxhlbn3jWFEx\n"
            + "k5DTkaL039FGLvYzMI0McwTIuHY/7JwCbZUJ3pVl0waW4sab+2LScnpe9c422Tqb\n"
            + "hECEhc71E/kRlG9FjQN3wjEj3RcnWZAWCqAnJN/dcd/1tBD88tzHVckDC9mSvxzP\n"
            + "hkmIRRifIDxcrmx7PkpJ6dAfiw9e1Pl5THdsPTDtiGJ4hjlsAi8ury3rrx31lsyo\n"
            + "kAwQy23Q7Rcbr2z8bijDuSWWWc9RRsz+O/ePy35NJci/RUwVFTpvOFtahC30Jdv3\n"
            + "vpmqxLqEF7Z9I1yb3Q6YUg==\n" + "-----END CERTIFICATE-----\n").getBytes();

    /**
     * b/171980069
     */
    @AsbSecurityTest(cveBugId = 171980069)
    @Test
    public void testPocCVE_2021_0341() throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        assumeNotNull(cf);
        HostnameVerifier verifier = HttpsURLConnection.getDefaultHostnameVerifier();
        assumeNotNull(verifier);
        InputStream in = new ByteArrayInputStream(X509_TEST_CERTIFICATE);
        java.security.cert.X509Certificate x509 =
                (java.security.cert.X509Certificate) cf.generateCertificate(in);
        assumeNotNull(x509);
        CVE_2021_0341_SSLSession session =
                new CVE_2021_0341_SSLSession(new java.security.cert.X509Certificate[] {x509});
        assertFalse(verifier.verify("\u82b1\u5b50.bar.com", session));
    }
}
