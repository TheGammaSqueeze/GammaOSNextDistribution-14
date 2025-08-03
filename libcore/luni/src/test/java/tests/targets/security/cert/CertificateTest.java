/*
 * Copyright (C) 2009 The Android Open Source Project
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
package tests.targets.security.cert;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.Security;
import java.security.SignatureException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertPathValidatorResult;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/*
 * Tests for CertPathValidator with deprecated certificate types. Verifies that the public
 * CertPath APIs can continue to process them even after they are rejected
 * by the platform for TLS purposes.
 *
 * Expectations:
 * * MD2-signed - always unsupported.
 * * MD5-signed - depending on the version of the Conscrypt module, may be supported.
 * * SHA1-signed - always supported.
 *
 * A supported certificate should always allow a CertPath rooted at that certificate to
 * validate correctly.
 *
 * TODO: Should also test non-deprecated certificate types, but that is effectively covered
 * by TLS tests.
 */
@RunWith(JUnit4.class)
public class CertificateTest {
    private final CertificateFactory certificateFactory = onlyX509CertificateFactory();

    /*
     * A self=signed certificate using MD2: https://crt.sh/?id=162
     *
     *         Signature Algorithm: md2WithRSAEncryption
     *         Issuer: C = US, O = "VeriSign, Inc.", OU = Class 3 Public Primary Certification Authority
     *         Validity
     *             Not Before: Jan 29 00:00:00 1996 GMT
     *             Not After : Aug  1 23:59:59 2028 GMT
     *         Subject: C = US, O = "VeriSign, Inc.", OU = Class 3 Public Primary Certification Authority
     */
    private final X509Certificate md2Root = loadCertificate("md2Root.pem");

    /*
     * A self-signed certificate using MD5withRSA: https://crt.sh/?id=20
     *
     *         Signature Algorithm: md5WithRSAEncryption
     *         Issuer: C = ZA, ST = Western Cape, L = Cape Town, O = Thawte Consulting cc, OU = Certification Services Division, CN = Thawte Premium Server CA, emailAddress = premium-server@thawte.com
     *         Validity
     *             Not Before: Aug  1 00:00:00 1996 GMT
     *             Not After : Dec 31 23:59:59 2020 GMT
     *         Subject: C = ZA, ST = Western Cape, L = Cape Town, O = Thawte Consulting cc, OU = Certification Services Division, CN = Thawte Premium Server CA, emailAddress = premium-server@thawte.com
     */
    private final X509Certificate md5ChainRoot = loadCertificate("md5ChainRoot.pem");
    /*
     * An intermediate certificate signed by md5ChainRoot: https://crt.sh/?id=845934
     *
     *         Signature Algorithm: sha1WithRSAEncryption
     *         Issuer: C = ZA, ST = Western Cape, L = Cape Town, O = Thawte Consulting cc, OU = Certification Services Division, CN = Thawte Premium Server CA, emailAddress = premium-server@thawte.com
     *         Validity
     *             Not Before: Nov 17 00:00:00 2006 GMT
     *             Not After : Dec 30 23:59:59 2020 GMT
     *         Subject: C = US, O = "thawte, Inc.", OU = Certification Services Division, OU = "(c) 2006 thawte, Inc. - For authorized use only", CN = thawte Primary Root CA
     */
    private final X509Certificate md5ChainIntermediate
        = loadCertificate("md5ChainIntermediate.pem");

    /*
     * A certificate signed by md5ChainIntermediate: https://crt.sh/?id=134
     *
     *         Signature Algorithm: sha1WithRSAEncryption
     *         Issuer: C = US, O = "thawte, Inc.", OU = Certification Services Division, OU = "(c) 2006 thawte, Inc. - For authorized use only", CN = thawte Primary Root CA
     *         Validity
     *             Not Before: Nov 17 00:00:00 2006 GMT
     *             Not After : Nov 16 23:59:59 2016 GMT
     *         Subject: C = US, O = "thawte, Inc.", OU = Terms of use at https://www.thawte.com/cps (c)06, CN = thawte Extended Validation SSL CA
     */
    private final X509Certificate md5ChainLeaf = loadCertificate("md5ChainLeaf.pem");

