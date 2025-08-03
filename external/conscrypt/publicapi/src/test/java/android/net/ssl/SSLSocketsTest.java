/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.net.ssl;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.org.conscrypt.javax.net.ssl.TestSSLSocketPair;
import com.android.org.conscrypt.tlswire.TlsTester;
import com.android.org.conscrypt.tlswire.handshake.ClientHello;
import com.android.org.conscrypt.tlswire.handshake.HelloExtension;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import tests.net.DelegatingSSLSocketFactory;

@RunWith(JUnit4.class)
public class SSLSocketsTest {

    private static class BrokenSSLSocket extends SSLSocket {
        @Override public String[] getSupportedCipherSuites() { throw new AssertionError(); }
        @Override public String[] getEnabledCipherSuites() { throw new AssertionError(); }
        @Override public void setEnabledCipherSuites(String[] strings) { throw new AssertionError(); }
        @Override public String[] getSupportedProtocols() { throw new AssertionError(); }
        @Override public String[] getEnabledProtocols() { throw new AssertionError(); }
        @Override public void setEnabledProtocols(String[] strings) { throw new AssertionError(); }
        @Override public SSLSession getSession() { throw new AssertionError(); }
        @Override public void addHandshakeCompletedListener(
                HandshakeCompletedListener handshakeCompletedListener) { throw new AssertionError(); }
        @Override public void removeHandshakeCompletedListener(
                HandshakeCompletedListener handshakeCompletedListener) { throw new AssertionError(); }
        @Override public void startHandshake() { throw new AssertionError(); }
        @Override public void setUseClientMode(boolean b) { throw new AssertionError(); }
        @Override public boolean getUseClientMode() { throw new AssertionError(); }
        @Override public void setNeedClientAuth(boolean b) { throw new AssertionError(); }
        @Override public boolean getNeedClientAuth() { throw new AssertionError(); }
        @Override public void setWantClientAuth(boolean b) { throw new AssertionError(); }
        @Override public boolean getWantClientAuth() { throw new AssertionError(); }
        @Override public void setEnableSessionCreation(boolean b) { throw new AssertionError(); }
        @Override public boolean getEnableSessionCreation() { throw new AssertionError(); }
    }

    private ExecutorService executor;

    @Before
    public void setUp() {
        executor = Executors.newCachedThreadPool();
    }

    @After
    public void tearDown() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    public void testIsSupported() throws Exception {
        SSLSocket s = (SSLSocket) SSLSocketFactory.getDefault().createSocket();
        assertTrue(SSLSockets.isSupportedSocket(s));

        s = new BrokenSSLSocket();
        assertFalse(SSLSockets.isSupportedSocket(s));
    }

    @Test(expected = IllegalArgumentException.class)
    public void setUseSessionTickets_InvalidSocket() {
        SSLSockets.setUseSessionTickets(new BrokenSSLSocket(), true);
    }

    @Test
    public void setUseSessionTickets_ValidSocket() throws Exception {
        SSLSocket s = (SSLSocket) SSLSocketFactory.getDefault().createSocket();
        SSLSockets.setUseSessionTickets(s, true);

        ClientHello hello = TlsTester.captureTlsHandshakeClientHello(executor,
                new DelegatingSSLSocketFactory((SSLSocketFactory) SSLSocketFactory.getDefault()) {
                    @Override public SSLSocket configureSocket(SSLSocket socket) {
                        SSLSockets.setUseSessionTickets(socket, true);
                        return socket;
                    }
                });
        assertNotNull(hello.findExtensionByType(HelloExtension.TYPE_SESSION_TICKET));

        hello = TlsTester.captureTlsHandshakeClientHello(executor,
                new DelegatingSSLSocketFactory((SSLSocketFactory) SSLSocketFactory.getDefault()) {
                    @Override public SSLSocket configureSocket(SSLSocket socket) {
                        SSLSockets.setUseSessionTickets(socket, false);
                        return socket;
                    }
                });
        assertNull(hello.findExtensionByType(HelloExtension.TYPE_SESSION_TICKET));
    }

    @Test(expected = IllegalArgumentException.class)
    public void exportKeyingMaterial_InvalidSocket() throws Exception {
        SSLSockets.exportKeyingMaterial(new BrokenSSLSocket(), "label", null, 20);
    }

    @Test
    public void exportKeyingMaterial_ValidSocket() throws Exception {
        TestSSLSocketPair pair = TestSSLSocketPair.create();
        String label = "Some label";
        int keyLength = 32;

        pair.connect();

        byte[] clientEkm = SSLSockets.exportKeyingMaterial(pair.client, label, null, keyLength);
        byte[] serverEkm = SSLSockets.exportKeyingMaterial(pair.server, label, null, keyLength);
        assertNotNull(clientEkm);
        assertNotNull(serverEkm);
        assertEquals(keyLength, clientEkm.length);
        assertEquals(keyLength, serverEkm.length);
        assertArrayEquals(clientEkm, serverEkm);
    }
}
