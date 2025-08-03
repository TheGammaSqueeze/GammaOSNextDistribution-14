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

package android.security.net.config.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.security.Flags;
import android.security.NetworkSecurityPolicy;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_CERTIFICATE_TRANSPARENCY_CONFIGURATION)
public class CertificateTransparencyTest extends BaseTestCase {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private final NetworkSecurityPolicy mInstance = NetworkSecurityPolicy.getInstance();

    @Test
    public void testCertificateTransparencyVerificationRequired() throws Exception {
        assertTrue(mInstance.isCertificateTransparencyVerificationRequired("foo.bar"));
        assertFalse(mInstance.isCertificateTransparencyVerificationRequired("android.com"));
        assertTrue(mInstance.isCertificateTransparencyVerificationRequired("example.com"));
        assertFalse(mInstance.isCertificateTransparencyVerificationRequired("wikipedia.org"));
    }

    @Test
    public void testCertificateTransparencyVerificationRequired_includeSubdomains()
            throws Exception {
        assertTrue(mInstance.isCertificateTransparencyVerificationRequired("now.foo.bar"));
        assertTrue(
                mInstance.isCertificateTransparencyVerificationRequired("something.android.com"));
        assertTrue(
                mInstance.isCertificateTransparencyVerificationRequired("completely.example.com"));
        assertFalse(
                mInstance.isCertificateTransparencyVerificationRequired("different.wikipedia.org"));
    }
}
