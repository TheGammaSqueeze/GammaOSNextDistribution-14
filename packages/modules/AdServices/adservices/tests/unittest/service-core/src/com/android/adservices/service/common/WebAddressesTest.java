/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import com.android.adservices.common.WebUtil;

import org.junit.Test;

import java.util.Optional;

public class WebAddressesTest {

    private static final String COM_PUBLIC_SUFFIX = "com";
    private static final String BLOGSPOT_COM_PUBLIC_SUFFIX = "blogspot.com";
    private static final String TOP_PRIVATE_DOMAIN = "private-domain";
    private static final String SUBDOMAIN = "subdomain";
    private static final String HTTPS_SCHEME = "https";
    private static final String HTTP_SCHEME = "http";
    private static final String INVALID_TLD = "invalid_tld";
    private static final String INVALID_URL = "invalid url";
    private static final String PORT = "443";
    private static final String LOCALHOST = "localhost";
    private static final String LOCALHOST_IP = "127.0.0.1";

    private static final Uri HTTPS_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX =
            Uri.parse(
                    String.format(
                            "%s://%s.%s", HTTPS_SCHEME, TOP_PRIVATE_DOMAIN, COM_PUBLIC_SUFFIX));

    private static final Uri HTTPS_PRIVATE_DOMAIN_BLOGSPOT_COM_PUBLIC_SUFFIX =
            Uri.parse(
                    String.format(
                            "%s://%s.%s",
                            HTTPS_SCHEME, TOP_PRIVATE_DOMAIN, BLOGSPOT_COM_PUBLIC_SUFFIX));

    private static final Uri HTTPS_SUBDOMAIN_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX =
            Uri.parse(
                    String.format(
                            "%s://%s.%s.%s",
                            HTTPS_SCHEME, SUBDOMAIN, TOP_PRIVATE_DOMAIN, COM_PUBLIC_SUFFIX));

    private static final Uri HTTPS_SUBDOMAIN_PRIVATE_DOMAIN_BLOGSPOT_COM_PUBLIC_SUFFIX =
            Uri.parse(
                    String.format(
                            "%s://%s.%s.%s",
                            HTTPS_SCHEME,
                            SUBDOMAIN,
                            TOP_PRIVATE_DOMAIN,
                            BLOGSPOT_COM_PUBLIC_SUFFIX));

    private static final Uri HTTPS_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX_PORT =
            Uri.parse(
                    String.format(
                            "%s://%s.%s:%s",
                            HTTPS_SCHEME, TOP_PRIVATE_DOMAIN, COM_PUBLIC_SUFFIX, PORT));

    private static final Uri HTTP_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX =
            Uri.parse(
                    String.format(
                            "%s://%s.%s", HTTP_SCHEME, TOP_PRIVATE_DOMAIN, COM_PUBLIC_SUFFIX));

    private static final Uri HTTPS_LOCALHOST =
            Uri.parse(String.format("%s://%s", HTTPS_SCHEME, LOCALHOST));

    private static final Uri HTTPS_LOCALHOST_IP =
            Uri.parse(String.format("%s://%s", HTTPS_SCHEME, LOCALHOST_IP));

    @Test
    public void testTopPrivateDomainAndScheme_validPublicDomainAndHttpsScheme() {
        Optional<Uri> output =
                WebAddresses.topPrivateDomainAndScheme(HTTPS_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX);
        assertEquals(HTTPS_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX, output.get());
    }

