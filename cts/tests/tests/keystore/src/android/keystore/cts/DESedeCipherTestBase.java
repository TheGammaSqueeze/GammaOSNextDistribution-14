package android.keystore.cts;

import android.keystore.cts.util.TestUtils;

import org.junit.Test;

public abstract class DESedeCipherTestBase extends BlockCipherTestBase {

    private static final byte[] KAT_KEY = HexEncoding.decode(
            "5EBE2294ECD0E0F08EAB7690D2A6EE6926AE5CC854E36B6B");
    private static final byte[] KAT_PLAINTEXT = HexEncoding.decode(
            "31323334353637383132333435363738");

    @Override
    protected int getBlockSize() {
        return 8;
    }

    @Override
    protected byte[] getKatKey() {
        return KAT_KEY.clone();
    }

    @Override
    protected byte[] getKatPlaintext() {
        return KAT_PLAINTEXT.clone();
    }

    @Override
    protected int getKatAuthenticationTagLengthBytes() {
        return 0;
    }

    @Override
    protected boolean isAuthenticatedCipher() {
        return false;
    }

    @Override
    protected boolean isStreamCipher() {
        return false;
    }

    @java.lang.Override
    @Test
    public void testGetProvider() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testGetProvider();
        }
    }

    @java.lang.Override
    @Test
    public void testGetAlgorithm() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testGetProvider();
        }
    }

    @java.lang.Override
    @Test
    public void testGetBlockSize() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testGetProvider();
        }
    }

    @java.lang.Override
    @Test
    public void testGetExemptionMechanism() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testGetProvider();
        }
    }

  @Override
    @Test
    public void testUpdateCopySafe() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testUpdateCopySafe();
        }
    }

    @Override
    @Test
    public void testUpdateAndDoFinalNotSupportedInWrapAndUnwrapModes() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testUpdateWithEmptyInputReturnsCorrectValue();
        }
    }

    @Override
    @Test
    public void testKeyDoesNotSurviveReinitialization() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testUpdateWithEmptyInputReturnsCorrectValue();
        }
    }

    @Override
    @Test
    public void testKatOneShotEncryptUsingDoFinal() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testUpdateWithEmptyInputReturnsCorrectValue();
        }
    }

    @Override
    @Test
    public void testKatOneShotDecryptUsingDoFinal() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testUpdateWithEmptyInputReturnsCorrectValue();
        }
    }

    @Override
    @Test
    public void testKatEncryptOneByteAtATime() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testUpdateWithEmptyInputReturnsCorrectValue();
        }
    }

    @Override
    @Test
    public void testKatDecryptOneByteAtATime() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testUpdateWithEmptyInputReturnsCorrectValue();
        }
    }

    @Override
    @Test
    public void testIvGeneratedAndUsedWhenEncryptingWithoutExplicitIv() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testUpdateWithEmptyInputReturnsCorrectValue();
        }
    }

    @Override
    @Test
    public void testGetParameters() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testUpdateWithEmptyInputReturnsCorrectValue();
        }
    }

    @Override
    @Test
    public void testGetOutputSizeInEncryptionMode() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testUpdateWithEmptyInputReturnsCorrectValue();
        }
    }

    @Override
    @Test
    public void testGetOutputSizeInDecryptionMode() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testUpdateWithEmptyInputReturnsCorrectValue();
        }
    }

    @Override
    @Test
    public void testGetIV() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testUpdateWithEmptyInputReturnsCorrectValue();
        }
    }

    @Override
    @Test
    public void testUpdateWithEmptyInputReturnsCorrectValue() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testUpdateWithEmptyInputReturnsCorrectValue();
        }
    }

    @Override
    @Test
    public void testUpdateDoesNotProduceOutputWhenInsufficientInput() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testUpdateWithEmptyInputReturnsCorrectValue();
        }
    }

    @Override
    @Test
    public void testUpdateAADNotSupported() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testUpdateWithEmptyInputReturnsCorrectValue();
        }
    }

    @Override
    @Test
    public void testGeneratedPadding() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testUpdateWithEmptyInputReturnsCorrectValue();
        }
    }

    @Override
    @Test
    public void testDoFinalResets() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testUpdateWithEmptyInputReturnsCorrectValue();
        }
    }

    @Override
    @Test
    public void testDoFinalCopySafe() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testUpdateWithEmptyInputReturnsCorrectValue();
        }
    }

    @Override
    @Test
    public void testDecryptWithMissingPadding() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testUpdateWithEmptyInputReturnsCorrectValue();
        }
    }

    @Override
    @Test
    public void testDecryptWithMangledPadding() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testUpdateWithEmptyInputReturnsCorrectValue();
        }
    }

    @Override
    @Test
    public void testReinitializingInDecryptModeDoesNotUsePreviouslyUsedIv() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testUpdateWithEmptyInputReturnsCorrectValue();
        }
    }

    @Override
    @Test
    public void testInitRequiresIvInDecryptMode() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testUpdateWithEmptyInputReturnsCorrectValue();
        }
    }

    @Override
    @Test
    public void testGeneratedIvSurvivesReset() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testUpdateWithEmptyInputReturnsCorrectValue();
        }
    }

    @Override
    @Test
    public void testGeneratedIvDoesNotSurviveReinitialization() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testUpdateWithEmptyInputReturnsCorrectValue();
        }
    }

    @Override
    @Test
    public void testExplicitlySetIvDoesNotSurviveReinitialization() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testUpdateWithEmptyInputReturnsCorrectValue();
        }
    }

    @Override
    @Test
    public void testVeryLargeBlock() throws Exception {
        if (TestUtils.supports3DES()) {
            super.testVeryLargeBlock();
        }
    }
}