    /*
     * A self-signed root certificate using SHA-1: https://crt.sh/?id=88
     *
     *     Signature Algorithm: sha1WithRSAEncryption
     *         Issuer: C=BE, O=GlobalSign nv-sa, OU=Root CA, CN=GlobalSign Root CA
     *         Validity
     *             Not Before: Sep  1 12:00:00 1998 GMT
     *             Not After : Jan 28 12:00:00 2028 GMT
     *         Subject: C=BE, O=GlobalSign nv-sa, OU=Root CA, CN=GlobalSign Root CA
     */
    private final X509Certificate sha1ChainRoot = loadCertificate("sha1ChainRoot.pem");

    /*
     * Intermediate certificate signed by sha1ChainRoot: https://crt.sh/?id=234
     *
     *     Signature Algorithm: sha1WithRSAEncryption
     *         Issuer: C=BE, O=GlobalSign nv-sa, OU=Root CA, CN=GlobalSign Root CA
     *         Validity
     *             Not Before: Apr 13 10:00:00 2011 GMT
     *             Not After : Apr 13 10:00:00 2022 GMT
     *         Subject: C=BE, O=GlobalSign nv-sa, CN=GlobalSign Organization Validation CA - G2
     */
    private final X509Certificate sha1ChainIntermediate
        = loadCertificate("sha1ChainIntermediate.pem");

    /*
     * Leaf certificate signed by sha1ChainIntermediate: https://crt.sh/?id=38169079
     *
     *     Signature Algorithm: sha1WithRSAEncryption
     *         Issuer: C=BE, O=GlobalSign nv-sa, CN=GlobalSign Organization Validation CA - G2
     *         Validity
     *             Not Before: Aug 25 14:51:20 2011 GMT
     *             Not After : Jun 28 16:04:51 2012 GMT
     *         Subject: C=GB, ST=London, L=London, OU=Internet Operations, O=British Broadcasting Corporation, CN=www.bbc.co.uk
     */
    private final X509Certificate sha1ChainLeaf = loadCertificate("sha1ChainLeaf.pem");

    private final boolean md5SignatureSupported = isMd5Supported();

    public CertificateTest()
        throws CertificateException, SignatureException, InvalidKeyException, NoSuchProviderException {
    }

    @Test
    public void verifySha1Supported() throws Exception {
        sha1ChainRoot.verify(sha1ChainRoot.getPublicKey());
    }

    @Test
    public void verifyMd2NotSupported() {
        assertThrows(NoSuchAlgorithmException.class, () ->
            md2Root.verify(md2Root.getPublicKey()));
    }

    @Test
    public void verifyMd5Chain_rootNotIncluded() throws Exception {
        Assume.assumeTrue(md5SignatureSupported);

        CertPath path = certificateFactory.generateCertPath(
            List.of(md5ChainLeaf, md5ChainIntermediate));

        CertPathValidator certPathValidator = CertPathValidator.getInstance("PKIX");
        PKIXParameters params = createPkixParams(md5ChainRoot);
        backdate(params, 2016);

        validate(path, certPathValidator, params);
    }

    @Test
    public void verifyMd5Chain_rootIncluded() throws Exception {
        Assume.assumeTrue(md5SignatureSupported);

        CertPath path = certificateFactory.generateCertPath(
            List.of(md5ChainLeaf, md5ChainIntermediate, md5ChainRoot));

        CertPathValidator certPathValidator = CertPathValidator.getInstance("PKIX");
        PKIXParameters params = createPkixParams(md5ChainRoot);
        backdate(params, 2016);

        validate(path, certPathValidator, params);
    }