    @Test
    public void testTopPrivateDomainAndScheme_validLocalhost() {
        // Localhost
        Optional<Uri> localhost = WebAddresses.topPrivateDomainAndScheme(HTTPS_LOCALHOST);
        assertEquals(HTTPS_LOCALHOST, localhost.get());

        Uri localhostWithPath =
                Uri.parse(String.format("%s://%s/%s", HTTPS_SCHEME, LOCALHOST, "path"));
        assertEquals(HTTPS_LOCALHOST,
                WebAddresses.topPrivateDomainAndScheme(localhostWithPath).get());

        Uri localhostWithPort =
                Uri.parse(String.format("%s://%s:%s", HTTPS_SCHEME, LOCALHOST, "4000"));
        assertEquals(HTTPS_LOCALHOST,
                WebAddresses.topPrivateDomainAndScheme(localhostWithPort).get());

        // localhost ip
        Optional<Uri> localhost_ip = WebAddresses.topPrivateDomainAndScheme(HTTPS_LOCALHOST_IP);
        assertEquals(HTTPS_LOCALHOST_IP, localhost_ip.get());

        Uri localhostIpWithPath =
                Uri.parse(String.format("%s://%s/%s", HTTPS_SCHEME, LOCALHOST_IP, "path"));
        assertEquals(HTTPS_LOCALHOST_IP,
                WebAddresses.topPrivateDomainAndScheme(localhostIpWithPath).get());

        Uri localhostIpWithPort =
                Uri.parse(String.format("%s://%s:%s", HTTPS_SCHEME, LOCALHOST_IP, "4000"));
        assertEquals(HTTPS_LOCALHOST_IP,
                WebAddresses.topPrivateDomainAndScheme(localhostIpWithPort).get());
    }

    @Test
    public void testOriginAndScheme_validPublicDomainAndHttpsScheme() {
        Optional<Uri> output = WebAddresses.originAndScheme(HTTPS_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX);
        assertEquals(HTTPS_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX, output.get());
    }

    @Test
    public void testOriginAndScheme_validLocalhost() {
        // Localhost
        Optional<Uri> localhost = WebAddresses.originAndScheme(HTTPS_LOCALHOST);
        assertEquals(HTTPS_LOCALHOST, localhost.get());

        Uri localhostWithPath =
                Uri.parse(String.format("%s://%s/%s", HTTPS_SCHEME, LOCALHOST, "path"));
        assertEquals(HTTPS_LOCALHOST, WebAddresses.originAndScheme(localhostWithPath).get());

        Uri localhostWithPort =
                Uri.parse(String.format("%s://%s:%s", HTTPS_SCHEME, LOCALHOST, "4000"));
        assertEquals(localhostWithPort, WebAddresses.originAndScheme(localhostWithPort).get());

        // localhost ip
        Optional<Uri> localhost_ip = WebAddresses.originAndScheme(HTTPS_LOCALHOST_IP);
        assertEquals(HTTPS_LOCALHOST_IP, localhost_ip.get());

        Uri localhostIpWithPath =
                Uri.parse(String.format("%s://%s/%s", HTTPS_SCHEME, LOCALHOST_IP, "path"));
        assertEquals(HTTPS_LOCALHOST_IP, WebAddresses.originAndScheme(localhostIpWithPath).get());

        Uri localhostIpWithPort =
                Uri.parse(String.format("%s://%s:%s", HTTPS_SCHEME, LOCALHOST_IP, "4000"));
        assertEquals(localhostIpWithPort, WebAddresses.originAndScheme(localhostIpWithPort).get());
    }

    @Test
    public void testTopPrivateDomainAndScheme_validPrivateDomainAndHttpsScheme() {
        Optional<Uri> output =
                WebAddresses.topPrivateDomainAndScheme(
                        HTTPS_PRIVATE_DOMAIN_BLOGSPOT_COM_PUBLIC_SUFFIX);
        assertEquals(HTTPS_PRIVATE_DOMAIN_BLOGSPOT_COM_PUBLIC_SUFFIX, output.get());
    }

    @Test
    public void testOriginAndScheme_validPrivateDomainAndHttpsScheme() {
        Optional<Uri> output = WebAddresses.originAndScheme(
                HTTPS_PRIVATE_DOMAIN_BLOGSPOT_COM_PUBLIC_SUFFIX);
        assertEquals(HTTPS_PRIVATE_DOMAIN_BLOGSPOT_COM_PUBLIC_SUFFIX, output.get());
    }

    @Test
    public void testTopPrivateDomainAndScheme_validPublicDomainAndHttpsScheme_extraSubdomain() {
        Optional<Uri> output =
                WebAddresses.topPrivateDomainAndScheme(
                        HTTPS_SUBDOMAIN_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX);
        assertEquals(HTTPS_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX, output.get());
    }

    @Test
    public void testOriginAndScheme_validPublicDomainAndHttpsScheme_extraSubdomain() {
        Optional<Uri> output =
                WebAddresses.originAndScheme(HTTPS_SUBDOMAIN_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX);
        assertEquals(HTTPS_SUBDOMAIN_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX, output.get());
    }

