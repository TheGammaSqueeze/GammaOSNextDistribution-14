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

package com.android.server.wifi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.Test;

import java.security.cert.X509Certificate;

public class CertificateEventInfoTest extends WifiBaseTest {
    private static final String TEST_CERT_HASH = "1234567890";
    CertificateEventInfo mCertificateEventInfo;

    /**
     * test the getCert() and getCertHash() methods
     */
    @Test
    public void testGetMethods() throws Exception {
        X509Certificate cert = mock(X509Certificate.class);
        mCertificateEventInfo = new CertificateEventInfo(cert, TEST_CERT_HASH);
        assertEquals(mCertificateEventInfo.getCert(), cert);
        assertTrue(TEST_CERT_HASH.equals(mCertificateEventInfo.getCertHash()));
    }

    /**
     * test that a null certificate throws an exception
     */
    @Test(expected = NullPointerException.class)
    public void testCertNullInitializer() throws Exception {
        mCertificateEventInfo = new CertificateEventInfo(null, TEST_CERT_HASH);
    }

    /**
     * test that a null certificate hash throws an exception
     */
    @Test(expected = NullPointerException.class)
    public void testCertHashNullInitializer() throws Exception {
        X509Certificate cert = mock(X509Certificate.class);
        mCertificateEventInfo = new CertificateEventInfo(cert, null);
    }
}
