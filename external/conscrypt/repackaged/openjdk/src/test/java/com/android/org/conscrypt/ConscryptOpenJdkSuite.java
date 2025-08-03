/* GENERATED SOURCE. DO NOT MODIFY. */
package com.android.org.conscrypt;

import static com.android.org.conscrypt.TestUtils.installConscryptAsDefaultProvider;

import com.android.org.conscrypt.ct.CTVerifierTest;
import com.android.org.conscrypt.ct.SerializationTest;
import com.android.org.conscrypt.java.security.AlgorithmParameterGeneratorTestDH;
import com.android.org.conscrypt.java.security.AlgorithmParameterGeneratorTestDSA;
import com.android.org.conscrypt.java.security.AlgorithmParametersPSSTest;
import com.android.org.conscrypt.java.security.AlgorithmParametersTestAES;
import com.android.org.conscrypt.java.security.AlgorithmParametersTestDES;
import com.android.org.conscrypt.java.security.AlgorithmParametersTestDESede;
import com.android.org.conscrypt.java.security.AlgorithmParametersTestDH;
import com.android.org.conscrypt.java.security.AlgorithmParametersTestDSA;
import com.android.org.conscrypt.java.security.AlgorithmParametersTestEC;
import com.android.org.conscrypt.java.security.AlgorithmParametersTestGCM;
import com.android.org.conscrypt.java.security.AlgorithmParametersTestOAEP;
import com.android.org.conscrypt.java.security.KeyFactoryTestDH;
import com.android.org.conscrypt.java.security.KeyFactoryTestDSA;
import com.android.org.conscrypt.java.security.KeyFactoryTestEC;
import com.android.org.conscrypt.java.security.KeyFactoryTestRSA;
import com.android.org.conscrypt.java.security.KeyFactoryTestRSACrt;
import com.android.org.conscrypt.java.security.KeyPairGeneratorTest;
import com.android.org.conscrypt.java.security.KeyPairGeneratorTestDH;
import com.android.org.conscrypt.java.security.KeyPairGeneratorTestDSA;
import com.android.org.conscrypt.java.security.KeyPairGeneratorTestRSA;
import com.android.org.conscrypt.java.security.KeyPairGeneratorTestXDH;
import com.android.org.conscrypt.java.security.MessageDigestTest;
import com.android.org.conscrypt.java.security.SignatureTest;
import com.android.org.conscrypt.java.security.cert.CertificateFactoryTest;
import com.android.org.conscrypt.java.security.cert.X509CRLTest;
import com.android.org.conscrypt.java.security.cert.X509CertificateTest;
import com.android.org.conscrypt.javax.crypto.AeadCipherTest;
import com.android.org.conscrypt.javax.crypto.CipherBasicsTest;
import com.android.org.conscrypt.javax.crypto.CipherTest;
import com.android.org.conscrypt.javax.crypto.ECDHKeyAgreementTest;
import com.android.org.conscrypt.javax.crypto.KeyGeneratorTest;
import com.android.org.conscrypt.javax.crypto.ScryptTest;
import com.android.org.conscrypt.javax.crypto.XDHKeyAgreementTest;
import com.android.org.conscrypt.javax.crypto.XdhKeyFactoryTest;
import com.android.org.conscrypt.javax.crypto.XdhKeyTest;
import com.android.org.conscrypt.javax.net.ssl.HttpsURLConnectionTest;
import com.android.org.conscrypt.javax.net.ssl.KeyManagerFactoryTest;
import com.android.org.conscrypt.javax.net.ssl.KeyStoreBuilderParametersTest;
import com.android.org.conscrypt.javax.net.ssl.SNIHostNameTest;
import com.android.org.conscrypt.javax.net.ssl.SSLContextTest;
import com.android.org.conscrypt.javax.net.ssl.SSLEngineTest;
import com.android.org.conscrypt.javax.net.ssl.SSLEngineVersionCompatibilityTest;
import com.android.org.conscrypt.javax.net.ssl.SSLParametersTest;
import com.android.org.conscrypt.javax.net.ssl.SSLServerSocketFactoryTest;
import com.android.org.conscrypt.javax.net.ssl.SSLServerSocketTest;
import com.android.org.conscrypt.javax.net.ssl.SSLSessionContextTest;
import com.android.org.conscrypt.javax.net.ssl.SSLSessionTest;
import com.android.org.conscrypt.javax.net.ssl.SSLSocketFactoryTest;
import com.android.org.conscrypt.javax.net.ssl.SSLSocketTest;
import com.android.org.conscrypt.javax.net.ssl.SSLSocketVersionCompatibilityTest;
import com.android.org.conscrypt.javax.net.ssl.TrustManagerFactoryTest;
import com.android.org.conscrypt.javax.net.ssl.X509KeyManagerTest;
import com.android.org.conscrypt.metrics.CipherSuiteTest;
import com.android.org.conscrypt.metrics.OptionalMethodTest;
import com.android.org.conscrypt.metrics.ProtocolTest;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * @hide This class is not part of the Android public SDK API
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        // org.conscrypt tests
        AddressUtilsTest.class,
        ApplicationProtocolSelectorAdapterTest.class,
        ArrayUtilsTest.class,
        CertPinManagerTest.class,
        ChainStrengthAnalyzerTest.class,
        ClientSessionContextTest.class,
        ConscryptSocketTest.class,
        ConscryptTest.class,
        DuckTypedHpkeSpiTest.class,
        DuckTypedPSKKeyManagerTest.class,
        FileClientSessionCacheTest.class,
        HostnameVerifierTest.class,
        HpkeContextTest.class,
        HpkeContextRecipientTest.class,
        HpkeContextSenderTest.class,
        HpkeSuiteTest.class,
        HpkeTestVectorsTest.class,
        NativeCryptoArgTest.class,
        NativeCryptoTest.class,
        NativeRefTest.class,
        NativeSslSessionTest.class,
        OpenSSLKeyTest.class,
        OpenSSLX509CertificateTest.class,
        PlatformTest.class,
        SSLUtilsTest.class,
        ServerSessionContextTest.class,
        TestSessionBuilderTest.class,
        TrustManagerImplTest.class,
        // org.conscrypt.ct tests
        CTVerifierTest.class,
        SerializationTest.class,
        // java.security tests
        CertificateFactoryTest.class,
        X509CertificateTest.class,
        X509CRLTest.class,
        AlgorithmParameterGeneratorTestDH.class,
        AlgorithmParameterGeneratorTestDSA.class,
        AlgorithmParametersPSSTest.class,
        AlgorithmParametersTestAES.class,
        AlgorithmParametersTestDES.class,
        AlgorithmParametersTestDESede.class,
        AlgorithmParametersTestDH.class,
        AlgorithmParametersTestDSA.class,
        AlgorithmParametersTestEC.class,
        AlgorithmParametersTestGCM.class,
        AlgorithmParametersTestOAEP.class,
        BufferUtilsTest.class,
        CipherSuiteTest.class,
        KeyFactoryTestDH.class,
        KeyFactoryTestDSA.class,
        KeyFactoryTestEC.class,
        KeyFactoryTestRSA.class,
        KeyFactoryTestRSACrt.class,
        KeyPairGeneratorTest.class,
        KeyPairGeneratorTestDH.class,
        KeyPairGeneratorTestDSA.class,
        KeyPairGeneratorTestRSA.class,
        KeyPairGeneratorTestXDH.class,
        MessageDigestTest.class,
        SignatureTest.class,
        // javax.crypto tests
        AeadCipherTest.class,
        CipherBasicsTest.class,
        CipherTest.class,
        MacTest.class,
        ECDHKeyAgreementTest.class,
        KeyGeneratorTest.class,
        XDHKeyAgreementTest.class,
        XdhKeyFactoryTest.class,
        XdhKeyTest.class,
        // javax.net.ssl tests
        HttpsURLConnectionTest.class,
        KeyManagerFactoryTest.class,
        KeyStoreBuilderParametersTest.class,
        OptionalMethodTest.class,
        ProtocolTest.class,
        ScryptTest.class,
        SNIHostNameTest.class,
        SSLContextTest.class,
        SSLEngineTest.class,
        SSLEngineVersionCompatibilityTest.class,
        SSLParametersTest.class,
        SSLServerSocketFactoryTest.class,
        SSLServerSocketTest.class,
        SSLSessionContextTest.class,
        SSLSessionTest.class,
        SSLSocketFactoryTest.class,
        SSLSocketTest.class,
        SSLSocketVersionCompatibilityTest.class,
        TrustManagerFactoryTest.class,
        VeryBasicHttpServerTest.class,
        X509KeyManagerTest.class,
})
public class ConscryptOpenJdkSuite {
    @BeforeClass
    public static void setupStatic() {
        installConscryptAsDefaultProvider();
    }
}