    @Test
    public void verifyMd5ChainExceptionWhenUnsupported() throws Exception {
        Assume.assumeFalse(md5SignatureSupported);

        CertPath path = certificateFactory.generateCertPath(
            List.of(md5ChainLeaf, md5ChainIntermediate, md5ChainRoot));

        CertPathValidator certPathValidator = CertPathValidator.getInstance("PKIX");
        PKIXParameters params = createPkixParams(md5ChainRoot);
        backdate(params, 2016);

        Exception exception = assertThrows(CertPathValidatorException.class,
            () -> validate(path, certPathValidator, params));
        // Correct cause should be NoSuchAlgorithmException but older Conscrypt modules
        // may throw CertificateException.
        assertTrue(exception.getCause().getClass() == CertificateException.class ||
            exception.getCause().getClass() == NoSuchAlgorithmException.class);

    }

    @Test
    public void verifySha1Chain_rootNotIncluded() throws Exception {
        CertPath path = certificateFactory.generateCertPath(
            List.of(sha1ChainLeaf, sha1ChainIntermediate));

        CertPathValidator certPathValidator = CertPathValidator.getInstance("PKIX");
        PKIXParameters params = createPkixParams(sha1ChainRoot);
        backdate(params, 2012);

        validate(path, certPathValidator, params);
    }

    @Test
    public void verifySha1Chain_rootIncluded() throws Exception {
        CertPath path = certificateFactory.generateCertPath(
            List.of(sha1ChainLeaf, sha1ChainIntermediate, sha1ChainRoot));

        CertPathValidator certPathValidator = CertPathValidator.getInstance("PKIX");
        PKIXParameters params = createPkixParams(sha1ChainRoot);
        backdate(params, 2012);

        validate(path, certPathValidator, params);
    }

    private PKIXParameters createPkixParams(X509Certificate rootCa) throws Exception {
        Set<TrustAnchor> anchors = Set.of(new TrustAnchor(rootCa, null));
        PKIXParameters params = new PKIXParameters(anchors);
        params.setRevocationEnabled(false);
        return params;
    }

    // The test certificates above are long expired, so allow checking at a point in time
    // when they weren't.
    private void backdate(PKIXParameters params, int year) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, Calendar.JANUARY, 1);
        params.setDate(calendar.getTime());
    }

    // Checks that a CertPath validates and matches the expected trust anchor.
    private void validate(CertPath path, CertPathValidator certPathValidator,
                PKIXParameters params) throws Exception {
        CertPathValidatorResult result = certPathValidator.validate(path, params);
        assertTrue("wrong result type",
            result instanceof PKIXCertPathValidatorResult);

        PKIXCertPathValidatorResult pkixResult = (PKIXCertPathValidatorResult) result;
        assertTrue("Wrong trust anchor returned",
            params.getTrustAnchors().contains(pkixResult.getTrustAnchor()));
    }


    // Returns true if the version of BoringSSL in use allows MD5-signed certificates.
    // Depending on the version of BoringSSL in use, which in turn depends on the version
    // of the Conscrypt module, MD5-signed certificates may or may not be rejected by BoringSSL.
    private boolean isMd5Supported() throws SignatureException, InvalidKeyException,
                NoSuchProviderException {
        try {
            md5ChainRoot.verify(md5ChainRoot.getPublicKey());
            return true;
        } catch (NoSuchAlgorithmException | CertificateException e) {
            // TODO(prb): After Conscrypt PR 1117 should only throw NoSuchAlgorithmException.
            return false;
        }
    }

    // There should only be a single x.509 Certificate Provider (Conscrypt).
    private CertificateFactory onlyX509CertificateFactory() throws CertificateException {
        Provider[] providers = Security.getProviders("CertificateFactory.X509");
        if (providers.length != 1) {
            throw new IllegalStateException("There should be exactly one X.509 CertificateFactory");
        }
        return CertificateFactory.getInstance("X509", providers[0]);
    }

    // Loads an X509Certificate from a PEM or DER resource.
    private X509Certificate loadCertificate(String name) throws CertificateException {
        InputStream inputStream = getClass().getResourceAsStream("/certpath/" + name);
        return (X509Certificate) certificateFactory.generateCertificate(inputStream);
    }
}