    @Test
    public void testTopPrivateDomainAndScheme_validPrivateDomainAndHttpsScheme_extraSubdomain() {
        Optional<Uri> output =
                WebAddresses.topPrivateDomainAndScheme(
                        HTTPS_SUBDOMAIN_PRIVATE_DOMAIN_BLOGSPOT_COM_PUBLIC_SUFFIX);
        assertEquals(HTTPS_PRIVATE_DOMAIN_BLOGSPOT_COM_PUBLIC_SUFFIX, output.get());
    }

    @Test
    public void testOriginAndScheme_validPrivateDomainAndHttpsScheme_extraSubdomain() {
        Optional<Uri> output =
                WebAddresses.originAndScheme(
                        HTTPS_SUBDOMAIN_PRIVATE_DOMAIN_BLOGSPOT_COM_PUBLIC_SUFFIX);
        assertEquals(HTTPS_SUBDOMAIN_PRIVATE_DOMAIN_BLOGSPOT_COM_PUBLIC_SUFFIX, output.get());
    }

    @Test
    public void testTopPrivateDomainAndScheme_validPublicDomainAndHttpScheme() {
        Optional<Uri> output = WebAddresses.topPrivateDomainAndScheme(
                HTTP_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX);
        assertEquals(HTTP_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX, output.get());
    }

    @Test
    public void testOriginAndScheme_validPublicDomainAndHttpScheme() {
        Optional<Uri> output = WebAddresses.originAndScheme(HTTP_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX);
        assertEquals(HTTP_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX, output.get());
    }

    @Test
    public void testTopPrivateDomainAndScheme_validPublicDomainAndPortAndHttpsScheme() {
        Optional<Uri> output =
                WebAddresses.topPrivateDomainAndScheme(HTTPS_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX_PORT);
        assertEquals(HTTPS_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX, output.get());
    }

    @Test
    public void testOriginAndScheme_validPublicDomainAndPortAndHttpsScheme() {
        Optional<Uri> output = WebAddresses.originAndScheme(
                HTTPS_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX_PORT);
        assertEquals(HTTPS_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX_PORT, output.get());
    }

    @Test
    public void testTopPrivateDomainAndPath_forInvalidUri_returnsEmptyOptional() {
        Optional<Uri> output = WebAddresses.topPrivateDomainAndScheme(Uri.parse(INVALID_URL));
        assertFalse(output.isPresent());
    }

    @Test
    public void testOriginAndScheme_forInvalidUri_returnsEmptyOptional() {
        Optional<Uri> output = WebAddresses.originAndScheme(Uri.parse(INVALID_URL));
        assertFalse(output.isPresent());
    }

    @Test
    public void testTopPrivateDomainAndScheme_invalidTldAndHttpScheme_returnsEmptyOptional() {
        String inputUrl = String.format("%s://%s.%s", HTTP_SCHEME, TOP_PRIVATE_DOMAIN, INVALID_TLD);
        Optional<Uri> output = WebAddresses.topPrivateDomainAndScheme(Uri.parse(inputUrl));
        assertFalse(output.isPresent());
    }

    @Test
    public void testTopPrivateDomainAndScheme_invalidLocalhostScheme_returnsEmptyOptional() {
        Uri inputUrl = Uri.parse(String.format("%s://%s", HTTP_SCHEME, LOCALHOST));
        Optional<Uri> output = WebAddresses.topPrivateDomainAndScheme(inputUrl);
        assertFalse(output.isPresent());
    }

    @Test
    public void testTopPrivateDomainAndScheme_invalidLocalhost_returnsEmptyOptional() {
        assertFalse(WebAddresses.topPrivateDomainAndScheme(
                Uri.parse(String.format("%s://%s", HTTPS_SCHEME, "localyhost"))).isPresent());
        assertFalse(WebAddresses.topPrivateDomainAndScheme(
                Uri.parse(String.format("%s://%s", HTTPS_SCHEME, "localhosts/path"))).isPresent());
        assertFalse(WebAddresses.topPrivateDomainAndScheme(
                Uri.parse(String.format("%s://%s", HTTPS_SCHEME, "localhosts:8000"))).isPresent());
        assertFalse(WebAddresses.topPrivateDomainAndScheme(
                Uri.parse(String.format("%s://%s", HTTPS_SCHEME, "128.0.0.1"))).isPresent());
        assertFalse(WebAddresses.topPrivateDomainAndScheme(
                Uri.parse(String.format("%s://%s", HTTPS_SCHEME, "127.1.0.1/path"))).isPresent());
        assertFalse(WebAddresses.topPrivateDomainAndScheme(
                Uri.parse(String.format("%s://%s", HTTPS_SCHEME, "127.0.0.2:7654"))).isPresent());
    }

