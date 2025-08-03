/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.content.pm.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.pm.Signature;
import android.os.Parcel;
import android.platform.test.annotations.AppModeFull;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
@AppModeFull // TODO(Instant) Figure out which APIs should work.
public class SignatureTest {

    private static final String SIGNATURE_STRING = "1234567890abcdef";
    // SIGNATURE_BYTE_ARRAY is the byte code of SIGNATURE_STRING.
    private static final byte[] SIGNATURE_BYTE_ARRAY = { (byte) 0x12, (byte) 0x34, (byte) 0x56,
            (byte) 0x78, (byte) 0x90, (byte) 0xab, (byte) 0xcd, (byte) 0xef };
    // DIFF_BYTE_ARRAY has different content to SIGNATURE_STRING.
    private static final byte[] DIFF_BYTE_ARRAY = { (byte) 0xfe, (byte) 0xdc, (byte) 0xba,
            (byte) 0x09, (byte) 0x87, (byte) 0x65, (byte) 0x43, (byte) 0x21 };

    @Test
    public void testSignatureStringConstructorValid() {
        Signature signature = new Signature(SIGNATURE_STRING);
        byte[] actualByteArray = signature.toByteArray();
        assertTrue("Output byte array should match constructor byte array.",
                Arrays.equals(SIGNATURE_BYTE_ARRAY, actualByteArray));
    }

    @Test
    public void testSignatureStringConstructorNull() {
        String sig = null;

        try {
            Signature signature = new Signature(sig);
            fail("Should throw NullPointerException on null input");
        } catch (NullPointerException e) {
            // pass
        }
    }

    @Test
    public void testSignatureStringConstructorInvalidLength() {
        try {
            Signature signature = new Signature("123");
            fail("Should throw IllegalArgumentException on odd-sized input");
        } catch (IllegalArgumentException e) {
            // pass
        }
    }

    @Test
    public void testSignatureByteArrayToCharsString() {
        Signature signature = new Signature(SIGNATURE_BYTE_ARRAY);
        String actualString = signature.toCharsString();
        assertEquals(SIGNATURE_STRING, actualString);
    }

    @Test
    public void testSignatureByteArrayConstructorNull() {
        byte[] sig = null;

        try {
            Signature signature = new Signature(sig);
            fail("Should throw NullPointerException on null input");
        } catch (NullPointerException e) {
            // pass
        }
    }

    @Test
    public void testSignatureToChars() {
        Signature signature = new Signature(SIGNATURE_BYTE_ARRAY);
        char[] charArray = signature.toChars();
        String actualString = new String(charArray);
        assertEquals(SIGNATURE_STRING, actualString);
    }

    @Test
    public void testSignatureToCharsExistingArrayCorrectlySized() {
        char[] existingCharArray = new char[SIGNATURE_STRING.length()];
        int[] intArray = new int[1];

        Signature signature = new Signature(SIGNATURE_BYTE_ARRAY);

        char[] charArray = signature.toChars(existingCharArray, intArray);

        assertTrue("Should return the same object since it's correctly sized.",
                existingCharArray == charArray);

        String actualString = new String(charArray);
        assertEquals("The re-encoded Signature should match the constructor input",
                SIGNATURE_STRING, actualString);

        // intArray[0] represents the length of array.
        assertEquals(intArray[0], SIGNATURE_BYTE_ARRAY.length);
    }

    @Test
    public void testSignatureToCharsExistingArrayTooSmall() {
        char[] existingCharArray = new char[0];
        int[] intArray = new int[1];

        Signature signature = new Signature(SIGNATURE_BYTE_ARRAY);
        char[] charArray = signature.toChars(existingCharArray, intArray);

        assertFalse("Should return a new array since the existing one is too small",
                existingCharArray == charArray);

        String actualString = new String(charArray);
        assertEquals("The re-encoded Signature should match the constructor input",
                SIGNATURE_STRING, actualString);

        // intArray[0] represents the length of array.
        assertEquals(intArray[0], SIGNATURE_BYTE_ARRAY.length);
    }

    @Test
    public void testSignatureToCharsNullArrays() {
        char[] existingCharArray = null;
        int[] intArray = null;

        Signature signature = new Signature(SIGNATURE_BYTE_ARRAY);
        char[] charArray = signature.toChars(existingCharArray, intArray);

        assertFalse("Should return a new array since the existing one is too small",
                existingCharArray == charArray);

        String actualString = new String(charArray);
        assertEquals("The re-encoded Signature should match the constructor input",
                SIGNATURE_STRING, actualString);
    }

    @Test
    public void testSignatureStringToByteArray() {
        Signature signature = new Signature(SIGNATURE_BYTE_ARRAY);
        byte[] actualByteArray = signature.toByteArray();

        assertFalse("Should return a different array to avoid modification",
                SIGNATURE_BYTE_ARRAY == actualByteArray);

        assertTrue("Output byte array should match constructor byte array.",
                Arrays.equals(SIGNATURE_BYTE_ARRAY, actualByteArray));
    }

    @Test
    public void testTools() {
        Signature byteSignature = new Signature(SIGNATURE_BYTE_ARRAY);
        Signature stringSignature = new Signature(SIGNATURE_STRING);

        // Test describeContents, equals
        assertEquals(0, byteSignature.describeContents());
        assertTrue(byteSignature.equals(stringSignature));

        // Test hashCode
        byteSignature = new Signature(DIFF_BYTE_ARRAY);
        assertNotSame(byteSignature.hashCode(), stringSignature.hashCode());

        // Test writeToParcel
        Parcel p = Parcel.obtain();
        byteSignature.writeToParcel(p, 0);
        p.setDataPosition(0);
        Signature signatureFromParcel = Signature.CREATOR.createFromParcel(p);
        assertTrue(signatureFromParcel.equals(byteSignature));
        p.recycle();
    }

    @Test
    public void testSignatureHashCodeEquals_doesNotIncludeFlags() {
        // Some classes rely on the hash code and equals not including the flags / capabilities
        // for the signer. This test verifies two signers with the same signature but different
        // flags have the same hash code and are still equal.
        Signature signatureWithAllCaps = new Signature(SIGNATURE_STRING);
        signatureWithAllCaps.setFlags(31);
        Signature signatureWithNoCaps = new Signature(SIGNATURE_STRING);
        signatureWithNoCaps.setFlags(0);

        assertEquals(signatureWithAllCaps.hashCode(), signatureWithNoCaps.hashCode());
        assertEquals(signatureWithAllCaps, signatureWithNoCaps);
    }
}
