/*
 * Copyright (C) 2021 The Android Open Source Project
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

package libcore.java.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.net.Authenticator;
import java.net.InetAddress;
import java.net.PasswordAuthentication;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AuthenticatorTest {

    private class MockAuthenticator extends Authenticator {
        private int requests = 0;
        private String userName;
        private String password;

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            ++requests;
            return new PasswordAuthentication(userName, password.toCharArray());
        }

        public int getRequests() {
            return requests;
        }

        public InetAddress getAddr() {
            return getRequestingSite();
        }

        public int getPort() {
            return getRequestingPort();
        }

        public String getProtocol() {
            return getRequestingProtocol();
        }

        public String getPrompt() {
            return getRequestingPrompt();
        }

        public String getScheme() {
            return getRequestingScheme();
        }

        public String getHost() {
            return getRequestingHost();
        }
    }

    @Test
    public void testRequestPasswordAuthentication() throws Exception {
        final InetAddress addr = InetAddress.getByName("localhost");
        final String host = "www.example.com";
        final int port = 42;
        final String protocol = "HTTP";
        final String prompt = "Please enter your password";
        final String scheme = "scheme";
        final String userName = "007";
        final String password = "super secret";

        MockAuthenticator auth = new MockAuthenticator();
        auth.setUserName(userName);
        auth.setPassword(password);
        Authenticator.setDefault(auth);
        PasswordAuthentication passAuth = Authenticator.requestPasswordAuthentication(
                addr, port, protocol, prompt, scheme);

        assertNotNull(passAuth);
        assertEquals(userName, passAuth.getUserName());
        assertEquals(password, String.valueOf(passAuth.getPassword()));

        assertEquals(1, auth.getRequests());
        assertEquals(addr, auth.getAddr());
        assertEquals(port, auth.getPort());
        assertEquals(protocol, auth.getProtocol());
        assertEquals(prompt, auth.getPrompt());
        assertEquals(scheme, auth.getScheme());

        passAuth = Authenticator.requestPasswordAuthentication(
                host, addr, port, protocol, prompt, scheme);

        assertNotNull(passAuth);
        assertEquals(userName, passAuth.getUserName());
        assertEquals(password, String.valueOf(passAuth.getPassword()));

        assertEquals(host, auth.getHost());
        assertEquals(2, auth.getRequests());
        assertEquals(addr, auth.getAddr());
        assertEquals(port, auth.getPort());
        assertEquals(protocol, auth.getProtocol());
        assertEquals(prompt, auth.getPrompt());
        assertEquals(scheme, auth.getScheme());
    }

    @Test
    public void testRequestPasswordAuthenticationWithNullAuthenticator() throws Exception {
        final String host = "www.example.com";
        final InetAddress addr = InetAddress.getByName("localhost");
        final int port = 42;
        final String protocol = "HTTP";
        final String prompt = "Please enter your password";
        final String scheme = "scheme";

        Authenticator.setDefault(null);
        assertNull(Authenticator.requestPasswordAuthentication(
                addr, port, protocol, prompt, scheme));

        assertNull(Authenticator.requestPasswordAuthentication(
                host, addr, port, protocol, prompt, scheme));
    }
}