    @Test
    public void testOriginAndScheme_invalidTldAndHttpScheme_returnsEmptyOptional() {
        String inputUrl = String.format("%s://%s.%s", HTTP_SCHEME, TOP_PRIVATE_DOMAIN, INVALID_TLD);
        Optional<Uri> output = WebAddresses.originAndScheme(Uri.parse(inputUrl));
        assertFalse(output.isPresent());
    }

    @Test
    public void testOriginAndScheme_invalidTldAndHttpsScheme_returnsEmptyOptional() {
        String inputUrl =
                String.format("%s://%s.%s", HTTPS_SCHEME, TOP_PRIVATE_DOMAIN, INVALID_TLD);
        Optional<Uri> output = WebAddresses.originAndScheme(Uri.parse(inputUrl));
        assertFalse(output.isPresent());
    }

    @Test
    public void testOriginAndScheme_invalidTldAndHttpSchemeAndSubdomain_returnsEmptyOptional() {
        String inputUrl =
                String.format(
                        "%s://%s.%s.%s", HTTP_SCHEME, SUBDOMAIN, TOP_PRIVATE_DOMAIN, INVALID_TLD);
        Optional<Uri> output = WebAddresses.originAndScheme(Uri.parse(inputUrl));
        assertFalse(output.isPresent());
    }

    @Test
    public void testOriginAndScheme_invalidTldAndHttpsSchemeAndSubdomain_returnsEmptyOptional() {
        String inputUrl =
                String.format(
                        "%s://%s.%s.%s", HTTPS_SCHEME, SUBDOMAIN, TOP_PRIVATE_DOMAIN, INVALID_TLD);
        Optional<Uri> output = WebAddresses.originAndScheme(Uri.parse(inputUrl));
        assertFalse(output.isPresent());
    }

    @Test
    public void testOriginAndScheme_invalidLocalhostScheme() {
        Uri inputUrl = Uri.parse(String.format("%s://%s", HTTP_SCHEME, LOCALHOST));
        Optional<Uri> output = WebAddresses.originAndScheme(inputUrl);
        assertFalse(output.isPresent());
    }

    @Test
    public void testOriginAndScheme_invalidLocalhost() {
        assertFalse(WebAddresses.originAndScheme(
                Uri.parse(String.format("%s://%s", HTTPS_SCHEME, "localyhost"))).isPresent());
        assertFalse(WebAddresses.originAndScheme(
                Uri.parse(String.format("%s://%s", HTTPS_SCHEME, "localhosts/path"))).isPresent());
        assertFalse(WebAddresses.originAndScheme(
                Uri.parse(String.format("%s://%s", HTTPS_SCHEME, "localhosts:8000"))).isPresent());
        assertFalse(WebAddresses.originAndScheme(
                Uri.parse(String.format("%s://%s", HTTPS_SCHEME, "128.0.0.1"))).isPresent());
        assertFalse(WebAddresses.originAndScheme(
                Uri.parse(String.format("%s://%s", HTTPS_SCHEME, "127.1.0.1/path"))).isPresent());
        assertFalse(WebAddresses.originAndScheme(
                Uri.parse(String.format("%s://%s", HTTPS_SCHEME, "127.0.0.2:7654"))).isPresent());
    }

    @Test
    public void testIsLocalHost_success() {
        assertTrue(WebAddresses.isLocalhost(Uri.parse("https://127.0.0.1")));
        assertTrue(WebAddresses.isLocalhost(Uri.parse("https://127.0.0.1:5000/path")));
        assertTrue(WebAddresses.isLocalhost(Uri.parse("https://127.0.0.1/path")));
        assertTrue(WebAddresses.isLocalhost(Uri.parse("https://localhost")));
        assertTrue(WebAddresses.isLocalhost(Uri.parse("https://localhost:5000/path")));
        assertTrue(WebAddresses.isLocalhost(Uri.parse("https://localhost/path")));
    }

    @Test
    public void testIsLocalHost_wrongScheme() {
        assertFalse(WebAddresses.isLocalhost(Uri.parse("android-app://com.example")));
        assertFalse(WebAddresses.isLocalhost(WebUtil.validUri("http://example.test:8000")));
        assertFalse(WebAddresses.isLocalhost(Uri.parse("http://127.0.0.1:5000/path")));
        assertFalse(WebAddresses.isLocalhost(Uri.parse("http://127.0.0.1/path")));
        assertFalse(WebAddresses.isLocalhost(Uri.parse("127.0.0.1:5000/path")));
        assertFalse(WebAddresses.isLocalhost(Uri.parse("127.0.0.1/path")));
        assertFalse(WebAddresses.isLocalhost(Uri.parse("http://localhost:5000/path")));
        assertFalse(WebAddresses.isLocalhost(Uri.parse("http://localhost/path")));
        assertFalse(WebAddresses.isLocalhost(Uri.parse("localhost:5000/path")));
        assertFalse(WebAddresses.isLocalhost(Uri.parse("localhost/path")));
    }

    @Test
    public void testIsLocalHost_wrongHost() {
        assertFalse(WebAddresses.isLocalhost(Uri.parse("https://128.0.0.1")));
        assertFalse(WebAddresses.isLocalhost(Uri.parse("https://127.56.0.1:5000/path")));
        assertFalse(WebAddresses.isLocalhost(Uri.parse("https://127.0.1.1/path")));
        assertFalse(WebAddresses.isLocalhost(Uri.parse("https://localhosts")));
        assertFalse(WebAddresses.isLocalhost(Uri.parse("https://not-localhost:5000/path")));
        assertFalse(WebAddresses.isLocalhost(Uri.parse("https://localyhost/path")));
    }

    @Test
    public void testIsLocalHostIp_success() {
        assertTrue(WebAddresses.isLocalhostIp(Uri.parse("https://127.0.0.1")));
        assertTrue(WebAddresses.isLocalhostIp(Uri.parse("https://127.0.0.1:5000/path")));
        assertTrue(WebAddresses.isLocalhostIp(Uri.parse("https://127.0.0.1/path")));
    }

    @Test
    public void testIsLocalHostIp_wrongScheme() {
        assertFalse(WebAddresses.isLocalhostIp(Uri.parse("android-app://com.example")));
        assertFalse(WebAddresses.isLocalhostIp(WebUtil.validUri("http://example.test:8000")));
        assertFalse(WebAddresses.isLocalhostIp(Uri.parse("http://127.0.0.1:5000/path")));
        assertFalse(WebAddresses.isLocalhostIp(Uri.parse("http://127.0.0.1/path")));
        assertFalse(WebAddresses.isLocalhostIp(Uri.parse("127.0.0.1:5000/path")));
        assertFalse(WebAddresses.isLocalhostIp(Uri.parse("127.0.0.1/path")));
    }

    @Test
    public void testIsLocalHostIp_wrongHost() {
        assertFalse(WebAddresses.isLocalhostIp(Uri.parse("https://localhost")));
        assertFalse(WebAddresses.isLocalhostIp(Uri.parse("https://localhost:5000/path")));
        assertFalse(WebAddresses.isLocalhostIp(Uri.parse("https://localhost/path")));
        assertFalse(WebAddresses.isLocalhostIp(Uri.parse("https://128.0.0.1")));
        assertFalse(WebAddresses.isLocalhostIp(Uri.parse("https://127.56.0.1:5000/path")));
        assertFalse(WebAddresses.isLocalhostIp(Uri.parse("https://127.0.1.1/path")));
        assertFalse(WebAddresses.isLocalhostIp(Uri.parse("https://localhosts")));
        assertFalse(WebAddresses.isLocalhostIp(Uri.parse("https://not-localhost:5000/path")));
        assertFalse(WebAddresses.isLocalhostIp(Uri.parse("https://localyhost/path")));
    }
}
